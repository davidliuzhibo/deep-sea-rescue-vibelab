package com.vrgames.deeprescue.domain.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** SDK-independent metre coordinates. +Y up; -Z forward. */
data class V3(val x:Double=0.0,val y:Double=0.0,val z:Double=0.0) {
    operator fun plus(v:V3)=V3(x+v.x,y+v.y,z+v.z)
    operator fun minus(v:V3)=V3(x-v.x,y-v.y,z-v.z)
    operator fun times(k:Double)=V3(x*k,y*k,z*k)
    fun dot(v:V3)=x*v.x+y*v.y+z*v.z
    fun cross(v:V3)=V3(y*v.z-z*v.y,z*v.x-x*v.z,x*v.y-y*v.x)
    fun length()=sqrt(dot(this))
    fun normalized():V3=length().let{if(it>1e-8&&it.isFinite())this*(1/it)else V3()}
    fun finite()=x.isFinite()&&y.isFinite()&&z.isFinite()
    fun capped(n:Double)=if(length()>n)normalized()*n else this
    fun yaw(r:Double)=V3(x*cos(r)+z*sin(r),y,-x*sin(r)+z*cos(r))
    fun moveTo(v:V3,d:Double)=this+(v-this).capped(d)
}
data class Triangle(val a:V3,val b:V3,val c:V3) {
    val normal get()=(b-a).cross(c-a).normalized()
    fun contains(p:V3,tolerance:Double=.012):Boolean {
        val u=b-a;val v=c-a;val w=p-a
        if(u.cross(v).length()<1e-9||abs(w.dot(normal))>tolerance)return false
        val d=u.dot(u)*v.dot(v)-u.dot(v)*u.dot(v)
        if(abs(d)<1e-12)return false
        val s=(v.dot(v)*w.dot(u)-u.dot(v)*w.dot(v))/d
        val t=(u.dot(u)*w.dot(v)-u.dot(v)*w.dot(u))/d
        return s>=-1e-6&&t>=-1e-6&&s+t<=1.000001
    }
}
enum class SurfaceKind { WALL, UP, OTHER }
data class RoomPlane(val id:String,val triangles:List<Triangle>,val normal:V3,val kind:SurfaceKind,val at:Long) {
    fun covers(p:V3)=triangles.any{it.contains(p)}
    fun valid()=id.isNotBlank()&&triangles.isNotEmpty()&&normal.finite()&&normal.length()>.9&&triangles.all{it.a.finite()&&it.b.finite()&&it.c.finite()&&abs(it.normal.dot(normal))>.95}
}
data class EyePose(val eye:V3,val forward:V3,val at:Long) {
    fun fresh(now:Long)=eye.finite()&&forward.finite()&&forward.length()>.9&&now-at in 0..250
    fun flatForward()=V3(forward.x,0.0,forward.z).normalized()
}
data class Mount(val eye:V3,val forward:V3,val center:V3,val normal:V3,val planeId:String?=null,val console:V3?=null,val tableId:String?=null) {
    val right get()=V3(0.0,1.0,0.0).cross(normal).normalized()
    fun fromLocal(v:V3)=center+right*v.x+V3(0.0,v.y,0.0)+normal*v.z
    val practice get()=planeId==null
}
