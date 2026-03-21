#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# stop-backend.sh — Stop all Stage 1 Spring Boot services
# ─────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$SCRIPT_DIR/.pids"

if [ ! -f "$PID_FILE" ]; then
  echo "[INFO] No .pids file found — killing by port instead"
  for port in 8761 8888 8080 8081 8082 8089 8084 8083 8090 8087 8086 8085; do
    pid=$(lsof -ti ":$port" 2>/dev/null)
    if [ -n "$pid" ]; then
      kill "$pid" 2>/dev/null && echo "[INFO] Killed process on port $port (PID $pid)"
    fi
  done
  exit 0
fi

while IFS='=' read -r name pid; do
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" && echo "[INFO] Sent SIGTERM to $name (PID $pid)"
  else
    echo "[INFO] $name (PID $pid) was not running"
  fi
done < "$PID_FILE"

# Wait up to 10s for all processes to exit, then force-kill any stragglers
echo "[INFO] Waiting for services to stop..."
sleep 3

for port in 8761 8888 8080 8081 8082 8089 8083 8084 8090 8087 8086 8085; do
  pid=$(lsof -ti ":$port" 2>/dev/null)
  if [ -n "$pid" ]; then
    echo "[WARN] Port $port still in use (PID $pid) — force killing..."
    kill -9 "$pid" 2>/dev/null && echo "[INFO] Force killed port $port (PID $pid)"
  fi
done

rm -f "$PID_FILE"
echo "[INFO] All services stopped"