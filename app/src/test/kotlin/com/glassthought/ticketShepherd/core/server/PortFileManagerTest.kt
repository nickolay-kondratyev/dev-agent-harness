package com.glassthought.ticketShepherd.core.server

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

    data class PortFileFixture(val portFilePath: Path, val portFileManager: PortFileManager)

    fun createFixture(): PortFileFixture {
        val tempDir = Files.createTempDirectory("port-file-manager-test")
        val portFilePath = tempDir.resolve("port.txt")
        return PortFileFixture(portFilePath, PortFileManager(portFilePath))
    }

    describe("GIVEN PortFileManager with a temp directory") {

        describe("WHEN writePort is called") {

            it("THEN port file exists") {
                val fixture = createFixture()
                fixture.portFileManager.writePort(8080)
                Files.exists(fixture.portFilePath) shouldBe true
            }

            it("THEN port file content is the port number as string") {
                val fixture = createFixture()
                fixture.portFileManager.writePort(12345)
                Files.readString(fixture.portFilePath) shouldBe "12345"
            }
        }

        describe("WHEN deletePort is called after writePort") {

            it("THEN port file does not exist") {
                val fixture = createFixture()
                fixture.portFileManager.writePort(8080)
                fixture.portFileManager.deletePort()
                Files.exists(fixture.portFilePath) shouldBe false
            }
        }

        describe("WHEN deletePort is called without prior writePort") {

            it("THEN does not throw") {
                val fixture = createFixture()
                shouldNotThrow<Exception> {
                    fixture.portFileManager.deletePort()
                }
            }
        }
    }
})
