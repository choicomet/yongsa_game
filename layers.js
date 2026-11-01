// Foreground review is independent of collision feedback. Never mutates the source painting.
const layerData = window.LAYER_REVIEW_MANIFEST;
let previewFoot = [.5, .65], previewOn = false, heroImage, frontImage, layerLoadToken = 0;
const layerColors = {always: 'rgba(234,177,71,.62)', behind: 'rgba(191,128,255,.62)'};
const layerReview = () => review().layers ??= {overrides: {}, strokes: [], done: false};
const layerRecord = () => layerData.items[keyOf(current)];
const layerCanvas = document.createElement('canvas');
layerCanvas.id = 'foreground'; $('marks').before(layerCanvas);
const previewCanvas = document.createElement('canvas');
previewCanvas.id = 'actorPreview'; $('marks').after(previewCanvas);
const layerPanel = document.createElement('section');
layerPanel.className = 'noteBox'; layerPanel.style.marginTop = '14px';
layerPanel.innerHTML = `<strong>가림 레이어 초안 검토</strong>
 <p>노랑: 항상 가림 · 보라: 발이 밑동보다 뒤에 있을 때만 가림. 이동 가능 여부와 별개예요.</p>
 <div class="actions" style="flex-wrap:wrap"><label><input id="layerVisible" type="checkbox" checked> 가림 표시</label>
 <label><input id="actorToggle" type="checkbox"> 용사 가림 미리보기</label>
 <button id="actorPlace">용사 위치 옮기기</button><button id="undoLayer">가림 되돌리기</button></div>
 <p id="layerSummary"></p><div id="layerRows"></div>
 <label style="margin-top:10px"><input id="layerDone" type="checkbox"> 가림 영역 검토 완료</label>
 <p>미리보기에서는 그림을 클릭하거나 드래그해 용사를 옮기세요. 발 위치가 점선보다 위면 ‘뒤에 있을 때’ 영역이 가려요.
 가려진 부분은 희미한 용사 실루엣으로 표시합니다. 몬스터도 같은 정렬 기준을 사용할 예정이며, 획득 안내는 위에 남깁니다.</p>`;
$('legend').after(layerPanel);
document.querySelector('h1').textContent = '이동 경계와 가림 레이어, 같이 정하기';
document.title = '용사가 다한다 · 이동 / 가림 검토';
for (const [mode, text] of [['always','항상 가림'],['behind','뒤에 있을 때 가림'],['erase-layer','가림 지우기']]) {
 const button = document.createElement('button'); button.dataset.tool = mode; button.textContent = text; $('tools').append(button);
}
const hint = document.createElement('span'); hint.textContent='노랑·보라: 가림 초안 (통과·막힘과 독립)'; $('legend').append(hint);

function polygonPath(ctx, points, w, h) {
 ctx.beginPath(); for(let i=0;i<points.length;i+=2) i ? ctx.lineTo(points[i]*w,points[i+1]*h) : ctx.moveTo(points[i]*w,points[i+1]*h); ctx.closePath();
}
function brushPath(ctx, stroke, w, h) {
 ctx.lineWidth=stroke.width;ctx.lineCap=ctx.lineJoin='round';ctx.beginPath();
 if(stroke.points.length===1){const p=stroke.points[0];ctx.arc(p[0]*w,p[1]*h,stroke.width/2,0,Math.PI*2);ctx.fill();}
 else{stroke.points.forEach((p,i)=>i?ctx.lineTo(p[0]*w,p[1]*h):ctx.moveTo(p[0]*w,p[1]*h));ctx.stroke();}
}
function regions() {
 const r=layerRecord(), overrides=layerReview().overrides;
 return [...r.polygons,...(r.raster?[r.raster]:[])].map(p=>({...p,...overrides[p.id]}));
}
function layerMask(preview) {
 const w=current.size[0],h=current.size[1],c=document.createElement('canvas');c.width=w;c.height=h;const ctx=c.getContext('2d');
 const visible=p=>!p.disabled&&(!preview||p.mode==='always'||previewFoot[1]<p.depth);
 for(const p of regions()) {
  if(!visible(p))continue;
  if(p.polygon){ctx.fillStyle=preview?'white':layerColors[p.mode];polygonPath(ctx,p.polygon,w,h);ctx.fill();}
  else if(frontImage){
   const cut=document.createElement('canvas');cut.width=w;cut.height=h;const cc=cut.getContext('2d');cc.drawImage(frontImage,0,0,w,h);
   cc.globalCompositeOperation='destination-out';polygonPath(cc,p.exclude,w,h);cc.fill();
   cc.globalCompositeOperation='source-in';cc.fillStyle=preview?'white':layerColors[p.mode];cc.fillRect(0,0,w,h);ctx.drawImage(cut,0,0);
  }
 }
 for(const stroke of layerReview().strokes){
  if(stroke.mode==='erase-layer'){ctx.globalCompositeOperation='destination-out';ctx.fillStyle=ctx.strokeStyle='white';}
  else {if(!visible(stroke))continue;ctx.globalCompositeOperation='source-over';ctx.fillStyle=ctx.strokeStyle=preview?'white':layerColors[stroke.mode];}
  brushPath(ctx,stroke,w,h);
 }
 return c;
}
function drawLayers(){
 if(!ready||!current)return;
 const w=current.size[0],h=current.size[1];
 for(const c of [layerCanvas,previewCanvas])if(c.width!==w||c.height!==h){c.width=w;c.height=h;}
 const ctx=layerCanvas.getContext('2d');ctx.clearRect(0,0,w,h);
 layerCanvas.hidden=view==='art'||!$('layerVisible').checked||previewOn;
 if(!layerCanvas.hidden)ctx.drawImage(layerMask(false),0,0);
 const pc=previewCanvas.getContext('2d');pc.clearRect(0,0,w,h);previewCanvas.hidden=!previewOn||view==='art';
 if(previewOn&&heroImage){
  const spec=layerData.hero,scale=w/layerRecord().worldWidth;
  const hw=spec.drawWidth*scale,hh=spec.drawHeight*scale,px=previewFoot[0]*w,py=previewFoot[1]*h;
  const actor=document.createElement('canvas');actor.width=w;actor.height=h;const ac=actor.getContext('2d');
  ac.drawImage(heroImage,spec.x,spec.y,spec.width,spec.height,px-hw/2,py-hh,hw,hh);
  pc.drawImage(actor,0,0);
  // Cover only the hero's pixels, so collision/annotation overlays elsewhere remain readable.
  const cover=layerMask(true),cc=cover.getContext('2d');cc.globalCompositeOperation='source-in';cc.drawImage($('art'),0,0,w,h);
  cc.globalCompositeOperation='destination-in';cc.drawImage(actor,0,0);pc.drawImage(cover,0,0);
  // Pale silhouette is restricted to the occluded part, not the whole character.
  const silhouette=layerMask(true),sc=silhouette.getContext('2d');sc.globalCompositeOperation='destination-in';sc.drawImage(actor,0,0);
  sc.globalCompositeOperation='source-in';sc.fillStyle='rgba(180,224,255,.28)';sc.fillRect(0,0,w,h);pc.drawImage(silhouette,0,0);
  pc.strokeStyle='#fff';pc.lineWidth=Math.max(2,w/800);pc.beginPath();pc.ellipse(px,py,hw*.3,hw*.1,0,0,Math.PI*2);pc.stroke();
 }
 // Ground-contact lines remain visible while adjusting a conditional region.
 if(!layerCanvas.hidden){ctx.setLineDash([w/100,w/140]);ctx.lineWidth=w/650;ctx.strokeStyle='#d9b8ff';
  for(const p of [...regions(),...layerReview().strokes])if(!p.disabled&&p.mode==='behind'){
   const points=p.polygon?Array.from({length:p.polygon.length/2},(_,i)=>[p.polygon[i*2],p.polygon[i*2+1]]):p.points;
   const xs=points.map(q=>q[0]);ctx.beginPath();ctx.moveTo(Math.min(...xs)*w,p.depth*h);ctx.lineTo(Math.max(...xs)*w,p.depth*h);ctx.stroke();
  }
 }
 $('undoLayer').disabled=!layerReview().strokes.length;
}
function layerRows(){
 if(!current)return;const r=layerReview();$('layerDone').checked=r.done;
 $('layerRows').replaceChildren();const all=regions();
 $('layerSummary').textContent=all.length?`기존 게임 가림 ${all.length}개를 초안으로 불러왔어요. 아래에서 각각 켜고 끄거나 기준을 바꿀 수 있어요.`:'기존 가림 데이터가 없는 그림이에요. 가려야 하는 부분을 위의 가림 붓으로 표시해주세요. 바닥 전체를 임의로 가리지 않았어요.';
 for(const [index,p] of [...all,...r.strokes].entries()){
  if(p.mode==='erase-layer')continue;
  const row=document.createElement('div');row.className='actions';row.style.cssText='margin:7px 0;flex-wrap:wrap';
  const label=document.createElement('label'),check=document.createElement('input');check.type='checkbox';check.checked=!p.disabled;
  label.append(check,document.createTextNode(' '+(p.id||`추가 가림 ${index-all.length+1}`)));
  const select=document.createElement('select');for(const [v,t] of [['always','항상 가림'],['behind','뒤에 있을 때']]){const o=document.createElement('option');o.value=v;o.textContent=t;select.append(o);}select.value=p.mode;
  const depth=document.createElement('input');depth.type='range';depth.min=0;depth.max=1;depth.step=.001;depth.value=p.depth;depth.setAttribute('aria-label',(p.id||'추가 가림')+' 밑동 높이');depth.disabled=p.mode!=='behind';
  const value=document.createElement('span');value.textContent=`밑동 ${Math.round(p.depth*100)}%`;
  function change(){const edit={disabled:!check.checked,mode:select.value,depth:Number(depth.value)};
   if(p.id)r.overrides[p.id]={...(r.overrides[p.id]||{}),...edit};else Object.assign(r.strokes[index-all.length],edit);
   depth.disabled=select.value!=='behind';value.textContent=`밑동 ${Math.round(Number(depth.value)*100)}%`;r.done=false;$('layerDone').checked=false;persist();draw();}
  check.onchange=select.onchange=depth.oninput=change;row.append(label,select,depth,value);$('layerRows').append(row);
 }
}
const baseDraw=draw;draw=function(){baseDraw();drawLayers();};
const baseSelect=select;select=async function(item){
 finishLayer();const mine=++layerLoadToken;frontImage=null;previewFoot=[.5,.65];
 const raster=layerData.items[keyOf(item)].raster;
 const load=raster?loadImage('assets/'+raster.image):Promise.resolve(null);
 await baseSelect(item);const loaded=await load;if(mine!==layerLoadToken)return;frontImage=loaded;layerRows();draw();
};
let activeLayer=null,placing=false;
const baseDown=$('surface').onpointerdown,baseMove=$('surface').onpointermove,baseUp=$('surface').onpointerup;
$('surface').onpointerdown=e=>{
 if(!ready||e.button!==0)return;
 if(tool==='actor'){placing=true;previewOn=true;$('actorToggle').checked=true;previewFoot=point(e);$('surface').setPointerCapture(e.pointerId);draw();return;}
 if(!['always','behind','erase-layer'].includes(tool)){baseDown(e);return;}
 e.preventDefault();$('surface').setPointerCapture(e.pointerId);const p=point(e);
 activeLayer={mode:tool,width:Number($('brush').value),points:[p],depth:Math.min(1,p[1]+Number($('brush').value)/current.size[1]/2),disabled:false};layerReview().strokes.push(activeLayer);layerReview().done=false;$('layerDone').checked=false;draw();
};
$('surface').onpointermove=e=>{if(placing){previewFoot=point(e);draw();return;}if(!activeLayer){baseMove(e);return;}const p=point(e),last=activeLayer.points.at(-1);
 if(Math.hypot((p[0]-last[0])*current.size[0],(p[1]-last[1])*current.size[1])<2)return;
 activeLayer.points.push(p);activeLayer.depth=Math.min(1,Math.max(...activeLayer.points.map(v=>v[1]))+activeLayer.width/current.size[1]/2);draw();};
function finishLayer(){if(activeLayer){activeLayer=null;persist();layerRows();list();}placing=false;}
$('surface').onpointerup=()=>{baseUp();finishLayer();};$('surface').onpointercancel=$('surface').onpointerup;
const baseFinish=finishStroke;finishStroke=function(){baseFinish();finishLayer();};
$('layerVisible').onchange=draw;$('actorToggle').onchange=()=>{previewOn=$('actorToggle').checked;draw();};
$('actorPlace').onclick=()=>{setTool('actor');previewOn=true;$('actorToggle').checked=true;draw();flash('그림을 클릭하거나 드래그해서 용사 발 위치를 옮기세요.');};
$('undoLayer').onclick=()=>{layerReview().strokes.pop();layerReview().done=false;persist();layerRows();draw();};
$('layerDone').onchange=()=>{layerReview().done=$('layerDone').checked;persist();list();};
const basePayload=payload;payload=function(){return {...basePayload(),layerSchema:1,layerSourceCommit:layerData.sourceCommit,layerDefinitions:layerData.items};};
loadImage('assets/'+layerData.hero.sheet).then(im=>{heroImage=im;draw();}).catch(e=>flash(e.message));

const collisionList=list;list=function(){collisionList();
 const done=Object.values(state.reviews).filter(r=>r.layers?.done).length;
 $('count').textContent+=` · 가림 ${done}장 완료`;
 for(const card of document.querySelectorAll('.card')){const r=state.reviews[card.dataset.key],l=r?.layers;if(!l)continue;
  const badge=card.querySelector('.badge');if(l.done){badge.textContent+=' · 가림 ✓';badge.classList.add('done');}
  else if(l.strokes.length||Object.keys(l.overrides).length){badge.textContent+=' · 가림 수정';badge.classList.add('marked');}
 }
};

const baseTool=setTool;setTool=function(value){if(['open','block','always','behind','erase-layer'].includes(value)){previewOn=false;$('actorToggle').checked=false;}baseTool(value);};
