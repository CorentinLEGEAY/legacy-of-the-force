package com.legacyoftheforce.core.api;
import java.util.*;
import java.util.function.BiFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
public final class AdventurePeople {
    private static BiFunction<ServerLevel,String,Mob> factory=(level,role)->null;
    public static void install(BiFunction<ServerLevel,String,Mob> value){factory=Objects.requireNonNull(value);}
    public static Optional<Mob> create(ServerLevel level,String role){return Optional.ofNullable(factory.apply(level,role));}
    public static void place(net.minecraft.world.level.WorldGenLevel world,net.minecraft.world.level.levelgen.structure.BoundingBox clip,net.minecraft.core.BlockPos feet,String role){
        if(!clip.isInside(feet)||!clip.isInside(feet.above())||!world.hasChunkAt(feet)||!world.hasChunkAt(feet.below())
                ||!world.getBlockState(feet).getCollisionShape(world,feet).isEmpty()||!world.getBlockState(feet.above()).getCollisionShape(world,feet.above()).isEmpty()
                ||!world.getFluidState(feet).isEmpty()||!world.getBlockState(feet.below()).isFaceSturdy(world,feet.below(),net.minecraft.core.Direction.UP))return;
        create(world.getLevel(),role).ifPresent(person->{
            String key=world.getLevel().dimension().location()+"/person/"+feet.asLong()+"/"+role;
            person.setUUID(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            person.moveTo(feet.getX()+.5,feet.getY(),feet.getZ()+.5,180,0);person.setPersistenceRequired();world.addFreshEntity(person);
        });
    }
    private AdventurePeople(){}
}
