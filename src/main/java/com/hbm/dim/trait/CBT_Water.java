package com.hbm.dim.trait;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

public class CBT_Water extends CelestialBodyTrait {

    /**
     * Defines a water table (or any sort of fluid cycle, for exotic planets)
     */
    
    // Doesn't necessarily have to be water
    public FluidType fluid;

    public CBT_Water() {
        fluid = Fluids.WATER;
    }

    public CBT_Water(FluidType fluid) {
        this.fluid = fluid;
    }

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		nbt.setInteger("fluid", fluid.getID());
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		// Missing legacy data means an Earth-like water table; an explicit NONE
		// means no harvestable surface liquid and must not silently become water.
		if(!nbt.hasKey("fluid")) {
			fluid = Fluids.WATER;
			return;
		}

		fluid = Fluids.fromID(nbt.getInteger("fluid"));
	}

	// These methods are for client syncing, the precision loss is intentional
	@Override
	public void writeToBytes(ByteBuf buf) {
		buf.writeInt(fluid.getID());
	}

	@Override
	public void readFromBytes(ByteBuf buf) {
		fluid = Fluids.fromID(buf.readInt());
	}

}
