# Trakπ

Trakπ is a library and CLI tool for testing and tracking quality of travel planners. It can be used to track
regressions, performance or any other _key performance indicators_ (KPIs) you might need. The tool is developed to be
entirely generic, which means you can use it to test any travel planner you like, provided you implement the necessary
adapters. Trakπ exposes a small SPI for this purpose, and includes a command line interface, so to use it, include the
library in your project, implement the adapters and wire up the CLI. A reference implementation made for testing
[OpenTripPlanner](https://github.com/opentripplanner/OpenTripPlanner) is provided in reference/otp.

### Why the name Trakπ?

- It is short for **Trak**ing **P**erformance **I**ndicators, with π standing in for "PI".
- Pi (π) in mathematics is the ratio of a circle's circumference to its diameter. It does not change when
  the diameter changes, like the goal of the Trakπ tool: we want to measure values/KPIs that don't change
  but that describe important aspects of the system under test.

## User guide
TODO

## Development
Requires JDK 25+ and Maven. The repository holds several independent builds — `core` (the library),
the `storage/*` adapters, and `reference/otp`. The downstream builds resolve `core` from your local
Maven repository, so build `core` first:

```bash
mvn -f core/pom.xml install
mvn -f storage/file/pom.xml package
mvn -f reference/otp/pom.xml package
```

Test a single core module with `-pl`:

```bash
mvn -f core/pom.xml -pl tester test
```

### Project layout
The repository contains several independent Maven builds: `core` is a multi-module reactor; each
`storage/*` adapter and `reference/otp` are standalone builds that depend on the published `core`.

```
core/
  common          shared value types (planner and testset versions)
  tester          runs tests against an already-started planner
  testset         builds and stores testsets — the versioned request sets a planner is tested against
  orchestrator    starts and stops the planner
  trakpi          the library: command-line surface and public entry point (runTrakpi)
storage/
  file            file-based ResultsWriter that writes each result as a JSON file
  bigquery        streams KPI metrics to BigQuery
  gcs             archives raw requests/responses and stores testsets in GCS
reference/
  otp             executable reference implementation for OpenTripPlanner
```

### Running the OTP reference
To use the OTP reference (`reference/otp`) with a local build of trakpi.core, build `core` first (`mvn -f core/pom.xml install`), then see [README](reference/otp/README.md).

## Goals

Many test tools validate the result by matching the result to an expected value. However, this approach does not scale well to a large number of test cases, and false positives are common with a high maintenance cost. This tool is designed to have zero test-case maintenance cost. Therefore;

- We instead compute _Key Performance Indicators (KPIs)_ for each test case/test run. These can be compared with a reference (production) and tracked over time. A KPI can be generic (response time, HTTP status code, number of graphql errors) or query specific (minimum number of transfers in the result of a trip planning call, name matches when looking up entities, findXById).
- What is the _best_ itinerary in a given case is subjective. So, instead of comparing two itineraries, we can decompose it and compare key component values instead, like number-of-transfers, walk-distance, operator-spread, minimum-waiting-time, etc.
- By tracking KPIs over time, we can discover changes caused by bugs and changes in data. We can also measure improvements in quality.


## Key Performance Indicators (KPIs)
We can compute KPIs for each test case and then compare average and standard deviation for each test run in a selected set of samples. Here is a list of possible KPIs that would be interesting
- Success based on dynamic criteria (dynamic criteria?)
- Number of transfers
- Walk distance
- Minimum waiting time
- Minimum travel time
- Earliest arrival time (after search start time)
- Latest departure time (arrive-by search)
- Number of errors (GraphQL errors)
- Number of itineraries returned (is that useful?)
  - Why: The timeline of development of itinerary counts can tell us if a change caused more or fewer itineraries.
  - This doesn't say anything about whether a change is good or bad, but it 
- Response time for successful requests (+)
- Contains the fastest alternative
- Contains the most cost effective alternative 
- % of pareto optimal results

## Use Cases
The tool can be used in many use cases/user scenarios: 
1. Regression testing, and monitoring quality over time <- pri 1
2. Performance testing, and monitoring response times over time <- pri 2
3. Tune Travel Planner configuration
4. Verify quality threshold in an integration chain(as part of continuous integration system)
5. Compare different travel planners
6. Compare special use cases like Accessibility, Mode-specific results, or Operator/Feed existence 

## Inputs
For a single run, the following inputs are required. They can be configured either with a configuration file or with
command-line parameters. Command-line parameters always override what is given in the configuration file.
1. Test cases (travel requests and more). Identified by an id.
2. Street and transit data
3. Planner (given by name)
4. Planner version (e.g. a specific commit hash)
5. Persistence configuration (e.g. a file path, db connection string, or cloud storage connection string)

## Usage - Running a test
`trakpi test` runs the requests against an already-running planner:

```bash
trakpi test --version A --testset-version <label>
```

To let the test manage the planner itself — start it first and stop it when done, even if the test fails —
add `--start` and `--stop-on-completion`:

```bash
trakpi test --version A --testset-version <label> --start --stop-on-completion
```

Or drive the lifecycle by hand with `start`/`stop`, e.g. to keep the planner up across several tests:

```bash
trakpi start --version A
trakpi test  --version A --testset-version <label>
trakpi test  --version A --testset-version <label>   # again, against the same running planner
trakpi stop  --version A
```

Only a single instance can be started at a time.

## Usage - Preparing a testset
A testset is the versioned set of requests a planner is tested against — its label is passed to
`trakpi test --testset-version <label>`. Build and inspect testsets with:

```bash
# Source raw requests, clean them, and store them under a version label
trakpi testset prepare --version 2026-07-21

# List the stored testset versions
trakpi testset list
```

Where requests come from, the transforms applied while cleaning them, and where testsets are stored
are all supplied by the planner integration — see the integration's own docs (e.g. the OTP
reference) for how they are configured.

## Usage - Inspecting results
Each `trakpi test` run writes one JSON file per request to `results/<runId>/<requestId>.json` (the
`FileResultsWriter` default; the output location is configured by the integration — see its docs).
Each file holds the
run metadata, the raw planner response, and the computed KPIs under `kpis`. Read them however you
like — [`jq`](https://jqlang.github.io/jq/) is handy for quick aggregates. For example, to average
the `routingTimeMs` KPI across a run:

```bash
# Slurp every result file in the run into one array, then average the KPI.
# `numbers` skips files where the KPI is absent, so `add` never chokes on a null.
jq -s '[.[].kpis.routingTimeMs | numbers] | add / length' results/<runId>/*.json
```

## Inputs - requests
Each test run executes a set of requests. Where those requests come from is up to the integration: a
`spi.RequestFileLoader` supplies them, and an implementation may read them from a local folder, a
cloud bucket, a database table, or anywhere else. Each is loaded as raw text — an id and a body —
without regard for how the request is formatted. The body is then handed to an `spi.RequestLoader`,
which parses it into a request in a format the `spi.TravelPlanner` supports.

## Outputs
Each test run stores the following outputs for each test case
1. Full raw outputs from the planner
2. Outputs from the planner mapped into the standardized format. (See section on Standardized format)
3. KPIs

## Usage - Analyzing results
Each test run stores the full outputs

```bash
# Look at the KPIs for version A
trakpi kpis --version A

# Compare the KPIs of A and B
trakpi diff --version A --baseline B
```


## Writing a planner adapter
An adapter is needed for each planner you wish to test against. By default, Trakπ comes with an adapter for
[OpenTripPlanner](https://github.com/opentripplanner/OpenTripPlanner). 

A planner must implement the following SPI:
* Start: Starts a planner and leaves it ready to start testing. No output.
* Stop: Stops and tears down the planner. No output.
* Test: Outputs raw outputs from the planner in an opaque text format.
* Mapping:
  * From and to standardized input (request) format
  * From and to standardized output format
* KPI computation:
  * From standardized outputs
  * From raw outputs (default: computed from raw outputs mapped to standardized format)

## Domain language
[Transmodel](https://transmodel-cen.eu/) language is used throughout the project whenever transit-specific terminology
is needed.

## Standardized input/output format
A test run outputs individual planning results in a standardized format, allowing you to compare different planners even
if they have different output formats.


## Visualizing results
TODO. Grafana.

## Usage with git
TODO.

High level usecase:
- Compare a git commit hash with a given baseline. 
- Test a sequence of commits
- Bisect with a commit range

## Multiple dimensions, drill-down and averaging
There are multiple dimensions to consider in tracking performance. Take for instance the response time KPI:
This KPI can be analyzed by drilling down into a cross-section of the data:
- Across planning requests: With e.g. 1000 requests in the sample dataset, the KPI can be analyzed for a single timestamp.
    - Why? This can show e.g. how different types of requests impact the response time. Some requests are naturally more expensive for a planner to resolve and by looking only at a single timestamp, we can discover those differences.
- Across "time" (e.g. planner versions by commit hashes): Honing in on a single KPI, it can be analyzed over time, to see e.g. how a request that hits a bottleneck in the planner has developed over time after applying various optimizations.

In addition to drilling down into a single dimension, we can also apply an average (or other aggregation like p95 or p99) across a dimension, e.g. view how response time has developed over time in general.
