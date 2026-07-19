# Steam turbine safety

A steam turbine must remain connected to an electrical load while it is processing steam. The Standard, Industrial, and Leviathan Steam Turbines stop consuming steam and stop producing power whenever their internal power buffer has no room for another generation operation. They resume automatically after power is exported to a connected consumer, cable network, battery, or chargeable item.

Design the electrical grid so the turbine can regularly export its stored power. A full power buffer safely pauses the turbine; it does not consume steam or discard generated energy while waiting for a load.

## Overpressure protection

Both the standard Steam Turbine and the Industrial Steam Turbine rupture in a destructive explosion when their steam inlet and spent-steam exhaust tanks are completely full. Keep both paths clear: connect the inlet to a controlled steam source and route the exhaust to a condenser, cooling tower, or enough storage to prevent a blocked system from reaching full capacity.

## Leviathan trip valves

The Leviathan Steam Turbine (the `chungus` turbine) uses protective trip valves rather than continuing into a destructive failure. It automatically shuts down and closes steam admission when its inlet and exhaust buffers are both full (overpressure). A full electrical buffer simply pauses generation, like the Standard and Industrial turbines; it does not trip the turbine.

Trip valves remain closed until reset. Use the turbine's built-in lever to reset a trip, or apply a redstone signal to the turbine controller for a remote reset. Ensure the blocked steam path is corrected before resetting it, or the turbine will trip again.
