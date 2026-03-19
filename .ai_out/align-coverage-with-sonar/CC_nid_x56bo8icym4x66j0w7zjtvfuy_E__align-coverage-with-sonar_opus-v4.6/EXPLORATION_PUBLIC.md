# Exploration: Align Coverage with Sonar

## Root Cause Analysis

### The Problem
When running `./run_sonar.sh`, the `:sonar` task outputs "File 'X.kt' not found in project sources" for every source file in the coverage report.

### Root Cause
The `sonar.coverage.jacoco.xmlReportPaths` property is set at the **ROOT** project level in `build.gradle.kts`:

```kotlin
sonar {
    properties {
        property("sonar.coverage.jacoco.xmlReportPaths", "${rootDir}/.out/coverage.xml")
    }
}
```

The Sonar Gradle plugin processes each project (root + subprojects) separately. When it processes the root project, it tries to match coverage file references against the root project's **indexed sources**. But the root project has **NO source files** — all Kotlin sources live in the `:app` subproject (`app/src/main/kotlin/`).

The JaCoCo XML coverage report references files by package + filename (e.g., `com/glassthought/shepherd/cli/AppMain.kt`). Sonar needs to resolve these against a source root. Since the coverage report path is associated with the root project (which has no source root), every file lookup fails.

### Fix
Move `sonar.coverage.jacoco.xmlReportPaths` from the root-level sonar block to the `:app` subproject's sonar configuration:

```kotlin
project(":app") {
    sonar {
        properties {
            property("sonar.coverage.jacoco.xmlReportPaths", "${rootDir}/.out/coverage.xml")
        }
    }
}
```

This associates the coverage report with the `:app` module where Sonar has indexed the actual source files.

## Key Files
- `build.gradle.kts` — root sonar config (needs fix)
- `app/build.gradle.kts` — kover config (correct, no changes needed)
- `.out/coverage.xml` — generated Kover report (correct format)
