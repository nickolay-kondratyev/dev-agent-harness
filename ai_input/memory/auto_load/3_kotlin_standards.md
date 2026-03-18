## Kotlin Development Standards

### Dependency Injection
- **Constructor injection only** — no DI framework, no singletons.
- Single instances wired at the top-level entry point.

### Logging
- Use `Out` / `OutFactory` for all logging (never `println`).
  - println is allowed to be used for user communication NOT logging.
  - Use `Val` with `ValTypeV2` for project specific types.
- Structured values via `Val(value, ValType.SPECIFIC_TYPE)` — never embed values in message strings.
- Use **lazy lambda** form for DEBUG/TRACE to avoid serialization overhead.
- Use **snake_case** for log message strings.
- `ValType` must be **semantically specific** to the value being logged.
- All `Out` methods are `suspend` functions.
- See deep memory: `out_logging_patterns.md` for full patterns.

### Exceptions
- Extend `AsgardBaseException` hierarchy for structured exceptions.
- **Do NOT log and throw** — let exceptions bubble up, log at the top-most layer only.
- See deep memory: `dont_log_and_throw.md`.

### Coroutines
- Avoid `runBlocking` — acceptable only at main entry points, tests, and framework callbacks.
- Use proper coroutine dispatchers via `DispatcherProvider`.
- Thread safety: use mutexes, not synchronized blocks.

### Code Style
- **Composition over inheritance** — always.
- Be classy and use interfaces.
  - Put interfaces as the same place as the default implementation.
  - Use naming that aligns with implementation or `Impl` as fallback naming (Doer/DoerImpl).
- **Favor immutability** — immutable data structures by default, pass values as parameters, return new values.
- **Be explicit** — no magic numbers, no `Pair`/`Triple`. Use descriptive `data class` instead.
- **No `@Deprecated`** — refactor directly, make clean breaks.
- **Favor functional style** — prefer `map`, `filter`, `zip`, `takeWhile` over manual loops with index tracking.
- **Resource management** — use `.use{}` pattern (AsgardCloseable). No resource leaks.
- **Sealed classes/enums in `when`** — no `else` branch; let compiler enforce exhaustiveness.
- **Favor compile-time checks** over runtime checks.
- **No free-floating functions** — favor cohesive classes; for stateless utilities, use a static class (companion object).

### Static Analysis (detekt)
- Detekt runs as part of `./test.sh` via `:app:test` task dependency.
- Baseline file at `detekt-baseline.xml` excludes pre-existing issues.
- **When modifying code in a file with baseline exceptions**: fix the issues and remove the corresponding entries from the baseline.
  - Our aim is to fix all detekt issues in long term.
- Run `./gradlew :app:detektBaseline` to regenerate baseline after fixes (only if needed).
- Config: `detekt-config.yml` — project-specific overrides on top of detekt defaults.
