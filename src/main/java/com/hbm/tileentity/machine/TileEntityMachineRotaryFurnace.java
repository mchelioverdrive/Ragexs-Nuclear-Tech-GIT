package com.hbm.tileentity.machine;

import java.util.Random;

import com.hbm.handler.pollution.PollutionHandler;
import com.hbm.handler.pollution.PollutionHandler.PollutionType;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.container.ContainerMachineRotaryFurnace;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineRotaryFurnace;
import com.hbm.inventory.material.MaterialShapes;
import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.Mats.MaterialStack;
import com.hbm.inventory.recipes.RotaryFurnaceRecipes;
import com.hbm.inventory.recipes.RotaryFurnaceRecipes.RotaryFurnaceRecipe;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.main.MainRegistry;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.AuxParticlePacketNT;
import com.hbm.tileentity.IConditionalInvAccess;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachinePolluting;
import com.hbm.util.CrucibleUtil;
import com.hbm.util.FurnaceGasEmission;
import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineRotaryFurnace extends TileEntityMachinePolluting implements IFluidStandardTransceiver, IGUIProvider, IFluidCopiable, IConditionalInvAccess, com.hbm.realsim.IMachineInfo {
	private static final int TASK_PROCESS = 1;
	private boolean runtimeInitialized;
	private boolean runtimeAir;
	private RotaryFurnaceRecipe runtimeRecipe;
    private String recipeAccountingKey = "";
    private final com.hbm.realsim.ThermalAccount drive = new com.hbm.realsim.ThermalAccount();
    private final com.hbm.realsim.FuelCredit firebox = new com.hbm.realsim.FuelCredit();
    private long processHeatQuanta;
	private DirPos[] runtimeSteamPorts;
	private DirPos[] runtimeFluidPorts;
	private int observedOrientation = Integer.MIN_VALUE;

	public FluidTank[] tanks;
	public boolean isProgressing;
	public float progress;
	public int burnTime;
	public int maxBurnTime;
	public boolean isVenting;
	public MaterialStack output;
	public static final int maxOutput = MaterialShapes.BLOCK.q(16);

	public int anim;
	public int lastAnim;

	public TileEntityMachineRotaryFurnace() {
		super(5, 50);
		tanks = new FluidTank[3];
		tanks[0] = new FluidTank(Fluids.NONE, 32).withDefaultCapacity();
		tanks[1] = new FluidTank(Fluids.STEAM, 1200).withDefaultCapacity();
		tanks[2] = new FluidTank(Fluids.SPENTSTEAM, 12).withDefaultCapacity();
		for(FluidTank tank : tanks) this.trackMachineFluidTank(tank);
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.refreshPorts();
		this.beginMachineFluidMutation();
		try { tanks[0].setType(3, slots); }
		finally { this.endMachineFluidMutation(); }
		if((causes & (MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE | MachineDirtyCause.LIFECYCLE)) != 0)
			refreshRecipe();
		runtimeAir = this.breatheAir(0);
		this.subscribeToInputs();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNT(50);
	}


    private void refreshRecipe() {
        RotaryFurnaceRecipe next = RotaryFurnaceRecipes.getRecipe(slots[0], slots[1], slots[2]);
        if(next != runtimeRecipe || recipeAccountingKey.isEmpty()) {
            String key = new RotaryFurnaceRecipes().accountingKey(next);
            if(!recipeAccountingKey.equals(key)) { progress = 0; recipeAccountingKey = key; }
        }
        runtimeRecipe = next;
        int flow = runtimeRecipe == null ? 1 : runtimeRecipe.steam;
        tanks[1].resizePreservingContents(flow * 1200); tanks[2].resizePreservingContents(flow * 12);
        int capacity = runtimeRecipe == null || runtimeRecipe.fluid == null ? 32 : (int) Math.min(Integer.MAX_VALUE,
                Math.max(32L, Math.max(runtimeRecipe.fluid.fill, (runtimeRecipe.fluid.fill * 1200L + runtimeRecipe.duration - 1L) / runtimeRecipe.duration)));
        tanks[0].resizePreservingContents(capacity);
    }
    public boolean canDrive(int count) {
        if(count <= 0 || count > 4 || com.hbm.realsim.ThermalMachine.state(tanks[1]) != com.hbm.realsim.SteamLedger.State.ORDINARY
                || com.hbm.realsim.ThermalMachine.state(tanks[2]) != com.hbm.realsim.SteamLedger.State.SPENT || tanks[1].getFill() < count) return false;
        long room = Math.max(0L, (long) tanks[2].getMaxFill() - tanks[2].getFill()) * com.hbm.realsim.SteamLedger.State.SPENT.massPerUnit;
        return room - drive.remainder(com.hbm.realsim.SteamLedger.State.SPENT) >= com.hbm.realsim.SteamLedger.State.ORDINARY.mass(count);
    }
    /** Steam provides mechanical drum/blower work; furnace fuel remains the separate high-temperature source. */
    public boolean driveTick(long tick, int count) {
        if(!canDrive(count)) return false;
        com.hbm.realsim.SteamLedger.Transition expansion = com.hbm.realsim.SteamLedger.transition(com.hbm.realsim.SteamLedger.State.ORDINARY,
                com.hbm.realsim.SteamLedger.State.SPENT, com.hbm.realsim.SteamLedger.Route.TURBINE, 400000);
        long shaftQuanta = count * 2000L;
        com.hbm.realsim.ThermalAccount.Result result = drive.advance(tick, expansion, tanks[1].getFill(), tanks[2].getMaxFill() - tanks[2].getFill(),
                com.hbm.realsim.SteamLedger.State.ORDINARY.mass(count), 0, 0, shaftQuanta, 0, 0, shaftQuanta, Long.MAX_VALUE);
        tanks[1].setFill(tanks[1].getFill() - result.consumed); tanks[2].setFill(tanks[2].getFill() + result.produced);
        // This work turns the machinery. It is not also exported as electricity or furnace heat.
        return result.mass > 0;
    }

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		if(cadence == 5) {
			boolean air = this.breatheAir(0);
			if(air != runtimeAir) {
				runtimeAir = air;
				this.markMachineDirty(MachineDirtyCause.ENVIRONMENT);
			}
		} else if(cadence == 20) {
			this.refreshPorts();
			this.subscribeToInputs();
			this.networkPackNT(50);
		}
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_PROCESS || taskSlot != 0 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.beginMachineFluidMutation();
		try { this.runFurnaceStep(); }
		finally { this.endMachineFluidMutation(); }
		this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNT(50);
	}

	private void refreshPorts() {
		int orientation = this.getBlockMetadata();
		if(runtimeSteamPorts == null || observedOrientation != orientation) {
			runtimeSteamPorts = this.getSteamPos();
			runtimeFluidPorts = this.getFluidPos();
			observedOrientation = orientation;
		}
	}

	private void subscribeToInputs() {
		for(DirPos pos : runtimeSteamPorts) this.trySubscribe(tanks[1].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		if(tanks[0].getTankType() != Fluids.NONE)
			for(DirPos pos : runtimeFluidPorts) this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}

	private void evaluateAndSchedule(long now) {
		boolean fuelReady = runtimeRecipe != null && runtimeAir && com.hbm.realsim.SolidFuel.resolve(slots[4]) != null;
		if(output != null || burnTime > 0 || firebox.hasCredit() || processHeatQuanta > 0 || fuelReady || tanks[2].getFill() > 0 || smoke.getFill() > 0 || legacyFeedDrainable() || legacySteamDrainable())
			this.scheduleMachineTransition(now + 1L, TASK_PROCESS, 0);
		else {
			isProgressing = false;
			isVenting = false;
			this.cancelMachineTransition(TASK_PROCESS, 0);
		}
	}

	@Override
	public String getName() {
		return "container.machineRotaryFurnace";
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) this.updateClientAnimation();
	}

	private void runFurnaceStep() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
		ForgeDirection rot = dir.getRotation(ForgeDirection.DOWN);
			for(DirPos pos : runtimeSteamPorts) {
                if(legacySteamDrainable()) this.sendFluid(tanks[1], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
				if(tanks[2].getFill() > 0) this.sendFluid(tanks[2], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			}

            if(legacyFeedDrainable()) for(DirPos pos : runtimeFluidPorts) this.sendFluid(tanks[0], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			if(smoke.getFill() > 0) this.sendFluid(smoke, worldObj, xCoord + rot.offsetX, yCoord + 5, zCoord + rot.offsetZ, Library.POS_Y);

			if(this.output != null) {

				Vec3 impact = Vec3.createVectorHelper(0, 0, 0);
				MaterialStack leftover = CrucibleUtil.pourSingleStack(worldObj, xCoord + 0.5D + rot.offsetX * 2.875D, yCoord + 1.25D, zCoord + 0.5D + rot.offsetZ * 2.875D, 6, true, this.output, MaterialShapes.INGOT.q(1), impact);

				if(leftover.amount != this.output.amount) {
					this.output = leftover;
					NBTTagCompound data = new NBTTagCompound();
					data.setString("type", "foundry");
					data.setInteger("color", leftover.material.moltenColor);
					data.setByte("dir", (byte) rot.ordinal());
					data.setFloat("off", 0.625F);
					data.setFloat("base", 0.625F);
					data.setFloat("len", Math.max(1F, yCoord + 1 - (float) (Math.ceil(impact.yCoord) - 1.125)));
					PacketDispatcher.wrapper.sendToAllAround(new AuxParticlePacketNT(data, xCoord + 0.5D + rot.offsetX * 2.875D, yCoord + 0.75, zCoord + 0.5D + rot.offsetZ * 2.875D), new TargetPoint(worldObj.provider.dimensionId, xCoord + 0.5, yCoord + 1, zCoord + 0.5, 50));
				}

				if(output.amount <= 0) this.output = null;
			}

			RotaryFurnaceRecipe recipe = runtimeRecipe;
			this.isProgressing = false;

			boolean hasAir = runtimeAir = breatheAir(0);
			if(recipe != null && hasAir) {

                if(!firebox.hasCredit()) {
                    com.hbm.realsim.SolidFuel fuel = com.hbm.realsim.SolidFuel.resolve(slots[4]);
                    if(fuel != null) { this.decrStackSize(4, 1); firebox.reserve(fuel.identity, fuel.chemicalMicrojoules(), 1, com.hbm.realsim.Combustion.FIREBOX_EFFICIENCY_PPM); markChanged(); }
                }
                long duty = com.hbm.realsim.PetroleumProcesses.ceilDiv(recipe.thermalJoules * 2L, recipe.duration);
                if(this.canProcess(recipe)) processHeatQuanta += firebox.release(Math.max(0, duty - processHeatQuanta));

				if(this.canProcess(recipe) && processHeatQuanta >= duty && driveTick(worldObj.getTotalWorldTime(), recipe.steam)) {
                    processHeatQuanta -= duty;
					this.progress += 1F / recipe.duration;
					this.isProgressing = true;

					if(this.progress >= 1F) {
						this.progress -= 1F;
						this.consumeItems(recipe);
						refreshRecipe();

						if(this.output == null) {
							this.output = recipe.output.copy();
						} else {
							this.output.amount += recipe.output.amount;
						}
						this.markDirty();
					}

				} else {
					this.progress = 0;
				}
			} else {
				this.progress = 0;
			}

			this.isVenting = false;
			if(this.isProgressing && breatheAir(1)) {
				FurnaceGasEmission.emitCarbonMonoxide(worldObj, xCoord, yCoord, zCoord, 600);
				this.pollute(PollutionType.SOOT, PollutionHandler.SOOT_PER_SECOND / 10F);

			}

	}

	private void updateClientAnimation() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
		ForgeDirection rot = dir.getRotation(ForgeDirection.DOWN);
			if((this.burnTime > 0 || isProgressing) && MainRegistry.proxy.me().getDistance(xCoord, yCoord, zCoord) < 25) {
				Random rand = worldObj.rand;
				worldObj.spawnParticle("flame", xCoord + 0.5 + dir.offsetX * 0.5 + rot.offsetX + rand.nextGaussian() * 0.25, yCoord + 0.375, zCoord + 0.5 + dir.offsetZ * 0.5 + rot.offsetZ + rand.nextGaussian() * 0.25, 0, 0, 0);
			}

			if(isVenting && worldObj.getTotalWorldTime() % 2 == 0) {

				NBTTagCompound fx = new NBTTagCompound();
				fx.setString("type", "tower");
				fx.setFloat("lift", 10F);
				fx.setFloat("base", 0.25F);
				fx.setFloat("max", 2.5F);
				fx.setInteger("life", 100 + worldObj.rand.nextInt(20));
				fx.setInteger("color",0x202020);
				fx.setDouble("posX", xCoord + 0.5 + rot.offsetX);
				fx.setDouble("posY", yCoord + 5);
				fx.setDouble("posZ", zCoord + 0.5 + rot.offsetZ);
				MainRegistry.proxy.effectNT(fx);
			}
			this.lastAnim = this.anim;
			if(this.isProgressing) {
				this.anim++;
			}
	}

	@Override public void serialize(ByteBuf buf) {
		super.serialize(buf);
		tanks[0].serialize(buf);
		tanks[1].serialize(buf);
		tanks[2].serialize(buf);
		buf.writeBoolean(isVenting);
		buf.writeBoolean(isProgressing);
		buf.writeFloat(progress);
		buf.writeInt(burnTime);
		buf.writeInt(maxBurnTime);

		if(this.output != null) {
			buf.writeBoolean(true);
			buf.writeInt(this.output.material.id);
			buf.writeInt(this.output.amount);
		} else {
			buf.writeBoolean(false);
		}
	}

	@Override public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		tanks[0].deserialize(buf);
		tanks[1].deserialize(buf);
		tanks[2].deserialize(buf);
		isVenting = buf.readBoolean();
		isProgressing = buf.readBoolean();
		progress = buf.readFloat();
		burnTime = buf.readInt();
		maxBurnTime = buf.readInt();

		if(buf.readBoolean()) {
			this.output = new MaterialStack(Mats.matById.get(buf.readInt()), buf.readInt());
		} else {
			this.output = null;
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.tanks[0].readFromNBT(nbt, "t0");
		this.tanks[1].readFromNBT(nbt, "t1");
		this.tanks[2].readFromNBT(nbt, "t2");
		this.progress = nbt.getFloat("prog");
        firebox.read(nbt.getCompoundTag("rotaryFirebox"));
        processHeatQuanta = Math.max(0, nbt.getLong("rotaryProcessHeat"));
        drive.read(nbt); recipeAccountingKey = nbt.getString("recipeAccountingKey");
		this.burnTime = nbt.getInteger("burn");
        if(!nbt.hasKey("rotaryProcessHeat") && burnTime > 0) { processHeatQuanta = burnTime * 50000L; burnTime = 0; }
		this.maxBurnTime = nbt.getInteger("maxBurn");
		if(nbt.hasKey("outputMaterial") && nbt.getInteger("outputAmount") > 0 && Mats.matById.get(nbt.getInteger("outputMaterial")) != null)
			this.output = new MaterialStack(Mats.matById.get(nbt.getInteger("outputMaterial")), nbt.getInteger("outputAmount"));
		else this.output = null;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		this.tanks[0].writeToNBT(nbt, "t0");
		this.tanks[1].writeToNBT(nbt, "t1");
		this.tanks[2].writeToNBT(nbt, "t2");
		nbt.setFloat("prog", progress);
        net.minecraft.nbt.NBTTagCompound fuelSave = new net.minecraft.nbt.NBTTagCompound(); firebox.write(fuelSave); nbt.setTag("rotaryFirebox", fuelSave); nbt.setLong("rotaryProcessHeat", processHeatQuanta);
        drive.write(nbt); nbt.setString("recipeAccountingKey", recipeAccountingKey);
		nbt.setInteger("burn", burnTime);
		nbt.setInteger("maxBurn", maxBurnTime);
		if(output != null) {
			nbt.setInteger("outputMaterial", output.material.id);
			nbt.setInteger("outputAmount", output.amount);
		}
	}

	public DirPos[] getSteamPos() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
		ForgeDirection rot = dir.getRotation(ForgeDirection.DOWN);

		return new DirPos[] {
			new DirPos(xCoord - dir.offsetX * 2 - rot.offsetX * 2, yCoord, zCoord - dir.offsetZ * 2 - rot.offsetZ * 2, dir.getOpposite()),
			new DirPos(xCoord - dir.offsetX * 2 - rot.offsetX, yCoord, zCoord - dir.offsetZ * 2 - rot.offsetZ, dir.getOpposite())
		};
	}

	public DirPos[] getFluidPos() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
		ForgeDirection rot = dir.getRotation(ForgeDirection.DOWN);

		return new DirPos[] {
			new DirPos(xCoord + dir.offsetX + rot.offsetX * 3, yCoord, zCoord + dir.offsetZ + rot.offsetZ * 3, rot),
			new DirPos(xCoord - dir.offsetX + rot.offsetX * 3, yCoord, zCoord - dir.offsetZ + rot.offsetZ * 3, rot)
		};
	}

	public boolean canProcess(RotaryFurnaceRecipe recipe) {
		if(recipe == null || RotaryFurnaceRecipes.getRecipe(slots[0], slots[1], slots[2]) != recipe) return false;

		if(!firebox.hasCredit() && processHeatQuanta <= 0) return false;

		if(recipe.fluid != null) {
			if(this.tanks[0].getTankType() != recipe.fluid.type) return false;
			if(this.tanks[0].getFill() < recipe.fluid.fill) return false;
		}

		if(!canDrive(recipe.steam)) return false;

		if(this.output != null) {
			if(this.output.material != recipe.output.material) return false;
			if(this.output.amount + recipe.output.amount > this.maxOutput) return false;
		}

		return true;
	}

	public void consumeItems(RotaryFurnaceRecipe recipe) {

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
			this.tanks[0].setFill(tanks[0].getFill() - recipe.fluid.fill);
		}
	}

	@Override
	public void pollute(PollutionType type, float amount) {
		FluidTank tank = type == PollutionType.SOOT ? smoke : type == PollutionType.HEAVYMETAL ? smoke_leaded : smoke_poison;

		int fluidAmount = (int) Math.ceil(amount * 100);
		tank.setFill(tank.getFill() + fluidAmount);

		if(tank.getFill() > tank.getMaxFill()) {
			int overflow = tank.getFill() - tank.getMaxFill();
			tank.setFill(tank.getMaxFill());
			PollutionHandler.incrementPollution(worldObj, xCoord, yCoord, zCoord, type, overflow / 100F);
			this.isVenting = true;
		}
	}

	@Override public int[] getAccessibleSlotsFromSide(int side) { return new int[0]; }
	@Override public boolean isItemValidForSlot(int slot, ItemStack stack) { return slot < 3 || slot == 4; }
	@Override public boolean canExtractItem(int slot, ItemStack  stack, int side) { return false; }

	@Override public boolean isItemValidForSlot(int x, int y, int z, int slot, ItemStack stack) { return slot < 3 || slot == 4; }
	@Override public boolean canExtractItem(int x, int y, int z, int slot, ItemStack stack, int side) { return false; }

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
				xCoord - 2,
				yCoord,
				zCoord - 2,
				xCoord + 3,
				yCoord + 5,
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
	public int[] getAccessibleSlotsFromSide(int x, int y, int z, int side) {
		BlockPos pos = new BlockPos(x, y, z);
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
		BlockPos core = new BlockPos(xCoord, yCoord, zCoord);

		//Red
		if(side == dir.getOpposite().ordinal() && pos.equals(core.clone().offset(dir, -1).offset(rot, -2))) return new int[] {0};
		//Yellow
		if(side == dir.getOpposite().ordinal() && pos.equals(core.clone().offset(dir, -1).offset(rot, -1))) return new int[] {1};
		//Green
		if(side == dir.getOpposite().ordinal() && pos.equals(core.clone().offset(dir, -1))) return new int[] {2};
		//Fuel
		if(side == dir.ordinal() && pos.equals(core.clone().offset(dir, 1).offset(rot, -1))) return new int[] {4};

		return new int[] { };
	}

    @Override public long getMachineDemandWatts() { return 0; }
    @Override public long getMachineCurrentDrawWatts() { return 0; }
    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        lines.add("Steam drives drum/blower; 500 kW firebox pays 1.8 MJ per ingot-equivalent process heat at 85% efficiency");
        lines.add("Drive: " + (runtimeRecipe == null ? 20 : runtimeRecipe.steam * 20) + " kW shaft; exhaust must be drained");
    }
	@Override public FluidTank[] getAllTanks() { return new FluidTank[] {tanks[0], tanks[1], tanks[2], smoke}; }
	private boolean legacyFeedDrainable() { return tanks[0].getFill() > 0 && (tanks[0].getFill() > tanks[0].getMaxFill() || runtimeRecipe == null || runtimeRecipe.fluid == null || runtimeRecipe.fluid.type != tanks[0].getTankType()); }
    private boolean legacySteamDrainable() { return tanks[1].getFill() > 0 && (tanks[1].getFill() > tanks[1].getMaxFill() || com.hbm.realsim.ThermalMachine.state(tanks[1]) != com.hbm.realsim.SteamLedger.State.ORDINARY); }
    @Override public FluidTank[] getSendingTanks() {
        java.util.List<FluidTank> result = new java.util.ArrayList<>(); result.add(tanks[2]); result.add(smoke);
        if(legacyFeedDrainable()) result.add(tanks[0]); if(legacySteamDrainable()) result.add(tanks[1]);
        return result.toArray(new FluidTank[result.size()]);
    }
	@Override
	public boolean isFluidDemandObservable() { return true; }

	@Override public FluidTank[] getReceivingTanks() {
        if(legacyFeedDrainable()) return legacySteamDrainable() ? new FluidTank[0] : new FluidTank[] {tanks[1]};
        return legacySteamDrainable() ? new FluidTank[] {tanks[0]} : new FluidTank[] {tanks[0], tanks[1]};
    }

	@Override public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) { return new ContainerMachineRotaryFurnace(player.inventory, this); }
	@Override public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) { return new GUIMachineRotaryFurnace(player.inventory, this); }
}
