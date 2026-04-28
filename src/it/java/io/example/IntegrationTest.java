package io.example;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import akka.javasdk.testkit.TestKitSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.application.BookingWorkflow;
import io.example.application.ReservationEntity;
import io.example.application.TimeSlotEntity;
import io.example.domain.Booking;
import io.example.domain.Reservation;
import io.example.domain.Reservation.Status;
import io.example.domain.TimeSlot;

class IntegrationTest extends TestKitSupport {
  private static final Logger log = LoggerFactory.getLogger(IntegrationTest.class);

  @Test
  void testReservationWithNoAvailableTimeSlots() throws Exception {
    var reservationId = Reservation.generateReservationId();
    var studentId = "student-1";
    var instructorId = "instructor-1";
    var aircraftId = "aircraft-1";
    var studentTimeSlotId = "student-timeSlot-1";
    var instructorTimeSlotId = "instructor-timeSlot-2";
    var aircraftTimeSlotId = "aircraft-timeSlot-3";

    var command = new Reservation.Command.CreateReservation(
        reservationId,
        studentId,
        studentTimeSlotId,
        instructorId,
        instructorTimeSlotId,
        aircraftId,
        aircraftTimeSlotId,
        Instant.now());

    componentClient.forEventSourcedEntity(reservationId)
        .method(ReservationEntity::createReservation)
        .invokeAsync(command);

    var status = Status.pending;
    while (status != Status.cancelled) {
      var state = await(componentClient.forEventSourcedEntity(reservationId)
          .method(ReservationEntity::get)
          .invokeAsync());
      status = state.status();
      Thread.sleep(100); // Add small delay between checks
    }
    log.info("Reservation cancelled");
  }

  @Test
  void testReservationWithAllAvailableTimeSlots() throws Exception {
    var reservationId = Reservation.generateReservationId();
    var studentId = "student-1";
    var instructorId = "instructor-1";
    var aircraftId = "aircraft-1";
    var studentTimeSlotId = "student-timeSlot-1";
    var instructorTimeSlotId = "instructor-timeSlot-2";
    var aircraftTimeSlotId = "aircraft-timeSlot-3";

    var reservationTime = Instant.now();

    {
      var command = new TimeSlot.Command.MakeTimeSlotAvailable(
          studentTimeSlotId,
          studentId,
          TimeSlot.ParticipantType.student,
          reservationTime);

      componentClient.forEventSourcedEntity(studentTimeSlotId)
          .method(TimeSlotEntity::createTimeSlot)
          .invokeAsync(command);
    }

    {
      var command = new TimeSlot.Command.MakeTimeSlotAvailable(
          instructorTimeSlotId,
          instructorId,
          TimeSlot.ParticipantType.instructor,
          reservationTime);

      componentClient.forEventSourcedEntity(instructorTimeSlotId)
          .method(TimeSlotEntity::createTimeSlot)
          .invokeAsync(command);
    }

    {
      var command = new TimeSlot.Command.MakeTimeSlotAvailable(
          aircraftTimeSlotId,
          aircraftId,
          TimeSlot.ParticipantType.aircraft,
          reservationTime);

      componentClient.forEventSourcedEntity(aircraftTimeSlotId)
          .method(TimeSlotEntity::createTimeSlot)
          .invokeAsync(command);
    }

    {
      var command = new Reservation.Command.CreateReservation(
          reservationId,
          studentId,
          studentTimeSlotId,
          instructorId,
          instructorTimeSlotId,
          aircraftId,
          aircraftTimeSlotId,
          reservationTime);

      componentClient.forEventSourcedEntity(reservationId)
          .method(ReservationEntity::createReservation)
          .invokeAsync(command);

      var status = Status.pending;
      while (status != Status.confirmed) {
        var state = await(componentClient.forEventSourcedEntity(reservationId)
            .method(ReservationEntity::get)
            .invokeAsync());
        status = state.status();
        Thread.sleep(100); // Add small delay between checks
      }
      log.info("Reservation confirmed");
    }
  }

  @Test
  void testReservationWithOneUnavailableTimeSlot() throws Exception {
    var reservationId = Reservation.generateReservationId();
    var studentId = "student-1";
    var instructorId = "instructor-1";
    var aircraftId = "aircraft-1";
    var studentTimeSlotId = "student-timeSlot-1";
    var instructorTimeSlotId = "instructor-timeSlot-2";
    var aircraftTimeSlotId = "aircraft-timeSlot-3";

    var reservationTime = Instant.now();

    {
      var command = new TimeSlot.Command.MakeTimeSlotAvailable(
          studentTimeSlotId,
          studentId,
          TimeSlot.ParticipantType.student,
          reservationTime);

      componentClient.forEventSourcedEntity(studentTimeSlotId)
          .method(TimeSlotEntity::createTimeSlot)
          .invokeAsync(command);
    }

    {
      var command = new TimeSlot.Command.MakeTimeSlotAvailable(
          instructorTimeSlotId,
          instructorId,
          TimeSlot.ParticipantType.instructor,
          reservationTime);

      componentClient.forEventSourcedEntity(instructorTimeSlotId)
          .method(TimeSlotEntity::createTimeSlot)
          .invokeAsync(command);
    }

    {
      var command = new TimeSlot.Command.MakeTimeSlotAvailable(
          aircraftTimeSlotId,
          aircraftId,
          TimeSlot.ParticipantType.aircraft,
          reservationTime);

      componentClient.forEventSourcedEntity(aircraftTimeSlotId)
          .method(TimeSlotEntity::createTimeSlot)
          .invokeAsync(command);
    }

    {
      var command = new TimeSlot.Command.CancelTimeSlot(aircraftTimeSlotId, reservationId);
      componentClient.forEventSourcedEntity(aircraftTimeSlotId)
          .method(TimeSlotEntity::cancelTimeSlot)
          .invokeAsync(command);
    }

    {
      var command = new Reservation.Command.CreateReservation(
          reservationId,
          studentId,
          studentTimeSlotId,
          instructorId,
          instructorTimeSlotId,
          aircraftId,
          aircraftTimeSlotId,
          reservationTime);

      componentClient.forEventSourcedEntity(reservationId)
          .method(ReservationEntity::createReservation)
          .invokeAsync(command);

      var status = Status.pending;
      while (status != Status.cancelled) {
        Thread.sleep(100); // Add small delay between status check
        var state = await(componentClient.forEventSourcedEntity(reservationId)
            .method(ReservationEntity::get)
            .invokeAsync());
        status = state.status();
        log.info("Reservation status: {}", status);
      }
      log.info("Reservation cancelled");
    }
  }

  @Test
  void testBookingWithNoAvailableTimeSlots() throws Exception {
    var reservationId = Reservation.generateReservationId();
    var studentId = "student-1";
    var reservationTime = Instant.now();

    var request = new BookingWorkflow.BookingRequest(reservationId, studentId, reservationTime);

    componentClient.forWorkflow(reservationId)
        .method(BookingWorkflow::startBooking)
        .invokeAsync(request);

    var status = Booking.Status.pending;
    while (status != Booking.Status.cancelledStudentNotAvailable) {
      var state = await(componentClient.forWorkflow(reservationId)
          .method(BookingWorkflow::get)
          .invokeAsync());
      status = state.status();
      Thread.sleep(100); // Add small delay between checks
    }
    log.info("Booking cancelled because student is not available");
  }
}
