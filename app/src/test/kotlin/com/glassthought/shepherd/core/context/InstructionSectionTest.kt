package com.glassthought.shepherd.core.context

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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

    // ── FeedbackItem ──────────────────────────────────────────────────────

    describe("GIVEN a FeedbackItem section with isOptional = false") {
        val tempDir = Files.createTempDirectory("section-feedbackitem-required-test")
        val feedbackPath = tempDir.resolve("pending/critical__missing-tests.md")
        val section = InstructionSection.FeedbackItem(
            feedbackContent = "Missing unit tests for UserService.",
            currentPath = feedbackPath,
            isOptional = false,
        )
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = section.render(request)

            it("THEN includes the feedback content") {
                result shouldContain "Missing unit tests for UserService."
            }

            it("THEN includes the feedback file path") {
                result shouldContain feedbackPath.toString()
            }

            it("THEN includes ADDRESSED resolution marker") {
                result shouldContain ProtocolVocabulary.FeedbackStatus.ADDRESSED
            }

            it("THEN does NOT include SKIPPED note for required items") {
                result shouldNotContain "This feedback is ${ProtocolVocabulary.Severity.OPTIONAL}"
            }
        }
    }

    describe("GIVEN a FeedbackItem section with isOptional = true") {
        val tempDir = Files.createTempDirectory("section-feedbackitem-optional-test")
        val feedbackPath = tempDir.resolve("pending/optional__naming-suggestion.md")
        val section = InstructionSection.FeedbackItem(
            feedbackContent = "Consider renaming variable for clarity.",
            currentPath = feedbackPath,
            isOptional = true,
        )
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = section.render(request)

            it("THEN includes the feedback content") {
                result shouldContain "Consider renaming variable for clarity."
            }

            it("THEN includes the SKIPPED note for optional items") {
                result shouldContain "This feedback is ${ProtocolVocabulary.Severity.OPTIONAL}"
            }

            it("THEN includes SKIPPED resolution marker") {
                result shouldContain ProtocolVocabulary.FeedbackStatus.SKIPPED
            }
        }
    }

    // ── StructuredFeedbackFormat ───────────────────────────────────────────

    describe("GIVEN a StructuredFeedbackFormat section") {
        val tempDir = Files.createTempDirectory("section-feedback-format-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.StructuredFeedbackFormat.render(request)

            it("THEN returns the REVIEWER_FEEDBACK_FORMAT text exactly") {
                result shouldBe InstructionText.REVIEWER_FEEDBACK_FORMAT
            }
        }
    }

    // ── FeedbackWritingInstructions ────────────────────────────────────────

    describe("GIVEN a FeedbackWritingInstructions section") {
        val tempDir = Files.createTempDirectory("section-feedback-writing-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = InstructionSection.FeedbackWritingInstructions.render(request)

            it("THEN returns the FEEDBACK_WRITING_INSTRUCTIONS text exactly") {
                result shouldBe InstructionText.FEEDBACK_WRITING_INSTRUCTIONS
            }
        }
    }

    // ── FeedbackDirectorySection ──────────────────────────────────────────

    describe("GIVEN a FeedbackDirectorySection with an empty directory") {
        val tempDir = Files.createTempDirectory("section-feedbackdir-empty-test")
        val emptyDir = tempDir.resolve("addressed")
        Files.createDirectories(emptyDir)
        val section = InstructionSection.FeedbackDirectorySection(
            dir = emptyDir,
            heading = "Addressed Feedback",
        )
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = section.render(request)

            it("THEN returns null") {
                result.shouldBeNull()
            }
        }
    }

    describe("GIVEN a FeedbackDirectorySection with a non-existent directory") {
        val tempDir = Files.createTempDirectory("section-feedbackdir-noexist-test")
        val section = InstructionSection.FeedbackDirectorySection(
            dir = tempDir.resolve("does-not-exist"),
            heading = "Addressed Feedback",
        )
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = section.render(request)

            it("THEN returns null") {
                result.shouldBeNull()
            }
        }
    }

    describe("GIVEN a FeedbackDirectorySection with populated directory") {
        val tempDir = Files.createTempDirectory("section-feedbackdir-populated-test")
        val feedbackDir = tempDir.resolve("addressed")
        Files.createDirectories(feedbackDir)
        Files.writeString(feedbackDir.resolve("critical__missing-tests.md"), "Fixed missing tests.")
        Files.writeString(feedbackDir.resolve("important__error-handling.md"), "Added error handling.")
        val section = InstructionSection.FeedbackDirectorySection(
            dir = feedbackDir,
            heading = "Addressed Feedback",
        )
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = section.render(request)

            it("THEN returns non-null") {
                result.shouldNotBeNull()
            }

            it("THEN starts with the heading") {
                result!! shouldStartWith "## Addressed Feedback"
            }

            it("THEN includes the first file name as sub-heading") {
                result!! shouldContain "### critical__missing-tests.md"
            }

            it("THEN includes the first file content") {
                result!! shouldContain "Fixed missing tests."
            }

            it("THEN includes the second file name as sub-heading") {
                result!! shouldContain "### important__error-handling.md"
            }

            it("THEN includes the second file content") {
                result!! shouldContain "Added error handling."
            }

            it("THEN separates files with horizontal rule") {
                result!! shouldContain "---"
            }
        }
    }

    describe("GIVEN a FeedbackDirectorySection with filenamePrefix filter") {
        val tempDir = Files.createTempDirectory("section-feedbackdir-prefix-test")
        val pendingDir = tempDir.resolve("pending")
        Files.createDirectories(pendingDir)
        Files.writeString(pendingDir.resolve("optional__naming.md"), "Naming suggestion.")
        Files.writeString(pendingDir.resolve("critical__bug.md"), "Critical bug found.")
        val section = InstructionSection.FeedbackDirectorySection(
            dir = pendingDir,
            heading = "Skipped Optional Feedback",
            filenamePrefix = ProtocolVocabulary.SeverityPrefix.OPTIONAL,
        )
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        describe("WHEN rendered") {
            val result = section.render(request)

            it("THEN returns non-null") {
                result.shouldNotBeNull()
            }

            it("THEN includes the optional file") {
                result!! shouldContain "optional__naming.md"
            }

            it("THEN does NOT include non-matching files") {
                result!! shouldNotContain "critical__bug.md"
            }
        }
    }
})
