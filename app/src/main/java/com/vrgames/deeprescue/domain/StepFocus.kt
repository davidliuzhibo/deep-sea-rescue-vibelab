package com.vrgames.deeprescue.domain

import com.vrgames.deeprescue.domain.model.MissionGeometry as M
import com.vrgames.deeprescue.domain.model.RescueGeometry as G
import com.vrgames.deeprescue.domain.model.RescuePhase as P
import com.vrgames.deeprescue.domain.model.RescueProgress
import com.vrgames.deeprescue.domain.model.V3

/**
 * Which target a step is about right now (v1.2.1, 2026-11-20 device test: the gold ring and dotted line lit 「尾部绳」
 * while the script said 先点「前鳍绳」, because they went to the allowed target nearest the arm; letting go near the wrong
 * rope cut it and counted a mistake; the ring stayed on cut ropes, on the hooked strap buckle, on spots already lit and
 * on the side already hooked; and in the three-wheel puzzle it picked a wheel by distance, which gave the puzzle away).
 *
 * - Ordered steps (cutting the ropes): only the next one, whatever is near or picked.
 * - Unordered steps (look-overs, the two lift rings): the one picked if not done yet, else the nearest one not done.
 * - The wheel puzzle: only the wheel the player picked; nothing is suggested.
 * - Steps whose next move is on the console (the float bag hooked, winching, lifting, the route): none.
 */
object StepFocus {
    /** The targets of this step in the order the script names them. */
    fun order(p: RescueProgress): List<String> = when (p.phase) {
        P.INSPECT -> listOf("front-rope", "rear-rope", "brace-point")
        P.BRACE -> listOf("brace-point")
        P.CUT -> listOf("front-rope", "rear-rope")
        P.BAG -> listOf("float-hook")
        P.BEACON_SURVEY -> listOf("power-port", "lens-left", "lens-mid")
        P.BEACON_POWER -> listOf("power-port")
        P.BEACON_ALIGN -> listOf("lens-left", "lens-mid", "lens-right")
        P.BALANCE_SURVEY -> listOf("anchor-left", "anchor-right", "load-weight")
        P.BALANCE_ATTACH -> listOf("anchor-left", "anchor-right")
        else -> emptyList()
    }

    /** Steps that must be done in the script's order. */
    fun ordered(p: RescueProgress) = p.phase == P.CUT

    /** The puzzle step: the player chooses, the game does not point. */
    fun puzzle(p: RescueProgress) = p.phase == P.BEACON_ALIGN

    /** Already done in this step (lit, cut, hooked...). */
    fun done(p: RescueProgress, id: String): Boolean = when (p.phase) {
        P.INSPECT -> id in p.inspected
        P.BRACE -> p.braced
        P.CUT -> order(p).indexOf(id).let { it in 0 until p.cuts }
        P.BAG -> p.attached
        P.BEACON_SURVEY, P.BALANCE_SURVEY -> id in p.work.surveyed
        P.BEACON_POWER -> p.work.cell
        P.BALANCE_ATTACH -> id in p.work.bags
        else -> false
    }

    /** The targets of this step not done yet, in the script's order. */
    fun remaining(p: RescueProgress): List<String> = order(p).filter { !done(p, it) }

    /** What letting go of the stick (or 「执行」 with nothing picked) may work on: for ordered steps only the next one. */
    fun reachable(p: RescueProgress): List<String> = remaining(p).let { if (ordered(p)) it.take(1) else it }

    /** The one target the ring and the dotted line show, or null when the step's next move is not on a target. */
    fun focus(p: RescueProgress, selected: String?): String? {
        val left = remaining(p)
        if (left.isEmpty()) return null
        return when {
            ordered(p) -> left.first()
            puzzle(p) -> selected?.takeIf { it in left }
            else -> selected?.takeIf { it in left } ?: left.minByOrNull { (p.tool - position(p, it)).length() }
        }
    }

    /** Where target [id] is in this mission (the weight sits on the heavy side). */
    fun position(p: RescueProgress, id: String): V3 =
        if (id == "load-weight" && !p.work.heavyRight) M.targets.getValue(id).let { it.copy(x = -it.x) }
        else G.targets[id] ?: M.targets.getValue(id)

    fun name(id: String) = G.names[id] ?: M.names[id] ?: id

    /** The panel line when nothing is picked: names the next target, or what to do instead. */
    fun nextLabel(p: RescueProgress, selected: String?): String {
        selected?.let { return name(it) }
        val f = focus(p, null)
        return when {
            f != null && ordered(p) -> "下一步：${name(f)}"
            f != null -> "下一步：${remaining(p).joinToString("、") { name(it) }}"
            puzzle(p) -> "先点一只导光轮"
            p.phase == P.BAG && p.attached -> "下一步：把「充气」旋钮往上推两下"
            else -> "用操纵台上的控件"
        }
    }

    /** One line for the debug log. */
    fun describe(p: RescueProgress, selected: String?) =
        "FOCUS phase=${p.phase.id} focus=${focus(p, selected) ?: "none"} selected=${selected ?: "none"} remaining=${remaining(p)}"
}
