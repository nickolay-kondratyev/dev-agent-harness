package com.glassthought.chainsaw.integtest

import com.asgard.core.annotation.AnchorPoint
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.chainsaw.core.initializer.ChainsawContext

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
 * Base class for integration tests that require [ChainsawContext].
 *
 * Extend this instead of [AsgardDescribeSpec] directly when your test needs access to
 * shared application-level dependencies (tmux, LLM, etc.).
 *
 * ### What it provides
 * - Pre-configured [AsgardDescribeSpecConfig.FOR_INTEG_TEST] settings (stop-on-first-failure,
 *   DEBUG log level, DATA_ERROR log level verification).
 * - A shared [ChainsawContext] singleton via the [chainsawContext] property.
 * - No config boilerplate — defaults are wired through [SharedContextIntegFactory].
 *
 * ### When to use
 * - Use `SharedContextDescribeSpec` when your integration test needs `ChainsawContext`
 *   (e.g., `chainsawContext.infra.tmux.sessionManager`, `chainsawContext.infra.directLlm.glmDirectLLM`).
 * - Use plain `AsgardDescribeSpec` for unit tests or tests that do NOT need `ChainsawContext`.
 *
 * ### Example
 * ```kotlin
 * @OptIn(ExperimentalKotest::class)
 * class MyIntegTest : SharedContextDescribeSpec({
 *     describe("GIVEN my use case").config(isIntegTestEnabled()) {
 *         val sessionManager = chainsawContext.infra.tmux.sessionManager
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
 * The underlying [ChainsawContext] is process-scoped and NOT closed between tests.
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
    // `chainsawContext` directly in their body lambda without a qualified `this` reference.
    @Suppress("UNCHECKED_CAST") (body as AsgardDescribeSpec.() -> Unit),
    config.asgardConfig,
) {
    val chainsawContext: ChainsawContext = SharedContextIntegFactory.chainsawContext
}
