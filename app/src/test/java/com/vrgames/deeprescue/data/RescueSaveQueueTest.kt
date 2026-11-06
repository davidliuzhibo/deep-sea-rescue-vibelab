package com.vrgames.deeprescue.data

import com.vrgames.deeprescue.domain.model.RescueProgress
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RescueSaveQueueTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun checkpointsWaitForWorkerAndPreserveOrder(){
        val jobs=ArrayDeque<Runnable>();val repo=RescueRepository(folder.root)
        val queue=RescueSaveQueue(repo,Executor{jobs.addLast(it)})
        val first=queue.save(RescueProgress(elapsed=1.0))
        val last=queue.save(RescueProgress(elapsed=2.0))
        assertFalse(first.isDone);assertFalse(last.isDone)
        assertEquals(0.0,repo.load().progress.elapsed,0.0)
        jobs.removeFirst().run();assertEquals(1.0,repo.load().progress.elapsed,0.0)
        jobs.removeFirst().run();assertEquals(2.0,repo.load().progress.elapsed,0.0)
        assertTrue(last.isDone);queue.close()
    }
    @Test fun failedCheckpointDoesNotPoisonNextSave(){
        val repo=RescueRepository(folder.root);val q=RescueSaveQueue(repo,Executor{it.run()})
        val failed=q.save(RescueProgress(errors=-1));assertTrue(failed.isCompletedExceptionally)
        q.save(RescueProgress(elapsed=3.0)).get(2,TimeUnit.SECONDS)
        assertEquals(3.0,repo.load().progress.elapsed,0.0);q.close()
    }
    @Test fun closeDrainsLatestSnapshotAndRejectsNewRequests(){
        val jobs=ArrayDeque<Runnable>();val repo=RescueRepository(folder.root)
        val q=RescueSaveQueue(repo,Executor{jobs.addLast(it)})
        q.save(RescueProgress(elapsed=4.0));q.close()
        assertThrows(IllegalStateException::class.java){q.save(RescueProgress())}
        jobs.removeFirst().run();assertEquals(4.0,repo.load().progress.elapsed,0.0)
    }
    @Test fun productionWorkerWritesVerifiedEnvelope(){
        val repo=RescueRepository(folder.root);val q=RescueSaveQueue(repo)
        try{q.save(RescueProgress(elapsed=5.0)).get(5,TimeUnit.SECONDS);assertEquals(5.0,repo.load().progress.elapsed,0.0)}finally{q.close()}
    }
}
