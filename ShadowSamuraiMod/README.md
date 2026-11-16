# The Shadow Samurai (Fabric, Minecraft 1.21.1, Java 21)

## Build
1. Easiest: generate a blank Fabric 1.21.1 project at https://fabricmc.net/develop/template/ (Mojang mappings, no Kotlin),
   then replace its `src/` folder with this one's `src/`, and use this `build.gradle`/`gradle.properties`
   (or just keep yours and make sure `mappings loom.officialMojangMappings()` is set).
2. `./gradlew build`  -> jar in `build/libs/`   |   `./gradlew runClient` to test.

## Play
* Creative tab "Spawn Eggs" -> Shadow Samurai, or `/summon shadowsamurai:shadow_samurai`
* Boss: 400 HP, intro cinematic (title + lightning), enrages at 50% (red bar, 3 moon waves, more chains, faster)
* Drops: The Eclipse Chain Reaper, Nether Star, Netherite Scrap
* Weapon (`/give @s shadowsamurai:eclipse_chain_reaper`):
  - Right-click: Reaper Throw (pulls enemies back)
  - Sneak + Right-click: Eclipse Pull (ring hook)
  - Melee hit: Shadow Sweep
  - Kills: Soul Harvest (up to 50 souls = +150% damage & more particles)
  - Look straight down + Right-click (10 souls): Nightmare Storm
