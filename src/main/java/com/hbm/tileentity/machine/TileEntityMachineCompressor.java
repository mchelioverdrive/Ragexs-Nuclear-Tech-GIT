package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import api.hbm.energymk2.IBatteryItem;
import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.container.ContainerCompressor;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUICompressor;
import com.hbm.inventory.recipes.CompressorRecipes;
import com.hbm.inventory.recipes.CompressorRecipes.CompressorRecipe;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.main.MainRegistry;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.BobMathUtil;
import com.hbm.util.I18nUtil;
import com.hbm.util.Tuple.Pair;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineCompressor extends TileEntityMachineBase implements IGUIProvider, IControlReceiver, IEnergyReceiverMK2, IFluidStandardTransceiver, IUpgradeInfoProvider, IFluidCopiable {
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();

	
	public FluidTank[] tanks;
	public long energyQuanta;
	public static final long maxPower = 100_000;
	public boolean isOn;
	public int progress;
	public int processTime = 100;
	public static final int processTimeBase = 100;
	public int powerRequirement;
	public static final int powerRequirementBase = 2_500;
	
	public float fanSpin;
	public float prevFanSpin;
	public float piston;
	public float prevPiston;
	public boolean pistonDir;
	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private CompressorRecipe cachedRecipe;
	private FluidType cachedRecipeInputType;
	private int cachedRecipeInputPressure = Integer.MIN_VALUE;
	private long cachedRecipeRevision = -1L;
	private long observedRecipeRevision = -1L;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private long operatingPowerWatts;
	private boolean runtimeMaterialEligible;

	public TileEntityMachineCompressor() {
		super(4);
		this.tanks = new FluidTank[2];
		this.tanks[0] = new FluidTank(Fluids.NONE, 16_000);
		this.tanks[1] = new FluidTank(Fluids.NONE, 16_000).withPressure(1);
		this.trackMachineFluidTank(this.tanks[0]);
		this.trackMachineFluidTank(this.tanks[1]);
	}

	@Override
	public String getName() {
		return "container.machineCompressor";
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			
			this.prevFanSpin = this.fanSpin;
			this.prevPiston = this.piston;
			
			if(this.isOn) {
				this.fanSpin += 15;
				
				if(this.fanSpin >= 360) {
					this.prevFanSpin -= 360;
					this.fanSpin -= 360;
				}
				
				if(this.pistonDir) {
					this.piston -= randSpeed;
					if(this.piston <= 0) {
						MainRegistry.proxy.playSoundClient(xCoord, yCoord, zCoord, "hbm:item.boltgun", this.getVolume(0.5F), 0.75F);
						this.pistonDir = !this.pistonDir;
					}
				} else {
					this.piston += 0.05F;
					if(this.piston >= 1) {
						this.randSpeed = 0.085F + worldObj.rand.nextFloat() * 0.03F;
						this.pistonDir = !this.pistonDir;
					}
				}
				
				this.piston = MathHelper.clamp_float(this.piston, 0F, 1F);
			}
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		this.refreshRecipe();
		this.refreshRuntimeSettings();
		this.setupTanks();
		this.runtimeMaterialEligible = this.canProcessMaterials();
		this.observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		runtimeInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_ACCOUNTING || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long now = worldObj.getTotalWorldTime();
		int oldProgress = progress;
		long oldPower = energyQuanta;
		this.setRuntimePower(Library.chargeTEFromItems(slots, 1, energyQuanta, maxPower));
		boolean canRun = runtimeMaterialEligible && this.hasOperatingPower();
		if(canRun && progress + 1 >= processTime) canRun = this.canProcessMaterials() && this.hasOperatingPower();
		if(canRun) {
			progress++;
			this.setRuntimePower(energyQuanta - EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts));
			isOn = true;
			if(progress >= processTime) {
				progress = 0;
				this.process();
				runtimeMaterialEligible = this.canProcessMaterials();
				this.markChanged();
			}
		} else {
			progress = 0;
			isOn = false;
		}
		if(oldProgress != progress || oldPower != energyQuanta) this.markDirty();
		this.sendRuntimeState();
		this.evaluateAndSchedule(now);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			boolean inventoryChanged = this.observeInventoryFingerprint();
			boolean containerChanged = this.tanks[0].setType(0, slots);
			if(inventoryChanged || containerChanged) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE | MachineDirtyCause.UPGRADE);
			for(DirPos pos : getConPos()) this.sendFluid(tanks[1], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			return;
		}
		if(cadence != 20) return;
		this.updateConnections();
		if(observedRecipeRevision != SerializableRecipe.getRegistryRevision()) {
			observedRecipeRevision = SerializableRecipe.getRegistryRevision();
			this.markMachineDirty(MachineDirtyCause.RECIPE);
		}
		this.sendRuntimeState();
	}

	private void refreshRuntimeSettings() {
		this.upgradeManager.checkSlots(slots, 1, 3);
		int speedLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.SPEED), 3);
		int powerLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.POWER), 3);
		int overLevel = this.upgradeManager.getLevel(UpgradeType.OVERDRIVE);
		int timeBase = this.cachedRecipe == null ? processTimeBase : this.cachedRecipe.duration;
		if(this.cachedRecipe == null) this.processTime = speedLevel == 3 ? 10 : speedLevel == 2 ? 20 : speedLevel == 1 ? 60 : timeBase;
		else this.processTime = timeBase / (speedLevel + 1);
		this.powerRequirement = this.powerRequirementBase / (powerLevel + 1);
		this.processTime /= overLevel + 1;
		this.powerRequirement *= (overLevel * 2) + 1;
		if(this.processTime <= 0) this.processTime = 1;
		this.operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(this.powerRequirement);
	}

	private void refreshRecipe() {
		FluidType type = tanks[0].getTankType();
		int pressure = tanks[0].getPressure();
		long revision = SerializableRecipe.getRegistryRevision();
		if(type != cachedRecipeInputType || pressure != cachedRecipeInputPressure || revision != cachedRecipeRevision) {
			cachedRecipeInputType = type;
			cachedRecipeInputPressure = pressure;
			cachedRecipeRevision = revision;
			cachedRecipe = CompressorRecipes.recipes.get(new Pair(type, pressure));
		}
	}

	private boolean canProcessMaterials() {
		this.refreshRecipe();
		if(cachedRecipe == null) return tanks[0].getFill() >= 1000 && tanks[1].getFill() + 1000 <= tanks[1].getMaxFill();
		return tanks[0].getFill() > cachedRecipe.inputAmount && tanks[1].getFill() + cachedRecipe.output.fill <= tanks[1].getMaxFill();
	}

	private boolean hasOperatingPower() {
		return this.energyQuanta > this.powerRequirement;
	}

	private boolean hasBatteryWork() {
		return energyQuanta < maxPower && slots[1] != null && slots[1].getItem() instanceof IBatteryItem;
	}

	private void evaluateAndSchedule(long now) {
		if((runtimeMaterialEligible && this.hasOperatingPower()) || this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_ACCOUNTING, TASK_SLOT_MAIN);
		else {
			this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_MAIN);
			isOn = false;
		}
	}

	private void setRuntimePower(long value) {
		runtimeEnergyMutation = true;
		try { this.setStoredEnergyQuanta(value); } finally { runtimeEnergyMutation = false; }
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

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		data.setInteger("progress", progress);
		data.setInteger("processTime", processTime);
		data.setInteger("powerRequirement", powerRequirement);
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		tanks[0].writeToNBT(data, "0");
		tanks[1].writeToNBT(data, "1");
		data.setBoolean("isOn", isOn);
		this.networkPack(data, 100);
	}
	
	private float randSpeed = 0.1F;
	
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
		
		this.progress = nbt.getInteger("progress");
		this.processTime = nbt.getInteger("processTime");
		this.powerRequirement = nbt.getInteger("powerRequirement");
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		tanks[0].readFromNBT(nbt, "0");
		tanks[1].readFromNBT(nbt, "1");
		this.isOn = nbt.getBoolean("isOn");
	}
	
	private void updateConnections() {
		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}
	
	public DirPos[] getConPos() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
		
		return new DirPos[] {
				new DirPos(xCoord + rot.offsetX * 2, yCoord, zCoord + rot.offsetZ * 2, rot),
				new DirPos(xCoord - rot.offsetX * 2, yCoord, zCoord - rot.offsetZ * 2, rot.getOpposite()),
				new DirPos(xCoord - dir.offsetX * 2, yCoord, zCoord - dir.offsetZ * 2, dir.getOpposite()),
		};
	}
	
	public boolean canProcess() {
		
		if(this.energyQuanta <= powerRequirement) return false;
		return this.canProcessMaterials();
	}
	
	public void process() {
		
		this.refreshRecipe();
		this.beginMachineFluidMutation();
		try {
		if(cachedRecipe == null) {
			tanks[0].setFill(tanks[0].getFill() - 1_000);
			tanks[1].setFill(tanks[1].getFill() + 1_000);
		} else {
			tanks[0].setFill(tanks[0].getFill() - cachedRecipe.inputAmount);
			tanks[1].setFill(tanks[1].getFill() + cachedRecipe.output.fill);
		}
		} finally { this.endMachineFluidMutation(); }
		this.markMachineDirty(MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
	}
	
	protected void setupTanks() {
		
		this.refreshRecipe();
		if(cachedRecipe == null) {
			tanks[1].withPressure(tanks[0].getPressure() + 1).setTankType(tanks[0].getTankType());
		} else {
			tanks[1].withPressure(cachedRecipe.output.pressure).setTankType(cachedRecipe.output.type);
		}
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		progress = nbt.getInteger("progress");
		tanks[0].readFromNBT(nbt, "0");
		tanks[1].readFromNBT(nbt, "1");
		runtimeInitialized = false;
		inventoryFingerprintInitialized = false;
		cachedRecipeInputType = null;
		cachedRecipeRevision = -1L;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setInteger("progress", progress);
		tanks[0].writeToNBT(nbt, "0");
		tanks[1].writeToNBT(nbt, "1");
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerCompressor(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUICompressor(player.inventory, this);
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return this.isUseableByPlayer(player);
	}

	@Override
	public void receiveControl(NBTTagCompound data) {
		int compression = data.getInteger("compression");
		
		if(compression != tanks[0].getPressure()) {
			tanks[0].withPressure(compression);
			this.refreshRecipe();
			if(cachedRecipe == null) {
				tanks[1].withPressure(compression + 1);
			} else {
				tanks[1].withPressure(cachedRecipe.output.pressure).setTankType(cachedRecipe.output.type);
			}
			
			this.markChanged();
			this.markMachineDirty(MachineDirtyCause.CONFIGURATION | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
		}
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
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
		return tanks;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {tanks[1]};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tanks[0]};
	}
	
	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 2,
					yCoord,
					zCoord - 2,
					xCoord + 3,
					yCoord + 9,
					zCoord + 3
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
		return type == UpgradeType.SPEED || type == UpgradeType.POWER || type == UpgradeType.OVERDRIVE;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_compressor));
		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + "Generic compression: "+ I18nUtil.resolveKey(this.KEY_DELAY, "-" + (level == 3 ? 90 : level == 2 ? 80 : level == 1 ? 40 : 0) + "%"));
			info.add(EnumChatFormatting.GREEN + "Recipe: "+ I18nUtil.resolveKey(this.KEY_DELAY, "-" + (100 - 100 / (level + 1)) + "%"));
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
		if(type == UpgradeType.OVERDRIVE) return 9;
		return 0;
	}

	@Override
	public NBTTagCompound getSettings(World world, int x, int y, int z) {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setIntArray("fluidID", getFluidIDToCopy());
		tag.setInteger("compression", tanks[0].getPressure());
		return tag;
	}

	@Override
	public void pasteSettings(NBTTagCompound nbt, int index, World world, EntityPlayer player, int x, int y, int z) {
		if(nbt.hasKey("compression")) {
			int compression = nbt.getInteger("compression");

			if (compression != tanks[0].getPressure()) {
				tanks[0].withPressure(compression);

				CompressorRecipe recipe = CompressorRecipes.recipes.get(new Pair(tanks[0].getTankType(), compression));

				if (recipe == null) {
					tanks[1].withPressure(compression + 1);
				} else {
					tanks[1].withPressure(recipe.output.pressure).setTankType(recipe.output.type);
				}

				this.markChanged();
			}
		}
		IFluidCopiable.super.pasteSettings(nbt, index, world, player, x, y, z);
	}
}
