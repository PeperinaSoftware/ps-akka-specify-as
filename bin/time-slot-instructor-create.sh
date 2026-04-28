#!/bin/bash

# Check for required parameters
if [ "$#" -lt 2 ]; then
    echo "Usage: $0 [host] time_slot_id instructor_id"
    echo "  host          - Optional. Server host (default: localhost:9000)"
    echo "  time_slot_id  - Required. Unique time slot identifier"
    echo "  instructor_id - Required. Instructor identifier"
    exit 1
fi

# Parse parameters based on argument count
if [ "$#" -eq 2 ]; then
    # No host provided, use default
    host="localhost:9000"
    timeSlotId="$1"
    instructorId="$2"
    urlScheme="http"
else
    # Host provided
    host="$1"
    timeSlotId="$2"
    instructorId="$3"
    urlScheme="https"
fi

# Create JSON body
json_body=$(cat <<EOF
{
  "timeSlotId": "${timeSlotId}",
  "participantId": "${instructorId}", 
  "participantType": "instructor",
  "startTime": "$(date -u +%Y-%m-%dT%H:%M:00Z)"
}
EOF
)

curl -X POST "${urlScheme}://${host}/flight/create-time-slot" \
  -H "Content-Type: application/json" \
  -d "$json_body"

echo # Add newline after curl output
