package com.legacyoftheforce.client;

import com.legacyoftheforce.quests.QuestJournalMenu;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

public final class QuestJournalScreen extends AbstractContainerScreen<QuestJournalMenu> {
    private record Row(FormattedCharSequence text, int color) {}
    private final List<Row> rows = new ArrayList<>();
    private int offset;
    public QuestJournalScreen(QuestJournalMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); imageWidth = 300; imageHeight = 228; }
    @Override protected void init() {
        super.init(); rows.clear();
        for (var line : menu.lines()) {
            var prefix = line.status() == 0 ? "" : line.status() == 1 ? "☐ " : "✓ ";
            var text = Component.literal(prefix).append(Component.translatable(line.key()));
            int color = line.status() == 2 ? 0xa6efb3 : line.status() == 1 ? 0xffd085 : 0xd5e9e9;
            for (var wrapped : font.split(text, 276)) rows.add(new Row(wrapped, color)); rows.add(new Row(FormattedCharSequence.EMPTY, color));
        }
        offset = Math.clamp(offset, 0, Math.max(0, rows.size() - 17));
        addRenderableWidget(Button.builder(Component.literal("▲"), button -> offset = Math.max(0, offset - 12)).bounds(leftPos + 8, topPos + 203, 40, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), button -> offset = Math.min(Math.max(0, rows.size() - 17), offset + 12)).bounds(leftPos + 52, topPos + 203, 40, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose()).bounds(leftPos + 202, topPos + 203, 90, 18).build());
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) { graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff142332); }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 7, 0xc9f5ff, false);
        for (int i = 0; i < 17 && i + offset < rows.size(); i++) { var row = rows.get(i + offset); graphics.drawString(font, row.text(), 12, 25 + i * 10, row.color(), false); }
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { super.render(graphics, mouseX, mouseY, partialTick); }
}
