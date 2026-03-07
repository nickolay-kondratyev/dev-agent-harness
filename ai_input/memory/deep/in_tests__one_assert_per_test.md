---
desc: "Structure tests with separate `it` blocks for each assertion. Use describe to group. Self-documenting."
---

## Testing Structure: One Assert Per Test Block

### Principle
Structure tests with separate `it` blocks for each assertion. Use `describe` blocks to group related assertions and provide context, eliminating the need for inline comments.

### Pattern
```kotlin
describe("GIVEN some precondition") {
    describe("AND additional setup") {
        describe("WHEN Sanity-Check") {
            it("THEN be able to query item 1") {
                // single assertion
            }
            it("THEN be able to query item 2") {
                // single assertion
            }
        }

        describe("WHEN some action") {
            describe("AND some other action") {
                it("THEN do specific thing") {
                    // single assertion
                }
            }
        }
    }
}
```

### Guidelines
- Each `it` block should contain **ONE logical assertion**.
- Use `describe` blocks to express GIVEN/AND preconditions as nesting.
- The `it` description should clearly state what is being verified.
- No WHAT comments needed inside test blocks — the structure IS the documentation.
