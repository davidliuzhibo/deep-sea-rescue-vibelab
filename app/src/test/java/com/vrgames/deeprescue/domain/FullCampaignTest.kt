package com.vrgames.deeprescue.domain

import com.vrgames.deeprescue.domain.model.*
import com.vrgames.deeprescue.domain.usecase.CampaignUseCase
import com.vrgames.deeprescue.data.RescueRepository
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.vrgames.deeprescue.data.SaveEnvelope
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class FullCampaignTest {
    @get:Rule val folder=TemporaryFolder()
    private fun time(e:RescueEngine,t:Double){repeat((t*120).toInt()){e.tick(1.0/120)}}
    private fun operate(e:RescueEngine,id:String){
        focusIsOpen(e)
        // v1.2.1: the rope step rings 前鳍绳 until it is cut, whatever is picked or near.
        if(e.progress.phase==RescuePhase.CUT&&e.progress.cuts==0)assertEquals("front-rope",StepFocus.focus(e.progress,e.selected))
        e.select(id);e.approach();time(e,6.0);assertFalse("tool failed to reach $id: ${e.message}",e.moving);e.execute();time(e,1.2)
        // Done targets are let go of: the ring moves on and the panel names what is next.
        assertNull("still picked after $id",e.selected);focusIsOpen(e)
    }
    /** Every step: the ring is on a target not done yet (or on none, where the next move is on the console or a puzzle). */
    private fun focusIsOpen(e:RescueEngine){val p=e.progress;val f=StepFocus.focus(p,e.selected)
        if(f!=null)assertFalse("focus on done $f in ${p.phase}",StepFocus.done(p,f))
        else assertTrue("no focus in ${p.phase} with ${StepFocus.remaining(p)} left",StepFocus.remaining(p).isEmpty()||StepFocus.puzzle(p))}
    /** Drags the arm with the joystick until the tool is [gap] metres from [target], then lets go (as LooserArmTest). */
    private fun dragTo(e:RescueEngine,target:V3,gap:Double){assertTrue(e.dragBegin(7));var total=V3();var guard=0
        while((e.progress.tool-target).length()>gap&&guard++<400){val left=target-e.progress.tool;total=total+left*(.015/maxOf(left.length(),1e-9));e.dragMove(7,total);time(e,.15)}
        e.dragEnd(7,false)}
    private fun toCut():RescueEngine{val e=RescueEngine();e.setActive(true)
        for(id in listOf("front-rope","rear-rope","brace-point"))operate(e,id);operate(e,"brace-point")
        assertEquals(RescuePhase.CUT,e.progress.phase);return e}
    @Test fun lettingGoNearTheWrongRopeOnlyHints(){val e=toCut()
        // The arm is parked at the beam clamp (nearer 尾部绳 than 前鳍绳).
        assertTrue((e.progress.tool-e.targetPosition("brace-point")!!).length()<.01)
        assertEquals("front-rope",StepFocus.focus(e.progress,e.selected))
        val errors=e.progress.errors
        dragTo(e,e.targetPosition("rear-rope")!!,.10);time(e,2.0)
        assertTrue((e.progress.tool-e.targetPosition("rear-rope")!!).length() in .08..10.0)
        assertEquals("no execution",0,e.progress.cuts);assertFalse(e.acting);assertEquals("no mistake",errors,e.progress.errors)
        assertTrue(e.message,e.message.contains("前鳍绳"));assertEquals("front-rope",StepFocus.focus(e.progress,e.selected))
        // 「执行」 there with nothing picked does not cut it either.
        e.execute();time(e,2.0);assertEquals(0,e.progress.cuts);assertEquals(errors,e.progress.errors)
        // Near the right one it cuts, lets go of it, and the ring moves to 尾部绳.
        dragTo(e,e.targetPosition("front-rope")!!,.10);time(e,1.5)
        assertEquals(e.message,1,e.progress.cuts);assertEquals(errors,e.progress.errors);assertNull(e.selected)
        assertEquals("rear-rope",StepFocus.focus(e.progress,e.selected))
        // The cut rope cannot be picked again.
        e.select("front-rope");assertNull(e.selected);assertEquals(errors,e.progress.errors)}
    @Test fun pickingTheWrongRopeStillSaysOrder(){val e=toCut();val errors=e.progress.errors
        e.go("rear-rope");time(e,6.0);assertEquals(0,e.progress.cuts);assertEquals(errors+1,e.progress.errors);assertTrue(e.message,e.message.contains("顺序不对"))
        assertEquals("front-rope",StepFocus.focus(e.progress,e.selected))}
    @Test fun hookedBagRingsNothingAndCannotBePickedAgain(){val e=toCut();operate(e,"front-rope");operate(e,"rear-rope");operate(e,"float-hook")
        assertTrue(e.progress.attached);assertNull(StepFocus.focus(e.progress,e.selected));e.select("float-hook");assertNull(e.selected)}
    private fun first():RescueEngine {
        val e=RescueEngine();e.setActive(true)
        for(id in listOf("front-rope","rear-rope","brace-point"))operate(e,id)
        operate(e,"brace-point");operate(e,"front-rope");operate(e,"rear-rope");operate(e,"float-hook");e.air(1);e.air(1)
        repeat(3){e.steer(-1);time(e,1.1)}
        repeat(6){e.steer(-1);time(e,.4);e.reel(1);time(e,1.6)}
        time(e,8.0);assertEquals(e.message,RescuePhase.RESULT,e.progress.phase);assertTrue(RescueRepository.valid(e.progress));return e
    }
    private fun beacon(e:RescueEngine){assertTrue(e.startMission(1));e.setActive(true)
        for(t in listOf("power-port","lens-left","lens-mid"))operate(e,t)
        operate(e,"power-port");assertTrue(e.progress.work.isolated);assertFalse(e.progress.work.cell);operate(e,"power-port")
        // The wheel puzzle: nothing is ringed until the player picks a wheel.
        assertEquals(RescuePhase.BEACON_ALIGN,e.progress.phase);assertNull(StepFocus.focus(e.progress,e.selected))
        e.select("lens-left");assertEquals("lens-left",StepFocus.focus(e.progress,e.selected));e.approach();time(e,6.0);e.dial(1)
        e.select("lens-mid");e.approach();time(e,6.0);e.dial(1);e.dial(1)
        assertEquals(listOf(1,3,2),e.progress.work.dials);e.execute();time(e,3.1)
        assertEquals(RescuePhase.RESULT,e.progress.phase);assertTrue(RescueRepository.valid(e.progress))
    }
    private fun balance(e:RescueEngine){assertTrue(e.startMission(2));e.setActive(true)
        for(t in listOf("anchor-left","anchor-right","load-weight"))operate(e,t)
        operate(e,"anchor-left");operate(e,"anchor-right")
        val heavy=if(e.progress.work.heavyRight)"right"else"left";val light=if(heavy=="right")"left"else"right"
        repeat(2){e.bagAir(light,1)};repeat(3){e.bagAir(heavy,1)}
        repeat(4){e.liftCage(1);time(e,1.9)};repeat(4){e.parkCage(1);time(e,1.6)};e.lockCage()
        assertEquals(e.message,RescuePhase.RESULT,e.progress.phase);assertTrue(RescueRepository.valid(e.progress))
    }
    private fun finish(e:RescueEngine){assertTrue(e.startMission(3));e.setActive(true)
        for((i,lane) in listOf(-1,1,-1).withIndex()){e.routeSelect(i);e.routeLane(lane);val old=e.progress.work.escort;e.execute();assertEquals(old,e.progress.work.escort);time(e,30.0);assertEquals(e.message,i+1,e.progress.work.segment)}
        time(e,8.1);assertEquals(RescuePhase.RESULT,e.progress.phase);assertEquals(setOf(0,1,2,3),e.progress.campaign.completed);assertTrue(RescueRepository.valid(e.progress))
    }
    @Test fun completeFourMissionsBeaconThenBalance(){val e=first();beacon(e);balance(e);finish(e)}
    @Test fun completeFourMissionsBalanceThenBeacon(){val e=first();balance(e);beacon(e);finish(e)}
    @Test fun lockedMissionCannotBypassTurtle(){val e=RescueEngine();assertFalse(e.startMission(3));assertFalse(e.startMission(1));assertFalse(e.startMission(2))}
    @Test fun anOpenLevelCanBePickedFromTheListAnyTime(){val e=first();e.startMission(1);assertTrue(e.startMission(2));assertEquals(2,e.progress.campaign.mission);assertFalse(e.startMission(3))}
    @Test fun nextLevelIsTheFirstOpenOneNotDone(){val e=first();assertEquals(1,CampaignUseCase.next(e.progress.campaign));beacon(e);assertEquals(2,CampaignUseCase.next(e.progress.campaign));balance(e);assertEquals(3,CampaignUseCase.next(e.progress.campaign))}
    @Test fun replayChangesVisibleWeightSideAndKeepsRecords(){val e=first();balance(e);val records=e.progress.campaign;balance(e);assertFalse(e.progress.work.heavyRight);assertEquals(records.completed,e.progress.campaign.completed)}
    @Test fun restartPreservesOtherAchievements(){val e=first();beacon(e);e.startMission(2);e.restart();assertEquals(setOf(0,1),e.progress.campaign.completed);assertEquals(RescuePhase.BALANCE_SURVEY,e.progress.phase)}
    @Test fun dialRequiresApproachAndSelection(){val e=first();e.startMission(1);e.setActive(true);e.dial(1);assertEquals(listOf(0,0,0),e.progress.work.dials)}
    @Test fun dialCouplingInverseIsReversible(){val e=first();e.startMission(1);e.setActive(true);for(t in listOf("power-port","lens-left","lens-mid"))operate(e,t);operate(e,"power-port");operate(e,"power-port");e.select("lens-left");e.approach();time(e,6.0);e.dial(1);assertEquals(listOf(1,1,0),e.progress.work.dials);e.dial(-1);assertEquals(listOf(0,0,0),e.progress.work.dials);e.execute();assertEquals(RescuePhase.BEACON_ALIGN,e.progress.phase)}
    @Test fun unbalancedCageCannotMove(){val e=first();e.startMission(2);e.setActive(true);for(t in listOf("anchor-left","anchor-right","load-weight"))operate(e,t);operate(e,"anchor-left");operate(e,"anchor-right");e.bagAir("left",1);e.liftCage(1);time(e,8.0);assertEquals(0.0,e.progress.work.cageHeight,0.0);assertFalse(e.progress.work.locked);e.parkCage(1);e.lockCage();assertEquals(RescuePhase.BALANCE_LIFT,e.progress.phase)}
    @Test fun suspendedLoadMustReturnToCradleBeforeAdjustingAir(){
        val e=first();e.startMission(2);e.setActive(true)
        for(t in listOf("anchor-left","anchor-right","load-weight"))operate(e,t)
        operate(e,"anchor-left");operate(e,"anchor-right")
        repeat(2){e.bagAir("left",1)};repeat(3){e.bagAir("right",1)}
        e.liftCage(1);time(e,1.9);val suspended=e.progress.work
        e.bagAir("right",1);assertEquals(suspended,e.progress.work)
        assertTrue(e.message.contains("托架"));e.liftCage(-1);time(e,2.0)
        assertEquals(0.0,e.progress.work.cageHeight,0.0001)
        e.bagAir("right",1);assertEquals(4,e.progress.work.rightAir)
        val resting=e.progress.work.cageHeight;e.liftCage(1);time(e,2.0);assertEquals(resting,e.progress.work.cageHeight,0.0)
    }
    @Test fun routeHasOneSafeChoiceEachAndTestsSweptSegments(){for(i in 0..2){val checkpoint=if(i==0)MissionGeometry.escortStart else MissionGeometry.node(i-1,listOf(-1,1,-1)[i-1]);for(lane in -1..1){val lanes=listOf(0,0,0).toMutableList();lanes[i]=lane;val w=MissionWork(segment=i,lanes=lanes,escort=checkpoint,checkpoint=checkpoint);if(lane==listOf(-1,1,-1)[i])assertNull("segment $i",MissionGeometry.obstruction(w))else assertNotNull("unsafe $i/$lane",MissionGeometry.obstruction(w))}}}
    @Test fun wrongRouteIsRejectedBeforeAnimalMoves(){val e=first();beacon(e);balance(e);e.startMission(3);e.setActive(true);val p=e.progress.work.escort;e.execute();time(e,5.0);assertEquals(p,e.progress.work.escort);assertEquals(0,e.progress.work.segment);assertTrue(e.progress.errors>0)}
    @Test fun pauseAndReturnNeverAutoResumesEscort(){val e=first();beacon(e);balance(e);e.startMission(3);e.setActive(true);e.routeLane(-1);e.execute();time(e,.8);val p=e.progress.work.escort;e.setActive(false);time(e,100.0);e.setActive(true);time(e,3.0);assertEquals(p,e.progress.work.escort);e.execute();time(e,15.0);assertEquals(1,e.progress.work.segment)}
    @Test fun staleHugeFrameCannotFinishLightOrEscort(){val e=first();beacon(e);balance(e);e.startMission(3);e.setActive(true);e.routeLane(-1);e.execute();e.tick(1000.0);assertEquals(0,e.progress.work.segment);assertTrue((e.progress.work.escort-MissionGeometry.escortStart).length()<=.0056)}
    @Test fun skipIsNotACheatToFinishEscort(){val e=first();beacon(e);balance(e);e.startMission(3);e.setActive(true);e.skipFarewell();assertEquals(RescuePhase.ESCORT_PLAN,e.progress.phase)}
    @Test fun saveReloadEachCheckpointPreservesAllFourMissions(){val r=RescueRepository(folder.root);var e=first();r.save(e.progress);e=RescueEngine(r.load().progress);beacon(e);r.save(e.progress);e=RescueEngine(r.load().progress);balance(e);r.save(e.progress);e=RescueEngine(r.load().progress);finish(e);r.save(e.progress);assertEquals(e.progress,r.load().progress)}
    @Test fun partialMissionSaveRetainsDialsWithoutClaimingCompletion(){val e=first();e.startMission(1);e.setActive(true);for(t in listOf("power-port","lens-left","lens-mid"))operate(e,t);operate(e,"power-port");operate(e,"power-port");e.select("lens-left");e.approach();time(e,5.0);e.dial(1);val r=RescueRepository(folder.root);r.save(e.progress);assertEquals(e.progress,r.load().progress);assertEquals(setOf(0),r.load().progress.campaign.completed)}
    @Test fun legacyFileIsMigratedWithoutOverwritingOriginal(){val e=first();val gson=Gson();val tree=JsonParser.parseString(gson.toJson(e.progress)).asJsonObject;tree.remove("campaign");tree.remove("work");val payload=tree.toString();val bytes=gson.toJson(SaveEnvelope(1,payload,RescueRepository.digest(payload)));val original=File(folder.root,"deep-sea-rescue-v1.json");original.writeText(bytes);val r=RescueRepository(folder.root);val loaded=r.load();assertNull(loaded.warning);assertEquals(setOf(0),loaded.progress.campaign.completed);r.save(loaded.progress);assertEquals(bytes,original.readText());assertTrue(File(folder.root,"deep-sea-rescue-v2.json").exists())}
    @Test fun impossibleCampaignAndEndingAreRejected(){assertFalse(RescueRepository.valid(RescueProgress(campaign=CampaignProgress(3,setOf(3)))));val e=first();e.startMission(1);assertFalse(RescueRepository.valid(e.progress.copy(phase=RescuePhase.RESULT)));assertFalse(RescueRepository.valid(e.progress.copy(work=e.progress.work.copy(dials=listOf(8,0,0)))))}
}
