# Claude 그림 요청 후속 작업 — 중간 저장

2026-10-14 · **그림 후보 저장본. 게임 적용 완료본이 아니다.**

현재 브랜치는 `codex/hero-map-harmony`의 `9b613c5`에서 시작했다.
생성한 PNG 17장과 프롬프트를 먼저 공유한다. 맵 좌표, 렌더러 연결, 실행 검수는 아직 하지 않았다.

## 생성한 것

| 용도 | 파일 | 상태 |
|---|---|---|
| L7 보스 앞 갱도 | [mine-antechamber.png](../../assets/scenes/natural-v1/mine-antechamber.png) | 북·동 출구 후보 |
| R7 성채 모퉁이 | [citadel-corner.png](../../assets/scenes/natural-v1/citadel-corner.png) | 북·동 출구 후보 |
| T7 보스 앞 회랑 | [tower-antechamber.png](../../assets/scenes/natural-v1/tower-antechamber.png) | 북·동 출구, 동쪽 난간 제거 편집 |
| R4 광물 저장고 | [citadel-ore-north.png](../../assets/scenes/natural-v1/citadel-ore-north.png) | 북쪽 출구 1개 후보 |
| L8 옛 갈림 갱도 | [mine-junction.png](archive/mine-junction.png) | **이전 요구 기준. 최신 L8에 그대로 적용 금지** |
| 캠프 열린 심연 문 | [central-camp-open.png](archive/central-camp-open.png) | **최신 요청에서 취소됨. 기록만 보존** |

앞벽 투명 레이어 11장:
`mine-boss-a`, `citadel-boss-a`, `tower-boss-a`, `seal-hall`,
`mine`, `citadel`, `tower`, `mine-mob-b`, `mine-mob-c`, `mine-ore-a`, `mine-ore-b`.
파일은 `assets/scenes/natural-v1/<id>-front.png`에 있다.

**앞벽 레이어는 검수 전 후보다.** 일부에 계단 바닥이 포함되었으므로 그대로 캐릭터 위에 그리면 통행 중 캐릭터가 가려질 수 있다.
원본과의 위치 정합 및 통행 구역 투명화 검수가 필요하다.

## 업로드 도중 확인한 새 요구사항

[팀 통합 PR #6](https://github.com/choicomet/yongsa_game/pull/6)의
[최신 ART_REQUESTS](https://github.com/choicomet/yongsa_game/blob/claude/team-integration-m9u9aa/docs/ART_REQUESTS.md)를 확인했다.

- 이제 방 사이는 실제 통로로 이어진다. 기존 순간이동 전제의 맵 좌표를 그대로 적용하지 않는다.
- L8은 북쪽 출구 하나만 남았다. 이 저장본의 L8 서쪽 출구는 최신 요구와 맞지 않는다.
- 캠프 아래 문은 마을 출구로 바뀌었고, 열린 심연 문 그림 요청은 취소되었다.
- 마을·주민·흰머리 용사·잠자는 용사·Q 스킬·보스 동작·포션·상자·통로 타일 요청이 새로 추가되었다. 이 저장본에는 포함하지 않았다.
- 통합 브랜치의 최신 코드와 좌표를 기준으로 후속 적용해야 한다.

## 생성 및 검증 상태

내장 `image_gen`으로 생성·편집했다. Python으로 그림을 수정하지 않았다.
[초기 방 프롬프트](PROMPTS.json), [후속 편집·가림 레이어 프롬프트](generated-assets.json)를 보존한다.
PNG 크기·투명도·파일 해시는 [asset-inventory.json](asset-inventory.json)에 기록한다.

크기 확인 결과 15장은 1672×941이며, `mine-mob-c-front.png`와 보관된 `mine-junction.png`는 1671×941이다. 원본을 임의로 늘리지 않았으며 후속 수정 대상이다. 투명 레이어 12장(앞벽 11 + 취소된 문 1)에는 실제 알파 채널이 있다.

이 커밋은 코드나 게임 데이터 연결을 바꾸지 않는다. 게임 테스트/완주 검증을 통과한 것으로 보아서는 안 된다.
