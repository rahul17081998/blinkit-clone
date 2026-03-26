#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# stop-infra.sh — Stop all Spring Boot services + Docker infra
# Usage:  ./stop-infra.sh
# ─────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ── Step 1: Stop Spring Boot services ────────────────────────────
echo ""
echo "[STOP] Stopping Spring Boot services..."
bash "$SCRIPT_DIR/stop-backend.sh"

# ── Step 2: Stop Docker infra containers ─────────────────────────
echo ""
echo "[STOP] Stopping Docker infra containers..."
if ! docker info >/dev/null 2>&1; then
  echo "[STOP] Docker not reachable — skipping."
else
  # Stop kafka-only prod compose if it was used
  if [ -f "$SCRIPT_DIR/docker-compose.kafka-prod.yml" ]; then
    docker compose -f "$SCRIPT_DIR/docker-compose.kafka-prod.yml" down 2>/dev/null
  fi
  # Stop full dev infra compose if it was used
  if [ -f "$SCRIPT_DIR/docker-compose.infra.yml" ]; then
    docker compose -f "$SCRIPT_DIR/docker-compose.infra.yml" down 2>/dev/null
  fi
  echo "[STOP] ✅ Docker containers stopped."
fi

# ── Step 2b: Stop monitoring stack ───────────────────────────────
# IMPORTANT: monitoring stack must also be stopped here.
# When docker-compose.infra.yml tears down backend_blinkit-network
# (e.g. during a Kafka clean restart), the monitoring containers
# (Prometheus, Grafana, kafka-exporter) lose their network connection
# and hold stale network references. If you then bring infra back up,
# `docker compose -f docker-compose.monitoring.yml up -d` will fail
# with "network ... not found".
# Solution: always stop monitoring here so it can be brought up fresh
# after infra restarts with a new network.
echo ""
echo "[STOP] Stopping monitoring stack (Prometheus + Grafana + kafka-exporter)..."
if ! docker info >/dev/null 2>&1; then
  echo "[STOP] Docker not reachable — skipping monitoring stop."
else
  ENV_FILE=""
  if [ -f "$SCRIPT_DIR/.env.prod" ]; then
    ENV_FILE="$SCRIPT_DIR/.env.prod"
  elif [ -f "$SCRIPT_DIR/.env.dev" ]; then
    ENV_FILE="$SCRIPT_DIR/.env.dev"
  fi

  if [ -n "$ENV_FILE" ]; then
    set -o allexport && source "$ENV_FILE" && set +o allexport
  fi
  # Stop both dev and prod compose files — they share the same container names,
  # so whichever was used to start the stack, this ensures it's fully stopped.
  docker compose -f "$SCRIPT_DIR/docker-compose.monitoring.yml" down 2>/dev/null
  docker compose -f "$SCRIPT_DIR/docker-compose.monitoring.prod.yml" down 2>/dev/null
  echo "[STOP] ✅ Monitoring stack stopped."
fi

# ── Step 3: Stop Colima (macOS only) ─────────────────────────────
if [ "$(uname -s)" = "Darwin" ] && command -v colima >/dev/null 2>&1; then
  echo ""
  echo "[STOP] Stopping Colima..."
  if colima status 2>/dev/null | grep -q "running"; then
    colima stop && echo "[STOP] ✅ Colima stopped."
  else
    echo "[STOP] Colima was not running."
  fi
fi

# ── Summary ───────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Full shutdown complete."
echo "  To restart: ./start-backend.sh prod"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
