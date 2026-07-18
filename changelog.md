# Port UniNodespace, PowerNet MK3, and FluidNet MK2

- Re-scoped the network migration to the actual node, power, and fluid APIs without restoring removed joke/reference content or unrelated upstream machines.
- Ported UniNodespace, FluidNet MK2 APIs, PowerNet MK2, and ILoadedTile lifecycle guards while leaving only deprecated old fluid facades for compatibility.
- Fixed the FluidConnectorBlock MK2 facade name regression, removed the recentlyChanged reconnect field, and migrated fillable-item imports to the MK2 package where safe.

# Port worldgen cascade and UniNodespace backports

- Backported the vanilla +8 decoration offset for upstream-selected world-generation entries that perform height lookups or can spill outside their origin, including flower, depth-deposit, and Eve spike/volcano starts.
- Added upstream UniNodespace, network providers, FluidNet MK2 API, PowerNet MK2, and the ILoadedTile lifecycle guard used by shared node-network cleanup.
- Followed up the UniNodespace backport with compatibility fixes for RNT's current codebase: restored the legacy Nodespace tick hook, added FluidType network-provider access, added tuple cache helpers, and kept the unported pneumatic provider compiling as a lifecycle-only placeholder until its tile subsystem is migrated.

# Cache machine upgrades and ore layers

- Replaced the shared static upgrade cache with per-machine `UpgradeManagerNT` instances backed by content-aware ItemStack signatures.
- Cached OreLayer3D X/Z noise columns, kept celestial stone replacement support, and skipped chunks that were already decorated per dimension.

# Remove PipeNet transfer debug hot-path work

- Removed active PipeNet fluid-transfer debug tracking, string/date formatting, BigInteger transfer accounting, and per-delivery chunk-dirty calls from production transfers.
- Reworked fair fluid distribution to avoid boxed demand weights while preserving the two-pass demand and transfer behavior.
- Trimmed fluid duct diagnostics and gauge transfer reporting that depended on removed PipeNet transfer counters.

# Port HFR VBO model backend

- Added reloadable HFR OBJ VBO buffers that upload triangle vertices, UVs, and normals with `GL_STATIC_DRAW` and render with `glDrawArrays`.
- Registered the client model reloader so resource reloads rebuild tracked VBO buffers.
- Switched the fluid pump model to the HFR VBO path while leaving broader model conversion selective.

# Fix generic block inventory stack overflows

- Fixed sandbag and barrier inventory rendering to draw their bounded cuboids directly instead of recursively invoking the block item renderer.

# Lower grenade throw speed and restore pullback animation

- Reduced fully charged grenade throws to the normal grenade velocity while retaining bow-style charge scaling.
- Added the bow use action to all throwable grenades so players see the pullback animation before release.
- Updated the getting-started guide with the pullback animation and throw-speed behavior.

# Charge grenade throws

- Changed all throwable grenades to single-item stacks.
- Made grenade throws charge and release using the standard bow draw curve, increasing throw distance with draw time.

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

## Backport: Fluid pump, sandbags, wooden barrier
- Backported the fluid pump, sandbags, and wooden barrier from DONOTMODIFY spacefork HBM.
- Registered the new blocks, fluid pump tile entity, GUI, recipes, render handler, and English names.

## Backport compile fixes: Fluid pump, sandbags, wooden barrier
- Fixed backported fluid pump compatibility with this codebase's tile sync base class, localization helper, number formatting helper, and removed the unavailable NBT transform dependency.
- Fixed sandbags and wooden barrier inventory rendering calls for this codebase's RenderBlocksNT API and restored block declarations.
