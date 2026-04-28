#!/bin/bash

# Check for required parameters
if [ "$#" -lt 7 ]; then
    echo "Usage: $0 [host] reservation_id student_time_slot_id instructor_time_slot_id aircraft_time_slot_id"
    echo "  host                  - Optional. Server host (default: localhost:9000)"
    echo "  reservation_id        - Required. Unique reservation identifier"
    echo "  student_id            - Required. Student identifier"
    echo "  student_time_slot_id  - Required. Student time slot identifier"
    echo "  instructor_id         - Required. Instructor identifier"
    echo "  instructor_time_slot_id - Required. Instructor time slot identifier" 
    echo "  aircraft_id           - Required. Aircraft identifier"
    echo "  aircraft_time_slot_id - Required. Aircraft time slot identifier"
    exit 1
fi

# Parse parameters based on argument count
if [ "$#" -eq 7 ]; then
    # No host provided, use default
    host="localhost:9000"
    reservationId="$1"
    studentId="$2"
    studentTimeSlotId="$3"
    instructorId="$4"
    instructorTimeSlotId="$5"
    aircraftId="$6"
    aircraftTimeSlotId="$7"
    urlScheme="http"
else
    # Host provided
    host="$1"
    reservationId="$2"
    studentId="$3"
    studentTimeSlotId="$4"
    instructorId="$5"
    instructorTimeSlotId="$6"
    aircraftId="$7"
    aircraftTimeSlotId="$8"
    urlScheme="https"
fi

# Create JSON body
json_body=$(cat <<EOF
{
  "reservationId": "${reservationId}",
  "studentId": "${studentId}",
  "studentTimeSlotId": "${studentTimeSlotId}",
  "instructorId": "${instructorId}",
  "instructorTimeSlotId": "${instructorTimeSlotId}",
  "aircraftId": "${aircraftId}",
  "aircraftTimeSlotId": "${aircraftTimeSlotId}",
  "reservationTime": "$(date -u +%Y-%m-%dT%H:%M:00Z)"
}
EOF
)

curl -X POST "${urlScheme}://${host}/flight/reservation" \
  -H "Content-Type: application/json" \
  -d "$json_body"

echo # Add newline after curl output
