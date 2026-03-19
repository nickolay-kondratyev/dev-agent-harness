# Private: HealthTimeoutLadder Implementation

## Status: COMPLETE

## Plan (all done)
1. [x] Create HealthTimeoutLadder data class
2. [x] Refactor HarnessTimeoutConfig to use it
3. [x] Update HarnessTimeoutConfigTest
4. [x] Verify no other callers need updating
5. [x] Run tests — all pass

## Decisions
- Placed HealthTimeoutLadder in the same file as HarnessTimeoutConfig (closely related, same package)
- Updated forTests() values to 1s/5s/1s per spec (was 2s/5s/2s)
