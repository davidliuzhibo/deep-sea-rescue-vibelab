package com.vrgames.deeprescue.domain

import com.vrgames.deeprescue.domain.model.RescueGeometry as G
import com.vrgames.deeprescue.domain.model.RescuePhase as P
import com.vrgames.deeprescue.domain.model.RescueProgress
import com.vrgames.deeprescue.domain.model.V3
import org.junit.Assert.*
import org.junit.Test

class RescueEngineTest {
    private fun engine()=RescueEngine().apply{setActive(true)}
    private fun time(e:RescueEngine,s:Double){repeat((s*120).toInt()){e.tick(1.0/120)}}
    private fun action(e:RescueEngine,id:String){e.select(id);e.approach();time(e,7.0);assertFalse(e.message,e.moving);e.execute();time(e,1.2)}
    private fun atWinch():RescueEngine {val e=engine();for(id in listOf("front-rope","rear-rope","brace-point"))action(e,id);action(e,"brace-point");action(e,"front-rope");action(e,"rear-rope");action(e,"float-hook");e.air(1);e.air(1);assertEquals(P.WINCH,e.progress.phase);return e}
    @Test fun selectionNeverMovesOrCompletes(){val e=engine();val before=e.progress;e.select("front-rope");time(e,1.0);assertEquals(before.tool,e.progress.tool);assertEquals(emptySet<String>(),e.progress.inspected)}
    @Test fun remoteExecuteCannotComplete(){val e=engine();e.select("front-rope");e.execute();time(e,4.0);assertTrue(e.progress.inspected.isEmpty())}
    @Test fun movementIsFiniteSpeed(){val e=engine();e.select("front-rope");val p=e.progress.tool;e.approach();time(e,.1);assertTrue((p-e.progress.tool).length()<=.0221);assertTrue(e.moving)}
    @Test fun staleFrameCannotTeleport(){val e=engine();e.select("front-rope");e.approach();val p=e.progress.tool;e.tick(300.0);assertTrue((p-e.progress.tool).length()<=.0221)}
    @Test fun inspectionRequiresDwell(){val e=engine();e.select("front-rope");e.approach();time(e,7.0);e.execute();time(e,.5);assertTrue(e.progress.inspected.isEmpty());time(e,.5);assertEquals(setOf("front-rope"),e.progress.inspected)}
    @Test fun duplicateInspectionDoesNotSkipOthers(){val e=engine();repeat(3){action(e,"front-rope")};assertEquals(P.INSPECT,e.progress.phase);assertEquals(1,e.progress.inspected.size)}
    @Test fun threeInspectionsUnlockBrace(){val e=engine();for(id in listOf("front-rope","rear-rope","brace-point"))action(e,id);assertEquals(P.BRACE,e.progress.phase)}
    @Test fun rearCutFirstIsBlocked(){val e=engine();for(id in listOf("front-rope","rear-rope","brace-point"))action(e,id);action(e,"brace-point");action(e,"rear-rope");assertEquals(0,e.progress.cuts);assertEquals(P.CUT,e.progress.phase)}
    @Test fun wrongTargetNeverSelected(){val e=engine();e.select("float-hook");assertNull(e.selected)}
    @Test fun airCannotBypassAttachment(){val e=engine();repeat(5){e.air(1)};assertEquals(0,e.progress.air);assertFalse(e.progress.attached)}
    @Test fun fullToolSequenceReachesWinch(){val e=atWinch();assertTrue(e.progress.braced);assertEquals(2,e.progress.cuts);assertEquals(2,e.progress.air);assertTrue(e.progress.attached)}
    @Test fun blockedReelBrakesWithoutDeath(){val e=atWinch();repeat(6){e.reel(1);time(e,3.0)};assertEquals(P.WINCH,e.progress.phase);assertTrue(e.progress.errors>0);assertTrue(e.progress.turtle.y<.20)}
    @Test fun payOutReducesTension(){val e=atWinch();e.reel(1);time(e,4.0);val high=e.tension;e.reel(-1);time(e,2.0);assertTrue(e.tension<high)}
    @Test fun safeRouteCompletesOnlyAfterFarewell(){val e=atWinch();repeat(3){e.steer(-1);time(e,1.1)};repeat(6){e.reel(1);time(e,1.6)};assertEquals(P.FAREWELL,e.progress.phase);assertTrue(G.safe(e.progress.turtle));time(e,8.0);assertEquals(P.RESULT,e.progress.phase)}
    @Test fun extraAirPreventsUncontrolledAscent(){val e=atWinch();repeat(3){e.steer(-1);time(e,1.1)};e.air(1);e.reel(1);val before=e.progress.turtle;time(e,3.0);assertEquals(before.y,e.progress.turtle.y,0.0);assertTrue(e.progress.turtle.x>=before.x);assertTrue(e.progress.turtle.x-before.x<=.0121);assertTrue(e.tension>.7)}
    @Test fun zeroAirDoesNotLift(){val e=atWinch();e.air(-1);e.air(-1);e.reel(1);val before=e.progress.turtle;time(e,3.0);assertEquals(before,e.progress.turtle)}
    @Test fun pauseFreezesPhysicsAndTimer(){val e=engine();e.select("front-rope");e.approach();time(e,1.0);e.setActive(false);val p=e.progress;time(e,20.0);assertEquals(p,e.progress)}
    @Test fun resumeDoesNotResumeOldMotion(){val e=engine();e.select("front-rope");e.approach();time(e,.4);e.setActive(false);e.setActive(true);val p=e.progress.tool;time(e,1.0);assertEquals(p,e.progress.tool)}
    @Test fun backgroundControlsDoNothing(){val e=engine();e.setActive(false);val p=e.progress;e.select("front-rope");e.approach();e.execute();e.air(1);e.reel(1);time(e,5.0);assertEquals(p,e.progress)}
    @Test fun cancelledDwellDoesNotComplete(){val e=engine();e.select("front-rope");e.approach();time(e,7.0);e.execute();time(e,.5);e.setActive(false);e.setActive(true);time(e,2.0);assertTrue(e.progress.inspected.isEmpty())}
    @Test fun onePointerOwnsDrag(){val e=engine();assertTrue(e.dragBegin(3));assertFalse(e.dragBegin(4));e.dragMove(4,V3(.1,0.0,0.0));time(e,.5);assertEquals(G.home,e.progress.tool)}
    @Test fun dragGainAndJumpAreLimited(){val e=engine();e.dragBegin(3);e.dragMove(3,V3(-10.0,0.0,0.0));time(e,1.0);assertEquals(.033,(G.home-e.progress.tool).length(),.0001)}
    @Test fun normalReleasePreservesPosition(){val e=engine();e.dragBegin(1);e.dragMove(1,V3(-.05,0.0,0.0));time(e,.4);e.dragEnd(1,false);val p=e.progress.tool;time(e,2.0);assertEquals(p,e.progress.tool)}
    @Test fun cancellationStopsPendingMotion(){val e=engine();e.dragBegin(1);e.dragMove(1,V3(-.06,0.0,0.0));e.dragEnd(1,true);time(e,1.0);assertEquals(G.home,e.progress.tool)}
    @Test fun unknownReleaseDoesNotDropOwner(){val e=engine();e.dragBegin(1);e.dragEnd(2,false);assertFalse(e.dragBegin(3))}
    @Test fun nonfiniteInputIsIgnored(){val e=engine();e.dragBegin(1);e.dragMove(1,V3(Double.NaN,0.0,0.0));e.tick(Double.POSITIVE_INFINITY);assertEquals(G.home,e.progress.tool)}
    @Test fun nudgeIsSmallFiniteMotion(){val e=engine();e.nudge(V3(-2.0,0.0,0.0));time(e,1.0);assertEquals(.025,(G.home-e.progress.tool).length(),.0001)}
    @Test fun boundaryCannotBeEscaped(){val e=engine();repeat(100){e.nudge(V3(.025,0.0,0.0));time(e,.2)};assertTrue(G.insideTool(e.progress.tool))}
    @Test fun protectedBodyRejectsTool(){val initial=RescueProgress(tool=V3(-.10,-.16,-.45));val e=RescueEngine(initial);e.setActive(true);repeat(20){e.nudge(V3(0.0,0.0,-.025));time(e,.2)};assertFalse(G.bodyContact(e.progress.tool,e.progress.turtle));assertTrue(e.progress.errors>0)}
    @Test fun restartRetainsAudioChoice(){val e=atWinch();e.settings(false,false);e.restart();assertEquals(P.INSPECT,e.progress.phase);assertFalse(e.progress.settings.music);assertEquals(0,e.progress.cuts)}
    @Test fun randomInputNeverLeavesToolEnvelope(){val e=engine();val r=java.util.Random(2);repeat(1000){e.nudge(V3(r.nextDouble()-.5,r.nextDouble()-.5,r.nextDouble()-.5));time(e,.05);assertTrue(G.insideTool(e.progress.tool));assertFalse(G.bodyContact(e.progress.tool,e.progress.turtle))}}
    @Test fun selectingDifferentTargetCancelsPreviousApproach(){
        val e=RescueEngine();e.setActive(true);e.select("front-rope");e.approach();e.tick(.05)
        e.select("rear-rope");val point=e.progress.tool;e.tick(.1)
        assertEquals(point,e.progress.tool);assertFalse(e.moving)
    }
    @Test fun waterCurrentIsGentleAndCannotPushThroughBeam(){
        val e=atWinch();val start=e.progress.turtle;time(e,3.0);assertEquals(start,e.progress.turtle)
        repeat(3){e.steer(-1);time(e,1.1)};val left=e.progress.turtle;time(e,5.0)
        assertTrue(e.progress.turtle.x>left.x);assertTrue(e.progress.turtle.x-left.x<=.0201)
        assertEquals(left.y,e.progress.turtle.y,0.0);assertFalse(G.beamContact(e.progress.turtle))
    }
    @Test fun pauseAlsoFreezesCurrentAndTension(){
        val e=atWinch();repeat(3){e.steer(-1);time(e,1.1)};e.setActive(false)
        val p=e.progress;val tension=e.tension;time(e,30.0)
        assertEquals(p,e.progress);assertEquals(tension,e.tension,0.0)
    }
    @Test fun everyNamedTargetIsWithinActualArmReach(){
        for((name,target) in G.targets)assertTrue(name,G.insideTool(target))
        assertFalse(G.insideTool(V3(-.58,.30,-.12)))
    }
}
