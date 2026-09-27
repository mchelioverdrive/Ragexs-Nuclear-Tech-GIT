package com.hbm.tileentity.machine.oil;

import api.hbm.energymk2.EnergyUnits;
import java.util.HashSet;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.*;
import com.hbm.util.BobMathUtil;
import com.hbm.util.Tuple;
import com.hbm.util.Tuple.Triplet;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.IBatteryItem;
import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

public abstract class TileEntityOilDrillBase extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardTransceiver, IConfigurableMachine, IPersistentNBT, IGUIProvider, IUpgradeInfoProvider, IFluidCopiable {
	private static final int TASK_DRILL = 1;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private DirPos[] runtimeConnections;
	private int observedOrientation = Integer.MIN_VALUE;
	private int runtimeAfterburn;
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();


	public int indicator = 0;

	public long energyQuanta;

	public FluidTank[] tanks;

	public TileEntityOilDrillBase() {
		super(8);
		tanks = new FluidTank[2];
		tanks[0] = new FluidTank(Fluids.OIL, 64_000);
		tanks[1] = new FluidTank(Fluids.GAS, 64_000);
		for(FluidTank tank : tanks) this.trackMachineFluidTank(tank);
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.refreshConnections();
		for(FluidTank tank : tanks) this.trackMachineFluidTank(tank);
		if((causes & (MachineDirtyCause.INVENTORY | MachineDirtyCause.UPGRADE | MachineDirtyCause.LIFECYCLE)) != 0) this.refreshUpgrades();
		this.beginMachineFluidMutation();
		try { this.unloadOutputContainers(); }
		finally { this.endMachineFluidMutation(); }
		this.updateConnections();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendUpdate();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.refreshConnections();
		this.updateConnections();
		this.sendUpdate();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_DRILL || taskSlot != 0 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.beginMachineFluidMutation();
		runtimeEnergyMutation = true;
		try { this.runDrillStep(); }
		finally {
			runtimeEnergyMutation = false;
			this.endMachineFluidMutation();
		}
		this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendUpdate();
	}

	private void refreshConnections() {
		int orientation = this.getBlockMetadata();
		if(runtimeConnections == null || observedOrientation != orientation) {
			runtimeConnections = this.getConPos();
			observedOrientation = orientation;
		}
	}

	private void refreshUpgrades() {
		this.upgradeManager.checkSlots(slots, 5, 7);
		this.speedLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.SPEED), 3);
		this.energyLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.POWER), 3);
		this.overLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.OVERDRIVE), 3) + 1;
		this.runtimeAfterburn = Math.min(this.upgradeManager.getLevel(UpgradeType.AFTERBURN), 3);
	}

	private void unloadOutputContainers() {
		this.tanks[0].unloadTank(1, 2, slots);
		this.tanks[1].unloadTank(3, 4, slots);
	}

	private boolean hasBatteryInput() {
		if(energyQuanta >= this.getEnergyCapacityQuanta() || slots[0] == null) return false;
		if(slots[0].getItem() == ModItems.battery_creative || slots[0].getItem() == ModItems.fusion_core_infinite) return true;
		return slots[0].getItem() instanceof IBatteryItem && ((IBatteryItem) slots[0].getItem()).getStoredEnergyQuanta(slots[0]) > 0;
	}

	private void evaluateAndSchedule(long now) {
		boolean outputFlow = tanks[0].getFill() > 0 || tanks[1].getFill() > 0;
		boolean canDrill = energyQuanta >= this.getPowerReqEff() && tanks[0].getFill() < tanks[0].getMaxFill() && tanks[1].getFill() < tanks[1].getMaxFill();
		if(outputFlow || canDrill || this.hasBatteryInput()) this.scheduleMachineTransition(now + 1L, TASK_DRILL, 0);
		else {
			indicator = 2;
			this.cancelMachineTransition(TASK_DRILL, 0);
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		for(int i = 0; i < this.tanks.length; i++)
			this.tanks[i].readFromNBT(nbt, "t" + i);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		for(int i = 0; i < this.tanks.length; i++)
			this.tanks[i].writeToNBT(nbt, "t" + i);
	}

	@Override
	public void writeNBT(NBTTagCompound nbt) {

		boolean empty = energyQuanta == 0;
		for(FluidTank tank : tanks) if(tank.getFill() > 0) empty = false;

		if(!empty) {
			EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
			for(int i = 0; i < this.tanks.length; i++) {
				this.tanks[i].writeToNBT(nbt, "t" + i);
			}
		}
	}

	@Override
	public void readNBT(NBTTagCompound nbt) {
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		for(int i = 0; i < this.tanks.length; i++)
			this.tanks[i].readFromNBT(nbt, "t" + i);
	}

	public int speedLevel;
	public int energyLevel;
	public int overLevel;

	@Override
	public void updateEntity() {
		// Drill accounting and world mutation are owned by MachineRuntime.
	}

	private void runDrillStep() {
			this.unloadOutputContainers();
			int toBurn = Math.min(tanks[1].getFill(), runtimeAfterburn * 10);

			if(toBurn > 0) {
				tanks[1].setFill(tanks[1].getFill() - toBurn);
				this.setStoredEnergyQuanta(this.energyQuanta + toBurn * 5);

				if(this.energyQuanta > this.getEnergyCapacityQuanta())
					this.setStoredEnergyQuanta(this.getEnergyCapacityQuanta());
			}

			this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 0, energyQuanta, this.getEnergyCapacityQuanta()));

			for(DirPos pos : runtimeConnections) {
				if(tanks[0].getFill() > 0) this.sendFluid(tanks[0], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
				if(tanks[1].getFill() > 0) this.sendFluid(tanks[1], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			}

			if(this.energyQuanta >= this.getPowerReqEff() && this.tanks[0].getFill() < this.tanks[0].getMaxFill() && this.tanks[1].getFill() < this.tanks[1].getMaxFill()) {

				this.setStoredEnergyQuanta(this.energyQuanta - this.getPowerReqEff());

				if(worldObj.getTotalWorldTime() % getDelayEff() == 0) {
					this.indicator = 0;

					for(int y = yCoord - 1; y >= getDrillDepth(); y--) {

						if(worldObj.getBlock(xCoord, y, zCoord) != ModBlocks.oil_pipe) {

							if(trySuck(y)) {
								break;
							} else {
								tryDrill(y);
								break;
							}
						}

						if(y == getDrillDepth())
							this.indicator = 1;
					}
				}

			} else {
				this.indicator = 2;
			}

	}

	public void sendUpdate() {
		NBTTagCompound data = new NBTTagCompound();
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		data.setInteger("indicator", this.indicator);
		for(int i = 0; i < tanks.length; i++) tanks[i].writeToNBT(data, "t" + i);
		this.networkPack(data, 25);
	}

	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.indicator = nbt.getInteger("indicator");
		for(int i = 0; i < tanks.length; i++) tanks[i].readFromNBT(nbt, "t" + i);
	}

	public boolean canPump() {
		return true;
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack stack) {
		super.setInventorySlotContents(i, stack);

		if(stack != null && i >= 5 && i <= 7 && stack.getItem() instanceof ItemMachineUpgrade)
			worldObj.playSoundEffect(xCoord + 0.5, yCoord + 1.5, zCoord + 0.5, "hbm:item.upgradePlug", 1.0F, 1.0F);
	}

	public int getPowerReqEff() {
		int req = this.getPowerReq();
		return (req + (req / 4 * this.speedLevel) - (req / 4 * this.energyLevel)) * this.overLevel;
	}

	public int getDelayEff() {
		int delay = getDelay();
		return Math.max((delay - (delay / 4 * this.speedLevel) + (delay / 10 * this.energyLevel)) / this.overLevel, 1);
	}

	public abstract int getPowerReq();
	public abstract int getDelay();

	public void tryDrill(int y) {
		Block b = worldObj.getBlock(xCoord, y, zCoord);

		if(b == Blocks.bedrock) {
			this.indicator = 0;
			return;
		}

		if(b.getExplosionResistance(null) < 1000) {
			onDrill(y);
			worldObj.setBlock(xCoord, y, zCoord, ModBlocks.oil_pipe);
		} else {
			this.indicator = 2;
		}
	}

	public void onDrill(int y) { }

	public int getDrillDepth() {
		//tiering shit so ores have to be enabled award
		//fuck you we milk regular bedrock here sir
		return 0;
	}

	public boolean trySuck(int y) {

		Block b = worldObj.getBlock(xCoord, y, zCoord);

		if(!canSuckBlock(b))
			return false;

		if(!this.canPump())
			return true;

		trace.clear();

		return suckRec(xCoord, y, zCoord, 0);
	}

	public boolean canSuckBlock(Block b) {
		return b == ModBlocks.ore_oil || b == ModBlocks.ore_oil_empty || b == ModBlocks.ore_gas || b == ModBlocks.ore_gas_empty || b == Blocks.bedrock;
	}

	protected HashSet<Tuple.Triplet<Integer, Integer, Integer>> trace = new HashSet();

	public boolean suckRec(int x, int y, int z, int layer) {

		Triplet<Integer, Integer, Integer> pos = new Triplet(x, y, z);

		if(trace.contains(pos))
			return false;

		trace.add(pos);

		if(layer > 64)
			return false;

		Block b = worldObj.getBlock(x, y, z);

		if(b == ModBlocks.ore_oil || b == Blocks.bedrock || b == ModBlocks.ore_gas) {
			doSuck(x, y, z);
			return true;
		}

		if(b == ModBlocks.ore_oil_empty || b == ModBlocks.ore_gas_empty) {
			ForgeDirection[] dirs = BobMathUtil.getShuffledDirs();

			for(ForgeDirection dir : dirs) {
				if(suckRec(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ, layer + 1))
					return true;
			}
		}

		return false;
	}

	public void doSuck(int x, int y, int z) {
		Block b = worldObj.getBlock(x, y, z);

		if(b == ModBlocks.ore_oil || b == ModBlocks.ore_gas || b == Blocks.bedrock) {
			onSuck(x, y, z);
		}
	}

	public abstract void onSuck(int x, int y, int z);

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override
	public long getStoredEnergyQuanta() {
		return this.energyQuanta;
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return TileEntity.INFINITE_EXTENT_AABB;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return tanks;
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[0];
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	public abstract DirPos[] getConPos();

	protected final DirPos[] getRuntimeConnections() {
		this.refreshConnections();
		return runtimeConnections;
	}

	protected void updateConnections() {
		for(DirPos pos : getRuntimeConnections()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED || type == UpgradeType.POWER || type == UpgradeType.OVERDRIVE || type == UpgradeType.AFTERBURN;
	}

	@Override
	public int getMaxLevel(UpgradeType type) {
		if(type == UpgradeType.SPEED) return 3;
		if(type == UpgradeType.POWER) return 3;
		if(type == UpgradeType.AFTERBURN) return 3;
		if(type == UpgradeType.OVERDRIVE) return 3;
		return 0;
	}

	@Override
	public FluidTank getTankToPaste() {
		return null;
	}
}
