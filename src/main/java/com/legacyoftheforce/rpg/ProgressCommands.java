package com.legacyoftheforce.rpg;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class ProgressCommands {
    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lotf")
                .then(Commands.literal("profile").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    var snapshot = player.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot();
                    ProgressEvents.sync(player);
                    context.getSource().sendSuccess(() -> Component.translatable("command.legacyoftheforce.profile",
                            snapshot.level(), snapshot.xpInLevel(), snapshot.forceEnergy(),
                            snapshot.maxForceEnergy(), snapshot.alignmentTenths() / 10.0), false);
                    return 1;
                }))
                .then(Commands.literal("debug").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("spend_energy")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.001, 1_000_000))
                                        .executes(context -> {
                                            var player = context.getSource().getPlayerOrException();
                                            var progress = player.getData(ProgressAttachments.PLAYER_PROGRESS);
                                            boolean spent = progress.trySpendEnergy(DoubleArgumentType.getDouble(context, "amount"));
                                            ProgressEvents.sync(player);
                                            context.getSource().sendSuccess(() -> Component.translatable(spent
                                                    ? "command.legacyoftheforce.energy_spent"
                                                    : "command.legacyoftheforce.energy_refused"), false);
                                            return spent ? 1 : 0;
                                        })))));
    }
}
