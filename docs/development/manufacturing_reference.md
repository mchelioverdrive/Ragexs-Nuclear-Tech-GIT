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

Native water electrolysis pays 55 kWh/kg H2 at 200 kWe; the Chemical Reactor uses 8 kWe for the same chemistry. Fluid and metal Electrolyser lanes debit their work independently and share actual available electricity. Cryogenic work is recipe-specific and bounded by the current separator rate; air and geological-gas separation are 50 kWe /6,000 standard L/min. See current owners in [process reference](process_reference.md).

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

## Information ownership

`IMachineInfo`, `MachineInfo`, `MachineInfoItems` and native owner getters supply item, GUI and NEI information. Live status comes from server snapshots; item previews use local defaults. Matching client/server builds are required. Display data cannot restore paid batch work. Proxy, dormant and decorative tiles receive no invented operating ratings.
