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

The resolver samples `world.getHeightValue` at the detonation column and converts the legacy radius through `BombConfig.ktFromRadius`. Its fireball radius is the converted base radius times 0.35. A burst is clean `AIR` only when that fireball is fully clear of terrain; otherwise coupling is the continuous fraction of fireball intersection. Vacuum/orbit, liquid at the detonation point, and below-surface positions classify `VACUUM`, `UNDERWATER`, and `SUBSURFACE` before normal air/surface classification.

MK5 scales crater radius and fallout source by the resolved coupling. A clean airburst queues no crater rays, but retains an 8% atmospheric fallout source from fission products; it finishes after its atmospheric shock front while still applying fireball vaporization, thermal burns/ignition, flash blindness, and prompt radiation. The first pressure shell explicitly includes the hypocenter, so targets directly beneath an airburst are not skipped by the expanding-front boundary. Vacuum removes atmospheric blast and fallout but retains prompt radiation. Underwater remains distinct from dry surface handling. The fallout entity now receives the calculated source multiplier, which scales its range, deposition chance, and maximum layer; this remains RNT-only.

Torex receives its resolved burst type, coupling, height, and fireball radius through its own data watchers. Airburst clouds begin at the detonation altitude and reduce ground-debris/stem behavior. Vacuum uses the no-atmosphere visual path without atmospheric nuclear sound. The existing cloud simulation remains gameplay-compressed.

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
| Underwater detonation at terrain level | caller radius / converted compatibility yield | coordinate-dependent / radius × 0.35 | UNDERWATER / computed intersection | coupling-scaled / coupling-scaled | Underwater-resolved visual path |
| Vacuum detonation | caller radius / converted compatibility yield | coordinate-dependent / radius × 0.35 | VACUUM / computed intersection | physical terrain coupling may exist; fallout 0 | No-atmosphere visual, no atmospheric sound |

The MCHeli example intentionally demonstrates compatibility conversion: radius 90 converts to about 98.88 kt with the existing RNT curve, even if a weapon is described elsewhere as “150 kt.” RNT does not reinterpret that existing configuration value.

## Remaining limitations

Fallout is still the existing incremental surface-deposition system rather than a saved wind/radiation plume; “ambient contamination” has no separate supported channel in this entity. Crater ray geometry and cloud simulation remain compressed legacy gameplay systems, and EMP remains on the legacy interface. These are incremental limits, not claims of a literal nuclear simulation.
