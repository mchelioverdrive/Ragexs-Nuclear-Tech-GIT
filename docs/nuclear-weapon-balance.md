# Nuclear weapon balance

RNT retains the existing cube-root conversion as its compatibility baseline: the 15 kt Little Boy-class yield maps to legacy radius 48. A legacy radius is a gameplay-scale input, not a universal blast radius and, at the MCHeli boundary, is **not** a literal kiloton value.

## Authoritative detonation boundary

Ordinary nuclear weapons now create their Torex visual through `EntityNukeExplosionMK5`; call sites no longer resolve a second context. The saved detonation specification distinguishes the pre-excavation `predictedBreakthroughFactor`, confirmed `actualSurfaceBreach`, fractional `atmosphericReleaseFactor`, `groundCoupling`, independent surface-deformation potential, and actual outlet coordinates.

Subsurface classification performs a capped 32,768-node air-connectivity search near the cavity. It accepts an open cave or shaft only when it reaches a block exposed to the sky. After incremental rays finish removing terrain, the same bounded check confirms whether excavation opened a release path. A prediction, shallow burial, or thin-but-intact roof is not a breach.

## Subsurface effects

The subsurface cavity radius is `base radius × 0.60 × coupling` (formerly 0.42). Batched rays receive that value as a radius rather than doubling it as a diameter; high-resistance blocks still consume ray strength, work remains chunk-incremental, and requested radii have a 512-block safety cap.

A contained shot retains cavity excavation, underground shock, and an LOS-independent seismic client effect. It has no Torex, flare/full-screen flash, surface shock ring, thermal ignition, atmospheric damage front, fallout rain/deposition, salted fallout, or radioactive crater-biome conversion. Mechanical roof deformation remains conceptually separate from particulate contamination.

An existing shaft or post-excavation opening changes the saved state to vented. Its visual, sound, thermal and pressure effects originate at the confirmed outlet and are scaled by atmospheric release; flash duration also scales rather than imposing a five-second whiteout. Fallout requires both a confirmed breach and at least 0.10 release, and its range/biome footprint inherits the scaled source multiplier. Surface bursts and airbursts retain their prior atmospheric behavior; clean airbursts still skip crater rays.

## Seismic presentation

MK5 sends one seismic packet independently of Torex. The packet includes hypocenter, yield, coupling, burial depth, intensity, and duration. Each client computes its own distance attenuation; solid blocks do not suppress motion. Buried, coupled shots therefore feel slower and longer without changing player health or hurt-camera fields, and the existing nuclear HUD-shake configuration continues to gate rendering.

## Save/load and diagnostics

The saved MK5 state includes prediction, confirmed breach/release and outlet, plus whether shake, visual/flash, prompt radiation, thermal work, fallout, and confirmation have run. The incremental ray cursor remains saved. Extended logging emits one initialization summary rather than block/ray spam, including type, hypocenter/surface, burial, yield, coupling, prediction, breach/release/outlet, cavity/deformation radii, and fallout eligibility.

## Remaining limitations

Fallout is still the existing incremental surface-deposition system rather than a saved wind/radiation plume; “ambient contamination” has no separate supported channel in this entity. Crater ray geometry and cloud simulation remain compressed legacy gameplay systems, and EMP remains on the legacy interface. These are incremental limits, not claims of a literal nuclear simulation.
