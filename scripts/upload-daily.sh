#!/usr/bin/env bash
# Upload today's (or a specific date's) digest files to fly.io /data/output/
#
# Usage:
#   ./scripts/upload-daily.sh                  # upload today
#   ./scripts/upload-daily.sh 2026-03-17       # upload specific date
#   OUTPUT_DIR=my-output ./scripts/upload-daily.sh

set -euo pipefail

APP="${FLY_APP:-jvm-daily}"
DATE="${1:-$(date +%Y-%m-%d)}"
OUTPUT_DIR="${OUTPUT_DIR:-output}"
REMOTE_DIR="/data/output"

MD_FILE="${OUTPUT_DIR}/jvm-daily-${DATE}.md"
JSON_FILE="${OUTPUT_DIR}/daily-${DATE}.json"

echo "Uploading ${DATE} digest to ${APP}..."

if [[ ! -f "$MD_FILE" ]] && [[ ! -f "$JSON_FILE" ]]; then
  echo "Error: no output files found for ${DATE} in ${OUTPUT_DIR}/"
  echo "Expected: ${MD_FILE} or ${JSON_FILE}"
  exit 1
fi

# Ensure the machine is running
fly machine start --app "$APP" 2>/dev/null || true

if [[ -f "$MD_FILE" ]]; then
  echo "  -> ${MD_FILE}"
  fly ssh console --app "$APP" -C "rm -f ${REMOTE_DIR}/jvm-daily-${DATE}.md" 2>/dev/null || true
  fly sftp put --app "$APP" "$MD_FILE" "${REMOTE_DIR}/jvm-daily-${DATE}.md"
fi

if [[ -f "$JSON_FILE" ]]; then
  echo "  -> ${JSON_FILE}"
  fly ssh console --app "$APP" -C "rm -f ${REMOTE_DIR}/daily-${DATE}.json" 2>/dev/null || true
  fly sftp put --app "$APP" "$JSON_FILE" "${REMOTE_DIR}/daily-${DATE}.json"
fi

echo "Done. View at https://${APP}.fly.dev/"
