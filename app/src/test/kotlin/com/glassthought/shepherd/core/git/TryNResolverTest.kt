package com.glassthought.shepherd.core.git

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.supporting.git.TryNResolverImpl
import com.glassthought.shepherd.core.supporting.ticket.TicketData
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class TryNResolverTest : AsgardDescribeSpec({

    val ticketData = TicketData(
        id = "TK-001",
        title = "My Feature",
        status = null,
        description = "",
    )

    describe("GIVEN TryNResolver") {

        describe("WHEN no .ai_out/ directory exists") {
            val repoRoot = Files.createTempDirectory("try-n-resolver-test")
            val resolver = TryNResolverImpl(repoRoot)

            it("THEN returns 1") {
                resolver.resolve(ticketData) shouldBe 1
            }
        }

        describe("WHEN .ai_out/ directory for try-1 exists") {
            val repoRoot = Files.createTempDirectory("try-n-resolver-test")
            // Branch name for try-1: "TK-001__my-feature__try-1"
            Files.createDirectories(repoRoot.resolve(".ai_out/TK-001__my-feature__try-1"))
            val resolver = TryNResolverImpl(repoRoot)

            it("THEN returns 2") {
                resolver.resolve(ticketData) shouldBe 2
            }
        }

        describe("WHEN .ai_out/ directories for try-1 and try-3 exist but not try-2") {
            val repoRoot = Files.createTempDirectory("try-n-resolver-test")
            Files.createDirectories(repoRoot.resolve(".ai_out/TK-001__my-feature__try-1"))
            Files.createDirectories(repoRoot.resolve(".ai_out/TK-001__my-feature__try-3"))
            val resolver = TryNResolverImpl(repoRoot)

            it("THEN returns 2 (first gap)") {
                resolver.resolve(ticketData) shouldBe 2
            }
        }

        describe("WHEN .ai_out/ directories for try-1 and try-2 exist") {
            val repoRoot = Files.createTempDirectory("try-n-resolver-test")
            Files.createDirectories(repoRoot.resolve(".ai_out/TK-001__my-feature__try-1"))
            Files.createDirectories(repoRoot.resolve(".ai_out/TK-001__my-feature__try-2"))
            val resolver = TryNResolverImpl(repoRoot)

            it("THEN returns 3") {
                resolver.resolve(ticketData) shouldBe 3
            }
        }

        describe("WHEN .ai_out/ exists but contains directories for a different ticket") {
            val repoRoot = Files.createTempDirectory("try-n-resolver-test")
            Files.createDirectories(repoRoot.resolve(".ai_out/TK-999__other-ticket__try-1"))
            val resolver = TryNResolverImpl(repoRoot)

            it("THEN returns 1 (unrelated directories are ignored)") {
                resolver.resolve(ticketData) shouldBe 1
            }
        }
    }
})
