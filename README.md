# ShopSphere — E-commerce Backend

ShopSphere is a REST backend for an online store: customers browse a catalogue, fill a cart, check out, pay,
and track their orders; administrators manage products and fulfil orders.

It is built with **Java 21, Spring Boot 4, Spring Security (JWT), Spring Data JPA, MySQL, Flyway, Redis
and Kafka**, and focuses on the parts of e-commerce that are hard to get right: never overselling stock
under concurrent checkouts, safe retries, consistent caching and a strict order lifecycle.

## Features

| Area | What it does |
|---|---|
| Accounts | Register / login with BCrypt-hashed passwords; stateless JWT access tokens; `CUSTOMER` and `ADMIN` roles |
| Addresses | Customers keep an address book; the chosen address is snapshotted onto each order |
| Catalogue | Categories and products; search by category, name prefix and price range with paging and sorting; soft delete |
| Cart | One cart per user; quantities validated against stock; prices always read live |
| Checkout | Atomic stock reservation (no overselling), `Idempotency-Key` replay, price snapshots, all-or-nothing rollback |
| Payments | `PaymentGateway` abstraction with a sandbox implementation; failed attempts kept; refunds on cancel |
| Order lifecycle | `PENDING_PAYMENT → PAID → SHIPPED → DELIVERED`, cancel before shipping; illegal moves rejected |
| Expiry | A scheduled job cancels orders unpaid for 30 minutes and returns their stock |
| Events | Order events published after commit — to Kafka in production, in-process locally — and turned into notifications |
| Performance | Redis cache for product detail, composite indexes for search, batch fetching instead of N+1 |
| Operations | Flyway migrations, Actuator health, Swagger UI, Docker image, docker-compose stack, GitHub Actions CI |

## Architecture

```
            ┌──────────────── Spring Boot application ────────────────┐
 Client ──► │ JWT filter → Controllers → Services → Repositories (JPA) │ ──► MySQL
            │                    │           │                         │
            │                    │           └─ @Cacheable ──────────► │ ──► Redis
            │                    └─ OrderEvent (after commit) ───────► │ ──► Kafka ──► Notification consumer
            └──────────────────────────────────────────────────────────┘
```

Code is organised by feature (`user`, `catalog`, `cart`, `order`, `payment`, `notification`), each with its own
entities, repository, service, controller and DTOs. Controllers stay thin; services own transactions and rules.

## Running locally

Requirements: **JDK 21**. Nothing else — the default profile uses an in-memory H2 database (MySQL mode),
an in-process cache and in-process events.

```bash
./mvnw spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Seeded admin: `admin@shopsphere.dev` / `Admin@123` (development only)

### Full stack with Docker (MySQL + Redis + Kafka)

```bash
docker compose up --build
```

This runs the `prod` profile against real MySQL, Redis and Kafka. All production settings come from
environment variables (see `src/main/resources/application-prod.yml`); no secrets are committed.

## Try it

```bash
# log in
TOKEN=$(curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"fullName":"Asha Rao","email":"asha@example.com","password":"Secret@123"}' | jq -r .accessToken)

# browse, add an address, fill the cart
curl -s "localhost:8080/api/products?q=Aurora&sortBy=price"
curl -s -X POST localhost:8080/api/addresses -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"line1":"12 MG Road","city":"Bengaluru","state":"Karnataka","postalCode":"560001","country":"India"}'
curl -s -X POST localhost:8080/api/cart/items -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"productId":1,"quantity":2}'

# check out (safe to retry with the same key) and pay
curl -s -X POST localhost:8080/api/orders -H "Authorization: Bearer $TOKEN" -H 'Idempotency-Key: 7f1c-checkout-1' \
  -H 'Content-Type: application/json' -d '{"addressId":1}'
curl -s -X POST localhost:8080/api/orders/1/payments -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"method":"UPI","paymentToken":"tok_test"}'
```

Payment tokens starting with `tok_fail` are declined by the sandbox gateway (HTTP 402).

## API overview

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | public | Create account / obtain token |
| GET | `/api/auth/me` | user | Current profile |
| GET, POST, DELETE | `/api/addresses[/{id}]` | user | Address book |
| GET | `/api/categories`, `/api/products`, `/api/products/{id}` | public | Browse and search |
| POST, PUT | `/api/categories[/{id}]` | admin | Manage categories |
| POST, PUT, PATCH, DELETE | `/api/products[/{id}[/stock]]` | admin | Manage products and stock |
| GET, POST, PUT, DELETE | `/api/cart[/items[/{productId}]]` | user | Cart |
| POST | `/api/orders` | user | Checkout (`Idempotency-Key` header) |
| GET | `/api/orders`, `/api/orders/{id}` | user | Order history / detail |
| POST | `/api/orders/{id}/cancel` | user | Cancel before shipping |
| POST, GET | `/api/orders/{id}/payments` | user | Pay / list attempts |
| GET, PATCH | `/api/admin/orders[/{id}/status]` | admin | List orders, ship / deliver / cancel |

Errors use RFC 9457 problem responses (`400` validation, `401`, `403`, `404`, `409` business rule, `402` declined).

## Tests

```bash
./mvnw test
```

38 tests boot the whole application and call it over HTTP: authentication and access control, catalogue
search and cache consistency, the full purchase lifecycle, idempotent retries, rollback on insufficient stock,
refunds, order expiry, Kafka wiring, and a race of 30 concurrent buyers for 7 units (exactly 7 succeed).

## Benchmarks

```bash
./mvnw test -Dsurefire.excludedGroups= -Dgroups=benchmark   # writes target/benchmark-results.md
```

Latest run (200,000 products, H2 in-memory, 12-core laptop) — full output in [`docs/benchmarks`](docs/benchmarks):

| Measurement | Before | After |
|---|---:|---:|
| Product detail, cache miss → hit (mean) | 1.26 ms | 0.21 ms (5.9×) |
| Search: category + price range (no index → index) | 7.24 ms | 0.58 ms (12.4×) |
| Search: specific name prefix (no index → index) | 29.0 ms | 0.39 ms (75×) |
| Checkout race, 50 buyers for 10 units: naive vs atomic UPDATE | 40 units oversold | 0 oversold |

The benchmark also caught a regression: joining `categories` into the paged search made the optimiser skip the
name index (~134 ms per search). Replacing the join with batch fetching fixed it — see `docs/benchmarks`.

## Project layout

```
src/main/java/com/shopsphere
├── cart/           Cart, CartItem, CartService, CartController
├── catalog/        Category, Product, search specifications, caching
├── common/         Error handling, paging DTO
├── config/         Security, OpenAPI, properties, data seeder
├── notification/   Order event relays (Kafka / in-process), NotificationService
├── order/          Order, OrderItem, state machine, checkout, expiry job
├── payment/        Payment, PaymentGateway + sandbox, PaymentService
├── security/       JWT service and filter
└── user/           User, Address, AuthService
src/main/resources/db/migration   Flyway schema (V1) and indexes (V2)
```
