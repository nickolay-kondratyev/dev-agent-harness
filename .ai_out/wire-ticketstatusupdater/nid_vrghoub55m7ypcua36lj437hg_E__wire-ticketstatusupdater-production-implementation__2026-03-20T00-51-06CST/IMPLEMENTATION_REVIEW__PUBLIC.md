# Implementation Review: Wire TicketStatusUpdater Production Implementation

## Summary

This change wires the production `TicketStatusUpdater` by:
1. Creating `TicketStatusUpdaterImpl` that calls `ticket close <ticketId>` via `ProcessRunner`
2. Introducing `TicketStatusUpdaterFactory` fun interface to defer construction until ticket ID is known
3. Wiring the factory into `TicketShepherdCreatorImpl` with a production default
4. Adding `ShepherdValType.TICKET_ID` for structured logging
5. Unit tests for the new impl

**Overall assessment: APPROVE.** Clean, well-structured change that follows established patterns. Two suggestions below, neither blocking.

## CRITICAL Issues

None.

## IMPORTANT Issues

None.

## Suggestions

### 1. Test coverage: missing failure-path test for `TicketStatusUpdaterImpl`

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/test/kotlin/com/glassthought/shepherd/usecase/ticketstatus/TicketStatusUpdaterImplTest.kt`

The two test cases only cover the happy path (process runner succeeds). There is no test verifying behavior when `processRunner.runProcess` throws an exception (e.g., `ticket` CLI not found, non-zero exit code). Currently `TicketStatusUpdaterImpl` does not catch exceptions, which means exceptions from `ProcessRunner` will propagate up naturally -- this is correct behavior per the "don't log and throw" principle. However, a test documenting that the second log line (`ticket_closed`) is NOT emitted on failure, and that the exception propagates, would be valuable for capturing that intent.

This is non-blocking because the current behavior (let exception propagate) is already correct by default.

### 2. Two test cases are nearly identical -- consider data-driven test

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-3/app/src/test/kotlin/com/glassthought/shepherd/usecase/ticketstatus/TicketStatusUpdaterImplTest.kt`

Per CLAUDE.md testing standards: "Use data-driven tests to eliminate duplication when testing the same logic with multiple inputs." The two test cases (`abc-123` and `nid_xyz_task`) test the exact same logic with different ticket IDs. These could be consolidated into a data-driven test. Non-blocking -- current form is readable and only two cases.

## Architecture Assessment

- **Factory pattern**: Appropriate. Follows the established `AllSessionsKillerFactory` pattern exactly. The ticket ID is only known at `create()` time, so a factory is the right abstraction.
- **`ticket` vs `tk`**: Good decision to use `ticket` (the actual executable) rather than `tk` (shell alias). Well-documented in the KDoc WHY comment.
- **ProcessRunner**: Correct choice -- follows codebase conventions for subprocess execution.
- **Structured logging**: Both log statements use `ShepherdValType.TICKET_ID` with `Val()` -- compliant with logging standards.
- **No lost functionality**: The only removed code was the `TODO("TicketStatusUpdater not yet wired for production")` stub, which is exactly what this change was supposed to replace. Existing tests in `TicketShepherdCreatorTest` updated minimally (stub -> factory) with no behavioral test removal.

## Documentation Updates Needed

None. KDoc is clear and up to date. The "Deps not yet wired internally" section in `TicketShepherdCreatorImpl` was correctly updated to remove `ticketStatusUpdater` from the list.
