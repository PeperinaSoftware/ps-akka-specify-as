# Flight Training Scheduler - Test Results

## 1. Mark Availability - alice (student)
Request:
curl -v -H "Content-Type: application/json" -X POST -d '{"participantId": "alice", "participantType": "student"}' localhost:9000/flight/availability/2026-12-10-10

Response: HTTP/1.1 200 OK

## 2. Mark Availability - superplane (aircraft)
Request:
curl -v -H "Content-Type: application/json" -X POST -d '{"participantId": "superplane", "participantType": "aircraft"}' localhost:9000/flight/availability/2026-12-10-10

Response: HTTP/1.1 200 OK

## 3. Mark Availability - superteacher (instructor)
Request:
curl -v -H "Content-Type: application/json" -X POST -d '{"participantId": "superteacher", "participantType": "instructor"}' localhost:9000/flight/availability/2026-12-10-10

Response: HTTP/1.1 200 OK

## 4. GET Slot State
Request:
curl -H "Content-Type: application/json" localhost:9000/flight/availability/2026-12-10-10

Response: HTTP/1.1 200 OK
{"bookings":[],"available":[{"id":"superteacher","participantType":"INSTRUCTOR"},{"id":"alice","participantType":"STUDENT"},{"id":"superplane","participantType":"AIRCRAFT"}]}

## 5. GET Alice Available Slots
Request:
curl -v localhost:9000/flight/slots/alice/available

Response: HTTP/1.1 200 OK
{"slots":[{"slotId":"2026-12-10-10","participantId":"alice","participantType":"STUDENT","bookingId":"","status":"AVAILABLE"}]}

## 6. GET Superplane Available Slots
Request:
curl -v localhost:9000/flight/slots/superplane/available

Response: HTTP/1.1 200 OK
{"slots":[{"slotId":"2026-12-10-10","participantId":"superplane","participantType":"AIRCRAFT","bookingId":"","status":"AVAILABLE"}]}

## 7. Create Booking
Request:
curl -v -H "Content-Type: application/json" localhost:9000/flight/bookings/2026-12-10-10 -d '{"bookingId": "booking4", "aircraftId": "superplane", "instructorId": "superteacher", "studentId": "alice"}'

Response: HTTP/1.1 201 Created

## 8. GET Alice Booked Slots
Request:
curl -v localhost:9000/flight/slots/alice/booked

Response: HTTP/1.1 200 OK
{"slots":[{"slotId":"2026-12-10-10","participantId":"alice","participantType":"STUDENT","bookingId":"booking4","status":"BOOKED"}]}

## 9. Cancel Booking
Request:
curl -v -X DELETE -H "Content-Type: application/json" localhost:9000/flight/bookings/2026-12-10-10/booking4

Response: HTTP/1.1 200 OK

Server logs during cancel:
11:25:10.482 INFO i.e.a.SlotToParticipantConsumer - Canceling participant superteacher for slot 2026-12-10-10
11:25:10.495 INFO i.e.a.SlotToParticipantConsumer - Canceling participant superplane for slot 2026-12-10-10
11:25:10.506 INFO i.e.a.SlotToParticipantConsumer - Canceling participant alice for slot 2026-12-10-10

## 10. GET Slot State After Cancel
Request:
curl -H "Content-Type: application/json" localhost:9000/flight/availability/2026-12-10-10

Response: HTTP/1.1 200 OK
{"bookings":[],"available":[]}
