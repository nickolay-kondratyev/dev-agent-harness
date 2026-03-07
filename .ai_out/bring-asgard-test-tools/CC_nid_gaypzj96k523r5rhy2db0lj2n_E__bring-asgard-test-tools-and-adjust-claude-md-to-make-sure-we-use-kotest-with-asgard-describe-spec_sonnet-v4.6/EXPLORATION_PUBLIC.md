# Exploration: Bring Asgard Test Tools & Migrate to Kotest

## Current State

### Build System
- **settings.gradle.kts**: includes composite build `submodules/thorg-root/source/libraries/kotlin-mp`
  - Currently substitutes: `asgardCore`, `asgardCoreShared`, `asgardCoreNodeJS`
  - Does **NOT** yet substitute `asgardTestTools`
- **app/build.gradle.kts**: uses JUnit 5 (`libs.junit.jupiter.engine`) + `kotlin-test`
- **gradle/libs.versions.toml**: no Kotest entries yet

### Existing Tests (all using JUnit 5 @Test)
1. `app/src/test/kotlin/org/example/AppTest.kt` - basic sanity test
2. `app/src/test/kotlin/org/example/InteractiveProcessRunnerTest.kt` - 5 tests with `runBlocking`
3. `app/src/test/kotlin/org/example/TmuxSessionManagerTest.kt` - 4 tests with `@AfterEach` cleanup
4. `app/src/test/kotlin/org/example/TmuxCommunicatorTest.kt` - 1 test with `Thread.sleep` polling

### AsgardDescribeSpec Location
- Path: `submodules/thorg-root/source/libraries/kotlin-mp/asgardTestTools/src/commonMain/kotlin/com/asgard/testTools/describe_spec/AsgardDescribeSpec.kt`
- Extends Kotest `DescribeSpec`
- Provides `outFactory` for structured logging
- Auto-detects WARNs during testing
- asgardTestTools group: `com.asgard`, artifact: `asgardTestTools`

### AsgardTestTools Dependencies
```
commonMain: asgardCore, kotest.assertions.core, kotest.framework.engine
jvmMain: kotlin.awaitility, kotest.framework.engine.jvm, kotest.runner.junit5, asgardCoreJVM
```

## Required Changes

### 1. settings.gradle.kts
- Add `asgardTestTools` to the composite build substitutions

### 2. app/build.gradle.kts
- Replace JUnit 5 with asgardTestTools dependency
- asgardTestTools pulls in Kotest runner transitively
- Keep `useJUnitPlatform()` (Kotest uses JUnit Platform runner)

### 3. gradle/libs.versions.toml
- May need to add Kotest version entries (or rely on transitive)

### 4. Test File Migration (4 files)
- Migrate from JUnit 5 `@Test` to Kotest `AsgardDescribeSpec`
- Use `describe`/`it` blocks for GIVEN/WHEN/THEN structure
- Replace `@AfterEach` with Kotest `afterEach` lambda
- One assert per `it` block

### 5. CLAUDE.md (ai_input source)
- Standards already correct in `ai_input/memory/auto_load/4_testing_standards.md`
- May need minor updates to clarify AsgardDescribeSpec is from asgardTestTools

## Key Notes
- THORG_ROOT env must be set: `export THORG_ROOT=$PWD/submodules/thorg-root`
- CLAUDE.md already mandates Kotest DescribeSpec - tests just haven't been updated
- TmuxCommunicatorTest has `Thread.sleep` anti-pattern - should use awaitility from asgardTestTools
- asgardTestTools version: `1.0.0`, group: `com.asgard`
