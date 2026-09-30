# RTM Tile Entity Performance, Correctness, and Architecture Audit

**Audit date:** 2026-09-30
**Mode:** source inspection only; no source or build files changed, and no fixes implemented.

## 1. Executive summary

The active HBM source tree contains **356 concrete TileEntity class declarations and 29 abstract TileEntity bases**. The prior [RTM performance architecture audit](RTM_PERFORMANCE_AUDIT.md) covers the narrower production-machine/runtime migration census (205 machine descendants and five proxy endpoints). This report broadens that to all concrete HBM TileEntity descendants in the active `src/main/java/com/hbm` source tree, including storage, decor, networking, structures, weapons, and helper tiles. The two counts have different boundaries and should not be added together.

The active tree has 254 concrete classes declaring `updateEntity()`, 30 concrete classes inheriting an HBM override, and 72 with no HBM update body. Twenty-eight concrete classes explicitly disable ticking and 328 explicitly or by inheritance allow it. Seventy-eight concrete overrides are empty. These are invocation facts, not estimates of server time.

The strongest source-confirmed findings are:

- Blast door opening/closing/locking propagates recursively through four neighbors per visited door with no visited set or depth bound; state changes stop cycles, but recursion depth follows the compatible connected component.
- Dummy-block teardown can destroy whatever multiblock currently occupies stored target coordinates without checking that it is the owning controller. The old dummy base performs a weaker block-type check, not an ownership check.
- Blast-door redstone condition precedence lets power at the upper input call `tryToggle()` even when the door is locked.
- Blast-door animation thresholds repeat world lookups and dummy-placement/removal attempts on every later animation tick: 499 redundant attempts per complete open-and-close cycle.
- Both moving doors send unchanged state packets every server tick to radius 250. Launch Table and Compact Launcher each send two packets per server tick, also including a radius-250 multipart packet while empty.
- Launch Table performs up to 81 indirect-power checks per server tick; Compact Launcher performs up to 9. Vault Door performs up to 60 checks per unlocked tick before choosing the orientation-specific result.
- The legacy bomb turret copies the full loaded-entity list and runs line-of-sight checks before distance rejection whenever powered AI is active. CIWS also allocates and refreshes its power-connection descriptor every server tick and broadcasts its gauge packet with `sendToAll()` every tick.
- `RequestNetwork.updateEntries()` is called at server-tick start, sets a static timer to 20, and never decrements it. Its full world/chunk/node expiry traversal therefore runs every server tick, not once per second as intended.
- Radio torch configuration, counter state, and drone requester matcher state are serialized and sent every server tick even when unchanged. The counter also allocates and fills an array sized to the adjacent inventory on each server tick.
- Sixty-six active concrete tile classes return `INFINITE_EXTENT_AABB`; 171 override max render distance. This is a source count, not a claim that every broad bound is unjustified. Door and turret bounds are clear candidates for a visual-extent check.

No profiler, packet capture, in-game timing, rendered-frame capture, or save/reload test was available in this audit. The report quantifies deterministic source frequencies only and makes no TPS, millisecond, bandwidth, or percentage-improvement claims.

## 2. Scope and method

The active census scans Java declarations beneath `src/main/java/com/hbm`, follows explicit `extends` chains to Minecraft `TileEntity` or HBM `TileEntityLockableBase`, and excludes abstract classes from the concrete matrix. The table distinguishes class declarations from registered block/tile IDs: a concrete class is not proof that it is registered or normally instantiated. It records full HBM inheritance, the effective update owner, effective `canUpdate()` result, and lexical feature surfaces on the class and its HBM ancestors.

The root build uses the normal `src/main/java` tree. This audit covers the active HBM tree under `src/main/java`; archived snapshots and add-on copies outside that tree are excluded.

| Source tree | Concrete HBM TileEntity descendants | Abstract descendants | Treatment |
|---|---:|---:|---|
| `src/main/java/com/hbm` | 356 | 29 | Active source; detailed findings and primary matrix |
| **In-scope total** | **356** | **29** | Active source declarations; class counts do not imply registration or instantiation |

The matrix feature codes are **source surfaces**, not an assertion that a call runs each tick. For example, `R` means the class or an ancestor contains a redstone API reference; the audit separately identifies event-driven uses and update-path polling. “Hot allocation” is marked only when a source signature appears in update code; it still needs runtime allocation profiling to establish material GC cost. No code was changed.

## 3. Inheritance and shared-base map

The following counts are concrete descendants in the active source tree. “Direct” means the concrete class directly names that superclass. “Transitive” counts all concrete descendants below it, excluding the base itself.

| Base class | Direct concrete descendants | Transitive concrete descendants | Audit significance |
|---|---:|---:|---|
| `TileEntityMachineBase` | 102 | 147 | Inventory, packet, runtime integration, and the abstract update contract; broadest machine owner |
| `TileEntityLoadedBase` | 53 | 242 | Lifecycle and machine-generation boundary; includes much of production machinery |
| `TileEntityMachinePolluting` | 9 | 11 | Pollution/environment behavior shared across a small family |
| `TileEntityTurretBaseNT` | 9 | 13 | Connection refresh, targeting, packet serialization, and turret behavior |
| `TileEntityCrateBase` | 8 | 8 | Shared storage inventory and access behavior |
| `TileEntityRBMKBase` | 6 | 14 | Reactor-column family and inherited simulation state |
| `TileEntityRBMKSlottedBase` | 5 | 8 | Shared slotted reactor members |
| `TileEntityCraneBase` | 5 | 5 | Shared crane control/interaction paths |
| `TileEntityPylonBase` | 5 | 5 | Pylon model/network behavior; ticker is inherited from cable base |
| `TileEntityCableBaseNT` | 3 | 8 | Deferred power-node reconciliation; `canUpdate()` is false |
| `TileEntityProxyBase` | 4 | 4 | Proxy endpoints; `canUpdate()` is false |
| `TileEntityCondenser` | 4 | 4 | Condenser/radiator family; detailed current runtime behavior is in the prior audit |
| `TileEntityPipeBaseNT` | 3 | 3 | Pipe compatibility endpoint; `canUpdate()` is false |
| `TileEntityInventoryBase` | 1 | 1 | Inventory base with ticking suppressed |

The machine audit’s 205-class census is a production behavior grouping and does not equal the 147 descendants of `TileEntityMachineBase`: it also classifies endpoints and support types by behavior, while this table follows Java inheritance only.

## 4. updateEntity census and idle ticking

| Effective tick path | Concrete classes | Meaning |
|---|---:|---|
| Declares a concrete HBM `updateEntity()` | 254 | The class body owns an override; many bodies branch on active state or side |
| Inherits an HBM `updateEntity()` | 30 | The implementation owner is shown in the class matrix |
| No HBM update body | 72 | Inherits Minecraft’s default no-op unless a framework hook applies |
| **Total concrete declarations** | **356** |  |

Seventy-eight of the 254 concrete overrides have an empty body, and nine additional classes inherit an empty HBM override. Another 72 have no HBM update body. Together, 159 concrete declarations have no substantive HBM update body; 143 of those allow ordinary tile callback dispatch, while 16 of the default-body group suppress ticking. An empty/default callback is overhead, not a measured hotspot.

The following 13 class-level update paths perform recurring work that remains present while their main operation is idle or unchanged. A subclass may inherit or call its parent’s path; class counts below name source owners and concrete implementations explicitly inspected, not world-instance counts:

| Class/path | Idle recurring work | Frequency and guard |
|---|---|---|
| `TileEntityBlastDoor` | Redstone read, animation-state packet | Server update; packet is unconditional, radius 250 |
| `TileEntityVaultDoor` | Redstone grid scan while unlocked, state packet | Server update; packet unconditional, radius 250 |
| `TileEntityLaunchTable` | Inventory/tank/energy refresh, state and multipart packets, power scan | Every server update; up to 81 power reads |
| `TileEntityCompactLauncher` | Inventory/tank/energy refresh, state and multipart packets, power scan | Every server update; up to 9 power reads |
| `TileEntityMachineRadarScreen` | Full screen-state packet | Every server update, radius 100 |
| `TileEntityRadioTorchBase` | NBT configuration snapshot and packet | Every server update, radius 50; sender/receiver call the base path |
| `TileEntityRadioTorchSender` | Adjacent redstone/comparator read, then base packet path | Every server update |
| `TileEntityRadioTorchReceiver` | Channel lookup, then base packet path | Every server update when configured |
| `TileEntityRadioTorchLogic` | Config/mapping packet; optional channel evaluation | Every server update, radius 50 |
| `TileEntityRadioTorchCounter` | Neighbor inventory snapshot and state packet | Every server update; inventory scan only when a neighbor inventory exists |
| `TileEntityTurretCIWS` | Connection refresh and unchanged gauge broadcast | Every server update; `sendToAll()` |
| `TileEntityTurretBaseNT` | Connection refresh and complete packet snapshot | Every server update, radius 250 |
| `TileEntityDroneRequester` | Matcher NBT packet | Every server update, radius 15 |

“Meaningful idle work” here is defined narrowly as unconditional synchronization, redstone/world probing, or inventory snapshotting that continues when state is unchanged or the principal operation is inactive. It is **13 confirmed source paths**, not a claim that every other class is free of inefficient polling. The migrated machine families retain ordinary TileEntity callbacks in some cases while useful work moves to `MachineRuntime`; see the earlier performance audit for exact migrated families and typed residual cadences.

## 5. Redstone polling audit

Twenty-two active concrete TE classes contain a direct reference to `isBlockIndirectlyGettingPowered()` or `getIndirectPowerLevelTo()` somewhere in the class. Thirteen concrete `updateEntity()` bodies contain such a reference. The full direct-reference roster is `TileEntityDoorGeneric`, `TileEntityRadioTorchSender`, `TileEntityCraneUnboxer`, `TileEntityCraneGrabber`, `TileEntityCraneExtractor`, `TileEntityCraneBoxer`, `TileEntityFluidPump`, `TileEntityFan`, `TileEntityMachineBattery`, `TileEntityChungus`, `TileEntityChlorineSeal`, `TileEntityBlastDoor`, `TileEntityFoundryOutlet`, `TileEntityMachineSiren`, `TileEntityWatz`, `TileEntityVaultDoor`, `TileEntityVent`, `TileEntityDecoTapeRecorder`, `TileEntityFireworks`, `TileEntityCompactLauncher`, `TileEntityLaunchTable`, and `TileEntityLaunchPadRusted`. The remaining consumers include block-neighbor callbacks, on-demand gating, and runtime callbacks, so the 22 are not all continuous pollers.

The 13 update-path owners are `TileEntityRadioTorchSender`, `TileEntityCraneUnboxer`, `TileEntityCraneGrabber`, `TileEntityCraneExtractor`, `TileEntityCraneBoxer`, `TileEntityFluidPump`, `TileEntityFan`, `TileEntityBlastDoor`, `TileEntityVaultDoor`, `TileEntityVent`, `TileEntityFireworks`, `TileEntityCompactLauncher`, and `TileEntityLaunchTable`.

| Finding | Source behavior | Deterministic upper bound |
|---|---|---:|
| F03 | Blast Door reads base and upper input each server update. Because `&&` binds more tightly than `||`, upper power bypasses `!isLocked()`. | 2 redstone API calls/tick = 40/s/tile |
| F11 | Vault Door scans both 5-by-6 planes before checking metadata orientation; only one plane’s result is used. | 60 calls/tick = 1,200/s/tile when unlocked and no early hit |
| F05 | Launch Table visits a 9-by-9 plane; its `canLaunch()` check is evaluated only after each powered test. | 81 calls/tick = 1,620/s/tile if no position is powered |
| F05 | Compact Launcher visits a 3-by-3 plane under the same ordering. | 9 calls/tick = 180/s/tile if no position is powered |

`TileEntityDoorGeneric` and `TileEntityLaunchPadBase`/`TileEntityLaunchPadRusted` also expose redstone behavior through event callbacks; these are not counted as update-path polling. `TileEntityMachineSiren`, `TileEntityChlorineSeal`, and other migrated machine consumers use declared `MachineRuntime` cadence and are covered by the prior audit. In particular, the siren’s 5/20-tick buckets and changed-state packets are an intentional cadence, not an unconditional every-tick packet.

## 6. Timer and deadline audit

The active matrix flags timer, modulo, age, cooldown, and world-time surfaces along each inheritance chain. These signatures have three materially different owners: stable recipe progress already migrated to elapsed-loaded-tick accounting; intentional one-shot animations; and periodic discovery/retry work. The prior audit contains the exact recipe/progress roster and residual active-task cadences; this report does not recast those intentional callbacks as waste.

**One repeated-threshold world-mutation system was confirmed:** `TileEntityBlastDoor` uses five `>=` thresholds per animation, with no one-shot latch around each dummy edit.

- Opening runs for 100 ticks. The five `removeDummy()` checks run 100 + 81 + 61 + 41 + 21 = **304 times**; five successful removals are needed, so **299 calls are redundant**.
- Closing runs for 100 ticks. The five `placeDummy()` checks run 81 + 61 + 41 + 21 + 1 = **205 times**; five placements are needed, so **200 calls are redundant**.
- One full open plus close therefore makes **509 dummy-operation attempts for 10 intended mutations, 499 redundant attempts**. Each redundant attempt still performs its block lookup/replaceability check or dummy-block comparison.

Vault Door uses `timer ==` sound events and finishes once at `timer >= 120`; its one-shot sequence does not have the Blast Door’s repeated world-mutation thresholds. Its timer and redstone edge state are persisted, so a reload during a transition resumes from saved `state`, `isOpening`, and `timer` values. No value normalization is visible in those read methods; malformed NBT behavior was not runtime-tested.

`RequestNetwork.updateEntries()` is a timer defect, not a per-tile timer: `timer` is set to 20 and never decremented. The guard `timer < 0` is unreachable from its initialized state, so the intended expiry throttle is absent.

## 7. World-query audit

The deterministic wide scans are concentrated in the redstone table above and turret targeting:

- `TileEntityTurretBase.updateEntity()` copies `worldObj.loadedEntityList` to an `Object[]` for each active powered AI tick. It calls `isInSight()` before checking squared distance, so eligible living entities or missiles outside the final radius can still trigger line-of-sight ray tests. For CIWS the configured squared distance is 100,000 (about 316 blocks); the loaded list copy and candidate walk are not spatially bounded by that radius.
- `TileEntityTurretCIWS.updateEntity()` calls its power connection refresh each server tick, even at zero spin or without a shot. Its connection descriptor method constructs a one-element array and `DirPos` on each refresh.
- `TileEntityTurretBaseNT.updateEntity()` refreshes eight power connection positions every server tick and serializes a complete state NBT snapshot every server tick. Target discovery itself is interval-gated; current-target validity/LOS still runs while the server path is active.
- `TileEntityDummy.updateEntity()` reads the stored controller coordinate once per server tick and retains itself if the block is any `IMultiblock`. There is no explicit loaded-chunk check and no owner identity check.
- `TileEntityRequestNetwork.updateEntity()` performs a bounded 5-by-5 chunk map lookup every 20 world ticks, checks all known paths, and attempts up to five new node discoveries per tile each cycle. `hasPath()` performs two ray traces. `TileEntityDroneDock` scans a 11-by-11 chunk region each second while a drone is present, builds request/offer lists, shuffles them during up to five attempts, and generates candidate paths. This is periodic active logistics work, but it repeatedly rediscovers state rather than responding to topology invalidation.

The current power and fluid network architecture has already moved away from the old global topology sweep: `TileEntityCableBaseNT` suppresses tile ticking and queues reconciliation from lifecycle changes into `UniNodespace`; FluidNetMK2 is the authoritative fluid graph and PipeNet remains an adapter. The prior RTM audit documents that work and the endpoint opt-ins. The issues above are separate legacy TE scans and should not be used to infer that the newer network graph is still globally polled.

## 8. Neighbor and topology audit

Seven recurring neighborhood/topology scan sites were confirmed in the focused paths: Blast Door’s four-way recursive discovery; Vault Door’s two redstone planes; Launch Table’s 9-by-9 scan; Compact Launcher’s 3-by-3 scan; CIWS connection refresh; TurretBaseNT’s eight-position connection refresh; and RequestNetwork/DroneDock periodic graph discovery. The first four are fixed spatial scans; the turret paths repeat connection setup; RequestNetwork and DroneDock perform bounded graph rediscovery. Other direct neighbor reads across 210 concrete class bodies are surfaced in the matrix but are not all scans or per-tick behavior.

Blast Door repeats four explicit `getTileEntity()` lookups in each `openNeigh()`, `closeNeigh()`, and `lockNeigh()` visit. The source repeats the same four direction branches three times. Lock state or transition state prevents revisiting a node, but no visited collection records the traversal.

## 9. Recipe, inventory, tank, and energy polling

The existing [recipe and progress cadence audit](RTM_PERFORMANCE_AUDIT.md#recipe-and-progress-cadence-overhaul--2026-09-27) is the authoritative detailed list for the migrated 35 recipe/progress classes. It records where stable work now settles at completion/resource boundaries and where one-tick work remains for a concrete simulation dependency. This audit found no evidence that those already documented changes should be reversed or broadened.

Outside that migrated roster, the highest-confidence recurring inventory/tank/energy reads in this pass are:

- Launch Table and Compact Launcher refresh tank contents and item-backed energy every server update before sending their packets and scanning redstone, even while not launching.
- Radio Torch Counter performs one adjacent tile lookup and, if it is an inventory, allocates an `ItemStack[]` and copies every slot each server update. It only performs pattern comparisons for configured channels and filters, but the snapshot is built even when all channels are empty.
- TurretBaseNT charges from item slots every server update and sends a full state snapshot independently of whether the values changed.

Most other machine inventory/tank mutations are covered by the previous audit’s `MachineRuntime` dirty causes, tank-owner callbacks, coarse fingerprints, and saved-state reconstruction. The matrix provides a static index of all classes touching these systems; a literal inventory signature is not equated with an unnecessary poll.

## 10. Packet and network audit

The following direct update paths send unchanged or full-state packets at a deterministic rate. Frequencies are packet-send invocations, not bytes; radius describes the source `TargetPoint` radius.

| Class/path | Trigger and payload | Server frequency | Scope |
|---|---|---:|---|
| `TileEntityBlastDoor` | `TEVaultPacket`, state heartbeat | 20/s/tile | Around radius 250; separate open/close transition packet also exists |
| `TileEntityVaultDoor` | `TEVaultPacket`, state heartbeat | 20/s/tile | Around radius 250; separate transition packet also exists |
| `TileEntityLaunchTable` | `BufPacket` plus `TEMissileMultipartPacket` | 40 sends/s/tile | Radii 50 and 250; empty state allocates/sends a new empty `MissileStruct` |
| `TileEntityCompactLauncher` | Same pair as Launch Table | 40 sends/s/tile | Radii 50 and 250; empty state allocates/sends a new empty `MissileStruct` |
| `TileEntityTurretCIWS` | `AuxGaugePacket` | 20/s/tile | `sendToAll()` across all clients, even unchanged rotation |
| `TileEntityTurretBaseNT` | Serialized complete NBT snapshot | 20/s/tile | Around radius 250; unconditional server update path |
| `TileEntityMachineRadarScreen` | `BufPacket` screen snapshot | 20/s/tile | Around radius 100 |
| `TileEntityRadioTorchBase` | NBT configuration and mappings | 20/s/tile | Around radius 50; Sender/Receiver call `super.updateEntity()` |
| `TileEntityRadioTorchLogic` | NBT configuration, mappings, and conditions | 20/s/tile | Around radius 50 |
| `TileEntityRadioTorchCounter` | NBT matcher and counts | 20/s/tile | Around radius 15 |
| `TileEntityDroneRequester` | Matcher NBT | 20/s/tile | Around radius 15 |

For scale, 100 loaded Blast Doors produce 2,000 heartbeat send calls per second; 100 launchers of either type produce 4,000 send calls per second. Recipient count and payload size are not inferred. The active HBM source contains **two direct `sendToAll()` call sites**: Blast Door’s transition method and CIWS’s per-tick gauge path. Local state is therefore globally broadcast by CIWS.

Other packet senders in the matrix are often event-, active-operation-, or coarse-cadence paths. For example, the migrated siren sends on state changes plus a 20-tick baseline; active particle packets are not counted as idle state heartbeats. The 130-class source-surface count in the matrix includes network helper and packet calls anywhere in class bodies and is not a count of unique wire messages.

## 11. Block/world mutation audit

F04’s Blast Door repeated-threshold attempts are the only confirmed repeated animation mutation candidate in this pass. A call can stop mutating after the first edit because the block no longer matches or is no longer replaceable, but the world query and method attempt still repeat for every later threshold tick.

The active census finds 52 concrete class bodies with direct `setBlock`, `setBlockToAir`, or `func_147480_a` calls. Thirteen concrete update bodies contain one of those calls. These include legitimate reactor, structure, and active machine effects; they are not all redundant. `TileEntityDummy` and the three dummy block break paths are called out separately because controller ownership affects whether the mutation is safe.

## 12. Multiblock, dummy, proxy, and ownership audit

`TileEntityDummy` persists only `targetX/Y/Z`. It checks whether that coordinate currently contains any `IMultiblock`; it does not verify a controller type, block identity, expected master identity, or matching dummy ownership. The blast/vault dummy break blocks use those stored coordinates to destroy the target tile with drops and do not first validate that the target is the corresponding Blast/Vault Door. A stale coordinate can therefore destroy a replacement multiblock at that position. `DummyOldBase` checks for `IMultiblock` before teardown, but still does not verify that it is the original owner.

The code has three static mutable guard flags: `DummyBlockBlast.safeBreak`, `DummyBlockVault.safeBreak`, and `DummyOldBase.safeBreak`. The first two are set true immediately before replacing a dummy with air and reset afterward; neither pair uses `try/finally`. They are class-global across all worlds and dimensions. Re-entrant block teardown can observe the guard while it is set, and an exception between the set/reset can leave it enabled. No writer to `DummyOldBase.safeBreak` was found in the active source tree, so that flag appears unused.

The marker scan found eight concrete classes with direct multiblock/structure identification signatures: `TileEntityLaunchTable`, `TileEntityLaunchPadRocket`, `TileEntityCompactLauncher`, `TileEntityCustomMachine`, `TileEntityDummy`, `TileEntityMachineRocketAssembly`, `TileEntityMachineSPP`, and `TileEntityMachineMissileAssembly`. This is a lexical scanner count; it does not include helper calls whose method names do not carry a structure marker.

## 13. Recursion and traversal audit

| Recursive family | Entry and recursive calls | Cycle control | Bound and risk |
|---|---|---|---|
| Blast Door | `openNeigh()` calls neighbor `open()` then `openNeigh()`; `closeNeigh()` calls neighbor `close()` then `closeNeigh()`; `lockNeigh()` calls neighbor `lock()` then `lockNeigh()` | Open/close use `state` transition eligibility; locking uses `isLocked()` before recursion. No visited set. | Up to four `getTileEntity()` lookups per visited tile per method. Stack depth is the connected compatible component size; no explicit code bound. Long chains can exhaust the Java stack. |

The recursion is cycle-terminating under normal state transitions, so the source does not show an infinite cycle on a small closed loop. Its risk is depth proportional to input topology and repeated graph rediscovery. Other inspected network traversal uses iterative collections and bounded per-cycle discovery rather than recursive tile calls.

## 14. Rendering and client tile audit

There are **66 active concrete classes with an explicit `INFINITE_EXTENT_AABB` return** and **171 concrete classes declaring a custom `getMaxRenderDistanceSquared()`**. All 171 direct overrides return 65,536 squared distance (a 256-block radius). The full set is marked in the class matrix. Many HBM renders represent structures or effects beyond the controller block, so this census is not a verdict that all 66 infinite bounds or 171 distance overrides are wrong.

The clearest review candidates are `TileEntityBlastDoor`, `TileEntityVaultDoor`, and the bomb turret base: each uses an infinite render AABB and a 65,536 squared-distance override (256-block radius), while the moving door/dummy or local turret geometry is much smaller. Actual TESR geometry, model bounds, and culling behavior need a client render capture before calling these confirmed rendering defects. This audit did not alter rendering or lighting code.

## 15. State-machine audit

Blast Door and Vault Door use `state` values 0/1/2 plus `isOpening`; the intended combinations are closed `(0,false)`, opening `(1,true)`, open `(2,true)`, and closing `(1,false)`. Their transition entry methods set the direction and state together, and their finish methods set the terminal state. The transition timer is reset in the next idle update and persisted alongside direction/state. NBT readers accept any integer state/timer and do not normalize invalid combinations.

Blast Door additionally persists `redstoned`, so the rising-edge latch survives unload. This prevents a held signal from automatically retriggering immediately after reload, but behavior depends on the saved edge value. `TileEntityCharge` has its own defuse pending timer and player state; it is not included as a door defect. No broad state-corruption claim is made without malformed-save runtime reproduction.

## 16. NBT and save/load audit

- `TileEntityDummy` writes/reads the target coordinates used by destructive teardown; the format contains no master identity token. This is the ownership weakness described in F02/F12.
- Blast Door and Vault Door write/read `state`, `isOpening`, `timer`, `redstoned`, and `sysTime`. `sysTime` has no use outside its declaration and NBT read/write in these two classes: **two persisted unused fields**.
- Static `RequestNetwork.activeWaypoints` is not NBT state. `RTTYSystem` keys state by `World` and `forgetWorld()` is called on world unload, so the source contains an explicit world cleanup path.
- The prior machine audit documents the current lifecycle-generation behavior, task reconstruction, deferred chunk dirty handling, and retained legacy save keys. In particular, do not treat the already corrected chunk-validation save recursion as a current finding.

These findings are source-only. This audit did not load or mutate a world to check transition resumption, chunk-boundary teardown, dirty marking, or legacy-save behavior.

## 17. Allocation and GC audit

Twenty-seven concrete update methods contain selected allocation signatures (`new` collection/array/geometry/packet-adjacent types). This is a candidate count, not a measured allocation profile. The complete source-level roster is `TileEntityDoorGeneric`, `TileEntityRadioTorchCounter`, `TileEntityDroneWaypoint`, `TileEntityDroneDock`, `TileEntityDroneCrate`, `TileEntityCraneBoxer`, `TileEntityPartEmitter`, `TileEntityEmitter`, `TileEntityLightningVolcano`, `TileEntityCranePartitioner`, `TileEntityVaultDoor`, `TileEntityPlasmaStruct`, `TileEntityMachineRadarNT`, `TileEntityMachineHTRF4`, `TileEntityMachineHTR3`, `TileEntityBlastDoor`, `TileEntityFileCabinet`, `TileEntityAtmosphereEditor`, `TileEntityDecoBlockAltF`, `TileEntityLaunchPadLarge`, `TileEntityLaunchPad`, `TileEntityFireworks`, `TileEntityCompactLauncher`, `TileEntityCharge`, `TileEntityLaunchTable`, `TileEntityLaunchPadRusted`, and `TileEntityCapacitor`. The strongest repeated allocations are:

- Active `TileEntityTurretBase` scan: a full `loadedEntityList.toArray()` copy each powered AI tick; line-of-sight helpers create temporary vectors for eligible candidates.
- Launch Table and Compact Launcher: two packet objects and two target points per server tick, plus an empty `MissileStruct` every tick when no custom missile is loaded.
- Radio Torch Counter: `ItemStack[]` sized to the neighbor inventory per update, followed by per-slot reference copies; NBT packet state is also rebuilt each update.
- CIWS: one `DirPos[]` and one `DirPos` from `getConPos()` each server update.
- Blast/Vault door heartbeats: packet and target-point objects per server tick; Blast Door also repeats the five dummy-operation calls while transitioning.
- Drone Dock: request/offer list construction and shuffling occur once per second while it has a drone; these are bounded active logistics allocations, not per-tick idle allocations.

No allocation rate or GC pause duration is claimed without a profiler capture.

## 18. Copy-paste and shared-pattern audit

The clearest correctness-relevant duplication is Blast Door’s repeated four-direction `te0` through `te3` logic copied into three recursive methods. This multiplies the same neighbor query pattern and makes lock/open/close cycle rules easy to diverge. The matrix also shows broad inheritance-centered behavior in `TileEntityMachineBase`, `TileEntityLoadedBase`, `TileEntityTurretBaseNT`, and network bases. The runtime migration documented in the prior audit addresses the production-machine polling architecture; this report does not recommend reintroducing a second scheduler or network owner.

Ten shared bad-pattern clusters were confirmed for future work planning:

| Cluster | Confirmed breadth | Main impact |
|---|---|---|
| Full-state idle packet heartbeat | 11 concrete source paths in the focused packet table | Network, packet construction, client decode |
| Fixed-area redstone polling | 4 high-frequency structures; plus 13 updater implementations containing redstone APIs | World queries and server CPU |
| Door recursive neighbor propagation | 1 family, 3 recursive methods | Stack robustness, repeated world lookup |
| Dummy/controller ownership gap | 3 dummy break implementations plus `TileEntityDummy` | Destructive correctness, reload robustness |
| World-global safe-break guards | 3 static fields; 2 active writers | Reentrancy and cross-world state |
| Repeated topology discovery | 3 families: legacy turret links, request network, drone dock | CPU, collections, ray tracing |
| Broad tile culling bounds | 66 explicit infinite bounds; selected local objects reviewed as candidates | Client culling/render work |
| Hot update allocation candidates | 27 selected update bodies; strongest cases enumerated above | Allocation/GC, CPU |
| Expensive target checks before distance rejection | Shared `TileEntityTurretBase` acquisition path used by its turret descendants | Entity-list copying, LOS/ray traces |
| Persisted transition state without normalization | Blast and Vault Door restore timer/state fields directly from NBT | Reload robustness, malformed-state behavior |

## 19. Severity-ranked findings

Severity describes source risk and expected execution shape, not measured runtime cost.

| ID | Severity | Type | Finding |
|---|---|---|---|
| F01 | CRITICAL | ROBUSTNESS, PERFORMANCE | Blast Door’s three recursive traversals have no visited set or depth limit; stack depth follows connected compatible door count. |
| F02 | CRITICAL | CORRECTNESS, ROBUSTNESS | Blast/Vault dummy break can destroy an unrelated replacement multiblock at persisted target coordinates because it does not validate controller ownership/type. |
| F03 | HIGH | CORRECTNESS | Blast Door upper redstone input bypasses the lock check due to `&&`/`||` precedence. |
| F04 | HIGH | NETWORK, PERFORMANCE | Blast/Vault send unchanged `TEVaultPacket` state every server tick to radius 250; transition packets are also sent. |
| F05 | HIGH | PERFORMANCE, NETWORK | Launch Table/Compact Launcher each combine two every-tick packet sends with up to 81/9 redstone checks, plus per-tick inventory/tank/energy refresh. |
| F06 | HIGH | NETWORK, PERFORMANCE | TurretBaseNT refreshes connection positions, serializes full state, and sends radius-250 data every server tick. |
| F07 | HIGH | PERFORMANCE, ALLOCATION | Powered bomb turret copies the complete loaded entity list and performs LOS before checking distance. |
| F08 | HIGH | PERFORMANCE, ARCHITECTURE | RequestNetwork expiry cleanup timer never decrements; global node/chunk expiry traversal runs once per server tick. |
| F09 | HIGH | NETWORK, ALLOCATION | Radio torch configuration/logic/counter and drone requester matcher state are repacked and sent unchanged every server tick. |
| F10 | HIGH | PERFORMANCE, WORLD ACCESS | Blast Door repeats threshold dummy edits 499 times redundantly over a full open/close animation. |
| F11 | MEDIUM | PERFORMANCE, WORLD ACCESS | Vault Door scans up to 60 redstone coordinates/tick while unlocked, even for the orientation plane not used. |
| F12 | MEDIUM | CORRECTNESS, ROBUSTNESS | Dummy TE accepts any `IMultiblock` at saved coordinates as its owner and has no explicit chunk-loaded guard. |
| F13 | MEDIUM | ROBUSTNESS | Two used `safeBreak` static booleans are not protected by `try/finally`; an exception can leave global teardown suppression enabled. |
| F14 | MEDIUM | RENDERING | Blast Door, Vault Door, and turret bounds are infinite with a 256-block render distance despite local moving geometry; client visual validation remains required. |
| F15 | MEDIUM | PERFORMANCE | 78 empty concrete overrides, nine inherited empty overrides, and 72 default/no-HBM ticker classes leave no substantive HBM update body; 143 of those classes permit ordinary callback dispatch. |
| F16 | MEDIUM | PERFORMANCE, ARCHITECTURE | RequestNetwork and DroneDock rediscover bounded chunk-local topology every second and ray-trace paths rather than being driven solely by invalidation. |
| F17 | MEDIUM | PERFORMANCE, ALLOCATION | Radio Torch Counter snapshots the full adjacent inventory into a new array every server update even with no configured channels. |
| F18 | MEDIUM | NETWORK | Radar Screen sends a full `BufPacket` every server update to radius 100 without a change check in its caller. |
| F19 | MEDIUM | ALLOCATION, PERFORMANCE | Twenty-seven concrete update bodies match selected hot-allocation patterns; only the enumerated examples are high-confidence recurring cases. |
| F20 | MEDIUM | RENDERING | Sixty-six active concrete TEs use an infinite AABB and 171 override render distance; many may be justified by multiblock/effect geometry, but all need extent ownership documented. |
| F21 | LOW | PERSISTENCE | Blast/Vault `sysTime` is persisted but unused outside NBT read/write (two fields total). |
| F22 | LOW | MAINTAINABILITY | Blast Door repeats identical directional query/cast branches across open, close, and lock propagation. |
| F23 | LOW | ROBUSTNESS | `DummyOldBase.safeBreak` is a static guard field with no active writer found in the HBM source tree. |
| F24 | LOW | PERFORMANCE, DIAGNOSTICS | Radio Torch Counter emits `System.out.println("guh")` for a control path; not a major tick hotspot, but it is uncontrolled console output. |

### Key source anchors

| Finding | Source |
|---|---|
| Blast Door lock precedence, repeated thresholds, heartbeat, recursion, and dummy removal | [TileEntityBlastDoor.java](../src/main/java/com/hbm/tileentity/machine/TileEntityBlastDoor.java#L38) |
| Vault Door redstone planes and heartbeat | [TileEntityVaultDoor.java](../src/main/java/com/hbm/tileentity/machine/TileEntityVaultDoor.java#L40) |
| Destructive dummy teardown and static guards | [DummyBlockBlast.java](../src/main/java/com/hbm/blocks/machine/DummyBlockBlast.java#L25), [DummyBlockVault.java](../src/main/java/com/hbm/blocks/machine/DummyBlockVault.java#L25), [DummyOldBase.java](../src/main/java/com/hbm/blocks/machine/DummyOldBase.java#L23) |
| Dummy controller probe and saved coordinates | [TileEntityDummy.java](../src/main/java/com/hbm/tileentity/machine/TileEntityDummy.java#L15) |
| Launch Table and Compact Launcher update loops | [TileEntityLaunchTable.java](../src/main/java/com/hbm/tileentity/bomb/TileEntityLaunchTable.java#L178), [TileEntityCompactLauncher.java](../src/main/java/com/hbm/tileentity/bomb/TileEntityCompactLauncher.java#L169) |
| Bomb turret entity scan and CIWS heartbeat | [TileEntityTurretBase.java](../src/main/java/com/hbm/tileentity/bomb/TileEntityTurretBase.java#L43), [TileEntityTurretCIWS.java](../src/main/java/com/hbm/tileentity/bomb/TileEntityTurretCIWS.java#L40) |
| TurretBaseNT refresh and packet send | [TileEntityTurretBaseNT.java](../src/main/java/com/hbm/tileentity/turret/TileEntityTurretBaseNT.java#L165) |
| Request-network expiry cadence and server-tick caller | [RequestNetwork.java](../src/main/java/com/hbm/tileentity/network/RequestNetwork.java#L18), [ModEventHandler.java](../src/main/java/com/hbm/main/ModEventHandler.java#L1721) |
| Radio torch/counter/requester and radar-screen packet paths | [TileEntityRadioTorchBase.java](../src/main/java/com/hbm/tileentity/network/TileEntityRadioTorchBase.java#L29), [TileEntityRadioTorchLogic.java](../src/main/java/com/hbm/tileentity/network/TileEntityRadioTorchLogic.java#L39), [TileEntityRadioTorchCounter.java](../src/main/java/com/hbm/tileentity/network/TileEntityRadioTorchCounter.java#L40), [TileEntityDroneRequester.java](../src/main/java/com/hbm/tileentity/network/TileEntityDroneRequester.java#L44), [TileEntityMachineRadarScreen.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadarScreen.java#L28) |

## 20. Class-by-class matrix

The matrix has one row per concrete HBM TileEntity declaration in the active source tree: 356 rows. The active machine families share detailed runtime notes with the existing RTM performance audit.

Feature codes: `R` redstone API; `I` inventory; `F` fluid; `E` energy/power; `H` heat; `Rc` recipe; `N` neighbor/world access; `G` network/graph; `Pkt` packet/send helper; `M` multiblock/dummy/structure; `Ent` entity/player; `Inf` infinite render bounds; `Rd` custom render distance; `T` timer/world-time in an update path; `Mut` block mutation; `NBT` NBT read/write; `A` selected allocation signature in an update path. Codes are inherited through the scanned source base chain. They are static source tags and must be read with the detailed findings above.

### Active HBM source

| Source tree | Class | Source file | Base chain (nearest first) | Effective ticker and side guard | canUpdate() | Source surfaces | Findings |
|---|---|---|---|---|---|---|---|
| Active HBM source | `JarDummyConnector` | `src/main/java/com/hbm/wiaj/cannery/Dummies.java` | `JarDummyConnector ← TileEntity` | no HBM body | true | — | F15 |
| Active HBM source | `TileEntityFauxOutlet` | `src/main/java/com/hbm/wiaj/cannery/CanneryFoundryChannel.java` | `TileEntityFauxOutlet ← TileEntityFoundryOutlet ← TileEntityFoundryBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityFoundryOutlet (remote-guard) | true | R, E, Pkt, NBT, A | — |
| Active HBM source | `TileEntityFauxCrucible` | `src/main/java/com/hbm/wiaj/cannery/CanneryCrucible.java` | `TileEntityFauxCrucible ← TileEntityCrucible ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityCrucible (unguarded) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityRailSwitch` | `src/main/java/com/hbm/blocks/rail/RailStandardSwitch.java` | `TileEntityRailSwitch ← TileEntity` | no HBM body | false | NBT | — |
| Active HBM source | `TileEntityLightningVolcano` | `src/main/java/com/hbm/blocks/BlockVolcanoV2.java` | `TileEntityLightningVolcano ← TileEntity` | own (server/client) | true | Pkt, Ent, Inf, Rd, T, NBT, A | F20, F19 |
| Active HBM source | `TileEntityFluidPump` | `src/main/java/com/hbm/blocks/network/FluidPump.java` | `TileEntityFluidPump ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | R, I, F, E, N, Pkt, T, NBT, A | — |
| Active HBM source | `TileEntityPipePaintable` | `src/main/java/com/hbm/blocks/network/FluidDuctPaintable.java` | `TileEntityPipePaintable ← TileEntityPipeBaseNT ← TileEntity` | own (remote-guard) | false | F, E, N, G, NBT, A | — |
| Active HBM source | `TileEntityPipeGauge` | `src/main/java/com/hbm/blocks/network/FluidDuctGauge.java` | `TileEntityPipeGauge ← TileEntityPipeBaseNT ← TileEntity` | own (server/client) | true | F, E, N, G, Pkt, T, NBT, A | — |
| Active HBM source | `TileEntityCranePartitioner` | `src/main/java/com/hbm/blocks/network/CranePartitioner.java` | `TileEntityCranePartitioner ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, Rc, N, Pkt, T, NBT, A | F19 |
| Active HBM source | `TileEntityDiode` | `src/main/java/com/hbm/blocks/network/CableDiode.java` | `TileEntityDiode ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | E, N, G, NBT | — |
| Active HBM source | `TileEntityCablePaintable` | `src/main/java/com/hbm/blocks/network/BlockCablePaintable.java` | `TileEntityCablePaintable ← TileEntityCableBaseNT ← TileEntity` | own (remote-guard) | false | E, G, NBT | — |
| Active HBM source | `TileEntityCableGauge` | `src/main/java/com/hbm/blocks/network/BlockCableGauge.java` | `TileEntityCableGauge ← TileEntityCableBaseNT ← TileEntity` | own (server/client) | true | E, G, Pkt, T, A | — |
| Active HBM source | `TileEntityVolcanoCore` | `src/main/java/com/hbm/blocks/bomb/BlockVolcano.java` | `TileEntityVolcanoCore ← TileEntity` | own (server/client) | true | N, Pkt, T, Mut, NBT, A | — |
| Active HBM source | `TileEntityBobble` | `src/main/java/com/hbm/blocks/generic/BlockBobble.java` | `TileEntityBobble ← TileEntity` | no HBM body | false | NBT | — |
| Active HBM source | `TileEntityBedrockOre` | `src/main/java/com/hbm/blocks/generic/BlockBedrockOreTE.java` | `TileEntityBedrockOre ← TileEntity` | no HBM body | false | F, NBT | — |
| Active HBM source | `TileEntityTurretTauon` | `src/main/java/com/hbm/tileentity/turret/TileEntityTurretTauon.java` | `TileEntityTurretTauon ← TileEntityTurretBaseNT ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Inf, Rd, T, NBT, A | F20, F06 |
| Active HBM source | `TileEntityTurretSentryDamaged` | `src/main/java/com/hbm/tileentity/turret/TileEntityTurretSentryDamaged.java` | `TileEntityTurretSentryDamaged ← TileEntityTurretSentry ← TileEntityTurretBaseNT ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityTurretSentry (server/client) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Inf, Rd, T, NBT, A | F20, F06 |
| Active HBM source | `TileEntityTurretSentry` | `src/main/java/com/hbm/tileentity/turret/TileEntityTurretSentry.java` | `TileEntityTurretSentry ← TileEntityTurretBaseNT ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Inf, Rd, T, NBT, A | F20, F06 |
| Active HBM source | `TileEntityTurretRichard` | `src/main/java/com/hbm/tileentity/turret/TileEntityTurretRichard.java` | `TileEntityTurretRichard ← TileEntityTurretBaseNT ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Inf, Rd, T, NBT, A | F20, F06 |
| Active HBM source | `TileEntityTurretMaxwell` | `src/main/java/com/hbm/tileentity/turret/TileEntityTurretMaxwell.java` | `TileEntityTurretMaxwell ← TileEntityTurretBaseNT ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Inf, Rd, T, NBT, A | F20, F06 |
| Active HBM source | `TileEntityTurretJeremy` | `src/main/java/com/hbm/tileentity/turret/TileEntityTurretJeremy.java` | `TileEntityTurretJeremy ← TileEntityTurretBaseNT ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Inf, Rd, T, NBT, A | F20, F06 |
| Active HBM source | `TileEntityTurretHowardDamaged` | `src/main/java/com/hbm/tileentity/turret/TileEntityTurretHowardDamaged.java` | `TileEntityTurretHowardDamaged ← TileEntityTurretHoward ← TileEntityTurretBaseNT ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityTurretHoward (server/client) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Inf, Rd, T, NBT, A | F20, F06 |
| Active HBM source | `TileEntityTurretHoward` | `src/main/java/com/hbm/tileentity/turret/TileEntityTurretHoward.java` | `TileEntityTurretHoward ← TileEntityTurretBaseNT ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Inf, Rd, T, NBT, A | F20, F06 |
| Active HBM source | `TileEntityTurretHIMARS` | `src/main/java/com/hbm/tileentity/turret/TileEntityTurretHIMARS.java` | `TileEntityTurretHIMARS ← TileEntityTurretBaseArtillery ← TileEntityTurretBaseNT ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Inf, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityTurretFritz` | `src/main/java/com/hbm/tileentity/turret/TileEntityTurretFritz.java` | `TileEntityTurretFritz ← TileEntityTurretBaseNT ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Inf, Rd, T, NBT, A | F20, F06 |
| Active HBM source | `TileEntityTurretChekhov` | `src/main/java/com/hbm/tileentity/turret/TileEntityTurretChekhov.java` | `TileEntityTurretChekhov ← TileEntityTurretBaseNT ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Inf, Rd, T, NBT, A | F20, F06 |
| Active HBM source | `TileEntityTurretBrandon` | `src/main/java/com/hbm/tileentity/turret/TileEntityTurretBrandon.java` | `TileEntityTurretBrandon ← TileEntityTurretBaseNT ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityTurretBaseNT (server/client) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Inf, Rd, T, NBT, A | F20, F06 |
| Active HBM source | `TileEntityTurretArty` | `src/main/java/com/hbm/tileentity/turret/TileEntityTurretArty.java` | `TileEntityTurretArty ← TileEntityTurretBaseArtillery ← TileEntityTurretBaseNT ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Inf, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityProxyInventory` | `src/main/java/com/hbm/tileentity/TileEntityProxyInventory.java` | `TileEntityProxyInventory ← TileEntityProxyBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | false | I, E, M, NBT, A | — |
| Active HBM source | `TileEntityProxyEnergy` | `src/main/java/com/hbm/tileentity/TileEntityProxyEnergy.java` | `TileEntityProxyEnergy ← TileEntityProxyBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | false | E, M, NBT | — |
| Active HBM source | `TileEntityProxyConductor` | `src/main/java/com/hbm/tileentity/TileEntityProxyConductor.java` | `TileEntityProxyConductor ← TileEntityProxyBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | false | E, M, NBT | — |
| Active HBM source | `TileEntityProxyCombo` | `src/main/java/com/hbm/tileentity/TileEntityProxyCombo.java` | `TileEntityProxyCombo ← TileEntityProxyBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | false | I, E, H, M, NBT, A | — |
| Active HBM source | `TileEntityProxyBase` | `src/main/java/com/hbm/tileentity/TileEntityProxyBase.java` | `TileEntityProxyBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | false | E, M, NBT | — |
| Active HBM source | `TileEntityLoadedBase` | `src/main/java/com/hbm/tileentity/TileEntityLoadedBase.java` | `TileEntityLoadedBase ← TileEntity` | no HBM body | true | E, NBT | F15 |
| Active HBM source | `TileEntityDoorGeneric` | `src/main/java/com/hbm/tileentity/TileEntityDoorGeneric.java` | `TileEntityDoorGeneric ← TileEntityLockableBase` | own (server/client) | true | R, Pkt, M, Inf, Rd, NBT, A | F20, F19 |
| Active HBM source | `TileEntityData` | `src/main/java/com/hbm/tileentity/TileEntityData.java` | `TileEntityData ← TileEntity` | no HBM body | true | NBT | F15 |
| Active HBM source | `TileEntityYellowBarrel` | `src/main/java/com/hbm/tileentity/deco/TileEntityYellowBarrel.java` | `TileEntityYellowBarrel ← TileEntity` | no HBM body | true | Rd | F15, F20 |
| Active HBM source | `TileEntityVent` | `src/main/java/com/hbm/tileentity/deco/TileEntityVent.java` | `TileEntityVent ← TileEntity` | own (server/client) | true | R, N | — |
| Active HBM source | `TileEntityTrappedBrick` | `src/main/java/com/hbm/tileentity/deco/TileEntityTrappedBrick.java` | `TileEntityTrappedBrick ← TileEntity` | own (server/client) | true | N, Ent, Mut, A | — |
| Active HBM source | `TileEntityObjTester` | `src/main/java/com/hbm/tileentity/deco/TileEntityObjTester.java` | `TileEntityObjTester ← TileEntity` | no HBM body | true | Inf, Rd | F15, F20 |
| Active HBM source | `TileEntityLanternBehemoth` | `src/main/java/com/hbm/tileentity/deco/TileEntityLanternBehemoth.java` | `TileEntityLanternBehemoth ← TileEntity` | own (server/client) | true | Pkt, Ent, Rd, NBT, A | F20 |
| Active HBM source | `TileEntityLantern` | `src/main/java/com/hbm/tileentity/deco/TileEntityLantern.java` | `TileEntityLantern ← TileEntity` | no HBM body | true | Rd | F15, F20 |
| Active HBM source | `TileEntityGeysir` | `src/main/java/com/hbm/tileentity/deco/TileEntityGeysir.java` | `TileEntityGeysir ← TileEntity` | own (server/client) | true | N, Pkt, Ent, T, Mut, A | — |
| Active HBM source | `TileEntityDecoTapeRecorder` | `src/main/java/com/hbm/tileentity/deco/TileEntityDecoTapeRecorder.java` | `TileEntityDecoTapeRecorder ← TileEntity` | no HBM body | true | R, N, Rd | F15, F20 |
| Active HBM source | `TileEntityDecoSteelPoles` | `src/main/java/com/hbm/tileentity/deco/TileEntityDecoSteelPoles.java` | `TileEntityDecoSteelPoles ← TileEntity` | no HBM body | true | Rd | F15, F20 |
| Active HBM source | `TileEntityDecoPoleTop` | `src/main/java/com/hbm/tileentity/deco/TileEntityDecoPoleTop.java` | `TileEntityDecoPoleTop ← TileEntity` | no HBM body | true | Rd | F15, F20 |
| Active HBM source | `TileEntityDecoPoleSatelliteReceiver` | `src/main/java/com/hbm/tileentity/deco/TileEntityDecoPoleSatelliteReceiver.java` | `TileEntityDecoPoleSatelliteReceiver ← TileEntity` | no HBM body | true | Rd | F15, F20 |
| Active HBM source | `TileEntityDecoBlockAltW` | `src/main/java/com/hbm/tileentity/deco/TileEntityDecoBlockAltW.java` | `TileEntityDecoBlockAltW ← TileEntity` | no HBM body | true | Inf, Rd | F15, F20 |
| Active HBM source | `TileEntityDecoBlockAltG` | `src/main/java/com/hbm/tileentity/deco/TileEntityDecoBlockAltG.java` | `TileEntityDecoBlockAltG ← TileEntity` | no HBM body | true | Inf, Rd | F15, F20 |
| Active HBM source | `TileEntityDecoBlockAltF` | `src/main/java/com/hbm/tileentity/deco/TileEntityDecoBlockAltF.java` | `TileEntityDecoBlockAltF ← TileEntity` | own (unguarded) | true | Ent, Inf, Rd, A | F20, F19 |
| Active HBM source | `TileEntityDecoBlockAlt` | `src/main/java/com/hbm/tileentity/deco/TileEntityDecoBlockAlt.java` | `TileEntityDecoBlockAlt ← TileEntity` | no HBM body | true | Inf, Rd | F15, F20 |
| Active HBM source | `TileEntityDecoBlock` | `src/main/java/com/hbm/tileentity/deco/TileEntityDecoBlock.java` | `TileEntityDecoBlock ← TileEntity` | no HBM body | true | Inf, Rd | F15, F20 |
| Active HBM source | `TileEntityTurretCIWS` | `src/main/java/com/hbm/tileentity/bomb/TileEntityTurretCIWS.java` | `TileEntityTurretCIWS ← TileEntityTurretBase ← TileEntity` | own (server/client) | true | E, N, Pkt, Ent, Inf, Rd, NBT, A | F07, F14, F20 |
| Active HBM source | `TileEntitySellafield` | `src/main/java/com/hbm/tileentity/bomb/TileEntitySellafield.java` | `TileEntitySellafield ← TileEntity` | own (unguarded) | true | Ent, NBT | — |
| Active HBM source | `TileEntityRedBarrel` | `src/main/java/com/hbm/tileentity/bomb/TileEntityRedBarrel.java` | `TileEntityRedBarrel ← TileEntity` | no HBM body | true | Rd | F15, F20 |
| Active HBM source | `TileEntityNukeTsar` | `src/main/java/com/hbm/tileentity/bomb/TileEntityNukeTsar.java` | `TileEntityNukeTsar ← TileEntity` | no HBM body | true | I, N, Inf, Rd, NBT, A | F15, F20 |
| Active HBM source | `TileEntityNukeSolinium` | `src/main/java/com/hbm/tileentity/bomb/TileEntityNukeSolinium.java` | `TileEntityNukeSolinium ← TileEntity` | no HBM body | true | I, N, Inf, Rd, NBT, A | F15, F20 |
| Active HBM source | `TileEntityNukeShrimp` | `src/main/java/com/hbm/tileentity/bomb/TileEntityNukeShrimp.java` | `TileEntityNukeShrimp ← TileEntity` | no HBM body | true | I, N, Inf, Rd, NBT, A | F15, F20 |
| Active HBM source | `TileEntityNukePrototype` | `src/main/java/com/hbm/tileentity/bomb/TileEntityNukePrototype.java` | `TileEntityNukePrototype ← TileEntity` | no HBM body | true | I, N, Inf, Rd, NBT, A | F15, F20 |
| Active HBM source | `TileEntityNukeN2` | `src/main/java/com/hbm/tileentity/bomb/TileEntityNukeN2.java` | `TileEntityNukeN2 ← TileEntity` | no HBM body | true | I, N, Inf, Rd, NBT, A | F15, F20 |
| Active HBM source | `TileEntityNukeMike` | `src/main/java/com/hbm/tileentity/bomb/TileEntityNukeMike.java` | `TileEntityNukeMike ← TileEntity` | no HBM body | true | I, N, Inf, Rd, NBT, A | F15, F20 |
| Active HBM source | `TileEntityNukeMan` | `src/main/java/com/hbm/tileentity/bomb/TileEntityNukeMan.java` | `TileEntityNukeMan ← TileEntity` | no HBM body | true | I, N, Inf, Rd, NBT, A | F15, F20 |
| Active HBM source | `TileEntityNukeGadget` | `src/main/java/com/hbm/tileentity/bomb/TileEntityNukeGadget.java` | `TileEntityNukeGadget ← TileEntity` | no HBM body | true | I, N, Inf, Rd, NBT, A | F15, F20 |
| Active HBM source | `TileEntityNukeFleija` | `src/main/java/com/hbm/tileentity/bomb/TileEntityNukeFleija.java` | `TileEntityNukeFleija ← TileEntity` | no HBM body | true | I, N, Inf, Rd, NBT, A | F15, F20 |
| Active HBM source | `TileEntityNukeCustom` | `src/main/java/com/hbm/tileentity/bomb/TileEntityNukeCustom.java` | `TileEntityNukeCustom ← TileEntity` | own (unguarded) | true | I, N, Inf, Rd, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityNukeBoy` | `src/main/java/com/hbm/tileentity/bomb/TileEntityNukeBoy.java` | `TileEntityNukeBoy ← TileEntity` | no HBM body | true | I, N, Inf, Rd, NBT, A | F15, F20 |
| Active HBM source | `TileEntityNukeBalefire` | `src/main/java/com/hbm/tileentity/bomb/TileEntityNukeBalefire.java` | `TileEntityNukeBalefire ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, N, Pkt, Inf, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityLaunchTable` | `src/main/java/com/hbm/tileentity/bomb/TileEntityLaunchTable.java` | `TileEntityLaunchTable ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | R, I, F, E, N, Pkt, M, Ent, Inf, Rd, T, NBT, A | F05, F20, F19 |
| Active HBM source | `TileEntityLaunchPadRusted` | `src/main/java/com/hbm/tileentity/bomb/TileEntityLaunchPadRusted.java` | `TileEntityLaunchPadRusted ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | R, I, F, E, N, Pkt, Ent, Rd, T, NBT, A | F20, F19 |
| Active HBM source | `TileEntityLaunchPadRocket` | `src/main/java/com/hbm/tileentity/bomb/TileEntityLaunchPadRocket.java` | `TileEntityLaunchPadRocket ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, N, Pkt, M, Ent, Inf, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityLaunchPadLarge` | `src/main/java/com/hbm/tileentity/bomb/TileEntityLaunchPadLarge.java` | `TileEntityLaunchPadLarge ← TileEntityLaunchPadBase ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | R, I, F, E, N, Pkt, Ent, Rd, T, NBT, A | F20, F19 |
| Active HBM source | `TileEntityLaunchPad` | `src/main/java/com/hbm/tileentity/bomb/TileEntityLaunchPad.java` | `TileEntityLaunchPad ← TileEntityLaunchPadBase ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | R, I, F, E, N, Pkt, Ent, Rd, T, NBT, A | F20, F19 |
| Active HBM source | `TileEntityLandmine` | `src/main/java/com/hbm/tileentity/bomb/TileEntityLandmine.java` | `TileEntityLandmine ← TileEntity` | own (remote-guard) | true | N, Ent, Rd, NBT | F20 |
| Active HBM source | `TileEntityFireworks` | `src/main/java/com/hbm/tileentity/bomb/TileEntityFireworks.java` | `TileEntityFireworks ← TileEntity` | own (server/client) | true | R, Pkt, T, NBT, A | F19 |
| Active HBM source | `TileEntityCrashedBomb` | `src/main/java/com/hbm/tileentity/bomb/TileEntityCrashedBomb.java` | `TileEntityCrashedBomb ← TileEntity` | no HBM body | true | Inf, Rd | F15, F20 |
| Active HBM source | `TileEntityCompactLauncher` | `src/main/java/com/hbm/tileentity/bomb/TileEntityCompactLauncher.java` | `TileEntityCompactLauncher ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | R, I, F, E, N, Pkt, M, Ent, Inf, Rd, T, NBT, A | F05, F20, F19 |
| Active HBM source | `TileEntityCharge` | `src/main/java/com/hbm/tileentity/bomb/TileEntityCharge.java` | `TileEntityCharge ← TileEntity` | own (server/client) | true | Pkt, T, A | F19 |
| Active HBM source | `TileEntityBrownBarrel` | `src/main/java/com/hbm/tileentity/bomb/TileEntityBrownBarrel.java` | `TileEntityBrownBarrel ← TileEntity` | no HBM body | true | Rd | F15, F20 |
| Active HBM source | `TileEntityBombMulti` | `src/main/java/com/hbm/tileentity/bomb/TileEntityBombMulti.java` | `TileEntityBombMulti ← TileEntity` | no HBM body | true | I, N, Inf, Rd, NBT, A | F15, F20 |
| Active HBM source | `TileEntityAntimatter` | `src/main/java/com/hbm/tileentity/bomb/TileEntityAntimatter.java` | `TileEntityAntimatter ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, N, Pkt, Inf, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityLoot` | `src/main/java/com/hbm/blocks/generic/BlockLoot.java` | `TileEntityLoot ← TileEntity` | no HBM body | false | NBT, A | — |
| Active HBM source | `TileEntityEmitter` | `src/main/java/com/hbm/blocks/generic/BlockEmitter.java` | `TileEntityEmitter ← TileEntity` | own (server/client) | true | N, Pkt, Inf, Rd, T, NBT, A | F20, F19 |
| Active HBM source | `TileEntitySlag` | `src/main/java/com/hbm/blocks/generic/BlockDynamicSlag.java` | `TileEntitySlag ← TileEntity` | no HBM body | false | NBT | — |
| Active HBM source | `TileEntityPlushie` | `src/main/java/com/hbm/blocks/generic/BlockPlushie.java` | `TileEntityPlushie ← TileEntity` | own (unguarded) | true | NBT | — |
| Active HBM source | `TileEntityPedestal` | `src/main/java/com/hbm/blocks/generic/BlockPedestal.java` | `TileEntityPedestal ← TileEntity` | no HBM body | false | NBT | — |
| Active HBM source | `TileEntityRandomOre` | `src/main/java/com/hbm/blocks/generic/BlockMotherOfAllOres.java` | `TileEntityRandomOre ← TileEntity` | no HBM body | false | NBT | — |
| Active HBM source | `TileEntitySnowglobe` | `src/main/java/com/hbm/blocks/generic/BlockSnowglobe.java` | `TileEntitySnowglobe ← TileEntity` | no HBM body | false | NBT | — |
| Active HBM source | `TileEntityPartEmitter` | `src/main/java/com/hbm/blocks/generic/PartEmitter.java` | `TileEntityPartEmitter ← TileEntity` | own (server/client) | true | Pkt, NBT, A | F19 |
| Active HBM source | `TileEntitySubstation` | `src/main/java/com/hbm/tileentity/network/TileEntitySubstation.java` | `TileEntitySubstation ← TileEntityPylonBase ← TileEntityCableBaseNT ← TileEntity` | inherited:TileEntityCableBaseNT (unguarded) | false | E, N, G, M, Inf, Rd, NBT, A | F20 |
| Active HBM source | `TileEntityRadioTorchSender` | `src/main/java/com/hbm/tileentity/network/TileEntityRadioTorchSender.java` | `TileEntityRadioTorchSender ← TileEntityRadioTorchBase ← TileEntity` | own (server/client) | true | R, N, G, Pkt, NBT, A | F09 |
| Active HBM source | `TileEntityRadioTorchReceiver` | `src/main/java/com/hbm/tileentity/network/TileEntityRadioTorchReceiver.java` | `TileEntityRadioTorchReceiver ← TileEntityRadioTorchBase ← TileEntity` | own (server/client) | true | G, Pkt, T, NBT, A | F09 |
| Active HBM source | `TileEntityRadioTorchLogic` | `src/main/java/com/hbm/tileentity/network/TileEntityRadioTorchLogic.java` | `TileEntityRadioTorchLogic ← TileEntity` | own (server/client) | true | G, Pkt, T, NBT, A | F09 |
| Active HBM source | `TileEntityRadioTorchCounter` | `src/main/java/com/hbm/tileentity/network/TileEntityRadioTorchCounter.java` | `TileEntityRadioTorchCounter ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, N, G, Pkt, T, NBT, A | F09, F17, F24, F19 |
| Active HBM source | `TileEntityRadioTorchBase` | `src/main/java/com/hbm/tileentity/network/TileEntityRadioTorchBase.java` | `TileEntityRadioTorchBase ← TileEntity` | own (server/client) | true | Pkt, NBT, A | F09 |
| Active HBM source | `TileEntityRadioTelex` | `src/main/java/com/hbm/tileentity/network/TileEntityRadioTelex.java` | `TileEntityRadioTelex ← TileEntity` | own (server/client) | true | G, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityPylonMedium` | `src/main/java/com/hbm/tileentity/network/TileEntityPylonMedium.java` | `TileEntityPylonMedium ← TileEntityPylonBase ← TileEntityCableBaseNT ← TileEntity` | inherited:TileEntityCableBaseNT (unguarded) | false | E, N, G, Inf, Rd, NBT, A | F20 |
| Active HBM source | `TileEntityPylonLarge` | `src/main/java/com/hbm/tileentity/network/TileEntityPylonLarge.java` | `TileEntityPylonLarge ← TileEntityPylonBase ← TileEntityCableBaseNT ← TileEntity` | inherited:TileEntityCableBaseNT (unguarded) | false | E, N, G, M, Inf, Rd, NBT, A | F20 |
| Active HBM source | `TileEntityPylon` | `src/main/java/com/hbm/tileentity/network/TileEntityPylon.java` | `TileEntityPylon ← TileEntityPylonBase ← TileEntityCableBaseNT ← TileEntity` | inherited:TileEntityCableBaseNT (unguarded) | false | E, N, G, Inf, Rd, NBT, A | F20 |
| Active HBM source | `TileEntityPipeExhaust` | `src/main/java/com/hbm/tileentity/network/TileEntityPipeExhaust.java` | `TileEntityPipeExhaust ← TileEntity` | own (unguarded) | false | E, G, A | — |
| Active HBM source | `TileEntityPipeBaseNT` | `src/main/java/com/hbm/tileentity/network/TileEntityPipeBaseNT.java` | `TileEntityPipeBaseNT ← TileEntity` | own (unguarded) | false | F, E, N, G, NBT, A | — |
| Active HBM source | `TileEntityFluidValve` | `src/main/java/com/hbm/tileentity/network/TileEntityFluidValve.java` | `TileEntityFluidValve ← TileEntityPipeBaseNT ← TileEntity` | inherited:TileEntityPipeBaseNT (unguarded) | false | F, E, N, G, NBT, A | — |
| Active HBM source | `TileEntityDroneWaypointRequest` | `src/main/java/com/hbm/tileentity/network/TileEntityDroneWaypointRequest.java` | `TileEntityDroneWaypointRequest ← TileEntityRequestNetwork ← TileEntity` | inherited:TileEntityRequestNetwork (server/client) | true | R, G, T, NBT, A | F08, F16 |
| Active HBM source | `TileEntityDroneWaypoint` | `src/main/java/com/hbm/tileentity/network/TileEntityDroneWaypoint.java` | `TileEntityDroneWaypoint ← TileEntity` | own (server/client) | true | Pkt, Ent, T, NBT, A | F08, F16, F19 |
| Active HBM source | `TileEntityDroneRequester` | `src/main/java/com/hbm/tileentity/network/TileEntityDroneRequester.java` | `TileEntityDroneRequester ← TileEntityRequestNetworkContainer ← TileEntityRequestNetwork ← TileEntity` | own (server/client) | true | R, I, N, G, Pkt, T, NBT, A | F09, F08, F16 |
| Active HBM source | `TileEntityDroneProvider` | `src/main/java/com/hbm/tileentity/network/TileEntityDroneProvider.java` | `TileEntityDroneProvider ← TileEntityRequestNetworkContainer ← TileEntityRequestNetwork ← TileEntity` | inherited:TileEntityRequestNetwork (server/client) | true | R, I, N, G, Pkt, T, NBT, A | F08, F16 |
| Active HBM source | `TileEntityDroneDock` | `src/main/java/com/hbm/tileentity/network/TileEntityDroneDock.java` | `TileEntityDroneDock ← TileEntityRequestNetworkContainer ← TileEntityRequestNetwork ← TileEntity` | own (server/client) | true | R, I, Rc, N, G, Pkt, T, NBT, A | F08, F16, F19 |
| Active HBM source | `TileEntityDroneCrate` | `src/main/java/com/hbm/tileentity/network/TileEntityDroneCrate.java` | `TileEntityDroneCrate ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, N, Pkt, Ent, T, NBT, A | F19 |
| Active HBM source | `TileEntityCraneUnboxer` | `src/main/java/com/hbm/tileentity/network/TileEntityCraneUnboxer.java` | `TileEntityCraneUnboxer ← TileEntityCraneBase ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | R, I, F, E, N, Pkt, T, Mut, NBT, A | — |
| Active HBM source | `TileEntityCraneSplitter` | `src/main/java/com/hbm/tileentity/network/TileEntityCraneSplitter.java` | `TileEntityCraneSplitter ← TileEntity` | no HBM body | true | NBT | F15 |
| Active HBM source | `TileEntityCraneRouter` | `src/main/java/com/hbm/tileentity/network/TileEntityCraneRouter.java` | `TileEntityCraneRouter ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, N, Pkt, T, NBT, A | — |
| Active HBM source | `TileEntityCraneInserter` | `src/main/java/com/hbm/tileentity/network/TileEntityCraneInserter.java` | `TileEntityCraneInserter ← TileEntityCraneBase ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, N, Pkt, T, Mut, NBT, A | — |
| Active HBM source | `TileEntityCraneGrabber` | `src/main/java/com/hbm/tileentity/network/TileEntityCraneGrabber.java` | `TileEntityCraneGrabber ← TileEntityCraneBase ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | R, I, F, E, N, Pkt, Ent, T, Mut, NBT, A | — |
| Active HBM source | `TileEntityCraneExtractor` | `src/main/java/com/hbm/tileentity/network/TileEntityCraneExtractor.java` | `TileEntityCraneExtractor ← TileEntityCraneBase ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | R, I, F, E, N, Pkt, T, Mut, NBT, A | — |
| Active HBM source | `TileEntityCraneBoxer` | `src/main/java/com/hbm/tileentity/network/TileEntityCraneBoxer.java` | `TileEntityCraneBoxer ← TileEntityCraneBase ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | R, I, F, E, N, Pkt, T, Mut, NBT, A | F19 |
| Active HBM source | `TileEntityConverterRfHe` | `src/main/java/com/hbm/tileentity/network/TileEntityConverterRfHe.java` | `TileEntityConverterRfHe ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | E, T, NBT | — |
| Active HBM source | `TileEntityConverterHeRf` | `src/main/java/com/hbm/tileentity/network/TileEntityConverterHeRf.java` | `TileEntityConverterHeRf ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | E, N, T, NBT | — |
| Active HBM source | `TileEntityConnector` | `src/main/java/com/hbm/tileentity/network/TileEntityConnector.java` | `TileEntityConnector ← TileEntityPylonBase ← TileEntityCableBaseNT ← TileEntity` | inherited:TileEntityCableBaseNT (unguarded) | false | E, N, G, Inf, Rd, NBT, A | F20 |
| Active HBM source | `TileEntityCableSwitch` | `src/main/java/com/hbm/tileentity/network/TileEntityCableSwitch.java` | `TileEntityCableSwitch ← TileEntityCableBaseNT ← TileEntity` | inherited:TileEntityCableBaseNT (unguarded) | false | E, G | — |
| Active HBM source | `TileEntityCableBaseNT` | `src/main/java/com/hbm/tileentity/network/TileEntityCableBaseNT.java` | `TileEntityCableBaseNT ← TileEntity` | own (unguarded) | false | E, G | — |
| Active HBM source | `TileEntityZirnoxDestroyed` | `src/main/java/com/hbm/tileentity/machine/TileEntityZirnoxDestroyed.java` | `TileEntityZirnoxDestroyed ← TileEntity` | own (server/client) | true | N, Ent, Rd, NBT | F20 |
| Active HBM source | `TileEntityXenonThruster` | `src/main/java/com/hbm/tileentity/machine/TileEntityXenonThruster.java` | `TileEntityXenonThruster ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, N, Pkt, M, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityWatzStruct` | `src/main/java/com/hbm/tileentity/machine/TileEntityWatzStruct.java` | `TileEntityWatzStruct ← TileEntity` | own (remote-guard) | true | N, M, Rd, T, Mut | F20 |
| Active HBM source | `TileEntityWatz` | `src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java` | `TileEntityWatz ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | R, I, F, E, H, N, Pkt, Ent, Rd, T, Mut, NBT, A | F15, F20 |
| Active HBM source | `TileEntityWasteDrum` | `src/main/java/com/hbm/tileentity/machine/TileEntityWasteDrum.java` | `TileEntityWasteDrum ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, E, Rc, N, T, NBT, A | F15 |
| Active HBM source | `TileEntityVaultDoor` | `src/main/java/com/hbm/tileentity/machine/TileEntityVaultDoor.java` | `TileEntityVaultDoor ← TileEntityLockableBase` | own (server/client) | true | R, N, Pkt, M, Inf, Rd, T, Mut, NBT, A | F04, F11, F13, F14, F21, F20, F19 |
| Active HBM source | `TileEntityTransporterRocket` | `src/main/java/com/hbm/tileentity/machine/TileEntityTransporterRocket.java` | `TileEntityTransporterRocket ← TileEntityTransporterBase ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, N, Pkt, Inf, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityTowerSmall` | `src/main/java/com/hbm/tileentity/machine/TileEntityTowerSmall.java` | `TileEntityTowerSmall ← TileEntityCondenser ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | F, E, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityTowerLarge` | `src/main/java/com/hbm/tileentity/machine/TileEntityTowerLarge.java` | `TileEntityTowerLarge ← TileEntityCondenser ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | F, E, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityTesla` | `src/main/java/com/hbm/tileentity/machine/TileEntityTesla.java` | `TileEntityTesla ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, Pkt, Ent, Inf, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityStorageDrum` | `src/main/java/com/hbm/tileentity/machine/TileEntityStorageDrum.java` | `TileEntityStorageDrum ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, Pkt, Ent, T, NBT, A | F15 |
| Active HBM source | `TileEntityStirling` | `src/main/java/com/hbm/tileentity/machine/TileEntityStirling.java` | `TileEntityStirling ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | E, H, N, Pkt, M, Rd, T, NBT | F20 |
| Active HBM source | `TileEntitySteamEngine` | `src/main/java/com/hbm/tileentity/machine/TileEntitySteamEngine.java` | `TileEntitySteamEngine ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | F, E, N, Pkt, M, Inf, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityStationPropulsionCreative` | `src/main/java/com/hbm/tileentity/machine/TileEntityStationPropulsionCreative.java` | `TileEntityStationPropulsionCreative ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | E, N, NBT | F15 |
| Active HBM source | `TileEntitySoyuzStruct` | `src/main/java/com/hbm/tileentity/machine/TileEntitySoyuzStruct.java` | `TileEntitySoyuzStruct ← TileEntity` | own (remote-guard) | true | N, Inf, Rd, T, Mut | F20 |
| Active HBM source | `TileEntitySoyuzLauncher` | `src/main/java/com/hbm/tileentity/machine/TileEntitySoyuzLauncher.java` | `TileEntitySoyuzLauncher ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, N, Pkt, M, Ent, Inf, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntitySolarMirror` | `src/main/java/com/hbm/tileentity/machine/TileEntitySolarMirror.java` | `TileEntitySolarMirror ← TileEntityTickingBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | F, E, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntitySolarBoiler` | `src/main/java/com/hbm/tileentity/machine/TileEntitySolarBoiler.java` | `TileEntitySolarBoiler ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | F, E, H, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntitySILEX` | `src/main/java/com/hbm/tileentity/machine/TileEntitySILEX.java` | `TileEntitySILEX ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntitySawmill` | `src/main/java/com/hbm/tileentity/machine/TileEntitySawmill.java` | `TileEntitySawmill ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityRtgFurnace` | `src/main/java/com/hbm/tileentity/machine/TileEntityRtgFurnace.java` | `TileEntityRtgFurnace ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, E, Rc, N, T, NBT, A | F15 |
| Active HBM source | `TileEntityRefueler` | `src/main/java/com/hbm/tileentity/machine/TileEntityRefueler.java` | `TileEntityRefueler ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | F, E, N, Ent, T, NBT, A | — |
| Active HBM source | `TileEntityReactorZirnox` | `src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java` | `TileEntityReactorZirnox ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, H, N, Pkt, M, Ent, Rd, T, Mut, NBT, A | F15, F20 |
| Active HBM source | `TileEntityReactorResearch` | `src/main/java/com/hbm/tileentity/machine/TileEntityReactorResearch.java` | `TileEntityReactorResearch ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, H, N, Pkt, Ent, Inf, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityReactorControl` | `src/main/java/com/hbm/tileentity/machine/TileEntityReactorControl.java` | `TileEntityReactorControl ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, H, N, Pkt, T, NBT, A | F15 |
| Active HBM source | `TileEntityRadioRec` | `src/main/java/com/hbm/tileentity/machine/TileEntityRadioRec.java` | `TileEntityRadioRec ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | E, G, Pkt, T, NBT | F15 |
| Active HBM source | `TileEntityRadiobox` | `src/main/java/com/hbm/tileentity/machine/TileEntityRadiobox.java` | `TileEntityRadiobox ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | E, N, Ent, Rd, T, NBT | F15, F20 |
| Active HBM source | `TileEntityRadiator` | `src/main/java/com/hbm/tileentity/machine/TileEntityRadiator.java` | `TileEntityRadiator ← TileEntityCondenser ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityCondenser (unguarded) | true | F, E, N, Pkt, M, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityPWRController` | `src/main/java/com/hbm/tileentity/machine/TileEntityPWRController.java` | `TileEntityPWRController ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, N, Pkt, T, Mut, NBT, A | — |
| Active HBM source | `TileEntityPlasmaStruct` | `src/main/java/com/hbm/tileentity/machine/TileEntityPlasmaStruct.java` | `TileEntityPlasmaStruct ← TileEntity` | own (remote-guard) | true | N, M, Inf, Rd, T, Mut, A | F20, F19 |
| Active HBM source | `TileEntityOrbitalStationComputer` | `src/main/java/com/hbm/tileentity/machine/TileEntityOrbitalStationComputer.java` | `TileEntityOrbitalStationComputer ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, Pkt, T, NBT, A | F15 |
| Active HBM source | `TileEntityOrbitalStation` | `src/main/java/com/hbm/tileentity/machine/TileEntityOrbitalStation.java` | `TileEntityOrbitalStation ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, N, Pkt, M, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityNukeFurnace` | `src/main/java/com/hbm/tileentity/machine/TileEntityNukeFurnace.java` | `TileEntityNukeFurnace ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, E, Rc, N, T, NBT, A | F15 |
| Active HBM source | `TileEntityMultiblock` | `src/main/java/com/hbm/tileentity/machine/TileEntityMultiblock.java` | `TileEntityMultiblock ← TileEntity` | own (server/client) | true | N, M, Inf, Rd, Mut | F20 |
| Active HBM source | `TileEntityMoltenSaltReactorPort` | `src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactorPort.java` | `TileEntityMoltenSaltReactorPort ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | F, E, N, T, NBT, A | F15 |
| Active HBM source | `TileEntityMoltenSaltReactor` | `src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactor.java` | `TileEntityMoltenSaltReactor ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, Pkt, T, NBT, A | F15 |
| Active HBM source | `TileEntityMicrowave` | `src/main/java/com/hbm/tileentity/machine/TileEntityMicrowave.java` | `TileEntityMicrowave ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, Inf, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineWoodBurner` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineWoodBurner.java` | `TileEntityMachineWoodBurner ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineVacuumCircuit` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineVacuumCircuit.java` | `TileEntityMachineVacuumCircuit ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineTurbofan` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbofan.java` | `TileEntityMachineTurbofan ← TileEntityMachinePolluting ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, N, Pkt, M, Ent, Inf, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityMachineTurbineGas` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java` | `TileEntityMachineTurbineGas ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, H, N, Pkt, M, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineTurbine` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbine.java` | `TileEntityMachineTurbine ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, M, T, Mut, NBT, A | F15 |
| Active HBM source | `TileEntityMachineTransformer` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineTransformer.java` | `TileEntityMachineTransformer ← TileEntity` | no HBM body | true | — | F15 |
| Active HBM source | `TileEntityMachineTeleporter` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineTeleporter.java` | `TileEntityMachineTeleporter ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | F, E, N, Pkt, Ent, T, NBT, A | — |
| Active HBM source | `TileEntityMachineStrandCaster` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineStrandCaster.java` | `TileEntityMachineStrandCaster ← TileEntityFoundryCastingBase ← TileEntityFoundryBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, Pkt, M, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineStardar` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineStardar.java` | `TileEntityMachineStardar ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, N, Pkt, Inf, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineSPP` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineSPP.java` | `TileEntityMachineSPP ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | E, N, M, T, NBT | F15 |
| Active HBM source | `TileEntityMachineSolderingStation` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineSolderingStation.java` | `TileEntityMachineSolderingStation ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineSolarPanel` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineSolarPanel.java` | `TileEntityMachineSolarPanel ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | E, Inf, T, NBT | F15, F20 |
| Active HBM source | `TileEntityMachineSiren` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineSiren.java` | `TileEntityMachineSiren ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | R, I, E, N, Pkt, T, NBT, A | F15 |
| Active HBM source | `TileEntityMachineShredder` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineShredder.java` | `TileEntityMachineShredder ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, E, Rc, N, Pkt, T, Mut, NBT, A | F15 |
| Active HBM source | `TileEntityMachineSchrabidiumTransmutator` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineSchrabidiumTransmutator.java` | `TileEntityMachineSchrabidiumTransmutator ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, Rc, N, Pkt, T, NBT, A | — |
| Active HBM source | `TileEntityMachineSatLinker` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineSatLinker.java` | `TileEntityMachineSatLinker ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, E, N, T, NBT, A | F15 |
| Active HBM source | `TileEntityMachineSatDock` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineSatDock.java` | `TileEntityMachineSatDock ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, E, N, Ent, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineRTG` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineRTG.java` | `TileEntityMachineRTG ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, E, H, N, Pkt, T, NBT, A | F15 |
| Active HBM source | `TileEntityMachineRotaryFurnace` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineRotaryFurnace.java` | `TileEntityMachineRotaryFurnace ← TileEntityMachinePolluting ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, Rc, N, Pkt, M, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineRocketAssembly` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineRocketAssembly.java` | `TileEntityMachineRocketAssembly ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, Pkt, M, Inf, Rd, T, Mut, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineReactorBreeding` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineReactorBreeding.java` | `TileEntityMachineReactorBreeding ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineRadiolysis` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadiolysis.java` | `TileEntityMachineRadiolysis ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, H, Rc, N, Pkt, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineRadGen` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadGen.java` | `TileEntityMachineRadGen ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, Pkt, M, Inf, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineRadarScreen` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadarScreen.java` | `TileEntityMachineRadarScreen ← TileEntity` | own (server/client) | true | Pkt, Rd, A | F18, F20 |
| Active HBM source | `TileEntityMachineRadarNT` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadarNT.java` | `TileEntityMachineRadarNT ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, N, Pkt, Ent, Rd, T, NBT, A | F20, F19 |
| Active HBM source | `TileEntityMachineRadarLarge` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadarLarge.java` | `TileEntityMachineRadarLarge ← TileEntityMachineRadarNT ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityMachineRadarNT (remote-guard) | true | I, F, E, N, Pkt, Ent, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachinePumpSteam` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpSteam.java` | `TileEntityMachinePumpSteam ← TileEntityMachinePumpBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | F, E, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachinePumpElectric` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpElectric.java` | `TileEntityMachinePumpElectric ← TileEntityMachinePumpBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | F, E, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachinePress` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachinePress.java` | `TileEntityMachinePress ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachinePlasmaHeater` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java` | `TileEntityMachinePlasmaHeater ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, Pkt, M, Inf, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineOreSlopper` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineOreSlopper.java` | `TileEntityMachineOreSlopper ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, N, Pkt, Ent, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineMixer` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java` | `TileEntityMachineMixer ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineMissileAssembly` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineMissileAssembly.java` | `TileEntityMachineMissileAssembly ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, E, N, Pkt, M, Inf, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineMiniRTG` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineMiniRTG.java` | `TileEntityMachineMiniRTG ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | E, T, NBT | F15 |
| Active HBM source | `TileEntityMachineMiningLaser` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineMiningLaser.java` | `TileEntityMachineMiningLaser ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, Ent, Inf, Rd, T, Mut, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineMilkReformer` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineMilkReformer.java` | `TileEntityMachineMilkReformer ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, Pkt, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineLPW2` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineLPW2.java` | `TileEntityMachineLPW2 ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, N, Pkt, M, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineLargeTurbine` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineLargeTurbine.java` | `TileEntityMachineLargeTurbine ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, N, Pkt, M, Inf, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityMachineKeyForge` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineKeyForge.java` | `TileEntityMachineKeyForge ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, E, N, T, NBT, A | F15 |
| Active HBM source | `TileEntityMachineIGenerator` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineIGenerator.java` | `TileEntityMachineIGenerator ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, Pkt, M, Inf, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineHTRF4` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTRF4.java` | `TileEntityMachineHTRF4 ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, N, Pkt, M, Inf, Rd, T, NBT, A | F20, F19 |
| Active HBM source | `TileEntityMachineHTR3` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTR3.java` | `TileEntityMachineHTR3 ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, N, Pkt, M, Rd, T, NBT, A | F20, F19 |
| Active HBM source | `TileEntityMachineHephaestus` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineHephaestus.java` | `TileEntityMachineHephaestus ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | F, E, H, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineGasDock` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasDock.java` | `TileEntityMachineGasDock ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineGasCent` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java` | `TileEntityMachineGasCent ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, M, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineFunnel` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineFunnel.java` | `TileEntityMachineFunnel ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, T, NBT, A | F15 |
| Active HBM source | `TileEntityMachineExposureChamber` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineExposureChamber.java` | `TileEntityMachineExposureChamber ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineExcavator` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineExcavator.java` | `TileEntityMachineExcavator ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, Rc, N, Pkt, M, Ent, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityMachineEPress` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineEPress.java` | `TileEntityMachineEPress ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineElectricFurnace` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineElectricFurnace.java` | `TileEntityMachineElectricFurnace ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, T, NBT, A | F15 |
| Active HBM source | `TileEntityMachineDriveProcessor` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineDriveProcessor.java` | `TileEntityMachineDriveProcessor ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, Pkt, T, NBT, A | F15 |
| Active HBM source | `TileEntityMachineDrain` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineDrain.java` | `TileEntityMachineDrain ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | F, E, N, Pkt, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityMachineDischarger` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineDischarger.java` | `TileEntityMachineDischarger ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, H, Rc, N, Pkt, T, NBT, A | — |
| Active HBM source | `TileEntityMachineDiesel` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java` | `TileEntityMachineDiesel ← TileEntityMachinePolluting ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, Pkt, M, T, NBT, A | F15 |
| Active HBM source | `TileEntityMachineDetector` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineDetector.java` | `TileEntityMachineDetector ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | E, N, T, Mut, NBT | F15 |
| Active HBM source | `TileEntityMachineCyclotron` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java` | `TileEntityMachineCyclotron ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineCrystallizer` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineCrystallizer.java` | `TileEntityMachineCrystallizer ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, Rc, N, Pkt, Ent, Inf, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineCryoDistill` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineCryoDistill.java` | `TileEntityMachineCryoDistill ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, M, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineCompressor` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java` | `TileEntityMachineCompressor ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, Rc, N, Pkt, M, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineCombustionEngine` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineCombustionEngine.java` | `TileEntityMachineCombustionEngine ← TileEntityMachinePolluting ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, N, Pkt, M, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineChemplant` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java` | `TileEntityMachineChemplant ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, Rc, N, Pkt, M, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineChemfac` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemfac.java` | `TileEntityMachineChemfac ← TileEntityMachineChemplantBase ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, Rc, N, Pkt, M, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineCentrifuge` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineCentrifuge.java` | `TileEntityMachineCentrifuge ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineAutosaw` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineAutosaw.java` | `TileEntityMachineAutosaw ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | F, E, N, Pkt, Ent, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityMachineAutocrafter` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineAutocrafter.java` | `TileEntityMachineAutocrafter ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, T, NBT, A | F15 |
| Active HBM source | `TileEntityMachineAssemfac` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemfac.java` | `TileEntityMachineAssemfac ← TileEntityMachineAssemblerBase ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, Rc, N, Pkt, M, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineAssembler` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssembler.java` | `TileEntityMachineAssembler ← TileEntityMachineAssemblerBase ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, Rc, N, Pkt, M, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityMachineArcWelder` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcWelder.java` | `TileEntityMachineArcWelder ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineArcFurnaceLarge` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnaceLarge.java` | `TileEntityMachineArcFurnaceLarge ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineArcFurnace` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnace.java` | `TileEntityMachineArcFurnace ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, E, Rc, N, Pkt, T, Mut, NBT, A | F15 |
| Active HBM source | `TileEntityMachineAmgen` | `src/main/java/com/hbm/tileentity/machine/TileEntityMachineAmgen.java` | `TileEntityMachineAmgen ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | E, N, T, Mut, NBT | F15 |
| Active HBM source | `TileEntityITERStruct` | `src/main/java/com/hbm/tileentity/machine/TileEntityITERStruct.java` | `TileEntityITERStruct ← TileEntity` | own (remote-guard) | true | N, M, Inf, Rd, T, Mut, A | F20 |
| Active HBM source | `TileEntityITER` | `src/main/java/com/hbm/tileentity/machine/TileEntityITER.java` | `TileEntityITER ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityICFStruct` | `src/main/java/com/hbm/tileentity/machine/TileEntityICFStruct.java` | `TileEntityICFStruct ← TileEntity` | own (remote-guard) | true | N, M, Inf, Rd, T, Mut | F20 |
| Active HBM source | `TileEntityICFPress` | `src/main/java/com/hbm/tileentity/machine/TileEntityICFPress.java` | `TileEntityICFPress ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, Pkt, T, NBT, A | F15 |
| Active HBM source | `TileEntityICFController` | `src/main/java/com/hbm/tileentity/machine/TileEntityICFController.java` | `TileEntityICFController ← TileEntityTickingBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | F, E, N, Pkt, Ent, T, Mut, NBT, A | — |
| Active HBM source | `TileEntityICF` | `src/main/java/com/hbm/tileentity/machine/TileEntityICF.java` | `TileEntityICF ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, H, N, Pkt, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityHeaterOven` | `src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java` | `TileEntityHeaterOven ← TileEntityFireboxBase ← TileEntityMachinePolluting ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityFireboxBase (remote-guard) | true | I, F, E, H, N, Pkt, M, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityHeaterOilburner` | `src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOilburner.java` | `TileEntityHeaterOilburner ← TileEntityMachinePolluting ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, H, N, Pkt, M, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityHeaterHeatex` | `src/main/java/com/hbm/tileentity/machine/TileEntityHeaterHeatex.java` | `TileEntityHeaterHeatex ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, H, N, Pkt, M, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityHeaterFirebox` | `src/main/java/com/hbm/tileentity/machine/TileEntityHeaterFirebox.java` | `TileEntityHeaterFirebox ← TileEntityFireboxBase ← TileEntityMachinePolluting ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityFireboxBase (remote-guard) | true | I, F, E, H, N, Pkt, M, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityHeaterElectric` | `src/main/java/com/hbm/tileentity/machine/TileEntityHeaterElectric.java` | `TileEntityHeaterElectric ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | E, H, N, Pkt, M, Rd, T, NBT | F20 |
| Active HBM source | `TileEntityHeatBoilerIndustrial` | `src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerIndustrial.java` | `TileEntityHeatBoilerIndustrial ← TileEntityHeatBoilerBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | F, E, H, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityHeatBoiler` | `src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoiler.java` | `TileEntityHeatBoiler ← TileEntityHeatBoilerBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityHeatBoilerBase (unguarded) | true | F, E, H, N, Pkt, M, Rd, T, Mut, NBT, A | F15, F20 |
| Active HBM source | `TileEntityHatch` | `src/main/java/com/hbm/tileentity/machine/TileEntityHatch.java` | `TileEntityHatch ← TileEntity` | own (server/client) | true | N, Mut, NBT | — |
| Active HBM source | `TileEntityHadronPower` | `src/main/java/com/hbm/tileentity/machine/TileEntityHadronPower.java` | `TileEntityHadronPower ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | E, N, Pkt, NBT, A | F15 |
| Active HBM source | `TileEntityHadronDiode` | `src/main/java/com/hbm/tileentity/machine/TileEntityHadronDiode.java` | `TileEntityHadronDiode ← TileEntityTickingBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | F, E, Pkt, T, NBT, A | — |
| Active HBM source | `TileEntityHadron` | `src/main/java/com/hbm/tileentity/machine/TileEntityHadron.java` | `TileEntityHadron ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, Rc, N, Pkt, T, NBT, A | — |
| Active HBM source | `TileEntityGeiger` | `src/main/java/com/hbm/tileentity/machine/TileEntityGeiger.java` | `TileEntityGeiger ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | E, T, NBT, A | F15 |
| Active HBM source | `TileEntityFurnaceSteel` | `src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceSteel.java` | `TileEntityFurnaceSteel ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, H, Rc, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityFurnaceIron` | `src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceIron.java` | `TileEntityFurnaceIron ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityFurnaceCombination` | `src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceCombination.java` | `TileEntityFurnaceCombination ← TileEntityMachinePolluting ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityFurnaceBrick` | `src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceBrick.java` | `TileEntityFurnaceBrick ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, T, NBT, A | F15 |
| Active HBM source | `TileEntityFoundryTank` | `src/main/java/com/hbm/tileentity/machine/TileEntityFoundryTank.java` | `TileEntityFoundryTank ← TileEntityFoundryBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | E, N, T, NBT, A | — |
| Active HBM source | `TileEntityFoundrySlagtap` | `src/main/java/com/hbm/tileentity/machine/TileEntityFoundrySlagtap.java` | `TileEntityFoundrySlagtap ← TileEntityFoundryOutlet ← TileEntityFoundryBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityFoundryOutlet (remote-guard) | true | R, E, N, Pkt, Mut, NBT, A | — |
| Active HBM source | `TileEntityFoundryOutlet` | `src/main/java/com/hbm/tileentity/machine/TileEntityFoundryOutlet.java` | `TileEntityFoundryOutlet ← TileEntityFoundryBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | R, E, Pkt, NBT, A | — |
| Active HBM source | `TileEntityFoundryMold` | `src/main/java/com/hbm/tileentity/machine/TileEntityFoundryMold.java` | `TileEntityFoundryMold ← TileEntityFoundryCastingBase ← TileEntityFoundryBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, E, T, NBT, A | — |
| Active HBM source | `TileEntityFoundryChannel` | `src/main/java/com/hbm/tileentity/machine/TileEntityFoundryChannel.java` | `TileEntityFoundryChannel ← TileEntityFoundryBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | E, N, T, NBT, A | — |
| Active HBM source | `TileEntityFoundryBasin` | `src/main/java/com/hbm/tileentity/machine/TileEntityFoundryBasin.java` | `TileEntityFoundryBasin ← TileEntityFoundryCastingBase ← TileEntityFoundryBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, E, T, NBT, A | — |
| Active HBM source | `TileEntityForceField` | `src/main/java/com/hbm/tileentity/machine/TileEntityForceField.java` | `TileEntityForceField ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, E, N, Pkt, Ent, Inf, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityFF` | `src/main/java/com/hbm/tileentity/machine/TileEntityFF.java` | `TileEntityFF ← TileEntity` | own (unguarded) | true | Ent, Inf, Rd, A | F20 |
| Active HBM source | `TileEntityFEL` | `src/main/java/com/hbm/tileentity/machine/TileEntityFEL.java` | `TileEntityFEL ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, N, Pkt, M, Ent, Inf, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityElectrolyser` | `src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java` | `TileEntityElectrolyser ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, M, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityDummy` | `src/main/java/com/hbm/tileentity/machine/TileEntityDummy.java` | `TileEntityDummy ← TileEntity` | own (server/client) | true | N, M, Mut, NBT | F02, F12 |
| Active HBM source | `TileEntityDiFurnaceRTG` | `src/main/java/com/hbm/tileentity/machine/TileEntityDiFurnaceRTG.java` | `TileEntityDiFurnaceRTG ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, T, NBT, A | F15 |
| Active HBM source | `TileEntityDiFurnace` | `src/main/java/com/hbm/tileentity/machine/TileEntityDiFurnace.java` | `TileEntityDiFurnace ← TileEntityMachinePolluting ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, M, T, NBT, A | F15 |
| Active HBM source | `TileEntityDeuteriumTower` | `src/main/java/com/hbm/tileentity/machine/TileEntityDeuteriumTower.java` | `TileEntityDeuteriumTower ← TileEntityDeuteriumExtractor ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityDeuteriumExtractor (unguarded) | true | I, F, E, N, Pkt, M, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityDeuteriumExtractor` | `src/main/java/com/hbm/tileentity/machine/TileEntityDeuteriumExtractor.java` | `TileEntityDeuteriumExtractor ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, Pkt, T, NBT, A | F15 |
| Active HBM source | `TileEntityDemonLamp` | `src/main/java/com/hbm/tileentity/machine/TileEntityDemonLamp.java` | `TileEntityDemonLamp ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, E, N, Ent, Inf, Rd, T, NBT | F15, F20 |
| Active HBM source | `TileEntityDecon` | `src/main/java/com/hbm/tileentity/machine/TileEntityDecon.java` | `TileEntityDecon ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | E, Ent, T, NBT | — |
| Active HBM source | `TileEntityCyberCrab` | `src/main/java/com/hbm/tileentity/machine/TileEntityCyberCrab.java` | `TileEntityCyberCrab ← TileEntity` | own (server/client) | true | N, Ent, T | — |
| Active HBM source | `TileEntityCustomMachine` | `src/main/java/com/hbm/tileentity/machine/TileEntityCustomMachine.java` | `TileEntityCustomMachine ← TileEntityMachinePolluting ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, H, Rc, N, Pkt, M, T, Mut, NBT, A | F15 |
| Active HBM source | `TileEntityCrucible` | `src/main/java/com/hbm/tileentity/machine/TileEntityCrucible.java` | `TileEntityCrucible ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, H, Rc, N, Pkt, M, Ent, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityCoreStabilizer` | `src/main/java/com/hbm/tileentity/machine/TileEntityCoreStabilizer.java` | `TileEntityCoreStabilizer ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, N, Pkt, Inf, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityCoreReceiver` | `src/main/java/com/hbm/tileentity/machine/TileEntityCoreReceiver.java` | `TileEntityCoreReceiver ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, N, Pkt, Inf, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityCoreInjector` | `src/main/java/com/hbm/tileentity/machine/TileEntityCoreInjector.java` | `TileEntityCoreInjector ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, N, Pkt, Inf, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityCoreEmitter` | `src/main/java/com/hbm/tileentity/machine/TileEntityCoreEmitter.java` | `TileEntityCoreEmitter ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, N, Pkt, Ent, Inf, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityCore` | `src/main/java/com/hbm/tileentity/machine/TileEntityCore.java` | `TileEntityCore ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, H, N, Pkt, Ent, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityConveyorPress` | `src/main/java/com/hbm/tileentity/machine/TileEntityConveyorPress.java` | `TileEntityConveyorPress ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, Rc, N, Pkt, Ent, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityCondenserPowered` | `src/main/java/com/hbm/tileentity/machine/TileEntityCondenserPowered.java` | `TileEntityCondenserPowered ← TileEntityCondenser ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | F, E, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityCondenser` | `src/main/java/com/hbm/tileentity/machine/TileEntityCondenser.java` | `TileEntityCondenser ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | F, E, Pkt, T, NBT, A | F15 |
| Active HBM source | `TileEntityChungus` | `src/main/java/com/hbm/tileentity/machine/TileEntityChungus.java` | `TileEntityChungus ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | R, F, E, N, Pkt, M, Inf, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityChlorineSeal` | `src/main/java/com/hbm/tileentity/machine/TileEntityChlorineSeal.java` | `TileEntityChlorineSeal ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | R, E, N, T, Mut, NBT | F15 |
| Active HBM source | `TileEntityChimneyIndustrial` | `src/main/java/com/hbm/tileentity/machine/TileEntityChimneyIndustrial.java` | `TileEntityChimneyIndustrial ← TileEntityChimneyBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityChimneyBase (remote-guard) | true | F, E, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityChimneyBrick` | `src/main/java/com/hbm/tileentity/machine/TileEntityChimneyBrick.java` | `TileEntityChimneyBrick ← TileEntityChimneyBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityChimneyBase (remote-guard) | true | F, E, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityCharger` | `src/main/java/com/hbm/tileentity/machine/TileEntityCharger.java` | `TileEntityCharger ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | E, N, Pkt, Ent, T, NBT, A | — |
| Active HBM source | `TileEntityBroadcaster` | `src/main/java/com/hbm/tileentity/machine/TileEntityBroadcaster.java` | `TileEntityBroadcaster ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | E, Pkt, Ent, Inf, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityBlastDoor` | `src/main/java/com/hbm/tileentity/machine/TileEntityBlastDoor.java` | `TileEntityBlastDoor ← TileEntityLockableBase` | own (server/client) | true | R, N, Pkt, M, Inf, Rd, T, Mut, NBT, A | F01, F03, F04, F10, F13, F14, F21, F22, F20, F19 |
| Active HBM source | `TileEntityAtmoTower` | `src/main/java/com/hbm/tileentity/machine/TileEntityAtmoTower.java` | `TileEntityAtmoTower ← TileEntityDeuteriumTower ← TileEntityDeuteriumExtractor ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityDeuteriumExtractor (unguarded) | true | I, F, E, N, Pkt, M, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityAtmoExtractor` | `src/main/java/com/hbm/tileentity/machine/TileEntityAtmoExtractor.java` | `TileEntityAtmoExtractor ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, N, Pkt, M, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityAshpit` | `src/main/java/com/hbm/tileentity/machine/TileEntityAshpit.java` | `TileEntityAshpit ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityAlgaeFilm` | `src/main/java/com/hbm/tileentity/machine/TileEntityAlgaeFilm.java` | `TileEntityAlgaeFilm ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, Pkt, T, NBT, A | F15 |
| Active HBM source | `TileEntityAirScrubber` | `src/main/java/com/hbm/tileentity/machine/TileEntityAirScrubber.java` | `TileEntityAirScrubber ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, N, Pkt, T, NBT, A | — |
| Active HBM source | `TileEntityAirPump` | `src/main/java/com/hbm/tileentity/machine/TileEntityAirPump.java` | `TileEntityAirPump ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, N, Pkt, T, NBT, A | — |
| Active HBM source | `TileEntityAbsorber` | `src/main/java/com/hbm/tileentity/machine/TileEntityAbsorber.java` | `TileEntityAbsorber ← TileEntity` | no HBM body | true | N, NBT | F15 |
| Active HBM source | `TileEntitySoyuzCapsule` | `src/main/java/com/hbm/tileentity/machine/storage/TileEntitySoyuzCapsule.java` | `TileEntitySoyuzCapsule ← TileEntityInventoryBase ← TileEntity` | no HBM body | false | I, N, NBT, A | — |
| Active HBM source | `TileEntitySafe` | `src/main/java/com/hbm/tileentity/machine/storage/TileEntitySafe.java` | `TileEntitySafe ← TileEntityCrateBase ← TileEntityLockableBase` | no HBM body | true | I, N, NBT, A | F15 |
| Active HBM source | `TileEntityMassStorage` | `src/main/java/com/hbm/tileentity/machine/storage/TileEntityMassStorage.java` | `TileEntityMassStorage ← TileEntityCrateBase ← TileEntityLockableBase` | own (server/client) | true | I, N, Pkt, NBT, A | — |
| Active HBM source | `TileEntityMachineUF6Tank` | `src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineUF6Tank.java` | `TileEntityMachineUF6Tank ← TileEntity` | no HBM body | true | — | F15 |
| Active HBM source | `TileEntityMachinePuF6Tank` | `src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachinePuF6Tank.java` | `TileEntityMachinePuF6Tank ← TileEntity` | no HBM body | true | — | F15 |
| Active HBM source | `TileEntityMachineOrbus` | `src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineOrbus.java` | `TileEntityMachineOrbus ← TileEntityBarrel ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityBarrel (unguarded) | true | I, F, E, N, Pkt, M, Rd, T, Mut, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineFluidTank` | `src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineFluidTank.java` | `TileEntityMachineFluidTank ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, Pkt, M, Ent, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityMachineFENSU` | `src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineFENSU.java` | `TileEntityMachineFENSU ← TileEntityMachineBattery ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | R, I, F, E, N, G, Pkt, Inf, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineBattery` | `src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineBattery.java` | `TileEntityMachineBattery ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | R, I, F, E, N, G, Pkt, T, NBT, A | F15 |
| Active HBM source | `TileEntityMachineBAT9000` | `src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineBAT9000.java` | `TileEntityMachineBAT9000 ← TileEntityBarrel ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityBarrel (unguarded) | true | I, F, E, N, Pkt, Rd, T, Mut, NBT, A | F15, F20 |
| Active HBM source | `TileEntityFileCabinet` | `src/main/java/com/hbm/tileentity/machine/storage/TileEntityFileCabinet.java` | `TileEntityFileCabinet ← TileEntityCrateBase ← TileEntityLockableBase` | own (server/client) | true | I, N, Pkt, Rd, T, NBT, A | F20, F19 |
| Active HBM source | `TileEntityCrateTungsten` | `src/main/java/com/hbm/tileentity/machine/storage/TileEntityCrateTungsten.java` | `TileEntityCrateTungsten ← TileEntityCrateBase ← TileEntityLockableBase` | own (server/client) | true | I, Rc, N, Mut, NBT, A | — |
| Active HBM source | `TileEntityCrateTemplate` | `src/main/java/com/hbm/tileentity/machine/storage/TileEntityCrateTemplate.java` | `TileEntityCrateTemplate ← TileEntityCrateBase ← TileEntityLockableBase` | no HBM body | true | I, N, NBT, A | F15 |
| Active HBM source | `TileEntityCrateSteel` | `src/main/java/com/hbm/tileentity/machine/storage/TileEntityCrateSteel.java` | `TileEntityCrateSteel ← TileEntityCrateBase ← TileEntityLockableBase` | no HBM body | true | I, N, NBT, A | F15 |
| Active HBM source | `TileEntityCrateIron` | `src/main/java/com/hbm/tileentity/machine/storage/TileEntityCrateIron.java` | `TileEntityCrateIron ← TileEntityCrateBase ← TileEntityLockableBase` | no HBM body | true | I, N, NBT, A | F15 |
| Active HBM source | `TileEntityCrateDesh` | `src/main/java/com/hbm/tileentity/machine/storage/TileEntityCrateDesh.java` | `TileEntityCrateDesh ← TileEntityCrateBase ← TileEntityLockableBase` | no HBM body | true | I, N, NBT, A | F15 |
| Active HBM source | `TileEntityBarrel` | `src/main/java/com/hbm/tileentity/machine/storage/TileEntityBarrel.java` | `TileEntityBarrel ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, N, Pkt, T, Mut, NBT, A | F15 |
| Active HBM source | `TileEntitySpacer` | `src/main/java/com/hbm/tileentity/machine/oil/TileEntitySpacer.java` | `TileEntitySpacer ← TileEntity` | no HBM body | false | Rd | F20 |
| Active HBM source | `TileEntityMachineVacuumDistill` | `src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineVacuumDistill.java` | `TileEntityMachineVacuumDistill ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineSolidifier` | `src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineSolidifier.java` | `TileEntityMachineSolidifier ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineShredderLarge` | `src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineShredderLarge.java` | `TileEntityMachineShredderLarge ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineRefinery` | `src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java` | `TileEntityMachineRefinery ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, Rc, N, Pkt, M, Ent, Inf, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityMachinePyroOven` | `src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java` | `TileEntityMachinePyroOven ← TileEntityMachinePolluting ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, Rc, N, Pkt, M, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachinePumpjack` | `src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java` | `TileEntityMachinePumpjack ← TileEntityOilDrillBase ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, N, Pkt, M, Inf, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityMachineOilWell` | `src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java` | `TileEntityMachineOilWell ← TileEntityOilDrillBase ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityOilDrillBase (unguarded) | true | I, F, E, N, Pkt, Inf, Rd, T, Mut, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineLiquefactor` | `src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineLiquefactor.java` | `TileEntityMachineLiquefactor ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineHydrotreater` | `src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineHydrotreater.java` | `TileEntityMachineHydrotreater ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityRBMKStorage` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKStorage.java` | `TileEntityRBMKStorage ← TileEntityRBMKSlottedBase ← TileEntityRBMKActiveBase ← TileEntityRBMKBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | I, F, E, H, N, Pkt, Ent, T, Mut, NBT, A | F15 |
| Active HBM source | `TileEntityMachineGasFlare` | `src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineGasFlare.java` | `TileEntityMachineGasFlare ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | I, F, E, N, Pkt, Ent, Inf, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityMachineFractionTower` | `src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFractionTower.java` | `TileEntityMachineFractionTower ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | F, E, Rc, N, Pkt, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityRBMKRodReaSim` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKRodReaSim.java` | `TileEntityRBMKRodReaSim ← TileEntityRBMKRod ← TileEntityRBMKSlottedBase ← TileEntityRBMKActiveBase ← TileEntityRBMKBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | I, F, E, H, N, Pkt, Ent, T, Mut, NBT, A | F15 |
| Active HBM source | `TileEntityMachineFrackingTower` | `src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFrackingTower.java` | `TileEntityMachineFrackingTower ← TileEntityOilDrillBase ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityOilDrillBase (unguarded) | true | I, F, E, N, Pkt, Inf, Rd, T, Mut, NBT, A | F15, F20 |
| Active HBM source | `TileEntityRBMKRod` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKRod.java` | `TileEntityRBMKRod ← TileEntityRBMKSlottedBase ← TileEntityRBMKActiveBase ← TileEntityRBMKBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | I, F, E, H, N, Pkt, Ent, T, Mut, NBT, A | F15 |
| Active HBM source | `TileEntityMachineCoker` | `src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCoker.java` | `TileEntityMachineCoker ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | I, F, E, H, Rc, N, Pkt, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityRBMKReflector` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKReflector.java` | `TileEntityRBMKReflector ← TileEntityRBMKBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | E, H, N, Pkt, Ent, T, Mut, NBT, A | F15 |
| Active HBM source | `TileEntityRBMKOutlet` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKOutlet.java` | `TileEntityRBMKOutlet ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | F, E, N, T, NBT, A | F15 |
| Active HBM source | `TileEntityRBMKOutgasser` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKOutgasser.java` | `TileEntityRBMKOutgasser ← TileEntityRBMKSlottedBase ← TileEntityRBMKActiveBase ← TileEntityRBMKBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | I, F, E, H, Rc, N, Pkt, Ent, T, Mut, NBT, A | F15 |
| Active HBM source | `TileEntityRBMKModerator` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKModerator.java` | `TileEntityRBMKModerator ← TileEntityRBMKBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | E, H, N, Pkt, Ent, T, Mut, NBT, A | F15 |
| Active HBM source | `TileEntityRBMKInlet` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKInlet.java` | `TileEntityRBMKInlet ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | F, E, N, T, NBT, A | F15 |
| Active HBM source | `TileEntityRBMKHeater` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKHeater.java` | `TileEntityRBMKHeater ← TileEntityRBMKSlottedBase ← TileEntityRBMKActiveBase ← TileEntityRBMKBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | I, F, E, H, N, Pkt, Ent, T, Mut, NBT, A | F15 |
| Active HBM source | `TileEntityMachineCatalyticReformer` | `src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java` | `TileEntityMachineCatalyticReformer ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, M, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityRBMKCooler` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKCooler.java` | `TileEntityRBMKCooler ← TileEntityRBMKBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | F, E, H, N, Pkt, Ent, T, Mut, NBT, A | — |
| Active HBM source | `TileEntityRBMKControlManual` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKControlManual.java` | `TileEntityRBMKControlManual ← TileEntityRBMKControl ← TileEntityRBMKSlottedBase ← TileEntityRBMKActiveBase ← TileEntityRBMKBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityRBMKControl (remote-guard) | true | I, F, E, H, N, Pkt, Ent, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityRBMKControlAuto` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKControlAuto.java` | `TileEntityRBMKControlAuto ← TileEntityRBMKControl ← TileEntityRBMKSlottedBase ← TileEntityRBMKActiveBase ← TileEntityRBMKBase ← TileEntityLoadedBase ← TileEntity` | inherited:TileEntityRBMKControl (remote-guard) | true | I, F, E, H, N, Pkt, Ent, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityMachineCatalyticCracker` | `src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticCracker.java` | `TileEntityMachineCatalyticCracker ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | F, E, Rc, N, Pkt, M, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityRBMKConsole` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKConsole.java` | `TileEntityRBMKConsole ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, H, N, Pkt, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityMachineAlkylation` | `src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineAlkylation.java` | `TileEntityMachineAlkylation ← TileEntityMachineBase ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | I, F, E, Rc, N, Pkt, M, Rd, T, NBT, A | F15, F20 |
| Active HBM source | `TileEntityRBMKBurner` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBurner.java` | `TileEntityRBMKBurner ← TileEntityRBMKBase ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | F, E, H, N, Pkt, Ent, T, Mut, NBT, A | — |
| Active HBM source | `TileEntityRBMKBoiler` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBoiler.java` | `TileEntityRBMKBoiler ← TileEntityRBMKSlottedBase ← TileEntityRBMKActiveBase ← TileEntityRBMKBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | I, F, E, H, N, Pkt, Ent, T, Mut, NBT, A | F15 |
| Active HBM source | `TileEntityRBMKBlank` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBlank.java` | `TileEntityRBMKBlank ← TileEntityRBMKBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | E, H, N, Pkt, Ent, T, Mut, NBT, A | F15 |
| Active HBM source | `TileEntityRBMKAbsorber` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKAbsorber.java` | `TileEntityRBMKAbsorber ← TileEntityRBMKBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | E, H, N, Pkt, Ent, T, Mut, NBT, A | F15 |
| Active HBM source | `TileEntityCraneConsole` | `src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityCraneConsole.java` | `TileEntityCraneConsole ← TileEntityLoadedBase ← TileEntity` | own (remote-guard) | true | E, N, Pkt, M, Ent, Inf, Rd, T, NBT, A | F20 |
| Active HBM source | `TileEntityPileSource` | `src/main/java/com/hbm/tileentity/machine/pile/TileEntityPileSource.java` | `TileEntityPileSource ← TileEntityPileBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | E, N, Ent, T, NBT | F15 |
| Active HBM source | `TileEntityPileNeutronDetector` | `src/main/java/com/hbm/tileentity/machine/pile/TileEntityPileNeutronDetector.java` | `TileEntityPileNeutronDetector ← TileEntityLoadedBase ← TileEntity` | own (unguarded) | true | E, N, T, NBT | F15 |
| Active HBM source | `TileEntityPileFuel` | `src/main/java/com/hbm/tileentity/machine/pile/TileEntityPileFuel.java` | `TileEntityPileFuel ← TileEntityPileBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | E, H, N, Pkt, Ent, T, Mut, NBT, A | F15 |
| Active HBM source | `TileEntityPileBreedingFuel` | `src/main/java/com/hbm/tileentity/machine/pile/TileEntityPileBreedingFuel.java` | `TileEntityPileBreedingFuel ← TileEntityPileBase ← TileEntityLoadedBase ← TileEntity` | no HBM body | true | E, H, N, Ent, T, Mut, NBT | F15 |
| Active HBM source | `TileEntityWatzPump` | `src/main/java/com/hbm/blocks/machine/WatzPump.java` | `TileEntityWatzPump ← TileEntity` | no HBM body | false | Rd | F20 |
| Active HBM source | `TileEntityBlockICF` | `src/main/java/com/hbm/blocks/machine/BlockICF.java` | `TileEntityBlockICF ← TileEntity` | own (server/client) | true | E, N, T, NBT | — |
| Active HBM source | `TileEntityAtmosphereEditor` | `src/main/java/com/hbm/blocks/machine/BlockAtmosphereEditor.java` | `TileEntityAtmosphereEditor ← TileEntity` | own (remote-guard) | true | Pkt, T, NBT, A | F19 |
| Active HBM source | `TileEntityFloodlightBeam` | `src/main/java/com/hbm/blocks/machine/FloodlightBeam.java` | `TileEntityFloodlightBeam ← TileEntity` | own (server/client) | true | N, T, Mut, NBT | — |
| Active HBM source | `TileEntityFloodlight` | `src/main/java/com/hbm/blocks/machine/Floodlight.java` | `TileEntityFloodlight ← TileEntity` | own (server/client) | true | E, N, Rd, T, Mut, NBT, A | F20 |
| Active HBM source | `TileEntityBlockPWR` | `src/main/java/com/hbm/blocks/machine/BlockPWR.java` | `TileEntityBlockPWR ← TileEntity` | own (server/client) | true | I, N, T, NBT, A | — |
| Active HBM source | `TileEntityCapacitor` | `src/main/java/com/hbm/blocks/machine/MachineCapacitor.java` | `TileEntityCapacitor ← TileEntityLoadedBase ← TileEntity` | own (server/client) | true | E, N, Pkt, NBT, A | F19 |
| Active HBM source | `TileEntityPistonInserter` | `src/main/java/com/hbm/blocks/machine/PistonInserter.java` | `TileEntityPistonInserter ← TileEntity` | own (server/client) | true | I, N, Pkt, T, NBT | — |
| Active HBM source | `TileEntityFan` | `src/main/java/com/hbm/blocks/machine/MachineFan.java` | `TileEntityFan ← TileEntity` | own (server/client) | true | R, N, Ent, Rd, T | F20 |

## 21. Exact static totals and definitions

These totals are for the active `src/main/java/com/hbm` tree unless explicitly stated. “Candidate” means a deterministic source signature; it does not imply measured cost.

| Requested total | Count | Definition |
|---|---:|---|
| Concrete TileEntity descendants audited | 356 | Active HBM class declarations in `src/main/java/com/hbm` |
| Abstract tile/base classes inspected | 29 | Active HBM descendants |
| Concrete `updateEntity()` declarations | 254 | Concrete classes with a direct HBM override |
| Inherited HBM tickers | 30 | Concrete descendants without their own override and with a nearest HBM update owner |
| Meaningful idle-work paths | 13 | Narrow confirmed list in §4 |
| Redstone consumers / update-path pollers | 22 / 13 | Direct redstone API class references anywhere / in concrete update bodies |
| Timer-bearing update paths | 58 | Active concrete update bodies with timer, modulo, threshold, or total-world-time signatures; includes intentional cadence |
| Repeated-threshold mutation systems | 1 | Blast Door; 499 redundant attempts per full cycle |
| Repeated neighbor/topology scan sites | 7 | Focused confirmed sites in §8 |
| Recipe/progress classes previously audited | 35 | Stable recipe/progress roster in linked machine audit; does not count all recipe API references |
| Inventory/tank/energy recurring idle poll families confirmed here | 3 | Launchers, Radio Torch Counter, TurretBaseNT as detailed in §9 |
| Direct packet/network helper source classes | 130 | Literal send or network-pack signature anywhere in the class body; includes helper calls and is not unique packets |
| Unconditional per-tick packet paths identified | 11 | Focused source paths in §10, including radio base/subclasses as one shared family owner set |
| Direct `sendToAll()` call sites/classes | 2 / 2 | Blast Door transition and CIWS update |
| Direct world-mutation classes | 52 | Concrete class bodies with `setBlock`, `setBlockToAir`, or `func_147480_a` |
| Repeated block-mutation candidates | 1 | Blast Door repeated-threshold animation |
| Recursive tile traversals | 1 family / 3 methods | Blast Door open, close, lock propagation |
| Direct structure/multiblock marker classes | 8 | Marker search listed in §12 |
| `INFINITE_EXTENT_AABB` users | 66 | Concrete classes with explicit active-source return |
| Suspicious large render-distance users | 171 source overrides | All return 65,536 squared distance (256-block radius); three local-geometry families are clearest review candidates |
| Unsafe global mutable guard flags | 3 | Blast, Vault, and Old dummy blocks; two have active writers |
| Persisted unused fields | 2 | `sysTime` in Blast/Vault Door |
| Hot update allocation candidates | 27 | Selected `new` collection/array/geometry signature in direct update bodies |
| Shared bad-pattern clusters | 10 | Table in §18 |
| CRITICAL / HIGH / MEDIUM / LOW findings | 2 / 8 / 10 / 4 | F01–F24 severity table |

`canUpdate()` result census: **28 false, 328 true**. `updateEntity()` census: **254 direct overrides, 30 inherited, 72 no HBM body**. The two counts overlap by design: e.g. a class may declare an empty update override while disabling TileEntity updates.

## 22. Hotspot prioritization

**Top 20 classes and supporting owners to inspect manually**

1. `TileEntityBlastDoor` — lock precedence, repeated mutation thresholds, recursive propagation, and per-tick sync.
2. `TileEntityDummy` — target ownership and stale-coordinate teardown behavior.
3. `TileEntityVaultDoor` — duplicate orientation-plane redstone scans and per-tick sync.
4. `TileEntityLaunchTable` — 81 redstone reads, state polling, and two packet paths per tick.
5. `TileEntityCompactLauncher` — repeated redstone and state polling, plus two packet paths per tick.
6. `TileEntityTurretCIWS` — global gauge broadcast and per-tick connection descriptor allocation.
7. `TileEntityTurretBase` — shared full-list targeting and LOS ordering for the legacy bomb turret family.
8. `TileEntityTurretBaseNT` — shared connection refresh and NBT packet path.
9. `TileEntityRadioTorchCounter` — adjacent-inventory snapshot and per-tick packet.
10. `TileEntityRadioTorchBase` — shared configuration serialization and per-tick packet path.
11. `TileEntityRadioTorchLogic` — repeated configuration and condition serialization.
12. `TileEntityRadioTorchSender` — inherited base work plus sender state synchronization.
13. `TileEntityRadioTorchReceiver` — inherited base work plus receiver state synchronization.
14. `TileEntityMachineRadarScreen` — per-tick screen-state packet.
15. `TileEntityDroneDock` — recurring chunk-region and request-path discovery.
16. `TileEntityDroneRequester` — per-tick matcher-state synchronization.
17. `RequestNetwork` — server-tick expiry traversal and node/path maintenance.
18. `TileEntityDoorGeneric` — redstone activation path and shared door state behavior.
19. `TileEntityLaunchPad` — launch-state and world-query review candidate.
20. `TileEntityLaunchPadLarge` — launch structure and update-path allocation review candidate.

The list includes shared abstract bases and the `RequestNetwork` helper because they own behavior inherited or used by concrete tiles.

### 22.1 Highest likely server CPU impact

1. Powered `TileEntityTurretBase`/CIWS entity-list copy and LOS-before-distance path.
2. `RequestNetwork.updateEntries()` full map traversal every server tick.
3. Launch Table 9-by-9 redstone reads and repeated launcher state refresh.
4. Vault Door redstone planes up to 60 calls/tick.
5. TurretBaseNT eight-position connection refresh every tick.
6. Blast Door repeated threshold world lookups during animation.
7. RequestNetwork known-node path checks and DroneDock 11-by-11 chunk view once per second.
8. Radio Torch Counter full neighbor inventory snapshot each tick.
9. Compact Launcher 3-by-3 redstone reads and full state refresh.
10. Unconditional per-tick serializers on screen/radio/requester paths.

### 22.2 Highest network waste

1. Launch Table and Compact Launcher: 40 sends/s/tile, second payload to radius 250 even when empty.
2. Blast Door and Vault Door: 20 sends/s/tile each to radius 250 while unchanged.
3. CIWS: 20 `sendToAll()` gauge broadcasts/s/tile.
4. TurretBaseNT: 20 full NBT sends/s/tile to radius 250.
5. Machine Radar Screen: 20 full screen sends/s/tile to radius 100.
6. Radio Torch base/logic: 20 NBT sends/s/tile to radius 50.
7. Radio Torch Counter and Drone Requester: 20/s/tile to radius 15.
8. Drone requester matcher serialization repeats even without matcher changes.
9. Doors also retain separate transition messages alongside the heartbeat.
10. No payload-byte ranking is possible without packet encoding sizes/captures.

### 22.3 Highest rendering candidates

1. Blast Door infinite AABB and 65,536 squared-distance override.
2. Vault Door infinite AABB and 65,536 squared-distance override.
3. `TileEntityTurretBase` infinite AABB and 65,536 squared-distance override.
4. Nuke/effect tiles using infinite AABBs; extent may be intentional during renderable effects.
5. Long multiblock render controllers such as rocket assembly/launch structures; require actual rendered geometry.
6. Turbine/steam/large machinery overrides; inspect the model and effects before narrowing.
7. Decorative TESR bounds: assess actual tile-local animation rather than inheritance alone.
8. Remaining explicit infinite-bound classes are listed by `Inf` in the matrix.
9. All 171 custom-distance methods return 65,536 squared distance; geometry determines which are justified.
10. No rendering changes or lighting-path changes were made.

### 22.4 Highest save/load fragility and correctness risk

1. Dummy target coordinates can outlive or point to a replacement multiblock; teardown does not verify ownership.
2. Blast Door upper redstone can bypass its lock condition.
3. Recursive blast-door propagation scales stack depth with the connected component.
4. Global safe-break flags can leak through reentrant/exception paths.
5. Door timer/state/redstone edge state resumes directly from NBT without normalization.
6. `sysTime` is dead persisted state in two door classes.
7. Request graph nodes are transient and cleaned on expiry; world unload cleanup exists for RTTY state.
8. Current machine runtime generation and deferred save behavior are already documented in the linked audit; no repeat finding raised.
9. Add-on and archived source copies have separate provenance and should not be mistaken for active core classes.
10. No save compatibility test was run.

### 22.5 Top 10 shared architectural problems

1. Unchanged state serialized/sent from tile tick methods.
2. Fixed-area redstone polling when the geometry and orientation are stable.
3. Repeated topology discovery rather than invalidation-driven connection updates.
4. Dummy/controller coordinate coupling without durable ownership identity.
5. Static mutable suppression flags around world callbacks.
6. Broad rendering bounds used as a substitute for precise model extent.
7. Update callbacks retained after meaningful work has moved to shared runtime tasks.
8. Repeated per-tick collections/arrays in target and inventory scans.
9. Turret targeting performs entity-list copying and line-of-sight checks before cheap distance rejection.
10. Door transition fields are restored from NBT without normalization or validation.

### 22.6 Easiest high-confidence future investigation/fix candidates

1. Correct Blast Door boolean grouping, with a lock/redstone regression matrix.
2. Replace Blast Door repeated `>=` dummy operations with one-shot transition events while preserving sequence timing.
3. Repair `RequestNetwork.updateEntries()` cadence and measure map visits before/after.
4. Change door/screen/radio/requester sync to change events plus an appropriate recovery baseline.
5. Move launcher power scans to a redstone edge/event cache keyed to relevant block changes.
6. Cache CIWS power position descriptors rather than rebuilding them per update.
7. Bound turret candidate collection before LOS while preserving target priority.
8. Give dummy teardown a verified controller ownership relation and test stale/chunk-edge cases.
9. Use scoped teardown state that cannot leak across callbacks.
10. Capture actual TESR extents before changing any bounds.

### 22.7 Highest-risk areas requiring careful behavior preservation

1. Door animation ordering, dummy creation/removal, locked-chain rules, and NBT transition resume.
2. Turret target eligibility, distance/LOS ordering, player exclusion, and firing cadence.
3. Missile launcher packet format and client multipart reconstruction.
4. Request network node leases, reachability updates, drone request priority, and retry fairness.
5. MachineRuntime’s elapsed-loaded-tick accounting and resource-boundary order.
6. UniNodespace and FluidNetMK2 lifecycle invalidation, authoritative graph ownership, and receiver-order semantics.
7. TESR/multiblock geometry and client-only class loading.
8. Existing NBT keys and chunk-load persistence constraints.
