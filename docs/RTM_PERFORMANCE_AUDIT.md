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

## 2026-09-25 09:43 — Phase 0 machine runtime infrastructure

### Previous execution architecture

Machine behavior remains primarily owned by loaded TileEntities and their `updateEntity()` methods. Inventory, energy, fluid, recipe, environment, connection, progress, and packet work are commonly interleaved in the same per-tick method. `TileEntityMachineBase` supplies inventory and network-sync helpers, `TileEntityTickingBase` supplies the common packet surface, and `TileEntityLoadedBase` supplies shared loaded-state plus power/fluid endpoint cleanup. None of those bases previously represented a logical machine after its TileEntity unloaded, and timers were generally TileEntity fields advanced once per tick.

Phase 0 does not change that behavior. `LEGACY` is the default execution strategy, legacy `updateEntity()` methods still run, and no existing machine has been opted into runtime-driven simulation.

### World ownership and logical machine graph

`MachineRuntimeManager` owns exactly one server-side `MachineRuntime` for each loaded `World`. World load creates it, the `WorldTickEvent` end phase drives it, and world unload removes it and clears every binding and queue. The manager's world map is a lifecycle-bound lookup, not persistent ownership: world unload is the mandatory cleanup boundary. Client worlds never acquire a runtime and no worker thread or asynchronous simulation is used.

Only TileEntities whose `getMachineExecutionStrategies()` returns a non-`LEGACY` capability set are registered. A logical entry contains its key, controller position, compatibility type, execution capabilities, dirty causes, typed scheduled-transition ownership, and an optional loaded binding. It contains no rendering state or migrated gameplay fields. Multiblock proxies remain unregistered by default; future migrations should opt in the controller and use the existing `TileEntityProxyBase.getTE()` / `BlockDummyable` / `IProxyController` resolution paths to delegate mutations to it.

### Identity, persistence, and replacement safety

A `MachineKey` is dimension + block position + a positive lifecycle generation. Coordinates locate an entry but do not identify its lifetime. Each opted-in controller stores `hbmMachineGeneration` in its existing TileEntity NBT. `MachineRuntimeSavedData` stores only the next generation counter in version-1 per-world saved data (`hbm_machine_runtime`). Old saves have neither value and allocate them lazily when a machine first opts in.

Binding a TileEntity with no generation allocates a new one. Reloading the same TileEntity NBT reuses its generation and rebinds the existing unloaded entry when it is still present. A different generation or compatibility type at an occupied position removes the old entry and cancels all work it owns before the replacement binds. Scheduled entries retain the full key and type, so work cannot resolve against a later occupant at the same coordinates.

The graph itself, loaded references, dirty buffers, polling buckets, scheduler heap, derived caches, and diagnostic counters are deliberately transient. The TileEntity generation preserves lifetime identity across saves. Scheduled operations are not serialized in Phase 0: a migrated machine must persist authoritative operation start/duration/due fields in its own NBT and reconstruct its typed schedule when it binds. This avoids two competing persistent copies of operation state.

### Binding and destruction lifecycle

`TileEntityLoadedBase.validate()` binds an opted-in controller. `onChunkUnload()` unbinds it but retains the logical entry, dirty causes, and scheduled ownership. `invalidate()` removes the logical machine, cancels its transitions, and clears the binding. World unload clears everything without retaining TileEntity references.

The electric furnace switches between on/off block instances while deliberately preserving the same TileEntity. That path now brackets the block swap as a retained transition: its incidental `invalidate()` unbinds rather than destroys, and the same instance rebinds after `validate()`. A real block break still uses normal destruction and a newly placed furnace receives a new generation.

The lifecycle inspection also found `TileEntityMachineAssembler`, `TileEntityHeatBoiler`, `TileEntityHeatBoilerIndustrial`, and `TileEntityPWRController` overriding `onChunkUnload()` only to stop audio without calling `super`. They now call the shared lifecycle first. Other classes that do not derive from `TileEntityLoadedBase`, and any optional/external subclass that overrides `validate()`, `invalidate()`, or `onChunkUnload()` without chaining to `super`, remain outside these guarantees until individually migrated.

### Dirty reevaluation

`MachineDirtyCause` defines infrastructure causes for inventory, fluid, energy, configuration, redstone, topology, recipe, environment, and lifecycle changes. Causes are bitwise-coalesced per logical machine. `MachineRuntime` uses two reusable deques: at pass start the public queue is swapped into execution storage, leaving an empty public queue. The entry is marked not queued and its accumulated causes are cleared immediately before its callback. A callback that dirties itself therefore enters the public queue for the next world-tick pass; it cannot recurse in the current pass.

Dirty state survives a chunk unload and is queued when the controller rebinds. Unloaded machines are never evaluated. The standard `TileEntityMachineBase` inventory paths now issue inventory + recipe invalidation in addition to their separate network-sync and PowerNet behavior. Direct `slots[]` writes still bypass this hook and require per-machine coverage before migration. Common configuration, topology, redstone, energy, and fluid hook methods are prepared, but the shared `FluidTank` is intentionally not given an owner callback in this pass because it is widely constructed and directly mutated without reliable ownership information.

Machine simulation dirtiness remains independent of `markNetworkDirty()`. PowerNet remains an independent authority and scheduler; the electric furnace's `setPower()` now exposes the future machine-energy invalidation seam while retaining its existing PowerNet invalidation. Since the furnace is still `LEGACY`, this has no runtime scheduling effect today.

### Scheduled transitions

The scheduler accepts primitive `taskType` and `taskSlot` identifiers plus an absolute world due tick. Ownership is `(MachineKey, taskType, taskSlot)`: scheduling the same pair replaces the prior transition, while different slots allow independent assembler or chemplant lanes. Cancellation, replacement, machine removal, and world unload invalidate ownership without arbitrary lambdas or captured TileEntity references.

When due, a transition resolves the logical entry by full generation-bearing key, verifies its compatibility type and active ownership record, then calls the currently loaded controller. If the controller is unloaded at the boundary, the transition remains owned but dormant and is requeued on a later bind. Phase 0 does not simulate unloaded machines. Removed, replaced, cancelled, mismatched, or stale work cannot call a replacement TileEntity.

### Coarse polling and execution classification

Execution capabilities are composable: `EVENT_DRIVEN`, `SCHEDULED`, `COARSE_5`, `COARSE_20`, `COARSE_100`, and `REALTIME`. `LEGACY` is zero and remains the default. A future machine can combine event-driven eligibility, scheduled completion, and one coarse compatibility cadence without being forced into a single category.

Coarse polling uses three world-owned cadence buckets rather than one timer per TileEntity. Each logical key is deterministically spread across the cadence's slots, preventing all machines from waking on the same tick. Only a valid loaded binding is called. Coarse polling is reserved for environment or compatibility checks that lack mutation events; ordinary recipe and inventory logic belongs in dirty reevaluation.

`REALTIME` is an explicit classification only. It does not suppress `updateEntity()` and therefore cannot accidentally turn off existing simulation.

### Server-thread ordering

The existing order is preserved conservatively:

1. At server-tick start, `Nodespace.updateNodespace()` performs bounded conductor topology reconciliation and dirty PowerNet/fluid work.
2. Vanilla/Forge world ticking invokes legacy and realtime TileEntity `updateEntity()` methods. Existing machines also perform their current packet synchronization there.
3. At `WorldTickEvent` end, after the existing HBM end-phase world work, `MachineRuntime` processes due scheduled transitions, then the swapped dirty queue, then the 5/20/100-tick coarse bucket for that world tick.

Scheduled callbacks may dirty a machine and have that dirtiness handled in the following dirty pass. Dirtiness raised while the dirty pass itself is running is deferred to the next world tick. Coarse callbacks run last, so their dirtiness is also deferred. This ordering is explicit rather than an incidental server-tick side effect. No currently shipped machine behavior is reordered because all machines remain `LEGACY`.

### Diagnostics

`1.46_enableMachineRuntimeDiagnostics` is disabled by default. When disabled, event methods perform only the configuration branch and allocate no report strings, timers, or diagnostic event objects. `/ntmmachinestats` reports the sender's server world and includes logical/loaded/unloaded entries, opt-in strategy counts, dirty signals/processing/depth, scheduled active/created/executed/cancelled/stale counts, lifecycle counts, and coarse poll executions. Legacy machine counts are not collected because doing so would require registering or scanning every unmigrated TileEntity.

### Migration probes and remaining hazards

The electric furnace is prepared for Phase 1 through common inventory invalidation, an explicit energy invalidation seam, persistent logical identity support, typed scheduling APIs, and retained identity across its on/off block swap. It has not been opted in and its progress, cooldown, charging, pollution, connection polling, block-state changes, power use, and network synchronization are unchanged.

The next electric-furnace pass should persist authoritative operation start/duration/due fields; override its strategy with event-driven + scheduled and only the coarse cadence genuinely needed for compatibility; reconstruct schedules from NBT on bind; reevaluate eligibility on input/output/upgrade and changed energy availability; schedule completion/cooldown boundaries; preserve pollution and block-state timing deliberately; and remove its per-tick progress advancement only after equivalent server behavior is verified.

Assembler and chemplant can use `taskSlot` for independent lane transitions, but their direct slot and tank writes must be audited before opting in. Their cached recipe/lane data stays TileEntity-owned for now. The shared legacy `FluidTank` lacks a universal mutation callback, many machines write `slots[]` directly, redstone and neighbor changes are not centralized, external subclasses can bypass base lifecycle methods, and large multiblocks have family-specific proxy/controller rules. Those are known migration hazards, not reasons to broaden Phase 0 into a mass conversion.

No machine gameplay loop, rendering state, PowerNet ownership, fluid-network ownership, external API, or legacy fallback was migrated or removed in this phase.

Source inspection covered the shared TileEntity bases, world lifecycle and tick handlers, saved data, block-state replacement, proxy/controller multiblocks, inventory and fluid mutation surfaces, MK2 endpoint lifecycle, diagnostics, and lifecycle overrides. Targeted offline `compileJava` completed successfully. Minecraft was not launched; dedicated-server world/chunk lifecycle, save/reload generation continuity, rapid replacement, scheduler cancellation/rebind, multiblock delegation, and diagnostics still require runtime validation.

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

## 2026-09-24 22:42 — Shared prepared-model architecture

### Replaced base pipeline

The active HBM model registry no longer retains Forge `WavefrontObject`, HFR `S_Face`/`S_GroupObject`, or HMF `HbmFace`/`HbmGroupObject` graphs. The old HFR and HMF classes remain only as source-compatible facades for external callers; their constructors resolve a prepared handle and do not construct the former face graph. All 485 in-tree OBJ/HFR/HMF load sites now converge on the prepared cache. The cache currently resolves 479 unique bundled geometry keys after resource, format, and smoothing options are applied.

The new path is:

`ResourceLocation + format/smoothing options -> one parse -> immutable packed vertices and range metadata -> stable handle -> optional one-VBO upload -> all renderer consumers`

- `PreparedObjParser` writes de-indexed position, atlas-independent UV, and selected normal data into one flat primitive array. It retains group/material names plus first-vertex, vertex-count, and GL draw-mode arrays. Parser index tables and numeric tokens are discarded; face parsing uses reusable four-corner scratch rather than per-face token/index arrays.
- `PreparedModelCache` keys geometry by resource, format semantics (Forge OBJ, HFR OBJ, or HMF), and smoothing where applicable. GPU preference promotes the existing handle and is not a second parse key. Duplicate inventory, TESR, entity, item, and block consumers therefore share one CPU representation.
- `PreparedModelHandle` is the renderer-facing stable reference. A resource reload parses replacement data, prepares its optional VBO, atomically swaps the handle state, then deletes the previous VBO. The cache map is not cleared, so long-lived renderer references cannot become stale.
- GPU-compatible triangle models use one interleaved VBO per model and group ranges use `glDrawArrays` offsets. The former HFR backend used three buffers per group and retained the parser graph behind them. Named group transforms, clipping, current texture, color, blend, lighting, and lightmap state remain external to geometry.
- CPU `renderAll` batches adjacent compatible ranges and named-part rendering does not allocate a temporary name array. Icon-remapped rendering reads the same packed data into the caller's existing block/inventory tessellation batch.

### Format and compatibility policies

- Forge OBJ semantics use flat calculated face normals and retain Forge's per-face `0.0005` atlas-bleed UV inset for normal draws, while icon-remapped paths continue to use the raw model UVs. HFR retains its existing explicit smoothing option and exact UV submission, using vertex normals only when smoothing is enabled. The cache treats Forge OBJ and HFR OBJ as distinct format options even when their resource and smoothing values match. OBJ V coordinates retain the established `1 - v` convention, negative indices are supported, and both triangles and quads remain represented with their original draw mode.
- HMF uses the compact CPU representation and preserves the per-face `0.0005` UV inset plus `HmfController` time/modulation offset. It is intentionally never uploaded as static UV geometry.
- Atlas/icon-remapped block and inventory renderers remain CPU submitted because their UVs, brightness, override icon, and optional tint are render-context inputs. The HEV battery was the initial block/inventory probe; the same prepared overload now covers every former `ObjUtil` Forge-model call site. Cable and duct 64-mask caches retain name selections rather than stale face-object references, so reload swaps remain visible.
- The solar mirror's pivoted panel transform has an allocation-free packed-geometry path and no longer requires direct `Face`/`Vertex` access or temporary `Vec3` objects.
- Angelica is an explicit optional backend boundary. Eligible triangle geometry keeps the same packed prepared data and one-time VBO lifecycle, but buffer creation/binding/upload/deletion, fixed-function client-array setup, and draw submission are routed through Angelica's `GLStateManager`. Non-Angelica clients retain the direct LWJGL backend. If Angelica is present but the required public GLSM surface cannot be resolved, prepared models deterministically retain their compact CPU path instead of attempting an untracked VBO draw.
- The four initial compatibility probes were the static chemplant body, animated assembly-factory groups, legacy Forge iron-furnace TESR, and icon-remapped HEV battery. The completed migration then covered the rest of `ResourceManager`, direct renderer-owned HFR/Forge models, and all four HMF resources. No model geometry is TileEntity-owned.

### Angelica GLSM compatibility boundary

Angelica 2.1.29 was inspected as the primary target and the current upstream implementation was inspected as the secondary target. In both versions, Angelica's redirector rewrites ordinary loaded mod bytecode calls such as `GL11.glPushMatrix`, translate/rotate calls, buffer calls, client-array calls, and `glDrawArrays` to `GLStateManager`. Consequently, the transforms already applied by RTM/HBM renderers remain in Angelica's software model-view stack; the prepared renderer must submit its draw through GLSM so Angelica can synchronize that current matrix and fixed-function state before issuing the backend draw. Baking live TESR transforms into uploaded vertices would be both slower and semantically wrong.

The stable public surface shared by 2.1.29 and current Angelica is sufficient for the prepared interleaved layout: `glGenBuffers`, `glBindBuffer`, `glBufferData(FloatBuffer)`, `glDeleteBuffers`, offset overloads of `glVertexPointer`, `glTexCoordPointer`, and `glNormalPointer`, client-array enable/disable, and `glDrawArrays`. In 2.1.29, the legacy pointer methods map conventional arrays to generated fixed-function shader attributes and `glDrawArrays` performs shader/matrix preparation plus quad conversion. Current Angelica retains those calls while adding stricter VAO/client-array tracking, draw locking, extended fixed-function attributes, buffer-respec invalidation, and broader deleted-buffer cleanup. Using only the common tracked entry points avoids relying on either version's private backend, shader, VAO, or matrix implementation.

`PreparedModelGpuBackend` resolves the GLSM class without initialization and caches exact method handles once when Angelica is detected, preserving a soft dependency and avoiding reflective lookup in render loops. Each immutable model state retains the backend that created its buffer, so reload replacement deletes the old buffer through the same state tracker after the new state is installed. Render selection, range offsets, group names, parser/cache ownership, and upload frequency are unchanged. Dynamically transformed named groups now use the prepared Angelica VBO path rather than the former global CPU downgrade; whole-model triangle draws still collapse to one submission. HMF animation and icon/atlas-remapped rendering remain CPU submitted because their UVs or batch context are genuinely draw-dependent, and the 14 bundled quad faces retain the existing CPU path rather than introducing a separate Angelica-specific representation.

The disabled-by-default prepared-model metrics now identify the selected backend (`LWJGL VBO`, `Angelica GLSM VBO`, or the Angelica capability-fallback reason) alongside reload, cache, retained CPU, uploaded GPU, and cumulative upload figures. No Angelica class is referenced by type, so clients without Angelica keep the original class-loading behavior.

Primary compatibility references: [Angelica 2.1.29 `GLStateManager`](https://github.com/GTNewHorizons/Angelica/blob/2.1.29/glsm/src/main/java/com/gtnewhorizons/angelica/glsm/GLStateManager.java), [2.1.29 GL redirect table](https://github.com/GTNewHorizons/Angelica/blob/2.1.29/glsm/src/main/java/com/gtnewhorizons/angelica/glsm/redirect/GLSMRedirector.java), [current `GLStateManager`](https://github.com/GTNewHorizons/Angelica/blob/master/glsm/src/main/java/com/gtnewhorizons/angelica/glsm/GLStateManager.java), and [the dynamic-group VBO transform report](https://github.com/GTNewHorizons/Angelica/issues/1935).

### Static size and CPU-impact accounting

Static inspection of the 479 resolved bundled keys found 385,859 faces: 385,845 triangles and 14 quads, with no faces outside the supported triangle/quad contract. Their exact packed primitive payload is 37,042,912 bytes for 1,157,591 draw vertices, excluding the small range/name arrays and object headers. Approximately 36,928,224 bytes are GPU-eligible with either the standard backend or a compatible Angelica GLSM backend; HMF accounts for 27,648 CPU bytes and remains dynamic.

The previous graph required 771,718 retained `Vertex[]` arrays for those faces, plus roughly one texture-coordinate array and one face object per face, group/list backing storage, source vertex/normal/UV objects, and—in the old VBO subset—separate uploaded position, three-component UV, and normal buffers. The face-local arrays alone are estimated at about 37.0 MB with compressed references; face objects and group list references add at least another approximately 13.8 MB before counting source attribute objects or collection overhead. The new 37.0 MB primitive payload therefore removes at least roughly 13.8 MB of retained CPU heap from the structural minimum and substantially more when source objects and collection overhead are included. This is a static estimate, not a heap-dump measurement.

The new eligible-model GPU ceiling is higher than the old approximately 24.8 MB HFR-only VBO subset because eligible legacy Forge and raw-HFR models are now uploaded too on both the standard and Angelica backends. In return, those TESR/entity/item paths stop replaying their face graphs through `Tessellator` every draw. The prepared GPU layout uses eight floats per vertex rather than the old HFR VBO's nine and one buffer per model rather than three buffers per group.

### Diagnostics and remaining paths

- `DEBUG_PREPARED_MODEL_METRICS` in `hbmClient.json` is disabled by default. When enabled it reports per-resource parse counts/times, cache hits/misses, prepared count, packed CPU bytes, uploaded GPU bytes, selected/fallback backend, total reload time, and upload time. Normal logging remains quiet.
- Legacy face/group classes and the Forge display-list constructor remain in source for compatibility but have no active HBM load path. External mods that instantiate Forge `WavefrontObject` directly are outside this cache; external `.hmf` loads through HBM's registered loader return prepared handles.
- The referenced `models/TheGadget3.obj` remains absent from bundled resources, as it was before this rewrite. Its renderer can only initialize if another resource source supplies it; this change does not invent a replacement asset.
- Static validation confirmed that every bundled referenced face is a triangle or quad, all active in-tree loader calls use prepared handles, and the Angelica integration resolves only the public GLSM surface shared by 2.1.29 and current upstream. Targeted offline `compileJava` completed successfully after the GLSM backend change. Minecraft was not launched. Resource-pack reload, visual parity, live Angelica matrix/state behavior, HMF UV animation, atlas overrides, translucent/color/clipped passes, and representative inventory/TESR/entity scenes still require in-game validation.

### 2026-09-25 runtime consumer migration audit

The first runtime launch after the prepared rewrite exposed an internal consumer that had compiled through the common `IModelCustom` interface but still cast its model back to Forge `WavefrontObject`. `RenderBlockDecoModel` performed that cast in both inventory and world rendering, so the authoritative `PreparedModelHandle` for `models/blocks/puter.obj` failed before `ObjUtil` could select its packed icon-remap path. This was a consumer migration hole, not a parser, cache, GPU, or Angelica failure.

The targeted sweep found one additional active renderer with the same latent failure: `RenderBlockRotated`, used by the dynamite, C4, and CSGO charge blocks. Both renderers now accept `PreparedModelHandle` directly, and the three corresponding `ResourceManager` fields expose that concrete prepared type. Their existing icon lookup and override-texture selection, inventory transforms, world translation, metadata rotations, brightness, normal-based shading, quad tessellation batch, and return values are unchanged; only the invalid legacy cast was removed. The packed icon-remap method continues to duplicate a triangle's final corner for the caller's quad batch exactly as the former Forge-object helper did.

The broader concrete-type audit separated the remaining references as follows:

- Active internal block, inventory, entity, TESR, and model renderers contain no casts to `WavefrontObject`, `HFRWavefrontObject`, `HFRWavefrontObjectVBO`, or `HbmModelObject`, and no accesses to their face/group graphs. All internal model loads still resolve through `PreparedModelCache`; no `AdvancedModelLoader.loadModel` or legacy parser construction was reintroduced.
- `ObjUtil` retains its public Forge `WavefrontObject` overloads for callers that genuinely own a Forge model. Its broad `IModelCustom` compatibility entry points now explicitly dispatch prepared handles and accept a legacy object only after a guarded `instanceof WavefrontObject` check, rather than assuming every non-prepared implementation is Forge OBJ.
- `HFRWavefrontObject`, `HFRWavefrontObjectVBO`, and `HbmModelObject` remain source-compatibility facades backed by prepared handles. `WavefrontObjDisplayList` and the legacy `S_*`/`Hbm*Face` graph classes remain isolated compatibility code with no active in-tree construction or consumer. They were not deleted or reactivated.

No model is reparsed, copied into a legacy graph, or attached to a block or TileEntity by this correction. Stable handle replacement and resource-reload visibility are preserved because the affected renderers retain the same prepared handles supplied by `ResourceManager`.

Targeted offline `compileJava` completed successfully after the consumer correction. Minecraft was not launched; the decorative computer and three rotated charge variants still require world, inventory, metadata-orientation, override-texture, and resource-reload confirmation in game.

## 2026-09-25 11:36 — Celestial, worldgen, ticket, and rocket performance pass

### Celestial rendering

`WorldProviderCelestial` now owns the body-sky metric snapshot and visible-sun fraction for a world tick, partial tick, and body. `SkyProviderCelestial` reuses that snapshot for body placement and star visibility. Tidal-lock longitude still evaluates its own observation time, but its angle now walks only the two bodies' parent chains instead of rebuilding the entire solar system. Server moon-phase calculations use that independent small path too.

`WorldProviderOrbit` similarly owns one orbital metric snapshot per tick, partial tick, station state, orbit/target, and transfer progress. Its angle, solar brightness/eclipsing geometry, and `SkyProviderOrbit` body/transfer rendering all reuse those positions. The cache lives on each provider and is replaced when its frame inputs change; no static cache retains a world. RTM's existing sun-power and eclipse rules, orbital-angle convention, partial-tick interpolation, tidal-lock offset, and star-visibility calculation remain in place. These are source-level cost reductions; visual behavior still needs in-game comparison across day/night, eclipse, tidal locking, and station transfers.

### Structure and world generation

- The active bundled Martian NBT base now loads its palette and blocks once and is generated through a registered `MapGenStructure` start/component. Its component stores the template identifier and chosen height, survives save/reload through `MapGenStructureIO`, and writes only the X/Z slice intersecting the current population bounds. The original origin-only Martian world-type rule remains; the old whole-template population call was removed. Structure resources now load from the bundled classpath so this path does not invoke the client resource manager on a dedicated server. Existing worlds created before this migration may need an in-game check near the origin because legacy direct placement had no persisted mapgen start.
- Eve gas and Laythe oil bubbles now run during chunk-primer generation. Their target/replacement blocks, celestial ore metadata, configured one-in-N frequency, Y range, and integer radius range of 10–16 are retained. Their exact seeded positions change because primer-time map generation uses a per-region random stream. The generator computes each source bubble's intersection with the current chunk and never reads or writes neighboring live chunks. Duna's oil bubble is commented out in RTM and remains disabled; there is no active celestial brine-bubble call site in this branch.
- `NTMWorldGenerator` now passes `null` to the `MapGenStructure` primer parameter instead of allocating an unused 65,536-element `Block[]`. The remaining two 65,536-element block arrays are real chunk storage in `ChunkProviderCelestial` and `ChunkProviderOrbit`.
- Flower placement already uses the safe population `+8` convention, while the HbmWorldGen meteor and spaceship population calls are disabled. Existing depth-deposit and OreLayer3D fixes were left intact.

The overworld oil bubble, oil-sand bubble, oil spot, and several legacy schematic/dungeon generators still perform direct world access during population. The overworld uses vanilla's chunk provider, so the celestial primer hook cannot host them; `OilSandBubble` also uses an independent fuzz random stream and `OilSpot` changes surface plants/blocks. Moving those paths safely needs deterministic per-chunk slicing plus their existing biome, loot, and placement semantics. Eve's large electric volcano remains a direct cross-chunk generator as well. This pass did not claim those remaining cascades are eliminated.

### Chunk tickets and rideable rockets

Entity ticket owners now release tickets on permanent death/cleanup and clear local references. Their moving forced-chunk windows still unforce individual chunks without releasing the live ticket. Restored tickets replace redundant constructor-requested tickets; the Forge callback now processes all returned tickets and releases ones with no valid owner. The shared transporter ticket is released when its last saved forced-chunk owner is removed. Repeated cleanup is harmless. Save/reload, entity transfer, and forced-chunk restoration still require dedicated-server validation.

Rideable rockets bypass the inherited whole-bounding-volume water/lava scans. A local block check now recognizes liquid materials for landing/tipping, while lava still ignites the rocket in any state and marks a tipping rocket to explode. The capsule's otherwise unnecessary entity ticket is released once its rocket type is known, including after restored-ticket adoption. In-game checks remain for water, lava, modded liquids, and capsule/missile variants.

### Trait-map review

`CelestialBody.traits` remains a `HashMap`. Per-body default maps are small; `getTrait`/`hasTrait` are key lookups, while live client/server overrides normally come from `SolarSystemWorldSavedData`'s own `HashMap` maps. Clone/copy paths also construct hash maps, and NBT serialization iterates trait registries rather than depending on default-trait insertion order. A `LinkedHashMap` change has no supported performance or ordering benefit here and would add entry overhead.

Targeted offline `compileJava` completed successfully. Minecraft was not launched, and no frame-time, heap, worldgen, or ticket-count measurements were performed.
