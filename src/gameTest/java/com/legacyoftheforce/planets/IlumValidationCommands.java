package com.legacyoftheforce.planets;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.lightsaber.*;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Native dedicated-server validation, console only, isolated smoke worlds only; excluded from the JAR. */
@EventBusSubscriber(modid = "legacyoftheforce")
public final class IlumValidationCommands {
    private static final List<String> SITES = List.of("ilum_crystal_mine", "ilum_jedi_temple", "ilum_sith_sanctum", "ilum_ruins");
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        for (boolean prepare : new boolean[]{true, false}) event.getDispatcher().register(Commands.literal(prepare ? "lotfilumprepare" : "lotfilumverify")
                .requires(source -> source.hasPermission(4) && source.getEntity() == null).executes(context -> {
                    var server = context.getSource().getServer(); var root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                    if (!root.getParent().getFileName().toString().equals("run-smoke")) throw new IllegalStateException("Use isolated run-smoke world");
                    var level = server.getLevel(PlanetContent.ILUM); require(level != null, "Ilum dimension must be loaded by the real dedicated server");
                    try {
                        var file = root.resolve("validation/ilum-proof.nbt");
                        if (prepare) { require(!Files.exists(file), "Existing proof must be verified, never overwritten"); prepare(level, file); }
                        else verify(level, file);
                    } catch (Exception failure) { LegacyOfTheForce.LOGGER.error("ILUM_VALIDATION_FAILED", failure); context.getSource().sendFailure(Component.literal(failure.toString())); return 0; }
                    context.getSource().sendSuccess(() -> Component.literal(prepare ? "ILUM_PREPARE_PASS" : "ILUM_RESTART_VERIFY_PASS"), false); return 1;
                }));
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static BlockPos locate(ServerLevel level, String name) {
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var holder = registry.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, PlanetContent.id(name)));
        var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder), BlockPos.ZERO, 64, false);
        require(found != null, "Cannot locate " + name); return found.getFirst();
    }
    private static StructureStart start(ServerLevel level, String name, BlockPos located) {
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(PlanetContent.id(name));
        var chunk = level.getChunk(located.getX() >> 4, located.getZ() >> 4); var start = chunk.getAllStarts().get(structure);
        require(start != null && start.isValid() && start.getPieces().size() == 1, "Missing generated site: " + name);
        int x = chunk.getPos().getMaxBlockX(), z = chunk.getPos().getMaxBlockZ();
        var surface = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
        require(level.getBiome(surface).is(ResourceKey.create(Registries.BIOME, PlanetContent.id("ilum_glacial_fields"))) && level.getBiome(surface).value().getBaseTemperature() < 0, "Real generated biome must be Ilum and cold");
        var ice = level.getBlockState(surface); require(ice.is(Blocks.SNOW_BLOCK) || ice.is(Blocks.SNOW) || ice.is(Blocks.ICE) || ice.is(Blocks.PACKED_ICE) || ice.is(Blocks.BLUE_ICE), "Frost feature must actually cover natural terrain");
        return start;
    }
    private static String hash(ServerLevel level, BoundingBox box) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        for (var pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
            digest.update((level.getBlockState(pos).toString() + "\n").getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void prepare(ServerLevel level, Path file) throws Exception {
        var proof = new CompoundTag(); proof.putInt("schemaVersion", 1); proof.putLong("seed", level.getSeed()); var sites = new ListTag();
        for (int kind = 0; kind < SITES.size(); kind++) {
            String name = SITES.get(kind); LegacyOfTheForce.LOGGER.info("ILUM_VALIDATION generating {}", name);
            var located = locate(level, name); var start = start(level, name, located); var piece = start.getPieces().getFirst(); var box = piece.getBoundingBox();
            var entry = new CompoundTag(); entry.putString("id", name); entry.putIntArray("located", new int[]{located.getX(), located.getY(), located.getZ()});
            var saved = piece.createTag(StructurePieceSerializationContext.fromLevel(level)); require(saved.getInt("siteKind") == kind, "Wrong site kind"); entry.put("piece", saved);
            if (kind == 0) {
                var node = new BlockPos(box.minX() + 7, box.minY() + 1, box.minZ() + 8);
                require(level.getBlockState(node).is(PlanetContent.CRYSTAL.get()), "Worldgen must really place the crystal node");
                int count = 0;
                for (var p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) if (level.getBlockState(p).is(PlanetContent.CRYSTAL.get())) count++;
                require(count == 1, "Exactly one rare node, not an abundant ore vein");
                var marker = new BlockPos(box.minX() + 3, box.minY() + 1, box.minZ() + 7); require(level.isEmptyBlock(marker), "Marker must not replace existing blocks");
                level.setBlockAndUpdate(marker, Blocks.DIAMOND_BLOCK.defaultBlockState()); proof.putIntArray("marker", new int[]{marker.getX(), marker.getY(), marker.getZ()});
                harvest(level, node, proof);
            }
            entry.putString("hash", hash(level, box)); sites.add(entry);
            new VerifiedChunkSaveBarrier().persist(level, Set.of(new BlockPos(box.minX(), box.minY(), box.minZ())));
        }
        proof.put("sites", sites); AtomicNbtFile.write(file, proof, 262144);
    }
    private static void harvest(ServerLevel level, BlockPos node, CompoundTag proof) throws Exception {
        var profile = new GameProfile(UUID.randomUUID(), "IlumHarvester"); var actor = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        actor.connection = new FakePlayer(level, profile).connection; actor.setGameMode(GameType.SURVIVAL); actor.setPos(node.getCenter().add(0, 0, -2));
        var tool = new ItemStack(Items.IRON_PICKAXE); actor.getInventory().setItem(0, tool);
        require(tool.isCorrectToolForDrops(PlanetContent.CRYSTAL.get().defaultBlockState()), "Iron pickaxe must harvest the source");
        var fortune = tool.copy(); fortune.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE), 3);
        var silk = tool.copy(); silk.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
        var ids = new HashSet<UUID>();
        for (var pick : List.of(tool, fortune, silk)) {
            var drops = Block.getDrops(PlanetContent.CRYSTAL.get().defaultBlockState(), level, node, null, actor, pick);
            require(drops.size() == 1 && drops.getFirst().is(SaberContent.KYBER.get()) && drops.getFirst().getCount() == 1, "Fortune/silk cannot multiply or return the source");
            var data = drops.getFirst().get(SaberContent.KYBER_DATA.get()); var value = data.state().orElseThrow();
            require(ids.add(value.id()) && value.purity() >= .55 && value.purity() <= .90 && value.stability() >= .60 && value.stability() <= .95, "Bounded individually identified crystals");
        }
        require(Block.getDrops(PlanetContent.CRYSTAL.get().defaultBlockState(), level, node, null, actor, ItemStack.EMPTY).isEmpty(), "Empty hand cannot obtain a crystal");
        require(actor.gameMode.destroyBlock(node) && level.isEmptyBlock(node), "Real ServerPlayerGameMode must break the generated node");
        var entities = level.getEntitiesOfClass(ItemEntity.class, new AABB(node).inflate(2), item -> item.getItem().is(SaberContent.KYBER.get()));
        require(entities.size() == 1, "Mining must create exactly one actual item entity");
        var entity = entities.getFirst(); var crystal = entity.getItem().copy(); entity.setNoPickUpDelay(); entity.playerTouch(actor);
        require(entity.isRemoved() && actor.getInventory().countItem(SaberContent.KYBER.get()) == 1, "Actual item must be picked up into real inventory");
        proof.putUUID("harvester", actor.getUUID()); proof.put("crystal", crystal.save(level.registryAccess()));
        var saved = new VerifiedPlayerSaveBarrier().persist(actor); proof.put("Inventory", saved.get("Inventory").copy());
    }
    private static void verify(ServerLevel level, Path file) throws Exception {
        var proof = AtomicNbtFile.read(file, 262144); require(proof.getInt("schemaVersion") == 1 && proof.getLong("seed") == level.getSeed(), "Proof world mismatch");
        for (var value : proof.getList("sites", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag)value; int[] coordinates = entry.getIntArray("located"); var located = new BlockPos(coordinates[0], coordinates[1], coordinates[2]);
            require(locate(level, entry.getString("id")).equals(located), "Seeded locator must stay stable after restart");
            var piece = start(level, entry.getString("id"), located).getPieces().getFirst();
            require(piece.createTag(StructurePieceSerializationContext.fromLevel(level)).equals(entry.getCompound("piece")) && hash(level, piece.getBoundingBox()).equals(entry.getString("hash")), "Site/player edits or consumed node changed after reload");
        }
        int[] marker = proof.getIntArray("marker"); require(level.getBlockState(new BlockPos(marker[0], marker[1], marker[2])).is(Blocks.DIAMOND_BLOCK), "Player edit must survive");
        var actor = new ServerPlayer(level.getServer(), level, new GameProfile(proof.getUUID("harvester"), "IlumHarvester"), ClientInformation.createDefault());
        actor.connection = new FakePlayer(level, actor.getGameProfile()).connection; var nativePlayer = new VerifiedPlayerSaveBarrier().read(actor);
        require(nativePlayer.get("Inventory").equals(proof.get("Inventory")), "Native inventory must survive restart"); actor.load(nativePlayer);
        var crystals = actor.getInventory().items.stream().filter(stack -> stack.is(SaberContent.KYBER.get())).toList();
        require(crystals.size() == 1 && crystals.getFirst().save(level.registryAccess()).equals(proof.getCompound("crystal")), "Collected UUID and quality must survive the real inventory codec");
        var positiveLoot = Block.getDrops(PlanetContent.CRYSTAL.get().defaultBlockState(), level, new BlockPos(marker[0], marker[1], marker[2]), null, actor, new ItemStack(Items.IRON_PICKAXE));
        require(positiveLoot.size() == 1 && positiveLoot.getFirst().is(SaberContent.KYBER.get()) && positiveLoot.getFirst().get(SaberContent.KYBER_DATA.get()).state().isPresent(), "Current loot table must still yield an identified crystal in Ilum");
        var verified = proof.copy(); verified.putBoolean("restartVerified", true); AtomicNbtFile.write(file.resolveSibling("ilum-verified.nbt"), verified, 262144);
    }
}
