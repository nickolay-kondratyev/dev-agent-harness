package com.glassthought.shepherd.core.context

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldNotBe
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
/**
 * Asserts that all [sectionNames] are found in [text] and appear in the given order.
 * On failure, the assertion message identifies WHICH section is missing or misordered.
 */
private fun assertSectionsInOrder(text: String, sectionNames: List<String>) {
    val indices = sectionNames.map { text.indexOf(it) }

    // Every section must be found
    sectionNames.zip(indices).forEach { (name, idx) ->
        io.kotest.assertions.withClue("Section '$name' not found in rendered instructions") {
            idx shouldNotBe -1
        }
    }

    // Each section must appear before the next
    indices.zipWithNext().forEachIndexed { i, (a, b) ->
        io.kotest.assertions.withClue(
            "'${sectionNames[i]}' (at index $a) should appear before '${sectionNames[i + 1]}' (at index $b)"
        ) {
            b shouldBeGreaterThan a
        }
    }
}

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
                assertSectionsInOrder(text, listOf(
                    "# Role:",
                    "## Part Context",
                    "# Ticket",
                    "# Plan",
                    "# Prior Agent Outputs",
                    "## Reviewer Feedback",
                    "PUBLIC.md Output Path",
                    "PUBLIC.md Writing Guidelines",
                    "Communicating with the Harness",
                ))
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
                assertSectionsInOrder(text, listOf(
                    "# Role:",
                    "## Part Context",
                    "# Ticket",
                    "Doer Output (for review)",
                    "Structured Feedback Format",
                    "Addressed Feedback",
                    "Writing Feedback Files",
                    "PUBLIC.md Output Path",
                    "PUBLIC.md Writing Guidelines",
                    "Communicating with the Harness",
                ))
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
                assertSectionsInOrder(text, listOf(
                    "# Role:",
                    "# Ticket",
                    "## Available Roles",
                    "Available Agent Types",
                    "## Plan Format",
                    "plan_flow.json Output Path",
                    "PLAN.md Output Path",
                    "PUBLIC.md Output Path",
                    "PUBLIC.md Writing Guidelines",
                    "Communicating with the Harness",
                ))
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
                assertSectionsInOrder(text, listOf(
                    "# Role:",
                    "# Ticket",
                    "## plan_flow.json",
                    "## PLAN.md",
                    "Available Agent Types",
                    "Planner's Rationale",
                    "PUBLIC.md Output Path",
                    "PUBLIC.md Writing Guidelines",
                    "Communicating with the Harness",
                ))
            }
        }
    }
})
