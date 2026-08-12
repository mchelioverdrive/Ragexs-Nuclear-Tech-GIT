# Ragex's Nuclear Tech Mod

Ragex's Nuclear Tech Mod (RNTM) is a Minecraft Forge 1.7.10 fork of HBM's Nuclear Tech Mod rebuilt around a more grounded nuclear-technology fantasy. The project goal is not simply to add more late-game content: it removes or de-emphasizes unrealistic/fantasy material from the original project and replaces it with real-world counterparts, plausible industrial processes, or grounded science-fiction systems.

RNTM is intended for players, pack makers, and server operators who want NTM-style scale with a stronger focus on realistic nuclear engineering, chemistry, radiation, ore/oil geology, space environments, and configurable server warfare. Expect more involved processing chains, more reactor and hazard management, and fewer joke/fantasy shortcuts than upstream NTM.

> **Minecraft version:** 1.7.10 only. This project does not target modern Minecraft versions.

## Project goals

- **Ground the mod in real or plausible technology.** Fantasy or heavily unrealistic systems from the original project are removed, renamed, reworked, or replaced where practical.
- **Make nuclear engineering the core identity.** Reactors, radiation, isotopes, fallout, enrichment, treatment, and contamination cleanup are central progression and server systems.
- **Expand chemistry and industry.** RNTM emphasizes large chemical process chains, periodic-table materials, isotope separation, blast-furnace progression, distillation, and fluid handling.
- **Make worlds and space feel physical.** Planetary bodies, gas giants, the Sun, orbits, bedrock oil, and bedrock ore deposits are treated as gameplay systems rather than purely decorative dimensions.
- **Keep dangerous systems configurable.** Server owners can tune or disable nukes, missiles, radiation, worldgen, mobs, dimensions, recipes, and client presentation.

## What changed from original HBM's Nuclear Tech Mod

Highlights from the RTM-era direction include:

- **Nuclear and radiation overhaul:** reactor systems were reworked, a Molten Salt Reactor was added, WATZ was converted into a Pebble Bed Reactor (PBR), radiation treatment was updated around items such as Prussian Blue IVs, fallout stacking/cleanup became harsher, and more hazard sources were added.
- **Chemistry expansion:** the fork adds many new chemical processes, expands periodic-table coverage, reworks distillation and isotope separation, improves SILEX enrichment realism, and adds numerous industrial production chains.
- **World and space rework:** planetary bodies were reworked, gas giant dimensions and Sun travel were added, orbit/time calculations were fixed, and oil/ore deposits were moved toward bedrock-based generation.
- **Industry and progression rebalance:** steam and fluid systems were rebalanced, blast-furnace usage is emphasized earlier, furnace smelting was adjusted, extra RTG variants were added, the Nuclear Furnace recipe was removed, and research reactors were re-enabled in 528 mode.
- **Weapons and warfare changes:** CIWS support was re-added/reworked, bombsite support for CSGO Charge was added, and server-side nuclear/missile timing controls exist for admins.
- **Gameplay and compatibility cleanup:** unrealistic features and recipes such as Power Fist recipes were removed or reduced, while grounded science-fiction content was preserved; Angelica/GTNH NEI compatibility and many rendering/crash fixes were added.

See [docs/upstream-changes.md](docs/upstream-changes.md) for a more focused comparison against the original project.

## Major feature areas

- **Realism-focused nuclear technology:** MSR, PBR, RBMK/PWR-style systems, research reactors, nuclear fuels, waste handling, meltdowns, fallout, radiation, and nuclear explosions.
- **Industrial chemistry:** large chemical production chains, isotope work, distillation, SILEX enrichment, acids, fuels, fluids, and periodic-table-driven materials.
- **Grounded resources:** realistic ore and oil deposit concepts, renewable bedrock oil/gas fallback extraction for custom maps, bedrock resource drilling, expanded ore processing, metallurgy, and blast-furnace-centered progression.
- **Power and fluids:** mod-native energy networks, RF-compatible APIs, fluid tanks, fluid traits, gases, fuels, coolants, corrosion, pollution, and radioactive fluid behavior.
- **Space and celestial bodies:** revised planetary bodies, gas giants, Sun travel, orbital stations, satellites, atmosphere and water-table logic, and station administration commands.
- **Weapons, hazards, and warfare controls:** guns, explosives, missiles, CIWS, fallout rain, biome damage from detonations, radiation hotspots, scheduled nuke toggles, and server moderation commands.
- **Configuration and pack tooling:** JSON recipe overrides, custom machines, fluid traits, loot pools, machine-value JSONs, and extensive Forge config categories.
- **Legacy 1.7.10 compatibility:** NotEnoughItems integration at build/runtime, GTNH NEI fork compatibility notes, OpenComputers API support, Inventory Tweaks compile-time support, Angelica rendering compatibility hooks, and selected legacy-mod compatibility hooks.

## Download links and resources

- **Modrinth:** <https://modrinth.com/mod/ragexs-nuclear-tech>
- **CurseForge:** <https://www.curseforge.com/minecraft/mc-mods/ragexs-nuclear-tech>
- **GitHub:** use this repository for source, issues, and development builds.
- **Original NTM wiki:** <https://nucleartech.wiki/wiki/Main_Page>
- **Original NTM / related projects:** see [docs/getting-started.md](docs/getting-started.md#related-projects).

The original NTM wiki is useful for inherited mechanics, but RNTM intentionally changes progression, names, recipes, radiation behavior, reactors, space, and some item availability. Prefer this repository's docs and generated configs for RNTM-specific behavior.

## Installation

### Client installation

1. Install **Minecraft 1.7.10**.
2. Install **Minecraft Forge 1.7.10-10.13.4.1614** or a compatible Forge 1.7.10 build.
3. Download the RNTM jar from Modrinth, CurseForge, or a GitHub release.
4. Put the jar in your `.minecraft/mods` folder.
5. Start the game once so configuration files can generate.
6. Optional but recommended: install NotEnoughItems for recipe browsing.

### Dedicated server installation

1. Install a Forge 1.7.10 dedicated server.
2. Put the same RNTM jar in the server `mods` folder.
3. Start the server once, then stop it after configuration files generate.
4. Review `config/hbm.cfg`, `config/hbmConfig/*.json`, and `config/hbmRecipes/` templates before opening the world to players.
5. Restart the server after changing startup-only configuration values.

### Updating an existing world

1. Back up the world, `config/hbm.cfg`, `config/hbmConfig/`, and `config/hbmRecipes/`.
2. Read the changelog for world generation, dimension, recipe, machine, and registry changes.
3. Replace the jar.
4. Start once in a test copy and check the log for missing mappings or config errors.
5. Only update the production world after the test copy loads correctly.

## Dependencies and compatibility

### Required

- Minecraft **1.7.10**
- Minecraft Forge **1.7.10-10.13.4.1614** or compatible Forge 1.7.10 runtime
- Java **8**

### Included or build-time APIs

The source build declares CodeChickenCore, CodeChickenLib, NotEnoughItems, Inventory Tweaks, and OpenComputers API dependencies. Pack authors should test their exact modpack combination because legacy 1.7.10 dependency resolution can vary by launcher and repository availability.

### Runtime identity API

Integration mods can distinguish Ragex Nuclear Tech from regular HBM by reflectively checking for `com.hbm.api.RTMApi`. This stable, server-safe API does not depend on client-only code or initialize RTM internals just to read identity constants. It exposes `MOD_FAMILY = "HBM"`, `MOD_VARIANT = "RTM"`, `MOD_NAME = "Ragex Nuclear Tech"`, `API_VERSION = 1`, and simple `isRTM()`, `getVariant()`, and `getApiVersion()` methods.

### Client and server requirements

- The mod must be installed on **both client and server** for multiplayer.
- Clients need the same mod jar as the server unless the server explicitly documents otherwise.
- Client-only commands and visual configuration are only available on clients.
- Server-side configuration, world generation, recipes, dimensions, and commands are controlled by the server.

### Known platform warning

The code contains a startup guard for Thermos/fork servers because Thermos tile-entity optimizations can break machine ticking. If you intentionally run Thermos, review the generated `hbm.cfg` Thermos option and the server's `tileentities.yml` before disabling the guard.

## Configuration overview

RNTM generates several configuration locations after first launch:

| Path | Purpose |
| --- | --- |
| `config/hbm.cfg` | Main Forge configuration for general toggles, worldgen, nukes, machines, mobs, radiation, dimensions, pollution, and weapons. |
| `config/hbmConfig/hbmClient.json` | Client display, HUD, tooltip, recoil, render, and debug options. Can be edited in game with `/ntmclient`. |
| `config/hbmConfig/hbmMachines.json` | Dynamically generated machine values for configurable tile entities. |
| `config/hbmConfig/hbmCustomMachines.json` | Custom multiblock machine definitions. |
| `config/hbmConfig/hbmFluidTraits.json` | Fluid behavior traits such as heat, cooling, pollution, radiation, corrosion, and fuel behavior. |
| `config/hbmConfig/hbmFallout.json` | Fallout block-transformation rules. A `_hbmFallout.json` template is generated until enabled. |
| `config/hbmConfig/hbmItemPools.json` | Loot/item pool overrides. A `_hbmItemPools.json` template is generated until enabled. |
| `config/hbmRecipes/` | JSON recipe templates and optional recipe overrides. Remove the leading underscore from a template filename to make it active. |

Important gameplay toggles include:

- `2.0RTM` in `hbm.cfg`: enables or disables nuclear warfare, primarily useful for servers.
- `enable528Mode`: enables the current 528 progression/balance mode.
- `enableLessBullshitMode`: easier progression mode; forced off when 528 mode is enabled.
- `1.42_threadedAtmospheres`: moves atmosphere processing to a separate thread for performance.
- `6.XX_enableChunkLoading`: allows procedural explosions to keep the central chunk loaded.
- Dimension IDs and biome IDs in the dimensions/biome categories.

See [docs/configuration.md](docs/configuration.md) for a fuller configuration guide.

## Commands

The server registers these commands:

| Command | Purpose |
| --- | --- |
| `/ntmreload` | Reloads serializable recipes and item pools. |
| `/ntmloadchunk <x> <z>` | Debugs tile entities in an unloaded chunk using block coordinates. |
| `/ntmsatellites orbit|descend|list` | Manages launched satellites. |
| `/ntmrad clear` and `/ntmrad set <amount>` | Clears or sets chunk radiation. |
| `/ntmstations launch|tp|list|fetch` | Manages orbital station drives and station teleportation. |
| `/ntmenablenukes true|false` | Enables or disables nuclear warfare at runtime. |
| `/ntmenablenukes schedule true|false yyyy-MM-dd HH:mm` | Schedules a nuke toggle for a local server time. |
| `/hbmbedrockdrop add|remove|list|clear` | Edits the in-memory bedrock excavator drop list. |

Clients also register:

| Command | Purpose |
| --- | --- |
| `/ntmclient help|list|reload|get|set` | Views and edits client JSON variables. |
| `/dumpthreadsandcrashgame dump|crash` | Logs a thread dump and optionally exits the client. Debug use only. |

See [docs/commands.md](docs/commands.md) for syntax, examples, and cautions.

## Getting started

New players should start with [docs/getting-started.md](docs/getting-started.md). In short:

1. Use JEI/NEI-style recipe lookup where available, because the mod has many machines and intermediate materials.
2. Explore for ores and early structures, then build a basic ore-processing line.
3. Learn radiation protection before handling fuels, waste, and reactor components.
4. Treat nuclear devices, high-radiation materials, and space systems as late-game content.
5. Server operators should review configs before generating a long-term world.

## Troubleshooting and FAQ

### The game crashes or hangs on first launch.

Confirm you are using Minecraft 1.7.10, Java 8, and a compatible Forge 1.7.10 runtime. If running a legacy server fork such as Thermos, review the Thermos warning above.

### Recipes or loot edits are ignored.

Recipe templates in `config/hbmRecipes/` are written with a leading underscore. Remove the underscore from the filename to enable that file. Item pools and fallout rules also use underscore-prefixed templates until you create the active JSON file.

### Clients cannot join my server.

Make sure all clients and the server use the same RNTM jar and compatible dependencies. Config differences that change registries, dimensions, or recipes can also cause issues.

### How do I disable nukes on a server?

Set `2.0RTM=false` in `config/hbm.cfg` before startup, or use `/ntmenablenukes false` at runtime. Use `/ntmenablenukes schedule false yyyy-MM-dd HH:mm` for a scheduled runtime change.

### Can I remove worldgen after a world already exists?

You can disable future generation, but already-generated chunks keep their blocks and structures. For major worldgen changes, start a new world or pre-generate carefully.

## Building from source

Requirements:

- Java 8 JDK
- Git
- Network access to ForgeGradle and legacy Maven repositories

```bash
git clone <this-repository-url>
cd Ragexs-Nuclear-Tech-GIT
./gradlew build
```

The built jar is written to `build/libs/` with the `RX-RNTM` archive base name.

For an IDE workspace:

```bash
./gradlew setupDecompWorkspace
./gradlew eclipse
```

## Documentation map

- [Getting Started](docs/getting-started.md)
- [Space Travel Guide](docs/space-travel.md)
- [What Changed from Original NTM](docs/upstream-changes.md)
- [Configuration Guide](docs/configuration.md)
- [Command Reference](docs/commands.md)
- [Server Administration Guide](docs/server-admin.md)
- [Documentation Audit](docs/documentation-audit.md)

## Contributing

Contributions should keep documentation aligned with source code. Before documenting a command, config key, or feature, verify it in the current codebase. For code changes, use Java 8-compatible syntax and test with the Gradle build where possible.

## License and credits

See [LICENSE](LICENSE), [LICENSE.LESSER](LICENSE.LESSER), Forge license files, and [gradle.properties](gradle.properties) credits for licensing and contributor information.

# Development mods

External mods used only while developing can be placed in `devmods/` instead of being copied to
`eclipse/mods` manually:

- Put ordinary production/SRG mod JARs in `devmods/`. The `prepareDevMods` task remaps them to MCP
  names under `build/devmods/remapped/` before `runClient` starts.
- Put JARs that are already built for an MCP development workspace in `devmods/deobf/`. They are
  loaded directly and are not remapped again.

Both locations are runtime-only and are excluded from the release JAR. Local JARs in these folders
are ignored by Git; the tracked `.gitkeep` files preserve the directory layout.
