#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

# Defaults (overridden by launchd plist EnvironmentVariables)
export DUCKDB_PATH="${DUCKDB_PATH:-$HOME/.jvm-daily/jvm-daily.duckdb}"
export OUTPUT_DIR="${OUTPUT_DIR:-$HOME/.jvm-daily/output}"
export JOBRUNR_STORE="${JOBRUNR_STORE:-$HOME/.jvm-daily/jobrunr}"
export VIEWER_PORT="${VIEWER_PORT:-8888}"

mkdir -p "$OUTPUT_DIR" "$JOBRUNR_STORE" "$(dirname "$DUCKDB_PATH")"

# Start the article viewer in a restart loop so it can be restarted independently
# (kill the process with: pkill -f serve.py — the loop below will revive it)
(
    while true; do
        python3 "$ROOT_DIR/viewer/serve.py" "$VIEWER_PORT" || true
        echo "[daemon] viewer exited — restarting in 1s"
        sleep 1
    done
) &

# Start the JVM daemon (JobRunr scheduler + dashboard)
exec "$ROOT_DIR/app/build/install/app/bin/app"
