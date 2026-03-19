# Implementation: StdinUserQuestionHandler — PUBLIC

## Summary

Implemented `StdinUserQuestionHandler` — the V1 stdin/stdout implementation of the `UserQuestionHandler` interface. This class prints a formatted question prompt to stdout and reads a multi-line answer from stdin, terminated by an empty line (pressing Enter twice).

## Files Created

| File | Purpose |
|------|---------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/question/StdinUserQuestionHandler.kt` | V1 implementation of `UserQuestionHandler` |
| `app/src/test/kotlin/com/glassthought/shepherd/core/question/StdinUserQuestionHandlerTest.kt` | Unit tests (8 test cases) |

## Implementation Details

- Constructor accepts `BufferedReader` + `PrintWriter` + `DispatcherProvider` for testability, with defaults for production (stdin/stdout/Dispatchers.IO)
- Uses `withContext(dispatcherProvider.io())` for suspend-friendly blocking I/O
- Prints formatted output per spec (double-line header, context info, question, single-line separator with instructions)
- Multi-line input via `generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }` — functional style, detekt-compliant
- Blocks indefinitely — no timeout (intentional per spec)
- Uses `println` for user-facing output (not Out logging)

## Test Results

All 8 test cases pass:
- Single-line answer returns answer text
- Multi-line answer returns joined text
- EOF returns empty string
- Stdout contains part name
- Stdout contains sub-part name and role
- Stdout contains handshakeGuid
- Stdout contains question text
- Stdout contains submission instructions and header

Full `:app:test` suite passes (EXIT_CODE=0).

## Decisions

- Used `generateSequence` + `takeWhile` instead of while-loop with breaks to satisfy detekt `LoopWithTooManyJumpStatements` and align with project's functional style preference.
- Used `Dispatchers.Unconfined` in tests (in-memory readers, no actual blocking I/O).
