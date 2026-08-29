AGENTS GUIDE — school-fee-managment

Purpose
- Quick reference for agents (and contributors) about where core features live and how to run the project.

Project layout (important files/dirs)
- Backend (Java / Spring Boot)
  - src/main/java/com/school/
    - Domain.java — JPA entities for School, AppUser, SchoolClass, Student, FeeType, FeeReceipt, etc.
    - ApiController.java — REST endpoints (/api/*) for classes, students, fees, types, reports.
    - Repositories: <repository interfaces> (look for *Repository.java) — data access.
    - Services: FeeService.java — business logic for receipts. 
    - Application entry: SchoolFeeApplication.java
- Database migrations
  - src/main/resources/db/migration/V1__initial_schema.sql — initial schema and seed inserts (classes now seeded).
- Frontend (static)
  - src/main/resources/static/index.html — single-page UI (dialogs, forms).
  - src/main/resources/static/app.js — client logic: state, API calls, renderers, event handlers.
  - src/main/resources/static/app.css — styling and utility classes (includes .hidden class).
- Build
  - pom.xml — Maven project file (Spring Boot parent, dependencies). Use Maven 3.9+ or the IDE embedded Maven.
  - target/ — build artifacts after mvn package.
- Docker / Compose
  - docker-compose.yml — development DB and app compose (migrations baked into image under db/migration Dockerfile).

How the frontend talks to backend
- API base is /api (ApiController mappings).
- Client sends Basic auth headers (state.user / state.password stored locally) — use the Connection dialog to set credentials.

Common developer tasks
- Build: mvn -DskipTests package
- Run (jar): java -jar target\school-fee-management-0.0.1-SNAPSHOT.jar
- Recreate DB (to apply migrations): docker compose down -v && docker compose up --build
- Run in IDE: use Run → Run 'SchoolFeeApplication' (ensure Maven/Java configured)
- Quick integration test script: scripts/create-class-integration-test.sh — run with SCHOOL_ID, USER, PASS environment variables to exercise POST /api/classes

Notes / caveats
- Enum mapping: DB uses VARCHAR columns and Java uses @Enumerated(EnumType.STRING) for Role and ReceiptStatus.
- Migrations are executed only when DB is initialized; removing volumes is required to reapply migration SQL.
- Frontend dialog behavior: dialogs are native <dialog> elements; form buttons must be type="button" to avoid accidental submission.

Where to look for common changes
- Add a REST endpoint: ApiController.java
- Change DB schema: edit migration SQL (note: only affects new DBs) or write a Flyway migration
- Modify UI markup: static/index.html and static/app.js (renderAll(), event handlers)

Contacts
- Repo: divyasanwaria2602-ai/school-fee-managment

This file is intended to be kept in the repo root and updated by maintainers as structure changes.