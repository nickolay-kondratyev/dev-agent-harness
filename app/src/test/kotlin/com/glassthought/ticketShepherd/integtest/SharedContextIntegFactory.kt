package com.glassthought.ticketShepherd.integtest

import com.asgard.core.out.impl.for_tests.testout.TestOutManager
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.ticketShepherd.core.initializer.data.ShepherdContext
import com.glassthought.ticketShepherd.core.initializer.Initializer
import kotlinx.coroutines.runBlocking

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
        )
    }

    internal fun buildDescribeSpecConfig(): AsgardDescribeSpecConfig =
        AsgardDescribeSpecConfig.FOR_INTEG_TEST.copy(testOutManager = testOutManager)
}
