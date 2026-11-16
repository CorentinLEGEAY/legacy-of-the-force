package com.legacyoftheforce.quests;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;

/** Rare surface ruin, entirely contained in its candidate chunk. */
public final class EchoRuinStructure extends Structure {
    public static final MapCodec<EchoRuinStructure> CODEC = simpleCodec(EchoRuinStructure::new);
    public EchoRuinStructure(StructureSettings settings) { super(settings); }
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMinBlockX() + 4, z = context.chunkPos().getMinBlockZ() + 4;
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int dx : new int[]{0, 6}) for (int dz : new int[]{0, 6}) {
            int y = context.chunkGenerator().getFirstOccupiedHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            low = Math.min(low, y); high = Math.max(high, y);
        }
        if (low < context.chunkGenerator().getSeaLevel() + 2 || high - low > 3 || high > context.heightAccessor().getMaxBuildHeight() - 6) return Optional.empty();
        var origin = new BlockPos(x, low, z);
        return Optional.of(new GenerationStub(origin, pieces -> pieces.addPiece(new EchoRuinPiece(origin))));
    }
    @Override public StructureType<?> type() { return QuestContent.RUIN.get(); }
}
