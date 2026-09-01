# School Fee Management Service

Spring Boot backend and static dashboard for school students, class-wise fee configuration, fee collection, immutable receipts, cancellation audit records, and monthly/yearly reports.

## Roles

- **ROOT** — global platform owner. Creates schools and the initial school administrator for each school.
- **SCHOOL_ADMIN** — belongs to one school. Manages classes, students, fee types, class-wise fee amounts, school users, receipts, cancellations, and reports.
- **SCHOOL_USER** — belongs to one school. Collects fees, prints receipts, manages students, and views receipts/reports. Cannot manage users or fee amounts and cannot cancel receipts.

## Fee structure

Fee amounts are stored in `class_fee_structures` by school, class, fee type, and academic year (for example `2026-27`). When a receipt is created, the backend reads the configured amounts from this table; the browser cannot choose an arbitrary amount.

## First run

Install Java 25, Maven, and Docker Desktop.

Start PostgreSQL:

```text
docker compose up -d
```

Then run the application:

```text
mvn spring-boot:run
```

Set the root credentials before first start:

```text
BOOTSTRAP_ROOT_USERNAME=root
BOOTSTRAP_ROOT_PASSWORD=change-this-password
```

The dashboard is served at `http://localhost:8080` and the API is under `/api`.

## Migrations

Flyway is the single owner of database schema migrations. PostgreSQL Docker is intentionally DB-only; do not copy Flyway SQL files into `/docker-entrypoint-initdb.d`.

For a clean local database:

```text
docker compose down -v
docker compose up -d
mvn spring-boot:run
```
