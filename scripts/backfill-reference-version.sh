#!/usr/bin/env bash
#
# One-off backfill for the reference designation, from before the comparison keyed on a build version.
# The reference run dev_2026-07-15T143219… tested OTP build 2.10.0-entur-133 but recorded version "dev"
# (it ran before the workflow started resolving the real version from serverInfo). This:
#   1. Corrects that run's version to 2.10.0-entur-133 (and marks it) so a version-based reference
#      (--reference-version 2.10.0-entur-133) resolves to it.
#   2. Stamps reference_version on the runs that compared against it (those carrying the comparison KPI).
#
# Usage: scripts/backfill-reference-version.sh [project] [dataset] [table]
# Requires: bq (authenticated).

set -euo pipefail

PROJECT="${1:-ent-trakpiotp-tst}"
DATASET="${2:-kpi_tracking}"
TABLE="${3:-kpi_metrics_v1}"
FQ="$PROJECT.$DATASET.$TABLE"

REF_RUN_ID="dev_2026-07-15T143219.169295795Z"
REF_VERSION="2.10.0-entur-133"

# 1) The reference run tested build 2.10.0-entur-133 (recorded as "dev"). Correct its version and mark it.
bq query --project_id="$PROJECT" --use_legacy_sql=false \
  "UPDATE \`$FQ\` SET version = '$REF_VERSION', is_reference_version = TRUE WHERE run_id = '$REF_RUN_ID'"

# 2) Runs that carry the comparison KPI measured against that reference — record which build.
bq query --project_id="$PROJECT" --use_legacy_sql=false \
  "UPDATE \`$FQ\` SET reference_version = '$REF_VERSION' WHERE run_id IN (SELECT DISTINCT run_id FROM \`$FQ\` WHERE kpi_name = 'itineraryCountMatchesReference')"

echo "Backfilled reference designation ($REF_VERSION) on $FQ."
