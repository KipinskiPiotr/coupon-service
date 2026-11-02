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

<!-- TODO: endpoints, example requests and responses, error codes -->

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

## Open questions

<!-- To be decided and moved to "Design decisions" -->

- Database schema: tables, columns, constraints
- API: endpoints, request and response format, error format
- Concurrency strategy for coupon redemption
- Geolocation provider, timeout, behavior when it is unavailable
- Handling of private and local IP addresses (local development, `X-Forwarded-For`)
