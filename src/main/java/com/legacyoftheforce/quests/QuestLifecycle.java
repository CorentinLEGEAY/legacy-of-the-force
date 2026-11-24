package com.legacyoftheforce.quests;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class QuestLifecycle {
    private static final Map<MinecraftServer, Map<UUID, Long>> NEXT_REQUEST = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Set<UUID>> PENDING = new IdentityHashMap<>();
    private static final VanillaProtectedWorldService PROTECTION = new VanillaProtectedWorldService();
    private static boolean valid(ServerPlayer actor) {
        return actor.server.isSameThread() && !actor.hasDisconnected() && actor.isAlive() && !actor.isFakePlayer() && !actor.isSpectator();
    }
    private static boolean request(ServerPlayer actor) {
        if (!valid(actor)) return false;
        var map = NEXT_REQUEST.computeIfAbsent(actor.server, ignored -> new HashMap<>());
        long now = actor.server.overworld().getGameTime();
        if (now < map.getOrDefault(actor.getUUID(), 0L) || !map.containsKey(actor.getUUID()) && map.size() >= 1024) return false;
        map.put(actor.getUUID(), now + 10); return true;
    }
    /** Native pickup callers enqueue only; persistence occurs after the item entity's pickup finishes. */
    private static void enqueue(ServerPlayer actor) {
        var pending = PENDING.computeIfAbsent(actor.server, ignored -> new LinkedHashSet<>());
        if (pending.size() < 1024) pending.add(actor.getUUID());
    }
    @SubscribeEvent public void pickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer actor && CrystalIdentityAccess.identified(event.getOriginalStack()).isPresent()) enqueue(actor);
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer actor) enqueue(actor); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer actor) enqueue(actor); }
    @SubscribeEvent public void teaching(HolocronDiscovered event){var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();if(server!=null&&server.isSameThread()){var actor=server.getPlayerList().getPlayer(event.playerId);if(actor!=null)enqueue(actor);}}
    @SubscribeEvent public void victory(BossDefeated event){var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();if(server!=null&&server.isSameThread())for(var id:event.participants){var actor=server.getPlayerList().getPlayer(id);if(actor!=null)enqueue(actor);}}
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event){if(event.getEntity() instanceof ServerPlayer actor)enqueue(actor);}
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        var pending=PENDING.get(event.getServer());if(pending==null)return;var deferred=new ArrayList<UUID>();
        for(int processed=0;processed<16&&!pending.isEmpty();processed++) {var iterator=pending.iterator();var id=iterator.next();iterator.remove();var actor=event.getServer().getPlayerList().getPlayer(id);
            if(actor!=null&&valid(actor)){if(!PlayerInventoryHooks.ready(actor))deferred.add(id);else observe(actor,null);}
        }
        pending.addAll(deferred);if(pending.isEmpty())PENDING.remove(event.getServer());
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer actor)) return;
        var map = NEXT_REQUEST.get(actor.server); if (map != null) map.remove(actor.getUUID());
        var pending = PENDING.get(actor.server); if (pending != null) pending.remove(actor.getUUID());
    }
    @SubscribeEvent public void stop(ServerStoppedEvent event) { NEXT_REQUEST.remove(event.getServer()); PENDING.remove(event.getServer()); }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lotf").then(Commands.literal("journal").executes(context -> open(context.getSource().getPlayerOrException()) ? 1 : 0)));
    }
    public static boolean discover(ServerPlayer actor, BlockPos pos) {
        if (!request(actor) || !actor.serverLevel().hasChunkAt(pos) || !actor.canInteractWithBlock(pos, 4)
                || !actor.serverLevel().getBlockState(pos).is(QuestContent.HOLOCRON.get())
                || !PROTECTION.mayUseContainer(actor, actor.serverLevel(), pos)) return false;
        if (!QuestCatalog.definitions().containsKey(QuestContent.ECHO)) {
            actor.displayClientMessage(Component.translatable("quest.legacyoftheforce.archive_unavailable"), false); return false;
        }
        if (!observe(actor, QuestContent.ECHO)) return false;
        show(actor); return true;
    }
    public static boolean open(ServerPlayer actor) {
        if (!request(actor) || !observe(actor, null)) return false;
        show(actor); return true;
    }
    public static boolean readGuide(ServerPlayer actor,net.minecraft.resources.ResourceLocation id) {
        return request(actor)&&QuestCatalog.definitions().containsKey(id)&&observe(actor,id);
    }
    /** Reconcile current native possession, real dimension and owner-provided ledgers; events only wake this reader. */
    static boolean observe(ServerPlayer actor,net.minecraft.resources.ResourceLocation holocron) {
        if(!valid(actor)||!PlayerInventoryHooks.ready(actor))return false;
        var before=actor.getData(QuestContent.KNOWLEDGE.get());var view=before.view();if(view.isEmpty()){actor.displayClientMessage(Component.translatable("quest.legacyoftheforce.unavailable"),false);return false;}
        QuestState next;
        try {
            var evidence=AdventureHooks.inspect(actor);var known=new HashSet<>(evidence.teachings());if(holocron!=null)known.add(holocron);
            var crystals=new HashMap<net.minecraft.resources.ResourceLocation,UUID>();
            for(int slot=0;slot<actor.getInventory().getContainerSize();slot++) {
                var value=CrystalIdentityAccess.inspect(actor.getInventory().getItem(slot));
                if(value.isPresent()&&value.get().origin()!=null&&QuestState.PLANETS.contains(value.get().origin()))crystals.putIfAbsent(value.get().origin(),value.get().id());
            }
            next=before.observe(known,actor.level().dimension().location(),crystals,evidence.rewardedBosses());
        } catch(RuntimeException invalid) {actor.displayClientMessage(Component.translatable("quest.legacyoftheforce.unavailable"),false);return false;}
        if(next.equals(before))return true;
        actor.setData(QuestContent.KNOWLEDGE.get(),next);
        try {
            var saved=new VerifiedPlayerSaveBarrier().persist(actor);
            if(!saved.getCompound("neoforge:attachments").getCompound("legacyoftheforce:quest_knowledge").equals(next.tag()))throw new java.io.IOException("Quest native save differs");
        } catch(java.io.IOException|RuntimeException failure) {
            // Preserve desired knowledge even if a native save hook changed the live attachment.
            actor.setData(QuestContent.KNOWLEDGE.get(),next);
            LegacyOfTheForce.LOGGER.error("Adventure knowledge save uncertain; native aggregate retained",failure);
            actor.connection.disconnect(Component.translatable("quest.legacyoftheforce.save_pending"));return false;
        }
        for(var id:next.view().orElseThrow().holocrons())if(!view.get().holocrons().contains(id)) {
            UUID receipt=UUID.nameUUIDFromBytes((actor.getUUID()+":holocron:"+id).getBytes(StandardCharsets.UTF_8));
            try{NeoForge.EVENT_BUS.post(new HolocronDiscovered(actor.getUUID(),receipt,id));}catch(RuntimeException failure){LegacyOfTheForce.LOGGER.error("Holocron consumer failed after durable knowledge commit; reconcile saved knowledge",failure);}
        }
        return true;
    }
    static List<QuestJournalMenu.Line> snapshot(QuestState state) {
        var lines=new ArrayList<QuestJournalMenu.Line>();var view=state.view();if(view.isEmpty())return List.of(new QuestJournalMenu.Line("quest.legacyoftheforce.unavailable",0));
        lines.add(new QuestJournalMenu.Line("quest.legacyoftheforce.optional",0));var definitions=QuestCatalog.definitions();
        if(view.get().holocrons().stream().anyMatch(id->!definitions.containsKey(id))||!definitions.containsKey(QuestContent.ECHO))lines.add(new QuestJournalMenu.Line("quest.legacyoftheforce.archive_unavailable",0));
        if(view.get().crystal()!=null&&!view.get().crystals().containsKey(QuestState.ILUM)&&lines.size()<QuestJournalMenu.MAX_LINES)lines.add(new QuestJournalMenu.Line("quest.legacyoftheforce.legacy_provenance",0));
        for(int section=1;section<=3;section++) {
            final int current=section;var entries=definitions.entrySet().stream().filter(entry->entry.getValue().visible(entry.getKey(),view.get()))
                    .filter(entry->section(entry.getKey(),entry.getValue())==current).sorted(Map.Entry.comparingByKey()).toList();
            lines.add(new QuestJournalMenu.Line("quest.legacyoftheforce.section_"+section,0));
            if(section==1&&!view.get().holocrons().contains(QuestContent.ECHO)&&definitions.containsKey(QuestContent.ECHO))lines.add(new QuestJournalMenu.Line("quest.legacyoftheforce.find_echo",0));
            if(entries.isEmpty())lines.add(new QuestJournalMenu.Line("quest.legacyoftheforce.section_empty",0));
            for(var entry:entries) {var definition=entry.getValue();
                if(lines.size()+3+definition.steps().size()>QuestJournalMenu.MAX_LINES-4){lines.add(new QuestJournalMenu.Line("quest.legacyoftheforce.more",0));return List.copyOf(lines);}
                lines.add(new QuestJournalMenu.Line(definition.title(),0));lines.add(new QuestJournalMenu.Line(definition.origin(),0));lines.add(new QuestJournalMenu.Line(definition.clue(),0));
                for(var step:definition.steps())lines.add(new QuestJournalMenu.Line(step.text(),definition.completed(step,entry.getKey(),view.get())?2:1));
            }
        }
        return List.copyOf(lines);
    }
    private static int section(net.minecraft.resources.ResourceLocation id,QuestDefinition definition){return definition.planet()!=null?2:id.getPath().startsWith("holocron_")?3:1;}
    private static void show(ServerPlayer actor) {
        var lines = snapshot(actor.getData(QuestContent.KNOWLEDGE.get()));
        actor.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new QuestJournalMenu(id, inventory, lines), Component.translatable("quest.legacyoftheforce.journal")), buffer -> QuestJournalMenu.write(buffer, lines));
    }
}
