## Testing Standards

### Framework & Style
- **BDD with GIVEN/WHEN/THEN** using Kotest `DescribeSpec`.
- Unit tests extend `AsgardDescribeSpec`.
- Use `describe` blocks for GIVEN/AND/WHEN structure; `it` blocks for THEN assertions.

### One Assert Per Test
- Each `it` block contains **one logical assertion**.
- The `it` description clearly states what is being verified.
- No inline WHAT comments needed — the nested describe/it structure IS the documentation.
- See deep memory: `in_tests__one_assert_per_test.md`.

### Fail Hard, Never Mask
- Tests must **fail explicitly** when dependencies, setup, or configuration are missing.
- **No silent fallbacks**, no conditional skipping of individual tests.
- Only entire test classes may be enabled/disabled based on environment — NOT individual tests.
- See deep memory: `in_tests__fail_hard_never_mask.md`.

### Synchronization
- **Do NOT use `delay`** for synchronization in tests. Use proper await mechanisms or polling.

### Data-Driven Tests
- Use data-driven tests to eliminate duplication when testing the same logic with multiple inputs.

### Naming
- Focused, descriptive test names that read naturally in the GIVEN/WHEN/THEN tree.
