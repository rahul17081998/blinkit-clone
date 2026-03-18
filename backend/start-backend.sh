#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# start-backend.sh — Start all Spring Boot microservices
# Usage:  ./start-backend.sh
# Stop:   ./stop-backend.sh
# ─────────────────────────────────────────────────────────────────

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
JAR_DIR="$SCRIPT_DIR"
PID_FILE="$SCRIPT_DIR/.pids"

# ── Load .env ────────────────────────────────────────────────────
if [ -f "$SCRIPT_DIR/.env" ]; then
  set -o allexport
  source "$SCRIPT_DIR/.env"
  set +o allexport
  echo "[INFO] Loaded .env"
else
  echo "[ERROR] .env file not found at $SCRIPT_DIR/.env"
  exit 1
fi

mkdir -p "$LOG_DIR"
> "$PID_FILE"   # clear old PIDs

# ── Step 0a: Start Colima + Docker infra if not running ──────────
if [ -f "$SCRIPT_DIR/start-infra.sh" ]; then
  bash "$SCRIPT_DIR/start-infra.sh"
  if [ $? -ne 0 ]; then
    echo "[ERROR] Infrastructure startup failed. See details above."
    exit 1
  fi
else
  echo "[WARN] start-infra.sh not found — skipping auto infra startup"
fi

# ── Step 0b: Infrastructure Health Check ─────────────────────────
echo ""
echo "[INFO] Running infrastructure connectivity check..."
echo "──────────────────────────────────────────────────"
if [ -f "$SCRIPT_DIR/infra-check.sh" ]; then
  bash "$SCRIPT_DIR/infra-check.sh"
  INFRA_EXIT=$?
  if [ $INFRA_EXIT -ne 0 ]; then
    echo ""
    echo "[ERROR] Infrastructure check failed. Fix the issues above before starting services."
    exit 1
  fi
else
  echo "[WARN] infra-check.sh not found — skipping connectivity check"
fi
echo "──────────────────────────────────────────────────"
echo ""

# ── Helper: start a service ──────────────────────────────────────
start_service() {
  local name=$1
  local jar=$2
  local log="$LOG_DIR/${name}.log"

  if [ ! -f "$jar" ]; then
    echo "[ERROR] JAR not found: $jar"
    echo "        Run:  cd backend && mvn clean package -DskipTests"
    exit 1
  fi

  echo "[INFO] Starting $name..."
  java -jar "$jar" > "$log" 2>&1 &
  local pid=$!
  echo "$name=$pid" >> "$PID_FILE"
  echo "[INFO] $name started (PID $pid) — logs: logs/${name}.log"
}

# ── Helper: wait for a port to be ready ─────────────────────────
wait_for_port() {
  local name=$1
  local port=$2
  local retries=30
  echo -n "[INFO] Waiting for $name on port $port "
  for i in $(seq 1 $retries); do
    if lsof -i ":$port" | grep -q LISTEN 2>/dev/null; then
      echo " ready!"
      return 0
    fi
    echo -n "."
    sleep 2
  done
  echo ""
  echo "[ERROR] $name did not start on port $port after 60s. Check logs/${name}.log"
  exit 1
}

# ── Step 1: Start Eureka (others depend on it) ───────────────────
start_service "eureka-server" "$JAR_DIR/eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar"
wait_for_port "eureka-server" 8761

# ── Step 2: Start Config Server ──────────────────────────────────
start_service "config-server" "$JAR_DIR/config-server/target/config-server-1.0.0-SNAPSHOT.jar"
wait_for_port "config-server" 8888

# ── Step 3: Start API Gateway ────────────────────────────────────
start_service "api-gateway" "$JAR_DIR/api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar"
wait_for_port "api-gateway" 8080

# ── Step 4: Start Auth Service ────────────────────────────────────
start_service "auth-service" "$JAR_DIR/auth-service/target/auth-service-1.0.0-SNAPSHOT.jar"
wait_for_port "auth-service" 8081

# ── Step 5: Start User Service ────────────────────────────────────
start_service "user-service" "$JAR_DIR/user-service/target/user-service-1.0.0-SNAPSHOT.jar"
wait_for_port "user-service" 8082

# ── Step 6: Start Notification Service ───────────────────────────
start_service "notification-service" "$JAR_DIR/notification-service/target/notification-service-1.0.0-SNAPSHOT.jar"
wait_for_port "notification-service" 8089

# ── Step 7: Start Product Service ────────────────────────────────
start_service "product-service" "$JAR_DIR/product-service/target/product-service-1.0.0-SNAPSHOT.jar"
wait_for_port "product-service" 8083

# ── Step 8: Start Inventory Service ──────────────────────────────
start_service "inventory-service" "$JAR_DIR/inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar"
wait_for_port "inventory-service" 8084

# ── Done ─────────────────────────────────────────────────────────
echo ""
echo "✔  All services are UP"
echo ""
echo "   Eureka dashboard  →  http://localhost:8761"
echo "   Config Server     →  http://localhost:8888/actuator/health"
echo "   API Gateway       →  http://localhost:8080/actuator/health"
echo "   Auth Service      →  http://localhost:8081/swagger-ui.html"
echo "   User Service      →  http://localhost:8082/swagger-ui.html"
echo "   Notification Svc  →  http://localhost:8089/actuator/health"
echo "   Product Service   →  http://localhost:8083/swagger-ui.html"
echo "   Inventory Service →  http://localhost:8084/swagger-ui.html"
echo "   Kafka UI          →  http://localhost:9093"
echo "   Redis Commander   →  http://localhost:9191"
echo ""
echo "   Stop all:  ./stop-backend.sh"