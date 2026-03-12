package com.glassthought.shepherd.cli

import com.asgard.core.annotation.AnchorPoint
import kotlinx.coroutines.runBlocking

// NOTE: Run via the installed distribution for interactive mode to work.
// `./gradlew :app:run` does NOT work — Gradle wraps the JVM without a real controlling
// terminal, breaking isatty() checks in interactive programs like `claude`.
//
// To run:
//   ./gradlew :app:installDist
//   ./app/build/install/app/bin/app
//
// CLI (picocli): shepherd run --workflow <name> --ticket <path> --iteration-max <N>
// See high-level.md ap.mmcagXtg6ulznKYYNKlNP.E for CLI spec.
// See high-level.md ap.HRlQHC1bgrTRyRknP3WNX.E for startup sequence spec.
@AnchorPoint("ap.4JVSSyLwZXop6hWiJNYevFQX.E")
fun main(args: Array<String>) {
  // [runBlocking] is acceptable at main() entry points per Kotlin development standards.
  runBlocking {
    // TODO: Implement Initializer that orchestrates:
    //   1. ContextInitializer → ShepherdContext
    //   2. ShepherdServer startup (Ktor CIO)
    //   3. TicketShepherdCreator → TicketShepherd
    //   4. TicketShepherd.run()
    //   5. Cleanup (ShepherdContext.close())
    TODO("CLI not yet implemented — see high-level.md ap.HRlQHC1bgrTRyRknP3WNX.E")
  }
}
