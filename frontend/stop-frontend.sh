#!/bin/bash

FRONTEND_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$FRONTEND_DIR/.frontend.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "⚠️  No PID file found. Frontend may not be running."
  # Try to kill by port as fallback
  PIDS=$(lsof -ti :5174 2>/dev/null)
  if [ -n "$PIDS" ]; then
    echo "$PIDS" | xargs kill 2>/dev/null
    sleep 1
    echo "$PIDS" | xargs kill -9 2>/dev/null
    echo "✅ Stopped all processes on port 5174"
  else
    echo "   Nothing running on port 5174."
  fi
  exit 0
fi

PID=$(cat "$PID_FILE")

if kill -0 "$PID" 2>/dev/null; then
  kill "$PID"
  sleep 1
  if kill -0 "$PID" 2>/dev/null; then
    kill -9 "$PID"
  fi
  rm -f "$PID_FILE"
  echo "✅ Frontend stopped (PID $PID)"
else
  echo "⚠️  Process $PID is not running. Cleaning up PID file."
  rm -f "$PID_FILE"
fi
