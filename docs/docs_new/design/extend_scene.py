from pathlib import Path
R=Path(__file__).resolve().parents[2];p=R/'demos/deep-sea-rescue/app/src/main/java/com/example/deepsearescue/pcd0b39b0/spatial/RescueScene.kt'
s=p.read_text(encoding='utf8').replace('import com.example.deepsearescue.pcd0b39b0.domain.model.Mount','import com.example.deepsearescue.pcd0b39b0.domain.model.MissionGeometry\nimport com.example.deepsearescue.pcd0b39b0.domain.model.Mount')
s=s.replace('private val particles=','private val dialAngles=DoubleArray(3)\n    private var lastMission=-1\n    private val extras=linkedMapOf<String,Entity>()\n    private val extraTargets=linkedMapOf<String,Entity>()\n    private val particles=')
s=s.replace('"float_bag","console","handle"','"float_bag","console","handle","beacon","lens","cell","cage","weight","arch","buoy"')
s=s.replace('clone("harness",turtle)','models["harness"]=clone("harness",turtle)')
s=s.replace('val support=primitive(ocean,"ClampSupport",cylinder,gold,V3(),V3(1.0,1.0,1.0))','val support=primitive(ocean,"ClampSupport",cylinder,gold,V3(),V3(1.0,1.0,1.0));models["support"]=support')
s=s.replace('        ready=true;Log.i', '''        extras["beacon"]=clone("beacon",ocean)
        for(i in 0..2)extras["lens$i"]=clone("lens",extras.getValue("beacon")).at(V3(-.24+i*.24,.03,.025))
        extras["cell"]=clone("cell",extras.getValue("beacon")).at(V3(.32,-.16,.10))
        extras["cage"]=clone("cage",ocean)
        extras["weight"]=clone("weight",extras.getValue("cage"))
        for(i in 0..1)extras["bag$i"]=clone("float_bag",extras.getValue("cage")).at(V3(if(i==0)-.20 else .20,.23,0.0))
        extras["arch"]=clone("arch",ocean).at(V3(0.0,-.15,-1.14))
        for(i in 0..2){extras["buoy$i"]=clone("buoy",ocean);extras["guide$i"]=primitive(ocean,"RouteSegment.$i",cylinder,cyan,V3(),V3(.002,.1,.002))}
        // Obstacles use the same conservative box extents as the production route checks.
        MissionGeometry.blocks.forEachIndexed{i,b->extras["block$i"]=primitive(ocean,"RouteObstacle.$i",MeshResource.createBox((b.half*2.0).sdk()).also{keep(it)},if(i==2)cyan else gold,b.center,V3(1.0,1.0,1.0))}
        for(i in 0..2)extras["beam$i"]=primitive(ocean,"BeaconLightTravel.$i",cylinder,cyan,V3(),V3(.004,.1,.004))
        for((id,point) in MissionGeometry.targets){val e=group("input.$id");ocean.addChild(e);transform(e).setPosition(point.sdk());primitive(e,"Marker.$id",ball,cyan,V3(),V3(.025,.025,.025));hit(e,V3(.11,.11,.11),V3());extraTargets[id]=e}
        ready=true;Log.i''')
s=s.replace('        if(p.elapsed!=visualTime){visualTime=p.elapsed','        renderMissions(s,frameDt)\n        if(p.elapsed!=visualTime){visualTime=p.elapsed')
before='    private fun link(e:Entity,a:V3,b:V3)'
insert='''    private fun renderMissions(s:RescueUiState,dt:Double){
        val p=s.progress;val w=p.work;val mission=p.campaign.mission;val first=mission==0
        for(k in listOf("beam","brace","support","harness"))models.getValue(k).enabled=first
        if(!first){ropes.flatten().forEach{it.enabled=false};models.getValue("float_bag").enabled=false
            models.getValue("lamp").enabled=p.phase in listOf(RescuePhase.BEACON_SURVEY,RescuePhase.BALANCE_SURVEY)
            models.getValue("clamp").enabled=p.phase in listOf(RescuePhase.BEACON_POWER,RescuePhase.BEACON_ALIGN,RescuePhase.BALANCE_ATTACH)
            val finalSwim=if(mission==3)(p.farewell/8).coerceIn(0.0,1.0)else 0.0
            val actor=if(mission==3)w.escort+V3(-.07*finalSwim,0.0,-.06*finalSwim)else V3(-.46,-.24,-1.02)
            transform(turtle).setPosition(actor.sdk());transform(turtle).setScaleVector(Vector3(.35f))
        }else transform(turtle).setScaleVector(Vector3(1f))
        extras.values.forEach{it.enabled=false}
        val showBeacon=mission==1||(mission>=2&&1 in p.campaign.completed)
        extras.getValue("beacon").apply{enabled=showBeacon;transform(this).setPosition(if(mission==1)V3(0.0,0.0,-.48).sdk()else V3(-.43,.19,-1.04).sdk());transform(this).setScaleVector(Vector3(if(mission==1)1f else .38f))}
        for(i in 0..2){val target=(if(mission==1)w.dials[i]else listOf(1,3,2)[i])*90.0
            if(lastMission!=mission)dialAngles[i]=target else {var diff=(target-dialAngles[i]+540)%360-180;dialAngles[i]+=diff.coerceIn(-180*dt,180*dt)}
            extras.getValue("lens$i").apply{enabled=showBeacon;transform(this).setEulerAngles(EulerAngles(0f,0f,-dialAngles[i].toFloat()))}
            val beam=extras.getValue("beam$i");beam.enabled=showBeacon&&(mission!=1||w.beamTime>i)
            val a=if(mission==1)V3(-.24+i*.24,.03,-.43)else V3(-.5+i*.09,.20,-.98)
            val b=a+V3(.22*(if(mission==1)(w.beamTime-i).coerceIn(0.0,1.0)else .38),0.0,0.0)
            segment(beam,a,b,.003)
        }
        extras.getValue("cell").enabled=showBeacon
        val cage=extras.getValue("cage");cage.enabled=mission==2||mission==3
        transform(cage).setPosition(if(mission==2)V3(w.cageOffset,-.18+w.cageHeight,-.48).sdk()else V3(.48,.06,-.82).sdk())
        transform(cage).setScaleVector(Vector3(if(mission==2)1f else .40f));transform(cage).setEulerAngles(EulerAngles(0f,0f,if(mission==2)(-MissionGeometry.roll(w)).toFloat()else 0f))
        extras.getValue("weight").apply{enabled=cage.enabled;transform(this).setPosition(V3(if(w.heavyRight).18 else -.18,-.02,.05).sdk())}
        for(i in 0..1){val air=if(i==0)w.leftAir else w.rightAir;extras.getValue("bag$i").apply{enabled=mission==3||(mission==2&&(if(i==0)"anchor-left"else"anchor-right") in w.bags);transform(this).setScaleVector(Vector3((.5+air*.15).toFloat()))}}
        extras.getValue("arch").enabled=mission==3
        for(i in 0..2){val node=MissionGeometry.node(i,w.lanes[i]);extras.getValue("buoy$i").apply{enabled=mission==3;transform(this).setPosition(node.sdk())}
            extras.getValue("block$i").enabled=mission==3
            extras.getValue("guide$i").apply{enabled=mission==3;val from=if(i==0)V3(-.38,-.18,-.46)else MissionGeometry.node(i-1,w.lanes[i-1]);segment(this,from,node,.0015)}
        }
        for((id,e) in extraTargets){e.enabled=MissionGeometry.allowed(p.phase,id);val point=MissionGeometry.targets.getValue(id).let{if(id=="load-weight"&&!w.heavyRight)it.copy(x=-it.x)else it};transform(e).setPosition(point.sdk())
            if(id==s.selected){selection.enabled=e.enabled;transform(selection).setPosition((point+V3(0.0,.045,0.0)).sdk())}}
        lastMission=mission
    }
'''
s=s.replace(before,insert+before)
p.write_bytes(s.encode())
print('full mission rendering integrated')
