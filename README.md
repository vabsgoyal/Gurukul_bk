# Gurukul Backend

School management backend for Gurukul, built with Spring Boot and Maven. The project is developed as vertical slices; the current slice implements **school registration**, **class-sections**, and **student enrollment** with multi-tenant school scoping.

## Prerequisites

- Java 25+
- Maven, or the included Maven wrapper

No external database is required for local development — the app uses **H2 in-memory** by default.

For production / Aurora testing you also need:

- [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) configured (`aws configure`)
- IAM permission `rds-db:connect` on the Aurora cluster DB user (see [Aurora (production)](#aurora-production))

## Setup

```bash
git clone https://github.com/vabsgoyal/Gurukul_bk.git
cd Gurukul_bk
mvn clean install
```

## Run locally (default — H2)

```bash
mvn spring-boot:run
```

Or with the wrapper:

```bash
./mvnw spring-boot:run
```

The **`local`** profile is active by default. The application starts at:

```text
http://localhost:8080
```

If port 8080 is already in use:

```bash
lsof -i :8080          # find the PID
kill <PID>             # stop the other process
# or use another port:
export PORT=8081
mvn spring-boot:run
```

### Swagger UI (API docs)

With the app running:

| Environment | Swagger UI | OpenAPI JSON |
|-------------|------------|--------------|
| **Local** (`local` profile) | http://localhost:8080/swagger-ui.html | http://localhost:8080/v3/api-docs |
| **Production** (EC2, `prod` profile) | `http://<EC2_HOST>:8080/swagger-ui.html` | `http://<EC2_HOST>:8080/v3/api-docs` |

Replace `<EC2_HOST>` with your EC2 public IP or domain (same host as `HEALTH_CHECK_URL` in GitHub Actions, without the path).

Every `/api/v1/**` endpoint requires the **`X-School-Id`** header. Swagger UI includes it on all operations — set it to the example school UUID below before trying requests.

> Swagger on production exposes your API schema publicly. Restrict EC2 security group access or add auth before wider exposure.

Packaged jar:

```bash
mvn clean package
java -jar target/gurukul-backend-0.0.1-SNAPSHOT.jar
```

## Profiles

| Profile | Database | Use when |
|---------|----------|----------|
| `local` (default) | H2 in-memory | Day-to-day development |
| `prod` | Aurora PostgreSQL via IAM | EC2 production, local Aurora testing |

## Production environment

The live backend runs on **AWS EC2** (`eu-north-1`) with **Aurora PostgreSQL** and **IAM auth**. Deploys are fully automated — no manual SSH for routine releases.

| Item | Value |
|------|--------|
| Base URL | `http://13.60.11.238:8080` |
| Health | `http://13.60.11.238:8080/actuator/health` |
| Region | `eu-north-1` |
| Database | Aurora cluster `gurukul` (Flyway migrations on startup) |
| Swagger | Disabled on `prod` |

```bash
# Quick prod check
curl http://13.60.11.238:8080/actuator/health

curl http://13.60.11.238:8080/api/v1/schools/11111111-1111-1111-1111-111111111111

curl http://13.60.11.238:8080/api/v1/students \
  -H "X-School-Id: 11111111-1111-1111-1111-111111111111"
```

> The EC2 public IP may change if the instance is stopped and started without an Elastic IP. Update `HEALTH_CHECK_URL` in GitHub secrets if it changes.

## Team workflow (CI/CD)

Every developer follows the same flow. **Merging to `main` deploys to production** — there is no separate staging environment yet.

| Step | Action | Result |
|------|--------|--------|
| 1 | Branch off `main`, make changes | Local dev with H2 (`mvn spring-boot:run`) |
| 2 | Open a **pull request** | **CI** runs — tests, JAR build, Docker build verify ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) |
| 3 | Get review, fix CI if needed | PR must pass CI before merge |
| 4 | **Merge PR → `main`** | **Deploy** runs automatically ([`.github/workflows/deploy.yml`](.github/workflows/deploy.yml)) |
| 5 | Wait for Actions to finish (~2 min) | JAR uploaded to S3 → EC2 restarts via SSM → health smoke test |
| 6 | Test on prod | Use `http://13.60.11.238:8080` (see examples above) |

```text
PR opened/updated  →  CI (test + build)           →  no deploy
Merge to main        →  Deploy (test → S3 → EC2)   →  live on prod
```

**What deploy does on each merge to `main`:**

1. `mvn clean test`
2. `mvn package` → upload JAR to S3 (`releases/gurukul-backend.jar` + SHA-tagged copy)
3. AWS SSM runs `/opt/gurukul/deploy-from-s3.sh` on EC2 (pull JAR, restart systemd, local health check)
4. GitHub Actions smoke-tests `HEALTH_CHECK_URL`

Monitor runs: [GitHub Actions](https://github.com/vabsgoyal/Gurukul_bk/actions). Manual redeploy: **Actions → Deploy → Run workflow**.

**Important for the team:**

- **Shared prod** — everyone tests against the same environment after merge.
- **Flyway migrations** run against Aurora on app startup — review SQL in `src/main/resources/db/migration/` carefully before merging.
- **Auth is not enforced yet** — prod APIs are open; do not put real PII in test data until JWT is added.
- **Smoke test only checks `/actuator/health`** — verify your specific API changes manually after deploy.

Infrastructure setup (one-time): **[deploy/aws/EC2.md](deploy/aws/EC2.md)** · **[deploy/aws/PIPELINE.md](deploy/aws/PIPELINE.md)**

## Database — local (H2)

| Setting | Value |
|---------|--------|
| JDBC URL | `jdbc:h2:mem:gurukul` |
| Username | `sa` |
| Password | *(empty)* |

Schema is managed by **Flyway** (`src/main/resources/db/migration/`). Data is reset when the application restarts.

### H2 Console

With the app running on `local`, open:

**http://localhost:8080/h2-console**

> Spring Boot 4 requires the `spring-boot-h2console` dependency (already in `pom.xml`) for the console UI to load.

Use the JDBC URL above with user `sa` and no password.

## Aurora (production)

Production uses **Aurora PostgreSQL** with **IAM database authentication** via the [AWS Advanced JDBC Wrapper](https://github.com/aws/aws-advanced-jdbc-wrapper).

This is required when Aurora **Internet Access Gateway (IAG)** is enabled — AWS does not allow password-only auth from the internet in that configuration. IAG cannot be disabled after cluster creation.

### Run locally against Aurora

```bash
export SPRING_PROFILES_ACTIVE=prod
export AWS_REGION=eu-north-1
export SPRING_DATASOURCE_URL="jdbc:aws-wrapper:postgresql://your-cluster-host:5432/postgres?sslmode=require"
export SPRING_DATASOURCE_USERNAME=postgres

mvn spring-boot:run
```

**No `SPRING_DATASOURCE_PASSWORD`** — the JDBC wrapper generates short-lived IAM tokens from your AWS credential chain (`~/.aws/credentials` locally, ECS task role in AWS).

Startup takes ~12s (network + IAM token + Flyway validation against RDS).

### IAM policy

Your IAM user or ECS task role needs `rds-db:connect`:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "rds-db:connect",
    "Resource": "arn:aws:rds-db:eu-north-1:ACCOUNT_ID:dbuser:CLUSTER_RESOURCE_ID/postgres"
  }]
}
```

Get the cluster resource ID:

```bash
aws rds describe-db-clusters \
  --db-cluster-identifier gurukul \
  --region eu-north-1 \
  --query 'DBClusters[0].DbClusterResourceId' \
  --output text
```

### Connectivity test (optional)

```bash
export RDSHOST="your-cluster-host"
export PGPASSWORD="$(aws rds generate-db-auth-token \
  --hostname "$RDSHOST" --port 5432 --username postgres --region eu-north-1)"
psql "host=$RDSHOST port=5432 dbname=postgres user=postgres sslmode=require" -c "SELECT 1;"
```

### Health check

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

### Docker

```bash
docker build -t gurukul-backend .

# Copy deploy/aws/.env.example → .env and set prod env vars (IAM URL, no password)
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e AWS_REGION=eu-north-1 \
  -e SPRING_DATASOURCE_URL="jdbc:aws-wrapper:postgresql://your-cluster-host:5432/postgres?sslmode=require" \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -v ~/.aws:/root/.aws:ro \
  gurukul-backend
```

Mount AWS credentials for local Docker runs, or use an ECS task role in AWS (no keys in the container).

Docker is optional for local prod-profile testing. **Production deploys use a JAR on EC2**, not Docker.

Other AWS guides: **[deploy/aws/DEPLOYMENT.md](deploy/aws/DEPLOYMENT.md)** (App Runner / ECS — not used for current prod)

## Multi-tenant: `X-School-Id` header

Most `/api/v1/**` requests **must** include the school tenant UUID:

| Header | Required | Example |
|--------|----------|---------|
| `X-School-Id` | Yes (tenant-scoped APIs) | `11111111-1111-1111-1111-111111111111` |

**Exceptions (no header):** `POST /api/v1/schools`, `GET /api/v1/schools/{id}`

Missing or invalid headers return **400 Bad Request**. Unknown school UUID returns **400 School not found**. Records are scoped to the school in the header — cross-tenant access by ID returns **404**.

Register a new school via `POST /api/v1/schools` and use the returned `id` as `X-School-Id`. For local dev, use the seeded demo school ID above.

## API — Schools (tenant registration)

Base path: `/api/v1/schools` — **no `X-School-Id` header**

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/schools` | Register a new school (tenant) |
| GET | `/api/v1/schools/{id}` | Get school details |

**Register example:**

```bash
curl -X POST http://localhost:8080/api/v1/schools \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Delhi Public School",
    "address": "45 Ring Road",
    "city": "Jaipur",
    "state": "Rajasthan",
    "pincode": "302001",
    "contactEmail": "admin@dps.example",
    "contactPhone": "9876543210",
    "principalName": "Dr. Anita Verma",
    "directorName": "Mr. Sanjay Mehta"
  }'
```

**Get seeded demo school:**

```bash
curl http://localhost:8080/api/v1/schools/11111111-1111-1111-1111-111111111111
```

**Response** includes `data.id` (use as `X-School-Id`) plus live counts:
- `studentCount` — from enrolled students
- `classSectionCount` — from class-sections
- `teacherCount` — `0` until the teachers module exists

Profile fields (`address`, `city`, `state`, `principalName`, etc.) are **stored** on the school. Counts are **never stored** — they stay accurate as you add students and classes.

## API — Class Sections

Base path: `/api/v1/class-sections`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/class-sections` | List class-sections for dropdowns |
| POST | `/api/v1/class-sections` | Create a grade + section + academic year |
| GET | `/api/v1/class-sections/{classSectionId}/students` | List students in a class-section |

**List example:**

```bash
curl http://localhost:8080/api/v1/class-sections \
  -H "X-School-Id: 11111111-1111-1111-1111-111111111111"
```

**Create example:**

```bash
curl -X POST http://localhost:8080/api/v1/class-sections \
  -H "X-School-Id: 11111111-1111-1111-1111-111111111111" \
  -H "Content-Type: application/json" \
  -d '{
    "className": "Grade 9",
    "section": "A",
    "academicYear": "2026-27"
  }'
```

Seeded class-sections (from Flyway V1):

| className | section | academicYear | ID |
|-----------|---------|--------------|-----|
| Grade 8 | A | 2026-27 | `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa` |
| Grade 8 | B | 2026-27 | `bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb` |

## API — Students (enrollment)

Base path: `/api/v1/students`

All responses use the standard wrapper:

```json
{ "success": true, "data": { ... }, "message": null }
```

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/students` | List all students |
| GET | `/api/v1/students/by-class-section` | List students by class + section + year |
| GET | `/api/v1/students/{id}` | Get one student |
| POST | `/api/v1/students` | Enroll a student (one-time intake) |
| PATCH | `/api/v1/students/{id}/class-section` | Transfer student to another class-section |
| PUT | `/api/v1/students/{id}` | Update enrollment data |
| DELETE | `/api/v1/students/{id}` | Delete a student |

**List students by class and section:**

```bash
curl "http://localhost:8080/api/v1/students/by-class-section?className=Grade%208&section=A&academicYear=2026-27" \
  -H "X-School-Id: 11111111-1111-1111-1111-111111111111"
```

**Transfer student to another section:**

```bash
curl -X PATCH http://localhost:8080/api/v1/students/{studentId}/class-section \
  -H "X-School-Id: 11111111-1111-1111-1111-111111111111" \
  -H "Content-Type: application/json" \
  -d '{ "classSectionId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb" }'
```

**Enroll example:**

```bash
curl -X POST http://localhost:8080/api/v1/students \
  -H "X-School-Id: 11111111-1111-1111-1111-111111111111" \
  -H "Content-Type: application/json" \
  -d '{
    "rollNumber": "8A-001",
    "name": "Rahul Sharma",
    "dob": "2012-05-15",
    "gender": "MALE",
    "address": "123 MG Road, Jaipur",
    "parentName": "Rajesh Sharma",
    "parentContact": "9876543210",
    "classSectionId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "admissionDate": "2026-04-01"
  }'
```

**Required fields on create:** `rollNumber`, `name`, `dob`, `gender`, `address`, `parentName`, `parentContact`, `classSectionId`, `admissionDate`.

**Notes:**

- Auth is not enforced yet (`permitAll` for development). Tenant scoping uses the `X-School-Id` header.
- Roll numbers must be unique per school.
- `classSectionId` must belong to the same school as the header.

## API — Fund Management

All fund endpoints require `X-School-Id`. Every monetary movement posts to the central ledger (`/api/v1/finance/transactions`).

| Area | Base path | Highlights |
|------|-----------|------------|
| Finance | `/api/v1/finance` | Transactions list, fund summary, manual inflow/outflow |
| Vendors | `/api/v1/vendors` | Vendor master CRUD |
| Employees | `/api/v1/employees` | Employee master CRUD |
| Events | `/api/v1/events` | Shared events for collections and expenses |
| Fee categories | `/api/v1/fee-categories` | Tuition, transport, hostel, etc. |
| Fee structures | `/api/v1/fee-structures` | Per class-section; generate assessments |
| Fee payments | `/api/v1/fee-payments` | Partial payments, receipt numbers |
| Event collections | `/api/v1/events/{id}/collections` | Participation fees, collections, balance |
| Sponsorships | `/api/v1/sponsorships` | Pledge and payment tracking |
| Infra expenses | `/api/v1/infra-expense-requests` | Request → approve → purchase → pay |
| Event expenses | `/api/v1/events/{id}/expense-requests` | Budget, approvals, vendor payments |
| Payroll | `/api/v1/payroll/runs` | Process and pay monthly salary runs |
| Reports | `/api/v1/reports` | Fund summary, dues, event P&L, sponsorships |

Architecture documentation: [`docs/HLD_LLD.md`](docs/HLD_LLD.md)

## Scripts

| Command | Description |
|---------|-------------|
| `mvn spring-boot:run` | Start the backend (local profile, H2) |
| `SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run` | Start against Aurora locally (set env vars first) |
| `mvn clean install` | Build, test, package, and install locally |
| `mvn test` | Run tests (same as CI) |
| `mvn clean package` | Build the executable JAR (same artifact CI deploys) |
| `docker build -t gurukul-backend .` | Optional — verify Docker image (CI checks this on PRs) |

## Project structure

```text
.
├── pom.xml
├── Dockerfile
├── .github/workflows/
│   ├── ci.yml                   # PR + push: test, build JAR, Docker verify
│   └── deploy.yml               # merge main: test → S3 → SSM → EC2 → smoke test
├── deploy/aws/
│   ├── EC2.md                   # EC2 + JAR setup (production)
│   ├── PIPELINE.md              # CI/CD pipeline details
│   ├── deploy-from-s3.sh        # EC2 deploy script (S3 pull + restart)
│   ├── gurukul-backend.service  # systemd unit
│   ├── DEPLOYMENT.md            # App Runner / ECS (alternative)
│   └── .env.example
├── src/
│   ├── main/
│   │   ├── java/com/gurukul/
│   │   │   ├── GurukulApplication.java
│   │   │   ├── common/          # BaseEntity, ApiResponse, SchoolContext, exception handling
│   │   │   ├── config/          # Security, SchoolContextFilter, OpenAPI
│   │   │   ├── schools/         # School registration + tenant lookup
│   │   │   ├── students/        # Students + ClassSection
│   │   │   ├── finance/         # Ledger, fund accounts, transactions
│   │   │   ├── vendors/         # Vendor master
│   │   │   ├── employees/       # Employee master
│   │   │   ├── events/          # Shared school events
│   │   │   ├── fees/            # Student fee structures and payments
│   │   │   ├── collections/     # Event collection inflows
│   │   │   ├── sponsorships/    # Sponsor and sponsorship payments
│   │   │   ├── workflow/        # Approval engine
│   │   │   ├── expenses/        # Infrastructure and event outflows
│   │   │   ├── payroll/         # Salary structures and payroll runs
│   │   │   └── reports/         # Fund summary and aggregations
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-local.properties
│   │       ├── application-prod.properties   # Aurora + IAM JDBC wrapper
│   │       └── db/migration/    # Flyway SQL migrations
│   └── test/java/com/gurukul/
└── README.md
```

## Tech stack

- Spring Boot 4.1.0
- Java 25
- Maven
- Spring Web MVC
- Spring Security (dev mode: open endpoints)
- Spring Data JPA
- Flyway (+ `flyway-database-postgresql` for Aurora PG 17)
- H2 (local) / Aurora PostgreSQL (prod)
- AWS Advanced JDBC Wrapper (IAM auth for Aurora)
- springdoc-openapi (Swagger UI — local and prod)
- Bean Validation
- Lombok

## Roadmap

Built slice-by-slice:

1. ~~ClassSection (link students to classes)~~
2. ~~School registration + multi-tenant scoping~~
3. ~~Aurora PostgreSQL + IAM auth (prod profile)~~
4. ~~EC2 deployment guide~~ — [deploy/aws/EC2.md](deploy/aws/EC2.md)
5. ~~Deploy to EC2 + CI/CD~~ — merge PR to `main` (see [Team workflow](#team-workflow-cicd))
6. Elastic IP + HTTPS
7. JWT auth and role-based access
8. Teachers, attendance, fees, and other modules

## License

MIT
