#!/usr/bin/env bash
#
# One-off backfill for kpi_metrics rows written before the run/request dimension columns existed.
# The raw responses that could have reconstructed these columns lived only in the nightly artifacts,
# which are gone, but the historical (OTP nightly) data was uniform enough to recover them from what
# we know about it:
#   - application: always "otp".
#   - success / http_status_*: every nightly request succeeded over HTTP 200 ("2xx").
#   - method: "trip" for rows carrying a trip KPI (itineraryCount, minTransfers, routingTimeMs,
#     fastestDurationSeconds); the rest carry departureCount and were "stopPlace" requests.
#   - testset_version: every historical run exercised the same initial request set, so they all get
#     the initial testset_version (INITIAL_TESTSET, an ISO date). This MUST match the --testset-version
#     the nightly passes for the same request set, or the historical and ongoing rows will look like
#     different testsets. Going forward, testset_version is the ISO date the request data was extracted.
# Filter on `application IS NOT NULL` (or `success IS NOT NULL`) to select this backfilled era.
#
# Run once, after `terraform apply` has added the new columns.
#
# Usage: scripts/backfill-kpi-metrics.sh [project] [dataset] [table] [initial_testset_version]
# Requires: bq (authenticated).

set -euo pipefail

PROJECT="${1:-ent-trakpiotp-tst}"
DATASET="${2:-kpi_tracking}"
TABLE="${3:-kpi_metrics_v1}"
INITIAL_TESTSET="${4:-2026-06-24}"

bq query --project_id="$PROJECT" --use_legacy_sql=false "UPDATE \`$PROJECT.$DATASET.$TABLE\` SET application = 'otp', success = true, http_status_code = '200', http_status_class = '2xx', testset_version = '$INITIAL_TESTSET', method = CASE WHEN kpi_name IN ('itineraryCount', 'minTransfers', 'routingTimeMs', 'fastestDurationSeconds') THEN 'trip' ELSE 'stopPlace' END WHERE application IS NULL"

echo "Backfilled application/success/http_status_*/method/testset_version ('$INITIAL_TESTSET') on $PROJECT.$DATASET.$TABLE."
