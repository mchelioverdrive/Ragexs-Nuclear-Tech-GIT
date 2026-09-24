# RTM Performance Architecture Audit

This document tracks source-confirmed hot paths, completed low-risk performance work, and the direction for the later scheduler and network redesign. It complements `RTM_Performance_Architecture_Audit.docx`; the DOCX remains the original audit snapshot, while this Markdown file records changes in the active checkout.

## 2026-09-19 21:00 — High reward allocation and polling pass

### Implemented

#### Dirty machine synchronization

- Added opt-in `markNetworkDirty()` and `networkPackNTIfDirty(int range)` APIs to `TileEntityMachineBase` and `TileEntityTickingBase`.
- The opt-in path skips packet construction and serialization while state is clean, but still sends the existing once-per-second baseline packet for chunk reload recovery.
- Standard `TileEntityMachineBase` inventory mutations mark sync state dirty and expose `onInventorySlotChanged(int)` for local cache invalidation.
- Migrated the electric furnace and battery to dirty sync. Their cheap primitive comparisons cover all fields serialized by those machines.
- Battery control packets explicitly dirty network sync state.
- The RTG now suppresses unchanged electricity packets while retaining a once-per-second baseline.
- Legacy `networkPackNT()` behavior remains available for unmigrated machines. Its comparison buffer is now released when replaced, suppressed, invalidated, or unloaded.

Relevant sources:

- `src/main/java/com/hbm/tileentity/TileEntityMachineBase.java`
- `src/main/java/com/hbm/tileentity/TileEntityTickingBase.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityMachineElectricFurnace.java`
- `src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineBattery.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityMachineRTG.java`
- `src/main/java/com/hbm/packet/toserver/AuxButtonPacket.java`

#### Upgrade manager allocation removal

- Replaced per-call `SlotSignature[]` and `SlotSignature` creation with reusable primitive/reference signature storage.
- Existing `checkSlots()` remains content-aware for machines that may receive direct inventory mutations, but no longer allocates on the steady-state path.
- Added `checkSlotsIfDirty()` for machines with reliable inventory invalidation.
- The electric furnace uses strict invalidation for its upgrade slot, making its clean per-tick upgrade check an immediate return.

Relevant source:

- `src/main/java/com/hbm/inventory/UpgradeManagerNT.java`

#### Power network scratch reuse

- Replaced per-update provider lists, receiver-list arrays, demand arrays, and endpoint `Pair` objects with per-network reusable endpoint and primitive amount buffers.
- Cached the connection-priority enum array.
- Applied the same allocation-free scratch design to `sendPowerDiode()` with separate per-network storage.
- Preserved timeout pruning, bad-link pruning, receiver priority order, weighted distribution, transfer limits, cumulative energy tracking, and rounding compensation.

Relevant source:

- `src/main/java/api/hbm/energymk2/PowerNetMK2.java`

#### Node and endpoint maintenance

- `UniNodespace.updateNodespace()` now processes each `GenNode` identity once per world update, even when a multi-position node is stored under several occupied positions.
- The deduplication set is world-local and reused rather than allocated each tick.
- Battery provider keepalive now refreshes its already-known valid node network directly instead of performing a tile lookup and node lookup at its own coordinates every tick.

Relevant sources:

- `src/main/java/com/hbm/uninos/UniNodespace.java`
- `src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineBattery.java`

#### Fluid network local allocation reduction

- `PipeNet` now reuses a per-network subscriber snapshot and primitive demand buffer.
- The main `transferFluid()` path queries each subscriber demand once per transfer and reuses that value for weighted distribution.
- Subscriber order and weighted transfer behavior remain unchanged.

Relevant source:

- `src/main/java/api/hbm/fluid/PipeNet.java`

#### Global entity scan allocation reduction

- Preserved snapshot iteration semantics in `ModEventHandler` while replacing the new full `ArrayList` allocation on every world tick with a reusable world-local snapshot.
- The snapshot is cleared after use and stored in a weak-keyed world map.

Relevant source:

- `src/main/java/com/hbm/main/ModEventHandler.java`

#### Assembler and chemplant local caching

- Assembler recipe inputs, output, and process time are cached per lane and invalidated by template stack identity, metadata, or NBT content changes.
- Assembler and chemplant derived slot-index arrays are cached per lane instead of recreated throughout each tick.
- Chemplant template-to-recipe resolution is cached per lane and invalidated by template identity or metadata changes.
- The assembler no longer performs `recipe.toArray(new AStack[0])` during every `canProcess()` call.

Relevant sources:

- `src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemblerBase.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java`

### Confirmed but deferred

- Full dirty/event-driven energy distribution and persistent endpoint membership. Timestamp keepalive remains the compatibility model.
- Full topology dirty queue and scheduler. Stable nodes are still visited, and all active networks still update each server tick.
- Broad machine migration to dirty sync. Direct field writes require per-machine coverage before opting in.
- RTG adjacency caching. Direct machine-to-machine delivery and cable rediscovery share the same path, so a correct invalidation design is needed before reducing its six-neighbor probes.
- Vanilla furnace emission registry. A load/unload and burning-state lifecycle is needed before replacing the once-per-second loaded tile scan.
- Fire and carbon monoxide spatial indexing. The current de-duplicated player-area scan is still expensive, but a robust active-fire registry is beyond this local pass.
- Adjacent inventory caching for assemblers and chemplants. Chunk unload, tile replacement, rotation, and third-party inventory invalidation must be handled first.
- Chemplant tank-list allocation and tank setup consolidation. Derived classes add tanks dynamically through overrides, so a shared immutable view needs a deliberate API.
- Broad upgrade-manager strict invalidation migration. Unmigrated machines use the allocation-free content-check fallback.

## Architecture direction

Future work should keep immediate dirty work separate from scheduled future transitions:

- Event only: inventory, topology, mode, and configuration mutations enqueue reevaluation.
- Scheduled: known recipe completion or cooldown boundaries use a world-scoped due-task queue.
- Coarse: environmental compatibility polling runs at a documented lower cadence.
- Realtime: reactor physics, continuous movement, and other genuinely tick-sensitive systems remain per tick.

The next energy pass should introduce persistent endpoint state and explicit network dirtying while retaining a low-frequency timeout reaper as a compatibility fallback. Topology rebuilds should be isolated from energy distribution so stable networks do not pay both costs every tick.

## Validation status

- Source paths and mutation/lifecycle call sites were inspected against the active checkout.
- Distribution formulas and update ordering were kept structurally equivalent.
- No Minecraft client, integrated server, dedicated server, or in-game runtime validation was performed.
- `git diff --check` completed without whitespace errors.
- `gradlew compileJava --offline --no-daemon` reached Gradle configuration but could not compile because the local offline cache lacks ForgeGradle `1.2-1.0.12` and `org.osgi.service.prefs 1.1.2`. This is an environment/dependency-cache failure, not a source compilation result.

## 2026-09-21 14:44 — Event-driven MK2 power-network bridge (superseded interim state)

> This section records the first bridge implementation. The completed architecture and current diagnostics are documented in the 2026-09-22 section below.

### Confirmed previous behavior

- `UniNodespace.updateNodespace()` traversed the global `activeNodeNets` set and called `update()` on every power network at the start of every server tick.
- Most providers and receivers use `tryProvide()`, `trySubscribe()`, or direct `addProvider()` / `addReceiver()` calls as timestamp keepalives. Some endpoints refresh every tick while others refresh at machine-specific intervals.
- A three-second wall-clock timeout and `ILoadedTile` / invalid-TileEntity checks remove stale endpoints. A full dirty-only cutover was therefore unsafe: many endpoints mutate stored energy or demand directly and have no complete lifecycle or mutation hook.
- Node removal destroys the whole containing network. Remaining nodes are assigned new networks by the subsequent topology pass, so endpoint membership has to be reconstructed unless it is transferred during a merge.

### Implemented scheduler bridge

- Power networks now acquire their owning world from their first registered node and are tracked in a world-local registry.
- Each world has a deduplicated dirty-power-network set. Topology, supply, demand, and compatibility causes coalesce into one redistribution per scheduler pass.
- The queue is copied into reusable scratch storage before execution. A dirty signal raised during distribution enters the now-empty queue and is retained for the next tick; it cannot create a same-tick retry loop.
- Removed, merged, destroyed, and world-unloaded networks are removed from all world-local runnable sets. World unload also destroys remaining node networks and clears endpoint membership indexes.
- Non-power UNINOS networks retain their previous per-tick update path. This pass does not migrate fluid, pneumatic, or other network types.
- Power-network trackers are still reset every tick so cable-gauge `HE/t` accounting keeps its established meaning, but clean power networks no longer run the distribution algorithm.

### Dirty ownership

- Node creation, joining, leaving, merge, and expired-link reaping mark topology dirty.
- Endpoint registration/removal and default provider consumption or receiver transfer mark supply or demand dirty.
- Battery/FEnSU stored-power and mode-facing membership changes explicitly dirty their attached network.
- Diode transfer-limit and priority controls explicitly mark the receiver-side demand state dirty. Diode delivery also retains a next-pass demand invalidation after successful transfer.

### Persistent membership and compatibility

- `PowerNetMK2` now exposes persistent provider and receiver membership. Persistent entries do not require timestamp refreshes, remain deduplicated, transfer across network merges, and are cleared on network destruction.
- Standard batteries use persistent provider and receiver membership. FEnSU uses persistent receiver membership; its unusual below-block provider discovery remains on the legacy path.
- Battery chunk unload explicitly detaches both memberships without deleting the unloaded conductor node. Invalidation, block replacement, network rebuild, and world unload clear membership through the existing node/network lifecycle.
- Unmigrated endpoints continue using timestamp refresh and timeout cleanup. Networks containing any legacy endpoint are kept in a world-local compatibility set and request a deduplicated compatibility update each tick, preserving direct-field mutation semantics without restoring a global all-power-network update loop.
- Persistent-only networks receive a controlled once-per-second compatibility pass for stale loaded/invalid tile cleanup. This remains a temporary safety net while more endpoint lifecycle hooks are migrated.

### Diagnostics

- Diagnostics are disabled by default. Set `1.45_enablePowerNetDiagnostics=true` in `hbm.cfg` and restart the server.
- Run `/ntmpowerstats` as an operator to read aggregate completed-tick counters: active/redistributed/skipped/compatibility networks, registrations and refreshes, timeout removals, topology/supply/demand invalidations, endpoint totals and maxima, and distribution nanoseconds.
- Disabled diagnostics avoid per-event allocation and skip the nanosecond timer. The command allocates report strings only when invoked.

### Preserved behavior and remaining gaps

- Priority order, weighted distribution formulas, provider/receiver speed limits, diode routing, battery modes, energy tracking, remainder compensation, and server-thread authority remain in the existing distribution implementation.
- Legacy endpoints still depend on refresh cadence and timeout cleanup. The next safe migrations should target high-frequency producers/consumers that can prove attach, detach, stored-energy, priority, and transfer-limit invalidation across chunk unload and tile replacement.
- FEnSU's directional provider discovery and unusual endpoints that override `transferPower()` / `usePower()` need individual lifecycle review before persistent conversion.
- Source inspection and static queue/lifecycle checks were performed, and `gradlew compileJava --offline --no-daemon` completed successfully. Dedicated-server and in-game tests are still required for large merges/splits, chunk unload/reload, block replacement, diode chains, cross-mod endpoints, and cable-gauge accounting.

## 2026-09-22 23:32 — Completed persistent MK2 endpoint migration

### Final membership architecture

- MK2 provider and receiver registrations are now persistent and idempotent. Normal endpoints no longer refresh wall-clock timestamps and no longer expire after three seconds.
- Connection descriptors retain the conductor coordinate, direction, role, endpoint identity, and owning world needed to reconstruct membership after node-network destruction, merge, and split. Topology reconciliation runs after the normal UNINOS node pass, on the owning server thread.
- Common `TileEntityLoadedBase` invalidation and chunk-unload paths detach energy endpoints. Previously bypassing audio-cleanup overrides now call their superclass, and the two plain-`TileEntity` endpoints have equivalent explicit cleanup. World unload removes descriptor and membership references before destroying the node world.
- Batteries retain their node-owned bidirectional membership, including explicit mode-based role attachment. FEnSU keeps its directional below-block provider descriptor while a disabled output role advertises zero provider speed. Capacitor bus endpoints replace their descriptors only when orientation or the resolved bus target changes.
- Multiblock controllers remain the logical endpoints; proxy ports delegate transfer to the controller and invalidate the proxy-facing network after accepted delivery. This avoids registering the controller and proxy as duplicate storage.

### Explicit invalidation coverage

- Provider generation, item discharge, decay, internal storage caps, cross-mod conversion, and distribution withdrawals route through notifying energy setters and raise supply invalidation.
- Receiver item charging, machine consumption, forced drains/resets, launcher/turret use, conversion, and distribution delivery route through the same notifying setters and raise demand invalidation.
- Standard inventory mutations invalidate endpoint state through `TileEntityMachineBase`. Battery mode and priority controls, diode priority and transfer-level controls, Stirling cog state, charger readiness/capacity, and ICF assembly/capacity changes have dedicated invalidation hooks.
- Topology invalidation covers node creation, join, leave, merge, destruction, expired-link reaping, capacitor-bus target changes, and world cleanup. A dirty signal raised during redistribution remains queued for the following scheduler pass.

### Removed transitional behavior

- Removed the per-tick legacy-network set, compatibility dirty cause, compatibility refresh scheduling, once-per-second all-network sweep, registration refresh counters, endpoint timestamps, and timeout-based normal cleanup.
- Clean power networks now skip redistribution. A 100-tick integrity audit only removes invalid endpoints or stale inconsistent memberships; it never attaches or reconstructs normal endpoints, does not redistribute clean networks, and does not serve as a keepalive. Descriptor reconstruction is confined to explicit topology reconciliation.
- No separate cross-mod polling fallback remains. The existing HE/RF boundary adapters notify the MK2 side when conversion changes stored HE; their external RF API remains governed by the adapter's normal tile tick.

### Diagnostics and validation

- `/ntmpowerstats` now reports active networks; dirty processed and clean skipped networks; topology, supply, and demand invalidations; endpoint attachments and detachments; merges and splits; integrity removals; active endpoint totals and maxima; and redistribution time. Diagnostics remain disabled by default and allocation-free per event.
- The targeted offline `compileJava` task completed successfully. Source searches confirmed removal of the compatibility set, timestamp refresh, and timeout cleanup from MK2 power code. Minecraft was not launched.
- Runtime validation is still required for dedicated-server chunk unload/reload, block replacement, world unload, large conductor merge/split cycles, capacitor-bus edits, battery/FEnSU redstone modes, diode chains, multiblock proxy ports, HE/RF adapters, and cable-gauge accounting.

### Next phase

- The next performance phase should migrate selected high-cost machines to the existing dirty synchronization API and then design the separate machine scheduler. It should not be folded back into power-network membership or described as asynchronous work.

## 2026-09-23 19:21 — Rendering and OBJ/model-allocation audit

### Confirmed allocation lifecycle

The reported `net.minecraftforge.client.model.obj.Vertex[]` count is overwhelmingly initial model parsing, not geometry rebuilt by a TESR, block renderer, or every rendered frame.

- Forge 1.7.10 `AdvancedModelLoader.loadModel(ResourceLocation)` is not a cache. It selects a loader by suffix and calls `loadInstance()` on every invocation; the OBJ loader immediately constructs and parses a new `WavefrontObject`.
- Forge `WavefrontObject`, RTM's `HFRWavefrontObject`, and the HMF variant `HbmModelObject` all create one `Vertex[]` for every face and a second `Vertex[]` for every face carrying vertex normals. They also create the `Face`, texture-coordinate array, token/sub-token strings, and temporary vectors used to calculate the face normal during parsing.
- The 481 active OBJ/HMF resource references in client code currently comprise 250 HFR loads and 231 `AdvancedModelLoader` loads (227 Forge OBJ and four HMF). Of these, 479 references resolve to bundled resources and contain 385,159 face records, producing exactly 770,318 `Vertex[]` arrays in one complete pass through those load sites. This is the same scale and class as the approximately 770,000 allocations in the profiler capture.
- Before the duplicate-load fixes below, the registered renderers would have produced 772,550 parser arrays: 2,232 more arrays from redundant parses of `Sphere.obj` and `LilBoy1.obj`.
- `renderAll`, `renderPart`, `GroupObject.render`, `Face.addFaceForRender`, HFR equivalents, and VBO drawing iterate the stored structures. They do not clone faces or allocate new `Vertex[]` arrays.

This confirms category 1, model loading/parsing, as the lifecycle responsible for the observed `Vertex[]` allocations. A resource reload repeats the HFR VBO subset, but normal frames, TESR draws, inventory draws, and chunk rebuilds do not repeat parsing.

### Model ownership and duplicate-load cases

| Owner / representation | Instances after this pass | Lifetime and consumers |
| --- | ---: | --- |
| `ResourceManager`: Forge OBJ | 218 | Eager static objects shared by block, item, entity, and TESR renderers |
| `ResourceManager`: HMF | 4 | Eager static CPU-tessellated objects |
| `ResourceManager`: raw HFR | 94 | Eager static CPU-tessellated objects |
| `ResourceManager`: HFR VBO | 148 | Eager static parsed objects plus uploaded VBOs |
| Renderer-local: Forge OBJ | 9 | Static or renderer-construction lifetime; one resource per remaining owner |
| Renderer-local: raw HFR | 5 | Static renderer lifetime |
| Renderer-local: HFR VBO | 3 | Static renderer lifetime and included in VBO reload tracking |

- No TileEntity owns parsed OBJ geometry. Inventory, world/block, entity, and TESR paths overwhelmingly reference the same `ResourceManager` object. Renderer construction creates the small renderer-local set once during client renderer registration; chunk reload/rebuild does not construct models.
- `Sphere.obj` was parsed eight times: one parse for each of four direct effect renderers, three separate `RenderBlackHole` instances registered for black-hole, vortex, and raging-vortex entities, and the `RenderQuasar` subclass whose implicit superclass construction ran the same loader. All eight consumers now reference `ResourceManager.sphere`, leaving one parse and eliminating seven parses (1,120 `Vertex[]` arrays).
- `LilBoy1.obj` was parsed separately by `ResourceManager`, `RenderFallingNuke`, and `RenderMinecartTest`. Both renderers now reference `ResourceManager.bomb_boy`, leaving one parse and eliminating two parses (1,112 `Vertex[]` arrays).
- After that consolidation, there are 477 active `.obj` references for 474 distinct OBJ resource names. The only repeated names are deliberate representation variants: `bot_prime_head.obj` has Forge and raw-HFR owners, while `rbmk_element.obj` and `rbmk_rods.obj` each have Forge and HFR-VBO owners. Their consumers depend on different concrete APIs or upload behavior, so they were not merged speculatively.
- Two referenced paths, `models/BombGenericLarge.obj` and `models/TheGadget3.obj`, are not present under the bundled main resources. They are renderer-local Forge loads and contribute no faces to the resource-derived allocation total. This audit records the existing asset gap but does not change unrelated renderer behavior.

The practical architecture is therefore already close to `ResourceLocation -> one shared parsed model` within each representation. The remaining exception is where a resource intentionally has both a Forge and HFR/VBO representation; unifying those requires a common prepared format, not merely a map around `AdvancedModelLoader`.

The shared objects are read-only by convention, not by type: Forge exposes mutable public vertex and group lists. No audited renderer mutates them, but a future prepared representation should enforce immutability instead of exposing the parser's mutable graph.

### Rendering, copying, and transforms

- Ordinary GL matrix transforms do not modify or copy model vertices. Animated machine sections render named groups from the same parsed model after changing the matrix; static and moving groups do not own separate parses.
- `ObjUtil` walks Forge faces and sends transformed scalar coordinates to the tessellator. It never creates `Face`, `Vertex`, or `Vertex[]` structures. The implemented primitive rotation path also avoids its previous per-face/per-vertex `Vec3` temporaries.
- `RenderMirror` likewise walks the shared `solar_mirror` faces. Its aimed-mirror path still creates a `Vec3` per submitted vertex, but it does not create model geometry or explain the profiled `Vertex[]` class. It should be selected by a steady-state allocation stack before receiving a separate change.
- HFR VBO construction parses the full HFR object and then copies scalar position, UV, and normal values into direct upload buffers. The buffer builder no longer creates three temporary `float[3]` arrays per uploaded vertex or placeholder texture-coordinate objects.
- `renderOnly` and `renderAllExcept` may receive Java varargs arrays from individual call sites, and `getPartNames()` creates a list when queried. Neither path creates OBJ `Vertex[]`; neither appears to be the reported allocation source.
- The unused `WavefrontObjDisplayList` conversion compiles groups once when explicitly constructed. No active `.asDisplayList()` consumer was found, so it is not part of the current allocation lifecycle.

### Cache and resource-reload lifetime

- Forge OBJ and HMF objects have no RTM cache or reload hook. The current shared instances live for the client class-loader lifetime and remain unchanged after a resource-pack reload.
- Raw HFR objects also have no reload registration and remain unchanged after a resource-pack reload.
- HFR VBO wrappers register themselves in a static strong list. The resource reload listener reparses all 151 VBO-backed resources, deletes their old GPU buffers, and uploads replacements. Those resources contain 229,268 faces and recreate 458,472 `Vertex[]` arrays on every full reload.
- Minecraft invokes a newly registered reload listener immediately. In the normal `ClientProxy` order the listener is registered before renderer registration and before the obvious `ResourceManager` consumers, so its first callback normally sees no VBO wrappers. If another path initializes them earlier, that callback reparses the already-created wrappers.
- Reload coverage is therefore internally inconsistent: the VBO subset refreshes, while 227 Forge OBJ, four HMF, and 109 raw-HFR instances retain pre-reload geometry. A future shared cache cannot safely fix this by clearing a map because renderers hold long-lived object references; it needs stable handles or in-place/atomic replacement.

### Current conductor and machine rendering architecture

- Ordinary power cables use `RenderCable` or `RenderCableClassic`; ordinary fluid ducts use `RenderTestPipe` or `RenderBoxDuct`. These are `ISimpleBlockRenderingHandler` chunk/block renderers, not TESRs.
- Connectivity is read while the chunk render is built. `RenderCable` and `RenderTestPipe` select groups from shared `ResourceManager` models; the classic cable and box duct emit primitive cuboids. Minecraft's chunk display-list/render-chunk cache retains the result until block or neighbor invalidation.
- No conductor renderer creates connection `Face`, `Vertex`, `Vertex[]`, model, list, or topology-helper objects. A separate 64-mask geometry cache would duplicate the existing chunk cache and is not justified by this allocation class.
- HFR-backed machine models are generally VBO compiled. TESRs apply transforms and draw named groups without rebuilding geometry. Legacy Forge machine models remain CPU-tessellated, but their static face arrays are parsed once and reused; any conversion should be selected by render self-time rather than the startup allocation capture.
- `TileEntityPipeBaseNT` is not constructed by a block renderer or TESR. Its separate profiler count still needs its own allocation call tree and is not explained by model parsing.

### Implemented changes

- Added one shared Forge `Sphere.obj` model to `ResourceManager` and redirected all eight effect-renderer owners to it.
- Redirected the falling-nuke and test-minecart renderers to the existing shared `LilBoy1.obj` model.
- Replaced per-face and per-vertex `Vec3` creation in shared icon-remapped OBJ rendering with equivalent primitive rotation math.
- Changed HFR VBO assembly to write scalar floats directly into its buffers and avoid placeholder UV allocation.
- No parser format, rendering output, animation grouping, texture/material selection, TileEntity lifecycle, chunk invalidation, or network/topology behavior changed.

### Is a prepared/compiled geometry layer justified?

Not as a response to per-frame garbage: the `Vertex[]` allocations occur during parsing and reload, and ordinary draws reuse them. A prepared layer may be justified if startup pause, resource-reload pause, or retained heap remains important after measuring those phases separately.

The highest-value candidate is the HFR VBO path. It currently retains the complete CPU face graph after uploading 151 immutable GPU models so that it can reparse/rebuild on reload. A deliberate replacement could parse once into immutable packed primitive arrays plus group ranges, upload from that representation, and retain only resource identity/options and the prepared data required for reload. A shared cache key must include parser semantics such as HFR smoothing, not just the `ResourceLocation`, unless a common raw parse can derive those variants.

Such a layer should expose stable reloadable handles and atomically replace prepared contents so existing renderers remain valid. It must preserve named groups, triangle/quad rules, smoothing normals, UV conventions, icon-remapped Forge consumers, and custom HMF UV behavior. The current evidence supports designing and profiling that layer, but not replacing all three loaders in this pass.

### Remaining measurements and next steps

1. Record an allocation profile that starts after client initialization. `Vertex[]` should disappear from the steady-state top list unless a reload or unexpected constructor runs.
2. Profile one explicit resource reload. Expect the HFR VBO registry to recreate approximately 458,472 arrays after the targeted machine-model promotions; verify pause time and retained-heap behavior before changing representation.
3. Capture retained heap by loader/owner, especially the parsed HFR graphs retained behind VBO wrappers. Allocation volume alone does not show whether compact prepared storage will materially reduce live heap.
4. Add loader instrumentation only if runtime evidence is still ambiguous: count parses by resource and loader at startup and reload, then remove or gate the instrumentation after measurement.
5. Treat resource-reload consistency as a correctness prerequisite for any general shared cache. Use stable references rather than cache eviction that leaves renderers pointing at stale objects.
6. Continue to select individual CPU-tessellated TESRs or block models by measured render time. Their use of shared parsed geometry does not itself justify a renderer rewrite.

### Validation status

- Source and resource inventories covered every active `.obj` and `.hmf` literal, direct and indirect loader call sites, the shared registry, renderer constructors, item/world/TESR consumers, loader implementations, VBO/display-list conversions, and resource-reload registration.
- Array totals were derived from each referenced bundled file's face syntax, including the second normal array only where present.
- Targeted offline Java compilation and final diff validation are recorded with this change. Minecraft was not launched, so visual parity, actual startup/reload allocation counts, reload correctness, and retained-heap improvement still require runtime testing.

## 2026-09-23 20:00 — Pipe and cable rendering audit

### Confirmed before architecture

- Ordinary round cables (`RenderCable`), classic cables (`RenderCableClassic`), round fluid ducts (`RenderTestPipe`), and box ducts (`RenderBoxDuct`) are all `ISimpleBlockRenderingHandler` implementations. They emit world geometry only while Minecraft builds a chunk render; none is a TESR and none rebuilds geometry every displayed frame.
- Chunk invalidation is already the persistent world-geometry cache. A connection or visual state change causes the affected block/chunk render to be rebuilt, after which the resulting display-list geometry is reused by normal frames.
- Each chunk rebuild still resolves live connectivity from the six adjacent positions. `Library.canConnect()` and `Library.canConnectFluid()` use block/TileEntity connector contracts and allocate no direction helpers, lists, vectors, or geometry.
- `RenderCable` selected named groups from the shared 10-group `cable_neo.obj`. Every selected piece called `ObjUtil.renderPartWithIcon()`, which rescanned all model groups and recomputed six zero-angle trigonometric values plus vertex/normal transform arithmetic.
- `RenderTestPipe` did the same against the shared 14-group `pipe_neo.obj`, once for the material pass and again for the fluid-color overlay. It repeatedly resolved the same group names and repeated identity transforms for both passes.
- `RenderCableClassic` already emitted primitive scalar vertices directly. It did not create temporary arrays, lists, vectors, buffers, models, or helper objects during world rendering.
- `RenderBoxDuct` already emitted primitive cuboids through a shared `RenderBlocksNT` instance. Its expensive path was indirect: every `renderStandardBlock()` face called `FluidDuctBox.getIcon()`, and every icon query repeated all six connectivity tests. A straight duct could therefore perform 42 connectivity tests during one block build; a seven-cuboid junction could perform up to 258.
- Inventory rendering used the same shared OBJ resources for round cable/duct items and direct primitive geometry for classic/box variants. It parsed no models and created no temporary model geometry.

### Implemented 64-mask geometry selection

- Added an immutable topology table for all 64 six-direction masks for `cable_neo.obj` and `pipe_neo.obj`. Each entry is a small array of references to the already-shared Forge `GroupObject` instances; faces, vertices, normals, and UVs are not copied.
- Tables initialize lazily on the first corresponding render. The temporary lists used to assemble them exist only during that one-time initialization; world and inventory render calls perform an array lookup and allocate nothing.
- Cable masks preserve the existing optimized `CX`, `CY`, and `CZ` groups for exact two-ended straight runs. Every other mask preserves the previous core/arm selection, including the established positive/negative Z group mapping.
- Pipe masks preserve the previous isolated shape, single-ended full-axis shapes, connected arms, and eight corner-filler rules. The material icon and fluid-colored overlay now render the same cached group array, keeping topology independent from material and fluid appearance.
- Added an untransformed group submission path to `ObjUtil`. Cached conductor groups now submit their stored positions and normals directly, avoiding name lookup, trigonometry, and identity transform arithmetic while retaining the existing lighting, triangle-to-quad duplication, icon UV remapping, and optional fluid tint.
- Classic cables now derive their direction booleans from the same allocation-free six-bit mask helper. Their emitted primitive geometry and UV layout are otherwise unchanged.
- Box ducts now publish their already-computed mask and metadata only for the duration of their block render. Repeated icon requests reuse that state, reducing connectivity work to the initial six tests per chunk build. Direct icon queries outside this renderer keep the uncached live behavior.

### Why connection masks are not stored in TileEntities

The six neighbor tests occur during chunk rebuilds, not every frame. Persisting a second connection mask in cable/pipe TileEntities would duplicate topology-derived state and would require complete invalidation for adjacent block replacement, connector orientation/mode changes, cross-mod connector behavior, chunk borders, and fluid-type changes. A stale cached mask would be a visual and collision correctness regression.

The implemented cache therefore stores immutable geometry selection for each possible mask while computing the current mask once from live connector contracts when the chunk actually rebuilds. This keeps rendering separate from network ownership and changes no network semantics.

### Materials, fluid visuals, paint, and special conductors

- Round duct material remains selected from block metadata. Fluid type remains read from `TileEntityPipeBaseNT`; its color applies only to the existing overlay pass. Texture overrides continue to replace the base material icon without replacing the fluid overlay.
- Box-duct metadata still controls material, diameter, UV family, and whether the fluid color multiplier is used. Only redundant mask/metadata lookups inside the same render were removed.
- Paintable cables and ducts use the generic two-pass full-block renderer, not the ordinary conductor OBJ geometry. Their camouflage texture and overlay/fluid color continue to come from their TileEntities. They create no model geometry but still perform repeated TileEntity/icon lookups across their two passes.
- Cable switches, fluid valves, and fluid switches remain metadata-textured normal blocks. Diodes, gauges, detector cables, detonation cord, exhaust ducts, pylons, and other special conductors retain their specialized rendering and connection rules.
- No conductor TileEntity was converted into or out of a TESR. Ordinary conductor geometry was already in the correct chunk/block rendering path.

### After architecture and remaining costs

- Normal displayed frames allocate nothing and perform no conductor connectivity or OBJ traversal; Minecraft reuses compiled chunk geometry.
- Ordinary round conductor chunk builds perform six live neighbor tests, one 64-entry topology lookup, then allocation-free traversal of preselected shared groups. Material, override texture, brightness, and fluid tint remain render-time inputs.
- Classic cable builds perform six live neighbor tests and direct scalar tessellation. Box ducts perform six live neighbor tests and render one to seven primitive cuboids without repeating topology work for each face.
- The unavoidable remaining cost is tessellator submission during a dirty chunk rebuild. Round ducts intentionally submit the selected geometry twice for material and translucent/color overlay appearance. UVs must still be remapped through the active texture-atlas icon, and brightness/color remain position- and fluid-dependent, so a single global compiled display list would not preserve current behavior.
- Specialized diode and detonation-cord renderers still resolve individual `cable_neo` group names and apply the generic zero-angle OBJ path during chunk builds. They are lower-volume special cases and were not folded into the ordinary conductor cache without profiler evidence.
- If chunk rebuild self-time remains high, the next measurement should separate tessellator submission from neighbor connector calls. A packed immutable local-vertex representation keyed by mask could reduce Forge face-object traversal, but should be considered only if rebuild CPU time remains material; it would not reduce steady-frame allocation.
- The cached group tables reference Forge model objects for their full lifetime. If Forge OBJ resource-reload support is added later, these tables must be rebuilt or point through a stable reloadable handle.

### Validation status

- All 64 pipe-mask selections were compared against the previous branch rules, including arm order and corner fillers; no selection differences were found. Cable straight-run and arm rules were copied directly into the equivalent mask table.
- Targeted offline `compileJava` completed successfully after the conductor changes.
- Minecraft was not launched. In-game validation is still required for all 64 visual masks, chunk-border changes, connector orientation changes, resource-pack overrides, three round-duct materials, all fluids/colors, all box-duct sizes/materials, paintable conductors, switches/valves, and special conductor blocks.

## 2026-09-23 20:16 — Machine TESR and dynamic-geometry audit

### Ownership and current architecture

- Audited machine TESRs do not store parsed OBJ/HMF models in their TileEntities. World, inventory, and item renderers reference the same class-loader-lifetime `ResourceManager` objects; moving groups are selected by name from those shared objects and transformed with the OpenGL matrix stack.
- Renderer registration can create more than one renderer object for related TileEntity classes (for example the centrifuge and gas centrifuge), but those renderer instances do not own model geometry. The duplicate `TileEntityMachinePress` renderer registration likewise constructs an empty renderer shell, not another parsed model.
- HFR VBO models compile one buffer set per named group. Static and animated sections therefore share one parse and one immutable uploaded representation; animation changes only the matrix or current GL color/texture before drawing a group. No vertex structures are cloned for a moving part.
- Legacy Forge OBJ, raw HFR, and HMF models are also shared and do not allocate `Vertex[]` while drawing, but they traverse their face graphs and submit every vertex through `Tessellator` on every TESR frame. This is render CPU work and retained object-graph cost rather than the profiled parser-array lifecycle.
- Fully static machine bodies were not moved into chunk rendering in this pass. Splitting a multiblock TESR between chunk and dynamic renderers would add orientation, bounds, lighting, invalidation, and duplicate-registration risk. The confirmed allocation problems can be removed while retaining the current visual/lifecycle contract, and VBO group draws already cache the expensive geometry.

### Highest-cost confirmed patterns

| Renderer / shared model | Static sections | Genuinely dynamic sections | Confirmed pre-pass cost and result |
| --- | --- | --- | --- |
| `RenderCryoDistill` / `cryo_distill` | Entire 4,418-face model | None | Raw HFR replayed the whole model each frame; now one shared VBO-backed model |
| `RenderChemfac` / `chemfac` | `Main` (3,074 faces) | `Fan1`, `Fan2` (46 faces each) | All 3,166 faces were CPU-submitted each frame; named groups are now shared VBO draws with fan matrices only |
| `RenderAssemfac` / `assemfac` | `Factory` (1,542 faces) | Six each of pivot, arm, piston, and striker groups (1,248 faces total) | 25 raw-HFR group traversals per frame are now VBO group draws; arm interpolation and transforms are unchanged |
| `RenderElectrolyser` / `electrolyser` | Entire 2,524-face model | None | Whole-model raw-HFR replay replaced by one shared VBO representation |
| `RenderRadGen` / `radgen` | `Base` (1,532 faces) | `Rotor` (768), emissive `Light` (64), translucent `Glass` (104, intentionally drawn twice) | Geometry is now compiled per group; rotation, lightmap, blend, color, and the two glass passes remain dynamic |
| `RenderIGenerator` / `igen` | `Body` (1,128 faces) | `Rotor` (486) | Shared VBO groups replace raw face traversal; only rotor interpolation remains dynamic |
| `RenderMixer` / `mixer` | `Main` (434 faces) | mixer blade (4) and scaled/colorized fluid (22) | Shared VBO groups preserve current color and fill-height state; the per-frame `Color` object was removed |
| `RenderChemplant` | body (406 faces) | two spinners, piston, variable fluid layers/caps and UV modulation | Body moved from raw HFR to VBO. Animated Forge/HMF pieces remain dynamic because the HMF fluid path uses `HmfController` UV modulation |
| `RenderStrandCaster` / `strand_caster` | caster (384 faces) | clipped plate (10) and scalar fluid quad | Both groups now use the shared VBO; clip plane and fill surface remain dynamic, and clip-plane array creation was removed |
| `RenderRefueler` / `refueler` | fueler (466 faces) | clipped/colorized fluid (22) | Both groups now use the shared VBO; clip/color state remains dynamic without temporary `Color` or array objects |
| `RenderPumpjack` / `pumpjack` | `Base` (846 faces) | rotor, head, carriage, rods and changing rope curve | Model groups were already VBO-backed. Six per-detail-frame `Vec3` objects used only as rotation scratch were replaced with equivalent scalar rotation math; the changing rope remains direct tessellation |

The ten HFR models promoted in this pass contain 18,728 triangle faces. They use the same HFR parse, group names, normals, UVs, textures, transforms, color, blend, and clip state as before; only their draw representation changed from repeated face traversal to RTM's existing reloadable VBO wrapper. Every promoted resource was checked to contain triangles only, which is required by that wrapper.

### Confirmed per-frame allocation sources and fixes

- `RenderAssembler`, `RenderPress`, `RenderEPress`, and `RenderPistonInserter` constructed a new `RenderDecoItem` during detail rendering. These and the ore slopper, arc welder, and soldering station also constructed a new dummy `EntityItem` for each visible displayed item. `RenderDecoItem` now owns one lazily created dummy entity, and each renderer owns one reusable `RenderDecoItem`; the current stack, size, and fixed hover state are refreshed before drawing.
- `RenderFoundry` constructed a new `RenderItem` for every non-block mold/output draw. It now keeps one renderer instance. Foundry molten color, mixer fluid color, and refueler fluid color now use packed RGB components directly instead of allocating `java.awt.Color` every frame.
- `RenderFluidTank` created a texture-path string and `ResourceLocation` for every world or inventory draw. The two special textures are constants and ordinary fluid texture identifiers are cached once per `FluidType`. This cache stores identifiers only, so resource reload still replaces texture contents through Minecraft's texture manager.
- `RenderRefueler` and `RenderStrandCaster` populated their reusable clip-plane buffers through newly allocated `double[]` values. They now write the four scalar coefficients directly.
- `RenderPumpjack` created two general rotation vectors and two more per rope side. The renderer now computes the same `Vec3.rotateAroundX` equations with scalar scratch values and emits the genuinely changing rope geometry directly.
- None of these paths created OBJ `Vertex[]`; they are independent steady-frame garbage that became visible after separating parser-time allocations from render-time allocations.

### Remaining expensive or allocation-bearing paths

- `RenderAssembler` remains the most obvious common legacy OBJ candidate: its shared Forge models replay approximately 1,920 faces per detailed frame because the 400-face cog is rendered four times, alongside the body, slider, and arm. Press/EPress, Stirling (932 faces), sawmill (776), solar boiler (572), microwave (569), autosaw (488), milk reformer (498), and several smaller machines also remain CPU-tessellated shared models. Converting Forge models changes parser/normal semantics, so these should be ranked by GPU/CPU render self-time and visually checked before migration.
- Chemplant fluid columns intentionally render a variable number of HMF layers and change `HmfController` UV modulation while processing. A static VBO conversion would freeze that effect unless the prepared format explicitly supports the modulation.
- Visible item machines still create or copy `ItemStack` data per frame (`toStack()`, `copy()`, or a fixed display stack) even though their renderer and entity shells are now reused. Safely removing that cost needs invalidation keyed to recipe/display-stack item, metadata, and NBT changes; retaining stale per-TileEntity copies would be incorrect.
- Foundry molten/output quads, mixer/refueler/strand-caster fluids, solar beams, pumpjack ropes, and similar surfaces are genuinely state-dependent scalar geometry. Their renderers should remain dynamic, but allocation profiles can still identify avoidable helper objects around the tessellation.
- Beam-heavy machines and special effects still pass newly created `Vec3` endpoints into `BeamPronter`; launch-pad renderers still create topology/name arrays or lookup keys. Those are separate lower-volume paths and were not changed without a steady-state allocation call tree proving priority.
- VBO promotion improves repeated drawing but increases the resource-reload subset. The 151 current VBO wrappers now reparse 229,268 faces and recreate about 458,472 parser `Vertex[]` arrays on a full reload. A compact prepared representation remains justified only if startup/reload pause or retained HFR face graphs measure as material.

### Recommended next measurements

1. Profile a dense factory after client initialization, separating allocation rate from TESR CPU/GPU self-time. The promoted models should disappear from tessellator face submission, while displayed-stack copies and genuinely dynamic surfaces remain visible.
2. Visually compare the promoted static and animated groups under normal lighting, colored/translucent fluid states, refueler/strand-caster clipping, and a resource reload. No in-game renderer validation was performed in this pass.
3. Rank the remaining Forge/raw-HFR machine models by rendered instance count multiplied by face submissions. Start with assembler, Stirling, sawmill, and autosaw rather than converting every legacy model wholesale.
4. If displayed-item stack copies remain material, add renderer-level stack snapshots invalidated by exact item, damage, and NBT changes; do not attach parsed models or renderer caches to TileEntities.

### Validation status

- Source inspection covered shared ownership, renderer registration, inventory/world consumers, named group boundaries, face/group counts, per-render constructors, temporary arrays/vectors/colors/texture identifiers, clipping, and resource-reload behavior for the prioritized machine classes.
- All ten promoted HFR resources were verified to be triangle-only. Targeted offline `compileJava` completed successfully after the model and allocation changes.
- Minecraft was not launched. Visual parity, GL-state interaction, animation, clipping, fluid color/fill, item display, resource reload, and measured frame/allocation improvement still require in-game validation.
