package com.hbm.tileentity.machine.oil;

import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.CrackingRecipes;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.INBTPacketReceiver;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineCatalyticCracker extends com.hbm.tileentity.TileEntityMachineBase implements api.hbm.energymk2.IEnergyReceiverMK2, INBTPacketReceiver, IFluidStandardTransceiver, IFluidCopiable, com.hbm.realsim.IMachineInfo {

	public final com.hbm.realsim.ProcessBatch physicalBatch = new com.hbm.realsim.ProcessBatch();
    public long energyQuanta;
    public FluidTank[] tanks;
	private static final int TASK_CRACK = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeFluidMutation;
	private FluidType observedInputType;
	private FluidType observedSteamType;
	private final FluidTank.ChangeListener tankChangeListener = new FluidTank.ChangeListener() {
		@Override public void onTankChanged(FluidTank tank) {
			if(!runtimeFluidMutation && worldObj != null && !worldObj.isRemote) markMachineDirty(MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
			markDirty();
		}
	};

	public TileEntityMachineCatalyticCracker() {
        super(1);
		tanks = new FluidTank[5];
		tanks[0] = new FluidTank(Fluids.BITUMEN, 4000);
		tanks[1] = new FluidTank(Fluids.STEAM, 8000);
		tanks[2] = new FluidTank(Fluids.OIL, 4000);
		tanks[3] = new FluidTank(Fluids.PETROLEUM, 4000);
		tanks[4] = new FluidTank(Fluids.SPENTSTEAM, 800);
		for(FluidTank tank : tanks) tank.setChangeListener(tankChangeListener);
	}

	@Override
	public void updateEntity() {
		// Server processing is scheduled through MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.updateTankTypes();
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.TOPOLOGY)) != 0 || observedInputType != tanks[0].getTankType() || observedSteamType != tanks[1].getTankType()) this.updateConnections();
		observedInputType = tanks[0].getTankType();
		observedSteamType = tanks[1].getTankType();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
        if(taskType != TASK_CRACK || worldObj == null || worldObj.isRemote) return;
        runtimeFluidMutation = true;
        try { com.hbm.realsim.ProcessMachine.step(this, physicalBatch, CrackingRecipes.getPhysical(tanks[0].getTankType()), new FluidTank[] {tanks[0]}, new FluidTank[] {tanks[2], tanks[3]}, slots, 0, -1, 0, 0); }
        finally { runtimeFluidMutation = false; }
        if(worldObj.getTotalWorldTime() % 5L == 0L) this.sendOutputFluids();
        if(worldObj.getTotalWorldTime() % 20L == 0L) this.sendRuntimeState();
        this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		long now = worldObj.getTotalWorldTime();
		if(cadence == 5 && now % 10L == 0L) {
			this.sendOutputFluids();
			this.sendRuntimeState();
		} else if(cadence == 20) {
			this.updateConnections();
			observedInputType = tanks[0].getTankType();
			observedSteamType = tanks[1].getTankType();
		}
	}

	private void updateTankTypes() {
        com.hbm.realsim.ProcessMachine.configure(physicalBatch, CrackingRecipes.getPhysical(tanks[0].getTankType()), new FluidTank[] {tanks[2], tanks[3]});
	}


	private void evaluateAndSchedule(long now) {
        if(physicalBatch.active() || CrackingRecipes.getPhysical(tanks[0].getTankType()) != null && tanks[0].getFill() > 0)
            this.scheduleMachineTransition(now + 1L, TASK_CRACK, TASK_SLOT_MAIN);
        else this.cancelMachineTransition(TASK_CRACK, TASK_SLOT_MAIN);
	}

	private void sendOutputFluids() {
		for(DirPos pos : getConPos()) {
			for(int i = 2; i <= 4; i++) {
				if(tanks[i].getFill() > 0) this.sendFluid(tanks[i], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			}
		}
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		for(int i = 0; i < 5; i++) tanks[i].writeToNBT(data, "tank" + i);
		INBTPacketReceiver.networkPack(this, data, 50);
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		for(int i = 0; i < 5; i++)
			tanks[i].readFromNBT(nbt, "tank" + i);
	}

	private void updateConnections() {

		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
            this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tanks[1].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
        physicalBatch.read(nbt); physicalBatch.electrifyUtilities();
        energyQuanta = api.hbm.energymk2.EnergyUnits.readEnergyQuanta(nbt, "power");
		super.readFromNBT(nbt);

		for(int i = 0; i < 5; i++)
			tanks[i].readFromNBT(nbt, "tank" + i);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
        physicalBatch.write(nbt);
        api.hbm.energymk2.EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		super.writeToNBT(nbt);

		for(int i = 0; i < 5; i++)
			tanks[i].writeToNBT(nbt, "tank" + i);
	}

	protected DirPos[] getConPos() {

		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

		return new DirPos[] {
				new DirPos(xCoord + dir.offsetX * 4 + rot.offsetX * 1, yCoord, zCoord + dir.offsetZ * 4 + rot.offsetZ * 1, dir),
				new DirPos(xCoord + dir.offsetX * 4 - rot.offsetX * 2, yCoord, zCoord + dir.offsetZ * 4 - rot.offsetZ * 2, dir),
				new DirPos(xCoord - dir.offsetX * 4 + rot.offsetX * 1, yCoord, zCoord - dir.offsetZ * 4 + rot.offsetZ * 1, dir.getOpposite()),
				new DirPos(xCoord - dir.offsetX * 4 - rot.offsetX * 2, yCoord, zCoord - dir.offsetZ * 4 - rot.offsetZ * 2, dir.getOpposite()),
				new DirPos(xCoord + dir.offsetX * 2 + rot.offsetX * 3, yCoord, zCoord + dir.offsetZ * 2 + rot.offsetZ * 3, rot),
				new DirPos(xCoord + dir.offsetX * 2 - rot.offsetX * 4, yCoord, zCoord + dir.offsetZ * 2 - rot.offsetZ * 4, rot),
				new DirPos(xCoord - dir.offsetX * 2 + rot.offsetX * 3, yCoord, zCoord - dir.offsetZ * 2 + rot.offsetZ * 3, rot.getOpposite()),
				new DirPos(xCoord - dir.offsetX * 2 - rot.offsetX * 4, yCoord, zCoord - dir.offsetZ * 2 - rot.offsetZ * 4, rot.getOpposite())
		};
	}

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 3,
					yCoord,
					zCoord - 3,
					xCoord + 4,
					yCoord + 16,
					zCoord + 4
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
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {tanks[2], tanks[3], tanks[4]};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tanks[0]};
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank getTankToPaste() {
		return tanks[0];
	}
    @Override public long getStoredEnergyQuanta() { return energyQuanta; }
    @Override public long getEnergyCapacityQuanta() { return 1_000_000L; }
    @Override public void setStoredEnergyQuanta(long amount) { energyQuanta = amount; markDirty(); markMachineEnergyDirty(); }
    @Override public String getName() { return "container.catalyticCracker"; }
    @Override public int[] getAccessibleSlotsFromSide(int side) { return new int[] {0}; }
    @Override public boolean canExtractItem(int slot, net.minecraft.item.ItemStack stack, int side) { return slot == 0; }

    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        com.hbm.realsim.MachineInfo.process(lines, physicalBatch.active() ? physicalBatch.recipe() : CrackingRecipes.getPhysical(tanks[0].getTankType()));
        lines.add("Blocked operation: check feed state, paid utilities and product storage");
    }
}
