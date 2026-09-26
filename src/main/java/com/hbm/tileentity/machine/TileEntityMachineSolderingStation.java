package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.List;

import com.hbm.blocks.ModBlocks;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.container.ContainerMachineSolderingStation;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineSolderingStation;
import com.hbm.inventory.recipes.SolderingRecipes;
import com.hbm.inventory.recipes.SolderingRecipes.SolderingRecipe;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.packet.PacketDispatcher;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.packet.toclient.AuxParticlePacketNT;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.I18nUtil;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluid.IFluidStandardReceiver;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineSolderingStation extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardReceiver, IControlReceiver, IGUIProvider, IUpgradeInfoProvider, IFluidCopiable {
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();


	public long energyQuanta;
	public long maxPower = 2_000;
	public long consumption;
	public boolean collisionPrevention = false;

	public int progress;
	public int processTime = 1;

	public FluidTank tank;
	public ItemStack display;
	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private boolean runtimeActive;
	private SolderingRecipe cachedRecipe;
	private int cachedInputFingerprint;
	private boolean recipeFingerprintInitialized;
	private long cachedRecipeRevision = -1L;
	private long observedRecipeRevision = -1L;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private long operatingPowerWatts;

	public TileEntityMachineSolderingStation() {
		super(11);
		this.tank = new FluidTank(Fluids.NONE, 8_000);
		this.trackMachineFluidTank(this.tank);
	}

	@Override
	public String getName() {
		return "container.machineSolderingStation";
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack stack) {
		super.setInventorySlotContents(i, stack);

		if(stack != null && stack.getItem() instanceof ItemMachineUpgrade && i >= 9 && i <= 10) {
			worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, "hbm:item.upgradePlug", 1.0F, 1.0F);
		}
	}

	@Override
	public void updateEntity() {
		// All mutable machine work is scheduled on the server; visuals and packet sync remain client visible.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if(!recipeFingerprintInitialized || (causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | MachineDirtyCause.UPGRADE | MachineDirtyCause.CONFIGURATION)) != 0) this.refreshRecipe();
		this.refreshRuntimeSettings();
		runtimeInitialized = true;
		observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_ACCOUNTING || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long now = worldObj.getTotalWorldTime();
		long oldPower = energyQuanta;
		int oldProgress = progress;
		this.setRuntimePower(Library.chargeTEFromItems(slots, 7, this.getStoredEnergyQuanta(), this.getEnergyCapacityQuanta()));
		boolean canRun = cachedRecipe != null && this.canProcess(cachedRecipe);
		if(canRun && progress + 1 >= processTime) {
			this.refreshRecipe();
			this.refreshRuntimeSettings();
			canRun = cachedRecipe != null && this.canProcess(cachedRecipe);
		}
		if(canRun) {
			this.progress++;
			this.setRuntimePower(this.energyQuanta - EnergyUnits.wattsToQuantaPerTick(this.operatingPowerWatts));
			runtimeActive = true;
			if(progress >= processTime) {
				if(cachedRecipe != null && this.canProcessMaterials(cachedRecipe)) this.completeOperation(cachedRecipe);
				else this.progress = 0;
			}
		} else {
			this.progress = 0;
			runtimeActive = false;
		}
		if(oldPower != energyQuanta || oldProgress != progress) this.markDirty();
		this.sendRuntimeState();
		this.evaluateAndSchedule(now);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			boolean inventoryChanged = this.observeInventoryFingerprint();
			boolean fluidItemChanged = this.tank.setType(8, slots);
			if(inventoryChanged || fluidItemChanged) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE | MachineDirtyCause.UPGRADE);
			return;
		}
		if(cadence != 20) return;
		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			if(tank.getTankType() != Fluids.NONE) this.trySubscribe(tank.getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
		if(observedRecipeRevision != SerializableRecipe.getRegistryRevision()) {
			observedRecipeRevision = SerializableRecipe.getRegistryRevision();
			this.markMachineDirty(MachineDirtyCause.RECIPE);
		}
		if(runtimeActive && cachedRecipe != null) this.emitSolderingParticle();
		this.sendRuntimeState();
	}

	private void refreshRecipe() {
		int fingerprint = this.recipeInputFingerprint();
		long revision = SerializableRecipe.getRegistryRevision();
		if(!recipeFingerprintInitialized || fingerprint != cachedInputFingerprint || revision != cachedRecipeRevision) {
			this.cachedRecipe = SolderingRecipes.getRecipe(new ItemStack[] {slots[0], slots[1], slots[2], slots[3], slots[4], slots[5]});
			this.cachedInputFingerprint = fingerprint;
			this.cachedRecipeRevision = revision;
			recipeFingerprintInitialized = true;
		}
	}

	private void refreshRuntimeSettings() {
		this.upgradeManager.checkSlots(slots, 9, 10);
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
		if(this.processTime <= 0) this.processTime = 1;
		this.operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(this.consumption);
		this.maxPower = Math.max(intendedMaxPower, energyQuanta);
	}

	private void completeOperation(SolderingRecipe recipe) {
		this.beginMachineFluidMutation();
		try { this.consumeItems(recipe); } finally { this.endMachineFluidMutation(); }
		if(slots[6] == null) slots[6] = recipe.output.copy();
		else slots[6].stackSize += recipe.output.stackSize;
		this.progress = 0;
		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
	}

	private boolean canProcessMaterials(SolderingRecipe recipe) {
		if(recipe == null) return false;
		if(recipe.fluid != null && (this.tank.getTankType() != recipe.fluid.type || this.tank.getFill() < recipe.fluid.fill)) return false;
		if(collisionPrevention && recipe.fluid == null && this.tank.getFill() > 0) return false;
		if(slots[6] != null && (slots[6].getItem() != recipe.output.getItem() || slots[6].getItemDamage() != recipe.output.getItemDamage() || slots[6].stackSize + recipe.output.stackSize > slots[6].getMaxStackSize())) return false;
		return true;
	}

	private boolean hasBatteryWork() {
		return energyQuanta < maxPower && slots[7] != null && slots[7].getItem() instanceof IBatteryItem;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(cachedRecipe != null && this.canProcess(cachedRecipe) || this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_ACCOUNTING, TASK_SLOT_MAIN);
		else {
			this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_MAIN);
			runtimeActive = false;
		}
	}

	private void setRuntimePower(long value) {
		runtimeEnergyMutation = true;
		try { this.setStoredEnergyQuanta(value); } finally { runtimeEnergyMutation = false; }
	}

	private int recipeInputFingerprint() {
		int hash = 1;
		for(int i = 0; i < 6; i++) hash = 31 * hash + this.stackFingerprint(slots[i]);
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

	private void emitSolderingParticle() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
		NBTTagCompound dPart = new NBTTagCompound();
		dPart.setString("type", "tau");
		dPart.setByte("count", (byte) 3);
		PacketDispatcher.wrapper.sendToAllAround(new AuxParticlePacketNT(dPart, xCoord + 0.5 - dir.offsetX * 0.5 + rot.offsetX * 0.5, yCoord + 1.125, zCoord + 0.5 - dir.offsetZ * 0.5 + rot.offsetZ * 0.5), new TargetPoint(worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 25));
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		EnergyUnits.writeCapacityQuanta(data, maxPower);
		data.setLong("consumption", consumption);
		data.setInteger("progress", progress);
		data.setInteger("processTime", processTime);
		data.setBoolean("collisionPrevention", collisionPrevention);
		if(cachedRecipe != null) {
			data.setInteger("display", Item.getIdFromItem(cachedRecipe.output.getItem()));
			data.setInteger("displayMeta", cachedRecipe.output.getItemDamage());
		}
		this.tank.writeToNBT(data, "t");
		this.networkPack(data, 25);
	}

	public boolean canProcess(SolderingRecipe recipe) {

		if(this.energyQuanta < this.consumption) return false;
		return this.canProcessMaterials(recipe);
	}

	public void consumeItems(SolderingRecipe recipe) {

		for(AStack aStack : recipe.toppings) {
			for(int i = 0; i < 3; i++) {
				ItemStack stack = slots[i];
				if(aStack.matchesRecipe(stack, true) && stack.stackSize >= aStack.stacksize) { this.decrStackSize(i, aStack.stacksize); break; }
			}
		}

		for(AStack aStack : recipe.pcb) {
			for(int i = 3; i < 5; i++) {
				ItemStack stack = slots[i];
				if(aStack.matchesRecipe(stack, true) && stack.stackSize >= aStack.stacksize) { this.decrStackSize(i, aStack.stacksize); break; }
			}
		}

		for(AStack aStack : recipe.solder) {
			for(int i = 5; i < 6; i++) {
				ItemStack stack = slots[i];
				if(aStack.matchesRecipe(stack, true) && stack.stackSize >= aStack.stacksize) { this.decrStackSize(i, aStack.stacksize); break; }
			}
		}

		if(recipe.fluid != null) {
			this.tank.setFill(tank.getFill() - recipe.fluid.fill);
		}
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		if(slot < 3) {
			for(int i = 0; i < 3; i++) if(i != slot && slots[i] != null && slots[i].isItemEqual(stack)) return false;
			for(AStack t : SolderingRecipes.toppings) if(t.matchesRecipe(stack, true)) return true;
		} else if(slot < 5) {
			for(int i = 3; i < 5; i++) if(i != slot && slots[i] != null && slots[i].isItemEqual(stack)) return false;
			for(AStack t : SolderingRecipes.pcb) if(t.matchesRecipe(stack, true)) return true;
		} else if(slot < 6) {
			for(int i = 5; i < 6; i++) if(i != slot && slots[i] != null && slots[i].isItemEqual(stack)) return false;
			for(AStack t : SolderingRecipes.solder) if(t.matchesRecipe(stack, true)) return true;
		}
		return false;
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return i == 6;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[] { 0, 1, 2, 3, 4, 5, 6 };
	}

	protected DirPos[] getConPos() {

		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

		return new DirPos[] {
			new DirPos(xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ, dir),
			new DirPos(xCoord + dir.offsetX + rot.offsetX, yCoord, zCoord + dir.offsetZ + rot.offsetZ, dir),
			new DirPos(xCoord - dir.offsetX * 2, yCoord, zCoord - dir.offsetZ * 2, dir.getOpposite()),
			new DirPos(xCoord - dir.offsetX * 2 + rot.offsetX, yCoord, zCoord - dir.offsetZ * 2 + rot.offsetZ, dir.getOpposite()),
			new DirPos(xCoord - rot.offsetX, yCoord, zCoord - rot.offsetZ, rot.getOpposite()),
			new DirPos(xCoord - dir.offsetX - rot.offsetX, yCoord, zCoord - dir.offsetZ - rot.offsetZ, rot.getOpposite()),
			new DirPos(xCoord + rot.offsetX * 2, yCoord, zCoord + rot.offsetZ * 2, rot),
			new DirPos(xCoord - dir.offsetX + rot.offsetX * 2, yCoord, zCoord - dir.offsetZ + rot.offsetZ * 2, rot),
		};
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.maxPower = EnergyUnits.readCapacityQuanta(nbt, "maxPower");
		this.consumption = nbt.getLong("consumption");
		this.progress = nbt.getInteger("progress");
		this.processTime = nbt.getInteger("processTime");
		this.collisionPrevention = nbt.getBoolean("collisionPrevention");

		if(nbt.hasKey("display")) {
			this.display = new ItemStack(Item.getItemById(nbt.getInteger("display")), 1, nbt.getInteger("displayMeta"));
		} else {
			this.display = null;
		}

		this.tank.readFromNBT(nbt, "t");
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.maxPower = EnergyUnits.readCapacityQuanta(nbt, "maxPower");
		this.progress = nbt.getInteger("progress");
		this.processTime = nbt.getInteger("processTime");
		this.collisionPrevention = nbt.getBoolean("collisionPrevention");
		tank.readFromNBT(nbt, "t");
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
		nbt.setBoolean("collisionPrevention", collisionPrevention);
		tank.writeToNBT(nbt, "t");
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
	public FluidTank[] getAllTanks() {
		return new FluidTank[] {tank};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tank};
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineSolderingStation(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineSolderingStation(player.inventory, this);
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
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_soldering_station));
		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_DELAY, "-" + (level * 100 / 6) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "+" + (level * 100) + "%"));
		}
		if(type == UpgradeType.POWER) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "-" + (level * 100 / 6) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(this.KEY_DELAY, "+" + (level * 100 / 3) + "%"));
		}
	}

	@Override
	public int getMaxLevel(UpgradeType type) {
		if(type == UpgradeType.SPEED) return 3;
		if(type == UpgradeType.POWER) return 3;
		return 0;
	}

	@Override
	public FluidTank getTankToPaste() {
		return tank;
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return this.isUseableByPlayer(player);
	}

	@Override
	public void receiveControl(NBTTagCompound data) {
		this.collisionPrevention = !this.collisionPrevention;
		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
	}
}
