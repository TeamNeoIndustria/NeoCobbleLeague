package xyz.neonetwork.neocobbleleague.events;

import net.minecraft.util.RandomSource;
import xyz.neonetwork.neocobbleleague.events.enums.EventEndReason;
import xyz.neonetwork.neocobbleleague.events.enums.EventPhase;
import xyz.neonetwork.neocobbleleague.events.records.SpawnPoint;
import xyz.neonetwork.neocobbleleague.events.specs.PokemonSpec;

import java.util.*;

public class EventInstance {
    private final UUID instanceId;
    private final EventDefinition definition;
    private final SpawnPoint spawnPoint;
    private final OptionalInt rolledLevel;

    private EventPhase phase;
    private EventEndReason endReason;
    private int ticksRemaining;
    private UUID entityUuid;
    private final Set<UUID> participants = new HashSet<>();

    public EventInstance(EventDefinition definition, RandomSource random) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.instanceId = UUID.randomUUID();

        // Pick the spawn point and roll the level once, so they survive a restart
        this.spawnPoint = definition.getSpawnPoints().get(random.nextInt(definition.getSpawnPoints().size()));

        if (definition.getSpec() instanceof PokemonSpec pokemonSpec) {
            this.rolledLevel = OptionalInt.of(pokemonSpec.levelRange().roll(random));
        } else {
            this.rolledLevel = OptionalInt.empty();
        }

        this.phase = EventPhase.PENDING;
        this.endReason = null;
        this.ticksRemaining = 0;
    }

    public boolean advance() {
        EventPhase next = switch (phase) {
            case PENDING -> EventPhase.ANNOUNCED;
            case ANNOUNCED -> EventPhase.ACTIVE;
            case ACTIVE, ENDED -> null;
        };

        if (next == null) {
            return false;
        }

        this.phase = next;
        this.ticksRemaining = (next == EventPhase.ANNOUNCED)
                ? definition.getCountdownTicks()
                : definition.getLifetimeTicks();

        return true;
    }

    public boolean end(EventEndReason reason) {
        Objects.requireNonNull(reason, "reason");

        if (phase == EventPhase.ENDED) {
            return false;
        }

        this.phase = EventPhase.ENDED;
        this.endReason = reason;
        this.ticksRemaining = 0;

        return true;
    }

    public boolean tick() {
        if (phase != EventPhase.ANNOUNCED && phase != EventPhase.ACTIVE) {
            return false;
        }

        if (ticksRemaining > 0) {
            ticksRemaining--;
        }

        return ticksRemaining <= 0;
    }

    public void setEntityUuid(UUID entityUuid) {
        this.entityUuid = entityUuid;
    }

    public void addParticipant(UUID playerUuid) {
        participants.add(playerUuid);
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public EventDefinition getDefinition() {
        return definition;
    }

    public String getDefinitionId() {
        return definition.getId();
    }

    public SpawnPoint getSpawnPoint() {
        return spawnPoint;
    }

    public OptionalInt getRolledLevel() {
        return rolledLevel;
    }

    public EventPhase getPhase() {
        return phase;
    }

    public Optional<EventEndReason> getEndReason() {
        return Optional.ofNullable(endReason);
    }

    public boolean isEnded() {
        return phase == EventPhase.ENDED;
    }

    public int getTicksRemaining() {
        return ticksRemaining;
    }

    public Optional<UUID> getEntityUuid() {
        return Optional.ofNullable(entityUuid);
    }

    public Set<UUID> getParticipants() {
        return Collections.unmodifiableSet(participants);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EventInstance that && instanceId.equals(that.instanceId);
    }

    @Override
    public int hashCode() {
        return instanceId.hashCode();
    }
}