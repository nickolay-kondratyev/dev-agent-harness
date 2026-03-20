package com.glassthought.shepherd.core.filestructure

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.Phase
import com.glassthought.shepherd.core.state.SubPart
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class AiOutputStructureEnsureStructureTest : AsgardDescribeSpec({

    // -- helpers ----------------------------------------------------------

    fun subPart(name: String) = SubPart(
        name = name,
        role = "DOER",
        agentType = "CLAUDE_CODE",
        model = "sonnet",
    )

    fun planningPart(name: String, vararg subPartNames: String) = Part(
        name = name,
        phase = Phase.PLANNING,
        description = "Planning part: $name",
        subParts = subPartNames.map { subPart(it) },
    )

    fun executionPart(name: String, vararg subPartNames: String) = Part(
        name = name,
        phase = Phase.EXECUTION,
        description = "Execution part: $name",
        subParts = subPartNames.map { subPart(it) },
    )

    // -- mixed planning + execution ---------------------------------------

    describe("GIVEN ensureStructure with planning + execution parts") {
        val tempDir = Files.createTempDirectory("ai-out-ensure-test")
        val structure = AiOutputStructure(repoRoot = tempDir, branch = "test_branch")

        val parts = listOf(
            planningPart("plan_part", "plan"),
            executionPart("backend", "impl", "review"),
        )

        structure.ensureStructure(parts)

        describe("WHEN checking always-present directories") {
            it("THEN harness_private exists") {
                Files.isDirectory(structure.harnessPrivateDir()) shouldBe true
            }

            it("THEN shared/plan exists") {
                Files.isDirectory(structure.sharedPlanDir()) shouldBe true
            }
        }

        describe("WHEN checking planning sub-part directories") {
            it("THEN planning/plan/private exists") {
                Files.isDirectory(structure.planningSubPartPrivateDir("plan")) shouldBe true
            }

            it("THEN planning/plan/comm/in exists") {
                Files.isDirectory(structure.planningCommInDir("plan")) shouldBe true
            }

            it("THEN planning/plan/comm/out exists") {
                Files.isDirectory(structure.planningCommOutDir("plan")) shouldBe true
            }
        }

        describe("WHEN checking execution part feedback directories") {
            it("THEN execution/backend/__feedback/pending exists") {
                Files.isDirectory(structure.feedbackPendingDir("backend")) shouldBe true
            }

            it("THEN execution/backend/__feedback/addressed exists") {
                Files.isDirectory(structure.feedbackAddressedDir("backend")) shouldBe true
            }

            it("THEN execution/backend/__feedback/rejected exists") {
                Files.isDirectory(structure.feedbackRejectedDir("backend")) shouldBe true
            }
        }

        describe("WHEN checking execution sub-part directories") {
            it("THEN execution/backend/impl/private exists") {
                Files.isDirectory(structure.executionSubPartPrivateDir("backend", "impl")) shouldBe true
            }

            it("THEN execution/backend/impl/comm/in exists") {
                Files.isDirectory(structure.executionCommInDir("backend", "impl")) shouldBe true
            }

            it("THEN execution/backend/impl/comm/out exists") {
                Files.isDirectory(structure.executionCommOutDir("backend", "impl")) shouldBe true
            }

            it("THEN execution/backend/review/private exists") {
                Files.isDirectory(structure.executionSubPartPrivateDir("backend", "review")) shouldBe true
            }

            it("THEN execution/backend/review/comm/in exists") {
                Files.isDirectory(structure.executionCommInDir("backend", "review")) shouldBe true
            }

            it("THEN execution/backend/review/comm/out exists") {
                Files.isDirectory(structure.executionCommOutDir("backend", "review")) shouldBe true
            }
        }

        describe("WHEN checking that planning dirs do NOT have __feedback") {
            it("THEN no __feedback directory exists under planning") {
                val planningDir = structure.branchRoot().resolve("planning")
                val feedbackUnderPlanning = planningDir.resolve("plan_part").resolve("__feedback")
                Files.exists(feedbackUnderPlanning) shouldBe false
            }

            it("THEN no __feedback directory exists under planning sub-part") {
                val feedbackUnderSubPart = structure.planningSubPartDir("plan").resolve("__feedback")
                Files.exists(feedbackUnderSubPart) shouldBe false
            }
        }

        afterSpec {
            tempDir.toFile().deleteRecursively()
        }
    }

    // -- idempotency ------------------------------------------------------

    describe("GIVEN ensureStructure called twice with same parts") {
        val tempDir = Files.createTempDirectory("ai-out-idempotent-test")
        val structure = AiOutputStructure(repoRoot = tempDir, branch = "idem_branch")

        val parts = listOf(
            planningPart("plan_part", "plan"),
            executionPart("backend", "impl"),
        )

        structure.ensureStructure(parts)
        structure.ensureStructure(parts)

        it("THEN harness_private still exists") {
            Files.isDirectory(structure.harnessPrivateDir()) shouldBe true
        }

        it("THEN planning/plan/private still exists") {
            Files.isDirectory(structure.planningSubPartPrivateDir("plan")) shouldBe true
        }

        it("THEN execution/backend/impl/private still exists") {
            Files.isDirectory(structure.executionSubPartPrivateDir("backend", "impl")) shouldBe true
        }

        it("THEN execution/backend/__feedback/pending still exists") {
            Files.isDirectory(structure.feedbackPendingDir("backend")) shouldBe true
        }

        afterSpec {
            tempDir.toFile().deleteRecursively()
        }
    }

    // -- planning-only ----------------------------------------------------

    describe("GIVEN ensureStructure with only planning parts") {
        val tempDir = Files.createTempDirectory("ai-out-planning-only-test")
        val structure = AiOutputStructure(repoRoot = tempDir, branch = "plan_only")

        val parts = listOf(
            planningPart("plan_part", "design", "review"),
        )

        structure.ensureStructure(parts)

        it("THEN harness_private exists") {
            Files.isDirectory(structure.harnessPrivateDir()) shouldBe true
        }

        it("THEN shared/plan exists") {
            Files.isDirectory(structure.sharedPlanDir()) shouldBe true
        }

        it("THEN planning/design/private exists") {
            Files.isDirectory(structure.planningSubPartPrivateDir("design")) shouldBe true
        }

        it("THEN planning/design/comm/in exists") {
            Files.isDirectory(structure.planningCommInDir("design")) shouldBe true
        }

        it("THEN planning/review/comm/out exists") {
            Files.isDirectory(structure.planningCommOutDir("review")) shouldBe true
        }

        it("THEN execution directory does NOT exist") {
            val executionDir = structure.branchRoot().resolve("execution")
            Files.exists(executionDir) shouldBe false
        }

        it("THEN no __feedback directory exists anywhere") {
            val branchRoot = structure.branchRoot()
            val hasFeedback = Files.walk(branchRoot).use { stream ->
                stream.anyMatch { it.fileName?.toString() == "__feedback" }
            }
            hasFeedback shouldBe false
        }

        afterSpec {
            tempDir.toFile().deleteRecursively()
        }
    }

    // -- execution-only ---------------------------------------------------

    describe("GIVEN ensureStructure with only execution parts") {
        val tempDir = Files.createTempDirectory("ai-out-exec-only-test")
        val structure = AiOutputStructure(repoRoot = tempDir, branch = "exec_only")

        val parts = listOf(
            executionPart("backend", "impl"),
            executionPart("frontend", "impl", "review"),
        )

        structure.ensureStructure(parts)

        it("THEN harness_private exists") {
            Files.isDirectory(structure.harnessPrivateDir()) shouldBe true
        }

        it("THEN shared/plan exists") {
            Files.isDirectory(structure.sharedPlanDir()) shouldBe true
        }

        it("THEN planning directory does NOT exist") {
            val planningDir = structure.branchRoot().resolve("planning")
            Files.exists(planningDir) shouldBe false
        }

        it("THEN execution/backend/__feedback/pending exists") {
            Files.isDirectory(structure.feedbackPendingDir("backend")) shouldBe true
        }

        it("THEN execution/frontend/__feedback/rejected exists") {
            Files.isDirectory(structure.feedbackRejectedDir("frontend")) shouldBe true
        }

        it("THEN execution/backend/impl/comm/in exists") {
            Files.isDirectory(structure.executionCommInDir("backend", "impl")) shouldBe true
        }

        it("THEN execution/frontend/review/private exists") {
            Files.isDirectory(structure.executionSubPartPrivateDir("frontend", "review")) shouldBe true
        }

        afterSpec {
            tempDir.toFile().deleteRecursively()
        }
    }

    // -- empty parts list -------------------------------------------------

    describe("GIVEN ensureStructure with empty parts list") {
        val tempDir = Files.createTempDirectory("ai-out-empty-parts-test")
        val structure = AiOutputStructure(repoRoot = tempDir, branch = "empty_branch")

        structure.ensureStructure(emptyList())

        it("THEN harness_private exists") {
            Files.isDirectory(structure.harnessPrivateDir()) shouldBe true
        }

        it("THEN shared/plan exists") {
            Files.isDirectory(structure.sharedPlanDir()) shouldBe true
        }

        it("THEN planning directory does NOT exist") {
            val planningDir = structure.branchRoot().resolve("planning")
            Files.exists(planningDir) shouldBe false
        }

        it("THEN execution directory does NOT exist") {
            val executionDir = structure.branchRoot().resolve("execution")
            Files.exists(executionDir) shouldBe false
        }

        afterSpec {
            tempDir.toFile().deleteRecursively()
        }
    }
})
