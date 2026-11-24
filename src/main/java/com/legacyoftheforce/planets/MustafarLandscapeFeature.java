package com.legacyoftheforce.planets;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/** Initial basalt cones and vents. Lava belongs to worldgen; encounter hazards never place fluids. */
public final class MustafarLandscapeFeature extends Feature<MustafarLandscapeFeature.Settings> {
    public record Settings(int volcanoChance, int minimumHeight, int maximumHeight) implements FeatureConfiguration {
        static final Codec<Settings> CODEC = RecordCodecBuilder.<Settings>create(instance -> instance.group(
                Codec.intRange(1, 64).fieldOf("volcano_chance").forGetter(Settings::volcanoChance),
                Codec.intRange(5, 16).fieldOf("minimum_height").forGetter(Settings::minimumHeight),
                Codec.intRange(5, 16).fieldOf("maximum_height").forGetter(Settings::maximumHeight)).apply(instance, Settings::new))
                .validate(value -> value.maximumHeight >= value.minimumHeight ? DataResult.success(value) : DataResult.error(() -> "Invalid volcanic height range"));
    }
    public MustafarLandscapeFeature() { super(Settings.CODEC); }
    @Override public boolean place(FeaturePlaceContext<Settings> context) {
        var world = context.level(); var chunk = new ChunkPos(context.origin()); var random = context.random();
        if (chunk.x == 0 && chunk.z == 0) {
            int y = Math.clamp(world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 8, 8), 66, 180);
            for (int x = 2; x <= 14; x++) for (int z = 2; z <= 14; z++) {
                int surface = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                for (int dy = Math.min(surface - 3, y - 4); dy <= y; dy++) world.setBlock(new BlockPos(x, dy, z), Blocks.POLISHED_BASALT.defaultBlockState(), 2);
                for (int dy = y + 1; dy < Math.max(y + 6, surface + 1); dy++) world.setBlock(new BlockPos(x, dy, z), Blocks.AIR.defaultBlockState(), 2);
            }
            for (int x : new int[]{2, 14}) for (int z : new int[]{2, 14}) for (int dy = 1; dy <= 3; dy++)
                world.setBlock(new BlockPos(x, y + dy, z), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
            return true;
        }
        int x = chunk.getMinBlockX() + 8, z = chunk.getMinBlockZ() + 8;
        int base = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        if (base < 68 || !world.getBlockState(new BlockPos(x, base - 1, z)).is(Blocks.BASALT) || random.nextInt(context.config().volcanoChance()) != 0) return true;
        int height = context.config().minimumHeight() + random.nextInt(context.config().maximumHeight() - context.config().minimumHeight() + 1);
        for (int dy = 0; dy <= height; dy++) {
            int radius = Math.max(2, 6 - dy * 4 / height);
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
                var at = new BlockPos(x + dx, base + dy, z + dz);
                if (dx * dx + dz * dz > radius * radius || !world.isEmptyBlock(at)) continue;
                world.setBlock(at, (dy == height && Math.abs(dx) + Math.abs(dz) <= 1 ? Blocks.LAVA : dy % 4 == 0 ? Blocks.BLACKSTONE : Blocks.BASALT).defaultBlockState(), 2);
            }
        }
        return true;
    }
}
