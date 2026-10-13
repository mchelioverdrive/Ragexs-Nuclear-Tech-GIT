# RealSim physical reference ledger

Investigation date: 2026-10-12. This is the **unscaled physical reference**, not implemented balance data. Read with [the state registry](REALSIM_SUBSTANCE_REGISTRY.md), [process inventory](REALSIM_PROCESS_INVENTORY.md), and [scale decision](REALSIM_PHASES_1_4.md). No recipes, traits, runtime code, configurations, or saves were changed.

## Data contract and evidence

Chemical energy uses LHV: combustion water exits as vapor. Sensible/latent enthalpy is a separate state account. Oxidizers supply material; do not credit oxygen with a second fuel LHV. Fission, fusion, radioactive decay, solar irradiation, and geothermal extraction are separate declared sources. Electrical quanta retain their existing meaning, 0.5 J each, and nominal simulation time is 20 ticks/s.

Every numerical reference below is either sourced, calculated from identified sources, or explicitly an **adopted modeling assumption**. A petroleum cut, mixed solvent, or undefined coolant has no uniquely measurable density/LHV until its composition is chosen. The central proxy values are fixed candidate identities, not claims that all commercial fuels share those exact properties. No current RTM fuel number was used to choose a physical reference.

Sources consulted on 2026-10-12:

| Key | Authoritative source and relevant boundary |
| --- | --- |
| F1 | [DOE AFDC fuel properties](https://afdc.energy.gov/fuels/properties): liquid fuel LHV/HHV, hydrogen LHV, fuel suitability and phase. Published liquid values converted from Btu/US gallon; 1 Btu = 1055.056 J, 1 US gallon = 3.785411784 L. |
| F2 | [IPCC 2006, Volume 2, chapter 1, table 1.2, pp. 1.18–1.19](https://www.ipcc-nggip.iges.or.jp/public/2006gl/pdf/2_Volume2/V2_1_Ch1_Introduction.pdf): net calorific values. TJ/Gg is numerically MJ/kg. Population reference values, not a particular refinery assay. |
| F3 | [NIST TN 2126, tables 4-4 and 4-6](https://nvlpubs.nist.gov/nistpubs/TechnicalNotes/NIST.TN.2126.pdf): pure-species combustion, LHV/HHV, molar mass. |
| T1 | [NISTIR 5078, tables 1 and 3](https://nvlpubs.nist.gov/nistpubs/Legacy/IR/nistir5078.pdf): IAPWS water/steam h, s, density; printed pp. 4–5, 16, 38–39, 46–47, 56–59. |
| T2 | [IAEA TECDOC 1912, coolant property comparison, table 2](https://www-pub.iaea.org/MTCD/Publications/PDF/TE-1912_web.pdf): temperature-specific sodium, lead, salt, helium and CO2 properties. |
| E1 | [EPA CHP reciprocating engines, section 2](https://www.epa.gov/sites/default/files/2015-07/documents/catalog_of_chp_technologies_section_2._technology_characterization_-_reciprocating_internal_combustion_engines.pdf): size/efficiency, exhaust and jacket recovery, heavy-oil equipment limitations. |
| E2 | [EPA CHP combustion turbines, section 3, tables 3-2 and 3-3](https://www.epa.gov/sites/default/files/2015-07/documents/catalog_of_chp_technologies_section_3._technology_characterization_-_combustion_turbines.pdf): direct electricity, exhaust heat and fuel compression; distinguish HHV and LHV. |
| E3 | [EPA CHP steam turbines, section 4, table 4-2](https://www.epa.gov/sites/default/files/2015-07/documents/catalog_of_chp_technologies_section_4._technology_characterization_-_steam_turbines.pdf): expansion efficiency versus plant efficiency, generator/gearbox efficiency, backpressure versus condensing operation. |
| P1 | [DOE petroleum refining bandwidth study, table 4-2](https://www.energy.gov/sites/prod/files/2015/08/f26/petroleum_refining_bandwidth_report.pdf): onsite process energy per barrel, excluding feedstock chemical energy. |
| P2 | [DOE PEM electrolysis targets](https://www.energy.gov/cmei/fuels/technical-targets-proton-exchange-membrane-electrolysis): **2022 status** 55 kWh/kg H2 system, 51 stack; 2026/ultimate targets are not measured present performance. |
| P3 | [DOE record 9013, hydrogen compression/liquefaction](https://www.hydrogen.energy.gov/pdfs/9013_energy_requirements_for_hydrogen_gas_compression.pdf): compression approximately 2–4 kWh/kg in the cited high-pressure applications; liquefaction approximately 8–13.3 kWh/kg depending scale. |
| P4 | [DOE natural-gas reforming](https://www.energy.gov/cmei/fuels/hydrogen-production-natural-gas-reforming): endothermic reforming plus water-gas shift, high-temperature heat, purification. |
| P5 | [NREL pyrolysis assessment, table 2](https://docs.nrel.gov/docs/fy07osti/37779.pdf): bio-oil LHV 13–18 MJ/kg, density 1.2–1.3 kg/L and substantial water fraction. |
| P6 | [DOE process heating sourcebook](https://www1.eere.energy.gov/manufacturing/tech_assistance/pdfs/process_heating_sourcebook2.pdf): EAF electrical energy 400–500 kWh/**short ton**; converted below to metric tonnes. |
| P7 | [DOE pumping sourcebook](https://www.energy.gov/sites/default/files/2014/05/f16/pump.pdf): flow, head, density, motor/drive efficiency and operating point. |
| P8 | [DOE mining bandwidth study](https://www.energy.gov/sites/default/files/2013/11/f4/mining_bandwidth.pdf): separate digging, drilling, crushing, grinding, ventilation and handling. Aggregate TBtu/year data are not kWh per individual block. |
| P9 | [NREL biofuels description](https://www.nrel.gov/research/re-biofuels): biodiesel from fats/oils plus alcohol; ethanol from sugar/starch/cellulose. |
| N1 | [NASA MMRTG reference](https://www.nasa.gov/wp-content/uploads/2015/04/mmrtg.pdf), [NASA thermoelectric efficiency discussion](https://science.nasa.gov/science-research/science-enabling-technology/technology-highlights/advanced-thermoelectric-technology/): roughly 110 We class, approximately 6% conversion, not RTM's 30 kWe per unspecified pellet inventory. |

## One chemical ledger per substance

Liquid densities in this table are **adopted nominal assay assumptions**, approximately room-temperature inventory densities except LPG/LH2. They are part of the proposed identity specification, not independent measured RTM properties. LHV citations apply to the named physical reference; calculated MJ/L = MJ/kg × assumed kg/L. Hot/cold versions carry the same chemical energy per mass plus different sensible enthalpy. Purity, sulfur, octane/cetane, viscosity and machine compatibility do not multiply chemical energy.

| Canonical fuel / applicable current identities | LHV MJ/kg | Nominal kg/liquid L | Derived MJ/liquid L | Basis / treatment |
| --- | ---: | ---: | ---: | --- |
| Crude, OIL/OIL_DS/CRACKOIL/CRACKOIL_DS | 42.3 | 0.850 | 35.955 | F2; mixed crude proxy. Cracking must distribute mass/energy into products, not confer a blanket bonus. |
| Diesel, DIESEL/DIESEL_CRACK | 43.0 | 0.833 | 35.819 | F2; F1 volume cross-check approximately 35.81 MJ/L. |
| Gasoline, GASOLINE/COALGAS | 44.3 | 0.730 | 32.339 | F2; coal-derived gasoline is a liquid gasoline product, not coal gas. |
| Kerosene, KEROSENE | 43.8 | 0.800 | 35.040 | F2 other-kerosene proxy; aviation-specific specification may use the separate jet-kerosene reference. |
| Heating oil, HEATINGOIL/HEATINGOIL_VACUUM | 43.0 | 0.833 | 35.819 | F2 gas/diesel-oil proxy; viscosity/sulfur handling may differ. |
| Residual/heavy oil, HEAVYOIL/HEAVYOIL_VACUUM/SMEAR/BITUMEN | 40.4 | 0.980 | 39.592 | F2 residual proxy; bitumen's separate 40.2 MJ/kg reference is close, but identity/temperature remain explicit. |
| Naphtha, NAPHTHA and DS/CRACK/COKER variants | 44.5 | 0.700 | 31.150 | F2; normal petroleum cut, not gasoline created without upgrading. |
| Light oil / vacuum light oil | 43.0 | 0.800 | 34.400 | Adopted distillate proxy using F2. Final separated cut must agree with downstream mass yields. |
| Reclaimed oil | 40.2 | 0.900 | 36.180 | F2 waste-oil proxy; contamination reduces usable equipment efficiency, not necessarily LHV. |
| Lubricant | 40.2 | 0.890 | 35.778 | F2; energy-bearing even when engines reject it. |
| Petroil | mixture | mixture | 36.100 | Defined 80% reclaimed + 20% lubricant by volume; weighted energy, no blending bonus. |
| Reformate | 44.3 | 0.730 | 32.339 | Adopted gasoline proxy. Actual assay could differ; increased octane alone cannot justify an LHV uplift. |
| DIESEL_REFORM / DIESEL_CRACK_REFORM | mixture | mixture | 35.471 | Actual mixer proportion: 90% diesel + 10% reformate; derived from the two canonical inputs. Proposed description: diesel/reformate blend, with suitability review. |
| KEROSENE_REFORM | mixture | mixture | 34.770 | 90% kerosene + 10% reformate; no chemical bonus. |
| Leaded variants | parent plus additive | parent plus additive | weighted inputs only | Parent fuel LHV unchanged absent an explicitly mass/energy-accounted additive. A 20% volume increase from one unweighed additive item is not defensible. |
| Liquid LPG | 47.3 | 0.520 | 24.596 | F2 propane/butane mixture; F1 supplies a propane-volume cross-check. Phase-specific liquid inventory. |
| Ethanol | 26.806 | 0.789 | 21.150 | Derived from F3 1234.94 kJ/mol / 46.068 kg/kmol; F1 volume cross-check. |
| Methanol | 19.928 | 0.792 | 15.783 | F3 638.53 kJ/mol / 32.042 kg/kmol; F1 volume cross-check. |
| BIOFUEL, candidate biodiesel identity | 37.841 | 0.880 | 33.300 | F1 B100 volume reference rounded, adopted density; current biogas/ethanol route cannot make this identity at its present yield. |
| Fish/sunflower/olive oil feedstocks | pending assay | pending assay | no final value | Real triglyceride mixtures; use a measured or explicitly chosen feedstock assay before locking transesterification. They cannot be treated as zero-energy material. P9. |
| WOODOIL, fast-pyrolysis candidate | 17.0 | 1.200 | 20.400 | P5, chosen point inside cited LHV/density ranges. Water content remains included, not dry-oil HHV. |
| COALOIL, synthetic crude candidate | 42.3 | 0.850 | 35.955 | Adopted crude proxy, not a uniquely identified real compound. Hydrogenation yield must be recalculated. |
| COALCREOSOTE / coal tar | 28.0 | 1.100 | 30.800 | F2 coal-tar reference, adopted density; wide composition uncertainty. |
| OIL_COKER | residual proxy | 0.980 | 39.592 | Adopted residual stream; NAPHTHA_COKER maps to naphtha. Mass yield and coke/gas byproducts are separately required. |
| SOLVENT / AROMATICS / XYLENE | pending composition | pending composition | no final value | Real chemicals or mixtures, not one generic fuel. A surrogate may be selected explicitly; the current registry percentage is not physical evidence. |
| Hydrazine, oxidized to N2 + water vapor | 16.673 | assumed 1.000 | 16.673 | Derived using [NIST liquid ΔfH = 50.63 kJ/mol](https://webbook.nist.gov/cgi/cbook.cgi?ID=C302012&Mask=FFFFFF), [water vapor −241.826 kJ/mol](https://pmc.ncbi.nlm.nih.gov/articles/PMC7339710/), molar mass 32.0452 g/mol. Catalytic decomposition is a different reaction; no normal diesel suitability. |
| Dicyanoacetylene / chloromethane / mixed unsaturateds | pending specific species | phase-dependent | no final value | A high flame temperature is not proof of exceptionally large LHV. Freeze composition and phase before entering the chemical ledger. |
| NITAN, BALEFIRE, BLOODGAS, NMASS*, fictional solvents/plasma | explicit fictional budget | nominal | fictional | Category D: one declared energy/material budget per identity, not fabricated real chemistry or diesel multipliers disguised as measurements. |

For generic blends with unresolved constituents, the formula is fixed but the assay remains a Phase 5 gate. An unresolved value is **not zero**. Do not permit a physical conservation claim or energy-bearing recipe registration until the missing constituent has a ledger value.

### Gases: state and composition first

Canonical standard gas is **0 °C, 1 bar absolute**, dry; ideal molar volume 22.711 L/mol calculated from the gas law. This is not the same as 1 atmosphere or a commercial standard at 15/20 °C. Network PU is unrelated. Compressed storage preserves standard-equivalent amount and adds a separate compression state/work account; it does not change LHV per mole.

| Gas identity / adopted composition | Physical LHV basis | Derived energy per standard L | Nominal mass per standard L |
| --- | --- | ---: | ---: |
| Methane / GAS | 802.3 kJ/mol, F3 rounded | 35.327 kJ | 0.7064 g |
| Hydrogen gas | 241.826 kJ/mol, water-vapor formation reference above | 10.648 kJ | 0.08876 g |
| Carbon monoxide | 282.952 kJ/mol, F3 | 12.459 kJ | 1.2333 g |
| SYNGAS, 50 mol% H2 + 50 mol% CO | Weighted preceding entries | 11.553 kJ | 0.6610 g |
| BIOGAS, 60 mol% CH4 + 40 mol% CO2 | Weighted methane, CO2 has zero combustion LHV | 21.196 kJ | 1.1990 g |
| Petroleum/refinery gas, initial **methane proxy** | Same methane reference | 35.327 kJ | 0.7064 g |
| REFORMGAS, initial **50 mol% H2 / 50 mol% CH4 proxy** | Weighted hydrogen/methane | 22.988 kJ | 0.3976 g |
| GAS_COKER, initial syngas proxy | Same defined syngas reference | 11.553 kJ | 0.6610 g |
| Oxyhydrogen, 2 mol H2 : 1 mol O2 | Only its 2/3 hydrogen fraction burns | 7.099 kJ | 0.5288 g |
| CO2 / He / N2 / O2 / air | No ordinary combustion LHV | 0 | Species/mixture molar mass / 22.711; thermal enthalpy separate |

The refinery/reform/coker gas compositions are **explicit surrogate proposals**. Current REFORMGAS's source declaration mentions MAPD; this hydrogen-rich proxy would therefore require a deliberate description/composition decision, not a silent interpretation. Petroleum GAS and LPG may have similar chemistry but must retain distinct phase quantities. Sour gas requires a defined inert/H2S/fuel fraction before it has a fixed LHV; raw and sweet gas cannot both inherit a full pure-methane budget.

Hydrogen uses the same chemical LHV in both phases: **119.963 MJ/kg**, derived from the gas row's 241.826 kJ/mol and 2.01588 g/mol, consistent with F1's rounded 120 MJ/kg. Candidate liquid hydrogen has 0.0708 kg/L near its boiling point, from [DOE hydrogen properties reference](https://www.hydrogen.energy.gov/docs/hydrogenprogramlibraries/pdfs/nhe_rpt.pdf?Status=Master), giving **8.493 MJ/liquid L**. That is roughly 798 times the standard-gas-liter energy. Liquefaction does not create chemical energy: it reduces physical volume and costs work (P3). The existing HYDROGEN fluid cannot mean both quantities in the same recipe ledger.

Solid reference entries: bituminous coal **25.8 MJ/kg**, coke **28.2**, petroleum coke **32.5**, wood/wood waste **15.6**, charcoal **29.5** (F2). The wood reference is not a universal dry-wood assay. Use the registry's item-mass proposal; do not infer kg from furnace burn ticks. Biomass needs a selected moisture/ash assay. Nuclear rods do not use a combustion LHV; fissile mass, burnup, capture/breeding, decay heat and exported thermal energy need their own boundary.

### Combustion and equipment

Hydrocarbon complete combustion yields CO2 + water; alcohols also yield CO2 + water; hydrogen yields water. Air combustion adds nitrogen ballast. Sulfur/lead/ash and incomplete-combustion pollution remain separate material channels. RTM's pollution counters and smoke-fluid amounts are hazard abstractions, not calibrated kilograms of exhaust. A complete mass balance must declare whether atmospheric oxygen/exhaust are explicit tank flows or an open environmental boundary.

| Equipment | Physical reference / adopted envelope | Accounting rule |
| --- | --- | --- |
| Portable spark generator | Adopted 2–30 kWe, 15–25% LHV electrical | Gasoline/alcohol compatibility separate; no heavy oil by default. |
| Compact diesel genset | Adopted 20–500 kWe, 25–35% LHV electrical, informed by E1 | One canonical fuel budget; use a rated shaft/generator and actual load. |
| Industrial reciprocating engine | Adopted 0.5–10 MWe, 35–45% LHV electrical; E1 covers engines from ~10 kW to >18 MW | Heavy oil requires appropriate slower equipment/preheat. |
| Simple gas turbine | E2 examples 3.3–44.5 MWe, 24–36% **HHV** electrical; adopted smaller-game LHV envelope 20–35% | HHV figures must not be silently labeled LHV. Exhaust budget = input less direct output and other losses. |
| Combined cycle | Adopted conservative 40–55% LHV electricity envelope, derived from a direct turbine plus residual-heat steam cycle | Not E2's “effective electrical efficiency”, which credits exported process heat. Check actual modeled sum. |
| Steam engine | Adopted small 2–50 kWe, 5–12% whole fuel-to-electric | Stage multiplier is separate from boiler, expansion availability and generator losses. |
| Industrial steam turbine | E3 examples 0.5–15 MWe backpressure; 52.5–78% isentropic and 94–96% generator/gearbox | Real condensing stations can be larger; the reference discrete transition sets a conservative work ceiling. |
| Boiler / CHP | E3 boilers 70–85% **HHV**, total CHP near 80%; adopted 85% LHV boiler for examples | Delivered steam heat is not electricity. Do not add electrical and process-heat uses of the same exhaust twice. |
| RTG | N1: hundred-watt electrical class, ~6% thermal conversion | More isotope can increase power but must increase source inventory/decay heat; fixed 7/25 kW units are explicit abstractions. |

These adopted envelopes are design ranges, not measured RTM ratings or universal efficiencies at every size/load. A fuel's useful electrical energy is LHV × equipment efficiency; there is no independent “engine chemical LHV”.

## Discrete water/steam reference

NIST anchors below are rounded physical values, per **kg**, using 20 °C water h = 83.91 kJ/kg as the zero reference. Ordinary steam is at 100 °C and 0.10142 MPa absolute. Hot, superhot and ultrahot pressure classes are **candidate engineering states**, not readings of existing PU or statements about current machines.

| State | Physical phase / T / p absolute | h, kJ/kg | h relative to cold feed, kJ/kg | Nominal mass kg per stored RTM steam L |
| --- | --- | ---: | ---: | ---: |
| Cold liquid feed | 20 °C, approximately atmospheric | 83.91 | 0 | Water inventory density separately defined |
| Hot liquid feed | 100 °C, near saturation | 419.17 | 335.26 | Derived from liquid water amount |
| STEAM | Saturated vapor, 100 °C / 0.10142 MPa | 2675.6 | 2591.69 | 0.01 |
| HOTSTEAM | Superheated vapor, 300 °C / 1 MPa | 3051.6 | 2967.69 | 0.1 |
| SUPERHOTSTEAM | Superheated vapor, 450 °C / 4 MPa | 3331.2 | 3247.29 | 1 |
| ULTRAHOTSTEAM | Superheated vapor, 600 °C / 16 MPa | 3574.4 | 3490.49 | 10 |
| SPENTSTEAM | Wet low-pressure exhaust, 10 kPa / ~45.8 °C, chosen h = 2400 | 2400.0 | 2316.09 | 1 |
| Condensate at condenser exit | Saturated liquid, 10 kPa / ~45.8 °C | 191.81 | 107.90 | Mass-conserving return |

T1 supplies h/p/T anchors; the chosen spent enthalpy corresponds to quality (2400−191.81)/(2583.9−191.81) ≈ **0.923**. This quality is derived once in the reference dataset, not continuously simulated. Condenser rejection is **2208.19 kJ/kg** to warm condensate, with a further **107.90 kJ/kg** released if the return is normalized to the existing cold-water identity. A future warm-water identity could retain the latter heat. Existing cold return must not credit that heat again.

Keep the useful 100:10:1:0.1 grade inventory ratio, expressed as rational **mass coefficients**. One water-inventory L is approximately 0.99816 kg at 20 °C (T1), rather than exactly 1 kg. To preserve existing 1 water L → 100 stored steam L and the return ratio, use a single declared **nominal 1 kg per feedwater-equivalent L** reference convention for steam transport; the ~0.18% distinction from a physical cold-water liter is explicit. For strict liquid density accounting, scale all steam coefficients consistently by 0.99816 instead; never apply it to one end only. Recommended initial contract: nominal kg water-equivalent, with water inventory identified as the 1 kg reference batch. This is the narrow water-volume normalization exception, not an energy multiplier.

### Legal transitions and conservative extractable work

| Transition, per kg water-equivalent | Net state-energy increase kJ | Candidate maximum credited work kJ | Residual handling |
| --- | ---: | ---: | --- |
| Cold water → ordinary steam | 2591.69 | 0 | 335.26 sensible + 2256.43 vaporization |
| Ordinary → hot | 376.00 net | 0 | Preparation must also meet pressure/entropy constraints; the compressor route below charges shaft work and rejects excess heat. |
| Hot → superhot | 279.60 net | 0 | 1→4 MPa preparation is not achievable by supplying this heat alone. |
| Superhot → ultrahot | 243.20 net | 0 | 4→16 MPa preparation uses the same route-dependent accounting; state relabeling cannot be free. |
| Ultrahot → superhot | 0 | 200 | Δh 243.2; uncredited 43.2 + machine losses rejected/retained once. |
| Superhot → hot | 0 | 220 | Δh 279.6; uncredited 59.6 + machine losses rejected/retained once. |
| Hot → ordinary | 0 | 300 | Δh 376.0; uncredited 76.0 + machine losses rejected/retained once. |
| Ordinary → spent | 0 | 250 | Δh 275.6; uncredited 25.6 + machine losses rejected/retained once. |
| Spent → warm condensate | 0 | 0 | Reject 2208.19; pumping/fans/vacuum are electrical work. |
| Warm condensate → cold reference | 0 | 0 | Reject 107.90, or retain in an explicitly warmer state. |

These work caps are **adopted conservative credits**, not NIST tabulated turbine outputs. They are less than the state enthalpy drops and less than the approximate isentropic expansion limits. Derivation from T1 entropy: superhot s = 6.9386 expands to ~2949.5 kJ/kg at 1 MPa, ideal drop ~381.7; ultrahot s = 6.6421 expands to ~3129.8 at 4 MPa, ideal drop ~444.6. Hot-to-near-atmosphere ideal drop is approximately 460, ordinary-to-10-kPa approximately 340; the corresponding adopted credits are lower. No work is obtained just by relabeling volume.

The net preparation increments above are **energy-balance differences, not standalone heat-only recipe charges**. Each selected higher-pressure grade has lower entropy than its predecessor. A compressor can supply shaft work, followed by cooling to the specified outlet state. Using T1 at the destination pressure and inlet entropy, with a deliberately adopted 70% compressor isentropic efficiency and ideal motor for this reference calculation:

| Upgrade route, per kg | Interpolated ideal compression work kJ | Candidate shaft/electric input kJ | Aftercooler rejection kJ | Retained state increment kJ |
| --- | ---: | ---: | ---: | ---: |
| Ordinary → hot | 514.93 | 735.61 | 359.61 | 376.00 |
| Hot → superhot | 419.81 | 599.73 | 320.13 | 279.60 |
| Superhot → ultrahot | 517.65 | 739.50 | 496.30 | 243.20 |

These are precomputed candidate compressor-and-aftercooler rows, not measured machine efficiencies. Motor losses add to input/rejection. Interpolation uses T1 at 1 MPa between 360/370 °C, 4 MPa between 500/520 °C, and 16 MPa between 700/720 °C. A physical boiler instead pressurizes liquid feed before heating; its total useful heat plus pump work supplies the cold-to-grade enthalpy rise, with losses extra and a sufficiently hot source. A reheater at unchanged pressure does not automatically confer the higher pressure class. Legacy steam “heating” adapters must declare which route they implement. Charging Δh as heat plus charging the same retained compression energy again would double-count; charging only Δh as electricity would undercharge the specified compressor route.

For a fixed nominal exhaust identity, actual work below Δh must leave an explicit dissipative heat account. One implementable discrete model sends nominal-state exhaust plus a residual-energy tail into the downstream exchanger/condenser, tracking the tail per batch, **or** books it as unrecoverable ambient rejection at the turbine. Recommended initial specification: book the difference as unrecoverable turbine rejection, without an extra recoverable fluid amount. This is a visible approximation, not disappearing energy or a second exhaust budget. Reverse preparation must supply at least Δh plus declared losses, divided explicitly between heat and shaft work; pressure and heat-source temperature remain constraints. Advanced direct expansions need their own precomputed transition rows rather than adding the stage budgets twice.

At an 85% machine factor, a full superhot train credits **654.5 kJ/kg** electricity from **3247.29 kJ/kg** heat, or **20.16% gross thermal-to-electric** before auxiliaries. Hot-only train: **467.5 / 2967.69 = 15.75%**. Ultrahot train: **824.5 / 3490.49 = 23.62%**. The remaining heat goes to the declared rejection/exhaust accounts. A 100% game machine factor still means 770/3247.29 = 23.71% for superhot, not 100% conversion of heat to electricity. These conservative discrete cycles do not reproduce every commercial plant's optimum regenerative/reheat cycle.

## Coolant reference candidates

Physical coolant heat is mass × Δh. Do not retain a 1:1 liter transition while using temperature-dependent actual volume expansion on only one side. The following approximate intervals are selected modeling states; integrate detailed properties only when freezing a particular coolant composition.

| Coolant | Candidate cold→hot interval / quantity basis | Physical reference and calculated energy |
| --- | --- | --- |
| Demineralized/borated light water | 280→325 °C, 15 MPa class, kg-equivalent inventory | T1 table 3, printed pp. 56–57: h(280) = 1233.0 kJ/kg; selected h(325) ≈ 1486.4, linearly interpolated between 320/330 °C. Candidate Δh = **253.4 kJ/kg**, with interpolation accuracy explicitly limited. Startup from the cold reference needs another 1149.09 kJ/kg total heat/work to reach the return state. Adopt dilute borated water as the same per-kg thermal table; solute concentration/moderation remain separate. |
| Heavy water | Separate isotope identity, same proposed pressure/temperature class | Requires heavy-water property data; do not copy ordinary-water enthalpy without documenting the approximation. |
| Helium | 300→750 °C, fixed standard-equivalent mass; actual loop pressure candidate 6 MPa | T2 cp ~5.190 kJ/(kg K), Δh ~2335.5 kJ/kg. 1 standard L ~0.1762 g, so ~411.5 J/standard L. Compression/circulation work separate. |
| CO2 | 250→410 °C, actual loop nominal pressure 2.6 MPa candidate | T2 representative high-temperature cp ~1.170 kJ/(kg K), rough Δh ~187.2 kJ/kg; use a final endpoint table. RTM's primary “bar” is a modeled reactor variable, not PU. |
| Sodium | Liquid 350→650 °C, reference density 0.857 kg/L at 500 °C | T2 cp ~1.262 kJ/(kg K); ~378.6 kJ/kg or **324.5 kJ/reference L**. No exchanger efficiency of 1.6. |
| Lead | Liquid 350→650 °C, reference density 10.724 kg/L at 500 °C | T2 cp ~0.145 kJ/(kg K); ~43.5 kJ/kg or **466.5 kJ/reference L**. No exchanger efficiency of 1.2. |
| Molten chloride salt | Candidate 500→650 °C; **composition must be fixed** | T2 example NaCl/KCl/MgCl2 at 600 °C: 1.800 kg/L, cp ~1.004 kJ/(kg K); ~271.1 kJ/reference L for the selected interval. Thorium concentration/burnup separate. |
| COOLANT, CRYOGEL, bromide “liquids”, WATZ/fictional coolant | Pending identity or explicit abstraction | Current synthesis and thermal/moderation claims conflict. No physically certified Δh until identity is settled. |

These are sensible-heat references, not universal reactor HU conversions. Molten thorium fuel salt is not pure molten Th metal, and not any arbitrary chloride salt with identical heat capacity. Cooling salt into THORIUM_SALT_DEPLETED changes fuel inventory as well as thermal state: nuclear depletion must be accounted separately from heat exchange.

## Process balances and physical requirements

The general ledger is: **feed chemical energy + incoming state enthalpy + heat supplied + electrical/shaft work + additional reactant energy = product chemical energy + outgoing state enthalpy + byproducts + rejected heat**. A reaction may be exothermic or endothermic; LHV and enthalpies of formation are alternative consistent chemical reference methods, not two independent credits. Separate electrical from thermal inputs even when quoting a total utility-energy survey.

### Petroleum unit operations

P1's onsite intensities converted using 158.9873 L/barrel and 1055.056 J/Btu. These are representative total onsite utility requirements, not all-electric demand and not thermodynamic minima.

| Operation | Reference Btu/bbl | Derived physical kJ/L feed or product | Implication |
| --- | ---: | ---: | --- |
| Atmospheric distillation | 109100 | 724.0 | Hot feed/preheat and reboiler utility ledger; do not add full preheat again if included here. |
| Vacuum distillation | 89100 | 591.3 | Vacuum work plus heat; reduced boiling temperature does not make separation free. |
| Hydrotreating | 80800 | 536.2 | Hydrogen reactant chemical energy separate from utilities. |
| Catalytic reforming | 263900 | 1751.3 | Reformate, hydrogen/light gases and coke/losses must balance. |
| Catalytic cracking | 182800 | 1213.1 | Cracking heat, coke regeneration, gas/fractions; a spent-steam token alone is insufficient. |
| Hydrocracking | 158900 | 1054.5 | Distinct hydrogen and thermal inputs. |
| Coking/visbreaking | 147700 | 980.2 | Preserve coke, gas, liquid and losses; current disappearance of most feed cannot be called a separator efficiency. |
| Alkylation | 246700 | 1637.2 per L **product** | Survey denominator is product; do not apply to feed indiscriminately. RTM chloromethane route is a different reaction abstraction. |

For the current 100-L crude recipe, a provisional physical assay gives 85 kg input, about 49 kg heavy +17.5 kg naphtha +12 kg light +0.007 kg methane-proxy petroleum gas, before sulfur. Approximately **6.49 kg is missing** if the gas output is only 10 standard L. The gas fraction must become a mass-based yield, e.g. thousands of standard L for kg of gas, with corrected liquid cut masses and sulfur/byproduct quantities. “Outputs add to 100 liters” is not a mass balance across phases.

The current 100 L light-oil split into 55 L diesel +45 L kerosene has 80 kg nominal feed versus 81.815 kg outputs, and 3440 MJ feed versus 3546.85 MJ product chemical energy using the proposed assays. The remedy is to freeze the cut composition/yields consistently, not add a magic positive separation energy to manufacture mass. Those assay choices may be adjusted within credible physical ranges, with a single canonical value per resulting stream.

Mixing diesel/reformate at 900:100 yields **35.471 GJ per 1000 L**, by weighted inputs; a cetane/octane suitability change is possible, a chemical-energy bonus is not. Leaded fuel must preserve parent volume except explicit additive volume/mass. Petroleum gas → LPG must conserve molecular mass: 2000 standard L methane-proxy cannot become 1000 L liquid propane/butane by compression alone, and methane is chemically different from propane.

### Hydrogen, coal, biofuel and synthetic liquids

| Process | Canonical physical/material equation | Energy boundary / current mismatch |
| --- | --- | --- |
| Water electrolysis | 9 kg water → ~1 kg H2 +8 kg O2 | P2 system example 55 kWh/kg = **198 MJ/kg H2**; chemical LHV ~120 MJ/kg; ~78 MJ is losses/heat. Existing 2000 liquid L →400 “LH2 L” +200 “LOX L” is neither mass-conserving nor a gas-standard-volume yield. |
| Electrolysis→combustion loop | Electricity →H2 →electricity; water returns | At 30% combustion conversion, 36/198 ≈ **18.2%** electrical round-trip, before compression/storage. At every global scale this stays <1. No current exploit is claimed from chemical-uplift alone. |
| Hydrogen compression/liquefaction | Same H2 molecules, altered storage state | P3 adds 7.2–14.4 MJ/kg for cited compression, or 28.8–47.9 MJ/kg for liquefaction. Do not charge both unless both operations actually occur. |
| Steam methane reforming + complete shift | CH4 +2H2O(g) →CO2 +4H2 | P4. Using the cited NIST formation references: approximately **165 kJ/mol CH4** net reaction heat, plus steam preparation and process losses. 1000 standard L CH4 needs ~1.59 kg reacted water, yields ~0.355 kg H2 (~4000 standard L) and ~1.94 kg CO2. Current 1000 ultrahot L denotes 10000 kg water-equivalent and is vastly oversized. |
| Carbon/steam gasification | C +H2O(g) →CO +H2 | Derived NIST formation difference: **~131.3 kJ/mol carbon**, ~10.9 MJ/kg carbon supplied as reaction heat; self-heated gasifiers oxidize part of feed instead. Coal includes ash/volatiles; no exact pure-carbon identity for a coal item. |
| Coal hydrogenation / Fischer–Tropsch | Coal-derived carbon/syngas +H2 →liquids +water/gas/char | Hydrogen energy is an input, not a catalyst. Current 1 coal item +500 H2 inventory L →1000 heavy-oil L cannot close under a 1 kg coal mass. Freeze carbon/H balance and recoverable product mass before setting utility power. |
| Methanol synthesis | CO +2H2 →CH3OH(l) | 1000 standard L 50:50 syngas +1000 standard L H2 contains ~22 mol CO, allowing only ~0.705 kg (~0.890 L) methanol, not 1000 liquid L. Unreacted H2/recycle plus released reaction heat must be declared. |
| Fermentation | C6H12O6 →2C2H5OH +2CO2 | Stoichiometric mass yield ~0.511 kg ethanol/kg glucose; sugar assay and water broth explicit. Current sugar→ethanol liquefactor route compresses fermentation, separation and heat. P9 identifies process family. |
| Biodiesel | Oil/fat +methanol →fatty-acid methyl esters +glycerol | P9. Current BIOFUEL recipes include biogas/ethanol synthesis and oil/wood-oil mixing; these do not identify one biodiesel chemistry. Retain BIOFUEL only after choosing synthesis versus blend identity and accounting byproducts. |
| Pyrolysis/coking | Biomass/coal/residue →oil/tar +gas +char/coke +water +heat losses | P5 product composition; char and vapor heat cannot be silently omitted. Burn a declared fraction for self-heating or charge external heat/work. |
| Air separation | Air components separated; phase changes optional | Moles retained. Current 100 gas L →77 LN2 L +21 LOX L is not physical cryogenic yield. The liquid products need density/molar conversions and refrigeration work, or stay distinct gas outputs. |
| Solidification / melting | Same material mass across phase | Current 1 ingot lead ↔100 fluid L contradicts a 1 kg ingot (~0.093 L at the T2 reference density). Cooling/fusion heat and heating must be paid; “solid fuel” made from residue is synthesis/binding, not freezing gasoline. |

Numerical reaction examples use the reference state; they are lower bounds/material checks, not complete industrial heat exchanger or reactor designs. Missing solvent concentrations, trace chemistry, exact item assays and catalyst consumption stay explicit; fictional recipes need a declared gameplay budget instead of a false stoichiometric story.

## Machine physical envelopes: operation, average, nameplate, throughput

An envelope without a throughput/material premise cannot determine draw. The rows below separate source reference from **constructed design examples**. For derived examples, Pavg = operation energy × operations/second, Pnameplate ≥ active draw, and electrical/thermal channels remain separate. Press peak force/velocity is not mean motor demand. Industrial fabrication is a bundle of processes, not a universal kWh/kg material law.

| Family | Physical reference or explicit constructed envelope | Operation/throughput and rating distinction |
| --- | --- | --- |
| Workshop furnace | Constructed 15 kWe at 1.5 MJ/kg electrical; P6 supplies a comparison for electrical melting energy | 1 kg/100 s; a 15 kW nameplate cannot process 1 kg every 5 s at that energy. Smaller parts or hotter feed can shorten time. |
| Bulk EAF | P6: 400–500 kWh/short ton = **441–551 kWh/metric tonne**, ~1.59–1.98 MJ/kg electrical | At 1 t/h, ~0.44–0.55 MW average active; 10 t/h ~4.4–5.5 MW. Nameplate plus startup/auxiliaries separately. |
| Foundry/melting/heat treatment | Material sensible +latent heat +losses, measured endpoint table required | Constructed 0.1–5 MWth for 0.1–3 kg/s; alloying/reduction reactants and slag outside simple remelting. |
| Crushers/grinding | P8 separates ore hardness, reduction ratio and comminution stages; [DOE SAG study](https://ntrl.ntis.gov/NTRL/dashboard/searchResults/titleDetail/DE2006861644.xhtml) gives 2–7.5 kWh per cited ton | Rough constructed 5–30 kJ/kg after ton-basis normalization; at 1 kg/s 5–30 kW, at 100 kg/s 0.5–3 MW. A crusher and fine grinder need different recipes. |
| Pumps | P7; P = Δp × volumetric flow / efficiency | Assumed 70% efficiency: water 10 L/s, 20 m head →2.80 kW; 100 L/s →28.0 kW. At current 200 m3/s and 20 m head, ~56 MW, not 10 kW. Zero head is a different boundary. |
| Gas compressors | Gas-law isothermal lower bound nRT ln(p2/p1), plus actual efficiency/intercooling | Constructed 1→8 bar, 100 standard L/s, 300 K: minimum ~22.8 kW, ~32.6 kW at assumed 70%; compression stores/rejects energy, not fuel. |
| Electrolysers | P2 55 kWh/kg H2 system | 1 kg/h →55 kW, 10 kg/h →550 kW, 100 kg/h →5.5 MW; water 9× product mass. Nameplate is not tank volume. |
| Refineries | P1 724 kJ/L atmospheric unit utility basis | 1 L/s →724 kW total utilities; 10 L/s →7.24 MW. Constructed 5% electrical/95% thermal split for scale examples only; real split needs selected technology. |
| Chemical plants | Specific reaction/material/state ledger; P1–P4 examples | Constructed workshop 3–15 kWe utilities; industrial 20–500 kWe +10 kW–10 MWth. No universal rate can represent fermentation and high-temperature synthesis. |
| Presses/welding/machining | Constructed operation budgets, based on force ×distance, arc electrical work, process time | A 100 kJ weld in 10 s draws 10 kW; a 20 kJ forming stroke in 0.1 s is 200 kW peak but one stroke/10 s averages 2 kW before losses. Flywheel/buffer can cover the peak. |
| Fabrication / industrial fabrication | Constructed sum of selected operations | 3 kW basic assembly for 30 s =90 kJ/job. Eight 25 kW active lanes =200 kW, not a new manufacturing capability by itself. Casting/welding/precision operations can be separate inputs. |
| Mining equipment | P8: drilling/digging/handling/ventilation modeled separately | Constructed 0.05–5 MW plant envelopes; block/item yield and laser excavation remain abstractions. No fabricated universal “energy per Minecraft block”. |
| Nuclear auxiliaries | Pump, blower, control, treatment and rejection equations | Constructed 3–8% gross electric auxiliary allowance for plant examples, **not measured RTM**. Gas coolant pumping and low-pressure condensers may need more. Black-start power must precede nuclear export. |
| Solar/geothermal | Explicit irradiance ×collector area ×efficiency / reservoir heat extraction | No physical MW rating inferred from mirror count, permanent geyser block or electrical storage capacity; candidate ratings need those source premises. |

Thermal storage duration is Eheat/transfer power; fuel autonomy is fuel chemical energy ×equipment efficiency/load; battery duration is Eelectric/load **subject to discharge W**. They are three different accounts. These equations, not one multiplier on every machine, determine the future physical envelopes.

## Ledger release conditions

The steam candidate is numerically closed at its specified reference states. Common fuel proxies are sufficient to compare scales. The unresolved assays and reactor boundaries above are intentionally **unreleased entries**, not permission to invent values during code edits. Phase 5 can start on the canonical accounting/representation contract after approval; a full fuel/recipe/reactor rebalance cannot begin until the readiness checklist in the main document is closed for its chosen implementation tranche.
