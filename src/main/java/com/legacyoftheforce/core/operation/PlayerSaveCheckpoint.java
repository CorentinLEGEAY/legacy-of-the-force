package com.legacyoftheforce.core.operation;

import com.legacyoftheforce.LegacyOfTheForce;
import com.mojang.serialization.Codec;
import java.io.IOException;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Monotone native player-save generation; not a gameplay balance or a second inventory. */
public record PlayerSaveCheckpoint(CompoundTag tag) {
    public static final String ID = "legacyoftheforce:save_checkpoint";
    private static final Codec<PlayerSaveCheckpoint> CODEC = CompoundTag.CODEC.xmap(PlayerSaveCheckpoint::new, PlayerSaveCheckpoint::tag);
    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LegacyOfTheForce.MOD_ID);
    public static final Supplier<AttachmentType<PlayerSaveCheckpoint>> TYPE = TYPES.register("save_checkpoint",
            () -> AttachmentType.builder(() -> at(0)).serialize(CODEC).copyOnDeath().build());

    public PlayerSaveCheckpoint { tag = tag.copy(); }
    @Override public CompoundTag tag() { return tag.copy(); }
    public static void register(IEventBus bus) { TYPES.register(bus); }
    public static PlayerSaveCheckpoint at(long generation) {
        if (generation < 0) throw new IllegalArgumentException("Negative save generation");
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putLong("generation", generation);
        return new PlayerSaveCheckpoint(tag);
    }
    public long generation() throws IOException {
        if (tag.sizeInBytes() > 256 || !tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != 1
                || !tag.contains("generation", Tag.TAG_LONG) || tag.getLong("generation") < 0) throw new IOException("Unsupported player save checkpoint");
        return tag.getLong("generation");
    }
    static long generation(CompoundTag player) throws IOException {
        var attachments = player.getCompound("neoforge:attachments");
        if (!attachments.contains(ID)) return 0; // Worlds written before the checkpoint exists.
        if (!attachments.contains(ID, Tag.TAG_COMPOUND)) throw new IOException("Malformed player save checkpoint");
        return new PlayerSaveCheckpoint(attachments.getCompound(ID)).generation();
    }
}
