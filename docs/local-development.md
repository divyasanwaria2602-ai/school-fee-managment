# Local build and server guide

## Prerequisites

1. Install **JDK 25**. Confirm it with `java -version`.
2. Install Apache Maven 3.9+ and confirm it with `mvn -version`.
3. Install Docker Desktop and start it.
4. Clone the repository and enter its folder:

```powershell
git clone https://github.com/divyasanwaria2602-ai/school-fee-managment.git
cd school-fee-managment
```

## Start PostgreSQL

```powershell
docker compose up -d
docker compose ps
```

The development database is exposed at `localhost:5432`, database `school_fees`, user `school`. Its password is for local development only; change it before any shared deployment.

To permanently erase local database data, stop it and use `docker compose down -v`. This is destructive.

## Start the API and bootstrap the first administrator

On the first run only, supply a username/password and school name. They are read at startup and the password is BCrypt-hashed before storage.

```powershell
$env:BOOTSTRAP_ADMIN_USERNAME = "admin"
$env:BOOTSTRAP_ADMIN_PASSWORD = "choose-a-long-local-password"
$env:BOOTSTRAP_SCHOOL_NAME = "Example Public School"
mvn spring-boot:run
```

Flyway automatically applies the schema migration. The bootstrap logic does nothing if an active user with that username already exists, so remove `BOOTSTRAP_ADMIN_PASSWORD` after the first successful startup:

```powershell
Remove-Item Env:BOOTSTRAP_ADMIN_PASSWORD
```

The server listens on `http://localhost:8080` and uses HTTP Basic authentication for the MVP. Use HTTPS and a token/session strategy before production deployment.

## Verify the API

Open a second PowerShell window. Replace `admin:choose-a-long-local-password` with the bootstrap credentials.

```powershell
curl.exe -u "admin:choose-a-long-local-password" http://localhost:8080/api/classes
curl.exe -u "admin:choose-a-long-local-password" http://localhost:8080/api/fee-types?schoolId=1
```

Create a class:

```powershell
curl.exe -u "admin:choose-a-long-local-password" -H "Content-Type: application/json" -d '{"name":"5","section":"A"}' "http://localhost:8080/api/classes?schoolId=1"
```

Create a student (use the class ID returned above):

```powershell
curl.exe -u "admin:choose-a-long-local-password" -H "Content-Type: application/json" -d '{"classId":1,"admissionNumber":"10234","name":"Rahul Sharma","fatherName":"Amit Sharma","phone":"9999999999"}' "http://localhost:8080/api/students?schoolId=1"
```

Create a combined fee receipt (the seeded fee types use IDs 1 and 2 on a new database):

```powershell
curl.exe -u "admin:choose-a-long-local-password" -H "Content-Type: application/json" -d '{"studentId":1,"paymentDate":"2026-08-26","items":[{"feeTypeId":1,"amount":3000},{"feeTypeId":2,"amount":1500}]}' "http://localhost:8080/api/fees?schoolId=1"
```

View the monthly report:

```powershell
curl.exe -u "admin:choose-a-long-local-password" "http://localhost:8080/api/reports/fees/monthly?schoolId=1&year=2026&month=8"
```

Cancel a receipt as the administrator; it stays in the database but is excluded from reports:

```powershell
curl.exe -X POST -u "admin:choose-a-long-local-password" -H "Content-Type: application/json" -d '{"reason":"Incorrect van-fee amount"}' "http://localhost:8080/api/fees/1/cancel?schoolId=1"
```

## Test and package

```powershell
mvn spotless:apply
mvn spotless:check
mvn test
mvn package
java -jar target/school-fee-management-0.0.1-SNAPSHOT.jar
```

`mvn verify` also runs `spotless:check`, so CI fails if Java files are not formatted. Run `mvn spotless:apply` before committing to rewrite them using Google Java Format.

## Development workflow

```powershell
git status
git add .
git commit -m "Describe the change"
git push
```

The remote GitHub repository is `https://github.com/divyasanwaria2602-ai/school-fee-managment`.
