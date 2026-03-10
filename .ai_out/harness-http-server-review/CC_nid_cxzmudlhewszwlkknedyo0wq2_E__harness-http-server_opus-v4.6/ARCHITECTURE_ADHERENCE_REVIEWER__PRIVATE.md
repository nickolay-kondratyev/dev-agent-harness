# Architecture Adherence Review: PRIVATE NOTES

## Overall assessment

This is clean, correct foundational work. The HTTP skeleton is well-structured and the
infrastructure concerns (port binding, file management, lifecycle, logging) are all done right.
The only real risk is that the stub handlers are invisible from the outside — a caller integrating
this server will not discover they are no-ops until they try to use them. That is the one thing
worth pushing back on before merging if handlers won't be wired in the same PR.

## What was checked

1. Endpoint roster against CLAUDE.md V1 spec — all four present.
2. Port-0 binding and port-file path — exact match with spec.
3. `AsgardCloseable` lifecycle — correctly used, port file cleaned up on close.
4. Constructor injection — verified, no singletons or static wiring.
5. Logging — `Out/OutFactory` used correctly with `Val`/`ValType`.
6. `AgentRequest` interface — `branch` field on every type, Jackson-deserializable.
7. Test structure — BDD style, one assert per `it`, correct `AsgardDescribeSpec` base.
8. `Initializer.kt` — `HarnessServer` is NOT yet wired into `AppDependencies`. This is expected
   for incremental delivery but confirms the stub handlers finding is accurate.
9. `build.gradle.kts` — Ktor 3.1.1 added correctly; OkHttp added to test classpath for
   `KtorHarnessServerTest` real-server tests (not mockwebserver).

## Risk rating

- Stub handlers: MEDIUM risk (silent contract violation for /agent/question)
- PortFileManager concreteness: LOW risk
- Version catalog: LOW risk
