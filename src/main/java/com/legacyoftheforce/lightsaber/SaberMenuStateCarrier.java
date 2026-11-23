package com.legacyoftheforce.lightsaber;

import net.minecraft.nbt.Tag;

/** Transient load bookkeeping only; saved content is the versioned NBT image, never a Java reference. */
public interface SaberMenuStateCarrier {
    Tag legacyoftheforce$loadedMenu();
    void legacyoftheforce$loadedMenu(Tag image);
    boolean legacyoftheforce$menuPending();
    void legacyoftheforce$menuRestored();
}
