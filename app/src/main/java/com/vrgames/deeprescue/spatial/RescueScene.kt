package com.vrgames.deeprescue.spatial

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.vrgames.deeprescue.domain.model.MissionGeometry
import com.vrgames.deeprescue.domain.model.Mount
import com.vrgames.deeprescue.domain.model.RescueGeometry
import com.vrgames.deeprescue.domain.model.RescuePhase
import com.vrgames.deeprescue.domain.model.V3
import com.vrgames.deeprescue.ui.rescue.RescueUiState
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.ImageBasedLightSource
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.PortalComponent
import com.pico.spatial.core.ecs.PortalWorldComponent
import com.pico.spatial.core.ecs.StageEnvironmentLightingComponent
import com.pico.spatial.core.ecs.SpotLightComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.animation.AnimationPlaybackController
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.PortalMaterial
import com.pico.spatial.core.ecs.resource.Resource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureCreateOption
import com.pico.spatial.core.ecs.resource.TextureEncoding
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

/** Owns rendering/resources only. The production engine owns progress and collision rules. */
class RescueScene {
    val root=group("RescueWorld")
    private val placementOwner=group("placementOwner")
    private val fittedSceneRoot=group("fittedSceneRoot")
    private val ocean=group("PortalWorld")
    private val aperture=group("ObservationAperture")
    private val consoleRoot=group("ConsolePlacement")
    private val templates=linkedMapOf<String,Entity>()
    private val resources=mutableListOf<Resource>()
    private val animation=mutableListOf<AnimationPlaybackController>()
    private val models=linkedMapOf<String,Entity>()
    private val targets=linkedMapOf<String,Entity>()
    private val targetVisuals=linkedMapOf<String,Entity>()
    private val doneVisuals=linkedMapOf<String,Entity>()
    private val ropes=mutableListOf<List<Entity>>()
    private val dialAngles=DoubleArray(3)
    private val bagAirShown=DoubleArray(2)
    private var cageRollShown=0.0
    private var lastMission=-1
    private val extras=linkedMapOf<String,Entity>()
    private val extraTargets=linkedMapOf<String,Entity>()
    private val particles=mutableListOf<Entity>()
    private val cutAt=mutableMapOf<Int,Double>()
    private var oldCuts:Int?=null
    private var controls:Entity?=null
    private var attachment:(()->Entity?)?=null
    private var panelScale=1f
    private var panelPosition=V3()
    private var panelYaw=0f
    private var panelPositioned=false
    private var mount:Mount?=null
    private var ready=false
    private var disposed=false
    private var visualTime=-1.0
    private var displayedAir=0.0
    private val completedMarkers=mutableSetOf<String>()
    private lateinit var ball:MeshResource
    private lateinit var cylinder:MeshResource
    private lateinit var gold:PhysicallyBasedMaterial
    private lateinit var cyan:UnlitMaterial
    private lateinit var complete:UnlitMaterial
    private lateinit var physics:PhysicsMaterialResource
    private lateinit var selection:Entity
    private lateinit var workGlow:Entity
    private lateinit var wrist:Entity
    private lateinit var turtle:Entity
    private fun <T:Resource> keep(r:T):T{resources+=r;return r}
    private fun primitive(parent:Entity,name:String,mesh:MeshResource,m:Material,p:V3,scale:V3):Entity {
        val e=group(name);parent.addChild(e);e.components.set(ModelComponent(mesh,m));transform(e).setPosition(p.sdk());transform(e).setScaleVector(scale.sdk());return e
    }
    suspend fun load(add:(Entity)->Unit,attachment:()->Entity?,panelScale:Float){
        root.addChild(placementOwner);placementOwner.addChild(fittedSceneRoot);fittedSceneRoot.addChild(ocean);fittedSceneRoot.addChild(aperture);root.addChild(consoleRoot);add(root)
        fittedSceneRoot.enabled=false;consoleRoot.enabled=false
        this.attachment=attachment;this.panelScale=panelScale;ensurePanel()
        ocean.components.set(PortalWorldComponent())
        // SDK 6.1.9 public API is createPlane; the portal guide's older createPanel example is stale.
        val portalMesh=keep(MeshResource.createPlane(1.5f,.9f,.025f));val portalMaterial=keep(PortalMaterial())
        aperture.components.set(ModelComponent(portalMesh,portalMaterial));aperture.components.set(PortalComponent(ocean,true,false,true,false))
        ball=keep(MeshResource.createSphere(.5f));cylinder=keep(MeshResource.createCylinder(1f,1f));physics=keep(PhysicsMaterialResource())
        gold=keep(PhysicallyBasedMaterial.create()).apply{setBaseColor(Color4(.56f,.33f,.095f,1f));setRoughness(.86f)}
        cyan=keep(UnlitMaterial.create()).apply{setBaseColor(Color4(.56f,.80f,.75f,1f))}
        complete=keep(UnlitMaterial.create()).apply{setBaseColor(Color4(.38f,.62f,.32f,1f))}
        val ibl=withContext(Dispatchers.IO){TextureResource.load("environment/marine-ibl.ktx",option=TextureCreateOption().apply{textureEncoding=TextureEncoding.LINEAR})};keep(ibl)
        ocean.components.set(StageEnvironmentLightingComponent(ImageBasedLightSource.Single(ibl),1.2f))
        val light=group("UnderwaterSoftKey");ocean.addChild(light);light.components.set(DirectionalLightComponent(Color4(.73f,.91f,1f,1f),420f,true));transform(light).setEulerAngles(EulerAngles(-50f,-28f,0f))
        for(n in listOf("frame","reef","turtle","harness","beam","arm_base","arm_link","lamp","cutter","cutter_jaw","clamp","float_bag","console","handle","beacon","lens","cell","cage","cage_cradle","weight","arch","buoy","route_rock")) {
            val entity=Entity.loadSuspend("asset://models/$n.glb")
            if(disposed){entity.destroy();return}
            templates[n]=entity;yield()
        }
        models["frame"]=clone("frame",fittedSceneRoot)
        clone("reef",ocean)
        models["beam"]=clone("beam",ocean).at(V3(.205,.09,-.66))
        turtle=group("TurtleActor");ocean.addChild(turtle);val hero=clone("turtle",turtle);models["harness"]=clone("harness",turtle)
        val playable=hero.findSkeletonRootEntity(true).ifEmpty{hero.findSkinnedMeshEntity(true)}
        for(e in playable){val anim=e.getAnimationResources().firstOrNull()?:continue;keep(anim);animation+=e.playAnimation(anim).also{it.pause()}}
        check(animation.isNotEmpty()){ "The turtle has no playable skeletal animation" }
        Log.i("DeepSeaRescue","TURTLE_RIG controllers=${animation.size}")
        models["arm_base"]=clone("arm_base",ocean).at(V3(.40,-.30,-.60))
        models["link0"]=clone("arm_link",ocean);models["link1"]=clone("arm_link",ocean)
        wrist=group("WristTranslationOnly");ocean.addChild(wrist)
        models["lamp"]=clone("lamp",wrist)
        val inspectionLight=group("InspectionSpot");models.getValue("lamp").addChild(inspectionLight)
        transform(inspectionLight).setPosition(Vector3(0f,0f,-.045f))
        inspectionLight.components.set(SpotLightComponent(Color4(1f,.94f,.76f,1f),35f,1.2f,16f,27f,false))
        models["cutter"]=clone("cutter",wrist)
        models["jaw0"]=clone("cutter_jaw",models.getValue("cutter"))
        models["jaw1"]=clone("cutter_jaw",models.getValue("cutter"))
        models["clamp"]=clone("clamp",wrist)
        models["brace"]=clone("clamp",ocean).at(RescueGeometry.targets.getValue("brace-point"))
        val support=primitive(ocean,"ClampSupport",cylinder,gold,V3(),V3(1.0,1.0,1.0));models["support"]=support
        segment(support,RescueGeometry.targets.getValue("brace-point"),V3(.205,-.13,-.61),.011)
        models["float_bag"]=clone("float_bag",ocean)
        models["console"]=clone("console",consoleRoot)
        models["handle"]=clone("handle",consoleRoot).also{it.setName("input.handle");transform(it).setPosition(V3(0.0,.06,.018).sdk());hit(it,V3(.09,.11,.09),V3(0.0,.04,0.0))}
        for((id,p) in RescueGeometry.targets){
            // Collision lives on an unscaled parent. Scaling a tiny visual must never shrink the tap target.
            val e=group("input.$id");ocean.addChild(e);transform(e).setPosition(p.sdk())
            targetVisuals[id]=primitive(e,"TargetMarker.$id",ball,cyan,V3(),V3(.022,.022,.022))
            // Replacing a model's material closes the old one in this SDK, and it cannot be set again: a twin instead.
            doneVisuals[id]=primitive(e,"TargetDone.$id",ball,complete,V3(),V3(.022,.022,.022)).also{it.enabled=false}
            nameTag(RescueGeometry.names[id]?:id).also{e.addChild(it);transform(it).setPosition(Vector3(0f,.05f,0f))}
            hit(e,V3(.11,.11,.11),V3());targets[id]=e
        }
        selection=primitive(ocean,"SelectionRing",ball,cyan,V3(),V3(.035,.035,.035)).also{it.enabled=false}
        workGlow=primitive(ocean,"WorkGlow",ball,keep(UnlitMaterial.create()).apply{setBaseColor(Color4(1f,.9f,.45f,1f))},V3(),V3(.05,.05,.05)).also{it.enabled=false}
        for(i in 0..1)ropes+=(0..15).map{primitive(ocean,"Rope.$i.$it",cylinder,gold,V3(),V3(.004,.01,.004))}
        repeat(18){i->particles+=primitive(ocean,"SuspendedGrain.$i",ball,cyan,V3(),V3(.002,.002,.002))}
        extras["beacon"]=clone("beacon",ocean)
        for(i in 0..2)extras["lens$i"]=clone("lens",extras.getValue("beacon")).at(V3(-.24+i*.24,.03,.025))
        extras["cell"]=clone("cell",extras.getValue("beacon")).at(V3(.32,-.16,.10))
        extras["cage"]=clone("cage",ocean)
        extras["cage_cradle"]=clone("cage_cradle",ocean).at(V3(0.0,-.18,-.48))
        extras["weight"]=clone("weight",extras.getValue("cage"))
        for(i in 0..1){extras["bag$i"]=clone("float_bag",extras.getValue("cage")).at(V3(if(i==0)-.20 else .20,.23,0.0))
            extras["bag-strap$i"]=primitive(extras.getValue("cage"),"LoadBearingStrap.$i",cylinder,gold,V3(),V3(.004,.1,.004))}
        extras["arch"]=clone("arch",ocean).at(V3(0.0,-.15,-1.14))
        for(i in 0..2){extras["buoy$i"]=clone("buoy",ocean);for(j in 0..2)extras["guide$i-$j"]=primitive(ocean,"RouteSegment.$i.$j",cylinder,cyan,V3(),V3(.002,.1,.002))}
        // Obstacles use the same conservative box extents as the production route checks.
        MissionGeometry.blocks.forEachIndexed{i,b->extras["block$i"]=if(i<2)clone("route_rock",ocean).at(b.center).also{transform(it).setScaleVector(Vector3(1f,((b.center.y+.40)/.42).toFloat(),1f))}else group("ExitCrossCurrent").also{e->ocean.addChild(e);transform(e).setPosition(b.center.sdk());for(j in 0..4)primitive(e,"VisibleCurrentStripe.$j",cylinder,cyan,V3(0.0,-.055+j*.027,0.0),V3(.002,.32,.002)).also{transform(it).setEulerAngles(EulerAngles(0f,0f,90f))}}}
        for(i in 0..2)extras["beam$i"]=primitive(ocean,"BeaconLightTravel.$i",cylinder,cyan,V3(),V3(.004,.1,.004))
        for((id,point) in MissionGeometry.targets){val e=group("input.$id");ocean.addChild(e);transform(e).setPosition(point.sdk());primitive(e,"Marker.$id",ball,cyan,V3(),V3(.025,.025,.025));nameTag(MissionGeometry.names[id]?:id).also{e.addChild(it);transform(it).setPosition(Vector3(0f,.05f,0f))};hit(e,V3(.11,.11,.11),V3());extraTargets[id]=e}
        ready=true;Log.i("DeepSeaRescue","ASSETS_READY models=${templates.size}; realNativeAppearance=unverified")
    }
    /** Steps and buttons call targets by these names, so the scene shows which dot is which. */
    private fun nameTag(text:String):Entity {
        val px=96
        val ink=Paint().apply{isAntiAlias=true;color=0xFF082830.toInt();textSize=px*.70f;typeface=Typeface.DEFAULT_BOLD;textAlign=Paint.Align.CENTER}
        val w=(ink.measureText(text)+px*.8f).toInt()
        val b=Bitmap.createBitmap(w,px,Bitmap.Config.ARGB_8888)
        Canvas(b).apply{drawRoundRect(0f,0f,w.toFloat(),px.toFloat(),px/2f,px/2f,Paint().apply{isAntiAlias=true;color=0xFFD8F4EE.toInt()});drawText(text,w/2f,px*.74f,ink)}
        val texture=keep(TextureResource.create(b));b.recycle()
        val m=keep(UnlitMaterial.create(BlendingMode.MASKED)).apply{setBaseColorTexture(texture);setOpacityThreshold(.3f)}
        val h=.032f;val x=h*w/px/2;val y=h/2
        // A texture made from a bitmap has its top row at v = 1.
        val mesh=keep(MeshResource.createWithMeshModel(MeshModel(listOf(Vector3(-x,-y,0f),Vector3(x,-y,0f),Vector3(x,y,0f),Vector3(-x,y,0f)),listOf(0,1,2,0,2,3),List(4){Vector3(0f,0f,1f)},List(4){com.pico.spatial.core.math.Vector4(1f,0f,0f,1f)},listOf(com.pico.spatial.core.math.Vector2(0f,0f),com.pico.spatial.core.math.Vector2(1f,0f),com.pico.spatial.core.math.Vector2(1f,1f),com.pico.spatial.core.math.Vector2(0f,1f))),com.pico.spatial.core.ecs.BoundingBox(Vector3(-x,-y,-.001f),Vector3(x,y,.001f)),"tag$text"))
        return group("tag$text").also{it.components.set(ModelComponent(mesh,m))}
    }
    fun debugView(v:String){
        val a=v.split(',').map{it.toFloatOrNull()?:0f}
        if(a.size<6)return
        transform(root).setPosition(Vector3(a[0],a[1],a[2]));transform(root).setEulerAngles(EulerAngles(a[5],a[3],0f));transform(root).setScaleVector(Vector3(a[4]))
    }
    private fun clone(n:String,parent:Entity):Entity {
        val e=requireNotNull(templates.getValue(n).clone(Entity.CloneOptions(true,true)));parent.addChild(e);e.enabled=true;return e
    }
    private fun ensurePanel(){
        if(controls==null){controls=attachment?.invoke();controls?.let{root.addChild(it);it.enabled=false}}
        controls?.let{e->transform(e).setScaleVector(Vector3(panelScale));if(panelPositioned){transform(e).setPosition(panelPosition.sdk());transform(e).setEulerAngles(EulerAngles(0f,panelYaw,0f));e.enabled=true}}
    }
    /** After "reposition" the panel was put away and has to be shown again where the player is looking. */
    fun needsMenu()=mount==null&&!panelPositioned
    fun alignMenu(eye:V3,forward:V3){if(mount!=null)return
        val f=V3(forward.x,0.0,forward.z).normalized();panelPosition=eye+f*1.4;panelYaw=(atan2(-f.x,-f.z)*180/PI).toFloat();panelPositioned=true;ensurePanel()}
    fun place(m:Mount,previewOnly:Boolean){if(!ready)return
        if(m!=mount){
            mount=m;val yaw=atan2(m.normal.x,m.normal.z)*180/PI
            transform(placementOwner).setPosition(m.center.sdk());transform(placementOwner).setEulerAngles(EulerAngles(0f,yaw.toFloat(),0f));transform(fittedSceneRoot).setScaleVector(Vector3(1f))
            val f=m.forward;val right=f.cross(V3(0.0,1.0,0.0)).normalized()
            val console=m.console?:m.eye+f*.72+V3(0.0,-.45,0.0)
            transform(consoleRoot).setPosition(console.sdk());transform(consoleRoot).setEulerAngles(EulerAngles(0f,(atan2(-f.x,-f.z)*180/PI).toFloat(),0f))
            panelPosition=m.eye+right*1.05+f*1.1+V3(0.0,-.06,0.0)
            val toward=m.eye-panelPosition;panelYaw=(atan2(toward.x,toward.z)*180/PI).toFloat();panelPositioned=true
            val frame=models.getValue("frame").getVisualBounds(fittedSceneRoot,true,true)
            val body=turtle.getVisualBounds(turtle,true,true)
            Log.i("DeepSeaRescue","PLACED mode=${if(m.practice)"practice"else"real-wall"} frameMin=${frame.min} frameMax=${frame.max} turtleMin=${body.min} turtleMax=${body.max}")
        }
        fittedSceneRoot.enabled=true;ocean.enabled=!previewOnly;aperture.enabled=!previewOnly;consoleRoot.enabled=!previewOnly;ensurePanel()
    }
    fun unplace(){fittedSceneRoot.enabled=false;consoleRoot.enabled=false;if(mount!=null){mount=null;panelPositioned=false;controls?.enabled=false}}
    fun render(s:RescueUiState){if(!ready)return;ensurePanel()
        val m=s.preview?:s.mount
        if(m==null){unplace();return}
        place(m,s.mount==null)
        val p=s.progress
        val frameDt=if(visualTime<0||p.elapsed<visualTime)0.0 else (p.elapsed-visualTime).coerceIn(0.0,.1)
        if(visualTime<0||p.elapsed<visualTime)displayedAir=p.air.toDouble() else displayedAir+=(p.air-displayedAir)*(frameDt*5).coerceAtMost(1.0)
        val swim=if(p.phase in listOf(RescuePhase.FAREWELL,RescuePhase.RESULT))(p.farewell/7.5).coerceIn(0.0,1.0)else 0.0
        val turtlePosition=p.turtle+V3(swim*.14,sin(swim*PI)*.003,-swim*.20)
        transform(turtle).setPosition(turtlePosition.sdk())
        transform(wrist).setPosition(p.tool.sdk())
        models.getValue("lamp").enabled=p.phase==RescuePhase.INSPECT
        models.getValue("cutter").enabled=p.phase==RescuePhase.CUT
        models.getValue("clamp").enabled=p.phase in listOf(RescuePhase.BRACE,RescuePhase.BAG)
        models.getValue("brace").enabled=true
        transform(models.getValue("brace")).setScaleVector(Vector3(if(p.braced)1f else 1.15f,1f,1f))
        transform(models.getValue("beam")).setEulerAngles(EulerAngles(0f,0f,if(p.braced)0f else (.8*sin(p.elapsed*1.4)).toFloat()))
        models.getValue("float_bag").apply{enabled=p.attached&&p.phase!=RescuePhase.RESULT;transform(this).setPosition((turtlePosition+V3(.01,.20,0.0)).sdk());val f=.55+displayedAir*.15;transform(this).setScaleVector(Vector3(f.toFloat()))}
        val jaw=if(s.acting)(20*(.5+.5*sin(p.elapsed*8))).toFloat()else 23f
        transform(models.getValue("jaw0")).setEulerAngles(EulerAngles(0f,jaw,0f));transform(models.getValue("jaw1")).setEulerAngles(EulerAngles(0f,180f-jaw,0f))
        val shoulder=RescueGeometry.armBase;val delta=p.tool-shoulder;val d=delta.length().coerceIn(.001,.758);val dir=delta.normalized();val up=(V3(0.0,1.0,0.0)-dir*dir.y).normalized();val elbow=shoulder+dir*(d/2)+up*sqrt((.38*.38-d*d/4).coerceAtLeast(0.0))
        link(models.getValue("link0"),shoulder,elbow);link(models.getValue("link1"),elbow,p.tool)
        for((id,e) in targets){e.enabled=RescueGeometry.targetAllowed(p.phase,id)
            val v=targetVisuals.getValue(id);val scale=if(id==s.selected).030f else .022f
            transform(v).setScaleVector(Vector3(scale))
            val done=p.phase==RescuePhase.INSPECT&&id in p.inspected
            if(done!=(id in completedMarkers)){v.enabled=!done;doneVisuals.getValue(id).enabled=done;if(done)completedMarkers+=id else completedMarkers-=id}
        }
        selection.enabled=s.selected!=null&&p.phase in listOf(RescuePhase.INSPECT,RescuePhase.BRACE,RescuePhase.CUT,RescuePhase.BAG)
        s.selected?.let{RescueGeometry.targets[it]?.let{point->transform(selection).setPosition((point+V3(0.0,.045,0.0)).sdk())}}
        if(oldCuts==null){for(i in 0 until p.cuts)cutAt[i]=p.elapsed-3;oldCuts=p.cuts}
        if(p.cuts<(oldCuts?:0))cutAt.clear()
        for(i in (oldCuts?:0) until p.cuts)cutAt[i]=p.elapsed
        oldCuts=p.cuts
        ropes.forEachIndexed{i,segments->
            val a=RescueGeometry.targets.getValue(if(i==0)"front-rope"else"rear-rope");val b=V3(.205,-.13+i*.08,-.61)
            val fall=cutAt[i]?.let{(p.elapsed-it).coerceAtLeast(0.0)}?:0.0
            for(j in segments.indices){val e=segments[j];e.enabled=fall<1.4
                fun point(t:Double)=a*(1-t)+b*t+V3(0.0,-sin(PI*t)*.045-fall*fall*.18,0.0)
                segment(e,point(j/16.0),point((j+1)/16.0),.004)
            }
        }
        renderMissions(s,frameDt)
        if(p.elapsed!=visualTime){visualTime=p.elapsed
            animation.forEach{if(it.valid)it.setTime(((p.elapsed*.7)%4.5).toFloat())}
            particles.forEachIndexed{i,e->transform(e).setPosition(V3(-.66+((i*37%130)/100.0+p.elapsed*.002)%1.30,-.37+((p.elapsed*.014+i*.041)%.75),-.20-(i*13%90)/100.0).sdk())}
        }
    }
    private fun renderMissions(s:RescueUiState,dt:Double){
        val p=s.progress;val w=p.work;val mission=p.campaign.mission;val first=mission==0
        for(k in listOf("beam","brace","support","harness"))models.getValue(k).enabled=first
        if(!first){ropes.flatten().forEach{it.enabled=false};models.getValue("float_bag").enabled=false
            models.getValue("lamp").enabled=p.phase in listOf(RescuePhase.BEACON_SURVEY,RescuePhase.BALANCE_SURVEY)
            models.getValue("clamp").enabled=p.phase in listOf(RescuePhase.BEACON_POWER,RescuePhase.BEACON_ALIGN,RescuePhase.BALANCE_ATTACH)
            val finalSwim=if(mission==3)(p.farewell/8).coerceIn(0.0,1.0)else 0.0
            val actor=if(mission==3)w.escort+V3(-.07*finalSwim,0.0,-.04*finalSwim)else V3(-.46,-.24,-1.02)
            transform(turtle).setPosition(actor.sdk());transform(turtle).setScaleVector(Vector3(.35f))
        }else transform(turtle).setScaleVector(Vector3(1f))
        extras.values.forEach{it.enabled=false}
        val showBeacon=mission==1||(mission>=2&&1 in p.campaign.completed)
        extras.getValue("beacon").apply{enabled=showBeacon;transform(this).setPosition(if(mission==1)V3(0.0,0.0,-.48).sdk()else V3(-.43,-.25,-1.04).sdk());transform(this).setScaleVector(Vector3(if(mission==1)1f else .38f))}
        for(i in 0..2){val target=(if(mission==1)w.dials[i]else listOf(1,3,2)[i])*90.0
            if(lastMission!=mission)dialAngles[i]=target else {var diff=(target-dialAngles[i]+540)%360-180;dialAngles[i]+=diff.coerceIn(-180*dt,180*dt)}
            extras.getValue("lens$i").apply{enabled=showBeacon;transform(this).setEulerAngles(EulerAngles(0f,0f,-dialAngles[i].toFloat()))}
            val beam=extras.getValue("beam$i");beam.enabled=showBeacon&&(mission!=1||w.beamTime>i)
            val a=if(mission==1)V3(-.24+i*.24,.03,-.43)else V3(-.5+i*.09,-.24,-.98)
            val b=a+V3(.22*(if(mission==1)(w.beamTime-i).coerceIn(0.0,1.0)else .38),0.0,0.0)
            segment(beam,a,b,.003)
        }
        extras.getValue("cell").enabled=showBeacon
        val cage=extras.getValue("cage");cage.enabled=mission==2||mission==3
        extras.getValue("cage_cradle").enabled=mission==2
        transform(cage).setPosition(if(mission==2)V3(w.cageOffset,-.18+w.cageHeight,-.48).sdk()else V3(.48,.06,-.82).sdk())
        val roll=if(mission==2&&w.cageHeight>.01)-MissionGeometry.roll(w)else 0.0
        cageRollShown=if(lastMission!=mission)roll else cageRollShown+(roll-cageRollShown).coerceIn(-45*dt,45*dt)
        transform(cage).setScaleVector(Vector3(if(mission==2)1f else .40f));transform(cage).setEulerAngles(EulerAngles(0f,0f,cageRollShown.toFloat()))
        extras.getValue("weight").apply{enabled=cage.enabled;transform(this).setPosition(V3(if(w.heavyRight).18 else -.18,-.02,.05).sdk())}
        for(i in 0..1){val air=if(mission==3)(if(i==0)2 else 3)else if(i==0)w.leftAir else w.rightAir
            bagAirShown[i]=if(lastMission!=mission)air.toDouble()else bagAirShown[i]+(air-bagAirShown[i]).coerceIn(-3*dt,3*dt)
            val scale=.5+bagAirShown[i]*.15
            val shown=mission==3||(mission==2&&(if(i==0)"anchor-left"else"anchor-right") in w.bags)
            extras.getValue("bag$i").apply{enabled=shown;transform(this).setScaleVector(Vector3(scale.toFloat()))}
            extras.getValue("bag-strap$i").apply{enabled=shown;val x=if(i==0)-.20 else .20;segment(this,V3(x,.105,0.0),V3(x,.23-.10*scale,0.0),.004)}
        }
        extras.getValue("arch").enabled=mission==3
        for(i in 0..2){val node=MissionGeometry.node(i,w.lanes[i]);extras.getValue("buoy$i").apply{enabled=mission==3;transform(this).setPosition(node.sdk())}
            extras.getValue("block$i").enabled=mission==3
            var from=if(i==0)MissionGeometry.escortStart else MissionGeometry.node(i-1,w.lanes[i-1])
            val path=MissionGeometry.path(w.copy(segment=i,checkpoint=from))
            for(j in 0..2){extras.getValue("guide$i-$j").apply{enabled=mission==3;segment(this,from,path[j],.0015)};from=path[j]}
        }
        for((id,e) in extraTargets){e.enabled=MissionGeometry.allowed(p.phase,id);val point=MissionGeometry.targets.getValue(id).let{if(id=="load-weight"&&!w.heavyRight)it.copy(x=-it.x)else it};transform(e).setPosition(point.sdk())
            if(id==s.selected){selection.enabled=e.enabled;transform(selection).setPosition((point+V3(0.0,.045,0.0)).sdk())}}
        // The moment a step is done has to show: the lamp is on all the time, so this is what the player sees change.
        val working=s.selected?.takeIf{s.acting}?.let{id->RescueGeometry.targets[id]?:MissionGeometry.targets[id]?.let{t->if(id=="load-weight"&&!w.heavyRight)t.copy(x=-t.x)else t}}
        workGlow.enabled=working!=null
        if(working!=null){transform(workGlow).setPosition(working.sdk());val g=(.045+.02*sin(p.elapsed*14)).toFloat();transform(workGlow).setScaleVector(Vector3(g))}
        lastMission=mission
    }
    private fun link(e:Entity,a:V3,b:V3){val d=b-a;transform(e).setPosition(a.sdk());orient(e,d);transform(e).setScaleVector(Vector3(1f,(d.length()/.38).toFloat(),1f))}
    private fun segment(e:Entity,a:V3,b:V3,r:Double){val d=b-a;transform(e).setPosition(((a+b)*.5).sdk());orient(e,d);transform(e).setScaleVector(Vector3(r.toFloat(),d.length().toFloat(),r.toFloat()))}
    private fun orient(e:Entity,d:V3){val n=d.normalized();val yaw=atan2(-n.z,n.x)*180/PI;val z=-acos(n.y.coerceIn(-1.0,1.0))*180/PI;transform(e).setEulerAngles(EulerAngles(0f,yaw.toFloat(),z.toFloat()))}
    fun deltaFromRoot(d:V3):V3 {val a=fittedSceneRoot.convertPositionFrom(d.sdk(),root);val b=fittedSceneRoot.convertPositionFrom(Vector3.ZERO,root);return V3((a.x-b.x).toDouble(),(a.y-b.y).toDouble(),(a.z-b.z).toDouble())}
    fun target(entity:Entity?):String?{var e=entity;repeat(16){val a=e?:return null;val n=a.getName();if(n.startsWith("input."))return n.substringAfter("input.");e=a.getParent()};return null}
    private fun hit(e:Entity,size:V3,center:V3){val base=keep(ShapeResource.createBox(size.sdk()));val shifted=base.offsetByTranslation(center.sdk());if(shifted!==base)keep(shifted);e.components.set(InteractableComponent());e.components.set(HoverEffectComponent());e.components.set(CollisionComponent(collisionShape=listOf(shifted),physicsMaterial=physics))}
    fun release(){if(disposed)return;disposed=true;ready=false;animation.forEach{runCatching{it.stop();it.close()}};animation.clear();controls?.let{if(it.getParent()==root)it.removeFromParent()};controls=null;root.destroy();templates.values.forEach{it.destroy()};templates.clear();resources.forEach{runCatching{it.close()}};resources.clear()}
    companion object {
        private fun group(name:String)=Entity().apply{setName(name);components.set(TransformComponent())}
        private fun transform(e:Entity)=e.components[TransformComponent::class.java]?:TransformComponent().also{e.components.set(it)}
        private fun Entity.at(v:V3)=apply{transform(this).setPosition(v.sdk())}
    }
}
fun V3.sdk()=Vector3(x.toFloat(),y.toFloat(),z.toFloat())
