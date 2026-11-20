package com.vrgames.deeprescue.domain

import com.vrgames.deeprescue.domain.model.MissionWork
import com.vrgames.deeprescue.domain.model.RescueGeometry as G
import com.vrgames.deeprescue.domain.model.RescuePhase as P
import com.vrgames.deeprescue.domain.model.RescueProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v1.2.1 (device test 2026-11-20): the ring and the dotted line go to what the script asks for, never to a done target. */
class StepFocusTest {
    private val cut = RescueProgress(phase = P.CUT, inspected = setOf("front-rope", "rear-rope", "brace-point"), braced = true, tool = G.targets.getValue("brace-point"))

    @Test fun ropesInScriptOrderWhateverIsNearOrPicked() {
        // The arm parked at the beam clamp is nearer 尾部绳: the ring still goes to 前鳍绳.
        assertEquals("front-rope", StepFocus.focus(cut, null))
        assertEquals("front-rope", StepFocus.focus(cut, "rear-rope"))
        assertEquals("front-rope", StepFocus.focus(cut.copy(tool = G.targets.getValue("rear-rope")), "rear-rope"))
        assertEquals(listOf("front-rope", "rear-rope"), StepFocus.remaining(cut))
        assertEquals(listOf("front-rope"), StepFocus.reachable(cut))
        assertEquals("下一步：前鳍绳", StepFocus.nextLabel(cut, null))
    }

    @Test fun aCutRopeLetsTheRingMoveOn() {
        val one = cut.copy(cuts = 1)
        assertEquals("rear-rope", StepFocus.focus(one, "front-rope"))
        assertEquals(listOf("rear-rope"), StepFocus.remaining(one))
        assertEquals("下一步：尾部绳", StepFocus.nextLabel(one, null))
    }

    @Test fun lookOverTakesThePickedOneElseTheNearestNotDone() {
        val p = RescueProgress(tool = G.targets.getValue("rear-rope"))
        assertEquals("rear-rope", StepFocus.focus(p, null))
        assertEquals("brace-point", StepFocus.focus(p, "brace-point"))
        val lit = p.copy(inspected = setOf("rear-rope"))
        // Picked but already lit: not ringed again; the nearest one not lit instead.
        val f = StepFocus.focus(lit, "rear-rope")
        assertEquals(listOf("front-rope", "brace-point").minByOrNull { (lit.tool - G.targets.getValue(it)).length() }, f)
        assertEquals(listOf("front-rope", "brace-point"), StepFocus.remaining(lit))
    }

    @Test fun hookedBagLeavesNothingToRing() {
        val p = RescueProgress(phase = P.BAG, braced = true, cuts = 2, attached = true, tool = G.targets.getValue("float-hook"))
        assertNull(StepFocus.focus(p, "float-hook"))
        assertEquals(emptyList<String>(), StepFocus.reachable(p))
        assertEquals("下一步：把「充气」旋钮往上推两下", StepFocus.nextLabel(p, null))
        assertEquals("float-hook", StepFocus.focus(p.copy(attached = false), null))
    }

    @Test fun wheelPuzzleNeverSuggestsAWheel() {
        val p = RescueProgress(phase = P.BEACON_ALIGN, tool = com.vrgames.deeprescue.domain.model.MissionGeometry.targets.getValue("lens-mid"),
            work = MissionWork(isolated = true, cell = true))
        assertNull(StepFocus.focus(p, null))
        assertEquals("lens-left", StepFocus.focus(p, "lens-left"))
        assertEquals("先点一只导光轮", StepFocus.nextLabel(p, null))
    }

    @Test fun liftRingsSkipTheSideAlreadyHooked() {
        val p = RescueProgress(phase = P.BALANCE_ATTACH, tool = com.vrgames.deeprescue.domain.model.MissionGeometry.targets.getValue("anchor-left"),
            work = MissionWork(surveyed = setOf("anchor-left", "anchor-right", "load-weight"), bags = setOf("anchor-left")))
        assertEquals("anchor-right", StepFocus.focus(p, "anchor-left"))
        assertEquals("anchor-right", StepFocus.focus(p, null))
    }

    @Test fun surveyedSpotsAreNotRingedAgain() {
        val p = RescueProgress(phase = P.BEACON_SURVEY, tool = com.vrgames.deeprescue.domain.model.MissionGeometry.targets.getValue("power-port"),
            work = MissionWork(surveyed = setOf("power-port")))
        val f = StepFocus.focus(p, "power-port")
        org.junit.Assert.assertTrue("focus $f", f in listOf("lens-left", "lens-mid"))
    }

    @Test fun consoleStepsHaveNoTarget() {
        for (phase in listOf(P.WINCH, P.BALANCE_LIFT, P.ESCORT_PLAN, P.BEACON_TEST, P.RESULT)) assertNull(phase.id, StepFocus.focus(RescueProgress(phase = phase), null))
    }
}
