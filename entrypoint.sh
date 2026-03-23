#!/bin/bash
set -e

mkdir -p "$OUTPUT_DIR"

# Start the JVM daemon (JobRunr scheduler + REST API + viewer)
exec /app/bin/app
