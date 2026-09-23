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
