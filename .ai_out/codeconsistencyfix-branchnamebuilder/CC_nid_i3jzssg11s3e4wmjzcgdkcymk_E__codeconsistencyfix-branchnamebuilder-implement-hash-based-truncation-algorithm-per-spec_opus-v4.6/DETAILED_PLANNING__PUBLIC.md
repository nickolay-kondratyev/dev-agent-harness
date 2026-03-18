# Detailed Implementation Plan: BranchNameBuilder Hash-Based Truncation

## Problem Understanding

`BranchNameBuilder.slugify()` currently uses character-level `.take(50)` truncation. The spec
(`doc/core/git.md`, ref.ap.BvNCIzjdHS2iAP4gAQZQf.E) requires word-boundary preservation with
a SHA-1 hash suffix for uniqueness when the slug exceeds `MAX_SLUG_LENGTH` (50).

**Current behavior**: `"implement-user-authentication-flow-with-oauth-and-session"` (57 chars)
gets truncated to `"implement-user-authentication-flow-with-oauth-and-s"` (50 chars) -- breaks
mid-word, and two similar titles sharing a prefix produce identical slugs.

**Required behavior**: Same input produces `"implement-user-authentication-flow-with-c33b35"` (46 chars)
-- word-boundary preserved, hash suffix ensures uniqueness.

### Assumptions

- No production callers of `BranchNameBuilder` exist yet (confirmed during exploration) -- changes are safe.
- `java.security.MessageDigest` is available (standard JDK) -- no new dependencies needed.
- The hash is computed from the **full slug** (after lowercase, hyphen-replace, collapse, trim) but
  **before** truncation.

---

## High-Level Architecture

No architectural changes. This is a **single-method algorithm change** within `BranchNameBuilder.slugify()`.
The `build()` method is unaffected -- it calls `slugify()` and the contract (returns a slug <= 50 chars) is preserved.

### Data Flow (unchanged)

```
TicketData.title --> slugify() --> slug (<=50 chars) --> build() formats "{id}__{slug}__try-{N}"
```

### Key Interface Contract (preserved)

- `slugify(title: String): String` -- returns a git-safe slug, always <= 50 chars, never ends with hyphen, deterministic.

---

## Implementation Phases

### Phase 1: Add Constants and SHA-1 Helper

**Goal**: Introduce the hash-suffix-related constants and a private helper for SHA-1 computation.

**File**: `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/BranchNameBuilder.kt`

**Key Steps**:

1. Add constant `HASH_SUFFIX_LENGTH = 7` (1 hyphen + 6 hex chars).
2. Add constant `MAX_WORD_BUDGET = MAX_SLUG_LENGTH - HASH_SUFFIX_LENGTH` (= 43).
3. Add a private helper method `sha1Hash6(input: String): String` that:
   - Uses `java.security.MessageDigest.getInstance("SHA-1")`
   - Computes digest of `input.toByteArray(Charsets.UTF_8)`
   - Converts first 3 bytes to 6 hex characters (lowercase)
   - Returns the 6-char hex string

**Verification**: Unit test the hash helper directly if made `internal`, or verify transitively via slugify tests.

### Phase 2: Implement Word-Boundary Truncation in slugify()

**Goal**: Replace `.take(MAX_SLUG_LENGTH)` with the spec algorithm.

**File**: `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/BranchNameBuilder.kt`

**Key Steps**:

1. Compute the full slug first (lowercase, replace non-alphanumeric, collapse hyphens, trim hyphens) -- this is the existing pipeline minus the `.take()` and final `.trimEnd('-')`.
2. After computing the full slug, apply the truncation decision:
   - If `fullSlug.length <= MAX_SLUG_LENGTH` -- return as-is (or `UNTITLED_FALLBACK` if empty).
   - If `fullSlug.length > MAX_SLUG_LENGTH` -- call a private `truncateWithHash(fullSlug)` method.
3. Implement `truncateWithHash(fullSlug: String): String`:
   - Split `fullSlug` by `-` to get words.
   - Accumulate whole words (re-joining with `-`) while the accumulated length <= `MAX_WORD_BUDGET` (43).
   - **Edge case: first word alone > MAX_WORD_BUDGET** -- take the first `MAX_WORD_BUDGET` characters of the slug (character-level fallback for this degenerate case). This ensures we always have content before the hash.
   - Compute `hash6 = sha1Hash6(fullSlug)`.
   - Return `"${wordPrefix}-${hash6}"`.
   - The result is guaranteed <= 50 chars because: word prefix <= 43 chars + `-` (1) + hash (6) = 50.
4. Remove the old `.take(MAX_SLUG_LENGTH).trimEnd('-')` from the pipeline.

**Algorithm Pseudocode** (for the word-accumulation logic):

```
fun truncateWithHash(fullSlug: String): String {
    val words = fullSlug.split("-")
    val prefix = buildWordPrefix(words, MAX_WORD_BUDGET)
    val hash6 = sha1Hash6(fullSlug)
    return "${prefix}-${hash6}"
}

fun buildWordPrefix(words: List<String>, budget: Int): String {
    // Edge case: first word alone exceeds budget
    if (words.first().length > budget) {
        return fullSlug.take(budget).trimEnd('-')
    }

    var result = words.first()
    for (word in words.drop(1)) {
        val candidate = "$result-$word"
        if (candidate.length > budget) break
        result = candidate
    }
    return result
}
```

**Important detail**: The `trimEnd('-')` after `.take(budget)` in the single-long-word fallback
is needed because the original slug could have hyphens at position 43 that would be exposed.
However, since hyphens were already collapsed and trimmed in the pipeline, this is defensive only.

**Verification**: All existing tests still pass (short slugs unchanged), new tests verify truncation behavior.

### Phase 3: Update and Add Tests

**Goal**: Ensure comprehensive test coverage for the new algorithm.

**File**: `app/src/test/kotlin/com/glassthought/shepherd/core/git/BranchNameBuilderTest.kt`

**Key Steps**:

#### 3a. Update Existing Tests That Change Behavior

1. **Test at line 42-49** ("GIVEN slugify WHEN called with a title exceeding 50 characters"):
   - Currently tests `"a".repeat(80)` and asserts length <= 50.
   - This single-character repeat has no hyphens, so it is one "word" of 80 chars.
   - The new algorithm's fallback will take 43 chars + hash suffix = 50 chars.
   - Update assertion to be more specific: `slug.length shouldBe 50` and verify it ends with a hash pattern.

2. **Test at line 51-59** ("WHEN called with a title that truncates to end with a hyphen"):
   - Title: `"a".repeat(49) + "-bbb"` = 53 chars after slugify (52 chars: 49 a's + hyphen + bbb).
   - This exceeds 50, so the new algorithm kicks in. Result will be word-prefix + hash, guaranteed not to end with hyphen.
   - The assertion `slug shouldNotEndWith "-"` remains valid but the test description may need updating to clarify it tests the hash-truncation path.

#### 3b. Add New Test Cases

Add a new `describe` block inside the existing `describe("GIVEN slugify")`:

1. **Short slug -- no truncation**:
   - Input: `"My Simple Feature"` -> slug = `"my-simple-feature"` (18 chars, well under 50)
   - Assert: result is `"my-simple-feature"` exactly, no hash appended.

2. **Exactly 50 chars -- no truncation**:
   - Construct a title that slugifies to exactly 50 chars.
   - Assert: result is the full slug, no hash suffix.

3. **51 chars -- triggers truncation**:
   - Construct a title that slugifies to exactly 51 chars (just over the limit).
   - Assert: result <= 50 chars, ends with 6 hex chars, contains hash suffix.

4. **Long multi-word title -- word boundary + hash (the spec example)**:
   - Input: `"implement user authentication flow with oauth and session"`
   - Full slug: `"implement-user-authentication-flow-with-oauth-and-session"` (57 chars)
   - Expected: `"implement-user-authentication-flow-with-c33b35"` (46 chars)
   - Assert exact match. This is the canonical spec example.

5. **Single long word -- character-level fallback with hash**:
   - Input: `"a".repeat(60)` (produces a 60-char slug with no hyphens)
   - Assert: result length == 50, starts with `"a".repeat(43)`, ends with `-` + 6 hex chars.

6. **Determinism -- same input always produces same output**:
   - Call `slugify()` twice with the same long input.
   - Assert: results are identical.

7. **Uniqueness -- similar long titles produce different slugs**:
   - Input A: `"implement user authentication flow with oauth and session management"`
   - Input B: `"implement user authentication flow with oauth and token refresh"`
   - Both share the prefix `"implement-user-authentication-flow-with"` (39 chars, fits in 43).
   - Assert: `slugify(A) != slugify(B)` (the hash suffixes differ because the full slugs differ).

8. **Result never ends with hyphen** (for truncated slugs):
   - Use a long input that triggers truncation.
   - Assert: `slug shouldNotEndWith "-"`.
   - (This is inherently guaranteed by the algorithm since the suffix is always 6 hex chars, but worth asserting explicitly.)

#### 3c. Update `build()` Test for Long Title

9. **Test at line 156-183** ("WHEN called with a long title and id='nid_abc123'"):
   - This test creates a long title and checks the slug portion is <= 50.
   - Update to also verify the slug contains a hash suffix (matches `Regex("-[a-f0-9]{6}$")`).

---

## Technical Considerations

### SHA-1 Hex Conversion

Use `java.security.MessageDigest` with standard byte-to-hex conversion. The implementation
should use a well-known pattern (e.g., `"%02x".format()` on each byte, or `joinToString`).
Take only the first 3 bytes (= 6 hex chars). No external dependency needed.

### Edge Cases to Handle

| Edge Case | Expected Behavior |
|---|---|
| Slug exactly 50 chars | No truncation, no hash -- use as-is |
| Slug 51 chars | Truncation kicks in, word boundary + hash |
| First word > 43 chars | Character-level fallback: take first 43 chars of slug + hash |
| All words empty after slugify | `UNTITLED_FALLBACK` ("untitled") -- same as current |
| Single word exactly 43 chars + more | Takes 43-char word, appends hash = 50 total |
| Words that sum to exactly 43 chars | Include all those words, append hash |

### Performance

SHA-1 computation is negligible. `MessageDigest` instance creation is cheap. No caching needed --
branch names are created at most once per `shepherd run`.

### Thread Safety

`BranchNameBuilder` is a stateless `object`. `MessageDigest.getInstance("SHA-1")` returns a
new instance each call, so no shared mutable state. Thread-safe by design.

---

## Testing Strategy

### Test Categories

1. **No-truncation path**: Short titles, exactly-50-char titles -- verify hash is NOT appended.
2. **Truncation path**: Long multi-word titles -- verify word-boundary preservation and hash suffix.
3. **Fallback path**: Single long word -- verify character-level truncation with hash.
4. **Determinism**: Same input -> same output across calls.
5. **Uniqueness**: Different long inputs sharing a prefix -> different outputs.
6. **Invariants**: Length <= 50, never ends with hyphen, always lowercase alphanumeric + hyphens.

### Verification Command

```bash
mkdir -p .tmp/; ./gradlew :app:test > .tmp/test-output.txt 2>&1
```

---

## Files Modified

| File | Change |
|---|---|
| `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/BranchNameBuilder.kt` | Add constants, SHA-1 helper, word-boundary truncation logic |
| `app/src/test/kotlin/com/glassthought/shepherd/core/git/BranchNameBuilderTest.kt` | Update existing tests, add new test cases |
| `doc/core/git.md` | Remove the implementation gap note (line 42) after implementation is complete |

---

## Open Questions / Decisions Needed

None -- the spec is fully specified and all edge cases have clear resolutions. The implementer
should proceed directly.

---

## Implementation Order Summary

1. Write the new/updated test cases first (they will fail -- TDD).
2. Add constants `HASH_SUFFIX_LENGTH` and `MAX_WORD_BUDGET` to `BranchNameBuilder`.
3. Add private `sha1Hash6()` helper.
4. Refactor `slugify()` to separate full-slug computation from truncation.
5. Implement `truncateWithHash()` with word-boundary logic.
6. Run tests, verify all pass.
7. Remove the implementation gap note from `doc/core/git.md` (line 42).
8. Run full `:app:test` to confirm no regressions.
