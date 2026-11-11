package com.legacyoftheforce.speeder;

import com.legacyoftheforce.core.api.ModificationKind;
import com.legacyoftheforce.core.api.ProtectedWorldService;
import com.legacyoftheforce.core.api.ForceAction;
import com.legacyoftheforce.core.api.ForceTargetAdapter;
import com.legacyoftheforce.core.api.VehicleForceTargetProvider;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.*;

/** The pilot sends intentions; this entity never accepts a client-controlled vehicle position. */
public final class SpeederEntity extends Entity implements VehicleForceTargetProvider {
    private static final EntityDataAccessor<CompoundTag> DESIGN = SynchedEntityData.defineId(SpeederEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Float> CHARGE = SynchedEntityData.defineId(SpeederEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(SpeederEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<CompoundTag> HEALTH = SynchedEntityData.defineId(SpeederEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private VehicleState state = new VehicleState(new CompoundTag());
    private final ProtectedWorldService protection;
    private SpeederMotion.Input intent = SpeederMotion.Input.parked();
    private long lastInput = Long.MIN_VALUE;
    private long nextDamage;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYaw, lerpPitch;
    private long retirementEpoch = -1;
    private SpeederRetirement.Status retirementStatus = SpeederRetirement.Status.ABSENT;
    private boolean retired() {
        if (!(level() instanceof ServerLevel level)) return false;
        long epoch = SpeederRetirement.epoch(level.getServer());
        if (retirementEpoch != epoch) {
            retirementStatus = state.snapshot().map(s -> SpeederRetirement.status(level.getServer(), getUUID(), s.owner())).orElse(SpeederRetirement.Status.UNSUPPORTED);
            retirementEpoch = epoch;
        }
        return retirementStatus != SpeederRetirement.Status.ABSENT;
    }

    public SpeederEntity(EntityType<? extends SpeederEntity> type, Level level) { this(type, level, new VanillaProtectedWorldService()); }
    SpeederEntity(EntityType<? extends SpeederEntity> type, Level level, ProtectedWorldService protection) {
        super(type, level); this.protection = Objects.requireNonNull(protection); blocksBuilding = true;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DESIGN, new CompoundTag()); builder.define(CHARGE, 0f); builder.define(SPEED, 0f); builder.define(HEALTH, new CompoundTag());
    }
    public SpeederBlueprint design() { return new SpeederBlueprint(entityData.get(DESIGN)); }
    public Optional<SpeederBlueprint.Plan> visualPlan() {
        return design().plan().filter(plan -> plan.width() <= 5 && plan.height() <= 3 && plan.depth() <= 3 && plan.modules().size() <= 45
                && plan.modules().stream().allMatch(part -> part.at().x() >= 0 && part.at().x() < plan.width()
                && part.at().y() >= 0 && part.at().y() < plan.height() && part.at().z() >= 0 && part.at().z() < plan.depth()));
    }
    public float charge() { return entityData.get(CHARGE); }
    public float speed() { return entityData.get(SPEED); }
    public VehicleState savedState() { return state; }
    @Override public ForceTargetAdapter forceTarget(ProtectedWorldService policy) {
        return new ForceTargetAdapter() {
            @Override public UUID id() { return getUUID(); }
            @Override public double effectiveMass() {
                return state.snapshot().flatMap(s -> SpeederPerformance.inspect(s, SpeederCatalog.definitions()))
                        .map(p -> p.stats().mass() + getPassengers().size() * SpeederConfig.PILOT_MASS.get()).orElse(Double.NaN);
            }
            @Override public double resistance() { return 0; }
            @Override public boolean canManipulate(ServerPlayer actor, ForceAction action) {
                if (actor == null || !actor.server.isSameThread() || !ownedBy(actor) || actor.isPassenger() || !getPassengers().isEmpty()
                        || !(level() instanceof ServerLevel level) || level.getEntity(getUUID()) != SpeederEntity.this
                        || !Double.isFinite(getDeltaMovement().lengthSqr()) || (action != ForceAction.STABILIZE && action != ForceAction.BRAKE)) return false;
                var snapshot = state.snapshot().orElseThrow();
                if (!BlueprintValidator.inspect(snapshot.blueprint(), SpeederCatalog.definitions(), SpeederConfig.limits()).valid()
                        || !SpeederRetirement.supportedStorage(SpeederEntity.this)
                        || SpeederPerformance.inspect(snapshot, SpeederCatalog.definitions()).orElseThrow().wrecked()
                        || !loadedAndAllowed(getBoundingBox(), actor)) return false;
                for (var pos : BlockPos.betweenClosedStream(getBoundingBox().deflate(.001)).toList())
                    if (!policy.mayMoveEntity(actor, SpeederEntity.this, pos)) return false;
                return Math.abs(action == ForceAction.STABILIZE ? snapshot.motion().verticalSpeed() : snapshot.motion().speed()) > .0001;
            }
            @Override public void dampMotion(ServerPlayer actor, ForceAction action, double retention) {
                if (!Double.isFinite(retention) || retention < 0 || retention > 1 || !canManipulate(actor, action)) throw new IllegalArgumentException("Invalid vehicle damping");
                var snapshot = state.snapshot().orElseThrow(); var motion = snapshot.motion(); var delta = getDeltaMovement();
                boolean vertical = action == ForceAction.STABILIZE;
                state = state.withMotion(new VehicleState.Pose(level().dimension().location(), getX(), getY(), getZ()),
                        new SpeederMotion.State(vertical ? motion.speed() : motion.speed() * retention,
                                vertical ? motion.verticalSpeed() * retention : motion.verticalSpeed(), motion.yaw(), motion.energy()));
                setDeltaMovement(vertical ? new Vec3(delta.x, delta.y * retention, delta.z) : new Vec3(delta.x * retention, delta.y, delta.z * retention));
                intent = SpeederMotion.Input.parked(); lastInput = Long.MIN_VALUE; hasImpulse = true; project();
            }
        };
    }
    public int moduleHealth(int index) { var values = entityData.get(HEALTH).getIntArray("values"); return index >= 0 && index < values.length ? values[index] : 100; }
    public int integrity() { var values = entityData.get(HEALTH).getIntArray("values"); return values.length == 0 ? 0 : Arrays.stream(values).sum() / values.length; }
    public int damagedModules() { return (int)Arrays.stream(entityData.get(HEALTH).getIntArray("values")).filter(hp -> hp < 100).count(); }

    /** Package-private: only assembly/recovery and server integration tests may initialize an instance. */
    void initialize(VehicleState initial) {
        if (level().isClientSide || state.snapshot().isPresent()) throw new IllegalStateException("Vehicle already initialized or wrong side");
        var snapshot = initial.snapshot().orElseThrow();
        var validated = BlueprintValidator.inspect(snapshot.blueprint(), SpeederCatalog.definitions(), SpeederConfig.limits());
        if (!validated.valid() || !snapshot.pose().dimension().equals(level().dimension().location())
                || snapshot.motion().energy() > validated.stats().capacity()) throw new IllegalArgumentException("Invalid initial vehicle");
        state = initial; setUUID(snapshot.id()); setPos(snapshot.pose().x(), snapshot.pose().y(), snapshot.pose().z());
        setYRot((float)snapshot.motion().yaw()); project(); refreshDimensions();
    }
    private void project() {
        state.snapshot().ifPresent(snapshot -> {
            var layout = snapshot.blueprint().tag(); if (!layout.equals(entityData.get(DESIGN))) entityData.set(DESIGN, layout);
            entityData.set(CHARGE, (float)snapshot.motion().energy()); entityData.set(SPEED, (float)snapshot.motion().speed());
            var health = new CompoundTag(); health.putIntArray("values", snapshot.health()); if (!health.equals(entityData.get(HEALTH))) entityData.set(HEALTH, health);
        });
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.put("vehicleState", state.tag()); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        state = new VehicleState(tag.getCompound("vehicleState")); intent = SpeederMotion.Input.parked(); lastInput = Long.MIN_VALUE;
        retirementEpoch = -1;
        // Vanilla pose wins when loading the entity; its own UUID must agree before gameplay is allowed.
        project(); refreshDimensions();
    }
    private Optional<VehicleState.Snapshot> supported() {
        var snapshot = state.snapshot();
        if (retired() || state.tag().contains("assemblyId") || state.tag().contains("rechargeId") || snapshot.isEmpty() || !snapshot.get().id().equals(getUUID()) || snapshot.get().garaged()
                || !snapshot.get().pose().dimension().equals(level().dimension().location())) return Optional.empty();
        return snapshot;
    }
    void activateAssembly(UUID operation) {
        var raw = state.tag();
        if (!raw.hasUUID("assemblyId") || !operation.equals(raw.getUUID("assemblyId"))) throw new IllegalStateException("Assembly activation mismatch");
        raw.remove("assemblyId"); state = new VehicleState(raw); project();
    }
    void applyRechargeState(VehicleState updated) {
        if (level().isClientSide || !updated.snapshot().orElseThrow().id().equals(getUUID())) throw new IllegalArgumentException("Invalid recharge state");
        state = updated; intent = SpeederMotion.Input.parked(); lastInput = Long.MIN_VALUE; project();
    }
    private boolean ownedBy(ServerPlayer actor) {
        return supported().map(snapshot -> snapshot.owner().equals(actor.getUUID())).orElse(false)
                && actor.isAlive() && !actor.isSpectator() && !actor.isFakePlayer() && actor.serverLevel() == level();
    }
    private boolean owner(ServerPlayer actor) {
        return ownedBy(actor) && state.snapshot().flatMap(snapshot -> SpeederPerformance.inspect(snapshot, SpeederCatalog.definitions()))
                .map(performance -> performance.pilotWorking() && !performance.wrecked()).orElse(false);
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (!(level() instanceof ServerLevel level) || !level.getServer().isSameThread() || !Float.isFinite(amount) || amount <= 0
                || isInvulnerableTo(source) || !(source.getEntity() instanceof ServerPlayer actor) || source.getDirectEntity() != actor
                || !ownedBy(actor) || actor.isPassenger() || actor.distanceToSqr(this) > SpeederConfig.HIT_RANGE.get() * SpeederConfig.HIT_RANGE.get() || !actor.hasLineOfSight(this)
                || !protection.mayModify(actor, level, blockPosition(), ModificationKind.PLACE)) return false;
        var plan = state.snapshot().orElseThrow().blueprint().plan().orElseThrow();
        double centerX = (plan.modules().stream().mapToInt(p -> p.at().x()).min().orElse(0) + plan.modules().stream().mapToInt(p -> p.at().x()).max().orElse(0)) / 2.0;
        double centerZ = (plan.modules().stream().mapToInt(p -> p.at().z()).min().orElse(0) + plan.modules().stream().mapToInt(p -> p.at().z()).max().orElse(0)) / 2.0;
        int floor = plan.modules().stream().mapToInt(p -> p.at().y()).min().orElse(0), closest = 0; double nearest = Double.POSITIVE_INFINITY;
        double yaw = Math.toRadians(180 - getYRot()); var hitSide = actor.position().add(0, .25, 0);
        for (int i = 0; i < plan.modules().size(); i++) {
            var cell = plan.modules().get(i).at(); double x = (cell.x() - centerX) * .5, z = (cell.z() - centerZ) * .5;
            var point = position().add(x * Math.cos(yaw) + z * Math.sin(yaw), (cell.y() - floor) * .5 + .25, -x * Math.sin(yaw) + z * Math.cos(yaw));
            double distance = point.distanceToSqr(hitSide); if (distance < nearest) { nearest = distance; closest = i; }
        }
        return damageModule(closest, (int)Math.min(SpeederConfig.MAX_HIT_DAMAGE.get(), Math.ceil(amount * SpeederConfig.HIT_DAMAGE_SCALE.get())));
    }
    private boolean damageModule(int index, int loss) {
        var snapshot = supported(); if (snapshot.isEmpty() || loss <= 0 || level().getGameTime() < nextDamage) return false;
        if (!BlueprintValidator.inspect(snapshot.get().blueprint(), SpeederCatalog.definitions(), SpeederConfig.limits()).valid()) return false;
        var health = new ArrayList<>(snapshot.get().health()); if (health.get(index) == 0) return false;
        health.set(index, Math.max(0, health.get(index) - loss));
        var damaged = state.withHealth(health, snapshot.get().motion().energy());
        var performance = SpeederPerformance.inspect(damaged.snapshot().orElseThrow(), SpeederCatalog.definitions()); if (performance.isEmpty()) return false;
        state = damaged.withHealth(health, Math.min(snapshot.get().motion().energy(), performance.get().stats().capacity()));
        nextDamage = level().getGameTime() + SpeederConfig.DAMAGE_INTERVAL.get();
        if (performance.get().wrecked() || !performance.get().pilotWorking()) ejectPassengers(); project(); return true;
    }
    @Override public InteractionResult interact(net.minecraft.world.entity.player.Player actor, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(actor instanceof ServerPlayer player) || !ownedBy(player) || !getPassengers().isEmpty()
                || !protection.mayModify(player, player.serverLevel(), blockPosition(), ModificationKind.MOVE_THROUGH)) return InteractionResult.FAIL;
        if (!owner(player)) {
            player.sendSystemMessage(Component.translatable("message.legacyoftheforce.speeder_disabled")); return InteractionResult.FAIL;
        }
        if (!BlueprintValidator.inspect(state.snapshot().orElseThrow().blueprint(), SpeederCatalog.definitions(), SpeederConfig.limits()).valid()) {
            player.sendSystemMessage(Component.translatable("message.legacyoftheforce.speeder_unsupported")); return InteractionResult.FAIL;
        }
        intent = SpeederMotion.Input.parked(); lastInput = Long.MIN_VALUE;
        return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }
    @Override protected boolean canAddPassenger(Entity passenger) { return passenger instanceof ServerPlayer actor && owner(actor) && getPassengers().isEmpty(); }
    // Deliberately null: vanilla ServerboundMoveVehiclePacket must never authorize a pilot's position.
    @Override public LivingEntity getControllingPassenger() { return null; }
    // World-owned persistence: never embed another mutable vehicle copy in the pilot's RootVehicle save.
    // Vanilla also uses this virtual hook to skip/remove mounts from entity storage; speeders stay in the world.
    @Override public boolean hasExactlyOnePlayerPassenger() { return false; }
    boolean acceptInput(ServerPlayer actor, double throttle, double steering) {
        if (!owner(actor) || getFirstPassenger() != actor || !Double.isFinite(throttle) || !Double.isFinite(steering)
                || Math.abs(throttle) > 1 || Math.abs(steering) > 1) return false;
        intent = new SpeederMotion.Input(throttle, steering, true); lastInput = level().getGameTime(); return true;
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (lerpSteps > 0) { lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYaw, lerpPitch); lerpSteps--; }
            return;
        }
        if (retired() && retirementStatus == SpeederRetirement.Status.RETIRED) { ejectPassengers(); discard(); return; }
        var snapshot = supported(); if (snapshot.isEmpty()) { ejectPassengers(); return; }
        var layout = snapshot.get().blueprint(); var validated = BlueprintValidator.inspect(layout, SpeederCatalog.definitions(), SpeederConfig.limits());
        if (!validated.valid() || snapshot.get().motion().energy() > validated.stats().capacity()) { ejectPassengers(); return; }
        var performance = SpeederPerformance.inspect(snapshot.get(), SpeederCatalog.definitions()).orElseThrow();
        if (snapshot.get().motion().energy() > performance.stats().capacity()) {
            state = state.withHealth(snapshot.get().health(), performance.stats().capacity()); snapshot = supported();
        }
        var pilot = getFirstPassenger() instanceof ServerPlayer actor && owner(actor) ? actor : null;
        if (getFirstPassenger() != null && pilot == null) ejectPassengers();
        // A dropped/stalled input stream brakes and parks after eight server ticks.
        var command = pilot != null && level().getGameTime() - lastInput >= 0 && level().getGameTime() - lastInput <= 8 ? intent : SpeederMotion.Input.parked();
        var stats = performance.stats();
        var loadedStats = new BlueprintValidator.Stats(stats.mass() + getPassengers().size() * SpeederConfig.PILOT_MASS.get(), stats.thrust(), stats.lift(), stats.capacity(), stats.stability());
        var step = SpeederMotion.tick(snapshot.get().motion(), command, loadedStats, clearance(), SpeederConfig.motion(), performance.engineFactor());
        double dx = step.dx(), dy = step.dy(), dz = step.dz();
        var target = getBoundingBox().move(dx, dy, dz);
        boolean blocked = !loadedAndAllowed(target, pilot);
        if (blocked) { dx = 0; dy = 0; dz = 0; }
        var before = position(); setYRot((float)step.state().yaw());
        setDeltaMovement(dx, dy, dz); move(MoverType.SELF, getDeltaMovement());
        var actual = position().subtract(before);
        double horizontal = blocked || horizontalCollision || Math.abs(actual.x - dx) > 1e-5 || Math.abs(actual.z - dz) > 1e-5 ? 0 : step.state().speed();
        double vertical = blocked || verticalCollision || Math.abs(actual.y - dy) > 1e-5 ? 0 : step.state().verticalSpeed();
        // Void safety keeps the paid instance addressable instead of deleting it as a generic falling entity.
        if (getY() < level().getMinBuildHeight()) { setPos(getX(), level().getMinBuildHeight(), getZ()); vertical = 0; horizontal = 0; }
        var motion = new SpeederMotion.State(horizontal, vertical, step.state().yaw(), step.state().energy());
        state = state.withMotion(new VehicleState.Pose(level().dimension().location(), getX(), getY(), getZ()), motion);
        if (!blocked && horizontalCollision && pilot != null && Math.abs(step.state().speed()) > SpeederConfig.COLLISION_THRESHOLD.get()
                && protection.mayModify(pilot, (ServerLevel)level(), blockPosition(), ModificationKind.PLACE)) {
            var parts = layout.plan().orElseThrow().modules();
            for (int i = 0; i < parts.size(); i++) if (SpeederCatalog.definitions().get(parts.get(i).kind()).family() == SpeederModule.Family.CHASSIS && state.snapshot().orElseThrow().health().get(i) > 0) {
                damageModule(i, (int)Math.min(SpeederConfig.MAX_HIT_DAMAGE.get(), Math.ceil((Math.abs(step.state().speed()) - SpeederConfig.COLLISION_THRESHOLD.get()) * SpeederConfig.COLLISION_DAMAGE_SCALE.get()))); break;
            }
        }
        setDeltaMovement(actual); project();
    }
    private OptionalDouble clearance() {
        var bottom = position(); var end = bottom.add(0, -6, 0);
        if (!level().hasChunkAt(BlockPos.containing(end))) return OptionalDouble.empty();
        var hit = level().clip(new ClipContext(bottom, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.BLOCK ? OptionalDouble.of(Math.clamp(bottom.y - hit.getLocation().y, 0, 6)) : OptionalDouble.empty();
    }
    private boolean loadedAndAllowed(AABB box, ServerPlayer pilot) {
        if (!level().getWorldBorder().isWithinBounds(box) || box.minY < level().getMinBuildHeight() || box.maxY >= level().getMaxBuildHeight()) return false;
        var minimum = BlockPos.containing(box.minX, box.minY, box.minZ);
        var maximum = BlockPos.containing(Math.nextDown(box.maxX), Math.nextDown(box.maxY), Math.nextDown(box.maxZ));
        long cells = (long)(maximum.getX() - minimum.getX() + 1) * (maximum.getY() - minimum.getY() + 1) * (maximum.getZ() - minimum.getZ() + 1);
        if (cells < 1 || cells > 64) return false;
        for (var pos : BlockPos.betweenClosed(minimum, maximum)) {
            if (!level().hasChunkAt(pos) || pilot != null && !protection.mayModify(pilot, (ServerLevel)level(), pos, ModificationKind.MOVE_THROUGH)) return false;
        }
        return true;
    }
    @Override public EntityDimensions getDimensions(Pose pose) {
        var plan = visualPlan(); if (plan.isEmpty() || plan.get().modules().isEmpty()) return EntityDimensions.scalable(1.5f, 1);
        var parts = plan.get().modules();
        int width = parts.stream().mapToInt(p -> p.at().x()).max().orElse(0) - parts.stream().mapToInt(p -> p.at().x()).min().orElse(0) + 1;
        int depth = parts.stream().mapToInt(p -> p.at().z()).max().orElse(0) - parts.stream().mapToInt(p -> p.at().z()).min().orElse(0) + 1;
        int height = parts.stream().mapToInt(p -> p.at().y()).max().orElse(0) - parts.stream().mapToInt(p -> p.at().y()).min().orElse(0) + 1;
        return EntityDimensions.scalable(Math.max(width, depth) * .5f, height * .5f);
    }
    @Override protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        var plan = visualPlan(); if (plan.isEmpty() || plan.get().modules().isEmpty()) return new Vec3(0, dimensions.height(), 0);
        var parts = plan.get().modules(); var seat = parts.stream().filter(part -> part.kind().equals(SpeederContent.id("seat_pilot"))).findFirst();
        if (seat.isEmpty()) return new Vec3(0, dimensions.height(), 0);
        double centerX = (parts.stream().mapToInt(p -> p.at().x()).min().orElse(0) + parts.stream().mapToInt(p -> p.at().x()).max().orElse(0)) / 2.0;
        double centerZ = (parts.stream().mapToInt(p -> p.at().z()).min().orElse(0) + parts.stream().mapToInt(p -> p.at().z()).max().orElse(0)) / 2.0;
        int floor = parts.stream().mapToInt(p -> p.at().y()).min().orElse(0);
        // Match the seat model's cushion top (7/16), not the top of its abstract cell.
        double cushionTop = (seat.get().at().y() - floor) * .5 + .25 + (7.0 / 16 - .5) * .5;
        // Minecraft 1.21.1: humanoid hips at 12/16, renderer translation 1.501, player scale .9375.
        double riderScale = passenger instanceof LivingEntity living ? living.getScale() : 1;
        double hipsAboveFeet = (1.501 - 12.0 / 16) * .9375 * riderScale;
        double anchorY = cushionTop - hipsAboveFeet + passenger.getVehicleAttachmentPoint(this).y;
        return new Vec3((seat.get().at().x() - centerX) * .5, anchorY,
                (seat.get().at().z() - centerZ) * .5).yRot((float)Math.toRadians(180 - getYRot()));
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) { super.onSyncedDataUpdated(accessor); if (DESIGN.equals(accessor)) refreshDimensions(); }
    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        lerpX = x; lerpY = y; lerpZ = z; lerpYaw = yaw; lerpPitch = pitch; lerpSteps = Math.clamp(steps, 1, 5);
    }
    @Override public boolean canUsePortal(boolean allowPassengers) { return false; }
    @Override public Entity changeDimension(DimensionTransition transition) { return null; }
    @Override protected void onBelowWorld() { /* Preserve the instance; server tick clamps its pose to the world floor. */ }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeCollidedWith() { return !isRemoved(); }
    @Override public boolean canCollideWith(Entity other) { return !isPassengerOfSameVehicle(other) && (other.canBeCollidedWith() || other.isPushable()); }
    @Override public boolean isPushable() { return false; }
}
