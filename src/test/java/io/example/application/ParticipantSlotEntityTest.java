package io.example.application;

import static org.junit.jupiter.api.Assertions.*;

import akka.Done;
import akka.javasdk.testkit.EventSourcedTestKit;
import io.example.application.ParticipantSlotEntity.Commands;
import io.example.application.ParticipantSlotEntity.State;
import io.example.domain.Participant.ParticipantType;
import org.junit.jupiter.api.Test;

public class ParticipantSlotEntityTest {

    private static final String SLOT_ID         = "slot-1";
    private static final String PARTICIPANT_ID  = "p-1";
    private static final ParticipantType TYPE   = ParticipantType.STUDENT;
    private static final String ENTITY_ID       = SLOT_ID + "-" + PARTICIPANT_ID;

    private EventSourcedTestKit<State, ParticipantSlotEntity.Event, ParticipantSlotEntity> newKit() {
        return EventSourcedTestKit.of(ENTITY_ID, () -> new ParticipantSlotEntity());
    }

    @Test
    void markAvailableSetsStatusAvailable() {
        var kit = newKit();
        var result = kit.method(ParticipantSlotEntity::markAvailable)
                .invoke(new Commands.MarkAvailable(SLOT_ID, PARTICIPANT_ID, TYPE));
        assertEquals(Done.done(), result.getReply());
        assertEquals("AVAILABLE", kit.getState().status());
    }

    @Test
    void unmarkAvailableSetsStatusNull() {
        var kit = newKit();
        kit.method(ParticipantSlotEntity::markAvailable)
                .invoke(new Commands.MarkAvailable(SLOT_ID, PARTICIPANT_ID, TYPE));
        var result = kit.method(ParticipantSlotEntity::unmarkAvailable)
                .invoke(new Commands.UnmarkAvailable(SLOT_ID, PARTICIPANT_ID, TYPE));
        assertEquals(Done.done(), result.getReply());
        assertNull(kit.getState().status());
    }

    @Test
    void bookSetsStatusBooked() {
        var kit = newKit();
        kit.method(ParticipantSlotEntity::markAvailable)
                .invoke(new Commands.MarkAvailable(SLOT_ID, PARTICIPANT_ID, TYPE));
        var result = kit.method(ParticipantSlotEntity::book)
                .invoke(new Commands.Book(SLOT_ID, PARTICIPANT_ID, TYPE, "booking-1"));
        assertEquals(Done.done(), result.getReply());
        assertEquals("BOOKED", kit.getState().status());
    }

    @Test
    void cancelSetsStatusNull() {
        var kit = newKit();
        kit.method(ParticipantSlotEntity::markAvailable)
                .invoke(new Commands.MarkAvailable(SLOT_ID, PARTICIPANT_ID, TYPE));
        kit.method(ParticipantSlotEntity::book)
                .invoke(new Commands.Book(SLOT_ID, PARTICIPANT_ID, TYPE, "booking-1"));
        var result = kit.method(ParticipantSlotEntity::cancel)
                .invoke(new Commands.Cancel(SLOT_ID, PARTICIPANT_ID, TYPE, "booking-1"));
        assertEquals(Done.done(), result.getReply());
        assertNull(kit.getState().status());
    }
}
