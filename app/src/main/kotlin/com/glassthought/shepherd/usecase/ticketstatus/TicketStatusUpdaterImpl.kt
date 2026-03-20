package com.glassthought.shepherd.usecase.ticketstatus

import com.asgard.core.data.value.Val
import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessRunner
import com.glassthought.shepherd.core.ShepherdValType

/**
 * Production implementation of [TicketStatusUpdater].
 *
 * Closes the ticket by executing `ticket close <ticketId>` via [ProcessRunner].
 *
 * WHY `ticket` not `tk`: `tk` is a shell alias; `ticket` is the actual executable
 * guaranteed to be on PATH in all environments.
 *
 * @param ticketId The ticket ID to close when [markDone] is called.
 * @param processRunner Subprocess executor for running the `ticket` CLI command.
 * @param outFactory Logging factory.
 */
class TicketStatusUpdaterImpl(
    private val ticketId: String,
    private val processRunner: ProcessRunner,
    outFactory: OutFactory,
) : TicketStatusUpdater {

    private val out = outFactory.getOutForClass(TicketStatusUpdaterImpl::class)

    override suspend fun markDone() {
        out.info(
            "closing_ticket",
            Val(ticketId, ShepherdValType.TICKET_ID),
        )

        processRunner.runProcess("ticket", "close", ticketId)

        out.info(
            "ticket_closed",
            Val(ticketId, ShepherdValType.TICKET_ID),
        )
    }
}
