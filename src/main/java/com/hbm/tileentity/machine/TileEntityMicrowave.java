package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import api.hbm.energymk2.IBatteryItem;
import com.hbm.handler.CompatHandler;
import com.hbm.interfaces.ICopiable;
import com.hbm.inventory.container.ContainerMicrowave;
import com.hbm.inventory.gui.GUIMicrowave;
import com.hbm.items.ModItems;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;

import api.hbm.energymk2.IEnergyReceiverMK2;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

@Optional.InterfaceList({@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")})
public class TileEntityMicrowave extends TileEntityMachineBase implements IEnergyReceiverMK2, IGUIProvider, SimpleComponent, CompatHandler.OCComponent, ICopiable {

	public long energyQuanta;
	public static final long maxPower = 50000;
	public static final int consumption = 50;
	public static final int maxTime = 300;
	public int time;
	public int speed;
	public static final int maxSpeed = 5;
	private ItemStack cachedResult;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private int observedRecipeCount;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private static final int TASK_PROCESS = 1;
	private static final int TASK_SLOT_MAIN = 0;

	public TileEntityMicrowave() {
		super(3);
	}

	@Override
	public String getName() {
		return "container.microwave";
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
		this.refreshCachedResult();
		this.observeInventoryFingerprint();
		this.observedRecipeCount = FurnaceRecipes.smelting().getSmeltingList().size();
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.TOPOLOGY)) != 0) this.updateConnections();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_PROCESS || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long oldEnergy = energyQuanta;
		int oldTime = time;
		int oldInventoryFingerprint = this.inventoryFingerprint();
		runtimeEnergyMutation = true;
		try {
			this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 2, energyQuanta, maxPower));
			if(this.canProcess()) {
				if(time >= maxTime) {
					this.process();
					time = 0;
					this.refreshCachedResult();
				}
				if(this.canProcess()) {
					this.setStoredEnergyQuanta(energyQuanta - consumption);
					time += speed * 2;
				}
			}
		} finally {
			runtimeEnergyMutation = false;
		}
		boolean inventoryChanged = oldInventoryFingerprint != this.inventoryFingerprint();
		if(inventoryChanged) this.markNetworkDirty();
		this.observeInventoryFingerprint();
		if(oldEnergy != energyQuanta || oldTime != time || inventoryChanged) this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | MachineDirtyCause.ENERGY);
		} else if(cadence == 20) {
			this.updateConnections();
		} else if(cadence == 100) {
			int count = FurnaceRecipes.smelting().getSmeltingList().size();
			if(count != observedRecipeCount) {
				observedRecipeCount = count;
				this.markMachineDirty(MachineDirtyCause.RECIPE);
			}
		}
	}

	private void refreshCachedResult() {
		ItemStack result = slots[0] == null ? null : FurnaceRecipes.smelting().getSmeltingResult(slots[0]);
		cachedResult = result == null ? null : result.copy();
	}

	private boolean canProcess() {
		if(speed == 0 || energyQuanta < consumption || slots[0] == null || cachedResult == null) return false;
		if(!(slots[0].getItem() instanceof ItemFood) && !(cachedResult.getItem() instanceof ItemFood)) return false;
		if(slots[1] == null) return true;
		return cachedResult.isItemEqual(slots[1]) && cachedResult.stackSize + slots[1].stackSize <= cachedResult.getMaxStackSize();
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[2] == null) return false;
		if(slots[2].getItem() == ModItems.battery_creative || slots[2].getItem() == ModItems.fusion_core_infinite) return true;
		if(!(slots[2].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[2].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[2]) > 0;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(this.canProcess() || this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_PROCESS, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_PROCESS, TASK_SLOT_MAIN);
	}

	private void updateConnections() {
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) this.trySubscribe(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		data.setInteger("time", time);
		data.setInteger("speed", speed);
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

	public void networkUnpack(NBTTagCompound data) {
		super.networkUnpack(data);

		energyQuanta = EnergyUnits.readEnergyQuanta(data, "power");
		time = data.getInteger("time");
		speed = data.getInteger("speed");
	}

	public void handleButtonPacket(int value, int meta) {

		if(value == 0)
			speed++;

		if(value == 1)
			speed--;

		if(speed < 0)
			speed = 0;

		if(speed > maxSpeed)
			speed = maxSpeed;
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
	}

	private void process() {

		ItemStack stack = cachedResult.copy();

		if(slots[0].getItem() == ModItems.squirrel) {
			worldObj.playSoundEffect(
				xCoord + 0.5D,
				yCoord + 0.5D,
				zCoord + 0.5D,
				"hbm:entity.alvinmicrowave",
				1.0F,
				1.0F
			);
		}

		if(slots[1] == null) {
			slots[1] = stack;
		} else {
			slots[1].stackSize += stack.stackSize;
		}

		this.decrStackSize(0, 1);

		this.markDirty();
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemStack) {
		return i == 0 && FurnaceRecipes.smelting().getSmeltingResult(itemStack) != null;
	}

	@Override
	public boolean canInsertItem(int i, ItemStack itemStack, int j) {
		return this.isItemValidForSlot(i, itemStack);
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return i == 1;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return side == 0 ? new int[] { 1 } : new int[] { 0 };
	}

	public long getPowerScaled(int i) {
		return (energyQuanta * i) / maxPower;
	}

	public int getProgressScaled(int i) {
		return (time * i) / maxTime;
	}

	public int getSpeedScaled(int i) {
		return (speed * i) / maxSpeed;
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return TileEntity.INFINITE_EXTENT_AABB;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared()
	{
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
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		speed = nbt.getInteger("speed");
		time = nbt.getInteger("time");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setInteger("speed", speed);
		nbt.setInteger("time", time);
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public String getComponentName() {
		return "microwave";
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] test(Context context, Arguments args) {
		return new Object[] {"This is a testing device for everything OC."};
	}

	@Callback(direct = true, getter = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] variableget(Context context, Arguments args) {
		return new Object[] {speed, "test of the `getter` callback function"};
	}

	@Callback(direct = true, setter = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] variableset(Context context, Arguments args) {
		speed = MathHelper.clamp_int(args.checkInteger(0), 0, 5);
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
		return new Object[] {"test of the `setter` callback function"};
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMicrowave(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMicrowave(player.inventory, this);
	}

	@Override
	public NBTTagCompound getSettings(World world, int x, int y, int z) {
		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setInteger("microSpeed", speed);
		return null;
	}

	@Override
	public void pasteSettings(NBTTagCompound nbt, int index, World world, EntityPlayer player, int x, int y, int z) {
		if(nbt.hasKey("microSpeed")) {
			speed = nbt.getInteger("microSpeed");
			this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
		}
	}

	@Override
	public String[] infoForDisplay(World world, int x, int y, int z) {
		return new String[]{ "copyTool.speed"};
	}
}
