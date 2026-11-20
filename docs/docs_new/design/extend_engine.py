from pathlib import Path
R=Path(__file__).resolve().parents[2];p=R/'demos/deep-sea-rescue/app/src/main/java/com/example/deepsearescue/pcd0b39b0/domain/RescueEngine.kt'
s=p.read_text(encoding='utf8')
s=s.replace('import kotlin.math.abs','import com.example.deepsearescue.pcd0b39b0.domain.model.MissionGeometry as M\nimport com.example.deepsearescue.pcd0b39b0.domain.usecase.CampaignUseCase\nimport kotlin.math.abs')
s=s.replace('var progress=initial;', 'var progress=CampaignUseCase.record(initial);')
s=s.replace('private var currentStrong=false','private var currentStrong=false\n    private var cageLift=0.0\n    private var cagePark=0.0\n    private var escorting=false\n    fun targetAllowed(id:String)=G.targetAllowed(progress.phase,id)||M.allowed(progress.phase,id)\n    fun targetPosition(id:String)=G.targets[id]?:M.targets[id]\n    fun targetName(id:String)=G.names[id]?:M.names[id]?:id\n    private fun contact(id:String)=!moving&&pointer==null&&(progress.tool-(targetPosition(id)?:return false)).length()<=.055')
s=s.replace('G.targetAllowed(progress.phase,id)','targetAllowed(id)')
# The forwarding method must retain the actual original predicate.
s=s.replace('fun targetAllowed(id:String)=targetAllowed(id)||','fun targetAllowed(id:String)=G.targetAllowed(progress.phase,id)||')
s=s.replace('${G.names[id]}','${targetName(id)}').replace('G.targets.getValue(id)','requireNotNull(targetPosition(id))')
s=s.replace('if(progress.phase==P.WINCH){reel(1);return}', 'if(progress.phase==P.WINCH){reel(1);return}\n        if(progress.phase==P.BALANCE_LIFT){liftCage(1);return}\n        if(progress.phase==P.ESCORT_PLAN){escort();return}')
s=s.replace('if(progress.phase==P.INSPECT&&id in progress.inspected)', 'if(progress.phase==P.BEACON_ALIGN){testBeacon();return}\n        if(progress.phase==P.INSPECT&&id in progress.inspected)')
s=s.replace('if(progress.phase==P.INSPECT).9 else 1.1','if(progress.phase in listOf(P.INSPECT,P.BEACON_SURVEY,P.BALANCE_SURVEY)).9 else 1.1')
s=s.replace('listOf(P.INSPECT,P.BRACE,P.CUT,P.BAG)', 'listOf(P.INSPECT,P.BRACE,P.CUT,P.BAG,P.BEACON_SURVEY,P.BEACON_POWER,P.BEACON_ALIGN,P.BALANCE_SURVEY,P.BALANCE_ATTACH)')
s=s.replace('fun brake(){reel=0.0;', 'fun brake(){cageLift=0.0;cagePark=0.0;escorting=false;reel=0.0;')
s=s.replace('fun stop(){pointer=null;', 'fun stop(){cageLift=0.0;cagePark=0.0;escorting=false;pointer=null;')
a=s.index('    fun restart()');b=s.index('    fun tick(',a)
s=s[:a]+'''    fun restart(){stop();progress=CampaignUseCase.fresh(progress);selected=null;tension=0.0;currentStrong=false;message="本次任务已重开；其他任务记录保留。"}
    fun startMission(id:Int):Boolean {val next=CampaignUseCase.start(progress,id)?:return false;stop();progress=next;selected=null;tension=0.0;message="${CampaignUseCase.titles[id]}：先读现场说明，再选择目标。";sound("progress");return true}
'''+s[b:]
s=s.replace('else if(G.bodyContact(next,progress.turtle))','else if(progress.campaign.mission==0&&G.bodyContact(next,progress.turtle))')
s=s.replace('        if(progress.phase==P.WINCH){\n','        if(progress.campaign.mission>0){stepMission(dt);return}\n        if(progress.phase==P.WINCH){\n')
s=s.replace('        else->Unit\n    }}','        else->completeMissionAction(id)\n    }}')
s=s.replace('progress=progress.copy(phase=p);message=m;', 'progress=CampaignUseCase.record(progress.copy(phase=p));message=m;')
insert='''
    private fun completeMissionAction(id:String){val w=progress.work;when(progress.phase){
        P.BEACON_SURVEY,P.BALANCE_SURVEY->{val inspected=w.surveyed+id;progress=progress.copy(work=w.copy(surveyed=inspected));sound("inspect");if(inspected.size==3)change(if(progress.phase==P.BEACON_SURVEY)P.BEACON_POWER else P.BALANCE_ATTACH,"检查完成。按提示准备设备，再进行作业。")else hint("此处已经照清，请检查其余位置。")}
        P.BEACON_POWER->{if(!w.isolated){progress=progress.copy(work=w.copy(isolated=true));hint("电源已断开。再操作一次，换入蓝色备用电芯。");sound("clamp")}else{progress=progress.copy(work=w.copy(cell=true));change(P.BEACON_ALIGN,"备用电芯已装好。三轮联动，请把刻度调成 1、3、2。")}}
        P.BALANCE_ATTACH->{progress=progress.copy(work=w.copy(bags=w.bags+id));sound("clamp");if(progress.work.bags.size==2)change(P.BALANCE_LIFT,"两个浮袋都已挂好。配重侧多一档气，先调平再提升。")else hint("这侧浮袋已挂好，再安装另一侧。")}
        else->Unit
    }}
    fun dial(delta:Int){if(!active||progress.phase!=P.BEACON_ALIGN)return
        val id=selected?:return hint("先选一只导光轮，对准后再转动。")
        val i=listOf("lens-left","lens-mid","lens-right").indexOf(id)
        if(i<0||!contact(id)||acting)return hint("机械臂需要先对准并停稳。")
        val values=progress.work.dials.toMutableList();for(j in listOf(i,(i+1)%3))values[j]=(values[j]+delta.coerceIn(-1,1)+4)%4
        progress=progress.copy(work=progress.work.copy(dials=values));hint("这只轮与右邻一起转了一格。目标刻度：1、3、2。");sound("clamp")
    }
    private fun testBeacon(){if(progress.work.dials!=listOf(1,3,2))return mistake("光路还没有接通。每次转动会同时带动右邻；目标是 1、3、2。")
        if(!progress.work.cell||!progress.work.isolated)return
        change(P.BEACON_TEST,"光束正在穿过三只透镜，抵达远端灯标。")}
    fun bagAir(side:String,delta:Int){if(!active||progress.phase!=P.BALANCE_LIFT||side !in listOf("left","right"))return
        brake();val w=progress.work;progress=progress.copy(work=if(side=="left")w.copy(leftAir=(w.leftAir+delta.coerceIn(-1,1)).coerceIn(0,4))else w.copy(rightAir=(w.rightAir+delta.coerceIn(-1,1)).coerceIn(0,4)))
        hint(if(M.balanced(progress.work))"网笼已平衡，可以小段提升。"else "${if(w.heavyRight)"右"else"左"}侧配重较重，需要比另一侧多一档气。");sound("air")}
    fun liftCage(direction:Int){if(!active||progress.phase!=P.BALANCE_LIFT)return
        if(direction>0&&!M.balanced(progress.work))return mistake("自动保护：先把两袋总量调到 5–6 档，重侧多一档，笼子调平再升。")
        if(direction<0&&progress.work.cageOffset>.03)return hint("先把网笼移回吊升位置，再降低。")
        cageLift=direction.coerceIn(-1,1)*.07;cagePark=0.0;hint("网笼正在小段升降，可以随时停止。")}
    fun parkCage(direction:Int){if(!active||progress.phase!=P.BALANCE_LIFT)return
        if(progress.work.cageHeight<.23||!M.balanced(progress.work))return mistake("还没有离开卡住的底座。先调平并提升到 23 厘米以上。")
        cagePark=direction.coerceIn(-1,1)*.08;cageLift=0.0;hint("正在向停放架平移；到位后点“锁住停放架”。")}
    fun lockCage(){if(!active||progress.phase!=P.BALANCE_LIFT)return
        val w=progress.work;if(w.cageHeight<.23||w.cageOffset<.32||!M.balanced(w)||abs(cagePark)>.00001)return hint("网笼需平稳停在右侧架上：抬高 23 厘米、平移 32 厘米，等它停稳。")
        progress=progress.copy(work=w.copy(locked=true));change(P.RESULT,"网笼已锁定在停放架，航道真正打开了。")}
    fun routeSelect(index:Int){if(!active||progress.phase!=P.ESCORT_PLAN||escorting||index !in progress.work.segment..2)return
        progress=progress.copy(work=progress.work.copy(routeSelected=index));hint("选中第 ${index+1} 个航标。选择路线不会立即放行海龟。");sound("select")}
    fun routeLane(lane:Int){if(!active||progress.phase!=P.ESCORT_PLAN||escorting||lane !in -1..1)return
        val w=progress.work;if(w.routeSelected<w.segment)return
        val a=w.lanes.toMutableList();a[w.routeSelected]=lane;progress=progress.copy(work=w.copy(lanes=a));hint("航标位置已设定；准备好后点“引导下一段”。");sound("select")}
    private fun escort(){if(!progress.campaign.completed.containsAll(listOf(0,1,2)))return
        if(progress.work.segment>=3||escorting)return
        escorting=true;hint("海龟正在跟随下一处灯标；遇阻会停下，请观察真实路径。")}
    fun skipFarewell(){if(active&&progress.phase==P.OCEAN_FAREWELL&&progress.work.segment==3){progress=progress.copy(farewell=8.0);change(P.RESULT,"全部救援完成。海龟已沿安全航道返回海流。")} }
    private fun stepMission(dt:Double){val w=progress.work
        if(progress.phase==P.BEACON_TEST){val t=(w.beamTime+dt).coerceAtMost(3.0);progress=progress.copy(work=w.copy(beamTime=t));if(t>=3)change(P.RESULT,"引航灯已点亮，光路会留在最后的归途里。")}
        if(progress.phase==P.BALANCE_LIFT){
            if(abs(cageLift)>.000001){val d=min(abs(cageLift),.04*dt)*if(cageLift<0)-1 else 1
                val y=(w.cageHeight+d).coerceIn(0.0,.28);progress=progress.copy(work=w.copy(cageHeight=y));cageLift-=d;if(y==0.0||y==.28)cageLift=0.0}
            if(abs(cagePark)>.000001){val d=min(abs(cagePark),.055*dt)*if(cagePark<0)-1 else 1
                val x=(progress.work.cageOffset+d).coerceIn(0.0,.40);progress=progress.copy(work=progress.work.copy(cageOffset=x));cagePark-=d;if(x==0.0||x==.40)cagePark=0.0}
        }
        if(progress.phase==P.ESCORT_PLAN&&escorting){
            val target=M.node(w.segment,w.lanes[w.segment]);val next=w.escort.moveTo(target,.055*dt);val obstacle=M.blocked(next)
            if(obstacle!=null){escorting=false;progress=progress.copy(work=w.copy(escort=w.checkpoint));mistake("保护停航：$obstacle 挡住了路线。已退回上一安全灯标，改线后重新放行。");return}
            progress=progress.copy(work=w.copy(escort=next))
            if((next-target).length()<.0001){escorting=false;val count=w.segment+1;progress=progress.copy(work=progress.work.copy(segment=count,checkpoint=next,routeSelected=count.coerceAtMost(2)));sound("progress");if(count==3)change(P.OCEAN_FAREWELL,"海龟已越过最后的航标，正在回到海流。")else hint("第 $count 段已安全通过。检查下一段航标，再明确放行。")}
        }
        if(progress.phase==P.OCEAN_FAREWELL){progress=progress.copy(farewell=(progress.farewell+dt).coerceAtMost(8.0));if(progress.farewell>=8)change(P.RESULT,"全部救援完成。引航灯和通行航道会保留。")}
    }
'''
s=s.replace('    private fun change(p:P,m:String)',insert+'\n    private fun change(p:P,m:String)')
# contact uses a block body to allow early return.
s=s.replace('private fun contact(id:String)=!moving&&pointer==null&&(progress.tool-(targetPosition(id)?:return false)).length()<=.055','private fun contact(id:String):Boolean {val t=targetPosition(id)?:return false;return !moving&&pointer==null&&(progress.tool-t).length()<=.055}')
p.write_bytes(s.encode())
print('extended production engine')
