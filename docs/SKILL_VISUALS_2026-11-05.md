# 스킬·용사 화면 일관성 개선 (2026-11-05)

## 적용
- 대지 가르기: 검푸른 바위 파편과 주황 균열.
- 혈월참: 붉은 초승달 날.
- 성광 검기: 금빛 날개 모양 검기.
- 천공 낙뢰: 예고 위치에 내려오는 푸른 번개.
- 투사체는 실제 속도 벡터로 회전하고 판정 반지름에 비례해 그린다. 생성 직후·소멸 직전에 짧게 투명도를 보간한다. 잔상이 시전 위치 뒤로 튀지 않도록 이동한 거리로 제한한다.
- 어두운 돌과 붉은색이 날아가지 않도록 본체는 일반 알파 합성, 주변 빛·입자는 기존 가산 합성으로 그린다.
- 낙뢰는 이미지 중심이 아니라 아래 충돌 별 모양을 실제 타격 좌표에 둔다.
- 렌더 프레임마다 무작위로 생성하던 대지 가르기 파편을 제거했다. 기존 시전 이벤트의 파편은 유지한다.
- 대장간 6개 동작의 용사를 은백색 머리·붉은 목도리·남색 옷으로 맞췄다. HUD 체력·구르기 그림과 메뉴 대체 배경도 현재 월드 용사 그림을 사용한다.
- 피해량·범위·쿨타임·타격 시점·강화 확률은 변경하지 않는다.

## 파일
- `assets/world/skills/{cleave,crescent,beam,strike}.png`: 생성 원본 RGBA.
- `assets/world/smith-harmony-v2.png`: 1536×1024, 기존 3열×2행 동작 순서.
- `assets/hd/title-harmony-v2.png`: 타이틀·소개·엔딩 공통 배경. 기존 구도와 배경을 유지하고 용사 머리색을 통일했다. 생성 결과 1671×941을 그대로 등록한다.
- `assets/data/hd-art.json`: 소스 픽셀 좌표와 프레임 여백.
- `WorldRenderer.drawSkillShots`: 실제 게임 연결.
- `SkillDesignLauncher`: 실제 Controls→GameSession→WorldRenderer 경로로 4종×8방향×일반/각성, 시간별 캡처 및 ForgeScene의 6개 자세 확인. 개인 저장 폴더 대신 출력 폴더의 qa-save 사용.

## 생성 기록
내장 imagegen으로 생성/편집했다. 원본 파일을 프로젝트로 복사했으며 코드로 그림을 다시 그리지 않았다. 여백은 파일 편집 대신 TextureRegion 좌표로 지정한다.

실제 호출 도구는 `image_gen.imagegen`이다. 이번 6개 결과는 `image_url`, `output_hint`만 반환했고 모델명·버전 필드는 없었다. 따라서 특정 GPT Image 버전(2.5 등)을 사용했다고 단정하지 않는다. Python/Pillow는 크기·알파 검사, 파일 복사/JSON 등록, 검수용 실제 화면 모음에 사용했다.

## 검증

- Java 17 `:core:test :lwjgl3:dist`: 146개 테스트, 실패·오류 0. 이미지 좌표·투명 여백 검사를 포함한다.
- `SkillDesignLauncher`: 실제 스킬 입력과 게임 업데이트로 일반/각성 상태, 8방향, 발사 후 6/12/21/32프레임 및 대장간 6자세를 총 262장 캡처했다. 빈 전투방 기준으로 낙뢰는 전방 1개 타격이며 다중 적 대상 동작은 기존 SkillTest가 검사한다.
- `VisualSmokeLauncher`: 타이틀, 캠프 HUD, 대장간 제작·강화·성공·유지·하락·파괴·각성, 가방, 작은 창 등 실제 UI 22장. 제작·장착·위험 확인 취소·강화 판정 1회 실행도 통과했다.
- 낙뢰 착지점의 약간 떠 보이는 위치를 원본 별 중심에 맞춰 보정하고 4방향·시간별 16장을 추가로 확인했다. 전체 캠페인 수동 플레이를 완료한 결과는 아니다.
- 캡처는 로컬 `build/skill-visual-qa`, `build/ui-harmony-qa`에 보관하며 Git에는 포함하지 않는다.

### 타이틀 (기존 title.png + hero-harmony-v1.png 참고)

```text
Edit the first image, using the second only as character identity reference. Make ONE tightly scoped change: the small hero standing below the campfire near bottom center must have silver-white hair with cool gray shadows matching reference, instead of brown hair. Preserve the back-facing pose, position, size, silhouette, red scarf, navy outfit, sword, and EVERY environment detail, lighting, composition and pixel art style. Do not add text, objects, or change camera. Keep original wide landscape composition. Opaque background.
```

### cleave
```text
Production game VFX sprite, transparent background, one isolated effect, no text/no icons/no character/no environment. Detailed crisp pixel-art clustered highlights matching a dark fantasy 2.5D painted dungeon RPG. Tight readable silhouette, restrained glow and lots of transparent negative space. Entire effect inside canvas with 10% clear padding. Ground rupture projectile travelling RIGHT: small wedge of jagged dark slate shards bursting forward, orange amber fissures, dusty trailing fragments extending LEFT. Side/top-down game projection. Width twice height, leading tip on right, no ground rectangle.
```

### crescent
```text
Production game VFX sprite, transparent background, one isolated effect, no text/no icons/no character/no environment. Detailed crisp pixel-art clustered highlights matching a dark fantasy 2.5D painted dungeon RPG. Tight readable silhouette, restrained glow and lots of transparent negative space. Entire effect inside canvas with 10% clear padding. Blood-red crescent slash travelling RIGHT, thin bright ivory-red cutting edge on RIGHT convex side, two pointed ends upper and lower, dark crimson inner wisps trailing LEFT. Single clean crescent, taller than wide, no circular ring.
```

### beam
```text
Production game VFX sprite, transparent background, one isolated effect, no text/no icons/no character/no environment. Detailed crisp pixel-art clustered highlights matching a dark fantasy 2.5D painted dungeon RPG. Tight readable silhouette, restrained glow and lots of transparent negative space. Entire effect inside canvas with 10% clear padding. Holy golden sword energy wave travelling RIGHT: upright broad wing-shaped chevron, bright ivory gold cutting edge on right, tapered gold feather-like trails sweeping left at upper and lower tips. Single symmetrical energy blade taller than wide, no symbol/cross, not a laser line.
```

### strike
```text
Production game VFX sprite, transparent background, one isolated effect, no text/no icons/no character/no environment. Detailed crisp pixel-art clustered highlights matching a dark fantasy 2.5D painted dungeon RPG. Tight readable silhouette, restrained glow and lots of transparent negative space. Entire effect inside canvas with 10% clear padding. Single vertical sky-blue lightning bolt striking DOWN: thin white-hot angular zigzag core from top center to bottom center, several smaller cyan branching forks, tiny sharp impact star at bottom. Tall narrow silhouette, all branches connected, no cloud or circular ground base.
```

### 대장간 (기존 smith.png + hero-harmony-v1.png 참고)
```text
Edit first image (smith sprite sheet), second is character identity reference only. Preserve all SIX smithing poses, hammer positions, each cell location, exact 3 columns by 2 rows grid, each 512 square in a 1536x1024 sheet. Keep all feet at same positions. Recolor every brown hair region to the reference's silver-white hair with cool gray shadows; same red scarf, navy tunic cream trim, leather gloves brown boots. Slightly mature proportions matching reference, but prioritize preserving original pose silhouettes and cell alignment. Transparent background (remove background glow), no floor, no labels or grids. Do not add/drop poses. Pose order idle hammer down, hammer raised, hammer strike, recoil, happy success, disappointed head lowered. Pixel art as reference. Entire sheet with full bodies and tools unclipped.
```
