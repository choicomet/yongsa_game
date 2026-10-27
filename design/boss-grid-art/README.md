# 고정 규격 보스 그림 (묶음 A)

2026-10-15 사용자 승인: 생성 해상도가 맞지 않는 고정 칸 시트는 크기를 조정해 규격에 맞춘다.

## 바뀐 것

- `assets/team/jiyong/boss_golem.png`: 갱도 바위/흑철석/목재·레일 파편.
- `boss_knight.png`: 어두운 갑옷, 붉은 망토·보석, 혈월 초승달 방패.
- `boss_lich.png`: 마지막 마왕의 왕관·검붉은 망토·붉은 보라 마력, 특수 공격은 척추 채찍.

모두 RGBA 2048×1280, 256×256 칸 8열×5행, 기존 동작 순서와 파일명을 유지합니다.
**main에서 생성했지만 통합 대상은 최신 Claude 브랜치입니다.** main의 예전 렌더러는 lich를 탑 수문장에 쓰지만, 최신 `claude/team-integration-m9u9aa`는 `demonlord`에 연결합니다. 이 매핑을 되돌리지 마세요. 게임 코드 수정은 없습니다.

## 규격 보정

내장 imagegen 생성 원본 1586×992는 `sources/`, 기존 시트는 `reference/`에 보관합니다.
`repack.py`는 투명 여백을 따라 40칸을 분리하고, 시트마다 **같은 비율**로 최근접 보간하여 대기 몸 높이를 약 128px에 맞춥니다.
x축은 기존 칸 중심 128을 기준으로 하고, y축은 기존 시트의 바닥 위치(대기는224)를 따릅니다. 프레임마다 몸 크기를 다르게 늘리지 않습니다.
알파8 이하의 바깥 잡음은 실제 그림 경계에서 2px 여유를 남겨 잘라 다음 칸으로 넘어가지 않게 합니다.
마왕의 첫 특수 공격 생성본은 채찍이 이웃 칸에 연결되어 재생성했고, **수정본의 특수 공격 행만 사용**합니다. 기본 공격 등 나머지는 첫 생성본을 유지합니다.

## 검증

- `python design/boss-grid-art/repack.py`: 3장 규격, 120개 비어 있지 않은 프레임, 셀 경계 투명 여백 검사 통과.
- 프레임별 원본 사각형·스케일·보정 위치·결과 경계·해시: `inspection.json`.
- `./gradlew :core:test :lwjgl3:dist`: main 기반 기존 테스트 **84개 통과**, 배포 JAR 빌드 성공.
- `BossAtlasCapture.java`: 게임의 `Art.cell(...,8,5,...)`로 실제 GPU 로딩 후 게임 배율0.75/발 기준(128,224)으로 120프레임 렌더링. `captures/` 3장 육안 확인 완료. 이는 **시트 검증 화면이며 실제 보스전 플레이 캡처는 아닙니다.**
- 모든 최종 프롬프트: `generation.json`. 도구: 내장 imagegen, Pillow는 승인된 크기/칸 보정에만 사용.

## GPU 미리보기 재실행

```powershell
./gradlew.bat :lwjgl3:dist
javac -encoding UTF-8 -cp lwjgl3/build/libs/yongsa-game-0.1.0.jar -d build/boss-grid-preview design/boss-grid-art/BossAtlasCapture.java
java -cp "build/boss-grid-preview;lwjgl3/build/libs/yongsa-game-0.1.0.jar" BossAtlasCapture design/boss-grid-art/captures
```
