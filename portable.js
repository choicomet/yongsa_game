// File-open edition: artwork uses relative files; masks are data URLs so canvas
// reads remain valid under file:// without weakening browser security settings.
const portableMode = Boolean(window.MASK_FEEDBACK_MANIFEST);

function validateFeedback(data) {
    if (!data || data.version !== 1 || data.baseCommit !== manifest.baseCommit ||
        !data.reviews || Array.isArray(data.reviews) || typeof data.rules !== 'string') {
        throw new Error('이 검토 화면과 맞는 검토안 JSON 파일을 선택해주세요.');
    }
    const known = new Map(items.map(item => [keyOf(item), item]));
    for (const [key, r] of Object.entries(data.reviews)) {
        if (!known.has(key) || !r || typeof r.note !== 'string' || typeof r.done !== 'boolean' || !Array.isArray(r.strokes)) {
            throw new Error('그림별 검토 정보가 올바르지 않아요.');
        }
        for (const stroke of r.strokes) {
            if (!['open', 'block'].includes(stroke.mode) || !Number.isFinite(stroke.width) || stroke.width <= 0 || stroke.width > 500 ||
                !Array.isArray(stroke.points) || !stroke.points.length ||
                stroke.points.some(p => !Array.isArray(p) || p.length !== 2 || p.some(v => !Number.isFinite(v) || v < 0 || v > 1))) {
                throw new Error('표시한 영역의 좌표가 올바르지 않아요.');
            }
        }
    }
    // Old collision-only files remain valid. Layer data never changes collision strokes.
    if (data.layerSchema != null && data.layerSchema !== 1) throw new Error('지원하지 않는 가림 검토안 버전이에요.');
    if (data.layerSourceCommit && data.layerSourceCommit !== layerData.sourceCommit) throw new Error('가림 초안 버전이 달라요. 기존 파일은 보관하고 채팅으로 전달해주세요.');
    for (const [key,r] of Object.entries(data.reviews)) {
        if (!r.layers) continue;
        const l=r.layers, record=layerData.items[key];
        if (!l.overrides || Array.isArray(l.overrides) || !Array.isArray(l.strokes) || typeof l.done !== 'boolean') throw new Error('가림 검토 정보가 올바르지 않아요.');
        const ids=new Set([...record.polygons.map(p=>p.id),...(record.raster?[record.raster.id]:[])]);
        const valid=p=>['always','behind'].includes(p.mode)&&typeof p.disabled==='boolean'&&Number.isFinite(p.depth)&&p.depth>=0&&p.depth<=1;
        for(const [id,p] of Object.entries(l.overrides))if(!ids.has(id)||!valid(p))throw new Error('가림 영역 기준이 올바르지 않아요.');
        for(const p of l.strokes)if(!(valid(p)||p.mode==='erase-layer')||!Number.isFinite(p.width)||p.width<=0||p.width>500||!Array.isArray(p.points)||!p.points.length||p.points.some(q=>!Array.isArray(q)||q.length!==2||q.some(v=>!Number.isFinite(v)||v<0||v>1)))throw new Error('가림 표시 좌표가 올바르지 않아요.');
    }
    if (data.artwork && data.artwork.some(a => !known.has(a.key) || known.get(a.key).artSha256 !== a.artSha256)) {
        throw new Error('다른 버전의 그림에 작성된 검토안이에요. 채팅으로 파일을 전달해주세요.');
    }
    return data;
}

const importInput = document.createElement('input');
importInput.type = 'file';
importInput.accept = '.json,application/json';
importInput.hidden = true;
importInput.id = 'importFile';
document.body.append(importInput);
const importButton = document.createElement('button');
importButton.textContent = '파일 불러오기';
importButton.id = 'import';
$('export').before(importButton);
importButton.onclick = () => importInput.click();
importInput.onchange = async () => {
    const file = importInput.files[0];
    if (!file) return;
    try {
        if (file.size > 10_000_000) throw new Error('검토안 파일은 10MB 이하여야 해요.');
        const incoming = validateFeedback(JSON.parse(await file.text()));
        const hasWork = state.rules || Object.values(state.reviews).some(r => r.note || r.done || r.strokes.length || r.layers?.done || r.layers?.strokes?.length || Object.keys(r.layers?.overrides || {}).length);
        if (hasWork && !confirm('현재 검토안을 불러올 파일로 바꿀까요? 현재 내용은 먼저 “파일로 받기”로 보관할 수 있어요.')) return;
        state = incoming;
        $('rules').value = state.rules;
        persist();
        await select(current || items[0]);
        flash('검토안 파일을 불러왔어요. 다른 컴퓨터에서 하던 표시와 메모를 이어서 수정할 수 있어요.');
    } catch (error) {
        flash(error.message);
    } finally {
        importInput.value = '';
    }
};

const downloadFeedback = $('export').onclick;
if (portableMode) {
    $('save').textContent = '검토안 파일 저장';
    $('save').onclick = () => {
        finishStroke();
        downloadFeedback();
        $('saveStatus').textContent = '검토안 JSON 다운로드 요청됨';
        flash('다운로드 폴더의 yongsa-mask-feedback.json을 보관해주세요. 다른 컴퓨터에서는 “파일 불러오기”로 이어갈 수 있어요.');
    };
    document.querySelector('header p').textContent = '설치·인터넷 연결 없이 검토하는 원화 73장 · 게임에는 바로 적용되지 않아요.';
    $('flash').nextElementSibling.textContent = '마치면 “검토안 파일 저장”을 눌러 JSON을 보관하세요. 다른 컴퓨터에서 “파일 불러오기”로 이어갈 수 있어요. 브라우저 자동 보관에만 의존하지 마세요.';
}

// Both the local server edition and file-open edition share import/export.
init().catch(error => {
    flash(error.message);
    $('loading').textContent = '불러오기에 실패했어요. 새로고침해주세요.';
});
