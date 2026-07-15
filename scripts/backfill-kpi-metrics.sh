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
# Filter on `application IS NOT NULL` (or `success IS NOT NULL`) to select this backfilled era.
#
# Run once, after `terraform apply` has added the new columns.
#
# Usage: scripts/backfill-kpi-metrics.sh [project] [dataset] [table]
# Requires: bq (authenticated).

set -euo pipefail

PROJECT="${1:-ent-trakpiotp-tst}"
DATASET="${2:-kpi_tracking}"
TABLE="${3:-kpi_metrics_v1}"

bq query --project_id="$PROJECT" --use_legacy_sql=false "UPDATE \`$PROJECT.$DATASET.$TABLE\` SET application = 'otp', success = true, http_status_code = '200', http_status_class = '2xx', method = CASE WHEN kpi_name IN ('itineraryCount', 'minTransfers', 'routingTimeMs', 'fastestDurationSeconds') THEN 'trip' ELSE 'stopPlace' END WHERE application IS NULL"

echo "Backfilled application/success/http_status_*/method on $PROJECT.$DATASET.$TABLE."
