package com.glassthought.shepherd.core.context

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files
import kotlin.io.path.readText

/**
 * Structural tests for [ContextForAgentProvider] — verifies that conditional sections
 * are correctly included or excluded based on request parameters.
 *
 * These tests complement the keyword tests. Keyword tests verify protocol vocabulary presence;
 * structural tests verify that the right sections appear for the right agent type and state.
 */
class ContextForAgentProviderAssemblyTest : AsgardDescribeSpec({

    describe("GIVEN a doer request with a plan (with-planning workflow)") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("assembly-plan-test")
        val baseRequest = ContextTestFixtures.doerRequest(tempDir)

        val planMdPath = tempDir.resolve("shared/plan/PLAN.md")
        Files.createDirectories(planMdPath.parent)
        Files.writeString(planMdPath, "# Plan\n\nStep 1: Do the thing.")

        val request = baseRequest.copy(planMdPath = planMdPath)

        describe("WHEN instructions are assembled") {
            val text = provider.assembleExecutionAgentInstructions(request).readText()

            it("THEN includes PLAN.md content") {
                text shouldContain "Step 1: Do the thing"
            }
        }
    }

    describe("GIVEN a doer request without a plan (no-planning workflow)") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("assembly-no-plan-test")
        val request = ContextTestFixtures.doerRequest(tempDir)

        describe("WHEN instructions are assembled") {
            val text = provider.assembleExecutionAgentInstructions(request).readText()

            it("THEN does NOT include plan section header") {
                text shouldNotContain "# Plan\n"
            }
        }
    }

    describe("GIVEN a doer request on iteration 1") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("assembly-doer-iter1-test")
        val request = ContextTestFixtures.doerRequest(tempDir)

        describe("WHEN instructions are assembled") {
            val text = provider.assembleExecutionAgentInstructions(request).readText()

            it("THEN does NOT include pushback guidance (first iteration)") {
                text shouldNotContain "Handling Reviewer Feedback"
            }

            it("THEN includes WHY-NOT reminder (all iterations)") {
                text shouldContain ProtocolVocabulary.WHY_NOT
            }
        }
    }

    describe("GIVEN a doer request on iteration 2 with reviewer feedback") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("assembly-doer-iter2-test")
        val baseRequest = ContextTestFixtures.doerRequest(tempDir)

        val reviewerPublicMd = tempDir.resolve("reviewer/comm/out/PUBLIC.md")
        Files.createDirectories(reviewerPublicMd.parent)
        Files.writeString(reviewerPublicMd, "# Review\n\nNeeds changes to error handling.")

        val request = baseRequest.copy(
            iterationNumber = 2,
            peerPublicMdPath = reviewerPublicMd,
        )

        describe("WHEN instructions are assembled") {
            val text = provider.assembleExecutionAgentInstructions(request).readText()

            it("THEN includes pushback guidance") {
                text shouldContain "Handling Reviewer Feedback"
            }

            it("THEN includes reviewer's PUBLIC.md content") {
                text shouldContain "Needs changes to error handling"
            }
        }
    }

    describe("GIVEN a reviewer request on iteration 1") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("assembly-reviewer-iter1-test")

        val doerPublicMd = tempDir.resolve("doer/comm/out/PUBLIC.md")
        Files.createDirectories(doerPublicMd.parent)
        Files.writeString(doerPublicMd, "# Implementation\n\nDone.")

        val outputDir = tempDir.resolve("reviewer/comm/in")
        Files.createDirectories(outputDir)
        val sharedContextPath = tempDir.resolve("SHARED_CONTEXT.md")
        Files.writeString(sharedContextPath, "Context.")
        val publicMdOutputPath = tempDir.resolve("reviewer/comm/out/PUBLIC.md")
        Files.createDirectories(publicMdOutputPath.parent)

        val request = ExecutionAgentInstructionRequest(
            roleDefinition = ContextTestFixtures.roleDefinition("REVIEWER"),
            partName = "part_1",
            partDescription = "Review impl",
            ticketContent = "Ticket.",
            sharedContextPath = sharedContextPath,
            planMdPath = null,
            priorPublicMdPaths = emptyList(),
            iterationNumber = 1,
            isReviewer = true,
            peerPublicMdPath = doerPublicMd,
            feedbackDir = null,
            outputDir = outputDir,
            publicMdOutputPath = publicMdOutputPath,
        )

        describe("WHEN instructions are assembled") {
            val text = provider.assembleExecutionAgentInstructions(request).readText()

            it("THEN includes structured feedback format guidance") {
                text shouldContain "Structured Feedback Format"
            }

            it("THEN includes feedback writing instructions") {
                text shouldContain "Writing Feedback Files"
            }

            it("THEN includes doer's output for review") {
                text shouldContain "Done."
            }

            it("THEN does NOT include addressed/rejected feedback headers (first iteration)") {
                text shouldNotContain "Addressed Feedback"
            }
        }
    }

    describe("GIVEN a reviewer request on iteration 2 with feedback state") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("assembly-reviewer-iter2-test")
        val request = ContextTestFixtures.reviewerRequestWithFeedback(tempDir)

        describe("WHEN instructions are assembled") {
            val text = provider.assembleExecutionAgentInstructions(request).readText()

            it("THEN includes addressed feedback header") {
                text shouldContain "Addressed Feedback"
            }

            it("THEN includes rejected feedback header") {
                text shouldContain "Rejected Feedback"
            }
        }
    }

    describe("GIVEN a doer request with prior PUBLIC.md files") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("assembly-prior-outputs-test")
        val baseRequest = ContextTestFixtures.doerRequest(tempDir)

        val priorPublicMd = tempDir.resolve("part_0/impl/comm/out/PUBLIC.md")
        Files.createDirectories(priorPublicMd.parent)
        Files.writeString(priorPublicMd, "# Prior Work\n\nSet up database schema.")

        val request = baseRequest.copy(priorPublicMdPaths = listOf(priorPublicMd))

        describe("WHEN instructions are assembled") {
            val text = provider.assembleExecutionAgentInstructions(request).readText()

            it("THEN includes prior PUBLIC.md content") {
                text shouldContain "Set up database schema"
            }

            it("THEN includes Prior Agent Outputs header") {
                text shouldContain "Prior Agent Outputs"
            }
        }
    }

    describe("GIVEN instructions are assembled for any agent") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("assembly-file-test")
        val request = ContextTestFixtures.doerRequest(tempDir)

        describe("WHEN the file is written") {
            val path = provider.assembleExecutionAgentInstructions(request)

            it("THEN the file is named instructions.md") {
                path.fileName.toString() shouldContain "instructions.md"
            }

            it("THEN sections are separated by horizontal rules") {
                path.readText() shouldContain "---"
            }
        }
    }
})
