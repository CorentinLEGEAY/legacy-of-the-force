package com.legacyoftheforce.content;

import com.legacyoftheforce.LegacyOfTheForce;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TransactionContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LegacyOfTheForce.MOD_ID);
    public static final Supplier<TransactionAnchorBlock> ANCHOR = BLOCKS.register("transaction_anchor", () ->
            new TransactionAnchorBlock(BlockBehaviour.Properties.of().strength(-1, 3_600_000)
                    .noLootTable().pushReaction(PushReaction.BLOCK).noOcclusion()));
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LegacyOfTheForce.MOD_ID);
    public static final Supplier<BlockEntityType<TransactionAnchorEntity>> ANCHOR_ENTITY = ENTITIES.register("transaction_anchor",
            () -> BlockEntityType.Builder.of(TransactionAnchorEntity::new, ANCHOR.get()).build(null));

    public static void register(IEventBus bus) { BLOCKS.register(bus); ENTITIES.register(bus); }
    @SubscribeEvent public void onBreak(BlockEvent.BreakEvent event) {
        if (event.getState().is(ANCHOR.get())) event.setCanceled(true);
    }
    @SubscribeEvent public void onExplosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(position -> event.getLevel().getBlockState(position).is(ANCHOR.get()));
    }
}
