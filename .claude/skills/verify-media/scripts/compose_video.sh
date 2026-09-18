#!/usr/bin/env bash
# 검증 영상 가공 — ffmpeg.
#
# 사용법:
#   compose_video.sh info   IN.mp4
#   compose_video.sh trim   IN.mp4 OUT.mp4 --ss 3 --to 11
#   compose_video.sh pair   A.mp4 B.mp4 OUT.mp4 [--labels "AS-IS,TO-BE"]
#   compose_video.sh gif    IN.mp4 OUT.gif [--width 400] [--fps 10] [--ss S] [--to E] [--max-dur N]
#   compose_video.sh frames IN.mp4 OUTDIR [--times "1,3,5"] [--count 6]
set -euo pipefail

GAP=12
MAX_GIF_BYTES=$((8 * 1024 * 1024))     # decision-rules.md 예산
# GIF 는 기본이 전체 길이다. GHE PR 본문은 <video> 를 제거하므로 리뷰어에게 인라인으로
# 재생되는 것은 GIF 뿐이고, 발췌하면 흐름의 일부만 보게 된다.
# 제약은 길이가 아니라 용량이므로 폭·fps 를 낮춰 맞추고, 그래도 안 되면 그때만 자른다.
GIF_TRUNCATE_FLOOR=20                  # 용량 사다리를 다 써도 안 맞을 때의 최후 트림 길이
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

die() { echo "ERROR: $*" >&2; exit 1; }
need() { [ -f "$1" ] || die "파일 없음: $1"; }

probe() { ffprobe -v error -select_streams v:0 -show_entries "$1" -of csv=p=0 "$2" | head -1; }
dur_of()  { probe format=duration "$1"; }
w_of()    { probe stream=width "$1"; }
h_of()    { probe stream=height "$1"; }

even() { python3 -c "import sys;v=int(round(float(sys.argv[1])));print(v-1 if v%2 else v)" "$1"; }

cmd_info() {
  need "$1"
  local d w h kb
  d=$(dur_of "$1"); w=$(w_of "$1"); h=$(h_of "$1")
  kb=$(( $(stat -f%z "$1") / 1024 ))
  echo "$1 ${w}x${h} ${d}s ${kb}KB"
}

cmd_trim() {
  local in="$1" out="$2"; shift 2
  need "$in"
  local ss="" to=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --ss) ss="$2"; shift 2 ;;
      --to) to="$2"; shift 2 ;;
      *) die "알 수 없는 인자: $1" ;;
    esac
  done
  [ -n "$ss" ] || die "--ss 필요"
  mkdir -p "$(dirname "$out")"
  local args=(-y -v error -ss "$ss")
  [ -n "$to" ] && args+=(-to "$to")
  # 재인코딩한다 — copy 는 키프레임 경계로 잘려 시작점이 밀린다
  ffmpeg "${args[@]}" -i "$in" -c:v libx264 -preset veryfast -crf 23 \
         -pix_fmt yuv420p -movflags +faststart -an "$out"
  cmd_info "$out"
}

cmd_pair() {
  local a="$1" b="$2" out="$3"; shift 3
  need "$a"; need "$b"
  local labels=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --labels) labels="$2"; shift 2 ;;
      *) die "알 수 없는 인자: $1" ;;
    esac
  done
  mkdir -p "$(dirname "$out")"

  local da db dmax pa pb ha hb H wa wb WA WB TOTW
  da=$(dur_of "$a"); db=$(dur_of "$b")
  dmax=$(python3 -c "print(max($da,$db))")
  pa=$(python3 -c "print(round(max(0.0,$dmax-$da),3))")
  pb=$(python3 -c "print(round(max(0.0,$dmax-$db),3))")
  ha=$(h_of "$a"); hb=$(h_of "$b"); wa=$(w_of "$a"); wb=$(w_of "$b")
  H=$(even "$(python3 -c "print(min($ha,$hb))")")
  WA=$(even "$(python3 -c "print($wa*$H/$ha)")")
  WB=$(even "$(python3 -c "print($wb*$H/$hb)")")
  TOTW=$((WA + GAP + WB))

  # 짧은 쪽은 마지막 프레임을 복제해 길이를 맞춘다(clone). 안 맞추면 hstack 이 짧은 쪽에서 끝난다.
  local fc="[0:v]tpad=stop_mode=clone:stop_duration=${pa},scale=${WA}:${H},setsar=1,pad=$((WA+GAP)):${H}:0:0:color=white[l];"
  fc+="[1:v]tpad=stop_mode=clone:stop_duration=${pb},scale=${WB}:${H},setsar=1[r];"
  fc+="[l][r]hstack=inputs=2[v0];"

  if [ -n "$labels" ]; then
    local bar="${TMPDIR:-/tmp}/vm_labelbar_$$.png"
    python3 "$SCRIPT_DIR/compose_image.py" labelbar -o "$bar" --width "$TOTW" --labels "$labels" >/dev/null
    local SH
    SH=$(python3 -c "from PIL import Image;print(Image.open('$bar').height)")
    # vstack + `-loop 1` 조합은 이미지 입력이 끝나지 않아 인코딩이 멈추지 않는다.
    # 대신 위쪽을 pad 로 비우고 단일 프레임을 overlay(eof_action=repeat) 한다.
    fc+="[v0]pad=${TOTW}:$((H+SH)):0:${SH}:color=white[v1];"
    fc+="[2:v]scale=${TOTW}:-2,setsar=1[lab];"
    fc+="[v1][lab]overlay=0:0:eof_action=repeat[v]"
    ffmpeg -y -v error -i "$a" -i "$b" -i "$bar" -filter_complex "$fc" \
           -map "[v]" -c:v libx264 -preset veryfast -crf 23 -pix_fmt yuv420p \
           -movflags +faststart -an "$out"
    rm -f "$bar"
  else
    fc="${fc%;}"
    ffmpeg -y -v error -i "$a" -i "$b" -filter_complex "$fc" \
           -map "[v0]" -c:v libx264 -preset veryfast -crf 23 -pix_fmt yuv420p \
           -movflags +faststart -an "$out"
  fi
  cmd_info "$out"
}

make_gif() {
  local in="$1" out="$2" width="$3" fps="$4" ss="$5" to="$6"
  local pre=(-y -v error)
  [ -n "$ss" ] && pre+=(-ss "$ss")
  [ -n "$to" ] && pre+=(-to "$to")
  ffmpeg "${pre[@]}" -i "$in" -vf \
    "fps=${fps},scale=${width}:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=bayer:bayer_scale=3" \
    -loop 0 "$out"
}

write_gif_meta() {
  local out="$1" dur="$2" width="$3" fps="$4"
  printf '{"duration": %s, "width": %s, "fps": %s}\n' "$dur" "$width" "$fps" > "${out}.meta"
}

cmd_gif() {
  local in="$1" out="$2"; shift 2
  need "$in"
  local width=400 fps=10 ss="" to="" maxdur=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --width)    width="$2"; shift 2 ;;
      --fps)      fps="$2"; shift 2 ;;
      --ss)       ss="$2"; shift 2 ;;
      --to)       to="$2"; shift 2 ;;
      --max-dur)  maxdur="$2"; shift 2 ;;
      *) die "알 수 없는 인자: $1" ;;
    esac
  done
  mkdir -p "$(dirname "$out")"

  # 기본은 전체 길이다 (decision-rules.md "영상과 GIF 의 역할").
  # 구간을 좁히는 것은 호출자가 --ss/--to 를 준 경우뿐이고, --max-dur 는 명시 상한이다.
  local src_dur win
  src_dur=$(dur_of "$in")
  window_of() {
    python3 -c "
ss = float('${ss:-0}')
to = '${to}'
end = float(to) if to else float('$src_dur')
print(round(max(0.0, end - ss), 3))"
  }
  win=$(window_of)
  if [ -n "$maxdur" ] && python3 -c "import sys; sys.exit(0 if float('$win') > float('$maxdur') else 1)"; then
    to=$(python3 -c "print(round(float('${ss:-0}') + float('$maxdur'), 3))")
    echo "INFO: --max-dur ${maxdur}s 로 ${ss:-0}s~${to}s 만 담는다." >&2
    win=$(window_of)
  fi

  # 길이가 아니라 용량이 제약이다. 폭·fps 를 단계적으로 낮춰 8MB 안에 전체를 담는다.
  local ladder=("$width $fps" "$width 8" "420 8" "360 8" "320 6" "280 5")
  local step size
  for step in "${ladder[@]}"; do
    set -- $step
    make_gif "$in" "$out" "$1" "$2" "$ss" "$to"
    size=$(stat -f%z "$out")
    if [ "$size" -le "$MAX_GIF_BYTES" ]; then
      write_gif_meta "$out" "$win" "$1" "$2"
      echo "$out $(( size / 1024 ))KB width=$1 fps=$2 dur=${win}s"
      return 0
    fi
  done

  # 사다리를 다 써도 안 맞으면 그때만 자른다 — 발췌는 최후 수단이다.
  local trunc_to
  trunc_to=$(python3 -c "print(round(float('${ss:-0}') + float('$GIF_TRUNCATE_FLOOR'), 3))")
  echo "WARN: 전체 ${win}s 를 용량 상한($(( MAX_GIF_BYTES / 1024 / 1024 ))MB) 안에 담지 못했다 — ${GIF_TRUNCATE_FLOOR}s 로 트림한다." >&2
  echo "      핵심 구간이 앞부분이 아니면 --ss/--to 로 직접 지정할 것" \
       "(compose_video.sh frames 로 프레임을 먼저 확인)." >&2
  to="$trunc_to"; win=$(window_of)
  make_gif "$in" "$out" 320 6 "$ss" "$to"
  size=$(stat -f%z "$out")
  write_gif_meta "$out" "$win" 320 6
  echo "$out $(( size / 1024 ))KB width=320 fps=6 dur=${win}s"
}

cmd_frames() {
  local in="$1" outdir="$2"; shift 2
  need "$in"
  local times="" count=6
  while [ $# -gt 0 ]; do
    case "$1" in
      --times) times="$2"; shift 2 ;;
      --count) count="$2"; shift 2 ;;
      *) die "알 수 없는 인자: $1" ;;
    esac
  done
  mkdir -p "$outdir"
  if [ -z "$times" ]; then
    local d
    d=$(dur_of "$in")
    times=$(python3 -c "
d=float('$d'); n=int('$count')
print(','.join(f'{d*(i+0.5)/n:.2f}' for i in range(n)))")
  fi
  local i=0 t
  IFS=',' read -ra arr <<< "$times"
  for t in "${arr[@]}"; do
    i=$((i+1))
    ffmpeg -y -v error -ss "$t" -i "$in" -frames:v 1 \
           -vf "scale=540:-2" "$outdir/f$(printf '%02d' $i)_t${t}.png"
  done
  ls "$outdir"/f*.png
}

case "${1:-}" in
  info)   shift; cmd_info "$@" ;;
  trim)   shift; cmd_trim "$@" ;;
  pair)   shift; cmd_pair "$@" ;;
  gif)    shift; cmd_gif "$@" ;;
  frames) shift; cmd_frames "$@" ;;
  *) sed -n '2,11p' "$0"; exit 1 ;;
esac
