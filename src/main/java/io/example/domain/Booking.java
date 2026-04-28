package io.example.domain;

import java.time.Instant;

public interface Booking {
  public enum Status {
    pending,
    cancelledStudentNotAvailable,
    cancelledInstructorNotAvailable,
    cancelledAircraftNotAvailable,
    reservationRequested
  }

  record State(
      String studentId,
      String studentTimeSlotId,
      String instructorId,
      String instructorTimeSlotId,
      String aircraftId,
      String aircraftTimeSlotId,
      Instant reservationTime,
      String reservationId,
      Status status) {
    public static State initialState(String studentId, Instant reservationTime) {
      return new State(
          studentId,
          null,
          null,
          null,
          null,
          null,
          reservationTime,
          null,
          Status.pending);
    }

    public boolean isEmpty() {
      return studentId == null;
    }

    public State withStudentTimeSlot(String newStudentTimeSlotId) {
      return new State(
          studentId,
          newStudentTimeSlotId,
          instructorId,
          instructorTimeSlotId,
          aircraftId,
          aircraftTimeSlotId,
          reservationTime,
          reservationId,
          status);
    }

    public State withInstructor(String newInstructorId, String newInstructorTimeSlotId) {
      return new State(
          studentId,
          studentTimeSlotId,
          newInstructorId,
          newInstructorTimeSlotId,
          aircraftId,
          aircraftTimeSlotId,
          reservationTime,
          reservationId,
          status);
    }

    public State withAircraftAndReservationId(String newAircraftId, String newAircraftTimeSlotId, String newReservationId) {
      return new State(
          studentId,
          studentTimeSlotId,
          instructorId,
          instructorTimeSlotId,
          newAircraftId,
          newAircraftTimeSlotId,
          reservationTime,
          newReservationId,
          status);
    }

    public State withStatus(Status newStatus) {
      return new State(
          studentId,
          studentTimeSlotId,
          instructorId,
          instructorTimeSlotId,
          aircraftId,
          aircraftTimeSlotId,
          reservationTime,
          reservationId,
          newStatus);
    }
  }
}
