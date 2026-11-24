package com.legacyoftheforce.planets;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/** Giant trees and clearings authored within one chunk; no cross-chunk writes or login rebuild. */
public final class EndorCanopyFeature extends Feature<EndorCanopyFeature.Settings> {
    public record Settings(int treeChance, int minimumHeight, int maximumHeight, int fernAttempts) implements FeatureConfiguration {
        static final Codec<Settings> CODEC = RecordCodecBuilder.<Settings>create(instance -> instance.group(
                Codec.intRange(1, 16).fieldOf("tree_chance").forGetter(Settings::treeChance),
                Codec.intRange(16, 36).fieldOf("minimum_height").forGetter(Settings::minimumHeight),
                Codec.intRange(16, 36).fieldOf("maximum_height").forGetter(Settings::maximumHeight),
                Codec.intRange(0, 32).fieldOf("fern_attempts").forGetter(Settings::fernAttempts)).apply(instance, Settings::new))
                .validate(value -> value.maximumHeight >= value.minimumHeight ? DataResult.success(value) : DataResult.error(() -> "Invalid canopy height range"));
    }
    public EndorCanopyFeature() { super(Settings.CODEC); }
    @Override public boolean place(FeaturePlaceContext<Settings> context) {
        var world = context.level(); var chunk = new ChunkPos(context.origin()); var random = context.random();
        if (chunk.x == 0 && chunk.z == 0) {
            int y = Math.clamp(world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 8, 8), 64, 160);
            for (int x = 3; x <= 13; x++) for (int z = 3; z <= 13; z++) {
                int surface = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                for (int dy = Math.min(surface - 2, y - 3); dy <= y; dy++) world.setBlock(new BlockPos(x, dy, z), (dy == y ? Blocks.MOSS_BLOCK : Blocks.STONE).defaultBlockState(), 2);
                for (int dy = y + 1; dy < Math.max(y + 5, surface + 1); dy++) world.setBlock(new BlockPos(x, dy, z), Blocks.AIR.defaultBlockState(), 2);
            }
            for (int x : new int[]{3, 13}) for (int z : new int[]{3, 13}) world.setBlock(new BlockPos(x, y + 1, z), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
            return true;
        }
        int cx = chunk.getMinBlockX() + 7, cz = chunk.getMinBlockZ() + 7;
        int base = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz);
        int low = base; boolean natural = true;
        for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) {
            int column = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx + dx, cz + dz);
            base = Math.max(base, column); low = Math.min(low, column);
            if (!world.getBlockState(new BlockPos(cx + dx, column - 1, cz + dz)).is(Blocks.GRASS_BLOCK)) natural = false;
        }
        int height = context.config().minimumHeight() + random.nextInt(context.config().maximumHeight() - context.config().minimumHeight() + 1);
        if (natural && base - low <= 2 && base > 55 && base + height + 4 < world.getMaxBuildHeight() && random.nextInt(context.config().treeChance()) == 0) {
            boolean clear = true;
            for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) for (int y = 0; y < height; y++)
                if (!world.getBlockState(new BlockPos(cx + dx, base + y, cz + dz)).canBeReplaced()) clear = false;
            if (clear) {
                for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) for (int y = low - 1; y < base; y++)
                    world.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.SPRUCE_LOG.defaultBlockState(), 2);
                for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) for (int y = 0; y < height; y++)
                    world.setBlock(new BlockPos(cx + dx, base + y, cz + dz), Blocks.SPRUCE_LOG.defaultBlockState(), 2);
                for (int y = height - 5; y <= height + 3; y++) for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
                    int radius = y < height - 2 ? 5 : y < height + 1 ? 7 : 4;
                    var pos = new BlockPos(cx + dx, base + y, cz + dz);
                    if (dx * dx + dz * dz <= radius * radius && world.isEmptyBlock(pos))
                        world.setBlock(pos, Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(BlockStateProperties.DISTANCE, 1), 2);
                }
                for (int sign : new int[]{-1, 1}) for (int length = 1; length <= 5; length++) {
                    var branch = new BlockPos(cx + sign * length, base + height - 4, cz);
                    world.setBlock(branch, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), 2);
                }
                for (int length = 1; length <= 4; length++) {
                    var root = new BlockPos(cx, base, cz + length);
                    if (world.isEmptyBlock(root)) world.setBlock(root, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z), 2);
                }
            }
        }
        for (int n = 0; n < context.config().fernAttempts(); n++) {
            int x = chunk.getMinBlockX() + 1 + random.nextInt(14), z = chunk.getMinBlockZ() + 1 + random.nextInt(14);
            int floorY = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            // WG heightmaps include our crown. Look through leaves rather than relying on a not-yet-primed runtime heightmap.
            for (int scan = 0; scan < 44 && floorY > world.getMinBuildHeight() + 1; scan++, floorY--) {
                var state = world.getBlockState(new BlockPos(x, floorY, z));
                if (state.isAir() || state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.LOGS)) continue;
                break;
            }
            var at = new BlockPos(x, floorY + 1, z);
            var plant = (n % 3 == 0 ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState();
            if (world.isEmptyBlock(at) && plant.canSurvive(world, at)) world.setBlock(at, plant, 2);
        }
        return true;
    }
}
