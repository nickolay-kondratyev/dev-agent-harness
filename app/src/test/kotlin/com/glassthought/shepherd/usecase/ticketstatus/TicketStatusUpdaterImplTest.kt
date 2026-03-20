package com.glassthought.shepherd.usecase.ticketstatus

import com.asgard.core.processRunner.ProcessRunner
import com.asgard.core.processRunner.ProcessResult
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import io.kotest.matchers.shouldBe
import kotlin.time.Duration

// ── Test Fake ───────────────────────────────────────────────────────────────

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

// ── Tests ───────────────────────────────────────────────────────────────────

class TicketStatusUpdaterImplTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        describe("GIVEN a TicketStatusUpdaterImpl with ticketId 'abc-123'") {
            val ticketId = "abc-123"

            describe("WHEN markDone is called") {
                val processRunner = RecordingProcessRunner()
                val updater = TicketStatusUpdaterImpl(
                    ticketId = ticketId,
                    processRunner = processRunner,
                    outFactory = outFactory,
                )

                it("THEN it invokes 'ticket close abc-123' via ProcessRunner") {
                    updater.markDone()

                    processRunner.invocations.size shouldBe 1
                    processRunner.invocations[0] shouldBe listOf("ticket", "close", "abc-123")
                }
            }
        }

        describe("GIVEN a TicketStatusUpdaterImpl with ticketId 'nid_xyz_task'") {
            val ticketId = "nid_xyz_task"

            describe("WHEN markDone is called") {
                val processRunner = RecordingProcessRunner()
                val updater = TicketStatusUpdaterImpl(
                    ticketId = ticketId,
                    processRunner = processRunner,
                    outFactory = outFactory,
                )

                it("THEN it invokes 'ticket close nid_xyz_task' via ProcessRunner") {
                    updater.markDone()

                    processRunner.invocations.size shouldBe 1
                    processRunner.invocations[0] shouldBe listOf("ticket", "close", "nid_xyz_task")
                }
            }
        }
    },
)
