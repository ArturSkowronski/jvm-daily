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

# Start the JVM daemon (JobRunr scheduler + REST API + viewer)
exec "$ROOT_DIR/app/build/install/app/bin/app"
