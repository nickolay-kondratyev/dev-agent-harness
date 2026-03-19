package com.glassthought.shepherd.core.state

/**
 * Role of a sub-part within a [Part], derived from its position in the sub-parts array.
 *
 * Position 0 = [DOER], position 1 = [REVIEWER].
 * Role is derived on-the-fly via [fromIndex] — never stored directly.
 *
 * Future roles (e.g. FIXER) are additive enum variants.
 */
enum class SubPartRole {
    DOER,
    REVIEWER;

    companion object {
        fun fromIndex(subPartIndex: Int): SubPartRole = when (subPartIndex) {
            0 -> DOER
            1 -> REVIEWER
            else -> throw IllegalArgumentException(
                "Invalid subPartIndex=[$subPartIndex]. Expected 0 (DOER) or 1 (REVIEWER)."
            )
        }
    }
}
