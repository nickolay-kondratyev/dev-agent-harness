# Exploration: StdinUserQuestionHandler

## Existing Infrastructure

| File | Status | Purpose |
|------|--------|---------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/question/UserQuestionHandler.kt` | ✅ Exists | `fun interface` with `suspend fun handleQuestion(context: UserQuestionContext): String` |
| `app/src/main/kotlin/com/glassthought/shepherd/core/question/UserQuestionContext.kt` | ✅ Exists | Data class: question, partName, subPartName, subPartRole (SubPartRole enum), handshakeGuid (HandshakeGuid inline class) |
| `app/src/main/kotlin/com/glassthought/shepherd/core/infra/UserInputReader.kt` | ✅ Exists | `fun interface UserInputReader { suspend fun readLine(): String? }` + `DefaultUserInputReader` |
| `app/src/main/kotlin/com/glassthought/shepherd/core/infra/ConsoleOutput.kt` | ✅ Exists | Has `printlnRed()` + ANSI colors |
| `app/src/main/kotlin/com/glassthought/shepherd/core/infra/DispatcherProvider.kt` | ✅ Exists | For `withContext(Dispatchers.IO)` |

## Files to Create

| File | Purpose |
|------|---------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/question/StdinUserQuestionHandler.kt` | V1 implementation |
| `app/src/test/kotlin/com/glassthought/shepherd/core/question/StdinUserQuestionHandlerTest.kt` | Unit tests |

## Key Design Notes
- Ticket suggests accepting `BufferedReader` + `PrintWriter` for testability (defaults to stdin/stdout)
- Existing `UserInputReader` already wraps `readLine()` — consider using it or the BufferedReader approach
- `println` is allowed for user-facing output (not Out logging)
- Two consecutive newlines (empty line) terminates multi-line input
- No timeout — blocks indefinitely
