package com.legacyoftheforce.force;

import java.util.Optional;
import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.config.ServerConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Shared tier rules for new selections. Recovery always uses the mass sealed in its journal. */
final class ForceRules {
    record BlockMass(double mass,int required) {}
    private static final TagKey<Block> VERY_LIGHT=tag("force_very_light_blocks"),LIGHT=tag("force_light_blocks"),MEDIUM=tag("force_stone_blocks"),
            HEAVY=tag("force_heavy_blocks"),EXTREME=tag("force_extreme_blocks");
    private static TagKey<Block> tag(String key){return TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("legacyoftheforce",key));}
    static int level(PlayerProgressService progress,ServerPlayer actor){return progress.get(actor.getUUID()).filter(p->!p.unavailable()).map(PlayerProgressSnapshot::level).orElse(0);}
    static int capacity(int level) {return Math.min(tierCapacity(level),ServerConfig.MAX_BLOCKS_PER_PLAYER.get());}
    private static int tierCapacity(int level) {
        if(level<ForceConfig.BLOCK_LEVEL.get())return 0;
        int result=1;var levels=ForceConfig.GROUP_LEVELS.get();var counts=ForceConfig.GROUP_COUNTS.get();
        for(int i=0;i<Math.min(levels.size(),counts.size());i++)if(level>=levels.get(i))result=Math.max(result,counts.get(i));
        return Math.min(32,result);
    }
    static double range(PlayerProgressService progress,ServerPlayer actor) {
        int level=level(progress,actor);if(level==0)return 0;
        double result=ForceConfig.LOW_LEVEL_RANGE.get();var levels=ForceConfig.GROUP_LEVELS.get();var ranges=ForceConfig.GROUP_RANGES.get();
        for(int i=0;i<Math.min(levels.size(),ranges.size());i++)if(level>=levels.get(i))result=Math.max(result,ranges.get(i));
        // The old default6 described the prototype. Preserve non-default operator caps, including shorter ones.
        double cap=ForceConfig.TIER_RANGE_CAP.get(),legacy=ForceConfig.RANGE.get();
        if(Math.abs(legacy-6)>.000001)cap=Math.min(cap,legacy);
        return Double.isFinite(cap)&&Double.isFinite(result)?Math.clamp(Math.min(result,cap),2,24):0;
    }
    static Optional<BlockMass> mass(BlockState state) {
        if(state.isAir()||state.hasBlockEntity()||!state.getFluidState().isEmpty())return Optional.empty();
        // Overlapping datapack tags select the more restrictive category.
        double mass;int required=ForceConfig.BLOCK_LEVEL.get();
        if(state.is(EXTREME)){mass=ForceConfig.EXTREME_MASS.get();required=Math.max(required,ForceConfig.EXTREME_LEVEL.get());}
        else if(state.is(HEAVY)){mass=ForceConfig.HEAVY_MASS.get();required=Math.max(required,ForceConfig.HEAVY_LEVEL.get());}
        else if(state.is(MEDIUM)){mass=ForceConfig.STONE_MASS.get();required=Math.max(required,ForceConfig.STONE_LEVEL.get());}
        else if(state.is(LIGHT))mass=ForceConfig.LIGHT_MASS.get();
        else if(state.is(VERY_LIGHT))mass=ForceConfig.VERY_LIGHT_MASS.get();else return Optional.empty();
        return Double.isFinite(mass)&&mass>0&&mass<=10?Optional.of(new BlockMass(mass,required)):Optional.empty();
    }
    static double massBudget(int level){return tierCapacity(level)*ForceConfig.LIGHT_MASS.get();}
    static double distanceCost(ServerPlayer actor,Vec3 target) {
        double distance=actor.getEyePosition().distanceTo(target);
        if(!Double.isFinite(distance))return Double.POSITIVE_INFINITY;
        return Math.min(ForceConfig.DISTANCE_COST_CAP.get(),1+Math.max(0,distance-ForceConfig.DISTANCE_REFERENCE.get())*ForceConfig.DISTANCE_COST.get());
    }
    static double moveStep(double mass){return ForceConfig.MOVE_STEP.get()/Math.pow(Math.max(1,mass),ForceConfig.MASS_SLOWDOWN.get());}
    private ForceRules(){}
}
