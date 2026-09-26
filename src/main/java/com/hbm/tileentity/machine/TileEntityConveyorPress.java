package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.List;

import com.hbm.entity.item.EntityMovingItem;
import com.hbm.inventory.recipes.PressRecipes;
import com.hbm.items.machine.ItemStamp;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityConveyorPress extends TileEntityMachineBase implements IEnergyReceiverMK2 {

	private static final int TASK_PRESS = 1;

	public int usage = 100;
	public long energyQuanta = 0;
	public final static long maxPower = 50000;

	public double speed = 0.125;
	public double press;
	public double renderPress;
	public double lastPress;
	private double syncPress;
	private int turnProgress;
	protected boolean isRetracting = false;
	private int delay;
	private boolean runtimeEnergyMutation;
	private boolean stampFingerprintInitialized;
	private int observedStampFingerprint;
	
	public ItemStack syncStack;

	public TileEntityConveyorPress() {
		super(1);
	}

	@Override
	public String getName() {
		return "";
	}

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		this.observeStampFingerprint();
		if(this.shouldRunPress()) this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_PRESS, 0);
		this.sendStateUpdate();
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_PRESS || taskSlot != 0 || worldObj == null || worldObj.isRemote) return;
		long oldEnergy = energyQuanta;
		double oldPress = press;
		int oldStamp = this.stampFingerprint();
		this.simulatePressTick();
		if(oldEnergy != energyQuanta || oldPress != press || oldStamp != this.stampFingerprint()) this.sendStateUpdate();
		if(this.shouldRunPress()) this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_PRESS, 0);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			int fingerprint = this.stampFingerprint();
			if(stampFingerprintInitialized && fingerprint != observedStampFingerprint) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
			this.observedStampFingerprint = fingerprint;
			this.stampFingerprintInitialized = true;
		} else if(cadence == 20) {
			this.updateConnections();
			this.sendStateUpdate();
		}
	}

	private boolean shouldRunPress() {
		if(energyQuanta < usage) return false;
		return isRetracting ? press > 0D : slots[0] != null;
	}

	private void simulatePressTick() {
		if(delay <= 0) {
			if(isRetracting) {
				if(this.canRetract()) {
					this.press -= speed;
					this.consumeOperatingEnergy();
					if(press <= 0) {
						press = 0;
						isRetracting = false;
						delay = 0;
					}
				}
			} else if(this.canExtend()) {
				this.press += speed;
				this.consumeOperatingEnergy();
				if(press >= 1) {
					press = 1;
					isRetracting = true;
					delay = 5;
					this.process();
				}
			}
		} else {
			delay--;
		}
	}

	private void consumeOperatingEnergy() {
		runtimeEnergyMutation = true;
		try {
			this.setStoredEnergyQuanta(this.energyQuanta - this.usage);
		} finally {
			runtimeEnergyMutation = false;
		}
	}

	private void sendStateUpdate() {
		if(worldObj == null || worldObj.isRemote) return;
		NBTTagCompound data = new NBTTagCompound();
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		data.setDouble("press", press);
		if(slots[0] != null) {
			NBTTagCompound stack = new NBTTagCompound();
			slots[0].writeToNBT(stack);
			data.setTag("stack", stack);
		}
		this.networkPack(data, 50);
	}

	private int stampFingerprint() {
		ItemStack stack = slots[0];
		if(stack == null) return 0;
		int hash = Item.getIdFromItem(stack.getItem());
		hash = 31 * hash + stack.getItemDamage();
		hash = 31 * hash + stack.stackSize;
		if(stack.hasTagCompound()) hash = 31 * hash + stack.getTagCompound().hashCode();
		return hash;
	}

	private void observeStampFingerprint() {
		this.observedStampFingerprint = this.stampFingerprint();
		this.stampFingerprintInitialized = true;
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			
			// approach-based interpolation, GO!
			this.lastPress = this.renderPress;
			
			if(this.turnProgress > 0) {
				this.renderPress = this.renderPress + ((this.syncPress - this.renderPress) / (double) this.turnProgress);
				--this.turnProgress;
			} else {
				this.renderPress = this.syncPress;
			}
		}
	}
	
	protected void updateConnections() {
		for(DirPos pos : getConPos()) this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}
	
	protected DirPos[] getConPos() {
		return new DirPos[] {
				new DirPos(xCoord + 1, yCoord, zCoord, Library.POS_X),
				new DirPos(xCoord - 1, yCoord, zCoord, Library.NEG_X),
				new DirPos(xCoord, yCoord, zCoord + 1, Library.POS_Z),
				new DirPos(xCoord, yCoord, zCoord - 1, Library.NEG_Z),
		};
	}
	
	public boolean canExtend() {
		
		if(this.energyQuanta < usage) return false;
		if(slots[0] == null) return false;
		
		List<EntityMovingItem> items = worldObj.getEntitiesWithinAABB(EntityMovingItem.class, AxisAlignedBB.getBoundingBox(xCoord, yCoord + 1, zCoord, xCoord + 1, yCoord + 1.5, zCoord + 1));
		if(items.isEmpty()) return false;
		
		for(EntityMovingItem item : items) {
			ItemStack stack = item.getItemStack();
			if(PressRecipes.getOutput(stack, slots[0]) != null && stack.stackSize == 1) {
				
				double d0 = 0.35;
				double d1 = 0.65;
				if(item.posX > xCoord + d0 && item.posX < xCoord + d1 && item.posZ > zCoord + d0 && item.posZ < zCoord + d1) {
					item.setPosition(xCoord + 0.5, item.posY, zCoord + 0.5);
				}
				
				return true;
			}
		}
		
		return false;
	}
	
	public void process() {
		
		List<EntityMovingItem> items = worldObj.getEntitiesWithinAABB(EntityMovingItem.class, AxisAlignedBB.getBoundingBox(xCoord, yCoord + 1, zCoord, xCoord + 1, yCoord + 1.5, zCoord + 1));
		
		for(EntityMovingItem item : items) {
			ItemStack stack = item.getItemStack();
			ItemStack output = PressRecipes.getOutput(stack, slots[0]);
			
			if(output != null && stack.stackSize == 1) {
				item.setDead();
				EntityMovingItem out = new EntityMovingItem(worldObj);
				out.setPosition(item.posX, item.posY, item.posZ);
				out.setItemStack(output.copy());
				worldObj.spawnEntityInWorld(out);
			}
		}
		
		this.worldObj.playSoundEffect(this.xCoord, this.yCoord, this.zCoord, "hbm:block.pressOperate", getVolume(1.5F), 1.0F);
		
		if(slots[0].getMaxDamage() != 0) {
			slots[0].setItemDamage(slots[0].getItemDamage() + 1);
			if(slots[0].getItemDamage() >= slots[0].getMaxDamage()) {
				slots[0] = null;
			}
			this.onInventorySlotChanged(0);
		}
	}
	
	public boolean canRetract() {
		if(this.energyQuanta < usage) return false;
		return true;
	}
	
	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.syncPress = nbt.getInteger("press");
		
		if(nbt.hasKey("stack")) {
			NBTTagCompound stack = nbt.getCompoundTag("stack");
			this.syncStack = ItemStack.loadItemStackFromNBT(stack);
		} else {
			this.syncStack = null;
		}
		
		this.turnProgress = 2;
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack stack) {
		return stack.getItem() instanceof ItemStamp;
	}
	
	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[] { 0 };
	}

	@Override
	public boolean canInsertItem(int i, ItemStack itemStack, int j) {
		return this.isItemValidForSlot(i, itemStack);
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	public long getOperatingPowerWatts() {
		return EnergyUnits.quantaPerTickToWatts(this.usage);
	}

	@Override
	public boolean canConnect(ForgeDirection dir) {
		return dir != ForgeDirection.DOWN;
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.press = nbt.getDouble("press");
		this.isRetracting = nbt.hasKey("retracting") ? nbt.getBoolean("retracting") : this.press >= 1D;
		this.delay = Math.max(0, nbt.getInteger("delay"));
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setDouble("press", press);
		nbt.setBoolean("retracting", isRetracting);
		nbt.setInteger("delay", delay);
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
					yCoord + 3,
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
}
