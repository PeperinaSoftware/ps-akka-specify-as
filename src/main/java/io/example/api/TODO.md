# Flight Training Scheduler REST API Endpoint

## Base Path: `/flight`

## Description

The ReservationEndpoint serves as the main HTTP interface for a flight training scheduling system.
It manages bookings, reservations, and time slots for flight training sessions between students,
instructors, and aircraft.

## Key Features

### 1. Booking Management

- Start new booking workflows
- Handle reservation creation and cancellation
- Track reservation status

### 2. Time Slot Management

- Make time slots available/unavailable
- Query time slots by participant type and time range
- View all time slots

### 3. Implementation Details

- Uses Akka's event-sourced architecture
- Handles asynchronous operations via CompletionStage
- Includes comprehensive logging
- Secured with internet-accessible ACL
- Implements workflow-based booking process

## Endpoints

### 1. Booking

- `POST /booking` - Start a new booking workflow

### 2. Reservations

- `POST /reservation` - Create a new reservation
- `PUT /reservation-cancel` - Cancel an existing reservation
- `GET /reservation/{entityId}` - Get reservation details

### 3. Time Slots

- `POST /make-time-slot-available` - Make a time slot available
- `PUT /make-time-slot-unavailable` - Make a time slot unavailable
- `GET /time-slot/{entityId}` - Get time slot details
- `GET /time-slot-view-all` - View all time slots

### 4. Time Slot Queries

- `POST /time-slot-view-by-type-and-time-range` - Query by participant type and time range
- `POST /time-slot-view-by-participant-and-time-range` - Query by participant and time range
