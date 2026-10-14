# RealSim canonical substance and state registry

2026-10-12, phases 1–4 investigation. **Candidate engineering identities only; no fluid ID, recipe, quantity, localization, configuration, or NBT changed.** Numerical references and citations live in [the physical ledger](REALSIM_PHYSICAL_LEDGER.md); current registration evidence is in [the source catalogue](REALSIM_SOURCE_CATALOGUE.md). See [the main decision and implementation gates](REALSIM_PHASES_1_4.md).

## Quantity contract

An inventory amount is an integer transport count. The current [FluidVolume](../src/main/java/com/hbm/inventory/fluid/FluidVolume.java) displays one count as 1 L and a bucket as 1000 L. Neither the integer nor its display proves a physical volume. The canonical registry must give every identity a **quantity basis** and a rational mass/mole coefficient. Energy = mass ×chemical LHV plus mass ×state enthalpy. Pressure state and network PU are different fields; no PU→bar conversion is established.

Recommended bases:

- **Liquid L:** physical reference-temperature liquid liter for ordinary petroleum, alcohols and specified solutions, using an adopted density/assay.
- **Standard gas L:** amount of dry gas occupying one liter at 0 °C and 1 bar absolute. A compressed tank stores this amount, not its shrinking actual volume. Compression work is separately charged.
- **Water-equivalent kg:** water/steam transport basis; one nominal feedwater-equivalent L is a 1 kg reference batch. The 0.18% cold-water-density approximation is explicit in the physical ledger. Steam L is a grade-dependent inventory coefficient, not literal gas volume.
- **Coolant reference L:** fixed reference mass per count across cold/hot transitions, even when actual density changes with temperature. A liquid coolant can reasonably have a reference liquid-liter basis; gas coolant uses standard-equivalent mass.
- **Material quantum:** existing MaterialShapes proportions mapped to mass. Solutions additionally state concentration. No “one item = one kg” rule for every component, ore block, log or rod.
- **Nominal process quantity:** legitimate for fictional fluids, plasma packets and unspecified mixtures. It cannot claim a measured kg, physical liter, bar or real LHV until an identity is supplied.

The physical registry and eventual scaled game ledger must be separate datasets. A global energy factor never changes molecular mass, reaction stoichiometry, actual liters, nominal steam amount, or the electrical quantum definition.

## Liquids and fuel streams

“Ambient” is an adopted 20 °C inventory state, generally near atmospheric pressure, not proof that every current registration specifies pressure. Phase/temperature shown in the current-state column comes from Fluids.java; final calculated fuel traits are registered later and override initial declarations.

| Current identity / player meaning | Current phase or naming issue | Recommended canonical modeled identity and basis | Energy/composition decision / future compatibility |
| --- | --- | --- | --- |
| WATER | Raw water, liquid | Raw environmental water, nominal 1 kg/L water-equivalent | Treatment removes an explicitly declared contaminant fraction; environmental extraction is an open source boundary. |
| FRESH_WATER | Treated water, liquid | Potable/process water, water-equivalent basis | Same H2O enthalpy as light water at same state; quality is not energy. |
| LIGHT_WATER / LIGHT_WATER_HOT | Demineralized water / nominal 325 °C hot liquid | Demineralized H2O, pressure-class liquid coolant; cold/hot fixed mass | Hot state needs sufficient physical pressure; purification ≠ isotope enrichment. |
| BORATED_WATER / BORATED_WATER_HOT | PWR moderator/coolant | Specified dilute borated demineralized H2O, cold/hot fixed mass | Boron concentration must be fixed independently from moderation coefficient. PWR already has explicit generic-coolant migration aliases. |
| HEAVYWATER / HEAVYWATER_HOT | D2O liquid, hot coolant | Heavy water, separate isotope mass and thermodynamic table | Preserve isotope separation and moderator behavior; no automatic ordinary-water density substitution. |
| OIL / HOTOIL | Crude / hot crude at 350 °C | Same crude assay and chemical LHV; hot state has separate sensible enthalpy | Heater/refinery own heat consumption. Keep current IDs; do not create LHV by heating. |
| OIL_DS / HOTOIL_DS | Desulfurized crude / hot counterpart | Crude with sulfur/H2 treatment metadata and balanced sour byproduct | No uniform 2% energy bonus. Product cut composition can deliberately change. |
| CRACKOIL / HOTCRACKOIL / CRACKOIL_DS / HOTCRACKOIL_DS | Cracked-crude feed and heated states | Defined cracked crude mixture, liquid-L basis | Cracking redistributes molecules; coke/gas/sulfur and treatment losses explicit. |
| HEAVYOIL / HEAVYOIL_VACUUM | Residual/heavy petroleum cuts | Defined heavy/residual oil, reference liquid L | Vacuum separation changes cut/composition, not an automatic 5% chemical uplift. |
| SMEAR / BITUMEN | Viscous residue / bitumen | Residual hydrocarbon/asphalt stream, warmed handling where necessary | Not zero-energy material just because an engine refuses it. Missing burner trait ≠ zero LHV. |
| HEATINGOIL / HEATINGOIL_VACUUM | Fuel oil cuts | Defined fuel-oil liquid | Burner or slower engine suitability; no universal diesel compatibility. |
| RECLAIMED | Recovered contaminated oil | Specified waste/reclaimed-oil assay | Equipment penalties and pollutant outputs separate from chemical energy. |
| LUBRICANT | Hydrocarbon oil with no combustion trait | Lubricating oil, liquid L and chemical energy even when not a fuel | Track consumed lubricant into waste/degraded lubricant or environmental sink. |
| PETROIL / PETROIL_LEADED | 80:20 reclaimed oil/lubricant blend; leaded variant | Literal weighted hydrocarbon blend | Not gasoline inferred from the suffix. Leaded additive volume/mass must be defined. |
| NAPHTHA / NAPHTHA_DS / NAPHTHA_CRACK / NAPHTHA_COKER | Intermediate cuts | Defined naphtha assays / quality variants, liquid L | Shared initial proxy can be used, but every split must close on material and energy. |
| LIGHTOIL / LIGHTOIL_DS / LIGHTOIL_CRACK / LIGHTOIL_VACUUM | Mixed intermediate light cut | Defined distillate feed, liquid L | Freeze cut densities jointly with diesel/kerosene yield. |
| DIESEL / DIESEL_CRACK | Diesel and unsaturated cracked diesel | Defined compression-ignition distillates, liquid L | Engine suitability/cetane is a property; same identity has one LHV across burner and engine. |
| DIESEL_REFORM / DIESEL_CRACK_REFORM | “High-Octane Diesel”, made by mixing 900 diesel +100 reformate | Diesel/reformate blend, liquid L | Category C wording: octane is spark-ignition knock resistance; diesel suitability needs a cetane/combustion-quality decision. Future rename descriptions only after review. |
| GASOLINE / GASOLINE_LEADED | Liquid spark-ignition fuel | Defined gasoline, liquid L | Lead changes suitability/pollution; cannot create 2000 L of fuel from an unweighed additive. |
| COALGAS / COALGAS_LEADED | Registered liquid; source calls coal-based gasoline | **Coal-derived gasoline liquid** | Retain liquid identity; do not “fix” it into a coal-gas fluid. Current chemistry uses hydrogenation/FT abstraction; descriptions should say synthetic gasoline. |
| KEROSENE / KEROSENE_REFORM | Aviation fuel / reformate blend | Kerosene and weighted blend, liquid L | Appropriate turbine/jet suitability; afterburning is extra fuel/thrust, not free electrical efficiency. |
| LPG | Registered liquid | Pressure-liquefied propane/butane assay; reference liquid L | Remains distinct from methane and standard gas. Recovery needs same molecules/composition. |
| ETHANOL / METHANOL | Ambient liquids | Pure alcohol reference, liquid L; assay dilution explicit | Fermentation/synthesis +separation energy required. |
| BIOFUEL | Multiple recipes imply unrelated synthesis/blends | Candidate biodiesel **or separately defined bio-oil blend** | Recommendation: make the canonical fuel biodiesel-like only with oil/alcohol/glycerol ledger; alternate blend routes need their own identity/assay. No recipe changed here. |
| COALOIL | Coal-derived liquid | Synthetic crude reference | Hydrogen and coal mass drive yield; a 1 kg coal item cannot provide a literal 1000 L heavy-oil product. |
| WOODOIL | Pyrolysis-derived oil | Wet fast-pyrolysis bio-oil reference | Lower LHV from oxygen/water, distinct from petroleum; upgrading requires hydrogen/heat and byproducts. |
| COALCREOSOTE | Coal tar / creosote | Assayed tar liquid | Coal tar is a broad family; select a central assay before final yields. |
| OIL_COKER / NAPHTHA_COKER | Coker residue/light liquids | Defined residue and naphtha streams | Coke, gas and water mass must accompany liquid products. |
| FISHOIL / SUNFLOWEROIL / OLIVEOIL | Triglyceride feedstocks | Assayed oils, liquid L | Feedstock mass/LHV still a release gate; oil extraction is not methanol synthesis or fermentation. |
| AROMATICS / XYLENE | Aromatics registered liquid; gas canister is misleading | Liquid aromatic mixture / specified xylene isomer mixture | Use liquid mass, not gas-volume energy; container type does not set phase. |
| SOLVENT / RADIOSOLVENT | Mixed solvent / fictional irradiation-derived solvent | Defined real solvent where possible; radioactive generic solvent otherwise nominal | No real LHV from “45% of diesel”. |
| HYDRAZINE | Liquid, real molecule | Liquid N2H4 reference with oxidation/decomposition distinguished | Chemical synthesis and oxidizer supply required; not a normal high-grade diesel fuel. |
| PEROXIDE | No concentration specified | Aqueous H2O2 with explicitly selected concentration | Chemical oxidizer/decomposition energy, not a fuel-engine LHV. |
| POLYTHYLENE | Liquid at default temperature | Polymer melt at a selected processing temperature **or polymer solution with solvent** | Current ambient-liquid state cannot stand for neat room-temperature polyethylene. Material mass and polymerization heat needed. |
| SEEDSLURRY / BLOOD / COLLOID / REDMUD / MINSOL / SLOP | Slurries/solutions | Wet process streams with solids and liquid fractions | Nominal inventory until concentration/solid mass fixed; byproducts are not free metal. |
| BRINE / IODINE_BRINE / BITTERN | Salt solutions | Aqueous solutions, liquid L with concentration | Water +salt balances separate. Brine-to-salt evaporation needs heat/rejected vapor. |
| HCL / SULFURIC_ACID / NITRIC_ACID / HYDROFLUORIC_ACID | Generic acids; molten/aqueous confusion in some recipes | Explicit aqueous concentration, except a separately declared anhydrous HF electrolyte | HCl gas synthesis needs absorption water to become acid. Aqueous electrolysis must not pretend to yield alkali metals. |
| SODIUM_HYDROXIDE / POTASSIUM_HYDROXIDE / chloride solutions | Liquids without concentration/phase contract | Defined aqueous alkali/salt solutions; molten electrolyte separate | Same displayed L cannot be both concentrated lye and molten salt. |
| BROMINE / MERCURY | Real liquids | Defined element/compound identity; mercury liquid | Registry comments call BROMINE alkyl bromide while display says bromine: elemental versus organobromine identity must be settled. |

## Gases and phase mismatches

| Identity | Actual registration/process conflict | Canonical decision |
| --- | --- | --- |
| HYDROGEN | LIQUID, −253 °C, EVAP; electrolysis/ammonia/mixing reason about gas-volume ratios | **Separate identities.** Preserve existing ID as liquid hydrogen for old stores/rocket use; propose a separate standard-equivalent process H2 gas for future chemical recipes, with explicit liquefaction/evaporation conversion. No automatic NBT amount rescale. |
| OXYGEN / NITROGEN | LIQUID, −183/−196 °C; electrolysis and atmospheric recipes retain gas-like integer proportions | Preserve cryogenic liquid identities; separate gaseous O2/N2 processing outputs/inputs. Use moles for blending. Cryogenic refrigeration is a charged process. |
| DEUTERIUM / TRITIUM | GASEOUS, fuel traits; isotope gases and plasma inputs | Standard-equivalent isotope gases; isotopic molecular mass and fusion packet mass must be defined. Cryogenic forms separate if required. |
| GAS | GASEOUS; localized Natural Gas/Methane | Methane proxy, standard L; actual deposits can later use a defined gas assay. |
| PETROLEUM | GASEOUS; refinery outputs count as if liquid fractions | Standard-equivalent refinery gas with declared composition; future mass-based output yields, not “10 liquid liters of gas”. |
| BIOGAS | GASEOUS | Defined methane/CO2 mixture; initial 60:40 molar proxy. |
| SYNGAS | GASEOUS, inconsistent sources include pyrolysis and oil/steam mixer | Defined H2/CO mixture; initial 50:50 molar proxy. Cleaning/reforming and char/CO2/water balances remain required. |
| REFORMGAS | GASEOUS; declaration calls MAPD; catalytic reformer output is also hydrogen-bearing process gas | Choose composition deliberately. Recommend a hydrogen-rich refinery offgas identity, revise MAPD wording; do not conflate with a high-temperature pure hydrogen coolant. |
| GAS_COKER | GASEOUS | Coker offgas, separately assayed; initial syngas proxy for scale arithmetic only. |
| SOURGAS | GASEOUS, toxic/corrosive | Methane/hydrocarbons +H2S/inerts; concentration release gate. Sweetening cannot discard carbon/chemical energy without a waste stream. |
| OXYHYDROGEN | GASEOUS, blended from cryogenic liquid HYDROGEN/OXYGEN | Canonical 2:1 molar gas mixture from gaseous identities; standard L. Current 666:334 inventory-L split is not that ratio for cryogenic liquids. |
| CARBONDIOXIDE / HELIUM4 / HELIUM3 | GASEOUS | Standard-equivalent amounts; stored primary-loop mass independent of actual loop pressure/volume. No combustion chemical credit. |
| HELIUM4_HOT | 750 °C gas, 1:1 cold/hot count | Same helium mass with thermal enthalpy; network tank count is not literal 750 °C volume. |
| AIR / NOBLE_GAS_MIX / real air mixtures | GASEOUS | Standard-equivalent dry gas mixtures with selected molar fractions. Separation preserves species mass. |
| CHLORINE / FLUORINE / HYDROGEN_SULFIDE / AMMONIA / CHLOROMETHANE | GASEOUS | Defined molecules, standard L; acid absorption/condensed ammonia are separate state conversions. |
| NEON / ARGON / KRYPTON / XENON | GASEOUS, cryogenic symbol on some | Standard gas identities; cryogenic symbol does not imply liquid density. Add liquid states only when genuinely produced. |
| METHYLENE | Named reactive intermediate, fractionates to GAS/CO2 | Category C generic mixed gas; define what the stream actually is, then rename description. Do not assign stable bottled CH2 chemistry. |
| SUPERHEATED_HYDROGEN | 2200 °C gas from 1:1 LH2 count | Same H2 mass; explicitly charge vaporization/heating and mass-equivalent conversion. Proposed high-temperature coolant/rocket boundary needs distinct thermal state, not 1 LH2 L →1 standard gas L. |
| GASEOUS_*_BROMIDE / GAS_WATZ / WASTEGAS | Thousands of °C, nuclear/rocket processes, speculative chemistry | Unspecified reactive nuclear process medium; nominal units until composition/speciation/mass set. Real element names alone do not establish a usable thermodynamic fluid. |
| UF6 and enrichment-stage fluids | Gas, temperature not selected; strong separation abstraction | Nuclear process quantity on UF6 mass/molar basis; phase temperature/pressure selected separately, no LHV. Do not make enrichment necessary for NU first power. |
| Planetary atmospheres | Various real and fictional mixtures | Preserve dimension access; real-mixture moles where defined, nominal atmosphere otherwise. Do not certify every cryogenic yield from a planet name. |

The state mismatches extend beyond hydrogen: air→LN2/LOX separation, aqueous acid versus gas absorption, gas→LPG chemistry, molten-polymer versus ambient liquid, superheated H2 quantity, actinide bromide speciation, and fuel-salt depletion during heat exchange all need explicit decisions. COALGAS is **not** a demonstrated gas/liquid mistake: it represents coal-derived gasoline.

## Steam and coolant state map

| Steam state | Stored L per kg water-equivalent | Heat reference / permitted next state |
| --- | ---: | --- |
| STEAM | 100 | 100 °C /0.10142 MPa candidate; →SPENTSTEAM by expansion, →HOTSTEAM only with heat/work |
| HOTSTEAM | 10 | 300 °C /1 MPa candidate; →STEAM, or supplied preparation →SUPERHOTSTEAM |
| SUPERHOTSTEAM | 1 | 450 °C /4 MPa candidate; →HOTSTEAM, or supplied preparation →ULTRAHOTSTEAM |
| ULTRAHOTSTEAM | 0.1 | 600 °C /16 MPa candidate; →SUPERHOTSTEAM; batches/remainder for sub-L flow |
| SPENTSTEAM | 1 | Chosen wet 10-kPa exhaust; →condensate with heat rejection; not zero-energy gas |

The [physical ledger](REALSIM_PHYSICAL_LEDGER.md#discrete-watersteam-reference) fixes candidate h, legal transitions, conservative work credits and condenser rejection. Grade compression is a transport representation. It neither compresses chemical energy nor establishes physical PU. A continuous equation-of-state simulation is unnecessary for these selected operating classes; a table plus rational mass, rated flow and owned residual-energy accounts suffices.

| Coolant identity | Current state | Canonical quantity and recommended action |
| --- | --- | --- |
| COOLANT/COOLANT_HOT | Declared borated-water model; Chemplant recipe 200 water +1800 ethanol; Mixer recipe 1800 water +niter→2000 coolant | **Unresolved composition.** Retain legacy generic coolant as a nominal medium until a chemistry/moderation choice. Use existing BORATED_WATER for actual PWR water. Do not silently reinterpret ethanol stores as water. |
| LIGHT_WATER / BORATED_WATER hot/cold | 325 °C hot states; cold states default 20 °C | Candidate recirculating 280→325 °C, 15 MPa pair has a 253.4 kJ/kg reference increment in the physical ledger. A warm return identity or explicit retained-heat account is needed. Startup makeup at 20 °C must pay its additional heating; boron concentration/moderation do not follow from heat capacity. |
| HEAVYWATER hot/cold | D2O analogue | Same isotope mass; independent heat table or explicitly approved approximation. |
| CARBONDIOXIDE, Zirnox primary | Tank type is ambient gas; reactor separately models gas temperature/pressure | Standard-equivalent fixed mass plus primary T/p classes; its nominal 26 bar variable does not calibrate fluid-network PU. |
| HELIUM4 hot/cold, Watz | Ambient→750 °C, 900 TU/L current | Standard-equivalent helium mass; energy from Δh, not a liquid-like nominal L energy. Watz coolant chemistry can be real even when fuel dynamics remain abstract. |
| SODIUM / SODIUM_HOT | Liquid temperature labels 350→650 °C | Fixed reference mass per L at mean loop temperature; sensible-heat pair. Preserve phase/reactivity behavior. |
| LEAD / LEAD_HOT | Liquid 350→650 °C | Fixed reference mass; correct >100% transfer factor later. |
| THORIUM_SALT / HOT / DEPLETED | Fuel salt and coolant entangled; HOT cools directly to DEPLETED | Separate thermal state from fuel assay/burnup. Recommendation: define chloride-based carrier composition matching the chlorine recipe, with thorium/fissile fractions; enrichment/breeding must make the reactor critical, not thorium alone. |
| MOLTEN_SALT / molten chlorides | Electrolysis versus fuel salt uses differ | Explicit electrolyte composition and temperature; not an alias for fuel salt. |
| CRYOGEL / BLOOD_HOT / MUG_HOT / WATZ | Fictional or composition-free thermal media | Category D or nominal process quantity until a credible substance/phase account is provided. Preserve unusual gameplay effects as declared abstractions. |
| URANIUM/PLUTONIUM/THORIUM_BROMIDE hot pairs | “Liquids” at 200 °C, gas at 2300–2600 °C | Specify solutions/molten salts versus decomposition products; otherwise nominal reactor/propulsion media, not invented pure-liquid properties. |

A 410 °C Magnox CO2 outlet cannot passively generate 450 °C superhot steam. The future Magnox candidate uses 300 °C HOTSTEAM with exchanger approach margin, or a different supported outlet state. Similarly, a 325 °C PWR primary cannot produce 450 °C secondary steam without an additional source. RBMK's default temperature-like heat threshold of 100 °C does not establish 450 °C steam either. This is a **temperature-quality boundary**, separate from the magnitude of HU.

## Solids, shapes, manufacturing and nuclear materials

The existing [MaterialShapes](../src/main/java/com/hbm/inventory/material/MaterialShapes.java#L16) already defines useful conserved proportions: ingot/dust 72 material units, nugget 8, billet 48, plate 72, cast plate 216, welded plate 432, shell 288, pipe 216, block 648, heavy component 55296. The [billet recipes](../src/main/java/com/hbm/crafting/MineralRecipes.java#L687) corroborate six nuggets/billet and three billets/two ingots.

Recommended reference mapping is **1 ordinary metal ingot =1 kg**, so 1 material unit =1/72 kg. This is an adopted gameplay material convention, not a statement about Minecraft mesh dimensions. It yields billet 2/3 kg, cast plate 3 kg, welded plate 6 kg, shell 4 kg, pipe 3 kg, ordinary metal block 9 kg and heavy component 768 kg. Use the same convention through cast/press/weld/melt recipes; a heavy component's operation time/energy should reflect its mass/capability rather than only its recipe index. Material-specific density determines fluid-L conversions, not the metal's ingot count alone.

Coal/coke/charcoal candidate item mass is 1 kg for a full standard fuel item; dust equivalent same mass and compressed blocks sum constituents. Biomass and logs require a separate moisture/dry-mass decision (unreleased); stone/ore blocks need declared process feed/yield masses rather than a visual cubic-meter assumption. Complete motors, housings, blades and machines use the sum of constituent materials plus any explicit discarded/returned material. Isotope billets keep their material mass; a rod's cladding is not fissile mass.

| Nuclear/fantasy material | Quantity/energy model |
| --- | --- |
| Natural uranium / U235/U238/U233 / plutonium / thorium | Material/isotope mass and nuclear inventory; heat from calibrated burnup, not combustion or arbitrary HU→J. Breeding/capture and spent fuel separately accounted. |
| Zirnox/Magnox rod | Empty cladding +specified fuel billets; NU route retained. Safety, oxidation, contamination, graphite, CO2 loss and decay heat remain meaningful mechanics. |
| RBMK NU/UEU rod | Existing natural-uranium billets and cladding route; neutron-source/moderator geometry explicit. No universal “enriched first reactor” prerequisite. |
| Watz TRISO pellets | Nuclear source inventory plus independent coolant mass; conservative measured export boundary required. |
| Plasma packet / ICF pellet | Nominal fuel packet with isotope mass and energy per reaction defined separately; do not interpret plasma “L” as a bulk liquid-liter density. |
| Schrabidium/balefire/stellar flux/antimatter gameplay products | Explicit fictional source and inventory budgets. Ordinary industrial balance does not require fabricated chemistry for them. |

## Compatibility and unresolved entries

Preserve ID/name mapping order, existing Forge quantities, custom-fluid definitions, recipe JSON schemas/templates, network amounts and unknown NBT. **Separate identities are a future recommendation**, not permission to rename/migrate now. Where a correction needs new gas states, retain old cryogenic IDs and make phase conversions explicit processes. A canister or inventory already containing liquid hydrogen stays liquid hydrogen; converting its amount to standard-equivalent gas happens only through an accounted operation.

Existing generic coolant cannot be mass-inferred from its type because distinct recipes make chemically different material under the same ID. Before implementing, choose one of: retain it as an explicitly nominal legacy fluid with a bounded adapter; define new canonical chemical identities with explicit conversion; or offer a versioned, opt-in world conversion with declared compensation and no silent amount reinterpretation. A legacy nominal route must not remain the authoritative internal RealSim ledger.

Unreleased entries are **composition/measurement gates**, not implicit zeros: generic coolant, heavy-water h endpoints, sour/refinery/reformer gas final assays, elemental versus organic BROMINE, reactive METHYLENE, aromatic/solvent assays, oil/fat/biomass moisture, fuel salt composition/fissile fraction, actinide bromide media, isotope-plasma mass. The canonical basis/rules are specified; those rows need an explicit selected assay before their balance is enabled. The complete current registration census remains in the source catalogue so unnamed aliases/late fluids are not silently forgotten.


## Implemented Phase 5A/5B subset

The historical candidate tables above remain the design/reference census. The current runtime uses `PhysicalRegistry`/`PhysicalState` for the released fuel assays and the explicit H2/O2/N2 gas/liquid states. Unresolved/fictional rows remain unresolved. Gas counts use 0 °C/1 bar standard-equivalent liters; old cryogenic IDs and stored quantities retain their meaning. The adopted starter wood item masses and the initial dry-air/noble-tail proxies are deliberate runtime assay choices. See [the Phase 5A/5B implementation contract](REALSIM_PHASES_1_4.md#phase-5a5b-physical-accounting-and-first-generation-tranche) for ratings, suitability, conversion work, resource ownership, persistence and migration boundaries.


## Phase 5C implementation — released petroleum assays (2026-10-13)

This section supersedes the candidate petroleum and mixed-gas values above. Values are adopted representative RTM assays, not measurements of arbitrary real petroleum. Density means kg per reference liquid L, dry standard gas L at 0 C/1 bar, or full solid inventory item. Sulfur/contaminant labels describe the chosen process family; elemental sulfur transport through arbitrary crude is not simulated. Hot crude and bitumen additionally retain the sensible energy stated below. Heating never adds LHV.

| Identity | Phase | kg/inventory unit | LHV MJ/kg | Composition, contaminants and downstream use |
| --- | --- | ---: | ---: | --- |
| `OIL` | liquid | 0.85 | 42.3 | 0.35 wt% sulfur adopted; atmospheric separation or hydrotreating. |
| `OIL_DS` | liquid | 0.85 | 42.3 | Hydrotreated crude surrogate; residual sulfur is nominal, not elemental tracking. |
| `HOTOIL` | liquid | 0.85 | 42.3 | Same crude mass/LHV plus 660 kJ/kg sensible; atmospheric feed. |
| `CRACKOIL` | liquid | 0.85 | 42.3 | Upgraded hydrocarbon assay; heated four-cut separation. |
| `HEAVYOIL` | liquid | 0.98 | 40.4 | Residual hydrocarbon cut; process heat, vacuum separation or coking. |
| `HEAVYOIL_VACUUM` | liquid | 0.98 | 40.4 | Same residual assay; heating fuel or coking. |
| `BITUMEN` | liquid | 0.99 | 39.75 | Residual asphalt surrogate; 260 kJ/kg sensible at 150 C; coker or solid tar. |
| `SMEAR` | liquid | 0.98 | 39.975 | 50 mass% lubricant /50% bitumen; fractionation or reclamation. |
| `LIGHTOIL` | liquid | 0.8 | 43.36 | 55 mass% diesel /45% kerosene. |
| `LIGHTOIL_CRACK` | liquid | 0.8 | 43.36 | Same cut-energy surrogate; unsaturated diesel output needs hydrotreating. |
| `LIGHTOIL_VACUUM` | liquid | 0.8 | 43.36 | Same diesel/kerosene assay; no free conversion of liquid into gas. |
| `NAPHTHA` | liquid | 0.7 | 44.5 | Light hydrocarbon reformer/FCC feed. |
| `NAPHTHA_CRACK` | liquid | 0.7 | 44.5 | Cracked light feed surrogate; reformer/FCC. |
| `NAPHTHA_COKER` | liquid | 0.7 | 44.5 | Same cracked-naphtha assay; stabilizing fractionation/reforming. |
| `OIL_COKER` | liquid | 0.833 | 43 | Heating-oil-range coker distillate surrogate; heated finishing. |
| `KEROSENE` | liquid | 0.8 | 43.8 | Aviation/heating fuel; exact Phase 5A/5B anchor. |
| `DIESEL` | liquid | 0.833 | 43 | Engine fuel; exact Phase 5A/5B anchor. |
| `GASOLINE` | liquid | 0.73 | 44.3 | Finished reformate surrogate; no octane energy multiplier. |
| `HEATINGOIL` | liquid | 0.833 | 43 | Heavy heating fuel surrogate. |
| `REFORMATE` | liquid | 0.73 | 44.3 | Gasoline blendstock; finishing, blends or FCC. |
| `AROMATICS` | liquid | 0.87 | 40.6 | Declared aromatic feedstock mixture; petrochemicals. |
| `UNSATURATEDS` | gas | 0.001235223 | 47.2 | Ethylene gas surrogate; alkylation/additive/petrochemicals. |
| `PETROLEUM` | gas | 0.001323986 | 48.023862 | 50 mol% methane /50% LPG-vapor surrogate; burners, recovery or compatible gas engine. |
| `SOURGAS` | gas | 0.0007858 | 43.365062 | 90 mol% methane /10% H2S; sweetening then sulfur recovery. |
| `GAS_COKER` | gas | 0.000243165 | 69.163019 | 75 mol% H2 /25% methane; no CO without an oxygen source. |
| `REFORMGAS` | gas | 0.000397568 | 57.821815 | 50 mol% H2 /50% methane; burner, compatible gas engine or chemical feed. |
| `LPG` | liquid | 0.52 | 47.3 | Stored liquid LPG surrogate; phase is distinct from refinery gas. |
| `HYDROGEN_GAS` | gas | 0.000088762 | 119.960514 | Process H2; compression to class 1 for hydro/synthesis. |
| `HYDROGEN_SULFIDE` | gas | 0.001500633 | 15.199138 | Sour-gas component; sulfur recovery with paid O2. |
| `RECLAIMED` | liquid | 0.9 | 40.2 | Reclaimed hydrocarbon surrogate; coker explicitly separates 5 mass% water. |
| `LUBRICANT` | liquid | 0.89 | 40.2 | Lubricating-oil surrogate; blend/industrial use. |
| `PETROIL` | liquid | 0.898 | 40.2 | 80/20 volume reclaimed/lubricant blend; weighted density and LHV. |
| `PETCOKE_ITEM` | solid | 1 | 32.5 | 1 kg petroleum coke/item; material/fuel outlet. |
| `COKE_ITEM` | solid | 1 | 28.2 | 1 kg coal coke/item; existing solid-fuel assay. |
| `LIGNITE_COKE_ITEM` | solid | 1 | 25 | 1 kg lignite coke/item; adopted carbonization assay. |
| `COAL_ITEM` | solid | 1 | 25.8 | 1 kg/item; 80%C, 5%H, 5%O, 10%ash for supported coal-liquid/gasifier bills. |
| `LIGNITE_ITEM` | solid | 1 | 18 | 1 kg/item, moist/inert lignite surrogate for carbonization. |
| `COALOIL` | liquid | 0.85 | 42.3 | Coal-derived crude surrogate; hydrotreating or fuel use. |
| `COALGAS` | liquid | 0.73 | 44.3 | Liquid coal gasoline, despite historical name. |
| `SYNGAS` | gas | 0.000661045 | 17.478397 | 50 mol% CO /50% H2; synthesis feed, never liquid liters. |
| `METHANOL` | liquid | 0.792 | 19.928 | Neat methanol; cooled synthesis product with intact syngas purge. |
| `BIOFUEL` | liquid | 0.8938 | 35.611979 | 80/20 volume triglyceride oil /ethanol blend; fish and seed oils share this representative assay. Not transesterified biodiesel. |
| `FISHOIL` | liquid | 0.92 | 37.5 | Triglyceride-oil surrogate; extraction, blending or chemistry. |
| `SUNFLOWEROIL` | liquid | 0.92 | 37.5 | Triglyceride-oil surrogate; extraction, blending or chemistry. |
| `WOODOIL` | liquid | 1.2 | 17 | Wet pyrolysis liquid surrogate; burner/material feed, hydro-upgrading deferred. |
| `COALCREOSOTE` | liquid | 1.1 | 28 | Wet coal-tar surrogate; 35 mass% recoverable water in coker bill. |

`OIL_DS`, `CRACKOIL_DS` and their hot forms keep their corresponding crude density/LHV; hydroprocessing pays H2 and separates sour gas instead of granting an energy multiplier. `LIGHTOIL_DS` shares the light assay; diesel/kerosene reform blends retain the 90/10 input volume-weighted mass and energy. Leaded fuels contain one 10 g additive per 10000 reference L, including its mass and chemical energy, without extra bulk fuel. Coal/lignite briquettes are 1 kg reference items; wood briquettes are 4 kg and saplings 1 kg. Wood ash has zero chemical LHV because it is a resolved inert product, not an unresolved substance.

The registry still deliberately refuses unassayed coolant, planetary gases, reactive/fictional mixtures and unrelated chemistry as physical fuel/process inputs. Existing IDs, stores and transport remain available. Specifying a process bill requires a resolved mass and LHV for every input, coproduct and loss identity. Gas-turbine direct output and steam recovery remain deferred to Phase 5D.


## Phase 5D released water-equivalent steam (2026-10-13)

`PhysicalRegistry` publishes WATER_EQUIVALENT mass for STEAM/HOTSTEAM/SUPERHOTSTEAM/ULTRAHOTSTEAM/SPENTSTEAM: 0.01/0.1/1/10/1 nominal kg per unchanged count. FRESH_WATER/raw WATER retain the 1-kg reference batch. Counts are not gas L or literal vessel liters; PU remains transport metadata. Shared hovers show water mass and enthalpy rather than retained nuclear trait budgets.

`SteamLedger` freezes cold, internal warm condensate, four steam grades and wet spent steam at the [temperature/pressure/enthalpy table](REALSIM_PHYSICAL_LEDGER.md#phase-5d-frozen-thermal-contract-2026-10-13). Warm condensate has an internal identity; no existing fluid ID was renamed or a new one inserted. Native condensers return cold fresh water, rejecting latent/wet heat and the final warm-to-cold increment. Nominal feedwater kg preserves the 100:10:1:0.1 saved ratios.

Gas-turbine suitability is explicit for released natural/refinery/reform/coker/syngas assays. Each retains Phase 5C composition and mass x LHV. The adopted multi-fuel combustor supports those H2-bearing mixtures; pure H2, sour gas, oxyhydrogen, fictional/unresolved gas and liquid LPG are refused. Refinery gas's existing LPG-vapor component is legitimate gas feed. Throttle/afterburner never rewrite LHV.

Saved stock gains interpretation without rescaling counts. Unknown/custom identities are retained, not replaced with water/air. Nuclear/cryogenic species and coolant/HU calibration remain outside this release.
