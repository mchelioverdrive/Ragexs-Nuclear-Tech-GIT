package com.hbm.tileentity.machine;

import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.handler.pollution.PollutionHandler;
import com.hbm.handler.pollution.PollutionHandler.PollutionType;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.ItemEnums.EnumAshType;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.module.ModuleBurnTime;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachinePolluting;
import com.hbm.util.FurnaceGasEmission;
import com.hbm.util.ItemStackUtil;

import api.hbm.fluid.IFluidStandardSender;
import api.hbm.tile.IHeatSource;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;

public abstract class TileEntityFireboxBase extends TileEntityMachinePolluting implements IFluidStandardSender, IGUIProvider, IHeatSource {

	protected static final int TASK_SIMULATE = 1;
	protected static final int TASK_SLOT_MAIN = 0;

	public int maxBurnTime;
	public int burnTime;
	public int burnHeat;
	public boolean wasOn = false;
	private int playersUsing = 0;

	public float doorAngle = 0;
	public float prevDoorAngle = 0;

	public int heatEnergy;
	private boolean waterlogged;
	private boolean waterloggedInitialized;
	private boolean fuelFingerprintInitialized;
	private int observedFuelFingerprint;


	public TileEntityFireboxBase() {
		super(2, 50);
	}

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if((causes & MachineDirtyCause.LIFECYCLE) != 0) {
			this.refreshWaterlogged(false);
			this.observeFuelInventory();
		}
		if((causes & MachineDirtyCause.INVENTORY) != 0) this.observeFuelInventory();
		if(this.hasContinuousWork() || this.hasFuelItems() || this.hasAdditionalRuntimeWork()) {
			this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_SIMULATE, TASK_SLOT_MAIN);
		}
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_SIMULATE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote) return;
		this.beforeFireboxSimulationTick();
		this.simulateFireboxTick();
		if(this.hasContinuousWork()) this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_SIMULATE, TASK_SLOT_MAIN);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			this.refreshWaterlogged(true);
			int fingerprint = this.fuelInventoryFingerprint();
			if(fuelFingerprintInitialized && fingerprint != observedFuelFingerprint) this.markMachineDirty(MachineDirtyCause.INVENTORY);
			this.observedFuelFingerprint = fingerprint;
			this.fuelFingerprintInitialized = true;
			if(this.hasFuelItems() || this.hasAdditionalRuntimeWork()) this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_SIMULATE, TASK_SLOT_MAIN);
		} else if(cadence == 20) {
			this.networkPackNTIfDirty(50);
		}
	}

	private void refreshWaterlogged(boolean notify) {
		boolean current = this.isWaterlogged();
		boolean changed = waterloggedInitialized && waterlogged != current;
		waterlogged = current;
		waterloggedInitialized = true;
		if(notify && changed) this.markMachineDirty(MachineDirtyCause.ENVIRONMENT);
	}

	private int fuelInventoryFingerprint() {
		int hash = 1;
		for(int slot = 0; slot < 2; slot++) {
			ItemStack stack = slots[slot];
			if(stack == null) {
				hash = 31 * hash;
				continue;
			}
			hash = 31 * hash + Item.getIdFromItem(stack.getItem());
			hash = 31 * hash + stack.getItemDamage();
			hash = 31 * hash + stack.stackSize;
			if(stack.hasTagCompound()) hash = 31 * hash + stack.getTagCompound().hashCode();
		}
		return hash;
	}

	private void observeFuelInventory() {
		this.observedFuelFingerprint = this.fuelInventoryFingerprint();
		this.fuelFingerprintInitialized = true;
	}

	private boolean hasFuelItems() {
		return (slots[0] != null && getModule().getBurnTime(slots[0]) > 0) || (slots[1] != null && getModule().getBurnTime(slots[1]) > 0);
	}

	private boolean hasSmoke() {
		return smoke.getFill() > 0 || smoke_leaded.getFill() > 0 || smoke_poison.getFill() > 0;
	}

	private boolean hasContinuousWork() {
		return burnTime > 0 || heatEnergy > 0 || wasOn || this.hasSmoke() || this.hasAdditionalRuntimeWork();
	}

	/** Hooks coupled variants into their required active simulation cadence. */
	protected void beforeFireboxSimulationTick() { }
	protected boolean hasAdditionalRuntimeWork() { return false; }

	@Override
	public void openInventory() {
		if(!worldObj.isRemote) this.playersUsing++;
	}

	@Override
	public void closeInventory() {
		if(!worldObj.isRemote) this.playersUsing--;
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			this.prevDoorAngle = this.doorAngle;
			float swingSpeed = (doorAngle / 10F) + 3;

			if(this.playersUsing > 0) {
				this.doorAngle += swingSpeed;
			} else {
				this.doorAngle -= swingSpeed;
			}

			this.doorAngle = MathHelper.clamp_float(this.doorAngle, 0F, 135F);

			if(wasOn && worldObj.getTotalWorldTime() % 5 == 0) {
				ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
				double x = xCoord + 0.5 + dir.offsetX;
				double y = yCoord + 0.25;
				double z = zCoord + 0.5 + dir.offsetZ;
				worldObj.spawnParticle("flame", x + worldObj.rand.nextDouble() * 0.5 - 0.25, y + worldObj.rand.nextDouble() * 0.25, z + worldObj.rand.nextDouble() * 0.5 - 0.25, 0, 0, 0);
			}
		}
	}

	private void simulateFireboxTick() {
		int oldBurnTime = burnTime;
		int oldBurnHeat = burnHeat;
		int oldHeatEnergy = heatEnergy;
		boolean oldWasOn = wasOn;
		boolean canOperate = false;

		if(hasSmoke()) {
			for(int i = 2; i < 6; i++) {
				ForgeDirection dir = ForgeDirection.getOrientation(i);
				ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
				for(int j = -1; j <= 1; j++) this.sendSmoke(xCoord + dir.offsetX * 2 + rot.offsetX * j, yCoord, zCoord + dir.offsetZ * 2 + rot.offsetZ * j, dir);
			}
		}

		wasOn = false;
		if(burnTime <= 0) {
			if(!waterlogged) {
				canOperate = breatheAir(0);
				for(int i = 0; i < 2; i++) {
					if(slots[i] == null) continue;
					int baseTime = getModule().getBurnTime(slots[i]);
					if(baseTime <= 0) continue;
					int fuel = (int) (baseTime * getTimeMult());
					TileEntity below = worldObj.getTileEntity(xCoord, yCoord - 1, zCoord);
					if(below instanceof TileEntityAshpit) {
						TileEntityAshpit ashpit = (TileEntityAshpit) below;
						EnumAshType type = getAshFromFuel(slots[i]);
						ashpit.addAsh(type, baseTime);
					}
					this.maxBurnTime = this.burnTime = fuel;
					this.burnHeat = getModule().getBurnHeat(getBaseHeat(), slots[i]);
					slots[i].stackSize--;
					if(slots[i].stackSize == 0) slots[i] = slots[i].getItem().getContainerItem(slots[i]);
					this.onInventorySlotChanged(i);
					this.wasOn = true;
					break;
				}
			}
		} else if(!waterlogged) {
			if(this.heatEnergy < getMaxHeat()) {
				canOperate = breatheAir(worldObj.getTotalWorldTime() % (500 / getBaseHeat()) == 0 ? 1 : 0);
				if(canOperate) {
					burnTime--;
					if(worldObj.getTotalWorldTime() % 20 == 0) this.pollute(PollutionType.SOOT, PollutionHandler.SOOT_PER_SECOND * 3);
				}
			} else {
				canOperate = breatheAir(0);
			}
			if(canOperate) {
				this.wasOn = true;
				FurnaceGasEmission.emitCarbonMonoxide(worldObj, xCoord, yCoord, zCoord, 600);
				if(worldObj.rand.nextInt(15) == 0 && !this.muffled) worldObj.playSoundEffect(xCoord, yCoord, zCoord, "fire.fire", 1.0F, 0.5F + worldObj.rand.nextFloat() * 0.5F);
			}
		}

		if(wasOn) this.heatEnergy = Math.min(this.heatEnergy + this.burnHeat, getMaxHeat());
		else {
			this.heatEnergy = Math.max(this.heatEnergy - Math.max(this.heatEnergy / 1000, 1), 0);
			if(canOperate) this.burnHeat = 0;
		}

		if(oldBurnTime != burnTime || oldBurnHeat != burnHeat || oldHeatEnergy != heatEnergy || oldWasOn != wasOn) this.markNetworkDirty();
		this.observeFuelInventory();
		this.networkPackNTIfDirty(50);
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeInt(maxBurnTime);
		buf.writeInt(burnTime);
		buf.writeInt(burnHeat);
		buf.writeInt(heatEnergy);
		buf.writeInt(playersUsing);
		buf.writeBoolean(wasOn);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		maxBurnTime = buf.readInt();
		burnTime = buf.readInt();
		burnHeat = buf.readInt();
		heatEnergy = buf.readInt();
		playersUsing = buf.readInt();
		wasOn = buf.readBoolean();
	}

	public static EnumAshType getAshFromFuel(ItemStack stack) {

		List<String> names = ItemStackUtil.getOreDictNames(stack);

		for(String name : names) {
			if(name.contains("Coke"))		return EnumAshType.COAL;
			if(name.contains("Coal"))		return EnumAshType.COAL;
			if(name.contains("Lignite"))	return EnumAshType.COAL;
			if(name.startsWith("log"))		return EnumAshType.WOOD;
			if(name.contains("Wood"))		return EnumAshType.WOOD;
			if(name.contains("Sapling"))	return EnumAshType.WOOD;
		}

		return EnumAshType.MISC;
	}

	public abstract ModuleBurnTime getModule();
	public abstract int getBaseHeat();
	public abstract double getTimeMult();
	public abstract int getMaxHeat();

	@Override
	public int[] getAccessibleSlotsFromSide(int meta) {
		return new int[] { 0, 1 };
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemStack) {
		return getModule().getBurnTime(itemStack) > 0;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.maxBurnTime = nbt.getInteger("maxBurnTime");
		this.burnTime = nbt.getInteger("burnTime");
		this.burnHeat = nbt.getInteger("burnHeat");
		this.heatEnergy = nbt.getInteger("heatEnergy");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		nbt.setInteger("maxBurnTime", maxBurnTime);
		nbt.setInteger("burnTime", burnTime);
		nbt.setInteger("burnHeat", burnHeat);
		nbt.setInteger("heatEnergy", heatEnergy);
	}

	@Override
	public int getHeatStored() {
		return heatEnergy;
	}

	@Override
	public void useUpHeat(int heat) {
		this.heatEnergy = Math.max(0, this.heatEnergy - heat);
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
					yCoord + 1,
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
	public FluidTank[] getAllTanks() {
		return new FluidTank[0];
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return this.getSmokeTanks();
	}

	@Override
	public boolean canConnect(FluidType type, ForgeDirection dir) {
		return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN;
	}
}
