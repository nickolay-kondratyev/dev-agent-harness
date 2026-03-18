---
id: nid_i3jzssg11s3e4wmjzcgdkcymk_E
title: "CODE_CONSISTENCY_FIX: BranchNameBuilder — implement hash-based truncation algorithm per spec"
status: in_progress
deps: []
links: []
created_iso: 2026-03-18T16:06:22Z
status_updated_iso: 2026-03-18T16:44:03Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
---

## Problem

The `BranchNameBuilder.slugify()` truncation algorithm deviates from spec (doc/core/git.md — ref.ap.BvNCIzjdHS2iAP4gAQZQf.E).

**Current implementation**: simple character-level truncation at 50 chars (was 60, now fixed).

**Spec algorithm (when slug > MAX_SLUG_LENGTH=50)**:
1. Slugify the full title (lowercase, spaces→hyphens, strip non-alphanumeric except hyphens)
2. If slug length ≤ 50 → use as-is
3. If longer → take the first K whole hyphen-delimited words that fit within `MAX_SLUG_LENGTH - 7` (43 chars), then append `-{hash6}` where `{hash6}` is the first 6 hex chars of SHA-1 of the **full** slug

**Example from spec:**
`"implement-user-authentication-flow-with-oauth-and-session"` (57 chars)
→ words fitting in 43 chars: `"implement-user-authentication-flow-with"`
→ hash of full slug: `a1b2c3...`
→ result: `"implement-user-authentication-flow-with-a1b2c3"` (46 chars ≤ 50)

## Why It Matters

The hash suffix preserves **uniqueness** across tickets with similar title prefixes. Simple truncation can produce identical branch names for different tickets.

## Files
- `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/BranchNameBuilder.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/git/BranchNameBuilderTest.kt`

## SHA-1 Note
Use `java.security.MessageDigest` for SHA-1. No new dependency required.

## Acceptance Criteria

1. slugify() uses word-boundary preservation when truncating (not character-level cut)
2. When slug > 50 chars: takes first K whole words fitting in 43 chars, appends `-{hash6}`
3. hash6 = first 6 hex chars of SHA-1 of the full (untruncated) slug
4. Result is always ≤ 50 chars
5. Result never ends with a hyphen
6. Same ticket title always produces the same branch name (deterministic)
7. Tests cover: short title (no truncation), long title (word-boundary + hash), title where truncation point falls mid-word, uniqueness for similar long titles

