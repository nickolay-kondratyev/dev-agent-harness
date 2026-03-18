---
closed_iso: 2026-03-18T22:15:39Z
id: nid_u9dsi8s5duqp0g2fwgpkdhmvu_E
title: "Add sonar cube reporting"
status: closed
deps: []
links: []
created_iso: 2026-03-18T21:57:47Z
status_updated_iso: 2026-03-18T22:15:39Z
type: task
priority: 3
assignee: nickolaykondratyev
---

TASK: add sonar cube report generation, we want to have a script that will create a local report file under ./_reports/sonar_report.json so that we can act upon the values that it produces.

SONAR should not be auto triggerred it should be triggerred explicitly.

--------------------------------------------------------------------------------

# How to Add SonarCloud to a Kotlin (Gradle) Repository

Guide for integrating [SonarCloud](https://sonarcloud.io) static analysis into a Kotlin repository hosted on GitHub.

## Prerequisites

- A GitHub repository with a Gradle (Kotlin DSL) build
- A [SonarCloud](https://sonarcloud.io) account linked to your GitHub organization


## We have token generated and project onboarded
`SONAR_TOKEN` environment variable is exported. and the project is onboarded.

## Step 3: Add the Gradle Plugin

In your root `build.gradle.kts`:

```kotlin
plugins {
    id("org.sonarqube") version "5.1.0.4882"
}
```

## Step 4: Configure Sonar Properties

Add the `sonar {}` block in your root `build.gradle.kts`:

```kotlin
sonar {
    properties {
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.organization", "your-org-key")           // from Step 1
        property("sonar.projectKey", "Your-Org_your-repo")       // from Step 1
        property("sonar.token", System.getenv("SONAR_TOKEN")
            ?: error("SONAR_TOKEN environment variable not set"))

        // Point to your source directories
        property(
            "sonar.sources",
            mainSourceDirs.filter { it.exists() }.joinToString(",") { it.absolutePath },
        )
        property(
            "sonar.tests",
            testSourceDirs.filter { it.exists() }.joinToString(",") { it.absolutePath },
        )

        // Exclude build artifacts and caches
        property(
            "sonar.exclusions",
            listOf(
                "**/build/**",
                "**/node_modules/**",
                "**/.gradle/**",
                "**/.kotlin/**",
                "**/dist/**",
            ).joinToString(","),
        )
    }
}
```

### Source Directory Discovery

For Kotlin Multiplatform projects, you can dynamically discover source directories:

```kotlin
val kmpRoot = file("path/to/kotlin-mp")

val artifactDirNames = setOf("build", "node_modules", ".gradle", ".kotlin")

val allKotlinDirs = kmpRoot.walkTopDown()
    .onEnter { it.name !in artifactDirNames }
    .filter { it.isDirectory && it.name == "kotlin" && it.parentFile?.parentFile?.name == "src" }
    .toList()

val mainSourceDirs = allKotlinDirs.filter { dir ->
    val sourceSet = dir.parentFile.name
    sourceSet.endsWith("Main") || sourceSet == "main"
}
val testSourceDirs = allKotlinDirs.filter { dir ->
    val sourceSet = dir.parentFile.name
    sourceSet.endsWith("Test") || sourceSet == "test"
}
```

For a simpler single-module project, list them explicitly:

```kotlin
val mainSourceDirs = listOf(file("src/main/kotlin"))
val testSourceDirs = listOf(file("src/test/kotlin"))
```

## Step 5: Run the Analysis

```bash
./gradlew sonar --no-configuration-cache
```

> `--no-configuration-cache` is required because the sonar plugin does not support Gradle configuration cache.

After the task completes, view results at:
```
https://sonarcloud.io/project/overview?id=Your-Org_your-repo
```

## Step 6 (Optional): Create a Run Script

Create `run_sonar.sh` at the repo root for convenience:

```bash
#!/usr/bin/env bash
main() {
  ./gradlew sonar --no-configuration-cache
}
main "${@}"
```

## Step 7 (Optional): Fetch Reports Programmatically

You can fetch SonarCloud data via its API for agent or CI consumption:

```bash
# Quality gate status
curl -sf -u "${SONAR_TOKEN}:" \
  "https://sonarcloud.io/api/qualitygates/project_status?projectKey=Your-Org_your-repo"

# Key metrics
curl -sf -u "${SONAR_TOKEN}:" \
  "https://sonarcloud.io/api/measures/component?component=Your-Org_your-repo&metricKeys=bugs,vulnerabilities,code_smells,coverage,duplicated_lines_density,ncloc"

# Open issues (paginated, max page_size=500)
curl -sf -u "${SONAR_TOKEN}:" \
  "https://sonarcloud.io/api/issues/search?componentKeys=Your-Org_your-repo&resolved=false&ps=500&p=1"
```

> **Note:** SonarCloud API paginates with a hard cap of `p * ps <= 10000`. Loop over pages until `fetched >= total` or the cap is reached.

## Quick Checklist

- [ ] SonarCloud project created and linked to GitHub repo
- [ ] `SONAR_TOKEN` generated and available as env var (and/or CI secret)
- [ ] `org.sonarqube` Gradle plugin added
- [ ] `sonar {}` block configured with correct org, project key, and source paths
- [ ] `./gradlew sonar --no-configuration-cache` runs successfully
- [ ] Results visible at `https://sonarcloud.io/project/overview?id=<project-key>`
