"""Build the audit report from manually reviewed frames; never modifies game assets."""
from pathlib import Path
from collections import Counter
import json

root = Path(__file__).resolve().parent
repo = root.parents[1]
findings = json.loads((root/'findings.json').read_text(encoding='utf-8'))
inventory = json.loads((root/'inventory.json').read_text(encoding='utf-8'))
map_data = json.loads((repo/'assets/data/map.json').read_text(encoding='utf-8'))
rows = findings['endpointReviews']
frames = {q['name']: q for q in inventory}
assert len(frames) == len(inventory) == 140
assert {q['id'] for q in rows} == {q['name'] for q in inventory if q['category'] == 'endpoint'}
assert len(rows) == 46
statuses = ['다시 그리기', '부분 보정', '유지']
counts = Counter(q['status'] for q in rows)

def sheet(category, index):
    return f'review/{category}-{index//6+1:02d}.jpg'

text = '''# 전체 맵 연결부 시각 점검 — 2026-10-20

**현재 맵에는 아직 그림이 이어지지 않는 곳이 남아 있다.** `haeseong/game`의 `4498574`를 빌드하고 실제 `WorldRenderer`로 방 25개, 통로 23개, 양쪽 연결부 46곳을 확인했다. 연결부마다 이동 가능 영역을 추가로 표시하여 **총 140장**을 검토했다. 아래 분류는 화면을 직접 비교한 시각적 판단이며, 수정 완료 수가 아니다.

| 지역 | 연결부 수 | 다시 그리기 | 부분 보정 | 현 상태 유지 |
|---|---:|---:|---:|---:|
'''
for region in ['광산', '성채', '탑', '마을길']:
    group = [q for q in rows if q['region'] == region]
    c = Counter(q['status'] for q in group)
    text += f"| {region} | {len(group)} | {c['다시 그리기']} | {c['부분 보정']} | {c['유지']} |\n"
text += f"| **합계** | **46** | **{counts['다시 그리기']}** | **{counts['부분 보정']}** | **{counts['유지']}** |\n"
text += '''
지역별 수에는 캠프와 해당 던전 사이의 양쪽 입구를 포함한다. 한 통로에는 연결부가 두 곳 있으므로 **23개 통로와 46개 연결부를 중복해 합산하지 않는다.** `유지`는 이번 기준에서 큰 그림 단절을 찾지 못했다는 뜻이며 모든 플레이 상황이 검증됐다는 뜻은 아니다.

## 사용자가 보낸 두 장의 위치와 업로드 상태

두 스크린샷은 **CAMP→L1의 양쪽 연결부**다. 캠프 쪽은 파란 석문과 광산 길의 방향이 어긋나고, L1 쪽은 목재 문틀이 교차한다. **이 두 곳은 아직 그림을 고치지 않은 상태이며 업로드 누락이 아니다.**

기존 `1b516b3`에 적용한 새 그림은 `entry-L1-L2`와 `exit-L3-L5` 두 곳뿐이다. 이 커밋은 현재 GitHub PR #16 및 PC 체크아웃에 포함돼 있다. 이번 보고서와 A/B 후보는 게임 아트의 추가 수정이 아니다. 이전 PR #14의 맵을 포함한 현재 누적 작업은 **PR #16 / haeseong/game**에서 진행한다.

![캠프·광산의 양쪽 입구와 기존 수정 구간 비교](review/endpoint-01.jpg)

## 반복되는 문제와 수정 방향

1. **겹친 문틀·기둥:** 광산의 목재 입구와 성채·탑의 옆문이 서로 다른 방향으로 겹친다. 같은 카메라에서 양쪽 바닥을 포함한 연결 그림을 만들고, 통행선 가운데 남은 기둥을 제거해야 한다.
2. **길이 문 지붕에 닿음:** 위쪽 길의 바닥이 아래 방의 터널·아치 윗벽에서 끝난다. 두 공간의 바닥 높이를 정하고, 열린 계단이나 층계참으로 실제로 이어지게 다시 그려야 한다. 문 형태를 유지하려면 문 내부와 옆벽까지 함께 설계해야 한다.
3. **계단·레일이 끊김:** 계단 폭과 방향, 레일 궤적을 접점에서 맞춰야 한다. 원화의 노선 자체가 다르면 투명도나 색만 조절해서는 해결되지 않는다.
4. **보행 영역과 그림이 다름:** 초록 점은 `WorldMap.free`가 용사의 발 크기로 통행을 허용한 위치다. 일부 점은 그림의 문기둥·지붕·난간과 겹친다. 통행 가능 테스트가 통과하더라도 그림이 자연스럽다는 뜻은 아니다. 새 그림을 확정한 뒤 바닥 폴리곤과 가림 영역을 함께 맞춰야 한다.

![성채의 옆문·아치 연결 문제](review/endpoint-04.jpg)
![탑의 문 지붕·난간 연결 문제](review/endpoint-07.jpg)

## 수정 우선순위와 완료 기준

- **1순위:** 사용자가 보낸 CAMP↔L1 양쪽. 기존 캠프 석문 분위기를 살리면서 광산의 흙길과 하나의 공간으로 연결한다.
- **2순위:** 광산의 반복 문틀, 갱도 지붕, L3↔L4 레일 단절. 기존에 제대로 연결된 두 곳은 보존한다.
- **3순위:** CAMP↔R1·CAMP↔T1과 성채·탑의 아치/난간 중첩. 같은 통로 그림을 쓰더라도 양끝 접점은 각 방에 맞게 제작한다.
- **4순위:** 남은 계단·측벽·바닥 재질 12곳을 국소 보정한다. CAMP↔V의 캠프 계단 사이 목재면도 여기에 포함한다.

각 연결부는 양쪽 바닥의 높이·진행 방향이 이어지고, 캐릭터 발이 지붕·벽·기둥을 밟는 것처럼 보이지 않아야 한다. 같은 카메라의 수정 전후 화면, 이동 영역, 물체 앞뒤 가림, 전투 중 출구 잠금을 확인한 뒤에만 `수정 완료`로 바꾼다. 색을 섞거나 경계를 흐리는 보정만으로 완료 처리하지 않는다.

방 구분 표식의 [A/B 비교](../room-boundary-ab/README.md)는 별도로 보관했다. A는 현재의 열린 길, B는 짧은 깃발과 바닥 문턱을 더한 후보다. **B는 선택 대기이며 기본 게임에 적용하지 않았다.** 어느 쪽을 선택해도 아래 연결 문제의 수정은 필요하다.

## 연결부 46곳 개별 결과

`entry`는 연결 이름의 첫 방, `exit`는 둘째 방 쪽이다. 예를 들어 `entry-CAMP-L1`은 캠프 쪽, `exit-CAMP-L1`은 L1 쪽이다. 그림 번호는 각 모음 이미지 안에서 **왼쪽 위부터 가로 방향으로 1~6**이다.

| 위치 | 판정 | 확인한 문제 / 유지 이유 | 증거 |
|---|---|---|---|
'''
for q in rows:
    text += f"| `{q['id']}` | {q['status']} | {q['notes']} | [화면 {q['cell']}]({q['evidence']}) · [이동 영역]({q['floorEvidence']}) |\n"

text += '''
## 방 25개와 통로 23개 내부

방 전체 화면과 통로 중앙 화면도 각각 확인했다. **이번 정적 화면에서 새로 지목한 구조적 단절은 위 연결부에 집중돼 있다.** 방 내부나 통로 중앙까지 일괄 교체할 근거는 찾지 못했다. 전경 물체 전부의 앞뒤를 걸어서 확인한 결과는 아니므로 가림 전체가 완벽하다고 판정하지 않는다. 광물방의 광석 가독성은 기존 남은 작업으로 유지한다.

| 방 | 원화 | 전체 화면 |
|---|---|---|
'''
room_frames = [q for q in inventory if q['category'] == 'room']
rooms = {r['id']: r for r in map_data['rooms']}
for i, q in enumerate(room_frames):
    room = rooms[q['location']]
    text += f"| `{q['location']}` | `{room.get('scene','')}` | [그림 {i%6+1}]({sheet('room',i)}) |\n"
text += '\n| 통로 | 중앙 화면 | 양끝 수정 대상 수 |\n|---|---|---:|\n'
for i, q in enumerate(q for q in inventory if q['category'] == 'passage'):
    end_rows = [r for r in rows if r['id'] in ('entry-'+q['location'], 'exit-'+q['location'])]
    fixes = sum(r['status'] != '유지' for r in end_rows)
    text += f"| `{q['location']}` | [그림 {i%6+1}]({sheet('passage',i)}) | {fixes}/2 |\n"
text += '''
`F`(봉인 홀)은 전체 방 화면에 포함했다. F→CAMP는 연속된 보행 통로가 아닌 일방향 이동이므로 23개 통로 수에는 포함하지 않는다.

## 검증 범위와 재현

- 기준 코드: `449857478a2d3343673b7fcd76acd6a7cf79bac6`. Java 17, 1280×720 실제 렌더러. 같은 기준으로 `:core:test :lwjgl3:dist` 빌드 성공, **111개 테스트 통과 / 실패 0 / 오류 0**.
- `FullMapAuditCapture.java`는 별도 저장 폴더를 사용한다. 모든 통로의 잠금을 풀고 몬스터를 제거한 상태로 캡처하므로 전투나 닫힌 문 상태의 화면 검수를 대신하지 않는다. 보스는 제거되므로 방 안 전투 구성 비교 자료가 아니다.
- 방은 전체 그림에 맞춘 카메라, 연결부는 동일한 줌 0.85, 통로 중앙은 줌 1.05다. 방 전체 축소 화면을 실제 플레이 캐릭터 크기로 해석하지 않는다.
- 이동 진단 46장은 입구 주변의 발 크기 통행 여부를 12 world-pixel 간격으로 표시한다. 140개 촬영 위치의 발 통행 결과는 모두 true였다. 이는 모든 지도 픽셀의 통행·가림을 검사했다는 뜻이 아니다. 기존 테스트가 별도로 통로 중심선을 촘촘히 검사한다.
- **이번 작업은 전체 맵의 그림 연결 점검이다.** 새 게임부터 엔딩까지의 수동 플레이, 전투 밸런스, 모든 이벤트·설정 UI 검수는 포함하지 않는다.
- 원본 140장과 촬영 좌표는 로컬 `outputs/full-map-audit/captures/`에 보관하고, Git에는 25개 검토 모음 이미지, [촬영 목록](inventory.json), [판정 원본](findings.json), 재현 코드를 보관한다. 좌표는 inventory의 camera/hero 값이다.

저장소 루트에서 현재 배포 JAR을 빌드한 뒤 실행한다. 아래 `<output>`은 원하는 검수 출력 폴더이며 사용자 세이브 폴더와 별개다.

```text
javac -encoding UTF-8 -cp lwjgl3/build/libs/yongsa-game-0.1.0.jar -d <classes> design/full-map-audit/FullMapAuditCapture.java
java -Xmx2g -cp "<classes>;lwjgl3/build/libs/yongsa-game-0.1.0.jar" FullMapAuditCapture <output>
python design/full-map-audit/build_review.py <output>
python design/full-map-audit/build_report.py
```

`build_review.py`는 촬영본의 검토용 축소 모음만 만들며 게임 그림을 가공하지 않는다. `build_report.py`는 수동 판정과 맵 목록을 문서로 옮긴다. 게임 에셋·동작·사용자 저장 파일을 변경하지 않는다.
'''
for q in rows:
    assert (root/q['evidence']).is_file()
    assert (root/q['floorEvidence']).is_file()
assert len(room_frames) == 25
assert sum(q['category'] == 'passage' for q in inventory) == 23
(root/'README.md').write_text(text, encoding='utf-8')
print('report: 25 rooms, 23 passages, 46 endpoints, 140 frames;', dict(counts))
