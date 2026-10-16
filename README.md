# 용사가 다한다

uiux 팀 프로젝트 (최해성 · 박지용 · 김선우). Java 17 + libGDX 2D 게임.

## 함께 작업하기

**최해성의 요청 작업은 `haeseong/game` 브랜치 하나에 누적합니다.** 클로드 통합본과 맵 PR #14, 이야기 그림 PR #15를 모두 포함합니다. 사용자가 따로 요청할 때만 브랜치를 나눕니다. [담당자별 브랜치와 가져오는 방법](docs/BRANCHES.md), [통합 게임 화면](design/haeseong-integration/README.md), [남은 작업](docs/REMAINING_WORK_2026-10-16.md)을 확인하세요.

현재 진행 방향은 [디자인 기준](docs/ART_DIRECTION.md), 작업 절차는 [협업 안내](docs/COLLABORATION.md)에 있습니다. `main`은 검토 후 병합하는 팀 기준 버전입니다.

[최신 인게임 적용본](design/style2-runtime/README.md)은 선택한 2번 스타일을 용사·검·적·소품·지형에 연결하고, 직접 망치질하는 대장간과 작은 확률 패널을 적용했습니다. 큰 미리보기는 기존 상세 그림을 유지합니다. [실제 게임 캡처 보기](design/style2-runtime/index.html) · [검수 기록](design/style2-runtime/QA.md). v3·v4 시안은 비교 기록으로 보존합니다.

## 실행

[자연스러운 던전 구상과 Java 미리보기](design/natural-dungeons/README.md)의 배경 그림 18장과 중앙캠프 그림은 이제 실제 방으로 쓰입니다 (`assets/scenes/natural-v1/`, 바닥 경계 `assets/data/natural-room-scenes.json`). [구상안 / Java 화면 / 경계 비교](design/natural-dungeons/index.html). 더 필요한 그림은 [그림 요청 목록](docs/ART_REQUESTS.md)에 있습니다.

Windows (JDK 17 필요, `JAVA_HOME` 설정):

```
.\gradlew.bat run
```

테스트: `.\gradlew.bat :core:test` · 실행 파일 묶기: `.\gradlew.bat :lwjgl3:dist` (lwjgl3/build/libs 에 jar 생성)

## 조작

| 키 | 동작 |
|---|---|
| WASD / 방향키 | 이동 |
| J | 베기 (누르고 있으면 계속) |
| 스페이스 / 왼쪽 Shift / K | 구르기 (잠깐 무적) |
| E | 상호작용 (대장간, 침대, 모닥불, 광맥 캐기, 포탈, 캠프 바닥 문양) |
| I | 가방 |
| M / Tab | 지도 열기·닫기 |
| 마우스 휠 / + - | 화면 확대·축소 (일반 구간과 보스방을 따로 기억) |
| Esc | 일시정지 메뉴 |
| F1 | 개발용 메뉴 (보스 부활, 광물 무한, 무적) |

## 구조

- `core/` 게임 로직과 화면 (`com.yongsa`)
  - `model/` 규칙 (강화, 제작, 흡수, 저장 상태) — 화면과 분리되어 테스트 가능
  - `world/` 방 배치, 문, 광맥 생성
  - `combat/` 용사와 몬스터, 충돌
  - `ui/`, `screen/`, `gfx/` 화면과 그림
- `lwjgl3/` 데스크톱 실행기
- `assets/data/balance.json` 모든 수치 (체력, 공격력, 강화 확률, 몬스터)
- `assets/data/map.json` 방 배치(그림·크기)와 문 이음, 잠긴 던전 입구 조건
- `assets/data/natural-room-scenes.json` 던전 방 그림의 바닥 경계, 막힌 소품, 출구 자리
- `assets/data/sprites.txt` 픽셀 그림 데이터
- 저장 파일: `%USERPROFILE%\.yongsa_game\save.json`

## 글꼴

나눔스퀘어라운드 (SIL Open Font License, `assets/fonts/NANUM_FONT_LICENSE.txt`).
