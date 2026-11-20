package com.vrgames.deeprescue.domain

import com.vrgames.deeprescue.domain.model.V3
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.2 (PICO 1118: 「机械臂触发执行的判定比较严格」): near is near enough; letting go near the target works it. */
class LooserArmTest {
    private fun time(e: RescueEngine, t: Double) { repeat((t * 120).toInt()) { e.tick(1.0 / 120) } }

    /** Drags the arm toward [target] until it is [gap] metres off, then lets go. */
    private fun dragTo(e: RescueEngine, target: V3, gap: Double, release: Boolean = true) {
        assertTrue(e.dragBegin(7))
        var total = V3()
        var guard = 0
        while ((e.progress.tool - target).length() > gap && guard++ < 400) {
            val left = target - e.progress.tool
            total = total + left * (0.015 / maxOf(left.length(), 1e-9))
            e.dragMove(7, total)
            time(e, 0.15)
        }
        if (release) e.dragEnd(7, false)
    }

    @Test
    fun lettingGoWithinSixteenCentimetresWorksTheTarget() {
        val e = RescueEngine(); e.setActive(true)
        val target = e.targetPosition("front-rope")!!
        dragTo(e, target, 0.13)
        time(e, 1.5)
        assertTrue("not inspected: ${e.message}", "front-rope" in e.progress.inspected)
    }

    @Test
    fun executeTwelveCentimetresOffDrawsTheArmOnAndWorks() {
        val e = RescueEngine(); e.setActive(true)
        val target = e.targetPosition("front-rope")!!
        dragTo(e, target, 0.12, release = false)
        // Still holding the stick (moving): 「执行」 stops it and works.
        e.execute()
        time(e, 1.5)
        assertTrue("not inspected: ${e.message}", "front-rope" in e.progress.inspected)
    }

    @Test
    fun farOffItSaysHowFar() {
        val e = RescueEngine(); e.setActive(true)
        e.select("front-rope")
        val far = e.targetPosition("front-rope")!! + V3(0.0, 0.25, 0.1)
        // Put the tool there by a drag, without letting go near anything.
        dragTo(e, far, 0.02, release = false)
        e.execute()
        assertTrue(e.message, e.message.contains("厘米"))
    }
}
