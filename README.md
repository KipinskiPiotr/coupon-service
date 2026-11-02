# Coupon Service

REST service for managing discount coupons: creating coupons and registering their usage,
with per-coupon usage limits and country restrictions based on the client's IP address.

## Tech stack

- Java 25, Spring Boot 4, Maven
- PostgreSQL 18, Flyway
- Docker Compose (local environment), Testcontainers (tests)
- GitHub Actions (CI)

## Quick start

Requirements: Docker with Compose v2.

```bash
docker compose up --build
```

This builds the application image and starts it together with the database.
The application is available on `http://localhost:8080`. Health check: `GET /actuator/health`.

If port `8080` or `5433` is already in use, override it with `APP_PORT` or `DB_HOST_PORT`
(as an environment variable or in a `.env` file), for example:

```bash
APP_PORT=8081 docker compose up --build
```

## Development

Requirements: Docker, JDK 25 and an IDE that supports Java 25.

```bash
# start only the database
docker compose up -d db

# run the application (or run CouponServiceApplication from the IDE)
./mvnw spring-boot:run
```

If the full stack is running, stop the application container first to free port `8080`:
`docker compose stop app`.

### Configuration

| Variable            | Default     | Description                                 |
|---------------------|-------------|---------------------------------------------|
| `APP_PORT`          | `8080`      | Host port the application is exposed on     |
| `DB_HOST`           | `localhost` | Database host used by the app               |
| `DB_PORT`           | `5433`      | Database port used by the app               |
| `DB_HOST_PORT`      | `5433`      | Host port the database is exposed on        |
| `POSTGRES_DB`       | `coupon`    | Database name                               |
| `POSTGRES_USER`     | `coupon`    | Database user                               |
| `POSTGRES_PASSWORD` | `coupon`    | Database password                           |

Inside Docker Compose the application connects to `db:5432`; the defaults above apply when it runs
on the host.

## Running tests

```bash
./mvnw verify
```

Integration tests start their own PostgreSQL container via Testcontainers, so only Docker is required.

## API

Base path: `/api/v1`. Coupons are identified by their code.

### Create a coupon

`POST /api/v1/coupons`

```json
{ "code": "Spring", "maxUses": 100, "country": "PL" }
```

- `code`: up to 32 characters, case-insensitive, stored and returned in upper case
- `maxUses`: positive integer, required
- `country`: ISO 3166-1 alpha-2 code

| Status | When |
|--------|------|
| `201 Created` | Coupon created. `Location` header points to the coupon, the body contains it. |
| `400 Bad Request` | Validation failed |
| `409 Conflict` | A coupon with this code already exists (`COUPON_CODE_ALREADY_EXISTS`) |

```json
{ "code": "SPRING", "maxUses": 100, "currentUses": 0, "country": "PL", "createdAt": "2026-11-02T12:00:00Z" }
```

### Get a coupon

`GET /api/v1/coupons/{code}`

Returns the current state of the coupon, in the same format as the response of the create endpoint.
The code is case-insensitive.

| Status | When |
|--------|------|
| `200 OK` | Coupon found |
| `404 Not Found` | The coupon does not exist (`COUPON_NOT_FOUND`) |

### Use a coupon

`POST /api/v1/coupons/{code}/usages`

```json
{ "userId": "user-123" }
```

- `userId`: any identifier up to 36 characters (fits a UUID), required

The client's country is resolved from the IP address of the request.

| Status | When |
|--------|------|
| `201 Created` | Usage registered, the body contains it |
| `400 Bad Request` | Validation failed |
| `403 Forbidden` | The coupon is not available in the client's country (`COUNTRY_NOT_ALLOWED`) |
| `404 Not Found` | The coupon does not exist (`COUPON_NOT_FOUND`) |
| `409 Conflict` | The usage limit has been reached (`COUPON_USAGE_LIMIT_REACHED`) or the user has already used the coupon (`COUPON_ALREADY_USED`) |

```json
{ "code": "SPRING", "userId": "user-123", "usedAt": "2026-11-02T12:05:00Z" }
```

Unlike coupons, a usage has no `Location` header: it is a record in the coupon's usage log, not a
resource with its own address, and exposing it would require leaking its technical ID.

### Errors

Errors follow [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457). The `code` field
distinguishes errors that share the same HTTP status:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Coupon SPRING has reached its usage limit",
  "code": "COUPON_USAGE_LIMIT_REACHED"
}
```

### Assumptions

- Coupon codes are globally unique (case-insensitive); each coupon targets exactly one country.
- `userId` is sent in the request body, as the task requires no authentication. In production it
  would come from the authenticated principal (e.g. a JWT), never from client-provided data.
- The client IP is taken from the connection (`getRemoteAddr()`), never from a header set by the
  client. Behind a load balancer `X-Forwarded-For` is honored only when the request comes from a
  trusted proxy (`server.forward-headers-strategy=native`).

## Database schema

### `coupon`

| Column         | Type          | Constraints                                         |
|----------------|---------------|-----------------------------------------------------|
| `id`           | `bigint`      | primary key, generated always as identity           |
| `code`         | `varchar(32)` | not null, unique, `check (code = upper(code))`      |
| `max_uses`     | `integer`     | not null, `check (max_uses > 0)`                    |
| `current_uses` | `integer`     | not null, default `0`, `check (current_uses between 0 and max_uses)` |
| `country`      | `varchar(2)`  | not null, `check (country ~ '^[A-Z]{2}$')`          |
| `created_at`   | `timestamptz` | not null, default `now()`                           |

### `coupon_usage`

| Column      | Type          | Constraints                                |
|-------------|---------------|--------------------------------------------|
| `id`        | `bigint`      | primary key, generated always as identity  |
| `coupon_id` | `bigint`      | not null, foreign key to `coupon(id)`      |
| `user_id`   | `varchar(36)` | not null                                   |
| `country`   | `varchar(2)`  | not null, country the coupon was used from |
| `used_at`   | `timestamptz` | not null, default `now()`                  |

`unique (coupon_id, user_id)` enforces one usage per user. Its index starts with `coupon_id`, so it
also serves lookups of usages by coupon; no separate index on the foreign key is needed.

## Design decisions

1. **PostgreSQL as the database.** The core of the task is enforcing business rules under concurrent
   requests (unique codes, usage limits, one use per user). A relational database enforces these with
   constraints and transactions instead of application code.
2. **Schema managed by Flyway**, with Hibernate set to `validate` only. Schema changes are explicit,
   versioned and reviewable.
3. **Geolocation results are cached instead of calling the external service on every request.**
   This reduces latency and protects against the provider's rate limits and outages. The cache lives
   in memory with a TTL. IP addresses are not stored in the database, because they are personal data
   under GDPR. Only the resolved country is kept.
4. **Local database exposed on port 5433** to avoid conflicts with a locally installed PostgreSQL.
   Inside the Docker network the database still runs on 5432.
5. **Default credentials for local development** in `application.properties`, so the project runs
   without any setup. In production every value is provided through environment variables.
6. **Integration tests on a real PostgreSQL** (Testcontainers, same version as in Docker Compose).
   An in-memory database would not reproduce PostgreSQL's locking and constraint behavior.
7. **Multi-stage Docker image.** The JDK is used only to build the jar; the final image contains
   just the JRE and the jar, and runs as a non-root user. Dependencies are downloaded in a separate
   layer, so code changes do not trigger a full re-download.
8. **Docker Compose is for local use only.** In production the image built from the `Dockerfile` is
   deployed to a container platform with a managed database, and configuration is provided through
   environment variables.
9. **Workflow: pull requests with squash merge.** CI runs on every pull request and on `main` with two
   parallel jobs: `test` (all tests) and `smoke-test` (starts the full Docker Compose stack and checks
   the health endpoint).
10. **Coupon code as the public identifier.** Clients know coupons by their code, so the API uses it
    in paths. The database keeps a technical primary key that is never exposed.
11. **Using a coupon creates a usage resource** (`POST /coupons/{code}/usages`) instead of calling an
    action. It matches the data model, where every usage is a new record, and leaves room for
    `GET /coupons/{code}/usages` later.
12. **API versioned in the path** (`/api/v1`): the simplest approach, visible in every request.
13. **`bigint` identity as the primary key** instead of UUID. The key is internal only (the API uses
    coupon codes), so the main advantages of UUIDs do not apply, while `bigint` is smaller and keeps
    the index ordered.
14. **Timestamps as `timestamptz`**, mapped to `Instant`: an unambiguous point in time, independent
    of the server's or client's time zone.
15. **Coupon codes limited to 32 characters**, a business rule: codes are typed in by users.
16. **Case-insensitive uniqueness enforced by the database.** Codes are normalized to upper case in
    the application, and `check (code = upper(code))` rejects any value that bypasses it, so a plain
    unique constraint is enough.
17. **Usage counter instead of counting usage rows.** The limit is enforced with a single atomic
    statement, `update coupon set current_uses = current_uses + 1 where id = ? and current_uses < max_uses`.
    Concurrent updates of the same coupon wait for the row lock and re-check the condition, so the
    limit can never be exceeded; zero updated rows means the limit is reached. Counting rows would
    need an explicit lock anyway and gets slower as usages grow. Reads are not blocked thanks to
    PostgreSQL's MVCC. The counter and the usage record are written in one transaction.

## Open questions

<!-- To be decided and moved to "Design decisions" -->

- Order of operations in the usage transaction and which error wins when several apply
- Geolocation provider, timeout, behavior when it is unavailable
- Behavior when the client IP cannot be geolocated (e.g. a private address)
