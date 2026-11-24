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

/** Bounded mangroves, roots and water plants; initial generation only. */
public final class DagobahLandscapeFeature extends Feature<DagobahLandscapeFeature.Settings> {
    public record Settings(int treeChance, int minimumHeight, int maximumHeight, int plantAttempts) implements FeatureConfiguration {
        static final Codec<Settings> CODEC = RecordCodecBuilder.<Settings>create(instance -> instance.group(
                Codec.intRange(1, 16).fieldOf("tree_chance").forGetter(Settings::treeChance),
                Codec.intRange(6, 18).fieldOf("minimum_height").forGetter(Settings::minimumHeight),
                Codec.intRange(6, 18).fieldOf("maximum_height").forGetter(Settings::maximumHeight),
                Codec.intRange(0, 32).fieldOf("plant_attempts").forGetter(Settings::plantAttempts)).apply(instance, Settings::new))
                .validate(value -> value.maximumHeight >= value.minimumHeight ? DataResult.success(value) : DataResult.error(() -> "Invalid mangrove height range"));
    }
    public DagobahLandscapeFeature() { super(Settings.CODEC); }
    @Override public boolean place(FeaturePlaceContext<Settings> context) {
        var world = context.level(); var chunk = new ChunkPos(context.origin()); var random = context.random();
        if (chunk.x == 0 && chunk.z == 0) {
            int y = Math.clamp(world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 8, 8), 65, 120);
            for (int x = 2; x <= 14; x++) for (int z = 2; z <= 14; z++) {
                int surface = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                for (int dy = Math.min(surface - 2, y - 4); dy <= y; dy++) world.setBlock(new BlockPos(x, dy, z), (dy == y ? Blocks.MOSS_BLOCK : Blocks.MUD_BRICKS).defaultBlockState(), 2);
                for (int dy = y + 1; dy < Math.max(y + 6, surface + 1); dy++) world.setBlock(new BlockPos(x, dy, z), Blocks.AIR.defaultBlockState(), 2);
            }
            for (int x : new int[]{2, 14}) for (int z : new int[]{2, 14}) world.setBlock(new BlockPos(x, y + 1, z), Blocks.MANGROVE_LOG.defaultBlockState(), 2);
            return true;
        }
        int x = chunk.getMinBlockX() + 8, z = chunk.getMinBlockZ() + 8;
        int base = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        int rootY = base - 1;
        for (int depth = 0; depth < 4 && world.getBlockState(new BlockPos(x, rootY, z)).is(Blocks.WATER); depth++) rootY--;
        var soil = world.getBlockState(new BlockPos(x, rootY, z));
        if ((soil.is(Blocks.MUD) || soil.is(Blocks.GRASS_BLOCK)) && base >= 63 && random.nextInt(context.config().treeChance()) == 0) {
            int height = context.config().minimumHeight() + random.nextInt(context.config().maximumHeight() - context.config().minimumHeight() + 1);
            boolean clear = true;
            for (int y = 1; y <= height; y++) if (!world.getBlockState(new BlockPos(x, base + y, z)).canBeReplaced()) clear = false;
            if (clear) {
                for (int y = rootY + 1; y < base; y++) world.setBlock(new BlockPos(x, y, z), Blocks.MANGROVE_ROOTS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true), 2);
                for (int y = 0; y <= height; y++) world.setBlock(new BlockPos(x, base + y, z), Blocks.MANGROVE_LOG.defaultBlockState(), 2);
                for (int y = height - 3; y <= height + 2; y++) for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                    int radius = y == height + 2 ? 3 : 5; var at = new BlockPos(x + dx, base + y, z + dz);
                    if (dx * dx + dz * dz <= radius * radius && world.isEmptyBlock(at)) world.setBlock(at, Blocks.MANGROVE_LEAVES.defaultBlockState().setValue(BlockStateProperties.DISTANCE, 1), 2);
                }
                for (var direction : Direction.Plane.HORIZONTAL) for (int length = 1; length <= 4; length++) {
                    var at = new BlockPos(x, base + Math.max(0, 2 - length), z).relative(direction, length);
                    if (world.isEmptyBlock(at)) world.setBlock(at, Blocks.MANGROVE_ROOTS.defaultBlockState(), 2);
                }
            }
        }
        for (int n = 0; n < context.config().plantAttempts(); n++) {
            int px = chunk.getMinBlockX() + 1 + random.nextInt(14), pz = chunk.getMinBlockZ() + 1 + random.nextInt(14);
            int floorY = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, px, pz) - 1;
            for (int scan = 0; scan < 24 && floorY > world.getMinBuildHeight() + 1; scan++, floorY--) {
                var state = world.getBlockState(new BlockPos(px, floorY, pz));
                if (state.isAir() || state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(Blocks.MANGROVE_ROOTS)) continue;
                break;
            }
            var at = new BlockPos(px, floorY + 1, pz);
            var plant = (world.getBlockState(at.below()).is(Blocks.WATER) ? Blocks.LILY_PAD : Blocks.FERN).defaultBlockState();
            if (world.isEmptyBlock(at) && plant.canSurvive(world, at)) world.setBlock(at, plant, 2);
        }
        return true;
    }
}
