package org.opentripplanner.trakpi

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
import org.opentripplanner.trakpi.tester.spi.DrillDownRenderer
import org.opentripplanner.trakpi.tester.spi.ResultsReader
import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.RunReader
import org.opentripplanner.trakpi.tester.spi.RunRef
import org.opentripplanner.trakpi.testset.TestsetSource
import org.opentripplanner.trakpi.testset.TestsetStore
import org.opentripplanner.trakpi.tester.spi.TravelPlanner
import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest

/**
 * The runtime side of a planner integration: what the `test` and `drill` commands need to run a planner
 * build, score its responses, and drill down into one. (The `start`/`stop` lifecycle commands need no
 * configuration.) [runReader] and [drillDownRenderer] back `drill`: the former reads which runs exercised a
 * request, the latter renders one run's response comparison (both are planner-agnostic to the command —
 * only [drillDownRenderer]'s output is planner-specific).
 */
class TesterConfig<R : TravelPlannerRequest>(
    val requestLoader: RequestLoader<R>,
    val travelPlanner: TravelPlanner<R>,
    val kpiCalculators: List<KPICalculator>,
    val resultsWriter: ResultsWriter,
    val comparativeKpiCalculators: List<ComparativeKPICalculator> = emptyList(),
    val resultsReader: ResultsReader? = null,
    val requestFileLoader: RequestFileLoader? = null,
    val runReader: RunReader? = null,
    val drillDownRenderer: DrillDownRenderer? = null,
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
    /** Desired number of requests in the prepared testset; the builder stops at this count. */
    val targetSize: Int? = null,
)

/**
 * Runs the trakpi command-line interface.
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
            Test(application, tester, orchestrator),
            Drill(tester),
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
    private val orchestrator: PlannerOrchestrator?,
) : VersionedCommand("test") {
    override fun help(context: Context) =
        "Run a test against a planner."

    private val configFile: Path? by
        option("--config", help = "Path to a trakpi config file (.properties)").path(mustExist = true, canBeDir = false)
    private val commandLineOverrides: Map<String, String> by
        option("--set", help = "Override a config value, e.g. --set requests.dir=<path> (repeatable)").associate()
    private val referenceVersion: String? by
        option("--reference-version", help = "Baseline version to compare against")
    private val testsetVersion: String by
        option("--testset-version", help = "Label of the request set being exercised").required()
    private val start: Boolean by option("--start", help = "Start the planner before testing").flag()
    private val stopOnCompletion: Boolean by
        option("--stop-on-completion", help = "Stop the planner after testing, even if it fails").flag()
    private val origin: String by
        option(
                "--origin",
                help = "How this run was triggered, e.g. nightly, manual, local. Labels every result so a dashboard can separate e.g. scheduled from one-off runs",
                envvar = "TRAKPI_RUN_ORIGIN",
            )
            .default("local")
    private val label: String? by
        option("--label", help = "Optional tag grouping this run with a named experiment", envvar = "TRAKPI_RUN_LABEL")

    // --version identifies the run: it labels every result and keys the archived responses a later run
    // compares against.
    override fun run() {
        val tester = tester ?: throw UsageError("Testing is not configured for this planner.")
        if ((start || stopOnCompletion) && orchestrator == null)
            throw UsageError("Orchestration is not configured for this planner.")
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
        try {
            if (start) orchestrator!!.start(plannerVersion, null)
            Tester(
                    run =
                        RunMetadata.create(
                            version = plannerVersion,
                            application = application,
                            startedAt = Instant.now(),
                            referenceVersion = referencePlannerVersion,
                            testsetVersion = testset,
                            origin = origin,
                            label = label?.takeIf { it.isNotBlank() },
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
        } finally {
            if (stopOnCompletion) orchestrator!!.stop(plannerVersion)
        }
    }

    private fun loadConfig() =
        try {
            TrakpiConfigLoader.load(configFile = configFile, commandLineOverrides = commandLineOverrides)
        } catch (e: IllegalArgumentException) {
            throw UsageError(e.message ?: "Invalid configuration")
        }
}

/**
 * Drills down into a single request. With no run, it prints the every run that exercised the
 * request and the KPIs it recorded. With `--run` (or an explicit `--version`/`--reference-version`/`--testset-version`
 * triple), it drills down to one run's comparison output and renders the candidate-vs-reference responses via the
 * planner's [DrillDownRenderer].
 */
internal class Drill(private val tester: TesterConfig<*>?) : CliktCommand(name = "drill") {
    override fun help(context: Context) =
        "Drill down into a request: the runs that exercised it, or one run's response comparison."

    private val requestId: String by argument(help = "The request's external correlation id")
    private val run: String? by option("--run", help = "Drill down to a single run's comparison output for this request")
    private val versionOpt: String? by
        option("--version", help = "Candidate build version, to drill down without a run lookup (needs --testset-version)")
    private val referenceVersionOpt: String? by option("--reference-version", help = "Reference build version for the drill-down")
    private val testsetOpt: String? by option("--testset-version", help = "Testset version for the drill-down")

    override fun run() {
        val tester = tester ?: throw UsageError("Testing is not configured for this planner.")
        if (run != null || versionOpt != null || referenceVersionOpt != null || testsetOpt != null) drillDownIntoRun(tester)
        else printRunVector(tester)
    }

    private fun printRunVector(tester: TesterConfig<*>) {
        val reader = tester.runReader ?: throw UsageError("The run vector needs a run store; set TRAKPI_BQ_PROJECT.")
        val runs = reader.runsForRequest(requestId)
        if (runs.isEmpty()) {
            println("No runs recorded for request $requestId.")
            return
        }
        println("Runs for request $requestId (newest first):")
        println()
        for ((runRef, kpis) in runs) {
            println("  ${localTime(runRef.runTs)}   version ${runRef.version}   reference ${runRef.referenceVersion ?: "-"}   testset ${runRef.testsetVersion}")
            println("      run-id: ${runRef.runId}")
            val kpiLine = kpis.entries.sortedBy { it.key }.joinToString("   ") { (name, value) -> "$name=${formatKpi(value)}" }
            if (kpiLine.isNotEmpty()) println("      $kpiLine")
            println()
        }
        println("Drill down to one run with:  drill $requestId --run <run-id>")
    }

    private fun drillDownIntoRun(tester: TesterConfig<*>) {
        val reader = tester.resultsReader ?: throw UsageError("The response comparison needs the archive; set TRAKPI_GCS_BUCKET.")
        val renderer = tester.drillDownRenderer ?: throw UsageError("This planner does not support drilling down into responses.")
        val runRef = resolveRun(tester)
        val testset = TestsetVersion(runRef.testsetVersion)
        val candidate =
            reader.response(PlannerVersion(runRef.version), testset, requestId)
                ?: throw UsageError("No archived response for version ${runRef.version}, testset ${runRef.testsetVersion}, request $requestId.")
        val reference = runRef.referenceVersion?.let { reader.response(PlannerVersion(it), testset, requestId) }
        println("Request $requestId")
        println("Run ${localTime(runRef.runTs)}   candidate ${runRef.version}   reference ${runRef.referenceVersion ?: "(none)"}   testset ${runRef.testsetVersion}")
        println()
        renderer.render(candidate, reference).forEach(::println)
    }

    /**
     * The run to drill down into: from an explicit `--version`/`--reference-version`/`--testset-version`
     * triple, or by resolving a `--run` id through the run store.
     */
    private fun resolveRun(tester: TesterConfig<*>): RunRef {
        if (versionOpt != null || referenceVersionOpt != null || testsetOpt != null) {
            val version = versionOpt ?: throw UsageError("--version is required when drilling down without --run.")
            val testset = testsetOpt ?: throw UsageError("--testset-version is required when drilling down without --run.")
            return RunRef("$version@$testset", "(given)", version, referenceVersionOpt, testset)
        }
        val runId = run!!
        val reader =
            tester.runReader
                ?: throw UsageError("Resolving --run needs a run store; set TRAKPI_BQ_PROJECT, or pass --version/--reference-version/--testset-version.")
        return reader.resolveRun(runId) ?: throw UsageError("No run found with id $runId.")
    }

    private fun formatKpi(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    /**
     * Formats a [RunRef.runTs] UTC instant in the user's own time zone (from the environment via
     * [ZoneId.systemDefault]). Non-instant values (e.g. the "(given)" placeholder of a manual drill-down) are
     * passed through unchanged.
     */
    private fun localTime(runTs: String): String =
        runCatching { Instant.parse(runTs).atZone(ZoneId.systemDefault()).format(LOCAL_TIME) }.getOrDefault(runTs)

    private companion object {
        private val LOCAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")
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
            val testset = TestsetBuilder(source, codec, config.transforms, store, config.targetSize).prepare(config.api, testsetVersion)
            echo("Prepared testset ${testset.api}/${testset.version}: ${testset.requests.size} request(s).")
        }
    }
}
