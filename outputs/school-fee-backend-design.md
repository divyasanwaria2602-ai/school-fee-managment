# School Fee Management Service — Backend Design (MVP)

## 1. Design decisions

* Stack: Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Flyway, PostgreSQL.
* Money is stored as `numeric(12,2)`; never use `float`/`double` for fees.
* A receipt is an immutable financial record. It can be cancelled by an administrator, never edited.
* Fee types are a database table rather than a Java-only enum. This keeps the initial two types while allowing new types without a schema or API redesign.
* The authenticated user's school determines access scope. Request bodies do not control `schoolId` for school users.

## 2. ER design and relationships

```text
schools 1 ── * users
schools 1 ── * classes 1 ── * students
schools 1 ── * fee_types
students 1 ── * fee_receipts 1 ── * fee_receipt_items * ── 1 fee_types
users 1 ── * fee_receipts (created_by)
users 1 ── * audit_logs
```

`students.school_id` is intentionally retained even though the class also has a school. It enables direct ownership checks and must match the selected class's school; the service enforces this invariant.

## 3. PostgreSQL / Flyway migration

Create `src/main/resources/db/migration/V1__initial_schema.sql` with the following content.

```sql
CREATE TYPE user_role AS ENUM ('ADMIN', 'SCHOOL');
CREATE TYPE receipt_status AS ENUM ('ACTIVE', 'CANCELLED');

CREATE TABLE schools (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    address TEXT,
    phone VARCHAR(30),
    email VARCHAR(254),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role user_role NOT NULL,
    school_id BIGINT REFERENCES schools(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT school_users_require_school CHECK (role = 'ADMIN' OR school_id IS NOT NULL)
);

CREATE TABLE classes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id),
    name VARCHAR(50) NOT NULL,
    section VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_class_per_school UNIQUE (school_id, name, section)
);

CREATE TABLE students (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id),
    class_id BIGINT NOT NULL REFERENCES classes(id),
    admission_number VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    father_name VARCHAR(150),
    mother_name VARCHAR(150),
    guardian_name VARCHAR(150),
    phone VARCHAR(30),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_admission_per_school UNIQUE (school_id, admission_number)
);

CREATE TABLE fee_types (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id),
    code VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_fee_type_per_school UNIQUE (school_id, code)
);

CREATE TABLE fee_receipts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id BIGINT NOT NULL REFERENCES schools(id),
    receipt_number VARCHAR(30) NOT NULL UNIQUE,
    student_id BIGINT NOT NULL REFERENCES students(id),
    total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount > 0),
    payment_date DATE NOT NULL,
    status receipt_status NOT NULL DEFAULT 'ACTIVE',
    cancellation_reason VARCHAR(500),
    cancelled_by BIGINT REFERENCES users(id),
    cancelled_at TIMESTAMPTZ,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cancelled_receipt_has_metadata CHECK (
        (status = 'ACTIVE' AND cancellation_reason IS NULL AND cancelled_by IS NULL AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND cancellation_reason IS NOT NULL AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL)
    )
);

CREATE TABLE fee_receipt_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receipt_id BIGINT NOT NULL REFERENCES fee_receipts(id),
    fee_type_id BIGINT NOT NULL REFERENCES fee_types(id),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    CONSTRAINT uq_receipt_fee_type UNIQUE (receipt_id, fee_type_id)
);

CREATE TABLE audit_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_id BIGINT REFERENCES schools(id),
    user_id BIGINT REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    old_value JSONB,
    new_value JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE receipt_number_sequences (
    school_id BIGINT NOT NULL REFERENCES schools(id),
    receipt_year INTEGER NOT NULL,
    last_value BIGINT NOT NULL DEFAULT 0 CHECK (last_value >= 0),
    PRIMARY KEY (school_id, receipt_year)
);

CREATE INDEX idx_classes_school ON classes(school_id);
CREATE INDEX idx_students_school_class ON students(school_id, class_id);
CREATE INDEX idx_receipts_school_date_status ON fee_receipts(school_id, payment_date, status);
CREATE INDEX idx_receipts_student ON fee_receipts(student_id);
CREATE INDEX idx_receipt_items_receipt ON fee_receipt_items(receipt_id);
CREATE INDEX idx_audit_logs_school_entity ON audit_logs(school_id, entity_type, entity_id, created_at DESC);

INSERT INTO fee_types (school_id, code, display_name)
SELECT id, 'INSTITUTE_FEE', 'Institute Fee' FROM schools;
-- Seed VAN_FEE alongside the initial school creation transaction, or use a V2 seed migration once the school exists.
```

### Important migration notes

* Add a small reusable `set_updated_at()` trigger in a follow-up migration for mutable tables (`schools`, `users`, `classes`, `students`, `fee_types`).
* Do **not** cascade-delete financial tables. Deactivate schools, classes, students, users, and fee types instead.
* The service validates that student, class, receipt, and fee type all belong to the acting user's school. Cross-table ownership is not inferred from IDs alone.

## 4. Receipt number generation

Inside the same transaction as receipt creation, atomically allocate a number:

```sql
INSERT INTO receipt_number_sequences (school_id, receipt_year, last_value)
VALUES (:schoolId, :year, 1)
ON CONFLICT (school_id, receipt_year)
DO UPDATE SET last_value = receipt_number_sequences.last_value + 1
RETURNING last_value;
```

Format it as `2026-000001` (or `S{schoolId}-2026-000001` once multi-school numbering needs human-level uniqueness). The unique receipt-number constraint is the final safety net. Allocation and receipt insert are rolled back together if creation fails.

## 5. API contract

All APIs are under `/api`. Return RFC 7807 `application/problem+json` errors with a stable `code` such as `STUDENT_NOT_FOUND`, `FORBIDDEN_SCHOOL_SCOPE`, or `RECEIPT_NOT_ACTIVE`.

| Endpoint | Roles | Purpose |
|---|---|---|
| `POST /auth/login` | public | Authenticate and return a short-lived access token plus user details. |
| `GET, POST, PUT /schools` | ADMIN | List/create/update schools. |
| `GET, POST, PUT /classes` | ADMIN; SCHOOL read/create as permitted | Scoped class management. |
| `GET, POST, PUT /students` | ADMIN; SCHOOL read/create/update as permitted | Scoped student management; no hard delete. |
| `GET, POST /fee-types` | ADMIN | List and manage configurable fee types. |
| `GET /fees`, `GET /fees/{id}`, `POST /fees` | ADMIN, SCHOOL | List/view/create fee receipts in caller's school. |
| `POST /fees/{id}/cancel` | ADMIN | Cancel an active receipt. |
| `GET /receipts/{feeReceiptId}`, `GET /receipts/{feeReceiptId}/print` | ADMIN, SCHOOL | Retrieve structured receipt / print-ready HTML or PDF. |
| `GET /reports/fees/monthly`, `GET /reports/fees/yearly` | ADMIN, SCHOOL | Scoped aggregates; restrict any school-user report dimensions as needed. |
| `GET /users`, `POST /users`, `PUT /users/{id}` | ADMIN | Manage users. |
| `GET /audit` | ADMIN | Search audit history. |

There is deliberately no `PUT /fees/{id}` and no deletion endpoint for a fee receipt.

## 6. Key DTOs

```java
public record CreateFeeReceiptRequest(
    @NotNull Long studentId,
    @NotNull LocalDate paymentDate,
    @NotEmpty List<@Valid FeeItemRequest> items
) {}

public record FeeItemRequest(
    @NotNull Long feeTypeId,
    @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount
) {}

public record FeeReceiptResponse(
    Long id, String receiptNumber, Long studentId, String studentName,
    String admissionNumber, String className, String section,
    LocalDate paymentDate, ReceiptStatus status, BigDecimal totalAmount,
    List<FeeReceiptItemResponse> items, Instant createdAt
) {}

public record FeeReceiptItemResponse(Long feeTypeId, String feeTypeCode,
                                     String feeTypeName, BigDecimal amount) {}

public record CancelFeeReceiptRequest(@NotBlank @Size(max = 500) String reason) {}

public record ReportResponse(String period, List<FeeBreakdownResponse> breakdown,
                             BigDecimal total, List<MonthlyTotalResponse> monthlyBreakdown) {}
public record FeeBreakdownResponse(String feeTypeCode, String feeTypeName, BigDecimal amount) {}
public record MonthlyTotalResponse(String period, BigDecimal total) {}
```

`CreateStudentRequest` includes class ID, admission number, name, family contact fields, and active status. It has no school ID for school users. `CreateUserRequest` includes username, password, role, and school ID only when creating a `SCHOOL` user.

## 7. Authentication and authorization

* Hash passwords with BCrypt (cost 12, configurable) and never log a password or hash.
* Use signed JWT access tokens; include `sub` (user ID), role, school ID, and a short expiry (e.g. 15 minutes). Use an HttpOnly secure refresh-token cookie only if refresh sessions are implemented.
* A `CurrentUser` service loads/validates the active user and school context. Repositories/services accept the resolved school scope; controllers never trust client-supplied school ownership.
* `ADMIN` has cross-school access. A school user can access only `school_id == currentUser.schoolId` and never cancel a receipt or alter a completed fee.
* Use method-level authorization (`@PreAuthorize`) for coarse roles and service-level ownership checks for every record lookup.

## 8. Fee creation and cancellation rules

### Create receipt (`@Transactional`)

1. Resolve active current user; require ADMIN or SCHOOL.
2. Load an active student within the user's school scope.
3. Require 1+ items; reject duplicate fee-type IDs, non-positive values, and a total of zero.
4. Load each active fee type in the same school; reject missing or foreign types.
5. Compute the total from item amounts on the server, using `BigDecimal`.
6. Atomically reserve the receipt number.
7. Insert the active receipt and its items.
8. Write a `CREATE_FEE` audit log with receipt metadata and items (no unnecessary sensitive data).
9. Commit, then return the receipt. Any exception rolls back every insert and the sequence increment.

### Cancel receipt (`@Transactional`, ADMIN only)

1. Load the receipt in the authorized scope with a row lock if needed.
2. Require `status == ACTIVE`; reject repeated cancellation.
3. Set status to `CANCELLED`, cancellation reason, actor, and timestamp. Do not change total/items/payment date.
4. Write `CANCEL_FEE` audit log including previous and new status.
5. Commit. Reports exclude it immediately.

## 9. Reporting query design

Required filters: `schoolId` (resolved from auth), date period, optional fee-type ID, optional class ID. Always include `r.status = 'ACTIVE'`.

### Monthly totals by type

```sql
SELECT ft.code AS fee_type_code, ft.display_name AS fee_type_name,
       COALESCE(SUM(i.amount), 0) AS amount
FROM fee_receipts r
JOIN fee_receipt_items i ON i.receipt_id = r.id
JOIN fee_types ft ON ft.id = i.fee_type_id
JOIN students s ON s.id = r.student_id
WHERE r.school_id = :schoolId
  AND r.status = 'ACTIVE'
  AND r.payment_date >= :periodStart
  AND r.payment_date < :periodEnd
  AND (:feeTypeId IS NULL OR i.fee_type_id = :feeTypeId)
  AND (:classId IS NULL OR s.class_id = :classId)
GROUP BY ft.code, ft.display_name
ORDER BY ft.code;
```

Compute `total` as the sum of returned rows. Use exact start/end dates, not string-based month matching.

### Financial-year monthly totals

For financial year `2026-27`, use `[2026-04-01, 2027-04-01)`. Reuse the query above for fee-type breakdown over the full range, and add:

```sql
SELECT date_trunc('month', r.payment_date)::date AS month,
       COALESCE(SUM(i.amount), 0) AS total
FROM fee_receipts r
JOIN fee_receipt_items i ON i.receipt_id = r.id
JOIN students s ON s.id = r.student_id
WHERE r.school_id = :schoolId AND r.status = 'ACTIVE'
  AND r.payment_date >= :fyStart AND r.payment_date < :fyEnd
  AND (:feeTypeId IS NULL OR i.fee_type_id = :feeTypeId)
  AND (:classId IS NULL OR s.class_id = :classId)
GROUP BY 1 ORDER BY 1;
```

The service fills missing financial-year months with zero totals.

## 10. Critical automated tests

Use Testcontainers PostgreSQL for integration tests; security and transactional tests should not rely solely on mocked repositories.

1. SCHOOL user creates a receipt for an active student in their school; response total equals item sum and all items persist.
2. A failed item insert/validation rolls back receipt, receipt items, audit record, and number allocation.
3. Concurrent fee submissions receive different receipt numbers and both persist successfully.
4. SCHOOL user cannot retrieve/create a receipt for another school's student, even with a guessed ID.
5. SCHOOL user receives `403` on `POST /fees/{id}/cancel` and no generic fee update route exists.
6. ADMIN cancellation changes only cancellation metadata/status; amount and items remain unchanged.
7. A cancelled receipt is absent from monthly and yearly totals.
8. Duplicate fee type in one request, zero/negative amount, empty items, inactive student, and inactive/foreign fee type are rejected.
9. Receipt response has correct class/student snapshot fields and printable data including amount in words, generated from the stored numeric total.
10. Password is BCrypt-hashed; inactive user cannot authenticate; SCHOOL user has a school ID.
11. Student admission number is unique within, but not across, schools.
12. Audit records identify actor, action, entity, timestamp, and a structured before/after payload for create/cancel.

## 11. Incremental delivery order

1. Bootstrap Spring Boot, PostgreSQL/Flyway, error handling, and testcontainers.
2. Create the migration and JPA entities/repositories.
3. Implement authentication, current-user context, roles, and authorization helpers.
4. Implement school, class, student, user, and fee-type management.
5. Implement fee creation, cancellation, receipt retrieval, and audit logging with integration tests.
6. Add monthly/yearly reporting and print-ready receipt endpoint.

This sequence preserves the fee workflow as the first complex, transactionally tested feature while avoiding premature infrastructure or frontend work.
