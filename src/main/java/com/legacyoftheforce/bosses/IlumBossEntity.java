package com.legacyoftheforce.bosses;

import com.legacyoftheforce.core.api.ForceImpulseResistance;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.Vec3;

/** Real server attacks with latched aim, finite energy and explicit counter windows. */
public final class IlumBossEntity extends PathfinderMob implements Enemy, ForceImpulseResistance {
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(IlumBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(IlumBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WINDUP = SynchedEntityData.defineId(IlumBossEntity.class, EntityDataSerializers.INT);
    private final BossKind kind;
    private final ServerBossEvent bar;
    private BlockPos altar;
    private final Map<UUID, Contribution> contributors = new LinkedHashMap<>();
    private record Contribution(long tick, double damage, int support) {}
    private int cooldown = 40, emptyTicks, sequence, vulnerable, controlImmunity, combo;
    private double energy = 100;
    private Vec3 aim = Vec3.ZERO;
    private UUID aimedPlayer;
    private boolean scaled;
    private CompoundTag retained;
    public IlumBossEntity(EntityType<? extends IlumBossEntity> type, Level level, BossKind kind) {
        super(type, level); this.kind = kind; setPersistenceRequired();
        bar = new ServerBossEvent(Component.translatable("entity.legacyoftheforce." + kind.path), kind == BossKind.GUARDIAN ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    }
    public static AttributeSupplier.Builder attributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 220).add(Attributes.MOVEMENT_SPEED, .24).add(Attributes.KNOCKBACK_RESISTANCE, .7).add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.FOLLOW_RANGE, 12); }
    public BossKind kind() { return kind; }
    public BlockPos altar() { return altar; }
    public ServerLevel serverLevel() { return (ServerLevel)level(); }
    public int phase() { return entityData.get(PHASE); }
    public BossAttack attack() { return BossAttack.values()[Math.clamp(entityData.get(ATTACK), 0, BossAttack.values().length - 1)]; }
    public int windup() { return entityData.get(WINDUP); }
    @Override public void move(MoverType type,Vec3 requested) {
        var allowed=!level().isClientSide() && altar!=null && retained==null ? ArenaMovement.constrain(BossArenaService.bounds(altar),getBoundingBox(),requested) : requested;
        super.move(type,allowed);
        if(!allowed.equals(requested)) {
            var motion=getDeltaMovement();setDeltaMovement(allowed.x==requested.x ? motion.x : 0,allowed.y==requested.y ? motion.y : 0,allowed.z==requested.z ? motion.z : 0);hurtMarked=true;
        }
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(PHASE, 0); builder.define(ATTACK, 0); builder.define(WINDUP, 0); }
    void bind(BlockPos pos) { altar = pos.immutable(); cooldown = BossConfig.INTRO.get(); getAttribute(Attributes.MAX_HEALTH).setBaseValue(BossConfig.health(1)); getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(BossConfig.SPEED.get()); setHealth(getMaxHealth()); }
    boolean join(ServerPlayer actor) {
        if (!valid(actor) || altar == null || !BossArenaService.bounds(altar).contains(actor.position())) return false;
        if (!contributors.containsKey(actor.getUUID()) && contributors.size() >= BossConfig.MAX_PLAYERS.get()) return false;
        contributors.putIfAbsent(actor.getUUID(), new Contribution(now(), 0, 0)); return true;
    }
    private long now() { return serverLevel().getServer().overworld().getGameTime(); }
    private boolean valid(ServerPlayer actor) { return actor != null && actor.isAlive() && !actor.isCreative() && !actor.isSpectator() && !actor.isFakePlayer() && actor.level() == level(); }
    public boolean participant(ServerPlayer actor) { return valid(actor) && altar != null && contributors.containsKey(actor.getUUID()) && BossArenaService.bounds(altar).contains(actor.position()); }
    void support(ServerPlayer actor) {
        if (!join(actor)) return; var old = contributors.get(actor.getUUID()); contributors.put(actor.getUUID(), new Contribution(now(), old.damage, Math.min(64, old.support + 1)));
    }
    Set<UUID> eligibleParticipants() {
        var ids = new HashSet<UUID>();
        for (var entry : contributors.entrySet()) {
            var actor = serverLevel().getServer().getPlayerList().getPlayer(entry.getKey()); var credit = entry.getValue();
            if (participant(actor) && now() >= credit.tick && now() - credit.tick <= BossConfig.CREDIT_WINDOW.get() && (credit.damage >= BossConfig.MIN_DAMAGE.get() || credit.support >= 2)) ids.add(entry.getKey());
        }
        return Set.copyOf(ids);
    }
    void noteDamage(ServerPlayer actor, double amount) {
        if (!participant(actor)) return; var old = contributors.get(actor.getUUID());
        contributors.put(actor.getUUID(), new Contribution(now(), Math.min(1_000_000, old.damage + amount), old.support));
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep(); if (retained != null) return;
        var service = BossArenaService.forServer(serverLevel().getServer());
        if (service == null || !service.authorized(this)) { discard(); return; }
        bar.setProgress(Math.clamp(getHealth() / getMaxHealth(), 0, 1));
        if (vulnerable > 0) vulnerable--; if (controlImmunity > 0) controlImmunity--; energy = Math.min(100, energy + BossConfig.ENERGY_REGEN.get());
        if (!BossArenaService.bounds(altar).inflate(1).contains(position())) { service.reset(this); return; }
        if (kind == BossKind.FROST_LORD) for (var actor : serverLevel().getEntitiesOfClass(ServerPlayer.class, BossArenaService.bounds(altar))) join(actor);
        var actors = contributors.keySet().stream().map(id -> serverLevel().getServer().getPlayerList().getPlayer(id)).filter(this::participant).toList();
        if (actors.isEmpty()) { getNavigation().stop(); if (++emptyTicks >= BossConfig.RESET_DELAY.get()) service.reset(this); return; }
        emptyTicks = 0;
        if (phase() > 0) {
            int nextPhase = getHealth() <= getMaxHealth() * .3 ? 3 : getHealth() <= getMaxHealth() * .7 ? 2 : 1;
            if (nextPhase > phase()) { entityData.set(PHASE, nextPhase); playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1, kind == BossKind.GUARDIAN ? 1.2f : .7f); }
        }
        if (cooldown > 0) {
            cooldown--;
            if (phase() == 0 && cooldown == 0) {
                scaled = true; getAttribute(Attributes.MAX_HEALTH).setBaseValue(BossConfig.health(actors.size())); setHealth(getMaxHealth()); entityData.set(PHASE, 1);
            }
            return;
        }
        if (windup() > 0) {
            getNavigation().stop(); getLookControl().setLookAt(aim.x, aim.y + 1, aim.z);
            if (tickCount % 4 == 0) serverLevel().sendParticles(kind == BossKind.GUARDIAN ? ParticleTypes.SNOWFLAKE : ParticleTypes.ENCHANT, aim.x, aim.y + .15, aim.z, 4, .6, .05, .6, .01);
            entityData.set(WINDUP, windup() - 1); if (windup() == 0) release(); return;
        }
        var target = actors.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElseThrow(); setTarget(target);
        if (distanceToSqr(target) > 7 || !hasLineOfSight(target)) { getNavigation().moveTo(target, 1); return; }
        var chosen = choose(); if (energy < BossConfig.energy(chosen)) { cooldown = 20; return; }
        energy -= BossConfig.energy(chosen); aim = target.position(); aimedPlayer = target.getUUID();
        entityData.set(ATTACK, chosen.ordinal()); entityData.set(WINDUP, BossConfig.windup(chosen)); getNavigation().stop();
        bar.setName(Component.translatable("entity.legacyoftheforce." + kind.path).append(" — ").append(Component.translatable("boss.legacyoftheforce.attack." + chosen.name().toLowerCase(Locale.ROOT))));
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, .8f, chosen == BossAttack.HEAVY || chosen == BossAttack.NOVA ? .5f : 1.1f);
    }
    private BossAttack choose() {
        int turn = sequence; sequence = (sequence + 1) & Integer.MAX_VALUE;
        if (kind == BossKind.GUARDIAN) {
            if (phase() >= 3 && turn % 3 == 0) return BossAttack.HEAVY;
            if (phase() >= 2 && turn % 4 == 1) return BossAttack.COVER;
            return new BossAttack[]{BossAttack.SLASH, BossAttack.GUARD, BossAttack.FEINT}[turn % 3];
        }
        if (phase() >= 3 && turn % 3 == 0) return BossAttack.NOVA;
        if (phase() >= 2 && turn % 4 == 1) return BossAttack.SHARDS;
        return new BossAttack[]{BossAttack.SLASH, BossAttack.DASH, BossAttack.PULL}[turn % 3];
    }
    private void release() {
        var action = attack(); var target = serverLevel().getServer().getPlayerList().getPlayer(aimedPlayer);
        if (action == BossAttack.COVER) {
            for (var old : serverLevel().getEntitiesOfClass(ArenaIceEntity.class, BossArenaService.bounds(altar), ice -> getUUID().equals(ice.owner()))) old.discard();
            var direction = aim.subtract(position()).multiply(1, 0, 1).normalize(); var side = new Vec3(-direction.z, 0, direction.x);
            for (int sign : new int[]{-1, 1}) ArenaIceEntity.spawn(this, position().add(direction.scale(1.2)).add(side.scale(.55 * sign)), Vec3.ZERO, false);
        } else if (action == BossAttack.SHARDS && participant(target)) {
            for (int sign : new int[]{-1, 1}) {
                var start = position().add(sign * .8, .8, 0); ArenaIceEntity.spawn(this, start, aim.add(0, .8, 0).subtract(start).normalize().scale(BossConfig.ICE_SPEED.get()), true);
            }
        } else if (action == BossAttack.NOVA) {
            for (var actor : serverLevel().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(BossConfig.range(action)))) {
                if (participant(actor) && actor.distanceToSqr(this) <= Math.pow(BossConfig.range(action), 2) && hasLineOfSight(actor)
                        && actor.hurt(damageSources().mobAttack(this), BossConfig.damage(action))) actor.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
            }
        } else if (participant(target) && hasLineOfSight(target) && target.distanceToSqr(this) <= Math.pow(BossConfig.range(action), 2)
                && target.position().distanceToSqr(aim) <= Math.pow(BossConfig.AIM_RADIUS.get(), 2)) {
            if (action == BossAttack.PULL && !target.isPassenger()) {
                var vector = position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(BossConfig.PULL_SPEED.get());
                if (target.level().noCollision(target, target.getBoundingBox().expandTowards(vector))) { target.setDeltaMovement(vector); target.hurtMarked = true; }
            } else if (action == BossAttack.DASH) {
                var vector = aim.subtract(position()).multiply(1, 0, 1).normalize().scale(BossConfig.PULL_SPEED.get());
                if (level().noCollision(this, getBoundingBox().expandTowards(vector))) setDeltaMovement(vector);
                target.hurt(damageSources().mobAttack(this), BossConfig.damage(action));
            } else if (action == BossAttack.SLASH || action == BossAttack.HEAVY) target.hurt(damageSources().mobAttack(this), BossConfig.damage(action));
        }
        if (action == BossAttack.SLASH && combo == 0) { combo = 1; entityData.set(WINDUP, BossConfig.windup(action)); return; }
        combo = 0; cooldown = BossConfig.cooldown(action); entityData.set(ATTACK, BossAttack.NONE.ordinal());
        bar.setName(Component.translatable("entity.legacyoftheforce." + kind.path));
    }
    void parried(ServerPlayer actor) { if (participant(actor)) { vulnerable = BossConfig.VULNERABILITY.get(); support(actor); } }
    @Override public boolean acceptsForceImpulse() { return vulnerable > 0 && controlImmunity == 0; }
    @Override public void forceImpulseCommitted() { vulnerable = 0; controlImmunity = BossConfig.CONTROL_IMMUNITY.get(); }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || retained != null || phase() == 0 || !Float.isFinite(amount) || amount <= 0) return false;
        var service = BossArenaService.forServer(serverLevel().getServer()); if (service == null || !service.authorized(this)) return false;
        if (!(source.getEntity() instanceof ServerPlayer actor) || !join(actor)) return false;
        if (attack() == BossAttack.GUARD && vulnerable == 0) amount *= BossConfig.GUARD_FACTOR.get();
        for (var ice : serverLevel().getEntitiesOfClass(ArenaIceEntity.class, BossArenaService.bounds(altar), e -> e.isAlive() && getUUID().equals(e.owner()) && !e.projectile())) {
            if (ice.getBoundingBox().inflate(.1).clip(actor.getEyePosition(), getEyePosition()).isPresent()) { amount *= BossConfig.GUARD_FACTOR.get(); break; }
        }
        return super.hurt(source, (float)Math.min(amount, getMaxHealth() * BossConfig.HIT_FRACTION.get()));
    }
    @Override public void die(DamageSource source) {
        if (level().isClientSide()) { super.die(source); return; }
        if (dead) return;
        var service = BossArenaService.forServer(serverLevel().getServer());
        if (service == null || !service.authorized(this)) { discard(); return; }
        super.die(source);
        if (!dead) { if (getHealth() <= 0) setHealth(1); return; }
        if (!service.defeated(this)) { dead = false; deathTime = 0; setPose(Pose.STANDING); setHealth(1); }
    }
    @Override protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {}
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public void startSeenByPlayer(ServerPlayer actor) { super.startSeenByPlayer(actor); bar.addPlayer(actor); }
    @Override public void stopSeenByPlayer(ServerPlayer actor) { super.stopSeenByPlayer(actor); bar.removePlayer(actor); }
    @Override public void remove(RemovalReason reason) { bar.removeAllPlayers(); super.remove(reason); }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (retained != null) { tag.put("lotfBoss", retained.copy()); return; }
        var data = new CompoundTag(); data.putInt("schemaVersion", 1); if (altar != null) data.putLong("altar", altar.asLong());
        data.putInt("phase", phase()); data.putInt("attack", attack().ordinal()); data.putInt("windup", windup()); data.putInt("cooldown", cooldown);
        data.putInt("sequence", sequence); data.putInt("vulnerable", vulnerable); data.putInt("controlImmunity", controlImmunity); data.putDouble("energy", energy); data.putBoolean("scaled", scaled);
        data.putInt("combo", combo);
        data.putDouble("aimX", aim.x); data.putDouble("aimY", aim.y); data.putDouble("aimZ", aim.z); if (aimedPlayer != null) data.putUUID("aimedPlayer", aimedPlayer);
        var list = new ListTag(); contributors.forEach((id, credit) -> { var c = new CompoundTag(); c.putUUID("player", id); c.putLong("tick", credit.tick); c.putDouble("damage", credit.damage); c.putInt("support", credit.support); list.add(c); }); data.put("contributors", list); tag.put("lotfBoss", data);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); var data = tag.getCompound("lotfBoss");
        try {
            if (data.getInt("schemaVersion") != 1 || !data.contains("altar", Tag.TAG_LONG) || data.sizeInBytes() > 16384 || data.getList("contributors", Tag.TAG_COMPOUND).size() > 16
                    || data.getInt("phase") < 0 || data.getInt("phase") > 3 || data.getInt("attack") < 0 || data.getInt("attack") >= BossAttack.values().length
                    || data.getInt("windup") < 0 || data.getInt("windup") > 200 || data.getInt("cooldown") < 0 || data.getInt("cooldown") > 400
                    || !Double.isFinite(data.getDouble("energy")) || data.getDouble("energy") < 0 || data.getDouble("energy") > 100) throw new IllegalArgumentException("Unsupported boss state");
            altar = BlockPos.of(data.getLong("altar")); entityData.set(PHASE, data.getInt("phase")); entityData.set(ATTACK, data.getInt("attack")); entityData.set(WINDUP, data.getInt("windup")); cooldown = data.getInt("cooldown");
            sequence = Math.max(0, data.getInt("sequence")); vulnerable = Math.clamp(data.getInt("vulnerable"), 0, 100); controlImmunity = Math.clamp(data.getInt("controlImmunity"), 0, 400); energy = data.getDouble("energy"); scaled = data.getBoolean("scaled");
            combo = Math.clamp(data.getInt("combo"), 0, 1);
            aim = new Vec3(data.getDouble("aimX"), data.getDouble("aimY"), data.getDouble("aimZ")); if (!Double.isFinite(aim.lengthSqr())) throw new IllegalArgumentException("Invalid aim"); aimedPlayer = data.hasUUID("aimedPlayer") ? data.getUUID("aimedPlayer") : null;
            contributors.clear(); for (var raw : data.getList("contributors", Tag.TAG_COMPOUND)) { var c = (CompoundTag)raw; double damage = c.getDouble("damage"); if (!c.hasUUID("player") || !Double.isFinite(damage) || damage < 0 || c.getLong("tick") < 0 || c.getInt("support") < 0 || c.getInt("support") > 64) throw new IllegalArgumentException("Invalid participation"); contributors.put(c.getUUID("player"), new Contribution(c.getLong("tick"), damage, c.getInt("support"))); }
        } catch (RuntimeException malformed) { retained = data.copy(); setNoAi(true); setInvulnerable(true); }
    }
}
