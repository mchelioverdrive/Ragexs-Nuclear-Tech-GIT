# Steam and heat

A steam plant connects a heat source, boiler, converter, condenser and water return. More heat permits more flow; hotter steam permits more work per kilogram. The turbine rating is a ceiling, not a promise that any boiler can supply it.

## Steam conditions and converters

| Steam | Temperature / absolute pressure | Suitable converters |
| --- | --- | --- |
| Ordinary Steam | 100°C / about 0.101 MPa | Steam Engine, Small, Industrial, Leviathan |
| Hot Steam | 300°C / 1 MPa | Small, Industrial, Leviathan |
| Superhot Steam | 450°C / 4 MPa | Industrial, Leviathan |
| Ultrahot Steam | 600°C / 16 MPa | Leviathan |
| Low-Pressure Steam | 46°C / 10 kPa | Exhaust for condensation |

| Converter | Maximum electrical output | Maximum steam flow | Purpose |
| --- | ---: | ---: | --- |
| Steam Engine | 25 kW | 0.30 kg/s | Early ordinary-steam reciprocating generator |
| Precision Small Steam Turbine | 75 kW | 0.20 kg/s | Compact precision conversion through hot steam |
| Industrial Steam Turbine | 5 MW | 8 kg/s | Central high-flow conversion through superhot steam |
| Leviathan Steam Turbine | 25 MW | 40 kg/s | Extreme flow and ultrahot service |

All four exhaust directly to Low-Pressure Steam. Route it to condensation rather than another turbine. Changing a selector cannot make a smaller converter accept a hotter grade. Conversion factors (70/75/85/88%) describe usable expansion work, not the whole fuel-to-electricity plant efficiency.

Steam quantities represent water mass. Each ordinary/hot/superhot/ultrahot unit contains 0.01/0.1/1/10 kg of water; each exhaust unit contains 1 kg. Read tank mass when comparing grades. The pipe pressure class shown in PU is separate from these physical steam conditions.

## Heat sources and boilers

Fireboxes, fluid burners and electric heaters supply process heat. Externally heated machines expose deliberate bottom contacts: boilers, the Coker, Blast Furnace, Combination Furnace, Steel Furnace and Crucible, plus thermal drives such as the Stirling Engine and Sawmill. Center the upward-output heater directly under the bottom core with the full 3x3 layer below clear; a two-high Oil Burner needs its lower layer clear as well. Powered chemical/refinery vessels without these contacts supply their own added heating electrically.

The ordinary boiler accepts up to 200 kW of heat and makes ordinary steam. The Industrial Boiler accepts up to 12 MW and makes hot steam with electrical feed pumping. A Heating Oven supplies up to 500 kW of combined firing and imported heat; an ordinary boiler still limits that train to 200 kW. Use suitable larger boiling equipment for its full supply.

Industrial Boiler fuel firing also heats oil and compatible coolants, and can heat blast air. A sufficiently hot external heater can share the duty and reduce fuel use. Electricity pays the declared pumping work. Partial heat payments are retained when supply is too small for a complete process step; heating resumes when enough has accumulated.

Solar boilers depend on illuminated mirrors. The gas turbine's heat-recovery steam generator uses exhaust to make hot or superhot steam. Compression can raise grade but consumes substantial electricity and rejects heat; it is not free upgrading.

## Condensation and feedwater

Condensation returns Fresh Water. Passive condensers reject up to 250 kW of heat, radiators 1 MW and powered condensers 20 MW. Powered condensation also consumes electricity. Passive condensers need suitable atmosphere; radiators support vacuum. Hot environments can prevent cooling. Size cooling for rejected heat as well as electrical output.

Boilers lose 0.5% of processed water as blowdown/steam loss. Supply makeup while recirculating condensate. Direct-boiling reactor water may need demineralization before return; see [Chemistry](chemistry.md#water-quality).

## Blocked output and protection

The Steam Engine, Small and Industrial turbines pause when electricity or exhaust cannot leave. They resume when space returns. Full Small/Industrial tanks do not themselves trigger a rupture. Upstream boilers and reactors retain their own hazards.

The Leviathan has latched trip valves for exhaust backpressure. Correct the blockage, then reset with its lever or controller redstone. Electrical saturation pauses generation; it is not measured rotor overspeed. An installation retaining an older load-rejection trip needs the same reset interaction.

Keep fuel, water, load and condensation dependable before relying on continuous power. Read [Power generation](power_generation.md) for alternatives and [Nuclear industry](nuclear_industry.md) for reactor protection.
