package com.glassthought.shepherd.core.context

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import io.kotest.matchers.shouldBe
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
        val provider = ContextForAgentProvider.standard(outFactory, ContextTestFixtures.TEST_AI_OUTPUT_STRUCTURE)
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
        val provider = ContextForAgentProvider.standard(outFactory, ContextTestFixtures.TEST_AI_OUTPUT_STRUCTURE)
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
        val provider = ContextForAgentProvider.standard(outFactory, ContextTestFixtures.TEST_AI_OUTPUT_STRUCTURE)
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
        val provider = ContextForAgentProvider.standard(outFactory, ContextTestFixtures.TEST_AI_OUTPUT_STRUCTURE)
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

    // -- DoerFeedbackItemRequest tests --

    describe("GIVEN a doer feedback item request") {
        val provider = ContextForAgentProvider.standard(outFactory, ContextTestFixtures.TEST_AI_OUTPUT_STRUCTURE)
        val tempDir = Files.createTempDirectory("assembly-doer-feedback-item-test")
        val request = ContextTestFixtures.doerFeedbackItemRequest(tempDir)

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(request).readText()

            it("THEN includes feedback item heading") {
                text shouldContain "Feedback Item to Address"
            }

            it("THEN includes the feedback content") {
                text shouldContain "Add null check before accessing result"
            }

            it("THEN includes feedback file path") {
                text shouldContain "critical__missing-null-check.md"
            }

            it("THEN includes resolution instructions for addressed") {
                text shouldContain "Resolution: addressed"
            }

            it("THEN includes resolution instructions for rejected") {
                text shouldContain "Resolution: rejected"
            }

            it("THEN does NOT include IterationFeedback (reviewer PUBLIC.md)") {
                text shouldNotContain "Reviewer Feedback"
            }
        }
    }

    describe("GIVEN a doer feedback item request with optional feedback") {
        val provider = ContextForAgentProvider.standard(outFactory, ContextTestFixtures.TEST_AI_OUTPUT_STRUCTURE)
        val tempDir = Files.createTempDirectory("assembly-doer-feedback-optional-test")
        val baseRequest = ContextTestFixtures.doerFeedbackItemRequest(tempDir)

        val optionalFeedbackFile = tempDir.resolve("feedback/pending/optional__style-nit.md")
        Files.createDirectories(optionalFeedbackFile.parent)
        Files.writeString(optionalFeedbackFile, "Consider renaming variable.")

        val request = baseRequest.copy(
            feedbackItem = InstructionSection.FeedbackItem(
                feedbackContent = "Consider renaming variable.",
                currentPath = optionalFeedbackFile,
                isOptional = true,
            ),
        )

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(request).readText()

            it("THEN includes optional note with skipped guidance") {
                text shouldContain "skipped"
            }

            it("THEN includes optional severity note") {
                text shouldContain "optional"
            }
        }
    }

    describe("GIVEN a reviewer request on iteration 1") {
        val provider = ContextForAgentProvider.standard(outFactory, ContextTestFixtures.TEST_AI_OUTPUT_STRUCTURE)
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
        val provider = ContextForAgentProvider.standard(outFactory, ContextTestFixtures.TEST_AI_OUTPUT_STRUCTURE)
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

            it("THEN includes skipped optional feedback section") {
                text shouldContain "consider-logging-improvement"
            }

            it("THEN includes skipped optional feedback header") {
                text shouldContain "Skipped ${ProtocolVocabulary.Severity.OPTIONAL} Feedback"
            }
        }
    }

    describe("GIVEN a doer request with prior PUBLIC.md files") {
        val provider = ContextForAgentProvider.standard(outFactory, ContextTestFixtures.TEST_AI_OUTPUT_STRUCTURE)
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
        val provider = ContextForAgentProvider.standard(outFactory, ContextTestFixtures.TEST_AI_OUTPUT_STRUCTURE)
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
    // Path resolution now happens via AiOutputStructure. The TEST_AI_OUTPUT_STRUCTURE uses
    // repoRoot=/tmp/test-repo, branch=test-branch. For execution requests with
    // partName=part_1_implementation, subPartName=impl, the resolved path is:
    // /tmp/test-repo/.ai_out/test-branch/execution/part_1_implementation/impl/private/PRIVATE.md

    describe("GIVEN a doer request where AiOutputStructure-resolved PRIVATE.md exists with content") {
        // Build AiOutputStructure with tempDir as repoRoot so we can create the PRIVATE.md file
        val tempDir = Files.createTempDirectory("assembly-privatemd-present-test")
        val aiOutputStructure = AiOutputStructure(repoRoot = tempDir, branch = "test-branch")
        val provider = ContextForAgentProvider.standard(outFactory, aiOutputStructure)
        val baseRequest = ContextTestFixtures.doerInstructionRequest(tempDir)

        // Create the PRIVATE.md at the path AiOutputStructure will resolve
        val privateMdFile = aiOutputStructure.executionPrivateMd(
            baseRequest.executionContext.partName, baseRequest.subPartName,
        )
        Files.createDirectories(privateMdFile.parent)
        Files.writeString(privateMdFile, "Session context from prior run.")

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(baseRequest).readText()

            it("THEN output contains PRIVATE.md content") {
                text shouldContain "Session context from prior run"
            }

            it("THEN output contains Prior Session Context header") {
                text shouldContain "Prior Session Context (PRIVATE.md)"
            }

            it("THEN PRIVATE.md content appears after role definition") {
                val roleIdx = text.indexOf("# Role:")
                val privateIdx = text.indexOf("Prior Session Context (PRIVATE.md)")
                val partIdx = text.indexOf("# Part Context")
                (roleIdx < privateIdx) shouldBe true
                (privateIdx < partIdx) shouldBe true
            }
        }
    }

    describe("GIVEN a doer request where AiOutputStructure-resolved PRIVATE.md does not exist") {
        val provider = ContextForAgentProvider.standard(outFactory, ContextTestFixtures.TEST_AI_OUTPUT_STRUCTURE)
        val tempDir = Files.createTempDirectory("assembly-privatemd-nonexistent-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(request).readText()

            it("THEN output does NOT contain Prior Session Context header") {
                text shouldNotContain "Prior Session Context"
            }
        }
    }

    describe("GIVEN a doer request where AiOutputStructure-resolved PRIVATE.md is empty") {
        val tempDir = Files.createTempDirectory("assembly-privatemd-empty-test")
        val aiOutputStructure = AiOutputStructure(repoRoot = tempDir, branch = "test-branch")
        val provider = ContextForAgentProvider.standard(outFactory, aiOutputStructure)
        val baseRequest = ContextTestFixtures.doerInstructionRequest(tempDir)

        val privateMdFile = aiOutputStructure.executionPrivateMd(
            baseRequest.executionContext.partName, baseRequest.subPartName,
        )
        Files.createDirectories(privateMdFile.parent)
        Files.writeString(privateMdFile, "")

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(baseRequest).readText()

            it("THEN output does NOT contain Prior Session Context header") {
                text shouldNotContain "Prior Session Context"
            }
        }
    }

    describe("GIVEN a planner request where AiOutputStructure-resolved PRIVATE.md exists") {
        val tempDir = Files.createTempDirectory("assembly-planner-privatemd-test")
        val aiOutputStructure = AiOutputStructure(repoRoot = tempDir, branch = "test-branch")
        val provider = ContextForAgentProvider.standard(outFactory, aiOutputStructure)
        val baseRequest = ContextTestFixtures.plannerRequest(tempDir)

        // Planning path: planningPrivateMd(subPartName)
        val privateMdFile = aiOutputStructure.planningPrivateMd(baseRequest.subPartName)
        Files.createDirectories(privateMdFile.parent)
        Files.writeString(privateMdFile, "Planner session context.")

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(baseRequest).readText()

            it("THEN output contains PRIVATE.md content") {
                text shouldContain "Planner session context"
            }

            it("THEN PRIVATE.md content appears after role definition and before ticket") {
                val roleIdx = text.indexOf("# Role:")
                val privateIdx = text.indexOf("Prior Session Context (PRIVATE.md)")
                val ticketIdx = text.indexOf("# Ticket")
                (roleIdx < privateIdx) shouldBe true
                (privateIdx < ticketIdx) shouldBe true
            }
        }
    }

    describe("GIVEN execution vs planning requests") {
        val tempDir = Files.createTempDirectory("assembly-path-resolution-test")
        val aiOutputStructure = AiOutputStructure(repoRoot = tempDir, branch = "test-branch")
        val provider = ContextForAgentProvider.standard(outFactory, aiOutputStructure)

        describe("WHEN a doer request resolves PRIVATE.md") {
            val doerRequest = ContextTestFixtures.doerInstructionRequest(tempDir)
            val expectedPath = aiOutputStructure.executionPrivateMd(
                doerRequest.executionContext.partName, doerRequest.subPartName,
            )
            Files.createDirectories(expectedPath.parent)
            Files.writeString(expectedPath, "Doer private context.")

            val text = provider.assembleInstructions(doerRequest).readText()

            it("THEN execution PRIVATE.md path is used (content appears)") {
                text shouldContain "Doer private context."
            }
        }

        describe("WHEN a planner request resolves PRIVATE.md") {
            val plannerRequest = ContextTestFixtures.plannerRequest(tempDir)
            val expectedPath = aiOutputStructure.planningPrivateMd(plannerRequest.subPartName)
            Files.createDirectories(expectedPath.parent)
            Files.writeString(expectedPath, "Planner private context.")

            val text = provider.assembleInstructions(plannerRequest).readText()

            it("THEN planning PRIVATE.md path is used (content appears)") {
                text shouldContain "Planner private context."
            }
        }
    }
})
