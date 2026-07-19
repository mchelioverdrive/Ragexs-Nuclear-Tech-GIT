# Steam turbine safety

A steam turbine must remain connected to an electrical load while it is processing steam. If its internal power buffer is full and it continues to receive steam without exporting electricity, the rotor enters an overspeed condition. The block look overlay displays `OVERSPEEDING - EXPORT POWER NOW!` during the one-second protection interval; reconnect a power consumer, cable network, or chargeable item before the turbine fails.

After 20 consecutive ticks of overspeed, the standard Steam Turbine explodes. The condition clears as soon as the turbine exports stored power or stops generating from steam. Design the electrical grid so that a disconnected load cannot leave an active turbine with nowhere to send power.
