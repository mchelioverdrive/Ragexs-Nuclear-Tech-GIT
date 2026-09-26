package com.hbm.tileentity.machine.oil;

import com.hbm.handler.pollution.PollutionHandler;
import com.hbm.handler.pollution.PollutionHandler.PollutionType;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.container.ContainerMachineCoker;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineCoker;
import com.hbm.inventory.recipes.CokerRecipes;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.main.MainRegistry;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.Tuple.Triplet;
import com.hbm.util.FurnaceGasEmission;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.fluid.IFluidStandardTransceiver;
import api.hbm.tile.IHeatSource;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

public class TileEntityMachineCoker extends TileEntityMachineBase implements IFluidStandardTransceiver, IGUIProvider, IFluidCopiable {

	public boolean wasOn;
	public int progress;
	public static int processTime = 20_000;
	
	public int heat;
	public static int maxHeat = 100_000;
	public static double diffusion = 0.25D;
	
	public FluidTank[] tanks;
	private Triplet<Integer, ItemStack, FluidStack> cachedRecipe;
	private boolean runtimeInitialized;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private FluidType observedInputType;
	private long observedRecipeRevision;
	private static final int TASK_SIMULATE = 1;
	private static final int TASK_SLOT_MAIN = 0;

	public TileEntityMachineCoker() {
		super(2);
		tanks = new FluidTank[2];
		tanks[0] = new FluidTank(Fluids.HEAVYOIL, 16_000);
		tanks[1] = new FluidTank(Fluids.OIL_COKER, 8_000);
		for(FluidTank tank : tanks) this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.machineCoker";
	}

	@Override
	public void updateEntity() {
		if(!worldObj.isRemote) return;
		if(this.wasOn && worldObj.getTotalWorldTime() % 2 == 0) {
			NBTTagCompound fx = new NBTTagCompound();
			fx.setString("type", "tower");
			fx.setFloat("lift", 10F);
			fx.setFloat("base", 0.75F);
			fx.setFloat("max", 3F);
			fx.setInteger("life", 200 + worldObj.rand.nextInt(50));
			fx.setInteger("color",0x404040);
			fx.setDouble("posX", xCoord + 0.5);
			fx.setDouble("posY", yCoord + 22);
			fx.setDouble("posZ", zCoord + 0.5);
			MainRegistry.proxy.effectNT(fx);
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20 | MachineExecutionStrategy.COARSE_100;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.refreshRuntimeState();
		FluidType inputType = tanks[0].getTankType();
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.TOPOLOGY)) != 0 || observedInputType != inputType) this.updateConnections();
		observedInputType = inputType;
		observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_SIMULATE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		int oldInputFill = tanks[0].getFill();
		int oldOutputFill = tanks[1].getFill();
		int oldInventoryFingerprint = this.inventoryFingerprint();
		wasOn = false;
		this.tryPullHeat();
		this.beginMachineFluidMutation();
		try {
			if(this.canProcess()) {
				int burn = heat / 100;
				if(burn > 0) {
					wasOn = true;
					progress += burn;
					heat -= burn;
					if(progress >= processTime) {
						this.markChanged();
						progress -= processTime;
						this.applyRecipeOutput();
					}
				}
				if(wasOn && worldObj.getTotalWorldTime() % 5 == 0) PollutionHandler.incrementPollution(worldObj, xCoord, yCoord, zCoord, PollutionType.SOOT, PollutionHandler.SOOT_PER_SECOND * 5);
				if(wasOn) FurnaceGasEmission.emitCarbonMonoxide(worldObj, xCoord, yCoord, zCoord, 300);
			}
		} finally {
			this.endMachineFluidMutation();
		}
		this.observeInventoryFingerprint();
		boolean inventoryChanged = oldInventoryFingerprint != observedInventoryFingerprint;
		if(inventoryChanged) this.markNetworkDirty();
		if(oldInputFill != tanks[0].getFill() || oldOutputFill != tanks[1].getFill() || inventoryChanged) this.markDirty();
		this.sendOutputFluid();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
			else if(this.hasHeatSource()) this.evaluateAndSchedule(worldObj.getTotalWorldTime());
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
	
	public boolean canProcess() {
		if(cachedRecipe == null) return false;
		int fillReq = cachedRecipe.getX();
		ItemStack output = cachedRecipe.getY();
		FluidStack byproduct = cachedRecipe.getZ();
		if(tanks[0].getFill() < fillReq) return false;
		if(byproduct != null && byproduct.fill + tanks[1].getFill() > tanks[1].getMaxFill()) return false;
		if(output != null && slots[1] != null) {
			if(output.getItem() != slots[1].getItem()) return false;
			if(output.getItemDamage() != slots[1].getItemDamage()) return false;
			if(output.stackSize + slots[1].stackSize > output.getMaxStackSize()) return false;
		}
		return true;
	}

	private void refreshRuntimeState() {
		this.beginMachineFluidMutation();
		try {
			tanks[0].setType(0, slots);
			cachedRecipe = CokerRecipes.getOutput(tanks[0].getTankType());
			if(cachedRecipe != null && cachedRecipe.getZ() != null) tanks[1].setTankType(cachedRecipe.getZ().type);
		} finally {
			this.endMachineFluidMutation();
		}
		this.observeInventoryFingerprint();
	}

	private void applyRecipeOutput() {
		if(cachedRecipe == null) return;
		ItemStack output = cachedRecipe.getY();
		FluidStack byproduct = cachedRecipe.getZ();
		if(output != null) {
			if(slots[1] == null) slots[1] = output.copy();
			else slots[1].stackSize += output.stackSize;
		}
		if(byproduct != null) tanks[1].setFill(tanks[1].getFill() + byproduct.fill);
		tanks[0].setFill(tanks[0].getFill() - cachedRecipe.getX());
	}

	private boolean hasHeatSource() {
		return worldObj.getTileEntity(xCoord, yCoord - 1, zCoord) instanceof IHeatSource;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(heat > 0 || this.hasHeatSource()) this.scheduleMachineTransition(now + 1L, TASK_SIMULATE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_SIMULATE, TASK_SLOT_MAIN);
	}

	private void updateConnections() {
		for(DirPos pos : getConPos()) this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}

	private void sendOutputFluid() {
		if(tanks[1].getFill() <= 0) return;
		for(DirPos pos : getConPos()) this.sendFluid(tanks[1], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		data.setBoolean("wasOn", wasOn);
		data.setInteger("heat", heat);
		data.setInteger("progress", progress);
		tanks[0].writeToNBT(data, "t0");
		tanks[1].writeToNBT(data, "t1");
		this.networkPack(data, 25);
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
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
		
		this.wasOn = nbt.getBoolean("wasOn");
		this.heat = nbt.getInteger("heat");
		this.progress = nbt.getInteger("progress");
		tanks[0].readFromNBT(nbt, "t0");
		tanks[1].readFromNBT(nbt, "t1");
	}
	
	protected void tryPullHeat() {
		
		if(this.heat >= this.maxHeat) return;
		
		TileEntity con = worldObj.getTileEntity(xCoord, yCoord - 1, zCoord);
		
		if(con instanceof IHeatSource) {
			IHeatSource source = (IHeatSource) con;
			int diff = source.getHeatStored() - this.heat;
			
			if(diff == 0) {
				return;
			}
			
			if(diff > 0) {
				diff = (int) Math.ceil(diff * diffusion);
				source.useUpHeat(diff);
				this.heat += diff;
				if(this.heat > this.maxHeat)
					this.heat = this.maxHeat;
				return;
			}
		}
		
		this.heat = Math.max(this.heat - Math.max(this.heat / 1000, 1), 0);
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return true;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[] { 1 };
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.tanks[0].readFromNBT(nbt, "t0");
		this.tanks[1].readFromNBT(nbt, "t1");
		this.progress = nbt.getInteger("prog");
		this.heat = nbt.getInteger("heat");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		this.tanks[0].writeToNBT(nbt, "t0");
		this.tanks[1].writeToNBT(nbt, "t1");
		nbt.setInteger("prog", progress);
		nbt.setInteger("heat", heat);
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { tanks[1] };
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { tanks[0] };
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
					yCoord + 23,
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
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineCoker(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineCoker(player.inventory, this);
	}
}
