package com.legacyoftheforce.planets;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/** Initial decoration only; bounded to its own chunk, never invoked on login or arrival. */
public final class TatooineLandscapeFeature extends Feature<TatooineLandscapeFeature.Settings> {
    public record Settings(int spireChance, int cactusAttempts) implements FeatureConfiguration {
        static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 256).fieldOf("spire_chance").forGetter(Settings::spireChance),
                Codec.intRange(0, 12).fieldOf("cactus_attempts").forGetter(Settings::cactusAttempts)).apply(instance, Settings::new));
    }
    public TatooineLandscapeFeature() { super(Settings.CODEC); }
    @Override public boolean place(FeaturePlaceContext<Settings> context) {
        var world = context.level(); var chunk = new ChunkPos(context.origin()); var random = context.random();
        // Arrival is a natural sandstone terrace, not a replenished machine or a login repair.
        if (chunk.x == 0 && chunk.z == 0) {
            int y = Math.clamp(world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 8, 8), 64, 180);
            for (int x = 3; x <= 13; x++) for (int z = 3; z <= 13; z++) {
                int surface = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                for (int dy = Math.min(surface - 2, y - 3); dy <= y; dy++) world.setBlock(new BlockPos(x, dy, z), Blocks.SANDSTONE.defaultBlockState(), 2);
                for (int dy = y + 1; dy < Math.max(y + 5, surface + 1); dy++) world.setBlock(new BlockPos(x, dy, z), Blocks.AIR.defaultBlockState(), 2);
            }
            for (int x : new int[]{3, 13}) for (int z : new int[]{3, 13})
                world.setBlock(new BlockPos(x, y + 1, z), Blocks.CHISELED_SANDSTONE.defaultBlockState(), 2);
            return true;
        }
        if (random.nextInt(context.config().spireChance()) == 0) {
            int x = chunk.getMinBlockX() + 8, z = chunk.getMinBlockZ() + 8;
            int y = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), height = 6 + random.nextInt(9);
            if (world.getBlockState(new BlockPos(x, y - 1, z)).is(Blocks.SAND))
            for (int dy = 0; dy < height; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                int radius = dy < height / 2 ? 3 : dy < height - 2 ? 2 : 1;
                if (dx * dx + dz * dz <= radius * radius && world.isEmptyBlock(new BlockPos(x + dx, y + dy, z + dz)))
                    world.setBlock(new BlockPos(x + dx, y + dy, z + dz), (dy % 5 == 0 ? Blocks.RED_SANDSTONE : Blocks.TERRACOTTA).defaultBlockState(), 2);
            }
        }
        for (int n = 0; n < context.config().cactusAttempts(); n++) {
            int x = chunk.getMinBlockX() + 2 + random.nextInt(12), z = chunk.getMinBlockZ() + 2 + random.nextInt(12);
            var pos = new BlockPos(x, world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
            int height = 1 + random.nextInt(3);
            for (int y = 0; y < height; y++) {
                var at = pos.above(y); var cactus = Blocks.CACTUS.defaultBlockState();
                if (world.isEmptyBlock(at) && cactus.canSurvive(world, at)) world.setBlock(at, cactus, 2); else break;
            }
        }
        return true;
    }
}
