# Dependency Management Exploration Report

## Executive Summary

This report documents the current state of dependency management in the Chainsaw project. The project uses Gradle Version Catalog (libs.versions.toml) with a partial migration in progress. Currently, some dependencies use inline version strings while others already use the catalog.

---

## 1. Dependencies Using Inline Version Strings

### Current inline dependencies (total: 7 dependencies, 8 total occurrences):

| Dependency | Version | Usage Pattern | Notes |
|------------|---------|---------------|-------|
| `com.asgard:asgardTestTools` | `1.0.0` | Test dependency | Used in `app/build.gradle.kts` line 25 |
| `com.asgard:asgardCore` | `1.0.0` | Implementation dependency | Line 35 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | `1.10.2` | Implementation dependency | Line 38 |
| `com.squareup.okhttp3:okhttp` | `4.12.0` | Implementation dependency | Line 41 |
| `org.json:json` | `20240303` | Implementation dependency | Line 44 |
| `org.yaml:snakeyaml` | `2.2` | Implementation dependency | Line 47 |
| `com.fasterxml.jackson.core:jackson-databind` | `2.17.2` | Implementation dependency | Line 50 |
| `com.fasterxml.jackson.module:jackson-module-kotlin` | `2.17.2` | Implementation dependency | Line 51 |
| `io.ktor:ktor-server-core` | `3.1.1` | Implementation dependency | Line 55 |
| `io.ktor:ktor-server-cio` | `3.1.1` | Implementation dependency | Line 56 |
| `io.ktor:ktor-server-content-negotiation` | `3.1.1` | Implementation dependency | Line 57 |
| `io.ktor:ktor-serialization-jackson` | `3.1.1` | Implementation dependency | Line 58 |
| `com.squareup.okhttp3:mockwebserver` | `4.12.0` | Test dependency | Line 61 |

**Note**: These are grouped by library, showing that there are 7 unique dependencies using inline versions:
1. asgardTestTools (test only)
2. asgardCore
3. kotlinx-coroutines-core
4. okhttp (includes mockwebserver)
5. json
6. snakeyaml
7. jackson-databind + jackson-module-kotlin (2.17.2)
8. ktor-server (core + cio + content-negotiation + serialization-jackson, all 3.1.1)

---

## 2. Dependencies Already Using libs.versions.toml

| Dependency | Catalog Reference | Catalog Entry |
|------------|-------------------|---------------|
| `com.google.guava:guava` | `libs.guava` | `guava = { module = "com.google.guava:guava", version.ref = "guava" }` |
| `io.kotest:kotest-assertions-core` | `libs.kotest.assertions.core` | `kotest-assertions-core = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }` |
| `io.kotest:kotest-runner-junit5` | `libs.kotest.runner.junit5` | `kotest-runner-junit5 = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }` |
| `org.jetbrains.kotlin.jvm` | `libs.plugins.kotlin.jvm` | `kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version = "2.2.20" }` |

**Note**: kotest dependencies share the same catalog version (`kotest = "5.9.1"`).

---

## 3. Project Build Structure

### Gradle Configuration Files:

1. **`gradle/libs.versions.toml`** (lines 1-14)
   - Main version catalog defining versions, libraries, and plugins
   
2. **`app/build.gradle.kts`** (lines 1-87)
   - Main application build file
   - Uses both inline and catalog dependencies
   
3. **`build.gradle.kts`** (lines 1-35)
   - Root project configuration
   - Excludes thorg-root from IDEA indexing (no dependencies here)
   
4. **`submodules/thorg-root/build.gradle.kts`** (lines 1-169)
   - Thorg root submodule (composite build)
   - Excludes source/libraries/kotlin-mp from other components
   - No direct dependencies (source-code dependencies only)

---

## 4. libs.versions.toml Structure

### Complete Structure:

```toml
[versions]
guava = "33.4.6-jre"
kotest = "5.9.1"

[libraries]
guava = { module = "com.google.guava:guava", version.ref = "guava" }
kotest-assertions-core = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
kotest-runner-junit5 = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version = "2.2.20" }
```

### Structure Breakdown:

#### 4.1. [versions] Section
- Defines named version references
- Used for easy version management and consistency
- Current versions:
  - `guava`: `33.4.6-jre`
  - `kotest`: `5.9.1`

#### 4.2. [libraries] Section
- Defines reusable library dependencies
- Links to version references via `version.ref`
- Supports modular references like `libs.kotest.assertions.core`

#### 4.3. [plugins] Section
- Defines plugin dependencies
- Used for plugin application (e.g., `alias(libs.plugins.kotlin.jvm)`)
- `kotlin-jvm` plugin version: `2.2.20`

---

## 5. Dependency Usage Patterns

### Pattern 1: Inline Version String (Current State)
```kotlin
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

### Pattern 2: Catalog Reference (Already in use)
```kotlin
dependencies {
    implementation(libs.guava)
}
```

### Pattern 3: Plugin Alias (Already in use)
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}
```

---

## 6. Plugin Dependencies

### Current Plugins:
1. **org.jetbrains.kotlin.jvm** (version 2.2.20)
   - Used in `app/build.gradle.kts` line 10
   - Already defined in `libs.versions.toml` plugins section

### Plugin Resolution:
- No other plugins currently used in `app/build.gradle.kts`
- No test framework plugins (relying on asgardTestTools and JUnit Platform)

---

## 7. Testing Dependencies

### Test Configuration:

| Dependency | Type | Version | Catalog Status |
|------------|------|---------|----------------|
| `com.asgard:asgardTestTools` | Implementation | `1.0.0` | Inline |
| `io.kotest:kotest-assertions-core` | Implementation | `5.9.1` | Catalog (via kotest version) |
| `io.kotest:kotest-runner-junit5` | Implementation | `5.9.1` | Catalog (via kotest version) |
| `org.junit.platform:junit-platform-launcher` | RuntimeOnly | - | Inline |

**Observation**: Kotest assertions and runner use catalog version `5.9.1`, while asgardTestTools is pinned to `1.0.0` inline.

---

## 8. Implementation Dependencies

### Production Dependencies (non-test):

| Dependency | Version | Catalog Status | Notes |
|------------|---------|----------------|-------|
| `com.google.guava` | 33.4.6-jre | **Catalog** | ✅ Already migrated |
| `com.asgard:asgardCore` | 1.0.0 | Inline | Foundation library |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.2 | Inline | Coroutines for async |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | Inline | HTTP client |
| `org.json:json` | 20240303 | Inline | JSON library |
| `org.yaml:snakeyaml` | 2.2 | Inline | YAML parser |
| `com.fasterxml.jackson.core:jackson-databind` | 2.17.2 | Inline | JSON deserialization |
| `com.fasterxml.jackson.module:jackson-module-kotlin` | 2.17.2 | Inline | Jackson Kotlin extension |
| `io.ktor:ktor-server-*` | 3.1.1 | Inline | HTTP server stack |

---

## 9. Recommendation Summary

### Migration Priority:

**High Priority (Critical Dependencies)**:
1. `com.asgard:asgardCore` - Core foundation library
2. `com.asgard:asgardTestTools` - Test framework
3. `org.jetbrains.kotlinx:kotlinx-coroutines-core` - Runtime dependency
4. `com.fasterxml.jackson:*` - JSON parsing (both databind and kotlin module)
5. `io.ktor:ktor-server-*` - HTTP server stack (all 4 components)

**Medium Priority**:
6. `com.squareup.okhttp3:okhttp` - HTTP client
7. `com.squareup.okhttp3:mockwebserver` - Test HTTP server
8. `org.json:json` - JSON construction/parsing

**Low Priority**:
9. `org.yaml:snakeyaml` - YAML parsing (less critical, used for ticket/role frontmatter)

### Best Practices Applied:

1. **Version Consolidation**: Group related dependencies with same version (e.g., Jackson bundle, Ktor stack)
2. **Naming Convention**: Use descriptive library names matching catalog structure
3. **Documentation**: Inline comments explain why specific versions are chosen (e.g., asgardTestTools inline for compilation visibility)
4. **Test/Production Separation**: Clear distinction between test and production dependencies

---

## 10. Conclusion

The Chainsaw project has **7 unique dependencies** using inline version strings that should be migrated to `libs.versions.toml`. This migration will improve:
- Dependency version consistency
- Easier updates across multiple dependencies
- Better version management and auditability
- Alignment with modern Gradle practices

The migration should focus on maintaining the current architecture while consolidating version declarations into the catalog structure.
