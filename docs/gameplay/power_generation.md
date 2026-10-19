# Power generation

Power in RTM is a choice of fuel, heat source and supporting plant. A generator's electrical rating is its maximum output; it still needs enough fuel or steam, a clear exhaust path and somewhere for the electricity to go. Ratings below are fresh defaults. The placed machine shows the server's installed values.

## Choosing a branch

| Branch | Typical electrical output | Why build it? | Supporting infrastructure |
| --- | --- | --- | --- |
| Stirling | 10 kW starter; 40 kW heavy | Simple workshop power from external heat | Compatible heater below and dependable heat supply |
| Steam Engine | Up to 25 kW | Early reciprocating steam power | Ordinary steam, condensation and water return |
| Precision Small Steam Turbine | Up to 75 kW | Compact precision conversion of ordinary/hot steam | Better mechanical parts, sufficient boiler heat and condenser |
| Industrial Steam Turbine | Up to 5 MW | Central conversion through Superhot Steam | Large boilers or reactor steam, pumping and substantial cooling |
| Leviathan | Up to 25 MW | Extreme flow and Ultrahot Steam service | Large steam plant, controls and protected exhaust handling |
| Diesel Generator | 200 kW | Compact automatic factory supply | Prepared diesel-family fuel and external storage |
| Industrial piston engine | Up to 750 kW | Throttled generation with interchangeable piston sets | Compatible fuel and continuous delivery |
| Gas Turbine with HRSG | 3 MW gross; 2.94 MW after turbine auxiliaries | Continuous gas power with exhaust recovery | Clean compatible gas, lubricant, startup control; water/steam plant for recovery |
| Jet-Derived Turboshaft Generator | 1 MW | Aviation-fuel branch | Aviation-grade fuel; afterburning wastes fuel without raising electrical rating |
| Solar panel | Around 1 kW peak in ordinary sunlight | Distributed fuel-free power | Sky access, daylight and backup for darkness |
| Geothermal contact generator | Up to 10 kW | Small site-dependent supply | Suitable lava/geyser contacts |
| RTGs | Source-dependent, watts to low kilowatts | Long-lived remote or specialist supply | Isotope source and radioactive-material handling |

## Heat engines and early power

The Starter Stirling converts up to 66.67 kW of heat into 10 kW electrical output at 15% conversion. The Heavy Stirling accepts up to 200 kW of heat and produces 40 kW at 20%. Both reject waste heat through an integral radiator. A colder heat source can limit conversion; a larger fuel store does not increase output.

Steam gives you a route from boilers to increasingly capable converters. A 200-kW heater/ordinary-boiler/Steam Engine/passive-condenser example supplies about 13 kW, below the engine's 25-kW nameplate. A sufficiently supplied Small Steam Turbine makes 37.5 kW on ordinary steam and can reach 75 kW on hot steam. Read [Steam and heat](steam_and_heat.md) before choosing conversion and cooling equipment.

## Liquid fuels and piston sets

Diesel has 43 MJ/kg, about 35.8 MJ/L at 0.833 kg/L. The Diesel Generator converts 32% to electricity on ordinary diesel. Its 20-L internal supply lasts roughly 19 minutes at full output; external tanks provide sustained autonomy.

The industrial piston engine's installed set determines compatible fuels and efficiency. High-grade fuel efficiencies range from 9.5% for the steel set to 38% for the best represented sets. Gasoline, kerosene, process gas and heating oil are not interchangeable just because all contain chemical energy. Use the machine's fuel list and [petroleum guide](petroleum.md).

## Gas turbines and combined cycle

The gas turbine burns approved methane/refinery/reform/coker/synthesis gas. It requires lubricant (0.06 L/min), reaches operating speed through startup and suits continuous supply. Its 6,000-standard-L gas tank is a service reserve: about 21 seconds at full methane load, not a substitute for a pipeline.

At full output it converts 30% of fuel energy directly to electricity and can recover much of the exhaust as process heat. The HRSG makes hot or Superhot Steam when exhaust temperature permits. Add an external Industrial Steam Turbine, powered condenser and closed water return to use it. One representative complete cycle produces about 3.91 MW net at 39.1% overall fuel efficiency. Recovery needs feedwater, electrical pumping, clear steam outputs and enough condensation. It cannot produce Ultrahot Steam.

## Nuclear-fed steam

A reactor supplies the steam branch. Select a turbine that accepts its steam grade and build condensation and water treatment before startup. A Small Steam Turbine cannot accept Superhot Steam. Reactor-specific protection and coolant requirements are in [Nuclear industry](nuclear_industry.md).

## Environmental, isotope and exotic sources

Solar output varies with daylight, sky obstruction and the celestial environment. Geothermal equipment depends on its site. Solar-thermal equipment instead feeds the steam branch and needs mirrors, feedwater, conversion and cooling.

RTGs convert 5% of isotope heat into electricity; source choice and decay matter. A fresh sealed 2-kg Pu-238 Mini RTG produces about 57 W; Tm-170 starts around 1.35 kW with different decay behavior. These suit remote loads rather than whole factories.

Radiation engines, matter conversion, ZPE shafts and fictional laser power retain specialist science-fiction roles. Their structures, inputs and hazards differ; ordinary combustion efficiencies do not describe them. The old Wood Generator is retained for existing installations; use Stirling or steam for a new starter. A flare disposes of gas and produces no electricity.

## Sizing a grid

Plan average supply, simultaneous peak load and storage discharge power separately. An 8-kW furnace, 2-kW press and 3-kW Fabrication Machine can overwhelm one starter if run together. Stagger jobs, add generation or provide a battery able to supply the peak. Generators have little internal electrical storage; external batteries bridge interruptions. Fuel delivery, clear byproducts and dependable cooling matter as much as the nameplate.

## Cable Drum and overhead wires

Right-click two loaded poles or connectors with the Cable Drum. Each completed attempt clears the selection; select the start again after an error. A selection identifies its dimension and the particular placed pole, so breaking and replacing that pole requires selecting it again. Existing established wires belong to the poles, not the drum.

Ordinary distribution poles connect directly regardless of model or material. Single/triple/quadruple labels describe visible wire mounts, not voltage ratings. RTM does not simulate nominal voltage, insulation, phase balance or wire losses; the two service classes below preserve the largest transmission pylon's existing substation requirement without inventing voltage values.

| Registered infrastructure | Wire service | Mounts | Maximum span | Permitted wire peers / local cable access |
| --- | --- | --- | --- | --- |
| Electricity Connector (`red_connector`) | Distribution | Single | 10 m | Any distribution pole/connector; local cable on its mounting face |
| Electricity Pylon (`red_pylon`) | Distribution | Single | 25 m | Any distribution pole/connector; local cables around its base |
| Medium Wooden Electricity Pylon (`red_pylon_medium_wood`) | Distribution | Triple | 45 m | Any distribution pole/connector; no local cable tap |
| Medium Wooden Electricity Pylon with Transformer (`red_pylon_medium_wood_transformer`) | Distribution | Triple | 45 m | Any distribution pole/connector; local cable opposite its facing |
| Medium Steel Electricity Pylon (`red_pylon_medium_steel`) | Distribution | Triple | 45 m | Any distribution pole/connector; no local cable tap |
| Medium Steel Electricity Pylon with Transformer (`red_pylon_medium_steel_transformer`) | Distribution | Triple | 45 m | Any distribution pole/connector; local cable opposite its facing |
| Large Electricity Pylon (`red_pylon_large`) | Transmission | Quadruple | 100 m | Large pylons and substations; reach distribution through a substation's cable ports |
| Substation (`substation`) | Transmission wire terminal / cable interface | Quadruple | 20 m | Large pylons and other substations; existing ground cable ports connect to the ordinary grid |

Each span uses the shorter endpoint's limit, measured between its attachment points. Mixed single/triple poles draw the supported common mount count. Self-connections and duplicate spans are rejected. There is no separate branch-count or wire-throughput cap in the existing system; generator, consumer and cable behavior is unchanged. A wire never supplies a voltage conversion. Medium transformer variants retain their existing cable tap; they do not impose a substation requirement on ordinary overhead links.

Breaking a connected endpoint removes its wires while preserving unrelated branches. Unloaded peers are repaired from a saved removal journal when they next attach; cleanup does not load their chunks. Existing coordinate-based pole saves remain readable. Duplicate or malformed wire records and loaded one-sided orphan records are discarded without recreating blocks.


## When electrical demand disappears

Stirlings continue drawing supplied heat. Steam Engines and small turbines continue expanding steam while their spent-steam outlet has room. Unused output dissipates instead of accumulating in a hidden battery. Stop their heat/steam supply deliberately to save input.

Industrial steam turbines close controlled intake without electrical/exhaust room; Leviathan retains its separate pressure protection/reset. Diesel governs down but still burns idle fuel. The industrial piston engine follows manual throttle; Turbofan keeps firing. Gas turbine retains minimum running fuel, lubrication, paid startup/shutdown and finite exhaust recovery. Isotope decay continues regardless of electrical demand. Material-conversion/custom batches follow their own contracts.

Ordinary generators need a separate battery for stored electricity. Electric furnace has a two-tick working reserve (800 J at default); its slot battery supplies the heater and is not charged there. Old saved excess drains without deletion. See the [generator reference](../development/generator_reference.md#saturation-and-physical-operation) for the full behavior/construction table.
