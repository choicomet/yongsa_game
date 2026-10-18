# 출입구 구조물 잘림 보정 — 2026-10-18

사용자가 PC에서 확인한 커밋은 PR #14의 `5372e64`였다. 이후 GitHub에서는 이 작업이 `haeseong/game` / [PR #16](https://github.com/choicomet/yongsa_game/pull/16)으로 통합됐다. 이번 수정은 최신 통합 `a0435d4` 위에 누적한다.

## 문제와 수정

갱도 북쪽 입구의 들보가 바닥에 덮이고, 옆문은 기둥이 잘린 채 두 그림이 붙어 보였다. 방 테두리 전체에 적용하던 넓은 투명 처리가 출입구 목재까지 지웠고, 문턱 바로 앞에서 방 그림을 지워 계단과 문틀도 함께 끊겼다. 길목 이미지 가장자리의 급격한 혼합을 막는 처리는 대각선 문에만 적용되어 있었다.

- 실제 연결된 문 주변은 테두리 혼합 폭을 줄여 들보·기둥·계단을 보존한다. 그림 바깥 경계는 계속 투명하게 이어진다.
- 방 그림을 길목 그림으로 넘기는 위치를 문턱보다 바깥으로 옮겨 문틀이 반으로 지워지지 않게 한다.
- 모든 출구 방향에 연속적인 혼합을 적용해 길목 이미지의 사각형 끝이 드러나지 않게 한다.
- `RoomJoinMask` 하나를 배경, 기존 앞쪽 PNG, 원본 픽셀 앞뒤 가림 처리에서 함께 사용한다.

원본 PNG, 맵 크기·배치, 이동 경계, 보상·전투·저장 규칙은 변경하지 않는다. 그림의 서로 다른 원근이나 조명까지 다시 그리는 작업은 포함하지 않는다.

## 검증

- Java 17 `:core:test :lwjgl3:dist --offline --no-daemon`: **104개 테스트 통과**, 실패·오류 0, 배포 JAR 생성.
- 회귀 테스트 3개: 보고된 북쪽 들보와 옆 기둥의 보존, 방 테두리의 연속 투명도, 23개 연결의 모든 문 방향에서 이미지 경계에 갑작스러운 잘림이 없는지 검사.
- 기존 23개 통로 양방향 통행·문 잠금·저장/재개·캠페인 자동 테스트 포함.
- 실제 `WorldRenderer`로 23개 길목의 입구·중간·출구 및 주요 방 등 80화면, 물체 앞뒤 가림 30화면을 검수했다. 별도 검수 저장 폴더를 사용했다.

실제 화면: [전체 입구](review/entry.jpg) · [전체 출구](review/exit.jpg) · [길목](review/link.jpg) · [가림 회귀 확인](review/foreground.jpg).

확대 비교: [북쪽 입구](north-comparison.jpg) · [옆문](side-comparison.jpg). BEFORE는 사용자가 실행한 PR #14, AFTER는 최신 통합본과 이번 보정이다.

## 재현

저장소 루트에서 배포 JAR을 만든 뒤 실행한다. 캡처 원본은 Git에 넣지 않고 비교 화면만 보관한다.

```powershell
javac -encoding UTF-8 -cp lwjgl3/build/libs/yongsa-game-0.1.0.jar -d build/seam-qa design/painted-map-links/PaintedLinksCapture.java design/combat-rewards/ForegroundCapture.java
java -Xmx2g -cp 'build/seam-qa;lwjgl3/build/libs/yongsa-game-0.1.0.jar' PaintedLinksCapture build/seam-captures
java -Xmx2g -cp 'build/seam-qa;lwjgl3/build/libs/yongsa-game-0.1.0.jar' ForegroundCapture build/seam-foreground
python design/seam-fix/build_review.py build/seam-captures design/seam-fix/review
python design/seam-fix/build_review.py build/seam-foreground design/seam-fix/review --foreground
```

전체 엔딩까지 직접 조작하며 이동·전투한 수동 플레이 검증을 의미하지 않는다.
