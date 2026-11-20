package com.vrgames.deeprescue.ui.rescue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vrgames.deeprescue.data.RescueRepository
import com.vrgames.deeprescue.data.RescueSaveQueue
import com.vrgames.deeprescue.domain.RescueEngine
import com.vrgames.deeprescue.domain.RescueCurrent
import com.vrgames.deeprescue.domain.RoomPlacement
import com.vrgames.deeprescue.domain.model.EyePose
import com.vrgames.deeprescue.domain.model.Mount
import com.vrgames.deeprescue.domain.model.RescueGeometry
import com.vrgames.deeprescue.domain.model.RoomPlane
import com.vrgames.deeprescue.domain.model.SurfaceKind
import com.vrgames.deeprescue.domain.model.V3
import com.vrgames.deeprescue.ui.rescue.components.PanelContract
import com.vrgames.deeprescue.domain.usecase.CampaignUseCase
import com.vrgames.deeprescue.domain.model.RescuePhase
import com.vrgames.deeprescue.domain.model.MissionGeometry
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import kotlin.math.roundToInt

class RescueViewModel(private val repository:RescueRepository, private val writer:RescueSaveQueue=RescueSaveQueue(repository)):ViewModel() {
    private val loaded=repository.load()
    val engine=RescueEngine(loaded.progress)
    private val mutable=MutableStateFlow(RescueUiState(progress=loaded.progress,message=loaded.warning?:"正在加载救援站。"))
    val state=mutable.asStateFlow()
    private val history=ArrayDeque<String>()
    private var pose:EyePose?=null
    private var planes:List<RoomPlane> = emptyList()
    private var now=0L
    private var tracking=false
    private var candidates:List<Mount> = emptyList()
    private var tables:List<Mount> = emptyList()
    private var uiClock=0.0
    private var saveClock=0.0
    private var lastPhase=engine.progress.phase
    private var autoFrom:Long?=null
    private fun change(block:(RescueUiState)->RescueUiState){mutable.update(block)}
    private fun publish(){change{visualState(it).copy(message=it.saveWarning?:engine.message)}}
    /** Full-rate scene snapshot, independent of the 20 Hz text/control refresh. */
    fun visualState(base:RescueUiState=state.value)=base.copy(progress=engine.progress,selected=engine.selected,tension=engine.tension,moving=engine.moving,acting=engine.acting,effectId=engine.effectId,effect=engine.effect)
    private fun active(){engine.setActive(!state.value.closing&&state.value.foreground&&state.value.ready&&state.value.trackingValid&&(state.value.page==engine.progress.phase.id||state.value.page=="adjust"))}
    fun resourcesReady(){change{it.copy(ready=true,message=loaded.warning?:"救援站已就绪。先寻找前方墙面，或选择虚拟练习。")};active()}
    fun resourcesFailed(){engine.stop();change{it.copy(ready=false,page="error",message="救援站模型暂未加载完成，请重试。进度已经保留。")};active()}
    fun foreground(value:Boolean){if(state.value.foreground==value)return
        if(!value){engine.stop();save();if(state.value.page==engine.progress.phase.id||state.value.page=="adjust")change{it.copy(page="pause")}}
        change{it.copy(foreground=value)};active()}
    fun room(eye:EyePose?,surfaces:List<RoomPlane>,running:Boolean,clock:Long){
        pose=eye;planes=surfaces;now=clock;tracking=running
        autoPlace()
        val s=state.value;val m=s.mount
        val valid=if(m==null)eye?.fresh(now)==true else (m.practice||running)&&RoomPlacement.valid(m,eye,surfaces,now)
        change{it.copy(trackingValid=valid)}
        if(m!=null&&!valid&&s.page in listOf(engine.progress.phase.id,"adjust")){
            // Any invalid tracking sample cancels ownership and requires explicit recovery.
            engine.setActive(false);save()
            change{it.copy(page="tracking",message="位置追踪暂不可用，机械臂已停住。请重新检查位置。")}
        }
        if(s.page=="wall-preview")refreshWalls(false)
        if(s.page=="table-select")refreshTables(false)
        active()
    }
    /** No choice and no scan: the window stands at the same comfortable place in front of the player. */
    private fun autoPlace(){val s=state.value
        if(s.page!="placement"||s.mount!=null||!s.ready||pose?.fresh(now)!=true){if(s.page!="placement")autoFrom=null;return}
        val start=autoFrom?:now.also{autoFrom=it}
        if(now-start>=AUTO_WAIT_MS)RoomPlacement.practice(pose,now)?.let{autoFrom=null;placeReady(it,"观察窗在你的正前方")}
    }
    fun advance(dt:Double){engine.tick(dt);uiClock+=dt;saveClock+=dt
        if(engine.progress.phase!=lastPhase){lastPhase=engine.progress.phase;change{it.copy(page=lastPhase.id)};history.clear();save();active()}
        if(saveClock>=2){saveClock=0.0;if(engine.active)save()}
        if(uiClock>=.05){uiClock=0.0;if(engine.active)publish()}}
    /** Pages that are part of a level, as opposed to the pages around it. */
    private fun inLevel()=state.value.page==engine.progress.phase.id
    /** The 返回 on every page: out of a level to the level list, anywhere else one step back. */
    private fun back(){
        if(inLevel()){navigate("chart");return}
        val target=history.removeLastOrNull()?:engine.progress.phase.id
        change{it.copy(page=target)};active();if(engine.active)publish()
    }
    private fun canBack()=state.value.page !in setOf("placement","wall-preview","table-select","ready","tracking","error","exited","exit-confirm","restart-confirm")
    private fun startLevel(id:Int){
        val p=engine.progress
        if(id==p.campaign.mission&&p.phase!=RescuePhase.RESULT){history.clear();change{it.copy(page=p.phase.id)};active();publish();return}
        if(engine.startMission(id)){lastPhase=engine.progress.phase;history.clear();change{it.copy(page=lastPhase.id)};save();active();publish()}else message("这一关还没开放。")
    }
    /** Debug builds only (AutoPilot): level [id] from its start, wherever the save is. */
    fun debugStart(id:Int){if(engine.startMission(id)){lastPhase=engine.progress.phase;history.clear();change{it.copy(page=lastPhase.id)};save();active();publish()}}
    fun control(id:String,c:PanelContract){
        if(!state.value.foreground||state.value.closing)return
        if(id==BACK){back();return}
        if(id !in c.actionsOn(state.value.page))return
        val payload=c.action(id)?:return
        val op=payload.string("op")?:return
        when(op){
            "scan"->{if(!state.value.ready)return;refreshWalls(true)}
            "next-wall"->cycleWall()
            "practice"->{if(!state.value.ready)return;val m=RoomPlacement.practice(pose,now)?:return message("请保持视线平稳，等待头部追踪就绪。");placeReady(m,"虚拟练习 · 不使用真实墙面或桌面")}
            "confirm-wall"->{val m=state.value.preview?:return;val latest=RoomPlacement.walls(pose,planes,now).firstOrNull{it.planeId==m.planeId}?:return message("这面墙已不可用，请重新寻找。");change{it.copy(mount=latest,preview=null,page="table-select")};refreshTables(true)}
            "next-table"->{if(tables.isNotEmpty()){val index=tables.indexOfFirst{it.tableId==state.value.preview?.tableId};change{it.copy(preview=tables[(index+1)%tables.size])}}}
            "confirm-table"->{val m=state.value.preview?:return;if(!RoomPlacement.valid(m,pose,planes,now))return message("桌面追踪已变化，请重新选择。");placeReady(m,"真实墙面观察窗 · 真实桌面控制台")}
            "virtual-console"->{val m=state.value.mount?:return;placeReady(m.copy(console=null,tableId=null),"真实墙面观察窗 · 虚拟控制台")}
            "start"->{if(!state.value.ready||!state.value.trackingValid)return message("位置或模型尚未就绪，请稍候。");history.clear();change{it.copy(page=engine.progress.phase.id)};active();publish()}
            "navigate"->navigate(payload.string("target")?:return)
            "back"->{val target=history.removeLastOrNull()?:"pause";change{it.copy(page=target)};active();if(engine.active)publish()}
            "resume"->{if(!state.value.trackingValid)return navigate("tracking");change{it.copy(page=engine.progress.phase.id)};history.clear();active();publish()}
            "revalidate"->{val m=state.value.mount;if(m!=null&&(m.practice||tracking)&&RoomPlacement.valid(m,pose,planes,now)){change{it.copy(page="pause",trackingValid=true,message="位置已确认，请点“继续救援”。")};active()}else message("当前观察窗的位置仍不可用，请重新定位。")}
            "retry"->{engine.stop();change{it.copy(page="placement",ready=false,generation=it.generation+1,mount=null,preview=null)}}
            "exit"->{engine.stop();change{it.copy(closing=true,message="正在保存救援进度……")};active()
                writer.save(engine.progress).whenComplete{_,error->
                    if(error==null)change{it.copy(page="exited",closing=false)}
                    else change{it.copy(closing=false,message="存档暂时写入失败，请重试退出；当前进度仍在内存中。")}
                }}
            "chart"->{navigate("chart")}
            "mission"->startLevel(payload.get("mission").asInt)
            "mission-next"->CampaignUseCase.next(engine.progress.campaign)?.let{startLevel(it)}
            "dial"->{engine.dial(payload.get("delta").asInt);publish()}
            "bag-air"->{engine.bagAir(payload.string("side")?:return,payload.get("delta").asInt);publish()}
            "cage-lift"->{engine.liftCage(payload.get("direction").asInt);publish()}
            "cage-park"->{engine.parkCage(payload.get("direction").asInt);publish()}
            "cage-lock"->{engine.lockCage();syncPhase();publish()}
            "route-select"->{engine.routeSelect(payload.get("index").asInt);publish()}
            "route-lane"->{engine.routeLane(payload.get("lane").asInt);publish()}
            "skip-farewell"->{engine.skipFarewell();syncPhase();publish()}
            "restart"->{engine.restart();lastPhase=engine.progress.phase;history.clear();change{it.copy(page="ready")};save();active();publish()}
            "toggle-music"->{engine.settings(!engine.progress.settings.music,engine.progress.settings.sfx);save();publish()}
            "toggle-sfx"->{engine.settings(engine.progress.settings.music,!engine.progress.settings.sfx);save();publish()}
            "select"->{engine.go(payload.string("targetId")?:return);publish()}
            "approach"->{engine.approach();publish()}
            "execute"->{engine.execute();publish()}
            "air"->{engine.air(payload.get("delta").asInt);syncPhase();publish()}
            "steer"->{engine.steer(payload.get("delta").asInt);publish()}
            "winch"->{engine.reel(payload.get("direction").asInt);publish()}
            "brake"->{engine.brake();publish()}
            "nudge"->{val d=payload.get("delta").asDouble;engine.nudge(when(payload.string("axis")){"x"->V3(d,0.0,0.0);"y"->V3(0.0,d,0.0);else->V3(0.0,0.0,d)});publish()}
        }
    }
    private fun syncPhase(){if(engine.progress.phase!=lastPhase){lastPhase=engine.progress.phase;change{it.copy(page=lastPhase.id)};save();active()}}
    private fun navigate(page:String){engine.stop();history.addLast(state.value.page);if(page=="placement"){change{it.copy(page=page,mount=null,preview=null)};candidates=emptyList()}else change{it.copy(page=page)};save();active()}
    private fun message(m:String){change{it.copy(message=m)}}
    private fun refreshWalls(show:Boolean){candidates=if(tracking)RoomPlacement.walls(pose,planes,now)else emptyList();val id=state.value.preview?.planeId
        val next=candidates.firstOrNull{it.planeId==id}?:if(show)candidates.firstOrNull()else null
        change{it.copy(page=if(show&&next!=null)"wall-preview"else it.page,preview=next,surface=if(next!=null)"已找到前方墙面，窗中心接近平视。请确认发光窗框的位置。"else"暂时没有足够宽且朝向合适的墙面。可以换个方向，或选择虚拟练习。")}}
    private fun cycleWall(){refreshWalls(false);if(candidates.isEmpty())return;val index=candidates.indexOfFirst{it.planeId==state.value.preview?.planeId};change{it.copy(preview=candidates[(index+1)%candidates.size])}}
    private fun refreshTables(show:Boolean){val m=state.value.mount?:return
        val floor=planes.filter{it.kind==SurfaceKind.UP&&it.valid()}.flatMap{it.triangles}.flatMap{listOf(it.a,it.b,it.c)}.minOfOrNull{it.y}
        tables=RoomPlacement.tables(m,planes,floor);val current=state.value.preview?.tableId
        val next=tables.firstOrNull{it.tableId==current}?:if(show)tables.firstOrNull()else null
        change{it.copy(preview=next,surface=if(next==null)"没有确认可用桌面。可选择虚拟控制台，救援内容相同。"else"桌面已找到，请确认控制台在桌面上方且没有挡住身体。")}}
    private fun placeReady(m:Mount,label:String){change{it.copy(mount=m,preview=null,page="ready",surface=label,trackingValid=true)};history.clear();active()}
    fun onEvent(event:RescueEvent):Boolean {
        when(event){
            is RescueEvent.Control->{control(event.id,event.contract);return true}
            is RescueEvent.DragEnd->{engine.dragEnd(event.pointer,event.cancelled);return true}
            RescueEvent.CancelInput->{engine.stop();return true}
            else->Unit
        }
        if(!engine.active||state.value.closing)return false
        return when(event){
            is RescueEvent.SelectTarget->{engine.go(event.id);publish();true}
            is RescueEvent.DragStart->engine.dragBegin(event.pointer)
            is RescueEvent.DragMove->{engine.dragMove(event.pointer,event.total);true}
            is RescueEvent.Physical->{physical(event.name,event.delta);true}
            else->false
        }
    }
    /** What a console control does in the current step. */
    private fun physical(name:String,d:Int){
        val lift=engine.progress.phase==com.vrgames.deeprescue.domain.model.RescuePhase.BALANCE_LIFT
        when(name){
            "go"->if(lift)engine.lockCage() else engine.execute()
            "stop"->engine.brake()
            "dial"->engine.dial(d)
            "air"->if(lift)engine.bagAir("left",d) else engine.air(d)
            "air2"->engine.bagAir("right",d)
            "winch"->if(lift)engine.liftCage(d) else engine.reel(d)
            "steer"->if(lift)engine.parkCage(d) else engine.steer(d)
        }
        syncPhase();publish()
    }
    fun pendingSave()=writer.pending()
    private fun save(){writer.save(engine.progress).whenComplete{_,error->
        val warning=if(error==null)null else "存档暂时写入失败，请保留游戏并重试；当前进度仍在内存中。"
        change{it.copy(saveWarning=warning,message=warning?:it.message)}
    }}
    fun binding(path:String):Any?=when(path){
        "ui.feedback"->state.value.message
        "ui.onePress"->true
        "ui.canBack"->canBack()
        "ui.startLabel"->engine.progress.let{if(it.phase==RescuePhase.RESULT)"打开关卡列表" else "开始第 ${it.campaign.mission+1} 关"}
        "ui.resultTitle"->engine.progress.campaign.let{c->if(c.completed.size>=4)"4 关全部完成！" else "第 ${c.mission+1} 关完成！一共 4 关，还剩 ${4-c.completed.size} 关"}
        "ui.resultNextLabel"->CampaignUseCase.next(engine.progress.campaign)?.let{"下一关：第 ${it+1} 关 · ${CampaignUseCase.titles[it]}"}?:"全部完成"
        "ui.resultNextHidden"->CampaignUseCase.next(engine.progress.campaign)==null
        "ui.chartContinueHidden"->engine.progress.phase==RescuePhase.RESULT
        // v1.2.1: with nothing picked it names the next target (was 「尚未选中目标」).
        "ui.selectionLabel"->com.vrgames.deeprescue.domain.StepFocus.nextLabel(engine.progress,engine.selected)
        "ui.surfaceLabel"->state.value.surface
        "ui.gauges"->"浮力：${engine.progress.air} / 3 · 张力：${(engine.tension*100).roundToInt()}% · 水流：${if(RescueCurrent.strength(engine.progress.elapsed)>.8)"增强"else"平缓"}"
        "ui.audioLabel"->"氛围声：${if(engine.progress.settings.music)"开"else"关"} · 操作音：${if(engine.progress.settings.sfx)"开"else"关"}"
        "ui.missionOpen.0","ui.missionOpen.1","ui.missionOpen.2","ui.missionOpen.3"->CampaignUseCase.unlocked(engine.progress.campaign,path.last().digitToInt())
        "ui.missionLabel.0","ui.missionLabel.1","ui.missionLabel.2","ui.missionLabel.3"->path.last().digitToInt().let{i->val c=engine.progress.campaign;"第 ${i+1} 关 · ${CampaignUseCase.titles[i]}"+when{i==c.mission&&engine.progress.phase!=RescuePhase.RESULT->"（进行中）";i in c.completed->" ✓ 已完成";CampaignUseCase.unlocked(c,i)->"（可以开始）";i==3->"（先完成第 2、3 关）";else->"（先完成第 1 关）"}}
        "ui.campaign"->CampaignUseCase.titles.mapIndexed{i,t->t+"："+when{ i in engine.progress.campaign.completed->"已完成 · 最好 ${engine.progress.campaign.bestSeconds[i]?.toInt()?:0} 秒";CampaignUseCase.unlocked(engine.progress.campaign,i)->"可开始";else->"完成前置任务后开放"}}.joinToString("\n")
        "ui.missionGauges"->missionGauges()
        "ui.missionHint"->missionHint()
        "ui.result"->"${CampaignUseCase.titles[engine.progress.campaign.mission]}完成。${if(engine.progress.campaign.completed.size==4)"四项成果已齐，归途已经开放。"else"还可以从任务航图继续本次值守。"}用时 ${(engine.progress.elapsed/60).toInt()} 分 ${(engine.progress.elapsed%60).toInt()} 秒；保护性制动 ${engine.progress.errors} 次。"
        else->if(path.startsWith("ui.level|"))"第 ${engine.progress.campaign.mission+1}/4 关 · "+path.substringAfter('|') else null
    }
    private fun missionGauges():String {val w=engine.progress.work;return when(engine.progress.campaign.mission){
        1->"电源：${if(w.isolated)"已隔离"else"未隔离"} · 电芯：${if(w.cell)"已换好"else"待更换"}\n刻度：${w.dials.joinToString(" / ")} · 目标：1 / 3 / 2\n传光：${(w.beamTime/3*100).toInt()}%"
        2->"${if(w.heavyRight)"右"else"左"}侧配重 · 左袋 ${w.leftAir} / 右袋 ${w.rightAir}\n倾斜 ${MissionGeometry.roll(w).toInt()}° · 抬高 ${(w.cageHeight*100).roundToInt()} cm · 停放 ${(w.cageOffset*100).roundToInt()} cm"
        3->"已通过 ${w.segment} / 3 段 · 已选第 ${w.routeSelected+1} 个航标\n路线：${w.lanes.joinToString(" → "){when(it){-1->"左";1->"右";else->"中"}}}"
        else->""}}
    private fun missionHint()=when(engine.progress.campaign.mission){
        0->"前鳍绳、尾部绳、黄色夹座都检查后，先稳梁再剪前后两绳。浮力两档，先向左绕梁，随后收缆；张力高就放缆。"
        1->"初始刻度都是0。左轮转一格，中轮转两格，可以得到1、3、2。转错可逆向退一格；每次轮与它右邻一起转。"
        2->"看深色配重在哪侧。轻侧2档、重侧3档；提升4小段，然后向右4小段，等停稳再锁定。换气会立即停止移动。"
        else->"三段推荐左、右、左。先选对应航标，再选路线，再放行；遇阻会停止并保留位置，可修改后继续。"}
    companion object { private const val AUTO_WAIT_MS=300L;const val BACK="__back" }
    override fun onCleared(){engine.stop();save();writer.close();super.onCleared()}
}
private fun JsonObject.string(k:String)=get(k)?.takeIf{it.isJsonPrimitive}?.asString
class RescueViewModelFactory(private val dir:File):ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T {require(modelClass.isAssignableFrom(RescueViewModel::class.java));return RescueViewModel(RescueRepository(dir)) as T}
}
