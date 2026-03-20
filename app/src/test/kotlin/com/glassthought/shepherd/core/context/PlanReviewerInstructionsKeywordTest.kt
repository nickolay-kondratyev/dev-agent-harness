package com.glassthought.shepherd.core.context

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import kotlin.io.path.readText

/**
 * Keyword presence tests for plan reviewer instructions.
 *
 * Verifies that plan reviewer instruction files contain all required protocol keywords
 * and plan-review-specific content (plan_flow.json, PLAN.md, planner rationale).
 */
class PlanReviewerInstructionsKeywordTest : AsgardDescribeSpec({

    describe("GIVEN a plan reviewer instruction request on iteration 1") {
        val provider = ContextForAgentProvider.standard(outFactory, ContextTestFixtures.TEST_AI_OUTPUT_STRUCTURE)
        val tempDir = Files.createTempDirectory("plan-reviewer-keyword-test")
        val request = ContextTestFixtures.planReviewerRequest(tempDir)

        describe("WHEN instructions are assembled") {
            val instructionsPath = provider.assembleInstructions(request)
            val text = instructionsPath.readText()

            // -- Reviewer-specific done results --
            it("THEN contains 'pass' done result") {
                text shouldContain ProtocolVocabulary.DoneResult.PASS
            }

            it("THEN contains 'needs_iteration' done result") {
                text shouldContain ProtocolVocabulary.DoneResult.NEEDS_ITERATION
            }

            // -- Callback scripts --
            it("THEN contains callback signal script name") {
                text shouldContain ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT
            }

            it("THEN contains callback query script name (plan reviewer validates plan)") {
                text shouldContain ProtocolVocabulary.CALLBACK_QUERY_SCRIPT
            }

            it("THEN contains 'validate-plan' query instruction") {
                text shouldContain "validate-plan"
            }

            it("THEN contains payload ACK tag") {
                text shouldContain ProtocolVocabulary.PAYLOAD_ACK_TAG
            }

            // -- Plan content --
            it("THEN contains plan_flow.json content") {
                text shouldContain request.planJsonContent
            }

            it("THEN contains PLAN.md content") {
                text shouldContain "Three-part implementation"
            }

            it("THEN contains agent types and models for validation") {
                text shouldContain "CLAUDE_CODE"
            }

            // -- Planner rationale --
            it("THEN contains planner's PUBLIC.md content") {
                text shouldContain "Chose 3-part approach"
            }

            // -- Role and ticket --
            it("THEN contains PLAN_REVIEWER role name") {
                text shouldContain "PLAN_REVIEWER"
            }

            it("THEN contains ticket content") {
                text shouldContain "Implement feature X"
            }
        }
    }
})
