# Production Deployment Guide: AWS EC2 (Ubuntu)

This runbook outlines the complete end-to-end procedure for deploying **TradeLedger** on an **AWS EC2 Ubuntu** instance using Docker and Docker Compose.

---

## 1. AWS EC2 Instance Provisioning

### Recommended Specifications
- **AMI:** Ubuntu Server 24.04 LTS or 22.04 LTS (64-bit x86_64)
- **Instance Type:** `t3.medium` (2 vCPUs, 4 GiB RAM) minimum for production; `t3.large` recommended for high concurrency.
- **Storage:** 30 GiB gp3 General Purpose SSD.

### AWS Security Group Configuration
In the AWS Management Console, attach a Security Group with the following inbound rules:

| Type | Protocol | Port Range | Source | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **SSH** | TCP | `22` | `Your-IP/32` (Restricted) | Administrative shell access |
| **Custom TCP** | TCP | `8080` | `0.0.0.0/0` (or ALB/CloudFront IP) | TradeLedger REST API & Swagger UI |
| **HTTP** *(optional)* | TCP | `80` | `0.0.0.0/0` | If using an Nginx reverse proxy |
| **HTTPS** *(optional)*| TCP | `443` | `0.0.0.0/0` | If using SSL/TLS certificate |

> [!CAUTION]
> Never expose database ports (`5432`, `27017`, `6379`) to `0.0.0.0/0`. In the provided `docker-compose.yml`, internal container networking bridges the databases securely without requiring external exposure.

---

## 2. Server Preparation & Docker Installation

Connect to your EC2 instance via SSH:
```bash
ssh -i /path/to/your-key.pem ubuntu@<EC2-PUBLIC-IP>
```

Update system packages and install the official Docker Engine and Docker Compose plugin:
```bash
# 1. Update package index and install certificates
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg

# 2. Add Docker's official GPG key
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# 3. Set up the Docker repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 4. Install Docker Engine, CLI, and Compose plugin
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 5. Enable non-root docker access for ubuntu user
sudo usermod -aG docker ubuntu
newgrp docker

# Verify installation
docker --version
docker compose version
```

---

## 3. Clone Repository & Configure Environment

Clone the repository to the EC2 server:
```bash
git clone https://github.com/umar-khatri/trade-ledger.git
cd trade-ledger
```

Create a production `.env` configuration file to customize credentials:
```bash
cat << 'EOF' > .env
POSTGRES_DB=tradeledger
POSTGRES_USER=tradeledger_admin
POSTGRES_PASSWORD=YourSecureProductionPassword123!
MONGO_DB=tradeledger
EOF
```

---

## 4. Build & Launch Application Stack

Launch the entire stack (PostgreSQL, MongoDB, Redis, and TradeLedger) in detached mode:

```bash
docker compose up -d --build
```

### Verify Service Health
Check that all four containers report `healthy`:
```bash
docker compose ps
```

Expected output:
```
NAME                   IMAGE                  COMMAND                  SERVICE      STATUS
tradeledger-postgres   postgres:16-alpine     "docker-entrypoint.s…"   postgres     Up (healthy)
tradeledger-mongo      mongo:7.0              "docker-entrypoint.s…"   mongo        Up (healthy)
tradeledger-redis      redis:7-alpine         "docker-entrypoint.s…"   redis        Up (healthy)
tradeledger-app        trade-ledger-app       "java -XX:+UseG1GC …"   app          Up (healthy)
```

### Inspect Live Application Logs
```bash
docker compose logs -f app
```

---

## 5. Health Check & Verification

### 1. Spring Boot Actuator Health Check
Verify overall system health and datastore connectivity:
```bash
curl -f http://localhost:8080/actuator/health
```

Expected response:
```json
{"status":"UP","components":{"db":{"status":"UP"},"mongo":{"status":"UP"},"redis":{"status":"UP"}}}
```

### 2. Smoke Test API
Create a test portfolio:
```bash
curl -X POST http://localhost:8080/api/v1/portfolios \
  -H "Content-Type: application/json" \
  -d '{"name": "Production Verification Portfolio"}'
```

Deposit cash:
```bash
curl -X POST http://localhost:8080/api/v1/portfolios/<PORTFOLIO_ID>/deposit \
  -H "Content-Type: application/json" \
  -d '{"amount": 100000.00}'
```

Query latest price:
```bash
curl -X GET http://localhost:8080/api/v1/prices/AAPL
```

### 3. Access Swagger UI from Browser
Open your browser and navigate to:
```
http://<EC2-PUBLIC-IP>:8080/swagger-ui.html
```

---

## 6. Operations & Maintenance

### Stopping Services
```bash
docker compose down
```

### Restarting Services
```bash
docker compose restart app
```

### Updating to Latest Code
```bash
git pull origin main
docker compose up -d --build app
```

### Database Backups (Automated Dump)
- **PostgreSQL:**
  ```bash
  docker compose exec postgres pg_dump -U tradeledger_admin tradeledger > backup_$(date +%F).sql
  ```
- **MongoDB:**
  ```bash
  docker compose exec mongo mongodump --db tradeledger --out /data/db/backup_$(date +%F)
  ```
