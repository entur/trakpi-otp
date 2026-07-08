#!/usr/bin/env bash
#
# Downloads the nightly `trakpi-results` artifacts from GitHub Actions into a local folder so that
# `trakpi analyze` can read a multi-run history. This is the only piece that knows about GitHub;
# trakpi itself just reads the resulting directory tree.
#
# Each run is extracted into its own `<output-dir>/<workflow-run-id>/` subdirectory. Keying on the
# workflow run id (always unique) keeps runs from colliding no matter how the artifact is laid out
# internally, and lets re-runs fetch only runs not already downloaded.
#
# Usage: scripts/download-artifacts.sh [output-dir]   (default: results)
# Requires: gh (authenticated), jq.

set -euo pipefail

REPO="opentripplanner/TrakPi"
ARTIFACT_NAME="trakpi-results"
OUT="${1:-results}"

mkdir -p "$OUT"

echo "Fetching $ARTIFACT_NAME artifacts from $REPO into $OUT/ ..."
gh api "repos/$REPO/actions/artifacts" --paginate \
  --jq ".artifacts[] | select(.name == \"$ARTIFACT_NAME\" and .expired == false) | .workflow_run.id" |
  sort -un |
  while read -r run_id; do
    dest="$OUT/$run_id"
    if [ -d "$dest" ]; then
      echo "  run $run_id (already present, skipping)"
      continue
    fi
    echo "  run $run_id"
    gh run download "$run_id" -R "$REPO" -n "$ARTIFACT_NAME" -D "$dest" || {
      echo "    (skipped: no downloadable artifact)"
      rm -rf "$dest"
    }
  done

echo "Done. Analyze with: trakpi analyze --loaderargs \"--results-dir $OUT\""
