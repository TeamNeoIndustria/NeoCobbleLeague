package xyz.neonetwork.neocobbleleague.events;

import xyz.neonetwork.neocobbleleague.events.enums.EventEndReason;
import xyz.neonetwork.neocobbleleague.events.enums.SpawnMode;
import xyz.neonetwork.neocobbleleague.events.enums.EventType;
import xyz.neonetwork.neocobbleleague.events.records.SpawnPoint;
import xyz.neonetwork.neocobbleleague.events.specs.EventSpec;
import xyz.neonetwork.neocobbleleague.events.specs.PokemonSpec;
import xyz.neonetwork.neocobbleleague.events.specs.TrainerSpec;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class EventDefinition {
    // Info
    private final String displayName;
    private final String id;

    // Event
    private final EventType type;
    private final boolean enabled;

    // Timings
    private final int countdownTicks;
    private final int lifetimeTicks;
    private final int cooldownTicks;
    private final int weight;

    // Type Specifics
    private final EventSpec spec;

    // Spawns
    private final SpawnMode spawnMode;
    private final List<SpawnPoint> spawnPoints;

    // Messages
    private final String announceMessage;
    private final String spawnMessage;
    private final Map<EventEndReason, String> endMessages;

    public EventDefinition(String id, String displayName, boolean enabled,
                    EventType type,
                    int countdownTicks, int lifetimeTicks, int cooldownTicks, int weight,
                    EventSpec spec, SpawnMode spawnMode, List<SpawnPoint> spawnPoints,
                    String announceMessage, String spawnMessage,
                    Map<EventEndReason, String> endMessages) {
        if ((type == EventType.POKEMON && spec instanceof TrainerSpec) || (type == EventType.TRAINER && spec instanceof PokemonSpec)) {
            throw new IllegalArgumentException("Cannot have mismatching Type and Specs. [" + id + "]");
        }

        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.enabled = enabled;
        this.type = Objects.requireNonNull(type, "type");

        if (countdownTicks < 0 || lifetimeTicks <= 0 || cooldownTicks < 0 || weight < 0) {
            throw new IllegalArgumentException("Invalid timing values for event " + id);
        }

        this.countdownTicks = countdownTicks;
        this.lifetimeTicks = lifetimeTicks;
        this.cooldownTicks = cooldownTicks;
        this.weight = weight;



        this.spec = Objects.requireNonNull(spec, "spec");

        this.spawnMode = Objects.requireNonNull(spawnMode, "spawnMode");

        this.spawnPoints = List.copyOf(spawnPoints);

        if (spawnPoints.isEmpty()) {
            throw new IllegalArgumentException("Event " + id + " needs at least one spawn point");
        }

        this.announceMessage = Objects.requireNonNull(announceMessage, "announceMessage");
        this.spawnMessage = Objects.requireNonNull(spawnMessage, "spawnMessage");
        this.endMessages = Map.copyOf(endMessages);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public EventType getType() {
        return type;
    }

    public int getCountdownTicks() {
        return countdownTicks;
    }

    public int getLifetimeTicks() {
        return lifetimeTicks;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public int getWeight() {
        return weight;
    }

    public EventSpec getSpec() {
        return spec;
    }

    public SpawnMode getSpawnMode() {
        return spawnMode;
    }

    public List<SpawnPoint> getSpawnPoints() {
        return spawnPoints;
    }

    public String getAnnounceMessage() {
        return announceMessage;
    }

    public String getSpawnMessage() {
        return spawnMessage;
    }

    public Optional<String> getEndMessage(EventEndReason reason) {
        return Optional.ofNullable(this.endMessages.get(reason));
    }
}
