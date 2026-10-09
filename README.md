# 용사가 다한다

uiux 팀 프로젝트 (최해성 · 박지용 · 김선우). Java 17 + libGDX 2D 게임.

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
| J / 스페이스 | 베기 |
| K | 회전 베기 |
| L | 돌진 베기 |
| E | 상호작용 (대장간, 침대, 모닥불, 광맥 캐기) |
| I | 가방 |
| Esc | 일시정지 메뉴 |

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
