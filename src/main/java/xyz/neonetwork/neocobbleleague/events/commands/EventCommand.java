package xyz.neonetwork.neocobbleleague.events.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sun.jdi.connect.Connector;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.Pack;
import xyz.neonetwork.neocobbleleague.NeoConstants;
import xyz.neonetwork.neocobbleleague.common.commands.MainCommand;
import xyz.neonetwork.neocobbleleague.events.EventDefinition;
import xyz.neonetwork.neocobbleleague.events.EventManager;
import xyz.neonetwork.neocobbleleague.events.json.EventDefinitionJson;

public class EventCommand {
    public static String commandName = "leagueevent";

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
                                        .then(Commands.argument("eventId", StringArgumentType.string())
                                        .then(Commands.argument("force", BoolArgumentType.bool())
                                                .executes(EventCommand::startOption)))

                        )
                        .then(
                                Commands.literal("stop")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("eventId", StringArgumentType.string())
                                                .executes(EventCommand::stopOption))
                        )
                        .then(
                                Commands.literal("skip")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("eventId", StringArgumentType.string())
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
        ServerPlayer sender = context.getSource().getPlayer();

        if (sender != null) {
            sender.sendSystemMessage(Component.literal("Attempting to reload events..."));
        }

        EventManager.loadDefinitions(NeoConstants.EVENTS_CONFIG);

        if (sender != null) {
            EventManager.getDefinitions().forEach(eventDefinition -> {
                sender.sendSystemMessage(Component.literal("-> Event loaded: " + eventDefinition.getId()));
            });
        }
        return 1;
    }

    public static int startOption(CommandContext<CommandSourceStack> context) {
        ServerPlayer sender = context.getSource().getPlayer();

        if (sender == null) {
            NeoConstants.LOGGER.error("Unknown Sender for EventCommand::startOption.");
            return 0;
        }

        String eventId = StringArgumentType.getString(context, "eventId");
        boolean force = BoolArgumentType.getBool(context, "force");

        switch (EventManager.startEvent(context.getSource().getServer(), eventId, force)) {
            case UNKNOWN -> sender.sendSystemMessage(Component.literal("Unable to start " + eventId + " has it's either not loaded or doesn't exist."));
            case ALREADY_ACTIVE -> sender.sendSystemMessage(Component.literal(eventId + " has already been started."));
            case DISABLED -> sender.sendSystemMessage(Component.literal(eventId + " is currently disabled, unable to start."));
            case ON_COOLDOWN -> sender.sendSystemMessage(Component.literal(eventId + " is currently on cooldown, " +
                    EventManager.getCooldownRemaining(context.getSource().getServer(), eventId) + " seconds remaining."));
            case STARTED -> sender.sendSystemMessage(Component.literal(eventId + " has successfully started."));
        }

        return 1;
    }

    public static int stopOption(CommandContext<CommandSourceStack> context) {
        return 1;
    }

    public static int skipOption(CommandContext<CommandSourceStack> context) {
        return 1;
    }

    public static int listOption(CommandContext<CommandSourceStack> context) {
        return 1;
    }
}
