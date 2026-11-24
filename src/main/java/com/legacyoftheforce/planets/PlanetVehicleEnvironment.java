package com.legacyoftheforce.planets;

import com.legacyoftheforce.core.api.VehicleEnvironmentHooks.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;

/** Bounded loaded-world sample, once per second per vehicle; one mild penalty, never a stack. */
public final class PlanetVehicleEnvironment {
    public static Effect inspect(Sample sample){
        var level=sample.level(); var at=sample.at();
        if(!TravelConfig.VEHICLE_ENVIRONMENT.get())return Effect.CLEAR;
        boolean ilum=level.dimension().equals(PlanetId.ILUM.dimension());
        boolean desert=level.dimension().equals(PlanetId.TATOOINE.dimension());
        boolean swamp=level.dimension().equals(PlanetId.DAGOBAH.dimension());
        boolean volcanic=level.dimension().equals(PlanetId.MUSTAFAR.dimension());
        boolean planet=ilum||desert||swamp||volcanic||level.dimension().equals(PlanetId.ENDOR.dimension())||level.dimension().equals(PlanetId.JEDHA.dimension());
        if(!planet)return Effect.CLEAR;
        Kind kind=Kind.NONE;double speed=1;
        // A same-column support search never asks for an absent chunk.
        for(int depth=0;depth<=6;depth++){
            BlockPos below=at.below(depth); if(level.isOutsideBuildHeight(below))break;
            var block=level.getBlockState(below);
            if(ilum&&(block.is(Blocks.SNOW)||block.is(Blocks.SNOW_BLOCK)||block.is(Blocks.POWDER_SNOW)||block.is(BlockTags.ICE))){kind=Kind.SNOW;speed=TravelConfig.SNOW_SPEED.get();break;}
            if(desert&&block.is(BlockTags.SAND)){kind=Kind.SAND;speed=TravelConfig.SAND_SPEED.get();break;}
            if(swamp&&block.is(Blocks.MUD)){kind=Kind.MUD;speed=TravelConfig.MUD_SPEED.get();break;}
            if(!block.getCollisionShape(level,below).isEmpty())break;
        }
        if(volcanic&&nearLava(sample)){kind=Kind.HEAT;speed=TravelConfig.HEAT_SPEED.get();}
        // Weather is server-owned; roofs protect, native visibility effects remain independent.
        if(level.isRaining()&&level.canSeeSky(at)&&TravelConfig.STORM_SPEED.get()<speed){kind=Kind.STORM;speed=TravelConfig.STORM_SPEED.get();}
        if(kind==Kind.NONE)return Effect.CLEAR;
        var protection=TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("legacyoftheforce","speeder_environment/"+(kind==Kind.STORM&&ilum?"snow":kind.name().toLowerCase(java.util.Locale.ROOT))));
        double remaining=sample.workingParts().stream().anyMatch(id->BuiltInRegistries.ITEM.get(id).builtInRegistryHolder().is(protection))?TravelConfig.ADAPTED_PENALTY.get():1;
        return new Effect(kind,1-(1-speed)*remaining,1+(TravelConfig.ENVIRONMENT_ENERGY.get()-1)*remaining);
    }
    private static boolean nearLava(Sample sample){
        var level=sample.level(); var at=sample.at();
        for(int x:new int[]{-2,2})for(int z:new int[]{-2,2})if(!level.hasChunkAt(at.offset(x,0,z)))return false;
        var eye=Vec3.atCenterOf(at);
        for(var pos:BlockPos.betweenClosed(at.offset(-2,-3,-2),at.offset(2,1,2))){
            if(level.isOutsideBuildHeight(pos)||!level.getFluidState(pos).is(FluidTags.LAVA))continue;
            var hit=level.clip(new ClipContext(Vec3.atCenterOf(pos),eye,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if(hit.getType()==HitResult.Type.MISS)return true;
        }
        return false;
    }
    private PlanetVehicleEnvironment(){}
}
