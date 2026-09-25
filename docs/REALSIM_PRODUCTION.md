# RealSim Production

> **Status: future design proposal.** Nothing in this document describes an implemented recipe tier, machine restriction, or save migration unless it is explicitly identified as current behavior. This document does not authorize recipe changes by itself.

RealSim Production is a possible future progression pass for Ragex's Nuclear Tech Mod (RTM). Its purpose is to make an early mechanized workshop, a heavy wartime production plant, and a controlled Cold War nuclear or aerospace facility feel different without requiring a new model for every manufacturing operation.

The proposed progression is based on **industrial capability**, not a generic `Machine I -> Machine II -> Machine III` ladder. Existing RTM machines, tooling, material shapes, and intermediate parts should carry most of the design. A new machine is justified only when an important process family has no credible existing home.

## Current RTM terminology and behavior

Use current player-facing RTM names in new documentation and interfaces. Historical HBM names and internal registry/class names are included below only to make source inspection and compatibility work unambiguous.

| Current player-facing name | Historical or internal wording | Current role relevant to this proposal |
| --- | --- | --- |
| **Fabrication Machine** | Assembly Machine, assembler, `machine_assembler` | One template-driven production lane. It currently uses the general assembler recipe pool and is the bootstrap machine for much of RTM industry. |
| **Industrial Fabrication Machine** | Assembly Factory, assemfac, `machine_assemfac` | Eight template-driven lanes, upgrades, automatic material handling, fresh-water consumption, and spent-steam output. It currently resolves the same recipe pool as the Fabrication Machine; it is not presently a separate heavy-fabrication tier. |
| **Foundry** | Crucible, `machine_crucible`, crucible recipes/templates | Heat-driven melting, alloying, molten-material handling, and casting through foundry channels, outlets, storage, and molds. |
| **Burner Press**, **Electric Press**, and **Conveyor Press** | Press variants | Forming operations selected by reusable press stamps. The Burner and Electric Press use the same `PressRecipes` outputs with different power/throughput behavior. |
| **Arc Welder** | Same display name | Dedicated joining recipes, including cast-to-welded plates and several structural, missile, satellite, and high-energy products. Some recipes also require a process gas. |
| **Soldering Station** | Same display name | Dedicated circuit assembly using component, PCB, solder, and sometimes solvent inputs. It should remain electronics-focused. |
| **Blast Furnace** | DiFurnace in source | Early alloy and steelmaking gate already emphasized by RTM progression. |
| **Chemical Reactor** | Chemical Plant, chemplant | General chemistry and material-conditioning machine. It is not a generic fabrication tier. |
| **Industrial Size Chemical Reactor** | Chemical Factory, chemfac | Larger chemical-processing equipment. It should not become a catch-all heavy assembler. |
| **Crusher** | Shredder | Size reduction and ore preparation, not fabrication. |
| **Vehicle Assembly Base** | Rocket Assembly | Dedicated multistage orbital-vehicle construction. It should remain specialized rather than being folded into general fabrication. |

The arc-furnace labels need a terminology pass before they become progression terms. The current language file calls `machine_arc_furnace` an **Electric Arc Furnace**, while related block and GUI labels use **Plasma Arc Furnace**. The large arc-furnace implementation also supports recipe-backed solid output and molten-material output. RealSim Production should not invent a distinction until the actual blocks, interfaces, recipes, and intended names are reconciled.

The current **Assembly Template** item name is also still valid even though the machine is now called the Fabrication Machine. Renaming that persistent item is optional and should be treated as a separate localization and compatibility decision, not assumed by this proposal.

Source anchors for the current state include the [English localization](../src/main/resources/assets/hbm/lang/en_US.lang), [shared fabrication implementation](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemblerBase.java), [Fabrication Machine](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssembler.java), [Industrial Fabrication Machine](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemfac.java), [Arc Welder recipes](../src/main/java/com/hbm/inventory/recipes/ArcWelderRecipes.java), [press recipes](../src/main/java/com/hbm/inventory/recipes/PressRecipes.java), and [Foundry recipes](../src/main/java/com/hbm/inventory/recipes/CrucibleRecipes.java).

## Goals

- Make industrial eras differ through capability, scale, precision, process control, and supporting infrastructure.
- Reuse RTM's existing machines and models before adding new blocks.
- Give cast, formed, welded, machined, sealed, balanced, and inspected products distinct uses where those distinctions improve progression.
- Preserve a viable non-nuclear bootstrap into the first reactor and its complete heat-to-electricity loop.
- Make large nuclear, aerospace, and pressure-boundary hardware require credible facilities without moving every important item out of ordinary crafting.
- Keep the system understandable through recipe lookup, templates, tooltips, and consistent machine names.
- Support modpacks and custom recipe files without silently changing existing templates into different products.

## Non-goals

- Simulating every real factory department or manufacturing operation.
- Adding one machine for every item family or historical era.
- Making recipes expensive solely because their output is late-game or physically large.
- Turning chemistry, enrichment, or ore processing machines into generic crafting gates.
- Requiring nuclear power to manufacture the machines or fuel needed for the first practical reactor.
- Replacing RTM's recipe configuration, ore-dictionary support, 528 mode, LBSM branches, or existing specialist assembly systems.
- Combining this progression pass with a broad machine scheduler, networking, renderer, or multiblock rewrite.

## Design principles

### Gate operations, not item importance

A process belongs in a machine when it needs repeatable forming, controlled melting, welding or sealing, precision machining, rotor balancing, clean handling, heavy lifting, inspection, or production-scale automation. Attaching a finished pipe, loading a finished fuel element, or combining interchangeable finished parts can remain hand work even when the result is strategically important.

### Prefer a family over a one-off

Every new capability, tool, or module should serve a coherent recipe family. A dedicated operation is credible for many rotors, pressure vessels, fuel elements, or instrument packages. A machine whose only job is to combine two named quest items is not.

### Scale and precision are independent

Large does not automatically mean precise, and precise does not automatically mean late-game. A heavy welded frame and a small high-speed centrifuge rotor need different capabilities. Recipes may require both by composing intermediates, but they should not be collapsed into a single universal higher tier.

### Throughput is not capability by default

The Industrial Fabrication Machine currently provides eight lanes and fluid/steam behavior but shares the Fabrication Machine's recipes. RealSim Production may give it heavy-fabrication eligibility, but existing throughput alone must not be documented as a capability tier before that change is implemented.

### Preserve compressed gameplay abstractions

RTM already compresses multistage industrial processes into practical machines and batch sizes. RealSim Production should add legible decisions, not turn every real heat treatment, cleaning step, fastener, or quality record into an item.

## Proposed fabrication capabilities

These are recipe capabilities, not necessarily separate machines.

| Capability | Existing RTM foothold | Suitable operations | Boundary |
| --- | --- | --- | --- |
| **Hand fitting and bench work** | Crafting table and tiered anvils | Simple fittings, brackets, housings, fasteners, manual toolmaking, and attachment of finished components | Do not hand-build complete heavy pressure boundaries or precision rotor assemblies. |
| **Primary metallurgy and casting** | Furnace, Blast Furnace, Foundry, molds, channels, and Strand Caster | Alloy preparation, rough castings, billets, cast plate, and near-net shapes | Casting creates blanks; it does not imply final tolerances, weld quality, or electronics installation. |
| **Forming and stamping** | Burner Press, Electric Press, Conveyor Press, and existing stamps | Plate, wire, shells, simple drawn or stamped parts, and repeatable sheet-metal forms | Keep equivalent forming recipes shared where the machines differ only in power and throughput. |
| **Electrical and instrument assembly** | Vacuum Solderer and Soldering Station | Vacuum-tube work, PCBs, circuits, controls, sensors, and avionics subassemblies | Do not move ordinary structural joining into electronics machines. |
| **General powered fabrication** | Fabrication Machine | Motors, pumps, compact machines, ordinary equipment modules, and the machines needed to bootstrap chemistry and power | It should not remain the automatic home for every reactor structure, launch facility, or large pressure vessel. |
| **Welded structural fabrication** | Arc Welder plus cast and welded material shapes | Welded plate, frames, tanks, casings, missile/space structures, and sealed structural subassemblies | Welding does not perform precision bearing installation, winding, balancing, or circuit assembly. |
| **Heavy industrial fabrication** | Industrial Fabrication Machine as a candidate host; Arc Welder and Foundry as suppliers | Large frames, reactor structural blanks, shielded vessels, turbine casings, and production-scale assemblies | This is proposed eligibility, not current behavior. The machine's own bootstrap recipe must remain available to the Fabrication Machine. |
| **Precision rotating equipment** | Existing machine models can consume parts; no clearly dedicated general-purpose process is established | Shafts, bearing fits, impellers, turbine rotors, centrifuge rotors, and final dynamic balancing | Add tooling or a new station only after one shared recipe family and its bootstrap are fully surveyed. |
| **Controlled and safety-critical fabrication** | Soldering Station, Chemical Reactor, Arc Welder process gases, specialist fuel and vehicle machines | Fuel pellet/cladding/seal operations, clean instrument packages, inspected pressure components, and special-atmosphere work | Controlled fabrication should extend existing specialist machines where credible rather than become a universal top tier. |
| **Production automation** | Industrial Fabrication Machine, inserters, conveyors, template crates, factory blocks | Parallel production, automatic feed/output, and reduced labor | Automation should improve sustained output; it should not arbitrarily unlock unrelated science. |

## Existing-machine reuse and repurposing

### Fabrication Machine

Keep it as the central early powered fabrication machine. It should continue to make the Chemical Reactor, ordinary Centrifuge, and other next-step equipment it currently supplies, and it must be able to make the Industrial Fabrication Machine. The first useful change is not a remodel; it is an explicit recipe-capability rule that prevents selected heavy recipes from running in this machine.

### Industrial Fabrication Machine

Use its current large multiblock, eight lanes, upgrades, input/output faces, fresh-water consumption, and spent-steam output as the leading candidate for heavy industrial fabrication. Preserve general recipes for backwards usefulness, then add eligibility for selected heavy recipes. Do not claim that its animated welder arms replace the separate Arc Welder's process-gas and material-shape roles.

Before it gates first-reactor structure, verify that fresh water, spent-steam handling, starter electricity, the machine recipe itself, and all required material forms are available without the gated reactor. A blocked steam tank already prevents processing, so documentation and recipe lookup must expose that operating dependency.

### Foundry

Treat the Foundry as RTM's current melting, alloying, and casting system, not as a renamed decorative crucible. Expand its importance by consuming existing cast shapes in downstream recipes. Prefer new molds or new outputs within its current molten-material network over introducing a second generic casting machine.

### Press family

Keep the Burner Press as the early forming route and the Electric Press as the powered throughput upgrade. The Conveyor Press remains an automation form of stamped production. Add stamps only when the shape is reused broadly enough to justify visible tooling; do not create one stamp per finished machine.

### Arc Welder

Keep welded plate and credible structural joining here. Its existing recipe model already supports multiple item inputs, energy/duration, and an optional fluid, making it suitable for higher-quality or controlled-atmosphere weldments without a new model. Preserve the documented separation between a welded housing and a complete motor or other internally fitted component.

### Soldering Station and Vacuum Solderer

Keep these focused on electronics and instrumentation. RealSim Production can make advanced machines consume more finished circuit or instrument modules, but it should not turn either station into a generic precision assembler.

### Blast Furnace, arc furnaces, and chemical equipment

Use these for the material or chemical state they actually produce. The Blast Furnace remains an early metallurgy gate. Arc-furnace roles should be clarified after their current Electric/Plasma names are reconciled. The Chemical Reactor and Industrial Size Chemical Reactor should condition materials, coolants, binders, etchants, fuels, and process chemicals rather than assembling structural equipment.

### Existing specialist assembly

Keep the Missile Assembly Station, Vehicle Assembly Base, ICF Fuel Pellet Maker, and comparable specialist systems distinct. General fabrication should supply their parts; it should not erase their gameplay role. Conversely, those specialist machines should not become prerequisites for unrelated industrial equipment.

## Tooling, modules, and intermediate parts

### Reuse first

RTM already has four strong tooling patterns:

- **Press stamps** select a forming operation.
- **Assembly Templates** select Fabrication Machine and Industrial Fabrication Machine recipes.
- **Foundry Templates and molds** select alloying and cast shapes.
- **Machine upgrades** change speed, power use, or overdrive behavior.

New tooling should follow these patterns and remain reusable. Tooling belongs in a recipe or machine slot when it represents a durable die, fixture, mandrel, winding head, balancing setup, inspection setup, or controlled-process attachment. Consumable electrode, gas, flux, solvent, binder, and shielding inputs should remain recipe inputs rather than permanent modules.

### Candidate modules

These are concepts to evaluate, not promised items:

- **Heavy fixture or lifting package:** enables large weldments or assemblies in the Industrial Fabrication Machine.
- **Machining and balancing package:** enables a shared family of shafts, impellers, turbine rotors, and centrifuge rotors if existing machines cannot express that family cleanly.
- **Controlled-atmosphere or clean-work package:** enables sealed fuel, vacuum, or instrument operations only where process gas and chemistry inputs are insufficient.
- **Inspection package:** represents dimensional checks or nondestructive examination for safety-critical outputs; it should be a reusable capability, not paperwork for every recipe.

Prefer a small number of visible capability modules over dozens of recipe-specific tokens. Modules must have clear slot behavior, tooltips, automation rules, and persistence before recipes depend on them.

### Existing intermediate vocabulary

Favor current material and component forms where they already express the process:

- ordinary, cast, and welded plate;
- shells, pipes, wires, billets, ingots, and blocks;
- motors, pumps, pistons, tanks, circuits, and machine casings;
- electrodes, process gases, solders, solvents, binders, and refractory materials;
- complete specialist subassemblies such as missile tanks/thrusters or finished circuits.

Candidate new intermediates are justified only when several recipes share them. Plausible families include rough rotor/shaft blanks, balanced rotor assemblies, pressure-vessel sections, inspected weldments, cladding tubes, sealed fuel elements, bearing packs, and instrument/control modules. Avoid creating both a part and an almost identical "advanced" part merely to add another step.

## Recipe migration criteria

Evaluate a recipe for migration only after tracing all of its current routes, modes, and consumers.

A recipe is a strong migration candidate when several of the following are true:

- the operation requires a material transformation already represented by a machine, such as casting, stamping, welding, soldering, or chemical conditioning;
- the product is too large or heavy for credible bench assembly;
- tolerance, alignment, balancing, sealing, cleanliness, atmosphere, or inspection is central to the product's function;
- a reusable intermediate already exists or would serve several related recipes;
- moving the recipe distinguishes a real facility capability rather than merely raising cost;
- the destination machine and all of its power, fluid, tooling, and exhaust needs are available before the output is needed;
- NEI/template presentation can explain the route without hidden knowledge.

Keep or return a recipe to simple crafting or anvil work when it is primarily:

- attaching finished interchangeable parts;
- loading a finished fuel element into a finished holder;
- making a small fitting, bracket, cover, or connection;
- a maintenance or field-installation action;
- already gated by meaningful upstream manufactured parts;
- too isolated to justify a durable tool, module, or machine family.

For every migration, check:

1. ordinary and expensive/528-mode variants;
2. LBSM simple-crafting alternatives;
3. ore-dictionary and material-shape substitutions;
4. recipe JSON overrides and template folders;
5. output counts and whether a batch represents one part or a production lot;
6. loot, starter kits, trading, structures, recycling, and other bypasses;
7. all machines, fuels, fluids, and power sources required to bootstrap the destination;
8. whether automation can insert tooling and extract output without consuming durable tools;
9. whether an old template continues to identify the same output;
10. whether documentation and NEI agree on the current player-facing machine name.

## Bootstrap constraints

The intended broad arc is:

**hand metalworking -> starter non-nuclear power -> Fabrication Machine and Chemical Reactor -> welded/heavy/precision fabrication -> first practical natural-uranium reactor and complete steam loop -> higher-throughput and enriched-fuel industry**

This arc imposes hard constraints:

- The Fabrication Machine remains buildable through the current early metalworking route.
- A starter generator and distribution equipment remain available before powered fabrication.
- The Fabrication Machine can build the Industrial Fabrication Machine and any tooling needed to unlock its first heavy recipes.
- Fresh water and a usable spent-steam outlet are available before an Industrial Fabrication Machine recipe becomes mandatory.
- The Arc Welder, Foundry, Soldering Station, and other required suppliers remain reachable before their outputs gate progression.
- Natural-uranium reactor fuel remains a viable first-power route; enrichment is an upgrade, not a hidden prerequisite.
- No first-reactor component requires power, steam, coolant, or material that can only be obtained from that reactor.
- The turbine, exhaust-steam, condensation, water-return, control, and radiation-safety chain is considered together with the reactor body.
- Modpack recipe overrides may deliberately change this arc, but the default data must not contain a circular dependency.

The source-inspected RBMK route in [RBMK first-power dependencies](RBMK_FIRST_POWER_DEPENDENCIES.md) identifies the RBMK structural blank as a possible first heavy-fabrication pilot and documents the current shortcut and bootstrap hazards. That document describes current dependencies plus a small proposal; it is not evidence that RealSim Production is already implemented.

## Compatibility and persistence

### Recipe data and templates

Fabrication recipes currently serialize output, inputs, duration, and optional folders. Both fabrication machines resolve the shared recipe through their common base. A future capability field should therefore be optional, explicit, and backwards-compatible. A safe default is the current general capability so old custom JSON remains valid and continues to run in both machines until a pack author opts into stricter behavior.

Eligibility must be enforced on the server processing path, not only hidden in NEI or template creation. NEI, template tooltips, and template folders should display the minimum facility capability. Unknown capability values should fail with a clear load error or documented fallback; they must not silently select an unrelated tier.

Persistent output-identified templates and legacy recipe-index templates must continue to identify the same output. Adding capability metadata to the recipe is safer than rewriting existing template NBT. An old template may become unable to run in the smaller machine after an intentional migration, but it must not become a template for a different recipe.

### Existing worlds and multiblocks

- Do not replace Fabrication Machine or Industrial Fabrication Machine block IDs, TileEntity IDs, NBT keys, inventories, fluids, orientations, or dummy-block layouts merely to add eligibility.
- Preserve stored items, upgrades, water, steam, power, progress, and lane state across upgrades.
- Define what happens when a recipe becomes ineligible while progress is nonzero. The safest default is to stop without consuming inputs and retain the template; do not destroy or convert inventory during chunk load.
- Keep current sided automation and template-crate behavior unless a documented capability module requires an additive slot rule.
- Preserve optional-mod behavior and avoid referencing client-only interfaces from server recipe checks.

### Configurations and external recipes

Preserve `hbmAssembler.json`, existing recipe folders, ore dictionary behavior, 528 mode, LBSM behavior, custom machine configuration, and established optional integrations. WarTec or other external code can register assembler recipes through legacy methods; those recipes need the backwards-compatible general default unless the integration adopts the new capability API.

## Performance and architecture constraints

RealSim Production should be data-driven and cheap on the steady-state path.

- Resolve and cache recipe capability alongside the existing per-lane cached inputs, output, and process time. Do not scan capability tables every tick.
- Invalidate eligibility when the template, capability module, configuration, or relevant inventory changes. Keep simulation dirtiness separate from client network dirtiness.
- Do not add world scans, adjacent-inventory scans, reflection, or allocation-heavy recipe conversion to per-tick processing.
- Do not migrate the Fabrication Machine or Industrial Fabrication Machine to the new machine runtime as a side effect of this feature. They remain legacy-driven today, and the performance audit identifies direct slot/tank writes and multiblock lifecycle behavior that require a separate audit before scheduler opt-in.
- If these machines are later migrated, preserve independent lane ownership and use the existing typed task-slot concept rather than one timer per new recipe object.
- Capability modules should use ordinary saved inventory state and existing controller ownership. Dummy blocks must not register independent logical machines.
- Packet changes should be state-change-driven and retain a low-frequency recovery baseline where RTM's synchronization architecture expects one.

See [RTM Performance Architecture Audit](RTM_PERFORMANCE_AUDIT.md) for the current assembler cache, lifecycle, dirty-state, scheduler, and migration constraints. A fabrication progression change must not be described as an optimization or asynchronous processing change.

## Phased implementation

### Phase 0 — Inventory and terminology

- Reconcile Electric Arc Furnace and Plasma Arc Furnace display names with their actual blocks and recipes.
- Produce a source-derived list of crafting, anvil, Fabrication Machine, Industrial Fabrication Machine, Arc Welder, Foundry, press, and Soldering Station outputs.
- Mark alternative 528/LBSM/custom routes and current bootstrap dependencies.
- Select capability names that describe operations rather than eras or vague tiers.
- Make no recipe changes.

### Phase 1 — Capability data without migrations

- Add an optional fabrication capability to the assembler recipe model and JSON schema.
- Default existing and external recipes to the current general behavior.
- Add server-side eligibility checks, template/NEI display, localization, and clear diagnostics.
- Give the Fabrication Machine and Industrial Fabrication Machine explicit supported-capability sets, initially equivalent so gameplay does not change.
- Verify old output-identified and legacy-index templates.

### Phase 2 — One heavy-fabrication pilot

- Allow the Industrial Fabrication Machine to run general and heavy recipes while the Fabrication Machine remains general.
- Keep the Industrial Fabrication Machine's own recipe general so it remains the bootstrap bridge.
- Migrate one well-understood family, with the RBMK structural blank as the leading candidate rather than an automatic decision.
- Verify the natural-uranium fuel route, starter power, fresh-water supply, spent-steam handling, complete turbine/condenser loop, NEI presentation, and all recipe modes before release.

### Phase 3 — Deepen existing process machines

- Move credible forming to existing presses and stamps.
- Move credible cast shapes to the Foundry and molds.
- Move credible structural joins to the Arc Welder, using optional process fluids only where justified.
- Move circuit and instrument packages to the Soldering Station or Vacuum Solderer.
- Rework downstream fabrication recipes to consume those finished intermediates without multiplying low-value steps.

### Phase 4 — Precision and controlled fabrication

- Survey turbine, centrifuge, pump, compressor, generator, reactor-fuel, and aerospace recipe families together.
- Introduce a reusable module or one new station only if existing machines cannot express a large enough family.
- Add balancing, sealing, clean handling, or inspection as capabilities with clear gameplay feedback.
- Keep special fuel, missile, and vehicle machines in their existing domains.

### Phase 5 — Throughput, migration, and documentation

- Balance batch sizes, duration, power, fluid use, byproducts, and automation after capability placement is stable.
- Provide explicit migration notes for recipes that changed machines.
- Update the getting-started and construction documentation only after implemented behavior exists.
- Validate dedicated-server processing, chunk unload/reload, multiblock replacement, custom JSON, old templates, automation, and representative 528/LBSM configurations.

## Candidate first changes

The smallest credible starting set is:

1. **Normalize arc-furnace terminology.** Decide which current block is Electric Arc Furnace and which, if any, is Plasma Arc Furnace; align block names, GUI names, NEI categories, and docs without changing recipes.
2. **Add non-restrictive capability metadata.** Extend assembler recipes with an optional capability whose legacy default preserves today's shared recipe access.
3. **Expose capability in recipe lookup.** Show the minimum facility on Assembly Templates and in NEI before any recipe is moved.
4. **Differentiate the two fabrication machines.** Give the Industrial Fabrication Machine heavy eligibility while preserving general recipes and the ability to build it in the Fabrication Machine.
5. **Pilot the RBMK structural blank only after a bootstrap check.** Its scale makes it a credible heavy-fabrication candidate, but the migration must prove starter power, fresh water, spent-steam handling, natural-uranium fuel, and the whole turbine-water loop remain reachable.
6. **Audit one rotating-equipment family.** Compare turbine rotors, centrifuge rotors/elements, pumps, and compressors before deciding whether a machining/balancing module has enough reuse to exist.
7. **Use existing shapes more consistently.** Prefer cast plate into welded plate into a heavy assembly where the operation warrants it, while avoiding blanket replacement of every ordinary plate.

Do not begin with a new machine model, a mass recipe migration, or a global scheduler conversion. The proposal becomes useful when one existing facility gains a clear, visible, backwards-compatible capability and a small family of recipes demonstrates that the progression is better.

## Validation required before implementation is called complete

- Static dependency review of each migrated recipe and every alternative route.
- Recipe JSON round-trip and legacy-template checks.
- NEI and tooltip verification for eligibility and missing-facility feedback.
- Dedicated-server processing and sided automation checks.
- Save/reload, chunk unload/reload, block replacement, and multiblock proxy checks.
- Fabrication Machine and eight-lane Industrial Fabrication Machine inventory/fluid behavior.
- A clean-world survival bootstrap through starter power, heavy fabrication, natural-uranium first power, turbine exhaust, condensation, and water return.
- Representative default, 528/expensive, and LBSM configurations.
- Profiling only if implementation changes the steady-state recipe or machine loop.

No build, Minecraft launch, NEI session, recipe migration, or runtime validation was performed for this design document. Its statements about current behavior are based on source and documentation inspection of the active checkout.
