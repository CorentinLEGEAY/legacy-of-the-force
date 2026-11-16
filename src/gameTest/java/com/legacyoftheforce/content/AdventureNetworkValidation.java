package com.legacyoftheforce.content;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.lightsaber.SaberContent;
import com.legacyoftheforce.speeder.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Finite vanilla supplies/camp terrain only. All mod items must be crafted by genuine clients. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class AdventureNetworkValidation {
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Session {
        final Path root;final long started;final List<ServerPlayer> players=new ArrayList<>();final ListTag before=new ListTag(),crafts=new ListTag();final Set<UUID> ready=new HashSet<>();String stage="WAIT";
        Session(MinecraftServer server,Path root) { this.root=root;started=server.overworld().getGameTime(); }
    }
    private static void require(boolean valid,String message) { if(!valid)throw new IllegalStateException(message); }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventurePrep"))return;
        event.getDispatcher().register(Commands.literal("lotf_adventure_test").then(Commands.literal("prepared").executes(context->{
            var actor=context.getSource().getPlayerOrException();var session=SESSIONS.get(actor.server);
            if(session==null||!session.players.contains(actor)||!actor.isAlive())return 0;session.ready.add(actor.getUUID());return 1;
        })));
    }
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventurePrep"))return;var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(server.isDedicatedServer()&&root.getParent().getFileName().toString().startsWith("run-adventure-"),"Isolated dedicated preparation world required");
        require(!Files.exists(root.resolve("validation/adventure-preparation-server.nbt")),"Preserve existing proof");SESSIONS.put(server,new Session(server,root));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { SESSIONS.remove(event.getServer()); }
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer actor))return;var session=SESSIONS.get(actor.server);
        if(session==null||!session.players.contains(actor)||!(event.getInventory() instanceof CraftingContainer grid))return;
        var recipe=actor.server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid.asCraftInput(),actor.level()).orElseThrow(()->new IllegalStateException("Crafting event without native recipe"));
        var tag=new CompoundTag();tag.putUUID("owner",actor.getUUID());tag.putString("recipe",recipe.id().toString());tag.put("result",recipe.value().getResultItem(actor.registryAccess()).save(actor.registryAccess()));
        var inputs=new ListTag();for(int i=0;i<grid.getContainerSize();i++)if(!grid.getItem(i).isEmpty()) {var input=(CompoundTag)grid.getItem(i).save(actor.registryAccess());input.putInt("gridSlot",i);inputs.add(input);}tag.put("inputs",inputs);session.crafts.add(tag);
        LegacyOfTheForce.LOGGER.info("ADVENTURE_CRAFT_SERVER owner={} recipe={}",actor.getGameProfile().getName(),recipe.id());
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||Set.of("PASS","FAILED").contains(s.stage))return;
        try {
            require(server.overworld().getGameTime()-s.started<12000,"Preparation exceeded 600 server seconds");
            if(s.stage.equals("WAIT")) {
                for(String name:AdventureRoute.NAMES)if(server.getPlayerList().getPlayerByName(name)==null)return;
                require(server.getPlayerList().getPlayerCount()==2,"Exactly two genuine clients required");
                for(int i=0;i<2;i++) {
                    var actor=server.getPlayerList().getPlayerByName(AdventureRoute.NAMES.get(i));var connection=actor.connection.getConnection();
                    require(connection.getRemoteAddress() instanceof InetSocketAddress a&&a.getAddress().isLoopbackAddress()&&!connection.isMemoryConnection(),"Genuine loopback socket required");
                    require(actor.getInventory().isEmpty()&&actor.gameMode.getGameModeForPlayer()==GameType.SURVIVAL,"Fresh survival inventory required");
                    var progress=actor.getData(com.legacyoftheforce.rpg.ProgressAttachments.PLAYER_PROGRESS).snapshot();require(progress.level()==1&&progress.xpInLevel()==0&&progress.alignmentTenths()==0,"No granted RPG progress");
                    setupCamp(server.overworld(),AdventureRoute.camp(i));supply(actor);var pos=AdventureRoute.camp(i);
                    actor.teleportTo(server.overworld(),pos.getX()+.5,pos.getY(),pos.getZ()+.5,Set.of(),0,0);actor.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);actor.resetFallDistance();actor.setOnGround(true);
                    s.players.add(actor);s.before.add(new VerifiedPlayerSaveBarrier().persist(actor));
                }
                require(!s.players.get(0).connection.getConnection().getRemoteAddress().equals(s.players.get(1).connection.getConnection().getRemoteAddress()),"Distinct sockets required");
                s.stage="CRAFT";for(var actor:s.players)actor.sendSystemMessage(Component.literal("LOTF_ADVENTURE_CAMP_READY"));return;
            }
            for(var actor:s.players)require(actor.isAlive()&&server.getPlayerList().getPlayer(actor.getUUID())==actor&&actor.connection.isAcceptingMessages(),"Original players must remain alive/connected");
            if(s.ready.size()!=2)return;
            var players=new ListTag();var vehicles=new ListTag();var blocks=new HashSet<BlockPos>();
            for(int index=0;index<2;index++) {
                var actor=s.players.get(index);require(actor.getInventory().countItem(SaberContent.SABER.get())==1,"One paid hilt required");
                var hilt=actor.getInventory().items.stream().filter(stack->stack.is(SaberContent.SABER.get())).findFirst().orElseThrow().get(SaberContent.ASSEMBLY.get());
                require(hilt!=null&&hilt.supported()&&hilt.crystal().isEmpty(),"Native hilt cannot invent a crystal");
                require(actor.getInventory().countItem(com.legacyoftheforce.planets.PlanetContent.COORDINATES.get())==1&&actor.getInventory().countItem(com.legacyoftheforce.planets.PlanetContent.TERMINAL_ITEM.get())==1&&actor.getInventory().countItem(SpeederContent.ENERGY_CELL.get())==4,"Crafted travel supplies required");
                for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))require(!actor.getItemBySlot(slot).isEmpty(),"Crafted armor must be equipped");
                var camp=AdventureRoute.camp(index);require(server.overworld().getBlockState(AdventureRoute.table(index)).is(Blocks.CRAFTING_TABLE)&&server.overworld().getBlockState(AdventureRoute.workshop(index)).is(SaberContent.WORKSHOP.get())&&server.overworld().getBlockState(AdventureRoute.garage(index)).is(SpeederContent.GARAGE.get())&&server.overworld().getBlockState(AdventureRoute.terminal(index)).is(com.legacyoftheforce.planets.PlanetContent.TERMINAL.get()),"Stations must actually be placed");
                blocks.addAll(List.of(AdventureRoute.table(index),AdventureRoute.workshop(index),AdventureRoute.garage(index),AdventureRoute.terminal(index)));
                var found=server.overworld().getEntitiesOfClass(SpeederEntity.class,new AABB(camp).inflate(10),candidate->{
                    var state=candidate.saveWithoutId(new CompoundTag()).getCompound("vehicleState");return state.hasUUID("ownerId")&&state.getUUID("ownerId").equals(actor.getUUID());
                });require(found.size()==1,"Exactly one paid vehicle for this owner near camp");var vehicle=found.getFirst();new VerifiedEntitySaveBarrier().persist(vehicle);vehicles.add(vehicle.saveWithoutId(new CompoundTag()));
                var profile=actor.getData(com.legacyoftheforce.rpg.ProgressAttachments.PLAYER_PROGRESS).snapshot();require(profile.level()==1&&profile.xpInLevel()==0&&profile.alignmentTenths()==0,"Crafting cannot grant RPG progress");
                require(s.crafts.stream().map(t->(CompoundTag)t).filter(t->t.getUUID("owner").equals(actor.getUUID())).count()==AdventureRoute.CRAFTS.size(),"Every planned native craft must be observed");players.add(new VerifiedPlayerSaveBarrier().persist(actor));
            }
            new VerifiedChunkSaveBarrier().persist(server.overworld(),blocks);var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putString("scope","preparation_only");proof.putLong("pid",ProcessHandle.current().pid());proof.put("before",s.before);proof.put("players",players);proof.put("crafts",s.crafts);proof.put("vehicles",vehicles);
            AtomicNbtFile.write(s.root.resolve("validation/adventure-preparation-server.nbt"),proof,1048576);s.stage="PASS";
            for(var actor:s.players)actor.sendSystemMessage(Component.literal("LOTF_ADVENTURE_PREPARATION_PASS"));LegacyOfTheForce.LOGGER.info("ADVENTURE_PREPARATION_SERVER_PASS pid={}",ProcessHandle.current().pid());
        } catch(Exception failure) {
            s.stage="FAILED";LegacyOfTheForce.LOGGER.error("ADVENTURE_PREPARATION_SERVER_FAILED",failure);
            try {var proof=new CompoundTag();proof.putString("error",failure.toString());AtomicNbtFile.write(s.root.resolve("validation/adventure-failed.nbt"),proof,16384);}catch(Exception ignored){}
        }
    }
    private static void setupCamp(ServerLevel level,BlockPos center) {
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++) {
            var pos=center.offset(x,-1,z);level.getChunk(pos.getX()>>4,pos.getZ()>>4);level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
            for(int y=0;y<6;y++)level.setBlockAndUpdate(center.offset(x,y,z),Blocks.AIR.defaultBlockState());
        }
    }
    private static void supply(ServerPlayer actor) {
        var materials=new LinkedHashMap<Item,Integer>();materials.put(Items.OAK_PLANKS,48);materials.put(Items.IRON_INGOT,96);materials.put(Items.COPPER_INGOT,48);materials.put(Items.REDSTONE,64);materials.put(Items.LEATHER,16);materials.put(Items.PAPER,32);materials.put(Items.INK_SAC,4);materials.put(Items.GLASS,8);materials.put(Items.AMETHYST_SHARD,8);materials.put(Items.LAPIS_LAZULI,16);materials.put(Items.STONE,8);materials.put(Items.GOLD_INGOT,8);materials.put(Items.POPPY,4);materials.put(Items.COOKED_BEEF,32);
        int slot=0;for(var entry:materials.entrySet()) {require(BuiltInRegistries.ITEM.getKey(entry.getKey()).getNamespace().equals("minecraft"),"Vanilla supplies only");int left=entry.getValue();while(left>0) {int count=Math.min(64,left);actor.getInventory().setItem(slot++,new ItemStack(entry.getKey(),count));left-=count;}}
        actor.getInventory().setChanged();actor.inventoryMenu.broadcastChanges();
    }
}
