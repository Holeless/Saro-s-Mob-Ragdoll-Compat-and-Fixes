# Saro's Mob Ragdoll Fix

A client-side addon for [Saro's Mob Ragdoll](https://www.curseforge.com/minecraft/mc-mods/saros-mob-ragdoll) on Minecraft 1.20.1 / Forge 47. It patches the original with mixins, so you need both jars. Servers don't need it.

Built and tested against Saro's Mob Ragdoll 1.0.1 (the dependency range is `[1.0.1,1.1)`).

## What it changes

- **Launch.** The original throws every mob 6 blocks/s away from the attacker and 2 up, at the same speed on every body part. Corpses fly far and stay stiff. Now the torso leads, limbs trail behind, the body is spun so it tips over, and the push is scaled down and capped. Mobs nobody pushed (fall damage, fire) fall in a random direction.
- **Joints.** The original locks every joint to about 40 degrees. The default here is 75.
- **GeckoLib and Citadel mobs** (Alex's Mobs, Naturalist). Their models aren't vanilla `ModelPart` trees, so the original used one rigid box that sometimes never moved and left a frozen copy of the mob behind. Where possible the model is now converted into a real multi-bone skeleton. Otherwise the box topples, is drawn from its own copy of the mob, and the original entity is removed.
- **Looks.** Each ragdoll is drawn from a copy of the dead mob instead of one shared default per type: variant, colour, iron golem cracks, equipment, villager profession and level badge, Better Blood Overlay wounds. Adult rabbits are no longer 1.67x too big.
- **Duplicates.** Repeat spawns for the same mob are ignored.
- **Beds.** Mobs that die in bed stay lying in it.

## Install

Put Saro's Mob Ragdoll 1.0.1 and this jar in `mods/`. The config is created on first launch at `config/saros_ragdoll_fix-client.toml`; every option has a comment in the file.

The main ones:

| Section | Key | Default |
|---|---|---|
| launch | `enabled`, `horizontalScale`, `verticalScale`, `maxLaunchSpeed` | true, 0.35, 0.5, 5.0 |
| launch | `limbVelocityFactor`, `spinOnKill` | 0.2, 2.5 |
| joints | `enabled`, `jointLimitDegrees` | true, 75 |
| compat | `convertGeoModels`, `convertCitadelModels` | true |
| compat | `forceRigid` | `["alexsmobs","naturalist"]` (ignored for mobs that convert fine) |
| compat | `perRagdollModel`, `removeOriginal` | true |
| fidelity | `copyDamageState`, `copyBloodWounds`, `fixModelScale`, `dedupeSpawns` | true |
| sleeping | `layDownSleepers`, `sleepFaceDown` | true, false |

If a mod's mobs look wrong, try adding its namespace to `forceRigid`.

## Building

JDK 17. Saro's mod is All Rights Reserved so it isn't in this repo: copy the 1.20.1 jar into `libs/` (see `libs/README.md`), run `gradle wrapper --gradle-version 8.8` once, then `./gradlew build`. The jar ends up in `build/libs/`.

`.github/workflows/main.yml` builds on every push and attaches the jar to a release when you push a `v*` tag. It needs a repository secret `SARO_JAR_URL` with a direct link to the Saro jar (or a private repo with the jar force-added to `libs/`).

`runClient` in the dev environment needs extra setup because the original isn't deobfuscated. The easy way to try a build is to drop both jars into a normal 1.20.1 Forge instance.

Before publishing, change `mod_issues_url` and `mod_authors` in `gradle.properties` and the copyright line in `LICENSE`.

## Tests

`./test-harness/run.sh` (Saro jar in `libs/`, JDK 17+) runs the real `BoneRagdollPhysics` and its Bullet native headless on a flat floor, with and without the addon's launch and joint limits. Results from the first run (travel after 10 s, torso tilt from vertical):

| Case | Old travel | Old tilt at 0.25 s / 1 s | New travel | New tilt at 0.25 s / 1 s |
|---|---|---|---|---|
| humanoid, hit | 7.6 | 0 / 84 | 2.1 | 63 / 119 |
| rigid 0.6x1.8 box, hit | 5.3 | 0 / 88 | 1.2 | 49 / 90 |
| rigid box, no attacker | 0.0 | 0 / 0 (never falls) | 1.2 | 22 / 90 |
| rigid 3.5x4 box, hit | 5.2 | 0 / 0 (never falls) | 2.8 | 24 / 77 |

Limbs on multi-part mobs now bend about 110 degrees in the first half second, against 13 originally. Mobs that stay on the rigid path are a single body and tumble as one piece.

## Not checked

The physics is tested; the Minecraft side is not. There was no Forge or Minecraft available when this was written, so it hasn't been built with ForgeGradle or played, only compile-checked against the real Saro classes with stand-ins for the Minecraft API. If something fails to compile, look in `DummyFactory` and `ClientRagdollManagerMixin` first (`ClientLevel#removeEntity`, `SynchedEntityData#getNonDefaultValues/assignValues`).

Also unchecked in game: how Alex's Mobs and Naturalist draw from the never-ticked copy, and the 180 degree facing in `DummyFactory.RENDER_YAW`. If a mob faces backwards, set it to 0 or turn off `perRagdollModel`.

The mixins use `defaultRequire: 1`, so a Saro build that moved a target stops the game with a mixin error instead of silently doing nothing.
