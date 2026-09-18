#!/usr/bin/env python3
"""검증 미디어 manifest — verify-media(생성) → verify-media-attach(업로드) 사이의 계약.

manifest.json 이 있으면 attach 스킬은 사람 개입 없이 업로드 경로와 PR 본문 캡션을 결정할 수 있다.

사용법:
  manifest.py init     --dir D --slug S --service calendar --feature month [--device ..] [--branch ..]
  manifest.py add      --dir D --kind image --file a.png --caption "..." [--compare] [--panels N] [--wide]
                       (본문 폭은 이미지 안 글자 높이를 재서 자동 결정 — --wide 는 850px 강제.
                        하한은 패널당 240px: --compare 는 2단 480, refrow 3단은 --panels 3 으로 720)
  manifest.py add      --dir D --kind video --file a.mp4 --gif a.gif --caption "..." [--compare] [--panels N]
  manifest.py add      --dir D --kind image --file spec.png --caption "..." --role spec [--section "이슈 정보"]
  manifest.py add      --dir D --kind image --file explain.png --caption "..." --role explain [--section 해결]
                       (설명 그림 — render_explain_png.py 로 만든 PNG. 본문에서 접힌 토글로 나간다)
  manifest.py validate --dir D
  manifest.py show     --dir D
  manifest.py remove   --dir D --file a.png [--keep-files]
  manifest.py prune    --dir D [--dry-run]
  manifest.py archive  --dir D [--dry-run]
"""
import argparse
import fcntl
import json
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone, timedelta
from pathlib import Path

KST = timezone(timedelta(hours=9))
SLUG_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
KEBAB_RE = re.compile(r"^[a-z0-9][a-z0-9-]{0,31}$")
MAX_ITEMS = 4
DEFAULT_SPEC_SECTION = "이슈 정보"   # role=spec 이 들어갈 본문 섹션 (프로젝트 PR 템플릿 기준)
DEFAULT_EXPLAIN_SECTION = "해결"     # role=explain 이 들어갈 본문 섹션 — 무엇을 어떻게 바꿨는지의 그림
DEFAULT_SECTION = {"spec": DEFAULT_SPEC_SECTION, "explain": DEFAULT_EXPLAIN_SECTION}
MAX_EXPLAIN = 2                # 설명 그림은 한 장으로 끝나야 한다 (explain-page "강조는 한 지점만")
MAX_VIDEO_SECONDS = 120        # decision-rules.md 예산 — mp4 는 근거의 원본
# GIF 는 기본이 영상 전체다 (decision-rules.md "영상과 GIF 의 역할").
# 길이 상한은 두지 않고 용량만 본다 — 인라인 재생되는 유일한 형태라 자르면 흐름이 끊긴다.
MAX_GIF_KB = 8 * 1024
MAX_IMAGE_KB = 4 * 1024
MAX_VIDEO_KB = 40 * 1024
def taxonomy_candidates():
    """attach 스킬의 taxonomy.json 위치. 프로젝트/유저 레벨 설치를 모두 지원한다."""
    rel = Path("verify-media-attach") / "references" / "taxonomy.json"
    return [
        Path(__file__).resolve().parent.parent.parent / rel,   # 형제 스킬 (같은 레벨 설치)
        Path(".claude/skills") / rel,                          # 프로젝트 설치
        Path.home() / ".claude" / "skills" / rel,               # 유저 레벨 설치
    ]


def load_taxonomy():
    for cand in taxonomy_candidates():
        try:
            return json.loads(cand.read_text())
        except (OSError, ValueError):
            continue
    return None


def mpath(d):
    return Path(d) / "manifest.json"


def read_manifest(d, allow_attached=False):
    p = mpath(d)
    if not p.exists() and allow_attached:
        alt = Path(d) / "manifest.attached.json"
        if alt.exists():
            return json.loads(alt.read_text())
    if not p.exists():
        sys.exit(f"ERROR: manifest 없음: {p} — 먼저 `manifest.py init` 실행")
    return json.loads(p.read_text())


def write_manifest(d, data):
    """임시 파일 → rename 으로 원자적 교체. 중간에 죽어도 반쪽 manifest 가 남지 않는다."""
    p = mpath(d)
    p.parent.mkdir(parents=True, exist_ok=True)
    tmp = p.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    os.replace(tmp, p)
    return p


class ManifestLock:
    """add/remove 의 read-modify-write 를 직렬화한다.

    여러 에이전트가 같은 slug 에 동시에 add 하면 마지막 쓰기만 남아 앞의 항목이 사라진다.
    """

    def __init__(self, d):
        self.path = Path(d) / ".manifest.lock"

    def __enter__(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.fh = open(self.path, "w")
        try:
            fcntl.flock(self.fh, fcntl.LOCK_EX)
        except OSError:
            pass          # 잠금 미지원 파일시스템이면 그냥 진행한다
        return self

    def __exit__(self, *exc):
        try:
            fcntl.flock(self.fh, fcntl.LOCK_UN)
        except OSError:
            pass
        self.fh.close()
        return False


def dimensions_of(path):
    """이미지/GIF 의 (폭, 높이). 본문 렌더 폭을 정하는 데 쓴다."""
    try:
        from PIL import Image
        with Image.open(path) as im:
            return im.size
    except Exception:
        return None


def text_height_of(path, edge_thr=40):
    """이미지 안 글자의 높이(px)와 잡힌 글줄 수. 본문 렌더 폭을 시인성으로 정하는 데 쓴다.

    render_section 은 이 값으로 "본문에서 글자가 약 10px 로 보이는 폭" 을 역산한다 —
    글자가 큰 이미지는 좁게, 글자가 작은(빽빽한) 이미지는 넓게 붙는다.

    측정 방법 (PIL 만 사용):
    - 가로로 1px 옮긴 그림과의 차이로 세로 획을 찾는다. 글자는 세로 획이 많고 배경·가로선은 없다
    - 행마다 획 비율을 재고, 비율이 높은 연속 행을 한 글줄로 묶는다 (2행 이하 끊김은 같은 줄)
    - 글줄 높이의 **잉크 양 가중 중앙값**을 글자 높이로 본다 — 아이콘 조각처럼 짧고 옅은 묶음이
      표를 적게 받는다. 5px 미만 · 120px 초과 묶음은 글줄이 아니라고 보고 뺀다
    - 글줄이 3개 미만이면 None — 표본이 적어 믿을 수 없다 (스낵바 한 줄 크롭 등)
    """
    try:
        from PIL import Image, ImageChops
        with Image.open(path) as src:
            im = src.convert("L")
    except Exception:
        return None, 0
    w, h = im.size
    if w < 40 or h < 40:
        return None, 0
    edges = ImageChops.difference(im, ImageChops.offset(im, 1, 0)).point(
        lambda v: 255 if v > edge_thr else 0)
    frac = [r / 255.0 for r in edges.resize((1, h), Image.BOX).getdata()]
    peak = sorted(frac)[int(len(frac) * 0.95)]
    thr = max(0.006, peak * 0.25)
    runs, cur, gap, mass = [], 0, 0, 0.0
    for f in frac:
        if f > thr:
            if cur and gap:
                cur += gap
            gap = 0
            cur += 1
            mass += f
        elif cur:
            gap += 1
            if gap > 2:
                runs.append((cur, mass))
                cur, mass, gap = 0, 0.0, 0
    if cur:
        runs.append((cur, mass))
    cand = sorted((r, m) for r, m in runs if 5 <= r <= 120)
    if len(cand) < 3:
        return None, len(cand)
    total, acc = sum(m for _, m in cand), 0.0
    for r, m in cand:
        acc += m
        if acc >= total / 2:
            return r, len(cand)
    return cand[-1][0], len(cand)


def duration_of(path):
    """초 단위 길이.

    GIF 는 프레임 지연이 센티초로 양자화되어 ffprobe 가 길이를 과장한다
    (20.0s 로 자른 GIF 를 23.4s 로 읽음). compose_video.sh 가 남긴 `.meta` 사이드카에
    실제 발췌 구간이 있으면 그것을 우선한다.
    """
    meta = Path(str(path) + ".meta")
    if meta.exists():
        try:
            d = json.loads(meta.read_text()).get("duration")
            if d is not None:
                return round(float(d), 2)
        except (OSError, ValueError, TypeError):
            pass
    try:
        r = subprocess.run(
            ["ffprobe", "-v", "error", "-show_entries", "format=duration",
             "-of", "csv=p=0", str(path)],
            capture_output=True, text=True, timeout=20)
        return round(float(r.stdout.strip()), 2) if r.returncode == 0 else None
    except (OSError, ValueError, subprocess.SubprocessError):
        return None


def git(*args):
    try:
        return subprocess.run(["git", *args], capture_output=True, text=True,
                              timeout=10).stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return ""


def cmd_init(a):
    # 재생성 때 init 을 다시 부르면 Gate E 에서 "유지"로 남긴 항목이 전부 날아간다.
    # 다른 에이전트가 같은 slug 를 쓰고 있을 때도 그 항목을 지워버린다.
    if mpath(a.dir).exists() and not a.force:
        sys.exit(f"ERROR: manifest 가 이미 있다: {mpath(a.dir)}\n"
                 f"  재생성이면 init 하지 말고 `remove` 로 대체 항목만 지우고 `add` 할 것 (Gate E).\n"
                 f"  다른 작업과 섞이는 것이면 다른 --slug 를 쓸 것.\n"
                 f"  정말 처음부터 새로 만들려면 --force.")
    if not SLUG_RE.match(a.slug):
        sys.exit(f"ERROR: slug 형식 위반(영숫자/.-_ 64자): {a.slug}")
    for name, val in (("service", a.service), ("feature", a.feature)):
        if not KEBAB_RE.match(val):
            sys.exit(f"ERROR: {name} 는 kebab-case 여야 한다: {val}")

    tax = load_taxonomy()
    if tax:
        if a.service not in tax["services"]:
            sys.exit(f"ERROR: 알 수 없는 service '{a.service}'. "
                     f"가능: {', '.join(tax['services'])}")
        known = tax["services"][a.service]
        if a.feature not in known:
            print(f"WARN: '{a.feature}' 는 {a.service} 의 표준 feature 목록에 없다. "
                  f"오타가 아니면 taxonomy.json 에 추가할 것. 가능: {', '.join(known)}",
                  file=sys.stderr)

    data = {
        "schema": 1,
        "slug": a.slug,
        "service": a.service,
        "feature": a.feature,
        "created": datetime.now(KST).isoformat(timespec="seconds"),
        "device": a.device or "",
        "branch": a.branch or git("rev-parse", "--abbrev-ref", "HEAD"),
        "commit": git("rev-parse", "--short", "HEAD"),
        "items": [],
    }
    print(write_manifest(a.dir, data))


def cmd_add(a):
    with ManifestLock(a.dir):
        return _add_locked(a)


def _add_locked(a):
    data = read_manifest(a.dir)
    if not a.caption or not a.caption.strip():
        sys.exit("ERROR: --caption 필수 — PR 본문에 들어가는 유일한 설명이다")
    base = Path(a.dir)
    for label, rel in (("file", a.file), ("gif", a.gif)):
        if rel and not (base / rel).exists():
            sys.exit(f"ERROR: {label} 이 manifest 디렉토리에 없음: {base / rel}")
    if a.kind == "video" and not a.gif:
        print("WARN: 영상에 --gif 가 없다. GHE PR 본문은 <video> 태그를 제거하므로 "
              "GIF 없이는 인라인 재생이 안 된다(링크만 남는다).", file=sys.stderr)
    if any(i["file"] == a.file for i in data["items"]):
        sys.exit(f"ERROR: 이미 등록된 파일: {a.file}")

    item = {"kind": a.kind, "file": a.file, "caption": a.caption.strip(),
            "compare": bool(a.compare)}
    if a.panels:
        # render_section 이 본문 하한을 패널당 240px 로 잡는 데 쓴다. 없으면 compare 는 2단으로 본다.
        if a.panels < 1:
            sys.exit("ERROR: --panels 는 1 이상")
        if a.panels > 1 and not a.compare:
            print("WARN: --panels 가 2 이상인데 --compare 가 없다. 격자(grid)면 그대로 두고, "
                  "AS-IS/TO-BE 비교면 --compare 를 붙일 것", file=sys.stderr)
        item["panels"] = a.panels
    if a.wide:
        item["display"] = "wide"   # 본문 최대폭으로 붙인다 (기본은 그보다 좁다)
    # role 은 검증 결과가 아닌 것을 가른다. 둘 다 하단 "## 검증" 이 아니라 본문 상단의
    # 해당 섹션에 들어간다 — 거기서 읽어야 맥락이 맞다.
    #   spec    = 근거 자료(기획 스펙·API 계약·이슈 재현 조건). 펼친 채로 들어간다
    #   explain = 변경 자체를 그린 설명 그림. 접힌 토글로 들어간다 (본문 흐름을 밀어내지 않게)
    if a.role and a.role != "verify":
        if a.role == "explain" and a.kind != "image":
            sys.exit("ERROR: role=explain 은 이미지만 — 설명 그림은 한 장으로 읽혀야 한다")
        item["role"] = a.role
        item["section"] = a.section or DEFAULT_SECTION[a.role]
    if a.kind == "image":
        dim = dimensions_of(base / a.file)
        if dim:
            item["width"], item["height"] = dim
        # 글자 높이를 재 둔다. render_section 이 이 값으로 폭을 정한다 (--wide 는 그 위의 강제 값)
        text_px, lines = text_height_of(base / a.file)
        if text_px:
            item["text_px"], item["text_lines"] = text_px, lines
            print(f"  글자 높이 약 {text_px}px ({lines}줄) — 본문 폭은 이 값으로 정해진다", file=sys.stderr)
        else:
            print(f"  글자 줄을 {lines}개만 찾음 — 본문 폭은 비율·기본 상한으로 정해진다", file=sys.stderr)
    if a.kind == "video":
        # 길이를 기록해 두면 render_section 이 "GIF 는 발췌" 를 자동으로 표시할 수 있다
        d = duration_of(base / a.file)
        if d is not None:
            item["duration"] = d
            if d > MAX_VIDEO_SECONDS:
                print(f"WARN: 영상 {d}s > 상한 {MAX_VIDEO_SECONDS}s — "
                      f"핵심 구간만 트림하는 편이 낫다", file=sys.stderr)
    if a.gif:
        item["gif"] = a.gif
        gdim = dimensions_of(base / a.gif)
        if gdim:
            item["gif_width"], item["gif_height"] = gdim
        gd = duration_of(base / a.gif)
        if gd is not None:
            item["gif_duration"] = gd
    data["items"].append(item)
    write_manifest(a.dir, data)
    print(f"added {a.kind} {a.file} (총 {len(data['items'])}개)")


def kb(p):
    return p.stat().st_size / 1024


def item_files(item):
    """한 item 이 소유하는 파일 이름들 (사이드카 포함)."""
    out = []
    for key in ("file", "gif"):
        rel = item.get(key)
        if rel:
            out.append(rel)
            out.append(rel + ".meta")
    return out


# prune 이 지우지 않는 메타 파일. 나머지는 manifest 가 참조하지 않으면 전부 정리 대상이다.
KEEP_ALWAYS = {"manifest.json", "urls.json", ".manifest.lock",
               "states.yaml",     # 진입 시나리오 (replay_states) — 재생성 회차에 재사용한다
               "comment.md"}      # PR 코멘트 초안 (verify-media-attach 가 URL 치환 후 게시)
# 확장자로 남기는 것. 설명 그림의 소스 HTML 은 첨부 대상이 아니지만, 지적을 받아 그림을
# 고칠 때 다시 필요하다 (PNG 만 남으면 처음부터 그려야 한다).
# prune 과 archive 가 같이 지킨다 — archive 가 지우면 prune 이 남긴 뜻이 없어진다.
KEEP_SUFFIXES = (".html",)


def cmd_remove(a):
    """item 을 manifest 에서 빼고 그 파일도 지운다 — 재생성 시 중복 정리용."""
    with ManifestLock(a.dir):
        return _remove_locked(a)


def _remove_locked(a):
    data = read_manifest(a.dir)
    base = Path(a.dir)
    target = Path(a.file).name
    hit = next((i for i in data["items"] if Path(i["file"]).name == target), None)
    if hit is None:
        sys.exit(f"ERROR: manifest 에 없는 파일: {a.file}\n"
                 f"등록된 것: {', '.join(Path(i['file']).name for i in data['items']) or '(없음)'}")
    data["items"] = [i for i in data["items"] if i is not hit]
    write_manifest(a.dir, data)

    removed = []
    if not a.keep_files:
        for rel in item_files(hit):
            f = base / rel
            if f.exists():
                f.unlink()
                removed.append(rel)
    print(f"removed {target} from manifest (남은 {len(data['items'])}개)"
          + (f" / 파일 삭제: {', '.join(removed)}" if removed else ""))


def cmd_prune(a):
    """manifest 가 참조하지 않는 파일을 정리한다.

    원본(raw-*), 검수용 프레임(_frames/), 이전 회차의 합성 결과가 남아 있으면
    "PR 에 첨부할 것만 남는다"는 상태가 깨진다. 그걸 맞춘다.
    """
    data = read_manifest(a.dir)
    base = Path(a.dir)
    keep = set(KEEP_ALWAYS)
    for item in data["items"]:
        keep.update(Path(r).name for r in item_files(item))

    victims_f = sorted(f for f in base.iterdir()
                       if f.is_file() and f.name not in keep
                       and not f.name.endswith(KEEP_SUFFIXES)
                       and not f.name.startswith("."))
    victims_d = sorted(d for d in base.iterdir() if d.is_dir())

    if not victims_f and not victims_d:
        print(f"OK: 정리할 것 없음 — 첨부 대상 {len(data['items'])}개만 남아 있다")
        return

    total = 0.0
    for f in victims_f:
        total += kb(f)
        print(f"{'[dry-run] ' if a.dry_run else ''}delete {f.name} ({kb(f):.0f}KB)")
        if not a.dry_run:
            f.unlink()
    for d in victims_d:
        print(f"{'[dry-run] ' if a.dry_run else ''}delete {d.name}/ (디렉토리)")
        if not a.dry_run:
            shutil.rmtree(d)
    print(f"{'[dry-run] ' if a.dry_run else ''}정리 {len(victims_f)}개 파일 "
          f"+ {len(victims_d)}개 디렉토리 ({total / 1024:.1f}MB), "
          f"첨부 대상 {len(data['items'])}개 유지")


ATTACHED = "manifest.attached.json"


def cmd_archive(a):
    """PR 첨부가 끝난 세트를 정리한다.

    미디어 파일은 storage 레포에 있고 PR 본문이 커밋 SHA 로 그것을 가리키므로 로컬 사본은
    복구 가능하다. 지워서 워킹트리를 깨끗하게 둔다.

    manifest 는 `manifest.attached.json` 으로 이름을 바꿔 남긴다. 두 가지를 동시에 만족한다:
      - `sets.sh list` 는 manifest.json 만 찾으므로 이 세트를 더 이상 첨부 대상으로 보지 않는다
        (파일이 없어 validate 가 실패하는 상태로 남으면 다음 attach 가 막힌다)
      - 재생성 시 Gate E 가 "이전에 무엇을 어떤 캡션으로 붙였는지" 를 여기서 읽는다
    """
    data = read_manifest(a.dir)
    base = Path(a.dir)
    keep = {"manifest.json", "urls.json", ATTACHED, ".manifest.lock"}
    # 미디어는 storage 레포에 있어 지워도 되지만, 소스 HTML 은 올라가지 않아 유일본이다.
    victims = sorted(f for f in base.iterdir()
                     if f.is_file() and f.name not in keep
                     and not f.name.endswith(KEEP_SUFFIXES))
    dirs = sorted(d for d in base.iterdir() if d.is_dir())

    total = sum(kb(f) for f in victims)
    for f in victims:
        print(f"{'[dry-run] ' if a.dry_run else ''}delete {f.name} ({kb(f):.0f}KB)")
        if not a.dry_run:
            f.unlink()
    for d in dirs:
        print(f"{'[dry-run] ' if a.dry_run else ''}delete {d.name}/")
        if not a.dry_run:
            shutil.rmtree(d)

    if not a.dry_run:
        data["attached_at"] = datetime.now(KST).isoformat(timespec="seconds")
        write_manifest(a.dir, data)
        os.replace(mpath(a.dir), base / ATTACHED)
    print(f"{'[dry-run] ' if a.dry_run else ''}미디어 {len(victims)}개 삭제 "
          f"({total / 1024:.1f}MB), {ATTACHED} 로 이력 보존")


def cmd_validate(a):
    data = read_manifest(a.dir)
    base = Path(a.dir)
    errors, warns = [], []

    for key in ("slug", "service", "feature", "items"):
        if key not in data:
            errors.append(f"manifest 에 '{key}' 누락")
    if errors:
        for e in errors:
            print(f"FAIL: {e}")
        sys.exit(1)

    if not data["items"]:
        errors.append("items 가 비어 있다 — 업로드할 미디어가 없다")
    verify_n = sum(1 for i in data["items"] if i.get("role", "verify") == "verify")
    if verify_n > MAX_ITEMS:
        warns.append(f"검증 미디어 {verify_n}개 > 권장 {MAX_ITEMS}개 — "
                     f"리뷰어가 다 보지 않는다 (role=spec·explain 은 예산에서 제외)")
    explain_n = sum(1 for i in data["items"] if i.get("role") == "explain")
    if explain_n > MAX_EXPLAIN:
        warns.append(f"설명 그림 {explain_n}장 > 권장 {MAX_EXPLAIN}장 — "
                     f"변경 하나를 여러 장으로 쪼개면 무엇이 요점인지 흐려진다")

    for i, item in enumerate(data["items"], 1):
        for key in ("kind", "file", "caption"):
            if not item.get(key):
                errors.append(f"item#{i}: '{key}' 누락")
        if item.get("kind") not in ("image", "video"):
            errors.append(f"item#{i}: kind 는 image|video (현재 {item.get('kind')})")
        f = base / item.get("file", "")
        if not f.exists() or f.stat().st_size == 0:
            errors.append(f"item#{i}: 파일 없음/빈 파일 — {f}")
            continue
        size = kb(f)
        if item["kind"] == "image" and size > MAX_IMAGE_KB:
            warns.append(f"item#{i}: 이미지 {size:.0f}KB > {MAX_IMAGE_KB}KB")
        if item["kind"] == "video":
            if size > MAX_VIDEO_KB:
                warns.append(f"item#{i}: 영상 {size:.0f}KB > {MAX_VIDEO_KB}KB")
            d = item.get("duration")
            if d and d > MAX_VIDEO_SECONDS:
                warns.append(f"item#{i}: 영상 {d}s > 상한 {MAX_VIDEO_SECONDS}s")
            gd = item.get("gif_duration")
            if gd and d and d - gd > 1:
                warns.append(f"item#{i}: GIF 가 {gd}s 로 영상({d}s)의 일부만 담는다 "
                             f"— 의도한 발췌가 아니면 --ss/--to 없이 다시 만들 것")
            g = item.get("gif")
            if not g:
                warns.append(f"item#{i}: GIF 없음 — 본문에서 인라인 재생 불가")
            else:
                gp = base / g
                if not gp.exists() or gp.stat().st_size == 0:
                    errors.append(f"item#{i}: GIF 없음/빈 파일 — {gp}")
                elif kb(gp) > MAX_GIF_KB:
                    warns.append(f"item#{i}: GIF {kb(gp):.0f}KB > {MAX_GIF_KB}KB")

    for w in warns:
        print(f"WARN: {w}")
    for e in errors:
        print(f"FAIL: {e}")
    if errors:
        sys.exit(1)
    print(f"OK: {len(data['items'])}개 미디어, "
          f"{data['service']}/{data['feature']}/{data['slug']}")


def cmd_show(a):
    data = read_manifest(a.dir, allow_attached=True)   # 첨부 완료 이력도 읽는다
    print(json.dumps(data, ensure_ascii=False, indent=2))


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("command",
                    choices=["init", "add", "validate", "show", "remove",
                             "prune", "archive"])
    ap.add_argument("--dir", required=True)
    ap.add_argument("--slug")
    ap.add_argument("--service")
    ap.add_argument("--feature")
    ap.add_argument("--device")
    ap.add_argument("--branch")
    ap.add_argument("--kind", choices=["image", "video"])
    ap.add_argument("--file")
    ap.add_argument("--gif")
    ap.add_argument("--caption")
    ap.add_argument("--compare", action="store_true",
                    help="AS-IS/TO-BE 비교. 본문 하한이 2단(480px)으로 잡힌다")
    ap.add_argument("--panels", type=int,
                    help="합성 패널 수. 본문 하한 = 240px × 패널 수 (refrow 3단은 3, "
                         "2x2 격자는 가로 패널 수 2). 생략하면 --compare 는 2, 아니면 1")
    ap.add_argument("--wide", action="store_true",
                    help="본문에서 최대폭(850px)으로 붙인다. 폭은 보통 이미지 안 글자 높이로 "
                         "자동 결정되므로, 그 결과가 맞지 않을 때만 강제한다")
    ap.add_argument("--role", choices=["verify", "spec", "explain"], default="verify",
                    help="spec = 근거 자료(펼침), explain = 변경 설명 그림(접힌 토글). "
                         "둘 다 본문 상단 해당 섹션에 배치된다")
    ap.add_argument("--section",
                    help="role=spec·explain 이 들어갈 섹션 제목 "
                         "(기본 spec='이슈 정보', explain='해결')")
    ap.add_argument("--keep-files", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--force", action="store_true",
                    help="init: 기존 manifest 를 버리고 새로 만든다")
    a = ap.parse_args()

    if a.command == "init":
        for k in ("slug", "service", "feature"):
            if not getattr(a, k):
                sys.exit(f"ERROR: init 은 --{k} 필요")
    if a.command == "add":
        for k in ("kind", "file", "caption"):
            if not getattr(a, k):
                sys.exit(f"ERROR: add 는 --{k} 필요")
    if a.command == "remove" and not a.file:
        sys.exit("ERROR: remove 는 --file 필요")

    {"init": cmd_init, "add": cmd_add, "validate": cmd_validate, "show": cmd_show,
     "remove": cmd_remove, "prune": cmd_prune, "archive": cmd_archive}[a.command](a)


if __name__ == "__main__":
    main()
