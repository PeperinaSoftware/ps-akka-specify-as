# Tasks: Flight Training Scheduler

**Input**: Design documents from `/specs/001-flight-training-scheduler/`
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, contracts/ ✓

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no incomplete dependencies)
- **[Story]**: Maps to user story from spec.md (US1–US4)
- All paths are relative to repository root

---

## Phase 1: Setup

**Purpose**: Verify build environment and LLM configuration are ready before any implementation begins

- [x] T001 Verify Java 21 is used by Maven: confirm `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and run `mvn compile` to confirm clean build — fix any version mismatch in `pom.xml` if found
- [x] T002 [P] Set LLM API key environment variable (`ANTHROPIC_API_KEY` or `OPENAI_API_KEY`) and confirm `FlightConditionsAgent` compiles with the chosen provider in `src/main/java/io/example/application/FlightConditionsAgent.java`

**Checkpoint**: `mvn compile` passes with Java 21; LLM key is available in the shell environment

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Implement the pure state-transition methods that every command handler depends on — no business logic, no external calls, no ComponentClient

**⚠️ CRITICAL**: No user story work can begin until `applyEvent` is correct in both entities

- [x] T003 Implement `BookingSlotEntity.applyEvent(BookingEvent event)` in `src/main/java/io/example/application/BookingSlotEntity.java` — switch on all 4 event types: `ParticipantMarkedAvailable` → `state.reserve(event)`, `ParticipantUnmarkedAvailable` → `state.unreserve(event)`, `ParticipantBooked` → `state.book(event)`, `ParticipantCanceled` → `state.cancelBooking(event.bookingId())`
- [x] T004 [P] Implement `ParticipantSlotEntity.applyEvent(Event event)` in `src/main/java/io/example/application/ParticipantSlotEntity.java` — switch on all 4 event types: `MarkedAvailable` → `new State(slotId, participantId, participantType, "AVAILABLE")`, `UnmarkedAvailable` → `new State(slotId, participantId, participantType, null)`, `Booked` → `new State(slotId, participantId, participantType, "BOOKED")`, `Canceled` → `new State(slotId, participantId, participantType, null)`
- [x] T005 Write `BookingSlotEntityTest` applyEvent unit tests in `src/test/java/io/example/application/BookingSlotEntityTest.java` — use Akka SDK EventSourcedTestKit; assert state after applying each of the 4 event types; verify `reserve`, `unreserve`, `book`, and idempotent `cancelBooking` transitions
- [x] T006 [P] Write `ParticipantSlotEntityTest` applyEvent unit tests in `src/test/java/io/example/application/ParticipantSlotEntityTest.java` — use Akka SDK EventSourcedTestKit; assert `State.status` is `"AVAILABLE"` after `MarkedAvailable`, `"BOOKED"` after `Booked`, and `null` after `UnmarkedAvailable`/`Canceled`

**Checkpoint**: `mvn test` passes for entity applyEvent tests — all 4 state transitions verified in both entities

---

## Phase 3: User Story 1 — Mark Participant Availability (Priority: P1) 🎯 MVP

**Goal**: Participants can mark/unmark availability for time slots; dispatchers can inspect slot state via REST

**Independent Test**: POST `/flight/availability/2026-06-01T10:00:00Z` × 3 types → GET same slot → all 3 in `available` list; second POST for same participant → 4xx

- [x] T007 [US1] Implement `BookingSlotEntity.markSlotAvailable`, `unmarkSlotAvailable`, and `getSlot` in `src/main/java/io/example/application/BookingSlotEntity.java` — `markSlotAvailable`: check participant not already in `available` or `bookings` sets (use `Timeslot.isWaiting` + `findBooking`), emit `ParticipantMarkedAvailable`, reply `Done.done()`; `unmarkSlotAvailable`: emit `ParticipantUnmarkedAvailable`, reply `Done.done()`; `getSlot`: return `effects().reply(currentState())`
- [x] T008 [P] [US1] Implement `ParticipantSlotEntity.markAvailable` and `unmarkAvailable` in `src/main/java/io/example/application/ParticipantSlotEntity.java` — `markAvailable`: emit `Event.MarkedAvailable`, reply `Done.done()`; `unmarkAvailable`: emit `Event.UnmarkedAvailable`, reply `Done.done()`
- [x] T009 [P] [US1] Implement `SlotToParticipantConsumer.onEvent` handling for `ParticipantMarkedAvailable` and `ParticipantUnmarkedAvailable` in `src/main/java/io/example/application/SlotToParticipantConsumer.java` — use `participantSlotId(event)` helper (already implemented); call `ParticipantSlotEntity::markAvailable`/`unmarkAvailable` via `client.forEventSourcedEntity(id).method(...).invoke(cmd)`; return `effects().done()`
- [x] T010 [P] [US1] Implement `FlightEndpoint.markAvailable`, `unmarkAvailable`, and `getSlot` in `src/main/java/io/example/api/FlightEndpoint.java` — `markAvailable`: call `componentClient.forEventSourcedEntity(slotId).method(BookingSlotEntity::markSlotAvailable).invoke(new MarkSlotAvailable(new Participant(request.participantId(), participantType)))`; `unmarkAvailable`: same pattern with `unmarkSlotAvailable`; `getSlot`: call `BookingSlotEntity::getSlot`, return `Timeslot`
- [x] T011 [P] [US1] Add `BookingSlotEntityTest` command tests for `markSlotAvailable`/`unmarkSlotAvailable` in `src/test/java/io/example/application/BookingSlotEntityTest.java` — test: first mark → `ParticipantMarkedAvailable` emitted; duplicate mark on same participant → error reply; unmark after mark → `ParticipantUnmarkedAvailable` emitted

**Checkpoint**: `POST /flight/availability/{slotId}` and `GET /flight/availability/{slotId}` work end-to-end; duplicate mark returns error response

---

## Phase 4: User Story 2 — Book a Flight Training Slot (Priority: P1)

**Goal**: Booking is confirmed when all 3 participants available, slot is future, and flight conditions agent approves; double bookings prevented by entity concurrency

**Independent Test**: Mark 3 participants → POST `/flight/bookings/{slotId}` → 201 (good conditions slot) or 422 (slotId contains "storm"); missing participant → 4xx; past slotId → 4xx

- [x] T012 [US2] Implement `FlightConditionsAgent` system prompt, `query` method, and `getWeatherForecast` tool in `src/main/java/io/example/application/FlightConditionsAgent.java` — `SYSTEM_MESSAGE`: instruct agent to call `getWeatherForecast(timeSlotId)`, evaluate visibility ≥ 5 miles / wind ≤ 25 knots / no precipitation, return a JSON `ConditionsReport`; `query`: update `userMessage` to include slotId, use `.responseAs(ConditionsReport.class).thenReply()`; `getWeatherForecast`: return bad-conditions string if `timeSlotId.contains("storm") || timeSlotId.contains("bad")`, good-conditions string otherwise
- [x] T013 [US2] Implement `BookingSlotEntity.bookSlot(Command.BookReservation cmd)` in `src/main/java/io/example/application/BookingSlotEntity.java` — guard 1: `Instant.parse(entityId).isBefore(Instant.now())` → `effects().error("Slot is in the past")`; guard 2: `!currentState().isBookable(cmd.studentId(), cmd.aircraftId(), cmd.instructorId())` → `effects().error("Not all participants available")`; emit 3× `ParticipantBooked`: `new ParticipantBooked(entityId, cmd.studentId(), STUDENT, cmd.bookingId())`, `new ParticipantBooked(entityId, cmd.aircraftId(), AIRCRAFT, cmd.bookingId())`, `new ParticipantBooked(entityId, cmd.instructorId(), INSTRUCTOR, cmd.bookingId())`; use `effects().persist(e1, e2, e3).thenReply(Done.done())`
- [x] T014 [P] [US2] Implement `ParticipantSlotEntity.book` in `src/main/java/io/example/application/ParticipantSlotEntity.java` — emit `Event.Booked(slotId, participantId, participantType, bookingId)`, reply `Done.done()`
- [x] T015 [P] [US2] Extend `SlotToParticipantConsumer.onEvent` to handle `ParticipantBooked` in `src/main/java/io/example/application/SlotToParticipantConsumer.java` — call `ParticipantSlotEntity::book` via `client.forEventSourcedEntity(participantSlotId(event)).method(...).invoke(new Commands.Book(...))`; return `effects().done()`
- [x] T016 [US2] Implement `FlightEndpoint.createBooking` in `src/main/java/io/example/api/FlightEndpoint.java` — call `componentClient.forAgent().inSession(slotId).method(FlightConditionsAgent::query).invoke(slotId)`; if `!report.meetsRequirements()` throw `HttpException.badRequest("Flight conditions unacceptable: " + reason)`; call `componentClient.forEventSourcedEntity(slotId).method(BookingSlotEntity::bookSlot).invoke(new BookReservation(request.studentId(), request.aircraftId(), request.instructorId(), request.bookingId()))`; return `HttpResponses.created()`
- [x] T017 [P] [US2] Add `BookingSlotEntityTest` command tests for `bookSlot` in `src/test/java/io/example/application/BookingSlotEntityTest.java` — test: all 3 available + future slotId → 3 `ParticipantBooked` events emitted; missing participant → error; past slotId (e.g. `"2020-01-01T00:00:00Z"`) → error
- [x] T018 [P] [US2] Write `FlightConditionsAgentTest` in `src/test/java/io/example/application/FlightConditionsAgentTest.java` — use Akka SDK AgentTestKit or mock `getWeatherForecast`; verify `ConditionsReport.meetsRequirements()` is `true` for a normal slotId and `false` for a slotId containing "storm"

**Checkpoint**: Full booking flow works end-to-end; agent conditions gate is enforced; entity rejects past slots and missing participants

---

## Phase 5: User Story 3 — Cancel a Booking (Priority: P2)

**Goal**: A confirmed booking can be canceled by slot ID + booking ID; all 3 participants removed from bookings list

**Independent Test**: Complete a booking from US2 → DELETE `/flight/bookings/{slotId}/{bookingId}` → 200 OK; GET `/flight/availability/{slotId}` → `bookings` list is empty

- [x] T019 [US3] Implement `BookingSlotEntity.cancelBooking(String bookingId)` in `src/main/java/io/example/application/BookingSlotEntity.java` — guard: `currentState().findBooking(bookingId).isEmpty()` → `effects().error("Booking not found")`; emit one `ParticipantCanceled` per entry returned by `findBooking(bookingId)` (3 entries); use `effects().persist(events).thenReply(Done.done())`
- [x] T020 [P] [US3] Implement `ParticipantSlotEntity.cancel` in `src/main/java/io/example/application/ParticipantSlotEntity.java` — emit `Event.Canceled(slotId, participantId, participantType, bookingId)`, reply `Done.done()`
- [x] T021 [P] [US3] Extend `SlotToParticipantConsumer.onEvent` to handle `ParticipantCanceled` in `src/main/java/io/example/application/SlotToParticipantConsumer.java` — call `ParticipantSlotEntity::cancel` via `client.forEventSourcedEntity(participantSlotId(event)).method(...).invoke(new Commands.Cancel(...))`; return `effects().done()`
- [x] T022 [P] [US3] Implement `FlightEndpoint.cancelBooking` in `src/main/java/io/example/api/FlightEndpoint.java` — call `componentClient.forEventSourcedEntity(slotId).method(BookingSlotEntity::cancelBooking).invoke(bookingId)`; return `HttpResponses.ok()`
- [x] T023 [P] [US3] Add `BookingSlotEntityTest` tests for `cancelBooking` in `src/test/java/io/example/application/BookingSlotEntityTest.java` — test: existing booking → 3 `ParticipantCanceled` events emitted; non-existent bookingId → error reply

**Checkpoint**: DELETE `/flight/bookings/{slotId}/{bookingId}` returns 200; slot state shows empty bookings after cancellation

---

## Phase 6: User Story 4 — Query Slots by Participant and Status (Priority: P2)

**Goal**: Per-participant slot queries powered by a reactive view fed through the consumer pipeline

**Independent Test**: Mark participant available in 2 slots → GET `/flight/slots/{participantId}/available` → 2 rows; after booking one → GET `.../booked` → 1 row

- [x] T024 [US4] Implement `ParticipantSlotsView.ParticipantSlotsViewUpdater.onEvent` in `src/main/java/io/example/application/ParticipantSlotsView.java` — switch on `ParticipantSlotEntity.Event` types: `MarkedAvailable` → `effects().updateRow(new SlotRow(e.slotId(), e.participantId(), e.participantType().name(), null, "AVAILABLE"))`; `UnmarkedAvailable` → `effects().deleteRow()`; `Booked` → `effects().updateRow(new SlotRow(e.slotId(), e.participantId(), e.participantType().name(), e.bookingId(), "BOOKED"))`; `Canceled` → `effects().deleteRow()`
- [x] T025 [US4] Add `@Query` annotations to `getSlotsByParticipant` and `getSlotsByParticipantAndStatus` in `src/main/java/io/example/application/ParticipantSlotsView.java` — `getSlotsByParticipant`: `@Query("SELECT * FROM slots WHERE participantId = :participantId")`; `getSlotsByParticipantAndStatus`: `@Query("SELECT * FROM slots WHERE participantId = :participantId AND status = :status")`
- [x] T026 [P] [US4] Implement `FlightEndpoint.slotsByStatus` in `src/main/java/io/example/api/FlightEndpoint.java` — call `componentClient.forView().method(ParticipantSlotsView::getSlotsByParticipantAndStatus).invoke(new ParticipantStatusInput(participantId, status.toUpperCase()))`; return the `SlotList`
- [x] T027 [P] [US4] Write `ParticipantSlotsViewTest` in `src/test/java/io/example/application/ParticipantSlotsViewTest.java` — use Akka SDK ViewTestKit; apply `MarkedAvailable` event → assert row exists with `status="AVAILABLE"`; apply `Booked` event → assert `status="BOOKED"` and `bookingId` is set; apply `UnmarkedAvailable` → assert row deleted

**Checkpoint**: GET `/flight/slots/{participantId}/{status}` returns correct rows; view updates reactively from entity events via consumer pipeline

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Full build verification, integration validation, and optional SDK upgrade

- [x] T028 Run full test suite: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn test` — fix any compilation errors or test failures across all 6 test classes before proceeding
- [x] T029 [P] Validate quickstart.md scenarios end-to-end: start service locally (`akka local run` or `mvn exec:java`) and execute all curl commands from `specs/001-flight-training-scheduler/quickstart.md`; verify 201 for happy-path booking and 422 for "storm" slot
- [x] T030 [P] Upgrade Akka SDK parent version from `3.5.6` → `3.5.18` in `pom.xml` and re-run `mvn compile` to verify no breaking API changes

**Checkpoint**: All 6 test classes pass, quickstart scenarios produce expected HTTP responses, build is clean

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — **BLOCKS all user stories**
- **Phases 3–6 (User Stories)**: All depend on Phase 2 completion
  - US1 (Phase 3) and US4 (Phase 6) are fully independent — can run in parallel
  - US2 (Phase 4) builds on US1 entity commands for meaningful end-to-end testing but entity/agent tasks are independent
  - US3 (Phase 5) entity tasks are independent; meaningful e2e test requires US2 to create a booking first
- **Phase 7 (Polish)**: Depends on all desired stories being complete

### User Story Dependencies

| Story | Can Start After | Depends On Stories |
|-------|----------------|-------------------|
| US1 (P1) | Phase 2 | None |
| US2 (P1) | Phase 2 | None at entity level; US1 for e2e testing |
| US3 (P2) | Phase 2 | None at entity level; US2 for e2e testing |
| US4 (P2) | Phase 2 | None — view is fully independent |

### Within Each User Story

1. Entity command handlers (T007, T013, T019, T024–T025) — no ComponentClient, implement first
2. Supporting entity (T008, T014, T020) — parallel with consumer/endpoint once entity done
3. Consumer extension (T009, T015, T021) — parallel with endpoint
4. Endpoint handler (T010, T016, T022, T026) — parallel with consumer
5. Tests (T011, T017–T018, T023, T027) — parallel with all of the above

### Parallel Opportunities

Within Phase 2: T003 + T004 (different files); T005 + T006 (different files)

Within Phase 3 (after T007): T008 + T009 + T010 + T011 all parallel

Within Phase 4 (after T013): T014 + T015 + T017 + T018 all parallel; T016 depends on T012 + T013

Within Phase 5 (after T019): T020 + T021 + T022 + T023 all parallel

Within Phase 6 (after T024 + T025): T026 + T027 parallel

---

## Parallel Example: User Story 1

```
# Step 1 — implement entity commands first (T007 blocks the rest):
Task T007: BookingSlotEntity.markSlotAvailable / unmarkSlotAvailable / getSlot

# Step 2 — once T007 is done, these 4 run in parallel:
Task T008: ParticipantSlotEntity.markAvailable / unmarkAvailable     (different file)
Task T009: SlotToParticipantConsumer.onEvent mark/unmark events      (different file)
Task T010: FlightEndpoint.markAvailable / unmarkAvailable / getSlot  (different file)
Task T011: BookingSlotEntityTest command tests                       (different file)
```

## Parallel Example: User Story 2

```
# Step 1 — agent + entity bookSlot (T012 and T013 can run in parallel):
Task T012: FlightConditionsAgent system prompt + query + tool        (different file)
Task T013: BookingSlotEntity.bookSlot                               (different file)

# Step 2 — once T012 + T013 are done, these run in parallel:
Task T014: ParticipantSlotEntity.book
Task T015: SlotToParticipantConsumer extend for ParticipantBooked
Task T016: FlightEndpoint.createBooking (depends on T012 + T013)
Task T017: BookingSlotEntityTest bookSlot tests
Task T018: FlightConditionsAgentTest
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2 Only)

1. Complete Phase 1 + 2 (build check + applyEvent)
2. Complete Phase 3 (US1 — availability marking)
3. **VALIDATE**: `POST/GET /flight/availability/{slotId}` work
4. Complete Phase 4 (US2 — booking + agent)
5. **VALIDATE**: Full booking flow with conditions gate works
6. Stop — core value delivered

### Incremental Delivery

| Milestone | Phases Complete | What Works |
|-----------|----------------|------------|
| Foundation | 1 + 2 | Build compiles, applyEvent correct |
| MVP | + Phase 3 | Availability marking + inspection |
| Bookable | + Phase 4 | Full booking flow with AI conditions gate |
| Cancelable | + Phase 5 | Cancellation works |
| Queryable | + Phase 6 | Per-participant slot queries |
| Certified | + Phase 7 | All tests pass, quickstart validated |

### Single Developer Strategy (Recommended for Certification)

Execute phases sequentially: Phase 1 → 2 → 3 → 4 → 5 → 6 → 7

Within each phase, tasks marked `[P]` can be done in any order (independent files). After T007 in Phase 3, complete T008–T011 in any order before moving to Phase 4.
