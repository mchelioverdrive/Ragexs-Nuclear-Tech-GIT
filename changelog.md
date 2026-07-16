# Rebalance nuclear weapons by kiloton yield

- Rebalanced nuclear blast radii around kiloton yield, warhead size, and cube-root destructiveness scaling.
- Updated nuclear missile warhead tooltips and assembled missile tooltips to show KT yield instead of generic strength.
- Added yield tooltips for placeable historical nuclear weapon blocks and documented the balance model.

# Move planetary rings away from planet discs

- Expanded celestial ring altitude offsets by 20x during sky rendering so Uranus and other ringed planets no longer clip their rings into the planet disc under RTM's 1:1 scale.

# Fix planetary ring planet occlusion

- Changed celestial ring rendering so the far side of a ring is drawn before the planet disc, allowing the planet to hide ring segments that are behind it.
- Rebound planet textures after the far ring pass so the planet and phase overlays continue to render correctly.

# Fix planetary ring compatibility rendering

- Reworked celestial ring drawing to disable face culling and emit explicit ring quads instead of a quad strip, avoiding compatibility renderers dropping the annulus.
- Increased ring tessellation/alpha slightly so Sarnus and Uranus rings remain visible after the planet and phase overlays render.

# Fix planetary ring rendering

- Fixed celestial ring geometry so ring quads rotate around the planet center instead of the world origin, restoring visible rings for ringed planets.

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
