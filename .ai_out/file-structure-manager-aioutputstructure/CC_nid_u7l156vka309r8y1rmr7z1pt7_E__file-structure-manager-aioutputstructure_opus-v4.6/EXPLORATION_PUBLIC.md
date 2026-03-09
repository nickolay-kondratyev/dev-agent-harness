# Exploration Findings

## Package Structure
- New package: `com.glassthought.chainsaw.core.filestructure` (doesn't exist yet)
- Follows existing pattern: `core/tmux/`, `core/directLLMApi/`, `core/initializer/`

## File Structure Design (from design ticket lines 410-461)
```
.ai_out/${git_branch}/
├── harness_private/
│   ├── current_state.json
│   └── PRIVATE.md
├── shared/
│   ├── SHARED_CONTEXT.md
│   ├── LOCATIONS_OF_PUBLIC_INFO_FROM_OTHER_AGENTS.txt
│   └── plan/
│       ├── PLAN.md
│       └── plan.json
├── planning/
│   ├── PLANNER/   (PUBLIC.md, PRIVATE.md, session_ids/)
│   └── PLAN_REVIEWER/
├── phases/
│   ├── part_1/${ROLE}/ (PUBLIC.md, PRIVATE.md, session_ids/)
│   └── part_2/...
```

## Test Patterns
- Base class: `AsgardDescribeSpec` (from `com.asgard.testTools.describe_spec`)
- BDD style: `describe("GIVEN ...") { it("THEN ...") { } }`
- One assert per `it` block
- `outFactory` inherited from `AsgardDescribeSpec`
- Data class fixtures for test setup

## Build Config
- No changes needed to `app/build.gradle.kts` — pure Kotlin stdlib
- `java.nio.file.Path` from JDK, no extra deps

## DI Pattern
- Constructor injection: takes `OutFactory` + domain params
- `private val out = outFactory.getOutForClass(MyClass::class)`

## No Existing `.ai_out` or `java.nio.file` Usage in Codebase
- This is entirely new functionality

## Key Source Paths
- Main: `app/src/main/kotlin/com/glassthought/chainsaw/core/`
- Test: `app/src/test/kotlin/`
- Design ticket: `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`
