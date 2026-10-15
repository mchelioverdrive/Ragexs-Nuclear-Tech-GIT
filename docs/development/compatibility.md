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

## External boundaries

RTM retains the `hbm` mod/config identity and replaces upstream HBM in an instance. RF and legacy energy adapters convert at their existing API boundaries; their unit is not the new internal source of truth. Fuel LHV alone grants no heater, engine, aviation, turbine or propellant acceptance. OreDictionary aliases do not override exact native identity or exact item distribution.

The server-safe [RTMApi](../../src/main/java/com/hbm/api/RTMApi.java) identifies family `HBM`, variant `RTM`, API version 1. Optional integrations must remain usable with the other mod absent. Reactor HU/burnup, fictional sources and arbitrary pack scripts do not gain physical certification merely by connecting to an SI turbine or transport pipe.

## Maintaining the documentation

Update the current reference when behavior changes; record the change in `CHANGELOG.md`. Preserve useful discovery evidence in the historical area rather than appending corrections to a contradictory current table. Keep exact source ownership and quantities here, operating explanations in gameplay, and candidates/unresolved choices in design. A source/build/static check is separate from in-game, visual, multiplayer or profiler acceptance.
