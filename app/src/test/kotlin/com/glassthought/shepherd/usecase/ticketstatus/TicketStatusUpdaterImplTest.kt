package com.glassthought.shepherd.usecase.ticketstatus

import com.asgard.core.processRunner.ProcessRunner
import com.asgard.core.processRunner.ProcessResult
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.time.Duration

// ── Test Fakes ──────────────────────────────────────────────────────────────

/**
 * A recording [ProcessRunner] that captures the command passed to [runProcess]
 * and returns an empty string (success).
 */
private class RecordingProcessRunner : ProcessRunner {

    val invocations = mutableListOf<List<String?>>()

    override suspend fun runProcess(vararg input: String?): String {
        invocations.add(input.toList())
        return ""
    }

    override suspend fun runScript(script: com.asgard.core.file.File): String {
        error("Not implemented in recording fake")
    }

    override suspend fun runProcessV2(
        timeout: Duration,
        vararg input: String?,
    ): ProcessResult {
        error("Not implemented in recording fake")
    }
}

/** Signals a simulated CLI failure in [FailingProcessRunner]. */
private class TicketCliFailure : Exception("ticket CLI failed")

/**
 * A [ProcessRunner] that always throws on [runProcess], simulating CLI failure.
 */
private class FailingProcessRunner : ProcessRunner {

    override suspend fun runProcess(vararg input: String?): String {
        throw TicketCliFailure()
    }

    override suspend fun runScript(script: com.asgard.core.file.File): String {
        error("Not implemented in failing fake")
    }

    override suspend fun runProcessV2(
        timeout: Duration,
        vararg input: String?,
    ): ProcessResult {
        error("Not implemented in failing fake")
    }
}

// ── Tests ───────────────────────────────────────────────────────────────────

class TicketStatusUpdaterImplTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        val testCases = listOf("abc-123", "nid_xyz_task")

        testCases.forEach { ticketId ->
            describe("GIVEN a TicketStatusUpdaterImpl with ticketId '$ticketId'") {

                describe("WHEN markDone is called") {
                    val processRunner = RecordingProcessRunner()
                    val updater = TicketStatusUpdaterImpl(
                        ticketId = ticketId,
                        processRunner = processRunner,
                        outFactory = outFactory,
                    )

                    it("THEN it invokes 'ticket close $ticketId' via ProcessRunner") {
                        updater.markDone()

                        processRunner.invocations.size shouldBe 1
                        processRunner.invocations[0] shouldBe listOf("ticket", "close", ticketId)
                    }
                }
            }
        }

        describe("GIVEN a TicketStatusUpdaterImpl with a failing ProcessRunner") {

            describe("WHEN markDone is called") {
                val updater = TicketStatusUpdaterImpl(
                    ticketId = "fail-ticket",
                    processRunner = FailingProcessRunner(),
                    outFactory = outFactory,
                )

                it("THEN the exception propagates to the caller") {
                    shouldThrow<TicketCliFailure> {
                        updater.markDone()
                    }
                }
            }
        }
    },
)
