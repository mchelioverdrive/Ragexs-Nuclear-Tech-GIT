package com.hbm.tileentity.machine.oil;

import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.CrackingRecipes;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.INBTPacketReceiver;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.Tuple.Pair;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineCatalyticCracker extends TileEntityLoadedBase implements INBTPacketReceiver, IFluidStandardTransceiver, IFluidCopiable {

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
		if(taskType != TASK_CRACK || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		boolean changed = false;
		runtimeFluidMutation = true;
		try {
			Pair<FluidStack, FluidStack> output = CrackingRecipes.getCracking(tanks[0].getTankType());
			if(output != null) {
				int left = output.getKey().fill;
				int right = output.getValue().fill;
				for(int i = 0; i < 2; i++) {
					if(this.canCrack(left, right)) {
						tanks[0].setFill(tanks[0].getFill() - 100);
						tanks[1].setFill(tanks[1].getFill() - 200);
						tanks[2].setFill(tanks[2].getFill() + left);
						tanks[3].setFill(tanks[3].getFill() + right);
						tanks[4].setFill(tanks[4].getFill() + 2);
						changed = true;
					}
				}
			}
		} finally {
			runtimeFluidMutation = false;
		}
		if(changed) markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		if(worldObj.getTotalWorldTime() % 10L == 0L) {
			this.sendOutputFluids();
			this.sendRuntimeState();
		}
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
		runtimeFluidMutation = true;
		try {
			Pair<FluidStack, FluidStack> output = CrackingRecipes.getCracking(tanks[0].getTankType());
			if(output != null) {
				tanks[1].setTankType(Fluids.STEAM);
				tanks[2].setTankType(output.getKey().type);
				tanks[3].setTankType(output.getValue().type);
				tanks[4].setTankType(Fluids.SPENTSTEAM);
			} else {
				tanks[2].setTankType(Fluids.NONE);
				tanks[3].setTankType(Fluids.NONE);
				tanks[4].setTankType(Fluids.NONE);
			}
		} finally {
			runtimeFluidMutation = false;
		}
	}

	private boolean canCrack(int left, int right) {
		return tanks[0].getFill() >= 100 && tanks[1].getFill() >= 200
				&& tanks[2].getFill() + left <= tanks[2].getMaxFill()
				&& tanks[3].getFill() + right <= tanks[3].getMaxFill()
				&& tanks[4].getFill() + 2 <= tanks[4].getMaxFill();
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		Pair<FluidStack, FluidStack> output = CrackingRecipes.getCracking(tanks[0].getTankType());
		if(output != null && this.canCrack(output.getKey().fill, output.getValue().fill)) {
			long due = now + (5L - now % 5L);
			this.scheduleMachineTransition(due, TASK_CRACK, TASK_SLOT_MAIN);
		} else {
			this.cancelMachineTransition(TASK_CRACK, TASK_SLOT_MAIN);
		}
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
			this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tanks[1].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		for(int i = 0; i < 5; i++)
			tanks[i].readFromNBT(nbt, "tank" + i);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
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
		return new FluidTank[] {tanks[0], tanks[1]};
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank getTankToPaste() {
		return tanks[0];
	}
}
