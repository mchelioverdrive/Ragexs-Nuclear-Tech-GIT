# Materials and their industrial roles

RTM's elements, alloys and intermediates do not form one universal rarity ladder. Ordinary materials carry most construction; specialists matter where their properties suit the job. A rare element can be useful in a few processes without becoming necessary in every machine.

## Structural metals and steels

Iron, copper, steel and common alloys build frames, shells, pipes, tanks, motors and machinery. The Metallurgical Furnace supplies first steel and small alloy batches with poor fuel economy. The coke-fired Blast Furnace supplies bulk ore-to-steel production; forced air, hot blast, optional flux and oxygen improve operation.

The Blast Furnace directly produces steel, including refining inside that machine. It does not need a Large Arc Furnace afterward. Electric arc processing instead supports melting, recycling, steelmaking and advanced alloys. Choose by feed, heat/power and scale.

### Prepared iron and bulk steel

Use the Chemical Reactor templates **Iron Fines Oxidative Conditioning**, **Iron Ore Pelletizing**, **Iron Pellet Thermal Hardening**, and optionally **Hydrogen Direct Reduction of Iron**. The reactor supplies mixing and electric process heating; no additional machine block or external heater is needed. Its normal cooling and electrical requirements apply.

| Stage | Whole-batch inputs | Outputs | Electrical work / time |
| --- | --- | --- | --- |
| Condition fines | 8 iron powder (`dustIron`), 2 L water, about 2440 standard L oxygen gas | 8 conditioned oxide fines, recovered water and unused oxygen | 0.4 MJ / 10 s |
| Agglomerate | 8 fines, 1 limestone powder, 1 clay ball, 2 L water | 8 green pellets | 0.4 MJ / 10 s |
| Harden | 8 green pellets | 8 hardened pellets, CO2, discharged water vapor | 24.8 MJ / 20 s; 1200 C process reference |
| Optional reduction | 8 hardened pellets, about 4880 standard L hydrogen gas | 8 DRI, recovered water and unused hydrogen | 32.8 MJ / 30 s; 900 C process reference |

The four preparation stages total 70 seconds per eight-powder lot on one supplied reactor. Hardening draws 1.24 MW and reduction about 1.093 MW, plus cooling power; faster processing retains the complete energy bill. Reactor water electrolysis supplies hydrogen and oxygen continuously from 1 L fresh water in 20 seconds for the unchanged 55 kWh/kg hydrogen. The compact native Electrolyser retains its separate 20-kW water route.

Gas quantities use whole standard litres; NEI shows the exact admitted quantities. Fractional returned gases, water and CO2 remain in the saved process account. Iron powder is metallic in RTM: conditioning deliberately supplies oxide oxygen before the pellet reduction route. It does not silently treat metal powder as hematite. Each clay ball represents a 100-g binder dose (1.25% of the eight-powder charge). Limestone calcines during hardening; green pellets cannot feed either steelmaking route.

| Steel route | Furnace charge | Steel / slag | Furnace duty |
| --- | --- | --- | --- |
| Ordinary Blast Furnace baseline | 1 iron ore + 1 coal coke | 3 / 2 | Existing 18-MJ / 2700-standard-L blast bill |
| Loose powder | 2 iron powder + 1 coal coke | 2 / 0 | Existing 8-MJ / 1800-standard-L blast bill |
| Prepared Blast Furnace | 2 hardened pellets + 1 coal coke | 6 / 1 | 24 MJ process heat, 3600 standard L blast |
| Large Arc Furnace, solid mode | 8 DRI + 1 coal coke + 1 quicklime; three working electrodes installed | 32 / 2 | 57.6 MJ electricity, 500 kW base drive |

Prepared blast feed gives **3 steel per original iron powder**, halving coke per steel relative to the ordinary ore route and reducing slag per steel. Coke, hot blast and the existing qualified heater share the heat bill. Four prepared charges from eight powder produce 24 steel and 4 slag, using four coke plus the preparation supplies. The furnace retains optional oxygen enrichment and additional limestone flux.

The electric route gives **4 steel per original powder**. All four preparation steps plus arc melting cost **116 MJ electricity per eight-powder/32-steel lot**, before reactor cooling auxiliaries, hydrogen/oxygen production and transport. It also uses one coal coke, one limestone powder, one clay ball and one quicklime, plus water and electrode wear. The Large Arc Furnace uses RTM electricity only; its existing speed upgrades raise the drive to 1.5/2.5/3.5 MW without reducing total work. Its electrical buffer is 100 MJ. At full base power, melting takes 115.2 s plus normal lid and unloading delays; partial supply stretches that time.

Distribute DRI, coke and quicklime across the arc furnace's existing grid; its normal input-per-slot upgrade limits still apply. It starts only with all eight DRI and both additives, working electrodes, an empty molten buffer, a closed lid and room for both products. DRI takes priority over ordinary melting in solid mode. Paid charges persist through power loss, missing electrodes, blocked output, chunk unload and server restart; changing to molten mode is refused during an owned steel charge. Automation retains unprocessed DRI and additives; manually remove an incomplete loose charge with the lid open. Breaking the controller returns its reserved unfinished inputs, or its fully paid products, once; electrical work is never refunded.

The final 3x/4x recovery factors are intentional gameplay rewards for preparation, **not conservation-of-mass claims**. Preparation has explicit physical bills; the final furnaces use separate count-based recovery bills. The new intermediates have no iron-ingot/dust ore-dictionary aliases, ordinary smelting, grinding or reverse-crafting routes. Existing iron, steel, foundry recycling, alloy recipes and custom recipe overrides retain their ownership. Packs adding conversions or overlapping ore aliases must balance those themselves; enabled recipe overrides can replace stock defaults.

Chromium-bearing stainless steels serve corrosion-resistant equipment. High-speed and refractory alloys suit demanding tools and thermal service. Tin bronze is copper/tin; arsenic bronze is different. Alloy names describe composition and use rather than simply a better ordinary metal.

Stainless steel is obtainable before the Centrifuge. Recover Chromite Chunks with the Excavator, break them on a tier-2 anvil, then use **Chromite Aluminothermic Reduction** in the Chemical Reactor: two chromite and one aluminium powder produce ferrochrome, alumina and returned unused aluminium. The 20-second batch pays 4.2 MJ of electricity plus cooling; fractional products accumulate across batches. Smelt **six steel and two ferrochrome into eight stainless ingots** in the Metallurgical Furnace, then form the stainless plates for the Centrifuge Element. Ferrochrome is an iron–chromium alloy, not a substitute for pure chromium in other recipes.

## Conductors, solder and magnets

Copper is the ordinary conductor and generator winding material. Precious and exotic conductors have specialist roles rather than replacing every coil. Tin–lead solder joins electronic components: alloy tin and lead in the Foundry using the Tin-Lead Solder Production template, then cast eight wires per batch with a wire mold in a Shallow Foundry Basin. Solder wire is not made on an anvil. Supply it to the Soldering Station; an Integrated Circuit Board needs four Microchips, four Printed Circuit Boards and four solder wires. Raw germanium or an expensive magnet alloy cannot replace a finished circuit.

Rare earths and magnetic alloys support suitable magnets, optics and nuclear/material applications. Samarium–cobalt is distinct from structural steel. Raw ingots still need the proper component-making route.

For optional switching contacts, alloy one Osmium and one Iridium ingot-equivalent in the Foundry using **Osmium-Iridium Contact Alloy Production**, then cast two alloy ingots. One contact-alloy ingot or one Rhodium ingot, surrounded by eight Coated Red Copper Wires in the crafting grid, makes eight Cable Switches. The ordinary lever and mercury switch recipes remain available. The historical `Osmiridium` registry name still means refined Osmium; the contact alloy is a separate material.

## Refractory and specialist materials

Tungsten and other refractory materials serve high-temperature equipment. Titanium and high-performance alloys support specified pressure, rotating and aerospace hardware. Beryllium has neutron and aerospace uses; it is not a generic workshop metal. Radioactive and toxic materials need appropriate handling.

## Minerals and chemical intermediates

Ore, concentrate, oxide, salt, powder and finished metal are different process states. Hematite powder supplies oxide oxygen for thermite; elemental iron powder does not. Leaching, roasting and reduction prepare usable metal. Oxides and refining charges cannot always melt as finished ingots.

Slag, gangue, ash and spent chemicals are outputs. Mineral ash is not fuel or free carbon. Coal coke, lignite coke, petroleum coke, graphite and char have different fuel/process uses. Coal coke is the efficient Blast Furnace reductant; heating value alone does not make every carbon form acceptable there.

## Forms and casting

The Foundry melts, alloys and casts. Templates and molds select the operation. Nine nuggets make one ingot; a block contains nine ingots. Ore routes can also produce stone and valuable traces. Cast accumulated stone with a block mold in a Foundry Basin.

Left-click a molten layer to select the next output; right-click for a list including small quantities. The selection waits for a compatible receiver and lasts until exhausted or changed. Foundry recipe inputs remain reserved for their template.

## Nuclear and niche materials

Uranium, thorium, plutonium and individual isotopes have different fuel, breeding, shielding or heat-source roles. An ingot is not a cladded fuel assembly. Enrichment, fabrication, irradiation, cooling and reprocessing are separate stages; see [Nuclear industry](nuclear_industry.md). Exotic materials deliberately retain science-fiction rules.

Use NEI for your pack's route. Not every registered trace, old component or decorative material has a stock survival chain, and not every element needs a general-purpose application.
