2026-10-17 23:36 — Fix turbofan information and Rotary Furnace NEI crashes

- Turbofan live information now uses a server-calculated fuel-flow snapshot instead of resolving the client's unsynchronized fuel ledger. Unresolved or pending flow displays as unavailable; resolved flow retains the existing LHV calculation. The same status packet now includes current electrical output. Added packet fields are optional, and saved fuel ownership, tank formats, fuel values, and strict simulation validation are unchanged. Rendering only reads the display snapshot.
- Rotary Furnace NEI tooltips tolerate NEI's null hovered stack and leave unrelated item tooltips to the parent handler. Recipe ingredients, results, machine icons, and cycling fuel choices retain their custom job information and quantities.
- Source paths were checked against both supplied crash reports and the NEI 2.7.69-GTNH callback implementation; the existing offline compileJava task passed with Java 8. Packaging and in-game behavior were not tested. In-game verification remains required for empty, newly opened, fueled, and unsupported-fuel turbofans, plus Rotary Furnace recipe/usage navigation and ingredient cycling.

2026-10-18 01:28 — Query live soldering recipes and remove anvil solder wire

- Soldering Station NEI output, usage and machine-category lookups now enumerate the authoritative `SolderingRecipes.recipes` list at query time, avoiding the constructor snapshot taken during early GTNH IMC registration. Recipe pages retain their exact source recipe for duration/power display, preserve circuit metadata and ingredient counts, and reject bills with unresolved required ingredients. The default Integrated Circuit Board remains four Microchips, four Printed Circuit Boards and four Tin-Lead Solder Wires for one board; no duplicate production recipe was added.
- Excluded only `MAT_SOLDER` from generated anvil fine-wire production. Both the server crafting path and NEI read that same anvil registry. The former input was an empty `ingotSolder` entry, which NEI represents with its fire fallback; no rendering substitution was added. Existing Foundry tin/lead alloying and wire-mold casting remain unchanged: the 5:3 nugget-equivalent alloy bill is retained, and casting consumes 72 foundry quanta for eight wires. Item identities, `wireFineSolder`, its consumers and other fine-wire recipes are unchanged. Updated the existing material guides.
- Verified registration order, metadata-sensitive output matching, recipe enumeration, anvil consumers and Foundry mold/runtime quantities by source inspection. The existing offline `compileJava` task passed with Java 8. The original inventory/NEI-list `R` failure was not reproduced in-game; an early empty recipe snapshot is confirmed, but its role in that runtime symptom remains unconfirmed because NEI normally creates a fresh handler for a lookup. Packaging, client/dedicated-server runtime, recipe reloads and in-game recipe navigation/casting still need verification.

2026-10-18 01:51 — Guard machine displays and preserve blocked machine contents

- Audited machine registration, GUI providers, machine-specific information and NEI callbacks, shared inventory/fluid/physical helpers, persistence and status transfer. Retained the existing turbofan server-flow snapshot and Rotary Furnace null-hover fixes; checked the callback contract against NEI 2.7.69-GTNH source.
- Fixed empty ingredient cycling and all its callers, signed Mixer recipe indices and empty recipe arrays, pending Solidifier and zero-capacity custom-machine gauges, empty turret ammo hovers, and unresolved exact-yield fluid/item preview calculations. Live machine demand/draw descriptions now read their snapshots without first invoking simulation getters. Furnace rendering no longer refreshes recipe caches.
- Preserved mixed NEI ingredient alternatives (including Rotary Furnace fuel choices), rejected incomplete required universal-handler inputs, kept normal tooltips for absent/unrelated hovered items, and retained custom-machine physical bills on their cached pages instead of tagging a discarded stack copy.
- Restored fixed lane-array dimensions when loading assembler/industrial chemical reactor, steel furnace and generator state. Legacy NBT status updates now apply on the client thread and release their decode buffers; wire formats and saved inventory keys remain unchanged.
- Reject invalid empty/zero-divisor heating/cooling traits during configuration loading. Boilers, heat exchangers, Hephaestus, ICF, ITER, PWR and Watz retain blocked or unsupported fluid stock; Watz avoids mixing incompatible segment tanks. Negative output room no longer reverses conversions. Custom recipes, Rocket Transporters and fluid settings cannot overwrite incompatible filled tanks; steam grade/type selection requires draining first. Mining Lasers retain blocked oil/gas ore drops and consume only one ore item per 500-unit recovery from merged stacks. Industrial chemical reactor fluid distribution now handles empty offers, over-cap inputs and wide transfer arithmetic without negative debits.
- Reviewed source paths, shared-helper callers and valid/missing-state behavior; checked representative signed-index, proportional-transfer and over-capacity retention arithmetic. The final existing offline `compileJava` task passed with Java 8; inspected machine/information/NEI class files target Java 8 (major 52). Compilation used the repository's existing NEI 1.0.3.74 development dependency; NEI 2.7.69-GTNH compatibility was checked against its callback source, not by running that jar. Packaging, client/dedicated-server launch, enabled configuration reloads, NEI navigation/cycling, legacy saves and in-game blocked-output/reactor behavior remain untested.

2026-10-18 07:23 — Use red copper wire in Cable Drum corners

- Cable Drum crafting now uses four red copper wires through the existing `MINGRADE.wireFine()` OreDictionary key in the corners, four steel plates on the edges and one steel ingot in the center, producing one drum. The Large Steel Gear recipe and Cable Drum networking behavior are unchanged.
- Checked the final recipe definition and diff by source inspection; no build or in-game crafting validation was performed.

2026-10-18 07:34 — Manufacture armor modifications from prepared components

- Replaced table manufacture of all five active ballistic inserts with Assembler integration. Kevlar uses prepared aramid sheets with a sewn leather cover; ceramic tiers independently consume increasing fired alumina tiles, aramid backing, polymer bonding film and covers. Added only unfired/fired alumina tiles: the existing plate-stamp Press forms the powder and the Metallurgical Furnace performs a separate coke-assisted firing step. Steel uses existing formed Weapon Steel plates with aramid/leather containment as an explicitly uncertified game proxy. Removed structural duct tape, bulk rubber in Kevlar panels, raw-element ceramic substitutes and the nested SAPI upgrade chain.
- Moved servos, complex cladding, cell-pack battery modules, magnets, sensors, night vision and oxygen life support to the Assembler using prepared materials, wiring and control components. Obsidian aggregate comes from the existing Shredder; shielding cloth and SmCo retain their existing preparation routes. Manufacture of the two attachment facepieces also uses the Assembler. Four hand recipes remain: powdered coating mixing, simple cut/laced pads and two finished-mask mounting recipes. The mask recipe handler deep-copies input NBT, preserving installed filters.
- Kept all existing item registry names, modifier metadata/NBT, installed modifications, protection/durability/movement behavior and armor-table installation/removal. Disabled/absent modification recipes stay disabled/absent. `plate_kevlar` retains its existing chemistry bill and mass accounting; its English label and documentation now identify prepared aramid sheets and explicitly describe the combined synthesis/fiber/sheet abstraction. Appended 21 machine recipes after existing registrations to preserve native numeric template ordering. Existing JSON overrides still replace entire machine registries and are never overwritten or silently merged; applying removed startup crafting recipes requires a restart. Updated the existing manufacturing/configuration guides.
- Inspected active registrations, material preparation, inventory limits, OreDict definitions, template/NEI representations, explicit Anvil imports, recipe reload clearing and component yields. All 21 Assembler outputs are unique, count one and metadata/initial damage zero; bills use at most seven of twelve ingredient entries, with one item per non-stackable battery input. The existing offline `compileJava` task passed using Java 8 and the unchanged Forge 1.7.10 build. Runtime registry diagnostics, packaging, client/dedicated-server launch, NEI navigation, recipe reloads and in-game manufacture/mask-NBT transfer remain untested.

2026-10-18 08:12 — Restore Deadhour nitre beds and mineral sulfur recovery

- Restored Chemical Plant templates 1143 `NITRE_BED` and 1144 `NITRE_BED2`, removed by `7ee2bec57e85f27608f4ba1042db1e7d43570b05` (compared with parent `5ba5b9455`). Eight leaves or one biomass plus two fertilizer and 1 L fresh water (the historical 1,000 mB) produce eight niter. Existing fixed-work processing pays 60 kJ at 1 kW over 1,200 ticks, including with speed upgrades. These explicitly documented biological gameplay abstractions use work-only bills; they do not invent a balanced nitrate mass assay or weaken `ProcessRecipe` validation. Removed only the explicit 1143/1144 bill-less JSON rejection; all other released-template and fluid-output validation remains active. Preserved template IDs/names and added the requested protection comments.
- Added an unconditional shapeless compost supply: one bone meal, one rotten flesh and one dirt block produce two fertilizer. This new gameplay abstraction avoids the industrial nitrate, potassium-salt and manganese fertilizer prerequisites. Existing fertilizer recipes remain. Traced leaves/shears, biomass from sugar/sawdust/leaves (logs through the Shredder), environmental-water treatment, native OreDict identities and coal/charcoal mixing through to three vanilla gunpowder per sulfur/niter/carbon craft.
- Restored the exact copper-crystal Centrifuge products from before `1ad8969606f6ef99e0d4624d949f251ae87d5a34`: two stacks of two copper powder, one sulfur and one tiny cobalt powder. Copper ore crystallization uses recovered peroxide; fresh-water electrolysis and peroxide synthesis do not need petroleum or sulfur. This is supplementary workshop production: the Crystallizer still needs titanium, Desh and rubber. Overworld sulfur mining remains the earlier route (default generation and two-to-four sulfur drops); sulfur-crystal processing and industrial sulfur recovery remain unchanged. No new sulfur abstraction or machine was necessary, and history does not establish that all early sulfur access was removed by the chemistry rewrite.
- Other confirmed sulfur history was distinguished from a missing early route: `46ca8aa35ea0adf8356f837ac51f7bdfae2ea98e` disabled the old byproduct-7-to-six-sulfur Shredder conversion; current byproduct bundling still yields sulfur at nine to one. `bc3a3ef2a2012d9a0d0a994a7f6387c0b78f04b7` removed cloud-powder centrifuging, whose input no longer has a native item registration. `f8e9e22173cdb834f31a7281372515bb816c593a` deleted a sour-gas Coker recipe already inside a disabled block; it is a petroleum route. These paths were not reintroduced or claimed as newly restored early production.
- Updated current chemistry/process documentation and marked historical unavailability statements superseded. Enabled JSON overrides continue to replace entire registries without automatic merging; underscore defaults supply the restored entries, with litre-based quantities and fixed-work metadata. Crafting changes require restart. Source inspection followed default/JSON loading, template-folder selection and retained template IDs, NEI exposure, server machine input/output checks and paid execution. Static checking found no collisions across 258 literal default Chemical Plant IDs; timing arithmetic gives exactly 1,200 ticks. `git diff --check` passed. The existing offline `compileJava` task passed with Java 8 and unchanged Gradle 4.5/Forge 1.7.10; the initial sandbox attempt stopped before compilation because its default Gradle cache resolved to an absent path. Packaging, client/dedicated-server launch, NEI interaction, enabled override reloads and in-game production remain untested.

2026-10-18 16:32 — Repair Cable Drum transactions and power pole wire lifecycle

- Ordinary Electricity Pylons, Electricity Connectors and all medium wooden/steel variants now interconnect directly as distribution infrastructure. Large Electricity Pylons retain transmission-only overhead connections to large pylons or substations. Existing ranges and local transformer/substation cable ports remain; no voltage values, branch caps, throughput limits or generator/consumer rebalance were added.
- Cable Drum selection is server-owned, dimension-scoped and tied to a persisted placed-pole identity. It is consumed before the connection attempt, rejects missing/unloaded/replaced starts, and preserves unrelated item NBT. Both endpoint records commit together; duplicate/self/over-range/incompatible connections fail with messages. Deferred or retained compact cable nodes no longer cause wire creation to append to a missing or unsuitable node.
- Destruction clears local wire records and removes only the deleted endpoint from peers and retained topology. Repeated old-tile callbacks cannot detach replacement wires. A dimension-local saved removal journal repairs unloaded peers without loading their chunks. Legacy endpoint NBT retains valid wires while rejecting malformed, duplicate, self and orphan records. Rendering requires loaded reciprocal peers; malformed multiblock activation safely rejects missing cores. Wire cleanup never places blocks or produces item drops.
- Updated the power guide with the complete infrastructure compatibility matrix and documented connection ownership and persistence in the existing architecture reference.

- Consecutive pole removals now retain every queued split branch; previously, a later removal could replace earlier split seeds and leave disconnected islands sharing a network. Seed scanning also consumes the existing topology work budget.

- Reattachment compares retained node ports with restored pole records and native geometry, replacing stale edges or old pole/cable ports. Loaded incompatible or over-range saved wires are reconciled at both ends.

2026-10-18 22:04 — Prepare iron pellets and add DRI electric steelmaking

- Added conditioned iron oxide fines, green pellets, hardened pellets and direct reduced iron. Existing Chemical Reactor templates perform oxidative conditioning, limestone/clay/water agglomeration, electrically heated induration and paid hydrogen reduction with explicit physical reactant/product bills and retained fractional outputs.
- Hardened pellets feed the existing Industrial Blast Furnace at three steel per original powder equivalent, with less coke and slag per steel. Ordinary ore/coke remains three steel plus two slag; loose-powder and existing alloy/recycling routes retain their bills.
- The existing Large Arc Furnace accepts a solid eight-DRI, coal-coke and quicklime charge for 32 steel plus two slag at 57.6 MJ electrical work. Native electrical admission, buffering, upgrades, multiblock, electrodes and MachineRuntime remain authoritative. Reserved inputs/products and paid work persist across interruption; blocked outputs retain ownership and controller removal releases owned material once.
- Added NEI ingredients/products and English labels; documented the complete chain, utilities and intentionally gameplay-scaled final recovery factors. New iron intermediates have no generic metal aliases or reverse conversions. No RF dependency, new machine block, molten-steel fluid or changes to existing fluid identities were introduced.

## 2026-10-19 08:53 — Complete native NEI process visibility and protect small-furnace synchronization

- Add read-only production and continuous-operation views for steam condensation/expansion/recovery, fuels and process heat, oil extraction, bedrock drilling, native smelting, environmental utilities and reactor/ICF paths. Use native definitions, physical bills, configured quantities and explicit variable-rate conditions; preserve processing balances and inventories.
- Rebuild Rotary Furnace, Industrial Blast Furnace, gas-centrifuge, cryogenic separation, spent-fuel cooling and fusion views around their actual processing models. Show paid rotary steam and exhaust, optional blast flux/enrichment, internal cascade stages, exact fractional products, coolant operations and separately timed fusion byproducts.
- Unify safe item/fluid navigation, filled-container matching and bounded ingredient cycling; preserve pseudo-stream and ICF pellet identities. Repair template/naming/filled-container Uses paths, reject empty required choices before NEI can fabricate fire placeholders, remove duplicate displayed products, fix SILEX fractional fluid ratios and register GTNH catalysts by registry ID. Keep informational snapshots cached and client-only; replace the shared fluid registry's direct NEI dependency with a neutral revision counter.
- Synchronize the Small Arc Furnace with the existing typed tile-state packet, including its opening snapshot. Reject incorrectly sized seismic payloads before reading fields so a misrouted gauge payload cannot trigger the reported buffer exception; keep packet IDs and furnace processing unchanged.
- Flatten breeder recipe export entries so multiple flux variants serialize without a list-to-recipe cast failure; preserve the existing JSON schema and runtime recipe map.
- Document native process coverage, conditional production quantities, storage/transport boundaries and informational navigation semantics in the development references.

2026-10-19 16:30 — Correct world-load message branding

- Use the registered mod name in the login message instead of the stale RTM: Space label, preserving the existing MOTD setting.
- Populate the in-game version from the same build settings as mcmod.info, replacing the stale 1.0.0 BETA constant. The current build remains 3.3_X1000; future version changes automatically reach both mod registration and the login message.

## 2026-10-19 16:45 — Fit NEI process category titles

- Shorten the seven new process category titles to fit between NEI's header navigation buttons while retaining the detailed information on each recipe page.

2026-10-19 17:07 — Classify groundwater by the extraction biome

- Electric and steam groundwater pumps now extract existing Fresh Water inland and existing raw/saline Water only in Forge OCEAN-tagged biomes, including vanilla Ocean, Deep Ocean, Frozen Ocean and tagged modded oceans. Sample the pump core's exact center column; unavailable biome/chunk data pauses extraction. Preserve planetary non-water fluids, terrain eligibility, throughput, drive demand and export limits.
- Preserve filled tanks across salinity changes and reloads; incompatible contents must drain before new extraction can spend power or steam. Retain paid fractional stock by fluid identity when an empty tank switches quality, without persisting biome classifications or converting saved water.
- Add regression coverage for biome classification, exact boundary sampling, native admission/production, incompatible/full outputs, retained fractional and shaft credit, binary save/reload and server-only class loading. Document salinity rules and show both conditional groundwater products in NEI.
- Exclude the nested pylon test helper from standalone Gradle discovery; its cases continue to run through the existing dedicated-server class-loader test.

2026-10-19 17:16 — Rename saline water identity and display names

- Register the former raw WATER fluid as SALT_WATER, displayed as Salt Water, and update native recipes, machines, containers, physical accounting, groundwater classification and all existing fluid locale entries. FRESH_WATER remains Fresh Water. Preserve numeric registration order, artwork, quantities and recipe balances.
- Read legacy WATER names through compatibility lookup, including fluid settings, saved process bills, fractional product mass and parked pump stock; write canonical SALT_WATER identities. Keep the deprecated Fluids.WATER external field as an alias of the same object, without a duplicate registration. Historical numeric tanks and containers retain their original contents.
- Extend regression coverage for legacy string/numeric tank data, retained paid process progress, fractional mass and pump stock, and the English fluid display names. Update current fluid and tank documentation.
2026-10-19 17:26 — Compress repeated industrial processing without discounting recipe energy

- Rebalance iron conditioning/pelletizing/hardening/hydrogen reduction to 10/10/20/30 seconds per unchanged eight-parcel batch. Preserve prepared Blast Furnace and DRI Large Arc Furnace yields, byproducts and paid work.
- Shorten selected common reagent, salt, fertilizer, polymer, leaching and oxide-reduction cycles. Reactor water electrolysis now takes 20 seconds per litre at its unchanged specific energy; bulk concrete/fibre concrete take 40 seconds per 256 blocks and asphalt takes 60 seconds. Keep molten polyethylene at 60 seconds to respect liquid throughput. Preserve petroleum, native cell/furnace, specialty and isotope processing.
- Raise the Chemical Reactor envelope to 2 MW process duty per lane, 24,000 standard L/min gas feed per inlet, 2 MJ workshop / 16 MJ industrial electrical buffers, and finite 5 MW workshop / 40 MW shared industrial cooling. Actual utilities remain debited, and Mixer/native Electrolyser limits remain independent.
- Add explicit immutable duration changes that preserve complete reaction bills and saved active-batch ownership. Keep recipe IDs, batch sizes, mass remainders, persistence and server authority; existing recipe overrides remain authoritative. Document the gameplay timing policy and calculated end-to-end steel throughput in the existing references.

2026-10-19 17:51 — Audit material utilization and restore specialist contacts and stainless progression

- Add a material-by-material source and historical-use audit under docs/gameplay, covering elemental/alloy identities, native material and component forms, chemical intermediates, fluid states, generated metadata families, specialist roles and disconnected or deliberately unused assets.
- Add a separate Osmium-Iridium Contact Alloy using the existing Foundry template/casting infrastructure. Refined Osmium retains its legacy Osmiridium identity; the new alloy uses material ID 24003 and template 25. Optional alloy and rhodium contact recipes each combine one contact blank with eight coated conductors for eight Cable Switches, preserving ordinary switch recipes and behavior without restoring historical chip/board multiplication.
- Break the chromite-Centrifuge-stainless bootstrap loop with paid Chemical Reactor template 1201: aluminothermic chromite reduction produces distinct Ferrochrome (material ID 24004), alumina and returned unused aluminium with exact saved mass accounting. The existing Metallurgical Furnace converts six steel plus two ferrochrome into eight stainless ingots; ordinary chromium routes and Centrifuge Element requirements remain intact.
- Extend existing regression coverage for physical bills, retained fractional products and paid progress, native metadata/ingredient matching, both furnace input orders, stock chemistry reachability and server-only registration. Document the new manufacturing paths and existing recipe-override boundaries.

2026-10-19 18:03 — Correct chlorocalcite startup ratings and blast-furnace template export

- Set chlorocalcite dissolution and conditioning to 50 and 40 seconds per existing doubled batch so liquid products fit the 12 L/min specialty flow limit. Preserve reaction quantities, mass accounting, paid energy, recipe IDs and saved active batches.
- Serialize blast-furnace OreDictionary stack inputs through the existing stack writer, preventing unfinished JSON fields during template generation. Preserve counted OreDictionary requirements when reading recipe overrides in either input slot, including the six-steel ferrochrome stainless charge.


2026-10-29 19:03 — Add Lunchables and Lunchly lunch kits

- Add craftable Lunchables and MrBeast's Lunchly foods with transparent package sprites. Each restores six hunger points and applies 250 points of the existing carcinogen exposure on consumption, respecting the carcinogen-disable setting.
- Add independent, prerequisite-free "I love my carcinogens" and "FDA approved TM" achievements for eating 100 of the respective food. Separate named Minecraft statistics retain each player's progress across death, reconnects, restarts and item-ID remapping; only completed server-side consumption advances progress.

2026-10-29 19:55 — Restore surface nuclear terrain processing and fallout lifecycle

- Bind procedural explosion and fallout tickets to their real assigned central chunk before the first update, avoiding Forge's neighboring-chunk update gate and premature unloading. Forget local ticket handles after Forge unload cleanup instead of releasing already-discarded world ticket maps.
- Resolve ordinary nuclear and factory-created mushroom-cloud terrain contact with a resumable voxel search under the existing shared server work budget and chunk-loading policy. Account for nearby cliffs and uneven terrain within the fireball, exclude the source block from burial detection, and retain genuine airburst, underground-release, underwater and vacuum distinctions.
- Allow weakly coupled surface crater rays to cross the air gap without increasing resistance energy or ray density. Preserve negative detonation coordinates and save the traversal reach alongside existing mask/checkpoint data; keep legacy terrain saves on their original reach.
- Keep crater completion and breach confirmation ahead of fallout deposition, retain the source's ownership until fallout spawning succeeds, and allow zero-delay or legacy negative-delay fallout queues to resume. Preserve weapon yields, fallout strengths/ranges, material transformations, contamination, server-thread mutation and existing worker/thermal/mask optimizations.

2026-10-29 20:13 — Restore nuclear terrain throughput and complete destruction masks

- Keep the final spiral pole finite in both server and worker calculations. Its undefined longitude previously produced NaN horizontal coordinates and an unrelated chunk-0,0 request, blocking all queued terrain removal and subsequent fallout when that chunk was unavailable. Repair saved invalid polar-ray checkpoints at the actual detonation source.
- Let bounded ray-worker batches reuse up to 32 captured neighboring section snapshots instead of repeatedly yielding at every section crossing. Preserve the existing per-batch operation/ray caps, global memory/queue/time/load limits, immutable worker inputs and server-thread mask commits. Terrain removal and subsequent fallout can progress beyond the prolonged collection bottleneck without changing resistance calculations or excavating ahead of ray collection.
- Include the last positive resistance sample in destruction masks, preserving isolated one-block and source-only hits that exclusive endpoint reconstruction discarded. Retain pending masks when world block removal fails, and include unfinished ray/build/worker stages in completion checks.
- Route airburst blast traversal through the existing terrain engine with the full surface strength scale while retaining its separate zero soil-coupled crater coefficient and atmospheric fallout multiplier. Actual reach, solid terrain geometry and resistance determine destruction; high bursts do not project artificial craters. Preserve surface/subsurface coefficients, underground breach confirmation, weapon definitions, yields and independent thermal/cloud effects.
- Add extended-logging lifecycle and bounded progress diagnostics with coordinates, type, yield, radius/reach, selected/destroyed counts, rejected worker batches, missing-chunk policy and completion/cancellation reasons. Persist counters alongside existing resumable terrain data and document the terrain/fallout processing contract.

2026-10-29 20:58 — Reduce nuclear ray handoffs and bound invalidation retries

- Process disjoint resumable ray ranges concurrently within the existing worker, queue and snapshot-memory limits. Increase bounded worker batches to 32,768 traversal/reconstruction operations and 256 rays, reuse the actual section working set, skip unqualified reconstruction reads and avoid per-block sampling of null vanilla-air sections.
- Keep collection ahead of excavation to preserve terrain resistance; merge masks on the server, retain unfinished range ownership in NBT, and route repeatedly invalidated ranges through the existing budgeted server algorithm without disabling workers or thermal effects. Resume completed chunk requests within the same tick and start mask application immediately when collection finishes with slice time remaining. Preserve yields, Fat Man manRadius, the default shared 5 ms budget, loading limits, resistance and fallout ordering.
- Extend rate-limited logging with first-removal/collection/application elapsed times, work durations, completed/remaining rays, batch acceptance/rejection, capture reuse, repeated operations, wait reasons, fallback activations and actual removal counts.

2026-10-29 22:12 — Remove large-nuke ray throttling and reuse exact terrain calculations

- Govern MK5 collection with the existing shared time budget instead of the inverse-strength completed-ray allowance. Allow cheap rays to fill bounded worker batches while retaining the 32,768-iteration cap, cancellation checks, independent range ownership and global queue limits. Keep per-range section corridors, acquire already-captured sections through a bounded world-free worker directory instead of repeated server handoffs, and prepare short continuations only in already-loaded terrain.
- Reuse proven voxel/vanilla-air prefixes with exact float energy and original tip reconstruction, and memoize exact attenuation values after resistance sampling. Skip reconstruction through fully represented sections while preserving terminal samples and tip priority; never terminate resistance traversal at an existing destruction mask.
- Compact immutable section data with exact resistance/flag palettes and a budgeted full-value fallback. Represent proven null-air sections uniformly and reuse captured sibling-section facts instead of reloading chunks at each altitude. Retain useful snapshots across chunk unloads without retaining chunk objects, and validate changed samples or reloaded sections before adoption instead of discarding unchanged data on render notifications. Reject known-invalid batches immediately; limit snapshot fallback to the current ray before resuming workers. Include metadata, capture scratch, expanded buffers and retired readers in memory accounting.
- Persist focused traversal, reconstruction and reuse counters alongside existing checkpoints; distinguish worker CPU time from active and overall elapsed durations. Preserve weapon yields, ray density/reach, server-thread world ownership, save compatibility, thermal effects, radiation, collection-before-excavation consistency and fallout completion ordering.

2026-10-29 23:57 — Apply nuclear excavation progressively alongside ray collection

- Interleave collection, server-side mask adoption and excavation within existing shared scheduler slices. Keep destruction coverage separate from pending/completed removals and terminal effects so later ranges can add work without duplicate removals or lost tip priority.
- Journal exact original resistance, air/liquid classification and source IDs in compact, bounded per-calculation terrain views, independent of snapshot eviction. Restore required originals before resuming saved work; retain normal validation for unrelated changes. Pause affected removals at the memory ceiling and defer unsafe block/neighbor callbacks while independent calculations and excavation continue.
- Preserve weapon yields, reach/density, Java 8/Forge 1.7.10, worker ownership, thermal/radiation behavior, shared budget and chunk-loading limits. Keep fallout behind all terrain dependencies and report pending excavation, retained bytes and stage wait reasons through extended logging.

2026-10-30 16:51 — Batch progressive nuclear terrain removal and schedule ready work

- Batch certified native terrain removals by column within a section. Use counted section setters for lower cells and Forge's native chunk setter for the highest opaque cell, consolidating height/skylight work while retaining per-cell world lighting, metadata clearing, chunk saving and native client notifications. Tile entities, special callbacks, terminal notifications, snapshot capture and non-native world/chunk/storage implementations retain the normal path.
- Continue across ready chunks under the existing deadline, retain independent partial chunk cursors, skip tick-blocked chunks and donate unused collection/excavation time to useful work. Preserve the shared default 5 ms budget, bounded loading, ray calculations, yields and progressive excavation.
- Separate collection-dependent special removals and terminal effects from ready masks; activate them once calculations finish, retain late tip upgrades and persist their ownership with existing progressive NBT. Cache retained terrain sections and use sparse hash storage with dense indexed storage as occupancy grows, preserving exact resistance, flags, source IDs, memory accounting and previous saves.
- Extend existing rate-limited diagnostics with fast/normal removal counts, interval removals, section/chunk progress, original-terrain preservation time and deferred work counts.

2026-10-30 17:19 — Preserve excavation queue ownership across deadline yields

- Rotate chunk links within their distance bucket without dequeuing and conditionally re-enqueuing. A slice that consumes or defers its last ready cell can otherwise lose queue membership on its next pass, causing a second dequeue to crash or unlink another chunk. Make removal of an already-dequeued chunk harmless while preserving deferred work, late mask insertion, fairness and queue counters.

2026-10-30 18:20 — Balance nuclear destruction and suppress attributable block drops

- Interleave new ray directions across latitude thirds and apply sections around burst height while excavation continues. Keep legacy saved direction checkpoints, ray count, resistance, yields and configured ranges; Fibonacci longitudes change individual new crater boundaries.
- Admit audited RNT ores, plants, waste/scorched terrain and native stair delegates by removal semantics rather than mining drops; re-audit saved deferrals under the application budget. Feed bounded, shielded pressure-front selections into the existing destruction masks and original-terrain journal, sharing completion and saved cursors without a second crater processor.
- Remove fragile attachments before support, suppress nuclear callbacks safely, clear destroyed inventories, and persist bounded exact delayed leaf/plant/gravity attribution. Extend suppression to existing fallout transformations and capped collapse callbacks without affecting player items or global drop rules.
- Retain section batching and client notifications, avoid proven no-op native air-light queue calls, and extend rate-limited diagnostics with directional progress, pressure work, deferral reasons, suppression, memory waits and surface maintenance.

2026-10-30 19:21 — Cover early blast angles and stream pressure candidates

- Permute the existing Fibonacci directions with a deterministic recursively interleaved index order, covering latitude and azimuth early without direction arrays, duplicate rays or changes to density, strength, reach or attenuation. Save direction-order version 2; resume existing three-band and legacy recurrence checkpoints with their saved mapping.
- Stream bounded section candidate batches inside the pressure footprint, skip proven empty storage and space above top-filled/original terrain, stop at original geological barriers, and refresh pending data after block changes. Reuse validated exact shielding prefixes and proven zero-air intervals. Govern discovery and traversal with the existing shared deadline instead of the fixed 1,024-operation allowance; donate unused stage time without changing default budgets or loading limits.
- Separate completed crater coverage from original-terrain and callback ownership. Retire finished masks after rays end, retain original shielding throughout unfinished pressure paths, release unowned history under the deadline, and permit proven no-op neighbor notifications before distant selection completes. Unknown callbacks, special cleanup, pending effects and fallout retain their required dependencies.
- Extend rate-limited logging with joint elevation/azimuth progress, selected/removed surface samples and first-surface-removal delay, pressure section pruning, traversal reuse, active stage time and callback dependencies. Preserve section lighting/saving/synchronization, protected blocks, audited RNT removals and attributable drop suppression.

2026-10-30 21:58 — Replace nuclear crater rays with geometric excavation

- Use the yield/coupling-derived crater bowl and separate buried cavity for ordinary nukes of every size; route destructive mini-nukes through the same bounded geometry while preserving their direct blast-radius input and existing immediate effects. Excavate stone and ores continuously without resistance rays or reconstruction, preserving bedrock and protected blocks.
- Classify concrete, reinforced and shielding structures separately. Destroy unprotected core barriers and use shared, bounded local thickness estimates at crater edges and in the expanding pressure front; retain aggregate original overburden for underground protection. Section summaries keep structural markers instead of caching dynamic resistance.
- Retain shared scheduling, chunk policies, safe removal, tile cleanup, drop suppression, lighting and client updates. Bound pending masks, stream compact overburden history, overlap thermal/pressure/fallout work and start fallout from material release without ray dependencies.
- Save geometric progress in NBT version 4. Older ray saves retain committed masks/remaining positions and pressure progress but retire unfinished ray-only excavation rather than reconstructing or restarting it. Keep public engine/lifecycle entry points and the inactive worker-count configuration key for compatibility.

2026-10-31 03:00 — Preserve fallout shapes and clean disconnected crater remnants

- Preserve half slabs, stairs and other partial collision shapes when a fallout substitution has no compatible shape. Keep slab height and stair metadata for compatible outcomes, distinguish double slabs, retain ordered rule/depth semantics, and skip unchanged writes without changing eligible full-block probabilities or distances.
- Align vanilla stone/sandstone slab subtypes and sandstone stairs with ordinary geometric excavation after live protection checks. Retain structural shielding for other masonry and reinforced/concrete forms, bedrock and protected materials.
- Add a saved, shared-budget component cleanup sweep over capped affected crater sections after excavation and local fallout/collapse work settle. Directly remove confirmed small disconnected groups through existing safe callbacks and drop suppression; retain supported, protected, unavailable, changed and limit-exhausted groups. Bound sections, traversal distance, component size and total probes; preserve legacy excavation progress without retrospective rescans or new cleanup chunk loads.
- Save the associated crater volume on new fallout jobs and defer its per-block air-below collapse to component cleanup. Keep collapse behavior outside the crater and existing saved collapse queues.

2026-11-01 21:12 — Add persistent mammal rabies simulation and shared pathogen framework

- Add server-owned pathogen definitions and one saved infection record per host/pathogen, separate from potion symptoms and existing radiation/toxic contagion. Preserve infection timelines across unloads, logout and dimensions; clear player infection on death/respawn. No unloaded transmission or forced chunk loading.
- Seed existing bat infections once per host with `batchance=0.08`; use independent per-bite settings including infectious bat-to-wolf `dogchance=0.08`. Both are eight percent gameplay defaults. Require a confirmed post-protection bite wound, with one establishment roll, explicit mammal host rules and a modded mammal allowlist.
- Add compressed configurable silent incubation, presymptomatic infectiousness, furious/paralytic clinical progression, neurological impairment and fatal decline. Retain vanilla bat flight and wolf ownership/taming; bound local bite behavior and remove disease tasks when infection is cleared.
- Add the `20_rabies` Forge configuration section and operator-only `/ntmdisease inspect|clear` diagnostics. Reuse vanilla symptom synchronization without sending private infection records. Healing and milk do not cure infection; existing medicine has no suitable rabies vaccine or post-exposure treatment to reuse.

2026-11-02 01:40 — Restore effective furious rabies pursuit and bite contact

- Prioritize nearby eligible players, retain local targets without short chase timeouts or sight-loss resets, and start bounded searches/path refreshes promptly. Prevent baby following and passive sitting from competing with disease movement; interrupt unmounted horse grazing/rearing before vanilla can skip disease AI. Preserve swimming, door handling, ownership, taming and saved sitting commands.
- Direct furious bats through native flight, including neurological aggression with impairment. Preserve silent stages and nonaggressive paralytic progression. Use one shared bounding-box reach and unobstructed contact check for disease attacks and confirmed bite wounds, retaining ordinary damage protection, transmission probabilities and the bite cooldown.
- Extend operator-only `/ntmdisease inspect` with behavior execution, target IDs and blocked-pursuit reasons. Clearing infection releases disease targets, navigation, flight destinations and temporary task changes; existing infection timelines remain intact.

2026-11-02 02:37 — Extend furious rabies roaming, bites and staged water symptoms

- Add restless local ground roaming and separate native bat flight destinations, visible nearby host selection without player priority, configurable 32-block ground detection/48-block retention, bounded last-observed pursuit and delayed failed-path retries. Match disease navigation range to pursuit without changing native attack attributes or loading chunks; retain swimming, obstacle handling, wolf ownership and saved sitting commands.
- Keep repeated bites on infected targets and the 40-tick bite cooldown. Add configurable post-wound target switching (default 0.5), close-host reactivity, a short switching cooldown and three temporary recent-target IDs. Preserve the existing single confirmed-wound transmission path and all transmission probabilities, host rules, saved infection deadlines and fatal progression.
- Block vanilla water-bottle drinking only during symptomatic furious player illness, including safe cancellation when symptoms start mid-drink, with rate-limited swallowing-spasm feedback. Preserve other potions, milk, filling and splash use. Add default-enabled configurable symptomatic player water-contact penalties and animal dry-route preferences, retaining stronger effects and safe swimming/escape; clearing infection releases temporary behavior settings.
- Document ticks, the 1.2-second total clinical window of `clinicalTicks=24`, and saved-deadline persistence. Distinguish Merck's roaming/reactivity and CDC's human swallowing spasms from gameplay water rules; follow WHO's distinction that hydrophobia is not a typical dog sign, and treat the 200-victim wolf story as unverified.

2026-11-02 03:27 — Add furious rabies encounter stares and sporadic movement

- Make furious roaming choose varied native destinations every 40–100 ticks, with occasional 10–40 tick attention pauses and bounded failed-route retries. Add short pursuit hesitations and greater disruption during neurological decline, retaining native movement limits, bat flight, swimming and the quiet/paralytic presentations.
- Add a one-time visible-target stare per nearby encounter, with configurable `stareDistance=20`, `stareMinTicks=40` and `stareMaxTicks=60`, validated bounds and duration ordering. Ground pursuit pauses while bats retain flight; disease bites wait until the stare ends, and attacks from the current target permit immediate retaliation without resetting bite cooldowns. Suspend and restore furious wolves' original melee tasks and gate native melee so it cannot bypass disease attack rules. Preserve bounded sight memory, protection hooks, confirmed-wound transmission, infection saves, ownership and sitting commands.
- Expose staring targets and remaining ticks through `/ntmdisease inspect`, alongside roaming, pursuing and impaired states. Release temporary movement/look/task controls on removal and document the stare as a gameplay choice; encounter timers are not persisted.

2026-11-02 03:40 — Add early HRIG rabies prevention and persistent Fatal status

- Register Human Rabies Immune Globulin as a separate medical syringe using the existing antidote icon. Server-owned treatment removes an established player infection through normal cleanup. Consume a dose and return an empty syringe only on success, with no Creative consumption, stored protection, immunity or animal treatment. Treatment timing and survival crafting are corrected in the entry below.
- Save fatal commitment in the rabies record and restore a separate harmful Fatal status after potion clearing without adding damage or a second death timer. Preserve legacy infection times, saves, dimensions, protection hooks and death/respawn clearing; administrative clearing removes commitment and its owned effect. Extend inspection with treatment eligibility, remaining cutoff ticks and commitment.
- Localize item guidance and feedback. Describe immediate passive antibodies at the exposure site and CDC's vaccination/wound-care requirements, explicitly identifying single-item prevention and the one-Minecraft-day cutoff as gameplay simplifications rather than real medical deadlines.

2026-11-02 17:26 — Guarantee the full player rabies treatment window and craftable HRIG

- Remove clinical-stage rejection from the shared HRIG, fatal-commitment and diagnostic eligibility rule. Use the original saved infection time and the existing disease clock: treatment remains available before 24000 elapsed ticks and closes exactly at the deadline, without committing Fatal on infection establishment or changing transmission.
- Shift early player clinical and later deadlines forward together once, preserving downstream durations, longer timelines and animal progression. Repair living legacy player records within their original window, clearing premature commitment and its disease-owned Fatal effect without resetting infection time. Preserve expired commitment, death clearing and administrative overrides; explicitly preserve missing timestamps as unknown and fill missing player deadlines without treating absent NBT as tick zero.
- Add one shapeless crafting-table HRIG recipe using an empty syringe, milk bucket, pumpkin seeds and sugar through the existing recipe/NEI path. Preserve item identity, antidote icon, ordinary antidote behavior and server-only successful-dose consumption/empty-syringe handling. Replace the long HRIG tooltip with the two short localized treatment lines and update existing treatment/configuration documentation.

2026-11-02 18:44 — Add controlled rabies injection and a rabies death achievement

- Register a separate Rabies Virus Syringe in medical consumables and the normal item/NEI catalogue, reusing the poison syringe icon. Native self- and hit-injection establish infection through `DiseaseManager.infect` without bite rolls or synthetic damage; retain susceptible-host/enable checks, normal incubation and presentation, and the full player HRIG window. Persist syringe attribution with injector UUID/name in existing source fields. Rejected or repeated infections consume nothing and preserve original deadlines and commitment; successful doses follow existing empty-syringe and Creative rules.
- Add prerequisite-free `achievement.rabiesDeath`, titled "DID YOU ALSO EAT A KID" with description "Die from rabies." and the virus syringe icon. Award through existing server statistics in the lowest-priority death handler only for lethal rabies disease damage after revival/cancellation, before respawn cleanup. Do not award for infection, Fatal status, unrelated deaths or animal deaths.
- Add item/achievement localization and update existing rabies and achievement command documentation without changing saved infection NBT, natural transmission, HRIG or ordinary antidotes.

2026-11-02 20:18 — Display persistent rabies status and restore treatable player symptoms

- Add a separate RABIES indicator using configurable potion ID `8.16_rabiesID` (default 79), preserving existing IDs. Reconcile it against living players' saved infections throughout every stage, including disabled behavior and invulnerability. Resist milk, restore after potion clearing, and remove on treatment, administrative clearing or death; the indicator neither creates nor cures infection and leaves unrelated effects intact.
- Add player-only `earlySymptomDelayTicks=1200`, bounded to 1–23999 ticks, for short refreshed Weakness I and Slowness I before clinical onset. Preserve stronger effects, later neurological symptoms, original timestamps, clinical deadlines, the full 24000-tick HRIG window and animal progression.
- Allow HRIG injection into healthy living players through existing dose/empty-syringe handling, potion-sickness checks and Creative rules. Grant no stored protection, immunity or infection; retain eligible treatment and dose-preserving rejection after the cutoff. Update existing configuration/command documentation and short English guidance.

2026-11-02 20:11 — Treat rabid villagers and collect rabies samples

- Allow HRIG treatment of living villagers using their original saved infection time and the existing eligibility/cleanup rules. Right-click HRIG takes precedence over trading without requiring damage; successful treatment releases disease behavior while preserving profession, trades, reputation and unrelated data. Retain player treatment, dose/empty-syringe returns, potion sickness and Creative rules.
- Reject villager-source rabies in the shared bite transmission path before any establishment roll, as a gameplay rule. Preserve normal bite damage, furious pursuit, staring, target switching, repeated attacks, neurological impairment and susceptibility to animal bites or virus injection.
- Let the existing empty syringe collect one existing Rabies Virus Syringe from an infected animal after a confirmed direct player melee wound, at any established stage. Bind collection to the attack's held stack and native post-protection damage; reject canceled, blocked, indirect and duplicate callbacks. Replace the last empty in hand, otherwise insert or drop the sample; Creative keeps the empty, including when inventory overflow drops the sample. Preserve the animal's infection and timeline without adding an exposure roll or changing item identities, textures or recipes.

2026-11-02 21:29 — Add animal recognition, avoidance and escape from rabid bites

- Let supported animals recognize visible clinical/neurological animal threats within a configurable 12-block gameplay radius, with a default 0.7 chance per encounter. Keep ignored decisions in bounded temporary memory; use staggered loaded-chunk searches, native movement away from threats and delayed path retries. Retain swimming, obstacles, rider control and saved sitting commands; exclude player and villager movement.
- After a confirmed infectious animal bite, finish the existing transmission roll before starting a configurable 160-tick escape from the actual attacker. Escape also applies to silent attackers, failed establishment and newly infected victims, with further bites refreshing it. Furious aggression and neurological/terminal impairment retain movement ownership. Preserve transmission probabilities, bite damage, host eligibility, infection NBT/timelines, HRIG, status effects, achievements and syringe behavior.

2026-11-02 22:15 — Replace furious rabies pursuit delays with one approach and sustained sprint

- Choose one configurable 60–200 tick creep period per encounter, including the existing initial stare. Advance at 0.5 times normal movement after the ground stare, then change active navigation directly to 1.5 times normal movement during furious CLINICAL illness. Preserve the phase through target switches, bites, path retries and valid sight memory; remove recurring pursuit hesitations. Bite reach and attacks from the current target end the approach immediately without resetting the bite cooldown.
- Adapt horizontal native bat flight steering to the approach/pursuit multipliers without ground navigation. Cap neurological pursuit multipliers at normal speed before existing Slowness/flight damping; preserve terminal paralysis, quiet/paralytic stages, avoidance, bounded loaded-chunk navigation, protection and confirmed-wound transmission. Use temporary behavior deadlines and navigation speeds without changing base movement attributes or removing unrelated effects; release controls on encounter end and infection removal, preserving ownership and sitting commands.
- Validate creep duration ordering and finite positive movement multipliers, expose creeping/sprinting and remaining approach ticks through existing disease inspection, and update configuration comments and behavior documentation.

2026-11-02 22:45 — Add subtle mouth froth to symptomatic rabid animals

- Emit tiny white saliva flecks from rendered animal head/jaw poses during clinical, neurological and terminal illness in both presentations. Account for wolf/livestock muzzles, horse jaws, cats, bats, young heads and native poses; use short falling particles with a brief fade and the existing white texture. Unknown modded head models omit the effect rather than emitting from guessed body positions.
- Synchronize only visual eligibility to tracking clients on changes and tracking entry, keeping infection records and NBT private. Clearing infection, disabling disease or death stops new emission while existing particles expire. Retain infection progression, transmission, AI, treatment, potion effects and saved records; particle contact has no gameplay behavior.
- Randomize emission at 10–20 tick intervals, honor particle settings and the vanilla 16-block particle distance, and cap total creation at four particles per client tick without entity scans or per-particle packets.

2026-11-03 01:36 — Add the hostile Wither Storm encounter

- Add a persistent Command Block Wither that evolves in place into Destroyer, Devourer and ThunderStorm using consumed mass. Coordinate three damageable heads, five tentacles and two devourers through saved parent/component UUIDs and one root health pool; retain entity identity, health and mass through evolution and reloads. Keep the vanilla Wither unchanged.
- Add server-owned flying pursuit, independent head targeting/skulls, gravity-compensated tractor beams, tentacle strikes, entity/item consumption and phase-scaled attacks. Use ordinary RNT explosion effects without nuclear crater generation or fallout. Include original model geometry, a procedural texture, synthesized sounds, visible command core, Forge boss bar and client-only beams/debris.
- Bound terrain coordinate work and skull-impact queues, cache bounded entity searches, preserve debris metadata, protect unbreakable blocks and default-protect tile entities/block vetoes. Honor mobGriefing and separate configuration, cap visual debris, and avoid chunk tickets, forced terrain loading and server debris entities.
- Register /rntwitherstorm spawning, inspection, growth and removal controls with a saved global encounter limit that includes unloaded bosses. Add a configurable survival ritual using existing euphemium, soul sand, wither skulls and a nether star; document commands, growth thresholds and budgets. Use Engender only as a technical behavior reference, with no copied code/assets or runtime dependency.
- Repair an existing missing quote in sounds.json so the sound registry, including the new encounter sounds, can parse.

2026-11-03 03:59 — Connect Wither Storm heads and align single eyes with tractor beams

- Extend the evolved body's shoulders and bridge all three heads with overlapping blocky neck segments. Anchor necks inside the interpolated body and rotated skulls so independent head movement retains connected anatomy, including during body emergence and at reduced detail. Preserve head positions, jaw animation and body scale.
- Replace each head's two glowing eye quads with one centered bright purple square. Start visual tractor beams at the transformed eye center with an eye-sized opening, retaining target endpoints, glow, fading and visibility guards. Keep the existing texture, mouths, teeth, targeting, server-side tractor effects, combat and spawning unchanged.

2026-11-03 04:29 — Rework Wither Storm anatomy and progressive head transformations

- Start default command and ritual summons as one persistent Wither Storm entity with ordinary Wither heads, a connected skeleton and an exposed rib-framed command block. Preserve explicit command/storm forms, growth edits, identity, health, terrain budgets and saved attachment ownership.
- Transform the central head at the existing transformation threshold, the first side at its midpoint with Devourer, and the final side above Devourer. Save and track head states and transition animation; migrate older saves from mass and retain completed transformations across threshold changes. Gate tractor beams to completed upgraded heads and aim their shared server/client eye coordinates independently.
- Replace scale-only body progression with solid overlapping growth lobes, a broad deep mantle, larger skulls, thick connected necks and longer tapered animated tentacles. Add hinged lower jaws, pale teeth and exactly one purple eye per upgraded main head. Keep the command core embedded and visible as the body develops.
- Render consumed blocks as rotating, metadata-textured six-face cubes in the existing client particle batch. Cache face UVs and light on creation, use safe textured fragments for unsupported models, and animate outward breakup followed by curved accelerating absorption. Keep visual caps and growth-dependent density separate from server destruction and mass accounting; create no server decorative entities.
- Advance attachment interpolation once per client tick, create fresh summon heads without the old restoration delay, and adapt bounds and loaded-terrain margins to the larger anatomy. Update existing command and configuration documentation.

2026-11-03 05:42 — Add the Sandworm exterior and persistent internal boss encounter

- Add an independent Forge 1.7.10 Sandworm encounter using ModularBosses as a gameplay reference. Supply original Java, articulated head/body/tail geometry, textures, room schematics and synthesized heartbeat sounds; import no restricted models, unlicensed resources or external mod dependencies. Keep existing entity and packet IDs stable and leave Wither Storm behavior unchanged.
- Coordinate eleven native multipart components through one server-owned exterior pose and bounded client interpolation. Add terrain-relative diving/surfacing, player and throwable bait attraction, mouth swallowing, body knockback and reliable parent-owned segment cleanup without standalone segment entities or chunk tickets.
- Add stationary Brain and Heart bosses, radial energy attacks, shield-granting Spark waves, damage-triggered Heart invulnerability and blood defense, four server-owned Spark effects, and five-stage Ticks with synchronized growth and hitboxes. Restrict healing and shields to allied encounter monsters and bound saved Spark populations.
- Generate separate connected interiors in a conflict-checked dimension using validated registry-name schematic palettes, reciprocal doors and room rotations. Bound construction and cleanup globally; save layout, progress, organ deaths, ownership and per-player return destinations. Preserve offline occupants, reconcile missing organs, prevent duplicate spawns and encounter resurrection, unlock the entrance exit after both organs die, return players safely and retire the exterior. Save a configurable ten-second return grace to prevent immediate re-swallowing after escape or death.
- Add isolated flesh, guts, tumors, blood, acid, saliva, gas and temporary projectile hazards. Register operator `/rntsandworm` controls and documented configuration for movement, combat, generation and cleanup. Natural desert encounters remain disabled by default; manual spawning is always available within the encounter limit.

2026-11-03 09:20 — Add the Prismarine Colossus boss encounter

- Add independent active, dormant, Romeo-controlled and glitched Forge 1.7.10 Colossi with original articulated prismarine geometry, raised carvings, mouth cavity, emissive rectangular eyes/rear rune and a held giant clock. Share one skeleton between rendering, functional hit regions, limb impacts and climbing anchors; retain Java 8 and existing entity/packet IDs.
- Add deliberate loaded-terrain steering, ground support, target selection, punch/sweep/slam/stomp/grab animations with server-owned contact windows, interrupted-attack staggering, braced multiplayer climbing, safe attachment release, and a directional rear-head symbol test. Add the survival-craftable 3D Prismarine Gauntlet and optional ordinary-weapon weak-point damage.
- Queue contact-only terrain processing under a dimension-wide budget, protect containers and indestructible blocks, honor mobGriefing/block vetoes/Forge break hooks, and suppress incidental item drops. Keep destruction and rare ocean spawning disabled by default. Persist variant, scale, encounter admission, recovery and clock state; reload interrupted attacks safely without replaying impacts.
- Add operator `/rntcolossus` spawning, activation and removal, a Creative spawn egg, configurable combat and effects, temporary opt-in clock time changes, and delayed collapse/fragment deaths with bounded shard drops. Document Story Mode references and RNT survival adaptations without changing other bosses, nuclear explosions or monument generation.

2026-11-03 19:39 — Add Wither Storm skull summoning, mob fear and Challenge Mode

- Add the mineable Fake Command Block to rare structure loot and exposed Ice Plains Spikes tips. Placing the last wither skeleton skull on either orientation of the matching structure starts a three-headed Command Block Wither and consumes the blocks after a successful spawn.
- Give non-boss mobs priority escape behavior in both Forge 1.7.10 AI dispatch paths. Use the encounter ledger for nearby threats and space out escape path attempts.
- Add an opt-in, world-specific 24,000-tick challenge countdown and operator controls. The single automatic Storm follows bounded Overworld player trails with its existing flight and growth systems. The world saves completion, and the Storm saves its current route.

2026-11-03 20:20 — Add the Giant Ghast boss encounter

- Add the Jailhouse Block Giant Ghast with a continuous three-faced body, animated tentacles and mouths, an emissive top core, a walkable roof, and the native boss health bar.
- Add three health phases, five fireball patterns, melee reflection, and capped summon waves. Real barrage hits can turn summoned Ghasts against their owner. Direct hits on the core multiply damage and trigger a distinct reaction.
- Save encounter ownership, attack progress and retaliation across chunk unloads and world reloads. Retire owned Ghasts and fireballs on boss death or operator removal. Add `/rntgiantghast`, `/summon hbm.giant_ghast`, and encounter settings with natural spawning and block damage disabled by default.

2026-11-03 20:45 — Refine Prismarine Colossus movement, grabs and model

- Climb supported one-block ledges with a bounded, collision-checked step. Use loaded terrain for clearance and gravity independently of block destruction.
- Aim and articulate either arm toward the grab target during windup. Capture on swept fist contact and give grabbed players four seconds to escape through distinct Sneak presses, with server-tracked HUD progress and a recoil on escape.
- Rebuild the prismarine silhouette, face, arms and legs with original stone materials and shared animation, contact geometry and climbing anchors. Preserve the emissive eyes and rear-head weak point.

2026-11-04 00:29 — Expand the Giant Ghast encounter

- Build a deep central head and connected flank heads with animated tentacles and a pulsing top core. The head and core geometry also define the multipart hitboxes and solid roof.
- Raise the configurable default to 2,000 HP. Schedule four ordinary shots and a reflectable fifth, 15-shot bursts, large radial flowers, and alternating triples and five-shot flowers. Keep the per-encounter projectile cap and summon retaliation.
- At 10% health, spin and descend with an erratic barrage, then expose the core for eight configurable melee strikes. Save combat and execution progress, credit the final attacker, and clear owned Ghasts and fireballs during the death sequence.

2026-11-04 01:07 — Fix Prismarine Colossus steps, aimed attacks and terrain contact

- Drive alternating planted steps, knee bend, foot lift and footsteps from horizontal travel. Synchronize stride and ground height with the shared render and collision pose.
- Aim punches and grabs with either arm, track the selected target during windup, and resolve contact along the committed animation. Keep grab escape and consumption server-authoritative.
- Use swept fist, arm and foot geometry for terrain strikes. During blocked pursuit, probe the failed movement path and punch reachable obstructions. Enable terrain destruction by default while respecting explicit config values, mobGriefing, block protections and the shared block budget.

2026-11-04 01:44 — Rebuild the Giant Ghast model and recessed core

- Recreate the charcoal cube, integrated flank heads, inset red eyes, and longer independently swaying tentacles. Shape a stepped roof recess around an irregular red core.
- Match the multipart bounds to the new heads and walkable roof rim, and align the fireball muzzle with the central mouth. The recessed core remains a melee target from above during the execution phase.

2026-11-04 03:12 — Add the Giant Enderman boss encounter

- Add the 30-block Giant Enderman with a dedicated animated model, exposed rib cage, emissive face, multipart hit regions and three health-based combat phases.
- Add telegraphed swipes, stomps and shockwaves, safe teleportation, throwable terrain formations and escapable grabs. Terrain changes respect mobGriefing, block protection and a shared per-dimension budget.
- Add a Prismarine Colossus duel and head-tearing finisher, an operator spawn command, a Creative spawn egg and opt-in natural encounters in the End.

2026-11-04 03:07 — Add the Giant Magma Golem boss fight

- Add a 12-block articulated golem with original stone and emissive magma textures, a boss bar, and three combat phases with telegraphed attacks and capped magma cube waves.
- Cool and mine each arm independently. Removing both arms exposes the feet to TNT. Once knocked down, the golem can be finished with water from a bucket or the surrounding terrain.
- Persist encounter progress and add `/rntmagmagolem spawn [x y z]` and server settings. Natural spawning is disabled by default, and boss terrain damage respects `mobGriefing`.

2026-11-04 18:49 — Remodel the Giant Magma Golem after its Story Mode appearance

- Give the boss a gray cobblestone face and chest, recessed glowing eyes, a projecting nose, and oversized molten forearms and fists. Concentrate emissive detail in the eyes and arms, with smaller seams across the chest and feet.
- Render cooled arms with a separate stone atlas and remove the entire arm when destroyed. Spread the elbows in the resting pose and retain the existing strike poses. Cooling, arm hitboxes, and rendering continue to use the same joints.

2026-11-04 19:19 — Fix Giant Magma Golem slams and defeat

- Land a damaging ground slam with either or both available arms. Hold the terrain contact pose for ten seconds so players can cool and mine each arm independently. Keep the remaining arm planted when the other breaks, and resume saved holds without replaying the impact.
- Match arm hitboxes to the shared model pose and show stone chips where a pickaxe hits. Retain cooling and mining progress across openings.
- Start the existing collapse on lethal damage in any phase. The arm removal, TNT knockdown, and water finish remain available.

2026-11-04 20:05 — Rebuild the Command Block Wither model

- Give the opening phase a compact charcoal skeleton, three independently aimed vanilla Wither skulls, and a fixed Command Block seated in the chest. Align the head joints and hitboxes with the new model.
- Switch to the existing purple mantle and evolved head sequence at the Destroyer threshold.

2026-11-04 20:32 — Rebuild the evolved Wither Storm silhouette

- Shape the mantle around connected shoulders and an exposed Command Block. Give evolved skulls shorter jaws and subdued teeth while retaining the single eyes and beam origins.
- Bend and taper the tentacles between their existing attachments. Show outward-facing devourer mouths starting in the Devourer phase.

2026-11-04 20:54 — Follow the Wither Storm's Story Mode progression

- Keep the two ordinary skulls visible during the center head's transformation. At Stage B, transform both side heads together and keep all three clear of the mantle. Bring older two-transformed-head saves forward to the paired form.
- Grow from the exposed-core Stage A forms into Stage B, then the small, big and damaged Stage C silhouettes and Stage D. Tentacles emerge in Stage B, and the core becomes visible again when Stage C fractures at low health.

2026-11-04 21:30 — Refine Destroyer and keep evolved heads clear of the mantle

- Give Stage B a stepped mantle, smaller front-mounted skulls, two raised curling arms and shorter hanging tentacles.
- Keep evolved skulls on the body's interpolated shoulders and limit their aiming range. Beam direction and skull rotation use the same limits, so heads hold at the edge of their travel when a target moves behind them.

2026-11-04 21:58 — Fix Wither Storm hurt audio and skull volleys

- Play the existing hurt clip after health loss, with a larger audible radius as the Storm grows and a short delay between cues.
- Aim and visibly wind up one head at a time. Launch from the rendered mouth along the head's aim, skip invalid shots, and orient the flying skull to its velocity.
- Limit each volley to one shot per head with 10 ticks between shots. The configurable cooldown starts at 80 ticks and shortens moderately with each phase. The skull range is configurable up to 96 blocks.

2026-11-04 22:37 — Remodel the Wither Storm Destroyer after Story Mode Stage B

- Build a broad, layered mantle with low front skulls, two long curling upper arms, five smaller appendages and floating fragments. Reuse the storm skin and keep the other stages' models.
- Align each head's glowing eye, hinged grin, skull launch point and selection bounds with its new pose. Animate the Stage B arms from the torso and use the same curves for their tracked contact points.

2026-11-04 23:21 — Rebuild the small Devourer and extend Stage B

- Give Stage C (Small) its own tall, irregular mantle, three low glowing heads, one long raised side arm and six curved lower appendages. Use the same attachments for rendered heads, beam and skull origins, and tracked tentacle contacts.
- Keep the Stage B body and arms. Replace its attached debris clusters with individual client-side block fragments that spiral inward and shrink, using the original block textures when absorption records arrive.
- Require 24,000 active Stage B ticks by default before automatic evolution. Save the timer and continue counting absorbed mass during the hold. Explicit growth commands still apply immediately.

2026-11-05 01:20 — Rebuild the second Devourer as Story Mode Stage C (Big)

- Replace the generic big Devourer mantle with a towering, deep sloped slab of corrupted blocks, three low glowing heads, a heavy curved side arm and nine varied hanging appendages. Share head and neck attachments with tracked parts, beam eyes and projectile mouths; retain the existing growth thresholds and damaged form.
- Give existing tentacles staggered travelling idle bends, smooth target-driven extension and recoil, and cosmetic terrain-gripping motion using the existing debris packets. Keep the seven gameplay contacts; the extra rear curls are visual geometry. Raise only the big Devourer's terrain clearance to accommodate its longer hanging arms.
- Lift captured block fragments to an upper-mantle orbit, darken them to purple-black, then draw them inside and expire them. Reuse the bounded client particle batch without adding server entities or awarding growth again.

2026-11-05 17:54 — Give Wither Storm tentacles active combat and opportunistic mob hunting

- Keep players as the primary pursuit target, intercepting the densest mob clusters along the route within the existing 96-block targeting radius. Without a player destination, seek the largest nearby cluster using a complete local living-entity census.
- Animate server-owned reaches, whips, grabs, crushing grips and slams, including defense against intruders inside the mantle. Share attack endpoints with rendering and hit contacts; activate the big Devourer's three rear arms as gameplay parts.
- Send tentacle terrain impacts through the existing protected, budgeted consumption queue, including breakable obsidian. Grow new arms, allow severing to release prey, save arm damage and material-fed regrowth, and repair body health as material is consumed. Keep existing growth stages, commands, terrain settings and save identities.

2026-11-05 18:31 — Rebuild Thunderstorm as Story Mode Stage D

- Give Stage D its own massive, deep, curved mantle of layered corrupted blocks, recessed branching purple fractures with emissive variation, and three small low-mounted glowing heads. Share head attachments, eye and mouth transforms with tracking, tractor beams and skull attacks; retain the earlier stages' models.
- Curve and taper all seven Thunderstorm tentacles with independent travelling bends and the existing server-driven reach, grip and slam poses. Match their tracked contacts and terrain clearance to the new anatomy, and keep the budgeted absorption sweep within reach of the ground at maximum growth.
- Draw five enormous, tilted, turbulent debris rings independently of body rotation using a bounded client-only cube batch. Preserve gaps, varied fragments, inward streams and subtle purple motes. Default to 2,000 visible decorative fragments, with configurable limits, frustum culling and distance/graphics reductions; no decorative server entities or growth rewards.
- Route existing absorption records through an inner-ring orbit, gradual corruption and final upper-body absorption. Preserve weather, progression, terrain budgets, commands and encounter save identities.

2026-11-05 19:55 — Fix Stage D frame stalls and retain its rendering geometry

- Skip empty chunk sections in Stage D's full-size fluid checks instead of visiting every air block on both client and server. Preserve material bounds, the water loaded-area guard, vanilla water entry/current behavior and lava contact detection.
- Replace first-render voxel generation with a premerged mantle asset and a separate distant mesh. Keep the curved silhouette and emissive fractures, and leave earlier stage models unchanged.
- Cache vortex fragment properties and sector geometry at four detail levels. Retain all five rotating, turbulent rings, inward streams, central motes and the existing configurable fragment cap; zero fully disables decorative vortex rendering.
- Batch all visible animated tentacles using shared segment templates and distance detail levels. Cull the mantle, arms, heads and vortex sectors by their bounds while preserving the existing poses, captures, attacks, absorption budgets and save identities.

2026-11-05 21:45 — Repair Wither Storm pursuit, feeding, beams, audio and opening form

- Enable existing player-trail pursuit for every Storm in its dimension, select the nearest eligible player with switching hysteresis, join the nearest forward segment when switching players, and retain destinations through lost visibility, expired waypoints and temporary absence. Keep local attack targets from overriding navigation.
- Replace exhausted vertical sweeps with resumable adjacent sections, scaling section size and coordinate allowance by stage. Share configurable coordinate and elapsed-time limits with protected skull and tentacle impacts; retain explicit existing limits and award growth only for removed blocks.
- Project fading purple tractor-beam light onto visible terrain along the actual beam ray without modifying world lighting. Send finite boss sound cues to distant listeners with gradual attenuation and duplicate suppression.
- Render flaming projectiles with the vanilla Wither Skull mesh and texture. Apply server-owned fire damage, ignition and protected, budgeted terrain impacts without item drops.
- Base Stage A on the actual vanilla Wither model, attaching an open rib cage and visible command block to its animated spine. Align ordinary head contacts and launch points with vanilla proportions; keep transformed and later-stage models and progression.

2026-11-05 23:35 — Restore Wither Storm terrain coverage and stage-scaled consumption

- Replace center-height cubes with world-aligned, resumable sections that discover each column from above, include transparent upper structures, and peel bounded depths before revisiting lower layers. Retain progress while moving, cover partial edge sections, skip empty chunk storage, and periodically rediscover rebuilt or previously protected blocks.
- Scale the feeding footprint with the mantle, heads and intact tentacle endpoints. Share flight clearance with vertical feeding reach and anchor columns to their own surfaces so elevated Devourers consume buildings and ground beneath them. Keep beam-focused feeding and queued skull/tentacle impacts on the same protected removal path.
- Count successful removals separately from configurable scan steps and elapsed time. Raise stage removal ceilings to 32/64/256/1024/2048/2048/4096 and new-config defaults to 4096 removals and a 48-block radius; retain existing explicit caps. Preserve growth rewards, item suppression, bedrock/block/tile protections and mobGriefing.
- Sample visual fragments across actual consumed positions independently of removal throughput. Preserve upper-body lift, hover, blackening, absorption and the existing vortex, without physical debris entities. Restart disposable scan cursors on load while retaining saved mass and queued impact compatibility.

2026-11-05 23:59 — Add shared humanoid restraints, sedation and four interactive devices

- Add persistent handcuffs, lead-based escorting, removable straight jackets and bounded fictional tranquilizer onset, unconsciousness and recovery. Keep the original entities and restore suspended native AI tasks on release; exclude bosses and oversized entities, with player support explicitly disabled by default.
- Add a multiblock guillotine, fuel-heated bronze bull, RNT-powered electric chair and consumable injection table. Share UUID/token occupancy validation, server-owned positioning and damage, deliberate manual confirmation, release interlocks and unload/reload cleanup.
- Add native textured 3D models with moving blades, hatches, restraints and injectors, biped/villager captive poses, bounded electrical/heat effects, existing RNT sound cues, crafting recipes and English descriptions. Add server configuration for support, duration, damage and device behavior.

2026-11-06 01:08 — Extend Wither Storm visibility and add terrain-aware idle flight

- Share stage- and growth-scaled distance checks between the root and all attachments, with a configurable 1,024-block maximum. Adjust only their native tracking entries beyond vanilla's view-distance clamp, reconcile observers across watched-chunk unload/re-entry, and retain native packets and the existing local flight ticket; never load distant chunks for visibility.
- Relocate moved attachments through native chunk bookkeeping when their own tick is suspended near a loaded boundary, keeping tracking and saved chunk membership aligned without double-ticking or loading more terrain.
- Cull against conservative render bounds covering the full model, raised/attacking arms, heads and Stage D vortex instead of attachment-sized bounds. Preserve hitboxes, shaders, lighting, beams, animations and existing distant Stage D detail reductions.
- Wander when no eligible player is present, even if saved pursuit trails or local mobs remain. Choose bounded loaded flight legs within a configurable 64-block radius, check terrain corridors periodically, climb before crossing hills, retain stage speeds/clearances and pause randomly between destinations. Player availability immediately restores normal trail pursuit without resetting saved encounter state; feeding, tentacle combat and destruction continue during idle flight.

2026-11-06 02:41 — Add the persistent Mars gate and Gemini invasion scheduler

- Record the shared `geminiMarsVisited` milestone only for server players actually arriving in the existing Mars/Duna dimension, checking its configured ID, provider and registered celestial identity. Preserve the milestone across player lifecycle changes, server restarts and disabled invasions without changing space travel.
- Add an Overworld-owned Forge world save and a shared server-side activity gate for future Gemini creatures, infections, structures and events. Old worlds remain locked until an actual Mars arrival is observed.
- Add configurable invasion enablement and randomized 20–40 minute event opportunity intervals. Persist the countdown, pause while disabled or without Overworld players, and dispatch one synchronous extension event per interval without unlock bursts or offline catch-up. This foundation adds no Gemini mobs or event effects.

2026-11-06 12:14 — Add the Mars-gated Gemini Woodcrawler

- Add a tall, single-eyed forest predator with a custom textured 3D model, eight independently articulated two-segment legs, stalking and sprinting gaits, wall-climbing foot placements, crouching and a delayed attack pose. Append registration IDs and isolate its AI and client renderer from existing mobs.
- Gate natural forest spawns, bounded invasion encounters and ongoing hostile behavior through the shared Overworld Mars milestone and Gemini enable setting. Configure detection, stalking, position memory, spawn weight and the loaded population limit for new spawns.
- Stalk players and villagers silently before charging, retreat briefly after losing sight and search the remembered occupied position for entrances. Bound target scans, entrance scans, path refreshes and climbing; knock at nearby wooden doors and break them with mobGriefing and protection checks. Require close hitbox distance, clear strike rays and a cooldown for melee attacks, and exclude the Gemini faction from prey selection.
- Store one captured weakened villager, including its UUID, trades and mod data, in the carrier's NBT. Restore captives when the carrier dies or Gemini activity is disabled, retaining the carrier if release is obstructed or vetoed. Expose a server-side conversion event after a holding period; no Fake People implementation is included.

2026-11-06 16:50 — Add Mars-gated Fake People from Woodcrawler captives

- Add a separate hostile Fake Person entity with a native villager disguise, profession skin, child size, custom name and preserved victim UUID. Use local wandering/door AI, distant player following, stationary staring, subtle model twitches and occasional low-pitched villager sounds without changing vanilla villager AI or trading.
- Reveal a distorted humanoid on damage, interaction or close visible approach; pursue with short speed bursts, bounded path refreshes, visibility-checked melee and retaliation against non-Gemini attackers. Remain exposed and return to wandering after losing a target. Allow one probabilistic apparent-death recovery, saved across reloads, with no premature loot or kill callbacks; fire deaths prevent recovery.
- Integrate the existing capture extension event with one saved conversion roll per captive, a configurable conversion chance and loaded admission limit, nearby collision checks and UUID duplicate rejection. Consume the original snapshot only after a successful spawn; retain failed or blocked captives for existing lossless release. Preserve unknown modded villager subclasses and external replacement handlers. Synchronize disguise/reveal/recovery state and retain the shared Mars, enablement and Overworld gates. Add no independent natural spawns, infection or Deep Root Disease.

2026-11-06 17:45 — Add progressive, Mars-gated Deep Root Disease

- Reuse the disease registry and extended entity NBT for a separate six-stage infection: Exposure, Roots, Contraction, Bulbs, Sprouts and Terminal. Save loaded stage progress and per-infection duration snapshots; pause while unloaded, disabled, outside the Overworld or otherwise ineligible without offline catch-up. Preserve non-death player cloning and clear infection on death/respawn without changing rabies timelines or treatment.
- Add reversible stage-scaled movement penalties, brief bounded spasms, intermittent player nausea and native animated-torso root, bulb and sprout overlays using the existing flesh texture. Synchronize presentation to trackers and affected players; keep infection records private and restore movement when inactive or cleared. Terminal causes no disease damage, permanent immobilization or player replacement.
- Add independent duration, transmission and enable settings, opt-in Gemini carrier melee transmission through confirmed native wounds, and a shared gated exposure/conversion-eligibility API. Enable Woodcrawlers and Fake People as carriers; support survival players, vanilla villagers and suitable vanilla animals. Expose terminal non-player eligibility without implementing Nature's Mockery or activating conversion. Extend disease inspection and clearing with an optional `deep_root` selector while preserving the rabies default.

2026-11-06 18:00 — Add Nature's Mockery and terminal Deep Root conversion

- Add a rooted Gemini organic predator with an animated reddish flesh body, irregular branches, moving feelers and textured tendrils attached to synchronized live targets. Support short local chat imitation and distorted native sound cues without voice capture.
- Convert terminal infected vanilla villagers and suitable animals only after an admitted replacement spawns in loaded, unobstructed space. Preserve players, retain blocked hosts for bounded retries, and drop equine inventories and carried equipment safely. Reuse the canonical Mars, Overworld and disease enable gates.
- Capture original live entities temporarily, infect through Deep Root exposure, release players intact, and award gradual bounded growth only for confirmed non-player feeding kills. Save capture UUID/anchor/timers, nutrition, growth, colony origin, successful placement count and sterile offspring state in NBT.
- Add incremental air-only organic placement above suitable ground with tile, native, spawn and Forge protection checks. Bound colony radius, lifetime placement budgets, loaded spawn admissions and sterile offspring generation through configuration; preserve unrelated crops and plants.

2026-11-06 19:32 — Add the underground Gardener and persistent Gemini nests

- Add a large rooted miniboss with one tracking eye, six animated segmented tentacles and independently damageable limb regions. Share collision and render geometry for reachable grabs and windup slams; pull real captives using native movement, release on limb disablement or timeout, and retain no persistent victim immobilization. Increase aggression as health falls and limit lifetime sterile Nature's Mockery support spawns.
- Add rare post-Mars colonization of loaded underground caves without replacing world generation or loading chunks. Validate and build nests incrementally, preserve 256-block spacing and lifetime reservations, and save construction cursors and boss identity. Protect saved vanilla/NTM structure bounds, tile entities, constructed blocks and claims through bounded checks and Forge placement/break callbacks.
- Add stable nest tissue and radial infectious offshoots, connect sterile support colonies to existing Mockery growth, and use canonical Deep Root exposure. Gate new admissions and all ongoing activity through the shared Overworld Mars milestone, Gemini settings and difficulty. Save boss anchors, health, limb durability/recovery and summon budgets; end captures and in-flight attacks on reload. Add Gardener configuration and English names.

2026-11-06 18:50 — Add The Wretch and post-Mars abandoned woodland camps

- Add a territory-bound major Overworld boss with an original animated, mutated bear model: hunched uneven torso, elongated split skull, sinewy articulated arms, asymmetric hands and rooted organic growths. Share arm geometry between rendering, multipart hit regions and attack contacts; append entity IDs without changing other fights.
- Add server-owned player pursuit, slow territorial wandering, telegraphed knockback swipes and ground slams, interruptible grabs, sneak escapes, delayed throws and explicit recovery. Change attack selection and cadence as health falls. Heal only from confirmed feeding kills of eligible unprotected vanilla animals, with a bounded feeding cooldown.
- Generate rare torn woodland camps only in newly populated forest chunks after the existing shared Mars milestone. Reserve camp locations and boss identities in world data, retain 512-block spacing and one encounter per camp, and preserve defeat state without respawning missing bosses or loading chunks. Reject constructed terrain and tile entities; respect Forge placement/break protections and retain player placement exclusions for bounded natural terrain damage.
- Add boss enablement, initial health, camp frequency and terrain destruction settings, English names and configuration/gameplay documentation. Disabled, locked and Peaceful encounters become dormant; in-flight attacks and captures end safely on reload.

2026-11-06 20:20 — Integrate staged Gemini invasion progression and population limits

- Start rare forest Woodcrawler encounters and captive-to-Fake-Person conversion at the existing Mars milestone, then unlock Deep Root and Nature's Mockery, underground Gardener nests and organic spread, and Wretch camps after separate configurable 3/5/7 Minecraft-day delays. Save phases and active elapsed ticks; migrate older Mars-unlocked saves to phase 1 without immediate mass activation or offline catch-up.
- Scale natural Woodcrawler admission, scheduled encounters and Gardener discovery by phase. Share loaded Gemini membership and combined/species admission caps across conversions, support summons, buds, nests, camps, eggs and commands, without repeated general world population scans. Pause capped boss jobs without exhausting retries; let unloaded nests yield to loaded jobs.
- Preserve saved creatures, Woodcrawler captive snapshots, infection records and structure reservations when disabled or reconfigured. Pause hostile activity, transmission, growth and generation through canonical server-side phase gates. Distinguish chunk loads from new NBT-bearing spawns, retain UUID safeguards, and keep entity/resource IDs and Mars travel unchanged.
- Add bounded local phase-3 coordination, faction damage exclusions, capped detailed prey/feeding scans and shared organic-contact throttling. Retain existing disease handling, colony radii, placement budgets, sterile offspring, boss behavior and protection callbacks.

2026-11-06 19:40 — Complete Gemini creature spawn eggs

- Register vanilla-style spawn eggs for Woodcrawler, Fake Person, Nature's Mockery, Gardener and The Wretch before their mod entity registrations; Forge previously rejected the first four as already registered, and The Wretch had no egg entry. Preserve existing FML save names, network entity IDs and legacy egg allocation order; omit multipart hit regions.
- Initialize and save egg-spawned Wretch territory independently of generated camps so its egg can admit a functional boss. Preserve phase-4, enablement, difficulty and loaded population checks, camp reservations and camp-only terrain damage. Existing saved camp bosses retain their original ownership rules.

2026-11-06 19:59 — Make Gemini spawn eggs independent of natural invasion gates

- Persist manual spawn origin through native entity Forge data. Eggs bypass Mars/phase gates, Gemini disable settings, spawn weights and population admission caps; their creatures retain active combat after reload and outside the Overworld. Peaceful behavior and duplicate-UUID rejection remain intact.
- Let manually spawned Gardeners activate at their placed surface anchor and Wretches use their saved standalone territory. Apply the manual policy throughout mobile AI, attacks, damage handling and rooted-creature activity instead of only bypassing initial admission.
- Identify native natural initialization through Forge's SpecialSpawn event and mark scheduled Woodcrawler initialization explicitly. Natural admissions, invasions, camp/nest generation, disease progression and environmental spread retain their existing gates; eggs do not advance world progression.

2026-11-06 22:07 — Complete Woodcrawler capture and persistent cocoon conversion

- Replace indefinite carrier-only conversion with explicit grab, placement, processing handoff, recovery and return-to-hunt states. Prefer villagers, abandon timed-out hunts and repeated path failures, and reset attack/navigation/awareness timers after capture or abort.
- Add a visible organic entity-based cocoon at the capture site, with complete saved villager data, configurable loaded processing delay, a saved conversion decision and bounded conversion retries. Release wounded villagers on failed rolls, timeout or destruction; retain a visibly broken cocoon if safe release is obstructed or vetoed. Consume its snapshot only after successful output admission, retain UUID safeguards and keep output in the same chunk. Migrate legacy carrier snapshots and saved decisions.
- Route cocoon conversion through the existing Fake Person system and extension event. New events expose cocoon/world with no live carrier dependency; retain the legacy event constructor. Preserve Fake Person appearance, identity and admission caps. Require the existing shared Mars milestone for manual Gemini admission/activity as well as natural progression; retain post-Mars manual phase/configuration overrides.

2026-11-07 00:36 — Give Woodcrawlers a towering silhouette, prey hooks and burrowing

- Replace the broad spider-like body with a narrow, approximately 5.6-block-tall frame, exposed ribs and spine, six thin angular support legs, raised folded raptorial arms, a small tilted head, moving sensory filaments and paired dangling hooks. Use the supplied Gemini images for the ribbed center and inverted-V upper limbs. Retain independently solved feet, stalking, low doorway crawling, wall climbing and legacy captive presentation.
- Synchronize live prey IDs and an extend/grab/retract timeline. Allow five-block player/villager grabs after a visible windup, one server-authoritative native attack and a brief bounded horizontal pull, with cooldowns and continuous obstruction checks using the rendered hook centerlines and low body rays. Damage interrupts grabbing; wounded villager capture still transfers lossless ownership to the existing cocoon/Fake Person pipeline.
- Add synchronized enter/submerge/emerge states for soil ambushes and wounded retreats. Keep the real body at the surface and vulnerable, restrict short repositioning to clear loaded natural-ground corridors, and forbid burrowing during capture, climbing or crawling. Bound terrain probes, preserve cooldowns and crawl posture in NBT, safely surface on reload and invalidate navigation after emergence. Fold older shorter-body saves beneath existing roofs instead of relocating them. Preserve the canonical Mars milestone and manual/natural invasion admission rules without changing shared Gemini systems.

2026-11-07 01:52 — Add a shared Mars gate command for Gemini testing

- Add operator/console `/visitedmars true|false` and a no-argument status query with boolean tab completion. Change the existing world-wide saved Mars milestone so all five GHE spawn eggs can be tested without space travel, while natural invasions retain their phase, configuration and dimension rules.
- Lock both manual and natural activity through the existing central gates, retaining saved creatures, invasion phase progress and the event countdown across toggles and restarts. First unlock starts phase 1; later unlocks or qualifying Mars arrivals resume saved progress.

2026-11-07 02:32 — Rebuild the Underground Gardener and add the Ocean Resident

- Replace the small cuboid Gardener with a sprawling approximately 26-block-wide articulated creature, an irregular dark organic carapace, angular branching limbs and a circular full-bright yellow-white eye with a soft halo. Add coordinated crawling, body lowering, independent idle motion, live-target tendrils and short collision-tested territorial steering. Preserve limb durability, capture/release, infections, support summons and protected slams; briefly restrain visible prey on Garden tissue and growth.
- Add a distinct approximately 32-block-tall Ocean Gardener with a narrow floating body, uneven hanging limbs, delayed underwater sway, a separate skeleton/model, 520 health and slow water-only movement. Reuse shared capture and damageable limb mechanics, including native damage to boats and supported entity vehicles, bounded territory, target loss/return and solid-terrain obstruction checks. Keep decorative forks entity-free and extend tracking/render distance for both forms.
- Introduce rare loaded deep-ocean encounters through the existing Mars-gated phase-3 progression, separate configurable ocean enablement/admission/rarity settings, a dedicated spawn egg and persistent 512-block territory reservations. Require full underwater spawn clearance; preserve the saved Mars milestone and travel system. Expand new Garden jobs with bounded protected construction, revalidate unfinished legacy jobs, retain completed/defeated nests and migrate saved zero-speed Gardeners without resetting health or limb state.

2026-11-07 02:51 — Fix Ocean Gardener spawn egg admission

- Exempt manual Ocean Gardener spawns from natural deep-ocean clearance checks so the egg works after the Mars gate is unlocked. Preserve natural spawning checks and water-only activity.

2026-11-07 03:16 — Connect Ocean Gardener legs and enable land behavior

- Root all six Ocean Gardener legs inside the lower body and keep their attachment points fixed through idle sway, walking, attacks and disabled-limb poses. Use a feet-based origin and a narrow full-height collision box for the approximately 32-block-tall silhouette; migrate legacy positions and anchors while preserving health and limb state.
- Remove water requirements from activity, prey eligibility and travel clearance. Retain the existing territorial steering and capture system, add supported land movement and native stepping, and preserve slow underwater altitude correction, solid-limb obstruction checks, wandering, Mars/difficulty gates and natural deep-ocean admission. Retain saved natural residents after they move onto land. Shared hooks leave Underground Gardener behavior unchanged.

2026-11-07 03:47 — Add hanging stations and restore restrained entity transport

- Add a craftable Noose and wooden Hanging Station multiblock with an attached rope, opening trapdoor, synchronized drop/sway and a four-second server-owned execution sequence. Reuse captive UUID/placement-token ownership, safe pre-activation release, loaded-tick NBT persistence and existing device confirmation controls; retain native damage cancellation and player-restraint opt-in.
- Share empty-hand/lead grab toggling across handcuffs and straight jackets. Suspend wandering and combat while preserving native collision, pushing, gravity and collision-tested dragging. Remove free-entity position anchors, prevent grab theft, and clear session grabs on release, death, logout, dimension changes, excessive distance and entity reload.
- Transfer grabbed jacketed or cuffed captives into all five execution devices, clearing the grab while retaining restraints. Preserve existing registry names, registration order, device packet layouts and saved device tokens. Add English item/control descriptions and reuse wooden-door and cloth sounds for the station and rope.

2026-11-07 04:24 — Rebuild the mature Wretch's anatomy, animation and sound

- Replace the stocky bear with a roughly 20-block-tall, hunched organism: elongated fused torso, narrow exposed skull, uneven shoulders, fixed-length skeletal limbs, articulated asymmetric fingers, rooted pores and seven fused human grafts, including a Glenn-inspired limb face. Treat unseen anatomy and exact proportions as interpretations; assign no identities to other grafts or a confirmed speaker to the vocal effects.
- Share the feet-based skeleton between rendering, articulated strike contacts and damageable multipart regions, including hands, feet, head and torso. Keep the navigation box separate from physical targeting, reject hits through empty limb space, plant alternating feet on loaded terrain, interpolate attack transitions and add dormant breathing, awakening, head tracking, tissue reactions, damage and death motion. Cosmetic window, wall and roof gestures do not alter AI or buildings. Preserve health, damage, existing attack windows, capture/feeding rules, entity identities and saved encounter data.
- Extend Wretch tracking and cull against the full visual envelope using Forge 1.7.10's rendering path. Reuse model objects, the flesh texture and a neutral detail texture; render embedded anatomy without extra entities. Replace generic mob audio with 27 original synthesized mono Vorbis clips across 15 positional events, bounded irregular foley, distant heavy steps/impacts and localized quiet sounds. Include no episode recordings or new dependencies.

2026-11-07 05:39 — Replace Wretch pursuit with a persistent horror encounter controller

- Introduce sixteen server-owned encounter states with activity-driven dormancy, complete awakening, concealed stalking, observation, prediction, ambushes, charges, bounded searches and return behavior. Retain the mature anatomy, original sounds, health/damage and combat/capture/feeding timelines; grabbing and assimilating states expose existing callbacks for the next stage. Save bounded UUID/position memories and loaded-time budgets while clearing live targets, routes and captures on reload.
- Replace short vanilla full-height paths and universal attack stops with local surface routes, obstacle exclusions, native stepping/gravity, anatomical clearance, soft foliage handling and accelerated behavior-specific speeds. Preserve movement during suitable attack frames and keep articulated contacts in the same coordinate system as rendering.
- Associate bounded structure checks with player activity, then circle, watch, tap, scratch or reach roofs before provoked breaches. Replace invisible pursuit excavation and broad structural slam removal with budgeted hand/finger contacts, protection events, tile safety and explicit player-construction permission, disabled by default. Retain old protection columns and add conservative placement/provenance records; unknown legacy construction remains protected.
- Preserve Mars/Duna progression, manual eggs, camp identities, spacing and defeat state. Default to one persistent lifetime natural encounter, with an explicit optional multiple-Wretch setting; never remove older saved encounters or respawn defeated bosses. Add small tracks and fallen wood to newly generated camps and document new configuration and compatibility boundaries.

2026-11-07 05:52 — Make Wretch eye and multipart rays dedicated-server safe

- Replace the preceding presentation stage's client-only interpolated player-position calls in melee/contact validation with native world-space eye coordinates. Use the same server-safe calculation for target observation, retaining visible anatomical hit validation without loading a client-only Minecraft method on dedicated servers.
2026-11-07 15:53 � Complete Wretch capture, assimilation and victim integration

- Finish the preceding AI stage's ray ownership fix: independent enclosure rays retain their activity origin, and visibility/contact rays cannot mutate shared skeletal joints through vanilla 1.7.10 traversal.
- Favor articulated hand grabs and add interruptible hold, pull and root-enclosure phases for eligible players, villagers and explicitly opted-in humanoids. Keep victims live until ordinary cancelable damage/death succeeds; preserve native inventory, drops and respawning. Release on escape, arm damage, invalid movement, logout, death and unloading, with temporary recapture and landing protection. Keep native floating validation from kicking a physically supported captive without granting flight or changing server policy.
- Save up to twelve distinct visual victim records, synchronize bounded appearance snapshots and render varied fused tissue, faces, torsos, hands and irregular individual reactions without retaining absorbed entities or expanding the body. Preserve the mature skeleton, camps and saved health; shorten recovery and restrict slam damage to actual articulated contact.
- Reuse the existing Deep Root exposure boundary once during root enclosure, retain disease progression and defer terminal Mockery replacement only for an actual live Wretch captive. Ordinary attacks do not become automatically infectious. Protect unknown legacy terrain conservatively when player-structure damage is disabled. Default livestock healing and health-threshold modifiers off; add adjustable rare positional human-like vocalizations using the original audio and document lore uncertainty.

2026-11-07 19:05 — Restore manual Gardener spawning and complete Ocean Gardener grabs

- Admit underground Gardener eggs using loaded, collision-free body space rather than natural nest and extended-limb clearance. Retract obstructed idle limbs using synchronized shared pose geometry, and keep manual activity independent of the full visual envelope. Preserve Mars/difficulty gates, natural admission and nest generation, entity identifiers and saved state.
- Replace the Ocean Gardener's fixed 80-tick hold with an upper-body lift that measures actual victim progress, follows the victim with the active tentacle, and attempts an ordinary 8-damage finishing attack. Retain native movement/collision, arm damage and victim validity checks; release stalled captures and enforce a total capture limit. Successful hits recover without escape immunity, while escapes, interruptions and rejected finishing damage retain temporary protection. Use server-safe player eye coordinates for limb interruption rays.

2026-11-07 20:15 — Rework nocturnal Woodcrawler hunting and complete Gardener feeding

- Accelerate Woodcrawler pursuit toward 2.5 times its previous maximum while preserving slow stalking, collision-resolved movement and near-capture braking. Add saved daytime soil dormancy, dark-route preferences, bounded light/fire checks, villager mimicry, non-destructive door harassment and connected, interruptible hardness-timed proboscis attacks against nearby heard or remembered prey. Reject unbreakable blocks, tiles and protection vetoes without changing terrain; retain native confirmed-wound infection handling.
- Default cocoon outcomes to 90% Fake Person and 10% Woodcrawler using the existing configurable probability and saved decision. Keep new captive villagers alive until admitted output, release the original on interruption, and retain legacy snapshot restoration and UUID deduplication; pause failure budgets at species/combined population limits. Converted Woodcrawlers use normal AI and persistent identity, with conversion admission independent of forest spawn weight and without inheriting population bypasses.
- Integrate Woodcrawler UV vulnerability with actual powered FEL beam contacts and existing UV projectile modes; ordinary lighting remains avoidance-only. Preserve other creatures' beam effects and existing ionizing radiation behavior. Add obstruction-aware flame avoidance and escalating off-contact heat without overlapping native burning damage.
- Replace both Gardeners' acquisition checks during holds with active-limb attachment validation, collision-resolved pulls and progress-aware feeding. Account for the Ocean Gardener's upper body; retain victims after bites, repeat valid feeding damage, synchronize tentacle tips and supported player movement, and release stalled or interrupted captures with recovery and appropriate escape immunity. Preserve earlier manual-spawn and pose corrections, existing entity identifiers, invasion progression and saved creatures; add only disposable proboscis contact regions.
