# Steam turbine safety

A steam turbine must remain connected to an electrical load while it is processing steam. If its internal power buffer is full and it continues to receive steam without exporting electricity, the rotor enters an overspeed condition. The block look overlay displays `OVERSPEEDING - EXPORT POWER NOW!` during the one-second protection interval; reconnect a power consumer, cable network, or chargeable item before the turbine fails.

After 20 consecutive ticks of overspeed, the standard Steam Turbine explodes. The condition clears as soon as the turbine exports stored power or stops generating from steam. Design the electrical grid so that a disconnected load cannot leave an active turbine with nowhere to send power.

## Overpressure protection

Both the standard Steam Turbine and the Industrial Steam Turbine rupture in a destructive explosion when their steam inlet and spent-steam exhaust tanks are completely full. Keep both paths clear: connect the inlet to a controlled steam source and route the exhaust to a condenser, cooling tower, or enough storage to prevent a blocked system from reaching full capacity.

## Leviathan trip valves

The Leviathan Steam Turbine (the `chungus` turbine) uses protective trip valves rather than continuing into a destructive failure. It automatically shuts down and closes steam admission when either its inlet and exhaust buffers are both full (overpressure) or its electrical buffer is full while steam remains in the inlet (overspeed). Its block-look overlay reports the dangerous trip event and confirms that the turbine auto-shut down.

Trip valves remain closed until reset. Use the turbine's built-in lever to reset a trip, or apply a redstone signal to the turbine controller for a remote reset. Ensure the blocked steam or power path is corrected before resetting it, or the turbine will trip again.
