package com.glassthought.shepherd.core.time

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ClockTest : AsgardDescribeSpec({

    describe("GIVEN a SystemClock") {
        val clock: Clock = SystemClock()

        describe("WHEN now() is called") {
            val result = clock.now()

            it("THEN it returns a time within 1 second of Instant.now()") {
                val diffMs = kotlin.math.abs(result.toEpochMilli() - Instant.now().toEpochMilli())
                diffMs shouldBeLessThan 1_000L
            }
        }
    }

    describe("GIVEN a TestClock with default initial time") {
        val clock = TestClock()

        it("THEN now() returns Instant.EPOCH") {
            clock.now() shouldBe Instant.EPOCH
        }
    }

    describe("GIVEN a TestClock with a custom initial time") {
        val customTime = Instant.parse("2026-01-15T10:30:00Z")
        val clock = TestClock(initialTime = customTime)

        it("THEN now() returns the custom initial time") {
            clock.now() shouldBe customTime
        }
    }

    describe("GIVEN a TestClock at EPOCH") {
        describe("WHEN advance is called with 5 minutes") {
            val clock = TestClock()
            clock.advance(5.minutes)

            it("THEN now() returns EPOCH + 5 minutes") {
                clock.now() shouldBe Instant.EPOCH.plusSeconds(300)
            }
        }

        describe("WHEN advance is called twice (5 minutes then 2 hours)") {
            val clock = TestClock()
            clock.advance(5.minutes)
            clock.advance(2.hours)

            it("THEN now() returns the cumulative advancement") {
                clock.now() shouldBe Instant.EPOCH.plusSeconds(300 + 7200)
            }
        }
    }

    describe("GIVEN a TestClock") {
        describe("WHEN set() is called with a specific instant") {
            val targetTime = Instant.parse("2026-06-15T12:00:00Z")
            val clock = TestClock()
            clock.set(targetTime)

            it("THEN now() returns that exact instant") {
                clock.now() shouldBe targetTime
            }
        }

        describe("WHEN set() is called after advance()") {
            val targetTime = Instant.parse("2026-06-15T12:00:00Z")
            val clock = TestClock()
            clock.advance(10.seconds)
            clock.set(targetTime)

            it("THEN now() returns the set instant (overriding previous advance)") {
                clock.now() shouldBe targetTime
            }
        }
    }
})
