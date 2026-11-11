package com.legacyoftheforce.content;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class TransactionAnchorEntity extends BlockEntity {
    private UUID operationId;
    private CompoundTag preserved;
    public TransactionAnchorEntity(BlockPos position, BlockState state) {
        super(TransactionContent.ANCHOR_ENTITY.get(), position, state);
    }
    public void bind(UUID id) { operationId = id; setChanged(); }
    public boolean matches(UUID id) { return preserved == null && id.equals(operationId); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (preserved != null) { tag.merge(preserved.copy()); return; }
        tag.putInt("schemaVersion", 1);
        if (operationId != null) tag.putUUID("operationId", operationId);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.getInt("schemaVersion") != 1 || !tag.hasUUID("operationId")) {
            preserved = tag.copy(); operationId = null;
        } else { preserved = null; operationId = tag.getUUID("operationId"); }
    }
}
