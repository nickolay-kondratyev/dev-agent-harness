# Logical Review: Role Catalog Loader

**Branch**: `CC_nid_pk1sdnmxo777oy9qz4hl7l06g_E__role-catalog-loader_opus-v4.6`
**Verdict**: READY (with minor issues noted)

---

## Summary

Implements `RoleCatalogLoader` — an interface + `RoleCatalogLoaderImpl` that auto-discovers agent roles by scanning a directory for `.md` files, parsing their YAML frontmatter for `description` (required) and `description_long` (optional), and deriving the role name from the filename. The implementation mirrors the existing `TicketParser` pattern, reuses `YamlFrontmatterParser`, and comes with 14 BDD tests covering the main scenarios. All tests pass. Sanity check passes.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. `filePath` documented as "Absolute path" but absoluteness is not guaranteed

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/rolecatalog/RoleDefinition.kt`, line 14

The `@param filePath` KDoc says "Absolute path to the source `.md` file." but the implementation never enforces this. The paths returned by `Files.walk()` inherit the absoluteness of the `dir` argument passed to `load()`. If a caller passes a relative `Path`, `filePath` values in the returned `RoleDefinition` instances will also be relative.

In production the `dir` will come from `$CHAINSAW_AGENTS_DIR` which is expected to be absolute, but the interface does not enforce this contract. The doc creates a false guarantee.

Fix options:
- Remove the word "Absolute" from the doc (weakest, but honest).
- Resolve the path to absolute at the top of `load()`: `val dir = dir.toAbsolutePath()`.

---

### 2. N separate `withContext(Dispatchers.IO)` calls inside the map loop

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/rolecatalog/RoleCatalogLoader.kt`, lines 73–89

```kotlin
val roles = mdFiles.map { file ->
    val content = withContext(Dispatchers.IO) { file.readText() }  // <-- separate dispatch per file
    ...
}
```

The directory walk is done inside a single `withContext(IO)`, but each file read triggers its own context switch back to IO. For N files this creates N coroutine context switches instead of 1. The correctness is fine — each switch lands on the IO thread pool — but the pattern is inconsistent and unnecessarily chatty.

A single `withContext(IO)` block that covers both the walk and all reads would be cleaner and more consistent with how the walk itself is structured. This matters more as the catalog grows (which is unlikely to be huge, but it's still a latency artifact for no gain).

---

## Suggestions

### S1. `ValType.STRING_USER_AGNOSTIC` for a count — use `ValType.COUNT` instead

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/rolecatalog/RoleCatalogLoader.kt`, line 93

```kotlin
out.info(
    "role_catalog_loaded",
    Val(roles.size.toString(), ValType.STRING_USER_AGNOSTIC),  // <-- roles.size is a count
)
```

`ValType.COUNT` exists and is semantically correct for an integer count. `STRING_USER_AGNOSTIC` is documented as "a bit of lazy value, and should not be used for important data." The CLAUDE.md standards explicitly state: "ValType must be semantically specific to the value being logged."

Note: The pre-existing `TicketParserImpl` uses `STRING_USER_AGNOSTIC` for `id` and `title` (strings, not counts), so this is a slightly different situation. Ticket strings arguably lack a more specific type. A count of roles has an exact type: `COUNT`. Worth fixing here even if the TicketParser usage needs a separate look.

---

### S2. Test: `forEach` with multiple assertions inside single `it` block

**File**: `app/src/test/kotlin/com/glassthought/chainsaw/core/rolecatalog/RoleCatalogLoaderTest.kt`, lines 66–71

```kotlin
it("THEN each role has a filePath ending with its filename") {
    val roles = loader.load(dir)
    roles.forEach { role ->
        role.filePath.fileName.toString() shouldBe "${role.name}.md"  // assertion per role
    }
}
```

The `forEach` loop effectively performs one assertion per role, which violates the "one assert per `it`" standard from CLAUDE.md. If the first role's assertion fails, the second role's result is hidden. This is a testing standards issue, not a logic bug. Could be resolved with `assertSoftly` or by using a single aggregate check like `roles.all { it.filePath.fileName.toString() == "${it.name}.md" } shouldBe true`.

---

### S3. Missing test: role file with no frontmatter delimiters

There is no test for a `.md` file that lacks `---` delimiters entirely (not just a missing `description` field). `YamlFrontmatterParser.parse()` would throw `IllegalArgumentException` with a "Content does not start with YAML frontmatter delimiter" message — which is the right behavior, but it's untested at the `RoleCatalogLoader` level.

This is low priority since `YamlFrontmatterParser` has its own tests, but it would improve the catalog loader's behavior documentation.

---

## Observations

- The fail-fast pattern on missing `description` is correct and clearly documented.
- The `maxDepth=1` in `Files.walk()` correctly prevents recursion into subdirectories, even though subdirectory non-traversal is not tested.
- The `Files.walk().use { }` pattern correctly closes the stream to prevent resource leak — good.
- `YamlFrontmatterParser` is in the `ticket` package but is documented as "Reused by both the ticket parser and the role catalog loader." This cross-package reuse is acceptable but over time the parser may want to live in a more neutral package (e.g., `core.yaml` or `core.markdown`). Not urgent — call out as a future refactor if more parsers appear.
- The `require()` call checking `mdFiles.isNotEmpty()` runs outside `withContext(IO)`. This is correct — it's a pure in-memory check on already-loaded data, not an IO operation. No issue.
- The `IOException` from `Files.walk()` or `file.readText()` would propagate as an unchecked exception from the `load()` method. The interface KDoc only documents `IllegalArgumentException`. This is a minor documentation gap (not a blocking issue since Kotlin callers don't need checked exception declarations), but callers who write `catch (e: IllegalArgumentException)` assuming they'll catch all errors from this method will miss `IOException` from permission errors or filesystem failures.
