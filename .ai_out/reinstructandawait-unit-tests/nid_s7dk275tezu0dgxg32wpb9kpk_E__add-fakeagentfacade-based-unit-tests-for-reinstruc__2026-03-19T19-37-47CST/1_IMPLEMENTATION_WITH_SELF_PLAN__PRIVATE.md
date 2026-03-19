# Implementation Private Notes

## Status: COMPLETE

## Key Implementation Details

- `buildHandle()` helper was extended with optional `guidValue` parameter to support scenario 3 (different handle identities). Default value preserves backward compatibility with all existing tests.
- Scenario 1 required a `buildSequentialFacade()` local helper function to create fresh `ArrayDeque`-backed facades per `it` block, since Kotest `it` blocks run independently and a shared deque would be exhausted by the first `it` block.
- Detekt enforced `TooGenericExceptionThrown` -- switched from `throw RuntimeException(...)` to `error(...)` which throws `IllegalStateException`.

## No follow-up items.
