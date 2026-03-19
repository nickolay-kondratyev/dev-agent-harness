package com.glassthought.shepherd.integtest

import java.io.File

/**
 * Shared stateless helper utilities for integration tests that spawn real agents.
 *
 * Provides callback script resolution, system prompt creation, and instruction file helpers
 * used by both [AgentFacadeImplIntegTest] and
 * `com.glassthought.shepherd.integtest.compaction.SelfCompactionIntegTest`.
 */
internal object IntegTestHelpers {

    private val defaultTmpDir: File by lazy {
        val dir = File(System.getProperty("user.dir"), ".tmp")
        dir.mkdirs()
        dir
    }

    /**
     * Resolves the absolute path to the callback scripts directory.
     * The scripts are at `app/src/main/resources/scripts/` relative to the project root.
     */
    fun resolveCallbackScriptsDir(): String {
        val projectDir = System.getProperty("user.dir")
        val scriptsDir = File(projectDir, "src/main/resources/scripts")
        check(scriptsDir.isDirectory) {
            "Callback scripts directory not found at " +
                "${scriptsDir.absolutePath}. " +
                "Ensure you are running from the app module directory."
        }
        val signalScript = File(
            scriptsDir, "callback_shepherd.signal.sh",
        )
        check(signalScript.exists()) {
            "callback_shepherd.signal.sh not found at " +
                signalScript.absolutePath
        }
        if (!signalScript.canExecute()) {
            signalScript.setExecutable(true)
        }
        return scriptsDir.absolutePath
    }

    /**
     * Creates a temporary system prompt file with basic callback protocol.
     * For self-compaction tests, use [createCompactionSystemPromptFile] instead.
     */
    fun createSystemPromptFile(tmpDir: File = defaultTmpDir): File {
        val file = File(
            tmpDir,
            "integ-test-system-prompt-${System.currentTimeMillis()}.md",
        )
        file.writeText(
            """
            |# Integration Test Agent Protocol
            |
            |You are a test agent running in an integration test. Follow these rules EXACTLY:
            |
            |${IntegTestCallbackProtocol.CORE_PROTOCOL}
            |
            |${IntegTestCallbackProtocol.IMPORTANT_NOTES_BASE}
            """.trimMargin()
        )
        return file
    }

    /**
     * Creates a temporary instruction file that tells the agent to signal done.
     */
    fun createDoneInstructionFile(
        tmpDir: File = defaultTmpDir,
    ): File {
        val file = File(
            tmpDir,
            "integ-test-done-instruction-${System.currentTimeMillis()}.md",
        )
        file.writeText(
            """
            |# Task: Signal Done
            |
            |Your task is simple: signal that you are done.
            |
            |Run this command using the Bash tool:
            |```bash
            |callback_shepherd.signal.sh done completed
            |```
            |
            |That is all you need to do.
            """.trimMargin()
        )
        return file
    }
}
