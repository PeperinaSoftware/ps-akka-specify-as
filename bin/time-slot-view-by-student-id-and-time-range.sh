#!/bin/bash

# Check for required and optional parameters
if [ "$#" -lt 3 ] || [ "$#" -gt 4 ]; then
    echo "Usage: $0 <studentId> <startTime> <endTime> [host]"
    echo "  studentId - Required. The ID of the student"
    echo "  startTime - Required. Start time in ISO format (e.g., 2024-03-20T00:00:00Z)"
    echo "  endTime   - Required. End time in ISO format (e.g., 2024-03-20T23:59:59Z)"
    echo "  host      - Optional. Server host (default: localhost:9000)"
    exit 1
fi

# Store required parameters
studentId="$1"
startTime="$2"
endTime="$3"

# Parse host parameter
if [ "$#" -eq 3 ]; then
    # No host provided, use default
    host="localhost:9000"
    urlScheme="http"
else
    # Host provided
    host="$4"
    urlScheme="https"
fi

# Create JSON body
json_body=$(cat <<EOF
{
  "participantId": "$studentId",
  "participantType": "student",
  "timeBegin": "$startTime",
  "timeEnd": "$endTime"
}
EOF
)

curl -X POST "${urlScheme}://${host}/flight/time-slot-view-by-participant-and-time-range" \
  -H "Content-Type: application/json" \
  -d "$json_body"

echo # Add newline after curl output