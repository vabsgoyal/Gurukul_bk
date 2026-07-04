# How to Run — Local vs Prod

Quick reference for starting Gurukul Backend with **local (H2)** or **prod (Aurora IAM)** config.

---

## Local (H2 — default)

No env vars needed. Uses in-memory H2, Swagger, H2 console.

```bash
cd /Users/yashmeena/Gurukul_bk
mvn spring-boot:run
```

Or explicitly:

```bash
export SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run
```

Or with the jar:

```bash
mvn clean package -DskipTests
java -jar target/gurukul-backend-0.0.1-SNAPSHOT.jar
```

**URLs**

| Service | URL |
|---------|-----|
| App | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |
| H2 Console | http://localhost:8080/h2-console |

**H2 credentials:** JDBC URL `jdbc:h2:mem:gurukul`, user `sa`, no password.

**Demo school ID:** `11111111-1111-1111-1111-111111111111`

---

## Prod (Aurora — IAM)

Requires AWS credentials with `rds-db:connect`. No DB password.

```bash
cd /Users/yashmeena/Gurukul_bk

export SPRING_PROFILES_ACTIVE=prod
export AWS_REGION=eu-north-1
export SPRING_DATASOURCE_URL="jdbc:aws-wrapper:postgresql://gurukul.cluster-cro4soma8nh2.eu-north-1.rds.amazonaws.com:5432/postgres?sslmode=require"
export SPRING_DATASOURCE_USERNAME=postgres

mvn spring-boot:run
```

One-liner:

```bash
SPRING_PROFILES_ACTIVE=prod AWS_REGION=eu-north-1 \
SPRING_DATASOURCE_URL="jdbc:aws-wrapper:postgresql://gurukul.cluster-cro4soma8nh2.eu-north-1.rds.amazonaws.com:5432/postgres?sslmode=require" \
SPRING_DATASOURCE_USERNAME=postgres \
mvn spring-boot:run
```

With jar:

```bash
export SPRING_PROFILES_ACTIVE=prod
export AWS_REGION=eu-north-1
export SPRING_DATASOURCE_URL="jdbc:aws-wrapper:postgresql://gurukul.cluster-cro4soma8nh2.eu-north-1.rds.amazonaws.com:5432/postgres?sslmode=require"
export SPRING_DATASOURCE_USERNAME=postgres

java -jar target/gurukul-backend-0.0.1-SNAPSHOT.jar
```

**Health check:**

```bash
curl http://localhost:8080/actuator/health
```

Swagger is **disabled** on the `prod` profile.

---

## Query prod DB from console

Prod Aurora requires **IAM auth** (no static password). Use an IAM token as the password.

### Option 1 — AWS CloudShell

1. AWS Console → **CloudShell** (region **eu-north-1**)
2. Run:

```bash
export RDSHOST="gurukul.cluster-cro4soma8nh2.eu-north-1.rds.amazonaws.com"

export PGPASSWORD="$(aws rds generate-db-auth-token \
  --hostname "$RDSHOST" --port 5432 --username postgres --region eu-north-1)"

psql "host=$RDSHOST port=5432 dbname=postgres user=postgres sslmode=require"
```

### Option 2 — Your Mac (local terminal)

Install the PostgreSQL client if needed:

```bash
brew install libpq
echo 'export PATH="/opt/homebrew/opt/libpq/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

Ensure AWS CLI is configured with a user that has **`rds-db:connect`** (e.g. `gurukul-deploy`):

```bash
aws sts get-caller-identity
```

Connect:

```bash
export RDSHOST="gurukul.cluster-cro4soma8nh2.eu-north-1.rds.amazonaws.com"
export AWS_REGION=eu-north-1

export PGPASSWORD="$(aws rds generate-db-auth-token \
  --hostname "$RDSHOST" --port 5432 --username postgres --region $AWS_REGION)"

psql "host=$RDSHOST port=5432 dbname=postgres user=postgres sslmode=require"
```

### Useful `psql` commands

**Meta / navigation**

| Command | Description |
|---------|-------------|
| `\conninfo` | Show current connection (host, user, db) |
| `\l` | List all databases |
| `\dt` | List tables in current schema |
| `\d school` | Describe columns, keys on `school` |
| `\d+ student` | Same with extra detail (size, description) |
| `\x` | Toggle expanded output (wide rows) |
| `\timing` | Show query execution time |
| `\q` | Quit |

**Flyway / schema**

```sql
-- Applied migrations
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;

-- All tables in public schema
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' ORDER BY table_name;
```

**Schools**

```sql
-- Demo school
SELECT id, name, city, state, contact_email FROM school;

SELECT * FROM school
WHERE id = '11111111-1111-1111-1111-111111111111';
```

**Class sections**

```sql
SELECT cs.id, cs.class_name, cs.section, cs.academic_year, s.name AS school_name
FROM class_section cs
JOIN school s ON s.id = cs.school_id
ORDER BY cs.class_name, cs.section;

-- Seeded sections for demo school
SELECT id, class_name, section, academic_year
FROM class_section
WHERE school_id = '11111111-1111-1111-1111-111111111111';
```

**Students**

```sql
-- All students with class info
SELECT st.roll_number, st.name, st.gender, st.status,
       cs.class_name, cs.section, cs.academic_year
FROM student st
JOIN class_section cs ON cs.id = st.class_section_id
ORDER BY st.roll_number;

-- Count per school
SELECT s.name, COUNT(st.id) AS student_count
FROM school s
LEFT JOIN student st ON st.school_id = s.id
GROUP BY s.id, s.name;

-- Students in Grade 8-A
SELECT st.roll_number, st.name, st.admission_date
FROM student st
JOIN class_section cs ON cs.id = st.class_section_id
WHERE cs.class_name = 'Grade 8' AND cs.section = 'A';
```

**Quick counts**

```sql
SELECT
  (SELECT COUNT(*) FROM school)        AS schools,
  (SELECT COUNT(*) FROM class_section) AS class_sections,
  (SELECT COUNT(*) FROM student)       AS students;
```

**One-off from shell** (no interactive `psql`):

```bash
psql "host=$RDSHOST port=5432 dbname=postgres user=postgres sslmode=require" \
  -c "SELECT COUNT(*) FROM student;"
```

**Token expires in 15 minutes.** Regenerate if auth fails:

```bash
export PGPASSWORD="$(aws rds generate-db-auth-token \
  --hostname "$RDSHOST" --port 5432 --username postgres --region eu-north-1)"
```

| Setting | Value |
|---------|--------|
| Host | `gurukul.cluster-cro4soma8nh2.eu-north-1.rds.amazonaws.com` |
| Port | `5432` |
| Database | `postgres` |
| User | `postgres` |
| Password | IAM token from `aws rds generate-db-auth-token` |
| SSL | `sslmode=require` |

---

## Switch back to local

Stop the running app (`Ctrl+C`), then:

```bash
unset SPRING_PROFILES_ACTIVE SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME AWS_REGION
mvn spring-boot:run
```

Or open a **new terminal** — `local` is the default when `SPRING_PROFILES_ACTIVE` is unset.

---

## Port already in use?

```bash
lsof -i :8080
kill <PID>
```

Or use another port:

```bash
export PORT=8081
mvn spring-boot:run
```

---

## Comparison

| | **Local** | **Prod** |
|---|-----------|----------|
| Profile | `local` (default) | `prod` |
| Database | H2 in-memory | Aurora PostgreSQL |
| Auth | none | IAM (AWS creds) |
| Swagger | yes | no |
| Startup | ~2s | ~12s |

---

See also: [README.md](README.md) · [deploy/aws/DEPLOYMENT.md](deploy/aws/DEPLOYMENT.md)
