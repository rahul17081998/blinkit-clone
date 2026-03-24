#!/bin/bash

FRONTEND_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_FILE="$FRONTEND_DIR/frontend.log"
PID_FILE="$FRONTEND_DIR/.frontend.pid"
PORT=5174

# Check if already running
if [ -f "$PID_FILE" ]; then
  OLD_PID=$(cat "$PID_FILE")
  if kill -0 "$OLD_PID" 2>/dev/null; then
    echo "⚠️  Frontend is already running (PID $OLD_PID) on http://localhost:$PORT"
    exit 0
  else
    rm -f "$PID_FILE"
  fi
fi

# Load nvm and use Node 20
export NVM_DIR="$HOME/.nvm"
source "$NVM_DIR/nvm.sh"
nvm use 20 --silent

echo "🚀 Starting frontend on http://localhost:$PORT ..."
nohup npm --prefix "$FRONTEND_DIR" run dev -- --port $PORT > "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

# Wait a moment and confirm
sleep 3
if kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "✅ Frontend running at http://localhost:$PORT  (PID $(cat $PID_FILE))"
  echo "   Logs: $LOG_FILE"
else
  echo "❌ Frontend failed to start. Check logs: $LOG_FILE"
  tail -20 "$LOG_FILE"
  exit 1
fi
