package com.hbm.tileentity.network;

import com.hbm.blocks.network.IBlockFluidDuct;
import com.hbm.extprop.HbmPlayerProps;
import com.hbm.handler.HbmKeybinds;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.tileentity.IFluidCopiable;

import api.hbm.fluid.IFluidConductor;
import api.hbm.fluid.IPipeNet;
import api.hbm.fluid.PipeNet;
import api.hbm.fluidmk2.FluidNetMK2;
import api.hbm.fluidmk2.FluidNode;
import api.hbm.fluidmk2.IFluidPipeMK2;
import api.hbm.energymk2.PowerNetDiagnostics;
import com.hbm.uninos.UniNodespace;
import com.hbm.uninos.IDeferredConductor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

/** Save-compatible, non-ticking state container for an event-driven UNINOS fluid node. */
public class TileEntityPipeBaseNT extends TileEntity implements IFluidConductor, IFluidPipeMK2, IFluidCopiable, IDeferredConductor {

	protected FluidNode node;
	protected FluidType type = Fluids.NONE;
	public boolean isLoaded;

	@Override
	public boolean canUpdate() { return false; }

	@Override
	public void validate() {
		super.validate();
		if(this.worldObj != null && !this.worldObj.isRemote && !this.isLoaded) PowerNetDiagnostics.recordChunkAttachment();
		this.isLoaded = true;
		this.queueNodeReconciliation();
	}

	@Override
	public void updateEntity() {
		this.queueNodeReconciliation();
	}

	@Override
	public void reconcileConductorNode() {
		if(this.worldObj == null || this.worldObj.isRemote || this.isInvalid()) return;
		if(!this.shouldCreateNode()) {
			FluidNode registered = (FluidNode) UniNodespace.getNode(this.worldObj, this.xCoord, this.yCoord, this.zCoord, this.type.getNetworkProvider());
			if(registered != null) UniNodespace.destroyNode(this.worldObj, registered);
			this.node = null;
			return;
		}
		if(this.node != null && !this.node.expired) return;
		this.node = (FluidNode) UniNodespace.getNode(this.worldObj, this.xCoord, this.yCoord, this.zCoord, this.type.getNetworkProvider());
		if(this.node == null || this.node.expired) {
			this.node = this.createNode(this.type);
			UniNodespace.createNode(this.worldObj, this.node);
		}
	}

	protected final void queueNodeReconciliation() {
		UniNodespace.queueConductor(this.worldObj, this.xCoord, this.yCoord, this.zCoord);
	}

	protected boolean shouldCreateNode() { return true; }

	public FluidType getType() { return this.type; }

	public void setType(FluidType nextType) {
		if(nextType == null || nextType == this.type) return;
		if(this.worldObj != null && !this.worldObj.isRemote && this.node != null) UniNodespace.destroyNode(this.worldObj, this.node);
		this.node = null;
		this.type = nextType;
		this.markDirty();
		if(this.worldObj instanceof WorldServer) ((WorldServer) this.worldObj).getPlayerManager().markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
		this.queueNodeReconciliation();
		api.hbm.fluidmk2.FluidNetEndpointRegistry.markTopologyDirty(this.worldObj);
	}

	public FluidNetMK2 getFluidNet() {
		return this.node != null && this.node.hasValidNet() ? this.node.net : null;
	}

	@Override
	public FluidNetMK2 getFluidNet(FluidType fluid) { return fluid == this.type ? this.getFluidNet() : null; }

	@Override
	public long transferFluid(FluidType fluid, int pressure, long amount) {
		FluidNetMK2 network = this.getFluidNet();
		return network != null ? network.transferFluidExternal(fluid, pressure, amount, null) : amount;
	}

	@Override
	public long getDemand(FluidType type, int pressure) { return 0; }

	@Override
	public IPipeNet getPipeNet(FluidType fluid) {
		return fluid == this.type ? PipeNet.forNetwork(this.getFluidNet()) : null;
	}

	@Override
	public void setPipeNet(FluidType type, IPipeNet network) {
		// Compatibility no-op: FluidNode/FluidNetMK2 own topology.
	}

	@Override
	public boolean canConnect(FluidType fluid, ForgeDirection direction) {
		return direction != ForgeDirection.UNKNOWN && fluid == this.type;
	}

	@Override
	public void invalidate() {
		this.recordUnload();
		if(this.worldObj != null && !this.worldObj.isRemote && this.node != null) UniNodespace.destroyNode(this.worldObj, this.node);
		this.node = null;
		super.invalidate();
	}

	@Override
	public void onChunkUnload() {
		this.recordUnload();
		super.onChunkUnload();
		// Logical conductor topology is compact, world-owned, and dormant while unloaded.
		this.node = null;
	}

	private void recordUnload() {
		if(this.worldObj != null && !this.worldObj.isRemote && this.isLoaded) PowerNetDiagnostics.recordChunkDetachment();
		this.isLoaded = false;
	}

	@Override
	public boolean isLoaded() { return this.isLoaded; }

	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound nbt = new NBTTagCompound();
		this.writeToNBT(nbt);
		return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, nbt);
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
		this.readFromNBT(packet.func_148857_g());
		if(this.worldObj != null) this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.type = Fluids.fromID(nbt.getInteger("type"));
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setInteger("type", this.type.getID());
	}

	@Override
	public int[] getFluidIDToCopy() { return new int[] {this.type.getID()}; }

	@Override
	public FluidTank getTankToPaste() { return null; }

	@Override
	public void pasteSettings(NBTTagCompound nbt, int index, World world, EntityPlayer player, int x, int y, int z) {
		int[] ids = nbt.getIntArray("fluidID");
		if(ids.length == 0) return;
		int id = index < ids.length ? ids[index] : 0;
		FluidType fluid = Fluids.fromID(id);
		if(HbmPlayerProps.getData(player).getKeyPressed(HbmKeybinds.EnumKeybind.TOOL_CTRL)) {
			((IBlockFluidDuct) world.getBlock(x, y, z)).changeTypeRecursively(world, x, y, z, getType(), fluid, 64);
		} else this.setType(fluid);
	}
}
