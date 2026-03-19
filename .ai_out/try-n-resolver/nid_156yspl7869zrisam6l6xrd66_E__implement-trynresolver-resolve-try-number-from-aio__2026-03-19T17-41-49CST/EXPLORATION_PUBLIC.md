# Exploration Summary — TryNResolver

## Task
Implement `TryNResolver` interface + `TryNResolverImpl` that determines the next try-N number by scanning `.ai_out/` directories.

## Algorithm (from doc/core/git.md lines 79-92)
1. Start candidateN = 1
2. Build candidate branch name via `BranchNameBuilder.build(ticket, candidateN)`
3. Check: does `.ai_out/{candidate}/` directory exist?
4. If exists → increment candidateN, repeat
5. If not → return candidateN

## Key Files
- **BranchNameBuilder**: `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/BranchNameBuilder.kt`
  - Object singleton, `fun build(ticketData: TicketData, tryNumber: Int): String`
  - Format: `{ticketId}__{slug}__try-{N}`
- **AiOutputStructure**: `app/src/main/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructure.kt`
  - `AI_OUT_DIR = ".ai_out"` (private const)
  - Constructor: `(repoRoot: Path, branch: String)`, `branchRoot()` → `repoRoot.resolve(".ai_out").resolve(branch)`
- **TicketData**: `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/ticket/TicketData.kt`
  - `data class TicketData(val id: String, val title: String, val status: String?, val description: String, val additionalFields: Map<String, String> = emptyMap())`

## Design Decisions
- Place in `com.glassthought.shepherd.core.supporting.git` package (alongside BranchNameBuilder)
- Interface + Impl pattern per project conventions
- Constructor takes `repoRoot: Path` — uses `.ai_out/` constant directly (not AiOutputStructure dependency, since we just need path check)
- `suspend fun resolve(ticketData: TicketData): Int`

## Test Scenarios
1. No .ai_out/ dirs → returns 1
2. .ai_out/ for try-1 exists → returns 2
3. Gap detection: try-1 and try-3 exist → returns 2 (first gap)
4. Multiple consecutive tries exist → returns next

## Test Pattern
- Extend `AsgardDescribeSpec`, BDD with GIVEN/WHEN/THEN, one assert per `it` block
- Use temp directories for filesystem tests
- See `BranchNameBuilderTest.kt` for reference pattern
