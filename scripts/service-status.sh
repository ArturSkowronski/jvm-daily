#!/usr/bin/env bash
set -euo pipefail

echo "=== launchd service status ==="
launchctl print "gui/$(id -u)/com.local.jvm-daily" 2>/dev/null || echo "(service not loaded)"

echo ""
echo "=== stdout (last 50 lines) ==="
tail -50 "$HOME/.jvm-daily/logs/stdout.log" 2>/dev/null || echo "(no stdout log yet)"

echo ""
echo "=== stderr (last 20 lines) ==="
tail -20 "$HOME/.jvm-daily/logs/stderr.log" 2>/dev/null || echo "(no stderr log yet)"
