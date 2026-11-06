package com.vrgames.deeprescue.domain

import com.vrgames.deeprescue.domain.model.EyePose
import com.vrgames.deeprescue.domain.model.RoomPlane
import com.vrgames.deeprescue.domain.model.SurfaceKind
import com.vrgames.deeprescue.domain.model.Triangle
import com.vrgames.deeprescue.domain.model.V3
import org.junit.Assert.*
import org.junit.Test

class RoomPlacementTest {
    private val eye=EyePose(V3(0.0,1.6,0.0),V3(0.0,0.0,-1.0),100)
    private fun wall(id:String="wall",distance:Double=1.6,w:Double=2.2,h:Double=1.6):RoomPlane {
        val a=V3(-w/2,1.58-h/2,-distance);val b=V3(w/2,a.y,-distance);val c=V3(w/2,1.58+h/2,-distance);val d=V3(-w/2,c.y,-distance)
        return RoomPlane(id,listOf(Triangle(a,b,c),Triangle(a,c,d)),V3(0.0,0.0,1.0),SurfaceKind.WALL,100)
    }
    private fun floor(y:Double,id:String)=RoomPlane(id,listOf(Triangle(V3(-2.0,y,-2.0),V3(2.0,y,-2.0),V3(2.0,y,1.0)),Triangle(V3(-2.0,y,-2.0),V3(2.0,y,1.0),V3(-2.0,y,1.0))),V3(0.0,1.0,0.0),SurfaceKind.UP,100)
    @Test fun frontWallHasMeasuredCenter(){val m=RoomPlacement.walls(eye,listOf(wall()),100).single();assertEquals(-1.592,m.center.z,.0001);assertEquals(1.58,m.center.y,.0001);assertFalse(m.practice)}
    @Test fun freshHeadRequired(){assertTrue(RoomPlacement.walls(eye,listOf(wall()),351).isEmpty());assertNull(RoomPlacement.practice(null,100))}
    @Test fun futureDatedPoseRejected(){assertTrue(RoomPlacement.walls(eye,listOf(wall()),99).isEmpty())}
    @Test fun narrowWallRejected(){assertTrue(RoomPlacement.walls(eye,listOf(wall(w=1.60)),100).isEmpty())}
    @Test fun shortWallRejected(){assertTrue(RoomPlacement.walls(eye,listOf(wall(h=.95)),100).isEmpty())}
    @Test fun wallBehindRejected(){assertTrue(RoomPlacement.walls(eye,listOf(wall(distance=-1.6)),100).isEmpty())}
    @Test fun distantWallRejected(){assertTrue(RoomPlacement.walls(eye,listOf(wall(distance=3.0)),100).isEmpty())}
    @Test fun closeWallRejected(){assertTrue(RoomPlacement.walls(eye,listOf(wall(distance=.7)),100).isEmpty())}
    @Test fun oppositeMeshNormalCanFaceUser(){val p=wall().copy(normal=V3(0.0,0.0,-1.0));assertEquals(1,RoomPlacement.walls(eye,listOf(p),100).size)}
    @Test fun tiltedWallRejected(){assertTrue(RoomPlacement.walls(eye,listOf(wall().copy(normal=V3(0.0,.7,.7))),100).isEmpty())}
    @Test fun triangleBoundsAreNotCoverage(){val p=wall();val t=p.triangles.first();assertFalse(RoomPlacement.covered(p.copy(triangles=listOf(t)),V3(0.0,1.58,-1.6),V3(1.0,0.0,0.0),V3(0.0,1.0,0.0),1.62,1.02))}
    @Test fun trackingLostInvalidatesSelectedPlane(){val m=RoomPlacement.walls(eye,listOf(wall()),100).single();assertFalse(RoomPlacement.valid(m,eye,listOf(wall("other")),100))}
    @Test fun geometryShiftInvalidatesPlacement(){val m=RoomPlacement.walls(eye,listOf(wall()),100).single();assertFalse(RoomPlacement.valid(m,eye,listOf(wall(distance=1.8)),100))}
    @Test fun unrelatedPlaneDoesNotReplaceIdentity(){val m=RoomPlacement.walls(eye,listOf(wall("A")),100).single();assertTrue(RoomPlacement.valid(m,eye,listOf(wall("B",1.3),wall("A")),100));assertEquals("A",m.planeId)}
    @Test fun realTableNeedsFloorEvidence(){val m=RoomPlacement.walls(eye,listOf(wall()),100).single();assertTrue(RoomPlacement.tables(m,listOf(floor(.9,"table")),null).isEmpty())}
    @Test fun floorCannotMasqueradeAsTable(){val m=RoomPlacement.walls(eye,listOf(wall()),100).single();assertTrue(RoomPlacement.tables(m,listOf(floor(0.0,"floor")),0.0).isEmpty())}
    @Test fun usableTablePreservesWallIdentity(){val m=RoomPlacement.walls(eye,listOf(wall()),100).single();val table=RoomPlacement.tables(m,listOf(floor(.9,"table")),0.0).single();assertEquals("wall",table.planeId);assertEquals("table",table.tableId);assertEquals(.99,table.console!!.y,.0001)}
    @Test fun practiceIsExplicitAndHasNoSurfaceId(){val m=RoomPlacement.practice(eye,100)!!;assertTrue(m.practice);assertNull(m.tableId);assertEquals(-1.6,m.center.z,.0001)}
    @Test fun yawRotatedRoomRemainsFront(){val a=Math.PI/4;val e=eye.copy(eye=eye.eye.yaw(a),forward=eye.forward.yaw(a));val p=wall();val rotated=p.copy(triangles=p.triangles.map{Triangle(it.a.yaw(a),it.b.yaw(a),it.c.yaw(a))},normal=p.normal.yaw(a));val m=RoomPlacement.walls(e,listOf(rotated),100).single();assertTrue((m.center-e.eye).normalized().dot(e.forward)>.99)}
    @Test fun localAxesTransformConsistently(){val m=RoomPlacement.practice(eye,100)!!;assertEquals(m.center+V3(.2,.1,-.3),m.fromLocal(V3(.2,.1,-.3)))}
}
