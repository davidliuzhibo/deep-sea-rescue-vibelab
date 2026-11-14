package com.vrgames.deeprescue.domain

import com.vrgames.deeprescue.domain.model.RescuePhase as P
import com.vrgames.deeprescue.domain.model.RescueProgress

/**
 * Which console controls do something at each step, and what each one is for (2026-11-13 review: many buttons and levers
 * with too little explanation; the back row hid behind the front row; at the beam it was unclear which control to use).
 * Controls that do nothing now cannot be pointed at or pinched and are dimmed; those in use light up under the finger
 * and, pointed at, show a short card with their name, purpose and use.
 */
object ConsoleGuide {
    val ALL = listOf("handle", "go", "stop", "dial", "air", "air2", "winch", "steer")

    private val aims = setOf(P.INSPECT, P.BRACE, P.CUT, P.BAG, P.BEACON_SURVEY, P.BEACON_POWER, P.BEACON_ALIGN, P.BALANCE_SURVEY, P.BALANCE_ATTACH)

    /** The controls that act at this point of the rescue. The stop button always does. */
    fun active(p: RescueProgress): Set<String> {
        val s = HashSet<String>()
        if (p.phase == P.RESULT || p.phase == P.FAREWELL || p.phase == P.OCEAN_FAREWELL) return s
        s += "stop"
        if (p.phase in aims) s += "handle"
        when (p.phase) {
            P.INSPECT, P.BRACE, P.CUT, P.BEACON_SURVEY, P.BEACON_POWER, P.BEACON_ALIGN, P.BALANCE_SURVEY, P.BALANCE_ATTACH, P.ESCORT_PLAN -> s += "go"
            P.BAG -> if (p.attached) s += "air" else s += "go"
            P.WINCH -> s += listOf("air", "winch", "steer")
            P.BALANCE_LIFT -> s += listOf("go", "air", "air2", "winch", "steer")
            else -> {}
        }
        if (p.phase == P.BEACON_ALIGN) s += "dial"
        return s
    }

    /** The card shown when the finger points at [name]: its name, then what it does and how, for this step. */
    fun card(name: String, phase: P): Pair<String, String> {
        val lift = phase == P.BALANCE_LIFT
        return when (name) {
            "handle" -> "摇杆" to "捏住拖动：机械臂跟着手移动，用来对准目标。"
            "go" -> "执行（橙色按键）" to when {
                lift -> "按一下：锁定货笼。"
                phase == P.ESCORT_PLAN -> "按一下：放行，护送海龟出发。"
                else -> "按一下：对准目标后，让工具动手（照亮、夹紧、剪开、挂上）。"
            }
            "stop" -> "停（红色按键）" to "按一下：所有动作立刻停下，位置保持不动。"
            "dial" -> "转轮旋钮（绿色）" to "往上推转一格，往下推退一格。"
            "air" -> (if (lift) "左袋充气（蓝色）" else "充气旋钮（蓝色）") to "往上推充一档气，往下推放一档。"
            "air2" -> "右袋充气（蓝色）" to "往上推充一档气，往下推放一档。"
            "winch" -> (if (lift) "提升拉杆（橙色）" else "收缆拉杆（橙色）") to if (lift) "捏住往右拨提升一小段，往左拨放低。" else "捏住往右拨收一小段缆，往左拨放缆、降低张力。"
            "steer" -> (if (lift) "停放拉杆（蓝色）" else "引导拉杆（蓝色）") to if (lift) "捏住往右拨向右挪一小段，往左拨挪回来。" else "管方向：捏住往左拨把海龟往左引，往右拨往右引。"
            else -> name to ""
        }
    }

    /** The levers move sideways (right +, left -); the knobs up and down (device 2026-11-13: an upward push was hard to read). */
    fun sideways(name: String) = name == "winch" || name == "steer"
}
