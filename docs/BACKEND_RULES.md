# Backend Rules

Concrete, hard-won rules for this codebase — each one traces back to a real incident on this
project, not a hypothetical. When a rule and convenience conflict, the rule wins; add a new rule
here rather than re-learning the same lesson twice.

---

## 1. Every list endpoint must be paginated from day one

**Rule:** Any repository method that returns `List<T>` scoped only by `schoolId` (or similarly
unbounded) is a bug waiting to happen. New list endpoints must use `Page<T>`/`Pageable` from the
start, not "add it later once it's slow."

**Why:** `GET /api/v1/students` shipped as `findAllBySchoolId` with no `Pageable`. At 1052 rows it
was serializing and shipping the entire table on every screen load, blocking the client's JSON
parse on the UI thread. Same defect independently existed on `/employees`, `/fee-assessments`, and
`/calls/history*` — it wasn't a one-off, it was a missing house rule.

**How to apply:**
- Default to `Page<T>` + `Pageable` (`page`, `size` query params, default `size=50`) for any new
  "list everything for this school/entity" endpoint.
- If a filter is optional (e.g. `classSectionId`, `status`), push the filter into the DB query
  (one derived-query method per filter combination, or a `Specification`) — **never** filter
  in-memory after fetching a `Pageable` result. Filtering post-page silently returns incomplete or
  short pages that look correct in small tests and break at scale.
- Watch for existing internal callers that need the *full*, unpaginated list for aggregation (e.g.
  `ReportService` needs every fee assessment to compute a total). Don't paginate the method they
  call — add a separate paginated method for the public endpoint and leave the internal one alone.

---

## 2. A breaking response-shape change needs a released-client check first — always

**Rule:** Before changing any API response shape (not just adding fields — restructuring `data`
itself), find out whether every client that depends on the *old* shape has been released with code
that expects the *new* one. If any hasn't, don't ship the breaking version — either hold the
backend deploy or design the change to be backward-compatible.

**Why:** Pagination initially shipped with `data` becoming `{content, hasNext, totalElements}`
instead of a bare array. This was merged and deployed to production before checking whether the
Android app consuming it had actually been *released* (not just "code complete") — Play Store
rollout is never instant, and any user on the old APK build would have started getting a broken
response shape the moment the backend deployed. Caught before real damage, by asking directly
whether the FE build had reached users yet — it hadn't.

**How to apply:**
- Mobile clients (Android/iOS) are the highest-risk case: rollout is asynchronous and partial by
  design (staged rollout, auto-update disabled, users who just don't open the store). Web clients
  are lower-risk only if you're certain the deploy is atomic with the backend's.
- Ask explicitly: "has the client build that expects this new shape actually reached real users
  yet?" — not "is the code done," not "is FE ready." Only a real release answers the question.
- Prefer additive, backward-compatible shapes: keep the old field/shape working exactly as before,
  and add new fields as siblings rather than nesting/restructuring. For pagination specifically:
  keep `data` as the literal array of the current page (old clients silently see one page instead
  of everything — no crash), and put `hasNext`/`totalElements` as sibling fields on the response
  envelope, not nested inside `data`.

---

## 3. `@EntityGraph`/fetch joins must cover every association the DTO mapper actually touches — not just the first hop

**Rule:** When a `*Response.from(entity)` mapper walks more than one association deep (e.g.
`student.getClassSection().getClassTeacher()`), every one of those hops needs to be in the fetch
plan. An `@EntityGraph` that only covers the first hop still leaves the second hop lazy.

**Why:** `StudentRepository`'s `@EntityGraph(attributePaths = "classSection")` fetched the
class-section eagerly, but `StudentResponse.from()` also called
`.getClassSection().getClassTeacher()` — a lazy `@OneToOne` one hop further. Every distinct
class-section in a result page triggered its own separate lazy-load query. This produced a very
specific, easy-to-misdiagnose symptom: response time plateaued once a page's rows covered most of
a school's class-sections, and adding *more* rows past that point didn't cost more (because no new
distinct sections showed up), making it look like the endpoint had a flat, page-size-independent
cost — actually still N+1, just saturated.

**How to apply:**
- When adding a field to a response DTO that reads `a.getB().getC()`, check whether `getC()` is
  itself a lazy association load, and if so, add it as a nested entity-graph path
  (`"b", "b.c"`), not just `"b"`.
- If a list endpoint's latency doesn't scale linearly with row count in an obvious way (plateaus,
  jumps in steps, or is flat regardless of page size), suspect N+1 over a small, roughly-fixed
  number of distinct parent rows before suspecting the primary query itself.
- Same applies any time a DTO mapper is extended later to include a new nested field — re-check the
  entity graph, don't assume the existing one still covers it.

---

## 4. Don't add an index and assume it's helping — check with `EXPLAIN ANALYZE`

**Rule:** After adding an index intended to fix a slow query, verify with `EXPLAIN ANALYZE` that
Postgres actually chooses to use it. Don't infer "the index fixed it" just because the deploy
succeeded and nothing else changed.

**Why:** An index was added on `student(school_id, name)` expecting it to speed up the paginated
students query. `EXPLAIN ANALYZE` against production showed Postgres still doing a sequential
scan — correctly. The one production school owns ~100% of the `student` table's rows, so the
`WHERE school_id = ...` filter has almost no selectivity; an index lookup plus per-row heap fetch is
slower than just scanning the table straight through. The index wasn't broken — it will start
mattering once there are multiple schools with meaningfully different row counts — but assuming it
was the fix (without checking) would have wasted a full re-measurement cycle chasing the wrong
lead.

**How to apply:**
- Selectivity matters more than "is there an index." An index on a column that (at current data
  volume) matches nearly every row in the table is not going to be used by the planner, and
  correctly so.
- Run `EXPLAIN ANALYZE` on the actual query (with real `LIMIT`/`OFFSET`/`ORDER BY`, not a
  simplified version) against production or production-like data volumes before and after adding an
  index, not just "did the numbers get better."
- Remember `Page<T>` queries are *two* queries — the content query and a separate `COUNT(*)` with
  no `LIMIT`. Check both with `EXPLAIN ANALYZE`, since an index that helps one doesn't necessarily
  help the other.

---

## 5. A DB round-trip on every single request is a tax on every single request — cache what's safe to cache

**Rule:** Before adding a DB lookup to a filter/interceptor that runs on every request (auth,
tenant-context resolution, etc.), ask whether the fact being checked can actually change in a way
that matters, and if not, cache it.

**Why:** `SchoolContextFilter` ran `schoolService.requireExists(schoolId)` — a DB `SELECT` — on
every single `/api/v1/*` request, regardless of what the endpoint itself needed. Combined with a
cross-region app/DB link, that's a fixed latency tax on every call in the system. Schools are never
deleted or deactivated anywhere in this codebase, so "school exists" can only ever go
false → true, never the reverse — a positive result cached in-memory can never go stale in a way
that produces a wrong answer.

**How to apply:**
- Before caching anything for correctness reasons, confirm the invariant: can this fact actually
  become false after being true? If the entity/feature has no delete/deactivate path anywhere in
  the code, a cached "yes" is safe indefinitely (a bounded TTL is still worth adding, as a hedge
  against a future deactivate feature being added without updating the cache).
  before it's checked, and it just falls through to the DB and cache the result then).
- Don't reach for a caching library for this kind of thing — a `ConcurrentHashMap<Key, Instant>`
  guarding a TTL is enough and doesn't add a new dependency.
- This applies to any other per-request filter/interceptor doing a DB lookup for something that's
  effectively immutable once true (e.g. tenant existence, feature-flag-that-never-gets-unflagged,
  etc.) — audit the request filter chain for this pattern, not just `SchoolContextFilter`.

---

## 6. Test isolation: don't assume a shared test school is empty, or that the current test is the only writer

**Rule:** Integration tests that use a shared, hardcoded test-school ID (`11111111-...`) and don't
run inside a rolled-back transaction must not assume they're the only test that has ever touched
that school. Assert by matching your own created entity's ID, not by array position or "there's
only one row."

**Why:** `PayrollIntegrationTest` asserted `$.data[0].net == 33000` assuming its own newly-created
employee was the only payroll line in the run. A different test (`ReportOverviewIntegrationTest`,
added later) left its own active employee with an applicable salary structure behind in the same
shared school — no cleanup, no transaction rollback (`@SpringBootTest`, no `@Transactional`) — and
that employee's payroll line rode along into the first test's run too. The two tests' data
happened to sort in a way that flipped which line was "first," and CI (with different
class-loading/test-execution order than local) reproduced the wrong order consistently.

**How to apply:**
- When asserting on a list returned by an endpoint scoped to the shared test school, filter/find
  the specific row belonging to *this* test (by an ID or a unique field this test controls, e.g. a
  random suffix in a name), never assume index `[0]` or "there's exactly one."
- If a test creates persistent, active state in the shared school (an active employee, an active
  salary structure, etc.) that other tests' aggregate queries could pick up, say so in a comment —
  the next person adding a test to the same shared school needs that context.
- Don't assume "it passed on my machine" and "it passed in this PR's CI run" together mean the test
  is correct — shared, non-transactional test state is exactly the kind of thing that passes by
  accident depending on execution order, and JVM/classpath differences between local and CI can
  flip that order.

---

## 7. Never commit, paste, or work around a real secret — always stop and get it rotated

**Rule:** If a real credential (API key, AWS access key, DB password, private key) appears in a
chat message, log output, or file that will be shared/committed, stop what you're doing and flag it
for rotation before continuing. Don't just avoid re-printing it — the moment it's been transmitted
anywhere outside its intended store, treat it as compromised.

**Why:** Over the course of this project, a live Anthropic API key and a live AWS access key/secret
pair both ended up pasted into chat (via `cat .env`, `history`, etc.) while debugging deploys. Both
needed immediate rotation, independent of anything else being worked on.

**How to apply:**
- Never `cat` or paste the full contents of an env/secrets file into a chat/log — grep for the
  specific non-secret line you need (and even then, double check the grep didn't also match a
  secret field that doesn't happen to have "PASSWORD"/"SECRET" in its name, like an API key).
- If a secret does leak into a chat transcript, log, or commit, the fix is rotation, not deletion —
  assume anything already transmitted is permanently exposed.
- This applies equally to production DB credentials, third-party API keys, and cloud provider
  credentials — there's no "low-stakes" secret in a production system.

---

## 8. Know what's actually live in production before debugging against assumptions

**Rule:** Before diagnosing a "why doesn't my fix work" question against production, verify the
actual identifiers, environment, and deployed state directly — don't assume a demo/seed ID, a
local test's school ID, or a previous session's understanding still matches what's really out
there.

**Why:** Multiple points in this project's history involved confusion between the shared
integration-test school ID (`11111111-...`, seeded for local/CI tests) and the real production
school (`99999999-...`, "JNV"). A login attempt against production using the test school's demo
credentials failed with "School not found" — not because anything was broken, but because that
demo data was never seeded in production at all. Similarly, whether a given deploy mechanism was
Docker-based or a plain systemd-run JAR had to be checked directly on the instance, because the
repo's own docs (`PIPELINE.md` vs. the actual `deploy.yml`) disagreed with each other.

**How to apply:**
- When a repo has more than one description of "how this deploys" (a doc, a systemd unit file, an
  actual CI workflow), don't trust any of them until you've confirmed what's *actually running*
  against the real instance (`systemctl cat`, `docker ps`, the actual env file).
- Don't reuse a school ID, employee ID, or credential from local tests/seed migrations against
  production without confirming it exists there — `SELECT`/`GET` first, mutate second.
- When something behaves unexpectedly "in production," get the actual current state (via a
  read-only query, a health check, a log tail) before forming a theory — don't extend a theory
  built from local/test behavior onto production without checking it transfers.

---

## 9. Always run the full test suite twice before calling a fix stable

**Rule:** After any fix — especially one touching shared/non-transactional test state, timing, or
concurrency — run the full suite at least twice in a row before considering it verified. A single
green run doesn't rule out flakiness; a second one substantially does.

**Why:** Used consistently throughout this project's fixes (Jitsi warm-up blocking, payroll test
isolation, pagination, N+1 fix) as the actual bar for "safe to merge" — every fix in this project's
history was verified with 103/103 (or the current total) passing on two separate `mvn test` runs,
not one.

**How to apply:**
- One green run after a change is necessary, not sufficient.
- If a fix specifically targets a flaky/order-dependent test, re-running it in isolation is not
  enough — run the *whole* suite, since the flakiness usually comes from cross-test state, not the
  test in isolation.
