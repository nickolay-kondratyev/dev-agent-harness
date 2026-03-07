# Plan Reviewer Private Context

## Review Session: 2026-03-07

### Key Findings

1. **No blockers found.** The plan is well-structured and follows project conventions.

2. **Most important implementation concern**: The `tmux send-keys` literal flag (`-l`). Without it, text containing tmux key names (Space, Enter, Escape, etc.) will be misinterpreted. This is a correctness issue, not just a nice-to-have. The implementer MUST use `-l` for literal text delivery and send `Enter` separately.

3. **Test framework**: Project uses JUnit 5, NOT Kotest. CLAUDE.md mentions Kotest but project hasn't adopted it. New tests should follow existing JUnit 5 pattern for consistency.

4. **DRY concern**: If two classes are kept, `runTmuxCommand` will be duplicated. Either extract to a shared helper or merge classes. Merging into `TmuxController` would be the simpler path but the two-class split is also fine.

5. **Error diagnostics**: The plan's `runTmuxCommand` snippet loses stdout/stderr. Must capture output before `waitFor()` and include in exceptions.

### Decision: Skip Plan Iteration

All concerns are minor enough to address during implementation. No architectural changes needed. The reviewer signals PLAN_ITERATION can be SKIPPED.

### Items for Implementer to Address

- [ ] Use `tmux send-keys -l` for literal text, send `Enter` separately
- [ ] Capture tmux command output for error messages
- [ ] Make session cleanup non-optional (finally block or .use{} pattern)
- [ ] Address DRY for `runTmuxCommand` if keeping two classes
- [ ] Add Out logging for key operations
- [ ] Follow existing JUnit 5 test patterns (not Kotest)
- [ ] Consider `waitFor` timeout for tmux CLI calls
- [ ] Be explicit about InteractiveProcessRunner becoming unused in App.kt
