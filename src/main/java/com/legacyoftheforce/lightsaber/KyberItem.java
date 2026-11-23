package com.legacyoftheforce.lightsaber;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class KyberItem extends Item {
    public KyberItem(Properties properties) { super(properties); }
    public static KyberData freshBlue() { return KyberData.blue(UUID.randomUUID(), SaberConfig.CRYSTAL_PURITY.get(), SaberConfig.CRYSTAL_STABILITY.get()); }
    @Override public Component getName(ItemStack stack){var data=stack.get(SaberContent.KYBER_DATA.get());return data!=null&&data.state().isPresent()?Component.translatable("crystal.legacyoftheforce.kind_"+data.kind().orElseThrow().name().toLowerCase(Locale.ROOT)):super.getName(stack);}
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide() && stack.getCount() == 1 && !stack.has(SaberContent.KYBER_DATA.get())) {
            stack.set(SaberContent.KYBER_DATA.get(), freshBlue());
        }
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
        var data = stack.get(SaberContent.KYBER_DATA.get());
        if (data == null) { lines.add(Component.translatable("crystal.legacyoftheforce.unidentified")); return; }
        describe(data, lines);
    }
    static void describe(KyberData data, List<Component> lines) {
        var state = data.state();
        if (state.isEmpty()) { lines.add(Component.translatable("crystal.legacyoftheforce.invalid")); return; }
        var value = state.get();
        var kind=data.kind().orElseThrow();lines.add(Component.translatable("crystal.legacyoftheforce.origin_named",Component.translatable("crystal.legacyoftheforce.planet_"+kind.name().toLowerCase(Locale.ROOT))));
        lines.add(Component.translatable("crystal.legacyoftheforce.state_"+data.tag().getString("state")));
        lines.add(Component.translatable("crystal.legacyoftheforce.affinity_"+kind.resonance()));
        lines.add(Component.translatable("crystal.legacyoftheforce.quality", String.format(Locale.ROOT, "%.0f%%", value.purity() * 100),
                String.format(Locale.ROOT, "%.0f%%", value.stability() * 100)));
        lines.add(Component.translatable("crystal.legacyoftheforce.bond", value.bondLevel()));
        lines.add(Component.translatable("crystal.legacyoftheforce.bond_stage_" + value.bondLevel(), value.bondProgress()));
        if(value.bondLevel()>=1)lines.add(Component.translatable("crystal.legacyoftheforce.affinity_active"));
        if(value.bondLevel()>=3)lines.add(Component.translatable("crystal.legacyoftheforce.specialization",Component.translatable("crystal.legacyoftheforce.specialization_"+KyberBond.specialization(data))));
    }
}
