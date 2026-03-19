package com.glassthought.shepherd.core.compaction

/**
 * Enumerates the conditions under which self-compaction is triggered.
 *
 * V1 supports only [DONE_BOUNDARY]. V2 adds `EMERGENCY_INTERRUPT` with continuous polling
 * (see `doc_v2/our-own-emergency-compression.md`).
 *
 * See spec: ref.ap.8nwz2AHf503xwq8fKuLcl.E (ContextWindowSelfCompactionUseCase.md)
 */
enum class CompactionTrigger {
    /** Agent at done boundary with remaining context <= soft threshold. No interrupt needed. */
    DONE_BOUNDARY,
}
