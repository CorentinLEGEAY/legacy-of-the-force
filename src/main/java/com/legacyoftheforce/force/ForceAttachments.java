package com.legacyoftheforce.force;

import com.legacyoftheforce.LegacyOfTheForce;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ForceAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LegacyOfTheForce.MOD_ID);
    public static final Supplier<AttachmentType<MobForceState>> MOB_IMPULSE = TYPES.register("mob_impulse", () -> AttachmentType.serializable(MobForceState::new).build());
    private ForceAttachments() {}
}
