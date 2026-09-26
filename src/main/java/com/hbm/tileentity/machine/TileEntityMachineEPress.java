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
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
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

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		this.upgradeManager.checkSlots(slots, 4, 4);
		this.runtimeSpeed = 1 + Math.min(3, this.upgradeManager.getLevel(UpgradeType.SPEED));
		this.resolveCachedOutput();
		observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		runtimeInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_PRESS || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long beforePower = energyQuanta;
		int beforePress = press;
		int beforeDelay = delay;
		boolean beforeRetracting = isRetracting;
		this.setRuntimePower(Library.chargeTEFromItems(slots, 0, energyQuanta, maxPower));
		boolean canProcess = this.canProcess();
		if((canProcess || this.isRetracting || this.delay > 0) && energyQuanta >= 100) {
			this.setRuntimePower(energyQuanta - 100L);
			if(delay <= 0) {
				int stampSpeed = this.isRetracting ? 20 : 45;
				stampSpeed *= (1D + (double) runtimeSpeed / 4D);
				if(this.isRetracting) {
					this.press -= stampSpeed;
					if(this.press <= 0) {
						this.isRetracting = false;
						this.delay = 5 - runtimeSpeed + 1;
					}
				} else if(canProcess) {
					this.press += stampSpeed;
					if(this.press >= this.maxPress) this.completePressOperation();
				} else if(this.press > 0) {
					this.isRetracting = true;
				}
			} else {
				delay--;
			}
		}
		if(beforePower != energyQuanta || beforePress != press || beforeDelay != delay || beforeRetracting != isRetracting) this.markDirty();
		this.sendRuntimeState();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
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
		return energyQuanta < maxPower && slots[0] != null && slots[0].getItem() instanceof IBatteryItem;
	}

	private boolean hasScheduledPressWork() {
		return this.canProcess() || this.isRetracting || this.delay > 0;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if((this.hasScheduledPressWork() && energyQuanta >= 100) || this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_PRESS, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_PRESS, TASK_SLOT_MAIN);
	}

	private void setRuntimePower(long value) {
		runtimeEnergyMutation = true;
		try { this.setStoredEnergyQuanta(value); } finally { runtimeEnergyMutation = false; }
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		data.setInteger("press", press);
		if(slots[2] != null) {
			NBTTagCompound stack = new NBTTagCompound();
			slots[2].writeToNBT(stack);
			data.setTag("stack", stack);
		}
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
		inventoryFingerprintInitialized = false;
		cachedRecipeRevision = -1L;
		observedRecipeRevision = -1L;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		nbt.setInteger("press", press);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setBoolean("ret", isRetracting);
		nbt.setInteger("delay", delay);
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
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
