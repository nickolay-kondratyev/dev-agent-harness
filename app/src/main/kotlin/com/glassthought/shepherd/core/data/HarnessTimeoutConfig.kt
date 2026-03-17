package com.glassthought.shepherd.core.data

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Centralizes all timeout and threshold constants for the harness.
 *
 * Having all values in one place enables:
 * - Single-location tuning (no hunting across implementations)
 * - Test injection of fast-timeout variants (see [forTests])
 *
 * ### Sources
 * - Health monitoring: ref.ap.RJWVLgUGjO5zAwupNLhA0.E (HealthMonitoring.md — Configuration section)
 * - Self-compaction: ref.ap.8nwz2AHf503xwq8fKuLcl.E (ContextWindowSelfCompactionUseCase.md — Thresholds)
 * - PartExecutor dependencies: ref.ap.fFr7GUmCYQEV5SJi8p6AS.E (PartExecutor.md)
 *
 * ### Wiring
 * Created by [com.glassthought.shepherd.core.initializer.ContextInitializer] with [defaults]
 * and held in [com.glassthought.shepherd.core.initializer.data.ShepherdContext].
 */
data class HarnessTimeoutConfig(

  // --- Health Monitoring (ref.ap.RJWVLgUGjO5zAwupNLhA0.E) ---

  /** After agent spawn, the window before declaring a startup failure.
   *  Catches misconfigured env, binary crashes, and TMUX issues 10× faster than [noActivityTimeout].
   *  Switches to [noActivityTimeout] as soon as any callback arrives. */
  val startupAckTimeout: Duration = 3.minutes,

  /** How often PartExecutor polls `lastActivityTimestamp` while the deferred is pending. */
  val healthCheckInterval: Duration = 5.minutes,

  /** Elapsed time since last HTTP callback before triggering a ping.
   *  Applies to all sub-parts once the startup phase is complete. */
  val noActivityTimeout: Duration = 30.minutes,

  /** Wait time after sending a ping before declaring the agent crashed.
   *  Any callback activity during this window resets the timer. */
  val pingTimeout: Duration = 3.minutes,

  // --- Payload Delivery ACK (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E) ---

  /** Maximum wait time per ACK attempt when delivering an instruction payload. */
  val payloadAckTimeout: Duration = 3.minutes,

  /** Maximum number of delivery attempts before the harness gives up. */
  val payloadAckRetries: Int = 3,

  // --- Context-Window Self-Compaction (ref.ap.8nwz2AHf503xwq8fKuLcl.E) ---

  /** Maximum time the harness waits for the agent to write PRIVATE.md after
   *  a self-compaction instruction is issued.  Expiry → `AgentCrashed`. */
  val selfCompactionTimeout: Duration = 5.minutes,

  /** Maximum age of `file_updated_timestamp` inside `context_window_slim.json` before
   *  the value is treated as stale.  When stale, `remainingPercentage` is returned as null —
   *  no compaction is triggered and a warning is logged instead.
   *  Ref: ref.ap.ufavF1Ztk6vm74dLAgANY.E (ContextWindowStateReader) */
  val contextFileStaleTimeout: Duration = 5.minutes,

  /** Remaining-context percentage at or below which soft (proactive) compaction triggers.
   *  Checked at `done` boundaries.  Default 35 means 65% of context has been used. */
  val contextWindowSoftThresholdPct: Int = 35,

  /** Remaining-context percentage at or below which hard (emergency) compaction triggers.
   *  Polled every second.  Default 20 means 80% of context has been used. */
  val contextWindowHardThresholdPct: Int = 20,

) {

  companion object {
    /** Production defaults — all values match the spec. */
    fun defaults(): HarnessTimeoutConfig = HarnessTimeoutConfig()

    /**
     * Fast-timeout variant for unit tests.
     *
     * Reduces every harness timeout to sub-second durations so tests that exercise
     * timeout-triggered branches complete in milliseconds instead of minutes.
     * Threshold percentages are left at production values (they drive logic, not wall time).
     */
    fun forTests(): HarnessTimeoutConfig = HarnessTimeoutConfig(
      startupAckTimeout = 2.seconds,
      healthCheckInterval = 1.seconds,
      noActivityTimeout = 5.seconds,
      pingTimeout = 2.seconds,
      payloadAckTimeout = 2.seconds,
      selfCompactionTimeout = 3.seconds,
      contextFileStaleTimeout = 2.seconds,
    )
  }
}
