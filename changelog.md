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

## NTM Space selective backport audit - planet rings and environment tooltips

- Audited newer NTM Space celestial rendering and data classes for small RTM-safe backports.
- Added presentation-only ring metadata and client-side ring rendering for ringed gas giants, guarded by `enablePlanetRingRendering`.
- Added VOTV destination drive tooltip environment summaries using existing RTM celestial data.
- Documented rejected categories: KSP-style orbital simulation, major propulsion/traversal rewrites, resource-generation balance changes, and exaggerated sci-fi systems.
