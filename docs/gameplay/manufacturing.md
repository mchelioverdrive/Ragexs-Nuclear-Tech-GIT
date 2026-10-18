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

## Armor modification manufacture

Make all five ballistic inserts in the Fabrication Machine (Assembler), then install or remove them at the existing Armor Modification Table. Kevlar panels use four prepared aramid sheets, two leather covers and four string. `plate_kevlar` keeps its registry identity and existing Chemical Reactor recipe: that production route combines polymer chemistry and fiber/sheet preparation as a game abstraction. Polymer synthesis alone is not textile weaving; its existing reactant bill, yields and mass accounting are unchanged.

For ceramic inserts, press one alumina powder with a plate stamp into one unfired armor tile, then sinter that tile with one coke gem in the Metallurgical Furnace. The furnace also consumes its normal heating fuel. Assemble SAPI / ESAPI / XSAPI from **1 / 2 / 3 fired tiles**, **4 / 6 / 8 aramid sheets**, **1 / 2 / 3 polymer units** as bonding film, and **2 / 3 / 4 leather covers**, respectively. Final assembly takes **200 / 300 / 400 base ticks**. Unfired tiles cannot substitute for fired tiles, and higher tiers do not consume completed lower-tier inserts. This shared alumina route and the increasing tile/backing counts are game design choices, not claims about exact military SAPI constructions. Ceramic forming and thermal treatment remain distinct from final lamination/assembly.

Steel inserts use four existing formed Weapon Steel plates, two aramid sheets, two leather and four string in the Assembler (200 base ticks). Weapon Steel already comes from Rotary Furnace refining, Foundry casting and plate forming. It represents a selected steel grade in this game; it is not a claim of ballistic certification. Ordinary construction steel blocks are no longer the protective component.

Servos, rubber/lead/obsidian cladding, battery modules, magnets, sensors, night vision and the oxygen life-support module also require Assembler integration. These use prepared shielding cloth, formed plates, manufactured cell packs, control boards, existing furnace-made SmCo magnets, wiring and optics as appropriate. Obsidian cladding uses Shredder-prepared obsidian gravel and polymer binder. Cell-pack charge is not transferred: armor battery modifications retain their existing capacity-multiplier behavior. Sensor fabrication/calibration, night-vision intensification, composite consolidation and exact panel geometry remain compressed manufacturing abstractions; the neutrino lens and indestructible obsidian cladding retain their fictional behavior.

Four armor-modification recipes remain at the crafting table: mix lead powder, clay and a bottle into paint; strap and mount each of the two finished gas masks using two leather and a steel bolt; cut and lace four prepared rubber units with two leather and two string into simple pads. Manufacture the M65/mono facepieces separately in the Assembler, using four rubber and one iron plate each, with two glass panes for the M65 (100 base ticks each). Mounting a finished mask copies its NBT, including any installed filter, into the attachment. Registered modifications with disabled/absent production recipes are not re-enabled. Existing protection values, durability, movement effects, item metadata/NBT and installed modifications are unchanged.

## Scaling production

Both fabrication machines use the same recipes. The industrial machine has eight lanes at twice the workshop lane throughput, not an exclusive heavy-fabrication tier. It requires a sealed **20-L Fresh Water charge**. An integral dry cooler recirculates it, consuming no operating makeup and producing no steam. Cooling pumps/fans contribute to demand. Industrial chemistry has the same charge requirement and can reach its shared cooling limit.

Welding gas, purge gas, testing gas and solvent have different roles. Some baths remain for reuse; fills and purges are consumed by the operation. Read the selected recipe before connecting supplies. Keep solid, fluid and molten outputs clear.

## Metallurgy and heavy equipment

The Metallurgical Furnace is the inexpensive, fuel-hungry first-alloy route. The Blast Furnace is bulk coke-fired steelmaking with forced air, optional hot blast, flux and oxygen. Top gas supplies suitable burners, but is a small byproduct benefit rather than a large power station. The Large Arc Furnace is an electrically intensive melting/alloying/recycling route: its 500-kW base rating grows with upgrades, while melting work remains necessary.

For walls, catwalks, roofs and furniture see [Construction recipes](../construction-recipes.md). Pressure vessels, reactor equipment and hot-process machinery retain specialist costs.

Industrial capability, precision, balancing and controlled fabrication are proposed in [RealSim Production](../design/REALSIM_PRODUCTION.md). Those restrictions are not current tiers. Active recipes and requirements remain visible in NEI.
