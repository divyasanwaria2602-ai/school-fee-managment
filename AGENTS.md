# AGENTS GUIDE — school-fee-managment

## Purpose
Quick reference for agents and contributors about where core features live and how to run the project.

## Project layout
- `ApiController.java` — REST endpoints for schools, users, classes, students, fee types, fee structures, fees, and reports.
- `UserManagementService.java` — root school provisioning and school-user management.
- `FeeStructureService.java` — class-wise fee amounts by academic year.
- `FeeService.java` — receipt creation, authoritative fee calculation, cancellation, and audit records.
- `Domain.java` — JPA entities including `ClassFeeStructure`.
- `Repositories.java` — data access.
- `SecurityConfig.java` — Basic authentication and role authorities.
- `BootstrapConfiguration.java` — creates the ROOT account from environment variables.
- `static/index.html`, `static/app.js`, `static/app.css` — dashboard UI.
- `V*.sql` — Flyway migrations.

## Roles
`ROOT`, `SCHOOL_ADMIN`, and `SCHOOL_USER`.

ROOT creates schools and their initial school administrator. SCHOOL_ADMIN manages the school's fee structure and SCHOOL_USER accounts. SCHOOL_USER can collect fees but cannot configure fees, manage users, or cancel receipts.

## Database migrations
Flyway is the only schema migration mechanism. Docker Compose starts PostgreSQL only. Do not put migration scripts under PostgreSQL's `/docker-entrypoint-initdb.d`.

## Fee rules
Fee amounts are authoritative in `class_fee_structures`, keyed by school, class, fee type, and academic year. Receipt creation looks up these amounts server-side.
