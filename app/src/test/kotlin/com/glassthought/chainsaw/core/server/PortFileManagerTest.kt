package com.glassthought.chainsaw.core.server

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for [PortFileManager].
 *
 * Uses temp directories to avoid polluting the developer's home directory.
 */
class PortFileManagerTest : AsgardDescribeSpec({

    fun createTempPortFile(): Pair<Path, PortFileManager> {
        val tempDir = Files.createTempDirectory("port-file-manager-test")
        val portFilePath = tempDir.resolve("port.txt")
        return portFilePath to PortFileManager(portFilePath)
    }

    describe("GIVEN PortFileManager with a temp directory") {

        describe("WHEN writePort is called") {

            it("THEN port file exists") {
                val (portFilePath, manager) = createTempPortFile()
                manager.writePort(8080)
                Files.exists(portFilePath) shouldBe true
            }

            it("THEN port file content is the port number as string") {
                val (portFilePath, manager) = createTempPortFile()
                manager.writePort(12345)
                Files.readString(portFilePath) shouldBe "12345"
            }
        }

        describe("WHEN deletePort is called after writePort") {

            it("THEN port file does not exist") {
                val (portFilePath, manager) = createTempPortFile()
                manager.writePort(8080)
                manager.deletePort()
                Files.exists(portFilePath) shouldBe false
            }
        }

        describe("WHEN deletePort is called without prior writePort") {

            it("THEN does not throw") {
                val (_, manager) = createTempPortFile()
                shouldNotThrow<Exception> {
                    manager.deletePort()
                }
            }
        }
    }
})
