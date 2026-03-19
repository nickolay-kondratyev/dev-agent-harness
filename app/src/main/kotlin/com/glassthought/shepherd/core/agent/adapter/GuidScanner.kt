package com.glassthought.shepherd.core.agent.adapter

import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import java.nio.file.Path

/**
 * Scans for JSONL files containing a given GUID string.
 *
 * Extracted as an interface so tests can inject a fake that controls
 * scan results on successive calls without touching the filesystem.
 */
fun interface GuidScanner {
    /** Returns all JSONL [Path]s whose content contains [guid]. */
    suspend fun scan(guid: HandshakeGuid): List<Path>
}
