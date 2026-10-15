# RTM documentation

## Guides and gameplay

Learn how RTM works. These pages describe current default systems; NEI, placed-machine information and server configuration show the rules active in your pack.

| Start here | What you will find |
| --- | --- |
| [Getting started](gameplay/getting_started.md) | First workshop, hazards and choosing the next system |
| [Power generation](gameplay/power_generation.md) | Generation branches, fuel, infrastructure and plant sizing |
| [Steam and heat](gameplay/steam_and_heat.md) | Steam grades, converter choice, boilers, condensation and protection |
| [Fluids and chemistry](gameplay/chemistry.md) | Water quality, gases, acids, minerals and process equipment |
| [Petroleum](gameplay/petroleum.md) | Extraction to fuels, lubricant and gas recovery |
| [Materials](gameplay/materials.md) | Structural, refractory, conductor, magnetic and nuclear families |
| [Manufacturing](gameplay/manufacturing.md) | Casting, forming, joining, electronics and assembly |
| [Nuclear industry](gameplay/nuclear_industry.md) | Fuel, reactor support, enrichment and waste |
| [Construction recipes](construction-recipes.md) | Workshop structures and furniture |
| [Space travel](space-travel.md) | Rockets, survival and stations |
| [Changes from HBM](upstream-changes.md) | RTM's scope and gameplay differences |

Server owners: [Configuration](configuration.md), [Commands](commands.md), [Administration](server-admin.md), [Nuclear warfare controls](development/nuclear_weapon_reference.md).

## Design

Possible future behavior, not implemented restrictions: [RealSim Production](design/REALSIM_PRODUCTION.md) describes industrial capabilities, candidate tooling, bootstrap constraints and unresolved decisions.

## Development reference

Exact current contracts and source ownership. Begin here for implementation rather than opening an audit snapshot.

| Reference | Contents |
| --- | --- |
| [Machine catalogue](development/machine_catalogue.md) | Complete audited native-machine and boundary owner index |
| [Processes](development/process_reference.md) | Complete audited process-family coverage, current bills/rates/utilities, recipe owners and model boundaries |
| [Petroleum](development/petroleum_reference.md) | Exact batch equations, product masses, work, heat and rejection |
| [Fluid quantities](development/fluid_reference.md) | States, mass, concentrations, gases, molten/nuclear/specialist quantities |
| [Substances](development/substance_reference.md) | Petroleum/fuel density, LHV and mixture coefficients |
| [Fluid census](development/fluid_registry.md) | Every native fluid declaration and identity |
| [Materials](development/material_reference.md) | Forms, acceptance, assays, fuel suitability and ID exceptions |
| [Material census](development/material_registry.md) | Every current Mats declaration, metadata and requested forms |
| [Generators](development/generator_reference.md) | Every generator, flow/grade/export/buffer/source and configuration contract |
| [Heat and steam](development/thermal_reference.md) | Exact state/transition equations, rejection, source quality and equipment |
| [Fluid infrastructure](development/fluid_infrastructure.md) | Containers, bulk/process storage, groundwater and extraction |
| [Tank declarations](development/tank_reference.md) | Native constructor capacities and source links; dynamic sizing remains in the named owner |
| [Manufacturing](development/manufacturing_reference.md) | Consumers, lanes, work, cooling and information ownership |
| [Blast Furnace](development/blast_furnace_reference.md) | Steel bills, heat, blast, gas, lifecycle and persistence |
| [MAGNOX](development/magnox_reference.md) | Reactor model, interlocks, protection, controls and saved-state migration |
| [Compatibility](development/compatibility.md) | Source hierarchy, config precedence, IDs, saves, APIs and contributor rules |
| [Performance](development/performance_reference.md) | Current scheduling/network/rendering architecture and evidence limits |

Build, Windows, IDE and development-mod instructions remain in the [README](../README.md#development) and [contribution guide](../CONTRIBUTING.md).

## Historical audits

[Historical evidence index](development/historical/index.md): original inventories, old defects, before/after values, reference sources, scale comparisons and migration reasoning. These are dated records, not current specifications.

## Authority and maintenance

1. **Current implementation:** production source and loaded server configuration/recipe maps, explained by current development references.
2. **Player documentation:** curated explanations of that implementation.
3. **Design proposals:** candidate future behavior, never an implicit feature promise.
4. **Historical audits:** evidence of earlier states and decisions.

Change the current reference when implementation changes. Record release history in `CHANGELOG.md`; preserve worthwhile discovery material separately. Avoid appending another correction below contradictory specifications. Exact custom values remain the server's responsibility.
