# Phase 7 — AWS Deployment Guide

## Overview

This guide walks through deploying the URL Shortener to AWS using:
- **EC2** — Runs the Spring Boot application + Kafka
- **RDS** — Managed PostgreSQL database
- **ElastiCache** — Managed Redis cache

All resources are in the **same VPC** with proper security group rules.

---

## Architecture

```
                    Internet
                       │
                       ▼
              ┌────────────────┐
              │  Security Group │
              │   (port 8080)   │
              └───────┬────────┘
                      │
              ┌───────▼────────┐
              │   EC2 Instance  │
              │  (t3.micro)     │
              │                 │
              │  ┌───────────┐  │
              │  │ Spring    │  │
              │  │ Boot App  │  │
              │  └───────────┘  │
              │  ┌───────────┐  │
              │  │   Kafka   │  │
              │  │ (local)   │  │
              │  └───────────┘  │
              └───────┬────────┘
                      │
         ┌────────────┼────────────┐
         │                         │
  ┌──────▼──────┐          ┌──────▼──────┐
  │     RDS     │          │ ElastiCache │
  │ PostgreSQL  │          │    Redis    │
  │ (db.t3.micro)│         │(cache.t3.micro)│
  └─────────────┘          └─────────────┘
```

---

## Step-by-Step Deployment

### Step 1: Create VPC and Security Groups

```bash
# Use default VPC or create a new one
# Security groups needed:

# 1. EC2 Security Group (sg-ec2):
#    - Inbound: TCP 8080 from your IP (app access)
#    - Inbound: TCP 22 from your IP (SSH)
#    - Outbound: All traffic

# 2. RDS Security Group (sg-rds):
#    - Inbound: TCP 5432 from sg-ec2 only
#    - (No public access)

# 3. ElastiCache Security Group (sg-redis):
#    - Inbound: TCP 6379 from sg-ec2 only
#    - (No public access)
```

### Step 2: Launch RDS PostgreSQL

```bash
aws rds create-db-instance \
  --db-instance-identifier url-shortener-db \
  --db-instance-class db.t3.micro \
  --engine postgres \
  --engine-version 16 \
  --master-username uttamsharma \
  --master-user-password YourStrongPassword123! \
  --allocated-storage 20 \
  --vpc-security-group-ids sg-rds \
  --no-publicly-accessible \
  --db-name urlshortener
```

**Note the endpoint** after creation (e.g., `url-shortener-db.xxxx.us-east-1.rds.amazonaws.com`)

### Step 3: Create ElastiCache Redis

```bash
aws elasticache create-cache-cluster \
  --cache-cluster-id url-shortener-cache \
  --cache-node-type cache.t3.micro \
  --engine redis \
  --num-cache-nodes 1 \
  --security-group-ids sg-redis
```

**Note the endpoint** after creation.

### Step 4: Launch EC2 Instance

```bash
# Launch t3.micro with Amazon Linux 2023
aws ec2 run-instances \
  --image-id ami-0c02fb55956c7d316 \
  --instance-type t3.micro \
  --key-name your-key-pair \
  --security-group-ids sg-ec2 \
  --count 1
```

### Step 5: Set Up EC2

```bash
# SSH into the instance
ssh -i your-key.pem ec2-user@<EC2_PUBLIC_IP>

# Install Java 21
sudo yum install -y java-21-amazon-corretto-headless

# Install and start Kafka (local to EC2)
wget https://downloads.apache.org/kafka/3.7.0/kafka_2.13-3.7.0.tgz
tar -xzf kafka_2.13-3.7.0.tgz
cd kafka_2.13-3.7.0

# Start Zookeeper (background)
bin/zookeeper-server-start.sh -daemon config/zookeeper.properties

# Start Kafka (background)
bin/kafka-server-start.sh -daemon config/server.properties
```

### Step 6: Deploy the Application

```bash
# On your local machine: build the JAR
./mvnw clean package -DskipTests

# Copy to EC2
scp -i your-key.pem target/url-shortener-0.0.1-SNAPSHOT.jar \
  ec2-user@<EC2_PUBLIC_IP>:~/app.jar

# On EC2: set environment variables
export RDS_ENDPOINT=url-shortener-db.xxxx.us-east-1.rds.amazonaws.com
export RDS_DB_NAME=urlshortener
export RDS_USERNAME=uttamsharma
export RDS_PASSWORD=YourStrongPassword123!
export ELASTICACHE_ENDPOINT=url-shortener-cache.xxxx.cache.amazonaws.com
export KAFKA_ENDPOINT=localhost
export JWT_SECRET=myProductionSecretKey2026VeryLongAndSecure123456789
export APP_BASE_URL=http://<EC2_PUBLIC_IP>:8080/api/v1/urls

# Run the app
java -jar app.jar --spring.profiles.active=aws
```

### Step 7: Create Systemd Service (Production)

```bash
# Create service file
sudo tee /etc/systemd/system/url-shortener.service << 'EOF'
[Unit]
Description=URL Shortener Application
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/home/ec2-user
ExecStart=/usr/bin/java -jar /home/ec2-user/app.jar --spring.profiles.active=aws
EnvironmentFile=/home/ec2-user/.env
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# Create environment file
cat > /home/ec2-user/.env << 'EOF'
RDS_ENDPOINT=url-shortener-db.xxxx.us-east-1.rds.amazonaws.com
RDS_DB_NAME=urlshortener
RDS_USERNAME=uttamsharma
RDS_PASSWORD=YourStrongPassword123!
ELASTICACHE_ENDPOINT=url-shortener-cache.xxxx.cache.amazonaws.com
KAFKA_ENDPOINT=localhost
JWT_SECRET=myProductionSecretKey2026VeryLongAndSecure123456789
APP_BASE_URL=http://<EC2_PUBLIC_IP>:8080/api/v1/urls
EOF

# Start the service
sudo systemctl daemon-reload
sudo systemctl enable url-shortener
sudo systemctl start url-shortener

# Check status
sudo systemctl status url-shortener
```

---

## Cost Estimate (AWS Free Tier)

| Service | Instance | Monthly Cost |
|---------|----------|-------------|
| EC2 | t3.micro | Free (750 hrs/month) |
| RDS | db.t3.micro | Free (750 hrs/month) |
| ElastiCache | cache.t3.micro | ~$13/month (not free tier) |
| **Total** | | **~$13/month** |

**Tip**: ElastiCache is the only cost. To keep it free, you could use Redis on the EC2 instance instead (same as Kafka), but using ElastiCache is more resume-worthy.

---

## Verification

```bash
# Test URL shortening
curl -X POST http://<EC2_PUBLIC_IP>:8080/api/v1/urls/shorten \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://google.com"}'

# Test redirect
curl -v http://<EC2_PUBLIC_IP>:8080/api/v1/urls/1

# Test Swagger UI
# Open in browser: http://<EC2_PUBLIC_IP>:8080/swagger-ui.html

# Test user registration
curl -X POST http://<EC2_PUBLIC_IP>:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "uttam", "password": "password123"}'
```

---

## Security Checklist

- [x] RDS not publicly accessible (private subnet)
- [x] ElastiCache not publicly accessible
- [x] Security groups: least-privilege (only EC2 → RDS/Redis)
- [x] JWT secret from environment variable (not in code)
- [x] DB password from environment variable
- [x] EC2 security group: only port 8080 + SSH from your IP
- [ ] (Stretch) ALB with HTTPS/TLS
- [ ] (Stretch) IAM roles instead of access keys

---

## Stretch Goals (if you want to go further)

1. **ALB + Auto Scaling**: Put EC2 behind an Application Load Balancer with auto-scaling group
2. **HTTPS**: ACM certificate + ALB HTTPS listener
3. **Custom Domain**: Route 53 → ALB → EC2
4. **CI/CD**: GitHub Actions → build JAR → SCP to EC2 → restart service
5. **MSK**: Replace local Kafka with managed AWS MSK
6. **CloudWatch**: Set up monitoring dashboards and alerts

---

## Interview Talking Points

> "I deployed the app to EC2 with RDS PostgreSQL and ElastiCache Redis in the same VPC. The database and cache are in private subnets, only accessible from the app's security group — no public endpoints."

> "I use Spring profiles to manage environment-specific configs — the AWS profile reads connection strings from environment variables, so no credentials are hardcoded."

> "For production, I'd add an ALB with auto-scaling, HTTPS via ACM, and move to ECS Fargate for container orchestration. The Docker setup from Phase 6 makes that migration straightforward."

> "The systemd service ensures the app auto-restarts on failure and starts on boot — basic but effective for a single-instance deployment."
