package com.glassthought.shepherd.core.state

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class CurrentStateDerivedStatusTest : AsgardDescribeSpec({

    // ── isPartCompleted ──

    describe("GIVEN a part where all sub-parts are COMPLETED") {
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
                            agentType = "CLAUDE_CODE",
                            model = "sonnet",
                            status = SubPartStatus.COMPLETED,
                        ),
                        SubPart(
                            name = "review",
                            role = "UI_REVIEWER",
                            agentType = "CLAUDE_CODE",
                            model = "sonnet",
                            status = SubPartStatus.COMPLETED,
                            iteration = IterationConfig(max = 3, current = 2),
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN checking isPartCompleted") {
            it("THEN returns true") {
                state.isPartCompleted("ui_design") shouldBe true
            }
        }

        describe("WHEN checking isPartFailed") {
            it("THEN returns false") {
                state.isPartFailed("ui_design") shouldBe false
            }
        }
    }

    describe("GIVEN a part where one sub-part is IN_PROGRESS") {
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
                            agentType = "CLAUDE_CODE",
                            model = "sonnet",
                            status = SubPartStatus.COMPLETED,
                        ),
                        SubPart(
                            name = "review",
                            role = "UI_REVIEWER",
                            agentType = "CLAUDE_CODE",
                            model = "sonnet",
                            status = SubPartStatus.IN_PROGRESS,
                            iteration = IterationConfig(max = 3, current = 1),
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN checking isPartCompleted") {
            it("THEN returns false") {
                state.isPartCompleted("ui_design") shouldBe false
            }
        }

        describe("WHEN checking isPartFailed") {
            it("THEN returns false") {
                state.isPartFailed("ui_design") shouldBe false
            }
        }
    }

    // ── isPartFailed ──

    describe("GIVEN a part where one sub-part is FAILED") {
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
                            agentType = "CLAUDE_CODE",
                            model = "sonnet",
                            status = SubPartStatus.IN_PROGRESS,
                        ),
                        SubPart(
                            name = "review",
                            role = "UI_REVIEWER",
                            agentType = "CLAUDE_CODE",
                            model = "sonnet",
                            status = SubPartStatus.FAILED,
                            iteration = IterationConfig(max = 3, current = 1),
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN checking isPartFailed") {
            it("THEN returns true") {
                state.isPartFailed("ui_design") shouldBe true
            }
        }

        describe("WHEN checking isPartCompleted") {
            it("THEN returns false") {
                state.isPartCompleted("ui_design") shouldBe false
            }
        }
    }

    // ── findResumePoint ──

    describe("GIVEN a state with all parts completed") {
        val state = CurrentState(
            parts = mutableListOf(
                Part(
                    name = "part1",
                    phase = Phase.EXECUTION,
                    description = "First part",
                    subParts = listOf(
                        SubPart(
                            name = "impl",
                            role = "DOER",
                            agentType = "CLAUDE_CODE",
                            model = "sonnet",
                            status = SubPartStatus.COMPLETED,
                        ),
                    ),
                ),
                Part(
                    name = "part2",
                    phase = Phase.EXECUTION,
                    description = "Second part",
                    subParts = listOf(
                        SubPart(
                            name = "impl",
                            role = "DOER",
                            agentType = "CLAUDE_CODE",
                            model = "sonnet",
                            status = SubPartStatus.COMPLETED,
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN finding resume point") {
            it("THEN returns null") {
                state.findResumePoint().shouldBeNull()
            }
        }
    }

    describe("GIVEN a state with first part completed and second part NOT_STARTED") {
        val state = CurrentState(
            parts = mutableListOf(
                Part(
                    name = "part1",
                    phase = Phase.EXECUTION,
                    description = "First part",
                    subParts = listOf(
                        SubPart(
                            name = "impl",
                            role = "DOER",
                            agentType = "CLAUDE_CODE",
                            model = "sonnet",
                            status = SubPartStatus.COMPLETED,
                        ),
                    ),
                ),
                Part(
                    name = "part2",
                    phase = Phase.EXECUTION,
                    description = "Second part",
                    subParts = listOf(
                        SubPart(
                            name = "impl",
                            role = "DOER",
                            agentType = "CLAUDE_CODE",
                            model = "sonnet",
                            status = SubPartStatus.NOT_STARTED,
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN finding resume point") {
            val resumePoint = state.findResumePoint()

            it("THEN returns the second part") {
                resumePoint.shouldNotBeNull()
                resumePoint.name shouldBe "part2"
            }
        }
    }

    describe("GIVEN a state with first part IN_PROGRESS") {
        val state = CurrentState(
            parts = mutableListOf(
                Part(
                    name = "part1",
                    phase = Phase.EXECUTION,
                    description = "First part",
                    subParts = listOf(
                        SubPart(
                            name = "impl",
                            role = "DOER",
                            agentType = "CLAUDE_CODE",
                            model = "sonnet",
                            status = SubPartStatus.IN_PROGRESS,
                        ),
                    ),
                ),
                Part(
                    name = "part2",
                    phase = Phase.EXECUTION,
                    description = "Second part",
                    subParts = listOf(
                        SubPart(
                            name = "impl",
                            role = "DOER",
                            agentType = "CLAUDE_CODE",
                            model = "sonnet",
                            status = SubPartStatus.NOT_STARTED,
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN finding resume point") {
            val resumePoint = state.findResumePoint()

            it("THEN returns the first part (first non-completed)") {
                resumePoint.shouldNotBeNull()
                resumePoint.name shouldBe "part1"
            }
        }
    }

    // ── Non-existent part name ──

    describe("GIVEN a non-existent part name") {
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
                            agentType = "CLAUDE_CODE",
                            model = "sonnet",
                            status = SubPartStatus.COMPLETED,
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN calling isPartCompleted") {
            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    state.isPartCompleted("nonexistent")
                }
            }
        }

        describe("WHEN calling isPartFailed") {
            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    state.isPartFailed("nonexistent")
                }
            }
        }
    }
})
