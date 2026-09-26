package com.hbm.tileentity.machine;

import java.io.IOException;
import java.util.HashSet;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.blocks.ModBlocks;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.orbit.WorldProviderOrbit;
import com.hbm.dim.trait.CBT_Water;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.main.MainRegistry;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.INBTPacketReceiver;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;

public abstract class TileEntityMachinePumpBase extends TileEntityLoadedBase implements IFluidStandardTransceiver, INBTPacketReceiver, IConfigurableMachine, IFluidCopiable {
	private static final int TASK_PUMP = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	protected boolean runtimeMachineMutation;
	private DirPos[] cachedPumpConnections;
	private int cachedConnectionMetadata = Integer.MIN_VALUE;
	private final FluidTank.ChangeListener pumpTankListener = new FluidTank.ChangeListener() {
		@Override public void onTankChanged(FluidTank tank) {
			if(!runtimeMachineMutation) {
				markDirty();
				markMachineDirty(MachineDirtyCause.FLUID);
			}
		}
	};

	public static final HashSet<Block> validBlocks = new HashSet();

	static {
		validBlocks.add(Blocks.grass);
		validBlocks.add(Blocks.dirt);
		validBlocks.add(Blocks.stone); //why was this not here before. it's GROUND WATER.
		// Are you a dumbass?
		// Are you ESL?
		validBlocks.add(Blocks.sand);
		validBlocks.add(Blocks.mycelium);
		validBlocks.add(ModBlocks.waste_earth);
		validBlocks.add(ModBlocks.dirt_dead);
		validBlocks.add(ModBlocks.dirt_oily);
		validBlocks.add(ModBlocks.sand_dirty);
		validBlocks.add(ModBlocks.sand_dirty_red);
		//if it's dirty shouldn't it pollute the water? Why did this idiot add glyphids?
		validBlocks.add(ModBlocks.eve_silt);
		validBlocks.add(ModBlocks.eve_rock);
		validBlocks.add(ModBlocks.ike_regolith);
		validBlocks.add(ModBlocks.ike_stone);
		validBlocks.add(ModBlocks.duna_sands);
		validBlocks.add(ModBlocks.moon_turf);
		validBlocks.add(ModBlocks.laythe_silt);
		validBlocks.add(ModBlocks.moho_regolith);
		validBlocks.add(ModBlocks.minmus_smooth);
	}

	public FluidTank water;

	public boolean isOn = false;
	public float rotor;
	public float lastRotor;
	public boolean onGround = false;
	public int groundCheckDelay = 0;

	public static int groundHeight = 70;
	public static int groundDepth = 4;
	public static int steamSpeed = 1_000;
	public static int electricSpeed = 10_000;
	public static int nonWaterDebuff = 100;

	@Override
	public String getConfigName() {
		return "waterpump";
	}

	@Override
	public void readIfPresent(JsonObject obj) {
		groundHeight = IConfigurableMachine.grab(obj, "I:groundHeight", groundHeight);
		groundDepth = IConfigurableMachine.grab(obj, "I:groundDepth", groundDepth);
		steamSpeed = IConfigurableMachine.grab(obj, "I:steamSpeed", steamSpeed);
		electricSpeed = IConfigurableMachine.grab(obj, "I:electricSpeed", electricSpeed);
	}

	@Override
	public void writeConfig(JsonWriter writer) throws IOException {
		writer.name("I:groundHeight").value(groundHeight);
		writer.name("I:groundDepth").value(groundDepth);
		writer.name("I:steamSpeed").value(steamSpeed);
		writer.name("I:electricSpeed").value(electricSpeed);
	}

	public void updateEntity() {
		if(worldObj.isRemote) {
			this.lastRotor = this.rotor;
			if(this.isOn) this.rotor += 10F;

			if(this.rotor >= 360F) {
				this.rotor -= 360F;
				this.lastRotor -= 360F;

				MainRegistry.proxy.playSoundClient(xCoord, yCoord, zCoord, "hbm:block.steamEngineOperate", 0.5F, 0.75F);
				MainRegistry.proxy.playSoundClient(xCoord, yCoord, zCoord, "game.neutral.swim.splash", 1F, 0.5F);
			}
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		for(FluidTank tank : this.getAllTanks()) if(tank != null) tank.setChangeListener(this.pumpTankListener);
		runtimeInitialized = true;
		if((causes & MachineDirtyCause.LIFECYCLE) != 0) {
			runtimeMachineMutation = true;
			try {
				onGround = this.checkGround();
				this.updatePumpConnections();
			} finally { runtimeMachineMutation = false; }
		}
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		INBTPacketReceiver.networkPack(this, this.getSync(), 150);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_PUMP || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		runtimeMachineMutation = true;
		try {
			for(DirPos pos : this.getCachedConnections()) if(water.getFill() > 0) this.sendFluid(water, worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.sendAdditionalFluids();
			this.isOn = false;
			if(this.canOperate() && yCoord <= groundHeight && onGround) {
				this.isOn = true;
				this.operate();
			}
		} finally { runtimeMachineMutation = false; }
		this.markDirty();
		INBTPacketReceiver.networkPack(this, this.getSync(), 150);
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote || cadence != 20) return;
		boolean oldGround = onGround;
		com.hbm.inventory.fluid.FluidType oldWaterType = water.getTankType();
		runtimeMachineMutation = true;
		try {
			onGround = this.checkGround();
			this.updatePumpConnections();
		} finally { runtimeMachineMutation = false; }
		groundCheckDelay = 20;
		if(oldGround != onGround || oldWaterType != water.getTankType()) {
			this.markDirty();
			INBTPacketReceiver.networkPack(this, this.getSync(), 150);
		}
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	protected void updatePumpConnections() { }
	protected void sendAdditionalFluids() { }

	private void evaluateAndSchedule(long now) {
		if(runtimeInitialized && (water.getFill() > 0 || this.hasAdditionalFluidToSend() || yCoord <= groundHeight && onGround && this.canOperate())) this.scheduleMachineTransition(now + 1L, TASK_PUMP, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_PUMP, TASK_SLOT_MAIN);
	}

	protected boolean hasAdditionalFluidToSend() { return false; }

	protected DirPos[] getCachedConnections() {
		int metadata = this.getBlockMetadata();
		if(cachedPumpConnections == null || cachedConnectionMetadata != metadata) {
			cachedPumpConnections = this.getConPos();
			cachedConnectionMetadata = metadata;
		}
		return cachedPumpConnections;
	}

	protected boolean checkGround() {

		if(worldObj.provider.hasNoSky) return false;
		if(worldObj.provider instanceof WorldProviderOrbit) return false;
		CBT_Water table = CelestialBody.getTrait(worldObj, CBT_Water.class);
		if(table == null) return false;

		water.setTankType(table.fluid);

		int validBlocks = 0;
		int invalidBlocks = 0;

		for(int x = -1; x <= 1; x++) {
			for(int y = -1; y >= -groundDepth; y--) {
				for(int z = -1; z <= 1; z++) {

					Block b = worldObj.getBlock(xCoord + x, yCoord + y, zCoord + z);

					if(y == -1 && !b.isNormalCube()) return false; // first layer has to be full solid

					if(this.validBlocks.contains(b)) validBlocks++;
					else invalidBlocks ++;
				}
			}
		}

		return validBlocks >= invalidBlocks; // valid block count has to be at least 50%
	}

	protected NBTTagCompound getSync() {
		NBTTagCompound data = new NBTTagCompound();
		data.setBoolean("isOn", isOn);
		data.setBoolean("onGround", onGround);
		water.writeToNBT(data, "w");
		return data;
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		this.isOn = nbt.getBoolean("isOn");
		this.onGround = nbt.getBoolean("onGround");
		water.readFromNBT(nbt, "w");
	}

	protected abstract boolean canOperate();
	protected abstract void operate();

	protected DirPos[] getConPos() {
		return new DirPos[] {
				new DirPos(xCoord + 2, yCoord, zCoord, Library.POS_X),
				new DirPos(xCoord - 2, yCoord, zCoord, Library.NEG_X),
				new DirPos(xCoord, yCoord, zCoord + 2, Library.POS_Z),
				new DirPos(xCoord, yCoord, zCoord - 2, Library.NEG_Z)
		};
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] {water};
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {water};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[0];
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
					yCoord + 5,
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
	public FluidTank getTankToPaste() {
		return null;
	}
}
