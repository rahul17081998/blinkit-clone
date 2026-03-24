#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# start-backend.sh — Start all Spring Boot microservices
# Usage:  ./start-backend.sh           → defaults to dev
#         ./start-backend.sh dev       → loads .env.dev, profile=dev
#         ./start-backend.sh prod      → loads .env.prod, profile=prod
# Stop:   ./stop-backend.sh
# ─────────────────────────────────────────────────────────────────

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
JAR_DIR="$SCRIPT_DIR"
PID_FILE="$SCRIPT_DIR/.pids"

# ── Resolve profile ──────────────────────────────────────────────
PROFILE="${1:-dev}"
if [[ "$PROFILE" != "dev" && "$PROFILE" != "prod" ]]; then
  echo "[ERROR] Unknown profile: '$PROFILE'. Use 'dev' or 'prod'."
  exit 1
fi
export PROFILE   # exported so infra-check.sh sees it
echo "[INFO] Starting with profile: $PROFILE"

# ── Load profile env file ────────────────────────────────────────
ENV_FILE="$SCRIPT_DIR/.env.$PROFILE"
if [ -f "$ENV_FILE" ]; then
  set -o allexport
  source "$ENV_FILE"
  set +o allexport
  echo "[INFO] Loaded $ENV_FILE"
elif [ -f "$SCRIPT_DIR/.env" ]; then
  # fallback to .env for backward compatibility
  set -o allexport
  source "$SCRIPT_DIR/.env"
  set +o allexport
  echo "[WARN] .env.$PROFILE not found — falling back to .env"
else
  echo "[ERROR] No env file found. Expected: $ENV_FILE"
  echo "        Copy .env.example to .env.$PROFILE and fill in your values."
  exit 1
fi

export SPRING_PROFILES_ACTIVE="$PROFILE"

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

  echo "[INFO] Starting $name (profile=$PROFILE)..."
  java -jar "$jar" --spring.profiles.active="$PROFILE" > "$log" 2>&1 &
  local pid=$!
  echo "$name=$pid" >> "$PID_FILE"
  echo "[INFO] $name started (PID $pid) — logs: logs/${name}.log"
}

# ── Helper: wait for a port to be ready ─────────────────────────
# Uses nc (netcat) which is available on both macOS and Ubuntu
wait_for_port() {
  local name=$1
  local port=$2
  local retries=60
  echo -n "[INFO] Waiting for $name on port $port "
  for i in $(seq 1 $retries); do
    if nc -z localhost "$port" 2>/dev/null; then
      echo " ready!"
      return 0
    fi
    echo -n "."
    sleep 2
  done
  echo ""
  echo "[ERROR] $name did not start on port $port after 120s. Check logs/${name}.log"
  exit 1
}

# ── Helper: wait for ALL ports in a wave, report failures ────────
wait_for_wave() {
  local failed=0
  # args: "name:port name:port ..."
  for entry in "$@"; do
    local name="${entry%%:*}"
    local port="${entry##*:}"
    wait_for_port "$name" "$port" || failed=1
  done
  if [ $failed -ne 0 ]; then
    echo "[ERROR] One or more services in this wave failed to start."
    exit 1
  fi
}

# ═══════════════════════════════════════════════════════════════════
# WAVE 1 — Eureka  (everything registers here)
# ═══════════════════════════════════════════════════════════════════
echo ""
echo "── Wave 1: Eureka Server ──────────────────────────────────────"
start_service "eureka-server" "$JAR_DIR/eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar"
wait_for_port "eureka-server" 8761

# ═══════════════════════════════════════════════════════════════════
# WAVE 2 — Config Server  (all services pull config from here)
# ═══════════════════════════════════════════════════════════════════
echo ""
echo "── Wave 2: Config Server ──────────────────────────────────────"
start_service "config-server" "$JAR_DIR/config-server/target/config-server-1.0.0-SNAPSHOT.jar"
wait_for_port "config-server" 8888

# ═══════════════════════════════════════════════════════════════════
# WAVE 3 — All business services in parallel
#   (each only needs Eureka + Config Server at startup,
#    no service calls any other service during boot)
# ═══════════════════════════════════════════════════════════════════
echo ""
echo "── Wave 3: All business services (parallel) ───────────────────"
start_service "auth-service"         "$JAR_DIR/auth-service/target/auth-service-1.0.0-SNAPSHOT.jar"
start_service "user-service"         "$JAR_DIR/user-service/target/user-service-1.0.0-SNAPSHOT.jar"
start_service "notification-service" "$JAR_DIR/notification-service/target/notification-service-1.0.0-SNAPSHOT.jar"
start_service "api-gateway"          "$JAR_DIR/api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar"
start_service "product-service"      "$JAR_DIR/product-service/target/product-service-1.0.0-SNAPSHOT.jar"
start_service "inventory-service"    "$JAR_DIR/inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar"
start_service "coupon-service"       "$JAR_DIR/coupon-service/target/coupon-service-1.0.0-SNAPSHOT.jar"
start_service "cart-service"         "$JAR_DIR/cart-service/target/cart-service-1.0.0-SNAPSHOT.jar"
start_service "payment-service"      "$JAR_DIR/payment-service/target/payment-service-1.0.0-SNAPSHOT.jar"
start_service "order-service"        "$JAR_DIR/order-service/target/order-service-1.0.0-SNAPSHOT.jar"
start_service "delivery-service"     "$JAR_DIR/delivery-service/target/delivery-service-1.0.0-SNAPSHOT.jar"
start_service "review-service"       "$JAR_DIR/review-service/target/review-service-1.0.0-SNAPSHOT.jar"

echo ""
echo "[INFO] All 12 services launched — waiting for them to be ready..."
wait_for_wave \
  "auth-service:8081" \
  "user-service:8082" \
  "notification-service:8089" \
  "api-gateway:8080" \
  "product-service:8083" \
  "inventory-service:8084" \
  "coupon-service:8090" \
  "cart-service:8087" \
  "payment-service:8086" \
  "order-service:8085" \
  "delivery-service:8088" \
  "review-service:8091"

# ── Done ─────────────────────────────────────────────────────────
# Detect public IP (works on Oracle Cloud; falls back to localhost)
if [ "$PROFILE" = "prod" ]; then
  PUBLIC_IP=$(curl -s --max-time 3 http://169.254.169.254/opc/v1/vnics/ 2>/dev/null \
    | python3 -c "import sys,json; v=json.load(sys.stdin); print(v[0].get('publicIp',''))" 2>/dev/null)
  if [ -z "$PUBLIC_IP" ]; then
    PUBLIC_IP=$(curl -s --max-time 3 ifconfig.me 2>/dev/null || echo "YOUR_VM_IP")
  fi
  BASE="http://$PUBLIC_IP"
else
  BASE="http://localhost"
fi

echo ""
echo "✔  All services are UP  (profile: $PROFILE)"
echo ""
echo "   ── Public Access ────────────────────────────────────────"
echo "   API Gateway (public) →  $BASE:8080/actuator/health"
echo ""
echo "   ── Internal (from VM only) ──────────────────────────────"
echo "   Eureka dashboard     →  http://localhost:8761"
echo "   Config Server        →  http://localhost:8888/actuator/health"
echo "   Auth Service         →  http://localhost:8081/swagger-ui.html"
echo "   User Service         →  http://localhost:8082/swagger-ui.html"
echo "   Notification Svc     →  http://localhost:8089/actuator/health"
echo "   Product Service      →  http://localhost:8083/swagger-ui.html"
echo "   Inventory Service    →  http://localhost:8084/swagger-ui.html"
echo "   Coupon Service       →  http://localhost:8090/swagger-ui.html"
echo "   Cart Service         →  http://localhost:8087/swagger-ui.html"
echo "   Payment Service      →  http://localhost:8086/swagger-ui.html"
echo "   Order Service        →  http://localhost:8085/swagger-ui.html"
echo "   Delivery Service     →  http://localhost:8088/actuator/health"
echo "   Review Service       →  http://localhost:8091/swagger-ui.html"
echo "   Kafka UI (SSH tunnel)→  http://localhost:9093"
echo ""
echo "   Stop all:  ./stop-backend.sh"