#!/usr/bin/env bash
set -euo pipefail

# Self-update script for Raspberry Pi.
# Downloads the latest app-dist and app-config artifacts from the most recent
# successful Deploy workflow run on GitHub Actions.
#
# Cron: run before reddit-push (e.g., 5:15 AM, 15 min before 5:30 reddit push)
# Requires: gh CLI authenticated with a token that has actions:read scope

REPO="ArturSkowronski/jvm-daily"
PI_DIR="${PI_DIR:-$HOME/jvm-daily}"
STAMP_FILE="$PI_DIR/.last-deploy-run-id"
LOG="$PI_DIR/logs/self-update-$(date +%Y-%m-%d).log"
mkdir -p "$PI_DIR/logs"

log() { echo "[$(date)] $*" | tee -a "$LOG"; }

# Find the latest successful Deploy workflow run
RUN_ID=$(gh run list -R "$REPO" -w Deploy -L 10 \
    --json databaseId,conclusion,status \
    -q '[.[] | select(.status=="completed" and .conclusion=="success")] | .[0].databaseId' 2>/dev/null)

if [ -z "$RUN_ID" ] || [ "$RUN_ID" = "null" ]; then
    log "No successful Deploy run found. Skipping."
    exit 0
fi

# Check if we already deployed this run
if [ -f "$STAMP_FILE" ] && [ "$(cat "$STAMP_FILE")" = "$RUN_ID" ]; then
    log "Already up to date (run $RUN_ID). Skipping."
    exit 0
fi

log "New deploy available: run $RUN_ID. Updating..."

# Download artifacts to temp dir
TMP=$(mktemp -d)
trap "rm -rf $TMP" EXIT

log "Downloading app-dist..."
gh run download "$RUN_ID" -R "$REPO" -n app-dist -D "$TMP/app-dist" 2>>"$LOG" || {
    log "Failed to download app-dist. Skipping."
    exit 1
}

log "Downloading app-config..."
gh run download "$RUN_ID" -R "$REPO" -n app-config -D "$TMP/app-config" 2>>"$LOG" || {
    log "Failed to download app-config. Skipping."
    exit 1
}

# Deploy
log "Installing app dist..."
rsync -a --delete "$TMP/app-dist/" "$PI_DIR/app/"

log "Installing config..."
rsync -a "$TMP/app-config/" "$PI_DIR/config/"

# Mark as deployed
echo "$RUN_ID" > "$STAMP_FILE"

log "Updated to run $RUN_ID. Done."

# Clean old logs (keep 7 days)
find "$PI_DIR/logs" -name "self-update-*.log" -mtime +7 -delete 2>/dev/null || true
