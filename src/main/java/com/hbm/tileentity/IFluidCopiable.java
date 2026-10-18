package com.hbm.tileentity;

import api.hbm.fluid.IFluidStandardTransceiver;
import api.hbm.fluid.IFluidUser;
import com.hbm.interfaces.ICopiable;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.util.BobMathUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.ArrayList;

public interface IFluidCopiable extends ICopiable {
	
	/**
	 * @return First type for the normal paste, second type for the alt paste,
	 *         none if there is no alt paste support
	 */
	default int[] getFluidIDToCopy() {
		IFluidUser tile = (IFluidUser) this;
		ArrayList<Integer> types = new ArrayList<>();

		for(FluidTank tank : tile.getAllTanks()) {
			if(!tank.getTankType().hasNoID())
				types.add(tank.getTankType().getID());
		}

		return BobMathUtil.intCollectionToArray(types);
	}

	default FluidTank getTankToPaste() {
		TileEntity te = (TileEntity) this;
		if(te instanceof IFluidStandardTransceiver) {
			IFluidStandardTransceiver tile = (IFluidStandardTransceiver) this;
			FluidTank[] tanks = tile.getReceivingTanks();
			return tanks != null && tanks.length > 0 ? tanks[0] : null;
		}
		return null;
	}

	@Override
	default NBTTagCompound getSettings(World world, int x, int y, int z) {
		NBTTagCompound tag = new NBTTagCompound();
		if(getFluidIDToCopy().length > 0) tag.setIntArray("fluidID", getFluidIDToCopy());
		return tag;
	}

	@Override
	default void pasteSettings(NBTTagCompound nbt, int index, World world, EntityPlayer player, int x, int y, int z) {
		FluidTank tank = getTankToPaste();
		if(tank != null) {
			int[] ids = nbt.getIntArray("fluidID");
			if(index >= 0 && index < ids.length) {
				int id = ids[index];
				tank.configureForRecipe(Fluids.fromID(id), tank.getPressure());
			}
		}
	}

	@Override
	default String[] infoForDisplay(World world, int x, int y, int z) {
		int[] ids = getFluidIDToCopy();
		String[] names = new String[ids.length];
		for(int i = 0; i < ids.length; i++) {
			names[i] = Fluids.fromID(ids[i]).getUnlocalizedName();
		}
		return names;
	}
}
