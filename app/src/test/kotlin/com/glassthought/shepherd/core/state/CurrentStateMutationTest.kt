package com.glassthought.shepherd.core.state

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class CurrentStateMutationTest : AsgardDescribeSpec({

    // ── updateSubPartStatus ──

    describe("GIVEN a CurrentState with NOT_STARTED sub-parts") {

        fun createState() = CurrentState(
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

        describe("WHEN transitioning from NOT_STARTED to IN_PROGRESS") {
            val state = createState()
            state.updateSubPartStatus("ui_design", "impl", SubPartStatus.IN_PROGRESS)

            it("THEN sub-part status is IN_PROGRESS") {
                state.parts[0].subParts[0].status shouldBe SubPartStatus.IN_PROGRESS
            }
        }

        describe("WHEN transitioning NOT_STARTED to COMPLETED (invalid)") {
            val state = createState()

            it("THEN throws IllegalStateException") {
                val ex = shouldThrow<IllegalStateException> {
                    state.updateSubPartStatus("ui_design", "impl", SubPartStatus.COMPLETED)
                }
                ex.message shouldContain "NOT_STARTED"
            }
        }

        describe("WHEN transitioning NOT_STARTED to FAILED (invalid)") {
            val state = createState()

            it("THEN throws IllegalStateException") {
                val ex = shouldThrow<IllegalStateException> {
                    state.updateSubPartStatus("ui_design", "impl", SubPartStatus.FAILED)
                }
                ex.message shouldContain "NOT_STARTED"
            }
        }
    }

    describe("GIVEN a CurrentState with IN_PROGRESS sub-part") {

        fun createInProgressState() = CurrentState(
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
                            status = SubPartStatus.IN_PROGRESS,
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN transitioning from IN_PROGRESS to COMPLETED") {
            val state = createInProgressState()
            state.updateSubPartStatus("ui_design", "impl", SubPartStatus.COMPLETED)

            it("THEN sub-part status is COMPLETED") {
                state.parts[0].subParts[0].status shouldBe SubPartStatus.COMPLETED
            }
        }

        describe("WHEN transitioning from IN_PROGRESS to FAILED") {
            val state = createInProgressState()
            state.updateSubPartStatus("ui_design", "impl", SubPartStatus.FAILED)

            it("THEN sub-part status is FAILED") {
                state.parts[0].subParts[0].status shouldBe SubPartStatus.FAILED
            }
        }

        describe("WHEN transitioning from IN_PROGRESS to NOT_STARTED (invalid)") {
            val state = createInProgressState()

            it("THEN throws IllegalStateException") {
                val ex = shouldThrow<IllegalStateException> {
                    state.updateSubPartStatus("ui_design", "impl", SubPartStatus.NOT_STARTED)
                }
                ex.message shouldContain "IN_PROGRESS"
            }
        }
    }

    describe("GIVEN a CurrentState with COMPLETED sub-part (terminal)") {

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
                            status = SubPartStatus.COMPLETED,
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN attempting any transition") {
            it("THEN throws IllegalStateException mentioning terminal") {
                val ex = shouldThrow<IllegalStateException> {
                    state.updateSubPartStatus("part1", "impl", SubPartStatus.IN_PROGRESS)
                }
                ex.message shouldContain "COMPLETED is terminal"
            }
        }
    }

    describe("GIVEN a CurrentState with FAILED sub-part (terminal)") {

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
                            status = SubPartStatus.FAILED,
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN attempting any transition") {
            it("THEN throws IllegalStateException mentioning terminal") {
                val ex = shouldThrow<IllegalStateException> {
                    state.updateSubPartStatus("part1", "impl", SubPartStatus.IN_PROGRESS)
                }
                ex.message shouldContain "FAILED is terminal"
            }
        }
    }

    describe("GIVEN an invalid part name") {
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

        describe("WHEN updating with non-existent part name") {
            it("THEN throws IllegalArgumentException") {
                val ex = shouldThrow<IllegalArgumentException> {
                    state.updateSubPartStatus("nonexistent", "impl", SubPartStatus.IN_PROGRESS)
                }
                ex.message shouldContain "nonexistent"
            }
        }

        describe("WHEN updating with non-existent sub-part name") {
            it("THEN throws IllegalArgumentException") {
                val ex = shouldThrow<IllegalArgumentException> {
                    state.updateSubPartStatus("part1", "nonexistent", SubPartStatus.IN_PROGRESS)
                }
                ex.message shouldContain "nonexistent"
            }
        }
    }

    // ── incrementIteration ──

    describe("GIVEN a CurrentState with a reviewer sub-part at iteration 0") {

        fun createState() = CurrentState(
            parts = mutableListOf(
                Part(
                    name = "ui_design",
                    phase = Phase.EXECUTION,
                    description = "Design the UI",
                    subParts = listOf(
                        SubPart(
                            name = "review",
                            role = "UI_REVIEWER",
                            agentType = "ClaudeCode",
                            model = "sonnet",
                            status = SubPartStatus.IN_PROGRESS,
                            iteration = IterationConfig(max = 3, current = 0),
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN incrementing iteration once") {
            val state = createState()
            state.incrementIteration("ui_design", "review")

            it("THEN iteration.current is 1") {
                state.parts[0].subParts[0].iteration!!.current shouldBe 1
            }

            it("THEN iteration.max is unchanged") {
                state.parts[0].subParts[0].iteration!!.max shouldBe 3
            }
        }

        describe("WHEN incrementing iteration twice") {
            val state = createState()
            state.incrementIteration("ui_design", "review")
            state.incrementIteration("ui_design", "review")

            it("THEN iteration.current is 2") {
                state.parts[0].subParts[0].iteration!!.current shouldBe 2
            }
        }
    }

    describe("GIVEN a sub-part without iteration config (doer)") {
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
                            status = SubPartStatus.IN_PROGRESS,
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN incrementing iteration") {
            it("THEN throws IllegalArgumentException") {
                val ex = shouldThrow<IllegalArgumentException> {
                    state.incrementIteration("part1", "impl")
                }
                ex.message shouldContain "no iteration config"
            }
        }
    }

    // ── addSessionRecord ──

    describe("GIVEN a CurrentState with no session records") {

        fun createState() = CurrentState(
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
                            status = SubPartStatus.IN_PROGRESS,
                        ),
                    ),
                ),
            ),
        )

        val sessionRecord = SessionRecord(
            handshakeGuid = "handshake.abc-123",
            agentSession = AgentSessionInfo(id = "session-001"),
            agentType = "ClaudeCode",
            model = "sonnet",
            timestamp = "2026-03-19T10:00:00Z",
        )

        describe("WHEN adding a session record") {
            val state = createState()
            state.addSessionRecord("ui_design", "impl", sessionRecord)

            it("THEN sessionIds has one entry") {
                state.parts[0].subParts[0].sessionIds!! shouldHaveSize 1
            }

            it("THEN session record matches the added record") {
                state.parts[0].subParts[0].sessionIds!![0] shouldBe sessionRecord
            }
        }

        describe("WHEN adding two session records") {
            val state = createState()
            val secondRecord = sessionRecord.copy(handshakeGuid = "handshake.def-456")

            state.addSessionRecord("ui_design", "impl", sessionRecord)
            state.addSessionRecord("ui_design", "impl", secondRecord)

            it("THEN sessionIds has two entries") {
                state.parts[0].subParts[0].sessionIds!! shouldHaveSize 2
            }

            it("THEN first record is the original") {
                state.parts[0].subParts[0].sessionIds!![0].handshakeGuid shouldBe "handshake.abc-123"
            }

            it("THEN second record is the newly added one") {
                state.parts[0].subParts[0].sessionIds!![1].handshakeGuid shouldBe "handshake.def-456"
            }
        }
    }

    // ── appendExecutionParts ──

    describe("GIVEN a CurrentState with one planning part") {

        fun createState() = CurrentState(
            parts = mutableListOf(
                Part(
                    name = "planning",
                    phase = Phase.PLANNING,
                    description = "Plan the workflow",
                    subParts = listOf(
                        SubPart(
                            name = "plan",
                            role = "PLANNER",
                            agentType = "ClaudeCode",
                            model = "opus",
                            status = SubPartStatus.COMPLETED,
                        ),
                    ),
                ),
            ),
        )

        val executionParts = listOf(
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
                ),
            ),
            Part(
                name = "backend",
                phase = Phase.EXECUTION,
                description = "Implement backend",
                subParts = listOf(
                    SubPart(
                        name = "impl",
                        role = "BACKEND_DEV",
                        agentType = "ClaudeCode",
                        model = "opus",
                        status = SubPartStatus.NOT_STARTED,
                    ),
                ),
            ),
        )

        describe("WHEN appending execution parts") {
            val state = createState()
            state.appendExecutionParts(executionParts)

            it("THEN parts list has three entries (1 planning + 2 execution)") {
                state.parts shouldHaveSize 3
            }

            it("THEN first part is still the planning part") {
                state.parts[0].name shouldBe "planning"
                state.parts[0].phase shouldBe Phase.PLANNING
            }

            it("THEN second part is ui_design execution part") {
                state.parts[1].name shouldBe "ui_design"
                state.parts[1].phase shouldBe Phase.EXECUTION
            }

            it("THEN third part is backend execution part") {
                state.parts[2].name shouldBe "backend"
                state.parts[2].phase shouldBe Phase.EXECUTION
            }
        }
    }

    // ── Mutation does not affect other sub-parts ──

    describe("GIVEN a part with two sub-parts") {

        fun createState() = CurrentState(
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

        describe("WHEN updating only impl sub-part status") {
            val state = createState()
            state.updateSubPartStatus("ui_design", "impl", SubPartStatus.IN_PROGRESS)

            it("THEN review sub-part status is unchanged") {
                state.parts[0].subParts[1].status shouldBe SubPartStatus.NOT_STARTED
            }

            it("THEN review sub-part iteration is unchanged") {
                state.parts[0].subParts[1].iteration!!.current shouldBe 0
            }
        }
    }
})
