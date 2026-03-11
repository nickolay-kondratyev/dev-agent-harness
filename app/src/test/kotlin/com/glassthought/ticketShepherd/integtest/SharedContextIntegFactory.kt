package com.glassthought.ticketShepherd.integtest

import com.asgard.core.out.impl.for_tests.testout.TestOutManager
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.ticketShepherd.core.initializer.ShepherdContext
import com.glassthought.ticketShepherd.core.initializer.Initializer
import com.glassthought.ticketShepherd.core.initializer.data.Environment
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Process-scoped singleton that provides a shared [ShepherdContext] and [TestOutManager]
 * for all integration tests.
 *
 * Initialization occurs once at JVM class-load time (via `runBlocking`), which is acceptable
 * per project standards at test entry points.
 *
 * The shared [ShepherdContext] is intentionally NOT closed between tests — it is held for the
 * entire JVM test process lifetime. Resources are released via OS cleanup when the JVM exits.
 *
 * ### Usage
 * Extend [SharedContextDescribeSpec] (ref.ap.20lFzpGIVAbuIXO5tUTBg.E) instead of accessing
 * this factory directly. The base class exposes [SharedContextDescribeSpec.shepherdContext].
 *
 * ### Fail-fast on misconfiguration
 * If initialization fails (e.g., missing env var for LLM API), the exception propagates at
 * class-load time and all tests using [SharedContextDescribeSpec] will fail immediately with
 * a clear error message. This is the desired "fail hard" behavior.
 */
object SharedContextIntegFactory {
    internal val testOutManager: TestOutManager = TestOutManager.standard()

    /** Shared shepherd context.
     *
     *  Meant to be shared between the integration tests to keep the wire up of tests faster.
     *  */
    internal val shepherdContext: ShepherdContext = runBlocking {
        Initializer.standard().initialize(
            outFactory = testOutManager.outFactory,
            environment = Environment.test(),
            systemPromptFilePath = resolveSystemPromptFilePath(),
        )
    }

    internal fun buildDescribeSpecConfig(): AsgardDescribeSpecConfig =
        AsgardDescribeSpecConfig.FOR_INTEG_TEST.copy(testOutManager = testOutManager)

    /**
     * Resolves the absolute path to the test system prompt file by walking up
     * from the current working directory to find the git repo root.
     */
    private fun resolveSystemPromptFilePath(): String {
        val repoRoot = findGitRepoRoot(File(System.getProperty("user.dir")))
        val promptFile = File(repoRoot, "config/prompts/test-agent-system-prompt.txt")
        require(promptFile.exists()) {
            "System prompt file not found at [${promptFile.absolutePath}]"
        }
        return promptFile.absolutePath
    }

    private fun findGitRepoRoot(startDir: File): File {
        var dir: File? = startDir
        while (dir != null) {
            if (File(dir, ".git").exists()) {
                return dir
            }
            dir = dir.parentFile
        }
        throw IllegalStateException("Could not find .git directory starting from [${startDir.absolutePath}]")
    }
}
