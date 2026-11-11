package com.legacyoftheforce.speeder;

import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Development source set only, excluded from the distributable JAR. Not a crafting path or proof of payment. */
@EventBusSubscriber(modid = "legacyoftheforce")
public final class SpeederValidationCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lotfvalidation").requires(source -> source.hasPermission(2))
                .then(Commands.literal("speeder").executes(context -> {
                    var actor = context.getSource().getPlayerOrException(); var level = actor.serverLevel();
                    var forward = actor.getDirection().getNormal(); var at = actor.position().add(forward.getX() * 3, 2, forward.getZ() * 3);
                    var blueprint = SpeederBlueprint.blank(UUID.randomUUID(), actor.getUUID()).withModules(SpeederContent.starterModules());
                    var vehicle = new SpeederEntity(SpeederContent.VEHICLE.get(), level);
                    vehicle.initialize(VehicleState.create(UUID.randomUUID(), actor.getUUID(), blueprint,
                            new VehicleState.Pose(level.dimension().location(), at.x, at.y, at.z), 1000));
                    if (!level.noCollision(vehicle) || !level.getWorldBorder().isWithinBounds(vehicle.getBoundingBox()) || !level.addFreshEntity(vehicle)) {
                        context.getSource().sendFailure(Component.literal("Validation fixture: spawn area obstructed")); return 0;
                    }
                    actor.startRiding(vehicle);
                    context.getSource().sendSuccess(() -> Component.literal("DEVELOPMENT FIXTURE: vehicle spawned; assembly payment NOT tested. UUID " + vehicle.getUUID()), false);
                    return 1;
                })));
    }
    private SpeederValidationCommands() {}
}
