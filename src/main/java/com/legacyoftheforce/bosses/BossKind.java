package com.legacyoftheforce.bosses;

import com.legacyoftheforce.core.api.RewardCategory;
import net.minecraft.resources.ResourceLocation;

public enum BossKind {
    GUARDIAN("ilum_guardian", RewardCategory.JEDI_BOSS), FROST_LORD("ilum_frost_lord", RewardCategory.SITH_BOSS),
    WANDERING_KNIGHT("tatooine_wandering_knight", RewardCategory.JEDI_BOSS), DESERT_EXILE("tatooine_desert_exile", RewardCategory.SITH_BOSS),
    NATURE_KEEPER("endor_nature_keeper", RewardCategory.JEDI_BOSS), NIGHT_ASSASSIN("endor_night_assassin", RewardCategory.SITH_BOSS),
    RUINS_PROTECTOR("mustafar_ruins_protector", RewardCategory.JEDI_BOSS), FLAME_LORD("mustafar_flame_lord", RewardCategory.SITH_BOSS),
    FORCE_HERMIT("dagobah_force_hermit", RewardCategory.JEDI_BOSS), ILLUSION_MASTER("dagobah_illusion_master", RewardCategory.SITH_BOSS),
    TEMPLE_GUARDIAN("jedha_temple_guardian", RewardCategory.JEDI_BOSS), RELIC_LORD("jedha_relic_lord", RewardCategory.SITH_BOSS);
    public final String path; public final RewardCategory category;
    BossKind(String path, RewardCategory category) { this.path = path; this.category = category; }
    public ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath("legacyoftheforce", path); }
    public ResourceLocation dimension() { return ResourceLocation.fromNamespaceAndPath("legacyoftheforce", path.substring(0, path.indexOf('_'))); }
    public boolean dark() { return category == RewardCategory.SITH_BOSS; }
    public boolean desert() { return this == WANDERING_KNIGHT || this == DESERT_EXILE; }
    public boolean forest() { return this == NATURE_KEEPER || this == NIGHT_ASSASSIN; }
    public boolean volcanic() { return this == RUINS_PROTECTOR || this == FLAME_LORD; }
    public boolean sacred() { return this == TEMPLE_GUARDIAN || this == RELIC_LORD; }
    public boolean swamp() { return this == FORCE_HERMIT || this == ILLUSION_MASTER; }
    public static boolean supportedDimension(ResourceLocation dimension) { return java.util.Arrays.stream(values()).anyMatch(kind -> kind.dimension().equals(dimension)); }
}
