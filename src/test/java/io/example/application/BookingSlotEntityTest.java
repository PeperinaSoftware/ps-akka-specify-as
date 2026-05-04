package io.example.application;

import static org.junit.jupiter.api.Assertions.*;

import akka.Done;
import akka.javasdk.testkit.EventSourcedTestKit;
import io.example.application.BookingSlotEntity.Command;
import io.example.domain.BookingEvent;
import io.example.domain.Participant;
import io.example.domain.Participant.ParticipantType;
import io.example.domain.Timeslot;
import org.junit.jupiter.api.Test;

public class BookingSlotEntityTest {

    static final String FUTURE_SLOT = "2099-01-01-10";
    static final String PAST_SLOT   = "2020-01-01-10";

    private static final Participant STUDENT    = new Participant("s1", ParticipantType.STUDENT);
    private static final Participant INSTRUCTOR = new Participant("i1", ParticipantType.INSTRUCTOR);
    private static final Participant AIRCRAFT   = new Participant("a1", ParticipantType.AIRCRAFT);

    // --- applyEvent tests ---

    @Test
    void applyReserveAddsToAvailable() {
        var kit = EventSourcedTestKit.of(FUTURE_SLOT, ctx -> new BookingSlotEntity(ctx));
        kit.method(BookingSlotEntity::markSlotAvailable)
                .invoke(new Command.MarkSlotAvailable(STUDENT));
        assertTrue(kit.getState().available().contains(STUDENT));
    }

    @Test
    void applyUnreserveRemovesFromAvailable() {
        var kit = EventSourcedTestKit.of(FUTURE_SLOT, ctx -> new BookingSlotEntity(ctx));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(STUDENT));
        kit.method(BookingSlotEntity::unmarkSlotAvailable)
                .invoke(new Command.UnmarkSlotAvailable(STUDENT));
        assertFalse(kit.getState().available().contains(STUDENT));
    }

    @Test
    void applyBookMovesParticipantsToBookings() {
        var kit = EventSourcedTestKit.of(FUTURE_SLOT, ctx -> new BookingSlotEntity(ctx));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(STUDENT));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(INSTRUCTOR));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(AIRCRAFT));
        kit.method(BookingSlotEntity::bookSlot).invoke(new Command.BookReservation("s1", "a1", "i1", "b1"));
        Timeslot state = kit.getState();
        assertFalse(state.available().contains(STUDENT));
        assertFalse(state.available().contains(INSTRUCTOR));
        assertFalse(state.available().contains(AIRCRAFT));
        assertEquals(3, state.bookings().size());
    }

    @Test
    void applyCancelBookingRemovesBookings() {
        var kit = EventSourcedTestKit.of(FUTURE_SLOT, ctx -> new BookingSlotEntity(ctx));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(STUDENT));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(INSTRUCTOR));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(AIRCRAFT));
        kit.method(BookingSlotEntity::bookSlot).invoke(new Command.BookReservation("s1", "a1", "i1", "b1"));
        kit.method(BookingSlotEntity::cancelBooking).invoke("b1");
        assertEquals(0, kit.getState().bookings().size());
    }

    // --- markSlotAvailable command tests ---

    @Test
    void markSlotAvailableEmitsEvent() {
        var kit = EventSourcedTestKit.of(FUTURE_SLOT, ctx -> new BookingSlotEntity(ctx));
        var result = kit.method(BookingSlotEntity::markSlotAvailable)
                .invoke(new Command.MarkSlotAvailable(STUDENT));
        assertEquals(Done.done(), result.getReply());
        result.getNextEventOfType(BookingEvent.ParticipantMarkedAvailable.class);
    }

    @Test
    void markSlotAvailableRejectsDuplicate() {
        var kit = EventSourcedTestKit.of(FUTURE_SLOT, ctx -> new BookingSlotEntity(ctx));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(STUDENT));
        var result = kit.method(BookingSlotEntity::markSlotAvailable)
                .invoke(new Command.MarkSlotAvailable(STUDENT));
        assertTrue(result.isError());
    }

    @Test
    void unmarkSlotAvailableEmitsEvent() {
        var kit = EventSourcedTestKit.of(FUTURE_SLOT, ctx -> new BookingSlotEntity(ctx));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(STUDENT));
        var result = kit.method(BookingSlotEntity::unmarkSlotAvailable)
                .invoke(new Command.UnmarkSlotAvailable(STUDENT));
        assertEquals(Done.done(), result.getReply());
        result.getNextEventOfType(BookingEvent.ParticipantUnmarkedAvailable.class);
    }

    // --- bookSlot command tests ---

    @Test
    void bookSlotEmitsThreeEvents() {
        var kit = EventSourcedTestKit.of(FUTURE_SLOT, ctx -> new BookingSlotEntity(ctx));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(STUDENT));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(INSTRUCTOR));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(AIRCRAFT));
        var result = kit.method(BookingSlotEntity::bookSlot)
                .invoke(new Command.BookReservation("s1", "a1", "i1", "b1"));
        assertEquals(Done.done(), result.getReply());
        assertEquals(3, result.getAllEvents().size());
    }

    @Test
    void bookSlotRejectsMissingParticipant() {
        var kit = EventSourcedTestKit.of(FUTURE_SLOT, ctx -> new BookingSlotEntity(ctx));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(STUDENT));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(INSTRUCTOR));
        // aircraft not marked available
        var result = kit.method(BookingSlotEntity::bookSlot)
                .invoke(new Command.BookReservation("s1", "a1", "i1", "b1"));
        assertTrue(result.isError());
    }

    @Test
    void bookSlotRejectsPastSlot() {
        var kit = EventSourcedTestKit.of(PAST_SLOT, ctx -> new BookingSlotEntity(ctx));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(STUDENT));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(INSTRUCTOR));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(AIRCRAFT));
        var result = kit.method(BookingSlotEntity::bookSlot)
                .invoke(new Command.BookReservation("s1", "a1", "i1", "b1"));
        assertTrue(result.isError());
    }

    // --- cancelBooking command tests ---

    @Test
    void cancelBookingEmitsThreeEvents() {
        var kit = EventSourcedTestKit.of(FUTURE_SLOT, ctx -> new BookingSlotEntity(ctx));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(STUDENT));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(INSTRUCTOR));
        kit.method(BookingSlotEntity::markSlotAvailable).invoke(new Command.MarkSlotAvailable(AIRCRAFT));
        kit.method(BookingSlotEntity::bookSlot).invoke(new Command.BookReservation("s1", "a1", "i1", "b1"));
        var result = kit.method(BookingSlotEntity::cancelBooking).invoke("b1");
        assertEquals(Done.done(), result.getReply());
        assertEquals(3, result.getAllEvents().size());
    }

    @Test
    void cancelBookingRejectsUnknownBookingId() {
        var kit = EventSourcedTestKit.of(FUTURE_SLOT, ctx -> new BookingSlotEntity(ctx));
        var result = kit.method(BookingSlotEntity::cancelBooking).invoke("nonexistent");
        assertTrue(result.isError());
    }
}
