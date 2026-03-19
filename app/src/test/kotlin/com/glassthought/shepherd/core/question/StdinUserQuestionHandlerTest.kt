package com.glassthought.shepherd.core.question

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.infra.DispatcherProvider
import com.glassthought.shepherd.core.state.SubPartRole
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.Dispatchers
import java.io.BufferedReader
import java.io.PrintWriter
import java.io.StringReader
import java.io.StringWriter

class StdinUserQuestionHandlerTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        val testContext = UserQuestionContext(
            question = "How should I handle the responsive layout?",
            partName = "ui_design",
            subPartName = "impl",
            subPartRole = SubPartRole.DOER,
            handshakeGuid = HandshakeGuid("handshake.test-guid-1234"),
        )

        // WHY Dispatchers.Unconfined: tests use in-memory StringReader/StringWriter,
        // no actual blocking I/O, so we avoid unnecessary thread switching.
        val testDispatcherProvider = DispatcherProvider { Dispatchers.Unconfined }

        describe("GIVEN question context") {

            describe("WHEN single-line answer followed by empty line") {

                it("THEN returns the answer text") {
                    val result = askQuestion(
                        input = "Use CSS Grid approach\n\n",
                        context = testContext,
                        dispatcherProvider = testDispatcherProvider,
                    )

                    result.answer shouldBe "Use CSS Grid approach"
                }
            }

            describe("WHEN multi-line answer followed by empty line") {

                it("THEN returns joined multi-line text") {
                    val result = askQuestion(
                        input = "Line one\nLine two\nLine three\n\n",
                        context = testContext,
                        dispatcherProvider = testDispatcherProvider,
                    )

                    result.answer shouldBe "Line one\nLine two\nLine three"
                }
            }

            describe("WHEN reader returns null (EOF)") {

                it("THEN returns empty string") {
                    val result = askQuestion(
                        input = "",
                        context = testContext,
                        dispatcherProvider = testDispatcherProvider,
                    )

                    result.answer shouldBe ""
                }
            }

            describe("WHEN prompt is displayed") {
                val result = askQuestion(
                    input = "answer\n\n",
                    context = testContext,
                    dispatcherProvider = testDispatcherProvider,
                )

                it("THEN stdout contains part name") {
                    result.output shouldContain "Part: ui_design"
                }

                it("THEN stdout contains sub-part name and role") {
                    result.output shouldContain "Sub-part: impl (DOER)"
                }

                it("THEN stdout contains handshakeGuid") {
                    result.output shouldContain "HandshakeGuid: handshake.test-guid-1234"
                }

                it("THEN stdout contains question text") {
                    result.output shouldContain "How should I handle the responsive layout?"
                }

                it("THEN stdout contains submission instructions") {
                    result.output shouldContain "Your answer (press Enter twice to submit):"
                }

                it("THEN stdout contains AGENT QUESTION header") {
                    result.output shouldContain "AGENT QUESTION"
                }
            }
        }
    },
)

// ── Helpers ─────────────────────────────────────────────────────────────────

private data class AskResult(
    val answer: String,
    val output: String,
)

private suspend fun askQuestion(
    input: String,
    context: UserQuestionContext,
    dispatcherProvider: DispatcherProvider,
): AskResult {
    val reader = BufferedReader(StringReader(input))
    val stringWriter = StringWriter()
    val writer = PrintWriter(stringWriter, true)

    val handler = StdinUserQuestionHandler(
        reader = reader,
        writer = writer,
        dispatcherProvider = dispatcherProvider,
    )

    val answer = handler.handleQuestion(context)

    return AskResult(
        answer = answer,
        output = stringWriter.toString(),
    )
}
