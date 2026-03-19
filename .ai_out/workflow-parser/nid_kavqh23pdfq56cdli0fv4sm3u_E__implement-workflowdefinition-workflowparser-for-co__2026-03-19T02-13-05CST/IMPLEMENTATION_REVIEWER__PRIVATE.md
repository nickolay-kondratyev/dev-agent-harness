# Implementation Reviewer -- Private Notes

## Review Process

1. Read all 4 implementation files + 2 context pattern files + spec + workflow JSONs
2. Verified all tests pass (`./gradlew :app:test --tests "com.glassthought.shepherd.core.workflow.*"`)
3. Verified `sanity_check.sh` passes
4. Confirmed no existing files were modified (purely additive change)
5. Cross-referenced against spec in `doc/schema/plan-and-current-state.md` lines 454-514
6. Checked pattern consistency with `TicketParser` and `ShepherdObjectMapper`

## What Was Checked

- Mutual exclusivity validation (parts XOR planningParts) -- correct
- Phase validation (straightforward=EXECUTION, planning=PLANNING) -- correct
- `executionPhasesFrom` required for with-planning -- correct
- Fail-fast on missing file -- correct
- Fail-fast on malformed JSON -- correct, wraps JacksonException into IllegalArgumentException
- Constructor injection pattern -- consistent with TicketParser
- OutFactory/Out usage -- correct, uses lazy debug, structured info
- DispatcherProvider for IO -- correct, consistent with TicketParser
- BDD test structure -- proper GIVEN/WHEN/THEN, one assert per `it`
- Test coverage -- straightforward happy path, with-planning happy path, mutual exclusivity (both, neither), missing executionPhasesFrom, missing file, malformed JSON, wrong phase (both directions)
- No existing tests removed
- No anchor points removed

## Key Finding

The only substantive issue is that `executionPhasesFrom` is silently accepted on straightforward workflows. This is a real bug that could mask configuration errors.

## Edge Cases Verified

- Empty parts list: Not tested, but would be valid per current code. The spec doesn't seem to require non-empty parts lists. Could be a future concern but not a bug now.
- `findProjectRoot()` traversal: Works correctly for any working directory within the project tree. Fails hard if not found.
