# Industrial manufacturing

Build the operation you need. Casting, forming, joining, electronics and assembly have distinct roles; a larger machine does not replace all of them.

| Machine | Operation | Purpose |
| --- | --- | --- |
| Foundry and casting equipment | Heat-driven melting, alloying and pouring | Cast parts, recover compatible metals and handle molten byproducts |
| Burner / Electric / Conveyor Press | Forming selected by reusable stamps | Plates and stamped parts with different power/automation arrangements |
| Arc Welder | Joining with recipe work and sometimes process gas | Welded structures and specialist hardware |
| Soldering Station / Vacuum Solderer | Circuit assembly | Prepared components, board, solder and any working solvent |
| Fabrication Machine | One template-selected assembly lane | Workshop machine/component production |
| Industrial Fabrication Machine | Eight assembly lanes with handling/cooling | Scale the same fabrication recipes |
| Chemical Reactor | Reaction and conditioning | Fuels, acids, polymers and process chemicals |
| Industrial Size Chemical Reactor | Eight chemical lanes with shared cooling | Parallel chemistry supported by power, heat and output handling |
| Specialist assembly | Fuel pellets, missiles, vehicles and other dedicated work | Corresponding assemblies from upstream parts |

## Building a workshop

Primitive recovery and anvil/furnace work lead to first steel and powered machinery. Electric Press draws 2 kW, Fabrication Machine 3 kW and Electric Furnace 8 kW at base settings. Stagger early jobs or expand generation before running everything together. Recipes, upgrades and configuration can change demand.

Build useful parts: cast/formed metal, welded structure, motors/pumps, electronics and final fabrication. Assembly Templates select fabrication recipes; stamps, Foundry Templates and molds are reusable tools. Expensive raw metal does not stand in for a motor or circuit.

## Scaling production

Both fabrication machines use the same recipes. The industrial machine has eight lanes at twice the workshop lane throughput, not an exclusive heavy-fabrication tier. It requires a sealed **20-L Fresh Water charge**. An integral dry cooler recirculates it, consuming no operating makeup and producing no steam. Cooling pumps/fans contribute to demand. Industrial chemistry has the same charge requirement and can reach its shared cooling limit.

Welding gas, purge gas, testing gas and solvent have different roles. Some baths remain for reuse; fills and purges are consumed by the operation. Read the selected recipe before connecting supplies. Keep solid, fluid and molten outputs clear.

## Metallurgy and heavy equipment

The Metallurgical Furnace is the inexpensive, fuel-hungry first-alloy route. The Blast Furnace is bulk coke-fired steelmaking with forced air, optional hot blast, flux and oxygen. Top gas supplies suitable burners, but is a small byproduct benefit rather than a large power station. The Large Arc Furnace is an electrically intensive melting/alloying/recycling route: its 500-kW base rating grows with upgrades, while melting work remains necessary.

For walls, catwalks, roofs and furniture see [Construction recipes](../construction-recipes.md). Pressure vessels, reactor equipment and hot-process machinery retain specialist costs.

Industrial capability, precision, balancing and controlled fabrication are proposed in [RealSim Production](../design/REALSIM_PRODUCTION.md). Those restrictions are not current tiers. Active recipes and requirements remain visible in NEI.
