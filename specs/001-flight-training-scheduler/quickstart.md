# Quickstart: Flight Training Scheduler

## Prerequisites

- Java 21, Maven 3.9+ (via SDKMAN)
- Akka CLI 3.0+
- Anthropic or OpenAI API key (for FlightConditionsAgent)

## 1. Set Your LLM Key

```bash
# Pick one:
export ANTHROPIC_API_KEY="sk-ant-..."
# or
export OPENAI_API_KEY="sk-..."
```

## 2. Start Locally

```bash
# Terminal 1 — start the Akka local runtime
akka local start

# Terminal 2 — run the service
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn compile exec:java
```

Or via Akka CLI:
```bash
akka local run
```

## 3. Try It Out

### Mark three participants available for a slot

```bash
SLOT="2026-06-01T10:00:00Z"

# Student
curl -X POST http://localhost:9000/flight/availability/$SLOT \
  -H "Content-Type: application/json" \
  -d '{"participantId":"student-1","participantType":"STUDENT"}'

# Instructor
curl -X POST http://localhost:9000/flight/availability/$SLOT \
  -H "Content-Type: application/json" \
  -d '{"participantId":"instructor-1","participantType":"INSTRUCTOR"}'

# Aircraft
curl -X POST http://localhost:9000/flight/availability/$SLOT \
  -H "Content-Type: application/json" \
  -d '{"participantId":"aircraft-1","participantType":"AIRCRAFT"}'
```

### Check slot state

```bash
curl http://localhost:9000/flight/availability/$SLOT
```

### Book the slot

```bash
curl -X POST http://localhost:9000/flight/bookings/$SLOT \
  -H "Content-Type: application/json" \
  -d '{
    "studentId":    "student-1",
    "aircraftId":   "aircraft-1",
    "instructorId": "instructor-1",
    "bookingId":    "booking-abc-123"
  }'
```

Expected: `201 Created` (conditions acceptable) or `422` (conditions rejected with reason).

### Query slots by participant

```bash
# All BOOKED slots for student-1
curl http://localhost:9000/flight/slots/student-1/booked

# All AVAILABLE slots for instructor-1
curl http://localhost:9000/flight/slots/instructor-1/available
```

### Cancel a booking

```bash
curl -X DELETE http://localhost:9000/flight/bookings/$SLOT/booking-abc-123
```

## 4. Test Bad Conditions

Use a slotId containing "storm" or "bad" to trigger the agent's rejection path:

```bash
STORM_SLOT="2026-06-01T10:00:00Z-storm"
# Mark all three available, then book → expect 422 with conditions reason
```

## 5. Run Tests

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn test
```
