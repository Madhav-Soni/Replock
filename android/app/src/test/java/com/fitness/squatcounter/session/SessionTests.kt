package com.fitness.squatcounter.session

import com.fitness.squatcounter.domain.LandmarkPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionStateMachineTest {

    private lateinit var sm: SessionStateMachine
    private val events = mutableListOf<SessionEvent>()

    private val ok = SessionSignals(bodyVisible = true, feedHealthy = true, phoneStill = true)
    private val noBody = ok.copy(bodyVisible = false)

    @Before
    fun setUp() {
        sm = SessionStateMachine(SessionConfig()) // 2 s hold, 5 s countdown, 1 s grace, 30 s pause timeout
        events.clear()
        sm.onEventListener = { events.add(it) }
    }

    /** Start at t=0, setup passes at t=2000, countdown finishes at t=7000. */
    private fun reachActive(): Long {
        sm.start(0L)
        sm.update(0L, ok)
        sm.update(2000L, ok)
        sm.update(7000L, ok)
        return 7000L
    }

    @Test
    fun startMovesToSetupCheck() {
        sm.start(0L)
        assertEquals(SessionPhase.SETUP_CHECK, sm.phase)
    }

    @Test
    fun setupNeedsContinuousHold() {
        sm.start(0L)
        sm.update(0L, ok)
        sm.update(1000L, ok)
        assertEquals(SessionPhase.SETUP_CHECK, sm.phase)

        // Losing the body resets the hold timer
        sm.update(1500L, noBody)
        sm.update(1600L, ok)
        sm.update(3500L, ok) // only 1900 ms since 1600
        assertEquals(SessionPhase.SETUP_CHECK, sm.phase)

        sm.update(3600L, ok) // 2000 ms
        assertEquals(SessionPhase.COUNTDOWN, sm.phase)
    }

    @Test
    fun countdownThenActiveFiresActivationOnce() {
        sm.start(0L)
        sm.update(0L, ok)
        sm.update(2000L, ok)
        assertEquals(SessionPhase.COUNTDOWN, sm.phase)

        sm.update(6999L, ok)
        assertEquals(SessionPhase.COUNTDOWN, sm.phase)

        sm.update(7000L, ok)
        assertEquals(SessionPhase.ACTIVE, sm.phase)
        assertEquals(1, events.count { it is SessionEvent.SessionActivated })
    }

    @Test
    fun problemDuringCountdownBeyondGraceReturnsToSetup() {
        sm.start(0L)
        sm.update(0L, ok)
        sm.update(2000L, ok)
        assertEquals(SessionPhase.COUNTDOWN, sm.phase)

        sm.update(2500L, noBody)
        sm.update(3400L, noBody) // 900 ms: still inside grace
        assertEquals(SessionPhase.COUNTDOWN, sm.phase)

        sm.update(3500L, noBody) // 1000 ms: grace over
        assertEquals(SessionPhase.SETUP_CHECK, sm.phase)
    }

    @Test
    fun briefFlickerDuringCountdownIsForgiven() {
        sm.start(0L)
        sm.update(0L, ok)
        sm.update(2000L, ok)

        sm.update(2500L, noBody)
        sm.update(2600L, ok) // recovered, grace timer cleared
        sm.update(7000L, ok)
        assertEquals(SessionPhase.ACTIVE, sm.phase)
    }

    @Test
    fun activePausesOnProblemAndResumes() {
        reachActive()

        sm.update(7100L, noBody)
        assertEquals(SessionPhase.PAUSED, sm.phase)
        assertEquals(SetupProblem.BODY_NOT_VISIBLE, sm.problem)

        sm.update(7200L, ok)
        assertEquals(SessionPhase.ACTIVE, sm.phase)
        // Resuming must not start a second session
        assertEquals(1, events.count { it is SessionEvent.SessionActivated })
    }

    @Test
    fun pauseTimeoutEndsSessionAsInterrupted() {
        reachActive()
        sm.update(7100L, noBody)

        sm.update(37099L, noBody) // 29999 ms paused
        assertEquals(SessionPhase.PAUSED, sm.phase)

        sm.update(37100L, noBody) // 30000 ms
        assertEquals(SessionPhase.ENDED, sm.phase)
        assertEquals(SessionEndReason.PAUSE_TIMEOUT, sm.endReason)

        val ended = events.filterIsInstance<SessionEvent.SessionEnded>().single()
        assertEquals(SessionEndReason.PAUSE_TIMEOUT, ended.reason)
        assertTrue(ended.hadActiveSession)
    }

    @Test
    fun userStopAfterActiveIsUserStopWithSession() {
        reachActive()
        sm.stop(8000L)

        assertEquals(SessionPhase.ENDED, sm.phase)
        val ended = events.filterIsInstance<SessionEvent.SessionEnded>().single()
        assertEquals(SessionEndReason.USER_STOP, ended.reason)
        assertTrue(ended.hadActiveSession)
    }

    @Test
    fun stopDuringSetupHasNoActiveSession() {
        sm.start(0L)
        sm.update(0L, ok)
        sm.stop(500L)

        assertEquals(SessionPhase.ENDED, sm.phase)
        val ended = events.filterIsInstance<SessionEvent.SessionEnded>().single()
        assertFalse(ended.hadActiveSession)
    }

    @Test
    fun interruptEndsALiveSessionAsInterrupted() {
        reachActive()
        sm.interrupt(8000L)

        assertEquals(SessionPhase.ENDED, sm.phase)
        assertEquals(SessionEndReason.INTERRUPTED, sm.endReason)
    }

    @Test
    fun interruptWhenIdleDoesNothing() {
        sm.interrupt(1000L)

        assertEquals(SessionPhase.IDLE, sm.phase)
        assertTrue(events.isEmpty())
    }

    @Test
    fun feedLostHasPriorityOverBodyNotVisible() {
        sm.start(0L)
        sm.update(0L, SessionSignals(bodyVisible = false, feedHealthy = false, phoneStill = true))
        assertEquals(SetupProblem.FEED_LOST, sm.problem)
    }

    @Test
    fun movingPhoneBlocksSetup() {
        sm.start(0L)
        val moving = SessionSignals(bodyVisible = true, feedHealthy = true, phoneStill = false)

        sm.update(0L, moving)
        sm.update(5000L, moving)

        assertEquals(SessionPhase.SETUP_CHECK, sm.phase)
        assertEquals(SetupProblem.PHONE_MOVING, sm.problem)
    }

    @Test
    fun canStartAgainAfterEndAndActivateAgain() {
        reachActive()
        sm.stop(8000L)
        assertEquals(SessionPhase.ENDED, sm.phase)

        sm.start(10_000L)
        assertEquals(SessionPhase.SETUP_CHECK, sm.phase)
        assertNull(sm.endReason)

        sm.update(10_000L, ok)
        sm.update(12_000L, ok)
        sm.update(17_000L, ok)
        assertEquals(SessionPhase.ACTIVE, sm.phase)
        assertEquals(2, events.count { it is SessionEvent.SessionActivated })
    }

    @Test
    fun resetReturnsToIdle() {
        reachActive()
        sm.reset(9000L)

        assertEquals(SessionPhase.IDLE, sm.phase)
        assertNull(sm.endReason)
    }

    @Test
    fun updateIsIgnoredWhenNotLive() {
        sm.update(0L, noBody)

        assertEquals(SessionPhase.IDLE, sm.phase)
        assertEquals(SetupProblem.NONE, sm.problem)
    }
}

class FeedMonitorTest {

    private fun pose(x: Float = 0.5f): List<LandmarkPoint> =
        List(33) { LandmarkPoint(x = x, y = 0.5f) }

    @Test
    fun healthyRightAfterReset() {
        val monitor = FeedMonitor(SessionConfig())
        monitor.reset(1000L)
        assertTrue(monitor.isHealthy(1000L))
    }

    @Test
    fun staleAfterTimeout() {
        val monitor = FeedMonitor(SessionConfig()) // 2000 ms stale timeout
        monitor.reset(0L)
        monitor.onFrame(100L, pose())

        assertTrue(monitor.isHealthy(2100L))
        assertFalse(monitor.isHealthy(2101L))
    }

    @Test
    fun frozenAfterIdenticalFrames() {
        val monitor = FeedMonitor(SessionConfig(frozenFrameCount = 5))
        monitor.reset(0L)

        for (i in 1..5) monitor.onFrame(i * 100L, pose())
        assertTrue(monitor.isHealthy(500L)) // 4 identical repeats so far

        monitor.onFrame(600L, pose())
        assertFalse(monitor.isHealthy(600L)) // 5 identical repeats
    }

    @Test
    fun movingLandmarksAreNeverFrozen() {
        val monitor = FeedMonitor(SessionConfig(frozenFrameCount = 5))
        monitor.reset(0L)

        for (i in 1..50) {
            monitor.onFrame(i * 100L, pose(if (i % 2 == 0) 0.5f else 0.51f))
        }
        assertTrue(monitor.isHealthy(5000L))
    }

    @Test
    fun emptyResultClearsFrozenCounter() {
        val monitor = FeedMonitor(SessionConfig(frozenFrameCount = 5))
        monitor.reset(0L)

        for (i in 1..5) monitor.onFrame(i * 100L, pose())
        monitor.onFrame(600L, emptyList()) // person left, feed still alive
        monitor.onFrame(700L, pose())
        monitor.onFrame(800L, pose())

        assertTrue(monitor.isHealthy(800L))
    }
}