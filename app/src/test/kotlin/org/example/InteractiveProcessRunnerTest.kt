package org.example

import com.asgard.core.out.impl.NoOpOutFactory
import com.glassthought.processRunner.InteractiveProcessResult
import com.glassthought.processRunner.InteractiveProcessRunner
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [com.glassthought.processRunner.InteractiveProcessRunner].
 *
 * NOTE: True interactive tests (requiring a TTY) cannot run in CI.
 * These tests cover construction and non-interactive commands, which work
 * fine with inheritIO even when stdin is not a terminal.
 */
class InteractiveProcessRunnerTest {

    private val outFactory = NoOpOutFactory.INSTANCE

    @Test
    fun `GIVEN InteractiveProcessRunner WHEN constructed THEN it is created without error`() {
        // Verifies the class can be instantiated — compile-time and construction sanity check.
      InteractiveProcessRunner(outFactory)
    }

    @Test
    fun `GIVEN a non-interactive command WHEN runInteractive is called THEN exit code is 0`() {
        val runner = InteractiveProcessRunner(outFactory)

        val result = runBlocking {
            runner.runInteractive("echo", "hello")
        }

        assertEquals(0, result.exitCode)
    }

    @Test
    fun `GIVEN a non-interactive command WHEN runInteractive succeeds THEN interrupted is false`() {
        val runner = InteractiveProcessRunner(outFactory)

        val result = runBlocking {
            runner.runInteractive("echo", "hello")
        }

        assertFalse(result.interrupted)
    }

    @Test
    fun `GIVEN a command that exits with non-zero code WHEN runInteractive is called THEN exit code is captured`() {
        val runner = InteractiveProcessRunner(outFactory)

        // `false` is a Unix command that always exits with code 1.
        val result = runBlocking {
            runner.runInteractive("false")
        }

        assertEquals(1, result.exitCode)
    }

    @Test
    fun `GIVEN InteractiveProcessResult WHEN constructed with given exit code THEN exitCode field holds that value`() {
        val result = InteractiveProcessResult(exitCode = 42, interrupted = false)

        assertEquals(42, result.exitCode)
    }

    @Test
    fun `GIVEN InteractiveProcessResult WHEN constructed with interrupted=true THEN interrupted field is true`() {
        val result = InteractiveProcessResult(exitCode = -1, interrupted = true)

        assertTrue(result.interrupted)
    }
}
