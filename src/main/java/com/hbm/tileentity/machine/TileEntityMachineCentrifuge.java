package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.io.IOException;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.container.ContainerCentrifuge;
import com.hbm.inventory.gui.GUIMachineCentrifuge;
import com.hbm.inventory.recipes.CentrifugeRecipes;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.main.MainRegistry;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.BobMathUtil;
import com.hbm.util.CompatEnergyControl;
import com.hbm.util.I18nUtil;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.IBatteryItem;
import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineCentrifuge extends TileEntityMachineBase implements IEnergyReceiverMK2, IGUIProvider, IUpgradeInfoProvider, IInfoProviderEC, IConfigurableMachine{
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();


	public int progress;
	public long energyQuanta;
	public boolean isProgressing;
	private int audioDuration = 0;
	private int soundCycle;
	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeStateInitialized;
	private boolean runtimeEnergyMutation;
	private long nextRuntimeTick = -1L;
	private long lastAccountingTick = Long.MIN_VALUE;
	private long observedPower;
	private long observedRecipeRevision = -1L;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(baseConsumption);
	private int processingSpeedPerTick = 1;
	private ItemStack cachedRecipeInput;
	private int cachedRecipeMeta;
	private int cachedRecipeTagHash;
	private long cachedRecipeRevision = -1L;
	private ItemStack[] cachedRecipeOutput;

	private AudioWrapper audio;

	//configurable values
	public static int maxPower = 100000;
	public static int processingSpeed = 200;
	public static int baseConsumption = 200;

	public String getConfigName() {
		return "centrifuge";
	}
	/* reads the JSON object and sets the machine's parameters, use defaults and ignore if a value is not yet present */
	public void readIfPresent(JsonObject obj) {
		maxPower = IConfigurableMachine.grabEnergyQuanta(obj, "I:energyCapacityQuanta", "I:powerCap", maxPower);
		processingSpeed = IConfigurableMachine.grab(obj, "I:timeToProcess", processingSpeed);
		baseConsumption = IConfigurableMachine.grab(obj, "I:consumption", baseConsumption);
	}
	/* writes the entire config for this machine using the relevant values */
	public void writeConfig(JsonWriter writer) throws IOException {
		writer.name("I:energyCapacityQuanta").value(maxPower);
		writer.name("I:timeToProcess").value(processingSpeed);
		writer.name("I:consumption").value(baseConsumption);
	}


	/*
	 * So why do we do this now? You have a funny mekanism/thermal/whatever pipe and you want to output stuff from a side
	 * that isn't the bottom, what do? Answer: make all slots accessible from all sides and regulate in/output in the
	 * dedicated methods. Duh.
	 */
	private static final int[] slot_io = new int[] { 0, 2, 3, 4, 5 };

	public TileEntityMachineCentrifuge() {
		super(8);
	}

	public String getName() {
		return "container.centrifuge";
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemStack) {
		return i == 0;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return slot_io;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		progress = nbt.getShort("progress");
		isProgressing = this.progress > 0;
		runtimeStateInitialized = false;
		nextRuntimeTick = -1L;
		cachedRecipeInput = null;
		cachedRecipeRevision = -1L;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setShort("progress", (short) progress);
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return i > 1;
	}

	public int getCentrifugeProgressScaled(int i) {
		return (progress * i) / processingSpeed;
	}

	public long getPowerRemainingScaled(int i) {
		return (energyQuanta * i) / maxPower;
	}

	public boolean canProcess() {

		if(slots[0] == null) {
			return false;
		}
		ItemStack[] out = this.getCachedRecipeOutput();

		if(out == null) {
			return false;
		}

		for(int i = 0; i < Math.min(4, out.length); i++) {

			//either the slot is null, the output is null or the output can be added to the existing slot
			if(slots[i + 2] == null)
				continue;

			if(out[i] == null)
				continue;

			if(slots[i + 2].isItemEqual(out[i]) && slots[i + 2].stackSize + out[i].stackSize <= out[i].getMaxStackSize())
				continue;

			return false;
		}

		return true;
	}

	private void processItem() {
		ItemStack[] out = this.getCachedRecipeOutput();
		if(out == null) return;

		for(int i = 0; i < Math.min(4, out.length); i++) {

			if(out[i] == null)
				continue;

			if(slots[i + 2] == null) {
				slots[i + 2] = out[i].copy();
			} else {
				slots[i + 2].stackSize += out[i].stackSize;
			}
		}

		this.decrStackSize(0, 1);
		this.markDirty();
	}

	public boolean hasPower() {
		return energyQuanta > 0;
	}

	public boolean isProcessing() {
		return this.progress > 0;
	}

	@Override
	public void updateEntity() {

		if(worldObj.isRemote) {

			if(isProgressing) {
				audioDuration += 2;
			} else {
				audioDuration -= 3;
			}

			audioDuration = MathHelper.clamp_int(audioDuration, 0, 60);

			if(audioDuration > 10) {

				if(audio == null) {
					audio = createAudioLoop();
					audio.startSound();
				} else if(!audio.isPlaying()) {
					audio = rebootAudio(audio);
				}

				audio.updateVolume(getVolume(1F));
				audio.updatePitch((audioDuration - 10) / 100F + 0.5F);

			} else {

				if(audio != null) {
					audio.stopSound();
					audio = null;
				}
			}
		}
	}

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		boolean settingsChanged = this.refreshRuntimeSettings(false);
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE)) != 0 || settingsChanged) {
			this.getCachedRecipeOutput();
		}
		this.runtimeStateInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_ACCOUNTING || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote) return;
		nextRuntimeTick = -1L;
		if(!runtimeStateInitialized) return;
		long now = worldObj.getTotalWorldTime();
		if(lastAccountingTick == now) return;
		lastAccountingTick = now;

		long oldPower = energyQuanta;
		int oldProgress = progress;
		boolean oldActive = isProgressing;
		this.setRuntimePower(Library.chargeTEFromItems(slots, 1, energyQuanta, maxPower));
		if(this.progress > 0 && this.hasPower()) {
			long tickEnergy = EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts);
			this.setRuntimePower(Math.max(0L, this.energyQuanta - tickEnergy));
		}

		if(this.hasPower() && this.canProcess()) {
			isProgressing = true;
			progress += processingSpeedPerTick;
			if(progress >= processingSpeed) {
				progress = 0;
				this.processItem();
			}
		} else {
			isProgressing = false;
			progress = 0;
		}

		if(isProgressing && soundCycle == 0) this.worldObj.playSoundEffect(xCoord, yCoord, zCoord, "minecart.base", getVolume(1.0F), 0.75F);
		if(isProgressing) soundCycle = (soundCycle + 1) % 50;

		if(oldPower != energyQuanta || oldProgress != progress || oldActive != isProgressing) {
			this.markDirty();
			this.markNetworkDirty();
			this.networkPackNTIfDirty(50);
		}
		this.observedPower = energyQuanta;
		this.evaluateAndSchedule(now);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			boolean inventoryChanged = this.observeInventoryFingerprint();
			if(inventoryChanged) {
				this.upgradeManager.invalidate();
				this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | MachineDirtyCause.CONFIGURATION);
			}
			return;
		}
		if(cadence != 20) return;
		boolean recipeChanged = observedRecipeRevision != SerializableRecipe.getRegistryRevision();
		boolean powerChanged = observedPower != energyQuanta;
		boolean settingsChanged = this.refreshRuntimeSettings(true);
		if(recipeChanged) observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		if(recipeChanged || powerChanged || settingsChanged || (this.hasBatteryWork() && nextRuntimeTick < 0L)) {
			this.markMachineDirty((recipeChanged ? MachineDirtyCause.RECIPE : 0) | (powerChanged ? MachineDirtyCause.ENERGY : 0) | (settingsChanged ? MachineDirtyCause.UPGRADE : 0));
		}
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) this.trySubscribe(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
		this.networkPackNTIfDirty(50);
	}

	private void evaluateAndSchedule(long now) {
		boolean eligible = this.hasPower() && this.canProcess();
		if(!eligible && this.progress == 0 && !this.hasBatteryWork()) {
			this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_MAIN);
			nextRuntimeTick = -1L;
			return;
		}
		long due = now + 1L;
		nextRuntimeTick = due;
		this.scheduleMachineTransition(due, TASK_ACCOUNTING, TASK_SLOT_MAIN);
	}

	private boolean refreshRuntimeSettings(boolean contentAware) {
		long oldPowerWatts = operatingPowerWatts;
		int oldSpeed = processingSpeedPerTick;
		if(contentAware) this.upgradeManager.checkSlots(slots, 6, 7);
		else this.upgradeManager.checkSlotsIfDirty(slots, 6, 7);
		int speedLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.SPEED), 3);
		int powerLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.POWER), 3);
		int overdriveLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.OVERDRIVE), 3);
		processingSpeedPerTick = (1 + speedLevel) * (1 + overdriveLevel * 5);
		long quantaPerTick = baseConsumption + (long) speedLevel * baseConsumption + (long) overdriveLevel * baseConsumption * 50L;
		quantaPerTick /= 1 + powerLevel;
		operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(quantaPerTick);
		return oldPowerWatts != operatingPowerWatts || oldSpeed != processingSpeedPerTick;
	}

	private ItemStack[] getCachedRecipeOutput() {
		ItemStack input = slots[0];
		int meta = input == null ? 0 : input.getItemDamage();
		int tagHash = input == null || input.getTagCompound() == null ? 0 : input.getTagCompound().hashCode();
		long revision = SerializableRecipe.getRegistryRevision();
		if(cachedRecipeInput != input || cachedRecipeMeta != meta || cachedRecipeTagHash != tagHash || cachedRecipeRevision != revision) {
			cachedRecipeInput = input;
			cachedRecipeMeta = meta;
			cachedRecipeTagHash = tagHash;
			cachedRecipeRevision = revision;
			cachedRecipeOutput = CentrifugeRecipes.getOutput(input);
		}
		return cachedRecipeOutput;
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[1] == null) return false;
		if(slots[1].getItem() == com.hbm.items.ModItems.battery_creative || slots[1].getItem() == com.hbm.items.ModItems.fusion_core_infinite) return true;
		if(!(slots[1].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[1].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[1]) > 0;
	}

	private void setRuntimePower(long value) {
		runtimeEnergyMutation = true;
		try { this.setStoredEnergyQuanta(value); } finally { runtimeEnergyMutation = false; }
	}

	private boolean observeInventoryFingerprint() {
		int hash = 1;
		for(int i = 0; i < slots.length; i++) {
			ItemStack stack = slots[i];
			int slot = stack == null ? 0 : 31 * (31 * (31 * System.identityHashCode(stack.getItem()) + stack.getItemDamage()) + stack.stackSize) + (stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode());
			hash = 31 * hash + slot;
		}
		boolean changed = inventoryFingerprintInitialized && hash != observedInventoryFingerprint;
		observedInventoryFingerprint = hash;
		inventoryFingerprintInitialized = true;
		return changed;
	}

	@Override
	protected void onInventorySlotChanged(int slot) {
		super.onInventorySlotChanged(slot);
		if(slot == 6 || slot == 7) this.upgradeManager.invalidate();
		if(slot == 0) this.cachedRecipeInput = null;
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(energyQuanta);
		buf.writeInt(progress);
		buf.writeBoolean(isProgressing);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		energyQuanta = buf.readLong();
		progress = buf.readInt();
		isProgressing = buf.readBoolean();
	}

	@Override
	public AudioWrapper createAudioLoop() {
		return MainRegistry.proxy.getLoopedSound("hbm:block.centrifugeOperate", xCoord, yCoord, zCoord, 1.0F, 10F, 1.0F);
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

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord,
					yCoord,
					zCoord,
					xCoord + 1,
					yCoord + 4,
					zCoord + 1
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
		return new ContainerCentrifuge(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineCentrifuge(player.inventory, this);
	}

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED || type == UpgradeType.POWER || type == UpgradeType.OVERDRIVE;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_centrifuge));
		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_DELAY, "-" + (100 - 100 / (level + 1)) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "+" + (level * 100) + "%"));
		}
		if(type == UpgradeType.POWER) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "-" + (100 - 100 / (level + 1)) + "%"));
		}
		if(type == UpgradeType.OVERDRIVE) {
			info.add((BobMathUtil.getBlink() ? EnumChatFormatting.RED : EnumChatFormatting.DARK_GRAY) + "YES");
		}
	}

	@Override
	public int getMaxLevel(UpgradeType type) {
		if(type == UpgradeType.SPEED) return 3;
		if(type == UpgradeType.POWER) return 3;
		if(type == UpgradeType.OVERDRIVE) return 3;
		return 0;
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, this.progress > 0);
		data.setInteger(CompatEnergyControl.B_ACTIVE, this.progress);
	}
}
