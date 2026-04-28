package io.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.example.application.ParticipantSlotEntity.Commands;
import io.example.domain.BookingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This class is responsible for consuming events from the booking
// slot entity and turning those into command calls on the
// participant slot entity
@Component(id = "booking-slot-consumer")
@Consume.FromEventSourcedEntity(BookingSlotEntity.class)
public class SlotToParticipantConsumer extends Consumer {

    private final ComponentClient client;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public SlotToParticipantConsumer(ComponentClient client) {
        this.client = client;
    }

    public Effect onEvent(BookingEvent event) {
        String id = participantSlotId(event);
        return switch (event) {
            case BookingEvent.ParticipantMarkedAvailable e -> {
                logger.info("Marking participant {} available for slot {}", e.participantId(), e.slotId());
                client.forEventSourcedEntity(id)
                        .method(ParticipantSlotEntity::markAvailable)
                        .invoke(new Commands.MarkAvailable(e.slotId(), e.participantId(), e.participantType()));
                yield effects().done();
            }
            case BookingEvent.ParticipantUnmarkedAvailable e -> {
                logger.info("Unmarking participant {} available for slot {}", e.participantId(), e.slotId());
                client.forEventSourcedEntity(id)
                        .method(ParticipantSlotEntity::unmarkAvailable)
                        .invoke(new Commands.UnmarkAvailable(e.slotId(), e.participantId(), e.participantType()));
                yield effects().done();
            }
            case BookingEvent.ParticipantBooked e -> {
                logger.info("Booking participant {} for slot {}", e.participantId(), e.slotId());
                client.forEventSourcedEntity(id)
                        .method(ParticipantSlotEntity::book)
                        .invoke(new Commands.Book(e.slotId(), e.participantId(), e.participantType(), e.bookingId()));
                yield effects().done();
            }
            case BookingEvent.ParticipantCanceled e -> {
                logger.info("Canceling participant {} for slot {}", e.participantId(), e.slotId());
                client.forEventSourcedEntity(id)
                        .method(ParticipantSlotEntity::cancel)
                        .invoke(new Commands.Cancel(e.slotId(), e.participantId(), e.participantType(), e.bookingId()));
                yield effects().done();
            }
        };
    }

    // Participant slots are keyed by a derived key made up of
    // {slotId}-{participantId}
    // We don't need the participant type here because the participant IDs
    // should always be unique/UUIDs
    private String participantSlotId(BookingEvent event) {
        return switch (event) {
            case BookingEvent.ParticipantBooked evt -> evt.slotId() + "-" + evt.participantId();
            case BookingEvent.ParticipantUnmarkedAvailable evt ->
                evt.slotId() + "-" + evt.participantId();
            case BookingEvent.ParticipantMarkedAvailable evt -> evt.slotId() + "-" + evt.participantId();
            case BookingEvent.ParticipantCanceled evt -> evt.slotId() + "-" + evt.participantId();
        };
    }
}
