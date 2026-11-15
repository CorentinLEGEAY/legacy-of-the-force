package com.legacyoftheforce.planets;

import net.minecraft.core.BlockPos;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/** Only the current generation chunk is touched; no login/chunk-load regeneration. */
public final class IlumFrostFeature extends Feature<IlumFrostFeature.Settings> {
    public record Settings(int ridgeChance, int minimumHeight, int maximumHeight) implements FeatureConfiguration {
        public static final Codec<Settings> CODEC = RecordCodecBuilder.<Settings>create(instance -> instance.group(
                Codec.intRange(1, 64).fieldOf("ridge_chance").forGetter(Settings::ridgeChance),
                Codec.intRange(2, 16).fieldOf("minimum_height").forGetter(Settings::minimumHeight),
                Codec.intRange(2, 16).fieldOf("maximum_height").forGetter(Settings::maximumHeight)).apply(instance, Settings::new))
                .validate(value -> value.maximumHeight >= value.minimumHeight ? DataResult.success(value) : DataResult.error(() -> "Invalid ice ridge height range"));
    }
    public IlumFrostFeature() { super(Settings.CODEC); }
    @Override public boolean place(FeaturePlaceContext<Settings> context) {
        var world = context.level(); var chunk = new ChunkPos(context.origin());
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int wx = chunk.getMinBlockX() + x, wz = chunk.getMinBlockZ() + z;
            var at = new BlockPos(wx, world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, wx, wz) - 1, wz);
            var state = world.getBlockState(at);
            if (state.is(Blocks.WATER)) world.setBlock(at, Blocks.ICE.defaultBlockState(), 2);
            else if (state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND))
                world.setBlock(at, Blocks.SNOW_BLOCK.defaultBlockState(), 2);
        }
        // A bounded original ice ridge; its footprint never spills into a neighboring chunk.
        int cx = chunk.getMinBlockX() + 8, cz = chunk.getMinBlockZ() + 8;
        var base = new BlockPos(cx, world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz) - 1, cz);
        if (world.getBlockState(base).is(Blocks.SNOW_BLOCK) && context.random().nextInt(context.config().ridgeChance()) == 0) {
            int height = context.config().minimumHeight() + context.random().nextInt(context.config().maximumHeight() - context.config().minimumHeight() + 1);
            for (int y = 1; y <= height; y++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > (y > height - 2 ? 1 : 2)) continue;
                var at = base.offset(dx, y, dz);
                if (world.isEmptyBlock(at)) world.setBlock(at, (y == height ? Blocks.BLUE_ICE : Blocks.PACKED_ICE).defaultBlockState(), 2);
            }
        }
        return true;
    }
}
