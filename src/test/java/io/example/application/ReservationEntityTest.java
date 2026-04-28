package io.example.application;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.example.domain.Reservation;
import io.example.domain.Reservation.ParticipantStatus;

class ReservationEntityTest {
  private static final String reservationId = Reservation.generateReservationId();
  private static final String studentId = "student-1";
  private static final String studentTimeSlotId = "student-time-slot-1";
  private static final String instructorId = "instructor-1";
  private static final String instructorTimeSlotId = "instructor-time-slot-1";
  private static final String aircraftId = "aircraft-1";
  private static final String aircraftTimeSlotId = "aircraft-time-slot-1";
  private static final Instant reservationTime = Instant.parse("2024-03-20T10:00:00Z");

  @Test
  void testCreateReservation() {
    var testKit = EventSourcedTestKit.of(ReservationEntity::new);

    var command = new Reservation.Command.CreateReservation(
        reservationId,
        studentId,
        studentTimeSlotId,
        instructorId,
        instructorTimeSlotId,
        aircraftId,
        aircraftTimeSlotId,
        reservationTime);

    var result = testKit.call(entity -> entity.createReservation(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var event = result.getNextEventOfType(Reservation.Event.ReservationCreated.class);
    assertEquals(studentId, event.studentId());
    assertEquals(studentTimeSlotId, event.studentTimeSlotId());
    assertEquals(instructorId, event.instructorId());
    assertEquals(instructorTimeSlotId, event.instructorTimeSlotId());
    assertEquals(aircraftId, event.aircraftId());
    assertEquals(aircraftTimeSlotId, event.aircraftTimeSlotId());
    assertEquals(reservationTime, event.reservationTime());
    assertEquals(Reservation.Status.pending, event.status());

    var studentWants = result.getNextEventOfType(Reservation.Event.StudentWantsTimeSlot.class);
    assertEquals(reservationId, studentWants.reservationId());
    assertEquals(studentTimeSlotId, studentWants.timeSlotId());

    var instructorWants = result.getNextEventOfType(Reservation.Event.InstructorWantsTimeSlot.class);
    assertEquals(reservationId, instructorWants.reservationId());
    assertEquals(instructorTimeSlotId, instructorWants.timeSlotId());

    var aircraftWants = result.getNextEventOfType(Reservation.Event.AircraftWantsTimeSlot.class);
    assertEquals(reservationId, aircraftWants.reservationId());
    assertEquals(aircraftTimeSlotId, aircraftWants.timeSlotId());
  }

  @Test
  void testStudentAvailable() {
    var testKit = EventSourcedTestKit.of(ReservationEntity::new);
    var reservationId = setupPendingReservation(testKit);

    var command = new Reservation.Command.StudentAvailable(reservationId);
    var result = testKit.call(entity -> entity.studentAvailable(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var event = result.getNextEventOfType(Reservation.Event.StudentAvailable.class);
    assertEquals(reservationId, event.reservationId());

    var state = testKit.getState();
    assertEquals(ParticipantStatus.available, state.student().status());
  }

  @Test
  void testStudentUnavailable() {
    var testKit = EventSourcedTestKit.of(ReservationEntity::new);
    var reservationId = setupPendingReservation(testKit);

    var command = new Reservation.Command.StudentUnavailable(reservationId);
    var result = testKit.call(entity -> entity.studentUnavailable(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var event = result.getNextEventOfType(Reservation.Event.StudentUnavailable.class);
    assertEquals(reservationId, event.reservationId());

    var cancelledInstructorReservation = result.getNextEventOfType(Reservation.Event.CancelledInstructorReservation.class);
    assertEquals(reservationId, cancelledInstructorReservation.reservationId());

    var cancelledAircraftReservation = result.getNextEventOfType(Reservation.Event.CancelledAircraftReservation.class);
    assertEquals(reservationId, cancelledAircraftReservation.reservationId());

    var cancelEvent = result.getNextEventOfType(Reservation.Event.ReservationCancelled.class);
    assertEquals(reservationId, cancelEvent.reservationId());

    var state = testKit.getState();
    assertEquals(ParticipantStatus.unavailable, state.student().status());
    assertEquals(Reservation.Status.cancelled, state.status());
  }

  @Test
  void testInstructorAvailable() {
    var testKit = EventSourcedTestKit.of(ReservationEntity::new);
    var reservationId = setupPendingReservation(testKit);

    var command = new Reservation.Command.InstructorAvailable(reservationId);
    var result = testKit.call(entity -> entity.instructorAvailable(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var event = result.getNextEventOfType(Reservation.Event.InstructorAvailable.class);
    assertEquals(reservationId, event.reservationId());

    var state = testKit.getState();
    assertEquals(ParticipantStatus.available, state.instructor().status());
  }

  @Test
  void testInstructorUnavailable() {
    var testKit = EventSourcedTestKit.of(ReservationEntity::new);
    var reservationId = setupPendingReservation(testKit);

    var command = new Reservation.Command.InstructorUnavailable(reservationId);
    var result = testKit.call(entity -> entity.instructorUnavailable(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var event = result.getNextEventOfType(Reservation.Event.InstructorUnavailable.class);
    assertEquals(reservationId, event.reservationId());

    var cancelledStudentReservation = result.getNextEventOfType(Reservation.Event.CancelledStudentReservation.class);
    assertEquals(reservationId, cancelledStudentReservation.reservationId());

    var cancelledAircraftReservation = result.getNextEventOfType(Reservation.Event.CancelledAircraftReservation.class);
    assertEquals(reservationId, cancelledAircraftReservation.reservationId());

    var cancelEvent = result.getNextEventOfType(Reservation.Event.ReservationCancelled.class);
    assertEquals(reservationId, cancelEvent.reservationId());

    var state = testKit.getState();
    assertEquals(ParticipantStatus.unavailable, state.instructor().status());
    assertEquals(Reservation.Status.cancelled, state.status());
  }

  @Test
  void testAircraftAvailable() {
    var testKit = EventSourcedTestKit.of(ReservationEntity::new);
    var reservationId = setupPendingReservation(testKit);

    var command = new Reservation.Command.AircraftAvailable(reservationId);
    var result = testKit.call(entity -> entity.aircraftAvailable(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var event = result.getNextEventOfType(Reservation.Event.AircraftAvailable.class);
    assertEquals(reservationId, event.reservationId());

    var state = testKit.getState();
    assertEquals(ParticipantStatus.available, state.aircraft().status());
  }

  @Test
  void testAircraftUnavailable() {
    var testKit = EventSourcedTestKit.of(ReservationEntity::new);
    var reservationId = setupPendingReservation(testKit);

    var command = new Reservation.Command.AircraftUnavailable(reservationId);
    var result = testKit.call(entity -> entity.aircraftUnavailable(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var event = result.getNextEventOfType(Reservation.Event.AircraftUnavailable.class);
    assertEquals(reservationId, event.reservationId());

    var cancelledStudentReservation = result.getNextEventOfType(Reservation.Event.CancelledStudentReservation.class);
    assertEquals(reservationId, cancelledStudentReservation.reservationId());

    var cancelledInstructorReservation = result.getNextEventOfType(Reservation.Event.CancelledInstructorReservation.class);
    assertEquals(reservationId, cancelledInstructorReservation.reservationId());

    var cancelEvent = result.getNextEventOfType(Reservation.Event.ReservationCancelled.class);
    assertEquals(reservationId, cancelEvent.reservationId());

    var state = testKit.getState();
    assertEquals(ParticipantStatus.unavailable, state.aircraft().status());
    assertEquals(Reservation.Status.cancelled, state.status());
  }

  @Test
  void testReservationConfirmedWhenAllAvailable() {
    var testKit = EventSourcedTestKit.of(ReservationEntity::new);
    var reservationId = setupPendingReservation(testKit);

    // Make student available
    testKit.call(entity -> entity.studentAvailable(new Reservation.Command.StudentAvailable(reservationId)));

    // Make instructor available
    testKit.call(entity -> entity.instructorAvailable(new Reservation.Command.InstructorAvailable(reservationId)));

    // Make aircraft available - this should trigger confirmation
    var result = testKit.call(entity -> entity.aircraftAvailable(new Reservation.Command.AircraftAvailable(reservationId)));

    var availableEvent = result.getNextEventOfType(Reservation.Event.AircraftAvailable.class);
    assertEquals(reservationId, availableEvent.reservationId());

    var confirmEvent = result.getNextEventOfType(Reservation.Event.ReservationConfirmed.class);
    assertEquals(reservationId, confirmEvent.reservationId());

    var state = testKit.getState();
    assertEquals(Reservation.Status.confirmed, state.status());
    assertEquals(ParticipantStatus.available, state.student().status());
    assertEquals(ParticipantStatus.available, state.instructor().status());
    assertEquals(ParticipantStatus.available, state.aircraft().status());
  }

  @Test
  void testGetEmptyReservation() {
    var testKit = EventSourcedTestKit.of(ReservationEntity::new);

    var result = testKit.call(entity -> entity.get());

    assertTrue(result.isError());
    assertEquals("Reservation not found", result.getError());
  }

  @Test
  void testGetExistingReservation() {
    var testKit = EventSourcedTestKit.of(ReservationEntity::new);
    var reservationId = setupPendingReservation(testKit);

    var result = testKit.call(entity -> entity.get());

    assertTrue(result.isReply());
    var state = result.getReply();
    assertEquals(reservationId, state.reservationId());
    assertEquals(studentTimeSlotId, state.student().timeSlotId());
    assertEquals(instructorTimeSlotId, state.instructor().timeSlotId());
    assertEquals(aircraftTimeSlotId, state.aircraft().timeSlotId());
    assertEquals(reservationTime, state.reservationTime());
    assertEquals(Reservation.Status.pending, state.status());
  }

  private String setupPendingReservation(
      EventSourcedTestKit<Reservation.State, Reservation.Event, ReservationEntity> testKit) {
    var command = new Reservation.Command.CreateReservation(
        reservationId,
        studentId,
        studentTimeSlotId,
        instructorId,
        instructorTimeSlotId,
        aircraftId,
        aircraftTimeSlotId,
        reservationTime);

    var result = testKit.call(entity -> entity.createReservation(command));
    var event = result.getNextEventOfType(Reservation.Event.ReservationCreated.class);
    return event.reservationId();
  }
}
