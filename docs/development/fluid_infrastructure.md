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

Oil well / pumpjack / stimulation crude ceilings are 6/24/48 L/min. Primary gas well/pumpjack deposits default to 600/4,800 standard L/min. New geological gas is 98 mol% methane /2% helium; separation has paid reactor and cryogenic routes. Existing methane stock is not relabeled. All paired outputs must fit before depletion or injection. See [fluid reference](fluid_reference.md#geological-gas-and-laser-fills) for exact source intervals and coflow.

## Display and transport

`FluidInfo` supplies shared container, tank, selector and NEI hovers. Liquid L, standard gas L, steam water mass and specialist units are distinct. Network PU is a transport pressure class, not a universal thermodynamic pressure. `FluidTank` preserves saved fill when the current maximum is lower; no further receiving is allowed until room exists. Native endpoints and `FluidNetMK2` own transfer on the server thread.
