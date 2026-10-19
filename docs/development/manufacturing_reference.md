# Manufacturing and consumer reference

Current default consumer envelopes at 20 TPS. Recipe work/heat is separate from electrical storage and from generator nameplates. See [manufacturing guide](../gameplay/manufacturing.md) for machine selection.

## Consumer ratings

| Equipment | Current demand / work |
| --- | --- |
| Electric furnace, intermittent workshop heating | **8 kW**, 40 kJ per 100-tick base job |
| Electric press, forming | **2 kW**, native cadence |
| Fabrication, workshop assembly | **3 kW**, recipe-duration work |
| Industrial fabrication, 8 assembly lanes | **6.5 kW/lane, 52 kW total**, twice workshop lane throughput, cooling included |
| Generic chemical reactor | **1/2/4/10 kW**: fermentation; mixing/dilution/neutralization; other utilities; polymer/rubber/plastic. Released physical bills override families |
| Industrial chemistry, 8 lanes | **2.5/4.5/8.5/20.5 kW per lane**, or **20/36/68/164 kW** total at twice nominal throughput; physical bills retain their own heat/work/time plus cooling |
| Centrifuge, separation | **5 kW**, speed/overdrive power follows throughput |
| Crusher/shredder, workshop processing | **3 kW**, blades/cadence retained; full-tick affordability enforced |
| Pyrolysis oven | **100 kW base retained**, upgraded draw follows duration/throughput/efficiency rather than a square |
| Large arc furnace, heavy melting | **500/1500/2500/3500 kW**, **1.8 MJ/kg-equivalent** persistent paid work |
| Mining laser, beam excavation | **500 kW base**, existing cycle/range/efficiency decisions |
| Excavator, digging/handling | **250 kW**, speed demand follows `(2+speed)/2` throughput, efficiency saves 10%/level |
| Welder/soldering, intermittent parts/electronics | Retained; steel welding **5 kW**, iron **1 kW**, basic soldering around **1 kW**; heavier recipe bills exposed |
| Compressor, compression/aftercooling | Retained **25 kW** base; physical compression work/pressure requirements |
| Petroleum machinery | Retained mass/work/heat/feed requirements, selected-bill information |
| Groundwater/well/pumpjack | Retained **120 L/min at 10 kW**, steam **30 L/min**, default oil auxiliaries **1/3 kW** |
| Large crusher / X3030 | Old processing tile is unregistered. Separately restored X3030 is a decorative prop with **no power or processing**; neither is a factory load |

Water electrolysis pays 55 kWh/kg H2: the compact native fluid Electrolyser retains 20 kWe; the Chemical Reactor processes its 1-L lot in 20 seconds at about 1.108 MW plus cooling. Fluid and metal Electrolyser lanes debit their work independently and share actual available electricity. Cryogenic work is recipe-specific and bounded by the current separator rate; air and geological-gas separation are 50 kWe /6,000 standard L/min. See current owners in [process reference](process_reference.md).

Released Chemical Reactor bills may draw up to **2 MW per lane**, plus cooling auxiliaries. The workshop buffer is **2 MJ**; the eight-lane industrial buffer is **16 MJ**. These are finite technical buffers, not recipe energy discounts. The ordinary/industrial chemical dry coolers reject **5/40 MW**, with one shared industrial budget. Reactor gas feeds support **24,000 standard L/min per inlet**; basic aqueous service retains 60 L/min and other liquids 12 L/min. Mixer and native Electrolyser limits remain independent.

## Fabrication and cooling

Fabrication Machine and Industrial Fabrication Machine resolve the same `AssemblerRecipes` pool through `TileEntityMachineAssemblerBase`. Industrial equipment has eight lanes, not exclusive heavy-fabrication capability. The industrial chemistry base likewise retains independent lane state and recipe work/heat/duration. Proposed eligibility tiers live only in [RealSim Production](../design/REALSIM_PRODUCTION.md).

Industrial fabrication/chemistry require one sealed 20-L fresh-water charge per machine and an integral dry cooler. No operating makeup or new spent steam is produced. Existing stored steam can drain. Active cooling costs at least 500 W/lane or 2% of process rejection, whichever is higher; this cost is included in draw and rejection. Internal circulation is rejected W ×60 /83,600 L/min (4.18 kJ/kg K, 20-K rise). Generic work rejects to heat; physical bills use `rejectedJ`. Chemical cooling limits are shared across lanes.

Large Arc Furnace work is 1.8 MJ per output kg-equivalent, with a local 1-kg/ingot-equivalent convention and 500/1,500/2,500/3,500-kW installed tiers. This is a melting-duty model around 1,600°C, not alloy-specific reaction thermodynamics. Paid work persists; speed changes duration/power, not batch work. Final partial ticks pay only the remaining work. Old unowned float progress creates no energy credit. Its 100-MJ electrical capacity supplies storage, not recipe eligibility.

`IndustrialDemand.atSpeed` relates generic work to throughput with up to 30% saving; physical recipe work is retained. Configurations, overdrive, integer tick rounding and active lanes determine actual demand. No physical-water discount is inferred from an efficiency upgrade.

## Factory planning assumptions

| Model | Normal average | Simultaneous peak | Sensible generation |
| --- | ---: | ---: | --- |
| Starter workshop | **10.90 kW** | **36.00 kW** | Heavy Stirling 40 kW covers peak; two 10-kW starters or approximately 13.17-kW early steam cover mean with peak support. One starter serves the smaller first bench |
| Diesel-era factory | **93.26 kW** | **148.20 kW** | One **200-kW genset** covers both; qualified petroleum heat is separate |
| Heavy pre-nuclear factory | **622.96 kW** | **1018.20 kW** | One **750-kW engine** covers mean with peak support; **two** cover peak. Adequately supplied steam is an alternative |
| Large fossil facility | **2515.08 kW** | **3241.20 kW** | Four 750-kW engines cover mean, five peak; **2.94-MW net simple gas** needs peak support; **3.91-MW combined cycle** or approximately **3.60-MW industrial steam reference** covers this peak |

These are constructed duty-cycle examples, not measured facilities or universal progression requirements. Large physical chemical bills and parallelism can change them. Storage must meet both joule demand and peak transfer watts. Process heat and generator auxiliaries remain separate loads.

## Construction vocabulary and fabrication limits

The workshop Assembler and every industrial Assembler lane have **12 item-input slots**. Industrial fabrication adds parallel lanes, not a larger ingredient grid. Stock definitions and component supplements must fit that limit; registration rejects larger arrays. `AssemblerRecipes.validateRegisteredNEIRecipes()` checks every loaded stock/configured ingredient through the same display contract used by NEI and reports the output, slot, Java type, nested identity and native matcher availability when a display cannot be compiled. Concrete, NBT and wildcard stacks retain quantity and tags; OreDict alternatives cycle through their real registered forms.

Construction uses shells/tanks for containment, cast/welded plates and beams for load paths, motors/gears/blades for drive assemblies, pipes/rubber for fluid handling and seals, and coils/boards for electrical equipment. A motor includes its shaft/bearing hardware; a turbine rotor includes its rotating assembly. Pipes and control hardware also represent local fittings/valves. Components are compressed functional assemblies, not literal real-world bills of materials. External process heaters, shielding and pipe plants remain external where the machine actually requires them.

Armor-modification defaults are appended by `AssemblerRecipes.registerArmorModRecipes()` after existing stock/optional registrations so native legacy numeric template indices are retained. All 19 modifier outputs and the two finished mask outputs use the normal template folder, metadata/initial damage zero, count one and no output NBT. Each bill has at most seven ingredient entries; manufactured battery packs are single non-stackable inputs. Ordinary `ComparableStack` battery inputs accept existing charge NBT without transferring that charge to the capacity-only armor modifier. No installed armor or modifier is consumed to make another tier. The two table-mounted masks use a registered `ShapedOreRecipe` subclass that deep-copies the finished mask's NBT, including the shared `hfrFilter` compound, to the attachment without sharing a mutable tag with the crafting preview/input.

The ceramic route uses exactly two new ordinary stackable parts: `armor_tile_unfired` and `armor_tile_alumina`. A plate-stamp Press converts one native alumina powder to one unfired tile; `BlastFurnaceRecipes` converts one tile plus one coke gem to one fired tile, using the existing Metallurgical Furnace sintering abstraction and ordinary heating fuel. The base furnace takes 400 ticks (its extension advances three process units per tick). Press recipes follow native mechanical cadence, not a new configurable duration. SAPI/ESAPI/XSAPI use one/two/three of these shared alumina tiles; this intentionally avoids unsupported claims about exact military material assignments and unnecessary carbide intermediates. See the [manufacturing guide](../gameplay/manufacturing.md#armor-modification-manufacture) for component counts and remaining abstractions.

These recipes use the existing serializable Press, Metallurgical Furnace and Assembler registries, clear/rebuild on `/ntmreload`, and appear through their existing NEI handlers. The Assembler's standard template selection and ingredient display contracts apply unchanged. The Anvil only imports explicitly named Assembler outputs; no armor modifications or ceramic tiles are imported. Formed metal supplied by an Anvil is still a component, not a bypass for final insert manufacture. Armor-table installation/removal and `ArmorModHandler` persistence/behavior remain unchanged.

| Equipment family | Construction represented |
| --- | --- |
| Industrial Mixer | Welded vessel, tanks, beam support, motors, reduction gear, steel agitators, piping, seals, wiring and Analog control. Fabricated in the Assembler. |
| Boilers | Small unit: steel vessel, copper heat-transfer tubes and service piping/insulation. Industrial unit: cast structure, pressure shells, steel/copper tube banks, refractory, supporting beams, feed tank/drive and Analog controls. |
| Rotary furnace | Refractory drum/support with steam piston and gear drive; its operating steam and process heat remain separate. |
| Electric / ICF presses | Reinforced load frame, hydraulic cylinders, pump drive and piping; ICF retains its gold target tooling and specialist controls. |
| Strand caster | Refractory tundish, copper mold/cooling pipes, support and powered withdrawal gears. |
| Chemical / petroleum equipment | Existing reactor/refinery vessel, heater, pump and control assemblies; crystallizer adds heat transfer, catalytic cracker and alkylation include feed circulation and controls. |
| Liquid/solid thermal processors | Vessels, heat-transfer/thermal coils, feed drives and controls; clay is not a universal melting/freezing catalyst. |
| Cryogenic column | Stainless containment, insulation, heat exchange, piping, circulation/compression drive and controls. No battery is required for ordinary grid operation. |
| Atmospheric collection / separation | Steel intake/receiver shells, powered compression drive, piping, wiring, scaffold support and Analog controls. The air separator adds stainless containment and heat-exchange hardware. The collector requires no titanium or petroleum reagent. |
| Isotope separation | Retained sulfur/exchange filters plus containment, copper contact hardware, circulation drives, piping, heat exchange, support and controls; sour-gas containers are not substitutes for these assemblies. |
| Large generators | Copper power cable, windings, casing, thermal/fluid hardware and mechanical assemblies. Gas-turbine duplicate shell entries are combined; Leviathan uses a consolidated high-temperature rotor assembly; combustion engines contain explicit reciprocating hardware. |

Copper dense cable is an existing generated material form, bundled from eight fine wires and rubber in the Assembler. It is registered with the same OreDict/material/rendering system as other dense conductors.

Crafting container keys use `FluidType.getCraftingContainerDict(legacyCapacity)` for the standard container class: current liquid canisters/tanks hold 20 L and barrels 200 L. Exact `getDict(quantity)` keys remain exact for external/configured recipes. Saved container quantities and fluid IDs are not rescaled. In particular, hydraulic pistons and powered condensers accept obtainable current lubricant containers rather than an absent 1000-L key.

## Information ownership

`IMachineInfo`, `MachineInfo`, `MachineInfoItems` and native owner getters supply item, GUI and NEI information. Live status comes from server snapshots; item previews use local defaults. Matching client/server builds are required. Display data cannot restore paid batch work. Proxy, dormant and decorative tiles receive no invented operating ratings.

The atmospheric collector, air-separation tower, small/large isotope separators, Compressor and industrial Electrolysis Machine expose their descriptions through `ITooltipProvider` and `ItemBlockBase`. Default native item profiles provide operating ratings for the atmospheric/isotope machines. Their existing block/item registry names and saved fluid identities are retained.

NEI has separate collection, atmospheric separation, isotope separation and Compressor handlers. Collection pages are environmental source descriptions, with the machine shown as retained infrastructure; gas availability follows registered celestial atmospheres, the 1-mbar presence threshold and the native bulk/selective collection rates. Compressor pages are rebuilt from the loaded compression/decompression registries for each lookup, with the actual pressure and work. Phase/steam conversion outputs and isotope separation use mass-backed icons so fractional amounts remain visible. Air separation delivers gases despite the tower's legacy Liquefaction name. These displays do not add processing recipes or alter machine ratings.

The atmospheric construction mapping follows the major assemblies described by [Atlas Copco's compressor component guide](https://www.atlascopco.com/en-us/compressors/air-compressor-blog/air-compressor-components) and [Air Products' separation-plant diagram](https://www.airproducts.com/-/media/files/en/33452-supply-options-air-separation-plant-poster.pdf): drive/compression element, containment, regulation, piping and heat-exchange/column hardware. Component counts are RealSim abstractions, not literal industrial bills of materials.

Compressor NEI headers distinguish compression, decompression, liquefaction and vaporization and show the network pressure transition. Tagged gas icons name their compressed/uncompressed state and pressure class; steam keeps its intrinsic thermodynamic identity. Decompression consumes its listed control work and recovers no energy. Missing GUI fluid art uses color-tinted neutral gas (`air.png`) or liquid (`water_base.png`) patterns, preserving bespoke/custom textures instead of defaulting newer gases to blue water.

The Compressor mode button sits below its upgrade slots, with a centered high-contrast label driven by server state. Button requests set an explicit mode, so duplicate clicks cannot toggle it back or reset progress repeatedly. Initial chunk snapshots include the mode, and control changes receive an immediate full status reply; clipboard settings use the same mode setter. Inventory coordinates and saved mode identity remain unchanged. Matching client/server builds are required for the additional description-packet field.
