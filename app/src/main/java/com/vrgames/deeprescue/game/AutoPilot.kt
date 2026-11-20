package com.vrgames.deeprescue.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.core.content.ContextCompat
import com.vrgames.deeprescue.domain.StepFocus
import com.vrgames.deeprescue.domain.model.V3
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
    private const val DRAG = 77L
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
            // v1.2.1 checks (no hands needed): what the ring is on, letting the stick go near a target, the whole rope check.
            "focus" -> logFocus(v, "now")
            "dragnear" -> { job?.cancel(); job = scope.launch { dragNear(v, id ?: "rear-rope", .10) } }
            "ropecheck" -> { job?.cancel(); job = scope.launch { ropeCheck(v) } }
        }
    }

    private fun logFocus(v: RescueViewModel, at: String) =
        Log.i(TAG, "focus $at ${StepFocus.describe(v.engine.progress, v.engine.selected)} label=${StepFocus.nextLabel(v.engine.progress, v.engine.selected)}")

    /**
     * Drags the arm with the joystick (the same drag events the hand sends) until the tool is [gap] metres from target
     * [id], then lets go. Logs whether anything was worked and whether a mistake was counted.
     */
    private suspend fun dragNear(v: RescueViewModel, id: String, gap: Double) {
        val e = v.engine
        val target = e.targetPosition(id) ?: run { Log.i(TAG, "dragnear unknown target $id"); return }
        val before = e.progress
        if (!v.onEvent(RescueEvent.DragStart(DRAG))) { Log.i(TAG, "dragnear refused phase=${before.phase.id}"); return }
        var total = V3(); var guard = 0
        while ((e.progress.tool - target).length() > gap && guard++ < 200) {
            val left = target - e.progress.tool
            total = total + left * (.015 / maxOf(left.length(), 1e-9))
            v.onEvent(RescueEvent.DragMove(DRAG, total))
            delay(60)
            until("arm follows", 10) { !e.moving }
        }
        val at = (e.progress.tool - target).length()
        v.onEvent(RescueEvent.DragEnd(DRAG, false))
        delay(200)
        val worked = e.acting
        until("work after release", 30) { !e.acting }
        val p = e.progress
        Log.i(TAG, "dragnear target=$id gap=${"%.3f".format(at)} executed=${worked || p.cuts != before.cuts || p.inspected != before.inspected || p.braced != before.braced || p.attached != before.attached || p.work != before.work} " +
            "cuts=${before.cuts}->${p.cuts} inspected=${before.inspected.size}->${p.inspected.size} errors=${before.errors}->${p.errors} message=${e.message}")
        logFocus(v, "after-dragnear")
    }

    /** Level 1 from the start to the rope step, then the wrong rope and the right rope by the joystick. */
    private suspend fun ropeCheck(v: RescueViewModel) {
        if (v.state.value.page in setOf("placement", "wall-preview", "table-select", "ready")) { enter(v); tap(v, "start"); delay(500) }
        v.debugStart(0); delay(500)
        logFocus(v, "inspect")
        for (t in listOf("front-rope", "rear-rope", "brace-point")) operate(v, t)
        operate(v, "brace-point")
        logFocus(v, "cut-start")
        dragNear(v, "rear-rope", .10)
        dragNear(v, "front-rope", .10)
        logFocus(v, "after-front")
        Log.i(TAG, "ropecheck done phase=${v.engine.progress.phase.id} cuts=${v.engine.progress.cuts} errors=${v.engine.progress.errors} selected=${v.engine.selected ?: "none"}")
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
