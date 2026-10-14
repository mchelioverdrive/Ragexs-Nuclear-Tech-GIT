# RealSim phases 1–4: physical foundation and energy-scale decision

2026-10-12. RTM/Deadhour, active Forge 1.7.10 checkout at a 933ecc05. **Source, engineering and progression investigation only. Phase 5 was not performed.** No fuel/recipe/output/consumption/reactor/configuration/ID/NBT/runtime change, build, Minecraft launch, benchmark or loaded-world test occurred.

The investigation above is historical. The implemented Phase 5A/5B subset is documented at the end of this file; the remaining thermal, petroleum and nuclear proposals remain future work.

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


## Phase 5A/5B: physical accounting and first generation tranche

Implemented 2026-10-12 for the active Forge 1.7.10 target. Literal SI energy is authoritative for the converted paths: one electrical/thermal quantum remains 0.5 J. This subset does not change electrical inventory, old fluid amounts, existing fluid IDs, manufacturing consumption, battery capacities, steam transitions, petroleum unit yields or reactor balances except the phase corrections explicitly listed below.

### Authoritative APIs and compatibility boundary

`com.hbm.realsim.PhysicalRegistry` supplies immutable `PhysicalState` contracts by fluid name. They specify canonical substance, phase, quantity basis, exact adopted mass coefficient, optional mixture, LHV and thermal reference. Chemical energy is calculated once as mass × LHV. Liquid densities and surrogate assays are adopted from the physical ledger; unresolved and fictional entries return an unresolved contract, with null LHV and throwing physical conversion methods. Unknown is never a zero-energy reference. `FluidType.getPhysicalState()` exposes this dataset independently of compatibility traits.

A mass unit is 1 microgram / 22711, allowing the 22.711 L/mol gas reference and selected liquid densities to coexist exactly. Gas counts mean standard-equivalent liters at 0 °C and 1 bar, even when stored at room temperature or compressed. No general conversion from fluid-network PU to bar is introduced.

`CombustionLedger` partitions one chemical input into useful output, recoverable heat and rejected loss; the sum cannot exceed input. This tranche credits no recoverable exhaust, so all non-useful energy is rejected. The model uses whole fuel-to-electric efficiencies, representing burner/expander/shaft/generator losses without inventing another fuel LHV. `Combustion` owns equipment suitability and tank reservations. `FuelCredit` owns the already consumed useful-energy allowance, separately from the tank. `MassRemainder` owns fractional converted product mass separately for each output identity. `GeneratorStats` reports observed production and equivalent continuous fuel flow. `OutputBudget` limits aggregate export across network ports, adjacent receivers and battery charging in a server tick.

The chemical dataset is independent of `FT_Flammable`/`FT_Combustible`. Their old JSON fields and deprecated public methods remain compatibility parameters for deferred equipment. Internal deferred callers explicitly use `getLegacyBurnerBudget()` / `getLegacyEngineBudgetQuanta()`. Their tooltips no longer advertise competing chemical energies; fluid tooltips show one canonical LHV. Converted equipment never calls those budgets except conservative migration of already removed legacy fuel fractions. Gas turbines, steam equipment, exotic fuels and old chemistry still require later conversion; the presence of this foundation does not certify those paths as a closed physical ledger.

### Selected equipment ratings and progression

| Equipment | Useful nameplate | LHV efficiency | Control and compatibility |
| --- | ---: | ---: | --- |
| Wood/solid starter generator | 40 kWe | 7% whole fuel-to-electric | Existing ignition, fuel mode, air, ash and pollution; solid or physically defined burner fuel. |
| Diesel Generator | 200 kWe default | 32% diesel; 30% diesel/reformate blend; 27% petroil | Automatic generator behavior remains. Waterlogging, air, charging, pollution and full-buffer gating retained. Spark fuels are not ordinary diesel fuel. |
| Industrial Combustion Engine | 750 kWe | 38% × existing piston compatibility factor | Ignition retained; 0–30 slider now sets 0–100% power ceiling, not liters/tick. All pistons share the same mechanical nameplate. Matching piston/fuel improves endurance rather than creating chemical energy or a hidden power tier. |
| Solid firebox | 200 kWth | 85% useful heat | Canonical solid fuel credit; air/waterlogging/ash and heat-buffer gating. Oven remains deferred. |
| Fluid burner | 200 kWth | 85% useful heat | 1–10 setting is a 10–100% thermal power ceiling; canonical flammability and resolved assay required. |

The Diesel Generator configuration adds `L:ratedPowerWatts` (default 200000, clamped to 10–500000 W, output rounded down to the 10 W/tick-quantum resolution). The obsolete `D[:efficiency` chemical multiplier is ignored. Firebox `I:baseHeat`/`D:burnTimeMult` and burn-module bonuses do not set physical fuel energy or the new fixed thermal nameplate; `I:heatCap` still configures storage and burn modules retain ash compatibility. Compressor and water-electrolysis recipe JSON now preserve physical work/power fields.

These are plant ratings, independent of the unchanged generator buffers (starter 50 kJ, diesel normally 25 kJ, engine 1.25 MJ; thermal buffers 50 kJ). A buffer cannot increase export above nameplate. Existing stored energy above a configured capacity remains loadable and can discharge; it is not rescaled or converted into new fuel credit. Old diesel saved capacities are retained; the NITAN capacity multiplier no longer governs future operation.

At the constructed Phase 4 loads, starter reserve is 22 kW at 18 kW mean, diesel reserve is 100 kW at 100 kW mean, and industrial reserve is approximately 319 kW at 431 kW mean. Industrial peaks near 1.13 MW require appropriately rated storage or another source. There is no generator technology eligibility flag on consumers. Consumers, batteries and their transfer limits still have their existing ratings; this tranche does not claim that an ordinary battery can service every peak.

Piston factors are compatibility modifiers: for diesel/HIGH, steel yields 9.5%, Dura 34.2%, Desh/Starmetal 38%. Steel's matching heavy-oil/LOW case also yields 38%. The low diesel result for steel is an intentional poor-match penalty inherited from its fuel preference, not a lower nameplate. Hydrazine and direct cryogenic liquid hydrogen are not reciprocating-engine fuels; hydrogen process gas can use the existing GAS-capable piston. Unresolved BIOFUEL, leaded additive assays, fictional fuels and unassayed feedstocks remain stored and transportable but do not supply invented physical energy to converted equipment.

Starter solid assays explicitly adopt 1 kg per full coal/charcoal/coke/petroleum-coke item or dust equivalent, 9 kg per coal block, and wood reference masses of 4 kg/log, 1 kg/plank, 0.5 kg/stick. Wood uses the ledger's 15.6 MJ/kg wet-reference proxy; these item masses are a chosen material convention, not inferred from furnace ticks or Minecraft dimensions. Other biomass, wooden assemblies and fictional solid fuels remain unresolved. Burn-time modules supply ash compatibility amounts only in converted equipment, never chemical energy bonuses.

### Fuel flow and autonomy at full useful output

| Path / representative fuel | Old flow / output | New full-load flow / output | Representative endurance |
| --- | --- | --- | --- |
| Starter, coal item | About 1 kWe for the old burn timer | 40 kWe; 1.329 kg/min at 7% | 1 kg coal = 45.15 s; charcoal 51.625 s; 4 kg wood log 109.2 s. |
| Starter, wood oil | 2 L/t; about 2.47 kWe | 40 kWe; 1.681 L/min | Existing 16000 L tank ≈158.67 h. |
| Diesel, canonical diesel | 1 L/t = 1200 L/min; 10.27 kWe | 200 kWe; 1.04693 L/min | 4000 L ≈63.68 h full /127.36 h at 100 kW; a 100 L day tank ≈1.59 h full. |
| Engine, setting 30 and matching diesel piston | 6 L/t = 7200 L/min; 82.2 kWe | 750 kWe; 3.30610 L/min at 38% | 24000 L ≈120.99 h full /210.54 h at 431 kW. |
| Fluid burner, diesel, setting 10 | 10 L/t; about 27.5 kWth | 200 kWth; ≈0.39414 L/min | 16000 L ≈676.58 h at full useful heat. |
| Solid firebox, coal | About 2 kWth for a 2000-tick timer | 200 kWth; 0.54720 kg/min | 1 kg coal =109.65 s at full useful heat. |

Old values are source arithmetic at the former defaults, not measured runtime exports. New endurance is calculated at constant selected efficiency and 20 TPS, excluding fuel-acquisition utilities and environmental downtime. Part-load efficiency is intentionally constant in this first model; no idle fuel is burned. Full buffers pause conversion and preserve owned fuel credit. Displayed flow is the equivalent fuel being used from credit, while physical tank withdrawals occur as whole-unit reservations.

### Gas identities and processes

New `HYDROGEN_GAS`, `OXYGEN_GAS`, `NITROGEN_GAS` identities are appended after the existing declarations and loaded custom mappings. Existing cryogenic `HYDROGEN` (ID 38), `OXYGEN` (39), `NITROGEN` (82) retain their meaning. New IDs normally start at 224; allocation skips occupied custom IDs. Existing custom numeric mappings are not overwritten. Configuration reload keeps the registered gas objects and IDs; custom definitions cannot replace built-in names or IDs. Creative ordering adds the new forms without changing old registration order.

At the selected densities, one liquid L corresponds to approximately 797.636 standard H2 L, 809.819 O2 L, or 655.466 N2 L. Whole product counts are emitted and the exact residual mass remains owned by the compressor. Reverse operations preserve the same species mass; pressure and phase do not multiply LHV. The compressor operates at its existing 25 kW rate for physical routes; faster/efficiency upgrades cannot reduce the required work or inherit progress from a different route.

| Existing Compressor route | Adopted work/heat charged |
| --- | --- |
| 1 L cryogenic liquid → room-temperature standard-equivalent gas | H2: 4 MJ/kg; O2/N2: 0.4 MJ/kg, conservatively modeled as electrically supplied vaporization/warming heat. |
| 1000 standard L gas, class 0 → same amount, class 1 | Approximate isothermal 1→10 bar compression at 70% efficiency: 100 J/L × ln(10)/0.70. Class 1's 10-bar meaning is confined to these three process-gas routes. |
| Class 1 gas → cryogenic liquid | H2: 36 MJ/kg (10 kWh/kg); O2/N2: adopted 1 MJ/kg refrigerator work. H2 liquefaction is a separate refrigeration operation after compression. |
| Class 1 gas → class 0 gas, decompression mode | 500 J control work per 1000 standard L; pressure work is rejected without energy recovery. Select the mode through the compressor's labeled control. |

LN2 uses 0.8085 kg/L and LO2 1.141 kg/L near normal boiling points; H2 keeps the physical ledger's 0.0708 kg/L. Nitrogen density and latent heat are supported by [Air Products Safetygram 7](https://www.airproducts.com/-/media/files/en/900/900-13-081-us-nov15-liquid-nitrogen-safetygram-7.pdf); oxygen density is supported by the [Air Products physical properties table](https://www.airproducts.com.cn/en/gases/gas-facts/boiling-points-and-densities). Warming and O2/N2 liquefaction budgets are explicitly selected conservative engineering assumptions, not exact plant performance claims.

The cryogenic separator now supplies process-gas H2/O2/N2 instead of labeling gas proportions as liquid liters. Earth AIR separation charges an adopted 50 kJ per 100 standard L for separation/refrigeration/warming. AIR and the noble tail have explicit initial mixture proxies matching the existing 77:21:1:1 and 96:2:1:1 recipe fractions, so their adopted mass accounts close exactly. This proxy is not a measurement of terrestrial atmosphere. Planetary/other separation yields and their utility costs remain legacy abstractions pending their own assays.

Active Chemical Reactor, mixer, fluid electrolysis, pyrolysis, hydrotreating, reforming, radiolysis, welding and crystallizer recipes now use process-gas forms where they formerly used gas-like H2/O2/N2 counts. Rocket oxidizer/fuel storage, fusion inputs and the existing superheated-H2 coolant path keep their old identities and quantity semantics for the later thermal/nuclear/rocket boundary pass. No additional phase identities were required by this subset; aqueous acids and other unresolved species remain later chemistry work.

The early Chemical Reactor water-electrolysis recipe remains accessible: 1 kg reference water →1260 standard H2 L +630 O2 L, with a 22.2 MJ electrical work budget at 8 kW. The bulk fluid electrolyser performs the same water batch at 200 kW. Integer product rounding rejects about 0.05% residual feedwater as an explicit unrepresented process loss; it never creates mass or extra fuel energy. The work corresponds approximately to 55 kWh/kg H2. Physical work duration and cost are protected from ordinary speed/power/overdrive shortcuts in both single and industrial chemistry lanes. Existing metal/isotope electrolysis remains deferred.

Oxyhydrogen uses 666 H2 gas +333 O2 gas →999 standard blend L. Ammonia uses 300 N2 +900 H2 →600 ammonia gas L. Methanol synthesis uses 1124 standard syngas L +562 H2 gas L →1 liquid methanol L; its sub-percent excess feed is a purge/loss. These are the minimum direct phase/stoichiometric repairs; they do not certify the remaining chemistry utility/yield ledger. Biological, atmospheric and reformer gas outputs adopt the gas identities. Existing populated cryogenic machine tanks are held for draining/conversion instead of erased or reinterpreted. Atmospheric users accept new oxygen gas with their legacy atmospheric identity as a compatibility boundary.

### Persistent ownership and migration

Each converted generator/firebox stores a `realSimFuel` compound with `version=1`, fuel identity, useful credit in microjoules, chemical microjoules per reserved unit and locked equipment efficiency in parts per million. The input is removed before the allowance is created. Remaining credit belongs to the reservation's original fuel and efficiency, including after switching tanks, pistons or fuel mode; new settings apply on the next reservation. Draining the tank never refunds consumed material. Less than 0.5 J unusable output remainder may be discarded when replacing an exhausted reservation; it is a bounded loss, not negative debt or free output.

`realSimGenerationTick` prevents repeated production in the same world tick. Electrical generators also persist `realSimExportTick`/`realSimExportUsed`, so same-tick reload cannot reset aggregate export allowance. There is no elapsed-time production catch-up on reload. The existing MachineRuntime server-thread lifecycle and scheduler remain authoritative; no second scheduler or worker thread was added.

Compressor `realSimMass` contains `version=1` and a map of output fluid names to retained mass units. Remainders survive route changes and reload, and cannot become credit for another species. The existing paid process progress persists with `realSimProcessVersion=1`; physical work changes reset unpaid old progress rather than upgrading it into a new allowance. Legacy generator burn timers/tenths migrate conservatively using their already paid old useful-work budget. Tank amounts, electrical quanta, old numeric IDs and saved generator buffers remain unchanged. Unsupported new account versions are rejected rather than silently reinterpreted.

### Validation and remaining boundary

The JUnit accounting suite uses production ledger, tank, budget and remainder classes, including binary compressed NBT round trips. It covers chemical-input ceilings, one LHV across equipment efficiencies, load-proportional consumption, idle/empty behavior, fuel switching/draining, repeated reload, persistent same-tick export limits, mass preservation, unresolved data, checked overflow and old fluid declaration order. It also checks gas registration stability, built-in identity collision protection and compressor refusal to deposit gas quantities into a preserved liquid output tank. Run `gradlew test` or the normal `gradlew build` with the existing Java 8 tooling.

The implementation surface is the nine `com.hbm.realsim` classes (`PhysicalState`, `PhysicalRegistry`, `CombustionLedger`, `Combustion`, `FuelCredit`, `MassRemainder`, `OutputBudget`, `GeneratorStats`, `PhaseConversions`); the wood, diesel and combustion-engine TileEntities; `TileEntityFireboxBase`/`TileEntityHeaterFirebox` and `TileEntityHeaterOilburner`; their GUIs and piston information; `Fluids`/`FluidType`/`FluidTank` and legacy trait boundaries; compressor, cryogenic separator and chemical/electrolysis recipe owners; the directly affected process-gas recipes, atmospheric users and machine tank defaults; and `PowerNetMK2` export rounding. `PhysicalAccountingTest` and the existing Gradle test configuration provide the automated accounting checks. The other two RealSim registry/ledger documents and `CHANGELOG.md` record the released subset; deferred equipment edits only make the legacy-budget boundary explicit.

Gameplay acceptance still requires observed operation of the three plant sizes, air/waterlogging/controls/pollution, several ports and batteries, fuel changes, saved old stocks, chunk/rotation lifecycle and long-run endurance. A compile or headless accounting test is not that acceptance. The implementation has no technology gate on consumers; the Phase 4 load scenarios remain constructed design anchors rather than measured factories.

Later 5C–5G work remains necessary for petroleum yields and chemistry utilities, gas-turbine/combined-cycle recovery, steam plants, machine demand, cooling water, nuclear boundaries and fictional assays. In particular, legacy coal-to-liquids, hydrazine/acid and other synthetic recipes are not mass/energy-certified merely because their gas phase is corrected. Existing enormous bulk tanks intentionally retain their long SI autonomy. This tranche ends at the common foundation and the selected generation paths.


## Phase 5C implementation — petroleum and fuel-production report (2026-10-13)

Phase 5C supersedes the petroleum/chemistry deferrals in the historical Phase 5A/5B report above. The literal 0.5 J electrical quantum, one mass ×LHV registry, gas identities, 40/200/750 kWe generation anchors and load-dependent diesel consumption remain authoritative. The new petroleum chain supplies those engines through reserved mass bills and paid heat/work. Gas-turbine generation and independent steam recovery remain legacy/deferred for Phase 5D.

The final [assay table](REALSIM_SUBSTANCE_REGISTRY.md#phase-5c-implementation--released-petroleum-assays-2026-10-13) specifies 46 major identities. The [process inventory](REALSIM_PROCESS_INVENTORY.md#phase-5c-implementation--authoritative-process-ownership-2026-10-13) records machine ownership, every removed/replaced route's intent and disposition, and all 100 stock physical batch variants including alternate feeds. The [physical ledger](REALSIM_PHYSICAL_LEDGER.md#phase-5c-frozen-petroleum-contract-2026-10-13) is the frozen model contract. These are adopted RTM representative assays and design envelopes, not measured arbitrary-crude analyses or observed plant performance.

### Material and energy model

One reference L crude contains 0.85 kg at 42.3 MJ/kg. Heated atmospheric separation yields **0.387989182 kg heavy oil, 0.147510818 kg naphtha, 0.2975 kg light oil and 0.017 kg refinery gas**. Light oil is 55 mass% diesel /45% kerosene. The combined path therefore yields 0.196428571 diesel L, 0.16734375 kerosene L, 0.395907329 heavy-oil L, 0.21072974 naphtha L and approximately 12.840 standard refinery-gas L per crude L. The gas is a deliberate 50/50 molar methane/LPG-vapor mixture. The feed and products have equal chemical energy before any fired heat is counted.

Chemical upgrading conserves total mass while bounding product LHV by feed LHV plus paid heat/work. It is deliberately more abstract than an elemental crude simulator. Coal liquids use the stated elemental coal assay: 80%C, 5%H, 5%O, 10%ash, 1 kg/item; 75% of carbon enters a CH2 liquid surrogate, with added H2, water, char, ash and any H2 purge explicit. Methanol follows CO +2H2 →CH3OH with 5% intact syngas purge. Synthetic gasoline follows the compressed FT/stabilization family CO +2H2 →CH2 +H2O. SMR includes water-gas shift: CH4 +2H2O →CO2 +4H2. Sulfur recovery follows H2S +0.5O2 →S +H2O.

Each bill enforces chemical input +sensible input +paid work +paid heat =chemical products +sensible products +rejected heat. Pure separation preserves chemical LHV exactly within a 1 microjoule arithmetic tolerance; heat is not an LHV bonus. Hot crude retains 660 kJ/kg sensible energy; bitumen retains 260 kJ/kg at its declared 150 C. Other cooled petroleum inventory returns to its reference state. Rejected energy is not exported as a second heat allowance. Broader steam/nuclear enthalpy is deferred.

### Before/after process scale and yields

Old rates are source arithmetic at stock settings and 20 TPS, not measured world operation. Geological availability, depletion, damage, catalyst, output backpressure and utility supply can reduce actual rates.

| Process | Previous active model | Released Phase 5C model |
| --- | --- | --- |
| Oil well /pumpjack | Bedrock oil 30/40 L per stock 50/25-tick operation, about 720/1920 L/min; upgrades could accelerate further | 1 L per persisted 200/50-tick extraction interval, 6/24 L/min ceilings. Stock active electrical auxiliaries 1/3 kWe; no upgrade/config bypass of oil ceiling. |
| Crude heating | Oil trait 200 TU/L, 100 J/L, without credible sensible duty | 561 kJ/L thermal for 20→350 C; 24 L/min material cap, 21.390 L/min with one 200 kWth source. |
| Atmospheric refinery | 100 L/t, 120000 L/min; 50%heavy/25%naphtha/15%light/10%gas by count, about 50 We; occasional free sulfur | 24 L/min; fixed mass cuts above; 163 kJ/L heat +36 kJ/L auxiliaries. At nameplate: 65.2 kWth and 14.4 kWe, in addition to upstream crude heating. No free sulfur. |
| Fractionation | 100 L/10 ticks, 12000 L/min; no thermal/electrical debit; mixed liquid/gas count splits | 12 L/min; mass/LHV-closed separation. Light feed: 440 kJ/kg heat +5 kJ/kg auxiliary, 200 C; 70.4 kWth /0.8 kWe at full light-feed rate. Heavy/smear duties are separately billed. |
| Vacuum refinery | 100 L/t, 120000 L/min; cold class-2 crude →40%heavy vacuum/25%reformate/20%light/15%sour gas by count, about 100 kWe | Heavy residue →50 mass% vacuum residue, 25%smear, remaining25% light/lube by energy closure; 12 L/min; 1360 kJ/kg heat +40 kJ/kg vacuum work at 400 C and declared internal 0.1 bar. Full heavy-feed rating 266.56 kWth /7.84 kWe; one 200 kWth source limits actual flow to9.004 L/min. Old saved crude inlet gets a paid flash/separation compatibility bill. |
| FCC | Two 100-L calls/5 ticks, about48000 L/min; fixed 200 steam counts/100 feed and2 spent-steam counts; incomplete coke/heat ledger | 12 L/min; explicit feed-class oil/gas/coke mass fractions. Cold crude/naphtha:85/10/5; residues70/5/25; diesel/heating oil80/15/5; others in bill table. 2 MJ/kg heat,50 kJ/kg aux at500 C; kerosene3.1 MJ/kg. Steam stock cannot pay the heat bill. |
| Hydrotreater | 100 L/2 ticks,60000 L/min; 5–10 standard H2 counts/100 liquid;90–95 liquid plus5–30 sour counts;10 kJ per old call | 12 L/min; about0.5 wt% H2, rounded whole gas feed, all of its mass retained;98%treated/2%sour by combined mass.400 kJ/kg heat +100 kJ/kg aux at350 C; class1 H2. Cold crude at full rate68 kWth /17 kWe. |
| Reformer | 100 L/t,120000 L/min;70–75 reformate +10–20 gas +5–10 H2 counts;200 kWe, missing thermal duty | 12 L/min;94/4.5/1.5 mass% reformate/reform gas/H2.2.6 MJ/kg heat +100 kJ/kg aux at500 C; naphtha364 kWth /14 kWe at nameplate,6.593 L/min with one200 kWth heater. |
| Delayed coker | 10000–16000 L residue →one coke item +about10% fluid output;20k nominal TU progress,10 kJ, most mass absent | 6 L/min; heavy75%oil/5%gas/20%coke; reclaimed70%naphtha/3%gas/5%water/22%coke; coal tar40%naphtha/5%gas/35%water/20%coke by mass.3.3 MJ/kg heat +20 kJ/kg aux at500 C; heavy323.4 kWth /1.96 kWe at material nameplate,3.711 L/min on one200 kWth source. |
| Coke oven carbonization | Coal/lignite item →one coke +50–150 L tar; wood/tar similarly discarded or created material under a nominal heat-progress timer | Same oven/feed families retained. Coal1 kg →0.70 kg coke +0.22 kg tar +0.08 kg vapor loss; lignite0.50/0.22/0.28.2 MJ/item,10 s, heat-only200 kWth. Logs/wood briquettes4 kg →1 kg charcoal +1.2 kg bio-oil +1.8 kg vapor loss;6 MJ/30 s. Solid tar1 kg →0.25 kg coke +0.75 kg corresponding liquid;3.3 MJ/16.5 s. Other oven jobs remain nominal. |
| Methanol | Phase5B correction1124 syngas +562H2 →1 L;120 ticks and provisional utilities | 1200 standard syngas +570H2 →0.804185646 kg methanol +0.039662692 kg intact syngas purge;75 s,600 kJ work +80 kJ heat,250 C.8 kWe /1.067 kWth. |
| Coal liquids | Eight coal items +4000H2 →2000 L gasoline; pyro shortcut one coal +500H2 →1000 L heavy oil | Eight declared kg coal +stoichiometric gas H2 →CH2 liquid,water,char,ash and whole-feed H2 purge.32 MJ work +240 MJ heat,4000 s:8 kWe /60 kWth; roughly7.679 L gasoline (or6.595 L coal crude) rather than2000 L. |
| Gas recovery /sweetening |2000 refinery-gas counts →1000 liquid LPG; sour gas split arbitrarily50/50 | Same gas composition/mass translated into liquid LPG +returned methane; sweetening yields90/10 molar methane/H2S. Separate paid sulfur recovery. Pressure conversions use charged gas compressor bills. |
| Blending /lead treatment | Petroil proportions recognizable but giant short batches; some bio routes lost bulk; leaded output gained20% volume | Petroil80/20 reclaimed/lube and biofuel80/20 oil/ethanol by input reference volume; weighted mass and chemical energy, both fish and seed oil variants. Diesel/kerosene reform blends90/10.5 kJ/L auxiliaries,12 L/min; one10 g additive per10000 L creates only its own trace mass. |

All changed equations and utility powers, including extraction, fermentation, alkylation, tar melting and supported coal synthesis variants, are in the released bill matrix rather than inferred from fluid-name resemblance. The removed-route review covers the former radiolysis copy, mixer gasification/lubricant shortcuts, irradiation fuel production, wet bio-oil upgrading, unassayed atmospheric/biological splits and old burn-time solid-fuel manufacture. Viable coke oven and fish-oil blend routes were retained. Dedicated gasifiers, fermenters, oil presses, hydro-upgraders, aromatics extraction and solid-fuel presses are future machine recommendations, not invented completed additions.

### Coproducts and facility calculations

These constructed facilities use only the uncracked atmospheric→light-fractionation diesel path. Per crude L it requires854.9 kJ thermal and37.4875 kJ electric, excluding the well/pump auxiliary. The totals already include crude sensible heating, separation and light fractionation. Whole-unit scheduling and retained mass make actual tank delivery discrete; figures below are steady averages.

For sustained heat, allocate refinery gas to the crude heater and light fractionator, and heavy oil to the atmospheric separator. At85% burner efficiency, this consumes0.016949907 kg refinery gas and0.004843522 heavy-oil L per crude L. Those amounts are **subtracted from export stocks**. Only about0.037835 standard gas L per crude L remains from the refinery. A startup fuel/heat stock is required before the first gas batch; the existing starter generator and coal-fired heater can start the chain. Associated well gas is additional stock and is not credited in these calculations; afterburner is off.

| Steady case | Small diesel facility | Heavy pre-nuclear facility | Expanded diesel-only fossil plant |
| --- | ---: | ---: | ---: |
| Generator gross output |100 kWe mean from one200 kWe genset |431 kWe mean from one750 kWe matched diesel engine |3000 kWe from four750 kWe matched diesel engines |
| Diesel L/min |0.523465 |1.899903 |13.224384 |
| Crude L/min |2.664914 |9.672234 |67.324137 |
| Process heat kWth |37.9706 |137.8132 |959.2567 |
| Process electrical kWe |1.6650 |6.0431 |42.0636 |
| Stock well/pump electrical allowance kWe |1 |3 |9 |
| Net to other consumers kWe |97.3350 |421.9569 |2948.9364 |
| Heavy oil produced /burned internally /export L/min |1.05506 /0.01291 /1.04215 |3.82931 /0.04685 /3.78246 |26.65412 /0.32609 /26.32803 |
| Naphtha export L/min |0.56158 |2.03823 |14.18720 |
| Kerosene export L/min |0.44596 |1.61859 |11.26627 |
| Refinery gas produced /remaining export standard L/min |34.218 /0.101 |124.192 /0.366 |864.443 /2.547 |
| Oil wells /pumpjacks |1 /0 |0 /1 |0 /3 |
| Crude heating boilers /atmospheric refineries /light fractionators |1 /1 /1 |1 /1 /1 |4 /3 /3 |
| Separate200 kWth process-heater blocks |3 |3 |10 (4 crude,3 refinery,3 tower) |
| Vacuum /FCC /hydro /reformer /coker /reactor /mixer required by baseline |0 each |0 each |0 each |
| Generator fuel autonomy at that load |4000 L ≈127.36 h |24000 L ≈210.54 h |Each24000 L engine tank ≈120.99 h |
| Full refinery crude-inlet autonomy |64000 L ≈400.27 h |64000 L ≈110.28 h |Three64000 L stores ≈47.53 h |
| Fractionator kerosene-buffer fill time without export |4000 L ≈149.49 h |4000 L ≈41.19 h |Three4000 L buffers ≈17.75 h |

Well/pump electricity is a conservative continuously active stock allowance; geology, full tanks, upgrades and deliberate dispatch change actual duty. For **100/431 kW net consumer demand**, including the same oil auxiliaries, use approximately102.710/440.172 kWe gross,2.737137/9.878061 crude L/min and0.537652/1.940333 diesel L/min. The same machine counts cover those cases.

One6 L/min well supplies approximately1.17857 diesel L/min before utility/export constraints, enough for the full200 kWe genset's1.04693 L/min and a modest reserve. One pump and one200 kWth crude heater supply up to21.390 crude L/min through this train, about4.20168 diesel L/min, enough for one full750 kWe diesel engine's3.30610 L/min plus stockpiling. The crude heater, not the refinery block count, is the first thermal capacity limit.

The diesel-only expanded case deliberately does not credit exported heavy oil, naphtha or kerosene as electricity. **It is not the minimum oil input for every3 MW plant.** An integrated coproduct plant can use two diesel engines and three matching LOW/steel heavy-fuel engines:21.148671 crude L/min supplies4.154203 diesel L/min (942.396 kWe) and8.270480 export heavy-oil L/min (2073.817 kWe). Subtract16.214 kWe of oil auxiliaries for3 MW net. One pump, one heating boiler, one refinery, one light tower and three process heaters suffice arithmetically; the crude heater runs near197.740 kWth, so a second heating train gives operating reserve. Different engine fuel lanes require five engine blocks for this allocation; excess naphtha/kerosene remain useful stocks. This calculation debits process fuel before generation and does not count the same heavy oil twice.

Those integrated figures show why legitimate coproduct use matters: petroleum can supply a few MW without refinery spam. Above a train's material/thermal capacity, more wells, heater trains, storage/export and engines are required. Naphtha upgrading is optional: one reformer plus finishing reactor covers each small/heavy case; the diesel-only expanded naphtha flow needs three reformers on individual200 kWth heaters and two finishing reactors. Heavy oil also feeds vacuum/FCC/coking/lubricants or matching heavy engines; kerosene supplies aviation/heating; gas feeds burners or compatible gas engines, LPG recovery and chemistry; coke feeds solid fuel/metallurgy; sulfur has its paid recovery/chemistry route.

Existing bulk storage intentionally gives days of autonomy and was not shrunk. Coproduct buffers still require an outlet over long operation; backpressure pauses paid ownership, it does not discard products. Future centralized gas-turbine/steam/nuclear comparisons require Phase5D and later thermal/nuclear ledgers. No nuclear eligibility flag, forced fuel expiry or technology gate was introduced.

### Accounting, persistence and compatibility

New `ProcessRecipe`, `ProcessBatch`, `ProcessMachine`, `PetroleumProcesses`, `ChemicalFuelProcesses` and the shared `PetroleumRecipeLoader` extend the existing RealSim state/mass foundation. `IProcessHeatSource` supplies an explicit temperature-qualified0.5-J heat debit; the existing heat proxy delegates only to a qualified actual core. `PhysicalState` adds sensible J/kg; `PhysicalRegistry` declares the final petroleum/gas/solid assays; `MassRemainder` retains literal owned mass across a density/assay revision even if it now exceeds one output unit. No new fluid or block IDs were needed by5C.

Every machine/lane stores `realSimProcess` version1: full owned recipe snapshot, input quantities/pressure, exact product masses, duration, electrical/thermal bill, paid counters, progress and last successful loaded world tick. The same compound contains `realSimMass` version1 with separate product identities and retained integer mass. A selected recipe/template/reload cannot overwrite an active owned batch. Output capacity and matching existing tank type/pressure are checked before reservation and completion; input is removed exactly once. No partial feed refund, offline catch-up, repeated-tick progress or unpaid completion is available. Legacy unowned progress cannot become physical credit.

Oil drills persist `realSimExtractionTick`, and their afterburner uses the existing versioned fuel account. New FCC/fraction/coker auxiliary stores persist literal energy quanta; coker old inlet/oil tanks retain their saved names and quantities, with new gas/water tanks added. Old cryogenic chemistry stocks are preserved for draining/conversion rather than relabeled gas. Filled tanks now refuse recipe-induced type/pressure replacement for every species, preventing coproduct deletion on switching; explicit player tank reconfiguration keeps its existing behavior.

Petroleum, affected reactor/mixer and coke-oven recipe JSON now carries a `physical` object with input identities/counts/pressure, solid identity/count, exact output `massUnits`, duration, utility J, temperature and separation flag. It replaces the stock bill; old preview fields are not a second runtime source. Invalid/unresolved/mass- or energy-unbalanced bills are rejected. Custom bills must respect the relevant material and work/heat rating, and ordinary hydrogen chemistry cannot use cryogenic liquid H2. Old volume-only petroleum JSON must be regenerated or deliberately translated to a physical bill; it is not silently interpreted under new units. Legacy public tuple getters and percentage constants remain metadata adapters for source compatibility; complete coproduct/work ownership requires the physical API.

### Observed validation and remaining acceptance

The repository's existing Java8/Gradle4.5 tooling completed an offline clean build and a final incremental build: compilation,34 JUnit tests, JAR creation and `reobf` all succeeded. The petroleum suite validates100 production bill variants, pure-separation chemical closure, total sensible/work/heat closure, weighted blends, hydrogen/gas mass, coker/FCC/reforming and coke-oven outputs, more than3000 batch/species remainder cycles with compressed binary NBT, unpaid/duplicate output refusal, partial paid save/load, recipe changes, no elapsed unload production, repeated-tick limits, nameplates, physical JSON round trips/corruption and unresolved inputs. Real filled-tank rules are tested for species and hydrogen-pressure preservation. Existing Phase5A/5B accounting tests remain green. Compiler deprecation/unchecked notes are existing warnings, not hidden.

These tests exercise production bills/accounts and tank rules; they do not instantiate a loaded Minecraft world or prove every native controller lifecycle. Gameplay checks still required: actual multiblock heat/power/fluid ports, stacked tower transfer, coke removal, reactor single/industrial lanes, catalyst removal, full coproduct backpressure, legacy-stock draining, template/recipe reload, chunk unload/save/reload, fire/repair/pollution, afterburner/export separation, sustained facility rates and startup fuel. No Minecraft launch, multiplayer/server-connection tests or UI acceptance were performed. Phase5D remains unimplemented.


## Infrastructure and UX normalization — 2026-10-13

A separate tranche after Phase 5C adopts role-sized liquid containers/day tanks, geometric bulk-storage limits, selected petroleum buffer capacities and bounded groundwater supply. It preserves saved amounts/identities with explicit legacy-over-cap handling and separates storage from export/processing ratings. Player quantities, canonical LHV/stored energy, gas standard-equivalent wording and idle rated efficiency now share one presentation path. The pump's steam-work allowance remains explicitly transitional; Phase 5D has not begun.

The [fluid infrastructure report](REALSIM_FLUID_INFRASTRUCTURE.md) is authoritative for changed capacities, retained compatibility boundaries, the complete native tank declaration inventory, groundwater settings/work model, revised storage endurance and acceptance checklist. Earlier Phase 5A–5C storage examples remain historical. The user's initial placement smoke test is recorded separately from sustained operation, UI, ports, rotation, save/load and multiplayer acceptance.


## Phase 5D implementation -- closed fossil thermal power (2026-10-13)

This supersedes earlier gas-turbine/steam deferrals for converted fossil equipment; nuclear calibration remains deferred. The [frozen ledger](REALSIM_PHYSICAL_LEDGER.md#phase-5d-frozen-thermal-contract-2026-10-13) defines all states, coefficients, legal routes and heat/work/rejection. Saved counts and IDs are unchanged. Steam is nominal water-equivalent mass, not literal gas volume or network PU pressure.

### APIs and migration ownership

New production APIs: `SteamLedger`, `ThermalAccount`, `ThermalMachine`, `TurbineFuelAccount`, `ExhaustAccount`, `HeatRecovery`, `HeatTransfer`, `ThermalInfo` and `IRatedGenerator`. `IProcessHeatSource` separates stored heat from a shared per-tick export debit, including proxy delegation. `ProcessMachine.stepWithHeater` supports qualified integrated heat without recreating a process bill. `PhysicalState.Basis.WATER_EQUIVALENT`/`PhysicalRegistry` publish steam mass. `Groundwater` derives its unchanged 100-kJ owned shaft parcel from 40% of the common ordinary expansion cap.

Thermal mass is integer milligrams; utilities retain half-joule quanta; undelivered heat/work/electric fractions use microjoules. Checked long arithmetic and checked wide multiplication/division protect authoritative results. Each versioned account owns removed inlet mass, inlet/outlet identity, paid utility fragments, pending electricity, output mass by species, steam-loss remainder and last world tick. No elapsed unload production, repeat/backwards-tick work, unpaid completion or identity rewriting is allowed. A full/incompatible steam outlet or electrical buffer prevents further conversion. Floating point is presentation only. Heater decay is integer 999/1000, and electric-heater/heat-exchanger funding and decay also persist their once-per-world-tick controller guards.

Changed controllers: gas turbine, turbofan, flare, boiler base/industrial boiler, steam engine/standard/industrial/Leviathan turbines, condensers/radiator, steam compressor, heat exchanger, solar boiler/mirrors, Hephaestus steam/crude heating, heating oven and qualified solid/fluid/electric heater boundaries. Related changes cover heat proxies, compressor metadata, steam quantity information, boiler/engine/condenser/Leviathan overlays, firebox/standard/gas GUIs and English descriptions. Existing runtime scheduling, server-thread mutation, controls, air/pollution/lubricant and optional integration paths remain native. No block/fluid ID was added.

| Owner | Rating and state | Storage / startup / separate constraint |
| --- | --- | --- |
| Solid firebox / fluid burner | 200 kWth generation/export, 85% LHV, 900 C ceiling | Existing heat stores retained |
| Heating oven | 500 kWth combined delivered firing + intake/export, 85% firing, 900 C | 4 MJ default store; qualified lower intake defaults to 50%; imported heat reduces firing within the same rating |
| Electric heater | 200 kWe, 98% resistive conversion; up to 200-kW intake at 85%, 400-kW combined export | Existing 0.25-MJ heat store; settings change power, not LHV |
| Ordinary boiler | 200 kWth, 98%, ordinary steam; 0.2 kg/s feed ceiling | 16 MJ heat, 0.25 MJ prepared structure/startup; actual full heat limits feed to about 0.07563 kg/s |
| Industrial boiler | 12 MWth combined solid/oil firing + qualified heat, 98%, hot steam; 8 kg/s feed ceiling | 64 MJ heat, 5 MJ startup; 85% LHV firing, 1.5 kJ/kg paid pump work, 1-MJ pump buffer |
| Solar boiler | 500 kWth collector, 95%, ordinary; 0.2 kg/s feed | 8 MJ heat, 0.5 MJ startup; each full-sky 4-m2 mirror supplies 3 kWth |
| Hephaestus | 2 MWth, 95%, ordinary; 1 kg/s feed | 16 MJ heat, 2 MJ startup; 1/20/40 kW per normal lava/volcanic lava/ore contact, existing fissure modifier and global cap; crude uses Phase 5C bills |
| Steam heat exchanger | 2 MWth recovery/export, conserved lower-grade/return mass | 32 MJ recovered heat maximum; quality no higher than outlet temperature minus 30 C; nuclear coolant legacy heat is uncertified |
| Internal gas HRSG | 6 MWth exhaust input, 90%, 30 C approach; hot/superhot, 4 kg/s feed | Draws 150-MJ exhaust account; paid feed work uses real electrical storage |
| Small engine | Ordinary -> spent, 70% credited work; 0.3 kg/s | 25 kWe, 0.5-MJ electrical store retained until export |
| Standard turbine | Any released steam -> spent, 75%; 0.75 kg/s | 500 kWe ceiling, 5-MJ default buffer, rated aggregate export |
| Industrial turbine | Any steam -> spent, 85%; 8 kg/s | 5 MWe ceiling, 50-MJ default buffer |
| Leviathan | Any steam -> spent, 88%; 40 kg/s | 25 MWe ceiling; existing 50-GJ stock buffer, rated export, trips/reset retained |
| Passive condenser / radiator | 250 kWth / 1 MWth environmental rejection; spent -> cold | Conserved passive/gravity return; radiator supports vacuum radiation |
| Powered condenser | 20 MWth total rejection; spent -> cold | 10 kJ/kg vacuum/fan/drain auxiliary, 5-MJ existing electrical store; atmospheric air sink |
| Steam compressor + aftercooler | 25 kWe work, 100 kWth rejection; 0.05 kg/s ceiling | Adjacent upgrades cost 735.61 / 599.73 / 739.50 kJ/kg; upgrades cannot discount physical work |

Actual turbine power is bounded by state work, flow, nameplate and free storage. Standard ordinary/hot/superhot output at stock flow is 140.625/309.375/433.125 kWe; ultrahot can reach 500 kW. Industrial hot reaches 3.74 MW, superhot/ultrahot reach 5 MW. Leviathan superhot/ultrahot reach 25 MW. Each native turbine now performs the full condensing expansion once and emits SPENTSTEAM, so a legacy staged installation must reroute exhaust; filled intermediate stock is retained until drained. Rupture, trip valves, overspeed/overpressure and reset remain meaningful.

Industrial boilers have two persisted solid-fuel slots, hand/hopper feeding and existing inventory drops for unburned items. Heavy/heating-oil has a separate 24,000-L inlet; select it with a fluid identifier only when empty. Solids/liquids share one owned fuel account. Cable feed-pump input uses existing ports, including older placed proxies. A 200-kW source cannot exploit the boiler's 12-MW rating.

Gas turbine: 3 MWe gross, 2.94 MWe after 60-kW own auxiliaries, 10-MW chemical input, 5.5-MW recoverable heat, 1.5-MW initial rejection. Part-load cycle efficiency is explicitly constant 30%; throttle changes flow/power/exhaust, never LHV. Admitted load is 25-100%, with transient RPM ramp and a 5% idle/spool chemical allowance; startup produces no electricity. Retained 580-tick startup costs up to 14.5 MJ over 29 s; about another 22.5 s reaches full RPM. Temperature/load conservatively chooses 350/500-C exhaust. Excess-air demand is roughly 500 standard inventory units/t at full load through the existing intake abstraction. Lubricant ownership persists at 0.06 L/min while firing/spooling. Shutdown/spool state persists; only completed startup admits the online state.

Fuel whitelist: natural gas, refinery gas, reform gas, coker gas and released syngas, each with its actual Phase 5C assay. This adopted multi-fuel combustor supports those H2-bearing mixtures; pure H2, sour gas, oxyhydrogen, fictional/unresolved gases and liquid LPG are refused. Refinery gas's LPG-vapor component is already proper gas feed. No standalone LPG vaporizer was invented.

Turbofan remains a 1-MWe aviation-derived generator. Base input is 4 MW at 25% LHV; afterburner levels 1-3 increase fuel to 8/12/16 MW and reduce efficiency to 12.5/about 8.333/6.25%, with electricity capped at 1 MW. Exhaust partitions are 55% base / 75% afterburning, with explicit residual rejection and retained jet/fire/damage behavior. Flares retain disposal/vent/fire/pollution/rates and saved electricity export, but generate no new electricity. Old yield upgrades remain stored and clearly have no recovery effect.

### Facility models and progression

These are analytical steady-state figures from exact adopted bills, not gameplay measurements. Native processing retains milligram/half-joule/whole-output fractions; rate ceilings may round down by less than one milligram/t. BOILER steam loss is 0.5%. Makeup pumping assumes active 120 L/min delivery and full-buffer pause: mean power is 10 kW times makeup demand/120. Continuously running makeup supply instead debits 10 kW. Water treatment, fuel production, startup and external distribution are outside these fuel-at-inlet boundaries, not claimed free.

| Quantity | Early firebox | Oven-fed hot steam | Industrial fossil | Combined cycle |
| --- | ---: | ---: | ---: | ---: |
| Chemical input MW | 0.235294118 | 0.588235294 | 28.235294118 | 10 |
| Source heat / exhaust MWth | 0.200 | 0.500 | 24 | 5.5 exhaust |
| Prepared feed kg/s | 0.075626318 | 0.165195054 | 7.929362595 | 1.527169455 |
| Steam kg/s / grade | 0.075248187 ordinary | 0.164369079 hot | 7.889715782 hot | 1.519533608 superhot |
| Steam gross electricity MW | 0.013168433 | 0.067802245 | 3.688442128 | 0.994534746 |
| Direct gas electricity MW | 0 | 0 | 0 | 3 |
| Gas auxiliaries kW | 0 | 0 | 0 | 60 |
| Feed pump kW | 0 | 0.247793 | 11.894044 | 9.163017 |
| Condenser heat MW excluding auxiliaries | 0.174281573 | 0.380693580 | 18.273291825 | 3.519376594 |
| Condenser electricity kW | 0 | 0 | 78.897158 | 15.195336 |
| Makeup L/min | 0.022687895 | 0.049558516 | 2.378808778 | 0.458150837 |
| Mean makeup pump kW | 0.001891 | 0.004130 | 0.198234 | 0.038179 |
| Net electricity MW | 0.013166542 | 0.067550323 | 3.597452692 | 3.910138214 |
| Net efficiency % | **5.595780** | **11.483555** | **12.740978** | **39.101382** |

Early: one 200-kW firebox, ordinary boiler, small engine and passive condenser. Coal 0.547195622 kg/min, wood 0.904977376 kg/min. The 16,000-count water inlet lasts 58.7685 h once-through or 11,753.7 h against makeup alone. This is a useful modest automatic solid-fuel alternative, not an efficiency upgrade over the 40-kW/7% starter or a diesel competitor. Abundant solid fuel/shared process heating are its role.

Oven: one 500-kW oven beneath an industrial boiler whose own fuel supply is empty, one standard turbine on hot steam, and one radiator (or two passive condensers). Coal 1.367989056 kg/min, wood 2.262443439 kg/min; about 67.55 kW net. The full expansion needs no second turbine. Imported qualified heat reduces oven firing; its store never grants higher boiler power. An ordinary boiler still limits an oven-fed loop to 200 kWth.

Industrial: two integrated 12-MWth boilers, **one industrial turbine**, one powered condenser, makeup/treatment and piping. Flow 7.88972 kg/s is below 8; total condenser rejection including auxiliaries is 18.352189 MW below 20. Coal 65.663474692 kg/min total, heavy oil 42.789392985 L/min, heating oil 47.296620427 L/min. Two coal feeders hold 256 ordinary coal items, about 3.90 min: hopper/storage logistics matter. Each 24,000-L heavy-oil tank lasts about 18.70 h; each 64,000-count feed tank about 4.484 h once-through or 37.37 days makeup-only. Four main thermal machines give 3.60 MW net. Coal/biomass is the rational alternative; engine-suitable oil remains more efficient in matching reciprocating engines.

| Gas at 3-MWe rating | Standard L/min | kg/min | 100,000-standard-L autonomy |
| --- | ---: | ---: | ---: |
| Natural gas / methane | 16983.573 | 11.996754 | 5.888 min |
| Refinery gas, 50/50 molar methane/LPG vapor | 9436.498 | 12.493789 | 10.597 min |
| Reform gas | 26100.448 | 10.376706 | 3.831 min |
| Coker gas | 35675.977 | 8.675156 | 2.803 min |
| Syngas | 51930.047 | 34.328091 | 1.926 min |

Each simple-cycle case has 10-MW chemical input, 3-MW gross electricity, 5.5-MW recoverable heat, 1.5-MW initial rejection, 60-kW auxiliaries and 0.06-L/min lubricant: **2.94 MW net / 29.4%**. A 24-L/min atmospheric refinery yields only about 308 standard refinery-gas L/min before its utility burning, versus 9436.5 needed here. A full turbine requires aggregated coproducts or substantial gas supply; fuel availability was not inflated.

Combined cycle uses that same fuel owner, its existing internal HRSG, **one industrial turbine**, one powered condenser and a closed feedwater/makeup loop. HRSG draws 5.5 MW exhaust, retaining 4.95 MW heat plus 9.163 kW paid feed work, with transfer/steam-loss residuals rejected once. Total auxiliaries including mean makeup are 84.396532 kW. **10 MW fuel = 3.910138214 MW net + 6.089861786 MW total rejection/dissipation**, including auxiliary bookkeeping at steady state. Stored steam/exhaust, utility/work fractions and prepared structure are explicit transient terms. No second fuel copy supplies the HRSG.

Combined storage: 16,000-count water gives 2.91025 h once-through or 582.05 h (24.25 days) makeup-only. The 160,000-count superhot store is 160,000 kg water-equivalent, about 29.25 h without export, not literal vessel volume. Old HOTSTEAM counts retain 0.1 kg/count. Exhaust holds only 150 MJ, 27.27 s full-load recoverable heat. Blockage pauses HRSG; the finite account then vents new exhaust while simple-cycle electricity can continue.

One matched 750-kWe diesel engine remains 38% gross, 3.30610 diesel L/min and about 20.17 h on the normalized 4,000-L store. Four produce 3 MW at the same efficiency and 13.22438 L/min, with more engines/pistons/lube/ports. Central steam trades efficiency/cooling/startup for abundant solid fuel and four main thermal machines. Simple gas uses one large machine, continuous gas logistics and long spool but no steam cooling when vented. Combined cycle uses three main thermal machines including the internal HRSG, with more cooling/startup and 39.10% net efficiency. Diesel/refinery utilities are separate paid inputs. Footprint, supported fuels, lubrication, cooling and coproduct availability determine choice; there is no unlock wall, forced fuel expiry or nuclear gate.

### Before/after, compatibility and validation

Old natural-gas turbine full throttle: about 63,000 nominal gas counts/min, roughly 268 kWe via the 536,000-q legacy trait/RPM term, and a separate potential boiling term near 72,799 water counts/min at maximum temperature. That boiling did not debit residual fuel energy or state enthalpy. New full load: about 16,984 standard L/min, 3 MWe gross, owned 5.5-MW exhaust and about 91.63 feed kg/min in the HRSG case. Old standard ordinary cap was 6000 steam counts/t -> about 51 kWe from tiny cooling traits; new is 0.75 kg/s -> 140.625 kWe with the real expansion cap. Industrial/Leviathan no longer derive output from 20%/all inlet stock or storage capacity. Comparisons are source/default arithmetic, not measured facilities.

Saved electrical quanta, fluid IDs, numeric steam/fuel counts, existing steam/fuel tanks, old cryogenic identities and unrelated data are retained. New thermal/exhaust/fuel/transfer accounts are versioned. Unowned boiler/compressor/oven progress is not paid work; oven stored heat retains its numbers but old unowned burn progress cannot credit new fuel. New boiler thermal buffers start uncertified/empty, not scaled from old GUI heat. Unknown/custom fluids remain drainable and are not certified steam/fuel. Incompatible filled output stock pauses, not relabels. Old staged trains need rerouting; PU is preserved separately. Old configured storage may remain smaller without reducing/increasing continuous nameplate. Standard maxSteamPerTick is only an additional downward inlet cap. Steam JSON cannot undercharge or skip pressure stages; old condenser per-count electricity settings are compatibility metadata, not authoritative bills.

Observed Java 8 offline compilation, build, JUnit, JAR packaging and reobfuscation succeeded. **62 tests passed**: 29 physical, 11 petroleum, 22 thermal. Production API/real tank/compressed-NBT checks cover mass/enthalpy and legal routes; gas split/assays and no duplicate exhaust; persistence/quality/finite plenum; nameplates/full-buffer/unpaid pause; steam loss/makeup and conserved cooling return; compressor work; fractional mass/work; repeat/backwards ticks and no unload catch-up; industrial checked arithmetic/overflow; legacy stock; full staged/direct equivalence; complete combined-cycle mass/energy closure; oven fractional transfer/shared firing; aggregate export and afterburning. These are not loaded-world controller tests.

Single-client startup: existing development tooling launched Minecraft 1.7.10; FML loaded six mods including hbm and created texture atlases. It logged 37 missing hbm textures outside the changed assets. No world or server connection was opened; no multiclient testing occurred. The startup client was intentionally stopped. Initialization is established, not placement/operation; final direct-expansion, port-migration and controller guards subsequently received build/static validation only.

User gameplay acceptance remains: new/saved boiler ports and rotation; solid hand/hopper feed, fuel selection and item recovery; oven intake/firing reduction, ash/pollution/waterlogging; turbine inlet/exhaust rerouting and aggregate cable/battery export; gas spool/throttle/lube/air/fuel switching; HRSG quality and blocked water/steam; condenser environment/return routing; rupture/trip/reset; actual UI; chunk unload/save/reload and sustained facility rates. Config/recipe reload and optional integrations were not exercised in a world. Nuclear outputs, coolant/HU calibration and reactor shutdown/relief/contamination/decay/trip/explosion code remain deferred compatibility paths.

### Changed file manifest

All 58 changed files belong to this tranche. Paths are repository-relative; generated build/runtime outputs are excluded.

**Thermal APIs and shared ownership**

- `src/main/java/com/hbm/realsim/Groundwater.java`
- `src/main/java/com/hbm/realsim/PhysicalRegistry.java`
- `src/main/java/com/hbm/realsim/PhysicalState.java`
- `src/main/java/com/hbm/realsim/ProcessMachine.java`
- `src/main/java/com/hbm/realsim/ExhaustAccount.java`
- `src/main/java/com/hbm/realsim/HeatRecovery.java`
- `src/main/java/com/hbm/realsim/HeatTransfer.java`
- `src/main/java/com/hbm/realsim/SteamLedger.java`
- `src/main/java/com/hbm/realsim/ThermalAccount.java`
- `src/main/java/com/hbm/realsim/ThermalInfo.java`
- `src/main/java/com/hbm/realsim/ThermalMachine.java`
- `src/main/java/com/hbm/realsim/TurbineFuelAccount.java`

**Energy and process-heat boundaries**

- `src/main/java/api/hbm/tile/IProcessHeatSource.java`
- `src/main/java/api/hbm/energymk2/IRatedGenerator.java`

**Native controllers and proxy**

- `src/main/java/com/hbm/tileentity/TileEntityProxyCombo.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityChungus.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityCondenser.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityCondenserPowered.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityFireboxBase.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerBase.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerIndustrial.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityHeaterElectric.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityHeaterHeatex.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOilburner.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityMachineHephaestus.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityMachineLargeTurbine.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbine.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbofan.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntityRadiator.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntitySolarBoiler.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntitySolarMirror.java`
- `src/main/java/com/hbm/tileentity/machine/TileEntitySteamEngine.java`
- `src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineGasFlare.java`

**Blocks, UI, recipes and fluid presentation**

- `src/main/java/com/hbm/blocks/machine/MachineChungus.java`
- `src/main/java/com/hbm/blocks/machine/MachineCondenser.java`
- `src/main/java/com/hbm/blocks/machine/MachineCondenserPowered.java`
- `src/main/java/com/hbm/blocks/machine/MachineHeatBoiler.java`
- `src/main/java/com/hbm/blocks/machine/MachineHeatBoilerIndustrial.java`
- `src/main/java/com/hbm/blocks/machine/MachineSteamEngine.java`
- `src/main/java/com/hbm/inventory/fluid/FluidInfo.java`
- `src/main/java/com/hbm/inventory/fluid/FluidType.java`
- `src/main/java/com/hbm/inventory/gui/GUIFirebox.java`
- `src/main/java/com/hbm/inventory/gui/GUIMachineTurbine.java`
- `src/main/java/com/hbm/inventory/gui/GUIMachineTurbineGas.java`
- `src/main/java/com/hbm/inventory/recipes/CompressorRecipes.java`

**Player text**

- `src/main/resources/assets/hbm/lang/en_US.lang`

**Automated accounting checks**

- `src/test/java/com/hbm/realsim/ThermalAccountingTest.java`

**Documentation**

- `docs/REALSIM_FLUID_INFRASTRUCTURE.md`
- `docs/REALSIM_PHASES_1_4.md`
- `docs/REALSIM_PHYSICAL_LEDGER.md`
- `docs/REALSIM_PROCESS_INVENTORY.md`
- `docs/REALSIM_SOURCE_CATALOGUE.md`
- `docs/REALSIM_SUBSTANCE_REGISTRY.md`
- `docs/steam-turbines.md`

**Release history**

- `CHANGELOG.md`
