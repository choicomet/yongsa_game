# 브랜치와 PR로 함께 작업하기

사용자 요청에 따라 Codex의 디자인 작업과 Claude의 게임성 작업을 같은 GitHub 저장소에서 공유한다. 별도 인수인계 ZIP 대신 저장소의 문서·자산·PR을 기준으로 이어간다.

## 현재 기준

- 저장소: `choicomet/yongsa_game`.
- 현재 게임 구현 브랜치: `claude/project-thread-q0jrky`.
- 이번 디자인 검토 브랜치: `codex/pixel-art-review`.
- 이번 작업의 시작 커밋: `f9e022d0cff311ad50e17db9603d461ce21fd3e8`.
- `main`에는 아직 게임 구현 전체가 합쳐지지 않았으므로 이번 디자인 PR은 현재 게임 구현 브랜치를 대상으로 한다. 이후 기준 브랜치가 바뀌면 실제 병합 상태를 보고 다음 PR의 대상을 정한다.

## 작업 방식

1. 시작할 때 원격 상태와 작업 트리 변경을 확인한다. [ART_DIRECTION.md](ART_DIRECTION.md)와 관련 PR을 읽는다.
2. 각 담당자는 별도 작업 폴더와 작업 브랜치에서 수정한다. 다른 담당자의 작업 폴더에서 브랜치를 바꾸거나 변경을 초기화하지 않는다.
3. 코드/자산과 그 변경을 설명하는 문서를 함께 커밋하고 PR로 올린다. 사용자 피드백으로 방향이 바뀌면 공통 디자인 문서도 갱신한다.
4. PR에는 문제와 결과, 포함 범위, 실제 검증, 미구현 부분을 명시한다. 도트 시안 업로드와 게임 적용 완료를 구분한다.
5. 다음 담당자는 PR diff와 이미지를 확인한다. 검토 후 변경을 반영하고, 겹치는 파일은 현재 구현과 비교하며 통합한다. 다른 작업의 커밋을 강제로 덮어쓰지 않는다.

기본 분담은 Codex가 아트·UI·표시 방향을, Claude가 전투·성장·방 진행 등 게임성을 맡는 방식이다. `WorldRenderer`, `GameScreen`, `Hud`, 맵 데이터처럼 양쪽에 걸친 파일은 수정 범위를 PR에 분명하게 적는다. 역할을 이유로 필요한 버그 수정을 막는 규칙은 아니다.

PR이 만들어졌다고 Claude가 자동으로 실행되거나 검토를 완료하는 것은 아니다. Claude에게 해당 PR 링크와 검토 요청을 전달해야 한다. 검토 여부는 실제 PR/작업 결과로 확인한다.

## 어디를 읽을지

| 목적 | 파일 |
|---|---|
| 최신 사용자 디자인 방향 | [ART_DIRECTION.md](ART_DIRECTION.md) |
| 최신 인게임 적용과 대장간 변경 | [런타임 README](../design/style2-runtime/README.md), [검수 기록](../design/style2-runtime/QA.md) |
| 이전 6포즈·검 8종 형태 참고 | [v3 README](../design/pixel-v3/README.md) |
| 카메라/캐릭터 크기 제안 | [DISPLAY_SCALE.md](../design/pixel-v3/DISPLAY_SCALE.md) |
| 현재 게임 규칙 | [GAME_SPEC.md](GAME_SPEC.md), 실제 코드와 데이터 |
| 기존 HD 적용 기록 | [ART_INTEGRATION.md](../ART_INTEGRATION.md) |

오래된 `GRAPHICS_HANDOFF.md`의 픽셀 크기 제한은 최신 사용자 의견으로 해제되었다. 원문 보존과 현재 요구사항을 구별한다. 이번 문서의 시작 커밋보다 이후 코드가 있다면 후속 변경을 우선 비교한다.
