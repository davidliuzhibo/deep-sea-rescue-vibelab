package com.vrgames.deeprescue.ui.rescue

import kotlin.math.abs

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vrgames.deeprescue.core.Facing
import com.vrgames.deeprescue.domain.model.EyePose
import com.vrgames.deeprescue.domain.model.V3
import com.vrgames.deeprescue.game.AutoPilot
import com.vrgames.deeprescue.spatial.RescueAudio
import com.vrgames.deeprescue.spatial.RescueScene
import com.vrgames.deeprescue.spatial.RoomTracking
import com.vrgames.deeprescue.ui.rescue.components.PanelContract
import com.vrgames.deeprescue.ui.rescue.components.RescuePanel
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive

@Composable fun RescueScreen(){
    val context=LocalContext.current
    val vm:RescueViewModel=viewModel(factory=RescueViewModelFactory(context.filesDir))
    val state by vm.state.collectAsStateWithLifecycle()
    RescueContent(state,vm)
}
@Composable private fun RescueContent(state:RescueUiState,vm:RescueViewModel){
    val context=LocalContext.current;val density=LocalDensity.current;val converter=LocalPhysicalLengthConverter.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle;val nav=LocalSpatialNavigator.current
    val contract=remember{context.assets.open("design/design-spec.json").bufferedReader().use{PanelContract(it.readText())}}
    remember(vm){AutoPilot.attach(vm,contract)}
    val tracking=remember{RoomTracking()};val audio=remember{RescueAudio(context.applicationContext)}
    val latest by rememberUpdatedState(state)
    var scene by remember{mutableStateOf<RescueScene?>(null)}
    DisposableEffect(Unit){onDispose{vm.onEvent(RescueEvent.CancelInput);tracking.stop();audio.close();scene?.release();scene=null}}
    val audible=state.foreground&&state.ready&&state.page !in listOf("pause","tracking","error","exit-confirm","exited")
    LaunchedEffect(state.progress.settings,audible){audio.update(state.progress.settings,audible)}
    LaunchedEffect(state.effectId){if(state.effectId>0)audio.effect(state.effect)}
    LaunchedEffect(state.page){if(state.page=="exited"){audio.update(state.progress.settings,false);tracking.stop();nav.closeStage()}}
    LaunchedEffect(scene,lifecycle){val native=scene?:return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            tracking.start();vm.foreground(true);var before=withFrameNanos{it};var roomClock=0.0;val facing=Facing();var hadMount=false
            try{while(isActive){val at=withFrameNanos{it};val dt=(at-before)/1e9;before=at;roomClock+=dt
                val mounted=vm.state.value.mount!=null;if(hadMount&&!mounted)facing.again();hadMount=mounted
                // The first panel is placed once, and then stays whatever the head does.
                if(!facing.placed)tracking.eye(native.root)?.let{eye->if(facing.update(Facing.heading(eye.forward.x,eye.forward.z),dt)){native.alignMenu(eye.eye,V3(facing.forwardX,0.0,facing.forwardZ));Log.i(AutoPilot.TAG,"facing placed at ${facing.yaw}")}}
                if(facing.placed&&native.needsMenu())tracking.eye(native.root)?.let{native.alignMenu(it.eye,it.forward)}
                if(roomClock>=.06){roomClock=0.0;val seen=tracking.eye(native.root);val pose=seen?.takeIf{facing.placed}?.let{EyePose(it.eye,V3(facing.forwardX,0.0,facing.forwardZ),it.at)};val planes=tracking.surfaces(native.root);vm.room(pose,planes,tracking.running(),pose?.at?:SystemClock.uptimeMillis())}
                AutoPilot.view?.let{native.debugView(it)}
                if(AutoPilot.debug)repeat(((dt*AutoPilot.timeScale)/.1).toInt().coerceIn(1,400)){vm.advance(.1)}else vm.advance(dt)
                native.render(vm.visualState())
            }}finally{vm.foreground(false);tracking.stop();audio.update(vm.state.value.progress.settings,false)}
        }
    }
    key(state.generation){
    // The panel reads live values; this makes it read them again. Without it, text and enabled buttons go stale until the page changes.
    val panelTick by androidx.compose.runtime.produceState(0){while(true){kotlinx.coroutines.delay(150);value++}}
        SpatialView(modifier=Modifier.fillMaxSize().pointerInput(state.generation,state.foreground,state.ready,state.page){
            var owner:Long?=null;var target:String?=null;var start=V3();var dragging=false
            fun physical(v:V3)=with(density){V3(converter.dpToLength(v.x.toFloat().toDp(),LengthUnit.Meters).toDouble(),-converter.dpToLength(v.y.toFloat().toDp(),LengthUnit.Meters).toDouble(),converter.dpToLength(v.z.toFloat().toDp(),LengthUnit.Meters).toDouble())}
            try{detectSpatialPointerEvent(context=context,targetedToEntity=TargetEntity.any(),onEvent={events->
                var consumed=false
                for(event in events){val native=scene?:continue
                    if(!latest.foreground||!latest.ready||!latest.trackingValid)continue
                    val p=V3(event.x.toDouble(),event.y.toDouble(),event.z.toDouble())
                    // The finger pointing, not pinching, at a console control: a short card says what it is for.
                    if(owner==null&&!event.pressed&&!event.isUpEvent()&&!event.isDownEvent()){
                        val id=native.target(event.targetedEntity)
                        val name=when{id=="handle"->"handle";id?.startsWith("ctl.")==true->id.removePrefix("ctl.");else->null}
                        if(name!=null){val (t,b)=com.vrgames.deeprescue.domain.ConsoleGuide.card(name,latest.progress.phase);native.pointAt(name,t,b)}
                        continue}
                    if(event.isDownEvent()&&owner==null){val id=native.target(event.targetedEntity)
                        if(id!=null){owner=event.pointerId.value;target=id;start=p;dragging=false;consumed=true
                            // The card also comes with a pinch, in case the system sends no pointing-only events.
                            val name=when{id=="handle"->"handle";id.startsWith("ctl.")->id.removePrefix("ctl.");else->null}
                            if(name!=null){native.held=name;val (t,b)=com.vrgames.deeprescue.domain.ConsoleGuide.card(name,latest.progress.phase);native.pointAt(name,t,b)}}}
                    // Console controls: a press, or pushes of a knob (up +, down -) or a lever (right +, left -), a step
                    // per 4 cm. The levers go sideways: an upward push was hard for the headset to read (2026-11-13).
                    if(owner==event.pointerId.value&&target?.startsWith("ctl.")==true){consumed=true
                        val name=target!!.removePrefix("ctl.");val d=physical(p-start)
                        val amount=if(com.vrgames.deeprescue.domain.ConsoleGuide.sideways(name))d.x else d.y
                        if(event.pressed&&abs(amount)>=.04){val step=if(amount>0)1 else -1;vm.onEvent(RescueEvent.Physical(name,step));start=p;dragging=true}
                        if(event.isUpEvent()||!event.pressed){if(!dragging&&event.isUpEvent())vm.onEvent(RescueEvent.Physical(name,1));owner=null;target=null;dragging=false;native.held=null}
                        continue}
                    if(owner==event.pointerId.value){consumed=true;val delta=native.deltaFromRoot(physical(p-start))
                        if(target=="handle"&&!dragging&&event.pressed&&delta.length()>.009)dragging=vm.onEvent(RescueEvent.DragStart(owner!!))
                        if(dragging&&(event.pressed||event.isUpEvent()))vm.onEvent(RescueEvent.DragMove(owner!!,delta))
                        if(event.isUpEvent()){if(dragging)vm.onEvent(RescueEvent.DragEnd(owner!!,false))else if(target!="handle")target?.let{vm.onEvent(RescueEvent.SelectTarget(it))};owner=null;target=null;dragging=false;native.held=null}
                        else if(!event.pressed){owner?.let{vm.onEvent(RescueEvent.DragEnd(it,true))};owner=null;target=null;dragging=false;native.held=null}
                    }
                };consumed
            })}finally{owner?.let{vm.onEvent(RescueEvent.DragEnd(it,true))};vm.onEvent(RescueEvent.CancelInput);scene?.held=null}
        },attachments={AttachmentPanel(id="controls"){
        val panelRead=androidx.compose.runtime.remember(panelTick){{key:String->vm.binding(key)}}
            Box(Modifier.size(820.dp,660.dp)){RescuePanel(contract,state.page,panelRead){vm.onEvent(RescueEvent.Control(it,contract))}}
        }}) {content,attachments->
            val native=RescueScene();scene?.release();scene=native
            try{native.load({content.addEntity(it)},{attachments.entity("controls")},.92f/converter.dpToLength(820.dp,LengthUnit.Meters));vm.resourcesReady();AutoPilot.ready()}
            catch(e:CancellationException){native.release();throw e}
            catch(e:Exception){Log.e("DeepSeaRescue","SCENE_LOAD_FAILED",e);vm.resourcesFailed()}
        }
    }
}
