package com.glassthought.shepherd.core.git

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.supporting.git.CommitMessageBuilder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class CommitMessageBuilderTest : AsgardDescribeSpec({

    describe("GIVEN build") {

        describe("WHEN called without reviewer (hasReviewer=false)") {

            describe("AND partName='planning', subPartName='plan', result='completed'") {
                val result = CommitMessageBuilder.build(
                    partName = "planning",
                    subPartName = "plan",
                    result = "completed",
                    hasReviewer = false,
                )

                it("THEN returns '[shepherd] planning/plan — completed'") {
                    result shouldBe "[shepherd] planning/plan — completed"
                }
            }

            describe("AND partName='backend_impl', subPartName='impl', result='completed'") {
                val result = CommitMessageBuilder.build(
                    partName = "backend_impl",
                    subPartName = "impl",
                    result = "completed",
                    hasReviewer = false,
                )

                it("THEN returns '[shepherd] backend_impl/impl — completed'") {
                    result shouldBe "[shepherd] backend_impl/impl — completed"
                }
            }
}

        describe("WHEN called with reviewer (hasReviewer=true)") {

            describe("AND partName='planning', subPartName='plan_review', result='pass', iteration 1/3") {
                val result = CommitMessageBuilder.build(
                    partName = "planning",
                    subPartName = "plan_review",
                    result = "pass",
                    hasReviewer = true,
                    currentIteration = 1,
                    maxIterations = 3,
                )

                it("THEN returns '[shepherd] planning/plan_review — pass (iteration 1/3)'") {
                    result shouldBe "[shepherd] planning/plan_review — pass (iteration 1/3)"
                }
            }

            describe("AND partName='ui_design', subPartName='impl', result='completed', iteration 1/3") {
                val result = CommitMessageBuilder.build(
                    partName = "ui_design",
                    subPartName = "impl",
                    result = "completed",
                    hasReviewer = true,
                    currentIteration = 1,
                    maxIterations = 3,
                )

                it("THEN returns '[shepherd] ui_design/impl — completed (iteration 1/3)'") {
                    result shouldBe "[shepherd] ui_design/impl — completed (iteration 1/3)"
                }
            }

            describe("AND partName='ui_design', subPartName='review', result='needs_iteration', iteration 1/3") {
                val result = CommitMessageBuilder.build(
                    partName = "ui_design",
                    subPartName = "review",
                    result = "needs_iteration",
                    hasReviewer = true,
                    currentIteration = 1,
                    maxIterations = 3,
                )

                it("THEN returns '[shepherd] ui_design/review — needs_iteration (iteration 1/3)'") {
                    result shouldBe "[shepherd] ui_design/review — needs_iteration (iteration 1/3)"
                }
            }

            describe("AND iteration 2/3") {
                val result = CommitMessageBuilder.build(
                    partName = "ui_design",
                    subPartName = "impl",
                    result = "completed",
                    hasReviewer = true,
                    currentIteration = 2,
                    maxIterations = 3,
                )

                it("THEN includes iteration 2/3 in the message") {
                    result shouldBe "[shepherd] ui_design/impl — completed (iteration 2/3)"
                }
            }
        }

        describe("WHEN called with invalid inputs") {

            it("THEN throws IllegalArgumentException for blank partName") {
                shouldThrow<IllegalArgumentException> {
                    CommitMessageBuilder.build(
                        partName = "  ",
                        subPartName = "impl",
                        result = "completed",
                        hasReviewer = false,
                    )
                }
            }

            it("THEN throws IllegalArgumentException for blank subPartName") {
                shouldThrow<IllegalArgumentException> {
                    CommitMessageBuilder.build(
                        partName = "planning",
                        subPartName = "",
                        result = "completed",
                        hasReviewer = false,
                    )
                }
            }

            it("THEN throws IllegalArgumentException for blank result") {
                shouldThrow<IllegalArgumentException> {
                    CommitMessageBuilder.build(
                        partName = "planning",
                        subPartName = "plan",
                        result = "  ",
                        hasReviewer = false,
                    )
                }
            }

            it("THEN throws IllegalArgumentException for currentIteration < 1 when hasReviewer=true") {
                shouldThrow<IllegalArgumentException> {
                    CommitMessageBuilder.build(
                        partName = "planning",
                        subPartName = "plan",
                        result = "completed",
                        hasReviewer = true,
                        currentIteration = 0,
                        maxIterations = 3,
                    )
                }
            }

            it("THEN throws IllegalArgumentException for maxIterations < 1 when hasReviewer=true") {
                shouldThrow<IllegalArgumentException> {
                    CommitMessageBuilder.build(
                        partName = "planning",
                        subPartName = "plan",
                        result = "completed",
                        hasReviewer = true,
                        currentIteration = 1,
                        maxIterations = 0,
                    )
                }
            }
        }
    }
})
