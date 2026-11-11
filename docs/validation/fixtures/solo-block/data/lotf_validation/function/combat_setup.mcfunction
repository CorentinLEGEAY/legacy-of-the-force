gamemode survival @s
tp @s 0.5 99 0.5 -90 10
fill 0 98 -1 4 98 1 minecraft:stone
summon minecraft:cow 2.5 99 0.5 {NoAI:1b,PersistenceRequired:1b,Tags:["lotf_saber_target"],Health:10.0f}
attribute @s minecraft:generic.attack_damage get
say SABER_COMBAT_FIXTURE_READY
