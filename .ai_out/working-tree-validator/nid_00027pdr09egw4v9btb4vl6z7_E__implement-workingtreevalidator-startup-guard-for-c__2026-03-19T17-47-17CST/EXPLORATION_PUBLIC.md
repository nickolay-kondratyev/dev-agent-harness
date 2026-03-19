# Exploration: WorkingTreeValidator

## Spec (doc/core/git.md lines 10-37, ref.ap.QL051Wl21jmmYqTQTLglf.E)
- Run `git status --porcelain`, verify empty output
- If dirty: fail hard with error listing dirty files
- Error instructs user to commit/stash before `shepherd run`
- Called by TicketShepherdCreator before branch creation

## Pattern to Follow: GitBranchManager
- File: `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitBranchManager.kt`
- Interface + Impl in same file
- Companion `standard()` factory
- Constructor: `outFactory: OutFactory, processRunner: ProcessRunner, workingDir: Path? = null`
- Private `gitCommand()` helper for `-C <workingDir>` prefix
- Logging via `Out` from `outFactory.getOutForClass()`

## FakeProcessRunner for Tests
- Already exists at `app/src/test/kotlin/com/glassthought/shepherd/core/supporting/git/GitOperationFailureUseCaseImplTest.kt` (lines 52-76)
- Uses `onCommand(vararg args, result: Result<String>)` to configure responses
- Key joined by space for lookup
- This is `internal` to that file — will need to extract to shared location OR create own inline fake

## Test Pattern
- Extend `AsgardDescribeSpec`
- BDD: describe("GIVEN...") / describe("WHEN...") / it("THEN...")
- One assert per `it` block
- `shouldThrow<>` for exception testing, `shouldContain` for message assertions
- `outFactory` inherited from `AsgardDescribeSpec`

## Key Files
| File | Purpose |
|------|---------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitBranchManager.kt` | Pattern reference |
| `app/src/test/kotlin/com/glassthought/shepherd/core/supporting/git/GitOperationFailureUseCaseImplTest.kt` | Test pattern + FakeProcessRunner |
| `doc/core/git.md` | Spec |
