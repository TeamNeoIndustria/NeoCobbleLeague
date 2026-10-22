package xyz.neonetwork.neocobbleleague.events;

import xyz.neonetwork.neocobbleleague.events.enums.EventEndReason;
import xyz.neonetwork.neocobbleleague.events.enums.SpawnMode;
import xyz.neonetwork.neocobbleleague.events.enums.EventType;
import xyz.neonetwork.neocobbleleague.events.records.LevelRange;
import xyz.neonetwork.neocobbleleague.events.records.SpawnPoint;
import xyz.neonetwork.neocobblelib.cobblemon.CobblemonSpecies;

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
    private final CobblemonSpecies species;
    private final LevelRange levelRange;

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
                    CobblemonSpecies species, int levelMin, int levelMax,
                    SpawnMode spawnMode, List<SpawnPoint> spawnPoints,
                    String announceMessage, String spawnMessage,
                    Map<EventEndReason, String> endMessages) {

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

        this.species = Objects.requireNonNull(species, "species");
        this.levelRange = new LevelRange(levelMin, levelMax);

        this.spawnMode = Objects.requireNonNull(spawnMode, "spawnMode");

        if (spawnPoints.isEmpty()) {
            throw new IllegalArgumentException("Event " + id + " needs at least one spawn point");
        }

        this.spawnPoints = List.copyOf(spawnPoints);

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

    public CobblemonSpecies getSpecies() {
        return species;
    }

    public LevelRange getLevelRange() {
        return levelRange;
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
