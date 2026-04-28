#!/bin/bash

# Check for required and optional parameters
if [ "$#" -lt 3 ] || [ "$#" -gt 4 ]; then
    echo "Usage: $0 <type> <startTime> <endTime> [host]"
    echo "  type      - Required. The type of time slot"
    echo "  startTime - Required. Start time in ISO format (e.g., 2024-03-20T00:00:00Z)"
    echo "  endTime   - Required. End time in ISO format (e.g., 2024-03-20T23:59:59Z)"
    echo "  host      - Optional. Server host (default: localhost:9000)"
    exit 1
fi

# Store required parameters
type="$1"
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
  "type": "$type",
  "startTime": "$startTime",
  "endTime": "$endTime"
}
EOF
)

curl -X POST "${urlScheme}://${host}/flight/time-slot-view-by-type-and-time-range" \
  -H "Content-Type: application/json" \
  -d "$json_body"

echo # Add newline after curl output
