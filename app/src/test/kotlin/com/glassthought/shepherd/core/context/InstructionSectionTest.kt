package com.glassthought.shepherd.core.context

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for each [InstructionSection] subtype rendered in isolation.
 *
 * Each section is tested with a minimal [AgentInstructionRequest] that provides the
 * data it needs. Tests follow BDD GIVEN/WHEN/THEN structure.
 */
class InstructionSectionTest : AsgardDescribeSpec({

    // ── RoleDefinition ───────────────────────────────────────────────────────

    describe("GIVEN a RoleDefinition section") {
        val tempDir = Files.createTempDirectory("section-role-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.RoleDefinition.render(request)

            it("THEN starts with role heading including role name") {
                result shouldStartWith "# Role: IMPLEMENTOR"
            }

            it("THEN includes the role file content") {
                result shouldContain "You are an implementation agent"
            }
        }
    }

    // ── PrivateMd ────────────────────────────────────────────────────────────

    describe("GIVEN a PrivateMd section with privateMdPath = null") {
        val tempDir = Files.createTempDirectory("section-privatemd-null-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.PrivateMd.render(request)

            it("THEN returns null") {
                result.shouldBeNull()
            }
        }
    }

    describe("GIVEN a PrivateMd section with non-existent file") {
        val tempDir = Files.createTempDirectory("section-privatemd-noexist-test")
        val baseRequest = ContextTestFixtures.doerInstructionRequest(tempDir)
        val request = baseRequest.copy(privateMdPath = tempDir.resolve("does-not-exist.md"))

        describe("WHEN rendered") {
            val result = InstructionSection.PrivateMd.render(request)

            it("THEN returns null") {
                result.shouldBeNull()
            }
        }
    }

    describe("GIVEN a PrivateMd section with blank file") {
        val tempDir = Files.createTempDirectory("section-privatemd-blank-test")
        val baseRequest = ContextTestFixtures.doerInstructionRequest(tempDir)

        val blankFile = tempDir.resolve("PRIVATE.md")
        Files.writeString(blankFile, "   \n  ")
        val request = baseRequest.copy(privateMdPath = blankFile)

        describe("WHEN rendered") {
            val result = InstructionSection.PrivateMd.render(request)

            it("THEN returns null") {
                result.shouldBeNull()
            }
        }
    }

    describe("GIVEN a PrivateMd section with existing non-blank file") {
        val tempDir = Files.createTempDirectory("section-privatemd-present-test")
        val baseRequest = ContextTestFixtures.doerInstructionRequest(tempDir)

        val privateMdFile = tempDir.resolve("PRIVATE.md")
        Files.writeString(privateMdFile, "Prior session context content.")
        val request = baseRequest.copy(privateMdPath = privateMdFile)

        describe("WHEN rendered") {
            val result = InstructionSection.PrivateMd.render(request)

            it("THEN returns non-null") {
                result.shouldNotBeNull()
            }

            it("THEN includes the Prior Session Context heading") {
                result!! shouldContain "# Prior Session Context (PRIVATE.md)"
            }

            it("THEN includes the file content") {
                result!! shouldContain "Prior session context content."
            }
        }
    }

    // ── PartContext ───────────────────────────────────────────────────────────

    describe("GIVEN a PartContext section with a DoerRequest") {
        val tempDir = Files.createTempDirectory("section-partctx-doer-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.PartContext.render(request)

            it("THEN returns non-null") {
                result.shouldNotBeNull()
            }

            it("THEN includes the part name") {
                result!! shouldContain "part_1_implementation"
            }

            it("THEN includes the part description") {
                result!! shouldContain "Implement the main feature"
            }
        }
    }

    describe("GIVEN a PartContext section with a ReviewerRequest") {
        val tempDir = Files.createTempDirectory("section-partctx-reviewer-test")
        val request = ContextTestFixtures.reviewerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.PartContext.render(request)

            it("THEN returns non-null") {
                result.shouldNotBeNull()
            }

            it("THEN includes the part name") {
                result!! shouldContain "part_1_implementation"
            }
        }
    }

    describe("GIVEN a PartContext section with a PlannerRequest") {
        val tempDir = Files.createTempDirectory("section-partctx-planner-test")
        val request = ContextTestFixtures.plannerRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.PartContext.render(request)

            it("THEN returns null") {
                result.shouldBeNull()
            }
        }
    }

    describe("GIVEN a PartContext section with a PlanReviewerRequest") {
        val tempDir = Files.createTempDirectory("section-partctx-planrev-test")
        val request = ContextTestFixtures.planReviewerRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.PartContext.render(request)

            it("THEN returns null") {
                result.shouldBeNull()
            }
        }
    }

    // ── Ticket ───────────────────────────────────────────────────────────────

    describe("GIVEN a Ticket section") {
        val tempDir = Files.createTempDirectory("section-ticket-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.Ticket.render(request)

            it("THEN starts with Ticket heading") {
                result shouldStartWith "# Ticket"
            }

            it("THEN includes the ticket content") {
                result shouldContain "Implement feature X."
            }
        }
    }

    // ── OutputPathSection ────────────────────────────────────────────────────

    describe("GIVEN an OutputPathSection for PUBLIC.md") {
        val outputPath = Path.of("/tmp/test/comm/out/PUBLIC.md")
        val section = InstructionSection.OutputPathSection(label = "PUBLIC.md", path = outputPath)
        val tempDir = Files.createTempDirectory("section-outputpath-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = section.render(request)

            it("THEN includes the label in heading") {
                result shouldContain "PUBLIC.md Output Path"
            }

            it("THEN includes the output path") {
                result shouldContain outputPath.toString()
            }
        }
    }

    describe("GIVEN an OutputPathSection for plan_flow.json") {
        val planJsonPath = Path.of("/tmp/test/plan_flow.json")
        val section = InstructionSection.OutputPathSection(label = "plan_flow.json", path = planJsonPath)
        val tempDir = Files.createTempDirectory("section-outputpath-plan-test")
        val request = ContextTestFixtures.plannerRequest(tempDir)

        describe("WHEN rendered") {
            val result = section.render(request)

            it("THEN includes the label in heading") {
                result shouldContain "plan_flow.json Output Path"
            }

            it("THEN includes the path") {
                result shouldContain planJsonPath.toString()
            }
        }
    }

    // ── WritingGuidelines ────────────────────────────────────────────────────

    describe("GIVEN a WritingGuidelines section") {
        val tempDir = Files.createTempDirectory("section-guidelines-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.WritingGuidelines.render(request)

            it("THEN returns the static writing guidelines text") {
                result shouldBe InstructionText.PUBLIC_MD_WRITING_GUIDELINES
            }
        }
    }

    // ── CallbackHelp ─────────────────────────────────────────────────────────

    describe("GIVEN a CallbackHelp section for a doer (not reviewer, no plan validation)") {
        val section = InstructionSection.CallbackHelp(forReviewer = false, includePlanValidation = false)
        val tempDir = Files.createTempDirectory("section-callback-doer-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = section.render(request)

            it("THEN includes the callback signal script name") {
                result shouldContain ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT
            }

            it("THEN includes the completed done result (not reviewer)") {
                result shouldContain ProtocolVocabulary.DoneResult.COMPLETED
            }

            it("THEN does NOT include validate-plan query") {
                result shouldNotContain "validate-plan"
            }
        }
    }

    describe("GIVEN a CallbackHelp section for a reviewer with plan validation") {
        val section = InstructionSection.CallbackHelp(forReviewer = true, includePlanValidation = true)
        val tempDir = Files.createTempDirectory("section-callback-reviewer-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = section.render(request)

            it("THEN includes pass done result (reviewer)") {
                result shouldContain ProtocolVocabulary.DoneResult.PASS
            }

            it("THEN includes needs_iteration done result (reviewer)") {
                result shouldContain ProtocolVocabulary.DoneResult.NEEDS_ITERATION
            }

            it("THEN includes validate-plan query section") {
                result shouldContain "validate-plan"
            }
        }
    }

    // ── InlineFileContentSection ──────────────────────────────────────────

    describe("GIVEN an InlineFileContentSection with a non-null path and existing file") {
        val tempDir = Files.createTempDirectory("section-inline-exists-test")
        val file = tempDir.resolve("notes.md")
        Files.writeString(file, "Some inline content here.")
        val section = InstructionSection.InlineFileContentSection(heading = "Design Notes", path = file)
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = section.render(request)

            it("THEN returns non-null") {
                result.shouldNotBeNull()
            }

            it("THEN includes the heading") {
                result!! shouldContain "## Design Notes"
            }

            it("THEN includes the file content") {
                result!! shouldContain "Some inline content here."
            }
        }
    }

    describe("GIVEN an InlineFileContentSection with a non-null path and missing file") {
        val tempDir = Files.createTempDirectory("section-inline-missing-test")
        val missingPath = tempDir.resolve("does-not-exist.md")
        val section = InstructionSection.InlineFileContentSection(heading = "Missing", path = missingPath)
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            it("THEN throws IllegalStateException") {
                shouldThrow<IllegalStateException> {
                    section.render(request)
                }
            }
        }
    }

    describe("GIVEN an InlineFileContentSection with a null path") {
        val section = InstructionSection.InlineFileContentSection(heading = "Irrelevant", path = null)
        val tempDir = Files.createTempDirectory("section-inline-null-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = section.render(request)

            it("THEN returns null") {
                result.shouldBeNull()
            }
        }
    }

    // ── PlanMd ────────────────────────────────────────────────────────────

    describe("GIVEN a PlanMd section with a DoerRequest and non-null planMdPath") {
        val tempDir = Files.createTempDirectory("section-planmd-doer-test")
        val planFile = tempDir.resolve("PLAN.md")
        Files.writeString(planFile, "Three-part implementation plan.")
        val baseRequest = ContextTestFixtures.doerInstructionRequest(tempDir)
        val request = baseRequest.copy(
            executionContext = baseRequest.executionContext.copy(planMdPath = planFile)
        )

        describe("WHEN rendered") {
            val result = InstructionSection.PlanMd.render(request)

            it("THEN returns non-null") {
                result.shouldNotBeNull()
            }

            it("THEN starts with Plan heading") {
                result!! shouldStartWith "# Plan"
            }

            it("THEN includes the plan file content") {
                result!! shouldContain "Three-part implementation plan."
            }
        }
    }

    describe("GIVEN a PlanMd section with a DoerRequest and null planMdPath") {
        val tempDir = Files.createTempDirectory("section-planmd-null-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.PlanMd.render(request)

            it("THEN returns null") {
                result.shouldBeNull()
            }
        }
    }

    describe("GIVEN a PlanMd section with a PlannerRequest") {
        val tempDir = Files.createTempDirectory("section-planmd-planner-test")
        val request = ContextTestFixtures.plannerRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.PlanMd.render(request)

            it("THEN returns null") {
                result.shouldBeNull()
            }
        }
    }

    // ── PriorPublicMd ─────────────────────────────────────────────────────

    describe("GIVEN a PriorPublicMd section with a DoerRequest and non-empty priorPublicMdPaths") {
        val tempDir = Files.createTempDirectory("section-priorpub-doer-test")
        val prior1 = tempDir.resolve("part_0_PUBLIC.md")
        Files.writeString(prior1, "Prior part 0 output.")
        val prior2 = tempDir.resolve("part_1_PUBLIC.md")
        Files.writeString(prior2, "Prior part 1 output.")

        val baseRequest = ContextTestFixtures.doerInstructionRequest(tempDir)
        val request = baseRequest.copy(
            executionContext = baseRequest.executionContext.copy(
                priorPublicMdPaths = listOf(prior1, prior2)
            )
        )

        describe("WHEN rendered") {
            val result = InstructionSection.PriorPublicMd.render(request)

            it("THEN returns non-null") {
                result.shouldNotBeNull()
            }

            it("THEN includes first prior file name as heading") {
                result!! shouldContain "## part_0_PUBLIC.md"
            }

            it("THEN includes first prior file content") {
                result!! shouldContain "Prior part 0 output."
            }

            it("THEN includes second prior file name as heading") {
                result!! shouldContain "## part_1_PUBLIC.md"
            }

            it("THEN includes second prior file content") {
                result!! shouldContain "Prior part 1 output."
            }
        }
    }

    describe("GIVEN a PriorPublicMd section with a DoerRequest and empty priorPublicMdPaths") {
        val tempDir = Files.createTempDirectory("section-priorpub-empty-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.PriorPublicMd.render(request)

            it("THEN returns null") {
                result.shouldBeNull()
            }
        }
    }

    describe("GIVEN a PriorPublicMd section with a PlannerRequest") {
        val tempDir = Files.createTempDirectory("section-priorpub-planner-test")
        val request = ContextTestFixtures.plannerRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.PriorPublicMd.render(request)

            it("THEN returns null") {
                result.shouldBeNull()
            }
        }
    }

    describe("GIVEN a PriorPublicMd section with specific prior paths (negative test)") {
        val tempDir = Files.createTempDirectory("section-priorpub-negative-test")

        val doerPublic = tempDir.resolve("doer_PUBLIC.md")
        Files.writeString(doerPublic, "Doer work output only.")

        val baseRequest = ContextTestFixtures.doerInstructionRequest(tempDir)
        val request = baseRequest.copy(
            executionContext = baseRequest.executionContext.copy(
                priorPublicMdPaths = listOf(doerPublic)
            )
        )

        describe("WHEN rendered") {
            val result = InstructionSection.PriorPublicMd.render(request)

            it("THEN renders only the files in the provided list") {
                result.shouldNotBeNull()
                result!! shouldContain "Doer work output only."
            }

            it("THEN does NOT include planner PUBLIC.md content (not in the list)") {
                result!! shouldNotContain "Planner Rationale"
                result!! shouldNotContain "Chose 3-part approach"
            }
        }
    }

    // ── IterationFeedback ─────────────────────────────────────────────────

    describe("GIVEN an IterationFeedback section with a DoerRequest and non-null reviewerPublicMdPath") {
        val tempDir = Files.createTempDirectory("section-iterfeedback-test")
        val reviewerPublicMd = tempDir.resolve("reviewer_PUBLIC.md")
        Files.writeString(reviewerPublicMd, "## Verdict: needs_iteration\n\nPlease fix the tests.")

        val baseRequest = ContextTestFixtures.doerInstructionRequest(tempDir)
        val request = baseRequest.copy(
            iterationNumber = 2,
            reviewerPublicMdPath = reviewerPublicMd,
        )

        describe("WHEN rendered") {
            val result = InstructionSection.IterationFeedback.render(request)

            it("THEN returns non-null") {
                result.shouldNotBeNull()
            }

            it("THEN includes Reviewer Feedback heading") {
                result!! shouldContain "## Reviewer Feedback"
            }

            it("THEN includes the reviewer feedback content") {
                result!! shouldContain "Please fix the tests."
            }

            it("THEN includes pushback guidance") {
                result!! shouldContain "Handling Reviewer Feedback"
            }

            it("THEN wraps pushback guidance in compaction-survival tags") {
                result!! shouldContain "<critical_to_keep_through_compaction>"
                result!! shouldContain "</critical_to_keep_through_compaction>"
            }
        }
    }

    describe("GIVEN an IterationFeedback section with a DoerRequest and null reviewerPublicMdPath (iteration 1)") {
        val tempDir = Files.createTempDirectory("section-iterfeedback-null-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.IterationFeedback.render(request)

            it("THEN returns null") {
                result.shouldBeNull()
            }
        }
    }

    describe("GIVEN an IterationFeedback section with a ReviewerRequest") {
        val tempDir = Files.createTempDirectory("section-iterfeedback-reviewer-test")
        val request = ContextTestFixtures.reviewerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.IterationFeedback.render(request)

            it("THEN returns null") {
                result.shouldBeNull()
            }
        }
    }

    // ── RoleCatalog ───────────────────────────────────────────────────────

    describe("GIVEN a RoleCatalog section with a PlannerRequest") {
        val tempDir = Files.createTempDirectory("section-rolecatalog-planner-test")
        val request = ContextTestFixtures.plannerRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.RoleCatalog.render(request)

            it("THEN returns non-null") {
                result.shouldNotBeNull()
            }

            it("THEN includes Available Roles heading") {
                result!! shouldContain "## Available Roles"
            }

            it("THEN includes role names from catalog") {
                result!! shouldContain "IMPLEMENTOR"
                result!! shouldContain "REVIEWER"
            }
        }
    }

    describe("GIVEN a RoleCatalog section with a DoerRequest") {
        val tempDir = Files.createTempDirectory("section-rolecatalog-doer-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.RoleCatalog.render(request)

            it("THEN returns null") {
                result.shouldBeNull()
            }
        }
    }

    // ── AvailableAgentTypes ───────────────────────────────────────────────

    describe("GIVEN an AvailableAgentTypes section") {
        val tempDir = Files.createTempDirectory("section-agenttypes-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered with any request type") {
            val result = InstructionSection.AvailableAgentTypes.render(request)

            it("THEN returns the static agent types text") {
                result shouldBe InstructionText.AGENT_TYPES_AND_MODELS
            }

            it("THEN mentions V1 constraints") {
                result shouldContain "V1 supports one agent type"
            }
        }
    }

    describe("GIVEN an AvailableAgentTypes section with a PlannerRequest") {
        val tempDir = Files.createTempDirectory("section-agenttypes-planner-test")
        val request = ContextTestFixtures.plannerRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.AvailableAgentTypes.render(request)

            it("THEN returns the static agent types text") {
                result shouldBe InstructionText.AGENT_TYPES_AND_MODELS
            }
        }
    }

    // ── PlanFormatInstructions ────────────────────────────────────────────

    describe("GIVEN a PlanFormatInstructions section with a PlannerRequest") {
        val tempDir = Files.createTempDirectory("section-planformat-planner-test")
        val request = ContextTestFixtures.plannerRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.PlanFormatInstructions.render(request)

            it("THEN returns the static plan format instructions text") {
                result shouldBe InstructionText.PLAN_FORMAT_INSTRUCTIONS
            }
        }
    }

    describe("GIVEN a PlanFormatInstructions section with a DoerRequest") {
        val tempDir = Files.createTempDirectory("section-planformat-doer-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.PlanFormatInstructions.render(request)

            it("THEN returns null") {
                result.shouldBeNull()
            }
        }
    }
})
