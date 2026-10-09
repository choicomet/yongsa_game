# 용사가 다한다

하나의 거대한 던전 안에서 용사가 직접 광물을 캐고, 무기를 제작·강화한 뒤 세 공략 구역의 보스를 쓰러뜨리는 PC 싱글 플레이 액션 게임.
기획서 v1.1(2026-10-09)을 기준으로 구현했다.

- Java 17+ / libGDX 1.13.1 (LWJGL3 데스크톱)
- **무작위 던전**: 새 게임마다 씨앗값으로 던전을 생성(저장에 씨앗값이 남아 같은 저장은 항상 같은 던전). 구역마다 약 16개, 전체 48개 방
- 수치는 전부 JSON (`assets/data/balance.json`)
- 충돌은 타일 + 단순 사각형 (Box2D 미사용), 1/60초 고정 간격 시뮬레이션

## 실행

```bash
./gradlew lwjgl3:run          # 바로 실행 (Windows: gradlew.bat lwjgl3:run)
./gradlew core:test           # 규칙 검증 테스트
./gradlew lwjgl3:dist         # build/dist/yongsagame.jar (모든 의존성·에셋 포함 단일 jar)
```

`java -jar build/dist/yongsagame.jar` 로 어디서든 실행할 수 있다.

### Java 설치 없이 실행되는 Windows 배포물 만들기

Windows PC(JDK 17+ 설치)에서 `lwjgl3:dist` 후:

```bat
jpackage --type app-image --name YongsaGame --input build\dist --main-jar yongsagame.jar ^
  --main-class com.yongsa.lwjgl3.Lwjgl3Launcher --dest build\package
```

`build\package\YongsaGame\` 폴더에 런타임이 포함된 `YongsaGame.exe` 가 생긴다. 이 폴더를 그대로 압축해 배포한다.

### 저장 위치

| OS | 폴더 |
| --- | --- |
| Windows | `%APPDATA%\YongsaGame` |
| macOS | `~/Library/Application Support/YongsaGame` |
| Linux | `~/.local/share/yongsagame` |

`save.json`(자동 저장), `save.bak.json`(직전 정상 백업), `settings.json`(설정).
개발 중에는 `./gradlew lwjgl3:run -Pyongsa.saveDir=폴더` 로 다른 폴더를 쓸 수 있다.

## 조작

| 입력 | 동작 |
| --- | --- |
| W A S D / 방향키 | 이동 (대각선도 같은 속도) |
| 마우스 위치 | 공격 방향 (8방향) |
| 마우스 왼쪽 | 기본 공격 (누르고 있으면 반복) |
| E | 상호작용 · 광맥에서는 1초 길게 눌러 채굴 |
| Q | 각성 충격파 (첫 보스 처치 후) |
| I | 가방 |
| Esc | 맨 위 창 닫기 · 없으면 일시정지 메뉴 |
| F3 | (개발용) 판정 영역 표시 |

## 코드 구조

기획서 16장의 묶음을 그대로 따른다. 규칙과 상태는 그리기와 분리되어 있어 화면 없이 테스트할 수 있다.

| 패키지 | 내용 |
| --- | --- |
| `config` | `Balance` — balance.json 전체 (무기·강화·적·보스·구역·충격파 수치) |
| `model` | `GameState`(저장되는 상태), `EnhanceService`, `CraftService`, `Progress`(해금·상한·목표 문구), `DamageCalc`, `Rng` |
| `world` | `DungeonGenerator`(무작위 던전), `DungeonMap`(공간 정보·검증), `GameWorld`(시뮬레이션), `VeinGenerator`, `FixedStepper` |
| `combat` | `Hero`, `Enemy`, `MonsterAI`, `BossPattern`, `HitShape`, `AttackInstance` |
| `save` | `SaveManager` — 임시 파일 → 재검증 → 정상 저장 백업 → 원자적 교체 |
| `ui` | `Hud`, `ForgeWindow`, `BagWindow`, `SettingsWindow`, `Windows`(창 쌓기) |
| `screen` | `TitleScreen`, `StoryScreen`(소개·엔딩), `GameScreen` |
| `gfx` | `WorldRenderer`, `TileLayer`(타일 자동 배치), `SpriteLibrary`(에셋 연결), `Lighting`, `Effects`, `Fonts` |

`GameWorld.step()` 의 갱신 순서는 입력과 이동 → 공격 판정 → 피해와 사망 → 방 완료와 보상 → 저장 요청 으로 고정되어 있다.
창이 열려 있거나 앱이 포커스를 잃으면 `step()` 을 호출하지 않으므로 적·무적·경고·재사용 시간이 모두 멈춘다.

## 던전 구조 (무작위 생성)

`world/DungeonGenerator` 가 씨앗값으로 던전 전체를 만든다.

- 중앙 캠프에서 왼쪽 폐광, 오른쪽 지하 요새, 위쪽 봉인의 신전으로 뻗는다 (요새·신전은 앞 보스 처치 후 개방).
- **아이작식 밀집 배치**: 세 구역이 캠프 둘레에서 번갈아 자라 한 덩어리로 모인다. 방이 칸을 거의 채워 문 사이 통로가 짧다.
- 구역마다 일반 방 14개(갈래 구조), 가장 깊은 막다른 방 뒤에 **보스 앞 공간 → 보스방**.
- 일반 방은 대부분 **몬스터와 광맥이 함께** 있다. 전투가 끝나야 광맥을 캘 수 있다. 일부는 전투만, 일부는 안전한 광맥 방.
- 곁가지는 광맥이 더 많은 보상 방. 첫 구역 입구는 무기 없이 재료를 모을 수 있는 안전한 광물방.
- 적 수는 깊이에 따라 늘고, 구역별 적 구성은 `balance.json` 의 `enemyTable` 로 정한다.
- 생성 수치(방 수, 크기, 방 성격 비율, 적 수)는 `balance.json` 의 `dungeon` 항목.

방 ID는 `<구역>_<번호>`(예: `mine_03`), 보스 앞/보스는 `<구역>_ante` / `<구역>_boss`. 같은 씨앗값이면 ID가 같으므로 저장과 호환된다.

```bash
./gradlew core:mapPreview -Pseed=7 -Pout=build/map.png   # 생성된 던전 전체 그림 (개발용)
```

기획서 v1.1의 '고정 Tiled 맵' 은 요청에 따라 무작위 생성으로 바뀌었다. 나머지 규칙(방 상태, 사망 시 실패한 방만 초기화, 지름길, 침대와 채굴 회차)은 그대로다.

## 그래픽

- 타일: CraftPix 던전 타일셋을 테마 7종으로 색을 바꿔 사용 (캠프·폐광·요새·신전 + 보스방 3종). 벽은 정면 2칸 + 윗면 16종 자동 배치.
- **보스방마다 다른 배경**: 폐광 수호자(붉은 바닥·금화·가마솥 불), 요새 수호자(바둑판 홀·갑옷 석상·용 두개골), 봉인의 수호자(언데드 대지·거대 리치 해골·무덤).
- 소품·불꽃·아치 문·수정 광맥: CraftPix 팩 (`tools/build_craftpix.py`, 자세한 내용은 `assets/README.md`).
- 몬스터·보스는 아직 임시 아트 (`tools/gen_art.py`).
- 조명(횃불·화로·모닥불·랜턴), 비네트, 떠다니는 먼지, 피격/처치 파편, 화면 흔들림, 미니맵.

## 에셋 (CraftPix 교체)

[`assets/README.md`](assets/README.md) 참고. 요약: `assets/data/sprites.json` 에 적힌 경로에 시트를 넣고 프레임 크기만 맞추면
코드 수정 없이 교체된다. 파일이 없으면 임시 픽셀 아트가 쓰인다.

## 기획서 검증 항목 대응

`core/src/test` 의 테스트가 헤드리스로 확인하는 항목:

| 테스트 | 기획서 ID |
| --- | --- |
| `EnhanceTest` | T01 재료 부족 · T02 확률 경계와 확정 성공 · T03 실패 누적 유지 · T04 상한/+10 |
| `CraftTest` | T05 장착/마지막 무기 분해 거부 · T06 분해 반환과 재제작 |
| `WorldTest` | T07 공격당 1회 판정 · T08 무적 · T09 동시 사망 · T11 침대/사망/귀환 · T16 충격파 벽 · T20 30/60/120FPS · T21 새 게임 위치 · T24 지름길 |
| `ProgressTest` | T10 중복 클리어 · 목표 문구 우선순위 · 강화 상한 |
| `MapTest` | T12 광맥 생성 · T23 캠프 경계 · 씨앗값 40개 던전 검증(연결성·방 수·방 성격 비율) · 같은 씨앗값 재현 |
| `SaveManagerTest` | T17 저장/불러오기 · T18 교체 중단 복구 · T19 손상/새 버전 |
| `BalanceTest` | 피해량 표 · 강화 확률표 자동 검증 |

T13(보스 처치와 엔딩 저장), T14·T15(창과 입력), T22(사망·이어하기·귀환 위치)는 `WorldTest`·`GameScreen` 에 구현되어 있으며 실제 플레이로 함께 확인한다.
18.2 플레이 테스트(초행 3명)는 빌드 후 직접 진행해야 한다.
