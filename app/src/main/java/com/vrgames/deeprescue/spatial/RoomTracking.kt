package com.vrgames.deeprescue.spatial

import android.os.SystemClock
import android.util.Log
import com.vrgames.deeprescue.domain.model.EyePose
import com.vrgames.deeprescue.domain.model.RoomPlane
import com.vrgames.deeprescue.domain.model.SurfaceKind
import com.vrgames.deeprescue.domain.model.Triangle
import com.vrgames.deeprescue.domain.model.V3
import com.vrgames.deeprescue.game.AutoPilot
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.sense.base.AnchorUpdate
import com.pico.spatial.sense.base.TrackingState
import com.pico.spatial.sense.plane.PlaneAnchor
import com.pico.spatial.sense.plane.PlaneOrientation
import com.pico.spatial.sense.plane.PlaneTrackingManager
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** App instance scoped; public OS gaze/hand-ray taps remain the only activation path. */
class RoomTracking {
    private companion object { const val SAMPLE_GRACE_MS=1000L }
    private var scope:CoroutineScope?=null
    private var hmd:HMDTrackingProvider?=null
    private var sub:Cancellable?=null
    private var sample:EyePose?=null
    private val planes=linkedMapOf<String,RoomPlane>()
    fun start() {
        stop();val owner=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);scope=owner
        try {
            val p=HMDTrackingProvider();hmd=p
            owner.launch(start=CoroutineStart.UNDISPATCHED){try{p.dataFlow.collect{data->
                if(p.state==DataProvider.State.STARTED&&p.supportState==DataProvider.SupportState.SUPPORTED)
                    sample=EyePose(data.hmdPose.position.v(),data.hmdPose.rotation.rotateVector(Vector3(0f,0f,-1f)).v(),SystemClock.uptimeMillis())
                else sample=null
            }}catch(e:CancellationException){throw e}catch(e:Exception){sample=null;Log.w("DeepSeaRescue","HMD stream failed",e)}}
            p.start()
        }catch(e:Exception){Log.w("DeepSeaRescue","HMD start failed",e)}
        // The room is not scanned: the game always stands at the same place in front of the player.
    }
    private fun absorb(a:PlaneAnchor,removed:Boolean) {
        val id=a.anchorUUID.toString()
        synchronized(planes) {
            if(removed)planes.remove(id)
            else if(a.planeOrientation in listOf(PlaneOrientation.HORIZONTAL_UPWARD,PlaneOrientation.VERTICAL)) {
                            val q=a.transform.rotation.toQuat()
                            val v=a.vertices.map{(a.transform.position+q.rotateVector(it)).v()}
                            val triangles=a.indices.chunked(3).mapNotNull{t->if(t.size==3&&t.all{it in v.indices})Triangle(v[t[0]],v[t[1]],v[t[2]])else null}
                            planes[id]=RoomPlane(id,triangles,q.rotateVector(Vector3(0f,1f,0f)).v(),if(a.planeOrientation==PlaneOrientation.VERTICAL)SurfaceKind.WALL else SurfaceKind.UP,SystemClock.uptimeMillis())
            }else planes.remove(id)
        }
    }
    fun eye(root:Entity):EyePose? {
        val now=SystemClock.uptimeMillis()
        // On the emulator (about 1 frame per second, head may never report) the walk still needs a pose.
        val p=sample?:if(AutoPilot.debug)EyePose(V3(0.0,1.6,0.0),V3(0.0,0.0,-1.0),now)else return null
        // A slow frame on the main thread can make the newest sample look old, and that is not a tracking loss.
        if(now-p.at>(if(AutoPilot.debug)600_000L else SAMPLE_GRACE_MS))return null
        val e=root.convertPositionFrom(p.eye.sdk(),null).v()
        val f=root.convertPositionFrom((p.eye+p.forward).sdk(),null).v()-e
        return EyePose(e,f.normalized(),now)
    }
    fun surfaces(root:Entity):List<RoomPlane> = synchronized(planes){planes.values.map{p->
        fun local(v:V3)=root.convertPositionFrom(v.sdk(),null).v()
        p.copy(triangles=p.triangles.map{Triangle(local(it.a),local(it.b),local(it.c))},normal=(local(p.normal)-local(V3())).normalized())
    }}
    fun running()=runCatching{PlaneTrackingManager.state==TrackingState.RUNNING}.getOrDefault(false)
    fun stop(){scope?.cancel();scope=null;runCatching{hmd?.stop()};hmd=null;sample=null;runCatching{sub?.cancel()};sub=null;runCatching{PlaneTrackingManager.stop()};synchronized(planes){planes.clear()}}
}
private fun Vector3.v()=V3(x.toDouble(),y.toDouble(),z.toDouble())
