package xyz.neonetwork.neocobbleleague.events;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import xyz.neonetwork.neocobbleleague.NeoConstants;
import xyz.neonetwork.neocobbleleague.events.enums.EventStartResult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EventScheduler {
    private static final int RETRY_TICKS = 600;

    public record SchedulerSettings(boolean enabled, int minIntervalTicks, int maxIntervalTicks, int minPlayers, boolean onAtATime) {
        public static final SchedulerSettings DISABLED = new SchedulerSettings(false, 36000, 72000, 1, true);
    }

    private static volatile SchedulerSettings settings = SchedulerSettings.DISABLED;
    private static long nextRunAt = -1;

    private EventScheduler() {
    }

    public static void configure(JsonElement element) {
        settings = parse(element);
        nextRunAt = -1;

        if (settings.enabled()) {
            NeoConstants.LOGGER.info("Event scheduler on: every {}-{} seconds, needs {} player(s).",
                    settings.minIntervalTicks() / 20, settings.maxIntervalTicks() / 20, settings.minPlayers());
        }
    }

    public static void reset() {
        nextRunAt = -1;
    }

    public static void tick(MinecraftServer server) {
        SchedulerSettings current = settings;

        if (!current.enabled()) {
            return;
        }

        if (current.onAtATime() && !EventManager.getActive().isEmpty()) {
            nextRunAt = -1;
            return;
        }

        long now = server.overworld().getGameTime();
        RandomSource random = server.overworld().getRandom();

        if (nextRunAt < 0) {
            nextRunAt = now + randomInterval(random, current);
            return;
        }

        if (now < nextRunAt) {
            return;
        }

        if (server.getPlayerCount() < current.minPlayers()) {
            nextRunAt = now + RETRY_TICKS;
            return;
        }

        nextRunAt = now + randomInterval(random, current);
        startRandomEvent(server, random);

    }

    private static int randomInterval(RandomSource random, SchedulerSettings settings) {
        return random.nextIntBetweenInclusive(settings.minIntervalTicks(), settings.maxIntervalTicks());
    }

    private static void startRandomEvent(MinecraftServer server, RandomSource random) {
        List<EventDefinition> candidates = EventManager.getDefinitions().stream()
                .filter(definition -> definition.isEnabled() && definition.getWeight() > 0)
                .filter(definition -> EventManager.findActive(definition.getId()).isEmpty())
                .filter(definition -> EventManager.getCooldownRemaining(server, definition.getId()) == 0)
                .collect(Collectors.toCollection(ArrayList::new));

        while (!candidates.isEmpty()) {
            EventDefinition pick = pickWeighted(candidates, random);

            if (EventManager.startEvent(server, pick.getId(), false) == EventStartResult.STARTED) {
                NeoConstants.LOGGER.info("Scheduler started event '{}'.", pick.getId());
                return;
            }

            candidates.remove(pick);
        }
    }

    private static EventDefinition pickWeighted(List<EventDefinition> candidates, RandomSource random) {
        int total = candidates.stream().mapToInt(EventDefinition::getWeight).sum();
        int roll = random.nextInt(total);

        for (EventDefinition definition : candidates) {
            roll -= definition.getWeight();

            if (roll < 0) {
                return definition;
            }
        }

        return candidates.getLast();
    }

    private static SchedulerSettings parse(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return SchedulerSettings.DISABLED;
        }

        JsonObject json = element.getAsJsonObject();

        try {
            boolean enabled = json.has("Enabled") && json.get("Enabled").getAsBoolean();
            double minSeconds = json.has("MinIntervalSeconds") ? json.get("MinIntervalSeconds").getAsDouble() : 1800;
            double maxSeconds = json.has("MaxIntervalSeconds") ? json.get("MaxIntervalSeconds").getAsDouble() : 3600;
            int minPlayers = json.has("MinPlayers") ? json.get("MinPlayers").getAsInt() : 1;
            boolean oneAtATime = !json.has("OneAtATime") || json.get("OneAtATime").getAsBoolean();

            if (minSeconds < 1 || maxSeconds < minSeconds || maxSeconds > 1_000_000 || minSeconds < 0) {
                throw new IllegalArgumentException("MinIntervalSeconds must be at least 1, MaxIntervalSeconds at least MinIntervalSeconds (and under 1,000,000), MinPlayers 0 or more.");
            }

            return new SchedulerSettings(enabled,
                    (int) Math.round(minSeconds * 20), (int) Math.round(maxSeconds * 20), minPlayers, oneAtATime);
        } catch (RuntimeException exception) {
            NeoConstants.LOGGER.error("Invalid Scheduler settings, scheduling stays off: {}", exception.getMessage());
            return SchedulerSettings.DISABLED;
        }
    }

}
