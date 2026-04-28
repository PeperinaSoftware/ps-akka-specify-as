# Feature Specification: Flight Training Scheduler

**Feature Branch**: `001-flight-training-scheduler`
**Created**: 2026-04-28
**Status**: Draft
**Input**: Akka SDK certification project — wire up 6 components over pre-existing domain objects

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Mark Participant Availability (Priority: P1)

A student, instructor, or aircraft operator signals that they are available for a specific time slot, so the system can track which slots have all three participant types ready for booking.

**Why this priority**: Availability is the foundation of every booking. Without it no other operation is possible.

**Independent Test**: POST to the availability endpoint for a slot with a participant ID and type; then GET the slot state and confirm the participant appears in the available list.

**Acceptance Scenarios**:

1. **Given** a time slot has no participants, **When** a student marks themselves available, **Then** the system records the student as available for that slot and returns success.
2. **Given** a participant is already available for a slot, **When** the same participant tries to mark availability again, **Then** the system rejects the request with a clear error.
3. **Given** a participant is already booked for a slot, **When** they try to mark availability again, **Then** the system rejects the request.
4. **Given** a participant is marked available, **When** they unmark availability, **Then** the system removes them from the available list.

---

### User Story 2 — Book a Flight Training Slot (Priority: P1)

A dispatcher books a time slot for a complete crew (student + instructor + aircraft), provided all three are available, the slot is in the future, and flight conditions are acceptable.

**Why this priority**: Booking is the primary business transaction the system exists to support.

**Independent Test**: Mark a student, instructor, and aircraft available for the same future slot, then POST a booking request; confirm the booking is accepted and all three participants transition to booked status.

**Acceptance Scenarios**:

1. **Given** a student, instructor, and aircraft are all available for a future slot AND flight conditions are acceptable, **When** a booking is requested with all three IDs and a booking ID, **Then** the booking is confirmed and all three participants move from available to booked.
2. **Given** at least one of the three participants is not available for the slot, **When** a booking is requested, **Then** the system rejects the request with a descriptive error.
3. **Given** all three participants are available but the slot is in the past, **When** a booking is requested, **Then** the system rejects the request because slots must be future.
4. **Given** all three participants are available for a future slot but the flight conditions agent reports unacceptable conditions, **When** a booking is requested, **Then** the system rejects the request with the reason from the agent.
5. **Given** a slot is already fully booked, **When** a second booking is attempted for the same slot, **Then** the system prevents the double booking.

---

### User Story 3 — Cancel a Booking (Priority: P2)

A dispatcher cancels an existing booking, releasing all three participants back to an unbooked state for that slot.

**Why this priority**: Cancellations are necessary for operational flexibility but are less frequent than bookings.

**Independent Test**: Create a complete booking, then DELETE with the slot ID and booking ID; confirm all three participants are no longer in the booked list.

**Acceptance Scenarios**:

1. **Given** a confirmed booking exists for a slot, **When** the booking is canceled with the correct slot ID and booking ID, **Then** all three participants are removed from the booked list for that slot.
2. **Given** a non-existent booking ID is used, **When** a cancellation is requested, **Then** the system returns an appropriate error.

---

### User Story 4 — Query Slots by Participant and Status (Priority: P2)

A user looks up all slots in which a given participant has a specific status (available or booked), enabling scheduling dashboards and conflict checks.

**Why this priority**: Querying existing availability is necessary for schedulers to avoid conflicts but is a read-side operation that does not block the core booking flow.

**Independent Test**: Mark a participant available in two slots, then GET slots by participant ID filtered by status "available"; confirm both slots appear in the response.

**Acceptance Scenarios**:

1. **Given** a participant is available in multiple slots, **When** queried by participant ID and status "available", **Then** all matching slots are returned.
2. **Given** a participant has bookings in some slots and availability in others, **When** queried by status "booked", **Then** only the booked slots are returned.
3. **Given** no slots match the criteria, **When** queried, **Then** an empty list is returned.

---

### Edge Cases

- What happens when the flight conditions agent is unavailable or returns an error? The booking must be rejected with a clear reason rather than silently proceeding.
- What if two concurrent booking requests race for the same slot? Entity-level concurrency must guarantee only one booking succeeds and the other receives a conflict error.
- What if the slot ID does not correspond to any previous availability marks? All three participants must be in the available list or the booking is rejected.
- What if the participant type string in an API request is invalid? The endpoint must reject the request immediately with a bad-request error.
- What if a booking is canceled that does not exist? The system must return a clear not-found or no-op response without corrupting state.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow a participant (student, instructor, or aircraft) to mark themselves as available for a time slot identified by a slot ID.
- **FR-002**: The system MUST reject an availability mark if the participant is already available or already booked for that slot.
- **FR-003**: The system MUST allow a participant to unmark their availability for a slot, removing them from the available list.
- **FR-004**: The system MUST allow a booking to be created only when a student, an instructor, and an aircraft are all in the available list for the same slot.
- **FR-005**: The system MUST reject a booking request if the time slot is not in the future.
- **FR-006**: The system MUST consult the flight conditions agent before confirming any booking; if conditions are unacceptable the booking MUST be rejected with the agent's reason.
- **FR-007**: Upon a successful booking, the system MUST move all three participants from the available list to the booked list, emitting one event per participant.
- **FR-008**: The system MUST prevent double bookings for the same slot via transactional entity-level guarantees — no two successful bookings may exist for the same slot.
- **FR-009**: The system MUST allow an existing booking to be canceled by slot ID and booking ID, emitting one cancellation event per participant.
- **FR-010**: The system MUST maintain a per-participant-per-slot read model so that queries by participant ID and status are efficient and up-to-date.
- **FR-011**: The system MUST expose a REST API with the following operations:
  - POST `/flight/availability/{slotId}` — mark a participant available (body: participantId, participantType)
  - DELETE `/flight/availability/{slotId}` — unmark a participant available (body: participantId, participantType)
  - GET `/flight/availability/{slotId}` — retrieve the current slot availability state
  - POST `/flight/bookings/{slotId}` — create a booking (body: studentId, aircraftId, instructorId, bookingId)
  - DELETE `/flight/bookings/{slotId}/{bookingId}` — cancel a booking
  - GET `/flight/slots/{participantId}/{status}` — list slots for a participant by status
- **FR-012**: The flight conditions assessment MUST be performed by an AI agent using a tool function that returns both a pass/fail result and a human-readable reason, without exceeding free-tier LLM usage limits.
- **FR-013**: A consumer component MUST reactively propagate every availability and booking event from the booking entity to the corresponding per-participant entity without any direct HTTP exposure.
- **FR-014**: Domain objects (`Timeslot`, `BookingEvent`, `Participant`) MUST NOT be modified.

### Key Entities

- **Booking Slot**: Represents a schedulable time window; holds the full set of available participants and confirmed bookings. Identity is the slot ID.
- **Participant Slot**: A per-participant view of a single slot with a status (AVAILABLE or BOOKED). Identity is the composite `{slotId}-{participantId}`.
- **Booking**: A confirmed reservation grouping a student, instructor, and aircraft under a shared booking ID.
- **Participant**: A named actor with a type (STUDENT, INSTRUCTOR, AIRCRAFT) and a unique ID.
- **Conditions Report**: The output of the flight conditions assessment — a slot ID, a boolean acceptability flag, and a reason string.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A participant can mark availability and have it reflected in the slot state within 1 second under normal load.
- **SC-002**: A booking request that meets all preconditions is confirmed within 5 seconds end-to-end, including the flight conditions check.
- **SC-003**: Concurrent booking attempts for the same fully-available slot result in exactly one successful booking and at least one rejection — zero double bookings occur.
- **SC-004**: A query for slots by participant and status returns correct, up-to-date results within 2 seconds.
- **SC-005**: All REST endpoints return meaningful, human-readable error messages for invalid input (not stack traces).
- **SC-006**: The flight conditions agent returns an assessment for every booking attempt, including a human-readable reason when the booking is rejected.
- **SC-007**: All six Akka SDK components compile and the project's test suite passes without any modification to domain objects.

## Assumptions

- Slot IDs are externally generated opaque strings (e.g. UUIDs); the system treats the slot's scheduled date/time as encoded or derivable from the slot ID or as a separate concern — future-slot validation is enforced within the entity.
- The flight conditions agent uses a lightweight LLM (Anthropic claude-haiku or OpenAI gpt-4o-mini) to stay within free-tier usage limits; actual weather data may be mocked or fetched via a simple deterministic tool function.
- Authentication and authorization are out of scope for this certification project; all endpoints are publicly accessible.
- Cancellation does not automatically re-mark participants as available — they must explicitly call the availability endpoint again if needed.
- A `bookingId` is provided by the caller in the booking request (not server-generated), consistent with the existing `BookReservation` command signature.
- `ParticipantSlotEntity` is implemented as an event-sourced entity (matching the existing stub), consistent with the project's architectural pattern.
- The per-participant read model (view) is updated asynchronously via the consumer; slight eventual consistency is acceptable for query results.
