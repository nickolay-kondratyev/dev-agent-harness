# SonarQube Integration Exploration Report

**Date:** 2026-03-18  
**Repository:** nickolay-kondratyev/dev-agent-harness  
**Ticket:** add-sonar-cube-reporting (nid_u9dsi8s5duqp0g2fwgpkdhmvu_E)  
**Task:** Integrate SonarQube reporting with local JSON report generation in `./_reports/sonar_report.json`

---

## 1. Project Structure Overview

### Repository Information
- **Git Remote:** `git@github.com:nickolay-kondratyev/dev-agent-harness.git`
- **GitHub Org/Repo:** nickolay-kondratyev / dev-agent-harness
- **Build System:** Gradle (Kotlin DSL)
- **JVM Language:** Kotlin
- **Java Version:** 21 (via toolchain)

### Module Structure: Single-Module Build
```
.
├── build.gradle.kts              # Root build file (task registration only)
├── settings.gradle.kts           # Project configuration: rootProject = "nickolay-kondratyev_dev-agent-harness", includes = ["app"]
└── app/
    ├── build.gradle.kts          # Main module build file
    └── src/
        ├── main/kotlin/com/glassthought/shepherd/
        │   ├── cli/              # CLI entry point (AppMainKt)
        │   └── core/             # Core implementation
        └── test/kotlin/
            └── (test packages)
```

---

## 2. Root build.gradle.kts Structure

**Location:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/build.gradle.kts`

**Current Content:** (Full file - 215 lines)

The root `build.gradle.kts` contains **only task registrations**, NO plugin declarations:

### Key Tasks Registered:
1. **tasksJson** - Lists all Gradle tasks as JSON (caching via UP-TO-DATE)
2. **publishAsgardToMavenLocal** - Publishes asgard libraries to ~/.m2
3. **checkAsgardInMavenLocal** - Validates asgard libraries presence
4. **ensureAsgardInMavenLocal** - Auto-publishes missing asgard libs

### Important Notes:
- **NO plugins defined at root level** - all plugins are applied in `app/build.gradle.kts`
- Configuration cache disabled for all root tasks (they spawn subprocesses or access env vars)
- Tasks handle asgard dependency self-healing (required for local development)

---

## 3. App Module build.gradle.kts Structure

**Location:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/build.gradle.kts`

**Current Plugins Applied:**
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)        // org.jetbrains.kotlin.jvm v2.2.20
    application                            // For CLI apps
    alias(libs.plugins.detekt)            // io.gitlab.arturbosch.detekt v1.23.8
}
```

### Key Configurations:
- **Java Toolchain:** JVM 21
- **Main Class:** `com.glassthought.shepherd.cli.AppMainKt`
- **Repositories:** mavenLocal() first, then mavenCentral()
- **Detekt Configuration:**
  - Baseline file: `$rootDir/detekt-baseline.xml`
  - Config file: `$rootDir/detekt-config.yml`
  - Parallel execution enabled
  - Wired to test task: `tasks.named("test") { dependsOn(tasks.named("detekt")) }`

### Dependencies (Key for Sonar):
- asgardCore, asgardTestTools (from maven local)
- Kotlin coroutines
- Jackson (JSON)
- Ktor server
- Kotest (testing)
- SnakeYAML, Guava, OkHttp

---

## 4. Source Directories

### Main Source Path:
```
app/src/main/kotlin/
  └── com/glassthought/shepherd/
      ├── cli/          # CLI entry point (AppMainKt)
      └── core/         # Core orchestration logic
```

### Test Source Path:
```
app/src/test/kotlin/
  └── (test implementations)
```

### Resources:
```
app/src/test/resources/
```

---

## 5. Dependency Version Management

**Location:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/gradle/libs.versions.toml`

**Catalog (Full Content):**

```toml
[versions]
guava = "33.4.6-jre"
kotest = "5.9.1"
asgard = "1.0.0"
kotlinx-coroutines = "1.10.2"
okhttp = "4.12.0"
json = "20240303"
snakeyaml = "2.2"
jackson = "2.17.2"
ktor = "3.1.1"
detekt = "1.23.8"

[libraries]
guava = { module = "com.google.guava:guava", version.ref = "guava" }
kotest-assertions-core = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
kotest-runner-junit5 = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }
asgard-test-tools = { module = "com.asgard:asgardTestTools", version.ref = "asgard" }
asgard-core = { module = "com.asgard:asgardCore", version.ref = "asgard" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
json = { module = "org.json:json", version.ref = "json" }
snakeyaml = { module = "org.yaml:snakeyaml", version.ref = "snakeyaml" }
jackson-databind = { module = "com.fasterxml.jackson.core:jackson-databind", version.ref = "jackson" }
jackson-module-kotlin = { module = "com.fasterxml.jackson.module:jackson-module-kotlin", version.ref = "jackson" }
ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
ktor-server-cio = { module = "io.ktor:ktor-server-cio", version.ref = "ktor" }
ktor-server-content-negotiation = { module = "io.ktor:ktor-server-content-negotiation", version.ref = "ktor" }
ktor-serialization-jackson = { module = "io.ktor:ktor-serialization-jackson", version.ref = "ktor" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version = "2.2.20" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```

**Note:** NO sonarqube plugin entry currently. Will need to add version for `org.sonarqube` plugin (latest: 5.1.0.4882).

---

## 6. Existing Reports Directory

**Location:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/_reports/`

**Current Status:** Empty directory exists, ready for report outputs.

**Structure:**
```
_reports/
├── (will contain sonar_report.json)
└── (can contain other reports)
```

---

## 7. Root-Level Scripts

**Existing Scripts at Repository Root:**

1. **run.sh** - Application runner
2. **test.sh** - Runs unit tests with self-healing asgard dependencies
3. **test_with_integ.sh** - Runs tests including integration tests
4. **sanity_check.sh** - Project sanity validation
5. **_prepare_pre_build.sh** - Ensures asgard libraries are in maven local
6. **gradle_run.sh** - Gradle task wrapper with caching
7. **gradle_tasks_jsonl_cached.sh** - Lists tasks as JSONL with UP-TO-DATE caching
8. **CLAUDE.generate.sh** - Generates CLAUDE.md from ai_input templates

**New Script to Create:** `run_sonar.sh` for explicit SonarQube analysis

---

## 8. Existing Sonar Configuration

**Current Status:** NO existing SonarQube configuration found

- No `org.sonarqube` plugin in build files
- No `sonar {}` block in any Gradle file
- No sonar-related environment variable handling
- No sonar token validation

**Files Checked:**
- `build.gradle.kts` ✓
- `app/build.gradle.kts` ✓
- `gradle/libs.versions.toml` ✓
- All gradle/ directory files ✓
- Root directory files ✓

---

## 9. GitHub Organization Context

From `.git/config`:
```
[remote "origin"]
    url = git@github.com:nickolay-kondratyev/dev-agent-harness.git
```

- **GitHub Org:** nickolay-kondratyev
- **Repository:** dev-agent-harness
- **SonarCloud Project Key Format:** `nickolay-kondratyev_dev-agent-harness`

---

## 10. Gradle Configuration Properties

**Location:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/gradle.properties`

```properties
org.gradle.configuration-cache=true
```

**Important:** Gradle configuration cache is enabled. SonarQube plugin requires `--no-configuration-cache` flag.

---

## 11. Project Settings (settings.gradle.kts)

**Location:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/settings.gradle.kts`

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "nickolay-kondratyev_dev-agent-harness"
include("app")
```

**Structure Implications:**
- Single included module: `app`
- Foojay resolver for automatic JDK downloads
- No additional multi-module configuration needed for Sonar

---

## 12. Task Definition Requirements Summary

For SonarQube integration, the following is needed:

### In libs.versions.toml:
```toml
[versions]
sonarqube = "5.1.0.4882"

[plugins]
sonarqube = { id = "org.sonarqube", version.ref = "sonarqube" }
```

### In root build.gradle.kts:
```kotlin
plugins {
    alias(libs.plugins.sonarqube)
}

sonar {
    properties {
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.organization", "nickolay-kondratyev")  // org key from SonarCloud
        property("sonar.projectKey", "nickolay-kondratyev_dev-agent-harness")
        property("sonar.token", System.getenv("SONAR_TOKEN") 
            ?: error("SONAR_TOKEN environment variable not set"))
        
        // Source paths
        property("sonar.sources", "app/src/main/kotlin")
        property("sonar.tests", "app/src/test/kotlin")
        
        // Exclusions
        property("sonar.exclusions", "**/build/**,**/.gradle/**,**/.kotlin/**,**/node_modules/**")
    }
}
```

### Execution Command:
```bash
./gradlew sonar --no-configuration-cache
```

### Script to Create (run_sonar.sh):
```bash
#!/usr/bin/env bash
./gradlew sonar --no-configuration-cache
```

---

## 13. Key Implementation Notes

1. **Configuration Cache Incompatibility:**
   - SonarQube plugin does NOT support Gradle configuration cache
   - Must use `--no-configuration-cache` flag when running
   - Override in command line, not in gradle.properties

2. **Token Management:**
   - Expects `SONAR_TOKEN` environment variable
   - Should fail loudly if not set (not optional)
   - Token comes from SonarCloud account settings

3. **Project Onboarding:**
   - According to ticket: project is already onboarded to SonarCloud
   - Token already generated and available as env var

4. **Report Output:**
   - Primary output: SonarCloud dashboard
   - For local JSON report: needs additional tooling (fetch via API or custom task)
   - Default location for .reports output: `_reports/sonar_report.json`

5. **Single vs Multi-Module:**
   - This is a single-module project (only `app` module)
   - Simpler Sonar configuration needed (no need for aggregate scans)

---

## 14. Testing & Validation Points

- Verify SONAR_TOKEN is available before running `./gradlew sonar`
- Confirm SonarCloud results visible at `https://sonarcloud.io/project/overview?id=nickolay-kondratyev_dev-agent-harness`
- Test local report generation script
- Validate JSON output format matches expected schema

---

## Summary

The project is a **single-module Kotlin/Gradle CLI application** with:
- ✓ Standard source structure (`app/src/main/kotlin`, `app/src/test/kotlin`)
- ✓ Central package: `com.glassthought.shepherd`
- ✓ Empty `_reports/` directory ready for outputs
- ✓ Configuration cache enabled (requires `--no-configuration-cache` for Sonar)
- ✓ Detekt already integrated (good foundation for quality gates)
- ✗ NO existing SonarQube configuration
- ✗ NO sonarqube plugin in libs.versions.toml
- ✓ SONAR_TOKEN environment variable expected (per ticket notes)
- ✓ Project already onboarded to SonarCloud (per ticket notes)

**Ready for implementation:** Add `org.sonarqube` plugin to gradle catalog and configure in root build.gradle.kts.

