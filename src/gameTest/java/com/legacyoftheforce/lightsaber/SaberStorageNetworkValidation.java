package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.content.AdventureRoute;
import com.legacyoftheforce.core.operation.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Empty storage fixtures only. Every item movement comes from a native client click. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class SaberStorageNetworkValidation {
    private static final boolean RECOVER=Boolean.getBoolean("lotf.validation.saberStorageRecover");
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Session {
        final Path root;final long start;final ListTag before=new ListTag(),frames=new ListTag();
        final Map<UUID,String> phases=new HashMap<>();final Map<UUID,UUID> targets=new HashMap<>();
        List<ServerPlayer> actors=List.of();String stage="WAIT";
        Session(MinecraftServer server,Path root) {this.root=root;start=server.overworld().getGameTime();}
    }
    static BlockPos storage(int role) {return AdventureRoute.camp(role).east().south();}
    private static String artifact() {return RECOVER?"saberstoragerecover":"saberstorage";}
    private static void require(boolean ok,String reason) {if(!ok)throw new IllegalStateException(reason);}
    private static void send(ServerPlayer actor,String message) {actor.sendSystemMessage(Component.literal("LOTF_SABER_STORAGE_"+message));}
    private static UUID identity(ItemStack stack) {var data=stack.get(SaberContent.ASSEMBLY.get());return data==null?null:data.tag().getUUID("saberId");}
    private static long revision(ServerPlayer actor) {return actor.getData(SaberContent.STORAGE_RECEIPT.get()).stamp().orElseThrow().revision();}
    @SubscribeEvent public static void start(ServerStartedEvent event) throws Exception {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberStorage"))return;
        var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(root.getParent().getFileName().toString().startsWith("run-adventure-")&&Files.exists(root.resolve("validation/adventure-saberworkshop-server.nbt")),"Stopped completed workshop copy required");
        require(Files.exists(root.resolve("validation/adventure-saberstorage-server.nbt"))==RECOVER,"Fresh storage scenario or its completed stopped copy required");
        if(!RECOVER) {
            var level=server.overworld();
            for(int role=0;role<2;role++) {var pos=storage(role);require(level.isEmptyBlock(pos)&&level.isEmptyBlock(pos.above()),"Empty fixture position required");
                if(role==1)require(level.setBlock(pos,Blocks.BARREL.defaultBlockState(),3),"Place empty barrel");
                else {require(level.isEmptyBlock(pos.east())&&level.isEmptyBlock(pos.east().above()),"Empty double chest fixture required");
                    require(level.setBlock(pos,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH).setValue(ChestBlock.TYPE,ChestType.LEFT),2),"Place first empty chest");
                    require(level.setBlock(pos.east(),Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH).setValue(ChestBlock.TYPE,ChestType.RIGHT),2),"Place second empty chest");}
            }
            new VerifiedChunkSaveBarrier().persist(level,Set.of(storage(0),storage(0).east(),storage(1)));
        }
        SESSIONS.put(server,new Session(server,root));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SESSIONS.remove(event.getServer());}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberStorage"))return;
        event.getDispatcher().register(Commands.literal("lotf_saber_storage_test").then(Commands.argument("phase",StringArgumentType.word()).executes(ctx->{
            var actor=ctx.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);
            require(s!=null&&s.stage.equals("RUN")&&s.actors.contains(actor),"Known native storage actor required");
            String phase=StringArgumentType.getString(ctx,"phase"),expected=s.phases.get(actor.getUUID());require(phase.equals(expected),"Expected "+expected+", received "+phase);
            try {acknowledge(s,actor,phase);}catch(java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);}return 1;
        })));
    }
    private static void acknowledge(Session s,ServerPlayer actor,String phase) throws java.io.IOException {
        int role=s.actors.indexOf(actor);UUID target=s.targets.get(actor.getUUID());long base=RECOVER?3:0;
        if(phase.equals("closed")) {require(actor.containerMenu==actor.inventoryMenu&&actor.getInventory().countItem(SaberContent.SABER.get())==1,"Normal close with one remaining hilt required");frame(s,actor,phase);s.phases.put(actor.getUUID(),"done");return;}
        require(actor.containerMenu instanceof ChestMenu,"Native storage menu required");var menu=(ChestMenu)actor.containerMenu;
        var endpoints=SaberStorageImages.endpoints(menu);require(endpoints.size()==(role==0?2:1)&&endpoints.stream().anyMatch(e->e.getBlockPos().equals(storage(role))),"Expected native chest/barrel endpoints required");
        if(phase.equals("opened")) {
            require(revision(actor)==base&&menu.getCarried().isEmpty(),"Initial receipt and empty cursor required");
            require(RECOVER?target.equals(identity(menu.getSlot(0).getItem())):menu.getContainer().isEmpty(),"Persisted target or fresh empty fixture required");
            frame(s,actor,phase);s.phases.put(actor.getUUID(),RECOVER?"withdrawn":"deposited");send(actor,RECOVER?"WITHDRAW":"SELECT");return;
        }
        if(phase.equals("withdrawn")) {
            require(menu.getContainer().isEmpty()&&target.equals(identity(menu.getCarried()))&&actor.getInventory().countItem(SaberContent.SABER.get())==1,"Real withdrawal must move the stored target only to the external cursor");
            require(revision(actor)==(RECOVER?4:2),"Exactly one receipt for withdrawal");frame(s,actor,phase);s.phases.put(actor.getUUID(),"stored");send(actor,"DEPOSIT");return;
        }
        require(Set.of("deposited","stored").contains(phase)&&menu.getCarried().isEmpty()&&target.equals(identity(menu.getSlot(0).getItem()))&&actor.getInventory().countItem(SaberContent.SABER.get())==1,"Real deposit must move only the target to storage");
        require(revision(actor)==(phase.equals("deposited")?1:RECOVER?5:3),"Exactly one receipt for deposit");frame(s,actor,phase);
        s.phases.put(actor.getUUID(),phase.equals("deposited")?"withdrawn":"closed");send(actor,phase.equals("deposited")?"WITHDRAW":"CLOSE");
    }
    private static void frame(Session s,ServerPlayer actor,String phase) throws java.io.IOException {
        var frame=new CompoundTag();frame.putUUID("owner",actor.getUUID());frame.putString("phase",phase);frame.putLong("tick",actor.server.overworld().getGameTime());frame.put("player",new VerifiedPlayerSaveBarrier().read(actor));
        var images=new ListTag();if(actor.containerMenu instanceof ChestMenu)for(var endpoint:SaberStorageImages.endpoints(actor.containerMenu))images.add(SaberStorageImages.image(endpoint));frame.put("storage",images);s.frames.add(frame);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||Set.of("PASS","FAILED").contains(s.stage))return;
        try {
            require(server.overworld().getGameTime()-s.start<6000,"Native storage deadline");
            if(s.stage.equals("WAIT")) {
                var one=server.getPlayerList().getPlayerByName(AdventureRoute.NAMES.get(0));var two=server.getPlayerList().getPlayerByName(AdventureRoute.NAMES.get(1));if(one==null||two==null)return;
                require(server.getPlayerList().getPlayerCount()==2,"Two actual sockets required");s.actors=List.of(one,two);
                CompoundTag previous=RECOVER?AtomicNbtFile.read(s.root.resolve("validation/adventure-saberstorage-server.nbt"),4194304):null;
                for(int role=0;role<2;role++) {var actor=s.actors.get(role);
                    require(!actor.hasPermissions(2)&&!actor.connection.getConnection().isMemoryConnection()&&actor.getInventory().countItem(SaberContent.SABER.get())==(RECOVER?1:2),"Unchanged non-OP native inventory required");
                    require(actor.canInteractWithBlock(storage(role),0),"Existing player position must reach storage fixture without teleportation");
                    UUID target=RECOVER?previous.getList("targets",Tag.TAG_COMPOUND).getCompound(role).getUUID("saber"):actor.getInventory().items.stream().map(SaberStorageNetworkValidation::identity).filter(Objects::nonNull).findFirst().orElseThrow();
                    s.targets.put(actor.getUUID(),target);s.before.add(new VerifiedPlayerSaveBarrier().persist(actor));s.phases.put(actor.getUUID(),"opened");
                    var pos=storage(role);send(actor,"OPEN "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" "+target);
                }s.stage="RUN";
            }else if(s.phases.values().stream().allMatch("done"::equals)) {
                var after=new ListTag();for(var actor:s.actors)after.add(new VerifiedPlayerSaveBarrier().persist(actor));
                new VerifiedChunkSaveBarrier().persist(server.overworld(),Set.of(storage(0),storage(0).east(),storage(1)));server.saveEverything(true,true,true);
                var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putBoolean("recovery",RECOVER);proof.put("before",s.before);proof.put("after",after);proof.put("frames",s.frames);
                var targets=new ListTag();var images=new ListTag();for(int role=0;role<2;role++) {var value=new CompoundTag();value.putUUID("owner",s.actors.get(role).getUUID());value.putUUID("saber",s.targets.get(s.actors.get(role).getUUID()));targets.add(value);images.add(SaberStorageImages.image(server.overworld().getBlockEntity(storage(role))));if(role==0)images.add(SaberStorageImages.image(server.overworld().getBlockEntity(storage(role).east())));}proof.put("targets",targets);proof.put("storage",images);
                AtomicNbtFile.write(s.root.resolve("validation/adventure-"+artifact()+"-server.nbt"),proof,4194304);s.stage="PASS";for(var actor:s.actors)send(actor,"PASS");LegacyOfTheForce.LOGGER.info("ADVENTURE_{}_SERVER_PASS",artifact().toUpperCase(Locale.ROOT));
            }
        }catch(Exception failure) {s.stage="FAILED";LegacyOfTheForce.LOGGER.error("ADVENTURE_"+artifact().toUpperCase(Locale.ROOT)+"_SERVER_FAILED",failure);for(var actor:s.actors)send(actor,"FAILED");}
    }
}
