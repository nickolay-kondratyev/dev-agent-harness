package com.glassthought.shepherd.core.state

/**
 * Iteration configuration for a reviewer sub-part.
 *
 * @property max Maximum number of iterations allowed.
 * @property current Current iteration count (starts at 0, incremented by executor on each NEEDS_ITERATION).
 */
data class IterationConfig(
    val max: Int,
    val current: Int = 0,
)
