#!/usr/bin/env bash
#
# One-off backfill of the runs table for the runs written before trakpi recorded run completions. Without
# it, dashboards that join on the runs table leave out every earlier run. It marks:
#   1. the test phase of every run that started before the cutoff, and
#   2. the compare phase of every such run that carries a comparative KPI.
# It assumes those runs are complete: delete a known partial run from the KPI table before running this.
# Rows are inserted only for (run_id, phase) pairs not already in the runs table, so a rerun is harmless.
#
# Usage: scripts/backfill-runs.sh <cutoff> [project] [dataset] [kpi table] [runs table]
#   cutoff: a BigQuery timestamp, e.g. "2026-11-10 12:00:00". Use the time the completion-recording
#           build was deployed, so runs started after it are left to record their own completion.
# Requires: bq (authenticated).

set -euo pipefail

CUTOFF="${1:?Usage: $0 <cutoff> [project] [dataset] [kpi table] [runs table]}"
PROJECT="${2:-ent-trakpiotp-tst}"
DATASET="${3:-kpi_tracking}"
KPI_TABLE="${4:-kpi_metrics_v1}"
RUNS_TABLE="${5:-runs_v1}"
KPIS="$PROJECT.$DATASET.$KPI_TABLE"
RUNS="$PROJECT.$DATASET.$RUNS_TABLE"

# The comparative KPIs of the OTP reference, written only by the compare phase.
COMPARATIVE_KPIS="'itinerariesMatchReference', 'itineraryCountMatchesReference'"

bq query --project_id="$PROJECT" --use_legacy_sql=false "
INSERT INTO \`$RUNS\` (run_id, phase, recorded_at)
WITH finished AS (
  SELECT run_id, 'test' AS phase, MAX(run_ts) AS recorded_at
  FROM \`$KPIS\` WHERE run_ts < TIMESTAMP('$CUTOFF') GROUP BY run_id
  UNION ALL
  SELECT run_id, 'compare' AS phase, MAX(run_ts) AS recorded_at
  FROM \`$KPIS\` WHERE run_ts < TIMESTAMP('$CUTOFF') AND kpi_name IN ($COMPARATIVE_KPIS) GROUP BY run_id
)
SELECT f.run_id, f.phase, f.recorded_at
FROM finished f
LEFT JOIN \`$RUNS\` r ON r.run_id = f.run_id AND r.phase = f.phase
WHERE r.run_id IS NULL"

echo "Backfilled run completions before $CUTOFF into $RUNS."
