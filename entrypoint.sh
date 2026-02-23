#!/bin/bash
set -e

mkdir -p "$OUTPUT_DIR"

# Start the article viewer in the background
python3 /app/viewer/serve.py "$VIEWER_PORT" &

# Start the JVM daemon (JobRunr scheduler + dashboard)
exec /app/bin/app
