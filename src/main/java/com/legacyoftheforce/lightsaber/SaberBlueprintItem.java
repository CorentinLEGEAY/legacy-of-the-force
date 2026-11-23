package com.legacyoftheforce.lightsaber;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

public final class SaberBlueprintItem extends Item {
    public SaberBlueprintItem(Properties properties) {super(properties);}
    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){
        var stack=player.getItemInHand(hand);if(level.isClientSide())return net.minecraft.world.InteractionResultHolder.success(stack);
        if(!(player instanceof net.minecraft.server.level.ServerPlayer actor)||!com.legacyoftheforce.core.api.PlayerInventoryHooks.ready(actor)||actor.getCooldowns().isOnCooldown(this))return net.minecraft.world.InteractionResultHolder.fail(stack);
        actor.getCooldowns().addCooldown(this,SaberConfig.ACTIVATION_INTERVAL.get());
        var plan=stack.get(SaberContent.BLUEPRINT_DATA.get());if(plan==null||plan.design().isEmpty()||!plan.design().get().compatible(SaberCatalog.parts()))return net.minecraft.world.InteractionResultHolder.fail(stack);
        var knowledge=actor.getData(SaberContent.KNOWLEDGE);if(knowledge.knows(plan.design().get().type()))return net.minecraft.world.InteractionResultHolder.success(stack);
        if(!knowledge.learn(plan.design().get().type()))return net.minecraft.world.InteractionResultHolder.fail(stack);
        try{new com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier().persist(actor);}catch(java.io.IOException|RuntimeException failure){com.legacyoftheforce.LegacyOfTheForce.LOGGER.error("Saber knowledge save uncertain",failure);actor.connection.disconnect(Component.translatable("saber.legacyoftheforce.menu_recovery"));return net.minecraft.world.InteractionResultHolder.fail(stack);}
        actor.sendSystemMessage(Component.translatable("workshop.legacyoftheforce.type_learned",Component.translatable("saber.legacyoftheforce.type_"+plan.design().get().type().key())));return net.minecraft.world.InteractionResultHolder.success(stack);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags) {
        var data=stack.get(SaberContent.BLUEPRINT_DATA.get());
        if(data==null) {lines.add(Component.translatable("workshop.legacyoftheforce.blueprint_empty"));return;}
        var parts=data.parts();if(parts.isEmpty()) {lines.add(Component.translatable("saber.legacyoftheforce.invalid"));return;}
        lines.add(Component.translatable("workshop.legacyoftheforce.blueprint_help"));
        data.design().ifPresent(design->lines.add(Component.translatable("saber.legacyoftheforce.type_"+design.type().key())));
        for(var slot:SaberSlot.values()) {var id=parts.get().get(slot);lines.add(Component.translatable("slot.legacyoftheforce."+slot.key()).append(": ").append(Component.translatable("item."+id.getNamespace()+"."+id.getPath())));}
    }
}
