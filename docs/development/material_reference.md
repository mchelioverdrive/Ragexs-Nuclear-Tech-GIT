# Material and form reference

Current material roles, form accounting, item acceptance and saved-ID rules. Enabled recipes and exact item distributions take precedence. For ordinary uses see [Materials](../gameplay/materials.md).

## Shape quantities

Foundry proportions: quantum 1; nugget/tiny 8; billet 48; ingot/dust/gem/crystal/plate 72; block 648. These are ingot-equivalent shape quantities, not kilograms, fluid litres or joules. Ore shape has zero generic quantity until an exact composition is supplied. Native refining charges can have separate local mass assays.

## Authoritative boundaries

- `MaterialRole` distinguishes industrial functions. `NTMMaterial` exposes explicit applicable roles for every foundry identity; roles are not universal recipe substitutions. A nuclear-material role does not make an ingot a reactor assembly, and a magnetic role does not make an ingot an electronic component.
- `MaterialShapes` owns shape quantities and prefixes independently of material initialization. One ingot-equivalent is 72 quanta; these quanta are not kilograms or chemical joules. Generation flags only retain shapes with actual native metadata generators. `hasForm()` checks actual registered aliases rather than interpreting a generation flag as an existing form.
- `Mats` resolves exact item assays first, then explicit ore assays, then positive generic form quantities. Exact assays replace generic matching instead of adding a second inventory. Returned assays are copies. Unassayed ores are rejected; their zero-valued shape cannot destroy an item. Dedicated native refining charges remain outside generic melting.
- `PhysicalRegistry` owns the adopted mass, phase and LHV of assayed substances. `SolidFuel` owns one-item identity and quantity, including native metadata and external form aliases. Native identities precede aliases; conflicting external identities or form quantities are rejected. Stack size is inventory count, not fuel content per item.
- `FuelSuitability` owns distinct equipment/process roles. `Combustion` applies both policy and fluid traits. Burner acceptance, engine acceptance, shaft-furnace burden and coking feed remain separate checks. Existing chemical process recipes own their exact feed bills and coproducts.
- Furnace burn/work counters are compatibility units. RTM's fuel handler/cache and Metallurgical Furnace derive fresh solid-fuel counters from assay energy relative to coal, without interpreting ticks as joules. Vanilla's internally handled fuels and external furnace integrations retain their own legacy counter contracts. RealSim heaters/generators use mass times LHV directly.

Material roles do not impose manufacturing tiers.

## Material families

| Family | Identity / role | Uses | Limited uses | Forms and acceptance |
| --- | --- | --- | --- | --- |
| Ordinary structural metals | Fe/Al/Ti/Cu and manufactured solids | Frames, castings, plates, conductors, fittings | Existing application selection retained | Positive quantities and actual form registrations govern acceptance |
| Structural/tool alloys | Steel, stainless, HSS, bronze, tool steel | Foundry alloying, forming, high-strength components | Different alloys need not receive equal recipe counts | Both normal and RTG furnaces honor declared input counts and output room |
| Refractory/specialty metals | W/Ta/Nb, platinum-group metals | High-temperature components, capacitors, contacts and specialist processes | Rare contacts/catalysts remain specialist rather than general structural demand | Dedicated Rh/Ir refining retained; generic names do not override exact arc bills |
| Conductors and solder | Cu/Al/Au conductors; Sn/Pb solder alloy | Wire, coils, conventional circuit assembly | Gold retains selected contact/conductor uses; not substituted everywhere for copper | New solder material ID 24002, wire metadata and Foundry template 24 |
| Light metals/alloying additions | Mg/Si/B/Cr/Ni/Co/Zn and others | Alloying, reduction, semiconductor feed, specialist components | Additions are chosen for a function, not progression symmetry | Persistent magnesium ID remains 1500; precursor gates are explicit |
| Rare earth/magnetic/optical materials | Nd/Sm/Co magnets; lanthanide separation and optical feed | SmCo motors, doped optical crystals, rare-earth chemistry, neutron-related Gd applications | Gd/Tb/Tm and trace lanthanides remain specialist | Cr/Sm/late Gd charges require dedicated preparation; Sm fragment stays registered outside nugget casting |
| Semiconductors/electronic materials | Si/Ge/GaAs/In and component states | Chip preparation, circuits, optical/electronic intermediates | High-purity or optical materials do not gain structural recipes | Existing press, crystallizer and chemical preparation retained |
| Carbonaceous fuels/reductants | Coal/lignite/coke grades, charcoal, wood, biomass, graphite/char | Heating, shaft reduction, coking, gasification, pyrolysis | Graphite/char carbon roles do not imply stock heater suitability | Four coke items become three graphite ingots; four sawdust items make a wood briquette |
| Mineral/oxide/sulfide/halide feeds | Hematite, mineral concentrates, salts and oxides | Beneficiation, leaching, roasting, reduction, flux | Mineral parcels need not have independent final applications | `dustHematite` and `dustAntimonyTrioxide` distinguish states; unmapped ores rejected |
| Chemical intermediates and process gases | Exact compounds, solutions, precipitates and typed gases | Existing Chemical Reactor, electrolyser and refinery chains | Intermediates can exist solely to carry a process state | Assayed physical process feed requires matching identity and per-item quantity |
| Nuclear/isotope families | Bare metals vs enriched/fueled/irradiated assemblies | Enrichment, fuel preparation, rods, reprocessing, isotope heat and shielding | Exotic/experimental isotopes remain narrow | IDs, metadata, fuel chemistry and assembly inventories preserved; no nuclear calibration |
| Wastes/residues | Mineral ash, slag, soot/char, depleted materials | Recovery, disposal and represented coproducts | Waste does not need synthetic gameplay demand | Ash grades rejected as heaters; char/soot identity remains distinct |
| Natural gem/organic forms | Emerald, diamond, wood/bone/polymers | Gems, cutting/abrasives, carbon conversion, fittings and existing chemistry | Gems need not provide an alternative industrial metal source | Existing emerald items/metadata retained; no direct castable Be bypass |
| Legacy/reference forms | Old/new bedrock grades, unused wires and heavy components | Active historical ore processing and external/configured recipe surfaces | Creative/debug, legacy-save and external-only identities remain registered | No deletion of items based on source non-use; disconnected new bedrock source remains a documented world-generation limitation |
| Explicit fictional materials | Starmetal, schrabidium family, alien/exotic conductor/alloy rules | Existing sci-fi progression | Exotic rules remain explicit rather than masquerading as real chemistry | Their saved IDs/items and gameplay transformations remain intact |

## Exceptions and compatibility

| Material/form | Result and reason |
|---|---|
| Red phosphorus / magnesium | New phosphorus material metadata is 1531; ID 1500 retains magnesium, the historical final `matById` binding. No blanket rewrite of ID 1500 inventories. Existing molten magnesium, scraps and generated Mg forms retain their identity/quantity. Old bare fragments intended as phosphorus cannot be distinguished from Mg fragments; their historical decoding is preserved. Ordinary non-metadata phosphorus items are unchanged. Duplicate persistent IDs now fail registration instead of silently replacing a material. |
| Chromium / samarium / gadolinium | Native powder/parcel names represent dedicated preparation charges. Their existing arc bills remain; generic crucible melting does not bypass them. Exact configured item assays can deliberately override this gate. This is a processing abstraction, not a claim that the existing arc charge has a calibrated elemental reduction bill. |
| Rhodium / iridium / thulium / impure terbium | Dedicated native preparation retained. Exact item arc recipes outrank generated aliases. Finished metal forms can remelt normally. |
| Samarium fragment | Its one-parcel chemical refinement is retained. Removing native `nuggetSamarium` prevents casting one ingot into nine refining parcels. External genuine samarium nuggets remain external metal forms. |
| Tin–lead solder | 5 Sn nugget-equivalents +3 Pb nugget-equivalents →8 solder nugget-equivalents. This is an exact foundry-quantity bill, not a measured 63/37 mass composition: the general foundry units have no universal density. Fine wire remains 9 q/item. Native solder has wire support, without fictitious ingot/dust/block generators. |
| Tin bronze | New material ID 51; template ID 23 retains its Cu/Tin/flux bill but produces tin bronze, not arsenic bronze without arsenic. Existing arsenic-bronze inventories remain unchanged. Native ingot/cast plate/light barrel/receiver forms join the existing bronze substitution group. This is a practical alloy identity correction, not a recipe-demand reward. |
| Calcined dolomite | Same ID 44; CaO/MgO-bearing reaction additive. Existing magnesium reduction template and chemical route remain available; no generic metal casting. |
| Hematite powder | One native hematite parcel →one oxide powder in the Crusher. Three powders +one Al powder →four thermite mixture items. Grinding/mixing and recipe batch quantities are compressed abstractions; metallic Fe no longer supplies nonexistent oxygen. |
| Antimony trioxide | Registered under its oxide key; exact roasting/reduction consumers remain. No generic elemental powder alias. Existing item registry name remains unchanged. |
| Beryllium | Existing nuclear/aerospace uses retained. Generic emerald remelting removed; no new arbitrary heat-exchanger or machinery demand introduced. |
| Uranium/GT6 compatibility | Existing GT6 material naming and native loader detection are retained. Shape initialization no longer triggers material initialization. No new cross-mod oxide/pure-metal equivalence is asserted. |
| Blade/stamp salvage | Audit MB04 documented lower recovery than cast cost; no loss intent was established. The existing conservative salvage loss is retained, rather than increasing old player stock based on an assumption. |
| Ordinary solid fuel / presto / infernal coal / fuel crystals / rocket fuel | Legacy furnace-counter identities remain where already implemented. No physical mass/LHV is inferred from those ticks. Ambiguous/exotic items remain rejected by physical heaters. |

Enabled `hbmCrucibleSmelting.json`, `hbmCrucible.json`, `hbmArcFurnace.json`, `hbmBlastFurnace.json`, `hbmSoldering.json` and other existing recipe overrides retain their loader/IMC boundaries. Stock changes do not rewrite enabled server files. Exact item distributions now replace aliases; a customization that previously depended on accidental additive double-credit must provide its complete assay explicitly. Existing fuel efficiency/time/heat configuration fields remain compatibility controls, not independent chemical-energy assays. Stored fuel credit, process work and inventory amounts are not rescaled. Water-treatment/demineralization defaults respect the 12 L/min reactor feed boundary. Resolved ethanol/water coolant retains chemical energy without heater suitability.

## Combustion and process matrix

Mass and LHV below are adopted gameplay assays from `PhysicalRegistry`, not measurements of the Minecraft object. Each column describes one inventory item or one fluid inventory unit. Heater acceptance also requires applicable fluid traits.

| Identity/family | Relevant forms | Adopted mass | LHV | Heater | Engine/process suitability | Storage multiplier / exclusions |
|---|---|---:|---:|---|---|---|
| Coal | Lump, powder, briquette, block | 1 kg/item | 25.8 MJ/kg | Yes | Coal gasification/liquefaction/coking; coal shaft bill | Block 9; one block cannot pay a one-item process bill |
| Charcoal | Vanilla charcoal; explicit external powder/forms | 1 kg/item | 29.5 MJ/kg | Yes | Carbon/reductant role; not coal coking feed or current shaft bill | Explicit external block 9 |
| Lignite | Lump, powder, briquette | 1 kg/item | 18 MJ/kg | Yes | Lignite coking feed; not current shaft bill | Explicit external block 9 |
| Coal coke | Lump/powder/native storage block | 1 kg/item | 28.2 MJ/kg | Yes | Coal-coke shaft burden; carbon additive | Block 9; generic coke aliases only provide a fallback identity |
| Lignite coke | Lump/powder/native storage block | 1 kg/item | 25 MJ/kg | Yes | Carbon additive; not coal-coke shaft burden | Block 9; grade survives generic coke alias |
| Petroleum coke | Lump/powder/native storage block | 1 kg/item | 32.5 MJ/kg | Yes | Carbon additive; not coal-coke shaft burden | Block 9; `PetCoke`/`PetroleumCoke` aliases recognized |
| Wood | Plank, log, stick, sawdust, briquette, sapling | Plank/sawdust/sapling 1 kg; stick 0.5 kg; log/briquette 4 kg | 15.6 MJ/kg | Yes | Explicit log/briquette/sapling pyrolysis/coking bills | Four planks/sawdust equal log/briquette mass; arbitrary wooden objects/books excluded |
| Biomass | Native biomass and one-to-one pressed compressed biomass | 1 kg/item | 15.6 MJ/kg | Yes | Explicit biomass digestion bill | Compression does not double mass or energy; no biogas-to-biomass cooling recipe |
| Carbon char / soot | Process char coproduct (`powder_ash:SOOT`) | 1 kg/item | 32.8 MJ/kg | No | Carbon additive, retained process residue | Heating suitability deliberately withheld |
| Mineral ash | Wood/coal/misc ash; physical ash identity | 1 kg adopted process item | 0 | No | Waste/recovery, not a carbon credit | No generic heating or metallurgical-carbon grant |
| Sulfur/sugar/oily feed/additive | Exact chemical reagent forms | Existing individual assay | Positive, identity-specific | No | Exact reactor/feedstock bill only | Energy content does not imply equipment suitability |
| Diesel/distillates | Diesel/cracked/reformed variants | 0.833 kg/L parent; blends retain weighted assays | 43 MJ/kg parent | Yes | Compression ignition: 32% diesel, 30% reformed; Petroil 27%; selected prepared fuels in reciprocating engine | Gasoline is not diesel; other positive-LHV feedstock is not automatically motor fuel |
| Gasoline/LPG/alcohol/prepared motor fuels | Typed liquid/gas forms | Existing density/phase/mixture assay | Existing assay | Yes | Explicit reciprocating policy plus combustible trait | No automatic compression-ignition or aviation grant |
| Kerosene | Kerosene/reformed aviation blend | 0.800 kg/L parent | 43.8 MJ/kg parent | Yes | Aviation policy plus AERO trait; relevant propellant role | Not a stock reciprocating/diesel fuel |
| Clean process/fuel gases | Methane, syngas, petroleum/reform/coker gas, biogas, CO, H2 gas, blast-furnace gas | Existing standard-gas mass/mole assay | Existing mass-weighted assay | Selected burners | Gas turbine: methane/petroleum/reform/coker gas/syngas; reciprocating: explicit subset | Sour gas/H2S and oxyhydrogen are excluded; pure H2 requires separate engine suitability |
| Residual/process oils | Crude/cuts, tar oils, bitumen, reclaimed oil and applicable biological oils | Existing density/mixture assay | Existing assay | Yes when explicitly allowed and flammable | Existing process/coker feed; not universal engine fuel | Solvent/xylene/aromatics remain chemical feed, not ordinary equipment fuels |
| Propellants/oxidizers/exotics | Cryogenic H2, hydrazine, oxygen, peroxide, fictional fuels | Existing resolved assay or unresolved identity | Oxygen has no fuel energy | No automatic grant | Existing propulsion-specific APIs/traits remain the boundary | Propellant and oxidizer roles do not confer heater/engine suitability |

Physical consumers covered by the shared solid resolution are Firebox, Heating Oven, Industrial Boiler, Wood Burner and their inventory admission paths. The Metallurgical Furnace uses the same identity/quantity through coal-normalized work counters. The Industrial Blast Furnace accepts one coal/coal-coke reductant item under its existing bills. Combination/coke-oven and reactor process matching require exact assayed feed quantities independently of heater acceptance. Process burners, diesel/reciprocating engines and gas/aviation turbines use explicit equipment policy while retaining their existing ratings/efficiencies and energy owners.

## Intentionally niche and low-use identities

| Materials/forms reviewed without new demand | Classification / retained role |
|---|---|
| Beryllium | Significant specialist: nuclear/neutron and aerospace applications; not ordinary mechanical material |
| Samarium/SmCo; gadolinium; terbium; thulium | Specialist magnets, optical/nuclear states and processing intermediates; no circuit-yield reward merely for extraction difficulty |
| Rhodium, iridium, osmium/osmiridium, ruthenium, precious metals | Specialist contacts/catalysis/material states; bulk electronics substitution is not justified by price or hardness |
| Scandium, yttrium, cerium, lanthanum, dysprosium, holmium, erbium, ytterbium and other trace rare earths | Specialist optical/alloying/recovery content already represented; no element-completion demand |
| Ga/Ge/In/GaAs and optical crystal dopants | Specialist high-purity feed and electronics; raw ingots do not directly become finished boards |
| As/Cd/Hg and bismuth/arsenic bronzes | Historical/specialist, sometimes dominated or undesirable; no symmetry recipes against ordinary structural metals |
| Tc alloys/ferrouranium; minor actinides and unusual isotope states | Historical/experimental or specialist nuclear gameplay; no general-purpose power/material requirement added |
| Oxides, halides, leachates, concentrates, calcined dolomite | Process intermediates; their process role is sufficient |
| Slag, ash, spent residues, depleted fuel | Byproduct/waste; disposal/recovery is valid usefulness |
| Old wires, heavy-component metadata, disconnected graded bedrock forms | Legacy-save, reference/external/creative surfaces; retained registration is not proof of stock obtainability |
| Infernal/exotic legacy fuels and fictional metals | Explicit sci-fi or unresolved compatibility identities; no fabricated physical assay |

## Modeling basis

Chromium's central stainless-steel role is supported by [USGS chromium information](https://www.usgs.gov/centers/national-minerals-information-center/chromium-statistics-and-information). The new solder use follows tin's established electronics/solderability role documented by [NIST](https://www.nist.gov/programs-projects/lead-free-surface-finishes-electronic-components-tin-whisker-growth). Beryllium's specialist aerospace and alloy applications are described by [USGS](https://www.usgs.gov/centers/national-minerals-information-center/beryllium-statistics-and-information); these do not justify general-purpose machinery demand. Rare-earth roles follow [USGS](https://pubs.usgs.gov/fs/2002/fs087-02/) and the [DOE critical-materials strategy](https://www.energy.gov/sites/default/files/piprod/documents/cms_dec_17_full_web.pdf), including temperature-qualified SmCo magnet applications.

Foundry quanta, mineral parcels, thermite mixture counts, unassayed refining charges, nominal oxide/solution batches and fictional transformations remain adopted process abstractions. The mass/LHV fuel model is quantitative only for its declared assays. Material forms do not universally define SI mass, full atom accounting or reactor/fusion calibration.
