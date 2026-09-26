package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.List;

import com.hbm.blocks.ModBlocks;
import com.hbm.extprop.HbmPlayerProps;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.container.ContainerCrystallizer;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUICrystallizer;
import com.hbm.inventory.recipes.CrystallizerRecipes;
import com.hbm.inventory.recipes.CrystallizerRecipes.CrystallizerRecipe;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.main.MainRegistry;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.BobMathUtil;
import com.hbm.util.I18nUtil;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluid.IFluidStandardReceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineCrystallizer extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardReceiver, IGUIProvider, IUpgradeInfoProvider, IFluidCopiable {
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();

	
	public long energyQuanta;
	public static final long maxPower = 1000000;
	public static final int demand = 1000;
	public short progress;
	public short duration = 600;
	public boolean isOn;
	
	public float angle;
	public float prevAngle;
	
	public FluidTank tank;
	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeStateInitialized;
	private boolean runtimeEnergyMutation;
	private long nextRuntimeTick = -1L;
	private long observedRecipeRevision = -1L;
	private long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(demand);
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private ItemStack cachedRecipeInput;
	private int cachedRecipeMeta;
	private int cachedRecipeTagHash;
	private com.hbm.inventory.fluid.FluidType cachedRecipeFluid;
	private long cachedRecipeRevision = -1L;
	private CrystallizerRecipe cachedRecipe;

	public TileEntityMachineCrystallizer() {
		super(8);
		tank = new FluidTank(Fluids.PEROXIDE, 8000);
		this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.crystallizer";
	}

	@Override
	public void updateEntity() {
		
		if(worldObj.isRemote) {
			
			prevAngle = angle;
			
			if(isOn) {
				angle += 5F * this.getCycleCount();
				
				if(angle >= 360) {
					angle -= 360;
					prevAngle -= 360;
				}
				
				if(worldObj.rand.nextInt(20) == 0 && MainRegistry.proxy.me().getDistance(xCoord + 0.5, yCoord + 6, zCoord + 0.5) < 50) {
					worldObj.spawnParticle("cloud", xCoord + worldObj.rand.nextDouble(), yCoord + 6.5D, zCoord + worldObj.rand.nextDouble(), 0.0, 0.1, 0.0);
				}
			}
		}
		
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
		List<EntityPlayer> players = worldObj.getEntitiesWithinAABB(EntityPlayer.class, AxisAlignedBB.getBoundingBox(xCoord + 0.25, yCoord + 1, zCoord + 0.25, xCoord + 0.75, yCoord + 6, zCoord + 0.75).offset(rot.offsetX * 1.5, 0, rot.offsetZ * 1.5));
		
		for(EntityPlayer player : players) {
			HbmPlayerProps props = HbmPlayerProps.getData(player);
			props.isOnLadder = true;
		}
	}
	
	private void updateConnections() {
		
		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tank.getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}
	
	protected DirPos[] getConPos() {

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
		buf.writeShort(progress);
		buf.writeShort(getDuration());
		buf.writeLong(energyQuanta);
		buf.writeBoolean(isOn);
		tank.serialize(buf);
	}
	
	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		progress = buf.readShort();
		duration = buf.readShort();
		energyQuanta = buf.readLong();
		isOn = buf.readBoolean();
		tank.deserialize(buf);
	}
	
	private void processItem() {

		CrystallizerRecipe result = this.resolveCachedRecipe();
		
		if(result == null) //never happens but you can't be sure enough
			return;
		
		ItemStack stack = result.output.copy();
		
		if(slots[2] == null)
			slots[2] = stack;
		else if(slots[2].stackSize + stack.stackSize <= slots[2].getMaxStackSize())
			slots[2].stackSize += stack.stackSize;
		
		tank.setFill(tank.getFill() - getRequiredAcid(result.acidAmount));
		
		float freeChance = this.getFreeChance();
		
		if(freeChance == 0 || freeChance < worldObj.rand.nextFloat())
			this.decrStackSize(0, result.itemAmount);
	}
	
	private boolean canProcess() {
		
		//Is there no input?
		if(slots[0] == null)
			return false;
		
		if(energyQuanta < getPowerRequired())
			return false;
		
		CrystallizerRecipe result = this.resolveCachedRecipe();
		
		//Or output?
		if(result == null)
			return false;
		
		//Not enough of the input item?
		if(slots[0].stackSize < result.itemAmount)
			return false;
		
		if(tank.getFill() < getRequiredAcid(result.acidAmount)) return false;
		
		ItemStack stack = result.output;
		
		//Does the output not match?
		if(slots[2] != null && (slots[2].getItem() != stack.getItem() || slots[2].getItemDamage() != stack.getItemDamage()))
			return false;
		
		//Or is the output slot already full?
		if(slots[2] != null && slots[2].stackSize + stack.stackSize > slots[2].getMaxStackSize())
			return false;
		
		return true;
	}

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		boolean settingsChanged = this.refreshRuntimeSettings(false);
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE | MachineDirtyCause.CONFIGURATION | MachineDirtyCause.UPGRADE)) != 0 || settingsChanged) this.resolveCachedRecipe();
		this.runtimeStateInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_ACCOUNTING || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeStateInitialized) return;
		nextRuntimeTick = -1L;
		long oldPower = energyQuanta;
		int oldProgress = progress;
		boolean oldOn = isOn;
		this.setRuntimePower(Library.chargeTEFromItems(slots, 1, energyQuanta, maxPower));
		isOn = false;
		for(int i = 0; i < getCycleCount(); i++) {
			if(canProcess()) {
				progress++;
				this.setRuntimePower(this.energyQuanta - EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts));
				isOn = true;
				if(progress > getDuration()) {
					progress = 0;
					processItem();
					this.markDirty();
				}
			} else {
				progress = 0;
			}
		}
		if(oldPower != energyQuanta || oldProgress != progress || oldOn != isOn) {
			this.markDirty();
			this.markNetworkDirty();
			this.networkPackNTIfDirty(25);
		}
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			boolean tankContainerChanged = tank.setType(7, slots);
			tankContainerChanged |= tank.loadTank(3, 4, slots);
			boolean inventoryChanged = this.observeInventoryFingerprint();
			if(tankContainerChanged || inventoryChanged) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
			return;
		}
		if(cadence != 20) return;
		boolean recipeChanged = observedRecipeRevision != SerializableRecipe.getRegistryRevision();
		boolean settingsChanged = this.refreshRuntimeSettings(true);
		if(recipeChanged) observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		if(recipeChanged || settingsChanged) this.markMachineDirty((recipeChanged ? MachineDirtyCause.RECIPE : 0) | (settingsChanged ? MachineDirtyCause.UPGRADE : 0));
		this.updateConnections();
		this.networkPackNTIfDirty(25);
	}

	private boolean refreshRuntimeSettings(boolean contentAware) {
		long oldPower = operatingPowerWatts;
		if(contentAware) this.upgradeManager.checkSlots(slots, 5, 6);
		else this.upgradeManager.checkSlotsIfDirty(slots, 5, 6);
		operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(this.getPowerRequired());
		return oldPower != operatingPowerWatts;
	}

	private void evaluateAndSchedule(long now) {
		boolean shouldRun = (energyQuanta > 0 && canProcess()) || progress > 0 || this.hasBatteryWork();
		if(!shouldRun) {
			this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_MAIN);
			nextRuntimeTick = -1L;
			return;
		}
		nextRuntimeTick = now + 1L;
		this.scheduleMachineTransition(nextRuntimeTick, TASK_ACCOUNTING, TASK_SLOT_MAIN);
	}

	private CrystallizerRecipe resolveCachedRecipe() {
		ItemStack input = slots[0];
		int meta = input == null ? 0 : input.getItemDamage();
		int tagHash = input == null || input.getTagCompound() == null ? 0 : input.getTagCompound().hashCode();
		com.hbm.inventory.fluid.FluidType fluid = tank.getTankType();
		long revision = SerializableRecipe.getRegistryRevision();
		if(cachedRecipeInput != input || cachedRecipeMeta != meta || cachedRecipeTagHash != tagHash || cachedRecipeFluid != fluid || cachedRecipeRevision != revision) {
			cachedRecipeInput = input;
			cachedRecipeMeta = meta;
			cachedRecipeTagHash = tagHash;
			cachedRecipeFluid = fluid;
			cachedRecipeRevision = revision;
			cachedRecipe = CrystallizerRecipes.getOutput(input, fluid);
		}
		return cachedRecipe;
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
	
	public int getRequiredAcid(int base) {
		int efficiency = Math.min(this.upgradeManager.getLevel(UpgradeType.EFFECT), 3);
		if(efficiency > 0) {
			return base * (efficiency + 2);
		}
		return base;
	}
	
	public float getFreeChance() {
		int efficiency = Math.min(this.upgradeManager.getLevel(UpgradeType.EFFECT), 3);
		if(efficiency > 0) {
			return Math.min(efficiency * 0.05F, 0.15F);
		}
		return 0;
	}
	
	public short getDuration() {
		CrystallizerRecipe result = this.resolveCachedRecipe();
		int base = result != null ? result.duration : 600;
		int speed = Math.min(this.upgradeManager.getLevel(UpgradeType.SPEED), 3);
		if(speed > 0) {
			return (short) Math.ceil((base * Math.max(1F - 0.25F * speed, 0.25F)));
		}
		return (short) base;
	}
	
	public int getPowerRequired() {
		int speed = Math.min(this.upgradeManager.getLevel(UpgradeType.SPEED), 3);
		return (int) (demand + Math.min(speed * 1000, 3000));
	}
	
	public float getCycleCount() {
		int speed = this.upgradeManager.getLevel(UpgradeType.OVERDRIVE);
		return Math.min(1 + speed * 2, 7);
	}
	
	public long getPowerScaled(int i) {
		return (energyQuanta * i) / maxPower;
	}
	
	public int getProgressScaled(int i) {
		return (progress * i) / duration;
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
		progress = nbt.getShort("runtimeProgress");
		duration = nbt.hasKey("runtimeDuration") ? nbt.getShort("runtimeDuration") : 600;
		isOn = nbt.getBoolean("runtimeOn");
		runtimeStateInitialized = false;
		nextRuntimeTick = -1L;
		cachedRecipeRevision = -1L;
		tank.readFromNBT(nbt, "tank");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setShort("runtimeProgress", progress);
		nbt.setShort("runtimeDuration", duration);
		nbt.setBoolean("runtimeOn", isOn);
		tank.writeToNBT(nbt, "tank");
	}

	@Override
	protected void onInventorySlotChanged(int slot) {
		super.onInventorySlotChanged(slot);
		if(slot == 5 || slot == 6) this.upgradeManager.invalidate();
		if(slot == 0) this.cachedRecipeInput = null;
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemStack) {
		
		CrystallizerRecipe recipe = CrystallizerRecipes.getOutput(itemStack, tank.getTankType());
		if(i == 0 && recipe != null) {
			return true;
		}
		
		if(i == 1 && itemStack.getItem() instanceof IBatteryItem)
			return true;
		
		return false;
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return i == 2;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		
		return side == 0 ? new int[] { 2 } : new int[] { 0, 2 };
	}
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return TileEntity.INFINITE_EXTENT_AABB;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack stack) {
		super.setInventorySlotContents(i, stack);
		
		if(stack != null && i >= 5 && i <= 6 && stack.getItem() instanceof ItemMachineUpgrade) {
			worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, "hbm:item.upgradePlug", 1.0F, 1.0F);
		}
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tank};
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { tank };
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerCrystallizer(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUICrystallizer(player.inventory, this);
	}

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED || type == UpgradeType.EFFECT || type == UpgradeType.OVERDRIVE;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_crystallizer));
		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_DELAY, "-" + (level * 25) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "+" + (level * 100) + "%"));
		}
		if(type == UpgradeType.EFFECT) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_EFFICIENCY, "+" + (level * 5) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(this.KEY_ACID, "+" + (level * 100 + 100) + "%"));
		}
		if(type == UpgradeType.OVERDRIVE) {
			info.add((BobMathUtil.getBlink() ? EnumChatFormatting.RED : EnumChatFormatting.DARK_GRAY) + "YES");
		}
	}

	@Override
	public int getMaxLevel(UpgradeType type) {
		if(type == UpgradeType.SPEED) return 3;
		if(type == UpgradeType.EFFECT) return 3;
		if(type == UpgradeType.OVERDRIVE) return 3;
		return 0;
	}

	@Override
	public int[] getFluidIDToCopy() {
		return new int[]{ tank.getTankType().getID()};
	}

	@Override
	public FluidTank getTankToPaste() {
		return tank;
	}
}
