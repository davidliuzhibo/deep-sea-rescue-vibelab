const SPEC=JSON.parse(document.getElementById('pico-design-spec').textContent);
const nodes=new Map(SPEC.nodes.map(n=>[n.id,n]));
const acts=new Map(SPEC.actions.map(n=>[n.id,n]));
let DATA=structuredClone(SPEC.dataCases.normal), state=SPEC.initialStateId, back='placement', suspended='ready', busy=false;
const get=p=>p.split('.').reduce((a,k)=>a?.[k],DATA);
const color=k=>SPEC.theme.brandColors[k]||`var(--sui-${k.replace(/[A-Z]/g,c=>'-'+c.toLowerCase())})`;
const msg=k=>DATA.messages[k];
function enter(s){state=s;DATA.ui.phase=s;if(DATA.phaseMessages[s])DATA.ui.feedback=msg(s==='winch'?'winchReady':'phaseReady');render();}
function choose(id){DATA.ui.selected=id;DATA.ui.selectionLabel=msg('selectedPrefix')+(DATA.targetLabels[id]||id);DATA.fixture.aimed=false;render();}
function action(id,payload={}){
 const p={...(acts.get(id)?.payload||{}),...payload}, f=DATA.fixture, u=DATA.ui;
 if(fullAction(p))return;
 if(busy && !['navigate','exit','brake'].includes(p.op))return;
 switch(p.op){
 case'navigate':if(p.target==='pause'){suspended=state;u.feedback=msg('paused')}back=state;enter(p.target);break;
 case'back':enter(back);break;
 case'scan':if(!DATA.room.valid){u.feedback=SPEC.dataCases.fallback.ui.feedback;render();break;}u.feedback=msg('wallReady');enter('wall-preview');break;
 case'practice':u.surfaceLabel=msg('practice');enter('ready');break;
 case'next-wall':case'next-table':render();break;
 case'confirm-wall':if(DATA.room.valid)enter('table-select');break;
 case'confirm-table':u.surfaceLabel=msg('tableReal');enter('ready');break;
 case'virtual-console':u.surfaceLabel=msg('tableVirtual');enter('ready');break;
 case'start':enter('inspect');break;
 case'select':choose(p.targetId||payload.target);break;
 case'approach':if(!u.selected){u.feedback=msg('needTarget');render();break;}u.feedback=msg('arrived');f.aimed=true;render();break;
 case'nudge':f.aimed=false;u.feedback=msg('needAim');render();break;
 case'execute':
  if(state==='winch'){if(f.air!==2){u.feedback=msg('needTwo');render();break;}if(!f.left){u.feedback=msg('route');render();break;}f.retrieval++;if(f.retrieval>=3){enter('farewell');setTimeout(()=>{if(state==='farewell'){u.result=msg('record');enter('result')}},1200)}else render();break;}
  if(!f.aimed){u.feedback=msg('needAim');render();break;}
  if(state==='inspect'){if(!f.inspected.includes(u.selected))f.inspected.push(u.selected);u.feedback=msg('inspected');f.inspected.length===3?enter('brace'):render();}
  else if(state==='brace'&&u.selected==='brace-point'){f.braced=true;enter('cut');}
  else if(state==='cut'){if(u.selected==='rear-rope'&&!f.cut.includes('front-rope')){u.feedback=msg('unsafeOrder');render();break;}if(['front-rope','rear-rope'].includes(u.selected)&&!f.cut.includes(u.selected))f.cut.push(u.selected);f.cut.length===2?enter('bag'):render();}
  else if(state==='bag'&&u.selected==='float-hook'){f.attached=true;u.feedback=msg('attached');render();}
  break;
 case'air':if(!f.attached){u.feedback=msg('needFloat');render();break;}f.air=Math.max(0,Math.min(3,f.air+p.delta));if(state==='bag'&&f.air===2)enter('winch');else render();break;
 case'steer':f.left=p.delta<0;render();break;
 case'winch':f.retrieval=Math.max(0,f.retrieval-1);render();break;
 case'brake':render();break;
 case'resume':enter(suspended);break;
 case'revalidate':u.feedback=msg('trackingRestored');enter('pause');break;
 case'toggle-music':DATA.music=!DATA.music;render();break;
 case'toggle-sfx':DATA.sfx=!DATA.sfx;render();break;
 case'restart':DATA.fixture=structuredClone(SPEC.dataCases.normal.fixture);u.selected='';enter('ready');break;
 case'retry':u.feedback=msg('retry');enter('placement');break;
 case'exit':enter('exited');break;
 }
}
// Explicit Web fixtures: verify UI state/control wiring only, never certify native physics.
function fullAction(p){
 const f=DATA.fixture,u=DATA.ui, finish=()=>{if(!f.completed.includes(f.mission))f.completed.push(f.mission);u.result='本项完成，成果保留。';enter('result')};
 if(p.op==='chart'){if(state==='result'&&!f.completed.includes(f.mission))f.completed.push(f.mission);enter('chart');return true;}
 if(p.op==='mission'){
  const i=p.mission, required=i===0?[]:i===3?[0,1,2]:[0];
  if(!required.every(n=>f.completed.includes(n))){u.feedback='先完成航图中的前置任务。';render();return true;}
  f.mission=i;f.survey=[];f.dials=[0,0,0];f.isolated=false;f.cell=false;f.bags=[];f.left=0;f.right=0;f.height=0;f.offset=0;f.lanes=[0,0,0];f.segment=0;u.selected='';
  enter(['inspect','beacon-survey','balance-survey','escort-plan'][i]);return true;
 }
 if(p.op==='skip-farewell'&&state==='ocean-farewell'){finish();return true;}
 if(f.mission===0)return false;
 if(p.op==='execute'){
  if(state==='balance-lift'){f.height=Math.min(.28,f.height+.07);if(f.left!==2||f.right!==3){f.height=0;u.feedback='重侧要多一档，先把网笼调平。'}render();return true;}
  if(state==='escort-plan'){if(f.lanes[f.segment]!==[-1,1,-1][f.segment]){u.feedback='这段路线受阻，停下改选航标。';render();return true;}f.segment++;f.segment===3?enter('ocean-farewell'):render();return true;}
  if(!f.aimed){u.feedback=msg('needAim');render();return true;}
  if(state.endsWith('-survey')){if(!f.survey.includes(u.selected))f.survey.push(u.selected);if(f.survey.length===3)enter(f.mission===1?'beacon-power':'balance-attach');else render();return true;}
  if(state==='beacon-power'){if(!f.isolated){f.isolated=true;u.feedback='已断电，再操作一次更换电芯。';render();}else{f.cell=true;enter('beacon-align')}return true;}
  if(state==='beacon-align'){if(String(f.dials)==='1,3,2'){enter('beacon-test');setTimeout(finish,800)}else{u.feedback='三束光还未接通，请检查刻度。';render()}return true;}
  if(state==='balance-attach'){if(!f.bags.includes(u.selected))f.bags.push(u.selected);f.bags.length===2?enter('balance-lift'):render();return true;}
 }
 if(p.op==='dial'){const i=['lens-left','lens-mid','lens-right'].indexOf(u.selected);if(i>=0&&f.aimed){f.dials[i]=(f.dials[i]+p.delta+4)%4;f.dials[(i+1)%3]=(f.dials[(i+1)%3]+p.delta+4)%4;}render();return true;}
 if(p.op==='bag-air'){if(f.height>.001||f.offset>.001){u.feedback='先移回中间并降回托架，再调整气量。';render();return true;}f[p.side]=Math.max(0,Math.min(4,f[p.side]+p.delta));render();return true;}
 if(p.op==='cage-lift'){f.height=Math.max(0,f.height-.07);render();return true;}
 if(p.op==='cage-park'){if(f.height>=.23)f.offset=Math.max(0,Math.min(.4,f.offset+.08*p.direction));render();return true;}
 if(p.op==='cage-lock'){if(f.height>=.23&&f.offset>=.32)finish();else{u.feedback='先抬高，再移到右侧停放架。';render()}return true;}
 if(p.op==='route-select'){f.route=p.index;render();return true;}
 if(p.op==='route-lane'){f.lanes[f.route??f.segment]=p.lane;render();return true;}
 return false;
}
function scene(n){
 const d=n.props, ns='http://www.w3.org/2000/svg', svg=document.createElementNS(ns,'svg');svg.setAttribute('viewBox',d.viewBox.join(' '));svg.style.width='100%';svg.style.maxWidth=d.sceneWidthDp+'px';svg.setAttribute('aria-label',n.accessibilityLabel);
 const m=DATA.fixture.mission,shapes=m?[...d.shapes.slice(0,3),...(d.missionShapes[m]||[])]:d.shapes;
 for(const s of shapes){if(s.phase==='front'&&DATA.fixture.cut.includes('front-rope'))continue;if(s.phase==='rear'&&DATA.fixture.cut.includes('rear-rope'))continue;const el=document.createElementNS(ns,s.tag);for(const[k,v]of Object.entries(s.attrs))el.setAttribute(k,SPEC.theme.brandColors[v]||v);svg.append(el);}
 for(const [id,xy]of Object.entries(m?(d.missionTargets[m]||{}):d.targetPositions)){const g=document.createElementNS(ns,'circle');g.setAttribute('cx',xy[0]);g.setAttribute('cy',xy[1]);g.setAttribute('r',DATA.ui.selected===id?13:9);g.setAttribute('fill',color(DATA.ui.selected===id?'ropeGold':'seaLight'));g.setAttribute('opacity',DATA.ui.selected===id?1:.7);g.style.cursor='pointer';g.onclick=()=>action('select-visible-target',{target:id});svg.append(g);}
 return svg;
}
function renderNode(id,width){
 const n=nodes.get(id);if(!n)throw Error(id);let l={...n.layout};for(const rule of SPEC.responsiveRules)if(width<=rule.when.maxWidthDp){const o=rule.overrides.find(x=>x.nodeId===id);if(o)l={...l,...o.layout};}
 let el=n.kind==='domain_visual'?scene(n):document.createElement(n.kind==='spatialui'?n.component:'div');el.dataset.nodeId=id;
 if(n.kind==='spatialui'){for(const[k,v]of Object.entries(n.props||{}))el.setAttribute(k,v);for(const[event,act]of Object.entries(n.events||{}))el.addEventListener(event,()=>action(act));}
 else if(n.kind==='text'){el.textContent=n.bindings?.content?get(n.bindings.content):n.content;const t=SPEC.theme.typography[n.textStyle.typography];Object.assign(el.style,{fontFamily:t.family,fontSize:t.sizeDp+'px',lineHeight:t.lineHeightDp+'px',fontWeight:t.weight,color:color(n.textStyle.color)});}
 if(n.appearance?.fill){el.dataset.designSurface=id;el.style.setProperty('--pico-design-surface-fill',color(n.appearance.fill));el.style.borderRadius=(n.appearance.radiusDp||0)+'px';}
 if(l.mode){el.style.display=l.mode==='grid'?'grid':'flex';if(l.mode==='grid')el.style.gridTemplateColumns=`repeat(${l.columns||2},minmax(0,1fr))`;else el.style.flexDirection=l.mode==='row'?'row':'column';}
 for(const[k,prop]of Object.entries({gapDp:'gap',paddingDp:'padding',heightDp:'height',minHeightDp:'minHeight'}))if(l[k]!=null)el.style[prop]=l[k]+'px';
 if(l.grow)el.style.flex='1 1 0';if(l.overflow==='scroll'){el.style.overflowY='auto';el.style.flexShrink='1';}else el.style.flexShrink='0';el.style.minWidth='0';
 for(const child of n.children||[])el.append(renderNode(child,width));return el;
}
function render(){
 if(DATA.fixture.mission){const f=DATA.fixture;DATA.ui.missionGauges=f.mission===1?`刻度 ${f.dials.join(' / ')} · 目标 1 / 3 / 2`:f.mission===2?`左袋 ${f.left} · 右袋 ${f.right} · 高度 ${Math.round(f.height*100)}cm · 停放 ${Math.round(f.offset*100)}cm`:`已通过 ${f.segment} / 3 段 · 航标 ${f.lanes.join(' / ')}`;}
 DATA.ui.campaign=['海龟解困','修复引航灯','双浮袋清障','归途护送'].map((n,i)=>n+'：'+(DATA.fixture.completed.includes(i)?'已完成':'待完成')).join('\n');
 DATA.ui.audioLabel=msg(DATA.music?'musicOn':'musicOff')+msg('sep')+msg(DATA.sfx?'sfxOn':'sfxOff');DATA.ui.gauges=msg('airPrefix')+DATA.fixture.air+msg('airSuffix')+msg('tensionPrefix')+(DATA.fixture.air===3?94:38)+'%';
 const app=document.getElementById('app');app.replaceChildren();const st=SPEC.states.find(s=>s.id===state);for(const surf of SPEC.surfaces){const w=surf.id==='ocean'?DATA.contract.preview.sceneWidthDp:Math.min(surf.defaultSize.width,window.innerWidth-32);const shell=document.createElement('section');shell.dataset.surfaceId=surf.id;shell.style.width=w+'px';shell.style.maxWidth='100%';if(surf.type==='planar'){shell.className='system-window';shell.style.borderRadius='32px';}shell.append(renderNode(st.surfaceRoots[surf.id],w));app.append(shell);}app.dataset.state=state;
}
window.DESIGN_TEST={snapshot:()=>({state,data:structuredClone(DATA)}),setCase:name=>{DATA=structuredClone(SPEC.dataCases[name]);enter(DATA.ui.phase)},enter,action};
render();window.addEventListener('resize',render);
