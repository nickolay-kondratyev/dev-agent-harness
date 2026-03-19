package com.glassthought.shepherd.usecase.ticketstatus

/**
 * Updates the ticket file's YAML frontmatter status to "done".
 *
 * Called by [TicketShepherd][com.glassthought.shepherd.core.TicketShepherd] after all parts
 * complete successfully.
 */
fun interface TicketStatusUpdater {
    suspend fun markDone()
}
