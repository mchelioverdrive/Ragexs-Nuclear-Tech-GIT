# Nuclear weapon balance

RNT retains the existing cube-root conversion as its compatibility baseline: the 15 kt Little Boy-class yield maps to legacy radius 48. A legacy radius is a gameplay-scale input, not a universal blast radius and, at the MCHeli boundary, is **not** a literal kiloton value.

## Modular compatibility boundary

RNT now resolves a small immutable `NuclearBurstContext` from the world, detonation coordinates, and legacy radius. `NuclearBurstResolver` is deterministic, inexpensive, and internal to RNT; there is no pending-detonation registry and nothing new is reflected into MCHeli.

The legacy factories retain their signatures and independently resolve the same context:

* `EntityNukeExplosionMK5.statFac(World, int, double, double, double)` owns physical blast, thermal, prompt-radiation, terrain, and fallout work only.
* `EntityNukeTorex.statFac(World, double, double, double, float)` owns visual cloud/fireball state and delayed pressure-wave sound only.

Neither factory creates the other entity or requires the other call to have happened. Consequently, MCHeli's existing `NukeEffectOnly` path continues to produce a fully resolved Torex without MK5, while callers that suppress visuals can use MK5 alone. Existing MCHeli `nukeYield` values remain legacy radii and require neither migration nor a new weapon setting.

`ExplosionAltitude` needs no special bridge: it changes the coordinates supplied by MCHeli, and each RNT factory independently resolves the resulting burst height. No MCHeli `BurstType`, ground-coupling, fireball, fallout, or visual-profile variable exists or is required.

## Burst environments and effects

### Burial, containment, and venting

Subsurface resolution distinguishes resistance-based predicted breakthrough from actual surface breach, atmospheric release, ground coupling, and surface deformation. Prediction sizes mechanical excavation only; it cannot authorize flash, cloud, thermal ignition, pressure, fallout, or radioactive crater biomes.

Actual release requires a bounded (16,384-node, radius-capped) traversal through air or replaceable space to a sky-exposed block. It runs initially to recognize an existing shaft and authoritatively after all incremental ray chunks finish. Solid surviving roof blocks cannot be crossed. Confirmed outlet coordinates are persisted and used as the subsurface atmospheric and fallout source.

The named subsurface cavity coefficient is 0.60. Batched rays retain their existing strength, resistance-budget, generalized-spiral, legacy maximum-reach, incremental cursor, and per-chunk semantics; no artificial vertical shaft is cut.

Contained shots retain cavity excavation, ground shock, and distance-scaled seismic shake, but produce no Torex flare, cloud, sound, surface thermal or pressure effects, fallout rain, or radioactive crater-biome conversion. Confirmed release is scaled by its atmospheric factor; fallout requires a minimum 0.10 release. Surface and air bursts do not acquire a subsurface-breach requirement.

MK5 sends a saved one-shot seismic cue carrying hypocenter, yield, coupling, burial depth, duration, and intensity. Clients attenuate it by distance, respect the HUD shake option, use slower and longer motion, and select the stronger active shake rather than stacking. Torex remains independent and visual-only-capable.


## Static resolver scenarios

The following are resolver calculations, not runtime measurements. “Clear” means a height strictly greater than the listed fireball radius. The profile's soil-coupled crater radius is the current full crater coefficient (`legacy radius * 0.42`) multiplied by coupling. A fully clear airburst has a zero soil-coupled crater coefficient and retains the 0.080 atmospheric fallout multiplier; this does not veto blast damage to reachable terrain or buildings. Surface fallout uses coupling (subject to fission/salted legacy settings).

| Scenario | Legacy radius / converted yield | Burst height / fireball | Type / coupling | Crater / fallout | Torex mode |
| --- | --- | --- | --- | --- | --- |
| Little Boy at terrain level | 48 / 15.00 kt | 0 / 16.80 blocks | SURFACE / 1.000 | 20.16 / 1.000 | Ground-connected mushroom cloud |
| Little Boy above clearance | 48 / 15.00 kt | 17 / 16.80 blocks | AIR / 0.000 | 0 / 0.080 | Altitude-origin atmospheric cloud |
| Ivy Mike 20 blocks above terrain | 424 / 10,338.68 kt | 20 / 148.40 blocks | SURFACE / 0.865 | 154.08 / 0.865 | Partially coupled stem/cloud |
| Ivy Mike fully clear | 424 / 10,338.68 kt | 150 / 148.40 blocks | AIR / 0.000 | 0 / 0.080 | Altitude-origin atmospheric cloud |
| MCHeli weapon labelled 150 kt, `nukeYield = 90`, impact level | 90 / 98.88 kt | 0 / 31.50 blocks | SURFACE / 1.000 | 37.80 / 1.000 | Ground-connected mushroom cloud |
| Same MCHeli weapon through `ExplosionAltitude` (40 blocks) | 90 / 98.88 kt | 40 / 31.50 blocks | AIR / 0.000 | 0 / 0.080 | Altitude-origin atmospheric cloud |
| Same MCHeli weapon, `NukeEffectOnly = true` | 90 / 98.88 kt | coordinate-dependent / 31.50 blocks | independently resolved / coordinate-dependent | no MK5 crater or fallout / N/A | Torex-only matching the resolved mode |
| Underwater detonation at terrain level | caller radius / converted compatibility yield | coordinate-dependent / radius × 0.35 | UNDERWATER / computed intersection | coupling-scaled / coupling-scaled | Underwater-resolved visual path |
| Vacuum detonation | caller radius / converted compatibility yield | coordinate-dependent / radius × 0.35 | VACUUM / computed intersection | physical terrain coupling may exist; fallout 0 | No-atmosphere visual, no atmospheric sound |

The MCHeli example intentionally demonstrates compatibility conversion: radius 90 converts to about 98.88 kt with the existing RNT curve, even if a weapon is described elsewhere as “150 kt.” RNT does not reinterpret that existing configuration value.

## Incremental terrain processing

MK5 initializes the single batched ray engine at the floored detonation coordinates, independently of Torex visibility or animation. Surface and subsurface strength retain the coupled crater and cavity coefficients respectively. Airbursts use the existing full surface strength scale (`legacy radius * 0.42 * 2`), without changing the airburst soil-entrainment or fallout coefficients. Rays must actually reach a solid block and retain positive resistance energy; high bursts do not project destruction onto the surface.

Workers process disjoint contiguous ranges of up to 256 rays, with at most min(4, configured worker count) outstanding batches per explosion. Each batch performs at most 32,768 traversal/reconstruction loop operations, checks cancellation every 32 operations, and reuses up to 64 immutable section inputs from the explosion's actual working set. Only a worker-requested missing section is captured; null vanilla-air storage sections require no per-block callbacks. All snapshot buffers, including retired buffers with active readers, remain under the existing shared memory ceiling, and the global 64-batch limit (16 slots reserved for resumptions), server-wide 5 ms default budget and chunk-loading allowance remain authoritative. The server reserves spiral directions incrementally with the original longitude recurrence, merges masks and adopts range checkpoints. NBT retains unfinished reservations, range cursors, per-ray chunk qualifications and committed masks; uncommitted work is recalculated on reload. Three snapshot rejections without committed traversal/reconstruction progress move only that range to the existing bounded server algorithm. Worker failure is counted separately. Unused cached inputs do not invalidate a batch; stale inputs actually read are always rejected. Thermal ignition continues independently and may invalidate snapshots. Loaded requests can resume within the same scheduler tick; workers are never joined or polled in a wait loop. Unavailable chunks retain their cursors. Destruction masks include the last positive ray sample, including isolated one-block hits. Collection completes before mask removal, preserving resistance against unexcavated terrain; fallout follows completed terrain work and any required breach confirmation. Mask application and all world callbacks run on the server thread, and rejected block removals remain pending.

The final generalized-spiral pole uses a finite zero longitude. Existing saves paused on its former NaN direction retry that ray at the original detonation coordinates and discard its invalid origin-chunk request, while retaining already committed destruction masks.

The existing extended-logging setting reports completed/remaining rays, submitted/accepted/rejected batches, snapshot captures/reuse, section misses, rejected calculation operations, fallback activations, wait reasons and blocks actually removed. It records elapsed time from detonation (including initialization) to first successful removal, collection completion and mask application, separately from accumulated server work and worker calculation durations. Work durations use monotonic wall time within calls, not CPU-time measurements; saved elapsed durations exclude time while unloaded. Progress is limited to one report per 200 server ticks, with additional first-removal, fallback and lifecycle events; individual rays and blocks are not logged. Counts and timings persist with terrain checkpoints.

## Remaining limitations

Fallout is still the existing incremental surface-deposition system rather than a saved wind/radiation plume; “ambient contamination” has no separate supported channel in this entity. Crater ray geometry and cloud simulation remain compressed legacy gameplay systems, and EMP remains on the legacy interface. These are incremental limits, not claims of a literal nuclear simulation.
