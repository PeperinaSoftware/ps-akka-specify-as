# Akka SDK Component Classes Overview

## 1. BookingWorkflow

**Purpose**: Orchestrates the flight training booking process
**Type**: Akka SDK Workflow component
**Key Features**:

* Manages the multi-step booking process
* Checks availability for students, instructors, and aircraft
* Creates reservations when all participants are available
* Handles booking state transitions

## 2. ReservationEntity

**Purpose**: Manages reservation state and lifecycle
**Type**: Akka SDK Event Sourced Entity component
**Key Features**:

* Handles reservation creation and cancellation
* Manages participant availability status
* Emits events for reservation state changes
* Maintains reservation history through event sourcing

## 3. ReservationToTimeSlotConsumer

**Purpose**: Processes reservation events and updates time slots
**Type**: Akka SDK Consumer component
**Key Features**:

* Consumes events from ReservationEntity
* Converts reservation events to time slot commands
* Handles participant time slot requests
* Manages time slot cancellations

## 4. TimeSlotToReservationConsumer

**Purpose**: Processes time slot events and updates reservations
**Type**: Akka SDK Consumer component
**Key Features**:

* Consumes events from TimeSlotEntity
* Converts time slot events to reservation commands
* Updates reservation status based on time slot availability
* Handles participant availability responses

## 5. TimeSlotView

**Purpose**: Provides read-only access to time slot availability data
**Type**: Akka SDK View component
**Key Features**:

* Maintains materialized view of time slot availability
* Processes time slot events to update view state
* Supports efficient querying of available time slots
* Enables filtering by participant type (student, instructor, aircraft)

## Component Interactions

### 1. Booking Flow

* BookingWorkflow initiates the process
* Queries TimeSlotView for availability
* Creates reservation through ReservationEntity
* ReservationToTimeSlotConsumer processes reservation events
* TimeSlotToReservationConsumer handles time slot responses

### 2. Event Flow

* ReservationEntity emits events
* ReservationToTimeSlotConsumer processes events and updates time slots
* TimeSlotEntity emits response events
* TimeSlotToReservationConsumer updates reservation status

### 3. State Management

* Each entity maintains its own state
* Events drive state transitions
* Consumers ensure consistency between entities
* Workflow coordinates the overall process
