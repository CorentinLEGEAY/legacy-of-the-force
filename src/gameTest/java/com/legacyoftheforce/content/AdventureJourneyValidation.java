package com.legacyoftheforce.content;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.lightsaber.*;
import com.legacyoftheforce.planets.*;
import com.mojang.brigadier.arguments.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Test-only read-only navigation hints and native evidence. Never grants a crystal or moves a player. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class AdventureJourneyValidation {
    public static boolean enabled() {return Boolean.getBoolean("lotf.validation.adventureJourney");}
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Session {
        final Path root;final CompoundTag proof=new CompoundTag();final ListTag paths=new ListTag(),crafts=new ListTag(),movement=new ListTag();
        final Map<UUID,Long> lastPath=new HashMap<>();final Set<UUID> arrived=new HashSet<>();
        BlockPos source,entrance;List<ServerPlayer> actors;boolean installed,failed;
        Session(Path root) {this.root=root;proof.putInt("schemaVersion",1);proof.putString("scope","guided_native_crystal_route");proof.put("paths",paths);proof.put("crafts",crafts);proof.put("movement",movement);}
    }
    private static void require(boolean valid,String reason) {if(!valid)throw new IllegalStateException(reason);}
    private static void send(ServerPlayer actor,String message) {actor.sendSystemMessage(Component.literal("LOTF_JOURNEY_"+message));}
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(!enabled())return;var root=event.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(root.getParent().getFileName().toString().startsWith("run-adventure-"),"Isolated journey required");SESSIONS.put(event.getServer(),new Session(root));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SESSIONS.remove(event.getServer());}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!enabled())return;
        event.getDispatcher().register(Commands.literal("lotf_adventure_journey")
            .then(Commands.literal("path").then(Commands.argument("goal",StringArgumentType.word()).then(Commands.argument("sequence",IntegerArgumentType.integer(1,512)).executes(context->{
                var actor=context.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);require(s!=null&&s.source!=null&&s.actors.contains(actor),"Native paired journey required");
                long now=actor.server.overworld().getGameTime();if(now-s.lastPath.getOrDefault(actor.getUUID(),-100L)<20) {send(actor,"BUSY "+IntegerArgumentType.getInteger(context,"sequence"));return 0;}s.lastPath.put(actor.getUUID(),now);require(s.paths.size()<512,"Bounded navigation trace");
                String goal=StringArgumentType.getString(context,"goal");require(Set.of("entrance","cavern").contains(goal),"Known test waypoint required");
                var target=goal.equals("entrance")?s.entrance:s.source.north(2).west(s.actors.indexOf(actor)*2);
                // A dimension-compatible gabarit is never added to the entity manager, ticked or persisted.
                var gabarit=new Zombie(EntityType.ZOMBIE,actor.serverLevel());gabarit.setPos(actor.position());gabarit.setOnGround(true);gabarit.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48);
                var navigation=new GroundPathNavigation(gabarit,actor.level());navigation.setMaxVisitedNodesMultiplier(4);navigation.setCanFloat(true);
                var path=navigation.createPath(Set.of(target),0);require(path!=null&&path.getNodeCount()>1,"Native pathfinder cannot progress toward "+goal+" from "+actor.position());
                var record=new CompoundTag();record.putUUID("owner",actor.getUUID());record.putString("goal",goal);record.putIntArray("from",new int[]{actor.getBlockX(),actor.getBlockY(),actor.getBlockZ()});
                var nodes=new ListTag();var message=new StringBuilder("PATH ").append(IntegerArgumentType.getInteger(context,"sequence")).append(' ');
                for(int i=0;i<Math.min(32,path.getNodeCount());i++) {var p=path.getNodePos(i);nodes.add(new IntArrayTag(new int[]{p.getX(),p.getY(),p.getZ()}));if(i>0)message.append(';');message.append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ());}
                record.put("nodes",nodes);s.paths.add(record);send(actor,message.toString());LegacyOfTheForce.LOGGER.info("ADVENTURE_JOURNEY_PATH owner={} goal={} nodes={} end={}",actor.getGameProfile().getName(),goal,nodes.size(),path.getEndNode().asBlockPos());return 1;
            }))))
            .then(Commands.literal("cavern").executes(context->{var actor=context.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);require(s!=null&&s.source!=null&&actor.blockPosition().distManhattan(s.source)<10,"Actual native cavern arrival required");s.arrived.add(actor.getUUID());return 1;}))
            .then(Commands.literal("installed").then(Commands.argument("workshop",BlockPosArgument.blockPos()).executes(context->{
                var actor=context.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);require(s!=null&&s.actors.getFirst()==actor&&s.arrived.size()==2&&!s.installed,"Two actual cavern visitors required");var pos=BlockPosArgument.getBlockPos(context,"workshop");
                require(actor.level().getBlockState(pos).is(SaberContent.WORKSHOP.get())&&actor.canInteractWithBlock(pos,0),"Real paid workshop within reach required");
                require(s.proof.contains("pickup")&&actor.level().getBlockState(s.source).isAir(),"Native source must be consumed and actual drop picked up");
                var saber=actor.getMainHandItem().get(SaberContent.ASSEMBLY.get());require(saber!=null&&saber.crystal().isPresent()&&saber.active(),"Identified crystal must actually be installed and active");
                require(saber.crystal().orElseThrow().tag().equals(s.proof.getCompound("drop").getCompound("stack").getCompound("components").getCompound("legacyoftheforce:kyber")),"Native dropped crystal identity must be preserved");
                try {
                    var players=new ListTag();for(var p:s.actors)players.add(new VerifiedPlayerSaveBarrier().persist(p));s.proof.put("players",players);s.proof.putIntArray("workshop",new int[]{pos.getX(),pos.getY(),pos.getZ()});new VerifiedChunkSaveBarrier().persist(actor.serverLevel(),Set.of(pos,s.source));
                    AtomicNbtFile.write(s.root.resolve("validation/adventure-crystal-stage.nbt"),s.proof,1048576);s.installed=true;for(var p:s.actors)send(p,"INSTALLED");LegacyOfTheForce.LOGGER.info("ADVENTURE_JOURNEY_CRYSTAL_PASS");return 1;
                } catch(java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);}
            }))));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||s.failed)return;
        try {
            if(s.source!=null) {
                if(!s.installed&&server.overworld().getGameTime()%20==0)for(var actor:s.actors) {
                    require(s.movement.size()<3600,"Bounded native movement evidence");var sample=new CompoundTag();sample.putUUID("owner",actor.getUUID());sample.putLong("tick",server.overworld().getGameTime());sample.putDouble("x",actor.getX());sample.putDouble("y",actor.getY());sample.putDouble("z",actor.getZ());sample.putFloat("health",actor.getHealth());s.movement.add(sample);
                }
                return;
            }
            var actors=AdventureRoute.NAMES.stream().map(server.getPlayerList()::getPlayerByName).toList();if(actors.stream().anyMatch(p->p==null||!p.level().dimension().equals(PlanetContent.ILUM)))return;s.actors=actors;
            var level=server.getLevel(PlanetContent.ILUM);var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE,PlanetContent.id("ilum_crystal_mine")));
            var located=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),BlockPos.ZERO,64,false);require(located!=null,"Actual mine required");
            var chunk=level.getChunk(located.getFirst().getX()>>4,located.getFirst().getZ()>>4);var start=chunk.getAllStarts().get(holder.value());require(start!=null&&start.isValid(),"Native mine start required");var box=start.getBoundingBox();
            s.source=new BlockPos(box.minX()+7,box.minY()+1,box.minZ()+8);s.entrance=new BlockPos(box.minX()+2,box.minY()+13,box.minZ()+1);
            for(int x=box.minX()>>4;x<=box.maxX()>>4;x++)for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++)level.getChunk(x,z);
            require(level.getBlockState(s.source).is(PlanetContent.CRYSTAL.get()),"Generated crystal source required");s.proof.put("mine",start.createTag(StructurePieceSerializationContext.fromLevel(level),chunk.getPos()));s.proof.putIntArray("source",new int[]{s.source.getX(),s.source.getY(),s.source.getZ()});
            for(var actor:actors)send(actor,"SITE "+s.source.getX()+" "+s.source.getY()+" "+s.source.getZ()+" "+s.entrance.getX()+" "+s.entrance.getY()+" "+s.entrance.getZ());
            LegacyOfTheForce.LOGGER.info("ADVENTURE_JOURNEY_GENERATED_MINE source={} entrance={}",s.source,s.entrance);
        } catch(Exception failure) {s.failed=true;LegacyOfTheForce.LOGGER.error("ADVENTURE_JOURNEY_SERVER_FAILED",failure);}
    }
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer actor))return;var s=SESSIONS.get(actor.server);if(s==null||!AdventureRoute.NAMES.contains(actor.getGameProfile().getName())||!(event.getInventory() instanceof CraftingContainer grid))return;
        var recipe=actor.server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid.asCraftInput(),actor.level()).orElseThrow();var tag=new CompoundTag();tag.putUUID("owner",actor.getUUID());tag.putString("recipe",recipe.id().toString());tag.put("result",recipe.value().getResultItem(actor.registryAccess()).save(actor.registryAccess()));var inputs=new ListTag();for(int i=0;i<grid.getContainerSize();i++)if(!grid.getItem(i).isEmpty()) {var input=(CompoundTag)grid.getItem(i).save(actor.registryAccess());input.putInt("gridSlot",i);inputs.add(input);}tag.put("inputs",inputs);s.crafts.add(tag);
    }
    @SubscribeEvent public static void drop(EntityJoinLevelEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof ItemEntity item)||!item.getItem().is(SaberContent.KYBER.get()))return;var s=SESSIONS.get(level.getServer());if(s==null||s.source==null||!level.dimension().equals(PlanetContent.ILUM))return;
        require(!s.proof.contains("drop"),"Only one native crystal drop allowed");var tag=new CompoundTag();tag.putUUID("entity",item.getUUID());tag.put("stack",item.getItem().save(level.registryAccess()));tag.putDouble("x",item.getX());tag.putDouble("y",item.getY());tag.putDouble("z",item.getZ());s.proof.put("drop",tag);
    }
    @SubscribeEvent public static void pickup(ItemEntityPickupEvent.Post event) {
        if(!(event.getPlayer() instanceof ServerPlayer actor)||!event.getOriginalStack().is(SaberContent.KYBER.get()))return;var s=SESSIONS.get(actor.server);if(s==null)return;
        require(s.proof.getCompound("drop").getUUID("entity").equals(event.getItemEntity().getUUID())&&actor==s.actors.getFirst()&&!s.proof.contains("pickup"),"Only original native drop/owner pickup allowed");var tag=new CompoundTag();tag.putUUID("entity",event.getItemEntity().getUUID());tag.putUUID("owner",actor.getUUID());tag.put("stack",event.getOriginalStack().save(actor.registryAccess()));s.proof.put("pickup",tag);
    }
}
