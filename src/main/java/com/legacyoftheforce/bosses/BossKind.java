package com.legacyoftheforce.bosses;

import com.legacyoftheforce.core.api.RewardCategory;
import net.minecraft.resources.ResourceLocation;

public enum BossKind {
    GUARDIAN("ilum_guardian", RewardCategory.JEDI_BOSS), FROST_LORD("ilum_frost_lord", RewardCategory.SITH_BOSS);
    public final String path; public final RewardCategory category;
    BossKind(String path, RewardCategory category) { this.path = path; this.category = category; }
    public ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath("legacyoftheforce", path); }
}
