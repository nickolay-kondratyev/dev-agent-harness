package com.glassthought.shepherd.core.context

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files
import kotlin.io.path.readText

/**
 * Keyword presence tests for execution agent (doer/reviewer) instructions.
 *
 * These tests verify that assembled instruction files contain all required protocol keywords
 * from [ProtocolVocabulary]. Tests use `shouldContain` for individual keywords — not brittle
 * exact-match. Rewording a paragraph won't break the test. Only removing a protocol keyword will.
 */
class ExecutionAgentInstructionsKeywordTest : AsgardDescribeSpec({

    describe("GIVEN a doer instruction request on iteration 1") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("doer-keyword-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN instructions are assembled") {
            val instructionsPath = provider.assembleInstructions(request)
            val text = instructionsPath.readText()

            // -- Callback signal script --
            it("THEN contains callback signal script name") {
                text shouldContain ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT
            }

            it("THEN contains 'done' signal") {
                text shouldContain ProtocolVocabulary.Signal.DONE
            }

            it("THEN contains 'completed' done result for doer") {
                text shouldContain ProtocolVocabulary.DoneResult.COMPLETED
            }

            it("THEN contains 'fail-workflow' signal") {
                text shouldContain ProtocolVocabulary.Signal.FAIL_WORKFLOW
            }

            it("THEN contains 'user-question' signal") {
                text shouldContain ProtocolVocabulary.Signal.USER_QUESTION
            }

            it("THEN contains 'ack-payload' signal") {
                text shouldContain ProtocolVocabulary.Signal.ACK_PAYLOAD
            }

            it("THEN contains 'ping-ack' signal") {
                text shouldContain ProtocolVocabulary.Signal.PING_ACK
            }

            it("THEN contains payload ACK XML tag") {
                text shouldContain ProtocolVocabulary.PAYLOAD_ACK_TAG
            }

            // -- WHY-NOT keyword --
            it("THEN contains WHY-NOT keyword") {
                text shouldContain ProtocolVocabulary.WHY_NOT
            }

            // -- Role and ticket --
            it("THEN contains role name") {
                text shouldContain "IMPLEMENTOR"
            }

            it("THEN contains ticket content") {
                text shouldContain "Implement feature X"
            }

            it("THEN contains part name") {
                text shouldContain request.executionContext.partName
            }

            // -- Doer callback shows 'completed', not reviewer results --
            it("THEN contains callback example with 'completed' (not 'pass' or 'needs_iteration')") {
                // The callback script section for doers shows only 'done completed'.
                // PUBLIC_MD_WRITING_GUIDELINES may mention pass/needs_iteration in context,
                // but the callback script usage section should show the correct done result.
                text shouldContain "${ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT} ${ProtocolVocabulary.Signal.DONE} ${ProtocolVocabulary.DoneResult.COMPLETED}"
            }

            // -- No plan validation query for execution agents --
            it("THEN does NOT contain query script (execution agents have no queries)") {
                text shouldNotContain ProtocolVocabulary.CALLBACK_QUERY_SCRIPT
            }
        }
    }

    describe("GIVEN a reviewer instruction request on iteration 2 with feedback") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("reviewer-keyword-test")
        val request = ContextTestFixtures.reviewerInstructionRequestWithFeedback(tempDir)

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

            // -- Feedback status keywords --
            it("THEN contains 'unaddressed' feedback status") {
                text shouldContain ProtocolVocabulary.FeedbackStatus.UNADDRESSED
            }

            it("THEN contains 'addressed' feedback status") {
                text shouldContain ProtocolVocabulary.FeedbackStatus.ADDRESSED
            }

            it("THEN contains 'rejected' feedback status") {
                text shouldContain ProtocolVocabulary.FeedbackStatus.REJECTED
            }

            // -- Severity keywords --
            it("THEN contains 'critical' severity") {
                text shouldContain ProtocolVocabulary.Severity.CRITICAL
            }

            it("THEN contains 'important' severity") {
                text shouldContain ProtocolVocabulary.Severity.IMPORTANT
            }

            it("THEN contains 'optional' severity") {
                text shouldContain ProtocolVocabulary.Severity.OPTIONAL
            }

            // -- Feedback file protocol --
            it("THEN contains Movement Log reference") {
                text shouldContain ProtocolVocabulary.MOVEMENT_LOG
            }

            // -- WHY-NOT protocol in structured feedback --
            it("THEN contains WHY-NOT in feedback format") {
                text shouldContain ProtocolVocabulary.WHY_NOT
            }

            // -- Feedback file content from test fixtures --
            it("THEN contains addressed feedback file content") {
                text shouldContain "Race condition in session manager"
            }

            it("THEN contains rejected feedback file content") {
                text shouldContain "CoroutineScope instead of GlobalScope"
            }

            // -- Doer output included for review --
            it("THEN contains doer's PUBLIC.md content") {
                text shouldContain "Implemented feature X"
            }
        }
    }
})
