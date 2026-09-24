package com.cdodi.core.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MS = 1_000_000L

class HeartbeatTest {

    private fun DefaultHeartbeat.tickAt(vararg millis: Long) = millis.forEach { tick(it * MS) }

    @Test
    fun phasesRunInOrderWithinAFrame() {
        val heartbeat = DefaultHeartbeat()
        val calls = mutableListOf<FramePhase>()
        FramePhase.entries.reversed().forEach { phase -> heartbeat.subscribe(phase) { calls += phase } }

        heartbeat.tickAt(0)

        assertEquals(FramePhase.entries.toList(), calls)
    }

    @Test
    fun dtIsSecondsBetweenTicksAndTheFirstFrameIsZero() {
        val heartbeat = DefaultHeartbeat()
        val dts = mutableListOf<Float>()
        heartbeat.subscribe(FramePhase.Simulation) { dts += it.dt }

        heartbeat.tickAt(1000, 1016, 1050)

        assertEquals(listOf(0f, 0.016f, 0.034f), dts.map { (it * 1000).toInt() / 1000f })
        assertEquals(0.05, heartbeat.elapsed, 1e-6)
    }

    @Test
    fun longStallsAreClamped() {
        val heartbeat = DefaultHeartbeat(maxDt = 0.1f)
        heartbeat.tickAt(0, 5000)

        assertEquals(0.1, heartbeat.elapsed, 1e-6)
    }

    @Test
    fun childClocksScaleAndPauseIndependently() {
        val heartbeat = DefaultHeartbeat()
        val slow = heartbeat.child("slow").apply { timeScale = 0.5f }
        val paused = heartbeat.child("paused").apply { isPaused = true }

        heartbeat.tickAt(0, 100)

        assertEquals(0.1, heartbeat.elapsed, 1e-6)
        assertEquals(0.05, slow.elapsed, 1e-6)
        assertEquals(0.0, paused.elapsed)
    }

    @Test
    fun scalesMultiplyAndPausingAParentStopsItsChildren() {
        val heartbeat = DefaultHeartbeat()
        val transitions = heartbeat.child("transitions").apply { timeScale = 0.5f }
        val inner = transitions.child("inner").apply { timeScale = 0.5f }

        heartbeat.tickAt(0, 100)
        assertEquals(0.025, inner.elapsed, 1e-6)

        transitions.isPaused = true
        heartbeat.tickAt(200)
        assertEquals(0.025, inner.elapsed, 1e-6)

        heartbeat.timeScale = 2f // global slow motion (or fast forward) reaches every clock
        transitions.isPaused = false
        heartbeat.tickAt(300)
        assertEquals(0.075, inner.elapsed, 1e-6)
    }

    @Test
    fun subscribersSeeTheirOwnClocksView() {
        val heartbeat = DefaultHeartbeat()
        val half = heartbeat.child("half").apply { timeScale = 0.5f }
        var rootDt = -1f
        var halfDt = -1f
        heartbeat.subscribe(FramePhase.Simulation) { rootDt = it.dt }
        half.subscribe(FramePhase.Simulation) { halfDt = it.dt }

        heartbeat.tickAt(0, 100)

        assertEquals(0.1f, rootDt, 1e-6f)
        assertEquals(0.05f, halfDt, 1e-6f)
    }

    @Test
    fun framesKeepComingWhilePaused() {
        val heartbeat = DefaultHeartbeat().apply { isPaused = true }
        val frames = mutableListOf<Frame>()
        heartbeat.subscribe(FramePhase.Render) { frames += it }

        heartbeat.tickAt(0, 100, 200)

        assertEquals(listOf(1L, 2L, 3L), frames.map(Frame::index))
        assertTrue(frames.all { it.dt == 0f })
    }

    @Test
    fun listenersCanUnsubscribeAndSubscribeWhileBeingCalled() {
        val heartbeat = DefaultHeartbeat()
        var onceCalls = 0
        var lateCalls = 0
        lateinit var once: Subscription
        once = heartbeat.subscribe(FramePhase.Input) {
            onceCalls++
            once.close()
            heartbeat.subscribe(FramePhase.Input) { lateCalls++ }
        }

        heartbeat.tickAt(0) // `late` joins during this frame's dispatch, so it first runs next frame
        heartbeat.tickAt(16)

        assertEquals(1, onceCalls)
        assertEquals(1, lateCalls)
    }

    @Test
    fun theFrameFlowIsPublishedInTheUiPhase() {
        val heartbeat = DefaultHeartbeat()
        var seenInRender = -1L
        var seenInUi = -1L
        heartbeat.subscribe(FramePhase.Render) { seenInRender = heartbeat.frame.value.index }
        heartbeat.subscribe(FramePhase.Ui) { seenInUi = heartbeat.frame.value.index }

        heartbeat.tickAt(0, 16)

        assertEquals(1, seenInRender)
        assertEquals(2, seenInUi)
    }

    @Test
    fun timeStaysPreciseAfterHoursOfFrames() {
        val heartbeat = DefaultHeartbeat()
        val frameNanos = 16_666_667L
        val frames = 3L * 3600 * 60 + 74 // three hours of 60 fps, plus 74 frames (about 1.233 s)
        for (i in 0..frames) heartbeat.tick(i * frameNanos)

        val exact = frames * frameNanos / 1e9
        assertEquals(exact, heartbeat.elapsed, 1e-6)
        assertEquals((exact % 60.0).toFloat(), heartbeat.elapsedFolded(period = 60.0), 1e-5f)
    }
}
