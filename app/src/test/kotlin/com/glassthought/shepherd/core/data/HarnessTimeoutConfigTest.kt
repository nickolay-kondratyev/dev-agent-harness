package com.glassthought.shepherd.core.data

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HarnessTimeoutConfigTest : AsgardDescribeSpec({

    describe("GIVEN HarnessTimeoutConfig.defaults()") {
        val config = HarnessTimeoutConfig.defaults()

        it("THEN startupAckTimeout is 3 minutes") {
            config.startupAckTimeout shouldBe 3.minutes
        }

        it("THEN healthCheckInterval is 5 minutes") {
            config.healthCheckInterval shouldBe 5.minutes
        }

        it("THEN noActivityTimeout is 30 minutes") {
            config.noActivityTimeout shouldBe 30.minutes
        }

        it("THEN pingTimeout is 3 minutes") {
            config.pingTimeout shouldBe 3.minutes
        }

        it("THEN payloadAckTimeout is 3 minutes") {
            config.payloadAckTimeout shouldBe 3.minutes
        }

        it("THEN payloadAckMaxAttempts is 3") {
            config.payloadAckMaxAttempts shouldBe 3
        }

        it("THEN selfCompactionTimeout is 5 minutes") {
            config.selfCompactionTimeout shouldBe 5.minutes
        }

        it("THEN contextWindowSoftThresholdPct is 35") {
            config.contextWindowSoftThresholdPct shouldBe 35
        }

        it("THEN contextWindowHardThresholdPct is 20") {
            config.contextWindowHardThresholdPct shouldBe 20
        }
    }

    describe("GIVEN HarnessTimeoutConfig.forTests()") {
        val config = HarnessTimeoutConfig.forTests()

        it("THEN startupAckTimeout is shorter than production default") {
            config.startupAckTimeout shouldNotBe 3.minutes
        }

        it("THEN noActivityTimeout is shorter than production default") {
            config.noActivityTimeout shouldNotBe 30.minutes
        }

        it("THEN selfCompactionTimeout is shorter than production default") {
            config.selfCompactionTimeout shouldNotBe 5.minutes
        }

        it("THEN contextWindowSoftThresholdPct retains production value") {
            config.contextWindowSoftThresholdPct shouldBe 35
        }

        it("THEN contextWindowHardThresholdPct retains production value") {
            config.contextWindowHardThresholdPct shouldBe 20
        }
    }
})
