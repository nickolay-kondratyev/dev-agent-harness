# Exploration: Ktor CIO HTTP Server Context

## Dependency Management
- Version catalog at `gradle/libs.versions.toml` — Kotlin 2.2.20, Java 21, OkHttpClient 4.12.0, Jackson 2.17.2
- All deps declared via `libs.` prefix — Ktor deps need catalog entries
- Ktor NOT yet added

## Code Architecture Patterns
- Interfaces: minimal, single-responsibility, `suspend` methods
- Implementations: descriptive names, constructor injection only
- Resource management: `AsgardCloseable` with `close()`, `.use{}` pattern
- Package: `com.glassthought.chainsaw.core.<subpackage>`
- No free-floating functions

## Logging
- `outFactory.getOutForClass(YourClass::class)`
- `Val(value, ValType.SPECIFIC_TYPE)` — never embed values in strings
- Lazy lambda for DEBUG/TRACE, snake_case messages, all `Out` methods are `suspend`

## Testing Framework
- Base: `AsgardDescribeSpec` (Kotest DescribeSpec)
- BDD: GIVEN/WHEN/THEN, one assert per `it` block
- Integration: `.config(isIntegTestEnabled())` on describe blocks
- MockWebServer pattern available for HTTP testing

## HTTP Server Requirements (from design ticket)
**Endpoints:**
- `POST /agent/done` — task completion with branch identifier
- `POST /agent/question` — ask question, curl blocks until answered
- `POST /agent/failed` — unrecoverable error notification
- `POST /agent/status` — health ping reply

**Lifecycle:**
- Bind port 0 (OS-assigned), write port to `$HOME/.chainsaw_agent_harness/server/port.txt`
- Agent CLI reads port file per request, delete port file on shutdown
- Server starts once, stays alive across all phases

## Agent CLI Script (`scripts/harness-cli-for-agent.sh`)
- Validates port: regex `^[1-9][0-9]*$`, range 1-65535
- URLs: `http://localhost:${PORT}/agent/{endpoint}`
- All requests include git branch, 30s curl timeout, jq for JSON body

## Initialization Pattern
- `Initializer` interface → `InitializerImpl`, companion `.standard()` factory
- `AppDependencies` implements `AsgardCloseable`

## Key Files
- `app/build.gradle.kts` — deps
- `gradle/libs.versions.toml` — version catalog
- `app/src/main/kotlin/com/glassthought/chainsaw/core/directLLMApi/` — interface pattern reference
- `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApiTest.kt` — MockWebServer example
- `app/src/test/kotlin/org/example/integTestSupport.kt` — integration test helper
- `scripts/harness-cli-for-agent.sh` — agent CLI expectations
- `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md` — design spec
