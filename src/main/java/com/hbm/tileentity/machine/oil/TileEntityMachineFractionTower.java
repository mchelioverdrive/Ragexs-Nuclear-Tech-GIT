package com.hbm.tileentity.machine.oil;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.FractionRecipes;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.BufPacket;
import com.hbm.tileentity.IBufPacketReceiver;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.Tuple.Pair;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

public class TileEntityMachineFractionTower extends TileEntityLoadedBase implements IBufPacketReceiver, IFluidStandardTransceiver, IFluidCopiable {
	private static final int TASK_PROCESS = 1;
	private boolean runtimeFluidMutation;
	private boolean runtimeInitialized;
	private boolean stackPresent;
	private boolean lowerNotifiedOfOutput;
	private FluidType runtimeInputType;
	private DirPos[] runtimeConnections;
	private final FluidTank.ChangeListener tankListener = new FluidTank.ChangeListener() {
		@Override public void onTankChanged(FluidTank tank) {
			if(worldObj != null && !worldObj.isRemote && !runtimeFluidMutation) markMachineFluidDirty();
		}
	};
	
	public FluidTank[] tanks;
	
	public TileEntityMachineFractionTower() {
		tanks = new FluidTank[3];
		tanks[0] = new FluidTank(Fluids.HEAVYOIL, 4000);
		tanks[1] = new FluidTank(Fluids.BITUMEN, 4000);
		tanks[2] = new FluidTank(Fluids.SMEAR, 4000);
		for(FluidTank tank : tanks) tank.setChangeListener(tankListener);
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.refreshConnections();
		this.refreshStack();
		if(runtimeInputType != tanks[0].getTankType()) {
			runtimeFluidMutation = true;
			try { this.setupTanks(); }
			finally { runtimeFluidMutation = false; }
			runtimeInputType = tanks[0].getTankType();
		}
		this.updateConnections();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.notifyLowerTower();
		this.sendRuntimePacket();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		if(this.refreshStack()) this.markMachineDirty(MachineDirtyCause.TOPOLOGY);
		this.updateConnections();
		this.sendRuntimePacket();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_PROCESS || taskSlot != 0 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		runtimeFluidMutation = true;
		try { this.processTowerStep(); }
		finally { runtimeFluidMutation = false; }
		this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.notifyLowerTower();
		this.sendRuntimePacket();
	}

	private boolean refreshStack() {
		boolean present = worldObj.getTileEntity(xCoord, yCoord + 3, zCoord) instanceof TileEntityMachineFractionTower;
		boolean changed = present != stackPresent;
		stackPresent = present;
		return changed;
	}

	private void refreshConnections() {
		if(runtimeConnections == null) runtimeConnections = this.getConPos();
	}

	private void evaluateAndSchedule(long now) {
		boolean outputFlow = tanks[1].getFill() > 0 || tanks[2].getFill() > 0;
		boolean inputReady = tanks[0].getFill() > 0;
		TileEntity upper = stackPresent ? worldObj.getTileEntity(xCoord, yCoord + 3, zCoord) : null;
		boolean upstreamReady = upper instanceof TileEntityMachineFractionTower &&
			(((TileEntityMachineFractionTower) upper).tanks[1].getFill() > 0 || ((TileEntityMachineFractionTower) upper).tanks[2].getFill() > 0);
		boolean fractionReady = false;
		if(inputReady && !stackPresent && tanks[0].getFill() >= 100) {
			Pair<FluidStack, FluidStack> recipe = FractionRecipes.getFractions(tanks[0].getTankType());
			fractionReady = recipe != null && this.hasSpace(recipe.getKey().fill, recipe.getValue().fill);
		}
		if(outputFlow || upstreamReady || (stackPresent && inputReady) || fractionReady) {
			long nextFraction = now + (10L - now % 10L);
			this.scheduleMachineTransition(outputFlow || upstreamReady || stackPresent ? now + 1L : nextFraction, TASK_PROCESS, 0);
		} else this.cancelMachineTransition(TASK_PROCESS, 0);
	}

	private void notifyLowerTower() {
		if(tanks[1].getFill() == 0 && tanks[2].getFill() == 0) {
			lowerNotifiedOfOutput = false;
			return;
		}
		if(lowerNotifiedOfOutput) return;
		TileEntity below = worldObj.getTileEntity(xCoord, yCoord - 3, zCoord);
		if(below instanceof TileEntityMachineFractionTower) {
			((TileEntityMachineFractionTower) below).markMachineFluidDirty();
			lowerNotifiedOfOutput = true;
		}
	}

	private void sendRuntimePacket() {
		PacketDispatcher.wrapper.sendToAllAround(new BufPacket(xCoord, yCoord, zCoord, this), new TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 50));
	}
	
	@Override
	public void updateEntity() {
		// Fractionation, stack transfer, and fluid output are runtime-owned.
	}

	private void processTowerStep() {
			TileEntity stack = worldObj.getTileEntity(xCoord, yCoord + 3, zCoord);
			
			if(stack instanceof TileEntityMachineFractionTower) {
				TileEntityMachineFractionTower frac = (TileEntityMachineFractionTower) stack;
				
				//make types equal
				for(int i = 0; i < 3; i++) {
					frac.tanks[i].setTankType(tanks[i].getTankType());
				}
				
				//calculate transfer
				int oil = Math.min(tanks[0].getFill(), frac.tanks[0].getMaxFill() - frac.tanks[0].getFill());
				int left = Math.min(frac.tanks[1].getFill(), tanks[1].getMaxFill() - tanks[1].getFill());
				int right = Math.min(frac.tanks[2].getFill(), tanks[2].getMaxFill() - tanks[2].getFill());
				
				//move oil up, pull fractions down
				tanks[0].setFill(tanks[0].getFill() - oil);
				tanks[1].setFill(tanks[1].getFill() + left);
				tanks[2].setFill(tanks[2].getFill() + right);
				frac.tanks[0].setFill(frac.tanks[0].getFill() + oil);
				frac.tanks[1].setFill(frac.tanks[1].getFill() - left);
				frac.tanks[2].setFill(frac.tanks[2].getFill() - right);
			}
			
			if(worldObj.getTotalWorldTime() % 10 == 0)
				fractionate();

			this.sendFluid();
	}

	@Override
	public void serialize(ByteBuf buf) {
		for(int i = 0; i < 3; i++)
			tanks[i].serialize(buf);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		for(int i = 0; i < 3; i++)
			tanks[i].deserialize(buf);
	}
	
	private void updateConnections() {
		
		for(DirPos pos : runtimeConnections) {
			this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}
	
	private void sendFluid() {
		
		for(DirPos pos : runtimeConnections) {
			this.sendFluid(tanks[1], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.sendFluid(tanks[2], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}
	
	private DirPos[] getConPos() {
		return new DirPos[] {
				new DirPos(xCoord + 2, yCoord, zCoord, Library.POS_X),
				new DirPos(xCoord - 2, yCoord, zCoord, Library.NEG_X),
				new DirPos(xCoord, yCoord, zCoord + 2, Library.POS_Z),
				new DirPos(xCoord, yCoord, zCoord - 2, Library.NEG_Z)
		};
	}
	
	private void setupTanks() {
		
		Pair<FluidStack, FluidStack> quart = FractionRecipes.getFractions(tanks[0].getTankType());
		
		if(quart != null) {
			tanks[1].setTankType(quart.getKey().type);
			tanks[2].setTankType(quart.getValue().type);
		} else {
			tanks[0].setTankType(Fluids.NONE);
			tanks[1].setTankType(Fluids.NONE);
			tanks[2].setTankType(Fluids.NONE);
		}
	}
	
	private void fractionate() {
		
		Pair<FluidStack, FluidStack> quart = FractionRecipes.getFractions(tanks[0].getTankType());
		
		if(quart != null) {
			
			int left = quart.getKey().fill;
			int right = quart.getValue().fill;
			
			if(tanks[0].getFill() >= 100 && hasSpace(left, right)) {
				tanks[0].setFill(tanks[0].getFill() - 100);
				tanks[1].setFill(tanks[1].getFill() + left);
				tanks[2].setFill(tanks[2].getFill() + right);
			}
		}
	}
	
	private boolean hasSpace(int left, int right) {
		return tanks[1].getFill() + left <= tanks[1].getMaxFill() && tanks[2].getFill() + right <= tanks[2].getMaxFill();
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		for(int i = 0; i < 3; i++)
			tanks[i].readFromNBT(nbt, "tank" + i);
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		for(int i = 0; i < 3; i++)
			tanks[i].writeToNBT(nbt, "tank" + i);
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
}
