# DRY + Specialist Review — Private Notes

## What I Looked At

Files read:
- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/HarnessServer.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/PortFileManager.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/AgentRequests.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/Initializer.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/cli/AppMain.kt`
- `app/src/test/kotlin/com/glassthought/chainsaw/core/server/KtorHarnessServerTest.kt`
- `app/src/test/kotlin/com/glassthought/chainsaw/core/server/PortFileManagerTest.kt`
- `scripts/harness-cli-for-agent.sh`
- `scripts/test_harness_cli.sh` (first 50 lines, pattern confirmed)
- Grep for `DEFAULT_PATH`, `chainsaw_agent_harness`, `port.txt` across the entire repo

## Reasoning on Each Concern

### Concurrency
- `engine` and `boundPort` are non-volatile vars.
- `start()` and `close()` are suspend functions called from the harness coordinator, which
  in V1 is a single `runBlocking` coroutine. The dispatcher for that coroutine could in
  theory be multithreaded but in practice (no explicit Dispatchers.Default switch) runs on
  the single main thread that calls `runBlocking`.
- Ktor CIO request handlers run on the CIO dispatcher (thread pool). BUT they only access
  `out` (val, set in constructor, thread-safe) and `call` (coroutine-scoped). They never
  read or write `engine` or `boundPort`.
- Conclusion: No real thread safety issue in V1. `@Volatile` on `engine`/`boundPort` would
  be theoretically cleaner but is low-ROI given V1 serial design. Not flagged as a finding.

### port() after close() race
- `close()` sets `boundPort = null`, then `port()` would throw.
- In V1, the harness calls `start()`, runs all phases, then calls `close()`.
  `port()` is only called during phase execution (before `close()`).
- No concurrent caller would observe this race in V1.
- Verdict: theoretical, not real. Not flagged.

### Stale port file
- Overwriting with `Files.writeString` is atomic at the OS level (POSIX rename semantics
  don't apply here — `writeString` with default TRUNCATE_EXISTING does a full write).
  In practice: the file is truncated then written. A reader that reads between truncate and
  write gets an empty file, which the shell script correctly rejects as "invalid port".
  The window is microseconds and only relevant if two harness instances start simultaneously.
  V1 is single-instance. Acceptable.
- No `deletePort()` before `writePort()` — correct. Delete-then-write would create a
  larger window where the file doesn't exist. Overwrite-in-place is better.

### Question endpoint — blocking vs non-blocking
- The shell script help text says: "This call returns immediately; the answer will be
  delivered back to you via TMUX — just wait for it."
- This is the documented V1 design: fire-and-forget POST, answer comes separately via TMUX.
- The current impl (immediate 200) matches the "call returns immediately" contract.
- BUT: no TMUX answer delivery code exists anywhere. So the agent fires the question,
  gets 200, and... nothing else happens. The endpoint is not just a stub; it is an
  incomplete half of a two-part handshake.
- This is important to call out at the routing site so the gap is obvious to implementors.
- Verdict: NOT_READY at the "no TODO comment" level. The behavioral gap (no TMUX delivery)
  is a future ticket concern, not a blocker for merging the server infrastructure.

### DEFAULT_PATH unused in production
- Grep confirms it appears only in the definition file (`PortFileManager.kt`).
- No reference in `Initializer.kt`, `AppMain.kt`, or any other main-source file.
- The server is not yet wired into the main application at all (AppMain.kt still does
  the early tmux demo).
- The constant will become used when wiring happens. No false alarm — just noting for
  completeness. Raised as OPTIONAL.

### DRY: handleAgentRequest
- Good abstraction. The four endpoints share identical receive-log-respond behavior.
- Using `inline reified` is the correct Kotlin approach to DRY this up while preserving
  Jackson's type inference.
- No issue here.

### DRY: AgentRequest data classes
- `AgentDoneRequest` and `AgentStatusRequest` both have only `branch: String`.
- But they represent different endpoint contracts. If we added retry-count to `done` or
  session-id to `status`, they would diverge. They should remain separate types.
- This is code that looks similar but represents different knowledge. Not a DRY violation.

### DRY: Port file path across Bash/Kotlin
- This is real knowledge duplication. The path `$HOME/.chainsaw_agent_harness/server/port.txt`
  appears as a string literal in the shell script AND as a composed path in Kotlin.
- They must stay in sync for the system to work. There is no test that validates this
  (would require shelling out to Kotlin from bash tests or vice versa — impractical).
- The only practical fix is explicit cross-reference comments. Raised as CRITICAL.
- Worth noting: `test_harness_cli.sh` also hardcodes this path repeatedly in its test
  fixtures (lines 74, 75, 125, 127, 146, 147, etc.). This is fine for test code — tests
  are allowed to be more explicit/repetitive. Not flagging test code.

## Verdict Rationale

NOT_READY because:
1. The Bash/Kotlin path duplication is a real maintenance trap with no guard rail. Easy to
   fix with comments. Should not ship without it.
2. The missing stub comment on `/agent/question` is a correctness communication failure.
   It will mislead future implementors and anyone reading the code.

Both fixes are small (comments, not code changes). The implementation itself is sound.
