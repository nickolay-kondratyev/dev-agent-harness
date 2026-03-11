# Implementation Review: Remove SpawnTmuxAgentSessionUseCase

## Verdict: PASS

---

## Summary

The implementation correctly removes `SpawnTmuxAgentSessionUseCase` and its full dependency chain. All ticket-specified files are gone. The dependency chain discovery was thorough — the agent identified and removed 6 additional orphaned files beyond the 5 ticket-specified ones (factories, interfaces, bundle data class, and their tests). Modified files (`Initializer.kt`, `SharedContextIntegFactory.kt`, `AppDependenciesCloseTest.kt`, and 3 KDoc cleanups) are correct and complete.

Build: SUCCESS. Tests: 115 passed, 0 failures, 0 errors (verified with `--rerun-tasks`).

---

## Verification Results

### Stale Reference Check
Grep for all removed types across `app/src/`:
- `SpawnTmuxAgentSessionUseCase` — 0 matches
- `StartAgentRequest` — 0 matches
- `PhaseType` — 0 matches
- `spawnTmuxAgentSession` — 0 matches
- `AgentStarterBundleFactory` — 0 matches
- `AgentTypeChooser` — 0 matches
- `AgentStarterBundle` — 0 matches
- `UseCases` — 0 matches

All clean.

### Completeness Check

Ticket-specified removals (5/5 done):
1. `SpawnTmuxAgentSessionUseCase.kt` — removed
2. `StartAgentRequest.kt` — removed
3. `PhaseType.kt` — removed
4. `SpawnTmuxAgentSessionUseCaseIntegTest.kt` — removed
5. `spawnTmuxAgentSession` field from `UseCases` — removed (along with the now-empty `UseCases` data class itself)

Additional orphan cleanup (correct decision):
- `AgentStarterBundleFactory.kt`, `AgentTypeChooser.kt`, `ClaudeCodeAgentStarterBundleFactory.kt`, `AgentStarterBundle.kt` — removed
- `DefaultAgentTypeChooserTest.kt`, `ClaudeCodeAgentStarterBundleFactoryTest.kt` — removed (tests for removed classes)

### Correctness of Modified Files

`Initializer.kt` — `UseCases` data class removed entirely (correct: an empty data class would be misleading). `ShepherdContext` now takes only `infra: Infra`. `Initializer.initialize()` signature simplified to `(outFactory, httpClient?)`. Clean and sensible.

`SharedContextIntegFactory.kt` — Updated to call `Initializer.standard().initialize(outFactory = ...)` without the removed `environment` and `systemPromptFilePath` arguments. `resolveSystemPromptFilePath()` and `findGitRepoRoot()` helpers removed. Clean.

`AppDependenciesCloseTest.kt` — `Environment` import and `environment = Environment.test()` argument removed. Test still correctly verifies OkHttpClient shutdown.

KDoc cleanups in `TmuxAgentSession.kt`, `ClaudeCodeAgentStarter.kt`, `HandshakeGuid.kt` — dangling references to removed types cleaned up correctly.

### No Over-Removal

Files correctly retained:
- `Environment.kt` + `EnvironmentTest.kt` — `Environment` has its own direct test coverage and is a valid domain concept for future use.
- `AgentType.kt` — actively used by `ResumableAgentSessionId` and `ClaudeCodeAgentSessionIdResolver`.
- `TmuxAgentSession.kt` — valid domain concept (tmux session + resolved session ID pair), will be used in the new implementation.
- `ClaudeCodeAgentStarter.kt` — builds the claude CLI command; independently valid.

---

## Minor Observations (non-blocking)

Three empty directories remain after removals:
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/useCase/`
- `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/impl/`
- `app/src/test/kotlin/com/glassthought/ticketShepherd/core/agent/impl/`

Git does not track empty directories so these do not affect the build or version control. They will disappear once the new implementations are placed there or can be cleaned up with a `rmdir`. This is a non-issue.

---

## CLAUDE.md Alignment

- No `@Deprecated` usage.
- No stale imports or dead code left behind.
- `UseCases` empty data class correctly removed rather than kept as a stub (avoids misleading abstraction).
- All modified tests still follow BDD GIVEN/WHEN/THEN pattern.
- No behavior-capturing tests removed beyond those that exclusively tested the removed code.
