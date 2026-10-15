# Petroleum reference

Current default bills at 20 TPS. For operating choices use [Petroleum](../gameplay/petroleum.md). For source, batch persistence and override rules use [Process reference](process_reference.md).

## Machine envelopes

| Existing machine | Decision and physical boundary |
| --- | --- |
| Oil well /pumpjack | Keep geology, depletion, drilling and upgrades; cap oil extraction at 6/24 L/min using a persisted extraction timestamp. Stock electrical auxiliaries are 1/3 kWe while running; afterburner debits owned gas through the existing fuel account. |
| Crude heating boiler | Reserve 1 L and pay 561 kJ to reach the declared hot state; 24 L/min material ceiling. Legacy heat-exchanger crude heating is disabled until that exchanger has a calibrated heat ledger. Steam and coolant paths use their own thermal contracts. |
| Atmospheric refinery | Heated separation, 24 L/min, 163 kJ/L thermal plus 36 kJ/L electrical; no free sulfur item. Existing damage/fire/repair and storage remain. |
| Fraction tower | Heated separation, 12 L/min, 5 kJ/kg auxiliaries; light/coker feed 440 kJ/kg at 200 C, heavy 740 at 350 C, smear 640 at 300 C. Stack passes feed; base supplies heat, each working stage needs electrical connection. Specialist non-petroleum processing uses its own current bills. |
| Vacuum refinery | Heavy-residue separation at 400 C, declared internal 0.1 bar absolute; 12 L/min, 1360 kJ/kg heat plus 40 kJ/kg vacuum/pump work. Saved class-2 crude inlet has a paid cold-feed boundary bill terminating in the canonical atmospheric cuts. |
| Catalytic cracker | Feed-class mass yields, coke coproduct, 12 L/min; 2 MJ/kg heat (kerosene 3.1), 50 kJ/kg auxiliaries, 500 C. Old steam stocks remain drainable but cannot substitute for calibrated heat. Added coke inventory access and power ports. |
| Hydrotreater | 12 L/min, approximately 0.5 wt% added H2 gas at class 1; 98% treated liquid /2% sour stream by combined mass; 400 kJ/kg heat and 100 kJ/kg auxiliaries at 350 C. Existing catalyst remains required. |
| Catalytic reformer | 12 L/min, 94/4.5/1.5 mass% reformate/reform gas/H2; 2.6 MJ/kg heat and 100 kJ/kg auxiliaries at 500 C. Existing catalyst remains required. |
| Delayed coker | 6 L/min; accounts oil, gas, coke and wet-feed water; 3.3 MJ/kg heat and 20 kJ/kg auxiliaries, 500 C. Added gas/water stores while preserving old inlet/oil slots. |
| Coke oven /combination furnace | Retain multipurpose furnace. Coal/lignite, logs/saplings/briquettes and four solid-tar families use owned heat-only carbonization bills at 500 C and up to 200 kWth. Mineral/clay/bedrock roasting stays on its existing nominal path. NEI shows ten-batch whole products; fractional products remain owned. |
| Alkylation unit | Ethylene gas +liquid LPG into alkylate/reform gas; 100 C paid heat/work. Recycled catalysts/internal equipment are an adopted abstraction. |
| Chemical Reactor, single and industrial lanes | Paid synthesis, gas recovery, fermentation, extraction, reclamation and tar melting; each physical lane is limited to 8 kWe, 100 kWth, 12 liquid L/min and 6000 standard gas L/min feed. Industrial lanes also require the sealed cooling loop. |
| Mixer /solidifier | Weighted blends and trace additive; bitumen solidification owns literal mass. Physical batch duration/work remain authoritative. |
| Compressor | Refinery, reform, sour, coker and synthesis gas pressure conversions use the existing charged phase/pressure account, including decompression work. LPG separation belongs to the paid reactor. |

## Exact batch equations

Every row is a batch equation: paid input counts @network pressure class →product masses in kg. Liquid input counts are reference L; gas counts are dry standard-equivalent L; solid counts are full declared items. The total input kg is the sum of the mass-closed products (display rounded to six decimals); validation uses exact integer mass units. Whole inventory outputs are emitted only after completion, with each species' remainder retained. `VENT_` is an explicit environmental loss, never a saleable product or heat allowance.

The energy equation is chemical input +sensible input +paid electrical work +paid heat =chemical product +sensible product +rejected heat. Rejected heat is currently a declared loss, not a recoverable export. Nameplate work/heat powers below are batch averages at 20 TPS; insufficient supply pauses the batch.

| Bill | Paid inputs | Input kg | Products kg | Seconds | kWe | kWth | C | Rejected MJ |
| --- | --- | ---: | --- | ---: | ---: | ---: | ---: | ---: |
| `heat:OIL` | OIL=1@0 | 0.85 | HOTOIL=0.850000000 | 2.5 | 0 | 224.4 | 350 | 0 |
| `heat:OIL_DS` | OIL_DS=1@0 | 0.85 | HOTOIL_DS=0.850000000 | 2.5 | 0 | 224.4 | 350 | 0 |
| `heat:CRACKOIL` | CRACKOIL=1@0 | 0.85 | HOTCRACKOIL=0.850000000 | 2.5 | 0 | 224.4 | 350 | 0 |
| `heat:CRACKOIL_DS` | CRACKOIL_DS=1@0 | 0.85 | HOTCRACKOIL_DS=0.850000000 | 2.5 | 0 | 224.4 | 350 | 0 |
| `refinery:HOTOIL` | HOTOIL=1@0 | 0.85 | HEAVYOIL=0.387989182; NAPHTHA=0.147510818; LIGHTOIL=0.297500000; PETROLEUM=0.017000000 | 2.5 | 14.4 | 65.2 | 350 | 0.76 |
| `refinery:HOTOIL_DS` | HOTOIL_DS=1@0 | 0.85 | HEAVYOIL=0.387989182; NAPHTHA_DS=0.147510818; LIGHTOIL_DS=0.297500000; PETROLEUM=0.017000000 | 2.5 | 14.4 | 65.2 | 350 | 0.76 |
| `refinery:HOTCRACKOIL` | HOTCRACKOIL=1@0 | 0.85 | NAPHTHA_CRACK=0.127500000; LIGHTOIL_CRACK=0.212500000; AROMATICS=0.455265152; UNSATURATEDS=0.054734848 | 2.5 | 14.4 | 65.2 | 350 | 0.76 |
| `refinery:HOTCRACKOIL_DS` | HOTCRACKOIL_DS=1@0 | 0.85 | NAPHTHA_CRACK=0.127500000; LIGHTOIL_CRACK=0.212500000; AROMATICS=0.455265152; UNSATURATEDS=0.054734848 | 2.5 | 14.4 | 65.2 | 350 | 0.76 |
| `fraction:LIGHTOIL` | LIGHTOIL=1@0 | 0.8 | DIESEL=0.440000000; KEROSENE=0.360000000 | 5 | 0.8 | 70.4 | 200 | 0.356 |
| `fraction:LIGHTOIL_DS` | LIGHTOIL_DS=1@0 | 0.8 | DIESEL=0.440000000; KEROSENE=0.360000000 | 5 | 0.8 | 70.4 | 200 | 0.356 |
| `fraction:LIGHTOIL_CRACK` | LIGHTOIL_CRACK=1@0 | 0.8 | DIESEL_CRACK=0.440000000; KEROSENE=0.360000000 | 5 | 0.8 | 70.4 | 200 | 0.356 |
| `fraction:LIGHTOIL_VACUUM` | LIGHTOIL_VACUUM=1@0 | 0.8 | DIESEL=0.440000000; KEROSENE=0.360000000 | 5 | 0.8 | 70.4 | 200 | 0.356 |
| `fraction:HEAVYOIL` | HEAVYOIL=1@0 | 0.98 | HEATINGOIL=0.196000000; BITUMEN=0.784000000 | 5 | 0.98 | 145.04 | 350 | 0.52626 |
| `fraction:HEAVYOIL_VACUUM` | HEAVYOIL_VACUUM=1@0 | 0.98 | HEATINGOIL_VACUUM=0.196000000; BITUMEN=0.784000000 | 5 | 0.98 | 145.04 | 350 | 0.52626 |
| `fraction:SMEAR` | SMEAR=1@0 | 0.98 | LUBRICANT=0.490000000; BITUMEN=0.490000000 | 5 | 0.98 | 125.44 | 300 | 0.5047 |
| `fraction:OIL_COKER` | OIL_COKER=1@0 | 0.833 | HEATINGOIL=0.833000000 | 5 | 0.833 | 73.304 | 200 | 0.370685 |
| `fraction:NAPHTHA_COKER` | NAPHTHA_COKER=1@0 | 0.7 | NAPHTHA_CRACK=0.700000000 | 5 | 0.7 | 61.6 | 200 | 0.3115 |
| `vacuum:HEAVYOIL` | HEAVYOIL=1@0 | 0.98 | HEAVYOIL_VACUUM=0.490000000; SMEAR=0.245000000; LIGHTOIL_VACUUM=0.048457278; LUBRICANT=0.196542722 | 5 | 7.84 | 266.56 | 400 | 1.372 |
| `vacuum:OIL` | OIL=1@2 | 0.85 | HEAVYOIL=0.387989182; NAPHTHA=0.147510818; LIGHTOIL=0.297500000; PETROLEUM=0.017000000 | 5 | 6.8 | 200 | 400 | 1.034 |
| `vacuum:OIL_DS` | OIL_DS=1@2 | 0.85 | HEAVYOIL=0.387989182; NAPHTHA_DS=0.147510818; LIGHTOIL_DS=0.297500000; PETROLEUM=0.017000000 | 5 | 6.8 | 200 | 400 | 1.034 |
| `crack:OIL` | OIL=1@0 | 0.85 | CRACKOIL=0.722500000; PETROLEUM=0.085000000; PETCOKE_ITEM=0.042500000 | 5 | 8.5 | 340 | 500 | 1.672472 |
| `crack:OIL_DS` | OIL_DS=1@0 | 0.85 | CRACKOIL_DS=0.722500000; PETROLEUM=0.085000000; PETCOKE_ITEM=0.042500000 | 5 | 8.5 | 340 | 500 | 1.672472 |
| `crack:NAPHTHA` | NAPHTHA=1@0 | 0.7 | NAPHTHA_CRACK=0.595000000; PETROLEUM=0.070000000; PETCOKE_ITEM=0.035000000 | 5 | 7 | 280 | 500 | 1.60833 |
| `crack:NAPHTHA_DS` | NAPHTHA_DS=1@0 | 0.7 | NAPHTHA_CRACK=0.595000000; PETROLEUM=0.070000000; PETCOKE_ITEM=0.035000000 | 5 | 7 | 280 | 500 | 1.60833 |
| `crack:NAPHTHA_COKER` | NAPHTHA_COKER=1@0 | 0.7 | NAPHTHA_CRACK=0.595000000; PETROLEUM=0.070000000; PETCOKE_ITEM=0.035000000 | 5 | 7 | 280 | 500 | 1.60833 |
| `crack:HEAVYOIL` | HEAVYOIL=1@0 | 0.98 | CRACKOIL=0.686000000; PETROLEUM=0.049000000; PETCOKE_ITEM=0.245000000 | 5 | 9.8 | 392 | 500 | 2.267531 |
| `crack:HEAVYOIL_VACUUM` | HEAVYOIL_VACUUM=1@0 | 0.98 | CRACKOIL=0.686000000; PETROLEUM=0.049000000; PETCOKE_ITEM=0.245000000 | 5 | 9.8 | 392 | 500 | 2.267531 |
| `crack:BITUMEN` | BITUMEN=1@0 | 0.99 | CRACKOIL=0.693000000; PETROLEUM=0.049500000; PETCOKE_ITEM=0.247500000 | 5 | 9.9 | 396 | 500 | 1.904569 |
| `crack:SMEAR` | SMEAR=1@0 | 0.98 | CRACKOIL=0.686000000; PETROLEUM=0.049000000; PETCOKE_ITEM=0.245000000 | 5 | 9.8 | 392 | 500 | 1.851031 |
| `crack:RECLAIMED` | RECLAIMED=1@0 | 0.9 | CRACKOIL=0.630000000; PETROLEUM=0.045000000; PETCOKE_ITEM=0.225000000 | 5 | 9 | 360 | 500 | 1.902426 |
| `crack:DIESEL` | DIESEL=1@0 | 0.833 | KEROSENE=0.666400000; PETROLEUM=0.124950000; PETCOKE_ITEM=0.041650000 | 5 | 8.33 | 333.2 | 500 | 0.984124 |
| `crack:DIESEL_CRACK` | DIESEL_CRACK=1@0 | 0.833 | KEROSENE=0.666400000; PETROLEUM=0.124950000; PETCOKE_ITEM=0.041650000 | 5 | 8.33 | 333.2 | 500 | 0.984124 |
| `crack:DIESEL_REFORM` | DIESEL_REFORM=1@0 | 0.8227 | KEROSENE=0.658160000; PETROLEUM=0.123405000; PETCOKE_ITEM=0.041135000 | 5 | 8.227 | 329.08 | 500 | 1.066855 |
| `crack:DIESEL_CRACK_REFORM` | DIESEL_CRACK_REFORM=1@0 | 0.8227 | KEROSENE=0.658160000; PETROLEUM=0.123405000; PETCOKE_ITEM=0.041135000 | 5 | 8.227 | 329.08 | 500 | 1.066855 |
| `crack:HEATINGOIL` | HEATINGOIL=1@0 | 0.833 | KEROSENE=0.666400000; PETROLEUM=0.124950000; PETCOKE_ITEM=0.041650000 | 5 | 8.33 | 333.2 | 500 | 0.984124 |
| `crack:HEATINGOIL_VACUUM` | HEATINGOIL_VACUUM=1@0 | 0.833 | KEROSENE=0.666400000; PETROLEUM=0.124950000; PETCOKE_ITEM=0.041650000 | 5 | 8.33 | 333.2 | 500 | 0.984124 |
| `crack:KEROSENE` | KEROSENE=1@0 | 0.8 | UNSATURATEDS=0.640000000; PETROLEUM=0.080000000; PETCOKE_ITEM=0.080000000 | 5 | 8 | 496 | 500 | 0.910091 |
| `crack:XYLENE` | XYLENE=1@0 | 0.86 | AROMATICS=0.602000000; PETROLEUM=0.043000000; PETCOKE_ITEM=0.215000000 | 5 | 8.6 | 344 | 500 | 3.357274 |
| `crack:REFORMATE` | REFORMATE=1@0 | 0.73 | UNSATURATEDS=0.511000000; REFORMGAS=0.036500000; PETCOKE_ITEM=0.182500000 | 5 | 7.3 | 292 | 500 | 1.674554 |
| `crack:LPG` | LPG=1@0 | 0.52 | UNSATURATEDS=0.416000000; PETROLEUM=0.078000000; PETCOKE_ITEM=0.026000000 | 5 | 5.2 | 208 | 500 | 1.435939 |
| `hydro:OIL` | OIL=1@0; HYDROGEN_GAS=48@1 | 0.854261 | OIL_DS=0.837175377; SOURGAS=0.017085212 | 5 | 17 | 68 | 350 | 0.737683 |
| `hydro:CRACKOIL` | CRACKOIL=1@0; HYDROGEN_GAS=48@1 | 0.854261 | CRACKOIL_DS=0.837175377; SOURGAS=0.017085212 | 5 | 17 | 68 | 350 | 0.737683 |
| `hydro:NAPHTHA` | NAPHTHA=1@0; HYDROGEN_GAS=40@1 | 0.70355 | NAPHTHA_DS=0.689479481; SOURGAS=0.014071010 | 5 | 14 | 56 | 350 | 0.633892 |
| `hydro:DIESEL_CRACK` | DIESEL_CRACK=1@0; HYDROGEN_GAS=47@1 | 0.837172 | DIESEL=0.820428390; SOURGAS=0.016743437 | 5 | 16.66 | 66.64 | 350 | 0.731454 |
| `hydro:DIESEL_CRACK_REFORM` | DIESEL_CRACK_REFORM=1@0; HYDROGEN_GAS=47@1 | 0.826872 | DIESEL_REFORM=0.810334390; SOURGAS=0.016537437 | 5 | 16.454 | 65.816 | 350 | 0.727805 |
| `hydro:COALOIL` | COALOIL=1@0; HYDROGEN_GAS=48@1 | 0.854261 | OIL_DS=0.837175377; SOURGAS=0.017085212 | 5 | 17 | 68 | 350 | 0.737683 |
| `reform:NAPHTHA` | NAPHTHA=1@0 | 0.7 | REFORMATE=0.658000000; REFORMGAS=0.031500000; HYDROGEN_GAS=0.010500000 | 5 | 14 | 364 | 500 | 0.809627 |
| `reform:NAPHTHA_DS` | NAPHTHA_DS=1@0 | 0.7 | REFORMATE=0.658000000; REFORMGAS=0.031500000; HYDROGEN_GAS=0.010500000 | 5 | 14 | 364 | 500 | 0.809627 |
| `reform:NAPHTHA_CRACK` | NAPHTHA_CRACK=1@0 | 0.7 | REFORMATE=0.658000000; REFORMGAS=0.031500000; HYDROGEN_GAS=0.010500000 | 5 | 14 | 364 | 500 | 0.809627 |
| `reform:NAPHTHA_COKER` | NAPHTHA_COKER=1@0 | 0.7 | REFORMATE=0.658000000; REFORMGAS=0.031500000; HYDROGEN_GAS=0.010500000 | 5 | 14 | 364 | 500 | 0.809627 |
| `coker:HEAVYOIL` | HEAVYOIL=1@0 | 0.98 | OIL_COKER=0.735000000; GAS_COKER=0.049000000; PETCOKE_ITEM=0.196000000 | 10 | 1.96 | 323.4 | 500 | 1.481612 |
| `coker:HEAVYOIL_VACUUM` | HEAVYOIL_VACUUM=1@0 | 0.98 | OIL_COKER=0.735000000; GAS_COKER=0.049000000; PETCOKE_ITEM=0.196000000 | 10 | 1.96 | 323.4 | 500 | 1.481612 |
| `coker:SMEAR` | SMEAR=1@0 | 0.98 | OIL_COKER=0.735000000; GAS_COKER=0.049000000; PETCOKE_ITEM=0.196000000 | 10 | 1.96 | 323.4 | 500 | 1.065112 |
| `coker:BITUMEN` | BITUMEN=1@0 | 0.99 | OIL_COKER=0.742500000; GAS_COKER=0.049500000; PETCOKE_ITEM=0.198000000 | 10 | 1.98 | 326.7 | 500 | 1.110631 |
| `coker:RECLAIMED` | RECLAIMED=1@0 | 0.9 | NAPHTHA_COKER=0.630000000; GAS_COKER=0.027000000; WATER=0.045000000; PETCOKE_ITEM=0.198000000 | 10 | 1.8 | 297 | 500 | 2.830598 |
| `coker:COALCREOSOTE` | COALCREOSOTE=1@0 | 1.1 | NAPHTHA_COKER=0.440000000; GAS_COKER=0.055000000; WATER=0.385000000; PETCOKE_ITEM=0.220000000 | 10 | 2.2 | 363 | 500 | 3.918034 |
| `alkyl:UNSATURATEDS` | UNSATURATEDS=1000@0; LPG=1@0 | 1.755223 | REFORMATE=1.720118991; REFORMGAS=0.035104469 | 20 | 2.5 | 5 | 100 | 4.817472 |
| `cokeoven:COAL_ITEM` | COAL_ITEM=1 | 1 | COKE_ITEM=0.700000000; COALCREOSOTE=0.220000000; VENT_WATER=0.080000000 | 10 | 0 | 200 | 500 | 1.9 |
| `cokeoven:COAL_BRIQUETTE_ITEM` | COAL_BRIQUETTE_ITEM=1 | 1 | COKE_ITEM=0.700000000; COALCREOSOTE=0.220000000; VENT_WATER=0.080000000 | 10 | 0 | 200 | 500 | 1.9 |
| `cokeoven:LIGNITE_ITEM` | LIGNITE_ITEM=1 | 1 | LIGNITE_COKE_ITEM=0.500000000; COALCREOSOTE=0.220000000; VENT_WATER=0.280000000 | 10 | 0 | 200 | 500 | 1.34 |
| `cokeoven:LIGNITE_BRIQUETTE_ITEM` | LIGNITE_BRIQUETTE_ITEM=1 | 1 | LIGNITE_COKE_ITEM=0.500000000; COALCREOSOTE=0.220000000; VENT_WATER=0.280000000 | 10 | 0 | 200 | 500 | 1.34 |
| `cokeoven:WOOD_LOG` | WOOD_LOG=1 | 4 | CHARCOAL_ITEM=1.000000000; WOODOIL=1.200000000; VENT_WATER=1.800000000 | 30 | 0 | 200 | 500 | 18.5 |
| `cokeoven:SAPLING_ITEM` | SAPLING_ITEM=1 | 1 | WOOD_ASH_ITEM=0.100000000; WOODOIL=0.200000000; VENT_WATER=0.700000000 | 7.5 | 0 | 200 | 500 | 13.7 |
| `cokeoven:WOOD_BRIQUETTE_ITEM` | WOOD_BRIQUETTE_ITEM=1 | 4 | CHARCOAL_ITEM=1.000000000; WOODOIL=1.200000000; VENT_WATER=1.800000000 | 30 | 0 | 200 | 500 | 18.5 |
| `cokeoven:TAR_CRUDE_ITEM` | TAR_CRUDE_ITEM=1 | 1 | PETCOKE_ITEM=0.250000000; OIL_COKER=0.750000000 | 16.5 | 0 | 200 | 500 | 2.675 |
| `cokeoven:TAR_CRACK_ITEM` | TAR_CRACK_ITEM=1 | 1 | PETCOKE_ITEM=0.250000000; OIL_COKER=0.750000000 | 16.5 | 0 | 200 | 500 | 5.225 |
| `cokeoven:TAR_COAL_ITEM` | TAR_COAL_ITEM=1 | 1 | COKE_ITEM=0.250000000; COALCREOSOTE=0.750000000 | 16.5 | 0 | 200 | 500 | 3.25 |
| `cokeoven:TAR_WOOD_ITEM` | TAR_WOOD_ITEM=1 | 1 | COKE_ITEM=0.250000000; WOODOIL=0.750000000 | 16.5 | 0 | 200 | 500 | 0.5 |
| `solidify:BITUMEN` | BITUMEN=1@0 | 0.99 | TAR_CRUDE_ITEM=0.990000000 | 5 | 1 | 0 | 20 | 0.2624 |

## Chemical fuel routes

Current fuel chemistry, recovery and blends are detailed in [process reference](process_reference.md). Exact integer equations, coproduct masses, duration, pressure and work/heat are defined by [ChemicalFuelProcesses](../../src/main/java/com/hbm/realsim/ChemicalFuelProcesses.java); ordinary chemistry and aqueous carrier handling use [ChemistryProcesses](../../src/main/java/com/hbm/realsim/ChemistryProcesses.java). `mass(name, count)`, `ratio(...)` and the output mass arrays define the complete bill; these are executable default specifications rather than a second independently rounded table. In particular, methane reforming returns excess liquid water, and coal gasification uses treated water.

