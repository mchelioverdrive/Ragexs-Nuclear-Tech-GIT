# RealSim fluid infrastructure and UX normalization

2026-10-13 — Separate normalization tranche after Phase 5C, before Phase 5D.

This tranche changes storage nameplates, groundwater supply and presentation. It does not convert gas turbines, steam enthalpy, nuclear cooling, life-support consumption or fictional assays. Phase 5A–5C reports remain historical; the capacities below supersede their storage assumptions. All rates assume 20 TPS and are design/source arithmetic, not measured facilities.

## Quantity and capacity policy

Capacity is stored quantity. Flow is quantity/s or quantity/min; generator power is W, energy is J and process work is J/batch. A tank size never grants a process rate. Liquid quantities with a released registry assay use L. Gas assays use **standard L**, the equivalent amount at **0 °C / 1 bar**, rather than vessel volume. Network PU is an existing compatibility class, not a physical pressure in bar. Unresolved fluids and unreleased steam quantities remain explicitly nominal. No feedwater-equivalent conversion has been released for steam in this tranche.

Hand-carried liquid vessels use 20 L, drums 200 L, day tanks 100–400 L and an industrial engine 4,000 L. Ordinary bulk storage uses 32,000 L in the 3 × 3 × 5 block envelope, and BAT9000 uses 64,000 L in its 5 × 5 × 3 envelope. Those envelopes are upper geometric bounds, not claims that every block is fluid space. The reductions from 256,000/2,048,000 address geometric overstatement, not long autonomy. Recipe costs, footprints and access remain unchanged; legitimate hours or days of stationary fuel storage are preserved.

Resolved process gases retain standard-equivalent capacities that need not equal vessel size. Nominal and custom assays retain their existing counts. A gas container may also have an existing cryogenic-liquid fill definition; its accepted phase is the registered fluid identity, not its item name. This preserves the Phase 5B liquid H2/O2/N2 stocks and container compatibility.

## Adopted capacities and compatibility

| System | Previous quantity | New default/nameplate | Role, apparent size and portability | Decision and compatibility |
| --- | ---: | ---: | --- | --- |
| Canister | 1,000 | 20 L liquid / 500 standard L resolved gas; nominal fills retain 1,000 | Hand-carried vessel | New fills/creative stacks stamped; old untagged fills remain 1,000 |
| Portable fluid tank, lead tank | 1,000 | 20 L resolved liquid; 1,000 standard L gas or nominal units | Hand-carried, lead variant retains shielding rules | Stamped new fills; identity and old stock preserved |
| Gas tank item | 1,000 | 1,000 standard L gas / 20 L released cryogenic liquid; nominal fills retain 1,000 | Portable gas/cryogenic vessel; physical pressure unspecified | No cryogenic-to-gas identity conversion; old amounts preserved |
| Portable barrel item | 16,000 | 200 L resolved liquid; 16,000 standard L gas or nominal units | Heavy drum; ordinary inventory transport abstraction | Stamped new fills; legacy 16,000-unit stocks still drain intact |
| Disperser canister | 2,000 | 20 L resolved liquid; gas/nominal fills retain 2,000 | Portable payload vessel | Filling and creative quantities stamped; grenade effects remain legacy behavior |
| Plastic / corroded / iron / steel / TC / antimatter block drums | 12,000 / 6,000 / 8,000 / 16,000 / 24,000 / 16,000 | 200 / 100 / 200 / 200 / 200 / 200 | One-block drums, movable with saved contents | Capacity applies on placement and loaded-world runtime; over-cap contents remain |
| Laboratory / ordinary and corrosion-resistant sampler | 50 / 1,000 | 1 / 20 quantity units, fluid basis shown | Coarse whole-unit sample / hand siphon reservoir | Old short type/fill/capacity tags preserved; configured fill ceiling bounded; no amount clamp; one-liter minimum is an explicit coarse abstraction |
| Starter wood/fluid generator | 16,000 | 100 L liquid day tank | Small workshop generator | Default replaces saved maximum, not saved fill |
| Oil burner | 16,000 | 200 L liquid day tank | Small 200 kWth heater | Thermal nameplate and 85% rated efficiency unchanged |
| Diesel generator | 4,000 | 400 L liquid day tank | Single-block 200 kWe generator | 4,000 L remains useful external storage; old internal stock preserved |
| Industrial reciprocating engine | 24,000 | 4,000 L liquid | 24 m³ envelope; stationary 750 kWe equipment | Installed pistons and their efficiencies unchanged |
| Ordinary multiblock fluid tank | 256,000 | 32,000 quantity units | 45 m³ envelope; dedicated bulk storage | Stored amount/type/pressure retained, no additional filling over cap |
| BAT9000 | 2,048,000 | 64,000 quantity units | 75 m³ envelope; dedicated large bulk storage | Same conservative over-cap treatment; no ID or structure change |
| Atmospheric refinery | 64,000 inlet; 24,000 each outlet | 32,000 L inlet; 8,000 L each liquid outlet; 24,000 standard L gas | 81 m³ tower, stationary | 24 L/min processing ceiling unchanged; full liquids fit within envelope |
| Vacuum refinery | 64,000 inlet; 24,000 each outlet | 32,000 L inlet; 8,000 L each liquid outlet; 24,000 standard L sour gas | Stationary process vessel | 12 L/min ceiling, heat/work bills and old-feed compatibility unchanged |
| Hydrotreater | 64,000 oil and H2; 24,000 outputs | 32,000 L oil; 16,000 L treated oil; H2 64,000 standard L and sour gas 24,000 standard L retained | 63 m³ tower, stationary | Gas pressure class 1 retained; 12 L/min material ceiling unchanged |
| Reformer | 64,000 naphtha; 24,000 outputs | 16,000 L naphtha; 8,000 L reformate; gases 24,000 standard L retained | 45 m³ process equipment | 12 L/min material ceiling unchanged |
| Fraction tower / FCC / coker | See declaration inventory below | Retained | Stacked 27 m³ tower sections / stationary reactor / 207 m³ coker | Existing 4,000-L fractions and 16,000/8,000-L coker stores fit their process roles; no autonomy-driven shrink |
| Chemical reactor / eight-lane industrial reactor | 24,000 per tank / 32,000 per lane tank plus utility tanks | Retained | Multipurpose batch machine / industrial plant | Includes nominal 16,000-unit salt recipes and arbitrary custom bills. Do not invalidate them through a generic liquid-only shrink; gas/nominal quantity is not geometric volume. Shared numeric/assay display applies |
| Compressor / cryogenic separator / electrolyser | 16,000 input/output / 64,000 air and 24,000 products / 16,000 each | Retained | Stationary phase/process equipment | Standard-equivalent gas stores; phase identity and process work unchanged |
| Groundwater electric / steam output buffer | 1,000,000 / 100,000 at old defaults | 400 / 200 L ordinary water | Workshop / bootstrap pump | Storage independent of production and export |
| Pipe pump buffer | Equal to selected rate, 0–10,000 | 10,000 quantity units | Network transport buffer | Legacy control key `capacity`, saved `buffer` and OC `setFlow` retain quantity/tick meaning; export budget separately enforced |

### Explicit retained boundaries

- Vanilla/custom world buckets remain 1,000-unit **world-block carriers**, with that abstraction named in their tooltip. A Minecraft fluid block is carried/placed as one block; Forge container registrations and world placement remain compatible. No claim of physically human-carried 1,000-L vessel geometry is made.
- Vanilla water bottles are 250, mercury bottles 1,000; legacy cups/cans/medical IVs are 100. Cells are 1,000, tritium rods 2,000 and particle stores 1,000; many identities remain nominal and unreleased. These fixed consumable/world/API quantities are retained rather than silently redefining old metadata-only stacks. Their filled/empty tooltips now expose the real registered quantity. A separate source/consumption migration is required before changing them safely.
- Explosive diesel/kerosene/oxygen barrels retain 10,000-unit fixed payload definitions. Oil/gas/shale ores (250/100/50–250), mercury ingots (125) and dry ice (1,000) are consumable resources, not vessels; they are not assigned invented empty-container capacity or rescaled.
- Jetpacks, oxygen packs, fueled armor/tools, chemical weapons, nuclear waste storage, rocket fuel/transport stores and custom machines retain their configured capacities and consumption contracts. Typed NBT holders now use the shared fluid formatter; legacy multi-fuel tools without a stored fluid identity explicitly show that limitation. Changing breathing, propulsion or nominal nuclear counts would require the corresponding physical migration, outside this tranche.
- Transporter rocket keeps eight 128,000-unit cargo tanks and two 64,000-unit propulsion stores. These are dedicated remote freight/launch abstractions, not hand containers. Pressure and cryogenic state come from actual fluid data; vessel pressure is never invented.
- Foundry material buffers use their material quantity system, pseudo-isotope tanks use nominal isotope counts, and creative infinite barrels are sources/sinks rather than finite fluid stock. They are not converted into liters by this pass.

## Existing worlds and container migration

No numeric fluid IDs, item IDs, block IDs or existing amount/type/pressure NBT fields are renumbered or rescaled. Tank reads retain every nonnegative saved amount even above the saved/new capacity, including an absent maximum tag. Only deliberately revised tanks opt into constructor nameplates; unrelated/custom/dynamic capacities still load their saved maximum. Drums created through the no-argument saved TileEntity path adopt their block's new nameplate during runtime registration. Packed tank/drum items display their new capacity while retaining their payload.

Over-cap stock can be consumed or exported. Standard receiver/transceiver demand is zero until space exists; filling never clamps old stock or accepts negative free space. Standard, fillable and infinite loaders preserve that rule. Bars saturate safely without integer overflow or drawing outside their bounds. Capacity-only drum corrosion replacement retains all old stock. Explicit destructive player actions (fluid selection/pressure changes, breaking unsafe tanks, intentional leaks) retain existing behavior; this is not an immunity to those actions.

Fixed filled items now carry additive integer `realSimContainerAmount` on **new reduced-capacity fills**. Registry matching strips only this marker for identity comparison; old untagged items still own their historical quantity. New loading/filling, siphoning, NEI results, creative entries and starter rewards use stamped amounts. Full-stack merges require equal NBT, preventing 20-L stock from merging into a legacy 1,000-L stack. No load-time write stamps old inventory. External producers creating an untagged legacy full stack deliberately receive the legacy quantity; integrations should obtain fresh fills through `getFullContainer`/`newFilledStack`. A legacy filled item larger than a new receiving vessel needs a larger stationary tank to drain atomically; its contents are not destroyed.

## Groundwater model

The shared runtime, valid-ground list, celestial water-table fluid, no-sky/orbit restrictions, height limit, first solid layer and majority-valid 3 × 3 × depth checks remain. There is no aquifer depletion simulator, biome scan, new upgrade system or background world mutation. Existing pumps have no upgrade slots. A changed celestial fluid cannot replace a filled old tank; draining it permits the new source identity.

`Q = min(nameplate, P × 0.35 / (1,000 kg/m³ × 9.81 m/s² × 100 m))`, converted from m³/s to L/s. The selected 100-m effective head includes the lift/resistance abstraction. Electric 10 kW gives a 3.568 L/s hydraulic ceiling, limited to **2 L/s (120 L/min)**. Steam 2 kW shaft gives 0.714 L/s, limited to **0.5 L/s (30 L/min)**. Water generation accumulates integer microliters and publishes whole network units, with saved remainder; the 100-fold non-water penalty remains. Fractional work/output is retained through stops and reloads. Full buffers stop operation, with no accumulated idle production.

Both pumps have a separate **20 L/s aggregate water export** ceiling across ports, independent of buffer size and production. The normal pump and pipe network move stored output on the owning server thread. Pipe pump export remains its existing configured quantity/tick rating, independent of its new 10,000-unit buffer; its GUI displays quantity/s in steps of 20 and the OC API retains its existing tick unit.

The steam pump reserves one existing 100 nominal-steam → 1 nominal-spent-steam parcel as **100 kJ shaft work**, then debits **100 J/tick** at its 2-kW nameplate. Thus a parcel supports 50 s / 25 L of ordinary-water pumping; sustained demand is 120 nominal steam units/min and 1.2 nominal exhaust units/min. Parcel reservation requires exhaust space; already reserved work remains usable. Shaft work, water remainder, electrical stock and all tanks now persist. This is an explicit **transitional pump-only calibration**, not an adopted steam enthalpy or steam-plant efficiency. Its replacement by thermodynamic shaft extraction belongs to Phase 5D; no turbine/boiler conversion was begun.

New `I:electricLitersPerMinute` (0–120) and `I:steamLitersPerMinute` (0–30) settings can lower the design rating. Old speed keys remain readable; nonpositive values disable the corresponding pump, positive legacy per-tick values cannot bypass the physical ceiling. `groundHeight`, `groundDepth` and terrain semantics remain.

## Shared player information

`FluidInfo` derives phase/basis/LHV from `PhysicalRegistry`/`PhysicalState`, temperature and hazard/suitability data from `FluidType` and its existing traits, and quantities from actual stock. `PortableFluidInfo` adapts registry-based filled and empty items; NBT holders call the same formatter. Forge-only unknown assays show nominal Forge quantities and never acquire invented RTM LHV.

- Filled containers: fluid name, current/capacity, phase, quantity basis, temperature, existing hazards/grade, and chemical energy where released.
- Empty containers: capacity, registered fill restrictions, shielding where applicable and available quantity bases. Mixed legacy gas/cryogenic definitions are explicitly allowed; no new physical pressure rating is fabricated.
- Tank GUIs retain graphical bars and show readable `current / max L`, `standard L` or `nominal units` in their common hover. All requested generator, burner, petroleum, chemistry, electrolyser, compressor, cryogenic and storage widgets route through that helper. Groundwater uses its existing world-look overlay; it has no tank GUI. The legacy gas centrifuge's separate pseudo-fluid widget explicitly says nominal isotope units.
- NEI fluid icons and selection hover use the same physical information. Packed machine tank items use the same quantity/assay display.
- LHV is chemical density, formatted from canonical chemical microjoules/unit as MJ/L or MJ/standard L. Stored energy is density × actual amount, never multiplied by fuel grade or engine efficiency; presentation uses floating point only after avoiding integer multiplication overflow.
- Generator statistics separate rated output, current output, load, installed/fuel-qualified rated efficiency and current fuel flow. Idle output/flow are zero; an installed compatible engine still shows its rated efficiency. No part-load curve was invented. Existing owned fuel batches retain their own conversion efficiency, visible with Shift along with chemical credit and owned identity. Low-level data is retained, not deleted.

Fuel **grade (suitability)** is a legacy equipment/ignition category, never another chemical LHV. High includes both spark and compression fuels; the 200-kW diesel machine accepts its explicit diesel/distillate/petroil list rather than every High fluid. Medium marks petroil-like blends; Low marks heavier heating/industrial oils; Aviation marks aviation-family preference; Gaseous marks gas equipment suitability. Industrial piston compatibility and efficiency still determine whether a category can operate. Gas-turbine/aviation consumers retain their deferred legacy implementation. Grade is not a certification of a universal ignition engine or energy bonus.

## Representative storage models

The following are calculated chemical-to-output endurance, excluding losses outside the specified efficiency, auxiliary load, dispatch and fuel compatibility. Long autonomy is acceptable for dedicated stationary stores.

| Storage | Starter 40 kW, wood oil at 7% | Diesel 100 kW at 32% | Diesel 200 kW at 32% | Industrial 431 kW at 38% | Industrial 750 kW at 38% |
| --- | ---: | ---: | ---: | ---: | ---: |
| 20 | 0.198 h | 0.637 h | 0.318 h | 0.175 h | 0.101 h |
| 100 | 0.992 h | 3.184 h | 1.592 h | 0.877 h | 0.504 h |
| 200 | 1.983 h | 6.368 h | 3.184 h | 1.754 h | 1.008 h |
| 400 | 3.967 h | 12.736 h | 6.368 h | 3.509 h | 2.016 h |
| 4,000 | 39.667 h | 127.356 h | 63.678 h | 35.090 h | 20.166 h |
| 32,000 | 317.333 h | 1018.852 h | 509.426 h | 280.716 h | 161.318 h |
| 64,000 | 634.667 h | 2037.703 h | 1018.852 h | 561.432 h | 322.636 h |

The new 400-L diesel day tank supplies 6.368 h at 200 kW. A 4,000-L external store supplies 63.678 h; the industrial engine's 4,000-L store supplies about 35.09 h at 431 kW or 20.166 h at 750 kW. The 20-L canister supplies about 19.10 min at 200 kW. The 100-L starter day tank supplies 0.992 h on wood oil. These are useful buffers rather than throughput ratings.

| Petroleum store | Inventory / relevant rate | Nameplate endurance or fill time |
| --- | --- | ---: |
| Retained well crude buffer | 64,000 L / 6 L/min well or 24 L/min pumpjack | 177.78 h / 44.44 h |
| Refinery crude inlet | 32,000 L / 24 L/min | 22.22 h; 24.93 h at one-heater crude-heating limit 21.390 L/min |
| One refinery liquid coproduct tank | 8,000 L / at most total 24 L/min material feed | At least 5.56 h on the conservative all-liquid-flow bound; actual cut takes longer |
| Fractionator inlet/product | 4,000 L / 12 L/min | 5.56 h at full inlet; individual product cut fills more slowly |
| Vacuum inlet | 32,000 L / 12 L/min | 44.44 h; 59.24 h at one-heater 9.004 L/min |
| Hydrotreater inlet | 32,000 L / 12 L/min | 44.44 h |
| Reformer inlet | 16,000 L / 12 L/min | 22.22 h; 40.45 h at one-heater 6.593 L/min |
| Coker inlet | 16,000 L / 6 L/min | 44.44 h; 71.86 h at one-heater 3.711 L/min |

Gas coproduct capacities are **standard equivalent**, so liquid feed L/min is not their fill-time denominator. For the released atmospheric 10 mass% petroleum gas cut, 24 L/min × 0.850 kg/L × 10% = 2.04 kg/min. Its 50:50 molar methane/LPG assay weighs (16.04246 + 44.09562)/2 / 22,711 = 0.0013239787 kg/standard L. Thus a 24,000-standard-L gas store fills in **15.576 min** without export, while the 8,000-L liquid coproduct tanks take hours. Maintaining gas removal is deliberately necessary; shrinking liquid tanks does not change this backpressure or the mass bill. The hydrotreater hydrogen and reformer offgas stores likewise retain their gas basis. The historical Phase 5C facility autonomy tables used previous internal capacities; this table supersedes only their storage endurance assumptions, not their process bills or facility loads.

## Runtime acceptance and validation

**User-reported initial placement smoke test:** converted RealSim machines were placed in-world without an immediate TileEntity/runtime crash, and gas/liquid distinctions and physical fuel information were visible. This is placement evidence only; it is not acceptance of this normalization's new UI or operation.

Remaining single-instance acceptance:

- Sustained workshop, boiler makeup and large cooling demand at both pump ratings; terrain/height/orbit/no-sky and non-water checks.
- Full buffers, aggregate multi-port export, exhausted power/steam, blocked exhaust, fractional remainder and reserved shaft work over save/load and unload/reload.
- Every converted machine's real ports, orientations/rotation, legacy over-cap drain, packed item replacement and several recipe/fuel choices.
- Fresh versus untagged legacy container quantities, filled/empty tooltips, stacked fills, cryogenic state, unresolved assays and stored-energy readability.
- All named machine tank hover layouts, NEI, fluid selection, idle/rated efficiency and Shift expansion in the actual client.
- Long-duration fuel consumption and coproduct backpressure at Phase 5C sustained rates.

Multiplayer/dedicated-server authority and synchronization still require later acceptance; **no multiclient or server-connection validation is performed in this pass**. Source inspection/builds cannot substitute for it. No new Minecraft launch is claimed.

Automated validation extends existing physical-accounting tests for shared filled/empty/gas formatting, unresolved LHV, stored chemical energy, binary NBT stock preservation, absent capacity, changed default, over-cap network rejection, old/new fixed-container descriptor quantities and mixed-quantity NBT distinction, hydraulic bounds and idle rated efficiency/debug separation. Standard loader filling/draining and equal-NBT merge guards were source-inspected; executing Forge ore/container registration in the plain headless JUnit runner fails during native registry/bootstrap initialization, so no simulated registry fixture was added. Actual transfer remains a runtime acceptance item. Existing petroleum accounting tests cover the released process bills and utility/output ownership. The observed final build status is recorded in the task response; generated test reports and JARs are not repository documentation or committed source.

## Source declaration capacity inventory

This inventory covers current `com/hbm` TileEntity `FluidTank` declarations, including abstract/shared/configured and nuclear/transport boundaries. Each row names the native owner/field and its default expression; instances can hold a different fluid or configured capacity. `L / standard L / nominal` means the actual selected registry identity decides, not an invented physical fluid. The footprint is an apparent block envelope from the native block definition where unambiguous; it is not a measured tank volume. All are stationary while operating; persistent drums/tanks are movable as packed blocks. Changed rows adopt the new nameplate and preserve over-cap contents. Retained rows keep saved/configured capacity; no quantity or process rating is altered. Temporary shared zero-capacity views are not additional storage.

| Native owner / tank field | Prior default | Adopted default | Quantity basis | Physical role / apparent size | Disposition / compatibility |
| --- | --- | --- | --- | --- | --- |
| `bomb/TileEntityCompactLauncher/tanks[0]` (NONE) | `25000` | `25000` | L / standard L / nominal | dedicated freight/launch storage; native/configured geometry | Retain; saved/configured quantities unchanged |
| `bomb/TileEntityCompactLauncher/tanks[1]` (NONE) | `25000` | `25000` | L / standard L / nominal | dedicated freight/launch storage; native/configured geometry | Retain; saved/configured quantities unchanged |
| `bomb/TileEntityLaunchPadBase/tanks[0]` (NONE) | `24_000` | `24_000` | L / standard L / nominal | dedicated freight/launch storage; native/configured geometry | Retain; saved/configured quantities unchanged |
| `bomb/TileEntityLaunchPadBase/tanks[1]` (NONE) | `24_000` | `24_000` | L / standard L / nominal | dedicated freight/launch storage; native/configured geometry | Retain; saved/configured quantities unchanged |
| `bomb/TileEntityLaunchPadRocket/i++) tanks[i]` (NONE) | `64_000` | `64_000` | L / standard L / nominal | dedicated freight/launch storage; native/configured geometry | Retain; saved/configured quantities unchanged |
| `bomb/TileEntityLaunchPadRocket/i++) tanks[i]` (NONE) | `64_000` | `64_000` | L / standard L / nominal | dedicated freight/launch storage; native/configured geometry | Retain; saved/configured quantities unchanged |
| `bomb/TileEntityLaunchTable/tanks[0]` (NONE) | `100000` | `100000` | L / standard L / nominal | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `bomb/TileEntityLaunchTable/tanks[1]` (NONE) | `100000` | `100000` | L / standard L / nominal | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineAlkylation/tanks[0]` (CHLOROMETHANE) | `8_000` | `8_000` | nominal/unreleased | stationary process buffer; 60 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineAlkylation/tanks[1]` (NONE) | `4_000` | `4_000` | L / standard L / nominal | stationary process buffer; 60 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineAlkylation/tanks[2]` (UNSATURATEDS) | `8_000` | `8_000` | standard L | stationary process buffer; 60 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineAlkylation/tanks[3]` (CHLORINE) | `8_000` | `8_000` | nominal/unreleased | stationary process buffer; 60 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineCatalyticCracker/tanks[0]` (BITUMEN) | `4000` | `4000` | liquid L | stationary process buffer; 42 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineCatalyticCracker/tanks[1]` (STEAM) | `8000` | `8000` | nominal/unreleased | stationary process buffer; 42 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineCatalyticCracker/tanks[2]` (OIL) | `4000` | `4000` | liquid L | stationary process buffer; 42 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineCatalyticCracker/tanks[3]` (PETROLEUM) | `4000` | `4000` | standard L | stationary process buffer; 42 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineCatalyticCracker/tanks[4]` (SPENTSTEAM) | `800` | `800` | nominal/unreleased | stationary process buffer; 42 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineCatalyticReformer/tanks[0]` (NAPHTHA) | `64_000` | `16_000).withDefaultCapacity(` | liquid L | stationary process buffer; 45 m³ envelope | Revised; over-cap stock retained |
| `machine/oil/TileEntityMachineCatalyticReformer/tanks[1]` (REFORMATE) | `24_000` | `8_000).withDefaultCapacity(` | liquid L | stationary process buffer; 45 m³ envelope | Revised; over-cap stock retained |
| `machine/oil/TileEntityMachineCatalyticReformer/tanks[2]` (PETROLEUM) | `24_000` | `24_000` | standard L | stationary process buffer; 45 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineCatalyticReformer/tanks[3]` (HYDROGEN_GAS) | `24_000` | `24_000` | standard L | stationary process buffer; 45 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineCoker/tanks[0]` (HEAVYOIL) | `16_000` | `16_000` | liquid L | stationary process buffer; 207 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineCoker/tanks[1]` (OIL_COKER) | `8_000` | `8_000` | liquid L | stationary process buffer; 207 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineCoker/tanks[2]` (GAS_COKER) | `8000` | `8000` | standard L | stationary process buffer; 207 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineCoker/tanks[3]` (WATER) | `8000` | `8000` | liquid L | stationary process buffer; 207 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineFrackingTower/tanks[0]` (OIL) | `64_000` | `64_000` | liquid L | stationary process buffer; 4 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineFrackingTower/tanks[1]` (GAS) | `64_000` | `64_000` | standard L | stationary process buffer; 4 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineFrackingTower/tanks[2]` (FRACKSOL) | `64_000` | `64_000` | nominal/unreleased | stationary process buffer; 4 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineFractionTower/tanks[0]` (HEAVYOIL) | `4000` | `4000` | liquid L | stationary process buffer; 27 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineFractionTower/tanks[1]` (BITUMEN) | `4000` | `4000` | liquid L | stationary process buffer; 27 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineFractionTower/tanks[2]` (SMEAR) | `4000` | `4000` | liquid L | stationary process buffer; 27 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineGasFlare/tank` (GAS) | `64000` | `64000` | standard L | stationary process buffer; 108 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineHydrotreater/tanks[0]` (OIL) | `64_000` | `32_000).withDefaultCapacity(` | liquid L | stationary process buffer; 63 m³ envelope | Revised; over-cap stock retained |
| `machine/oil/TileEntityMachineHydrotreater/tanks[1]` (HYDROGEN_GAS) | `64_000` | `64_000` | standard L | stationary process buffer; 63 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineHydrotreater/tanks[2]` (OIL_DS) | `24_000` | `16_000).withDefaultCapacity(` | liquid L | stationary process buffer; 63 m³ envelope | Revised; over-cap stock retained |
| `machine/oil/TileEntityMachineHydrotreater/tanks[3]` (SOURGAS) | `24_000` | `24_000` | standard L | stationary process buffer; 63 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineLiquefactor/tank` (NONE) | `24_000` | `24_000` | L / standard L / nominal | stationary process buffer; 36 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachinePyroOven/tanks[0]` (NONE) | `24_000` | `24_000` | L / standard L / nominal | stationary process buffer; 105 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachinePyroOven/tanks[1]` (NONE) | `24_000` | `24_000` | L / standard L / nominal | stationary process buffer; 105 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineRefinery/tanks[0]` (HOTOIL) | `64_000` | `32_000).withDefaultCapacity(` | liquid L | stationary process buffer; 81 m³ envelope | Revised; over-cap stock retained |
| `machine/oil/TileEntityMachineRefinery/tanks[1]` (HEAVYOIL) | `24_000` | `8_000).withDefaultCapacity(` | liquid L | stationary process buffer; 81 m³ envelope | Revised; over-cap stock retained |
| `machine/oil/TileEntityMachineRefinery/tanks[2]` (NAPHTHA) | `24_000` | `8_000).withDefaultCapacity(` | liquid L | stationary process buffer; 81 m³ envelope | Revised; over-cap stock retained |
| `machine/oil/TileEntityMachineRefinery/tanks[3]` (LIGHTOIL) | `24_000` | `8_000).withDefaultCapacity(` | liquid L | stationary process buffer; 81 m³ envelope | Revised; over-cap stock retained |
| `machine/oil/TileEntityMachineRefinery/tanks[4]` (PETROLEUM) | `24_000` | `24_000` | standard L | stationary process buffer; 81 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineShredderLarge/tank` (BLOOD) | `24000` | `24000` | nominal/unreleased | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineSolidifier/tank` (NONE) | `24_000` | `24_000` | L / standard L / nominal | stationary process buffer; 36 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityMachineVacuumDistill/tanks[0]` (HEAVYOIL) | `64_000` | `32_000).withDefaultCapacity(` | liquid L | stationary process buffer; 81 m³ envelope | Revised; over-cap stock retained |
| `machine/oil/TileEntityMachineVacuumDistill/tanks[1]` (HEAVYOIL_VACUUM) | `24_000` | `8_000).withDefaultCapacity(` | liquid L | stationary process buffer; 81 m³ envelope | Revised; over-cap stock retained |
| `machine/oil/TileEntityMachineVacuumDistill/tanks[2]` (REFORMATE) | `24_000` | `8_000).withDefaultCapacity(` | liquid L | stationary process buffer; 81 m³ envelope | Revised; over-cap stock retained |
| `machine/oil/TileEntityMachineVacuumDistill/tanks[3]` (LIGHTOIL_VACUUM) | `24_000` | `8_000).withDefaultCapacity(` | liquid L | stationary process buffer; 81 m³ envelope | Revised; over-cap stock retained |
| `machine/oil/TileEntityMachineVacuumDistill/tanks[4]` (SOURGAS) | `24_000` | `24_000` | standard L | stationary process buffer; 81 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityOilDrillBase/tanks[0]` (OIL) | `64_000` | `64_000` | liquid L | stationary extraction/transport; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/oil/TileEntityOilDrillBase/tanks[1]` (GAS) | `64_000` | `64_000` | standard L | stationary extraction/transport; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/rbmk/TileEntityRBMKBoiler/feed` (LIGHT_WATER) | `10000).migrateFrom(Fluids.WATER` | `10000).migrateFrom(Fluids.WATER` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/rbmk/TileEntityRBMKBoiler/steam` (STEAM) | `1000000` | `1000000` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/rbmk/TileEntityRBMKBurner/tank` (GASOLINE) | `8000` | `8000` | liquid L | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/rbmk/TileEntityRBMKCooler/tank` (CRYOGEL) | `8_000` | `8_000` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/rbmk/TileEntityRBMKHeater/feed` (COOLANT) | `16_000` | `16_000` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/rbmk/TileEntityRBMKHeater/steam` (COOLANT_HOT) | `16_000` | `16_000` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/rbmk/TileEntityRBMKInlet/water` (LIGHT_WATER) | `32000).migrateFrom(Fluids.WATER` | `32000).migrateFrom(Fluids.WATER` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/rbmk/TileEntityRBMKOutgasser/gas` (TRITIUM) | `64000` | `64000` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/rbmk/TileEntityRBMKOutlet/steam` (SUPERHOTSTEAM) | `32000` | `32000` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/storage/TileEntityBarrel/tank` (NONE) | `capacity` | `0` | L / standard L / nominal | stationary storage / packed where supported; native/configured geometry | View/unconfigured; no new storage rating |
| `machine/storage/TileEntityBarrel/tank` (NONE) | `capacity` | `capacity).withDefaultCapacity(` | L / standard L / nominal | stationary storage / packed where supported; native/configured geometry | Revised; over-cap stock retained |
| `machine/storage/TileEntityMachineFluidTank/tank` (NONE) | `256000` | `32_000).withDefaultCapacity(` | L / standard L / nominal | stationary storage / packed where supported; 45 m³ envelope | Revised; over-cap stock retained |
| `machine/TileEntityAirPump/tank` (OXYGEN_GAS) | `16000` | `16000` | standard L | stationary extraction/transport; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityAirScrubber/tank` (CARBONDIOXIDE) | `16_000` | `16_000` | standard L | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityAlgaeFilm/tanks[0]` (CARBONDIOXIDE) | `8_000` | `8_000` | standard L | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityAlgaeFilm/tanks[1]` (OXYGEN_GAS) | `8_000` | `8_000` | standard L | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityAtmoExtractor/tank` (AIR) | `50000` | `50000` | standard L | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityAtmoTower/tanks[0]` (AIR) | `50000` | `50000` | standard L | stationary process buffer; 28 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityAtmoTower/tanks[1]` (NITROGEN_GAS) | `5000` | `5000` | standard L | stationary process buffer; 28 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityChungus/tanks[0]` (STEAM) | `inputTankSize` | `inputTankSize` | nominal/unreleased | thermal/nuclear/nominal boundary; 80 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityChungus/tanks[1]` (SPENTSTEAM) | `outputTankSize` | `outputTankSize` | nominal/unreleased | thermal/nuclear/nominal boundary; 80 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityCondenser/tanks[0]` (SPENTSTEAM) | `inputTankSize` | `inputTankSize` | nominal/unreleased | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityCondenser/tanks[1]` (FRESH_WATER) | `outputTankSize).migrateFrom(Fluids.WATER` | `outputTankSize).migrateFrom(Fluids.WATER` | liquid L | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityCondenserPowered/tanks[0]` (SPENTSTEAM) | `inputTankSizeP` | `inputTankSizeP` | nominal/unreleased | stationary process buffer; 63 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityCondenserPowered/tanks[1]` (FRESH_WATER) | `outputTankSizeP).migrateFrom(Fluids.WATER` | `outputTankSizeP).migrateFrom(Fluids.WATER` | liquid L | stationary process buffer; 63 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityCore/tanks[0]` (DEUTERIUM) | `128000` | `128000` | nominal/unreleased | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityCore/tanks[1]` (TRITIUM) | `128000` | `128000` | nominal/unreleased | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityCoreEmitter/tank` (CRYOGEL) | `64000` | `64000` | nominal/unreleased | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityCoreInjector/tanks[0]` (DEUTERIUM) | `128000` | `128000` | nominal/unreleased | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityCoreInjector/tanks[1]` (TRITIUM) | `128000` | `128000` | nominal/unreleased | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityCoreReceiver/tank` (CRYOGEL) | `64000` | `64000` | nominal/unreleased | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityCustomMachine/i++) inputTanks[i]` (NONE) | `config.fluidInCap` | `config.fluidInCap` | L / standard L / nominal | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityCustomMachine/outputTanks[i]` (NONE) | `config.fluidOutCap` | `config.fluidOutCap` | L / standard L / nominal | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityDeuteriumExtractor/tanks[0]` (LIGHT_WATER) | `1000).migrateFrom(Fluids.WATER` | `1000).migrateFrom(Fluids.WATER` | nominal/unreleased | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityDeuteriumExtractor/tanks[1]` (HEAVYWATER) | `100` | `100` | nominal/unreleased | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityDeuteriumTower/tanks[0]` (LIGHT_WATER) | `50000).migrateFrom(Fluids.WATER` | `50000).migrateFrom(Fluids.WATER` | nominal/unreleased | stationary process buffer; 40 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityDeuteriumTower/tanks[1]` (HEAVYWATER) | `5000` | `5000` | nominal/unreleased | stationary process buffer; 40 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityElectrolyser/tanks[0]` (FRESH_WATER) | `16000).migrateFrom(Fluids.WATER` | `16000).migrateFrom(Fluids.WATER` | liquid L | stationary process buffer; 55 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityElectrolyser/tanks[1]` (HYDROGEN_GAS) | `16000` | `16000` | standard L | stationary process buffer; 55 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityElectrolyser/tanks[2]` (OXYGEN_GAS) | `16000` | `16000` | standard L | stationary process buffer; 55 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityElectrolyser/tanks[3]` (NITRIC_ACID) | `16000` | `16000` | nominal/unreleased | stationary process buffer; 55 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityFurnaceCombination/tank` (NONE) | `24_000` | `24_000` | L / standard L / nominal | stationary process buffer; 18 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityHeatBoiler/tanks[0]` (FRESH_WATER) | `16_000).migrateFrom(Fluids.WATER` | `16_000).migrateFrom(Fluids.WATER` | liquid L | thermal/nuclear/nominal boundary; 36 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityHeatBoiler/tanks[1]` (STEAM) | `16_000 * 100` | `16_000 * 100` | nominal/unreleased | thermal/nuclear/nominal boundary; 36 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityHeatBoilerIndustrial/tanks[0]` (FRESH_WATER) | `64_000).migrateFrom(Fluids.WATER` | `64_000).migrateFrom(Fluids.WATER` | liquid L | thermal/nuclear/nominal boundary; 45 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityHeatBoilerIndustrial/tanks[1]` (STEAM) | `64_000 * 100` | `64_000 * 100` | nominal/unreleased | thermal/nuclear/nominal boundary; 45 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityHeaterHeatex/tanks[0]` (COOLANT_HOT) | `24_000` | `24_000` | nominal/unreleased | stationary process buffer; 9 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityHeaterHeatex/tanks[1]` (COOLANT) | `24_000` | `24_000` | nominal/unreleased | stationary process buffer; 9 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityHeaterOilburner/tank` (HEATINGOIL) | `16000` | `200).withDefaultCapacity(` | liquid L | stationary fuel/day tank; 18 m³ envelope | Revised; over-cap stock retained |
| `machine/TileEntityICF/tanks[0]` (SODIUM) | `512_000` | `512_000` | nominal/unreleased | thermal/nuclear/nominal boundary; 306 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityICF/tanks[1]` (SODIUM_HOT) | `512_000` | `512_000` | nominal/unreleased | thermal/nuclear/nominal boundary; 306 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityICF/tanks[2]` (STELLAR_FLUX) | `24_000` | `24_000` | nominal/unreleased | thermal/nuclear/nominal boundary; 306 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityICFPress/tanks[0]` (DEUTERIUM) | `16_000` | `16_000` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityICFPress/tanks[1]` (TRITIUM) | `16_000` | `16_000` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityITER/tanks[0]` (LIGHT_WATER) | `1280000).migrateFrom(Fluids.WATER` | `1280000).migrateFrom(Fluids.WATER` | nominal/unreleased | thermal/nuclear/nominal boundary; 1 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityITER/tanks[1]` (ULTRAHOTSTEAM) | `128000` | `128000` | nominal/unreleased | thermal/nuclear/nominal boundary; 1 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityITER/tanks[2]` (COOLANT) | `16_000` | `16_000` | nominal/unreleased | thermal/nuclear/nominal boundary; 1 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityITER/tanks[3]` (COOLANT_HOT) | `16_000` | `16_000` | nominal/unreleased | thermal/nuclear/nominal boundary; 1 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityITER/plasma` (PLASMA_DT) | `16000` | `16000` | nominal/unreleased | thermal/nuclear/nominal boundary; 1 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineArcWelder/tank` (NONE) | `24_000` | `24_000` | L / standard L / nominal | stationary process buffer; 12 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineAssemfac/water` (FRESH_WATER) | `64_000).migrateFrom(Fluids.WATER` | `64_000).migrateFrom(Fluids.WATER` | liquid L | stationary process buffer; 256 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineAssemfac/steam` (SPENTSTEAM) | `64_000` | `64_000` | nominal/unreleased | stationary process buffer; 256 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineAutosaw/tank` (WOODOIL) | `100` | `100` | liquid L | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineChemfac/water` (FRESH_WATER) | `64_000).migrateFrom(Fluids.WATER` | `64_000).migrateFrom(Fluids.WATER` | liquid L | stationary process buffer; 256 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineChemfac/steam` (SPENTSTEAM) | `64_000` | `64_000` | nominal/unreleased | stationary process buffer; 256 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineChemplant/tanks[i]` (NONE) | `24_000` | `24_000` | L / standard L / nominal | stationary process buffer; 48 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineChemplantBase/tanks[i]` (NONE) | `getTankCapacity()` | `getTankCapacity()` | L / standard L / nominal | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineCombustionEngine/tank` (DIESEL) | `24_000` | `4_000).withDefaultCapacity(` | liquid L | stationary fuel/day tank; 24 m³ envelope | Revised; over-cap stock retained |
| `machine/TileEntityMachineCompressor/tanks[0]` (NONE) | `16_000` | `16_000` | L / standard L / nominal | stationary process buffer; 36 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineCompressor/tanks[1]` (NONE) | `16_000` | `16_000` | L / standard L / nominal | stationary process buffer; 36 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineCryoDistill/tanks[0]` (AIR) | `64_000` | `64_000` | standard L | stationary process buffer; 210 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineCryoDistill/tanks[1]` (NITROGEN_GAS) | `24_000` | `24_000` | standard L | stationary process buffer; 210 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineCryoDistill/tanks[2]` (OXYGEN_GAS) | `24_000` | `24_000` | standard L | stationary process buffer; 210 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineCryoDistill/tanks[3]` (KRYPTON) | `24_000` | `24_000` | standard L | stationary process buffer; 210 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineCryoDistill/tanks[4]` (CARBONDIOXIDE) | `24_000` | `24_000` | standard L | stationary process buffer; 210 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineCrystallizer/tank` (PEROXIDE) | `8000` | `8000` | nominal/unreleased | stationary process buffer; 54 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineCyclotron/tanks[0]` (FRESH_WATER) | `32000).migrateFrom(Fluids.WATER` | `32000).migrateFrom(Fluids.WATER` | liquid L | stationary process buffer; 75 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineCyclotron/tanks[1]` (SPENTSTEAM) | `32000` | `32000` | nominal/unreleased | stationary process buffer; 75 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineCyclotron/tanks[2]` (AMAT) | `8000` | `8000` | nominal/unreleased | stationary process buffer; 75 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineDiesel/tank` (DIESEL) | `4_000` | `400).withDefaultCapacity(` | liquid L | stationary fuel/day tank; native/configured geometry | Revised; over-cap stock retained |
| `machine/TileEntityMachineDrain/tank` (NONE) | `2_000` | `2_000` | L / standard L / nominal | stationary process buffer; 3 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineExcavator/tank` (SULFURIC_ACID) | `16_000` | `16_000` | nominal/unreleased | stationary process buffer; 196 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineGasCent/tank` (UF6) | `2000` | `2000` | nominal/unreleased | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineGasDock/tanks[0]` (JOOLGAS) | `64_000` | `64_000` | nominal/unreleased | stationary process buffer; 9 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineGasDock/tanks[1]` (HYDROGEN) | `32_000` | `32_000` | liquid L | stationary process buffer; 9 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineGasDock/tanks[2]` (OXYGEN) | `32_000` | `32_000` | liquid L | stationary process buffer; 9 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineHephaestus/input` (OIL) | `24_000` | `24_000` | liquid L | stationary process buffer; 108 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineHephaestus/output` (HOTOIL) | `24_000` | `24_000` | liquid L | stationary process buffer; 108 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineHTR3/tanks[0]` (WASTEGAS) | `1_280_000` | `1_280_000` | nominal/unreleased | stationary process buffer; 539 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineHTR3/tanks[0]` (GAS_WATZ) | `1_280_000` | `1_280_000` | nominal/unreleased | stationary process buffer; 539 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineHTR3/tanks[0]` (GASEOUS_URANIUM_BROMIDE) | `1_280_000` | `1_280_000` | nominal/unreleased | stationary process buffer; 539 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineHTR3/tanks[0]` (GASEOUS_PLUTONIUM_BROMIDE) | `1_280_000` | `1_280_000` | nominal/unreleased | stationary process buffer; 539 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineHTR3/tanks[0]` (GASEOUS_THORIUM_BROMIDE) | `1_280_000` | `1_280_000` | nominal/unreleased | stationary process buffer; 539 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineHTR3/tanks[0]` (GASEOUS_SCHRABIDIUM_BROMIDE) | `1_280_000` | `1_280_000` | nominal/unreleased | stationary process buffer; 539 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineHTR3/tanks[0]` (SUPERHEATED_HYDROGEN) | `1_280_000` | `1_280_000` | nominal/unreleased | stationary process buffer; 539 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineHTR3/tanks[0]` (NONE) | `1_280_000` | `1_280_000` | L / standard L / nominal | stationary process buffer; 539 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineHTRF4/tanks[0]` (PLASMA_DT) | `64000` | `64000` | nominal/unreleased | stationary process buffer; 525 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineHTRF4/tanks[0]` (PLASMA_HD) | `64000` | `64000` | nominal/unreleased | stationary process buffer; 525 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineHTRF4/tanks[0]` (PLASMA_HT) | `64000` | `64000` | nominal/unreleased | stationary process buffer; 525 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineHTRF4/tanks[0]` (PLASMA_DH3) | `64000` | `64000` | nominal/unreleased | stationary process buffer; 525 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineHTRF4/tanks[0]` (PLASMA_XM) | `64000` | `64000` | nominal/unreleased | stationary process buffer; 525 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineHTRF4/tanks[0]` (PLASMA_BF) | `64000` | `64000` | nominal/unreleased | stationary process buffer; 525 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineIGenerator/tanks[0]` (WATER) | `waterCap` | `waterCap` | liquid L | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineIGenerator/tanks[1]` (HEATINGOIL) | `oilCap` | `oilCap` | liquid L | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineIGenerator/tanks[2]` (LUBRICANT) | `lubeCap` | `lubeCap` | liquid L | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineLargeTurbine/tanks[0]` (STEAM) | `inputTankSize` | `inputTankSize` | nominal/unreleased | thermal/nuclear/nominal boundary; 30 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineLargeTurbine/tanks[1]` (SPENTSTEAM) | `outputTankSize` | `outputTankSize` | nominal/unreleased | thermal/nuclear/nominal boundary; 30 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineLPW2/tanks[0]` (KEROSENE_REFORM) | `256_000` | `256_000` | liquid L | stationary process buffer; 980 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineLPW2/tanks[1]` (OXYGEN) | `256_000` | `256_000` | liquid L | stationary process buffer; 980 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineMilkReformer/tanks[0]` (MILK) | `64_000` | `64_000` | nominal/unreleased | stationary process buffer; 63 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineMilkReformer/tanks[1]` (EMILK) | `32_000` | `32_000` | nominal/unreleased | stationary process buffer; 63 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineMilkReformer/tanks[2]` (CMILK) | `32_000` | `32_000` | nominal/unreleased | stationary process buffer; 63 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineMilkReformer/tanks[3]` (CREAM) | `32_000` | `32_000` | nominal/unreleased | stationary process buffer; 63 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineMiningLaser/tank` (OIL) | `64_000` | `64_000` | liquid L | stationary process buffer; 27 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineMixer/tanks[0]` (NONE) | `16_000` | `16_000` | L / standard L / nominal | stationary process buffer; 3 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineMixer/tanks[1]` (NONE) | `16_000` | `16_000` | L / standard L / nominal | stationary process buffer; 3 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineMixer/tanks[2]` (NONE) | `24_000` | `24_000` | L / standard L / nominal | stationary process buffer; 3 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineOreSlopper/tanks[0]` (WATER) | `16_000` | `16_000` | liquid L | stationary process buffer; 84 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineOreSlopper/tanks[1]` (SLOP) | `16_000` | `16_000` | nominal/unreleased | stationary process buffer; 84 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachinePlasmaHeater/tanks[0]` (DEUTERIUM) | `16_000` | `16_000` | nominal/unreleased | stationary process buffer; 18 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachinePlasmaHeater/tanks[1]` (TRITIUM) | `16_000` | `16_000` | nominal/unreleased | stationary process buffer; 18 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachinePlasmaHeater/plasma` (PLASMA_DT) | `PLASMA_BUFFER` | `PLASMA_BUFFER` | nominal/unreleased | stationary process buffer; 18 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachinePumpElectric/water` (WATER) | `electricSpeed * 100` | `com.hbm.realsim.Groundwater.ELECTRIC_BUFFER).withDefaultCapacity(` | liquid L | stationary extraction/transport; native/configured geometry | Revised; over-cap stock retained |
| `machine/TileEntityMachinePumpSteam/water` (WATER) | `steamSpeed * 100` | `com.hbm.realsim.Groundwater.STEAM_BUFFER).withDefaultCapacity(` | liquid L | stationary extraction/transport; native/configured geometry | Revised; over-cap stock retained |
| `machine/TileEntityMachinePumpSteam/steam` (STEAM) | `1_000` | `1_000` | nominal/unreleased | stationary extraction/transport; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachinePumpSteam/lps` (SPENTSTEAM) | `10` | `10` | nominal/unreleased | stationary extraction/transport; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineRadiolysis/tanks[0]` (NONE) | `2_000` | `2_000` | L / standard L / nominal | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineRadiolysis/tanks[1]` (NONE) | `2_000` | `2_000` | L / standard L / nominal | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineRadiolysis/tanks[2]` (NONE) | `2_000` | `2_000` | L / standard L / nominal | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineRotaryFurnace/tanks[0]` (NONE) | `16_000` | `16_000` | L / standard L / nominal | stationary process buffer; 75 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineRotaryFurnace/tanks[1]` (STEAM) | `4_000` | `4_000` | nominal/unreleased | stationary process buffer; 75 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineRotaryFurnace/tanks[2]` (SPENTSTEAM) | `40` | `40` | nominal/unreleased | stationary process buffer; 75 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineSolderingStation/tank` (NONE) | `8_000` | `8_000` | L / standard L / nominal | stationary process buffer; 4 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineStrandCaster/water` (FRESH_WATER) | `64_000).migrateFrom(Fluids.WATER` | `64_000).migrateFrom(Fluids.WATER` | liquid L | stationary process buffer; 14 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineStrandCaster/steam` (SPENTSTEAM) | `64_000` | `64_000` | nominal/unreleased | stationary process buffer; 14 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineTeleporter/tank` (NMASS) | `16000` | `16000` | nominal/unreleased | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineTurbine/tanks[0]` (STEAM) | `inputTankSize` | `inputTankSize` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineTurbine/tanks[1]` (SPENTSTEAM) | `outputTankSize` | `outputTankSize` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineTurbineGas/tanks[0]` (GAS) | `100000` | `100000` | standard L | thermal/nuclear/nominal boundary; 90 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineTurbineGas/tanks[1]` (LUBRICANT) | `16000` | `16000` | liquid L | thermal/nuclear/nominal boundary; 90 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineTurbineGas/tanks[2]` (FRESH_WATER) | `16000).migrateFrom(Fluids.WATER` | `16000).migrateFrom(Fluids.WATER` | liquid L | thermal/nuclear/nominal boundary; 90 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineTurbineGas/tanks[3]` (HOTSTEAM) | `160000` | `160000` | nominal/unreleased | thermal/nuclear/nominal boundary; 90 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineTurbofan/tank` (KEROSENE) | `24000` | `24000` | liquid L | stationary process buffer; 63 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineTurbofan/blood` (BLOOD) | `24000` | `24000` | nominal/unreleased | stationary process buffer; 63 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMachineWoodBurner/tank` (WOODOIL) | `16_000` | `100).withDefaultCapacity(` | liquid L | stationary fuel/day tank; 8 m³ envelope | Revised; over-cap stock retained |
| `machine/TileEntityMoltenSaltReactor/tanks[0]` (THORIUM_SALT) | `SALT_CAPACITY` | `SALT_CAPACITY` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMoltenSaltReactor/tanks[1]` (THORIUM_SALT_HOT) | `HOT_SALT_CAPACITY` | `HOT_SALT_CAPACITY` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityMoltenSaltReactorPort/tank` (input ? THORIUM_SALT : THORIUM_SALT_HOT) | `16_000` | `16_000` | L / standard L / nominal | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityOrbitalStation/tanks[0]` (HYDROGEN) | `16_000` | `16_000` | liquid L | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityOrbitalStation/tanks[1]` (OXYGEN) | `16_000` | `16_000` | liquid L | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityPWRController/tanks[0]` (BORATED_WATER) | `128_000).migrateFrom(Fluids.COOLANT` | `128_000).migrateFrom(Fluids.COOLANT` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityPWRController/tanks[1]` (BORATED_WATER_HOT) | `128_000).migrateFrom(Fluids.COOLANT_HOT` | `128_000).migrateFrom(Fluids.COOLANT_HOT` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityRadiator/tanks[0]` (SPENTSTEAM) | `inputTankSize` | `inputTankSize` | nominal/unreleased | thermal/nuclear/nominal boundary; 9 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityRadiator/tanks[1]` (FRESH_WATER) | `outputTankSize).migrateFrom(Fluids.WATER` | `outputTankSize).migrateFrom(Fluids.WATER` | liquid L | thermal/nuclear/nominal boundary; 9 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityReactorZirnox/steam` (SUPERHOTSTEAM) | `8000` | `8000` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityReactorZirnox/carbonDioxide` (CARBONDIOXIDE) | `16000` | `16000` | standard L | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityReactorZirnox/water` (LIGHT_WATER) | `32000).migrateFrom(Fluids.WATER` | `32000).migrateFrom(Fluids.WATER` | nominal/unreleased | thermal/nuclear/nominal boundary; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityRefueler/tank` (KEROSENE) | `100` | `100` | liquid L | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntitySILEX/tank` (PEROXIDE) | `16000` | `16000` | nominal/unreleased | stationary process buffer; 27 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntitySolarBoiler/water` (FRESH_WATER) | `100).migrateFrom(Fluids.WATER` | `100).migrateFrom(Fluids.WATER` | liquid L | thermal/nuclear/nominal boundary; 27 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntitySolarBoiler/steam` (STEAM) | `10_000` | `10_000` | nominal/unreleased | thermal/nuclear/nominal boundary; 27 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntitySoyuzLauncher/tanks[0]` (KEROSENE) | `128_000` | `128_000` | liquid L | dedicated freight/launch storage; 1 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntitySoyuzLauncher/tanks[1]` (OXYGEN) | `128_000` | `128_000` | liquid L | dedicated freight/launch storage; 1 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntitySteamEngine/tanks[0]` (STEAM) | `steamCap` | `steamCap` | nominal/unreleased | stationary process buffer; 42 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntitySteamEngine/tanks[1]` (SPENTSTEAM) | `ldsCap` | `ldsCap` | nominal/unreleased | stationary process buffer; 42 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityStorageDrum/tanks[0]` (WASTEFLUID) | `16000` | `16000` | nominal/unreleased | stationary storage / packed where supported; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityStorageDrum/tanks[1]` (WASTEGAS) | `16000` | `16000` | nominal/unreleased | stationary storage / packed where supported; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityTowerLarge/tanks[0]` (SPENTSTEAM) | `inputTankSizeTL` | `inputTankSizeTL` | nominal/unreleased | thermal/nuclear/nominal boundary; 1053 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityTowerLarge/tanks[1]` (FRESH_WATER) | `outputTankSizeTL).migrateFrom(Fluids.WATER` | `outputTankSizeTL).migrateFrom(Fluids.WATER` | liquid L | thermal/nuclear/nominal boundary; 1053 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityTowerSmall/tanks[0]` (SPENTSTEAM) | `inputTankSizeTS` | `inputTankSizeTS` | nominal/unreleased | thermal/nuclear/nominal boundary; 475 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityTowerSmall/tanks[1]` (FRESH_WATER) | `outputTankSizeTS).migrateFrom(Fluids.WATER` | `outputTankSizeTS).migrateFrom(Fluids.WATER` | liquid L | thermal/nuclear/nominal boundary; 475 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityTransporterBase/tanks[i]` (NONE) | `extraTankSize` | `tankSize` | L / standard L / nominal | dedicated freight/launch storage; native/configured geometry | Revised; over-cap stock retained |
| `machine/TileEntityTransporterBase/tanks[i]` (NONE) | `extraTankSize` | `extraTankSize` | L / standard L / nominal | dedicated freight/launch storage; native/configured geometry | Retain; saved/configured quantities unchanged |
| `machine/TileEntityWatz/tanks[0]` (HELIUM4) | `64_000` | `64_000` | standard L | thermal/nuclear/nominal boundary; 63 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityWatz/tanks[1]` (HELIUM4_HOT) | `64_000` | `64_000` | nominal/unreleased | thermal/nuclear/nominal boundary; 63 m³ envelope | Retain; saved/configured quantities unchanged |
| `machine/TileEntityXenonThruster/tanks[0]` (XENON) | `4_000` | `4_000` | standard L | stationary process buffer; 18 m³ envelope | Retain; saved/configured quantities unchanged |
| `network/TileEntityDroneCrate/tank` (NONE) | `64_000` | `64_000` | L / standard L / nominal | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `TileEntityMachinePolluting/smoke` (SMOKE) | `buffer` | `buffer` | nominal/unreleased | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `TileEntityMachinePolluting/smoke_leaded` (SMOKE_LEADED) | `buffer` | `buffer` | nominal/unreleased | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `TileEntityMachinePolluting/smoke_poison` (SMOKE_POISON) | `buffer` | `buffer` | nominal/unreleased | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |
| `turret/TileEntityTurretFritz/tank` (DIESEL) | `16000` | `16000` | liquid L | stationary process buffer; native/configured geometry | Retain; saved/configured quantities unchanged |

Inherited BAT9000 storage changes from 2,048,000 to 64,000 through its `TileEntityBarrel` constructor. Industrial chemical lane `getTankCapacity()` is 32,000. Transporter rocket supplies 128,000/64,000 to its shared base. Boiler/turbine capacities expressed through their native static settings retain those settings and existing steam scaling; these expressions are storage, not adopted thermodynamics. Runtime Watz shared tanks begin at zero as aggregate views. Per-machine recipe, thermal and nuclear source contracts still govern the retained rows.


## Phase 5D steam quantity release (2026-10-13)

This supersedes earlier nominal/unreleased steam wording, not capacity or groundwater decisions. Steam grades/spent declare 0.01/0.1/1/10/1 nominal kg water-equivalent per existing count; NBT amounts and IDs are unchanged. Shared information shows water-equivalent units, total mass and enthalpy. Process gases still use standard L; petroleum liquids use L; PU is not thermodynamic pressure.

Converted systems use [one state ledger](REALSIM_PHYSICAL_LEDGER.md#phase-5d-frozen-thermal-contract-2026-10-13). Existing steam capacities are retained independently of ratings. Versioned thermal/exhaust storage is not obtained by scaling saves or certifying old progress. Power buffers retain over-cap stock for rated draining. Industrial boilers add separate 24,000-L oil stores/two solid slots without replacing saved water/steam tanks; the heating oven expands default heat storage to 4 MJ independently of its 500-kW generation/export ceiling.

Closed feedwater circuits replace only the declared 0.5% steam-loss/blowdown equivalent. Passive/radiator/powered cooling has a separate finite ambient heat-rejection rating; it does not evaporate all feedwater. No wet tower/cooling-water species conversion was invented. A future wet/water-cooled adapter must own its separate cooling-water return/evaporative loss; it cannot reuse feedwater mass. Implemented dry/environmental routes close steam mass and heat rejection.

Groundwater remains 120 L/min electric / 30 L/min steam, with prior hydraulics/storage/export. The unchanged 100-kJ shaft parcel now equals 40% of the common ordinary-steam cap. Industrial/combined makeup is 2.379/0.458 L/min, below one pump's rate; once-through demand is much larger, giving condenser return real logistical value. Autonomy and compatibility are in the [Phase 5D report](REALSIM_PHASES_1_4.md#phase-5d-implementation--closed-fossil-thermal-power-2026-10-13).
