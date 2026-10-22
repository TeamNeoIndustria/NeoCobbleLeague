package xyz.neonetwork.neocobbleleague.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class MainCommand {
    public static String commandName = "neocobbleleague";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal(commandName)
                        .then(
                                Commands.literal("ping")
                                        .requires(source -> source.hasPermission(2))
                                        .executes(MainCommand::pingOption)
                        )
        );
    }

    public static int pingOption(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer sender = context.getSource().getPlayer();

        if (sender == null) {
            return 0;
        }

        sender.sendSystemMessage(Component.literal("Pong!"));

        return 1;
    }
}
