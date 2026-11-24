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
    private int offset,filter,visibleRows;
    public QuestJournalScreen(QuestJournalMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); imageWidth = 300; imageHeight = 228; }
    @Override protected void init() {
        imageWidth=Math.min(320,width-8);imageHeight=Math.min(250,height-8);super.init();rows.clear();visibleRows=Math.max(1,(imageHeight-55)/10);
        int section=0;
        for (var line : menu.lines()) {
            if(line.key().startsWith("quest.legacyoftheforce.section_")&&!line.key().equals("quest.legacyoftheforce.section_empty"))try{section=Integer.parseInt(line.key().substring(line.key().lastIndexOf('_')+1));}catch(NumberFormatException ignored){}
            if(filter!=0&&section!=0&&section!=filter)continue;
            var prefix = line.status() == 0 ? "" : line.status() == 1 ? "☐ " : "✓ ";
            var text = Component.literal(prefix).append(Component.translatable(line.key()));
            int color = line.status() == 2 ? 0xa6efb3 : line.status() == 1 ? 0xffd085 : 0xd5e9e9;
            for (var wrapped : font.split(text,imageWidth-24)) rows.add(new Row(wrapped, color)); rows.add(new Row(FormattedCharSequence.EMPTY, color));
        }
        offset = Math.clamp(offset, 0, Math.max(0, rows.size() - visibleRows));
        int footer=topPos+imageHeight-25;
        addRenderableWidget(Button.builder(Component.literal("▲"),button->offset=Math.max(0,offset-Math.max(1,visibleRows-2))).bounds(leftPos+8,footer,28,18).build());
        addRenderableWidget(Button.builder(Component.literal("▼"),button->offset=Math.min(Math.max(0,rows.size()-visibleRows),offset+Math.max(1,visibleRows-2))).bounds(leftPos+40,footer,28,18).build());
        addRenderableWidget(Button.builder(Component.translatable("quest.legacyoftheforce.filter_"+filter),button->{filter=(filter+1)%4;offset=0;rebuildWidgets();}).bounds(leftPos+72,footer,Math.max(30,imageWidth-144),18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),button->onClose()).bounds(leftPos+imageWidth-64,footer,56,18).build());
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) { graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff142332); }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 7, 0xc9f5ff, false);
        for (int i = 0; i < visibleRows && i + offset < rows.size(); i++) { var row = rows.get(i + offset); graphics.drawString(font, row.text(), 12, 25 + i * 10, row.color(), false); }
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { super.render(graphics, mouseX, mouseY, partialTick); }
}
