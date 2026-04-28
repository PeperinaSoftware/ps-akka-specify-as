# Domain Classes

The domain folder contains pre-implemented business logic classes that should not be modified:

## 1. Booking

**Purpose**: Represents the booking process state machine  
**Key Features**:

* Tracks booking workflow status
* Manages participant selection process
* Handles booking state transitions
* Coordinates reservation creation

## 2. Reservation

**Purpose**: Models flight training reservations  
**Key Features**:

* Defines reservation states and transitions
* Manages participant availability status
* Handles reservation creation/cancellation logic
* Maintains participant relationships

## 3. TimeSlot

**Purpose**: Manages time slot availability  
**Key Features**:

* Defines time slot states and transitions
* Handles participant scheduling
* Manages availability status
* Coordinates reservation assignments

## Important Notes

* Domain classes are provided as part of the certification process
* These classes contain the core business logic
* Classes should not be modified or changed
* Focus should be on implementing Akka SDK components that interact with these domain objects
* Domain objects handle validation and business rules
* Akka components should delegate business decisions to domain objects
