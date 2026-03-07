package org.example

/**
 * Returns true when integration tests are enabled via the Gradle property `-PrunIntegTests=true`.
 *
 * Reads the system property injected by `app/build.gradle.kts` so that Gradle tracks the value
 * as a task input — cache is invalidated automatically when the property changes, unlike env vars.
 */
fun isIntegTestEnabled(): Boolean = System.getProperty("runIntegTests") == "true"
