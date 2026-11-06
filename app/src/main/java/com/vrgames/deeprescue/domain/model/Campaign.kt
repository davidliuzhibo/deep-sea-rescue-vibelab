package com.vrgames.deeprescue.domain.model

data class CampaignProgress(val mission:Int=0,val completed:Set<Int> = emptySet(),val bestSeconds:Map<Int,Double> = emptyMap(),val bestStops:Map<Int,Int> = emptyMap())
data class MissionWork(
    val surveyed:Set<String> = emptySet(),val isolated:Boolean=false,val cell:Boolean=false,
    val dials:List<Int> = listOf(0,0,0),val beamTime:Double=0.0,
    val bags:Set<String> = emptySet(),val leftAir:Int=0,val rightAir:Int=0,val heavyRight:Boolean=true,
    val cageHeight:Double=0.0,val cageOffset:Double=0.0,val locked:Boolean=false,
    val lanes:List<Int> = listOf(0,0,0),val routeSelected:Int=0,val segment:Int=0,val routeLeg:Int=0,
    val escort:V3=V3(-.38,-.18,-.28),val checkpoint:V3=V3(-.38,-.18,-.28)
)
object MissionGeometry {
    val targets=linkedMapOf("power-port" to V3(.32,-.16,-.38),"lens-left" to V3(-.24,.03,-.48),"lens-mid" to V3(0.0,.03,-.48),"lens-right" to V3(.24,.03,-.48),"anchor-left" to V3(-.20,-.10,-.48),"anchor-right" to V3(.20,-.10,-.48),"load-weight" to V3(.18,-.20,-.43))
    val names=mapOf("power-port" to "蓝色电源扣","lens-left" to "左侧导光轮","lens-mid" to "中间导光轮","lens-right" to "右侧导光轮","anchor-left" to "左侧吊环","anchor-right" to "右侧吊环","load-weight" to "配重块")
    fun allowed(p:RescuePhase,id:String)=when(p){
        RescuePhase.BEACON_SURVEY->id in listOf("power-port","lens-left","lens-mid")
        RescuePhase.BEACON_POWER->id=="power-port"
        RescuePhase.BEACON_ALIGN->id in listOf("lens-left","lens-mid","lens-right")
        RescuePhase.BALANCE_SURVEY->id in listOf("anchor-left","anchor-right","load-weight")
        RescuePhase.BALANCE_ATTACH->id in listOf("anchor-left","anchor-right")
        else->false
    }
    fun roll(w:MissionWork)=((w.rightAir-w.leftAir)-(if(w.heavyRight)1 else -1))*9.0
    fun balanced(w:MissionWork)=kotlin.math.abs(roll(w))<=4&&w.leftAir+w.rightAir in 5..6
    val escortStart=V3(-.38,-.18,-.28)
    // Conservative envelope from all 37 exported skeletal-animation samples at scene scale .35.
    // The origin is asymmetric: do not replace with a smaller centred body-only box.
    val escortMin=V3(-.146,-.098,-.129)
    val escortMax=V3(.084,.053,.089)
    fun node(index:Int,lane:Int)=V3(lane*.28,listOf(.02,.09,.17)[index],listOf(-.46,-.78,-1.10)[index])
    // Visible limestone shelves occupy the two opposing edges; the third volume is visible cross-current.
    data class Block(val center:V3,val half:V3,val label:String,val grounded:Boolean=false)
    val blocks=listOf(Block(V3(.23,.02,-.46),V3(.18,.085,.017),"近处右侧岩石",true),Block(V3(-.23,.09,-.78),V3(.18,.085,.017),"中段左侧岩石",true),Block(V3(.07,.17,-1.10),V3(.17,.07,.015),"出口中央横流"))
    fun blocked(p:V3):String?=blocks.firstOrNull{b->
        p.x+escortMax.x>b.center.x-b.half.x&&p.x+escortMin.x<b.center.x+b.half.x&&
        p.y+escortMax.y>(if(b.grounded)-.42 else b.center.y-b.half.y)&&p.y+escortMin.y<b.center.y+b.half.y&&
        p.z+escortMax.z>b.center.z-b.half.z&&p.z+escortMin.z<b.center.z+b.half.z
    }?.label
    fun path(w:MissionWork):List<V3>{val n=node(w.segment,w.lanes[w.segment]);return listOf(V3(w.checkpoint.x,n.y,n.z+.17),V3(n.x,n.y,n.z+.17),n)}
    fun obstruction(w:MissionWork):String? {var p=w.escort;for(t in path(w).drop(w.routeLeg)){while((p-t).length()>.0001){p=p.moveTo(t,.005);blocked(p)?.let{return it}}};return null}
}
