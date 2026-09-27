package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import api.hbm.energymk2.IBatteryItem;
import java.util.List;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.container.ContainerMachineEPress;
import com.hbm.inventory.gui.GUIMachineEPress;
import com.hbm.inventory.recipes.PressRecipes;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.items.machine.ItemStamp;
import com.hbm.lib.Library;
import com.hbm.packet.toclient.AuxElectricityPacket;
import com.hbm.packet.PacketDispatcher;
//import com.hbm.packet.TEPressPacket;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.CompatEnergyControl;
import com.hbm.util.I18nUtil;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineEPress extends TileEntityMachineBase implements IEnergyReceiverMK2, IGUIProvider, IUpgradeInfoProvider, IInfoProviderEC {
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();


	public long energyQuanta = 0;
	public final static long maxPower = 50000;

	public int press;
	public double renderPress;
	public double lastPress;
	private int syncPress;
	private int turnProgress;
	public final static int maxPress = 200;
	boolean isRetracting = false;
	private int delay;
	private static final int TASK_PRESS = 1;
	private static final int TASK_BATTERY = 2;
	private static final int TASK_SLOT_MAIN = 0;
	private static final int TASK_SLOT_BATTERY = 1;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private boolean runtimeSettling;
	private boolean pressCanAdvance;
	private boolean runtimeOutputEligible;
	private long lastAccountingTick = Long.MIN_VALUE;
	private long lastBatteryChargeTick = Long.MIN_VALUE;
	private long clientPressTick;
	private int clientPressRate;
	private int clientPressDelay;
	private boolean clientRetracting;
	private boolean clientPressMoving;
	private ItemStack cachedStamp;
	private int cachedStampDamage;
	private NBTTagCompound cachedStampTag;
	private ItemStack cachedInput;
	private int cachedInputDamage;
	private NBTTagCompound cachedInputTag;
	private long cachedRecipeRevision = -1L;
	private long observedRecipeRevision = -1L;
	private ItemStack cachedOutput;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private int runtimeSpeed = 1;
	
	public ItemStack syncStack;
	
	public TileEntityMachineEPress() {
		super(5);
	}

	@Override
	public String getName() {
		return "container.epress";
	}
	
	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			this.lastPress = this.renderPress;
			this.renderPress = this.getProjectedPress();
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		long now = worldObj.getTotalWorldTime();
		if(lastAccountingTick == Long.MIN_VALUE) lastAccountingTick = now;
		else this.settlePressThrough(now - 1L);
		this.upgradeManager.checkSlots(slots, 4, 4);
		this.runtimeSpeed = 1 + Math.min(3, this.upgradeManager.getLevel(UpgradeType.SPEED));
		this.resolveCachedOutput();
		this.runtimeOutputEligible = this.hasOutputSpace(cachedOutput);
		observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		runtimeInitialized = true;
		pressCanAdvance = energyQuanta >= 100L && runtimeOutputEligible;
		this.evaluateAndSchedule(now);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long now = worldObj.getTotalWorldTime();
		if(taskType == TASK_BATTERY && taskSlot == TASK_SLOT_BATTERY) {
			if(lastBatteryChargeTick != now) this.chargeBattery(now);
			this.evaluateAndSchedule(now);
			if(this.hasScheduledPressWork() && energyQuanta >= 100L) this.scheduleMachineTransition(now, TASK_PRESS, TASK_SLOT_MAIN);
			this.sendBatteryState();
			return;
		} else if(taskType == TASK_PRESS && taskSlot == TASK_SLOT_MAIN) {
			if(lastBatteryChargeTick != now && this.hasBatteryWork()) {
				this.scheduleMachineTransition(now, TASK_PRESS, TASK_SLOT_MAIN);
				return;
			}
			this.settlePressThrough(now);
		} else return;
		this.markDirty();
		this.sendRuntimeState(taskType == TASK_PRESS);
		this.evaluateAndSchedule(now);
	}

	private void chargeBattery(long now) {
		this.settlePressThrough(now - 1L);
		long oldPower = energyQuanta;
		this.setRuntimePower(Library.chargeTEFromItems(slots, 0, energyQuanta, maxPower));
		lastBatteryChargeTick = now;
		if(oldPower != energyQuanta) this.markDirty();
	}

	private void settlePressThrough(long target) {
		if(runtimeSettling || lastAccountingTick == Long.MIN_VALUE || target <= lastAccountingTick) return;
		long remaining = target - lastAccountingTick;
		lastAccountingTick = target;
		runtimeSettling = true;
		try {
			while(remaining > 0L) {
				boolean canProcess = energyQuanta >= 100L && runtimeOutputEligible;
				boolean active = canProcess || isRetracting || delay > 0 || press > 0;
				if(!active || energyQuanta < 100L) break;
				long poweredTicks = energyQuanta / 100L;
				if(delay > 0) {
					long steps = Math.min(remaining, Math.min(poweredTicks, delay));
					this.setRuntimePower(energyQuanta - steps * 100L);
					delay -= (int) steps;
					remaining -= steps;
					if(remaining <= 0L || delay > 0) break;
					continue;
				}
				if(isRetracting) {
					int rate = this.getStampSpeed(true);
					long phaseTicks = press > 0 ? (press + (long) rate - 1L) / rate : 1L;
					long steps = Math.min(remaining, Math.min(poweredTicks, phaseTicks));
					this.setRuntimePower(energyQuanta - steps * 100L);
					press -= (int) (steps * rate);
					remaining -= steps;
					if(press <= 0) {
						isRetracting = false;
						delay = 5 - runtimeSpeed + 1;
					}
					if(remaining > 0L && steps >= poweredTicks) break;
					continue;
				}
				if(canProcess) {
					int rate = this.getStampSpeed(false);
					long phaseTicks = Math.max(1L, (maxPress - (long) press + rate - 1L) / rate);
					long steps = Math.min(remaining, Math.min(poweredTicks, phaseTicks));
					this.setRuntimePower(energyQuanta - steps * 100L);
					press += (int) (steps * rate);
					remaining -= steps;
					if(press >= maxPress) this.completePressOperation();
					if(remaining > 0L && steps >= poweredTicks) break;
					continue;
				}
				this.setRuntimePower(energyQuanta - 100L);
				isRetracting = true;
				remaining--;
			}
			pressCanAdvance = energyQuanta >= 100L && runtimeOutputEligible;
		} finally {
			runtimeSettling = false;
		}
	}

	private int getStampSpeed(boolean retracting) {
		int rate = retracting ? 20 : 45;
		rate *= (1D + (double) runtimeSpeed / 4D);
		return Math.max(1, rate);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | MachineDirtyCause.UPGRADE);
		} else if(cadence == 20) {
			this.updateConnections();
			if(observedRecipeRevision != SerializableRecipe.getRegistryRevision()) {
				observedRecipeRevision = SerializableRecipe.getRegistryRevision();
				this.markMachineDirty(MachineDirtyCause.RECIPE);
			}
			if(this.hasBatteryWork() && !this.hasScheduledPressWork()) this.markMachineDirty(MachineDirtyCause.ENERGY);
		}
	}

	private void completePressOperation() {
		this.resolveCachedOutput();
		if(cachedOutput == null || !this.hasOutputSpace(cachedOutput)) return;
		this.worldObj.playSoundEffect(this.xCoord, this.yCoord, this.zCoord, "hbm:block.pressOperate", getVolume(1.5F), 1.0F);
		if(slots[3] == null) slots[3] = cachedOutput.copy();
		else slots[3].stackSize += cachedOutput.stackSize;
		this.decrStackSize(2, 1);
		if(slots[1].getMaxDamage() != 0) {
			slots[1].setItemDamage(slots[1].getItemDamage() + 1);
			if(slots[1].getItemDamage() >= slots[1].getMaxDamage()) slots[1] = null;
		}
		this.isRetracting = true;
		this.delay = 5 - runtimeSpeed + 1;
		this.resolveCachedOutput();
		this.runtimeOutputEligible = this.hasOutputSpace(cachedOutput);
		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
	}

	private void resolveCachedOutput() {
		long revision = SerializableRecipe.getRegistryRevision();
		ItemStack stamp = slots[1];
		ItemStack input = slots[2];
		int stampDamage = stamp == null ? -1 : stamp.getItemDamage();
		int inputDamage = input == null ? -1 : input.getItemDamage();
		NBTTagCompound stampTag = stamp == null || stamp.getTagCompound() == null ? null : (NBTTagCompound) stamp.getTagCompound().copy();
		NBTTagCompound inputTag = input == null || input.getTagCompound() == null ? null : (NBTTagCompound) input.getTagCompound().copy();
		if(cachedRecipeRevision != revision || cachedStamp != stamp || cachedStampDamage != stampDamage || !tagsEqual(cachedStampTag, stampTag) || cachedInput != input || cachedInputDamage != inputDamage || !tagsEqual(cachedInputTag, inputTag)) {
			cachedStamp = stamp;
			cachedStampDamage = stampDamage;
			cachedStampTag = stampTag;
			cachedInput = input;
			cachedInputDamage = inputDamage;
			cachedInputTag = inputTag;
			cachedRecipeRevision = revision;
			cachedOutput = stamp == null || input == null ? null : PressRecipes.getOutput(input, stamp);
		}
	}

	private static boolean tagsEqual(NBTTagCompound first, NBTTagCompound second) {
		return first == null ? second == null : first.equals(second);
	}

	private boolean hasOutputSpace(ItemStack output) {
		return output != null && (slots[3] == null || slots[3].stackSize + output.stackSize <= slots[3].getMaxStackSize() && slots[3].getItem() == output.getItem() && slots[3].getItemDamage() == output.getItemDamage());
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[0] == null) return false;
		if(slots[0].getItem() == com.hbm.items.ModItems.battery_creative || slots[0].getItem() == com.hbm.items.ModItems.fusion_core_infinite) return true;
		if(!(slots[0].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[0].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[0]) > 0;
	}

	private boolean hasScheduledPressWork() {
		return (energyQuanta >= 100L && runtimeOutputEligible) || this.isRetracting || this.delay > 0 || this.press > 0;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		boolean canProcess = energyQuanta >= 100L && runtimeOutputEligible;
		boolean work = canProcess || isRetracting || delay > 0 || press > 0;
		long poweredTicks = energyQuanta / 100L;
		if(work && poweredTicks > 0L) {
			long phaseTicks;
			if(delay > 0) {
				long afterDelay = isRetracting ? (press > 0 ? (press + (long) this.getStampSpeed(true) - 1L) / this.getStampSpeed(true) : 1L)
						: (canProcess ? Math.max(1L, (maxPress - (long) press + this.getStampSpeed(false) - 1L) / this.getStampSpeed(false)) : (press > 0 ? 1L : 0L));
				phaseTicks = delay + afterDelay;
			} else if(isRetracting) {
				int rate = this.getStampSpeed(true);
				phaseTicks = press > 0 ? (press + (long) rate - 1L) / rate : 1L;
			} else if(canProcess) {
				int rate = this.getStampSpeed(false);
				phaseTicks = Math.max(1L, (maxPress - (long) press + rate - 1L) / rate);
			} else phaseTicks = 1L;
			this.scheduleMachineTransition(now + Math.max(1L, Math.min(poweredTicks, phaseTicks)), TASK_PRESS, TASK_SLOT_MAIN);
		} else this.cancelMachineTransition(TASK_PRESS, TASK_SLOT_MAIN);
		if(this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_BATTERY, TASK_SLOT_BATTERY);
		else this.cancelMachineTransition(TASK_BATTERY, TASK_SLOT_BATTERY);
	}

	private void setRuntimePower(long value) {
		runtimeEnergyMutation = true;
		try { this.setStoredEnergyQuanta(value); } finally { runtimeEnergyMutation = false; }
	}

	private void sendRuntimeState(boolean phaseBoundary) {
		NBTTagCompound data = new NBTTagCompound();
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		data.setInteger("press", press);
		data.setInteger("pressDelay", delay);
		data.setInteger("pressRate", this.getStampSpeed(isRetracting));
		data.setBoolean("pressRetracting", isRetracting);
		data.setBoolean("pressMoving", energyQuanta >= 100L && this.hasScheduledPressWork());
		data.setLong("pressTick", worldObj == null ? 0L : worldObj.getTotalWorldTime());
		if(slots[2] != null) {
			NBTTagCompound stack = new NBTTagCompound();
			slots[2].writeToNBT(stack);
			data.setTag("stack", stack);
		}
		this.networkPack(data, phaseBoundary ? 1 : 50);
	}

	private void sendBatteryState() {
		NBTTagCompound data = new NBTTagCompound();
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		data.setBoolean("muffled", this.muffled);
		this.networkPack(data, 50);
	}

	private boolean observeInventoryFingerprint() {
		int hash = 1;
		for(ItemStack stack : slots) {
			int slot = stack == null ? 0 : System.identityHashCode(stack.getItem());
			if(stack != null) {
				slot = 31 * slot + stack.stackSize;
				slot = 31 * slot + stack.getItemDamage();
				slot = 31 * slot + (stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode());
			}
			hash = 31 * hash + slot;
		}
		boolean changed = inventoryFingerprintInitialized && hash != observedInventoryFingerprint;
		observedInventoryFingerprint = hash;
		inventoryFingerprintInitialized = true;
		return changed;
	}
	
	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
		
		if(nbt.hasKey(EnergyUnits.ENERGY_KEY) || nbt.hasKey("power")) this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		if(nbt.hasKey("press")) {
			this.syncPress = nbt.getInteger("press");
			this.clientPressDelay = Math.max(0, nbt.getInteger("pressDelay"));
			this.clientPressRate = Math.max(1, nbt.getInteger("pressRate"));
			this.clientRetracting = nbt.getBoolean("pressRetracting");
			this.clientPressMoving = nbt.getBoolean("pressMoving");
			this.clientPressTick = nbt.getLong("pressTick");
		}
		
		if(nbt.hasKey("stack")) {
			NBTTagCompound stack = nbt.getCompoundTag("stack");
			this.syncStack = ItemStack.loadItemStackFromNBT(stack);
		} else if(nbt.hasKey("press")) {
			this.syncStack = null;
		}
		
		if(nbt.hasKey("press")) this.turnProgress = 0;
	}
	
	public boolean canProcess() {
		this.resolveCachedOutput();
		return energyQuanta >= 100 && this.hasOutputSpace(cachedOutput);
	}
	
	private void updateConnections() {
		
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
			this.trySubscribe(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack stack) {
		
		if(stack.getItem() instanceof ItemStamp)
			return i == 1;
		
		return i == 2;
	}
	
	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[] { 1, 2, 3 };
	}

	@Override
	public boolean canInsertItem(int i, ItemStack itemStack, int j) {
		return this.isItemValidForSlot(i, itemStack);
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return i == 3;
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		
		press = nbt.getInteger("press");
		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		isRetracting = nbt.getBoolean("ret");
		delay = nbt.getInteger("delay");
		runtimeInitialized = false;
		lastAccountingTick = Long.MIN_VALUE;
		lastBatteryChargeTick = Long.MIN_VALUE;
		inventoryFingerprintInitialized = false;
		cachedRecipeRevision = -1L;
		observedRecipeRevision = -1L;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		if(worldObj != null && !worldObj.isRemote) this.settlePressThrough(worldObj.getTotalWorldTime() - 1L);
		super.writeToNBT(nbt);

		nbt.setInteger("press", press);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setBoolean("ret", isRetracting);
		nbt.setInteger("delay", delay);
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		if(!runtimeEnergyMutation && !runtimeSettling && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settlePressThrough(worldObj.getTotalWorldTime() - 1L);
		this.energyQuanta = i;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation && worldObj != null && !worldObj.isRemote) this.markMachineEnergyDirty();
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}

	public double getProjectedPress() {
		if(worldObj == null || !worldObj.isRemote || !clientPressMoving) return syncPress;
		long elapsed = Math.max(0L, worldObj.getTotalWorldTime() - clientPressTick);
		long movingTicks = Math.max(0L, elapsed - clientPressDelay);
		double projected = syncPress + (clientRetracting ? -1D : 1D) * clientPressRate * movingTicks;
		return Math.max(0D, Math.min(maxPress, projected));
	}

	@Override protected void beforeInventorySlotChanged(int slot) {
		if(!runtimeSettling && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settlePressThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override public void onChunkUnload() {
		if(worldObj != null && !worldObj.isRemote) this.settlePressThrough(worldObj.getTotalWorldTime() - 1L);
		lastAccountingTick = Long.MIN_VALUE;
		lastBatteryChargeTick = Long.MIN_VALUE;
		super.onChunkUnload();
	}
	
	AxisAlignedBB aabb;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(aabb != null)
			return aabb;
		
		aabb = AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord + 1, yCoord + 3, zCoord + 1);
		return aabb;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineEPress(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineEPress(player.inventory, this);
	}

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_epress));
		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_DELAY, "-" + (50 * level / 3) + "%"));
		}
	}

	@Override
	public int getMaxLevel(UpgradeType type) {
		if(type == UpgradeType.SPEED) return 3;
		return 0;
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setInteger(CompatEnergyControl.I_PROGRESS, this.press);
	}
}
