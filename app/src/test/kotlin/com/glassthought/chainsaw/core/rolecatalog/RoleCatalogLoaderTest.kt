package com.glassthought.chainsaw.core.rolecatalog

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path

class RoleCatalogLoaderTest : AsgardDescribeSpec({

    /**
     * Resolves a test resource subdirectory to a [Path].
     * Works because Gradle runs tests against exploded resources, not JARs.
     */
    fun resourceDir(name: String): Path =
        Path.of(
            RoleCatalogLoaderTest::class.java
                .getResource("/com/glassthought/chainsaw/core/rolecatalog/$name")!!
                .toURI()
        )

    describe("GIVEN a valid catalog directory with multiple roles") {
        val loader = RoleCatalogLoader.standard(outFactory)
        val dir = resourceDir("valid-catalog")

        describe("WHEN load is called") {
            it("THEN returns a list with 2 roles") {
                val roles = loader.load(dir)
                roles.size shouldBe 2
            }

            it("THEN contains a role named IMPLEMENTOR") {
                val roles = loader.load(dir)
                roles.any { it.name == "IMPLEMENTOR" } shouldBe true
            }

            it("THEN contains a role named REVIEWER") {
                val roles = loader.load(dir)
                roles.any { it.name == "REVIEWER" } shouldBe true
            }

            it("THEN IMPLEMENTOR has correct description") {
                val roles = loader.load(dir)
                val implementor = roles.first { it.name == "IMPLEMENTOR" }
                implementor.description shouldBe "Implements features based on detailed plans"
            }

            it("THEN IMPLEMENTOR has descriptionLong populated") {
                val roles = loader.load(dir)
                val implementor = roles.first { it.name == "IMPLEMENTOR" }
                implementor.descriptionLong shouldBe "Full-stack implementation agent that writes production code, tests, and documentation"
            }

            it("THEN REVIEWER has description populated") {
                val roles = loader.load(dir)
                val reviewer = roles.first { it.name == "REVIEWER" }
                reviewer.description shouldBe "Reviews code for correctness and style"
            }

            it("THEN REVIEWER has null descriptionLong") {
                val roles = loader.load(dir)
                val reviewer = roles.first { it.name == "REVIEWER" }
                reviewer.descriptionLong shouldBe null
            }

            it("THEN each role has a filePath ending with its filename") {
                val roles = loader.load(dir)
                roles.forEach { role ->
                    role.filePath.fileName.toString() shouldBe "${role.name}.md"
                }
            }
        }
    }

    describe("GIVEN a catalog directory with a role missing description") {
        val loader = RoleCatalogLoader.standard(outFactory)
        val dir = resourceDir("missing-description")

        describe("WHEN load is called") {
            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    loader.load(dir)
                }
            }

            it("THEN error message contains the filename") {
                val exception = shouldThrow<IllegalArgumentException> {
                    loader.load(dir)
                }
                exception.message shouldContain "BAD_ROLE.md"
            }
        }
    }

    describe("GIVEN an empty catalog directory (no .md files)") {
        val loader = RoleCatalogLoader.standard(outFactory)
        val dir = resourceDir("empty-catalog")

        describe("WHEN load is called") {
            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    loader.load(dir)
                }
            }

            it("THEN error message indicates no .md files found") {
                val exception = shouldThrow<IllegalArgumentException> {
                    loader.load(dir)
                }
                exception.message shouldContain "No .md files found"
            }
        }
    }

    describe("GIVEN a non-existent directory path") {
        val loader = RoleCatalogLoader.standard(outFactory)
        val dir = Path.of("/non/existent/directory/that/does/not/exist")

        describe("WHEN load is called") {
            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    loader.load(dir)
                }
            }

            it("THEN error message contains the directory path") {
                val exception = shouldThrow<IllegalArgumentException> {
                    loader.load(dir)
                }
                exception.message shouldContain dir.toString()
            }
        }
    }

    describe("GIVEN a catalog directory with a single role") {
        val loader = RoleCatalogLoader.standard(outFactory)
        val dir = resourceDir("single-role")

        describe("WHEN load is called") {
            it("THEN returns a list with exactly 1 role") {
                val roles = loader.load(dir)
                roles.size shouldBe 1
            }

            it("THEN the role name matches the filename without extension") {
                val roles = loader.load(dir)
                roles.first().name shouldBe "PLANNER"
            }
        }
    }
})
