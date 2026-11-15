package com.legacyoftheforce.planets;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Dedicated-server native transfer/restart probe. No client rendering or real socket: those gates stay open. */
@EventBusSubscriber(modid = "legacyoftheforce")
public final class TravelValidationCommands {
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        for (String stage : List.of("start", "outbound", "away", "return", "home")) event.getDispatcher().register(Commands.literal("lotftravel" + stage)
                .requires(source -> source.hasPermission(4) && source.getEntity() == null).executes(context -> {
                    var server = context.getSource().getServer(); var root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                    if (!root.getParent().getFileName().toString().equals("run-smoke")) throw new IllegalStateException("Use isolated run-smoke world");
                    try { run(server, root, stage); }
                    catch (Exception failure) { LegacyOfTheForce.LOGGER.error("TRAVEL_VALIDATION_FAILED stage=" + stage, failure); context.getSource().sendFailure(Component.literal(failure.toString())); return 0; }
                    context.getSource().sendSuccess(() -> Component.literal("TRAVEL_" + stage.toUpperCase(Locale.ROOT) + "_PASS"), false); return 1;
                }));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static ServerPlayer join(MinecraftServer server, UUID id) {
        var existing = server.getPlayerList().getPlayer(id); if (existing != null) return existing;
        var cookie = CommonListenerCookie.createInitial(new GameProfile(id, "TravelProbe"), false);
        var actor = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, actor, cookie); return actor;
    }
    private static Path journal(MinecraftServer server, UUID owner) { return server.getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/travel/" + owner + ".nbt"); }
    private static TravelTransaction receipt(ServerPlayer actor) throws Exception { return new TravelTransaction(AtomicNbtFile.read(journal(actor.server, actor.getUUID()), 262144)); }
    private static void menu(ServerPlayer actor, BlockPos terminal, int button) {
        var menu = new GalacticTerminalMenu(71, actor.getInventory(), ContainerLevelAccess.create(actor.level(), terminal)); actor.containerMenu = menu; menu.broadcastChanges();
        require(menu.stillValid(actor) && menu.clickMenuButton(actor, button), "Real server menu must accept button " + button + ": " + menu.status());
    }
    private static void inventoryMatches(ServerPlayer actor, CompoundTag expected) throws Exception {
        var actual = actor.getInventory().save(new ListTag()); var nativePlayer = new VerifiedPlayerSaveBarrier().read(actor);
        require(actual.equals(expected.get("Inventory")) && actual.equals(nativePlayer.get("Inventory")), "Live and native inventory must match expected payment exactly");
        require(receipt(actor).view().phase() == TravelTransaction.Phase.DONE, "Native travel journal must be DONE");
    }
    private static void run(MinecraftServer server, Path root, String stage) throws Exception {
        var path = root.resolve("validation/travel-proof.nbt");
        if (stage.equals("start")) {
            require(!Files.exists(path), "Do not overwrite existing travel proof"); require(server.getLevel(PlanetContent.ILUM) != null, "Actual Ilum dimension required");
            var actor = join(server, UUID.randomUUID()); actor.setGameMode(GameType.SURVIVAL);
            var terminal = new BlockPos(600, 240, 600);
            for (var pos : BlockPos.betweenClosed(terminal.offset(-2, -1, -3), terminal.offset(2, 2, 2))) require(actor.level().getBlockState(pos).isAir(), "Fixture requires untouched air");
            for (int x = -2; x <= 2; x++) for (int z = -3; z <= 2; z++) actor.level().setBlockAndUpdate(terminal.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            actor.level().setBlockAndUpdate(terminal, PlanetContent.TERMINAL.get().defaultBlockState());
            actor.teleportTo(600.5, 240, 598.5); actor.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            actor.getInventory().clearContent(); actor.getInventory().setItem(0, new ItemStack(PlanetContent.COORDINATES.get()));
            actor.getInventory().setItem(1, new ItemStack(PlanetContent.TERMINAL_ITEM.get()));
            actor.getInventory().setItem(2, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(PlanetContent.id("speeder_energy_cell")), 2));
            actor.getInventory().setItem(3, new ItemStack(Items.DIAMOND, 7));
            menu(actor, terminal, 2); require(actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow().ilumKnown(), "Chart must unlock Ilum");
            require(actor.getInventory().countItem(PlanetContent.COORDINATES.get()) == 0, "One actual chart consumed");
            var proof = new CompoundTag(); proof.putInt("schemaVersion", 1); proof.putUUID("owner", actor.getUUID()); proof.putString("stage", "START");
            proof.putIntArray("terminal", new int[]{600, 240, 600}); proof.put("home", TravelPoint.at(actor).tag());
            AtomicNbtFile.write(path, proof, 262144); new VerifiedChunkSaveBarrier().persist(actor.serverLevel(), Set.of(terminal)); return;
        }
        var proof = AtomicNbtFile.read(path, 262144); var actor = join(server, proof.getUUID("owner"));
        if (stage.equals("outbound")) {
            require(proof.getString("stage").equals("START"), "Expected start proof"); var xyz = proof.getIntArray("terminal");
            menu(actor, new BlockPos(xyz[0], xyz[1], xyz[2]), 0);
            require(actor.level().dimension().equals(PlanetContent.ILUM), "Real ServerPlayer must change to Ilum");
            var atlas = actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow(); require(atlas.returnCredit() && atlas.pending() == null && atlas.revision() == 2, "Arrival must activate prepaid return");
            var record = receipt(actor); require(record.view().cost() == 2, "Both configured legs must be paid once"); inventoryMatches(actor, record.activeImage());
            require(actor.getInventory().countItem(Items.DIAMOND) == 7 && actor.getInventory().countItem(PlanetContent.TERMINAL_ITEM.get()) == 1, "Cargo and portable terminal must survive transfer");
            proof.put("away", TravelPoint.at(actor).tag()); proof.put("awayInventory", actor.getInventory().save(new ListTag())); proof.putString("stage", "AWAY");
        } else if (stage.equals("away")) {
            require(proof.getString("stage").equals("AWAY"), "Expected away proof");
            require(actor.level().dimension().equals(PlanetContent.ILUM) && TravelPoint.at(actor).equals(TravelPoint.read(proof.getCompound("away"))), "Restart must restore real dimension and exact position");
            var expected = new CompoundTag(); expected.put("Inventory", proof.get("awayInventory").copy()); inventoryMatches(actor, expected);
            require(actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow().returnCredit(), "Prepaid return must survive restart");
            proof.putBoolean("awayRestartVerified", true);
        } else if (stage.equals("return")) {
            require(proof.getBoolean("awayRestartVerified"), "Verify away restart first");
            BlockPos position = null, support = null; net.minecraft.core.Direction face = null;
            int solids = 0, air = 0, reachable = 0, clear = 0;
            search: for (var clicked : BlockPos.betweenClosed(actor.blockPosition().offset(-2, -2, -2), actor.blockPosition().offset(2, 1, 2))) {
                if (actor.level().getBlockState(clicked).getCollisionShape(actor.level(), clicked).isEmpty()) continue;
                solids++;
                for (var direction : net.minecraft.core.Direction.values()) {
                    var candidate = clicked.relative(direction);
                    var candidateState = actor.level().getBlockState(candidate);
                    if (!candidateState.isAir() && !candidateState.is(Blocks.SNOW)) continue; air++;
                    if (!actor.canInteractWithBlock(clicked, 4) || !actor.canInteractWithBlock(candidate, 4)) continue; reachable++;
                    if (new net.minecraft.world.phys.AABB(candidate).intersects(actor.getBoundingBox())) continue; clear++;
                    position = candidate.immutable(); support = clicked.immutable(); face = direction; break search;
                }
            }
            require(position != null, "Find exposed face: solids=" + solids + " air=" + air + " reachable=" + reachable + " clear=" + clear + " actor=" + actor.position() + " block=" + actor.blockPosition() + " box=" + actor.getBoundingBox());
            require(actor.getInventory().getItem(1).is(PlanetContent.TERMINAL_ITEM.get()), "Portable terminal must still be present");
            actor.getInventory().selected = 1;
            var hit = new net.minecraft.world.phys.BlockHitResult(support.getCenter().add(net.minecraft.world.phys.Vec3.atLowerCornerOf(face.getNormal()).scale(.5)), face, support, false);
            actor.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(actor, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
            require(actor.level().getBlockState(position).is(PlanetContent.TERMINAL.get()) && actor.getInventory().countItem(PlanetContent.TERMINAL_ITEM.get()) == 0, "Vanilla BlockItem must place and pay exactly one carried terminal");
            new VerifiedChunkSaveBarrier().persist(actor.serverLevel(), Set.of(position)); menu(actor, position, 1);
            require(actor.level().dimension().equals(Level.OVERWORLD) && TravelPoint.at(actor).equals(TravelPoint.read(proof.getCompound("home"))), "Real return must restore exact safe home");
            var atlas = actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow(); require(!atlas.returnCredit() && atlas.pending() == null && atlas.revision() == 3, "Return credit must be consumed once");
            inventoryMatches(actor, receipt(actor).activeImage()); require(actor.getInventory().countItem(Items.DIAMOND) == 7, "All cargo survives return");
            proof.put("homeInventory", actor.getInventory().save(new ListTag())); proof.putString("stage", "HOME");
        } else if (stage.equals("home")) {
            require(proof.getString("stage").equals("HOME"), "Expected home proof");
            require(actor.level().dimension().equals(Level.OVERWORLD) && TravelPoint.at(actor).equals(TravelPoint.read(proof.getCompound("home"))), "Second restart must preserve returned position");
            var expected = new CompoundTag(); expected.put("Inventory", proof.get("homeInventory").copy()); inventoryMatches(actor, expected);
            require(!actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow().returnCredit(), "Spent credit cannot reappear after restart"); proof.putBoolean("homeRestartVerified", true);
        }
        AtomicNbtFile.write(path, proof, 262144);
    }
}
