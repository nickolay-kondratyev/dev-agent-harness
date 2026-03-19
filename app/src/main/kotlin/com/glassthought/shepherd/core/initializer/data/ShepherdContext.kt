package com.glassthought.shepherd.core.initializer.data

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.lifecycle.AsgardCloseable
import com.glassthought.shepherd.core.agent.noninteractive.NonInteractiveAgentRunner
import com.glassthought.shepherd.core.data.HarnessTimeoutConfig
import com.glassthought.shepherd.core.initializer.Infra

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
 * - Created by [com.glassthought.shepherd.core.initializer.ContextInitializer]/ref.ap.9zump9YISPSIcdnxEXZZX.E
 */
@AnchorPoint("ap.TkpljsXvwC6JaAVnIq02He98.E")
// Delegates AsgardCloseable to infra: infra is closed last as it contains the out factory used
// for logging — we may want to log during shutdown.
class ShepherdContext(
  val infra: Infra,
  val nonInteractiveAgentRunner: NonInteractiveAgentRunner,
  val timeoutConfig: HarnessTimeoutConfig = HarnessTimeoutConfig.defaults(),
) : AsgardCloseable by infra