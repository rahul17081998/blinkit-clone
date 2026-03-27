# Production Deployment Plan — Blinkit Clone

> Last updated: 2026-03-24

---

## Architecture Overview

```
                        Internet
                           │
                    ┌──────▼──────┐
                    │   Vercel    │  React frontend (static)
                    │  (free CDN) │  VITE_API_BASE_URL → Oracle VM
                    └──────┬──────┘
                           │ HTTPS API calls
                    ┌──────▼──────────────────────────┐
                    │      Oracle Cloud Free VM        │
                    │   (ARM, 24 GB RAM — always free) │
                    │                                  │
                    │  ┌─────────────────────────────┐ │
                    │  │  API Gateway  :8080          │ │  ← only public port
                    │  └──────────────┬──────────────┘ │
                    │  ┌─────────────────────────────┐ │
                    │  │  Eureka     :8761 (internal) │ │
                    │  │  Config Srv :8888 (internal) │ │
                    │  │  Auth       :8081 (internal) │ │
                    │  │  User       :8082 (internal) │ │
                    │  │  ...11 more services         │ │
                    │  └─────────────────────────────┘ │
                    └──────────────────────────────────┘
                           │           │          │
               ┌───────────┘   ┌───────┘   ┌─────┘
               ▼               ▼           ▼
         MongoDB Atlas    Upstash Redis  Docker Kafka
         (free M0 tier)   (free tier)   (on Oracle VM)
```

---

## Free Tier Services Used

| Service | Provider | Free Limit | What It Does |
|---------|----------|-----------|--------------|
| Backend VM | Oracle Cloud Always Free | 4x ARM VMs, 24 GB RAM total | Runs all 13 Spring Boot services |
| Frontend | Vercel | Unlimited deploys | Serves React app globally via CDN |
| MongoDB | MongoDB Atlas M0 | 512 MB storage | All service databases |
| Redis | Upstash Redis | 10,000 cmd/day | JWT blacklist, OTP, refresh tokens |
| Kafka | Docker on Oracle VM | No limit (local) | All async events between services |
| Images | Cloudinary | 25 GB bandwidth | Product/profile photo CDN |
| Email | Gmail SMTP | 500 emails/day | OTP, order confirmations |

> **Oracle Cloud signup:** https://www.oracle.com/cloud/free/
> Use the "Always Free" tier — choose ARM shape (Ampere A1). 4 OCPUs + 24 GB RAM total for free forever.

---

## Phase 1 — External Services Setup

### 1.1 MongoDB Atlas (skip if already done)

1. Go to https://cloud.mongodb.com
2. Create a free M0 cluster
3. Create a database user (username + strong password)
4. Network access → Allow `0.0.0.0/0` (or add your Oracle VM IP later)
5. Connect → Drivers → copy the connection string:
   ```
   mongodb+srv://<user>:<password>@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority
   ```
6. Paste into `.env.prod` as `MONGO_URI`

---

### 1.2 Upstash Redis

1. Go to https://upstash.com → Create a Redis database
2. Select region closest to your Oracle VM (e.g. `ap-southeast-1`)
3. After creation, go to **Details** tab:
   - `Endpoint` → `REDIS_HOST` in `.env.prod`
   - `Port` → `REDIS_PORT` (usually 6379 for TLS, or 6380)
   - `Password` → `REDIS_PASSWORD`
4. Enable TLS (Upstash Redis requires TLS in prod)

> **Note:** Upstash Redis uses TLS. Update `application-prod.yml` if needed:
> ```yaml
> spring:
>   data:
>     redis:
>       ssl:
>         enabled: true
> ```

---

### 1.3 Kafka — Docker on Oracle VM (no signup needed)

Upstash removed their Kafka product. We run Kafka in Docker on the same Oracle VM.
This is fully free with no message limits.

**No action needed in this phase** — `docker-compose.infra.yml` handles Kafka.
`KAFKA_BOOTSTRAP_SERVERS=localhost:9092` is already set in `.env.prod`.

The same `docker-compose.infra.yml` used in dev runs on the Oracle VM. The prod override
(`docker-compose.infra.prod.yml`) binds all ports to `127.0.0.1` so Kafka is never
exposed to the internet.

---

### 1.4 Cloudinary (skip if already done)

Already working in dev. Same credentials work in prod — just add them to `.env.prod`.

---

### 1.5 Gmail SMTP App Password

1. Go to https://myaccount.google.com/apppasswords
2. Enable 2FA first if not already done
3. Generate App Password → select "Mail" + "Other device"
4. Copy the 16-char password → `SMTP_PASSWORD` in `.env.prod`

---

## Phase 2 — Oracle Cloud VM Setup

### 2.1 Create the VM

1. Sign in to https://cloud.oracle.com
2. Compute → Instances → Create Instance
3. Shape: **Ampere A1** (ARM) — `VM.Standard.A1.Flex`
   - OCPUs: 4, Memory: 24 GB (max free tier)
4. Image: **Ubuntu 22.04** (recommended)
5. Add your SSH public key
6. Create and wait for instance to be `RUNNING`
7. Note the **Public IP address**

### 2.2 Open Firewall Ports

In Oracle Cloud Console → Virtual Cloud Network → Security List → Add Ingress Rules:

| Port | Protocol | Purpose |
|------|----------|---------|
| 22 | TCP | SSH access |
| 8080 | TCP | API Gateway (public — only this one!) |

> All other service ports (8081–8091, 8761, 8888) stay internal — never expose them publicly.

Also run on the VM:
```bash
sudo iptables -I INPUT -p tcp --dport 8080 -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 22 -j ACCEPT
sudo netfilter-persistent save
```

### 2.3 Install Java & Tools

```bash
# SSH into VM
ssh ubuntu@<oracle-vm-ip>

# Update packages
sudo apt update && sudo apt upgrade -y

# Install Java 21
sudo apt install -y openjdk-21-jdk

# Verify
java -version    # should show openjdk 21

# Install useful tools (nc is needed by start-backend.sh wait_for_port)
sudo apt install -y git curl wget unzip lsof net-tools netcat-openbsd

# Install mongosh (for infra-check)
wget -qO- https://www.mongodb.org/static/pgp/server-7.0.asc | sudo tee /etc/apt/trusted.gpg.d/server-7.0.asc
echo "deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu jammy/mongodb-org/7.0 multiverse" \
  | sudo tee /etc/apt/sources.list.d/mongodb-org-7.0.list
sudo apt update && sudo apt install -y mongodb-mongosh
```

### 2.4 Install Docker + Colima (or plain Docker)

```bash
# Install Docker (no Colima needed on Linux — use Docker directly)
sudo apt install -y docker.io docker-compose-plugin
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker ubuntu
# Log out and back in for group to take effect
```

### 2.5 Clone the Repository

```bash
cd ~
git clone https://github.com/rahul17081998/blinkit-clone.git
cd blinkit-clone/backend
```

### 2.6 Create `.env.prod`

```bash
cp .env.example .env.prod
nano .env.prod   # fill in all values from Phase 1
```

Key values to fill in:
```
MONGO_URI="mongodb+srv://..."
REDIS_HOST=<upstash-host>.upstash.io
REDIS_PASSWORD=<upstash-password>
KAFKA_BOOTSTRAP_SERVERS=localhost:9092   # Kafka runs in Docker on this VM
JWT_SECRET_KEY=<generate: openssl rand -base64 64>
INTERNAL_SECRET=<generate: openssl rand -base64 32>
EUREKA_SERVER_URL=http://localhost:8761/eureka
CONFIG_SERVER_URL=http://localhost:8888
FRONTEND_URL=https://<your-app>.vercel.app
PASSWORD_RESET_BASE_URL=https://<your-app>.vercel.app/reset-password
ADMIN_EMAIL=<your-admin-email>
SMTP_USERNAME=<gmail>
SMTP_PASSWORD=<app-password>
CLOUDINARY_CLOUD_NAME=<your-name>
CLOUDINARY_API_KEY=<your-key>
CLOUDINARY_API_SECRET=<your-secret>
```

Generate strong secrets:
```bash
# JWT secret (64 chars)
openssl rand -base64 64

# Internal secret (32 chars)
openssl rand -base64 32
```

---

## Phase 3 — Build & Deploy Backend

### 3.1 Build All JARs

```bash
cd ~/blinkit-clone/backend
mvn clean package -DskipTests -q
echo "Build complete"
```

Expected output: 13 JAR files, one per service in `{service}/target/`.

### 3.2 Start Kafka infrastructure (Docker on Oracle VM)

In prod, Redis is Upstash (cloud) but Kafka runs in Docker locally.
`start-backend.sh prod` calls `start-infra.sh` automatically, which starts Docker containers
using `docker-compose.kafka-prod.yml` (Kafka + Zookeeper + Kafka UI, all bound to 127.0.0.1).

To start infra manually:
```bash
cd ~/blinkit-clone/backend
docker compose -f docker-compose.kafka-prod.yml up -d
```

Access Kafka UI via SSH tunnel (never exposed publicly):
```bash
# From your local machine:
ssh -L 9093:localhost:9093 ubuntu@140.238.244.245   # → Kafka UI at http://localhost:9093
```

### 3.3 Run Infra Check

```bash
PROFILE=prod bash infra-check.sh
```

All 19 checks must pass before proceeding.

### 3.4 Start All Services

```bash
bash start-backend.sh prod
```

Expected flow:
```
[INFO] Starting with profile: prod
[INFO] Loaded .env.prod
── Wave 1: Eureka Server ──────────────────────
[INFO] eureka-server started (PID ...) — ready!
── Wave 2: Config Server ──────────────────────
[INFO] config-server started (PID ...) — ready!
── Wave 3: All business services (parallel) ───
[INFO] All 12 services launched...
[INFO] auth-service ... ready!
[INFO] user-service ... ready!
...
✔  All services are UP
```

### 3.5 Verify All Services Registered

```bash
curl -s http://localhost:8761 | grep -o 'UP.*instances'
# or
curl -s http://localhost:8080/actuator/health | python3 -m json.tool | grep -A2 applications
```

Expected: 13 services registered in Eureka.

---

## Phase 4 — Keep Services Running (systemd)

Services started via `start-backend.sh` will die when the SSH session closes. Use systemd to keep them alive.

### 4.1 Create a systemd service

```bash
sudo nano /etc/systemd/system/blinkit-backend.service
```

```ini
[Unit]
Description=Blinkit Clone Backend
After=network.target docker.service

[Service]
Type=forking
User=ubuntu
WorkingDirectory=/home/ubuntu/blinkit-clone/backend
ExecStart=/bin/bash /home/ubuntu/blinkit-clone/backend/start-backend.sh prod
ExecStop=/bin/bash /home/ubuntu/blinkit-clone/backend/stop-backend.sh
Restart=on-failure
RestartSec=30

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable blinkit-backend
sudo systemctl start blinkit-backend

# Check status
sudo systemctl status blinkit-backend
```

### 4.2 Auto-restart on crash

The `Restart=on-failure` in the systemd unit handles this. Check logs:
```bash
journalctl -u blinkit-backend -f
# or
tail -f ~/blinkit-clone/backend/logs/api-gateway.log
```

---

## Phase 5 — Frontend Deployment (Vercel)

### 5.1 Create Vercel account

1. Go to **vercel.com** → Sign Up → **Continue with GitHub**
2. Authorize Vercel to access your GitHub account

### 5.2 Import project on Vercel

1. Click **Add New → Project**
2. Find `blinkit-clone` repo → click **Import**
3. Configure:

| Setting | Value |
|---------|-------|
| Root Directory | `frontend` |
| Framework Preset | Vite (auto-detected) |
| Build Command | `npm run build` (default) |
| Output Directory | `dist` (default) |

4. Scroll to **Environment Variables** → paste these 3 vars:

```
VITE_CLOUDINARY_CLOUD_NAME=dsouj58b0
VITE_CLOUDINARY_UPLOAD_PRESET=bqniosw3
```

> Do NOT set `VITE_API_BASE_URL` — the `vercel.json` proxy handles API routing (see §5.3 below)

5. Click **Deploy** — takes ~1 minute

### 5.3 Why no VITE_API_BASE_URL — the Vercel proxy

The frontend is served over **HTTPS** but the Oracle VM only speaks **HTTP**.
Browsers block HTTP calls from HTTPS pages (Mixed Content error).

Fix: `frontend/vercel.json` proxies all `/api/*` calls through Vercel (server-to-server):

```json
{
  "rewrites": [
    {
      "source": "/api/:path*",
      "destination": "http://140.238.244.245:8080/api/:path*"
    },
    {
      "source": "/((?!api/).*)",
      "destination": "/index.html"
    }
  ]
}
```

- First rule: `/api/*` → forwarded to Oracle VM (HTTP, server-side — no mixed content)
- Second rule: all other URLs → `index.html` (required for React Router deep links to work)

### 5.4 Update backend CORS after getting Vercel URL

Once deployed, copy the permanent Vercel URL (e.g. `https://blinkit-clone-psi-murex.vercel.app`).

Update `.env.prod` locally:
```
FRONTEND_URL=https://blinkit-clone-psi-murex.vercel.app
PASSWORD_RESET_BASE_URL=https://blinkit-clone-psi-murex.vercel.app/reset-password
```

Upload to Oracle VM:
```bash
scp -i ~/Downloads/ssh-key-2026-03-24-2.key \
  /Users/rahulkumar/Documents/mongoDb-springboot/blinkit-clone/backend/.env.prod \
  ubuntu@140.238.244.245:~/blinkit-clone/backend/.env.prod
```

Restart the gateway:
```bash
ssh -i ~/Downloads/ssh-key-2026-03-24-2.key ubuntu@140.238.244.245 \
  "pkill -f 'api-gateway'; sleep 2; cd ~/blinkit-clone/backend && source .env.prod && \
   export SPRING_PROFILES_ACTIVE=prod && \
   nohup java -jar api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar \
   --spring.profiles.active=prod > logs/api-gateway.log 2>&1 & echo 'Gateway restarted'"
```

### 5.5 Deployed URLs

| URL | Purpose |
|-----|---------|
| `https://blinkit-clone-psi-murex.vercel.app` | **Permanent production URL** — always use this |
| `https://blinkit-clone-git-main-rahul17081998s-projects.vercel.app` | Permanent alias for `main` branch |
| `https://blinkit-clone-xxxx.vercel.app` | Per-deployment preview — ignore these |

### 5.6 Verify Frontend

- [ ] Home page loads with products
- [ ] Login works → avatar shows immediately in header
- [ ] Deep links work: `/product/xxx`, `/profile`, `/orders`
- [ ] Password reset email link opens correctly
- [ ] Add to cart → checkout → order placed
- [ ] Admin dashboard accessible with admin account

---

## Phase 6 — Updating the Frontend

Vercel auto-deploys on every push to `main`. Workflow:

```bash
# Make changes locally
git add .
git commit -m "fix: your change"
git push origin feature/your-branch
# Open PR on GitHub → merge to main → Vercel auto-deploys in ~1 min
```

**To update env vars on Vercel:**
1. Go to Vercel Dashboard → project → **Settings → Environment Variables**
2. Edit the variable → Save
3. Go to **Deployments** → click latest → **Redeploy** (to pick up the new var)

**Permanent URL never changes** — `https://blinkit-clone-psi-murex.vercel.app` always points to the latest `main` deployment.

---

## Day-to-Day Operations

> Oracle VM IP: **140.238.244.245**
> SSH key: `~/Downloads/ssh-key-2026-03-24-2.key`

---

### SSH into the VM

```bash
# From your local Mac
ssh -i ~/Downloads/ssh-key-2026-03-24-2.key ubuntu@140.238.244.245
```

Keep the key permissions correct (required by SSH):
```bash
chmod 400 ~/Downloads/ssh-key-2026-03-24-2.key
```

---

### Start all services

```bash
# SSH in first, then:
cd ~/blinkit-clone/backend
bash start-backend.sh prod
```

What happens:
1. Loads `.env.prod`
2. Starts Docker (Kafka + Zookeeper + Kafka UI) via `docker-compose.kafka-prod.yml`
3. Runs `infra-check.sh` — verifies MongoDB, Redis, Kafka connectivity
4. Starts Eureka (waits until ready on :8761)
5. Starts Config Server (waits until ready on :8888)
6. Starts all 12 business services in parallel (waits up to 120s each)
7. Prints public access URL when done

Expected final output:
```
✔  All services are UP  (profile: prod)

   API Gateway (public) →  http://140.238.244.245:8080/actuator/health
   Eureka dashboard     →  http://localhost:8761
   ...
```

---

### Stop all services (Spring Boot + Docker)

```bash
cd ~/blinkit-clone/backend
bash stop-infra.sh
```

This:
1. Kills all Spring Boot JARs (reads PIDs from `.pids`)
2. Stops Docker containers (`docker-compose.kafka-prod.yml`)

To stop Spring Boot services only (keep Kafka running):
```bash
bash stop-backend.sh
```

---

### View logs

```bash
# All services at once (Ctrl+C to stop)
tail -f ~/blinkit-clone/backend/logs/*.log

# One specific service
tail -f ~/blinkit-clone/backend/logs/api-gateway.log
tail -f ~/blinkit-clone/backend/logs/auth-service.log
tail -f ~/blinkit-clone/backend/logs/order-service.log

# Last 200 lines of a service (not live)
tail -200 ~/blinkit-clone/backend/logs/payment-service.log

# Search logs for errors
grep -i "error\|exception" ~/blinkit-clone/backend/logs/user-service.log | tail -50
```

### Download all logs to local Mac

Run this on your **local Mac:**
```bash
scp -i ~/Downloads/ssh-key-2026-03-24-2.key "ubuntu@140.238.244.245:~/blinkit-clone/backend/logs/*.log" /Users/rahulkumar/Documents/mongoDb-springboot/blinkit-clone/backend/logs-prod/
```

> Make sure the local destination exists first: `mkdir -p .../backend/logs-prod`

---

### Monitor RAM & CPU Usage

```bash
# Overall RAM summary
free -h
```
Example output:
```
               total        used        free      shared  buff/cache   available
Mem:            23Gi       7.5Gi        11Gi       5.0Mi       4.6Gi        15Gi
Swap:             0B          0B          0B
```

```bash
# RAM usage per Java process (top consumers first)
ps -eo pid,comm,rss --sort=-rss | head -20
```
Example output:
```
    PID COMMAND           RSS
 262627 java            1092676
 263836 java             471028
 263827 java             470056
 263817 java             469396
   1051 dockerd           88988
```

```bash
# RAM usage mapped to service JAR names
ps -eo pid,comm,rss --sort=-rss | grep java | while read pid comm rss; do
  echo "$rss KB - $(ls -la /proc/$pid/fd 2>/dev/null | grep -o '[^ ]*\.jar' | head -1)"
done
```
Example output:
```
1092716 KB -                        ← Kafka broker (Docker/JVM)
471028 KB - /home/ubuntu/blinkit-clone/backend/delivery-service/target/delivery-service-1.0.0-SNAPSHOT.jar
470056 KB - /home/ubuntu/blinkit-clone/backend/cart-service/target/cart-service-1.0.0-SNAPSHOT.jar
469412 KB - /home/ubuntu/blinkit-clone/backend/product-service/target/product-service-1.0.0-SNAPSHOT.jar
466400 KB - /home/ubuntu/blinkit-clone/backend/order-service/target/order-service-1.0.0-SNAPSHOT.jar
464656 KB - /home/ubuntu/blinkit-clone/backend/user-service/target/user-service-1.0.0-SNAPSHOT.jar
457528 KB - /home/ubuntu/blinkit-clone/backend/payment-service/target/payment-service-1.0.0-SNAPSHOT.jar
452136 KB - /home/ubuntu/blinkit-clone/backend/inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar
449348 KB - /home/ubuntu/blinkit-clone/backend/auth-service/target/auth-service-1.0.0-SNAPSHOT.jar
445920 KB - /home/ubuntu/blinkit-clone/backend/api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar
434644 KB - /home/ubuntu/blinkit-clone/backend/notification-service/target/notification-service-1.0.0-SNAPSHOT.jar
431176 KB - /home/ubuntu/blinkit-clone/backend/eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar
393840 KB - /home/ubuntu/blinkit-clone/backend/coupon-service/target/coupon-service-1.0.0-SNAPSHOT.jar
393012 KB -                         ← Zookeeper (Docker/JVM)
362112 KB - /home/ubuntu/blinkit-clone/backend/review-service/target/review-service-1.0.0-SNAPSHOT.jar
329824 KB - /home/ubuntu/blinkit-clone/backend/config-server/target/config-server-1.0.0-SNAPSHOT.jar
135916 KB -                         ← Kafka UI (Docker/JVM)
```

> Unknowns (no JAR path) = Docker Java processes (Kafka broker ~1GB, Zookeeper ~384MB, Kafka UI ~133MB)

```bash
# Disk usage
df -h /
```
Example output:
```
Filesystem      Size  Used Avail Use% Mounted on
/dev/sda1        45G  6.6G   39G  15% /
```

---

### Check which services are running

```bash
# Quick port scan (all services)
for port in 8761 8888 8080 8081 8082 8083 8084 8085 8086 8087 8088 8089 8090 8091; do
  nc -z localhost $port 2>/dev/null && echo "UP  :$port" || echo "DOWN :$port"
done

# Gateway health (from internet)
curl -s http://140.238.244.245:8080/actuator/health | python3 -m json.tool

# Eureka registered services (internal)
curl -s http://localhost:8761/eureka/apps | grep -o '<name>[^<]*</name>'
```

---

### Pull latest code and restart

**Option A — Full restart (all services):**
```bash
cd ~/blinkit-clone/backend
bash stop-infra.sh            # stop everything
git pull origin main          # get latest code
mvn clean package -DskipTests -q   # rebuild all JARs (~5 min)
bash start-backend.sh prod    # start everything
```

**Option B — Restart a single service after a fix:**
```bash
cd ~/blinkit-clone/backend
git pull origin main

# Rebuild only the changed service
mvn install -pl auth-service -am -DskipTests -q

# Kill old process and start new JAR
pkill -f "auth-service" ; sleep 2
source .env.prod
export SPRING_PROFILES_ACTIVE=prod
nohup java -jar auth-service/target/auth-service-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=prod > logs/auth-service.log 2>&1 &
echo "auth-service restarted (PID $!)"
```

Replace `auth-service` with any other service name as needed.

---

### Open SSH tunnel to Kafka UI (from local Mac)

```bash
# Run this on your Mac (not the VM)
ssh -i ~/Downloads/ssh-key-2026-03-24-2.key \
    -L 9093:localhost:9093 \
    ubuntu@140.238.244.245 -N
```

Then open: http://localhost:9093 in your browser.
Press Ctrl+C to close the tunnel.

---

### Stop the Oracle VM (when not needed)

From Oracle Cloud Console → Compute → Instances → select VM → **Stop**
(Keeps your data and IP, just stops billing for compute — though it's free anyway)

To restart: Console → Instances → **Start**
VM typically boots and is SSH-accessible within 2 minutes.

> **Note:** When VM restarts, you need to manually run `bash start-backend.sh prod` unless systemd is configured (Phase 4).

---

## Phase 7 — Post-Deployment Smoke Tests

Run these from your local machine after everything is deployed:

```bash
VM_IP=<your-oracle-vm-ip>
FRONTEND_URL=https://<your-app>.vercel.app

# 1. Gateway health
curl -s http://$VM_IP:8080/actuator/health | python3 -m json.tool

# 2. Signup
curl -s -X POST http://$VM_IP:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Prod","lastName":"Test","email":"prodtest@example.com","phone":"9999999999","password":"Test@1234"}'

# 3. Products publicly accessible
curl -s "http://$VM_IP:8080/api/products?page=0&size=5" | python3 -c \
  "import sys,json; d=json.load(sys.stdin); print('Products:', d['data']['totalElements'])"

# 4. Coupons
curl -s http://$VM_IP:8080/api/coupons/active | python3 -c \
  "import sys,json; d=json.load(sys.stdin); [print(c['code']) for c in d.get('data',[])]"
```

---

## Checklist — Before Going Live

### Infrastructure
- [ ] Oracle Cloud VM created (ARM, 4 OCPU, 24 GB RAM)
- [ ] Port 8080 open in Oracle security list + iptables
- [ ] Java 21 installed on VM
- [ ] MongoDB Atlas M0 cluster running, IP whitelist updated
- [ ] Upstash Redis created, TLS enabled
- [ ] Docker installed on Oracle VM, `docker compose` available
- [ ] Kafka starts successfully via `docker-compose.infra.yml` on Oracle VM

### Backend
- [ ] `.env.prod` fully filled in (no `<placeholder>` values remaining)
- [ ] `JWT_SECRET_KEY` is >= 64 chars random string
- [ ] `INTERNAL_SECRET` is >= 32 chars random string
- [ ] All JARs built successfully (`mvn clean package -DskipTests`)
- [ ] `PROFILE=prod bash infra-check.sh` → 19/19 pass
- [ ] `bash start-backend.sh prod` → all 14 services UP
- [ ] All 13 services registered in Eureka
- [ ] systemd service enabled and running

### Frontend
- [ ] `frontend/.env.production` has correct `VITE_API_BASE_URL`
- [ ] Deployed to Vercel with env var set
- [ ] Login works end-to-end
- [ ] CORS configured in API Gateway for Vercel domain

### Security
- [ ] Only port 8080 exposed publicly (8081–8091, 8761, 8888 are internal only)
- [ ] No secrets in any committed file
- [ ] `.env.prod` is git-ignored
- [ ] Actuator endpoints restricted (`health,info` only in `application-prod.yml`)

---

## Updating Production

When you push new code:

```bash
# SSH into VM first
ssh -i ~/Downloads/ssh-key-2026-03-24-2.key ubuntu@140.238.244.245

# Then on Oracle VM
cd ~/blinkit-clone/backend
git pull origin main

# Rebuild changed service(s) only
mvn install -pl auth-service -am -DskipTests -q

# Restart just that service
pkill -f "auth-service"
sleep 2
source .env.prod
java -jar auth-service/target/auth-service-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=prod > logs/auth-service.log 2>&1 &
```

For a full redeploy:
```bash
bash stop-backend.sh
git pull origin main
mvn clean package -DskipTests -q
bash start-backend.sh prod
```

Frontend (auto-deploys via Vercel on every push to `main`).

---

## Monitoring

```bash
# Watch all logs in real time
tail -f logs/*.log

# Check which services are up
for port in 8080 8081 8082 8083 8084 8085 8086 8087 8088 8089 8090 8091; do
  status=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$port/actuator/health)
  echo "Port $port: HTTP $status"
done

# Eureka registered services
curl -s http://localhost:8761/eureka/apps | grep -o '<application>.*</application>' | grep -o '<name>[^<]*</name>'
```

---

## Cost Summary

| Resource | Provider | Cost |
|----------|----------|------|
| VM (4 CPU, 24GB RAM) | Oracle Cloud | **Free forever** |
| Frontend CDN | Vercel | **Free** |
| MongoDB | Atlas M0 | **Free** |
| Redis | Upstash | **Free** (10K cmd/day) |
| Kafka | Docker on Oracle VM | **Free** (no limits) |
| Image CDN | Cloudinary | **Free** (25 GB bandwidth) |
| Email | Gmail SMTP | **Free** (500/day) |
| **Total** | | **$0/month** |

---

*For issues, check service logs in `backend/logs/{service-name}.log`*
