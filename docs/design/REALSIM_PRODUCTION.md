# RealSim Production

**Future design proposal.** Capability restrictions and tooling below are candidates, not implemented recipe tiers. Current behavior is explained in [Manufacturing](../gameplay/manufacturing.md); exact current contracts live in [development references](../index.md#development-reference).

## Intended goals

Make a mechanized workshop, heavy industrial plant and controlled nuclear/aerospace facility differ through industrial capability. Reuse machines, shapes, tools and intermediate components. A new station is justified only by a substantial process family with no credible existing home.

Gate operations, not strategic importance. Large welded structures, sealed pressure vessels, balanced rotors and clean fuel assemblies need different facilities. Attaching finished interchangeable parts can remain hand work. Scale, precision and throughput are independent: eight production lanes alone do not create a new capability.

## Proposed capabilities

| Capability | Candidate operations / existing foothold |
| --- | --- |
| General fabrication | Repeatable assembly from finished components; Fabrication Machine |
| Heavy fabrication | Large welded frames, pressure structures and lifting fixtures; Industrial Fabrication Machine |
| Precision rotating equipment | Machined shafts, aligned bearings and balanced rotors; reusable tooling if a broad family justifies it |
| Controlled fabrication | Sealed fuel, atmosphere-sensitive assembly, clean instrumentation and inspection where process gases alone cannot express the operation |

The two fabrication machines currently share recipes. A future heavy capability would be a deliberate eligibility change, not a description of current throughput. The Foundry remains melting/alloying/casting; presses remain forming; Arc Welder remains joining; soldering equipment remains electronics; chemical equipment remains material conditioning and chemistry. Missile, vehicle and fuel-pellet assembly keep their specialist roles.

## Candidate tooling and parts

Reuse stamps, templates, molds and existing upgrades first. Candidate reusable packages include heavy fixtures/lifting, machining/balancing, controlled atmosphere and inspection. Each must serve multiple recipes and have clear slots, automation and operating feedback. Gas, flux, electrodes, binder and solvent remain consumable process supplies where appropriate.

Prefer cast/formed/welded plate, pipes, shells, motors, pumps, circuits, casings and finished specialist assemblies. Add rotor blanks, balanced shafts, cladding tubes or inspected weldments only where several products share them. Avoid a new token for every real-world manufacturing step.

## Recipe placement

Migrate a recipe when its transformation needs controlled melting, forming, welding, sealing, balancing, precision, cleanliness or heavy handling. Keep ordinary fitting and field assembly simple. Expensive materials alone do not justify another machine.

Evaluate ordinary, 528, LBSM and custom routes together. Check all upstream feeds, alternative recipes, recycling, loot, tooling and downstream consumers. The candidate first heavy-fabrication family is large reactor structure; an RBMK blank is a possible pilot, not an automatic migration.

## Bootstrap constraints

Hand metalworking must reach starter non-nuclear power and general fabrication. General fabrication must build the industrial machine and its initial tooling. Supporting Foundry, presses, welding, electronics, cooling and process gases must be reachable before their outputs are mandatory.

The first reactor route must include fuel manufacture, heat removal, an appropriately rated turbine, condensation, water treatment, control and radiation protection. Natural-uranium first power must remain viable without enrichment or a component made only with that reactor's output. Never gate the first cooling loop or turbine behind the power plant it is needed to start.

## Proposed implementation approach

1. Establish candidate recipe families and bootstrap dependencies.
2. Add optional capability metadata with the current general default for native, external and custom recipes.
3. Enforce eligibility in server processing and display requirements in templates/NEI.
4. Pilot one heavy family while keeping the industrial machine's own recipe accessible.
5. Extend only where a family proves useful; then tune throughput and utilities.

Use existing cached recipe and MachineRuntime lifecycle/invalidation paths. Capability checks must not introduce continuous world/inventory scans or duplicate schedulers. Preserve block/tile IDs, stored inventories, lane progress, output-identified and legacy templates, custom recipe loaders and optional integrations. Unknown capability data needs a clear error or documented fallback; it must never silently select another product.

## Unresolved decisions

- Which heavy recipe family provides a worthwhile first pilot?
- Can precision and controlled work use existing stations, or does a broad shared family need a new station?
- Which tooling is meaningful to players without duplicating ordinary consumables?
- How should an in-progress recipe behave if a configured capability becomes unavailable?
- Which new intermediates add useful decisions rather than extra crafting?

Before adopting restrictions, verify survival bootstrap, NEI feedback, sided automation, custom JSON, existing templates, eight-lane behavior, save/reload, multiblock replacement and dedicated-server operation. This proposal supplies no current recipe inventory or balance patch.
