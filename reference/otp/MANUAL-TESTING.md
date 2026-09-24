# Running a manual test

This document explains how to run a test locally. We run trakpi commands locally, and test results are stored locally on
disk, but the planner itself (OTP) runs up in google cloud.

## What to know

- **`start` run locally holds at `waiting... otp`.** Its readiness probe hits the in-cluster DNS,
  which your laptop can't reach. Watch for `1/1 Running`, then Ctrl-C `start` — the pod stays up (a
  local pod has no owner reference).
- **`test` needs `OTP_ENDPOINT` set and the `TRAKPI_OTP_*` vars unset**, so it targets the
  port-forward instead of the in-cluster DNS.
- **`start --version` is the image tag** (`start` runs `${repo}:${version}`); **`test --version` is
  just a label** recorded on the results.

## Prerequisites


### Gcloud authentication
Make sure we are authenticated with gcloud and in targeting the right project
```
gcloud auth application-default login
```
```
gcloud container clusters get-credentials kub-ent-jp-tst-001 --region europe-west4 --project ent-kub-tst
```

### Build trakpi
Build the latest trakpi version from source
```
mvn -f core/pom.xml install -DskipTests && mvn -f reference/otp/pom.xml package
```

### Prepare testset
Prepare a testset once and reuse it across passes.

```
GOOGLE_CLOUD_PROJECT=ent-trakpiotp-tst TRAKPI_REQUESTS_ENV=prd TRAKPI_REQUESTS_SAMPLE_SIZE=5000 TRAKPI_TESTSET_DIR=./local-testsets reference/otp/trakpi testset prepare --version dts-test
```

## Comparing two versions

### Version A

Fill out `<otp-docker-repo>` with the repo containing the images and `<version-a>` with the otp version to test (e.g. v2.0.0-entur-180).
```
TRAKPI_OTP_IMAGE_REPO=<otp-docker-repo> TRAKPI_OTP_NAMESPACE=trakpiotp TRAKPI_OTP_POD_NAME=trakpi-otp-manual TRAKPI_OTP_SERVICE_NAME=trakpi-otp-manual TRAKPI_OTP_MEMORY=24000Mi TRAKPI_OTP_EPHEMERAL_STORAGE=30Gi TRAKPI_OTP_READINESS_TIMEOUT_SECONDS=5400 reference/otp/trakpi start --version <version-a>
```
Watch the pods. When it turns to `1/1 Running`, the pod is ready for testing.
```
kubectl -n trakpiotp get pods -w
```

Port-forward the pod from the cluster, allowing us to send requests to it from the local machine.
When you kill this terminal process (Ctrl+C), the port-forwarding stops, so keep it open until testing is done.
```
kubectl -n trakpiotp port-forward svc/trakpi-otp-manual 8080:8080
```

Confirm OTP is serving and the port-forward worked.
```
curl -s -X POST http://localhost:8080/otp/transmodel/v3 -H 'Content-Type: application/json' -H 'ET-Client-Name: entur-trakpi-dev' -d '{"query":"{ serverInfo { version } }"}'
```

Run the test. Results are stored under `./results/a` (both versions share one `./results` dir, each in
its own `--run-id` subdirectory, so `compare` can read them back).
```
OTP_ENDPOINT=http://localhost:8080/otp/transmodel/v3 TRAKPI_RESULTS_DIR=./results reference/otp/trakpi test --version <version-a> --run-id a --testset-version dts-test --set requests.dir=./local-testsets/transmodel/dts-test
```

### Version B

Start a second OTP pod alongside version A's, so both stay up while you compare. Use a different name
and a different local port. Fill out `<version-b>` with the other otp version.
```
TRAKPI_OTP_IMAGE_REPO=<otp-docker-repo> TRAKPI_OTP_NAMESPACE=trakpiotp TRAKPI_OTP_POD_NAME=trakpi-otp-manual-b TRAKPI_OTP_SERVICE_NAME=trakpi-otp-manual-b TRAKPI_OTP_MEMORY=24000Mi TRAKPI_OTP_EPHEMERAL_STORAGE=30Gi TRAKPI_OTP_READINESS_TIMEOUT_SECONDS=5400 reference/otp/trakpi start --version <version-b>
```
Watch the pods. When `trakpi-otp-manual-b` turns `1/1 Running`, it is ready for testing. Version A's pod keeps running.
```
kubectl -n trakpiotp get pods -w
```
Port-forward the second pod on local port 8081, so version A's forward on 8080 stays up. Keep it open until testing is done.
```
kubectl -n trakpiotp port-forward svc/trakpi-otp-manual-b 8081:8080
```
Confirm OTP is serving and the port-forward worked.
```
curl -s -X POST http://localhost:8081/otp/transmodel/v3 -H 'Content-Type: application/json' -H 'ET-Client-Name: entur-trakpi-dev' -d '{"query":"{ serverInfo { version } }"}'
```
Run the test. Results are stored under `./results/b`.
```
OTP_ENDPOINT=http://localhost:8081/otp/transmodel/v3 TRAKPI_RESULTS_DIR=./results reference/otp/trakpi test --version <version-b> --run-id b --testset-version dts-test --set requests.dir=./local-testsets/transmodel/dts-test
```

### Compare and analyze
Score version B against version A in a second pass. `compare` reads both runs' stored responses from
`./results` and writes the comparative KPIs (`itineraryCountMatchesReference`,
`itinerariesMatchReference`) into B's result files under `--run-id b`:
```
TRAKPI_RESULTS_DIR=./results reference/otp/trakpi compare --version <version-b> --reference-version <version-a> --testset-version dts-test --run-id b
```
`compare` runs offline against the stored responses — the OTP pods don't need to be up for it, so you
can tear them down first and, if you like, test the two versions one after another rather than side by
side.

Then aggregate the comparative KPIs with the `jq` "json-query" tool. For example, how many requests B
routed identically to A versus differently:
```
find ./results/b -name '*.json' -exec cat {} + | jq -n 'reduce inputs as $r ({match:0,differ:0}; ($r.kpis.itinerariesMatchReference) as $m | if $m==1 then .match+=1 elif $m==0 then .differ+=1 else . end)'
```
`match`/`differ`: requests where B's itineraries matched / differed from A's.

### Teardown

Delete both pods and services.
```
kubectl -n trakpiotp delete pod/trakpi-otp-manual svc/trakpi-otp-manual pod/trakpi-otp-manual-b svc/trakpi-otp-manual-b --ignore-not-found
```

Both manual OTP pods run at once, each requesting 24Gi/4 CPU, so make sure the cluster has room for
both and run outside the nightly window (~04:00–05:00 Oslo).
