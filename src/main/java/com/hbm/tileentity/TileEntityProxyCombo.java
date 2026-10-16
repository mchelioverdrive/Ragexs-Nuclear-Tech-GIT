package com.hbm.tileentity;

import api.hbm.block.ICrucibleAcceptor;
import com.hbm.handler.CompatHandler;
import com.hbm.handler.CompatHandler.OCComponent;
import com.hbm.inventory.fluid.FluidType;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluid.IFluidConnector;
import api.hbm.tile.IHeatSource;
import com.hbm.inventory.material.Mats;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

@Optional.InterfaceList({
		@Optional.Interface(iface = "com.hbm.handler.CompatHandler.OCComponent", modid = "opencomputers"),
		@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "opencomputers")
})
public class TileEntityProxyCombo extends TileEntityProxyBase implements IEnergyReceiverMK2, ISidedInventory, IFluidConnector, api.hbm.tile.IProcessHeatSource, ICrucibleAcceptor, SimpleComponent, OCComponent {

	TileEntity tile;
	boolean inventory;
	boolean power;
	boolean fluid;
	boolean heat;
	public boolean moltenMetal;

	// due to some issues with OC deciding that it's gonna call the component name function before the worldObj is loaded
	// the component name must be cached to prevent it from shitting itself
	String componentName = CompatHandler.nullComponent;

	public TileEntityProxyCombo() { }

	public TileEntityProxyCombo(boolean inventory, boolean power, boolean fluid) {
		this.inventory = inventory;
		this.power = power;
		this.fluid = fluid;
	}

	public TileEntityProxyCombo inventory() {
		this.inventory = true;
		return this;
	}

	public TileEntityProxyCombo power() {
		this.power = true;
		return this;
	}
	public TileEntityProxyCombo moltenMetal() {
		this.moltenMetal = true;
		return this;
	}
	public TileEntityProxyCombo fluid() {
		this.fluid = true;
		return this;
	}

	public TileEntityProxyCombo heatSource() {
		this.heat = true;
		return this;
	}

	//fewer messy recursive operations
	public TileEntity getTile() {

		if(tile == null || tile.isInvalid()) {
			tile = this.getTE();
		}

		return tile;
	}

    private boolean powerEnabled() { return power || worldObj != null && getTile() instanceof com.hbm.tileentity.machine.TileEntityHeatBoilerBase; }
    private boolean inventoryEnabled() { return inventory || worldObj != null && getTile() instanceof com.hbm.tileentity.machine.TileEntityHeatBoilerIndustrial; }

	@Override
	public void setStoredEnergyQuanta(long i) {

		if(!powerEnabled())
			return;

		if(getTile() instanceof IEnergyReceiverMK2) {
			((IEnergyReceiverMK2)getTile()).setStoredEnergyQuanta(i);
		}
	}

	@Override
	public long getStoredEnergyQuanta() {

		if(!powerEnabled())
			return 0;

		if(getTile() instanceof IEnergyReceiverMK2) {
			return ((IEnergyReceiverMK2)getTile()).getStoredEnergyQuanta();
		}

		return 0;
	}

	@Override
	public long getEnergyCapacityQuanta() {

		if(!powerEnabled())
			return 0;

		if(getTile() instanceof IEnergyReceiverMK2) {
			return ((IEnergyReceiverMK2)getTile()).getEnergyCapacityQuanta();
		}

		return 0;
	}

	@Override
	public long receiveEnergyQuanta(long power) {

		if(!powerEnabled())
			return power;

		if(getTile() instanceof IEnergyReceiverMK2) {
			long remainder = ((IEnergyReceiverMK2)getTile()).receiveEnergyQuanta(power);
			if(remainder != power) this.markPowerNetDirty();
			return remainder;
		}

		return power;
	}

	@Override
	public boolean canConnect(ForgeDirection dir) {

		if(!powerEnabled())
			return false;

		if(getTile() instanceof IEnergyReceiverMK2) {
			return ((IEnergyReceiverMK2)getTile()).canConnect(dir);
		}

		return true;
	}

	@Override
	public int getSizeInventory() {

		if(!inventoryEnabled())
			return 0;

		if(getTile() instanceof ISidedInventory) {
			return ((ISidedInventory)getTile()).getSizeInventory();
		}

		return 0;
	}

	@Override
	public ItemStack getStackInSlot(int slot) {

		if(!inventoryEnabled())
			return null;

		if(getTile() instanceof ISidedInventory) {
			return ((ISidedInventory)getTile()).getStackInSlot(slot);
		}

		return null;
	}

	@Override
	public ItemStack decrStackSize(int i, int j) {

		if(getTile() instanceof ISidedInventory) {
			return ((ISidedInventory)getTile()).decrStackSize(i, j);
		}

		return null;
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int slot) {

		if(!inventoryEnabled())
			return null;

		if(getTile() instanceof ISidedInventory) {
			return ((ISidedInventory)getTile()).getStackInSlotOnClosing(slot);
		}

		return null;
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {

		if(!inventoryEnabled())
			return;

		if(getTile() instanceof ISidedInventory) {
			((ISidedInventory)getTile()).setInventorySlotContents(slot, stack);
		}
	}

	@Override
	public String getInventoryName() {

		if(!inventoryEnabled())
			return null;

		if(getTile() instanceof ISidedInventory) {
			return ((ISidedInventory)getTile()).getInventoryName();
		}

		return null;
	}

	@Override
	public boolean hasCustomInventoryName() {

		if(!inventoryEnabled())
			return false;

		if(getTile() instanceof ISidedInventory) {
			return ((ISidedInventory)getTile()).hasCustomInventoryName();
		}

		return false;
	}

	@Override
	public int getInventoryStackLimit() {

		if(!inventoryEnabled())
			return 0;

		if(getTile() instanceof ISidedInventory) {
			return ((ISidedInventory)getTile()).getInventoryStackLimit();
		}

		return 0;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer player) {

		if(!inventoryEnabled())
			return false;

		if(getTile() instanceof ISidedInventory) {
			return ((ISidedInventory)getTile()).isUseableByPlayer(player);
		}

		return false;
	}

	@Override
	public void openInventory() {

		if(!inventoryEnabled())
			return;

		if(getTile() instanceof ISidedInventory) {
			((ISidedInventory)getTile()).openInventory();
		}
	}

	@Override
	public void closeInventory() {

		if(!inventoryEnabled())
			return;

		if(getTile() instanceof ISidedInventory) {
			((ISidedInventory)getTile()).closeInventory();
		}
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {

		if(!inventoryEnabled())
			return false;

		if(getTile() instanceof ISidedInventory) {

			if(getTile() instanceof IConditionalInvAccess) return ((IConditionalInvAccess) getTile()).isItemValidForSlot(xCoord, yCoord, zCoord, slot, stack);

			return ((ISidedInventory)getTile()).isItemValidForSlot(slot, stack);
		}

		return false;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {

		if(!inventoryEnabled())
			return new int[0];

		if(getTile() instanceof ISidedInventory) {

			if(getTile() instanceof IConditionalInvAccess) return ((IConditionalInvAccess) getTile()).getAccessibleSlotsFromSide(xCoord, yCoord, zCoord, side);

			return ((ISidedInventory)getTile()).getAccessibleSlotsFromSide(side);
		}

		return new int[0];
	}

	@Override
	public boolean canInsertItem(int i, ItemStack stack, int j) {

		if(!inventoryEnabled())
			return false;

		if(getTile() instanceof ISidedInventory) {

			if(getTile() instanceof IConditionalInvAccess) return ((IConditionalInvAccess) getTile()).canInsertItem(xCoord, yCoord, zCoord, i, stack, j);

			return ((ISidedInventory)getTile()).canInsertItem(i, stack, j);
		}

		return false;
	}

	@Override
	public boolean canExtractItem(int i, ItemStack stack, int j) {

		if(!inventoryEnabled())
			return false;

		if(getTile() instanceof ISidedInventory) {

			if(getTile() instanceof IConditionalInvAccess) return ((IConditionalInvAccess) getTile()).canExtractItem(xCoord, yCoord, zCoord, i, stack, j);

			return ((ISidedInventory)getTile()).canExtractItem(i, stack, j);
		}

		return false;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.inventory = nbt.getBoolean("inv");
		this.power = nbt.getBoolean("power");
		this.fluid = nbt.getBoolean("fluid");
		this.moltenMetal = nbt.getBoolean("metal");
		this.heat = nbt.getBoolean("heat");
		if(Loader.isModLoaded("OpenComputers"))
			this.componentName = nbt.getString("ocname");

	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		nbt.setBoolean("inv", inventory);
		nbt.setBoolean("power", power);
		nbt.setBoolean("fluid", fluid);
		nbt.setBoolean("metal", moltenMetal);
		nbt.setBoolean("heat", heat);
		if(Loader.isModLoaded("OpenComputers"))
			nbt.setString("ocname", componentName);
	}

	@Override
	public long transferFluid(FluidType type, int pressure, long fluid) {

		if(!this.fluid)
			return fluid;

		if(getTile() instanceof IFluidConnector) {
			return ((IFluidConnector)getTile()).transferFluid(type, pressure, fluid);
		}
		return fluid;
	}

	@Override
	public long getDemand(FluidType type, int pressure) {

		if(!this.fluid)
			return 0;

		if(getTile() instanceof IFluidConnector) {
			return ((IFluidConnector)getTile()).getDemand(type, pressure);
		}
		return 0;
	}

	@Override
	public boolean canConnect(FluidType type, ForgeDirection dir) {

		if(!this.fluid)
			return false;

		if(getTile() instanceof IFluidConnector) {
			return ((IFluidConnector)getTile()).canConnect(type, dir);
		}
		return true;
	}

	@Override
	public int getHeatStored() {

		if(!this.heat)
			return 0;

		if(getTile() instanceof IHeatSource) {
			return ((IHeatSource)getTile()).getHeatStored();
		}

		return 0;
	}

    @Override public long getAvailableProcessHeatQuanta(long tick) {
        TileEntity source = getTile();
        return heat && source instanceof api.hbm.tile.IProcessHeatSource ? ((api.hbm.tile.IProcessHeatSource) source).getAvailableProcessHeatQuanta(tick) : 0;
    }
    @Override public boolean canSupplyProcessHeat(ForgeDirection side) {
        TileEntity source = getTile();
        return heat && source instanceof api.hbm.tile.IProcessHeatSource
                && ((api.hbm.tile.IProcessHeatSource) source).canSupplyProcessHeat(side);
    }
    @Override public long drawProcessHeatQuanta(long tick, long request) {
        TileEntity source = getTile();
        return heat && source instanceof api.hbm.tile.IProcessHeatSource ? ((api.hbm.tile.IProcessHeatSource) source).drawProcessHeatQuanta(tick, request) : 0;
    }
    @Override public int getProcessTemperatureC() {
        TileEntity source = getTile();
        return heat && source instanceof api.hbm.tile.IProcessHeatSource ? ((api.hbm.tile.IProcessHeatSource) source).getProcessTemperatureC() : Integer.MIN_VALUE;
    }

	@Override
	public void useUpHeat(int heat) {

		if(!this.heat)
			return;

		if(getTile() instanceof IHeatSource) {
			((IHeatSource)getTile()).useUpHeat(heat);
		}
	}

	@Override
	public boolean canAcceptPartialPour(World world, int x, int y, int z, double dX, double dY, double dZ, ForgeDirection side, Mats.MaterialStack stack) {
		if(this.moltenMetal && getTile() instanceof ICrucibleAcceptor){
			return ((ICrucibleAcceptor)getTile()).canAcceptPartialPour(world, x, y, z, dX, dY, dZ, side, stack);
		}
		return false;
	}

	@Override
	public Mats.MaterialStack pour(World world, int x, int y, int z, double dX, double dY, double dZ, ForgeDirection side, Mats.MaterialStack stack) {
		if(this.moltenMetal && getTile() instanceof ICrucibleAcceptor){
			return ((ICrucibleAcceptor)getTile()).pour(world, x, y, z, dX, dY, dZ, side, stack);
		}
		return null;
	}

	@Override
	public boolean canAcceptPartialFlow(World world, int x, int y, int z, ForgeDirection side, Mats.MaterialStack stack) {
		if(this.moltenMetal && getTile() instanceof ICrucibleAcceptor){
			return ((ICrucibleAcceptor)getTile()).canAcceptPartialFlow(world, x, y, z, side, stack);
		}
		return false;
	}

	@Override
	public Mats.MaterialStack flow(World world, int x, int y, int z, ForgeDirection side, Mats.MaterialStack stack) {
		if(this.moltenMetal && getTile() instanceof ICrucibleAcceptor){
			return ((ICrucibleAcceptor)getTile()).flow(world, x, y, z, side, stack);
		}
		return null;
	}

	@Override // please work
	@Optional.Method(modid = "OpenComputers")
	public String getComponentName() {
		if(this.worldObj == null) // OC is going too fast, grab from NBT!
			return componentName;
		if(this.getTile() instanceof OCComponent) {
			if (componentName == null || componentName.equals(OCComponent.super.getComponentName())) {
				componentName = ((OCComponent) this.getTile()).getComponentName();
			}
			return componentName;
		}
		return OCComponent.super.getComponentName();
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public boolean canConnectNode(ForgeDirection side) {
		if(this.getTile() instanceof OCComponent)
			return (this.getBlockMetadata() >= 6 && this.getBlockMetadata() <= 11)
					&& (power || fluid) &&
					((OCComponent) this.getTile()).canConnectNode(side);
		return OCComponent.super.canConnectNode(null);
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public String[] methods() {
		if(this.getTile() instanceof OCComponent)
			return ((OCComponent) this.getTile()).methods();
		return OCComponent.super.methods();
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public Object[] invoke(String method, Context context, Arguments args) throws Exception {
		if(this.getTile() instanceof OCComponent)
			return ((OCComponent) this.getTile()).invoke(method, context, args);
		return OCComponent.super.invoke(null, null, null);
	}
}
