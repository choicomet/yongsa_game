# GPT 그림 작업 순서 (2026-10-14 팀 통합 이후)

팀원 세 명의 게임을 하나로 합치면서(PR #6) 기능은 모두 들어갔고, 그림이 없는 곳은 **임시 그림**으로 돌아간다.
이 문서는 GPT 가 할 그림 작업을 **묶음별로, 중요한 순서대로** 정리한 것이다.
각 항목의 크기·칸 배치·좌표 같은 자세한 내용은 [`ART_REQUESTS.md`](ART_REQUESTS.md) 의 해당 절에 있다.

## GPT 에게 붙여 넣을 요청문

```text
choicomet/yongsa_game 저장소의 docs/GPT_WORK_ORDER.md 를 읽고 그대로 작업해 줘.
기준 브랜치는 claude/team-integration-m9u9aa (이미 main 에 합쳐졌으면 main).
묶음 A 부터 시작해서 묶음 하나가 끝날 때마다 새 브랜치로 PR 을 하나씩 올려 줘 (PR 대상도 기준 브랜치).
각 항목의 크기·칸 배치·좌표는 docs/ART_REQUESTS.md 의 해당 절을 따르고,
파일 이름·칸 크기·발 위치는 지금과 똑같이 맞춰서 코드 수정 없이 바뀌게 해 줘.
코드(core/, lwjgl3/)는 건드리지 말고, 이미 있는 그림을 바탕으로 고치되 비는 조각만 새로 그려 줘.
```

묶음 하나만 맡길 때는 첫 줄 뒤에 "이번에는 묶음 B 만" 처럼 덧붙이면 된다.

## 작업 규칙

- **기준 브랜치**: PR #6 이 아직 안 합쳐졌으면 `claude/team-integration-m9u9aa`, 합쳐졌으면 `main`.
  그 브랜치에서 새 브랜치를 만들어 **묶음 하나 = PR 하나**로 올린다.
- **그림 파일만 바꾼다.** 코드(`core/`, `lwjgl3/`)는 건드리지 않는다.
  예외: 방 그림을 새로 그리면 `assets/data/natural-room-scenes.json`(바닥 다각형·출구)과 `assets/data/map.json`(그 방의 `scene`)을 같이 고친다.
- **이미 있는 그림을 먼저 살린다.** 지금 그림(`assets/world/`, `assets/hd/`, `assets/scenes/natural-v1/`, 지용 보스 시트)을 바탕으로 고치고, 비거나 어울리지 않는 조각만 새로 그린다.
- 파일 이름·칸 크기·칸 배치·발 위치를 지금과 똑같이 지키면 코드 수정 없이 바로 바뀐다.
  "코드 연결 필요"라고 적은 것은 그림만 넣어 두면 Claude 가 코드에 붙인다.
- 화풍: 지금 방 그림과 같은 위에서 비스듬히 내려다보는 시점. 스프라이트는 투명 배경.
- PR 설명에 바꾼 파일 목록과 게임 화면 캡처(가능하면)를 넣는다.

## 묶음 A: 가장 눈에 띄는 것 (먼저)

| 순서 | 할 일 | 파일 | 바로 반영? | 자세히 |
|---|---|---|---|---|
| A1 | 흰머리 용사 (머리만 흰색, 나머지 그대로) | `assets/world/hero-harmony-v1.png`, `assets/hd/hero.png` | 바로 | ART_REQUESTS 0-4 |
| A2 | 골렘을 갱도에 맞는 바위 골렘으로 (흑철석 결정, 버팀목·레일 조각) | `assets/team/jiyong/boss_golem.png` | 바로 | 0-6 |
| A3 | 기사를 성채에 맞게 (더 어두운 갑옷, 붉은 망토·혈월 문양 방패) | `assets/team/jiyong/boss_knight.png` | 바로 | 0-6 |
| A4 | 해골 사제 손질 (하늘색·보라 마력, 얼굴 잘 보이게) | `assets/team/jiyong/boss_lich.png` | 바로 | 0-6 |
| A5 | 고향 마을 방 그림 (임시 그림 배치 그대로, 마을 사람 자리 비우기) | `assets/scenes/team/` 새 그림 + `natural-room-scenes.json` 의 `village` | 바닥 다각형 다시 따기 | 0-1 |

## 묶음 B: 이야기에 필요한 것

| 순서 | 할 일 | 파일 | 바로 반영? | 자세히 |
|---|---|---|---|---|
| B1 | 마왕 동작 시트 2장 (지금 마왕 그림을 살려 지용 시트 형식으로: 1페이즈 채찍, 2페이즈 본모습·눈알) | `assets/team/jiyong/boss_demon.png`, `boss_demon2.png` | 코드 연결 필요 | 0-6 |
| B2 | 마을 사람 6명 + 수정 감옥 | `assets/team/sunwoo/villager_*.png`, `crystal_prison.png` | 바로 | 0-2 |
| B3 | 침대에서 자는 흰머리 용사 | `assets/sprites/hero_sleep.png` | 바로 | 0-3 |

## 묶음 C: 전투·보상 효과

| 순서 | 할 일 | 파일 | 바로 반영? | 자세히 |
|---|---|---|---|---|
| C1 | 검 Q 스킬 효과 4종 + 아이콘 4개 | `assets/sprites/skill/` | 코드 연결 필요 | 0-5 |
| C2 | 보스 패턴 효과 (뼈 가시, 마왕 눈알) | `assets/sprites/bone_spike.png`, `demon_eye.png` | 코드 연결 필요 | 0-6 아래 |
| C3 | 포션 아이콘 | `assets/sprites/potion.png` | 코드 연결 필요 | 0-7 |
| C4 | 던전별 보물상자 3종 | `assets/team/sunwoo/chest_{left,right,top}.png` | 바로 | 0-8 |

## 묶음 D: 방 그림 다듬기

| 순서 | 할 일 | 파일 | 바로 반영? | 자세히 |
|---|---|---|---|---|
| D1 | 다른 그림을 뒤집어 쓰는 임시 방 5개 (L7, L8, R7, T7, R4) | 새 방 그림 + 장면·맵 JSON | 바닥 다각형 따기 | ART_REQUESTS 1, 2 |
| D2 | 앞쪽 가림 레이어 (기둥·난간이 용사 앞에 오게) | `<장면 id>-front.png` | 코드 연결 필요 | 4 |
| D3 | 던전별 닫힌 출구 (판자 / 쇠창살 / 마법 장벽) | 64×64 한 칸 | 코드 연결 필요 | 5 |

## 나중에

| 할 일 | 메모 | 자세히 |
|---|---|---|
| 방 사이 통로 타일 (던전별 바닥·벽) | 길이는 지금 그대로 좋음 (해성 확인). 이미 있는 `world/terrain.png` 타일을 먼저 쓰고, 어색한 테마만 새로 | 0-9 |

## 참고 화면

- `/mnt/project-files/yongsa-team-integration/screens.jpg`: 마을, 잠, 스킬, 보스 (임시 그림 상태)
- `/mnt/project-files/yongsa-team-integration/corridors.jpg`: 지도, 통로
