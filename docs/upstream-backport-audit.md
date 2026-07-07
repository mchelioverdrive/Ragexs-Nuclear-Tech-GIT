# NTM Space Backport Audit

## Safe to backport

- `com.hbm.dim.CelestialBody` ring metadata (`withRings`, ring color, ring size, ring tilt): presentation-only data attached to existing celestial definitions. Dependencies: existing `CelestialBody` construction and `SkyProviderCelestial` rendering only.
- `com.hbm.dim.SkyProviderCelestial` ring quad rendering: client-only rendering polish for bodies that opt into ring metadata. Dependencies: existing Minecraft/Forge client render classes, `SpaceConfig`, and the existing celestial render loop.
  - Compatibility note: rings are emitted as explicit untextured quads with face culling disabled around the draw call so legacy OpenGL/Angelica-style render paths do not drop the annulus. The far ring half renders before the planet disc and the near half renders after planet overlays, allowing the planet to mask ring sections behind it without a depth-buffer dependency.
- Planet/environment tooltip summaries on VOTV destination drives: read-only display of existing body dimension, orbit, atmosphere, temperature, and ring metadata. Dependencies: existing `CelestialBody`, `CBT_Atmosphere`, `CBT_Temperature`, and `I18nUtil`.

## Maybe backport with review

- Newer skybox, city-light masks, atmospheric shaders, impact/shock visuals, and body-specific effects: visual improvements, but they touch many shaders/assets and newer rendering paths.
- Data organization around biome/city masks and additional celestial traits: useful for presentation, but should be split into small commits and reviewed for compatibility with RTM's current dimension data.
- Fog-distance/rendering safety changes for Angelica/Celeritas-like environments: promising compatibility work, but needs isolated testing against RTM's current sky provider.

## Reject

- KSP-style orbital mechanics, delta-v calculations, insertion burns, and route planning.
- Major orbital station propulsion/traversal rewrites.
- Large terrain/resource generation rewrites and balance-changing body generation changes.
- Dyson, war, compromised-body, invasion, and other exaggerated sci-fi presentation systems unless a future RTM design review explicitly accepts them.
