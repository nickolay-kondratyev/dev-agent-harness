# Exploration Summary

## Current Project State
- CLI Kotlin Agent Harness project, depends on AsgardCore via composite Gradle build
- AsgardCore integration: Out/OutFactory logging, ProcessRunner, AsgardCloseable.use{}, coroutines
- Minimal CLAUDE.md: generated from 3 auto_load files (core_description, claude_editing, deep_memory_pointers)
- Deep memory folder: only placeholder `0_example_deep_memory.md`
- One test file using kotlin.test

## ai_input/memory/auto_load/ (current)
- `1_core_description.md` - project description
- `2_claude_editing.md` - instructions about keeping docs updated
- `z_deep_memory_pointers.md` - deep memory references (only placeholder)

## Key Patterns from Thorg to Adopt (non-thorg-specific)
1. **Kotlin Principles**: SOLID, DRY, KISS, composition over inheritance, immutability, explicit code
2. **Logging**: Out/OutFactory with semantic ValTypes, never println, never log-and-throw
3. **DI**: Constructor injection only, no singletons
4. **Exceptions**: Extend AsgardBaseException hierarchy
5. **Testing**: One assert per test, BDD GIVEN/WHEN/THEN, fail hard never mask
6. **Resource mgmt**: Use `.use{}` pattern, no leaks
7. **Functional style**: Prefer functional collection ops over manual loops
8. **Coroutines**: Avoid runBlocking except at entry points/tests
9. **Detekt**: Static analysis integration

## What NOT to Include in CLAUDE.md
- Specific dependency versions
- Build commands (go stale)
- File path details easily discovered from code
- Thorg-specific patterns (thin client, S-VM-V, VSCode, etc.)
