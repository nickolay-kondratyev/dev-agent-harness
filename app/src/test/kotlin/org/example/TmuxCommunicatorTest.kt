package org.example

import com.asgard.core.out.impl.NoOpOutFactory
import com.glassthought.tmux.util.TmuxCommandRunner
import com.glassthought.tmux.TmuxCommunicator
import com.glassthought.tmux.data.TmuxSessionName
import com.glassthought.tmux.TmuxSessionManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [com.glassthought.tmux.TmuxCommunicator].
 *
 * Requires tmux to be installed on the system. Uses bash sessions to verify
 * that keystrokes are delivered correctly.
 */
class TmuxCommunicatorTest {

    private val outFactory = NoOpOutFactory.INSTANCE
    private val commandRunner = TmuxCommandRunner()
    private val sessionManager = TmuxSessionManager(outFactory, commandRunner)
    private val communicator = TmuxCommunicator(outFactory, commandRunner)
    private val createdSessions = mutableListOf<TmuxSessionName>()
    private val createdFiles = mutableListOf<File>()

    @AfterEach
    fun tearDown() {
        runBlocking {
            createdSessions.forEach { session ->
                try {
                    sessionManager.killSession(session)
                } catch (_: Exception) {
                    // Session may already be killed.
                }
            }
        }
        createdFiles.forEach { file ->
            file.delete()
        }
    }

    @Test
    fun `GIVEN a tmux session running bash WHEN sendKeys with echo command THEN file is created with expected content`() {
        val sessionName = "test-comm-${System.currentTimeMillis()}"
        val outputFile = File("/tmp/tmux_test_out_${System.currentTimeMillis()}")
        createdFiles.add(outputFile)

        val session = runBlocking {
            sessionManager.createSession(sessionName, "bash")
        }
        createdSessions.add(session)

        runBlocking {
            communicator.sendKeys(session, "echo hello > ${outputFile.absolutePath}")
        }

        // Poll for the file to appear, with a reasonable timeout.
        val deadline = System.currentTimeMillis() + 5_000
        while (!outputFile.exists() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }

        assertEquals("hello", outputFile.readText().trim())
    }
}
