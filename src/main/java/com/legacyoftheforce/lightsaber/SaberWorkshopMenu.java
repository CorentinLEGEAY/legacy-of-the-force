package com.legacyoftheforce.lightsaber;

import java.util.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

/** Virtual recipe selection: components stay in the player's inventory until the atomic server action. */
public final class SaberWorkshopMenu extends AbstractContainerMenu {
    public enum Status { READY, MISSING, FULL, INCOMPATIBLE, TOO_FAR, COOLDOWN, INVALID, NO_SABER, NO_CRYSTAL, DUPLICATE, INSERTED, REPLACED, AWAKENED, SPECIALIZED, RITUAL_MATERIALS, BOND_NOT_READY, MODIFIED, DISMANTLED, MAINTAINED, NO_BLUEPRINT, PLAN_SAVED, PLAN_LOADED, UNCHANGED, TYPE_LOCKED, CORRUPTED, PURIFIED, RITUAL_ALIGNMENT, RITUAL_LEVEL, FINISHED, FINISH_MATERIALS, FINISH_UNAVAILABLE }
    private final DataSlot styleZone=DataSlot.standalone(),engraving=DataSlot.standalone();
    private final DataSlot[] finishes=new DataSlot[5],colorHigh=new DataSlot[5],colorLow=new DataSlot[5];
    private final PriceProjection refinishPrices=new PriceProjection(),craftPrices=new PriceProjection();
    private final class PriceProjection {
        private final DataSlot available=DataSlot.standalone();private final DataSlot[] high=new DataSlot[6],low=new DataSlot[6],count=new DataSlot[6];
        private PriceProjection(){addDataSlot(available);for(int i=0;i<6;i++){high[i]=DataSlot.standalone();low[i]=DataSlot.standalone();count[i]=DataSlot.standalone();addDataSlot(high[i]);addDataSlot(low[i]);addDataSlot(count[i]);}}
        private void set(java.util.Optional<java.util.Map<net.minecraft.world.item.Item,Integer>> quote){
            available.set(quote.isPresent()?1:0);for(int i=0;i<6;i++){high[i].set(0);low[i].set(0);count[i].set(0);}if(quote.isEmpty())return;
            var entries=quote.get().entrySet().stream().sorted(java.util.Comparator.comparing(e->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getKey()).toString())).toList();
            for(int i=0;i<Math.min(6,entries.size());i++){int id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(entries.get(i).getKey());high[i].set((short)(id>>>16));low[i].set((short)id);count[i].set(entries.get(i).getValue());}
        }
        private List<ItemStack> stacks(){var values=new ArrayList<ItemStack>();for(int i=0;i<6;i++){int amount=Math.clamp(count[i].get(),0,384);if(amount==0)continue;var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.byId((high[i].get()&65535)<<16|low[i].get()&65535);if(item!=net.minecraft.world.item.Items.AIR)values.add(new ItemStack(item,amount));}return List.copyOf(values);}
    }
    private final Inventory inventory;
    private final ContainerLevelAccess access;
    private final com.legacyoftheforce.core.api.ProtectedWorldService protection=new com.legacyoftheforce.core.security.VanillaProtectedWorldService();
    private final DataSlot emitter = DataSlot.standalone();
    private final DataSlot status = DataSlot.standalone();
    private final DataSlot ritualAmethyst = DataSlot.standalone(), ritualLapis = DataSlot.standalone();
    private final DataSlot condition = DataSlot.standalone(), damage = DataSlot.standalone(), stability = DataSlot.standalone(),reach=DataSlot.standalone(),speed=DataSlot.standalone(),cost=DataSlot.standalone();
    private final List<DataSlot> owned = new ArrayList<>();
    private long nextCraftTick,nextSelection;
    private final DataSlot[] ritualParameters=new DataSlot[11];
    private final DataSlot type=DataSlot.standalone(),secondary=DataSlot.standalone();
    private final DataSlot secondaryOwned=DataSlot.standalone(),typeKnown=DataSlot.standalone();
    private final List<DataSlot> chosen=new ArrayList<>();
    public SaberWorkshopMenu(int id, Inventory inventory) { this(id, inventory, ContainerLevelAccess.NULL); }
    public SaberWorkshopMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(SaberContent.MENU.get(), id); this.inventory = inventory; this.access = access;
        addDataSlot(emitter); addDataSlot(status);addDataSlot(type);addDataSlot(secondary);addDataSlot(secondaryOwned);addDataSlot(typeKnown);
        for(var part:SaberSlot.values()){var choice=DataSlot.standalone();choice.set(SaberContent.PART_NAMES.indexOf(part.key()+"_basic"));chosen.add(choice);addDataSlot(choice);}
        addDataSlot(styleZone);addDataSlot(engraving);
        for(int i=0;i<5;i++){finishes[i]=DataSlot.standalone();colorHigh[i]=DataSlot.standalone();colorLow[i]=DataSlot.standalone();colorHigh[i].set(255);colorLow[i].set((short)0xffff);addDataSlot(finishes[i]);addDataSlot(colorHigh[i]);addDataSlot(colorLow[i]);}
        secondary.set(SaberContent.PART_NAMES.indexOf("emitter_twin"));
        var held=inventory.getSelected().get(SaberContent.ASSEMBLY.get());if(held!=null&&held.supported())selectDesign(held);
        for (var slot : SaberSlot.values()) { var count = DataSlot.standalone(); owned.add(count); addDataSlot(count); }
        ritualAmethyst.set(4); ritualLapis.set(2); addDataSlot(ritualAmethyst); addDataSlot(ritualLapis);
        for(int i=0;i<ritualParameters.length;i++){ritualParameters[i]=DataSlot.standalone();addDataSlot(ritualParameters[i]);}
        addDataSlot(condition);addDataSlot(damage);addDataSlot(stability);addDataSlot(reach);addDataSlot(speed);addDataSlot(cost);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 47 + col * 18, 142 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 47 + col * 18, 200));
    }
    public SaberSlot styleZone(){return SaberAppearance.ZONES.get(Math.clamp(styleZone.get(),0,4));}
    public SaberAppearance appearance(){var result=SaberAppearance.original();for(int i=0;i<5;i++)result=result.withZone(SaberAppearance.ZONES.get(i),SaberAppearance.Finish.values()[Math.clamp(finishes[i].get(),0,5)],(colorHigh[i].get()&255)<<16|colorLow[i].get()&65535);return result.withEngraving(Math.clamp(engraving.get(),0,3));}
    private void selectAppearance(SaberAppearance value){for(int i=0;i<5;i++){var zone=SaberAppearance.ZONES.get(i);finishes[i].set(value.finish(zone).ordinal());int color=value.paint(zone);colorHigh[i].set(color>>>16&255);colorLow[i].set((short)color);}engraving.set(value.engraving());}
    public List<ItemStack> refinishPrices(){return refinishPrices.stacks();}
    public List<ItemStack> craftFinishPrices(){return craftPrices.stacks();}
    public boolean craftFinishPricesAvailable(){return craftPrices.available.get()!=0;}
    public boolean finishPricesAvailable(){return refinishPrices.available.get()!=0;}
    public int emitterIndex() { return emitter.get(); }
    public int ritualParameter(int i){return ritualParameters[i].get();}public int specializationThreshold(){return (ritualParameter(9)&65535)<<16|ritualParameter(10)&65535;}
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
    private SaberData design(){var parts=new EnumMap<SaberSlot,net.minecraft.resources.ResourceLocation>(SaberSlot.class);for(var slot:SaberSlot.values())parts.put(slot,SaberContent.id(partName(slot)));return SaberData.create(new UUID(0,0),selectedType(),parts,selectedType()==SaberType.DOUBLE?SaberContent.id(secondaryName()):null).withAppearance(appearance());}
    private boolean selectDesign(SaberData design){if(!design.supported()||design.bill().keySet().stream().anyMatch(id->SaberContent.partIndex(id)<0))return false;selectAppearance(design.appearance().orElseThrow());type.set(design.type().ordinal());for(var slot:SaberSlot.values())chosen.get(slot.ordinal()).set(SaberContent.partIndex(design.parts().orElseThrow().get(slot)));secondary.set(design.secondaryEmitter().map(SaberContent::partIndex).orElse(SaberContent.PART_NAMES.indexOf("emitter_twin")));emitter.set(partName(SaberSlot.EMITTER).equals("emitter_claw")?1:0);return true;}
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
            int[] values={SaberConfig.CORRUPT_REAGENT.get(),SaberConfig.PURIFY_REAGENT.get(),SaberConfig.CORRUPT_BLAZE.get(),SaberConfig.PURIFY_AMETHYST.get(),SaberConfig.FOCUS_REAGENT.get(),SaberConfig.CORRUPT_LEVEL.get(),SaberConfig.PURIFY_LEVEL.get(),SaberConfig.CORRUPT_ALIGNMENT.get(),SaberConfig.PURIFY_ALIGNMENT.get(),(short)(SaberConfig.BOND_3.get()>>>16),(short)(int)SaberConfig.BOND_3.get()};for(int i=0;i<values.length;i++)ritualParameters[i].set(values[i]);
            var selectedAppearance=appearance();craftPrices.set(SaberFinishCatalog.quote(SaberAppearance.original(),selectedAppearance));
            var heldAppearance=inventory.getSelected().get(SaberContent.ASSEMBLY.get());refinishPrices.set(heldAppearance!=null&&heldAppearance.supported()?SaberFinishCatalog.quote(heldAppearance.appearance().orElseThrow(),selectedAppearance):Optional.empty());
            var held=inventory.getSelected().get(SaberContent.ASSEMBLY.get());condition.set(held!=null&&held.supported()?held.condition():100);var stats=SaberStats.estimate(preview().get(SaberContent.ASSEMBLY.get()),SaberCatalog.parts());reach.set(stats.map(v->(int)Math.round(v.reach()*100)).orElse(0));speed.set(stats.map(v->(int)Math.round(v.attacksPerSecond()*100)).orElse(0));cost.set(stats.map(v->(int)Math.round(v.costMultiplier()*100)).orElse(0));damage.set(stats.map(value->(int)Math.round(value.damage()*100)).orElse(0));stability.set(stats.map(value->(int)Math.round(value.stability()*100)).orElse(0));
        }
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer actor) || actor.isFakePlayer() || !actor.server.isSameThread()
                || actor.containerMenu != this || inventory.player != actor) return false;
        if (!stillValid(player)) { status.set(Status.TOO_FAR.ordinal()); return false; }
        long tick=actor.server.overworld().getGameTime();
        if(button>=150&&button<=153){
            if(tick<nextSelection)return false;nextSelection=tick+2;var style=appearance();var zone=styleZone();
            if(button==150)styleZone.set((styleZone.get()+1)%5);
            else if(button==151){var finish=SaberAppearance.Finish.values()[(style.finish(zone).ordinal()+1)%6];int color=style.paint(zone);boolean validPaint=false;for(var dye:net.minecraft.world.item.DyeColor.values())if((dye.getTextureDiffuseColor()&0xffffff)==color)validPaint=true;if(finish==SaberAppearance.Finish.ENAMEL&&!validPaint)color=net.minecraft.world.item.DyeColor.WHITE.getTextureDiffuseColor()&0xffffff;selectAppearance(style.withZone(zone,finish,color));}
            else if(button==152){var palette=net.minecraft.world.item.DyeColor.values();int selected=-1;for(int i=0;i<palette.length;i++)if((palette[i].getTextureDiffuseColor()&0xffffff)==style.paint(zone))selected=i;var color=palette[(selected+1)%palette.length];selectAppearance(style.withZone(zone,SaberAppearance.Finish.ENAMEL,color.getTextureDiffuseColor()&0xffffff));}
            else selectAppearance(style.withEngraving((style.engraving()+1)%4));
            broadcastChanges();return true;
        }
        if(button==1||button==100||button>=110&&button<116||button==120){
            if(tick<nextSelection)return false;nextSelection=tick+2;
            if(button==100)selectDesign(SaberContent.defaultDesign(SaberType.values()[(selectedType().ordinal()+1)%SaberType.values().length]).withAppearance(appearance()));
            else if(button==1){if(selectedType()!=SaberType.SINGLE)return false;emitter.set(1-emitter.get());chosen.get(SaberSlot.EMITTER.ordinal()).set(SaberContent.PART_NAMES.indexOf(emitter.get()==1?"emitter_claw":"emitter_basic"));}
            else {var slot=button==120?SaberSlot.EMITTER:SaberSlot.values()[button-110];if(button==120&&selectedType()!=SaberType.DOUBLE)return false;
                var choices=SaberContent.choices(slot).stream().filter(id->{var part=SaberCatalog.parts().get(id);return part!=null&&part.types().contains(selectedType().key());}).toList();if(choices.isEmpty())return false;
                var current=SaberContent.id(button==120?secondaryName():partName(slot));int index=(choices.indexOf(current)+1)%choices.size();if(button==120)secondary.set(SaberContent.partIndex(choices.get(index)));else chosen.get(slot.ordinal()).set(SaberContent.partIndex(choices.get(index)));}
            status.set(Status.READY.ordinal());broadcastChanges();return true;
        }
        if(button<0||button>9&&!(button>=10&&button<=12)){status.set(Status.INVALID.ordinal());return false;}
        if(tick<nextCraftTick){status.set(Status.COOLDOWN.ordinal());return false;}
        nextCraftTick=tick+SaberConfig.CRAFT_INTERVAL.get();
        if(button>=10&&button<=12){nextCraftTick=tick+SaberConfig.CRAFT_INTERVAL.get();var ceremony=button==10?KyberRitual.Ceremony.CORRUPT:button==11?KyberRitual.Ceremony.PURIFY:KyberRitual.Ceremony.FOCUS;var result=KyberRitual.perform(actor,ceremony);status.set(Status.valueOf(result.name()).ordinal());boolean success=result==KyberRitual.Result.CORRUPTED||result==KyberRitual.Result.PURIFIED||result==KyberRitual.Result.SPECIALIZED;if(success)nextCraftTick=tick+SaberConfig.CRAFT_INTERVAL.get();broadcastChanges();return success;}
        if(button==8){for(int index=0;index<36;index++){var stack=inventory.getItem(index);if(!stack.is(SaberContent.BLUEPRINT.get()))continue;var plan=stack.get(SaberContent.BLUEPRINT_DATA.get());if(plan!=null&&plan.design().isPresent()&&plan.design().get().compatible(SaberCatalog.parts())&&selectDesign(plan.design().get())){nextCraftTick=tick+SaberConfig.CRAFT_INTERVAL.get();status.set(Status.PLAN_LOADED.ordinal());broadcastChanges();return true;}}status.set(Status.NO_BLUEPRINT.ordinal());return false;}
        if(button>=4) {var result=SaberWorkshopActions.perform(actor,button,design());status.set(result.ordinal());boolean success=result==Status.MODIFIED||result==Status.DISMANTLED||result==Status.MAINTAINED||result==Status.PLAN_SAVED||result==Status.FINISHED;if(success)nextCraftTick=tick+SaberConfig.CRAFT_INTERVAL.get();broadcastChanges();return success;}
        if (button == 3) {
            nextCraftTick=tick+SaberConfig.CRAFT_INTERVAL.get();
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
                    && player.canInteractWithBlock(pos, 4.0) && level.mayInteract(player, pos)
                    &&(!(player instanceof ServerPlayer actor)||protection.mayUseContainer(actor,actor.serverLevel(),pos)), player.level().isClientSide());
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
