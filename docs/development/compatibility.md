# Configuration, persistence and compatibility

Current contracts for contributors and pack authors. [Generator reference](generator_reference.md), [fluid reference](fluid_reference.md) and native recipe loaders supply subsystem-specific details.

## Source-of-truth order

Production source plus loaded server configuration and enabled recipe overrides determine behavior. Current references describe stock defaults and explicit compatibility boundaries. Player guides simplify those defaults. Design proposals do not enable features. Historical audits record earlier evidence and cannot override current source or references.

Machine JSON values already written to a server are explicit configuration, even when they match an older default. Fresh default changes do not overwrite them. Underscore-prefixed recipe files are templates; enabled files retain loader validation and IMC boundaries. A physical recipe must declare the complete supported mass/work/heat bill. A legacy volume-only override cannot silently bypass that requirement. Existing 528/LBSM branches remain independent progression choices.

## Saved quantities and identities

- Preserve item, block, TileEntity, fluid and metadata identities. Append new fluid identities without reordering existing registrations; reject custom-name collisions.
- Reduced capacity limits new receiving. Retain saved over-cap contents and allow normal draining; never clamp, rescale or reinterpret unknown stock.
- Electrical storage uses half-joule `long` quanta. Versioned NBT writes `energyFormatVersion`, `energyQuanta` and applicable `energyCapacityQuanta`; unversioned legacy counts load exactly as quanta. Network state is not another energy store.
- Fluid identity determines quantity basis. Cryogenic liquid IDs remain liquid; their process-gas IDs are separate. Steam retains water-equivalent mass per count. Molten/nuclear/specialist aliquots keep their individual conventions.
- New reduced portable fills use `realSimContainerAmount`. Untagged old stacks keep historical quantities; unequal amounts must not merge as equivalent fills.
- Paid chemical fuel, process work, exhaust heat, fractional products and electrical storage are separate accounts. Display snapshots cannot recreate any of them.
- Source-specific Mini RTG items retain source, age and energy through crafting/placement/drops. Unidentified old Mini/Power units adopt Pu/Po once; unknown source NBT remains intact without generation.
- Material metadata 1500 retains magnesium; phosphorus is 1531; tin bronze is 51. No blanket conversion of indistinguishable old fragments or arsenic-bronze stock.

## Lifecycle and authority

World/inventory/fluid mutation belongs to the owning server thread. MachineRuntime lifecycle, dirty events, scheduled boundaries and native compatibility reconciliation own progress. Unload/replacement must not run deferred work against a new controller. Offline time produces no free electricity or process work.

Preserve multiblock proxies, native sided inventories, automation and computer controls. Client information follows server-owned status; matching client/server versions are required. Item previews use local settings. Keep client classes out of dedicated-server loading paths.

Legacy NBT machine status packets are decoded before being queued onto the client thread; tile lookup and status application happen there, as with buffer-based status packets. Machine information uses synchronized demand/draw snapshots without first evaluating the client's simulation getters. An unavailable recipe duration or zero configured display capacity produces an empty gauge, without changing machine contents. Strict mass/LHV validation remains required for simulation; unresolved preview properties omit only their exact physical calculation.

Fixed machine lane arrays retain their saved prefix and pad missing lanes with zero on load. The assembler and industrial assembler/chemical reactor use their current lane counts; steel furnaces and generators retain their fixed slot counts. Existing NBT keys and packet layouts are unchanged.

Heating-trait JSON must contain at least one step with positive input/output amounts and heat, and a registered output fluid. Cooling-trait JSON requires positive input/output amounts, nonnegative heat and a registered output. Invalid traits fail at configuration loading rather than being installed for tooltips or machine division. Filled output tanks block incompatible heat conversion. Watz segments with incompatible stored fluid types/pressures are not pooled; existing reaction and failure handling continue without that shared cooling operation. Recoverable over-cap stock remains stored.

Fluid settings copied to a machine, steam-type computer controls and the RBMK boiler grade selector require the affected tank to be drained before selecting a different type. Rocket Transporters retain an incompatible destination tank and skip that fluid transfer. Custom-machine production waits for compatible output storage, including already-reserved generator recipes. Mining Lasers recover 500 fluid units per oil/gas ore item only when the whole amount fits a compatible tank; blocked ore drops follow normal item collection, and merged stacks retain their remaining items. Industrial chemical reactor distribution weights only positive receiving room and bounds outgoing removal by available fluid.

Recipe display cycling can return no representative for an empty ore-dictionary entry; callers omit that unavailable item description. Universal NEI pages retain arrays of ingredient alternatives and reject missing required ingredient slots rather than dropping them. Optional outputs remain optional. Custom-machine pages retain their physical bill directly. Custom item-tooltip additions require a nonnull item belonging to the cached recipe; the normal parent tooltip is preserved otherwise, including GTNH NEI's per-visible-recipe callbacks.

## External boundaries

RTM retains the `hbm` mod/config identity and replaces upstream HBM in an instance. RF and legacy energy adapters convert at their existing API boundaries; their unit is not the new internal source of truth. Fuel LHV alone grants no heater, engine, aviation, turbine or propellant acceptance. OreDictionary aliases do not override exact native identity or exact item distribution.

The server-safe [RTMApi](../../src/main/java/com/hbm/api/RTMApi.java) identifies family `HBM`, variant `RTM`, API version 1. Optional integrations must remain usable with the other mod absent. Reactor HU/burnup, fictional sources and arbitrary pack scripts do not gain physical certification merely by connecting to an SI turbine or transport pipe.

## Maintaining the documentation

Update the current reference when behavior changes; record the change in `CHANGELOG.md`. Preserve useful discovery evidence in the historical area rather than appending corrections to a contradictory current table. Keep exact source ownership and quantities here, operating explanations in gameplay, and candidates/unresolved choices in design. A source/build/static check is separate from in-game, visual, multiplayer or profiler acceptance.
