#!/usr/bin/env bash
# Dev mode: auto-restart viewer on serve.py changes, auto-rebuild+restart JVM on .kt changes.
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
pkill -f "serve\.py"       2>/dev/null && echo "[dev] stopped existing viewer" || true
pkill -f "jvm.daily.AppKt" 2>/dev/null && echo "[dev] stopped existing JVM" || true
sleep 1

# Re-enable launchd service when dev mode exits
trap 'echo; echo "[dev] restoring launchd service..."; launchctl load "$PLIST" 2>/dev/null; echo "[dev] done"' EXIT

echo "Dev mode — watching for changes"
echo "  Viewer : http://localhost:$VIEWER_PORT"
echo "  Press Ctrl-C to stop"
echo ""

# Build once upfront to make sure binary is current
"$ROOT_DIR/gradlew" -p "$ROOT_DIR" installDist -q
echo "[dev] Initial build OK"

exec python3 - "$ROOT_DIR" "$VIEWER_PORT" <<'EOF'
import os, sys, time, subprocess, glob, signal

root, viewer_port = sys.argv[1], sys.argv[2]

VIEWER_SCRIPT = os.path.join(root, 'viewer', 'serve.py')
APP_BIN       = os.path.join(root, 'app', 'build', 'install', 'app', 'bin', 'app')
GRADLEW       = os.path.join(root, 'gradlew')
KT_PATTERN    = os.path.join(root, 'app', 'src', 'main', 'kotlin', '**', '*.kt')

def mtime_map(paths):
    return {p: os.stat(p).st_mtime for p in paths if os.path.exists(p)}

def kt_files():
    return glob.glob(KT_PATTERN, recursive=True)

def start_viewer():
    p = subprocess.Popen(['python3', VIEWER_SCRIPT, viewer_port])
    print(f'[dev] viewer started (PID {p.pid})')
    return p

def start_jvm():
    p = subprocess.Popen([APP_BIN], env=os.environ)
    print(f'[dev] JVM started (PID {p.pid})')
    return p

viewer = start_viewer()
jvm    = start_jvm()

viewer_mtimes = mtime_map([VIEWER_SCRIPT])
kt_mtimes     = mtime_map(kt_files())

def shutdown(sig, frame):
    print('\n[dev] shutting down')
    viewer.terminate()
    jvm.terminate()
    sys.exit(0)

signal.signal(signal.SIGINT,  shutdown)
signal.signal(signal.SIGTERM, shutdown)

while True:
    time.sleep(1)

    # ── Viewer ───────────────────────────────────────────────────────────────
    new = mtime_map([VIEWER_SCRIPT])
    if new != viewer_mtimes:
        viewer_mtimes = new
        print('[dev] serve.py changed — restarting viewer')
        viewer.terminate(); viewer.wait()
        viewer = start_viewer()

    # ── JVM sources ──────────────────────────────────────────────────────────
    new = mtime_map(kt_files())
    if new != kt_mtimes:
        # Debounce: wait for burst of saves to settle
        time.sleep(2)
        kt_mtimes = mtime_map(kt_files())
        print('[dev] Kotlin sources changed — rebuilding...')
        jvm.terminate(); jvm.wait()
        result = subprocess.run([GRADLEW, 'installDist', '-q'], cwd=root)
        if result.returncode == 0:
            print('[dev] Build OK — restarting JVM')
        else:
            print('[dev] Build FAILED — fix errors and save again to retry')
        jvm = start_jvm()
EOF
