package org.example

/**
 * Returns true when integration tests are enabled via the Gradle property `-PrunIntegTests=true`
 * OR when running tests inside IntelliJ IDEA.
 *
 * The Gradle property is injected by `app/build.gradle.kts` so that Gradle tracks the value
 * as a task input — cache is invalidated automatically when the property changes, unlike env vars.
 *
 * When running in IntelliJ, integration tests are always enabled to provide a seamless
 * developer experience without needing to configure run properties.
 */
fun isIntegTestEnabled(): Boolean =
    System.getProperty("runIntegTests") == "true" || isRunningInIntellij()

/**
 * Detects if the test is running inside IntelliJ IDEA.
 *
 * IntelliJ sets certain system properties when running tests:
 * - `idea.is.unit`: Set to "true" when running unit tests from IntelliJ
 * - `idea.home.path`: Path to IntelliJ installation
 */
private fun isRunningInIntellij(): Boolean =
    System.getProperty("idea.is.unit") == "true" || System.getProperty("idea.home.path") != null
