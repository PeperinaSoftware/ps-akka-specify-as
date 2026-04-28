# HTTP API Contract: Flight Training Scheduler

**Base path**: `/flight`  
**Auth**: None (public — certification project scope)  
**Content-Type**: `application/json`

---

## POST /flight/availability/{slotId}

Mark a participant as available for a time slot.

**Path params**: `slotId` — ISO-8601 datetime string identifying the slot (e.g. `2026-05-01T14:00:00Z`)

**Request body**:
```json
{
  "participantId":   "string (required) — unique participant ID",
  "participantType": "string (required) — one of: STUDENT, INSTRUCTOR, AIRCRAFT"
}
```

**Responses**:
| Status | Meaning |
|--------|---------|
| 200 OK | Availability marked successfully |
| 400 Bad Request | Invalid participantType value |
| 409 Conflict | Participant already available or booked for this slot |

---

## DELETE /flight/availability/{slotId}

Unmark a participant's availability for a time slot.

**Path params**: `slotId`

**Request body**:
```json
{
  "participantId":   "string (required)",
  "participantType": "string (required) — one of: STUDENT, INSTRUCTOR, AIRCRAFT"
}
```

**Responses**:
| Status | Meaning |
|--------|---------|
| 200 OK | Availability removed |
| 400 Bad Request | Invalid participantType |

---

## GET /flight/availability/{slotId}

Retrieve the current availability state for a slot.

**Path params**: `slotId`

**Response body** (200 OK):
```json
{
  "bookings": [
    {
      "participant": { "id": "string", "participantType": "STUDENT|INSTRUCTOR|AIRCRAFT" },
      "bookingId": "string"
    }
  ],
  "available": [
    { "id": "string", "participantType": "STUDENT|INSTRUCTOR|AIRCRAFT" }
  ]
}
```

---

## POST /flight/bookings/{slotId}

Book a flight training slot for a complete crew.

**Path params**: `slotId`

**Request body**:
```json
{
  "studentId":    "string (required)",
  "aircraftId":   "string (required)",
  "instructorId": "string (required)",
  "bookingId":    "string (required) — caller-supplied unique booking identifier"
}
```

**Responses**:
| Status | Meaning |
|--------|---------|
| 201 Created | Booking confirmed |
| 400 Bad Request | One or more participants not available, or slot is in the past |
| 422 Unprocessable Entity | Flight conditions unacceptable (body contains agent reason) |
| 409 Conflict | Slot already booked (double booking prevented) |

**Notes**:
- Flight conditions are checked via AI agent before the booking is committed.
- The entity guarantees at-most-one successful booking per slot via entity-level concurrency.

---

## DELETE /flight/bookings/{slotId}/{bookingId}

Cancel an existing booking.

**Path params**: `slotId`, `bookingId`

**Responses**:
| Status | Meaning |
|--------|---------|
| 200 OK | Booking canceled |
| 404 Not Found | Booking ID not found for this slot |

---

## GET /flight/slots/{participantId}/{status}

List all slots for a participant filtered by status.

**Path params**:
- `participantId` — participant ID
- `status` — `available` or `booked` (case-insensitive)

**Response body** (200 OK):
```json
{
  "slots": [
    {
      "slotId":          "string",
      "participantId":   "string",
      "participantType": "STUDENT|INSTRUCTOR|AIRCRAFT",
      "bookingId":       "string or null",
      "status":          "AVAILABLE|BOOKED"
    }
  ]
}
```

**Notes**:
- Results are sourced from the view (eventually consistent).
- Returns empty list when no matches found.
