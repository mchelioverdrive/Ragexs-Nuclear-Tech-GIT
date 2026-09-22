# Magnox thermal and protection model

## Historical design basis

Magnox is modeled as a graphite-moderated, carbon-dioxide-cooled reactor whose primary gas transfers heat to water-fed steam generators. Water is not a core moderator, and this model therefore has no water-void reactivity term.

The principal design source is the [NKS description of the Magnox reactor](https://www.nks.org/scripts/getdocument.php?file=111010111119675). It describes negative temperature feedback in the natural-uranium core, predicts a temporary 125 C fuel-temperature rise after coincident loss of circulators and trip circuits, and describes decay-heat removal through the main steam generators by low-speed and natural CO2 circulation.

The protection and material-limit source is IAEA [GC(07)/INF/62](https://www.iaea.org/sites/default/files/gc/gc07inf-62_en.pdf). It describes triplicated fail-to-safety protection, rapid coolant-pressure loss and high fuel-cladding surface temperature trip inputs, roughly five seconds of shutdown-rod travel, an assumed 620 C Magnox cladding ignition limit, and analyses of blower loss, rod withdrawal, a single-channel fire, and combined pressure and blower loss.

Those facts identify the plant and motivate the model. The numerical heat capacities, transfer rates, thresholds, and damage rates below are gameplay balance values, not historical engineering claims.

## Automatic protection and startup interlocks

Enabled automatic protection has three operating trip inputs:

* peak fuel-channel cladding temperature at or above **560 C**;
* primary pressure at or above **29 bar**; and
* primary pressure falling by at least **1.5 bar in one tick**.

A SCRAM latches its first cause, targets 100% rod insertion, and moves the actual rods one percentage point per tick. Full travel from zero therefore takes 100 ticks, approximately five seconds. Fission follows the actual rod position during travel and stops only at full insertion. Rod insertion and manual SCRAM do not require external power.

Feedwater, CO2 inventory, output space, temperature, pressure, and existing damage are checked separately when starting. Startup requires at least 12,000 mB feedwater, 90% of nominal CO2 inventory, at least 1,000 mB free steam space, core temperature below 350 C, primary pressure below 27 bar, and no accumulated damage. Feedwater quantity and steam-tank fullness are **not** automatic operating trip inputs. During operation they are warnings. A full steam tank is blocked output, not a boiler-pressure measurement.

## Thermal stores and heat accounting

`thermalVersion=3` uses independent energy stores above 20 C:

| State | Gameplay meaning |
| --- | --- |
| `coreEnergy` | Fuel and graphite store, **2,500 HU/C**. |
| `primaryEnergy` | CO2 circuit and steam-generator metal store, **600 HU/C**. |
| `graphiteHeat` | Legacy 0–100000 core-temperature display (20–800 C), calculated from `coreEnergy`. |
| `heat` | Legacy 0–100000 primary-temperature display, calculated from `primaryEnergy`. |
| `decayEnergy` | Unreleased fission-product energy. |
| `decayHeat` | Heat released from that inventory this tick. |

The larger capacities remove the prior numerical identity between core energy and the legacy gauge. A protected hot reactor cannot lose hundreds of degrees through that display conversion: at the maximum 400 HU/t shutdown transfer rate, cooling the core alone from 400 C to 100 C requires at least 1,875 ticks, before decay heat and transfer equilibrium are considered. Neither store is clamped to 100 C and changing operating state never deletes stored heat.

Fuel power assigns 5.5% to the delayed inventory and 94.5% directly to the core. Each tick releases 0.0005 of the delayed inventory. CO2 exchange is equal and opposite between the stores and is proportional to actual inventory; at zero CO2 it is exactly zero. Ambient losses are explicit and bounded. Venting removes CO2 and its modeled gas enthalpy. Every other removed HU is associated with useful steam, shutdown boiling and its water discharge, or ambient loss.

Version 2 saves migrate by reconstructing the saved core and primary temperatures on the new capacity scale. Their old energy numbers are never reinterpreted as version 3 HU, and `decayEnergy` is preserved.

## Steam generation and shutdown cooling

Useful generation ramps between 300 C and 450 C, up to 55 mB/t. Each mB of Light Water produces one mB of Super Dense Steam and removes 80 HU from the primary store. Water, heat, and free output capacity all limit the cycle. When storage fills, production and useful heat removal stop; unmatched heat stays in the reactor.

After shutdown, low-speed/natural CO2 circulation continues moving as much as 400 HU/t from core to primary, scaled by actual CO2 inventory. The water-dependent shutdown path consumes at most 10 mB/t and removes 16 HU per mB above its 100 C boiling floor. It creates no useful output credit. Zero feedwater stops it completely. Decay heat can consequently produce a post-SCRAM plateau or rise whenever release exceeds heat removal.

Primary pressure is `26 * (CO2 mB / 14000) * (primaryC + 273.15)/(410 + 273.15)` bar. Relief begins at 30 bar and vents at most **24 mB/t**. This controls ordinary natural-uranium transients but is finite, so an extreme exotic-fuel heat-up can cross the 34 bar rupture threshold faster than relief can recover it.

## Fuel channels, feedback, and failures

Each of the 24 existing fuel slots has a persistent cladding temperature. Its target uses local fuel power, neighboring-channel power, flux shape, actual rod insertion, bulk core temperature, and CO2 cooling. The model reports the hottest channel independently of graphite temperature and uses that peak for protection and cladding damage. Weak or absent CO2 cooling raises the local fuel-to-cladding temperature difference. Damage begins above 500 C and can progress to failure during sustained exposure near the IAEA's 620 C Magnox limit.

Natural uranium retains strong negative temperature feedback. Its reactivity decreases smoothly as the graphite heats, and normal circulation keeps its channels inside the operating envelope. Loss of circulation therefore reduces its power rather than creating a prompt runaway; enabled peak-temperature and pressure protection then inserts the rods over five seconds. One ordinary mistake is not tuned to guarantee destruction of a healthy natural-uranium core.

Exotic fuels do not receive an invented historical Magnox feedback coefficient. Their existing higher heat and instability multipliers remain, and a full ZFB MOX loading exceeds normal steam-generator capacity. Consequently blocked steam output plus lost feedwater can cause severe cladding damage, while CO2 loss during high-power operation can create a severe local temperature excursion during rod travel.

There are three terminal paths:

* accumulated peak-cladding damage produces a contaminated, nonexplosive wreck;
* bulk core temperature reaching 800 C (or supported structural graphite damage reaching its limit) produces a contaminated, nonexplosive wreck; and
* primary pressure reaching 34 bar mechanically ruptures the vessel, producing the only explosion and launched debris path.

No thermal failure is a nuclear detonation. Low CO2 inventory is not evidence of air ingress. Intact CO2 does not oxidize graphite, and the model does not invent a graphite fire or random channel blockage.

## Compatibility and automation

Existing tank, temperature, pressure, control, damage, and decay NBT keys remain. Target rod position, all 24 channel temperatures, the pressure history used for loss detection, and protection status are saved or synchronized in tile packets. Existing OpenComputers method names and return types remain, and the first 14 `getInfo()` values are unchanged. Energy Control integration remains on the existing fields. The GUI keeps its main gauge on `graphiteHeat` and separately displays core, primary, and peak-cladding temperature, actual/target rod position, SCRAM progress, decay heat, both damage counters, operating warnings, and the active trip input.
