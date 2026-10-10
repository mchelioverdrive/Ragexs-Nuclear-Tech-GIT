2026-10-09 01:26 — Keep dark adaptation active with the HUD hidden

- Run the existing eye-adaptation scene capture, exposure update, and shader composite at render-tick end when F1 skips Forge's overlay event. The normal pre-HUD path, effect settings and suppression rules remain unchanged; hidden HUD elements stay hidden.
- Retain the world-depth capture and framebuffer/GL restoration paths. Source tracing confirms the event routing; F1 behavior and shader interoperability still require in-game validation.

2026-10-09 01:52 — Add primitive processing for stone-drop minerals

- Added low-recovery furnace routes for iron/carbon deposits, chalcopyrite, chalcocite, pentlandite, pyrrhotite, cassiterite, sphalerite, molybdenite, stibnite, ilmenite, cinnabar, and dolomite. Pentlandite supplies nickel nuggets before centrifuge/stainless-steel progression; stibnite yields antimony oxide and ilmenite recovers only iron, cinnabar gives a mercury nugget, and dolomite gives lime while discarding magnesium.
- Added lossy steel-anvil crushing for spodumene, petalite, pollucite, columbite, zircon, and celestite using existing intermediates. Industrial yields, byproducts, chemistry, anvil tiers, material registrations, and stone-drop configuration remain unchanged; the existing wolframite furnace route is retained.

2026-10-09 02:28 — Select molten outputs and cast ordinary ore gangue

- Added shared clickable molten gauges and a scrollable material list to the crucible and large arc furnace. Manual selection reorders existing stack objects, persists by material ID, waits for compatible output, and releases on exhaustion or recipe invalidation; crucible inputs and additives remain reserved.
- Apply selection requests against the player's open, usable container during the server container tick and immediately synchronize the authoritative state. Preserve automatic pouring when no manual selection is active and retain the electrolyser's fixed output lanes and single-material foundry storage.
- Changed all fourteen stock stone-bearing ore definitions from 162 to 648 quanta of ordinary stone per ore, matching one vanilla stone block in the existing basin block mold. Metal, trace, slag, remelting, scrap handling, GT6 exclusions, and active custom recipe overrides retain their existing behavior. Documented selection controls, molten units, casting, and recipe override quantities.

2026-10-10 08:19 — Add furnace-to-motor achievement path

- Renamed the blast furnace achievement without changing its ID, parent, or craft trigger. Added iron furnace, crusher, vacuum tube, and motor achievements in a single parent chain with their own icons and spaced tree positions.
- Award the two component achievements only through successful player crafting paths. Machine output shift-clicks now award from completed output pickups, avoiding awards from failed transfers or input inventory moves. In-game unlocks and the display of the long titles still require runtime validation.
