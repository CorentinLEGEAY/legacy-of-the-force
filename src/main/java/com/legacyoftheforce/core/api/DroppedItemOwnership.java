package com.legacyoftheforce.core.api;

import java.util.UUID;

/** Read-only native thrower identity, including owners currently offline. Never changes pickup rules. */
public interface DroppedItemOwnership {
    UUID legacyoftheforce$thrower();
}
