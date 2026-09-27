package com.hbm.tileentity.machine;

import java.util.ArrayList;
import java.util.List;

import com.hbm.dim.CelestialBody;
import com.hbm.explosion.ExplosionLarge;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.ParticleUtil;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;

public class TileEntityMachineGasDock extends TileEntityMachineBase implements IFluidStandardTransceiver {
	private static final int TASK_LAUNCH_CYCLE = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean isJool;
	private long lastLaunchAccountingTick = -1L;

	public FluidTank[] tanks;
	
	public boolean hasRocket = true;
	public int launchTicks = 0;
	
	private AxisAlignedBB renderBoundingBox;

	public TileEntityMachineGasDock() {
		super(0);
		this.tanks = new FluidTank[3];
		this.tanks[0] = new FluidTank(Fluids.JOOLGAS, 64_000);
		this.tanks[1] = new FluidTank(Fluids.HYDROGEN, 32_000);
		this.tanks[2] = new FluidTank(Fluids.OXYGEN, 32_000);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		hasRocket = nbt.hasKey("hasRocker") ? nbt.getBoolean("hasRocker") : true;
		launchTicks = nbt.getInteger("launchTicks");
		lastLaunchAccountingTick = -1L;
		runtimeInitialized = false;
		tanks[0].readFromNBT(nbt, "gas");
		tanks[1].readFromNBT(nbt, "f1");
		tanks[2].readFromNBT(nbt, "f2");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setBoolean("hasRocker", hasRocket);
		long elapsed = worldObj != null && !worldObj.isRemote && isLoaded() && getMachineRuntimeBinding() != null && lastLaunchAccountingTick >= 0L
				? Math.max(0L, worldObj.getTotalWorldTime() - lastLaunchAccountingTick) : 0L;
		nbt.setInteger("launchTicks", this.projectLaunchTicks(elapsed));

		tanks[0].writeToNBT(nbt, "gas");
		tanks[1].writeToNBT(nbt, "f1");
		tanks[2].writeToNBT(nbt, "f2");
	}


	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			launchTicks = MathHelper.clamp_int(launchTicks + (hasRocket ? -1 : 1), hasRocket ? -20 : 0, 100);
			if(launchTicks > 0 && launchTicks < 100) {
				ParticleUtil.spawnGasFlame(worldObj, xCoord + 0.5, yCoord + 0.5 + launchTicks, zCoord + 0.5, 0.0, -1.0, 0.0);
	
				if(launchTicks < 10) {
					ExplosionLarge.spawnShock(worldObj, xCoord + 0.5, yCoord, zCoord + 0.5, 1 + worldObj.rand.nextInt(3), 1 + worldObj.rand.nextGaussian());
				}
			}
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		for(FluidTank tank : tanks) this.trackMachineFluidTank(tank);
		if((causes & MachineDirtyCause.LIFECYCLE) != 0) isJool = CelestialBody.getTarget(worldObj, xCoord, zCoord).body.getPlanet() == CelestialBody.getBody("jool");
		long now = worldObj.getTotalWorldTime();
		boolean wasRunning = runtimeInitialized;
		if(!runtimeInitialized || (causes & MachineDirtyCause.LIFECYCLE) != 0) lastLaunchAccountingTick = now;
		else this.advanceLaunchMotion(now - 1L);
		runtimeInitialized = true;
		this.evaluateAndSchedule(now);
		if(wasRunning && (tanks[0].getFill() > 0 || isJool && hasFuel() && (launchTicks <= -20 || launchTicks >= 100)))
			this.scheduleMachineTransition(now, TASK_LAUNCH_CYCLE, TASK_SLOT_MAIN);
		this.networkPackNTIfDirty(150);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_LAUNCH_CYCLE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		boolean oldRocket = hasRocket;
		int oldLaunchTicks = launchTicks;
		int oldGasFill = tanks[0].getFill();
		int oldHydrogenFill = tanks[1].getFill();
		int oldOxygenFill = tanks[2].getFill();
		this.beginMachineFluidMutation();
		try {
			for(DirPos pos : getConPos()) if(tanks[0].getFill() > 0) this.sendFluid(tanks[0], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.advanceLaunchMotion(worldObj.getTotalWorldTime());
			if(isJool && hasFuel()) {
				if(launchTicks <= -20) hasRocket = false;
				else if(launchTicks >= 100) hasRocket = true;
				if(launchTicks <= -20) this.collectGas();
			}
		} finally { this.endMachineFluidMutation(); }
		if(oldRocket != hasRocket || oldLaunchTicks != launchTicks || oldGasFill != tanks[0].getFill() || oldHydrogenFill != tanks[1].getFill() || oldOxygenFill != tanks[2].getFill()) {
			this.markDirty();
			this.markNetworkDirty();
		}
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNTIfDirty(150);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote || cadence != 20) return;
		this.updateConnections();
	}

	private void evaluateAndSchedule(long now) {
		boolean moving = hasRocket ? launchTicks > -20 : launchTicks < 100;
		boolean canToggle = isJool && hasFuel() && (launchTicks <= -20 || launchTicks >= 100);
		if(!runtimeInitialized || !(moving || canToggle || tanks[0].getFill() > 0)) {
			this.cancelMachineTransition(TASK_LAUNCH_CYCLE, TASK_SLOT_MAIN);
			return;
		}
		long delay = tanks[0].getFill() > 0 || canToggle ? 1L : hasRocket ? launchTicks + 20L : 100L - launchTicks;
		this.scheduleMachineTransition(now + delay, TASK_LAUNCH_CYCLE, TASK_SLOT_MAIN);
	}

	private int projectLaunchTicks(long elapsed) {
		return hasRocket ? (int) Math.max(-20L, (long) launchTicks - elapsed)
				: (int) Math.min(100L, (long) launchTicks + elapsed);
	}

	private void advanceLaunchMotion(long tick) {
		if(lastLaunchAccountingTick < 0L) {
			lastLaunchAccountingTick = tick;
			return;
		}
		if(tick <= lastLaunchAccountingTick) return;
		launchTicks = this.projectLaunchTicks(tick - lastLaunchAccountingTick);
		lastLaunchAccountingTick = tick;
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeBoolean(hasRocket);
		for(int i = 0; i < tanks.length; i++) tanks[i].serialize(buf);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		hasRocket = buf.readBoolean();
		for(int i = 0; i < tanks.length; i++) tanks[i].deserialize(buf);
	}
	
	private void updateConnections() {
		for(DirPos pos : getConPos()) {
			for(int i = 0; i < tanks.length; i++) {
				if(tanks[i].getTankType() != Fluids.NONE) {
					trySubscribe(tanks[i].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
				}
			}
		}
	}
	
	private void collectGas() {
		if(tanks[1].getFill() < 100) return;
		if(tanks[2].getFill() < 500) return;
		if(tanks[0].getFill() + 8000 > tanks[0].getMaxFill()) return;

		tanks[1].setFill(tanks[1].getFill() - 100);		
		tanks[2].setFill(tanks[2].getFill() - 500);
		tanks[0].setFill(tanks[0].getFill() + 8000);
	}
	
	private boolean hasFuel() {
		return tanks[1].getFill() >= 100 && tanks[2].getFill() >= 500;
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		if (renderBoundingBox == null) {
			renderBoundingBox = AxisAlignedBB.getBoundingBox(
				xCoord - 1,
				yCoord,
				zCoord - 1,
				xCoord + 2,
				yCoord + 1,
				zCoord + 2
			);
		}

		return renderBoundingBox;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {tanks[0]};	
		}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tanks[1], tanks[2]};
	}
	
	
	private DirPos[] conPos;

	
	protected DirPos[] getConPos() {
		if(conPos == null) {
			List<DirPos> list = new ArrayList<>();

			// Below
			for(int x = -1; x <= 1; x++) {
				for(int z = -1; z <= 1; z++) {
					list.add(new DirPos(xCoord + x, yCoord - 1, zCoord + z, Library.NEG_Y));
				}
			}

			// Sides
			for(int i = -1; i <= 1; i++) {
				list.add(new DirPos(xCoord + i, yCoord, zCoord + 2, Library.POS_Z));
				list.add(new DirPos(xCoord + i, yCoord, zCoord - 2, Library.NEG_Z));
				list.add(new DirPos(xCoord + 2, yCoord, zCoord + i, Library.POS_X));
				list.add(new DirPos(xCoord - 2, yCoord, zCoord + i, Library.NEG_X));
			}

			conPos = list.toArray(new DirPos[0]);
		}
		
		return conPos;
	}

	@Override
	public String getName() {
		return "container.gasDock";
	}

}
