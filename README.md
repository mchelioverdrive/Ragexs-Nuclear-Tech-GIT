# Ragex's Nuclear Tech Mod

Ragex's Nuclear Tech Mod (**RTM**) is a realism-focused fork of **HBM's Nuclear Tech Mod (NTM)** for **Minecraft Forge 1.7.10**. It is an industrial and nuclear mod with chemistry, engineering, and science-fiction progression built around the infrastructure behind the technology: extracting resources, processing materials, producing energy, manufacturing equipment, and managing the consequences.

RTM uses real science and industrial engineering as its foundation, with room for plausible science-fiction when it makes interesting gameplay. Its aim is to make industrial chemistry, metallurgy, nuclear reactors, radiation, energy generation, resource processing, and space technology understandable through building and operating interconnected systems. It is a progression overhaul, not simply a larger catalogue of machines and items.

> **Supported platform:** Minecraft 1.7.10 with Forge. The source targets Forge **10.13.4.1614** and Java **8**.

## Relationship to HBM / NTM

RTM retains the recognizable large-scale engineering and nuclear sandbox of [HBM's Nuclear Tech Mod](https://github.com/HbmMods/Hbm-s-Nuclear-Tech-GIT). Reactors, chemical plants, industrial facilities, strategic weapons, environmental hazards, and space infrastructure remain central to the experience.

The difference is what connects those systems. RTM is interested in making the technology behind the machines matter: the fuel supply, material preparation, chemical feedstocks, manufacturing capability, cooling, and power needed to operate them. Players who enjoy building a supporting industrial economy alongside their nuclear facilities are the intended audience.

For players familiar with HBM, this is still recognizably Nuclear Tech Mod, but recipes, machines, power balance, industrial dependencies, and progression can differ substantially. Upstream behavior is a starting point rather than an automatic design requirement. Systems may be rebalanced, replaced, restored, expanded, or reinterpreted where that produces a more coherent technological progression.

## Performance and large factories

Performance is a major part of RTM's work over original HBM. The mod encourages refineries, reactor complexes, storage networks, processing plants, and other installations containing hundreds of machines, so those systems should not become unnecessarily expensive simply because the factory grows.

Much of original HBM's machinery was built around ordinary TileEntity update loops. Machines could repeatedly inspect recipes, inventories, fluids, connections, upgrades, or synchronization state every server tick even when little or nothing had changed.

RTM has extensively reworked that architecture. Of the **205 concrete TileEntities in the production-machine census, 186 now use event-driven, scheduled, or bounded coarse runtime strategies** rather than the legacy default execution path. Stable production can advance from elapsed loaded time and wake at meaningful resource or completion boundaries instead of requiring every recipe to be reconsidered every tick.

Related optimization work includes:

- persistent power-network endpoints and dirty topology updates instead of repeatedly rediscovering network membership;
- reduced temporary allocation and repeated traversal in power and fluid networks;
- cached recipe, upgrade, geometry, and machine state where a reliable invalidation path exists;
- dirty-state synchronization so supported machines avoid constructing and broadcasting unchanged packets every tick;
- shared coarse polling for compatibility or environmental state that cannot yet provide a direct change event;
- explicit lifecycle handling so scheduled work does not continue through unloaded, removed, or replaced machines.

RTM does **not** claim a universal TPS or percentage performance improvement from these changes. Active reactors, thermal systems, fluid transfer, world interaction, and other genuinely continuous simulations still perform work at the cadence their mechanics require.

The goal is instead structural: **idle and unchanged machinery should do as little work as practical, while active machinery preserves the simulation it actually needs.** Performance audits continue to identify remaining legacy hot paths as the project grows.

## Progression philosophy

Progression should increasingly follow **capability**: acquiring the actual infrastructure, materials, energy, processing equipment, and environmental conditions needed for a technology. A new machine should become possible because your industry can build and run it, rather than because an unrelated crafting gate has been passed.

This direction connects several parts of the mod:

- **Metallurgy:** better steel production starts with suitable furnaces and prepared coke, with hot air and oxygen infrastructure as a direction for further development. Current blast-furnace recipes already give coke an advantage over coal for iron-ore processing; they do not yet model a complete hot-blast steelworks.
- **Chemical production:** increasingly demanding processes should require appropriate separation, reaction, thermal, or controlled-processing equipment. Dedicated distillation, cracking, hydrotreating, electrolysis, and other machines already coexist with the general chemical reactor; further specialization remains part of the direction.
- **Power:** early heat engines, steam, and combustion should lead naturally into larger industrial generation and eventually nuclear power. Electrical demand and available generation capacity should affect which machines a factory can operate together.
- **Early technology:** primitive recovery and workshop equipment should remain useful for getting started, while becoming intentionally inefficient in yield, throughput, or fuel use once proper industrial replacements are available.
- **Materials and nuclear technology:** high-performance materials and reactor equipment should depend on metallurgy, chemistry, separation, enrichment, fabrication, and controlled processing. Nuclear progression should grow out of the supporting industrial economy rather than function as an isolated tech tree.

The goal is meaningful engineering decisions, not complexity for its own sake. Repeated inconvenience or grind without a physical or technological purpose should be removed or avoided.

**This is a design direction, not a claim that every process is fully simulated.** The current project combines reworked systems with inherited mechanics and deliberate abstractions. A recipe may represent several industrial stages when there is not yet a worthwhile dedicated machine or mechanic. Grounded science-fiction remains part of the mod, and some reactor and material systems still use simplified or nominal models.

## Technology and gameplay

### Nuclear engineering and the fuel cycle

The repository includes research reactors, RBMK and PWR systems, a gas-cooled Magnox reactor, a helium-cooled Pebble Bed Reactor, and a Molten Salt Reactor. Nuclear technology includes isotope production, gas-centrifuge and SILEX enrichment, fuel fabrication, breeding, spent-fuel cooling, reprocessing, and waste conditioning.

Fuel choice, heat removal, control systems, and waste handling connect reactor operation to the rest of the factory. Accidents and meltdowns can release radiation and damage the world; reactor behavior and balance vary by design and configuration.

### Industrial chemistry, oil, and fuels

Chemical production connects acids, gases, polymers, solvents, mineral processing, and nuclear feedstocks. The general chemical reactor works alongside electrolysers, centrifuges, crystallizers, and specialized petroleum equipment for refining, fractionation, cracking, hydrotreating, reforming, and coking.

Current petroleum processing includes declared feed and product compositions, material accounting, process heat, and electrical work. Oil extraction, coal and biomass routes, fuel blending, and sulfur recovery supply an industrial fuel economy. Tanks, pipes, pumps, and gas-handling equipment connect production to storage and consumers. Some multistage chemistry is intentionally compressed into the available equipment.

### Metallurgy, material processing, and fabrication

Primitive furnaces and anvils provide early recovery and fabrication routes. Blast furnaces, coke production, crucibles and casting, rotary and arc furnaces, presses, welding, soldering, and assembly support larger manufacturing chains. Alloy preparation, ore processing, and component fabrication connect raw resources to industrial machinery and reactor construction.

### Mining and resource extraction

Ore processing, drilling, excavation, oil wells, and bedrock resource systems support bulk production. Bedrock oil/gas fallback extraction and configurable mining outputs also provide options for custom maps that lack ordinary deposits. Resource availability depends on world generation, terrain, celestial location, and pack configuration.

### Heat, steam, and electrical power

Heat sources, Stirling engines, boilers, steam engines and turbines, combustion generators, gas turbines, and nuclear systems provide different scales of generation. Steam infrastructure includes feedwater, exhaust handling, condensation, heat rejection, and overpressure protection. Electrical networks connect generation, storage, and machine demand, with RF conversion equipment for compatible external systems.

Reworked heat, fossil-fuel, and steam systems account for fuel energy, conversion efficiency, and rated output, while industrial consumers have operating power requirements. Machine information exposes ratings and operating conditions where supported. This does not mean every inherited generator or nuclear heat model has received the same physical calibration.

### Radiation and environmental hazards

Radioactive materials, contaminated areas, fallout, and nuclear detonations create persistent risks. Protection, monitoring, treatment, cleanup, and waste storage matter alongside production. Industrial hazards include toxic gases, carbon monoxide, pollution, corrosive fluids, and dangerous temperatures or pressures. Ventilation and environmental conditions can affect whether equipment operates safely.

### Space technology and long-term progression

Rocket fabrication and launch infrastructure connect the industrial base to planets, moons, gas giants, satellites, and orbital stations. Celestial bodies have atmosphere and environmental rules that affect survival and machinery. Propellant production, manufacturing, life support, and power supply make space technology a continuation of the factory's development. See the [Space Travel Guide](docs/space-travel.md).

### Strategic weapons and defensive systems

Nuclear devices, missiles, explosives, firearms, radar, and CIWS coexist with the industrial systems that supply them. Warfare is configurable: server operators can control nuclear use, schedule enablement changes, and manage radiation, satellites, and stations. These tools support multiplayer worlds without making warfare a requirement for industrial progression.

### Servers and pack customization

Forge configuration controls world generation, radiation, dimensions, machinery, weapons, and other gameplay systems. JSON support covers serializable recipes, configurable machine values, custom multiblock machines, fluid traits, fallout transformations, and loot pools. Pack makers can adapt progression and server rules within those supported interfaces; converted physical processes require valid material and energy definitions rather than arbitrary quantity overrides.

## Download links and resources

- **Downloads:** [Modrinth](https://modrinth.com/mod/ragexs-nuclear-tech) and [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ragexs-nuclear-tech).
- **Source and support:** [repository](https://github.com/mchelioverdrive/Ragexs-Nuclear-Tech-GIT), [releases](https://github.com/mchelioverdrive/Ragexs-Nuclear-Tech-GIT/releases), and [issues](https://github.com/mchelioverdrive/Ragexs-Nuclear-Tech-GIT/issues).
- **Original NTM wiki:** [Nuclear Tech Wiki](https://nucleartech.wiki/wiki/Main_Page).
- **Original NTM / related projects:** see [docs/getting-started.md](docs/getting-started.md#related-projects).

The original NTM wiki is useful for inherited mechanics. For RTM-specific behavior, check the recipes, tooltips, generated configuration, and documentation for the release you use. Source development can be ahead of published downloads, and older guides may describe earlier balance or abstractions.

## Installation

### Client installation

1. Install **Minecraft 1.7.10**.
2. Install **Minecraft Forge 1.7.10-10.13.4.1614** or a compatible Forge 1.7.10 build.
3. Download the RTM jar from Modrinth, CurseForge, or a GitHub release.
4. Put the jar in your `.minecraft/mods` folder.
5. Start the game once so configuration files can generate.
6. Optional but recommended: install NotEnoughItems for recipe browsing.

### Dedicated server installation

1. Install a Forge 1.7.10 dedicated server.
2. Put the same RTM jar in the server `mods` folder.
3. Start the server once, then stop it after configuration files generate.
4. Review `config/hbm.cfg`, `config/hbmConfig/*.json`, and `config/hbmRecipes/` templates before opening the world to players.
5. Restart the server after changing startup-only configuration values.

### Updating an existing world

1. Back up the world, `config/hbm.cfg`, `config/hbmConfig/`, and `config/hbmRecipes/`.
2. Read [CHANGELOG.md](CHANGELOG.md) for world generation, dimension, recipe, machine, and registry changes.
3. Replace the jar.
4. Start once in a test copy and check the log for missing mappings or config errors.
5. Only update the production world after the test copy loads correctly.

## Dependencies and compatibility

### Required

- Minecraft **1.7.10**
- Minecraft Forge **1.7.10-10.13.4.1614** or compatible Forge 1.7.10 runtime
- Java **8**

### Optional integrations and development dependencies

NotEnoughItems (NEI) provides recipe browsing and machine information. The source contains integration for maintained GTNH NEI variants, including handler and recipe-catalyst registration. Angelica compatibility paths handle client rendering and prepared-model GPU access. Pack developers should test their selected versions and rendering setup together.

OpenComputers integrations expose supported machine controls and information. Inventory Tweaks API support is also present. Install optional mods with their own required dependencies.

The development build declares CodeChickenCore, CodeChickenLib, and NEI dependencies, the OpenComputers API, and a compile-only Inventory Tweaks API. These build declarations are not a list of mods every player must install separately.

### Client and server requirements

- The mod must be installed on **both client and server** for multiplayer.
- Clients need the same mod jar as the server unless the server explicitly documents otherwise.
- Client-only commands and visual configuration are only available on clients.
- Server-side configuration, world generation, recipes, dimensions, and commands are controlled by the server.

### Known platform warning

The code contains a startup guard for Thermos/fork servers because Thermos tile-entity optimizations can break machine ticking. If you intentionally run Thermos, review the generated `hbm.cfg` Thermos option and the server's `tileentities.yml` before disabling the guard.

## Configuration overview

RTM generates several configuration locations after first launch:

| Path | Purpose |
| --- | --- |
| `config/hbm.cfg` | Main Forge configuration for general toggles, worldgen, nukes, machines, mobs, radiation, dimensions, pollution, and weapons. |
| `config/hbmConfig/hbmClient.json` | Client display, HUD, tooltip, recoil, render, and debug options. Can be edited in game with `/ntmclient`. |
| `config/hbmConfig/hbmMachines.json` | Dynamically generated machine values for configurable tile entities. |
| `config/hbmConfig/hbmCustomMachines.json` | Custom multiblock machine definitions. |
| `config/hbmConfig/hbmFluidTraits.json` | Fluid behavior traits such as heat, cooling, pollution, radiation, corrosion, and fuel behavior. A `_hbmFluidTraits.json` template is generated until enabled. |
| `config/hbmConfig/hbmFallout.json` | Fallout block-transformation rules. A `_hbmFallout.json` template is generated until enabled. |
| `config/hbmConfig/hbmItemPools.json` | Loot/item pool overrides. A `_hbmItemPools.json` template is generated until enabled. |
| `config/hbmRecipes/` | JSON recipe templates and optional recipe overrides. Remove the leading underscore from a template filename to make it active. |

Important gameplay toggles include:

- `2.0RTM` in `hbm.cfg`: enables or disables nuclear warfare, primarily useful for servers.
- `enable528Mode`: selects the 528 progression/balance mode; enabled by default.
- `enableLessBullshitMode`: optional easier progression mode; disabled by default and forced off when 528 mode is enabled.
- `1.42_threadedAtmospheres`: moves atmosphere processing to a separate thread for performance.
- `6.XX_enableChunkLoading`: allows procedural explosions to keep the central chunk loaded.
- Dimension IDs and biome IDs in the dimensions/biome categories.

See [docs/configuration.md](docs/configuration.md) for a fuller configuration guide.

The inherited `hbm` mod ID and configuration names remain in use. RTM and upstream HBM are alternative versions of that mod, so do not install both in the same instance.

## Commands

The server registers these commands:

| Command | Purpose |
| --- | --- |
| `/ntmreload` | Reloads serializable recipes and item pools. |
| `/rntachievement list` and `/rntachievement unlock <player> <achievementId>` | Lists RNT achievement IDs or awards one to an online player. |
| `/ntmloadchunk <x> <z>` | Debugs tile entities in an unloaded chunk using block coordinates. |
| `/ntmsatellites orbit\|descend\|list` | Manages launched satellites. |
| `/ntmrad clear` and `/ntmrad set <amount>` | Clears or sets chunk radiation. |
| `/ntmstations launch\|tp\|list\|fetch` | Manages orbital station drives and station teleportation. |
| `/ntmenablenukes true\|false` | Enables or disables nuclear warfare at runtime. |
| `/ntmenablenukes schedule true\|false yyyy-MM-dd HH:mm` | Schedules a nuke toggle for a local server time. |
| `/ntmenableredstonegroundnukes true\|false` | Controls redstone detonation of placed nuclear bomb blocks; nuclear warfare must also be enabled. |
| `/hbmbedrockdrop add\|remove\|list\|clear` | Edits the in-memory bedrock excavator drop list. |
| `/ntmmachinestats` | Reports machine runtime statistics for the sender's world. |
| `/ntmpowerstats` | Reports electrical-network diagnostics. |

Clients also register:

| Command | Purpose |
| --- | --- |
| `/ntmclient help\|list\|reload\|get\|set` | Views and edits client JSON variables. |
| `/dumpthreadsandcrashgame dump\|crash` | Logs a thread dump and optionally exits the client. Debug use only. |

See [docs/commands.md](docs/commands.md) for syntax, examples, and cautions.

Warfare command toggles and scheduled changes are runtime settings. Use `hbm.cfg` for the nuclear-warfare startup default.

## Getting started

The [Getting Started guide](docs/getting-started.md) covers survival mechanics and hazards. A practical starting approach is:

1. Use NEI and machine tooltips to check the recipes, power requirements, and hazards in your release or pack.
2. Explore for resources and establish primitive recovery, furnace work, and basic fabrication.
3. Build fuel preparation and dependable heat or power before expanding into continuous processing. Plan generation for the machines you intend to run together.
4. Connect metallurgy, chemical feedstocks, cooling, and manufacturing before scaling into reactors or space equipment. Learn radiation protection before handling radioactive fuel or waste.
5. Server operators should review configuration before generating a long-term world.

## Troubleshooting and FAQ

### The game crashes or hangs on first launch.

Confirm you are using Minecraft 1.7.10, Java 8, and a compatible Forge 1.7.10 runtime. If running a legacy server fork such as Thermos, review the Thermos warning above.

### Recipes or loot edits are ignored.

Recipe templates in `config/hbmRecipes/` are written with a leading underscore. Remove the underscore from the filename to enable that file. Item pools and fallout rules also use underscore-prefixed templates until you create the active JSON file.

### Clients cannot join my server.

Make sure all clients and the server use the same RTM jar and compatible dependencies. Config differences that change registries, dimensions, or recipes can also cause issues.

### How do I disable nukes on a server?

Set `2.0RTM=false` in `config/hbm.cfg` before startup, or use `/ntmenablenukes false` at runtime. Use `/ntmenablenukes schedule false yyyy-MM-dd HH:mm` for a scheduled runtime change.

### Can I remove worldgen after a world already exists?

You can disable future generation, but already-generated chunks keep their blocks and structures. For major worldgen changes, start a new world or pre-generate carefully.

## Development

### Building from source

Requirements:

- A full Java 8 JDK, with `JAVA_HOME` pointing to it
- Git
- Network access to ForgeGradle and legacy Maven repositories

```bash
git clone https://github.com/mchelioverdrive/Ragexs-Nuclear-Tech-GIT.git
cd Ragexs-Nuclear-Tech-GIT
./gradlew build
```

The built jar is written to `build/libs/` with the `RX-RNTM` archive base name.

On Windows, use `gradlew.bat build`; the corresponding IDE setup commands are `gradlew.bat setupDecompWorkspace` and `gradlew.bat eclipse`. Use the supplied Gradle wrapper and existing ForgeGradle configuration.

For an IDE workspace:

```bash
./gradlew setupDecompWorkspace
./gradlew eclipse
```

Launch the development client with `./gradlew runClient` or, on Windows, `gradlew.bat runClient`.

### Development mods

External mods used only while developing can be placed in `devmods/` instead of being copied to
`eclipse/mods` manually:

- Put ordinary production/SRG mod JARs in `devmods/`. The `prepareDevMods` task remaps them to MCP
  names under `build/devmods/remapped/` before `runClient` starts.
- Put JARs that are already built for an MCP development workspace in `devmods/deobf/`. They are
  loaded directly and are not remapped again.

Both locations are runtime-only and are excluded from the release JAR. Local JARs in these folders
are ignored by Git; the tracked `.gitkeep` files preserve the directory layout.

`runClient` also copies ForgeGradle's generated `fields.csv` and `methods.csv` into the repository's
local `mcp/` directory. Legacy coremods such as Hardcore Darkness 1.7.10 look for those CSV files at
`../mcp/` relative to the `eclipse` run directory. The generated directory is ignored by Git and is
not included in release artifacts.

### Runtime identity API

Integration mods can distinguish RTM from regular HBM by reflectively checking for `com.hbm.api.RTMApi`. This stable, server-safe API does not depend on client-only code or initialize RTM internals just to read identity constants. It exposes `MOD_FAMILY = "HBM"`, `MOD_VARIANT = "RTM"`, `MOD_NAME = "Ragex Nuclear Tech"`, `API_VERSION = 1`, and simple `isRTM()`, `getVariant()`, and `getApiVersion()` methods.

### Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Keep changes focused and preserve supported Minecraft 1.7.10 APIs, save compatibility, and server authority. Use Java 8-compatible syntax, build code changes with the existing tooling, and test gameplay changes on both client and dedicated server. Optional integrations should also be checked with the other mod absent.

Contributions should keep documentation aligned with source code. Verify commands, config keys, recipes, and feature claims against the current implementation, and record meaningful completed changes in [CHANGELOG.md](CHANGELOG.md).

## Documentation map

- [Getting Started](docs/getting-started.md)
- [Space Travel Guide](docs/space-travel.md)
- [Steam Turbine Guide](docs/steam-turbines.md)
- [What Changed from Original NTM](docs/upstream-changes.md)
- [Configuration Guide](docs/configuration.md)
- [Command Reference](docs/commands.md)
- [Server Administration Guide](docs/server-admin.md)
- [Changelog](CHANGELOG.md)

Developer references include the [RealSim implementation and design notes](docs/REALSIM_PHASES_1_4.md), [physical ledger](docs/REALSIM_PHYSICAL_LEDGER.md), [production design proposal](docs/REALSIM_PRODUCTION.md), and [documentation audit](docs/documentation-audit.md). Proposals and historical audits describe their own scope; they are not a list of features guaranteed in every release.

## License and credits

RTM builds on HBM's Nuclear Tech Mod and the work of its contributors. Project and contributor credits are recorded in [gradle.properties](gradle.properties). See [LICENSE](LICENSE) and [LICENSE.LESSER](LICENSE.LESSER) for the repository's GPLv3 and LGPLv3 license texts, and retain applicable component notices when redistributing.

Forge and FML notices are provided in [MinecraftForge-License.txt](MinecraftForge-License.txt), [MinecraftForge-Credits.txt](MinecraftForge-Credits.txt), [LICENSE-fml.txt](LICENSE-fml.txt), and [CREDITS-fml.txt](CREDITS-fml.txt).
