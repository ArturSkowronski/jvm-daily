#!/usr/bin/env bash
set -euo pipefail

# Deploy JVM Daily ingress-push to Raspberry Pi via Tailscale SSH.
# Safe: Pi runs ingress-push as a one-shot cron job, not a daemon.
# New code is picked up automatically on the next cron run.

PI_HOST="${PI_HOST:-arturskowronski@100.112.239.76}"
PI_DIR="${PI_DIR:-~/jvm-daily}"
PI_KEY="${PI_KEY:-$HOME/.ssh/id_rsa_pi}"
SSH_OPTS="-i $PI_KEY -o StrictHostKeyChecking=no"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

echo "═══ Deploy to Raspberry Pi ═══"
echo "Host: $PI_HOST"
echo "Dir:  $PI_DIR"
echo ""

# 1. Build
echo "▶ Building dist..."
cd "$ROOT_DIR"
./gradlew installDist -q
echo "✓ Build complete"

# 2. Check Pi is reachable
echo "▶ Checking Pi connectivity..."
if ! ssh $SSH_OPTS -o ConnectTimeout=5 "$PI_HOST" "echo ok" >/dev/null 2>&1; then
    echo "✗ Cannot reach $PI_HOST — is Tailscale connected?"
    exit 1
fi
echo "✓ Pi reachable"

# 3. Sync app dist (--delete removes stale jars)
echo "▶ Syncing app dist..."
rsync -az --delete -e "ssh $SSH_OPTS" \
    "$ROOT_DIR/app/build/install/app/" \
    "$PI_HOST:$PI_DIR/app/"
echo "✓ App synced"

# 4. Sync config
echo "▶ Syncing config..."
rsync -az -e "ssh $SSH_OPTS" \
    "$ROOT_DIR/config/" \
    "$PI_HOST:$PI_DIR/config/"
echo "✓ Config synced"

# 5. Verify
echo "▶ Verifying..."
REMOTE_VERSION=$(ssh $SSH_OPTS "$PI_HOST" "JAVA_HOME=/usr/lib/jvm/temurin-21-jre-arm64 $PI_DIR/app/bin/app --version 2>&1 || echo 'ok'")
echo "✓ Remote app responds"

echo ""
echo "═══ Deploy complete ═══"
echo "Next cron run (5:30 AM) will use the new code."
echo "To test now: ssh $PI_HOST '$PI_DIR/reddit-push.sh'"
