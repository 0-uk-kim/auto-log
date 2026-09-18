#!/usr/bin/env python3
"""states.yaml 의 영역별 진입 시나리오를 재생하고 캡처한다.

이 스킬의 핵심 레버다. **같은 시나리오를 AS-IS 빌드와 TO-BE 빌드에서 두 번 재생**해야
비교가 성립하는데, 사람이 두 번 똑같이 조작하는 것은 불가능하다. 그래서 조작을 데이터로 적는다.

좌표 대신 **화면의 텍스트/id 로 요소를 찾는 것을 기본**으로 한다(uiautomator dump 로 런타임 해소).
- 사람이 좌표를 재서 적을 필요가 없다 → states.yaml 을 스킬이 스스로 만들 수 있다
- density 를 바꿔도 안 깨진다 (반응형 검수에서 좌표를 두 벌 관리하던 문제가 사라진다)
좌표(`[x, y]`)도 계속 받는다 — 텍스트가 없는 핸들·썸 같은 것 때문이다.

subcommands
  capture       --states S --area ID --out PNG        한 영역 재생 + 캡처
  prepare       --states S                            권한 부여 + 알림 배너 차단
  teardown      --states S                            demo mode·density 원복
  dump                                                지금 화면의 탭 가능한 요소 목록 (탐색용)
  find          --text T | --id I | --desc D          선택자가 실제로 잡히는지 확인
  init          --base REF --out YAML                 검수 커밋에서 states.yaml 초안 생성
  await-install --states S [--timeout 1800]           설치가 바뀔 때까지 대기 (AS-IS 빌드 기다리기)
  reset                                               density 원복만
"""
import argparse
import hashlib
import pathlib
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

import yaml

BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def sh(serial, *args, check=True):
    cmd = ["adb"] + (["-s", serial] if serial else []) + list(args)
    r = subprocess.run(cmd, capture_output=True, text=True)
    if check and r.returncode != 0:
        sys.exit(f"ERROR: {' '.join(cmd)}\n{r.stderr.strip()}")
    return r.stdout


def load(path):
    return yaml.safe_load(pathlib.Path(path).read_text())


def area_of(spec, aid):
    for a in spec["areas"]:
        if a["id"] == aid:
            return a
    sys.exit(f"ERROR: area '{aid}' 없음. 있는 것: " + ", ".join(a["id"] for a in spec["areas"]))


# ── 화면 요소 조회 ────────────────────────────────────────────────────────────

def dump_nodes(serial):
    """지금 화면의 요소를 (text, id, desc, center, clickable) 로 뽑는다."""
    sh(serial, "shell", "uiautomator", "dump", "/sdcard/_ui.xml", check=False)
    xml = sh(serial, "shell", "cat", "/sdcard/_ui.xml")
    sh(serial, "shell", "rm", "/sdcard/_ui.xml", check=False)
    start = xml.find("<?xml")
    if start < 0:
        return []
    try:
        root = ET.fromstring(xml[start:])
    except ET.ParseError:
        return []

    out = []
    for n in root.iter("node"):
        m = BOUNDS.match(n.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = (int(v) for v in m.groups())
        if x2 <= x1 or y2 <= y1:
            continue
        out.append({
            "text": (n.get("text") or "").strip(),
            "id": (n.get("resource-id") or "").split("/")[-1],
            "desc": (n.get("content-desc") or "").strip(),
            "clickable": n.get("clickable") == "true",
            "center": ((x1 + x2) // 2, (y1 + y2) // 2),
            "bounds": (x1, y1, x2, y2),
        })
    return out


def resolve(serial, sel, timeout=8.0):
    """선택자로 요소 중심 좌표를 찾는다. 화면이 아직 안 그려졌을 수 있어 잠깐 재시도한다."""
    want_text = sel.get("text")
    want_id = sel.get("id")
    want_desc = sel.get("desc")
    deadline = time.time() + timeout

    while True:
        cands = []
        for n in dump_nodes(serial):
            if want_text and want_text not in n["text"]:
                continue
            if want_id and want_id != n["id"]:
                continue
            if want_desc and want_desc not in n["desc"]:
                continue
            if not (want_text or want_id or want_desc):
                continue
            cands.append(n)
        if cands:
            # 탭 가능한 것을 우선한다 (텍스트 라벨보다 그 부모 버튼이 맞는 경우가 많다)
            cands.sort(key=lambda n: (not n["clickable"],))
            return cands[0]["center"]
        if time.time() > deadline:
            return None
        time.sleep(0.7)


def screencap(serial, dst, trim_top=0):
    sh(serial, "shell", "screencap", "-p", "/sdcard/_drv.png")
    sh(serial, "pull", "/sdcard/_drv.png", str(dst))
    sh(serial, "shell", "rm", "/sdcard/_drv.png", check=False)
    if trim_top:
        # 상태바를 잘라낸다. demo mode 로 시계·배터리를 고정하려 해도 Samsung One UI 등
        # 벤더 SystemUI 에서는 무시된다(실측). AS-IS/TO-BE 를 다른 시각에 찍으면
        # 시계 차이가 그대로 비교 이미지에 남으므로, 아예 빼는 쪽이 확실하다.
        from PIL import Image
        im = Image.open(dst)
        im.crop((0, int(trim_top), im.width, im.height)).save(dst)


# ── steps ────────────────────────────────────────────────────────────────────

def as_point(serial, val, what):
    """[x, y] 는 그대로, {text:…}/{id:…}/{desc:…} 는 화면에서 찾아 좌표로 바꾼다."""
    if isinstance(val, (list, tuple)) and len(val) == 2:
        return int(val[0]), int(val[1])
    if isinstance(val, dict):
        pt = resolve(serial, val)
        if pt is None:
            sys.exit(f"ERROR: {what} 선택자를 화면에서 못 찾음: {val}\n"
                     f"       `replay_states.py dump` 로 지금 화면 요소를 확인할 것")
        return pt
    sys.exit(f"ERROR: {what} 값 형식 오류: {val!r}")


def foreground(serial):
    for line in sh(serial, "shell", "dumpsys", "activity", "activities",
                   check=False).splitlines():
        if "topResumedActivity" in line:
            m = re.search(r"u0 (\S+)", line)
            return m.group(1) if m else ""
    return ""


def launch(serial, pkg, act, wait):
    """앱을 앞으로 띄우고 **실제로 앞에 왔는지 확인**한다.

    `am start -S` 만으로는 부족하다 — 다른 앱(예: 카메라)이 top-most 면
    `Warning: Activity not started, intent has been delivered to currently running
    top-most instance` 가 뜨고 Status 는 ok 인데 포그라운드는 그대로다(실측).
    확인 없이 넘어가면 **엉뚱한 앱 화면을 캡처**하고, 그림이 멀쩡해 보여 눈으로 못 잡는다.
    """
    for attempt in (1, 2):
        sh(serial, "shell", "am", "start", "-S", "-W", "-n", f"{pkg}/{act}", check=False)
        time.sleep(wait)
        deadline = time.time() + 8
        while time.time() < deadline:
            if foreground(serial).startswith(pkg):
                return
            time.sleep(0.7)
        if attempt == 1:
            # top-most 를 치우고 한 번 더. HOME 을 거치면 인텐트가 제대로 새 태스크로 간다.
            sh(serial, "shell", "input", "keyevent", "3", check=False)
            time.sleep(1.5)
    sys.exit(f"ERROR: 앱이 포그라운드로 오지 않았다 (현재: {foreground(serial) or '알 수 없음'})\n"
             f"       기기 화면을 확인할 것 — 다른 앱이 떠 있거나 잠금 상태일 수 있다")


def run_steps(serial, spec, area, out):
    app = spec["app"]
    pkg, act = app["package"], app["activity"]

    for step in area.get("steps", []):
        if isinstance(step, str) and step == "launch":
            launch(serial, pkg, act, step_wait(area, 3.5))
            continue
        if not isinstance(step, dict) or len(step) != 1:
            sys.exit(f"ERROR: step 형식 오류: {step!r}")
        (kind, val), = step.items()
        if kind == "tap":
            x, y = as_point(serial, val, "tap")
            sh(serial, "shell", "input", "tap", str(x), str(y))
            time.sleep(step_wait(area, 2.0))
        elif kind == "swipe":
            if isinstance(val, dict):                     # {from: {...}, dy: -1300, ms: 900}
                x, y = as_point(serial, val["from"], "swipe.from")
                sh(serial, "shell", "input", "swipe", str(x), str(y),
                   str(x + int(val.get("dx", 0))), str(y + int(val.get("dy", 0))),
                   str(int(val.get("ms", 400))))
            else:
                sh(serial, "shell", "input", "swipe", *[str(v) for v in val])
            time.sleep(step_wait(area, 2.0))
        elif kind == "back":
            sh(serial, "shell", "input", "keyevent", "4")
            time.sleep(step_wait(area, 1.5))
        elif kind == "wait":
            time.sleep(float(val))
        elif kind == "wait_for":                          # 화면이 뜰 때까지 (고정 sleep 대신)
            if resolve(serial, val, timeout=float(val.get("timeout", 10))) is None:
                sys.exit(f"ERROR: wait_for 실패 — 화면에 안 나타남: {val}")
        else:
            sys.exit(f"ERROR: 모르는 step '{kind}'")

    trim = int((spec.get("device") or {}).get("status_bar_px", 0))
    cap = area.get("capture", "screen")
    if cap == "screen":
        screencap(serial, out, trim)
    elif cap == "drag":
        drag_capture(serial, area, out, trim)
    else:
        sys.exit(f"ERROR: 모르는 capture '{cap}'")


def step_wait(area, default):
    return float(area.get("step_wait", default))


def drag_capture(serial, area, out, trim_top=0):
    """드래그를 **유지한 채** 캡처한다.

    빠른 스크롤바처럼 '누르고 있는 동안에만 보이는' UI 가 있다. swipe 는 손을 떼므로
    그 상태를 절대 못 찍는다. motionevent 로 DOWN → MOVE… → (캡처) → UP 순으로 간다.
    Compose 의 detectVerticalDragGestures 는 touch slop 을 넘겨야 시작되므로
    첫 MOVE 를 크게 준다.
    """
    d = area["drag"]
    x, y0, y1 = int(d["x"]), int(d["y0"]), int(d["y1"])
    n = int(d.get("steps", 10))
    slop = int(d.get("slop", 60))

    if d.get("fling"):
        # 썸이 '스크롤 중에만' 렌더되는 경우, 먼저 띄워 놓고 사라지기 전에 잡는다.
        sh(serial, "shell", "input", "swipe", *[str(v) for v in d["fling"]])

    sh(serial, "shell", "input", "motionevent", "DOWN", str(x), str(y0))
    sh(serial, "shell", "input", "motionevent", "MOVE", str(x), str(y0 + slop))
    for i in range(1, n + 1):
        y = y0 + slop + (y1 - y0 - slop) * i // n
        sh(serial, "shell", "input", "motionevent", "MOVE", str(x), str(y))
    screencap(serial, out, trim_top)
    sh(serial, "shell", "input", "motionevent", "UP", str(x), str(y1), check=False)


# ── density ──────────────────────────────────────────────────────────────────

def apply_density(serial, spec, area):
    want = area.get("density")
    if not want or want == "default":
        sh(serial, "shell", "wm", "density", "reset")
        time.sleep(2)          # density 변경은 configuration change 다 — 재배치를 기다린다
        return "reset"
    if isinstance(want, dict):                      # {width_dp: 600}
        phys = int(sh(serial, "shell", "wm", "size").strip().split(":")[-1].split("x")[0])
        want = round(phys / int(want["width_dp"]) * 160)
    sh(serial, "shell", "wm", "density", str(int(want)))
    time.sleep(2)
    return str(want)


def installed_at(serial, pkg):
    for line in sh(serial, "shell", "dumpsys", "package", pkg, check=False).splitlines():
        if "lastUpdateTime" in line:
            return line.split("=", 1)[-1].strip()
    return ""


# ── subcommands ──────────────────────────────────────────────────────────────

def cmd_dump(s):
    seen = set()
    for n in dump_nodes(s):
        label = n["text"] or n["desc"] or n["id"]
        if not label or label in seen:
            continue
        seen.add(label)
        kind = "tap" if n["clickable"] else "   "
        sel = f'text: "{n["text"]}"' if n["text"] else (
              f'desc: "{n["desc"]}"' if n["desc"] else f'id: "{n["id"]}"')
        print(f"  {kind}  {n['center'][0]:>5},{n['center'][1]:<5}  {{{sel}}}")


def cmd_init(base, out_path):
    """검수 커밋에서 states.yaml 초안을 만든다. steps 는 스킬이 기기를 보며 채운다."""
    here = pathlib.Path(__file__).resolve().parent
    r = subprocess.run([sys.executable, str(here / "parse_design_commits.py"),
                        "--base", base, "--json"], capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit(f"ERROR: parse_design_commits 실패\n{r.stderr}")
    import json
    data = json.loads(r.stdout)

    # 노드 하나 = 영역 하나. 같은 노드를 가리키는 커밋은 한 영역으로 합친다.
    areas = {}
    for c in data["commits"]:
        for n in c["nodes"]:
            a = areas.setdefault(n["node"], {"node": n["node"], "name": n["name"],
                                             "subjects": [], "files": set()})
            a["subjects"].append(c["subject"])
            a["files"].update(c["files"])

    def slug(n):
        base_ = re.sub(r"[^a-z0-9]+", "-", (n["name"] or "").lower()).strip("-")
        return base_ or "area-" + n["node"].split("-")[0]

    spec = {
        "schema": "verify-media-states/v1",
        "screen": "TODO — 화면 이름",
        "device": {"model": "TODO", "size_px": [0, 0], "density": 0, "status_bar_px": 0},
        "figma": {"file_key": (data["file_keys"] or ["TODO"])[0]},
        "app": {"platform": "android", "package": "TODO", "activity": "TODO",
                "build_task": "TODO", "locale": "ko-KR", "permissions": []},
        "areas": [{
            "id": slug(a),
            "name": a["name"] or a["node"],
            "node": a["node"],
            "caption": a["subjects"][0],
            "files": sorted(a["files"]),
            "density": "default",
            "capture": "screen",
            "steps": ["launch"],
        } for a in areas.values()],
    }
    p = pathlib.Path(out_path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(yaml.safe_dump(spec, allow_unicode=True, sort_keys=False))
    print(f"{p}  영역 {len(spec['areas'])}개 초안 생성")
    print("TODO 필드(device/app)와 각 영역 steps 를 채울 것 — `dump` 로 요소를 찾는다")


def cmd_await_install(s, spec, timeout):
    pkg = spec["app"]["package"]
    before = installed_at(s, pkg)
    print(f"현재 설치본: {before or '(없음)'}")
    print(f"설치가 바뀌기를 기다린다 (최대 {timeout}s)…")
    deadline = time.time() + timeout
    while time.time() < deadline:
        time.sleep(5)
        now = installed_at(s, pkg)
        if now and now != before:
            print(f"설치 감지: {now}")
            return
    sys.exit("ERROR: 시간 안에 설치가 감지되지 않았다")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["capture", "prepare", "teardown", "dump", "find",
                                    "init", "await-install", "reset"])
    ap.add_argument("--states")
    ap.add_argument("--area")
    ap.add_argument("--out")
    ap.add_argument("--serial")
    ap.add_argument("--base")
    ap.add_argument("--text")
    ap.add_argument("--id")
    ap.add_argument("--desc")
    ap.add_argument("--timeout", type=int, default=1800)
    a = ap.parse_args()
    s = a.serial

    if a.cmd == "reset":
        sh(s, "shell", "wm", "density", "reset")
        print("density reset")
        return
    if a.cmd == "dump":
        cmd_dump(s)
        return
    if a.cmd == "find":
        sel = {k: v for k, v in (("text", a.text), ("id", a.id), ("desc", a.desc)) if v}
        pt = resolve(s, sel)
        print(f"{sel} → {pt if pt else 'NOT FOUND'}")
        sys.exit(0 if pt else 1)
    if a.cmd == "init":
        cmd_init(a.base, a.out)
        return

    spec = load(a.states)

    if a.cmd == "await-install":
        cmd_await_install(s, spec, a.timeout)
        return

    if a.cmd == "prepare":
        pkg = spec["app"]["package"]
        for perm in spec["app"].get("permissions", []):
            # 권한 다이얼로그가 뜨면 좌표 조작이 통째로 어긋난다. 미리 부여해 없앤다.
            sh(s, "shell", "pm", "grant", pkg, perm, check=False)
            print(f"granted {perm}")
        # 에뮬레이터는 스냅샷 복원으로 이전 세션의 wm size 오버라이드를 되살린다(실측).
        # 오버라이드가 남으면 좌표·레이아웃이 통째로 어긋나므로 여기서 정리한다.
        if "Override" in sh(s, "shell", "wm", "size", check=False):
            sh(s, "shell", "wm", "size", "reset", check=False)
            print("wm size override 감지 → reset")
        sh(s, "shell", "settings", "put", "global", "heads_up_notifications_enabled", "0",
           check=False)
        print("알림 배너 차단 (상단바 시계는 device.status_bar_px 로 잘라낸다)")
        print("★ 끝나면 teardown 을 반드시 실행할 것")
        return

    if a.cmd == "teardown":
        sh(s, "shell", "settings", "put", "global", "heads_up_notifications_enabled", "1",
           check=False)
        sh(s, "shell", "wm", "density", "reset", check=False)
        print("알림 배너 복구 + density reset 완료")
        return

    area = area_of(spec, a.area)
    out = pathlib.Path(a.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    apply_density(s, spec, area)
    run_steps(s, spec, area, out)
    digest = hashlib.sha1(out.read_bytes()).hexdigest()[:12]
    print(f"{out}  sha1={digest}")
    print("★ 다른 영역과 sha1 이 같으면 진입이 실패해 앞 화면이 찍힌 것이다 — 재확인할 것")


if __name__ == "__main__":
    main()
