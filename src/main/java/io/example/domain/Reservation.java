package io.example.domain;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public interface Reservation {
  public enum Status {
    pending, // Initial state: awaiting confirmation from instructor and aircraft
    confirmed, // Both instructor and aircraft have accepted the reservation
    cancelled // Cancelled due to: student/instructor cancellation, aircraft unavailability,
              // weather conditions, mechanical issues, or other safety concerns
  }

  public enum ParticipantStatus {
    pending, // Hasn't responded to reservation request
    available, // Has confirmed availability
    unavailable // Has declined or is not available
  }

  public record Participant(
      String participantId,
      String participantType,
      String timeSlotId,
      ParticipantStatus status) {

    public static Participant pending(String participantId, String participantType, String timeSlotId) {
      return new Participant(participantId, participantType, timeSlotId, ParticipantStatus.pending);
    }
  }

  public record State(
      String reservationId,
      Participant student,
      Participant instructor,
      Participant aircraft,
      Instant reservationTime,
      Status status) {
    public static State empty() {
      return new State(null, null, null, null, null, null);
    }

    public boolean isEmpty() {
      return reservationId == null;
    }

    public List<Event> onCommand(Command.CreateReservation command) {
      if (isEmpty()) {
        Instant roundedTime = command.reservationTime.plus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.HOURS);
        return List.of(
            new Event.ReservationCreated(
                command.reservationId(),
                command.studentId(),
                command.studentTimeSlotId(),
                command.instructorId(),
                command.instructorTimeSlotId(),
                command.aircraftId(),
                command.aircraftTimeSlotId(),
                roundedTime,
                Status.pending),
            new Event.StudentWantsTimeSlot(
                command.reservationId(),
                command.studentTimeSlotId()),
            new Event.InstructorWantsTimeSlot(
                command.reservationId(),
                command.instructorTimeSlotId()),
            new Event.AircraftWantsTimeSlot(
                command.reservationId(),
                command.aircraftTimeSlotId()));
      }

      return List.of();
    }

    public List<Event> onCommand(Command.StudentAvailable command) {
      if (isEmpty() || student.status() != ParticipantStatus.pending) {
        return List.of();
      }

      var newStudent = new Participant(student.participantId(), student.participantType(), student.timeSlotId(), ParticipantStatus.available);
      return (instructor.status() == ParticipantStatus.available &&
          aircraft.status() == ParticipantStatus.available)
              ? List.of(
                  new Event.StudentAvailable(command.reservationId(), newStudent),
                  new Event.ReservationConfirmed(command.reservationId()))
              : List.of(new Event.StudentAvailable(command.reservationId(), newStudent));
    }

    public List<Event> onCommand(Command.StudentUnavailable command) {
      if (isEmpty() || student.status() != ParticipantStatus.pending) {
        return List.of();
      }

      var newStudent = new Participant(student.participantId(), student.participantType(), student.timeSlotId(), ParticipantStatus.unavailable);
      return List.of(
          new Event.StudentUnavailable(command.reservationId(), newStudent),
          new Event.CancelledInstructorReservation(instructor.timeSlotId(), command.reservationId()),
          new Event.CancelledAircraftReservation(aircraft.timeSlotId(), command.reservationId()),
          new Event.ReservationCancelled(command.reservationId()));
    }

    public List<Event> onCommand(Command.InstructorAvailable command) {
      if (isEmpty() || instructor.status() != ParticipantStatus.pending) {
        return List.of();
      }

      var newInstructor = new Participant(instructor.participantId(), instructor.participantType(), instructor.timeSlotId(), ParticipantStatus.available);
      return (student.status() == ParticipantStatus.available &&
          aircraft.status() == ParticipantStatus.available)
              ? List.of(
                  new Event.InstructorAvailable(command.reservationId(), newInstructor),
                  new Event.ReservationConfirmed(command.reservationId()))
              : List.of(new Event.InstructorAvailable(command.reservationId(), newInstructor));
    }

    public List<Event> onCommand(Command.InstructorUnavailable command) {
      if (isEmpty() || instructor.status() != ParticipantStatus.pending) {
        return List.of();
      }

      var newInstructor = new Participant(instructor.participantId(), instructor.participantType(), instructor.timeSlotId(), ParticipantStatus.unavailable);
      return List.of(
          new Event.InstructorUnavailable(command.reservationId(), newInstructor),
          new Event.CancelledStudentReservation(student.timeSlotId(), command.reservationId()),
          new Event.CancelledAircraftReservation(aircraft.timeSlotId(), command.reservationId()),
          new Event.ReservationCancelled(reservationId));
    }

    public List<Event> onCommand(Command.AircraftAvailable command) {
      if (isEmpty() || aircraft.status() != ParticipantStatus.pending) {
        return List.of();
      }

      var newAircraft = new Participant(aircraft.participantId(), aircraft.participantType(), aircraft.timeSlotId(), ParticipantStatus.available);
      return (student.status() == ParticipantStatus.available &&
          instructor.status() == ParticipantStatus.available)
              ? List.of(
                  new Event.AircraftAvailable(command.reservationId(), newAircraft),
                  new Event.ReservationConfirmed(command.reservationId()))
              : List.of(new Event.AircraftAvailable(command.reservationId(), newAircraft));
    }

    public List<Event> onCommand(Command.AircraftUnavailable command) {
      if (isEmpty() || aircraft.status() != ParticipantStatus.pending) {
        return List.of();
      }

      var newAircraft = new Participant(aircraft.participantId(), aircraft.participantType(), aircraft.timeSlotId(), ParticipantStatus.unavailable);
      return List.of(
          new Event.AircraftUnavailable(command.reservationId(), newAircraft),
          new Event.CancelledStudentReservation(student.timeSlotId(), command.reservationId()),
          new Event.CancelledInstructorReservation(instructor.timeSlotId(), command.reservationId()),
          new Event.ReservationCancelled(command.reservationId()));
    }

    public List<Event> onCommand(Command.CancelReservation command) {
      if (isEmpty() || status() != Status.confirmed) {
        return List.of();
      }

      return List.of(
          new Event.CancelledStudentReservation(student.timeSlotId(), command.reservationId()),
          new Event.CancelledInstructorReservation(instructor.timeSlotId(), command.reservationId()),
          new Event.CancelledAircraftReservation(aircraft.timeSlotId(), command.reservationId()),
          new Event.ReservationCancelled(command.reservationId()));
    }

    public State onEvent(Event.ReservationCreated event) {
      return new State(
          event.reservationId(),
          Participant.pending(event.studentId(), TimeSlot.ParticipantType.student.name(), event.studentTimeSlotId()),
          Participant.pending(event.instructorId(), TimeSlot.ParticipantType.instructor.name(), event.instructorTimeSlotId()),
          Participant.pending(event.aircraftId(), TimeSlot.ParticipantType.aircraft.name(), event.aircraftTimeSlotId()),
          event.reservationTime(),
          event.status());
    }

    public State onEvent(Event.StudentWantsTimeSlot event) {
      return this;
    }

    public State onEvent(Event.StudentAvailable event) {
      return new State(
          reservationId,
          event.student,
          instructor,
          aircraft,
          reservationTime,
          status);
    }

    public State onEvent(Event.StudentUnavailable event) {
      return new State(
          reservationId,
          new Participant(student.participantId(), student.participantType(), student.timeSlotId(), ParticipantStatus.unavailable),
          instructor,
          aircraft,
          reservationTime,
          status);
    }

    public State onEvent(Event.InstructorWantsTimeSlot event) {
      return this;
    }

    public State onEvent(Event.InstructorAvailable event) {
      return new State(
          reservationId,
          student,
          new Participant(instructor.participantId(), instructor.participantType(), instructor.timeSlotId(), ParticipantStatus.available),
          aircraft,
          reservationTime,
          status);
    }

    public State onEvent(Event.InstructorUnavailable event) {
      return new State(
          reservationId,
          student,
          new Participant(instructor.participantId(), instructor.participantType(), instructor.timeSlotId(), ParticipantStatus.unavailable),
          aircraft,
          reservationTime,
          status);
    }

    public State onEvent(Event.AircraftWantsTimeSlot event) {
      return this;
    }

    public State onEvent(Event.AircraftAvailable event) {
      return new State(
          reservationId,
          student,
          instructor,
          new Participant(aircraft.participantId(), aircraft.participantType(), aircraft.timeSlotId(), ParticipantStatus.available),
          reservationTime,
          status);
    }

    public State onEvent(Event.AircraftUnavailable event) {
      return new State(
          reservationId,
          student,
          instructor,
          new Participant(aircraft.participantId(), aircraft.participantType(), aircraft.timeSlotId(), ParticipantStatus.unavailable),
          reservationTime,
          status);
    }

    public State onEvent(Event.ReservationConfirmed event) {
      return new State(
          reservationId,
          student,
          instructor,
          aircraft,
          reservationTime,
          Status.confirmed);
    }

    public State onEvent(Event.ReservationCancelled event) {
      return new State(
          reservationId,
          student,
          instructor,
          aircraft,
          reservationTime,
          Status.cancelled);
    }

    public State onEvent(Event.CancelledStudentReservation event) {
      return this;
    }

    public State onEvent(Event.CancelledInstructorReservation event) {
      return this;
    }

    public State onEvent(Event.CancelledAircraftReservation event) {
      return this;
    }
  }

  public sealed interface Command {
    public record CreateReservation(
        String reservationId,
        String studentId,
        String studentTimeSlotId,
        String instructorId,
        String instructorTimeSlotId,
        String aircraftId,
        String aircraftTimeSlotId,
        Instant reservationTime) implements Command {}

    public record StudentAvailable(
        String reservationId) implements Command {}

    public record StudentUnavailable(
        String reservationId) implements Command {}

    public record InstructorAvailable(
        String reservationId) implements Command {}

    public record InstructorUnavailable(
        String reservationId) implements Command {}

    public record AircraftAvailable(
        String reservationId) implements Command {}

    public record AircraftUnavailable(
        String reservationId) implements Command {}

    public record CancelReservation(
        String reservationId) implements Command {}
  }

  public sealed interface Event {
    public record ReservationCreated(
        String reservationId,
        String studentId,
        String studentTimeSlotId,
        String instructorId,
        String instructorTimeSlotId,
        String aircraftId,
        String aircraftTimeSlotId,
        Instant reservationTime,
        Status status) implements Event {}

    public record StudentWantsTimeSlot(
        String reservationId,
        String timeSlotId) implements Event {}

    public record StudentAvailable(
        String reservationId,
        Participant student) implements Event {}

    public record StudentUnavailable(
        String reservationId,
        Participant student) implements Event {}

    public record InstructorWantsTimeSlot(
        String reservationId,
        String timeSlotId) implements Event {}

    public record InstructorAvailable(
        String reservationId,
        Participant instructor) implements Event {}

    public record InstructorUnavailable(
        String reservationId,
        Participant instructor) implements Event {}

    public record AircraftWantsTimeSlot(
        String reservationId,
        String timeSlotId) implements Event {}

    public record AircraftAvailable(
        String reservationId,
        Participant aircraft) implements Event {}

    public record AircraftUnavailable(
        String reservationId,
        Participant aircraft) implements Event {}

    public record ReservationConfirmed(
        String reservationId) implements Event {}

    public record ReservationCancelled(
        String reservationId) implements Event {}

    public record CancelledStudentReservation(
        String studentTimeSlotId,
        String reservationId) implements Event {}

    public record CancelledInstructorReservation(
        String instructorTimeSlotId,
        String reservationId) implements Event {}

    public record CancelledAircraftReservation(
        String aircraftTimeSlotId,
        String reservationId) implements Event {}
  }

  public static String generateReservationId() {
    String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    StringBuilder sb = new StringBuilder();
    SecureRandom random = new SecureRandom();

    for (int i = 0; i < 6; i++) {
      sb.append(chars.charAt(random.nextInt(chars.length())));
    }
    return sb.toString();
  }
}
