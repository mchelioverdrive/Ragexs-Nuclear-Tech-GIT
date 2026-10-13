package api.hbm.fluid;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;

/**
 * Uses default implementation to make the underlying interfaces easier to use for the most common fluid users.
 * Only handles a single input tank of the same type.
 * Uses standard FluidTanks which use int32.
 * Don't use this as part of the API!
 * @author hbm
 *
 */
public interface IFluidStandardReceiver extends IFluidUser {
	
	@Override
	public default long transferFluid(FluidType type, int pressure, long amount) {

		for(FluidTank tank : getReceivingTanks()) {
			if(tank.getTankType() == type && tank.getPressure() == pressure) {
				long accepted = Math.min(Math.max(0L, amount), Math.max(0L, (long) tank.getMaxFill() - tank.getFill()));
				if(accepted > 0) tank.setFill(tank.getFill() + (int) accepted);
				return amount - accepted;
			}
		}
		
		return amount;
	}

	public FluidTank[] getReceivingTanks();

	@Override
	public default long getDemand(FluidType type, int pressure) {
		
		for(FluidTank tank : getReceivingTanks()) {
			if(tank.getTankType() == type && tank.getPressure() == pressure) {
				return Math.max(0L, (long) tank.getMaxFill() - tank.getFill());
			}
		}
		
		return 0;
	}
}
