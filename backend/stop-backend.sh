#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# stop-backend.sh — Stop all Spring Boot services
# ─────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$SCRIPT_DIR/.pids"

# ── Helper: get PID listening on a port (macOS + Linux) ──────────
get_pid_for_port() {
  local port=$1
  # Try lsof first (works on macOS + Linux if installed)
  local pid
  pid=$(lsof -ti ":$port" 2>/dev/null)
  if [ -n "$pid" ]; then
    echo "$pid"
    return
  fi
  # Linux fallback: ss + awk (no grep -P needed, POSIX-compatible)
  ss -tlnp 2>/dev/null | grep ":${port} " | awk -F'pid=' '{print $2}' | awk -F',' '{print $1}' | head -1
}

if [ ! -f "$PID_FILE" ]; then
  echo "[INFO] No .pids file found — killing by port instead"
  for port in 8761 8888 8080 8081 8082 8089 8083 8084 8090 8087 8086 8085 8088 8091; do
    pid=$(get_pid_for_port "$port")
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

for port in 8761 8888 8080 8081 8082 8089 8083 8084 8090 8087 8086 8085 8088 8091; do
  pid=$(get_pid_for_port "$port")
  if [ -n "$pid" ]; then
    echo "[WARN] Port $port still in use (PID $pid) — force killing..."
    kill -9 "$pid" 2>/dev/null && echo "[INFO] Force killed port $port (PID $pid)"
  fi
done

rm -f "$PID_FILE"
echo "[INFO] All services stopped"
