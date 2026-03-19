package com.glassthought.shepherd.core.context

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import kotlin.io.path.readText

/**
 * Verifies that sections appear in spec-defined order for each agent role.
 *
 * Each test assembles a full instruction file via [ContextForAgentProvider.standard] and
 * asserts that key sections appear in the expected relative order using index-of checks.
 * This catches accidental reordering of the plan lists in [ContextForAgentProviderImpl].
 *
 * See ContextForAgentProvider spec (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) for authoritative
 * concatenation tables.
 */
class InstructionSectionOrderingTest : AsgardDescribeSpec({

    describe("GIVEN a doer request (iteration 2 with reviewer feedback and plan)") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("ordering-doer-test")
        val baseRequest = ContextTestFixtures.doerInstructionRequest(tempDir)

        val planMdPath = tempDir.resolve("shared/plan/PLAN.md")
        Files.createDirectories(planMdPath.parent)
        Files.writeString(planMdPath, "# Plan\n\nStep 1: Do the thing.")

        val reviewerPublicMd = tempDir.resolve("reviewer/comm/out/PUBLIC.md")
        Files.createDirectories(reviewerPublicMd.parent)
        Files.writeString(reviewerPublicMd, "# Review\n\nNeeds changes.")

        val priorPublicMd = tempDir.resolve("part_0/impl/comm/out/PUBLIC.md")
        Files.createDirectories(priorPublicMd.parent)
        Files.writeString(priorPublicMd, "Prior work done.")

        val request = baseRequest.copy(
            iterationNumber = 2,
            reviewerPublicMdPath = reviewerPublicMd,
            executionContext = baseRequest.executionContext.copy(
                planMdPath = planMdPath,
                priorPublicMdPaths = listOf(priorPublicMd),
            ),
        )

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(request).readText()

            it("THEN doer sections appear in spec-defined order") {
                val indices = listOf(
                    text.indexOf("# Role:"),
                    text.indexOf("## Part Context"),
                    text.indexOf("# Ticket"),
                    text.indexOf("# Plan"),
                    text.indexOf("# Prior Agent Outputs"),
                    text.indexOf("## Reviewer Feedback"),
                    text.indexOf("PUBLIC.md Output Path"),
                    text.indexOf("PUBLIC.md Writing Guidelines"),
                    text.indexOf("Communicating with the Harness"),
                )
                // All must be found
                indices.none { it == -1 } shouldBe true
                // Each index must be strictly less than the next
                indices.zipWithNext().all { (a, b) -> a < b } shouldBe true
            }
        }
    }

    describe("GIVEN a reviewer request (iteration 2 with feedback state)") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("ordering-reviewer-test")
        val request = ContextTestFixtures.reviewerInstructionRequestWithFeedback(tempDir)

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(request).readText()

            it("THEN reviewer sections appear in spec-defined order") {
                val indices = listOf(
                    text.indexOf("# Role:"),
                    text.indexOf("## Part Context"),
                    text.indexOf("# Ticket"),
                    text.indexOf("Doer Output (for review)"),
                    text.indexOf("Structured Feedback Format"),
                    text.indexOf("Addressed Feedback"),
                    text.indexOf("Writing Feedback Files"),
                    text.indexOf("PUBLIC.md Output Path"),
                    text.indexOf("PUBLIC.md Writing Guidelines"),
                    text.indexOf("Communicating with the Harness"),
                )
                indices.none { it == -1 } shouldBe true
                indices.zipWithNext().all { (a, b) -> a < b } shouldBe true
            }
        }
    }

    describe("GIVEN a planner request (iteration 1)") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("ordering-planner-test")
        val request = ContextTestFixtures.plannerRequest(tempDir)

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(request).readText()

            it("THEN planner sections appear in spec-defined order") {
                val indices = listOf(
                    text.indexOf("# Role:"),
                    text.indexOf("# Ticket"),
                    text.indexOf("## Available Roles"),
                    text.indexOf("Available Agent Types"),
                    text.indexOf("## Plan Format"),
                    text.indexOf("plan_flow.json Output Path"),
                    text.indexOf("PLAN.md Output Path"),
                    text.indexOf("PUBLIC.md Output Path"),
                    text.indexOf("PUBLIC.md Writing Guidelines"),
                    text.indexOf("Communicating with the Harness"),
                )
                indices.none { it == -1 } shouldBe true
                indices.zipWithNext().all { (a, b) -> a < b } shouldBe true
            }
        }
    }

    describe("GIVEN a plan reviewer request (iteration 1)") {
        val provider = ContextForAgentProvider.standard(outFactory)
        val tempDir = Files.createTempDirectory("ordering-plan-reviewer-test")
        val request = ContextTestFixtures.planReviewerRequest(tempDir)

        describe("WHEN instructions are assembled") {
            val text = provider.assembleInstructions(request).readText()

            it("THEN plan reviewer sections appear in spec-defined order") {
                val indices = listOf(
                    text.indexOf("# Role:"),
                    text.indexOf("# Ticket"),
                    text.indexOf("## plan_flow.json"),
                    text.indexOf("## PLAN.md"),
                    text.indexOf("Available Agent Types"),
                    text.indexOf("Planner's Rationale"),
                    text.indexOf("PUBLIC.md Output Path"),
                    text.indexOf("PUBLIC.md Writing Guidelines"),
                    text.indexOf("Communicating with the Harness"),
                )
                indices.none { it == -1 } shouldBe true
                indices.zipWithNext().all { (a, b) -> a < b } shouldBe true
            }
        }
    }
})
