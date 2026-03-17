#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
PLIST_TEMPLATE="$ROOT_DIR/launchd/com.local.jvm-daily.plist"
PLIST_DEST="$HOME/Library/LaunchAgents/com.local.jvm-daily.plist"
DATA_DIR="$HOME/.jvm-daily"

# ── Validate required env vars ────────────────────────────────────────────────
if [[ -z "${GEMINI_API_KEY:-}" ]]; then
    echo "ERROR: GEMINI_API_KEY is not set" >&2
    exit 1
fi
if [[ -z "${GITHUB_TOKEN:-}" ]]; then
    echo "ERROR: GITHUB_TOKEN is not set" >&2
    exit 1
fi

# ── Build the app ─────────────────────────────────────────────────────────────
echo "Building app..."
"$ROOT_DIR/gradlew" -p "$ROOT_DIR" :app:installDist

# ── Make daemon script executable ────────────────────────────────────────────
chmod +x "$SCRIPT_DIR/local-daemon.sh"

# ── Create data directories ───────────────────────────────────────────────────
mkdir -p "$DATA_DIR/logs" "$DATA_DIR/output" "$DATA_DIR/jobrunr"

# ── Generate plist from template ─────────────────────────────────────────────
sed \
    -e "s|__HOME__|$HOME|g" \
    -e "s|__REPO__|$ROOT_DIR|g" \
    -e "s|__GEMINI_API_KEY__|$GEMINI_API_KEY|g" \
    -e "s|__GITHUB_TOKEN__|$GITHUB_TOKEN|g" \
    "$PLIST_TEMPLATE" > "$PLIST_DEST"

chmod 600 "$PLIST_DEST"

# ── Load the service ──────────────────────────────────────────────────────────
# Unload first if already loaded (ignore errors if not loaded)
launchctl bootout "gui/$(id -u)/com.local.jvm-daily" 2>/dev/null || true

launchctl bootstrap "gui/$(id -u)" "$PLIST_DEST"

echo ""
echo "════════════════════════════════════════"
echo " JVM Daily service installed and started"
echo " Data dir  : $DATA_DIR"
echo " Logs      : $DATA_DIR/logs/"
echo " Viewer    : http://localhost:8888"
echo " Dashboard : http://localhost:8000/dashboard"
echo "════════════════════════════════════════"
echo ""
echo "Check status: ./scripts/service-status.sh"
