package org.opentripplanner.trakpi

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import java.time.Instant
import org.opentripplanner.trakpi.config.TrakpiConfigLoader
import org.opentripplanner.trakpi.orchestrator.Orchestrator
import org.opentripplanner.trakpi.tester.RequestFileLoader
import org.opentripplanner.trakpi.tester.Tester
import org.opentripplanner.trakpi.tester.spi.KPICalculator
import org.opentripplanner.trakpi.tester.spi.RequestLoader
import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TravelPlanner
import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest

/** Runs the trakpi command-line interface, wiring the CLI to the supplied planner implementations. */
fun <R : TravelPlannerRequest> runTrakpi(
    args: Array<String>,
    application: String,
    requestLoader: RequestLoader<R>,
    travelPlanner: TravelPlanner<R>,
    kpiCalculators: List<KPICalculator>,
    resultsWriter: ResultsWriter,
) {
    val orchestrator = Orchestrator()
    Trakpi()
        .subcommands(
            Prepare(orchestrator),
            Start(orchestrator),
            Stop(orchestrator),
            Test(application, requestLoader, travelPlanner, kpiCalculators, resultsWriter),
        )
        .main(args)
}

internal class Trakpi : CliktCommand(name = "trakpi") {
    override fun help(context: Context) = "Measure travel planner quality using Key Performance Indicators."

    override fun run() = Unit
}

/** Base for commands that operate on a single planner version. */
internal abstract class VersionedCommand(name: String) : CliktCommand(name = name) {
    protected val version: String by option("--version", help = "Planner version label, e.g. a commit hash").required()
}

internal class Prepare(private val orchestrator: Orchestrator) : VersionedCommand("prepare") {
    override fun help(context: Context) = "Prepare a planner version for testing."

    private val plannerArgs: String? by option("--plannerargs", help = "Opaque arguments passed to the planner adapter")

    override fun run() = orchestrator.prepare(version, plannerArgs)
}

internal class Start(private val orchestrator: Orchestrator) : VersionedCommand("start") {
    override fun help(context: Context) = "Start a planner process."

    override fun run() = orchestrator.start(version)
}

internal class Stop(private val orchestrator: Orchestrator) : VersionedCommand("stop") {
    override fun help(context: Context) = "Stop a running planner process."

    override fun run() = orchestrator.stop(version)
}

internal class Test<R : TravelPlannerRequest>(
    private val application: String,
    private val requestLoader: RequestLoader<R>,
    private val travelPlanner: TravelPlanner<R>,
    private val kpiCalculators: List<KPICalculator>,
    private val resultsWriter: ResultsWriter,
) : VersionedCommand("test") {
    override fun help(context: Context) = "Run a test. Assumes the planner is running and ready."

    private val configFile: Path? by
        option("--config", help = "Path to a trakpi config file (.properties)").path(mustExist = true, canBeDir = false)
    private val overrides: Map<String, String> by
        option("--set", help = "Override a config value, e.g. --set requests.dir=<path> (repeatable)").associate()
    private val referenceVersion: String? by
        option("--reference-version", help = "Baseline version to compare against; marks this run as the reference when it matches --version")
    private val testsetVersion: String by
        option("--testset-version", help = "Label of the request set being exercised").required()

    // TODO: --version is not yet used by the engine; it will select the prepared planner build.
    override fun run() {
        val config =
            try {
                TrakpiConfigLoader.load(configFile = configFile, overrides = overrides)
            } catch (e: IllegalArgumentException) {
                throw UsageError(e.message ?: "Invalid configuration")
            }
        Tester(
                run =
                    RunMetadata.create(
                        version = version,
                        application = application,
                        startedAt = Instant.now(),
                        referenceVersion = referenceVersion,
                        testsetVersion = testsetVersion,
                    ),
                requestFileLoader = RequestFileLoader(config.requestsDir),
                requestLoader = requestLoader,
                travelPlanner = travelPlanner,
                kpiCalculators = kpiCalculators,
                resultsWriter = resultsWriter,
            )
            .run()
    }
}
