# TOP_LEVEL_AGENT Coordination

## Ticket
nid_wgr6tfn0labkt9py8h24ikzwq_E — Add Gradle IDEA plugin to exclude most of thorg-root submodule

## Phase Status
- [x] EXPLORATION (inline — sufficient context gathered)
- [x] CLARIFICATION (no ambiguities — proceeding)
- [ ] IMPLEMENTATION_WITH_SELF_PLAN
- [ ] IMPLEMENTATION_REVIEW
- [ ] IMPLEMENTATION_ITERATION (if needed)

## Key Decisions
- Apply `idea` plugin at root project level via new root `build.gradle.kts`
- Exclude all of `submodules/thorg-root` except the path to `source/libraries/kotlin-mp`
- Implementation approach: enumerate subdirs at each level, excluding non-relevant ones

## Files
- EXPLORATION_PUBLIC.md: full context
