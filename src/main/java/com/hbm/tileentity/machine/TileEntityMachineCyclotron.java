package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.List;
import java.util.Map.Entry;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.container.ContainerMachineCyclotron;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineCyclotron;
import com.hbm.inventory.recipes.CyclotronRecipes;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.*;
import com.hbm.util.CompatEnergyControl;
import com.hbm.util.I18nUtil;
import com.hbm.util.Tuple.Pair;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.IBatteryItem;
import api.hbm.fluid.IFluidStandardTransceiver;
import api.hbm.tile.IInfoProviderEC;
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
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineCyclotron extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardTransceiver, IGUIProvider, IConditionalInvAccess, IUpgradeInfoProvider, IInfoProviderEC, IFluidCopiable {
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();


	public long energyQuanta;
	public static final long maxPower = 100000000;
	public static int consumption = 1_000_000;

	private byte plugs;

	public int progress;
	public static final int duration = 690;

	public FluidTank[] tanks;
	private final Object[][] cachedLaneResults = new Object[3][];
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private FluidType observedCoolantType;
	private long observedRecipeRevision;
	private static final int TASK_PROCESS = 1;
	private static final int TASK_SLOT_MAIN = 0;

	public TileEntityMachineCyclotron() {
		super(12);

		this.tanks = new FluidTank[3];
		this.tanks[0] = new FluidTank(Fluids.FRESH_WATER, 32000).migrateFrom(Fluids.WATER);
		this.tanks[1] = new FluidTank(Fluids.SPENTSTEAM, 32000);
		this.tanks[2] = new FluidTank(Fluids.AMAT, 8000);
		for(FluidTank tank : tanks) this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.cyclotron";
	}

	@Override
	public void updateEntity() {
		// Server work is driven by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20 | MachineExecutionStrategy.COARSE_100;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.refreshRuntimeState();
		FluidType coolantType = tanks[0].getTankType();
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.TOPOLOGY)) != 0 || observedCoolantType != coolantType) this.updateConnections();
		observedCoolantType = coolantType;
		observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNT(25);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_PROCESS || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long oldEnergy = energyQuanta;
		int oldProgress = progress;
		int oldCoolantFill = tanks[0].getFill();
		int oldSteamFill = tanks[1].getFill();
		int oldAmatFill = tanks[2].getFill();
		int oldInventoryFingerprint = this.inventoryFingerprint();
		if(inventoryFingerprintInitialized && oldInventoryFingerprint != observedInventoryFingerprint) this.refreshRuntimeState();
		this.beginMachineFluidMutation();
		runtimeEnergyMutation = true;
		try {
			this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 9, energyQuanta, maxPower));
			if(this.canProcess()) {
				progress += this.getSpeed();
				this.setStoredEnergyQuanta(energyQuanta - this.getConsumption());
				int convert = this.getCoolantConsumption();
				tanks[0].setFill(tanks[0].getFill() - convert);
				tanks[1].setFill(tanks[1].getFill() + convert);
				if(progress >= duration) {
					this.process();
					progress = 0;
					this.refreshRuntimeState();
					this.markDirty();
				}
			} else {
				progress = 0;
			}
		} finally {
			runtimeEnergyMutation = false;
			this.endMachineFluidMutation();
		}
		boolean inventoryChanged = oldInventoryFingerprint != this.inventoryFingerprint();
		if(inventoryChanged) this.markNetworkDirty();
		this.observeInventoryFingerprint();
		if(oldEnergy != energyQuanta || oldProgress != progress || oldCoolantFill != tanks[0].getFill() || oldSteamFill != tanks[1].getFill() || oldAmatFill != tanks[2].getFill() || inventoryChanged) this.markDirty();
		this.sendFluid();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNT(25);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | MachineDirtyCause.UPGRADE | MachineDirtyCause.ENERGY);
		} else if(cadence == 20) {
			this.updateConnections();
		} else if(cadence == 100) {
			long revision = SerializableRecipe.getRegistryRevision();
			if(revision != observedRecipeRevision) {
				observedRecipeRevision = revision;
				this.markMachineDirty(MachineDirtyCause.RECIPE);
			}
		}
	}

	private void refreshRuntimeState() {
		this.upgradeManager.checkSlots(slots, 10, 11);
		for(int i = 0; i < 3; i++) cachedLaneResults[i] = CyclotronRecipes.getOutput(slots[i + 3], slots[i]);
		this.observeInventoryFingerprint();
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

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[9] == null) return false;
		if(slots[9].getItem() == ModItems.battery_creative || slots[9].getItem() == ModItems.fusion_core_infinite) return true;
		if(!(slots[9].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[9].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[9]) > 0;
	}

	private boolean hasFluidOutput() {
		return tanks[1].getFill() > 0 || tanks[2].getFill() > 0;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(this.canProcess() || this.hasBatteryWork() || this.hasFluidOutput() || progress > 0) this.scheduleMachineTransition(now + 1L, TASK_PROCESS, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_PROCESS, TASK_SLOT_MAIN);
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(energyQuanta);
		buf.writeInt(progress);
		buf.writeByte(plugs);

		for(int i = 0; i < 3; i++)
			tanks[i].serialize(buf);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		energyQuanta = buf.readLong();
		progress = buf.readInt();
		plugs = buf.readByte();

		for(int i = 0; i < 3; i++)
			tanks[i].deserialize(buf);
	}

	private void updateConnections()  {
		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}

	private void sendFluid() {
		for(int i = 1; i < 3; i++) {
			if(tanks[i].getFill() > 0) {
				for(DirPos pos : getConPos()) {
					this.sendFluid(tanks[i], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
				}
			}
		}
	}
	//todo remove blackhole logic if its in here which i dont see it so...

	public DirPos[] getConPos() {
		return new DirPos[] {
				new DirPos(xCoord + 3, yCoord, zCoord + 1, Library.POS_X),
				new DirPos(xCoord + 3, yCoord, zCoord - 1, Library.POS_X),
				new DirPos(xCoord - 3, yCoord, zCoord + 1, Library.NEG_X),
				new DirPos(xCoord - 3, yCoord, zCoord - 1, Library.NEG_X),
				new DirPos(xCoord + 1, yCoord, zCoord + 3, Library.POS_Z),
				new DirPos(xCoord - 1, yCoord, zCoord + 3, Library.POS_Z),
				new DirPos(xCoord + 1, yCoord, zCoord - 3, Library.NEG_Z),
				new DirPos(xCoord - 1, yCoord, zCoord - 3, Library.NEG_Z)
		};
	}

	public boolean canProcess() {

		if(energyQuanta < getConsumption())
			return false;

		int convert = getCoolantConsumption();

		if(tanks[0].getFill() < convert)
			return false;

		if(tanks[1].getFill() + convert > tanks[1].getMaxFill())
			return false;

		for(int i = 0; i < 3; i++) {

			Object[] res = cachedLaneResults[i];

			if(res == null)
				continue;

			ItemStack out = (ItemStack)res[0];

			if(out == null)
				continue;

			if(slots[i + 6] == null)
				return true;

			if(slots[i + 6].getItem() == out.getItem() && slots[i + 6].getItemDamage() == out.getItemDamage() && slots[i + 6].stackSize < out.getMaxStackSize())
				return true;
		}

		return false;
	}

	public void process() {

		for(int i = 0; i < 3; i++) {

			Object[] res = cachedLaneResults[i];

			if(res == null)
				continue;

			ItemStack out = (ItemStack)res[0];

			if(out == null)
				continue;

			if(slots[i + 6] == null) {

				this.decrStackSize(i, 1);
				this.decrStackSize(i + 3, 1);
				slots[i + 6] = out;

				this.tanks[2].setFill(this.tanks[2].getFill() + (Integer)res[1]);

				continue;
			}

			if(slots[i + 6].getItem() == out.getItem() && slots[i + 6].getItemDamage() == out.getItemDamage() && slots[i + 6].stackSize < out.getMaxStackSize()) {

				this.decrStackSize(i, 1);
				this.decrStackSize(i + 3, 1);
				slots[i + 6].stackSize++;

				this.tanks[2].setFill(this.tanks[2].getFill() + (Integer)res[1]);
			}
		}

		if(this.tanks[2].getFill() > this.tanks[2].getMaxFill())
			this.tanks[2].setFill(this.tanks[2].getMaxFill());
	}

	public int getSpeed() {
		return Math.min(this.upgradeManager.getLevel(UpgradeType.SPEED), 3) + 1;
	}

	public int getConsumption() {
		int efficiency = Math.min(this.upgradeManager.getLevel(UpgradeType.POWER), 3);

		return consumption - 100_000 * efficiency;
	}

	public int getCoolantConsumption() {
		int efficiency = Math.min(this.upgradeManager.getLevel(UpgradeType.EFFECT), 3);
		//half a small tower's worth
		return 500 / (efficiency + 1) * getSpeed();
	}

	public long getPowerScaled(long i) {
		return (energyQuanta * i) / maxPower;
	}

	public int getProgressScaled(int i) {
		return (progress * i) / duration;
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return AxisAlignedBB.getBoundingBox(xCoord - 2, yCoord, zCoord - 2, xCoord + 3, yCoord + 4, zCoord + 3);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		for(int i = 0; i < 3; i++)
			tanks[i].readFromNBT(nbt, "t" + i);

		this.progress = nbt.getInteger("progress");
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.plugs = nbt.getByte("plugs");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		for(int i = 0; i < 3; i++)
			tanks[i].writeToNBT(nbt, "t" + i);

		nbt.setInteger("progress", progress);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setByte("plugs", plugs);
	}

	public void setPlug(int index) {
		this.plugs |= (1 << index);
		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
	}

	public boolean getPlug(int index) {
		return (this.plugs & (1 << index)) > 0;
	}

	public static Item getItemForPlug(int i) {

		switch(i) {
		case 0: return ModItems.powder_balefire;
		case 1: return ModItems.bedrock_ore;
		//go fuck yourself
		case 2: return ModItems.diamond_gavel;
		case 3: return ModItems.coin_maskman;
		}

		return null;
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack stack) {
		super.setInventorySlotContents(i, stack);

		if(stack != null && i >= 14 && i <= 15 && stack.getItem() instanceof ItemMachineUpgrade)
			worldObj.playSoundEffect(xCoord + 0.5, yCoord + 1.5, zCoord + 0.5, "hbm:item.upgradePlug", 1.5F, 1.0F);
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
		return this.energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return this.maxPower;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { tanks[1], tanks[2] };
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { tanks[0] };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineCyclotron(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineCyclotron(player.inventory, this);
	}

	@Override
	public boolean isItemValidForSlot(int x, int y, int z, int slot, ItemStack stack) {

		if(slot < 3) {
			for(Entry<Pair<ComparableStack, AStack>, Pair<ItemStack, Integer>> entry : CyclotronRecipes.recipes.entrySet()) {
				if(entry.getKey().getKey().matchesRecipe(stack, true)) return true;
			}
		} else if(slot < 6) {

			for(Entry<Pair<ComparableStack, AStack>, Pair<ItemStack, Integer>> entry : CyclotronRecipes.recipes.entrySet()) {
				if(entry.getKey().getValue().matchesRecipe(stack, true)) return true;
			}
		}

		return false;
	}

	@Override
	public boolean canInsertItem(int x, int y, int z, int slot, ItemStack stack, int side) {
		return this.isItemValidForSlot(x, y, z, slot, stack);
	}

	@Override
	public boolean canExtractItem(int x, int y, int z, int slot, ItemStack stack, int side) {
		return slot >= 6 && slot <= 8;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int x, int y, int z, int side) {

		for(int i = 2; i < 6; i++) {
			ForgeDirection dir = ForgeDirection.getOrientation(i);
			ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

			if(x == xCoord + dir.offsetX * 2 + rot.offsetX && z == zCoord + dir.offsetZ * 2 + rot.offsetZ) return new int[] {0, 3, 6, 7, 8};
			if(x == xCoord + dir.offsetX * 2 && z == zCoord + dir.offsetZ * 2) return new int[] {1, 4, 6, 7, 8};
			if(x == xCoord + dir.offsetX * 2 - rot.offsetX && z == zCoord + dir.offsetZ * 2 - rot.offsetZ) return new int[] {2, 5, 6, 7, 8};
		}

		return new int[] {6, 7, 8};
	}

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED || type == UpgradeType.POWER || type == UpgradeType.EFFECT;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_cyclotron));
		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_DELAY, "-" + (100 - 100 / (level + 1)) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(this.KEY_COOLANT_CONSUMPTION, "+" + (level * 100) + "%"));
		}
		if(type == UpgradeType.POWER) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "-" + (level * 10) + "%"));
		}
		if(type == UpgradeType.EFFECT) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_COOLANT_CONSUMPTION, "-" + (100 - 100 / (level + 1)) + "%"));
		}
	}

	@Override
	public int getMaxLevel(UpgradeType type) {
		if(type == UpgradeType.SPEED) return 3;
		if(type == UpgradeType.POWER) return 3;
		if(type == UpgradeType.EFFECT) return 3;
		return 0;
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, this.progress > 0);
		data.setDouble(CompatEnergyControl.D_CONSUMPTION_HE, EnergyUnits.quantaToLegacyHe(this.progress > 0 ? getConsumption() : 0));
	}

	@Override
	public FluidTank getTankToPaste() {
		return null;
	}
}
