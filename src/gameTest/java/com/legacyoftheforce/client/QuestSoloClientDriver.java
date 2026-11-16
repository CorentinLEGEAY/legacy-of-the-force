package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import com.legacyoftheforce.quests.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Test-only native client: production commands/use packets, S2C menu observation and framebuffer captures. */
@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class QuestSoloClientDriver {
    private static final boolean RECOVER=Boolean.getBoolean("lotf.validation.questSoloRecover");
    private static final long STARTED=System.nanoTime();
    private static long ticks,ready;private static int commands,itemUses;private static String stage="START";
    private static ListTag emptyLines,knownLines;private static BlockPos source;private static boolean backedUp;
    private static void require(boolean value,String message) { if(!value) throw new IllegalStateException(message); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("lotf.validation.questSolo")) return;
        var mc=Minecraft.getInstance();ticks++;
        if(stage.equals("PASS")||stage.equals("FAILED")) { mc.stop();return; }
        try {
            require((System.nanoTime()-STARTED)/1_000_000_000L<240,"Solo quest client timed out");
            require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().startsWith("run-quest-client-solo-"),"Isolated client profile required");
            if(!backedUp&&mc.screen instanceof BackupConfirmScreen confirm
                    &&(confirm.getTitle().getString().equals(Component.translatable("selectWorld.backupQuestion.experimental").getString())
                    ||confirm.getTitle().getString().equals(Component.translatable("selectWorld.backupQuestion.customized").getString()))) {
                String label=Component.translatable("selectWorld.backupJoinConfirmButton").getString();
                var button=confirm.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals(label)).map(w->(Button)w).findFirst().orElseThrow();
                backedUp=true;button.onPress();return;
            }
            var root=mc.gameDirectory.toPath();var world=root.resolve("saves/QuestValidation");
            require(!Files.exists(world.resolve("validation/quest-solo-failed.nbt")),"Integrated validator failed; preserve evidence");
            if(mc.player==null||mc.level==null||mc.gameMode==null||mc.getConnection()==null) return;
            require(mc.player.isAlive(),"Owner must stay alive");
            if(stage.equals("START")) { stage="WARM";ready=ticks+40;return; }
            if(stage.equals("WARM")) {
                if(ticks<ready) return;command(mc);stage=RECOVER?"KNOWN":"EMPTY";return;
            }
            if(stage.equals("EMPTY")) {
                if(!(mc.player.containerMenu instanceof QuestJournalMenu menu)||!(mc.screen instanceof QuestJournalScreen)) return;
                require(menu.lines().stream().anyMatch(l->l.key().equals("quest.legacyoftheforce.find_echo")),"Fresh client must receive only an empty collection");
                emptyLines=lines(menu);capture(mc,"quest-empty.png");stage="CLOSE_EMPTY";ready=ticks+12;return;
            }
            if(stage.equals("CLOSE_EMPTY")) { if(ticks<ready) return;mc.player.closeContainer();stage="USE";ready=ticks+20;return; }
            if(stage.equals("USE")) {
                if(ticks<ready) return;source=findSource(mc);if(source==null) return;
                require(mc.player.distanceToSqr(Vec3.atCenterOf(source))<16,"Use must be within native reach");
                mc.player.getInventory().selected=0;
                mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(source),Direction.NORTH,source,false));
                itemUses++;stage="KNOWN";return;
            }
            if(stage.equals("KNOWN")) {
                if(!(mc.player.containerMenu instanceof QuestJournalMenu menu)||!(mc.screen instanceof QuestJournalScreen)) return;
                verifyKnown(menu);knownLines=lines(menu);stage="CAPTURE_KNOWN";ready=ticks+15;return;
            }
            if(stage.equals("CAPTURE_KNOWN")) {
                if(ticks<ready) return;capture(mc,"quest-known.png");
                var down=mc.screen.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals("▼")).map(w->(Button)w).findFirst().orElseThrow();
                down.onPress();stage="CAPTURE_SCROLL";ready=ticks+12;return;
            }
            if(stage.equals("CAPTURE_SCROLL")) {
                if(ticks<ready) return;capture(mc,"quest-scrolled.png");mc.player.closeContainer();stage="REOPEN";ready=ticks+20;return;
            }
            if(stage.equals("REOPEN")) {
                if(ticks<ready) return;
                require(mc.player.getInventory().isEmpty(),"Knowledge cannot depend on a physical journal or holocron item");
                if(!RECOVER) require(mc.player.distanceToSqr(Vec3.atCenterOf(source))>256,"Reopening must happen away from the discovered block");
                command(mc);stage="VERIFY_REOPEN";return;
            }
            if(stage.equals("VERIFY_REOPEN")) {
                if(!(mc.player.containerMenu instanceof QuestJournalMenu menu)||!(mc.screen instanceof QuestJournalScreen)) return;
                verifyKnown(menu);require(lines(menu).equals(knownLines),"Reopening must retain the same personal guide");
                if(!Files.exists(world.resolve("validation/"+(RECOVER?"quest-solo-recovered.nbt":"quest-solo-server.nbt")))) return;
                for(String file:List.of("quest-known.png","quest-scrolled.png")) if(!Files.isRegularFile(root.resolve("screenshots/"+file))||Files.size(root.resolve("screenshots/"+file))==0) return;
                var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putUUID("owner",mc.player.getUUID());
                proof.putBoolean("recovered",RECOVER);proof.put("knownLines",knownLines);if(emptyLines!=null) proof.put("emptyLines",emptyLines);
                proof.putInt("commands",commands);proof.putInt("itemUses",itemUses);proof.put("Inventory",mc.player.getInventory().save(new ListTag()));
                proof.putString("dimension",mc.level.dimension().location().toString());proof.putBoolean("nativeJournalScreen",true);
                AtomicNbtFile.write(root.resolve("validation/quest-solo-client.nbt"),proof,262144);
                stage="PASS";LegacyOfTheForce.LOGGER.info("QUEST_SOLO_CLIENT_PASS recover={} pid={}",RECOVER,ProcessHandle.current().pid());mc.stop();
            }
        } catch(Exception failure) {
            String failedStage=stage;stage="FAILED";LegacyOfTheForce.LOGGER.error("QUEST_SOLO_CLIENT_FAILED stage={} ticks={}",failedStage,ticks,failure);
            try { var proof=new CompoundTag();proof.putString("error",failure.toString());AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/quest-solo-client-failed.nbt"),proof,16384); }
            catch(Exception ignored) {} mc.stop();
        }
    }
    private static void command(Minecraft mc) { mc.getConnection().sendCommand("lotf journal");commands++; }
    private static void capture(Minecraft mc,String name) {
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->LegacyOfTheForce.LOGGER.info("QUEST_SOLO_CAPTURE {}",name));
    }
    private static ListTag lines(QuestJournalMenu menu) {
        var lines=new ListTag();for(var line:menu.lines()) { var tag=new CompoundTag();tag.putString("key",line.key());tag.putInt("status",line.status());lines.add(tag); }return lines;
    }
    private static void verifyKnown(QuestJournalMenu menu) {
        var steps=menu.lines().stream().filter(l->l.status()>0).toList();
        require(steps.size()==3&&steps.get(0).status()==2&&steps.get(1).status()==1&&steps.get(2).status()==1,"S2C journal must show read=true, visit=false, crystal=false");
        require(menu.lines().stream().anyMatch(l->l.key().equals("quest.legacyoftheforce.echo.clue")),"Guide clue must be present in native menu payload");
    }
    private static BlockPos findSource(Minecraft mc) {
        var base=mc.player.blockPosition();
        for(var pos:BlockPos.betweenClosed(base.offset(-5,-3,-5),base.offset(5,3,5))) if(mc.level.getBlockState(pos).is(QuestContent.HOLOCRON.get())) return pos.immutable();return null;
    }
}
