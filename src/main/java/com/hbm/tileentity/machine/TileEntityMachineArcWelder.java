package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.List;

import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.container.ContainerMachineArcWelder;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineArcWelder;
import com.hbm.inventory.recipes.ArcWelderRecipes;
import com.hbm.inventory.recipes.ArcWelderRecipes.ArcWelderRecipe;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.packet.PacketDispatcher;
import com.hbm.tileentity.*;
import com.hbm.packet.toclient.AuxParticlePacketNT;
import com.hbm.util.I18nUtil;
import com.hbm.util.FurnaceGasEmission;
import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.IBatteryItem;
import api.hbm.fluid.IFluidStandardReceiver;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
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

public class TileEntityMachineArcWelder extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardReceiver, IConditionalInvAccess, IGUIProvider, IUpgradeInfoProvider, IFluidCopiable {
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();

	
	public long energyQuanta;
	public long maxPower = 2_000;
	public long consumption;
	
	public int progress;
	public int processTime = 1;
	
	public FluidTank tank;
	public ItemStack display;
	private ArcWelderRecipe cachedRecipe;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private long observedRecipeRevision;
	private static final int TASK_PROCESS = 1;
	private static final int TASK_SLOT_MAIN = 0;

	public TileEntityMachineArcWelder() {
		super(8);
		this.tank = new FluidTank(Fluids.NONE, 24_000);
		this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.machineArcWelder";
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack stack) {
		super.setInventorySlotContents(i, stack);
		
		if(stack != null && stack.getItem() instanceof ItemMachineUpgrade && i >= 6 && i <= 7) {
			worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, "hbm:item.upgradePlug", 1.0F, 1.0F);
		}
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
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.TOPOLOGY)) != 0) this.updateConnections();
		observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNT(25);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_PROCESS || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long oldEnergy = energyQuanta;
		int oldProgress = progress;
		int oldInventoryFingerprint = this.inventoryFingerprint();
		if(inventoryFingerprintInitialized && oldInventoryFingerprint != observedInventoryFingerprint) this.refreshRuntimeState();
		this.beginMachineFluidMutation();
		runtimeEnergyMutation = true;
		try {
			this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 4, this.getStoredEnergyQuanta(), this.getEnergyCapacityQuanta()));
			if(cachedRecipe != null) {
				if(this.canProcess(cachedRecipe)) {
					progress++;
					this.setStoredEnergyQuanta(energyQuanta - consumption);
					FurnaceGasEmission.emitCarbonMonoxide(worldObj, xCoord, yCoord, zCoord, 1200);
					if(progress >= processTime) {
						progress = 0;
						this.consumeItems(cachedRecipe);
						if(slots[3] == null) slots[3] = cachedRecipe.output.copy();
						else slots[3].stackSize += cachedRecipe.output.stackSize;
						this.refreshCachedRecipe();
						this.markDirty();
					}
					if(worldObj.getTotalWorldTime() % 2 == 0) this.sendArcParticle();
				} else {
					progress = 0;
				}
			} else {
				progress = 0;
			}
			this.maxPower = Math.max(cachedRecipe == null ? 2_000L : cachedRecipe.consumption * 20L, energyQuanta);
		} finally {
			runtimeEnergyMutation = false;
			this.endMachineFluidMutation();
		}
		boolean inventoryChanged = oldInventoryFingerprint != this.inventoryFingerprint();
		if(inventoryChanged) this.markNetworkDirty();
		this.observeInventoryFingerprint();
		if(oldEnergy != energyQuanta || oldProgress != progress || inventoryChanged) this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNT(25);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | MachineDirtyCause.FLUID | MachineDirtyCause.UPGRADE);
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
		this.beginMachineFluidMutation();
		try {
			tank.setType(5, slots);
		} finally {
			this.endMachineFluidMutation();
		}
		this.refreshCachedRecipe();
		this.upgradeManager.checkSlots(slots, 6, 7);
		if(cachedRecipe != null) {
			int redLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.SPEED), 3);
			int blueLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.POWER), 3);
			this.processTime = cachedRecipe.duration - (cachedRecipe.duration * redLevel / 6) + (cachedRecipe.duration * blueLevel / 3);
			this.consumption = cachedRecipe.consumption + (cachedRecipe.consumption * redLevel) - (cachedRecipe.consumption * blueLevel / 6);
			this.maxPower = Math.max(cachedRecipe.consumption * 20L, energyQuanta);
		} else {
			this.consumption = 100;
			this.maxPower = Math.max(2_000L, energyQuanta);
		}
		this.observeInventoryFingerprint();
	}

	private void refreshCachedRecipe() {
		cachedRecipe = ArcWelderRecipes.getRecipe(slots[0], slots[1], slots[2]);
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(this.canProcess(cachedRecipe) || this.hasBatteryWork() || progress > 0) this.scheduleMachineTransition(now + 1L, TASK_PROCESS, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_PROCESS, TASK_SLOT_MAIN);
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= this.getEnergyCapacityQuanta() || slots[4] == null) return false;
		if(slots[4].getItem() == com.hbm.items.ModItems.battery_creative || slots[4].getItem() == com.hbm.items.ModItems.fusion_core_infinite) return true;
		if(!(slots[4].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[4].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[4]) > 0;
	}

	private void updateConnections() {
		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			if(tank.getTankType() != Fluids.NONE) this.trySubscribe(tank.getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}

	private void sendArcParticle() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
		NBTTagCompound data = new NBTTagCompound();
		data.setString("type", worldObj.getTotalWorldTime() % 20 == 0 ? "tau" : "hadron");
		data.setByte("count", (byte) 5);
		PacketDispatcher.wrapper.sendToAllAround(new AuxParticlePacketNT(data, xCoord + 0.5 - dir.offsetX * 0.5, yCoord + 1.25, zCoord + 0.5 - dir.offsetZ * 0.5), new TargetPoint(worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 25));
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
	
	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(energyQuanta);
		buf.writeLong(maxPower);
		buf.writeLong(consumption);
		buf.writeInt(progress);
		buf.writeInt(processTime);
		
		tank.serialize(buf);
		
		ArcWelderRecipe recipe = cachedRecipe;
		
		if(recipe != null) {
			buf.writeBoolean(true);
			buf.writeInt(Item.getIdFromItem(recipe.output.getItem()));
			buf.writeInt(recipe.output.getItemDamage());
		} else
			buf.writeBoolean(false);
	}
	
	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		energyQuanta = buf.readLong();
		maxPower = buf.readLong();
		consumption = buf.readLong();
		progress = buf.readInt();
		processTime = buf.readInt();
		
		tank.deserialize(buf);
		
		if(buf.readBoolean()) {
			this.display = new ItemStack(Item.getItemById(buf.readInt()), 1, buf.readInt());
		} else
			this.display = null;
	}
	
	public boolean canProcess(ArcWelderRecipe recipe) {
		if(recipe == null) return false;
		if(this.energyQuanta < this.consumption) return false;
		
		if(recipe.fluid != null) {
			if(this.tank.getTankType() != recipe.fluid.type) return false;
			if(this.tank.getFill() < recipe.fluid.fill) return false;
		}
		
		if(slots[3] != null) {
			if(slots[3].getItem() != recipe.output.getItem()) return false;
			if(slots[3].getItemDamage() != recipe.output.getItemDamage()) return false;
			if(slots[3].stackSize + recipe.output.stackSize > slots[3].getMaxStackSize()) return false;
		}
		
		return true;
	}
	
	public void consumeItems(ArcWelderRecipe recipe) {
		
		for(AStack aStack : recipe.ingredients) {
			
			for(int i = 0; i < 3; i++) {
				ItemStack stack = slots[i];
				if(aStack.matchesRecipe(stack, true) && stack.stackSize >= aStack.stacksize) {
					this.decrStackSize(i, aStack.stacksize);
					break;
				}
			}
		}
		
		if(recipe.fluid != null) {
			this.tank.setFill(tank.getFill() - recipe.fluid.fill);
		}
	}
	
	protected DirPos[] getConPos() {
		
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
		
		return new DirPos[] {
				new DirPos(xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ, dir),
				new DirPos(xCoord + dir.offsetX + rot.offsetX, yCoord, zCoord + dir.offsetZ + rot.offsetZ, dir),
				new DirPos(xCoord + dir.offsetX - rot.offsetX, yCoord, zCoord + dir.offsetZ - rot.offsetZ, dir),
				new DirPos(xCoord - dir.offsetX * 2, yCoord, zCoord - dir.offsetZ * 2, dir.getOpposite()),
				new DirPos(xCoord - dir.offsetX * 2 + rot.offsetX, yCoord, zCoord - dir.offsetZ * 2 + rot.offsetZ, dir.getOpposite()),
				new DirPos(xCoord - dir.offsetX * 2 - rot.offsetX, yCoord, zCoord - dir.offsetZ * 2 - rot.offsetZ, dir.getOpposite()),
				new DirPos(xCoord + rot.offsetX * 2, yCoord, zCoord + rot.offsetZ * 2, rot),
				new DirPos(xCoord - dir.offsetX + rot.offsetX * 2, yCoord, zCoord - dir.offsetZ + rot.offsetZ * 2, rot),
				new DirPos(xCoord - rot.offsetX * 2, yCoord, zCoord - rot.offsetZ * 2, rot.getOpposite()),
				new DirPos(xCoord - dir.offsetX - rot.offsetX * 2, yCoord, zCoord - dir.offsetZ - rot.offsetZ * 2, rot.getOpposite())
		};
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.maxPower = EnergyUnits.readCapacityQuanta(nbt, "maxPower");
		this.progress = nbt.getInteger("progress");
		this.processTime = nbt.getInteger("processTime");
		tank.readFromNBT(nbt, "t");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		EnergyUnits.writeCapacityQuanta(nbt, maxPower);
		nbt.setInteger("progress", progress);
		nbt.setInteger("processTime", processTime);
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
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
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
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		return slot < 3;
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack  stack, int side) {
		return slot == 3;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[] { 1, 3 };
	}

	@Override
	public boolean isItemValidForSlot(int x, int y, int z, int slot, ItemStack stack) {
		return slot < 3;
	}

	@Override
	public boolean canInsertItem(int x, int y, int z, int slot, ItemStack stack, int side) {
		return slot < 3;
	}

	@Override
	public boolean canExtractItem(int x, int y, int z, int slot, ItemStack stack, int side) {
		return slot == 3;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int x, int y, int z, int side) {
		BlockPos pos = new BlockPos(x, y, z);
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
		BlockPos core = new BlockPos(xCoord, yCoord, zCoord);
		
		//Red
		if(pos.equals(core.clone().offset(rot)) || pos.equals(core.clone().offset(rot.getOpposite()).offset(dir.getOpposite())))
			return new int[] { 0, 3 };
		
		//Yellow
		if(pos.equals(core.clone().offset(dir.getOpposite())))
			return new int[] { 1, 3 };
		
		//Green
		if(pos.equals(core.clone().offset(rot.getOpposite())) || pos.equals(core.clone().offset(rot).offset(dir.getOpposite())))
			return new int[] { 2, 3 };
		
		return new int[] { };
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineArcWelder(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineArcWelder(player.inventory, this);
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
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_arc_welder));
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
}
