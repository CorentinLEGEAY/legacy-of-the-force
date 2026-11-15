package com.legacyoftheforce.planets;

import com.legacyoftheforce.core.api.ProtectedWorldService;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** Bounded loaded-chunk search. Never replaces a block or generates a free terminal/platform. */
final class SafeArrival {
    static Optional<TravelPoint> find(ServerPlayer actor, TravelPoint anchor, boolean surface, ProtectedWorldService protection) {
        var level = actor.server.getLevel(ResourceKey.create(Registries.DIMENSION, anchor.dimension())); if (level == null) return Optional.empty();
        int radius = TravelConfig.SEARCH_RADIUS.get(); int cx = net.minecraft.util.Mth.floor(anchor.x()), cz = net.minecraft.util.Mth.floor(anchor.z());
        for (int ring = 0; ring <= radius; ring++) for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
            int x = cx + dx, z = cz + dz; var block = new BlockPos(x, level.getMinBuildHeight(), z);
            if (!level.getWorldBorder().isWithinBounds(block)) continue;
            var chunk = new net.minecraft.world.level.ChunkPos(block); level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunk, 2, actor.getId()); level.getChunk(chunk.x, chunk.z);
            if (!surface && ring == 0 && safe(actor, level, anchor, protection)) return Optional.of(anchor);
            int top = surface ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1 : net.minecraft.util.Mth.floor(anchor.y()) + TravelConfig.VERTICAL_SEARCH.get();
            int bottom = surface ? top - 5 : net.minecraft.util.Mth.floor(anchor.y()) - TravelConfig.VERTICAL_SEARCH.get();
            for (int y = top; y >= bottom; y--) {
                var point = new TravelPoint(anchor.dimension(), x + .5, y, z + .5, anchor.yaw(), anchor.pitch());
                if (safe(actor, level, point, protection)) return Optional.of(point);
            }
        }
        return Optional.empty();
    }
    static boolean safe(ServerPlayer actor, ServerLevel level, TravelPoint point, ProtectedWorldService protection) {
        var dimensions = actor.getDimensions(net.minecraft.world.entity.Pose.STANDING);
        if (!level.dimension().location().equals(point.dimension()) || point.y() <= level.getMinBuildHeight() + 1 || point.y() + dimensions.height() >= level.getMaxBuildHeight()) return false;
        double half = dimensions.width() / 2.0; var box = new AABB(point.x() - half, point.y(), point.z() - half, point.x() + half, point.y() + dimensions.height(), point.z() + half);
        if (!level.getWorldBorder().isWithinBounds(box)) return false;
        var touched = BlockPos.betweenClosedStream(box.inflate(.1, .1, .1)).iterator();
        while (touched.hasNext()) {
            var pos = touched.next();
            if (!level.hasChunkAt(pos) || !protection.mayArrive(actor, level, pos)) return false;
            var state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK)
                    || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)
                    || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)) return false;
        }
        if (!level.getEntities(actor, box, entity -> entity.isAlive() && !entity.isSpectator()
                && (entity instanceof net.minecraft.world.entity.LivingEntity || entity.canBeCollidedWith())).isEmpty()) return false;
        return level.noCollision(actor, box) && level.getBlockCollisions(actor, box.move(0, -.05, 0)).iterator().hasNext();
    }
    static boolean inside(TravelPoint point, TravelPoint anchor) {
        int radius = TravelConfig.SEARCH_RADIUS.get() + 1;
        return point.dimension().equals(anchor.dimension()) && Math.abs(point.x() - anchor.x()) <= radius && Math.abs(point.z() - anchor.z()) <= radius;
    }
    private SafeArrival() {}
}
