package org.example

import com.asgard.testTools.TestEnvUtil

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
    System.getProperty("runIntegTests") == "true" || TestEnvUtil.isRunningInIntelliJ
