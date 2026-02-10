#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

LOG_DIR="${LOG_DIR:-$SCRIPT_DIR/logs}"
mkdir -p "$LOG_DIR"

TIMESTAMP=$(date +%Y-%m-%d_%H-%M-%S)
LOG_FILE="$LOG_DIR/ingress-$TIMESTAMP.log"

echo "[$TIMESTAMP] Starting JVM Daily ingress..." | tee "$LOG_FILE"

./gradlew -q run 2>&1 | tee -a "$LOG_FILE"
EXIT_CODE=${PIPESTATUS[0]}

if [ "$EXIT_CODE" -eq 0 ]; then
    echo "[$TIMESTAMP] Ingress completed successfully." | tee -a "$LOG_FILE"
else
    echo "[$TIMESTAMP] Ingress failed with exit code $EXIT_CODE." | tee -a "$LOG_FILE"
fi

# Keep only last 30 days of logs
find "$LOG_DIR" -name "ingress-*.log" -mtime +30 -delete 2>/dev/null || true

exit "$EXIT_CODE"
