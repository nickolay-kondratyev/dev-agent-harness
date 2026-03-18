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
        val baseRequest = ContextTestFixtures.doerInstructionRequest(tempDir)

        val planMdPath = tempDir.resolve("shared/plan/PLAN.md")
        Files.createDirectories(planMdPath.parent)
        Files.writeString(planMdPath, "# Plan\n\nStep 1: Do the thing.")

        val request = baseRequest.copy(
            executionContext = baseRequest.executionContext.copy(planMdPath = planMdPath),
        )

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(request).readText()

            it("THEN includes PLAN.md content") {
                text shouldContain "Step 1: Do the thing"
            }
        }
    }

    describe("GIVEN a doer request without a plan (no-planning workflow)") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("assembly-no-plan-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(request).readText()

            it("THEN does NOT include plan section header") {
                text shouldNotContain "# Plan\n"
            }
        }
    }

    describe("GIVEN a doer request on iteration 1") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("assembly-doer-iter1-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(request).readText()

            it("THEN does NOT include pushback guidance (first iteration)") {
                text shouldNotContain "Handling Reviewer Feedback"
            }

            it("THEN contains WHY-NOT keyword") {
                text shouldContain ProtocolVocabulary.WHY_NOT
            }
        }
    }

    describe("GIVEN a doer request on iteration 2 with reviewer feedback") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("assembly-doer-iter2-test")
        val baseRequest = ContextTestFixtures.doerInstructionRequest(tempDir)

        val reviewerPublicMd = tempDir.resolve("reviewer/comm/out/PUBLIC.md")
        Files.createDirectories(reviewerPublicMd.parent)
        Files.writeString(reviewerPublicMd, "# Review\n\nNeeds changes to error handling.")

        val request = baseRequest.copy(
            iterationNumber = 2,
            reviewerPublicMdPath = reviewerPublicMd,
        )

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(request).readText()

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
        val request = ContextTestFixtures.reviewerInstructionRequest(tempDir)

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(request).readText()

            it("THEN includes structured feedback format guidance") {
                text shouldContain "Structured Feedback Format"
            }

            it("THEN includes feedback writing instructions") {
                text shouldContain "Writing Feedback Files"
            }

            it("THEN includes doer's output for review") {
                text shouldContain "Implemented feature X"
            }

            it("THEN does NOT include addressed/rejected feedback headers (first iteration)") {
                text shouldNotContain "Addressed Feedback"
            }
        }
    }

    describe("GIVEN a reviewer request on iteration 2 with feedback state") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("assembly-reviewer-iter2-test")
        val request = ContextTestFixtures.reviewerInstructionRequestWithFeedback(tempDir)

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(request).readText()

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
        val baseRequest = ContextTestFixtures.doerInstructionRequest(tempDir)

        val priorPublicMd = tempDir.resolve("part_0/impl/comm/out/PUBLIC.md")
        Files.createDirectories(priorPublicMd.parent)
        Files.writeString(priorPublicMd, "# Prior Work\n\nSet up database schema.")

        val request = baseRequest.copy(
            executionContext = baseRequest.executionContext.copy(
                priorPublicMdPaths = listOf(priorPublicMd),
            ),
        )

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(request).readText()

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
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN the file is written") {
            val path = provider.assembleInstructions(request)

            it("THEN the file is named instructions.md") {
                path.fileName.toString() shouldContain "instructions.md"
            }

            it("THEN sections are separated by horizontal rules") {
                path.readText() shouldContain "---"
            }
        }
    }

    // -- PrivateMd tests --

    describe("GIVEN a doer request with PRIVATE.md present") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("assembly-privatemd-present-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        // outputDir = tempDir/comm/in -> parent.parent = tempDir
        val privateMdDir = tempDir.resolve("private")
        Files.createDirectories(privateMdDir)
        Files.writeString(privateMdDir.resolve("PRIVATE.md"), "Session context from prior run.")

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(request).readText()

            it("THEN output contains PRIVATE.md content") {
                text shouldContain "Session context from prior run"
            }

            it("THEN output contains Prior Session Context header") {
                text shouldContain "Prior Session Context (PRIVATE.md)"
            }
        }
    }

    describe("GIVEN a doer request without PRIVATE.md") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("assembly-privatemd-absent-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(request).readText()

            it("THEN output does NOT contain Prior Session Context header") {
                text shouldNotContain "Prior Session Context"
            }
        }
    }

    describe("GIVEN a planner request with PRIVATE.md present") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("assembly-planner-privatemd-test")
        val request = ContextTestFixtures.plannerRequest(tempDir)

        // outputDir = tempDir/planner/comm/in -> parent.parent = tempDir/planner
        val privateMdDir = tempDir.resolve("planner/private")
        Files.createDirectories(privateMdDir)
        Files.writeString(privateMdDir.resolve("PRIVATE.md"), "Planner session context.")

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(request).readText()

            it("THEN output contains PRIVATE.md content") {
                text shouldContain "Planner session context"
            }
        }
    }
})
