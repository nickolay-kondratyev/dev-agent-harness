package com.glassthought.chainsaw.integtest

import com.asgard.core.out.impl.for_tests.testout.TestOutManager
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.chainsaw.core.initializer.AppDependencies
import com.glassthought.chainsaw.core.initializer.Initializer
import com.glassthought.chainsaw.core.initializer.data.Environment
import kotlinx.coroutines.runBlocking

/**
 * Process-scoped singleton that provides a shared [AppDependencies] and [TestOutManager]
 * for all integration tests.
 *
 * Initialization occurs once at JVM class-load time (via `runBlocking`), which is acceptable
 * per project standards at test entry points.
 *
 * The shared [AppDependencies] is intentionally NOT closed between tests — it is held for the
 * entire JVM test process lifetime. Resources are released via OS cleanup when the JVM exits.
 *
 * ### Usage
 * Extend [SharedAppDepDescribeSpec] (ref.ap.20lFzpGIVAbuIXO5tUTBg.E) instead of accessing
 * this factory directly. The base class exposes [SharedAppDepDescribeSpec.appDependencies].
 *
 * ### Fail-fast on misconfiguration
 * If initialization fails (e.g., missing env var for LLM API), the exception propagates at
 * class-load time and all tests using [SharedAppDepDescribeSpec] will fail immediately with
 * a clear error message. This is the desired "fail hard" behavior.
 */
object SharedAppDepIntegFactory {
    internal val testOutManager: TestOutManager = TestOutManager.standard()

    internal val appDependencies: AppDependencies = runBlocking {
        Initializer.standard().initialize(
            outFactory = testOutManager.outFactory,
            environment = Environment.test(),
        )
    }

    internal fun buildDescribeSpecConfig(): AsgardDescribeSpecConfig =
        AsgardDescribeSpecConfig.FOR_INTEG_TEST.copy(testOutManager = testOutManager)
}
