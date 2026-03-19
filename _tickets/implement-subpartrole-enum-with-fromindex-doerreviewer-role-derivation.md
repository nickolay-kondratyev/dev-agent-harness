---
id: nid_6zpwfuz85gl4x175fcudp9lju_E
title: "Implement SubPartRole enum with fromIndex() — doer/reviewer role derivation"
status: open
deps: []
links: []
created_iso: 2026-03-19T00:39:14Z
status_updated_iso: 2026-03-19T00:39:14Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, sessions-state, foundational]
---

## Context

Spec: `doc/core/SessionsState.md` (ref.ap.7V6upjt21tOoCFXA7nqNh.E), section "Entry Structure".

`SubPartRole` is a two-value enum that derives role from sub-part array position. Position 0 = DOER, position 1 = REVIEWER. Used for `/callback-shepherd/signal/done` result validation (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) — doers send `completed`, reviewers send `pass` or `needs_iteration`.

## What to Implement

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/state/SubPartRole.kt`

```kotlin
enum class SubPartRole {
    DOER,
    REVIEWER;

    companion object {
        fun fromIndex(subPartIndex: Int): SubPartRole = when (subPartIndex) {
            0 -> DOER
            1 -> REVIEWER
            else -> throw IllegalArgumentException(
                "Invalid subPartIndex=[$subPartIndex]. Expected 0 (DOER) or 1 (REVIEWER)."
            )
        }
    }
}
```

## Key Design Points

- `fromIndex()` is the SINGLE source of truth for position-to-role mapping (spec explicitly states this)
- Future roles (e.g. FIXER) are additive enum variants — door propped open
- Exhaustive `when` — no `else` branch on the enum itself; `fromIndex` uses explicit index matching
- Role is derived on-the-fly, never stored — `SessionEntry` stores `subPartIndex`, role derived via `SubPartRole.fromIndex(subPartIndex)`

## Why Explicit Enum (from spec)
1. Self-documentation — role is a named concept
2. Evolvability — future roles are additive enum variants, not index conventions

## Tests (BDD/DescribeSpec)

- GIVEN index 0 WHEN fromIndex THEN returns DOER
- GIVEN index 1 WHEN fromIndex THEN returns REVIEWER
- GIVEN index 2 WHEN fromIndex THEN throws IllegalArgumentException
- GIVEN index -1 WHEN fromIndex THEN throws IllegalArgumentException
- Data-driven: all valid indices map correctly

## Package
`com.glassthought.shepherd.core.state`

## Acceptance Criteria
- SubPartRole enum with DOER and REVIEWER values
- fromIndex() companion function with clear error messages
- Unit tests cover all valid and invalid inputs
- `./test.sh` passes

