# AWS deployment — Gurukul Backend

Production stack options:

```text
Internet → EC2 (Docker)          ← simplest for your setup (see EC2.md)
Internet → App Runner / ALB → ECS Fargate
              ↓ IAM auth
         Aurora PostgreSQL (gurukul, eu-north-1)
```

**Your live database:** Aurora cluster `gurukul` in **eu-north-1** with **IAM authentication** (Internet Access Gateway). No DB password in env vars.

| Guide | Use when |
|-------|----------|
| **[EC2.md](./EC2.md)** | Single EC2 + Docker + GitHub clone (**recommended to start**) |
| This file | App Runner, ECS, general reference |

Flyway runs migrations automatically on app startup (`prod` profile).

---

## Prerequisites

| Tool | Purpose |
|------|---------|
| [AWS account](https://aws.amazon.com/) | Hosting |
| [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) | CLI deploy |
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | Build container image |
| Region choice | e.g. `ap-south-1` (Mumbai) — use one region everywhere |

Install and configure AWS CLI:

```bash
aws configure
# AWS Access Key ID, Secret, region (ap-south-1), output json
aws sts get-caller-identity   # verify
```

---

## Step 1 — Create RDS PostgreSQL

### Console (recommended first time)

1. **RDS → Create database**
2. **Engine:** PostgreSQL 16 (or latest stable)
3. **Template:** Free tier (dev) or Production
4. **DB identifier:** `gurukul-db`
5. **Master username:** `gurukul_admin`
6. **Master password:** strong password (save in password manager)
7. **Instance:** `db.t4g.micro` (dev) / `db.t4g.small`+ (prod)
8. **Storage:** 20 GB gp3, enable storage autoscaling if desired
9. **Connectivity:**
   - VPC: default (or your app VPC)
   - **Public access: No** (recommended)
   - VPC security group: create new → `gurukul-rds-sg`
10. **Database name:** `gurukul` (creates the initial DB)
11. Create database — wait until **Available** (~5–10 min)

Note the **Endpoint**, e.g.:

```text
gurukul-db.xxxxx.ap-south-1.rds.amazonaws.com
```

### JDBC URL

```text
jdbc:postgresql://gurukul-db.xxxxx.ap-south-1.rds.amazonaws.com:5432/gurukul
```

### Security group rules

| Type | Port | Source | Purpose |
|------|------|--------|---------|
| Inbound PostgreSQL | 5432 | `gurukul-app-sg` (ECS tasks) | App → RDS |
| Inbound PostgreSQL | 5432 | Your IP (temporary) | Manual psql debug only — remove later |

RDS must **not** be open to `0.0.0.0/0`.

### CLI alternative

```bash
aws rds create-db-instance \
  --db-instance-identifier gurukul-db \
  --engine postgres \
  --engine-version 16.6 \
  --db-instance-class db.t4g.micro \
  --allocated-storage 20 \
  --master-username gurukul_admin \
  --master-user-password 'YOUR_STRONG_PASSWORD' \
  --db-name gurukul \
  --vpc-security-group-ids sg-XXXXXXXX \
  --no-publicly-accessible \
  --backup-retention-period 7 \
  --region ap-south-1
```

---

## Step 2 — Store secrets (Secrets Manager)

Do **not** put DB passwords in git or Docker images.

```bash
aws secretsmanager create-secret \
  --name gurukul/prod/datasource \
  --description "Gurukul RDS credentials" \
  --secret-string '{
    "SPRING_DATASOURCE_URL":"jdbc:postgresql://gurukul-db.xxxxx.ap-south-1.rds.amazonaws.com:5432/gurukul",
    "SPRING_DATASOURCE_USERNAME":"gurukul_admin",
    "SPRING_DATASOURCE_PASSWORD":"YOUR_STRONG_PASSWORD"
  }' \
  --region ap-south-1
```

App env vars (ECS / App Runner):

| Variable | Value |
|----------|--------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_URL` | from secret |
| `SPRING_DATASOURCE_USERNAME` | from secret |
| `SPRING_DATASOURCE_PASSWORD` | from secret |

---

## Step 3 — Build and test locally

```bash
# Unit tests (H2)
mvn clean test

# Production jar
mvn clean package -DskipTests

# Docker image
docker build -t gurukul-backend:latest .

# Optional: run against RDS from laptop (only if RDS SG allows your IP temporarily)
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://..." \
  -e SPRING_DATASOURCE_USERNAME=gurukul_admin \
  -e SPRING_DATASOURCE_PASSWORD='...' \
  gurukul-backend:latest

curl http://localhost:8080/actuator/health
```

On first successful start, Flyway creates `school`, `class_section`, and `student` tables plus seed data.

---

## Step 4 — Push image to ECR

```bash
AWS_REGION=ap-south-1
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REPO=gurukul-backend

aws ecr create-repository --repository-name $ECR_REPO --region $AWS_REGION 2>/dev/null || true

aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

docker tag gurukul-backend:latest \
  $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO:latest

docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO:latest
```

---

## Step 5 — Deploy on ECS Fargate (recommended)

High-level steps:

1. **ECS → Clusters → Create** → `gurukul-cluster`
2. **Task definition** (Fargate, 0.5 vCPU / 1 GB):
   - Container image: ECR URI above
   - Port `8080`
   - Env: `SPRING_PROFILES_ACTIVE=prod` + datasource secrets
   - Health check: `GET /actuator/health` on port 8080
   - Log driver: `awslogs` → CloudWatch group `/ecs/gurukul-backend`
3. **Service** → Application Load Balancer:
   - Public subnets
   - Security group `gurukul-app-sg`: inbound 80/443 from internet, outbound all
   - Target group health check path: `/actuator/health`
4. RDS SG: allow inbound 5432 **only** from `gurukul-app-sg`

Verify:

```bash
curl https://your-alb-dns.ap-south-1.elb.amazonaws.com/actuator/health
curl -X POST https://your-alb/api/v1/schools -H "Content-Type: application/json" -d '{ ... }'
```

---

## Step 6 — Simpler alternative: AWS App Runner

Good for a first deploy without managing ALB/ECS:

1. **App Runner → Create service → Container registry → ECR**
2. Select your image, port `8080`
3. **Environment variables** — add the four `SPRING_*` vars (or link Secrets Manager)
4. **VPC connector** — required so App Runner can reach **private** RDS
5. Health check URL: `/actuator/health`

Trade-off: less control than ECS, easier setup.

---

## Production checklist

| Item | Status in this repo |
|------|---------------------|
| PostgreSQL driver | ✅ in `pom.xml` |
| `prod` profile | ✅ `application-prod.properties` |
| Flyway migrations | ✅ `V1__student_school.sql` |
| Health endpoint | ✅ `/actuator/health` |
| Swagger enabled in prod | ✅ (`/swagger-ui.html`) |
| H2 disabled in prod | ✅ |
| Secrets via env vars | ✅ (you configure in AWS) |
| HTTPS | ⚠️ Add ACM cert on ALB / App Runner custom domain |
| Auth (JWT) | ⚠️ Still `permitAll` — add before real users |
| `X-School-Id` header | ⚠️ OK for dev/staging; replace with JWT for prod |
| RDS backups | ⚠️ Enable in RDS (7+ days retention) |
| Multi-AZ RDS | ⚠️ Enable for production SLA |

---

## CI/CD (optional — GitHub Actions)

See `.github/workflows/ci.yml` — runs tests on every push. Add deploy steps later:

1. Build Docker image
2. Push to ECR
3. Update ECS service (`aws ecs update-service --force-new-deployment`)

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| App cannot connect to RDS | Check VPC, security groups, RDS is in same VPC as app |
| `Connection timed out` | RDS not publicly accessible — app must run in VPC (ECS/App Runner connector) |
| Flyway migration failed | Check CloudWatch logs; fix SQL; for fresh DB, drop and recreate |
| `School not found` on API | Seed data only in Flyway — register school via `POST /api/v1/schools` or rely on seed UUID |
| Java version error on EC2 | Use Docker (Java 25 in image) or Corretto 25 AMI |

---

## Cost estimate (dev / staging, ap-south-1)

| Service | Approx/month |
|---------|----------------|
| RDS `db.t4g.micro` | ~$12–15 |
| ECS Fargate 0.5 vCPU | ~$15–20 |
| ALB | ~$18 |
| **Total** | **~$45–55** |

App Runner + RDS can be slightly cheaper without ALB. Use **Free Tier** where eligible for first year.

---

## Next steps

1. Create RDS in AWS Console
2. Build & push Docker image to ECR
3. Deploy ECS Fargate or App Runner with VPC connector
4. Hit `/actuator/health`, then register a school
5. Add JWT auth before opening to real schools
