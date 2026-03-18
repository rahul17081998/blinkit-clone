#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# stop-infra.sh — Full shutdown: Spring Boot + Docker infra + Colima
# Usage:  ./stop-infra.sh
# ─────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ── Step 1: Stop Spring Boot services ────────────────────────────
echo ""
echo "[STOP] Stopping Spring Boot services..."
PORTS=(8761 8888 8080 8081 8082 8089 8083 8084 8087 8085 8086 8088 8090 8091)
ANY_KILLED=false
for port in "${PORTS[@]}"; do
  pid=$(lsof -ti ":$port" 2>/dev/null)
  if [ -n "$pid" ]; then
    kill "$pid" 2>/dev/null && echo "[STOP] Killed service on port $port (PID $pid)"
    ANY_KILLED=true
  fi
done

PID_FILE="$SCRIPT_DIR/.pids"
if [ -f "$PID_FILE" ]; then
  while IFS='=' read -r name pid; do
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null && echo "[STOP] Stopped $name (PID $pid)"
      ANY_KILLED=true
    fi
  done < "$PID_FILE"
  rm -f "$PID_FILE"
fi

if [ "$ANY_KILLED" = false ]; then
  echo "[STOP] No Spring Boot services were running."
else
  echo "[STOP] ✅ All Spring Boot services stopped."
fi

# ── Step 2: Stop Docker infra containers + wipe volumes ──────────
echo ""
echo "[STOP] Stopping Docker infra containers..."
if ! docker info >/dev/null 2>&1; then
  echo "[STOP] Docker not reachable — skipping container shutdown."
else
  docker compose -f "$SCRIPT_DIR/docker-compose.infra.yml" down -v
  echo "[STOP] ✅ Containers stopped and volumes wiped."
fi

# ── Step 3: Stop Colima ───────────────────────────────────────────
echo ""
echo "[STOP] Stopping Colima..."
if colima status 2>/dev/null | grep -q "running"; then
  colima stop
  echo "[STOP] ✅ Colima stopped."
else
  echo "[STOP] Colima was not running."
fi

# ── Summary ───────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Full shutdown complete."
echo ""
echo "  Spring Boot services  → stopped"
echo "  Docker containers     → stopped + volumes wiped"
echo "  Colima                → stopped"
echo ""
echo "  To restart everything: ./start-backend.sh"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"