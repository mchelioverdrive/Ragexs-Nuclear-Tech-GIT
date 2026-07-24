# Modular MCHeli-compatible nuclear burst resolution

**Tracker:** `Modular MCHeli-compatible nuclear burst resolution`

* Added an RNT-only deterministic nuclear burst resolver shared independently by MK5 and Torex. The preserved MCHeli reflection signatures continue to accept legacy HBM radius values, convert them through the existing compatibility curve, and require no new MCHeli weapon variables or configuration migration.
* Kept ownership modular: MK5 performs physical detonation effects only and never spawns Torex; Torex performs nuclear visuals and delayed visual sound only and never spawns MK5. This preserves the existing `NukeEffectOnly` Torex-only path and lets detonation coordinates (including `ExplosionAltitude`) determine the resolved environment.
* Replaced the fixed 12-block airburst rule with terrain/fireball intersection and continuous ground coupling. Clean airbursts have no crater rays or local fallout source, while partial coupling scales crater and fallout continuously; vacuum suppresses atmospheric blast, rain, cloud sound, and fallout without suppressing prompt radiation.
* Passed RNT's calculated fallout source multiplier into fallout rain so range, deposition probability, and deposited-layer cap scale with coupling instead of treating source strength as a boolean.
* Corrected Ivy Mike's test-bomb helper to use its supplied radius for both independent MK5 and Torex calls. Updated the nuclear balance guide with the compatibility boundary, static resolver scenarios, and remaining legacy limitations.
* Fixed the expanding pressure front's zero-distance shell check so entities directly under an airburst hypocenter receive atmospheric overpressure instead of being skipped permanently after the first shell.

# Modular nuclear-effects migration and MK5 correctness fixes

**Tracker:** `Modular nuclear-effects migration and MK5 correctness fixes`

* Reviewed the prior height-only blast change and replaced its repeated, unconditional damage path with a separate expanding pressure front using interpolated 50/20/5/2/1 psi gameplay anchors. Thermal flash is now a distinct, line-of-sight effect with exposure-based ignition instead of setting every exposed entity on fire for 15 seconds.
* Added compact `NuclearDetonationSpec`, `BurstType`, `NuclearEffectsProfile`, and solver classes. Legacy radius callers remain supported through `BombConfig.ktFromRadius`, while crater terrain work, blast, thermal, prompt radiation, fallout, and visuals now have separate calculated distances.
* Added atmosphere-, water-, terrain-, and depth-aware surface, air, subsurface, underwater, and vacuum classifications. Vacuum suppresses atmospheric blast/fallout; airbursts greatly reduce crater/fallout coupling; terrain ray work uses the smaller crater distance rather than the universal damage distance.
* Fixed MK5 salted fallout propagation, Ivy Mike's ignored radius parameter, and the batched ray collector's always-true air/water condition. Fallout salt state and queued rain work persist through save/load.
* MK5 now saves its effect state and batched ray cursor/work queue, allowing a partially processed terrain detonation to resume after reload instead of silently restarting exposure processing. Documented the compatibility boundary and remaining fallout/EMP/visual migration work in `docs/nuclear-weapon-balance.md`.

# Height-based nuclear blast lethality

**Tracker:** `Height-based nuclear blast lethality`

* Changed nuclear entity damage from a spherical range check to a ground-facing lethal volume: effects extend one blast radius below a detonation and half a blast radius above it.
* Nuclear damage now falls off by horizontal distance, making exposed entities below airbursts take the intended blast damage rather than losing it to vertical separation.
* Exposed entities within a nuclear blast zone now burn for 15 seconds, and the nuclear weapon balance guide documents the vertical bounds, fire effect, and obstruction behavior.

# Fix infinite solid rocket fuel in launch pads

**Tracker:** `Fix infinite solid rocket fuel in launch pads`

* Added Solid Rocket Fuel as a fluid propellant and made solid-fueled custom rockets request it through their normal tank requirements.
* All launch-pad variants now accept and consume Solid Rocket Fuel from their normal fluid tanks instead of consuming the legacy item or using a separate buffer.
* Removed the redundant solid-fuel GUI and overlay indicators; the normal fuel tank now presents the propellant state. Removed the obsolete client gauge synchronization for the deleted solid-fuel counters.
* Existing launch-pad saves migrate stored legacy solid-fuel buffers into the new propellant tank when a solid-fueled rocket or missile is loaded.

# Move Magnox fuel fabrication to the Chemical Plant

**Tracker:** `Move Magnox fuel fabrication to the Chemical Plant`

* Moved Magnox fuel fabrication from hand crafting to the Chemical Plant for every fuel variant.
* Chemical Plant fabrication consumes 10 mB of helium to represent industrial helium backfilling before the fuel rod is sealed.
* Gameplay balance is intentionally unchanged: fuel behavior, burn time, depleted outputs, waste recipes, metadata, enum values, and item IDs are preserved.

# Restore grounded petrochemical and biomass processing

**Tracker:** `Restore grounded petrochemical and biomass processing`

* Restored a coal-tar coker-gas route and a cracked-light-oil fractionation route to the existing cracked-diesel/hydrotreating chain.
* Added compact chemical-plant abstractions for coal-derived gasoline, syngas-to-methanol synthesis, and olefin-to-polyethylene polymerization.
* Added the four-flesh-to-biomass crafting route and documented the new industrial chains and their gameplay-scale abstractions.

## Realistify RNT’s Nuclear Fuel Cycle and Spent-Fuel Processing

* Uranium ore is leached to yellowcake, fluorinated to UF6, and enriched through the existing gas-centrifuge cascade with explicit depleted-uranium tails; the terminal LEU stream is deconverted into fuel-grade uranium feed rather than a finished fuel rod.
* Moved cooled PWR-fuel recovery out of the ordinary mechanical centrifuge. Chemical-plant reprocessing now consumes nitric acid and returns limited bulk uranium/plutonium-bearing material, recoverable zirconium cladding, solid nuclear waste, and radioactive liquid raffinate for the existing vitrification recipes.
* Preserved fuel-pool metadata cooling for every PWR fuel variant and the existing waste-fluid-to-vitrified-waste route. Existing machine and item formats intentionally keep UF6 enrichment stages as internal pseudo-fluids and represent chemical separation as a single compact chemical-plant operation.
* Removed impossible direct cooled-fuel isotope-nugget recipes and non-defensible U-238-to-Np-237 and direct Pu/Cm-to-fermium breeder shortcuts. Retained lithium-to-tritium, cobalt activation, U-238-to-Pu-239 (compressed capture and beta decays), and the incremental actinide chain.
* Compatibility: existing item metadata, fuel enum ordering, recipe JSON formats, and the intentionally disabled schrabidium PWR recipes remain unchanged. New chemical recipe IDs are 1117 through 1127.

## Realistify General Industrial Assembler Recipes

* Updated the general assembler bills of materials for ordinary refinery, chemical, pumping, heating, power, mining, storage, shredding, and battery machines.
* Made machine recipes represent their main fabricated vessel or frame, fluid routing, drive, thermal, electrical, sealing, and control systems rather than generic raw-metal bundles.
* Preserved the existing progression and expensive-mode branches while removing the pumpjack's unrelated titanium drill requirement and replacing the pyro oven's internal hard-plastic ingredient with refractory firebrick.
* Left already coherent advanced refinery trains, the ore slopper, electric press, chemical factory, and mining laser recipe structures unchanged; excluded nuclear, fuel-cycle, exotic, and portable-power content from this pass.

## Realistify electrochemical and process-machine recipes

* Moved direct water and brine electrolysis out of the chemical plant into the fluid electrolyser, corrected water and heavy-water gas ratios, and retained fluorine as a coarse nonaqueous fluoride-electrolyte abstraction.
* Removed compressor conversions that changed petroleum into LPG or blood into heavy oil, and made oxyhydrogen require its hydrogen and oxygen constituents at a 2:1 ratio.
* Removed raw-ore electrolysis and non-irradiation recipes from the RBMK irradiation channel; clarified the crystallizer's acid-assisted concentrate-recovery abstraction.

## Require atmospheres for RBMK and combustion machinery

* RBMK fuel rods now halt fuel burn and neutron propagation in near-vacuum conditions while allowing residual reactor heat to cool normally.
* RBMK burners and rotary furnaces now require breathable air before consuming fuel or processing materials, including on airless celestial bodies.
* Documented the atmosphere requirements and the distinction between combustion, reactor, electric, and heat-exchange machinery.

## Waterlog combustible machines

* Fixed water shutdown checks for dummyable combustion multiblocks so water touching the sides or top of any part stops the machine, rather than only water next to the controller block.
* Combustion generators, turbofans, fireboxes, heating ovens, and oil burners now stop consuming fuel while water touches one of their sides or top faces.
* Added water-shutdown notices to their item tooltips and documented the placement requirement in the getting-started guide.

## Add carbon monoxide emissions to heat burners

* Fireboxes and heating ovens now probabilistically emit carbon monoxide while they actively burn solid fuel.
* Fluid burners now probabilistically emit carbon monoxide while consuming flammable fluid.
* Added carbon monoxide warning tooltips for all three heat burners and documented the ventilation requirement in the getting-started guide.

## Fix Leviathan turbine trip status and redstone reset

* Added an always-visible `READY` or `TRIPPED` trip-valve status line to the Leviathan Steam Turbine's look overlay.
* Exported stored power before evaluating Leviathan trip resets and overspeed protection, allowing a redstone reset to clear once a connected power load drains the buffer.
* Documented the trip-status overlay and the electrical-load requirement for redstone resets.

## Fix Leviathan Steam Turbine status and overspeed trips

* Synced the Leviathan Steam Turbine's input and output tank contents to clients so its look overlay no longer reports `0/max` for both steam tanks.
* Restored the Leviathan's internal HE-buffer overspeed trip: if its buffer fills while steam remains admitted because power is not exported, it shuts down and closes its trip valves until reset.
* Updated the steam-turbine safety guide with the Leviathan overspeed behavior and its electrical-load requirement.

## Pause steam turbines when their power buffers are full

* Updated the Standard, Industrial, and Leviathan Steam Turbines to stop consuming steam and generating power when their internal HE buffers cannot fit another generation operation.
* Turbines now resume automatically after connected power consumers, batteries, or chargeable items free enough buffer capacity; the Leviathan no longer trips solely because its power buffer is full.
* Updated the steam-turbine safety documentation to describe the safe power-buffer pause behavior.

## Fix Industrial Steam Turbine overpressure explosions

* Fixed the Industrial Steam Turbine so it ruptures when both its steam inlet and spent-steam exhaust tanks are full, matching the documented overpressure behavior.

## Leviathan Steam Turbine trip protection

* Added automatic overpressure and overspeed shutdown protection to the Leviathan Steam Turbine (`chungus`) through steam trip valves.
* Added the dangerous-trip status and reset instructions to the turbine's block tooltip and look overlay.
* Added lever and redstone trip-valve reset controls, and documented the protection behavior in `docs/steam-turbines.md`.

# Add steam turbine overspeed events

- Standard Steam Turbines now enter a one-second overspeed warning and explode if they continue to process steam while their power buffer is full and no electrical load exports that power.
- Added a turbine look-overlay warning and steam-turbine safety documentation explaining how to avoid an overspeed failure.

# Add steam turbine overpressure explosions

- Standard Steam Turbines now rupture in a destructive explosion when both their input and spent-steam output tanks are full, preventing permanently blocked steam systems from silently remaining safe.
- Documented the required steam-turbine input and exhaust path safety practice in the getting-started guide.

# Render-distance gates for TESR details

- Added a shared 35-block TESR detail distance helper, matching the upstream assembly-factory item-display gate.
- Gated expensive machine item displays, EntityItem/RenderManager-backed renders, animated blades/sliders, plasma/transparent overlays, and other close-range TESR details while leaving static machine bodies visible at distance.

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
## Realistify chemical plant recipes

* Reworked the chemical plant's core oxidizer, acid/base, polymer, uranium-conversion, and coltan recipes so their feeds represent the relevant material families and their major ratios are coherent.
* Removed duplicate water and heavy-water electrolysis recipes from the chemical plant; the dedicated electrolyser remains the production route.
* Documented the deliberately compressed yellowcake-to-UF6, hydrazine, Kevlar, and coltan abstractions while preserving recipe IDs and the existing two-fluid/four-item machine limits.
# Refinery recipe realism pass

**Tracker:** `unironicpain` — petroleum and petrochemical processing audit

* Made fractionation a boiling-range separation pass: heavy oil now supplies vacuum feed and residue, while naphtha is reserved for upgrading rather than being split into diesel and heating oil.
* Restricted reforming to naphtha-derived feeds, added naphtha hydrotreating, and removed the refinery-gas-to-petroleum shortcut.
* Replaced alkylation's halogenation/peroxide recipes with an olefin-plus-LPG alkylate abstraction.
* Limited delayed coking to heavy residues, removed coal-as-meltable-oil recipes, and eliminated duplicated pyro-oven liquid-to-solid-fuel registrations.
## Realistify manufactured component recipes

* Moved basic motor final assembly and dense-wire bundling from the arc welder to the assembler, preserving one motor per set of housing, winding, hardware, and insulation abstractions.
* Made missile fuel tanks use structural shell, scaffold reinforcement, and rubber seal abstractions; empty missile assemblies now use wiring rather than loose rocket fuel.
* Replaced acid and peroxide final-board fluids with the existing solvent cleaning abstraction, removed the unsupported terbium-to-sixteen-capacitor-board route, and stopped rare-metal alternatives from multiplying assembled circuit boards.

## Realistify advanced nuclear assembly recipes

**Tracker:** `Realistify advanced nuclear assembly recipes`

* Reworked the UF6 gas-centrifuge module and cascade around titanium rotors, stainless vacuum containment, steel tank headers, seals, and advanced monitoring rather than fictional structural metal and unrelated coils.
* Reworked the molten-salt-reactor vessel and inlet port around corrosion-resistant salt piping, high-temperature casing, tankage, seals, a circulation drive, and basic process control; removed thorium metal and the unrelated geothermal machine from their construction bills.
* Kept the audited ZIRNOX reactor, fusion, accelerator, radiation, cryogenic, and orbital-system recipes unchanged where the existing bill already maps to actual in-game operation or represents a multiblock segment rather than a complete machine.

## Realistify accelerator, irradiation, and cryogenic processing

* Reassigned neutron-capture and reactor-breeding abstractions from the cyclotron and hadron collider to the exposure chamber, including cobalt-60, lithium breeding, uranium-238 to plutonium-239, and thorium-232 to uranium-233. The latter two intentionally compress the short beta-decay intermediates that RNT does not model as items.
* Restricted the cyclotron to its supported legacy heavy-ion input and a trace-scale californium-249 plus carbon-12 rutherfordium synthesis abstraction. Retained bismuth alpha bombardment in the hadron collider and explicitly marked its tungsten collision products as fictional gameplay-scale collider output.
* Removed generic electron exposure, enrichment-by-irradiation, and arbitrary low-mass collider conversions that had no compatible radiation or target pathway.
* Balanced every cryogenic-distillation batch to its 100 mB feed and made noble-gas trace separation argon-dominant while retaining small neon, krypton, and xenon outputs for progression.
* Compatibility: no item IDs, metadata, machine slots, JSON schemas, GUI/container behavior, or `MatDistribution` registrations changed. Existing actinide capture chains and fictional collider products remain available through their appropriate machine roles.
# Turbine safety, registration diagnostics, and solid-fuel missiles

**Tracker:** `Turbine safety, registration diagnostics, and solid-fuel missiles`

* Added item tooltips that describe the Standard and Industrial Steam Turbines' actual overpressure explosion condition and their safe full-power pause behavior.
* Added startup-only registration diagnostics for malformed crafting recipes, including null stacks, invalid metadata, missing inputs, unregistered references, and recipe-registration failures.
* Converted ABM and micro missiles from incorrectly described pre-fueled items to solid-fuel missiles. Large launch pads now store, synchronize, persist, validate, and consume Rocket Fuel for them; the launcher GUI, item tooltip, and getting-started guide describe the requirement.
