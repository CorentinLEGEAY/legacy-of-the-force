package com.legacyoftheforce.planets;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;

/** One bounded site per candidate chunk; weighted structure set prevents site overlap. */
public final class IlumSiteStructure extends Structure {
    public static final MapCodec<IlumSiteStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance), Codec.intRange(0, 3).fieldOf("site_kind").forGetter(site -> site.kind)).apply(instance, IlumSiteStructure::new));
    private final int kind;
    public IlumSiteStructure(StructureSettings settings, int kind) { super(settings); this.kind = kind; }
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMinBlockX() + 2, z = context.chunkPos().getMinBlockZ() + 2;
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int dx : new int[]{0, 5, 10}) for (int dz : new int[]{0, 5, 10}) {
            int y = context.chunkGenerator().getFirstOccupiedHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            low = Math.min(low, y); high = Math.max(high, y);
        }
        if (low < context.chunkGenerator().getSeaLevel() + 2 || high - low > 8 || high > context.heightAccessor().getMaxBuildHeight() - 20) return Optional.empty();
        int base = kind == 0 ? low - 12 : low;
        var origin = new BlockPos(x, base, z);
        return Optional.of(new GenerationStub(origin, pieces -> pieces.addPiece(new IlumSitePiece(origin, kind))));
    }
    @Override public StructureType<?> type() { return PlanetContent.SITE.get(); }
}
