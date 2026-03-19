package com.glassthought.shepherd.core.state

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class SubPartRoleTest : AsgardDescribeSpec({

    describe("GIVEN valid sub-part indices") {

        describe("WHEN fromIndex(0)") {
            val role = SubPartRole.fromIndex(0)

            it("THEN returns DOER") {
                role shouldBe SubPartRole.DOER
            }
        }

        describe("WHEN fromIndex(1)") {
            val role = SubPartRole.fromIndex(1)

            it("THEN returns REVIEWER") {
                role shouldBe SubPartRole.REVIEWER
            }
        }
    }

    describe("GIVEN invalid sub-part indices") {

        describe("WHEN fromIndex(2)") {
            it("THEN throws IllegalArgumentException") {
                val exception = shouldThrow<IllegalArgumentException> {
                    SubPartRole.fromIndex(2)
                }
                exception.message shouldContain "subPartIndex=[2]"
            }
        }

        describe("WHEN fromIndex(-1)") {
            it("THEN throws IllegalArgumentException") {
                val exception = shouldThrow<IllegalArgumentException> {
                    SubPartRole.fromIndex(-1)
                }
                exception.message shouldContain "subPartIndex=[-1]"
            }
        }
    }

    // Data-driven: all valid indices map correctly
    describe("GIVEN all valid index-to-role mappings") {
        val expectedMappings = listOf(
            0 to SubPartRole.DOER,
            1 to SubPartRole.REVIEWER,
        )

        expectedMappings.forEach { (index, expectedRole) ->
            describe("WHEN fromIndex($index)") {
                it("THEN returns $expectedRole") {
                    SubPartRole.fromIndex(index) shouldBe expectedRole
                }
            }
        }
    }
})
