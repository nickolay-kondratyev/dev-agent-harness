package com.glassthought.shepherd.core.time

import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Deterministic [Clock] for tests. NOT thread-safe.
 *
 * Allows explicit control of time via [advance] and [set].
 */
class TestClock(
    initialTime: Instant = Instant.EPOCH,
) : Clock {

    private var currentTime: Instant = initialTime

    override fun now(): Instant = currentTime

    /**
     * Advances the internal clock by [duration].
     */
    fun advance(duration: Duration) {
        currentTime = currentTime.plus(duration.toJavaDuration())
    }

    /**
     * Sets the internal clock to [instant].
     */
    fun set(instant: Instant) {
        currentTime = instant
    }
}
