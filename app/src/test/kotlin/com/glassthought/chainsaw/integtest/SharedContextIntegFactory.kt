package com.glassthought.chainsaw.integtest

import com.asgard.core.out.impl.for_tests.testout.TestOutManager
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.chainsaw.core.initializer.ChainsawContext
import com.glassthought.chainsaw.core.initializer.Initializer
import com.glassthought.chainsaw.core.initializer.data.Environment
import kotlinx.coroutines.runBlocking

/**
 * Process-scoped singleton that provides a shared [ChainsawContext] and [TestOutManager]
 * for all integration tests.
 *
 * Initialization occurs once at JVM class-load time (via `runBlocking`), which is acceptable
 * per project standards at test entry points.
 *
 * The shared [ChainsawContext] is intentionally NOT closed between tests — it is held for the
 * entire JVM test process lifetime. Resources are released via OS cleanup when the JVM exits.
 *
 * ### Usage
 * Extend [SharedContextDescribeSpec] (ref.ap.20lFzpGIVAbuIXO5tUTBg.E) instead of accessing
 * this factory directly. The base class exposes [SharedContextDescribeSpec.chainsawContext].
 *
 * ### Fail-fast on misconfiguration
 * If initialization fails (e.g., missing env var for LLM API), the exception propagates at
 * class-load time and all tests using [SharedContextDescribeSpec] will fail immediately with
 * a clear error message. This is the desired "fail hard" behavior.
 */
object SharedContextIntegFactory {
    internal val testOutManager: TestOutManager = TestOutManager.standard()

    /** Shared chainsaw context.
     *
     *  Meant to be shared between the integration tests to keep the wire up of tests faster.
     *  */
    internal val chainsawContext: ChainsawContext = runBlocking {
        Initializer.standard().initialize(
            outFactory = testOutManager.outFactory,
            environment = Environment.test(),
        )
    }

    internal fun buildDescribeSpecConfig(): AsgardDescribeSpecConfig =
        AsgardDescribeSpecConfig.FOR_INTEG_TEST.copy(testOutManager = testOutManager)
}
