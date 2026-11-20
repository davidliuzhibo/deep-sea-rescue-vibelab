package com.vrgames.deeprescue.ui.rescue

import com.vrgames.deeprescue.data.RescueRepository
import com.vrgames.deeprescue.data.RescueSaveQueue
import com.vrgames.deeprescue.domain.model.EyePose
import com.vrgames.deeprescue.domain.model.RescuePhase
import com.vrgames.deeprescue.domain.model.RescueProgress
import com.vrgames.deeprescue.domain.model.V3
import com.vrgames.deeprescue.domain.usecase.CampaignUseCase
import com.vrgames.deeprescue.ui.rescue.components.PanelContract
import java.io.File
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FullCampaignUiTest {
    @get:Rule val tmp=TemporaryFolder()
    private val contract get()=PanelContract(File("src/main/assets/design/design-spec.json").readText())
    private fun vm():RescueViewModel {
        // Explicit completed slice fixture; the original suite independently covers its full real rule path.
        val p=CampaignUseCase.record(RescueProgress(phase=RescuePhase.RESULT,inspected=setOf("front-rope","rear-rope","brace-point"),braced=true,cuts=2,attached=true,air=2,turtle=V3(-.28,.15,-.68),elapsed=120.0,farewell=7.5))
        val r=RescueRepository(tmp.root);r.save(p)
        return RescueViewModel(r,RescueSaveQueue(r,Executor{it.run()})).also{v->v.resourcesReady();v.foreground(true);v.room(EyePose(V3(0.0,1.6,0.0),V3(0.0,0.0,-1.0),100),emptyList(),false,100);v.control("practice-action",contract);v.control("start-action",contract)}
    }
    private fun time(v:RescueViewModel,t:Double){repeat((120*t).toInt()){v.advance(1.0/120)}}
    private fun tap(v:RescueViewModel,id:String){v.onEvent(RescueEvent.Control(id+"-action",contract))}
    private fun operate(v:RescueViewModel,t:String){val p=v.state.value.page;tap(v,"$p-$t");tap(v,"$p-aim");time(v,6.0);tap(v,"$p-execute");time(v,1.3)}
    @Test fun allNewMissionsThroughPackagedControlsThenExit(){
        val v=vm();tap(v,"chart-open");tap(v,"mission-3");assertEquals("chart",v.state.value.page);tap(v,"mission-1")
        for(t in listOf("power-port","lens-left","lens-mid"))operate(v,t)
        operate(v,"power-port");operate(v,"power-port")
        tap(v,"beacon-align-lens-left");tap(v,"beacon-align-aim");time(v,6.0);tap(v,"dial-plus")
        tap(v,"beacon-align-lens-mid");tap(v,"beacon-align-aim");time(v,6.0);tap(v,"dial-plus");tap(v,"dial-plus");tap(v,"beacon-align-execute");time(v,3.1)
        assertEquals(v.state.value.message,"result",v.state.value.page);tap(v,"chart-open");tap(v,"mission-2")
        for(t in listOf("anchor-left","anchor-right","load-weight"))operate(v,t)
        operate(v,"anchor-left");operate(v,"anchor-right")
        repeat(2){tap(v,"bag-left1")};repeat(3){tap(v,"bag-right1")}
        repeat(4){tap(v,"balance-lift-execute");time(v,1.9)};repeat(4){tap(v,"cage-right");time(v,1.6)};tap(v,"cage-lock")
        assertEquals(v.state.value.message,"result",v.state.value.page);tap(v,"chart-open");tap(v,"mission-3")
        for((i,lane) in listOf(0,2,0).withIndex()){tap(v,"route-$i");tap(v,"lane-$lane");tap(v,"escort-plan-execute");time(v,30.0)}
        time(v,8.1);assertEquals(v.state.value.message,"result",v.state.value.page);assertEquals(setOf(0,1,2,3),v.engine.progress.campaign.completed)
        tap(v,"result-exit");tap(v,"confirm-exit");assertEquals("exited",v.state.value.page);assertEquals(v.engine.progress,RescueRepository(tmp.root).load().progress)
    }
    @Test fun missionSelectionCannotActInBackground(){val v=vm();tap(v,"chart-open");v.foreground(false);tap(v,"mission-1");assertEquals(0,v.engine.progress.campaign.mission)}
    @Test fun newMissionPauseHelpAndRestartKeepCompletedRecord(){val v=vm();tap(v,"chart-open");tap(v,"mission-1");tap(v,"beacon-survey-pause");tap(v,"pause-help");assertTrue(v.binding("ui.missionHint").toString().contains("1、3、2"));tap(v,"help-back");tap(v,"pause-restart");assertEquals("restart-confirm",v.state.value.page);tap(v,"restart-cancel");assertEquals("pause",v.state.value.page);tap(v,"pause-restart");tap(v,"restart-current");assertEquals("ready",v.state.value.page);assertEquals(setOf(0),v.engine.progress.campaign.completed)}
    @Test fun oneTargetPressDoesTheStep(){val v=vm();tap(v,"chart-open");tap(v,"mission-1");v.onEvent(RescueEvent.SelectTarget("power-port"));time(v,8.0);assertEquals(setOf("power-port"),v.engine.progress.work.surveyed);/* v1.2.1: a done target is let go of */assertNull(v.engine.selected);assertTrue(v.binding("ui.selectionLabel").toString().startsWith("下一步："));v.foreground(false);v.foreground(true);assertFalse(v.engine.active);assertEquals("pause",v.state.value.page)}
}
