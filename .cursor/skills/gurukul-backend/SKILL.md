---
name: gurukul-backend
description: >-
  Gurukul Spring Boot backend conventions — vertical slices, multi-tenant APIs,
  Flyway, JPA, MockMvc tests. Use when adding or changing code in this repository,
  reviewing PRs, writing migrations, or answering architecture questions for
  Gurukul_bk.
---

# Gurukul Backend — Project Conventions

Apply these rules for **all** work in this repo unless the user explicitly overrides them.

## Stack

- Java 25, Spring Boot 4.x, Maven
- Spring Web MVC, Spring Data JPA, Bean Validation
- Flyway migrations (`src/main/resources/db/migration/`)
- H2 in-memory (`local` profile), Aurora PostgreSQL (`prod` profile)
- springdoc-openapi (Swagger UI on local and prod)
- Lombok (`@Getter`, `@Setter`, `@RequiredArgsConstructor`)

## Architecture

- **Vertical slices** by domain under `com.gurukul.<domain>`
- Each slice owns: `entity`, `repository`, `service`, `controller`, `dto`
- Shared cross-cutting code lives in `com.gurukul.common` and `com.gurukul.config`
- Prefer extending existing abstractions over new frameworks or layers
- **Minimize scope** — smallest correct diff; do not refactor unrelated code

## Multi-tenancy

- Almost every `/api/v1/**` request requires header `X-School-Id: {school-uuid}`
- Exceptions (no header): `POST /api/v1/schools`, `GET /api/v1/schools/{id}`
- Tenant scoping is enforced by:
  - `SchoolContextFilter` → `SchoolContext` (thread-local)
  - `schoolId` on tenant entities via `BaseEntity`
- **Every read/write** in a tenant-scoped service must filter by `schoolContext.getSchoolId()`
- Cross-tenant access by ID must return **404** (`EntityNotFoundException`), not 403
- Local dev seed school: `11111111-1111-1111-1111-111111111111`

## Entities

- Tenant-scoped entities extend `BaseEntity` (`id`, `schoolId`, `createdAt`, `updatedAt`)
- School registration uses its own entity (not `BaseEntity`)
- Use `UUID` primary keys, `Instant` for audit timestamps, `LocalDate` for calendar dates
- Use `BigDecimal` (scale 2) for money — never `double` or `float`
- Enum fields: `@Enumerated(EnumType.STRING)`
- Avoid SQL reserved words as column/table names (e.g. `month`, `year`, `event`); use prefixed names in Flyway and `@Column(name = "...")` when needed
- Lazy associations: `@ManyToOne(fetch = FetchType.LAZY)`; use `@Transactional(readOnly = true)` on read methods that map lazy relations to DTOs

## Database migrations (Flyway)

- One migration per logical change: `V{n}__snake_case_description.sql`
- **Never modify** a migration that has already run in prod
- Write SQL compatible with **both H2 and PostgreSQL** when possible
- Add FK constraints to `school(id)` for tenant tables
- Seed data for local dev may live in the same migration file
- Review migrations carefully — prod applies them automatically on deploy

## API design

- Base path: `/api/v1`
- Response envelope: `ApiResponse<T>` → `{ "success", "data", "message" }`
- Success: `ApiResponse.success(data)` or `ApiResponse.success(data, "message")`
- Errors handled by `GlobalExceptionHandler`:
  - `400` — validation, `IllegalArgumentException`, missing/invalid `X-School-Id`
  - `404` — `EntityNotFoundException`
- Request DTOs: `*Request` with Jakarta validation (`@NotNull`, `@NotBlank`, etc.)
- Response DTOs: `*Response` with static factory `from(Entity e)`
- Controllers: `@RestController`, `@RequiredArgsConstructor`, inject service only
- Document endpoints with `@Tag`, `@Operation`, `@ApiResponses` (match existing controllers)
- Use **full explicit paths** on `@GetMapping` / `@PostMapping` when class-level `@RequestMapping` would produce ambiguous URLs

## Service layer

- `@Service`, `@RequiredArgsConstructor`, constructor injection
- Private `findScoped(UUID id)` loads by `id` + `schoolId`; throws `EntityNotFoundException` if missing
- `@Transactional` on write operations; `@Transactional(readOnly = true)` on reads touching lazy graphs
- Business rule violations → `IllegalArgumentException` (maps to 400)
- Missing records → `EntityNotFoundException` (maps to 404)
- Do not bypass tenant scoping in repositories

## Repositories

- Spring Data JPA interfaces
- Naming: `findByIdAndSchoolId`, `findAllBySchoolId`, etc.
- Keep query methods school-scoped where the entity is tenant-owned

## Testing

- Integration tests: `@SpringBootTest` + `@AutoConfigureMockMvc`
- Use `MockMvc` with `X-School-Id` header and JSON via `MediaType.APPLICATION_JSON`
- Assert envelope: `jsonPath("$.success")`, `jsonPath("$.data....")`
- One test class per domain slice for meaningful flows
- Run `mvn test` before considering work done
- Avoid hard-coded ledger/balance totals when tests share one DB — assert deltas or relative changes

## Code style

- Match surrounding indentation (tabs in existing Java files)
- Controllers stay thin; business logic in services
- Comments only for non-obvious business rules
- No over-abstraction (no one-line helpers, no premature generic frameworks)
- Reuse existing enums, DTO patterns, and package naming

## Git and deploy

- **Do not commit** unless the user explicitly asks
- **Do not push** unless explicitly asked
- Merging to `main` deploys to production (Aurora + EC2)
- Do not skip hooks or force-push unless explicitly requested

## Definition of done (new slice or endpoint)

- [ ] Flyway migration (if schema changed)
- [ ] Entity, repository, service, controller, DTOs
- [ ] OpenAPI annotations
- [ ] Integration test(s) with `X-School-Id`
- [ ] `mvn test` passes
- [ ] README API section updated when adding user-facing endpoints

## Anti-patterns

- Writing to tenant tables without `schoolId`
- Returning entities directly from controllers
- Editing applied Flyway migrations
- Using `findAll()` without school filter on tenant data
- Large cross-cutting refactors bundled with a small feature fix
- Adding README or docs files unless requested
