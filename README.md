# 용사가 다한다

uiux 팀 프로젝트 (최해성 · 박지용 · 김선우). Java 17 + libGDX 2D 게임.

## 함께 작업하기

디자인과 구현 변경은 작업별 브랜치와 PR로 공유합니다. 현재 진행 방향은 [디자인 기준](docs/ART_DIRECTION.md), 작업 절차는 [협업 안내](docs/COLLABORATION.md)를 먼저 확인하세요.

[최신 도트 기준안](design/pixel-v4/README.md)은 질감을 줄인 용사·성검 시안과 **32×48 용사 / 32×32 성검의 고정 격자 원본**입니다. 정수배 확대와 발·손잡이 기준점을 확인할 수 있습니다. 현재 정면 대기 1프레임과 검 1종의 디자인 검토이며 런타임 교체 전입니다. [이전 v3 시안](design/pixel-v3/README.md)의 6포즈·검 8종은 형태 참고 자료로 보존합니다.

## 실행

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
| E | 상호작용 (대장간, 침대, 모닥불, 광맥 캐기) |
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
- `assets/data/map.json` 방 배치와 연결, 잠긴 통로 조건
- `assets/data/sprites.txt` 픽셀 그림 데이터
- 저장 파일: `%USERPROFILE%\.yongsa_game\save.json`

## 글꼴

나눔스퀘어라운드 (SIL Open Font License, `assets/fonts/NANUM_FONT_LICENSE.txt`).
