# C01 Engineering Spike

**Variant A — Persistence.** Reservation → real database → read back → verify.

Run it yourself: `./scripts/spike-persistence.sh`
Raw output of the run quoted below: [`docs/evidence/spike-a-persistence-run.log`](evidence/spike-a-persistence-run.log)
Test source: [`ReservationPersistenceSpikeIT`](../src/test/java/cz/swi/parking/spike/ReservationPersistenceSpikeIT.java)

## Question / unknown

Does a reservation created through our service really reach PostgreSQL and come back unchanged,
and — the part we were actually unsure about — **does our no-overlap rule still hold when the check
runs against a real database instead of against objects in memory?**

Three specific doubts:

1. **Mapping.** We store `OffsetDateTime` in `timestamptz` and enums as strings, with Flyway owning
   the schema and Hibernate set to `validate`. Do the mapping and the migration actually agree, or
   does the application only appear to work because tests never left memory?
2. **The rule.** `existsConfirmedOverlap` expresses the overlap rule as SQL with strict `<`/`>`
   comparisons. Does it really reject an overlapping window and really accept a merely touching one
   once PostgreSQL, not a Java `if`, evaluates it?
3. **Concurrency.** `confirm` reads ("is there an overlap?") and then writes, inside one
   transaction. If two drivers confirm the same spot at the same moment, does the check still hold?
   None of us could say with confidence what `READ COMMITTED` does here, and guessing about
   isolation levels is exactly what a spike is for.

## What we did

- Wrote `ReservationPersistenceSpikeIT`, a `@SpringBootTest` that talks to a **real PostgreSQL 16**
  — no H2, no mock, no in-memory substitute — with four cases:
  1. create a reservation through `ReservationService`, then read it back **in a new transaction**
     (so nothing can come from Hibernate's first-level cache) and compare every field, including the
     instants;
  2. confirm it and re-read the status in yet another transaction, to show the state change is
     durable and not just an in-memory object mutation;
  3. sequentially: confirm one reservation, try to confirm an overlapping one, then confirm a
     merely touching one;
  4. **observation:** two threads released by a `CountDownLatch` confirm two overlapping
     reservations of the same spot at the same instant; afterwards we count the `CONFIRMED` rows
     that PostgreSQL actually holds for that spot.
- Wrote `scripts/db-up.sh` / `db-down.sh` / `spike-persistence.sh` so the whole thing is one
  command: the script starts PostgreSQL in podman or docker, waits for `pg_isready`, runs the spike
  through the `spike` Maven profile, writes the full output to `docs/evidence/`, and removes the
  container afterwards even if the tests fail.
- Executed it on 2026-09-14 against `postgres:16-alpine` (server reported version 16.15) on
  Temurin JDK 21.0.12.1, Fedora, podman 5.8.4.

## Observed result

All four cases ran; the build passed.

```
Database version: 16.15

[SPIKE] round trip OK: id=6ac6cee4-bee2-457e-8495-962c3a204432 status=DRAFT spot=SPIKE-107b4b6e version=0
[SPIKE] state change persisted: id=2bf7a60d-0460-4b10-84db-1a4bd54506ed status=CONFIRMED
[SPIKE] sequential overlap rule OK on spot SPIKE-f38a5c7b: 3 reservations, 2 confirmed, 1 rejected
[SPIKE] concurrent confirms on spot SPIKE-19564b5e: accepted=2 rejected=0 -> OVERLAPPING CONFIRMED ROWS IN DB = 2 (1 = rule held, 2 = double booking)

Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

Point by point:

1. **The mapping works, and the migration is real.** Flyway created the schema, the `validate`
   check passed, and the reservation came back from a fresh transaction with every field intact.
   `timestamptz` preserves the *instant*, not the client's offset: a value sent as `+02:00` returns
   as the same moment in UTC, which is what our comparisons rely on. The `version` column came back
   as `0`, so optimistic locking is wired up.
2. **The overlap rule is correct sequentially.** On one spot: the first confirm succeeded, the
   overlapping confirm was rejected with `SpotNotAvailableException`, the touching window
   (`ends_at` of one equal to `starts_at` of the next) was accepted. Three reservations on the spot,
   two confirmed, one refused — exactly the intended half-open semantics.
3. **The rule does not survive concurrency, and we now have the number.** Two simultaneous
   confirms: `accepted=2, rejected=0`, and PostgreSQL ended up holding **2 overlapping `CONFIRMED`
   rows for the same spot**. The common business rule was broken by the system itself, with no error
   anywhere. The cause is plain once seen: under `READ COMMITTED`, each transaction's
   `existsConfirmedOverlap` runs before the other has committed, so both see an empty result and
   both write. `@Version` does not help — the two transactions update *different* rows, so there is
   nothing for the optimistic lock to collide on.

**It reproduces.** Member C ran the same spike on a different machine during review and got the
same numbers, and a re-run after the branch was integrated did too
([`spike-a-persistence-rerun-after-merge.log`](evidence/spike-a-persistence-rerun-after-merge.log):
`accepted=2 rejected=0 -> OVERLAPPING CONFIRMED ROWS IN DB = 2`). This is not a one-off timing
fluke on one laptop.

The unpleasant part is how quiet the failure is. Nothing logged a warning, no test went red; the
only way to see it was to ask the database how many rows it was holding.

## Decision / what changes because of the result

1. **The overlap rule moves into PostgreSQL.** The application check stays as a fast, friendly
   rejection path, but it stops being the guarantee. In C02 we add a migration with
   `btree_gist` and an exclusion constraint on the confirmed window:

   ```sql
   create extension if not exists btree_gist;
   alter table reservation add constraint reservation_no_overlap
       exclude using gist (
           spot_id with =,
           tstzrange(starts_at, ends_at, '[)') with &&
       ) where (status = 'CONFIRMED');
   ```

   The database then refuses the second insert whatever the interleaving, and `ReservationService`
   translates the resulting constraint violation into the same `409 SPOT_NOT_AVAILABLE` the
   application check already returns, so the API contract does not change. This is precisely why we
   kept PostgreSQL instead of taking H2 for tests — the escape hatch only exists because the
   database is real.
2. **The spike test becomes a regression test.** Case 4 currently only *records* what happens,
   because timing decides whether the race is hit and we refuse to write a flaky assertion. Once
   the constraint is in place it will be inverted into a real assertion — exactly one confirm
   succeeds, the other gets a `409` — and it stays in the suite as the guard for the fix.
3. **Integration tests run against a real database from now on.** The spike paid for itself in one
   run: a mocked or in-memory repository would have reported success in all four cases, including
   the broken one. Persistence and concurrency behaviour is only ever tested against PostgreSQL in
   this project. The cost we accept is that `./scripts/spike-persistence.sh` needs podman or docker;
   `./mvnw test` stays infrastructure-free so nobody is blocked from running the unit tests.
4. **It confirmed our choice of future pressure.** We had picked Q (10× concurrent reservations) on
   intuition. The spike turned it into a measured fact: the system does not merely slow down under
   concurrency, it silently breaks its central promise. The pressure now has a concrete first
   milestone — re-run case 4 with the constraint in place, then with 10× concurrent confirms, and
   measure the rejection rate rather than guess it.
5. **One thing we will not do yet.** We are not adding a distributed lock or a queue in front of
   confirm. A single database constraint solves the correctness problem at this scale, and until we
   have the load numbers named in our Unknown, anything larger would be a guess.
