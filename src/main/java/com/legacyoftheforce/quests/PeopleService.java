package com.legacyoftheforce.quests;

import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.SubscribeEvent;

public final class PeopleService {
    private static final VanillaProtectedWorldService PROTECTION=new VanillaProtectedWorldService();
    private static final Map<MinecraftServer,Map<UUID,Long>> NEXT=new IdentityHashMap<>();
    static boolean valid(ServerPlayer actor,AdventurePersonEntity person){return actor.server.isSameThread()&&!actor.hasDisconnected()&&actor.isAlive()&&!actor.isFakePlayer()&&!actor.isSpectator()&&person.isAlive()&&actor.level()==person.level()&&actor.distanceToSqr(person)<=16&&loadedBetween(actor,person)&&actor.hasLineOfSight(person)&&PROTECTION.mayUseContainer(actor,actor.serverLevel(),person.blockPosition())&&PlayerInventoryHooks.ready(actor)&&actor.getData(QuestContent.RELATIONS).view().filter(v->v.revision()<Long.MAX_VALUE).isPresent();}
    private static boolean personalGridEmpty(ServerPlayer actor){
        // Opening a conversation must not return/drop a personal crafting input after a player save.
        for(int slot=0;slot<=4;slot++)if(!actor.inventoryMenu.getSlot(slot).getItem().isEmpty())return false;
        return true;
    }
    private static boolean loadedBetween(ServerPlayer actor,AdventurePersonEntity person){
        for(double x:new double[]{Math.min(actor.getX(),person.getX()),Math.max(actor.getX(),person.getX())})
            for(double z:new double[]{Math.min(actor.getZ(),person.getZ()),Math.max(actor.getZ(),person.getZ())})
                if(!actor.serverLevel().hasChunkAt(net.minecraft.core.BlockPos.containing(x,actor.getY(),z)))return false;
        return true;
    }
    static boolean request(ServerPlayer actor){var map=NEXT.computeIfAbsent(actor.server,k->new HashMap<>());long now=actor.server.overworld().getGameTime();if(now<map.getOrDefault(actor.getUUID(),0L)||!map.containsKey(actor.getUUID())&&map.size()>=1024)return false;map.put(actor.getUUID(),now+PeopleConfig.ACTION_INTERVAL.get());return true;}
    static String branch(ServerPlayer actor,AdventureRole role){
        var view=actor.getData(QuestContent.RELATIONS).view().orElseThrow();int alignment=PlayerProgressAccess.snapshot(actor).filter(p->!p.unavailable()).map(PlayerProgressSnapshot::alignmentTenths).orElse(0);
        if(role==AdventureRole.JEDI&&alignment<-PeopleConfig.CAUTION_ALIGNMENT.get()||role==AdventureRole.SITH&&alignment>PeopleConfig.CAUTION_ALIGNMENT.get())return "cautious";
        return view.reputation().getOrDefault(role.faction(),0)>=PeopleConfig.TRUSTED_REPUTATION.get()?"trusted":"welcome";
    }
    public static boolean open(ServerPlayer actor,AdventurePersonEntity person){
        if(actor.containerMenu!=actor.inventoryMenu||!personalGridEmpty(actor)||!valid(actor,person)||!request(actor))return false;
        var definition=PeopleCatalog.get(person.role());if(definition==null)return false;
        var current=actor.getData(QuestContent.RELATIONS);var next=current.observe(person.role(),branch(actor,person.role()));
        if(!commit(actor,next,null))return false;
        actor.openMenu(new SimpleMenuProvider((id,inventory,player)->new ConversationMenu(id,inventory,person,definition),Component.translatable("people.legacyoftheforce.role."+person.role().key())),buffer->ConversationMenu.write(buffer,person,definition));return true;
    }
    static boolean clue(ServerPlayer actor,AdventurePersonEntity person){return commit(actor,actor.getData(QuestContent.RELATIONS).observe(person.role(),"clue"),null);}
    static boolean help(ServerPlayer actor,AdventurePersonEntity person,PeopleCatalog.Definition definition){
        var current=actor.getData(QuestContent.RELATIONS);if(current.view().orElseThrow().helped().contains(person.role().key()))return false;
        var inventory=payment(actor,definition.help(),null,0);if(inventory==null)return false;
        return commit(actor,current.help(person.role(),PeopleConfig.HELP_REPUTATION.get()),inventory);
    }
    static boolean trade(ServerPlayer actor,AdventurePersonEntity person,PeopleCatalog.Definition definition){
        var current=actor.getData(QuestContent.RELATIONS);var view=current.view().orElseThrow();long now=actor.server.overworld().getGameTime();
        if(definition.result()==null||now<view.nextTrade()||view.reputation().getOrDefault(person.role().faction(),0)<definition.minimumReputation())return false;
        var inventory=payment(actor,definition.price(),definition.result(),definition.count());if(inventory==null)return false;
        return commit(actor,current.traded(Math.addExact(now,PeopleConfig.TRADE_INTERVAL.get())),inventory);
    }
    private static ListTag payment(ServerPlayer actor,PeopleCatalog.Cost price,Item output,int count){
        var before=actor.getInventory().save(new ListTag());var preview=new Inventory(actor);preview.load(before.copy());
        if(!preview.save(new ListTag()).equals(before))return null;
        int remaining=price.count();var ordinary=new ItemStack(price.item());
        for(int i=0;i<36&&remaining>0;i++){var stack=preview.getItem(i);if(!ItemStack.isSameItemSameComponents(stack,ordinary))continue;int take=Math.min(remaining,stack.getCount());stack.shrink(take);remaining-=take;}
        if(remaining!=0)return null;
        if(output!=null){var reward=new ItemStack(output,count);preview.add(reward);if(!reward.isEmpty())return null;}
        return preview.save(new ListTag());
    }
    private static boolean commit(ServerPlayer actor,RelationsState desired,ListTag inventory){
        if(desired.equals(actor.getData(QuestContent.RELATIONS))&&inventory==null)return true;
        if(!PlayerInventoryHooks.ready(actor))return false;
        var expected=inventory==null?actor.getInventory().save(new ListTag()):inventory.copy();
        if(inventory!=null){actor.getInventory().load(inventory.copy());actor.getInventory().setChanged();}
        actor.setData(QuestContent.RELATIONS,desired);
        try{
            var saved=new VerifiedPlayerSaveBarrier().persist(actor);
            if(!expected.equals(saved.get("Inventory"))||!expected.equals(actor.getInventory().save(new ListTag()))||!saved.getCompound("neoforge:attachments").getCompound("legacyoftheforce:relations").equals(desired.tag())||!actor.getData(QuestContent.RELATIONS).equals(desired))throw new java.io.IOException("Native conversation aggregate differs");
        }catch(java.io.IOException|RuntimeException failure){
            // One native file owns both payment and receipt. Never restore an old inventory after uncertain IO.
            actor.setData(QuestContent.RELATIONS,desired);
            com.legacyoftheforce.LegacyOfTheForce.LOGGER.error("Conversation payment/relations save uncertain; live aggregate retained",failure);
            actor.connection.disconnect(Component.translatable("people.legacyoftheforce.save_pending"));return false;
        }
        actor.inventoryMenu.broadcastChanges();return true;
    }
    @SubscribeEvent public void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer actor){var map=NEXT.get(actor.server);if(map!=null)map.remove(actor.getUUID());}}
    @SubscribeEvent public void stop(net.neoforged.neoforge.event.server.ServerStoppedEvent e){NEXT.remove(e.getServer());}
}
