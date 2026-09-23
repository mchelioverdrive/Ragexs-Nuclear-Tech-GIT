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
