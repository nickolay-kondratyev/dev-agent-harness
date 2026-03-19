# Implementation Review -- Private Notes

## Verification Steps Performed

1. `sanity_check.sh` -- PASS
2. `./gradlew :app:test` -- PASS (BUILD SUCCESSFUL)
3. `./gradlew :app:detekt` -- PASS
4. Verified old files deleted (glob for `agent/starter/**/*` and `sessionresolver/impl/**/*` -- no matches)
5. Verified `HandshakeGuid.kt` and `ResumableAgentSessionId.kt` still exist with updated doc refs
6. Verified no stale references to `AgentStarter`, `AgentSessionIdResolver`, `ClaudeCodeAgentStarter`,
   `ClaudeCodeAgentSessionIdResolver` in `app/src/` (only historical mentions in KDoc comments)
7. Verified detekt baseline cleanup is valid -- all removed entries reference code that no longer exists
8. Traced shell escaping logic through concrete examples to verify correctness
9. Checked old `ClaudeCodeAgentStarter` (via `git show main:...`) to confirm `SERVER_PORT` gap is pre-existing

## Analysis Details

### Shell escaping deep-dive

Traced `shellQuote` + `escapeForBashC` interaction:
- `shellQuote` wraps bootstrap message in double quotes, escaping `\`, `"`, `$`, backtick, `!`
- `escapeForBashC` escapes single quotes in the outer `bash -c '...'` wrapper
- Two-layer escaping is correct: inner double-quoted argument inside outer single-quoted bash -c

The `DOLLAR` constant workaround (line 218) is needed because Kotlin string templates would
interpret `"\\$"` as a template start. Using `"\\$DOLLAR"` where DOLLAR='$' correctly produces
the replacement string `\$`.

### BuildStartCommandParams vs spec signature

The spec explicitly shows `buildStartCommand(bootstrapMessage: String)`. The old code had
per-session values as constructor parameters on `ClaudeCodeAgentStarter` (instantiated per session).
The new code has them as method parameters via `BuildStartCommandParams`.

Both approaches are valid. The params-based approach is better if one `ClaudeCodeAdapter` instance
serves multiple sessions (which is the case here -- it's wired once in `ContextInitializer`).
But the spec should be updated.

### Why the secondary constructor pattern works but is suboptimal

The mutable `guidScanner` field creates a temporal coupling: the primary constructor initializes
the field, then the secondary constructor body overwrites it. This is safe because Kotlin guarantees
constructor body runs before any method call, but it:
1. Creates a throwaway `FilesystemGuidScanner` with `/dev/null`
2. Uses a mutable field where immutable would be preferred
3. Has a public constructor that should be `internal`

A cleaner approach: single primary constructor taking `GuidScanner`, with companion factory.

### Pre-existing gap: SERVER_PORT

Confirmed via `git show main:...ClaudeCodeAgentStarter.kt` that the old implementation also
did not export `TICKET_SHEPHERD_SERVER_PORT`. This is a spec gap, not a regression. Should
be tracked as a follow-up ticket.
