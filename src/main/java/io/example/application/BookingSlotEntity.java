package io.example.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.example.domain.BookingEvent;
import io.example.domain.BookingEvent.ParticipantBooked;
import io.example.domain.BookingEvent.ParticipantCanceled;
import io.example.domain.BookingEvent.ParticipantMarkedAvailable;
import io.example.domain.BookingEvent.ParticipantUnmarkedAvailable;
import io.example.domain.Participant;
import io.example.domain.Participant.ParticipantType;
import io.example.domain.Timeslot;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "booking-slot")
public class BookingSlotEntity extends EventSourcedEntity<Timeslot, BookingEvent> {

    private final String entityId;
    private static final Logger logger = LoggerFactory.getLogger(BookingSlotEntity.class);

    public BookingSlotEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    public Effect<Done> markSlotAvailable(Command.MarkSlotAvailable cmd) {
        Participant p = cmd.participant();
        if (currentState().isWaiting(p.id(), p.participantType())) {
            return effects().error("Participant already available for this slot");
        }
        if (!currentState().findBooking(p.id()).isEmpty()) {
            return effects().error("Participant already booked for this slot");
        }
        return effects()
                .persist(new ParticipantMarkedAvailable(entityId, p.id(), p.participantType()))
                .thenReply(__ -> Done.done());
    }

    public Effect<Done> unmarkSlotAvailable(Command.UnmarkSlotAvailable cmd) {
        Participant p = cmd.participant();
        return effects()
                .persist(new ParticipantUnmarkedAvailable(entityId, p.id(), p.participantType()))
                .thenReply(__ -> Done.done());
    }

    // NOTE: booking a slot should produce 3
    // `ParticipantBooked` events
    public Effect<Done> bookSlot(Command.BookReservation cmd) {
        try {
            String[] parts = entityId.split("-");
            int year  = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day   = Integer.parseInt(parts[2]);
            int hour  = Integer.parseInt(parts[3]);
            LocalDateTime slotTime = LocalDateTime.of(year, month, day, hour, 0);
            if (slotTime.isBefore(LocalDateTime.now())) {
                return effects().error("Slot is in the past");
            }
        } catch (Exception e) {
            return effects().error("Invalid slot ID — expected format YYYY-MM-DD-HH: " + entityId);
        }
        if (!currentState().isBookable(cmd.studentId(), cmd.aircraftId(), cmd.instructorId())) {
            return effects().error("Not all participants are available for this slot");
        }
        return effects().persist(
                new ParticipantBooked(entityId, cmd.studentId(), ParticipantType.STUDENT, cmd.bookingId()),
                new ParticipantBooked(entityId, cmd.aircraftId(), ParticipantType.AIRCRAFT, cmd.bookingId()),
                new ParticipantBooked(entityId, cmd.instructorId(), ParticipantType.INSTRUCTOR, cmd.bookingId())
        ).thenReply(__ -> Done.done());
    }

    // NOTE: canceling a booking should produce 3
    // `ParticipantCanceled` events
    public Effect<Done> cancelBooking(String bookingId) {
        List<Timeslot.Booking> bookings = currentState().findBooking(bookingId);
        if (bookings.isEmpty()) {
            return effects().error("Booking not found: " + bookingId);
        }
        List<BookingEvent> events = bookings.stream()
                .map(b -> (BookingEvent) new ParticipantCanceled(
                        entityId, b.participant().id(), b.participant().participantType(), bookingId))
                .toList();
        return effects().persistAll(events).thenReply(__ -> Done.done());
    }

    public ReadOnlyEffect<Timeslot> getSlot() {
        return effects().reply(currentState());
    }

    @Override
    public Timeslot emptyState() {
        return new Timeslot(
                // NOTE: these are just estimates for capacity based on it being a sample
                HashSet.newHashSet(10), HashSet.newHashSet(10));
    }

    @Override
    public Timeslot applyEvent(BookingEvent event) {
        return switch (event) {
            case BookingEvent.ParticipantMarkedAvailable e -> currentState().reserve(e);
            case BookingEvent.ParticipantUnmarkedAvailable e -> currentState().unreserve(e);
            case BookingEvent.ParticipantBooked e -> currentState().book(e);
            case BookingEvent.ParticipantCanceled e -> currentState().cancelBooking(e.bookingId());
        };
    }

    public sealed interface Command {
        record MarkSlotAvailable(Participant participant) implements Command {
        }

        record UnmarkSlotAvailable(Participant participant) implements Command {
        }

        record BookReservation(
                String studentId, String aircraftId, String instructorId, String bookingId)
                implements Command {
        }
    }
}
