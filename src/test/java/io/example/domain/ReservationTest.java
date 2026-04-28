package io.example.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ReservationTest {
  private static final String reservationId = Reservation.generateReservationId();
  private static final String studentId = "student-1";
  private static final String studentTimeSlotId = "student-time-slot-1";
  private static final String instructorId = "instructor-1";
  private static final String instructorTimeSlotId = "instructor-time-slot-1";
  private static final String aircraftId = "aircraft-1";
  private static final String aircraftTimeSlotId = "aircraft-time-slot-1";
  private static final Instant reservationTime = Instant.parse("2024-03-20T10:00:00Z");

  @Test
  void shouldCreateNewReservation() {
    // Given
    var command = new Reservation.Command.CreateReservation(
        reservationId,
        studentId,
        studentTimeSlotId,
        instructorId,
        instructorTimeSlotId,
        aircraftId,
        aircraftTimeSlotId,
        reservationTime);
    var state = Reservation.State.empty();

    // When
    var events = state.onCommand(command);

    // Then
    assertEquals(4, events.size());
    var event = events.get(0);
    assertTrue(event instanceof Reservation.Event.ReservationCreated);
    var created = (Reservation.Event.ReservationCreated) event;

    assertEquals(reservationId, created.reservationId());
    assertEquals(studentTimeSlotId, created.studentTimeSlotId());
    assertEquals(instructorTimeSlotId, created.instructorTimeSlotId());
    assertEquals(aircraftTimeSlotId, created.aircraftTimeSlotId());
    assertEquals(reservationTime, created.reservationTime());
    assertEquals(Reservation.Status.pending, created.status());

    // Check the additional events
    assertTrue(events.get(1) instanceof Reservation.Event.StudentWantsTimeSlot);
    assertTrue(events.get(2) instanceof Reservation.Event.InstructorWantsTimeSlot);
    assertTrue(events.get(3) instanceof Reservation.Event.AircraftWantsTimeSlot);

    var studentWants = (Reservation.Event.StudentWantsTimeSlot) events.get(1);
    assertEquals(reservationId, studentWants.reservationId());
    assertEquals(studentTimeSlotId, studentWants.timeSlotId());

    var instructorWants = (Reservation.Event.InstructorWantsTimeSlot) events.get(2);
    assertEquals(reservationId, instructorWants.reservationId());
    assertEquals(instructorTimeSlotId, instructorWants.timeSlotId());

    var aircraftWants = (Reservation.Event.AircraftWantsTimeSlot) events.get(3);
    assertEquals(reservationId, aircraftWants.reservationId());
    assertEquals(aircraftTimeSlotId, aircraftWants.timeSlotId());
  }

  @Test
  void shouldNotCreateReservationWhenStateNotEmpty() {
    // Given
    var state = createPendingReservation();
    var command = new Reservation.Command.CreateReservation(
        reservationId,
        studentId,
        studentTimeSlotId,
        instructorId,
        instructorTimeSlotId,
        aircraftId,
        aircraftTimeSlotId,
        reservationTime);

    // When
    var events = state.onCommand(command);

    // Then
    assertTrue(events.isEmpty());
  }

  @Test
  void shouldMakeStudentAvailable() {
    // Given
    var state = createPendingReservation();
    var command = new Reservation.Command.StudentAvailable(state.reservationId());

    // When
    var events = state.onCommand(command);

    // Then
    assertEquals(1, events.size());
    var event = events.get(0);
    assertTrue(event instanceof Reservation.Event.StudentAvailable);
    assertEquals(state.reservationId(), ((Reservation.Event.StudentAvailable) event).reservationId());

    var newState = state.onEvent((Reservation.Event.StudentAvailable) event);
    assertEquals(Reservation.ParticipantStatus.available, newState.student().status());
  }

  @Test
  void shouldNotMakeStudentAvailableWhenStateEmpty() {
    // Given
    var state = Reservation.State.empty();
    var command = new Reservation.Command.StudentAvailable("non-existent");

    // When
    var events = state.onCommand(command);

    // Then
    assertTrue(events.isEmpty());
  }

  @Test
  void shouldMakeStudentUnavailable() {
    // Given
    var state = createPendingReservation();
    var command = new Reservation.Command.StudentUnavailable(state.reservationId());

    // When
    var events = state.onCommand(command);

    // Then
    assertEquals(4, events.size());
    assertTrue(events.get(0) instanceof Reservation.Event.StudentUnavailable);
    assertTrue(events.get(1) instanceof Reservation.Event.CancelledInstructorReservation);
    assertTrue(events.get(2) instanceof Reservation.Event.CancelledAircraftReservation);
    assertTrue(events.get(3) instanceof Reservation.Event.ReservationCancelled);

    var newState = state
        .onEvent((Reservation.Event.StudentUnavailable) events.get(0))
        .onEvent((Reservation.Event.CancelledInstructorReservation) events.get(1))
        .onEvent((Reservation.Event.CancelledAircraftReservation) events.get(2))
        .onEvent((Reservation.Event.ReservationCancelled) events.get(3));
    assertEquals(Reservation.ParticipantStatus.unavailable, newState.student().status());
    assertEquals(Reservation.Status.cancelled, newState.status());
  }

  @Test
  void shouldMakeInstructorAvailable() {
    // Given
    var state = createPendingReservation();
    var command = new Reservation.Command.InstructorAvailable(state.reservationId());

    // When
    var events = state.onCommand(command);

    // Then
    assertEquals(1, events.size());
    var event = events.get(0);
    assertTrue(event instanceof Reservation.Event.InstructorAvailable);
    assertEquals(state.reservationId(), ((Reservation.Event.InstructorAvailable) event).reservationId());

    var newState = state.onEvent((Reservation.Event.InstructorAvailable) event);
    assertEquals(Reservation.ParticipantStatus.available, newState.instructor().status());
  }

  @Test
  void shouldMakeInstructorUnavailable() {
    // Given
    var state = createPendingReservation();
    var command = new Reservation.Command.InstructorUnavailable(state.reservationId());

    // When
    var events = state.onCommand(command);

    // Then
    assertEquals(4, events.size());
    assertTrue(events.get(0) instanceof Reservation.Event.InstructorUnavailable);
    assertTrue(events.get(1) instanceof Reservation.Event.CancelledStudentReservation);
    assertTrue(events.get(2) instanceof Reservation.Event.CancelledAircraftReservation);
    assertTrue(events.get(3) instanceof Reservation.Event.ReservationCancelled);

    var newState = state
        .onEvent((Reservation.Event.InstructorUnavailable) events.get(0))
        .onEvent((Reservation.Event.CancelledStudentReservation) events.get(1))
        .onEvent((Reservation.Event.CancelledAircraftReservation) events.get(2))
        .onEvent((Reservation.Event.ReservationCancelled) events.get(3));
    assertEquals(Reservation.ParticipantStatus.unavailable, newState.instructor().status());
    assertEquals(Reservation.Status.cancelled, newState.status());
  }

  @Test
  void shouldMakeAircraftAvailable() {
    // Given
    var state = createPendingReservation();
    var command = new Reservation.Command.AircraftAvailable(state.reservationId());

    // When
    var events = state.onCommand(command);

    // Then
    assertEquals(1, events.size());
    var event = events.get(0);
    assertTrue(event instanceof Reservation.Event.AircraftAvailable);
    assertEquals(state.reservationId(), ((Reservation.Event.AircraftAvailable) event).reservationId());

    var newState = state.onEvent((Reservation.Event.AircraftAvailable) event);
    assertEquals(Reservation.ParticipantStatus.available, newState.aircraft().status());
  }

  @Test
  void shouldMakeAircraftUnavailable() {
    // Given
    var state = createPendingReservation();
    var command = new Reservation.Command.AircraftUnavailable(state.reservationId());

    // When
    var events = state.onCommand(command);

    // Then
    assertEquals(4, events.size());
    assertTrue(events.get(0) instanceof Reservation.Event.AircraftUnavailable);
    assertTrue(events.get(1) instanceof Reservation.Event.CancelledStudentReservation);
    assertTrue(events.get(2) instanceof Reservation.Event.CancelledInstructorReservation);
    assertTrue(events.get(3) instanceof Reservation.Event.ReservationCancelled);

    var newState = state
        .onEvent((Reservation.Event.AircraftUnavailable) events.get(0))
        .onEvent((Reservation.Event.CancelledStudentReservation) events.get(1))
        .onEvent((Reservation.Event.CancelledInstructorReservation) events.get(2))
        .onEvent((Reservation.Event.ReservationCancelled) events.get(3));
    assertEquals(Reservation.ParticipantStatus.unavailable, newState.aircraft().status());
    assertEquals(Reservation.Status.cancelled, newState.status());
  }

  @Test
  void shouldConfirmReservationWhenAllParticipantsAvailable() {
    // Given
    var state = createPendingReservation();

    // Make student and instructor available
    var student = new Reservation.Participant(studentId, TimeSlot.ParticipantType.student.name(), studentTimeSlotId, Reservation.ParticipantStatus.available);
    var studentAvailable = new Reservation.Event.StudentAvailable(state.reservationId(), student);
    var instructor = new Reservation.Participant(instructorId, TimeSlot.ParticipantType.instructor.name(), instructorTimeSlotId, Reservation.ParticipantStatus.available);
    var instructorAvailable = new Reservation.Event.InstructorAvailable(state.reservationId(), instructor);
    state = state
        .onEvent(studentAvailable)
        .onEvent(instructorAvailable);

    // Make aircraft available
    var command = new Reservation.Command.AircraftAvailable(state.reservationId());

    // When
    var events = state.onCommand(command);

    // Then
    assertEquals(2, events.size());
    assertTrue(events.get(0) instanceof Reservation.Event.AircraftAvailable);
    assertTrue(events.get(1) instanceof Reservation.Event.ReservationConfirmed);

    var newState = state
        .onEvent((Reservation.Event.AircraftAvailable) events.get(0))
        .onEvent((Reservation.Event.ReservationConfirmed) events.get(1));
    assertEquals(Reservation.Status.confirmed, newState.status());
    assertEquals(Reservation.ParticipantStatus.available, newState.student().status());
    assertEquals(Reservation.ParticipantStatus.available, newState.instructor().status());
    assertEquals(Reservation.ParticipantStatus.available, newState.aircraft().status());
  }

  @Test
  void shouldCancelReservation() {
    // Given
    var state = createPendingReservation();

    {
      var command = new Reservation.Command.StudentAvailable(state.reservationId());
      var events = state.onCommand(command);
      state = state.onEvent((Reservation.Event.StudentAvailable) events.get(0));
      assertEquals(Reservation.ParticipantStatus.available, state.student().status());
    }

    {
      var command = new Reservation.Command.InstructorAvailable(state.reservationId());
      var events = state.onCommand(command);
      state = state.onEvent((Reservation.Event.InstructorAvailable) events.get(0));
      assertEquals(Reservation.ParticipantStatus.available, state.instructor().status());
    }

    {
      var command = new Reservation.Command.AircraftAvailable(state.reservationId());
      var events = state.onCommand(command);
      assertEquals(2, events.size());
      state = state
          .onEvent((Reservation.Event.AircraftAvailable) events.get(0))
          .onEvent((Reservation.Event.ReservationConfirmed) events.get(1));
      assertEquals(Reservation.Status.confirmed, state.status());
    }

    {
      var command = new Reservation.Command.CancelReservation(state.reservationId());
      var events = state.onCommand(command);
      assertEquals(4, events.size());
      state = state
          .onEvent((Reservation.Event.CancelledStudentReservation) events.get(0))
          .onEvent((Reservation.Event.CancelledInstructorReservation) events.get(1))
          .onEvent((Reservation.Event.CancelledAircraftReservation) events.get(2))
          .onEvent((Reservation.Event.ReservationCancelled) events.get(3));
      assertEquals(Reservation.Status.cancelled, state.status());
    }

    { // idempotence test, already cancelled should not change state or emit events
      var command = new Reservation.Command.CancelReservation(state.reservationId());
      var events = state.onCommand(command);
      assertTrue(events.isEmpty());
    }
  }

  // Helper methods
  private Reservation.State createPendingReservation() {
    var command = new Reservation.Command.CreateReservation(
        reservationId,
        studentId,
        studentTimeSlotId,
        instructorId,
        instructorTimeSlotId,
        aircraftId,
        aircraftTimeSlotId,
        reservationTime);
    var state = Reservation.State.empty();
    var events = state.onCommand(command);
    var event = (Reservation.Event.ReservationCreated) events.get(0);
    return state.onEvent(event);
  }
}