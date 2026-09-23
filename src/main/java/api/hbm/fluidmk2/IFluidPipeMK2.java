package api.hbm.fluidmk2;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.util.fauxpointtwelve.BlockPos;

import net.minecraft.tileentity.TileEntity;

/**
 * IFluidConductorMK2 with added node creation method
 * @author hbm
 */
public interface IFluidPipeMK2 extends IFluidConnectorMK2 {
	
	public default FluidNode createNode(FluidType type) {
		TileEntity tile = (TileEntity) this;
		return (FluidNode) new FluidNode(type.getNetworkProvider(), new BlockPos(tile.xCoord, tile.yCoord, tile.zCoord))
				.setStandardConnections(tile.xCoord, tile.yCoord, tile.zCoord);
	}
}
