package com.glassthought.shepherd.core.agent.facade

import com.asgard.core.annotation.AnchorPoint

/**
 * Snapshot of an agent's context window usage at a point in time.
 *
 * Read via [AgentFacade.readContextWindowState]/ref.ap.1aEIkOGUeTijwvrACf3Ga.E at done boundaries
 * for self-compaction decisions (ref.ap.8nwz2AHf503xwq8fKuLcl.E).
 *
 * @property remainingPercentage Percentage of context window remaining (0–100), or `null` if the
 *   value is stale, unavailable, or could not be read. Callers must handle `null` gracefully —
 *   it indicates the state is unknown, not that the context window is full.
 */
@AnchorPoint("ap.f4OVHiR0b7dpozBJDmIhv.E")
data class ContextWindowState(
    val remainingPercentage: Int?,
)
