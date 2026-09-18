#!/usr/bin/env python3
"""검증 이미지 합성 — PIL 기반.

이 환경의 ffmpeg 빌드에는 drawtext 필터가 없다(`ffmpeg -filters | grep drawtext` → 0건).
따라서 라벨이 붙는 이미지 합성은 전부 PIL 로 처리한다.

사용법:
  compose_image.py pair  A.png B.png -o out.png [--labels "AS-IS,TO-BE"] [--dir h|v]
  compose_image.py grid  IN... -o out.png [--labels "a,b,c,d"] [--cols 2]
  compose_image.py refrow DEVICE... REF.png -o out.png [--labels "AS-IS,TO-BE,Figma 시안"]
  compose_image.py crop  IN.png -o out.png --box X,Y,W,H [--zoom 2]
  compose_image.py mark  IN.png -o out.png --box X,Y,W,H [--color red]
  compose_image.py scale IN.png -o out.png --width 800
  compose_image.py info  IN.png
  compose_image.py labelbar -o out.png --width W --labels "AS-IS,TO-BE"
"""
import argparse
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit("ERROR: Pillow 필요 — python3 -m pip install --user Pillow")

MAX_TOTAL_WIDTH = 1600   # decision-rules.md 폭 규칙
GAP = 12
BORDER = 1
BG = (255, 255, 255)
LABEL_BG = (246, 248, 250)
LABEL_FG = (36, 41, 47)
BORDER_COLOR = (208, 215, 222)
MIN_PANEL_WIDTH = 300    # 이보다 좁아지면 판독 불가 → 경고
# 라벨은 PR 본문에서 축소돼 보인다 (세로로 긴 2단 이미지는 1/3 가까이 줄어든다).
# 그 뒤에도 읽히도록 패널 폭에 비례해 키우고, 넘치면 잘라내지 말고 접는다.
# 1/16 · 최대 56 은 2x2 격자(패널 800px)에서 본문 축소 뒤 27px, 휴대폰 2단(패널 1080px)에서 22px 로
# 커 보였다. 1/27 · 최대 46 이면 각각 15px · 16px — 본문 글자(14px)와 비슷한 크기로 읽힌다.
LABEL_FONT_DIV = 27      # 글자 크기 = 패널 폭 / 이 값
LABEL_MIN_FONT = 26
LABEL_MAX_FONT = 46

FONT_CANDIDATES = [
    "/System/Library/Fonts/Supplemental/AppleGothic.ttf",  # 한글 + ASCII
    "/Library/Fonts/Arial Unicode.ttf",
    "/System/Library/Fonts/Helvetica.ttc",
]


def load_font(size):
    for path in FONT_CANDIDATES:
        if Path(path).exists():
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def open_rgb(path):
    p = Path(path)
    if not p.exists():
        sys.exit(f"ERROR: 파일 없음: {path}")
    img = Image.open(p)
    # 투명 배경(Figma export 등)은 흰 배경에 합성한다 — 바로 RGB 변환하면 검게 죽는다
    if img.mode in ("RGBA", "LA") or (img.mode == "P" and "transparency" in img.info):
        img = img.convert("RGBA")
        base = Image.new("RGB", img.size, BG)
        base.paste(img, mask=img.split()[-1])
        return base
    return img.convert("RGB")


def _text_w(draw, s, font):
    b = draw.textbbox((0, 0), s, font=font)
    return b[2] - b[0]


def wrap_label(draw, text, font, max_w):
    """라벨을 폭에 맞게 접는다. 공백 우선, 공백이 없으면 글자 단위."""
    if _text_w(draw, text, font) <= max_w:
        return [text]
    lines, cur = [], ""
    for token in text.split(" "):
        cand = f"{cur} {token}".strip()
        if not cur or _text_w(draw, cand, font) <= max_w:
            cur = cand
        else:
            lines.append(cur)
            cur = token
    if cur:
        lines.append(cur)
    out = []
    for line in lines:
        if _text_w(draw, line, font) <= max_w:
            out.append(line)
            continue
        cur = ""
        for ch in line:            # 한글은 공백이 적어 글자 단위로 한 번 더 접는다
            if not cur or _text_w(draw, cur + ch, font) <= max_w:
                cur += ch
            else:
                out.append(cur)
                cur = ch
        if cur:
            out.append(cur)
    return out


def label_strip(w, texts, min_h=0):
    """폭 w 짜리 라벨 스트립. texts 가 여럿이면 균등 분할해 각 칸에 그린다."""
    probe = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    n = max(1, len(texts))
    size = max(LABEL_MIN_FONT, min(w // (LABEL_FONT_DIV * n), LABEL_MAX_FONT))
    font = load_font(size)
    cell = w / n
    pad_x = max(8, int(cell * 0.04))
    wrapped = [wrap_label(probe, t, font, cell - pad_x * 2) for t in texts]
    line_h = int(size * 1.35)
    pad_y = max(10, size // 2)
    strip_h = max(min_h, pad_y * 2 + line_h * max(len(x) for x in wrapped))
    out = Image.new("RGB", (w, strip_h), LABEL_BG)
    draw = ImageDraw.Draw(out)
    for idx, lines in enumerate(wrapped):
        cx = cell * idx + cell / 2
        y = (strip_h - line_h * len(lines)) // 2
        for line in lines:
            b = draw.textbbox((0, 0), line, font=font)
            draw.text((cx - (b[2] - b[0]) / 2 - b[0],
                       y - b[1] + (line_h - (b[3] - b[1])) // 2),
                      line, font=font, fill=LABEL_FG)
            y += line_h
    return out


def add_label(img, text):
    """이미지 위에 라벨 스트립을 얹어 새 이미지를 만든다."""
    if not text:
        return img
    w, h = img.size
    strip = label_strip(w, [text])
    out = Image.new("RGB", (w, h + strip.height), LABEL_BG)
    out.paste(strip, (0, 0))
    out.paste(img, (0, strip.height))
    return out


def add_labels(imgs, labels):
    """여러 장에 라벨을 붙인다. **스트립 높이를 모두 같게** 맞춘다.

    라벨 길이가 달라 한쪽만 두 줄이 되면 패널이 아래로 밀려 좌우 비교가 어긋난다.
    가장 높은 스트립에 나머지를 맞춰 화면이 같은 y 에서 시작하게 한다.
    """
    labels = list(labels) + [""] * (len(imgs) - len(labels))
    heights = [label_strip(im.width, [t]).height for im, t in zip(imgs, labels) if t]
    if not heights:
        return list(imgs)
    top = max(heights)
    out = []
    for im, text in zip(imgs, labels):
        strip = (label_strip(im.width, [text], min_h=top) if text
                 else Image.new("RGB", (im.width, top), LABEL_BG))
        new = Image.new("RGB", (im.width, im.height + top), LABEL_BG)
        new.paste(strip, (0, 0))
        new.paste(im, (0, top))
        out.append(new)
    return out


def add_border(img):
    w, h = img.size
    out = Image.new("RGB", (w + BORDER * 2, h + BORDER * 2), BORDER_COLOR)
    out.paste(img, (BORDER, BORDER))
    return out


def normalize(imgs, axis):
    """h 배치는 높이를, v 배치는 폭을 맞춘다. 업스케일은 하지 않는다(흐려짐)."""
    if axis == "h":
        target = min(i.height for i in imgs)
        return [i if i.height == target
                else i.resize((max(1, round(i.width * target / i.height)), target), Image.LANCZOS)
                for i in imgs]
    target = min(i.width for i in imgs)
    return [i if i.width == target
            else i.resize((target, max(1, round(i.height * target / i.width))), Image.LANCZOS)
            for i in imgs]


def stack(imgs, axis):
    if axis == "h":
        total_w = sum(i.width for i in imgs) + GAP * (len(imgs) - 1)
        total_h = max(i.height for i in imgs)
        out = Image.new("RGB", (total_w, total_h), BG)
        x = 0
        for i in imgs:
            out.paste(i, (x, 0))
            x += i.width + GAP
    else:
        total_w = max(i.width for i in imgs)
        total_h = sum(i.height for i in imgs) + GAP * (len(imgs) - 1)
        out = Image.new("RGB", (total_w, total_h), BG)
        y = 0
        for i in imgs:
            out.paste(i, ((total_w - i.width) // 2, y))
            y += i.height + GAP
    return out


def fit_width(img, max_w=MAX_TOTAL_WIDTH):
    if img.width <= max_w:
        return img
    h = max(1, round(img.height * max_w / img.width))
    return img.resize((max_w, h), Image.LANCZOS)


def save(img, out):
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG", optimize=True)
    kb = Path(out).stat().st_size / 1024
    print(f"{out} {img.width}x{img.height} {kb:.0f}KB")


def split_labels(raw, n):
    if not raw:
        return [None] * n
    parts = [p.strip() for p in raw.split(",")]
    if len(parts) != n:
        sys.exit(f"ERROR: 라벨 {len(parts)}개 vs 이미지 {n}개 — 개수가 맞아야 한다")
    return parts


def warn_narrow(panel_count, total_width):
    per = total_width / max(1, panel_count)
    if per < MIN_PANEL_WIDTH:
        print(f"WARN: 패널당 폭 {per:.0f}px < {MIN_PANEL_WIDTH}px — "
              f"판독이 어렵다. 개별 파일로 올리는 편이 낫다.", file=sys.stderr)


def cmd_pair(a):
    labels = split_labels(a.labels, 2)
    imgs = normalize([open_rgb(a.inputs[0]), open_rgb(a.inputs[1])], a.dir)
    imgs = [add_border(i) for i in add_labels(imgs, labels)]
    out = fit_width(stack(imgs, a.dir))
    if a.dir == "h":
        warn_narrow(2, out.width)
    save(out, a.output)


def fit_ref(img, target_h, target_ratio):
    """참조(시안) 패널을 기기 패널 높이에 맞춘다. 기기 비율 대비 심하게 납작한
    컴포넌트 프레임(예: 322x96)은 같은 비율의 흰 캔버스 중앙에 얹는다 — 늘려서 왜곡하지 않는다."""
    ratio = img.width / img.height
    if ratio > target_ratio * 2.5:
        canvas_w = max(1, round(target_h * target_ratio))
        scale = min(canvas_w * 0.78 / img.width, target_h * 0.5 / img.height)
        scale = max(scale, 0.01)
        w, h = max(1, round(img.width * scale)), max(1, round(img.height * scale))
        small = img.resize((w, h), Image.LANCZOS)
        canvas = Image.new("RGB", (canvas_w, target_h), BG)
        canvas.paste(small, ((canvas_w - w) // 2, (target_h - h) // 2))
        return canvas
    w = max(1, round(img.width * target_h / img.height))
    return img.resize((w, target_h), Image.LANCZOS)


def cmd_refrow(a):
    """마지막 입력을 참조(시안) 패널로 취급하는 가로 1행 합성.
    기준은 기기 스크린샷(앞의 것들) — 시안을 기기 높이에 맞춘다(그 반대가 아니다).
    pair/grid 의 normalize 는 최소 높이에 맞추므로 작은 시안 프레임을 넣으면
    기기 패널이 그 높이로 뭉개진다. 그래서 참조 패널만 별도 규칙으로 맞춘다."""
    n = len(a.inputs)
    labels = split_labels(a.labels, n)
    device = normalize([open_rgb(p) for p in a.inputs[:-1]], "h")
    target_h = device[0].height
    target_ratio = device[0].width / device[0].height
    ref = fit_ref(open_rgb(a.inputs[-1]), target_h, target_ratio)
    imgs = [add_border(i) for i in add_labels(device + [ref], labels)]
    out = fit_width(stack(imgs, "h"))
    warn_narrow(n, out.width)
    save(out, a.output)


def cmd_grid(a):
    n = len(a.inputs)
    if n < 2:
        sys.exit("ERROR: grid 는 2개 이상 필요")
    labels = split_labels(a.labels, n)
    imgs = [open_rgb(p) for p in a.inputs]
    cols = a.cols
    rows = []
    for start in range(0, n, cols):
        chunk = imgs[start:start + cols]
        chunk_labels = labels[start:start + cols]
        chunk = normalize(chunk, "h")
        chunk = [add_border(i) for i in add_labels(chunk, chunk_labels)]
        rows.append(stack(chunk, "h"))
    rows = normalize(rows, "v") if len(rows) > 1 else rows
    out = fit_width(stack(rows, "v") if len(rows) > 1 else rows[0])
    warn_narrow(min(cols, n), out.width)
    save(out, a.output)


def parse_box(raw):
    try:
        x, y, w, h = (int(v) for v in raw.split(","))
    except ValueError:
        sys.exit("ERROR: --box 는 X,Y,W,H 형식 (정수)")
    if w <= 0 or h <= 0:
        sys.exit("ERROR: --box 의 W,H 는 양수여야 한다")
    return x, y, w, h


def cmd_crop(a):
    img = open_rgb(a.inputs[0])
    x, y, w, h = parse_box(a.box)
    if x + w > img.width or y + h > img.height:
        sys.exit(f"ERROR: 크롭 영역이 이미지({img.width}x{img.height}) 밖으로 나간다")
    out = img.crop((x, y, x + w, y + h))
    if a.zoom and a.zoom != 1:
        out = out.resize((out.width * a.zoom, out.height * a.zoom), Image.NEAREST)
    save(fit_width(out), a.output)


COLORS = {"red": (215, 58, 73), "blue": (9, 105, 218), "green": (26, 127, 55)}


def cmd_mark(a):
    img = open_rgb(a.inputs[0])
    x, y, w, h = parse_box(a.box)
    draw = ImageDraw.Draw(img)
    color = COLORS.get(a.color, COLORS["red"])
    width = max(3, img.width // 250)
    draw.rectangle([x, y, x + w, y + h], outline=color, width=width)
    save(img, a.output)


def cmd_scale(a):
    img = open_rgb(a.inputs[0])
    save(fit_width(img, a.width), a.output)


def cmd_labelbar(a):
    """영상 합성용 라벨 스트립. ffmpeg 에 vstack 입력으로 넘긴다."""
    labels = [p.strip() for p in (a.labels or "").split(",") if p.strip()]
    if not labels:
        sys.exit("ERROR: labelbar 는 --labels 필요")
    strip = label_strip(a.width, labels)
    h = strip.height + (strip.height % 2)   # ffmpeg vstack + yuv420p 는 짝수 높이를 요구한다
    out = Image.new("RGB", (a.width, h), LABEL_BG)
    out.paste(strip, (0, 0))
    save(out, a.output)


def cmd_info(a):
    for p in a.inputs:
        img = open_rgb(p)
        kb = Path(p).stat().st_size / 1024
        print(f"{p} {img.width}x{img.height} {kb:.0f}KB")


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("command", choices=["pair", "grid", "refrow", "crop", "mark", "scale",
                                       "info", "labelbar"])
    ap.add_argument("inputs", nargs="*")
    ap.add_argument("-o", "--output")
    ap.add_argument("--labels")
    ap.add_argument("--dir", choices=["h", "v"], default="h")
    ap.add_argument("--cols", type=int, default=2)
    ap.add_argument("--box")
    ap.add_argument("--zoom", type=int, default=1)
    ap.add_argument("--color", default="red")
    ap.add_argument("--width", type=int, default=MAX_TOTAL_WIDTH)
    a = ap.parse_args()

    if a.command != "info" and not a.output:
        sys.exit("ERROR: -o/--output 필요")
    if a.command == "pair" and len(a.inputs) != 2:
        sys.exit("ERROR: pair 는 이미지 2개 필요")
    if a.command == "refrow" and len(a.inputs) < 2:
        sys.exit("ERROR: refrow 는 2개 이상 필요 (마지막이 참조 패널)")
    if a.command in ("crop", "mark") and not a.box:
        sys.exit(f"ERROR: {a.command} 은 --box 필요")
    if not a.inputs and a.command != "labelbar":
        sys.exit("ERROR: 입력 이미지 필요")

    {"pair": cmd_pair, "grid": cmd_grid, "refrow": cmd_refrow, "crop": cmd_crop,
     "mark": cmd_mark, "scale": cmd_scale, "info": cmd_info,
     "labelbar": cmd_labelbar}[a.command](a)


if __name__ == "__main__":
    main()
