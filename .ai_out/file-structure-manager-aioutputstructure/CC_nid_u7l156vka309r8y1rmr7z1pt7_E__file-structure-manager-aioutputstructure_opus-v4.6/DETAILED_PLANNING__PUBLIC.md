# Implementation Plan: AiOutputStructure

## 1. Problem Understanding

**Goal**: Create an `AiOutputStructure` class that encapsulates all path resolution and directory creation logic for the `.ai_out/${branch}/` directory tree used by the Chainsaw harness.

**Key Constraints**:
- Pure Kotlin stdlib + `java.nio.file` -- no new dependencies
- Constructor takes repo root `Path`, fail-fast if it does not exist
- Path resolution methods are pure (no I/O) -- they just compute paths
- Only `ensureStructure` performs I/O (directory creation)
- Follows existing project conventions: constructor injection, `Out`/`OutFactory` logging, BDD tests with `AsgardDescribeSpec`

**Assumptions**:
- The `branch` parameter is the raw git branch name string (callers handle slugification separately)
- The `part` parameter is the part identifier string (e.g., `"part_1"`, `"part_2"`)
- The `role` parameter is the role name string (e.g., `"PLANNER"`, `"IMPLEMENTOR"`)
- No interface is needed yet (YAGNI) -- this is a concrete utility class. If DIP is needed later, extract then.

---

## 2. High-Level Architecture

### Component Overview

```
AiOutputStructure (single class)
  - constructor(repoRoot: Path)         // fail-fast validation
  - pure path resolution methods        // no I/O, just Path arithmetic
  - ensureStructure(branch, parts)      // I/O: creates directory tree
```

**No interface needed**: This is a deterministic utility class with no external dependencies beyond the filesystem (only in `ensureStructure`). An interface would be over-engineering at this stage. The class takes no `OutFactory` -- all methods are either pure path computation or trivial `Files.createDirectories` calls with no logging need.

### File Structure to Create

```
app/src/main/kotlin/com/glassthought/chainsaw/core/filestructure/
  AiOutputStructure.kt

app/src/test/kotlin/com/glassthought/chainsaw/core/filestructure/
  AiOutputStructureTest.kt
```

### Data Flow

```
Caller (e.g., ContextProvider, workflow executor)
  |
  v
AiOutputStructure.harnessPrivateDir("my-branch")
  |
  v
Returns: repoRoot.resolve(".ai_out/my-branch/harness_private")
```

---

## 3. Implementation Phases

### Phase 1: Create AiOutputStructure Class

**Goal**: Implement the class with all path resolution methods and `ensureStructure`.

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/filestructure/AiOutputStructure.kt`

**Key Steps**:

1. Create the package directory `com.glassthought.chainsaw.core.filestructure`
2. Define the class with a single constructor parameter `repoRoot: Path`
3. In the `init` block, validate `repoRoot` exists (`Files.exists`) and throw `IllegalArgumentException` if not
4. Define named constants as a companion object for the directory/file names:
   - `AI_OUT_DIR = ".ai_out"`
   - `HARNESS_PRIVATE_DIR = "harness_private"`
   - `SHARED_DIR = "shared"`
   - `PLAN_DIR = "plan"`
   - `PLANNING_DIR = "planning"`
   - `PHASES_DIR = "phases"`
   - `SESSION_IDS_DIR = "session_ids"`
   - `PUBLIC_MD = "PUBLIC.md"`
   - `PRIVATE_MD = "PRIVATE.md"`
   - `SHARED_CONTEXT_MD = "SHARED_CONTEXT.md"`
   - `LOCATIONS_FILE = "LOCATIONS_OF_PUBLIC_INFO_FROM_OTHER_AGENTS.txt"`
5. Add a private helper `branchRoot(branch: String): Path` that returns `repoRoot.resolve(AI_OUT_DIR).resolve(branch)`
6. Implement each path resolution method (all pure, no I/O):

| Method | Returns |
|--------|---------|
| `harnessPrivateDir(branch)` | `branchRoot(branch) / "harness_private"` |
| `sharedDir(branch)` | `branchRoot(branch) / "shared"` |
| `planDir(branch)` | `sharedDir(branch) / "plan"` |
| `planningRoleDir(branch, role)` | `branchRoot(branch) / "planning" / role` |
| `phaseRoleDir(branch, part, role)` | `branchRoot(branch) / "phases" / part / role` |
| `sessionIdsDir(branch, part, role)` | `phaseRoleDir(branch, part, role) / "session_ids"` |
| `publicMd(branch, part, role)` | `phaseRoleDir(branch, part, role) / "PUBLIC.md"` |
| `privateMd(branch, part, role)` | `phaseRoleDir(branch, part, role) / "PRIVATE.md"` |
| `sharedContextMd(branch)` | `sharedDir(branch) / "SHARED_CONTEXT.md"` |
| `locationsFile(branch)` | `sharedDir(branch) / "LOCATIONS_OF_PUBLIC_INFO_FROM_OTHER_AGENTS.txt"` |

7. Implement `ensureStructure(branch: String, parts: List<Part>)` where `Part` is a data class containing the part name and list of role names:

```kotlin
data class Part(val name: String, val roles: List<String>)
```

`ensureStructure` creates all directories using `Files.createDirectories` (idempotent by design):
- `harnessPrivateDir(branch)`
- `sharedDir(branch)`
- `planDir(branch)`
- For each part: for each role: `phaseRoleDir(branch, part.name, role)` and `sessionIdsDir(branch, part.name, role)`

Note: `Files.createDirectories` already creates parent directories, so calling it on the deepest path is sufficient. No need to separately create intermediate dirs.

8. Add KDoc to the class referencing the design ticket anchor point: `ref.ap.XXX.E` (anchor point to be created at implementation time)

**Design Decisions**:
- **`Part` data class**: Rather than taking `List<String>` for parts and separately needing roles, encapsulate the part-name + roles relationship in a small data class. This is placed in the same file as `AiOutputStructure` since it is a simple value type tightly coupled to this API.
- **No `OutFactory`**: The path resolution methods are pure, and `Files.createDirectories` is a simple stdlib call. Logging adds no value here. The caller (e.g., workflow executor) logs at a higher level.
- **No `suspend`**: `Files.createDirectories` is a fast local I/O call. No need for coroutine overhead.

### Phase 2: Create Unit Tests

**Goal**: Comprehensive BDD tests using temp directories.

**File**: `app/src/test/kotlin/com/glassthought/chainsaw/core/filestructure/AiOutputStructureTest.kt`

**Key Steps**:

1. Create the test class extending `AsgardDescribeSpec`
2. Use Kotest's `tempdir()` for test isolation (creates a temp dir per spec, cleaned up automatically)
3. Structure tests in BDD format with `describe`/`it` blocks
4. One assertion per `it` block

**Test Cases**:

```
describe("GIVEN AiOutputStructure") {

  describe("WHEN constructed with non-existent repo root") {
    it("THEN throws IllegalArgumentException") { ... }
  }

  describe("WHEN constructed with valid repo root") {
    it("THEN does not throw") { ... }
  }

  describe("AND branch is 'feature__my-task__try-1'") {

    describe("WHEN harnessPrivateDir is called") {
      it("THEN path ends with .ai_out/feature__my-task__try-1/harness_private") { ... }
      it("THEN path starts with repo root") { ... }
    }

    describe("WHEN sharedDir is called") {
      it("THEN path ends with .ai_out/feature__my-task__try-1/shared") { ... }
    }

    describe("WHEN planDir is called") {
      it("THEN path ends with .ai_out/feature__my-task__try-1/shared/plan") { ... }
    }

    describe("WHEN sharedContextMd is called") {
      it("THEN path ends with .ai_out/feature__my-task__try-1/shared/SHARED_CONTEXT.md") { ... }
    }

    describe("WHEN locationsFile is called") {
      it("THEN path ends with .../shared/LOCATIONS_OF_PUBLIC_INFO_FROM_OTHER_AGENTS.txt") { ... }
    }

    describe("AND role is 'PLANNER'") {
      describe("WHEN planningRoleDir is called") {
        it("THEN path ends with .ai_out/feature__my-task__try-1/planning/PLANNER") { ... }
      }
    }

    describe("AND part is 'part_1' AND role is 'IMPLEMENTOR'") {
      describe("WHEN phaseRoleDir is called") {
        it("THEN path ends with .../phases/part_1/IMPLEMENTOR") { ... }
      }

      describe("WHEN sessionIdsDir is called") {
        it("THEN path ends with .../phases/part_1/IMPLEMENTOR/session_ids") { ... }
      }

      describe("WHEN publicMd is called") {
        it("THEN path ends with .../phases/part_1/IMPLEMENTOR/PUBLIC.md") { ... }
      }

      describe("WHEN privateMd is called") {
        it("THEN path ends with .../phases/part_1/IMPLEMENTOR/PRIVATE.md") { ... }
      }
    }
  }
}

describe("GIVEN AiOutputStructure with valid repo root") {

  describe("WHEN ensureStructure is called with branch and parts") {
    // Setup: create AiOutputStructure with tempdir, call ensureStructure

    it("THEN harness_private directory exists") { ... }
    it("THEN shared directory exists") { ... }
    it("THEN plan directory exists") { ... }
    it("THEN phase role directory exists for each part/role") { ... }
    it("THEN session_ids directory exists for each part/role") { ... }
  }

  describe("WHEN ensureStructure is called twice") {
    it("THEN does not throw (idempotent)") { ... }
  }

  describe("WHEN ensureStructure is called with empty parts list") {
    it("THEN shared directories still exist") { ... }
    it("THEN no phases directories are created") { ... }
  }
}
```

**Test Fixture Pattern** (following the GLMHighestTierApiTest pattern):

```kotlin
data class TestFixture(
    val repoRoot: Path,
    val structure: AiOutputStructure,
)
```

Create a helper method that sets up a `tempdir()` and constructs `AiOutputStructure` with it. Use it across test groups.

**Assertion Style**: Use `shouldEndWith` or `toString().endsWith(...)` / `shouldBe` for path comparison. Kotest's `shouldBe` works well with `Path`. For directory existence, use `Files.isDirectory(path) shouldBe true`.

---

## 4. Technical Considerations

### Path Resolution
- All methods use `Path.resolve()` which handles OS-specific separators
- No string concatenation for paths -- always use `Path` API
- Methods return `Path` (not `String`) -- caller converts if needed

### Fail-Fast Constructor
- Check `Files.exists(repoRoot)` in `init` block
- Throw `IllegalArgumentException` with a clear message including the actual path value
- This is the ONLY I/O in the class besides `ensureStructure`

### Idempotency of ensureStructure
- `Files.createDirectories` is inherently idempotent (no-op if dir exists, creates parents automatically)
- No need for explicit existence checks before creation

### Thread Safety
- Path resolution methods are pure and stateless -- inherently thread-safe
- `ensureStructure` uses `Files.createDirectories` which is thread-safe per JDK spec

### Edge Cases to Consider
- Branch names with special characters (slashes, dots) -- `Path.resolve` handles these naturally
- Empty parts list -- `ensureStructure` should still create shared/harness_private dirs
- Role names with spaces or special chars -- works via `Path.resolve`, no special handling needed

---

## 5. Testing Strategy

### What Gets Tested
- All 10 path resolution methods return correct paths relative to repo root
- Constructor fails on non-existent path
- Constructor succeeds on existing path
- `ensureStructure` creates all expected directories
- `ensureStructure` is idempotent (calling twice is safe)
- `ensureStructure` with empty parts list still creates base dirs

### What Does NOT Need Testing
- `Files.createDirectories` itself (JDK responsibility)
- OS-specific path separator behavior (JDK responsibility)

### Verification Command
```bash
export THORG_ROOT=$PWD/submodules/thorg-root
mkdir -p .tmp/; ./gradlew :app:test --tests "com.glassthought.chainsaw.core.filestructure.AiOutputStructureTest" > .tmp/test-output.txt 2>&1
```

---

## 6. Acceptance Criteria

1. `AiOutputStructure` class exists at `app/src/main/kotlin/com/glassthought/chainsaw/core/filestructure/AiOutputStructure.kt`
2. All 10 path resolution methods are implemented and return correct paths
3. `ensureStructure` creates the full directory tree idempotently
4. Constructor throws `IllegalArgumentException` for non-existent repo root
5. All unit tests pass: `./gradlew :app:test --tests "com.glassthought.chainsaw.core.filestructure.AiOutputStructureTest"`
6. Full build passes: `./gradlew :app:build`
7. No modifications to `app/build.gradle.kts`
8. Anchor point created and cross-referenced between design ticket and KDoc
9. `Part` data class is co-located with `AiOutputStructure`

---

## 7. Files to Create/Modify

| Action | File |
|--------|------|
| **CREATE** | `app/src/main/kotlin/com/glassthought/chainsaw/core/filestructure/AiOutputStructure.kt` |
| **CREATE** | `app/src/test/kotlin/com/glassthought/chainsaw/core/filestructure/AiOutputStructureTest.kt` |
| **MODIFY** | `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md` (add anchor point below `## File Structure` heading) |

No changes to `app/build.gradle.kts`.

---

## 8. Open Questions / Decisions

**Resolved during planning** (no open items):

1. **Interface or not?** -- No interface. Class is a deterministic utility with no polymorphic need. Extract if needed later (YAGNI).
2. **OutFactory needed?** -- No. Methods are pure path computation or trivial `createDirectories`. Logging adds no value at this level.
3. **Suspend needed?** -- No. Local filesystem I/O is fast. No coroutine overhead warranted.
4. **Part representation** -- A `Part(name: String, roles: List<String>)` data class co-located in the same file. Simple, explicit, avoids `Pair`.
