package api.hbm.fluid;

import com.hbm.inventory.fluid.FluidType;

import api.hbm.fluidmk2.FluidNetMK2;

public interface IFluidConductor extends IFluidConnector {

	public FluidNetMK2 getFluidNet(FluidType type);

	public default IPipeNet getPipeNet(FluidType type) { return PipeNet.forNetwork(this.getFluidNet(type)); }
	public default void setPipeNet(FluidType type, IPipeNet network) { }
	
	@Override
	public default long transferFluid(FluidType type, int pressure, long amount) {
		
		FluidNetMK2 network = this.getFluidNet(type);
		if(network == null)
			return amount;

		return network.transferFluidExternal(type, pressure, amount, null);
	}
}
