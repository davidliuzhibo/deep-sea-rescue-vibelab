package com.vrgames.deeprescue.ui.rescue

import com.vrgames.deeprescue.data.RescueRepository
import com.vrgames.deeprescue.data.RescueSaveQueue
import java.util.concurrent.Executor
import com.vrgames.deeprescue.domain.model.EyePose
import com.vrgames.deeprescue.domain.model.V3
import com.vrgames.deeprescue.ui.rescue.components.PanelContract
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RescueUiPolicyTest {
    @get:Rule val folder=TemporaryFolder()
    private fun contract()=PanelContract(File("src/main/assets/design/design-spec.json").readText())
    private fun model():RescueViewModel=RescueRepository(folder.root).let{RescueViewModel(it,RescueSaveQueue(it,Executor { task->task.run() }))}.also{it.resourcesReady();it.foreground(true);it.room(EyePose(V3(0.0,1.6,0.0),V3(0.0,0.0,-1.0),100),emptyList(),false,100)}
    @Test fun allDeclaredStatesHaveRenderableRoot(){val c=contract();c.document.getAsJsonArray("states").forEach{val id=it.asJsonObject.get("id").asString;assertNotNull(c.root(id))}}
    @Test fun allRenderedButtonsHaveActionPayload(){val c=contract();c.document.getAsJsonArray("states").forEach{for(id in c.actionsOn(it.asJsonObject.get("id").asString))assertNotNull(id,c.action(id))}}
    @Test fun backgroundCannotStartPractice(){val vm=model();vm.foreground(false);vm.control("practice-action",contract());assertEquals("placement",vm.state.value.page);assertNull(vm.state.value.mount)}
    @Test fun hiddenStartCannotBypassPlacement(){val vm=model();vm.control("start-action",contract());assertEquals("placement",vm.state.value.page);assertNull(vm.state.value.mount)}
    @Test fun noSurfaceIsNotPretendedToBeReal(){val vm=model();vm.control("scan-wall-action",contract());assertNull(vm.state.value.mount);assertNull(vm.state.value.preview);assertTrue(vm.state.value.surface.contains("没有"))}
    @Test fun explicitPracticeUsesFreshPose(){val vm=model();vm.control("practice-action",contract());assertEquals("ready",vm.state.value.page);assertTrue(vm.state.value.mount!!.practice)}
    @Test fun readyGateProtectsStart(){val vm=model();vm.control("practice-action",contract());vm.resourcesFailed();vm.control("start-action",contract());assertEquals("error",vm.state.value.page);assertFalse(vm.engine.active)}
    @Test fun backgroundReturnNeedsContinue(){val vm=model();val c=contract();vm.control("practice-action",c);vm.control("start-action",c);assertTrue(vm.engine.active);vm.foreground(false);vm.foreground(true);assertEquals("pause",vm.state.value.page);assertFalse(vm.engine.active);vm.control("continue-action",c);assertTrue(vm.engine.active)}
    @Test fun placementLossStopsSimulation(){val vm=model();val c=contract();vm.control("practice-action",c);vm.control("start-action",c);vm.room(null,emptyList(),false,500);assertFalse(vm.engine.active);vm.room(null,emptyList(),false,900);assertEquals("tracking",vm.state.value.page)}
    @Test fun trackingReturnDoesNotAutomaticallyContinue(){val vm=model();val c=contract();vm.control("practice-action",c);vm.control("start-action",c);vm.room(null,emptyList(),false,500);vm.room(null,emptyList(),false,900);vm.room(EyePose(V3(0.0,1.6,0.0),V3(0.0,0.0,-1.0),1000),emptyList(),false,1000);assertFalse(vm.engine.active);assertEquals("tracking",vm.state.value.page)}
    @Test fun audioControlsAreIndependentAndPersisted(){val vm=model();val c=contract();vm.control("placement-settings-action",c);vm.control("toggle-music-action",c);assertFalse(vm.engine.progress.settings.music);assertTrue(vm.engine.progress.settings.sfx);assertFalse(RescueRepository(folder.root).load().progress.settings.music)}
    @Test fun exitRequiresVisibleConfirmation(){val vm=model();val c=contract();vm.control("confirm-exit-action",c);assertEquals("placement",vm.state.value.page);vm.control("placement-exit-action",c);assertEquals("exit-confirm",vm.state.value.page);vm.control("cancel-exit-action",c);assertEquals("placement",vm.state.value.page)}
    @Test fun compactTargetsRetainThreeColumns(){val c=contract();val nodes=c.document.getAsJsonArray("nodes").map{it.asJsonObject};val n=nodes.first{it.get("id").asString=="inspect-targets"};assertEquals(3,c.layout(n,640f)!!.get("columns").asInt)}
    @Test fun evenBriefTrackingLossNeedsExplicitRecovery(){
        val vm=model();val c=contract();vm.control("practice-action",c);vm.control("start-action",c)
        assertTrue(vm.onEvent(RescueEvent.DragStart(1)))
        vm.room(null,emptyList(),false,300)
        vm.room(EyePose(V3(0.0,1.6,0.0),V3(0.0,0.0,-1.0),350),emptyList(),false,350)
        assertEquals("tracking",vm.state.value.page);assertFalse(vm.engine.active)
        assertFalse(vm.onEvent(RescueEvent.DragStart(2)))
    }
    @Test fun sceneCanReadMotionBetweenTextUpdates(){
        val vm=model();val c=contract();vm.control("practice-action",c);vm.control("start-action",c)
        vm.onEvent(RescueEvent.SelectTarget("front-rope"));vm.engine.approach()
        val old=vm.state.value.progress.tool;vm.advance(1.0/120)
        assertEquals(old,vm.state.value.progress.tool);assertNotEquals(old,vm.visualState().progress.tool)
    }
    @Test fun confirmExitWaitsForDiskCheckpoint(){
        val jobs=ArrayDeque<Runnable>();val repo=RescueRepository(folder.root)
        val vm=RescueViewModel(repo,RescueSaveQueue(repo,Executor{jobs.addLast(it)}));val c=contract()
        vm.foreground(true);vm.resourcesReady();vm.control("placement-exit-action",c)
        vm.control("confirm-exit-action",c)
        assertEquals("exit-confirm",vm.state.value.page);assertTrue(vm.state.value.closing)
        vm.control("cancel-exit-action",c);assertEquals("exit-confirm",vm.state.value.page)
        while(jobs.isNotEmpty())jobs.removeFirst().run()
        assertEquals("exited",vm.state.value.page);assertFalse(vm.state.value.closing)
        assertTrue(File(folder.root,"deep-sea-rescue-v2.json").exists())
    }
    @Test fun cancelledDragDoesNotResumeFromLateMove(){
        val vm=model();val c=contract();vm.control("practice-action",c);vm.control("start-action",c)
        assertTrue(vm.onEvent(RescueEvent.DragStart(7)));vm.onEvent(RescueEvent.DragMove(7,V3(-.04,0.0,0.0)));vm.advance(.05)
        vm.onEvent(RescueEvent.CancelInput);val point=vm.engine.progress.tool
        vm.onEvent(RescueEvent.DragMove(7,V3(-.2,0.0,0.0)));vm.advance(.1)
        assertEquals(point,vm.engine.progress.tool)
    }
    @Test fun completeRouteUsesPublishedControlsAndSavesFinalPhase(){
        val vm=model();val c=contract()
        fun click(id:String){vm.onEvent(RescueEvent.Control(id,c))}
        fun time(seconds:Double){repeat((seconds*120).toInt()){vm.advance(1.0/120)}}
        fun operate(target:String){val phase=vm.state.value.page;click("$phase-$target-action");click("$phase-aim-action");time(7.0);click("$phase-execute-action");time(1.2)}
        click("practice-action");click("start-action")
        for(id in listOf("front-rope","rear-rope","brace-point"))operate(id)
        assertEquals("brace",vm.state.value.page);operate("brace-point")
        operate("rear-rope");assertEquals(0,vm.engine.progress.cuts)
        operate("front-rope");operate("rear-rope");operate("float-hook")
        click("bag-air-plus-action");click("bag-air-plus-action");assertEquals("winch",vm.state.value.page)
        repeat(3){click("steer-left-action");time(1.1)}
        repeat(6){click("winch-execute-action");time(1.6)}
        assertEquals("farewell",vm.state.value.page);time(8.0);assertEquals("result",vm.state.value.page)
        assertEquals(vm.engine.progress.phase,RescueRepository(folder.root).load().progress.phase)
    }
}
