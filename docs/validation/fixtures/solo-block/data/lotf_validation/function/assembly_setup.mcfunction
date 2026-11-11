# Disposable validation world only. Add a fresh platform above the Nether roof without clearing existing builds.
execute in minecraft:the_nether run tp @s 1005.5 240 1002.5 0 20
execute in minecraft:the_nether run fill 1000 239 1000 1012 239 1012 minecraft:stone keep
execute in minecraft:the_nether run setblock 1005 240 1005 legacyoftheforce:speeder_garage
say ASSEMBLY_PLATFORM_READY
