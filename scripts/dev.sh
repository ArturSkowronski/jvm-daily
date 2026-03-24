#!/usr/bin/env bash
# Dev mode: auto-rebuild JVM on .kt changes, auto-rebuild SvelteKit on viewer changes.
# Usage: ./scripts/dev.sh
# Ctrl-C to stop everything.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

export DUCKDB_PATH="${DUCKDB_PATH:-$HOME/.jvm-daily/jvm-daily.duckdb}"
export OUTPUT_DIR="${OUTPUT_DIR:-$HOME/.jvm-daily/output}"
export JOBRUNR_STORE="${JOBRUNR_STORE:-$HOME/.jvm-daily/jobrunr}"
export VIEWER_PORT="${VIEWER_PORT:-8888}"

mkdir -p "$OUTPUT_DIR" "$JOBRUNR_STORE" "$(dirname "$DUCKDB_PATH")"

# Stop launchd service first (KeepAlive would otherwise respawn killed processes)
PLIST="$HOME/Library/LaunchAgents/com.local.jvm-daily.plist"
if launchctl list com.local.jvm-daily &>/dev/null; then
    launchctl unload "$PLIST" 2>/dev/null && echo "[dev] launchd service unloaded" || true
    sleep 1
fi

# Stop any leftover processes
pkill -f "jvm.daily.AppKt" 2>/dev/null && echo "[dev] stopped existing JVM" || true
sleep 1

# Re-enable launchd service when dev mode exits
trap 'echo; echo "[dev] restoring launchd service..."; launchctl load "$PLIST" 2>/dev/null; echo "[dev] done"' EXIT

echo "Dev mode — watching for changes"
echo "  Viewer : http://localhost:$VIEWER_PORT"
echo "  Press Ctrl-C to stop"
echo ""

# Build SvelteKit viewer
echo "[dev] Building SvelteKit viewer..."
(cd "$ROOT_DIR/viewer-svelte" && npm run build --silent)
echo "[dev] Viewer build OK"

# Build JVM
"$ROOT_DIR/gradlew" -p "$ROOT_DIR" installDist -q
echo "[dev] JVM build OK"

# Start JVM daemon (serves REST API + SvelteKit static files)
"$ROOT_DIR/app/build/install/app/bin/app" &
JVM_PID=$!
echo "[dev] JVM started (PID $JVM_PID)"

cleanup() {
    echo -e "\n[dev] shutting down"
    kill $JVM_PID 2>/dev/null
    wait $JVM_PID 2>/dev/null
}
trap 'cleanup; echo "[dev] restoring launchd service..."; launchctl load "$PLIST" 2>/dev/null; echo "[dev] done"' EXIT

# Watch for changes
while true; do
    sleep 2

    # Check Kotlin sources
    NEWEST_KT=$(find "$ROOT_DIR/app/src" -name '*.kt' -newer "$ROOT_DIR/app/build/install/app/lib/app.jar" 2>/dev/null | head -1)
    if [ -n "$NEWEST_KT" ]; then
        echo "[dev] Kotlin changed — rebuilding..."
        kill $JVM_PID 2>/dev/null; wait $JVM_PID 2>/dev/null
        "$ROOT_DIR/gradlew" -p "$ROOT_DIR" installDist -q && echo "[dev] Build OK" || echo "[dev] Build FAILED"
        "$ROOT_DIR/app/build/install/app/bin/app" &
        JVM_PID=$!
        echo "[dev] JVM restarted (PID $JVM_PID)"
    fi

    # Check SvelteKit sources
    NEWEST_SVELTE=$(find "$ROOT_DIR/viewer-svelte/src" \( -name '*.svelte' -o -name '*.ts' \) -newer "$ROOT_DIR/viewer-svelte/build/index.html" 2>/dev/null | head -1)
    if [ -n "$NEWEST_SVELTE" ]; then
        echo "[dev] Svelte changed — rebuilding viewer..."
        (cd "$ROOT_DIR/viewer-svelte" && npm run build --silent) && echo "[dev] Viewer rebuild OK" || echo "[dev] Viewer build FAILED"
    fi
done
