package com.legacyoftheforce.bosses;

import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** Solid, non-exportable arena object. Force impulses really move cover/projectiles. */
public final class ArenaIceEntity extends PathfinderMob {
    private UUID owner, reflector;
    private boolean projectile;
    private int remaining;
    public ArenaIceEntity(EntityType<? extends ArenaIceEntity> type, Level level) { super(type, level); setNoGravity(true); setPersistenceRequired(); }
    public static AttributeSupplier.Builder attributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.MOVEMENT_SPEED, 0); }
    public UUID owner() { return owner; }
    public boolean projectile() { return projectile; }
    private Entity ownerEntity() { return owner != null && level() instanceof net.minecraft.server.level.ServerLevel level ? level.getEntity(owner) : null; }
    static void spawn(IlumBossEntity boss, Vec3 point, Vec3 velocity, boolean projectile) {
        var level = boss.serverLevel();
        if (level.getEntitiesOfClass(ArenaIceEntity.class, BossArenaService.bounds(boss.altar()), ice -> ice.isAlive() && boss.getUUID().equals(ice.owner)).size() >= 2) return;
        var ice = BossContent.ICE.get().create(level); if (ice == null) return;
        ice.owner = boss.getUUID(); ice.projectile = projectile; ice.remaining = BossConfig.ICE_LIFETIME.get();
        ice.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BossConfig.ICE_HEALTH.get()); ice.setHealth(ice.getMaxHealth()); ice.setPos(point); ice.setDeltaMovement(velocity);
        if (level.noCollision(ice, ice.getBoundingBox())) level.addFreshEntity(ice);
    }
    void deflected(ServerPlayer actor) { if (ownerEntity() instanceof IlumBossEntity boss && boss.participant(actor)) { reflector = actor.getUUID(); boss.support(actor); } }
    @Override protected void customServerAiStep() {
        if (!(ownerEntity() instanceof IlumBossEntity boss) || --remaining <= 0 || boss.altar() == null
                || BossArenaService.forServer(boss.serverLevel().getServer()) == null || !BossArenaService.forServer(boss.serverLevel().getServer()).authorized(boss)
                || !BossArenaService.bounds(boss.altar()).inflate(1).contains(position())) { discard(); return; }
        if (!projectile) { setDeltaMovement(getDeltaMovement().scale(.9)); return; }
        var end = position().add(getDeltaMovement());
        if (level().clip(new ClipContext(position(), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this)).getType() != HitResult.Type.MISS) { discard(); return; }
        if (reflector != null && getBoundingBox().inflate(.1).intersects(boss.getBoundingBox())) {
            var actor = boss.serverLevel().getServer().getPlayerList().getPlayer(reflector);
            if (boss.participant(actor)) boss.hurt(damageSources().playerAttack(actor), BossConfig.damage(BossAttack.SHARDS)); discard(); return;
        }
        if (reflector == null) for (var actor : level().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().expandTowards(getDeltaMovement()).inflate(.1))) {
            if (boss.participant(actor)) { actor.hurt(damageSources().mobAttack(boss), BossConfig.damage(BossAttack.SHARDS)); discard(); return; }
        }
    }
    @Override public boolean canBeCollidedWith() { return isAlive(); }
    @Override public boolean isPushable() { return true; }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || !(source.getEntity() instanceof ServerPlayer actor) || !(ownerEntity() instanceof IlumBossEntity boss) || !boss.participant(actor)) return false;
        boolean hit = super.hurt(source, amount); if (hit) boss.support(actor); return hit;
    }
    @Override protected void dropAllDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source) {}
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); var data = new CompoundTag(); data.putInt("schemaVersion", 1); if (owner != null) data.putUUID("owner", owner);
        if (reflector != null) data.putUUID("reflector", reflector); data.putBoolean("projectile", projectile); data.putInt("remaining", remaining); tag.put("lotfArenaIce", data);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); var data = tag.getCompound("lotfArenaIce");
        if (data.getInt("schemaVersion") != 1 || !data.hasUUID("owner") || data.getInt("remaining") < 1 || data.getInt("remaining") > 400) { remaining = 0; return; }
        owner = data.getUUID("owner"); reflector = data.hasUUID("reflector") ? data.getUUID("reflector") : null; projectile = data.getBoolean("projectile"); remaining = data.getInt("remaining");
    }
}
