package com.vrgames.deeprescue.data

import com.vrgames.deeprescue.domain.model.RescueProgress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Ordered immutable checkpoints. Disk sync never runs in the render/frame callback. */
class RescueSaveQueue(
    private val repository: RescueRepository,
    private val executor: Executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "RescueSaveWriter").apply { isDaemon = true }
    }
) : AutoCloseable {
    private var closed = false
    private var tail = CompletableFuture.completedFuture(Unit)

    @Synchronized fun save(snapshot: RescueProgress): CompletableFuture<Unit> {
        check(!closed)
        val result = CompletableFuture<Unit>()
        // Serial scheduling preserves checkpoint order even when a previous write failed.
        val previous = tail
        tail = result
        previous.handle { _, _ -> Unit }.thenRunAsync({
            try { repository.save(snapshot); result.complete(Unit) }
            catch (error: Exception) { result.completeExceptionally(error) }
        }, executor)
        return result
    }

    @Synchronized fun pending(): CompletableFuture<Unit> = tail

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        // Finish already queued snapshots, then release the worker. No blocking UI wait.
        tail.whenComplete { _, _ -> (executor as? ExecutorService)?.shutdown() }
    }
}
