# Fluids and chemistry

RTM's chemistry links water treatment, gases, mineral processing, petroleum and manufacturing. A useful plant needs the right material state, power, heat and outlets for the entire reaction. NEI shows your pack's active recipe; machine information explains operating limits.

The Chemical Reactor uses electrical process work and has no internal temperature or heater requirement. Hot liquids, molten feeds and steam must still arrive in the specified material state. Each recipe can use up to four item inputs, four fluid inputs, four item outputs and four fluid outputs. The template is retained. The workshop interface exposes each fluid channel with a gauge and container pair; the industrial interface shows all four inlet and outlet gauges for each lane.

NEI shows exact batch yields, duration and electrical work. Hover process feeds for pressure, flow and utility details. Other machines can require external heat: NEI reports the heat energy, rate and source temperature; fuel is consumed by that heat source. Rotary furnace jobs also show their firebox fuel energy separately from drive steam.

## Shared fluid pipes

A placed Universal Fluid Duct can carry up to four selected fluids. Right-click it with a fluid identifier to add that fluid; click with the same identifier again to remove it. Removing an assignment does not drain or alter any machine tank. A fifth assignment is rejected. Shared pipes use the original pipe model and its first selected fluid color; the pipe overlay lists every assigned fluid.

For a shared trunk, add each required identifier to its pipes. Sneak/CTRL-click applies the same change to connected pipes with the same fluid set, within the existing 64-block range; differently filtered branches stop propagation. Build a branch with only one identifier to carry only that fluid from the trunk to its destination. Fluids and network pressure remain separate, and closed valves stop every channel. Pipe overlays list all assigned fluids. A shared flow gauge retains its first-channel reading and labels which fluid it meters.

The Chemical Reactor's four utility positions leave three available for fluid piping when one is occupied by power. Use shared trunks to carry its full feed/coproduct set through those available connections; the internal gauges and container pairs are storage channels, not additional physical pipe sockets.

## Water quality

**Water** is raw environmental water for washing, concrete and rough service. Chemical Reactor treatment turns 50 kg into 45 kg of **Fresh Water** and 5 kg of mineral brine over 50 seconds. Drain both products. The default feed rate is 60 L/min.

**Fresh Water** supplies boilers, chemistry and closed industrial cooling charges. Demineralization polishes 10 L into 10 L of **Light Water** in 50 seconds. Light Water supplies direct-boiling reactor systems and deuterium extraction. Steam condensate is fresh water; polish it again where a reactor needs light water.

**Borated Water** contains 1 wt% boric acid, about 1,748 ppm elemental boron. Make the acid precursor before preparing coolant. The PWR also supports light and heavy water with different operating behavior. Hot coolant needs the correct cooling route; refilling does not remove heat.

**Heavy Water** requires isotope separation. Both extractor sizes supply separation and heating electrically. Extraction produces depleted reject that cannot simply be passed through again as natural feed.

## Reading fluid quantities

Ordinary liquids use litres. Gases use **standard litres (0°C, 1 bar)**, so compression changes conditions without creating gas. Liquid hydrogen, oxygen and nitrogen are cryogenic liquids distinct from warm process gases. Cooling and vaporization need their own equipment and work.

Steam shows water mass and conditions; see [Steam and heat](steam_and_heat.md). Molten metals, nuclear salts and a few concentrates use small weighed units rather than ordinary litres. Hovers show their mass per unit. Compare the actual fluid and recipe rather than assuming every displayed count means the same volume.

## Early gunpowder for Deadhour

The Chemical Plant/Reactor retains both biological nitre beds as intentional gameplay abstractions. Template **1143 `NITRE_BED`** consumes eight `treeLeaves`, two fertilizer and **1 L fresh water (1,000 mB)** for eight niter. Template **1144 `NITRE_BED2`** substitutes one biomass for the leaves. Both take **1,200 ticks / 60 seconds at 20 TPS**, paying 60 kJ at 1 kW through the existing fixed-work recipe system. Speed upgrades do not shorten these cycles. These are work-only gameplay bills, not mass-balanced nitrate synthesis; industrial nitrate exchange remains available alongside them.

Make two fertilizer by shapelessly crafting **one bone meal + one rotten flesh + one dirt block**. Bones from skeletons, zombie flesh and dug dirt require neither nitrate nor industrial reagents. Leaves are obtainable with vanilla shears. For the biomass variant, the existing craft makes four biomass from sugar, two sawdust and six leaves; reeds supply sugar and the Shredder makes four sawdust per log. The existing four-flesh biomass craft is another option. Raw environmental water goes through reactor template 1145 to fresh water, with mineral brine as a coproduct; this treatment has no reagent input or external heater requirement. The existing workshop machine and power supply are still required.

Sulfur ore still generates in the Overworld by default and drops two to four sulfur when mined. The restored Centrifuge recipe also turns **one copper crystal into four copper powder + one sulfur + one tiny cobalt powder**, matching its historical products. This supplementary machine route needs the existing Leaching Reactor/Crystallizer: copper ore plus its recovered peroxide carrier produces the crystal. Peroxide comes from reactor hydrogen/oxygen and fresh water; workshop water electrolysis supplies both gases. The Crystallizer retains its existing titanium/Desh/rubber construction requirements, so mining sulfur is the earlier option. Neither route requires petroleum, hydrotreating or Claus recovery. Existing sulfur-crystal processing and industrial recovery remain useful for larger yields and bulk coproduct recovery.

Shapelessly craft **one sulfur + one niter + one coal or charcoal into three vanilla gunpowder**. `dustSulfur` and `dustSaltpeter` accept the native sulfur and niter items. Charcoal can be made by vanilla log smelting. This chain has no fertilizer/niter or sulfur/acid bootstrap loop.

Defaults appear in NEI and the template folder. Enabled `hbmChemplant.json` and `hbmCentrifuge.json` files replace their entire registries; they are not merged or overwritten. Existing override users must copy the restored entries from the regenerated underscore templates, preserving unique IDs. Current liquid counts are litres: use `FRESH_WATER` quantity **1**, not the old pre-conversion value 1000. Retain `duration: 1200` and `physicalPowerQuanta: 100` for the default fixed-work nitre beds. Their bill-less JSON entries are accepted again; unrelated physical-bill validation remains active. The compost crafting addition requires a restart.

## Reaction and separation equipment

The **Chemical Reactor** handles reaction, dilution, controlled mixing and compact multistage chemistry. Its physical recipes supply process work and added heating electrically; process-temperature references do not require a separate heater. Its industrial counterpart runs eight lanes with a sealed fresh-water cooling charge and shared heat-rejection limit. More lanes increase throughput without unlocking a hidden chemistry tier.

The **Electrolyser** separates compounds with electricity. Water produces hydrogen/oxygen gas in a 2:1 molar ratio. Early reactor electrolysis is slower; the native electrolyser increases production with a 20-kW water duty. Reactive metals need appropriate dry or molten salts: an aqueous sodium salt does not directly yield sodium metal.

The **Compressor** prepares gas pressure or changes steam grade with paid work. The **Cryogenic Separator** separates compatible mixtures and recovers atmospheric/geological gases. The **Leaching Reactor** prepares crystals and concentrates with a working bath. Filtration and regeneration return the liquid instead of consuming a tank of acid per crystal. Efficiency saves electricity, not raw material.

## Acids, salts and minerals

Common strengths are hydrochloric acid 30 wt%, nitric acid 65 wt%, sulfuric acid 98 wt% and hydrofluoric acid 48 wt%. Water, unused reagent, salts and residues can be outputs. Leave room and plumbing for them before automating feed.

Manufactured salt brine supplies chlor-alkali chemistry. Geological mineral brine also supplies bromine and iodine; adding table salt to water cannot supply those elements. Evaporation needs heat and produces steam. Lithium, coltan, bauxite and rare-earth chains connect leaching, separation, oxide/halide preparation and reduction. Concentrate is not finished metal.

## Gases and chemical manufacturing

Nitrogen/hydrogen supply ammonia; hydrogen also supports refining. Methane reforming and coal gasification supply synthesis gas for methanol and other routes. Raw natural gas contains methane and helium; separation supplies both without requiring space travel.

Prepared monomers feed plastics. Rubber, PVC, phenolic resin, polycarbonate and aramid have different reagents and coproducts. Sulfur, chlorine and aromatic intermediates matter because they are part of the chemistry. Fuel mixtures are covered in [Petroleum](petroleum.md).

Common prerequisites run in useful industrial lots: polyethylene uses 8,000 standard L monomer per 100-second cycle, water treatment handles 50 L per cycle, and basic acid/base feeds use the same 60-L/min plumbing allowance as water. The existing reactor cooling system supports these lots without an extra coolant chain; the workshop cooler rejects up to 1 MW and the industrial plant shares 5 MW across its eight lanes. Cooling fans and pumps still consume electricity. Oil, nuclear chemistry and specialty materials retain their separate process constraints.

Bulk construction deliberately uses Minecraft building quantities: concrete and fibre concrete produce four stacks per 80-second cycle, and asphalt four stacks per 240-second cycle. See [construction recipes](../construction-recipes.md) for the complete supply chain and rates. Stored stock is unchanged, and a batch already in progress finishes its original paid recipe. Enabled recipe overrides must be updated from the generated templates to adopt the new defaults.

Follow hazard labels for toxic gases, corrosive acids, hot melts and cryogens. Combustion needs suitable atmosphere and ventilation; electrical processing can still need atmosphere for cooling or shielding gas for the operation.
