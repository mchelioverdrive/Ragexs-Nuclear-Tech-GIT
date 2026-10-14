package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.realsim.Combustion;
import com.hbm.realsim.FuelCredit;
import com.hbm.realsim.GeneratorStats;
import com.hbm.realsim.OutputBudget;
import com.hbm.handler.pollution.PollutionHandler;
import com.hbm.handler.pollution.PollutionHandler.PollutionType;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.OreDictManager.DictFrame;
import com.hbm.inventory.container.ContainerMachineWoodBurner;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineWoodBurner;
import com.hbm.items.ModItems;
import com.hbm.items.ItemEnums.EnumAshType;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.module.ModuleBurnTime;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.CompatEnergyControl;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.fluid.IFluidStandardReceiver;
import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineWoodBurner extends TileEntityMachineBase implements IFluidStandardReceiver, IControlReceiver, IEnergyProviderMK2, IGUIProvider, IInfoProviderEC, IFluidCopiable, com.hbm.realsim.IMachineInfo {
	private static final int TASK_GENERATE = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private DirPos[] runtimeConnections;
	private int observedOrientation = Integer.MIN_VALUE;

	public long energyQuanta;
	public static final long maxPower = 100_000;
	public int burnTime;
	public int maxBurnTime;
	public boolean liquidBurn = false;
	public boolean isOn = false;
	protected int powerGen = 0;

	public FluidTank tank;
	public static final long ratedPowerWatts = 40_000L;
	public static final int electricalEfficiencyPpm = 70_000;
	public final FuelCredit fuelCredit = new FuelCredit();
	public final GeneratorStats generatorStats = new GeneratorStats();
	private final OutputBudget outputBudget = new OutputBudget();
	private long lastGenerationTick = Long.MIN_VALUE;

	public static ModuleBurnTime burnModule = new ModuleBurnTime().setLogTimeMod(4).setWoodTimeMod(2);

	public int ashLevelWood;
	public int ashLevelCoal;
	public int ashLevelMisc;

	public TileEntityMachineWoodBurner() {
		super(6);
		this.tank = new FluidTank(Fluids.WOODOIL, 100).withDefaultCapacity();
		this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.machineWoodBurner";
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			if(powerGen > 0) {
				ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
				ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
				worldObj.spawnParticle("smoke", xCoord + 0.5 - dir.offsetX + rot.offsetX, yCoord + 4, zCoord + 0.5 - dir.offsetZ + rot.offsetZ, 0, 0.05, 0);
			}
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.beginMachineFluidMutation();
		try {
			tank.setType(2, slots);
			tank.loadTank(3, 4, slots);
		} finally {
			this.endMachineFluidMutation();
		}
		this.refreshRuntimeConnections();
		if((causes & MachineDirtyCause.LIFECYCLE) != 0) this.updatePowerConnections();
		this.subscribeToFuel();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNT(25);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.refreshRuntimeConnections();
		this.updatePowerConnections();
		this.subscribeToFuel();
		this.networkPackNT(25);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_GENERATE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		if(lastGenerationTick == worldObj.getTotalWorldTime()) return;
		lastGenerationTick = worldObj.getTotalWorldTime();
		powerGen = 0;
		generatorStats.idle();
		this.beginMachineFluidMutation();
		runtimeEnergyMutation = true;
		try {
			tank.setType(2, slots);
			tank.loadTank(3, 4, slots);
			long charge = Math.min(energyQuanta, getMaxOutputQuantaPerTick());
			this.extractEnergyQuanta(charge - Library.chargeItemsFromTE(slots, 5, charge, charge));
			if(energyQuanta > 0L) for(DirPos pos : runtimeConnections) this.providePowerToDirectReceiver(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			long demand = Math.min(ratedPowerWatts / 10L, Math.max(0L, maxPower - energyQuanta));
			if(isOn && demand > 0 && (fuelCredit.hasCredit() || liquidBurn && tank.getFill() > 0 && Combustion.isBurnerFuel(tank.getTankType()) || !liquidBurn && Combustion.solidChemicalMicrojoules(slots[0]) > 0) && breatheAir(1)) {
				if(!fuelCredit.hasCredit() && !liquidBurn && slots[0] != null) {
					long chemical = Combustion.solidChemicalMicrojoules(slots[0]);
					int burn = burnModule.getBurnTime(slots[0]);
					if(chemical > 0) {
						EnumAshType type = TileEntityFireboxBase.getAshFromFuel(slots[0]);
						if(type == EnumAshType.WOOD) ashLevelWood += burn;
						if(type == EnumAshType.COAL) ashLevelCoal += burn;
						if(type == EnumAshType.MISC) ashLevelMisc += burn;
						int threshold = 2000;
						if(processAsh(ashLevelWood, EnumAshType.WOOD, threshold)) ashLevelWood -= threshold;
						if(processAsh(ashLevelCoal, EnumAshType.COAL, threshold)) ashLevelCoal -= threshold;
						if(processAsh(ashLevelMisc, EnumAshType.MISC, threshold)) ashLevelMisc -= threshold;
						String identity = Combustion.solidIdentity(slots[0]);
						ItemStack container = slots[0].getItem().getContainerItem(slots[0]);
						this.decrStackSize(0, 1);
						if(slots[0] == null && container != null) slots[0] = container.copy();
						fuelCredit.reserve(identity, chemical, 1, electricalEfficiencyPpm);
						this.maxBurnTime = (int) (fuelCredit.usefulMicrojoules() / 500_000L / (ratedPowerWatts / 10L));
						this.markChanged();
					}
				}
				powerGen = (int) (liquidBurn ? Combustion.generate(tank, fuelCredit, demand, electricalEfficiencyPpm) : fuelCredit.release(demand));
				generatorStats.record(powerGen, fuelCredit);
				if(powerGen > 0 && worldObj.getTotalWorldTime() % 20 == 0) PollutionHandler.incrementPollution(worldObj, xCoord, yCoord, zCoord, PollutionType.SOOT, (float) (PollutionHandler.SOOT_PER_SECOND * powerGen / (double) (ratedPowerWatts / 10L)));
			}
			burnTime = (int) (fuelCredit.usefulMicrojoules() / 500_000L / (ratedPowerWatts / 10L));
			this.setStoredEnergyQuanta(energyQuanta + powerGen);
		} finally {
			runtimeEnergyMutation = false;
			this.endMachineFluidMutation();
		}
		this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNT(25);
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		boolean solidFuelReady = !liquidBurn && isOn && Combustion.solidChemicalMicrojoules(slots[0]) > 0;
		boolean solidBurnActive = fuelCredit.hasCredit() && isOn && energyQuanta < maxPower;
		boolean liquidBurnActive = liquidBurn && tank.getFill() > 0 && isOn && energyQuanta < maxPower && Combustion.isBurnerFuel(tank.getTankType());
		if(solidFuelReady || solidBurnActive || liquidBurnActive || energyQuanta > 0L) this.scheduleMachineTransition(now + 1L, TASK_GENERATE, TASK_SLOT_MAIN);
		else {
			powerGen = 0;
			this.cancelMachineTransition(TASK_GENERATE, TASK_SLOT_MAIN);
		}
	}

	private void refreshRuntimeConnections() {
		int orientation = this.getBlockMetadata();
		if(runtimeConnections == null || observedOrientation != orientation) {
			runtimeConnections = this.buildConnections();
			observedOrientation = orientation;
		}
	}

	private void subscribeToFuel() {
		for(DirPos pos : runtimeConnections) this.trySubscribe(tank.getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}

	private void updatePowerConnections() {
		for(DirPos pos : runtimeConnections) this.registerPowerConnection(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(energyQuanta);
		buf.writeInt(burnTime);
		buf.writeInt(powerGen);
		buf.writeInt(maxBurnTime);
		buf.writeBoolean(isOn);
		buf.writeBoolean(liquidBurn);

		tank.serialize(buf);
		generatorStats.write(buf, fuelCredit, ratedPowerWatts, electricalEfficiencyPpm);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		energyQuanta = buf.readLong();
		burnTime = buf.readInt();
		powerGen = buf.readInt();
		maxBurnTime = buf.readInt();
		isOn = buf.readBoolean();
		liquidBurn = buf.readBoolean();

		tank.deserialize(buf);
		generatorStats.read(buf, fuelCredit);
	}

	private DirPos[] getConPos() {
		this.refreshRuntimeConnections();
		return runtimeConnections;
	}

	private DirPos[] buildConnections() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
		return new DirPos[] {
			new DirPos(xCoord - dir.offsetX * 2, yCoord, zCoord - dir.offsetZ * 2, dir.getOpposite()),
			new DirPos(xCoord - dir.offsetX * 2 + rot.offsetX, yCoord, zCoord - dir.offsetZ * 2 + rot.offsetZ, dir.getOpposite())
		};
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.burnTime = nbt.getInteger("burnTime");
		this.maxBurnTime = nbt.getInteger("maxBurnTime");
		this.isOn = nbt.getBoolean("isOn");
		this.liquidBurn = nbt.getBoolean("liquidBurn");
		tank.readFromNBT(nbt, "t");
		fuelCredit.read(nbt);
		outputBudget.read(nbt);
		if(!nbt.hasKey("realSimFuel") && burnTime > 0) fuelCredit.reserve("legacy_solid", burnTime * 50_000_000L, 1, 1_000_000);
		lastGenerationTick = nbt.hasKey("realSimGenerationTick") ? nbt.getLong("realSimGenerationTick") : Long.MIN_VALUE;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setInteger("burnTime", burnTime);
		nbt.setInteger("maxBurnTime", maxBurnTime);
		nbt.setBoolean("isOn", isOn);
		nbt.setBoolean("liquidBurn", liquidBurn);
		tank.writeToNBT(nbt, "t");
		fuelCredit.write(nbt);
		outputBudget.write(nbt);
		nbt.setLong("realSimGenerationTick", lastGenerationTick);
	}

	protected boolean processAsh(int level, EnumAshType type, int threshold) {

		if(level >= threshold) {
			if(slots[1] == null) {
				slots[1] = DictFrame.fromOne(ModItems.powder_ash, type);
				return true;
			} else if(slots[1].stackSize < slots[1].getMaxStackSize() && slots[1].getItem() == ModItems.powder_ash && slots[1].getItemDamage() == type.ordinal()) {
				slots[1].stackSize++;
				return true;
			}
		}

		return false;
	}

	@Override
	public void receiveControl(NBTTagCompound data) {
		if(data.hasKey("toggle")) {
			this.isOn = !this.isOn;
			this.markChanged();
		}
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
		if(data.hasKey("switch")) {
			this.liquidBurn = !this.liquidBurn;
			this.markChanged();
		}
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return this.isUseableByPlayer(player);
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineWoodBurner(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineWoodBurner(player.inventory, this);
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int meta) {
		return new int[] { 0, 1 };
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemStack) {
		return i == 0 && Combustion.solidChemicalMicrojoules(itemStack) > 0;
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack itemStack, int side) {
		return slot == 1;
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		this.markDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}
	@Override public long getMaxOutputQuantaPerTick() { return outputBudget.available(worldObj == null ? 0 : worldObj.getTotalWorldTime(), ratedPowerWatts / 10L); }
	@Override public void extractEnergyQuanta(long amount) {
		long extracted = Math.max(0L, Math.min(amount, Math.min(energyQuanta, getMaxOutputQuantaPerTick())));
		outputBudget.use(worldObj == null ? 0 : worldObj.getTotalWorldTime(), ratedPowerWatts / 10L, extracted);
		this.setStoredEnergyQuanta(energyQuanta - extracted);
	}

	@Override
	public boolean canConnect(ForgeDirection dir) {
		ForgeDirection rot = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
		return dir == rot.getOpposite();
	}

	@Override
	public boolean canConnect(FluidType type, ForgeDirection dir) {
		ForgeDirection rot = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
		return dir == rot.getOpposite();
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] {tank};
	}

	@Override
	public boolean isFluidDemandObservable() { return true; }

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tank};
	}

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
				xCoord - 1,
				yCoord,
				zCoord - 1,
				xCoord + 2,
				yCoord + 6,
				zCoord + 2
			);
		}

		return bb;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, generatorStats.outputQuanta > 0);
		if(this.liquidBurn) data.setDouble(CompatEnergyControl.D_CONSUMPTION_MB, generatorStats.fuelUnitsPerSecond / 20D);
		data.setDouble(CompatEnergyControl.D_OUTPUT_HE, EnergyUnits.quantaToLegacyHe(generatorStats.outputQuanta));
	}

	public long getPowerOutputWatts() {
		return EnergyUnits.quantaPerTickToWatts(powerGen);
	}

	@Override
	public FluidTank getTankToPaste() {
		return tank;
	}

    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        lines.add("Deprecated generator; use Stirling or early steam for new starter plants");
        lines.add("Rated electrical output: " + com.hbm.realsim.MachineInfo.watts(generatorStats.ratedWatts(ratedPowerWatts)));
        if(live) { String[] status = generatorStats.info(ratedPowerWatts, fuelCredit, tank.getTankType().getName(), tank.getFill(), electricalEfficiencyPpm, false); for(int i = 1; i < status.length; i++) lines.add(status[i]); }
        lines.add(String.format(java.util.Locale.US, "Installed fuel efficiency: %.1f%%", generatorStats.ratedEfficiency(electricalEfficiencyPpm) / 10000D));
    }
}
