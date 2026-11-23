package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier;
import com.legacyoftheforce.content.PlanetaryMaterials;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;

/** Every ceremony prepares copies and commits materials/crystal/history in one native player aggregate. */
final class KyberRitual {
    enum Ceremony { BOND, FOCUS, CORRUPT, PURIFY }
    enum Result { AWAKENED, SPECIALIZED, RITUAL_MATERIALS, BOND_NOT_READY, INVALID, CORRUPTED, PURIFIED, RITUAL_ALIGNMENT, RITUAL_LEVEL }
    static Result perform(ServerPlayer actor){return perform(actor,Ceremony.BOND);}
    static Result perform(ServerPlayer actor,Ceremony ceremony){
        if(!PlayerInventoryHooks.ready(actor)||!(actor.containerMenu instanceof SaberWorkshopMenu menu)||!menu.stillValid(actor))return Result.INVALID;
        var source=actor.getMainHandItem();var saber=source.get(SaberContent.ASSEMBLY.get());
        boolean embedded=source.is(SaberContent.SABER.get());
        KyberData crystal;
        if(embedded){if(source.getCount()!=1||saber==null||!saber.supported()||!saber.compatible(SaberCatalog.parts())||saber.crystal().isEmpty())return Result.INVALID;crystal=saber.crystal().orElseThrow();}
        else {if(!source.is(SaberContent.KYBER.get())||source.getCount()!=1||!source.has(SaberContent.KYBER_DATA.get())||ceremony==Ceremony.BOND||ceremony==Ceremony.FOCUS)return Result.INVALID;crystal=source.get(SaberContent.KYBER_DATA.get());}
        var state=crystal.state();if(state.isEmpty())return Result.INVALID;
        var inspected=new ArrayList<ItemStack>();for(int i=0;i<actor.getInventory().getContainerSize();i++)inspected.add(actor.getInventory().getItem(i));
        for(int i=1;i<=4;i++)inspected.add(actor.inventoryMenu.getSlot(i).getItem());
        var crystalIds=new HashSet<UUID>();var saberIds=new HashSet<UUID>();
        for(var stack:inspected){
            if(stack.getCount()>stack.getMaxStackSize())return Result.INVALID;
            if(stack.is(SaberContent.KYBER.get())){var data=stack.get(SaberContent.KYBER_DATA.get());if(data!=null&&data.state().isEmpty())return Result.INVALID;}
            if(stack.is(SaberContent.SABER.get())){var data=stack.get(SaberContent.ASSEMBLY.get());if(data==null||!data.supported()||!saberIds.add(data.tag().getUUID("saberId")))return Result.INVALID;}
            var id=CrystalIdentityAccess.identified(stack);if(id.isPresent()&&!crystalIds.add(id.get()))return Result.INVALID;
        }
        var materials=new LinkedHashMap<Item,Integer>();KyberData updated;Result result;long now=actor.server.overworld().getGameTime();
        try{
            switch(ceremony){
                case CORRUPT, PURIFY->{
                    boolean purify=ceremony==Ceremony.PURIFY;var progress=PlayerProgressAccess.snapshot(actor);if(progress.isEmpty()||progress.get().unavailable())return Result.INVALID;
                    if(progress.get().level()<(purify?SaberConfig.PURIFY_LEVEL.get():SaberConfig.CORRUPT_LEVEL.get()))return Result.RITUAL_LEVEL;
                    if(purify?progress.get().alignmentTenths()<SaberConfig.PURIFY_ALIGNMENT.get():progress.get().alignmentTenths()>SaberConfig.CORRUPT_ALIGNMENT.get())return Result.RITUAL_ALIGNMENT;
                    if(purify?!crystal.tag().getString("state").equals("corrupted"):crystal.tag().getString("state").equals("corrupted"))return Result.BOND_NOT_READY;
                    updated=crystal.transform(purify,actor.getUUID(),now);
                    materials.put(PlanetaryMaterials.item(purify?"jedha_relic_dust":"mustafar_slag"),purify?SaberConfig.PURIFY_REAGENT.get():SaberConfig.CORRUPT_REAGENT.get());
                    materials.put(purify?Items.AMETHYST_SHARD:Items.BLAZE_POWDER,purify?SaberConfig.PURIFY_AMETHYST.get():SaberConfig.CORRUPT_BLAZE.get());
                    result=purify?Result.PURIFIED:Result.CORRUPTED;
                }
                case FOCUS->{updated=KyberBond.specialize(crystal,SaberConfig.bondRules(),"focus");materials.put(PlanetaryMaterials.item("dagobah_essence"),SaberConfig.FOCUS_REAGENT.get());result=Result.SPECIALIZED;}
                default->{updated=state.get().bondLevel()==0?KyberBond.awaken(crystal,now):KyberBond.specialize(crystal,SaberConfig.bondRules());
                    if(state.get().bondLevel()==0){materials.put(Items.AMETHYST_SHARD,SaberConfig.BOND_AMETHYST.get());materials.put(Items.LAPIS_LAZULI,SaberConfig.BOND_LAPIS.get());}result=state.get().bondLevel()==0?Result.AWAKENED:Result.SPECIALIZED;}
            }
        }catch(IllegalArgumentException|ArithmeticException invalid){return Result.INVALID;}
        if(updated.equals(crystal))return Result.BOND_NOT_READY;
        var planned=new ItemStack[36];for(int i=0;i<36;i++)planned[i]=actor.getInventory().getItem(i).copy();
        for(var entry:materials.entrySet()){int needed=entry.getValue();for(int i=0;i<36&&needed>0;i++)if(i!=actor.getInventory().selected&&planned[i].is(entry.getKey())){int take=Math.min(needed,planned[i].getCount());planned[i].shrink(take);needed-=take;}if(needed>0)return Result.RITUAL_MATERIALS;}
        int held=actor.getInventory().selected;
        if(embedded)planned[held].set(SaberContent.ASSEMBLY.get(),saber.updateCrystal(updated).withActive(false));else planned[held].set(SaberContent.KYBER_DATA.get(),updated);
        for(int i=0;i<36;i++)actor.getInventory().setItem(i,planned[i]);actor.getInventory().setChanged();
        try{new VerifiedPlayerSaveBarrier().persist(actor);}catch(java.io.IOException|RuntimeException uncertain){com.legacyoftheforce.LegacyOfTheForce.LOGGER.error("Kyber ceremony save uncertain; reload native player aggregate",uncertain);actor.connection.disconnect(net.minecraft.network.chat.Component.translatable("saber.legacyoftheforce.menu_recovery"));return Result.INVALID;}
        actor.inventoryMenu.broadcastChanges();return result;
    }
    private KyberRitual(){}
}
