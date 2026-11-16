package com.legacyoftheforce.quests;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Read-only server snapshot; no button can award knowledge or mutate an inventory. */
public final class QuestJournalMenu extends AbstractContainerMenu {
    public record Line(String key, int status) {
        public Line { if (!key.matches("[a-z0-9_.]{1,128}") || status < 0 || status > 2) throw new IllegalArgumentException("Invalid journal line"); }
    }
    private final Player owner;
    private final List<Line> lines;
    public QuestJournalMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) { this(id, inventory, read(buffer)); }
    public QuestJournalMenu(int id, Inventory inventory, List<Line> lines) {
        super(QuestContent.JOURNAL.get(), id); owner = inventory.player;
        if (lines.size() > 48) throw new IllegalArgumentException("Journal snapshot exceeds budget"); this.lines = List.copyOf(lines);
    }
    public List<Line> lines() { return lines; }
    public static void write(RegistryFriendlyByteBuf buffer, List<Line> lines) {
        if (lines.size() > 48) throw new IllegalArgumentException("Journal snapshot exceeds budget");
        buffer.writeVarInt(lines.size()); for (var line : lines) { buffer.writeUtf(line.key(), 128); buffer.writeByte(line.status()); }
    }
    private static List<Line> read(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt(); if (count < 0 || count > 48) throw new IllegalArgumentException("Journal snapshot exceeds budget");
        var lines = new ArrayList<Line>(); for (int i = 0; i < count; i++) lines.add(new Line(buffer.readUtf(128), buffer.readUnsignedByte())); return List.copyOf(lines);
    }
    @Override public boolean stillValid(Player player) { return player == owner && player.isAlive() && !player.isSpectator(); }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean clickMenuButton(Player player, int button) { return false; }
}
