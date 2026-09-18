---
name: verify-media
description: >-
  PR 리뷰용 검증 이미지/영상 생성. 변경 내용이 눈으로 확인해야 하는 것인지 먼저 판정하고,
  필요할 때만 단일/AS-IS·TO-BE 비교 이미지 또는 영상을 만들어 manifest 와 함께 저장한다.
  Figma 시안 URL 이 있으면(인자·대화·커밋 메시지) 시안을 마지막 패널로 더해 3단 대조를 만든다.
  design/* 검수 브랜치는 자동 인식해 커밋의 피그마 규약을 전수 파싱하고 노드(영역)별 3단을 만든다.
  Use when the user asks for verification screenshots/videos, before/after comparison media,
  PR 첨부용 검증 이미지·영상, or after UI changes that a reviewer must see.
  NOT for data/logic-only changes (그 경우 스킬이 스스로 "불필요"로 판정하고 종료),
  NOT for uploading or attaching to a PR (그건 verify-media-attach).
allowed-tools: Bash, Read, Glob, Grep, AskUserQuestion
argument-hint: "[--slug S] [--service calendar] [--feature month] [--figma <URL>] [--skip-build] [--headless]"
---

# verify-media — 검증 이미지/영상 생성

리뷰어가 **눈으로 확인해야 하는 변경**에 대해서만 미디어를 만든다.
데이터/로직 변경이면 만들지 않고 그 근거를 보고하고 끝낸다.

산출물은 `.claude/verify-results/{브랜치}/{slug}/` 에 모으고, `manifest.json` 으로 목록·캡션을 고정한다.
경로에 브랜치가 들어가므로 다른 PR 의 검증물과 섞이지 않는다 — 경로는 `sets.sh` 로 얻는다.
그 manifest 가 `verify-media-attach` 스킬의 입력이다 — 업로드·PR 첨부는 이 스킬이 하지 않는다.

## 도구

| 스크립트 | 용도 |
|---------|------|
| `scripts/capture.sh` | adb 스크린샷/화면녹화 (백그라운드 녹화를 스크립트 내부에서 처리) |
| `scripts/compose_image.py` | 이미지 합성 — pair/grid/refrow/crop/mark/scale/labelbar (PIL) |
| `scripts/fetch_figma.py` | Figma 노드 PNG 다운로드 (시안 참조 패널용, REST) |
| `scripts/parse_design_commits.py` | 검수 커밋 전수 파싱 — Figma 노드·이름·변경 파일 (`/figma-apply` 규약) |
| `scripts/replay_states.py` | 진입 시나리오(states.yaml) 재생·캡처 — AS-IS/TO-BE 동일 조작 보장. 스키마는 `references/states-schema.md` |
| `scripts/compose_video.sh` | 영상 가공 — trim/pair/gif/frames (ffmpeg) |
| `scripts/render_explain_png.py` | 설명 그림 HTML → PNG (headless Chrome, 높이 탐색 + 여백 트림) |
| `scripts/manifest.py` | manifest 생성·검증 |
| `scripts/sets.sh` | 세트 경로 해석·탐색 (브랜치 스코프) |

판정 규칙은 `references/decision-rules.md` 에 있다. **Phase 0 전에 반드시 읽는다.**

### 설명 그림 (role=explain) — 이 스킬의 Phase 를 타지 않는다

`render_explain_png.py` 는 캡처가 아니라 **그림을 PNG 파일로 만드는** 도구다. 실기기에서 찍는 것이 없으므로
Gate A~F 판정 대상이 아니고, 세트에 `--role explain` 으로 얹히기만 한다.
그림의 근거 수집과 작성은 `create-pr` Phase 5½ 가 한다 (`pr-explain` · `explain-page` 규칙).

```bash
python3 scripts/render_explain_png.py --html $DIR/explain.html --out $DIR/explain.png
python3 scripts/manifest.py add --dir $DIR --kind image --file explain.png \
  --role explain --caption "..."
```

- 소스 HTML 은 `prune` · `archive` 가 지우지 않는다 (`.html`) — 지적을 받아 고칠 때 다시 쓴다.
  storage 레포에는 PNG 만 올라가므로 HTML 은 로컬 유일본이다
- 검증 미디어 예산(4개)에 들어가지 않는다. 대신 설명 그림은 **한 장**이 기준이다

### 경로 해석

```bash
# 스크립트 경로 — 프로젝트에 있으면 그것, 없으면 유저 레벨 설치본(~/.claude/skills)
VM=$(ls -d .claude/skills/verify-media ~/.claude/skills/verify-media 2>/dev/null | head -1)
VMA=$(ls -d .claude/skills/verify-media-attach ~/.claude/skills/verify-media-attach 2>/dev/null | head -1)
```

에뮬레이터 중재(`emulator-pool` 플러그인)는 **아직 파일럿**이다 — 세부 동작은 바뀔 수 있다.
그래도 **확보(acquire)는 건너뛸 수 없다.** 에뮬레이터로 찍는 모든 캡처가 Phase 1.5 를 탄다.

이 스킬은 브랜치를 옮겨도 쓸 수 있어야 하므로(프로젝트 `.claude/skills` 는 브랜치에 묶인다)
경로를 하드코딩하지 않는다.


### 환경 제약 (실측 — 우회하지 말고 그대로 따를 것)

- 이 환경 ffmpeg 에는 **drawtext 필터가 없다**. 텍스트가 들어가는 합성은 전부 `compose_image.py`(PIL).
- `android-cli` 는 이 프로젝트에서 SIGKILL(137) 로 죽는다. **원시 adb 만 쓴다** (`capture.sh` 가 감싼다).
- `monkey -c LAUNCHER` 는 직전 task 를 되살려 엉뚱한 화면이 뜬다. 앱 실행은 `capture.sh launch`(컴포넌트 명시).
- 복합 Bash 한 줄(`&`, 다중 파이프)은 권한 프롬프트가 매번 뜬다. 스크립트 서브커맨드로 호출한다.
- **에뮬레이터는 다른 Claude 세션과 공유된다.** 확보하지 않고 찍으면 녹화 중간에 남의 조작이
  들어와 그 영상을 버려야 한다. **`adb devices` 에 한 대만 보여도 비어 있다는 뜻이 아니다.**
  확보 없이 기기를 건드리는 명령은 `emulator-pool` 훅이 차단한다 (Phase 1.5).
- 방금 부팅한 에뮬레이터는 `boot_completed` 직후에도 느려서 `screenrecord` 가 바로 안 뜬다.
  `capture.sh` 와 `emu-pool` 이 각각 폴링으로 막아 두었으니 sleep 을 더 끼워 넣지 않는다.

## 전체 흐름

```
Phase 0  생성 필요 여부 판정 (Gate A)     → 불필요면 즉시 종료
Phase 0.5 재생성이면 중복 판정 (Gate E)  → 대체·폐기 항목 삭제
Phase 1  유형·비교·합성·배치 판정 (Gate B~D,F) → 계획 확인
Phase 1.5 디바이스 확정 (에뮬레이터는 풀에서 lease · 실기기는 serial 고정)
Phase 2  AS-IS 확보 (비교일 때만)
Phase 3  TO-BE 캡처
Phase 3.5 Figma 시안 확보 (계획에 시안 패널이 있을 때만)
Phase 4  합성 (pair/grid/refrow/gif)
Phase 5  자체 검수 (프레임을 실제로 본다)
Phase 6  manifest 작성 → prune → 디바이스 해제 → 보고
```

## Phase 0 — 생성 필요 여부 (Gate A)

`references/decision-rules.md` 의 Gate A 표로 판정한다.

### base 브랜치 결정

develop 을 하드코딩하지 않는다 — 다른 피처 브랜치에서 딴 스택 브랜치라면 develop 대비 diff 에
**부모 브랜치의 변경까지 섞여** 남의 변경을 검증 대상으로 오판한다. 우선순위:

1. **열린 PR 의 base** — `gh pr view --json baseRefName -q .baseRefName` (가장 정확)
2. **checkout 출처** — `git reflog --format='%gs'` 에서 현재 브랜치로의
   `checkout: moving from {X} to {현재브랜치}` 중 가장 오래된 항목의 X
   (X 가 `git rev-parse --verify` 로 해석되는 브랜치일 때만. `origin/{X}` 가 있으면 그쪽 우선)
3. **fallback** — `origin/develop`

```bash
BASE=$(git merge-base HEAD {base브랜치})
git diff --name-only $BASE...HEAD              # 커밋된 변경
git diff --name-only HEAD                      # 커밋 안 된 변경 (staged + unstaged)
git ls-files --others --exclude-standard       # untracked 신규 파일
```

**세 목록의 합집합**이 판정 대상이다. 커밋 안 된 변경도 TO-BE 빌드에 그대로 들어가므로
빼면 검증 누락이 된다. 어느 base 를 왜 골랐는지 보고에 한 줄 남긴다.

### 디자인 검수 모드 감지

아래 중 하나라도 참이면 **디자인 검수 모드**다:

- 브랜치명이 `design/` 로 시작한다
- `$BASE..HEAD` 커밋 본문에 `피그마:` 줄 또는 figma.com URL 이 있다 (`/figma-apply` 규약)

이 모드에서는 판정과 계획이 달라진다:

- **Gate A 는 MEDIA 확정** — 시안 맞춤 변경은 정의상 눈으로 확인하는 것이 목적이다.
  diff 가 `10.dp → 8.3.dp` 처럼 사소해 보여도 NO-MEDIA 로 내리지 않는다.
- **커밋 메시지를 감으로 훑지 않는다.** 스크립트로 전수 파싱한다:

  ```bash
  python3 $SK/parse_design_commits.py --base $BASE --json
  ```

  커밋마다 Figma 노드(url·이름)와 변경 파일이 나온다. **노드(영역)마다 별도 산출물**을
  계획한다 — 한 검수 PR 에 노드가 4개면 3단 이미지도 4장이다.
- **Gate C 는 비교 + 시안 패널(3단)이 기본** — 노드별 `url` 을 Phase 3.5 의
  `fetch_figma.py` 에 그대로 넘긴다.
- 파싱 결과 노드가 **0개면 이때만** 사용자에게 Figma URL 을 묻는다
  (규약이 안 지켜진 커밋 — 일반 모드의 "되묻지 않는다"보다 이 예외가 우선한다).
- 노드와 커밋의 변경 파일이 묶여 나오므로, 영역별 캡처 시나리오는 그 파일이 그리는
  화면으로 잡는다. 묶음이 애매하면(한 노드에 커밋 여럿) 파싱 결과를 근거로 사용자에게 확인한다.

- **NO-MEDIA 판정**: 캡처를 시도하지 말고 아래 형식으로 보고하고 **종료**한다.

  ```
  ## 검증 미디어: 불필요

  변경 파일이 전부 데이터/로직 계층이다.
  - {파일 경로} → {Gate A NO-MEDIA 표의 어느 행}
  근거: {한 줄}
  ```

- **MEDIA 판정**: 어떤 파일이 어떤 신호에 걸렸는지 1~3줄로 밝히고 Phase 1로 간다.

## Phase 0.5 — 재생성 중복 판정 (Gate E)

`.claude/verify-results/{slug}/manifest.json` 이 이미 있으면 재생성이다.
**작업 끝에 PR 첨부용만 남아야 한다** — 기존 항목을 먼저 정리하고 시작한다.

```bash
$SK/sets.sh list                       # 아직 첨부 안 된 세트
$SK/sets.sh history                    # 이미 PR 에 첨부된 세트 (create-pr 가 정리한 것)
python3 $SK/manifest.py show --dir $OUT # 첨부 완료본도 읽는다
```

`history` 쪽 세트는 **미디어 파일이 없고 manifest 만 남아 있다** — PR 첨부 후
`create-pr` Phase 11 이 정리한 것이다. 파일이 없어도 캡션·kind·`attached_at` 은 남아 있어
"이번에 만들 것이 이전에 붙인 것과 겹치는지" 판정에는 충분하다. 겹치면 새로 찍어 대체한다
(이미 PR 에 올라간 URL 은 SHA 로 고정돼 있어 영향받지 않는다).

`references/decision-rules.md` 의 Gate E 로 기존 항목을 **대체 / 유지 / 폐기**로 분류한다.
캡션만 믿지 말고 애매하면 파일을 Read 로 열어 내용을 확인한다 — 캡션이 부실한 경우가 있다.

분류 결과를 표로 보고한 뒤 대체·폐기 항목을 지운다:

```bash
python3 $SK/manifest.py remove --dir $OUT --file old-month.png
```

- **유지 항목은 다시 찍지 않는다.** 재생성 대상은 대체 항목뿐이다.
- 폐기(대체물 없이 삭제)는 되돌릴 수 없으니 사용자에게 한 줄로 알린다.
- 판정이 애매하면 유지가 아니라 대체를 택한다 (Gate E 규칙).

새 slug 라면 이 Phase 를 건너뛴다.

## Phase 1 — 유형 판정 (Gate B~D)

Gate B(이미지/영상), Gate C(단일/비교), Gate D(합성 여부)를 순서대로 적용해 **계획**을 만든다.

계획은 아래를 확정해야 한다:

| 항목 | 값 |
|------|-----|
| slug | Jira 키가 있으면 그것(`CALENDAR_APP-2131`), 없으면 `{feature}-{YYYYMMDD}` |
| | 한 브랜치에서 담당 화면이 나뉘면 feature 를 접미로: `CALENDAR_APP-2131-month` |
| service / feature | `verify-media-attach/references/taxonomy.json` 의 값 중 하나 |
| 산출물 | 항목별 {이미지\|영상} × {단일\|비교} × {합성\|개별} |
| 시안 패널 | Figma URL 이 확보되면 마지막 패널로 시안 추가 — Gate C 의 시안 패널 규칙 |
| 배치(role) | 검증 결과는 `verify`(하단), 근거 자료(스펙·시안·이슈)는 `spec`(상단 섹션) — Gate F |
| 시나리오 | 각 산출물을 얻기 위한 조작 순서 (탭 좌표는 Phase 3에서 확인) |
| 캡션 | 각 산출물의 설명 — PR 본문에 들어가는 **유일한** 텍스트다. 첫 줄 + 하위 불릿 |

AskUserQuestion 으로 계획을 확인한다. 단 아래는 확인 없이 진행한다:
- 산출물이 1개이고 단일 이미지인 경우 (되돌리기 비용이 없다)

`--slug` / `--service` / `--feature` 가 인자로 왔으면 그 값을 쓰고 되묻지 않는다.

### 경로는 브랜치로 갈린다

세트 디렉토리는 직접 조립하지 말고 `sets.sh` 로 얻는다:

```bash
SK=$VM/scripts
OUT=$($SK/sets.sh dir {slug})     # .claude/verify-results/{브랜치}/{slug}/ (없으면 생성)
```

브랜치가 경로에 들어가는 덕에:

- **다른 PR 의 검증물과 섞이지 않는다.** PR 을 만들 때 "이 세트가 이번 작업 것인가"를
  판단할 필요가 없다 — `sets.sh list` 가 이 브랜치 것만 준다.
- 브랜치를 옮기면 그 브랜치의 세트만 보인다. 이전 작업물이 딸려 붙지 않는다.
- 브랜치명의 `/` 는 디렉토리로 중첩된다 (`-` 로 치환하면 `a/b` 와 `a-b` 가 같아진다).

### 한 브랜치에서 에이전트가 여러 개일 때

같은 브랜치를 향해 여러 에이전트가 각자 미디어를 만들 수 있다. 그때도 **slug 는 겹치지 않게** 한다:
`CALENDAR_APP-2131-month`, `CALENDAR_APP-2131-todo` 처럼 담당 범위를 접미로 붙인다.

같은 slug 를 공유하면 `prune` 이 남의 원본을 지우고, `init` 이 남의 manifest 를 덮어쓴다
(스크립트가 에러로 막지만, 애초에 나누는 것이 맞다).
같은 slug 에 동시에 `add` 하는 것 자체는 파일 잠금으로 안전하다.

세트가 나뉘어도 PR 첨부는 문제없다 — `verify-media-attach` 가 이 브랜치의 세트를 전부 찾아
한 커밋으로 올리고 본문에서 feature 별로 구분해 준다.

## Phase 1.5 — 디바이스 확정 (캡처 전 한 번)

AS-IS(Phase 2)와 TO-BE(Phase 3)는 **같은 기기**여야 비교가 성립한다. 캡처를 시작하기 전에
어느 기기로 찍을지 정하고 `ANDROID_SERIAL` 을 고정한다.

### 에뮬레이터 — 확보하고 쓴다 (예외 없음)

여러 Claude 세션이 한 머신에서 돌 때 같은 에뮬레이터를 잡는 것을 lease 로 막는
사내 플러그인(`emulator-pool`)이 중재한다. **플러그인은 파일럿이지만 확보는 필수다** —
켜고 끄는 신호 같은 것은 없고, 확보 없이 기기를 건드리면 플러그인 훅이 그 호출을 막는다.

```bash
# emu-pool 경로 — 설치된 플러그인에서 찾는다
EP=$(ls ~/.claude/plugins/cache/emulator-pool/emulator-pool/*/skills/emulator-pool/bin/emu-pool 2>/dev/null | tail -1)
$EP status                     # lease 를 잡지 않으므로 언제 돌려도 된다
```

**이 표를 사용자에게 그대로 출력한 뒤 확보한다.** 보기만 하고 넘어가면 사용자는 어느 기기를
왜 잡았는지, 남이 무엇을 쓰는 중인지 알 수 없다 — Bash 출력은 에이전트에게 가는 것이지
사용자 화면에 남는 것이 아니다. 전부 BUSY 여서 물어봐야 할 때도 이 표가 근거다.

`$EP` 가 비면 플러그인이 없는 것이다 — `.claude/context/emulator-pool-plugin.md` 의
설치 확인·승인 절차를 따른다. **설치를 거절하면 에뮬레이터 캡처를 하지 않는다**(실기기로 돌리거나
미디어 없이 진행). 확보 없이 쓰는 경로는 남겨 두지 않는다.

```bash
export ANDROID_SERIAL=$($EP acquire --note "verify-media {slug}" --ttl 10800 --window)
```

**`--window` 로 창을 띄우는 것이 기본이다.** 사람이 옆에서 무엇이 찍히는지 보고,
조작이 빗나가면 바로 알아챌 수 있어야 한다 (headless 로도 `screencap`·`screenrecord` 는
정상 동작하지만 화면이 보이지 않는다). `--headless` 인자를 받았을 때만 `--window` 를 뺀다.

- **이미 떠 있는 기기를 잡으면 창은 새로 뜨지 않는다** — `--window` 는 emu-pool 이 직접
  부팅할 때만 걸린다. 화면을 봐야 하는데 그 기기가 headless 로 떠 있으면
  `$EP stop <AVD>` 로 내린 뒤 다시 확보한다.
- 머신 기본값을 창 표시로 바꾸려면 `$EP config --window` (모든 세션에 걸린다).

**확보한 뒤 지킬 것**

- **한 번 확보해서 Phase 6 까지 붙잡는다.** 중간에 놓으면 다른 세션이 가져가 비교 전제가 깨진다.
- **TTL 은 넉넉히.** base 워크트리 빌드가 끼면 1시간을 쉽게 넘긴다. `--ttl 10800`(3시간)을
  주고, 그보다 길어지면 `$EP renew --ttl 10800`.
- 비교 대상이 이전에 찍은 것이면 그때와 **같은 기기**를 `--avd` 로 지정한다
  (기존 `manifest.json` 의 `device` 값이 근거). 해상도·density 가 다르면 비교가 성립하지 않는다.
- 꺼져 있는 AVD 를 잡으면 emu-pool 이 부팅까지 하고 기다린 뒤 serial 을 준다 (실측 10~15초).

| emu-pool 종료코드 | 대응 |
|---|---|
| 2 (설치된 AVD 없음) | 사용자 승인 후 `$EP init`. 승인 안 되면 실기기 또는 미디어 없이 진행 |
| 3 (전부 사용 중) | `--wait` 로 기다리거나, `$EP status` 를 사용자에게 보여주고 묻는다. **`--force` 로 뺏지 않는다** |
| 5 (부팅 실패) | `~/.claude/emulator-pool/logs/<AVD>.log` 확인 후 다른 AVD 로 재시도 |
| 7 (그 API 가 풀에 없음) | 사용자 승인 후 `$EP init`. 승인 안 되면 `--min-api` 로 완화하거나 중단 |

### 실기기(USB) — 풀의 중재 범위 밖

```bash
adb devices
```

serial 을 확인해 `export ANDROID_SERIAL=<serial>` 로 고정한다. 여러 대면 어느 것을 쓸지
사용자에게 묻는다. **에뮬레이터가 섞여 보여도 그쪽은 확보 없이 쓰지 않는다.**

`capture.sh`·`replay_states.py`·`adb` 전부 `ANDROID_SERIAL` 을 따라가므로, export 한 뒤로는
평소대로 쓰면 된다. 자세한 것은 `emulator-pool` 스킬(플러그인).

## Phase 2 — AS-IS 확보 (Gate C 가 비교일 때만)

**AS-IS 는 새로 만들기 전에 이미 있는 걸 먼저 찾는다.** base 빌드는 이 프로젝트에서 가장 비싼 단계다.

우선순위:

1. **사용자/이슈가 가진 파일** — QA 지적 영상, Jira 첨부, `.claude/verify-results/` 의 기존 캡처.
   있으면 그대로 쓴다. 사용자에게 "AS-IS 자료가 이미 있나요?"를 먼저 묻는다.
2. **현재 설치된 APK 가 base 커밋 것일 때** — 코드 수정 후 아직 설치하지 않았다면
   지금 설치된 앱이 곧 AS-IS다. 먼저 AS-IS를 캡처한 뒤 새 APK 를 설치한다. (가장 저렴)
3. **base 워크트리 빌드** — 위 둘이 안 되면 (Phase 0 에서 정한 `$BASE` 를 쓴다):

   ```bash
   git worktree add /tmp/vm-base $BASE
   ```
   그 워크트리에서 `./gradlew :app:mobile:assembleAlphaDebug` → 설치 → 캡처 → 원래 APK 재설치.
   작업 후 `git worktree remove /tmp/vm-base` 로 정리한다.

AS-IS 와 TO-BE 는 **같은 디바이스·같은 시작 상태·같은 조작**이어야 한다. 다르면 비교 근거가 못 된다.
재현이 불가능하면 단일로 내리고 사유를 캡션에 한 줄 남긴다.

**같은 조작은 손이 아니라 데이터로 보장한다.** 진입 조작을 states.yaml 로 적고
`replay_states.py` 로 두 빌드에서 재생한다 — 사람이 (에이전트도) 두 번 똑같이 조작하는 것은
불가능하다. 자세한 것은 Phase 3 의 "비교 캡처는 states 재생으로".

## Phase 3 — 캡처

```bash
SK=$VM/scripts
OUT=$($SK/sets.sh dir {slug})          # 브랜치 스코프 경로 (없으면 생성)

# ANDROID_SERIAL 은 Phase 1.5 에서 이미 export 돼 있다 (실기기면 미설정)
$SK/capture.sh probe                      # 디바이스/설치 확인 (device= model= 를 기록)
$SK/capture.sh launch                      # 앱 실행 (컴포넌트 명시)
```

### 이미지

```bash
$SK/capture.sh shot $OUT/raw-before.png
$SK/capture.sh shot $OUT/raw-after.png
```

### 영상

```bash
# --limit 은 상한이지 대기 시간이 아니다. 시나리오가 길면 넉넉히 준다 (상한 2분).
$SK/capture.sh rec-start after --limit 120 --size 540x1200
# ... 이 사이에 adb input 조작 (rec-start 가 이미 1.5s 대기하므로 바로 조작해도 된다)
$SK/capture.sh rec-stop after $OUT/raw-after.mp4
```

긴 시나리오는 **일단 넉넉히 녹화하고 나중에 트림**한다. 짧게 잡아 재현을 놓치는 것이 더 비싸다.
`--size 540x1200` 기준 2분이 약 9MB 다 (측정값) — 이 정도는 그대로 올려도 된다.

### 비교 캡처는 states 재생으로 (Gate C 비교일 때)

조작을 states.yaml (스키마: `references/states-schema.md`) 로 적고, **같은 파일을 AS-IS 와
TO-BE 빌드에서 재생**한다. 요소는 좌표가 아니라 화면의 텍스트/desc 선택자로 적는다 —
density 를 바꿔도(W600 검수) 좌표 관리가 필요 없다. 좌표는 접근성 노드가 없는 것
(드래그 핸들, 스크롤바 썸)에만 남긴다.

```bash
STATES=$OUT/states.yaml                      # 세트 디렉토리(gitignore)에 둔다 — 커밋 금지
python3 $SK/replay_states.py dump --serial $SERIAL          # 지금 화면의 탭 가능한 요소
python3 $SK/replay_states.py find --text "앨범" --serial $SERIAL
python3 $SK/replay_states.py prepare --states $STATES --serial $SERIAL   # 권한·배너 (건너뛰기 금지)
python3 $SK/replay_states.py capture --states $STATES --area {id} --out $OUT/tobe-{id}.png --serial $SERIAL
python3 $SK/replay_states.py await-install --states $STATES --serial $SERIAL   # AS-IS 설치 자동 감지
python3 $SK/replay_states.py teardown --states $STATES --serial $SERIAL        # density·배너 원복
```

단일 이미지 1장이면 states 없이 아래의 직접 조작으로 충분하다.

### 조작 규칙

- **좌표 추측 금지**: 탭 전에 `adb shell uiautomator dump /dev/tty` 또는 스크린샷으로 좌표를 확인한다.
- **신규 설치 첫 진입에는 일회성 UI 가 뜬다** — 프로모션 팝업, 기능 툴팁, 코치마크.
  안 닫고 캡처하면 AS-IS/TO-BE 조건이 오염된다 (실측: 사이드메뉴 툴팁이 TO-BE 에만 찍힘).
  설치 직후 대상 화면에 한 번 진입해 일회성 UI 를 닫고, **두 번째 진입을 캡처**한다.
- 화면 전환 뒤에는 최소 1초 대기 후 다음 조작.
- 크래시 의심 시: `adb logcat -d -t 50 *:E`.
- 녹화 실패(재생 불가 파일)면 `capture.sh rec-abort {tag}` 로 버리고 다시 찍는다.

## Phase 3.5 — Figma 시안 확보 (계획에 시안 패널이 있을 때만)

시안 패널은 리뷰어 참고용이다 — 픽셀 1:1 판정·오버레이는 design-qa(-states) 의 몫이고,
여기서는 "무엇을 향해 고친 것인지"를 비교 이미지 한 장에서 보게 하는 것이 목적이다.

URL 소스 우선순위 (Gate C 시안 패널 규칙과 동일):

1. `--figma` 인자, 또는 사용자가 대화에서 준 URL
2. 커밋 메시지의 `피그마: https://...?node-id=...` 줄 (`/figma-apply` 규약) —
   `parse_design_commits.py --base $BASE --json` 의 노드별 `url` (눈으로 훑지 말 것)
3. 없으면 시안 패널 없이 2단(또는 단일)으로 진행 — 시안은 있으면 좋은 것이지 필수가 아니다

```bash
python3 $SK/fetch_figma.py --out $OUT --names month \
  "https://www.figma.com/design/<file_key>/...?node-id=123-456"
```

`FIGMA_ACCESS_TOKEN` 이 없거나 렌더가 실패하면 시안 패널을 생략하고 사유를 보고에 한 줄 남긴다
(작업을 막지 않는다).

## Phase 4 — 합성

Gate D 결과대로 합친다.

```bash
SK=$VM/scripts

# AS-IS / TO-BE 이미지 → 한 장
python3 $SK/compose_image.py pair $OUT/raw-before.png $OUT/raw-after.png \
  -o $OUT/month-symbol.png --labels "AS-IS,TO-BE"

# 순차 상태 4개 → 2x2
python3 $SK/compose_image.py grid $OUT/s1.png $OUT/s2.png $OUT/s3.png $OUT/s4.png \
  -o $OUT/states.png --labels "빈 상태,목록,에러,로딩" --cols 2

# 시안 패널 포함 3단 — 마지막 입력이 참조(시안). 시안을 기기 패널 높이에 맞춘다.
# pair/grid 에 시안을 넣지 않는다 — 작은 컴포넌트 프레임이 들어오면 기기 패널이 뭉개진다.
python3 $SK/compose_image.py refrow $OUT/raw-before.png $OUT/raw-after.png $OUT/figma-month.png \
  -o $OUT/month-3way.png --labels "AS-IS,TO-BE,Figma 시안"

# 신규 화면(AS-IS 없음)이면 TO-BE + 시안 2단
python3 $SK/compose_image.py refrow $OUT/raw-after.png $OUT/figma-month.png \
  -o $OUT/month-vs-spec.png --labels "TO-BE,Figma 시안"

> **`--labels` 는 이미지 안에 그려져 들어간다.** PR 본문 캡션과 역할이 다르다 —
> 라벨은 **어느 패널이 어느 조건인지**, 캡션은 **무엇을 확인했는지**다.
> 이미지가 PR 밖으로 나가도(메신저 공유 등) 좌우가 뒤바뀌어 읽히지 않게 라벨을 반드시 넣는다.
>
> - 글자 크기는 패널 폭에 비례해 정해진다(`LABEL_FONT_DIV`). **줄이지 말 것** —
>   세로로 긴 2단 이미지는 본문에서 1/3 가까이 축소돼, 작게 잡으면 읽을 수 없다
> - 라벨이 길면 **잘리지 않고 여러 줄로 접힌다.** 패널마다 스트립 높이를 같게 맞추므로
>   한쪽만 두 줄이 돼도 좌우 화면은 같은 높이에서 시작한다
> - 조건만 쓰지 말고 **봐야 할 것까지** 넣으면 더 낫다
>   (`en-US 로케일 — 화면은 영어 / 툴팁은 한국어 유지`)

# 세부 픽셀 → 크롭 + 확대, 또는 위치 강조
python3 $SK/compose_image.py crop $OUT/raw-after.png -o $OUT/detail.png --box 0,240,1080,420 --zoom 2
python3 $SK/compose_image.py mark $OUT/raw-after.png -o $OUT/marked.png --box 100,300,880,500

# 영상: 트림 → 좌우 비교 → GIF
$SK/compose_video.sh trim $OUT/raw-before.mp4 $OUT/b.mp4 --ss 2 --to 9
$SK/compose_video.sh pair $OUT/b.mp4 $OUT/a.mp4 $OUT/tooltip.mp4 --labels "AS-IS,TO-BE"

# GIF 는 기본이 전체 길이다. 그냥 돌리면 영상 전체가 담긴다
$SK/compose_video.sh gif  $OUT/tooltip.mp4 $OUT/tooltip.gif --width 480 --fps 10
# 발췌가 필요할 때만 --ss/--to 로 구간을 명시한다
$SK/compose_video.sh gif  $OUT/tooltip.mp4 $OUT/tooltip.gif --ss 5 --to 18
```

**GIF 는 영상마다 반드시 만든다.** GHE PR 본문은 `<video>` 태그를 제거하므로, GIF 가 없으면
리뷰어에게는 링크만 남는다.

### GIF 는 자르지 않는 것이 기본이다

GHE 는 본문의 `<video>` 를 제거하므로 **리뷰어가 실제로 재생해 보는 것은 GIF 뿐**이다.
발췌하면 흐름의 일부만 보게 되고 루프가 중간부터 돌아 "영상이 끊겨 보인다"는 지적을 받는다.
자세한 근거는 `references/decision-rules.md` 의 "영상과 GIF 의 역할".

제약은 길이가 아니라 용량(8MB)이다. `--ss/--to` 없이 돌리면 전체를 담고, 용량이 넘치면
폭·fps 를 사다리로 낮춰 맞춘다. 그래도 안 되면 그때만 자동 트림하고 WARN 을 낸다.

**발췌를 택하는 경우는 두 가지뿐이다** — 재현이 2분 가까이 길고 변경점이 그 안의 짧은 순간일 때,
또는 앞부분이 로딩·로그인 같은 준비 동작으로 길게 채워져 있을 때. 이때 핵심 구간은 이렇게 찾는다:

```bash
$SK/compose_video.sh frames $OUT/tooltip.mp4 $OUT/_frames --count 8
```

뽑힌 프레임을 Read 로 열어 변경이 드러나는 시각 t 를 찾고, 그 앞뒤로 `--ss $((t-3)) --to $((t+8))`
정도를 잡는다. manifest 에 mp4·GIF 길이가 기록되어, 발췌했을 때만 본문에
"GIF 는 N초 발췌 — 클릭하면 전체(M분)"이 자동으로 붙는다.

`compose_image.py` 가 `WARN: 패널당 폭 ...` 을 뱉으면 합성을 포기하고 개별 파일로 간다.

## Phase 5 — 자체 검수 (생략 금지)

만든 미디어를 **실제로 본다**. 캡처가 엉뚱한 화면이거나 조작이 빗나간 걸 여기서 잡는다.

```bash
python3 $SK/compose_image.py info $OUT/*.png
$SK/compose_video.sh frames $OUT/tooltip.mp4 $OUT/_frames --count 4
```

`_frames/*.png` 와 합성 이미지를 Read 도구로 열어 확인한다. 체크 항목:

- [ ] 의도한 화면이 맞는가 (엉뚱한 탭/이전 화면이 아닌가)
- [ ] 변경점이 실제로 보이는가 (안 보이면 크롭·확대·mark 로 보이게 한다)
- [ ] AS-IS 와 TO-BE 가 같은 조건인가 (날짜·계정·테마·스크롤 위치)
- [ ] **일회성 UI 가 한쪽에만 찍히지 않았는가** — 신규 설치 툴팁·프로모션 팝업·코치마크.
      한쪽에만 있으면 조건 오염이다 → 닫고 재캡처 (Phase 3 조작 규칙)
- [ ] 개인정보/사내 정보가 찍히지 않았는가 — 실계정 이메일, 실제 일정 제목, 전화번호.
      찍혔으면 `mark` 로 가리거나 다시 찍는다. **이 미디어는 사내 레포에 영구히 올라간다.**
- [ ] 텍스트가 판독 가능한가

`_frames` 디렉토리는 검수용이므로 manifest 에 넣지 않는다.

## Phase 6 — manifest + 보고

```bash
# init 은 manifest 가 없을 때만. 재생성이면 건너뛴다 —
# 다시 부르면 Gate E 에서 "유지"로 남긴 항목이 전부 날아간다 (스크립트가 에러로 막는다).
[ -f $OUT/manifest.json ] || python3 $SK/manifest.py init --dir $OUT \
  --slug {slug} --service {service} --feature {feature} \
  --device "{capture.sh probe 의 model + android}"

python3 $SK/manifest.py add --dir $OUT --kind image --file month-symbol.png \
  --caption "월뷰 심볼 겹침 — AS-IS(좌) / TO-BE(우)" --compare
python3 $SK/manifest.py add --dir $OUT --kind video --file tooltip.mp4 --gif tooltip.gif \
  --caption "툴팁 재노출 차단 동작"
# refrow 3단(AS-IS | TO-BE | 시안)은 --panels 3 — 본문 하한이 패널당 240px 이라 3단은 720px 로 붙는다
python3 $SK/manifest.py add --dir $OUT --kind image --file month-3way.png \
  --caption "월뷰 심볼 — AS-IS(좌) / TO-BE(중) / Figma 시안(우)" --compare --panels 3

# 근거 자료(스펙·시안)는 role=spec — 하단이 아니라 본문 상단 섹션에 붙는다 (Gate F)
# 단, 시안을 refrow 로 비교에 이미 합성했으면 같은 시안을 role=spec 으로 중복 등록하지 않는다
python3 $SK/manifest.py add --dir $OUT --kind image --file api-spec.png \
  --caption "API 스펙 — 에러 status 표" --role spec

python3 $SK/manifest.py prune --dir $OUT      # 원본·프레임·이전 회차 잔여물 정리
python3 $SK/manifest.py validate --dir $OUT
```

`prune` 은 manifest 가 참조하지 않는 파일을 전부 지워, 디렉토리에 **PR 첨부 대상만** 남긴다
(`raw-*.png`, `_frames/`, 이전 회차 합성 결과). 확인하려면 `--dry-run` 을 먼저 붙인다.
원본을 남겨야 할 이유가 있으면 prune 을 건너뛰되, 그 사유를 보고에 한 줄로 남긴다.

캡션 규칙 — PR 본문에 그대로 들어간다:
- **문체는 `write-pr-body/SKILL.md` 의 「검증 미디어 설명」 이 단일 출처다.** 여기서 다시 정의하지 않는다.
  요약하면 **명사형 종결 · 줄 끝 마침표 없음 · 한 줄에 한 가지**.
- **첫 줄에는 무엇을 보여 주는지와 조건만** 쓴다. 구현 설명·코드 언급 금지.
- **확인한 것이 여럿이면 줄을 나눈다.** `--caption` 에 여러 줄을 그대로 넘기면
  첫 줄만 굵게 나가고 나머지는 하위 불릿으로 렌더된다 (`<img alt>` 에도 첫 줄만 들어간다).
  한 줄에 몰아 쓰지 않는다 — 리뷰어가 무엇을 봐야 하는지 찾지 못한다.
- 비교면 좌우 표기를 붙인다: `— AS-IS(좌) / TO-BE(우)`. **그리고 `--compare` 를 반드시 붙인다** —
  본문 하한이 패널당 240px 이라 `--compare` 가 없으면 2단 이미지가 240 으로 나가 패널 하나가 120px 이 된다 (#8951).
  3단이면 `--panels 3`.
- **본문 렌더 폭은 이미지 안 글자 크기로 자동 정해진다.** `manifest.py add` 가 글자 높이를 재 두고,
  본문에서 글자가 약 10px(본문 글자 크기)로 보이는 폭을 역산한다 — 글자가 큰 웹 페이지 크롭은 좁게,
  글자가 작은 격자·터미널 캡처는 넓게(최대 850px) 붙는다. `--wide` 는 그 결과가 맞지 않을 때만 쓰는
  850px 강제 값이다. 글줄을 3개 못 찾은 이미지(아이콘·스낵바 한 줄)는 비율과 기본 상한 640px 로 정해진다.
- **가로로 아주 긴 확대 이미지는 만들지 않는다.** 폭 상한에 걸려 원본의 절반 아래로 줄면
  `verify-media-attach` 가 경고한다 — 확대한 뜻이 사라지므로 핵심만 크롭하거나 두 장으로 나눈다.
- 이슈 번호를 캡션에 넣지 않는다 (PR 본문 상단에 이미 있다).
- **수치는 근거가 있는 것만 쓴다** — diff 의 실제 값(`40.dp → 32.dp`)에서 인용하거나,
  캡처에서 픽셀 실측한 값(dp 환산·"실측" 표기). 화면을 보고 어림한 수치는 쓰지 않는다.
  수치가 있는 캡션이 리뷰어에게 훨씬 강하다: "아이콘 영역 40→32dp" > "아이콘 영역 축소".

`validate` 가 FAIL 이면 고치고 다시 돌린다. WARN 은 사유를 보고에 한 줄로 남기고 진행할 수 있다.

### PR 코멘트 초안 (디자인 검수 모드일 때)

검수 대조는 본문 첨부보다 **코멘트가 낫다** — 본문은 변경 설명이고, 대조 이미지는 리뷰 근거라
성격이 다르다. 디자인 검수 모드면 `$OUT/comment.md` 초안을 아래 템플릿 **그대로** 만든다
(형식을 임의로 바꾸지 않는다). 이미지 자리는 `{{img:파일명}}` — URL 은 `verify-media-attach` 가
업로드 후 치환한다. comment.md 는 prune 이 지우지 않는다.

```markdown
## 디자인 검수 대조 (AS-IS / TO-BE / Figma 원본)

각 이미지는 왼쪽부터 **AS-IS**(base `{AS-IS 리비전 7자리}`) · **TO-BE**(이 브랜치) · **Figma 원본** 순입니다.
동일 기기({model}, Android {버전}) · 동일 계정 · 동일 states 재생으로 캡처했습니다.

### {영역 이름}

{manifest 캡션 그대로}

{{img:영역파일.png}}

(영역 반복 — manifest 등록 순)

---

<sub>캡처 조건 — {flavor}·{기기}·상태바 {N}px 트림{·density 전환이 있었다면 그 내용}.
Figma 원본은 노드 {node-id(영역명) 나열}.</sub>
```

- 리드 문장·각주는 사실만 담는다 (실제 기기·실제 리비전 — 확인 안 된 문구를 넣지 않는다).
- 캡션은 manifest 의 것을 그대로 쓴다 — 여기서 다시 쓰지 않는다 (수치 근거 규칙은 캡션에서 이미 지켜졌다).
- **게시는 이 스킬이 하지 않는다.** 업로드·치환·`gh pr comment` 는 verify-media-attach 의 몫이다.

### 디바이스 해제

실기기로 찍었으면 해제할 것이 없다 — 이 절을 건너뛴다.

```bash
$EP release
```

prune 까지 끝나고 **더 찍을 것이 없다고 확정된 뒤에** 놓는다. 재캡처 가능성이 남아 있으면
`$EP renew` 로 연장한다 — 놓았다가 다시 잡으면 다른 기기가 걸려 비교가 깨질 수 있다.
실패로 중단할 때도 해제한다 (안 놓으면 TTL 만료까지 그 기기가 묶인다).

### 최종 출력

```
## 검증 미디어 생성 완료

판정: {이미지 N개 / 영상 N개} — {단일|AS-IS·TO-BE 비교}
근거: {Gate A 에서 MEDIA 로 본 파일과 신호}
{재생성일 때만: 정리: 대체 N개 / 유지 N개 / 폐기 N개}
디바이스: {model} (Android {ver})
위치: {sets.sh dir 이 준 경로}
  - {파일명} — {캡션}

다음: /verify-media-attach 로 업로드하면 PR 본문 최하단에 첨부된다.
(comment.md 가 있는 세트는 본문 대신 PR 코멘트로 게시된다.
create-pr 로 PR 을 만들면 자동으로 첨부된다. 다른 에이전트가 만든 세트가 있으면 함께 붙는다.)
```

## 원본 파일 처리

`raw-*.png`, `raw-*.mp4`, `_frames/` 는 manifest 에 넣지 않는다 — 업로드 대상이 아니다.
합성 결과만 manifest 에 등록한다.

원본은 재작업 중에는 로컬에 두되, **Phase 6 의 `prune` 이 마지막에 정리한다.**
그래야 다음 재생성 때 "어느 파일이 첨부 대상인지" 헷갈리지 않는다.

`.claude/verify-results/` 는 git 에 커밋하지 않는다 (미디어가 프로젝트 레포를 비대화시킨다).
`.gitignore` 에 등록돼 있다.

## 에러 처리

| 상황 | 대응 |
|------|------|
| 디바이스 미연결 | `$EP acquire` 가 꺼진 AVD 를 부팅한다. 풀에 AVD 가 없으면(exit 2) 안내 후 종료 — 미디어 없이 PR 을 만들 수 있음을 알린다 |
| 디바이스 2대 이상 | Phase 1.5 미수행이다. 확보한 기기의 serial 로 `ANDROID_SERIAL` 을 세운다 |
| 훅이 명령을 막았다 (`확보 없이 기기를…`) | Phase 1.5 를 건너뛴 것이다. `$EP status` → `$EP acquire` 후 재시도. 우회로를 찾지 않는다 |
| 녹화에 남의 조작이 찍힘 | 확보 없이 찍었거나 남의 기기를 썼다. 폐기 후 확보하고 재촬영한다 |
| `screenrecord 시작 실패` | 부팅 직후라 아직 느린 것이다. 10초 뒤 `rec-start` 재시도 (`capture.sh` 가 8초까지는 자체 폴링한다) |
| 앱 미설치 | `./gradlew :app:mobile:assembleAlphaDebug` → `adb install -r` |
| 녹화 파일 재생 불가 | `rec-abort` 후 재촬영 (SIGINT 종료 실패) |
| GIF 8MB 초과 | `compose_video.sh gif` 가 폭·fps 사다리를 내려가고, 그래도 안 되면 자동 트림 + WARN. 판독성이 나쁘면 `--ss/--to` 로 핵심 구간만 담는다 |
| 영상 2분 초과 | 핵심 구간만 `trim`. 재현이 정말 2분 넘게 필요하면 넘겨도 되나 사유를 보고에 남긴다 |
| GIF 발췌 구간이 엉뚱함 | 기본은 전체라 발췌는 `--ss/--to` 를 준 경우뿐이다. `frames` 로 시각을 확인해 다시 지정한다 |
| Pillow 없음 | `python3 -m pip install --user Pillow` |
| PyYAML 없음 (replay_states) | `python3 -m pip install --user PyYAML` |
| 일회성 UI(툴팁·팝업)가 한쪽에만 찍힘 | 닫고 재캡처. 신규 설치 후에는 두 번째 진입을 캡처 (Phase 3) |
| `FIGMA_ACCESS_TOKEN` 미설정 | 시안 패널을 생략하고 진행. 사유를 보고에 한 줄 남긴다 |
| Figma 렌더 실패 (MISS) | node-id 가 그 URL 의 file_key 에 있는지 확인. 안 되면 시안 패널 생략 |
| base 빌드 실패 | AS-IS 를 포기하고 단일로 내린다. 사유를 캡션에 남긴다 |
| init 이 "manifest 가 이미 있다" 로 실패 | 정상 방어다. 재생성이면 init 을 건너뛰고 `remove`+`add` 로 진행 |
| 지울 항목이 manifest 에 없음 | `manifest.py show` 로 실제 파일명 확인 (파일명이 바뀌었을 수 있다) |
| prune 이 필요한 파일을 지울까 걱정 | `--dry-run` 으로 목록 먼저 확인. manifest 등록분은 절대 지우지 않는다 |
| 변경점이 화면에 안 보인다 | crop+zoom 또는 mark 로 보이게 한다. 그래도 안 되면 Gate A 판정을 재검토 |
