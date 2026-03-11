package com.glassthought.chainsaw.core.server

import java.nio.file.Files
import java.nio.file.Path

/**
 * Publishes (writes/removes) the port file that agents read to discover the harness server port.
 *
 * Default implementation: [PortFileManager].
 */
interface PortPublisher {
    /** Writes [port] to the port file so agents can discover the server. */
    fun writePort(port: Int)

    /** Removes the port file. Idempotent. */
    fun deletePort()
}

/**
 * Manages the port file that agents read to discover the harness server port.
 *
 * Default path: $HOME/.chainsaw_agent_harness/server/port.txt
 *
 * @param portFilePath Path where the port number will be written.
 */
class PortFileManager(private val portFilePath: Path) : PortPublisher {

    /**
     * Writes the port number to the port file, creating parent directories as needed.
     * The port is written as a plain number string without trailing newline,
     * matching what the shell script's `read -r` expects.
     */
    override fun writePort(port: Int) {
        Files.createDirectories(portFilePath.parent)
        Files.writeString(portFilePath, port.toString())
    }

    /**
     * Deletes the port file if it exists. Idempotent.
     */
    override fun deletePort() {
        Files.deleteIfExists(portFilePath)
    }

    companion object {
        // Port file path convention: $HOME/.chainsaw_agent_harness/server/port.txt
        val DEFAULT_PATH: Path = Path.of(
            System.getProperty("user.home"),
            ".chainsaw_agent_harness", "server", "port.txt"
        )
    }
}
