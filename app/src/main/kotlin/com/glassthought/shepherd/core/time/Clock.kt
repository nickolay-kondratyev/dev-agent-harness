package com.glassthought.shepherd.core.time

import java.time.Instant

/**
 * Abstraction over wall-clock time, enabling deterministic time control in tests.
 *
 * Two timing axes exist in the harness:
 * 1. Coroutine delays — controlled via `TestDispatcher`
 * 2. Wall-clock reads — controlled via this [Clock] interface
 *
 * See ref.ap.whDS8M5aD2iggmIjDIgV9.E for the Virtual Time Strategy spec.
 *
 * ap.xR4kT7vNcW9pLmQjY2bFs.E
 */
interface Clock {
    fun now(): Instant
}

/**
 * Production [Clock] implementation backed by [Instant.now].
 */
class SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}
