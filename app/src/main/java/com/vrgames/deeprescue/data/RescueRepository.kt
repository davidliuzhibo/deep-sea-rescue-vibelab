package com.vrgames.deeprescue.data

import com.vrgames.deeprescue.domain.model.RescueGeometry
import com.vrgames.deeprescue.domain.model.RescuePhase
import com.vrgames.deeprescue.domain.model.RescueProgress
import com.vrgames.deeprescue.domain.model.CampaignProgress
import com.vrgames.deeprescue.domain.model.MissionWork
import com.vrgames.deeprescue.domain.usecase.CampaignUseCase
import com.vrgames.deeprescue.domain.usecase.CampaignValidation
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

data class SaveEnvelope(val schema:Int,val payload:String,val digest:String)
data class LoadedRescue(val progress:RescueProgress,val warning:String?=null)
class RescueRepository(private val dir:File) {
    private val gson=Gson()
    private val file get()=File(dir,"deep-sea-rescue-v2.json")
    private val legacy get()=File(dir,"deep-sea-rescue-v1.json")
    fun load():LoadedRescue {
        val input=if(file.exists())file else legacy
        if(!input.exists())return LoadedRescue(RescueProgress())
        return try {
            val envelope=gson.fromJson(input.readText(),SaveEnvelope::class.java)
            require(envelope.schema in 1..2&&digest(envelope.payload)==envelope.digest)
            val tree=JsonParser.parseString(envelope.payload).asJsonObject
            if(envelope.schema==1){tree.add("campaign",gson.toJsonTree(CampaignProgress()));tree.add("work",gson.toJsonTree(MissionWork()))}
            val raw=gson.fromJson(tree,RescueProgress::class.java)
            val p=if(envelope.schema==1)CampaignUseCase.record(raw)else raw
            require(valid(p));LoadedRescue(p)
        }catch(_:Exception){
            // Preserve damaged evidence rather than replacing the user's only save on load.
            input.copyTo(File(dir,"deep-sea-rescue-unreadable-${System.currentTimeMillis()}.json"),false)
            LoadedRescue(RescueProgress(),"之前的存档无法读取，已保留副本。本次从救援起点开始。")
        }
    }
    @Synchronized fun save(p:RescueProgress) {
        require(valid(p));dir.mkdirs()
        val payload=gson.toJson(p);val temp=File(dir,"deep-sea-rescue-v2.tmp")
        temp.outputStream().use{out->out.write(gson.toJson(SaveEnvelope(2,payload,digest(payload))).toByteArray(Charsets.UTF_8));out.fd.sync()}
        try{Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)}
        catch(_:java.nio.file.AtomicMoveNotSupportedException){Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING)}
    }
    companion object {
        fun digest(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
        fun valid(p:RescueProgress):Boolean=runCatching {
            p.phase.id.isNotBlank()&&p.settings.toString().isNotBlank()&&p.tool.finite()&&RescueGeometry.insideTool(p.tool)&&p.turtle.finite()&&
            p.turtle.x in -.47.. .18&&p.turtle.y in -.25.. .34&&p.turtle.z in -.90..-.50&&p.air in 0..3&&p.cuts in 0..2&&
            p.elapsed.isFinite()&&p.elapsed>=0&&p.farewell.isFinite()&&p.farewell in 0.0..8.001&&p.errors>=0&&
            CampaignValidation.valid(p)&&(p.campaign.mission!=0||(p.inspected.all{it in listOf("front-rope","rear-rope","brace-point")}&&
            (p.phase==RescuePhase.INSPECT||p.inspected.size==3)&&
            (p.phase.ordinal<RescuePhase.CUT.ordinal||p.braced)&&
            (p.phase.ordinal<RescuePhase.BAG.ordinal||p.cuts==2)&&
            (p.phase.ordinal<RescuePhase.WINCH.ordinal||p.attached)&&
            (p.phase.ordinal<RescuePhase.FAREWELL.ordinal||RescueGeometry.safe(p.turtle))&&
            (p.phase!=RescuePhase.RESULT||p.farewell>=7.5)))
        }.getOrDefault(false)
    }
}
