execute in minecraft:the_nether if block 0 99 3 minecraft:air if block 2 99 2 minecraft:dirt run say SOLO_BLOCK_TRANSFER_PASS
execute in minecraft:the_nether unless entity @e[type=minecraft:block_display] run say SOLO_NO_ORPHAN_DISPLAY
