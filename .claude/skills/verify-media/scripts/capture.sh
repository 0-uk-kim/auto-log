#!/usr/bin/env bash
# 검증 미디어 캡처 — adb 원시 명령 래퍼.
#
# 왜 스크립트인가: screenrecord 는 백그라운드 실행(&)이 필요한데, 복합 Bash 한 줄은
# 정적 분석이 안 돼 매번 권한 프롬프트가 뜬다. 스크립트 안으로 감추면 호출부는 단순 명령이 된다.
#
# 사용법:
#   capture.sh probe                                  디바이스/앱 상태 확인
#   capture.sh launch [--pkg P] [--activity A]        앱 명시 실행 (monkey 금지)
#   capture.sh shot <out.png>                         스크린샷 1장
#   capture.sh rec-start <tag> [--limit 30] [--size WxH] [--bitrate 8000000]
#   capture.sh rec-stop  <tag> <out.mp4>              녹화 종료 + pull
#   capture.sh rec-abort <tag>                        녹화 폐기
set -euo pipefail

PKG="${VERIFY_MEDIA_PKG:-com.nhn.android.calendar.alpha}"
ACTIVITY="${VERIFY_MEDIA_ACTIVITY:-com.nhn.android.calendar.feature.main.base.ui.MainActivity}"
SERIAL="${ANDROID_SERIAL:-}"
STATE_DIR="${TMPDIR:-/tmp}/verify-media-rec"
mkdir -p "$STATE_DIR"

adb_() {
  if [ -n "$SERIAL" ]; then adb -s "$SERIAL" "$@"; else adb "$@"; fi
}

die() { echo "ERROR: $*" >&2; exit 1; }

require_device() {
  local n
  n=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
  [ "$n" -ge 1 ] || die "연결된 디바이스 없음. adb devices 확인."
  if [ "$n" -gt 1 ] && [ -z "$SERIAL" ]; then
    die "디바이스 $n 대 연결됨. ANDROID_SERIAL 로 지정할 것."
  fi
}

cmd_probe() {
  require_device
  local model rel dens size installed
  model=$(adb_ shell getprop ro.product.model | tr -d '\r')
  rel=$(adb_ shell getprop ro.build.version.release | tr -d '\r')
  dens=$(adb_ shell wm density | tr -d '\r' | awk -F': ' '{print $2}' | tail -1)
  size=$(adb_ shell wm size | tr -d '\r' | awk -F': ' '{print $2}' | tail -1)
  installed=$(adb_ shell pm list packages "$PKG" | tr -d '\r')
  echo "device=$(adb_ get-serialno | tr -d '\r')"
  echo "model=$model"
  echo "android=$rel"
  echo "size=$size"
  echo "density=$dens"
  echo "package=${installed:-NOT_INSTALLED}"
}

cmd_launch() {
  require_device
  # monkey -c LAUNCHER 는 직전 task 를 되살려 엉뚱한 화면이 뜬다. 항상 컴포넌트 명시.
  adb_ shell am start -W -n "$PKG/$ACTIVITY" >/dev/null 2>&1 || die "앱 실행 실패: $PKG/$ACTIVITY"
  sleep 2
  echo "launched=$PKG/$ACTIVITY"
}

cmd_shot() {
  local out="${1:-}"
  [ -n "$out" ] || die "출력 경로 필요: capture.sh shot <out.png>"
  require_device
  mkdir -p "$(dirname "$out")"
  adb_ exec-out screencap -p > "$out"
  [ -s "$out" ] || die "스크린샷이 비어 있음: $out"
  # PNG 매직바이트 확인 — exec-out 은 실패해도 exit 0 인 경우가 있다
  head -c 8 "$out" | od -An -tx1 | tr -d ' \n' | grep -q '^89504e470d0a1a0a' || die "PNG 아님: $out"
  echo "$out"
}

cmd_rec_start() {
  local tag="${1:-}"; shift || true
  [ -n "$tag" ] || die "태그 필요: capture.sh rec-start <tag>"
  local limit=30 size="" bitrate=8000000
  while [ $# -gt 0 ]; do
    case "$1" in
      --limit)   limit="$2"; shift 2 ;;
      --size)    size="$2"; shift 2 ;;
      --bitrate) bitrate="$2"; shift 2 ;;
      *) die "알 수 없는 인자: $1" ;;
    esac
  done
  require_device
  local remote="/sdcard/vm_${tag}.mp4"
  adb_ shell rm -f "$remote" >/dev/null 2>&1 || true
  local args="--time-limit $limit --bit-rate $bitrate"
  [ -n "$size" ] && args="$args --size $size"
  adb_ shell "screenrecord $args $remote" >/dev/null 2>&1 &
  echo "$!" > "$STATE_DIR/$tag.pid"
  echo "$remote" > "$STATE_DIR/$tag.remote"
  sleep 1.5   # 녹화가 실제로 시작되기까지의 지연. 이 전에 조작하면 앞부분이 잘린다.
  # 방금 부팅한 에뮬레이터는 boot_completed 직후에도 느려서 1.5초 안에 못 뜬다(실측). 폴링한다.
  w=0
  until adb_ shell pidof screenrecord >/dev/null 2>&1; do
    w=$((w+1)); [ "$w" -gt 16 ] && die "screenrecord 시작 실패 (디바이스가 아직 준비되지 않았을 수 있음)"
    sleep 0.5
  done
  echo "recording=$tag limit=${limit}s remote=$remote"
}

cmd_rec_stop() {
  local tag="${1:-}" out="${2:-}"
  [ -n "$tag" ] && [ -n "$out" ] || die "사용법: capture.sh rec-stop <tag> <out.mp4>"
  require_device
  local remote
  remote=$(cat "$STATE_DIR/$tag.remote" 2>/dev/null || echo "/sdcard/vm_${tag}.mp4")
  # SIGINT 로 끝내야 moov atom 이 기록된다. SIGKILL 이면 재생 불가 파일이 남는다.
  adb_ shell 'pkill -INT screenrecord' >/dev/null 2>&1 \
    || adb_ shell 'killall -INT screenrecord' >/dev/null 2>&1 || true
  local i=0
  while adb_ shell pidof screenrecord >/dev/null 2>&1; do
    i=$((i+1)); [ "$i" -gt 20 ] && break; sleep 0.5
  done
  sleep 1   # 파일 flush 대기
  mkdir -p "$(dirname "$out")"
  adb_ pull "$remote" "$out" >/dev/null 2>&1 || die "pull 실패: $remote"
  adb_ shell rm -f "$remote" >/dev/null 2>&1 || true
  rm -f "$STATE_DIR/$tag.pid" "$STATE_DIR/$tag.remote"
  [ -s "$out" ] || die "녹화 파일이 비어 있음: $out"
  ffprobe -v error -show_entries format=duration -of csv=p=0 "$out" >/dev/null 2>&1 \
    || die "재생 불가 파일 — SIGINT 종료 실패로 추정: $out"
  local dur
  dur=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$out")
  echo "$out (${dur}s)"
}

cmd_rec_abort() {
  local tag="${1:-}"
  require_device
  adb_ shell 'pkill -INT screenrecord' >/dev/null 2>&1 || true
  adb_ shell rm -f "/sdcard/vm_${tag}.mp4" >/dev/null 2>&1 || true
  rm -f "$STATE_DIR/$tag.pid" "$STATE_DIR/$tag.remote"
  echo "aborted=$tag"
}

case "${1:-}" in
  probe)     shift; cmd_probe "$@" ;;
  launch)    shift; cmd_launch "$@" ;;
  shot)      shift; cmd_shot "$@" ;;
  rec-start) shift; cmd_rec_start "$@" ;;
  rec-stop)  shift; cmd_rec_stop "$@" ;;
  rec-abort) shift; cmd_rec_abort "$@" ;;
  *) sed -n '2,16p' "$0"; exit 1 ;;
esac
