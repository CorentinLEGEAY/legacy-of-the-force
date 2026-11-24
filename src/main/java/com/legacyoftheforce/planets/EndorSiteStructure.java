package com.legacyoftheforce.planets;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;

public final class EndorSiteStructure extends Structure {
    public static final MapCodec<EndorSiteStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance), Codec.intRange(0, 4).fieldOf("site_kind").forGetter(site -> site.kind)).apply(instance, EndorSiteStructure::new));
    private final int kind;
    public EndorSiteStructure(StructureSettings settings, int kind) { super(settings); this.kind = kind; }
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (Math.abs((long)context.chunkPos().x) <= 3 && Math.abs((long)context.chunkPos().z) <= 3) return Optional.empty();
        int x = context.chunkPos().getMinBlockX() + 1, z = context.chunkPos().getMinBlockZ() + 1;
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int dx : new int[]{0, 6, 12}) for (int dz : new int[]{0, 6, 12}) {
            int y = context.chunkGenerator().getFirstOccupiedHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            low = Math.min(low, y); high = Math.max(high, y);
        }
        if (low < 58 || high - low > 10 || high > context.heightAccessor().getMaxBuildHeight() - 40) return Optional.empty();
        var origin = new BlockPos(x, kind == 0 ? low - 12 : low, z);
        return Optional.of(new GenerationStub(origin, pieces -> pieces.addPiece(new EndorSitePiece(origin, kind))));
    }
    @Override public StructureType<?> type() { return PlanetContent.FOREST_SITE.get(); }
}
