package com.vrgames.deeprescue.domain.usecase

import com.vrgames.deeprescue.domain.model.CampaignProgress
import com.vrgames.deeprescue.domain.model.MissionWork
import com.vrgames.deeprescue.domain.model.RescuePhase
import com.vrgames.deeprescue.domain.model.RescueProgress

object CampaignUseCase {
    val titles=listOf("海龟解困","修复引航灯","双浮袋清障","归途护送")
    fun unlocked(c:CampaignProgress,id:Int)=id in 0..3&&when(id){0->true;1,2->0 in c.completed;else->c.completed.containsAll(listOf(0,1,2))}
    fun start(p:RescueProgress,id:Int):RescueProgress? {
        if(!unlocked(p.campaign,id))return null
        return fresh(p,id)
    }
    /** The next level to play: the first one open and not done yet, or null when all four are done. */
    fun next(c:CampaignProgress)=(0..3).firstOrNull{it !in c.completed&&unlocked(c,it)}
    fun fresh(p:RescueProgress,id:Int=p.campaign.mission)=RescueProgress(
        phase=listOf(RescuePhase.INSPECT,RescuePhase.BEACON_SURVEY,RescuePhase.BALANCE_SURVEY,RescuePhase.ESCORT_PLAN)[id],
        settings=p.settings,campaign=p.campaign.copy(mission=id),work=MissionWork(heavyRight=2 !in p.campaign.completed))
    fun record(p:RescueProgress):RescueProgress {
        if(p.phase!=RescuePhase.RESULT)return p
        val c=p.campaign;val i=c.mission
        return p.copy(campaign=c.copy(completed=c.completed+i,bestSeconds=c.bestSeconds+(i to minOf(c.bestSeconds[i]?:Double.MAX_VALUE,p.elapsed)),bestStops=c.bestStops+(i to minOf(c.bestStops[i]?:Int.MAX_VALUE,p.errors))))
    }
}
