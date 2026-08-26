# School Fee Management Service

Spring Boot backend for school students, fee collection, immutable receipts, cancellation audit records, and reports.

## Run locally

Install Java 25, Maven, and Docker Desktop. Start PostgreSQL with `docker compose up -d`, then run `mvn spring-boot:run`.

The responsive dashboard is served at `http://localhost:8080`; its API is under `http://localhost:8080/api`. On first start, create the bootstrap administrator using the environment variables in the setup guide. See [the backend design](outputs/school-fee-backend-design.md) for the full API and data design.

See [the full local development guide](docs/local-development.md) for first-run bootstrapping, API examples, tests, and Git commands.
