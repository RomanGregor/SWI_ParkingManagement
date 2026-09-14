# Car Park Reservation System

A reservation system for a single car park: drivers reserve an individual **parking spot** for a
time window, the system guarantees that no spot is promised to two cars at once, and that the spot
they get actually fits their vehicle.

> **TODO before C02 — fill these in and delete this block**
>
> | Item | Value |
> |---|---|
> | Team name | `TODO_TEAM_NAME` |
> | Member 1 | Roman Gregor — commits authored as `RomanGregor` |
> | Member 2 | `TODO_MEMBER_2` — commits authored as *Member B* (wrote the C01 spike) |
> | Member 3 | `TODO_MEMBER_3` — commits authored as *Member C* (reviewed the C01 spike) |
> | Repository URL | https://github.com/RomanGregor/SWI_Reservation |
>
> Members 2 and 3 still carry the placeholder identities `Member B` / `Member C` in the git history.
> Put their real names in this table when they join; rewriting the commit authors to match is
> optional and described in the git history section below.

---

## CP1 walking skeleton

The one end-to-end path that will be genuinely runnable after C03 / before C04:

```
POST /reservations
→ validate            (time window, spot exists and is in service, spot/vehicle compatibility)
→ persist             (INSERT into reservation in PostgreSQL, status = DRAFT)
→ return reservation ID  (HTTP 201, body { "id": ..., "status": "DRAFT" }, Location header)
→ automated check     (ReservationApiIT over real HTTP + ReservationPersistenceSpikeIT against a real database)
```

Concretely, in this repository the skeleton is:

| Step | Where it lives today |
|---|---|
| HTTP entry point | [`ReservationController.create`](src/main/java/cz/swi/parking/web/ReservationController.java) — `POST /api/reservations` |
| validate | [`ReservationPolicy`](src/main/java/cz/swi/parking/domain/ReservationPolicy.java) + bean validation on [`CreateReservationRequest`](src/main/java/cz/swi/parking/web/dto/CreateReservationRequest.java) |
| persist | [`ReservationService.create`](src/main/java/cz/swi/parking/service/ReservationService.java) → [`ReservationRepository`](src/main/java/cz/swi/parking/repo/ReservationRepository.java) → PostgreSQL, schema owned by [Flyway](src/main/resources/db/migration) |
| return ID | `201 Created`, `Location: /api/reservations/{id}` |
| automated check | [`ReservationApiIT`](src/test/java/cz/swi/parking/api/ReservationApiIT.java) walks the path over HTTP; [`ReservationPersistenceSpikeIT`](src/test/java/cz/swi/parking/spike/ReservationPersistenceSpikeIT.java) proves the database round trip. Both run by `./scripts/integration-tests.sh` |

Status after C01: every step above already runs. What is **not** finished is authentication, the
scheduled DRAFT expiry, and the concurrency fix identified by the C01 spike
(see [docs/evidence-and-evolution.md](docs/evidence-and-evolution.md)).

---

## What is reserved

| Mandatory element | In this system |
|---|---|
| **Resource** | `ParkingSpot` — one physical spot, e.g. `P1-A01`, with a type (`STANDARD`, `EV_CHARGING`, `ACCESSIBLE`, `MOTORCYCLE`) and a status (`ACTIVE`, `OUT_OF_SERVICE`) |
| **Reservation** | `Reservation` — UUID identity, a half-open window `[startsAt, endsAt)`, a state, the vehicle and the driver |
| **User** | `AppUser` — `DRIVER`, `OPERATOR` or `ADMIN`, optionally holding an accessibility permit |
| **States** | `DRAFT → CONFIRMED → COMPLETED`, with `CANCELLED` reachable from `DRAFT` and `CONFIRMED` |
| **Operations** | create, confirm, cancel, check availability (plus complete) |
| **Common rule** | two `CONFIRMED` reservations of the same spot must not overlap |
| **Domain rule** | spot/vehicle compatibility (see below) |
| **Boundary** | Notification Service |

### Common business rule

> Two **CONFIRMED** reservations for the same parking spot must not overlap.

Windows are half-open: a reservation `10:00–12:00` and one `12:00–14:00` do **not** overlap.
`DRAFT` reservations hold nothing, so several drafts may overlap freely; the rule is enforced at
confirm time, against the database.

### Domain-specific business rule — spot/vehicle compatibility

> A reservation is only valid when the vehicle fits the spot's type, and an `ACCESSIBLE` spot
> additionally requires that the driver holds an accessibility permit valid on the day the
> reservation starts.

| Spot type | Accepts | Extra condition |
|---|---|---|
| `STANDARD` | `COMBUSTION`, `ELECTRIC` | — |
| `EV_CHARGING` | `ELECTRIC` only | a combustion car would occupy a charger it cannot use |
| `ACCESSIBLE` | `COMBUSTION`, `ELECTRIC` | driver's permit must be valid on `startsAt` |
| `MOTORCYCLE` | `MOTORCYCLE` only | — |

The rule is applied twice: when a reservation is created, when it is confirmed — and it also filters
the availability search, so a driver is never shown a spot that would be refused a moment later.

---

## Stack and why

| Choice | Why |
|---|---|
| **Java 21** (LTS) | The stack the course supports. `pom.xml` compiles with `--release 21`, so a newer JDK on someone's laptop still produces Java 21 bytecode. |
| **Spring Boot 3.5** | REST, transactions, dependency injection and test support out of one box; nobody on the team has to maintain plumbing. |
| **Maven + Maven Wrapper** | `./mvnw` pins the build tool, so a clean checkout needs only a JDK. |
| **PostgreSQL 16** | The overlap rule is fundamentally a *range* question. PostgreSQL can enforce it in the database itself (`tstzrange` + an exclusion constraint), which is exactly the escape hatch our chosen future pressure will need. |
| **Flyway** | The schema is versioned code, and `ddl-auto: validate` keeps Hibernate from quietly inventing a different one. |
| **JUnit 5 + AssertJ + Mockito** | Ships with `spring-boot-starter-test`. |

Rejected: an in-memory H2 for tests. The spike question was explicitly about *real* database
behaviour (timestamps with time zone, transaction isolation); H2 would have answered a different
question. See [docs/architecture-and-decisions.md](docs/architecture-and-decisions.md).

---

## Prerequisites

- **JDK 21** — a full JDK, not just a JRE. Check with `javac -version`.
  - Fedora/RHEL: `sudo dnf install java-21-openjdk-devel`
  - Debian/Ubuntu: `sudo apt install openjdk-21-jdk`
  - No root: unpack [Temurin 21](https://adoptium.net/temurin/releases/?version=21) and
    `export JAVA_HOME=/path/to/jdk-21`
- **podman** or **docker**, to run PostgreSQL locally.
- Nothing else: `./mvnw` downloads Maven and the dependencies on first run.

## Build and run

```bash
git clone <TODO_REPO_URL> && cd park-reservation

./mvnw test               # unit tests only, no database needed

./scripts/db-up.sh        # PostgreSQL 16 on localhost:55432 (db parkdb, user/password park/park)
./mvnw spring-boot:run    # Flyway creates the schema and seeds demo data
                          # web UI on http://localhost:8080, API under /api
./scripts/db-down.sh      # when you are done
```

Configuration is overridable by environment variable: `PARK_DB_URL`, `PARK_DB_USER`,
`PARK_DB_PASSWORD`, `PARK_PORT`, `PARK_NOTIFICATIONS_MODE` (`log` or `http`),
`PARK_NOTIFICATIONS_URL`.

## Web UI

Open **http://localhost:8080** once the application is running. It is a single static page
(`src/main/resources/static/index.html`, no build step, no framework) served from the same origin as
the API it calls.

The page is a plan view of level P1. Pick a time window, a vehicle type and who you are, and every
spot is colour-coded:

| Colour | Meaning |
|---|---|
| green | free for this window **and** legal for your vehicle and permit — click it to book |
| amber | free, but the compatibility rule would refuse you (`wrong vehicle`, `permit required`) |
| red | already held by a confirmed reservation |
| grey | out of service |

Clicking a green spot creates a **DRAFT**, which holds nothing. It appears on the right with a
**Confirm** button — pressing it is the `DRAFT → CONFIRMED` transition, and the moment the
no-overlap rule is enforced. Rejections surface as a toast naming the rule that refused
(`NO_OVERLAP`, `SPOT_VEHICLE_COMPATIBILITY`), so both business rules are visible without reading a
log.

Switching the driver between *Demo Driver* and *Permit Holder* is the quickest way to see the
domain-specific rule work: the accessible spot `P1-H01` turns from amber to green.

## Trying the API

The seed migration creates fixed demo IDs, so these commands work as written.

```bash
# Which spots are free tomorrow morning for an electric car?
curl -s "http://localhost:8080/api/availability?from=2026-09-15T08:00:00Z&to=2026-09-15T10:00:00Z&vehicleType=ELECTRIC"

# Create (DRAFT). userId 1111... is the demo driver, spotId bbbb...0001 is charger P1-E01.
curl -s -X POST http://localhost:8080/api/reservations \
  -H 'Content-Type: application/json' \
  -d '{
        "userId":      "11111111-1111-1111-1111-111111111111",
        "spotId":      "bbbbbbbb-0000-0000-0000-000000000001",
        "vehiclePlate":"1AB 2345",
        "vehicleType": "ELECTRIC",
        "startsAt":    "2026-09-15T08:00:00Z",
        "endsAt":      "2026-09-15T10:00:00Z"
      }'

RESERVATION_ID=<id from the response>

curl -s -X POST "http://localhost:8080/api/reservations/$RESERVATION_ID/confirm"   # DRAFT -> CONFIRMED
curl -s      "http://localhost:8080/api/reservations/$RESERVATION_ID"              # read it back
curl -s -X POST "http://localhost:8080/api/reservations/$RESERVATION_ID/cancel"    # -> CANCELLED
```

| Method | Path | Operation |
|---|---|---|
| `POST` | `/api/reservations` | **create** — validates and stores a `DRAFT`, returns `201` + id |
| `POST` | `/api/reservations/{id}/confirm` | **confirm** — `DRAFT → CONFIRMED`, enforces the overlap rule |
| `POST` | `/api/reservations/{id}/cancel` | **cancel** — `DRAFT`/`CONFIRMED → CANCELLED` |
| `POST` | `/api/reservations/{id}/complete` | `CONFIRMED → COMPLETED` once the window has passed |
| `GET` | `/api/reservations/{id}` | read one |
| `GET` | `/api/reservations?userId=...` | a driver's reservations |
| `GET` | `/api/availability?from=&to=[&spotType=][&vehicleType=][&userId=]` | **check availability** |
| `GET` | `/api/spots` | every spot in the car park |
| `GET` | `/api/users` | the people who can hold reservations (the UI's driver picker) |

Failures are reported as `400` (malformed request), `404` (unknown id) or `409` (a business rule
said no, with the rule name in the `rule` field).

## Tests

```bash
./mvnw test                      # 36 unit tests, no infrastructure needed
./scripts/integration-tests.sh   # 8 integration tests against a real PostgreSQL (API + persistence spike)
./scripts/spike-persistence.sh   # the C01 spike alone, capturing its output as evidence
```

Tests that need a database are named `*IT` and are excluded from `./mvnw test`, so a teammate with
no container runtime can still run the unit suite.

`scripts/spike-persistence.sh` appends the full run to
[`docs/evidence/spike-a-persistence-run.log`](docs/evidence/spike-a-persistence-run.log) and stops
the database afterwards, even if the tests fail.

## Repository layout

```
README.md
docs/
  intent-and-change.md            Project Frame, future pressure, the C01 change + review loop
  architecture-and-decisions.md   stack rationale and the decisions taken so far
  evidence-and-evolution.md       the C01 spike: question, what we did, result, decision
  reviews/C01-review.md           the review one member wrote before the change was integrated
  evidence/                       raw output of the spike run
scripts/                          db-up.sh, db-down.sh, integration-tests.sh, spike-persistence.sh
src/main/java/cz/swi/parking/
  domain/        entities, the state machine and ReservationPolicy (rules, no framework)
  repo/          Spring Data repositories, including the overlap query
  service/       the four core operations
  notification/  the external boundary and its stub
  web/           REST controllers, DTOs, error handling
src/main/resources/static/        index.html - the web UI, one self-contained file
src/main/resources/db/migration/  Flyway schema and demo seed
src/test/java/cz/swi/parking/
  domain/, service/  unit tests, no infrastructure
  api/               ReservationApiIT - the walking skeleton over HTTP (needs a database)
  spike/             the C01 persistence spike (needs a database)
```
