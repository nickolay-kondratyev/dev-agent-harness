# Exploration: Wingman Session ID Tracker

## Package Structure
- `com.glassthought.chainsaw.core` has sub-packages: `directLLMApi`, `initializer`, `processRunner`, `tmux`
- New `wingman` package fits naturally at `com.glassthought.chainsaw.core.wingman`

## Test Structure
- Tests under `app/src/test/kotlin/` — two patterns:
  - `com.glassthought.*` for unit tests (e.g., `GLMHighestTierApiTest`)
  - `org.example.*` for integ tests (e.g., `TmuxCommunicatorIntegTest`)
- All unit tests extend `AsgardDescribeSpec` (BDD style with describe/it)
- `outFactory` is inherited from `AsgardDescribeSpec`
- Integ tests gated by `isIntegTestEnabled()` from `org.example.integTestSupport`

## Dependencies Available
- `org.json:json:20240303` — already in build.gradle.kts
- `com.asgard:asgardCore:1.0.0` — provides Out/OutFactory, AnchorPoint annotation
- Kotest assertions + runner for testing

## Exception Patterns
- Codebase uses `IllegalStateException` (e.g., in GLMHighestTierApi)
- `AsgardBaseException` exists in asgardCore but not yet used in chainsaw code

## AnchorPoint Usage
- `@AnchorPoint("ap.XXX.E")` annotation on classes
- Import: `com.asgard.core.annotation.AnchorPoint`

## Design Reference (from design ticket)
- Harness generates GUID, sends to agent as first TMUX message
- ClaudeCodeWingman searches `$HOME/.claude/projects/.../*.jsonl` for GUID
- Matched filename = session ID (UUID format, e.g., `77d5b7ea-cf04-453b-8867-162404763e18`)
- Fail-fast on no match or multiple matches

## Key Observations
- This is a pure unit-testable feature (file scanning with temp dirs)
- No integration test needed (no external system dependency)
- No new build.gradle.kts changes needed
