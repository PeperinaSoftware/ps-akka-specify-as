#!/bin/bash

# Check for optional host parameter
if [ "$#" -gt 1 ]; then
    echo "Usage: $0 [host]"
    echo "  host    - Optional. Server host (default: localhost:9000)"
    exit 1
fi

# Parse host parameter
if [ "$#" -eq 0 ]; then
    # No host provided, use default
    host="localhost:9000"
    urlScheme="http"
else
    # Host provided
    host="$1"
    urlScheme="https"
fi

curl "${urlScheme}://${host}/flight/time-slot-view-all" \
    -H "Content-Type: application/json"

echo # Add newline after curl output
