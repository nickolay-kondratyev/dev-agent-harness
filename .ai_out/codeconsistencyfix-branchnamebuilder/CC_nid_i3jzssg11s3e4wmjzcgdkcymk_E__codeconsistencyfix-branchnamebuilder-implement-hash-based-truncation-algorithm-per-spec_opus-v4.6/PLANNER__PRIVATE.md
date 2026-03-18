# Planner Private Context

## Key Findings

- **No production callers** of `BranchNameBuilder` exist -- only tests reference it. Safe to change behavior.
- **SHA-1 of spec example**: `echo -n "implement-user-authentication-flow-with-oauth-and-session" | sha1sum` starts with `c33b35`. The spec example uses `a1b2c3` as a placeholder, NOT an actual hash. Tests must use the real computed value.
- **`java.security.MessageDigest`** is standard JDK -- no dependency addition needed.
- **Word-budget**: MAX_SLUG_LENGTH(50) - HASH_SUFFIX_LENGTH(7) = 43 chars for word prefix.
- **Edge case identified**: If the first hyphen-delimited word alone exceeds 43 chars (e.g., a single 60-char word), need character-level fallback to take first 43 chars + hash suffix.

## Verification of Spec Example

Full slug: `"implement-user-authentication-flow-with-oauth-and-session"` = 57 chars
Words fitting in 43: `"implement-user-authentication-flow-with"` = 39 chars
Hash6: `c33b35`
Result: `"implement-user-authentication-flow-with-c33b35"` = 46 chars -- correct, <= 50.

## Existing Test Impact

- Tests at lines 42-49 (long title) and 51-59 (trailing hyphen) will change behavior under new algorithm.
- Tests at lines 19, 27, 33, 37, 62, 67, 73 are short-slug tests -- unaffected.
- `build()` test at line 156 needs the slug assertion updated to expect hash suffix.

## Decision: `sha1Hash6` Visibility

Made it a private method. If testing it directly is desired, it can be `internal` (Kotlin package-private equivalent). However, testing transitively through `slugify()` is sufficient and preferred.

## No Blocking Questions

Spec is unambiguous. All edge cases have deterministic resolutions.
