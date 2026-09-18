#!/usr/bin/env python3
"""설명 그림 HTML 한 장을 PR 본문에 붙일 PNG 파일로 만든다.

`explain-page` 스킬이 만드는 것은 브라우저에서 열어 보는 HTML 이다. PR 본문에는 HTML 을
붙일 수 없고(GHE 새니타이저가 `<style>`·`<script>` 를 지운다) 첨부도 안 되므로,
그 한 장을 PNG 파일로 만들어 storage 레포에 올린다 — 그 뒤는 검증 미디어와 같은 경로를 탄다.

높이는 재지 않고 **찾는다**. 콘텐츠 높이를 알 방법이 headless 스위치에 없어서,
창을 키워 가며 찍고 바닥이 배경색으로 남는 첫 크기를 고른 뒤 여백을 잘라낸다.

배율(`--scale`)은 기본 2 다. `manifest.py add` 가 이미지 안 글자 높이를 재고
`render_section.py` 가 "본문에서 글자가 약 10px 로 보이는 폭" 을 역산하므로,
2배로 구우면 본문에서는 CSS 폭 그대로 보이면서 글자만 선명해진다.

사용법:
  render_explain_png.py --html explain.html --out explain.png
  render_explain_png.py --html explain.html --out explain.png --width 860 --scale 2
"""
import argparse
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

CHROME_CANDIDATES = [
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
]
# 창을 키워 가며 찍는다. 콘텐츠가 바닥에 닿아 있으면 잘렸다는 뜻이라 다음 크기로 넘어간다.
HEIGHT_STEPS = (1400, 2800, 5600, 11200)
PROBE_ROWS = 6          # 바닥이 배경색인지 볼 행 수 (device px)
MIN_WIDTH_PX = 240      # 이보다 좁게 잘리면 그림이 아니라 사고다


def die(msg):
    print(f"ERROR: {msg}", file=sys.stderr)
    sys.exit(1)


def find_chrome():
    env = os.environ.get("CHROME_BIN")
    if env:
        if not Path(env).exists():
            die(f"CHROME_BIN 이 가리키는 실행 파일이 없다: {env}")
        return env
    for c in CHROME_CANDIDATES:
        if Path(c).exists():
            return c
    for name in ("google-chrome", "chromium", "chromium-browser"):
        found = shutil.which(name)
        if found:
            return found
    die("Chrome 을 찾지 못했다 — CHROME_BIN 으로 경로를 지정할 것")


def shoot(chrome, html, out, width, height, scale):
    """한 번 찍는다. Chrome 은 stderr 에 무해한 경고를 늘 뱉으므로 코드만 본다."""
    r = subprocess.run(
        [chrome, "--headless", "--disable-gpu", "--hide-scrollbars",
         "--no-first-run", "--no-default-browser-check",
         f"--force-device-scale-factor={scale}",
         f"--window-size={width},{height}",
         "--virtual-time-budget=2000",
         "--default-background-color=ffffffff",
         f"--screenshot={out}", Path(html).resolve().as_uri()],
        capture_output=True, text=True, timeout=120)
    if not Path(out).exists() or Path(out).stat().st_size == 0:
        die(f"Chrome 이 스크린샷을 만들지 못했다 (exit={r.returncode})\n"
            f"{(r.stderr or r.stdout).strip()[-800:]}")


def load_rgb(path):
    from PIL import Image
    im = Image.open(path)
    if im.mode in ("RGBA", "LA", "P"):
        rgba = im.convert("RGBA")
        bg = Image.new("RGB", rgba.size, (255, 255, 255))
        bg.paste(rgba, mask=rgba.split()[-1])
        return bg
    return im.convert("RGB")


def bottom_is_background(im, rows=PROBE_ROWS):
    """바닥 몇 행이 좌상단 배경색 하나로만 채워져 있는가."""
    w, h = im.size
    bg = im.getpixel((0, 0))
    strip = im.crop((0, max(0, h - rows), w, h))
    return strip.getcolors(maxcolors=4) == [(w * min(rows, h), bg)]


def trim(im, pad):
    """배경색 여백을 잘라내고 pad 만큼만 남긴다."""
    from PIL import Image, ImageChops
    bg = im.getpixel((0, 0))
    box = ImageChops.difference(im, Image.new("RGB", im.size, bg)).getbbox()
    if box is None:
        die("그림이 배경색 한 가지뿐이다 — HTML 이 비어 있거나 렌더되지 않았다")
    l, t, r, b = box
    w, h = im.size
    return im.crop((max(0, l - pad), max(0, t - pad),
                    min(w, r + pad), min(h, b + pad)))


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--html", required=True, help="설명 그림 HTML (단독 파일)")
    ap.add_argument("--out", required=True, help="출력 PNG")
    ap.add_argument("--width", type=int, default=900,
                    help="렌더 폭(CSS px). 기본 900 — GitHub 본문 콘텐츠 폭(약 850)에 맞춘 값")
    ap.add_argument("--scale", type=int, default=2,
                    help="device scale. 기본 2 (본문 폭은 글자 높이로 역산되므로 커지지 않는다)")
    ap.add_argument("--pad", type=int, default=16, help="잘라낸 뒤 남길 여백(CSS px)")
    ap.add_argument("--no-trim", action="store_true", help="여백을 그대로 둔다")
    a = ap.parse_args()

    if not Path(a.html).exists():
        die(f"HTML 이 없다: {a.html}")
    if a.width < MIN_WIDTH_PX:
        die(f"--width 는 {MIN_WIDTH_PX} 이상이어야 한다")
    chrome = find_chrome()

    with tempfile.TemporaryDirectory() as tmp:
        raw = str(Path(tmp) / "raw.png")
        im = None
        for i, h in enumerate(HEIGHT_STEPS):
            shoot(chrome, a.html, raw, a.width, h, a.scale)
            im = load_rgb(raw)
            if bottom_is_background(im):
                break
            if i == len(HEIGHT_STEPS) - 1:
                print(f"WARN: 창 높이 {h}px 에서도 콘텐츠가 바닥에 닿는다 — 그림이 잘렸을 수 있다. "
                      f"한 장이 이만큼 길면 두 장으로 나누는 편이 낫다", file=sys.stderr)

        out = im if a.no_trim else trim(im, a.pad * a.scale)
        if out.width < MIN_WIDTH_PX * a.scale:
            die(f"잘라낸 결과가 {out.width}px 뿐이다 — HTML 이 거의 비어 있다")
        Path(a.out).parent.mkdir(parents=True, exist_ok=True)
        out.save(a.out, optimize=True)

    kb = Path(a.out).stat().st_size / 1024
    print(f"{a.out}  {out.width}x{out.height} (scale {a.scale}x)  {kb:.0f}KB")
    if kb > 4096:
        print("WARN: 4MB 를 넘는다 — manifest validate 가 경고한다. "
              "그림을 나누거나 --scale 1 로 구울 것", file=sys.stderr)


if __name__ == "__main__":
    main()
