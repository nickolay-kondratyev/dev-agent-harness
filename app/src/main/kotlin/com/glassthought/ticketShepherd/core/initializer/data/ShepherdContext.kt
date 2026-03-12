package com.glassthought.ticketShepherd.core.initializer.data

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.lifecycle.AsgardCloseable
import com.glassthought.ticketShepherd.core.initializer.Infra

/**
 * Encapsulates all application-level dependencies created during initialization.
 *
 * Dependencies are organized into logical groups. As we get more groups such as useCases
 * they will go under this class as well.
 *
 * Implements [com.asgard.core.lifecycle.AsgardCloseable] to ensure proper cleanup of all held resources.
 * .close() should be called at the end of main function when we are shutting down.
 *
 * ### Relationships
 * - Created by [com.glassthought.ticketShepherd.core.initializer.Initializer]/ref.ap.9zump9YISPSIcdnxEXZZX.E
 */
@AnchorPoint("ap.TkpljsXvwC6JaAVnIq02He98.E")
class ShepherdContext(
  val infra: Infra,
) : AsgardCloseable {

  override suspend fun close() {

    // Infra should be the last to be closed as it contains the out factory which is used
    // for logging, and we may want to log while we are shutting down.
    infra.close()
  }
}