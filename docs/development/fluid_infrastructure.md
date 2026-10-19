# Fluid storage and supply reference

Capacity, process flow, thermal duty and electrical power are independent limits. Fresh capacities apply to new receiving space; over-cap saved stock remains intact and can drain. Unknown fluid identities are preserved. Exact fluid quantity conventions are in [fluid reference](fluid_reference.md).

## Containers and bulk storage

| Container / store | Current fresh default |
| --- | --- |
| Canister | 20 L resolved liquid; 500 standard L resolved gas |
| Portable / lead tank | 20 L resolved liquid; 1,000 standard L gas |
| Gas tank item | 1,000 standard L gas; 20 L resolved cryogenic liquid |
| Portable barrel | 200 L resolved liquid; 16,000 standard L gas |
| Disperser canister | 20 L resolved liquid; gas fill 2,000 counts |
| Plastic / corroded / iron / steel / TC / antimatter drum | 200 / 100 / 200 / 200 / 200 / 200 quantity counts; physical basis follows contents |
| Laboratory / ordinary sampler | 1 / 20 counts |
| Newly world-filled water or milk bucket / bottle | 10 L / 1 L; placement still uses one Minecraft fluid block |
| Ordinary multiblock tank / BAT9000 | 32,000 / 64,000 counts, with selected-fluid basis |
| Pipe pump | 10,000-count buffer; configured `capacity`, saved `buffer` and OC `setFlow` retain quantity/tick meaning |

[FluidContainerRegistry](../../src/main/java/com/hbm/inventory/FluidContainerRegistry.java) controls all individual fills, including unresolved and specialist exceptions. New reduced fills have additive `realSimContainerAmount`; untagged containers retain their original amounts. Different quantities do not stack as though equal. Registry identity, custom names and pressure are independent of amount. Packed tanks retain their contents; changing capacity never changes an existing filled amount.

## Native process stores

Generators use the reserves listed in [generator reference](generator_reference.md). Chemistry and specialist machines use batch/minute-sized stores derived from their current bills. Their source declarations and enabled configuration, rather than a universal liquid-volume rule, define individual capacities. Petroleum bulk process stores remain deliberately larger: atmospheric refinery 32,000-L inlet /8,000-L liquid outlets /24,000-standard-L gas; vacuum 32,000-L inlet /8,000-L liquid outlets; Hydrotreater 32,000-L oil /16,000-L treated /64,000-standard-L H2 /24,000-standard-L sour gas; Reformer 16,000-L naphtha /8,000-L reformate /24,000-standard-L gas outlets.

## Groundwater and geological extraction

Electric groundwater: 120 L/min at 10 kW, 400-L output. Steam groundwater: 30 L/min, 200-L output. Production and export have independent per-tick limits; fractional progress and paid shaft work persist, without offline catch-up. A 20-L industrial cooling charge takes 10/40 seconds at these rates; recirculation is not withdrawal.

Ordinary groundwater uses `FRESH_WATER` by default. Only biomes classified with Forge 1.7.10 `BiomeDictionary.Type.OCEAN` yield the existing `SALT_WATER` (Salt Water): vanilla Ocean, Deep Ocean and Frozen Ocean, plus modded ocean biomes with that classification. Rivers, swamps, forests, plains, mountains and inland lakes remain fresh unless explicitly ocean-tagged. The sampling point is the center column immediately below the pump core (`xCoord`, `yCoord - 1`, `zCoord`); the biome lookup uses that exact X/Z, never neighboring outlets or a footprint vote. Missing biome/chunk data pauses extraction. Planetary non-water tables retain their existing fluid and non-water throughput penalty; terrain, altitude and dimension eligibility remain unchanged.

Stored water is never relabeled. A pump moved across a salinity boundary exports its existing contents under the old identity and resumes extraction of the new type only after the output tank empties. Paid fractional stock for inactive fluids is preserved by fluid name in `pumpStoredWaterMicroliters`; the active fraction keeps the existing `pumpWaterMicroliters` key, and shaft work keeps its existing key. These are stored quantities, not cached biome classifications. Legacy saves need no conversion. Full or incompatible tanks stop paid extraction; blocked pipes can fill the existing local buffer, then pumping pauses. Native type/pressure transactions and the shared export budget remain authoritative. Inland groundwater directly supplies existing freshwater boilers, cooling and chemistry; ocean water retains the existing water-treatment/desalination route.

The canonical saline fluid name is `SALT_WATER`, displayed as **Salt Water**. `FRESH_WATER` remains **Fresh Water**. The former `WATER` name is accepted when reading legacy fluid names, recipe overrides, saved process bills and fractional stock; new definitions and saved process identities use `SALT_WATER`. The numeric fluid slot, material quantities, existing artwork and process balances are unchanged. Legacy `WATER` entries in fluid-trait settings remain readable, with an explicit `SALT_WATER` entry taking precedence. The deprecated Java `Fluids.WATER` field aliases the same registered object for external binary compatibility; native production code uses `Fluids.SALT_WATER`.

Oil well / pumpjack / stimulation crude ceilings are 6/24/48 L/min. Primary gas well/pumpjack deposits default to 600/4,800 standard L/min. New geological gas is 98 mol% methane /2% helium; separation has paid reactor and cryogenic routes. Existing methane stock is not relabeled. All paired outputs must fit before depletion or injection. See [fluid reference](fluid_reference.md#geological-gas-and-laser-fills) for exact source intervals and coflow.

## Display and transport

`FluidInfo` supplies shared container, tank, selector and NEI hovers. Liquid L, standard gas L, steam water mass and specialist units are distinct. Network PU is a transport pressure class, not a universal thermodynamic pressure. `FluidTank` preserves saved fill when the current maximum is lower; no further receiving is allowed until room exists. Native endpoints and `FluidNetMK2` own transfer on the server thread.
