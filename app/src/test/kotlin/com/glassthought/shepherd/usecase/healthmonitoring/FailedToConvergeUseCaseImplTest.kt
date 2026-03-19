package com.glassthought.shepherd.usecase.healthmonitoring

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.data.HarnessTimeoutConfig
import com.glassthought.shepherd.core.infra.ConsoleOutput
import com.glassthought.shepherd.core.infra.UserInputReader
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

// ── Test Fakes ──────────────────────────────────────────────────────────────

internal class FakeUserInputReader(
    private val response: String?,
) : UserInputReader {
    override suspend fun readLine(): String? = response
}

// ── Tests ───────────────────────────────────────────────────────────────────

class FailedToConvergeUseCaseImplTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        val defaultConfig = HarnessTimeoutConfig.defaults()

        describe("GIVEN iteration budget exhausted") {

            describe("WHEN user enters \"y\"") {
                it("THEN returns true") {
                    val result = buildAndAsk(userInput = "y", config = defaultConfig)
                    result.returnValue shouldBe true
                }
            }

            describe("WHEN user enters \"Y\"") {
                it("THEN returns true") {
                    val result = buildAndAsk(userInput = "Y", config = defaultConfig)
                    result.returnValue shouldBe true
                }
            }

            describe("WHEN user enters \"N\"") {
                it("THEN returns false") {
                    val result = buildAndAsk(userInput = "N", config = defaultConfig)
                    result.returnValue shouldBe false
                }
            }

            describe("WHEN user enters empty string") {
                it("THEN returns false") {
                    val result = buildAndAsk(userInput = "", config = defaultConfig)
                    result.returnValue shouldBe false
                }
            }

            describe("WHEN readLine returns null") {
                it("THEN returns false") {
                    val result = buildAndAsk(userInput = null, config = defaultConfig)
                    result.returnValue shouldBe false
                }
            }

            describe("WHEN prompt is displayed") {
                it("THEN prompt contains correct iteration counts and increment") {
                    val result = buildAndAsk(
                        userInput = "N",
                        config = defaultConfig,
                        currentMax = 10,
                        iterationsUsed = 10,
                    )
                    result.fakeConsole.printedMessages.first() shouldContain "10/10"
                    result.fakeConsole.printedMessages.first() shouldContain
                        "Grant ${defaultConfig.failedToConvergeIterationIncrement} more iterations?"
                }
            }

            describe("GIVEN custom iteration increment") {
                val customConfig = HarnessTimeoutConfig(failedToConvergeIterationIncrement = 5)

                describe("WHEN prompt is displayed") {
                    it("THEN prompt uses custom increment value") {
                        val result = buildAndAsk(
                            userInput = "N",
                            config = customConfig,
                            currentMax = 6,
                            iterationsUsed = 6,
                        )
                        result.fakeConsole.printedMessages.first() shouldContain "Grant 5 more iterations?"
                    }
                }
            }
        }
    },
)

// ── Helpers ─────────────────────────────────────────────────────────────────

private data class AskResult(
    val returnValue: Boolean,
    val fakeConsole: FakeConsoleOutput,
)

private suspend fun buildAndAsk(
    userInput: String?,
    config: HarnessTimeoutConfig,
    currentMax: Int = 8,
    iterationsUsed: Int = 8,
): AskResult {
    val fakeConsole = FakeConsoleOutput()
    val fakeReader = FakeUserInputReader(response = userInput)

    val useCase = FailedToConvergeUseCaseImpl(
        consoleOutput = fakeConsole,
        userInputReader = fakeReader,
        config = config,
    )

    val result = useCase.askForMoreIterations(
        currentMax = currentMax,
        iterationsUsed = iterationsUsed,
    )

    return AskResult(
        returnValue = result,
        fakeConsole = fakeConsole,
    )
}
