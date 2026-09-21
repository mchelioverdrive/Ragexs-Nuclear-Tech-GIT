# Magnox heat removal and protection

The existing Magnox multiblock includes its graphite core, CO2 primary circuit, steam generator, shutdown controls and relief equipment. Supply **Carbon Dioxide** and **Light Water** to the existing ports. Graphite is the moderator; secondary feedwater has no reactivity or water-void effect. No additional cooling machine or external loop is required by this model.

Physical reference: [NKS-2, *Description of the Magnox Type of Gas Cooled Reactor*, sections 4 and 5.5–5.7](https://www.nks.org/scripts/getdocument.php?file=111010111119675). Section 4 describes negative temperature feedback for the natural-uranium reactor. Section 5.5 describes CO2 heat transport to steam generators. Sections 5.6–5.7 route residual and emergency heat through those generators, including low-speed circulation and natural circulation. These support continued heat transport after shutdown, not indefinite operation without a heat sink. All numerical limits, equipment capacities and protection logic below are **gameplay choices**, not historical plant specifications or engineering safety claims.

## Operation and explicit restart

Keep feedwater supplied, maintain sufficient CO2, and export steam. The reactor checks protection **before adding new fuel heat**, and again after heat transfer. Trips insert all control rods and latch the reactor off. Restoring fluids never restarts it automatically. The GUI shows the latched reason beneath the fuel slots; hover over the control for reserve information. Use the existing on/off control, `setActive(true)`, or a rod-insertion command below 100 to request a restart.

| Protection | Shutdown condition | Explicit restart requirement |
| --- | --- | --- |
| Feedwater | At or below calculated reserve | More than reserve + 2,000 mB |
| Primary CO2 | Below 11,200 mB | At least 12,600 mB |
| Core temperature | At least 450 C | Below 350 C |
| Primary pressure | At least 29 bar | Below 27 bar |
| Steam storage | Less than 55 mB free after export | At least 1,000 mB free |
| Existing core damage | Does not erase damage on shutdown | Both damage counters must be zero |

The first automatic trip reason remains visible. A rejected restart reports its current blocking condition. Manual SCRAM records `manual`; upgrading an old save records `migration`. Protection can trip a stopped reactor too. All start paths share the same checks; there is no bypass or random protection failure. Restarting with some steam space cannot prove that downstream pipes work; continued blockage trips it again before storage overflows.

## Thermal accounting

Energy is measured in local gameplay heat units (HU), not joules or the global boiler energy unit. The implementation retains the existing fuel identities, metadata, base heat, lifetimes, flux layout, fuel multipliers, breeding and depleted outputs.

| State | Meaning |
| --- | --- |
| `coreEnergy` | Stored thermal energy above 20 C; capacity 100000/780 HU per C. Lumps fuel and graphite together. |
| `primaryEnergy` | Stored thermal energy above 20 C in primary gas and steam-generator metal; capacity one quarter of core capacity. |
| `graphiteHeat` | Legacy core-temperature display: 0–100000 maps to 20–800 C. Numerically equals core HU because of the chosen capacity, but is not another store. |
| `heat` | Legacy primary-temperature display on that same scale; not another store or an additional outlet heat inventory. |
| `decayEnergy` | Remaining unreleased fission-product energy, separate from thermal energy. |
| `decayHeat` | Decay heating rate in HU/t, independent of inserted control rods. |
| `activePower` | Total recoverable fuel heat generated this tick, before splitting prompt and delayed heat. |
| `co2Cooling` | Signed core-to-primary heat transport in HU/t; internal transport is not an environmental heat sink. |

Each fuel tick puts 94.5% of power into the core and 5.5% into `decayEnergy`. Each tick releases 0.0005 of the resulting decay inventory into the core. The old code instead credited full fuel heat and then added decay heat, including an immediate full operating decay rate after a single tick. The new recurrence conserves total generated energy: prompt heating + decay-inventory increase + released decay heating equals fuel power. At 3,000 HU/t, one operating tick leaves 164.9175 HU of unreleased energy, compared with 329,835 HU after long operation. The decay half-life is about 1,386 loaded ticks; this single exponential is a gameplay approximation.

CO2 transfers equal and opposite amounts between the thermal stores, limited by available energy, thermal equilibrium, and circulation capacity. Maximum transfer is 4,400 HU/t running or 1,200 HU/t stopped, multiplied by CO2 inventory / 14,000 mB, capped at one. A small remaining gas inventory cannot provide full transport. The stopped capacity represents internal low-speed/natural circulation and still needs secondary heat removal. Ambient loss is only max(1, coreEnergy/25000) and max(0.25, primaryEnergy/15000) HU/t, bounded by available energy.

Natural uranium uses a smooth bounded feedback factor: with `x = max(0, coreC - 300)/250`, power-producing flux is multiplied by `0.5 + 0.5/(1+x*x)`. It also affects fuel burn through the existing flux calculation. Exotic fuels retain a factor of one because the selected report does not establish their coefficients. Feedback is independent of protective shutdown. Exotic damage multipliers remain; their heat-related damage begins above the shared 500 C cladding threshold, above the shutdown limit.

## Steam and finite shutdown cooling

Normal production ramps from zero at 300 C to at most 55 mB/t at 450 C. One mB of Light Water makes one mB of Super Dense Steam (`SUPERHOTSTEAM`), preserving the existing reactor conversion and the water-to-superhot-steam volume ratio. Each mB exports exactly 80 HU from the primary store. Water, heat and available steam space limit production. Full steam storage stops useful production.

Stopped reactors additionally use an **internal secondary steam relief path**, bounded to 60 mB water/t and 960 HU/t. Above the 100 C low-pressure boiling floor this approximates venting ordinary steam: one water mB corresponds to 100 mB of ordinary steam and removes 16 HU, consistent with the ordinary/superhot steam heat ratio of 100/500 in the fluid definitions. Vented steam is not added to the output tank and earns no useful steam or electricity. The internal steam generator continues to accept Light Water; this does not change its global fluid traits or other machines' recipes.

This bounded low-pressure boiling approximation continues through the 100–300 C range even when useful superhot-steam generation has stopped. It removes only heat above the 100 C floor and consumes real tank water. Useful production and relief are separately accounted; shutdown can use both, and a full output tank does not block relief. Below 100 C (and for sub-mB residual boiling energy), only ambient heat loss remains. Remaining decay heat can maintain boiling near 100 C until it declines; it is never erased. When water is empty, all water-dependent heat removal stops. There is no hidden supply. Primary stored heat can still boil water after CO2 is lost, but the core no longer has that transport path.
## Supported feedwater-interruption range

Reserve sizing supports a healthy reactor generating **at most 3,000 HU/t**, with both thermal stores at **450 C or below**, **11,200–14,000 mB CO2 retained**, no accumulated damage, and feedwater maintained above the reserve before interruption. This includes a full natural-uranium core; stronger fuel layouts may require rod insertion and are not automatically guaranteed. Simultaneous gas loss, external draining/destruction, damaged reactors, hotter legacy saves and higher-power histories fall outside the guarantee.

The reserve in mB is:

`ceil(1.1 * (coreEnergy + primaryEnergy + max(decayEnergy, 330000) + 3000) / 16) + 256`

It budgets **all stored heat and all remaining expected decay energy**, using the less efficient shutdown boiling rather than useful steam. The 330,000 HU floor covers long operation at the supported power; larger actual decay inventories increase the requirement rather than being silently capped. One full-power tick, 10% margin and 256 mB cover the discrete trip interval and fluid quantization. Required reserve is 23,150 mB cold and at most 27,888 mB with both stores at 450 C; even the additional restart margin fits the unchanged 32,000 mB tank.

At the minimum supported gas inventory, shutdown transport capacity is 960 HU/t and high-temperature boiling capacity is also 960 HU/t, versus at most 165 HU/t initial decay heating. Stored heat can therefore be removed while decay heating declines, and the energy budget leaves sufficient actual water. These are analytical bounds, not an in-game validation result. Ordinary interruption triggers shutdown while substantial feedwater remains. Loss of both heat-removal paths can leave hundreds of thousands of HU to heat the core beyond damage limits; shutdown does not erase that danger.

## Pressure, damage and contamination

Primary pressure is `26 * (CO2 mB / 14000) * (primaryC + 273.15)/(410 + 273.15)` bar. Nominal inventory is consistently 14,000 mB, not the tank's 16,000 mB capacity. Overfilling can cause an earlier pressure trip. The legacy gauge scale still maps 100000 to 30 bar.

Normal reference pressure is 26 bar, trip pressure 29, automatic primary relief 30, and rupture 34. Primary relief vents at most 100 mB/t; the manual vent remains 1,000 mB per command. Removed gas carries its modeled enthalpy away (a quarter of primary energy belongs to gas at nominal inventory, scaled down with inventory), reduces pressure, and weakens core heat transport. Secondary steam relief never removes primary gas. Rupture is checked before the finite relief valve can reduce an already excessive pressure.

Cladding damage accumulates above 500 C, accelerated fuel damage above 600 C, and graphite structural damage above 600 C. Reaching either damage limit or a core temperature of 800 C produces the existing destroyed-reactor structure **without a blast or launched debris**. Only a pressure rupture launches the existing debris and invokes an explosion. A destroyed reactor terminates the live tile update immediately.

Contamination scales with occupied fuel channels, modeled cladding/fuel damage, and barrier failure: thermal wreck exposure uses 40% of the fuel-damage fraction; rupture uses 15% plus 85% of that fraction. This scales waste radius, wreck radiation, gas emission probability and radioactive debris count. Mechanical debris belongs only to rupture. Wrecks do not ignite by default. Neither a low CO2 level nor a hot secondary tank proves ingress; the old inferred air/water ingress and graphite-fire failure classes have been removed. The `airIngress` automation position/key remains zero for compatibility.

## Saves and automation

`thermalVersion=2` explicitly migrates old temperatures to the two energy stores. Legacy `decayHeat` is converted to remaining inventory by dividing by 0.0005, preserving its heating rate conservatively when operating history is unavailable. Old reactors are latched off for an explicit checked restart; heat and damage survive. Existing `heat`, `graphiteHeat`, `pressure`, controls, damage, tank and decay keys remain. All new energy, shutdown and discharge fields are saved and synchronized through the same NBT packet. Energy and decay pause while chunks are unloaded; reload does not clear them.

The existing `FluidTank.migrateFrom(Fluids.WATER)` is unchanged: only the reactor's saved water tank becomes Light Water while retaining its amount. Ports and item loading accept Light Water; ordinary water containers and outside tanks are not rewritten.

All existing OpenComputers method names and return types remain. `getInfo()` retains its first 14 positions and appends latch, reason, reserve, shutdown boiling water mB/t and primary gas vented mB/t. New `getShutdownStatus()` returns `(latched, reason, restartBlocker, reserve)`. Reason identifiers are `none`, `manual`, `migration`, `water`, `co2`, `temperature`, `pressure`, `steam`, and `damage`. Control callbacks are synchronized rather than direct. GUI packets, rod commands and OpenComputers activation use the same server-side gate.

Energy Control receives standard active/core-temperature fields and a standard tank-text status, plus `shutdownLatched`, `shutdownReason`, `restartBlocker`, `waterReserve`, `shutdownWaterUsed`, and `primaryGasVented` extra data. Its consumption value includes useful-steam water plus shutdown discharge; output includes useful steam only. Custom-key display depends on the installed Energy Control version.

## Original failure path and remaining limits

The inspected June Magnox change (`85c752f7b`, following `530bf1cc1`) introduced a lagging outlet display that was also cooled as though it were a separate store, instant decay inventory, inferred ingress, and unconditional explosions for overheating and damage. The September water-tier change (`895f15de9`) migrated feedwater to Light Water. That migration and the existing multiblock, fuel tables and connections remain intact; the corrected behavior separates shutdown, heat transport, thermal damage and pressure rupture.

This is a two-store gameplay model, not a validated reactor simulator. It has no resolved fuel channels, gas flow dynamics, real steam pressure, water outlet temperature, oxidant ingress, exchanger leaks, variable gas heat capacity, xenon or multi-isotope decay model. Relief equipment has fixed capacity and no random failures. Wreck radiation and environmental contamination retain coarse existing game mechanics and do not model isotope depletion; terminal wrecks do not continue the operating tile's thermal simulation. Fuel removed through inventory automation does not take away the reactor's accumulated decay inventory, a conservative simplification. There is no claim that every exotic fuel has real Magnox feedback or that a gas-cooled reactor can run indefinitely without heat removal.
