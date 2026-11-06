package com.vrgames.deeprescue.data

import com.vrgames.deeprescue.domain.model.RescuePhase
import com.vrgames.deeprescue.domain.model.RescueProgress
import com.vrgames.deeprescue.domain.model.V3
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RescueRepositoryTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun freshProgressLoadsBeforeDefaultsAreWritten(){val r=RescueRepository(folder.root);assertEquals(RescueProgress(),r.load().progress);assertEquals(0,folder.root.listFiles()!!.size)}
    @Test fun roundTripKeepsSettingsAndTool(){val r=RescueRepository(folder.root);val p=RescueProgress(tool=V3(.30,-.10,-.30),elapsed=29.0,inspected=setOf("front-rope"),settings=com.vrgames.deeprescue.domain.model.AudioSettings(false,true));r.save(p);assertEquals(p,r.load().progress)}
    @Test fun corruptedSaveIsPreserved(){val f=java.io.File(folder.root,"deep-sea-rescue-v1.json");f.writeText("not-json");val r=RescueRepository(folder.root);assertNotNull(r.load().warning);assertEquals("not-json",f.readText());assertTrue(folder.root.listFiles()!!.any{it.name.startsWith("deep-sea-rescue-unreadable-")})}
    @Test fun impossiblePhaseIsRejected(){assertFalse(RescueRepository.valid(RescueProgress(phase=RescuePhase.RESULT)))}
    @Test fun nonfinitePositionIsRejected(){assertFalse(RescueRepository.valid(RescueProgress(tool=V3(Double.NaN,0.0,0.0))))}
    @Test fun unknownInspectionIsRejected(){assertFalse(RescueRepository.valid(RescueProgress(inspected=setOf("fake"))))}
    @Test fun overwriteLeavesNoTemporaryFile(){val r=RescueRepository(folder.root);r.save(RescueProgress(elapsed=1.0));r.save(RescueProgress(elapsed=2.0));assertEquals(2.0,r.load().progress.elapsed,.0001);assertFalse(java.io.File(folder.root,"deep-sea-rescue-v1.tmp").exists())}
}
