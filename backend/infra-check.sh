#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# infra-check.sh — Infrastructure Connectivity Tester
#
# Tests:
#   1. MongoDB Atlas  — insert doc, read, verify, delete
#   2. Redis          — set key, get, verify, delete
#   3. Kafka          — produce to connectivity.test topic, consume & verify
#   4. Cloudinary CDN — ping cloud name, verify API credentials, upload/verify/delete test image
#
# Usage:  cd backend && ./infra-check.sh
# ─────────────────────────────────────────────────────────────────

set +e  # Don't exit on errors — we report failures ourselves

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PASS="✅ PASS"
FAIL="❌ FAIL"
TOTAL_PASS=0
TOTAL_FAIL=0

# ── Colors ───────────────────────────────────────────────────────
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ── Load .env (profile-aware) ────────────────────────────────────
# Honour the same profile as start-backend.sh
PROFILE="${PROFILE:-dev}"
ENV_FILE="$SCRIPT_DIR/.env.$PROFILE"
if [ ! -f "$ENV_FILE" ]; then
  ENV_FILE="$SCRIPT_DIR/.env"   # fallback
fi
if [ ! -f "$ENV_FILE" ]; then
  echo -e "${RED}[ERROR]${NC} No env file found (tried .env.$PROFILE and .env)"
  exit 1
fi
echo -e "${BLUE}[INFO]${NC} Using env file: $ENV_FILE"

# Read vars safely — handles special chars (&, ?, =) in values like MONGO_URI
# Also strips surrounding quotes (single or double) added for shell safety
_get_env() {
  local raw
  raw=$(grep "^${1}=" "$ENV_FILE" | head -1 | cut -d'=' -f2-)
  # Strip surrounding double quotes
  raw="${raw%\"}"
  raw="${raw#\"}"
  # Strip surrounding single quotes
  raw="${raw%\'}"
  raw="${raw#\'}"
  echo "$raw"
}

MONGO_URI=$(_get_env "MONGO_URI")
REDIS_HOST=$(_get_env "REDIS_HOST")
REDIS_PORT=$(_get_env "REDIS_PORT")
REDIS_PASSWORD=$(_get_env "REDIS_PASSWORD")
KAFKA_BOOTSTRAP_SERVERS=$(_get_env "KAFKA_BOOTSTRAP_SERVERS")
CLOUDINARY_CLOUD_NAME=$(_get_env "CLOUDINARY_CLOUD_NAME")
CLOUDINARY_API_KEY=$(_get_env "CLOUDINARY_API_KEY")
CLOUDINARY_API_SECRET=$(_get_env "CLOUDINARY_API_SECRET")

REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"

print_header() {
  echo ""
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${BLUE}  $1${NC}"
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

check() {
  local name="$1"
  local result="$2"   # "pass" or "fail"
  local detail="$3"

  if [ "$result" = "pass" ]; then
    echo -e "  ${GREEN}${PASS}${NC}  $name  ${YELLOW}→ $detail${NC}"
    TOTAL_PASS=$((TOTAL_PASS + 1))
  else
    echo -e "  ${RED}${FAIL}${NC}  $name  ${RED}→ $detail${NC}"
    TOTAL_FAIL=$((TOTAL_FAIL + 1))
  fi
}

# ─────────────────────────────────────────────────────────────────
# 1. MONGODB
# ─────────────────────────────────────────────────────────────────
print_header "1. MongoDB Atlas Connectivity"

MONGO_TEST_DOC_ID="infra_check_$(date +%s)"
MONGO_DB="connectivity_test_db"

# Insert document
MONGO_INSERT=$(mongosh "${MONGO_URI}" --quiet --eval "
  db = db.getSiblingDB('${MONGO_DB}');
  const result = db.connectivity_check.insertOne({
    _id: '${MONGO_TEST_DOC_ID}',
    service: 'blinkit-clone',
    test: 'connectivity',
    timestamp: new Date()
  });
  print(result.acknowledged ? 'ok' : 'fail');
" 2>/dev/null | tail -1)

if [ "$MONGO_INSERT" = "ok" ]; then
  check "MongoDB INSERT document" "pass" "_id=${MONGO_TEST_DOC_ID}"
else
  check "MongoDB INSERT document" "fail" "insert returned: $MONGO_INSERT"
fi

# Read back document
MONGO_READ=$(mongosh "${MONGO_URI}" --quiet --eval "
  db = db.getSiblingDB('${MONGO_DB}');
  const doc = db.connectivity_check.findOne({_id: '${MONGO_TEST_DOC_ID}'});
  print(doc ? doc.test : 'not_found');
" 2>/dev/null | tail -1)

if [ "$MONGO_READ" = "connectivity" ]; then
  check "MongoDB READ document" "pass" "field test='connectivity' verified"
else
  check "MongoDB READ document" "fail" "got: $MONGO_READ"
fi

# Count documents in collection
MONGO_COUNT=$(mongosh "${MONGO_URI}" --quiet --eval "
  db = db.getSiblingDB('${MONGO_DB}');
  print(db.connectivity_check.countDocuments({_id: '${MONGO_TEST_DOC_ID}'}));
" 2>/dev/null | tail -1)

if [ "$MONGO_COUNT" = "1" ]; then
  check "MongoDB COUNT verify" "pass" "count=1 as expected"
else
  check "MongoDB COUNT verify" "fail" "count=$MONGO_COUNT"
fi

# Delete document
MONGO_DELETE=$(mongosh "${MONGO_URI}" --quiet --eval "
  db = db.getSiblingDB('${MONGO_DB}');
  const result = db.connectivity_check.deleteOne({_id: '${MONGO_TEST_DOC_ID}'});
  print(result.deletedCount == 1 ? 'ok' : 'fail');
" 2>/dev/null | tail -1)

if [ "$MONGO_DELETE" = "ok" ]; then
  check "MongoDB DELETE document" "pass" "deleted successfully, collection clean"
else
  check "MongoDB DELETE document" "fail" "deleteOne returned: $MONGO_DELETE"
fi

# Drop test database
mongosh "${MONGO_URI}" --quiet --eval "db.getSiblingDB('${MONGO_DB}').dropDatabase();" 2>/dev/null
check "MongoDB CLEANUP (drop test db)" "pass" "${MONGO_DB} dropped"

# ─────────────────────────────────────────────────────────────────
# 2. REDIS
# ─────────────────────────────────────────────────────────────────
print_header "2. Redis Connectivity"

REDIS_PASS="${REDIS_PASSWORD:-}"
REDIS_KEY="infra:check:$(date +%s)"
REDIS_VALUE="blinkit-connectivity-ok"

REDIS_ARGS="-h ${REDIS_HOST} -p ${REDIS_PORT}"
if [ -n "$REDIS_PASS" ]; then
  REDIS_ARGS="${REDIS_ARGS} -a ${REDIS_PASS}"
fi
# Upstash and other cloud Redis require TLS — detect by non-localhost host
if [ "${REDIS_HOST}" != "localhost" ] && [ "${REDIS_HOST}" != "127.0.0.1" ]; then
  REDIS_ARGS="${REDIS_ARGS} --tls"
fi

# PING
PING_RESULT=$(redis-cli ${REDIS_ARGS} PING 2>/dev/null)
if [ "$PING_RESULT" = "PONG" ]; then
  check "Redis PING" "pass" "PONG received"
else
  check "Redis PING" "fail" "got: $PING_RESULT"
fi

# SET
SET_RESULT=$(redis-cli ${REDIS_ARGS} SET "${REDIS_KEY}" "${REDIS_VALUE}" EX 60 2>/dev/null)
if [ "$SET_RESULT" = "OK" ]; then
  check "Redis SET key" "pass" "key=${REDIS_KEY}"
else
  check "Redis SET key" "fail" "got: $SET_RESULT"
fi

# GET and verify
GET_RESULT=$(redis-cli ${REDIS_ARGS} GET "${REDIS_KEY}" 2>/dev/null)
if [ "$GET_RESULT" = "$REDIS_VALUE" ]; then
  check "Redis GET and VERIFY" "pass" "value='${REDIS_VALUE}' matched"
else
  check "Redis GET and VERIFY" "fail" "expected='${REDIS_VALUE}', got='${GET_RESULT}'"
fi

# TTL check
TTL_RESULT=$(redis-cli ${REDIS_ARGS} TTL "${REDIS_KEY}" 2>/dev/null)
if [ "$TTL_RESULT" -gt 0 ] 2>/dev/null; then
  check "Redis TTL verify" "pass" "TTL=${TTL_RESULT}s (set to 60s)"
else
  check "Redis TTL verify" "fail" "TTL=$TTL_RESULT"
fi

# DEL
DEL_RESULT=$(redis-cli ${REDIS_ARGS} DEL "${REDIS_KEY}" 2>/dev/null)
if [ "$DEL_RESULT" = "1" ]; then
  check "Redis DELETE key" "pass" "deleted, key no longer exists"
else
  check "Redis DELETE key" "fail" "deleted count: $DEL_RESULT"
fi

# Verify deletion
VERIFY_DEL=$(redis-cli ${REDIS_ARGS} EXISTS "${REDIS_KEY}" 2>/dev/null)
if [ "$VERIFY_DEL" = "0" ]; then
  check "Redis verify key deleted" "pass" "EXISTS=0 confirmed"
else
  check "Redis verify key deleted" "fail" "EXISTS=$VERIFY_DEL (still exists!)"
fi

# ─────────────────────────────────────────────────────────────────
# 3. KAFKA
# ─────────────────────────────────────────────────────────────────
print_header "3. Kafka Connectivity"

KAFKA_CONTAINER="blinkit-kafka"
KAFKA_TEST_TOPIC="connectivity.test"
KAFKA_TEST_MSG="blinkit-infra-check-$(date +%s)"

# Check if kafka container is running (exact match via grep -x)
KAFKA_RUNNING=$(docker ps --filter "name=${KAFKA_CONTAINER}" --filter "status=running" --format "{{.Names}}" 2>/dev/null | grep -x "${KAFKA_CONTAINER}")
if [ "$KAFKA_RUNNING" != "$KAFKA_CONTAINER" ]; then
  check "Kafka container running" "fail" "Container ${KAFKA_CONTAINER} not running — start infra first"
  echo -e "  ${RED}Skipping remaining Kafka tests${NC}"
else
  check "Kafka container running" "pass" "${KAFKA_CONTAINER} is UP"

  # Create test topic (ignore error if already exists)
  docker exec ${KAFKA_CONTAINER} kafka-topics \
    --bootstrap-server localhost:9092 \
    --create \
    --if-not-exists \
    --topic ${KAFKA_TEST_TOPIC} \
    --partitions 1 \
    --replication-factor 1 2>/dev/null

  TOPIC_EXISTS=$(docker exec ${KAFKA_CONTAINER} kafka-topics \
    --bootstrap-server localhost:9092 \
    --list 2>/dev/null | grep "^${KAFKA_TEST_TOPIC}$")

  if [ "$TOPIC_EXISTS" = "$KAFKA_TEST_TOPIC" ]; then
    check "Kafka CREATE topic '${KAFKA_TEST_TOPIC}'" "pass" "topic exists and is ready"
  else
    check "Kafka CREATE topic '${KAFKA_TEST_TOPIC}'" "fail" "topic not found after create"
  fi

  # Produce a message
  echo "${KAFKA_TEST_MSG}" | docker exec -i ${KAFKA_CONTAINER} kafka-console-producer \
    --bootstrap-server localhost:9092 \
    --topic ${KAFKA_TEST_TOPIC} 2>/dev/null

  check "Kafka PRODUCE message" "pass" "message='${KAFKA_TEST_MSG}'"

  # Consume the message (read from beginning, timeout after 8 seconds)
  CONSUMED=$(docker exec ${KAFKA_CONTAINER} kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic ${KAFKA_TEST_TOPIC} \
    --from-beginning \
    --max-messages 1 \
    --timeout-ms 8000 2>/dev/null | grep -v "^$" | tail -1)

  if echo "$CONSUMED" | grep -q "blinkit-infra-check"; then
    check "Kafka CONSUME and VERIFY" "pass" "consumed: '${CONSUMED}'"
  else
    check "Kafka CONSUME and VERIFY" "fail" "consumed: '${CONSUMED}' (no match)"
  fi

  # List all active Kafka topics (excluding internal __consumer_offsets etc.)
  ALL_TOPICS=$(docker exec ${KAFKA_CONTAINER} kafka-topics \
    --bootstrap-server localhost:9092 \
    --list 2>/dev/null | grep -v "^__" | sort)

  echo ""
  echo -e "  ${YELLOW}Active Kafka topics:${NC}"
  while IFS= read -r topic; do
    [ -n "$topic" ] && echo "    • $topic"
  done <<< "$ALL_TOPICS"
fi

# ─────────────────────────────────────────────────────────────────
# 4. CLOUDINARY CDN
# ─────────────────────────────────────────────────────────────────
print_header "4. Cloudinary CDN Connectivity"

if [ -z "$CLOUDINARY_CLOUD_NAME" ] || [ -z "$CLOUDINARY_API_KEY" ] || [ -z "$CLOUDINARY_API_SECRET" ]; then
  echo -e "  ${RED}[SKIP]${NC} Cloudinary credentials missing in .env — skipping all Cloudinary checks"
else
  # Usage — verifies cloud name + API key + secret are all correct (Basic auth: api_key:api_secret)
  USAGE_HTTP=$(curl -s -o /dev/null -w "%{http_code}" --max-time 30 \
    -u "${CLOUDINARY_API_KEY}:${CLOUDINARY_API_SECRET}" \
    "https://api.cloudinary.com/v1_1/${CLOUDINARY_CLOUD_NAME}/usage" 2>/dev/null)
  if [ "$USAGE_HTTP" = "200" ]; then
    check "Cloudinary API credentials valid" "pass" "API key + secret accepted by Cloudinary"
  elif [ "$USAGE_HTTP" = "401" ]; then
    check "Cloudinary API credentials valid" "fail" "HTTP 401 — wrong API key or secret, check .env"
  else
    check "Cloudinary API credentials valid" "fail" "HTTP ${USAGE_HTTP} — unexpected response"
  fi

  # Upload test image → verify CDN URL is reachable → delete from Cloudinary
  TEST_IMAGE="${SCRIPT_DIR}/../resource/test_image.png"
  if [ ! -f "$TEST_IMAGE" ]; then
    check "Cloudinary image upload" "fail" "test file not found: resource/test_image.png"
  else
    # Generate a unique public_id so parallel runs don't collide
    UPLOAD_TS=$(date +%s)
    UPLOAD_PUBLIC_ID="blinkit/infra_check/connectivity_test_${UPLOAD_TS}"

    # Sign: SHA1 of "public_id=<id>&timestamp=<ts><api_secret>"
    UPLOAD_SIG=$(echo -n "public_id=${UPLOAD_PUBLIC_ID}&timestamp=${UPLOAD_TS}${CLOUDINARY_API_SECRET}" \
      | openssl dgst -sha1 -hex | awk '{print $2}')

    UPLOAD_RESPONSE=$(curl -s --max-time 15 \
      -F "file=@${TEST_IMAGE}" \
      -F "public_id=${UPLOAD_PUBLIC_ID}" \
      -F "api_key=${CLOUDINARY_API_KEY}" \
      -F "timestamp=${UPLOAD_TS}" \
      -F "signature=${UPLOAD_SIG}" \
      "https://api.cloudinary.com/v1_1/${CLOUDINARY_CLOUD_NAME}/image/upload" 2>/dev/null)

    UPLOADED_URL=$(echo "$UPLOAD_RESPONSE" | grep -o '"secure_url":"[^"]*"' | cut -d'"' -f4)

    if [ -n "$UPLOADED_URL" ]; then
      check "Cloudinary image upload" "pass" "test_image.png uploaded → public_id=${UPLOAD_PUBLIC_ID}"

      # Verify the CDN URL is actually reachable
      CDN_HTTP=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$UPLOADED_URL" 2>/dev/null)
      if [ "$CDN_HTTP" = "200" ]; then
        check "Cloudinary CDN delivery" "pass" "uploaded image served successfully from CDN edge"
      else
        check "Cloudinary CDN delivery" "fail" "HTTP ${CDN_HTTP} — image uploaded but CDN not serving it"
      fi

      # Delete the uploaded test image (clean up)
      TS=$(date +%s)
      DEL_SIG=$(echo -n "public_id=${UPLOAD_PUBLIC_ID}&timestamp=${TS}${CLOUDINARY_API_SECRET}" | openssl dgst -sha1 -hex | awk '{print $2}')
      DELETE_RESPONSE=$(curl -s --max-time 10 \
        -X POST \
        -F "public_id=${UPLOAD_PUBLIC_ID}" \
        -F "api_key=${CLOUDINARY_API_KEY}" \
        -F "timestamp=${TS}" \
        -F "signature=${DEL_SIG}" \
        "https://api.cloudinary.com/v1_1/${CLOUDINARY_CLOUD_NAME}/image/destroy" 2>/dev/null)

      DEL_RESULT=$(echo "$DELETE_RESPONSE" | grep -o '"result":"[^"]*"' | cut -d'"' -f4)
      if [ "$DEL_RESULT" = "ok" ]; then
        check "Cloudinary image cleanup" "pass" "test image deleted from Cloudinary storage"
      else
        check "Cloudinary image cleanup" "fail" "delete returned '${DEL_RESULT}' — manually remove ${UPLOAD_PUBLIC_ID}"
      fi
    else
      UPLOAD_ERROR=$(echo "$UPLOAD_RESPONSE" | grep -o '"message":"[^"]*"' | cut -d'"' -f4)
      check "Cloudinary image upload" "fail" "${UPLOAD_ERROR:-no secure_url returned — check API credentials}"
    fi
  fi
fi

# ─────────────────────────────────────────────────────────────────
# SUMMARY
# ─────────────────────────────────────────────────────────────────
print_header "Summary"
echo ""
TOTAL=$((TOTAL_PASS + TOTAL_FAIL))
echo -e "  Total tests : ${TOTAL}"
echo -e "  ${GREEN}Passed      : ${TOTAL_PASS}${NC}"
if [ $TOTAL_FAIL -gt 0 ]; then
  echo -e "  ${RED}Failed      : ${TOTAL_FAIL}${NC}"
else
  echo -e "  Failed      : 0"
fi
echo ""

if [ $TOTAL_FAIL -eq 0 ]; then
  echo -e "  ${GREEN}🎉 All infrastructure checks passed — system is ready!${NC}"
  echo ""
  exit 0
else
  echo -e "  ${RED}⚠️  Some checks failed. Fix the issues above before starting services.${NC}"
  echo ""
  exit 1
fi
