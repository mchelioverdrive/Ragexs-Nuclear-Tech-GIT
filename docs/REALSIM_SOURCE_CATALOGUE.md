# RealSim current-source parameter catalogue

2026-10-12; active source a933ecc05. Documentation snapshot, not executable data, a generated test, or a new runtime registry. Read the [process judgments](REALSIM_PROCESS_INVENTORY.md), [canonical state decisions](REALSIM_SUBSTANCE_REGISTRY.md), and [main scope/limitations](REALSIM_PHASES_1_4.md).

The census below records **171 relevant tile/base/boundary source files**. Shared bases, controller/storage variants and external converter boundaries are deliberately included; their count is not a count of distinct operating processes. Selected exact expressions supplement the inventory rather than replace call-path inspection. All links refer to the current source, not historical copied implementations.

Units: electrical capacity/maxPower is **quanta stored**, not W. Electrical per-processing-tick consumption is q/t where confirmed by its debit path. Heat/core/progress constants keep their original meaning until the physical boundary is established. Tank constructor amounts are nominal inventory L. Parameter expressions are not evaluated as if they were SI ratings. Recipe-dependent buffers and per-lane rates remain expressions. Client formatting/NBT getters are excluded from operation excerpts. Comments are removed; disabled conditional code can still appear in a declaration census and is explicitly distinguished by the inventory.

## Machine and boundary parameters

### AirPump

Source: [TileEntityAirPump.java](../src/main/java/com/hbm/tileentity/machine/TileEntityAirPump.java). Persistent tile registry: `tileentity_air_vent`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityAirPump.java#L59) | `tank = new FluidTank(Fluids.OXYGEN, 16000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L179](../src/main/java/com/hbm/tileentity/machine/TileEntityAirPump.java#L179): `try { tank.setFill(fill); } finally { this.endMachineFluidMutation(); }`

### AirScrubber

Source: [TileEntityAirScrubber.java](../src/main/java/com/hbm/tileentity/machine/TileEntityAirScrubber.java). Persistent tile registry: `tileentity_air_scrubber`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityAirScrubber.java#L36) | `tank = new FluidTank(Fluids.CARBONDIOXIDE, 16_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L129](../src/main/java/com/hbm/tileentity/machine/TileEntityAirScrubber.java#L129): `tank.setFill(tank.getFill() + add);`
- [L130](../src/main/java/com/hbm/tileentity/machine/TileEntityAirScrubber.java#L130): `this.setStoredEnergyQuanta(this.energyQuanta - add * 10);`

### AtmoExtractor

Source: [TileEntityAtmoExtractor.java](../src/main/java/com/hbm/tileentity/machine/TileEntityAtmoExtractor.java). Persistent tile registry: `tileentity_atmospheric_vent`.

| Field/boundary | Exact current source |
| --- | --- |
| [operatingPowerWatts](../src/main/java/com/hbm/tileentity/machine/TileEntityAtmoExtractor.java#L36) | `private long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(10_000L);` |
| [energyQuanta](../src/main/java/com/hbm/tileentity/machine/TileEntityAtmoExtractor.java#L44) | `public long energyQuanta = 0;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityAtmoExtractor.java#L50) | `tank = new FluidTank(Fluids.AIR, 50000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L104](../src/main/java/com/hbm/tileentity/machine/TileEntityAtmoExtractor.java#L104): `tank.setFill(tank.getFill() + 100);`
- [L105](../src/main/java/com/hbm/tileentity/machine/TileEntityAtmoExtractor.java#L105): `this.setStoredEnergyQuanta(energyQuanta - EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts));`

### AtmoTower

Source: [TileEntityAtmoTower.java](../src/main/java/com/hbm/tileentity/machine/TileEntityAtmoTower.java). Persistent tile registry: `tileentity_atmospheric_tower`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityAtmoTower.java#L20) | `tanks[0] = new FluidTank(Fluids.AIR, 50000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityAtmoTower.java#L21) | `tanks[1] = new FluidTank(Fluids.NITROGEN, 5000);` |

### Chungus

Source: [TileEntityChungus.java](../src/main/java/com/hbm/tileentity/machine/TileEntityChungus.java). Persistent tile registry: `tileentity_chungus`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_STEAM](../src/main/java/com/hbm/tileentity/machine/TileEntityChungus.java#L48) | `private static final int TASK_STEAM = 1;` |
| [TRIP_OVERPRESSURE](../src/main/java/com/hbm/tileentity/machine/TileEntityChungus.java#L53) | `public static final int TRIP_OVERPRESSURE = 1;` |
| [TRIP_OVERSPEED](../src/main/java/com/hbm/tileentity/machine/TileEntityChungus.java#L54) | `public static final int TRIP_OVERSPEED = 2;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityChungus.java#L72) | `public static long maxPower = 100000000000L;` |
| [outputTankSize](../src/main/java/com/hbm/tileentity/machine/TileEntityChungus.java#L74) | `public static int outputTankSize = 1_000_000_000;` |
| [efficiency](../src/main/java/com/hbm/tileentity/machine/TileEntityChungus.java#L75) | `public static double efficiency = 0.85D;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityChungus.java#L79) | `tanks[0] = new FluidTank(Fluids.STEAM, inputTankSize);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityChungus.java#L80) | `tanks[1] = new FluidTank(Fluids.SPENTSTEAM, outputTankSize);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L197](../src/main/java/com/hbm/tileentity/machine/TileEntityChungus.java#L197): `tanks[0].setFill(tanks[0].getFill() - ops * trait.amountReq);`
- [L198](../src/main/java/com/hbm/tileentity/machine/TileEntityChungus.java#L198): `tanks[1].setFill(tanks[1].getFill() + ops * trait.amountProduced);`
- [L199](../src/main/java/com/hbm/tileentity/machine/TileEntityChungus.java#L199): `this.setStoredEnergyQuanta(this.energyQuanta + (long) (ops * trait.heatEnergy * eff));`

### Condenser

Source: [TileEntityCondenser.java](../src/main/java/com/hbm/tileentity/machine/TileEntityCondenser.java). Persistent tile registry: `tileentity_condenser`.

| Field/boundary | Exact current source |
| --- | --- |
| [age](../src/main/java/com/hbm/tileentity/machine/TileEntityCondenser.java#L38) | `public int age = 0;` |
| [waterTimer](../src/main/java/com/hbm/tileentity/machine/TileEntityCondenser.java#L41) | `public int waterTimer = 0;` |
| [outputTankSize](../src/main/java/com/hbm/tileentity/machine/TileEntityCondenser.java#L48) | `public static int outputTankSize = 100;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityCondenser.java#L52) | `tanks[0] = new FluidTank(Fluids.SPENTSTEAM, inputTankSize);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityCondenser.java#L53) | `tanks[1] = new FluidTank(Fluids.FRESH_WATER, outputTankSize).migrateFrom(Fluids.WATER);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L111](../src/main/java/com/hbm/tileentity/machine/TileEntityCondenser.java#L111): `tanks[0].setFill(tanks[0].getFill() - convert);`
- [L119](../src/main/java/com/hbm/tileentity/machine/TileEntityCondenser.java#L119): `if(shouldEvaporate) tanks[1].setFill(tanks[1].getFill() - convert);`
- [L120](../src/main/java/com/hbm/tileentity/machine/TileEntityCondenser.java#L120): `else tanks[1].setFill(tanks[1].getFill() + convert);`

### CondenserPowered

Source: [TileEntityCondenserPowered.java](../src/main/java/com/hbm/tileentity/machine/TileEntityCondenserPowered.java). Persistent tile registry: `tileentity_condenser_powered`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityCondenserPowered.java#L29) | `public static long maxPower = 10_000_000;` |
| [outputTankSizeP](../src/main/java/com/hbm/tileentity/machine/TileEntityCondenserPowered.java#L31) | `public static int outputTankSizeP = 1_000_000;` |
| [energyCostQuantaPerMb](../src/main/java/com/hbm/tileentity/machine/TileEntityCondenserPowered.java#L32) | `public static int energyCostQuantaPerMb = 10;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityCondenserPowered.java#L36) | `tanks[0] = new FluidTank(Fluids.SPENTSTEAM, inputTankSizeP);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityCondenserPowered.java#L37) | `tanks[1] = new FluidTank(Fluids.FRESH_WATER, outputTankSizeP).migrateFrom(Fluids.WATER);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L98](../src/main/java/com/hbm/tileentity/machine/TileEntityCondenserPowered.java#L98): `this.setStoredEnergyQuanta(this.energyQuanta - getOperationEnergyRequirementQuanta(convert));`

### ConveyorPress

Source: [TileEntityConveyorPress.java](../src/main/java/com/hbm/tileentity/machine/TileEntityConveyorPress.java). Persistent tile registry: `tileentity_conveyor_press`.

| Field/boundary | Exact current source |
| --- | --- |
| [usage](../src/main/java/com/hbm/tileentity/machine/TileEntityConveyorPress.java#L28) | `public int usage = 100;` |
| [energyQuanta](../src/main/java/com/hbm/tileentity/machine/TileEntityConveyorPress.java#L29) | `public long energyQuanta = 0;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityConveyorPress.java#L30) | `public final static long maxPower = 50000;` |
| [speed](../src/main/java/com/hbm/tileentity/machine/TileEntityConveyorPress.java#L32) | `public double speed = 0.125;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L128](../src/main/java/com/hbm/tileentity/machine/TileEntityConveyorPress.java#L128): `this.setStoredEnergyQuanta(this.energyQuanta - this.usage);`

### Core

Source: [TileEntityCore.java](../src/main/java/com/hbm/tileentity/machine/TileEntityCore.java). Player container name: **Dark Fusion Core**. Persistent tile registry: `tileentity_v0`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityCore.java#L51) | `tanks[0] = new FluidTank(Fluids.DEUTERIUM, 128000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityCore.java#L52) | `tanks[1] = new FluidTank(Fluids.TRITIUM, 128000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L238](../src/main/java/com/hbm/tileentity/machine/TileEntityCore.java#L238): `heat += (int)Math.ceil((double)joules / 10000D);`
- [L240](../src/main/java/com/hbm/tileentity/machine/TileEntityCore.java#L240): `tanks[0].setFill(tanks[0].getFill() - demand);`
- [L241](../src/main/java/com/hbm/tileentity/machine/TileEntityCore.java#L241): `tanks[1].setFill(tanks[1].getFill() - demand);`

### CoreEmitter

Source: [TileEntityCoreEmitter.java](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreEmitter.java). Player container name: **DFC Emitter**. Persistent tile registry: `tileentity_v0_emitter`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreEmitter.java#L44) | `public static final long maxPower = 1000000000L;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreEmitter.java#L56) | `tank = new FluidTank(Fluids.CRYOGEL, 64000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L79](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreEmitter.java#L79): `tank.setFill(tank.getFill() - 20);`
- [L92](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreEmitter.java#L92): `this.setStoredEnergyQuanta(this.energyQuanta - demand);`
- [L184](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreEmitter.java#L184): `return (energyQuanta * i) / maxPower;`

### CoreInjector

Source: [TileEntityCoreInjector.java](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreInjector.java). Player container name: **DFC Fuel Injector**. Persistent tile registry: `tileentity_v0_injector`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreInjector.java#L37) | `tanks[0] = new FluidTank(Fluids.DEUTERIUM, 128000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreInjector.java#L38) | `tanks[1] = new FluidTank(Fluids.TRITIUM, 128000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L78](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreInjector.java#L78): `tanks[t].setFill(tanks[t].getFill() - f);`
- [L79](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreInjector.java#L79): `core.tanks[t].setFill(core.tanks[t].getFill() + f);`
- [L87](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreInjector.java#L87): `tanks[t].setFill(tanks[t].getFill() - f);`
- [L88](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreInjector.java#L88): `core.tanks[t].setFill(core.tanks[t].getFill() + f);`

### CoreReceiver

Source: [TileEntityCoreReceiver.java](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreReceiver.java). Player container name: **DFC Receiver**. Persistent tile registry: `tileentity_v0_receiver`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreReceiver.java#L42) | `tank = new FluidTank(Fluids.CRYOGEL, 64000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L65](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreReceiver.java#L65): `tank.setFill(tank.getFill() - 20);`

### CoreStabilizer

Source: [TileEntityCoreStabilizer.java](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreStabilizer.java). Player container name: **DFC Stabilizer**. Persistent tile registry: `tileentity_v0_stabilizer`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreStabilizer.java#L35) | `public static final long maxPower = 2500000000L;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L77](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreStabilizer.java#L77): `this.setStoredEnergyQuanta(this.energyQuanta - demand);`
- [L119](../src/main/java/com/hbm/tileentity/machine/TileEntityCoreStabilizer.java#L119): `return (energyQuanta * i) / maxPower;`

### Crucible

Source: [TileEntityCrucible.java](../src/main/java/com/hbm/tileentity/machine/TileEntityCrucible.java). Player container name: **Foundry**. Persistent tile registry: `tileentity_crucible`.

| Field/boundary | Exact current source |
| --- | --- |
| [recipeZCapacity](../src/main/java/com/hbm/tileentity/machine/TileEntityCrucible.java#L104) | `public static int recipeZCapacity = MaterialShapes.BLOCK.q(16);` |
| [wasteZCapacity](../src/main/java/com/hbm/tileentity/machine/TileEntityCrucible.java#L105) | `public static int wasteZCapacity = MaterialShapes.BLOCK.q(16);` |
| [processTime](../src/main/java/com/hbm/tileentity/machine/TileEntityCrucible.java#L106) | `public static int processTime = 20_000;` |
| [diffusion](../src/main/java/com/hbm/tileentity/machine/TileEntityCrucible.java#L107) | `public static double diffusion = 0.25D;` |
| [maxHeat](../src/main/java/com/hbm/tileentity/machine/TileEntityCrucible.java#L108) | `public static int maxHeat = 100_000;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L456](../src/main/java/com/hbm/tileentity/machine/TileEntityCrucible.java#L456): `this.heat += diff;`
- [L476](../src/main/java/com/hbm/tileentity/machine/TileEntityCrucible.java#L476): `this.progress += delta;`
- [L477](../src/main/java/com/hbm/tileentity/machine/TileEntityCrucible.java#L477): `this.heat -= delta;`

### CustomMachine

Source: [TileEntityCustomMachine.java](../src/main/java/com/hbm/tileentity/machine/TileEntityCustomMachine.java). Persistent tile registry: `tileentity_custom_machine`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxProgress](../src/main/java/com/hbm/tileentity/machine/TileEntityCustomMachine.java#L69) | `public int maxProgress = 1;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityCustomMachine.java#L98) | `for (int i = 0; i < inputTanks.length; i++) inputTanks[i] = new FluidTank(Fluids.NONE, config.fluidInCap);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityCustomMachine.java#L102) | `outputTanks[i] = new FluidTank(Fluids.NONE, config.fluidOutCap);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L341](../src/main/java/com/hbm/tileentity/machine/TileEntityCustomMachine.java#L341): `this.setStoredEnergyQuanta(this.energyQuanta + powerReq);`
- [L342](../src/main/java/com/hbm/tileentity/machine/TileEntityCustomMachine.java#L342): `this.heat -= cachedRecipe.heat;`
- [L366](../src/main/java/com/hbm/tileentity/machine/TileEntityCustomMachine.java#L366): `this.setStoredEnergyQuanta(this.energyQuanta - powerReq);`
- [L367](../src/main/java/com/hbm/tileentity/machine/TileEntityCustomMachine.java#L367): `this.heat -= recipe.heat;`
- [L441](../src/main/java/com/hbm/tileentity/machine/TileEntityCustomMachine.java#L441): `this.heat += diff;`
- [L479](../src/main/java/com/hbm/tileentity/machine/TileEntityCustomMachine.java#L479): `this.inputTanks[i].setFill(this.inputTanks[i].getFill() - recipe.inputFluids[i].fill);`
- [L491](../src/main/java/com/hbm/tileentity/machine/TileEntityCustomMachine.java#L491): `this.outputTanks[i].setFill(this.outputTanks[i].getFill() + recipe.outputFluids[i].fill);`

### DeuteriumExtractor

Source: [TileEntityDeuteriumExtractor.java](../src/main/java/com/hbm/tileentity/machine/TileEntityDeuteriumExtractor.java). Persistent tile registry: `tileentity_deuterium_extractor`.

| Field/boundary | Exact current source |
| --- | --- |
| [energyQuanta](../src/main/java/com/hbm/tileentity/machine/TileEntityDeuteriumExtractor.java#L18) | `public long energyQuanta = 0;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityDeuteriumExtractor.java#L27) | `tanks[0] = new FluidTank(Fluids.LIGHT_WATER, 1000).migrateFrom(Fluids.WATER);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityDeuteriumExtractor.java#L28) | `tanks[1] = new FluidTank(Fluids.HEAVYWATER, 100);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L63](../src/main/java/com/hbm/tileentity/machine/TileEntityDeuteriumExtractor.java#L63): `tanks[0].setFill(tanks[0].getFill() - convert * 50);`
- [L64](../src/main/java/com/hbm/tileentity/machine/TileEntityDeuteriumExtractor.java#L64): `tanks[1].setFill(tanks[1].getFill() + convert);`
- [L65](../src/main/java/com/hbm/tileentity/machine/TileEntityDeuteriumExtractor.java#L65): `this.setStoredEnergyQuanta(energyQuanta - getEnergyCapacityQuanta() / 100);`

### DeuteriumTower

Source: [TileEntityDeuteriumTower.java](../src/main/java/com/hbm/tileentity/machine/TileEntityDeuteriumTower.java). Persistent tile registry: `tileentity_deuterium_tower`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityDeuteriumTower.java#L20) | `tanks[0] = new FluidTank(Fluids.LIGHT_WATER, 50000).migrateFrom(Fluids.WATER);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityDeuteriumTower.java#L21) | `tanks[1] = new FluidTank(Fluids.HEAVYWATER, 5000);` |

### DiFurnace

Source: [TileEntityDiFurnace.java](../src/main/java/com/hbm/tileentity/machine/TileEntityDiFurnace.java). Player container name: **Blast Furnace**. Persistent tile registry: `tilentity_diFurnace`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxFuel](../src/main/java/com/hbm/tileentity/machine/TileEntityDiFurnace.java#L43) | `public static final int maxFuel = 12800;` |
| [processingSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntityDiFurnace.java#L44) | `public static final int processingSpeed = 400;` |
| [sideFuel](../src/main/java/com/hbm/tileentity/machine/TileEntityDiFurnace.java#L47) | `public byte sideFuel = 1;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L277](../src/main/java/com/hbm/tileentity/machine/TileEntityDiFurnace.java#L277): `progress += extension ? 3 : 1;`

### DiFurnaceRTG

Source: [TileEntityDiFurnaceRTG.java](../src/main/java/com/hbm/tileentity/machine/TileEntityDiFurnaceRTG.java). Player container name: **Nuclear Blast Furnace**. Persistent tile registry: `tileentity_rtg_difurnace`.

| Field/boundary | Exact current source |
| --- | --- |
| [processSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntityDiFurnaceRTG.java#L30) | `private short processSpeed = 0;` |
| [timeRequired](../src/main/java/com/hbm/tileentity/machine/TileEntityDiFurnaceRTG.java#L32) | `private static final short timeRequired = 1200;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L114](../src/main/java/com/hbm/tileentity/machine/TileEntityDiFurnaceRTG.java#L114): `progress += processSpeed;`

### Electrolyser

Source: [TileEntityElectrolyser.java](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java). Player container name: **Industrial Electrolysis Machine**. Persistent tile registry: `tileentity_electrolyser`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L60) | `public static final long maxPower = 20000000;` |
| [usageOreBase](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L61) | `public static final int usageOreBase = 10_000;` |
| [usageFluidBase](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L62) | `public static final int usageFluidBase = 10_000;` |
| [processFluidTime](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L67) | `public int processFluidTime = 100;` |
| [processOreTime](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L69) | `public int processOreTime = 600;` |
| [maxMaterial](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L73) | `public int maxMaterial = MaterialShapes.BLOCK.q(16);` |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L88) | `private static final int TASK_ACCOUNTING = 1;` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L94) | `private long lastAccountingTick = Long.MIN_VALUE;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L112) | `tanks[0] = new FluidTank(Fluids.FRESH_WATER, 16000).migrateFrom(Fluids.WATER);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L113) | `tanks[1] = new FluidTank(Fluids.HYDROGEN, 16000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L114) | `tanks[2] = new FluidTank(Fluids.OXYGEN, 16000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L115) | `tanks[3] = new FluidTank(Fluids.NITRIC_ACID, 16000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L385](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L385): `if(costPerOperation > 0L && operationsPerTick > 0L) ticks = Math.min(ticks, ceilDiv(energyQuanta / costPerOperation, operationsPerTick));`
- [L419](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L419): `long energyTick = costPerOperation <= 0L || operationsPerTick <= 0L ? Long.MAX_VALUE : ceilDiv(energyQuanta / costPerOperation, operationsPerTick);`
- [L426](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L426): `this.setStoredEnergyQuanta(energyQuanta - cost);`
- [L433](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L433): `this.setStoredEnergyQuanta(energyQuanta - costFluid);`
- [L438](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L438): `this.setStoredEnergyQuanta(energyQuanta - costMetal);`
- [L517](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L517): `tanks[0].setFill(tanks[0].getFill() - recipe.amount);`
- [L520](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L520): `tanks[1].setFill(tanks[1].getFill() + recipe.output1.fill);`
- [L521](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L521): `tanks[2].setFill(tanks[2].getFill() + recipe.output2.fill);`
- [L604](../src/main/java/com/hbm/tileentity/machine/TileEntityElectrolyser.java#L604): `this.tanks[3].setFill(this.tanks[3].getFill() - 100);`

### FEL

Source: [TileEntityFEL.java](../src/main/java/com/hbm/tileentity/machine/TileEntityFEL.java). Player container name: **FEL**. Persistent tile registry: `tileentity_fel`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityFEL.java#L51) | `public static final long maxPower = 20000000;` |
| [powerReq](../src/main/java/com/hbm/tileentity/machine/TileEntityFEL.java#L52) | `public static final int powerReq = 1250;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L156](../src/main/java/com/hbm/tileentity/machine/TileEntityFEL.java#L156): `this.setStoredEnergyQuanta(this.energyQuanta - req);`
- [L314](../src/main/java/com/hbm/tileentity/machine/TileEntityFEL.java#L314): `return (energyQuanta * i) / maxPower;`

### FireboxBase

Source: [TileEntityFireboxBase.java](../src/main/java/com/hbm/tileentity/machine/TileEntityFireboxBase.java). Persistent registration inherited/constructed; class identity is the inspection anchor.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### FoundryBase

Source: [TileEntityFoundryBase.java](../src/main/java/com/hbm/tileentity/machine/TileEntityFoundryBase.java). Persistent registration inherited/constructed; class identity is the inspection anchor.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### FoundryBasin

Source: [TileEntityFoundryBasin.java](../src/main/java/com/hbm/tileentity/machine/TileEntityFoundryBasin.java). Persistent tile registry: `tileentity_foundry_basin`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### FoundryCastingBase

Source: [TileEntityFoundryCastingBase.java](../src/main/java/com/hbm/tileentity/machine/TileEntityFoundryCastingBase.java). Persistent registration inherited/constructed; class identity is the inspection anchor.

| Field/boundary | Exact current source |
| --- | --- |
| [lastCastingAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityFoundryCastingBase.java#L28) | `private long lastCastingAccountingTick = -1L;` |

### FoundryChannel

Source: [TileEntityFoundryChannel.java](../src/main/java/com/hbm/tileentity/machine/TileEntityFoundryChannel.java). Persistent tile registry: `tileentity_foundry_channel`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### FoundryMold

Source: [TileEntityFoundryMold.java](../src/main/java/com/hbm/tileentity/machine/TileEntityFoundryMold.java). Persistent tile registry: `tileentity_foundry_mold`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### FoundryOutlet

Source: [TileEntityFoundryOutlet.java](../src/main/java/com/hbm/tileentity/machine/TileEntityFoundryOutlet.java). Persistent tile registry: `tileentity_foundry_outlet`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### FoundrySlagtap

Source: [TileEntityFoundrySlagtap.java](../src/main/java/com/hbm/tileentity/machine/TileEntityFoundrySlagtap.java). Persistent tile registry: `tileentity_foundry_slagtap`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### FoundryTank

Source: [TileEntityFoundryTank.java](../src/main/java/com/hbm/tileentity/machine/TileEntityFoundryTank.java). Persistent tile registry: `tileentity_foundry_tank`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### FurnaceBrick

Source: [TileEntityFurnaceBrick.java](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceBrick.java). Player container name: **Bricked Furnace**. Persistent tile registry: `tileentity_furnace_brick`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L103](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceBrick.java#L103): `progress += this.getBurnSpeed();`

### FurnaceCombination

Source: [TileEntityFurnaceCombination.java](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceCombination.java). Player container name: **Combination Oven**. Persistent tile registry: `tileentity_combination_oven`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_HEAT](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceCombination.java#L37) | `private static final int TASK_HEAT = 1;` |
| [processTime](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceCombination.java#L43) | `public static int processTime = 20_000;` |
| [maxHeat](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceCombination.java#L46) | `public static int maxHeat = 100_000;` |
| [diffusion](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceCombination.java#L47) | `public static double diffusion = 0.25D;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceCombination.java#L53) | `this.tank = new FluidTank(Fluids.NONE, 24_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L150](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceCombination.java#L150): `this.progress += burn;`
- [L151](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceCombination.java#L151): `this.heat -= burn;`
- [L155](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceCombination.java#L155): `progress -= this.processTime;`
- [L174](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceCombination.java#L174): `tank.setFill(tank.getFill() + fluid.fill);`
- [L255](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceCombination.java#L255): `this.heat += diff;`

### FurnaceIron

Source: [TileEntityFurnaceIron.java](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceIron.java). Player container name: **Iron Furnace**. Persistent tile registry: `tileentity_furnace_iron`.

| Field/boundary | Exact current source |
| --- | --- |
| [baseTime](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceIron.java#L43) | `public static final int baseTime = 160;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L57](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceIron.java#L57): `burnModule = new ModuleBurnTime()`
- [L58](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceIron.java#L58): `.setLigniteTimeMod(1.25)`
- [L59](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceIron.java#L59): `.setCoalTimeMod(1.25)`
- [L60](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceIron.java#L60): `.setCokeTimeMod(1.5)`
- [L61](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceIron.java#L61): `.setSolidTimeMod(2)`
- [L62](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceIron.java#L62): `.setRocketTimeMod(2)`
- [L63](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceIron.java#L63): `.setBalefireTimeMod(2);`

### FurnaceSteel

Source: [TileEntityFurnaceSteel.java](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceSteel.java). Player container name: **Steel Furnace**. Persistent tile registry: `tileentity_furnace_steel`.

| Field/boundary | Exact current source |
| --- | --- |
| [processTime](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceSteel.java#L34) | `public static final int processTime = 40_000;` |
| [maxHeat](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceSteel.java#L37) | `public static final int maxHeat = 100_000;` |
| [diffusion](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceSteel.java#L38) | `public static final double diffusion = 0.05D;` |
| [TASK_HEAT_AND_PROCESS](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceSteel.java#L49) | `private static final int TASK_HEAT_AND_PROCESS = 1;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L108](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceSteel.java#L108): `this.heat -= burn;`
- [L299](../src/main/java/com/hbm/tileentity/machine/TileEntityFurnaceSteel.java#L299): `this.heat += diff;`

### Hadron

Source: [TileEntityHadron.java](../src/main/java/com/hbm/tileentity/machine/TileEntityHadron.java). Player container name: **Particle Accelerator**. Persistent tile registry: `tileentity_hadron`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityHadron.java#L43) | `public static final long maxPower = 10000000;` |
| [delaySuccess](../src/main/java/com/hbm/tileentity/machine/TileEntityHadron.java#L54) | `private static final int delaySuccess = 20;` |
| [delayNoResult](../src/main/java/com/hbm/tileentity/machine/TileEntityHadron.java#L55) | `private static final int delayNoResult = 60;` |
| [delayError](../src/main/java/com/hbm/tileentity/machine/TileEntityHadron.java#L56) | `private static final int delayError = 100;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L121](../src/main/java/com/hbm/tileentity/machine/TileEntityHadron.java#L121): `this.setStoredEnergyQuanta((long) (this.energyQuanta - maxPower * 0.75));`
- [L266](../src/main/java/com/hbm/tileentity/machine/TileEntityHadron.java#L266): `this.setStoredEnergyQuanta(energyQuanta + toDraw);`
- [L314](../src/main/java/com/hbm/tileentity/machine/TileEntityHadron.java#L314): `return (int)(energyQuanta * i / maxPower);`

### HadronPower

Source: [TileEntityHadronPower.java](../src/main/java/com/hbm/tileentity/machine/TileEntityHadronPower.java). Persistent tile registry: `tileentity_hadron_power`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### HeatBoiler

Source: [TileEntityHeatBoiler.java](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoiler.java). Persistent tile registry: `tileentity_heat_boiler`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxHeat](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoiler.java#L26) | `public static int maxHeat = 3_200_000;` |
| [diffusion](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoiler.java#L27) | `public static double diffusion = 0.1D;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoiler.java#L35) | `tanks[0] = new FluidTank(Fluids.FRESH_WATER, 16_000).migrateFrom(Fluids.WATER);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoiler.java#L36) | `tanks[1] = new FluidTank(Fluids.STEAM, 16_000 * 100);` |

### HeatBoilerBase

Source: [TileEntityHeatBoilerBase.java](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerBase.java). Persistent registration inherited/constructed; class identity is the inspection anchor.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L179](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerBase.java#L179): `if(this.isAmbientHeatAvailable()) this.heat += ((this.getMaxBoilerHeat() - heat) * 0.000005D);`
- [L191](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerBase.java#L191): `this.heat += diff;`
- [L203](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerBase.java#L203): `HeatingStep entry = trait.getFirstStep();`
- [L217](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerBase.java#L217): `HeatingStep entry = trait.getFirstStep();`
- [L222](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerBase.java#L222): `tanks[0].setFill(tanks[0].getFill() - entry.amountReq * ops);`
- [L223](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerBase.java#L223): `tanks[1].setFill(tanks[1].getFill() + entry.amountProduced * ops);`
- [L224](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerBase.java#L224): `this.heat -= entry.heatReq * ops;`

### HeatBoilerIndustrial

Source: [TileEntityHeatBoilerIndustrial.java](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerIndustrial.java). Persistent tile registry: `tileentity_heat_boiler_industrial`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxHeat](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerIndustrial.java#L22) | `public static int maxHeat = 12_800_000;` |
| [diffusion](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerIndustrial.java#L23) | `public static double diffusion = 0.1D;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerIndustrial.java#L31) | `tanks[0] = new FluidTank(Fluids.FRESH_WATER, 64_000).migrateFrom(Fluids.WATER);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityHeatBoilerIndustrial.java#L32) | `tanks[1] = new FluidTank(Fluids.STEAM, 64_000 * 100);` |

### HeaterElectric

Source: [TileEntityHeaterElectric.java](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterElectric.java). Persistent tile registry: `tileentity_electric_heater`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_HEAT](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterElectric.java#L26) | `private static final int TASK_HEAT = 1;` |
| [maxHeatEnergy](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterElectric.java#L35) | `public static final int maxHeatEnergy = 500_000;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L89](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterElectric.java#L89): `if(setting > 0 && this.energyQuanta >= this.getConsumption() && this.heatEnergy < maxHeatEnergy) {`
- [L91](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterElectric.java#L91): `try { this.setStoredEnergyQuanta(this.energyQuanta - this.getConsumption()); }`
- [L93](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterElectric.java#L93): `this.heatEnergy = Math.min(this.heatEnergy + getHeatGen(), maxHeatEnergy);`
- [L183](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterElectric.java#L183): `this.heatEnergy += toPull * 0.85D;`
- [L201](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterElectric.java#L201): `public long getConsumption() {`
- [L206](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterElectric.java#L206): `return EnergyUnits.quantaPerTickToWatts(this.getConsumption());`
- [L211](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterElectric.java#L211): `return getConsumption() * 20;`
- [L214](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterElectric.java#L214): `public int getHeatGen() {`

### HeaterFirebox

Source: [TileEntityHeaterFirebox.java](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterFirebox.java). Player container name: **Firebox**. Persistent tile registry: `tileentity_firebox`.

| Field/boundary | Exact current source |
| --- | --- |
| [baseHeat](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterFirebox.java#L22) | `public static int baseHeat = 100;` |
| [timeMult](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterFirebox.java#L23) | `public static double timeMult = 1D;` |
| [maxHeatEnergy](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterFirebox.java#L24) | `public static int maxHeatEnergy = 100_000;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L25](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterFirebox.java#L25): `public static ModuleBurnTime burnModule = new ModuleBurnTime()`
- [L26](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterFirebox.java#L26): `.setLigniteTimeMod(1.25)`
- [L27](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterFirebox.java#L27): `.setCoalTimeMod(1.25)`
- [L28](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterFirebox.java#L28): `.setCokeTimeMod(1.25)`
- [L29](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterFirebox.java#L29): `.setSolidTimeMod(1.5)`
- [L30](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterFirebox.java#L30): `.setRocketTimeMod(1.5)`
- [L31](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterFirebox.java#L31): `.setBalefireTimeMod(0.5)`

### HeaterHeatex

Source: [TileEntityHeaterHeatex.java](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterHeatex.java). Player container name: **Heat Exchanger**. Persistent tile registry: `tileentity_heater_heatex`.

| Field/boundary | Exact current source |
| --- | --- |
| [tickDelay](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterHeatex.java#L41) | `public int tickDelay = 1;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterHeatex.java#L47) | `this.tanks[0] = new FluidTank(Fluids.COOLANT_HOT, 24_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterHeatex.java#L48) | `this.tanks[1] = new FluidTank(Fluids.COOLANT, 24_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L177](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterHeatex.java#L177): `tanks[0].setFill(tanks[0].getFill() - trait.amountReq * ops);`
- [L178](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterHeatex.java#L178): `tanks[1].setFill(tanks[1].getFill() + trait.amountProduced * ops);`
- [L179](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterHeatex.java#L179): `this.heatEnergy += trait.heatEnergy * ops * trait.getEfficiency(CoolingType.HEATEXCHANGER);`

### HeaterOilburner

Source: [TileEntityHeaterOilburner.java](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOilburner.java). Player container name: **Fluid Burner**. Persistent tile registry: `tileentity_oilburner`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_BURN](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOilburner.java#L31) | `private static final int TASK_BURN = 1;` |
| [maxHeatEnergy](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOilburner.java#L44) | `public static final int maxHeatEnergy = 100_000;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOilburner.java#L48) | `tank = new FluidTank(Fluids.HEATINGOIL, 16000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L120](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOilburner.java#L120): `tank.setFill(tank.getFill() - toBurn);`
- [L122](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOilburner.java#L122): `heatEnergy += heat * toBurn;`

### HeaterOven

Source: [TileEntityHeaterOven.java](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java). Player container name: **Heating Oven**. Persistent tile registry: `tileentity_heating_oven`.

| Field/boundary | Exact current source |
| --- | --- |
| [baseHeat](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java#L28) | `public static int baseHeat = 500;` |
| [timeMult](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java#L29) | `public static double timeMult = 0.125D;` |
| [maxHeatEnergy](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java#L30) | `public static int maxHeatEnergy = 500_000;` |
| [heatEff](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java#L31) | `public static double heatEff = 0.5D;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L32](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java#L32): `public static ModuleBurnTime burnModule = new ModuleBurnTime()`
- [L33](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java#L33): `.setLigniteTimeMod(1.25)`
- [L34](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java#L34): `.setCoalTimeMod(1.25)`
- [L35](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java#L35): `.setCokeTimeMod(1.25)`
- [L36](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java#L36): `.setSolidTimeMod(1.5)`
- [L37](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java#L37): `.setRocketTimeMod(1.5)`
- [L38](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java#L38): `.setBalefireTimeMod(0.5)`
- [L73](../src/main/java/com/hbm/tileentity/machine/TileEntityHeaterOven.java#L73): `this.heatEnergy += toPull * heatEff;`

### ICF

Source: [TileEntityICF.java](../src/main/java/com/hbm/tileentity/machine/TileEntityICF.java). Player container name: **ICF**. Persistent tile registry: `tileentity_icf`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxHeat](../src/main/java/com/hbm/tileentity/machine/TileEntityICF.java#L54) | `public static final long maxHeat = 1_000_000_000_000L;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityICF.java#L64) | `this.tanks[0] = new FluidTank(Fluids.SODIUM, 512_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityICF.java#L65) | `this.tanks[1] = new FluidTank(Fluids.SODIUM_HOT, 512_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityICF.java#L66) | `this.tanks[2] = new FluidTank(Fluids.STELLAR_FLUX, 24_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L192](../src/main/java/com/hbm/tileentity/machine/TileEntityICF.java#L192): `this.heat += heatup;`
- [L198](../src/main/java/com/hbm/tileentity/machine/TileEntityICF.java#L198): `tanks[2].setFill(tanks[2].getFill() + (int) Math.ceil(this.heat * 2.5D / this.maxHeat));`
- [L199](../src/main/java/com/hbm/tileentity/machine/TileEntityICF.java#L199): `if(tanks[2].getFill() > tanks[2].getMaxFill()) tanks[2].setFill(tanks[2].getMaxFill());`
- [L208](../src/main/java/com/hbm/tileentity/machine/TileEntityICF.java#L208): `this.heat += this.laser * 0.25D;`
- [L216](../src/main/java/com/hbm/tileentity/machine/TileEntityICF.java#L216): `HeatingStep step = trait.getFirstStep();`
- [L224](../src/main/java/com/hbm/tileentity/machine/TileEntityICF.java#L224): `tanks[0].setFill(tanks[0].getFill() - step.amountReq * cycles);`
- [L225](../src/main/java/com/hbm/tileentity/machine/TileEntityICF.java#L225): `tanks[1].setFill(tanks[1].getFill() + step.amountProduced * cycles);`
- [L226](../src/main/java/com/hbm/tileentity/machine/TileEntityICF.java#L226): `this.heat -= step.heatReq * cycles;`

### ICFController

Source: [TileEntityICFController.java](../src/main/java/com/hbm/tileentity/machine/TileEntityICFController.java). Persistent tile registry: `tileentity_icf_controller`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### ICFPress

Source: [TileEntityICFPress.java](../src/main/java/com/hbm/tileentity/machine/TileEntityICFPress.java). Player container name: **ICF Fuel Pellet Maker**. Persistent tile registry: `tileentity_icf_press`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxMuon](../src/main/java/com/hbm/tileentity/machine/TileEntityICFPress.java#L38) | `public static final int maxMuon = 16;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityICFPress.java#L43) | `this.tanks[0] = new FluidTank(Fluids.DEUTERIUM, 16_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityICFPress.java#L44) | `this.tanks[1] = new FluidTank(Fluids.TRITIUM, 16_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L193](../src/main/java/com/hbm/tileentity/machine/TileEntityICFPress.java#L193): `if(usedFluid[0]) tanks[0].setFill(tanks[0].getFill() - 1000); else this.decrStackSize(4, 1);`
- [L194](../src/main/java/com/hbm/tileentity/machine/TileEntityICFPress.java#L194): `if(usedFluid[1]) tanks[1].setFill(tanks[1].getFill() - 1000); else this.decrStackSize(5, 1);`

### ITER

Source: [TileEntityITER.java](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java). Player container name: **Fusion Reactor**. Persistent tile registry: `tileentity_iter`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L56) | `public static final long maxPower = 10000000;` |
| [powerReq](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L57) | `public static final int powerReq = 100000;` |
| [MAX_HEAT_STRESS](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L81) | `private static final int MAX_HEAT_STRESS = 600;` |
| [HEAT_STRESS_PER_FAILED_COOLING](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L82) | `private static final int HEAT_STRESS_PER_FAILED_COOLING = 20;` |
| [HEAT_STRESS_DECAY](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L83) | `private static final int HEAT_STRESS_DECAY = 4;` |
| [BASE_SHIELD_DAMAGE_PER_TICK](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L85) | `private static final int BASE_SHIELD_DAMAGE_PER_TICK = 1;` |
| [DISRUPTION_SHIELD_DAMAGE](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L86) | `private static final int DISRUPTION_SHIELD_DAMAGE = 75;` |
| [OVERHEAT_SHIELD_DAMAGE](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L87) | `private static final int OVERHEAT_SHIELD_DAMAGE = 25;` |
| [MAX_PLASMA_BURN_PER_TICK](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L89) | `private static final int MAX_PLASMA_BURN_PER_TICK = 20;` |
| [rotorSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L111) | `private float rotorSpeed = 0F;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L152) | `tanks[0] = new FluidTank(Fluids.LIGHT_WATER, 1280000).migrateFrom(Fluids.WATER);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L158) | `tanks[1] = new FluidTank(Fluids.ULTRAHOTSTEAM, 128000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L160) | `tanks[2] = new FluidTank(Fluids.COOLANT, 16_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L161) | `tanks[3] = new FluidTank(Fluids.COOLANT_HOT, 16_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L163) | `plasma = new FluidTank(Fluids.PLASMA_DT, 16000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L320](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L320): `this.setStoredEnergyQuanta(this.energyQuanta - actualPowerReq);`
- [L444](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L444): `HeatingStep step = trait.getFirstStep();`
- [L518](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L518): `tanks[2].setFill(tanks[2].getFill() - coolantReq);`
- [L519](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L519): `tanks[3].setFill(tanks[3].getFill() + coolantReq);`
- [L521](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L521): `plasma.setFill(plasma.getFill() - 1);`
- [L587](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L587): `plasma.setFill(0);`
- [L837](../src/main/java/com/hbm/tileentity/machine/TileEntityITER.java#L837): `return (energyQuanta * i) / maxPower;`

### MachineAmgen

Source: [TileEntityMachineAmgen.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAmgen.java). Persistent tile registry: `tileentity_amgen`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_GENERATE](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAmgen.java#L18) | `private static final int TASK_GENERATE = 1;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAmgen.java#L24) | `public long maxPower = 500;` |
| [output](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAmgen.java#L25) | `protected long output = 0;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L58](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAmgen.java#L58): `this.setStoredEnergyQuanta(this.energyQuanta + this.output);`

### MachineArcFurnace

Source: [TileEntityMachineArcFurnace.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnace.java). Persistent tile registry: `tileentity_arc_furnace`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnace.java#L41) | `public static final long maxPower = 50000;` |
| [processingSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnace.java#L42) | `public static final int processingSpeed = 20;` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnace.java#L57) | `private long lastAccountingTick = Long.MIN_VALUE;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L260](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnace.java#L260): `return (energyQuanta * i) / maxPower;`
- [L396](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnace.java#L396): `long successfulTicks = energyQuanta / 250L;`
- [L400](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnace.java#L400): `try { this.setStoredEnergyQuanta(Math.max(0L, energyQuanta - steps * 250L)); }`
- [L452](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnace.java#L452): `long powerBoundary = energyQuanta / 250L + 1L;`

### MachineArcFurnaceLarge

Source: [TileEntityMachineArcFurnaceLarge.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnaceLarge.java). Player container name: **Plasma Arc Furnace**. Persistent tile registry: `tileentity_arc_furnace_large`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnaceLarge.java#L57) | `private static final int TASK_ACCOUNTING = 1;` |
| [TASK_EFFECT](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnaceLarge.java#L60) | `private static final int TASK_EFFECT = 4;` |
| [operatingPowerWatts](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnaceLarge.java#L71) | `private long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(1_000L);` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnaceLarge.java#L72) | `private long lastAccountingTick = Long.MIN_VALUE;` |
| [progressEffectThroughTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnaceLarge.java#L73) | `private long progressEffectThroughTick = Long.MIN_VALUE;` |
| [lastProgressEffectTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnaceLarge.java#L75) | `private long lastProgressEffectTick = Long.MIN_VALUE;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnaceLarge.java#L83) | `public static final long maxPower = 2_500_000;` |
| [maxLiquid](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnaceLarge.java#L109) | `public static final int maxLiquid = MaterialShapes.BLOCK.q(128);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L405](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnaceLarge.java#L405): `long affordable = consumption <= 0L ? elapsed : energyQuanta / consumption;`
- [L418](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnaceLarge.java#L418): `progress += increment;`
- [L428](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnaceLarge.java#L428): `try { this.setStoredEnergyQuanta(energyQuanta - consumption * progressedTicks); }`
- [L478](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcFurnaceLarge.java#L478): `long resourceTicks = energyQuanta / consumption;`

### MachineArcWelder

Source: [TileEntityMachineArcWelder.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcWelder.java). Player container name: **Arc Welder**. Persistent tile registry: `tileentity_arc_welder`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcWelder.java#L51) | `public long maxPower = 2_000;` |
| [processTime](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcWelder.java#L55) | `public int processTime = 1;` |
| [TASK_EFFECT](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcWelder.java#L72) | `private static final int TASK_EFFECT = 2;` |
| [TASK_SLOT_EFFECT](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcWelder.java#L76) | `private static final int TASK_SLOT_EFFECT = 2;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcWelder.java#L80) | `this.tank = new FluidTank(Fluids.NONE, 24_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L173](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcWelder.java#L173): `long poweredTicks = cost <= 0L ? Long.MAX_VALUE : energyQuanta / cost;`
- [L176](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcWelder.java#L176): `this.setStoredEnergyQuanta(energyQuanta - steps * cost);`
- [L177](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcWelder.java#L177): `progress += (int) steps;`
- [L256](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcWelder.java#L256): `long poweredBoundary = cost <= 0L ? Long.MAX_VALUE : energyQuanta / cost + 1L;`
- [L404](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineArcWelder.java#L404): `this.tank.setFill(tank.getFill() - recipe.fluid.fill);`

### MachineAssembler

Source: [TileEntityMachineAssembler.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssembler.java). Player container name: **Fabrication Machine**. Persistent tile registry: `tileentity_assembly_machine`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssembler.java#L43) | `private static final int TASK_ACCOUNTING = 1;` |
| [nextRuntimeTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssembler.java#L49) | `private long nextRuntimeTick = -1L;` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssembler.java#L50) | `private long lastAccountingTick = Long.MIN_VALUE;` |

### MachineAssemblerBase

Source: [TileEntityMachineAssemblerBase.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemblerBase.java). Persistent registration inherited/constructed; class identity is the inspection anchor.

| Field/boundary | Exact current source |
| --- | --- |
| [runtimeRecipeGeneration](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemblerBase.java#L49) | `private long runtimeRecipeGeneration = -1L;` |
| [runtimeLastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemblerBase.java#L51) | `private long runtimeLastAccountingTick = Long.MIN_VALUE;` |
| [runtimeLastChargeTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemblerBase.java#L52) | `private long runtimeLastChargeTick = Long.MIN_VALUE;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L217](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemblerBase.java#L217): `if(cost > 0L) wholeTicks = Math.min(wholeTicks, energyQuanta / cost / active);`
- [L221](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemblerBase.java#L221): `if(cost > 0L) this.setStoredEnergyQuanta(energyQuanta - wholeTicks * cost * active);`
- [L250](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemblerBase.java#L250): `this.setStoredEnergyQuanta(energyQuanta - cost);`
- [L276](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemblerBase.java#L276): `long powerBoundary = cost <= 0L ? Long.MAX_VALUE : energyQuanta / cost / active + 1L;`
- [L398](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemblerBase.java#L398): `this.setStoredEnergyQuanta(this.energyQuanta - EnergyUnits.wattsToQuantaPerTick(this.operatingPowerWatts));`

### MachineAssemfac

Source: [TileEntityMachineAssemfac.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemfac.java). Persistent tile registry: `tileentity_assemfac`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemfac.java#L53) | `water = new FluidTank(Fluids.FRESH_WATER, 64_000).migrateFrom(Fluids.WATER);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemfac.java#L54) | `steam = new FluidTank(Fluids.SPENTSTEAM, 64_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L175](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemfac.java#L175): `water.setFill(water.getFill() - amount);`
- [L176](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemfac.java#L176): `steam.setFill(steam.getFill() + amount);`
- [L187](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemfac.java#L187): `this.water.setFill(this.water.getFill() - getWaterRequired());`
- [L188](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAssemfac.java#L188): `this.steam.setFill(this.steam.getFill() + getWaterRequired());`

### MachineAutocrafter

Source: [TileEntityMachineAutocrafter.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAutocrafter.java). Player container name: **Automatic Crafting Table**. Persistent tile registry: `tileentity_autocrafter`.

| Field/boundary | Exact current source |
| --- | --- |
| [observedCraftingRecipeCount](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAutocrafter.java#L49) | `private int observedCraftingRecipeCount = -1;` |
| [consumption](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAutocrafter.java#L423) | `public static int consumption = 100;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAutocrafter.java#L424) | `public static long maxPower = consumption * 100;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L196](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAutocrafter.java#L196): `this.setStoredEnergyQuanta(energyQuanta - consumption);`

### MachineAutosaw

Source: [TileEntityMachineAutosaw.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAutosaw.java). Persistent tile registry: `tileentity_autosaw`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_FUEL_CHECK](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAutosaw.java#L37) | `private static final int TASK_FUEL_CHECK = 1;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAutosaw.java#L68) | `this.tank = new FluidTank(Fluids.WOODOIL, 100);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L99](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineAutosaw.java#L99): `tank.setFill(tank.getFill() - 1);`

### MachineCentrifuge

Source: [TileEntityMachineCentrifuge.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCentrifuge.java). Player container name: **Centrifuge**. Persistent tile registry: `tileentity_centrifuge`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCentrifuge.java#L54) | `private static final int TASK_ACCOUNTING = 1;` |
| [nextRuntimeTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCentrifuge.java#L61) | `private long nextRuntimeTick = -1L;` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCentrifuge.java#L62) | `private long lastAccountingTick = Long.MIN_VALUE;` |
| [operatingPowerWatts](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCentrifuge.java#L68) | `private long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(baseConsumption);` |
| [processingSpeedPerTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCentrifuge.java#L69) | `private int processingSpeedPerTick = 1;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCentrifuge.java#L81) | `public static int maxPower = 100000;` |
| [processingSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCentrifuge.java#L82) | `public static int processingSpeed = 200;` |
| [baseConsumption](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCentrifuge.java#L83) | `public static int baseConsumption = 200;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L162](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCentrifuge.java#L162): `return (energyQuanta * i) / maxPower;`
- [L321](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCentrifuge.java#L321): `else if(progress == 0) poweredSteps = (energyQuanta - 1L) / cost + 1L;`
- [L322](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCentrifuge.java#L322): `else poweredSteps = (energyQuanta - 1L) / cost;`
- [L325](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCentrifuge.java#L325): `if(cost > 0L && drained > 0L) this.setRuntimePower(energyQuanta - drained);`
- [L335](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCentrifuge.java#L335): `if(cost > 0L && chargedSteps > 0L) this.setRuntimePower(Math.max(0L, energyQuanta - chargedSteps * cost));`
- [L336](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCentrifuge.java#L336): `progress += (int) (steps * processingSpeedPerTick);`
- [L394](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCentrifuge.java#L394): `long poweredSteps = cost <= 0L ? Long.MAX_VALUE : progress == 0 ? (energyQuanta - 1L) / cost + 1L : (energyQuanta - 1L) / cost;`

### MachineChemfac

Source: [TileEntityMachineChemfac.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemfac.java). Persistent tile registry: `tileentity_chemfac`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemfac.java#L51) | `water = new FluidTank(Fluids.FRESH_WATER, 64_000).migrateFrom(Fluids.WATER);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemfac.java#L52) | `steam = new FluidTank(Fluids.SPENTSTEAM, 64_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L201](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemfac.java#L201): `water.setFill(water.getFill() - amount);`
- [L202](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemfac.java#L202): `steam.setFill(steam.getFill() + amount);`
- [L214](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemfac.java#L214): `this.water.setFill(this.water.getFill() - getWaterRequired());`
- [L215](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemfac.java#L215): `this.steam.setFill(this.steam.getFill() + getWaterRequired());`

### MachineChemplant

Source: [TileEntityMachineChemplant.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java). Player container name: **Chemical Reactor**. Persistent tile registry: `tileentity_chemical_plant`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L52) | `private static final int TASK_ACCOUNTING = 1;` |
| [nextRuntimeTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L64) | `private long nextRuntimeTick = -1L;` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L65) | `private long lastAccountingTick = Long.MIN_VALUE;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L82) | `public static final long maxPower = 100000;` |
| [maxProgress](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L84) | `public int maxProgress = 100;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L111) | `tanks[i] = new FluidTank(Fluids.NONE, 24_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L243](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L243): `if(recipe.inputFluids[0] != null) tanks[0].withPressure(recipe.inputFluids[0].pressure).setTankType(recipe.inputFluids[0].type);	else tanks[0].setTankType(Fluids.NONE);`
- [L244](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L244): `if(recipe.inputFluids[1] != null) tanks[1].withPressure(recipe.inputFluids[1].pressure).setTankType(recipe.inputFluids[1].type);	else tanks[1].setTankType(Fluids.NONE);`
- [L245](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L245): `if(recipe.outputFluids[0] != null) tanks[2].withPressure(recipe.outputFluids[0].pressure).setTankType(recipe.outputFluids[0].type);	else tanks[2].setTankType(Fluids.NONE);`
- [L246](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L246): `if(recipe.outputFluids[1] != null) tanks[3].withPressure(recipe.outputFluids[1].pressure).setTankType(recipe.outputFluids[1].type);	else tanks[3].setTankType(Fluids.NONE);`
- [L270](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L270): `if(recipe.inputFluids[0] != null) tanks[0].setFill(tanks[0].getFill() - recipe.inputFluids[0].fill);`
- [L271](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L271): `if(recipe.inputFluids[1] != null) tanks[1].setFill(tanks[1].getFill() - recipe.inputFluids[1].fill);`
- [L275](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L275): `if(recipe.outputFluids[0] != null) tanks[2].setFill(tanks[2].getFill() + recipe.outputFluids[0].fill);`
- [L276](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L276): `if(recipe.outputFluids[1] != null) tanks[3].setFill(tanks[3].getFill() + recipe.outputFluids[1].fill);`
- [L617](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L617): `long available = cost <= 0L ? Long.MAX_VALUE : energyQuanta / cost;`
- [L620](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L620): `progress += (int) whole;`
- [L621](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L621): `if(cost > 0L) this.setPowerInternal(energyQuanta - whole * cost);`
- [L644](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L644): `this.setPowerInternal(energyQuanta - cost);`
- [L680](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplant.java#L680): `long poweredTicks = cost <= 0L ? Long.MAX_VALUE : energyQuanta / cost + 1L;`

### MachineChemplantBase

Source: [TileEntityMachineChemplantBase.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java). Persistent registration inherited/constructed; class identity is the inspection anchor.

| Field/boundary | Exact current source |
| --- | --- |
| [runtimeRecipeGeneration](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L55) | `private long runtimeRecipeGeneration = -1L;` |
| [runtimeLastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L57) | `private long runtimeLastAccountingTick = Long.MIN_VALUE;` |
| [runtimeLastChargeTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L58) | `private long runtimeLastChargeTick = Long.MIN_VALUE;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L93) | `tanks[i] = new FluidTank(Fluids.NONE, getTankCapacity());` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L255](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L255): `if(cost > 0L) wholeTicks = Math.min(wholeTicks, energyQuanta / cost / active);`
- [L259](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L259): `if(cost > 0L) this.setStoredEnergyQuanta(energyQuanta - wholeTicks * cost * active);`
- [L296](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L296): `this.setStoredEnergyQuanta(energyQuanta - cost);`
- [L326](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L326): `long powerBoundary = cost <= 0L ? Long.MAX_VALUE : energyQuanta / cost / active + 1L;`
- [L400](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L400): `if(recipe.inputFluids[0] != null) tanks[index * 4].withPressure(recipe.inputFluids[0].pressure).setTankType(recipe.inputFluids[0].type);		else tanks[index * 4].setTankType(Fluids.NONE);`
- [L401](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L401): `if(recipe.inputFluids[1] != null) tanks[index * 4 + 1].withPressure(recipe.inputFluids[1].pressure).setTankType(recipe.inputFluids[1].type);	else tanks[index * 4 + 1].setTankType(Fluids.NONE);`
- [L402](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L402): `if(recipe.outputFluids[0] != null) tanks[index * 4 + 2].withPressure(recipe.outputFluids[0].pressure).setTankType(recipe.outputFluids[0].type);	else tanks[index * 4 + 2].setTankType(Fluids.NONE);`
- [L403](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L403): `if(recipe.outputFluids[1] != null) tanks[index * 4 + 3].withPressure(recipe.outputFluids[1].pressure).setTankType(recipe.outputFluids[1].type);	else tanks[index * 4 + 3].setTankType(Fluids.NONE);`
- [L430](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L430): `this.setStoredEnergyQuanta(this.energyQuanta - EnergyUnits.wattsToQuantaPerTick(this.operatingPowerWatts));`
- [L454](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L454): `if(recipe.inputFluids[0] != null) tanks[index * 4].setFill(tanks[index * 4].getFill() - recipe.inputFluids[0].fill);`
- [L455](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L455): `if(recipe.inputFluids[1] != null) tanks[index * 4 + 1].setFill(tanks[index * 4 + 1].getFill() - recipe.inputFluids[1].fill);`
- [L459](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L459): `if(recipe.outputFluids[0] != null) tanks[index * 4 + 2].setFill(tanks[index * 4 + 2].getFill() + recipe.outputFluids[0].fill);`
- [L460](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L460): `if(recipe.outputFluids[1] != null) tanks[index * 4 + 3].setFill(tanks[index * 4 + 3].getFill() + recipe.outputFluids[1].fill);`
- [L807](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L807): `tank.setFill(tank.getFill() - part);`
- [L819](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L819): `tank.setFill(tank.getFill() - total);`
- [L879](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineChemplantBase.java#L879): `tank.setFill(tank.getFill() + part);`

### MachineCombustionEngine

Source: [TileEntityMachineCombustionEngine.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCombustionEngine.java). Persistent tile registry: `tileentity_combustion_engine`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_GENERATE](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCombustionEngine.java#L41) | `private static final int TASK_GENERATE = 1;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCombustionEngine.java#L50) | `public static long maxPower = 2_500_000;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCombustionEngine.java#L66) | `this.tank = new FluidTank(Fluids.DIESEL, 24_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L179](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCombustionEngine.java#L179): `this.setStoredEnergyQuanta(this.energyQuanta + runtimeGeneratedQuanta);`
- [L191](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCombustionEngine.java#L191): `tank.setFill(fill / 10);`

### MachineCompressor

Source: [TileEntityMachineCompressor.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java). Player container name: **Compressor**. Persistent tile registry: `tileentity_compressor`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L53) | `public static final long maxPower = 100_000;` |
| [processTime](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L56) | `public int processTime = 100;` |
| [processTimeBase](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L57) | `public static final int processTimeBase = 100;` |
| [powerRequirementBase](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L59) | `public static final int powerRequirementBase = 2_500;` |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L66) | `private static final int TASK_ACCOUNTING = 1;` |
| [cachedRecipeInputPressure](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L74) | `private int cachedRecipeInputPressure = Integer.MIN_VALUE;` |
| [randSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L315) | `private float randSpeed = 0.1F;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L88) | `this.tanks[0] = new FluidTank(Fluids.NONE, 16_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L89) | `this.tanks[1] = new FluidTank(Fluids.NONE, 16_000).withPressure(1);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L89](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L89): `this.tanks[1] = new FluidTank(Fluids.NONE, 16_000).withPressure(1);`
- [L188](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L188): `long poweredTicks = energyQuanta <= powerRequirement ? 0L : powerRequirement <= 0L ? processTime - progress : (energyQuanta - 1L) / powerRequirement;`
- [L191](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L191): `this.setRuntimePower(energyQuanta - steps * cost);`
- [L192](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L192): `progress += (int) steps;`
- [L271](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L271): `long poweredTicks = powerRequirement <= 0 ? processTime : Math.max(1L, (energyQuanta - 1L) / powerRequirement);`
- [L360](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L360): `tanks[0].setFill(tanks[0].getFill() - 1_000);`
- [L361](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L361): `tanks[1].setFill(tanks[1].getFill() + 1_000);`
- [L363](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L363): `tanks[0].setFill(tanks[0].getFill() - cachedRecipe.inputAmount);`
- [L364](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L364): `tanks[1].setFill(tanks[1].getFill() + cachedRecipe.output.fill);`
- [L374](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L374): `tanks[1].withPressure(tanks[0].getPressure() + 1).setTankType(tanks[0].getTankType());`
- [L376](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L376): `tanks[1].withPressure(cachedRecipe.output.pressure).setTankType(cachedRecipe.output.type);`
- [L425](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L425): `tanks[0].withPressure(compression);`
- [L428](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L428): `tanks[1].withPressure(compression + 1);`
- [L430](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L430): `tanks[1].withPressure(cachedRecipe.output.pressure).setTankType(cachedRecipe.output.type);`
- [L555](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L555): `tanks[0].withPressure(compression);`
- [L560](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L560): `tanks[1].withPressure(compression + 1);`
- [L562](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCompressor.java#L562): `tanks[1].withPressure(recipe.output.pressure).setTankType(recipe.output.type);`

### MachineCryoDistill

Source: [TileEntityMachineCryoDistill.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCryoDistill.java). Player container name: **Fractional Distillation Column**. Persistent tile registry: `tileentity_cryogenic_distillator`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCryoDistill.java#L40) | `public static final long maxPower = 1_000_000;` |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCryoDistill.java#L43) | `private static final int TASK_ACCOUNTING = 1;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCryoDistill.java#L62) | `this.tanks[0] = new FluidTank(Fluids.AIR, 64_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCryoDistill.java#L63) | `this.tanks[1] = new FluidTank(Fluids.NITROGEN, 24_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCryoDistill.java#L64) | `this.tanks[2] = new FluidTank(Fluids.OXYGEN, 24_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCryoDistill.java#L65) | `this.tanks[3] = new FluidTank(Fluids.KRYPTON, 24_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCryoDistill.java#L66) | `this.tanks[4] = new FluidTank(Fluids.CARBONDIOXIDE, 24_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L144](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCryoDistill.java#L144): `tanks[0].setFill(tanks[0].getFill() - (int) (batches * 100L));`
- [L145](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCryoDistill.java#L145): `tanks[1].setFill(tanks[1].getFill() + (int) (batches * output.getW().fill));`
- [L146](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCryoDistill.java#L146): `tanks[2].setFill(tanks[2].getFill() + (int) (batches * output.getX().fill));`
- [L147](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCryoDistill.java#L147): `tanks[3].setFill(tanks[3].getFill() + (int) (batches * output.getY().fill));`
- [L148](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCryoDistill.java#L148): `tanks[4].setFill(tanks[4].getFill() + (int) (batches * output.getZ().fill));`
- [L149](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCryoDistill.java#L149): `this.setStoredEnergyQuanta(energyQuanta - batches * 20_000L);`
- [L223](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCryoDistill.java#L223): `long batches = Math.min(limit, Math.min(tanks[0].getFill() / 100L, energyQuanta / 20_000L));`

### MachineCrystallizer

Source: [TileEntityMachineCrystallizer.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCrystallizer.java). Player container name: **Leaching Reactor**. Persistent tile registry: `tileentity_acidomatic`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCrystallizer.java#L51) | `public static final long maxPower = 1000000;` |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCrystallizer.java#L61) | `private static final int TASK_ACCOUNTING = 1;` |
| [nextRuntimeTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCrystallizer.java#L68) | `private long nextRuntimeTick = -1L;` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCrystallizer.java#L69) | `private long lastAccountingTick = Long.MIN_VALUE;` |
| [operatingPowerWatts](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCrystallizer.java#L72) | `private long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(demand);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCrystallizer.java#L84) | `tank = new FluidTank(Fluids.PEROXIDE, 8000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L182](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCrystallizer.java#L182): `tank.setFill(tank.getFill() - getRequiredAcid(result.acidAmount));`
- [L279](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCrystallizer.java#L279): `long affordable = cost <= 0L ? remaining : energyQuanta / cost;`
- [L288](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCrystallizer.java#L288): `if(cost > 0L) this.setRuntimePower(energyQuanta - steps * cost);`
- [L289](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCrystallizer.java#L289): `progress += (int) steps;`
- [L349](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCrystallizer.java#L349): `long affordableUnits = cost <= 0L ? Long.MAX_VALUE : energyQuanta / cost;`
- [L442](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCrystallizer.java#L442): `return (energyQuanta * i) / maxPower;`

### MachineCyclotron

Source: [TileEntityMachineCyclotron.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java). Player container name: **Particle Accelerator**. Persistent tile registry: `tileentity_cyclotron`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L52) | `public static final long maxPower = 100000000;` |
| [consumption](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L53) | `public static int consumption = 1_000_000;` |
| [runtimeSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L69) | `private int runtimeSpeed = 1;` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L70) | `private long lastAccountingTick = Long.MIN_VALUE;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L88) | `this.tanks[0] = new FluidTank(Fluids.FRESH_WATER, 32000).migrateFrom(Fluids.WATER);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L89) | `this.tanks[1] = new FluidTank(Fluids.SPENTSTEAM, 32000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L90) | `this.tanks[2] = new FluidTank(Fluids.AMAT, 8000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L176](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L176): `long energyTicks = energyQuanta / cost;`
- [L189](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L189): `try { this.setStoredEnergyQuanta(energyQuanta - ticks * cost); }`
- [L192](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L192): `tanks[0].setFill(tanks[0].getFill() - coolantAmount);`
- [L193](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L193): `tanks[1].setFill(tanks[1].getFill() + coolantAmount);`
- [L194](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L194): `progress += (int) (ticks * speed);`
- [L232](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L232): `runtimeConsumption = Math.max(1, this.getConsumption());`
- [L294](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L294): `long energyTicks = energyQuanta / cost;`
- [L367](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L367): `if(energyQuanta < getConsumption())`
- [L420](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L420): `this.tanks[2].setFill(this.tanks[2].getFill() + (Integer)res[1]);`
- [L431](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L431): `this.tanks[2].setFill(this.tanks[2].getFill() + (Integer)res[1]);`
- [L436](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L436): `this.tanks[2].setFill(this.tanks[2].getMaxFill());`
- [L443](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L443): `public int getConsumption() {`
- [L456](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineCyclotron.java#L456): `return (energyQuanta * i) / maxPower;`

### MachineDiesel

Source: [TileEntityMachineDiesel.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java). Player container name: **Diesel Generator**. Persistent tile registry: `tileentity_diesel_generator`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_GENERATE](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java#L45) | `private static final int TASK_GENERATE = 1;` |
| [powerCap](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java#L57) | `public long powerCap = maxPower;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java#L61) | `public static long maxPower = 50000;` |
| [fluidCap](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java#L62) | `public static int fluidCap = 16000;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java#L77) | `tank = new FluidTank(Fluids.DIESEL, 4_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L65](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java#L65): `fuelEfficiency.put(FuelGrade.MEDIUM,	0.5D);`
- [L66](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java#L66): `fuelEfficiency.put(FuelGrade.HIGH,		0.75D);`
- [L67](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java#L67): `fuelEfficiency.put(FuelGrade.AERO,		0.1D);`
- [L142](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java#L142): `return (energyQuanta * i) / powerCap;`
- [L325](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java#L325): `tank.setFill(tank.getFill() - 1);`
- [L327](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java#L327): `tank.setFill(0);`
- [L334](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java#L334): `if(energyQuanta + getEnergyQuantaFromFuel() <= powerCap) {`
- [L335](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java#L335): `this.setStoredEnergyQuanta(this.energyQuanta + getEnergyQuantaFromFuel());`
- [L391](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDiesel.java#L391): `fuelEfficiency.put(grade, array.get(grade.ordinal()).getAsDouble());`

### MachineDischarger

Source: [TileEntityMachineDischarger.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDischarger.java). Player container name: **Matter Discharging Device.**. Persistent tile registry: `tileentity_discharger`.

| Field/boundary | Exact current source |
| --- | --- |
| [energyQuanta](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDischarger.java#L41) | `public long energyQuanta = 0;` |
| [maxtemp](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDischarger.java#L44) | `public static final int maxtemp = 2000;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDischarger.java#L45) | `public static final long maxPower = 500000000;` |
| [processSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDischarger.java#L47) | `public static final int processSpeed = 100;` |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDischarger.java#L55) | `private static final int TASK_ACCOUNTING = 1;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L159](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDischarger.java#L159): `return (energyQuanta * i) / maxPower;`
- [L329](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDischarger.java#L329): `this.setStoredEnergyQuanta(this.energyQuanta + (long) (Gen * 0.8));`
- [L334](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineDischarger.java#L334): `this.setStoredEnergyQuanta(this.energyQuanta + Gen * 4);`

### MachineEPress

Source: [TileEntityMachineEPress.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineEPress.java). Player container name: **Electric Press**. Persistent tile registry: `tileentity_electric_press`.

| Field/boundary | Exact current source |
| --- | --- |
| [energyQuanta](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineEPress.java#L44) | `public long energyQuanta = 0;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineEPress.java#L45) | `public final static long maxPower = 50000;` |
| [maxPress](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineEPress.java#L52) | `public final static int maxPress = 200;` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineEPress.java#L64) | `private long lastAccountingTick = Long.MIN_VALUE;` |
| [runtimeSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineEPress.java#L82) | `private int runtimeSpeed = 1;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L163](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineEPress.java#L163): `long poweredTicks = energyQuanta / 100L;`
- [L166](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineEPress.java#L166): `this.setRuntimePower(energyQuanta - steps * 100L);`
- [L176](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineEPress.java#L176): `this.setRuntimePower(energyQuanta - steps * 100L);`
- [L190](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineEPress.java#L190): `this.setRuntimePower(energyQuanta - steps * 100L);`
- [L197](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineEPress.java#L197): `this.setRuntimePower(energyQuanta - 100L);`
- [L290](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineEPress.java#L290): `long poweredTicks = energyQuanta / 100L;`

### MachineElectricFurnace

Source: [TileEntityMachineElectricFurnace.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineElectricFurnace.java). Player container name: **Electric Furnace**. Persistent tile registry: `tileentity_electric_furnace`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineElectricFurnace.java#L46) | `public static final long maxPower = 100000;` |
| [maxProgress](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineElectricFurnace.java#L47) | `public int maxProgress = 100;` |
| [operatingPowerWatts](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineElectricFurnace.java#L48) | `public long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(50L);` |
| [nextRuntimeTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineElectricFurnace.java#L52) | `private long nextRuntimeTick = -1L;` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineElectricFurnace.java#L53) | `private long lastAccountingTick = Long.MIN_VALUE;` |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineElectricFurnace.java#L59) | `private static final int TASK_ACCOUNTING = 1;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L145](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineElectricFurnace.java#L145): `return (energyQuanta * i) / maxPower;`
- [L302](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineElectricFurnace.java#L302): `long available = cost <= 0L ? target - cursor : energyQuanta / cost;`
- [L305](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineElectricFurnace.java#L305): `if(cost > 0L) this.setPowerInternal(energyQuanta - steps * cost);`
- [L306](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineElectricFurnace.java#L306): `progress += (int) steps;`
- [L342](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineElectricFurnace.java#L342): `long powerBoundary = cost <= 0L ? Long.MAX_VALUE : energyQuanta / cost + 1L;`

### MachineExcavator

Source: [TileEntityMachineExcavator.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExcavator.java). Persistent tile registry: `tileentity_ntm_excavator`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExcavator.java#L78) | `public static final long maxPower = 1_000_000;` |
| [chuteTimer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExcavator.java#L98) | `public int chuteTimer = 0;` |
| [speed](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExcavator.java#L100) | `public double speed = 1.0D;` |
| [baseConsumption](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExcavator.java#L101) | `public final long baseConsumption = 10_000L;` |
| [consumption](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExcavator.java#L102) | `public long consumption = baseConsumption;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExcavator.java#L108) | `this.tank = new FluidTank(Fluids.SULFURIC_ACID, 16_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L207](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExcavator.java#L207): `this.setStoredEnergyQuanta(this.energyQuanta - this.getPowerConsumption());`
- [L453](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExcavator.java#L453): `tank.setFill(tank.getFill() - ore.acidRequirement.fill);`

### MachineExposureChamber

Source: [TileEntityMachineExposureChamber.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExposureChamber.java). Player container name: **Exposure Chamber**. Persistent tile registry: `tileentity_exposure_chamber`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExposureChamber.java#L43) | `public static final long maxPower = 1_000_000;` |
| [processTimeBase](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExposureChamber.java#L46) | `public static final int processTimeBase = 200;` |
| [processTime](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExposureChamber.java#L47) | `public int processTime = processTimeBase;` |
| [consumptionBase](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExposureChamber.java#L48) | `public static final int consumptionBase = 10_000;` |
| [consumption](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExposureChamber.java#L49) | `public int consumption = consumptionBase;` |
| [maxParticles](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExposureChamber.java#L51) | `public static final int maxParticles = 8;` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExposureChamber.java#L58) | `private long lastAccountingTick = Long.MIN_VALUE;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L288](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExposureChamber.java#L288): `long affordable = energyQuanta / cost;`
- [L297](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExposureChamber.java#L297): `try { this.setStoredEnergyQuanta(energyQuanta - steps * cost); }`
- [L299](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExposureChamber.java#L299): `progress += (int) steps;`
- [L340](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineExposureChamber.java#L340): `long affordable = energyQuanta / cost;`

### MachineGasCent

Source: [TileEntityMachineGasCent.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java). Player container name: **Gas Centrifuge**. Persistent tile registry: `tileentity_gas_centrifuge`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L50) | `public static final int maxPower = 100000;` |
| [processingSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L51) | `public static final int processingSpeed = 150;` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L62) | `private long lastAccountingTick = Long.MIN_VALUE;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L81) | `tank = new FluidTank(Fluids.UF6, 2000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L136](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L136): `return (energyQuanta * i) / maxPower;`
- [L156](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L156): `inputTank.setFill(inputTank.getFill() - inputTank.getTankType().getFluidConsumed());`
- [L157](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L157): `outputTank.setFill(outputTank.getFill() + inputTank.getTankType().getFluidProduced());`
- [L167](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L167): `tank.setFill(tank.getFill() - fill);`
- [L168](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L168): `inputTank.setFill(inputTank.getFill() + fill);`
- [L189](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L189): `outputTank.setFill(outputTank.getFill() - fill);`
- [L190](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L190): `cent.inputTank.setFill(cent.inputTank.getFill() + fill);`
- [L294](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L294): `long affordable = energyQuanta / cost;`
- [L309](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L309): `try { this.setStoredEnergyQuanta(energyQuanta - steps * cost); }`
- [L311](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L311): `progress += (int) steps;`
- [L388](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L388): `outputTank.setFill(outputTank.getFill() - 600);`
- [L399](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L399): `long affordable = energyQuanta / cost;`
- [L590](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L590): `public void setFill(int i) {`
- [L604](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineGasCent.java#L604): `this.setFill(0);`

### MachineHTR3

Source: [TileEntityMachineHTR3.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTR3.java). Persistent tile registry: `tileentity_machine_htr3`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTR3.java#L48) | `tanks[0] = new FluidTank(Fluids.WASTEGAS, 1_280_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTR3.java#L49) | `tanks[0] = new FluidTank(Fluids.GAS_WATZ, 1_280_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTR3.java#L50) | `tanks[0] = new FluidTank(Fluids.GASEOUS_URANIUM_BROMIDE, 1_280_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTR3.java#L51) | `tanks[0] = new FluidTank(Fluids.GASEOUS_PLUTONIUM_BROMIDE, 1_280_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTR3.java#L52) | `tanks[0] = new FluidTank(Fluids.GASEOUS_THORIUM_BROMIDE, 1_280_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTR3.java#L53) | `tanks[0] = new FluidTank(Fluids.GASEOUS_SCHRABIDIUM_BROMIDE, 1_280_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTR3.java#L54) | `tanks[0] = new FluidTank(Fluids.SUPERHEATED_HYDROGEN, 1_280_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTR3.java#L55) | `tanks[0] = new FluidTank(Fluids.NONE, 1_280_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L343](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTR3.java#L343): `tank.setFill(tank.getFill() - fuelCost);`

### MachineHTRF4

Source: [TileEntityMachineHTRF4.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTRF4.java). Persistent tile registry: `tileentity_machine_htrf4`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTRF4.java#L38) | `public static long maxPower = 1_000_000_000;` |
| [POWER_COST_MULTIPLIER](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTRF4.java#L40) | `private static final int POWER_COST_MULTIPLIER = 1_000_000;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTRF4.java#L56) | `tanks[0] = new FluidTank(Fluids.PLASMA_DT, 64000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTRF4.java#L57) | `tanks[0] = new FluidTank(Fluids.PLASMA_HD, 64000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTRF4.java#L58) | `tanks[0] = new FluidTank(Fluids.PLASMA_HT, 64000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTRF4.java#L59) | `tanks[0] = new FluidTank(Fluids.PLASMA_DH3, 64000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTRF4.java#L60) | `tanks[0] = new FluidTank(Fluids.PLASMA_XM, 64000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTRF4.java#L61) | `tanks[0] = new FluidTank(Fluids.PLASMA_BF, 64000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L327](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTRF4.java#L327): `this.setStoredEnergyQuanta(this.energyQuanta - fuelCost * POWER_COST_MULTIPLIER);`
- [L329](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHTRF4.java#L329): `tank.setFill(tank.getFill() - fuelCost);`

### MachineHephaestus

Source: [TileEntityMachineHephaestus.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHephaestus.java). Persistent tile registry: `tileentity_hephaestus`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_HEAT](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHephaestus.java#L29) | `private static final int TASK_HEAT = 1;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHephaestus.java#L40) | `this.input = new FluidTank(Fluids.OIL, 24_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHephaestus.java#L41) | `this.output = new FluidTank(Fluids.HOTOIL, 24_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L170](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHephaestus.java#L170): `HeatingStep step = trait.getFirstStep();`
- [L177](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHephaestus.java#L177): `input.setFill(input.getFill() - step.amountReq * ops);`
- [L178](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHephaestus.java#L178): `output.setFill(output.getFill() + step.amountProduced * ops);`
- [L191](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHephaestus.java#L191): `FluidType outType = trait.getFirstStep().typeProduced;`
- [L220](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineHephaestus.java#L220): `heat += h;`

### MachineIGenerator

Source: [TileEntityMachineIGenerator.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineIGenerator.java). Player container name: **Industrial Generator**. Persistent tile registry: `tileentity_igenerator`.

| Field/boundary | Exact current source |
| --- | --- |
| [age](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineIGenerator.java#L45) | `public int age = 0;` |
| [coalConRate](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineIGenerator.java#L47) | `public static final int coalConRate = 75;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineIGenerator.java#L50) | `public static long maxPower = 1_000_000;` |
| [waterCap](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineIGenerator.java#L51) | `public static int waterCap = 16000;` |
| [oilCap](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineIGenerator.java#L52) | `public static int oilCap = 16000;` |
| [lubeCap](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineIGenerator.java#L53) | `public static int lubeCap = 4000;` |
| [coalGenRate](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineIGenerator.java#L54) | `public static int coalGenRate = 100;` |
| [rtgHeatMult](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineIGenerator.java#L55) | `public static double rtgHeatMult = 0.15D;` |
| [waterRate](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineIGenerator.java#L56) | `public static int waterRate = 10;` |
| [lubeRate](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineIGenerator.java#L57) | `public static int lubeRate = 1;` |
| [fluidHeatDiv](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineIGenerator.java#L58) | `public static long fluidHeatDiv = 1_000L;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineIGenerator.java#L65) | `tanks[0] = new FluidTank(Fluids.WATER, waterCap);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineIGenerator.java#L66) | `tanks[1] = new FluidTank(Fluids.HEATINGOIL, oilCap);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineIGenerator.java#L67) | `tanks[2] = new FluidTank(Fluids.LUBRICANT, lubeCap);` |

### MachineLPW2

Source: [TileEntityMachineLPW2.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineLPW2.java). Persistent tile registry: `tileentity_machine_lpw2`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineLPW2.java#L46) | `tanks[0] = new FluidTank(Fluids.KEROSENE_REFORM, 256_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineLPW2.java#L47) | `tanks[1] = new FluidTank(Fluids.OXYGEN, 256_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L261](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineLPW2.java#L261): `tank.setFill(tank.getFill() - fuelCost);`

### MachineLargeTurbine

Source: [TileEntityMachineLargeTurbine.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineLargeTurbine.java). Player container name: **Industrial Steam Turbine**. Persistent tile registry: `tileentity_industrial_turbine`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineLargeTurbine.java#L70) | `public static long maxPower = 100000000;` |
| [outputTankSize](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineLargeTurbine.java#L72) | `public static int outputTankSize = 10_240_000;` |
| [efficiency](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineLargeTurbine.java#L73) | `public static double efficiency = 1.0;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineLargeTurbine.java#L80) | `tanks[0] = new FluidTank(Fluids.STEAM, inputTankSize);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineLargeTurbine.java#L81) | `tanks[1] = new FluidTank(Fluids.SPENTSTEAM, outputTankSize);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L229](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineLargeTurbine.java#L229): `tanks[0].setFill(tanks[0].getFill() - ops * trait.amountReq);`
- [L230](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineLargeTurbine.java#L230): `tanks[1].setFill(tanks[1].getFill() + ops * trait.amountProduced);`
- [L231](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineLargeTurbine.java#L231): `this.setStoredEnergyQuanta(this.energyQuanta + (long) (ops * trait.heatEnergy * eff));`
- [L325](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineLargeTurbine.java#L325): `return (energyQuanta * i) / maxPower;`

### MachineMiniRTG

Source: [TileEntityMachineMiniRTG.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMiniRTG.java). Persistent tile registry: `tileentity_mini_rtg`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_GENERATE](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMiniRTG.java#L16) | `private static final int TASK_GENERATE = 1;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L46](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMiniRTG.java#L46): `this.setStoredEnergyQuanta(this.energyQuanta + this.getOutput());`

### MachineMiningLaser

Source: [TileEntityMachineMiningLaser.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMiningLaser.java). Player container name: **Mining Laser**. Persistent tile registry: `tileentity_mining_laser`.

| Field/boundary | Exact current source |
| --- | --- |
| [age](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMiningLaser.java#L64) | `public int age = 0;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMiningLaser.java#L65) | `public static final long maxPower = 100000000;` |
| [consumption](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMiningLaser.java#L66) | `public static final int consumption = 10000;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMiningLaser.java#L86) | `tank = new FluidTank(Fluids.OIL, 64_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L189](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMiningLaser.java#L189): `this.setStoredEnergyQuanta(this.energyQuanta - consumption);`
- [L426](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMiningLaser.java#L426): `tank.setFill(tank.getFill() + 500);`
- [L428](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMiningLaser.java#L428): `tank.setFill(tank.getMaxFill());`
- [L438](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMiningLaser.java#L438): `tank.setFill(tank.getFill() + 500);`
- [L440](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMiningLaser.java#L440): `tank.setFill(tank.getMaxFill());`
- [L609](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMiningLaser.java#L609): `public int getConsumption() {`
- [L619](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMiningLaser.java#L619): `return (int)((energyQuanta * i) / maxPower);`

### MachineMissileAssembly

Source: [TileEntityMachineMissileAssembly.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMissileAssembly.java). Persistent tile registry: `tileentity_missile_assembly`.

| Field/boundary | Exact current source |
| --- | --- |
| [lastPacketTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMissileAssembly.java#L33) | `private long lastPacketTick = Long.MIN_VALUE;` |

### MachineMixer

Source: [TileEntityMachineMixer.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java). Player container name: **Industrial Mixer**. Persistent tile registry: `tileentity_mixer`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java#L43) | `public static final long maxPower = 10_000;` |
| [operatingPowerWatts](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java#L52) | `private long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(50L);` |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java#L53) | `private static final int TASK_ACCOUNTING = 1;` |
| [nextRuntimeTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java#L60) | `private long nextRuntimeTick = -1L;` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java#L61) | `private long lastAccountingTick = Long.MIN_VALUE;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java#L72) | `this.tanks[0] = new FluidTank(Fluids.NONE, 16_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java#L73) | `this.tanks[1] = new FluidTank(Fluids.NONE, 16_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java#L74) | `this.tanks[2] = new FluidTank(Fluids.NONE, 24_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L157](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java#L157): `long affordable = cost <= 0L ? remaining : energyQuanta / cost;`
- [L165](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java#L165): `if(cost > 0L) this.setRuntimePower(energyQuanta - steps * cost);`
- [L166](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java#L166): `progress += (int) steps;`
- [L245](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java#L245): `long affordable = cost <= 0L ? Long.MAX_VALUE : energyQuanta / cost;`
- [L366](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java#L366): `if(recipe.input1 != null) tanks[0].setFill(tanks[0].getFill() - recipe.input1.fill);`
- [L367](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java#L367): `if(recipe.input2 != null) tanks[1].setFill(tanks[1].getFill() - recipe.input2.fill);`
- [L369](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java#L369): `tanks[2].setFill(tanks[2].getFill() + recipe.output);`
- [L372](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineMixer.java#L372): `public int getConsumption() {`

### MachineOreSlopper

Source: [TileEntityMachineOreSlopper.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineOreSlopper.java). Player container name: **B.O.P.**. Persistent tile registry: `tileentity_ore_slopper`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineOreSlopper.java#L66) | `public static final long maxPower = 100_000;` |
| [waterUsedBase](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineOreSlopper.java#L68) | `public static final int waterUsedBase = 1_000;` |
| [waterUsed](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineOreSlopper.java#L69) | `public int waterUsed = waterUsedBase;` |
| [consumptionBase](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineOreSlopper.java#L70) | `public static final long consumptionBase = 200;` |
| [consumption](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineOreSlopper.java#L71) | `public long consumption = consumptionBase;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineOreSlopper.java#L93) | `tanks[0] = new FluidTank(Fluids.WATER, 16_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineOreSlopper.java#L94) | `tanks[1] = new FluidTank(Fluids.SLOP, 16_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L219](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineOreSlopper.java#L219): `this.setStoredEnergyQuanta(this.energyQuanta - this.consumption);`
- [L220](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineOreSlopper.java#L220): `this.progress += 1F / (600 - speed * 150);`
- [L225](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineOreSlopper.java#L225): `progress -= 1F;`
- [L232](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineOreSlopper.java#L232): `this.tanks[0].setFill(this.tanks[0].getFill() - waterUsed);`
- [L233](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineOreSlopper.java#L233): `this.tanks[1].setFill(this.tanks[1].getFill() + waterUsed);`

### MachinePlasmaHeater

Source: [TileEntityMachinePlasmaHeater.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java). Player container name: **Plasma Heater**. Persistent tile registry: `tileentity_plasma_heater`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L40) | `public static final long maxPower = 100000000;` |
| [STARTUP_POWER_REQUIRED](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L60) | `private static final int STARTUP_POWER_REQUIRED = 500000;` |
| [OPERATING_POWER_PER_PACKET](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L65) | `private static final int OPERATING_POWER_PER_PACKET = 50000;` |
| [CONTAINMENT_POWER_PER_TICK](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L71) | `private static final int CONTAINMENT_POWER_PER_TICK = 10000;` |
| [MAX_PLASMA_AGE](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L76) | `private static final int MAX_PLASMA_AGE = 5;` |
| [MAX_CONVERT_PER_TICK](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L82) | `private static final int MAX_CONVERT_PER_TICK = 1;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L93) | `tanks[0] = new FluidTank(Fluids.DEUTERIUM, 16_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L94) | `tanks[1] = new FluidTank(Fluids.TRITIUM, 16_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L100) | `plasma = new FluidTank(Fluids.PLASMA_DT, PLASMA_BUFFER);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L190](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L190): `plasma.setFill(0);`
- [L259](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L259): `this.setStoredEnergyQuanta(this.energyQuanta - charge);`
- [L267](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L267): `convert = Math.min(convert, (int) (energyQuanta / OPERATING_POWER_PER_PACKET));`
- [L284](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L284): `tanks[0].setFill(tanks[0].getFill() - convert);`
- [L285](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L285): `tanks[1].setFill(tanks[1].getFill() - convert);`
- [L287](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L287): `plasma.setFill(plasma.getFill() + convert);`
- [L289](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L289): `this.setStoredEnergyQuanta(this.energyQuanta - convert * OPERATING_POWER_PER_PACKET);`
- [L305](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L305): `this.setStoredEnergyQuanta(this.energyQuanta - CONTAINMENT_POWER_PER_TICK);`
- [L324](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L324): `plasma.setFill(0);`
- [L386](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L386): `this.plasma.setFill(this.plasma.getFill() - toLoad);`
- [L387](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L387): `iter.plasma.setFill(iter.plasma.getFill() + toLoad);`
- [L443](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L443): `this.plasma.setFill(this.plasma.getFill() - toLoad);`
- [L444](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L444): `htrf.tanks[0].setFill(htrf.tanks[0].getFill() + toLoad);`
- [L549](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L549): `return (energyQuanta * i) / maxPower;`
- [L570](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePlasmaHeater.java#L570): `plasma.setFill(plasma.getMaxFill());`

### MachinePress

Source: [TileEntityMachinePress.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePress.java). Player container name: **Burner Press**. Persistent tile registry: `tileentity_press`.

| Field/boundary | Exact current source |
| --- | --- |
| [speed](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePress.java#L27) | `public int speed = 0;` |
| [maxSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePress.java#L28) | `public static final int maxSpeed = 400;` |
| [progressAtMax](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePress.java#L29) | `public static final int progressAtMax = 25;` |
| [burnTime](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePress.java#L30) | `public int burnTime = 0;` |
| [maxPress](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePress.java#L37) | `public final static int maxPress = 200;` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePress.java#L44) | `private long lastAccountingTick = Long.MIN_VALUE;` |

### MachinePumpBase

Source: [TileEntityMachinePumpBase.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpBase.java). Persistent registration inherited/constructed; class identity is the inspection anchor.

| Field/boundary | Exact current source |
| --- | --- |
| [groundCheckDelay](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpBase.java#L80) | `public int groundCheckDelay = 0;` |
| [steamSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpBase.java#L84) | `public static int steamSpeed = 1_000;` |
| [electricSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpBase.java#L85) | `public static int electricSpeed = 10_000;` |
| [nonWaterDebuff](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpBase.java#L86) | `public static int nonWaterDebuff = 100;` |

### MachinePumpElectric

Source: [TileEntityMachinePumpElectric.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpElectric.java). Persistent tile registry: `tileentity_electric_pump`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpElectric.java#L15) | `public static final long maxPower = 10_000;` |
| [operatingPowerWatts](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpElectric.java#L16) | `private static final long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(1_000L);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpElectric.java#L20) | `water = new FluidTank(Fluids.WATER, electricSpeed * 100);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L50](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpElectric.java#L50): `this.setStoredEnergyQuanta(this.energyQuanta - EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts));`
- [L52](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpElectric.java#L52): `water.setFill(Math.min(water.getFill() + pumpSpeed, water.getMaxFill()));`

### MachinePumpSteam

Source: [TileEntityMachinePumpSteam.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpSteam.java). Persistent tile registry: `tileentity_steam_pump`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpSteam.java#L16) | `water = new FluidTank(Fluids.WATER, steamSpeed * 100);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpSteam.java#L17) | `steam = new FluidTank(Fluids.STEAM, 1_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpSteam.java#L18) | `lps = new FluidTank(Fluids.SPENTSTEAM, 10);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L74](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpSteam.java#L74): `steam.setFill(steam.getFill() - 100);`
- [L75](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpSteam.java#L75): `lps.setFill(lps.getFill() + 1);`
- [L77](../src/main/java/com/hbm/tileentity/machine/TileEntityMachinePumpSteam.java#L77): `water.setFill(Math.min(water.getFill() + pumpSpeed, water.getMaxFill()));`

### MachineRTG

Source: [TileEntityMachineRTG.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRTG.java). Persistent tile registry: `tileentity_machine_rtg`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_GENERATE](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRTG.java#L32) | `private static final int TASK_GENERATE = 1;` |
| [heatMax](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRTG.java#L42) | `public final int heatMax = VersatileConfig.rtgDecay() ? 600 : 200;` |
| [powerMax](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRTG.java#L44) | `public final long powerMax = 100000;` |
| [lastSyncedPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRTG.java#L45) | `private long lastSyncedPower = Long.MIN_VALUE;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L206](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRTG.java#L206): `return (energyQuanta * i) / powerMax;`
- [L256](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRTG.java#L256): `this.setStoredEnergyQuanta(this.energyQuanta + heat * 5L);`

### MachineRadGen

Source: [TileEntityMachineRadGen.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadGen.java). Player container name: **Radiation-Powered Engine**. Persistent tile registry: `tileentity_radgen`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_GENERATE](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadGen.java#L36) | `private static final int TASK_GENERATE = 1;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadGen.java#L56) | `public static final long maxPower = 1000000;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L113](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadGen.java#L113): `this.setStoredEnergyQuanta(energyQuanta + EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts[i]));`

### MachineRadiolysis

Source: [TileEntityMachineRadiolysis.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadiolysis.java). Player container name: **RTG and Radiolysis Chamber**. Persistent tile registry: `tileentity_radiolysis`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadiolysis.java#L45) | `public static final int maxPower = 1000000;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadiolysis.java#L56) | `tanks[0] = new FluidTank(Fluids.NONE, 2_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadiolysis.java#L57) | `tanks[1] = new FluidTank(Fluids.NONE, 2_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadiolysis.java#L58) | `tanks[2] = new FluidTank(Fluids.NONE, 2_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L173](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadiolysis.java#L173): `this.setStoredEnergyQuanta(this.energyQuanta + heat * 10);`
- [L232](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadiolysis.java#L232): `tanks[0].setFill(tanks[0].getFill() - 100);`
- [L233](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadiolysis.java#L233): `tanks[1].setFill(tanks[1].getFill() + left);`
- [L234](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRadiolysis.java#L234): `tanks[2].setFill(tanks[2].getFill() + right);`

### MachineReactorBreeding

Source: [TileEntityMachineReactorBreeding.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineReactorBreeding.java). Player container name: **Fast Neutron Breeding Blanket**. Persistent tile registry: `tileentity_reactor`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L74](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineReactorBreeding.java#L74): `progress += 0.0025F * ((float) this.flux / (float) out.flux);`

### MachineRocketAssembly

Source: [TileEntityMachineRocketAssembly.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRocketAssembly.java). Persistent tile registry: `tileentity_rocket_assembly`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### MachineRotaryFurnace

Source: [TileEntityMachineRotaryFurnace.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRotaryFurnace.java). Player container name: **Rotary Furnace**. Persistent tile registry: `tileentity_rotary_furnace`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxOutput](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRotaryFurnace.java#L63) | `public static final int maxOutput = MaterialShapes.BLOCK.q(16);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRotaryFurnace.java#L71) | `tanks[0] = new FluidTank(Fluids.NONE, 16_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRotaryFurnace.java#L72) | `tanks[1] = new FluidTank(Fluids.STEAM, 4_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRotaryFurnace.java#L73) | `tanks[2] = new FluidTank(Fluids.SPENTSTEAM, 40);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L199](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRotaryFurnace.java#L199): `this.progress += 1F / recipe.duration;`
- [L200](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRotaryFurnace.java#L200): `tanks[1].setFill(tanks[1].getFill() - recipe.steam);`
- [L201](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRotaryFurnace.java#L201): `tanks[2].setFill(tanks[2].getFill() + recipe.steam / 100);`
- [L205](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRotaryFurnace.java#L205): `this.progress -= 1F;`
- [L383](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRotaryFurnace.java#L383): `this.tanks[0].setFill(tanks[0].getFill() - recipe.fluid.fill);`
- [L392](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRotaryFurnace.java#L392): `tank.setFill(tank.getFill() + fluidAmount);`
- [L396](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineRotaryFurnace.java#L396): `tank.setFill(tank.getMaxFill());`

### MachineSPP

Source: [TileEntityMachineSPP.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSPP.java). Persistent tile registry: `tileentity_spp`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_GENERATE](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSPP.java#L17) | `private static final int TASK_GENERATE = 1;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSPP.java#L23) | `public static final long maxPower = 100000;` |
| [age](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSPP.java#L24) | `public int age = 0;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L62](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSPP.java#L62): `if(gen > 0) this.setStoredEnergyQuanta(this.energyQuanta + gen);`

### MachineSchrabidiumTransmutator

Source: [TileEntityMachineSchrabidiumTransmutator.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSchrabidiumTransmutator.java). Player container name: **Unbihexium Transmutation Device**. Persistent tile registry: `tileentity_schrabidium_transmutator`.

| Field/boundary | Exact current source |
| --- | --- |
| [energyQuanta](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSchrabidiumTransmutator.java#L31) | `public long energyQuanta = 0;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSchrabidiumTransmutator.java#L33) | `public static final long maxPower = 5000000;` |
| [processSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSchrabidiumTransmutator.java#L34) | `public static final int processSpeed = 600;` |
| [TASK_ACCOUNT](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSchrabidiumTransmutator.java#L45) | `private static final int TASK_ACCOUNT = 1;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L123](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSchrabidiumTransmutator.java#L123): `return (energyQuanta * i) / maxPower;`

### MachineShredder

Source: [TileEntityMachineShredder.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineShredder.java). Persistent tile registry: `tileentity_machine_shredder`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineShredder.java#L44) | `public static final long maxPower = 10000;` |
| [processingSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineShredder.java#L45) | `public static final int processingSpeed = 60;` |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineShredder.java#L50) | `private static final int TASK_ACCOUNTING = 1;` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineShredder.java#L62) | `private long lastAccountingTick = Long.MIN_VALUE;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L331](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineShredder.java#L331): `long steps = Math.min(remaining, Math.min(Math.min(processingSpeed - progress, (energyQuanta + 4L) / 5L), untilSound));`
- [L338](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineShredder.java#L338): `this.setStoredEnergyQuanta(energyQuanta - 5L * steps);`
- [L339](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineShredder.java#L339): `progress += (int) steps;`
- [L407](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineShredder.java#L407): `long due = Math.min(Math.min(processingSpeed - progress, (energyQuanta + 4L) / 5L), untilSound);`
- [L537](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineShredder.java#L537): `return (energyQuanta * i) / maxPower;`

### MachineSolarPanel

Source: [TileEntityMachineSolarPanel.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSolarPanel.java). Persistent tile registry: `tileentity_solarpanel`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_GENERATE](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSolarPanel.java#L20) | `private static final int TASK_GENERATE = 1;` |
| [maxpwr](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSolarPanel.java#L26) | `private long maxpwr = 1_000;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L55](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSolarPanel.java#L55): `this.setStoredEnergyQuanta(this.energyQuanta + getOutput());`

### MachineSolderingStation

Source: [TileEntityMachineSolderingStation.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSolderingStation.java). Player container name: **Soldering Station**. Persistent tile registry: `tileentity_soldering_station`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSolderingStation.java#L52) | `public long maxPower = 2_000;` |
| [processTime](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSolderingStation.java#L57) | `public int processTime = 1;` |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSolderingStation.java#L61) | `private static final int TASK_ACCOUNTING = 1;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSolderingStation.java#L81) | `this.tank = new FluidTank(Fluids.NONE, 8_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L153](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSolderingStation.java#L153): `long poweredTicks = cost <= 0L ? processTime - progress : energyQuanta / cost;`
- [L156](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSolderingStation.java#L156): `this.setRuntimePower(energyQuanta - steps * cost);`
- [L157](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSolderingStation.java#L157): `progress += (int) steps;`
- [L253](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSolderingStation.java#L253): `long poweredTicks = cost <= 0L ? processTime : Math.max(1L, energyQuanta / cost);`
- [L353](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineSolderingStation.java#L353): `this.tank.setFill(tank.getFill() - recipe.fluid.fill);`

### MachineStrandCaster

Source: [TileEntityMachineStrandCaster.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineStrandCaster.java). Player container name: **Strand Caster**. Persistent tile registry: `tileentity_strand_caster`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineStrandCaster.java#L67) | `water = new FluidTank(Fluids.FRESH_WATER, 64_000).migrateFrom(Fluids.WATER);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineStrandCaster.java#L68) | `steam = new FluidTank(Fluids.SPENTSTEAM, 64_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L153](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineStrandCaster.java#L153): `water.setFill(water.getFill() - waterUsed);`
- [L154](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineStrandCaster.java#L154): `steam.setFill(steam.getFill() + waterUsed);`

### MachineTransformer

Source: [TileEntityMachineTransformer.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTransformer.java). Persistent tile registry: `tileentity_transformer`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### MachineTurbine

Source: [TileEntityMachineTurbine.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbine.java). Persistent tile registry: `tileentity_turbine`.

| Field/boundary | Exact current source |
| --- | --- |
| [age](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbine.java#L63) | `public int age = 0;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbine.java#L74) | `public static long maxPower = 1_000_000;` |
| [outputTankSize](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbine.java#L76) | `public static int outputTankSize = 128_000;` |
| [maxSteamPerTick](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbine.java#L77) | `public static int maxSteamPerTick = 6_000;` |
| [efficiency](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbine.java#L78) | `public static double efficiency = 0.85D;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbine.java#L83) | `tanks[0] = new FluidTank(Fluids.STEAM, inputTankSize);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbine.java#L84) | `tanks[1] = new FluidTank(Fluids.SPENTSTEAM, outputTankSize);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L330](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbine.java#L330): `return (energyQuanta * i) / maxPower;`
- [L372](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbine.java#L372): `tanks[0].setFill(tanks[0].getFill() - ops * trait.amountReq);`
- [L373](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbine.java#L373): `tanks[1].setFill(tanks[1].getFill() + ops * trait.amountProduced);`
- [L374](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbine.java#L374): `this.setStoredEnergyQuanta(this.energyQuanta + (long) (ops * trait.heatEnergy * eff));`

### MachineTurbineGas

Source: [TileEntityMachineTurbineGas.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java). Player container name: **Combined Cycle Gas Turbine**. Persistent tile registry: `tileentity_machine_gasturbine`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L58) | `public static final long maxPower = 1000000L;` |
| [counter](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L71) | `public int counter = 0;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L93) | `tanks[0] = new FluidTank(Fluids.GAS, 100000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L94) | `tanks[1] = new FluidTank(Fluids.LUBRICANT, 16000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L95) | `tanks[2] = new FluidTank(Fluids.FRESH_WATER, 16000).migrateFrom(Fluids.WATER);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L96) | `tanks[3] = new FluidTank(Fluids.HOTSTEAM, 160000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L81](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L81): `fuelMaxCons.put(Fluids.GAS, 50D);`
- [L82](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L82): `fuelMaxCons.put(Fluids.SYNGAS, 10D);`
- [L83](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L83): `fuelMaxCons.put(Fluids.OXYHYDROGEN, 100D);`
- [L84](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L84): `fuelMaxCons.put(Fluids.REFORMGAS, 5D);`
- [L188](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L188): `int powerSliderTarget = 60 - (int) (60 * energyQuanta / maxPower);`
- [L404](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L404): `tanks[0].setFill(tanks[0].getFill() - (int) Math.floor(fuelToConsume));`
- [L408](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L408): `tanks[1].setFill(tanks[1].getFill() - 1);`
- [L411](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L411): `tanks[0].setFill(0);`
- [L415](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L415): `tanks[1].setFill(0);`
- [L439](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L439): `this.setStoredEnergyQuanta(this.energyQuanta + instantPowerOutput);`
- [L449](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L449): `tanks[2].setFill(tanks[2].getFill() - (int) Math.floor(waterToBoil));`
- [L450](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbineGas.java#L450): `tanks[3].setFill(tanks[3].getFill() + 10 * (int) Math.floor(waterToBoil));`

### MachineTurbofan

Source: [TileEntityMachineTurbofan.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbofan.java). Player container name: **Turbofan**. Persistent tile registry: `tileentity_machine_turbofan`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_BURN](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbofan.java#L55) | `private static final int TASK_BURN = 1;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbofan.java#L65) | `public static final long maxPower = 1_000_000;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbofan.java#L83) | `tank = new FluidTank(Fluids.KEROSENE, 24000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbofan.java#L84) | `blood = new FluidTank(Fluids.BLOOD, 24000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L218](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbofan.java#L218): `return (energyQuanta * i) / maxPower;`
- [L284](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbofan.java#L284): `this.tank.setFill(this.tank.getFill() - amountToBurn);`
- [L286](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbofan.java#L286): `this.setStoredEnergyQuanta(this.energyQuanta + this.output);`
- [L389](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbofan.java#L389): `blood.setFill(blood.getFill() + 50);`
- [L391](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineTurbofan.java#L391): `blood.setFill(blood.getMaxFill());`

### MachineVacuumCircuit

Source: [TileEntityMachineVacuumCircuit.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineVacuumCircuit.java). Player container name: **Vacuum**. Persistent tile registry: `tileentity_vacuum_circuit`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineVacuumCircuit.java#L46) | `public long maxPower = 2_000;` |
| [processTime](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineVacuumCircuit.java#L50) | `public int processTime = 1;` |
| [canOperate](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineVacuumCircuit.java#L55) | `public boolean canOperate = true;` |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineVacuumCircuit.java#L56) | `private static final int TASK_ACCOUNTING = 1;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L148](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineVacuumCircuit.java#L148): `long available = cost <= 0L ? processTime - progress : energyQuanta / cost;`
- [L151](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineVacuumCircuit.java#L151): `this.setRuntimePower(energyQuanta - steps * cost);`
- [L152](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineVacuumCircuit.java#L152): `progress += (int) steps;`
- [L251](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineVacuumCircuit.java#L251): `long poweredTicks = cost <= 0L ? Long.MAX_VALUE : energyQuanta / cost + 1L;`

### MachineWoodBurner

Source: [TileEntityMachineWoodBurner.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineWoodBurner.java). Player container name: **Wood-Burner**. Persistent tile registry: `tileentity_wood_burner`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_GENERATE](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineWoodBurner.java#L41) | `private static final int TASK_GENERATE = 1;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineWoodBurner.java#L49) | `public static final long maxPower = 100_000;` |
| [liquidBurn](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineWoodBurner.java#L52) | `public boolean liquidBurn = false;` |
| [powerGen](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineWoodBurner.java#L54) | `protected int powerGen = 0;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineWoodBurner.java#L66) | `this.tank = new FluidTank(Fluids.WOODOIL, 16_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L58](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineWoodBurner.java#L58): `public static ModuleBurnTime burnModule = new ModuleBurnTime().setLogTimeMod(4).setWoodTimeMod(2);`
- [L154](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineWoodBurner.java#L154): `tank.setFill(tank.getFill() - toBurn);`
- [L159](../src/main/java/com/hbm/tileentity/machine/TileEntityMachineWoodBurner.java#L159): `this.setStoredEnergyQuanta(energyQuanta + powerGen);`

### MoltenSaltReactor

Source: [TileEntityMoltenSaltReactor.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactor.java). Player container name: **Molten Salt Reactor**. Persistent tile registry: `tileentity_molten_salt_reactor`.

| Field/boundary | Exact current source |
| --- | --- |
| [SALT_CAPACITY](../src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactor.java#L25) | `public static final int SALT_CAPACITY = 16_000;` |
| [HOT_SALT_CAPACITY](../src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactor.java#L26) | `public static final int HOT_SALT_CAPACITY = 16_000;` |
| [MAX_CORROSION](../src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactor.java#L28) | `public static final int MAX_CORROSION = 100;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactor.java#L37) | `tanks[0] = new FluidTank(Fluids.THORIUM_SALT, SALT_CAPACITY);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactor.java#L38) | `tanks[1] = new FluidTank(Fluids.THORIUM_SALT_HOT, HOT_SALT_CAPACITY);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L129](../src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactor.java#L129): `tanks[0].setFill(tanks[0].getFill() - operations);`
- [L130](../src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactor.java#L130): `tanks[1].setFill(tanks[1].getFill() + operations);`
- [L156](../src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactor.java#L156): `tanks[1].setFill(tanks[1].getFill() - hotLeak);`
- [L158](../src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactor.java#L158): `tanks[0].setFill(tanks[0].getFill() - coldLeak);`

### MoltenSaltReactorPort

Source: [TileEntityMoltenSaltReactorPort.java](../src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactorPort.java). Persistent tile registry: `tileentity_msr_port`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactorPort.java#L29) | `this.tank = new FluidTank(input ? Fluids.THORIUM_SALT : Fluids.THORIUM_SALT_HOT, 16_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L101](../src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactorPort.java#L101): `tank.setFill((int) overshoot);`
- [L111](../src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactorPort.java#L111): `hotTank.setFill(hotTank.getFill() - transfer);`
- [L112](../src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactorPort.java#L112): `tank.setFill(tank.getFill() + transfer);`
- [L120](../src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactorPort.java#L120): `tank.setFill(tank.getFill() + (int) amount);`
- [L123](../src/main/java/com/hbm/tileentity/machine/TileEntityMoltenSaltReactorPort.java#L123): `tank.setFill(tank.getMaxFill());`

### NukeFurnace

Source: [TileEntityNukeFurnace.java](../src/main/java/com/hbm/tileentity/machine/TileEntityNukeFurnace.java). Persistent tile registry: `tileentity_nukefurnace`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_FUEL](../src/main/java/com/hbm/tileentity/machine/TileEntityNukeFurnace.java#L30) | `private static final int TASK_FUEL = 1;` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/TileEntityNukeFurnace.java#L37) | `private long lastAccountingTick = Long.MIN_VALUE;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/TileEntityNukeFurnace.java#L48) | `public static final int maxPower = 1000;` |
| [processingSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntityNukeFurnace.java#L49) | `public static final int processingSpeed = 25;` |

### PWRController

Source: [TileEntityPWRController.java](../src/main/java/com/hbm/tileentity/machine/TileEntityPWRController.java). Persistent tile registry: `tileentity_pwr_controller`.

| Field/boundary | Exact current source |
| --- | --- |
| [coreHeatCapacityBase](../src/main/java/com/hbm/tileentity/machine/TileEntityPWRController.java#L54) | `public static final long coreHeatCapacityBase = 12_000_000;` |
| [coreHeatCapacity](../src/main/java/com/hbm/tileentity/machine/TileEntityPWRController.java#L55) | `public long coreHeatCapacity = 12_000_000;` |
| [hullHeatCapacityBase](../src/main/java/com/hbm/tileentity/machine/TileEntityPWRController.java#L57) | `public static final long hullHeatCapacityBase = 12_000_000;` |
| [unloadDelay](../src/main/java/com/hbm/tileentity/machine/TileEntityPWRController.java#L76) | `public int unloadDelay = 0;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityPWRController.java#L88) | `this.tanks[0] = new FluidTank(Fluids.BORATED_WATER, 128_000).migrateFrom(Fluids.COOLANT);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityPWRController.java#L89) | `this.tanks[1] = new FluidTank(Fluids.BORATED_WATER_HOT, 128_000).migrateFrom(Fluids.COOLANT_HOT);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L316](../src/main/java/com/hbm/tileentity/machine/TileEntityPWRController.java#L316): `this.progress += totalOutput;`
- [L319](../src/main/java/com/hbm/tileentity/machine/TileEntityPWRController.java#L319): `this.progress -= this.processTime;`
- [L342](../src/main/java/com/hbm/tileentity/machine/TileEntityPWRController.java#L342): `this.hullHeat -= (hullHeat - averageCoreHeat) * coreCoolingApproachNum;`
- [L348](../src/main/java/com/hbm/tileentity/machine/TileEntityPWRController.java#L348): `this.hullHeat += this.coreHeat * 0.002D;`
- [L434](../src/main/java/com/hbm/tileentity/machine/TileEntityPWRController.java#L434): `HeatingStep step = trait.getFirstStep();`
- [L440](../src/main/java/com/hbm/tileentity/machine/TileEntityPWRController.java#L440): `this.hullHeat -= step.heatReq * cycles;`
- [L441](../src/main/java/com/hbm/tileentity/machine/TileEntityPWRController.java#L441): `this.tanks[0].setFill(tanks[0].getFill() - step.amountReq * cycles);`
- [L442](../src/main/java/com/hbm/tileentity/machine/TileEntityPWRController.java#L442): `this.tanks[1].setFill(tanks[1].getFill() + step.amountProduced * cycles);`
- [L477](../src/main/java/com/hbm/tileentity/machine/TileEntityPWRController.java#L477): `tanks[1].setTankType(trait.getFirstStep().typeProduced);`

### Radiator

Source: [TileEntityRadiator.java](../src/main/java/com/hbm/tileentity/machine/TileEntityRadiator.java). Persistent tile registry: `tileentity_radiator`.

| Field/boundary | Exact current source |
| --- | --- |
| [outputTankSize](../src/main/java/com/hbm/tileentity/machine/TileEntityRadiator.java#L22) | `public static int outputTankSize = 500;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityRadiator.java#L26) | `tanks[0] = new FluidTank(Fluids.SPENTSTEAM, inputTankSize);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityRadiator.java#L27) | `tanks[1] = new FluidTank(Fluids.FRESH_WATER, outputTankSize).migrateFrom(Fluids.WATER);` |

### ReactorResearch

Source: [TileEntityReactorResearch.java](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorResearch.java). Player container name: **Pool-Type Research Reactor**. Persistent tile registry: `tileentity_small_reactor`.

| Field/boundary | Exact current source |
| --- | --- |
| [speed](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorResearch.java#L59) | `public double speed = 0.04;` |
| [maxHeat](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorResearch.java#L64) | `public final int maxHeat = 50000;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L147](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorResearch.java#L147): `this.heat -= (this.heat * (float) 0.07 * water / 12);`
- [L149](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorResearch.java#L149): `this.heat -= 1;`
- [L375](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorResearch.java#L375): `this.heat += (int) Math.ceil(outFlux * 1.35D);`

### ReactorZirnox

Source: [TileEntityReactorZirnox.java](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java). Player container name: **Magnox Gas-Cooled Reactor**. Persistent tile registry: `tileentity_zirnox`.

| Field/boundary | Exact current source |
| --- | --- |
| [runtimeConnectionMetadata](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L73) | `private int runtimeConnectionMetadata = Integer.MIN_VALUE;` |
| [maxHeat](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L76) | `public static final int maxHeat = 100000;` |
| [maxPressure](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L79) | `public static final int maxPressure = 100000;` |
| [graphiteHeat](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L107) | `public int graphiteHeat = 0;` |
| [decayHeat](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L127) | `public double decayHeat = 0.0D;` |
| [graphiteDamage](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L130) | `public int graphiteDamage = 0;` |
| [claddingDamage](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L131) | `public int claddingDamage = 0;` |
| [activePower](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L137) | `public int activePower = 0;` |
| [NOMINAL_PRESSURE_BAR](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L161) | `private static final double NOMINAL_PRESSURE_BAR = 26.0D;` |
| [MAX_PRESSURE_BAR](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L162) | `private static final double MAX_PRESSURE_BAR = 30.0D;` |
| [STEAM_START_C](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L164) | `private static final double STEAM_START_C = 300.0D;` |
| [STEAM_FULL_C](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L165) | `private static final double STEAM_FULL_C = 450.0D;` |
| [MAX_STEAM_PER_TICK](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L168) | `private static final int MAX_STEAM_PER_TICK = 55;` |
| [HEAT_REMOVED_PER_MB_STEAM](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L169) | `private static final int HEAT_REMOVED_PER_MB_STEAM = 80;` |
| [CLADDING_DAMAGE_TEMP_C](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L171) | `private static final double CLADDING_DAMAGE_TEMP_C = 500.0D;` |
| [FUEL_DAMAGE_TEMP_C](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L172) | `private static final double FUEL_DAMAGE_TEMP_C = 600.0D;` |
| [GRAPHITE_DAMAGE_TEMP_C](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L173) | `private static final double GRAPHITE_DAMAGE_TEMP_C = 600.0D;` |
| [CORE_CAPACITY](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L175) | `public static final double CORE_CAPACITY = 2500.0D;` |
| [PRIMARY_CAPACITY](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L176) | `public static final double PRIMARY_CAPACITY = 600.0D;` |
| [DECAY_RELEASE_RATE](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L178) | `private static final double DECAY_RELEASE_RATE = 0.0005D;` |
| [FEEDWATER_TRIP_MB](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L179) | `public static final int FEEDWATER_TRIP_MB = 8000;` |
| [FEEDWATER_RESTART_MB](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L180) | `public static final int FEEDWATER_RESTART_MB = 12000;` |
| [TRIP_PRESSURE_BAR](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L184) | `private static final double TRIP_PRESSURE_BAR = 29.0D;` |
| [RESTART_PRESSURE_BAR](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L185) | `private static final double RESTART_PRESSURE_BAR = 27.0D;` |
| [RELIEF_PRESSURE_BAR](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L186) | `private static final double RELIEF_PRESSURE_BAR = 30.0D;` |
| [RUPTURE_PRESSURE_BAR](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L187) | `private static final double RUPTURE_PRESSURE_BAR = 34.0D;` |
| [RAPID_PRESSURE_LOSS_BAR](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L189) | `private static final double RAPID_PRESSURE_LOSS_BAR = 1.5D;` |
| [SHUTDOWN_WATER_MB](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L193) | `private static final int SHUTDOWN_WATER_MB = 10;` |
| [SHUTDOWN_HEAT_PER_MB](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L196) | `private static final int SHUTDOWN_HEAT_PER_MB = HEAT_REMOVED_PER_MB_STEAM / 5;` |
| [RESTART_STEAM_ROOM](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L197) | `private static final int RESTART_STEAM_ROOM = 1000;` |
| [MAX_GRAPHITE_DAMAGE](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L199) | `private static final int MAX_GRAPHITE_DAMAGE = 100000;` |
| [CLADDING_LEAK_DAMAGE](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L200) | `public static final int CLADDING_LEAK_DAMAGE = 10000;` |
| [CLADDING_SEVERE_DAMAGE](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L201) | `public static final int CLADDING_SEVERE_DAMAGE = 50000;` |
| [MAX_CLADDING_DAMAGE](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L202) | `public static final int MAX_CLADDING_DAMAGE = 100000;` |
| [MAX_PRIMARY_CONTAMINATION](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L203) | `private static final double MAX_PRIMARY_CONTAMINATION = 1000000.0D;` |
| [MELTDOWN_OVERPRESSURE](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L208) | `private static final int MELTDOWN_OVERPRESSURE = 0;` |
| [MELTDOWN_OVERHEAT](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L209) | `private static final int MELTDOWN_OVERHEAT = 1;` |
| [COLLAPSE_MIN_HORIZONTAL_SPEED](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L212) | `private static final double COLLAPSE_MIN_HORIZONTAL_SPEED = 0.0D;` |
| [COLLAPSE_MAX_HORIZONTAL_SPEED](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L213) | `private static final double COLLAPSE_MAX_HORIZONTAL_SPEED = 0.10D;` |
| [COLLAPSE_MIN_VERTICAL_SPEED](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L214) | `private static final double COLLAPSE_MIN_VERTICAL_SPEED = 0.02D;` |
| [COLLAPSE_MAX_VERTICAL_SPEED](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L215) | `private static final double COLLAPSE_MAX_VERTICAL_SPEED = 0.08D;` |
| [RUPTURE_HEAVY_MIN_HORIZONTAL_SPEED](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L216) | `private static final double RUPTURE_HEAVY_MIN_HORIZONTAL_SPEED = 0.0D;` |
| [RUPTURE_HEAVY_MAX_HORIZONTAL_SPEED](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L217) | `private static final double RUPTURE_HEAVY_MAX_HORIZONTAL_SPEED = 0.20D;` |
| [RUPTURE_HEAVY_MIN_VERTICAL_SPEED](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L218) | `private static final double RUPTURE_HEAVY_MIN_VERTICAL_SPEED = 0.05D;` |
| [RUPTURE_HEAVY_MAX_VERTICAL_SPEED](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L219) | `private static final double RUPTURE_HEAVY_MAX_VERTICAL_SPEED = 0.20D;` |
| [RUPTURE_LIGHT_MIN_HORIZONTAL_SPEED](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L220) | `private static final double RUPTURE_LIGHT_MIN_HORIZONTAL_SPEED = 0.15D;` |
| [RUPTURE_LIGHT_MAX_HORIZONTAL_SPEED](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L221) | `private static final double RUPTURE_LIGHT_MAX_HORIZONTAL_SPEED = 0.45D;` |
| [RUPTURE_LIGHT_MIN_VERTICAL_SPEED](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L222) | `private static final double RUPTURE_LIGHT_MIN_VERTICAL_SPEED = 0.10D;` |
| [RUPTURE_LIGHT_MAX_VERTICAL_SPEED](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L223) | `private static final double RUPTURE_LIGHT_MAX_VERTICAL_SPEED = 0.30D;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L276) | `steam = new FluidTank(Fluids.SUPERHOTSTEAM, 8000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L277) | `carbonDioxide = new FluidTank(Fluids.CARBONDIOXIDE, 16000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L278) | `water = new FluidTank(Fluids.LIGHT_WATER, 32000).migrateFrom(Fluids.WATER);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L698](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L698): `water.setFill(water.getFill() - cycle);`
- [L699](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L699): `steam.setFill(steam.getFill() + cycle);`
- [L711](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L711): `water.setFill(water.getFill() - used);`
- [L1414](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L1414): `carbonDioxide.setFill(currentFill + lost);`
- [L1429](../src/main/java/com/hbm/tileentity/machine/TileEntityReactorZirnox.java#L1429): `carbonDioxide.setFill(fill - removed);`

### RtgFurnace

Source: [TileEntityRtgFurnace.java](../src/main/java/com/hbm/tileentity/machine/TileEntityRtgFurnace.java). Persistent tile registry: `tileentity_rtgfurnace`.

| Field/boundary | Exact current source |
| --- | --- |
| [processingSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntityRtgFurnace.java#L32) | `public static final int processingSpeed = 1000;` |

### SILEX

Source: [TileEntitySILEX.java](../src/main/java/com/hbm/tileentity/machine/TileEntitySILEX.java). Player container name: **SILEX**. Persistent tile registry: `tileentity_silex`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxFill](../src/main/java/com/hbm/tileentity/machine/TileEntitySILEX.java#L46) | `public static final int maxFill = 16000;` |
| [processTime](../src/main/java/com/hbm/tileentity/machine/TileEntitySILEX.java#L48) | `public final int processTime = 100;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntitySILEX.java#L58) | `tank = new FluidTank(Fluids.PEROXIDE, 16000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L210](../src/main/java/com/hbm/tileentity/machine/TileEntitySILEX.java#L210): `tank.setFill(tank.getFill() - toFill);`
- [L225](../src/main/java/com/hbm/tileentity/machine/TileEntitySILEX.java#L225): `tank.setFill(tank.getFill() - toFill);`
- [L246](../src/main/java/com/hbm/tileentity/machine/TileEntitySILEX.java#L246): `tank.setFill(tank.getFill() - load);`
- [L273](../src/main/java/com/hbm/tileentity/machine/TileEntitySILEX.java#L273): `progress += progressSpeed;`

### Sawmill

Source: [TileEntitySawmill.java](../src/main/java/com/hbm/tileentity/machine/TileEntitySawmill.java). Persistent tile registry: `tileentity_sawmill`.

| Field/boundary | Exact current source |
| --- | --- |
| [diffusion](../src/main/java/com/hbm/tileentity/machine/TileEntitySawmill.java#L55) | `public static final double diffusion = 0.1D;` |
| [overspeed](../src/main/java/com/hbm/tileentity/machine/TileEntitySawmill.java#L58) | `private int overspeed = 0;` |
| [processingTime](../src/main/java/com/hbm/tileentity/machine/TileEntitySawmill.java#L65) | `public static final int processingTime = 600;` |
| [bladeSpeed](../src/main/java/com/hbm/tileentity/machine/TileEntitySawmill.java#L71) | `public double bladeSpeed = 0.0D;` |
| [MAX_HEAT_DRAW](../src/main/java/com/hbm/tileentity/machine/TileEntitySawmill.java#L77) | `private static final int MAX_HEAT_DRAW = 120;` |
| [HEAT_TO_BLADE_SPEED](../src/main/java/com/hbm/tileentity/machine/TileEntitySawmill.java#L79) | `private static final double HEAT_TO_BLADE_SPEED = 0.060D;` |
| [MIN_CUT_SPEED](../src/main/java/com/hbm/tileentity/machine/TileEntitySawmill.java#L83) | `private static final double MIN_CUT_SPEED = 90.0D;` |
| [NOMINAL_CUT_SPEED](../src/main/java/com/hbm/tileentity/machine/TileEntitySawmill.java#L84) | `private static final double NOMINAL_CUT_SPEED = 240.0D;` |
| [MAX_SAFE_SPEED](../src/main/java/com/hbm/tileentity/machine/TileEntitySawmill.java#L85) | `private static final double MAX_SAFE_SPEED = 360.0D;` |
| [FAILURE_SPEED](../src/main/java/com/hbm/tileentity/machine/TileEntitySawmill.java#L86) | `private static final double FAILURE_SPEED = 460.0D;` |
| [MAX_WEAR](../src/main/java/com/hbm/tileentity/machine/TileEntitySawmill.java#L88) | `private static final double MAX_WEAR = 3.0D;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L246](../src/main/java/com/hbm/tileentity/machine/TileEntitySawmill.java#L246): `progress += gain;`

### SolarBoiler

Source: [TileEntitySolarBoiler.java](../src/main/java/com/hbm/tileentity/machine/TileEntitySolarBoiler.java). Persistent tile registry: `tileentity_solarboiler`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntitySolarBoiler.java#L44) | `water = new FluidTank(Fluids.FRESH_WATER, 100).migrateFrom(Fluids.WATER);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntitySolarBoiler.java#L45) | `steam = new FluidTank(Fluids.STEAM, 10_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L57](../src/main/java/com/hbm/tileentity/machine/TileEntitySolarBoiler.java#L57): `heat += amount;`
- [L125](../src/main/java/com/hbm/tileentity/machine/TileEntitySolarBoiler.java#L125): `water.setFill(water.getFill() - process);`
- [L126](../src/main/java/com/hbm/tileentity/machine/TileEntitySolarBoiler.java#L126): `steam.setFill(steam.getFill() + process * 100);`

### SolarMirror

Source: [TileEntitySolarMirror.java](../src/main/java/com/hbm/tileentity/machine/TileEntitySolarMirror.java). Persistent tile registry: `tileentity_solarmirror`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_HEAT_TRANSFER](../src/main/java/com/hbm/tileentity/machine/TileEntitySolarMirror.java#L17) | `private static final int TASK_HEAT_TRANSFER = 1;` |

### SteamEngine

Source: [TileEntitySteamEngine.java](../src/main/java/com/hbm/tileentity/machine/TileEntitySteamEngine.java). Persistent tile registry: `tileentity_steam_engine`.

| Field/boundary | Exact current source |
| --- | --- |
| [steamCap](../src/main/java/com/hbm/tileentity/machine/TileEntitySteamEngine.java#L59) | `private static int steamCap = 2_000;` |
| [ldsCap](../src/main/java/com/hbm/tileentity/machine/TileEntitySteamEngine.java#L60) | `private static int ldsCap = 20;` |
| [efficiency](../src/main/java/com/hbm/tileentity/machine/TileEntitySteamEngine.java#L61) | `private static double efficiency = 0.85D;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntitySteamEngine.java#L66) | `tanks[0] = new FluidTank(Fluids.STEAM, steamCap);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntitySteamEngine.java#L67) | `tanks[1] = new FluidTank(Fluids.SPENTSTEAM, ldsCap);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L143](../src/main/java/com/hbm/tileentity/machine/TileEntitySteamEngine.java#L143): `tanks[0].setFill(tanks[0].getFill() - ops * trait.amountReq);`
- [L144](../src/main/java/com/hbm/tileentity/machine/TileEntitySteamEngine.java#L144): `tanks[1].setFill(tanks[1].getFill() + ops * trait.amountProduced);`

### Stirling

Source: [TileEntityStirling.java](../src/main/java/com/hbm/tileentity/machine/TileEntityStirling.java). Persistent tile registry: `tileentity_stirling`.

| Field/boundary | Exact current source |
| --- | --- |
| [overspeed](../src/main/java/com/hbm/tileentity/machine/TileEntityStirling.java#L36) | `private int overspeed = 0;` |
| [diffusion](../src/main/java/com/hbm/tileentity/machine/TileEntityStirling.java#L43) | `public static double diffusion = 0.1D;` |
| [efficiency](../src/main/java/com/hbm/tileentity/machine/TileEntityStirling.java#L44) | `public static double efficiency = 0.5D;` |
| [maxHeatNormal](../src/main/java/com/hbm/tileentity/machine/TileEntityStirling.java#L45) | `public static int maxHeatNormal = 300;` |
| [maxHeatSteel](../src/main/java/com/hbm/tileentity/machine/TileEntityStirling.java#L46) | `public static int maxHeatSteel = 1500;` |
| [overspeedLimit](../src/main/java/com/hbm/tileentity/machine/TileEntityStirling.java#L47) | `public static int overspeedLimit = 300;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L182](../src/main/java/com/hbm/tileentity/machine/TileEntityStirling.java#L182): `this.heat += heatSrc;`

### Watz

Source: [TileEntityWatz.java](../src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java). Persistent tile registry: `tileentity_watz`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java#L99) | `this.tanks[0] = new FluidTank(Fluids.HELIUM4, 64_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java#L100) | `this.tanks[1] = new FluidTank(Fluids.HELIUM4_HOT, 64_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java#L101) | `this.runtimeSharedTanks = new FluidTank[] { new FluidTank(Fluids.HELIUM4, 0), new FluidTank(Fluids.HELIUM4_HOT, 0) };` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L228](../src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java#L228): `sharedTanks[i].setFill(0);`
- [L242](../src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java#L242): `sharedTanks[i].setFill(`
- [L271](../src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java#L271): `segment.heat -= passiveCooling;`
- [L279](../src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java#L279): `sharedTanks[j].setFill(sharedTanks[j].getFill() - min);`
- [L280](../src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java#L280): `segment.tanks[j].setFill(min);`
- [L357](../src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java#L357): `tanks[1].setTankType(tanks[0].getTankType().getTrait(FT_Heatable.class).getFirstStep().typeProduced);`
- [L373](../src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java#L373): `HeatingStep step = trait.getFirstStep();`
- [L380](../src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java#L380): `this.heat -= cycles * step.heatReq;`
- [L381](../src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java#L381): `tanks[0].setFill(tanks[0].getFill() - cycles * step.amountReq);`
- [L382](../src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java#L382): `tanks[1].setFill(tanks[1].getFill() + cycles * step.amountProduced);`
- [L540](../src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java#L540): `this.heat += addedHeat;`
- [L570](../src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java#L570): `this.heat += diff;`
- [L571](../src/main/java/com/hbm/tileentity/machine/TileEntityWatz.java#L571): `above.heat -= diff;`

### MachineAlkylation

Source: [oil/TileEntityMachineAlkylation.java](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineAlkylation.java). Persistent tile registry: `tileentity_alkylation`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineAlkylation.java#L30) | `public static final long maxPower = 1_000_000;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineAlkylation.java#L46) | `this.tanks[0] = new FluidTank(Fluids.CHLOROMETHANE, 8_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineAlkylation.java#L47) | `this.tanks[1] = new FluidTank(Fluids.NONE, 4_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineAlkylation.java#L48) | `this.tanks[2] = new FluidTank(Fluids.UNSATURATEDS, 8_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineAlkylation.java#L49) | `this.tanks[3] = new FluidTank(Fluids.CHLORINE, 8_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L142](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineAlkylation.java#L142): `tanks[0].setFill(tanks[0].getFill() - 100);`
- [L143](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineAlkylation.java#L143): `tanks[1].setFill(tanks[1].getFill() - cachedRecipe.getX().fill);`
- [L144](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineAlkylation.java#L144): `tanks[2].setFill(tanks[2].getFill() + cachedRecipe.getY().fill);`
- [L145](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineAlkylation.java#L145): `tanks[3].setFill(tanks[3].getFill() + cachedRecipe.getZ().fill);`
- [L146](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineAlkylation.java#L146): `this.setStoredEnergyQuanta(energyQuanta - 4_000L);`

### MachineCatalyticCracker

Source: [oil/TileEntityMachineCatalyticCracker.java](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticCracker.java). Persistent tile registry: `tileentity_catalytic_cracker`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticCracker.java#L42) | `tanks[0] = new FluidTank(Fluids.BITUMEN, 4000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticCracker.java#L43) | `tanks[1] = new FluidTank(Fluids.STEAM, 8000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticCracker.java#L44) | `tanks[2] = new FluidTank(Fluids.OIL, 4000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticCracker.java#L45) | `tanks[3] = new FluidTank(Fluids.PETROLEUM, 4000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticCracker.java#L46) | `tanks[4] = new FluidTank(Fluids.SPENTSTEAM, 800);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L81](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticCracker.java#L81): `tanks[0].setFill(tanks[0].getFill() - 100);`
- [L82](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticCracker.java#L82): `tanks[1].setFill(tanks[1].getFill() - 200);`
- [L83](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticCracker.java#L83): `tanks[2].setFill(tanks[2].getFill() + left);`
- [L84](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticCracker.java#L84): `tanks[3].setFill(tanks[3].getFill() + right);`
- [L85](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticCracker.java#L85): `tanks[4].setFill(tanks[4].getFill() + 2);`

### MachineCatalyticReformer

Source: [oil/TileEntityMachineCatalyticReformer.java](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java). Player container name: **Catalytic Reformer**. Persistent tile registry: `tileentity_catalytic_reformer`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java#L39) | `public static final long maxPower = 1_000_000;` |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java#L42) | `private static final int TASK_ACCOUNTING = 1;` |
| [TASK_OUTPUT](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java#L44) | `private static final int TASK_OUTPUT = 3;` |
| [TASK_SLOT_OUTPUT](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java#L47) | `private static final int TASK_SLOT_OUTPUT = 2;` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java#L53) | `private long lastAccountingTick = Long.MIN_VALUE;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java#L62) | `this.tanks[0] = new FluidTank(Fluids.NAPHTHA, 64_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java#L63) | `this.tanks[1] = new FluidTank(Fluids.REFORMATE, 24_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java#L64) | `this.tanks[2] = new FluidTank(Fluids.PETROLEUM, 24_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java#L65) | `this.tanks[3] = new FluidTank(Fluids.HYDROGEN, 24_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L223](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java#L223): `long steps = Math.min(tanks[0].getFill() / 100L, energyQuanta / 20_000L);`
- [L239](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java#L239): `long steps = Math.min(elapsed, Math.min(tanks[0].getFill() / 100L, energyQuanta / 20_000L));`
- [L248](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java#L248): `tanks[0].setFill(tanks[0].getFill() - (int) (steps * 100L));`
- [L249](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java#L249): `tanks[1].setFill(tanks[1].getFill() + (int) (steps * output.getX().fill));`
- [L250](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java#L250): `tanks[2].setFill(tanks[2].getFill() + (int) (steps * output.getY().fill));`
- [L251](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java#L251): `tanks[3].setFill(tanks[3].getFill() + (int) (steps * output.getZ().fill));`
- [L252](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCatalyticReformer.java#L252): `this.setStoredEnergyQuanta(energyQuanta - steps * 20_000L);`

### MachineCoker

Source: [oil/TileEntityMachineCoker.java](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCoker.java). Player container name: **Coker Unit**. Persistent tile registry: `tileentity_coker`.

| Field/boundary | Exact current source |
| --- | --- |
| [processTime](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCoker.java#L40) | `public static int processTime = 20_000;` |
| [maxHeat](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCoker.java#L43) | `public static int maxHeat = 100_000;` |
| [diffusion](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCoker.java#L44) | `public static double diffusion = 0.25D;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCoker.java#L59) | `tanks[0] = new FluidTank(Fluids.HEAVYOIL, 16_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCoker.java#L60) | `tanks[1] = new FluidTank(Fluids.OIL_COKER, 8_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L116](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCoker.java#L116): `progress += burn;`
- [L117](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCoker.java#L117): `heat -= burn;`
- [L120](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCoker.java#L120): `progress -= processTime;`
- [L205](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCoker.java#L205): `if(byproduct != null) tanks[1].setFill(tanks[1].getFill() + byproduct.fill);`
- [L206](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCoker.java#L206): `tanks[0].setFill(tanks[0].getFill() - cachedRecipe.getX());`
- [L287](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineCoker.java#L287): `this.heat += diff;`

### MachineFrackingTower

Source: [oil/TileEntityMachineFrackingTower.java](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFrackingTower.java). Player container name: **Hydraulic Fracking Tower**. Persistent tile registry: `tileentity_fracking_tower`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFrackingTower.java#L34) | `protected static int maxPower = 5_000_000;` |
| [consumption](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFrackingTower.java#L35) | `protected static int consumption = 5000;` |
| [delay](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFrackingTower.java#L37) | `protected static int delay = 20;` |
| [gasPerDepositMax](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFrackingTower.java#L40) | `protected static int gasPerDepositMax = 500;` |
| [gasPerBedrockDepositMax](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFrackingTower.java#L44) | `protected static int gasPerBedrockDepositMax = 50;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFrackingTower.java#L52) | `tanks[0] = new FluidTank(Fluids.OIL, 64_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFrackingTower.java#L53) | `tanks[1] = new FluidTank(Fluids.GAS, 64_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFrackingTower.java#L54) | `tanks[2] = new FluidTank(Fluids.FRACKSOL, 64_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L152](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFrackingTower.java#L152): `this.tanks[0].setFill(this.tanks[0].getFill() + oil);`
- [L153](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFrackingTower.java#L153): `if(this.tanks[0].getFill() > this.tanks[0].getMaxFill()) this.tanks[0].setFill(tanks[0].getMaxFill());`
- [L154](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFrackingTower.java#L154): `this.tanks[1].setFill(this.tanks[1].getFill() + gas);`
- [L155](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFrackingTower.java#L155): `if(this.tanks[1].getFill() > this.tanks[1].getMaxFill()) this.tanks[1].setFill(tanks[1].getMaxFill());`
- [L157](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFrackingTower.java#L157): `this.tanks[2].setFill(tanks[2].getFill() - solutionRequired);`

### MachineFractionTower

Source: [oil/TileEntityMachineFractionTower.java](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFractionTower.java). Persistent tile registry: `tileentity_fraction_tower`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFractionTower.java#L46) | `tanks[0] = new FluidTank(Fluids.HEAVYOIL, 4000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFractionTower.java#L47) | `tanks[1] = new FluidTank(Fluids.BITUMEN, 4000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFractionTower.java#L48) | `tanks[2] = new FluidTank(Fluids.SMEAR, 4000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L158](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFractionTower.java#L158): `tanks[0].setFill(tanks[0].getFill() - oil);`
- [L159](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFractionTower.java#L159): `tanks[1].setFill(tanks[1].getFill() + left);`
- [L160](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFractionTower.java#L160): `tanks[2].setFill(tanks[2].getFill() + right);`
- [L161](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFractionTower.java#L161): `frac.tanks[0].setFill(frac.tanks[0].getFill() + oil);`
- [L162](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFractionTower.java#L162): `frac.tanks[1].setFill(frac.tanks[1].getFill() - left);`
- [L163](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFractionTower.java#L163): `frac.tanks[2].setFill(frac.tanks[2].getFill() - right);`
- [L232](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFractionTower.java#L232): `tanks[0].setFill(tanks[0].getFill() - 100);`
- [L233](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFractionTower.java#L233): `tanks[1].setFill(tanks[1].getFill() + left);`
- [L234](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineFractionTower.java#L234): `tanks[2].setFill(tanks[2].getFill() + right);`

### MachineGasFlare

Source: [oil/TileEntityMachineGasFlare.java](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineGasFlare.java). Player container name: **Flare Stack**. Persistent tile registry: `tileentity_gasflare`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineGasFlare.java#L60) | `public static final long maxPower = 100000;` |
| [doesBurn](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineGasFlare.java#L63) | `public boolean doesBurn = false;` |
| [output](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineGasFlare.java#L65) | `protected int output = 0;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineGasFlare.java#L69) | `tank = new FluidTank(Fluids.GAS, 64000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L97](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineGasFlare.java#L97): `return (energyQuanta * i) / maxPower;`
- [L223](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineGasFlare.java#L223): `tank.setFill(tank.getFill() - eject);`
- [L232](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineGasFlare.java#L232): `tank.setFill(tank.getFill() - eject);`
- [L238](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineGasFlare.java#L238): `this.setStoredEnergyQuanta(energyQuanta + powerProd);`

### MachineHydrotreater

Source: [oil/TileEntityMachineHydrotreater.java](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineHydrotreater.java). Player container name: **Hydrotreater**. Persistent tile registry: `tileentity_hydrotreater`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineHydrotreater.java#L39) | `public static final long maxPower = 1_000_000;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineHydrotreater.java#L58) | `this.tanks[0] = new FluidTank(Fluids.OIL, 64_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineHydrotreater.java#L59) | `this.tanks[1] = new FluidTank(Fluids.HYDROGEN, 64_000).withPressure(1);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineHydrotreater.java#L60) | `this.tanks[2] = new FluidTank(Fluids.OIL_DS, 24_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineHydrotreater.java#L61) | `this.tanks[3] = new FluidTank(Fluids.SOURGAS, 24_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L59](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineHydrotreater.java#L59): `this.tanks[1] = new FluidTank(Fluids.HYDROGEN, 64_000).withPressure(1);`
- [L163](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineHydrotreater.java#L163): `tanks[1].withPressure(cachedRecipe.getX().pressure).setTankType(cachedRecipe.getX().type);`
- [L182](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineHydrotreater.java#L182): `tanks[0].setFill(tanks[0].getFill() - 100);`
- [L183](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineHydrotreater.java#L183): `tanks[1].setFill(tanks[1].getFill() - cachedRecipe.getX().fill);`
- [L184](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineHydrotreater.java#L184): `tanks[2].setFill(tanks[2].getFill() + cachedRecipe.getY().fill);`
- [L185](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineHydrotreater.java#L185): `tanks[3].setFill(tanks[3].getFill() + cachedRecipe.getZ().fill);`
- [L186](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineHydrotreater.java#L186): `this.setStoredEnergyQuanta(energyQuanta - 20_000L);`

### MachineLiquefactor

Source: [oil/TileEntityMachineLiquefactor.java](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineLiquefactor.java). Player container name: **Liquefactor**. Persistent tile registry: `tileentity_liquefactor`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineLiquefactor.java#L46) | `public static final long maxPower = 100000;` |
| [usageBase](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineLiquefactor.java#L47) | `public static final int usageBase = 500;` |
| [processTimeBase](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineLiquefactor.java#L50) | `public static final int processTimeBase = 100;` |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineLiquefactor.java#L54) | `private static final int TASK_ACCOUNTING = 1;` |
| [operatingPowerWatts](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineLiquefactor.java#L64) | `private long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(usageBase);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineLiquefactor.java#L77) | `tank = new FluidTank(Fluids.NONE, 24_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L141](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineLiquefactor.java#L141): `long available = cost <= 0L ? processTime - progress : energyQuanta / cost;`
- [L144](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineLiquefactor.java#L144): `this.setRuntimePower(energyQuanta - steps * cost);`
- [L145](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineLiquefactor.java#L145): `progress += (int) steps;`
- [L245](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineLiquefactor.java#L245): `long poweredTicks = cost <= 0L ? Long.MAX_VALUE : energyQuanta / cost + 1L;`
- [L274](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineLiquefactor.java#L274): `tank.setFill(tank.getFill() + cachedRecipe.fill);`
- [L331](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineLiquefactor.java#L331): `this.setStoredEnergyQuanta(this.energyQuanta - usage);`
- [L339](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineLiquefactor.java#L339): `tank.setFill(tank.getFill() + out.fill);`

### MachineOilWell

Source: [oil/TileEntityMachineOilWell.java](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java). Player container name: **Oil Derrick**. Persistent tile registry: `tileentity_derrick`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L35) | `protected static int maxPower = 100_000;` |
| [consumption](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L36) | `protected static int consumption = 100;` |
| [delay](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L37) | `protected static int delay = 50;` |
| [gasPerDepositMax](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L41) | `protected static int gasPerDepositMax = 500;` |
| [gasPerBedrockDepositMax](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L47) | `protected static int gasPerBedrockDepositMax = 5;` |
| [petgasPerDepositMax](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L53) | `protected static int petgasPerDepositMax = 100;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L125](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L125): `this.tanks[0].setFill(this.tanks[0].getFill() + oilPerDunaDeposit);`
- [L126](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L126): `if(this.tanks[0].getFill() > this.tanks[0].getMaxFill()) this.tanks[0].setFill(tanks[0].getMaxFill());`
- [L127](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L127): `this.tanks[1].setFill(this.tanks[1].getFill() + (gasPerDepositMin + worldObj.rand.nextInt((gasPerDepositMax - gasPerDepositMin + 1))));`
- [L128](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L128): `if(this.tanks[1].getFill() > this.tanks[1].getMaxFill()) this.tanks[1].setFill(tanks[1].getMaxFill());`
- [L134](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L134): `this.tanks[0].setFill(this.tanks[0].getFill() + oilPerDeposit);`
- [L135](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L135): `if(this.tanks[0].getFill() > this.tanks[0].getMaxFill()) this.tanks[0].setFill(tanks[0].getMaxFill());`
- [L136](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L136): `this.tanks[1].setFill(this.tanks[1].getFill() + (gasPerDepositMin + worldObj.rand.nextInt((gasPerDepositMax - gasPerDepositMin + 1))));`
- [L137](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L137): `if(this.tanks[1].getFill() > this.tanks[1].getMaxFill()) this.tanks[1].setFill(tanks[1].getMaxFill());`
- [L154](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L154): `tanks[0].setFill(tanks[0].getFill() + gasPerDeposit);`
- [L155](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L155): `if(tanks[0].getFill() > tanks[0].getMaxFill()) tanks[0].setFill(tanks[0].getMaxFill());`
- [L156](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L156): `tanks[1].setFill(tanks[1].getFill() + (petgasPerDepositMin + worldObj.rand.nextInt((petgasPerDepositMax - petgasPerDepositMin + 1))));`
- [L157](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L157): `if(tanks[1].getFill() > tanks[1].getMaxFill()) tanks[1].setFill(tanks[1].getMaxFill());`
- [L164](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L164): `this.tanks[0].setFill(this.tanks[0].getFill() + oil);`
- [L165](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L165): `if(this.tanks[0].getFill() > this.tanks[0].getMaxFill()) this.tanks[0].setFill(tanks[0].getMaxFill());`
- [L166](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L166): `this.tanks[1].setFill(this.tanks[1].getFill() + gas);`
- [L167](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineOilWell.java#L167): `if(this.tanks[1].getFill() > this.tanks[1].getMaxFill()) this.tanks[1].setFill(tanks[1].getMaxFill());`

### MachinePumpjack

Source: [oil/TileEntityMachinePumpjack.java](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java). Player container name: **Pumpjack**. Persistent tile registry: `tileentity_machine_pumpjack`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L37) | `protected static int maxPower = 250_000;` |
| [consumption](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L38) | `protected static int consumption = 200;` |
| [delay](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L39) | `protected static int delay = 25;` |
| [gasPerDepositMax](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L43) | `protected static int gasPerDepositMax = 250;` |
| [gasPerBedrockDepositMax](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L49) | `protected static int gasPerBedrockDepositMax = 9;` |
| [petgasPerDepositMax](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L54) | `protected static int petgasPerDepositMax = 50;` |
| [speed](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L58) | `public float speed = 0;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L158](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L158): `this.tanks[0].setFill(this.tanks[0].getFill() + oilPerDunaDeposit);`
- [L159](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L159): `if(this.tanks[0].getFill() > this.tanks[0].getMaxFill()) this.tanks[0].setFill(tanks[0].getMaxFill());`
- [L160](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L160): `this.tanks[1].setFill(this.tanks[1].getFill() + (gasPerDepositMin + worldObj.rand.nextInt((gasPerDepositMax - gasPerDepositMin + 1))));`
- [L161](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L161): `if(this.tanks[1].getFill() > this.tanks[1].getMaxFill()) this.tanks[1].setFill(tanks[1].getMaxFill());`
- [L167](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L167): `this.tanks[0].setFill(this.tanks[0].getFill() + oilPerDeposit);`
- [L168](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L168): `if(this.tanks[0].getFill() > this.tanks[0].getMaxFill()) this.tanks[0].setFill(tanks[0].getMaxFill());`
- [L169](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L169): `this.tanks[1].setFill(this.tanks[1].getFill() + (gasPerDepositMin + worldObj.rand.nextInt((gasPerDepositMax - gasPerDepositMin + 1))));`
- [L170](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L170): `if(this.tanks[1].getFill() > this.tanks[1].getMaxFill()) this.tanks[1].setFill(tanks[1].getMaxFill());`
- [L187](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L187): `tanks[0].setFill(tanks[0].getFill() + gasPerDeposit);`
- [L188](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L188): `if(tanks[0].getFill() > tanks[0].getMaxFill()) tanks[0].setFill(tanks[0].getMaxFill());`
- [L189](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L189): `tanks[1].setFill(tanks[1].getFill() + (petgasPerDepositMin + worldObj.rand.nextInt((petgasPerDepositMax - petgasPerDepositMin + 1))));`
- [L190](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L190): `if(tanks[1].getFill() > tanks[1].getMaxFill()) tanks[1].setFill(tanks[1].getMaxFill());`
- [L197](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L197): `this.tanks[0].setFill(this.tanks[0].getFill() + oil);`
- [L198](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L198): `if(this.tanks[0].getFill() > this.tanks[0].getMaxFill()) this.tanks[0].setFill(tanks[0].getMaxFill());`
- [L199](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L199): `this.tanks[1].setFill(this.tanks[1].getFill() + gas);`
- [L200](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePumpjack.java#L200): `if(this.tanks[1].getFill() > this.tanks[1].getMaxFill()) this.tanks[1].setFill(tanks[1].getMaxFill());`

### MachinePyroOven

Source: [oil/TileEntityMachinePyroOven.java](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java). Player container name: **Pyrolysis Oven**. Persistent tile registry: `tileentity_pyrooven`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L49) | `private static final int TASK_ACCOUNTING = 1;` |
| [TASK_EFFECT](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L50) | `private static final int TASK_EFFECT = 2;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L73) | `public static final long maxPower = 10_000_000;` |
| [consumption](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L77) | `public static int consumption = 10_000;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L89) | `tanks[0] = new FluidTank(Fluids.NONE, 24_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L90) | `tanks[1] = new FluidTank(Fluids.NONE, 24_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L189](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L189): `int cost = this.getConsumption(runtimeSpeed + runtimeOverdrive * 2, runtimePowerSaving);`
- [L191](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L191): `long poweredTicks = energyQuanta / cost + 1L;`
- [L259](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L259): `int cost = this.getConsumption(runtimeSpeed + runtimeOverdrive * 2, runtimePowerSaving);`
- [L261](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L261): `long steps = Math.min(remaining, energyQuanta / cost);`
- [L266](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L266): `progress += rate;`
- [L272](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L272): `try { this.setStoredEnergyQuanta(energyQuanta - cost * used); }`
- [L359](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L359): `public static int getConsumption(int speed, int powerSaving) {`
- [L404](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L404): `if(energyQuanta < this.getConsumption(speed + overdrive * 2, powerSaving)) return false;`
- [L431](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L431): `tanks[1].setFill(tanks[1].getFill() + recipe.outputFluid.fill);`
- [L437](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L437): `tanks[0].setFill(tanks[0].getFill() - recipe.inputFluid.fill);`
- [L511](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L511): `tank.setFill(tank.getFill() + fluidAmount);`
- [L515](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachinePyroOven.java#L515): `tank.setFill(tank.getMaxFill());`

### MachineRefinery

Source: [oil/TileEntityMachineRefinery.java](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java). Player container name: **Oil Refinery**. Persistent tile registry: `tileentity_refinery`.

| Field/boundary | Exact current source |
| --- | --- |
| [energyQuanta](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L52) | `public long energyQuanta = 0;` |
| [maxSulfur](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L54) | `public static final int maxSulfur = 100;` |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L55) | `public static final long maxPower = 1000;` |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L57) | `private static final int TASK_ACCOUNTING = 1;` |
| [lastAccountingTick](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L66) | `private long lastAccountingTick = Long.MIN_VALUE;` |
| [operatingPowerWatts](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L70) | `private long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(5L);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L91) | `tanks[0] = new FluidTank(Fluids.HOTOIL, 64_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L92) | `tanks[1] = new FluidTank(Fluids.HEAVYOIL, 24_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L93) | `tanks[2] = new FluidTank(Fluids.NAPHTHA, 24_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L94) | `tanks[3] = new FluidTank(Fluids.LIGHTOIL, 24_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L95) | `tanks[4] = new FluidTank(Fluids.PETROLEUM, 24_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L376](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L376): `long steps = Math.min(tanks[0].getFill() / 100L, energyQuanta / cost);`
- [L393](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L393): `long steps = Math.min(elapsed, Math.min(tanks[0].getFill() / 100L, energyQuanta / cost));`
- [L403](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L403): `tanks[0].setFill(tanks[0].getFill() - (int) (steps * 100L));`
- [L404](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L404): `for(int i = 0; i < 4; i++) tanks[i + 1].setFill(tanks[i + 1].getFill() + (int) (steps * cachedFractions[i].fill));`
- [L405](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L405): `this.setStoredEnergyQuanta(energyQuanta - steps * cost);`
- [L458](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L458): `tanks[i].setFill(Math.max(tanks[i].getFill() - 10, 0));`
- [L519](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineRefinery.java#L519): `return (energyQuanta * i) / maxPower;`

### MachineShredderLarge

Source: [oil/TileEntityMachineShredderLarge.java](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineShredderLarge.java). Persistent registration inherited/constructed; class identity is the inspection anchor.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineShredderLarge.java#L42) | `public static final long maxPower = 100000;` |
| [usageBase](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineShredderLarge.java#L43) | `public static final int usageBase = 500;` |
| [processTimeBase](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineShredderLarge.java#L46) | `public static final int processTimeBase = 200;` |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineShredderLarge.java#L50) | `private static final int TASK_ACCOUNTING = 1;` |
| [operatingPowerWatts](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineShredderLarge.java#L59) | `private long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(usageBase);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineShredderLarge.java#L72) | `tank = new FluidTank(Fluids.BLOOD, 24000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L136](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineShredderLarge.java#L136): `long available = cost <= 0L ? processTime - progress : energyQuanta / cost;`
- [L139](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineShredderLarge.java#L139): `this.setRuntimePower(energyQuanta - steps * cost);`
- [L140](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineShredderLarge.java#L140): `progress += (int) steps;`
- [L237](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineShredderLarge.java#L237): `long poweredTicks = cost <= 0L ? Long.MAX_VALUE : energyQuanta / cost + 1L;`
- [L266](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineShredderLarge.java#L266): `tank.setFill(tank.getFill() + cachedRecipe.fill);`
- [L331](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineShredderLarge.java#L331): `this.setStoredEnergyQuanta(this.energyQuanta - usage);`
- [L339](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineShredderLarge.java#L339): `tank.setFill(tank.getFill() + out.fill);`
- [L399](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineShredderLarge.java#L399): `return (energyQuanta * i) / maxPower;`

### MachineSolidifier

Source: [oil/TileEntityMachineSolidifier.java](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineSolidifier.java). Player container name: **Solidifier**. Persistent tile registry: `tileentity_solidifier`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineSolidifier.java#L45) | `public static final long maxPower = 100000;` |
| [usageBase](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineSolidifier.java#L46) | `public static final int usageBase = 500;` |
| [processTimeBase](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineSolidifier.java#L49) | `public static final int processTimeBase = 100;` |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineSolidifier.java#L53) | `private static final int TASK_ACCOUNTING = 1;` |
| [operatingPowerWatts](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineSolidifier.java#L63) | `private long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(usageBase);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineSolidifier.java#L74) | `tank = new FluidTank(Fluids.NONE, 24_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L138](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineSolidifier.java#L138): `long available = cost <= 0L ? processTime - progress : energyQuanta / cost;`
- [L141](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineSolidifier.java#L141): `this.setRuntimePower(energyQuanta - steps * cost);`
- [L142](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineSolidifier.java#L142): `progress += (int) steps;`
- [L236](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineSolidifier.java#L236): `long poweredTicks = cost <= 0L ? Long.MAX_VALUE : energyQuanta / cost + 1L;`
- [L263](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineSolidifier.java#L263): `try { tank.setFill(tank.getFill() - cachedRecipe.getKey()); } finally { this.endMachineFluidMutation(); }`
- [L332](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineSolidifier.java#L332): `this.setStoredEnergyQuanta(this.energyQuanta - usage);`
- [L341](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineSolidifier.java#L341): `tank.setFill(tank.getFill() - req);`

### MachineVacuumDistill

Source: [oil/TileEntityMachineVacuumDistill.java](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineVacuumDistill.java). Player container name: **Vacuum Refinery**. Persistent tile registry: `tileentity_vacuuum_distill`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineVacuumDistill.java#L39) | `public static final long maxPower = 1_000_000;` |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineVacuumDistill.java#L56) | `private static final int TASK_ACCOUNTING = 1;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineVacuumDistill.java#L67) | `this.tanks[0] = new FluidTank(Fluids.OIL, 64_000).withPressure(2);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineVacuumDistill.java#L68) | `this.tanks[1] = new FluidTank(Fluids.HEAVYOIL_VACUUM, 24_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineVacuumDistill.java#L69) | `this.tanks[2] = new FluidTank(Fluids.REFORMATE, 24_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineVacuumDistill.java#L70) | `this.tanks[3] = new FluidTank(Fluids.LIGHTOIL_VACUUM, 24_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineVacuumDistill.java#L71) | `this.tanks[4] = new FluidTank(Fluids.SOURGAS, 24_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L67](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineVacuumDistill.java#L67): `this.tanks[0] = new FluidTank(Fluids.OIL, 64_000).withPressure(2);`
- [L164](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineVacuumDistill.java#L164): `this.setStoredEnergyQuanta(energyQuanta - batches * 10_000L);`
- [L165](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineVacuumDistill.java#L165): `tanks[0].setFill(tanks[0].getFill() - (int) (batches * 100L));`
- [L166](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineVacuumDistill.java#L166): `tanks[1].setFill(tanks[1].getFill() + (int) (batches * cachedRecipe.getW().fill));`
- [L167](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineVacuumDistill.java#L167): `tanks[2].setFill(tanks[2].getFill() + (int) (batches * cachedRecipe.getX().fill));`
- [L168](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineVacuumDistill.java#L168): `tanks[3].setFill(tanks[3].getFill() + (int) (batches * cachedRecipe.getY().fill));`
- [L169](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineVacuumDistill.java#L169): `tanks[4].setFill(tanks[4].getFill() + (int) (batches * cachedRecipe.getZ().fill));`
- [L289](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityMachineVacuumDistill.java#L289): `long batches = Math.min(limit, Math.min(tanks[0].getFill() / 100L, energyQuanta / 10_000L));`

### OilDrillBase

Source: [oil/TileEntityOilDrillBase.java](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityOilDrillBase.java). Persistent registration inherited/constructed; class identity is the inspection anchor.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityOilDrillBase.java#L54) | `tanks[0] = new FluidTank(Fluids.OIL, 64_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityOilDrillBase.java#L55) | `tanks[1] = new FluidTank(Fluids.GAS, 64_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L188](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityOilDrillBase.java#L188): `tanks[1].setFill(tanks[1].getFill() - toBurn);`
- [L189](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityOilDrillBase.java#L189): `this.setStoredEnergyQuanta(this.energyQuanta + toBurn * 5);`
- [L204](../src/main/java/com/hbm/tileentity/machine/oil/TileEntityOilDrillBase.java#L204): `this.setStoredEnergyQuanta(this.energyQuanta - this.getPowerReqEff());`

### PileBreedingFuel

Source: [pile/TileEntityPileBreedingFuel.java](../src/main/java/com/hbm/tileentity/machine/pile/TileEntityPileBreedingFuel.java). Persistent tile registry: `tileentity_pile_breedingfuel`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxProgress](../src/main/java/com/hbm/tileentity/machine/pile/TileEntityPileBreedingFuel.java#L17) | `public static final int maxProgress = GeneralConfig.enable528 ? 120000 : 80000;` |
| [maxHeat](../src/main/java/com/hbm/tileentity/machine/pile/TileEntityPileBreedingFuel.java#L21) | `public static final double maxHeat = 500D;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L69](../src/main/java/com/hbm/tileentity/machine/pile/TileEntityPileBreedingFuel.java#L69): `this.progress += absorbed;`
- [L71](../src/main/java/com/hbm/tileentity/machine/pile/TileEntityPileBreedingFuel.java#L71): `heat += absorbed * 0.015D;`

### PileFuel

Source: [pile/TileEntityPileFuel.java](../src/main/java/com/hbm/tileentity/machine/pile/TileEntityPileFuel.java). Persistent tile registry: `tileentity_pile_fuel`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxHeat](../src/main/java/com/hbm/tileentity/machine/pile/TileEntityPileFuel.java#L20) | `public static final int maxHeat = 1000;` |
| [maxProgress](../src/main/java/com/hbm/tileentity/machine/pile/TileEntityPileFuel.java#L24) | `public static final int maxProgress = GeneralConfig.enable528 ? 75000 : 50000;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L75](../src/main/java/com/hbm/tileentity/machine/pile/TileEntityPileFuel.java#L75): `this.heat -= (this.getBlockMetadata() & 4) == 4 ? heat * 0.065 : heat * 0.05;`
- [L92](../src/main/java/com/hbm/tileentity/machine/pile/TileEntityPileFuel.java#L92): `this.progress += reaction;`
- [L102](../src/main/java/com/hbm/tileentity/machine/pile/TileEntityPileFuel.java#L102): `this.heat += Math.max(1,`
- [L112](../src/main/java/com/hbm/tileentity/machine/pile/TileEntityPileFuel.java#L112): `this.heat += Math.max(1, reaction / 6);`

### RBMKBase

Source: [rbmk/TileEntityRBMKBase.java](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBase.java). Persistent registration inherited/constructed; class identity is the inspection anchor.

| Field/boundary | Exact current source |
| --- | --- |
| [maxWater](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBase.java#L60) | `public static final int maxWater = 16000;` |
| [maxSteam](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBase.java#L62) | `public static final int maxSteam = 16000;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L208](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBase.java#L208): `this.heat -= processedWater * heatConsumption;`
- [L268](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBase.java#L268): `column.heat += (targetHeat - column.heat) * stepSize;`
- [L291](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBase.java#L291): `this.heat += this.passiveCooling() * 2;`
- [L295](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBase.java#L295): `this.heat -= this.passiveCooling();`

### RBMKBoiler

Source: [rbmk/TileEntityRBMKBoiler.java](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBoiler.java). Player container name: **RBMK Steam Channel**. Persistent tile registry: `tileentity_rbmk_boiler`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBoiler.java#L45) | `feed = new FluidTank(Fluids.LIGHT_WATER, 10000).migrateFrom(Fluids.WATER);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBoiler.java#L46) | `steam = new FluidTank(Fluids.STEAM, 1000000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L101](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBoiler.java#L101): `feed.setFill(feed.getFill() - waterUsed);`
- [L102](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBoiler.java#L102): `steam.setFill(steam.getFill() + steamProduced);`
- [L105](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBoiler.java#L105): `steam.setFill(steam.getMaxFill());`
- [L107](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBoiler.java#L107): `this.heat -= waterUsed * HEAT_PER_MB_WATER;`
- [L193](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBoiler.java#L193): `if(type == Fluids.STEAM) {			steam.setTankType(Fluids.HOTSTEAM);			steam.setFill(steam.getFill() / 10); }`
- [L194](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBoiler.java#L194): `if(type == Fluids.HOTSTEAM) {		steam.setTankType(Fluids.SUPERHOTSTEAM);	steam.setFill(steam.getFill() / 10); }`
- [L195](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBoiler.java#L195): `if(type == Fluids.SUPERHOTSTEAM) {	steam.setTankType(Fluids.ULTRAHOTSTEAM);	steam.setFill(steam.getFill() / 10); }`
- [L196](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBoiler.java#L196): `if(type == Fluids.ULTRAHOTSTEAM) {	steam.setTankType(Fluids.STEAM);			steam.setFill(Math.min(steam.getFill() * 1000, steam.getMaxFill())); }`

### RBMKBurner

Source: [rbmk/TileEntityRBMKBurner.java](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBurner.java). Persistent tile registry: `tileentity_rbmk_burner`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBurner.java#L27) | `this.tank = new FluidTank(Fluids.GASOLINE, 8000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L86](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBurner.java#L86): `tank.setFill(tank.getFill() - heating );`
- [L89](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKBurner.java#L89): `this.heat += powerProd;`

### RBMKCooler

Source: [rbmk/TileEntityRBMKCooler.java](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKCooler.java). Persistent tile registry: `tileentity_rbmk_cooler`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKCooler.java#L30) | `this.tank = new FluidTank(Fluids.CRYOGEL, 8_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L81](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKCooler.java#L81): `this.heat -= cooling;`
- [L82](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKCooler.java#L82): `this.tank.setFill(this.tank.getFill() - cooling);`

### RBMKHeater

Source: [rbmk/TileEntityRBMKHeater.java](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKHeater.java). Player container name: **RBMK Fluid Heater**. Persistent tile registry: `tileentity_rbmk_heater`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKHeater.java#L37) | `this.feed = new FluidTank(Fluids.COOLANT, 16_000);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKHeater.java#L38) | `this.steam = new FluidTank(Fluids.COOLANT_HOT, 16_000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L68](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKHeater.java#L68): `HeatingStep step = trait.getFirstStep();`
- [L80](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKHeater.java#L80): `feed.setFill(feed.getFill() - step.amountReq * ops);`
- [L81](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKHeater.java#L81): `steam.setFill(steam.getFill() + step.amountProduced * ops);`
- [L82](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKHeater.java#L82): `this.heat -= (step.heatReq * ops / TU_PER_DEGREE) * trait.getEfficiency(HeatingType.HEATEXCHANGER);`

### RBMKInlet

Source: [rbmk/TileEntityRBMKInlet.java](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKInlet.java). Persistent tile registry: `tileentity_rbmk_inlet`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKInlet.java#L23) | `water = new FluidTank(Fluids.LIGHT_WATER, 32000).migrateFrom(Fluids.WATER);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L67](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKInlet.java#L67): `water.setFill(water.getFill() - amount);`

### RBMKOutgasser

Source: [rbmk/TileEntityRBMKOutgasser.java](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKOutgasser.java). Player container name: **RBMK Irradiation Channel**. Persistent tile registry: `tileentity_rbmk_outgasser`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKOutgasser.java#L40) | `gas = new FluidTank(Fluids.TRITIUM, 64000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L106](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKOutgasser.java#L106): `progress += flux * RBMKDials.getOutgasserMod(worldObj);`
- [L149](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKOutgasser.java#L149): `gas.setFill(gas.getFill() + output.getValue().fill);`

### RBMKOutlet

Source: [rbmk/TileEntityRBMKOutlet.java](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKOutlet.java). Persistent tile registry: `tileentity_rbmk_outlet`.

| Field/boundary | Exact current source |
| --- | --- |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKOutlet.java#L23) | `steam = new FluidTank(Fluids.SUPERHOTSTEAM, 32000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L71](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKOutlet.java#L71): `steam.setFill(steam.getFill() + amount);`

### Barrel

Source: [storage/TileEntityBarrel.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityBarrel.java). Player container name: **Barrel**. Persistent tile registry: `tileentity_fluid_barrel`.

| Field/boundary | Exact current source |
| --- | --- |
| [age](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityBarrel.java#L61) | `public int age = 0;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityBarrel.java#L69) | `tank = new FluidTank(Fluids.NONE, 0);` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityBarrel.java#L75) | `tank = new FluidTank(Fluids.NONE, capacity);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L104](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityBarrel.java#L104): `tank.setFill(tank.getFill() + (int) toTransfer);`
- [L162](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityBarrel.java#L162): `tank.setFill(transmitFluidFairly(worldObj, tank, this, tank.getFill(), this.mode == 0 || this.mode == 1, this.mode == 1 || this.mode == 2, getConPos()));`
- [L296](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityBarrel.java#L296): `barrel.tank.setFill(Math.min(barrel.tank.getMaxFill(), tank.getFill()));`
- [L305](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityBarrel.java#L305): `tank.setFill(tank.getFill() - 1);`

### CrateBase

Source: [storage/TileEntityCrateBase.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityCrateBase.java). Persistent registration inherited/constructed; class identity is the inspection anchor.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### CrateDesh

Source: [storage/TileEntityCrateDesh.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityCrateDesh.java). Persistent tile registry: `tileentity_crate_desh`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### CrateIron

Source: [storage/TileEntityCrateIron.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityCrateIron.java). Persistent tile registry: `tileentity_crate_iron`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### CrateSteel

Source: [storage/TileEntityCrateSteel.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityCrateSteel.java). Persistent tile registry: `tileentity_crate_steel`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### CrateTemplate

Source: [storage/TileEntityCrateTemplate.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityCrateTemplate.java). Persistent tile registry: `tileentity_crate_template`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### CrateTungsten

Source: [storage/TileEntityCrateTungsten.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityCrateTungsten.java). Persistent tile registry: `tileentity_crate_hot`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### FileCabinet

Source: [storage/TileEntityFileCabinet.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityFileCabinet.java). Persistent tile registry: `tileentity_file_cabinet`.

| Field/boundary | Exact current source |
| --- | --- |
| [timer](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityFileCabinet.java#L23) | `private int timer = 0;` |

### MachineBAT9000

Source: [storage/TileEntityMachineBAT9000.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineBAT9000.java). Player container name: **Big-Ass Tank 9000**. Persistent tile registry: `tileentity_bat9000`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### MachineBattery

Source: [storage/TileEntityMachineBattery.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineBattery.java). Player container name: **Energy Storage**. Persistent tile registry: `tileentity_battery`.

| Field/boundary | Exact current source |
| --- | --- |
| [TASK_ACCOUNTING](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineBattery.java#L43) | `private static final int TASK_ACCOUNTING = 1;` |
| [energyQuanta](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineBattery.java#L49) | `public long energyQuanta = 0;` |
| [prevPowerState](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineBattery.java#L50) | `public long prevPowerState = 0;` |
| [mode_output](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineBattery.java#L62) | `public static final int mode_output = 2;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L162](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineBattery.java#L162): `return (energyQuanta * i) / this.getEnergyCapacityQuanta();`
- [L167](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineBattery.java#L167): `double frac = (double) this.energyQuanta / (double) this.getEnergyCapacityQuanta() * 15D;`
- [L257](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineBattery.java#L257): `long avg = (energyQuanta + prevPower) / 2;`

### MachineFENSU

Source: [storage/TileEntityMachineFENSU.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineFENSU.java). Persistent tile registry: `tileentity_fensu`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxTransfer](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineFENSU.java#L20) | `public static final long maxTransfer = 10_000_000_000_000_000L;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L68](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineFENSU.java#L68): `long avg = (energyQuanta / 2 + prevPower / 2);`
- [L107](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineFENSU.java#L107): `double powerScaled = (double)energyQuanta / (double)getEnergyCapacityQuanta();`
- [L117](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineFENSU.java#L117): `return (float) Math.pow(Math.log(energyQuanta * 0.75 + 1) * 0.05F, 5);`
- [L138](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineFENSU.java#L138): `overshoot += energyQuanta - maxTransfer;`
- [L147](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineFENSU.java#L147): `overshoot += energyQuanta - freespace;`

### MachineFluidTank

Source: [storage/TileEntityMachineFluidTank.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineFluidTank.java). Player container name: **Tank**. Persistent tile registry: `tileentity_fluid_tank`.

| Field/boundary | Exact current source |
| --- | --- |
| [age](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineFluidTank.java#L68) | `public int age = 0;` |
| [Fluid buffer](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineFluidTank.java#L72) | `tank = new FluidTank(Fluids.NONE, 256000);` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L150](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineFluidTank.java#L150): `tank.setFill(TileEntityBarrel.transmitFluidFairly(worldObj, tank, this, tank.getFill(), this.mode == 0 || this.mode == 1, this.mode == 1 || this.mode == 2, getConPos()));`
- [L170](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineFluidTank.java#L170): `this.tank.setFill(0);`
- [L245](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineFluidTank.java#L245): `this.tank.setFill(Math.max(0, this.tank.getFill() - amount));`
- [L379](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineFluidTank.java#L379): `tank.setFill(tank.getFill() + (int) toTransfer);`

### MachineOrbus

Source: [storage/TileEntityMachineOrbus.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineOrbus.java). Player container name: **Heavy Antimatter Storage**. Persistent tile registry: `tileentity_orbus`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### MachinePuF6Tank

Source: [storage/TileEntityMachinePuF6Tank.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachinePuF6Tank.java). Persistent tile registry: `tileentity_puf6_tank`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### MachineUF6Tank

Source: [storage/TileEntityMachineUF6Tank.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMachineUF6Tank.java). Persistent tile registry: `tileentity_uf6_tank`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### MassStorage

Source: [storage/TileEntityMassStorage.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMassStorage.java). Persistent tile registry: `tileentity_mass_storage`.

| Field/boundary | Exact current source |
| --- | --- |
| [output](../src/main/java/com/hbm/tileentity/machine/storage/TileEntityMassStorage.java#L21) | `public boolean output = false;` |

### Safe

Source: [storage/TileEntitySafe.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntitySafe.java). Persistent tile registry: `tileentity_safe`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### SoyuzCapsule

Source: [storage/TileEntitySoyuzCapsule.java](../src/main/java/com/hbm/tileentity/machine/storage/TileEntitySoyuzCapsule.java). Player container name: **Cargo Landing Capsule**. Persistent tile registry: `tileentity_soyuz_capsule`.

| Field/boundary | Exact current source |
| --- | --- |
| Inherited/dynamic | No local initialized physical-rate field; inspect the linked base/recipe/operation. |

### ConverterHeRf

Source: [network/TileEntityConverterHeRf.java](../src/main/java/com/hbm/tileentity/network/TileEntityConverterHeRf.java). Persistent tile registry: `tileentity_converter_herf`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/network/TileEntityConverterHeRf.java#L25) | `public final long maxPower = 5_000_000;` |
| [inputQuantaPerBatch](../src/main/java/com/hbm/tileentity/network/TileEntityConverterHeRf.java#L26) | `public static long inputQuantaPerBatch = 5;` |
| [outputRfPerBatch](../src/main/java/com/hbm/tileentity/network/TileEntityConverterHeRf.java#L27) | `public static long outputRfPerBatch = 1;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L40](../src/main/java/com/hbm/tileentity/network/TileEntityConverterHeRf.java#L40): `long batches = Math.min(this.energyQuanta / inputQuanta, (storage.getMaxEnergyStored() - storage.getEnergyStored()) / outputRf);`
- [L42](../src/main/java/com/hbm/tileentity/network/TileEntityConverterHeRf.java#L42): `this.setStoredEnergyQuanta(this.energyQuanta - batches * inputQuanta);`
- [L44](../src/main/java/com/hbm/tileentity/network/TileEntityConverterHeRf.java#L44): `if(energyQuanta > 0) this.setStoredEnergyQuanta((long) (this.energyQuanta * (1D - inputDecay)));`

### ConverterRfHe

Source: [network/TileEntityConverterRfHe.java](../src/main/java/com/hbm/tileentity/network/TileEntityConverterRfHe.java). Persistent tile registry: `tileentity_converter_rfhe`.

| Field/boundary | Exact current source |
| --- | --- |
| [maxPower](../src/main/java/com/hbm/tileentity/network/TileEntityConverterRfHe.java#L20) | `public final long maxPower = 5_000_000;` |
| [outputQuantaPerBatch](../src/main/java/com/hbm/tileentity/network/TileEntityConverterRfHe.java#L22) | `public static long outputQuantaPerBatch = 5;` |

Selected processing/transfer expressions (line links keep the owning condition/callback inspectable):

- [L39](../src/main/java/com/hbm/tileentity/network/TileEntityConverterRfHe.java#L39): `this.setStoredEnergyQuanta(this.energyQuanta + batches * outputQuanta);`

## RBMK fuel-column supplement

The active fuel owners are RBMKRod and RBMKRodReaSim (not classes named RBMKFuel). These source boundaries are included in the census; temperature/flux parameters are not SI fuel-energy ratings.

### RBMKRod

Source: [TileEntityRBMKRod](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKRod.java); persistent names `tileentity_rbmk_rod`.

| Parameter/operation | Exact source |
| --- | --- |
| [L102](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKRod.java#L102) | `this.heat += rod.provideHeat(worldObj, slots[0], heat, 1.0D);` |
| [L165](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKRod.java#L165) | `public static final ForgeDirection[] fluxDirs = new ForgeDirection[] {` |
| [L436](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKRod.java#L436) | `public Object[] getDepletion(Context context, Arguments args) {` |
| [L525](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKRod.java#L525) | `data.setDouble(CompatEnergyControl.D_DEPLETION_PERCENT, ((1.0D - ItemRBMKRod.getEnrichment(slots[0])) * 100_000.0D) / 1_000.0D);` |

### RBMKRodReaSim

Source: [TileEntityRBMKRodReaSim](../src/main/java/com/hbm/tileentity/machine/rbmk/TileEntityRBMKRodReaSim.java); persistent names `tileentity_rbmk_rod_reasim`.

| Parameter/operation | Exact source |
| --- | --- |

## Recipe-owner map

This map is a source index, not proof that commented recipes register. Current stock/canonical balances and important batch amounts appear in the process inventory and physical ledger. Custom JSON replaces/appends defaults according to SerializableRecipe; loaded pack data remain a runtime/configuration gate.

| Owner | Serialization / entry points |
| --- | --- |
| [RefineryRecipes](../src/main/java/com/hbm/inventory/recipes/RefineryRecipes.java) | Built-in/non-JSON owner; registerRefinery |
| [FractionRecipes](../src/main/java/com/hbm/inventory/recipes/FractionRecipes.java) | `hbmFractions.json`; getRecipeObject, registerDefaults |
| [CrackingRecipes](../src/main/java/com/hbm/inventory/recipes/CrackingRecipes.java) | `hbmCracking.json`; getRecipeObject, registerDefaults |
| [HydrotreatingRecipes](../src/main/java/com/hbm/inventory/recipes/HydrotreatingRecipes.java) | `hbmHydrotreating.json`; getOutput, getRecipeObject, getRecipes, registerDefaults |
| [ReformingRecipes](../src/main/java/com/hbm/inventory/recipes/ReformingRecipes.java) | `hbmReforming.json`; getOutput, getRecipeObject, getRecipes, registerDefaults |
| [AlkylationRecipes](../src/main/java/com/hbm/inventory/recipes/AlkylationRecipes.java) | `hbmAlkylation.json`; getOutput, getRecipeObject, getRecipes, registerDefaults |
| [CokerRecipes](../src/main/java/com/hbm/inventory/recipes/CokerRecipes.java) | `hbmCoker.json`; getOutput, getRecipeObject, getRecipes, registerDefaults |
| [PyroOvenRecipes](../src/main/java/com/hbm/inventory/recipes/PyroOvenRecipes.java) | `hbmPyrolysis.json`; getRecipeObject, getRecipes, registerDefaults |
| [LiquefactionRecipes](../src/main/java/com/hbm/inventory/recipes/LiquefactionRecipes.java) | `hbmLiquefactor.json`; getOutput, getRecipeObject, getRecipes, registerDefaults |
| [SolidificationRecipes](../src/main/java/com/hbm/inventory/recipes/SolidificationRecipes.java) | `hbmSolidifier.json`; getOutput, getRecipeObject, getRecipes, registerDefaults |
| [MixerRecipes](../src/main/java/com/hbm/inventory/recipes/MixerRecipes.java) | `hbmMixer.json`; getOutput, getRecipeObject, getRecipes, register, registerDefaults |
| [ChemplantRecipes](../src/main/java/com/hbm/inventory/recipes/ChemplantRecipes.java) | `hbmChemplant.json`; getRecipeObject, registerDefaults, registerOtherOil |
| [ElectrolyserFluidRecipes](../src/main/java/com/hbm/inventory/recipes/ElectrolyserFluidRecipes.java) | `hbmElectrolyzerFluid.json`; getRecipe, getRecipeObject, getRecipes, registerDefaults |
| [ElectrolyserMetalRecipes](../src/main/java/com/hbm/inventory/recipes/ElectrolyserMetalRecipes.java) | `hbmElectrolyzerMetal.json`; getRecipe, getRecipeObject, getRecipes, registerDefaults |
| [CompressorRecipes](../src/main/java/com/hbm/inventory/recipes/CompressorRecipes.java) | `hbmCompressor.json`; getRecipeObject, registerDefaults |
| [CryoRecipes](../src/main/java/com/hbm/inventory/recipes/CryoRecipes.java) | `hbmCryodistillator.json`; getOutput, getRecipeObject, registerDefaults |
| [AssemblerRecipes](../src/main/java/com/hbm/inventory/recipes/AssemblerRecipes.java) | `hbmAssembler.json`; getRecipeFromTempate, getRecipeObject, getRecipes, makeRecipe, registerDefaults |
| [ArcWelderRecipes](../src/main/java/com/hbm/inventory/recipes/ArcWelderRecipes.java) | `hbmArcWelder.json`; getRecipe, getRecipeObject, getRecipes, registerDefaults |
| [SolderingRecipes](../src/main/java/com/hbm/inventory/recipes/SolderingRecipes.java) | `hbmSoldering.json`; getRecipe, getRecipeObject, getRecipes, registerDefaults |
| [VacuumCircuitRecipes](../src/main/java/com/hbm/inventory/recipes/VacuumCircuitRecipes.java) | `hbmVacuumCircuit.json`; getRecipe, getRecipeObject, getRecipes, registerDefaults |
| [PressRecipes](../src/main/java/com/hbm/inventory/recipes/PressRecipes.java) | `hbmPress.json`; getOutput, getRecipeObject, makeRecipe, registerDefaults |
| [CrucibleRecipes](../src/main/java/com/hbm/inventory/recipes/CrucibleRecipes.java) | `hbmCrucible.json`; getRecipeObject, registerDefaults |
| [RotaryFurnaceRecipes](../src/main/java/com/hbm/inventory/recipes/RotaryFurnaceRecipes.java) | `hbmRotaryFurnace.json`; getRecipe, getRecipeObject, getRecipes, registerDefaults |
| [BlastFurnaceRecipes](../src/main/java/com/hbm/inventory/recipes/BlastFurnaceRecipes.java) | `hbmBlastFurnace.json`; getOutput, getRecipeObject, getRecipes, getRecipesForNEI, registerDefaults |
| [ArcFurnaceRecipes](../src/main/java/com/hbm/inventory/recipes/ArcFurnaceRecipes.java) | `hbmArcFurnace.json`; getOutput, getRecipeObject, registerDefaults |
| [ShredderRecipes](../src/main/java/com/hbm/inventory/recipes/ShredderRecipes.java) | `hbmShredder.json`; getRecipeObject, registerDefaults, registerPost |
| [CentrifugeRecipes](../src/main/java/com/hbm/inventory/recipes/CentrifugeRecipes.java) | `hbmCentrifuge.json`; getOutput, getRecipeObject, getRecipes, registerDefaults, registerPost |
| [GasCentrifugeRecipes](../src/main/java/com/hbm/inventory/recipes/GasCentrifugeRecipes.java) | Built-in/non-JSON owner; getOutput, register |
| [CrystallizerRecipes](../src/main/java/com/hbm/inventory/recipes/CrystallizerRecipes.java) | `hbmCrystallizer.json`; getOutput, getRecipeObject, getRecipes, registerDefaults, registerRecipe |
| [FusionRecipes](../src/main/java/com/hbm/inventory/recipes/FusionRecipes.java) | Built-in/non-JSON owner; getRecipes |
| [FuelPoolRecipes](../src/main/java/com/hbm/inventory/recipes/FuelPoolRecipes.java) | `hbmFuelpool.json`; getRecipeObject, registerDefaults |
| [OutgasserRecipes](../src/main/java/com/hbm/inventory/recipes/OutgasserRecipes.java) | `hbmIrradiation.json`; getOutput, getRecipeObject, getRecipes, registerDefaults |
| [CyclotronRecipes](../src/main/java/com/hbm/inventory/recipes/CyclotronRecipes.java) | `hbmCyclotron.json`; getOutput, getRecipeObject, getRecipes, registerDefaults |
| [ExposureChamberRecipes](../src/main/java/com/hbm/inventory/recipes/ExposureChamberRecipes.java) | `hbmExposureChamber.json`; getRecipe, getRecipeObject, getRecipes, registerDefaults |
| [SILEXRecipes](../src/main/java/com/hbm/inventory/recipes/SILEXRecipes.java) | Built-in/non-JSON owner; getOutput, getRecipes, register |
| [HadronRecipes](../src/main/java/com/hbm/inventory/recipes/HadronRecipes.java) | `hbmHadronCollider.json`; getOutput, getRecipeObject, getRecipes, registerDefaults |
| [RadiolysisRecipes](../src/main/java/com/hbm/inventory/recipes/RadiolysisRecipes.java) | Built-in/non-JSON owner; getRecipesForNEI, registerRadiolysis |

## Fluid identity census

All **225 distinct literal FluidType assignments** found in the active registry, using the last assignment where the same field is initialized again. Includes decorative/fictional/reagent states so a named coolant/phase alias cannot disappear from the investigation. Default temperature is verified against FluidType; this table is the current registration, **not** a physical certification. Custom/demo fields created from variable names are excluded. The canonical decision tables group real process substances; remaining fantasy or unassayed substances retain nominal quantities and are unreleased for physical balance.

| Registry field | Player-facing name | Current temperature °C | Phase/state tags |
| --- | --- | ---: | --- |
| [ACIDWASTE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L556) | Acid Waste | 300 | LIQUID,VISCOUS |
| [AIR](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L438) | Compressed Air | default20 | GASEOUS |
| [AMAT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L383) | Antimatter | default20 | GASEOUS |
| [AMMONIA](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L435) | Ammonia | default20 | GASEOUS |
| [ARGON](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L453) | Argon Gas | default20 | GASEOUS |
| [AROMATICS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L417) | Aromatic Hydrocarbons | default20 | LIQUID,VISCOUS |
| [ASCHRAB](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L384) | Antischrabidium | default20 | GASEOUS |
| [BALEFIRE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L391) | BF Rocket Fuel | 1500 | LIQUID,VISCOUS |
| [BIOFUEL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L377) | Biofuel | default20 | LIQUID |
| [BIOGAS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L376) | Biogas | default20 | GASEOUS |
| [BITTERN](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L580) | Bittern | 300 | LIQUID,VISCOUS |
| [BITUMEN](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L363) | Bitumen | default20 | LIQUID,VISCOUS |
| [BLOOD](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L433) | Blood | default20 | LIQUID,VISCOUS |
| [BLOODGAS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L437) | Blood Fuel | default20 | LIQUID |
| [BLOOD_HOT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L439) | Hot Blood | 666 | LIQUID,VISCOUS |
| [BORATED_WATER](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L593) | Borated Water | default20 | LIQUID |
| [BORATED_WATER_HOT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L595) | Hot Borated Water | 325 | LIQUID |
| [BRINE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L579) | Brine | default20 | unspecified |
| [BROMINE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L476) | Alkyl Bromide | default20 | LIQUID,VISCOUS |
| [CACL2](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L571) | Calcium Chloride | default20 | LIQUID |
| [CALCIUM_CHLORIDE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L507) | Calciumchloride Solution | default20 | LIQUID,VISCOUS,NOCON |
| [CALCIUM_SOLUTION](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L508) | Calcium Solution | default20 | LIQUID,NOCON |
| [CARBONDIOXIDE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L405) | Carbon Dioxide | default20 | GASEOUS |
| [CARBONDIOXIDE_NITROGEN_HELIUM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L584) | CO2/N2/He Gas Mix | default20 | GASEOUS |
| [CHLORINE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L446) | Chlorine Gas | default20 | GASEOUS |
| [CHLOROCALCITE_CLEANED](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L502) | Cleaned Chlorocalcite Solution | default20 | LIQUID,NOCON |
| [CHLOROCALCITE_MIX](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L501) | Mixed Chlorocalcite Solution | default20 | LIQUID,NOCON |
| [CHLOROCALCITE_SOLUTION](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L500) | Chlorocalcite Solution | default20 | LIQUID,NOCON |
| [CHLOROMETHANE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L469) | Chloromethane | default20 | GASEOUS |
| [CHOLESTEROL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L494) | Cholesterol Solution | default20 | LIQUID |
| [CMILK](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L517) | Condensed Milk | default20 | LIQUID |
| [COALCREOSOTE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L430) | Coal Tar Creosote | default20 | LIQUID,VISCOUS |
| [COALGAS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L397) | Coal Gasoline | default20 | LIQUID |
| [COALGAS_LEADED](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L424) | Leaded Coal Gasoline | default20 | LIQUID |
| [COALOIL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L412) | Coal Oil | default20 | LIQUID,VISCOUS |
| [COFFEE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L455) | Coffee | default20 | LIQUID |
| [COLLOID](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L466) | Colloid | default20 | LIQUID,VISCOUS |
| [COOLANT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L356) | Borated PWR Coolant | default20 | LIQUID |
| [COOLANT_HOT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L426) | Hot Borated PWR Coolant | 325 | LIQUID |
| [CRACKOIL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L411) | Cracked Oil | default20 | LIQUID,VISCOUS |
| [CRACKOIL_DS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L534) | Desulfurized Cracked Oil | default20 | LIQUID,VISCOUS |
| [CREAM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L518) | Heavy Whipping Cream | default20 | LIQUID |
| [CRYOGEL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L387) | Cryogel | -170 | LIQUID,VISCOUS |
| [DEUTERIUM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L358) | Deuterium | default20 | GASEOUS |
| [DICYANOACETYLENE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L519) | Dicyanoacetylene | default20 | GASEOUS |
| [DIESEL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L370) | Diesel | default20 | LIQUID |
| [DIESEL_CRACK](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L416) | Cracked Diesel | default20 | LIQUID |
| [DIESEL_CRACK_REFORM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L460) | High-Octane Cracked Diesel | default20 | LIQUID |
| [DIESEL_REFORM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L459) | High-Octane Diesel | default20 | LIQUID |
| [DUNAAIR](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L539) | Martian Air | default20 | GASEOUS |
| [EGG](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L493) | Dissolved Egg | default20 | LIQUID |
| [ELBOWGREASE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L485) | Elbow Grease | default20 | LIQUID |
| [EMILK](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L516) | Evaporated Milk | default20 | LIQUID |
| [ENDERJUICE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L421) | Ender Juice | default20 | LIQUID |
| [ESTRADIOL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L495) | Estradiol Solution | default20 | LIQUID |
| [ETHANOL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L409) | Ethanol | default20 | LIQUID |
| [EVEAIR](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L467) | Venus Air | default20 | GASEOUS |
| [EXCIMER_GASMIX](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L586) | Excimer Gas Mix | default20 | GASEOUS |
| [FISHOIL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L496) | Fish Oil | default20 | LIQUID |
| [FLUORINE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L479) | Fluorine | default20 | GASEOUS |
| [FRACKSOL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L399) | Fracking Solution | default20 | LIQUID,VISCOUS |
| [FRESH_WATER](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L591) | Fresh Water | default20 | LIQUID |
| [FULLERENE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L529) | Fullerene Solution | default20 | LIQUID |
| [GADOLINIUM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L583) | Liquid Gadolinium | default20 | LIQUID,VISCOUS |
| [GAS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L373) | Natural Gas/Methane | default20 | GASEOUS |
| [GASEOUS_PLUTONIUM_BROMIDE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L551) | Gaseous Plutonium Bromide | 2600 | GASEOUS,NOID,NOCON |
| [GASEOUS_SCHRABIDIUM_BROMIDE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L552) | Gaseous Larp Bromide | 3000 | GASEOUS,NOID,NOCON |
| [GASEOUS_THORIUM_BROMIDE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L553) | GASEOUS_THORIUM_BROMIDE | 2300 | GASEOUS,NOID,NOCON |
| [GASEOUS_URANIUM_BROMIDE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L550) | Gaseous Uranium Bromide | 2500 | GASEOUS,NOID,NOCON |
| [GASOLINE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L396) | Gasoline | default20 | LIQUID |
| [GASOLINE_LEADED](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L423) | Leaded Gasoline | default20 | LIQUID |
| [GAS_COKER](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L492) | Coker Gas | default20 | GASEOUS |
| [GAS_WATZ](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L545) | Radioactive TRISO Offgas | 2500 | GASEOUS,NOID,NOCON |
| [HCL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L441) | Hydrochloric Acid | default20 | LIQUID |
| [HEATINGOIL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L365) | Heating Oil | default20 | LIQUID,VISCOUS |
| [HEATINGOIL_VACUUM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L458) | Heavy Heating Oil | default20 | LIQUID,VISCOUS |
| [HEAVYOIL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L362) | Heavy Oil | default20 | LIQUID,VISCOUS |
| [HEAVYOIL_VACUUM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L447) | Vacuum Heavy Oil | default20 | LIQUID,VISCOUS |
| [HEAVYWATER](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L410) | Heavy Water | default20 | LIQUID |
| [HEAVYWATER_HOT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L521) | Hot Heavy Water | 325 | LIQUID |
| [HELIUM3](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L407) | Helium-3 | default20 | GASEOUS |
| [HELIUM4](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L520) | Helium-4 | default20 | GASEOUS |
| [HELIUM4_HOT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L589) | Hot Helium-4 | 750 | GASEOUS |
| [HONEY](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L457) | Honey | default20 | LIQUID |
| [HOTCRACKOIL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L413) | Hot Cracked Oil | 350 | LIQUID,VISCOUS |
| [HOTCRACKOIL_DS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L535) | Desulfurized Hot Cracked Oil | 350 | LIQUID,VISCOUS |
| [HOTOIL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L361) | Hot Crude Oil | 350 | LIQUID,VISCOUS |
| [HOTOIL_DS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L533) | Desulfurized Hot Crude Oil | 350 | LIQUID,VISCOUS |
| [HOTSTEAM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L353) | Dense Steam | 300 | GASEOUS |
| [HTCO4](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L489) | Pertechnetic Acid | default20 | LIQUID |
| [HYDRAZINE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L436) | Hydrazine | default20 | LIQUID |
| [HYDROFLUORIC_ACID](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L578) | Hydrofluoric Acid | 300 | LIQUID,VISCOUS |
| [HYDROGEN](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L388) | Liquid Hydrogen | -253 | LIQUID,EVAP |
| [HYDROGEN_SULFIDE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L574) | Hydrogen Sulfide | default20 | GASEOUS |
| [IODINE_BRINE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L581) | Iodine Brine | default20 | LIQUID,VISCOUS |
| [IONGEL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L484) | Ionic Gel | default20 | LIQUID,VISCOUS |
| [IRIDIUM_SOLUTION](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L577) | Iridium Solution | 300 | LIQUID,VISCOUS |
| [JOOLGAS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L512) | Jool Air | default20 | GASEOUS |
| [KEROSENE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L372) | Kerosene | default20 | LIQUID |
| [KEROSENE_REFORM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L461) | Jet Fuel | default20 | LIQUID |
| [KMnO4](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L468) | Potassium Permanganate Solution | default20 | LIQUID |
| [KRYPTON](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L454) | Krypton Gas | default20 | GASEOUS |
| [KRYPTON_FLUORINE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L587) | Krypton Difluoride | default20 | GASEOUS,NOCON |
| [LAVA](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L357) | Lava | 1200 | LIQUID,VISCOUS |
| [LEAD](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L543) | Liquid Lead | 350 | LIQUID,VISCOUS |
| [LEAD_HOT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L544) | Hot Liquid Lead | 650 | LIQUID,VISCOUS |
| [LIGHTOIL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L371) | Light Oil | default20 | LIQUID |
| [LIGHTOIL_CRACK](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L415) | Cracked Light Oil | default20 | LIQUID |
| [LIGHTOIL_DS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L537) | Desulfurized Light Oil | default20 | LIQUID |
| [LIGHTOIL_VACUUM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L449) | Vacuum Light Oil | default20 | LIQUID |
| [LIGHT_WATER](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L592) | Light Water | default20 | LIQUID |
| [LIGHT_WATER_HOT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L594) | Hot Light Water | 325 | LIQUID |
| [LPG](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L375) | LPG | default20 | LIQUID |
| [LUBRICANT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L368) | Engine Lubricant | default20 | LIQUID |
| [MERCURY](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L392) | Mercury | default20 | LIQUID |
| [METHANOL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L470) | Methanol | default20 | LIQUID |
| [METHYLENE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L477) | Methylene | default20 | GASEOUS |
| [MILK](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L463) | Milk | default20 | LIQUID |
| [MINSOL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L442) | Mineral Slurry | default20 | LIQUID |
| [MOLTEN_SALT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L555) | Fluoride Reactor Salt | 800 | LIQUID,VISCOUS |
| [MOLTEN_STRONTIUM_CHLORIDE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L573) | Molten Strontium Chloride | 700 | LIQUID |
| [MUG](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L427) | Mug Root Beer | default20 | LIQUID |
| [MUG_HOT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L428) | Hot Mug Root Beer | 500 | LIQUID |
| [MUSTARDGAS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L483) | Mustard Gas | default20 | GASEOUS |
| [NAPHTHA](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L369) | Naphtha | default20 | LIQUID,VISCOUS |
| [NAPHTHA_COKER](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L491) | Coker Naphtha | default20 | LIQUID,VISCOUS |
| [NAPHTHA_CRACK](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L414) | Cracked Naphtha | default20 | LIQUID,VISCOUS |
| [NAPHTHA_DS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L536) | Desulfurized Naphtha | default20 | LIQUID,VISCOUS |
| [NEON](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L452) | Neon Gas | default20 | GASEOUS |
| [NGAS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L515) | Neidon Air | default20 | GASEOUS |
| [NITAN](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L378) | NITAN© 100 Octane Super Fuel | default20 | LIQUID |
| [NITRIC_ACID](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L434) | Nitric Acid | default20 | LIQUID |
| [NITROGEN](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L432) | Liquid Nitrogen | -196 | LIQUID,EVAP |
| [NITROGEN_HELIUM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L585) | Nitrogen Helium Mix | default20 | GASEOUS,NOCON |
| [NITROGLYCERIN](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L498) | Nitroglycerin | default20 | LIQUID |
| [NMASS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L487) | N-MASS(II) Driver Fuel | default20 | LIQUID |
| [NMASSTETRANOL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L486) | N-MASS(III) Tetrahexaethanol | default20 | LIQUID |
| [NOBLE_GAS_MIX](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L582) | Noble Gas Mix | default20 | GASEOUS,NOCON |
| [NONE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L350) | None | default20 | unspecified |
| [OIL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L360) | Crude Oil | default20 | LIQUID,VISCOUS |
| [OIL_COKER](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L490) | Coker Oil | default20 | LIQUID,VISCOUS |
| [OIL_DS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L532) | Desulfurized Crude Oil | default20 | LIQUID,VISCOUS |
| [OLIVEOIL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L465) | Olive Oil | default20 | LIQUID |
| [OSMIRIDIUM_SOLUTION](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L408) | Osmium Tetroxide Solution | 300 | LIQUID,VISCOUS |
| [OXYGEN](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L389) | Liquid Oxygen | -183 | LIQUID,EVAP |
| [OXYHYDROGEN](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L444) | Oxyhydrogen | default20 | GASEOUS |
| [PAIN](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L393) | Pandemonium(III)tantalite Solution | 300 | LIQUID,VISCOUS |
| [PEROXIDE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L385) | Hydrogen Peroxide | default20 | LIQUID |
| [PETROIL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L367) | Petroil | default20 | LIQUID |
| [PETROIL_LEADED](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L422) | Leaded Petroil | default20 | LIQUID |
| [PETROLEUM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L374) | Petroleum Gas | default20 | GASEOUS |
| [PHOSGENE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L482) | Phosgene | default20 | GASEOUS |
| [PLASMA_BF](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L404) | Balefire Plasma | 8500 | PLASMA,NOID,NOCON |
| [PLASMA_DH3](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L406) | Deuterium-Helium-3 Plasma | 300_000_000 | PLASMA,NOID,NOCON |
| [PLASMA_DT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L400) | Deuterium-Tritium Plasma | 150_000_000 | PLASMA,NOID,NOCON |
| [PLASMA_HD](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L401) | Hydrogen-Deuterium Plasma | 80_000_000 | PLASMA,NOID,NOCON |
| [PLASMA_HT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L402) | Hydrogen-Tritium Plasma | 100_000_000 | PLASMA,NOID,NOCON |
| [PLASMA_XM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L403) | Helium-4-Oxygen Plasma | 4250 | PLASMA,NOID,NOCON |
| [PLUTONIUM_BROMIDE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L547) | Plutonium(III) Bromide Melt | 200 | LIQUID,VISCOUS |
| [POLLUCITE_SOLUTION](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L566) | Pollucite Solution | default20 | LIQUID,NOCON |
| [POLLUCITE_SOLUTION_HEAVY](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L567) | Heavy Pollucite Solution | default20 | LIQUID,NOCON |
| [POLLUCITE_SOLUTION_LIGHT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L568) | Light Pollucite Solution | default20 | LIQUID,NOCON |
| [POLYTHYLENE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L478) | Polythylene | default20 | LIQUID |
| [POTASSIUM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L558) | Liquid Potassium | 200 | LIQUID |
| [POTASSIUM_CHLORIDE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L503) | Potassiumchloride Solution | default20 | LIQUID,NOCON |
| [POTASSIUM_HYDROXIDE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L563) | Potassium Hydroxide | 200 | LIQUID |
| [PUF6](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L380) | Plutonium Hexafluoride | default20 | GASEOUS |
| [RADIOSOLVENT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L445) | High-Performance Solvent | default20 | LIQUID |
| [RAFFINATE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L575) | Raffinate | default20 | LIQUID,VISCOUS |
| [RECLAIMED](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L366) | Reclaimed Industrial Oil | default20 | LIQUID,VISCOUS |
| [REDMUD](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L499) | Red Mud | default20 | LIQUID,VISCOUS |
| [REFORMATE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L448) | Reformate | default20 | LIQUID,VISCOUS |
| [REFORMGAS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L462) | Reformate Gas | default20 | GASEOUS |
| [RHODIUM_SOLUTION](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L576) | Rhodium Solution | 300 | LIQUID,VISCOUS |
| [ROCKET_FUEL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L590) | Solid Rocket Fuel | default20 | LIQUID |
| [SALIENT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L419) | Salient Green | default20 | LIQUID,VISCOUS |
| [SARNUSGAS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L513) | Sarnus Air | default20 | GASEOUS |
| [SAS3](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L381) | Unbihexium Trisulfide | default20 | LIQUID |
| [SCHRABIDIC](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L382) | Schrabidic Acid | default20 | LIQUID |
| [SCHRABIDIUM_BROMIDE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L548) | SCHRABIDIUM_BROMIDE | 200 | LIQUID,VISCOUS |
| [SCUTTERBLOOD](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L488) | Scutterblood | default20 | LIQUID,VISCOUS |
| [SEEDSLURRY](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L431) | Seeding Slurry | default20 | LIQUID,VISCOUS |
| [SLOP](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L541) | Ore Slop | default20 | LIQUID,VISCOUS |
| [SMEAR](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L364) | Industrial Oil | default20 | LIQUID,VISCOUS |
| [SMILK](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L464) | Strawberry Milk | default20 | LIQUID |
| [SMOKE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L509) | Smoke | default20 | GASEOUS,NOID,NOCON |
| [SMOKE_LEADED](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L510) | Leaded Smoke | default20 | GASEOUS,NOID,NOCON |
| [SMOKE_POISON](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L511) | Poison Smoke | default20 | GASEOUS,NOID,NOCON |
| [SODIUM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L522) | Liquid Sodium | 400 | LIQUID,VISCOUS |
| [SODIUM_CARBONATE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L524) | Sodium Carbonate | 400 | LIQUID,VISCOUS |
| [SODIUM_HOT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L525) | Hot Liquid Sodium | 550 | LIQUID,VISCOUS |
| [SODIUM_HYDROXIDE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L523) | Sodium Hydroxide | 400 | LIQUID,VISCOUS |
| [SOLVENT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L440) | Solvent | default20 | LIQUID |
| [SOURGAS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L450) | Sour Gas | default20 | GASEOUS |
| [SPENTSTEAM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L398) | Low-Pressure Steam | default20 | GASEOUS,NOCON |
| [STEAM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L352) | Steam | 100 | GASEOUS |
| [STELLAR_FLUX](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L538) | Stellar Flux | default20 | GASEOUS |
| [SULFURIC_ACID](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L425) | Sulfuric Acid | default20 | LIQUID |
| [SUNFLOWEROIL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L497) | Sunflower Seed Oil | default20 | LIQUID |
| [SUPERHEATED_HYDROGEN](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L542) | Superheated Hydrogen | 2200 | GASEOUS,NOID,NOCON |
| [SUPERHOTSTEAM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L354) | Super Dense Steam | 450 | GASEOUS |
| [SYNGAS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L443) | Syngas | default20 | GASEOUS |
| [TEA](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L456) | Tea | default20 | LIQUID |
| [TEKTOAIR](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L481) | Tekto Air | default20 | GASEOUS |
| [THORIUM_BROMIDE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L549) | Thorium Bromide | 200 | LIQUID,VISCOUS |
| [THORIUM_SALT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L526) | Liquid Thorium Salt | 800 | LIQUID,VISCOUS |
| [THORIUM_SALT_DEPLETED](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L528) | Depleted Liquid Thorium Salt | 800 | LIQUID,VISCOUS |
| [THORIUM_SALT_HOT](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L527) | Hot Liquid Thorium Salt | 1600 | LIQUID,VISCOUS |
| [TRITIUM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L359) | Tritium | default20 | GASEOUS |
| [UF6](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L379) | Uranium Hexafluoride | default20 | GASEOUS |
| [UGAS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L514) | Urlum Air | default20 | GASEOUS |
| [ULTRAHOTSTEAM](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L355) | Ultra Dense Steam | 600 | GASEOUS |
| [UNSATURATEDS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L418) | Unsaturated Hydrocarbons | default20 | GASEOUS |
| [URANIUM_BROMIDE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L546) | Uranium(IV) Bromide Melt | 200 | LIQUID,VISCOUS |
| [VITRIOL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L540) | Vitriol | default20 | LIQUID,VISCOUS |
| [WASTEFLUID](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L394) | Liquid Nuclear Waste | default20 | LIQUID,VISCOUS,NOCON |
| [WASTEGAS](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L395) | Gaseous Nuclear Waste | default20 | GASEOUS,NOCON |
| [WATER](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L351) | Water | default20 | LIQUID |
| [WATZ](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L386) | TRISO Reprocessing Slurry | default20 | LIQUID,VISCOUS |
| [WOODOIL](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L429) | Wood Oil | default20 | LIQUID,VISCOUS |
| [XENON](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L390) | Xenon Gas | default20 | GASEOUS |
| [XENON_CHLORINE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L588) | Xenon Difluoride | default20 | GASEOUS,NOCON |
| [XPJUICE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L420) | Experience Juice | default20 | LIQUID,VISCOUS |
| [XYLENE](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L451) | BTX | default20 | LIQUID,VISCOUS |

## Other accounting anchors

- [EnergyUnits](../src/main/java/api/hbm/energymk2/EnergyUnits.java): exact quanta, accumulator and versioned energy NBT.
- [ThermalUnits](../src/main/java/com/hbm/util/ThermalUnits.java): thermal presentation; does not calibrate reactor HU.
- [Fluids final traits and rounding](../src/main/java/com/hbm/inventory/fluid/Fluids.java#L1135): final calculated registrations supersede earlier constructor traits.
- [FluidTank](../src/main/java/com/hbm/inventory/fluid/tank/FluidTank.java#L310): persisted fill/max/type/PU.
- [FluidNetMK2](../src/main/java/api/hbm/fluidmk2/FluidNetMK2.java): endpoint supply/demand/rate; [PowerNetMK2](../src/main/java/api/hbm/energymk2/PowerNetMK2.java): authoritative electrical distribution.
- [MaterialShapes](../src/main/java/com/hbm/inventory/material/MaterialShapes.java): conserved material proportions; [MineralRecipes](../src/main/java/com/hbm/crafting/MineralRecipes.java#L687): billet conversion.
- [TileMappings](../src/main/java/com/hbm/tileentity/TileMappings.java): persistent class registrations; [English localization](../src/main/resources/assets/hbm/lang/en_US.lang): current player names.

This catalogue is a broad parameter census supporting a source/design investigation. It is not an assertion that every listed callback, recipe variant, addon, world condition or safety transition has been exercised. Runtime measurement gates are in the main document.


## Phase 5D catalogue boundary (2026-10-13)

The original census/line links are historical evidence. The [frozen ledger](REALSIM_PHYSICAL_LEDGER.md#phase-5d-frozen-thermal-contract-2026-10-13) and [owner inventory](REALSIM_PROCESS_INVENTORY.md#phase-5d-authoritative-thermal-owners-2026-10-13) supersede converted equipment's old trait/RPM expressions and buffer-derived rates. Steam state/work/heat/rejection originate in `com.hbm.realsim.SteamLedger`; controllers use `ThermalAccount`/`ThermalMachine`, gas recovery uses `TurbineFuelAccount`/`ExhaustAccount`/`HeatRecovery`. Nuclear traits remain deferred boundaries, not converted runtime tables.
