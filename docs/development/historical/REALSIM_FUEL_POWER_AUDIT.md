# RTM fuel and pre-nuclear power audit — supplied prior work

> **Historical evidence.** Preserved incoming investigation and implementation record; values describe its recorded stages. For current behavior use the [current reference](../generator_reference.md).

Preserved from the audit supplied by the user on 2026-10-12. The original audit date was not supplied. The attachment ends at the word "Suggested"; its missing continuation is not reconstructed. Links are normalized for this repository. The statement below that no documentation changed describes the earlier investigation. This preservation is documentation-only.

For current verification, canonical substance decisions, physical references, scale comparison, and supersession, see [RealSim phases 1–4](REALSIM_PHASES_1_4.md). Figures below remain the prior audit snapshot and are not a Phase 5 specification.

---

**RTM’s pre-nuclear balance needs more than a diesel buff.** Its electrical units are defined consistently, but fuel heat, engine output, steam production, processing costs, and fluid quantities do not share a consistent physical model.

The largest confirmed problems are:

- The same diesel has separate **275 kJ burner** and **685 kJ engine** budgets.
- The diesel generator credits electricity equal to **187% of the burner budget**.
- Gas-turbine electricity plus recoverable steam can exceed its burner budget by approximately **6.6×**.
- Some separation and blending recipes increase chemical energy without accounting for that increase.
- Other fuel-production routes consume more electricity than their product can subsequently generate.
- The strongest fossil route can exceed the first Magnox’s electrical output.
- Several machine names and descriptions overstate what their mechanics actually implement.

**No code, configuration, or documentation was changed.** This is a source and arithmetic review, not an observed gameplay test.

I used the existing [industrial-scale audit](INDUSTRIAL_SCALE_AUDIT.md), [thermal-power design](REALSIM_THERMAL_POWER_DESIGN.md), [Magnox documentation](../magnox_reference.md), and [first-power dependency audit](RBMK_FIRST_POWER_DEPENDENCIES.md), then checked the relevant current source. One correction to the earlier audit: the current default RTG configuration permits the **30 kW heat-cap ceiling**, rather than its 10 kW non-decay variant.

**1. What RTM’s units currently mean**

| Quantity | Current implementation |
|---|---|
| Electrical quantum | **0.5 J** |
| Legacy HE | One quantum: **0.5 J** |
| RF | Five quanta: **2.5 J** |
| EU | Twenty quanta: **10 J** |
| Nominal EU/RF relationship | **1 EU = 4 RF** |
| One quantum per tick | **10 W**, assuming 20 ticks/s |
| Thermal TU | Presented as **0.5 J** |
| One TU per tick | Presented as **10 W** |
| Fluid inventory unit | Explicitly displayed as **1 L** |
| Minecraft bucket/canister | **1,000 inventory units = 1,000 displayed L** |
| Magnox HU | Separate gameplay heat units; no independently calibrated universal joule conversion |

The bucket assumption in the request therefore has an explicit exception here: **this checkout defines a bucket as 1,000 displayed liters**. Steam liters are additionally described as nominal network quantities, not literal gas volumes.

The electrical conversions are real implementation contracts. They do **not** prove that the surrounding fuel and machine values represent real physical joules.

The actual converter defaults also differ:

- RTM → RF: **5 quanta → 1 RF**, lossless.
- RF → RTM: **2 RF → 5 quanta**, a historical **50% loss**.
- Consequently, an RTM → RF → RTM round trip retains **50%**.
- Configured converter outputs are capped against energy creation.
- The EU constant exists, but I found no active direct EU conversion caller in the inspected source.

These compatibility constants explain a cross-mod penalty, but **not** petroleum fuel’s enormous energy-density shortfall.

Another exception is the DFC receiver: its field named `joules` becomes `joules × 5,000` electrical quanta. That field is therefore **not ordinary SI joules** under the electrical contract.

Sources: [EnergyUnits](../../../src/main/java/api/hbm/energymk2/EnergyUnits.java#L10), [fluid-volume definition](../../../src/main/java/com/hbm/inventory/fluid/FluidVolume.java#L5), [RF import converter](../../../src/main/java/com/hbm/tileentity/network/TileEntityConverterRfHe.java#L20), [DFC receiver](../../../src/main/java/com/hbm/tileentity/machine/TileEntityCoreReceiver.java#L40).

**2. Real fuel references and current values**

The table uses lower heating values where practical. Petroleum fractions require density and composition assumptions; their figures are engineering ranges, not exact identities for RTM mixtures.

Reference basis: [DOE fuel properties](https://afdc.energy.gov/fuels/properties), [IPCC net calorific values](https://www.ipcc-nggip.iges.or.jp/public/2006gl/pdf/2_Volume2/V2_1_Ch1_Introduction.pdf), and [NIST combustion data](https://nvlpubs.nist.gov/nistpubs/TechnicalNotes/NIST.TN.2126.pdf). Gas comparisons assume standard-condition volume; RTM does not currently supply a sufficiently consistent gas-state contract to treat those comparisons as exact.

**Current budgets below are kJ per RTM bucket. Because that bucket contains 1,000 displayed liters, the same numbers are numerically J per displayed liter.** Neither budget is automatically final electrical output.

| Fuel | Current burner / engine budget, kJ/bucket | Real chemical energy, MJ/L | Plausible usable electricity, MJ/L |
|---|---:|---:|---:|
| Diesel | **275 / 685** | **35–36** | **10.5–14.4**, at 30–40% |
| Gasoline | 242 / 605 | 31–33 | 6.2–9.9, at 20–30% |
| Kerosene | 266.5 / 400 | 34–35 | 8.5–12.3, in suitable equipment |
| Crude oil | 261 / — | 34–38 | 8.5–13.3, in an appropriate plant |
| Heavy/residual oil | 288.5 / 360.5 | 38–41 | 11.4–16.4, in suitable industrial equipment |
| Heating oil | 277.5 / 347 | 35–36 | 10.5–14.4 |
| Naphtha | 225.5 / 338 | 29–33 | 5.8–9.9 |
| Light oil | 247.5 / 371 | Approximately 32–36 | 8–12.6; composition dependent |
| Petroil | 274 / 411 | Approximately 32–38 | 8–13.3; mixture dependent |
| LPG, liquid | 184 / 460.5 | 23–26 | 5.8–9.1 |
| Ethanol | 151 / 378 | Approximately 21.3 | 5.3–6.4 |
| Methanol | 112.5 / 281.5 | Approximately 16 | 4–4.8 |
| Biofuel | 255.5 / 635 | Approximately 33, **if biodiesel-like** | Approximately 10–13 |
| Coal oil | 220 / — | Approximately 30–38 | Process and equipment dependent |
| Coal gasoline | 165 / 247.5 | Approximately 30–33 | 6–9.9 |
| Wood pyrolysis oil | 123.5 / — | Approximately 15–22 | Approximately 2–5 |
| Fish/vegetable oils | 206 / —; 220 / — | Approximately 32–35 | Approximately 8–12, in suitable equipment |
| Coal creosote/tar oil | 192.5 / — | Broadly 30–38 | Composition dependent |
| Natural gas | 178.5 / 268 | Approximately **0.033–0.040** | Approximately 0.010–0.016 |
| Petroleum gas | 178.5 / 268 | Composition dependent; roughly 0.04–0.11 | Depends on composition and engine |
| Syngas | 151.5 / 341.5 | Approximately **0.004–0.012** | Approximately 0.001–0.004 |
| Biogas | 98 / 122.5 | Approximately 0.018–0.025 | Approximately 0.005–0.009 |
| Reformate gas | 193 / 289.5 | Hydrogen/hydrocarbon mixture; no single value | Cannot finalize without composition |
| Hydrogen, **liquid** as registered | 2.5 / 5 | Approximately **8.5** | Approximately 2.6–3.4 in combustion equipment |
| Hydrogen, standard-condition gas | Same fluid identity currently confounds states | Approximately **0.0108** | Approximately 0.003–0.004 |
| Oxyhydrogen gas | 2.5 / 7.5 | Approximately 0.0072 for a 2:1 mixture | Approximately 0.0014–0.0022 |
| Hydrazine | 250 / 625 | Approximately 17 for oxidation | Not an ordinary diesel-engine fuel |

For **1,000 L**, multiply the real MJ/L column by 1,000. Thus a current RTM bucket of diesel corresponds to approximately **35 GJ chemical**, or **10.5 GJ electrical at 30%**, if the displayed volume is literal.

The current diesel burner value is approximately **127,000× below** that chemical reference. The current diesel generator’s actual electrical yield is approximately **20,450× below** the 30%-efficient reference.

Those factors are not universal:

- Natural gas’s burner shortfall is approximately **200×** under standard-volume assumptions.
- Liquid hydrogen’s is approximately **3.4 million×**.
- Gas/liquid-state ambiguity changes these comparisons drastically.

**There is no single existing compression factor that makes the current registry physically consistent.**

Solid fuels have no explicit mass contract. Under an illustrative **1 kg/item** assumption:

| Solid | Real chemical energy | Current representative budget |
|---|---:|---|
| Coal | Approximately 26 MJ/kg | Approximately **200 kJ heat/item** in the configured firebox |
| Charcoal | Approximately 30 MJ/kg | Furnace burn-time abstraction; not a distinct chemical ledger |
| Coke | Approximately 28–33 MJ/kg | Approximately **600 kJ heat/item** in the configured firebox |
| Dry wood | Approximately 16–19 MJ/kg | Depends strongly on the machine’s burn-time multiplier |
| Generic biomass | Moisture/composition dependent, roughly 10–18 MJ/kg | Approximately **20 kJ/item** at the basic firebox rate |

These absolute solid comparisons are **mass-assumption dependent**. A log represents a block-sized object, whereas a coal item has no defined kilograms. That must be resolved before assigning final per-item physical values.

Current source: [final fuel registrations](../../../src/main/java/com/hbm/inventory/fluid/Fluids.java#L1135).

**What the diesel tooltip actually means**

The runtime path is:

1. `dieselHeat = 550,000 TU/bucket`.
2. Burner tooltip: `550,000 × 0.5 J = 275 kJ`.
3. Engine registry: `550,000 × 2.5`, rounded down to **1,370,000 quanta**.
4. Engine tooltip: **685 kJ**.
5. Diesel generator: `floor((1,370,000 / 1,000) × 0.75) = 1,027 quanta/L`.
6. Actual yield: **513.5 J/L**, or **513.5 kJ/bucket**.
7. Burning 1 L/t gives **10.27 kW**.

`[Combustible]` means **the engine fuel trait and its grade**, not another substance. Its value precedes the particular engine’s multiplier.

The existing petroleum **burner ratios are already considerably better** than the original arbitrary values. Gasoline is below diesel per volume; heavy oil is close; ethanol and methanol are lower. The principal remaining problem is the separate engine budget and inconsistent absolute/state scaling.

**3. Generators: actual mechanics, output, and efficiency**

The efficiency ranges are design envelopes, not guarantees for every machine size. EPA distinguishes electrical efficiency from combined electricity-plus-useful-heat efficiency; its diesel and turbine data do not justify 75–100% fuel-to-electric conversion. [EPA CHP technology catalogue](https://19january2021snapshot.epa.gov/sites/static/files/2015-07/documents/catalog_of_chp_technologies.pdf)

| Machine | Current source-derived behavior | Physical assessment |
|---|---|---|
| Wood burner, solids | **1 kW** while burning; fuel duration comes from burn-time modifiers | Plausible starter output, but no common fuel/efficiency ledger |
| Wood burner, liquids | Burns up to **2 L/t**; **2.47 kW on wood oil**, **5.5 kW on diesel** | Credits approximately 50% of burner heat as electricity; high for a small biomass/steam plant |
| Stirling engine | Credits **50%** of heat withdrawn; normal/steel overspeed thresholds correspond to approximately 1.5/7.5 kW at those heat rates | 50% is overly generous for this implied equipment; thresholds are not hard nameplates |
| Diesel Generator | **10.27 kW**, diesel consumption **1 L/t = 1,200 L/min** | 75% of engine registry; **187% of burner budget** |
| Industrial Combustion Engine, steel pistons, diesel | Maximum GUI setting: **6 L/t**, approximately **20.55 kW** | Piston suitability multiplier; not a physical engine efficiency |
| Same engine, Tool Steel/Desh pistons, diesel | **6 L/t**, approximately **82.2 kW** | Approximately **249% of burner budget** |
| Same engine, steel pistons, heavy oil | **6 L/t**, approximately **43.26 kW** | Approximately 125% of burner budget |
| Gas turbine, natural gas | Approximately **268 kW direct**, **52.5 L/t** at full throttle including idle allowance | Approximately 95% of engine budget, approximately 143% of burner budget |
| Gas turbine, syngas / reform gas / oxyhydrogen | Approximately **68.3 / 28.95 / 15 kW direct** | Fuel-specific consumption constants produce arbitrary rating relationships |
| Gas turbine with downstream steam recovery | Approximately **1.09 MW standard-train** or **1.24 MW industrial-train gross** on natural gas | Heat recovery is not bounded by remaining fuel energy |
| Turbofan, kerosene | Approximately **8 kW** without afterburning; output-per-liter multiplier can reach **5×** | Afterburning increasing electrical efficiency is physically backwards |
| Small steam engine | Approximately **17 kW** when processing a full ordinary-steam inlet | 85% of its steam work budget; cannot be interpreted as whole-cycle efficiency |
| Standard turbine, ordinary steam | **51 kW** at **6,000 nominal steam L/t** | Steam ledger allows 85% heat-to-electric recovery |
| Industrial turbine, ordinary steam | Up to approximately **1.024 MW** from a full inlet; rate follows inlet inventory | Buffer-dependent throughput, not an independent nameplate |
| Leviathan turbine | Huge buffers and 85% factor; inventory-dependent processing | Needs an explicit flow and power rating; tank size is not plant capacity |
| Fluid burner | Up to **55.5 kW heat** on heating oil at setting 10 | Essentially full burner-budget credit before transfer losses |
| Firebox, coal / coke | Approximately **2 kW heat** while burning | Very small thermal supply beside industrial process requirements |
| Gas flare | Natural gas: approximately **7.14 kW** at basic settings; upgrades can raise recovery | Useful gameplay recovery device; a flare itself does not generate electricity without recovery equipment |
| Solar panel / solar boiler | Panel peaks around **1 kW** at sun factor 1; boiler depends on mirrors and sunlight | Collector area and conversion efficiency are not physically calibrated |
| Geothermal/convection generator | Credits electricity from adjacent geyser/lava block types | Resource-backed abstraction; no reservoir heat or extraction ledger |
| RTG | Current default ceiling **30 kW**, pellet-dependent and decaying | Real technology, arbitrary heat/power scale |
| Mini/Power RTG | Fixed **7 / 25 kW** | These fixed generators lack the normal pellet-decay accounting |
| Industrial Generator | Generation body is commented out | **Not a working generation tier in this source** |

A plausible hierarchy is:

| Technology | Approximate fuel-to-electric efficiency | Suitable power class |
|---|---:|---|
| Small gasoline generator | 15–25% | Several to tens of kW |
| Compact diesel genset | 25–35% | Tens to hundreds of kW |
| Industrial diesel engine | 35–45% | Hundreds of kW to several MW |
| Small steam engine plant | 5–12% | Several to tens of kW |
| Small industrial steam plant | 10–25% overall | Hundreds of kW to MW |
| Coal steam station | Approximately 30–40% overall | MW and larger |
| Small/simple gas turbine | Approximately 20–35% | Hundreds of kW to MW |
| Combined cycle | Approximately 40–55% for a conservative tier; modern large plants can exceed 60% | MW and larger |
| Magnox steam station | Approximately 25–33% gross, with additional auxiliaries | Pilot-scale MW through large commercial stations |

Boiler efficiency—typically a much higher percentage—is **heat delivery efficiency**, not electrical efficiency. DOE’s coal reference is around 33% for the average fleet. [DOE coal-power guidance](https://www.energy.gov/hgeo/transformative-power-systems)

Principal source paths: [diesel generation](../../../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java#L295), [piston generation](../../../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCombustionEngine.java#L161), [gas turbine and steam production](../../../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L397).

**4. Major machine consumption**

These are active processing rates. Idle machines, blocked outputs, recipe cadence, and upgrades change average demand.

| Consumer | Current draw | Assessment |
|---|---:|---|
| Electric furnace | **0.5 kW** base | Plausible appliance rating; implausibly little energy for bulk metallurgy unless item mass/process time is heavily abstracted |
| Basic Fabrication Machine | **1 kW** base | Reasonable for modest assembly; inadequate for every heavy manufacturing recipe |
| Industrial Fabrication Machine | **1 kW per active lane**, approximately **8 kW** with eight lanes | Too little distinction for its implied industrial scope |
| Chemical Reactor | **1 kW** base | A universal rate cannot represent fermentation, separation, and demanding synthesis equally |
| Industrial Chemical Reactor | **1 kW** base before upgrades | Industrial throughput and consumption need reconciliation |
| Centrifuge | **2 kW** base | Plausible small-machine class |
| Gas centrifuge | **2 kW**, or **3 kW** with its speed upgrade | Plausible individual-machine class; cascade consumption scales with count |
| Crusher | **50 W** base | Very low for ore crushing at its processing cadence |
| Large crusher | **5 kW** base | Plausible small crusher; throughput/mass still matters |
| Electric press | **1 kW** while processing | Mean process energy may be modest; peak mechanical power is not represented |
| Compressor | **25 kW** base | Plausible motor; steam recipes are actually compression-plus-heating |
| Large arc furnace | **10 kW × 5^upgrade** | Upgrade 2: **250 kW**; upgrade 3: **1.25 MW**; upgrade 4: **6.25 MW** |
| Electrolyser | **100 kW per active branch**; up to **200 kW** with both branches | Heavy load; output quantities are not physically consistent with that draw |
| Pyrolysis oven | **100 kW** base | Some fuel products cannot repay its processing electricity |
| Hydrotreater | **10 kJ per 100-L operation**; up to **200 kW** at one operation/tick | Processing cost and throughput are separate; underfeeding lowers average demand |
| Catalytic reformer | Same **10 kJ/100 L**, up to **200 kW** | Can dominate a base at sustained processing |
| Liquefactor / solidifier | **5 kW** base | Several recipes imply chemical processes that this generic cost does not explain |
| Oil well / pumpjack | **1 / 2 kW** | Extraction flow is extraordinarily high relative to these motors |
| Refinery | **50 W electrical**, up to **100 L/t** feed | Absurdly low auxiliary draw at literal 2,000 L/s; hot-oil heat must also be supplied |
| Electric water pump | **10 kW**, up to **10,000 L/t** | Literal flow is 200,000 L/s; plausible only as a major abstraction |
| Mining laser | **100 kW** base | Appropriate heavy progression load; process scale is still abstracted |
| ITER magnets | Nominal **1 MW**, modified by shield | Requires a separately reconciled net-positive fusion boundary |

The arc-furnace upgrade deserves special attention. Processing duration falls roughly with `2 × upgrade + 1`, while power grows as `5^upgrade`. At upgrade 2, processing power rises **25×** for approximately **5×** process speed: roughly **5× more processing energy per batch**, before delay behavior.

That can be an intentional overdrive penalty, but it should be disclosed. It is a genuine way to create a pre-nuclear power crisis without any change to generator constants.

I would **not** multiply all machine draw uniformly. Workshop assembly, melting, crushing, electrolysis, and refinery auxiliaries need separate process-energy budgets.

**5. Fuel-chain energy accounting**

These calculations use **the existing game heat ledger**, not literal chemical energy. They distinguish registry discrepancies from proven closed-loop exploits.

| Route | Current accounting | Finding |
|---|---|---|
| Crude → hot oil → refinery fractions | Per bucket: **261 kJ chemical**, **100 kJ added heat**, approximately **25 J electricity**; fractions contain approximately **255.6 kJ chemical** | Fraction chemical total is reasonably conserved, but heating is enormous relative to the compressed feed energy |
| Light oil → diesel + kerosene | **247.5 → 271.175 kJ** | **9.6% chemical-energy gain** in a tower requiring no energy input |
| Naphtha → gasoline | **225.5 → 193.6 kJ**, plus approximately 2 kJ processing electricity | No chemical-energy creation; approximately 14% product-energy loss |
| Naphtha → reformate + reform gas + hydrogen | **225.5 → 224.95 kJ chemical**, plus approximately **100 kJ electricity/bucket** | Chemical ledger nearly closes; processing electricity is approximately 44% of feed chemical energy |
| Diesel + reformate → “High-Octane Diesel” | Approximately **273.6 → 297 kJ** | Blending gives an unjustified approximately **8.6% chemical uplift**; mixing electricity must also be included in a full ledger |
| Biogas + ethanol → biofuel | **184.75 kJ chemical + approximately 3 kJ electricity → 255.5 kJ chemical** | Approximately **67.75 kJ unaccounted increase** |
| Leaded fuel mixing | **10,000 L → 12,000 L**, with approximately 3% higher density budget | Approximately **23.6% more fuel energy**, before accounting for the finite additive’s feedstock budget |
| Coal → syngas in pyrolysis oven | Approximately **200 kJ coal heat + 0.5 kJ steam + at least 500 kJ electricity → 151.5 kJ burner / 341.5 kJ engine budget** | Product cannot repay processing electricity, even before counting coal |
| Coal + hydrogen → heavy oil | Approximately **200 kJ coal + 1.25 kJ hydrogen heat + at least 500 kJ electricity → 288.5 kJ burner / 360.5 kJ engine budget** | Again electrically negative; upstream hydrogen production worsens it |
| Heavy-oil coking | **12 buckets ≈ 3.462 MJ chemical → approximately 0.919 MJ coke + liquid-product heat**, plus processing heat | Roughly **73% chemical-energy loss** in the represented products |
| Coker gas → reform gas | **4 buckets → 0.1 bucket** | Approximately **97% chemical-energy loss**, plus a substantial electricity cost |
| Methane steam reforming | **178.5 kJ gas + approximately 9.45 MJ ultrahot-steam heat → 7.5 kJ hydrogen heat** | Extreme mismatch; the steam quantity is vastly oversized relative to product fuel |
| Water electrolysis → hydrogen → diesel generator | Approximately **50 kJ electrical/batch → 1 kJ hydrogen burner budget → 1.4 kJ generator output** | **No current electrical perpetual motion**; fuel production is extremely inefficient |

Recipes: [fractionation](../../../src/main/java/com/hbm/inventory/recipes/FractionRecipes.java#L24), [chemical processing](../../../src/main/java/com/hbm/inventory/recipes/ChemplantRecipes.java#L334), [pyrolysis/gasification](../../../src/main/java/com/hbm/inventory/recipes/PyroOvenRecipes.java#L81), [coking](../../../src/main/java/com/hbm/inventory/recipes/CokerRecipes.java#L28).

Additional distinctions matter:

- **A chemical-energy gain is not automatically an infinite loop.** I have not demonstrated a stock closed fuel-processing cycle that generates indefinitely.
- The reachable electric-heater → staged steam → turbine route returns at most approximately **42.5%/50%** before auxiliaries. The earlier direct-steam metadata discrepancy was corrected; it should not be reported as a current stock exploit.
- Steam compressor recipes currently cost more than their added steam energy. Raising steam enthalpies without changing those recipes could introduce a new exploit.
- Hydrogen is registered as cryogenic liquid, while electrolysis comments use gas-volume reasoning. Gas/liquid identity, mass yield, and liquefaction energy must be reconciled together.
- Lubricant carries no combustion trait despite being an energy-bearing hydrocarbon input. That leaves some chemical-feedstock budgets incomplete.
- Heat-exchanger factors above 100%, including hot lead and hot thorium salt, need explicit justification or correction. They are not ordinary exchanger efficiencies.

**6. Late pre-nuclear infrastructure model**

This is an illustrative RTM factory, not a measurement of someone’s base. It deliberately includes intermittently operated heavy processes.

| Load group | Fully active draw | Assumed average |
|---|---:|---:|
| Four fabrication machines, six chemical reactors, two centrifuges, one large crusher | 19 kW | 13 kW |
| Compressor | 25 kW | 6.25 kW |
| Large arc furnace, upgrade 2 | 250 kW | 62.5 kW |
| Electrolysis, one branch | 100 kW | 50 kW |
| Pyrolysis oven | 100 kW | 50 kW |
| Hydrotreater | 200 kW | 50 kW |
| Reformer | 200 kW | 20 kW |
| Two pumpjacks and refinery electricity | 4.05 kW | 4.05 kW |
| Water pump | 10 kW | 10 kW |
| Mining laser | 100 kW | 50 kW |
| **Total** | **1.008 MW** | **315.8 kW** |

The assumed duty factors are the basis of the average column. Different recipes or upgrades can shift this considerably.

**Supplying that factory today**

| Supply choice | Approximate requirement | Important consequence |
|---|---|---|
| Diesel generators | **31** for the initial average; approximately **34** after expanding extraction electricity | Generator spam is real for this particular route |
| Diesels without peak buffering | **99** for the listed simultaneous peak | Scheduling and storage substantially change the requirement |
| Diesel fuel for the 34-generator case | **40,800 L/min = 40.8 RTM buckets/min** | A 4,000-L generator tank lasts approximately **200 seconds** |
| Straight crude-to-diesel production | Diesel yield **15% light oil × 55% diesel = 8.25% crude volume** | Approximately **494,500 L crude/min** needed |
| Associated production hardware | Approximately **14 ordinary pumpjacks, five refineries, seven fractioning towers** | Lower bound before supplying the refinery’s heating fuel |
| Hot-oil heating | Approximately **824 kW heat**, equivalent to roughly **15 max-setting heating-oil burners** | The heating-fuel supply adds another processing/resource burden |
| Piston engines, best diesel suitability | **Four** cover the initial average; **13** cover the peak | Far less generator spam, still substantial fuel consumption |
| Piston engines, steel pistons on heavy oil | Approximately **eight** cover the initial average | Uses an existing pre-nuclear fuel relationship worth preserving |
| Natural-gas turbine, direct electricity | **Two** cover the initial average | Gas supply remains necessary |
| Natural-gas turbine with full industrial steam recovery | **One** can theoretically cover the listed electrical peak before auxiliaries | Its advantage is partly created by broken heat accounting |

A fully loaded natural-gas turbine burns approximately **63,000 L/min**, and its 100,000-L tank lasts approximately **95 seconds**. Supplying it solely from the refinery’s 10% petroleum-gas fraction would require approximately **525 L/t crude**, six refineries, and eighteen ordinary pumpjacks, before process-heat fuel. Natural-gas deposits provide a different acquisition route.

**The pre-nuclear problem is therefore a combination of poor fuel autonomy, expensive fuel processing, upgrade penalties, and a distorted choice of generators.** “Need forty diesels” is credible in one route, but it is not an accurate description of every fossil option.

**7. A realistic progression target**

My preferred engineering reference is **literal chemical energy with explicit material/state assumptions**. The previous audit’s **1:100 effective-energy preset** remains a possible gameplay alternative, but it must be declared and applied coherently. It cannot be treated as literal joules, nor adopted by changing fuel values alone.

Suggested
