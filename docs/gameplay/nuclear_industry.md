# Nuclear industry

Nuclear power grows from chemistry, metallurgy and manufacturing. Build heat removal and the electrical plant before loading fuel: the reactor body alone cannot supply electricity.

## Fuel and plant choice

Natural-uranium routes can support first nuclear power without enrichment. Enriched fuel, breeding and advanced reactors have different materials, controls and wastes. Use the specific reactor's fuel information rather than assuming every uranium item is loadable.

MAGNOX is graphite-moderated and CO2-cooled. Primary gas transfers heat to a secondary steam generator. Maintain cooling, feedwater, clear steam output and condensation. Shutdown rods stop fission over their travel time; stored and decay heat still need cooling. Interlocks and protection are in the [MAGNOX reference](../development/magnox_reference.md).

Default ReaSim RBMK operation uses Light Water and side-mounted inlet/outlet columns. Superhot Steam needs an Industrial Turbine or Leviathan. Route turbine exhaust directly to condensation and demineralize returned Fresh Water. Legacy RBMK modes have different fluid paths; check server configuration.

PWR supports Borated, Light and Heavy Water with matching hot/cold coolant paths. Pebble-bed, molten-salt, research and fusion systems have specialist fuel/coolant requirements rather than being interchangeable boiler upgrades.

## Enrichment and fabrication

Uranium feed is processed into yellowcake and fluoride feed for centrifuge enrichment. Each stage separates enriched material and depleted tails. Enriched feed still needs preparation, cladding and the correct rod/assembly. Depleted material and fluorine recovery are part of the chain.

Breeding changes nuclides through neutron exposure; enrichment separates an existing isotope mixture. Lithium targets supply tritium, but spent targets cannot repeatedly supply the original natural-isotope yield. Specialist isotopes also support RTGs and process irradiation.

## Spent fuel and waste

Hot PWR fuel goes through the Fuel Pool before chemical reprocessing. Cooled fuel retains its variant and hazards. Processing recovers represented uranium/plutonium and cladding with waste and radioactive raffinate. Conditioning/vitrification helps storage; it does not remove radioactivity.

Plan shielding, transport, carrier-liquid returns and waste outlets alongside fresh fuel. Do not store unshielded radioactive material in work/living areas or assume shutdown makes dismantling safe.

## Operating practice

Test new layouts in a creative copy. Monitor fuel, coolant, temperature, pressure, controls and outputs. Know which conditions trip automatically, which only warn and how to reset after correcting a fault. Retain power/cooling for shutdown. Reactor-specific game models and settings determine safety limits; real reactor figures do not replace displayed limits.

Read [Steam and heat](steam_and_heat.md), [Chemistry](chemistry.md) and [Materials](materials.md) for the supporting industry.
