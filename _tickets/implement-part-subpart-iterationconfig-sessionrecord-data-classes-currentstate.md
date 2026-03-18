---
id: nid_m3cm8xizw5qhu1cu3454rca79_E
title: "Implement Part, SubPart, IterationConfig, SessionRecord data classes + CurrentState"
status: open
deps: [nid_smb6zudqraf0hkp3u9kjx855e_E]
links: []
created_iso: 2026-03-18T18:02:34Z
status_updated_iso: 2026-03-18T18:02:34Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [plan-current-state, data-model, jackson]
---

Implement the JSON-serializable data model for plan-and-current-state spec (ref.ap.56azZbk7lAMll0D4Ot2G0.E).

## What to implement

### 1. Part data class (spec lines 38-43)
```kotlin
data class Part(
    val name: String,
    val phase: Phase,         // enum: PLANNING, EXECUTION
    val description: String,
    val subParts: List<SubPart>,
)

enum class Phase {
    @JsonProperty("planning") PLANNING,
    @JsonProperty("execution") EXECUTION,
}
```

### 2. SubPart data class (spec lines 47-57)
```kotlin
data class SubPart(
    val name: String,
    val role: String,
    val agentType: String,
    val model: String,
    val status: SubPartStatus? = null,           // runtime, absent in plan_flow.json
    val iteration: IterationConfig? = null,      // present only on reviewer sub-parts
    val sessionIds: List<SessionRecord>? = null,  // runtime, added by harness
)
```

### 3. IterationConfig (spec lines 54-56, 197-212)
```kotlin
data class IterationConfig(
    val max: Int,
    val current: Int = 0,    // runtime field, starts at 0
)
```

### 4. SessionRecord (spec lines 561-577, ap.mwzGc1hYkVwu3IJQbTeW4.E)
```kotlin
data class SessionRecord(
    val handshakeGuid: String,
    val agentSession: AgentSessionInfo,
    val agentType: String,
    val model: String,
    val timestamp: String,    // ISO-8601
)

data class AgentSessionInfo(
    val id: String,
)
```

### 5. CurrentState (spec lines 8-28, ap.K3vNzHqR8wYm5pJdL2fXa.E)
```kotlin
data class CurrentState(
    val parts: MutableList<Part>,  // mutable: execution parts appended after planning
)
```
This is the in-memory single source of truth. Key invariant: no component reads current_state.json during a run.

### 6. Jackson Configuration
Configure ObjectMapper with:
- KotlinModule (for data class support)
- PropertyNamingStrategy.SNAKE_CASE (JSON uses camelCase per spec examples — verify)
- DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = false (for forward-compat)
- SerializationInclusion = NON_NULL (omit absent runtime fields)

Create a shared ObjectMapper factory or companion for reuse across the project.

### 7. Tests
- Serialize/deserialize round-trip tests for each data class
- Verify plan_flow.json (no runtime fields) deserializes correctly with null status/sessionIds
- Verify current_state.json (with runtime fields) deserializes correctly
- Use actual JSON examples from the spec (lines 268-295, 297-365) as test fixtures
- Verify Phase enum serialization ("planning"/"execution")

## Package
`com.glassthought.shepherd.core.state` (same package as SubPartStatus)

## Files to read
- `doc/schema/plan-and-current-state.md` — authoritative spec with JSON examples
- `app/build.gradle.kts` — Jackson dependencies already declared
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/ResumableAgentSessionId.kt` — existing session ID pattern

