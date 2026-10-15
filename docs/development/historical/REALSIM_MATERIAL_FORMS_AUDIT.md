# RealSim material forms, processing, and energy-source audit

> **Historical evidence.** Preserved incoming investigation and implementation record; values describe its recorded stages. For current behavior use the [current reference](../material_reference.md).

**Discovery snapshot:** 2026-10-14, before the subsequent material implementation pass, including the already-present industrial blast furnace changes. **Mode of this snapshot:** discovery and documentation only.

**Current implementation:** [RealSim material roles and forms](REALSIM_MATERIAL_IMPLEMENTATION.md) supersedes the affected historical rules below. ID collision, MB01/MB02/MB03/MB05, physical fuel omissions/coke identity collapse, native refining bypasses, misleading generation flags, the bismuth metadata branch and ignored CRUMBS multiplier have been corrected. MB04's conservative salvage loss remains intentional pending evidence of a different contract. Historical counts, recipes and findings below are preserved as discovery evidence, not current acceptance/value tables.

## 1. Executive summary

RTM has an explicit **foundry quantity model**, but not a canonical physical material model shared by all processing. `MaterialShapes` defines quantities in integer quanta; `Mats` maps some OreDictionary identities into that model. Crafting, chemistry, fuel handling, nuclear fuel, fluid metals, and several machine generators remain independently authoritative. A quantity in this model is an ingot-equivalent accounting unit, not a universal mass in kilograms.

The most consequential findings are:

- **Identity corruption:** red phosphorus and magnesium both use material ID `1500`. Metadata forms, scraps, and saved foundry material IDs cannot distinguish them.
- **Definite stock quantity defects:** stainless-to-HSS doubles the registered input; crafted steel pipes recover eight times their input; a three-metal-unit steel precursor becomes nine steel units; unmapped smeltable ores can be consumed for zero material. Custom blade/stamp casting also disagrees with salvage, but whether its loss is intended remains unresolved.
- **Fuel acceptance is fragmented:** firebox/heating oven use `Combustion`, not the much broader `FuelHandler` list or their displayed legacy burn modules. Several intended fuel forms are rejected; different coke grades collapse to the generic coke energy identity.
- **Dust is not chemically consistent:** the same iron powder is obtained from metal shredding and ore separation, melts as metallic iron, smelts into iron, substitutes for an ingot, and participates in thermite and steelmaking abstractions.
- **Existing beneficiation infrastructure is substantial:** old bedrock stages, a newer graded bedrock representation, leaching/crystallization, separation, concentrates, solutions, roasting, slag and scrap already exist. The newer bedrock pipeline lacks a stock source for its required base item in this source snapshot.
- **Generation flags are not registrations:** `setAutogen(DUST, NUGGET, BLOCK, PLATE, BILLET)` does not create those forms. Tin and several isotope materials illustrate the gap.

### Counts and their meaning

| Measure | Result and counting boundary |
|---|---|
| Registry material identities audited | **175** distinct primary names across concrete `DictFrame` definitions and inline Mats frames; duplicate `ST`/`SR` collapsed, nine collection/helper frames excluded. Includes declared identities without registered forms. Non-registry compound intermediates are additionally covered below, not miscounted as new elements. |
| Foundry material records | **125** Mats declarations: 96 `makeSmeltable`, 20 `makeNonSmeltable`, 5 `makeAdditive`, 4 `make` with conversion. Only **124 distinct numeric IDs**, because of `1500`. |
| OreDictionary frames inspected | **179** `DictFrame` declarations, plus `DictGroup` substitution collections and direct registrations. |
| Useful shared processing/behavior families | **15**, F01–F15 below. These are overlapping common-rule families with explicit exception modifiers, not a claim that every member has an identical entire lifecycle. |
| Automatic registration/conversion cohorts | **82** candidate signatures from explicit ODM form calls, implemented metadata generators, and Mats conversion behavior. These are **not** 82 proven whole-system equivalence classes; manual recipes split them further. |
| Named core form descriptors | **31** `MaterialShapes` constants, including technical `ANY`, `QUANTUM`, and `QUART`; **15** additional GT6-only shapes. |
| Ore process-state descriptors | **26** newer bedrock grades × 6 ore types = **156** item variants; **10** old bedrock stage items × 19 types = **190** variants. Together with shapes, **82 named shape/state descriptors**; not 82 distinct chemical substances. Other chemical, fuel and nuclear states are catalogued separately. |
| Prioritized exception materials | **32** explicitly named registry identities in §5; chemical intermediates and lower-priority exceptions are additional. |
| Crucible acceptance rules | **8** patterns, C01–C08. |
| Foundry/casting rules | **6** patterns, K01–K06, including dynamic slag deposition. |
| Definite conservation defects | **4 grouped stock defects**, MB01/MB02/MB03/MB05; source-proven arithmetic/control flow, not reproduced in-game. MB04 is a confirmed quantity disagreement with unresolved loss intent and is not included. |
| Significant acceptance inconsistencies | **10 grouped findings**, A01–A10, including three fuel findings. |
| Dead/legacy forms | **190 reachable old bedrock variants**; **8 registered legacy standalone wires with no active source/use found**; **10 registered heavy-component variants without an active stock producer/consumer**; **157 source-disconnected newer bedrock variants/base item**. Source-disconnected items are candidates, not proof of modpack-wide unreachability. One unused three-material indexing implementation. See §16. |

### Method and limits

The audit searched the repository's production Java, resources, documentation and source provenance, then followed registration and recipe consumers. Comment-aware temporary analysis enumerated Mats/DictFrames, registered form calls, implemented metadata generation, recipe-system form references, material IDs, ore enums, and shared signatures. Temporary analysis ran outside the repository; no test harness or permanent audit tooling was added.

Production is `src/main/java` and `src/main/resources` under the existing Forge 1.7.10 build. `DONOTMODIFY/hbmspace/` is a separate reference tree, not the current runtime: similarly named forms/recipes there do not prove production registration. `build/`, IDE outputs, logs, caches and old changelogs are not current authorities. The inventory scan covered **3,658 production Java files**; this is a search-coverage count, not a claim of manually reading every file. [BUILD]

This report describes **stock source rules**, not an executed recipe registry dump. Forge's vanilla entries, external mods, configured fluids, IMC and enabled `config/hbmRecipes/*.json` can change results. `_hbm*.json` templates do not override defaults; enabled files replace each handler's defaults, followed by `registerPost()`. There was no build, Minecraft launch, runtime recipe enumeration, multiplayer test, or mass/energy recalibration. [LOAD]

## 2. Material system architecture

| Authority | What it owns | What it does not establish |
|---|---|---|
| `ModItems`, `ModBlocks`, enum/subitem classes | Registered item/block identities, metadata and NBT states | Chemical equivalence or consistent mass |
| `OreDictManager.DictFrame` | Material suffix aliases and form-key registrations | A form's obtainability, density, composition or every machine's acceptance |
| `DictGroup`, ore registration callback | Deliberate substitution groups: plastics, rubbers, resistant alloys, bismoid bronzes | A common foundry material identity; group keys are not Mats alloy IDs |
| `MaterialShapes` | Quantity and recognized prefix for a form | Actual existence of an item or the chemical interpretation of dust |
| `Mats` / `NTMMaterial` | Foundry IDs, names, traits, conversion, shape generation flags | Every chemical material, fuel, isotope or compound |
| `MatDistribution` | Explicit ore mixtures and salvage compositions; `hbmCrucibleSmelting.json` | A universal ore assay, or a universal override of every alias |
| `ItemAutogen` | One registered item with material ID metadata and selected creative subitems | An unrestricted material × form Cartesian product |
| `ItemMold` | Casting shapes, costs, fixed outputs and native block overrides | Alloy decomposition, fluid-metal conversion or material temperature |
| `Fluids` / `FluidType` | HBM process fluids, container identities and traits | Foundry material identity/quantum quantity |
| `PhysicalRegistry` / `ProcessRecipe` | Selected adopted mass/LHV/phase assays and mass-accounted chemical fuel bills | A kilogram mapping for all 125 Mats entries |
| `FuelHandler`, `ModuleBurnTime`, `Combustion`, machine-specific fuel methods | Different actual fuel acceptance/value systems | One universal meaning of “burnable” |
| Nuclear item/rod/pellet classes and recipes | Enrichment, depletion, irradiation, decay, reprocessing and reactor-specific fuel | Interchangeability with bare metal nuggets/ingots |
| `HbmMaterials` | Unused IRON/GOLD/STEEL double-valued indexing scaffold | Active canonical material accounting |

Initialization matters: `MainRegistry` installs groups before ODM registrations; stock crafting/smelting registration is separate; `SerializableRecipe` runs `MatDistribution` before arc-furnace generation. SILEX, gas centrifuge, anvil, magic and radiolysis have separate registration paths. Blast furnace, crystallizer and centrifuge IMC are independent extension boundaries. Assembler defaults also reflectively invoke `com.wartec.wartecmod.inventory.wartecmodAssemblerRecipes.AssemblerRecipes()` when that external class is available; its recipes are not present in this checkout. [INIT] [ODM] [LOAD] [INDEX] [ASSEMBLER]

`Mats.getMaterialsFromItem()` treats a stack as **one item**, chooses the **first resolvable ore key**, and then **adds** an exact item/metadata distribution. It is not simply an item override table: an item carrying both a generic material key and a custom salvage mapping can accumulate both. `getSmeltingMaterialsFromItem()` then applies `convOut / convIn`, with integer truncation. NBT is ignored by `ComparableStack`; scraps are decoded separately from their NBT quantity. [MATS] [MATCH] [SCRAP]

## 3. Common material form families

Families below group **implemented common rules**. A metal sharing the melt rule does not thereby share another metal's ore yield, shape inventory or chemistry. Read the exception matrix and the generated form-support inventory together.

| Family | Members / exact inclusion rule | Shared behavior | Required exceptions |
|---|---|---|---|
| F01 Identity metallic solids | Mats materials made smeltable with self 1:1 conversion and metal trait; ordinary metals, registered isotope metals and alloys | Recognized, registered form prefixes give shape quanta; arc generator supplies molten routes for supported shapes | Shape support, explicit ore mixtures, isotope states, casting overrides and manually gated powders differ; §§5,8–12 |
| F02 Identity molten nonmetals | Stone, obsidian, redstone, borax, asbestos, slag; also nominal nonmetal weapon materials where marked smeltable | Foundry identity quantities can exist even without realistic metallic chemistry | Ordinary stone/cobble explicit block quantity; asbestos/borax arc chemistry; slag vs stone kept distinct |
| F03 Crucible additives | Carbon, hematite, malachite, conglomerate, flux | Accepted as reaction material; additive flag is not ordinary castability | Explicit ore/flux definitions and reaction outputs; pure carbon is an additive, not a castable ingot |
| F04 Carbon-converting feed | Coal 2:1, lignite 3:1, coal/lignite/petroleum coke 4:3, graphite 1:1, diamond 1:1 | Conversion targets `MAT_CARBON`; prefix quantity is scaled and truncated | Diamond is not retained as molten diamond; charcoal/ash use explicit mappings; coke pressing disagreement |
| F05 Extraction-converting gem | Emerald | 4:3 conversion into beryllium, using registered form quantity | It is an extraction abstraction, not neutral remelting of emerald |
| F06 Non-melting minerals/organics | Nonconverting NOT_SMELTABLE Mats: sulfur, saltpeter, fluorite, red phosphorus, chlorocalcite, molysite, cinnabar, sodalite, rare earth, wood/bone, polymers/rubber | Generic material-prefix melting is rejected | Chemical/furnace/press routes remain active; no universal mineral melting recommendation |
| F07 Simple ore grinding/recovery | Materials with a non-scrap native `dustX`; shredder's generated ingot/plate/ore rules | One ingot/plate → one dust; ore → two dust; approved raw gem/crystal → one dust | Raw/storage allowlists, explicit recipes first, native-output restriction, nuclear and mineral branches |
| F08 Acid-assisted ore crystal recovery | Explicit ore/mineral inputs in `CrystallizerRecipes`; crystals in shredder/centrifuge/electrolyser | One solid plus one process fluid → one concentrated/crystalline solid, then richer recovery | Outputs and byproducts are manual; “crystal” is frequently an ore parcel, not one material unit |
| F09 Old bedrock ore stages | All 19 `EnumBedrockOre` types | Common stage graph, byproduct tiers and enriched ore registration | Bismuth/CAD metadata 10/11 special reagent branch; differing endpoint smelting and byproducts |
| F10 New graded mixed bedrock parcels | LIGHT_METAL, HEAVY_METAL, RARE_EARTH, ACTINIDE, NON_METAL, CRYSTALLINE | 26-grade graph, shared washing/roasting/separation generators | Six compositions differ; non-smeltable outputs cannot become molten; stock base source disconnected |
| F11 Chemical/mineral powder intermediates | Oxides, salts, sulfides, fluorides, concentrates, solutions, precipitates | Exact chemical recipes, often deliberately outside generic material prefixes | Each chemistry route is meaningful; no shared ingot-equivalent quantity established |
| F12 Fabricated material shapes | Materials whose autogen set contains implemented wires, bolts, shells, pipes, cast/welded plates, heavy components or gun parts | One registered item per shape; metadata holds Mats ID | Registered forms and recipes differ by flags; composite insulation/fasteners are not tracked by Mats |
| F13 Reactor/decay fuel and waste | Fuel nuggets/ingots/billets, RBMK/PWR/ICF/Watz/RTG pellets and rods, depleted fuel and waste enums | Specialized material/state identities and native consumers | Bare-metal melting rules cannot be generalized to fueled/irradiated compounds or assemblies |
| F14 Solid chemical fuel / reductant | Coal/charcoal, lignite, coke grades, briquettes, wood, biomass, solid fuel, tar/char/ash | Several physical and legacy energy consumers, plus reduction/carbon input | §13 gives the actual form/value/consumer split; material quantity and heat are independent |
| F15 Fluid/gas fuel and thermal carrier | Petroleum cuts, biological oils/alcohols, combustible gases, exotic propellants, steam/coolants | Fluid identity/traits plus machine-specific energy boundary | Resolved physical LHV vs legacy traits; steam/oxidizers are not chemical fuel |

The tightest automatically identified **registration** cohorts include U233/U235/RGP/Pu238/Pu239/Pu240/Solinium; Pu241/AmRG/Am241/Am242/Pb209/Ghiorsium/GaAs; and copper/aluminium. They still split on reaction consumers, actual ore distribution, fuel or isotope behavior. Bismuth/arsenic bronze share casting-form support but differ in constituents. These candidates are useful for generator documentation, not permission to merge material definitions. [MATS] [ODM] [CRUCIBLE] [MOLD]

## 4. Form ratios and quantity rules

### Foundry quantities

| Form | Prefix | Quanta per item | Relationship |
|---|---|---:|---|
| Quantum | none | 1 | 1/72 ingot; technical accounting unit |
| Nugget / tiny / bedrock fragment / tiny dust | `nugget`, `tiny`, `bedrockorefragment`, `dustTiny` | 8 | 9 = one ingot-equivalent |
| Fine wire / bolt | `wireFine`, `bolt` | 9 | 8 = one ingot-equivalent |
| Billet | `billet` | 48 | 6 nuggets; 3 billets = 2 ingots |
| Ingot / dust / gem / crystal / plate / dense wire | corresponding prefix | 72 | One unit when mapped to a Mats material; explicit recipes can override interpretation |
| Cast plate / pipe / light barrel | `plateTriple`, `ntmpipe`, `barrelLight` | 216 | 3 ingots |
| Welded plate / heavy barrel | `plateSextuple`, `barrelHeavy` | 432 | 6 ingots |
| Shell / light receiver / mechanism / stock | `shell`, `receiverLight`, `gunMechanism`, `stock` | 288 | 4 ingots |
| Grip | `grip` | 144 | 2 ingots, even for wood/polymer |
| Block / heavy receiver | `block`, `receiverHeavy` | 648 | 9 ingots |
| Heavy component | `componentHeavy` | 55,296 | 256 triple plates = 768 ingots |
| Quart | none | 162 | 2.25 ingots; accounting quantity, not a registered physical item |
| Ore / nether ore / ANY | `ore`, `oreNether`, technical empty prefix | **0** | Requires explicit ore composition; dangerous zero fallback, MB05 |

Common quantity evidence is `MaterialShapes` itself. `q(unitsUsed, itemsProduced)` performs integer division, so rails get 27 q each (`6×72/16`), while values such as four nuggets versus half an ingot can differ by rounding. These are registered quantity conventions, not measured masses. [SHAPES] [DIST]

`Mats.formatAmount(showInMb=true)` displays `amount × 2`: an ingot appears as 144 fluid-volume units. This is a **display relation**. Foundry networks move integer material quanta and IDs, not `Fluids.LEAD` or Forge FluidStacks. Fluid lead/sodium instead use **100 fluid units per solid unit**, potassium 800 out/1000 in, mercury 125 in per ingot. Do not apply 144 globally to chemistry. [MATS] [LIQUEFY] [SOLIDIFY]

### Verified common conversion generators

| Rule | Inclusion / exclusion | Evidence |
|---|---|---|
| Nine small units ↔ one larger unit | Explicit `add1To9Pair`, `addMineralSet`, same-meta waste/coke helpers; not all Mats automatically | `MineralRecipes.register`, helper methods [MINERAL] |
| Six nuggets ↔ billet; three billets ↔ two ingots | Explicit `addBillet` calls; isotope/fuel families, cobalt, silicon, bismuth, GaAs and others | `addBillet`, `addBilletToIngot` [MINERAL] |
| Nine generated bedrock fragments → first available dust/gem/crystal/ingot | Only Mats with FRAGMENT; primary suffix; billet fallback uses six fragments; missing target produces `nothing` | `OreDictManager.registerOres` final fragment loop [ODM] |
| Ingot → eight fine wires | WIRE autogen flag plus existing primary `ingotX`; press and tier-3 anvil | [PRESS] [ANVIL] |
| Four plates → shell; three plates or ingots → pipe | SHELL/PIPE autogen flags; pipe prefers existing plate key | `AnvilRecipes.registerConstructionRecipes` [ANVIL] |
| Two triple plates → one sextuple welded plate | Explicit metal-specific arc-welder rows; input 432 q = output 432 q | `ArcWelderRecipes.registerDefaults` [WELDER] |
| Eight fine wires + insulation → dense wire | Advanced alloy and gold only in assembler | `AssemblerRecipes.registerDefaults`, first dense-wire entries [ASSEMBLER] |
| Ingot/plate → one native dust; ore → two native dust | Shredder post-generation, native dust required; explicit recipe wins | [SHREDDER] |
| Owned approved storage block → nine dust, or four if no ingot/gem exists | `STORAGE_MATERIALS` allowlist; blocks with tile entities/unsafe names excluded | [SHREDDER] |

Plate formation is quantity-neutral for the explicit press list and three-ingot/three-plate assembler batches. Dense wire is quantity-neutral **for metal** but represents insulation as part of the same item; salvage does not recover that insulation. There is no universal rod/sheet/raw-ore/sinter/pellet quantity definition. [PRESS] [ASSEMBLER]

## 5. Exception matrix

The **32 prioritized identities** are: Iron, Steel, StainlessSteel, DuraSteel, Titanium, Tungsten, Copper, Aluminium, Lead, Tin, Nickel, Niobium, Lanthanum, Lithium, Zirconium, Neodymium, Bismuth, Cadmium, RedPhosphorus, Magnesium, Schrabidium, Chromium, Samarium, Gadolinium, Rhodium, Iridium, Coal, Lignite, CoalCoke, LigniteCoke, PetCoke, Potassium. Registry spelling/identifiers are retained in the evidence; this list uses readable names.

| Material(s) | Divergence from common rule | Significance / evidence |
|---|---|---|
| Red phosphorus / magnesium | Both Mats IDs = 1500; later magnesium constructor overwrites `matById` | Critical persistence and metadata identity defect [MATS] [NTM] |
| Tin | Autogen advertises nugget/dust/plate/block; implemented generators do not generate those; actual basic registration is ingot + ore | A02; generated triple/welded plates, shell/pipe/fragment are different, real forms [MATS] [ODM] |
| Schrabidium | Standalone ingot/nugget/powder items registered; SA326 ODM chain commented out; fine/dense wire and triple plate OD keys still generated | A01: inconsistent old/new support, not wholesale deletion [ITEMS] [ODM] |
| Chromium, samarium, gadolinium | Powder registered under dust suffix of smeltable Mats; generic crucible can bypass dedicated arc powder→ingot steps | A03; Gd uses `powder_gadolinium2`, not earlier precipitate [ODM] [ARC] |
| Rhodium, iridium | Raw powder has exact arc route; raw powder not registered as generic dust | Deliberately different from previous row; do not add dust aliases indiscriminately [ODM] [ARC] |
| Iron, copper, gold, uranium, thorium, lead, cobalt, titanium, tungsten, aluminium, beryllium, nickel | Explicit ore mixtures; common metal-prefix quantities do not describe ore yield | Main/trace/gangue differences in §10 [DIST] |
| Tin, niobium, lanthanum, lithium, zirconium, neodymium, bismuth, cadmium | Mats identities but no corresponding stock explicit ore-distribution key in `MatDistribution` | Examples include oreZinc as well; unmapped ore fallback is 0 q, MB05. Some ore forms are old enriched subitems [ODM] [DIST] |
| Steel / stainless / DuraSteel | Several conflicting synthesis ratios, HSS and carbon precursors | MB01/MB03; §11 [CRUCIBLE] [BLAST] [POWDER] |
| Titanium / tungsten; stone/iron/steel/titanium/obsidian stamps | Custom molds charge more than exact salvage mapping | MB04; confirmed loss/ratio disagreement, intent unestablished. Compare mold IDs 5 and 7 with `MatDistribution` [MOLD] [DIST] |
| Steel pipe assembly | Crafting outputs eight `pipes_steel`, while each is assigned 27 ingots in casting/salvage | MB02; not the 3-ingot metadata `pipe` form [CRAFT] [DIST] [MOLD] |
| Copper / aluminium / tungsten / other fine-wire materials | Press/casting use eight wires per ingot; old crafting recombination uses nine wires | Undocumented 1/8-ingot loss per recombination; separate from definite creation bugs [CRAFT] [SHAPES] |
| Coal / lignite / three coke grades | Different carbon conversions and physical fuel assays; modern burner matcher omits lignite and collapses coke types | A08–A10; §13 [MATS] [COMBUST] [FUEL] |
| Potassium | 800 fluid units per powder liquefaction, 1000 per powder solidification | 20% loss in an otherwise reciprocal form route; no automatic rebalance [LIQUEFY] [SOLIDIFY] |
| Lead / sodium | 100 fluid units per ingot/powder, versus foundry 72 q / display 144 | Separate architectures, not a proven duplication loop; no automatic bridge exists [LIQUEFY] [SOLIDIFY] [MATS] |

Additional meaningful exceptions: silver, mercury, antimony, manganese, molybdenum, cerium, scandium, yttrium, vanadium, praseodymium, palladium and several transuranic/fission products have OD forms but **no Mats material**. Emerald converts to beryllium, diamond to additive carbon. Calcium and calcined dolomite are marked smeltable despite chemical process meanings. `MAT_PLATNIUM`/`Platnium` is the actual key, not `Platinum`; preserve identity when later designing aliases. [MATS] [ODM]

## 6. Powder/dust families and semantics

| Powder family | Typical source | Consumers / melt behavior | Physical interpretation supported by code |
|---|---|---|---|
| Ingot-equivalent metal dust | Generated shredding of native/OD ingot or plate; explicit powder items | Furnace 1:1, metallic Mats 72 q, metallurgical substitution, fabrication | Metallic powder or generic one-metal-unit abstraction |
| Ordinary ore fines | Shredding ore; ordinary ore centrifuge; processing crystals | Same powder item as metal shredding for iron, Cu, Al, W, Pb etc | Ore concentration/recovery is already collapsed into an ingot-equivalent output; no retained gangue/oxide state in the powder |
| Alloy powder | Steel, red copper, advanced alloy, DuraSteel, Desh, combined steel, TCA etc | Synthesis, furnace/arc/crucible depending on registration | Some are finished alloy powder, others mixtures; do not infer from `powder_` |
| Carbonaceous powder | Coal/lignite/coke, sawdust, ash grades | Fuel systems, coke preparation, graphite, crucible additives, reduction, chemistry | Fuel assay/carbon fraction, not a metal ingot |
| Named mineral powder | Molybdenite, columbite, celestite, pollucite, zircon, coltan ore | Separation, thermal or chemical extraction | Ground mineral feed, with differing yields/byproducts |
| Oxide/sulfide/halide/salt | Antimony/arsenic trioxide, tellurium dioxide, strontium oxide/sulfide/chloride, rare-earth oxides/fluorides | Exact reduction/leaching/precipitation/electrolysis | Compound identities; usually deliberately not generic melt feed |
| Rare-earth concentrate/precipitate | Gd step/precipitate/late powder; Tb and Dy/Ho/Er/Yb/Lu/Tm concentrates | Crystallizer, centrifuge, chemplant, furnace, arc | Multiple grades and oxidation/reduction stages, not interchangeable metal dust |
| Reactor powder and waste | U/Th/Pu powders, yellowcake, isotope tiny powders, fission waste | Fuel preparation, SILEX, reprocessing, irradiation, specialized recipes | Bare metal, compound or waste according to exact item/state |
| Generic shredder dust/scrap | Unrecoverable items; dust inputs post-generate generic dust/tiny dust | Clay, recycling/legacy fuel | Material identity has been discarded; `dust` and `powder_iron` are not equivalent |

### Iron specifically

`powder_iron` is registered as `dustIron`. It is obtained from one iron ingot/plate (shredder), one iron ore yielding three powder in the centrifuge, and a crystal yielding three powder in shredder or four powder plus byproducts in centrifuge. The furnace returns one iron ingot per powder; the crucible returns 72 q metallic iron; the Metallurgical Furnace expands IRON into ingot/plate/gem/dust alternatives; the new blast furnace explicitly accepts `dustIron`. Thermite crafting also uses three `dustIron` plus aluminium dust, while steel precursor crafting uses the same iron powder. Thus it functions as **metallic iron powder, recovered ore metal, generic iron unit, and oxidation/reduction abstraction in different contexts**. No separate iron oxide or iron concentrate identity is enforced along that common route. [ODM] [SHREDDER] [CENTRIFUGE] [SMELT] [MATS] [BLAST] [IBLAST] [POWDER]

### Chemical examples that must remain separate

- `ChemRecipe` **46 `YELLOWCAKE`** consumes four exact `powder_uranium`, not arbitrary ore/ingot forms. Later fuel chemistry is distinct from metal remelting.
- **1026 `CALCINED_DOLOMITE`**: one magnesite ore → one calcined dolomite + 500 CO2; **1027 `MAGNESIUM_REDUCTION`**: one calcined dolomite + one silicon ingot → one magnesium ingot + one slag ingot. Crucible magnesium recipe **22** uses a different 2-ingot-equivalent dolomite and 2-nugget silicon bill.
- **1029 `MOLTEN_STRONTIUM_CHLORIDE`**, **1030 `STRONTIUM_CHLORIDE`** and fluid electrolysis separate oxide, salt, liquid salt, chlorine and metal powder.
- **1056 `RHODIUM_EXTRACTION`**, **1078 `GADOLINIUM_PRECIPITATION`**, **1079–1081** terbium separation/oxidation/fluoride and the nearby lanthanide routes encode real distinctions that a generic `dustX` alias could erase.

These quantities are gameplay batches unless an explicit physical bill establishes more. Do not label the different chemical yields as material creation solely by counting items. [CHEM] [EFLUID]

## 7. Nugget families

| Pattern | Members / rule | Obtainability and use |
|---|---|---|
| Standard 1/9 solid unit | Explicit mineral-pair calls and registered metallic `nuggetX`; generated foundry shape quantity 8 q | Crafting compression/decompression, mold 0, residues, isotope/ore byproducts, fabrication |
| Six-small-unit billet | Explicit billet families; radioactive/fuel billets, cobalt, bismuth, silicon, GaAs, others | Three billets = two ingots; molds 1/2 reciprocal where both forms actually registered |
| Fragment registered as nugget | Legacy Co/Nb/B/La/Nd/Ce/Y/V and related fragments in ODM | Prefix can make a fragment chemically equivalent to a nugget; not every fragment is a generated bedrock fragment |
| Generated bedrock fragment | Mats FRAGMENT flag, `bedrock_ore_fragment:<mat.id>` | 8 q; nine-to-dust/gem/ingot generation, six-to-billet fallback |
| Reduction residue nugget | Arsenic/antimony/tellurium and trace byproducts | Exact recipe meaning; antimony oxide is aliased as antimony dust, not ordinary metal powder |
| Isotope/fuel/waste small states outside Mats | Cm/Bk/Cf/Es, fuel mixes, fission-product tiny dust, depleted waste enums | Crafting/reactor/chemical routes can exist without any crucible or nugget-casting support |

No common Mats rule gives a nugget 72 q. The core nugget is correctly 8 q. The more important nugget issues are missing Mats identities, unregistered autogen expectations, old fragment aliases, and compound↔nugget routes. Shredder generation has **no general nugget→material dust rule**; unknown inputs become generic scrap. Nuclear SILEX `.addOut(stack, weight)` numbers are **weights**, not simultaneous product counts: a 900-unit feed is processed in 100-unit increments, one selected output per increment. Uranium's weights 2/10 do not mean twelve nuggets per ingot. [SHAPES] [MINERAL] [ODM] [SHREDDER] [BLAST] [SILEX] [SILEXTE]

## 8. Crucible acceptance patterns — high priority

| Pattern | Acceptance/quantity rule | Members and boundaries |
|---|---|---|
| C01 Registered identity smeltable prefix | Registered prefix + a Mats name whose target is SMELTABLE → `shape.q(1)` | All applicable identity smeltable Mats; availability table below, not autogen declarations alone |
| C02 Registered additive prefix | Target ADDITIVE → shape quantity | Carbon and any actually registered additive forms; not automatically castable |
| C03 Converted prefix | Shape quantity × `convOut/convIn`, integer truncation | Coal, lignite, three coke grades, graphite, diamond → carbon; emerald → beryllium |
| C04 Explicit ore mixture | `materialOreEntries` for first applicable name | Named stock ore distributions; primary + trace + gangue or additive feed |
| C05 Exact salvage item | Adds `materialEntries` for singular item+metadata | Rails, minecart, blades/stamps, pipe assembly, casings, flux, charcoal, ash; can add to OD-derived amount |
| C06 NBT scrap | `scraps` metadata → Mats ID; NBT `amount`; absent NBT defaults to 72 q | Not generic `scrap`; preserves arbitrary fractional quantity, then conversion is applied |
| C07 Rejected semantic material | Target neither SMELTABLE nor ADDITIVE | Nonconverting F06 forms; physical form registration alone is insufficient |
| C08 Unresolved/zero fallback | Unknown Mats suffix → no material; known smeltable ore prefix without explicit mapping → **0 q list entry** | Unknown materials rejected; zero-valued known ores can be consumed, MB05 |

`noAutogen` is **not a runtime acceptance blacklist** in `Mats.getMaterialsFromItem()`. The crucible recognizes prefix mappings, including ore prefixes and GT6 shapes, while the NEI melt recipe generator skips `shape.noAutogen`. Some actual acceptance therefore has no generated NEI melt entry. [MATS] [SHAPES] [CRUCIBLE]

`TileEntityCrucible.isItemSmeltable()` checks nonempty material list, loaded-template membership/ratio caps and buffer capacities. With no template, material enters the waste stack. “Waste” is a buffer role, not necessarily slag: ordinary melted iron may be in it. With a template, required ingredients and output materials go to the recipe buffer; unrelated material can go to the waste buffer only when another component satisfies recipe membership. Additives reserved for a reaction should not be confused with drainable finished melt. [CRUCIBLETE]

Other boundaries:

- Alias suffixes are honored by `matByName`; **not all OreDictionary material names have a Mats entry**.
- External forms can melt when their prefix/suffix is recognized; their actual recipe mass is not verified. A foreign `plateIron` is assigned 72 q regardless of its manufacture cost.
- Only the first resolvable ore key is used; aliases with contradictory mass interpretations are not combined or reconciled. Exact salvage is subsequently additive.
- Metadata-specific distributions preserve the named variants, but `ComparableStack` does not include NBT. Special NBT scraps work because they bypass that assumption; generic tagged tools/components do not have a composition model.
- `setAutogen` mostly governs item/shape generation, not material recognition; manually registered powder can melt even when DUST was not selected for generation.
- Alloys melt to their **alloy material ID**, not their constituents. Fixed composition is encoded only in the forward synthesis recipes.
- Integer conversion can lose fractional remainder. No conserved remainder account exists in the generic material conversion path.

## 9. Foundry and casting patterns

| Pattern | Shared rule | Examples / exceptions |
|---|---|---|
| K01 OreDictionary shape mold | Find `shape.name()+material alias`; prefer HBM item, otherwise first external entry; cost `shape.q(outputCount)` | Nuggets 8, billet 48, ingot/plate 72, wire batch 8×9, triple plate 216, dense wire 72, shell/pipe/gun parts |
| K02 Block mold with override | K01 except native stone/obsidian outputs | Basin mold 12 charges 648 q for stone/obsidian; generic material block otherwise |
| K03 Single material/output | Exact Mats object plus output/cost | Pipe assembly mold 13: 1944 q → one `pipes_steel`; copper casing molds 14/15/18 |
| K04 Explicit multi-material output map | Mold ID maps materials to named item at one common cost | Blade mold 5, rotor blades 6, stamp 7, casing 16/17; MB04 exceptions |
| K05 Continuous strand caster | Same ItemMold output/cost, repeated casting with water/cooling and buffer limits | Uses installed small mold; dynamic costs and output slots. Does not imply every basin recipe is supported |
| K06 Slag-tap deposition/recovery | Material-bearing dynamic slag tile, amount retained; same-material accumulation | Not only MAT_SLAG; retained material can be recovered as scraps. Separate from solid slag item/block recipes |

There are **29 registered mold IDs (0–28)**. Shared batch molds produce nine ingots, nine plates or nine dense wires for nine times the unit cost. There is no generic dust mold, welded-plate mold, heavy-component mold, ore mold, pellet mold, or sinter mold. Nugget casting does exist. A material with molten support but no registered ingot/nugget/plate/block key will have no output for that shape. [MOLD]

Channels, tanks and outlets transfer a single Mats type and quantity; they do not mix alloys. Crucible and large arc furnace hold multi-material lists; the electrolyser's left/right lanes have separate roles. Multi-material output selection rearranges/selects existing material stacks, not their composition. Foundry save data stores `type` ID and `amount`, so the ID collision is a save-compatibility problem. [FOUNDRY] [CRUCIBLETE] [ARCTE] [ELECTE]

Small casting blocks require an installed mold, correct output, exact filled cost and an empty output slot before cooling completes. Shovel recovery and breaking the block explicitly create quantity-bearing scraps. `normalizeCastingBuffer()` clamps excess to the currently installed mold capacity without refund; this is a separate lifecycle risk if configuration/installed mold state changes. The normal hand interaction does not expose unrestricted replacement of a filled mold, so this audit does not count the clamp as a demonstrated ordinary gameplay loss. Strand-caster overflow instead emits scrap. [CASTTE] [CASTBLOCK] [STRAND]

## 10. Ore processing families

### Ordinary ore recovery (not automatically a conservation bug)

| Group | One ore → crucible contents | Centrifuge / grinding comparison |
|---|---|---|
| Cu, Ni, Be | 2 ingots main + 1 block stone | Cu: 2 Cu dust + Cd dust + gravel (LBSM 3 Cu); Ni: 2 Ni dust + Fe dust + gravel; Be: 2 Be dust + emerald dust + gravel |
| W, Al | 2 main + stone block; disabled when GT6 loaded | W: 2 W dust + Fe dust + gravel (LBSM 3 W); Al: 2 Al dust + Fe dust + gravel |
| Fe, Ti | 2 main + 3 trace nuggets of the other metal + stone block; disabled under GT6 | Fe: 3 Fe dust + gravel; Ti: 2 Ti dust + Fe dust + gravel (LBSM 4 Ti) |
| Au, U, Th | 2 main + 3 Pb nuggets for Au/U; Th gets 3 U nuggets; all + stone | Au usually 3 Au dust; U usually 2 U dust + Ra nugget; Th 2 Th dust + U dust; LBSM changes selected products |
| Pb | 2 Pb ingots + 1 Au nugget + stone block | 2 Pb dust + Au dust + gravel, or LBSM Pb/Bi variant |
| Co | 1 Co ingot + stone block | 2 Co dust + Fe dust + Cu dust + gravel |
| Coal / redstone | 3 carbon units / 4 redstone units + stone block | Coal: 6 dust + gravel; redstone: 6 redstone + mercury + gravel |
| Hematite / malachite / conglomerate | Additive 1 / 1 / 4 ingot-equivalent units | Template refining differs; named mineral furnace/metallurgical routes also exist |

The common shredder ore rule is **two native dust per ore**, not one; explicit crystal routes commonly give three dust. Centrifuge ore recipes are manually specified and config-sensitive. Main/trace/gangue quantities above cite the common source tables once, rather than duplicating every material row. The stone block is stock **648 q** throughout these ordinary stone-bearing distributions; slag is a different material. [DIST] [SHREDDER] [CENTRIFUGE]

Ore has no fixed unit quantity, grade, density or oxygen content in `MaterialShapes`. Higher ore yields may mean better recovery, and these comparisons alone do not prove creation. **Enriched old bedrock ore shares `oreX` with ordinary ore**, so generic recovery machines may process that enriched item again under their ore rules. The grade/yield meaning is not preserved at that boundary. [ODM]

### Existing directed process graph

| Chain | Inputs → process → outputs | Interpretation and represented waste |
|---|---|---|
| Ordinary metal ore | ore → shredder 2 dust → furnace 1 ingot/dust | Crushing/recovery plus implicit reduction; gangue absent from shredder route |
| Ordinary ore separation | ore → centrifuge explicit dust/trace products + gravel | Beneficiation abstraction; metal dust identity hides oxidation state |
| Acid-assisted crystal chain | ore/mineral + acid/solvent → crystallizer crystal/concentrate → shredder/centrifuge/electrolyser richer metal products | Leaching and concentrate/crystal recovery; not generic casting |
| Named mineral separation | chalcopyrite/bornite/chalcocite/covellite → centrifuge products or leaching → copper ore parcel | Named “flotation” abstraction lacks an actual flotation mechanism; recipe executes as centrifugation |
| Heavy mineral separation | wolframite → 3 W dust + Fe dust + Mn powder + gravel; columbite powder → 2 Nb + Ta + Fe + Mn | Gravity/multi-mineral separation abstraction; not a fixed metallic input |
| Roasting | goethite → hematite; lanthanide concentrates → oxides; bedrock grade → roasted grade + vitriol | Existing roasting labels/states; not a shared oxygen/volatile ledger |
| Chemical reduction | oxides/sulfides/halides → metal/nugget + process products | Exact recipes; reducing agents sometimes abstracted or absent |
| Refinery mineral coproduct | PGM residue → Rh solution powder + Pd/Platnium/Fe powders; titanium crystal → trace-metal slurry | Item solutions/slurries and typed process fluids are distinct representations |
| Rare earth cascade | concentrates → solutions → separation/precipitate/oxide/fluoride → finished powder/ingot | Existing chemistry grades can be reused; not all steps use generic OD keys |

[CENTRIFUGE] [CRYSTAL] [SMELT] [CHEM] [EMETAL]

### Old bedrock generator graph

For all 19 types, stock stage recipes implement:

```text
1 ore_bedrock → centrifuge → 4 ore_centrifuged
each → crystallizer → 1 ore_cleaned
each → centrifuge → 4 ore_separated
each → acidizer/crystallizer + sulfuric → 1 ore_purified
each → centrifuge → 4 ore_enriched
ore_enriched → furnace → material-specific endpoint
```

That is **64 enriched parcels per base parcel**, not a conserved 1:1 metal-form loop. The nitric branch gives two nitrocrystalline parcels plus two tier-1 byproducts; organic or radiosolvent branches recover enriched parcels plus tier-2/3 byproducts. Any intermediate also has a one-to-one shredder shortcut into enriched ore. The actual byproduct table is attached to `EnumBedrockOre`; helper compression does not make all byproducts equal: silicon gives three nuggets, arsenic an ingot, Tc/Po a billet, others powder. [OLD] [CENTRIFUGE] [CRYSTAL] [SHREDDER] [SMELT] [MINERAL]

**Metadata exception:** current enum order is GOLD=9, BISMUTH=10, CAD=11. Crystallizer special branches target 10/11; the `i==10` branch registers some later routes for metadata **9**, leaving the intended bismuth nitric/deepcleaned/seared branch incomplete and adding duplicate gold registrations. This is a source-proven indexing inconsistency, not an ore mass-balance claim. [OLD] [CRYSTAL]

### New mixed bedrock generator graph

The base item stores six composition fractions in NBT (`light`, `heavy`, `rare`, `actinide`, `nonmetal`, `crystal`). Slopper consumes that item and water; output balances accumulate fractional grade-BASE parcels with an efficiency multiplier `1 + efficiency×0.1`. Generated recipes then wash, roast, centrifuge, leach with sulfuric/solvent/radiosolvent, partition primary material, and recover residues. [BASEORE] [SLOPPER] [NEW]

Common generators use all six types; the grade × type item metadata is `grade.ordinal()<<4 | type.ordinal()`. Each type has two primary and nine secondary output definitions. `toFluid()` uses only SMELTABLE materials, multiplies fragment quantity and rounds **up**; `extract()` rounds up to fragments and caps each stack at 64. The two-output metal electrolyser routes remaining/nonmeltable products into solids, and supplies nominal slag when no molten product exists. [NEW] [EMETAL]

Reagents/costs easily exposed in source: newer base washing uses 250 water and 100 ticks; primary acid/solvent steps 250 fluid and 200 ticks; secondary washing consumes four input parcels per washed output; optional roasting produces 50 vitriol. These are process rules, not experimentally verified recovery efficiencies. The current stock world generation entry for `bedrock_ore_base` is commented out; slopper/recipes are registered, but no stock producer was found. [CRYSTAL] [COMBINE] [PYRO] [WORLD]

The common newer-grade conversions below apply to **all six ore types**. Let P1/P2 be the type's primary output definitions and A/S/R1–3 its sulfuric/solvent/rad secondary definitions. `extract(P,k)` means `min(ceil(P.amount×k),64)` fragments; molten equivalent is `ceil(8×P.amount×k)` q only for SMELTABLE output. This notation keeps type composition separate from the shared graph. [NEW]

| Input grade(s), one parcel unless stated | Process | Output rule |
|---|---|---|
| BASE; BASE_ROASTED | Centrifuge | 1 PRIMARY + gravel |
| BASE_WASHED | Centrifuge | 2 PRIMARY + gravel |
| PRIMARY_SULFURIC | Centrifuge | 2 PRIMARY_NOSULFURIC + 2 SULFURIC_BYPRODUCT |
| PRIMARY_SOLVENT | Centrifuge | 2 PRIMARY_NOSOLVENT + 2 each SULFURIC/SOLVENT_BYPRODUCT |
| PRIMARY_RAD | Centrifuge | 2 PRIMARY_NORAD + 2 each SULFURIC/SOLVENT/RAD_BYPRODUCT |
| PRIMARY; PRIMARY_ROASTED | Centrifuge | extract(P1,1) + extract(P2,1) |
| PRIMARY_NOSULFURIC/NOSOLVENT/NORAD | Centrifuge | Same primary extraction + 1 CRUMBS |
| PRIMARY_FIRST / SECOND | Centrifuge | Two separate extract stacks of favored primary + one other primary + 2 CRUMBS |
| SULFURIC/SOLVENT/RAD_WASHED | Centrifuge | extract(each of that branch's 3 secondary definitions,1) + CRUMBS |
| BASE/PRIMARY/branch BYPRODUCT | Combination oven or pyro oven | Corresponding ROASTED state + 50 VITRIOL |
| Branch BYPRODUCT / ROASTED | Arc furnace solid mode | 2 / 4 corresponding ARC parcels |
| PRIMARY_FIRST / SECOND | Arc furnace molten mode | favored primary ×5 + other primary ×2; non-smeltable definitions omitted |
| CRUMBS | Arc furnace molten mode | primary1 ×1 + primary2 ×1 where smeltable |
| Branch WASHED | Arc furnace molten mode | each of 3 branch secondary definitions ×3 where smeltable |
| PRIMARY_FIRST / SECOND | Metal electrolyser | favored primary ×8 + other primary ×4; molten if possible, otherwise solid fragments; CRUMBS byproduct stack |
| CRUMBS | Metal electrolyser | primary1 ×2 + primary2 ×2; same molten/solid selection |
| Primary variants | Crystallizer + 250 H2 / chlorine | PRIMARY_FIRST / SECOND respectively; no second solid product |
| Four branch BYPRODUCT/ROASTED/ARC parcels | Crystallizer + 250 water | One corresponding WASHED parcel |

[CENTRIFUGE] [ARC] [EMETAL] [CRYSTAL] [COMBINE] [PYRO]

**Generator exception:** the metal electrolyser adds the CRUMBS product as `Pair(ItemStack,3)`, but `makeBedrockOreProduct()` copies ItemStack products without applying the pair's integer. The source therefore returns **one CRUMBS**, not the apparent intended three, in PRIMARY_FIRST/SECOND recipes. This is a definite ignored-output-multiplier bug in an ore-recovery branch, kept outside the pure-form conservation count. Fragment-stack capping and omission of non-smeltable arc outputs are likewise explicit recovery differences, not fixed-unit metal creation.

Equipment costs are distinct from form mass: shredder has the RealSim 3 kW equipment demand; press 2 kW; baseline fabrication 3 kW; chemical base 4 kW; arc work is 1.8 MJ/kg under its adopted accounting. The industrial blast furnace's explicit six bills are in §11. No universal per-ore kWh can responsibly be derived without recipe time, upgrades and the actual machine path. [DEMAND]

## 11. Alloy processing families

### Crucible recipes (n = 8 q; i = 72 q)

| Shared pattern / alloy | Recipe IDs | Input → output | Accounting |
|---|---|---|---|
| Binary batch | 1 red copper; 2 advanced alloy | Cu n + redstone n → 2n Mingrade; steel n + Mingrade n → 2n alloy | Equal nominal units; redstone is not a real metallic constituent |
| Weighted HSS | 3 | steel 5n + W 3n + Co n → Dura 9n | 72 → 72 q |
| Stainless-fed HSS exception | **14** | stainless 5n + W 3n + Co n → Dura 2i | **72 → 144 q**, MB01 |
| Steel family additions | 4 ferro; 5 TCA; 13 CDA; 21 stainless | steel 2n + U238 n → ferro 3n; steel 8n + Tc/Cd n → i alloy; steel 7n + Cr n + Ni n → i stainless | Equal nominal units, different recipes/composition |
| Carbon steel | 0; GT6 9/10/11 | iron-family 2n + carbon n → steel 2n | Carbon discarded from nominal output; reduction/carburizing abstraction |
| Flux bronze | 15 arsenic; 16 bismuth; 23 tin alternative | Cu 8n + As/Bi/Sn n + flux 3n → bronze i + slag 3n | 96 → 96 q; **Sn maps to arsenic bronze**, intentional naming substitution in source |
| Other nominal alloy/compound | 18 MagTung; 19 BSCCO; 20 GaAs | W i + Schrabidium n → MagTung i; Bi2n+Sr2n+Ca2n+Cu3n → BSCCO i; Ga6n+As3n → GaAs i | MagTung loses dopant units; others equal nominal units, not atomic-mass stoichiometry |
| Mineral refining | 6 barium; 7 hematite; 8 malachite; 12 conglomerate; 22 magnesium | See source bills; mineral+flux/reductant → metal+slag | Extraction, not pure form conversion; conglomerate's Tc input is quantity **0**, so not consumed/required |

[CRUCIBLE]

### Other alloy/steel authorities

| Machine/model | Common rule and concrete examples | Important divergence |
|---|---|---|
| Metallurgical Furnace (`BlastFurnaceRecipes`) | Two inputs; `DictFrame` expands **ingot/plate/gem/dust**, one item of either feed. Fe+coal/coke → 1 steel; Fe ore+coal → 2 | One unit from each slot, not Mats composition; nuggets and billets excluded unless explicitly named |
| Stainless special recipes | Ni+steel → 2; Ce+steel → 1; Cr+steel → 3; tiny Yb+steel → 12; steel+Se dust → 4 | Multiple incompatible alloy/yield abstractions; no stable stainless composition |
| Carbon precursor variants | Steel+coal dust → one `ingot_steel_dusted`; steel+Mn dust → two; powder crafting 2 steel dust + Fe dust + 2 coal dust → `powder_steel_dusted` | Powder precursor arc output is one **steel block**, MB03 |
| Rotary furnace | Fe ingot+coal/coke → one molten steel; 9 Fe fragments+coal/coke/flux → 2/3/4 steel; 3 Cu+Sn → 4 gunmetal | Fragments already carry 8 q each; higher yield is an ore-recovery/form-boundary disagreement, not counted automatically as creation |
| Rotary advanced steel | steel+flux+coker gas → weapon steel; 4 Dura dust + Cu dust + reform gas → 2 Saturnite | Lossy/processing abstraction; steam/gas charge separate from metal amount |
| Anvil/manual | Cu ingot + Al ingot → one gunmetal; repeated hot dusted-steel forging reaches chainsteel | Gunmetal differs from rotary Cu/Sn; hot form is NBT heat with metadata forging count, not melt temperature |
| Crafting scrap alloy | Cu dust+redstone dust → 2 Mingrade units; steel dust+Tc nugget → 1 TCA unit; Fe dust+coal dust → 1 steel | Scrap amount is explicit, and can differ from crucible constituent ratio |
| Industrial blast furnace (new) | oreIron+coke → 3 steel+2 slag, 18 MJ, 2700 air; oreIron+coal → 2 steel+3 slag, 16 MJ, 2400 air | Ore parcel explicitly assumes recoverable kg; not a universal Mats ore mass |
| Industrial solid feed | 2 iron ingots **or 2 dustIron** + coke → 2 steel, 8 MJ, 1800 air; coal versions → 2 steel, 12 MJ, 2400 air | No plate/nugget/billet feed bill. One reductant reserved plus auxiliary combustion fuel; only coal-coke grade has coke identity |

[BLAST] [ROTARY] [ANVIL] [POWDER] [ARC] [IBLAST] [BFPROCESS]

Before assigning alloy compositions, reconcile **what each alloy means** across these authorities. Existing molten alloy IDs carry no elemental composition or carbon percentage; a cast steel item is simply MAT_STEEL. Distinguish intended gameplay shortcuts from mistaken quantity arithmetic.

## 12. Machine acceptance patterns

| Consumer | Actual matching authority | Form policy |
|---|---|---|
| Crafting | Exact items, OreRecipes and special crafting handlers | Recipe-specific; no automatic cross-form conversion |
| Furnace | `GameRegistry.addSmelting`, item+metadata | Exact stock inputs; no blanket OD powder/ore smelting |
| Metallurgical Furnace | `getRecipeStacks` + one item from each feed slot | DictFrame ingot/plate/gem/dust alternatives; explicit strings/items restrict further |
| Industrial blast furnace | Explicit six `AStack` bills + `fuelIdentity` | oreIron, ingotIron, dustIron; coal/coal-coke reductant, not all carbon fuel |
| Small/large arc | `ArcFurnaceRecipes.getOutput(stack, liquid)` | Solid vs molten recipes independent; matching loops, molten NBT scraps special case |
| Crucible | Mats decoding plus template/buffer checks | Prefix/alias quantities and explicit composition, including additives; zero-ore edge |
| Foundry caster | Mats type + mold output availability | Receives molten type, not powders/ores directly |
| Shredder / excavator crusher | Exact defaults, then constrained OD post-generation | Native dust output required; no blanket nugget/billet rule; excavator preserves input on generic scrap/dust result |
| Centrifuge | Exact/OD recipe lookup | Explicit ore, crystal, mineral, waste, and generated bedrock recipes; no universal alloy dismantling |
| Crystallizer / acidizer | Solid+fluid key plus input count | Exact grades and reagents; no universal oxide/dust equivalence |
| Ore slopper | Exact `bedrock_ore_base`, NBT composition | Not ordinary ore blocks, not old `ore_bedrock` |
| Metal electrolyser | Exact/OD input → two molten lanes plus solids | Crystals and new bedrock grades; requires nitric acid; no generic “any dust” |
| Fluid electrolyser | FluidType identity | Solutions/molten salts/gases; does not automatically use Mats molten metal |
| Chemical plant | `ChemRecipe` AStacks/fluid requirements | Exact compound items or explicit OD forms; selected physical fuel bills |
| Press | Stamp type + AStack | Explicit ingots/finished dusts, fuel briquettes, generator for fine wires |
| Anvil | Smithing/construction recipes, tiers/hot predicates | Exact or named prefixes; mold imprint accepts prefix without a material suffix; longest-prefix guard prevents wrong shape |
| Assembler / fabrication / soldering | Recipe ingredients and stack sizes | Explicit ingot/dust/nugget/plate/triple/sextuple/wire/pipe/shell roles; components not interchangeable wholesale |
| Recycling | Explicit anvil recipes, shredder recovery, MatDistribution, scrap handler | Lossy/chance salvage and intact material scrap are different models |
| Refinery/coker/pyro/mixer | Fluid bills or exact feed compounds | Solid coke/tar/char/ash coproducts; no general metal-form policy |
| Nuclear/RTG consumers | Native rod/pellet/state classes and recipe registries | Fuel enrichment, decay and assembly state, not OD metal identity |
| Energy/heating consumers | §13 | Physical matcher, legacy fuel handler, or dedicated power table |

All machine families in the requested scope are addressed here. A separate generic **roller**, ore **separator**, or standalone **crusher recipe registry** was not found in production; their conceptual operations occur in anvil/press/assembler, centrifuge/electrolyser and shredder/excavator paths. Names and model resources such as `acidizer.obj` do not establish a second recipe authority. [EXCAVATOR] [CRYSTAL] [CUSTOM] [INIT]

### Significant inconsistencies (counted as ten grouped findings)

| ID | Finding | Decision required before cleanup |
|---|---|---|
| A01 | Schrabidium direct forms lack their OD material keys while generated shapes have keys | Restore intended support or document deliberate restriction; preserve saved item IDs |
| A02 | Generation flags advertise forms not actually generated, especially tin and isotope dust | Distinguish intended support from unused flags; do not silently add forms |
| A03 | Cr/Sm/late Gd dust generic melting bypasses their exact arc route, unlike Rh/Ir | Decide intended chemical state/gating |
| A04 | OD-only metals/fuel states have processing recipes but no Mats/casting identity | Decide which should remain outside foundry; no universal acceptance |
| A05 | GT6 compatibility shapes are accepted by runtime Mats but excluded by arc/NEI `noAutogen` loops | Separate runtime support and display/generator coverage |
| A06 | Unmapped known smeltable ores accepted for 0 q; arc molten generator omits them | Reject or explicitly assay; do not invent generic yield |
| A07 | Runtime crucible accepts any registered Mats suffix alias; generated arc/melt display queries primary suffix only | Alias-only external entries can diverge between machines; startup/order/config dependent |
| A08 | Lignite, briquettes, biomass/sawdust, solid fuel and several legacy fuels burn in older consumers but fail physical matcher | Explicit physical assay/form mapping, with scope-aware consumer policy |
| A09 | All local coke grades carry `fuelCoke` / `gemCoke`; physical matcher returns generic COKE before grade-specific interpretation | Preserve coke grade identity in energy matching |
| A10 | Coke storage blocks have legacy burn values but no modern solid matcher; foreign coal/coke blocks lack quantity-aware mapping | Distinguish nine-item storage from arbitrary named block; avoid blanket salvage/burn aliases |

A01–A07 evidence: [ODM] [MATS] [SHAPES] [ARC] [CRUCIBLE]. A08–A10: [COMBUST] [FUEL] [FIREBOX] [OVEN] [BOILERI]. These are inconsistencies to classify, not instructions to accept every form in every process.

## 13. Energy-source forms and intended fuels — expanded scope

This section distinguishes **chemical fuel**, **reductant/carbon feed**, **nuclear fuel/decay source**, **oxidizer**, **stored energy**, and **heat/work carrier**. “Has LHV,” “has a legacy burn value,” “appears in a fuel list,” and “actually accepted by a firebox” are different facts.

### Solid fuel registry and actual physical acceptance

`FuelHandler.getBurnTime()` is the native legacy fuel registration. `ModuleBurnTime` adjusts those values. Both firebox and heating oven override `usesPhysicalCombustion()` to **true**, so their fuel-slot and burn tests instead call `Combustion.solidChemicalMicrojoules()`. The oven's configurable `burnModule` bonuses therefore do **not** define its current chemical fuel energy. They still appear in legacy descriptions/config and ash accounting. [FUEL] [BURNMODULE] [FIREBOX] [OVEN] [FIREBOXBASE]

| Fuel forms | Legacy burn ticks (per item) | Physical firebox/heating oven acceptance | Material/process role |
|---|---:|---|---|
| Vanilla coal / charcoal | Vanilla furnace registration | Yes: exact vanilla item; distinct COAL/CHARCOAL identity | Carbon feed vs fuel; charcoal has explicit crucible mapping |
| Native coal powder | 1600 | Yes, `dustCoal` | Same COAL_ITEM assay as coal; full powder is not tiny powder |
| Coal block | Vanilla furnace registration | Yes, exact vanilla block = nine coal energy units | Storage multiple preserved in physical matcher |
| Lignite lump / powder | 1200 | **No stock mapping**, despite LIGNITE_ITEM physical assay | Coke preparation; Mats carbon fraction 1/3 |
| Coal/lignite/petroleum coke / powder | 4800 | Yes via shared aliases, but **all resolve to generic COKE_ITEM** | Distinct real physical assays and 3/4 carbon conversion; grade lost in burner |
| Coke block (all three metadata grades) | 44000 | **No** `blockCoke`/`blockCoalCoke` mapping | Nine-item storage; legacy 44000 differs from 9×4800=43200 |
| Coal / lignite / wood briquette | 2000 / 1600 / 400 | **No stock mapping** | Pressed powder/sawdust; physical assays exist for briquettes but not matcher entries |
| Sawdust | 100 | No | Briquette/coke/cordite feed; not equivalent to registered logWood |
| Biomass / compressed biomass | 400 / 800 | No | Biological feedstock and intended legacy fuel; BIOMASS_ITEM assay alone does not enable burning |
| Ordinary solid fuel / presto / presto triplet | 3200 / 8000 / 40000 | No | Crafting/heating-oil synthesis; dedicated legacy fuels, not named physical identities |
| Rocket fuel item | 6400 | No | Legacy furnace fuel/propellant; fluid ROCKET_FUEL is separate |
| Infernal coal / coal crystal | 4800 / 6400 | No stock mapping | Special fuel/ore crystal; coal crystal also has ore processing routes |
| Generic scrap / dust / scrap block | 50 / 25 / 400 | No | Deliberate legacy burn/recovery abstraction; do not infer composition or LHV |
| Guide book | 200 | No | Legacy burnable object, not a metallurgy fuel material |
| Ash WOOD/COAL/MISC/FLY/SOOT | 100 / 200 / 100 / 200 / 100 | No | Residue grades; physical ash assays are zero LHV. FULLERENE has no listed burn case |
| Logs / planks / vanilla stick | Vanilla furnace registration | Yes through `logWood`, `plankWood`, exact stick | Adopted mass: log 4 kg, plank 1 kg, stick 0.5 kg; other wooden items are not implicitly matched |
| Saplings, sugar, sulfur, tar, char | Varies / no general listed native burn case | No general stock solid matcher | Some positive physical assays or process fuel feedstocks; not proof of heater acceptance |
| Balefire solid fuel/presto/triplet | Native FuelHandler cases **commented out** | No | Still registered/intended exotic fuel forms; module bonuses do not make them burnable |
| Graphite, diamond, metal powder | No general physical fuel mapping | No | Carbon/reductant or metal role; crucible acceptance is not fuel acceptance |
| `pellet_coal` | No FuelHandler case | No | Registered legacy item; no active production/consumer found outside registration and missing-mapping handling. Its name does not establish a usable coal pellet fuel |

Native `dustTinyCoal` has no common proportional fuel mapping. Do not infer 1/9 heat from its Mats quantity. External coal/charcoal dust with the exact supported keys can match; arbitrary other FML fuels cannot. `solidIdentity()` ignores item NBT, and only the exact vanilla coal block gets a nine-unit multiplier. A foreign block merely registered as `dustCoal` or `gemCoal` would be treated as one feed unit. [COMBUST] [ODM]

### Physical energy quantities already defined

These are **adopted source values**, not this audit's recommendations:

| Assay | Mass per item | LHV | Current thermal implications |
|---|---:|---:|---|
| COAL_ITEM / COAL_BRIQUETTE_ITEM | 1 kg | 25.8 MJ/kg | 25.8 MJ chemical; recognized coal form gives 21.93 MJ useful at 85% |
| LIGNITE_ITEM / LIGNITE_BRIQUETTE_ITEM | 1 kg | 18 MJ/kg | Defined, but physical solid matcher omits it |
| CHARCOAL_ITEM | 1 kg | 29.5 MJ/kg | Recognized vanilla charcoal gives 25.075 MJ useful |
| COKE_ITEM | 1 kg | 28.2 MJ/kg | All local coke grades currently use this through aliases |
| LIGNITE_COKE_ITEM | 1 kg | 25 MJ/kg | Defined, not distinguished by burner matcher |
| PETCOKE_ITEM | 1 kg | 32.5 MJ/kg | Defined; local petroleum coke uses PetCoke suffix, while matcher checks PetroleumCoke and also generic fuelCoke |
| CHAR_ITEM | 1 kg | 32.8 MJ/kg | Process product, not wired to generic heater |
| WOOD_BRIQUETTE_ITEM | 4 kg | 15.6 MJ/kg | 62.4 MJ chemical defined; no heater mapping |
| BIOMASS_ITEM / SAPLING_ITEM | 1 kg | 15.6 MJ/kg | Process assay; no general physical heater mapping |
| WOOD_ASH_ITEM / ASH_ITEM | 1 kg | 0 | Residue, not physical fuel |
| Sulfur / sugar | 1 kg | 9.26 / 16.5 MJ/kg | Chemical feed energy budget, not ordinary heater support |
| Crude/coal/wood/crack tar item | 1 kg | 39.75 / 28 / 17 / 42.3 MJ/kg | Coking/melting bills, not generic solid burner support |

[PHYSICAL] [COMBUST]

The shared thermal path reserves chemical credit once, releases useful energy against rated demand, and persists credit; energy is not inferred from Mats q. Firebox is rated 200 kW; oven 500 kW with a 900°C process-source interface. The oven can also **import heat** from an appropriate source. Industrial boiler solid ports and wood burner use the same solid matcher, so the form omissions affect more than the two named heaters. These are source capacities/policies, not a runtime throughput benchmark. [FIREBOX] [OVEN] [BOILERI] [WOODBURN] [CREDIT]

### Other fuel consumers and process roles

| Consumer / role | Accepted material forms | Actual authority / important differences |
|---|---|---|
| Metallurgical Furnace fuel slot | Vanilla coal/charcoal, coal block, coke block, lava bucket, blaze rod/powder, lignite lump/powder, coal powder, any briquette/coke/powder metadata, solid fuel | `TileEntityDiFurnace.getItemPower`: 200 coal, 2000 coal block, 400 coke, 4000 coke block, 12800 lava; direct identities, nominal equipment power, atmosphere gate. Coal/coke blocks get **10×**, not nine; no claim those values are joules |
| Iron furnace / ordinary furnace | Legacy `ModuleBurnTime` / Forge furnace fuel boundary | Broader fuel list than modern heater; different quantities and bonus rules |
| Metallurgical reaction input | Coal/coke through DictFrame gem/dust, graphite exact in oxide reductions; carbon powder and selected nuggets | Fuel-slot energy is separate from coal/coke consumed as an alloy/reduction ingredient |
| Crucible reaction | Coal/lignite/coke/graphite/diamond conversion, charcoal/ash composition | Carbon is material feed; crucible obtains heat from a source, not by treating every additive as its own burner fuel |
| Industrial blast furnace | Coal or coal-coke reductant; coal powder and explicit supported aliases | `BlastFurnaceProcess.fuelIdentity`; excludes native lignite and petroleum/lignite coke. Auxiliary heat paid by mass/LHV; coke/coal process outputs differ |
| Coke oven / pyro oven / coker | Coal/lignite lumps, powder/briquettes, logs/saplings/wood briquette, tar or liquid residue | Current physical bills reserve solid coproduct mass and process energy, producing coke/char/ash and fluids. Product coke's furnace burn value is not the source bill's conserved energy |
| Chemical/mixer/reactor processes | Biomass, fish/seed oils, sugars, alcohol/gas feed, sulfur/additives | Feedstock can carry chemical energy without being equipment fuel |
| Fuel assembly / vehicles / rockets | Fuel fluid containers, rocket fuels, engine-grade fluids, some exact solid propellants | Container volume and propellant/oxidizer suitability are separate from heating fuel and alloy material |

[DIFURNACE] [IRONFURNACE] [BLAST] [BFPROCESS] [COMBINE] [PYRO] [COKER] [CHEM] [MIXER]

### Fluid/gas fuel and intended energy-source families

`Combustion.isBurnerFuel(type)` requires a flammable trait, a **resolved** physical state and positive chemical energy. A legacy flammable number alone is insufficient. Oil burner uses this boundary. Diesel engine accepts only DIESEL, DIESEL_CRACK, DIESEL_REFORM, DIESEL_CRACK_REFORM and PETROIL under its compression-ignition efficiency table. Reciprocating engine requires combustible suitability and excludes fluid HYDRAZINE/HYDROGEN. Gas turbine and exotic/vehicle consumers have their own suitability paths. [COMBUST] [OILBURN] [DIESEL] [ENGINE] [GASTURBINE]

The gas turbine's actual `TurbineFuelAccount.suitable()` set is **GAS, PETROLEUM, REFORMGAS, GAS_COKER, SYNGAS**. Sour gas, oxyhydrogen and pure hydrogen are explicitly excluded from that stock combustor; having a resolved gas LHV is insufficient. The alternative aero path checks AERO combustible grade plus resolved positive energy, rather than this gas set. [TURBINEFUEL]

| Family | Concrete identities covered | Energy/source status |
|---|---|---|
| Crude and process oil | OIL/OIL_DS, CRACKOIL/CRACKOIL_DS, COALOIL, hot counterparts, OIL_COKER | Resolved crude/coker assays; only trait-approved fuels qualify for burner; hot phase does not invent additional LHV |
| Distillates/residues | DIESEL and cracked/reformed versions; HEATINGOIL/VACUUM; HEAVYOIL/VACUUM; KEROSENE/REFORM; NAPHTHA/DS/CRACK/COKER; LIGHTOIL/DS/CRACK/VACUUM | Physical fuel assays; equipment grade restricts useful consumers |
| Spark/aromatic fuels | GASOLINE, COALGAS, REFORMATE, AROMATICS, SOLVENT, XYLENE; leaded PETROIL/GASOLINE/COALGAS | Resolved energy/blends where defined; lead additive is trace, not a whole ingot's fuel gain |
| Lubricants/bitumen/reclaimed | SMEAR, BITUMEN, RECLAIMED, LUBRICANT, PETROIL | Positive assays for processing/burning where suited; not automatically diesel compatible |
| Biological/wood-derived | FISHOIL, SUNFLOWEROIL, OLIVEOIL, ETHANOL, METHANOL, BIOFUEL, WOODOIL, COALCREOSOTE | Defined physical fuel assays; trait/consumer support differs |
| Combustible gases | GAS, BIOGAS, SYNGAS, GAS_COKER, REFORMGAS, PETROLEUM, SOURGAS, LPG, HYDROGEN_GAS, CO, HYDROGEN_SULFIDE, UNSATURATEDS, BLAST_FURNACE_GAS | Defined gas/mixture assays; some lack equipment suitability despite positive energy. Blast-furnace top gas is recoverable fuel, not generic flue gas |
| Cryogenic chemical fuel / special mixture | HYDROGEN, OXYHYDROGEN, HYDRAZINE | Defined chemical budgets; oxygen contributes no LHV; special engine/propellant restrictions apply |
| Intended/legacy exotic combustible fluids | ROCKET_FUEL, BLOODGAS, BALEFIRE, DICYANOACETYLENE, ELBOWGREASE, NMASSTETRANOL, RADIOSOLVENT, CHLOROMETHANE, TEKTOAIR, POLYTHYLENE, RAFFINATE | Registered traits/legacy equipment budgets do not imply resolved physical burner support; retain as explicit unresolved/consumer-specific cases |
| Oxidizers | OXYGEN/OXYGEN_GAS, peroxide and oxidizer-bearing mixtures | Reaction support, **not independent chemical energy** in physical combustion; exotic core tables may assign nominal multipliers |
| Heat/work carriers | Steam states, water, hot oil, coolant/heated coolant, plasma | Generated heat/enthalpy or reactor heat carrier, not ordinary chemical fuel; no automatic Mats relationship |

This table records both used and intended fuel identities; it deliberately does not claim every resolved fluid has a flammable trait or every tagged fluid burns in all equipment. Custom fluid definitions can alter those boundaries. [FLUIDS] [PHYSICAL]

### Nuclear, decay, fusion and stored-energy forms

| Family | Material/state representations | Consumers / accounting boundary |
|---|---|---|
| Fission / breeder | Natural/enriched/depleted U states; Th fuel; Pu/mixed Pu/MOX; Np/Am/Cm/Cf and fictional enriched fuel variants; nuggets/ingots/billets into reactor assemblies | Magnox/Zirnox, RBMK, PWR, Watz and breeder native recipes. Radiation or metal registration alone does not make an item reactor fuel |
| Fuel pellets and rods | RBMK enrichment/xenon metadata and NBT burnup; PWR fuel enum/state; plate fuel; rods; ICF/Watz pellets | Exact class/state consumers, geometry and burnup; not crucible-meltable merely because material occurs in Mats |
| Radioisotope decay fuel | RTG weak/base, radium, promethium, strontium, cobalt, actinium, polonium, americium, berkelium, Tm170, gold, lead, Cf251/Cf252 pellets | `ItemRTGPellet` heat/decay/lifespan; RTG, mini RTG and isotope furnace consumers; depleted pellet enum is a product state, not an ordinary fuel nugget |
| Fusion reactants/plasma | H/D/T/He3, plasma DT/DH3/HD/HT/XM/BF | Plasma heater and fusion recipe/material channels; separate from chemical LHV and generic fluid burning |
| Exotic core feed | HYDROGEN, DEUTERIUM, TRITIUM, OXYGEN, PEROXIDE, XENON, SAS3, BALEFIRE, AMAT, ASCHRAB | `TileEntityCore.getFuelEfficiency` nominal multipliers and AMS core items; oxygen multiplier is an exotic gameplay table, not chemical fuel energy |
| Electrical storage / charged matter | Batteries/cells, charged pellets and coils, antimatter forms | Stored-energy/weapon/process carrier; do not classify charge discharge as creating chemical material energy |

PWR reprocessing recipes **1117–1127** recover bulk material and raffinate in chemistry; ordinary centrifuge explicitly excludes cooled PWR fuel. Outgasser is an irradiation channel despite its legacy name. SILEX's weighted isotope recovery preserves a nine-event feed quantity, rather than summing weight numbers as products. This is a representation/consumer audit, not nuclear calibration. [NUCLEAR] [RTGITEM] [RTGTE] [CHEM] [CENTRIFUGE] [OUTGAS] [SILEXTE] [FUSION] [CORE]

Lava is an explicit noncombustion energy source in the Metallurgical Furnace's lava-bucket power table; water/steam and heated coolant transfer previously supplied heat/work rather than chemical fuel. Solar boiler/electrical heater/heat-exchanger sources belong to environmental, electrical or imported-heat supply, not a coal-like material form. Their presence does not make dust, ores or molten metals combustible. Fuel containers, oxidizers, batteries and reactor moderators must remain distinguishable from the energy source they support. [DIFURNACE] [THERMALSOURCES]

## 14. Material conservation findings

### Stock conservation findings (four definite defects, one unresolved loss)

| ID / rank | Source-proven problem | Reproducible accounting, without running Minecraft |
|---|---|---|
| MB01 — High | Crucible recipe **14 `crucible.hsss`** expands metallic alloy input | 5n stainless + 3n W + n Co = **72 q**; output 2i Dura = **144 q**. Recipe 3 uses same total feed but yields 72 q; actual `tryRecipe()` subtracts/credits these amounts |
| MB02 — Critical | Crafted steel pipe assembly carries eight times its source steel | 3 steel blocks = **1944 q** → 8 `pipes_steel`; each exact salvage mapping is **1944 q**. Total recoverable steel = **15552 q**, gain 13608 q. Mold 13 produces only **one** such item for 1944 q |
| MB03 — High | Crafted dusted-steel powder creates steel units in arc processing | 2 steel dust + 1 iron dust = **216 q metal**, plus two coal dust. Output `powder_steel_dusted` has arc output **one steel block = 648 q**. Even counting carbon-feed conversion at 72 q total, nominal inputs are only 288 q. Main metal triples |
| MB04 — Medium; loss intent unresolved | Custom shape casting/remelting is nonreciprocal | Mold 5 charges **3 ingots** per titanium/tungsten blade; salvage assigns **2**. Mold 7 charges **4 ingots** per flat stamp; salvage assigns **3** for five materials. Exact quantity disagreement for the same item; no declared salvage penalty found, but deliberate loss cannot be ruled out. **Excluded from definite bug count** |
| MB05 — High | Known but unmapped ores can be destroyed for zero output | Prefix `ORE.q(1)=0`; `Mats` adds a zero MaterialStack for smeltable suffix; crucible tests nonempty list, not positive sum, then consumes one item; zero stack removed by cleanup. Examples: `oreTin`, `oreZinc`, `oreNiobium`, old enriched ore variants without explicit distribution |

[CRUCIBLE] [CRUCIBLETE] [CRAFT] [DIST] [MOLD] [POWDER] [ARC] [MATS] [SHAPES]

The count is by **grouped defect**, not each affected item/material. MB05 includes many missing ore mappings. MB04 records two custom mold/salvage rule disagreements separately because loss intent is unestablished. Source proves the arithmetic or destructive path; no exploit/runtime reproduction was performed.

### Separate ratio disagreements, losses and ambiguities

| Finding | Why it is kept separate |
|---|---|
| Nine fine wires → one ingot vs eight wires from pressing/casting | Pure-form loss: 81 q → 72 q. No source declaration of loss; later cleanup should decide whether deliberate efficiency or obsolete recipe |
| Coke→graphite press gives 72 q carbon versus direct coke conversion 54 q | Ratio disagreement across preparation/melting; carbon assay/press semantics need a decision before treating this as chemistry creation |
| Carbon steel consumes carbon but retains only iron-unit count | Plausible reduction/carburizing abstraction; missing carbon/oxygen ledger is not automatically a duplication bug |
| Rotary nine Fe fragments yield 2/3/4 steel | Conflicts with metallic fragment 72 q total, but fragments may be intended ore recovery parcels; determine intended model before changing yields |
| Stainless metallurgical special yield multipliers | Alloy mass/composition unresolved across machine authorities; isolate exact shortcuts rather than automatically flattening them |
| Potassium 800→1000 fluid reciprocal path | 20% loss; no universal fluid-mass assertion. No positive creation loop shown |
| Small arc silicon solid vs molten output | Flint/FIBER/asbestos solid four nuggets =32 q vs molten half-ingot=36 q; block output forty nuggets=320 vs324 q; rounding/output-form inconsistency, not a loop claim |
| Ore crystals and centrifuge/electrolyser yields | Better recovery/chemical extraction, not fixed pure-metal form conversion |
| Nuclear conversion/isotope values | Weighted outputs, irradiation/transmutation, reactor state and chemical assays; not ordinary alloy mass arithmetic |
| Plastic insulation/steel fasteners disappear on generic material salvage | Composite item lacks retained bill of materials; potential intended loss, not every composite should remelt to all inputs |
| Generic OD + exact salvage both contribute | Resolver architecture can double credit externally aliased custom salvage items. No contradictory stock custom input was established, so not added to definite stock bug count |

## 15. OreDictionary and alias problems

### Shared patterns

`DictFrame.registerStack` registers each form under **all suffix aliases**, and additionally registers each ingot under the **bare suffix** for NuclearCraft compatibility. This is deliberate compatibility, not automatically an accidental duplicate. `DictGroup` re-registration creates recipe substitution groups, with recursion protection. `oreAll` uses wildcard metadata while mixed stone/basalt resource ores use exact enum metadata. [ODM]

| Pattern / exception | Current meaning / risk |
|---|---|
| Standard metal prefixes | ingot/dust/nugget/plate/block plus billet, wireFine/wireDense, triple/sextuple plates and custom shapes; actual registrations vary |
| `dustSmall(...)` method | Registers **dustTiny**, quantity 8; do not infer GT6 dustSmall=18 q from method name |
| GT6-specific prefixes | Conditional blockDust/blockGem/blockIngot/blockSolid; crushed/crushedTiny; purified/centrifuged crushed and tiny; dustSmall, dustDiv72, dustImpure/Pure/Refined. noAutogen excludes automatic display/arc recipes but not Mats runtime recognition |
| GT6 uranium rename | Generic U DictFrame uses Uraninite when GT6 loaded; meaning and ore mapping become modpack dependent |
| ST / SR | Two Strontium DictFrames, same powder; duplicate definition, not two elements |
| OSMIRIDIUM | Repeated nugget/ingot registrations; same material/item, not distinct quantity. Finished label also covers an osmium extraction abstraction |
| MAT_PLATNIUM / `Platnium` | Misspelled but actual production identity; normal `dustPlatinum`/`ingotPlatinum` is not automatically that identity |
| `Tantalium`, `Lanthanum`, lowercase `conglomerate` | Actual keys/spellings; name similarities cannot substitute a new suffix |
| NETHERQUARTZ vs QUARTZ | Powder registered to both; crystalline/gem/block consumers use different keys. Some support depends on Forge/external registrations |
| Antimony dust | `powder_antimony_trioxide` registered as `dustAntimony`, while nugget is metal: generic substitution can erase oxidation state |
| Schrabidium | Commented direct registration chain, active standalone items, active generated shape keys |
| Old standalone wires | Eight legacy IDs registered, their `wireFine` registration lines commented; metadata `wire_fine` is the active generated form |
| Literal `ingotNetherBrick` | CraftingManager's polymer-plate alternative uses this spelling, while Forge/KEY_NETHERBRICK register `ingotBrickNether`; no native registration for the reversed spelling found. External mods could supply it, but stock alternate feed is disconnected [CRAFT] |
| Enriched ore aliases | `ore_enriched:<old enum>` registered as ordinary `oreX`; material identity survives, grade does not |
| Glass/construction groups | `blockGlass` is recipe substitution, not nine ingots of a material. Current shredder raw/storage allowlists explicitly avoid treating every `block...` alias as salvage |
| Recipes with absent keys | Generation flags do not prove e.g. `nuggetTin`, `dustTin`, or isotope dust exists; imprint prefixes without a suffix are special prefix matchers, not missing literal ores |

GT6 quantities, in q: storage aliases 648; crushed 81, tiny 9; purified 90/tiny10; centrifuged 99/tiny11; dustSmall18; dustDiv72=1; dustImpure80; dustPure88; dustRefined96. These are compatibility accounting assumptions; runtime GT6 item recipes were not audited to prove matching real content. [SHAPES]

## 16. Dead, legacy and unused forms

| Inventory / system | Classification | Evidence and reason |
|---|---|---|
| Ten old bedrock stage items, 19 types: **190 variants** | **Legacy but still reachable** | Registered items, stock bedrock definitions/worldgen and working stock stage generators; not superseded merely because new class exists [ITEMS] [OLD] [WORLD] |
| New NBT `bedrock_ore_base` and 6×26 grades: **157 representations** | **Stock-unobtainable candidates / incomplete source path** | Base item registered/hidden in NEI, sole stock producer reference commented, slopper requires it. Commands, old saves, custom machines/modpack configs could supply it; do not delete on static evidence alone [BASEORE] [SLOPPER] [WORLD] |
| Eight standalone wire items | **Registered legacy; no active source/use found** | Registered in ModItems; active OD registrations commented; production non-registration reference search found no consumers/producers. Metadata fine wire is active replacement [ITEMS] [ODM] |
| Ten `heavy_component:<mat.id>` variants | **Registered/creative-only candidates in stock** | Ti, Cu, Al, W, Pb, Steel, AdvancedAlloy, Desh, TCA, CDA. Quantity is 768 ingots each; assembler's 256-triple-plate generator is commented out, no active native producer/consumer found, no heavy-component mold. External WarTec/config recipes may change reachability [MATS] [ODM] [ASSEMBLER] [MOLD] |
| `HbmMaterials` / Mat IRON/GOLD/STEEL | **Dead internal scaffold** | Class has no production callers; not a hidden material source of truth [INDEX] |
| `MachineRecipes` old shredder region | **Obsolete implementation text** | Comment says old code removed; current ShredderRecipes/loader authoritative [MACHINE] |
| `setAutogen` flags with no implemented form item generator | **Referenced/intended only** | Cannot count DUST/NUGGET/BLOCK/PLATE/BILLET flag as registered item; partial families [ODM] [MATS] |
| Chemical intermediates / concentrates / oxides | **Active** | Named extraction routes; apparent low reuse is not proof of dead content [CHEM] [CENTRIFUGE] [CRYSTAL] |
| Nuclear waste/depleted forms | **Active / state-specific** | Decay, cooling, reprocessing, irradiation and manual compression; not “cruft” because not general melt feed [NUCLEAR] |
| Generated grips/stocks for organics | **Active nonmetal shape family** | ItemAutogen flags and group substitutions; quantities describe recipe abstraction, not metallurgical mass [AUTOGEN] [ODM] |
| Unregistered DictFrames such as AL_OXIDE, PET, CN989, AMF, MIN | **Uncertain / intended compatibility or legacy** | Declaration alone doesn't make OD entries. Bare item recipes or external registration can still matter |
| `registerSFAuto` helper in solidifier/pyro oven | **Uncalled legacy fuel conversion helper** | Definitions remain; production call blocks commented. Do not report fluid→solid fuel recipes as currently generated [SOLIDIFY] [PYRO] |
| `DONOTMODIFY/hbmspace` forms/recipes | **Reference-only** | Separate source tree outside current build; not an active fallback |

No modpack-wide “dead” total is claimed: enabled recipe JSON, IMC, loot, saves and commands can supply forms. The exact 190/157/8/10 counts above have explicit source boundaries; they must not be summed as one verified deletion list.

## 17. High-impact exceptions and ranked findings

| Rank | Findings |
|---|---|
| **Critical** | ID 1500 collision; steel pipe assembly recovery multiplication (MB02) |
| **High** | HSS output doubling (MB01); powder precursor steel multiplication (MB03); zero-output ore consumption (MB05); physical fuel form omissions/coke identity collapse; iron powder chemical-state ambiguity; manual alloy definitions conflict |
| **Medium** | Custom mold/salvage discrepancies with unresolved loss intent (MB04); new ore pipeline stock source disconnected; old bedrock metadata 10 branch points at 9; primary-alias generation divergence; arc gate bypass; lack of canonical physical quantity/composition bridge |
| **Low** | Duplicate same-value OSMIRIDIUM/Strontium registrations; typo identities needing deliberate aliases; standalone legacy wires/scaffold; display gaps; undeclared pure-form rounding losses |

Ranks describe risk to the intended next metallurgy stage, not observed server damage. Source-disconnected content and deliberate incompatibility are kept distinct from confirmed arithmetic defects.

## 18. RealSim opportunities

| Future mechanic | Existing infrastructure worth reusing | Missing semantic/quantity contract |
|---|---|---|
| Ore concentration / beneficiation | Centrifuge, acidizer/crystallizer, old/new ore grades, mineral-specific byproducts and NBT base fractions | Feed assay, grade, retained gangue, oxidation state and recovery definition |
| Sintering | Existing `er_yag_feedstock`→`er_yag_sintered`, `yby_feedstock`→`yby_sintered` furnace/chemical paths; hot NBT forming | No generic iron sinter form, mineral composition or agglomeration mass rule |
| Pelletizing | Fuel pellet classes, press/briquette mechanics, metadata item generation | Reactor pellet semantics are unsuitable for ore pellets; binder/moisture/ore-grade representation needed |
| Coke preparation | Three coke grades, powder/briquette/storage, mass-accounted coke/wood/tar bills | Grade-preserving burner mapping, physical form ratios, compatibility between carbon assay and fuel assay |
| Bloomery | Hematite/malachite and flux/additive reaction patterns, heat-source interface, slag/recovery | Explicit oxide→metal/reductant/gas bill, bloom/slag separation, process temperature |
| Hot metal handling | Mats quantities/network, crucible/arc list outputs, molds/strand caster, source heat interfaces | Molten material temperature/enthalpy/oxidation and composition are not carried by MaterialStack |
| Steelmaking | Industrial blast bills, steel/stainless/HSS/weapon/tool alloy routes, gas coproducts | Stable alloy identity/composition and conserved metallic input across every authority |
| Recycling | NBT quantity scraps, exact salvage, anvil chance recovery and conservative shredder generation | Composite bill of materials, explicit recovery losses, no duplicate generic/custom credit |
| Explicit alloy composition | Mats stable IDs and saved material quantities, recipe input material arrays | Composition belongs in a new canonical contract; not simply inferred from whichever recipe made an item |

**Answer on item proliferation:** there is enough processing, metadata and transport infrastructure to avoid inventing a standalone item for every operation/material. There is **not** enough existing semantic definition to reinterpret all `dustX` as concentrate or to use nuclear pellets as iron pellets. A small deliberate grade/form representation at the correct boundary could support concentrate/sinter/pellet progression; assay and phase definitions must come first. This audit proposes no new item IDs or gameplay implementation.

## 19. Recommended cleanup order and top ten prerequisites

1. **Resolve the ID 1500 collision with save-aware migration design.** Identify old phosphorus/magnesium metadata and saved foundry contents; never simply renumber and reinterpret old saves.
2. **Close exact material creation/destruction defects:** pipe assembly, HSS recipe 14, powder-steel precursor, zero-q ores, and custom mold/salvage quantity mismatch. Preserve ore-recovery bonuses pending assay decisions.
3. **Reconcile actual fuel form/grade matching across intended consumers.** Lignite, briquettes, coke blocks, solid fuel and per-grade coke must have explicit authority; preserve chemical heat vs reductant vs legacy equipment distinctions.
4. **Define iron dust's intended chemical meaning.** Trace ore/ingot powder, thermite, alloying, reduction and direct melting as one dependency graph before adding concentrates.
5. **Define the quantity boundary between foundry q, physical solid mass and fluid volumes.** Keep compatible external formats; do not turn a display 144 into a universal chemical volume.
6. **Establish intended alloy compositions and retain deliberate alternate recipes explicitly.** Steel/stainless/tool/HSS/gunmetal/bronze currently disagree; finish this before steelmaking calibration.
7. **Make form support truthful.** Separate registered/obtainable/accepted flags; classify tin, isotope powders, Schrabidium and arc-gated powders; preserve existing intentional absence restrictions.
8. **Choose supported ore preparation paths and reconnect only the intended ones.** Old bedrock is active; newer graded bedrock lacks its stock base producer. Correct metadata-based special-case indexing deliberately.
9. **Reconcile casting, salvage and aliases at their boundaries.** Retain NBT scrap quantity, distinguish alloys/composites, specify losses, and keep GT6/alias-only runtime vs generated recipe behavior visible.
10. **Then design concentrate→sinter/pellet→reduction progression.** Reuse the existing machines, thermal interfaces, metadata and waste handling, introducing only the state distinctions the chosen ore assay actually needs.

Dependencies: identity safety precedes persistence/form migration; conservation and fuel authority precede economic/energy balancing; chemical-state and alloy contracts precede deeper metallurgy; source reachability and compatibility boundaries precede removing legacy forms. This is a later implementation order, not authorization to change these systems during this audit.

## Evidence index

Links are repository-relative. Named methods/IDs are the stable evidence anchors; source line numbers may move with the already-present working changes. Each significant grouped finding references its shared generator/registry here, with exception IDs and identifiers in the associated table.

### Evidence file paths

| Reference | Source path |
|---|---|
| BUILD | [build.gradle](../../../build.gradle) |
| INIT | [src/main/java/com/hbm/main/MainRegistry.java](../../../src/main/java/com/hbm/main/MainRegistry.java) |
| ITEMS | [src/main/java/com/hbm/items/ModItems.java](../../../src/main/java/com/hbm/items/ModItems.java) |
| ODM | [src/main/java/com/hbm/inventory/OreDictManager.java](../../../src/main/java/com/hbm/inventory/OreDictManager.java) |
| MATS | [src/main/java/com/hbm/inventory/material/Mats.java](../../../src/main/java/com/hbm/inventory/material/Mats.java) |
| NTM | [src/main/java/com/hbm/inventory/material/NTMMaterial.java](../../../src/main/java/com/hbm/inventory/material/NTMMaterial.java) |
| SHAPES | [src/main/java/com/hbm/inventory/material/MaterialShapes.java](../../../src/main/java/com/hbm/inventory/material/MaterialShapes.java) |
| DIST | [src/main/java/com/hbm/inventory/material/MatDistribution.java](../../../src/main/java/com/hbm/inventory/material/MatDistribution.java) |
| AUTOGEN | [src/main/java/com/hbm/items/special/ItemAutogen.java](../../../src/main/java/com/hbm/items/special/ItemAutogen.java) |
| MATCH | [src/main/java/com/hbm/inventory/RecipesCommon.java](../../../src/main/java/com/hbm/inventory/RecipesCommon.java) |
| LOAD | [src/main/java/com/hbm/inventory/recipes/loader/SerializableRecipe.java](../../../src/main/java/com/hbm/inventory/recipes/loader/SerializableRecipe.java) |
| CRUCIBLE | [src/main/java/com/hbm/inventory/recipes/CrucibleRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/CrucibleRecipes.java) |
| CRUCIBLETE | [src/main/java/com/hbm/tileentity/machine/TileEntityCrucible.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityCrucible.java) |
| SCRAP | [src/main/java/com/hbm/items/machine/ItemScraps.java](../../../src/main/java/com/hbm/items/machine/ItemScraps.java) |
| MOLD | [src/main/java/com/hbm/items/machine/ItemMold.java](../../../src/main/java/com/hbm/items/machine/ItemMold.java) |
| FOUNDRY | [src/main/java/com/hbm/tileentity/machine/TileEntityFoundryBase.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityFoundryBase.java) |
| CASTTE | [src/main/java/com/hbm/tileentity/machine/TileEntityFoundryCastingBase.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityFoundryCastingBase.java) |
| CASTBLOCK | [src/main/java/com/hbm/blocks/machine/FoundryCastingBase.java](../../../src/main/java/com/hbm/blocks/machine/FoundryCastingBase.java) |
| STRAND | [src/main/java/com/hbm/tileentity/machine/TileEntityMachineStrandCaster.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityMachineStrandCaster.java) |
| ARCTE | [src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnaceLarge.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnaceLarge.java) |
| ARC | [src/main/java/com/hbm/inventory/recipes/ArcFurnaceRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/ArcFurnaceRecipes.java) |
| CRAFT | [src/main/java/com/hbm/main/CraftingManager.java](../../../src/main/java/com/hbm/main/CraftingManager.java) |
| POWDER | [src/main/java/com/hbm/crafting/PowderRecipes.java](../../../src/main/java/com/hbm/crafting/PowderRecipes.java) |
| MINERAL | [src/main/java/com/hbm/crafting/MineralRecipes.java](../../../src/main/java/com/hbm/crafting/MineralRecipes.java) |
| SMELT | [src/main/java/com/hbm/crafting/SmeltingRecipes.java](../../../src/main/java/com/hbm/crafting/SmeltingRecipes.java) |
| SHREDDER | [src/main/java/com/hbm/inventory/recipes/ShredderRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/ShredderRecipes.java) |
| CENTRIFUGE | [src/main/java/com/hbm/inventory/recipes/CentrifugeRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/CentrifugeRecipes.java) |
| CRYSTAL | [src/main/java/com/hbm/inventory/recipes/CrystallizerRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/CrystallizerRecipes.java) |
| CHEM | [src/main/java/com/hbm/inventory/recipes/ChemplantRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/ChemplantRecipes.java) |
| BLAST | [src/main/java/com/hbm/inventory/recipes/BlastFurnaceRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/BlastFurnaceRecipes.java) |
| IBLAST | [src/main/java/com/hbm/inventory/recipes/IndustrialBlastFurnaceRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/IndustrialBlastFurnaceRecipes.java) |
| BFPROCESS | [src/main/java/com/hbm/realsim/BlastFurnaceProcess.java](../../../src/main/java/com/hbm/realsim/BlastFurnaceProcess.java) |
| ROTARY | [src/main/java/com/hbm/inventory/recipes/RotaryFurnaceRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/RotaryFurnaceRecipes.java) |
| PRESS | [src/main/java/com/hbm/inventory/recipes/PressRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/PressRecipes.java) |
| WELDER | [src/main/java/com/hbm/inventory/recipes/ArcWelderRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/ArcWelderRecipes.java) |
| ASSEMBLER | [src/main/java/com/hbm/inventory/recipes/AssemblerRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/AssemblerRecipes.java) |
| ANVIL | [src/main/java/com/hbm/inventory/recipes/anvil/AnvilRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/anvil/AnvilRecipes.java) |
| EMETAL | [src/main/java/com/hbm/inventory/recipes/ElectrolyserMetalRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/ElectrolyserMetalRecipes.java) |
| EFLUID | [src/main/java/com/hbm/inventory/recipes/ElectrolyserFluidRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/ElectrolyserFluidRecipes.java) |
| ELECTE | [src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java) |
| EXCAVATOR | [src/main/java/com/hbm/tileentity/machine/TileEntityMachineExcavator.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExcavator.java) |
| CUSTOM | [src/main/java/com/hbm/inventory/recipes/CustomMachineRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/CustomMachineRecipes.java) |
| COMBINE | [src/main/java/com/hbm/inventory/recipes/CombinationRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/CombinationRecipes.java) |
| PYRO | [src/main/java/com/hbm/inventory/recipes/PyroOvenRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/PyroOvenRecipes.java) |
| COKER | [src/main/java/com/hbm/inventory/recipes/CokerRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/CokerRecipes.java) |
| MIXER | [src/main/java/com/hbm/inventory/recipes/MixerRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/MixerRecipes.java) |
| LIQUEFY | [src/main/java/com/hbm/inventory/recipes/LiquefactionRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/LiquefactionRecipes.java) |
| SOLIDIFY | [src/main/java/com/hbm/inventory/recipes/SolidificationRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/SolidificationRecipes.java) |
| OLD | [src/main/java/com/hbm/items/special/ItemBedrockOre.java](../../../src/main/java/com/hbm/items/special/ItemBedrockOre.java) |
| NEW | [src/main/java/com/hbm/items/special/ItemBedrockOreNew.java](../../../src/main/java/com/hbm/items/special/ItemBedrockOreNew.java) |
| BASEORE | [src/main/java/com/hbm/items/special/ItemBedrockOreBase.java](../../../src/main/java/com/hbm/items/special/ItemBedrockOreBase.java) |
| SLOPPER | [src/main/java/com/hbm/tileentity/machine/TileEntityMachineOreSlopper.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityMachineOreSlopper.java) |
| WORLD | [src/main/java/com/hbm/lib/HbmWorldGen.java](../../../src/main/java/com/hbm/lib/HbmWorldGen.java) |
| INDEX | [src/main/java/com/hbm/handler/indexing/HbmMaterials.java](../../../src/main/java/com/hbm/handler/indexing/HbmMaterials.java) |
| MACHINE | [src/main/java/com/hbm/inventory/recipes/MachineRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/MachineRecipes.java) |
| PHYSICAL | [src/main/java/com/hbm/realsim/PhysicalRegistry.java](../../../src/main/java/com/hbm/realsim/PhysicalRegistry.java) |
| FLUIDS | [src/main/java/com/hbm/inventory/fluid/Fluids.java](../../../src/main/java/com/hbm/inventory/fluid/Fluids.java) |
| DEMAND | [src/main/java/com/hbm/realsim/IndustrialDemand.java](../../../src/main/java/com/hbm/realsim/IndustrialDemand.java) |
| FUEL | [src/main/java/com/hbm/handler/FuelHandler.java](../../../src/main/java/com/hbm/handler/FuelHandler.java) |
| BURNMODULE | [src/main/java/com/hbm/module/ModuleBurnTime.java](../../../src/main/java/com/hbm/module/ModuleBurnTime.java) |
| COMBUST | [src/main/java/com/hbm/realsim/Combustion.java](../../../src/main/java/com/hbm/realsim/Combustion.java) |
| CREDIT | [src/main/java/com/hbm/realsim/FuelCredit.java](../../../src/main/java/com/hbm/realsim/FuelCredit.java) |
| FIREBOXBASE | [src/main/java/com/hbm/tileentity/machine/TileEntityFireboxBase.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityFireboxBase.java) |
| FIREBOX | [src/main/java/com/hbm/tileentity/machine/TileEntityHeaterFirebox.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterFirebox.java) |
| OVEN | [src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java) |
| BOILERI | [src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerIndustrial.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerIndustrial.java) |
| WOODBURN | [src/main/java/com/hbm/tileentity/machine/TileEntityMachineWoodBurner.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityMachineWoodBurner.java) |
| OILBURN | [src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOilburner.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOilburner.java) |
| DIESEL | [src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java) |
| ENGINE | [src/main/java/com/hbm/tileentity/machine/TileEntityMachineCombustionEngine.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCombustionEngine.java) |
| GASTURBINE | [src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java) |
| DIFURNACE | [src/main/java/com/hbm/tileentity/machine/TileEntityDiFurnace.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityDiFurnace.java) |
| IRONFURNACE | [src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceIron.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceIron.java) |
| NUCLEAR | [src/main/java/com/hbm/items/machine/ItemPWRFuel.java](../../../src/main/java/com/hbm/items/machine/ItemPWRFuel.java) |
| RTGITEM | [src/main/java/com/hbm/items/machine/ItemRTGPellet.java](../../../src/main/java/com/hbm/items/machine/ItemRTGPellet.java) |
| RTGTE | [src/main/java/com/hbm/tileentity/machine/TileEntityMachineRTG.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRTG.java) |
| OUTGAS | [src/main/java/com/hbm/inventory/recipes/OutgasserRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/OutgasserRecipes.java) |
| SILEX | [src/main/java/com/hbm/inventory/recipes/SILEXRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/SILEXRecipes.java) |
| SILEXTE | [src/main/java/com/hbm/tileentity/machine/TileEntitySILEX.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntitySILEX.java) |
| FUSION | [src/main/java/com/hbm/inventory/recipes/FusionRecipes.java](../../../src/main/java/com/hbm/inventory/recipes/FusionRecipes.java) |
| CORE | [src/main/java/com/hbm/tileentity/machine/TileEntityCore.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntityCore.java) |
| THERMALSOURCES | [src/main/java/com/hbm/tileentity/machine/TileEntitySolarBoiler.java](../../../src/main/java/com/hbm/tileentity/machine/TileEntitySolarBoiler.java); `TileEntityHeaterElectric`, `TileEntityHeaterHeatex` in the same package |
| TURBINEFUEL | [src/main/java/com/hbm/realsim/TurbineFuelAccount.java](../../../src/main/java/com/hbm/realsim/TurbineFuelAccount.java), `suitable`/`burn` |

[BUILD]: ../build.gradle
[INIT]: ../src/main/java/com/hbm/main/MainRegistry.java
[ITEMS]: ../src/main/java/com/hbm/items/ModItems.java
[ODM]: ../src/main/java/com/hbm/inventory/OreDictManager.java
[MATS]: ../src/main/java/com/hbm/inventory/material/Mats.java
[NTM]: ../src/main/java/com/hbm/inventory/material/NTMMaterial.java
[SHAPES]: ../src/main/java/com/hbm/inventory/material/MaterialShapes.java
[DIST]: ../src/main/java/com/hbm/inventory/material/MatDistribution.java
[AUTOGEN]: ../src/main/java/com/hbm/items/special/ItemAutogen.java
[MATCH]: ../src/main/java/com/hbm/inventory/RecipesCommon.java
[LOAD]: ../src/main/java/com/hbm/inventory/recipes/loader/SerializableRecipe.java
[CRUCIBLE]: ../src/main/java/com/hbm/inventory/recipes/CrucibleRecipes.java
[CRUCIBLETE]: ../src/main/java/com/hbm/tileentity/machine/TileEntityCrucible.java
[SCRAP]: ../src/main/java/com/hbm/items/machine/ItemScraps.java
[MOLD]: ../src/main/java/com/hbm/items/machine/ItemMold.java
[FOUNDRY]: ../src/main/java/com/hbm/tileentity/machine/TileEntityFoundryBase.java
[CASTTE]: ../src/main/java/com/hbm/tileentity/machine/TileEntityFoundryCastingBase.java
[CASTBLOCK]: ../src/main/java/com/hbm/blocks/machine/FoundryCastingBase.java
[STRAND]: ../src/main/java/com/hbm/tileentity/machine/TileEntityMachineStrandCaster.java
[ARCTE]: ../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnaceLarge.java
[ARC]: ../src/main/java/com/hbm/inventory/recipes/ArcFurnaceRecipes.java
[CRAFT]: ../src/main/java/com/hbm/main/CraftingManager.java
[POWDER]: ../src/main/java/com/hbm/crafting/PowderRecipes.java
[MINERAL]: ../src/main/java/com/hbm/crafting/MineralRecipes.java
[SMELT]: ../src/main/java/com/hbm/crafting/SmeltingRecipes.java
[SHREDDER]: ../src/main/java/com/hbm/inventory/recipes/ShredderRecipes.java
[CENTRIFUGE]: ../src/main/java/com/hbm/inventory/recipes/CentrifugeRecipes.java
[CRYSTAL]: ../src/main/java/com/hbm/inventory/recipes/CrystallizerRecipes.java
[CHEM]: ../src/main/java/com/hbm/inventory/recipes/ChemplantRecipes.java
[BLAST]: ../src/main/java/com/hbm/inventory/recipes/BlastFurnaceRecipes.java
[IBLAST]: ../src/main/java/com/hbm/inventory/recipes/IndustrialBlastFurnaceRecipes.java
[BFPROCESS]: ../src/main/java/com/hbm/realsim/BlastFurnaceProcess.java
[ROTARY]: ../src/main/java/com/hbm/inventory/recipes/RotaryFurnaceRecipes.java
[PRESS]: ../src/main/java/com/hbm/inventory/recipes/PressRecipes.java
[WELDER]: ../src/main/java/com/hbm/inventory/recipes/ArcWelderRecipes.java
[ASSEMBLER]: ../src/main/java/com/hbm/inventory/recipes/AssemblerRecipes.java
[ANVIL]: ../src/main/java/com/hbm/inventory/recipes/anvil/AnvilRecipes.java
[EMETAL]: ../src/main/java/com/hbm/inventory/recipes/ElectrolyserMetalRecipes.java
[EFLUID]: ../src/main/java/com/hbm/inventory/recipes/ElectrolyserFluidRecipes.java
[ELECTE]: ../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java
[EXCAVATOR]: ../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExcavator.java
[CUSTOM]: ../src/main/java/com/hbm/inventory/recipes/CustomMachineRecipes.java
[COMBINE]: ../src/main/java/com/hbm/inventory/recipes/CombinationRecipes.java
[PYRO]: ../src/main/java/com/hbm/inventory/recipes/PyroOvenRecipes.java
[COKER]: ../src/main/java/com/hbm/inventory/recipes/CokerRecipes.java
[MIXER]: ../src/main/java/com/hbm/inventory/recipes/MixerRecipes.java
[LIQUEFY]: ../src/main/java/com/hbm/inventory/recipes/LiquefactionRecipes.java
[SOLIDIFY]: ../src/main/java/com/hbm/inventory/recipes/SolidificationRecipes.java
[OLD]: ../src/main/java/com/hbm/items/special/ItemBedrockOre.java
[NEW]: ../src/main/java/com/hbm/items/special/ItemBedrockOreNew.java
[BASEORE]: ../src/main/java/com/hbm/items/special/ItemBedrockOreBase.java
[SLOPPER]: ../src/main/java/com/hbm/tileentity/machine/TileEntityMachineOreSlopper.java
[WORLD]: ../src/main/java/com/hbm/lib/HbmWorldGen.java
[INDEX]: ../src/main/java/com/hbm/handler/indexing/HbmMaterials.java
[MACHINE]: ../src/main/java/com/hbm/inventory/recipes/MachineRecipes.java
[PHYSICAL]: ../src/main/java/com/hbm/realsim/PhysicalRegistry.java
[FLUIDS]: ../src/main/java/com/hbm/inventory/fluid/Fluids.java
[DEMAND]: ../src/main/java/com/hbm/realsim/IndustrialDemand.java
[FUEL]: ../src/main/java/com/hbm/handler/FuelHandler.java
[BURNMODULE]: ../src/main/java/com/hbm/module/ModuleBurnTime.java
[COMBUST]: ../src/main/java/com/hbm/realsim/Combustion.java
[CREDIT]: ../src/main/java/com/hbm/realsim/FuelCredit.java
[FIREBOXBASE]: ../src/main/java/com/hbm/tileentity/machine/TileEntityFireboxBase.java
[FIREBOX]: ../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterFirebox.java
[OVEN]: ../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java
[BOILERI]: ../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerIndustrial.java
[WOODBURN]: ../src/main/java/com/hbm/tileentity/machine/TileEntityMachineWoodBurner.java
[OILBURN]: ../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOilburner.java
[DIESEL]: ../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java
[ENGINE]: ../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCombustionEngine.java
[GASTURBINE]: ../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java
[DIFURNACE]: ../src/main/java/com/hbm/tileentity/machine/TileEntityDiFurnace.java
[IRONFURNACE]: ../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceIron.java
[NUCLEAR]: ../src/main/java/com/hbm/items/machine/ItemPWRFuel.java
[RTGITEM]: ../src/main/java/com/hbm/items/machine/ItemRTGPellet.java
[RTGTE]: ../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRTG.java
[OUTGAS]: ../src/main/java/com/hbm/inventory/recipes/OutgasserRecipes.java
[SILEX]: ../src/main/java/com/hbm/inventory/recipes/SILEXRecipes.java
[SILEXTE]: ../src/main/java/com/hbm/tileentity/machine/TileEntitySILEX.java
[FUSION]: ../src/main/java/com/hbm/inventory/recipes/FusionRecipes.java
[CORE]: ../src/main/java/com/hbm/tileentity/machine/TileEntityCore.java
[THERMALSOURCES]: ../src/main/java/com/hbm/tileentity/machine/TileEntitySolarBoiler.java
[TURBINEFUEL]: ../src/main/java/com/hbm/realsim/TurbineFuelAccount.java


## Appendix A. Registered form inventory by shared signature

This is the concrete source inventory for all **125 Mats records**, grouped by identical **registered form sets, nominal melt category and conversion**. It includes the exact Forge 1.7.10-10.13.4.1614 vanilla OD defaults, inspected in the cached dependency source `net/minecraftforge/oredict/OreDictionary.java`, `initVanillaEntries()` (lines 59–120). It does not include external mod-added forms. These signatures do **not** erase the manually documented recipe/casting/chemical differences.
Member names below are the exact suffix after `Mats.MAT_`; each row shares its printed form/quantity rule. `melt` = identity SMELTABLE; `add` = identity ADDITIVE; `reject` = nonconverting NOT_SMELTABLE; `convert` names the target ratio. `FRAGMENT` is the metadata bedrock fragment; `DUSTTINY` is dustTiny. Bare suffix/tiny aliases do not constitute additional physical forms. A missing form is not implied by an unused autogen flag.

| Common registered forms | Members | Melt/conversion rule |
|---|---|---|
| BILLET, INGOT, NUGGET | PU241, RGA, AM241, AM242, PB209, GHIORSIUM, GAAS | melt |
| BILLET, BLOCK, INGOT, NUGGET | U233, U235, RGP, PU238, PU239, PU240, SOLINIUM | melt |
| INGOT | RHODIUM, IRIDIUM, TERBIUM, THULIUM, CALCINED_DOLOMITE | melt |
| none from generic prefix inventory | STONE, OBSIDIAN, WROUGHTIRON, PIGIRON, METEORICIRON | melt |
| DUST, INGOT | CALCIUM, CHROMIUM, GADOLINIUM | melt |
| BLOCK, DUST, GEM | COALCOKE, PETCOKE, LIGCOKE | convert: CARBON, 4, 3 |
| ORE | HEMATITE, MALACHITE, CONGLOMERATE | add |
| DUST, INGOT, NUGGET | INDIUM, SAMARIUM, PLATNIUM | melt |
| BLOCK, DUST, FRAGMENT, ORE | KNO, FLUORITE, PHOSPHORUS | reject |
| BILLET, BLOCK, DUST, FRAGMENT, INGOT, NUGGET | POLONIUM, RADIUM, BISMUTH | melt |
| INGOT, NUGGET | BARIUM, ARSENIC | melt |
| CASTPLATE, INGOT, LIGHTBARREL, LIGHTRECEIVER | BBRONZE, ABRONZE | melt |
| BILLET, DUST, INGOT, NUGGET | CO60, AU198 | melt |
| BLOCK, CASTPLATE, DUST, FRAGMENT, HEAVY_COMPONENT, INGOT, ORE, PIPE, PLATE, SHELL, WELDEDPLATE, WIRE | COPPER, ALUMINIUM | melt |
| GRIP, INGOT, STOCK | HARDPLASTIC, PVC | reject |
| BLOCK, DUST, DUSTTINY, FRAGMENT, INGOT, NUGGET, ORE | NIOBIUM, LANTHANIUM | melt |
| BLOCK, DUST, GRIP, INGOT, STOCK | POLYMER, BAKELITE | reject |
| BLOCK, INGOT | SCHRARANIUM, SLAG | melt |
| DUST, FRAGMENT, INGOT | SODIUM, STRONTIUM | melt |
| BILLET, FRAGMENT, INGOT, NUGGET | TECHNETIUM, SILICON | melt |
| BILLET, BLOCK, DUST, FRAGMENT, INGOT, NUGGET, ORE | THORIUM, BERYLLIUM | melt |
| BILLET, BLOCK, DUST, DUSTTINY, INGOT, NUGGET | ACTINIUM | melt |
| BLOCK, CASTPLATE, DENSEWIRE, DUST, HEAVY_COMPONENT, INGOT, PLATE, WIRE | ALLOY | melt |
| BLOCK, DUST, FRAGMENT, INGOT, ORE | ASBESTOS | melt |
| DUST, FRAGMENT, INGOT, ORE | BORAX | melt |
| BLOCK, DUST, DUSTTINY, FRAGMENT, INGOT, NUGGET | BORON | melt |
| DENSEWIRE, INGOT | BSCCO | melt |
| BLOCK, DUST, INGOT | CADMIUM | melt |
| BLOCK, INGOT, WIRE | CARBON | add |
| BLOCK, CASTPLATE, HEAVYBARREL, HEAVYRECEIVER, HEAVY_COMPONENT, INGOT, LIGHTBARREL, LIGHTRECEIVER, WELDEDPLATE | CDALLOY | melt |
| DUST, FRAGMENT | CHLOROCALCITE | reject |
| CRYSTAL, FRAGMENT, GEM, ORE | CINNABAR | reject |
| BLOCK, CASTPLATE, DUST, INGOT, NUGGET, PLATE, WELDEDPLATE | CMB | melt |
| BLOCK, DUST, DUSTTINY, FRAGMENT, GEM, ORE | COAL | convert: CARBON, 2, 1 |
| BILLET, BLOCK, DUST, DUSTTINY, FRAGMENT, INGOT, NUGGET, ORE | COBALT | melt |
| BLOCK, CASTPLATE, DUST, GRIP, HEAVYBARREL, HEAVY_COMPONENT, INGOT, LIGHTBARREL, LIGHTRECEIVER, NUGGET, STOCK | DESH | melt |
| BLOCK, DUST, FRAGMENT, GEM, ORE | DIAMOND | convert: CARBON, 1, 1 |
| BLOCK, DENSEWIRE, DUST, INGOT, NUGGET | DNT | melt |
| BLOCK, BOLT, DUST, GRIP, HEAVYBARREL, HEAVYRECEIVER, INGOT, LIGHTBARREL, LIGHTRECEIVER, PIPE, PLATE | DURA | melt |
| BLOCK, DUST, FRAGMENT, GEM, ORE | EMERALD | convert: BERYLLIUM, 4, 3 |
| HEAVYBARREL, HEAVYRECEIVER, INGOT | FERRO | melt |
| none from generic prefix inventory | FLUX | add |
| DUST, DUSTTINY, INGOT, NUGGET | GALLIUM | melt |
| BLOCK, CASTPLATE, DENSEWIRE, DUST, FRAGMENT, INGOT, NUGGET, ORE, PLATE, WIRE | GOLD | melt |
| BLOCK, INGOT | GRAPHITE | convert: CARBON, 1, 1 |
| GRIP, HEAVYBARREL, HEAVYRECEIVER, INGOT, LIGHTBARREL, LIGHTRECEIVER, MECHANISM, PLATE, STOCK | GUNMETAL | melt |
| BLOCK, CASTPLATE, DUST, FRAGMENT, INGOT, ORE, PIPE, PLATE, WELDEDPLATE | IRON | melt |
| GRIP | IVORY | reject |
| BLOCK, BOLT, CASTPLATE, DUST, FRAGMENT, HEAVY_COMPONENT, INGOT, NUGGET, ORE, PIPE, PLATE, WIRE | LEAD | melt |
| DUST, FRAGMENT, GEM, ORE | LIGNITE | convert: CARBON, 3, 1 |
| BLOCK, DUST, DUSTTINY, FRAGMENT, INGOT, ORE | LITHIUM | melt |
| FRAGMENT, INGOT, NUGGET | MAGNESIUM | melt |
| BLOCK, DENSEWIRE, DUST, INGOT, WIRE | MAGTUNG | melt |
| BILLET, BLOCK, DUST, INGOT, WIRE | MINGRADE | melt |
| DUST, FRAGMENT, ORE | MOLYSITE | reject |
| DENSEWIRE, DUST, DUSTTINY, FRAGMENT, INGOT, NUGGET, ORE, ORENETHER | NEODYMIUM | melt |
| BILLET, BLOCK, DUST, INGOT, NUGGET | NEPTUNIUM | melt |
| BLOCK, DUST, INGOT, NUGGET, ORE, PLATE | NICKEL | melt |
| BLOCK, CASTPLATE, INGOT, NUGGET, WELDEDPLATE | OSMIRIDIUM | melt |
| BILLET, BLOCK, DUST, INGOT, NUGGET, ORE, ORENETHER | PLUTONIUM | melt |
| FRAGMENT, INGOT, ORE | RAREEARTH | reject |
| BLOCK, DUST, FRAGMENT, ORE | REDSTONE | melt |
| BLOCK, GRIP, INGOT, PIPE | RUBBER | reject |
| CASTPLATE, GRIP, HEAVYBARREL, HEAVYRECEIVER, INGOT, LIGHTBARREL, LIGHTRECEIVER, MECHANISM, PLATE, SHELL, STOCK | SATURN | melt |
| BLOCK, CASTPLATE, DENSEWIRE, DUST, INGOT | SCHRABIDATE | melt |
| CASTPLATE, DENSEWIRE, WIRE | SCHRABIDIUM | melt |
| FRAGMENT, GEM | SODALITE | reject |
| INGOT, PLATE | STAINLESS | melt |
| BLOCK, DENSEWIRE, INGOT | STAR | melt |
| BLOCK, BOLT, CASTPLATE, DUST, DUSTTINY, GRIP, HEAVYBARREL, HEAVY_COMPONENT, INGOT, LIGHTBARREL, LIGHTRECEIVER, PIPE, PLATE, SHELL, WELDEDPLATE, WIRE | STEEL | melt |
| BLOCK, DUST, FRAGMENT, ORE, ORENETHER | SULFUR | reject |
| BLOCK, DUST, GEM, INGOT, NUGGET | TANTALIUM | melt |
| BLOCK, CASTPLATE, DUST, HEAVYBARREL, HEAVYRECEIVER, HEAVY_COMPONENT, INGOT, LIGHTBARREL, LIGHTRECEIVER, WELDEDPLATE | TCALLOY | melt |
| CASTPLATE, FRAGMENT, INGOT, ORE, PIPE, SHELL, WELDEDPLATE | TIN | melt |
| BLOCK, CASTPLATE, DUST, FRAGMENT, HEAVY_COMPONENT, INGOT, ORE, PLATE, SHELL, WELDEDPLATE | TITANIUM | melt |
| BLOCK, BOLT, CASTPLATE, DENSEWIRE, DUST, FRAGMENT, HEAVY_COMPONENT, INGOT, ORE, ORENETHER, WELDEDPLATE, WIRE | TUNGSTEN | melt |
| BILLET, BLOCK, FRAGMENT, INGOT, NUGGET | U238 | melt |
| BILLET, BLOCK, DUST, FRAGMENT, INGOT, NUGGET, ORE, ORENETHER | URANIUM | melt |
| GRIP, HEAVYBARREL, HEAVYRECEIVER, INGOT, LIGHTBARREL, LIGHTRECEIVER, MECHANISM, PLATE, SHELL, STOCK | WEAPONSTEEL | melt |
| GRIP, STOCK | WOOD | reject |
| DUST, INGOT, NUGGET, ORE, WIRE | ZINC | melt |
| BILLET, BLOCK, CASTPLATE, DUST, FRAGMENT, INGOT, NUGGET, ORE, WELDEDPLATE, WIRE | ZIRCONIUM | melt |

Stock explicit distributions for stone/obsidian, flux, ore mixtures, manufactured salvage, charcoal and ash are additional to this prefix inventory (§§4,8,10). Wrought/pig/meteoric iron names are external-compatibility hooks with no native form here. Their no-form row does not mean every such identity is unobtainable in a modpack. One source citation covers the common inventory: [MATS], [ODM], [SHAPES], [AUTOGEN]; custom exceptions: [DIST], [MOLD].

## Appendix B. Concrete OD identities outside Mats

These members share **only their listed registration pattern**, not full processing behavior. None gains crucible/casting support merely by being listed here. Groups/aliases are listed separately, and compound items with no OD registration remain in Appendix C. The rows cover every concrete DictFrame outside Mats; definitions with no registered forms are included. [ODM]

| Registered forms | DictFrame identifiers (canonical names in source) |
|---|---|
| DUST | K, Mn, Se, AT, CS, ST, BR, AT209 |
| no native form registration | AMF, CN989, PET, MIN, AL_OXIDE |
| BLOCK, DUST, INGOT, NUGGET | REIIUM, WEIDANIUM, UNOBTAINIUM, VERTICIUM, DAFFERGON |
| INGOT | Rb, Dy, Ho, Mo |
| DUST, DUSTTINY | I131, XE135, CS137 |
| DUST, INGOT, NUGGET | Tl, Ag, Pr |
| BLOCK, INGOT | FIBER, P_WHITE |
| INGOT, NUGGET | Hg, SC |
| BLOCK, DUST, GEM, ORE | LAPIS, QUARTZ |
| DUST, ORE | LIMESTONE, I |
| BILLET, INGOT, NUGGET | Pm, TM170 |
| DUST, NUGGET | Sb, Te |
| BILLET, BLOCK, DUST, INGOT, NUGGET, ORE | AUSTRALIUM |
| DUST, DUSTTINY, NUGGET | CE |
| BLOCK, DUST, INGOT, ORE | COLTAN |
| GEM, INGOT | LATEX |
| DUST, GEM, ORE | NETHERQUARTZ |
| NUGGET | Pa |
| DUST, INGOT, ORE | Pd |
| BILLET, DUST, DUSTTINY, INGOT, NUGGET | SR90 |
| BLOCK, DUST, DUSTTINY, INGOT, NUGGET | V |
| GEM, ORE | VOLCANIC |
| DUST, DUSTTINY, INGOT, NUGGET | Y |

Collection frames excluded from element counting: ANY_GUNPOWDER, ANY_SMOKELESS, ANY_PLASTICEXPLOSIVE, ANY_HIGHEXPLOSIVE, ANY_COKE/COKE, ANY_CONCRETE, ANY_BISMOID and ANY_ASH. The DictGroups are ANY_RUBBER, ANY_PLASTIC, ANY_HARDPLASTIC, ANY_RESISTANTALLOY, ANY_BISMOIDBRONZE and ANY_COAL_COKE. Their membership/prefix logic is in `registerGroups()`/DictGroup; they do not define a new alloy assay. ST and SR are the same Strontium suffix. Generic U is Uranium normally, Uraninite under GT6.

## Appendix C. Registered non-registry intermediates and specialized forms

The following inventory uses actual `GameRegistry.registerItem` calls, not fields alone. Each row groups a representation/recipe vocabulary; **membership does not imply chemical equivalence**. This supplemental inventory is a lexical registration census; ambiguous names are candidates, not chemically verified classifications. Some identifiers also appear in Mats/OD tables; they are not counted as additional elements. This closes the search beyond obvious material registries, including standalone grades, chemistry, fuel and nuclear assemblies. [ITEMS]

| Representation group | Registered identifiers |
|---|---|
| Standalone mineral feed/chunk/shard/fragment | `bornite`, `chalcocite`, `chalcopyrite`, `chromite`, `chunk_carbon`, `chunk_chromite`, `chunk_coppersulfide`, `chunk_evaporite`, `chunk_heavymineralsand`, `chunk_ironoxide`, `chunk_leadzincsulfide`, `chunk_lithiumpegmatite`, `chunk_nickelsulfide`, `chunk_ore`, `chunk_tintungsten`, `cinnebar`, `covellite`, `crushed_petalite`, `crushed_spodumene`, `fragment_actinium`, `fragment_alien`, `fragment_boron`, `fragment_cerium`, `fragment_cobalt`, `fragment_coltan`, `fragment_dysprosium`, `fragment_erbium`, `fragment_europium`, `fragment_gadolinium`, `fragment_holmium`, `fragment_lanthanium`, `fragment_lutetium`, `fragment_meteorite`, `fragment_neodymium`, `fragment_niobium`, `fragment_praseodymium`, `fragment_samarium`, `fragment_terbium`, `fragment_thulium`, `fragment_vanadium`, `fragment_ytterbium`, `fragment_yttrium`, `goethite`, `gypsum`, `ilmenite`, `magnetite`, `mineral_dust`, `mineral_fragment`, `petalite`, `pyrrhotite`, `spodumene`, `stibnite`, `wolframite` |
| Concentrate / precursor / graded powder | `er_yag_feedstock`, `gadolinium_dust_step`, `gadolinium_dust_tiny`, `powder_desh_mix`, `powder_desh_ready`, `powder_dysprosium_concentrate`, `powder_erbium_concentrate`, `powder_holmium_concentrate`, `powder_lutetium_concentrate`, `powder_nitan_mix`, `powder_semtex_mix`, `powder_spark_mix`, `powder_steel_dusted`, `powder_thulium_concentrate`, `powder_ytterbium_concentrate`, `yby_feedstock`, `znse_feedstock` |
| Oxide / sulfide / fluoride / chloride / salts | `ammonium_chloride`, `ammonium_nitrate`, `arsenic_trioxide`, `barium_carbonate`, `barium_oxide`, `barium_sulfide`, `calcined_dolomite`, `calcium_fluoride_crystal`, `cesium_salt`, `crystal_basaltic`, `iridium_oxide`, `itemsalt`, `magnesium_chloride`, `ore_nitrated`, `potassium_sulfate`, `powder_antimony_trioxide`, `powder_holmium_oxide`, `powder_strontium_oxide`, `powder_tellurium_dioxide`, `powder_terbium_fluoride`, `powder_terbium_oxide`, `powder_ytterbium_fluoride`, `powder_ytterbium_oxide`, `quicklime`, `rubidiumsalt`, `saltleaf`, `scandium_chloride`, `scandium_oxide`, `sodium_nitrate`, `sodium_sulfate`, `sodium_sulfide`, `strontium_chloride`, `strontium_sulfide`, `yttrium_chloride`, `yttrium_oxide` |
| Item-backed solution / slurry / precipitate / residue | `dysprosiumsol`, `europiumsol`, `gadoliniumsol`, `iridium_rich_residue`, `lutetiumsol`, `pgm_residue`, `powder_rhodium_solution`, `ruthenium_residue`, `terbiumsol`, `thuliumsol`, `titanium_trace_metals_slurry` |
| Sintered intermediates | `er_yag_sintered`, `yby_sintered` |
| Crystals | `BBO_crystal`, `CLBO_crystal`, `KDP_crystal`, `KTP_crystal`, `LBO_crystal`, `YbY_crystal`, `ZnSe_crystal`, `cerium_crystal`, `crystal_aluminium`, `crystal_beryllium`, `crystal_charred`, `crystal_cinnebar`, `crystal_cleaned`, `crystal_coal`, `crystal_cobalt`, `crystal_copper`, `crystal_diamond`, `crystal_fluorite`, `crystal_gold`, `crystal_horn`, `crystal_iron`, `crystal_lapis`, `crystal_lead`, `crystal_lithium`, `crystal_meth`, `crystal_mineral`, `crystal_nickel`, `crystal_niobium`, `crystal_niter`, `crystal_osmiridium`, `crystal_phosphorus`, `crystal_plutonium`, `crystal_rare`, `crystal_redstone`, `crystal_schrabidium`, `crystal_schraranium`, `crystal_starmetal`, `crystal_sulfur`, `crystal_thorium`, `crystal_titanium`, `crystal_trixite`, `crystal_tungsten`, `crystal_uranium`, `crystal_zinc`, `gem_alexandrite`, `gem_rad`, `gem_ruby`, `gem_sapphire`, `gem_sodalite`, `gem_tantalium`, `gem_volcanic`, `germanium_crystal`, `lithium_niobate_crystal`, `titanium_sapphire_crystal`, `visible_dopant_crystal`, `yttrium_aluminum_erbium_crystal` |
| Fuel / coke / briquette / char / tar / ash forms | `biomass`, `biomass_compressed`, `briquette`, `coal_infernal`, `coke`, `lignite`, `oil_tar`, `pellet_coal`, `powder_ash`, `powder_coke`, `powder_sawdust`, `rocket_fuel`, `solid_fuel`, `solid_fuel_bf`, `solid_fuel_presto`, `solid_fuel_presto_bf`, `solid_fuel_presto_triplet`, `solid_fuel_presto_triplet_bf` |
| Nuclear bulk fuel forms | `billet_americium_fuel`, `billet_cm_fuel`, `billet_mox_fuel`, `billet_neptunium_fuel`, `billet_plutonium_fuel`, `billet_schrabidium_fuel`, `billet_thorium_fuel`, `billet_uranium_fuel`, `ingot_americium_fuel`, `ingot_cm_fuel`, `ingot_mox_fuel`, `ingot_neptunium_fuel`, `ingot_plutonium_fuel`, `ingot_schrabidium_fuel`, `ingot_thorium_fuel`, `ingot_uranium_fuel`, `nugget_americium_fuel`, `nugget_cm_fuel`, `nugget_mox_fuel`, `nugget_neptunium_fuel`, `nugget_plutonium_fuel`, `nugget_schrabidium_fuel`, `nugget_thorium_fuel`, `nugget_uranium_fuel` |
| RBMK fuel rods | `rbmk_fuel_balefire`, `rbmk_fuel_balefire_gold`, `rbmk_fuel_bk247`, `rbmk_fuel_empty`, `rbmk_fuel_flashlead`, `rbmk_fuel_hea241`, `rbmk_fuel_hea242`, `rbmk_fuel_heaus`, `rbmk_fuel_hecm`, `rbmk_fuel_hen`, `rbmk_fuel_hep239`, `rbmk_fuel_hep241`, `rbmk_fuel_hes`, `rbmk_fuel_heu233`, `rbmk_fuel_heu235`, `rbmk_fuel_lea`, `rbmk_fuel_leaus`, `rbmk_fuel_lecm`, `rbmk_fuel_lep`, `rbmk_fuel_les`, `rbmk_fuel_mea`, `rbmk_fuel_mecm`, `rbmk_fuel_men`, `rbmk_fuel_mep`, `rbmk_fuel_mes`, `rbmk_fuel_meu`, `rbmk_fuel_mox`, `rbmk_fuel_po210be`, `rbmk_fuel_pu238be`, `rbmk_fuel_ra226be`, `rbmk_fuel_test`, `rbmk_fuel_thmeu`, `rbmk_fuel_ueu`, `rbmk_fuel_zfb_am_mix`, `rbmk_fuel_zfb_bismuth`, `rbmk_fuel_zfb_pu241` |
| RBMK fuel pellets | `rbmk_pellet_balefire`, `rbmk_pellet_balefire_gold`, `rbmk_pellet_bk247`, `rbmk_pellet_flashlead`, `rbmk_pellet_hea241`, `rbmk_pellet_hea242`, `rbmk_pellet_heaus`, `rbmk_pellet_hecm`, `rbmk_pellet_hen`, `rbmk_pellet_hep239`, `rbmk_pellet_hep241`, `rbmk_pellet_hes`, `rbmk_pellet_heu233`, `rbmk_pellet_heu235`, `rbmk_pellet_lea`, `rbmk_pellet_leaus`, `rbmk_pellet_lecm`, `rbmk_pellet_lep`, `rbmk_pellet_les`, `rbmk_pellet_mea`, `rbmk_pellet_mecm`, `rbmk_pellet_men`, `rbmk_pellet_mep`, `rbmk_pellet_mes`, `rbmk_pellet_meu`, `rbmk_pellet_mox`, `rbmk_pellet_po210be`, `rbmk_pellet_pu238be`, `rbmk_pellet_ra226be`, `rbmk_pellet_thmeu`, `rbmk_pellet_ueu`, `rbmk_pellet_zfb_am_mix`, `rbmk_pellet_zfb_bismuth`, `rbmk_pellet_zfb_pu241` |
| RTG/decay pellets | `pellet_rtg`, `pellet_rtg_actinium`, `pellet_rtg_americium`, `pellet_rtg_berkelium`, `pellet_rtg_cf251`, `pellet_rtg_cf252`, `pellet_rtg_cobalt`, `pellet_rtg_depleted`, `pellet_rtg_gold`, `pellet_rtg_lead`, `pellet_rtg_polonium`, `pellet_rtg_promethium`, `pellet_rtg_radium`, `pellet_rtg_strontium`, `pellet_rtg_tm170`, `pellet_rtg_weak` |
| Other reactor fuel/rod assemblies | `icf_pellet`, `icf_pellet_depleted`, `icf_pellet_empty`, `plate_fuel_mox`, `plate_fuel_pu238be`, `plate_fuel_pu239`, `plate_fuel_ra226be`, `plate_fuel_sa326`, `plate_fuel_u233`, `plate_fuel_u235`, `pwr_fuel`, `pwr_fuel_depleted`, `pwr_fuel_hot`, `rod_dual`, `rod_dual_empty`, `rod_empty`, `rod_quad`, `rod_quad_empty`, `rod_zirnox`, `rod_zirnox_empty`, `rod_zirnox_les_fuel_depleted`, `rod_zirnox_mox_fuel_depleted`, `rod_zirnox_natural_uranium_fuel_depleted`, `rod_zirnox_plutonium_fuel_depleted`, `rod_zirnox_thorium_fuel_depleted`, `rod_zirnox_tritium`, `rod_zirnox_u233_fuel_depleted`, `rod_zirnox_u235_fuel_depleted`, `rod_zirnox_uranium_fuel_depleted`, `rod_zirnox_zfb_mox_depleted`, `watz_pellet`, `watz_pellet_depleted` |
| Waste/depleted solid states | `billet_nuclear_waste`, `nuclear_waste`, `nuclear_waste_long`, `nuclear_waste_long_depleted`, `nuclear_waste_long_depleted_tiny`, `nuclear_waste_long_tiny`, `nuclear_waste_short`, `nuclear_waste_short_depleted`, `nuclear_waste_short_depleted_tiny`, `nuclear_waste_short_tiny`, `nuclear_waste_tiny`, `nuclear_waste_vitrified`, `nuclear_waste_vitrified_tiny`, `waste_mox`, `waste_natural_uranium`, `waste_plate_mox`, `waste_plate_pu238be`, `waste_plate_pu239`, `waste_plate_ra226be`, `waste_plate_sa326`, `waste_plate_u233`, `waste_plate_u235`, `waste_plutonium`, `waste_schrabidium`, `waste_thorium`, `waste_u233`, `waste_u235`, `waste_uranium`, `waste_zfb_mox` |
| Related fuel containers/additives and unusual carriers (not all radioactive) | `ammo_fuel`, `debris_fuel`, `fuel_additive`, `fuel_tank_large`, `fuel_tank_medium`, `fuel_tank_small`, `redstone_depleted`, `rod_of_discord` |

Metadata/NBT representations also audited: `ItemEnums` coke 3, briquette 3, tar 6, ash 6, chunk RARE; stone-resource and basalt-ore enums; generated Mats shapes/parts; old bedrock ore/stage/byproduct enums; new 26×6 grade types and six NBT assay fields; `ItemMineralOre` six chemical clump states (SCHRABIDIC source route disabled); `ItemHotDusted` forging metadata 0–9 plus heat NBT; `ItemScraps.amount`; RTG depleted materials/lifespan NBT; RBMK enrichment/xenon/depletion and reactor fuel/waste types. Enum state means a separately handled state, not a separate universal mass unit.


## Fluid-recipe reagent forms — 2026-10-14

The fluid correction pass adds chemically meaningful forms through the existing item/fluid registries: caustic soda/potash, boric acid, alumina, Ta/Nb oxides, lithium sulfate/carbonate, barium-aluminate residue, rare-earth residue, sulfur doses, trace fuel-additive doses and polymer intermediates (BPA/TPC/PPD). Existing powder/tinted-fluid presentation is reused. Ordinary reagent parcels adopt 1 kg unless explicitly listed otherwise; this is not a universal ingot, nugget, ore-block or construction-item mass rule.

The cement binder parcel is 0.5 kg. Sulfur feed doses are 10 g, existing additive concentrate 10 g and its new metered doses 100 mg. Iodine/KI/KIO3 medical reagent packets are 10 g. Ga/As/GaAs, Ru, Sc/Y and uranium/plutonium nugget quantities use the declared native fraction of their kilogram parcel with exact integer mass remainders. Construction ice/snow are compressed parcels rather than cubic metres. Uranium fluoride fluid counts retain their legacy carrier-aliquot interpretation; they are neither liquid litres nor standard gas litres.

Bayer processing yields alumina and explicit bauxite residue before metal reduction. Coltan separation yields Ta/Nb oxides rather than pretending acid leaching directly produces metal. Lepidolite yields sulfate, then carbonate and dried molten chloride before lithium electrolysis. Os/Ir/Rh feeds have adopted concentrate assays; aqueous carrier and oxidation/reduction reagents are accounted, and an Ir-only feed cannot produce unbacked Ru. These assays govern corrected recipes, not unknown old nominal routes. Existing item counts/NBT are not rewritten on load; introducing an assay for a previously unresolved reagent does not rescale inventories.

The [process inventory](REALSIM_PROCESS_INVENTORY.md#fluid-correction-implementation-table--2026-10-14) lists implemented families and unresolved specialist/nominal exceptions. Crystallizer and metal-electrolyser mineral assays still need a complete coupled review before their outputs can receive a whole-chain SI conservation claim.


Fluid correction follow-through (2026-10-15): nitrate feed/product packets use 1-kg dry-salt assays in released fluid crystallization bills. Generic fertilizer is a 250-g dry nutrient-salt dose for potassium recovery/packaging, allowing chloride and sulfate nutrient salts as an intentional gameplay category. This does not certify every historical dry fertilizer craft or give that generic item a unique elemental formula. Potassium chloride carrier and excess salt remain separate mass accounts.

Fluid follow-through forms (2026-10-15): coarse/fine aggregate and selected regolith feed are 100-kg bags, nineteen bags per adopted source block. Concrete parcels represent compressed construction outputs, not cubic metres. U238 billets retain six native ninth-kilogram nuggets (2/3 kg); DUcrete uses this native form ratio. Specialist optical crystals are 100-g packets, other new oxide/fluoride/organic reagent packets have their individual registry assays. Selected radium residue is 10 ppm Ra226; ordinary pure uranium is not a radium source. Graphite arc electrode mass includes a retained spent holder; fullerene recovery retains its suspension carrier. Spent lithium targets carry their size metadata and cannot substitute for unirradiated lithium. Named spent-fuel and salt assays retain the existing game isotope progression without claiming reactor-physics calibration. See the process table for physical limits of concentrated Crystallizer and mineral-electrolyser solid outputs.
