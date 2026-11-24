package com.legacyoftheforce.quests;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Persistent optional guide, never a disguised boss or source of combat rewards. */
public final class AdventurePersonEntity extends PathfinderMob {
    private final AdventureRole role;
    public AdventurePersonEntity(EntityType<? extends PathfinderMob> type,Level level,AdventureRole role){super(type,level);this.role=role;setPersistenceRequired();setInvulnerable(true);}
    public AdventureRole role(){return role;}
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,.18).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(1,new LookAtPlayerGoal(this,Player.class,6));goalSelector.addGoal(2,new RandomLookAroundGoal(this));}
    @Override protected Component getTypeName(){return Component.translatable("people.legacyoftheforce.role."+role.key());}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canBeLeashed(){return false;}
    @Override protected boolean shouldDropLoot(){return false;}
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand){
        if(level().isClientSide())return InteractionResult.SUCCESS;
        if(player instanceof ServerPlayer actor&&hand==InteractionHand.MAIN_HAND&&PeopleService.open(actor,this))return InteractionResult.CONSUME;
        return InteractionResult.PASS;
    }
}
