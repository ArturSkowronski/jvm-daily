#!/usr/bin/env bash
set -euo pipefail

PLIST="$HOME/Library/LaunchAgents/com.local.jvm-daily.plist"

if [[ ! -f "$PLIST" ]]; then
    echo "Service not installed (plist not found at $PLIST)"
    exit 0
fi

launchctl bootout "gui/$(id -u)" "$PLIST" 2>/dev/null || true
rm "$PLIST"

echo "JVM Daily service uninstalled."
