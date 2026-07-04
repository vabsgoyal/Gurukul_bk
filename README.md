# Gurukul Backend

School management backend for Gurukul, built with Spring Boot and Maven. The project is developed as vertical slices; the current slice implements **student enrollment** with class-sections and multi-tenant school scoping.

## Prerequisites

- Java 25+
- Maven, or the included Maven wrapper

No external database is required for local development — the app uses **H2 in-memory** by default.

## Setup

```bash
git clone https://github.com/vabsgoyal/Gurukul_bk.git
cd Gurukul_bk
mvn clean install
```

## Run locally

```bash
mvn spring-boot:run
```

Or with the wrapper:

```bash
./mvnw spring-boot:run
```

The application starts at:

```text
http://localhost:8080
```

If port 8080 is already in use, stop the other process first (for example `Ctrl+C` in the other terminal).

### Swagger UI (API docs)

With the app running, open:

**http://localhost:8080/swagger-ui.html**

OpenAPI JSON spec: **http://localhost:8080/v3/api-docs**

Every `/api/v1/**` endpoint requires the **`X-School-Id`** header. Swagger UI includes it on all operations — set it to the example school UUID below before trying requests.

Packaged jar:

```bash
mvn clean package
java -jar target/gurukul-backend-0.0.1-SNAPSHOT.jar
```

## Database (local)

The **`local`** profile is active by default and uses **H2 in-memory**:

| Setting | Value |
|---------|--------|
| JDBC URL | `jdbc:h2:mem:gurukul` |
| Username | `sa` |
| Password | *(empty)* |

Schema is managed by **Flyway** (`src/main/resources/db/migration/`). Data is reset when the application restarts.

### H2 Console

With the app running, open:

**http://localhost:8080/h2-console**

> Spring Boot 4 requires the `spring-boot-h2console` dependency (already in `pom.xml`) for the console UI to load.

Use the JDBC URL above with user `sa` and no password.

- H2 documentation: https://www.h2database.com/html/main.html

### PostgreSQL (future)

The PostgreSQL driver is on the classpath for production use later. To switch, add a `prod` profile with PostgreSQL connection settings and set `spring.profiles.active=prod`.

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

## Scripts

| Command | Description |
|---------|-------------|
| `mvn spring-boot:run` | Start the backend server |
| `mvn clean install` | Build, test, package, and install locally |
| `mvn clean install -DskipTests` | Build without running tests |
| `mvn test` | Run tests |
| `mvn clean package` | Build the executable jar |

## Project structure

```text
.
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/gurukul/
│   │   │   ├── GurukulApplication.java
│   │   │   ├── common/          # BaseEntity, ApiResponse, SchoolContext, exception handling
│   │   │   ├── config/          # Security, SchoolContextFilter, OpenAPI
│   │   │   └── students/        # Students + ClassSection (entity, repo, service, controller)
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-local.properties
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
- Flyway
- H2 (local) / PostgreSQL driver (production-ready)
- springdoc-openapi (Swagger UI)
- Bean Validation
- Lombok

## Roadmap

Built slice-by-slice inside the `students` module first, then expanding:

1. ~~ClassSection (link students to classes)~~
2. Admissions + enroll workflow
3. JWT auth and role-based access
4. Teachers, attendance, fees, and other modules

## License

MIT
