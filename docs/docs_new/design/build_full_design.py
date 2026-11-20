"""Full-game revision, based on the preserved accepted slice; no native execution."""
from pathlib import Path
import json, copy
R=Path(__file__).resolve().parents[2]; E=R/'.scratch/deep-sea-rescue-full-20261105'; O=R/'demos/deep-sea-rescue/.scratch'
s=json.loads((E/'baseline-slice/design-spec.json').read_text(encoding='utf8'))
nodes=s['nodes']; actions=s['actions']; states=s['states']
def n(id,kind,**kw): nodes.append(dict(id=id,kind=kind,**kw));return id
def text(id,value='',binding=None,title=False):
 return n(id,'text',content=value,textStyle={'typography':'title' if title else 'body','color':'seaInk' if title else 'labelPrimaryLight'},**({'bindings':{'content':binding}} if binding else {}),**({} if title else {'appearance':{'fill':'fillPrimary','radiusDp':16},'layout':{'mode':'column','paddingDp':16}}))
def layout(id,ch,mode='column',**kw):return n(id,'layout',children=ch,layout={'mode':mode,'gapDp':16,**kw})
def button(id,label,op,**payload):
 actions.append({'id':id+'-action','type':'custom','purpose':label,'payload':{'op':op,**payload}})
 return n(id,'spatialui',component='sui-button',props={'size':'max','text':label},events={'click-action':id+'-action'},layout={'mode':'row','minHeightDp':56})
def panel(id,title,body,foot):
 root=layout(id+'-root',[text(id+'-title',title,title=True),layout(id+'-content',body,'scroll',grow=1,minHeightDp=100,overflow='scroll'),layout(id+'-footer',foot,'grid',columns=2)],heightDp=660,paddingDp=24)
 states.append({'id':id,'surfaceRoots':{'ocean':'ocean-root','controls':root}})
def nav(id,label,target):return button(id,label,'navigate',target=target)
def change(id,**kw):next(x for x in nodes if x['id']==id).update(kw)
s['metadata'].update(projectName='深海救援站 · 一条安全归途',revision=6)
s['experience'].update(singleJob='救出海龟、修复引航灯、平衡清障，再护送它沿安全航道归海',signature='同一海龟游过亲手点亮、清开的航道，修复成果留在窗外')
for case in s['dataCases'].values():
 c=case['contract']; c['scope']='Complete four-mission offline game; first-player target 15–25 minutes, unmeasured; no forced time padding'
 c['campaign']={'missions':['turtle','beacon','balance','escort'],'unlock':{'0':[],'1':[0],'2':[0],'3':[0,1,2]},'middleOrderFree':True,'switchOnlyAtCompletedAttempt':True,'partialAttemptResumable':True,'replayPreservesRecords':True,'schema':2,'legacySavePreserved':True,'resultRequiresPhysicalArrival':True}
 c['beacon']={'sequence':['survey','isolate','cell','align','test'],'targets':['power-port','lens-left','lens-mid'],'dialCount':3,'positions':4,'target':[1,3,2],'coupling':'Turning a dial also turns its right neighbour, wrapping at three','testSeconds':3,'contactRequired':True}
 c['balance']={'targets':['anchor-left','anchor-right','load-weight'],'airRange':[0,4],'heavySide':'right on first play; left on completed replay','safeAirTotal':[5,6],'requiredHeavyMinusLight':1,'rollDegreesPerImbalance':9,'maxRollDegrees':4,'liftPulseM':.07,'liftSpeedMps':.04,'parkPulseM':.08,'parkSpeedMps':.055,'lockAtHeightM':.23,'lockAtOffsetM':.32,'normalReleaseNotCompletion':True,'airAdjustOnlyOnCradle':True}
 c['escort']={'laneX':[-.28,0,.28],'waypointYZ':[[.02,-.46],[.09,-.78],[.17,-1.10]],'speedMps':.055,'animatedEnvelopeMinM':[-.146,-.098,-.129],'animatedEnvelopeMaxM':[.084,.053,.089],'approachCorridorM':.17,'groundedRockSupports':True,'checkpointOnArrival':True,'editOnlyStopped':True,'pauseNeverAutoRestartsMotion':True,'prerequisites':['beacon lit','cage parked'],'farewellSeconds':8,'skipOnlyAfterSafeArrival':True}
 case['ui'].update(campaign='海龟解困：待完成\n修复引航灯：完成解困后开放\n双浮袋清障：完成解困后开放\n归途护送：修复灯与清障后开放',missionTitle='海龟解困',missionGauges='左灯 0 / 右灯 0',missionHint='点选对象只会高亮。对准后再明确操作。')
 case['fixture'].update(mission=0,completed=[],dials=[0,0,0],survey=[],isolated=False,cell=False,bags=[],left=0,right=0,height=0,offset=0,lanes=[0,0,0],segment=0)
 case['targetLabels'].update({'power-port':'蓝色电源扣','lens-left':'左侧导光轮','lens-mid':'中间导光轮','lens-right':'右侧导光轮','anchor-left':'左侧吊环','anchor-right':'右侧吊环','load-weight':'右侧配重','route-0':'近处航标','route-1':'拱门航标','route-2':'出口航标'})
change('welcome',content='风浪留下的旧绳和网笼挡住了海龟归途。救出它，点亮引航灯、移开网笼，再送它回到开阔海流。')
change('ready-story',content='这班值守包含四项任务。先救出海龟；引航灯与清障可以任选先后，最后护送归海。中途离开会保留当前工作。')
change('ready-title',content='准备开始值守')
change('result-title',content='本项救援完成')
change('result-story',content='本项成果已记入航图。返回任务航图继续工作，或者重玩已经完成的任务。')
change('farewell-story',content='海龟舒展开前鳍，正在游向安全的等候区。它的回海航道还需要引航灯和清障。')
change('result-footer',children=[button('chart-open','打开任务航图','chart'),nav('result-settings','声音设置','settings'),nav('result-exit','离开','exit-confirm')])
# Remove newly duplicated IDs from superseded footer controls.
for key in ['nodes','actions']:
 d={x['id']:x for x in s[key]};s[key]=list(d.values())
nodes=s['nodes'];actions=s['actions']
panel('chart','一条安全归途',[text('chart-records',binding='ui.campaign'),text('chart-rule','完成海龟解困后，灯塔和清障任选先后；两项都完成才可护送。进行中的任务先继续或重开，不能直接丢弃。'),layout('chart-choices',[button('mission-'+str(i),t,'mission',mission=i) for i,t in enumerate(['海龟解困','修复引航灯','双浮袋清障','归途护送'])],'grid',columns=2),text('chart-status',binding='ui.feedback')],[button('chart-continue','继续当前任务','resume'),nav('chart-exit','保存离开','exit-confirm')])
phase_data={
 'beacon-survey':('引航灯为何熄灭','先检查蓝色电源扣、左侧和中间导光轮。灯的电源与齿轮互相独立，先查明再修。',['power-port','lens-left','lens-mid'],'照清此处'),
 'beacon-power':('先断电，再换电芯','选蓝色电源扣，对准后先断电；再操作一次换入备用电芯。不要在通电时拆卸。',['power-port'],'断电 / 更换电芯'),
 'beacon-align':('把三束光接起来','三个导光轮相互咬合：转动一只，也带动它右边那只；最右轮带动最左轮。目标刻度依次为 1、3、2。选轮、对准，再转一格。',['lens-left','lens-mid','lens-right'],'测试光路'),
 'balance-survey':('看清网笼的偏载','检查两侧吊环和配重。配重在哪一侧，那一侧就需要多一档浮力；第一轮配重在右边。',['anchor-left','anchor-right','load-weight'],'照清此处'),
 'balance-attach':('先挂好两只浮袋','分别选中两侧吊环，对准后挂袋。两袋都挂好之前不会提升。',['anchor-left','anchor-right'],'挂好这侧浮袋'),
 'balance-lift':('让网笼平稳离开航道','每次只调一侧气量。重侧比轻侧多一档，总量 5–6 档，笼子接近平衡时再提升。抬高后向右停放，最后锁定。如需重新调气，先回到中间、降回托架。',[],'提升一小段'),
 'escort-plan':('读航道，再放行','航标依次穿过三段水域：近处避开右岩，中段绕过左岩，出口避开中央横流。为下一段选择左、中或右；海龟真正抵达后才进入下一段。',[],'引导下一段')}
for phase,(title,copy_,targets,execute) in phase_data.items():
 content=[text(phase+'-hint',copy_),text(phase+'-status',binding='ui.feedback'),text(phase+'-gauges',binding='ui.missionGauges')]
 if targets:content.append(layout(phase+'-targets',[button(phase+'-'+t,s['dataCases']['normal']['targetLabels'][t],'select',targetId=t) for t in targets],'grid',columns=3))
 if phase=='beacon-align':content.append(layout('beacon-turns',[button('dial-minus','逆时针一格','dial',delta=-1),button('dial-plus','顺时针一格','dial',delta=1)],'grid',columns=2))
 if phase=='balance-lift':
  content.append(layout('balance-air',[button('bag-'+side+str(d),label,'bag-air',side=side,delta=d) for side,label1 in [('left','左袋'),('right','右袋')] for d,label in [(-1,label1+'减气'),(1,label1+'加气')]],'grid',columns=2))
  content.append(layout('balance-park',[button('cage-down','降低一小段','cage-lift',direction=-1),button('cage-right','向右停放','cage-park',direction=1),button('cage-left','往左收回','cage-park',direction=-1),button('cage-lock','锁住停放架','cage-lock')],'grid',columns=2))
 if phase=='escort-plan':
  content.append(layout('route-select',[button('route-'+str(i),l,'route-select',index=i) for i,l in enumerate(['近处航标','拱门航标','出口航标'])],'grid',columns=3))
  content.append(layout('route-lanes',[button('lane-'+str(i+1),l,'route-lane',lane=i) for i,l in [(-1,'左侧'),(0,'中央'),(1,'右侧')]],'grid',columns=3))
 foot=([button(phase+'-aim','对准已选目标','approach')] if targets else [button(phase+'-stop','停止移动','brake')])+[button(phase+'-execute',execute,'execute'),nav(phase+'-pause','暂停 / 提示','pause')]
 panel(phase,title,content,foot)
panel('beacon-test','引航光正在穿过透镜',[text('beacon-test-copy','三道导光轮已经对齐。光束依次经过三段连接；三段全部亮起，引航灯才算修复。'),text('beacon-test-status',binding='ui.missionGauges')],[nav('beacon-test-pause','暂停','pause')])
panel('ocean-farewell','归途已经打开',[text('ocean-farewell-copy','它游过了你点亮的引航灯，也通过了你移开的网笼。现在，海龟可以回到开阔海流了。')],[button('farewell-skip','完成告别','skip-farewell'),nav('final-pause','暂停','pause')])
panel('restart-confirm','重开当前任务？',[text('restart-warning','只重开当前任务。已完成的其他任务与最好记录保留；本次尚未完成的步骤将重置。')],[button('restart-current','确认重开','restart'),button('restart-cancel','返回','back')])
change('pause-footer',children=[button('continue','继续当前任务','resume'),nav('pause-help','给我一点提示','help'),nav('pause-restart','重开当前任务','restart-confirm'),nav('pause-exit-full','保存离开','exit-confirm')])
panel('help','看看现场的线索',[text('help-specific',binding='ui.missionHint'),text('help-input','点选只高亮。对准需要一点时间，到位后再操作；拖动圆柄只是另一条微调路径，不是通关必需。')],[button('help-back','返回','back'),button('help-resume','继续当前任务','resume')])
# All transitions and responsive grid rules stay explicit and executable.
s['responsiveRules']=[{'id':'narrow-controls','surfaceId':'controls','when':{'maxWidthDp':700},'overrides':[{'nodeId':x['id'],'layout':{'mode':'grid','columns':2}} for x in nodes if x.get('layout',{}).get('columns',0)>2 and x['id']!='inspect-targets']}]
s['transitions']=[] # Domain/custom actions are declared on their owning nodes; no fake static destination.
for x in states:
 if x['id'] in phase_data:s['dataCases']['normal']['phaseMessages'][x['id']]=phase_data[x['id']][1]
visual=next(n for n in nodes if n['id']=='ocean-visual')
visual['props']['missionShapes']={
 '1':[{'tag':'path','attrs':{'d':'M180 255 L180 300 L590 300 L590 200','fill':'none','stroke':'brass','stroke-width':18}}]+[{'tag':'circle','attrs':{'cx':245+i*125,'cy':200,'r':49,'fill':'ivory','stroke':'brass','stroke-width':12}} for i in range(3)],
 '2':[{'tag':'path','attrs':{'d':'M220 230 L220 300 L560 300 L560 230 Z M280 230 L280 300 M350 230 L350 300 M420 230 L420 300 M490 230 L490 300','fill':'none','stroke':'brass','stroke-width':10}}]+[{'tag':'ellipse','attrs':{'cx':v,'cy':165,'rx':36,'ry':57,'fill':'ropeGold'}} for v in [250,530]],
 '3':[{'tag':'path','attrs':{'d':'M170 310 L290 225 L500 165 L370 90','fill':'none','stroke':'seaLight','stroke-width':5}}]+[{'tag':'circle','attrs':{'cx':x,'cy':y,'r':14,'fill':'ropeGold'}} for x,y in [(290,225),(500,165),(370,90)]]}
visual['props']['missionTargets']={'1':{'power-port':[180,265],'lens-left':[245,200],'lens-mid':[370,200],'lens-right':[495,200]},'2':{'anchor-left':[250,225],'anchor-right':[530,225],'load-weight':[490,290]},'3':{'route-0':[290,225],'route-1':[500,165],'route-2':[370,90]}}
O.mkdir(exist_ok=True,parents=True)
for key in ['nodes','actions']:s[key]=list({x['id']:x for x in s[key]}.values())
(O/'design-spec.json').write_bytes((json.dumps(s,ensure_ascii=False,indent=2)+'\n').encode())
print(len(s['nodes']),'nodes',len(states),'states',len(s['actions']),'actions')
