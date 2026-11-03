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

### Trying it out

The client's country is resolved from its IP address. Requests sent from your machine reach the
application from a private address (`127.0.0.1` or the Docker network), which has no country, so
coupon usage would fail with `503 GEOLOCATION_UNAVAILABLE`. Local addresses are trusted as proxies,
so you can simulate a client by sending its public IP in `X-Forwarded-For`:

```bash
curl -X POST localhost:8080/api/v1/coupons \
  -H "Content-Type: application/json" \
  -d '{ "code": "Spring", "maxUses": 2, "country": "PL" }'

# 83.0.0.1 is a Polish IP address
curl -X POST localhost:8080/api/v1/coupons/spring/usages \
  -H "Content-Type: application/json" -H "X-Forwarded-For: 83.0.0.1" \
  -d '{ "userId": "lukasz" }'

curl localhost:8080/api/v1/coupons/spring
```

The examples use ASCII only: on Windows, shells may not send command-line arguments as UTF-8.

The same works in Swagger UI at `http://localhost:8080/swagger-ui.html`: the usage endpoint has an
`X-Forwarded-For` field. The OpenAPI document is available at `/v3/api-docs`.

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

Geolocation is configured in `application.properties`; any value can be overridden with an
environment variable (e.g. `GEOLOCATION_READTIMEOUT=10s`):

| Property                         | Default             | Description                              |
|----------------------------------|---------------------|------------------------------------------|
| `geolocation.base-url`           | `https://ipapi.co`  | Geolocation service                      |
| `geolocation.connect-timeout`    | `2s`                | Maximum time to connect to the service   |
| `geolocation.read-timeout`       | `5s`                | Maximum time to wait for its response    |
| `geolocation.cache.ttl`          | `1h`                | How long a resolved country is cached    |
| `geolocation.cache.maximum-size` | `10000`             | Maximum number of cached IP addresses    |

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

- `code`: up to 32 characters (ASCII letters, digits, `-`, `_`), case-insensitive, stored and
  returned in upper case
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
| `503 Service Unavailable` | The client's country cannot be determined (`GEOLOCATION_UNAVAILABLE`) |

```json
{ "code": "SPRING", "userId": "user-123", "usedAt": "2026-11-02T12:05:00Z" }
```

Unlike coupons, a usage has no `Location` header: it is a record in the coupon's usage log, not a
resource with its own address, and exposing it would require leaking its technical ID.

When several errors apply, the first one in this order is returned:

1. `COUPON_NOT_FOUND`
2. `COUPON_ALREADY_USED`: a fact about the user that does not depend on where they connect from
3. `GEOLOCATION_UNAVAILABLE`
4. `COUNTRY_NOT_ALLOWED`
5. `COUPON_USAGE_LIMIT_REACHED`

### Errors

Errors follow [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457). The `code` field
distinguishes errors that share the same HTTP status:

```json
{
  "title": "Conflict",
  "status": 409,
  "detail": "Coupon SPRING has reached its usage limit",
  "instance": "/api/v1/coupons/SPRING/usages",
  "code": "COUPON_USAGE_LIMIT_REACHED"
}
```

Validation errors (`VALIDATION_FAILED`) also list the invalid fields:
`"errors": [{ "field": "maxUses", "message": "must be greater than 0" }]`.

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

The `country` check validates the format only; membership in ISO 3166-1 is validated by the
application.

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

### Indexes

All queries go through the indexes created by the primary key and unique constraints:

| Index                         | Used for                                                     |
|-------------------------------|--------------------------------------------------------------|
| `uq_coupon_code`              | every API operation (coupons are looked up by code)          |
| `uq_coupon_usage_coupon_user` | the "already used" check, usages by coupon, foreign key checks |

No additional indexes are needed. Indexes on `country` or `user_id` alone would only slow down
writes, as no query filters by them.

## Scalability

The application is stateless, so it scales horizontally: any number of instances can run behind a
load balancer, sharing one database.

- **Correctness does not depend on the number of instances.** Usage limits and one use per user are
  enforced by the database (an atomic conditional update and unique constraints, see decisions 17
  and 20), not by in-memory locks, so they hold across instances without distributed locking.
  `CouponUsageConcurrencyTest` checks this with 100 concurrent requests for a coupon with a limit of 10.
- **Contention is limited to a single coupon.** Only usages of the same coupon wait for each other,
  and only for a short transaction (one insert and one update); the external geolocation call happens
  before it starts. Usages of different coupons do not block each other, and reads are never blocked.
- **Most rejected requests do not write.** Requests for an exhausted coupon, a repeated usage or a
  wrong country are answered from reads, so a popular coupon running out does not turn into a
  stream of failing transactions.
- **The geolocation cache is per instance.** Each instance resolves a given IP address at most once
  per hour, and concurrent requests for the same address share one lookup. With several instances
  an address may be resolved once per instance, which is cheaper than running a shared cache.
- **Database connections are short-lived.** No connection is held during the geolocation call, so
  the default pool of 10 connections per instance goes a long way. The total across instances must
  stay below PostgreSQL's `max_connections`.

Under much higher load, the next steps would be:

- **A local GeoIP database** (e.g. MaxMind GeoLite2) instead of an HTTP API. The free ipapi.co tier
  (about 1,000 requests a day) is the first bottleneck; a local database has no rate limit, no
  network call and no external dependency.
- **A connection pooler** (PgBouncer) once the number of instances makes `max_connections` a limit.
- **Read replicas** for `GET /coupons/{code}`, as coupons are read far more often than created.
- **Splitting the counter of a very hot coupon** into several rows incremented independently, if the
  row lock on a single coupon ever became the bottleneck.

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
18. **Coupon codes restricted to ASCII letters, digits, `-` and `_`.** Codes are typed in by users,
    and upper-casing non-ASCII letters is locale-dependent, which could break case-insensitive
    uniqueness.
19. **Packages by feature** (`coupon`, `geolocation`, `error`) instead of by layer. Everything
    about a feature lives in one place, and classes that are not part of its API (controller, DTOs,
    entities, repositories) stay package-private, so the compiler enforces the boundaries.
    A feature package is split into subpackages only when its size requires it.
20. **Coupon usage: read-only checks first, then a short transaction.**

    | Step | Where | Fails with |
    |------|-------|------------|
    | 1. Find the coupon by code | read | `COUPON_NOT_FOUND` |
    | 2. Check whether the user has already used it | read | `COUPON_ALREADY_USED` |
    | 3. Resolve the client's country and compare it | read | `GEOLOCATION_UNAVAILABLE`, `COUNTRY_NOT_ALLOWED` |
    | 4. Check `current_uses < max_uses` | read | `COUPON_USAGE_LIMIT_REACHED` |
    | 5. Insert the usage record | transaction | `COUPON_ALREADY_USED` (unique constraint) |
    | 6. Conditionally increment the counter | transaction | `COUPON_USAGE_LIMIT_REACHED` (0 rows updated) |

    Steps 1–4 are an optimization and define the error precedence: most rejected requests (e.g. for an
    exhausted coupon) are answered without any write or lock. Steps 5–6 guarantee correctness: they
    catch the races between the reads and the transaction (the same user sending two requests at once,
    the coupon running out meanwhile), and removing steps 1–4 would not change the outcome. The country
    is resolved outside the transaction, so no database connection is held during the external call.
    Inserting before updating keeps the row lock on the coupon, the contention point, as short as
    possible, and every transaction takes its locks in the same order, which rules out deadlocks.
    Exhausted coupons are not cached: the reads are cheap, and a cache would break as soon as limits
    could be raised.
21. **Country resolution behind the `CountryResolver` interface.** The coupon logic does not know
    where the country comes from, and tests can simulate any country.
22. **[ipapi.co](https://ipapi.co) as the geolocation provider.** No signup or API key, so the
    project runs without configuration; HTTPS, which matters as IP addresses are personal data; and
    the `/{ip}/country/` endpoint returns just the country code. The free tier allows about 1,000
    requests a day, which the cache stretches. Switching providers means writing one new
    `CountryResolver` implementation.
23. **Fail closed when the country cannot be determined.** Provider errors, timeouts, rate limiting,
    unknown countries and non-public client addresses all reject the usage with
    `503 GEOLOCATION_UNAVAILABLE`. Letting the usage through would allow bypassing the country
    restriction by causing a geolocation failure. Private addresses are rejected without calling the
    provider; in production they only appear when the proxy configuration is wrong.
24. **Explicit timeouts for the provider: 2 s to connect, 5 s for the response.** Without them a
    hanging provider would block request threads indefinitely and take the whole service down with
    it. The defaults are generous on purpose, so the project works on slow connections, and both are
    configurable.
25. **Cache as an explicit decorator** (`CachingCountryResolver`, Caffeine) rather than
    `@Cacheable`. It makes visible that only successful lookups are cached, and it is tested without
    a Spring context. Entries expire after 1 hour, as IP addresses get reassigned.
26. **`X-Forwarded-For` honored only from trusted proxies** (`server.forward-headers-strategy=native`,
    Tomcat's `RemoteIpValve`). Requests from internal addresses, such as a load balancer, may forward
    the client's IP; anyone else gets their connection address used, so clients cannot spoof their
    country.
27. **OpenAPI document generated from the code** (springdoc), so it cannot drift from the
    implementation. Annotations add only what the code does not express: statuses and error codes.
    The Problem Details schema is attached to every error response in one place
    (`OpenApiConfiguration`) instead of being repeated on each endpoint.
28. **Errors identified by a `code` property rather than custom `type` URIs.** Responses use the
    RFC 9457 default type, `about:blank` (omitted from the JSON, which means the same), with the
    HTTP status as `title`. Custom type URIs would need a stable address documenting each error,
    which this project does not have, while a plain code is simpler for clients to handle.
