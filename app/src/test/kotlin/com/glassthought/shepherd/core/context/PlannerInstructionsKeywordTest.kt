package com.glassthought.shepherd.core.context

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import kotlin.io.path.readText

/**
 * Keyword presence tests for planner instructions.
 *
 * Verifies that planner instruction files contain all required protocol keywords
 * and planner-specific content (role catalog, agent types, plan format).
 */
class PlannerInstructionsKeywordTest : AsgardDescribeSpec({

    describe("GIVEN a planner instruction request on iteration 1") {
        val provider = ContextForAgentProvider.standard(outFactory, ContextTestFixtures.TEST_AI_OUTPUT_STRUCTURE)
        val tempDir = Files.createTempDirectory("planner-keyword-test")
        val request = ContextTestFixtures.plannerRequest(tempDir)

        describe("WHEN instructions are assembled") {
            val instructionsPath = provider.assembleInstructions(request)
            val text = instructionsPath.readText()

            // -- Callback scripts --
            it("THEN contains callback signal script name") {
                text shouldContain ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT
            }

            it("THEN contains callback query script name (planner validates plan)") {
                text shouldContain ProtocolVocabulary.CALLBACK_QUERY_SCRIPT
            }

            it("THEN contains 'validate-plan' query instruction") {
                text shouldContain "validate-plan"
            }

            it("THEN contains 'completed' done result") {
                text shouldContain ProtocolVocabulary.DoneResult.COMPLETED
            }

            it("THEN contains payload ACK tag") {
                text shouldContain ProtocolVocabulary.PAYLOAD_ACK_TAG
            }

            // -- Planner-specific content --
            it("THEN contains role catalog with IMPLEMENTOR") {
                text shouldContain "IMPLEMENTOR"
            }

            it("THEN contains role catalog with REVIEWER") {
                text shouldContain "REVIEWER"
            }

            it("THEN contains agent types and models section") {
                text shouldContain "ClaudeCode"
            }

            it("THEN contains plan format instructions") {
                text shouldContain "plan_flow.json"
            }

            it("THEN contains plan_flow.json output path") {
                text shouldContain request.planJsonOutputPath.toString()
            }

            it("THEN contains PLAN.md output path") {
                text shouldContain request.planMdOutputPath.toString()
            }

            // -- Role and ticket --
            it("THEN contains PLANNER role name") {
                text shouldContain "PLANNER"
            }

            it("THEN contains ticket content") {
                text shouldContain "Implement feature X"
            }
        }
    }
})
