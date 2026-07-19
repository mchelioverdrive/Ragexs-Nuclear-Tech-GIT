# Getting Started with Ragex's Nuclear Tech Mod

This guide is for players joining a world or modpack that includes Ragex's Nuclear Tech Mod (RNTM). It avoids hidden implementation details and focuses on practical survival play.

## Before you start

RNTM is a large 1.7.10 technology mod, but it should not be approached as a drop-in copy of original HBM's Nuclear Tech Mod. Progression, recipes, names, reactors, radiation, space, and removed fantasy features can differ because this fork is built around more realistic or plausible counterparts. It expects you to use recipe lookup and to learn systems gradually. The mod includes dangerous blocks, items, fluids, machines, radiation, explosions, and space content; do not treat every item as safe to hold or place.

Recommended setup:

- Minecraft 1.7.10 with Forge.
- RNTM installed on both client and server.
- NotEnoughItems or the recipe viewer bundled in your pack, if available.
- Backups before testing explosives, reactors, or config changes.

## Early-game priorities

1. **Find ores and surface resources.** RNTM adds many ore types and generated resources. Mine broadly and keep unfamiliar ores instead of discarding them.
2. **Build basic processing.** Prioritize machines that multiply ore output, make plates/circuits, and unlock chemical or metallurgical steps.
3. **Organize intermediates.** The mod uses many dusts, ingots, plates, fluids, fuel products, and machine components. Dedicated storage prevents progression stalls.
4. **Read tooltips.** Many items expose hazard, radiation, recipe, or machine information through tooltips.
5. **Avoid late-game hazards early.** Do not handle radioactive waste, reactor fuel, high explosives, or nuclear devices without understanding consequences.

## Mid-game progression themes

RNTM progression is less about a single linear quest and more about connected real-industry systems:

- **Ore processing and bedrock resources** feed metallurgy and machine crafting; bedrock pumping provides a fallback renewable oil/gas trickle for custom maps, while the Large Mining Drill can extract configured resources from bedrock.
- **Blast furnace work** matters earlier than many original-NTM players may expect.
- **Chemistry and refining** unlock fuels, acids, plastics, isotope work, and advanced materials.
- **Power generation** scales from basic generators toward reworked nuclear options such as MSR, PBR, RBMK/PWR-style systems, and research reactors.
- **Radiation protection and treatment** become mandatory around fuels, waste, fallout, and reactor incidents.
- **Space infrastructure** requires specialized items, destinations, atmosphere awareness, and station/satellite systems.

Use recipe lookup to work backward from a target machine or item. When a material is unfamiliar, search for both its item form and its ore/fluid equivalents.

## Radiation basics

Radiation is a persistent environmental and item hazard. Practical rules:

- Keep radioactive materials contained and away from living areas.
- Use protective equipment before working with fuel, waste, fallout, or contaminated zones.
- Do not store radioactive materials in common player inventory unless you know the shielding behavior.
- Server administrators can clear or set chunk radiation with `/ntmrad`, but players should assume radiation is persistent unless cleaned up.

## Grenade use

Grenades are single-item stacks. Hold the use button to draw back a throw—the same pullback animation used by bows appears while charging—then release it. Throw power follows the standard bow draw curve, so a longer hold produces a faster, farther throw up to the normal grenade throw speed. Very brief taps do not release a grenade.

## Nuclear safety basics

Nuclear systems are powerful but punishing:

- Test reactor designs in a creative copy before using them on a server.
- Keep backups before first startup of a new reactor design.
- Monitor heat, coolant, fuel, waste, and output products.
- Put reactors away from bases and spawn until the design is proven.
- Treat meltdown, blast, fallout, and biome damage as real world-changing risks.

## Steam turbine safety

Steam turbines require a clear route for both incoming steam and spent-steam exhaust. Both standard and Industrial Steam Turbines rupture in a destructive overpressure explosion if their input and output tanks are both completely full. Keep the exhaust connected to a condenser, cooling tower, or sufficient storage, and do not allow the steam supply to keep filling a blocked turbine.

## Space basics

The mod includes celestial bodies, orbit logic, satellites, and orbital stations. Practical advice:

- Confirm the server's configured dimension IDs before adding other dimension mods.
- Use station/satellite commands only if you are an operator or the server rules allow it.
- Keep backup travel items or admin support available when testing station teleportation.
- Read the dedicated [Space Travel Guide](space-travel.md) before committing rare rocket hardware to a launch.
- Remember that atmosphere and water-table behavior can differ by body.

## Multiplayer etiquette

Because RNTM can damage worlds, ask before:

- Detonating explosives or nukes.
- Starting an untested reactor.
- Launching missiles.
- Creating large pollution/radiation sources.
- Editing recipes, item pools, machine configs, or dimension IDs.

## Related projects

For other Minecraft versions, use separate projects rather than this repository:

- NTM Reloaded for 1.12: <https://github.com/TheOriginalGolem/Hbm-s-Nuclear-Tech-GIT/releases>
- NTM Extended Edition for 1.12: <https://github.com/Alcatergit/Hbm-s-Nuclear-Tech-GIT/releases>
- Nuclear Tech Mod Remake for 1.18: <https://codeberg.org/MartinTheDragon/Nuclear-Tech-Mod-Remake/releases>


## Carbon monoxide warnings

Machines that burn fuel or handle exhaust can produce carbon monoxide while running. This includes fireboxes, heating ovens, and fluid burners when they are consuming fuel. Read machine tooltips for the carbon monoxide warning and ventilate enclosed work areas before operating those machines. Steel grates, item grates, and air vents can help vent carbon monoxide from enclosed spaces.
