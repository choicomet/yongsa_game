# 에셋 안내

## 지금 들어 있는 것

그림은 출처별로 폴더 하나, 연결표(매니페스트) 하나씩이다. 게임에서 실제로 쓰는 것만 둔다.

| 폴더 | 연결표 | 내용 | 만드는 법 |
| --- | --- | --- | --- |
| `sprites/hero/`, `sprites/items/` | `data/sprites.json` | 용사 4방향 × 3프레임, 철검·망치·곡괭이 | 팀 제공 원본(`art_source/hero_sheet.png`) → `tools/import_hero_sheet.py` |
| `sprites/cp/` | `data/sprites_craftpix.json` | 소품, 불꽃, 아치 문, 광맥, 상자(열리는 동작), 제단 3종, 대장간, 감시자, 묘목 | CraftPix 팩 4종 → `tools/build_craftpix.py` |
| `tiles/craftpix_tiles.png` | `data/tileset.json` | 바닥·벽 타일 7테마 (캠프·폐광·요새·신전 + 보스방 3종) | 위와 같음 |
| `sprites/gen/` | `data/sprites_generated.json` | 코드로 그린 것: 슬라임, 돌진병, 마력 포탑, 쌍단검(+아이콘), 휴대용 대장간, 회복약 3종, 캠프의 모닥불 자리와 돌기둥, 창살 문, 지름길 문, 봉인 문양 | `tools/gen_sprites.py` |
| `sprites/boss/` | `data/sprites_bosses.json` | 보스 3종의 동작 시트 (256px 칸, 8열×5행: 대기·피격 / 이동 / 기본 공격 / 특수 공격 / 사망) | 직접 만든 시트 `art_source/bosses/motion/*.png` → `tools/import_boss_motion.py` |
| `story/` | `data/story.json` | 이야기 그림 6장 (시작 3, 마무리 3)과 문장 | 원본 `output/story-v1` 을 1280×720 JPG 로 줄인 것 |
| `ui/` | — | 타이틀 로고와 배경 | 로고 `tools/make_title.py`, 배경은 플레이 화면을 찍은 것 |
| — | `data/placeholder_art.txt` | UI 아이콘(하트·광물·충격파)과, 위 어디에도 없는 키를 위한 최후의 임시 그림 | 직접 작성 |
| `sfx/`, `music/` | — | 임시 효과음·배경음 | `tools/gen_sfx.py` 가 합성 |
| `fonts/Galmuri11.ttf` | — | 한글 픽셀 폰트 | Galmuri (quiple), SIL OFL 1.1 — `fonts/Galmuri-LICENSE.txt` |

그림 우선순위: `sprites.json` > `sprites_bosses.json` > `sprites_craftpix.json` > `sprites_generated.json` > `placeholder_art.txt`.
같은 종류의 그림이 여러 장이면 `crate_0`, `crate_1` … 처럼 번호를 붙이고, 게임은 위치에 따라 하나를 고른다.

`build_craftpix.py` 와 `gen_sprites.py` 는 실행할 때 자기 출력 폴더를 비우고 다시 만든다.
그래서 **스크립트에서 뺀 그림은 폴더에서도 사라진다.** 그림을 추가·삭제할 때는 PNG 를 직접 넣고 빼지 말고 스크립트를 고친다.

사용한 CraftPix 팩 (배포물에는 이 목록을 함께 넣는다. 라이선스: https://craftpix.net/file-licenses/):

- Free 2D Top-Down Pixel Dungeon Asset Pack — 타일, 불꽃, 아치 문, 상자·통·항아리
- Free Pixel Art Dungeon Objects Asset Pack — 선반·보급품·자루·무기 거치대·갑옷·깃발·용 두개골, 받침돌(제단·대장간)
- Free Undead Tileset Top-Down Pixel Art — 신전 보스방 바닥, 무덤, 리치 해골, 감시자·묘목 애니메이션
- Top-Down Crystals Pixel Art — 광맥 (노랑·파랑·보라)

## 그림 사용 규칙

방은 전투가 잘 보이도록 깔끔하게 둔다. 새 그림을 넣을 때 아래를 지킨다.

1. **수정·광석 모양은 캘 수 있는 광맥에만 쓴다.** 장식으로 쓰지 않는다. 광맥은 구역마다 색 하나, 모양 하나(`vein_mine` 노랑, `vein_fortress` 파랑, `vein_temple` 보라)라서 "저 모양 = 캘 수 있다"가 항상 성립한다.
2. **보상처럼 생긴 것은 실제로 보상이다.** 회복약(`potion_*`)은 상자에서 나올 때만 보인다. 상자(`chest`)는 방을 돌파했을 때만 나타나고, 제단(`shrine_<구역>`)은 축복을 받는 곳에만 쓴다. 장식으로 놓지 않는다.
3. **구역마다 정해진 소품만 쓴다.** 세 공략 구역은 일부러 분위기가 다르다. 서로의 소품을 섞지 않는다.
4. 일반 방의 소품은 2~4개, 벽 가까이에만. 방 한가운데(상자·제단 자리)와 문 앞은 비운다. 전투방 안쪽에는 따로 엄폐물 2~4개가 놓인다: 폐광 상자·통, 요새 보급품 더미, 신전 돌기둥(`camp_pillar`).
5. 바닥에는 그림을 덧그리지 않는다 (예외: 캠프 깔개, 보스방 봉인 문양, 신전 보스방 리치 해골, 소환 표식과 제단의 문양).
6. 강화 개체는 새 그림을 그리지 않는다. 같은 그림에 구역 색조(`zones[].eliteTint`)만 입힌다.

| 곳 | 횃불 | 소품 (`DungeonGenerator.propsFor` / `bossDecor`) |
| --- | --- | --- |
| 캠프 | 주황 | 가운데 모닥불 · 둘레의 돌기둥 여덟 개 · 문마다 그 구역 색의 화로(왼쪽 주황, 오른쪽 파랑, 위 보라) · 아래쪽 바닥에 지름길 문양 셋(구역 색, 화면 정보나 기둥에 가리지 않는 자리). 시설은 없다 — 다시 일어나는 곳일 뿐이라 어둑하게 둔다 |
| 폐광 | 주황 | 나무 통, 상자, 상자 더미, 자루 |
| 지하 요새 | 파랑 | 무기 거치대, 갑옷 석상, 통, 상자 더미, 푸른 깃발 |
| 봉인의 신전 | 보라 | 꽃병, 항아리, 묘비, 촛불, 붉은 깃발 |
| 폐광 보스방 | | 붉은 바닥, 가마솥 불, 자루 |
| 요새 보스방 | | 바둑판 바닥, 깃발, 갑옷 석상, 용 두개골, 무기 거치대 |
| 신전 보스방 | | 언데드 대지, 거대 리치 해골, 묘비 줄, 촛불 |

제단은 구역마다 하나이고 그림이 다르다 (받침돌 + 해골 전리품 조합, `build_craftpix.py` 의 `stack`).

| 곳 | 제단 | 지키는 적 |
| --- | --- | --- |
| 폐광 | 광부의 제단 `shrine_mine` (회색 받침돌) | 없음 |
| 지하 요새 | 용의 제단 `shrine_fortress` (푸른 천) | 있음 — 강화 개체가 섞인 웨이브 |
| 봉인의 신전 | 피의 제단 `shrine_temple` (붉은 천) | 있음 |

### CraftPix 팩 다시 처리하기

원본 팩은 라이선스(게임에 넣어 배포는 가능, 에셋 자체를 그대로 재배포하는 것은 불가) 때문에 저장소에 올리지 않는다
(`.gitignore` 의 `art_source/craftpix/`). 다시 처리하려면 팩을 받아 아래처럼 풀고 실행한다.

```
art_source/craftpix/dungeon/   ← Free 2D Top-Down Pixel Dungeon Asset Pack 의 Tiled_files
art_source/craftpix/objects/   ← Free Pixel Art Dungeon Objects Asset Pack 의 PNG
art_source/craftpix/undead/    ← Free Undead Tileset Top-Down Pixel Art 의 PNG
art_source/craftpix/crystals/  ← Top-Down Crystals Pixel Art 의 PNG/Assets_texture_shadow
python3 tools/build_craftpix.py
```

## CraftPix 스프라이트로 바꾸는 방법

슬라임·돌진병처럼 아직 직접 그린 그림을 쓰는 대상을 시트로 바꿀 때:

1. 시트를 `assets/sprites/` 아래에 넣는다 (경로는 자유).
2. `assets/data/sprites.json` 에 해당 키를 추가하고 `file` 경로와 프레임 정보를 적는다.
3. 실행하면 파일이 있는 항목만 교체되고, 없는 항목은 아래 단계의 그림이 그대로 쓰인다.

```json
"slime": {
  "originX": 32, "originY": 8,
  "anims": {
    "idle": { "file": "sprites/slime/Idle.png", "frameWidth": 64, "frameHeight": 64, "frames": 6, "fps": 6 },
    "move": { "file": "sprites/slime/Walk.png", "frameWidth": 64, "frameHeight": 64, "frames": 8, "fps": 10 }
  }
}
```

| 항목 | 뜻 |
| --- | --- |
| `frameWidth`, `frameHeight` | 한 프레임 크기(px) |
| `frames`, `row`, `col` | 프레임 수, 시작 줄(0부터), 시작 칸 |
| `frameOrder` | 재생 순서 지정 (예: `[0, 1, 2, 1]`) |
| `fps`, `loop` | 초당 프레임, 반복 여부 |
| `originX`, `originY` | 발 위치 — 프레임 왼쪽 아래 기준 px. 그림자·겹침 순서·판정이 이 점을 기준으로 맞춰진다 |
| `scale` | 확대 배율 (게임 화면 기준 32px = 1타일) |
| `facesLeft` | 원본이 왼쪽을 보고 있으면 true (좌우 반전 기준) |
| `directional` | `idle_down`, `walk_left` 처럼 방향별 동작을 쓰면 true (용사가 이 방식) |
| `pointAngle` | 무기 그림이 가리키는 각도(도). 위를 향하면 90 |

캐릭터·도구 키: `hero`, `slime`, `charger`, `watcher`, `boss_mine`, `boss_fortress`, `boss_temple`, `sword`, `hammer`, `pickaxe`.
그 밖의 키는 각 연결표에 적힌 이름 그대로다.

**판정은 그림 크기와 무관하다.** 공격 범위·피격 반경은 `balance.json` 값을 쓴다. 그림을 바꾼 뒤 게임에서 F3 을 눌러
판정 영역과 그림이 어긋나지 않는지 확인한다 (기획서: 판정이 보이는 이펙트보다 넓으면 안 됨).

## 타일셋 교체

던전은 무작위로 생성되고, 각 칸의 역할(바닥 / 벽 정면 위·아래 / 벽 윗면)은 코드가 정한다.
`data/tileset.json` 에서 역할마다 타일셋 이미지의 [열, 행] 좌표를 지정하면 CraftPix 타일셋으로 바뀐다.

```json
{
  "image": "tiles/craftpix_tileset.png", "tileSize": 16,
  "themes": [
    { "floor": [[1,1],[2,1]], "floorWeights": [5,1],
      "faceUpper": [[1,0]], "faceLower": [[1,1]],
      "top": [ 16개: 열린 방향 비트(1=북,2=동,4=남,8=서) 순서 ] }
  ],
  "shadow": { "n": [0,5], "w": [1,5], "e": [2,5], "s": [3,5] }
}
```

`themes` 순서는 캠프, 폐광, 요새, 신전. `tileSize` 가 16이면 자동으로 2배로 그린다.

## 효과음 교체

`sfx/<id>.ogg` 또는 `.wav` 를 같은 이름으로 넣는다(.ogg 우선). id 목록은 `tools/gen_sfx.py` 의 `SOUNDS` 참고.
배경음: `music/bgm_title`, `music/bgm_dungeon`, `music/bgm_boss`.
