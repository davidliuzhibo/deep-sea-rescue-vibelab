package com.vrgames.deeprescue.ui.rescue

import com.vrgames.deeprescue.domain.model.Mount
import com.vrgames.deeprescue.domain.model.RescueProgress
import com.vrgames.deeprescue.domain.model.V3
import com.vrgames.deeprescue.ui.rescue.components.PanelContract

sealed interface RescueEvent {
    data class Control(val id:String,val contract:PanelContract):RescueEvent
    data class SelectTarget(val id:String):RescueEvent
    data class DragStart(val pointer:Long):RescueEvent
    data class DragMove(val pointer:Long,val total:V3):RescueEvent
    data class DragEnd(val pointer:Long,val cancelled:Boolean):RescueEvent
    data object CancelInput:RescueEvent
    /** A console control: a button pressed ([delta] 1), or a lever or knob pushed one step up (1) or down (-1). */
    data class Physical(val name:String,val delta:Int):RescueEvent
}

data class RescueUiState(
    val page:String="placement",val progress:RescueProgress=RescueProgress(),
    val selected:String?=null,val message:String="正在加载救援站。",val surface:String="等待前方墙面与新鲜头部朝向。",
    val mount:Mount?=null,val preview:Mount?=null,val foreground:Boolean=false,val ready:Boolean=false,
    val tension:Double=0.0,val moving:Boolean=false,val acting:Boolean=false,
    val effectId:Long=0,val effect:String="",val generation:Int=0,val trackingValid:Boolean=false,
    val closing:Boolean=false,val saveWarning:String?=null
)
