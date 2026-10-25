package xyz.neonetwork.neocobbleleague.events.commands;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xyz.neonetwork.neocobbleleague.NeoConstants;
import xyz.neonetwork.neocobbleleague.common.utils.TimeUtils;
import xyz.neonetwork.neocobbleleague.events.EventDefinition;
import xyz.neonetwork.neocobbleleague.events.EventInstance;
import xyz.neonetwork.neocobbleleague.events.EventManager;
import xyz.neonetwork.neocobbleleague.events.enums.EventStartResult;
import xyz.neonetwork.neocobblelib.cobblemon.CobblemonTrainerManager;
import xyz.neonetwork.neocobblelib.utils.NPCUtils;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public class EventCommand {
    public static String commandName = "leagueevent";
    private static final SuggestionProvider<CommandSourceStack> EVENT_IDS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    EventManager.getDefinitions().stream().map(EventDefinition::getId), builder);
    private static final SuggestionProvider<CommandSourceStack> ACTIVE_EVENT_IDS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    EventManager.getActive().stream().map(EventInstance::getDefinitionId), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal(commandName)
                        .then(
                                Commands.literal("reload")
                                        .requires(source -> source.hasPermission(2))
                                                .executes(EventCommand::reloadOption)
                        )
                        .then(
                                Commands.literal("start")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("eventId", StringArgumentType.word())
                                                .suggests(EVENT_IDS)
                                                .executes(context -> startOption(context, false))
                                                .then(Commands.literal("force")
                                                        .executes(context -> startOption(context, true))))
                        )
                        .then(
                                Commands.literal("stop")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("eventId", StringArgumentType.word())
                                                .suggests(ACTIVE_EVENT_IDS)
                                                .executes(EventCommand::stopOption))
                        )
                        .then(
                                Commands.literal("skip")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("eventId", StringArgumentType.word())
                                                .suggests(ACTIVE_EVENT_IDS)
                                                .executes(EventCommand::skipOption))
                        )
                        .then(
                                Commands.literal("list")
                                        .requires(source -> source.hasPermission(2))
                                                .executes(EventCommand::listOption)
                        )

        );
    }

    public static int reloadOption(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.literal("Attempting to reload events..."), false);

        boolean success = NeoConstants.EVENTS_CONFIG.loadConfig();

        if (!success) {
            source.sendFailure(Component.literal("Error loading Events Config. Check the server log for details."));
            return 0;
        }

        EventManager.loadDefinitions(NeoConstants.EVENTS_CONFIG);

        source.sendSuccess(() -> Component.literal("Loaded " + EventManager.getDefinitions().size() + " event(s):"), false);

        EventManager.getDefinitions().forEach(eventDefinition ->
                source.sendSuccess(() -> Component.literal("-> " + eventDefinition.getId()), false));

        return 1;
    }

    public static int startOption(CommandContext<CommandSourceStack> context, boolean force) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();

        String eventId = StringArgumentType.getString(context, "eventId");

        EventStartResult result = EventManager.startEvent(server, eventId, force);

        switch (result) {
            case UNKNOWN -> source.sendFailure(
                    Component.literal("Unable to start " + eventId + ", it's either not loaded or doesn't exist."));

            case ALREADY_ACTIVE -> source.sendFailure(
                    Component.literal(eventId + " has already been started."));

            case DISABLED -> source.sendFailure(
                    Component.literal(eventId + " is currently disabled. Use force to start it anyway."));

            case ON_COOLDOWN -> source.sendFailure(
                    Component.literal(eventId + " is on cooldown, "
                            + TimeUtils.formatTicks(EventManager.getCooldownRemaining(server, eventId))
                            + " remaining. Use force to start it anyway."));

            case NO_VALID_SPAWN -> source.sendFailure(
                    Component.literal("Unable to find valid spawn point for " + eventId + ", please check console logs."));

            case STARTED -> source.sendSuccess(
                    () -> Component.literal(eventId + " has successfully started."), true);
        }

        return result == EventStartResult.STARTED ? 1 : 0;
    }

    public static int stopOption(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        String eventId = StringArgumentType.getString(context, "eventId");
        boolean success = EventManager.stopEvent(server, eventId);

        if (success) {
            context.getSource().sendSuccess(() -> Component.literal("Successfully stopped event: " + eventId), false);
        } else {
            context.getSource().sendFailure(Component.literal("Failed to stop event: " + eventId));
        }

        return success ? 1 : 0;
    }

    public static int skipOption(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        String eventId = StringArgumentType.getString(context, "eventId");
        boolean success = EventManager.skipCountdown(server, eventId);

        if (success) {
            context.getSource().sendSuccess(() -> Component.literal("Successfully skipped event: " + eventId), false);
        } else {
            context.getSource().sendFailure(Component.literal("Failed to skip event: " + eventId));
        }

        return success ? 1 : 0;
    }

    public static int listOption(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Collection<EventDefinition> definitions = EventManager.getDefinitions();

        source.sendSuccess(() -> Component.literal("Loaded Events (" + definitions.size() + "):"), false);

        for (EventDefinition definition : definitions) {
            String status = EventManager.findActive(definition.getId())
                    .map(instance -> "running (" + instance.getPhase() + ", "
                            + TimeUtils.formatTicks(instance.getTicksRemaining()) + " left)")
                    .orElseGet(() -> {
                        long cooldown = EventManager.getCooldownRemaining(server, definition.getId());
                        return cooldown > 0 ? "cooldown (" + TimeUtils.formatTicks(cooldown) + " left)" : "idle";
                    });

            source.sendSuccess(() -> Component.literal("-> " + definition.getId()
                    + " [" + definition.getType() + ", " + (definition.isEnabled() ? "enabled" : "disabled") + "] "
                    + status), false);
        }

        return 1;
    }
}
