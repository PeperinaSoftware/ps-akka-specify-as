#!/bin/bash

# Check for required parameters
if [ "$#" -lt 1 ]; then
    echo "Usage: $0 [host] time_slot_id"
    echo "  host        - Optional. Server host (default: localhost:9000)"
    echo "  time_slot_id - Required. Unique time slot identifier"
    exit 1
fi

# Parse parameters based on argument count
if [ "$#" -eq 1 ]; then
    # No host provided, use default
    host="localhost:9000"
    timeSlotId="$1"
    urlScheme="http"
else
    # Host provided
    host="$1"
    timeSlotId="$2"
    urlScheme="https"
fi

curl -X GET "${urlScheme}://${host}/flight/time-slot/${timeSlotId}" \
  -H "Content-Type: application/json"

echo # Add newline after curl output
