# Nuclear weapon balance

RNT uses the existing cube-root yield conversion as a compatibility baseline: the 15 kt Little Boy-class yield maps to legacy radius 48, and a legacy radius can be converted back to an estimated kiloton yield. A radius is no longer intended to describe every nuclear effect.

## Modular detonation effects

Ordinary MK5 detonations now construct a compact detonation specification and calculate separate **fireball, crater, blast, thermal, prompt-radiation, fallout, and visual** distances. These remain deliberately compressed Minecraft distances. The terrain processor receives the smaller crater coupling distance, while blast exposure uses an expanding pressure front and thermal radiation is applied independently before that front arrives.

Blast pressure uses gameplay anchors of 50, 20, 5, 2, and 1 psi at increasing relative distances from the 5-psi radius. Entity damage and impulse are applied only when that front crosses an entity; terrain-processing duration therefore cannot repeatedly damage entities. Thermal ignition is fluence-threshold based instead of an unconditional 15-second burn, so exposed villagers below an airburst can receive thermal and blast effects without every target being set ablaze.

## Burst environments

The detonation position, water at the burst, terrain height, and the existing celestial atmosphere trait choose a burst environment. Surface bursts retain crater coupling and fallout; airbursts greatly reduce crater/fallout coupling; subsurface bursts compress atmospheric effects; underwater bursts suppress ordinary thermal effects; and vacuum bursts have no conventional atmospheric blast, fallout rain, or dry-land cloud behavior. This is a gameplay approximation, not a kilometre-scale simulation.

## Persistence and performance

MK5 saves its yield-derived specification, burst type, fallout/salted flags, shock-front radii, one-shot thermal/prompt-radiation state, and the queued batched-ray terrain work. A restarted server resumes queued terrain work instead of beginning the detonation again. Fallout rain persists its salted state and queued chunk lists. Ray collection explicitly skips air and all liquids, preventing useless water/air terrain queues and avoiding ocean deletion.

Legacy `EntityNukeExplosionMK5.statFac`, `statFacNoRad`, and `statFacSalted` remain compatibility wrappers. Existing bomb blocks, missile tiers, MIRVs, custom missiles, artillery, and test callers still use them; their configured radius is converted into the detonation specification. Ivy Mike's test-bomb method now honors its supplied radius for both terrain and visual entities.

## Current limitations

Fallout deposition is still the existing incremental surface-deposition system rather than a saved chunk-radiation plume, and the current visual entity has not yet been fully profile-driven. EMP remains on the legacy interface. These limitations are intentional incremental-migration boundaries, not claims of a literal nuclear simulation.

## Airburst coupling scenarios

The fireball/terrain intersection, rather than a fixed altitude, sets coupling. Values are gameplay-compressed and depend on the configured radius-to-yield conversion; `R` below is the solver fireball radius for that yield.

| Scenario | Burst type / height | Fireball | Coupling | Crater | Moderate blast | Fallout | Torex mode |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 15 kt at ground | Surface / 0 | R | 1.00 | full | 1.35 base | 1.00 | dense ground stem |
| 15 kt at +20 | Air if `20 > R`, otherwise coupled surface / +20 | R | `clamp((R-20)/R)` | continuous | air 1.75 base when air | coupling | elevated if air |
| 15 kt at +100 | Air / +100 | R | 0.00 | none | 1.75 base | 0.00 | elevated atmospheric cloud |
| 1 Mt at +20 | Surface when its larger R intersects / +20 | R | continuous, >0.15 | reduced/full | 1.35 base | coupling | ground-coupled stem |
| 1 Mt just clear | Air / R+epsilon | R | 0.00 | none | 1.75 base | 0.00 | elevated atmospheric cloud |
| Surface over water | Underwater / 0 | R | 1.00 | half legacy radius | atmospheric blast profile | 1.00 | water-coupled legacy cloud |
| Underwater | Underwater / 0 | R | 1.00 | half legacy radius | atmospheric blast profile | 1.00 | water-coupled legacy cloud |
| Vacuum | Vacuum / 0 | R | 0.00 | none | none | 0.00 | existing no-atmosphere orb |

`R = BombConfig.radiusFromKt(yield) * 0.35`. Prompt gamma and neutron exposure are deliberately not disabled in vacuum; only atmospheric blast, fallout, and dry-land cloud effects are.
