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

Start the OTP pod. Note: we set the name to `trakpi-otp-manual`. This name must not collide with other running pods in the cluster.
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

Run the test. Results are stored in `./results-a`.
```
OTP_ENDPOINT=http://localhost:8080/otp/transmodel/v3 TRAKPI_RESULTS_DIR=./results-a reference/otp/trakpi test --version <version-a> --testset-version dts-test --set requests.dir=./local-testsets/transmodel/dts-test
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
Run the test. Results are stored in `./results-b`.
```
OTP_ENDPOINT=http://localhost:8081/otp/transmodel/v3 TRAKPI_RESULTS_DIR=./results-b reference/otp/trakpi test --version <version-b> --testset-version dts-test --set requests.dir=./local-testsets/transmodel/dts-test
```

### Compare and analyze
Test results are stored in json format. To compare and analyze the test results, we use the `jq` "json-query" command line tool.

Here are some examples taking version A as a baseline and comparing B against it:

ItineraryCount:
```
find ./results-a -name '*.json' -exec cat {} + | jq -n 'reduce inputs as $r ({}; if $r.kpis.itineraryCount!=null then .[$r.requestId]=$r.kpis.itineraryCount else . end)' > /tmp/a-counts.json
```

ItineraryCount:
```
find ./results-b -name '*.json' -exec cat {} + | jq -n --slurpfile a /tmp/a-counts.json '$a[0] as $base | reduce inputs as $r ({more:0,equal:0,fewer:0,onlyB:0,aTotal:0,bTotal:0}; if $r.kpis.itineraryCount==null then . else ($base[$r.requestId]) as $x | if $x==null then .onlyB+=1 else .bTotal+=$r.kpis.itineraryCount | .aTotal+=$x | (if $r.kpis.itineraryCount>$x then .more+=1 elif $r.kpis.itineraryCount==$x then .equal+=1 else .fewer+=1 end) end end)'
```
`more`/`equal`/`fewer`: requests where B returned more/same/fewer itineraries than A.
`aTotal`/`bTotal`: itinerary totals across shared requests. `onlyB`: requests A has no baseline for.

### Teardown

Delete both pods and services.
```
kubectl -n trakpiotp delete pod/trakpi-otp-manual svc/trakpi-otp-manual pod/trakpi-otp-manual-b svc/trakpi-otp-manual-b --ignore-not-found
```

Both manual OTP pods run at once, each requesting 24Gi/4 CPU, so make sure the cluster has room for
both and run outside the nightly window (~04:00–05:00 Oslo).
