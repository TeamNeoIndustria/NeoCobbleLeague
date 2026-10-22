package xyz.neonetwork.neocobbleleague.events;

import xyz.neonetwork.neocobbleleague.events.enums.EventEndReason;
import xyz.neonetwork.neocobbleleague.events.enums.EventPhase;
import xyz.neonetwork.neocobbleleague.events.enums.EventType;

import java.util.UUID;

public class EventInstance {
    // Event Info
    public String eventName;
    public String eventId;

    // Event Battle
    private EventType type;
    private EventPhase phase;
    private EventEndReason endReason;

    public EventInstance(String eventName, EventType type, EventPhase phase) {
        this.eventName = eventName;
        this.eventId = UUID.randomUUID().toString();

        this.type = type;
        this.phase = phase;
        this.endReason = null;
    }

    public String getEventName() {
        return eventName;
    }

    public String getEventId() {
        return eventId;
    }

    public EventType getType() {
        return type;
    }

    public EventPhase getPhase() {
        return phase;
    }

    public EventEndReason getEndReason() {
        return endReason;
    }
}
