package com.glassthought.shepherd.core.context

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Unit tests for [InstructionPlanAssembler] — the rendering engine that walks an
 * [InstructionSection] plan and produces the final `instructions.md` file.
 */
class InstructionPlanAssemblerTest : AsgardDescribeSpec({

    describe("GIVEN a plan with multiple sections") {
        val assembler = InstructionPlanAssembler(outFactory)
        val tempDir = Files.createTempDirectory("assembler-multi-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        val plan = listOf(
            InstructionSection.RoleDefinition,
            InstructionSection.Ticket,
            InstructionSection.WritingGuidelines,
        )

        describe("WHEN assembled") {
            val path = assembler.assembleFromPlan(plan, request)
            val text = path.readText()

            it("THEN renders sections in order") {
                val roleIdx = text.indexOf("# Role: IMPLEMENTOR")
                val ticketIdx = text.indexOf("# Ticket")
                val guidelinesIdx = text.indexOf("PUBLIC.md Writing Guidelines")
                (roleIdx < ticketIdx) shouldBe true
                (ticketIdx < guidelinesIdx) shouldBe true
            }

            it("THEN sections are separated by horizontal rule") {
                text shouldContain "\n\n---\n\n"
            }

            it("THEN writes to instructions.md inside outputDir") {
                path.fileName.toString() shouldBe "instructions.md"
                path.parent shouldBe request.outputDir
            }
        }
    }

    describe("GIVEN a plan containing sections that return null") {
        val assembler = InstructionPlanAssembler(outFactory)
        val tempDir = Files.createTempDirectory("assembler-skip-null-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)
        // PrivateMd returns null when privateMdPath is null (default in fixture)

        val plan = listOf(
            InstructionSection.RoleDefinition,
            InstructionSection.PrivateMd,  // will return null
            InstructionSection.Ticket,
        )

        describe("WHEN assembled") {
            val text = assembler.assembleFromPlan(plan, request).readText()

            it("THEN skips null sections (no Prior Session Context)") {
                text shouldNotContain "Prior Session Context"
            }

            it("THEN does not produce stray separators for skipped sections") {
                // Should have exactly one separator between Role and Ticket, not two
                val segments = text.split("\n\n---\n\n")
                segments.size shouldBe 2
            }
        }
    }

    describe("GIVEN an empty plan") {
        val assembler = InstructionPlanAssembler(outFactory)
        val tempDir = Files.createTempDirectory("assembler-empty-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        val plan = emptyList<InstructionSection>()

        describe("WHEN assembled") {
            val text = assembler.assembleFromPlan(plan, request).readText()

            it("THEN produces empty file") {
                text shouldBe ""
            }
        }
    }

    describe("GIVEN a plan with OutputPathSection") {
        val assembler = InstructionPlanAssembler(outFactory)
        val tempDir = Files.createTempDirectory("assembler-outputpath-test")
        val request = ContextTestFixtures.doerInstructionRequest(tempDir)

        val plan = listOf(
            InstructionSection.OutputPathSection(
                label = "PUBLIC.md",
                path = request.publicMdOutputPath,
            ),
        )

        describe("WHEN assembled") {
            val text = assembler.assembleFromPlan(plan, request).readText()

            it("THEN includes the output path section") {
                text shouldContain "PUBLIC.md Output Path"
            }

            it("THEN includes the concrete path value") {
                text shouldContain request.publicMdOutputPath.toString()
            }
        }
    }

    describe("GIVEN a plan with PartContext for a PlannerRequest (returns null)") {
        val assembler = InstructionPlanAssembler(outFactory)
        val tempDir = Files.createTempDirectory("assembler-partctx-planner-test")
        val request = ContextTestFixtures.plannerRequest(tempDir)

        val plan = listOf(
            InstructionSection.RoleDefinition,
            InstructionSection.PartContext,  // returns null for planner
            InstructionSection.Ticket,
        )

        describe("WHEN assembled") {
            val text = assembler.assembleFromPlan(plan, request).readText()

            it("THEN PartContext is skipped for planner") {
                text shouldNotContain "Part Context"
            }

            it("THEN Role and Ticket are still present") {
                text shouldContain "# Role: PLANNER"
                text shouldContain "# Ticket"
            }
        }
    }
})
