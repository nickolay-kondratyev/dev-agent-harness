# Implementation: StdinUserQuestionHandler — PRIVATE

## Status: COMPLETE

## Plan (executed)

1. Read spec, interface, context class, infra dependencies
2. Implement `StdinUserQuestionHandler` with `BufferedReader`/`PrintWriter` for testability
3. Write unit tests following BDD Kotest DescribeSpec pattern
4. Fix detekt issue (loop with multiple breaks -> functional `generateSequence`)
5. Verify all tests pass

## Decisions

- Used `generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }` instead of a while-loop with breaks to satisfy detekt's `LoopWithTooManyJumpStatements` rule. This is also more idiomatic Kotlin (functional style per project guidelines).
- Used `Dispatchers.Unconfined` in tests since we use in-memory `StringReader`/`StringWriter` with no actual blocking I/O.
- Constructor defaults to stdin/stdout for production use; tests inject `StringReader`/`StringWriter`.
