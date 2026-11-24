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
    private UUID owner;
    private int remaining;
    private CompoundTag unsupported;
    public ArenaIllusionEntity(EntityType<? extends ArenaIllusionEntity> type, Level level) { super(type, level); setNoGravity(true); }
    public UUID owner() { return owner; }
    public boolean supported() { return entityData.get(SUPPORTED); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(SUPPORTED, true); }
    static void spawn(IlumBossEntity boss, Vec3 point) {
        var level = boss.serverLevel();
        if (level.getEntitiesOfClass(ArenaIllusionEntity.class, BossArenaService.bounds(boss.altar()), illusion -> boss.getUUID().equals(illusion.owner)).size() >= 2) return;
        var illusion = BossContent.ILLUSION.get().create(level); if (illusion == null) return;
        illusion.owner = boss.getUUID(); illusion.remaining = BossConfig.ILLUSION_TICKS.get(); illusion.setPos(point); illusion.setYRot(boss.getYRot());
        if (BossArenaService.bounds(boss.altar()).contains(point) && level.noCollision(illusion, illusion.getBoundingBox())) level.addFreshEntity(illusion);
    }
    @Override public void tick() {
        super.tick(); if (level().isClientSide()) return;
        if (unsupported != null) { setDeltaMovement(Vec3.ZERO); return; }
        var level = (ServerLevel)level(); var entity = owner == null ? null : level.getEntity(owner);
        if (!(entity instanceof IlumBossEntity boss) || --remaining <= 0 || boss.altar() == null || !boss.isAlive()
                || BossArenaService.forServer(level.getServer()) == null || !BossArenaService.forServer(level.getServer()).authorized(boss)
                || !BossArenaService.bounds(boss.altar()).contains(position())) discard();
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
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        var data = tag.getCompound("lotfIllusion");
        if (data.contains("schemaVersion", Tag.TAG_INT) && data.getInt("schemaVersion") != 1) { unsupported = data.copy(); entityData.set(SUPPORTED, false); return; }
        if (data.getInt("schemaVersion") != 1 || !data.hasUUID("owner") || !data.contains("remaining", Tag.TAG_INT) || data.getInt("remaining") < 1 || data.getInt("remaining") > 120) { remaining = 0; return; }
        owner = data.getUUID("owner"); remaining = data.getInt("remaining");
    }
}
