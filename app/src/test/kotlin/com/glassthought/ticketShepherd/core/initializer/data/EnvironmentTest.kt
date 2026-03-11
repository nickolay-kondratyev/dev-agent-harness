package com.glassthought.ticketShepherd.core.initializer.data

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.shouldBe

class EnvironmentTest : AsgardDescribeSpec({

    describe("GIVEN Environment.production()") {
        val env = Environment.production()

        describe("WHEN isTest is checked") {
            it("THEN isTest is false") {
                env.isTest shouldBe false
            }
        }
    }

    describe("GIVEN TestEnvironment") {
        val env = TestEnvironment()

        describe("WHEN isTest is checked") {
            it("THEN isTest is true") {
                env.isTest shouldBe true
            }
        }
    }

    describe("GIVEN Environment.test()") {
        val env = Environment.test()

        describe("WHEN isTest is checked") {
            it("THEN isTest is true") {
                env.isTest shouldBe true
            }
        }
    }
})
