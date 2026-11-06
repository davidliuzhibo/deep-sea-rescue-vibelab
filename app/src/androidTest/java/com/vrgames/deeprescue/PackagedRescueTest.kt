package com.vrgames.deeprescue

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vrgames.deeprescue.data.RescueRepository
import com.vrgames.deeprescue.data.RescueSaveQueue
import com.vrgames.deeprescue.domain.model.EyePose
import com.vrgames.deeprescue.domain.model.V3
import com.vrgames.deeprescue.ui.rescue.RescueEvent
import com.vrgames.deeprescue.ui.rescue.RescueViewModel
import com.vrgames.deeprescue.ui.rescue.components.PanelContract
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Future Android-runtime checks. No Activity/optical-input/visual acceptance is implied. */
@RunWith(AndroidJUnit4::class)
class PackagedRescueTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    @Test fun installedResourcesMatchCandidate(){
        val expected=instrumentation.context.assets.open("expected-assets.json").bufferedReader().use{JsonParser.parseReader(it).asJsonObject}
        for((name,hash) in expected.entrySet()){
            val bytes=instrumentation.targetContext.assets.open(name).use{it.readBytes()}
            val actual=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
            assertEquals(name,hash.asString,actual)
        }
    }
    @Test fun audioCanBeOpenedWithoutDecompression(){
        for(name in listOf("marine-ambience","select","inspect","clamp","cut","air","warning","progress"))
            instrumentation.targetContext.assets.openFd("audio/$name.wav").use{assertTrue(it.length>44)}
    }
    @Test fun productionButtonSequenceCompletesAndRestores(){
        // Test owns only this fresh directory, never the player's production save.
        val dir=File(instrumentation.targetContext.cacheDir,"rescue-instrumented-${System.nanoTime()}")
        val repo=RescueRepository(dir);val writer=RescueSaveQueue(repo,Executor{it.run()})
        val vm=RescueViewModel(repo,writer)
        val c=instrumentation.targetContext.assets.open("design/design-spec.json").bufferedReader().use{PanelContract(it.readText())}
        fun click(id:String){vm.onEvent(RescueEvent.Control(id,c))}
        fun time(seconds:Double){repeat((seconds*120).toInt()){vm.advance(1.0/120)}}
        fun operate(target:String){val phase=vm.state.value.page;click("$phase-$target-action");click("$phase-aim-action");time(7.0);click("$phase-execute-action");time(1.2)}
        try{
            vm.resourcesReady();vm.foreground(true)
            // Explicit synthetic room-pose fixture: this does NOT test a real room or live HMD freshness.
            vm.room(EyePose(V3(0.0,1.6,0.0),V3(0.0,0.0,-1.0),100),emptyList(),false,100)
            click("practice-action");click("start-action")
            for(id in listOf("front-rope","rear-rope","brace-point"))operate(id)
            assertEquals("brace",vm.state.value.page);operate("brace-point")
            operate("rear-rope");assertEquals(0,vm.engine.progress.cuts)
            operate("front-rope");operate("rear-rope");operate("float-hook")
            click("bag-air-plus-action");click("bag-air-plus-action");assertEquals("winch",vm.state.value.page)
            repeat(3){click("steer-left-action");time(1.1)}
            repeat(6){click("winch-execute-action");time(1.6)}
            assertEquals("farewell",vm.state.value.page);time(8.0);assertEquals("result",vm.state.value.page)
            assertEquals(vm.engine.progress.phase,repo.load().progress.phase)
        }finally{writer.close();dir.listFiles()?.forEach{it.delete()};dir.delete()}
    }
}
