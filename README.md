# Kings & Monsters — Minecraft ModJam 2026

This repository contains the source code corresponding to the Minecraft 26.1.2 NeoForge ModJam 2026 submission of Kings & Monsters for the "Echoes of the Past" theme.

Kings & Monsters is an ongoing mod project; this repository is a snapshot of one specific release — the 26.1.2 ModJam 2026 submission — and does not represent the entirety of the mod's development history or every platform it targets.

## Overview

Kings & Monsters adds an animated ogre faction to Minecraft: a full tribe of ogres (grunts, captains, brutes, mages, guards, archers, a merchant, and the Ogre King boss), bog iron equipment and worldgen, an outpost/tribute/patrol escalation system, and a "Royal Defence" trial encounter tied to the Ogre King's fort.

- **Minecraft version:** 26.1.2
- **Modloader:** NeoForge
- **Mod version:** 1.2.1
- **Required Java version:** Java 25

## Building

```bash
gradlew.bat build
```

(Use `./gradlew build` on Linux/macOS.) The build requires a Java 25 JDK — either set `JAVA_HOME` to a Java 25 installation, or let Gradle's toolchain resolution provision one automatically.

The resulting mod jar is generated at `build/libs/kingsandmonsters-mc26.1.2-neoforge-1.2.1.jar`.

## Major Gameplay Features

- **Ogre tribe:** Grunt, Grunt Captain, Brute, Mage, Guard, Archer, and Merchant ogres with GeckoLib-animated combat, ranged attacks, and pursuit AI.
- **The Ogre King:** a multi-phase boss encounter with a fort, boss music, and a scripted attack roster.
- **Royal Defence:** a trial-spawner-based encounter tied to the Ogre King's fort.
- **Bog Iron:** a swamp-themed ore/equipment progression line, including full tool and armor sets, gated behind defeating the Ogre King.
- **Outposts, tribute, and patrols:** an anger/reputation system that escalates ogre patrol activity in response to player actions.

## Runtime Dependencies

**Required:**
- NeoForge (matching this build's targeted version)
- GeckoLib 5.5+
- Curios API 15.0+
- Patchouli 26.1-94-beta+

**Optional:**
- Better Combat 3.2+ (combat animation integration)
- JEI 29.0+ (recipe/item lookup, client-side)
- Jade 26.1+ (entity/block info overlay)

## Development-Only Testing Dependencies

This project's Gradle build declares a "Training Dummy" dependency (via `localRuntime`) purely for development-time combat testing in the local run configuration. It is not bundled in the production jar and is not a player-facing dependency.

## License

**All Rights Reserved.** This project is not distributed under an open-source license. Third-party assets and template files retain their own licenses and attribution, preserved in this repository (see `licenses/` and `TEMPLATE_LICENSE.txt`).

## Submission Note

This repository is a source snapshot corresponding to the CurseForge build submitted for Minecraft ModJam 2026.
