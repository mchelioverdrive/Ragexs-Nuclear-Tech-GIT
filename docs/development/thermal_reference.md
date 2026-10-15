# Heat and steam reference

Current discrete state/transition contract. [SteamLedger](../../src/main/java/com/hbm/realsim/SteamLedger.java) owns state mass, enthalpy and conversion work. Qualified heat and electricity use 0.5-J quanta. Reactor HU and temperature are separate models; a turbine's SI conversion does not calibrate its upstream reactor.

## States and legal transitions

| State / identity | Nominal C | Pressure absolute | kg per unchanged inventory count | h above cold feed kJ/kg | Sensible / latent kJ/kg |
| --- | ---: | --- | ---: | ---: | --- |
| Cold / FRESH_WATER; WATER mass alias | 20 | 0.10142 MPa | 1 | 0 | 0 / 0 |
| Warm / WARM_CONDENSATE, internal ledger state | 46 | 0.010 MPa | 1 | 107.90 | 107.90 / 0 |
| STEAM | 100 | 0.10142 MPa | 0.01 | 2591.69 | 335.26 / 2256.43 |
| HOTSTEAM | 300 | 1 MPa | 0.1 | 2967.69 | 953.09 / 2014.60 |
| SUPERHOTSTEAM | 450 | 4 MPa | 1 | 3247.29 | 1533.99 / 1713.30 |
| ULTRAHOTSTEAM | 600 | 16 MPa | 10 | 3490.49 | 2559.39 / 931.10 |
| SPENTSTEAM, wet exhaust | 46 | 0.010 MPa | 1 | 2316.09 | 107.90 / 2208.19 |

Enthalpy/temperature anchors are the values encoded by `SteamLedger`; higher-grade latent values use the [NIST saturation-pressure table](https://nvlpubs.nist.gov/nistpubs/Legacy/IR/nistir5078.pdf), printed pp. 13 and 15. Nominal 46 C rounds the 45.806 C class; wet exhaust retains the adopted roughly 0.923 quality. Sensible includes superheating. These are discrete engineering classes, not an equation-of-state solver, literal gas vessel volumes or PU readings. Nominal 1 kg per feedwater-equivalent count preserves the 100:10:1:0.1 ratios. Warm condensate is an internal state, not a new fluid ID; native condensers return cold FRESH_WATER and also reject the final 107.90 kJ/kg.

| Legal transition | Heat input kJ/kg | Shaft/electric input kJ/kg | Maximum credited work kJ/kg | Residual |
| --- | ---: | ---: | ---: | --- |
| Cold -> ordinary | 2591.69 / transfer efficiency | 0 | 0 | Transfer and declared steam loss |
| Cold -> hot / superhot / ultrahot | 2966.19 / 3241.29 / 3466.49, divided by transfer efficiency | 1.50 / 6.00 / 24.00 | 0 | Pump work is retained in state h; source temperature must suffice |
| Warm -> legal steam | Subtract 107.90 from the corresponding cold heat numerator | Same pump | 0 | Only an explicitly warm inlet retains this credit |
| Ordinary -> hot compressor/aftercooler | 0 | 735.61 | 0 | Reject 359.61 |
| Hot -> superhot compressor/aftercooler | 0 | 599.73 | 0 | Reject 320.13 |
| Superhot -> ultrahot compressor/aftercooler | 0 | 739.50 | 0 | Reject 496.30 |
| Ultra -> super / super -> hot / hot -> ordinary / ordinary -> spent | 0 | 0 | 200 / 220 / 300 / 250 | Delta h minus electricity rejected once |
| Ordinary / hot / super / ultra -> spent, direct condensing rows | 0 | 0 | 250 / 550 / 770 / 970 | Same conservative total as full staged expansion, no intermediate steam output |
| Spent -> warm / warm -> cold | 0 | Passive environmental/gravity return or powered auxiliaries | 0 | Reject 2208.19 / 107.90 |
| Spent -> cold, passive / powered | 0 | 0 / 10.00 | 0 | Reject 2316.09 / 2326.09 including powered auxiliaries |
| Steam -> next lower grade exchanger; spent -> cold | 0 | 0 | 0 | Delta h enters one recovered heat account at outlet-temperature-minus-30 C quality |

Native standard/industrial/Leviathan turbines perform direct expansion to SPENTSTEAM; staged API rows remain available for genuinely staged adapters. A spent outlet cannot earn intermediate work again. Old staged installations must reroute turbine exhaust to condensers.

Ordinary/industrial boiler transfer is 98%, solar/geothermal 95%, HRSG 90%. BOILER transitions reject 0.5% processed mass as a declared steam-loss/blowdown equivalent, including its outlet enthalpy. Condensers conserve returned feedwater; makeup replaces this separate loss. Utility heat/work is paid on all reserved feed before delivery.

Gas turbine: 3 MWe gross, 30% LHV electricity, 55% recoverable exhaust and 15% initial rejection: **10 MW = 3 + 5.5 + 1.5 MW**. Own auxiliaries consume 2% gross (60 kW). This is an adopted modest industrial rating informed by [EPA combustion turbine characteristics](https://www.epa.gov/sites/default/files/2015-07/documents/catalog_of_chp_technologies_section_3._technology_characterization_-_combustion_turbines.pdf), not measured RTM performance. The roughly 10-by-10-by-3 machine can contain a low-MW prime mover/internal HRSG and replace about four industrial engines without selecting the upper 35% efficiency envelope.

`TurbineFuelAccount` owns removed chemical fuel; `ExhaustAccount` owns split heat at immutable 350/500/700 C classes, capped at 150 MJ total. Excess is vented/rejected. `HeatRecovery` draws this account at 6 MWth, 90% transfer and 30 C approach. 350 C allows hot steam, 500 C superhot; neither allows ultrahot. Fuel/throttle changes and save/load never upgrade old exhaust or recreate it from fuel.



## Equipment and quality

| Equipment | Current ceiling / condition |
| --- | --- |
| Firebox / oil burner / electric heater | 200 kWth source; combustion conversion 85%; source temperature 900°C |
| Heating Oven | 500 kWth combined firing/import; 4 MJ heat storage; imported heat transfer 50% by default displaces firing |
| Ordinary / Industrial Boiler | 200 kWth / 12 MWth intake; ordinary / hot output; 98% transfer; pressure feed work from the transition table |
| Solar / geothermal boiler | 95% transfer; environmental supply and native source restrictions |
| Gas Turbine HRSG | 6 MWth exhaust withdrawal, 90% transfer, 30°C approach; hot or superhot output |
| Passive condenser / radiator | 250 kWth / 1 MWth rejection; passive atmosphere / vacuum-capable radiator |
| Powered condenser | 20 MWth rejection including 10 kJ/kg auxiliaries; requires atmosphere |
| Heat exchanger | Grade reduction or spent-to-cold recovery; recovered heat quality is outlet temperature minus 30°C; coolant recovery has its own <=2-MW boundary |

Native Steam Engine, Small, Industrial and Leviathan converters discharge directly to spent steam. Direct work is credited once; intermediate stage rows support genuinely staged adapters and cannot also be credited on a direct outlet. Converter grade restrictions, flow and buffers are in [generator reference](generator_reference.md).

`ThermalAccount` persists admitted mass, heat, work and rejection. `ThermalMachine` pays through owning-thread native heat/electrical ports; `HeatTransfer` shares the per-tick source allowance. `TurbineFuelAccount` partitions combustion once; `ExhaustAccount` retains exhaust quantity and temperature quality; `HeatRecovery` consumes that finite exhaust. Reload, throttle changes and blocked outputs cannot recreate source energy. Condensers return cold fresh water and reject the entire residual enthalpy. Makeup replaces boiler blowdown, not all circulating water.
