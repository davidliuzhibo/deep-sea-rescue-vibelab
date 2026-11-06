package com.vrgames.deeprescue.domain.model

enum class RescuePhase(val id:String) { INSPECT("inspect"), BRACE("brace"), CUT("cut"), BAG("bag"), WINCH("winch"), FAREWELL("farewell"), RESULT("result"), BEACON_SURVEY("beacon-survey"), BEACON_POWER("beacon-power"), BEACON_ALIGN("beacon-align"), BEACON_TEST("beacon-test"), BALANCE_SURVEY("balance-survey"), BALANCE_ATTACH("balance-attach"), BALANCE_LIFT("balance-lift"), ESCORT_PLAN("escort-plan"), OCEAN_FAREWELL("ocean-farewell") }
data class AudioSettings(val music:Boolean=true,val sfx:Boolean=true)
data class RescueProgress(
    val phase:RescuePhase=RescuePhase.INSPECT,
    val inspected:Set<String> = emptySet(),val braced:Boolean=false,val cuts:Int=0,
    val attached:Boolean=false,val air:Int=0,val turtle:V3=V3(-.10,-.16,-.68),
    val tool:V3=V3(.48,-.22,-.28),val elapsed:Double=0.0,val errors:Int=0,
    val farewell:Double=0.0,val settings:AudioSettings=AudioSettings(),
    val campaign:CampaignProgress=CampaignProgress(),val work:MissionWork=MissionWork()
)
object RescueGeometry {
    val armBase=V3(.40,-.30,-.60)
    val armReach=.75
    val home=V3(.48,-.22,-.28)
    val targets=linkedMapOf("front-rope" to V3(-.28,-.10,-.40),"rear-rope" to V3(.10,-.12,-.40),"brace-point" to V3(.28,-.23,-.44),"float-hook" to V3(-.10,.005,-.64))
    val names=mapOf("front-rope" to "前鳍绳","rear-rope" to "尾部绳","brace-point" to "黄色夹座","float-hook" to "龟背上的背带扣")
    fun targetAllowed(phase:RescuePhase,id:String)=when(phase){RescuePhase.INSPECT->id in listOf("front-rope","rear-rope","brace-point");RescuePhase.BRACE->id=="brace-point";RescuePhase.CUT->id in listOf("front-rope","rear-rope");RescuePhase.BAG->id=="float-hook";else->false}
    fun insideTool(p:V3)=p.finite()&&p.x in -.58.. .60&&p.y in -.32.. .32&&p.z in -.95..-.12&&(p-armBase).length()<=armReach
    fun bodyContact(p:V3,turtle:V3):Boolean {val d=p-turtle;return d.x*d.x/(.23*.23)+d.y*d.y/(.095*.095)+d.z*d.z/(.18*.18)<1.0}
    // Independent beam volume, expanded by the turtle body and flipper envelope in X/Y.
    fun beamContact(p:V3)=p.x+.28>.13&&p.x-.36<.28&&p.y+.10>-.08&&p.y-.12<.26&&p.z+.33>-.80&&p.z-.33<-.52
    // Clear of the beam and up in open water. The window was 4 x 1 cm with a current pushing out of it: a player saw no way to hit it.
    fun safe(p:V3)=p.x in -.30..-.20&&p.y>=.15&&p.z in -.90..-.50
}
