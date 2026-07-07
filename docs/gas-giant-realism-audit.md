# Gas Giant and Ice Giant Water Realism Audit

This audit covers the parent giant planet definitions for Jupiter (`jool`), Saturn (`sarnus`), Uranus, and Neptune. Moons such as Europa/Laythe, Enceladus/Slate, Titan/Tekto, Triton, and other satellites are intentionally treated as separate bodies and were not stripped of valid ice, subsurface ocean, or hydrocarbon settings.

| Planet | Type | Previous water behavior | Valid? | Replacement / retained behavior | Files/classes touched |
| --- | --- | --- | --- | --- | --- |
| Jupiter / Jool | Gas giant | No `CBT_Water`, no rain, no lakes, no ocean sea block; chunk provider uses ammonia clouds, storm clouds, supercritical hydrogen, and metallic hydrogen. | Valid. | Left unchanged except included in startup audit warning coverage. Atmospheric composition remains hydrogen/helium with trace methane. | `SolarSystem`, `WorldProviderJupiter`, `ChunkProviderJupiter`, `BiomeGenJupiter` |
| Saturn / Sarnus | Gas giant | No `CBT_Water`, no rain, no lakes, no ocean sea block; chunk provider uses ammonia haze/clouds, storm cells, supercritical hydrogen, and metallic hydrogen. | Valid. | Left unchanged except included in startup audit warning coverage. Atmospheric composition remains hydrogen/helium with trace methane. | `SolarSystem`, `WorldProviderSaturn`, `ChunkProviderSaturn`, `BiomeGenSaturn` |
| Uranus | Ice giant | No `CBT_Water`, no rain/lakes, but solar-system display textures used vanilla water and the chunk provider configured ice as the fallback sea block. Deep generated layers used `ammonia_water` and `supercritical_water`. | Partly invalid: display/fallback settings could imply normal water/ice seas. Deep high-pressure water-ammonia-methane interiors are valid if not treated as surface water. | Display textures now use existing Uranus space-display assets; fallback sea block is air. Deep `ammonia_water` and `supercritical_water` layers remain but comments clarify they are inaccessible high-pressure interior fluids, not surface oceans. | `SolarSystem`, `ChunkProviderUranus`, `WorldProviderUranus`, `BiomeGenUranus` |
| Neptune | Ice giant | No `CBT_Water`, no rain/lakes, no ocean sea block, but solar-system display textures used water textures. Deep generated mantle used `supercritical_water`. | Partly invalid: display settings could imply water seas. Deep supercritical water under pressure is valid if not surface-accessible normal water. | Display textures now use existing Neptune space-display assets. Deep `supercritical_water` mantle remains with explicit high-pressure, non-surface wording. | `SolarSystem`, `ChunkProviderNeptune`, `WorldProviderNeptune`, `BiomeGenNeptune` |

## Guardrails added

- Explicit `CBT_Water(Fluids.NONE)` data now survives serialization instead of silently becoming normal water; missing legacy water-table data still defaults to water for compatibility.
- Startup diagnostics now warn if any audited parent giant has a harvestable surface-liquid water-table trait.
- Uranus and Neptune no longer advertise vanilla water texture assets in solar-system metadata.

## Intentionally unchanged

- Europa/Laythe retains its water trait because icy moons can plausibly have accessible or subsurface water gameplay.
- Titan/Tekto remains nitrogen/methane-rich and does not receive normal water.
- Deep Uranus/Neptune `supercritical_water` and `ammonia_water` blocks remain because they represent hot dense high-pressure interiors rather than normal Minecraft water oceans.
