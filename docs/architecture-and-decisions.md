# Architecture and decisions

## Shape of the system

A single Spring Boot service with a PostgreSQL database and one external dependency.

```
        HTTP (JSON)
            │
   ┌────────▼─────────┐
   │  web             │  ReservationController, AvailabilityController,
   │                  │  DTOs, GlobalExceptionHandler (400 / 404 / 409)
   └────────┬─────────┘
            │
   ┌────────▼─────────┐        ┌──────────────────────────┐
   │  service         │───────▶│  Notification Service    │  ← the system boundary
   │                  │        │  log stub | HTTP client  │     (unreliable by assumption)
   │ ReservationSvc   │        └──────────────────────────┘
   │ AvailabilitySvc  │
   └────┬────────┬────┘
        │        │
 ┌──────▼──┐  ┌──▼───────────────┐
 │ domain  │  │  repo            │
 │         │  │  Spring Data JPA │
 │ entities│  └──┬───────────────┘
 │ states  │     │
 │ policy  │  ┌──▼──────────────┐
 └─────────┘  │  PostgreSQL 16  │  schema owned by Flyway, Hibernate = validate
              └─────────────────┘
```

Four layers, and the rule that keeps them honest: **`domain` knows nothing about Spring, JPA
annotations aside, and nothing about HTTP.** `ReservationPolicy` and the `ReservationStatus` state
machine are plain Java and are tested without starting a context — which is why 36 unit tests run in
about two seconds with no database.

## Decisions

### D1 — The resource is an individual spot, not a capacity pool

Reserving "a spot in P1" would let us count rather than schedule, and would make the overlap rule
trivial. We reserve `P1-A01`. That makes the resource carry properties (charger, accessible width),
which is what our domain-specific rule is built on, and it makes the promise to the driver concrete.

### D2 — DRAFT holds nothing; CONFIRMED holds the spot

Several drafts may overlap on the same spot. Only `CONFIRMED` reservations collide. This keeps
create cheap and unambiguous, and gives the system exactly one moment where a promise is made —
`confirm` — which is the only place the overlap rule has to be enforced. The price is that a driver
can create a draft that later cannot be confirmed; we consider that better than pretending a draft
is a reservation.

### D3 — Half-open time windows `[startsAt, endsAt)`

A reservation ending at 12:00 and one starting at 12:00 do not overlap. Closed intervals would
force an arbitrary "minimum gap" and endless off-by-one arguments. Half-open intervals are also
exactly what PostgreSQL's `tstzrange(…, '[)')` expresses, which matters for the fix in D8.

### D4 — Business rules live in `ReservationPolicy`, not in the controller or the entity

The window rules and the compatibility rule are static methods on a framework-free class. They are
called from `create` and again from `confirm`, because the world moves between the two: a permit can
expire, a spot can go out of service. Re-validating is cheap and removes a class of bug we would
otherwise have to reason about.

### D5 — PostgreSQL, and a real one in tests

Rejected: H2 or an in-memory repository for the integration test. The questions we needed answered
— does `timestamptz` round-trip, does the SQL overlap predicate behave, what does `READ COMMITTED`
do to a read-then-write check — are questions *about PostgreSQL*. An in-memory substitute would
have answered all three with a confident, wrong yes. The C01 spike proved this: case 4 passes on
any mock and fails on the real thing. See [evidence-and-evolution.md](evidence-and-evolution.md).

### D6 — Flyway owns the schema, Hibernate validates it

`ddl-auto: validate`. The schema is reviewable, versioned code, and a mapping that drifts from the
migration fails at startup rather than in production. The demo seed is a separate migration (`V2`)
so it can be dropped from a real deployment without touching `V1`.

### D7 — A failing Notification Service must not fail a reservation

The boundary is called after the state change, inside the transaction, and every exception is
caught and logged. A reservation that exists but was not announced is a recoverable annoyance; a
driver whose confirmed reservation vanished because a notification timed out is a broken promise.
The HTTP client carries explicit 2 s connect / 3 s read timeouts so a hung service cannot hold a
database transaction open indefinitely.

Known limitation, to revisit: the notification is sent *inside* the transaction, so a rollback after
the call would produce a message about a reservation that does not exist. Moving the call to an
after-commit event is small and planned; it was not needed to answer the C01 question.

### D8 — The overlap rule will move into the database (C02)

Decided by the C01 spike, not in advance: the application-level check is correct sequentially and
silently wrong under concurrent confirms. C02 adds a `btree_gist` exclusion constraint over
`(spot_id, tstzrange(starts_at, ends_at, '[)'))` filtered on `status = 'CONFIRMED'`. The application
check stays as the fast, friendly path; the constraint becomes the guarantee.

### D9 — Availability answers must obey the same rules as confirm

Raised in review of the C01 branch. An availability endpoint that offers a spot `confirm` would
refuse is worse than no endpoint: it teaches drivers the system lies. Both halves of the
domain-specific rule now filter the answer — the vehicle type always, the accessibility permit when
the caller identifies the driver with `userId`. An anonymous query hides nothing, deliberately, and
a test pins that.

### D10 — Time is injected

Everything reads "now" from a `Clock` bean. Rules that depend on time (no reservations in the past,
permit validity, completing only after the window) are then testable with a fixed clock instead of
`Thread.sleep`. The application runs on `Clock.systemUTC()`; instants are stored in UTC, and
formatting to a local time zone is a presentation concern we have not needed yet.

### D11 — `open-in-view` stays off, and reads fetch what they need

Spring's open-session-in-view is disabled, so a transaction is closed before a controller turns an
entity into a DTO. That is the behaviour we want — it keeps database access inside the service layer
instead of letting it leak into serialization — but it has to be paid for: every read of a
reservation declares an `@EntityGraph` over `spot` and `user`.

We learned this the direct way. Without the graphs, `GET /api/reservations/{id}`, the by-driver list
and `complete` returned HTTP 500 with `LazyInitializationException`, while `create`, `confirm` and
`cancel` worked — those happen to touch the associations while the session is still open, so the
bug was invisible from the operations we had been exercising. `ReservationApiIT` now walks the whole
path over HTTP and asserts on `spotCode` in the response, which is exactly the field that used to
blow up.

The alternative — turning `open-in-view` back on — would have hidden the problem rather than fixed
it, at the cost of unpredictable queries during serialization.

## Open points

- **Authentication and authorisation.** Every endpoint currently trusts the `userId` it is given.
  Nothing stops one driver cancelling another's reservation. Deliberately out of scope for C01;
  it has to land before anything resembling a release.
- **DRAFT lifetime.** Drafts are never cleaned up. They hold no spot, so they are harmless, but the
  table grows. A scheduled expiry (`DRAFT` older than *n* hours → `CANCELLED`) is the obvious fix.
- **Notification delivery guarantees.** Today: best effort, logged on failure. No retry, no outbox.
  Acceptable while the boundary is a stub.
- **Spot inventory management.** `OUT_OF_SERVICE` exists on the entity and is honoured by create and
  by availability, but no endpoint sets it yet. Note that taking a spot out of service does not
  cancel reservations already confirmed on it — that is a decision we have not made.
