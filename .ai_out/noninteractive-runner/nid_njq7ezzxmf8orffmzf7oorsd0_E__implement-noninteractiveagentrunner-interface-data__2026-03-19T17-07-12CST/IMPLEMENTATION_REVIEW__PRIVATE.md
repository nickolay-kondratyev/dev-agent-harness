# Implementation Review: NonInteractiveAgentRunner (Private Notes)

## Review Checklist

### Spec Compliance
- [x] Interface matches spec exactly: `suspend fun run(request): NonInteractiveAgentResult`
- [x] `NonInteractiveAgentRequest` fields match spec: instructions, workingDirectory (Path), agentType (AgentType), model (String), timeout (Duration)
- [x] `NonInteractiveAgentResult` sealed class matches spec: Success(output), Failed(exitCode, output), TimedOut(output)
- [x] CLAUDE_CODE command: `claude --print --model {model} -p '{instructions}'` -- correct
- [x] PI command: `export ZAI_API_KEY=... && pi --provider zai --model {model} -p '{instructions}'` -- correct
- [x] Exit code 0 -> Success, non-zero -> Failed, timeout -> TimedOut -- correct
- [x] Working directory via `cd` wrapper (ProcessRunner limitation documented in exploration) -- correct

### Architecture
- [x] Constructor injection: ProcessRunner, OutFactory, zaiApiKey -- good
- [x] No singletons, no DI framework
- [x] Interface in separate file from impl -- they're in the same package but different files; interface file also contains data classes (acceptable)
- [x] `when` on `AgentType` enum without `else` -- compiler-enforced exhaustiveness -- good
- [x] Sealed class `FakeProcessBehavior` in test -- good pattern

### Security
- [x] Shell escaping uses single-quote wrapping with `'\''` -- industry-standard safe approach
- [x] No hardcoded secrets -- zaiApiKey passed via constructor
- [x] ZAI_API_KEY only exported for PI, not CLAUDE_CODE -- correct per spec

### Issues Found
1. **Pair usage in test** -- CLAUDE.md violation, should use named data class
2. **No timeout forwarding test** -- meaningful gap since timeout is only safeguard
3. **`shouldBeInstanceOf` more idiomatic** -- minor

### Things That Are Fine
- `internal` visibility on `buildShellCommand` -- enables direct command construction testing, good tradeoff
- `combineOutput` logic handles blank-only cases correctly
- `FakeProcessRunner` throwing `UnsupportedOperationException` for unused methods -- appropriate for test double
- Test structure is clean BDD with one assert per `it` block
- All 19 tests pass, build is green
