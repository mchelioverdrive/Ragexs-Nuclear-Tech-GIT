# Fix legacy gas-giant water trait repair

- Fixed loaded solar-system saved data so Jupiter/Jool and the other parent giant planets that explicitly define `CBT_Water(Fluids.NONE)` cannot keep stale or legacy normal-water traits.
- Documented that the gas giant water audit now covers saved world trait data as well as default planet definitions.

# Backport local stardar processing logic

- Backported the newer space project's relative stardar processing-tier model while preserving this fork's realistic orbital values.
- Destination drives, drive processors, stardar GUI icons/tooltips, and OpenComputers planet stats now rate nearby parent/moon targets as local instead of always using an absolute Earth-centric tier.
- Fixed stardar OpenComputers lookups so unknown body names return an error instead of silently reporting Kerbin/Earth, stars no longer dereference a null parent, and satellite lists return all moons instead of only the first.
- Removed a duplicate world trait-data lookup in celestial trait saving.

## Backport realistic space display and rocket safety changes

- Backported realistic gas-giant ring metadata and sky rendering support from the newer space project, tuned for the realistification solar-system scale rather than upstream KSP-scale values.
- Added ring display parameters to Jupiter/Jool, Saturn/Sarnus, Uranus, and Neptune so their space displays better match real planetary features.
- Restored strict custom rocket fuel sufficiency checks and added a safe fallback capsule for legacy or malformed rocket NBT.
# Rebalance orbital station thrusters

- Made LPW-3N, LPW-2, HTR-F4, and Xenon Station Thruster construction substantially more expensive and progression-gated.
- Rebalanced station thruster recipes around real station-propulsion needs: refractory nozzles, feed plumbing, propellant tankage, motors/coils, avionics, and xenon ion-thruster hardware.
- Documented the new station thruster balance rationale in the space travel documentation.

# Add space travel guidebook

- Added a new in-game space travel guide book with pages for rocket planning, destination drives, survival, orbit, stations, satellites, and return logistics.
- Added a crafting recipe for the space travel guide book using a book and a 1m rocket fuel tank.
- Added repository documentation for space travel preparation and linked it from the README and getting-started guide.

# Update RBMK guidebook for ReaSim defaults

- Revised the RBMK guidebook introduction, fuel, cooling, and fuel behavior pages to describe RTM's default ReaSim RBMK design instead of only the legacy straight-line RBMK behavior.
- Added new guidebook pages covering ReaSim RBMK construction and operating notes, including ReaSim rods, inlet/outlet plumbing, randomized neutron streams, and safer startup guidance.

# Refine guide book bedrock extraction notes

- Updated the starter guide oil section to describe renewable bedrock oil/gas fallback behavior for custom map compatibility.
- Added guide text calling out the Industrial Mining Drill line and Large Mining Drill bedrock resource extraction.
- Updated player-facing docs to mention bedrock pumping and configured bedrock resource drilling.

# Update guide book accuracy

- Audited the starter guide book text against the current localized block and item names.
- Updated early progression, petrochemical, radiation treatment, uranium processing, and MAGNOX reactor guidance to match the realistified terminology.
- Fixed stale ZIRNOX, Rad-Away, Rad-X, pumpjack, tool steel formatting, condenser, and auxiliary cooling references in the guide book.

# Add carbon monoxide ventilation tooltips

- Added inventory tooltips for steel grates, item grates, and air vents noting that they can vent carbon monoxide from enclosed spaces.
- Registered steel grates and air vents with the shared item block tooltip path so their descriptions appear in item form.
- Updated getting-started documentation to call out grates and air vents as carbon monoxide ventilation options.
- NTM Extended Edition for 1.12: <https://github.com/Alcatergit/Hbm-s-Nuclear-Tech-GIT/releases>
- Nuclear Tech Mod Remake for 1.18: <https://codeberg.org/MartinTheDragon/Nuclear-Tech-Mod-Remake/releases>

## Gas Giant / Ice Giant Water Realism Audit
- Audited Jupiter/Jool, Saturn/Sarnus, Uranus, and Neptune planet definitions for normal water oceans, lakes, rain, water-table traits, fallback sea blocks, atmosphere/resource definitions, and display textures.
- Removed misleading vanilla water display/fallback behavior from Uranus and Neptune while preserving scientifically plausible deep high-pressure water-ammonia-methane interior layers.
- Added startup diagnostics for giant planets with harvestable surface-liquid traits and fixed explicit `Fluids.NONE` water-table serialization so no-surface-liquid settings do not silently become water.
- Documented the planet-by-planet audit in `docs/gas-giant-realism-audit.md`.
- Explicitly set Jupiter/Jool, Saturn/Sarnus, Uranus, and Neptune to `CBT_Water(Fluids.NONE)` so parent giant planets cannot inherit normal water-table behavior.
