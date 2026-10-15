# Getting started

RTM is an industrial and nuclear mod for Forge 1.7.10. Build a small reliable workshop, then connect materials, chemistry, fuel and power before scaling into reactors or space technology. NEI and placed-machine information show the recipes and settings active on your server.

## First workshop

1. Establish mining, ordinary furnace recovery and primitive metalworking.
2. Produce first steel in the Metallurgical Furnace, then prepare plates, pipes and machine parts.
3. Choose external-heat Stirling or early steam generation. Plan load before switching on every machine.
4. Add pressing, a Fabrication Machine, electronics assembly and a Chemical Reactor.
5. Treat water and provide every byproduct outlet. Add bulk steel, petroleum and specialists as needed.

Starter/Heavy Stirling supply up to 10/40 kW. Electric Furnace/Press/Fabrication base draws are 8/2/3 kW, so a single starter needs staggered jobs. Larger factories need sustained fuel, external heat where specified, cooling and storage with enough discharge power.

## Choose a system guide

- [Power generation](power_generation.md): heat engines, steam, piston/gas plants and specialists.
- [Steam and heat](steam_and_heat.md): grades, turbines, boilers, condensation and protection.
- [Chemistry](chemistry.md): water, gases, acids, separation and chemical manufacturing.
- [Petroleum](petroleum.md): crude to fuels, lubricant and recovered gas.
- [Materials](materials.md): structural metals, alloys, casting, conductors and specialists.
- [Manufacturing](manufacturing.md): forming, welding, soldering and assembly.
- [Nuclear industry](nuclear_industry.md): fuel, reactors, conversion and waste.
- [Construction recipes](../construction-recipes.md): workshop structures and furniture.

## Radiation basics

Radiation is a persistent environmental and item hazard. Practical rules:

- Keep radioactive materials contained and away from living areas.
- Use protective equipment before working with fuel, waste, fallout, or contaminated zones.
- Do not store radioactive materials in common player inventory unless you know the shielding behavior.
- Server administrators can clear or set chunk radiation with `/ntmrad`, but players should assume radiation is persistent unless cleaned up.

## Grenade use

Grenades are single-item stacks. Hold the use button to draw back a throw—the same pullback animation used by bows appears while charging—then release it. Throw power follows the standard bow draw curve, so a longer hold produces a faster, farther throw up to the normal grenade throw speed. Very brief taps do not release a grenade.

## Nuclear safety basics

Nuclear systems are powerful but punishing:

- Test reactor designs in a creative copy before using them on a server.
- Keep backups before first startup of a new reactor design.
- Monitor heat, coolant, fuel, waste, and output products.
- Put reactors away from bases and spawn until the design is proven.
- Treat meltdown, blast, fallout, and biome damage as real world-changing risks.


## Space basics

The mod includes celestial bodies, orbit logic, satellites, and orbital stations. Practical advice:

- Confirm the server's configured dimension IDs before adding other dimension mods.
- Use station/satellite commands only if you are an operator or the server rules allow it.
- Keep backup travel items or admin support available when testing station teleportation.
- Read the dedicated [Space Travel Guide](../space-travel.md) before committing rare rocket hardware to a launch.
- Remember that atmosphere and water-table behavior can differ by body.

## Multiplayer etiquette

Because RTM can damage worlds, ask before:

- Detonating explosives or nukes.
- Starting an untested reactor.
- Launching missiles.
- Creating large pollution/radiation sources.
- Editing recipes, item pools, machine configs, or dimension IDs.

## Solid-fuel missiles

ABM and micro missiles are no longer pre-fueled. Load **Rocket Fuel** into the large launch pad's primary fuel-input slot; each item supplies 250 units of solid propellant. A solid-fuel missile requires its listed fuel capacity before launch, and that amount is consumed when it launches. Liquid-fuel missiles continue to use the launch pad's fuel and oxidizer tanks.


## Carbon monoxide warnings

Machines that burn fuel or handle exhaust can produce carbon monoxide while running. This includes fireboxes, heating ovens, and fluid burners when they are consuming fuel. Ordinary fire also releases carbon monoxide near active players, so fireplaces and accidental indoor fires need ventilation too. Read machine tooltips for the carbon monoxide warning and ventilate enclosed work areas before operating those machines. Steel grates, item grates, and air vents can help vent carbon monoxide from enclosed spaces.

Combustion machines also stop operating while water touches a side or top of any part of their multiblock. Keep every exposed multiblock face dry; water directly below a machine does not trigger this shutdown.

## Atmosphere-dependent machinery

Fuel-burning machinery needs breathable air to run. This includes the RBMK burner and rotary furnace, so build them inside a pressurized habitat when working on an airless body. RBMK fuel rods also require a pressurized atmosphere to sustain their reaction; their remaining heat can still cool down after the atmosphere is lost. Electric, nuclear, and heat-exchange machinery is not subject to this combustion rule unless its individual design requires an atmosphere.



## Installation and settings

Use the same RTM build on client and server. RTM replaces upstream HBM in an instance; both use the `hbm` identity. Follow [installation](../../README.md#installation), [configuration](../configuration.md) and [server administration](../server-admin.md) before generating a long-term world.
