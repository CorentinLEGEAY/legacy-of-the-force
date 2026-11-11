package com.legacyoftheforce.force;

import java.io.IOException;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

record BlockMoveRecord(UUID id, UUID actor, ResourceKey<Level> dimension, BlockPos source,
        BlockPos destination, BlockState original, double mass) {
    CompoundTag payload() {
        var tag = new CompoundTag();
        tag.putInt("schemaVersion", 1); tag.putUUID("actor", actor);
        tag.putString("dimension", dimension.location().toString()); tag.putLong("source", source.asLong());
        if (destination != null) tag.putLong("destination", destination.asLong());
        tag.put("original", NbtUtils.writeBlockState(original)); tag.putDouble("mass", mass);
        return tag;
    }
    BlockMoveRecord withDestination(BlockPos position) {
        return new BlockMoveRecord(id, actor, dimension, source, position.immutable(), original, mass);
    }
    ServerLevel level(MinecraftServer server) throws IOException {
        var level = server.getLevel(dimension);
        if (level == null) throw new IOException("Operation dimension unavailable: " + dimension.location());
        return level;
    }
    static BlockMoveRecord read(UUID id, CompoundTag tag, MinecraftServer server) throws IOException {
        if (tag.getInt("schemaVersion") != 1 || !tag.hasUUID("actor") || !tag.contains("source", Tag.TAG_LONG)
                || !tag.contains("original", Tag.TAG_COMPOUND)) throw new IOException("Unsupported block operation");
        var location = ResourceLocation.tryParse(tag.getString("dimension"));
        if (location == null) throw new IOException("Invalid dimension");
        var key = ResourceKey.create(Registries.DIMENSION, location);
        var level = server.getLevel(key);
        if (level == null) throw new IOException("Missing dimension");
        var stateTag = tag.getCompound("original");
        var blockId = ResourceLocation.tryParse(stateTag.getString("Name"));
        if (blockId == null || !BuiltInRegistries.BLOCK.containsKey(blockId)) throw new IOException("Unknown original block");
        var state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), stateTag);
        double mass = tag.getDouble("mass");
        if (!NbtUtils.writeBlockState(state).equals(stateTag) || state.isAir() || state.hasBlockEntity()
                || !Double.isFinite(mass) || mass <= 0 || mass > 10) throw new IOException("Invalid original block state");
        var source = BlockPos.of(tag.getLong("source"));
        var destination = tag.contains("destination", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("destination")) : null;
        if (!level.isInWorldBounds(source) || (destination != null && (!level.isInWorldBounds(destination) || destination.equals(source)))) {
            throw new IOException("Invalid operation positions");
        }
        return new BlockMoveRecord(id, tag.getUUID("actor"), key, source, destination, state, mass);
    }
}
