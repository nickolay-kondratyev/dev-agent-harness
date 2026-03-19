package com.glassthought.shepherd.core.supporting.git

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path

class GitCommandBuilderTest : AsgardDescribeSpec(body = {

    describe("GIVEN no workingDir (null)") {
        val builder = GitCommandBuilder()

        describe("WHEN build is called with args") {
            val result = builder.build("status", "--porcelain")

            it("THEN returns git command without -C flag") {
                result shouldBe arrayOf("git", "status", "--porcelain")
            }
        }

        describe("WHEN build is called with no args") {
            val result = builder.build()

            it("THEN returns array with only git") {
                result shouldBe arrayOf("git")
            }
        }
    }

    describe("GIVEN workingDir is specified") {
        val builder = GitCommandBuilder(workingDir = Path.of("/some/repo"))

        describe("WHEN build is called with args") {
            val result = builder.build("add", "-A")

            it("THEN prepends -C <workingDir> before args") {
                result shouldBe arrayOf("git", "-C", "/some/repo", "add", "-A")
            }
        }

        describe("WHEN build is called with no args") {
            val result = builder.build()

            it("THEN returns git with -C <workingDir>") {
                result shouldBe arrayOf("git", "-C", "/some/repo")
            }
        }
    }

    describe("GIVEN workingDir with spaces in path") {
        val builder = GitCommandBuilder(workingDir = Path.of("/my repo/path"))

        describe("WHEN build is called") {
            val result = builder.build("log")

            it("THEN preserves the path with spaces as a single element") {
                result[2] shouldBe "/my repo/path"
            }
        }
    }
})
