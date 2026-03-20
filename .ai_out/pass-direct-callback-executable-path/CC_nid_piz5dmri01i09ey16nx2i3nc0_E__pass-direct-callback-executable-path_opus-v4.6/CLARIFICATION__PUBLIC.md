# Clarification: Pass Direct Callback Executable Path

## Requirement
Replace script-name-only references (`callback_shepherd.signal.sh`) in agent instructions with full absolute paths (`/tmp/shepherd-callback-scripts-XXX/callback_shepherd.signal.sh`).

## Key Decisions
- **Keep PATH export**: Retain `export PATH=$PATH:${callbackScriptsDir.path}` in `ClaudeCodeAdapter` as defense-in-depth
- **Change instructions only**: The instructions that agents receive will now use full paths instead of bare script names
- **Thread path through**: `CallbackScriptsDir` → `ContextForAgentProviderImpl` → `CallbackHelp`/`FeedbackItem` sections, and `SelfCompactionInstructionBuilder`

## No ambiguities — clear and simple scope.
