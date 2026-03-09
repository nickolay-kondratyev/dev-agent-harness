# Detailed Plan: Wingman Session ID Tracker

## 1. Problem Understanding

**Goal**: Create the `Wingman` abstraction and `ClaudeCodeWingman` implementation that resolves a Claude Code session ID from a GUID marker. The harness generates a GUID, sends it as the first message in a TMUX session, and then uses `Wingman` to discover which Claude Code session file contains that GUID -- thereby learning the session ID (needed for `--resume` on crashes).

**Key Constraints**:
- Pure file-scanning logic -- no external service dependencies
- Must be fully unit-testable with temp directories
- Fail-fast semantics: 0 matches = error, >1 matches = error, exactly 1 = success
- Follow existing codebase patterns (constructor injection, `IllegalStateException`, `Out` logging, `@AnchorPoint`)

**Assumptions**:
- Claude Code writes the GUID string verbatim into a JSONL file at `~/.claude/projects/<project-path>/<session-uuid>.jsonl`
- JSONL files are small enough to read entirely into memory for string matching
- The session ID is the filename without the `.jsonl` extension (a UUID like `77d5b7ea-cf04-453b-8867-162404763e18`)

---

## 2. High-Level Architecture

```
Wingman (interface)
  |
  +-- ClaudeCodeWingman (implementation)
        |
        +-- constructor(claudeProjectsDir: Path, outFactory: OutFactory)
        +-- resolveSessionId(guid: String): String
              1. Walk claudeProjectsDir recursively for *.jsonl files
              2. Read each file, check if content contains the GUID
              3. Collect matches
              4. Validate: exactly 1 match
              5. Return filename (minus extension) as session ID
```

**Data Flow**: `GUID string` --> file system scan --> matched file path --> filename extraction --> `session ID string`

**Interface Contract**: `suspend fun resolveSessionId(guid: String): String`
- Input: A GUID string (e.g., `"a1b2c3d4-e5f6-7890-abcd-ef1234567890"`)
- Output: A session ID string (the UUID filename of the matched JSONL file)
- Throws: `IllegalStateException` if zero or multiple matches

---

## 3. Implementation Phases

### Phase 1: Create `Wingman` Interface

**Goal**: Define the contract for session ID resolution.

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/Wingman.kt`

**Key Steps**:
1. Create package `com.glassthought.chainsaw.core.wingman`
2. Define `Wingman` interface with `suspend fun resolveSessionId(guid: String): String`
3. Add KDoc explaining the purpose, the GUID handshake flow, and a `ref.ap.XXX.E` cross-reference to the design ticket section
4. The `@AnchorPoint` annotation goes on the **implementation class**, not the interface (consistent with `TmuxCommunicator.kt` where interface is plain and impl has `@AnchorPoint`)

**Verification**: File compiles (no test needed for a pure interface).

---

### Phase 2: Create `ClaudeCodeWingman` Implementation

**Goal**: Implement JSONL file scanning and session ID extraction.

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingman.kt`

**Key Steps**:
1. Constructor takes `claudeProjectsDir: Path` and `outFactory: OutFactory` (constructor injection, no defaults -- caller decides the path)
2. Create `private val out = outFactory.getOutForClass(ClaudeCodeWingman::class)`
3. Implement `resolveSessionId`:
   a. Log the search start using `Out.info` with GUID and search directory as structured `Val` values
   b. Use `Files.walk(claudeProjectsDir)` to find all `*.jsonl` files
   c. Filter to files whose content `contains(guid)` -- use `file.readText()`
   d. Collect matching `Path` objects into a list
   e. When 0 matches: throw `IllegalStateException` with message including GUID and search directory
   f. When >1 matches: throw `IllegalStateException` with message including GUID and all matched filenames
   g. When exactly 1: extract filename without `.jsonl` extension, log success, return
4. Add `@AnchorPoint("ap.XXX.E")` annotation on the class (anchor point created during completion)
5. Wrap `Files.walk()` in a `.use {}` block since it returns a `Stream` that must be closed

**Design Decisions**:
- **`Path` type** for `claudeProjectsDir` (modern NIO API, consistent with `java.nio.file.Files.walk`)
- **`IllegalStateException`** for errors (matches existing pattern in `GLMHighestTierApi`)
- **Plain `String.contains()`** for GUID matching (KISS -- JSONL files are small, no parsing needed)
- **`Dispatchers.IO` wrapping** for `Files.walk` and file reads since they are blocking I/O. Use `withContext(Dispatchers.IO) { ... }` consistent with `GLMHighestTierApi` pattern
- **`ValType` choices**: Use `ValType.STRING_USER_AGNOSTIC` for GUIDs and session IDs, `ValType.FILE_PATH` for directory paths (if available, otherwise `STRING_USER_AGNOSTIC`)

**Verification**: Compiles, tests cover all paths (see Phase 3).

---

### Phase 3: Create Unit Tests

**Goal**: Thorough coverage of all code paths with BDD-style tests.

**File**: `app/src/test/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingmanTest.kt`

**Key Steps**:
1. Class extends `AsgardDescribeSpec`
2. Use `kotlin.io.path.createTempDirectory()` for isolated test directories
3. Create helper function to set up temp directory structures with JSONL files
4. Clean up temp dirs in `afterEach` or use unique temp dirs per test group

**Test Structure (BDD)**:

```
describe("GIVEN a ClaudeCodeWingman with a temp projects directory") {

    describe("AND a single JSONL file containing the target GUID") {
        describe("WHEN resolveSessionId is called with that GUID") {
            it("THEN returns the session ID extracted from the filename") { }
        }
    }

    describe("AND no JSONL files contain the target GUID") {
        describe("WHEN resolveSessionId is called") {
            it("THEN throws IllegalStateException") { }
            it("THEN the exception message contains the GUID") { }
        }
    }

    describe("AND multiple JSONL files contain the same GUID") {
        describe("WHEN resolveSessionId is called") {
            it("THEN throws IllegalStateException") { }
            it("THEN the exception message indicates ambiguous match") { }
        }
    }

    describe("AND the matching JSONL file is in a nested subdirectory") {
        describe("WHEN resolveSessionId is called") {
            it("THEN finds the GUID in the nested file and returns the session ID") { }
        }
    }

    describe("AND the directory contains non-JSONL files with the GUID") {
        describe("WHEN resolveSessionId is called") {
            it("THEN ignores non-JSONL files and throws no-match exception") { }
        }
    }

    describe("AND the projects directory is empty") {
        describe("WHEN resolveSessionId is called") {
            it("THEN throws IllegalStateException") { }
        }
    }
}
```

**Test Data Setup Pattern**:
- Create a helper that creates temp directory with specified structure:
  - `createTempProjectDir()` returns a `Path`
  - `writeJsonlFile(dir: Path, sessionId: String, content: String)` creates `<sessionId>.jsonl` with content
- GUID values: use fixed UUIDs for predictability (e.g., `"test-guid-abc123"`)
- Session IDs in filenames: use UUID-like strings (e.g., `"77d5b7ea-cf04-453b-8867-162404763e18"`)

**Verification**: All tests pass via `./gradlew :app:test`.

---

### Phase 4: Create Anchor Point and Cross-References

**Goal**: Link the new component into the project's anchor point system.

**Key Steps**:
1. Run `anchor_point.create` to generate a new anchor point identifier
2. Add the generated `ap.XXX.E` to the `@AnchorPoint` annotation on `ClaudeCodeWingman`
3. Add `ref.ap.XXX.E` in the KDoc of the `Wingman` interface (referencing the design section)
4. In `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`, add the `ap.XXX.E` immediately below the `## Session ID Tracking -- Wingman` heading (line 207)

**Verification**: `anchor_point.find_anchor_point_and_references` confirms the AP and its references are discoverable.

---

### Phase 5: Final Verification

**Goal**: Ensure nothing is broken and all acceptance criteria are met.

**Key Steps**:
1. Run `./gradlew :app:build` (redirect output to `.tmp/`) -- must pass cleanly
2. Run `./gradlew :app:test` -- all existing + new tests pass
3. Verify anchor point cross-references are correct
4. Verify the ticket completion criteria are all satisfied

---

## 4. Technical Considerations

### File I/O and Coroutines
The `resolveSessionId` method is `suspend` but performs blocking file I/O (`Files.walk`, `readText`). Wrap all blocking I/O in `withContext(Dispatchers.IO)` following the pattern established in `GLMHighestTierApi.kt`.

### Stream Resource Management
`Files.walk()` returns a `Stream<Path>` that holds an open directory handle. MUST use `.use {}` (or the Kotlin equivalent for Java streams) to ensure it is closed even on exceptions.

### Error Message Quality
Exception messages must be actionable. Include:
- The GUID being searched for
- The directory being searched
- For ambiguous matches: list all matched filenames (so the user can diagnose)

### Performance
Not a concern for V1. The `~/.claude/projects` directory will contain a modest number of JSONL files. Reading each file fully is acceptable. If this ever becomes a bottleneck, the interface allows swapping to a more efficient implementation.

---

## 5. Testing Strategy

### Unit Tests (Phase 3)
- **Happy path**: Single match in flat directory, single match in nested directory
- **Error paths**: No match (empty dir, files without GUID, non-JSONL files only), multiple matches
- **Edge case**: File extension filtering (only `*.jsonl`, not `.json` or `.txt`)

### What NOT to Test
- Real `~/.claude/projects` directory (that would be an integration test requiring Claude Code to be running)
- Internal implementation details like logging calls

### Test Isolation
Each test group creates its own temp directory. No shared mutable state between test groups.

---

## 6. Files Summary

### New Files
| File | Purpose |
|------|---------|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/Wingman.kt` | Interface: `suspend fun resolveSessionId(guid: String): String` |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingman.kt` | Implementation: scans JSONL files for GUID, extracts session ID from filename |
| `app/src/test/kotlin/com/glassthought/chainsaw/core/wingman/ClaudeCodeWingmanTest.kt` | BDD unit tests for all code paths |

### Modified Files
| File | Change |
|------|--------|
| `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md` | Add `ap.XXX.E` below `## Session ID Tracking -- Wingman` heading |

### NOT Modified
| File | Reason |
|------|--------|
| `app/build.gradle.kts` | No new dependencies needed |
| Any existing source files | This is a new, self-contained component |

---

## 7. Acceptance Criteria

- [ ] `Wingman` interface exists at `com.glassthought.chainsaw.core.wingman.Wingman`
- [ ] `ClaudeCodeWingman` implements `Wingman` at `com.glassthought.chainsaw.core.wingman.ClaudeCodeWingman`
- [ ] `ClaudeCodeWingman` constructor takes `claudeProjectsDir: Path` and `outFactory: OutFactory`
- [ ] Recursively scans for `*.jsonl` files under the provided directory
- [ ] Returns session ID (filename without `.jsonl` extension) on exactly 1 match
- [ ] Throws `IllegalStateException` with descriptive message (including GUID) on 0 matches
- [ ] Throws `IllegalStateException` with descriptive message (including GUID, matched filenames) on >1 matches
- [ ] Blocking file I/O wrapped in `withContext(Dispatchers.IO)`
- [ ] `Files.walk()` stream properly closed via `.use {}`
- [ ] Structured logging with `Out` for search start and resolved session ID
- [ ] `@AnchorPoint` annotation on `ClaudeCodeWingman`
- [ ] `ref.ap.XXX.E` in `Wingman` interface KDoc
- [ ] `ap.XXX.E` added to design ticket below `## Session ID Tracking -- Wingman`
- [ ] All new unit tests pass
- [ ] All existing tests still pass
- [ ] `./gradlew :app:build` succeeds

## 8. Implementation Order (for the implementing agent)

1. Create `Wingman.kt` (interface)
2. Create `ClaudeCodeWingman.kt` (implementation)
3. Create `ClaudeCodeWingmanTest.kt` (tests)
4. Run `./gradlew :app:test` to verify all tests pass (redirect to `.tmp/`)
5. Run `anchor_point.create` to get a new AP
6. Add AP annotation and cross-references
7. Run `./gradlew :app:build` for final verification
8. Close ticket, compress change log
