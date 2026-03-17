#!/usr/bin/env bash
# Run the full pipeline locally (Reddit works here, not on fly.io) then upload to fly.io.
#
# Usage:
#   LLM_PROVIDER=gemini GEMINI_API_KEY=<key> ./scripts/run-and-upload.sh
#   LLM_PROVIDER=gemini GEMINI_API_KEY=<key> GITHUB_TOKEN=<token> ./scripts/run-and-upload.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
DATE="$(date +%Y-%m-%d)"

cd "$ROOT_DIR"

echo "=== JVM Daily — local run + fly.io upload ==="
echo "Date: ${DATE}"
echo ""

# Build distribution if needed
if [[ ! -f "app/build/install/app/bin/app" ]]; then
  echo "[1/3] Building..."
  ./gradlew :app:installDist -q
else
  echo "[1/3] Build up-to-date"
fi

# Run pipeline
echo "[2/3] Running pipeline..."
./app/build/install/app/bin/app pipeline

# Upload
echo "[3/3] Uploading to fly.io..."
"$SCRIPT_DIR/upload-daily.sh" "$DATE"
