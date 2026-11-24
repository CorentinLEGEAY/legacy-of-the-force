package com.legacyoftheforce.planets;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/** One-time calcite/sandstone formations and a stable arrival terrace, within the current chunk. */
public final class JedhaLandscapeFeature extends Feature<JedhaLandscapeFeature.Settings> {
    public record Settings(int formationChance, int maximumHeight) implements FeatureConfiguration {
        static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 64).fieldOf("formation_chance").forGetter(Settings::formationChance),
                Codec.intRange(4, 14).fieldOf("maximum_height").forGetter(Settings::maximumHeight)).apply(instance, Settings::new));
    }
    public JedhaLandscapeFeature() { super(Settings.CODEC); }
    @Override public boolean place(FeaturePlaceContext<Settings> context) {
        var world = context.level(); var chunk = new ChunkPos(context.origin()); var random = context.random();
        if (chunk.x == 0 && chunk.z == 0) {
            int y = Math.clamp(world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 8, 8), 64, 180);
            for (int x = 2; x <= 14; x++) for (int z = 2; z <= 14; z++) {
                int surface = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                for (int dy = Math.min(surface - 2, y - 3); dy <= y; dy++) world.setBlock(new BlockPos(x, dy, z), Blocks.SMOOTH_SANDSTONE.defaultBlockState(), 2);
                for (int dy = y + 1; dy < Math.max(y + 6, surface + 1); dy++) world.setBlock(new BlockPos(x, dy, z), Blocks.AIR.defaultBlockState(), 2);
            }
            for (int x : new int[]{2, 14}) for (int z : new int[]{2, 14}) world.setBlock(new BlockPos(x, y + 1, z), Blocks.CHISELED_SANDSTONE.defaultBlockState(), 2);
            return true;
        }
        int x = chunk.getMinBlockX() + 8, z = chunk.getMinBlockZ() + 8, y = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, chunk.getMinBlockX() + 8, chunk.getMinBlockZ() + 8);
        if (!world.getBlockState(new BlockPos(x, y - 1, z)).is(Blocks.SAND) || random.nextInt(context.config().formationChance()) != 0) return true;
        int height = 4 + random.nextInt(context.config().maximumHeight() - 3);
        for (int dy = 0; dy < height; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            var at = new BlockPos(x + dx, y + dy, z + dz);
            int radius = dy < height - 3 ? 3 : 1;
            if (dx * dx + dz * dz <= radius * radius && world.isEmptyBlock(at)) world.setBlock(at, (dy % 4 == 0 ? Blocks.CALCITE : Blocks.SANDSTONE).defaultBlockState(), 2);
        }
        return true;
    }
}
