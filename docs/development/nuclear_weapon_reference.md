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

Subsurface resolution distinguishes aggregate-overburden predicted breakthrough from actual surface breach, atmospheric release, ground coupling, and surface deformation. Prediction sizes mechanical excavation only; it cannot authorize flash, cloud, thermal ignition, pressure, fallout, or radioactive crater biomes.

Actual release requires a bounded (16,384-node, radius-capped) traversal through air or replaceable space to a sky-exposed block. It runs initially to recognize an existing shaft and authoritatively after geometric excavation finishes. Solid surviving roof blocks cannot be crossed. Confirmed outlet coordinates are persisted and used as the subsurface atmospheric and fallout source.

The named subsurface cavity coefficient is 0.60. Buried shots excavate that distinct geometric cavity; no artificial vertical release shaft is cut.

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

`EntityNukeExplosionMK5` runs geometric excavation through the historical `ExplosionNukeRayBatched` API. Ordinary nuclear weapons of every yield use the same implementation. `ExplosionNukeSmall` also routes its destructive mini-nukes into this job, retaining their direct `blastRadius` dimension and their existing immediate damage, particles, sound, shrapnel and local radiation. Safe and visual-only mini-nukes do not excavate.

Surface excavation is a bounded ellipsoidal bowl centered on the resolved ground elevation. Its horizontal radius and lower depth are the effects solver's `craterRadius` and `craterDepth`; the upper clearance is half the depth. Each column uses `sqrt(1 - horizontalDistanceSquared / radiusSquared)` to determine its continuous vertical range. Subsurface excavation uses the independently calculated cavity radius about the buried source. Neither uses an underground fireball sphere. Uncoupled airbursts have no crater but retain atmospheric pressure and thermal effects. Yield conversion, fireball dimensions and configured yields are unchanged.

Chunks stream outward in a deterministic spiral and are rejected against the horizontal volume before loading. Each intersecting column is visited once, empty sections are skipped, and Y is bounded to 0–255. Dirt, grass, sand, gravel, ordinary stone, gneiss and ores use geometry and material rules, with no explosion resistance calls, ray generation or reconstruction. There are no random missing blocks inside the ordinary excavation volume. Bedrock, unbreakable blocks and protected materials survive, including a final live protection check before application.

RNT concrete/ducrete families, reinforced blocks, their slab/stair forms and blast/vault doors are structural barriers. Unprotected barriers inside the core (`normalized squared distance <= 0.36`) are destroyed. Edge barriers compare local zone intensity against their own contextual resistance and aggregate local shielding. Unknown blocks retain the existing 200-resistance protection threshold; recognized construction barriers remain destructible below 2,000 resistance, preserving ultra-resistant materials such as reinforced CMB brick. Obsidian retains its previous nuclear stone-times-three override. A single concrete block is not an absolute shield.

Shielding is a documented local approximation: each four-by-one-by-four cell shares one approach toward the source, through at most 64 blocks / 128 half-block samples. Traversal stops as soon as accumulated cover rules out destruction; a later stronger decision can resume that prefix. Ordinary solids contribute aggregate overburden, liquids contribute cover, and only visited structural barriers call Forge's position/metadata/context-sensitive resistance method. Distant barriers outside this local window are not surveyed, and shared cells approximate silhouettes. Section summaries contain ordinary cover and structural markers rather than cached dynamic structural resistance. They are invalidated by block/render changes and chunk loading. The 256-entry per-phase shielding cache expires on world changes and every tick. Pressure discovery also stops below original geological cover, so buried spaces do not become exposed merely because excavation bypasses stone resistance.

Geometric enumeration, pressure selection and safe application overlap within the existing server-wide work budget and round-robin slices. Thermal ground ignition and fallout keep their independent resumable scheduler jobs; thermal exposure retains selective line-of-sight checks. Pressure selections follow the expanding entity front. At most 32,768 pending blocks/notifications and 128 retained mask chunks are admitted before selection yields to application; geometry uses half that allowance to reserve progress for pressure and history reclamation. Existing chunk-loading/generation policy and snapshot-memory limits remain authoritative. All world reads, callbacks and mutations run on the server thread. The crater ray worker, endpoint reconstruction, worker queues and ray completion dependencies have been removed. `explosionWorkerThreads` remains a compatibility configuration key and no longer enables crater workers.

Destruction coverage, pending/completed removal and terminal notification bits remain distinct. Completed chunks retire without waiting for distant selection. Ordinary excavated terrain is retained as compact section occupancy bits for pressure overburden; structural/callback sources retain saved costs and block IDs. History is reclaimed once both outward cursors have passed its bounded shielding window and pending callbacks no longer own it. Allocation failure retains the cursor rather than dropping required history. Native callbacks may run during excavation because geometry does not depend on a frozen resistance survey; observer invalidation refreshes affected pressure summaries.

The existing removal path clears inventories, runs tile-entity removal through Forge's block lifecycle, removes fragile attachments before their support and suppresses attributable direct and delayed plant/leaf/gravity drops. Audited native column batches keep counted section setters, native height/skylight gap maintenance, chunk dirty state, lighting and watcher notifications. Unknown block/chunk/world implementations use normal world setters. Synchronous callbacks and chunk loads remain indivisible and may overrun a slice; no new governed work begins after its deadline.

Fallout can start after material release while excavation, pressure and ignition continue. Its eligibility and strength still use ground coupling, fission and atmospheric release. Existing airburst atmospheric products retain their small source multiplier. Subsurface fallout waits for a confirmed opening after excavation, with the existing minimum 0.10 release factor. It does not wait for a removed ray-completion flag.

NBT work version 4 saves crater dimensions, chunk/column/Y cursors, pressure discovery progress, masks, notifications and shielding history. Shielding scratch and uncommitted bounded traversal can be recalculated after reload. Invalid crater cursors stop further enumeration while retaining committed masks, rather than restarting a large explosion. Older ray checkpoints explicitly retain materialized masks and remaining block lists, retire unfinished ray reservations/endpoints, and resume pressure work. Their unfinished ray-only crater portion is not converted or restarted, so an old in-progress crater can remain partial. The historical public engine name and lifecycle/collection entry points remain compatibility adapters to the geometric implementation.

Extended logging reports geometric completion, selected/removed blocks, pending masks, pressure progress, retained bytes and wait conditions. These architectural changes are not measured performance guarantees.

## Remaining limitations

Local shielding approximates nearby silhouettes and omits distant barriers outside its bounded window. Fallout retains incremental surface deposition rather than a saved wind/radiation plume; cloud simulation and EMP retain their existing implementations. Reduced history-memory limits can pause work that cannot safely retain its required shielding state.
