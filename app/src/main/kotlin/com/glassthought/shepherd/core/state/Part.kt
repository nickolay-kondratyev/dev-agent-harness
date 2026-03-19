package com.glassthought.shepherd.core.state

/**
 * A single part in the plan — groups related sub-parts (doer + optional reviewer).
 *
 * @property name Unique name for this part (e.g., "ui_design").
 * @property phase The phase this part belongs to ([Phase.PLANNING] or [Phase.EXECUTION]).
 * @property description Human-readable description of what this part accomplishes.
 * @property subParts Ordered list of sub-parts (doer first, then reviewer if present).
 */
data class Part(
    val name: String,
    val phase: Phase,
    val description: String,
    val subParts: List<SubPart>,
)
