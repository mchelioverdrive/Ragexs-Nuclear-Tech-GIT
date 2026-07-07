# Space Travel Guide

Space systems in RNTM are late-game infrastructure. Before attempting launch, establish reliable power, oil processing, precision manufacturing, radiation protection, and enough spare materials to replace lost rocket parts.

## Progression checklist

1. Build a stable industrial base with HE power, machine templates, metal processing, petrochemicals, and advanced circuits.
2. Produce rocket hardware for an Orbital Rocket through the Vehicle Assembly Base.
3. Place and power a Rocket Launch Pad, then verify fueling and launch-pad access.
4. Program the correct destination drive for the trip.
5. Bring survival equipment for vacuum, atmosphere, radiation, heat, pressure, and return travel.

## Rocket planning

- Treat every launch as a logistics project: payload, capsule, fuel tanks, stability, destination, and return plan all matter.
- Fill tanks completely and leave margin for destination changes or failed attempts.
- Verify whether the payload is for crew travel, satellite deployment, or an orbital station core before launch.
- Keep a backup destination drive and recovery supplies somewhere safe.

## Survival planning

Space and planetary bodies can differ in atmosphere, pressure, water-table behavior, radiation, and environmental hazards. Wear sealable armor with PLSS support where required, carry oxygen and power reserves, and scout a destination before placing expensive infrastructure.

## Orbit, stations, and satellites

Orbit is useful for staging transfers, deploying satellites, and creating orbital stations. Station drives can revisit known stations, so name stations clearly and keep spare programmed drives. Satellite payloads should be tracked by frequency; server operators can use station and satellite commands for recovery or cleanup.

## Planetary ring rendering

Gas-giant and ice-giant rings are rendered by the RTM celestial sky provider as local annular bands around the apparent planet disc. The ring dimensions are intentionally derived from the already-clamped sky size of each visible body instead of from regular NTM:Space distances, because RTM's realistified solar-system scale uses much larger real-world radii and orbital distances.

## Server notes

Server owners should review dimension IDs and space-related configuration before a long-term world starts. Avoid changing orbit or celestial dimension IDs after stations, satellites, or player bases exist.

## Orbital station thruster balancing

Station propulsion is now balanced as full spacecraft propulsion hardware rather than simple nozzle blocks. The LPW-3N, LPW-2, HTR-F4, and Xenon Station Thruster recipes ask for more refractory metals, feed plumbing, tanks, high-grade motors/coils, and avionics to reflect the way real station propulsion combines thrust hardware with propellant storage, valve regulation, thermal control, and guidance/control systems.

- **LPW-3N Station Thruster:** baseline nuclear/thermal station thruster package using refractory chambers, durable plumbing, tankage, tungsten coils, and avionics.
- **LPW-2 Station Thruster:** upgraded chemical station thruster assembly that now depends on an LPW-3N core plus extra pumps, tanks, titanium shelling, and advanced-alloy coils.
- **HTR-F4 Station Thruster:** high-end plasma-heater station thruster with two LPW-3N cores, expanded tungsten/resistant-alloy structure, extra durable feed lines, and more avionics.
- **Xenon Station Thruster:** ion station thruster fabrication now consumes xenon working gas, high arc-welder energy, tungsten/niobium welded plates, avionics, and Saturnite electrodes to represent grids, discharge chambers, neutralizers, and precision feed hardware.

## Destination drive processing range

Stardar and drive processor tiers are now evaluated relative to the body where the machine is being used. A planet, its moons, and its parent body are treated as local navigation targets, while unrelated planets still require interplanetary processing capability and any configured minimum tier. This keeps local moon operations practical without weakening long-range mission planning.
### Orbit sky rendering parity

Orbit sky rendering intentionally follows the HBM Space reference under `DONOTMODIFY/hbmspace`. Planet visuals in orbit are square billboard-style celestial renders driven by provider-cached satellite orbital metrics, matching the HBM Space API shape instead of replacing it with local workarounds.
