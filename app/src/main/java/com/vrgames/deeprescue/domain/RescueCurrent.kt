package com.vrgames.deeprescue.domain

import kotlin.math.PI
import kotlin.math.sin

/** Gentle, predictable 12-second current. Simulation time freezes with pause/tracking loss. */
object RescueCurrent {
    fun strength(elapsed:Double)=.5+.5*sin(elapsed*2*PI/12)
    fun pushMetresPerSecond(elapsed:Double)=strength(elapsed)*.004
}
