# Ragex's Nuclear Tech Mod

Ragex's Nuclear Tech Mod (**RTM**) is a gameplay and performance rework of [HBM's Nuclear Tech Mod](https://github.com/HbmMods/Hbm-s-Nuclear-Tech-GIT) for **Minecraft Forge 1.7.10**. It rebuilds familiar Nuclear Tech systems around fuel chemistry, industrial processing, meaningful plant requirements and more involved nuclear operation.

Choose RTM if you want the supporting industry to matter: preparing the right fuel, recovering refinery coproducts, making suitable alloys, supplying process heat, selecting a turbine for its steam conditions, and keeping a reactor cooled after shutdown. RTM retains HBM's scale, hazards, weapons and science-fiction technology while changing how much of that industry works.

## What RTM changes from HBM

These are changes to the inherited systems, rather than a list of everything Nuclear Tech already provides.

**A rebuilt fuel and power economy.** RTM ties fuel consumption to chemical energy and equipment efficiency across burners and generators. Diesel, gasoline, aviation fuel, heating oil and process gases have distinct applications. Starter Stirling, piston generation and central gas plants serve different factory scales. Gas-turbine exhaust can feed a working combined-cycle steam plant, with feed pumping and cooling counted in its useful output. Isotope generators depend on source heat and decay rather than identical perpetual output from different sources. See [Power generation](docs/gameplay/power_generation.md).

**Steam plants with meaningful equipment differences.** Steam grades have defined temperature, pressure and water content. The compact Small Steam Turbine handles ordinary/hot steam; industrial equipment handles hotter, higher-flow service. Turbines discharge to condensation, boilers need makeup water, and finite condenser capacity can constrain the plant. Electrical output depends on supplied heat and flow rather than turbine size alone. See [Steam and heat](docs/gameplay/steam_and_heat.md).

**Reworked petroleum and chemical production.** Refining, cracking, hydrotreating, reforming and coking now require suitable feeds, process heat, electrical work and handling for their gas, water and solid coproducts. Sulfur recovery, hydrogen production, coal liquids and biological fuels connect to that plant. Chemistry distinguishes raw/treated/reactor water, warm gases from cryogenic liquids, acid concentrations, molten salts and prepared intermediates. Expanded mineral, polymer and specialist chains replace many direct conversion shortcuts. See [Petroleum](docs/gameplay/petroleum.md) and [Chemistry](docs/gameplay/chemistry.md).

**More deliberate metallurgy and material use.** The inexpensive Metallurgical Furnace supplies first steel and small alloys; the separate coke-fired Blast Furnace supplies bulk steel with forced air, hot blast, optional limestone/oxygen and recoverable top gas. Material and recipe corrections distinguish oxides from metals, coke grades from graphite, tin bronze from arsenic bronze, and solder from electronic components. Rare materials keep specialist roles instead of becoming universal substitutes. Welding, forming, soldering and assembly connect those materials to equipment. See [Materials](docs/gameplay/materials.md) and [Manufacturing](docs/gameplay/manufacturing.md).

**Reactor and fuel-cycle changes.** RTM adds a Molten Salt Reactor, converts WATZ toward a Pebble Bed Reactor, restores research-reactor access in 528 mode and uses ReaSim RBMK operation by default. MAGNOX has separate core/coolant behavior, fuel-channel temperatures, moving shutdown rods, startup interlocks, pressure protection and retained decay heat. Enrichment, irradiation, cooled spent-fuel reprocessing and waste conditioning have revised connected routes. These are different operating systems, not just renamed reactor blocks. See [Nuclear industry](docs/gameplay/nuclear_industry.md).

**Changed hazards, exploration and pack support.** Radiation treatment and contamination handling are reworked, including medical treatments and accumulating fallout. Planetary/orbital behavior, additional celestial destinations and bedrock resource routes expand the exploration side. Server warfare controls and compatibility work for Angelica and maintained GTNH NEI make those systems more practical to configure and use. The [changes overview](docs/upstream-changes.md) covers these branches.

RTM's progression increasingly follows industrial capability: what your factory can manufacture and operate. Large parallel machines currently improve throughput; proposed fabrication eligibility restrictions remain [future design](docs/design/REALSIM_PRODUCTION.md). Practical multistage abstractions and deliberate science fiction remain part of the mod; not every process or reactor is a complete physical simulation.

## Performance and large factories

RTM also changes the architecture behind large factories. Much inherited machinery repeatedly checked recipes, inventories, connections and synchronization each tick. RTM migrates suitable machines to event-driven, scheduled or bounded coarse work; retains network endpoints instead of repeatedly rediscovering them; suppresses unchanged synchronization; caches recipe data; and shares prepared rendering geometry. Active simulations retain the cadence their mechanics require. These are substantial structural changes, without an unmeasured universal TPS or frame-rate claim. Contributors can read the [performance reference](docs/development/performance_reference.md).

## Installation

Use **Minecraft 1.7.10**, **Forge 10.13.4.1614** (or a compatible Forge 1.7.10 runtime) and **Java 8**. Install the RTM jar in the instance's `mods` folder on both client and server. Use matching builds. RTM and upstream HBM share the `hbm` identity and must not be installed together.

Back up existing worlds and configurations; test an update in a copy before replacing a production installation. NEI provides recipe lookup; optional mods need their own required dependencies. The project includes compatibility paths for maintained GTNH NEI, Angelica, OpenComputers and Inventory Tweaks; test your selected pack and renderer together. Development dependencies are not a list every player must install separately.

Thermos/fork servers have a startup guard because tile-entity optimizations can break machine operation. Review the generated Thermos setting and `tileentities.yml` before changing that guard.

## Playing and configuring

Start with [Getting started](docs/gameplay/getting_started.md) and the [documentation index](docs/index.md). System guides explain operating requirements and production choices without requiring the technical audit history.

Server settings control recipes, generation, dimensions, radiation and warfare. `config/hbm.cfg` is the main configuration; `hbmConfig/hbmMachines.json` holds machine values, and `hbmRecipes/` holds recipe templates/overrides. Underscore-prefixed templates are inactive until enabled. See [Configuration](docs/configuration.md), [Commands](docs/commands.md) and [Server administration](docs/server-admin.md).

For nuclear warfare, `2.0RTM=false` sets the startup default; `/ntmenablenukes false` changes it at runtime. Clients and servers need matching RTM builds. When an override appears ignored, confirm its active filename and inspect the loading log before changing recipes.

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


## Documentation

The [documentation index](docs/index.md) separates **gameplay guides**, **future design**, **current development references** and **historical audits**. Current references cover exact processes, fluids, materials, generation, thermal work and compatibility. Use [CHANGELOG.md](CHANGELOG.md) for completed change history.

## License and credits

RTM builds on HBM's Nuclear Tech Mod and the work of its contributors. Project and contributor credits are recorded in [gradle.properties](gradle.properties). See [LICENSE](LICENSE) and [LICENSE.LESSER](LICENSE.LESSER) for the repository's GPLv3 and LGPLv3 license texts, and retain applicable component notices when redistributing.

Forge and FML notices are provided in [MinecraftForge-License.txt](MinecraftForge-License.txt), [MinecraftForge-Credits.txt](MinecraftForge-Credits.txt), [LICENSE-fml.txt](LICENSE-fml.txt), and [CREDITS-fml.txt](CREDITS-fml.txt).
