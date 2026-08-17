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
import java.time.LocalDate
import java.time.ZoneOffset
import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.config.TrakpiConfigLoader
import org.opentripplanner.trakpi.orchestrator.PlannerOrchestrator
import org.opentripplanner.trakpi.tester.DirectoryRequestFileLoader
import org.opentripplanner.trakpi.tester.Tester
import org.opentripplanner.trakpi.tester.spi.RequestFileLoader
import org.opentripplanner.trakpi.testset.RequestCodec
import org.opentripplanner.trakpi.testset.TestsetBuilder
import org.opentripplanner.trakpi.tester.spi.kpi.ComparativeKPICalculator
import org.opentripplanner.trakpi.tester.spi.kpi.KPICalculator
import org.opentripplanner.trakpi.tester.spi.RequestLoader
import org.opentripplanner.trakpi.testset.RequestTransform
import org.opentripplanner.trakpi.tester.spi.ResultsReader
import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.testset.TestsetSource
import org.opentripplanner.trakpi.testset.TestsetStore
import org.opentripplanner.trakpi.tester.spi.TravelPlanner
import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest

/**
 * The runtime side of a planner integration: what the `test` command needs to run a planner build and
 * score its responses. (The `start`/`stop` lifecycle commands need no configuration.)
 */
class TesterConfig<R : TravelPlannerRequest>(
    val requestLoader: RequestLoader<R>,
    val travelPlanner: TravelPlanner<R>,
    val kpiCalculators: List<KPICalculator>,
    val resultsWriter: ResultsWriter,
    val comparativeKpiCalculators: List<ComparativeKPICalculator> = emptyList(),
    val resultsReader: ResultsReader? = null,
    val requestFileLoader: RequestFileLoader? = null,
)

/**
 * The testset side of a planner integration: what's needed to build and store the versioned request
 * sets a planner is tested against. [api] names the request format the planner speaks (scopes
 * testsets). Supplying it to [runTrakpi] enables the `testset` commands.
 */
class TestsetConfig<T>(
    val api: String,
    val source: TestsetSource? = null,
    val codec: RequestCodec<T>? = null,
    val transforms: List<RequestTransform<T>> = emptyList(),
    val store: TestsetStore? = null,
)

/**
 * Runs the trakpi command-line interface. Every command is always present so `trakpi` documents itself:
 * `start`/`stop` need no configuration, while `test` and the `testset` commands explain when
 * their [tester]/[testset] is not configured. A planner integration supplies whichever side(s) it
 * supports — the OTP one supplies both.
 */
fun <R : TravelPlannerRequest, T> runTrakpi(
    args: Array<String>,
    application: String,
    orchestrator: PlannerOrchestrator? = null,
    tester: TesterConfig<R>? = null,
    testset: TestsetConfig<T>? = null,
) {
    val commands =
        listOf(
            Start(orchestrator),
            Stop(orchestrator),
            Test(application, tester),
            Testset().subcommands(Testset.List(testset), Testset.Prepare(testset)),
        )
    Trakpi().subcommands(commands).main(args)
}

internal class Trakpi : CliktCommand(name = "trakpi") {
    override fun help(context: Context) = "Measure travel planner quality using Key Performance Indicators."

    override fun run() = Unit
}

/** Base for commands that operate on a single planner version. */
internal abstract class VersionedCommand(name: String) : CliktCommand(name = name) {
    protected val version: String by option("--version", help = "Planner version label, e.g. a commit hash").required()

    /** Parses [version] into a [PlannerVersion], reporting a usage error when it is malformed. */
    protected fun plannerVersion(): PlannerVersion =
        try {
            PlannerVersion(version)
        } catch (e: IllegalArgumentException) {
            throw UsageError(e.message ?: "Invalid version")
        }
}

internal class Start(private val orchestrator: PlannerOrchestrator?) : VersionedCommand("start") {
    override fun help(context: Context) = "Start a planner process."

    private val plannerArgs: String? by
        option("--plannerargs", help = "Opaque arguments passed to the planner adapter")

    override fun run() {
        val orchestrator = orchestrator ?: throw UsageError("Orchestration is not configured for this planner.")
        orchestrator.start(plannerVersion(), plannerArgs)
    }
}

internal class Stop(private val orchestrator: PlannerOrchestrator?) : VersionedCommand("stop") {
    override fun help(context: Context) = "Stop a running planner process."

    override fun run() {
        val orchestrator = orchestrator ?: throw UsageError("Orchestration is not configured for this planner.")
        orchestrator.stop(plannerVersion())
    }
}

internal class Test<R : TravelPlannerRequest>(
    private val application: String,
    private val tester: TesterConfig<R>?,
) : VersionedCommand("test") {
    override fun help(context: Context) = "Run a test. Assumes the planner is running and ready."

    private val configFile: Path? by
        option("--config", help = "Path to a trakpi config file (.properties)").path(mustExist = true, canBeDir = false)
    private val commandLineOverrides: Map<String, String> by
        option("--set", help = "Override a config value, e.g. --set requests.dir=<path> (repeatable)").associate()
    private val referenceVersion: String? by
        option("--reference-version", help = "Baseline version to compare against")
    private val testsetVersion: String by
        option("--testset-version", help = "Label of the request set being exercised").required()

    // --version identifies the run: it labels every result and keys the archived responses a later run
    // compares against.
    override fun run() {
        val tester = tester ?: throw UsageError("Testing is not configured for this planner.")
        val requestFileLoader = tester.requestFileLoader ?: DirectoryRequestFileLoader(loadConfig().requestsDir)
        val (plannerVersion, referencePlannerVersion, testset) =
            try {
                Triple(
                    PlannerVersion(version),
                    referenceVersion?.takeIf { it.isNotBlank() }?.let { PlannerVersion(it) },
                    TestsetVersion(testsetVersion),
                )
            } catch (e: IllegalArgumentException) {
                throw UsageError(e.message ?: "Invalid version")
            }
        Tester(
                run =
                    RunMetadata.create(
                        version = plannerVersion,
                        application = application,
                        startedAt = Instant.now(),
                        referenceVersion = referencePlannerVersion,
                        testsetVersion = testset,
                    ),
                requestFileLoader = requestFileLoader,
                requestLoader = tester.requestLoader,
                travelPlanner = tester.travelPlanner,
                kpiCalculators = tester.kpiCalculators,
                resultsWriter = tester.resultsWriter,
                comparativeKpiCalculators = tester.comparativeKpiCalculators,
                resultsReader = tester.resultsReader,
                referenceVersion = referencePlannerVersion,
            )
            .run()
    }

    private fun loadConfig() =
        try {
            TrakpiConfigLoader.load(configFile = configFile, commandLineOverrides = commandLineOverrides)
        } catch (e: IllegalArgumentException) {
            throw UsageError(e.message ?: "Invalid configuration")
        }
}

internal class Testset : CliktCommand(name = "testset") {
    override fun help(context: Context) = "Prepare and inspect testsets — the versioned request sets a planner is tested against."

    override fun run() = Unit

    class List(private val config: TestsetConfig<*>?) : CliktCommand(name = "list") {
        override fun help(context: Context) = "List the available testset versions for this planner's api."

        override fun run() {
            val config = config ?: throw UsageError("Testsets are not configured for this planner.")
            val store = config.store ?: throw UsageError("No testset store configured for this planner.")
            val versions = store.versions(config.api)
            if (versions.isEmpty()) echo("No testsets for api '${config.api}'.")
            else versions.forEach { echo(it.value) }
        }
    }

    class Prepare<T>(private val config: TestsetConfig<T>?) : CliktCommand(name = "prepare") {
        override fun help(context: Context) = "Prepare a new testset: source raw requests, clean them, and store them under a version label (today's date by default)."

        private val version: String? by
            option("--version", help = "Version label for the new testset; defaults to today's date (UTC), e.g. 2026-08-11")

        override fun run() {
            val config = config ?: throw UsageError("Testsets are not configured for this planner.")
            val source = config.source ?: throw UsageError("No testset source configured for this planner.")
            val codec = config.codec ?: throw UsageError("No request codec configured for this planner.")
            val store = config.store ?: throw UsageError("No testset store configured for this planner.")
            val testsetVersion =
                try {
                    TestsetVersion(version ?: LocalDate.now(ZoneOffset.UTC).toString())
                } catch (e: IllegalArgumentException) {
                    throw UsageError(e.message ?: "Invalid version")
                }
            val testset = TestsetBuilder(source, codec, config.transforms, store).prepare(config.api, testsetVersion)
            echo("Prepared testset ${testset.api}/${testset.version}: ${testset.requests.size} request(s).")
        }
    }
}
