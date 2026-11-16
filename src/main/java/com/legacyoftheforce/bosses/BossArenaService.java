package com.legacyoftheforce.bosses;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;

/** One server-thread authority. Native entities/containers remain the only world writers. */
public final class BossArenaService {
    private static final Map<MinecraftServer, BossArenaService> SERVICES = new IdentityHashMap<>();
    private final MinecraftServer server;
    private final PlayerProgressService progress;
    private final Path root;
    private final UUID boot = UUID.randomUUID();
    private final Map<UUID, CompoundTag> arenas = new LinkedHashMap<>();
    private final Map<UUID, Long> nextInteraction = new HashMap<>();
    private final DurableOperationStore victories;
    private boolean ready = true;
    public static BossArenaService forServer(MinecraftServer server) { return SERVICES.get(server); }
    public static void start(MinecraftServer server, PlayerProgressService progress) throws IOException { SERVICES.put(server, new BossArenaService(server, progress)); }
    public static void stop(MinecraftServer server) { SERVICES.remove(server); }
    BossArenaService(MinecraftServer server, PlayerProgressService progress) throws IOException {
        this.server = server; this.progress = progress; root = server.getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/bosses");
        victories = new DurableOperationStore(root.resolve("victories"), 128);
        for (var entry : victories.list()) validateVictory(entry);
    }
    static UUID arenaId(ServerLevel level, BlockPos altar, BossKind kind) { return UUID.nameUUIDFromBytes((level.dimension().location() + "/" + altar.asLong() + "/" + kind.name()).getBytes(StandardCharsets.UTF_8)); }
    static AABB bounds(BlockPos altar) {
        var center = altar.north(3); return new AABB(center.getX() - 4, center.getY() - 1, center.getZ() - 4, center.getX() + 5, center.getY() + 5, center.getZ() + 5);
    }
    private Path arenaPath(UUID id) { return root.resolve("arenas/" + id + ".nbt"); }
    private void save(UUID id, CompoundTag tag) throws IOException {
        AtomicNbtFile.write(arenaPath(id), tag, 16384);
        if (!arenas.containsKey(id) && arenas.size() >= 128 && tag.getString("state").equals("COOLDOWN")) return;
        arenas.put(id, tag.copy());
    }
    private CompoundTag arena(ServerLevel level, BlockPos pos, BossKind kind) throws IOException {
        UUID id = arenaId(level, pos, kind); var cached = arenas.get(id); if (cached != null) return cached.copy();
        var path = arenaPath(id); var tag = new CompoundTag();
        if (Files.exists(path)) {
            tag = AtomicNbtFile.read(path, 16384);
            if (tag.getInt("schemaVersion") != 1 || !tag.getString("dimension").equals(level.dimension().location().toString())
                    || tag.getLong("altar") != pos.asLong() || !tag.getString("kind").equals(kind.name())
                    || !Set.of("SPAWNING", "ACTIVE", "SETTLING", "COOLDOWN").contains(tag.getString("state")) || !tag.hasUUID("encounter") || tag.getLong("readyAt") < 0) throw new IOException("Unsupported arena state retained");
            if (Set.of("SPAWNING", "ACTIVE").contains(tag.getString("state")) && (!tag.hasUUID("boot") || !tag.getUUID("boot").equals(boot))) {
                var encounter = tag.getUUID("encounter"); boolean won = victories.list().stream().anyMatch(entry -> entry.id().equals(encounter));
                tag.putString("state", won ? "SETTLING" : "COOLDOWN"); tag.putLong("readyAt", now() + BossConfig.RESET_DELAY.get()); save(id, tag);
            }
        } else {
            tag.putInt("schemaVersion", 1); tag.putString("dimension", level.dimension().location().toString()); tag.putLong("altar", pos.asLong());
            tag.putString("kind", kind.name()); tag.putString("state", "COOLDOWN"); tag.putLong("readyAt", 0); tag.putUUID("encounter", UUID.randomUUID());
        }
        if (arenas.size() >= 128) {
            var removable = arenas.entrySet().stream().filter(e -> e.getValue().getString("state").equals("COOLDOWN")).findFirst();
            if (removable.isEmpty()) return tag; arenas.remove(removable.get().getKey());
        }
        arenas.put(id, tag.copy()); return tag;
    }
    private long now() { return server.overworld().getGameTime(); }
    private void feedback(ServerPlayer actor, String key) { actor.displayClientMessage(Component.translatable("boss.legacyoftheforce." + key), false); }
    public boolean canOpen(ServerPlayer actor, BlockPos pos) {
        if (!ready || actor.server != server || !server.isSameThread() || !actor.isAlive() || actor.isSpectator() || actor.isFakePlayer() || !actor.canInteractWithBlock(pos, 0)) return false;
        var kind = actor.level().getBlockState(pos).is(BossContent.JEDI_ALTAR.get()) ? BossKind.GUARDIAN : actor.level().getBlockState(pos).is(BossContent.SITH_ALTAR.get()) ? BossKind.FROST_LORD : null;
        if (kind == null) return false;
        if(!(actor.level().getBlockEntity(pos) instanceof BossAltarEntity tile) || !tile.supported() || tile.pendingClaim()!=null || !new VanillaProtectedWorldService().mayUseContainer(actor,actor.serverLevel(),pos)) return false;
        var tag = arenas.get(arenaId(actor.serverLevel(), pos, kind));
        if (tag == null) {
            if (!(actor.level().getBlockState(pos).getBlock() instanceof BossAltarBlock)) return false;
            try { tag = arena(actor.serverLevel(), pos, actor.level().getBlockState(pos).is(BossContent.JEDI_ALTAR.get()) ? BossKind.GUARDIAN : BossKind.FROST_LORD); }
            catch (IOException failure) { fail(failure); return false; }
        }
        return tag.getString("state").equals("COOLDOWN") && tag.getList("lootOwners", Tag.TAG_STRING).stream().anyMatch(t -> t.getAsString().equals(actor.getUUID().toString()));
    }
    public boolean challenge(ServerPlayer actor, BlockPos pos, BossKind kind) {
        if (!ready) { feedback(actor,"pending"); return false; }
        if (actor.server != server || !server.isSameThread() || !actor.isAlive() || actor.isSpectator() || actor.isCreative() || actor.isFakePlayer()
                || !actor.level().dimension().location().toString().equals("legacyoftheforce:ilum") || !actor.canInteractWithBlock(pos, 0)
                || !actor.level().getBlockState(pos).is(kind == BossKind.GUARDIAN ? BossContent.JEDI_ALTAR.get() : BossContent.SITH_ALTAR.get())
                || !(actor.level().getBlockEntity(pos) instanceof BossAltarEntity altar) || !altar.supported() || !actor.level().mayInteract(actor, pos)) return false;
        if (now() < nextInteraction.getOrDefault(actor.getUUID(),0L)) return false;
        if (nextInteraction.size() >= 1024 && !nextInteraction.containsKey(actor.getUUID())) return false;
        nextInteraction.put(actor.getUUID(),now()+10);
        var spawn=pos.north(3);
        if(!actor.serverLevel().entityManager.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(spawn).toLong()) || !actor.serverLevel().isPositionEntityTicking(spawn)) { feedback(actor,"loading");return false; }
        if (!arenas.containsKey(arenaId(actor.serverLevel(),pos,kind)) && arenas.size() >= 128 && arenas.values().stream().noneMatch(t -> t.getString("state").equals("COOLDOWN"))) return false;
        try {
            var record = arena(actor.serverLevel(), pos, kind); UUID id = arenaId(actor.serverLevel(), pos, kind);
            if (record.getString("state").equals("ACTIVE")) {
                var existing = actor.serverLevel().getEntity(record.getUUID("encounter"));
                if (existing instanceof IlumBossEntity boss && boss.join(actor)) { feedback(actor, "joined"); return true; }
                feedback(actor, "active"); return false;
            }
            if (!record.getString("state").equals("COOLDOWN") || now() < record.getLong("readyAt")) { feedback(actor, "cooldown"); return false; }
            if (!altar.isEmpty() || altar.pendingClaim()!=null) { feedback(actor, "collect_first"); return false; }
            if (victories.list().size() >= 128 || !new VanillaProtectedWorldService().mayModify(actor, actor.serverLevel(), pos, ModificationKind.MOVE_THROUGH)) return false;
            var center = pos.north(3);
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                var floor = center.offset(x, -1, z);
                if (!actor.level().hasChunkAt(floor) || actor.level().getBlockState(floor).getCollisionShape(actor.level(), floor).isEmpty()
                        || !actor.level().getBlockState(floor).getFluidState().isEmpty()) { feedback(actor, "floor"); return false; }
            }
            var boss = (kind == BossKind.GUARDIAN ? BossContent.GUARDIAN.get() : BossContent.FROST_LORD.get()).create(actor.level());
            if (boss == null) return false;
            boss.setPos(center.getX() + .5, center.getY(), center.getZ() + .5);
            if (!actor.level().noCollision(boss, boss.getBoundingBox())) { feedback(actor, "space"); return false; }
            UUID encounter = UUID.randomUUID(); boss.setUUID(encounter); boss.bind(pos); boss.join(actor);
            record.putUUID("encounter", encounter); record.putUUID("boot", boot); record.putString("state", "SPAWNING"); record.remove("lootOwners"); save(id, record);
            if (!actor.serverLevel().addFreshEntity(boss)) throw new IOException("Boss publication refused");
            new VerifiedEntitySaveBarrier().persist(boss);
            record.putString("state", "ACTIVE"); save(id, record); feedback(actor, "challenge"); return true;
        } catch (IOException | RuntimeException failure) { fail(failure); feedback(actor, "pending"); return false; }
    }
    public void logout(UUID player) { nextInteraction.remove(player); }
    boolean authorized(IlumBossEntity boss) {
        if (!ready || !(boss.level() instanceof ServerLevel level) || boss.altar() == null) return false;
        try { var record = arena(level, boss.altar(), boss.kind()); return record.getString("state").equals("ACTIVE") && record.getUUID("encounter").equals(boss.getUUID()); }
        catch (IOException failure) { fail(failure); return false; }
    }
    void reset(IlumBossEntity boss) {
        if (!authorized(boss)) return;
        try {
            var tag = arena(boss.serverLevel(), boss.altar(), boss.kind()); tag.putString("state", "COOLDOWN"); tag.putLong("readyAt", now() + BossConfig.RESET_DELAY.get());
            save(arenaId(boss.serverLevel(), boss.altar(), boss.kind()), tag); boss.discard();
        } catch (IOException failure) { fail(failure); }
    }
    boolean defeated(IlumBossEntity boss) {
        if (!authorized(boss)) return false;
        boolean prepared = false;
        try {
            var payload = new CompoundTag(); payload.putInt("schemaVersion", 1); payload.putString("dimension", boss.level().dimension().location().toString());
            payload.putLong("altar", boss.altar().asLong()); payload.putString("kind", boss.kind().name());
            payload.putInt("xp", BossConfig.XP.get()); payload.putInt("rewardCooldown", BossConfig.REWARD_COOLDOWN.get());
            payload.putLong("arenaReadyAt", now() + BossConfig.ARENA_COOLDOWN.get());
            var eligible = new ListTag(); for (UUID player : boss.eligibleParticipants()) eligible.add(StringTag.valueOf(player.toString())); payload.put("eligible", eligible);
            victories.prepare(boss.getUUID(), payload);
            prepared = true;
            var tag = arena(boss.serverLevel(), boss.altar(), boss.kind()); tag.putString("state", "SETTLING"); save(arenaId(boss.serverLevel(), boss.altar(), boss.kind()), tag);
            settle(victories.read(boss.getUUID()));
            return true;
        } catch (IOException failure) { fail(failure); return prepared; }
    }
    public boolean protectedAt(ServerLevel level, BlockPos pos) {
        if (!server.isSameThread()) return true;
        if (level.getBlockEntity(pos) instanceof BossAltarEntity tile && (!tile.supported() || !tile.isEmpty() || tile.pendingClaim()!=null)) return true;
        for (var tag : arenas.values()) {
            if (!tag.getString("dimension").equals(level.dimension().location().toString())) continue;
            var altar = BlockPos.of(tag.getLong("altar"));
            if (!tag.getString("state").equals("COOLDOWN") && bounds(altar).contains(Vec3.atCenterOf(pos))) return true;
            if (altar.equals(pos) && level.getBlockEntity(altar) instanceof BossAltarEntity tile && !tile.isEmpty()) return true;
        }
        return false;
    }
    public void tick() {
        if (!ready || now() % 20 != 0) return;
        try { for (var entry : victories.list()) settle(entry); }
        catch (IOException | RuntimeException failure) { fail(failure); }
    }
    private void validateVictory(StoredOperation entry) throws IOException {
        var p = entry.payload();
        if (!entry.supported() || p.getInt("schemaVersion") != 1 || !Set.of("GUARDIAN", "FROST_LORD").contains(p.getString("kind"))
                || !p.getString("dimension").equals("legacyoftheforce:ilum") || p.getInt("xp") < 1 || p.getInt("xp") > 100000
                || p.getInt("rewardCooldown") < 1 || p.getLong("arenaReadyAt") < 0 || !p.contains("eligible", Tag.TAG_LIST)
                || p.getList("eligible", Tag.TAG_STRING).size() > 16) throw new IOException("Unsupported boss victory retained");
        for (var id : p.getList("eligible", Tag.TAG_STRING)) UUID.fromString(id.getAsString());
    }
    private void settle(StoredOperation entry) throws IOException {
        validateVictory(entry); var p = entry.payload(); var kind = BossKind.valueOf(p.getString("kind"));
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(p.getString("dimension")))); var pos = BlockPos.of(p.getLong("altar"));
        if (level == null || !level.hasChunkAt(pos)) return;
        if (entry.phase().equals("PREPARED")) {
            var awarded = new ListTag();
            for (var id : p.getList("eligible", Tag.TAG_STRING)) {
                UUID player = UUID.fromString(id.getAsString());
                var status = progress.bossRewardStatus(player, entry.id());
                if (status == BossRewardStatus.PENDING) {
                    progress.awardBoss(player, new BossReward(entry.id(), kind.id().toString(), kind.category, p.getInt("xp"), p.getInt("rewardCooldown")));
                    status = progress.bossRewardStatus(player, entry.id());
                }
                if (status == BossRewardStatus.PENDING) return;
                if (status == BossRewardStatus.AWARDED) awarded.add(StringTag.valueOf(player.toString()));
            }
            p.put("awarded", awarded); entry = victories.transition(entry.id(), OperationPhase.PREPARED, OperationPhase.HELD, p);
        }
        if (entry.phase().equals("HELD")) {
            if (!(level.getBlockEntity(pos) instanceof BossAltarEntity altar) || !altar.supported()
                    || !level.getBlockState(pos).is(kind == BossKind.GUARDIAN ? BossContent.JEDI_ALTAR.get() : BossContent.SITH_ALTAR.get())) return;
            if (!entry.id().equals(altar.lootVictory())) {
                if (!altar.isEmpty()) return;
                altar.publish(entry.id(), !p.getList("awarded", Tag.TAG_STRING).isEmpty());
                new VerifiedChunkSaveBarrier().persist(level, Set.of(pos));
            }
            entry = victories.transition(entry.id(), OperationPhase.HELD, OperationPhase.DESTINATION_STAGED, p);
        }
        if (entry.phase().equals("DESTINATION_STAGED")) {
            var tag = arena(level, pos, kind);
            if (tag.getUUID("encounter").equals(entry.id())) {
                tag.putString("state", "COOLDOWN"); tag.putLong("readyAt", p.getLong("arenaReadyAt")); tag.put("lootOwners", p.getList("awarded", Tag.TAG_STRING).copy()); save(arenaId(level, pos, kind), tag);
            }
            var ids = new HashSet<UUID>();
            for (var raw : p.getList("awarded", Tag.TAG_STRING)) {
                UUID player = UUID.fromString(raw.getAsString()); ids.add(player); var actor = server.getPlayerList().getPlayer(player);
                if (actor != null) NeoForge.EVENT_BUS.post(new KyberMilestone(player, entry.id(), actor.level().dimension(), now(), KyberMilestone.Kind.BOSS_VICTORY));
            }
            NeoForge.EVENT_BUS.post(new BossDefeated(entry.id(), arenaId(level, pos, kind), kind.id(), ids));
            entry = victories.transition(entry.id(), OperationPhase.DESTINATION_STAGED, OperationPhase.COMMITTED, p);
        }
        if (entry.phase().equals("COMMITTED")) entry = victories.transition(entry.id(), OperationPhase.COMMITTED, OperationPhase.CLOSED, p);
        if (entry.phase().equals("CLOSED")) victories.removeClosed(entry.id());
    }
    private void fail(Exception failure) { ready = false; LegacyOfTheForce.LOGGER.error("Boss arenas paused; durable data retained for recovery", failure); }
}
