package com.glassthought.shepherd.core.state

/**
 * A single sub-part within a [Part] — either a doer or a reviewer.
 *
 * Fields [status], [iteration], and [sessionIds] are runtime-only: they are absent in the
 * plan-flow JSON and populated during execution (current-state JSON).
 *
 * @property name Unique name within the parent part (e.g., "impl", "review").
 * @property role The agent role to assign (e.g., "UI_DESIGNER").
 * @property agentType The type of agent to spawn (e.g., "ClaudeCode").
 * @property model The model to use (e.g., "sonnet").
 * @property status Current lifecycle status. Null in plan-flow, populated at runtime.
 * @property iteration Iteration configuration for reviewer sub-parts. Null for doers.
 * @property sessionIds History of agent sessions spawned for this sub-part. Null in plan-flow.
 */
data class SubPart(
    val name: String,
    val role: String,
    val agentType: String,
    val model: String,
    val status: SubPartStatus? = null,
    val iteration: IterationConfig? = null,
    val sessionIds: List<SessionRecord>? = null,
)
