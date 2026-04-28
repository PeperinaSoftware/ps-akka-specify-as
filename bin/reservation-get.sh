#!/bin/bash

# Check for required parameters
if [ "$#" -lt 1 ]; then
    echo "Usage: $0 [host] reservation_id"
    echo "  host           - Optional. Server host (default: localhost:9000)"
    echo "  reservation_id - Required. Unique reservation identifier"
    exit 1
fi

# Parse parameters based on argument count
if [ "$#" -eq 1 ]; then
    # No host provided, use default
    host="localhost:9000"
    reservationId="$1"
    urlScheme="http"
else
    # Host provided
    host="$1"
    reservationId="$2"
    urlScheme="https"
fi

curl -X GET "${urlScheme}://${host}/flight/reservation/${reservationId}" \
  -H "Content-Type: application/json"

echo # Add newline after curl output
