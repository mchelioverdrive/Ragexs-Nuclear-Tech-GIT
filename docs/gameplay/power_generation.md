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
