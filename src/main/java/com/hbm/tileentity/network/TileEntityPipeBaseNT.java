package com.hbm.tileentity.network;

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

/** Save-compatible, non-ticking pipe with up to four independent species graphs in UNINOS. */
public class TileEntityPipeBaseNT extends TileEntity implements IFluidConductor, IFluidPipeMK2, IFluidCopiable, IDeferredConductor {

	protected FluidNode node;
	protected FluidType type = Fluids.NONE;
    private FluidType[] channels = new FluidType[] {Fluids.NONE};
    public static final int MAX_CHANNELS = 4;
    private final FluidNode[] channelNodes = new FluidNode[MAX_CHANNELS];
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

    @Override public void reconcileConductorNode() {
        if(worldObj == null || worldObj.isRemote || isInvalid()) return;
        if(!shouldCreateNode()) { detachNodes(); return; }
        for(int i = 0; i < channels.length; i++) {
            if(channelNodes[i] != null && !channelNodes[i].expired) continue;
            channelNodes[i] = (FluidNode) UniNodespace.getNode(worldObj, xCoord, yCoord, zCoord, channels[i].getNetworkProvider());
            if(channelNodes[i] == null || channelNodes[i].expired) {
                channelNodes[i] = createNode(channels[i]); UniNodespace.createNode(worldObj, channelNodes[i]);
            }
        }
        node = channelNodes[0]; // Historical no-argument gauge/API view is the first channel.
    }
    /** Remove every species graph, including dormant nodes whose loaded reference was cleared. */
    protected final void detachNodes() {
        if(worldObj != null && !worldObj.isRemote) for(int i = 0; i < channels.length; i++) {
            FluidNode registered = (FluidNode) UniNodespace.getNode(worldObj, xCoord, yCoord, zCoord, channels[i].getNetworkProvider());
            if(registered != null) UniNodespace.destroyNode(worldObj, registered);
        }
        java.util.Arrays.fill(channelNodes, null); node = null;
    }

	protected final void queueNodeReconciliation() {
		UniNodespace.queueConductor(this.worldObj, this.xCoord, this.yCoord, this.zCoord);
	}

	protected boolean shouldCreateNode() { return true; }

	public FluidType getType() { return this.type; }

    public FluidType[] getTypes() { return channels.clone(); }
    public boolean carries(FluidType fluid) {
        for(FluidType channel : channels) if(channel == fluid) return true;
        return false;
    }
    /** A fifth fluid is rejected without changing any channel. Clicking an existing fluid removes it. */
    public boolean toggleType(FluidType fluid) {
        FluidType[] next = toggledTypes(fluid);
        if(next == null) return false;
        setTypes(next); return true;
    }
    public FluidType[] toggledTypes(FluidType fluid) {
        if(fluid == null) return null;
        java.util.List<FluidType> next = new java.util.ArrayList<>();
        for(FluidType channel : channels) if(channel != Fluids.NONE && channel != fluid) next.add(channel);
        if(fluid != Fluids.NONE && !carries(fluid)) {
            if(next.size() == MAX_CHANNELS) return null;
            next.add(fluid);
        }
        return next.isEmpty() ? new FluidType[] {Fluids.NONE} : next.toArray(new FluidType[0]);
    }
    public void setType(FluidType nextType) { if(nextType != null) setTypes(new FluidType[] {nextType}); }
    public void setTypes(FluidType... requested) {
        java.util.List<FluidType> next = new java.util.ArrayList<>();
        for(FluidType fluid : requested) if(fluid != null && fluid != Fluids.NONE && !next.contains(fluid)) next.add(fluid);
        if(next.size() > MAX_CHANNELS) throw new IllegalArgumentException("A shared pipe supports at most four fluids");
        FluidType[] selected = next.isEmpty() ? new FluidType[] {Fluids.NONE} : next.toArray(new FluidType[0]);
        if(java.util.Arrays.equals(channels, selected)) return;
        detachNodes(); channels = selected; type = channels[0];
        markDirty();
        if(worldObj instanceof WorldServer) ((WorldServer) worldObj).getPlayerManager().markBlockForUpdate(xCoord, yCoord, zCoord);
        queueNodeReconciliation(); api.hbm.fluidmk2.FluidNetEndpointRegistry.markTopologyDirty(worldObj);
    }
    public FluidNetMK2 getFluidNet() { return getFluidNet(type); }
    @Override public FluidNetMK2 getFluidNet(FluidType fluid) {
        if(!shouldCreateNode()) return null;
        for(int i = 0; i < channels.length; i++) if(fluid == channels[i] && channelNodes[i] != null && channelNodes[i].hasValidNet()) return channelNodes[i].net;
        return null;
    }
    @Override public long transferFluid(FluidType fluid, int pressure, long amount) {
        FluidNetMK2 network = getFluidNet(fluid);
        return network == null ? amount : network.transferFluidExternal(fluid, pressure, amount, null);
    }

	@Override
	public long getDemand(FluidType type, int pressure) { return 0; }

	@Override
	public IPipeNet getPipeNet(FluidType fluid) {
		return PipeNet.forNetwork(getFluidNet(fluid));
	}

	@Override
	public void setPipeNet(FluidType type, IPipeNet network) {
		// Compatibility no-op: FluidNode/FluidNetMK2 own topology.
	}

	@Override
	public boolean canConnect(FluidType fluid, ForgeDirection direction) {
		return direction != ForgeDirection.UNKNOWN && carries(fluid);
	}

	@Override
	public void invalidate() {
		this.recordUnload();
		detachNodes();
		super.invalidate();
	}

	@Override
	public void onChunkUnload() {
		this.recordUnload();
		super.onChunkUnload();
		// Logical conductor topology is compact, world-owned, and dormant while unloaded.
        java.util.Arrays.fill(channelNodes, null); this.node = null;
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
		int[] saved = nbt.hasKey("sharedPipeTypes") ? nbt.getIntArray("sharedPipeTypes") : new int[] {nbt.getInteger("type")};
        java.util.List<FluidType> restored = new java.util.ArrayList<>();
        for(int id : saved) {
            FluidType fluid = Fluids.fromID(id);
            if(fluid != Fluids.NONE && !restored.contains(fluid)) restored.add(fluid);
        }
        if(restored.size() > MAX_CHANNELS) throw new IllegalArgumentException("Invalid saved shared pipe channels");
        FluidType[] next = restored.isEmpty() ? new FluidType[] {Fluids.NONE} : restored.toArray(new FluidType[0]);
        if(!java.util.Arrays.equals(channels, next)) { detachNodes(); channels = next; }
        type = channels[0];
        if(worldObj != null && !worldObj.isRemote) queueNodeReconciliation();
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setInteger("type", this.type.getID());
        nbt.setIntArray("sharedPipeTypes", getFluidIDToCopy());
	}

	@Override
	public int[] getFluidIDToCopy() {
        int[] ids = new int[channels.length]; for(int i = 0; i < ids.length; i++) ids[i] = channels[i].getID(); return ids;
    }
    @Override public NBTTagCompound getSettings(World world, int x, int y, int z) {
        NBTTagCompound settings = IFluidCopiable.super.getSettings(world, x, y, z);
        settings.setIntArray("sharedPipeTypes", getFluidIDToCopy()); return settings;
    }
    public void appendChannelInfo(java.util.List<String> text) {
        if(channels.length > 1) text.add("Shared pipe: " + channels.length + "/4 fluids");
        for(FluidType channel : channels) text.add("&[" + channel.getColor() + "&]" + channel.getLocalizedName());
    }

	@Override
	public FluidTank getTankToPaste() { return null; }

	@Override
	public void pasteSettings(NBTTagCompound nbt, int index, World world, EntityPlayer player, int x, int y, int z) {
        if(world.isRemote) return;
        int[] ids = nbt.hasKey("sharedPipeTypes") ? nbt.getIntArray("sharedPipeTypes") : nbt.getIntArray("fluidID");
        if(ids.length == 0) return;
        FluidType[] fluids;
        if(nbt.hasKey("sharedPipeTypes")) {
            fluids = new FluidType[ids.length]; for(int i = 0; i < ids.length; i++) fluids[i] = Fluids.fromID(ids[i]);
        } else fluids = new FluidType[] {Fluids.fromID(index >= 0 && index < ids.length ? ids[index] : 0)};
        if(HbmPlayerProps.getData(player).getKeyPressed(HbmKeybinds.EnumKeybind.TOOL_CTRL)) {
            com.hbm.blocks.network.FluidDuctBase.changeChannelsRecursively(world, x, y, z, getTypes(), fluids, 64, player);
        } else setTypes(fluids);
	}
}
