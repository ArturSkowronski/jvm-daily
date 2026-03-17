#!/usr/bin/env bash
# Restart the article viewer without touching the JVM app server.
# The daemon loop in local-daemon.sh will revive the process automatically.
set -euo pipefail

if pkill -f "python3.*serve\.py" 2>/dev/null; then
    echo "Viewer restarted."
else
    echo "Viewer was not running — it will start via daemon loop."
fi
