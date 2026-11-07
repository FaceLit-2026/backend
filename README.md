# FaceLit backend

The application retains three layers: controllers/DTOs, application services/rules,
and repositories/external clients. The Java application remains one modular monolith.
CSV imports keep their transactional facade and original processing order; focused
components under `academic/service/csv` handle syntax, users, each import type and
notifications. Shared CSV lexical rules have a single definition.

Attendance reporting SQL lives in JDBC repositories, authorization in
`FacialAccessPolicy`, and notification composition in dedicated services. Active
registration status/confirmation routes delegate to `FacialRegistrationService` and
its repository. Existing HTTP contracts, shared PostgreSQL ownership and the facial
client URL/API-key protocol are preserved. Retired enrollment endpoints remain denied
by the existing security configuration.

H-50 mappings use `role_name`, `description` and `assigned_at`. Apply at least the
FaceLit-DB English-nomenclature expansion changeset before starting this version;
the old schema alone fails Hibernate validation. The notification catalog writes
English categories. Complete contraction only after all legacy writers are stopped
or upgraded; no public DTO field names or role values change.

The opt-in `ArchitectureFlowPostgresIT` exercises real HTTP, JWT, FastAPI and PostgreSQL
with a deterministic inference fixture and a mocked mail sender. Run it through the
sibling DB repository's `scripts/test-architecture-e2e.ps1`; it creates and removes a
separate Docker project. It does not use the normal database. Browser verification
is optional and requires Playwright tooling plus an available browser.

The runner must report an executed test without
failures or skips; a local pass does not establish CI or camera acceptance.

Spring Boot REST backend for FaceLit's account access, academic management, environment sessions, facial attendance and notifications. This repository uses Java 17 and Spring Boot 3.4.1, as declared in [pom.xml](pom.xml). Its Java package is `com.FaceLit.backend`.

## Architecture

```text
HTTP / JWT filter -> controllers -> services -> repositories / PostgreSQL
                                      |
                         SMTP, realtime and facial integration

src/main/java/com/FaceLit/backend/
  academic/       Programs, training groups, enrollment and CSV processing
  auth/           Accounts, credentials, roles, recovery and configuration
  config/         Security and application configuration
  environment/    Environments and attendance sessions
  facial/         Enrollment, reset authorization and attendance
  notification/   In-app messages and email dispatch
  realtime/       Authenticated realtime communication
  shared/         Shared exceptions, utilities and infrastructure
src/main/resources/application.yaml
src/main/resources/static/email/   Runtime email assets
src/test/                         Automated tests and isolated configuration
README.md                         Single maintained documentation entry
```

Controllers accept requests and delegate application behavior; services apply rules and coordinate persistence/delivery. The current facial implementation also contains JDBC integration. This README describes that implementation rather than claiming an architectural refactor has already been delivered. Technical comments remain beside the code; separate implementation reports are not application inputs.

## Prerequisites and configuration

Install Java 17 and use the committed `mvnw`/`mvnw.cmd`, or compatible Maven. Prepare PostgreSQL with [FaceLit-DB](https://github.com/FaceID-Proyect-2026/FaceLit-DB). Facial operations and email acceptance require their configured services.

[application.yaml](src/main/resources/application.yaml) is the configuration source. Supply private environment variables or an ignored local `.env`, read by spring-dotenv. Do not overwrite a working local configuration during a checkout update.

For a new installation, configure the database, mail, JWT and internal facial-service environment variables with private values. The initial coordinator is no longer a database seed. Follow the one-time setup below after Liquibase has prepared the schema and roles.

| Variable | Purpose |
| --- | --- |
| `DB_URL` | PostgreSQL JDBC connection URL |
| `DB_USERNAME`, `DB_PASSWORD` | Database account |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP account; YAML configures Gmail SMTP on port 587 |
| `JWT_SECRET` | Signing key compatible with the JWT implementation |
| `FACIAL_EMBEDDING_URL` | Facial API address; development default is localhost:8090 |
| `FACIAL_EMBEDDING_API_KEY` | Server-side service credential; configure it privately |
| `FACELIT_ACCESS_URL` | Frontend address used in notifications; development default is localhost:8081 |

HTTP uses port 8080. Hibernate is configured with `ddl-auto: validate`, and backend Liquibase execution is disabled. Run the database project's migration procedure before normal startup. Do not use a Hibernate setting change to conceal missing migrations or schema differences.

The currently checked-out YAML/client still has the legacy facial credential fallback; the coordinated security change is delivered separately. Removing generated files does not certify credential rotation or complete all security findings.

## First coordinator and credential rotation

Account creation belongs to `auth` application services. Liquibase creates the schema and role catalog, without a default user/password. Coordinator setup is disabled by default. Its configuration lives under `facelit.bootstrap.coordinator`; no public registration endpoint is added.

From this backend directory, run one of these commands with the intended document and email:

```powershell
# New installation: only when no coordinator exists.
.\scripts\initialize-coordinator.ps1 -Mode Create -Document "<document>" -Email "<email>" -FirstName "<name>" -LastName "<surname>"

# Existing coordinator: keep the same identity/email and choose a different private password.
.\scripts\initialize-coordinator.ps1 -Mode Rotate -Document "<existing-document>" -Email "<existing-email>"
```

Replace the placeholders. The script requests password and confirmation using hidden terminal input, not command-line arguments. The existing password policy applies: 8–15 characters, uppercase, number and a permitted symbol. Configure `DB_*`, `JWT_SECRET`, SMTP and facial service values privately first. The helper uses a loopback random port, performs the operation and closes its application context; it restores process environment values afterwards. It does not write the password to `.env` or the repository.

Creation never promotes an existing ordinary account or creates another initial coordinator. A mismatched existing credential requires explicit rotation. Rotation keeps IDs, names, email, roles and historical relations, invalidates sessions/recovery authorizations and cancels pending access mail. Repeating the same completed operation is idempotent. Keep `FACELIT_COORDINATOR_BOOTSTRAP` and `FACELIT_COORDINATOR_ROTATE_PASSWORD` disabled during normal service operation.

If an active coordinator still holds exactly the retired seed's published bcrypt value, startup refuses to proceed until rotation. Detection uses a SHA-256 fingerprint, not a usable password or bcrypt credential. This check does not prove every historically exposed password has been rotated. An operator must perform and record rotation separately in each real environment; replacing Git files alone does not do it.

## Run and build

From PowerShell in this repository:

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd "-Dspring.profiles.active=test" test
.\mvnw.cmd "-Dspring.profiles.active=test" package
```

On Unix-like systems, use `./mvnw` with the same arguments; use `bash mvnw` if the checkout's executable bit is unavailable. Configure isolated mail/JWT values when running tests. [application-test.yaml](src/test/resources/application-test.yaml) uses H2 with disposable schemas. It does not certify PostgreSQL-specific behavior or a production deployment. The explicitly enabled PostgreSQL integration test needs its own local verification environment.

## Continuous integration

The [CI workflow](.github/workflows/ci.yml) runs on pull requests and pushes to dev, qa and main, with optional manual dispatch. The `Maven tests and JaCoCo coverage` job uses Java 17 and the Maven Wrapper to run JaCoCo 0.8.12 prepare-agent, `verify` and report generation without skipping tests. Its test profile uses H2 with dummy JWT/mail values and local SMTP; it does not certify PostgreSQL or real email delivery.

Download `backend-tests-and-coverage` from the exact commit's Actions run for Surefire/Failsafe reports and JaCoCo HTML/XML. The HTML entry is `target/site/jacoco/index.html`. Coverage measures executed lines/branches; no minimum threshold is configured by this workflow. The opt-in PostgreSQL integration test needs a separately enabled environment. A local or historical successful run does not establish the current head's status.

HU-MEJ-006 automates tests and evidence for H-16/H-18. Authorization/ownership and account-recovery fixes remain responsibilities of HU-MEJ-001/HU-MEJ-002; CI configuration alone does not close H-17. Verify required-check rules, failed-check blocking evidence and independent approval separately; a workflow file does not configure repository protection.

## API and access model

The actual controllers and [SecurityConfig](src/main/java/com/FaceLit/backend/config/SecurityConfig.java) define the contract. The JWT filter reads a Bearer token and establishes the user principal/authorities before controller execution. Public authentication routes and authenticated role checks are distinct; application/service ownership checks must also be tested for the flow in question.

| Area | Current route families and examples |
| --- | --- |
| Authentication | `POST /api/auth/login`, `/verify-password`, `/privacy-acceptance-status` |
| Account access/recovery | `/api/auth/complete-access`, `/request-recovery`, `/verify-token`, `/reset-password`, `/change-password` |
| User administration | `/api/admin/users`, `/api/admin/users/{userId}/role` |
| Personal profile | `/api/profile/me`, `/api/profile/configuration` |
| Academic management | `/api/academic/programs`, `/chips`, `/instructors`, `/csv` and `/me` |
| Environment sessions | `/api/environment/environments`, `/sessions`, `/sessions/active` and session exit-password verification |
| Facial enrollment | `/api/facial/me`, `/registration-status`, `/profile-photo`, `/challenge`, `/enrollment`, `/registration-confirmation` |
| Facial reset | `POST /api/facial/reset-requests`, `GET /api/facial/reset-requests/me` |
| Notifications | `/api/notifications`, read/read-all and recipient-bound facial-request resolution |
| Realtime | `/ws/realtime`, with token validation through its existing protocol |

Use request DTOs and controllers for exact method/body requirements. Program/training-group modification and administration are coordinator operations; environment-session routes are instructor operations in the current security configuration. The `/api/academic/me/**` routes provide role-aware self-service reads. This inventory does not introduce missing CRUD operations or grant new roles.

## Academic and environment behavior

Academic management coordinates programs, training groups (`chip` in the database), instructors, apprentices, assignments and change history. CSV endpoints provide the template, upload and pending-transfer decisions. Duplicate checks, active status and transfer handling are defined by their services and DTOs.

Environment sessions hold entry/exit and shutdown times for attendance. They remain part of the application after schedule management was removed. Follow their existing validation and instructor assignment rules; do not infer support for a screen operation that lacks an API contract.

## Facial enrollment and reset authorization

Enrollment status/confirmation and the facial processing service use the schema established by FaceLit-DB. A reset request is associated with the authenticated apprentice; academic membership supplies display context. Coordinators resolve recipient-bound notifications in the app, not by following an unauthenticated email action.

Approval creates a one-use authorization, normally valid for 24 hours (`facial.reset.authorization-hours`). The token is returned to its authenticated owner through the self-service request endpoint, not in email or coordinator notifications. Replacing the existing face requires the approved, unexpired authorization and live capture. The microservice consumes the permission and saves the replacement in one transaction; a failure must leave the previous state intact. Test the coordinated backend/micro/frontend versions before release.

## Notifications, email and troubleshooting

Notification records and email delivery state are separate concerns. Event/recipient information drives in-app rendering and realtime updates; email dispatch records attempts and outcomes through the existing email log. `FACELIT_ACCESS_URL` must resolve from the recipient's device. A localhost link in a received message targets that device, not the server.

Account-access/recovery links and codes follow their existing services; do not distribute fixed sample passwords or verification codes. A database commit does not by itself prove SMTP delivery. Diagnose mail with the dispatch/error logs and configured account, while preserving private values.

Exit reminders use the session's `exit_time`: periodic checks select the relevant attendees and selected instructor during the configured window before exit. They use the EMAIL channel, are excluded from the in-app inbox and are revalidated before delivery/retry. The existing tests cover cancellation, deduplication, late arrivals and isolation of email-only messages. Actual timing and reception still depend on the running scheduler and SMTP provider.

For startup failures, verify environment variables, database availability and schema validation first. For denied requests, inspect the authenticated role and service ownership conditions; broadening permissions is not a cleanup fix. For facial failures, verify private service connectivity, credential compatibility, capture/authorization and the relevant persisted state. Keep raw biometrics, tokens and credentials out of shared diagnostic output.

## Container and deployment

The [Dockerfile](Dockerfile) builds a new JAR from `pom.xml` and `src/`, using `mvn -q -DskipTests clean package`, then copies `target/*.jar` into a Java 17 runtime image. It does not consume the deleted root JAR, repository-local Maven cache, diagnostic logs or developer-specific settings. Run the appropriate tests independently before accepting this branch; this Dockerfile does not prove a passing suite on its own.

```bash
docker build -t facelit-backend:review .
```

Supply runtime environment through the host configuration. The team identified `srv2068863.hstgr.cloud` as its Hostinger target, but source readiness is not proof that the final commit is deployed.

The runtime uses UID/GID `10001:10001`. Docker probes the anonymous `GET /health`
endpoint every 30 seconds after a 120-second startup grace period. It reports
process availability only; it does not run facial inference, contact SMTP or test
the microservice/database. Startup still validates the database schema. A probe
adds no wait to registration or attendance. The JAR is read-only to this user;
Java can write temporary files under `/tmp`. Do not mount secrets into the build
context: `.dockerignore` excludes environment files and local build caches.

## Repository hygiene and release acceptance

`.gitignore` excludes `.m2/`, local Maven settings, generated JAR/log files and `.tmp/`, including nested generated files. Maven Wrapper scripts/properties remain tracked; an exception permits a wrapper JAR if a future wrapper distribution needs it. Removing current artifacts does not rewrite Git history.

This README is the single maintained explanatory document. Historical notes, reports and screenshots were archived outside the source checkout before removal and remain recoverable through Git history. Runtime email images, source comments, tests and executable configuration remain present.

Before acceptance, record the final commits and applicable CI checks; verify startup, login per role, academic/environment behavior, enrollment/reset, attendance, notifications and SMTP on a coordinated deployment. Unit/H2 tests do not replace PostgreSQL integration or real camera acceptance. Obtain independent review before merging.

## OpenAPI contract

Springdoc generates the current API contract at `GET /v3/api-docs` (or
`/v3/api-docs.yaml`). A valid coordinator Bearer token is required; anonymous and
other roles cannot read this catalog. The document declares contract version
`1.0.0`, JWT authentication and the request/response schemas inferred from the
existing controllers and DTO validation. Public authentication operations are
identified separately. Denied legacy enrollment endpoints are excluded.

The metadata version is not a new URL prefix: current `/api/...` routes remain
unchanged for frontend/native compatibility. Introducing `/api/v1` routes and
migrating consumers is a separate coordinated contract change, not completed here.
Swagger UI is not installed; use a JSON/YAML viewer or API client with the exported
contract. Catalog generation runs on documentation requests and does not invoke
the facial service or add work to capture requests.

Related repositories: [frontend](https://github.com/FaceID-Proyect-2026/FaceLit), [database](https://github.com/FaceID-Proyect-2026/FaceLit-DB), [facial service](https://github.com/FaceID-Proyect-2026/micro-facial).
