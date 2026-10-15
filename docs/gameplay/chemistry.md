# Fluids and chemistry

RTM's chemistry links water treatment, gases, mineral processing, petroleum and manufacturing. A useful plant needs the right material state, power, heat and outlets for the entire reaction. NEI shows your pack's active recipe; machine information explains operating limits.

## Water quality

**Water** is raw environmental water for washing, concrete and rough service. Chemical Reactor treatment turns 10 kg into 9 kg of **Fresh Water** and 1 kg of mineral brine over 50 seconds. Drain both products. The default feed rate is 12 L/min.

**Fresh Water** supplies boilers, chemistry and closed industrial cooling charges. Demineralization polishes 10 L into 10 L of **Light Water** in 50 seconds. Light Water supplies direct-boiling reactor systems and deuterium extraction. Steam condensate is fresh water; polish it again where a reactor needs light water.

**Borated Water** contains 1 wt% boric acid, about 1,748 ppm elemental boron. Make the acid precursor before preparing coolant. The PWR also supports light and heavy water with different operating behavior. Hot coolant needs the correct cooling route; refilling does not remove heat.

**Heavy Water** requires isotope separation. Extraction produces depleted reject that cannot simply be passed through again as natural feed.

## Reading fluid quantities

Ordinary liquids use litres. Gases use **standard litres (0°C, 1 bar)**, so compression changes conditions without creating gas. Liquid hydrogen, oxygen and nitrogen are cryogenic liquids distinct from warm process gases. Cooling and vaporization need their own equipment and work.

Steam shows water mass and conditions; see [Steam and heat](steam_and_heat.md). Molten metals, nuclear salts and a few concentrates use small weighed units rather than ordinary litres. Hovers show their mass per unit. Compare the actual fluid and recipe rather than assuming every displayed count means the same volume.

## Reaction and separation equipment

The **Chemical Reactor** handles reaction, dilution, controlled mixing and compact multistage chemistry. Some recipes need external heat at a specified temperature. Its industrial counterpart runs eight lanes with a sealed fresh-water cooling charge and shared heat-rejection limit. More lanes increase throughput without unlocking a hidden chemistry tier.

The **Electrolyser** separates compounds with electricity. Water produces hydrogen/oxygen gas in a 2:1 molar ratio. Early reactor electrolysis is slower; the native electrolyser increases production with a 200-kW water duty. Reactive metals need appropriate dry or molten salts: an aqueous sodium salt does not directly yield sodium metal.

The **Compressor** prepares gas pressure or changes steam grade with paid work. The **Cryogenic Separator** separates compatible mixtures and recovers atmospheric/geological gases. The **Leaching Reactor** prepares crystals and concentrates with a working bath. Filtration and regeneration return the liquid instead of consuming a tank of acid per crystal. Efficiency saves electricity, not raw material.

## Acids, salts and minerals

Common strengths are hydrochloric acid 30 wt%, nitric acid 65 wt%, sulfuric acid 98 wt% and hydrofluoric acid 48 wt%. Water, unused reagent, salts and residues can be outputs. Leave room and plumbing for them before automating feed.

Manufactured salt brine supplies chlor-alkali chemistry. Geological mineral brine also supplies bromine and iodine; adding table salt to water cannot supply those elements. Evaporation needs heat and produces steam. Lithium, coltan, bauxite and rare-earth chains connect leaching, separation, oxide/halide preparation and reduction. Concentrate is not finished metal.

## Gases and chemical manufacturing

Nitrogen/hydrogen supply ammonia; hydrogen also supports refining. Methane reforming and coal gasification supply synthesis gas for methanol and other routes. Raw natural gas contains methane and helium; separation supplies both without requiring space travel.

Prepared monomers feed plastics. Rubber, PVC, phenolic resin, polycarbonate and aramid have different reagents and coproducts. Sulfur, chlorine and aromatic intermediates matter because they are part of the chemistry. Fuel mixtures are covered in [Petroleum](petroleum.md).

Follow hazard labels for toxic gases, corrosive acids, hot melts and cryogens. Combustion needs suitable atmosphere and ventilation; electrical processing can still need atmosphere for cooling or shielding gas for the operation.
