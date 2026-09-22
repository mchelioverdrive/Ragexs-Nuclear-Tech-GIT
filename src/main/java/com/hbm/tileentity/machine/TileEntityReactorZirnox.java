package com.hbm.tileentity.machine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.config.MobConfig;
import com.hbm.entity.projectile.EntityZirnoxDebris;
import com.hbm.entity.projectile.EntityZirnoxDebris.DebrisType;
import com.hbm.explosion.ExplosionNukeGeneric;
import com.hbm.handler.CompatHandler;
import com.hbm.handler.MultiblockHandlerXR;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.container.ContainerReactorZirnox;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIReactorZirnox;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemZirnoxRod;
import com.hbm.items.machine.ItemZirnoxRod.EnumZirnoxType;
import com.hbm.main.MainRegistry;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.CompatEnergyControl;
import com.hbm.util.EnumUtil;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.fluid.IFluidStandardTransceiver;
import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

// Realistification: Magnox/Zirnox Nuclear Reactor
//
// Design identity:
// - graphite moderated
// - CO2 primary coolant
// - water/steam secondary side
// - cheap natural uranium works best
// - exotic fuels work but are hotter/dirtier/riskier
// - high thermal inertia
// - decay heat after shutdown
// - dirty pressure/graphite/cladding failure, not a nuclear detonation

@Optional.InterfaceList({@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")})
public class TileEntityReactorZirnox extends TileEntityMachineBase implements IControlReceiver, IFluidStandardTransceiver, SimpleComponent, IGUIProvider, IInfoProviderEC, CompatHandler.OCComponent {

	public int heat;
	public static final int maxHeat = 100000;

	public int pressure;
	public static final int maxPressure = 100000;

	// Kept for GUI/container compatibility.
	// Internally this now maps to control rods:
	// isOn = true  -> control rods withdrawn
	// isOn = false -> SCRAM / rods fully inserted
	public boolean isOn = false;

	public FluidTank steam;
	public FluidTank carbonDioxide;
	public FluidTank water;

	protected int output;

	// ==========================
	// REALISTIFICATION STATE
	// ==========================

	// 0 = full power, 100 = fully shut down/SCRAM.
	public int controlRodInsertion = 100;
	public int targetControlRodInsertion = 100;
	public double peakCladdingTemperature = 20.0D;
	private final double[] channelCladdingTemperature = new double[24];
	private final int[] channelPower = new int[24];
	private double previousPressureBar;
	public String activeTripInput = "none";

	// Legacy temperature displays: 0..100000 = 20..800 C. Neither is an energy store.
	public int graphiteHeat = 0;
	// Thermal stores in gameplay heat units (HU), measured above 20 C.
	private double coreEnergy;
	private double primaryEnergy;
	// Unreleased fission-product energy in HU; charged by operating history.
	private double decayEnergy;
	public boolean shutdownLatched;
	public String shutdownReason = "none";
	public int shutdownWaterUsed;
	public int primaryGasVented;
	private boolean terminalFailure;

	// Residual decay heat. Stored as a double because it decays smoothly.
	public double decayHeat = 0.0D;

	// Damage accumulators. 100000 means severe failure.
	public int graphiteDamage = 0;
	public int claddingDamage = 0;

	// Compatibility only. There is no represented ingress/oxidant source.
	public int airIngress = 0;

	// Debug/OC fields.
	public int activePower = 0;
	public int co2Cooling = 0;

	private static final int[] slots_io = new int[] {
		0, 1, 2, 3, 4, 5, 6, 7,
		8, 9, 10, 11, 12, 13, 14, 15,
		16, 17, 18, 19, 20, 21, 22, 23
	};

	// ==========================
	// BALANCE CONSTANTS
	// ==========================

	private static final double TEMP_BASE_C = 20.0D;
	private static final double TEMP_RANGE_C = 780.0D;

	// Late Magnox-ish target range.
	private static final double NOMINAL_CO2_OUTLET_C = 410.0D;
	private static final double NOMINAL_PRESSURE_BAR = 26.0D;
	private static final double MAX_PRESSURE_BAR = 30.0D;

	private static final double STEAM_START_C = 300.0D;
	private static final double STEAM_FULL_C = 450.0D;

	private static final int NOMINAL_CO2_FILL = 14000;
	private static final int MAX_STEAM_PER_TICK = 55;
	private static final int HEAT_REMOVED_PER_MB_STEAM = 80;

	private static final double CLADDING_DAMAGE_TEMP_C = 500.0D;
	private static final double FUEL_DAMAGE_TEMP_C = 600.0D;
	private static final double GRAPHITE_DAMAGE_TEMP_C = 600.0D;
	// Gameplay protection/thermal constants, not historical engineering limits.
	public static final double CORE_CAPACITY = 2500.0D;
	public static final double PRIMARY_CAPACITY = 600.0D;
	private static final double DECAY_FRACTION = 0.055D;
	private static final double DECAY_RELEASE_RATE = 0.0005D;
	public static final int FEEDWATER_TRIP_MB = 8000;
	public static final int FEEDWATER_RESTART_MB = 12000;
	private static final double TRIP_CLADDING_C = 560.0D;
	private static final double RESTART_CORE_C = 350.0D;
	private static final double RESTART_CO2_FRACTION = 0.9D;
	private static final double TRIP_PRESSURE_BAR = 29.0D;
	private static final double RESTART_PRESSURE_BAR = 27.0D;
	private static final double RELIEF_PRESSURE_BAR = 30.0D;
	private static final double RUPTURE_PRESSURE_BAR = 34.0D;
	public static final int PRIMARY_RELIEF_MB = 24;
	private static final double RAPID_PRESSURE_LOSS_BAR = 1.5D;
	private static final int ROD_TRAVEL_TICKS = 100;
	private static final double CHANNEL_RESPONSE = 0.04D;
	private static final int SHUTDOWN_TRANSFER_HU = 400;
	private static final int SHUTDOWN_WATER_MB = 10;
	private static final double SHUTDOWN_BOILING_C = 100.0D;
	// 1 water -> 1 superhot steam; ordinary steam has 1/5 its heat, 100x its volume.
	private static final int SHUTDOWN_HEAT_PER_MB = HEAT_REMOVED_PER_MB_STEAM / 5;
	private static final int RESTART_STEAM_ROOM = 1000;

	private static final int MAX_GRAPHITE_DAMAGE = 100000;
	private static final int MAX_CLADDING_DAMAGE = 100000;

	private static final int MELTDOWN_OVERPRESSURE = 0;
	private static final int MELTDOWN_OVERHEAT = 1;
	private static final int MELTDOWN_CLADDING = 2;
	private static final int MELTDOWN_GRAPHITE = 3;

	// Slot flux shaping.
	// Center/near-center channels run hotter; edge channels are slightly weaker.
	private static final float[] FLUX = new float[] {
		0.85F, 0.95F, 0.85F,
		0.90F, 1.05F, 1.05F, 0.90F,
		0.95F, 1.10F, 1.10F,
		0.90F, 1.15F, 1.15F, 0.90F,
		0.95F, 1.10F, 1.10F,
		0.90F, 1.05F, 1.05F, 0.90F,
		0.85F, 0.95F, 0.85F
	};

	public static final HashMap<ComparableStack, ItemStack> fuelMap = new HashMap<ComparableStack, ItemStack>();
	static {
		fuelMap.put(new ComparableStack(ModItems.rod_zirnox, 1, EnumZirnoxType.NATURAL_URANIUM_FUEL.ordinal()), new ItemStack(ModItems.rod_zirnox_natural_uranium_fuel_depleted));
		fuelMap.put(new ComparableStack(ModItems.rod_zirnox, 1, EnumZirnoxType.URANIUM_FUEL.ordinal()), new ItemStack(ModItems.rod_zirnox_uranium_fuel_depleted));
		fuelMap.put(new ComparableStack(ModItems.rod_zirnox, 1, EnumZirnoxType.TH232.ordinal()), new ItemStack(ModItems.rod_zirnox, 1, EnumZirnoxType.THORIUM_FUEL.ordinal()));
		fuelMap.put(new ComparableStack(ModItems.rod_zirnox, 1, EnumZirnoxType.THORIUM_FUEL.ordinal()), new ItemStack(ModItems.rod_zirnox_thorium_fuel_depleted));
		fuelMap.put(new ComparableStack(ModItems.rod_zirnox, 1, EnumZirnoxType.MOX_FUEL.ordinal()), new ItemStack(ModItems.rod_zirnox_mox_fuel_depleted));
		fuelMap.put(new ComparableStack(ModItems.rod_zirnox, 1, EnumZirnoxType.PLUTONIUM_FUEL.ordinal()), new ItemStack(ModItems.rod_zirnox_plutonium_fuel_depleted));
		fuelMap.put(new ComparableStack(ModItems.rod_zirnox, 1, EnumZirnoxType.U233_FUEL.ordinal()), new ItemStack(ModItems.rod_zirnox_u233_fuel_depleted));
		fuelMap.put(new ComparableStack(ModItems.rod_zirnox, 1, EnumZirnoxType.U235_FUEL.ordinal()), new ItemStack(ModItems.rod_zirnox_u235_fuel_depleted));
		fuelMap.put(new ComparableStack(ModItems.rod_zirnox, 1, EnumZirnoxType.LES_FUEL.ordinal()), new ItemStack(ModItems.rod_zirnox_les_fuel_depleted));
		fuelMap.put(new ComparableStack(ModItems.rod_zirnox, 1, EnumZirnoxType.LITHIUM.ordinal()), new ItemStack(ModItems.rod_zirnox_tritium));
		fuelMap.put(new ComparableStack(ModItems.rod_zirnox, 1, EnumZirnoxType.ZFB_MOX.ordinal()), new ItemStack(ModItems.rod_zirnox_zfb_mox_depleted));
	}

	public TileEntityReactorZirnox() {
		super(28);
		steam = new FluidTank(Fluids.SUPERHOTSTEAM, 8000);
		carbonDioxide = new FluidTank(Fluids.CARBONDIOXIDE, 16000);
		water = new FluidTank(Fluids.LIGHT_WATER, 32000).migrateFrom(Fluids.WATER);
	}

	@Override
	public String getName() {
		return "container.zirnox";
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return slots_io;
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack stack) {
		return i < 24 && stack.getItem() instanceof ItemZirnoxRod;
	}

	@Override
	public boolean canExtractItem(int i, ItemStack stack, int j) {
		return i < 24 && !(stack.getItem() instanceof ItemZirnoxRod);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		heat = nbt.getInteger("heat");
		pressure = nbt.getInteger("pressure");
		isOn = nbt.getBoolean("isOn");

		if(nbt.hasKey("controlRodInsertion")) {
			controlRodInsertion = clamp(nbt.getInteger("controlRodInsertion"), 0, 100);
		} else {
			controlRodInsertion = isOn ? 0 : 100;
		}
		targetControlRodInsertion = nbt.hasKey("targetControlRodInsertion") ? clamp(nbt.getInteger("targetControlRodInsertion"), 0, 100) : controlRodInsertion;

		if(nbt.hasKey("graphiteHeat")) {
			graphiteHeat = nbt.getInteger("graphiteHeat");
		} else {
			graphiteHeat = heat;
		}

		decayHeat = nbt.getDouble("decayHeat");
		graphiteDamage = nbt.getInteger("graphiteDamage");
		claddingDamage = nbt.getInteger("claddingDamage");
		airIngress = nbt.getInteger("airIngress");
		activePower = nbt.getInteger("activePower");
		co2Cooling = nbt.getInteger("co2Cooling");
		activeTripInput = nbt.hasKey("activeTripInput") ? nbt.getString("activeTripInput") : "none";

		steam.readFromNBT(nbt, "steam");
		carbonDioxide.readFromNBT(nbt, "carbondioxide");
		water.readFromNBT(nbt, "water");
		readThermalState(nbt);
	}

	private void readThermalState(NBTTagCompound data) {
		int version = data.getInteger("thermalVersion");
		if(version >= 3) {
			coreEnergy = Math.max(0, data.getDouble("coreEnergy"));
			primaryEnergy = Math.max(0, data.getDouble("primaryEnergy"));
		} else {
			// Version 2 energies used the legacy display scale as capacity. Migrate
			// temperatures, rather than reinterpreting those values as version 3 HU.
			coreEnergy = Math.max(0, (graphiteHeat * 1.0E-5D * TEMP_RANGE_C) * CORE_CAPACITY);
			primaryEnergy = Math.max(0, (heat * 1.0E-5D * TEMP_RANGE_C) * PRIMARY_CAPACITY);
		}
		decayEnergy = Math.max(0, version >= 2 ? data.getDouble("decayEnergy") : decayHeat / DECAY_RELEASE_RATE);
		decayHeat = decayEnergy * DECAY_RELEASE_RATE;
		shutdownLatched = version >= 2 ? data.getBoolean("shutdownLatched") : true;
		shutdownReason = version >= 2 ? data.getString("shutdownReason") : "migration";
		if(shutdownReason.isEmpty()) shutdownReason = shutdownLatched ? "manual" : "none";
		shutdownWaterUsed = data.getInteger("shutdownWaterUsed");
		primaryGasVented = data.getInteger("primaryGasVented");
		airIngress = 0;
		for(int i = 0; i < channelCladdingTemperature.length; i++) {
			String key = "channelCladding" + i;
			channelCladdingTemperature[i] = version >= 3 && data.hasKey(key) ? data.getDouble(key) : getGraphiteHeatC();
		}
		updatePeakCladdingTemperature();
		previousPressureBar = data.hasKey("previousPressureBar") ? data.getDouble("previousPressureBar") : 0.0D;
		if(shutdownLatched) {
			isOn = false;
			targetControlRodInsertion = 100;
		}
		updateThermalDisplays();
	}

	private void writeThermalState(NBTTagCompound data) {
		data.setInteger("thermalVersion", 3);
		data.setDouble("coreEnergy", coreEnergy);
		data.setDouble("primaryEnergy", primaryEnergy);
		data.setDouble("decayEnergy", decayEnergy);
		data.setBoolean("shutdownLatched", shutdownLatched);
		data.setString("shutdownReason", shutdownReason);
		data.setInteger("shutdownWaterUsed", shutdownWaterUsed);
		data.setInteger("primaryGasVented", primaryGasVented);
		data.setDouble("previousPressureBar", previousPressureBar);
		for(int i = 0; i < channelCladdingTemperature.length; i++) data.setDouble("channelCladding" + i, channelCladdingTemperature[i]);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		nbt.setInteger("heat", heat);
		nbt.setInteger("pressure", pressure);
		nbt.setBoolean("isOn", isOn);

		nbt.setInteger("controlRodInsertion", controlRodInsertion);
		nbt.setInteger("targetControlRodInsertion", targetControlRodInsertion);
		nbt.setInteger("graphiteHeat", graphiteHeat);
		nbt.setDouble("decayHeat", decayHeat);
		nbt.setInteger("graphiteDamage", graphiteDamage);
		nbt.setInteger("claddingDamage", claddingDamage);
		nbt.setInteger("airIngress", airIngress);
		nbt.setInteger("activePower", activePower);
		nbt.setInteger("co2Cooling", co2Cooling);
		nbt.setString("activeTripInput", activeTripInput);

		steam.writeToNBT(nbt, "steam");
		carbonDioxide.writeToNBT(nbt, "carbondioxide");
		water.writeToNBT(nbt, "water");
		writeThermalState(nbt);
	}

	public void networkUnpack(NBTTagCompound data) {
		super.networkUnpack(data);

		this.heat = data.getInteger("heat");
		this.pressure = data.getInteger("pressure");
		this.isOn = data.getBoolean("isOn");

		this.controlRodInsertion = data.hasKey("controlRodInsertion") ? clamp(data.getInteger("controlRodInsertion"), 0, 100) : (isOn ? 0 : 100);
		this.targetControlRodInsertion = data.hasKey("targetControlRodInsertion") ? clamp(data.getInteger("targetControlRodInsertion"), 0, 100) : controlRodInsertion;
		this.graphiteHeat = data.hasKey("graphiteHeat") ? data.getInteger("graphiteHeat") : heat;
		this.decayHeat = data.getDouble("decayHeat");
		this.graphiteDamage = data.getInteger("graphiteDamage");
		this.claddingDamage = data.getInteger("claddingDamage");
		this.airIngress = data.getInteger("airIngress");
		this.activePower = data.getInteger("activePower");
		this.co2Cooling = data.getInteger("co2Cooling");
		this.activeTripInput = data.hasKey("activeTripInput") ? data.getString("activeTripInput") : "none";

		steam.readFromNBT(data, "t0");
		carbonDioxide.readFromNBT(data, "t1");
		water.readFromNBT(data, "t2");
		readThermalState(data);
	}

	public int getGaugeScaled(int i, int type) {
		switch(type) {
			case 0: return (steam.getFill() * i) / steam.getMaxFill();
			case 1: return (carbonDioxide.getFill() * i) / carbonDioxide.getMaxFill();
			case 2: return (water.getFill() * i) / water.getMaxFill();
			case 3: return clamp((this.graphiteHeat * i) / maxHeat, 0, i);
			case 4: return clamp((this.pressure * i) / maxPressure, 0, i);
			default: return 1;
		}
	}

	private int[] getNeighbouringSlots(int id) {
		switch(id) {
			case 0: return new int[] { 1, 7 };
			case 1: return new int[] { 0, 2, 8 };
			case 2: return new int[] { 1, 9 };
			case 3: return new int[] { 4, 10 };
			case 4: return new int[] { 3, 5, 11 };
			case 5: return new int[] { 4, 6, 12 };
			case 6: return new int[] { 5, 13 };
			case 7: return new int[] { 0, 8, 14 };
			case 8: return new int[] { 1, 7, 9, 15 };
			case 9: return new int[] { 2, 8, 16 };
			case 10: return new int[] { 3, 11, 17 };
			case 11: return new int[] { 4, 10, 12, 18 };
			case 12: return new int[] { 5, 11, 13, 19 };
			case 13: return new int[] { 6, 12, 20 };
			case 14: return new int[] { 7, 15, 21 };
			case 15: return new int[] { 8, 14, 16, 22 };
			case 16: return new int[] { 9, 15, 23 };
			case 17: return new int[] { 10, 18 };
			case 18: return new int[] { 11, 17, 19 };
			case 19: return new int[] { 12, 18, 20 };
			case 20: return new int[] { 13, 19 };
			case 21: return new int[] { 14, 22 };
			case 22: return new int[] { 15, 21, 23 };
			case 23: return new int[] { 16, 22 };
		}

		return null;
	}

	@Override
	public void updateEntity() {
		if(!worldObj.isRemote && !terminalFailure) {

			this.output = 0;
			this.activePower = 0;
			this.co2Cooling = 0;
			this.shutdownWaterUsed = 0;
			this.primaryGasVented = 0;

			if(worldObj.getTotalWorldTime() % 20 == 0) {
				this.updateConnections();
			}

			carbonDioxide.loadTank(24, 26, slots);
			water.loadTank(25, 27, slots);
			// Export first so a working outlet is not mistaken for blocked storage.
			for(DirPos pos : getConPos()) {
				this.sendFluid(steam, worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			}
			updatePressureFromCO2();
			checkProtection();
			if(checkIfMeltdown()) return;
			moveControlRods();

			this.activePower = runFuelCycle();
			applyDecayHeat(this.activePower);
			updateChannelThermals();

			transferPrimaryHeat();
			generateSteam();

			applyPassiveCooling();
			updateThermalDisplays();

			updatePressureFromCO2();
			checkProtection();
			// Rupture is checked before relief: a finite valve cannot undo a burst.
			if(getPressureBar() >= RUPTURE_PRESSURE_BAR) { meltdown(MELTDOWN_OVERPRESSURE); return; }
			if(getPressureBar() >= RELIEF_PRESSURE_BAR) ventCarbonDioxide(PRIMARY_RELIEF_MB);

			applyDamageModel();
			if(checkIfMeltdown()) return;
			previousPressureBar = getPressureBar();
			markDirty();

			NBTTagCompound data = new NBTTagCompound();

			data.setInteger("heat", heat);
			data.setInteger("pressure", pressure);
			data.setBoolean("isOn", isOn);

			data.setInteger("controlRodInsertion", controlRodInsertion);
			data.setInteger("targetControlRodInsertion", targetControlRodInsertion);
			data.setInteger("graphiteHeat", graphiteHeat);
			data.setDouble("decayHeat", decayHeat);
			data.setInteger("graphiteDamage", graphiteDamage);
			data.setInteger("claddingDamage", claddingDamage);
			data.setInteger("airIngress", airIngress);
			data.setInteger("activePower", activePower);
			data.setInteger("co2Cooling", co2Cooling);
			data.setString("activeTripInput", activeTripInput);

			steam.writeToNBT(data, "t0");
			carbonDioxide.writeToNBT(data, "t1");
			water.writeToNBT(data, "t2");
			writeThermalState(data);

			this.networkPack(data, 150);
		}
	}

	private int runFuelCycle() {
		for(int i = 0; i < channelPower.length; i++) channelPower[i] = 0;
		if(controlRodInsertion >= 100) return 0;

		int power = 0;

		for(int i = 0; i < 24; i++) {
			if(slots[i] != null && slots[i].getItem() instanceof ItemZirnoxRod) {
				channelPower[i] = decay(i);
				power += channelPower[i];
			}
		}

		return power;
	}

	private void moveControlRods() {
		if(controlRodInsertion == targetControlRodInsertion) return;
		int step = Math.max(1, 100 / ROD_TRAVEL_TICKS);
		if(controlRodInsertion < targetControlRodInsertion) {
			controlRodInsertion = Math.min(targetControlRodInsertion, controlRodInsertion + step);
		} else {
			controlRodInsertion = Math.max(targetControlRodInsertion, controlRodInsertion - step);
		}
		if(controlRodInsertion >= 100) isOn = false;
	}

	private void updateChannelThermals() {
		double coreTemperature = getGraphiteHeatC();
		double coolingFraction = clampDouble(getCO2FillFraction(), 0.0D, 1.0D);
		for(int i = 0; i < channelCladdingTemperature.length; i++) {
			double localPower = channelPower[i];
			double neighborPower = 0.0D;
			int[] neighbours = getNeighbouringSlots(i);
			if(neighbours != null) {
				for(int neighbour : neighbours) neighborPower += channelPower[neighbour];
				if(neighbours.length > 0) neighborPower /= neighbours.length;
			}
			double localHeating = (localPower + neighborPower * 0.12D) * 0.30D;
			double coolingDenominator = 0.05D + coolingFraction;
			double targetTemperature = coreTemperature + localHeating / coolingDenominator;
			channelCladdingTemperature[i] += (targetTemperature - channelCladdingTemperature[i]) * CHANNEL_RESPONSE;
			channelCladdingTemperature[i] = Math.max(TEMP_BASE_C, channelCladdingTemperature[i]);
		}
		updatePeakCladdingTemperature();
	}

	private void updatePeakCladdingTemperature() {
		peakCladdingTemperature = getGraphiteHeatC();
		for(double temperature : channelCladdingTemperature) {
			peakCladdingTemperature = Math.max(peakCladdingTemperature, temperature);
		}
	}

	private void applyDecayHeat(int activePowerThisTick) {
		// Fuel power is TOTAL recoverable heat: defer 5.5%, do not add it twice.
		decayEnergy += activePowerThisTick * DECAY_FRACTION;
		decayHeat = decayEnergy * DECAY_RELEASE_RATE;
		decayEnergy -= decayHeat;
		coreEnergy += activePowerThisTick * (1.0D - DECAY_FRACTION) + decayHeat;
	}

	private void updateThermalDisplays() {
		graphiteHeat = clamp((int)Math.round((getGraphiteHeatC() - TEMP_BASE_C) / TEMP_RANGE_C * maxHeat), 0, maxHeat);
		heat = clamp((int)Math.round((getHeatC() - TEMP_BASE_C) / TEMP_RANGE_C * maxHeat), 0, maxHeat);
	}

	private void updatePressureFromCO2() {
		double tempK = getHeatC() + 273.15D;
		double nominalK = NOMINAL_CO2_OUTLET_C + 273.15D;
		pressure = barToPressure(getCO2FillFraction() * NOMINAL_PRESSURE_BAR * tempK / nominalK);
	}

	private void transferPrimaryHeat() {
		// Primary store includes exchanger metal and gas. Circulation transports heat;
		// it never removes it. Exchange cannot overshoot thermal equilibrium.
		double fraction = Math.min(1.0D, getCO2FillFraction());
		double difference = getGraphiteHeatC() - getHeatC();
		double equilibrium = Math.abs(difference) / (1.0D / CORE_CAPACITY + 1.0D / PRIMARY_CAPACITY);
		boolean rodsMovingOrWithdrawn = controlRodInsertion < 100;
		double capacity = (rodsMovingOrWithdrawn ? MAX_STEAM_PER_TICK * HEAT_REMOVED_PER_MB_STEAM : SHUTDOWN_TRANSFER_HU) * fraction;
		double exchange = Math.min(equilibrium, capacity);
		if(difference >= 0) {
			exchange = Math.min(exchange, coreEnergy);
			coreEnergy -= exchange;
			primaryEnergy += exchange;
		} else {
			exchange = Math.min(exchange, primaryEnergy);
			primaryEnergy -= exchange;
			coreEnergy += exchange;
		}
		co2Cooling = (int)Math.round(difference >= 0 ? exchange : -exchange);
	}

	private int normalSteamCapacity() {
		double factor = clampDouble((getHeatC() - STEAM_START_C) / (STEAM_FULL_C - STEAM_START_C), 0, 1);
		return (int)Math.floor(MAX_STEAM_PER_TICK * factor);
	}

	private void generateSteam() {
		if(water.getFill() <= 0) return;
		if(controlRodInsertion < 100) {
			// Useful steam is the sole water-dependent sink while operating.
			int cycle = Math.min(normalSteamCapacity(), (int)(primaryEnergy / HEAT_REMOVED_PER_MB_STEAM));
			cycle = Math.min(cycle, Math.min(water.getFill(), steam.getMaxFill() - steam.getFill()));
			if(cycle > 0) {
				water.setFill(water.getFill() - cycle);
				steam.setFill(steam.getFill() + cycle);
				primaryEnergy -= cycle * HEAT_REMOVED_PER_MB_STEAM;
				output = cycle;
			}
			return;
		}
		// Bounded low-pressure shutdown boiling, including the 100..300 C range
		// where useful superhot steam is unavailable. Vent 100 mB ordinary steam
		// per water mB, carrying 16 HU; no output/energy credit. Never cool below
		// the boiling floor. Below 100 C only modest ambient loss remains.
		double available = Math.max(0, primaryEnergy - (SHUTDOWN_BOILING_C - TEMP_BASE_C) * PRIMARY_CAPACITY);
		int used = Math.min(SHUTDOWN_WATER_MB, Math.min(water.getFill(), (int)(available / SHUTDOWN_HEAT_PER_MB)));
		water.setFill(water.getFill() - used);
		primaryEnergy -= used * SHUTDOWN_HEAT_PER_MB;
		shutdownWaterUsed = used;
	}

	private void applyPassiveCooling() {
		// Only modest ambient loss; no water-independent emergency heat sink.
		coreEnergy -= Math.min(coreEnergy, Math.max(1.0D, coreEnergy / 25000.0D));
		primaryEnergy -= Math.min(primaryEnergy, Math.max(0.25D, primaryEnergy / 15000.0D));
	}

	public int getWaterReserve() {
		// Advisory only: removable heat above the shutdown boiling floor, remaining
		// delayed heat, and one discrete tick of current heat input. This estimate
		// neither trips the reactor nor promises that transport will remain available.
		double floorEnergy = (SHUTDOWN_BOILING_C - TEMP_BASE_C) * (CORE_CAPACITY + PRIMARY_CAPACITY);
		double removableThermalEnergy = Math.max(0.0D, coreEnergy + primaryEnergy - floorEnergy);
		double discreteTickMargin = Math.max(0.0D, activePower) + decayHeat;
		return (int)Math.ceil((removableThermalEnergy + decayEnergy + discreteTickMargin) / SHUTDOWN_HEAT_PER_MB) + 16;
	}

	private String protectionReason(boolean restart) {
		if(restart) {
			if(water.getFill() < FEEDWATER_RESTART_MB) return "water";
			if(getCO2FillFraction() < RESTART_CO2_FRACTION) return "co2";
			if(getGraphiteHeatC() >= RESTART_CORE_C) return "temperature";
			if(getPressureBar() >= RESTART_PRESSURE_BAR) return "pressure";
			if(steam.getMaxFill() - steam.getFill() < RESTART_STEAM_ROOM) return "steam";
			if(claddingDamage > 0 || graphiteDamage > 0) return "damage";
			return "none";
		}
		if(peakCladdingTemperature >= TRIP_CLADDING_C) return "temperature";
		if(getPressureBar() >= TRIP_PRESSURE_BAR) return "pressure";
		if(previousPressureBar > 0.0D && previousPressureBar - getPressureBar() >= RAPID_PRESSURE_LOSS_BAR) return "pressure_loss";
		return "none";
	}

	private void checkProtection() {
		String reason = protectionReason(false);
		activeTripInput = reason;
		if(!"none".equals(reason) && (!shutdownLatched || "manual".equals(shutdownReason))) trip(reason);
	}

	private void applyDamageModel() {
		double tempC = peakCladdingTemperature;
		if(tempC > CLADDING_DAMAGE_TEMP_C) {
			double severity = (tempC - CLADDING_DAMAGE_TEMP_C) / 200.0D;
			claddingDamage += (int)Math.ceil(750.0D * severity * severity * getCoreInstabilityMultiplier());
		}
		// Intact CO2 does not oxidize graphite. Structural damage is reserved for
		// hard bulk-core overheating, not a hot fuel-can surface.
		if(getGraphiteHeatC() > 760.0D) graphiteDamage += (int)Math.ceil((getGraphiteHeatC() - 760.0D) * 2.0D);
		claddingDamage = clamp(claddingDamage, 0, MAX_CLADDING_DAMAGE);
		graphiteDamage = clamp(graphiteDamage, 0, MAX_GRAPHITE_DAMAGE);
		airIngress = 0; // Low inventory is not evidence of an oxidant or water leak.
	}

	private boolean hasFuelRod(int id) {
		if(slots[id] != null) {
			if(slots[id].getItem() instanceof ItemZirnoxRod) {
				final EnumZirnoxType num = EnumUtil.grabEnumSafely(EnumZirnoxType.class, slots[id].getItemDamage());

				if(num == null)
					return false;

				return !num.breeding;
			}
		}

		return false;
	}

	private boolean hasBreedingRod(int id) {
		if(slots[id] != null) {
			if(slots[id].getItem() instanceof ItemZirnoxRod) {
				final EnumZirnoxType num = EnumUtil.grabEnumSafely(EnumZirnoxType.class, slots[id].getItemDamage());

				if(num == null)
					return false;

				return num.breeding;
			}
		}

		return false;
	}

	private int getNeighbourCount(int id) {

		int[] neighbours = this.getNeighbouringSlots(id);

		if(neighbours == null)
			return 0;

		int count = 0;

		for(int i = 0; i < neighbours.length; i++) {
			if(hasFuelRod(neighbours[i]))
				count++;
		}

		return count;
	}

	private int getNeighbourAbsorberCount(int id) {

		int[] neighbours = this.getNeighbouringSlots(id);

		if(neighbours == null)
			return 0;

		int count = 0;

		for(int i = 0; i < neighbours.length; i++) {
			if(hasBreedingRod(neighbours[i]))
				count++;
		}

		return count;
	}

	// itemstack in slots[id] has to contain ItemZirnoxRod
	private int decay(int id) {

		final EnumZirnoxType num = EnumUtil.grabEnumSafely(EnumZirnoxType.class, slots[id].getItemDamage());

		if(num == null)
			return 0;

		int neighbourFuel = getNeighbourCount(id);
		int absorberNeighbours = getNeighbourAbsorberCount(id);

		double neutronFlux = neighbourFuel;

		if(!num.breeding)
			neutronFlux += 1.0D;

		// Breeder/target rods absorb neutrons and reduce nearby reactivity.
		neutronFlux -= absorberNeighbours * 0.35D;

		if(neutronFlux <= 0.0D)
			return 0;

		double controlFactor = clampDouble((100.0D - controlRodInsertion) / 100.0D, 0.0D, 1.0D);

		if(controlFactor <= 0.0D)
			return 0;

		double slotFlux = getFluxFactor(id);
		double fuelHeat = getFuelHeatMultiplier(num);

		// Reference-supported negative feedback for natural uranium only. Exotic
		// fuel coefficients are unspecified, not asserted to match real Magnox fuel.
		double temperatureRise = Math.max(0, getGraphiteHeatC() - 300.0D) / 250.0D;
		double feedback = num == EnumZirnoxType.NATURAL_URANIUM_FUEL ?
			0.5D + 0.5D / (1.0D + temperatureRise * temperatureRise) : 1.0D;
		double effectiveFlux = neutronFlux * slotFlux * controlFactor * feedback;

		if(effectiveFlux <= 0.0D)
			return 0;

		// Breeding/target rods should process under flux but should not behave
		// like main power fuel.
		double breedingHeatPenalty = num.breeding ? 0.15D : 1.0D;

		int heatAdded = (int)Math.round(num.heat * effectiveFlux * fuelHeat * breedingHeatPenalty);

		if(heatAdded < 0)
			heatAdded = 0;

		// Heat is credited once by applyDecayHeat after summing fuel power.

		// Lifetime increment is now based on local neutron flux, not merely
		// hard neighbor count. Natural uranium still burns through relatively
		// fast because Magnox has low burnup.
		int lifeIncrements = (int)Math.round(effectiveFlux * getFuelBurnMultiplier(num));

		if(lifeIncrements < 1)
			lifeIncrements = 1;

		for(int i = 0; i < lifeIncrements; i++) {
			ItemZirnoxRod.incrementLifeTime(slots[id]);

			if(ItemZirnoxRod.getLifeTime(slots[id]) > num.maxLife) {
				ItemStack result = fuelMap.get(new ComparableStack(getStackInSlot(id)));

				if(result != null) {
					slots[id] = result.copy();
				} else {
					slots[id] = null;
				}

				break;
			}
		}

		// Exotic fuels are allowed, but punish hot operation.
		if(heatAdded > 0 && getGraphiteHeatC() > CLADDING_DAMAGE_TEMP_C) {
			double instability = getFuelInstabilityMultiplier(num) - 1.0D;

			if(instability > 0.0D) {
				claddingDamage += (int)Math.ceil(instability * heatAdded * 0.03D);
			}
		}

		return heatAdded;
	}

	private double getFluxFactor(int id) {
		if(id >= 0 && id < FLUX.length)
			return FLUX[id];

		return 1.0D;
	}

	private double getFuelHeatMultiplier(EnumZirnoxType type) {
		switch(type) {
			case NATURAL_URANIUM_FUEL:
				return 0.75D;
			case URANIUM_FUEL:
				return 1.00D;
			case TH232:
				return 0.05D;
			case THORIUM_FUEL:
				return 0.65D;
			case MOX_FUEL:
				return 1.30D;
			case PLUTONIUM_FUEL:
				return 1.55D;
			case U233_FUEL:
				return 1.25D;
			case U235_FUEL:
				return 1.40D;
			case LES_FUEL:
				return 1.10D;
			case LITHIUM:
				return 0.02D;
			case ZFB_MOX:
				return 1.35D;
			default:
				return 1.0D;
		}
	}

	private double getFuelBurnMultiplier(EnumZirnoxType type) {
		switch(type) {
			case NATURAL_URANIUM_FUEL:
				return 1.30D;
			case URANIUM_FUEL:
				return 1.15D;
			case TH232:
				return 0.75D;
			case THORIUM_FUEL:
				return 1.05D;
			case MOX_FUEL:
				return 1.55D;
			case PLUTONIUM_FUEL:
				return 1.75D;
			case U233_FUEL:
				return 1.45D;
			case U235_FUEL:
				return 1.60D;
			case LES_FUEL:
				return 1.25D;
			case LITHIUM:
				return 0.70D;
			case ZFB_MOX:
				return 1.60D;
			default:
				return 1.0D;
		}
	}

	private double getFuelInstabilityMultiplier(EnumZirnoxType type) {
		switch(type) {
			case NATURAL_URANIUM_FUEL:
				return 1.00D;
			case URANIUM_FUEL:
				return 1.05D;
			case TH232:
				return 1.00D;
			case THORIUM_FUEL:
				return 1.10D;
			case MOX_FUEL:
				return 1.35D;
			case PLUTONIUM_FUEL:
				return 1.55D;
			case U233_FUEL:
				return 1.25D;
			case U235_FUEL:
				return 1.45D;
			case LES_FUEL:
				return 1.15D;
			case LITHIUM:
				return 1.00D;
			case ZFB_MOX:
				return 1.45D;
			default:
				return 1.0D;
		}
	}

	private double getCoreInstabilityMultiplier() {

		double instability = 0.0D;
		int count = 0;

		for(int i = 0; i < 24; i++) {
			if(slots[i] != null && slots[i].getItem() instanceof ItemZirnoxRod) {
				final EnumZirnoxType num = EnumUtil.grabEnumSafely(EnumZirnoxType.class, slots[i].getItemDamage());

				if(num != null && !num.breeding) {
					instability += getFuelInstabilityMultiplier(num) - 1.0D;
					count++;
				}
			}
		}

		if(count <= 0)
			return 1.0D;

		return 1.0D + instability / count;
	}

	private boolean checkIfMeltdown() {
		if(getPressureBar() >= RUPTURE_PRESSURE_BAR) {
			meltdown(MELTDOWN_OVERPRESSURE);
			return true;
		}
		if(claddingDamage >= MAX_CLADDING_DAMAGE) {
			meltdown(MELTDOWN_CLADDING);
			return true;
		}
		if(graphiteDamage >= MAX_GRAPHITE_DAMAGE) {
			meltdown(MELTDOWN_GRAPHITE);
			return true;
		}
		if(getGraphiteHeatC() >= 800.0D) {
			meltdown(MELTDOWN_OVERHEAT);
			return true;
		}
		return false;
	}

	private void spawnDebris(DebrisType type) {

		EntityZirnoxDebris debris = new EntityZirnoxDebris(worldObj, xCoord + 0.5D, yCoord + 4D, zCoord + 0.5D, type);
		debris.motionX = worldObj.rand.nextGaussian() * 0.75D;
		debris.motionZ = worldObj.rand.nextGaussian() * 0.75D;
		debris.motionY = 0.01D + worldObj.rand.nextDouble() * 1.25D;

		if(type == DebrisType.CONCRETE) {
			debris.motionX *= 0.25D;
			debris.motionY += worldObj.rand.nextDouble();
			debris.motionZ *= 0.25D;
		}

		if(type == DebrisType.EXCHANGER) {
			debris.motionX += 0.5D;
			debris.motionY *= 0.1D;
			debris.motionZ += 0.5D;
		}

		worldObj.spawnEntityInWorld(debris);
	}

	private void zirnoxDebris(double contamination) {
		for(int i = 0; i < 2; i++) spawnDebris(DebrisType.EXCHANGER);
		for(int i = 0; i < 20; i++) {
			spawnDebris(DebrisType.CONCRETE);
			spawnDebris(DebrisType.BLANK);
		}
		for(int i = 0; i < 10; i++) spawnDebris(DebrisType.SHRAPNEL);
		for(int i = 0; i < (int)Math.ceil(10 * contamination); i++) {
			spawnDebris(DebrisType.ELEMENT);
			spawnDebris(DebrisType.GRAPHITE);
		}
	}

	private void meltdown(int type) {
		if(worldObj.isRemote || terminalFailure) return;
		terminalFailure = true;
		isOn = false;
		int fuelCount = 0;
		for(int i = 0; i < 24; i++) if(slots[i] != null) fuelCount++;
		double fuelDamage = Math.max(claddingDamage / (double)MAX_CLADDING_DAMAGE,
			clampDouble((getGraphiteHeatC() - FUEL_DAMAGE_TEMP_C) / 200.0D, 0, 1));
		boolean rupture = type == MELTDOWN_OVERPRESSURE;
		// A rupture breaches the pressure barrier even with intact fuel cans.
		double contamination = fuelCount / 24.0D * (rupture ? 0.15D + 0.85D * fuelDamage : 0.4D * fuelDamage);
		for(int i = 0; i < slots.length; i++) slots[i] = null;
		int metadata = getBlockMetadata();
		if(rupture) {
			worldObj.playSoundEffect(xCoord, yCoord + 2, zCoord, "hbm:block.rbmk_explosion", 10.0F, 0.85F);
			worldObj.createExplosion(null, xCoord, yCoord + 3, zCoord, 5.5F, true);
			zirnoxDebris(contamination);
		}
		int wasteRadius = (int)Math.floor(35 * Math.sqrt(contamination));
		if(wasteRadius >= 4) ExplosionNukeGeneric.waste(worldObj, xCoord, yCoord, zCoord, wasteRadius);
		worldObj.setBlock(xCoord, yCoord, zCoord, ModBlocks.zirnox_destroyed, metadata, 3);
		MultiblockHandlerXR.fillSpace(worldObj, xCoord, yCoord, zCoord, new int[] {1, 0, 2, 2, 2, 2},
			ModBlocks.zirnox_destroyed, ForgeDirection.getOrientation(metadata - BlockDummyable.offset));
		if(worldObj.getTileEntity(xCoord, yCoord, zCoord) instanceof TileEntityZirnoxDestroyed) {
			TileEntityZirnoxDestroyed wreck = (TileEntityZirnoxDestroyed)worldObj.getTileEntity(xCoord, yCoord, zCoord);
			wreck.onFire = false;
			wreck.contaminationScale = contamination;
			wreck.terminalReason = getTerminalReason(type);
			wreck.markDirty();
		}
		List<EntityPlayer> players = worldObj.getEntitiesWithinAABB(EntityPlayer.class,
			AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord + 1, yCoord + 1, zCoord + 1).expand(100, 100, 100));
		for(EntityPlayer player : players) {
			if(rupture) player.triggerAchievement(MainRegistry.achZIRNOXBoom);
			if(contamination > 0 && MobConfig.enableElementals)
				player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).setBoolean("radMark", true);
		}
	}

	private String getTerminalReason(int type) {
		switch(type) {
			case MELTDOWN_OVERPRESSURE:
				return "pressure_rupture";
			case MELTDOWN_CLADDING:
				return "cladding_failure";
			case MELTDOWN_GRAPHITE:
				return "graphite_failure";
			default:
				return "hard_overheating";
		}
	}

	private void updateConnections() {
		for(DirPos pos : getConPos()) {
			this.trySubscribe(water.getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(carbonDioxide.getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}

	private DirPos[] getConPos() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

		return new DirPos[] {
			new DirPos(this.xCoord + rot.offsetX * 3, this.yCoord + 1, this.zCoord + rot.offsetZ * 3, rot),
			new DirPos(this.xCoord + rot.offsetX * 3, this.yCoord + 3, this.zCoord + rot.offsetZ * 3, rot),
			new DirPos(this.xCoord + rot.offsetX * -3, this.yCoord + 1, this.zCoord + rot.offsetZ * -3, rot.getOpposite()),
			new DirPos(this.xCoord + rot.offsetX * -3, this.yCoord + 3, this.zCoord + rot.offsetZ * -3, rot.getOpposite())
		};
	}

	public List<FluidTank> getTanks() {
		List<FluidTank> list = new ArrayList<FluidTank>();

		list.add(steam);
		list.add(carbonDioxide);
		list.add(water);

		return list;
	}

	public AxisAlignedBB getRenderBoundingBox() {
		return AxisAlignedBB.getBoundingBox(xCoord - 2, yCoord, zCoord - 2, xCoord + 3, yCoord + 5, zCoord + 3);
	}

	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return Vec3.createVectorHelper(xCoord - player.posX, yCoord - player.posY, zCoord - player.posZ).lengthVector() < 20;
	}

	@Override
	public void receiveControl(NBTTagCompound data) {
		if(worldObj == null || worldObj.isRemote || terminalFailure) return;

		if(data.hasKey("control")) {
			setControlRodInsertion(isOn ? 100 : 0);
		}

		if(data.hasKey("scram")) {
			scram();
		}

		if(data.hasKey("controlRodInsertion")) {
			setControlRodInsertion(data.getInteger("controlRodInsertion"));
		}

		if(data.hasKey("vent")) {
			ventCarbonDioxide(1000);
		}

		this.markDirty();
	}

	private void ventCarbonDioxide(int amount) {
		if(worldObj == null || worldObj.isRemote || terminalFailure) return;
		int fill = carbonDioxide.getFill();
		int removed = Math.min(fill, Math.max(0, amount));
		// The primary store includes fixed exchanger metal; assign only 1/4 of its
		// energy to the gas at nominal inventory. Vent only that gas's enthalpy.
		double gasShare = 0.25D * Math.min(1.0D, getCO2FillFraction());
		if(fill > 0) primaryEnergy -= primaryEnergy * gasShare * removed / fill;
		carbonDioxide.setFill(fill - removed);
		primaryGasVented += removed;
		updateThermalDisplays();
		updatePressureFromCO2();
		checkProtection();
		markDirty();
	}

	private void trip(String reason) {
		if(worldObj == null || worldObj.isRemote || terminalFailure) return;
		shutdownLatched = true;
		shutdownReason = reason;
		targetControlRodInsertion = 100;
		isOn = false;
		markDirty();
	}

	private void scram() {
		trip("manual");
	}

	private void setControlRodInsertion(int insertion) {
		if(worldObj == null || worldObj.isRemote || terminalFailure) return;
		insertion = clamp(insertion, 0, 100);
		if(insertion == 100) { scram(); return; }
		updatePressureFromCO2();
		String reason = protectionReason(!isOn || shutdownLatched);
		if(!"none".equals(reason)) { trip(reason); return; }
		shutdownLatched = false;
		shutdownReason = "none";
		targetControlRodInsertion = insertion;
		isOn = true;
		markDirty();
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { steam };
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { water, carbonDioxide };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { water, steam, carbonDioxide };
	}

	// ==========================
	// HELPERS
	// ==========================

	private double getHeatC() {
		return primaryEnergy / PRIMARY_CAPACITY + TEMP_BASE_C;
	}

	private double getGraphiteHeatC() {
		return coreEnergy / CORE_CAPACITY + TEMP_BASE_C;
	}

	private double getPressureBar() {
		return pressure * 1.0E-5D * MAX_PRESSURE_BAR;
	}

	private int barToPressure(double bar) {
		return (int)Math.round(bar / MAX_PRESSURE_BAR * maxPressure);
	}

	private double getCO2FillFraction() {
		if(carbonDioxide.getMaxFill() <= 0)
			return 0.0D;

		return Math.max(0.0D, (double)carbonDioxide.getFill() / NOMINAL_CO2_FILL);
	}

	private static int clamp(int value, int min, int max) {
		if(value < min)
			return min;

		if(value > max)
			return max;

		return value;
	}

	private static double clampDouble(double value, double min, double max) {
		if(value < min)
			return min;

		if(value > max)
			return max;

		return value;
	}

	// ==========================
	// OpenComputers
	// ==========================

	@Override
	@Optional.Method(modid = "OpenComputers")
	public String getComponentName() {
		return "zirnox_reactor";
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getTemp(Context context, Arguments args) {
		return new Object[] { heat };
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getTempC(Context context, Arguments args) {
		return new Object[] { getHeatC() };
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getGraphiteTemp(Context context, Arguments args) {
		return new Object[] { graphiteHeat };
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getGraphiteTempC(Context context, Arguments args) {
		return new Object[] { getGraphiteHeatC() };
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getPressure(Context context, Arguments args) {
		return new Object[] { pressure };
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getPressureBar(Context context, Arguments args) {
		return new Object[] { getPressureBar() };
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getWater(Context context, Arguments args) {
		return new Object[] { water.getFill() };
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getSteam(Context context, Arguments args) {
		return new Object[] { steam.getFill() };
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getCarbonDioxide(Context context, Arguments args) {
		return new Object[] { carbonDioxide.getFill() };
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] isActive(Context context, Arguments args) {
		return new Object[] { isOn };
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getControlRodInsertion(Context context, Arguments args) {
		return new Object[] { controlRodInsertion };
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getGraphiteDamage(Context context, Arguments args) {
		return new Object[] { graphiteDamage };
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getCladdingDamage(Context context, Arguments args) {
		return new Object[] { claddingDamage };
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getAirIngress(Context context, Arguments args) {
		return new Object[] { airIngress };
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getDecayHeat(Context context, Arguments args) {
		return new Object[] { decayHeat };
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getActivePower(Context context, Arguments args) {
		return new Object[] { activePower };
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getCooling(Context context, Arguments args) {
		return new Object[] { co2Cooling };
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getInfo(Context context, Arguments args) {
		return new Object[] {
			heat,
			pressure,
			water.getFill(),
			steam.getFill(),
			carbonDioxide.getFill(),
			isOn,
			controlRodInsertion,
			graphiteHeat,
			graphiteDamage,
			claddingDamage,
			airIngress,
			decayHeat,
			activePower,
			co2Cooling,
			shutdownLatched,
			shutdownReason,
			getWaterReserve(),
			shutdownWaterUsed,
			primaryGasVented,
			targetControlRodInsertion,
			peakCladdingTemperature,
			activeTripInput
		};
	}

	@Callback(direct = false, limit = 4)
	@Optional.Method(modid = "OpenComputers")
	public Object[] setActive(Context context, Arguments args) {
		boolean active = args.checkBoolean(0);

		if(active) {
			setControlRodInsertion(0);
		} else {
			scram();
		}

		return new Object[] {};
	}

	@Callback(direct = false, limit = 4)
	@Optional.Method(modid = "OpenComputers")
	public Object[] setControlRodInsertion(Context context, Arguments args) {
		setControlRodInsertion(args.checkInteger(0));
		return new Object[] { controlRodInsertion };
	}

	@Callback(direct = false, limit = 4)
	@Optional.Method(modid = "OpenComputers")
	public Object[] scram(Context context, Arguments args) {
		scram();
		return new Object[] {};
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getShutdownStatus(Context context, Arguments args) {
		return new Object[] {shutdownLatched, shutdownReason, protectionReason(true), getWaterReserve()};
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public String[] methods() {
		return new String[] {
			"getTemp",
			"getTempC",
			"getGraphiteTemp",
			"getGraphiteTempC",
			"getPressure",
			"getPressureBar",
			"getWater",
			"getSteam",
			"getCarbonDioxide",
			"isActive",
			"getControlRodInsertion",
			"getGraphiteDamage",
			"getCladdingDamage",
			"getAirIngress",
			"getDecayHeat",
			"getActivePower",
			"getCooling",
			"getInfo",
			"setActive",
			"setControlRodInsertion",
			"scram",
			"getShutdownStatus"
		};
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public Object[] invoke(String method, Context context, Arguments args) throws Exception {
		switch(method) {
			case ("getTemp"):
				return getTemp(context, args);
			case ("getTempC"):
				return getTempC(context, args);
			case ("getGraphiteTemp"):
				return getGraphiteTemp(context, args);
			case ("getGraphiteTempC"):
				return getGraphiteTempC(context, args);
			case ("getPressure"):
				return getPressure(context, args);
			case ("getPressureBar"):
				return getPressureBar(context, args);
			case ("getWater"):
				return getWater(context, args);
			case ("getSteam"):
				return getSteam(context, args);
			case ("getCarbonDioxide"):
				return getCarbonDioxide(context, args);
			case ("isActive"):
				return isActive(context, args);
			case ("getControlRodInsertion"):
				return getControlRodInsertion(context, args);
			case ("getGraphiteDamage"):
				return getGraphiteDamage(context, args);
			case ("getCladdingDamage"):
				return getCladdingDamage(context, args);
			case ("getAirIngress"):
				return getAirIngress(context, args);
			case ("getDecayHeat"):
				return getDecayHeat(context, args);
			case ("getActivePower"):
				return getActivePower(context, args);
			case ("getCooling"):
				return getCooling(context, args);
			case ("getInfo"):
				return getInfo(context, args);
			case ("setActive"):
				return setActive(context, args);
			case ("setControlRodInsertion"):
				return setControlRodInsertion(context, args);
			case ("scram"):
				return scram(context, args);
			case ("getShutdownStatus"):
				return getShutdownStatus(context, args);
		}

		throw new NoSuchMethodException();
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerReactorZirnox(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIReactorZirnox(player.inventory, this);
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setDouble(CompatEnergyControl.D_HEAT_C, Math.round(heat * 1.0E-5D * TEMP_RANGE_C + TEMP_BASE_C));
		data.setDouble(CompatEnergyControl.D_MAXHEAT_C, Math.round(maxHeat * 1.0E-5D * TEMP_RANGE_C + TEMP_BASE_C));
		data.setLong(CompatEnergyControl.L_PRESSURE_BAR, Math.round(getPressureBar()));
		data.setDouble(CompatEnergyControl.D_CONSUMPTION_MB, output + shutdownWaterUsed);
		data.setDouble(CompatEnergyControl.D_OUTPUT_MB, output);
		data.setBoolean(CompatEnergyControl.B_ACTIVE, isOn);
		data.setDouble(CompatEnergyControl.D_CORE_C, getGraphiteHeatC());
		// Standard tank text is rendered by existing EC versions; custom keys also
		// expose machine-readable protection state without changing EC globally.
		data.setString(CompatEnergyControl.S_TANK, "Magnox: " + (shutdownLatched ? shutdownReason : (isOn ? "running" : "off")));
		data.setBoolean("shutdownLatched", shutdownLatched);
		data.setString("shutdownReason", shutdownReason);
		data.setString("restartBlocker", protectionReason(true));
		data.setInteger("waterReserve", getWaterReserve());
		data.setInteger("shutdownWaterUsed", shutdownWaterUsed);
		data.setInteger("primaryGasVented", primaryGasVented);
	}
}
