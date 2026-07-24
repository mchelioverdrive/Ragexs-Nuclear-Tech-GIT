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
