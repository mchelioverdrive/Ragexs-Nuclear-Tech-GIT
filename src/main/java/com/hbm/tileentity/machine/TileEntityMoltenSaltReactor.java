package com.hbm.tileentity.machine;

import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.CompatEnergyControl;

import api.hbm.fluid.IFluidStandardTransceiver;
import api.hbm.tile.IInfoProviderEC;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMoltenSaltReactor extends TileEntityMachineBase implements IFluidStandardTransceiver, IFluidCopiable, IInfoProviderEC {

	public static final int SALT_CAPACITY = 16_000;
	public static final int HOT_SALT_CAPACITY = 16_000;
	public static final int SALT_PER_TICK = 4;

	public FluidTank[] tanks;
	public int output;

	public TileEntityMoltenSaltReactor() {
		super(0);
		tanks = new FluidTank[2];
		tanks[0] = new FluidTank(Fluids.THORIUM_SALT, SALT_CAPACITY);
		tanks[1] = new FluidTank(Fluids.THORIUM_SALT_HOT, HOT_SALT_CAPACITY);
	}

	@Override
	public String getName() {
		return "container.moltenSaltReactor";
	}

	@Override
	public void updateEntity() {
		if(!worldObj.isRemote) {
			this.output = 0;
			this.updateConnections();

			int operations = Math.min(SALT_PER_TICK, tanks[0].getFill());
			operations = Math.min(operations, tanks[1].getMaxFill() - tanks[1].getFill());

			if(operations > 0) {
				tanks[0].setFill(tanks[0].getFill() - operations);
				tanks[1].setFill(tanks[1].getFill() + operations);
				this.output = operations;
			}

			this.subscribeToAllAround(tanks[0].getTankType(), this);
			this.sendFluidToAll(tanks[1], this);

			NBTTagCompound data = new NBTTagCompound();
			data.setInteger("output", output);
			tanks[0].writeToNBT(data, "salt");
			tanks[1].writeToNBT(data, "hotSalt");
			this.networkPack(data, 50);
		}
	}

	protected void updateConnections() {
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
			this.trySubscribe(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
	}

	@Override
	public void networkUnpack(NBTTagCompound data) {
		super.networkUnpack(data);
		this.output = data.getInteger("output");
		tanks[0].readFromNBT(data, "salt");
		tanks[1].readFromNBT(data, "hotSalt");
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.output = nbt.getInteger("output");
		tanks[0].readFromNBT(nbt, "salt");
		tanks[1].readFromNBT(nbt, "hotSalt");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setInteger("output", output);
		tanks[0].writeToNBT(nbt, "salt");
		tanks[1].writeToNBT(nbt, "hotSalt");
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { tanks[1] };
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { tanks[0] };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank getTankToPaste() {
		return tanks[0];
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, this.output > 0);
		data.setDouble(CompatEnergyControl.D_OUTPUT_MB, this.output);
	}
}
