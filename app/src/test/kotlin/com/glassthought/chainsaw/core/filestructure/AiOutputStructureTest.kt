package com.glassthought.chainsaw.core.filestructure

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import java.nio.file.Files
import java.nio.file.Path

private data class TestFixture(val repoRoot: Path, val structure: AiOutputStructure)

class AiOutputStructureTest : AsgardDescribeSpec({

    val branch = "feature__my-task__try-1"
    val role = "IMPLEMENTOR"
    val part = "part_1"

    fun createTestFixture(): TestFixture {
        val repoRoot = Files.createTempDirectory("ai-output-structure-test")
        return TestFixture(repoRoot, AiOutputStructure(repoRoot))
    }

    describe("GIVEN AiOutputStructure") {

        describe("WHEN constructed with non-existent repo root") {
            it("THEN throws IllegalArgumentException") {
                val nonExistent = Path.of("/tmp/does-not-exist-${System.nanoTime()}")

                shouldThrow<IllegalArgumentException> {
                    AiOutputStructure(nonExistent)
                }
            }
        }

        describe("WHEN constructed with valid repo root") {
            it("THEN does not throw") {
                val fixture = createTestFixture()

                fixture.structure shouldBe fixture.structure // constructed successfully
            }
        }

        describe("WHEN constructed with a file path (not a directory)") {
            it("THEN throws IllegalArgumentException") {
                val tempFile = Files.createTempFile("ai-output-not-a-dir", ".txt")

                shouldThrow<IllegalArgumentException> {
                    AiOutputStructure(tempFile)
                }
            }
        }
    }

    describe("GIVEN AiOutputStructure with blank string parameters") {
        val (_, structure) = createTestFixture()

        describe("WHEN branch is blank") {
            it("THEN harnessPrivateDir throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    structure.harnessPrivateDir("")
                }
            }

            it("THEN sharedDir throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    structure.sharedDir("  ")
                }
            }
        }

        describe("WHEN role is blank") {
            it("THEN planningRoleDir throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    structure.planningRoleDir(branch, "")
                }
            }

            it("THEN phaseRoleDir throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    structure.phaseRoleDir(branch, part, "")
                }
            }
        }

        describe("WHEN part is blank") {
            it("THEN phaseRoleDir throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    structure.phaseRoleDir(branch, "", role)
                }
            }
        }
    }

    describe("GIVEN AiOutputStructure with valid repo root") {
        val (repoRoot, structure) = createTestFixture()

        describe("AND branch is '$branch'") {

            describe("WHEN harnessPrivateDir is called") {
                val result = structure.harnessPrivateDir(branch)

                it("THEN path ends with .ai_out/$branch/harness_private") {
                    result.toString() shouldEndWith ".ai_out/$branch/harness_private"
                }

                it("THEN path starts with repo root") {
                    result.toString() shouldStartWith repoRoot.toString()
                }
            }

            describe("WHEN sharedDir is called") {
                val result = structure.sharedDir(branch)

                it("THEN path ends with .ai_out/$branch/shared") {
                    result.toString() shouldEndWith ".ai_out/$branch/shared"
                }

                it("THEN path starts with repo root") {
                    result.toString() shouldStartWith repoRoot.toString()
                }
            }

            describe("WHEN planDir is called") {
                val result = structure.planDir(branch)

                it("THEN path ends with .ai_out/$branch/shared/plan") {
                    result.toString() shouldEndWith ".ai_out/$branch/shared/plan"
                }
            }

            describe("WHEN sharedContextMd is called") {
                val result = structure.sharedContextMd(branch)

                it("THEN path ends with .ai_out/$branch/shared/SHARED_CONTEXT.md") {
                    result.toString() shouldEndWith ".ai_out/$branch/shared/SHARED_CONTEXT.md"
                }
            }

            describe("AND role is 'PLANNER'") {
                val plannerRole = "PLANNER"

                describe("WHEN planningRoleDir is called") {
                    val result = structure.planningRoleDir(branch, plannerRole)

                    it("THEN path ends with .ai_out/$branch/planning/PLANNER") {
                        result.toString() shouldEndWith ".ai_out/$branch/planning/PLANNER"
                    }

                    it("THEN path starts with repo root") {
                        result.toString() shouldStartWith repoRoot.toString()
                    }
                }

                describe("WHEN planningPublicMd is called") {
                    val result = structure.planningPublicMd(branch, plannerRole)

                    it("THEN path ends with planning/PLANNER/PUBLIC.md") {
                        result.toString() shouldEndWith "planning/PLANNER/PUBLIC.md"
                    }
                }

                describe("WHEN planningPrivateMd is called") {
                    val result = structure.planningPrivateMd(branch, plannerRole)

                    it("THEN path ends with planning/PLANNER/PRIVATE.md") {
                        result.toString() shouldEndWith "planning/PLANNER/PRIVATE.md"
                    }
                }

                describe("WHEN planningSessionIdsDir is called") {
                    val result = structure.planningSessionIdsDir(branch, plannerRole)

                    it("THEN path ends with planning/PLANNER/session_ids") {
                        result.toString() shouldEndWith "planning/PLANNER/session_ids"
                    }
                }
            }

            describe("AND part is '$part' AND role is '$role'") {

                describe("WHEN phaseRoleDir is called") {
                    val result = structure.phaseRoleDir(branch, part, role)

                    it("THEN path ends with phases/$part/$role") {
                        result.toString() shouldEndWith "phases/$part/$role"
                    }

                    it("THEN path starts with repo root") {
                        result.toString() shouldStartWith repoRoot.toString()
                    }
                }

                describe("WHEN sessionIdsDir is called") {
                    val result = structure.sessionIdsDir(branch, part, role)

                    it("THEN path ends with phases/$part/$role/session_ids") {
                        result.toString() shouldEndWith "phases/$part/$role/session_ids"
                    }
                }

                describe("WHEN publicMd is called") {
                    val result = structure.publicMd(branch, part, role)

                    it("THEN path ends with phases/$part/$role/PUBLIC.md") {
                        result.toString() shouldEndWith "phases/$part/$role/PUBLIC.md"
                    }
                }

                describe("WHEN privateMd is called") {
                    val result = structure.privateMd(branch, part, role)

                    it("THEN path ends with phases/$part/$role/PRIVATE.md") {
                        result.toString() shouldEndWith "phases/$part/$role/PRIVATE.md"
                    }
                }
            }
        }
    }

    describe("GIVEN AiOutputStructure for ensureStructure") {

        describe("WHEN ensureStructure is called with branch and parts") {
            val (repoRoot, structure) = createTestFixture()
            val parts = listOf(
                Part("part_1", listOf("IMPLEMENTOR", "REVIEWER")),
                Part("part_2", listOf("IMPLEMENTOR")),
            )
            structure.ensureStructure(branch, parts)

            it("THEN harness_private directory exists") {
                Files.isDirectory(structure.harnessPrivateDir(branch)) shouldBe true
            }

            it("THEN shared directory exists") {
                Files.isDirectory(structure.sharedDir(branch)) shouldBe true
            }

            it("THEN plan directory exists") {
                Files.isDirectory(structure.planDir(branch)) shouldBe true
            }

            it("THEN phase role directory exists for part_1/IMPLEMENTOR") {
                Files.isDirectory(structure.phaseRoleDir(branch, "part_1", "IMPLEMENTOR")) shouldBe true
            }

            it("THEN phase role directory exists for part_1/REVIEWER") {
                Files.isDirectory(structure.phaseRoleDir(branch, "part_1", "REVIEWER")) shouldBe true
            }

            it("THEN phase role directory exists for part_2/IMPLEMENTOR") {
                Files.isDirectory(structure.phaseRoleDir(branch, "part_2", "IMPLEMENTOR")) shouldBe true
            }

            it("THEN session_ids directory exists for part_1/IMPLEMENTOR") {
                Files.isDirectory(structure.sessionIdsDir(branch, "part_1", "IMPLEMENTOR")) shouldBe true
            }

            it("THEN session_ids directory exists for part_1/REVIEWER") {
                Files.isDirectory(structure.sessionIdsDir(branch, "part_1", "REVIEWER")) shouldBe true
            }

            it("THEN session_ids directory exists for part_2/IMPLEMENTOR") {
                Files.isDirectory(structure.sessionIdsDir(branch, "part_2", "IMPLEMENTOR")) shouldBe true
            }
        }

        describe("WHEN ensureStructure is called with planningRoles") {
            val (repoRoot, structure) = createTestFixture()
            val planningRoles = listOf("PLANNER", "PLAN_REVIEWER")
            structure.ensureStructure(branch, emptyList(), planningRoles)

            it("THEN planning/PLANNER directory exists") {
                Files.isDirectory(structure.planningRoleDir(branch, "PLANNER")) shouldBe true
            }

            it("THEN planning/PLANNER/session_ids directory exists") {
                Files.isDirectory(structure.planningSessionIdsDir(branch, "PLANNER")) shouldBe true
            }

            it("THEN planning/PLAN_REVIEWER directory exists") {
                Files.isDirectory(structure.planningRoleDir(branch, "PLAN_REVIEWER")) shouldBe true
            }

            it("THEN planning/PLAN_REVIEWER/session_ids directory exists") {
                Files.isDirectory(structure.planningSessionIdsDir(branch, "PLAN_REVIEWER")) shouldBe true
            }
        }

        describe("WHEN ensureStructure is called twice") {
            it("THEN does not throw (idempotent)") {
                val (_, structure) = createTestFixture()
                val parts = listOf(Part("part_1", listOf("IMPLEMENTOR")))
                val planningRoles = listOf("PLANNER")

                structure.ensureStructure(branch, parts, planningRoles)
                structure.ensureStructure(branch, parts, planningRoles)
                // No exception means idempotent
            }
        }

        describe("WHEN ensureStructure is called with empty parts list") {
            val (repoRoot, structure) = createTestFixture()
            structure.ensureStructure(branch, emptyList())

            it("THEN shared directory still exists") {
                Files.isDirectory(structure.sharedDir(branch)) shouldBe true
            }

            it("THEN plan directory still exists") {
                Files.isDirectory(structure.planDir(branch)) shouldBe true
            }

            it("THEN harness_private directory still exists") {
                Files.isDirectory(structure.harnessPrivateDir(branch)) shouldBe true
            }

            it("THEN no phases directory is created") {
                val phasesDir = repoRoot.resolve(".ai_out").resolve(branch).resolve("phases")
                Files.exists(phasesDir) shouldBe false
            }
        }
    }
})
