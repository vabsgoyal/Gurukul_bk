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
