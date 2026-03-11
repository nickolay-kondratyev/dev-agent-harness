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
    val subPart = "1_impl"
    val part = "ui_design"

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

        describe("WHEN subPart is blank") {
            it("THEN planningSubPartDir throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    structure.planningSubPartDir(branch, "")
                }
            }

            it("THEN subPartDir throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    structure.subPartDir(branch, part, "")
                }
            }
        }

        describe("WHEN part is blank") {
            it("THEN subPartDir throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    structure.subPartDir(branch, "", subPart)
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

            describe("AND planning sub-part is '1_plan'") {
                val planSubPart = "1_plan"

                describe("WHEN planningSubPartDir is called") {
                    val result = structure.planningSubPartDir(branch, planSubPart)

                    it("THEN path ends with .ai_out/$branch/planning/1_plan") {
                        result.toString() shouldEndWith ".ai_out/$branch/planning/1_plan"
                    }

                    it("THEN path starts with repo root") {
                        result.toString() shouldStartWith repoRoot.toString()
                    }
                }

                describe("WHEN planningPublicMd is called") {
                    val result = structure.planningPublicMd(branch, planSubPart)

                    it("THEN path ends with planning/1_plan/PUBLIC.md") {
                        result.toString() shouldEndWith "planning/1_plan/PUBLIC.md"
                    }
                }

                describe("WHEN planningSessionIdsDir is called") {
                    val result = structure.planningSessionIdsDir(branch, planSubPart)

                    it("THEN path ends with planning/1_plan/session_ids") {
                        result.toString() shouldEndWith "planning/1_plan/session_ids"
                    }
                }
            }

            describe("AND part is '$part' AND subPart is '$subPart'") {

                describe("WHEN subPartDir is called") {
                    val result = structure.subPartDir(branch, part, subPart)

                    it("THEN path ends with phases/$part/$subPart") {
                        result.toString() shouldEndWith "phases/$part/$subPart"
                    }

                    it("THEN path starts with repo root") {
                        result.toString() shouldStartWith repoRoot.toString()
                    }
                }

                describe("WHEN sessionIdsDir is called") {
                    val result = structure.sessionIdsDir(branch, part, subPart)

                    it("THEN path ends with phases/$part/$subPart/session_ids") {
                        result.toString() shouldEndWith "phases/$part/$subPart/session_ids"
                    }
                }

                describe("WHEN publicMd is called") {
                    val result = structure.publicMd(branch, part, subPart)

                    it("THEN path ends with phases/$part/$subPart/PUBLIC.md") {
                        result.toString() shouldEndWith "phases/$part/$subPart/PUBLIC.md"
                    }
                }
            }
        }
    }

    describe("GIVEN AiOutputStructure for ensureStructure") {

        describe("WHEN ensureStructure is called with branch and parts") {
            val (repoRoot, structure) = createTestFixture()
            val parts = listOf(
                Part("ui_design", listOf("1_impl", "2_review")),
                Part("backend", listOf("1_impl")),
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

            it("THEN sub-part directory exists for ui_design/1_impl") {
                Files.isDirectory(structure.subPartDir(branch, "ui_design", "1_impl")) shouldBe true
            }

            it("THEN sub-part directory exists for ui_design/2_review") {
                Files.isDirectory(structure.subPartDir(branch, "ui_design", "2_review")) shouldBe true
            }

            it("THEN sub-part directory exists for backend/1_impl") {
                Files.isDirectory(structure.subPartDir(branch, "backend", "1_impl")) shouldBe true
            }

            it("THEN session_ids directory exists for ui_design/1_impl") {
                Files.isDirectory(structure.sessionIdsDir(branch, "ui_design", "1_impl")) shouldBe true
            }

            it("THEN session_ids directory exists for ui_design/2_review") {
                Files.isDirectory(structure.sessionIdsDir(branch, "ui_design", "2_review")) shouldBe true
            }

            it("THEN session_ids directory exists for backend/1_impl") {
                Files.isDirectory(structure.sessionIdsDir(branch, "backend", "1_impl")) shouldBe true
            }
        }

        describe("WHEN ensureStructure is called with planningSubParts") {
            val (_, structure) = createTestFixture()
            val planningSubParts = listOf("1_plan", "2_plan_review")
            structure.ensureStructure(branch, emptyList(), planningSubParts)

            it("THEN planning/1_plan directory exists") {
                Files.isDirectory(structure.planningSubPartDir(branch, "1_plan")) shouldBe true
            }

            it("THEN planning/1_plan/session_ids directory exists") {
                Files.isDirectory(structure.planningSessionIdsDir(branch, "1_plan")) shouldBe true
            }

            it("THEN planning/2_plan_review directory exists") {
                Files.isDirectory(structure.planningSubPartDir(branch, "2_plan_review")) shouldBe true
            }

            it("THEN planning/2_plan_review/session_ids directory exists") {
                Files.isDirectory(structure.planningSessionIdsDir(branch, "2_plan_review")) shouldBe true
            }
        }

        describe("WHEN ensureStructure is called twice") {
            it("THEN does not throw (idempotent)") {
                val (_, structure) = createTestFixture()
                val parts = listOf(Part("ui_design", listOf("1_impl")))
                val planningSubParts = listOf("1_plan")

                structure.ensureStructure(branch, parts, planningSubParts)
                structure.ensureStructure(branch, parts, planningSubParts)
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
