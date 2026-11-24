package com.legacyoftheforce.bosses;

import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A targetable decoy, not a mob: no damage, health loot, XP, alignment or solid collision. */
public final class ArenaIllusionEntity extends Entity {
    private static final EntityDataAccessor<Boolean> SUPPORTED = SynchedEntityData.defineId(ArenaIllusionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(ArenaIllusionEntity.class, EntityDataSerializers.INT);
    private UUID owner;
    private int remaining;
    private CompoundTag unsupported;
    public ArenaIllusionEntity(EntityType<? extends ArenaIllusionEntity> type, Level level) { super(type, level); setNoGravity(true); }
    public UUID owner() { return owner; }
    public boolean supported() { return entityData.get(SUPPORTED); }
    public int form() { return entityData.get(FORM); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(SUPPORTED, true); builder.define(FORM, 0); }
    @Override public EntityDimensions getDimensions(Pose pose) { return entityData != null && form() == 3 ? EntityDimensions.scalable(.95f, .95f) : super.getDimensions(pose); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) { super.onSyncedDataUpdated(key); if (key.equals(FORM)) refreshDimensions(); }
    static void spawn(IlumBossEntity boss, Vec3 point) {
        var level = boss.serverLevel();
        if (level.getEntitiesOfClass(ArenaIllusionEntity.class, BossArenaService.bounds(boss.altar()), illusion -> boss.getUUID().equals(illusion.owner)).size() >= 2) return;
        var illusion = BossContent.ILLUSION.get().create(level); if (illusion == null) return;
        illusion.owner = boss.getUUID(); illusion.remaining = boss.kind().swamp() ? BossConfig.SWAMP_ILLUSION_TICKS.get() : BossConfig.ILLUSION_TICKS.get();
        illusion.entityData.set(FORM, boss.kind() == BossKind.FORCE_HERMIT ? 1 : boss.kind() == BossKind.ILLUSION_MASTER ? 2 : 0);
        illusion.setPos(point); illusion.setYRot(boss.getYRot());
        if (BossArenaService.bounds(boss.altar()).contains(point) && level.noCollision(illusion, illusion.getBoundingBox())) level.addFreshEntity(illusion);
    }
    static void spawnProjectile(IlumBossEntity boss, Vec3 point, Vec3 velocity) {
        var level = boss.serverLevel();
        if (level.getEntitiesOfClass(ArenaIllusionEntity.class, BossArenaService.bounds(boss.altar()), illusion -> boss.getUUID().equals(illusion.owner)).size() >= 2) return;
        var illusion = BossContent.ILLUSION.get().create(level); if (illusion == null) return;
        illusion.owner = boss.getUUID(); illusion.remaining = BossConfig.SWAMP_ILLUSION_TICKS.get(); illusion.entityData.set(FORM, 3);
        illusion.setPos(point); illusion.setDeltaMovement(velocity);
        if (BossArenaService.bounds(boss.altar()).contains(point) && level.noCollision(illusion, illusion.getBoundingBox())) level.addFreshEntity(illusion);
    }
    @Override public void tick() {
        super.tick(); if (level().isClientSide()) return;
        if (unsupported != null) { setDeltaMovement(Vec3.ZERO); return; }
        var level = (ServerLevel)level(); var entity = owner == null ? null : level.getEntity(owner);
        if (!(entity instanceof IlumBossEntity boss) || --remaining <= 0 || boss.altar() == null || !boss.isAlive()
                || BossArenaService.forServer(level.getServer()) == null || !BossArenaService.forServer(level.getServer()).authorized(boss)
                || !BossArenaService.bounds(boss.altar()).contains(position())) { discard(); return; }
        if (form() == 3) {
            if (tickCount % 4 == 0) level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, getX(), getY() + .45, getZ(), 2, .1, .1, .1, .01);
            var end = position().add(getDeltaMovement());
            if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(end)) || level.clip(new net.minecraft.world.level.ClipContext(position(), end,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, this)).getType() != net.minecraft.world.phys.HitResult.Type.MISS) { discard(); return; }
            move(MoverType.SELF, getDeltaMovement());
            for (var actor : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(.1))) if (boss.participant(actor)) { discard(); break; }
        }
    }
    @Override public boolean isAttackable() { return supported() && unsupported == null; }
    @Override public boolean isPickable() { return supported() && unsupported == null && isAlive(); }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (unsupported != null || !(level() instanceof ServerLevel level) || owner == null || !(source.getEntity() instanceof ServerPlayer actor)
                || !(level.getEntity(owner) instanceof IlumBossEntity boss) || !boss.participant(actor) || !Float.isFinite(amount) || amount <= 0) return false;
        boss.support(actor); discard(); return true;
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (unsupported != null) { tag.put("lotfIllusion", unsupported.copy()); return; }
        var data = new CompoundTag(); data.putInt("schemaVersion", 1); if (owner != null) data.putUUID("owner", owner); data.putInt("remaining", remaining); tag.put("lotfIllusion", data);
        if (form() != 0) { data.putInt("schemaVersion", 2); data.putString("form", form() == 1 ? "dagobah_force_hermit" : form() == 2 ? "dagobah_illusion_master" : "dagobah_bolt"); }
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        var data = tag.getCompound("lotfIllusion");
        if (data.contains("schemaVersion", Tag.TAG_INT) && data.getInt("schemaVersion") != 1 && data.getInt("schemaVersion") != 2
                || data.getInt("schemaVersion") == 2 && !java.util.Set.of("dagobah_force_hermit", "dagobah_illusion_master", "dagobah_bolt").contains(data.getString("form"))) { unsupported = data.copy(); entityData.set(SUPPORTED, false); return; }
        if ((data.getInt("schemaVersion") != 1 && data.getInt("schemaVersion") != 2) || !data.hasUUID("owner") || !data.contains("remaining", Tag.TAG_INT) || data.getInt("remaining") < 1 || data.getInt("remaining") > 120) { remaining = 0; return; }
        entityData.set(FORM, data.getInt("schemaVersion") == 1 ? 0 : data.getString("form").equals("dagobah_force_hermit") ? 1 : data.getString("form").equals("dagobah_illusion_master") ? 2 : 3);
        owner = data.getUUID("owner"); remaining = data.getInt("remaining");
    }
}
