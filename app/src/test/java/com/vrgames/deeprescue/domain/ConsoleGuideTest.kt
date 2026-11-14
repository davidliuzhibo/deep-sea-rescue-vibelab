package com.vrgames.deeprescue.domain

import com.vrgames.deeprescue.domain.model.RescuePhase
import com.vrgames.deeprescue.domain.model.RescueProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-11-13 review: only the controls a step needs can be pointed at; each has a card saying what it is for. */
class ConsoleGuideTest {
    private fun at(p: RescuePhase, attached: Boolean = false) = ConsoleGuide.active(RescueProgress(phase = p, attached = attached))

    @Test
    fun aimingStepsUseTheJoystickAndTheExecuteKeyOnly() {
        for (p in listOf(RescuePhase.INSPECT, RescuePhase.BRACE, RescuePhase.CUT)) assertEquals(setOf("handle", "go", "stop"), at(p))
    }

    @Test
    fun goingRoundTheBeamOffersOnlyTheLeversAndAir() {
        val s = at(RescuePhase.WINCH)
        assertTrue("steer" in s && "winch" in s)
        assertFalse("dial" in s || "handle" in s || "air2" in s)
    }

    @Test
    fun theBagStepMovesFromHangingItToFillingIt() {
        assertTrue("go" in at(RescuePhase.BAG) && "air" !in at(RescuePhase.BAG))
        assertTrue("air" in at(RescuePhase.BAG, attached = true) && "go" !in at(RescuePhase.BAG, attached = true))
    }

    @Test
    fun theDialIsOnlyForTheBeaconRings() {
        for (p in RescuePhase.entries) assertEquals(p.name, p == RescuePhase.BEACON_ALIGN, "dial" in at(p))
    }

    @Test
    fun stopIsAlwaysThereWhileWorkingAndEveryControlHasACard() {
        for (p in RescuePhase.entries) if (p != RescuePhase.RESULT && p != RescuePhase.FAREWELL && p != RescuePhase.OCEAN_FAREWELL) assertTrue(p.name, "stop" in at(p))
        for (n in ConsoleGuide.ALL) for (p in RescuePhase.entries) {
            val (t, b) = ConsoleGuide.card(n, p)
            assertTrue(t.isNotBlank() && b.isNotBlank())
        }
    }
}
