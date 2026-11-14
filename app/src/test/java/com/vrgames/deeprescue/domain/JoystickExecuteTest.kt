package com.vrgames.deeprescue.domain

import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-11-13 device test: the arm moved to the target with the joystick, 「执行」 did nothing until the target was picked. */
class JoystickExecuteTest {
    private fun time(e: RescueEngine, t: Double) { repeat((t * 120).toInt()) { e.tick(1.0 / 120) } }

    @Test
    fun executeWorksOnTheTargetTheJoystickBroughtTheArmTo() {
        val e = RescueEngine(); e.setActive(true)
        val target = e.targetPosition("front-rope")!!
        assertTrue(e.dragBegin(1))
        // The hand moves in small steps, as on the headset; the arm follows at its own gain.
        var total = com.vrgames.deeprescue.domain.model.V3()
        var guard = 0
        while ((e.progress.tool - target).length() > 0.04 && guard++ < 400) {
            val left = target - e.progress.tool
            total = total + left * (0.02 / maxOf(left.length(), 1e-9))
            e.dragMove(1, total)
            time(e, 0.2)
        }
        e.dragEnd(1, false)
        assertTrue("arm still ${(e.progress.tool - target).length()} m away", (e.progress.tool - target).length() <= RescueEngine.NEAR)
        e.execute()
        time(e, 1.5)
        assertTrue("not inspected: ${e.message}", "front-rope" in e.progress.inspected)
    }

    @Test
    fun executeFarFromEveryTargetSaysWhatToDo() {
        val e = RescueEngine(); e.setActive(true)
        e.execute()
        assertTrue(e.message.contains("摇杆"))
    }
}
