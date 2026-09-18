# states.yaml 스키마 — `verify-media-states/v1`

위치: `.claude/verify-results/<브랜치>/<slug>/states.yaml` — **gitignore. 레포에 커밋하지 않는다.**

커밋하지 않는 이유:
- AS-IS/TO-BE 두 번 재생은 **한 세션 안에서** 끝난다 → 영속 저장이 필요 없다
- 좌표·기기 모델이 들어가 다른 사람 기기엔 맞지 않는다
- git 추적이면 정작 필요한 검수 브랜치(오래된 base 에서 갈라짐)에 파일이 없다

같은 화면을 또 검수할 것 같으면 `~/.claude/design-review/states/<화면>.yaml` 로 복사해 둔다
(레포 밖 · 기기별 로컬). 채워진 예시는 `example-album.yaml`.

## design-qa-states 의 매니페스트와 무엇이 다른가

둘 다 "상태를 데이터로 적는다"는 발상은 같지만 전제가 다르다.

| | `design-qa-states/v1` | **`verify-media-states/v1`** |
|---|---|---|
| 진입 | `am start -S … -e state {key}` 한 줄 | **adb 조작 시퀀스** |
| 전제 | 상태 주입 진입점이 브랜치에 커밋돼 있어야 함 | **코드 수정 불필요** |
| 대상 | plan 의 unit(상태 매트릭스) | 검수 PR 이 건드린 영역 |
| 축 | Figma ↔ 실기기 | AS-IS ↔ TO-BE ↔ Figma |

검수 브랜치는 디자이너가 `/figma-apply` 로 만든 것이라 **디버그 진입점을 추가할 수 없다.**
그래서 진입을 조작 시퀀스로 적는다 — 결정론은 떨어지지만 코드를 건드리지 않는다.

## 스키마

```yaml
schema: verify-media-states/v1
screen: <화면 이름>

device:                    # 좌표가 묶여 있는 기기. 다른 해상도면 재측정이 필요하다
  model: <모델명>
  size_px: [<w>, <h>]
  density: <기본 density>
  status_bar_px: <px>      # 캡처에서 잘라낼 상단 높이. 0/생략이면 안 자른다

figma:
  file_key: "<file key>"   # 영역마다 다르면 area.file_key 로 덮어쓴다

app:
  platform: android
  package: <applicationId>       # ★ flavor 를 여기서 고정한다
  activity: <런처 Activity FQCN>
  build_task: "<gradle 태스크>"   # 사용자에게 그대로 알려줄 문자열
  locale: <BCP-47>
  permissions:                   # 신규 설치 시 다이얼로그가 뜨는 권한 — 미리 grant
    - android.permission.READ_MEDIA_IMAGES

areas:
  - id: <영문 소문자 id — 파일명에 쓰인다>
    name: <사람이 읽는 이름>
    node: "<figma node-id, - 구분>"
    caption: "<PR 에 들어갈 한 줄. 무엇이 어떻게 바뀌었는지 + 수치>"
    files: [<이 영역이 건드린 파일 — 영역 묶음 근거>]
    density: default | <정수> | {width_dp: <dp>}
    capture: screen | drag
    steps:
      - launch                       # am start -S + 포그라운드 도착 확인
      - tap: {text: "앨범"}           # ★ 기본형 — 화면 텍스트로 찾는다 (id/desc 도 가능)
      - tap: [<x>, <y>]              # 접근성 속성이 없는 것만 좌표
      - wait_for: {desc: "캘린더 필터"} # 도착 확인 (고정 sleep 보다 정확하다)
      - swipe: [<x1>, <y1>, <x2>, <y2>, <ms>]
      - swipe: {from: {text: "..."}, dy: -1300, ms: 900}
      - wait: <초>
      - back
    drag:                            # capture: drag 일 때만
      fling: [<x1>, <y1>, <x2>, <y2>, <ms>]   # 선택 — 눌러야 보이는 UI 를 먼저 띄운다
      x: <썸 중심 x>
      y0: <시작 y>
      y1: <끝 y>                              # 짧게 — 멀리 끌면 콘텐츠(썸네일)가 언로드돼 회색 판이 찍힌다
      steps: <MOVE 분할 수, 기본 10>
      slop: <첫 MOVE 이동량, 기본 60>          # touch slop(약 24px) 보다 커야 한다
    step_wait: <스텝 간 기본 대기(초)>          # 선택
    note: |
      왜 이렇게 잡았는지. 다음 사람이 좌표만 보고 못 알아낼 것을 적는다.
```

## 필드가 존재하는 이유 (빠뜨리면 나는 사고)

| 필드 | 빠뜨리면 |
|---|---|
| `app.package` | AS-IS 를 다른 flavor 로 빌드해 계정·데이터가 달라진다 → 비교 무효 |
| `app.permissions` | 신규 설치 후 권한 다이얼로그가 떠서 이후 탭이 전부 어긋난다 |
| `density` | 반응형 변경(W600+)이 폰 기본 폭에서 안 타 "변경 없음"으로 오독된다 |
| `capture: drag` | 눌러야 보이는 UI(빠른 스크롤바)가 손 뗀 뒤 찍혀 아무것도 안 나온다 |
| `device.status_bar_px` | AS-IS 와 TO-BE 의 상태바 시계가 달라 그 차이가 비교 이미지에 남는다 |
| `note` | 좌표만 남으면 다음 사람이 왜 그 위치인지 몰라 그대로 복사만 한다 |

## 선택자 우선 · 좌표는 예외

`tap: {text: "앨범"}` 은 실행 시점에 `uiautomator dump` 로 좌표를 해소한다. 그래서

- 사람이 좌표를 잴 필요가 없다 → 스킬이 states 를 **스스로 만들 수 있다**
- **density 를 바꿔도 안 깨진다** — 반응형(W600) 영역 때문에 좌표를 두 벌 관리하던 문제가 사라진다

`replay_states.py dump` 로 지금 화면의 선택자 후보를, `find --text …` 로 실제로 잡히는지 확인한다.

좌표는 접근성 속성이 없는 것에만 쓴다(드래그 핸들, 스크롤바 썸).

- **추측 금지.** 스크린샷을 찍어 확인한 좌표만 적는다.
- **density 를 바꾸면 좌표계가 통째로 바뀐다.** `density: {width_dp: 600}` 영역의 좌표는
  그 density 에서 따로 측정한 값이어야 한다 (기본 density 좌표를 재사용하면 엉뚱한 곳을 누른다).
- 눌러야 하는 컴포넌트가 작으면 **코드의 크기 상수로 히트 범위를 계산**한다.
  예: `width 16.dp` + `paddingEnd 4.dp` → 3x 기기에서 x = 1080-12-48 … 1080-12 = 1020~1068.
