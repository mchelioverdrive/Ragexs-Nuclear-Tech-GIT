# Nuclear weapon balance

RNT retains the existing cube-root conversion as its compatibility baseline: the 15 kt Little Boy-class yield maps to legacy radius 48. A legacy radius is a gameplay-scale input, not a universal blast radius and, at the MCHeli boundary, is **not** a literal kiloton value.

## Modular compatibility boundary

RNT now resolves a small immutable `NuclearBurstContext` from the world, detonation coordinates, and legacy radius. `NuclearBurstResolver` is deterministic, inexpensive, and internal to RNT; there is no pending-detonation registry and nothing new is reflected into MCHeli.

The legacy factories retain their signatures. Paired physical and visual callers can resolve once and pass the same immutable context through context-taking overloads, so coordinate offsets cannot make the cloud disagree with its physical explosion:

* `EntityNukeExplosionMK5.statFac(World, int, double, double, double)` owns physical blast, thermal, prompt-radiation, terrain, and fallout work only.
* `EntityNukeTorex.statFac(World, double, double, double, float)` owns visual cloud/fireball state and delayed pressure-wave sound only.

Neither factory creates the other entity or requires the other call to have happened. Consequently, MCHeli's existing `NukeEffectOnly` path continues to produce a fully resolved Torex without MK5, while callers that suppress visuals can use MK5 alone. Legacy overloads still resolve their own context for compatibility. Existing MCHeli `nukeYield` values remain legacy radii and require neither migration nor a new weapon setting.

`ExplosionAltitude` needs no special bridge: it changes the coordinates supplied by MCHeli, and each RNT factory independently resolves the resulting burst height. No MCHeli `BurstType`, ground-coupling, fireball, fallout, or visual-profile variable exists or is required.

## Burst environments and effects

### Burial, containment, and venting

Subsurface resolution distinguishes resistance-based predicted breakthrough from actual surface breach, atmospheric release, ground coupling, and surface deformation. Prediction sizes mechanical excavation only; it cannot authorize flash, cloud, thermal ignition, pressure, fallout, or radioactive crater biomes.

Actual release requires a bounded (16,384-node, radius-capped) traversal through air or replaceable space to a sky-exposed block. It runs initially to recognize an existing shaft and authoritatively after all incremental ray chunks finish. Solid surviving roof blocks cannot be crossed. Confirmed outlet coordinates are persisted and used as the subsurface atmospheric and fallout source.

The named subsurface cavity coefficient is 0.60. Batched rays retain their existing strength, resistance-budget, generalized-spiral, legacy maximum-reach, incremental cursor, and per-chunk semantics; no artificial vertical shaft is cut.

Contained shots retain cavity excavation, ground shock, and distance-scaled seismic shake, but produce no Torex flare, cloud, sound, surface thermal or pressure effects, fallout rain, or radioactive crater-biome conversion. Confirmed release is scaled by its atmospheric factor; fallout requires a minimum 0.10 release. Surface and air bursts do not acquire a subsurface-breach requirement.

MK5 sends a saved one-shot seismic cue carrying hypocenter, yield, coupling, burial depth, duration, and intensity. Clients attenuate it by distance, respect the HUD shake option, use slower and longer motion, and select the stronger active shake rather than stacking. Torex remains independent and visual-only-capable.


### Underwater resolution

Underwater classification uses a bounded vertical 3×3 sample rather than the ocean height map or only the origin block. Water must have an unobstructed vertical route to an open surface; lava and roofed flooded caves do not qualify. One solid origin layer is allowed so bombs removed before resolution and missiles embedded at the seabed remain underwater, while deeper geological overburden remains subsurface.

The context records water-surface height, depth, seabed height, bottom distance, and a yield-scaled surface-interaction factor. Bottom distance controls ground coupling and cratering once; water depth does not. Every underwater Torex remains at the hypocenter and renders a white expanding/collapsing bubble plus pressure-pulse rings, while surface interaction controls only spray/steam at the separately synchronized water-surface height and radioactive mist/rainout. Deep shots therefore retain bounded spherical waterborne pressure damage, muffled sound, and local shake even with no atmospheric plume or fallout rain. Fallout deposition rejects liquid surfaces centrally and does not descend through an intact water column to the seabed.

## Static resolver scenarios

The following are resolver calculations, not runtime measurements. “Clear” means a height strictly greater than the listed fireball radius. Crater radius is the current full crater coefficient (`legacy radius * 0.42`) multiplied by coupling. A fully clear airburst has no crater but retains the 0.080 atmospheric fallout multiplier; surface fallout uses coupling (subject to fission/salted legacy settings).

| Scenario | Legacy radius / converted yield | Burst height / fireball | Type / coupling | Crater / fallout | Torex mode |
| --- | --- | --- | --- | --- | --- |
| Little Boy at terrain level | 48 / 15.00 kt | 0 / 16.80 blocks | SURFACE / 1.000 | 20.16 / 1.000 | Ground-connected mushroom cloud |
| Little Boy above clearance | 48 / 15.00 kt | 17 / 16.80 blocks | AIR / 0.000 | 0 / 0.080 | Altitude-origin atmospheric cloud |
| Ivy Mike 20 blocks above terrain | 424 / 10,338.68 kt | 20 / 148.40 blocks | SURFACE / 0.865 | 154.08 / 0.865 | Partially coupled stem/cloud |
| Ivy Mike fully clear | 424 / 10,338.68 kt | 150 / 148.40 blocks | AIR / 0.000 | 0 / 0.080 | Altitude-origin atmospheric cloud |
| MCHeli weapon labelled 150 kt, `nukeYield = 90`, impact level | 90 / 98.88 kt | 0 / 31.50 blocks | SURFACE / 1.000 | 37.80 / 1.000 | Ground-connected mushroom cloud |
| Same MCHeli weapon through `ExplosionAltitude` (40 blocks) | 90 / 98.88 kt | 40 / 31.50 blocks | AIR / 0.000 | 0 / 0.080 | Altitude-origin atmospheric cloud |
| Same MCHeli weapon, `NukeEffectOnly = true` | 90 / 98.88 kt | coordinate-dependent / 31.50 blocks | independently resolved / coordinate-dependent | no MK5 crater or fallout / N/A | Torex-only matching the resolved mode |
| Open-water or seabed-contact detonation | caller radius / converted compatibility yield | water depth / radius × 0.35 | UNDERWATER / seabed-distance coupling | bottom-coupled crater / surface-interaction-scaled rainout | Hypocenter bubble and pressure rings; optional depth-scaled surface spray |
| Vacuum detonation | caller radius / converted compatibility yield | coordinate-dependent / radius × 0.35 | VACUUM / computed intersection | physical terrain coupling may exist; fallout 0 | No-atmosphere visual, no atmospheric sound |

The MCHeli example intentionally demonstrates compatibility conversion: radius 90 converts to about 98.88 kt with the existing RNT curve, even if a weapon is described elsewhere as “150 kt.” RNT does not reinterpret that existing configuration value.

## Remaining limitations

Fallout is still the existing incremental surface-deposition system rather than a saved wind/radiation plume; “ambient contamination” has no separate supported channel in this entity. Crater ray geometry and cloud simulation remain compressed legacy gameplay systems, and EMP remains on the legacy interface. These are incremental limits, not claims of a literal nuclear simulation.
