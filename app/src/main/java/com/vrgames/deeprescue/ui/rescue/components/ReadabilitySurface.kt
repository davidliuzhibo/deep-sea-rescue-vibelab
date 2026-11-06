package com.vrgames.deeprescue.ui.rescue.components
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.pico.spatial.ui.design.PicoTheme
@Composable internal fun Modifier.readabilitySurface(n:JsonObject):Modifier {
    val clipped=clip(RoundedCornerShape(n.obj("appearance")!!.number("radiusDp",16f).dp))
    val filled=when(n.str("id")) {
        "welcome" -> clipped
            // design-style: design-surface welcome
            .background(PicoTheme.colorScheme.fillPrimary)
        "placement-instruction" -> clipped
            // design-style: design-surface placement-instruction
            .background(PicoTheme.colorScheme.fillPrimary)
        "placement-status" -> clipped
            // design-style: design-surface placement-status
            .background(PicoTheme.colorScheme.fillPrimary)
        "wall-info" -> clipped
            // design-style: design-surface wall-info
            .background(PicoTheme.colorScheme.fillPrimary)
        "wall-guide" -> clipped
            // design-style: design-surface wall-guide
            .background(PicoTheme.colorScheme.fillPrimary)
        "table-guide" -> clipped
            // design-style: design-surface table-guide
            .background(PicoTheme.colorScheme.fillPrimary)
        "table-status" -> clipped
            // design-style: design-surface table-status
            .background(PicoTheme.colorScheme.fillPrimary)
        "ready-story" -> clipped
            // design-style: design-surface ready-story
            .background(PicoTheme.colorScheme.fillPrimary)
        "ready-controls" -> clipped
            // design-style: design-surface ready-controls
            .background(PicoTheme.colorScheme.fillPrimary)
        "ready-system" -> clipped
            // design-style: design-surface ready-system
            .background(PicoTheme.colorScheme.fillPrimary)
        "inspect-hint" -> clipped
            // design-style: design-surface inspect-hint
            .background(PicoTheme.colorScheme.fillPrimary)
        "inspect-selection" -> clipped
            // design-style: design-surface inspect-selection
            .background(PicoTheme.colorScheme.fillPrimary)
        "inspect-feedback" -> clipped
            // design-style: design-surface inspect-feedback
            .background(PicoTheme.colorScheme.fillPrimary)
        "brace-hint" -> clipped
            // design-style: design-surface brace-hint
            .background(PicoTheme.colorScheme.fillPrimary)
        "brace-selection" -> clipped
            // design-style: design-surface brace-selection
            .background(PicoTheme.colorScheme.fillPrimary)
        "brace-feedback" -> clipped
            // design-style: design-surface brace-feedback
            .background(PicoTheme.colorScheme.fillPrimary)
        "cut-hint" -> clipped
            // design-style: design-surface cut-hint
            .background(PicoTheme.colorScheme.fillPrimary)
        "cut-selection" -> clipped
            // design-style: design-surface cut-selection
            .background(PicoTheme.colorScheme.fillPrimary)
        "cut-feedback" -> clipped
            // design-style: design-surface cut-feedback
            .background(PicoTheme.colorScheme.fillPrimary)
        "bag-hint" -> clipped
            // design-style: design-surface bag-hint
            .background(PicoTheme.colorScheme.fillPrimary)
        "bag-selection" -> clipped
            // design-style: design-surface bag-selection
            .background(PicoTheme.colorScheme.fillPrimary)
        "bag-feedback" -> clipped
            // design-style: design-surface bag-feedback
            .background(PicoTheme.colorScheme.fillPrimary)
        "bag-gauges" -> clipped
            // design-style: design-surface bag-gauges
            .background(PicoTheme.colorScheme.fillPrimary)
        "winch-hint" -> clipped
            // design-style: design-surface winch-hint
            .background(PicoTheme.colorScheme.fillPrimary)
        "winch-selection" -> clipped
            // design-style: design-surface winch-selection
            .background(PicoTheme.colorScheme.fillPrimary)
        "winch-feedback" -> clipped
            // design-style: design-surface winch-feedback
            .background(PicoTheme.colorScheme.fillPrimary)
        "winch-gauges" -> clipped
            // design-style: design-surface winch-gauges
            .background(PicoTheme.colorScheme.fillPrimary)
        "adjust-guide" -> clipped
            // design-style: design-surface adjust-guide
            .background(PicoTheme.colorScheme.fillPrimary)
        "adjust-status" -> clipped
            // design-style: design-surface adjust-status
            .background(PicoTheme.colorScheme.fillPrimary)
        "farewell-story" -> clipped
            // design-style: design-surface farewell-story
            .background(PicoTheme.colorScheme.fillPrimary)
        "farewell-status" -> clipped
            // design-style: design-surface farewell-status
            .background(PicoTheme.colorScheme.fillPrimary)
        "result-story" -> clipped
            // design-style: design-surface result-story
            .background(PicoTheme.colorScheme.fillPrimary)
        "result-record" -> clipped
            // design-style: design-surface result-record
            .background(PicoTheme.colorScheme.fillPrimary)
        "pause-text" -> clipped
            // design-style: design-surface pause-text
            .background(PicoTheme.colorScheme.fillPrimary)
        "settings-guide" -> clipped
            // design-style: design-surface settings-guide
            .background(PicoTheme.colorScheme.fillPrimary)
        "settings-values" -> clipped
            // design-style: design-surface settings-values
            .background(PicoTheme.colorScheme.fillPrimary)
        "tracking-body" -> clipped
            // design-style: design-surface tracking-body
            .background(PicoTheme.colorScheme.fillPrimary)
        "tracking-reason" -> clipped
            // design-style: design-surface tracking-reason
            .background(PicoTheme.colorScheme.fillPrimary)
        "error-reason" -> clipped
            // design-style: design-surface error-reason
            .background(PicoTheme.colorScheme.fillPrimary)
        "error-hint" -> clipped
            // design-style: design-surface error-hint
            .background(PicoTheme.colorScheme.fillPrimary)
        "exit-copy" -> clipped
            // design-style: design-surface exit-copy
            .background(PicoTheme.colorScheme.fillPrimary)
        "exit-done" -> clipped
            // design-style: design-surface exit-done
            .background(PicoTheme.colorScheme.fillPrimary)
        // A surface the list forgot must not take the whole game down.
        else->clipped.background(PicoTheme.colorScheme.fillPrimary)
    }
    return filled.padding((n.obj("layout")?.number("paddingDp")?:0f).dp)
}
