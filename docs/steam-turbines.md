# Steam turbine safety

For the Magnox steam source, see [Magnox heat removal and protection](MAGNOX.md). Blocked steam storage trips that reactor; its internal shutdown relief consumes feedwater without supplying useful steam to a turbine.

A steam turbine must remain connected to an electrical load while it is processing steam. The Standard and Industrial Steam Turbines stop consuming steam and stop producing power whenever their internal power buffer has no room for another generation operation. They resume automatically after power is exported to a connected consumer, cable network, battery, or chargeable item.

Design the electrical grid so a Standard or Industrial turbine can regularly export its stored power. Their full power buffers safely pause generation; they do not consume steam or discard generated energy while waiting for a load. The Leviathan instead closes its trip valves when its full power buffer indicates an overspeed condition.

## Overpressure protection

Both the standard Steam Turbine and the Industrial Steam Turbine rupture in a destructive explosion when their steam inlet and spent-steam exhaust tanks are completely full. Keep both paths clear: connect the inlet to a controlled steam source and route the exhaust to a condenser, cooling tower, or enough storage to prevent a blocked system from reaching full capacity.

## Leviathan trip valves

The Leviathan Steam Turbine (the `chungus` turbine) uses protective trip valves rather than continuing into a destructive failure. It automatically shuts down and closes steam admission when its inlet and exhaust buffers are both full (overpressure), or when its internal electrical buffer is full while steam remains in the inlet (overspeed). Keep an electrical consumer connected so generated power is exported before the buffer fills.

Trip valves remain closed until reset. Use the turbine's built-in lever to reset a trip, or apply a redstone signal to the turbine controller for a remote reset. The turbine exports stored power before it evaluates the reset, so reconnecting an electrical load allows an overspeed trip to clear. Ensure the blocked steam or electrical path is corrected before resetting it, or the turbine will trip again. The turbine's look overlay reports synchronized inlet and exhaust amounts and whether its trip valves are `READY` or `TRIPPED`, including on the client.


## Phase 5D operation and ratings

Converted power uses [one water/state ledger](REALSIM_PHYSICAL_LEDGER.md#phase-5d-frozen-thermal-contract-2026-10-13). Standard, Industrial and Leviathan now expand selected steam **directly to wet SPENTSTEAM**. Previous staged installations must reroute exhaust to condensers. Filled intermediate stocks are retained and must be drained before outlet identity changes.

| Machine | Electrical ceiling | Water-mass flow ceiling | Fraction of credited expansion work |
| --- | ---: | ---: | ---: |
| Small engine, ordinary only | 25 kWe | 0.3 kg/s | 70% |
| Standard | 500 kWe | 0.75 kg/s | 75% |
| Industrial | 5 MWe | 8 kg/s | 85% |
| Leviathan | 25 MWe | 40 kg/s | 88% |

Actual power also depends on state work, supplied mass and output space; these factors are not heat-to-electric efficiency. Counts carry 0.01/0.1/1/10/1 kg for ordinary/hot/superhot/ultrahot/spent. Saves and PU are unchanged. All four retain electricity and pause steam conversion at full storage; aggregate cable/port/battery export has an independent nameplate. Rupture and trip/reset behavior above remains active.

Ordinary boilers accept up to 200 kWth qualified heat and make ordinary steam. Industrial boilers accept 12 MWth, make hot steam, and require paid electrical feed pumping through existing ports. Hand/hopper feed solids or select an empty heavy/heating-oil inlet with a fluid identifier; filled stock is not overwritten. The heating oven supplies 500 kWth combined firing/intake, with 4 MJ storage and default 50% qualified lower-heat transfer; imported heat reduces fuel firing. An ordinary boiler still limits an oven to a 200-kW steam loop. Use an industrial boiler with its fuel supply empty for an oven-fed hot-steam loop.

Steam compression is a compressor + aftercooler charging 735.61/599.73/739.50 kJ/kg for adjacent grades, with fractional ownership; it cannot skip grades or use a tiny fee. The general name remains because process gases use the same block. A future steam-mode label of "Compressor / Aftercooler" is recommended.

Passive condensers reject 250 kWth, radiators 1 MWth, powered condensers 20 MWth including 10 kJ/kg auxiliaries. Return cold fresh water to the boiler. Passive sink requires adequate atmosphere; radiators also support vacuum; powered air cooling requires atmosphere. Adverse hot environments pause condensation. These finite dry/environmental sinks conserve condensate; they do not evaporate all feedwater or charge a token electric fee instead of latent rejection. Makeup replaces the generator's 0.5% steam loss; groundwater stays 120/30 L/min.

The gas turbine's existing water/steam ports form a 6-MWth internal HRSG with owned exhaust, 90% transfer and 30 C approach. It makes only hot/superhot states permitted by stored quality, never ultrahot. Combine its 3-MWe prime mover with one industrial turbine, one powered condenser, feed pumping and closed return for the [39.101382% reference combined cycle](REALSIM_PHASES_1_4.md#phase-5d-implementation--closed-fossil-thermal-power-2026-10-13). Blockage pauses recovery; new excess exhaust is vented after finite storage fills.

Nuclear heat/HU/coolant calibration remains deferred and protection paths remain intact. Steam at a converted turbine boundary does not certify a reactor's uncalibrated thermal output.
