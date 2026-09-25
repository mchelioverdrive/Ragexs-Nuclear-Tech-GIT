package com.hbm.tileentity;

import java.util.List;

import com.hbm.dim.CelestialBody;
import com.hbm.dim.orbit.WorldProviderOrbit;
import com.hbm.dim.trait.CBT_Atmosphere;
import com.hbm.handler.atmosphere.AtmosphereBlob;
import com.hbm.handler.atmosphere.ChunkAtmosphereManager;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.AuxGaugePacket;
import com.hbm.packet.toclient.BufPacket;
import com.hbm.packet.toclient.NBTPacket;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyHandlerMK2;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidTank;

public abstract class TileEntityMachineBase extends TileEntityLoadedBase implements ISidedInventory, INBTPacketReceiver, IBufPacketReceiver {

	public ItemStack slots[];

	private String customName;

	private NBTTagCompound lastPackedNBT = null;
	private ByteBuf lastPackedBuf = null;
	private boolean networkSyncDirty = true;

	public TileEntityMachineBase(int slotCount) {
		slots = new ItemStack[slotCount];
	}

	/** The "chunks is modified, pls don't forget to save me" effect of markDirty, minus the block updates */
	public void markChanged() {
		this.worldObj.markTileEntityChunkModified(this.xCoord, this.yCoord, this.zCoord, this);
	}

	@Override
	public int getSizeInventory() {
		return slots.length;
	}

	@Override
	public ItemStack getStackInSlot(int i) {
		return slots[i];
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int i) {
		if(slots[i] != null)
		{
			ItemStack itemStack = slots[i];
			slots[i] = null;
			onInventorySlotChanged(i);
			return itemStack;
		} else {
			return null;
		}
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack itemStack) {
		slots[i] = itemStack;
		if(itemStack != null && itemStack.stackSize > getInventoryStackLimit())
		{
			itemStack.stackSize = getInventoryStackLimit();
		}
		onInventorySlotChanged(i);
	}

	@Override
	public String getInventoryName() {
		return this.hasCustomInventoryName() ? this.customName : getName();
	}

	public abstract String getName();

	@Override
	public boolean hasCustomInventoryName() {
		return this.customName != null && this.customName.length() > 0;
	}

	public void setCustomName(String name) {
		this.customName = name;
		this.markNetworkDirty();
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer player) {
		if(worldObj.getTileEntity(xCoord, yCoord, zCoord) != this) {
			return false;
		} else {
			return player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 128;
		}
	}

	@Override
	public void openInventory() {}
	@Override
	public void closeInventory() {}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack itemStack) {
		return false;
	}

	@Override
	public ItemStack decrStackSize(int slot, int amount) {
		if(slots[slot] != null) {

			if(slots[slot].stackSize <= amount) {
				ItemStack itemStack = slots[slot];
				slots[slot] = null;
				onInventorySlotChanged(slot);
				return itemStack;
			}

			ItemStack itemStack1 = slots[slot].splitStack(amount);
			if(slots[slot].stackSize == 0) {
				slots[slot] = null;
			}
			onInventorySlotChanged(slot);

			return itemStack1;
		} else {
			return null;
		}
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack itemStack, int side) {
		return this.isItemValidForSlot(slot, itemStack);
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack itemStack, int side) {
		return false;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[] { };
	}

	/** Called by the standard inventory mutation paths. Subclasses can invalidate local caches here. */
	protected void onInventorySlotChanged(int slot) {
		this.markNetworkDirty();
		this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
		if(this instanceof IEnergyHandlerMK2) ((IEnergyHandlerMK2) this).markPowerNetDirty();
	}

	/** Future common hook for tank owners once a mutation path has reliable coverage. */
	protected void onFluidStorageChanged() {
		this.markMachineFluidDirty();
	}

	/** Future common hook for block/configuration mutations. */
	protected void onMachineConfigurationChanged() {
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
	}

	/** Future common hook for neighbor/controller topology mutations. */
	protected void onMachineTopologyChanged() {
		this.markMachineDirty(MachineDirtyCause.TOPOLOGY);
	}

	/** Future common hook for redstone state changes. */
	protected void onMachineRedstoneChanged() {
		this.markMachineDirty(MachineDirtyCause.REDSTONE);
	}

	/** Marks client-visible machine state for the opt-in allocation-free sync path. */
	public void markNetworkDirty() {
		this.networkSyncDirty = true;
	}

	public int getGaugeScaled(int i, FluidTank tank) {
		return tank.getFluidAmount() * i / tank.getCapacity();
	}

	//abstracting this method forces child classes to implement it
	//so i don't have to remember the fucking method name
	//was it update? onUpdate? updateTile? did it have any args?
	//shit i don't know man
	@Override
	public abstract void updateEntity();

	@Deprecated public void updateGauge(int val, int id, int range) {
		if(!worldObj.isRemote) PacketDispatcher.wrapper.sendToAllAround(new AuxGaugePacket(xCoord, yCoord, zCoord, val, id), new TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, range));
	}
	@Deprecated public void processGauge(int val, int id) { }

	@Deprecated public void networkPack(NBTTagCompound nbt, int range) {
		nbt.setBoolean("muffled", muffled);

		if(worldObj.isRemote) {
			return;
		}

		// Same as networkPackNT
		if (lastPackedNBT != null && lastPackedNBT.equals(nbt) && worldObj.getWorldTime() % 20 != 0) {
			return;
		}
		this.lastPackedNBT = nbt;

		PacketDispatcher.wrapper.sendToAllAround(new NBTPacket(nbt, xCoord, yCoord, zCoord), new TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, range));
	}

	@Deprecated
	public void networkUnpack(NBTTagCompound nbt) {
		this.muffled = nbt.getBoolean("muffled");
	}

	/** Sends a sync packet that uses ByteBuf for efficient information-cramming */
	public void networkPackNT(int range) {
		if(worldObj.isRemote) {
			return;
		}

		BufPacket packet = new BufPacket(xCoord, yCoord, zCoord, this);
		ByteBuf buf = Unpooled.buffer();
		packet.toBytes(buf);

		// Don't send unnecessary packets, except for maybe one every second or so.
		// Retain the periodic duplicate as a fallback for later packet loss. Initial chunk state
		// is delivered independently by getDescriptionPacket() and onDataPacket().
		if (lastPackedBuf != null && buf.equals(lastPackedBuf) && worldObj.getWorldTime() % 20 != 0) {
			buf.release();
			return;
		}
		if(lastPackedBuf != null) lastPackedBuf.release();
		this.lastPackedBuf = buf;

		PacketDispatcher.wrapper.sendToAllAround(packet, new TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, range));
	}

	/**
	 * Opt-in sync path for machines whose client-visible mutations call markNetworkDirty().
	 * A baseline packet is still sent once per second for clients that reload a chunk.
	 */
	public void networkPackNTIfDirty(int range) {
		if(worldObj.isRemote || (!this.networkSyncDirty && worldObj.getWorldTime() % 20 != 0)) return;

		PacketDispatcher.wrapper.sendToAllAround(new BufPacket(xCoord, yCoord, zCoord, this), new TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, range));
		this.networkSyncDirty = false;
	}

	@Override
	public Packet getDescriptionPacket() {
		return BufPacketTileEntitySync.createDescriptionPacket(this, this);
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
		BufPacketTileEntitySync.applyOnClientThread(packet.func_148857_g(), this);
	}

	private void releaseLastPackedBuf() {
		if(this.lastPackedBuf != null) {
			if(this.lastPackedBuf.refCnt() > 0) this.lastPackedBuf.release();
			this.lastPackedBuf = null;
		}
	}

	@Override
	public void invalidate() {
		releaseLastPackedBuf();
		super.invalidate();
	}

	@Override
	public void onChunkUnload() {
		releaseLastPackedBuf();
		super.onChunkUnload();
	}

	@Override public void serialize(ByteBuf buf) {
		buf.writeBoolean(muffled);
	}

	@Override public void deserialize(ByteBuf buf) {
		this.muffled = buf.readBoolean();
	}

	@Deprecated
	public void handleButtonPacket(int value, int meta) { }

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		NBTTagList list = nbt.getTagList("items", 10);

		for(int i = 0; i < list.tagCount(); i++)
		{
			NBTTagCompound nbt1 = list.getCompoundTagAt(i);
			byte b0 = nbt1.getByte("slot");
			if(b0 >= 0 && b0 < slots.length)
			{
				slots[b0] = ItemStack.loadItemStackFromNBT(nbt1);
			}
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		NBTTagList list = new NBTTagList();

		for(int i = 0; i < slots.length; i++)
		{
			if(slots[i] != null)
			{
				NBTTagCompound nbt1 = new NBTTagCompound();
				nbt1.setByte("slot", (byte)i);
				slots[i].writeToNBT(nbt1);
				list.appendTag(nbt1);
			}
		}
		nbt.setTag("items", list);
	}

	public void updateRedstoneConnection(DirPos pos) {

		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		ForgeDirection dir = pos.getDir();
		Block block1 = worldObj.getBlock(x, y, z);

		block1.onNeighborChange(worldObj, x, y, z, xCoord, yCoord, zCoord);
		block1.onNeighborBlockChange(worldObj, x, y, z, this.getBlockType());
		if(block1.isNormalCube(worldObj, x, y, z)) {
			x += dir.offsetX;
			y += dir.offsetY;
			z += dir.offsetZ;
			Block block2 = worldObj.getBlock(x, y, z);

			if(block2.getWeakChanges(worldObj, x, y, z)) {
				block2.onNeighborChange(worldObj, x, y, z, xCoord, yCoord, zCoord);
				block2.onNeighborBlockChange(worldObj, x, y, z, this.getBlockType());
			}
		}
	}

	// TODO: Consume air from connected tanks if available
	public boolean breatheAir(int amount) {
		CBT_Atmosphere atmosphere = worldObj.provider instanceof WorldProviderOrbit ? null : CelestialBody.getTrait(worldObj, CBT_Atmosphere.class);
		if(atmosphere != null) {
			if(atmosphere.hasFluid(Fluids.AIR, 0.19) || atmosphere.hasFluid(Fluids.OXYGEN, 0.09)) {
				return true;
			}
		}

		List<AtmosphereBlob> blobs = ChunkAtmosphereManager.proxy.getBlobs(worldObj, xCoord, yCoord, zCoord);
		for(AtmosphereBlob blob : blobs) {
			if(blob.hasFluid(Fluids.AIR, 0.19) || blob.hasFluid(Fluids.OXYGEN, 0.09)) {
				blob.consume(amount);
				return true;
			}
		}

		return false;
	}
}
