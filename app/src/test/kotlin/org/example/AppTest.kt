package org.example

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.chainsaw.core.tmux.util.TmuxCommandRunner
import io.kotest.matchers.shouldNotBe

/**
 * Sanity test: verifies the core project infrastructure can be instantiated.
 *
 * Note: the original generated App class was replaced by main() entry point.
 * This test validates the primary infrastructure used by the harness.
 */
class AppTest : AsgardDescribeSpec({
    describe("GIVEN TmuxCommandRunner") {
        it("THEN can be instantiated without error") {
            TmuxCommandRunner() shouldNotBe null
        }
    }
})
