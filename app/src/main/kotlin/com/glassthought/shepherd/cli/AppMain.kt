package com.glassthought.shepherd.cli

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.out.impl.console.SimpleConsoleOutFactory
import com.asgard.core.processRunner.ProcessRunner
import com.glassthought.shepherd.core.creator.TicketShepherdCreatorImpl
import com.glassthought.shepherd.core.initializer.CliParams
import com.glassthought.shepherd.core.initializer.ContextInitializer
import com.glassthought.shepherd.core.initializer.EnvironmentValidator
import com.glassthought.shepherd.core.initializer.ShepherdInitializer
import com.glassthought.shepherd.core.initializer.TicketShepherdCreatorFactory
import com.glassthought.shepherd.core.supporting.git.GitBranchManager
import com.glassthought.shepherd.core.supporting.git.TryNResolverImpl
import com.glassthought.shepherd.core.supporting.git.WorkingTreeValidator
import com.glassthought.shepherd.core.supporting.ticket.TicketParser
import com.glassthought.shepherd.core.workflow.WorkflowParser
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.Path
import java.util.concurrent.Callable

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
    // Step 0: Validate environment before any infrastructure is created.
    // Ensures Docker container and required env vars. See ref.ap.A8WqG9oplNTpsW7YqoIyX.E.
    EnvironmentValidator.standard().validate()

    @Suppress("SpreadOperator")
    val exitCode = CommandLine(ShepherdRunCommand()).execute(*args)
    if (exitCode != 0) {
        System.exit(exitCode)
    }
}

/**
 * Picocli command for `shepherd run`.
 *
 * Parses CLI arguments and delegates to [ShepherdInitializer] for the full startup sequence.
 * No business logic lives here — this is a thin translation layer from CLI args to [CliParams].
 */
@Command(
    name = "shepherd",
    subcommands = [RunSubcommand::class],
    mixinStandardHelpOptions = true,
    description = ["Ticket Shepherd CLI — agent harness coordinator."],
)
class ShepherdRunCommand : Callable<Int> {
    override fun call(): Int {
        // [shepherd] without subcommand prints usage
        CommandLine.usage(this, System.out)
        return 0
    }
}

/**
 * The `run` subcommand that actually starts the workflow.
 */
@Command(
    name = "run",
    mixinStandardHelpOptions = true,
    description = ["Run a workflow on a ticket."],
)
class RunSubcommand : Callable<Int> {

    @Option(
        names = ["--ticket"],
        required = true,
        description = ["Path to the ticket markdown file."],
    )
    lateinit var ticketPath: Path

    // WHY required: TicketShepherdCreator.create() requires workflowName: String with no default.
    // Could become optional with a default (e.g., "straightforward") in the future if spec changes.
    // * @param workflowName Workflow name (e.g., "straightforward", "with-planning")
    @Option(
        names = ["--workflow"],
        required = true,
        description = ["Workflow definition name (e.g., 'straightforward', 'with-planning')."],
    )
    lateinit var workflowName: String

    @Option(
        names = ["--iteration-max"],
        required = true,
        description = ["Default iteration budget for agent feedback loops."],
    )
    var iterationMax: Int = 0

    override fun call(): Int {
        val cliParams = CliParams(
            ticketPath = ticketPath,
            workflowName = workflowName,
            iterationMax = iterationMax,
        )

        val outFactory = SimpleConsoleOutFactory.standard()

        val creatorFactory = TicketShepherdCreatorFactory { outFact ->
            val processRunner = ProcessRunner.standard(outFact)
            val repoRoot = Path.of(System.getProperty("user.dir"))
            TicketShepherdCreatorImpl(
                workflowParser = WorkflowParser.standard(outFact),
                ticketParser = TicketParser.standard(outFact),
                workingTreeValidator = WorkingTreeValidator.standard(outFact, processRunner),
                tryNResolver = TryNResolverImpl(repoRoot),
                gitBranchManager = GitBranchManager.standard(outFact, processRunner),
            )
        }

        val initializer = ShepherdInitializer(
            outFactory = outFactory,
            contextInitializer = ContextInitializer.standard(),
            ticketShepherdCreatorFactory = creatorFactory,
        )

        // [runBlocking] is acceptable at main() entry points per Kotlin development standards.
        // WHY: Catching generic Exception is intentional at this top-level entry point to
        // provide a clean error message instead of a stack trace to the user.
        @Suppress("TooGenericExceptionCaught")
        return try {
            runBlocking {
                initializer.run(cliParams)
            }
            0
        } catch (e: Exception) {
            System.err.println("Startup failed: ${e.message}")
            1
        }
    }
}
