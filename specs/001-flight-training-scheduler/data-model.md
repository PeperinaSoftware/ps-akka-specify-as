# Data Model: Flight Training Scheduler

**Feature**: 001-flight-training-scheduler
**Date**: 2026-04-28

---

## Component 1: BookingSlotEntity

**Type**: EventSourcedEntity  
**Entity ID**: `slotId` (ISO-8601 datetime string, e.g. `"2026-05-01T14:00:00Z"`)  
**Package**: `io.example.application`

### State: `Timeslot` (existing domain object — do not modify)

```
Timeslot
├── bookings: Set<Booking>      // confirmed bookings, each has Participant + bookingId
└── available: Set<Participant> // participants awaiting booking
```

### Events: `BookingEvent` (existing sealed interface — do not modify)

| Event | Fields | Trigger |
|-------|--------|---------|
| `ParticipantMarkedAvailable` | slotId, participantId, participantType | markSlotAvailable command succeeds |
| `ParticipantUnmarkedAvailable` | slotId, participantId, participantType | unmarkSlotAvailable command succeeds |
| `ParticipantBooked` | slotId, participantId, participantType, bookingId | bookSlot command (1 per participant, 3 total) |
| `ParticipantCanceled` | slotId, participantId, participantType, bookingId | cancelBooking command (1 per participant, 3 total) |

### Commands

| Method | Command Type | Validates | Emits |
|--------|-------------|-----------|-------|
| `markSlotAvailable` | `Command.MarkSlotAvailable(Participant)` | not already available or booked | `ParticipantMarkedAvailable` |
| `unmarkSlotAvailable` | `Command.UnmarkSlotAvailable(Participant)` | participant is available | `ParticipantUnmarkedAvailable` |
| `bookSlot` | `Command.BookReservation(studentId, aircraftId, instructorId, bookingId)` | all 3 available; slot in future (`Instant.parse(entityId) > now`) | 3× `ParticipantBooked` |
| `cancelBooking` | `String bookingId` | booking exists | 3× `ParticipantCanceled` |
| `getSlot` | — | — | — (read-only) |

### applyEvent Mappings

| Event | Timeslot method called |
|-------|----------------------|
| `ParticipantMarkedAvailable` | `state.reserve(event)` |
| `ParticipantUnmarkedAvailable` | `state.unreserve(event)` |
| `ParticipantBooked` | `state.book(event)` |
| `ParticipantCanceled` | `state.cancelBooking(event.bookingId())` — idempotent; safe across 3 events |

---

## Component 2: ParticipantSlotEntity

**Type**: EventSourcedEntity  
**Entity ID**: `{slotId}-{participantId}` (composite — see consumer helper)  
**Package**: `io.example.application`

### State

```
State
├── slotId:          String
├── participantId:   String
├── participantType: ParticipantType (STUDENT | INSTRUCTOR | AIRCRAFT)
└── status:          String ("AVAILABLE" | "BOOKED")
```

### Events (own sealed interface)

| Event | @TypeName | Fields |
|-------|-----------|--------|
| `MarkedAvailable` | `"marked-available"` | slotId, participantId, participantType |
| `UnmarkedAvailable` | `"unmarked-available"` | slotId, participantId, participantType |
| `Booked` | `"participant-booked"` | slotId, participantId, participantType, bookingId |
| `Canceled` | `"participant-canceled"` | slotId, participantId, participantType, bookingId |

### Commands

| Method | Command Type | Emits |
|--------|-------------|-------|
| `markAvailable` | `Commands.MarkAvailable(slotId, participantId, participantType)` | `MarkedAvailable` |
| `unmarkAvailable` | `Commands.UnmarkAvailable(slotId, participantId, participantType)` | `UnmarkedAvailable` |
| `book` | `Commands.Book(slotId, participantId, participantType, bookingId)` | `Booked` |
| `cancel` | `Commands.Cancel(slotId, participantId, participantType, bookingId)` | `Canceled` |

### applyEvent Mappings

| Event | New State |
|-------|-----------|
| `MarkedAvailable` | `new State(slotId, participantId, participantType, "AVAILABLE")` |
| `UnmarkedAvailable` | `new State(slotId, participantId, participantType, null)` |
| `Booked` | `new State(slotId, participantId, participantType, "BOOKED")` |
| `Canceled` | `new State(slotId, participantId, participantType, null)` |

---

## Component 3: ParticipantSlotsView

**Type**: View  
**Package**: `io.example.application`  
**Subscribes to**: `ParticipantSlotEntity` events via `@Consume.FromEventSourcedEntity`

### Table Row

```
SlotRow
├── slotId:          String
├── participantId:   String
├── participantType: String
├── bookingId:       String (null when status is AVAILABLE)
└── status:          String ("AVAILABLE" | "BOOKED")
```

### TableUpdater Event Handling

| Event | View Action |
|-------|-------------|
| `MarkedAvailable` | `updateRow(new SlotRow(slotId, participantId, participantType.name(), null, "AVAILABLE"))` |
| `UnmarkedAvailable` | `deleteRow()` |
| `Booked` | `updateRow(new SlotRow(slotId, participantId, participantType.name(), bookingId, "BOOKED"))` |
| `Canceled` | `deleteRow()` |

### Queries

| Method | Query | Parameters |
|--------|-------|------------|
| `getSlotsByParticipant(participantId)` | `SELECT * FROM slots WHERE participantId = :participantId` | String |
| `getSlotsByParticipantAndStatus(input)` | `SELECT * FROM slots WHERE participantId = :participantId AND status = :status` | `ParticipantStatusInput(participantId, status)` |

---

## Component 4: SlotToParticipantConsumer

**Type**: Consumer  
**Package**: `io.example.application`  
**Subscribes to**: `BookingSlotEntity` events via `@Consume.FromEventSourcedEntity(BookingSlotEntity.class)`

### Event-to-Command Mapping

| BookingEvent | ParticipantSlotEntity Command | Via |
|-------------|-------------------------------|-----|
| `ParticipantMarkedAvailable` | `markAvailable(MarkAvailable)` | `componentClient.forEventSourcedEntity(participantSlotId).method(ParticipantSlotEntity::markAvailable)` |
| `ParticipantUnmarkedAvailable` | `unmarkAvailable(UnmarkAvailable)` | same pattern |
| `ParticipantBooked` | `book(Book)` | same pattern |
| `ParticipantCanceled` | `cancel(Cancel)` | same pattern |

Entity ID: derived via existing `participantSlotId(event)` helper: `{slotId}-{participantId}`

---

## Component 5: FlightConditionsAgent

**Type**: Agent  
**Package**: `io.example.application`  
**Model**: Environment-configured (Anthropic claude-haiku or OpenAI gpt-4o-mini)

### Response Type

```
ConditionsReport
├── timeSlotId:        String
└── meetsRequirements: Boolean
```

### Tool

| Method | @FunctionTool | Logic |
|--------|--------------|-------|
| `getWeatherForecast(timeSlotId)` | "Queries weather forecast for the time slot" | Returns deterministic mock: bad conditions if slotId contains "storm" or "bad", good otherwise |

### System Prompt

Must instruct the agent to:
1. Call `getWeatherForecast` with the given timeSlotId
2. Assess visibility, wind, precipitation based on the result
3. Return a `ConditionsReport` with `meetsRequirements` and a human-readable reason

---

## Component 6: FlightEndpoint

**Type**: HttpEndpoint  
**Package**: `io.example.api`  
**Base path**: `/flight`

### Request/Response Types (already in stub)

```
BookingRequest(studentId, aircraftId, instructorId, bookingId)
AvailabilityRequest(participantId, participantType)
```

### Orchestration for createBooking

```
1. componentClient.forAgent().inSession(slotId).method(FlightConditionsAgent::query).invoke(slotId)
2. if (!report.meetsRequirements()) → throw HttpException(422, reason)
3. componentClient.forEventSourcedEntity(slotId).method(BookingSlotEntity::bookSlot).invoke(cmd)
```

---

## State Transition Diagram

```
[No state]
    │
    ▼ markSlotAvailable
[AVAILABLE in BookingSlotEntity.available]
    │                        │
    ▼ bookSlot               ▼ unmarkSlotAvailable
[BOOKED in                [removed from available]
 BookingSlotEntity.bookings]
    │
    ▼ cancelBooking
[removed from bookings]
```

For ParticipantSlotEntity (read-model, driven by consumer):
```
[No row] → markAvailable → [status=AVAILABLE] → unmarkAvailable → [row deleted]
[status=AVAILABLE] → book → [status=BOOKED] → cancel → [row deleted]
```
