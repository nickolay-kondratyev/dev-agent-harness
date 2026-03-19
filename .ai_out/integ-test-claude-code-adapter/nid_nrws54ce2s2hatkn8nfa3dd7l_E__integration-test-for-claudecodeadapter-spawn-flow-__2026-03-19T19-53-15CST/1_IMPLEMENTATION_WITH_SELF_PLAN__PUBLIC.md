# Implementation: Integration Test for ClaudeCodeAdapter Spawn Flow

## What Was Done

### Part 1: GlmConfig Data Class (ref.ap.8BYTb6vcyAzpWavQguBrb.E)

Created `GlmConfig` data class that encapsulates all GLM (Z.AI) env vars needed to redirect
Claude Code to the Anthropic-compatible GLM endpoint:

- `ANTHROPIC_BASE_URL`, `ANTHROPIC_AUTH_TOKEN`, `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC`
- `ANTHROPIC_DEFAULT_OPUS_MODEL`, `ANTHROPIC_DEFAULT_SONNET_MODEL`, `ANTHROPIC_DEFAULT_HAIKU_MODEL`

Provides `toEnvVarExports()` for shell command construction and `standard(authToken)` factory.

### Part 2: GLM Injection in ClaudeCodeAdapter

Modified `ClaudeCodeAdapter`:
- Added nullable `glmConfig: GlmConfig?` constructor parameter (default `null`)
- In `buildStartCommand()`, when `glmConfig` is non-null, GLM env var exports are prepended
  **before** the `cd` in the `innerCommand` string
- Updated `create()` factory to accept `glmConfig`

### Part 3: Wiring Through ContextInitializer

Refactored `ContextInitializerImpl`:
- Extracted `readZaiApiKey()` to read the API token once
- Token is shared between `NonInteractiveAgentRunner` and `ClaudeCodeAdapter` (via `GlmConfig.standard()`)
- `ClaudeCodeAdapter.create()` now receives `glmConfig` parameter

### Part 4: Unit Tests

Added to `ClaudeCodeAdapterTest`:
- Tests with `GlmConfig` present: verifies all 6 env var exports appear, ordering (before `cd`), and command integrity
- Tests with `GlmConfig` null: verifies no GLM env vars appear, command structure unchanged
- Tests for `GlmConfig.standard()` factory: verifies default model mappings and endpoint

### Part 5: Integration Test

Created `ClaudeCodeAdapterSpawnIntegTest`:
- Extends `SharedContextDescribeSpec`, gated with `isIntegTestEnabled()`
- Spawns a real Claude Code agent session via tmux with GLM redirect
- Validates: tmux session creation, start command contents (GLM vars, GUID, model, permissions),
  session ID resolution from JSONL file
- Proper cleanup via `afterEach` killing tmux sessions

## Files Created/Modified

| File | Action |
|------|--------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/GlmConfig.kt` | **Created** — GLM config data class |
| `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapter.kt` | **Modified** — Added `glmConfig` param, GLM prefix in `buildStartCommand()` |
| `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt` | **Modified** — Refactored to share ZAI API key, wire `GlmConfig` |
| `app/src/test/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapterTest.kt` | **Modified** — Added GLM config unit tests |
| `app/src/test/kotlin/com/glassthought/shepherd/integtest/ClaudeCodeAdapterSpawnIntegTest.kt` | **Created** — Integration test |

## Test Results

- `./gradlew :app:test` — **PASS** (all unit tests green)
- Integration test requires `-PrunIntegTests=true` with tmux and GLM token available

## Decisions Made

1. **GlmConfig is always wired in production `ContextInitializer`** — the ZAI API token is already required at startup, so `GlmConfig` is always constructed. This means all spawned agents in production will also use GLM. If production needs to NOT redirect to GLM, a separate concern should gate this (e.g., a config flag). For now, this aligns with the current design where GLM is the target.

2. **GLM env vars prepended before `cd`** — placed at the very beginning of the `innerCommand` string, before `cd` to working directory, per the deep memory specification.

3. **Single session per integration test run** — the test reuses the `SharedContextDescribeSpec` shared context and creates minimal tmux sessions, following the doc guidance on resource efficiency.
