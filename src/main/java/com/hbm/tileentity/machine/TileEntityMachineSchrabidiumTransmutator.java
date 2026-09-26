package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.config.VersatileConfig;
import com.hbm.inventory.OreDictManager;
import com.hbm.inventory.container.ContainerMachineSchrabidiumTransmutator;
import com.hbm.inventory.gui.GUIMachineSchrabidiumTransmutator;
import com.hbm.inventory.recipes.MachineRecipes;
import com.hbm.items.ModItems;
import com.hbm.lib.Library;
import com.hbm.main.MainRegistry;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyReceiverMK2;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineSchrabidiumTransmutator extends TileEntityMachineBase implements IEnergyReceiverMK2, IGUIProvider {

	public long energyQuanta = 0;
	public int process = 0;
	public static final long maxPower = 5000000;
	public static final int processSpeed = 600;
	
	private AudioWrapper audio;
	private boolean runtimeInitialized;
	private boolean runtimeActive;
	private boolean runtimeEnergyMutation;
	private long lastAccountTick;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private static final int TASK_ACCOUNT = 1;
	private static final int TASK_SLOT_MAIN = 0;

	private static final int[] slots_io = new int[] { 0, 1, 2, 3 };

	public TileEntityMachineSchrabidiumTransmutator() {
		super(4);
	}

	@Override
	public String getName() {
		return "container.machine_schrabidium_transmutator";
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack stack) {
		switch (i) {
		case 0:
			if (MachineRecipes.mODE(stack, OreDictManager.U.ingot()))
				return true;
			break;
		case 2:
			if (stack.getItem() == ModItems.redcoil_capacitor || stack.getItem() == ModItems.euphemium_capacitor)
				return true;
			break;
		case 3:
			if (stack.getItem() instanceof IBatteryItem)
				return true;
			break;
		}
		return false;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		process = nbt.getInteger("process");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setInteger("process", process);
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int p_94128_1_) {
		return slots_io;
	}

	@Override
	public boolean canExtractItem(int i, ItemStack stack, int j) {
		
		if(stack.getItem() == ModItems.euphemium_capacitor) return false;
		
		if(i == 2 && stack.getItem() != null && (stack.getItem() == ModItems.redcoil_capacitor && stack.getItemDamage() == stack.getMaxDamage())) {
			return true;
		}

		if(i == 1) {
			return true;
		}

		if(i == 3) {
			if(stack.getItem() instanceof IBatteryItem && ((IBatteryItem) stack.getItem()).getStoredEnergyQuanta(stack) == 0)
				return true;
		}

		return false;
	}

	public long getPowerScaled(long i) {
		return (energyQuanta * i) / maxPower;
	}

	public int getProgressScaled(int i) {
		return (process * i) / processSpeed;
	}

	public boolean canProcess() {
		if (energyQuanta >= 4990000 && slots[0] != null && MachineRecipes.mODE(slots[0], OreDictManager.U.ingot()) && slots[2] != null
				&& (slots[2].getItem() == ModItems.redcoil_capacitor && slots[2].getItemDamage() < slots[2].getMaxDamage() || slots[2].getItem() == ModItems.euphemium_capacitor)
				&& (slots[1] == null || (slots[1] != null && slots[1].getItem() == VersatileConfig.getTransmutatorItem()
						&& slots[1].stackSize < slots[1].getMaxStackSize()))) {
			return true;
		}
		return false;
	}

	public boolean isProcessing() {
		return process > 0;
	}

	private void completeOperation() {
		if(!this.canProcess()) {
			process = 0;
			return;
		}
		this.setStoredEnergyQuanta(0);
		process = 0;
		slots[0].stackSize--;
		if(slots[0].stackSize <= 0) slots[0] = null;
		if(slots[1] == null) slots[1] = new ItemStack(VersatileConfig.getTransmutatorItem());
		else slots[1].stackSize++;
		if(slots[2] != null && slots[2].getItem() == ModItems.redcoil_capacitor) slots[2].setItemDamage(slots[2].getItemDamage() + 1);
		this.worldObj.playSoundEffect(this.xCoord, this.yCoord, this.zCoord, "ambient.weather.thunder", 10000.0F,
				0.8F + this.worldObj.rand.nextFloat() * 0.2F);
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[3] == null) return false;
		if(slots[3].getItem() == ModItems.battery_creative || slots[3].getItem() == ModItems.fusion_core_infinite) return true;
		if(!(slots[3].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[3].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[3]) > 0;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		int previousProgress = process;
		boolean eligible = this.canProcess();
		if(eligible) {
			if(!runtimeActive) {
				runtimeActive = true;
				lastAccountTick = now;
			}
		} else {
			if(process != 0) process = 0;
			runtimeActive = false;
		}
		if(previousProgress != process) this.markDirty();
		if(this.hasBatteryWork()) {
			this.scheduleMachineTransition(now + 1L, TASK_ACCOUNT, TASK_SLOT_MAIN);
		} else if(eligible) {
			this.scheduleMachineTransition(now + (5L - now % 5L), TASK_ACCOUNT, TASK_SLOT_MAIN);
		} else {
			this.cancelMachineTransition(TASK_ACCOUNT, TASK_SLOT_MAIN);
		}
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		data.setInteger("progress", process);
		this.networkPack(data, 50);
	}

	private int inventoryFingerprint() {
		int hash = 1;
		for(ItemStack stack : slots) {
			hash = 31 * hash + (stack == null ? 0 : System.identityHashCode(stack));
			if(stack != null) {
				hash = 31 * hash + stack.stackSize;
				hash = 31 * hash + stack.getItemDamage();
				hash = 31 * hash + (stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode());
			}
		}
		return hash;
	}

	private boolean observeInventoryFingerprint() {
		int current = this.inventoryFingerprint();
		boolean changed = inventoryFingerprintInitialized && current != observedInventoryFingerprint;
		observedInventoryFingerprint = current;
		inventoryFingerprintInitialized = true;
		return changed;
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {

			if(process > 0) {
				
				if(audio == null) {
					audio = createAudioLoop();
					audio.startSound();
				} else if(!audio.isPlaying()) {
					audio = rebootAudio(audio);
				}
				audio.updateVolume(getVolume(1F));
			} else {
				
				if(audio != null) {
					audio.stopSound();
					audio = null;
				}
			}
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_ACCOUNT || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long now = worldObj.getTotalWorldTime();
		long oldEnergy = energyQuanta;
		int oldProgress = process;
		int oldInventoryFingerprint = this.inventoryFingerprint();
		boolean charging = this.hasBatteryWork();
		long elapsed = charging ? 1L : Math.max(1L, now - lastAccountTick);
		runtimeEnergyMutation = true;
		try {
			this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 3, energyQuanta, maxPower));
			if(this.canProcess()) {
				process = (int) Math.min((long) processSpeed, process + elapsed);
				if(process >= processSpeed) this.completeOperation();
			} else {
				process = 0;
				runtimeActive = false;
			}
		} finally {
			runtimeEnergyMutation = false;
		}
		lastAccountTick = now;
		this.observeInventoryFingerprint();
		if(oldEnergy != energyQuanta || oldProgress != process || oldInventoryFingerprint != observedInventoryFingerprint) this.markDirty();
		this.evaluateAndSchedule(now);
		this.sendRuntimeState();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | MachineDirtyCause.ENERGY);
			if(process > 0) this.sendRuntimeState();
		} else if(cadence == 20) {
			this.updateConnections();
			boolean eligible = this.canProcess();
			if(eligible != runtimeActive) this.markMachineDirty(MachineDirtyCause.RECIPE | MachineDirtyCause.TOPOLOGY);
		}
	}
	
	@Override
	public AudioWrapper createAudioLoop() {
		return MainRegistry.proxy.getLoopedSound("hbm:weapon.tauChargeLoop", xCoord, yCoord, zCoord, 1.0F, 10F, 1.0F);
	}
	
	private void updateConnections() {
		
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
			this.trySubscribe(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
	}

	@Override
	public void onChunkUnload() {
		super.onChunkUnload();

		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Override
	public void invalidate() {

		super.invalidate();

		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}
	
	@Override
	public void networkUnpack(NBTTagCompound data) {
		super.networkUnpack(data);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(data, "power");
		this.process = data.getInteger("progress");
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
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

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineSchrabidiumTransmutator(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineSchrabidiumTransmutator(player.inventory, this);
	}
}
