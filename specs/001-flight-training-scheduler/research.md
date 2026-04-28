# Research: Flight Training Scheduler

**Feature**: 001-flight-training-scheduler
**Date**: 2026-04-28
**Status**: Complete — all unknowns resolved

---

## Decision 1: Agent-From-Entity — Endpoint Orchestrates

**Question**: The spec says "call the flight conditions agent inside the BookSlot command handler." Is that possible?

**Finding**: `ComponentClient` (the Akka SDK mechanism for inter-component calls) is only injectable in Endpoints, Workflows, Consumers, Agents, Timed Actions, and Service Setup. It is explicitly **not available** in Event Sourced Entities or Key Value Entities (confirmed in `akka-context/sdk/component-and-service-calls.html.md` line 62 and `setup-and-dependency-injection.html.md`).

**Decision**: `FlightEndpoint.createBooking` orchestrates in this order:
1. Call `FlightConditionsAgent.query(slotId)` via `ComponentClient`.
2. If `ConditionsReport.meetsRequirements == false` → return HTTP 422 with the agent's reason.
3. If conditions pass → call `BookingSlotEntity.bookSlot(BookReservation)`.

This matches the existing endpoint stub comment: *"Make sure to get a flight conditions report from the AI agent and use that to decide if the booking can be created."*

**Alternatives considered**:
- Workflow orchestration (agent step → entity step) — adds complexity not needed for a single synchronous booking flow; no durable retry requirement. Rejected for YAGNI.
- Passing `ConditionsReport` into the entity command — would require adding a field to `BookReservation`, violating the no-domain-modification constraint. Rejected.

---

## Decision 2: Future-Slot Validation — SlotId Encodes Date/Time

**Question**: `BookingSlotEntity` must reject bookings for past slots, but `Timeslot` has no date field and `BookReservation` carries no timestamp. How does the entity know the slot time?

**Finding**: The `Timeslot` domain object and all `BookingEvent` types contain only `slotId` as the time reference. No date fields are present and domain objects cannot be modified.

**Decision**: The `slotId` is treated as an ISO-8601 datetime string (e.g. `"2026-05-01T14:00:00Z"`). `BookingSlotEntity.bookSlot` attempts `Instant.parse(entityId)` and compares against `Instant.now()`. If parsing fails, the booking is rejected with a descriptive error. This is consistent with externally generated opaque IDs that embed time.

**Alternatives considered**:
- Validate in endpoint only — entity could still be called directly; no protection at entity level. Rejected.
- Add date to `BookReservation` command — violates no-domain-modification constraint. Rejected.

---

## Decision 3: ParticipantSlotEntity — Event-Sourced (Not Key-Value)

**Question**: The feature description called for a `KeyValueEntity`. The existing stub is an `EventSourcedEntity`. Which is correct?

**Finding**: The existing `ParticipantSlotEntity` stub explicitly extends `EventSourcedEntity<State, Event>` and defines its own `Event` sealed interface with `@TypeName` annotations. The `ParticipantSlotsView` subscribes to `ParticipantSlotEntity.class` as an event-sourced source (`@Consume.FromEventSourcedEntity`).

**Decision**: Implement `ParticipantSlotEntity` as an `EventSourcedEntity` exactly as stubbed. The view's `TableUpdater` receives `ParticipantSlotEntity.Event` events, not state snapshots.

---

## Decision 4: View Delete Strategy — deleteRow on Unmark/Cancel

**Question**: When a participant unmarks availability or a booking is canceled, should the view row be deleted or status-updated?

**Decision**: 
- `UnmarkedAvailable` → `effects().deleteRow()` — the slot-participant relationship no longer exists.
- `Canceled` → `effects().deleteRow()` — same rationale.
- `MarkedAvailable` → `effects().updateRow(new SlotRow(..., "AVAILABLE"))`.
- `Booked` → `effects().updateRow(new SlotRow(..., "BOOKED"))`.

This keeps the view lean and makes "no row" semantically equivalent to "no relationship."

---

## Decision 5: LLM Provider — Environment-Configured, Haiku/GPT-4o-mini

**Decision**: `FlightConditionsAgent` uses the default model provider configured via environment variable (`ANTHROPIC_API_KEY` → Anthropic claude-haiku, `OPENAI_API_KEY` → gpt-4o-mini). The system prompt encodes weather criteria directly so the agent does NOT make external HTTP calls — the `@FunctionTool getWeatherForecast` returns deterministic mock data based on the slotId string (simulate good conditions for most IDs, bad for IDs containing "storm" or "bad").

**Rationale**: Stays within free tier; avoids external weather API dependencies; still demonstrates the agent tool-calling pattern.

---

## Decision 6: SlotToParticipantConsumer — One-Handler Pattern

**Decision**: The consumer uses a single `onEvent(BookingEvent event)` handler with a `switch` expression to dispatch all four event types. Each case calls the appropriate `ParticipantSlotEntity` command via `ComponentClient` using the composite `participantSlotId` helper already present in the stub.

---

## Decision 7: cancelBooking applyEvent — Idempotent cancelBooking Call

**Finding**: `Timeslot.cancelBooking(bookingId)` removes all bookings with the given `bookingId` by filtering. When 3 `ParticipantCanceled` events are applied sequentially, the first call removes all 3 booking entries; subsequent calls are no-ops (filter finds nothing to remove).

**Decision**: `BookingSlotEntity.applyEvent` for `ParticipantCanceled` calls `currentState().cancelBooking(event.bookingId())`. This is safe and idempotent. Domain object is not modified.
