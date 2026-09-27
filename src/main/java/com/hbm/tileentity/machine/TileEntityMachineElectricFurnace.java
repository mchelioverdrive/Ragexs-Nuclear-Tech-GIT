package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.List;

import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.machine.MachineElectricFurnace;
import com.hbm.handler.pollution.PollutionHandler;
import com.hbm.handler.pollution.PollutionHandler.PollutionType;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.container.ContainerElectricFurnace;
import com.hbm.inventory.gui.GUIMachineElectricFurnace;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.I18nUtil;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyReceiverMK2;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineElectricFurnace extends TileEntityMachineBase implements ISidedInventory, IEnergyReceiverMK2, IGUIProvider, IUpgradeInfoProvider {
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();


	// HOLY FUCKING SHIT I SPENT 5 DAYS ON THIS SHITFUCK CLASS FILE
	// thanks Martin, vaer and Bob for the help
	public int progress;
	public long energyQuanta;
	public static final long maxPower = 100000;
	public int maxProgress = 100;
	public long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(50L);
	private int cooldown = 0;
	private boolean operationActive;
	private boolean cachedRecipeEligible;
	private long nextRuntimeTick = -1L;
	private long lastAccountingTick = Long.MIN_VALUE;
	private boolean runtimeEnergyMutation;
	private boolean runtimeStateInitialized;
	private boolean runtimeWorkScheduled;
	private long clientProgressTick;

	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_BATTERY = 2;
	private static final int TASK_SLOT_MAIN = 0;
	private static final int TASK_SLOT_BATTERY = 1;

	private static final int[] slots_io = new int[] { 0, 1, 2 };

	public TileEntityMachineElectricFurnace() {
		super(4);
	}

	@Override public void onChunkUnload() {
		if(worldObj != null && !worldObj.isRemote) this.settleAccountingThrough(worldObj.getTotalWorldTime() - 1L);
		lastAccountingTick = Long.MIN_VALUE;
		super.onChunkUnload();
	}

	@Override
	public String getName() {
		return "container.electricFurnace";
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemStack) {
		if(i == 0) {
			return itemStack.getItem() instanceof IBatteryItem;
		}

		if(i == 1) {
			return FurnaceRecipes.smelting().getSmeltingResult(itemStack) != null;
		}

		return false;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.progress = nbt.getInteger("progress");
		this.cooldown = nbt.getInteger("runtimeCooldown");
		this.operationActive = nbt.hasKey("runtimeActive") ? nbt.getBoolean("runtimeActive") : this.progress > 0;
		this.nextRuntimeTick = nbt.hasKey("runtimeNextTick") ? nbt.getLong("runtimeNextTick") : -1L;
		if(nbt.hasKey("runtimeDuration")) this.maxProgress = nbt.getInteger("runtimeDuration");
		if(nbt.hasKey("runtimeOperatingPowerWatts")) this.operatingPowerWatts = nbt.getLong("runtimeOperatingPowerWatts");
		else if(nbt.hasKey("runtimeConsumption")) this.operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(nbt.getInteger("runtimeConsumption"));
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		if(worldObj != null && !worldObj.isRemote) this.settleAccountingThrough(worldObj.getTotalWorldTime() - 1L);
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setInteger("progress", progress);
		nbt.setInteger("runtimeCooldown", cooldown);
		nbt.setBoolean("runtimeActive", operationActive);
		nbt.setLong("runtimeNextTick", nextRuntimeTick);
		nbt.setInteger("runtimeDuration", maxProgress);
		nbt.setLong("runtimeOperatingPowerWatts", operatingPowerWatts);
		nbt.setInteger("runtimeConsumption", (int) EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts));
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return slots_io;
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		if(i == 0)
			if(itemStack.getItem() instanceof IBatteryItem && ((IBatteryItem) itemStack.getItem()).getStoredEnergyQuanta(itemStack) == 0)
				return true;
		if(i == 2)
			return true;

		return false;
	}

	public int getProgressScaled(int i) {
		long displayed = progress;
		if(worldObj != null && worldObj.isRemote && operationActive) displayed += Math.max(0L, worldObj.getTotalWorldTime() - clientProgressTick);
		return (int) (Math.min(maxProgress, displayed) * i / maxProgress);
	}

	public long getPowerScaled(long i) {
		return (energyQuanta * i) / maxPower;
	}

	public boolean hasPower() {
		return energyQuanta >= EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts);
	}

	public boolean isProcessing() {
		return this.progress > 0;
	}

	public boolean canProcess() {
		return cooldown <= 0 && this.findEligibleRecipe() != null;
	}

	private ItemStack findEligibleRecipe() {
		if(slots[1] == null) return null;
		ItemStack itemStack = FurnaceRecipes.smelting().getSmeltingResult(this.slots[1]);

		if(itemStack == null) return null;

		if(slots[2] == null) return itemStack;

		if(!slots[2].isItemEqual(itemStack)) return null;

		if(slots[2].stackSize < getInventoryStackLimit() && slots[2].stackSize < slots[2].getMaxStackSize()) {
			return itemStack;
		}
		return slots[2].stackSize < itemStack.getMaxStackSize() ? itemStack : null;
	}

	private void processItem(ItemStack itemStack) {
		if(itemStack == null || slots[1] == null) return;

		if(slots[2] == null) {
			slots[2] = itemStack.copy();
		} else if(slots[2].isItemEqual(itemStack)) {
			slots[2].stackSize += itemStack.stackSize;
		}

		for(int i = 1; i < 2; i++) {
			if(slots[i].stackSize <= 0) {
				slots[i] = new ItemStack(slots[i].getItem().setFull3D());
			} else {
				slots[i].stackSize--;
			}
			if(slots[i].stackSize <= 0) {
				slots[i] = null;
			}
		}
	}

	@Override
	public void updateEntity() {
		// Server work is owned by MachineRuntime.
	}

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public String getMachineRuntimeType() {
		return "hbm:electric_furnace";
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		long now = worldObj.getTotalWorldTime();
		if(lastAccountingTick == Long.MIN_VALUE) lastAccountingTick = now;
		else this.settleAccountingThrough(now - 1L);
		boolean wasScheduled = runtimeWorkScheduled;
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | MachineDirtyCause.CONFIGURATION)) != 0) {
			if(this.refreshUpgrades(false)) {
				this.markDirty();
				this.markNetworkDirty();
			}
			this.cachedRecipeEligible = this.findEligibleRecipe() != null;
		}
		this.runtimeStateInitialized = true;
		if(wasScheduled || cooldown > 0 && this.hasPower()) this.settleAccountingThrough(now);
		this.scheduleNextBoundary(now);
		this.networkPackNTIfDirty(50);
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(worldObj == null || worldObj.isRemote || !runtimeStateInitialized) return;
		long now = worldObj.getTotalWorldTime();
		if(taskType == TASK_BATTERY && taskSlot == TASK_SLOT_BATTERY) {
			this.settleAccountingThrough(now);
			this.setPowerInternal(Library.chargeTEFromItems(slots, 0, energyQuanta, maxPower));
			this.scheduleNextBoundary(now);
			return;
		} else if(taskType == TASK_ACCOUNTING && taskSlot == TASK_SLOT_MAIN) this.settleAccountingThrough(now);
		else return;
		this.scheduleNextBoundary(now);
		this.networkPackNTIfDirty(50);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote) return;
		this.settleAccountingThrough(worldObj.getTotalWorldTime());
		boolean upgradeChanged = this.refreshUpgrades(true);
		boolean eligible = this.findEligibleRecipe() != null;
		boolean eligibilityChanged = eligible != this.cachedRecipeEligible;
		this.cachedRecipeEligible = eligible;
		if(upgradeChanged) {
			this.markDirty();
			this.markNetworkDirty();
		}
		if(upgradeChanged || eligibilityChanged || (this.hasBatteryWork() && this.nextRuntimeTick < 0L)) {
			this.cancelAccountingTransition();
			this.markMachineDirty((upgradeChanged ? MachineDirtyCause.CONFIGURATION : 0) | MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
		}
		if((worldObj.getTotalWorldTime() / 20L & 1L) == 0L) this.updateConnections();
		this.markNetworkDirty();
		this.networkPackNTIfDirty(50);
	}

	private void settleAccountingThrough(long target) {
		if(lastAccountingTick == Long.MIN_VALUE || target <= lastAccountingTick) return;
		long cursor = lastAccountingTick;
		long oldPower = energyQuanta;
		int oldProgress = progress;
		int oldCooldown = cooldown;
		boolean oldActive = operationActive;
		boolean completed = false;
		while(cursor < target) {
			if(!this.hasPower()) {
				cooldown = 20;
				progress = 0;
				operationActive = false;
				cursor = target;
				break;
			}
			if(cooldown > 0) {
				long spent = Math.min(target - cursor, cooldown - 1L);
				cooldown -= (int) spent;
				cursor += spent;
				if(cursor >= target) break;
				cooldown--;
			}
			if(!cachedRecipeEligible) {
				progress = 0;
				operationActive = false;
				cursor = target;
				break;
			}
			long cost = EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts);
			long available = cost <= 0L ? target - cursor : energyQuanta / cost;
			long steps = Math.min(target - cursor, Math.min(available, maxProgress - progress));
			if(steps > 0L) {
				if(cost > 0L) this.setPowerInternal(energyQuanta - steps * cost);
				progress += (int) steps;
				operationActive = true;
				long sootTicks = (cursor + steps) / 20L - cursor / 20L;
				for(long i = 0L; i < sootTicks; i++) PollutionHandler.incrementPollution(worldObj, xCoord, yCoord, zCoord, PollutionType.SOOT, PollutionHandler.SOOT_PER_SECOND);
				cursor += steps;
			}
			if(progress >= maxProgress) {
				progress = 0;
				operationActive = false;
				ItemStack result = this.findEligibleRecipe();
				if(result != null) this.processItem(result);
				cachedRecipeEligible = this.findEligibleRecipe() != null;
				completed = true;
			} else if(steps == 0L) {
				cursor++;
				cooldown = 20;
				progress = 0;
				operationActive = false;
			}
		}
		lastAccountingTick = target;
		this.setVisualActive(operationActive || completed && this.hasPower() && cooldown <= 0 && cachedRecipeEligible);
		if(oldPower != energyQuanta || oldProgress != progress || oldCooldown != cooldown || oldActive != operationActive || completed) {
			this.markDirty();
			this.markNetworkDirty();
		}
	}

	private void scheduleNextBoundary(long now) {
		this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_MAIN);
		runtimeWorkScheduled = false;
		if(this.hasPower() && cooldown > 0) {
			nextRuntimeTick = now + cooldown;
			runtimeWorkScheduled = true;
		} else if(this.hasPower() && cachedRecipeEligible) {
			long cost = EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts);
			long powerBoundary = cost <= 0L ? Long.MAX_VALUE : energyQuanta / cost + 1L;
			long sootBoundary = 20L - now % 20L;
			long delay = Math.max(1L, Math.min(Math.max(1, maxProgress - progress), Math.min(powerBoundary, sootBoundary)));
			nextRuntimeTick = now + delay;
			runtimeWorkScheduled = true;
		} else {
			if(operationActive || progress > 0) {
				operationActive = false;
				progress = 0;
				this.setVisualActive(false);
			}
			nextRuntimeTick = -1L;
		}
		if(runtimeWorkScheduled) this.scheduleMachineTransition(nextRuntimeTick, TASK_ACCOUNTING, TASK_SLOT_MAIN);
		if(this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_BATTERY, TASK_SLOT_BATTERY);
		else this.cancelMachineTransition(TASK_BATTERY, TASK_SLOT_BATTERY);
	}

	private boolean refreshUpgrades(boolean contentAware) {
		int oldMaxProgress = this.maxProgress;
		long oldOperatingPowerWatts = this.operatingPowerWatts;
		if(contentAware) this.upgradeManager.checkSlots(slots, 3, 3);
		else this.upgradeManager.checkSlotsIfDirty(slots, 3, 3);

		int speedLevel = this.upgradeManager.getLevel(UpgradeType.SPEED);
		int powerLevel = this.upgradeManager.getLevel(UpgradeType.POWER);
		this.maxProgress = 100 - speedLevel * 25 + powerLevel * 10;
		long consumptionQuantaPerTick = 50L + speedLevel * 50L - powerLevel * 15L;
		this.operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(consumptionQuantaPerTick);
		return oldMaxProgress != this.maxProgress || oldOperatingPowerWatts != this.operatingPowerWatts;
	}

	private boolean hasBatteryWork() {
		if(this.energyQuanta >= maxPower || slots[0] == null) return false;
		if(slots[0].getItem() == ModItems.battery_creative || slots[0].getItem() == ModItems.fusion_core_infinite) return true;
		if(!(slots[0].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[0].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[0]) > 0;
	}

	private void setPowerInternal(long value) {
		this.runtimeEnergyMutation = true;
		try {
			this.setStoredEnergyQuanta(value);
		} finally {
			this.runtimeEnergyMutation = false;
		}
	}

	private void cancelAccountingTransition() {
		this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_MAIN);
		this.nextRuntimeTick = -1L;
	}

	private void setVisualActive(boolean active) {
		if(worldObj == null || worldObj.isRemote) return;
		if(active && worldObj.getBlock(xCoord, yCoord, zCoord) == ModBlocks.machine_electric_furnace_on) return;
		if(!active && worldObj.getBlock(xCoord, yCoord, zCoord) == ModBlocks.machine_electric_furnace_off) return;
		MachineElectricFurnace.updateBlockState(active, worldObj, xCoord, yCoord, zCoord);
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(energyQuanta);
		buf.writeInt(maxProgress);
		buf.writeInt(progress);
		buf.writeLong(worldObj == null ? 0L : worldObj.getTotalWorldTime());
		buf.writeBoolean(operationActive || runtimeWorkScheduled && cachedRecipeEligible && cooldown <= 0 && this.hasPower());
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		energyQuanta = buf.readLong();
		maxProgress = buf.readInt();
		progress = buf.readInt();
		clientProgressTick = buf.readLong();
		operationActive = buf.readBoolean();
	}

	private void updateConnections() {

		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
			this.trySubscribe(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
	}

	@Override
	protected void onInventorySlotChanged(int slot) {
		super.onInventorySlotChanged(slot);
		this.cancelAccountingTransition();
		if(slot == 3) this.upgradeManager.invalidate();
	}

	@Override protected void beforeInventorySlotChanged(int slot) {
		if(worldObj != null && !worldObj.isRemote) this.settleAccountingThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		if(!runtimeEnergyMutation && worldObj != null && !worldObj.isRemote) this.settleAccountingThrough(worldObj.getTotalWorldTime() - 1L);
		this.markNetworkDirty();
		this.energyQuanta = i;
		this.markPowerNetDirty();
		if(!this.runtimeEnergyMutation) {
			this.cancelAccountingTransition();
			this.markMachineEnergyDirty();
		}

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
		return new ContainerElectricFurnace(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineElectricFurnace(player.inventory, this);
	}

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED || type == UpgradeType.POWER;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_electric_furnace_off));
		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_DELAY, "-" + (level * 25) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "+" + (level * 100) + "%"));
		}
		if(type == UpgradeType.POWER) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "-" + (level * 30) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(this.KEY_DELAY, "+" + (level * 10) + "%"));
		}
	}

	@Override
	public int getMaxLevel(UpgradeType type) {
		if(type == UpgradeType.SPEED) return 3;
		if(type == UpgradeType.POWER) return 3;
		return 0;
	}
}
