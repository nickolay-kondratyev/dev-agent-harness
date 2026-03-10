package com.glassthought.chainsaw.core.server

import java.nio.file.Files
import java.nio.file.Path

/**
 * Manages the port file that agents read to discover the harness server port.
 *
 * Default path: $HOME/.chainsaw_agent_harness/server/port.txt
 * (matches ref.ap.8PB8nMd93D3jipEWhME5n.E harness-cli-for-agent.sh)
 *
 * @param portFilePath Path where the port number will be written.
 */
class PortFileManager(private val portFilePath: Path) {

    /**
     * Writes the port number to the port file, creating parent directories as needed.
     * The port is written as a plain number string without trailing newline,
     * matching what the shell script's `read -r` expects.
     */
    fun writePort(port: Int) {
        Files.createDirectories(portFilePath.parent)
        Files.writeString(portFilePath, port.toString())
    }

    /**
     * Deletes the port file if it exists. Idempotent.
     */
    fun deletePort() {
        Files.deleteIfExists(portFilePath)
    }

    companion object {
        val DEFAULT_PATH: Path = Path.of(
            System.getProperty("user.home"),
            ".chainsaw_agent_harness", "server", "port.txt"
        )
    }
}
