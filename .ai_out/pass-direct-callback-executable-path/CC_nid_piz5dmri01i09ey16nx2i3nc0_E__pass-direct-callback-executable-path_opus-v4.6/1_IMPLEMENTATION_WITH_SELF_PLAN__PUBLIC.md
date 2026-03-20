# Pass Direct Callback Executable Path

## What Was Done

Replaced PATH-based resolution of callback scripts with explicit full absolute paths in all agent instruction sections. This fixes tmux environments where PATH modifications are not inherited by agent subprocesses.

## Changes

### Core: CallbackScriptsDir
- Added `signalScriptPath` and `queryScriptPath` derived properties that produce the full absolute path to `callback_shepherd.signal.sh` and `callback_shepherd.query.sh`.

### Instruction Sections
- `InstructionSection.CallbackHelp` now accepts `callbackSignalScriptPath` and `callbackQueryScriptPath` and renders them as full paths instead of bare script names.
- `InstructionSection.FeedbackItem` now accepts `callbackSignalScriptPath` and renders it as a full path.
- Text updated from "Two scripts on your $PATH" to "Two callback scripts" (no longer depends on PATH).

### Context Assembly
- `ContextForAgentProvider.standard()` now requires `CallbackScriptsDir`.
- `ContextForAgentProviderImpl` stores `CallbackScriptsDir` and builds `CallbackHelp` via a `callbackHelpSection()` helper.

### Self-Compaction
- `SelfCompactionInstructionBuilder.build()` now takes `callbackSignalScriptPath` and uses the full path in the rendered callback command.

### Execution Infrastructure
- `PartExecutorDeps` carries `CallbackScriptsDir` for threading to all subsystems.
- `InnerFeedbackLoop` / `InnerFeedbackLoopDeps` thread `callbackSignalScriptPath` to `FeedbackItem`.
- `ContextInitializer.Infra` includes `CallbackScriptsDir`.

### Defense in Depth
- The `export PATH=$PATH:${callbackScriptsDir.path}` in `ClaudeCodeAdapter` was kept as a fallback safety net.

## Decisions

- Used `@Suppress("LongParameterList")` on `InnerFeedbackLoop.buildFeedbackItemRequest()` since it's a factory method constructing a complex request object (6 params, threshold 6).
- Extracted `buildGitCommitStrategy()` from `ProductionPartExecutorFactoryCreator.create()` to stay under the 60-line method length limit.
- Created `ContextTestFixtures.standardProvider()` helpers to DRY up all test `ContextForAgentProvider.standard()` calls and fix MaxLineLength violations.

## Tests

All 1482 tests pass (8 skipped integration tests). All detekt static analysis checks pass.
