package com.legacyoftheforce.bosses;

import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraft.network.syncher.*;

/** Solid, non-exportable arena object. Force impulses really move cover/projectiles. */
public final class ArenaIceEntity extends PathfinderMob implements com.legacyoftheforce.core.api.ForceImpulseResistance {
    private static final EntityDataAccessor<Integer> MATERIAL = SynchedEntityData.defineId(ArenaIceEntity.class, EntityDataSerializers.INT);
    private UUID owner, reflector;
    private boolean projectile;
    private int remaining;
    private CompoundTag unsupported;
    public ArenaIceEntity(EntityType<? extends ArenaIceEntity> type, Level level) { super(type, level); setNoGravity(true); setPersistenceRequired(); }
    public static AttributeSupplier.Builder attributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.MOVEMENT_SPEED, 0); }
    public UUID owner() { return owner; }
    public boolean projectile() { return projectile; }
    public boolean disrupted() { return reflector != null; }
    @Override public boolean acceptsForceImpulse() { return unsupported == null; }
    @Override public void forceImpulseCommitted() {}
    public int material() { return entityData.get(MATERIAL); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(MATERIAL, 0); }
    private Entity ownerEntity() { return owner != null && level() instanceof net.minecraft.server.level.ServerLevel level ? level.getEntity(owner) : null; }
    static void spawn(IlumBossEntity boss, Vec3 point, Vec3 velocity, boolean projectile) {
        var level = boss.serverLevel();
        if (level.getEntitiesOfClass(ArenaIceEntity.class, BossArenaService.bounds(boss.altar()), ice -> ice.isAlive() && boss.getUUID().equals(ice.owner)).size() >= 2) return;
        var ice = BossContent.ICE.get().create(level); if (ice == null) return;
        ice.owner = boss.getUUID(); ice.projectile = projectile; ice.remaining = BossConfig.ICE_LIFETIME.get();
        ice.entityData.set(MATERIAL, boss.kind().sacred() ? 8 : boss.kind().swamp() ? projectile ? 7 : 6 : boss.kind().volcanic() ? projectile ? 4 : 5 : boss.kind().forest() ? 3 : boss.kind().desert() ? boss.kind().dark() ? 2 : 1 : 0);
        ice.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BossConfig.ICE_HEALTH.get()); ice.setHealth(ice.getMaxHealth()); ice.setPos(point); ice.setDeltaMovement(velocity);
        if (BossArenaService.bounds(boss.altar()).inflate(.1).contains(point) && level.noCollision(ice, ice.getBoundingBox())) level.addFreshEntity(ice);
    }
    void deflected(ServerPlayer actor) { if (ownerEntity() instanceof IlumBossEntity boss && boss.participant(actor)) {
        if ((boss.kind().volcanic() || boss.kind().swamp() || boss.kind().sacred()) && projectile) {
            if (!actor.getUUID().equals(reflector)) { reflector = actor.getUUID(); boss.support(actor); }
            return; // A later real reflector receives impact credit; no immediate guard break before contact.
        }
        if ((boss.kind().desert() || boss.kind().forest() || boss.kind().volcanic() || boss.kind().swamp() || boss.kind().sacred()) && reflector != null) return;
        reflector = actor.getUUID(); boss.support(actor); if (boss.kind().desert() || boss.kind().forest() || (boss.kind().volcanic() || boss.kind().swamp() || boss.kind().sacred()) && !projectile) boss.arenaObjectDisrupted();
    } }
    @Override protected void customServerAiStep() {
        if (unsupported != null) { setDeltaMovement(Vec3.ZERO); return; }
        if (!(ownerEntity() instanceof IlumBossEntity boss) || --remaining <= 0 || boss.altar() == null
                || BossArenaService.forServer(boss.serverLevel().getServer()) == null || !BossArenaService.forServer(boss.serverLevel().getServer()).authorized(boss)
                || !BossArenaService.bounds(boss.altar()).inflate(1).contains(position())) { discard(); return; }
        if (!projectile) { setDeltaMovement(getDeltaMovement().scale(.9)); return; }
        if (material() == 4 || material() == 7) {
            if (getDeltaMovement().lengthSqr() > .0001) setDeltaMovement(getDeltaMovement().normalize().scale(material() == 7 ? BossConfig.SWAMP_BOLT_SPEED.get() : BossConfig.EMBER_SPEED.get()));
            if (tickCount % 4 == 0) boss.serverLevel().sendParticles(material() == 7 ? net.minecraft.core.particles.ParticleTypes.SOUL : net.minecraft.core.particles.ParticleTypes.FLAME, getX(), getY() + .45, getZ(), 2, .1, .1, .1, .01);
        }
        var end = position().add(getDeltaMovement());
        if (level().clip(new ClipContext(position(), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this)).getType() != HitResult.Type.MISS) { discard(); return; }
        if (reflector != null && getBoundingBox().inflate(.1).intersects(boss.getBoundingBox())) {
            var actor = boss.serverLevel().getServer().getPlayerList().getPlayer(reflector);
            if (boss.participant(actor) && boss.hurt(damageSources().playerAttack(actor), material() == 7 ? BossConfig.damage(boss.kind(), BossAttack.DECEPTIVE_BOLT) : material() == 4 ? BossConfig.damage(boss.kind(), BossAttack.FIRE_BOLT) : BossConfig.damage(BossAttack.SHARDS)) && (material() == 4 || material() == 7)) boss.arenaObjectDisrupted(); discard(); return;
        }
        if (reflector == null) for (var actor : level().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().expandTowards(getDeltaMovement()).inflate(.1))) {
            if (boss.participant(actor)) {
                if (material() == 7) {
                    var type = level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC);
                    actor.hurt(new DamageSource(type, this, boss), BossConfig.damage(boss.kind(), BossAttack.DECEPTIVE_BOLT));
                } else if (material() == 4) {
                    var type = level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.FIREBALL);
                    if (actor.hurt(new DamageSource(type, this, boss), BossConfig.damage(boss.kind(), BossAttack.FIRE_BOLT)) && BossConfig.EMBER_BURN_SECONDS.get() > 0) actor.igniteForSeconds(BossConfig.EMBER_BURN_SECONDS.get());
                } else actor.hurt(damageSources().mobAttack(boss), BossConfig.damage(BossAttack.SHARDS));
                discard(); return;
            }
        }
    }
    @Override public boolean canBeCollidedWith() { return material() >= 0 && unsupported == null && isAlive(); }
    @Override public boolean isPushable() { return material() >= 0 && unsupported == null; }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (unsupported != null || level().isClientSide() || !(source.getEntity() instanceof ServerPlayer actor) || !(ownerEntity() instanceof IlumBossEntity boss) || !boss.participant(actor)) return false;
        boolean hit = super.hurt(source, amount); if (hit) { boss.support(actor); if (isDeadOrDying() && (boss.kind().desert() || boss.kind().forest() || boss.kind().volcanic() || boss.kind().swamp() || boss.kind().sacred())) boss.arenaObjectDisrupted(); } return hit;
    }
    @Override protected void dropAllDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source) {}
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); if (unsupported != null) { tag.put("lotfArenaIce", unsupported.copy()); return; }
        var data = new CompoundTag(); data.putInt("schemaVersion", 1); if (owner != null) data.putUUID("owner", owner);
        if (reflector != null) data.putUUID("reflector", reflector); data.putBoolean("projectile", projectile); data.putInt("remaining", remaining); tag.put("lotfArenaIce", data);
        if (material() != 0) { data.putInt("schemaVersion", 2); data.putString("material", material() == 8 ? "relic_anchor" : material() == 6 ? "mire_stone" : material() == 7 ? "mind_bolt" : material() == 4 ? "ember" : material() == 5 ? "basalt" : material() == 3 ? "living_wood" : material() == 1 ? "sandstone" : "red_sandstone"); }
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); var data = tag.getCompound("lotfArenaIce");
        if (data.contains("schemaVersion", Tag.TAG_INT) && data.getInt("schemaVersion") != 1 && data.getInt("schemaVersion") != 2
                || data.getInt("schemaVersion") == 2 && !java.util.Set.of("sandstone", "red_sandstone", "living_wood", "ember", "basalt", "mire_stone", "mind_bolt", "relic_anchor").contains(data.getString("material"))) {
            unsupported = data.copy(); entityData.set(MATERIAL, -1); return;
        }
        if ((data.getInt("schemaVersion") != 1 && data.getInt("schemaVersion") != 2) || !data.hasUUID("owner") || data.getInt("remaining") < 1 || data.getInt("remaining") > 400
                || data.getInt("schemaVersion") == 2 && !java.util.Set.of("sandstone", "red_sandstone", "living_wood", "ember", "basalt", "mire_stone", "mind_bolt", "relic_anchor").contains(data.getString("material"))) { remaining = 0; return; }
        entityData.set(MATERIAL, data.getInt("schemaVersion") == 1 ? 0 : data.getString("material").equals("relic_anchor") ? 8 : data.getString("material").equals("mire_stone") ? 6 : data.getString("material").equals("mind_bolt") ? 7 : data.getString("material").equals("ember") ? 4 : data.getString("material").equals("basalt") ? 5 : data.getString("material").equals("living_wood") ? 3 : data.getString("material").equals("sandstone") ? 1 : 2);
        owner = data.getUUID("owner"); reflector = data.hasUUID("reflector") ? data.getUUID("reflector") : null; projectile = data.getBoolean("projectile"); remaining = data.getInt("remaining");
    }
}
