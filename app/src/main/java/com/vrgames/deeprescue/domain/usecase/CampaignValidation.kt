package com.vrgames.deeprescue.domain.usecase

import com.vrgames.deeprescue.domain.model.RescuePhase as P
import com.vrgames.deeprescue.domain.model.RescueProgress
import com.vrgames.deeprescue.domain.model.MissionGeometry

object CampaignValidation {
    fun valid(p:RescueProgress):Boolean=runCatching {
        val c=p.campaign;val w=p.work
        require(c.mission in 0..3&&c.completed.all{it in 0..3})
        require((c.completed.none{it>0}||0 in c.completed)&&(3 !in c.completed||c.completed.containsAll(listOf(0,1,2))))
        require(CampaignUseCase.unlocked(c,c.mission))
        require(c.bestSeconds.keys==c.completed&&c.bestStops.keys==c.completed)
        require(c.bestSeconds.values.all{it.isFinite()&&it>=0}&&c.bestStops.values.all{it>=0})
        require(w.dials.size==3&&w.dials.all{it in 0..3}&&w.lanes.size==3&&w.lanes.all{it in -1..1})
        require(w.leftAir in 0..4&&w.rightAir in 0..4&&w.beamTime.isFinite()&&w.beamTime in 0.0..3.001)
        require(w.cageHeight.isFinite()&&w.cageHeight in 0.0.. .281&&w.cageOffset.isFinite()&&w.cageOffset in 0.0.. .401)
        if(w.cageHeight>.001||w.cageOffset>.001)require(MissionGeometry.balanced(w))
        require(w.segment in 0..3&&w.routeLeg in 0..2&&w.routeSelected in 0..2&&w.escort.finite()&&w.checkpoint.finite())
        require(listOf(w.escort,w.checkpoint).all{it.x in -.50.. .50&&it.y in -.25.. .30&&it.z in -1.101..-.27})
        require(w.bags.all{it in listOf("anchor-left","anchor-right")}&&w.surveyed.all{it in listOf("power-port","lens-left","lens-mid","anchor-left","anchor-right","load-weight")})
        when(c.mission){
            0->require(p.phase.ordinal<=P.RESULT.ordinal)
            1->{require(p.phase in listOf(P.BEACON_SURVEY,P.BEACON_POWER,P.BEACON_ALIGN,P.BEACON_TEST,P.RESULT));if(p.phase!=P.BEACON_SURVEY)require(w.surveyed==setOf("power-port","lens-left","lens-mid"));if(p.phase in listOf(P.BEACON_ALIGN,P.BEACON_TEST,P.RESULT))require(w.isolated&&w.cell);if(p.phase in listOf(P.BEACON_TEST,P.RESULT))require(w.dials==listOf(1,3,2));if(p.phase==P.RESULT)require(w.beamTime>=3)}
            2->{require(p.phase in listOf(P.BALANCE_SURVEY,P.BALANCE_ATTACH,P.BALANCE_LIFT,P.RESULT));if(p.phase!=P.BALANCE_SURVEY)require(w.surveyed==setOf("anchor-left","anchor-right","load-weight"));if(p.phase in listOf(P.BALANCE_LIFT,P.RESULT))require(w.bags.size==2);if(p.phase==P.RESULT)require(w.locked&&w.cageHeight>=.23&&w.cageOffset>=.32)}
            3->{require(p.phase in listOf(P.ESCORT_PLAN,P.OCEAN_FAREWELL,P.RESULT));if(p.phase!=P.ESCORT_PLAN)require(w.segment==3);if(p.phase==P.RESULT)require(p.farewell>=8)}
        };true
    }.getOrDefault(false)
}
