# Exploration: BranchNameBuilder Hash-Based Truncation

## Key Files
- **Implementation**: `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/BranchNameBuilder.kt`
- **Tests**: `app/src/test/kotlin/com/glassthought/shepherd/core/git/BranchNameBuilderTest.kt`
- **Spec**: `doc/core/git.md` (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E)

## Current Implementation
- `slugify()` does character-level `.take(MAX_SLUG_LENGTH)` truncation at 50 chars
- No word-boundary preservation, no hash suffix
- Constants: `MAX_SLUG_LENGTH=50`, `DELIMITER="__"`, `TRY_PREFIX="try-"`, `UNTITLED_FALLBACK="untitled"`

## Spec Algorithm (when slug > 50)
1. Slugify full title (lowercase, spaces→hyphens, strip non-alphanumeric except hyphens)
2. If ≤ 50 → use as-is
3. If > 50 → take first K whole hyphen-delimited words fitting in 43 chars (50-7), append `-{hash6}` (first 6 hex of SHA-1 of full slug)

## Gap: Character truncation vs word-boundary + hash → uniqueness broken for similar long titles

## Callers
- **No production callers yet** — only tests reference BranchNameBuilder
- `GitBranchManager` accepts any branchName string — not affected

## SHA-1
- Use `java.security.MessageDigest` — no new dependency needed
