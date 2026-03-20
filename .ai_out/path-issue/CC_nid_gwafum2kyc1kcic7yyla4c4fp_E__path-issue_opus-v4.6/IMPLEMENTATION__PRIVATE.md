# Implementation Private State

## Completed

- Created `app/src/test/kotlin/com/glassthought/shepherd/integtest/TmuxPathIntegTest.kt`
- All unit tests pass (`./gradlew :app:test`)
- Integration test passes (`./gradlew :app:test -PrunIntegTests=true --tests "com.glassthought.shepherd.integtest.TmuxPathIntegTest"`)
- All tmux-related integration tests pass together

## Key Implementation Details

### Command Replacement Strategy
The command produced by `ClaudeCodeAdapter.buildStartCommand()` has this structure:
```
bash -c '...export PATH=$PATH:<dir> && claude --model sonnet --dangerously-skip-permissions "..."'
```

The replacement finds `claude --model` and replaces everything from that point to (but not including)
the closing `'` with `echo $PATH > <tmpFile>`, then re-appends the closing `'`.

### Why `$PATH` Works Inside Single Quotes
Inside `bash -c '...'`, the `$PATH` is a literal string passed to the inner bash process.
The inner bash then expands `$PATH` when evaluating the `export PATH=$PATH:<dir>` and the
subsequent `echo $PATH`. This is exactly the production behavior -- the adapter's
`escapeForBashC` only escapes single quotes, not dollar signs.

### Cleanup
- tmux sessions tracked in `createdSessions` list, killed in `afterEach`
- Temp output files tracked in `createdFiles` list, deleted in `afterEach`
- Both lists cleared after each test to prevent double cleanup

## Remaining Work
- Commit and close ticket (to be done by orchestrator/top-level agent)
