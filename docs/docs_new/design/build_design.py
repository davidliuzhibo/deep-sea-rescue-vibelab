"""Author the first rescue slice's executable design IR. No native/device execution."""
from pathlib import Path
import json, copy

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'demos/deep-sea-rescue/.scratch'
OUT.mkdir(parents=True,exist_ok=True)
roles='fillPrimary fillSecondary fillTertiary fillLight labelPrimaryLight labelPrimary labelSecondary labelTertiary labelQuaternary lightenHover lightenPressed error alert passable interaction dividerLine'.split()
nodes=[]; actions=[]; states=[]; transitions=[]
def node(id,kind,**kw): nodes.append(dict(id=id,kind=kind,**kw)); return id
def text(id,value='',binding=None,title=False):
    return node(id,'text',content=value,textStyle={'typography':'title' if title else 'body','color':'seaInk' if title else 'labelPrimaryLight'},**({'bindings':{'content':binding}} if binding else {}),**({} if title else {'appearance':{'fill':'fillPrimary','radiusDp':16},'layout':{'mode':'column','paddingDp':16}}))
def layout(id,children,mode='column',**kw):return node(id,'layout',children=children,layout={'mode':mode,'gapDp':16,**kw})
def button(id,label,op,**payload):
    actions.append({'id':id+'-action','type':'custom','purpose':label,'payload':{'op':op,**payload}})
    return node(id,'spatialui',component='sui-button',props={'size':'max','text':label},events={'click-action':id+'-action'},layout={'mode':'row','minHeightDp':56})
def panel(id,title,body,buttons):
    root=layout(id+'-root',[text(id+'-title',title,title=True),layout(id+'-content',body,'scroll',grow=1,minHeightDp=100,overflow='scroll'),layout(id+'-footer',buttons,'grid',columns=2)],heightDp=660,paddingDp=24)
    states.append({'id':id,'surfaceRoots':{'ocean':'ocean-root','controls':root}})
    return root
def b(id,label,target):return button(id,label,'navigate',target=target)

contract={
 'scope':'3–5 minute first-player target: one turtle rescue, not a complete multi-level product; no forced idle padding',
 'runtime':'offline; no business server, account, cloud AI or model key in APK',
 'coordinates':'right-handed metres, +Y up, -Z forward; one calibrated root for visuals, colliders and input',
 'world':{'portalSizeM':[1.5,.9],'portalCenterRelativeEyeM':[0,-.02,-1.6],'waterDepthM':1.3,'worldContentBoundsM':[[-.75,-.45,-1.27],[.75,.45,-.05]],'turtleBodyLengthM':.42,'turtleFlipperSpanM':.66,'panelCenterRelativeEyeM':[1.05,-.06,-1.45],'panelPhysicalSizeM':[.65,.52],'panelFacesEye':True,'consoleCenterRelativeEyeM':[0,-.45,-.72],'consoleSizeM':[.42,.18,.26],'bodyClearanceEstimateM':.20,'panelSamePositionInPauseAndResult':True,'activeHeadRangeTargetDeg':[-65,65],'taskTimeNearEyeLevelTarget':.85},
 'placement':{'poseMaxAgeMs':250,'wallMinimumSizeM':[1.62,1.02],'wallDistanceM':[1.1,2.8],'wallFacingMaxAngleDeg':35,'windowCenterNearEyeMaxOffsetM':.12,'planeNormalSource':'orientation plus mesh geometry, never semantic label alone','selectedIdentityStable':True,'coverage':'all four footprint corners in plane triangle union, with edge margin; not AABB-only','tableOptional':True,'tableFootprintM':[.48,.32],'tableMinimumFloorClearanceM':.35,'tableDistanceM':[.50,1.05],'fallback':'explicit virtual practice; no fabricated room surfaces','drift':'pause on missing selected plane, stale pose or material relocation; require explicit revalidation/continue'},
 'input':{'controllers':False,'systemHandRay':True,'nearHandleDrag':True,'selectDoesNotExecute':True,'translationGain':.55,'maxToolSpeedMps':.22,'maxInputDeltaM':.06,'keepToolOrientation':True,'onePointerOwner':True,'dragSlopM':.009,'normalReleaseRetainsToolPose':True,'cancelStopsMotion':True,'resumeRequiresExplicitContinue':True,'noAutomaticGrab':'only a fresh SDK pointer-down after ready/resume; never acquire from hand position or held contact'},
 'rescue':{'sequence':['inspect','brace','cut','bag','winch','farewell','result'],'inspectTargets':['front-rope','rear-rope','brace-point'],'cutOrder':['front-rope','rear-rope'],'inspectionDwellSeconds':.9,'contactRadiusM':.055,'actionDurationSeconds':1.1,'movementBeforeResult':True,'bodyProtection':True,'winch':{'buoyancyLevels':[0,1,2,3],'recommendedLevel':2,'tensionSafeMax':.75,'tensionStop':.9,'boundedWinchPulse':True,'maxPulseLiftM':.08,'stopControl':True,'route':'steer left below the beam, then lift into the safe lane','recoverableFailure':'overload/obstacle stops movement without killing the turtle; pay out or steer to recover'},'completion':'turtle physically enters safe region after both ropes, stabilizer and float; farewell finishes before result'},
 'lifecycle':{'pauseFreezesSimulation':True,'backgroundCancelsInputAndAudio':True,'restoreBeforeDefaults':True,'saveCheckpointOnPhaseChange':True,'resourceReleaseAfterEntityDestroy':True},
 'audio':{'musicDefault':True,'musicGain':.08,'sfxDefault':True,'sfxGain':.18,'licensed':'original procedural audio with documented source; no third-party music','separateSwitches':True},
 'preview':{'scope':'web_design_validation_only','simulation':'declared fixture outcomes only; native engine owns physical contact/time/collision; never treat Web action as actual rescue proof','sceneWidthDp':920,'sceneHeightDp':420,'viewBox':[0,0,920,420],'frame':[72,30,650,350],'turtle':[390,211],'armBase':[645,327],'targetPositions':{'front-rope':[335,201],'rear-rope':[438,226],'brace-point':[468,277],'float-hook':[381,169]},'colors':{'water':'waterTeal','frame':'ivory','metal':'brass','rope':'ropeGold','turtle':'shellGreen','light':'seaLight'},'sceneCaption':'深海救援站 · 小海龟的归途'}
}
node('ocean-visual','domain_visual',rendererKey='rescue-observation-window',purpose='A true three-dimensional bounded ocean and articulated tool; Web projects only declared fixture geometry.',props=contract['preview'],bindings={'phase':'ui.phase','selected':'ui.selected','feedback':'ui.feedback'},events={'target-selected':'select-visible-target'},accessibilityLabel='海龟、两根缠绕绳、残骸和机械臂的立体救援现场')
actions.append({'id':'select-visible-target','type':'custom','purpose':'只选择可见目标，不移动或作用','payload':{'op':'select'}})
layout('ocean-root',['ocean-visual'])
panel('placement','深海救援站',[text('welcome','一只小海龟被旧渔绳困住了。借助灯光、机械臂和浮袋，帮它回到海里。'),text('placement-instruction','面向前方宽阔墙面。先预览观察窗，再确认位置；可选真实桌面安放控制台。'),text('placement-status',binding='ui.feedback')],[button('scan-wall','寻找前方墙面','scan'),button('practice','虚拟练习','practice'),b('placement-settings','声音设置','settings'),b('placement-exit','离开','exit-confirm')])
panel('wall-preview','确认观察窗',[text('wall-info',binding='ui.surfaceLabel'),text('wall-guide','完整窗框应在选中的墙面范围内，海龟位于平视附近。确认后不会因候选排序而换墙。')],[button('confirm-wall','确认这面墙','confirm-wall'),button('next-wall','换一面墙','next-wall'),button('rescan-wall','重新寻找','scan'),button('wall-practice','虚拟练习','practice')])
panel('table-select','安放控制台',[text('table-guide','控制台可放在前方真实桌面上。没有合适桌面时，选择虚拟控制台；观察窗仍使用刚才确认的墙面。'),text('table-status',binding='ui.surfaceLabel')],[button('confirm-table','使用选中桌面','confirm-table'),button('next-table','换一张桌','next-table'),button('virtual-console','使用虚拟控制台','virtual-console'),b('table-back','重新选墙','placement')])
panel('ready','准备救援',[text('ready-story','看向窗内的小海龟：它的前鳍和尾部各缠着一根绳，右侧破木梁压住了绳子。'),text('ready-controls','先点目标，再点“对准已选目标”，机械臂会慢慢靠近；到位后再点操作。也可以捏住控制台上的圆柄缓慢拖动。'),text('ready-system','全程不需要手柄。若眼手点选不便，可到系统“设置 → 眼与手 → 交互方式”使用手势射线。')],[button('start','打开探照灯','start'),b('ready-settings','声音设置','settings'),b('ready-reposition','重新定位','placement'),b('ready-exit','离开','exit-confirm')])

phase_data={
 'inspect':('照见束缚','用灯照清前鳍绳、尾部绳和木梁右下方的黄色夹座；光停稳后，目标会显出标记。',[('front-rope','前鳍绳'),('rear-rope','尾部绳'),('brace-point','黄色夹座')],'照射已选目标'),
 'brace':('先稳住木梁','选择右下方木梁右下方的黄色夹座，对准后夹紧。木梁停止晃动，绳索才可以安全剪断。',[('brace-point','黄色夹座')],'夹紧托座'),
 'cut':('解除两根渔绳','先剪前鳍旁的短绳，再剪尾部绳。让绳进入钳口后再闭合；不要把钳口靠近龟壳。',[('front-rope','前鳍绳'),('rear-rope','尾部绳')],'闭合剪钳'),
 'bag':('给海龟一点浮力','把橙色浮袋挂到龟背上方的背带扣，安装后加到两档。浮袋会慢慢鼓起；接下来用绞盘抬升海龟。',[('float-hook','龟背上的背带扣')],'挂好浮袋'),
 'winch':('绕过木梁，安全上浮','先向左绕过木梁，再缓慢收缆。张力过高时停止收缆或放一点缆；海龟会随水流稍向右漂；每次收缆一小段，必要时向左修正或点“停止”。',[],'收缆一小段')}
for phase,(title,instruction,targets,executeLabel) in phase_data.items():
    content=[text(phase+'-hint',instruction),text(phase+'-selection',binding='ui.selectionLabel'),text(phase+'-feedback',binding='ui.feedback')]
    if targets:content.append(layout(phase+'-targets',[button(phase+'-'+id,label,'select',targetId=id) for id,label in targets],'grid',columns=3))
    if phase in ('bag','winch'):
        content.append(text(phase+'-gauges',binding='ui.gauges'))
        content.append(layout(phase+'-float',[button(phase+'-air-minus','少一点气','air',delta=-1),button(phase+'-air-plus','多一点气','air',delta=1)],'grid',columns=2))
    if phase=='winch':
        content.append(layout('winch-steer',[button('steer-left','向左引导','steer',delta=-1),button('steer-right','向右引导','steer',delta=1),button('pay-out','放缆一小段','winch',direction=-1),button('brake','停止收放','brake')],'grid',columns=2))
    foot=[] if phase=='winch' else [button(phase+'-aim','对准已选目标','approach')]
    foot += [button(phase+'-execute',executeLabel,'execute'),b(phase+'-pause','暂停 / 设置','pause')]
    if phase!='winch':foot.append(b(phase+'-adjust','精细调整','adjust'))
    panel(phase,title,content,foot)
panel('adjust','微调机械臂',[text('adjust-guide','每次移动一点点。工具保持朝向，返回后再明确进行操作；移动本身不会剪断绳索。'),text('adjust-status',binding='ui.selectionLabel'),layout('adjust-moves',[button('adjust-'+id,label,'nudge',axis=axis,delta=delta) for id,label,axis,delta in [('left','向左','x',-.025),('right','向右','x',.025),('up','向上','y',.025),('down','向下','y',-.025),('near','靠近窗边','z',.025),('far','深入一点','z',-.025)]],'grid',columns=3)],[button('adjust-back','返回操作','back'),b('adjust-pause','暂停','pause')])
panel('farewell','它可以自己游了',[text('farewell-story','海龟已经越过木梁。看，它正在舒展前鳍，游向窗上方的开阔水域。'),text('farewell-status',binding='ui.feedback')],[b('farewell-pause','暂停','pause')])
panel('result','小海龟，回家吧',[text('result-story','你稳住了残骸、解开渔绳，并护送海龟安全离开。窗内留下的，是一条松开的旧绳。'),text('result-record',binding='ui.result')],[button('replay','再救援一次','restart'),b('result-settings','声音设置','settings'),b('result-exit','离开','exit-confirm')])
panel('pause','救援已暂停',[text('pause-text','水流、机械臂和声音都已暂停；刚才的抓取已松开。已完成的救援步骤保留。')],[button('continue','继续救援','resume'),b('pause-settings','声音设置','settings'),b('pause-exit','离开','exit-confirm')])
panel('settings','声音设置',[text('settings-guide','低音量水下氛围与轻操作音分别控制；设置会保存。静音不影响任何救援提示。'),text('settings-values',binding='ui.audioLabel')],[button('toggle-music','切换氛围声','toggle-music'),button('toggle-sfx','切换操作音','toggle-sfx'),button('settings-back','返回','back')])
panel('tracking','定位暂时不可用',[text('tracking-body','已停止机械臂与水流，保留救援进度。看向先前墙面；定位恢复后由你决定继续。'),text('tracking-reason',binding='ui.feedback')],[button('tracking-retry','重新检查位置','revalidate'),b('tracking-reposition','重新定位','placement'),b('tracking-exit','离开','exit-confirm')])
panel('error','暂时无法开始',[text('error-reason',binding='ui.feedback'),text('error-hint','当前进度没有被清空。可以重试资源加载，或离开后再回来。')],[button('retry','重试加载','retry'),b('error-exit','离开','exit-confirm')])
panel('exit-confirm','离开救援站？',[text('exit-copy','已完成的阶段会保留，正在移动的机械臂将停下。下次进入可以继续。')],[button('confirm-exit','保存并离开','exit'),button('cancel-exit','继续留在这里','back')])
panel('exited','本次救援已保存',[text('exit-done','救援站已关闭。')],[])
ui={'phase':'placement','selected':'','selectionLabel':'尚未选中目标','feedback':'等待前方墙面与新鲜头部朝向。','surfaceLabel':'墙面 2.1 × 1.5 m · 窗中心接近平视','gauges':'浮力：0 / 3 · 张力：0%','audioLabel':'氛围声：开 · 操作音：开','result':'本次记录将在安全抵达后生成。'}
contract['preview']['shapes']=[
 {'tag':'rect','attrs':{'x':72,'y':30,'width':650,'height':350,'rx':36,'fill':'ivory'}},
 {'tag':'rect','attrs':{'x':90,'y':48,'width':614,'height':314,'rx':24,'fill':'waterTeal'}},
 {'tag':'path','attrs':{'d':'M90 320 Q210 270 340 310 T704 295 L704 362 L90 362 Z','fill':'seaInk'}},
 {'tag':'path','attrs':{'d':'M491 300 L548 149 L570 157 L514 309 Z','fill':'brass'}},
 {'tag':'ellipse','attrs':{'cx':390,'cy':211,'rx':77,'ry':47,'fill':'shellGreen'}},
 {'tag':'ellipse','attrs':{'cx':306,'cy':197,'rx':22,'ry':17,'fill':'shellGreen'}},
 {'tag':'circle','attrs':{'cx':298,'cy':191,'r':3,'fill':'seaInk'}},
 {'tag':'path','attrs':{'d':'M345 181 Q297 143 312 136 Q348 149 366 169 M351 242 Q316 274 330 279 Q365 271 377 251 M435 181 Q473 158 479 174 L457 200 M440 238 Q471 260 478 250 L458 226','fill':'shellGreen'}},
 {'tag':'path','attrs':{'d':'M364 176 L387 187 L413 179 L438 199 L429 226 L404 243 L378 237 L350 220 L349 198 Z M387 187 L378 209 L350 220 M378 209 L405 217 L413 179 M405 217 L429 226 M378 237 L378 209 M404 243 L405 217','fill':'none','stroke':'ropeGold','stroke-width':2}},
 {'tag':'path','phase':'front','attrs':{'d':'M334 180 Q357 205 334 229 L470 294','fill':'none','stroke':'ropeGold','stroke-width':5}},
 {'tag':'path','phase':'rear','attrs':{'d':'M435 185 Q455 224 433 243 L501 298','fill':'none','stroke':'ropeGold','stroke-width':5}},
 {'tag':'circle','attrs':{'cx':645,'cy':327,'r':21,'fill':'brass'}},
 {'tag':'path','attrs':{'d':'M645 327 L602 254 L551 249','fill':'none','stroke':'ivory','stroke-width':18}},
 {'tag':'circle','attrs':{'cx':602,'cy':254,'r':11,'fill':'brass'}}]
normal={'ui':ui,'contract':contract,'music':True,'sfx':True,'room':{'valid':True},'fixture':{'inspected':[],'cut':[],'air':0,'attached':False,'retrieval':0},'phaseMessages':{k:row[1] for k,row in phase_data.items()},'targetLabels':{'front-rope':'前鳍绳','rear-rope':'尾部绳','brace-point':'黄色夹座','float-hook':'龟背上的背带扣'}}
normal['messages']={'selectedPrefix':'已选：','arrived':'工具已对准。请再点本步操作。','moving':'机械臂正在靠近，请稍候。','needTarget':'请先选择本步目标。','needAim':'请先对准目标，再进行操作。','inspected':'灯光已照清此处。','unsafeOrder':'先剪前鳍旁的短绳，再剪尾部绳，避免突然拉紧。','needFloat':'先挂好浮袋，再加气。','attached':'浮袋已挂好，请慢慢加气。','airPrefix':'浮力：','airSuffix':' / 3','tensionPrefix':' · 张力：','needTwo':'建议先调到两档浮力，过多会把缆绳拉得太紧。','route':'向左绕过木梁后再收缆；过紧时停止或放缆。','safe':'海龟已进入安全水域。','record':'救援完成。已保存本次进度。','musicOn':'氛围声：开','musicOff':'氛围声：关','sfxOn':'操作音：开','sfxOff':'操作音：关','sep':' · ','practice':'虚拟练习 · 不使用真实墙面或桌面','wallReady':'选中墙面已通过预览；请确认。','tableVirtual':'观察窗使用真实墙面，控制台为虚拟摆放。','tableReal':'观察窗和控制台均使用已确认的真实表面。','trackingRestored':'位置可用，请明确继续救援。','retry':'正在重试资源加载。','paused':'已暂停，正在进行的操作已停止。'}
normal['messages']['phaseReady']='选中目标后，对准再操作；可用“精细调整”小幅移动。'
normal['messages']['winchReady']='浮袋与缆绳会共同影响路径。先让海龟移到木梁左侧。'
fallback=copy.deepcopy(normal);fallback['room']['valid']=False;fallback['ui']['feedback']='没有足够宽、朝向合适的墙面。请换一个方向，或明确选择虚拟练习。'
error=copy.deepcopy(normal);error['ui']['phase']='error';error['ui']['feedback']='海龟模型尚未加载完成；请重试。'
spec={'schemaVersion':'1.0','metadata':{'projectName':'深海救援站 · 小海龟的归途','sourceMode':'intent_only','revision':1},'experience':{'subject':'深海动物救援','user':'坐姿或原地站姿、系统手势操控的 Swan 玩家','context':'真实墙面承载观察窗，可选真实桌面支撑控制台','singleJob':'用灯、机械臂、浮力与绞盘救出一只被缠住的海龟','space':'full','form':'stage_mixed','signature':'最后一根渔绳松开后，海龟舒展鳍肢，在玩家护送下自主游离'},'theme':{'scheme':'vibrant','colorScheme':{r:{'source':'vibrant','role':r} for r in roles},'brandColors':{'seaInk':'#16363B','waterTeal':'#103D49','seaLight':'#8EC3C1','ivory':'#D9D5BD','brass':'#B29154','ropeGold':'#CC9D49','shellGreen':'#697A46'},'typography':{'title':{'family':'sans-serif','sizeDp':28,'lineHeightDp':36,'weight':600},'body':{'family':'sans-serif','sizeDp':20,'lineHeightDp':28,'weight':500},'label':{'family':'sans-serif','sizeDp':20,'lineHeightDp':26,'weight':500}},'spacing':{'small':8,'regular':16,'medium':24,'large':32}},'surfaces':[{'id':'ocean','type':'stage_mixed','defaultSize':{'width':1.5,'height':.9,'depth':1.3,'unit':'m'},'minSize':{'width':1.5,'height':.9,'depth':1.3,'unit':'m'},'maxSize':{'width':1.5,'height':.9,'depth':1.3,'unit':'m'},'rootNodeId':'ocean-root','placement':{'nativeRole':'one MR Stage with PortalWorld and PortalComponent; no flat ocean image','webProjectionWidthDp':920,'webProjectionHeightDp':420}},{'id':'controls','type':'planar','defaultSize':{'width':820,'height':660,'unit':'dp'},'minSize':{'width':640,'height':560,'unit':'dp'},'maxSize':{'width':1000,'height':800,'unit':'dp'},'rootNodeId':'placement-root','placement':{'nativeRole':'one AttachmentPanel with stable physical size and location','initial':'fresh head-aligned front before room placement','playing':'beside window facing calibrated eye; unchanged in pause/settings/result','runtimeSizing':'public dpToLength conversion, not fixed dp/metre guess'}}],'initialStateId':'placement','states':states,'transitions':transitions,'actions':actions,'dataCases':{'normal':normal,'fallback':fallback,'error':error},'nodes':nodes,'responsiveRules':[{'id':'compact','surfaceId':'controls','when':{'maxWidthDp':700},'overrides':[{'nodeId':n['id'],'layout':{'mode':'column','heightDp':560,'paddingDp':16}} for n in nodes if n['id'].endswith('-root') and n['id']!='ocean-root']+[{'nodeId':n['id'],'layout':{'mode':'grid','columns':2,'gapDp':8}} for n in nodes if n.get('layout',{}).get('mode')=='grid']}],'assets':[]}
spec['metadata']['revision']=4
for override in spec['responsiveRules'][0]['overrides']:
    if override['nodeId'].endswith('-targets'):override['layout']['columns']=3
(OUT/'design-spec.json').write_bytes((json.dumps(spec,ensure_ascii=False,indent=2)+'\n').encode())
print(json.dumps({'nodes':len(nodes),'states':len(states),'actions':len(actions),'out':str(OUT)},ensure_ascii=False))
