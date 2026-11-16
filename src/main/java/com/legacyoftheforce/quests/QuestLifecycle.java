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
        return actor.server.isSameThread() && actor.isAlive() && !actor.isFakePlayer() && !actor.isSpectator();
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
        var pending = PENDING.computeIfAbsent(actor.server, ignored -> new HashSet<>());
        if (pending.size() < 1024) pending.add(actor.getUUID());
    }
    @SubscribeEvent public void pickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer actor && CrystalIdentityAccess.identified(event.getOriginalStack()).isPresent()) enqueue(actor);
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer actor) enqueue(actor); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer actor) enqueue(actor); }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        var pending = PENDING.remove(event.getServer()); if (pending == null) return;
        for (var id : pending) { var actor = event.getServer().getPlayerList().getPlayer(id); if (actor != null) observe(actor, null); }
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
                || !PROTECTION.mayUseContainer(actor, actor.serverLevel(), pos) || !QuestCatalog.definitions().containsKey(QuestContent.ECHO)) return false;
        if (!observe(actor, QuestContent.ECHO)) return false;
        show(actor); return true;
    }
    public static boolean open(ServerPlayer actor) {
        if (!request(actor) || !observe(actor, null)) return false;
        show(actor); return true;
    }
    /** Reconcile authoritative possession and current dimension; never accept IDs from a packet. */
    static boolean observe(ServerPlayer actor, net.minecraft.resources.ResourceLocation holocron) {
        if (!valid(actor)) return false;
        var before = actor.getData(QuestContent.KNOWLEDGE.get()); var view = before.view();
        if (view.isEmpty()) { actor.displayClientMessage(Component.translatable("quest.legacyoftheforce.unavailable"), false); return false; }
        UUID crystal = null;
        if (view.get().crystal() == null) for (int slot = 0; slot < actor.getInventory().getContainerSize(); slot++) {
            var stack = actor.getInventory().getItem(slot);
            var id = CrystalIdentityAccess.identified(stack); if (id.isPresent()) { crystal = id.get(); break; }
        }
        try {
            var next = before.observe(holocron, actor.level().dimension().location().toString().equals("legacyoftheforce:ilum"), crystal);
            if (next.equals(before)) return true;
            actor.setData(QuestContent.KNOWLEDGE.get(), next);
            var saved = new VerifiedPlayerSaveBarrier().persist(actor);
            if (!saved.getCompound("neoforge:attachments").getCompound("legacyoftheforce:quest_knowledge").equals(next.tag()))
                throw new java.io.IOException("Quest native save differs");
        } catch (Exception failure) {
            // There is no economic reward. A save may have reached disk before a failed verification;
            // keeping the old in-memory state permits an explicit retry with the same stable receipt.
            actor.setData(QuestContent.KNOWLEDGE.get(), before);
            NEXT_REQUEST.computeIfAbsent(actor.server, ignored -> new HashMap<>()).put(actor.getUUID(), actor.server.overworld().getGameTime() + 200);
            LegacyOfTheForce.LOGGER.error("Optional knowledge save failed for {}; no reward granted", actor.getUUID(), failure);
            actor.displayClientMessage(Component.translatable("quest.legacyoftheforce.unavailable"), false); return false;
        }
        if (holocron != null && !view.get().holocrons().contains(holocron)) {
            UUID receipt = UUID.nameUUIDFromBytes((actor.getUUID() + ":holocron:" + holocron).getBytes(StandardCharsets.UTF_8));
            try { NeoForge.EVENT_BUS.post(new HolocronDiscovered(actor.getUUID(), receipt, holocron)); }
            catch (RuntimeException consumerFailure) { LegacyOfTheForce.LOGGER.error("Holocron consumer failed after durable knowledge commit; reconcile saved knowledge", consumerFailure); }
        }
        return true;
    }
    static List<QuestJournalMenu.Line> snapshot(QuestState state) {
        var lines = new ArrayList<QuestJournalMenu.Line>(); var view = state.view();
        if (view.isEmpty()) return List.of(new QuestJournalMenu.Line("quest.legacyoftheforce.unavailable", 0));
        lines.add(new QuestJournalMenu.Line("quest.legacyoftheforce.optional", 0));
        var entries = QuestCatalog.definitions().entrySet().stream().filter(entry -> view.get().holocrons().contains(entry.getKey())).sorted(Map.Entry.comparingByKey()).toList();
        if (entries.isEmpty()) lines.add(new QuestJournalMenu.Line("quest.legacyoftheforce.find_echo", 0));
        for (var entry : entries) {
            var definition = entry.getValue();
            if (lines.size() + 3 + definition.steps().size() > 47) { lines.add(new QuestJournalMenu.Line("quest.legacyoftheforce.more", 0)); break; }
            lines.add(new QuestJournalMenu.Line(definition.title(), 0)); lines.add(new QuestJournalMenu.Line(definition.origin(), 0));
            lines.add(new QuestJournalMenu.Line(definition.clue(), 0));
            for (var step : definition.steps()) lines.add(new QuestJournalMenu.Line(step.text(), definition.completed(step, entry.getKey(), view.get()) ? 2 : 1));
        }
        return List.copyOf(lines);
    }
    private static void show(ServerPlayer actor) {
        var lines = snapshot(actor.getData(QuestContent.KNOWLEDGE.get()));
        actor.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new QuestJournalMenu(id, inventory, lines), Component.translatable("quest.legacyoftheforce.journal")), buffer -> QuestJournalMenu.write(buffer, lines));
    }
}
