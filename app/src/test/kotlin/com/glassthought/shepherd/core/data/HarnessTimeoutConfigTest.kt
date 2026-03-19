package com.glassthought.shepherd.core.data

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HarnessTimeoutConfigTest : AsgardDescribeSpec({

    describe("GIVEN HarnessTimeoutConfig.defaults()") {
        val config = HarnessTimeoutConfig.defaults()

        describe("WHEN inspecting healthTimeouts ladder") {

            it("THEN startup is 3 minutes") {
                config.healthTimeouts.startup shouldBe 3.minutes
            }

            it("THEN normalActivity is 30 minutes") {
                config.healthTimeouts.normalActivity shouldBe 30.minutes
            }

            it("THEN pingResponse is 3 minutes") {
                config.healthTimeouts.pingResponse shouldBe 3.minutes
            }
        }

        it("THEN healthCheckInterval is 5 minutes") {
            config.healthCheckInterval shouldBe 5.minutes
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

        describe("WHEN inspecting healthTimeouts ladder") {

            it("THEN startup is 1 second") {
                config.healthTimeouts.startup shouldBe 1.seconds
            }

            it("THEN normalActivity is 5 seconds") {
                config.healthTimeouts.normalActivity shouldBe 5.seconds
            }

            it("THEN pingResponse is 1 second") {
                config.healthTimeouts.pingResponse shouldBe 1.seconds
            }
        }

        it("THEN healthCheckInterval is 1 second") {
            config.healthCheckInterval shouldBe 1.seconds
        }

        it("THEN payloadAckTimeout is 2 seconds") {
            config.payloadAckTimeout shouldBe 2.seconds
        }

        it("THEN selfCompactionTimeout is shorter than production default") {
            config.selfCompactionTimeout shouldBe 3.seconds
        }

        it("THEN contextWindowSoftThresholdPct retains production value") {
            config.contextWindowSoftThresholdPct shouldBe 35
        }

        it("THEN contextWindowHardThresholdPct retains production value") {
            config.contextWindowHardThresholdPct shouldBe 20
        }
    }

    describe("GIVEN HealthTimeoutLadder with default values") {
        val ladder = HealthTimeoutLadder()

        it("THEN startup defaults to 3 minutes") {
            ladder.startup shouldBe 3.minutes
        }

        it("THEN normalActivity defaults to 30 minutes") {
            ladder.normalActivity shouldBe 30.minutes
        }

        it("THEN pingResponse defaults to 3 minutes") {
            ladder.pingResponse shouldBe 3.minutes
        }
    }
})
