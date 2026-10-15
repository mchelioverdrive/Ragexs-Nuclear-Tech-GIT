# Petroleum and fuel substance coefficients

Current default mass/LHV coefficients for petroleum, supported fuel streams and carbonization feed. Liquid unit = litre; gas unit = dry standard litre at 0°C / 1 bar; solid unit = the stated item. These are RTM representative assays. Source definitions in [PhysicalRegistry](../../src/main/java/com/hbm/realsim/PhysicalRegistry.java) and `ChemicalFuelProcesses` control exact rounding. Hot crude carries 660 kJ/kg sensible energy and hot bitumen 260 kJ/kg; heating never adds chemical LHV.

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
| `POLYMER_ITEM` | solid | 1 | 47.2 | Polyethylene parcel bound specifically to `ingot_polymer` for process inputs and outputs; other plastics keep their own identities. |
| `CHAR_ITEM` | solid | 1 | 32.8 | Carbon-char parcel bound to the soot metadata of `powder_ash`; ordinary ash is not this feed. |
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



Other chemical, cryogenic, molten and specialist fluid conventions are in [fluid reference](fluid_reference.md). An assay does not grant fuel acceptance: [FuelSuitability](../../src/main/java/com/hbm/realsim/FuelSuitability.java) and native fluid traits define consumers independently.
