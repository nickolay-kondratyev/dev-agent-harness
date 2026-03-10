# Plan Review: Harness HTTP Server

## Executive Summary

The plan is well-structured, matches the ticket requirements, and follows existing codebase conventions closely. The design is appropriately simple for a stub server -- 4 data classes, 1 interface, 1 implementation, 1 port file helper. I have one critical issue (dependency declaration pattern inconsistency), two major concerns (PortFileManager design and test lifecycle), and several minor suggestions. With the adjustments below, PLAN_ITERATION can be skipped.

## Critical Issues (BLOCKERS)

### 1. Dependency Declaration: Inline Strings vs Version Catalog

- **Issue:** The plan declares Ktor dependencies as inline version strings (`"io.ktor:ktor-server-core:3.4.1"`) in `build.gradle.kts`. However, the exploration notes correctly identified that the project uses `gradle/libs.versions.toml` for version management. The exploration itself noted: "All deps declared via `libs.` prefix -- Ktor deps need catalog entries." Yet the plan contradicts this by saying "matching existing pattern -- no version catalog entries for Ktor."

- **Impact:** Looking at `build.gradle.kts`, inline strings ARE used for `asgardCore`, `okhttp`, `snakeyaml`, and `jackson`. So the plan is actually consistent with the existing pattern in this project. The exploration note was aspirational, not reflecting reality. **This is NOT a blocker after all** -- inline strings match the established pattern here. However, the plan's explanation is misleading ("matching existing pattern -- no version catalog entries") -- it should simply say "inline version strings, consistent with how other non-catalog dependencies (OkHttp, Jackson, asgardCore) are declared in this project."

- **Recommendation:** Clarify the rationale. The current wording is confusing because it implies versions catalogs are the pattern, then says they won't be used. Just state the truth: this project uses inline versions for most dependencies, and Ktor follows the same convention. **MINOR adjustment -- no iteration needed.**

## Major Concerns

### 1. PortFileManager: Over-Designed for What It Does

- **Concern:** The plan creates a `PortFileManager` interface with a companion factory (`standard()`, `withCustomPath()`), a private `PortFileManagerImpl`, and a `portFilePath()` accessor. This is 4 methods + a companion object + an interface/impl split for writing a number to a file and deleting that file.

- **Why:** SRP is good, but this is KISS territory. The port file logic is ~10 lines of code total (createDirectories + writeString + deleteIfExists). An interface + impl with factory methods is over-engineering for something that (a) has exactly one production caller, (b) has no behavioral variants, and (c) is trivially testable by just passing a `Path`.

- **Suggestion:** Simplify to a plain class that takes `portFilePath: Path` as a constructor parameter. No interface needed (there is no second implementation), no companion factory. The test injects a temp path directly. This follows PARETO -- 80% value, 20% code:

  ```kotlin
  class PortFileManager(private val portFilePath: Path) {
      fun writePort(port: Int) { ... }
      fun deletePort() { ... }

      companion object {
          val DEFAULT_PATH: Path = Path.of(
              System.getProperty("user.home"),
              ".chainsaw_agent_harness", "server", "port.txt"
          )
      }
  }
  ```

  The caller in `InitializerImpl` (or wherever wiring happens) does `PortFileManager(PortFileManager.DEFAULT_PATH)`. Tests do `PortFileManager(tempDir.resolve("port.txt"))`. Simple, explicit, testable. If a need for an interface arises later, it can be extracted in 30 seconds.

- **Severity:** This is a simplification opportunity, not a blocker. The implementer can decide. But the current design is more ceremony than the problem warrants.

### 2. Test Lifecycle: `withServer` Helper Hides Resource Cleanup

- **Concern:** The plan proposes a `withServer` helper function that starts the server, runs a block, and closes in `finally`. Every single `it` block will call `withServer`, starting and stopping an HTTP server per test. This is correct for isolation but could be slow (12 server start/stop cycles).

- **Why:** More importantly, the `withServer` pattern means each `it` block is completely self-contained -- there is no shared server across the `describe("GIVEN a started KtorHarnessServer")` block. This means the test structure implies a shared server (the GIVEN), but each test independently creates its own. This is slightly misleading but acceptable.

- **Suggestion:** Consider using Kotest's `beforeEach` / `afterEach` within the describe block to start/stop the server, making the shared-server lifecycle explicit and matching the GIVEN semantics. This also avoids repeating the `withServer` call in every `it` block. However, with `AsgardDescribeSpec` constraints, the `withServer` pattern is pragmatically fine. **No change required** -- just calling it out.

## Simplification Opportunities (PARETO)

### 1. Separate PortFileManager Tests: Unnecessary

- **Current approach:** Plan proposes separate `PortFileManager` tests AND server tests that also verify port file behavior.
- **Simpler alternative:** The server tests already verify port file creation, content, and deletion. Port file logic is ~10 lines. Testing it separately AND as part of server tests is redundant. Just test it through the server. If `PortFileManager` stays as a plain class (per suggestion above), it is thoroughly exercised by the server tests.
- **Value:** Fewer test files, less maintenance, same confidence. The PortFileManager unit tests can be dropped to ~3 tests if kept at all (writePort creates file, deletePort removes file, deletePort is idempotent). The server tests cover the rest.

### 2. `ServerTestFixture` Data Class

- **Current approach:** A `ServerTestFixture` data class wrapping server, portFilePath, and httpClient.
- **Simpler alternative:** Since `withServer` is a function that provides these, just pass them as parameters to the lambda. Or even simpler: just use local variables in the `withServer` block. A data class for 3 test-only fields is mild over-engineering but acceptable.
- **Value:** Less code, same clarity.

### 3. Response Body

- **Current approach:** All stubs return `{"status": "ok"}`.
- **Note:** The plan correctly identifies that the agent CLI does not parse response bodies. Returning an empty body with 200 status would also work and is even simpler. However, returning `{"status": "ok"}` is good practice and aids debugging. No change needed.

## Minor Suggestions

### 1. Ktor Version Verification

The plan assumes Ktor 3.4.1 is compatible with Kotlin 2.2.20. Ktor 3.4.1 does exist (released March 3, 2026), but explicit Kotlin compatibility was not confirmed. Ktor generally follows Kotlin versions closely. The implementer should verify this compiles in Phase 1 (which the plan already calls for). **No action needed** -- Phase 1 is exactly the right time to catch this.

### 2. `engine.resolvedConnectors()`

The plan references `engine.resolvedConnectors().first().port` as the Ktor 3 API for resolving the bound port. This is confirmed by [Ktor documentation](https://ktor.io/docs/server-configuration-code.html). Correct.

### 3. Thread Safety: `AtomicReference` vs Simple State

The plan mentions guarding against double-start with `AtomicReference` or similar. For a stub server that is called single-threaded from the harness lifecycle, a simple `private var started: Boolean` with `check(!started) { "Already started" }` is sufficient. `AtomicReference` is overkill unless there is concurrent access to `start()`. The plan leaves this to the implementer, which is fine.

### 4. Anchor Point in Ticket Completion

The ticket has explicit completion criteria about creating anchor points and cross-referencing them. The plan's Section 12 does not mention this. The implementer should remember to do this at close time (per the ticket's "Completion Criteria" section).

### 5. Logging Pattern for Endpoints

The plan uses `Val(endpoint, ValType.URL_PATH)` for endpoint logging. Given that `ValType` may not have `URL_PATH`, and the plan already suggests falling back to `STRING_USER_AGNOSTIC`, this is fine. The implementer should check what exists and use the best semantic match or create a new `ValType` if justified.

### 6. Port File: Trailing Newline Consideration

The plan says "no trailing newline -- matches what the shell script's `read -r` expects." Looking at the CLI script, `read -r port < "${PORT_FILE}" || true` is used, where `|| true` handles the case when the file lacks a trailing newline (read returns 1 at EOF without newline). So both with and without trailing newline work. The plan's choice is correct and consistent.

## Strengths

1. **Matches the ticket exactly.** All 4 endpoints, port file lifecycle, AsgardCloseable, package naming -- all align with the ticket spec. JSON payloads match the CLI script (`harness-cli-for-agent.sh`).

2. **Correct decision to NOT use `ktor-server-test-host`.** The core behavior under test is real port binding and port file I/O. Ktor's test host bypasses exactly the things that need testing. Using OkHttp (already in the project) as the test client is the right call.

3. **Phased implementation.** Dependencies first, then PortFileManager, then data classes, then server, then tests. This is a disciplined incremental approach with verification at each step.

4. **Correct `AsgardCloseable` integration.** The close lifecycle (stop engine, then delete port file) follows the existing pattern in `AppDependencies` and mirrors the thorgServer `PortMarker` pattern (which also tracks whether it was the one that marked the port).

5. **Appropriate scope.** Deferring `AppDependencies` wiring to a follow-up ticket keeps this ticket focused. The plan explicitly calls this out.

6. **Data classes per endpoint.** Even though some share the same shape today, having separate types is the right call for future type safety when real handlers are wired in.

7. **Test classification as unit tests** (not integration tests requiring `isIntegTestEnabled`). Binding to localhost port 0 is in-process and requires no external dependencies.

## Verdict

- [x] APPROVED WITH MINOR REVISIONS

**Revisions needed (all minor -- can be applied by implementer, no plan iteration):**

1. **Simplify `PortFileManager`**: Drop the interface/impl split and companion factory. Use a plain class with a `Path` constructor parameter and a `DEFAULT_PATH` companion constant.

2. **Clarify dependency declaration rationale**: Fix the misleading wording about version catalog vs inline strings.

3. **Consider dropping separate PortFileManager tests**: The server tests cover port file behavior end-to-end. If the implementer keeps PortFileManager tests, keep them minimal (3 tests max).

4. **Remember anchor point completion criteria** from the ticket when closing.

These are all adjustments the implementer can make during implementation without requiring a plan rewrite. **PLAN_ITERATION can be skipped.**
