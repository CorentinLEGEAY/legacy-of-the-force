package com.legacyoftheforce.speeder;

import com.legacyoftheforce.core.api.PlayerInventoryHooks;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Read-only slots project contents; clicks request a journaled transfer and never create a cursor stack. */
public final class SpeederCargoMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final UUID vehicle;
    private final SimpleContainer cargo=new SimpleContainer(SpeederCargoData.MAX_SLOTS);
    private final DataSlot capacity=DataSlot.standalone(),status=DataSlot.standalone(),massHigh=DataSlot.standalone(),massLow=DataSlot.standalone();
    private long nextRefresh,nextAction;
    public SpeederCargoMenu(int id,Inventory inventory){this(id,inventory,new UUID(0,0));}
    private SpeederCargoMenu(int id,Inventory inventory,UUID vehicle){
        super(SpeederContent.CARGO_MENU.get(),id);this.inventory=inventory;this.vehicle=vehicle;
        addDataSlot(capacity);addDataSlot(status);addDataSlot(massHigh);addDataSlot(massLow);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(readonly(cargo,col+row*9,8+col*18,30+row*18));
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(readonly(inventory,col+row*9+9,8+col*18,112+row*18));
        for(int col=0;col<9;col++)addSlot(readonly(inventory,col,8+col*18,170));
    }
    private Slot readonly(net.minecraft.world.Container container,int index,int x,int y){return new Slot(container,index,x,y){
        @Override public boolean mayPlace(ItemStack stack){return false;}
        @Override public boolean mayPickup(Player player){return false;}
    };}
    static boolean open(ServerPlayer actor,SpeederEntity vehicle){
        if(!vehicle.cargoReady(actor)||!PlayerInventoryHooks.ready(actor))return false;
        actor.openMenu(new SimpleMenuProvider((id,inventory,player)->new SpeederCargoMenu(id,inventory,vehicle.getUUID()),Component.translatable("speeder.legacyoftheforce.cargo_title")));return true;
    }
    public UUID vehicleId(){return vehicle;}
    public int capacity(){return capacity.get();}
    public float cargoMass(){return Float.intBitsToFloat((massHigh.get()&65535)<<16|massLow.get()&65535);}
    public SpeederRechargeService.Result status(){return SpeederRechargeService.Result.values()[Math.clamp(status.get(),0,SpeederRechargeService.Result.values().length-1)];}
    private SpeederEntity target(ServerPlayer actor){var target=actor.serverLevel().getEntity(vehicle);return target instanceof SpeederEntity speeder?speeder:null;}
    @Override public void broadcastChanges(){
        if(inventory.player instanceof ServerPlayer actor&&actor.server.overworld().getGameTime()>=nextRefresh){
            var vehicle=target(actor);
            if(vehicle!=null&&vehicle.cargoReady(actor))try{
                var state=vehicle.savedState().snapshot().orElseThrow();var contents=SpeederCargoInventory.decode(state.cargo(),actor.registryAccess());
                for(int i=0;i<cargo.getContainerSize();i++)cargo.setItem(i,contents.getItem(i).copy());
                capacity.set(SpeederCargoInventory.capacity(state,true));int mass=Float.floatToIntBits((float)SpeederCargoInventory.mass(state.cargo()));massHigh.set((short)(mass>>>16));massLow.set((short)mass);
            }catch(IllegalArgumentException unsupported){status.set(SpeederRechargeService.Result.UNSUPPORTED_STORAGE.ordinal());capacity.set(0);}
            nextRefresh=actor.server.overworld().getGameTime()+5;
        }
        super.broadcastChanges();
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(!(player instanceof ServerPlayer actor)||actor.containerMenu!=this||!stillValid(actor)||slot<0||slot>=slots.size()
                ||button<0||button>1||type!=ClickType.PICKUP&&type!=ClickType.QUICK_MOVE||!PlayerInventoryHooks.ready(actor))return;
        long now=actor.server.overworld().getGameTime();if(now<nextAction)return;nextAction=now+SpeederConfig.CARGO_INTERVAL.get();
        boolean deposit=slot>=SpeederCargoData.MAX_SLOTS;int source=deposit?slots.get(slot).getContainerSlot():slot;
        var result=SpeederAssemblyLifecycle.cargo(actor,vehicle,deposit,source,button==1);status.set(result.ordinal());nextRefresh=0;broadcastChanges();
        actor.inventoryMenu.broadcastChanges();
    }
    @Override public boolean stillValid(Player player){
        if(player!=inventory.player||!player.isAlive()||player.isSpectator())return false;
        return !(player instanceof ServerPlayer actor)||target(actor)!=null&&target(actor).cargoReady(actor);
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot){return false;}
}
