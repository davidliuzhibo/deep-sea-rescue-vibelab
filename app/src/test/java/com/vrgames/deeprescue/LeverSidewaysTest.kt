package com.vrgames.deeprescue

import com.vrgames.deeprescue.domain.ConsoleGuide
import com.vrgames.deeprescue.domain.model.RescuePhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Device 2026-11-13: pushing a lever upward was hard to read, so the two levers move sideways. */
class LeverSidewaysTest {
    @Test
    fun theTwoLeversMoveSidewaysTheKnobsUpAndDown() {
        assertTrue(ConsoleGuide.sideways("winch"))
        assertTrue(ConsoleGuide.sideways("steer"))
        for (k in listOf("dial", "air", "air2", "go", "stop", "handle")) assertFalse(k, ConsoleGuide.sideways(k))
    }

    @Test
    fun theLeverCardsSaySideways() {
        for (phase in listOf(RescuePhase.WINCH, RescuePhase.BALANCE_LIFT)) for (k in listOf("winch", "steer")) {
            val (_, body) = ConsoleGuide.card(k, phase)
            assertTrue("$k $phase: $body", "往右拨" in body && "往左拨" in body)
            assertFalse("$k $phase: $body", "往上推" in body || "往下推" in body)
        }
    }
}
