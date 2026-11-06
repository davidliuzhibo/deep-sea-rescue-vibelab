package com.vrgames.deeprescue.domain

import com.vrgames.deeprescue.domain.model.EyePose
import com.vrgames.deeprescue.domain.model.Mount
import com.vrgames.deeprescue.domain.model.RoomPlane
import com.vrgames.deeprescue.domain.model.SurfaceKind
import com.vrgames.deeprescue.domain.model.V3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.PI

object RoomPlacement {
    /** Samples the full footprint as well as all four corners; holes in the middle are rejected. */
    fun covered(p:RoomPlane,c:V3,right:V3,up:V3,w:Double,h:Double):Boolean =
        (0..16).all{x->(0..10).all{y->p.covers(c+right*((x/16.0-.5)*w)+up*((y/10.0-.5)*h))}}
    fun walls(eye:EyePose?,planes:List<RoomPlane>,now:Long):List<Mount> {
        if(eye==null||!eye.fresh(now)||eye.flatForward().length()<.9)return emptyList()
        val f=eye.flatForward()
        return planes.mapNotNull{p->
            if(!p.valid()||p.kind!=SurfaceKind.WALL||abs(p.normal.y)>.15)return@mapNotNull null
            val anchor=p.triangles.first().a
            var n=V3(p.normal.x,0.0,p.normal.z).normalized()
            if((eye.eye-anchor).dot(n)<0)n=n*(-1.0)
            if(n.dot(f*(-1.0))<cos(20*PI/180))return@mapNotNull null
            val d=(anchor-eye.eye).dot(n)/f.dot(n)
            if(d !in 1.3..2.2)return@mapNotNull null
            val c=eye.eye+f*d+V3(0.0,-.02,0.0)
            val right=V3(0.0,1.0,0.0).cross(n).normalized()
            if(!covered(p,c,right,V3(0.0,1.0,0.0),1.62,1.02))return@mapNotNull null
            // Move only 8 mm in front of measured wall to avoid z fighting, not a fake distance change.
            Mount(eye.eye,f,c+n*.008,n,p.id)
        }.sortedBy{(it.center-eye.eye).length()}
    }
    fun practice(eye:EyePose?,now:Long):Mount? {
        if(eye==null||!eye.fresh(now)||eye.flatForward().length()<.9)return null
        val f=eye.flatForward()
        return Mount(eye.eye,f,eye.eye+f*1.6+V3(0.0,-.02,0.0),f*(-1.0))
    }
    fun tables(m:Mount,planes:List<RoomPlane>,floorY:Double?):List<Mount> {
        if(floorY==null)return emptyList()
        val f=m.forward;val right=f.cross(V3(0.0,1.0,0.0)).normalized()
        return planes.filter{it.valid()&&it.kind==SurfaceKind.UP&&it.normal.y>.92}.mapNotNull{p->
            val y=p.triangles.first().a.y
            if(y-floorY<.35||y !in (m.eye.y-.80)..(m.eye.y-.24))return@mapNotNull null
            val c=m.eye+f*.72+V3(0.0,y-m.eye.y,0.0)
            if(!covered(p,c,right,f,.48,.32))return@mapNotNull null
            m.copy(console=c+V3(0.0,.09,0.0),tableId=p.id)
        }
    }
    fun valid(m:Mount,eye:EyePose?,planes:List<RoomPlane>,now:Long):Boolean {
        if(eye==null||!eye.fresh(now))return false
        if(m.practice)return true
        val p=planes.firstOrNull{it.id==m.planeId}?:return false
        if(!p.valid()||p.normal.dot(m.normal).let{abs(it)}<.98)return false
        if(!covered(p,m.center-m.normal*.008,m.right,V3(0.0,1.0,0.0),1.62,1.02))return false
        if(m.tableId!=null){val table=planes.firstOrNull{it.id==m.tableId}?:return false
            if(!covered(table,m.console!!-V3(0.0,.09,0.0),m.right,m.forward,.48,.32))return false}
        return true
    }
}
