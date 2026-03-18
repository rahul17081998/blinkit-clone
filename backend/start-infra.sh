#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# start-infra.sh — Ensure Colima + Docker infra containers are up
# Called automatically by start-backend.sh before starting services
# ─────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ── Step 1: Start Colima if not running ──────────────────────────
echo ""
echo "[INFRA] Checking Colima (Docker runtime)..."
if ! docker info >/dev/null 2>&1; then
  echo "[INFRA] Docker is not reachable — starting Colima..."
  colima start
  # Wait up to 40s for Docker socket to be ready after colima start
  echo -n "[INFRA] Waiting for Docker socket to be ready "
  for i in $(seq 1 20); do
    if docker info >/dev/null 2>&1; then
      echo " ✅ Docker is ready!"
      break
    fi
    echo -n "."
    sleep 2
    if [ "$i" -eq 20 ]; then
      echo ""
      echo "[INFRA] ERROR: Docker did not become ready after 40s. Run manually: colima start"
      exit 1
    fi
  done
  echo "[INFRA] Colima started successfully."
else
  echo "[INFRA] Colima is already running."
fi

# ── Step 2: Check which containers need to be started ────────────
echo ""
echo "[INFRA] Checking Docker infra containers..."

REDIS_RUNNING=$(docker ps --filter "name=blinkit-redis" --filter "status=running" --format "{{.Names}}" 2>/dev/null | grep -x "blinkit-redis" || true)
KAFKA_RUNNING=$(docker ps --filter "name=blinkit-kafka" --filter "status=running" --format "{{.Names}}" 2>/dev/null | grep -x "blinkit-kafka" || true)

if [ -z "$REDIS_RUNNING" ] || [ -z "$KAFKA_RUNNING" ]; then
  echo "[INFRA] One or more infra containers are not running — starting docker compose..."
  docker compose -f "$SCRIPT_DIR/docker-compose.infra.yml" up -d

  # Check if Kafka container actually started (NodeExistsException causes exit code 1)
  sleep 3
  KAFKA_EXITED=$(docker ps -a --filter "name=blinkit-kafka" --filter "status=exited" --format "{{.Names}}" 2>/dev/null | grep -x "blinkit-kafka" || true)
  if [ -n "$KAFKA_EXITED" ]; then
    echo "[INFRA] Kafka failed to start (likely stale Zookeeper data). Doing clean restart..."
    docker compose -f "$SCRIPT_DIR/docker-compose.infra.yml" down -v
    docker compose -f "$SCRIPT_DIR/docker-compose.infra.yml" up -d
    echo "[INFRA] Clean restart done."
  else
    echo "[INFRA] Docker compose started."
  fi
else
  echo "[INFRA] All infra containers are already running."
fi

# ── Step 3: Wait for Redis to be healthy ─────────────────────────
echo ""
echo -n "[INFRA] Waiting for Redis to be ready "
for i in $(seq 1 20); do
  REDIS_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' blinkit-redis 2>/dev/null || echo "unknown")
  if [ "$REDIS_HEALTH" = "healthy" ]; then
    echo " ✅ Redis is ready!"
    break
  fi
  echo -n "."
  sleep 2
  if [ "$i" -eq 20 ]; then
    echo ""
    echo "[INFRA] ERROR: Redis did not become healthy after 40s."
    echo "        Check logs: docker logs blinkit-redis"
    exit 1
  fi
done

# ── Step 4: Wait for Kafka to be healthy ─────────────────────────
echo ""
echo -n "[INFRA] Waiting for Kafka to be ready "
for i in $(seq 1 30); do
  KAFKA_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' blinkit-kafka 2>/dev/null || echo "unknown")
  if [ "$KAFKA_HEALTH" = "healthy" ]; then
    echo " ✅ Kafka is ready!"
    break
  fi
  echo -n "."
  sleep 2
  if [ "$i" -eq 30 ]; then
    echo ""
    echo "[INFRA] ERROR: Kafka did not become healthy after 60s."
    echo "        Try a clean restart:"
    echo "          docker compose -f docker-compose.infra.yml down -v"
    echo "          docker compose -f docker-compose.infra.yml up -d"
    exit 1
  fi
done

echo ""
echo "[INFRA] ✅ All infrastructure is up and healthy."
echo "        Redis    → localhost:6379"
echo "        Kafka    → localhost:9092"
echo "        Kafka UI → http://localhost:9093"
echo "        Redis UI → http://localhost:9191"
echo ""