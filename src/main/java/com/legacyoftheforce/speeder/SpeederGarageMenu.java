package com.legacyoftheforce.speeder;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class SpeederGarageMenu extends AbstractContainerMenu {
    public enum Status { EDITING, SAVED, HOLD_BLUEPRINT, READ_ONLY, CHANGED, TOO_FAR, COOLDOWN,
        BUILD_BUILT, BUILD_INVALID, BUILD_SAVE_FIRST, BUILD_CLEAR_CURSOR, BUILD_MISSING, BUILD_SPACE, BUILD_PERMISSION, BUILD_BUSY, BUILD_UNAVAILABLE, BUILD_PENDING }
    private final Inventory inventory;
    private final ContainerLevelAccess access;
    private SpeederBlueprint draft;
    private final UUID originalId;
    private final boolean readOnly;
    private long nextAction;
    private boolean assemblyAttemptClosed;
    private UUID inspectedVehicle;
    private long nextInspection;
    private final DataSlot diagnostic = DataSlot.standalone(), diagnosticStatus = DataSlot.standalone(), integrity = DataSlot.standalone(), damaged = DataSlot.standalone(), modules = DataSlot.standalone();
    private final DataSlot[] vehicleId = new DataSlot[8];
    private final ProjectedFloat instanceEnergy = new ProjectedFloat(), instanceCapacity = new ProjectedFloat();
    private final DataSlot[] cells = new DataSlot[45], owned = new DataSlot[4];
    private final DataSlot selected = DataSlot.standalone(), rotation = DataSlot.standalone(), status = DataSlot.standalone(), error = DataSlot.standalone();
    private final ProjectedFloat mass = new ProjectedFloat(), thrust = new ProjectedFloat(), lift = new ProjectedFloat(), capacity = new ProjectedFloat(), stability = new ProjectedFloat();
    private final class ProjectedFloat {
        private final DataSlot high = DataSlot.standalone(), low = DataSlot.standalone();
        private ProjectedFloat() { addDataSlot(high); addDataSlot(low); }
        private void set(double value) { int bits = Float.floatToIntBits((float) value); high.set((short) (bits >>> 16)); low.set((short) bits); }
        private float get() { return Float.intBitsToFloat((high.get() & 0xffff) << 16 | low.get() & 0xffff); }
    }
    public SpeederGarageMenu(int id, Inventory inventory) { this(id, inventory, ContainerLevelAccess.NULL); }
    public SpeederGarageMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(SpeederContent.MENU.get(), id); this.inventory = inventory; this.access = access;
        var held = inventory.getSelected(); if (inventory.player instanceof ServerPlayer actor) BlueprintItem.identify(held, actor);
        var data = held.is(SpeederContent.BLUEPRINT_ITEM.get()) && held.getCount() == 1 ? held.get(SpeederContent.BLUEPRINT.get()) : null;
        originalId = data != null && data.plan().isPresent() ? data.plan().get().id() : null;
        readOnly = data != null && (data.plan().isEmpty() || data.plan().get().width() != 5 || data.plan().get().height() != 3 || data.plan().get().depth() != 3
                || data.plan().get().modules().stream().anyMatch(module -> SpeederContent.index(module.kind()) < 0));
        draft = data != null && !readOnly ? data : SpeederBlueprint.blank(UUID.randomUUID(), inventory.player.getUUID());
        for (int i = 0; i < cells.length; i++) { cells[i] = DataSlot.standalone(); addDataSlot(cells[i]); }
        for (int i = 0; i < owned.length; i++) { owned[i] = DataSlot.standalone(); addDataSlot(owned[i]); }
        addDataSlot(selected); addDataSlot(rotation); addDataSlot(status); addDataSlot(error);
        for (var projection : List.of(diagnostic, diagnosticStatus, integrity, damaged, modules)) addDataSlot(projection);
        for (int i = 0; i < vehicleId.length; i++) { vehicleId[i] = DataSlot.standalone(); addDataSlot(vehicleId[i]); }
        status.set((readOnly ? Status.READ_ONLY : originalId == null ? Status.HOLD_BLUEPRINT : Status.EDITING).ordinal());
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 47 + col * 18, 142 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 47 + col * 18, 200));
    }
    public int moduleAt(int cell) { return cell >= 0 && cell < cells.length ? (cells[cell].get() & 7) - 1 : -1; }
    public int rotationAt(int cell) { return cell >= 0 && cell < cells.length ? cells[cell].get() >>> 3 & 3 : 0; }
    public int selected() { return selected.get(); }
    public int rotation() { return rotation.get(); }
    public boolean diagnostic() { return diagnostic.get() != 0; }
    public GarageDiagnostics.Status diagnosticStatus() { return GarageDiagnostics.Status.values()[Math.clamp(diagnosticStatus.get(), 0, GarageDiagnostics.Status.values().length - 1)]; }
    public float instanceEnergy() { return instanceEnergy.get(); }
    public float instanceCapacity() { return instanceCapacity.get(); }
    public int integrity() { return integrity.get(); }
    public int damaged() { return damaged.get(); }
    public int modules() { return modules.get(); }
    public UUID inspectedVehicle() {
        long high = 0, low = 0;
        for (int i = 0; i < 4; i++) { high = high << 16 | vehicleId[i].get() & 0xffffL; low = low << 16 | vehicleId[i + 4].get() & 0xffffL; }
        return new UUID(high, low);
    }
    public int owned(int part) { return owned[part].get(); }
    public int required(int part) { int count = 0; for (int i = 0; i < cells.length; i++) if (moduleAt(i) == part) count++; return count; }
    public float mass() { return mass.get(); } public float thrust() { return thrust.get(); }
    public float lift() { return lift.get(); } public float capacity() { return capacity.get(); } public float stability() { return stability.get(); }
    public Status status() { return Status.values()[Math.clamp(status.get(), 0, Status.values().length - 1)]; }
    public BlueprintValidator.Error error() { return BlueprintValidator.Error.values()[Math.clamp(error.get(), 0, BlueprintValidator.Error.values().length - 1)]; }
    @Override public void broadcastChanges() {
        if (!inventory.player.level().isClientSide()) {
            if (diagnostic() && inventory.player instanceof ServerPlayer actor && actor.server.overworld().getGameTime() >= nextInspection) {
                var report = access.evaluate((level, pos) -> GarageDiagnostics.inspect(actor, pos, inspectedVehicle,
                        new com.legacyoftheforce.core.security.VanillaProtectedWorldService()), GarageDiagnostics.Report.unavailable(GarageDiagnostics.Status.DENIED));
                if (inspectedVehicle == null && report.vehicle() != null) inspectedVehicle = report.vehicle();
                diagnosticStatus.set(report.status().ordinal()); instanceEnergy.set(report.energy()); instanceCapacity.set(report.capacity());
                integrity.set(report.integrity()); damaged.set(report.damaged()); modules.set(report.modules());
                var id = report.vehicle() == null ? new UUID(0, 0) : report.vehicle();
                for (int i = 0; i < 4; i++) { vehicleId[i].set((short)(id.getMostSignificantBits() >>> (48 - i * 16))); vehicleId[i + 4].set((short)(id.getLeastSignificantBits() >>> (48 - i * 16))); }
                nextInspection = actor.server.overworld().getGameTime() + 10;
            }
            for (var cell : cells) cell.set(0);
            draft.plan().ifPresent(plan -> plan.modules().forEach(module -> {
                var at = module.at(); int index = at.x() + 5 * (at.z() + 3 * at.y()); int kind = SpeederContent.index(module.kind());
                if (at.x() >= 0 && at.x() < 5 && at.y() >= 0 && at.y() < 3 && at.z() >= 0 && at.z() < 3 && kind >= 0)
                    cells[index].set(kind + 1 | module.rotation() << 3);
            }));
            for (int part = 0; part < 4; part++) { int count = 0; for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (inventory.getItem(slot).is(SpeederContent.part(part))) count += inventory.getItem(slot).getCount(); owned[part].set(count); }
            var result = BlueprintValidator.inspect(draft, SpeederCatalog.definitions(), SpeederConfig.limits()); error.set(result.error().ordinal());
            mass.set(result.stats().mass()); thrust.set(result.stats().thrust()); lift.set(result.stats().lift()); capacity.set(result.stats().capacity()); stability.set(result.stats().stability());
        }
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer actor) || actor.isFakePlayer() || !actor.server.isSameThread() || actor.containerMenu != this || inventory.player != actor) return false;
        if (!stillValid(actor)) { status.set(Status.TOO_FAR.ordinal()); return false; }
        if (!(button >= 0 && button < 45) && !(button >= 50 && button <= 59)) return false;
        long tick = actor.server.overworld().getGameTime();
        if (tick < nextAction) { status.set(Status.COOLDOWN.ordinal()); return false; }
        if (button == 59) {
            diagnostic.set(diagnostic() ? 0 : 1); inspectedVehicle = null; nextInspection = 0; nextAction = tick + 10;
            broadcastChanges(); return true;
        }
        if (diagnostic()) return false;
        if (readOnly) { status.set(Status.READ_ONLY.ordinal()); return false; }
        if (button == 58) {
            if (assemblyAttemptClosed) return false;
            var held = actor.getMainHandItem(); var current = held.get(SpeederContent.BLUEPRINT.get());
            if (originalId == null || current == null || current.plan().isEmpty() || !originalId.equals(current.plan().get().id())) { status.set(Status.CHANGED.ordinal()); return false; }
            var result = access.evaluate((level, pos) -> SpeederAssemblyLifecycle.build(actor, pos, draft), SpeederAssemblyService.Result.UNAVAILABLE);
            assemblyAttemptClosed = result == SpeederAssemblyService.Result.BUILT || result == SpeederAssemblyService.Result.PENDING;
            status.set(Status.valueOf("BUILD_" + result.name()).ordinal()); nextAction = tick + 10; broadcastChanges();
            return result == SpeederAssemblyService.Result.BUILT;
        }
        if (button == 56) {
            var held = actor.getMainHandItem(); var current = held.get(SpeederContent.BLUEPRINT.get());
            if (!held.is(SpeederContent.BLUEPRINT_ITEM.get()) || held.getCount() != 1 || originalId == null) { status.set(Status.HOLD_BLUEPRINT.ordinal()); return false; }
            if (current == null || current.plan().isEmpty() || !originalId.equals(current.plan().get().id())) { status.set(Status.CHANGED.ordinal()); return false; }
            var result = BlueprintValidator.inspect(draft, SpeederCatalog.definitions(), SpeederConfig.limits());
            if (!result.valid()) { status.set(Status.EDITING.ordinal()); broadcastChanges(); return false; }
            held.set(SpeederContent.BLUEPRINT.get(), draft); inventory.setChanged(); status.set(Status.SAVED.ordinal()); nextAction = tick + 10;
            actor.inventoryMenu.broadcastChanges(); broadcastChanges(); return true;
        }
        if (button == 57) { draft = draft.withModules(SpeederContent.starterModules()); selected.set(0); rotation.set(0); }
        else if (button >= 50 && button <= 54) selected.set(button - 50);
        else if (button == 55) rotation.set((rotation.get() + 1) % 4);
        else {
            var at = new SpeederBlueprint.Cell(button % 5, button / 15, button / 5 % 3);
            var parts = new ArrayList<>(draft.plan().orElseThrow().modules()); parts.removeIf(part -> part.at().equals(at));
            if (selected.get() < 4) parts.add(new SpeederBlueprint.PlacedModule(SpeederContent.id(SpeederContent.PART_NAMES.get(selected.get())), at, rotation.get()));
            draft = draft.withModules(parts);
        }
        nextAction = tick + 1; status.set(Status.EDITING.ordinal()); broadcastChanges(); return true;
    }
    @Override public boolean stillValid(Player player) {
        return inventory.player == player && player.isAlive() && !player.isSpectator()
                && access.evaluate((level, pos) -> player.level() == level && level.getBlockState(pos).is(SpeederContent.GARAGE.get())
                    && player.canInteractWithBlock(pos, 4) && level.mayInteract(player, pos), player.level().isClientSide());
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem(); var copy = stack.copy();
        if (!moveItemStackTo(stack, index < 27 ? 27 : 0, index < 27 ? 36 : 27, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged(); slot.onTake(player, stack); return copy;
    }
}
