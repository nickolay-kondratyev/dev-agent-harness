package com.glassthought.shepherd.coroutines

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

class VirtualTimeProofTest : AsgardDescribeSpec({

    describe("GIVEN kotlinx-coroutines-test virtual time") {

        describe("WHEN advanceTimeBy is called with 1000ms") {

            it("THEN currentTime reflects the advanced virtual time") {
                runTest {
                    advanceTimeBy(1000)
                    testScheduler.currentTime shouldBe 1000
                }
            }
        }
    }
})
