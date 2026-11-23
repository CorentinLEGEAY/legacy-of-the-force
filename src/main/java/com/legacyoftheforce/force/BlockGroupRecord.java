package com.legacyoftheforce.force;

import java.io.IOException;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/** All members share one decision and journal entry; projections never own world blocks. */
record BlockGroupRecord(UUID id, UUID actor, ResourceKey<Level> dimension, BlockPos pivot,
        List<Member> members, BlockPos destination, int turns) {
    record Member(BlockPos source, BlockState state, double mass) {}
    BlockGroupRecord { members = List.copyOf(members); }
    double mass() { return members.stream().mapToDouble(Member::mass).sum(); }
    BlockPos offset(Member member) {
        var p = member.source().subtract(pivot);
        return switch (turns) {
            case 1 -> new BlockPos(-p.getZ(), p.getY(), p.getX());
            case 2 -> new BlockPos(-p.getX(), p.getY(), -p.getZ());
            case 3 -> new BlockPos(p.getZ(), p.getY(), -p.getX());
            default -> p;
        };
    }
    BlockState state(Member member) {
        return member.state().rotate(switch (turns) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        });
    }
    BlockGroupRecord rotated() { return new BlockGroupRecord(id, actor, dimension, pivot, members, destination, (turns + 1) % 4); }
    BlockGroupRecord staged(BlockPos target) { return new BlockGroupRecord(id, actor, dimension, pivot, members, target.immutable(), turns); }
    Set<BlockPos> sources() {
        var positions = new HashSet<BlockPos>();
        members.forEach(m -> positions.add(m.source()));
        return positions;
    }
    Set<BlockPos> positions() {
        var positions = sources();
        if (destination != null) members.forEach(m -> positions.add(destination.offset(offset(m))));
        return positions;
    }
    ServerLevel level(MinecraftServer server) throws IOException {
        var level = server.getLevel(dimension);
        if (level == null) throw new IOException("Group dimension unavailable");
        return level;
    }
    CompoundTag payload() {
        var tag = new CompoundTag();
        tag.putInt("schemaVersion", 1); tag.putUUID("actor", actor);
        tag.putString("dimension", dimension.location().toString()); tag.putLong("pivot", pivot.asLong());
        tag.putInt("turns", turns);
        if (destination != null) tag.putLong("destination", destination.asLong());
        var list = new ListTag();
        for (var member : members) {
            var item = new CompoundTag(); item.putLong("source", member.source().asLong());
            item.put("state", NbtUtils.writeBlockState(member.state())); item.putDouble("mass", member.mass()); list.add(item);
        }
        tag.put("members", list); return tag;
    }
    static BlockGroupRecord read(UUID id, CompoundTag tag, MinecraftServer server) throws IOException {
        if (tag.getInt("schemaVersion") != 1 || !tag.hasUUID("actor") || !tag.contains("pivot", Tag.TAG_LONG)
                || !tag.contains("members", Tag.TAG_LIST) || !tag.contains("turns", Tag.TAG_INT)) throw new IOException("Unsupported group journal");
        var key = ResourceLocation.tryParse(tag.getString("dimension"));
        if (key == null) throw new IOException("Invalid group dimension");
        var dimension = ResourceKey.create(Registries.DIMENSION, key);
        var level = server.getLevel(dimension);
        if (level == null) throw new IOException("Missing group dimension");
        int turns = tag.getInt("turns");
        var pivot = BlockPos.of(tag.getLong("pivot"));
        var destination = tag.contains("destination", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("destination")) : null;
        var list = tag.getList("members", Tag.TAG_COMPOUND);
        if (turns < 0 || turns > 3 || list.isEmpty() || list.size() > 32) throw new IOException("Invalid group bounds");
        var members = new ArrayList<Member>(); var seen = new HashSet<BlockPos>();
        for (int i = 0; i < list.size(); i++) {
            var item = list.getCompound(i); var source = BlockPos.of(item.getLong("source"));
            var stateTag = item.getCompound("state"); var blockId = ResourceLocation.tryParse(stateTag.getString("Name"));
            if (!item.contains("source", Tag.TAG_LONG) || blockId == null || !BuiltInRegistries.BLOCK.containsKey(blockId)) throw new IOException("Invalid group member");
            var state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), stateTag);
            double mass = item.getDouble("mass");
            if (!seen.add(source) || !level.isInWorldBounds(source) || source.distManhattan(pivot) > 24
                    || state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                    || !NbtUtils.writeBlockState(state).equals(stateTag) || !Double.isFinite(mass) || mass <= 0 || mass > 10) throw new IOException("Invalid group member state");
            members.add(new Member(source, state, mass));
        }
        if (!seen.contains(pivot)) throw new IOException("Group pivot missing");
        var result = new BlockGroupRecord(id, tag.getUUID("actor"), dimension, pivot, members, destination, turns);
        if (destination != null) for (var member : members) {
            var pos = destination.offset(result.offset(member));
            if (!level.isInWorldBounds(pos) || seen.contains(pos)) throw new IOException("Overlapping group endpoints");
        }
        return result;
    }
}
