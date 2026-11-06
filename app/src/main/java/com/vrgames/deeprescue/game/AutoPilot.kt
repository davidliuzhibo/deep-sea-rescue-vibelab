package com.vrgames.deeprescue.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.core.content.ContextCompat
import com.vrgames.deeprescue.ui.rescue.RescueEvent
import com.vrgames.deeprescue.ui.rescue.RescueViewModel
import com.vrgames.deeprescue.ui.rescue.components.PanelContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The test hook (debug builds only): adb shell am broadcast -a com.vrgames.deeprescue.TEST -p
 * com.vrgames.deeprescue --es cmd walk. It presses the same panel buttons the player has and runs the
 * real rules. It does not exercise pointing at targets, pinching or dragging the handle.
 */
object AutoPilot {
    const val ACTION = "com.vrgames.deeprescue.TEST"
    const val TAG = "RescueTest"
    var debug = false; private set
    var timeScale = 1.0
    var view: String? = null
    private var vm: RescueViewModel? = null
    private var contract: PanelContract? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    fun register(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        debug = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = command(i.getStringExtra("cmd") ?: "", i.getStringExtra("id"))
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "ready")
    }

    fun attach(model: RescueViewModel, panel: PanelContract) { vm = model; contract = panel }
    fun ready() = Log.i(TAG, "scene ready")

    private fun command(cmd: String, id: String?) {
        Log.i(TAG, "cmd $cmd ${id.orEmpty()}")
        val v = vm ?: return
        when (cmd) {
            "walk" -> { job?.cancel(); job = scope.launch { walk(v) } }
            "enter" -> { job?.cancel(); job = scope.launch { enter(v) } }
            "speed" -> timeScale = id?.toDoubleOrNull() ?: 1.0
            "view" -> view = id?.takeIf { it != "reset" }
            "tap" -> id?.let { tap(v, it) }
            "state" -> v.state.value.let { Log.i(TAG, "state page=${it.page} phase=${v.engine.progress.phase} message=${it.message}") }
        }
    }

    private fun tap(v: RescueViewModel, id: String) { v.onEvent(RescueEvent.Control("$id-action", contract!!)) }

    private suspend fun until(what: String, seconds: Int = 180, test: () -> Boolean): Boolean {
        var t = 0
        while (!test()) {
            delay(200)
            if (++t > seconds * 5) { Log.i(TAG, "timeout waiting for $what; page=${vm?.state?.value?.page} message=${vm?.engine?.message}"); return false }
        }
        return true
    }

    private suspend fun time(v: RescueViewModel, seconds: Double) {
        val from = v.engine.progress.elapsed
        until("$seconds game seconds") { v.engine.progress.elapsed >= from + seconds }
    }

    private suspend fun enter(v: RescueViewModel) {
        until("placing") { v.state.value.page == "ready" }
        Log.i(TAG, "entered page=${v.state.value.page}")
    }

    private suspend fun operate(v: RescueViewModel, target: String) {
        val page = v.state.value.page
        // One press does the step, as for the player.
        tap(v, "$page-$target"); delay(100)
        until("arm at $target", 120) { !v.engine.moving }
        until("work on $target", 120) { !v.engine.acting }
        Log.i(TAG, "step $page $target -> ${v.state.value.page}")
    }

    private fun result(v: RescueViewModel, n: Int) {
        val p = v.engine.progress
        Log.i(TAG, "result mission=$n page=${v.state.value.page} seconds=${p.elapsed.toInt()} stops=${p.errors} completed=${p.campaign.completed.size}")
    }

    private suspend fun walk(v: RescueViewModel) {
        enter(v)
        tap(v, "start"); delay(500)
        for (t in listOf("front-rope", "rear-rope", "brace-point")) operate(v, t)
        operate(v, "brace-point")
        operate(v, "front-rope"); operate(v, "rear-rope")
        operate(v, "float-hook")
        repeat(2) { tap(v, "bag-air-plus") }
        delay(300)
        // By what it sees, as a player would: the current keeps pushing it, and frames are slow here.
        var tries = 0
        while (v.state.value.page == "winch" && tries++ < 400) {
            val p = v.engine.progress.turtle
            when {
                p.x > -.24 -> tap(v, "steer-left")
                p.y < .15 -> tap(v, "winch-execute")
                else -> tap(v, "pay-out")
            }
            delay(600)
        }
        until("mission 0 end") { v.state.value.page == "result" }
        result(v, 0); delay(1500)
        tap(v, "chart-open"); delay(300); tap(v, "mission-1"); delay(500)
        for (t in listOf("power-port", "lens-left", "lens-mid")) operate(v, t)
        operate(v, "power-port"); operate(v, "power-port")
        tap(v, "beacon-align-lens-left"); tap(v, "beacon-align-aim"); until("left wheel") { !v.engine.moving }; tap(v, "dial-plus")
        tap(v, "beacon-align-lens-mid"); tap(v, "beacon-align-aim"); until("mid wheel") { !v.engine.moving }; tap(v, "dial-plus"); tap(v, "dial-plus")
        delay(1500); tap(v, "beacon-align-execute")
        until("mission 1 end") { v.state.value.page == "result" }
        result(v, 1); delay(1500)
        tap(v, "chart-open"); delay(300); tap(v, "mission-2"); delay(500)
        for (t in listOf("anchor-left", "anchor-right", "load-weight")) operate(v, t)
        operate(v, "anchor-left"); operate(v, "anchor-right")
        repeat(2) { tap(v, "bag-left1") }; repeat(3) { tap(v, "bag-right1") }
        repeat(4) { tap(v, "balance-lift-execute"); time(v, 1.9) }
        repeat(4) { tap(v, "cage-right"); time(v, 1.6) }
        tap(v, "cage-lock")
        until("mission 2 end") { v.state.value.page == "result" }
        result(v, 2); delay(1500)
        tap(v, "chart-open"); delay(300); tap(v, "mission-3"); delay(500)
        listOf(0, 2, 0).forEachIndexed { i, lane ->
            tap(v, "route-$i"); tap(v, "lane-$lane"); delay(800); tap(v, "escort-plan-execute")
            until("leg $i", 240) { v.engine.progress.work.segment > i || v.state.value.page != "escort-plan" }
            Log.i(TAG, "leg $i passed")
        }
        until("mission 3 end", 240) { v.state.value.page == "result" }
        result(v, 3)
        Log.i(TAG, "walk done completed=${v.engine.progress.campaign.completed.size}")
    }
}
