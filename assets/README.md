# 에셋 안내

## 지금 들어 있는 것

| 경로 | 내용 | 출처 / 라이선스 |
| --- | --- | --- |
| `sprites/hero/hero.png` | 용사 4방향 × 3프레임 (26×37) | 팀 제공 원본(`art_source/hero_sheet.png`)을 `tools/import_hero_sheet.py` 로 변환 |
| `sprites/items/*.png` | 철검, 망치, 곡괭이(채굴 동작), 랜턴 | 위와 같은 원본 시트의 아이템 줄 |
| `tiles/dungeon_tiles.png` | 테마별 바닥·벽 타일 (임시) | `tools/gen_art.py` 가 생성 |
| `sprites/gen/*.png` | 소품·몬스터·보스 (임시) | `tools/gen_sprites.py` 가 생성 |
| `data/placeholder_art.txt` | 아이콘 등 작은 임시 그림 | 이 프로젝트에서 직접 작성 |
| `sfx/*.wav`, `music/*.wav` | 임시 효과음·배경음 | `tools/gen_sfx.py` 가 합성 |
| `fonts/Galmuri11.ttf` | 한글 픽셀 폰트 | Galmuri (quiple), SIL OFL 1.1 — `fonts/Galmuri-LICENSE.txt` |

배포물에는 이 표(에셋 출처와 라이선스 목록)를 함께 넣는다. CraftPix 에셋을 추가하면 팩 이름과 라이선스를 이 표에 적는다.

## CraftPix 스프라이트로 바꾸는 방법

1. 시트를 `assets/sprites/craftpix/...` 아래에 넣는다 (경로는 자유).
2. `assets/data/sprites.json` 에서 해당 키의 `file` 경로와 프레임 정보를 맞춘다.
3. 실행하면 파일이 있는 항목만 교체되고, 없는 항목은 임시 그림이 그대로 쓰인다.

```json
"slime": {
  "originX": 32, "originY": 8,
  "anims": {
    "idle": { "file": "sprites/craftpix/slime/Idle.png", "frameWidth": 64, "frameHeight": 64, "frames": 6, "fps": 6 },
    "move": { "file": "sprites/craftpix/slime/Walk.png", "frameWidth": 64, "frameHeight": 64, "frames": 8, "fps": 10 }
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

사용하는 키: `hero`, `slime`, `charger`, `watcher`, `boss_mine`, `boss_fortress`, `boss_temple`,
`sword`, `hammer`, `pickaxe`, `anvil`, `furnace`, `workbench`, `bedroll`, `crate`, `lantern_table`, `torch`, `brazier`, `pillar`, `vein`.

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
