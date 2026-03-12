package com.glassthought.ticketShepherd.core.initializer.data

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.lifecycle.AsgardCloseable
import com.glassthought.ticketShepherd.core.initializer.Infra

/**
 * Encapsulates all application-level dependencies created during initialization.
 *
 * Dependencies are organized into logical groups:
 * - [infra] — shared services and IO adapters (tmux, LLM, logging)
 *
 * Implements [com.asgard.core.lifecycle.AsgardCloseable] to ensure proper cleanup of all held resources.
 * Use via `.use{}` at the call site to guarantee shutdown even on exceptions.
 *
 * ### Relationships
 * - Created by [com.glassthought.ticketShepherd.core.initializer.Initializer]/ref.ap.9zump9YISPSIcdnxEXZZX.E
 */
@AnchorPoint("ap.TkpljsXvwC6JaAVnIq02He98.E")
class ShepherdContext(
  val infra: Infra,
) : AsgardCloseable {

    override suspend fun close() {
        // Shut down OkHttpClient connection and thread pools to prevent resource leaks
        // in long-running server usage. Order matters: dispatcher first, then connections.
        infra.directLlm.httpClient.dispatcher.executorService.shutdown()
        infra.directLlm.httpClient.connectionPool.evictAll()
        infra.outFactory.close()
    }
}