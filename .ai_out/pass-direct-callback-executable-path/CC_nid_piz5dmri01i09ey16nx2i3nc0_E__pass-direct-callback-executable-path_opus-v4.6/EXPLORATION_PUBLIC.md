# Exploration: Pass Direct Callback Executable Path

## Problem
The temp directory containing `callback_shepherd.signal.sh` isn't reflected in PATH when Claude Code runs in tmux sessions, even though the handshake GUID environment variable works fine.

## Current Architecture

### Callback Script Flow
1. `ContextInitializerImpl.resolveCallbackScriptsDir()` extracts `callback_shepherd.signal.sh` from classpath to a temp dir
2. `CallbackScriptsDir` wraps the validated temp dir path (e.g., `/tmp/shepherd-callback-scripts-XXX/`)
3. `ClaudeCodeAdapter.buildStartCommand()` exports `PATH=$PATH:${callbackScriptsDir.path}` in the tmux session
4. Agent instructions reference scripts by name only: `callback_shepherd.signal.sh done completed`

### Key Files
| File | Role |
|------|------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/CallbackScriptsDir.kt` | Validated callback scripts directory |
| `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapter.kt` | Builds tmux start command, exports PATH |
| `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt` | `CallbackHelp` and `FeedbackItem` render script name in instructions |
| `app/src/main/kotlin/com/glassthought/shepherd/core/context/ProtocolVocabulary.kt` | `CALLBACK_SIGNAL_SCRIPT` / `CALLBACK_QUERY_SCRIPT` constants |
| `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt` | Constructs instruction plans with sections |
| `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt` | Interface + factory |
| `app/src/main/kotlin/com/glassthought/shepherd/core/compaction/SelfCompactionInstructionBuilder.kt` | Uses script name in compaction instructions |
| `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt` | Wires CallbackScriptsDir |

### Places Using Script Name (need full path)
1. **`InstructionSection.CallbackHelp.render()`** — references `CALLBACK_SIGNAL_SCRIPT` and `CALLBACK_QUERY_SCRIPT`
2. **`InstructionSection.FeedbackItem.render()`** — references `CALLBACK_SIGNAL_SCRIPT`
3. **`SelfCompactionInstructionBuilder.build()`** — references `CALLBACK_SIGNAL_SCRIPT`

## Solution Approach
1. Add `signalScriptPath` property to `CallbackScriptsDir`
2. Add `callbackSignalScriptPath` / `callbackQueryScriptPath` to `CallbackHelp`, `FeedbackItem`
3. Thread `CallbackScriptsDir` through `ContextForAgentProviderImpl` → sections
4. Update `SelfCompactionInstructionBuilder.build()` to accept full path
5. Keep PATH export in `ClaudeCodeAdapter` as defense-in-depth
