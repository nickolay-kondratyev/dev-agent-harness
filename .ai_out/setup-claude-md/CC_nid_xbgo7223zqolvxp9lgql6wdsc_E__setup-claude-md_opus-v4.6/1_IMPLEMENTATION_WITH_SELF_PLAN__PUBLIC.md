# Implementation Complete: Setup ai_input Folder

## What was done

Set up the `ai_input` folder with best practices adapted from thorg for this CLI Kotlin Agent Harness project.

### Auto-load files created/updated (`ai_input/memory/auto_load/`)

1. **`1_core_description.md`** - Updated with richer project context: CLI Kotlin agent harness, asgardCore dependency, agent coordination, architecture principles.

2. **`2_claude_editing.md`** - Kept as-is (already good).

3. **`3_kotlin_standards.md`** - NEW. Kotlin development standards covering: constructor injection, Out/OutFactory logging rules, AsgardBaseException hierarchy, coroutines (avoid runBlocking), code style (composition, immutability, explicit, functional style, sealed when without else, compile-time checks).

4. **`4_testing_standards.md`** - NEW. Testing conventions: BDD GIVEN/WHEN/THEN via Kotest DescribeSpec, one assert per it block, fail hard never mask, no delay for synchronization, data-driven tests.

5. **`5_ticket_and_change_log_usage.md`** - NEW. Adapted from thorg's ticket/changelog tooling. Tags are project-specific: `harness`, `harness.workflow`, `harness.cli`, `asgardCore`, `agents`, `file-io`, `docs`. Removed all thorg-specific tags.

6. **`z_deep_memory_pointers.md`** - Updated to reference the 5 new deep memory files.

### Deep memory files created (`ai_input/memory/deep/`)

1. **`out_logging_patterns.md`** - Full Out logging reference: getting Out instances, log levels, Val/ValType usage, lazy lambda patterns, rules.

2. **`dont_log_and_throw.md`** - Anti-pattern documentation: never log and throw, let exceptions bubble up.

3. **`favor_functional_style.md`** - Prefer functional collection operations over manual loops.

4. **`in_tests__one_assert_per_test.md`** - One logical assertion per `it` block, GIVEN/WHEN/THEN structure.

5. **`in_tests__fail_hard_never_mask.md`** - Tests must fail explicitly, no silent fallbacks, no conditional skipping.

### Deleted
- `ai_input/memory/deep/0_example_deep_memory.md` - Placeholder removed.

### CLAUDE.md
- Regenerated via `CLAUDE.generate.sh` - now includes all 6 auto_load files.

## Decisions Made
- Tags for change_log/tk are project-specific (`harness`, `harness.workflow`, `harness.cli`, `asgardCore`, `agents`, `file-io`, `docs`), deliberately excluding all thorg-specific tags.
- Deep memory files use the `---\ndesc: "..."\n---` frontmatter format for consistency with thorg conventions and to support auto-generation of the pointer table.
- Kept CLAUDE.md content focused on principles and patterns (not build commands, not file paths, not dependency versions) per the "things NOT in code" philosophy.
