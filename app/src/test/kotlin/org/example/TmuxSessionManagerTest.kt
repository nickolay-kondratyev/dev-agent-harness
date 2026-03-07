package org.example

import com.asgard.core.out.impl.NoOpOutFactory
import com.glassthought.tmux.TmuxCommandRunner
import com.glassthought.tmux.data.TmuxSessionName
import com.glassthought.tmux.TmuxSessionManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [com.glassthought.tmux.TmuxSessionManager].
 *
 * Requires tmux to be installed on the system. Tests use bash sessions (not claude)
 * so they can run in CI environments.
 */
class TmuxSessionManagerTest {

    private val outFactory = NoOpOutFactory.INSTANCE
    private val commandRunner = TmuxCommandRunner()
    private val sessionManager = TmuxSessionManager(outFactory, commandRunner)
    private val createdSessions = mutableListOf<TmuxSessionName>()

    @AfterEach
    fun tearDown() {
        // Clean up any sessions created during tests.
        runBlocking {
            createdSessions.forEach { session ->
                try {
                    sessionManager.killSession(session)
                } catch (_: Exception) {
                    // Session may already be killed by the test.
                }
            }
        }
    }

    @Test
    fun `GIVEN TmuxSessionManager WHEN createSession with bash THEN session is created`() {
        val sessionName = "test-session-${System.currentTimeMillis()}"

        val session = runBlocking {
            sessionManager.createSession(sessionName, "bash")
        }
        createdSessions.add(session)

        val exists = runBlocking { sessionManager.sessionExists(sessionName) }
        assertTrue(exists)
    }

    @Test
    fun `GIVEN an existing session WHEN sessionExists THEN returns true`() {
        val sessionName = "test-exists-${System.currentTimeMillis()}"

        val session = runBlocking {
            sessionManager.createSession(sessionName, "bash")
        }
        createdSessions.add(session)

        val exists = runBlocking { sessionManager.sessionExists(sessionName) }
        assertTrue(exists)
    }

    @Test
    fun `GIVEN no session WHEN sessionExists with random name THEN returns false`() {
        val nonExistentName = "non-existent-session-${System.currentTimeMillis()}"

        val exists = runBlocking { sessionManager.sessionExists(nonExistentName) }
        assertFalse(exists)
    }

    @Test
    fun `GIVEN an existing session WHEN killSession THEN session no longer exists`() {
        val sessionName = "test-kill-${System.currentTimeMillis()}"

        val session = runBlocking {
            sessionManager.createSession(sessionName, "bash")
        }
        // Do NOT add to createdSessions since we kill it explicitly.

        runBlocking { sessionManager.killSession(session) }

        val exists = runBlocking { sessionManager.sessionExists(sessionName) }
        assertFalse(exists)
    }
}
