package com.vrgames.deeprescue.domain

import com.vrgames.deeprescue.domain.model.RescueGeometry as G
import com.vrgames.deeprescue.domain.model.RescuePhase as P
import com.vrgames.deeprescue.domain.model.RescueProgress
import com.vrgames.deeprescue.domain.model.V3
import com.vrgames.deeprescue.domain.model.MissionGeometry as M
import com.vrgames.deeprescue.domain.usecase.CampaignUseCase
import kotlin.math.abs
import kotlin.math.min

/** Deterministic fixed-step production simulation. UI/OS do not set completion flags. */
class RescueEngine(initial:RescueProgress=RescueProgress()) {
    var progress=CampaignUseCase.record(initial);private set
    var selected:String?=null;private set
    var message="请选择需要照清的位置。";private set
    var tension=0.0;private set
    var active=false;private set
    var moving=false;private set
    var acting=false;private set
    var effect="";private set
    var effectId=0L;private set
    private var goal:V3?=null
    private var actTime=0.0
    private var targetAtAction:String?=null
    private var reel=0.0
    private var steer=0.0
    private var accumulator=0.0
    private var pointer:Long?=null
    private var rawLast=V3()
    private var currentStrong=false
    private var cageLift=0.0
    private var cagePark=0.0
    private var escorting=false
    private var doOnArrival=false
    fun targetAllowed(id:String)=G.targetAllowed(progress.phase,id)||M.allowed(progress.phase,id)
    fun targetPosition(id:String)=if(id=="load-weight"&&!progress.work.heavyRight)M.targets[id]?.let{it.copy(x=-it.x)}else G.targets[id]?:M.targets[id]
    fun targetName(id:String)=G.names[id]?:M.names[id]?:id
    /**
     * The target of this step the tool is nearest, if within [r] metres — among those still to do, and in an ordered step
     * only the next one (v1.2.1: letting go near 「尾部绳」 before 「前鳍绳」 cut it and counted a mistake).
     */
    fun nearestAllowed(r:Double):String?=nearestOf(StepFocus.reachable(progress),r)
    /** An allowed target near the tool that may not be worked now (cut out of order, or already done), if any. */
    private fun nearestOutOfTurn(r:Double):String?=nearestOf(StepFocus.order(progress).filter{it !in StepFocus.reachable(progress)},r)
    private fun nearestOf(ids:List<String>,r:Double)=ids.mapNotNull{id->targetPosition(id)?.let{id to (progress.tool-it).length()}}.filter{it.second<=r}.minByOrNull{it.second}?.first
    /** Says why the arm let go next to [id] does nothing, and what is next. Never counts as a mistake. */
    private fun outOfTurn(id:String){val next=StepFocus.reachable(progress).firstOrNull()?.let{targetName(it)}
        val name=targetName(id)
        hint(when{
            progress.phase==P.BAG&&progress.attached->"浮袋已挂好。把蓝色「充气」旋钮往上推两下。"
            StepFocus.done(progress,id)->when(progress.phase){P.CUT->"${name}已经剪开了。";P.BALANCE_ATTACH->"这侧浮袋已经挂好了。";else->"${name}已经照清了。"}+(next?.let{"下一处：$it。"}?:"")
            next!=null->"这里是${name}，现在还不能动它：先剪「$next」。把机械臂移到${next}旁边再松手。"
            else->"这里不用再操作。"})}
    private fun contact(id:String):Boolean {val t=targetPosition(id)?:return false;return !moving&&pointer==null&&(progress.tool-t).length()<=CONTACT}
    fun setActive(value:Boolean){active=value;if(!value)stop()}
    fun settings(music:Boolean,sfx:Boolean){progress=progress.copy(settings=progress.settings.copy(music=music,sfx=sfx))}
    fun select(id:String){if(!active||!targetAllowed(id))return
        // v1.2.1: a target already done (a cut rope, the hooked buckle, a spot lit) is not picked again.
        if(StepFocus.done(progress,id)){outOfTurn(id);return}
        cancelAction();goal=null;moving=false;selected=id;message="机械臂正在去${targetName(id)}……";sound("select")}
    /** The whole step from one press: the arm goes to the target and does what this step does there. */
    fun go(id:String){select(id);if(selected!=id)return;approach();doOnArrival=moving&&progress.phase in ONE_PRESS}
    fun approach(){if(!active)return;val id=selected?:return hint("请先选择本步目标。")
        if(!targetAllowed(id))return
        cancelAction();goal=requireNotNull(targetPosition(id));moving=true;message="机械臂正在去${targetName(id)}……"}
    fun execute(){
        if(!active)return
        if(progress.phase==P.WINCH){reel(1);return}
        if(progress.phase==P.BALANCE_LIFT){liftCage(1);return}
        if(progress.phase==P.ESCORT_PLAN){escort();return}
        // Nothing picked but the arm brought there with the joystick: work on the target it is at (2026-11-13 device test:
        // the arm at the turtle's back, 「执行」 did nothing, because only a target picked first could be worked on).
        val id=selected?:nearestAllowed(NEAR)?.also{n->selected=n;targetPosition(n)?.let{progress=progress.copy(tool=it)}}
            ?:return nearestOutOfTurn(NEAR)?.let{outOfTurn(it)}?:hint("先用摇杆把机械臂移到要操作的目标旁边，或者点一下那个目标。")
        if(!targetAllowed(id))return
        // v1.2 (PICO 1118: 「机械臂触发执行的判定比较严格」): still moving, it stops here and works; near enough, it is drawn
        // onto the target (within [NEAR]); only farther off is it told to come closer.
        if(moving||pointer!=null){goal=null;moving=false;pointer=null}
        val gap=(progress.tool-requireNotNull(targetPosition(id))).length()
        if(gap>NEAR)return hint("工具离目标还有 ${(gap*100).toInt()} 厘米：用摇杆再靠近一点（16 厘米内会自动吸过去），或者点一下那个目标。")
        if(gap>CONTACT)progress=progress.copy(tool=requireNotNull(targetPosition(id)))
        if(progress.phase==P.CUT&&(!progress.braced||id!=listOf("front-rope","rear-rope").getOrNull(progress.cuts)))return mistake("顺序不对：先点「前鳍绳」，再点「尾部绳」。")
        if(progress.phase==P.BAG&&progress.attached)return hint("浮袋已挂好，把「充气」旋钮推到两档。")
        if(progress.phase==P.BEACON_ALIGN){testBeacon();return}
        if(progress.phase==P.INSPECT&&id in progress.inspected)return hint("这里已经照清，可以检查下一处。")
        actTime=0.0;acting=true;targetAtAction=id;message=if(progress.phase in listOf(P.INSPECT,P.BEACON_SURVEY,P.BALANCE_SURVEY))"探照灯正在照${targetName(id)}……"else"正在操作${targetName(id)}……"
    }
    fun air(delta:Int){if(!active||progress.phase !in listOf(P.BAG,P.WINCH))return
        if(!progress.attached)return hint("先把浮袋挂在背带扣上。")
        progress=progress.copy(air=(progress.air+delta.coerceIn(-1,1)).coerceIn(0,3));sound("air")
        if(progress.phase==P.BAG&&progress.air==2)change(P.WINCH,"浮袋已就位。海龟得先从木梁左边绕过去，再一点点收缆：找找操纵台上管方向的拉杆和管缆绳的拉杆。")
        else if(progress.air==3)hint("浮力偏大，缆绳容易拉紧。可以少放一点气。")
    }
    fun steer(delta:Int){if(!active||progress.phase!=P.WINCH)return;steer=delta.coerceIn(-1,1)*.085;reel=0.0;message="正在平稳引导。"}
    fun reel(direction:Int){if(!active||progress.phase!=P.WINCH)return
        if(!progress.attached||progress.cuts!=2||!progress.braced)return
        reel=direction.coerceIn(-1,1)*.08;steer=0.0;message=if(direction>0)"收缆一小段。遇到木梁会自动刹住。"else"缓慢放缆，降低张力。"}
    fun brake(){doOnArrival=false;cageLift=0.0;cagePark=0.0;escorting=false;reel=0.0;steer=0.0;goal=null;moving=false;cancelAction();hint("已停止，位置保持。")}
    fun nudge(delta:V3){if(!active||!delta.finite()||progress.phase !in listOf(P.INSPECT,P.BRACE,P.CUT,P.BAG,P.BEACON_SURVEY,P.BEACON_POWER,P.BEACON_ALIGN,P.BALANCE_SURVEY,P.BALANCE_ATTACH))return
        cancelAction();goal=progress.tool+delta.capped(.025);moving=true}
    fun dragBegin(id:Long):Boolean {if(!active||pointer!=null||progress.phase !in listOf(P.INSPECT,P.BRACE,P.CUT,P.BAG,P.BEACON_SURVEY,P.BEACON_POWER,P.BEACON_ALIGN,P.BALANCE_SURVEY,P.BALANCE_ATTACH))return false
        doOnArrival=false;pointer=id;rawLast=V3();goal=null;moving=false;cancelAction();return true}
    fun dragMove(id:Long,total:V3){if(!active||pointer!=id||!total.finite())return
        val delta=(total-rawLast).capped(.06)*.55;rawLast=total
        goal=(goal?:progress.tool)+delta;moving=true}
    fun dragEnd(id:Long,cancelled:Boolean){if(pointer!=id)return;pointer=null;goal=null;moving=false;cancelAction();if(cancelled){hint("操作已取消，机械臂停住。");return}
        // v1.2: let go near the step's target: the arm takes hold of it and works at once (no 「执行」 to find).
        // v1.2.1: only a target still to do (in an ordered step only the next one); near any other it only says why not.
        val near=nearestAllowed(NEAR)
        if(near!=null&&StepFocus.puzzle(progress)){selected=near;targetPosition(near)?.let{progress=progress.copy(tool=it)};hint(arrived())}
        else if(near!=null){selected=near;targetPosition(near)?.let{progress=progress.copy(tool=it)};message="已靠近${targetName(near)}，自动执行。";execute()}
        else nearestOutOfTurn(NEAR)?.let{outOfTurn(it)}?:hint("位置已保留。移到目标 16 厘米内松手，就会自动执行。")}
    fun stop(){doOnArrival=false;cageLift=0.0;cagePark=0.0;escorting=false;pointer=null;goal=null;moving=false;cancelAction();reel=0.0;steer=0.0;accumulator=0.0}
    private fun cancelAction(){acting=false;actTime=0.0;targetAtAction=null}
    fun restart(){stop();progress=CampaignUseCase.fresh(progress);selected=null;tension=0.0;currentStrong=false;message="本次任务已重开；其他任务记录保留。"}
    fun startMission(id:Int):Boolean {val next=CampaignUseCase.start(progress,id)?:return false;stop();progress=next;selected=null;tension=0.0;message="${CampaignUseCase.titles[id]}：先读现场说明，再选择目标。";sound("progress");return true}
    fun tick(seconds:Double){if(!active||!seconds.isFinite()||seconds<=0)return
        // A suspended frame cannot teleport objects, increase progress or silently replay seconds.
        accumulator+=min(seconds,.10)
        while(accumulator+1e-9>=1.0/120){step(1.0/120);accumulator-=1.0/120}
    }
    private fun step(dt:Double){
        if(progress.phase!=P.RESULT)progress=progress.copy(elapsed=progress.elapsed+dt)
        val destination=goal
        if(destination!=null){val next=progress.tool.moveTo(destination,.22*dt)
            if(!G.insideTool(next)){doOnArrival=false;goal=null;moving=false;mistake("已到机械臂活动边界，请向内移动，或重新对准目标。")}
            else if(progress.campaign.mission==0&&G.bodyContact(next,progress.turtle)){doOnArrival=false;goal=null;moving=false;mistake("保护海龟：工具已停住。请从外侧接近，或重新对准目标。")}
            else {progress=progress.copy(tool=next);if((next-destination).length()<.0001){goal=null;moving=false;if(doOnArrival){doOnArrival=false;execute()}else hint(arrived())}}}
        if(acting){val id=targetAtAction
            if(id==null||selected!=id||moving||(progress.tool-requireNotNull(targetPosition(id))).length()>CONTACT){cancelAction();return}
            actTime+=dt
            if(actTime>=if(progress.phase in listOf(P.INSPECT,P.BEACON_SURVEY,P.BALANCE_SURVEY)).9 else 1.1){cancelAction();completeAction(id)}}
        if(progress.campaign.mission>0){stepMission(dt);return}
        if(progress.phase==P.WINCH){
            val current=RescueCurrent.strength(progress.elapsed)
            if(current>.8&&!currentStrong){hint("水流正在增强，海龟会稍向右漂。留意张力，必要时向左引导。");sound("warning")}
            currentStrong=current>.8
            val drift=progress.turtle+V3(RescueCurrent.pushMetresPerSecond(progress.elapsed)*dt,0.0,0.0)
            if(drift.x<=.18&&!G.beamContact(drift))progress=progress.copy(turtle=drift)
            val base=when(progress.air){0->.60;1->.30;2->.18;else->.80}+current*.12
            val blockedUp=reel>0&&(G.beamContact(progress.turtle+V3(0.0,.002,0.0))||progress.air==0||progress.air==3)
            if(!blockedUp)tension+=(base-tension)*min(1.0,dt*2.4)
            if(abs(steer)>.000001){val amount=min(abs(steer),.085*dt)*if(steer<0)-1 else 1
                val next=progress.turtle+V3(amount,0.0,0.0)
                if(next.x in -.30.. .18&&(!G.beamContact(next)||next.x<progress.turtle.x))progress=progress.copy(turtle=next)
                else{steer=0.0;hint("这边空间不足，换一个方向。")}
                steer-=amount}
            if(abs(reel)>.000001){val amount=min(abs(reel),.055*dt)*if(reel<0)-1 else 1
                val next=progress.turtle+V3(0.0,amount,0.0)
                if(amount>0&&(G.beamContact(next)||progress.air==0)){tension=min(1.0,tension+dt*.80)
                    if(tension>=.90){reel=0.0;mistake("自动刹车：先向左绕开木梁，或放缆降低张力。")}}
                else if(amount>0&&progress.air==3){tension=min(1.0,tension+dt*.8);if(tension>=.9){reel=0.0;mistake("浮力过大，先少放一点气。")}}
                else if(next.y in -.25.. .34){progress=progress.copy(turtle=next);reel-=amount;if(amount<0)tension=(tension-dt*.8).coerceAtLeast(0.0)}
                else reel=0.0
            }
            if(G.safe(progress.turtle)&&progress.attached&&progress.cuts==2&&progress.braced&&progress.air in 1..2)change(P.FAREWELL,"海龟到达安全水域，正在舒展鳍肢。")
        }
        if(progress.phase==P.FAREWELL){progress=progress.copy(farewell=progress.farewell+dt)
            if(progress.farewell>=7.5)change(P.RESULT,"救援完成。小海龟已经重返海流。")}
    }
    /** Says what to press next, by the name on the button. */
    private fun arrived():String{val name=selected?.let{targetName(it)}?:return "机械臂已到位。先选一个目标。"
        return when(progress.phase){
            P.INSPECT->"已对准$name。按操纵台上的橙色「执行」键照亮它。"
            P.BRACE->"已对准$name。按橙色「执行」键夹紧托座。"
            P.CUT->"已对准$name。按橙色「执行」键闭合剪钳。"
            P.BAG->"已对准$name。按橙色「执行」键挂好浮袋。"
            P.BEACON_SURVEY,P.BALANCE_SURVEY->"已对准$name。按橙色「执行」键照清此处。"
            P.BEACON_POWER->"已对准$name。按橙色「执行」键断电、换电芯。"
            P.BEACON_ALIGN->"已对准$name。把绿色「转轮」旋钮往上推或往下推，转动它。"
            P.BALANCE_ATTACH->"已对准$name。按橙色「执行」键挂好这侧浮袋。"
            else->"已对准$name。"
        }
    }
    private fun completeAction(id:String){
        // v1.2.1: a finished target is let go of, so the ring and the panel move on (it stayed on the cut rope).
        if(selected==id)selected=null
        when(progress.phase){
        P.INSPECT->{progress=progress.copy(inspected=progress.inspected+id);sound("inspect");if(progress.inspected.size==3)change(P.BRACE,"三处都已照清。点「黄色夹座」，把木梁夹稳。")else hint("${targetName(id)}已照清（${progress.inspected.size}/3）。还差："+listOf("front-rope","rear-rope","brace-point").filter{it !in progress.inspected}.joinToString("、"){targetName(it)}+"，点它们。")}
        P.BRACE->{progress=progress.copy(braced=true);sound("clamp");change(P.CUT,"木梁已经固定。先点「前鳍绳」，再点「尾部绳」。")}
        P.CUT->{progress=progress.copy(cuts=progress.cuts+1);sound("cut");if(progress.cuts==2)change(P.BAG,"绳子都松开了。点「龟背上的背带扣」，挂上浮袋。")else hint("前鳍绳已剪开。现在点「尾部绳」。")}
        P.BAG->{progress=progress.copy(attached=true);sound("clamp");hint("浮袋已挂好。把蓝色「充气」旋钮往上推两下。")}
        else->completeMissionAction(id)
    }}

    private fun completeMissionAction(id:String){val w=progress.work;when(progress.phase){
        P.BEACON_SURVEY,P.BALANCE_SURVEY->{val inspected=w.surveyed+id;progress=progress.copy(work=w.copy(surveyed=inspected));sound("inspect");if(inspected.size==3)change(if(progress.phase==P.BEACON_SURVEY)P.BEACON_POWER else P.BALANCE_ATTACH,if(progress.phase==P.BEACON_SURVEY)"检查完成。点「蓝色电源扣」断电。"else"检查完成。分别点左、右吊环，挂上浮袋。")else hint("${targetName(id)}已照清（${inspected.size}/3）。再点下一处。")}
        P.BEACON_POWER->{if(!w.isolated){progress=progress.copy(work=w.copy(isolated=true));hint("电源已断开。再点一次「蓝色电源扣」，换入备用电芯。");sound("clamp")}else{progress=progress.copy(work=w.copy(cell=true));change(P.BEACON_ALIGN,"备用电芯已装好。三轮联动，请把刻度调成 1、3、2。")}}
        P.BALANCE_ATTACH->{progress=progress.copy(work=w.copy(bags=w.bags+id));sound("clamp");if(progress.work.bags.size==2)change(P.BALANCE_LIFT,"两个浮袋都已挂好。配重侧多一档气，先调平再提升。")else hint("这侧浮袋已挂好。再点另一侧吊环。")}
        else->Unit
    }}
    fun dial(delta:Int){if(!active||progress.phase!=P.BEACON_ALIGN)return
        val id=selected?:return hint("先点一只导光轮，机械臂到位后再转「转轮」旋钮。")
        val i=listOf("lens-left","lens-mid","lens-right").indexOf(id)
        if(i<0||!contact(id)||acting)return hint("机械臂需要先对准并停稳。")
        val values=progress.work.dials.toMutableList();for(j in listOf(i,(i+1)%3))values[j]=(values[j]+delta.coerceIn(-1,1)+4)%4
        progress=progress.copy(work=progress.work.copy(dials=values));hint("这只轮与右邻一起转了一格。目标刻度：1、3、2。");sound("clamp")
    }
    private fun testBeacon(){if(progress.work.dials!=listOf(1,3,2))return mistake("光路还没有接通。每次转动会同时带动右邻；目标是 1、3、2。")
        if(!progress.work.cell||!progress.work.isolated)return
        change(P.BEACON_TEST,"光束正在穿过三只透镜，抵达远端灯标。")}
    fun bagAir(side:String,delta:Int){if(!active||progress.phase!=P.BALANCE_LIFT||side !in listOf("left","right"))return
        if(progress.work.cageHeight>.001||progress.work.cageOffset>.001){brake();return hint("先移回中间并降回托架，再调整气量，避免悬吊时倾翻。")}
        brake();val w=progress.work;progress=progress.copy(work=if(side=="left")w.copy(leftAir=(w.leftAir+delta.coerceIn(-1,1)).coerceIn(0,4))else w.copy(rightAir=(w.rightAir+delta.coerceIn(-1,1)).coerceIn(0,4)))
        hint(if(M.balanced(progress.work))"网笼已平衡，可以小段提升。"else "${if(w.heavyRight)"右"else"左"}侧配重较重，需要比另一侧多一档气。");sound("air")}
    fun liftCage(direction:Int){if(!active||progress.phase!=P.BALANCE_LIFT)return
        if(direction>0&&!M.balanced(progress.work))return mistake("自动保护：先把两袋总量调到 5–6 档，重侧多一档，笼子调平再升。")
        if(direction<0&&progress.work.cageOffset>.03)return hint("先把网笼移回吊升位置，再降低。")
        cageLift=direction.coerceIn(-1,1)*.07;cagePark=0.0;hint("网笼正在小段升降，可以随时停止。")}
    fun parkCage(direction:Int){if(!active||progress.phase!=P.BALANCE_LIFT)return
        if(progress.work.cageHeight<.23||!M.balanced(progress.work))return mistake("还没有离开卡住的底座。先调平并提升到 23 厘米以上。")
        cagePark=direction.coerceIn(-1,1)*.08;cageLift=0.0;hint("正在向停放架平移；到位后按橙色「执行」键锁住。")}
    fun lockCage(){if(!active||progress.phase!=P.BALANCE_LIFT)return
        val w=progress.work;if(w.cageHeight<.23||w.cageOffset<.32||!M.balanced(w)||abs(cagePark)>.00001)return hint("网笼需平稳停在右侧架上：抬高 23 厘米、平移 32 厘米，等它停稳。")
        progress=progress.copy(work=w.copy(locked=true));change(P.RESULT,"网笼已锁定在停放架，航道真正打开了。")}
    fun routeSelect(index:Int){if(!active||progress.phase!=P.ESCORT_PLAN||escorting||index !in progress.work.segment..2)return
        progress=progress.copy(work=progress.work.copy(routeSelected=index));hint("选中第 ${index+1} 个航标。选择路线不会立即放行海龟。");sound("select")}
    fun routeLane(lane:Int){if(!active||progress.phase!=P.ESCORT_PLAN||escorting||lane !in -1..1)return
        val w=progress.work;if(w.routeSelected<w.segment)return
        val a=w.lanes.toMutableList();a[w.routeSelected]=lane;progress=progress.copy(work=w.copy(lanes=a,routeLeg=if(w.routeSelected==w.segment)0 else w.routeLeg));hint("航标位置已设定；准备好后点“引导下一段”。");sound("select")}
    private fun escort(){if(!progress.campaign.completed.containsAll(listOf(0,1,2)))return
        if(progress.work.segment>=3||escorting)return
        M.obstruction(progress.work)?.let{return mistake("暂不能放行：$it 挡住了这条路线。调整航标后再试。")}
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
            val target=M.path(w)[w.routeLeg];val next=w.escort.moveTo(target,.055*dt);val obstacle=M.blocked(next)
            if(obstacle!=null){escorting=false;mistake("保护停航：$obstacle 挡住了路线。位置已保持，改线后重新放行。");return}
            progress=progress.copy(work=w.copy(escort=next))
            if((next-target).length()<.0001){if(w.routeLeg<2){progress=progress.copy(work=progress.work.copy(routeLeg=w.routeLeg+1));return};escorting=false;val count=w.segment+1;progress=progress.copy(work=progress.work.copy(segment=count,checkpoint=next,routeLeg=0,routeSelected=count.coerceAtMost(2)));sound("progress");if(count==3)change(P.OCEAN_FAREWELL,"海龟已越过最后的航标，正在回到海流。")else hint("第 $count 段已安全通过。检查下一段航标，再明确放行。")}
        }
        if(progress.phase==P.OCEAN_FAREWELL){progress=progress.copy(farewell=(progress.farewell+dt).coerceAtMost(8.0));if(progress.farewell>=8)change(P.RESULT,"全部救援完成。引航灯和通行航道会保留。")}
    }

    private fun change(p:P,m:String){stop();selected=null;progress=CampaignUseCase.record(progress.copy(phase=p));message=m;sound("progress")}
    private fun hint(m:String){message=m}
    private fun mistake(m:String){progress=progress.copy(errors=progress.errors+1);message=m;sound("warning")}
    private fun sound(s:String){effect=s;effectId++}
    companion object {
        /** 「执行」 with nothing picked works on a target of this step at most this far from the tool. */
        /** v1.2: within this of a target the arm is drawn onto it and works (was 10 cm). */
        const val NEAR=.16
        /** At the target: the tool within this (was 5.5 cm). */
        const val CONTACT=.085
        private val ONE_PRESS=setOf(P.INSPECT,P.BRACE,P.CUT,P.BAG,P.BEACON_SURVEY,P.BEACON_POWER,P.BALANCE_SURVEY,P.BALANCE_ATTACH)
    }
}
