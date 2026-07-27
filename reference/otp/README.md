# Trakπ — OTP reference

The executable reference implementation of Trakπ for
[OpenTripPlanner](https://github.com/opentripplanner/OpenTripPlanner). It wires the OTP-specific
adapters to the trakpi CLI, and doubles as a worked example for implementing your own planner.

This is a **standalone build** that depends on a published `trakpi` library — it is not part of the
trakpi reactor, exactly like a third-party integration.

## The trakpi version
`pom.xml` pins the library through the `trakpi.version` property:

TODO: Currently this is not true. We are referencing -SNAPSHOT for now until we've published an actual version.
Once we do so, we'll add a dev profile for running with -SNAPSHOT.

- During development it is `1.0.0-SNAPSHOT`. A `-SNAPSHOT` is mutable and resolved from your local
  `~/.m2`, so every `mvn install` of the library is picked up here. Re-install the library after
  changing it, then rebuild this module.
- For a reproducible build against a release, set `trakpi.version` to a published version (or pass
  `-Dtrakpi.version=<x>` on the command line).

## Build and run
The `trakpi` library must be in your local Maven repository first. During development, install it
from the repo root, then build this module:

```bash
mvn -f reference/otp/pom.xml install
mvn -f reference/otp/pom.xml package
```

This produces a self-contained executable jar at `reference/otp/target/trakpi.jar`. Run it directly,
or via the `trakpi` launcher next to it:

```bash
java -jar reference/otp/target/trakpi.jar --help
reference/otp/trakpi --help
```

`--help` works on any subcommand too, e.g. `reference/otp/trakpi test --help`.

## Configuration

All wiring is driven by environment variables, read in `Main.kt`. None are required for a local
`test` run against a file-based results directory; the rest opt into BigQuery/GCS.

| Variable | Controls | Default |
| --- | --- | --- |
| `TRAKPI_RESULTS_DIR` | Directory for local JSON result files, used when KPIs are not streamed to BigQuery. | `results` |
| `TRAKPI_BQ_PROJECT` | GCP project to stream KPI results into BigQuery. Unset → results are written as local files instead. | _(unset)_ |
| `TRAKPI_BQ_DATASET` | BigQuery dataset for KPI results. | `kpi_tracking` |
| `TRAKPI_BQ_TABLE` | BigQuery table for KPI results. | `kpi_metrics_v1` |
| `TRAKPI_GCS_BUCKET` | GCS bucket. When set: each result's raw request/response is archived there (and read back for `--reference-version` comparisons), **and** prepared testsets are stored there. | _(unset)_ |
| `TRAKPI_TESTSET_DIR` | Local directory for prepared testsets, used when `TRAKPI_GCS_BUCKET` is unset. | `testsets` |
| `TRAKPI_REQUESTS_ENV` | Entur environment whose prod request log seeds `testset prepare`: `prd` or `tst`. Unset → `testset prepare` has no source. | _(unset)_ |
| `TRAKPI_REQUESTS_SAMPLE_SIZE` | How many requests `testset prepare` samples. | `1000` |

BigQuery and GCS authenticate with Application Default Credentials, which can be set up with
`gcloud auth application-default login`.

## Preparing a testset

`trakpi testset prepare --version <label>` builds the versioned request set that `trakpi test
--testset-version <label>` later exercises.

```bash
export TRAKPI_REQUESTS_ENV=prd
export TRAKPI_GCS_BUCKET=ent-trakpiotp-tst-trakpi-archive
reference/otp/trakpi testset prepare --version 2026-07-21
reference/otp/trakpi testset list
```

## Request sources
The OTP reference supplies test requests through two `spi.RequestFileLoader` implementations. A
local `test` run reads them from a directory, where each file holds one request and its filename
(without extension) is the request id. When a GCS bucket is configured, requests are read from the
prepared testset stored there instead.

## Run from source
To run without repackaging (the library still needs to be installed first):

```bash
mvn -f reference/otp/pom.xml compile exec:java -Dexec.mainClass=org.opentripplanner.trakpi.otp.MainKt -Dexec.args="--help"
```

### Run after modifying trakpi.core
If you change the upstream trakpi.core during local development, the following command will run after first compiling trakpi.core
```bash
mvn -q -f core/pom.xml install -DskipTests && mvn -q -f reference/otp/pom.xml compile exec:java -Dexec.mainClass=org.opentripplanner.trakpi.otp.MainKt -Dexec.args="test --version A"
```

### IDE
In an IDE, just run `main` in this module.
