package org.example

import com.asgard.testTools.awaitility.AsgardAwaitility
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.tmux.TmuxCommunicator
import com.glassthought.tmux.TmuxSessionManager
import com.glassthought.tmux.data.TmuxSessionName
import com.glassthought.tmux.util.TmuxCommandRunner
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [com.glassthought.tmux.TmuxCommunicator].
 *
 * Requires tmux to be installed on the system. Uses bash sessions to verify
 * that keystrokes are delivered correctly.
 */
@OptIn(ExperimentalKotest::class)
class TmuxCommunicatorTest : AsgardDescribeSpec({

    describe("GIVEN a tmux session running bash").config(isIntegTestEnabled()) {
        val commandRunner = TmuxCommandRunner()
        val sessionManager = TmuxSessionManager(outFactory, commandRunner)
        val communicator = TmuxCommunicator(outFactory, commandRunner)
        val createdSessions = mutableListOf<TmuxSessionName>()
        val createdFiles = mutableListOf<File>()

        afterEach {
            createdSessions.forEach { session ->
                try {
                    sessionManager.killSession(session)
                } catch (_: Exception) {
                    // Session may already be killed.
                }
            }
            createdSessions.clear()
            createdFiles.forEach { file -> file.delete() }
            createdFiles.clear()
        }

        describe("WHEN sendKeys with echo command") {
            it("THEN file is created with expected content") {
                val sessionName = "test-comm-${System.currentTimeMillis()}"
                val outputFile = File(System.getProperty("user.dir"), ".tmp/tmux_test_out_${System.currentTimeMillis()}")
                outputFile.parentFile.mkdirs()
                createdFiles.add(outputFile)

                val session = sessionManager.createSession(sessionName, "bash")
                createdSessions.add(session)

                communicator.sendKeys(session, "echo hello > ${outputFile.absolutePath}")

                AsgardAwaitility.wait()
                    .pollDelay(100.milliseconds)
                    .atMost(5.seconds)
                    .until { outputFile.exists() }

                outputFile.readText().trim() shouldBe "hello"
            }
        }
    }
})
