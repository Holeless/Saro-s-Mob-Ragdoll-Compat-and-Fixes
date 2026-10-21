Saro's Mob Ragdoll Compat and Fixes aims to broaden Saro's Mob Ragdoll's compatibility to mods dependent on Geckolib, AzureLib and Citadel.

This mod also changes how mobs are flung; in the original mod, mobs have a hardcoded launch of 6 b/s away from the attacker, +2 up, applied as the same velocity to every body part, making the corpse launch stiff. Mob launch is now dynamic and configurable in config/saros_ragdoll_fix-client.toml.

Fixes bug in the original mod where certain mobs like bunnies, iron golems, and villagers, have the wrong ragdoll entity; ragdolls are now a copy of the individual mob rather than one shared default.

Fixes bug in the original mod where sleeping mobs snap out of bed instead of ragdolling in place.

This mod requires Saro's Mob Ragdoll. 

Ragdolls now match the size of the individual mob. The original builds one skeleton per mob type from a default-sized mob, so a grown Ice and Fire dragon ragdolled as a hatchling. Toggle: fixEntitySize.
