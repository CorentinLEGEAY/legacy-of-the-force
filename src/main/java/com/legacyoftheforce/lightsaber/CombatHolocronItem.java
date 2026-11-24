package com.legacyoftheforce.lightsaber;

import java.util.List;
import com.legacyoftheforce.core.api.PlayerInventoryHooks;
import com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** One physical teaching device and learned knowledge are committed in the same player aggregate. */
public final class CombatHolocronItem extends Item {
    private final SaberForm form;
    public CombatHolocronItem(SaberForm form,Properties properties) {super(properties);if(form==SaberForm.BASIC)throw new IllegalArgumentException("Basic form needs no holocron");this.form=form;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);if(level.isClientSide())return InteractionResultHolder.success(stack);
        if(!(player instanceof ServerPlayer actor)||!actor.server.isSameThread()||!actor.isAlive()||actor.isFakePlayer()||actor.isSpectator()
                ||actor.containerMenu!=actor.inventoryMenu||!PlayerInventoryHooks.ready(actor)||actor.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(stack);
        actor.getCooldowns().addCooldown(this,SaberConfig.FORM_INTERVAL.get());var knowledge=actor.getData(SaberContent.DISCIPLINE);
        if(knowledge.knows(form)){actor.displayClientMessage(Component.translatable("saber.legacyoftheforce.form_known"),true);return InteractionResultHolder.success(stack);}
        if(!knowledge.learn(form))return InteractionResultHolder.fail(stack);
        stack.shrink(1);actor.getInventory().setChanged();
        try{new VerifiedPlayerSaveBarrier().persist(actor);}catch(java.io.IOException|RuntimeException failure){
            com.legacyoftheforce.LegacyOfTheForce.LOGGER.error("Combat teaching save uncertain; native aggregate retained",failure);
            actor.connection.disconnect(Component.translatable("saber.legacyoftheforce.menu_recovery"));return InteractionResultHolder.fail(stack);
        }
        var id=SaberContent.id("holocron_"+form.key());
        var receipt=java.util.UUID.nameUUIDFromBytes((actor.getUUID()+":holocron:"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.legacyoftheforce.core.api.HolocronDiscovered(actor.getUUID(),receipt,id));}
        catch(RuntimeException failure){com.legacyoftheforce.LegacyOfTheForce.LOGGER.error("Combat holocron consumer failed after durable teaching; reconcile discipline knowledge",failure);}
        actor.containerMenu.broadcastChanges();actor.sendSystemMessage(Component.translatable("saber.legacyoftheforce.form_learned",Component.translatable("saber.legacyoftheforce.form_"+form.key())));
        return InteractionResultHolder.success(stack);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags) {
        lines.add(Component.translatable("saber.legacyoftheforce.form_"+form.key()));lines.add(Component.translatable("saber.legacyoftheforce.holocron_help"));lines.add(Component.translatable("saber.legacyoftheforce.form_help_"+form.key()));
    }
}
