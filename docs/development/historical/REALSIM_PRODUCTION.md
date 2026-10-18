# RealSim Production

> **Historical evidence.** Preserved incoming investigation and implementation record; values describe its recorded stages. For current behavior use the [current reference](../../design/REALSIM_PRODUCTION.md).

> **2026-10-18 correction:** the later statement that nitre-bed IDs 1143/1144 are unavailable is superseded. Both are restored as explicit work-only gameplay abstractions for Deadhour, with early compost fertilizer and the historical copper-crystal sulfur coproduct. See [current chemistry routes](../../gameplay/chemistry.md#early-gunpowder-for-deadhour) for quantities, dependencies and override handling.

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
| **Metallurgical Furnace** | DiFurnace in source; registry and saves retained | Cheap first steel and general-purpose small alloy batches; intentionally poor fuel economy. |
| **Blast Furnace** | `machine_blast_furnace` | Bulk ore-to-steel coke-fired shaft furnace; coke, forced air, paid hot blast, optional limestone and O2, recoverable top gas. |
| **Chemical Reactor** | Chemical Plant, chemplant | General chemistry and material-conditioning machine. It is not a generic fabrication tier. |
| **Industrial Size Chemical Reactor** | Chemical Factory, chemfac | Larger chemical-processing equipment. It should not become a catch-all heavy assembler. |
| **Crusher** | Shredder | Size reduction and ore preparation, not fabrication. |
| **Vehicle Assembly Base** | Rocket Assembly | Dedicated multistage orbital-vehicle construction. It should remain specialized rather than being folded into general fabrication. |

The arc-furnace labels need a terminology pass before they become progression terms. The current language file calls `machine_arc_furnace` an **Electric Arc Furnace**, while related block and GUI labels use **Plasma Arc Furnace**. The large arc-furnace implementation also supports recipe-backed solid output and molten-material output. RealSim Production should not invent a distinction until the actual blocks, interfaces, recipes, and intended names are reconciled.

The current **Assembly Template** item name is also still valid even though the machine is now called the Fabrication Machine. Renaming that persistent item is optional and should be treated as a separate localization and compatibility decision, not assumed by this proposal.

Current material-role corrections are implemented independently of these proposed capability tiers: the Foundry can prepare tin–lead solder wire for the Soldering Station; thermite uses distinct oxide powder; native refining intermediates cannot substitute for finished metal; and both small furnace variants honor complete alloy bills. See the [material implementation report](REALSIM_MATERIAL_IMPLEMENTATION.md) for current forms, fuel suitability and save compatibility. These changes do not implement the future manufacturing tiers below.

Source anchors for the current state include the [English localization](../../../src/main/resources/assets/hbm/lang/en_US.lang), [shared fabrication implementation](../../../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemblerBase.java), [Fabrication Machine](../../../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssembler.java), [Industrial Fabrication Machine](../../../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemfac.java), [Arc Welder recipes](../../../src/main/java/com/hbm/inventory/recipes/ArcWelderRecipes.java), [press recipes](../../../src/main/java/com/hbm/inventory/recipes/PressRecipes.java), and [Foundry recipes](../../../src/main/java/com/hbm/inventory/recipes/CrucibleRecipes.java).

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

Use these for the material or chemical state they actually produce. The Metallurgical Furnace supplies first steel and small alloy batches. The Blast Furnace can be constructed from five steel blocks, one firebrick block, a basic motor and two steel pipe items; efficient bulk operation depends on coke and blast infrastructure, with optional flux, oxygen enrichment and gas recovery. It abstracts downstream hot-metal refining and directly outputs steel. The Large Arc Furnace remains an electric steelmaking/recycling and advanced-alloy machine, rather than a required step after this furnace. The Chemical Reactor and Industrial Size Chemical Reactor should condition materials, coolants, binders, etchants, fuels, and process chemicals rather than assembling structural equipment.

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
- Current shared assembler and chemistry bases already use MachineRuntime accounting. Preserve that runtime and independent lane ownership when adding capability eligibility; use existing typed task slots rather than one timer per new recipe object. Direct inventory/tank mutations and multiblock lifecycle changes still require the established invalidation/ownership rules.
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


## Generator construction branches

The [implemented generator contracts](REALSIM_GENERATOR_PROGRESSION_AUDIT.md#implemented-generator-contract--2026-10-14) retain parallel workshop branches: external heat, early steam/precision turbines, liquid-fuel piston generation, gas-turbine/HRSG plants, isotope/environmental specialists and explicitly exotic machinery. Nuclear heat feeds the same steam-conversion branch. Early Stirling/steam-engine construction uses ordinary steel pressure/mechanical hardware, pistons and copper heat surfaces/windings. Precision turbine rotors, welded pressure structure, larger shafts/generator hardware, compressor/hot stages and heat recovery justify later plant construction; extra gold, beryllium or boards are not generic advancement tokens. Beryllium's Leviathan flywheel and magnetic generator material retain their specialist functions.

Existing Anvil, Foundry/press, Arc Welder, Soldering/component and Fabrication routes remain authoritative. The shared Fabrication/Industrial Fabrication recipe pool has not become an exclusive capability gate. Generator recipe overrides and 528-dependent construction branches remain available; no generator-specific manufacturing machine is introduced.


## Implemented fluid chemistry and machine operation — 2026-10-14

The [fluid correction table](REALSIM_PROCESS_INVENTORY.md#fluid-correction-implementation-table--2026-10-14) and [quantity contract](REALSIM_PHYSICAL_LEDGER.md#fluid-recipe-quantity-contract--2026-10-14-correction-pass) supersede old arbitrary fluid defaults for the listed released families. They also list remaining nominal exceptions; the broader audit is still in progress.

Chemical Reactor physical recipes use immutable mass/work/heat bills, at most 8 kWe and 100 kWth before the dry-cooler auxiliary. A temperature-qualified adjacent heater supplies heat; the electric heater can qualify up to 1600 C only when its heat input is actually qualified at that temperature. It does not turn a 900 C input into higher-grade heat. The small reactor has a 200-kW integral dry cooler. The eight-lane industrial machine uses the same per-lane chemistry and a shared 1-MW rejection budget, fair rotating admission, and a retained 20-L fresh-water cooling charge. It produces no new cooling steam. Eight lanes may be constrained by the common cooler; they do not each receive an independent 1-MW budget.

Reactor/factory feeds and products have four fluid ports per lane and existing item slots. Old input/output tank indices are retained; the additional ports are appended. Multiple solid feeds, fractional products, excess reagent, return water and paid recipe snapshots remain owned until outputs fit. External tanks supply long-term autonomy. Physical process tanks resize to one batch or 60 seconds at that recipe's rated flow, whichever is larger. Existing over-cap contents remain stored and drain normally, while new filling stops at the current cap. Loaded paid batches continue their saved bill after recipe/config reload; unpaid nominal progress is reset where a changed recipe could reinterpret it.

The native electrolyser has separate physical fluid and nominal mineral lanes. Fluid recipes pay their declared work, retain all coproducts and cannot use speed/efficiency upgrades to underpay the bill. The mineral lane retains a 20-L nitric-acid bath rather than destroying 100 L per ore batch; this is a circulating process abstraction and does not certify the remaining mineral solid-reduction chemistry. Water washing in the crystallizer returns its carrier and blocks on a full return tank. Its acid/organic families remain listed exceptions pending individual correction.

Water treatment is 10 kg raw -> 9 kg fresh + 1 kg geological mineral brine per 50 s. Manufactured brine is 31 kg fresh water + 1 kg salt. Mineral-brine separation, rather than heating pure salt brine, supplies iodine/bromine-bearing liquors. Iodine, KI and iodate medical reagent packets use a declared 10-g assay. The early salt-to-chlorine route remains in the Chemical Reactor, at the same stoichiometry as native brine electrolysis. Water electrolysis produces standard gases; the old unsuffixed H2/O2/N2 fluid IDs remain cryogenic liquids.

Default paper pressing uses 1 L water per kilogram of fiber and pays drying work; its evaporation is an explicit environmental steam discharge. Drinks retain carrier water, and tea rejects spent botanical solids explicitly. Concrete retains one kilogram of water per four half-kilogram binder parcels; this is a binder/water contract, not a claim that a construction block's geometric volume is SI-calibrated.

Fluoride fuel chemistry and the existing game cascade retain their registry IDs and packet routing. Fluorine is recovered, isotope fractions accumulate in saved mass accounts and mixed Pu feed cannot manufacture a separate Pu238 item. The cascade's adopted module-work model is deliberately a game abstraction rather than real facility design. Appended process fluids/items reuse the existing fluid tint/powder icon system and retain every earlier fluid constructor/meta-order position.

Enabled legacy recipe JSON for released routes must carry a physical bill and be regenerated or deliberately migrated; an obsolete arbitrary-count recipe is rejected instead of silently restoring a mass/energy shortcut. Custom consumer recipes can carry the same `physical` bill, including saved paid inputs/outputs. Their native port dimensions and work rate must fit the configured machine. Physical consumer bills cannot be inverted into generator recipes. Existing unrelated nominal pack extensions remain available and are not asserted to be physically certified.


Electrolysis reserves a one-litre water batch but releases gases in proportion to paid cell work. First gas therefore becomes available within seconds instead of waiting approximately 46 minutes for the early 8-kW machine to finish that whole litre. Total throughput and 55 kWh/kg-H2 work remain unchanged. Saved bills record released progress and species remainders; reloading cannot release the already exported part again. Older saved bills without this flag retain their original whole-batch ownership semantics.


Fresh geological gas now requires separation: pipe `RAW_NATURAL_GAS` into the ordinary Chemical Reactor or cryogenic separator, then send recovered `GAS` (pure methane) to synthesis/clean-gas machinery and helium to its own storage. Update fluid filters accordingly. Old stored methane remains methane, remains drainable and is not converted or rescaled. Raw gas can still supply the drill's afterburner/heating role from its actual lower mixture LHV; it is not automatically admitted as prepared reciprocating/turbine fuel. Gas extraction commits both products together; blocked coproducts cannot be vented/clamped implicitly or deplete a deposit. Fracking only injects slurry after successful paired extraction.


Native melting and freezing recipe overrides now carry the same physical bills used during operation; disabled recipes stay disabled, the exported quantity matches the mass-based phase conversion, and old count-only overrides cannot restore free phase changes. Sodium/potassium cooling returns the declared kilogram parcel. The solidifier no longer turns red mud into iron without a reduction step or slop into oil sand. Unsupported old stock remains drainable through the existing fluid ports.

Atmospheric extractors collect only compatible natural planetary gas, with separate bulk (300 standard L/min, 5 kWe) and selective (60 standard L/min, 50 kWe) duties. A persisted admission budget forbids multiple captures in one tick or reloading to repeat that capture. Old filled gas is never relabelled or erased when an atmosphere changes or the machine enters vacuum; it can still drain. Scrubber capture is bounded by positive storage space, request and paid electricity; over-cap legacy stock cannot turn into negative capture.

Soldering uses 1–4-L retained solvent baths for ordinary cleaning, with trace filter servicing abstracted. Quantum assembly uses a vented four-standard-litre He4 purge, rather than pretending the standard gas is a cryogenic liquid. The specialist He3 controller takes one standard litre as a sealed fill. `hbmSoldering.json` writes `retainFluid`; old solvent entries default to a retained cleaning bath, and a deliberately overridden consumptive utility must declare `retainFluid: false`. Welding uses argon shielding and flow-sized reducing/test gases. These gas utilities are declared environmental discharges; they provide no recoverable heat or fuel credit. Assembly/welding electrical work has not yet been recalibrated against every solid form.

Fabrication tanks hold a batch or about one minute at the recipe's unupgraded flow, with a 32-unit technical minimum. Filled legacy tanks retain all contents and export displaced/over-cap stock through the existing ports. Fluid identifiers now select empty tanks and cannot erase a filled tank. Soldering/welding save their recipe accounting identity: changing a recipe resets partial work before any material commit. These machines pay materials on completion, so this reset preserves all reserved-in-inventory solids and fluids; already spent work is not refunded. Unidentified old partial progress is also reset conservatively.


The rotary furnace now treats ordinary steam as drum/blower mechanical service, rather than high-temperature furnace heat. Defaults consume 1 or 2 ordinary counts/tick (0.2/0.4 kg/s), delivering an adopted 20/40 kW shaft through 40%-efficient ledger expansion. Fractional spent steam and the admission tick are persisted in `realSimThermal`; the shaft work is consumed internally and cannot also become electricity or furnace heat. A blocked spent outlet stops further admission. Combustion now uses one persisted physical fuel credit at 85% useful efficiency, a 500-kW ceiling and an adopted 1.8-MJ/kg-output-equivalent heat bill. Removed variant multipliers cannot turn the same iron feed into two-to-four steel outputs. Solid ore/reductant/gangue chemistry remains an abstraction. Old remaining burntime becomes a one-time persisted 500-kW-nameplate heat voucher, never a second renewable fuel credit. Overrides must specify 1–4 ordinary counts/tick; obsolete 100/400-count mechanical-token values are rejected. Saved owned steam stays owned even when a partial solid recipe resets after recipe changes.

Potassium fertilizer recovery now processes the assayed liquor and recovers its carrier: five litres of 20-wt% KCl solution yield 1.13 kg dry nutrient salts and 4.52 kg water in 150 s. The generic fertilizer item is a 250-g nutrient-salt dose for these routes. Sulfate packaging yields four such doses per kilogram. Ammonium/sodium nitrate exchange similarly uses a five-litre KCl batch, preserves excess KCl and water, and emits the corresponding ammonium chloride or salt coproduct by mass. Both crystallization variants pay 10 MJ thermal duty; fertilizer evaporation/condensation pays 12 MJ. They require a qualified 120 C heat source. Nitre-bed templates 1143/1144, which granted eight nitrate items without a mineral/nitrogen assay, are unavailable; the existing chemical exchange routes supply niter. Old saved template items retain their IDs. Recipe overrides can replace retired templates only with an explicit physical bill.

Reactor JSON loading validates each candidate before inserting it into either the recipe list or template-ID map. A rejected output, missing bill, excessive port count or unsupported physical item adapter therefore cannot leave a usable half-registered recipe behind.


Crystallizer efficiency upgrades now save 5% electrical demand per level, up to 15%, and consume the full solid feed on every successful batch. The former extra-acid/free-solid lottery is removed. Reagent amounts are unchanged by that upgrade. Acid baths are 5 L and organic solvent baths 2 L, retained with integrated regeneration and at least 60-second cycles. Saved recipe identities prevent old partial work from completing a changed recipe; revised tank capacities retain legacy contents and allow displaced stock to drain. This fixes material duplication and buffer/reload behavior without certifying remaining acid/organic leaching bills.


### Acceptance still required for this fluid correction pass

No gameplay or client/server connection validation is asserted by the automated build.

- Establish raw-water supply, first treatment, boiler steam, returned condensate, starter hydrogen/oxygen, acid and polymer production using the documented small-machine rates. Confirm that first useful gas appears before a whole electrolysis batch finishes.
- Bootstrap petroleum from an existing save and a new plant: route newly extracted raw gas through separation, obtain first diesel/lubricant, and check refinery/cracker/coker/hydrotreater/reformer backpressure and upstream supply. Existing pure methane must retain its identity and use.
- Exercise all eight chemical-factory lanes with mixed heating/rejection loads, one retained cooling charge and a blocked coproduct. Check fair progress and the shared cooler ceiling without continuous water consumption.
- Save/reload active paid batches, reload recipes while processing, block and unblock every coproduct, and drain old over-cap/incompatible stock. Check progressive gas/fractional spent-steam ownership and verify that changing an identifier never deletes stock.
- Operate atmospheric extraction in a compatible natural atmosphere and in vacuum/artificial life-support environments; recover legacy gas without new incompatible production. Verify retained soldering solvent, welding purge supply and rotary mechanical steam return.

The released graph includes every canonical Chemical Reactor and mixer fluid bill plus explicit prepared-plant/mined/crafted/biological roots. It checks recipe reachability, not native machine construction or in-game progression. Crystallizer concentrated-solid assays, mineral-electrolyser reduction, solid furnace chemistry and reactor burnup/heat-generation models remain explicit physical limits.

### Fluid follow-through operating and compatibility contract — 2026-10-15

The default Chemical Reactor has no remaining volume-only fluid templates; all default mixer fluid routes also use the common process bill. The [implementation table](REALSIM_PROCESS_INVENTORY.md#remaining-fluid-families-implementation-follow-through--2026-10-15) groups the corrected construction, specialist, organic, nuclear-carrier, food and utility families. The industrial reactor uses the same bills per lane and one shared cooling ceiling; it does not change chemistry or debit water every lane/tick.

Construction consumes weighed coarse/fine aggregate bags (100 kg each, nineteen bags per adopted sand/gravel source block) and 0.5-kg cement parcels. Concrete uses w/c 0.5: 32 kg cement, 200 kg aggregate and 16 kg raw water yield 248 kg compressed construction parcels. Asphalt draws only 3 L/min bitumen. New specialist oxide, fluoride, residue and organic precursor items use existing native item registries and presentation. Templates 1182–1199 supply the needed separate reduction, carrier recovery and precursor routes; the instant tritium-decay template is unavailable. Stored template IDs and old fluid counts are retained.

The milk reformer now uses the common saved paid batch, admits no catch-up duplication, requires qualified 120-C heat and emits recovered water through a fifth pipe tank. The original four tank indices remain unchanged. All coproducts must have space. New world-collected water/milk buckets are stamped 10 L, water bottles 1 L, including dispenser water collection. Untagged old containers still decode their historical quantities; existing tank NBT is neither rescaled nor clamped.

Hot water/alcohol coolants keep their cold-reference mass per count and carry paid 20-to-325-C sensible heat. The boiler uses the rated physical process source rather than nominal HU for these four coolant families. The exchanger owns a hot parcel's recoverable heat and cold return in NBT until both can be delivered; it cannot release the same heat twice or use a blocked return to delete water. This defines the coolant transfer boundary, not a calibration of nuclear heat generation.

Additional manual acceptance: operate the milk reformer with the condensate outlet blocked, run a hot-coolant loop across save/reload, acquire new and old untagged world containers, obtain weighed concrete/asphalt, and produce the first optical/fluoride/organic precursor with qualified heat. Check the one-litre He3 assembly fill, graphite arc throughput and irradiated lithium spent-target exclusion. No runtime/UI/multiclient validation was performed for this pass.
