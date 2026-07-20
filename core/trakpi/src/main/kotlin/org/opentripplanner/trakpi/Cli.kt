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
import org.opentripplanner.trakpi.testset.RequestCodec
import org.opentripplanner.trakpi.testset.TestsetBuilder
import org.opentripplanner.trakpi.tester.spi.ComparativeKPICalculator
import org.opentripplanner.trakpi.tester.spi.KPICalculator
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
 * Runs the trakpi command-line interface, wiring the CLI to the supplied planner implementations.
 * [api] names the request format the planner speaks (used to scope testsets); it defaults to
 * [application]. The `testset` command group is available when a [testsetStore] is supplied.
 */
fun <R : TravelPlannerRequest, T> runTrakpi(
    args: Array<String>,
    application: String,
    requestLoader: RequestLoader<R>,
    travelPlanner: TravelPlanner<R>,
    kpiCalculators: List<KPICalculator>,
    resultsWriter: ResultsWriter,
    comparativeKpiCalculators: List<ComparativeKPICalculator> = emptyList(),
    resultsReader: ResultsReader? = null,
    api: String = application,
    testsetSource: TestsetSource? = null,
    requestCodec: RequestCodec<T>? = null,
    transforms: List<RequestTransform<T>> = emptyList(),
    testsetStore: TestsetStore? = null,
) {
    val orchestrator = Orchestrator()
    val commands =
        buildList {
            add(Prepare(orchestrator))
            add(Start(orchestrator))
            add(Stop(orchestrator))
            add(Test(application, requestLoader, travelPlanner, kpiCalculators, resultsWriter, comparativeKpiCalculators, resultsReader))
            add(
                Testset()
                    .subcommands(
                        Testset.List(api, testsetStore),
                        Testset.Prepare(api, testsetSource, requestCodec, transforms, testsetStore),
                    )
            )
        }
    Trakpi().subcommands(commands).main(args)
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
    private val comparativeKpiCalculators: List<ComparativeKPICalculator>,
    private val resultsReader: ResultsReader?,
) : VersionedCommand("test") {
    override fun help(context: Context) = "Run a test. Assumes the planner is running and ready."

    private val configFile: Path? by
        option("--config", help = "Path to a trakpi config file (.properties)").path(mustExist = true, canBeDir = false)
    private val overrides: Map<String, String> by
        option("--set", help = "Override a config value, e.g. --set requests.dir=<path> (repeatable)").associate()
    private val referenceVersion: String? by
        option("--reference-version", help = "Baseline version to compare against")
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
                comparativeKpiCalculators = comparativeKpiCalculators,
                resultsReader = resultsReader,
                referenceVersion = referenceVersion?.takeIf { it.isNotBlank() },
            )
            .run()
    }
}

internal class Testset : CliktCommand(name = "testset") {
    override fun help(context: Context) = "Prepare and inspect testsets — the versioned request sets a planner is tested against."

    override fun run() = Unit

    class List(private val api: String, private val store: TestsetStore?) : CliktCommand(name = "list") {
        override fun help(context: Context) = "List the available testset versions for this planner's api."

        override fun run() {
            val store = store ?: throw UsageError("No testset store configured for this planner.")
            val versions = store.versions(api)
            if (versions.isEmpty()) echo("No testsets for api '$api'.")
            else versions.forEach { echo(it) }
        }
    }

    class Prepare<T>(
        private val api: String,
        private val source: TestsetSource?,
        private val codec: RequestCodec<T>?,
        // kotlin.collections.List: the nested `List` command above shadows the bare name in this scope.
        private val transforms: kotlin.collections.List<RequestTransform<T>>,
        private val store: TestsetStore?,
    ) : CliktCommand(name = "prepare") {
        override fun help(context: Context) = "Prepare a new testset: source raw requests, clean them, and store them under --version."

        private val version: String by option("--version", help = "Version label for the new testset, e.g. the ISO date").required()

        override fun run() {
            val source = source ?: throw UsageError("No testset source configured for this planner.")
            val codec = codec ?: throw UsageError("No request codec configured for this planner.")
            val store = store ?: throw UsageError("No testset store configured for this planner.")
            val testset = TestsetBuilder(source, codec, transforms, store).prepare(api, version)
            echo("Prepared testset ${testset.api}/${testset.version}: ${testset.requests.size} request(s).")
        }
    }
}
