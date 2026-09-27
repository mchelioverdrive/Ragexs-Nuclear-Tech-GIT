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
	private boolean runtimeSettling;
	private boolean runtimeActive;
	private boolean runtimeHasOutputRoom;
	private int runtimeConsumption;
	private int runtimeCoolantConsumption;
	private int runtimeSpeed = 1;
	private long lastAccountingTick = Long.MIN_VALUE;
	private long lastBatteryChargeTick = Long.MIN_VALUE;
	private long clientProgressTick;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private FluidType observedCoolantType;
	private long observedRecipeRevision;
	private static final int TASK_PROCESS = 1;
	private static final int TASK_BATTERY = 2;
	private static final int TASK_FLUID = 3;
	private static final int TASK_SLOT_MAIN = 0;
	private static final int TASK_SLOT_BATTERY = 1;
	private static final int TASK_SLOT_FLUID = 2;

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
		long now = worldObj.getTotalWorldTime();
		boolean wasActive = runtimeInitialized && progress > 0;
		if(lastAccountingTick == Long.MIN_VALUE) lastAccountingTick = now;
		else this.settleProgressThrough(now - 1L);
		runtimeInitialized = true;
		this.refreshRuntimeState();
		runtimeActive = this.hasRuntimeResources();
		FluidType coolantType = tanks[0].getTankType();
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.TOPOLOGY)) != 0 || observedCoolantType != coolantType) this.updateConnections();
		observedCoolantType = coolantType;
		observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		if(wasActive && this.hasRuntimeResources()) this.settleProgressThrough(now);
		this.evaluateAndSchedule(now);
		this.networkPackNT(25);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long now = worldObj.getTotalWorldTime();
		if(taskType == TASK_PROCESS && taskSlot == TASK_SLOT_MAIN) {
			this.settleProgressThrough(now);
		} else if(taskType == TASK_BATTERY && taskSlot == TASK_SLOT_BATTERY) {
			if(lastBatteryChargeTick != now) this.chargeBattery(now);
			this.evaluateAndSchedule(now);
			return;
		} else if(taskType == TASK_FLUID && taskSlot == TASK_SLOT_FLUID) {
			this.settleProgressThrough(now);
			this.sendFluid();
		} else return;
		this.observeInventoryFingerprint();
		this.evaluateAndSchedule(now);
		this.networkPackNTIfDirty(25);
	}

	private void chargeBattery(long now) {
		this.settleProgressThrough(now);
		long oldEnergy = energyQuanta;
		runtimeEnergyMutation = true;
		try { this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 9, energyQuanta, maxPower)); }
		finally { runtimeEnergyMutation = false; }
		lastBatteryChargeTick = now;
		if(oldEnergy != energyQuanta) this.markDirty();
	}

	private void settleProgressThrough(long target) {
		if(runtimeSettling || lastAccountingTick == Long.MIN_VALUE || target <= lastAccountingTick) return;
		long elapsed = target - lastAccountingTick;
		lastAccountingTick = target;
		long remaining = elapsed;
		boolean changed = false;
		runtimeSettling = true;
		this.beginMachineFluidMutation();
		try {
			while(remaining > 0L) {
				if(!this.runtimeHasOutputRoom) {
					if(progress != 0) changed = true;
					progress = 0;
					runtimeActive = false;
					break;
				}
				runtimeActive = true;
				long cost = Math.max(1L, runtimeConsumption);
				int coolant = Math.max(1, runtimeCoolantConsumption);
				int speed = Math.max(1, runtimeSpeed);
				long energyTicks = energyQuanta / cost;
			long coolantTicks = tanks[0].getFill() / (long) coolant;
			long steamTicks = (tanks[1].getMaxFill() - (long) tanks[1].getFill()) / coolant;
			long availableTicks = Math.min(energyTicks, Math.min(coolantTicks, steamTicks));
			if(availableTicks <= 0L) {
				progress = 0;
				runtimeActive = false;
				changed = true;
				break;
				}
				long completionTicks = Math.max(1L, ((long) duration - progress + speed - 1L) / speed);
				long ticks = Math.min(remaining, Math.min(availableTicks, completionTicks));
				this.runtimeEnergyMutation = true;
				try { this.setStoredEnergyQuanta(energyQuanta - ticks * cost); }
				finally { this.runtimeEnergyMutation = false; }
				int coolantAmount = (int) (ticks * coolant);
				tanks[0].setFill(tanks[0].getFill() - coolantAmount);
				tanks[1].setFill(tanks[1].getFill() + coolantAmount);
				progress += (int) (ticks * speed);
				remaining -= ticks;
				changed = true;
				if(progress >= duration) {
					progress = 0;
					this.process();
					this.refreshRuntimeState();
					continue;
				}
				if(remaining > 0L && ticks >= availableTicks) { progress = 0; break; }
			}
			runtimeActive = this.hasRuntimeResources();
		} finally {
			this.endMachineFluidMutation();
			runtimeSettling = false;
		}
		if(changed) { this.markDirty(); this.markNetworkDirty(); }
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
		runtimeSpeed = Math.max(1, this.getSpeed());
		runtimeConsumption = Math.max(1, this.getConsumption());
		runtimeCoolantConsumption = Math.max(1, this.getCoolantConsumption());
		runtimeHasOutputRoom = this.hasRuntimeOutputRoom();
		this.observeInventoryFingerprint();
	}

	private boolean hasRuntimeOutputRoom() {
		for(int i = 0; i < 3; i++) {
			Object[] result = cachedLaneResults[i];
			if(result == null || result[0] == null) continue;
			ItemStack output = (ItemStack) result[0];
			if(slots[i + 6] == null || slots[i + 6].isItemEqual(output) && slots[i + 6].stackSize < output.getMaxStackSize()) return true;
		}
		return false;
	}

	private boolean hasRuntimeResources() {
		return runtimeHasOutputRoom && energyQuanta >= runtimeConsumption
				&& tanks[0].getFill() >= runtimeCoolantConsumption
				&& tanks[1].getFill() + runtimeCoolantConsumption <= tanks[1].getMaxFill();
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
		if(this.hasRuntimeResources() || progress > 0) {
			long cost = Math.max(1L, runtimeConsumption);
			int coolant = Math.max(1, runtimeCoolantConsumption);
			int speed = Math.max(1, runtimeSpeed);
			long completionTicks = Math.max(1L, ((long) duration - progress + speed - 1L) / speed);
			long energyTicks = energyQuanta / cost;
			long coolantTicks = tanks[0].getFill() / (long) coolant;
			long steamTicks = (tanks[1].getMaxFill() - (long) tanks[1].getFill()) / coolant;
			long availableTicks = Math.min(energyTicks, Math.min(coolantTicks, steamTicks));
			long resourceBoundary = availableTicks >= completionTicks ? Long.MAX_VALUE : availableTicks + 1L;
			long delay = Math.max(1L, Math.min(completionTicks, resourceBoundary));
			this.scheduleMachineTransition(now + delay, TASK_PROCESS, TASK_SLOT_MAIN);
		} else this.cancelMachineTransition(TASK_PROCESS, TASK_SLOT_MAIN);
		if(this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_BATTERY, TASK_SLOT_BATTERY);
		else this.cancelMachineTransition(TASK_BATTERY, TASK_SLOT_BATTERY);
		if(this.hasFluidOutput()) this.scheduleMachineTransition(now + 1L, TASK_FLUID, TASK_SLOT_FLUID);
		else this.cancelMachineTransition(TASK_FLUID, TASK_SLOT_FLUID);
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(energyQuanta);
		buf.writeInt(progress);
		buf.writeByte(plugs);
		buf.writeBoolean(runtimeActive);
		buf.writeLong(worldObj == null ? 0L : worldObj.getTotalWorldTime());

		for(int i = 0; i < 3; i++)
			tanks[i].serialize(buf);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		energyQuanta = buf.readLong();
		progress = buf.readInt();
		plugs = buf.readByte();
		runtimeActive = buf.readBoolean();
		clientProgressTick = buf.readLong();

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
		long projected = progress;
		if(worldObj != null && worldObj.isRemote && runtimeActive) projected += Math.max(0L, worldObj.getTotalWorldTime() - clientProgressTick) * Math.max(1, getSpeed());
		return (int) (Math.min(duration, projected) * i / duration);
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
		this.runtimeInitialized = false;
		this.inventoryFingerprintInitialized = false;
		this.observedRecipeRevision = -1L;
		this.lastAccountingTick = Long.MIN_VALUE;
		this.lastBatteryChargeTick = Long.MIN_VALUE;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
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
		if(!runtimeEnergyMutation && !runtimeSettling && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
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

	@Override protected void beforeInventorySlotChanged(int slot) {
		if(!runtimeSettling && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override protected void beforeFluidStorageChanged(FluidTank changedTank) {
		if(!runtimeSettling && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override public void onChunkUnload() {
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
		lastAccountingTick = Long.MIN_VALUE;
		lastBatteryChargeTick = Long.MIN_VALUE;
		super.onChunkUnload();
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
