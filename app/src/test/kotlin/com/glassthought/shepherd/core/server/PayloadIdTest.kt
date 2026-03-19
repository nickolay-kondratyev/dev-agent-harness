package com.glassthought.shepherd.core.server

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicInteger

class PayloadIdTest : AsgardDescribeSpec({

    val handshakeGuid = HandshakeGuid("handshake.a1b2c3d4-e5f6-7890-abcd-ef1234567890")

    describe("GIVEN a HandshakeGuid and counter starting at 1") {
        val counter = AtomicInteger(1)

        describe("WHEN generate is called") {
            val payloadId = PayloadId.generate(handshakeGuid, counter)

            it("THEN produces correct format {8chars}-{seq}") {
                payloadId.value shouldBe "a1b2c3d4-1"
            }

            it("THEN toString returns raw value") {
                payloadId.toString() shouldBe "a1b2c3d4-1"
            }
        }
    }

    describe("GIVEN a HandshakeGuid and counter starting at 1 for sequential calls") {
        val counter = AtomicInteger(1)

        describe("WHEN generate is called three times") {
            val first = PayloadId.generate(handshakeGuid, counter)
            val second = PayloadId.generate(handshakeGuid, counter)
            val third = PayloadId.generate(handshakeGuid, counter)

            it("THEN first payload has sequence 1") {
                first.value shouldBe "a1b2c3d4-1"
            }

            it("THEN second payload has sequence 2") {
                second.value shouldBe "a1b2c3d4-2"
            }

            it("THEN third payload has sequence 3") {
                third.value shouldBe "a1b2c3d4-3"
            }
        }
    }

    describe("GIVEN a HandshakeGuid with handshake. prefix") {
        val counter = AtomicInteger(1)

        describe("WHEN generate is called") {
            val payloadId = PayloadId.generate(handshakeGuid, counter)

            it("THEN shortGuid is first 8 chars after prefix removal") {
                payloadId.value.substringBefore("-") shouldBe "a1b2c3d4"
            }
        }
    }
})
