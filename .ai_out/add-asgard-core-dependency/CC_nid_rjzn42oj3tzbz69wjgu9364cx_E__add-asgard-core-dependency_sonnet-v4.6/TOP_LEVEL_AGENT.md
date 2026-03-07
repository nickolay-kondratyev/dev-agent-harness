# TOP_LEVEL_AGENT Tracking

## Ticket
nid_rjzn42oj3tzbz69wjgu9364cx_E — Add asgard core dependency

## Phases

- [x] EXPLORATION
- [ ] IMPLEMENTATION_WITH_SELF_PLAN (in progress)
- [ ] IMPLEMENTATION_REVIEW
- [ ] IMPLEMENTATION_ITERATION (if needed)
- [ ] Ticket close + change log

## Key Findings from Exploration

### THORG_ROOT Status
- `THORG_ROOT=/Users/nkondrat/vintrin-env/submodules/thorg-root` — IS set and path EXISTS on this system
- Our submodule is at: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness/submodules/thorg-root`

### Gradle Versions
- Root project: **Gradle 9.2.1**
- kotlin-mp submodule: **Gradle 8.12** (but composite builds use parent's Gradle version)

### Implementation Strategy Decided
Create `asgard-composite/` directory in project root with a settings.gradle.kts that:
1. Uses THORG_ROOT for `build-logic` and `asgardIncludedBuild` and version catalog
2. Points module source to our submodule path for `asgardCoreShared`, `asgardCoreNodeJS`, `asgardCore`
3. Root settings.gradle.kts includes this composite build with dependency substitution

### Main API to Use
- `ProcessRunner.standard(outFactory)` from `com.asgard.core.processRunner.ProcessRunner`
- `SimpleConsoleOutFactory.standard()` from `com.asgard.core.out.impl.console.SimpleConsoleOutFactory`
- `runBlocking { runner.runProcess("echo", "Hello from AsgardCore ProcessRunner!") }` in main()
- Need `kotlinx-coroutines-core` dependency for `runBlocking`

### Risk: Gradle Version Mismatch
If Gradle 9.2.1 is incompatible with kotlin-mp 8.12 files, fallback is:
publish asgardCore to local maven using submodule's gradlew (8.12), then depend via Maven coordinates.
