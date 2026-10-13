# RealSim phases 1–4: physical foundation and energy-scale decision

2026-10-12. RTM/Deadhour, active Forge 1.7.10 checkout at a 933ecc05. **Source, engineering and progression investigation only. Phase 5 was not performed.** No fuel/recipe/output/consumption/reactor/configuration/ID/NBT/runtime change, build, Minecraft launch, benchmark or loaded-world test occurred.

## Recommendation and document set

Recommend **literal 1:1 energy** for the canonical RealSim engineering model: one represented physical joule is one displayed joule; one electrical quantum remains 0.5 J. Preserve discrete steam grades as **nominal inventory amounts with explicit water-equivalent mass**, distinguish process gases from cryogenic liquids, give machines independent work/heat/flow/power ratings, and accumulate fractional consumption. Do not retain separate flammable/combustible chemical energy contents.

This recommendation supersedes the earlier provisional 1:100 choice. Uniform compression can remain an explicitly labeled later preset, but it is not necessary to avoid generator spam, GW/TW ordinary machinery, or fractional fluid counts. Those issues are principally **rating, material/state, recipe yield, throughput, duty-cycle and storage-policy problems**. The detailed comparison below does not fit a factor to current RTM numbers.

| Deliverable | Document |
| --- | --- |
| Process inventory/classification, actual mechanics, chain recipes and recommended future actions | [REALSIM_PROCESS_INVENTORY.md](REALSIM_PROCESS_INVENTORY.md) |
| Current initialized parameters, buffers, debit/conversion expressions, persistent class registration and complete literal fluid-identity census | [REALSIM_SOURCE_CATALOGUE.md](REALSIM_SOURCE_CATALOGUE.md) |
| Canonical substance/phase/quantity decisions, mismatches and compatibility | [REALSIM_SUBSTANCE_REGISTRY.md](REALSIM_SUBSTANCE_REGISTRY.md) |
| Unscaled cited physical references, conservative steam transitions, fuel/process balances and machine envelopes | [REALSIM_PHYSICAL_LEDGER.md](REALSIM_PHYSICAL_LEDGER.md) |
| Supplied prior fuel/power audit preserved as historical input | [REALSIM_FUEL_POWER_AUDIT.md](REALSIM_FUEL_POWER_AUDIT.md) |
| Scale comparison, plant cases, numerical/migration constraints, supersession and Phase 5 gates | This document |

The source catalogue covers 171 selected tile/base/boundary files, including the explicit fuel-column supplement,225 literal fluid assignments and 37 recipe-owner files. This is a scope census, not 171 individually tested machines or a claim of physical accuracy for 225 substances. Process variants are grouped where they share the same authoritative operation. Existing [industrial audit](INDUSTRIAL_SCALE_AUDIT.md), [thermal design](REALSIM_THERMAL_POWER_DESIGN.md), [production proposal](REALSIM_PRODUCTION.md), [RBMK first-power dependencies](RBMK_FIRST_POWER_DEPENDENCIES.md) and [steam safety](steam-turbines.md) were reused.

The supplied fuel audit ends at “Suggested”; its missing continuation was not reconstructed. Its 316-kW/1-MW factory is a documented illustrative duty-cycle model, not a measured base. Real-world reference values are cited in the physical ledger; selected mixture densities/compositions, machine envelopes and plant sizes are labeled design assumptions. The canonical structure, steam candidate and common-fuel reference proxies can be fixed now. Several chemistry/thermal source entries remain explicitly unreleased, so **a full balance implementation is not yet ready**; the final checklist names exactly what blocks it.

## Phases 1–3 findings that control scale

1. **Electrical units are coherent; independent ledgers are not.** EnergyUnits defines 0.5 J/q and 10 W/(q/t). FluidVolume defines 1000 displayed L/bucket. ThermalUnits presents 0.5 J/TU. None calibrates isotope fuel, HU/core temperatures, FT_Rocket quantities, DFC “joules”, or physical fluid mass.
2. **Diesel's two chemical budgets are confirmed.** Final trait registration rounds 550000 TU/bucket to 275kJ burner heat, and 550000×2.5 to 1370000 electricalq/bucket =685kJ engine budget. A 0.75 diesel multiplier produces 1027q/L =513.5J/L,10.27kW at 1L/t, approximately 187% of the burner budget. Future equipment must apply its efficiency to one LHV, not choose a different chemical energy.
3. **Gas-turbine electricity/recovery are independent.** makePower calculates direct electricity from consMax, fuelq/L and RPM, and separately waterPerTick from consMax×fuelq/L×temperature/220000. It never subtracts electrical work from remaining exhaust enthalpy. The prior~6.6× burner-budget comparison is a default-chain arithmetic result, not observed sustained export or a closed perpetuity proof.
4. **Phase/mass errors dominate several apparent energy errors.** HYDROGEN/OXYGEN/NITROGEN are cryogenic liquids, while electrolysis/air separation/ammonia/oxyhydrogen use gas-like ratios. LPG recovery changes chemical identity as well as state. Methanol synthesis maps standard-gas-like counts into huge liquid quantities. Correct these quantities before assigning joules.
5. **COALGAS is liquid coal gasoline by intent.** Its liquid trait is consistent with that process identity. “High-Octane Diesel” is genuinely problematic terminology for a diesel/reformate mixture; octane gain is not chemical-energy gain or demonstrated diesel suitability.
6. **Generic coolant lacks one identity.** Its thermal registration describes borated water; a chemical recipe makes 90% ethanol, a mixer uses water+niter. Use explicit BORATED_WATER for actual PWR water; generic legacy coolant cannot be silently reinterpreted.
7. **Manufacturing cooling is another broken steam ledger.** Current industrial fabrication/chemistry consume 10 L per active lane tick at base speed, including the completion tick. Eight lanes drawing 8kW can emit 80 spent-steam L/t. Applying the proposed wet-steam enthalpy would imply 3.71GWth without a heat source. This is a model contradiction, not measured heat. Future cooling must return cooled liquid or account genuine evaporation/rejection energy.
8. **Reactor temperature and heat magnitude are different gates.** Magnox's nominal 410 °C CO2 cannot passively supply 450 °C steam; PWR's325 °C primary also cannot. Default ReaSim boiling above 100 °C does not justify 450 °C steam. Neither HU nor column temperature has a universal J conversion. A scale multiplier cannot fix source temperature quality.
9. **Buffers must not choose nameplates.** Industrial turbine input level controls transient rate; boilers/exchangers process whatever their stored heat/room permit; batteries derive W from capacity/600. Fixed flow, thermal-transfer W, event work, averaged demand and peak motor rating are separate fields.
10. **Useful engineering/progression mechanics should survive.** Full-power-buffer turbine pause, inlet/exhaust rupture, Leviathan trips, Magnox shutdown/relief/decay/contamination, reactor controls, backpressure, treated return and natural-uranium bootstrap remain requirements.

Current safety/call ownership was traced through source callbacks, including MachineRuntime accounting rather than assuming updateEntity bodies represent the active process. No scheduler, network architecture, recipe or gameplay was changed. A chemical-energy uplift by itself is not labeled perpetual motion. Sodium/lead/salt exchanger factors exceed 1, but unavailable BOILER heating transitions and nuclear external sources prevent inferring a closed stock exploit from those factors alone.

## Canonical conversion model

For a fuel batch: chemical energy =mass×LHV. A burner delivers a fraction as useful heat and books unrecovered heat/pollution separately. An engine has chemical→thermal→shaft work→generator electricity boundaries, even if compressed into one recipe entry. A gas turbine's exhaust/recovery is **remaining enthalpy after direct work and other losses**. Steam turbines act on a conservative extractable-work transition, never the entire steam heat inventory.

A fully closed illustrative combined-cycle ledger from 100 MJ LHV is: direct electricity 30 MJ +available exhaust 55 MJ +other losses 15 MJ. A heat-recovery unit captures 45 MJ of the exhaust, leaving 10 MJ stack heat. A steam cycle converting 22% of 45 MJ adds 9.9 MJ electricity and rejects 35.1 MJ. Total electricity 39.9 MJ; total rejection 60.1 MJ. No second 100-MJ fuel copy. A more capable explicitly selected cycle may have greater efficiency, bounded by the same residual budget and temperature class.

The proposed steam table preserves 100:10:1:0.1 stored-grade ratios on a nominal 1-kg feedwater-equivalent basis. Physical state values are sourced independently of RTM. Superhot 450 °C/4MPa has 3247.29kJ/kg above cold feed; conservative credited stage work totals 770kJ/kg. At 0.85 machine factor, gross electricity 654.5kJ/kg (20.16% of supplied heat), with explicit residual/turbine/condenser rejection. Hot 300 °C/1MPa uses 2967.69kJ/kg and 467.5kJ/kg gross electricity (15.75%). These are fixed **candidate design rows**, not observed reactor outputs or exact optimized utility cycles. Pressure classes are unrelated to PU.

Retain discrete states; no continuous equation-of-state solver is needed. One canonical table must serve boilers, heaters, compression/reheating, turbines, condensers and reactor output adapters. Legacy traits can be external registration/compatibility adapters, but may not remain an independent authoritative internal ledger.

Steam preparation also needs a defined route. Net enthalpy rise is not the whole electrical bill for raising steam pressure. The physical ledger supplies compressor/aftercooler candidate rows at an adopted 70% isentropic efficiency; a boiler instead pumps liquid feed and supplies heat at a sufficient temperature. Reheating at unchanged pressure cannot grant a higher pressure class. Both routes account retained energy and rejection without duplicate heat credit.

## Phase 4: what a uniform factor actually changes

Define C =physical-reference energy / game energy. Candidate factors are C=1,10,100,1000. All energies and powers in a coherent preset use Egame=Ephysical/C and Pgame=Pphysical/C. **Mass, moles, state identity, volumes, yields and operation time do not scale.** Equipment efficiencies do not scale.

At unchanged physical throughput and duration:

- Generator count =demand/rating: both divide by C, so count is unchanged.
- Fuel autonomy =fuel×LHV×efficiency/demand: both energy and demand divide by C, so duration is unchanged.
- Process overhead as a fraction of generation is unchanged.
- Steam/coolant/fuel flow and the required port/pump count are unchanged.
- If battery/thermal capacity also divide by C, buffering seconds are unchanged. Preserving old capacities while only scaling demand is a separate balance change.

Consequently, a score that credits 1:100 with fewer generators while keeping unscaled MW consumers is testing inconsistent plant premises. The earlier thermal design's convenient integer flows were useful examples, but did not establish an optimal global factor. Fractional flow removes that integer constraint; ratings and operation mass must still be chosen.

### Representative coherent plant set

The following are **constructed physical design cases**, not new defaults. Same physical plant/throughput under every candidate; common reference LHV, state table and selected equipment efficiencies come from the physical ledger. Construction/runtime source gates are evaluated separately below.

| Case | Physical design premises |
| --- | --- |
| Starter workshop | One 40kWe non-nuclear solid-fuel/steam generator; average 18kW, simultaneous normal processing 34kW. Furnace 15 +Fabrication 3 +Chemical Reactor 8 +centrifuge 2 +press 3 +pump/support 3kW. Mean forming peak is buffered; startup heater/boiler must fit the source boundary. Example whole fuel-electric efficiency 7%, not turbine-stage efficiency. |
| Diesel-era facility | One 200kWe genset,32% LHV electricity; approximately 10kW full-rate extraction/refinery auxiliary allowance and 90kW other average shop loads,100kW average total; full-rate fuel/crude/heating infrastructure sized below. |
| Heavy pre-nuclear | Rebuild the supplied 316kW/1.008MW factory by operation/throughput premises:430.65kW mean,1.134MW simultaneous peak. One 750kWe industrial engine at 38% LHV covers the mean; a 100MJ buffer rated≥1.2MW can cover~4.3min of peak deficit, or a second engine covers sustained peaks. |
| First Magnox | **Conditional**20MWth pilot,300 °C HOTSTEAM train,0.85 stage factor; gross 3.151MWe,0.15MWe assumed auxiliaries, net 3.001MWe. Source/core/HU and bootstrap not yet calibrated. |
| First natural-uranium RBMK | **Conditional**60MWth layout with a physically supported superhot outlet;gross 12.093MWe,0.60MWe assumed auxiliaries, net 11.493MWe. A real NU layout/dial/export measurement must prove it; no universal MW per column. |
| Large industrial/nuclear | **Conditional**250MWth plant with superhot discrete train;gross 50.388MWe,2.5MWe assumed auxiliaries, net 47.888MWe;example industry 30MW mean/45MW peak. Heavy machine lines can be 0.5–10MW each without TW nameplates. |

| Candidate | Starter generator / mean / peak kW | Diesel gross / aux / average load kW | Heavy generator / mean / peak kW | First Magnox net MW | NU RBMK net MW | Large net / mean industrial MW |
| --- | --- | --- | --- | ---: | ---: | --- |
| **1:1** |40 /18 /34 |200 /10 /100 |750 /430.65 /1134 |3.001 |11.493 |47.888 /30 |
| **1:10** |4 /1.8 /3.4 |20 /1 /10 |75 /43.065 /113.4 |0.3001 |1.1493 |4.7888 /3 |
| **1:100** |0.4 /0.18 /0.34 |2 /0.1 /1 |7.5 /4.3065 /11.34 |0.03001 |0.11493 |0.47888 /0.3 |
| **1:1000** |0.04 /0.018 /0.034 |0.2 /0.01 /0.1 |0.75 /0.43065 /1.134 |0.003001 |0.011493 |0.047888 /0.03 |

All four operate the same workshop with one starter, the same factory with one engine plus short peak buffer, and the same nuclear physical flows. Under compression the screen W values cease to be ordinary physical nameplates. To retain the same displayed MW industry at C=100, the represented throughput/rating must increase 100-fold, or the throughput/material contract must be explicitly abstracted. That is a second design choice, not a benefit obtained for free from energy compression.

### Starter workshop and storage

The 40kW physical starter consumes chemical power~571.4kW at 7% whole fuel-electric conversion. With 25.8MJ/kg coal this is~1.33kg/min, approximately one proposed 1-kg coal item per 45s. The point is a rated small plant, not a literal continuation of the current 1kW generator's fuel duration. A 1-kg steel heating job at 15kW and 1.5MJ electrical takes 100s; smaller parts/batches, hotter input or larger equipment alter cadence. Do not keep a bulk 1kg job every 5s and declare that 15kW conserves energy.

One generator covers the 34kW normal workshop; a second supports heavy simultaneous jobs/startup. A 10MJ battery rated 50kW provides~294s at 34kW or~556s at 18kW. Existing ordinary battery is 0.5MJ and 16.7kW discharge; it cannot support the whole 34kW workshop merely because E/P gives a duration. Future capacity/rating changes need explicit migration and affordability review.

Starter chemistry must include early H2/oxygen and treated water without requiring a reactor, enriched fuel or an inaccessible high-end electrolyser. Selected 8kW chemistry is an auxiliary/controlled-processing envelope; a reaction with larger work/heat must run more slowly or obtain a charged non-nuclear heat input, not receive free chemistry because it fits an 8kW label.

### Diesel-era acquisition and autonomy

At full 200kWe and 32% LHV, diesel use is **1.047 L/min**, with chemical 625kW and 375kW non-electric heat/loss. A 4000-L fuel store lasts **63.68h** at full load (~127.36h at 100kW if 32% efficiency is retained; part-load losses can shorten this). A 256000-L central tank represents **169.8days** at full load. These are the coherent plant's durations at every C.

Using the current route as a logistics comparison only, diesel yield 0.15×0.55 =8.25% of crude volume requires **12.69 crude L/min** at full generator rating. It is not certified as a physical mixed-phase fractionation yield. At the physical atmospheric-distillation reference 724kJ/L, this throughput uses **153.13kW total utilities**. For an explicit illustrative 5% electricity/95% heat split:7.66kWe and 145.47kWth, with remaining~2.34kW extraction/control allowance giving the 10kW auxiliary example. This split is a modeling choice, not a measured universal refinery utility mix.

At 85% boiler efficiency and 39.592MJ/L heavy oil, process heat needs~**0.259 L/min** heavy oil, versus the comparison route's~6.35 L/min heavy fraction. Enough coproduct fuel exists in this illustrative mass/energy budget; it cannot be credited twice as export fuel and burned process heat. Gas/kerosene/naphtha storage/use must prevent backpressure. A physical 1L/s refinery is already much larger than this facility; fewer plants with throttled/batched processing are plausible. Current 30L/t pumpjacks,100L/t refineries and short-buffer periods are not future protected ratings.

At every C, one reference-scale genset supports the modeled facility, overhead/gross≈5%, fuel/min and tank runtime are unchanged. Useful smaller day tanks can be 100–400L (approximately 1.59–6.37h at full 200kW), with existing 4000/256000-L tanks remaining longer-duration bulk stores. Do not shrink existing saved contents. Long literal bulk-tank autonomy is a real gameplay tradeoff: enforce equipment/material capability, fuel acquisition and stockpiling cost; do not disguise it with contradictory chemistry.

### Heavy pre-nuclear rebuild

The prior audit's316kW mean and 1.008MW peak are retained as **current-value comparison**, with explicitly assumed utilization. The rebuild uses physical process/capability premises rather than multiplying that old sum:

| Load group | Constructed full active kW | Duty assumption | Mean kW / premise |
| --- | ---: | ---: | --- |
| Four fabrication, six chemistry, two centrifuges, large crusher |69 |60% |41.4; 4×5 +6×5 +2×2 +15kW |
| Compressor |25 |25% |6.25; modest process-gas service, pressure work separately stated |
| Large arc furnace |500 |25% |125; ~0.9–1.1t/h active with cited 441–551kWh/t EAF reference |
| Electrolysis, one system |200 |50% |100; at 55kWh/kg active~3.64kg H2/h, not present liquid-output rate |
| Pyrolysis/thermal processing electrical part |100 |50% |50; throughput/reactant/heat split selected per batch before implementation |
| Hydrotreater electrical auxiliaries |20 |25% |5; separate H2 and thermal utility supply |
| Reformer electrical auxiliaries |30 |10% |3; process heat separately supplied |
| Oil extraction/refinery electrical utilities |30 |50% |15; sized batch/stockpile duty, coproduct-fired heat separate |
| Water/cooling support |10 |100% |10; includes loop pumping/conditioning, not infinite literal raw-water flow |
| Mining equipment |150 |50% |75; rated task/throughput abstraction |
| **Total** |**1134** |Individual above |**430.65** |

Some entries are constructed auxiliary envelopes rather than a completed recipe bill. Every recipe later assigned to them must satisfy its specific work/heat/material equation and achievable throughput. This is a scenario for scale comparison, not permission to implement all its rates as universal machine constants.

At 430.65kW mean,38% engine efficiency and 35.819MJ/L diesel, fuel is~**1.90 L/min**;4000L lasts~35.1h. One 750kW engine has~319kW mean headroom; peak deficit 384kW makes 100MJ last~260s, subject to≥1.2MW battery discharge. For persistent peak operation use two engines or a larger matched plant. Scheduling and buffering avoid both the old 31–34 compact-generator average case and 99-generator peak case; this result follows engine **rating and energy per operation**, not C. Small compact gensets remain backup/early equipment rather than a compulsory repeated late-game block.

A gas-turbine/combined-cycle alternative is legitimate if gas supply, residual exhaust, boiler/condensing plant and auxiliaries close. It must not retain the current unbounded steam bonus merely to beat diesel. Coal hydrogenation/gasification may deliberately have expensive utilities, but the useful product mass/energy must be specified before requiring entire fleets of machinery.

### First nuclear and large plant flows

All nuclear examples are **required exported thermal design cases**, not conversions of existing HU or guarantees that current fuel/core behavior can achieve them. First Magnox uses the 300 °C grade to avoid the 410→450 °C temperature violation; a future 300 °C steam boundary still needs exchanger approach/rating and operating proof. NU RBMK's450 °C candidate likewise requires sufficiently hot source conditions; current boiling at 100 °C does not prove them.

| Plant | Heat MWth | Water-equivalent kg/s /kg/t | Stored inlet L/t | Full ordinary-grade header L/t | Gross/aux/net MWe | Warm-condenser rejection MWth |
| --- | ---: | --- | ---: | ---: | --- | ---: |
| First Magnox, hot steam |20 |6.739 /0.3370 |3.370 HOT |33.70 |3.151 /0.15 /3.001 |14.88 |
| NU RBMK, superhot |60 |18.477 /0.9238 |0.9238 SUPER |92.38 |12.093 /0.60 /11.493 |40.80 |
| Large nuclear/industrial, superhot |250 |76.987 /3.8494 |3.8494 SUPER |384.94 |50.388 /2.5 /47.888 |170.00 |

Cold-return normalization and turbine rejection add further heat beyond the last column. With an **assumed**10K rise in a water cooling loop and 4190J/(kg K) approximation, the rejection column requires at least~355/974/4057L/s circulation respectively (~17.8/48.7/202.9L/t); real heat-transfer approach/loss/head add constraints. These are recirculating cooling flows, not feedwater consumption or infinite new makeup. For unchanged physical plant under factor C, heat/cp both scale and these circulation amounts are unchanged.

The current 55L/t Magnox ceiling must not be preserved as the rating at literal thermodynamics:55kg/t corresponds to 1100kg/s, orders above the pilot flow. Source/store sizes are separate from rated output. At 3.849kg/t a 250MWth plant still needs fractional superhot flow and much larger sensible-heat storage/transfer W than current heat boxes. No ordinary machine needs TW merely because final fuel LHV is literal.

Bootstrap requirements are **hand metalworking→starter fossil/biomass/heat power→general fabrication and chemistry→heavy/precision/sealed parts→NU reactor plus complete heat/turbine/condenser/quality-return/control loop→enrichment/high-throughput industry**. Current Magnox natural rod uses empty cladding +2 ordinary uranium billets +10 helium inventory L in 50 chemical ticks; Mg/Be/Al cladding and helium acquisition must be available from non-nuclear metallurgy/atmospheric routes. Current RBMK UEU uses ordinary uranium billets; do not substitute an enriched-only route. A proposed heavy machine must itself be producible using early machinery/tooling and startup electricity. Battery-backed black start, coolant filling/treatment and off-line fabrication complete before first export; assumed auxiliary power cannot simply come from the reactor before it is running.

At 34kW peak, this is feasible with the modeled 40kW workshop for ordinary preparation and larger non-nuclear engines for intermittent heavy work. The exact chosen recipe/capability set and clean-world route must still be checked: a source dependency path is not a survival-play proof.

## Fixed displayed-power stress test

This second comparison deliberately holds screen power at 100kWe diesel/1MWe steam. It answers logistics questions if the product insists on the same displayed ratings while compressing material energy. It is **not** the same physical-throughput plant used above.

| Energy scale |100kWe diesel,30%: fuel L/min |4000L runtime |1MWe superhot train: kg-equivalent/t |Final ordinary steam header L/t |
| --- | ---: | --- | ---: | ---: |
| **1:1** |0.5584 |119.40h |0.076394 |7.639 |
| **1:10** |5.5836 |11.94h |0.763942 |76.394 |
| **1:100** |55.8363 |71.64min |7.639419 |763.942 |
| **1:1000** |558.3629 |7.16min |76.394194 |7639.419 |

At 100kW literal diesel flow is 0.0004653L/t, one whole liquid-L batch about every 107.46s. At 10kW, one batch about every 17.91min. These rates are small but uncomplicated with owned persisted fuel-credit accounting.1000L/t or “a bucket every tick” is unnecessary. Compression at fixed screen W increases extraction/refining/steam/cooling throughput and tank depletion proportionally; it is not automatically more playable. A literal bulk fuel tank's long autonomy is honest physical storage; choose smaller new day tanks/expensive bulk tanks if needed rather than changing what a joule means.

## Implementability, numerical safety and compatibility

### Fractional rates and batch ownership

Use a rational/fixed-point per-tick or scheduled-interval mass/work budget. Integer tank amounts can remain unchanged while a machine reserves one whole input L/batch into an owned fuel/thermal credit and releases energy only at its rated power. **Prepay input** or otherwise reserve it before output; do not emit energy now and lose unpaid fractional debt when a chunk unloads. Rate-limited release prevents one whole-liter energy burst from overflowing a small generator buffer. Persist the remaining fuel mass/chemical credit, fluid identity, state, remainder and transition version; do not reset them on reload, rotation, tank change or recipe change. Prevent duplicate tank/escrow ownership, mixed-fuel credits and “refund plus retained credit”.

Alternatives are longer process intervals and whole-batch jobs (e.g.10s/100s), still with rated heat/work and charged reactants, or lower physical flow from a smaller machine. These are compatible with current server-owned MachineRuntime scheduling; do not introduce a parallel runtime. World mutation stays on the owning thread. Pause/blockage, invalidation, unload and world shutdown must preserve debt/credit and control safety. EnergyUnits already contains a fractional W→q tick accumulator, and gas-turbine fuel/water doubles demonstrate sub-L flow; neither substitutes for audited persistent process ownership.

### Numerical audit

| Boundary | Current evidence | Required future specification |
| --- | --- | --- |
| Electrical storage/transfer | Long q,0.5J; checked conversions in EnergyUnits | Keep exact fixed point and checked multiplication. Long capacity already covers normal kW–GW operation. |
| Thermal stores/traits | IHeatSource/int stores; heatReq, amount fields int; some compound int×int×double arithmetic | Generic thermal J/q stores and products need long/checks before enabling large state values.500000TU current electric-heater heat buffer is 250kJ, less than one literal 1kg boiling charge 2.592MJ. |
| Recipe batch arithmetic | Steam compressor 1000 super L→100 ultra represents 1000kg; retained Δh is 243.2MJ=486.4millionq, but the specified 70%-efficient compression route charges ~739.5MJ=1.479billionq and rejects ~496.3MJ. Input steam enthalpy is 3247.29MJ=6.49458billionq | Check intermediate types before multiplication, not only destination type; no int overflow in whole-batch budgets. Larger/custom recipes can exceed these examples. |
| Fluid capacities/amounts | Int amount/max, short PU; tanks as large as 1e9L | Preserve amounts; use long mass/energy products and rational remainders. An int-max ultrahot tank represents~1.499e17q enthalpy, below 9.22e18 long maximum; repeated additions/multipliers still need bounds. |
| Extreme fictional energy | Capacitors/batteries/DFC amplification, variable quantities | Checked/saturating explicitly documented bounds; ordinary SI model does not make fantasy products physically certified. |
| GUI/packets/API | SI formatting uses doubles; energy/amount packets and compatibility APIs have differing widths | Keep exact long authoritative values; approximate formatted doubles above 2^53q must not feed arithmetic. Review every int/short compatibility export for clamping/width. |
| Reactor internals | Mixed long hull heat, int HU, double temperatures, dials/arrays | Calibrate output boundary; no silent HU→J or temperature→energy cast. Preserve existing control/safety dynamics until a family-specific migration. |

C=100 can reduce some numerical magnitudes, but cannot correct an int multiplication performed before widening, and is unnecessary to make ordinary industrial long arithmetic safe. C=1000 introduces more sub 10-W consumer rounding at fixed physical throughput; persist fractional W accounting instead of setting those jobs to zero cost.

### Existing worlds

- Electrical quanta already mean 0.5J; preserve stored q exactly. Do not multiply old batteries by 100 when selecting 1:1. New capacity/rating defaults and existing serialized maxima need an explicit upgrade policy; retain over-cap stored energy without dropping it, or a documented user-chosen migration.
- FluidTank persists fill, **max**, typeID andPU; changing a constructor default does not retrofit saves. Do not rescale or empty saved fluids. Keep numeric fluid/TileEntity IDs and unknown/custom data intact.
- Preserve existing cryogenic IDs. Future gaseous counterparts and charged phase conversions are preferable to changing existing hydrogen stores into gas liters. Generic coolant identity needs an explicit legacy/canonical adapter decision, not a silent reinterpretation.
- New thermal stores must keep old stored TU's actual legacy 0.5J presentation or use an explicit documented versioned compensation conversion. Reactor HU/temperature cannot use that generic rule. New capacity is not newly granted stored energy.
- Version process accounting separately from the already coherent electrical NBT format. Persist partial work/fuel credit, recipe state and capacity/rating policy with idempotent conversion and safe rollback semantics. Keep legacy templates identifying the same recipe output, not a renumbered recipe.
- Existing fuel quantities gain different future usable budgets after rebalance; disclose this balance effect. Preserve stockpiles and choose explicit release/conversion behavior. Legacy unknown fuels/traits need bounded adapters or unresolved labels; silently inventing physical identity is not compatibility.
- Custom trait/recipe/machine JSON and addon registration require the same canonical state/process ledger or an honest nominal compatibility path. No permanent dual chemical/steam authoritative ledger.

No migration was written or run in this investigation.

## Scale criteria and scores

Scores 1–5 are a declared qualitative design judgment, not benchmark results. Scores for generator count, autonomy and throughput use the **coherent same-physical-plant** comparison; they are equal becauseC cancels. The fixed-display stress test is a separate product/logistics tradeoff.

| Criterion |1:1 |1:10 |1:100 |1:1000 |Reason |
| --- | ---: | ---: | ---: | ---: | --- |
| Physical clarity |5 |4 |2 |1 |Literal SI names correspond directly to physical process power. Compression needs visible “scaled” labels/physical equivalents. |
| Conservation |5 |5 |5 |5 |Any single declared factor can conserve; none cures incorrect phase/mass/duplicate energy. |
| Gameplay progression |5 |4 |3 |2 |Ratios can be preserved, but compressed screen ratings put physical MW processes in kW bands or demand inflated throughput to retain named MW tiers. |
| Generator count |5 |5 |5 |5 |Set ratings and active jobs; no scale-only advantage. |
| Fuel autonomy |4 |4 |4 |4 |Bulk-storage autonomy invariant if demand scales. Smaller day tanks/stockpile cost are deliberate levers; fixed-displayC=1000 is punitive. |
| Fluid throughput |5 |5 |5 |5 |Rated physical plants have manageable flow; rational consumption handles tiny rates. Fixed-display higherC raises flows, not lowers them. |
| Storage |5 |5 |5 |5 |Choose seconds/minutes and independent W; factor cancels if capacities scale. Existing saved stores require migration at every choice. |
| Numerical safety |4 |4 |4 |3 |All require long/checks;1000 adds more tiny electrical rounding. None should avoid structural arithmetic fixes. |
| Existing-world compatibility |3 |3 |3 |3 |All change broken fuel/state/process budgets; no factor authorizes silent NBT/fluid reinterpretation. |
| **Total /45** |**41** |**39** |**36** |**33** |Differences reflect clarity/label/progression judgments, not a falsely scale-dependent generator-count benefit. |

Literal 1:1 is selected from the physical ledger and manageable rated-flow cases, **not** because the prior fuel audit preferred it or because current diesel happened to match a target.1:10 is a defensible explicitly compressed alternative if a future user-facing preset wants smaller numbers;1:100 is possible but loses its former integer-flow rationale;1:1000 provides little engineering benefit. No subsystem-specific energy factor is proposed. Correct phase and mass assumptions first.

## Supersession of prior conclusions

| Existing document/conclusion | Disposition | Current controlling decision |
| --- | --- | --- |
| INDUSTRIAL_SCALE_AUDIT: q0.5J /q/t10W, capacity vs continuous rating | **Retain** | Exact unit contract and independent ratings. |
| INDUSTRIAL_SCALE_AUDIT: preserve current 0.5–2kW workshop and selected large-machine 3–30× adjustment | **Supersede as target** | Physical operation mass/time/work first; scenario workshop 18/34kW with 40kW starter. No universal multiplier. |
| INDUSTRIAL_SCALE_AUDIT: default RTG10kW | **Revise** | Current default decay/528 path allows 600heat→30kW ceiling; pellet-dependent, not guaranteed. |
| INDUSTRIAL_SCALE_AUDIT/old thermal examples: standard 640/industrial 5120 spent-tank capacity | **Revise current snapshot** | Current source 128000/10240000; saved maxima may differ. Pause/rupture mechanics retained. |
| INDUSTRIAL_SCALE_AUDIT: industrial fabrication water 10L only per completed recipe | **Supersede** | Current accounting consumes per active tick. Both industrial families have 8 lanes; heat/mass consequence must be resolved. |
| REALSIM_THERMAL_POWER_DESIGN: default recommended 1:100, integer 1/4/40L/t flows | **Supersede** |1:1 recommended; rational/batch consumption and explicit physical rating. Old plant watt figures conditional/historical, not current accepted targets. |
| REALSIM_THERMAL_POWER_DESIGN: steam inventory distinct from water-equivalent mass; discrete table | **Retain/refine** | Rational mass basis with specified p/T/h/work/rejection, not literal gas volume or PU→bar. |
| REALSIM_THERMAL_POWER_DESIGN: provisional 3180kJ/kg superhot and 550kJ/kg full work without consistent pressure exits | **Supersede candidate table** | New selected 450 °C/4MPa3331.2kJ/kg absolute,3247.29relative;770kJ conservative full work before machine factor. These are still design rows, not reactor calibration. |
| Prior thermal audit: corrected inconsistent direct steam metadata equals reachable staged totals | **Retain current-source finding** | No current stock direct-route perpetual-motion claim. New physical values require every charged transition updated coherently later. |
| Fuel audit: separate burner/engine diesel chemical budgets and unbounded gas recovery | **Retain confirmed problem** | One LHV and remaining-exhaust recovery. Equipment traits become compatibility/ignition/grade, not second energy content. |
| Fuel audit: “coal gas liquid” suspicious / high-octane diesel | **Resolve/revise** | COALGAS intentionally coal gasoline liquid; diesel octane wording/suitability remains a real issue. |
| Fuel processing improvements in current source | **Retain useful intent, revise ledger** | Residue-only coker and narrower catalytic reformer routes are active; disabled old auto registrations are not stock behavior. No phase/mass conservation established merely by improved recipes. |
| Fuel audit:316kW average /~1MW peak | **Retain comparison, supersede future bill** | Constructed 430.65kW/1.134MW process-premise case; measured utilization still required. |
| REALSIM_PRODUCTION: capabilities (cast/form/weld/precision/controlled), bootstrap and shared-recipe caution | **Retain** | General Fabrication Machine builds heavy tooling/machine before capability restrictions; throughput alone not capability. |
| REALSIM_PRODUCTION: fabrication machines remain legacy-driven | **Revise source status** | Current shared assembler/chemistry bases are MachineRuntime-driven; no scheduler migration included here. |
| RBMK_FIRST_POWER_DEPENDENCIES: natural uranium→NU/UEU first power, enrichment optional | **Retain** | Current Rod/Mineral/Smelting path plus Magnox NU helium-backfilled rod route; recipe-mode/runtime bootstrap gate. |
| Steam-turbines: full electrical buffer pause, destructive blocked inlet/exhaust and Leviathan trips | **Retain** | Acceptance requirement for later accounting changes. |
| Reactor HU/J bridges, generic coolant chemistry, final gas/solvent/feedstock assays, isotope-plasma mass | **Defer with explicit gates** | Cannot infer from a name, buffer or copied thermal trait. Must be settled before enabling those paths. |

The old documents remain prior proposal/source snapshots. The main conclusions above supersede conflicting design recommendations; this investigation does not retroactively claim their runtime acceptance.

## Phase 5 readiness checklist — no implementation in this pass

These are release gates, not a request to perform Phase 5 now. “Specified” means this investigation provides an engineering proposal; “unreleased” means implementation must not invent that datum while editing code.

| Gate | Status / exactly what must be settled |
| --- | --- |
| **Canonical energy factor** | **Specified recommendation:1:1.** Accept literal SI or explicitly choose one coherent preset; no per subsystem factors. Preserve 0.5J electricalq and 20t/s simulation-time basis. |
| **Canonical substance/state table** | Basis and primary steam/gas/liquid decisions specified. **Unreleased assays:** generic coolant; final refinery/reform/sour/coker gas composition; solvents/aromatics; feedstock oils/biomass moisture; bromide media; reactive names. Select actual identities and numeric mass/enthalpy coefficients, including isotope gases and fuel salt. |
| **Fuel ledger** | Common petroleum/alcohol/H2 references and blend formulas specified. Finish unresolved assays and solid/log masses; one LHV per substance, compatible machine/ignition grades separate; atmospheric oxygen/exhaust and oil/waste sinks explicit. No unresolved entry treated as zero. |
| **Steam ledger** | Candidate mass/p/T/h/work/rejection rows specified. Accept fixed nominal 1kg water-equivalent convention or consistently use 0.99816kg cold-water-liter density. Finalize machine factors, condensation warm/cold return and residual rejection account; every boiler/compressor/turbine/process-steam recipe uses the same rows. |
| **Machine classification** | Broad inventory/current-source ownership supplied. Accept retain/rebalance/redesign/rename/description actions **per process**; finish recipe-specific operation budgets and throughput, especially physical melting versus fermentation/synthesis in generic machines. Do not apply scenario auxiliary powers universally. |
| **Reactor output boundaries** | **Unreleased:** stable fuel mass/burnup→thermal export, HU/source heat removal, source/sink T approach, nameplate flow and auxiliary work for Magnox, both RBMK modes, PWR, salt, Watz, ITER/ICF. DT packet/shield capture and plasma containment must close; generic physics does not certify fictional fuel behavior. |
| **Cooling/manufacturing steam** | **Unreleased:** choose liquid coolant return versus genuine evaporation for fabrication/chemfac/strand caster/cyclotron; charge machine heat/source state and rate condenser heat rejection. No 8kW machine creatingGW wet-steam enthalpy. |
| **Recipe material/process-energy balances** | Full fuel/chemistry chains need released mass/yield/reactant/heat/work/byproduct rows: fractionation, coking, gasification, coal hydrogenation, reforming, electrolysis, liquefaction, methanol, biofuel, leaded mixing, gas↔liquid conversion. Include both Mixer and Chemplant alternatives and loaded JSON. |
| **Machine/storage/transport ratings** | Scenario bands specified; freeze exact per-machine W, heat-transferW, active-jobJ, batch mass/time, peak/duty and storage duration. Independently rate battery charge/discharge; verify fluid/power ports before assigning pipe/pump counts. |
| **Bootstrap and capability set** | Select reachable heavy/precision/sealed-part/tool recipes and all 528/expensive/LBSM routes. Keep early H2, water treatment, NU fuel and non-nuclear startup. Verify helium/Mg/Be/Al acquisition, machines' own recipes, complete return loop and black-start power; no reactor required to manufacture itself. |
| **Migration/compatibility** | Freeze versioned thermal store/capacity and process-credit policy; preserve saved electricalq/fluid amounts/IDs/unknown data. Retain old cryogenic states and use explicit conversions; decide generic-coolant legacy adapter or opt-in migration. Idempotent partial-progress/template/override behavior and external API limits documented. |
| **Numerical safety** | Specify long checked products and bounds, rational remainders, GUI/packet widths and fake/exotic energy caps. Audit all transition call sites and config overrides before enabling new physical values. No build/toolchain modernization needed. |
| **Remaining runtime measurements** | Obtain loaded-time per-operation debit/yield/cadence and duty profiles; steady turbine/boiler/exchanger/port/condensate flows over several buffer durations; reactor heat/fuel/coolant/aux exports by geometry/dial/shield; cold start, starvation, full power/blocked fluid, trips, unload/reload/world shutdown, legacy saves and dedicated-server behavior. Profile only if later implementation changes hot paths. |

Validation performed here: current-source/callback/recipe inspection, prior-document comparison, authoritative reference lookup, direct arithmetic of ledgers/scale examples, source-link and final-diff checks. No compile/build/reobfuscation/runtime/visual/multiplayer/profiler result is implied. Final recommendation is **1:1 with discrete mass/state accounting and rated throughput**; Phase 5 balance work begins only from released substance/process/reactor rows, not provisional powers copied into code.
