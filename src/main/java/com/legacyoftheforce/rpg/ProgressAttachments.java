package com.legacyoftheforce.rpg;

import com.legacyoftheforce.LegacyOfTheForce;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ProgressAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(
            NeoForgeRegistries.ATTACHMENT_TYPES, LegacyOfTheForce.MOD_ID);
    public static final Supplier<AttachmentType<PlayerProgress>> PLAYER_PROGRESS = TYPES.register(
            "player_progress", () -> AttachmentType.serializable(PlayerProgress::new).copyOnDeath().build());
    public static final Supplier<AttachmentType<KillCredit>> KILL_CREDIT = TYPES.register(
            "kill_credit", () -> AttachmentType.serializable(KillCredit::new).build());
    private ProgressAttachments() {}
}
