package com.glassthought.shepherd.core.state

/**
 * In-memory representation of the current execution state.
 *
 * Wraps [Part] list as a mutable structure so the executor can update status,
 * iteration counts, and session records during execution.
 *
 * **Mutation methods** operate on the in-memory state only — the caller (PartExecutor)
 * is responsible for calling [CurrentStatePersistence.flush] after mutations.
 *
 * **Part-level status** is derived from sub-part statuses (no explicit part status field).
 * See [isPartCompleted], [isPartFailed], [findResumePoint].
 *
 * ap.hcNCxgMidaquKohuHeVEU.E (mutations)
 * ap.7mqCiP5Cr9i25k8NENYlR.E (derived queries)
 *
 * @property parts Mutable list of plan parts with their current runtime state.
 */
data class CurrentState(
    val parts: MutableList<Part>,
) {

    // ── Mutations ──────────────────────────────────────────────────────────

    /**
     * Updates the status of a specific sub-part identified by part name and sub-part name.
     *
     * @throws IllegalArgumentException if the part or sub-part is not found.
     * @throws IllegalStateException if the transition is invalid (via [SubPartStatus.validateCanSpawn]
     *         or [SubPartStatus.transitionTo]).
     */
    fun updateSubPartStatus(partName: String, subPartName: String, newStatus: SubPartStatus) {
        val partIndex = findPartIndex(partName)
        val part = parts[partIndex]
        val subPartIndex = findSubPartIndex(part, subPartName)
        val subPart = part.subParts[subPartIndex]

        val currentStatus = requireNotNull(subPart.status) {
            "Cannot update status of sub-part [$subPartName] in part [$partName]: " +
                "status is null (not initialized)"
        }

        validateStatusTransition(currentStatus, newStatus)

        val updatedSubPart = subPart.copy(status = newStatus)
        val updatedSubParts = part.subParts.toMutableList().apply { set(subPartIndex, updatedSubPart) }
        parts[partIndex] = part.copy(subParts = updatedSubParts)
    }

    /**
     * Increments the `iteration.current` counter for a reviewer sub-part.
     *
     * @throws IllegalArgumentException if the part, sub-part, or iteration config is not found.
     */
    fun incrementIteration(partName: String, subPartName: String) {
        val partIndex = findPartIndex(partName)
        val part = parts[partIndex]
        val subPartIndex = findSubPartIndex(part, subPartName)
        val subPart = part.subParts[subPartIndex]

        val iteration = requireNotNull(subPart.iteration) {
            "Cannot increment iteration of sub-part [$subPartName] in part [$partName]: " +
                "no iteration config (not a reviewer)"
        }

        val updatedSubPart = subPart.copy(iteration = iteration.copy(current = iteration.current + 1))
        val updatedSubParts = part.subParts.toMutableList().apply { set(subPartIndex, updatedSubPart) }
        parts[partIndex] = part.copy(subParts = updatedSubParts)
    }

    /**
     * Appends a [SessionRecord] to the sessionIds list of a sub-part.
     *
     * @throws IllegalArgumentException if the part or sub-part is not found.
     */
    fun addSessionRecord(partName: String, subPartName: String, record: SessionRecord) {
        val partIndex = findPartIndex(partName)
        val part = parts[partIndex]
        val subPartIndex = findSubPartIndex(part, subPartName)
        val subPart = part.subParts[subPartIndex]

        val currentSessions = subPart.sessionIds.orEmpty()
        val updatedSubPart = subPart.copy(sessionIds = currentSessions + record)
        val updatedSubParts = part.subParts.toMutableList().apply { set(subPartIndex, updatedSubPart) }
        parts[partIndex] = part.copy(subParts = updatedSubParts)
    }

    /**
     * Appends execution parts to the parts list.
     * Used after planning converges to add execution parts from the plan.
     */
    fun appendExecutionParts(newParts: List<Part>) {
        parts.addAll(newParts)
    }

    // ── Derived Status Queries ─────────────────────────────────────────────

    /**
     * A part is complete when ALL of its sub-parts have status [SubPartStatus.COMPLETED].
     */
    fun isPartCompleted(partName: String): Boolean {
        val part = findPart(partName)
        return part.subParts.all { it.status == SubPartStatus.COMPLETED }
    }

    /**
     * A part is failed when ANY of its sub-parts has status [SubPartStatus.FAILED].
     */
    fun isPartFailed(partName: String): Boolean {
        val part = findPart(partName)
        return part.subParts.any { it.status == SubPartStatus.FAILED }
    }

    /**
     * Finds the first part that has non-[SubPartStatus.COMPLETED] sub-parts (the resume point).
     * Returns null if all parts are completed.
     */
    fun findResumePoint(): Part? {
        return parts.firstOrNull { part ->
            part.subParts.any { it.status != SubPartStatus.COMPLETED }
        }
    }

    // ── Private Helpers ────────────────────────────────────────────────────

    private fun findPart(partName: String): Part {
        return parts.firstOrNull { it.name == partName }
            ?: throw IllegalArgumentException("Part not found: [$partName]")
    }

    private fun findPartIndex(partName: String): Int {
        val index = parts.indexOfFirst { it.name == partName }
        require(index >= 0) { "Part not found: [$partName]" }
        return index
    }

    private fun findSubPartIndex(part: Part, subPartName: String): Int {
        val index = part.subParts.indexOfFirst { it.name == subPartName }
        require(index >= 0) { "SubPart not found: [$subPartName] in part [${part.name}]" }
        return index
    }

    companion object {
        private fun validateStatusTransition(from: SubPartStatus, to: SubPartStatus) {
            when (from) {
                SubPartStatus.NOT_STARTED -> {
                    check(to == SubPartStatus.IN_PROGRESS) {
                        "Invalid transition from NOT_STARTED to $to; only IN_PROGRESS is allowed"
                    }
                }
                SubPartStatus.IN_PROGRESS -> {
                    check(to in VALID_FROM_IN_PROGRESS) {
                        "Invalid transition from IN_PROGRESS to $to; " +
                            "allowed: $VALID_FROM_IN_PROGRESS"
                    }
                }
                SubPartStatus.COMPLETED -> {
                    error("COMPLETED is terminal — no further transitions allowed")
                }
                SubPartStatus.FAILED -> {
                    error("FAILED is terminal — no further transitions allowed")
                }
            }
        }

        private val VALID_FROM_IN_PROGRESS = setOf(
            SubPartStatus.COMPLETED,
            SubPartStatus.FAILED,
        )
    }
}
