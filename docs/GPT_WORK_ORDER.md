# GPT 그림 작업 순서 (2026-10-14 팀 통합 이후)

팀원 세 명의 게임을 하나로 합치면서(PR #6) 기능은 모두 들어갔고, 그림이 없는 곳은 **임시 그림**으로 돌아간다.
이 문서는 GPT 가 할 그림 작업을 **묶음별로, 중요한 순서대로** 정리한 것이다.
각 항목의 크기·칸 배치·좌표 같은 자세한 내용은 [`ART_REQUESTS.md`](ART_REQUESTS.md) 의 해당 절에 있다.

## GPT 에게 붙여 넣을 요청문

```text
choicomet/yongsa_game 저장소의 docs/GPT_WORK_ORDER.md 를 읽고 그대로 작업해 줘.
기준 브랜치는 main.
묶음 A 부터 시작해서 묶음 하나가 끝날 때마다 새 브랜치로 PR 을 하나씩 올려 줘 (PR 대상도 기준 브랜치).
각 항목의 크기·칸 배치·좌표는 docs/ART_REQUESTS.md 의 해당 절을 따르고,
파일 이름·칸 크기·발 위치는 지금과 똑같이 맞춰서 코드 수정 없이 바뀌게 해 줘.
코드(core/, lwjgl3/)는 건드리지 말고, 이미 있는 그림을 바탕으로 고치되 비는 조각만 새로 그려 줘.
```

묶음 하나만 맡길 때는 첫 줄 뒤에 "이번에는 묶음 B 만" 처럼 덧붙이면 된다.

## 작업 규칙

- **기준 브랜치**: `main`.
  그 브랜치에서 새 브랜치를 만들어 **묶음 하나 = PR 하나**로 올린다.
- **그림 파일만 바꾼다.** 코드(`core/`, `lwjgl3/`)는 건드리지 않는다.
  예외: 방 그림을 새로 그리면 `assets/data/natural-room-scenes.json`(바닥 다각형·출구)과 `assets/data/map.json`(그 방의 `scene`)을 같이 고친다.
- **이미 있는 그림을 먼저 살린다.** 지금 그림(`assets/world/`, `assets/hd/`, `assets/scenes/natural-v1/`, 지용 보스 시트)을 바탕으로 고치고, 비거나 어울리지 않는 조각만 새로 그린다.
- 파일 이름·칸 크기·칸 배치·발 위치를 지금과 똑같이 지키면 코드 수정 없이 바로 바뀐다.
  "코드 연결 필요"라고 적은 것은 그림만 넣어 두면 Claude 가 코드에 붙인다.
- 화풍: 지금 방 그림과 같은 위에서 비스듬히 내려다보는 시점. 스프라이트는 투명 배경.
- PR 설명에 바꾼 파일 목록과 게임 화면 캡처(가능하면)를 넣는다.

## 이미 끝난 것

- 흰머리 용사, 고향 마을, 출구 막는 문 3종, 스킬 아이콘 4개, 바닥 포션 (`codex/art-batch-a`), 새 방 4개(L7, R7, T7, R4)와 앞쪽 가림 레이어 11장 (`codex/claude-art-followup`), 잡몹 크기 (`codex/mob-species-scale`): 게임에 붙었다.

## 묶음 A: 가장 눈에 띄는 것 (먼저)

**가장 먼저: 방 사이 길을 방 그림처럼 그리기 (해성 요청 2026-10-15)** — 지금 방 사이 길은 네모 띠이고 둘레가 까맣다. 방 그림과 같은 화풍의 **길 그림**(갱도 굴, 성채 회랑, 탑 복도…)으로 맵 전체가 이어진 느낌이 나게.
던전마다 가로 길·세로 길·굽은 길 그림부터 (12장), 그리고 바깥을 메우는 땅 그림 1장씩. 크기와 이음 목록: ART_REQUESTS 0-9. 파일 `assets/scenes/passages/`, 바닥 다각형·연결은 Claude 몫.

| 순서 | 할 일 | 파일 | 바로 반영? | 자세히 |
|---|---|---|---|---|
| A1 | 골렘을 갱도에 맞는 바위 골렘으로 (흑철석 결정, 버팀목·레일 조각) | `assets/team/jiyong/boss_golem.png` | 바로 | 0-6 |
| A2 | 기사를 성채에 맞게 (더 어두운 갑옷, 붉은 망토·혈월 문양 방패) | `assets/team/jiyong/boss_knight.png` | 바로 | 0-6 |
| A3 | 리치 시트를 **마왕**답게 (마지막 보스) | `assets/team/jiyong/boss_lich.png` | 바로 | 0-6 |

## 묶음 B: 이야기에 필요한 것

| 순서 | 할 일 | 파일 | 바로 반영? | 자세히 |
|---|---|---|---|---|
| B1 | 마왕 2페이즈 본모습 시트 (리치 시트와 같은 형식, 촉수·눈알) | `assets/team/jiyong/boss_lich2.png` | 코드 연결 필요 | 0-6 |
| B2 | 마을 사람 6명 + 수정 감옥 | `assets/team/sunwoo/villager_*.png`, `crystal_prison.png` | 바로 | 0-2 |
| B3 | 침대에서 자는 흰머리 용사 | `assets/sprites/hero_sleep.png` | 바로 | 0-3 |

## 묶음 C: 전투·보상 효과

| 순서 | 할 일 | 파일 | 바로 반영? | 자세히 |
|---|---|---|---|---|
| C1 | 마왕 눈알 패턴 효과 | `assets/sprites/demon_eye.png` | 코드 연결 필요 | 0-6 아래 |
| C2 | 던전별 보물상자 3종 | `assets/team/sunwoo/chest_{left,right,top}.png` | 바로 | 0-8 |

## 묶음 D: 방 그림·길 다듬기

| 순서 | 할 일 | 파일 | 바로 반영? | 자세히 |
|---|---|---|---|---|
| D1 | 남은 임시 방 1개 (L8 갈림 갱도, 위 출구 하나) | 새 방 그림 + 장면·맵 JSON | 바닥 다각형 따기 | ART_REQUESTS 1 |
| D2 | 남은 방 앞쪽 가림 레이어 | `<장면 id>-front.png` | 바로 | 4 |
| D3 | 광석 덩어리가 흐린 광물방 3곳 손보기 (선택) | 방 그림 + `oreSpots` | 좌표 다시 찍기 | 0-10 |

## 나중에

| 할 일 | 메모 | 자세히 |
|---|---|---|
| 검 스킬 효과 그림 | 스킬을 보스 패턴에서 가져오는 쪽으로 바꾼 뒤에 요청 | 0-5 |
| 탑 보스 새 디자인 | 지금은 원래 수문장 그림 | 0-6 |

## 참고 화면

- `/mnt/project-files/yongsa-team-integration/screens.jpg`: 마을, 잠, 스킬, 보스 (임시 그림 상태)
- `/mnt/project-files/yongsa-team-integration/corridors.jpg`: 지도, 통로 (예전 타일)
- `/mnt/project-files/yongsa-team-integration/corridor-skin.jpg`: 방 그림 질감을 깐 지금 통로
- `/mnt/project-files/yongsa-team-integration/ore-spots.jpg`: 그림 속 광석 앞 광맥
