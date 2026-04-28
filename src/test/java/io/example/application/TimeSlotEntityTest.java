package io.example.application;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.example.domain.TimeSlot;
import io.example.domain.TimeSlot.ParticipantType;

class TimeSlotEntityTest {
  @Test
  void testMakeTimeSlotAvailable() {
    var testKit = EventSourcedTestKit.of(TimeSlotEntity::new);

    var timeSlotId = "timeSlot-1";
    var participantId = "participant-1";
    var participantType = ParticipantType.aircraft;
    var startTime = Instant.parse("2024-03-20T10:00:00Z");

    {
      var command = new TimeSlot.Command.MakeTimeSlotAvailable(
          timeSlotId,
          participantId,
          participantType,
          startTime);
      var result = testKit.call(entity -> entity.createTimeSlot(command));

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());

      var event = result.getNextEventOfType(TimeSlot.Event.TimeSlotMadeAvailable.class);
      assertEquals(timeSlotId, event.timeSlotId());
      assertEquals(participantId, event.participantId());
      assertEquals(participantType, event.participantType());
      assertEquals(startTime, event.startTime());
    }

    {
      var state = testKit.getState();
      assertEquals(timeSlotId, state.timeSlotId());
      assertEquals(participantId, state.participantId());
      assertEquals(participantType, state.participantType());
      assertEquals(startTime, state.startTime());
      assertEquals(TimeSlot.Status.available, state.status());
    }
  }

  @Test
  void testCreateTimeSlotThatAlreadyExists() {
    var testKit = EventSourcedTestKit.of(TimeSlotEntity::new);

    var timeSlotId = "timeSlot-1";
    var participantId = "participant-1";
    var participantType = ParticipantType.aircraft;
    var startTime = Instant.parse("2024-03-20T10:00:00Z");

    {
      var command = new TimeSlot.Command.MakeTimeSlotAvailable(
          timeSlotId,
          participantId,
          participantType,
          startTime);
      var result = testKit.call(entity -> entity.createTimeSlot(command));

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
    }

    {
      var command = new TimeSlot.Command.MakeTimeSlotAvailable(
          timeSlotId,
          participantId,
          participantType,
          startTime);
      var result = testKit.call(entity -> entity.createTimeSlot(command));

      assertTrue(result.getAllEvents().isEmpty());
    }
  }

  @Test
  void testMakeTimeSlotUnavailable() {
    var testKit = EventSourcedTestKit.of(TimeSlotEntity::new);
    var timeSlotId = setupAvailableTimeSlot(testKit);

    var command = new TimeSlot.Command.MakeTimeSlotUnavailable(timeSlotId);
    var result = testKit.call(entity -> entity.makeTimeSlotUnavailable(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var event = result.getNextEventOfType(TimeSlot.Event.TimeSlotMadeUnavailable.class);
    assertEquals(timeSlotId, event.timeSlotId());
  }

  @Test
  void testMakeTimeSlotUnavailableWhenAlreadyUnavailable() {
    var testKit = EventSourcedTestKit.of(TimeSlotEntity::new);
    var timeSlotId = setupAvailableTimeSlot(testKit);

    var command = new TimeSlot.Command.MakeTimeSlotUnavailable(timeSlotId);
    testKit.call(entity -> entity.makeTimeSlotUnavailable(command)); // duplicate command to simulate duplicate command
    var result = testKit.call(entity -> entity.makeTimeSlotUnavailable(command));

    assertTrue(result.getAllEvents().isEmpty());
  }

  @Test
  void testStudentRequestsTimeSlotAccepted() {
    var testKit = EventSourcedTestKit.of(TimeSlotEntity::new);
    var timeSlotId = setupAvailableTimeSlot(testKit);
    var reservationId = "reservation-1";

    var command = new TimeSlot.Command.StudentRequestsTimeSlot(timeSlotId, reservationId);
    var result = testKit.call(entity -> entity.studentRequestsTimeSlot(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var event = result.getNextEventOfType(TimeSlot.Event.StudentRequestAccepted.class);
    assertEquals(timeSlotId, event.timeSlotId());
    assertEquals(reservationId, event.reservationId());

    var state = testKit.getState();
    assertEquals(TimeSlot.Status.scheduled, state.status());
    assertEquals(reservationId, state.reservationId());
  }

  @Test
  void testStudentRequestsTimeSlotRejected() {
    var testKit = EventSourcedTestKit.of(TimeSlotEntity::new);
    var timeSlotId = setupAvailableTimeSlot(testKit);

    // First schedule the time slot
    testKit.call(entity -> entity.studentRequestsTimeSlot(
        new TimeSlot.Command.StudentRequestsTimeSlot(timeSlotId, "reservation-1")));

    // Try to schedule again
    var command = new TimeSlot.Command.StudentRequestsTimeSlot(timeSlotId, "reservation-2");
    var result = testKit.call(entity -> entity.studentRequestsTimeSlot(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var event = result.getNextEventOfType(TimeSlot.Event.StudentRequestRejected.class);
    assertEquals(timeSlotId, event.timeSlotId());
    assertEquals("reservation-2", event.reservationId());
  }

  @Test
  void testInstructorRequestsTimeSlotAccepted() {
    var testKit = EventSourcedTestKit.of(TimeSlotEntity::new);
    var timeSlotId = setupAvailableTimeSlot(testKit);
    var reservationId = "reservation-1";

    var command = new TimeSlot.Command.InstructorRequestsTimeSlot(timeSlotId, reservationId);
    var result = testKit.call(entity -> entity.instructorRequestsTimeSlot(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var event = result.getNextEventOfType(TimeSlot.Event.InstructorRequestAccepted.class);
    assertEquals(timeSlotId, event.timeSlotId());
    assertEquals(reservationId, event.reservationId());

    var state = testKit.getState();
    assertEquals(TimeSlot.Status.scheduled, state.status());
    assertEquals(reservationId, state.reservationId());
  }

  @Test
  void testInstructorRequestsTimeSlotRejected() {
    var testKit = EventSourcedTestKit.of(TimeSlotEntity::new);
    var timeSlotId = setupAvailableTimeSlot(testKit);

    // First schedule the time slot
    testKit.call(entity -> entity.instructorRequestsTimeSlot(
        new TimeSlot.Command.InstructorRequestsTimeSlot(timeSlotId, "reservation-1")));

    // Try to schedule again
    var command = new TimeSlot.Command.InstructorRequestsTimeSlot(timeSlotId, "reservation-2");
    var result = testKit.call(entity -> entity.instructorRequestsTimeSlot(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var event = result.getNextEventOfType(TimeSlot.Event.InstructorRequestRejected.class);
    assertEquals(timeSlotId, event.timeSlotId());
    assertEquals("reservation-2", event.reservationId());
  }

  @Test
  void testAircraftRequestsTimeSlotAccepted() {
    var testKit = EventSourcedTestKit.of(TimeSlotEntity::new);
    var timeSlotId = setupAvailableTimeSlot(testKit);
    var reservationId = "reservation-1";

    var command = new TimeSlot.Command.AircraftRequestsTimeSlot(timeSlotId, reservationId);
    var result = testKit.call(entity -> entity.aircraftRequestsTimeSlot(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var event = result.getNextEventOfType(TimeSlot.Event.AircraftRequestAccepted.class);
    assertEquals(timeSlotId, event.timeSlotId());
    assertEquals(reservationId, event.reservationId());

    var state = testKit.getState();
    assertEquals(TimeSlot.Status.scheduled, state.status());
    assertEquals(reservationId, state.reservationId());
  }

  @Test
  void testAircraftRequestsTimeSlotRejected() {
    var testKit = EventSourcedTestKit.of(TimeSlotEntity::new);
    var timeSlotId = setupAvailableTimeSlot(testKit);

    // First schedule the time slot
    testKit.call(entity -> entity.aircraftRequestsTimeSlot(
        new TimeSlot.Command.AircraftRequestsTimeSlot(timeSlotId, "reservation-1")));

    // Try to schedule again
    var command = new TimeSlot.Command.AircraftRequestsTimeSlot(timeSlotId, "reservation-2");
    var result = testKit.call(entity -> entity.aircraftRequestsTimeSlot(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var event = result.getNextEventOfType(TimeSlot.Event.AircraftRequestRejected.class);
    assertEquals(timeSlotId, event.timeSlotId());
    assertEquals("reservation-2", event.reservationId());
  }

  @Test
  void testCancelTimeSlot() {
    var testKit = EventSourcedTestKit.of(TimeSlotEntity::new);
    var timeSlotId = setupAvailableTimeSlot(testKit);
    var reservationId = "reservation-1";

    // First schedule the time slot
    testKit.call(entity -> entity.studentRequestsTimeSlot(
        new TimeSlot.Command.StudentRequestsTimeSlot(timeSlotId, reservationId)));

    // Then cancel it
    var command = new TimeSlot.Command.CancelTimeSlot(timeSlotId, reservationId);
    var result = testKit.call(entity -> entity.cancelTimeSlot(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());

    var event = result.getNextEventOfType(TimeSlot.Event.TimeSlotReservationCancelled.class);
    assertEquals(timeSlotId, event.timeSlotId());
    assertEquals(reservationId, event.reservationId());

    var state = testKit.getState();
    assertEquals(TimeSlot.Status.available, state.status());
    assertNull(state.reservationId());
  }

  @Test
  void testCancelTimeSlotWithWrongReservationId() {
    var testKit = EventSourcedTestKit.of(TimeSlotEntity::new);
    var timeSlotId = setupAvailableTimeSlot(testKit);
    var reservationId = "reservation-1";

    // First schedule the time slot
    testKit.call(entity -> entity.cancelTimeSlot(
        new TimeSlot.Command.CancelTimeSlot(timeSlotId, reservationId)));

    // Try to cancel with wrong reservation ID
    var command = new TimeSlot.Command.CancelTimeSlot(timeSlotId, "wrong-id");
    var result = testKit.call(entity -> entity.cancelTimeSlot(command));

    assertTrue(result.getAllEvents().isEmpty());
  }

  private String setupAvailableTimeSlot(EventSourcedTestKit<TimeSlot.State, TimeSlot.Event, TimeSlotEntity> testKit) {
    var timeSlotId = "timeSlot-1";
    var command = new TimeSlot.Command.MakeTimeSlotAvailable(
        timeSlotId,
        "participant-1",
        ParticipantType.aircraft,
        Instant.parse("2024-03-20T10:00:00Z"));
    testKit.call(entity -> entity.createTimeSlot(command));
    return timeSlotId;
  }
}
