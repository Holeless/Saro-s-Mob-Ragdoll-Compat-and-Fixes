# Saro's Mob Ragdoll Fix

A small **client-side addon for [Saro's Mob Ragdoll](https://www.curseforge.com/minecraft/mc-mods/saros-mob-ragdoll)** on **Minecraft 1.20.1 / Forge 47.x**.
It does not replace the original mod; it patches it at load time with mixins. Both jars are needed.

Tested against Saro's Mob Ragdoll **1.0.1** (the dependency range is `[1.0.1,1.1)`).

## What it fixes

| Problem | Cause in the original | Fix |
|---|---|---|
| Mobs fly too far when they die | Death launch is hardcoded: 6 blocks/s away from the attacker, +2 up, for every mob | Configurable, scaled by mob size, capped (defaults: about a third of the horizontal speed, half the vertical) |
| Mob is **stiff** before it ragdolls | Every body part gets the *same* velocity, so no joint flexes and the corpse flies upright like a plank. Joints are also locked to about 40 degrees, and a body with no push stands on its legs for seconds | Torso leads, limbs trail at 60%, the body is spun so it tips over, tip-over in a random direction if nothing pushed it, joint limit 75 degrees |
| Alex's Mobs / Naturalist mobs: **frozen duplicate** that never ragdolls | Their models are not vanilla `ModelPart` trees, so the original falls back to one rigid box. That box was launched upright and, with no push, **never moved at all**. The original mob was only hidden with `setInvisible` + `RenderLivingEvent`, which GeckoLib renderers (Naturalist) never fire and Citadel renderers (Alex's Mobs) can ignore | The box now topples. It is drawn with the mob's own renderer from a per-ragdoll copy that has the dead mob's variant/colour and faces the right way (the original's shared copy faced backwards). The original entity is removed from the client world |

The ragdolls are simulated client side, so this is a **client-only** mod. Put it in the client's `mods` folder; servers do not need it.

## Install

1. Saro's Mob Ragdoll 1.20.1 (1.0.1) and this jar in `mods/`.
2. Config is created on first launch: `config/saros_ragdoll_fix-client.toml`.

## Config (`saros_ragdoll_fix-client.toml`)

| Section | Key | Default | Meaning |
|---|---|---|---|
| launch | enabled | true | false = original launch |
| launch | horizontalScale / verticalScale | 0.35 / 0.5 | multipliers on what the original computed (original = 1.0) |
| launch | maxLaunchSpeed | 5.0 | blocks/s cap |
| launch | scaleBySize | true | big mobs pushed less |
| launch | limbVelocityFactor | 0.6 | limb speed relative to torso (1.0 = original, stiff) |
| launch | spinOnKill | 2.5 | topple rate, rad/s |
| launch | randomToppleWhenNotPushed, notPushedBelow, notPushedSpeed | true, 0.8, 0.8 | tip over randomly when no attacker pushed it |
| joints | enabled, jointLimitDegrees | true, 75 | original is about 40 |
| compat | forceRigid | ["alexsmobs","naturalist"] | namespaces or entity ids that always use the real-renderer path. Add any other mod whose mobs look wrong |
| compat | perRagdollModel | true | per-ragdoll copy: right variant, right facing |
| compat | removeOriginal | true | remove the original mob when a rigid ragdoll takes over |

## Building

Requires JDK 17. Saro's Mob Ragdoll is "All Rights Reserved", so it is not in this repo.

1. Copy the 1.20.1 jar into `libs/` (see `libs/README.md`).
2. `gradle wrapper --gradle-version 8.8` once (or open the folder in IntelliJ and let it import), then `./gradlew build`.
3. The jar is `build/libs/saros_ragdoll_fix-forge-1.20.1-1.0.0.jar`.

To try it quickly, drop both jars into a 1.20.1 Forge instance's `mods` folder (a production run; the original is not deobfuscated, so `runClient` in the dev environment needs extra setup).

Before publishing, set `mod_issues_url` and `mod_authors` in `gradle.properties`, and the copyright line in `LICENSE`.

## Verification, and what is not verified

**Verified** (`test-harness/`, run with `./test-harness/run.sh` once the Saro jar is in `libs/`): the real `BoneRagdollPhysics` class from the 1.20.1 jar, with its Bullet native, simulated headless on a floor with and without this addon's launch and joint limits. Tilt is the torso angle from vertical; travel is horizontal distance after 10 s.

| | OLD travel | OLD tilt at 0.25s / 1s | NEW travel | NEW tilt at 0.25s / 1s |
|---|---|---|---|---|
| player-sized humanoid, hit | 7.6 blocks | 0 / 84 deg | 2.1 | 44 / 103 deg |
| rigid 0.6x1.8 box (Alex's / Naturalist), hit | 5.3 | 0 / 88 | 1.2 | 49 / 90 |
| rigid box, **no attacker** | 0.0 | **0 / 0 (never falls)** | 1.2 | 22 / 90 |
| rigid 3.5x4 box (elephant-like), hit | 5.2 | **0 / 0 (never falls)** | 2.8 | 24 / 77 |

The OLD rigid box standing forever is the "stiff, not moving" model.

**Not verified** (no Forge/Minecraft was available where this was written, so it was never built with ForgeGradle or run in game):

- It was compile-checked against the real Saro classes with small stand-ins for the Minecraft/Forge/Mixin API, so Saro-side names and signatures are confirmed, and the mixin target strings were checked against the jar's bytecode. The Minecraft-side calls are standard 1.20.1 API but were not compiled against the real thing. If one fails to compile, it is in `DummyFactory` or `ClientRagdollManagerMixin.saros_fix$spawnTail` (`ClientLevel#removeEntity`, `SynchedEntityData#getNonDefaultValues/assignValues`).
- Whether Alex's Mobs / Naturalist renderers draw correctly from the never-ticked copy (they get no walk/idle animation, so they are drawn in their default pose and tumble as one piece; limbs do not flop).
- The 180 degree facing correction in `DummyFactory.RENDER_YAW` (reasoned from the renderer code: living renderers rotate by 180 - bodyYaw). If a rigid mob starts facing backwards, set it to 0 or set `perRagdollModel=false`.
- Mixins use `defaultRequire: 1`, so if a different Saro build moved a target the game stops with a clear mixin error instead of silently doing nothing.
