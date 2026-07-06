# What Ragex's Nuclear Tech Changes from Original NTM

Ragex's Nuclear Tech Mod is a fork of HBM's Nuclear Tech Mod with a different design target. Original NTM is a broad chaotic tech sandbox with many references, jokes, and fantasy systems. RNTM keeps the scale and danger but redirects the mod toward grounded nuclear technology, industrial chemistry, realistic resource processing, and plausible science-fiction where strict realism would not fit Minecraft gameplay.

## Design direction

RNTM was made to provide:

- A stronger nuclear-engineering focus.
- More realistic or plausible replacements for fantasy/unrealistic systems.
- More chemistry, isotopes, industrial production, and periodic-table materials.
- Rebalanced progression that asks players to use real industrial steps earlier.
- More serious radiation, treatment, fallout, and contamination cleanup.
- Server tools for controlling nuclear and missile warfare.
- Compatibility and cleanup for modern 1.7.10 modpack environments such as Angelica and GTNH NEI forks.

## Removed, reduced, or reworked fantasy elements

RNTM intentionally removes or reduces systems that do not fit its grounded direction. Examples include removed or changed unrealistic recipes/features such as Power Fist recipes and the removed Nuclear Furnace recipe. The goal is not to delete all science-fiction content, but to keep science-fiction closer to real counterparts or plausible extensions of real technology.

## Nuclear and radiation overhaul

The RTM direction reworks nuclear gameplay around more realistic reactor and radiation behavior:

- Reactor systems were reworked rather than treated as simple power blocks.
- A **Molten Salt Reactor** was added.
- WATZ was converted into a **Pebble Bed Reactor (PBR)** concept.
- Research reactors were re-enabled in 528 mode.
- Radiation mechanics and treatment systems were reworked.
- Radiation-related treatments such as transplants, IVs, and Prussian Blue were updated.
- Fallout can stack when areas are repeatedly irradiated.
- Fallout debris is more difficult to clean up, making contaminated areas a longer-term problem.
- Additional hazard sources were added across the mod.

## Chemistry and materials

RNTM expands the industrial side of the mod substantially:

- Many new chemical processes and recipes were added.
- Periodic-table coverage was expanded, excluding unstable synthetic elements where appropriate.
- Distillation and isotope separation were reworked.
- SILEX enrichment was adjusted toward a more realistic process.
- Industrial production chains were expanded so materials have more grounded manufacturing paths.

## World generation and space

RNTM treats space and geology as part of the realism pass:

- Planetary bodies were reworked.
- Gas giant dimensions were added.
- Travel to the Sun was added.
- Planetary orbit calculations and broken time progression were fixed.
- Oil deposits were moved toward bedrock-generated deposits, and drills can fall back to slow renewable oil/gas from bedrock for custom or WorldPainter-style maps.
- Ore deposits were moved toward bedrock-generated deposits, with the Large Mining Drill able to extract configured bedrock resources.
- Atmosphere and water-table behavior are important gameplay rules, especially for space and planetary bodies.

## Industry and progression

Progression was changed to make industrial infrastructure matter earlier:

- Steam systems were rebalanced.
- Fluid production and handling were rebalanced.
- Blast furnace use is emphasized earlier in progression.
- Furnace smelting recipes were tweaked.
- Additional RTG variants were added.
- The Nuclear Furnace recipe was removed.

## Weapons, warfare, and server controls

RNTM keeps destructive content but gives servers clearer control points:

- CIWS was re-added and reworked.
- Bombsite support for CSGO Charge was added.
- Scheduled nuclear/missile launch or enablement controls exist for server administration.
- Some combat drop behavior was changed so beheading/flesh drops can occur from standard deaths.

## Compatibility and cleanup

RNTM also includes practical cleanup for modpack use:

- Rendering and crash fixes for modern Angelica compatibility.
- GTNH NEI fork compatibility work.
- Removal of ShadyUtil.
- Fertilizer tooltips and other usability improvements.
- General balancing, optimization, cleanup, and many small fixes.

## Documentation note

The exact inherited-versus-reworked boundary can vary by release. When in doubt, check current recipes, generated configs, command behavior, and in-game names in this repository rather than assuming original NTM wiki behavior applies unchanged.
