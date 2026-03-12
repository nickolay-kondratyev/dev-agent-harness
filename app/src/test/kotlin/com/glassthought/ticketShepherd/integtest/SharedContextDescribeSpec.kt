package com.glassthought.ticketShepherd.integtest

import com.asgard.core.annotation.AnchorPoint
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.ticketShepherd.core.initializer.data.ShepherdContext

/**
 * Wraps [AsgardDescribeSpecConfig] with defaults pulled from [SharedContextIntegFactory].
 *
 * Callers may override [asgardConfig] to customize individual spec behavior while
 * keeping the shared [SharedContextIntegFactory.testOutManager] in place.
 */
data class SharedContextSpecConfig(
    val asgardConfig: AsgardDescribeSpecConfig = SharedContextIntegFactory.buildDescribeSpecConfig(),
)

/**
 * Base class for integration tests that require [ShepherdContext].
 *
 * Extend this instead of [AsgardDescribeSpec] directly when your test needs access to
 * shared application-level dependencies (tmux, LLM, etc.).
 *
 * ### What it provides
 * - Pre-configured [AsgardDescribeSpecConfig.FOR_INTEG_TEST] settings (stop-on-first-failure,
 *   DEBUG log level, DATA_ERROR log level verification).
 * - A shared [ShepherdContext] singleton via the [shepherdContext] property.
 * - No config boilerplate — defaults are wired through [SharedContextIntegFactory].
 *
 * ### When to use
 * - Use `SharedContextDescribeSpec` when your integration test needs `ShepherdContext`
 *   (e.g., `shepherdContext.infra.tmux.sessionManager`, `shepherdContext.infra.directLlm.budgetHigh`).
 * - Use plain `AsgardDescribeSpec` for unit tests or tests that do NOT need `ShepherdContext`.
 *
 * ### Example
 * ```kotlin
 * @OptIn(ExperimentalKotest::class)
 * class MyIntegTest : SharedContextDescribeSpec({
 *     describe("GIVEN my use case").config(isIntegTestEnabled()) {
 *         val sessionManager = shepherdContext.infra.tmux.sessionManager
 *         describe("WHEN something happens") {
 *             it("THEN expected result") {
 *                 // assertion
 *             }
 *         }
 *     }
 * })
 * ```
 *
 * ### Lifecycle note
 * The underlying [ShepherdContext] is process-scoped and NOT closed between tests.
 * It is held for the entire JVM test process lifetime. See [SharedContextIntegFactory].
 *
 * ap.20lFzpGIVAbuIXO5tUTBg.E
 */
@AnchorPoint("ap.20lFzpGIVAbuIXO5tUTBg.E")
abstract class SharedContextDescribeSpec(
    body: SharedContextDescribeSpec.() -> Unit,
    config: SharedContextSpecConfig = SharedContextSpecConfig(),
) : AsgardDescribeSpec(
    // Safe cast: every SharedContextDescribeSpec IS an AsgardDescribeSpec.
    // Using SharedContextDescribeSpec as the receiver type allows subclass tests to access
    // `shepherdContext` directly in their body lambda without a qualified `this` reference.
    @Suppress("UNCHECKED_CAST") (body as AsgardDescribeSpec.() -> Unit),
    config.asgardConfig,
) {
    val shepherdContext: ShepherdContext = SharedContextIntegFactory.shepherdContext
}
