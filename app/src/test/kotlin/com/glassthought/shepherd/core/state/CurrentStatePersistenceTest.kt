package com.glassthought.shepherd.core.state

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.fasterxml.jackson.module.kotlin.readValue
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path

class CurrentStatePersistenceTest : AsgardDescribeSpec({

    val mapper = ShepherdObjectMapper.create()

    // ── Flush writes valid JSON to target path ──

    describe("GIVEN a CurrentState with execution parts") {

        val state = CurrentState(
            parts = mutableListOf(
                Part(
                    name = "ui_design",
                    phase = Phase.EXECUTION,
                    description = "Design the UI",
                    subParts = listOf(
                        SubPart(
                            name = "impl",
                            role = "UI_DESIGNER",
                            agentType = "ClaudeCode",
                            model = "sonnet",
                            status = SubPartStatus.NOT_STARTED,
                        ),
                        SubPart(
                            name = "review",
                            role = "UI_REVIEWER",
                            agentType = "ClaudeCode",
                            model = "sonnet",
                            status = SubPartStatus.NOT_STARTED,
                            iteration = IterationConfig(max = 3, current = 0),
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN flushing to disk") {
            val tempDir = Files.createTempDirectory("persistence-test")
            val aiOutputStructure = AiOutputStructure(repoRoot = tempDir, branch = "test-branch")
            val persistence = CurrentStatePersistenceImpl(aiOutputStructure)

            it("THEN creates current_state.json at expected path") {
                persistence.flush(state)

                val targetPath = aiOutputStructure.currentStateJson()
                Files.exists(targetPath) shouldBe true
            }
        }

        describe("AND WHEN reading back flushed file") {
            val tempDir = Files.createTempDirectory("persistence-roundtrip")
            val aiOutputStructure = AiOutputStructure(repoRoot = tempDir, branch = "test-branch")
            val persistence = CurrentStatePersistenceImpl(aiOutputStructure)

            it("THEN file contains valid JSON that deserializes to equal CurrentState") {
                persistence.flush(state)

                val targetPath = aiOutputStructure.currentStateJson()
                val jsonContent = Files.readString(targetPath)
                val deserialized = mapper.readValue<CurrentState>(jsonContent)

                deserialized shouldBe state
            }
        }

        describe("AND WHEN flushing includes all expected fields") {
            val tempDir = Files.createTempDirectory("persistence-fields")
            val aiOutputStructure = AiOutputStructure(repoRoot = tempDir, branch = "test-branch")
            val persistence = CurrentStatePersistenceImpl(aiOutputStructure)

            it("THEN JSON contains status field") {
                persistence.flush(state)

                val jsonContent = Files.readString(aiOutputStructure.currentStateJson())
                jsonContent shouldContain "\"status\""
            }
        }
    }

    // ── Flush overwrites existing file ──

    describe("GIVEN an existing current_state.json") {

        val initialState = CurrentState(
            parts = mutableListOf(
                Part(
                    name = "part1",
                    phase = Phase.EXECUTION,
                    description = "Initial part",
                    subParts = listOf(
                        SubPart(
                            name = "impl",
                            role = "DOER",
                            agentType = "ClaudeCode",
                            model = "sonnet",
                            status = SubPartStatus.NOT_STARTED,
                        ),
                    ),
                ),
            ),
        )

        val updatedState = CurrentState(
            parts = mutableListOf(
                Part(
                    name = "part1",
                    phase = Phase.EXECUTION,
                    description = "Initial part",
                    subParts = listOf(
                        SubPart(
                            name = "impl",
                            role = "DOER",
                            agentType = "ClaudeCode",
                            model = "sonnet",
                            status = SubPartStatus.IN_PROGRESS,
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN flushing updated state") {
            val tempDir = Files.createTempDirectory("persistence-overwrite")
            val aiOutputStructure = AiOutputStructure(repoRoot = tempDir, branch = "test-branch")
            val persistence = CurrentStatePersistenceImpl(aiOutputStructure)

            it("THEN file reflects the updated state") {
                persistence.flush(initialState)
                persistence.flush(updatedState)

                val jsonContent = Files.readString(aiOutputStructure.currentStateJson())
                val deserialized = mapper.readValue<CurrentState>(jsonContent)

                deserialized.parts[0].subParts[0].status shouldBe SubPartStatus.IN_PROGRESS
            }
        }
    }

    // ── Flush does not leave temp files ──

    describe("GIVEN a successful flush") {
        val state = CurrentState(
            parts = mutableListOf(
                Part(
                    name = "part1",
                    phase = Phase.EXECUTION,
                    description = "Part",
                    subParts = listOf(
                        SubPart(
                            name = "impl",
                            role = "DOER",
                            agentType = "ClaudeCode",
                            model = "sonnet",
                            status = SubPartStatus.NOT_STARTED,
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN checking the harness_private directory after flush") {
            val tempDir = Files.createTempDirectory("persistence-no-temp")
            val aiOutputStructure = AiOutputStructure(repoRoot = tempDir, branch = "test-branch")
            val persistence = CurrentStatePersistenceImpl(aiOutputStructure)

            it("THEN no .tmp files remain") {
                persistence.flush(state)

                val harnessDir = aiOutputStructure.currentStateJson().parent
                val tmpFiles = Files.list(harnessDir).use { stream ->
                    stream.filter { it.toString().endsWith(".tmp") }.toList()
                }
                tmpFiles shouldHaveSize 0
            }
        }
    }

    // ── Round-trip: init → flush → read ──

    describe("GIVEN a state created by CurrentStateInitializer") {

        val initializer = CurrentStateInitializerImpl()
        val workflowDef = com.glassthought.shepherd.core.workflow.WorkflowDefinition(
            name = "roundtrip-test",
            parts = listOf(
                Part(
                    name = "ui",
                    phase = Phase.EXECUTION,
                    description = "UI work",
                    subParts = listOf(
                        SubPart(name = "impl", role = "DESIGNER", agentType = "ClaudeCode", model = "sonnet"),
                        SubPart(
                            name = "review",
                            role = "REVIEWER",
                            agentType = "ClaudeCode",
                            model = "sonnet",
                            iteration = IterationConfig(max = 5),
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN init → flush → read back") {
            val tempDir = Files.createTempDirectory("persistence-roundtrip-init")
            val aiOutputStructure = AiOutputStructure(repoRoot = tempDir, branch = "test-branch")
            val persistence = CurrentStatePersistenceImpl(aiOutputStructure)
            val state = initializer.createInitialState(workflowDef)

            it("THEN round-tripped state equals original") {
                persistence.flush(state)

                val jsonContent = Files.readString(aiOutputStructure.currentStateJson())
                val deserialized = mapper.readValue<CurrentState>(jsonContent)

                deserialized shouldBe state
            }
        }
    }

    // ── Null fields omitted (NON_NULL) ──

    describe("GIVEN a state with null sessionIds") {
        val state = CurrentState(
            parts = mutableListOf(
                Part(
                    name = "p1",
                    phase = Phase.EXECUTION,
                    description = "Part",
                    subParts = listOf(
                        SubPart(
                            name = "impl",
                            role = "DOER",
                            agentType = "ClaudeCode",
                            model = "sonnet",
                            status = SubPartStatus.NOT_STARTED,
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN flushing") {
            val tempDir = Files.createTempDirectory("persistence-nonnull")
            val aiOutputStructure = AiOutputStructure(repoRoot = tempDir, branch = "test-branch")
            val persistence = CurrentStatePersistenceImpl(aiOutputStructure)

            it("THEN JSON does not contain sessionIds key") {
                persistence.flush(state)

                val jsonContent = Files.readString(aiOutputStructure.currentStateJson())
                jsonContent.contains("sessionIds") shouldBe false
            }
        }
    }
})
