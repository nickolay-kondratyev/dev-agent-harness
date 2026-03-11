## Testing Standards

### Framework & Style
- **BDD with GIVEN/WHEN/THEN** using Kotest `DescribeSpec`.
- Unit tests extend `AsgardDescribeSpec`.
- Use `describe` blocks for GIVEN/AND/WHEN structure; `it` blocks for THEN assertions.

### Dependencies
- `AsgardDescribeSpec` comes from `testImplementation("com.asgard:asgardTestTools:1.0.0")`.
- Kotest deps must be declared explicitly (they are `implementation`, not `api`, in `asgardTestTools`):
  ```kotlin
  testImplementation(libs.kotest.assertions.core)  // io.kotest:kotest-assertions-core
  testImplementation(libs.kotest.runner.junit5)     // io.kotest:kotest-runner-junit5
  ```
- `outFactory` is **inherited** from `AsgardDescribeSpec` — do NOT construct `NoOpOutFactory` manually in tests.

### Integration Tests (environment-dependent)
- Gate entire `describe` blocks with `.config(isIntegTestEnabled())` for tests requiring external resources (e.g., tmux, network).
- Annotate the class with `@OptIn(ExperimentalKotest::class)`.
- Only entire test classes (or top-level describe blocks) may be enabled/disabled — NOT individual `it` blocks.
- `isIntegTestEnabled()` is defined in `app/src/test/kotlin/org/example/integTestSupport.kt` and reads the
  `runIntegTests` system property injected by Gradle. Enable via: `./gradlew :app:test -PrunIntegTests=true`.
  This is tracked as a Gradle task input so the cache invalidates automatically (unlike env vars).

### Integration Test Base Class (with AppDependencies)
- For integration tests requiring `AppDependencies` (tmux, LLM, etc.), extend `SharedAppDepDescribeSpec` instead of `AsgardDescribeSpec` directly.
- `SharedAppDepDescribeSpec` provides a shared singleton `AppDependencies` and pre-configured `AsgardDescribeSpecConfig.FOR_INTEG_TEST` settings.
- No config required — defaults pull from `SharedAppDepIntegFactory`.
- Access shared deps via the `appDependencies` property (e.g., `appDependencies.tmuxSessionManager`).
- See `SharedAppDepDescribeSpec` (ref.ap.20lFzpGIVAbuIXO5tUTBg.E) for full KDoc and examples.
- Location: `app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedAppDepDescribeSpec.kt`

### Suspend Context
- `describe` block bodies are **NOT** suspend contexts.
- Suspend calls must go inside `it` or `afterEach` blocks.

### One Assert Per Test
- Each `it` block contains **one logical assertion**.
- The `it` description clearly states what is being verified.
- No inline WHAT comments needed — the nested describe/it structure IS the documentation.
- See deep memory: `in_tests__one_assert_per_test.md`.

### Fail Hard, Never Mask
- Tests must **fail explicitly** when dependencies, setup, or configuration are missing.
- **No silent fallbacks**, no conditional skipping of individual tests.
- Only entire test classes may be enabled/disabled based on environment — NOT individual tests.
- See deep memory: `in_tests__fail_hard_never_mask.md`.

### Synchronization
- **Do NOT use `delay`** for synchronization in tests. Use proper await mechanisms or polling.

### Data-Driven Tests
- Use data-driven tests to eliminate duplication when testing the same logic with multiple inputs.

### Naming
- Focused, descriptive test names that read naturally in the GIVEN/WHEN/THEN tree.
