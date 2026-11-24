package com.legacyoftheforce.quests;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

/** No slots or cursor operations. Confirmation belongs to this server menu, never a client flag. */
public final class ConversationMenu extends AbstractContainerMenu {
    private final Player owner;
    private final AdventurePersonEntity person;
    private final AdventureRole role;
    private final int helpGain;
    private final PeopleCatalog.Definition definition;
    private final ContainerData data=new SimpleContainerData(5);
    public ConversationMenu(int id,Inventory inventory,RegistryFriendlyByteBuf buffer){
        super(QuestContent.CONVERSATION.get(),id);owner=inventory.player;person=null;
        int index=buffer.readVarInt();if(index<0||index>=AdventureRole.values().length)throw new IllegalArgumentException("Unknown role");role=AdventureRole.values()[index];
        helpGain=buffer.readVarInt();if(helpGain<1||helpGain>20)throw new IllegalArgumentException("Invalid reputation projection");
        var help=readCost(buffer);PeopleCatalog.Cost price=null;Item result=null;int count=0,minRep=0;
        if(buffer.readBoolean()){price=readCost(buffer);result=BuiltInRegistries.ITEM.get(buffer.readResourceLocation());count=buffer.readVarInt();minRep=buffer.readVarInt();if(result==Items.AIR||count<1||count>16||minRep<0||minRep>20)throw new IllegalArgumentException("Invalid barter projection");}
        definition=new PeopleCatalog.Definition(help,price,result,count,minRep);addDataSlots(data);
    }
    public ConversationMenu(int id,Inventory inventory,AdventurePersonEntity person,PeopleCatalog.Definition definition){super(QuestContent.CONVERSATION.get(),id);owner=inventory.player;this.person=person;role=person.role();helpGain=PeopleConfig.HELP_REPUTATION.get();this.definition=definition;refresh();addDataSlots(data);}
    public static void write(RegistryFriendlyByteBuf b,AdventurePersonEntity person,PeopleCatalog.Definition definition){b.writeVarInt(person.role().ordinal());b.writeVarInt(PeopleConfig.HELP_REPUTATION.get());writeCost(b,definition.help());b.writeBoolean(definition.result()!=null);if(definition.result()!=null){writeCost(b,definition.price());b.writeResourceLocation(BuiltInRegistries.ITEM.getKey(definition.result()));b.writeVarInt(definition.count());b.writeVarInt(definition.minimumReputation());}}
    private static void writeCost(RegistryFriendlyByteBuf b,PeopleCatalog.Cost cost){b.writeResourceLocation(BuiltInRegistries.ITEM.getKey(cost.item()));b.writeVarInt(cost.count());}
    private static PeopleCatalog.Cost readCost(RegistryFriendlyByteBuf b){var item=BuiltInRegistries.ITEM.get(b.readResourceLocation());int count=b.readVarInt();if(item==Items.AIR||count<1||count>64)throw new IllegalArgumentException("Invalid payment projection");return new PeopleCatalog.Cost(item,count);}
    private void refresh(){if(owner instanceof ServerPlayer actor){var v=actor.getData(QuestContent.RELATIONS).view();if(v.isEmpty())return;data.set(0,v.get().reputation().getOrDefault(role.faction(),0));data.set(1,com.legacyoftheforce.core.api.PlayerProgressAccess.snapshot(actor).filter(p->!p.unavailable()).map(p->p.alignmentTenths()).orElse(0));data.set(3,v.get().helped().contains(role.key())?1:0);String branch=PeopleService.branch(actor,role);data.set(4,branch.equals("cautious")?1:branch.equals("trusted")?2:0);}}
    public int helpGain(){return helpGain;}
    public AdventureRole role(){return role;}public PeopleCatalog.Definition definition(){return definition;}
    public int reputation(){return data.get(0);}public int alignment(){return data.get(1);}public int page(){return data.get(2);}public boolean helped(){return data.get(3)==1;}
    public String branch(){return data.get(4)==1?"cautious":data.get(4)==2?"trusted":"welcome";}
    @Override public boolean stillValid(Player player){return player==owner&&(person==null?player.level().isClientSide():player instanceof ServerPlayer actor&&PeopleService.valid(actor,person)&&definition.equals(PeopleCatalog.get(role))&&helpGain==PeopleConfig.HELP_REPUTATION.get());}
    @Override public boolean clickMenuButton(Player player,int button){
        if(!(player instanceof ServerPlayer actor)||actor.containerMenu!=this||!stillValid(actor)||button<0||button>5||!PeopleService.request(actor))return false;
        boolean result=true;
        if(button==0){result=PeopleService.clue(actor,person);if(result)data.set(2,1);}
        else if(button==1){if(helped())return false;data.set(2,2);}
        else if(button==2){if(definition.result()==null)return false;data.set(2,3);}
        else if(button==3){if(page()!=2||helped())return false;result=PeopleService.help(actor,person,definition);data.set(2,0);}
        else if(button==4){if(page()!=3)return false;result=PeopleService.trade(actor,person,definition);data.set(2,0);}
        else data.set(2,0);
        if(!result&&!actor.hasDisconnected())actor.sendSystemMessage(Component.translatable("people.legacyoftheforce.action_refused"));refresh();broadcastChanges();return result;
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player){}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
}
