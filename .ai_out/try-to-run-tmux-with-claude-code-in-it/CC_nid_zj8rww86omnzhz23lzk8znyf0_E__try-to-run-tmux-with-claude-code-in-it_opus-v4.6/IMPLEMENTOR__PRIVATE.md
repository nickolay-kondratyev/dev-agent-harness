# IMPLEMENTOR Private State

## Completed Items
- [x] TmuxSessionManager.kt -- session lifecycle (create, kill, exists)
- [x] TmuxCommunicator.kt -- sendKeys (literal + Enter), sendRawKeys
- [x] App.kt -- updated main() to use tmux-based flow
- [x] TmuxSessionManagerTest.kt -- 4 tests covering lifecycle
- [x] TmuxCommunicatorTest.kt -- 1 test covering sendKeys via bash echo
- [x] All 12 tests passing
- [x] Committed: 7626cda

## Linter Modification Accepted
The linter modified TmuxCommunicator.sendKeys to use `-l` flag for literal text and separate `Enter` sending. This is a correct improvement -- without `-l`, tmux would interpret words like "Enter" or "Space" in the prompt text as key names rather than literal text.

## Key Patterns Used
- Constructor injection: `outFactory` passed to both classes
- `Out` logging: `outFactory.getOutForClass(ClassName::class)`
- `Val(value, ValType.STRING_USER_AGNOSTIC)` for session names
- `Val(value, ValType.SHELL_COMMAND)` for commands
- `suspend` + `withContext(Dispatchers.IO)` for ProcessBuilder calls
- `out.debug(message) { listOf(Val(...)) }` for lazy debug logging
- `NoOpOutFactory.INSTANCE` in tests
- JUnit `@Test` style tests (matching this repo's pattern, not thorg-root's Kotest)
- `@AfterEach` for tmux session cleanup in tests

## Not Implemented (out of scope)
- AsgardCloseable pattern for TmuxSession auto-cleanup (mentioned as optional in plan)
- No `run.sh` changes needed (tmux approach works without TTY passthrough)
