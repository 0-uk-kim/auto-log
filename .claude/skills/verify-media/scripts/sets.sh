#!/usr/bin/env bash
# 검증 미디어 세트의 위치를 해석·탐색한다.
#
# 레이아웃:  .claude/verify-results/{브랜치}/{slug}/
#
# 브랜치를 경로에 넣는 이유: 다른 브랜치(=다른 PR)의 검증물과 구조적으로 섞이지 않는다.
# PR 을 만들 때 "이 세트가 이번 작업 것인가"를 판단할 필요가 없어진다 — 경로가 답이다.
# 브랜치명의 `/` 는 그대로 디렉토리로 중첩한다 (`-` 로 바꾸면 a/b 와 a-b 가 같아진다).
#
# 사용법:
#   sets.sh branch                현재 브랜치 이름 (detached 면 detached-{sha})
#   sets.sh branch-dir            이 브랜치의 세트 루트
#   sets.sh dir <slug>            이 브랜치의 특정 세트 경로 (없으면 만든다)
#   sets.sh list [--all]          첨부 대상 세트 (manifest.json 이 있는 것)
#   sets.sh history [--all]       첨부가 끝난 세트 (manifest.attached.json) — Gate E 용
#   sets.sh args [--all]          위 목록을 "--dir A --dir B" 형태로
set -euo pipefail

ROOT="${VERIFY_MEDIA_ROOT:-.claude/verify-results}"

die() { echo "ERROR: $*" >&2; exit 1; }

cmd_branch() {
  local b
  b=$(git rev-parse --abbrev-ref HEAD 2>/dev/null) || die "git 저장소가 아니다"
  if [ "$b" = "HEAD" ]; then
    b="detached-$(git rev-parse --short HEAD)"
  fi
  printf '%s\n' "$b"
}

cmd_branch_dir() { printf '%s/%s\n' "$ROOT" "$(cmd_branch)"; }

cmd_dir() {
  local slug="${1:-}"
  [ -n "$slug" ] || die "slug 필요: sets.sh dir <slug>"
  case "$slug" in
    */*|.|..) die "slug 에 '/' 를 넣지 말 것: $slug" ;;
  esac
  local d
  d="$(cmd_branch_dir)/$slug"
  mkdir -p "$d"
  printf '%s\n' "$d"
}

cmd_list() {
  local scope="${1:-}" base
  if [ "$scope" = "--all" ]; then
    base="$ROOT"
  else
    base="$(cmd_branch_dir)"
  fi
  [ -d "$base" ] || return 0
  # manifest.json 이 있는 디렉토리만. 원본만 있는 작업 중 디렉토리는 제외된다.
  find "$base" -name manifest.json -type f 2>/dev/null \
    | while read -r f; do dirname "$f"; done | sort
}

cmd_history() {
  local scope="${1:-}" base
  if [ "$scope" = "--all" ]; then base="$ROOT"; else base="$(cmd_branch_dir)"; fi
  [ -d "$base" ] || return 0
  find "$base" -name manifest.attached.json -type f 2>/dev/null \
    | while read -r f; do dirname "$f"; done | sort
}

cmd_args() {
  local n=0
  while read -r d; do
    [ -n "$d" ] || continue
    printf -- '--dir %s ' "$d"
    n=$((n+1))
  done < <(cmd_list "${1:-}")
  [ "$n" -gt 0 ] && printf '\n'
  return 0
}

case "${1:-}" in
  branch)     shift; cmd_branch ;;
  branch-dir) shift; cmd_branch_dir ;;
  dir)        shift; cmd_dir "$@" ;;
  list)       shift; cmd_list "${1:-}" ;;
  history)    shift; cmd_history "${1:-}" ;;
  args)       shift; cmd_args "${1:-}" ;;
  *) sed -n '2,19p' "$0"; exit 1 ;;
esac
