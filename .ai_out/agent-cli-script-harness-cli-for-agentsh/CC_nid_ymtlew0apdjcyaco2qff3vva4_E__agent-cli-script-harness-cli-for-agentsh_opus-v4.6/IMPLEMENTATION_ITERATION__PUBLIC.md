# Implementation Iteration 2 -- harness-cli-for-agent.sh

## Summary

All 3 required changes from the reviewer have been addressed. 1 of 2 suggestions was accepted and implemented. All 42 tests pass (37 original + 5 new).

---

## Required Changes -- Status

### 1. CRITICAL BUG: `read -r` + `set -e` silent exit -- FIXED

**File**: `scripts/harness-cli-for-agent.sh`, line 25

Changed `read -r port < "${PORT_FILE}"` to `read -r port < "${PORT_FILE}" || true`.

Added WHY comment explaining that `read` returns exit 1 at EOF when file lacks trailing newline, and `|| true` suppresses this to let the regex validation handle invalid values.

**Regression test added**: `test_port_file_no_trailing_newline` -- uses `printf "12345"` (no trailing newline) and verifies exit 0 with correct port reading.

### 2. IMPORTANT: Missing test for invalid port value -- ADDED

**File**: `scripts/test_harness_cli.sh`, `test_invalid_port_value()`

Writes `"abc"` to port file and verifies:
- Exit code 1
- stderr contains "Invalid port value"
- stderr includes the bad value "abc"

### 3. IMPORTANT: Anchor point cross-reference in design ticket -- ADDED

**File**: `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`, line 111

Added `<!-- ref.ap.8PB8nMd93D3jipEWhME5n.E -- implementation in scripts/harness-cli-for-agent.sh -->` below the `### Agent CLI Script` heading.

---

## Suggestions -- Evaluation

### 1. Declare `local json_body` once at top of `main()` -- ACCEPTED

Moved the `local json_body` declaration before the `case` statement, removing 4 redundant `local` declarations from individual branches. Clean DRY improvement with zero risk.

### 2. Validate non-empty `$2` for `question` and `failed` -- REJECTED

The `$# -lt 2` check already catches the missing argument case. An explicitly passed empty string `""` is a valid bash argument; content validation belongs to the server. Adding this check would be over-engineering for marginal value.

---

## Test Results

```
42 passed, 0 failed
```

New tests added:
- `test_port_file_no_trailing_newline` (2 assertions)
- `test_invalid_port_value` (3 assertions)

---

## Files Modified

| File | Change |
|------|--------|
| `scripts/harness-cli-for-agent.sh` | Fixed `read -r` bug, DRY `json_body` declaration |
| `scripts/test_harness_cli.sh` | Added 2 new test functions (5 assertions) |
| `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md` | Added AP cross-reference |

---

## Commit

`8daafd5` -- Fix read -r silent exit bug, add missing tests, DRY json_body, add AP cross-ref
