package com.glassthought.chainsaw.integtest

import com.asgard.core.annotation.AnchorPoint
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.chainsaw.core.initializer.AppDependencies

/**
 * Wraps [AsgardDescribeSpecConfig] with defaults pulled from [SharedAppDepIntegFactory].
 *
 * Callers may override [asgardConfig] to customize individual spec behavior while
 * keeping the shared [SharedAppDepIntegFactory.testOutManager] in place.
 */
data class SharedAppDepSpecConfig(
    val asgardConfig: AsgardDescribeSpecConfig = SharedAppDepIntegFactory.buildDescribeSpecConfig(),
)

/**
 * Base class for integration tests that require [AppDependencies].
 *
 * Extend this instead of [AsgardDescribeSpec] directly when your test needs access to
 * shared application-level dependencies (tmux, LLM, etc.).
 *
 * ### What it provides
 * - Pre-configured [AsgardDescribeSpecConfig.FOR_INTEG_TEST] settings (stop-on-first-failure,
 *   DEBUG log level, DATA_ERROR log level verification).
 * - A shared [AppDependencies] singleton via the [appDependencies] property.
 * - No config boilerplate — defaults are wired through [SharedAppDepIntegFactory].
 *
 * ### When to use
 * - Use `SharedAppDepDescribeSpec` when your integration test needs `AppDependencies`
 *   (e.g., `appDependencies.tmuxSessionManager`, `appDependencies.glmDirectLLM`).
 * - Use plain `AsgardDescribeSpec` for unit tests or tests that do NOT need `AppDependencies`.
 *
 * ### Example
 * ```kotlin
 * @OptIn(ExperimentalKotest::class)
 * class MyIntegTest : SharedAppDepDescribeSpec({
 *     describe("GIVEN my use case").config(isIntegTestEnabled()) {
 *         val sessionManager = appDependencies.tmuxSessionManager
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
 * The underlying [AppDependencies] is process-scoped and NOT closed between tests.
 * It is held for the entire JVM test process lifetime. See [SharedAppDepIntegFactory].
 *
 * ap.20lFzpGIVAbuIXO5tUTBg.E
 */
@AnchorPoint("ap.20lFzpGIVAbuIXO5tUTBg.E")
abstract class SharedAppDepDescribeSpec(
    body: SharedAppDepDescribeSpec.() -> Unit,
    config: SharedAppDepSpecConfig = SharedAppDepSpecConfig(),
) : AsgardDescribeSpec(
    // Safe cast: every SharedAppDepDescribeSpec IS an AsgardDescribeSpec.
    // Using SharedAppDepDescribeSpec as the receiver type allows subclass tests to access
    // `appDependencies` directly in their body lambda without a qualified `this` reference.
    @Suppress("UNCHECKED_CAST") (body as AsgardDescribeSpec.() -> Unit),
    config.asgardConfig,
) {
    val appDependencies: AppDependencies = SharedAppDepIntegFactory.appDependencies
}
