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
