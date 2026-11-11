package com.legacyoftheforce.client;

import com.legacyoftheforce.core.network.DiagnosticRequest;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Read-only protocol probe for the reproducible multiplayer acceptance procedure. */
public final class DiagnosticCommands {
    public void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lotf_probe")
                .then(Commands.argument("sequence", LongArgumentType.longArg()).executes(context -> {
                    PacketDistributor.sendToServer(new DiagnosticRequest(LongArgumentType.getLong(context, "sequence")));
                    return 1;
                })));
    }
}
