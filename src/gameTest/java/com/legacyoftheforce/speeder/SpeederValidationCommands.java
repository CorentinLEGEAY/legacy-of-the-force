package com.legacyoftheforce.speeder;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Development-only fixtures and bounded crash-injection pause. Never included in the distributable JAR. */
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
                }))
                .then(Commands.literal("assembly_pause")
                        .then(Commands.argument("garage", BlockPosArgument.blockPos())
                                .then(Commands.argument("checkpoint", StringArgumentType.word()).executes(context -> {
                                    var actor = context.getSource().getPlayerOrException();
                                    final AssemblyCoordinator.Checkpoint checkpoint;
                                    try { checkpoint = AssemblyCoordinator.Checkpoint.valueOf(StringArgumentType.getString(context, "checkpoint")); }
                                    catch (IllegalArgumentException invalid) { context.getSource().sendFailure(Component.literal("Unknown assembly checkpoint")); return 0; }
                                    var blueprint = actor.getMainHandItem().get(SpeederContent.BLUEPRINT.get());
                                    if (blueprint == null) { context.getSource().sendFailure(Component.literal("Hold the saved blueprint and supply real parts")); return 0; }
                                    var directory = actor.server.getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/speeder_assembly");
                                    var service = new SpeederAssemblyService(actor.server, directory, new VanillaProtectedWorldService(), reached -> {
                                        if (reached != checkpoint) return;
                                        LegacyOfTheForce.LOGGER.warn("VALIDATION_ASSEMBLY_PAUSED checkpoint={} owner={} timeoutSeconds=30", checkpoint, actor.getUUID());
                                        try { new CountDownLatch(1).await(30, TimeUnit.SECONDS); }
                                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Validation pause interrupted", interrupted); }
                                        LegacyOfTheForce.LOGGER.warn("VALIDATION_ASSEMBLY_PAUSE_EXPIRED checkpoint={} (not a process-kill proof)", checkpoint);
                                    });
                                    var result = service.build(actor, BlockPosArgument.getLoadedBlockPos(context, "garage"), blueprint);
                                    context.getSource().sendSuccess(() -> Component.literal("Validation assembly result: " + result), false); return result == SpeederAssemblyService.Result.BUILT ? 1 : 0;
                                })))));
    }
    private SpeederValidationCommands() {}
}
