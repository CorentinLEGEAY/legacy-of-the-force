package com.legacyoftheforce.lightsaber;

import java.util.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

/** Virtual recipe selection: components stay in the player's inventory until the atomic server action. */
public final class SaberWorkshopMenu extends AbstractContainerMenu {
    public enum Status { READY, MISSING, FULL, INCOMPATIBLE, TOO_FAR, COOLDOWN, INVALID, NO_SABER, NO_CRYSTAL, DUPLICATE, INSERTED, REPLACED, AWAKENED, SPECIALIZED, RITUAL_MATERIALS, BOND_NOT_READY, MODIFIED, DISMANTLED, MAINTAINED, NO_BLUEPRINT, PLAN_SAVED, PLAN_LOADED, UNCHANGED, TYPE_LOCKED }
    private final Inventory inventory;
    private final ContainerLevelAccess access;
    private final DataSlot emitter = DataSlot.standalone();
    private final DataSlot status = DataSlot.standalone();
    private final DataSlot ritualAmethyst = DataSlot.standalone(), ritualLapis = DataSlot.standalone();
    private final DataSlot condition = DataSlot.standalone(), damage = DataSlot.standalone(), stability = DataSlot.standalone(),reach=DataSlot.standalone(),speed=DataSlot.standalone(),cost=DataSlot.standalone();
    private final List<DataSlot> owned = new ArrayList<>();
    private long nextCraftTick,nextSelection;
    private final DataSlot type=DataSlot.standalone(),secondary=DataSlot.standalone();
    private final DataSlot secondaryOwned=DataSlot.standalone(),typeKnown=DataSlot.standalone();
    private final List<DataSlot> chosen=new ArrayList<>();
    public SaberWorkshopMenu(int id, Inventory inventory) { this(id, inventory, ContainerLevelAccess.NULL); }
    public SaberWorkshopMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(SaberContent.MENU.get(), id); this.inventory = inventory; this.access = access;
        addDataSlot(emitter); addDataSlot(status);addDataSlot(type);addDataSlot(secondary);addDataSlot(secondaryOwned);addDataSlot(typeKnown);
        for(var part:SaberSlot.values()){var choice=DataSlot.standalone();choice.set(SaberContent.PART_NAMES.indexOf(part.key()+"_basic"));chosen.add(choice);addDataSlot(choice);}
        secondary.set(SaberContent.PART_NAMES.indexOf("emitter_twin"));
        var held=inventory.getSelected().get(SaberContent.ASSEMBLY.get());if(held!=null&&held.supported())selectDesign(held);
        for (var slot : SaberSlot.values()) { var count = DataSlot.standalone(); owned.add(count); addDataSlot(count); }
        ritualAmethyst.set(4); ritualLapis.set(2); addDataSlot(ritualAmethyst); addDataSlot(ritualLapis);
        addDataSlot(condition);addDataSlot(damage);addDataSlot(stability);addDataSlot(reach);addDataSlot(speed);addDataSlot(cost);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 47 + col * 18, 142 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 47 + col * 18, 200));
    }
    public int emitterIndex() { return emitter.get(); }
    public int ritualAmethyst() { return ritualAmethyst.get(); }
    public int ritualLapis() { return ritualLapis.get(); }
    public int condition() {return condition.get();}
    public double reach(){return reach.get()/100.0;}public double attacksPerSecond(){return speed.get()/100.0;}public double costMultiplier(){return cost.get()/100.0;}
    public double damage() {return damage.get()/100.0;}
    public double stability() {return stability.get()/100.0;}
    public Status status() { return Status.values()[Math.clamp(status.get(), 0, Status.values().length - 1)]; }
    public int owned(SaberSlot slot) { return owned.get(slot.ordinal()).get(); }
    public int required(SaberSlot slot){return design().bill().getOrDefault(SaberContent.id(partName(slot)),0);}
    public int secondaryOwned(){return secondaryOwned.get();}
    public boolean typeKnown(){return selectedType()==SaberType.SINGLE||typeKnown.get()!=0;}
    public String partName(SaberSlot slot) {int index=chosen.get(slot.ordinal()).get();return index>=0&&index<SaberContent.PART_NAMES.size()&&SaberContent.PART_NAMES.get(index).startsWith(slot.key()+"_")?SaberContent.PART_NAMES.get(index):slot.key()+"_basic";}
    public SaberType selectedType(){return SaberType.values()[Math.clamp(type.get(),0,SaberType.values().length-1)];}
    public String secondaryName(){int i=secondary.get();return i>=0&&i<SaberContent.PART_NAMES.size()?SaberContent.PART_NAMES.get(i):"emitter_twin";}
    private SaberData design(){var parts=new EnumMap<SaberSlot,net.minecraft.resources.ResourceLocation>(SaberSlot.class);for(var slot:SaberSlot.values())parts.put(slot,SaberContent.id(partName(slot)));return SaberData.create(new UUID(0,0),selectedType(),parts,selectedType()==SaberType.DOUBLE?SaberContent.id(secondaryName()):null);}
    private boolean selectDesign(SaberData design){if(!design.supported()||design.bill().keySet().stream().anyMatch(id->SaberContent.partIndex(id)<0))return false;type.set(design.type().ordinal());for(var slot:SaberSlot.values())chosen.get(slot.ordinal()).set(SaberContent.partIndex(design.parts().orElseThrow().get(slot)));secondary.set(design.secondaryEmitter().map(SaberContent::partIndex).orElse(SaberContent.PART_NAMES.indexOf("emitter_twin")));emitter.set(partName(SaberSlot.EMITTER).equals("emitter_claw")?1:0);return true;}
    public boolean previewUsesHeldSaber() { return inventory.getSelected().is(SaberContent.SABER.get()); }
    public ItemStack preview() {
        var stack=new ItemStack(SaberContent.SABER.get());var data=design();var held=inventory.getSelected().get(SaberContent.ASSEMBLY.get());
        if(held!=null&&held.supported()&&held.crystal().isPresent())data=data.withCrystal(held.crystal().orElseThrow()).withCondition(held.condition()).withActive(held.active());
        stack.set(SaberContent.ASSEMBLY.get(),data);return stack;
    }
    private int count(String name) {
        int total = 0;
        for (int slot = 0; slot < 36; slot++) if (inventory.getItem(slot).is(SaberContent.partItem(name))) total += inventory.getItem(slot).getCount();
        return Math.min(32767, total);
    }
    @Override public void broadcastChanges() {
        if (!inventory.player.level().isClientSide()) {
            secondaryOwned.set(selectedType()==SaberType.DOUBLE?count(secondaryName()):0);
            if(inventory.player instanceof ServerPlayer actor)typeKnown.set(actor.getData(SaberContent.KNOWLEDGE).knows(selectedType())?1:0);
            for (var slot : SaberSlot.values()) owned.get(slot.ordinal()).set(count(partName(slot)));
            ritualAmethyst.set(SaberConfig.BOND_AMETHYST.get()); ritualLapis.set(SaberConfig.BOND_LAPIS.get());
            var held=inventory.getSelected().get(SaberContent.ASSEMBLY.get());condition.set(held!=null&&held.supported()?held.condition():100);var stats=SaberStats.estimate(preview().get(SaberContent.ASSEMBLY.get()),SaberCatalog.parts());reach.set(stats.map(v->(int)Math.round(v.reach()*100)).orElse(0));speed.set(stats.map(v->(int)Math.round(v.attacksPerSecond()*100)).orElse(0));cost.set(stats.map(v->(int)Math.round(v.costMultiplier()*100)).orElse(0));damage.set(stats.map(value->(int)Math.round(value.damage()*100)).orElse(0));stability.set(stats.map(value->(int)Math.round(value.stability()*100)).orElse(0));
        }
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer actor) || actor.isFakePlayer() || !actor.server.isSameThread()
                || actor.containerMenu != this || inventory.player != actor) return false;
        if (!stillValid(player)) { status.set(Status.TOO_FAR.ordinal()); return false; }
        long tick=actor.server.overworld().getGameTime();
        if(button==1||button==100||button>=110&&button<116||button==120){
            if(tick<nextSelection)return false;nextSelection=tick+2;
            if(button==100)selectDesign(SaberContent.defaultDesign(SaberType.values()[(selectedType().ordinal()+1)%SaberType.values().length]));
            else if(button==1){if(selectedType()!=SaberType.SINGLE)return false;emitter.set(1-emitter.get());chosen.get(SaberSlot.EMITTER.ordinal()).set(SaberContent.PART_NAMES.indexOf(emitter.get()==1?"emitter_claw":"emitter_basic"));}
            else {var slot=button==120?SaberSlot.EMITTER:SaberSlot.values()[button-110];if(button==120&&selectedType()!=SaberType.DOUBLE)return false;
                var choices=SaberContent.choices(slot).stream().filter(id->{var part=SaberCatalog.parts().get(id);return part!=null&&part.types().contains(selectedType().key());}).toList();if(choices.isEmpty())return false;
                var current=SaberContent.id(button==120?secondaryName():partName(slot));int index=(choices.indexOf(current)+1)%choices.size();if(button==120)secondary.set(SaberContent.partIndex(choices.get(index)));else chosen.get(slot.ordinal()).set(SaberContent.partIndex(choices.get(index)));}
            status.set(Status.READY.ordinal());broadcastChanges();return true;
        }
        if(button<0||button>8){status.set(Status.INVALID.ordinal());return false;}
        if(tick<nextCraftTick){status.set(Status.COOLDOWN.ordinal());return false;}
        if(button==8){for(int index=0;index<36;index++){var stack=inventory.getItem(index);if(!stack.is(SaberContent.BLUEPRINT.get()))continue;var plan=stack.get(SaberContent.BLUEPRINT_DATA.get());if(plan!=null&&plan.design().isPresent()&&plan.design().get().compatible(SaberCatalog.parts())&&selectDesign(plan.design().get())){nextCraftTick=tick+SaberConfig.CRAFT_INTERVAL.get();status.set(Status.PLAN_LOADED.ordinal());broadcastChanges();return true;}}status.set(Status.NO_BLUEPRINT.ordinal());return false;}
        if(button>=4) {var result=SaberWorkshopActions.perform(actor,button,design());status.set(result.ordinal());boolean success=result==Status.MODIFIED||result==Status.DISMANTLED||result==Status.MAINTAINED||result==Status.PLAN_SAVED;if(success)nextCraftTick=tick+SaberConfig.CRAFT_INTERVAL.get();broadcastChanges();return success;}
        if (button == 3) {
            var result = KyberRitual.perform(actor); status.set(Status.valueOf(result.name()).ordinal());
            boolean success = result == KyberRitual.Result.AWAKENED || result == KyberRitual.Result.SPECIALIZED;
            if (success) nextCraftTick = tick + SaberConfig.CRAFT_INTERVAL.get();
            actor.inventoryMenu.broadcastChanges(); broadcastChanges(); return success;
        }
        if (button == 2) {
            var result = CrystalExchange.install(actor);
            status.set(Status.valueOf(result.name()).ordinal());
            boolean success = result == CrystalExchange.Result.INSERTED || result == CrystalExchange.Result.REPLACED;
            if (success) {
                nextCraftTick = tick + SaberConfig.CRAFT_INTERVAL.get();
                // The workshop only exposes main inventory slots; synchronize the offhand projection too.
                actor.inventoryMenu.broadcastChanges();
            }
            broadcastChanges(); return success;
        }
        var result=SaberWorkshopActions.perform(actor,0,design());status.set(result.ordinal());if(result==Status.READY)nextCraftTick=tick+SaberConfig.CRAFT_INTERVAL.get();broadcastChanges();return result==Status.READY;
    }
    @Override public boolean stillValid(Player player) {
        return player == inventory.player && player.isAlive() && !player.isSpectator()
                && access.evaluate((level, pos) -> player.level() == level && level.getBlockState(pos).is(SaberContent.WORKSHOP.get())
                    && player.canInteractWithBlock(pos, 4.0) && level.mayInteract(player, pos), player.level().isClientSide());
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem(); var copy = stack.copy();
        if (!moveItemStackTo(stack, index < 27 ? 27 : 0, index < 27 ? 36 : 27, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return copy;
    }
}
