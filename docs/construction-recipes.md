# Workshop and construction recipes

Use NEI to inspect the construction recipes and alternatives active in your pack. Ordinary workshop structures craft in useful lots; pressure, thermal and reactor hardware retains specialist costs.

## Construction batch inventory

| Family | Classification | Workshop batch | Notes / exception |
| --- | --- | ---: | --- |
| Steel wall and corner | Structural | 16 | Corner conversion consumes two walls and cannot recover steel. |
| Steel roof | Structural sheet | 32 | Thin roof sheet rather than a solid vessel plate. |
| Steel beam and scaffold | Structural | 16 | Scaffold colour conversions retain their existing eight-block paint lots. |
| Steel grate and wide grate | Structural / catwalk | 16 | One-way shape conversions return construction blocks, never raw metal. |
| Concrete, ordinary concrete brick, lab tile, vinyl tile | Structural / lining | 16 | Mossy, cracked, and broken recipes are weathering conversions only. |
| Reinforced brick and compound brick | Structural / lining | 16 | Iron bars/bolts and tar remain meaningful reinforcement and binder costs. |
| Reinforced light and lamp | Electrical / structural | 8 | Tritium lamps remain single-output safety exceptions because each contains a full tritium charge. |
| Decorative pipe shapes and colours | Structural / fluid dressing | 8 | Rust uses ash; marked green pipes use yellow safety paint. No conversion yields metal. |
| Functional gas vents and sealed chlorine vent | Fluid handling / process-specific | 1 | These are active dispersal machines, not decorative vents; their tanks, dispensers, gas payloads, and specialist seal materials are retained. |
| Reactor blocks and pressure/high-temperature machinery | Vessel, thermal/chemical lining, control | varies | Reactor containment, coker/firebrick, refinery pressure shells, advanced centrifuges, and similar equipment deliberately remain outside decorative batch bands. |

The inventory also reviewed common tanks and pipes (vessel/fluid handling), motors and pistons (drive), pumps (drive/fluid handling), circuits and casings (electrical/control and structural), refinery and chemical equipment (vessel plus thermal/chemical lining), and assembler infrastructure (structural plus process tooling). Ordinary machines already compose these reusable parts. Common steel pipe, steel tank, motor, and piston recipes now produce predictable batches; advanced motors, lead-shielded vessels, corrosive-service equipment, reactors, and high-pressure/high-temperature machines retain specialized inputs justified by service conditions.

## Bulk concrete and paving

Ordinary construction uses Minecraft-scale output lots. A supplied Chemical Reactor makes **256 concrete or fibre-concrete blocks in 80 seconds** (192 blocks/minute), or **256 asphalt blocks in 240 seconds** (64/minute). It needs four empty output slots for the four stacks. Mixing times remain long enough for automated supply and storage to matter; building a factory does not require a specialist chemistry progression chain. DUcrete remains a shielding material at 16 blocks per 80 seconds.

| Product | Feed per cycle | Recipe energy / cycle | Supply for continuous production |
| --- | --- | --- | --- |
| Concrete | 64 cement, one coarse and one fine aggregate bag, 16 L raw water | 320 kJ electrical; 4 kW over 80 s | 48 cement/min, 0.75 bags/min of each aggregate, 12 L/min water |
| Fibre concrete | Concrete feed plus one asbestos ingot-equivalent | 320 kJ electrical; 4 kW over 80 s | Same supply plus 0.75 fibre/min |
| Asphalt | One coarse and one fine aggregate bag, 12 L bitumen | 21 MJ electrical; 87.5 kW over 240 s | 0.25 bags/min of each aggregate, 3 L/min bitumen |
| Soil | Eight coarse aggregate bags, eight ammonium nitrate, 32 L fresh water | 960 kJ electrical over 60 s | 64 blocks/min; fertilizer and fresh-water supply |

Cement is supplied by the existing crafting routes: limestone dust plus three clay balls produces eight cement, while gypsum produces six; calcium/sand alternatives produce four. These have no machine cycle or electrical bill. The limestone route needs eight crafts (eight limestone dust and 24 clay balls) per 256-block concrete lot, or six crafts/minute to keep one line supplied. Shredding existing slag blocks remains a four-cement recovery alternative; its own upstream throughput depends on the source of slag. One gravel/sand block produces 19 matching aggregate bags. Concrete therefore needs about 0.0395 gravel and sand blocks/minute each; its coarse/fine ratio and water/cement mass ratio remain unchanged.

At the listed supplied rates, 1,000 concrete blocks take about 5.21 minutes of sustained production (four complete lots take 5 minutes 20 seconds and yield 1,024 blocks). Asphalt takes 15.63 minutes of sustained production (four complete lots take 16 minutes). Power figures exclude the reactor's separately paid cooling auxiliaries. Bitumen still comes from the petroleum chain: paving output uses the existing binder, rather than increasing refinery yields. Storage can accumulate construction supplies while the player builds elsewhere.

The block output count is a deliberate gameplay abstraction; it does not claim that each placed block contains a cubic metre of inventory material. Material and chemical-energy ledgers still close for the complete lot. Old paid construction lots finish with their old output quantities; existing blocks and stored items are never rescaled. NEI shows the four output stacks and their exact shares of the batch.

## Obtainable control-room furniture

The IBM computer and old CRT craft in lots of four, while the toaster crafts in lots of eight. The existing office-door item recipe remains the correct route to the existing office-door block. Steel filing cabinets are directly craftable; adding green dye converts one to the already-rendered and localized `GREEN` metadata. There is no reverse cabinet recipe and no invented dusty metadata.

## Strategic assembler review

Assembler foundation recipes remain intentionally separate from ordinary crafting. Centrifuge elements and machine blocks retain rotor, vessel, drive, and control costs. Large assembler/chemical infrastructure retains resistant alloys and component volume. Launch pads retain concrete foundations, welded/cast steel, scaffold, hard polymer, and advanced controls; rocket assembly remains precision-component and propulsion infrastructure. These are functional/safety exceptions, not palette blocks, and were reviewed without changing fuel-pool cooling or enabling disabled power armour.

## Suggested survival checks

In a clean survival world, use NEI to confirm output counts and cabinet metadata. Place and break each new furniture output to check drops, orientation, metadata, and stacking. Build a steel/concrete workshop, refinery shell, reactor shell, warehouse catwalk, and control room. Compare the raw inputs for an ordinary machine and a launch pad, and confirm power-armour items still expose no recipes.
