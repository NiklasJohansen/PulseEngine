package no.njoh.pulseengine.core.shared.utils

import java.util.concurrent.BrokenBarrierException

/**
 * This class is a simple implementation of a cyclic thread barrier that blocks a number of threads
 * until they all reach a common synchronization point. When the last thread arrives, all threads are
 * released and the barrier is reset for the next cycle.
 */
class ThreadBarrier(private val threadCount: Int)
{
    private val lock = Object()
    private var waitingThreadCount = threadCount
    private var currentGeneration = 0L
    private var wasBarrierBroken = false

    /**
     * Blocks until all threads have arrived
     */
    fun await() = synchronized(lock)
    {
        if (wasBarrierBroken) throw BrokenBarrierException("Barrier already broken")

        if (Thread.interrupted())
        {
            // Break the barrier if the thread was interrupted
            breakBarrier()
            throw InterruptedException()
        }

        val arrivalGeneration = currentGeneration
        val index = --waitingThreadCount
        if (index == 0)
        {
            // Last to arrive, wake up all threads
            nextGeneration()
            return
        }

        // If not last to arrive, wait until:
        // (1) barrier is tripped (generation changes), or
        // (2) barrier is broken, or
        // (3) spurious wakeup
        while (arrivalGeneration == currentGeneration && !wasBarrierBroken) lock.wait()

        // If barrier was broken, throw
        if (wasBarrierBroken) throw BrokenBarrierException("Barrier broken while waiting")
    }

    private fun nextGeneration()
    {
        lock.notifyAll() // Wake all threads that are waiting in this generation
        waitingThreadCount = threadCount
        currentGeneration++
        wasBarrierBroken = false
    }

    private fun breakBarrier()
    {
        wasBarrierBroken = true
        currentGeneration++ // So waiting threads see the barrier as advanced
        waitingThreadCount = threadCount
        lock.notifyAll()
    }
}
