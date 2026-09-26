# Pull Request: Separate Magnox collapse and pressure-rupture debris

- Added a slow, local debris profile for nonexplosive overheating, cladding, and graphite collapse failures, with no exchanger debris or high-speed shrapnel.
- Kept the 34 bar CO2 pressure-boundary rupture as the sole local mechanical explosion while separating light debris from heavy reactor components.
- Limited heavy rupture debris to realistic nearby motion so pressure rupture remains destructive without launching reactor components across the surrounding area.

# Pull Request: Restore Magnox gauge pointers and original controls

- Restored the temperature and pressure pointers by drawing all texture-atlas sprites before font-rendered control labels and explicitly rebinding the Magnox GUI texture for each sprite group.
- Restored the original small reactor-control and CO2-valve hitboxes alongside the extended panel, with one packet and sound per click.
- Routed the original reactor toggle through server-side trip-reset and restart-interlock validation, and routed its valve through the same authoritative vent action as the extended control.
- Documented the restored legacy controls in the Magnox operator guidance.

# Pull Request: Repair Magnox controls and cladding radiation

- Added distinct server-authoritative SCRAM, controlled-shutdown, trip-reset, rod-target, vent, and creative-only fault controls with complete restart diagnostics.
- Made leaking installed fuel create a measurable shieldable local field and improved low-dose Geiger precision without creating contained chunk contamination.
- Made every contaminated CO2 discharge transfer its proportional activity into environmental radiation, with a large pressure-rupture release and state-specific detector warnings.
- Documented the IAEA and NKS design basis and separated cladding leakage, containment, local dose, environmental release, and mechanical rupture behavior.

# Pull Request: Correct Magnox thermal inertia and protection

- Added five-second target-based SCRAM rod travel, peak fuel-channel cladding temperatures, rapid pressure-loss protection, and version 3 thermal-store migration.
- Separated operating trips from feedwater and steam-space startup interlocks and warnings, while conserving heat when useful steam output is blocked.
- Increased core and primary thermal inertia, made CO2-dependent shutdown transport explicit, and made exotic-fuel combined-fault wreck and pressure-rupture paths reachable without forcing healthy natural uranium to fail.
- Documented the NKS and IAEA design basis separately from gameplay balance values.

# Pull Request: Rework Magnox thermal failure model

- Replaced calculated-reserve protection with fixed 8,000 mB trip and 12,000 mB restart thresholds while retaining an advisory cooldown-water estimate.
- Made shutdown heat removal finite, gradual, exclusive of useful steam generation, and dependent on actual Light Water and CO2 inventories.
- Rebalanced nonlinear cladding and graphite damage, preserved distinct nonexplosive wreck causes, and exposed core/primary temperatures, decay heat, and damage in the localized GUI.
- Updated Magnox documentation to distinguish historical behavior from gameplay balance and describe credible loss-of-cooling outcomes.

# Pull Request: Treat partial Magnox cladding damage as a radiological fault

- Preserved the original Magnox SCRAM reason and reported rejected-start interlocks separately.
- Added persistent primary-circuit contamination, shielded live-reactor leakage, and proportionate contaminated-CO2 vent exposure.
- Made leaking cladding restart-blocking but nonexplosive, with automatic safe empty-channel servicing and nonexplosive terminal thermal wrecks.
- Added localized cladding condition, contamination, restart-blocker, and radiation-warning GUI text and expanded the Magnox model documentation.

# Pull Request: Localize water-processing Chemical Plant recipes

- Added localized Chemical Plant recipe names for water treatment, water demineralization, borated water production, and brine evaporation across all maintained full locales.
- Updated the language synchronization baseline and water-tiering documentation for the new recipe labels.

# Water Tiering and Purification

- Added raw, fresh, light, and borated water progression through Chemical Plant recipes.
- Added matching hot light-water and borated-water PWR loops while retaining heavy water and engineered coolant compatibility.
- Migrated industrial, direct-reactor, deuterium, and PWR machine tanks without losing stored amounts.
- Updated industrial recipes, condensers, cooling towers, fluid labels, and cold-water extinguishing behavior.
- Removed direct water-bucket salt crafting in favor of brine evaporation.

# Pull Request: Remove the visible scotopic range boundary

- Replaced the finite FAR/FADE visibility endpoint with a smooth exponential long-tail falloff, so recovered low-light signal becomes negligible gradually instead of stopping at one distance.
- Reconstructed approximate camera-to-surface range from linearized forward depth and the active world projection matrix scales, removing the planar distance metric across the view.
- Replaced normalized NEAR, MID, and FAR weights with overlapping loss curves for continuous blur, acuity, and local-detail degradation while preserving bright distant framebuffer objects.

## Previous changes

# Pull Request: Add distance-based RTM scotopic perception

- Added continuous NEAR, MID, FAR, and final-fade ranges for distance-based scotopic acuity and visibility falloff.
- Increased the existing blur radius and local-contrast compression smoothly with linearized distance while retaining the existing sample count.
- Added inexpensive depth-aware blur weighting to reduce foreground/sky silhouette bleeding.
- Limited distance falloff to synthetic low-light and exact-black recovery so original bright photopic signals remain visible.

## Previous changes

# Pull Request: Prevent stacked thunder darkness in RTM dark adaptation

## Unreleased

- Fixed stacked thunderstorm attenuation between Minecraft, Hardcore Darkness, and RTM by using
  rain-level sustained low-light attenuation for both rain and thunderstorm cloud cover.
- Corrected only vanilla's additional thunder fog multiplier during active nighttime RTM and
  Hardcore Darkness adaptation; normal rain fog darkening remains intact.
- Kept rendered lightning flashes in exposure metering, so a flash can still temporarily damage
  dark adaptation before the normal cone and rod recovery resumes.

- Corrected scotopic color perception by recovering bounded luminance before chroma reconstruction,
  making deep rod vision nearly monochrome while preserving limited twilight color and locally bright color.
- Replaced the false saturation-based contrast loss with blurred-neighborhood luminance detail loss,
  strengthened rod-driven acuity loss in deep darkness, and added subtle extra central acuity loss.
- Kept near-field and exact-black recovery achromatic and made scarcity-weighted visual noise monochrome.

- Added a low-light signal-recovery and scotopic tone-mapping patch that dynamically raises Hardcore
  Darkness's sky-only information carrier as eyes adapt, amplifies retained dark terrain RGB with a
  bounded gain, and reserves depth recovery for effectively black pixels.
- Added a subtle depth-bounded near-field perception boost without creating a Minecraft light source,
  and changed configured rod recovery time to mean approximately 95% adaptation instead of one time
  constant.
- Preserved a configurable, sky-only carrier signal before Hardcore Darkness removes nighttime
  texture information, while retaining its removed global RGB floor and RTM's final dark-adaptation
  control.
- Moved dark-adaptation depth capture from the pre-HUD color-copy path to highest-priority
  `RenderWorldLastEvent`, preserving completed world geometry without depending on hand or HUD depth.
- Added current-render generation, active-framebuffer, size, copy-success, and debug-only 16×12
  geometry-coverage validation so stale or cleared depth cannot be advertised to the shader.
- Added exact geometry-mask and remapped linear-depth views plus active-framebuffer restoration for
  vanilla and Angelica rendering.

- Fixed mathematical-black terrain recovery that previously multiplied several small linear factors and produced only about 1–3/255 display luminance (about 1.8/255 in the reported outdoor case).
- Replaced linear environmental scaling with bounded perceptual scaling and a target display luminance, added both cone and rod recovery, linearized depth-based shape cues, safe effect weights, and geometry/recovery debug views.
- Expanded diagnostics to expose requested versus clamped strength and every recovery stage; values such as strength `10.0` can no longer hide the renderer's actual bounded input or destabilize shader interpolation.
- Fixed the dark-adaptation viewport query crashing under LWJGL 2 by ensuring its reusable query
  buffer meets LWJGL's required capacity.
- Added a `runClient` preparation task that exposes ForgeGradle's generated MCP field and method CSV
  mappings at the location expected by legacy coremods.
- Fixed Hardcore Darkness 1.7.10 failing to transform `WorldProviderHell` in the development client
  with `Couldn't find MCP mappings`.
- Documented the generated, development-only `mcp/` directory and excluded it from version control.

2026-09-21 14:44 — Event-driven MK2 power-network bridge

- Replaced unconditional per-tick `PowerNetMK2` redistribution with deduplicated world-scoped dirty queues while preserving per-tick tracker resets and existing distribution formulas.
- Added explicit topology, supply, and demand invalidation, persistent battery/FEnSU receiver memberships, merge transfer, chunk/world unload cleanup, and legacy timeout-compatible fallback updates.
- Added disabled-by-default aggregate power-network diagnostics, enabled by `1.45_enablePowerNetDiagnostics` and readable with `/ntmpowerstats`.
- Retained per-tick compatibility updates only for networks with unmigrated refresh-based endpoints plus a once-per-second persistent-network cleanup pass; dedicated-server and in-game lifecycle validation remains outstanding.

2026-09-22 23:32 — Complete persistent MK2 energy endpoint migration

- Replaced timestamp-refresh registration with persistent endpoint descriptors, deterministic topology replay, explicit chunk/tile/world detachment, and dirty-only redistribution.
- Migrated generator, consumer, storage, diode, converter, directional, multiblock, proxy, charger, and ICF state changes to explicit supply, demand, or topology invalidation while preserving distribution formulas and transfer limits.
- Removed the per-tick legacy compatibility set, compatibility dirty scheduling, timestamp keepalive, three-second expiry, and all-network compatibility sweep; retained only a 100-tick invalid-endpoint integrity audit that does not redistribute clean networks.
- Updated `/ntmpowerstats` for final-architecture attachment, detachment, merge, split, invalidation, integrity, endpoint, skip, and redistribution-time counters.
- Targeted offline Java compilation completed successfully. Dedicated-server and in-game lifecycle, topology, diode, storage-mode, proxy, converter, and accounting scenarios remain to be tested.

2026-09-23 — WIP: Rework conductor topology and fluid networking

- Reworked UNINOS conductor topology around packed coordinate lookup and bounded dirty processing instead of broad stored-node scans. Ordinary six-way power nodes now avoid allocating temporary directional position objects during connectivity work.
- Continued moving power conductor ownership out of loaded TileEntities and into persistent network state. Cable lifecycle paths now register topology changes with UNINOS rather than relying on conductor-side network maintenance.
- Replaced the live legacy fluid-network path with the MK2/UNINOS model. `FluidNode` and `FluidNetMK2` are now being used as the authoritative fluid topology instead of `PipeNet`.
- Reduced the old `PipeNet` implementation to a compatibility-facing layer rather than allowing it to own conductor topology or loaded TileEntities.
- Reworked ordinary fluid ducts so they no longer tick solely to maintain network connectivity. Pipe TileEntities now retain only the state still required by the block while topology is handled by the persistent network layer.
- Added persistent fluid endpoint registration for both providers and receivers so supply/demand membership follows the same lifecycle-driven model as conductor topology.
- Updated fluid connector, conductor, sender, receiver, and user interfaces to bridge existing machine code into the new fluid-network implementation.
- Updated fluid valves, exhaust pipes, duct gauges, base ducts, and shared loaded-TileEntity lifecycle handling for the new topology ownership model.
- Removed an earlier transitional fluid endpoint implementation after the fluid rewrite was redirected fully onto `FluidNetMK2`.

INCOMPLETE:
- Final merge/split and dirty-topology behavior still needs review.
- Remaining legacy `PipeNet` call sites and compatibility boundaries still need cleanup.
- Provider/receiver lifecycle handling and unload/reload behavior still need final verification.
- Full compile cleanup and final diff review were not completed before this work session ended.

2026-09-23 05:42 - Complete deferred conductor topology lifecycle

- Fixed the recursive server chunk-load crash by replacing synchronous cable, switch, duct, valve, and exhaust node attachment during `validate()` with a deduplicated packed-coordinate UNINOS queue.
- Added bounded loaded-only conductor reconciliation. Deferred entries are inspected only after their chunks are available, unavailable coordinates are retained once per pass without loading them, and the current tile at the coordinate is resolved before mutation so stale lifecycle events cannot register replacement tiles.
- Preserved switch and valve metadata semantics: enabled state `1` attaches after safe reconciliation, disabled state `0` removes the live node, and state changes enqueue topology work without conductor update ticks.
- Made conductor invalidation remove only the exact registered node instance and clear local references, while chunk unload drops TileEntity references and leaves compact world-owned topology available for safe reload reconciliation.
- Applied the same deferred lifecycle to authoritative `FluidNode`/`FluidNetMK2` conductors, including multi-fluid exhaust nodes, without returning topology ownership to the legacy `PipeNet` adapter.
- Bounded dirty power and fluid network visits per server tick and retained overflow or repair-blocked entries for later processing.
- Completed compile-oriented cleanup of the conductor lifecycle transition; dedicated-server runtime merge, split, border-unload, and rapid-switch scenarios remain to be exercised in game.

2026-09-23 19:21 — Reduce OBJ rendering and VBO-build garbage

- Confirmed that the profiled approximately 770,000 `Vertex[]` allocations match the 770,318 face and vertex-normal arrays created while the client's current referenced OBJ/HMF resources are parsed, rather than per-frame geometry reconstruction.
- Consolidated eight registered `Sphere.obj` owners and three `LilBoy1.obj` owners onto shared `ResourceManager` models, removing nine redundant parses and 2,232 parser-array allocations during client renderer initialization.
- Removed per-face and per-vertex temporary `Vec3` objects from shared icon-remapped OBJ rendering, including pipe and cable chunk builds, while preserving the existing rotation and lighting behavior.
- Removed temporary three-float arrays and placeholder UV objects from HFR VBO buffer construction.
- Documented exact model ownership, duplicate-load cases, cache/reload lifetime, conductor chunk rendering, remaining legacy TESR paths, and prepared-geometry criteria in `docs/RTM_PERFORMANCE_AUDIT.md`.

2026-09-23 20:00 — Cache ordinary conductor render topology

- Added lazy immutable 64-mask group-selection tables for ordinary round cables and fluid ducts, referencing shared OBJ groups without copying faces or vertices.
- Added an allocation-free untransformed OBJ group path that skips repeated name scans, trigonometry, and identity vertex transforms while preserving icon remapping, lighting, and fluid overlay tint.
- Reduced box-duct connectivity checks from repeated per-face/per-cuboid queries to one six-neighbor calculation per chunk build by reusing render-scoped mask and metadata state.
- Kept ordinary conductors in chunk/block rendering, retained live connector-based mask calculation and every material/fluid/paint/special-conductor path, and made no network-topology changes.
- Targeted offline Java compilation completed successfully; visual mask, chunk-border, texture-override, fluid/color, and special-conductor behavior still requires in-game validation.

2026-09-23 20:16 — Compile shared machine geometry and reduce TESR garbage

- Promoted ten high-use, triangle-only HFR machine models to the existing per-group VBO representation, including the assembly and chemical factories, cryogenic distiller, electrolyser, industrial generator, rad generator, mixer, chemplant body, strand caster, and refueler.
- Kept static bodies and animated rotors, fans, arms, pistons, fluids, clipping, color, texture, blend, and lightmap behavior in their existing named groups and render passes; no TileEntity owns model data.
- Reused decorative item renderers and dummy item entities across assembler, press, inserter, ore-slopper, welding, and soldering frames, and reused the foundry item renderer.
- Removed per-frame color objects, fluid-tank texture identifiers, clip-plane arrays, and pumpjack rotation vectors from prioritized machine TESRs.
- Targeted offline Java compilation completed successfully. Minecraft was not launched; visual parity, animation, clipping, fluid/item rendering, resource reload, and measured performance still require in-game validation.

2026-09-24 02:30 — Restore Angelica celestial-sky mixin target

- Restored the complete sun-rendering body to the `SkyProviderCelestial.renderSun` signature targeted by Angelica 2.1.31 instead of leaving that signature as a forwarding bridge to a different overload.
- Preserved orbital irradiance-based glare through an overridable brightness hook while retaining the exact `glColor4f`/blanking `Tessellator.draw`/`glColor4f` bytecode slice Angelica uses to suppress the blanking quad.
- Removed the now-unneeded overloaded sun-render helpers so Angelica's name-only callback target is unambiguous; the direct call from `render` and the orbit override retain the descriptor expected by Angelica's wrapper.
- Targeted offline Java compilation completed successfully. Static bytecode inspection confirmed one matching `renderSun` descriptor and one `Tessellator.draw()` call between the first two `GL11.glColor4f` invocations; Minecraft was not launched.

2026-09-24 14:24 - Synchronize loaded machine state with chunk data

- Added an initial chunk-data snapshot for shared machine and ticking tile entities by reusing their existing byte-buffer serializers, so client machines no longer wait for a later range broadcast after relogging or chunk reload.
- Made buffer packet payloads independently owned, applied them on the Minecraft client thread, and rejected missing chunks, replaced tiles, and mismatched receiver types while releasing copied buffers after every outcome.
- Preserved the existing range-based update path, fluid serialization order, persistence keys, capacities, modes, and fluid-network topology.

2026-09-24 22:42 — Replace legacy model graphs with shared prepared geometry

- Replaced active Forge OBJ, HFR, HFR-VBO, and HMF loading with a resource/options-keyed prepared-model cache, compact immutable primitive geometry, named/material range metadata, and stable handles that atomically replace state on resource reload.
- Consolidated 485 in-tree load sites into 479 unique prepared keys, removed retained per-face `Face`/`Vertex[]` graphs from active loaders, and changed eligible non-Angelica models to one interleaved VBO per model with range draws.
- Preserved Forge flat normals and atlas-bleed UV inset, HFR smoothing and exact UVs, triangle/quad modes, named groups, HMF UV modulation, icon/atlas remapping, tints, texture overrides, clipping, animation matrices, and inventory rendering through explicit GPU and compact CPU paths.
- Disabled prepared VBO upload under Angelica and retained compact CPU submission there, including dynamically transformed named groups.
- Added disabled-by-default prepared-model parse/cache/CPU/GPU/reload/upload diagnostics. Static inspection covered 479 bundled keys and 385,859 faces; targeted offline Java compilation completed successfully. Minecraft was not launched, so visual, reload, Angelica, HMF, and render-state validation remains outstanding.

2026-09-24 23:18 — Enable prepared-model acceleration with Angelica

- Added an optional Angelica GLSM backend for prepared-model buffer upload, client-array setup, drawing, and resource-reload cleanup while keeping Angelica a soft dependency.
- Restored one-time VBO acceleration for eligible static and animated named model groups under Angelica, including live RTM/HBM matrix transforms, without duplicating model parsing or uploads.
- Kept dynamic HMF and icon-remapped geometry on their existing compact CPU paths and added prepared-model backend selection to the opt-in diagnostics.

2026-09-25 09:06 — Fix prepared block-model rendering

- Fixed decorative computers, dynamite charges, C4, and CSGO charges crashing when their prepared models were rendered in the world or inventory.
- Preserved block-atlas textures, override textures, orientation, brightness, shading, and resource-reload behavior without restoring legacy OBJ model graphs.

2026-09-25 09:43 — Add opt-in world-scoped machine runtime infrastructure

- Added server-world logical machine registries with persistent generation identity, loaded/unloaded controller bindings, replacement-safe cleanup, and explicit world lifecycle ownership while leaving every existing machine on legacy ticking by default.
- Added deduplicated cause-aware dirty processing, generation-safe typed multi-slot transition scheduling, deterministic shared 5/20/100-tick coarse polling buckets, and composable event/scheduled/coarse/realtime execution capabilities.
- Added common inventory, fluid, energy, configuration, redstone, and topology invalidation seams without combining simulation dirtiness with client synchronization or PowerNet ownership.
- Prepared the electric furnace's inventory, energy, and retained on/off block-swap lifecycle for its later migration; no progress, cooldown, recipe, charging, pollution, networking, or tick behavior was migrated.
- Restored shared chunk-unload lifecycle calls in the assembler, heat boilers, and PWR controller, and added disabled-by-default `/ntmmachinestats` aggregate diagnostics.
- Documented persistence, ordering, multiblock ownership, migration phases, and remaining direct-mutation/lifecycle hazards. Dedicated-server and in-game validation remains outstanding.

2026-09-25 11:36 — Reduce space-rendering and chunk-generation overhead

- Reused celestial calculations across sky, orbital transfer, eclipse, and sunlight rendering for smoother travel and planetary views.
- Generated Eve gas, Laythe oil, and the Martian start base in chunk-sized portions to reduce neighboring chunk generation and worldgen hitching.
- Removed an unused full-chunk worldgen allocation and kept structure templates parsed for reuse.
- Released retired chunkloading tickets for moving entities and transporters, including redundant tickets after world reload.
- Reduced rideable rocket update cost by replacing broad liquid scans with a local landing check while retaining lava hazards.

2026-09-25 16:05 — Document proposed RealSim Production progression

- Added a future-design proposal for capability-based fabrication progression using RTM's current player-facing machine names and existing process equipment.
- Documented existing-machine reuse, tooling and intermediate-part criteria, recipe migration rules, bootstrap and compatibility constraints, performance boundaries, phased implementation, and candidate first changes.
- Explicitly kept the proposal separate from implemented behavior; no recipes, machines, configuration formats, or progression rules changed.

2026-09-25 16:37 — Finish chunk-safe large world generation

- Moved overworld oil, oil sand, bedrock oil, radioactive craters, Moon ice pockets, and Eve volcanoes into chunk-owned generation to reduce neighboring-chunk generation and exploration stalls.
- Large legacy buildings, vaults, tombs, and jungle dungeons now generate in saved, chunk-sized portions with consistent seeded layouts and loot.
- Kept small geysers, spikes, and bedrock-ore clusters within safe generation boundaries; already-generated chunks remain unchanged.

2026-09-25 20:21 — Migrate Electric Furnace to the machine runtime

- Opted the Electric Furnace into event-driven eligibility, typed scheduled accounting, and a deterministic 20-tick compatibility poll, removing per-tick recipe and upgrade evaluation while retaining per-tick battery, power, cooldown, progress, and pollution semantics when work requires them.
- Persisted active operation, cooldown, effective duration/consumption, and the next accounting boundary alongside the existing power/progress keys, with lifecycle revalidation and schedule reconstruction that never simulates unloaded catch-up time.
- Wired standard inventory, recipe, upgrade, lifecycle, and external energy mutations to cancel and reevaluate work; retained a bounded poll for direct slot/`ItemStack` mutations that bypass inventory hooks.
- Prevented Forge's temporary TileEntity during lit/unlit block replacement from taking the retained logical generation, while preserving new generations for real destruction and replacement.
- Preserved sided automation, vanilla recipes, input/output timing, upgrade formulas, PowerNet subscriptions, GUI synchronization cadence, soot emission timing, and the lit-state recipe-boundary behavior. Minecraft was not launched; dedicated-server and in-game lifecycle/behavior validation remains outstanding.
- Targeted offline `compileJava` completed successfully.

2026-09-25 20:51 — Migrate the regular Assembler to the machine runtime

- Moved its one recipe lane to dirty eligibility evaluation and typed slot-0 accounting, keeping per-tick battery/HE use, progress, output timing, and prompt neighboring inventory transfers.
- Skipped full ingredient/output evaluation and unnecessary neighboring input/output scans during stable work, reused direction/position descriptors, and retained a distributed 20-tick audit for direct inventory and power mutations.
- Preserved existing power/progress NBT and added accounting, active, and template-switch state for safe load/rebind reconstruction without unloaded production.
- Made existing assembler recipe caching notice registration and `/ntmreload` changes, and marked neighboring inventories dirty on actual direct transfers.
- Kept the eight-lane assembly factory on its legacy loop. Dedicated-server and in-game behavior, performance, and lifecycle validation remain outstanding.
- Targeted offline `compileJava` completed successfully.

2026-09-25 23:02 — Migrate the regular Chemplant to the machine runtime

- Moved its one template-selected operation to dirty eligibility and typed slot-0 accounting; retained per-tick battery, power, oxygen, progress, fluid/container transfer, and neighbor automation cadence.
- Added explicit inventory, power, and standard fluid-transfer invalidation plus a compact 20-tick item/tank audit for direct mutations; completion revalidates current shared resources before consumption and production.
- Added Chemplant recipe generations for `/ntmreload` and saved the next accounting boundary and active presentation state beside existing progress, power, and tank NBT, without unloaded-time production.
- Kept multi-recipe Chemplant variants and Assembly Factory on their legacy paths. In-game fluid, atmosphere, automation, reload, and lifecycle behavior remains to be validated.

2026-09-26 01:28 — Convert electrical energy to joules and watts

- Converted RTM electrical machines, item batteries, storage, and MK2 transfers to exact half-joule energy quanta; displayed energy and power now use SI joules and watts while preserving equivalent machine balance.
- Added versioned energy NBT reads for existing HE-valued worlds and writes for new saves. Migrated relevant packets, capacity and machine configuration keys, converter behavior, and electrical displays.
- Kept legacy HE APIs and configuration keys at compatibility boundaries. RF uses 2.5 J per RF; the existing default RF-to-RTM converter loss remains a separate efficiency behavior.
- Offline Java compilation passed. Existing-world loading, RF exchange, addon behavior, dedicated-server operation, and in-game UI still require runtime checks.

2026-09-26 05:39 — Continue machine-runtime migration

- Extended runtime-driven execution across oil/fluid processing, gas and environmental machines, pump variants, furnace families, radiation generation, ash handling, the Rocket Assembly VAB, and the Drive Processor. Fifty-four concrete machine-related TileEntities now use the runtime directly or inherit a runtime-enabled base.
- Added owner-scoped FluidTank change callbacks, a shared recipe-registry revision, and explicit watt conversion for migrated power accounting. Preserved eight-lane factory ordering and moved stable eligibility, connection, inventory, and environment checks to dirty evaluation or bounded polls.
- Documented all 139 remaining legacy concrete descendants by subsystem and their current coupling constraints. Their production-family migration is not complete; in-game timing, resource contention, lifecycle, automation, fluid, energy, and multiblock checks remain outstanding.
- `git diff --check` passed. The final offline `compileJava` attempt stopped in the Gradle wrapper before Java compilation because its cached distribution lockfile was inaccessible; no source compile result is available. Minecraft was not launched.

2026-09-26 05:57 — Migrate the large arc furnace to the machine runtime

- Moved the large arc furnace's shared batch, progress, lid, energy use, electrode wear, and molten-material pouring into one ordered runtime task while preserving the twenty-slot batch order and existing per-tick active behavior.
- Cached the twenty recipe results across stable work, reevaluating on inventory, liquid-mode, and recipe-registry changes. Neighbor connections now refresh every twenty ticks; direct inventory and registry mutation retain bounded compatibility polls.
- Updated the machine census to 55 runtime-covered classes and 138 legacy descendants. Offline `compileJava` passed with the installed Corretto Java 8 JDK, and `git diff --check` passed. Minecraft was not launched.

2026-09-26 06:22 — Migrate foundry casting to the machine runtime

- Enabled loaded-tile lifecycle binding for foundry controllers and moved Foundry Basin and Foundry Mold cooling/output work into a scheduled casting task with inventory and crucible-flow invalidation.
- Migrated Strand Caster to event-driven batch eligibility, owner callbacks for water and steam tanks, a scheduled 200-tick inactivity flush boundary, and twenty-tick pipe-connection refreshes while preserving its batch threshold and existing network packet cadence.
- Updated the census to 58 runtime-covered classes and 135 legacy descendants. Offline `compileJava` and `git diff --check` passed; Minecraft was not launched.

2026-09-26 06:29 — Move Solar Mirror simulation to the machine runtime

- Moved idle startup checks into a twenty-tick runtime poll and kept active light evaluation and heat delivery on a scheduled per-tick transition.
- Target changes now invalidate the logical machine; client-side boiler registration and animation remain on the existing TileEntity update path.
- Updated the census to 59 runtime-covered classes and 134 legacy descendants. Minecraft was not launched.

2026-09-26 06:40 — Migrate Machine Detector power accounting

- Replaced per-tick connection discovery with twenty-tick topology refreshes and retained the one-quantum-per-tick active drain as a scheduled runtime transition.
- Persisted its buffer through the existing Joule-quantum NBT helpers and moved metadata activation to energy invalidation.
- Updated the census to 60 runtime-covered classes and 133 legacy descendants. Minecraft was not launched.

2026-09-26 06:44 — Move Machine Drain spill work to the runtime

- Tank mutations now start or stop the scheduled spill task; its decay, pollution, gas release, volatile-fluid effects, and americium explosion cadence remain server-thread owned.
- Neighbor discovery and idle tank synchronization now run every twenty ticks; active spill synchronization retains the existing per-tick cadence.
- Updated the census to 61 runtime-covered classes and 132 legacy descendants. Minecraft was not launched.

2026-09-26 06:50 — Migrate Autosaw fuel and cutting work

- Moved fuel consumption and fluid-network renewal to an exact twenty-tick runtime boundary; while fueled, tree scanning, entity interaction, cutting motion, and packet sync retain their per-tick server cadence.
- Tank writes now invalidate the scheduled operation, and idle Autosaws no longer execute the server-side update path.
- Updated the census to 62 runtime-covered classes and 131 legacy descendants. Minecraft was not launched.

2026-09-26 06:59 — Move firebox thermal simulation to the runtime

- Migrated both Heater Firebox and Heater Oven through their shared runtime-enabled base, removing its legacy server tick path.
- Active fuel burn, oxygen use, heat decay, smoke transfer, and oven heat-source draw remain scheduled at their existing per-tick rate; waterlogging, fuel slots, and external heat availability use five-tick reevaluation.
- Updated the census to 64 runtime-covered classes and 129 legacy descendants. Minecraft was not launched.

2026-09-26 07:05 — Migrate Conveyor Press operation

- Moved powered extension, retraction, moving-item capture, and stamping to a scheduled transition; neighbor power discovery now refreshes every twenty ticks.
- Added explicit 1,000 W operating-rate reporting for its default 100 energy quanta per tick and persisted the retraction phase and delay for safe reconstruction.
- Updated the census to 65 runtime-covered classes and 128 legacy descendants. Minecraft was not launched.

2026-09-26 07:16 — Migrate additional generation controllers

- Moved Mini RTG and Solar Panel generation/export into scheduled machine-runtime work, with the Solar Panel checking its light and time boundary every twenty ticks while idle.
- Moved Solar Power Plant tower validation to the twenty-tick poll and generation/export to an active scheduled task; migrated Stirling's heat exchange, overspeed behavior, and output to a scheduled simulation while retaining client animation.
- Exposed watt-rate conversion for the migrated generators and updated the census to 69 runtime-covered classes and 124 legacy descendants. Minecraft was not launched.

2026-09-26 07:20 — Schedule geothermal generator work

- Moved Amgen output, Joule storage, and power export to a scheduled runtime transition; source discovery now checks the two geothermal/lava positions every twenty ticks while idle.
- Preserved the existing per-tick lava conversion chance while the generator is active, and updated the census to 70 runtime-covered classes and 123 legacy descendants. Minecraft was not launched.

2026-09-26 07:23 — Migrate RTG pellet simulation

- Moved isotope pellet decay, heat production, Joule storage, power export, and electricity synchronization into runtime tasks.
- Added slot invalidation with a five-tick fingerprint fallback and retained the twenty-tick energy packet baseline; the census now has 71 runtime-covered classes and 122 legacy descendants. Minecraft was not launched.

2026-09-26 07:29 — Move Diesel Generator processing to MachineRuntime

- Moved fuel conversion, item battery charging, pollution, Joule storage, smoke/power transfer, and state synchronization into a scheduled active task.
- Fuel tanks use owner dirty callbacks; direct inventory and waterlogging changes receive five-tick checks, while fluid subscriptions and connection geometry refresh every twenty ticks. Census: 72 runtime-covered classes and 121 legacy descendants. Minecraft was not launched.

2026-09-26 07:37 — Migrate chimney smoke and ash handling

- Moved chimney ash/soot delivery and active smoke countdown into runtime tasks; pending ash and soot now persist across unload until delivery to a loaded Ashpit.
- Moved smoke pipe subscriptions and idle packet refresh to twenty-tick maintenance; the two chimney types now inherit runtime binding from their shared base. Census: 74 runtime-covered classes and 119 legacy descendants. Minecraft was not launched.

2026-09-26 07:43 — Move condenser family onto MachineRuntime

- Migrated the Condenser, Radiator, and Powered Condenser through their shared controller, with tank/power invalidation, scheduled bulk conversion, and twenty-tick pipe renewal.
- Made configured powered-condenser cost authoritative for both operation-energy availability and consumption, and exposed the resulting operating rate in watts. The Large and Small cooling towers inherit the runtime-enabled condenser controller. Census: 79 runtime-covered classes and 114 legacy descendants. Minecraft was not launched.

2026-09-26 07:48 — Migrate Electric Heater heat simulation

- Moved heat decay/transfer and configured Joule consumption to scheduled runtime work, with power and setting invalidation, 20-tick network subscription, and an idle heat-source wake-up check.
- Added watt-rate reporting for its configured draw. Census: 80 runtime-covered classes and 113 legacy descendants. Minecraft was not launched.

2026-09-26 07:57 — Migrate both Heat Boiler variants

- Replaced the duplicated per-tick boiler controllers with a shared runtime-owned base for tank invalidation, thermal exchange, conversion, fluid output, and lifecycle reconstruction.
- Kept active heat simulation one tick, moved pipe and idle environmental checks to twenty ticks, and preserved the standard boiler's backpressure explosion plus industrial client audio/port layout. Census: 82 runtime-covered classes and 111 legacy descendants. Minecraft was not launched.

2026-09-26 08:15 — Migrate additional continuous heat and generation machines

- Moved the steam engine, heat exchanger, gas flare, and wood burner server work to scheduled runtime callbacks with tank, inventory, energy, and configuration invalidation as applicable.
- Cached fixed port geometry and retained active one-tick rotor, heat, fuel, pollution, fluid-transfer, and power accounting. Steam engine, gas flare, and wood burner now expose generated power in watts. Census: 86 runtime-covered classes and 107 legacy descendants. Minecraft was not launched.

2026-09-26 08:22 — Move oil-burner heat simulation to MachineRuntime

- Replaced its full server tick with scheduled fuel, heat-decay, pollution, and smoke work; tank/control changes invalidate eligibility, waterlogging refreshes every five ticks, and port connections are cached and renewed every twenty.
- Preserved air consumption and active heat-source behavior. Census: 87 runtime-covered classes and 106 legacy descendants. Minecraft was not launched.
