package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import api.hbm.energymk2.IBatteryItem;
import java.util.List;

import com.hbm.blocks.ModBlocks;
import com.hbm.dim.trait.CBT_Atmosphere;
import com.hbm.handler.atmosphere.ChunkAtmosphereManager;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.container.ContainerVacuumCircuit;
import com.hbm.inventory.gui.GUIVacuumCircuit;
import com.hbm.inventory.recipes.VacuumCircuitRecipes;
import com.hbm.inventory.recipes.VacuumCircuitRecipes.VacuumCircuitRecipe;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.I18nUtil;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

public class TileEntityMachineVacuumCircuit extends TileEntityMachineBase implements IEnergyReceiverMK2, IGUIProvider, IUpgradeInfoProvider {
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();


	public long energyQuanta;
	public long maxPower = 2_000;
	public long consumption;
	
	public int progress;
	public int processTime = 1;
	
	private VacuumCircuitRecipe recipe;
	public ItemStack display;

	public boolean canOperate = true;
	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private VacuumCircuitRecipe cachedRecipe;
	private int cachedInputFingerprint;
	private boolean recipeFingerprintInitialized;
	private long cachedRecipeRevision = -1L;
	private long observedRecipeRevision = -1L;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private long operatingPowerWatts;
	private boolean runtimeMaterialEligible;
	
	public TileEntityMachineVacuumCircuit() {
		super(8);
	}

	
	@Override
	public String getName() {
		return "container.machineVacuumCircuit";
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack stack) {
		super.setInventorySlotContents(i, stack);
		
		if(stack != null && stack.getItem() instanceof ItemMachineUpgrade && i >= 6 && i <=7) {
			worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, "hbm:item.upgradePlug", 1.0F, 1.0F);
		}
	}

	@Override
	public void updateEntity() {
		// Recipe work, atmosphere checks, and connections are runtime driven.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if(!recipeFingerprintInitialized || (causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | MachineDirtyCause.UPGRADE | MachineDirtyCause.CONFIGURATION)) != 0) this.refreshRecipe();
		this.refreshRuntimeSettings();
		this.runtimeMaterialEligible = this.canProcessMaterials();
		runtimeInitialized = true;
		observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_ACCOUNTING || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long now = worldObj.getTotalWorldTime();
		long oldPower = energyQuanta;
		int oldProgress = progress;
		this.setRuntimePower(Library.chargeTEFromItems(slots, 5, this.getStoredEnergyQuanta(), this.getEnergyCapacityQuanta()));
		boolean canRun = runtimeMaterialEligible && this.hasOperatingPower();
		if(canRun && progress + 1 >= processTime) {
			this.refreshRecipe();
			this.refreshRuntimeSettings();
			canRun = this.canProcessMaterials() && this.hasOperatingPower();
		}
		if(canRun) {
			this.progress++;
			this.setRuntimePower(this.energyQuanta - EnergyUnits.wattsToQuantaPerTick(this.operatingPowerWatts));
			if(progress >= processTime) {
				if(cachedRecipe != null && this.canProcessMaterials()) this.completeOperation(cachedRecipe);
				else this.progress = 0;
			}
		} else {
			this.progress = 0;
		}
		if(oldPower != energyQuanta || oldProgress != progress) this.markDirty();
		this.networkPackNT(25);
		this.evaluateAndSchedule(now);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | MachineDirtyCause.UPGRADE);
			return;
		}
		if(cadence != 20) return;
		this.updateConnections();
		CBT_Atmosphere atmosphere = ChunkAtmosphereManager.proxy.getAtmosphere(worldObj, xCoord, yCoord, zCoord);
		boolean canOperateNow = atmosphere == null || atmosphere.getPressure() <= 0.001;
		if(canOperateNow != canOperate) {
			canOperate = canOperateNow;
			this.markMachineDirty(MachineDirtyCause.ENVIRONMENT);
		}
		if(observedRecipeRevision != SerializableRecipe.getRegistryRevision()) {
			observedRecipeRevision = SerializableRecipe.getRegistryRevision();
			this.markMachineDirty(MachineDirtyCause.RECIPE);
		}
		this.networkPackNT(25);
	}

	private void refreshRecipe() {
		int fingerprint = this.recipeInputFingerprint();
		long revision = SerializableRecipe.getRegistryRevision();
		if(!recipeFingerprintInitialized || fingerprint != cachedInputFingerprint || revision != cachedRecipeRevision) {
			this.cachedRecipe = VacuumCircuitRecipes.getRecipe(new ItemStack[] {slots[0], slots[1], slots[2], slots[3]});
			this.cachedInputFingerprint = fingerprint;
			this.cachedRecipeRevision = revision;
			recipeFingerprintInitialized = true;
		}
		this.recipe = this.cachedRecipe;
	}

	private void refreshRuntimeSettings() {
		this.upgradeManager.checkSlots(slots, 6, 7);
		int redLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.SPEED), 3);
		int blueLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.POWER), 3);
		long intendedMaxPower;
		if(cachedRecipe != null) {
			this.processTime = cachedRecipe.duration - (cachedRecipe.duration * redLevel / 6) + (cachedRecipe.duration * blueLevel / 3);
			this.consumption = cachedRecipe.consumption + (cachedRecipe.consumption * redLevel) - (cachedRecipe.consumption * blueLevel / 6);
			intendedMaxPower = cachedRecipe.consumption * 20;
		} else {
			this.progress = 0;
			this.consumption = 100;
			intendedMaxPower = 2000;
		}
		if(processTime <= 0) processTime = 1;
		this.operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(this.consumption);
		this.maxPower = Math.max(intendedMaxPower, energyQuanta);
	}

	private boolean canProcessMaterials() {
		return cachedRecipe != null && canOperate && this.hasOutputSpace(cachedRecipe.output);
	}

	private boolean hasOutputSpace(ItemStack output) {
		return output != null && (slots[4] == null || slots[4].getItem() == output.getItem() && slots[4].getItemDamage() == output.getItemDamage() && slots[4].stackSize + output.stackSize <= slots[4].getMaxStackSize());
	}

	private boolean hasOperatingPower() { return energyQuanta >= consumption; }

	private boolean hasBatteryWork() {
		return energyQuanta < maxPower && slots[5] != null && slots[5].getItem() instanceof IBatteryItem;
	}

	private void completeOperation(VacuumCircuitRecipe recipe) {
		this.consumeItems(recipe);
		if(slots[4] == null) slots[4] = recipe.output.copy();
		else slots[4].stackSize += recipe.output.stackSize;
		this.progress = 0;
		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
	}

	private void evaluateAndSchedule(long now) {
		if((runtimeMaterialEligible && this.hasOperatingPower()) || this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_ACCOUNTING, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_MAIN);
	}

	private void setRuntimePower(long value) {
		runtimeEnergyMutation = true;
		try { this.setStoredEnergyQuanta(value); } finally { runtimeEnergyMutation = false; }
	}

	private int recipeInputFingerprint() {
		int hash = 1;
		for(int i = 0; i < 4; i++) hash = 31 * hash + this.stackFingerprint(slots[i]);
		return hash;
	}

	private boolean observeInventoryFingerprint() {
		int hash = 1;
		for(int i = 0; i < slots.length; i++) {
			ItemStack stack = slots[i];
			int tag = stack == null || stack.getItem() instanceof IBatteryItem || stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode();
			int slot = stack == null ? 0 : 31 * (31 * (31 * System.identityHashCode(stack.getItem()) + stack.getItemDamage()) + stack.stackSize) + tag;
			hash = 31 * hash + slot;
		}
		boolean changed = inventoryFingerprintInitialized && hash != observedInventoryFingerprint;
		observedInventoryFingerprint = hash;
		inventoryFingerprintInitialized = true;
		return changed;
	}

	private int stackFingerprint(ItemStack stack) {
		if(stack == null) return 0;
		int hash = System.identityHashCode(stack.getItem());
		hash = 31 * hash + stack.getItemDamage();
		hash = 31 * hash + stack.stackSize;
		return 31 * hash + (stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode());
	}
	
	public boolean canProcess(VacuumCircuitRecipe recipe) {
		if(!canOperate) return false;
		
		if(this.energyQuanta < this.consumption) return false;
		return this.hasOutputSpace(recipe.output);
	}
	private void updateConnections() {
		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}
	public void consumeItems(VacuumCircuitRecipe recipe) {
		
		for(AStack aStack : recipe.wafer) {
			for(int i = 0; i < 2; i++) {
				ItemStack stack = slots[i];
				if(aStack.matchesRecipe(stack, true) && stack.stackSize >= aStack.stacksize) { this.decrStackSize(i, aStack.stacksize); break; }
			}
		}
		
		for(AStack aStack : recipe.pcb) {
			for(int i = 2; i < 4; i++) {
				ItemStack stack = slots[i];
				if(aStack.matchesRecipe(stack, true) && stack.stackSize >= aStack.stacksize) { this.decrStackSize(i, aStack.stacksize); break; }
			}
		}
		
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		if(slot < 2) {
			for(int i = 0; i < 2; i++) if(i != slot && slots[i] != null && slots[i].isItemEqual(stack)) return false;
			for(AStack t : VacuumCircuitRecipes.wafer) if(t.matchesRecipe(stack, true)) return true;
		} else if(slot < 4) {
			for(int i = 2; i < 4; i++) if(i != slot && slots[i] != null && slots[i].isItemEqual(stack)) return false;
			for(AStack t : VacuumCircuitRecipes.pcb) if(t.matchesRecipe(stack, true)) return true;
		}
		return false;
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return i == 4;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[] { 0, 1, 2, 3, 4 };
	}
	
	public DirPos[] getConPos() {
		return new DirPos[] {
				new DirPos(xCoord + 2, yCoord, zCoord + 1, Library.POS_X),
				new DirPos(xCoord + 2, yCoord, zCoord - 1, Library.POS_X),
				new DirPos(xCoord - 2, yCoord, zCoord + 1, Library.NEG_X),
				new DirPos(xCoord - 2, yCoord, zCoord - 1, Library.NEG_X),
				new DirPos(xCoord + 1, yCoord, zCoord + 2, Library.POS_Z),
				new DirPos(xCoord - 1, yCoord, zCoord + 2, Library.POS_Z),
				new DirPos(xCoord + 1, yCoord, zCoord - 2, Library.NEG_Z),
				new DirPos(xCoord - 1, yCoord, zCoord - 2, Library.NEG_Z)
		};
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);

		buf.writeLong(energyQuanta);
		buf.writeLong(maxPower);
		buf.writeLong(consumption);
		buf.writeInt(progress);
		buf.writeInt(processTime);
		buf.writeBoolean(canOperate);

		if(recipe != null) {
			buf.writeBoolean(true);
			buf.writeInt(Item.getIdFromItem(recipe.output.getItem()));
			buf.writeInt(recipe.output.getItemDamage());
		} else {
			buf.writeBoolean(false);
		}
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);

		energyQuanta = buf.readLong();
		maxPower = buf.readLong();
		consumption = buf.readLong();
		progress = buf.readInt();
		processTime = buf.readInt();
		canOperate = buf.readBoolean();

		if(buf.readBoolean()) {
			int id = buf.readInt();
			int meta = buf.readInt();
			display = new ItemStack(Item.getItemById(id), 1, meta);
		} else {
			display = null;
		}
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.maxPower = EnergyUnits.readCapacityQuanta(nbt, "maxPower");
		this.progress = nbt.getInteger("progress");
		this.processTime = nbt.getInteger("processTime");
		runtimeInitialized = false;
		recipeFingerprintInitialized = false;
		inventoryFingerprintInitialized = false;
		cachedRecipeRevision = -1L;
		observedRecipeRevision = -1L;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		EnergyUnits.writeCapacityQuanta(nbt, maxPower);
		nbt.setInteger("progress", progress);
		nbt.setInteger("processTime", processTime);
	}

	@Override
	public long getStoredEnergyQuanta() {
		return Math.max(Math.min(energyQuanta, maxPower), 0);
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation && worldObj != null && !worldObj.isRemote) this.markMachineEnergyDirty();
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}


	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerVacuumCircuit(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIVacuumCircuit(player.inventory, this);
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

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED || type == UpgradeType.POWER;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_vacuum_circuit));
		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(KEY_DELAY, "-" + (level * 100 / 6) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(KEY_CONSUMPTION, "+" + (level * 100) + "%"));
		}
		if(type == UpgradeType.POWER) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(KEY_CONSUMPTION, "-" + (level * 100 / 6) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(KEY_DELAY, "+" + (level * 100 / 3) + "%"));
		}
	}

	@Override
	public int getMaxLevel(UpgradeType type) {
		if(type == UpgradeType.SPEED) return 3;
		if(type == UpgradeType.POWER) return 3;
		return 0;
	}

}
