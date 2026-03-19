package com.glassthought.shepherd.core.session

import com.asgard.core.annotation.AnchorPoint
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.util.MutableSynchronizedMap

/**
 * In-memory registry of live agent sessions, keyed by [HandshakeGuid].
 *
 * Bridges the HTTP server (which receives callbacks identified by GUID) with the
 * [kotlinx.coroutines.CompletableDeferred] that the executor is suspended on.
 *
 * Internal to `AgentFacadeImpl` — `PartExecutor` does not access this directly.
 *
 * See spec at `doc/core/SessionsState.md`.
 */
@AnchorPoint("ap.7V6upjt21tOoCFXA7nqNh.E")
class SessionsState(
    private val map: MutableSynchronizedMap<HandshakeGuid, SessionEntry> = MutableSynchronizedMap()
) {
    /** Adds or updates a session entry for the given [guid]. */
    suspend fun register(guid: HandshakeGuid, entry: SessionEntry) {
        map.put(guid, entry)
    }

    /** Returns the [SessionEntry] for the given [guid], or null if not found. */
    suspend fun lookup(guid: HandshakeGuid): SessionEntry? {
        return map.get(guid)
    }

    /** Removes the session entry for the given [guid]. Returns the removed entry, or null. */
    suspend fun remove(guid: HandshakeGuid): SessionEntry? {
        return map.remove(guid)
    }

    /**
     * Removes all sessions belonging to the given [partName].
     * Returns the list of removed [SessionEntry] instances.
     */
    suspend fun removeAllForPart(partName: String): List<SessionEntry> {
        return map.removeAll { _, entry -> entry.partName == partName }
    }
}
