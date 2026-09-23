package api.hbm.energymk2;

import com.hbm.util.fauxpointtwelve.BlockPos;

import api.hbm.energymk2.Nodespace.PowerNode;
import net.minecraft.tileentity.TileEntity;

public interface IEnergyConductorMK2 extends IEnergyConnectorMK2 {
	
	public default PowerNode createNode() {
		TileEntity tile = (TileEntity) this;
		return (PowerNode) new PowerNode(new BlockPos(tile.xCoord, tile.yCoord, tile.zCoord))
				.setStandardConnections(tile.xCoord, tile.yCoord, tile.zCoord);
	}
}
