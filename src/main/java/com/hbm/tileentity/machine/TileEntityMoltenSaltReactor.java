package com.hbm.tileentity.machine;

import java.util.ArrayList;
import java.util.List;

import com.hbm.inventory.container.ContainerMoltenSaltReactor;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMoltenSaltReactor;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.CompatEnergyControl;
import com.hbm.util.fauxpointtwelve.BlockPos;

import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import api.hbm.tile.IInfoProviderEC;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMoltenSaltReactor extends TileEntityMachineBase implements IFluidStandardTransceiver, IFluidCopiable, IInfoProviderEC, IGUIProvider {

	public static final int SALT_CAPACITY_BASE = 16_000;
	public static final int SALT_CAPACITY_PER_BLOCK = 250;
	public static final int SALT_PER_CHANNEL = 2;

	public FluidTank[] tanks;
	public int output;
	public boolean assembled;
	public int reactorBlocks;
	public int portCount;
	public int channelCount;
	public int heatExchangerCount;
	public int sourceCount;

	protected List<BlockPos> ports = new ArrayList<BlockPos>();

	public TileEntityMoltenSaltReactor() {
		super(0);
		tanks = new FluidTank[2];
		tanks[0] = new FluidTank(Fluids.THORIUM_SALT, SALT_CAPACITY_BASE);
		tanks[1] = new FluidTank(Fluids.THORIUM_SALT_HOT, SALT_CAPACITY_BASE);
	}

	@Override
	public String getName() {
		return "container.moltenSaltReactor";
	}

	public void setup(int reactorBlocks, int portCount, int channelCount, int heatExchangerCount, int sourceCount, List<BlockPos> ports) {
		this.assembled = true;
		this.reactorBlocks = reactorBlocks;
		this.portCount = portCount;
		this.channelCount = channelCount;
		this.heatExchangerCount = heatExchangerCount;
		this.sourceCount = sourceCount;
		this.ports.clear();
		this.ports.addAll(ports);

		int capacity = this.getTankCapacity();
		tanks[0].changeTankSize(capacity);
		tanks[1].changeTankSize(capacity);
		this.markDirty();
	}

	@Override
	public void updateEntity() {
		if(!worldObj.isRemote) {
			this.output = 0;

			if(this.assembled) {
				this.updateConnections();
				this.processSalt();
				this.sendFluid();
			}

			NBTTagCompound data = new NBTTagCompound();
			data.setInteger("output", output);
			data.setBoolean("assembled", assembled);
			data.setInteger("reactorBlocks", reactorBlocks);
			data.setInteger("portCount", portCount);
			data.setInteger("channelCount", channelCount);
			data.setInteger("heatExchangerCount", heatExchangerCount);
			data.setInteger("sourceCount", sourceCount);
			tanks[0].writeToNBT(data, "salt");
			tanks[1].writeToNBT(data, "hotSalt");
			this.networkPack(data, 50);
		}
	}

	protected void processSalt() {
		int operations = Math.min(this.getMaxProcessRate(), tanks[0].getFill());
		operations = Math.min(operations, tanks[1].getMaxFill() - tanks[1].getFill());

		if(operations > 0) {
			tanks[0].setFill(tanks[0].getFill() - operations);
			tanks[1].setFill(tanks[1].getFill() + operations);
			this.output = operations;
		}
	}

	public int getMaxProcessRate() {
		if(!assembled) return 0;
		return Math.max(1, Math.min(channelCount, heatExchangerCount) * Math.max(1, sourceCount) * SALT_PER_CHANNEL);
	}

	public int getTankCapacity() {
		return SALT_CAPACITY_BASE + reactorBlocks * SALT_CAPACITY_PER_BLOCK;
	}

	protected void updateConnections() {
		for(BlockPos port : ports) {
			for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
				this.trySubscribe(tanks[0].getTankType(), worldObj, port.getX() + dir.offsetX, port.getY() + dir.offsetY, port.getZ() + dir.offsetZ, dir);
			}
		}
	}

	protected void sendFluid() {
		for(BlockPos port : ports) {
			for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
				this.sendFluid(tanks[1], worldObj, port.getX() + dir.offsetX, port.getY() + dir.offsetY, port.getZ() + dir.offsetZ, dir);
			}
		}
	}

	@Override
	public void networkUnpack(NBTTagCompound data) {
		super.networkUnpack(data);
		this.output = data.getInteger("output");
		this.assembled = data.getBoolean("assembled");
		this.reactorBlocks = data.getInteger("reactorBlocks");
		this.portCount = data.getInteger("portCount");
		this.channelCount = data.getInteger("channelCount");
		this.heatExchangerCount = data.getInteger("heatExchangerCount");
		this.sourceCount = data.getInteger("sourceCount");
		tanks[0].changeTankSize(this.getTankCapacity());
		tanks[1].changeTankSize(this.getTankCapacity());
		tanks[0].readFromNBT(data, "salt");
		tanks[1].readFromNBT(data, "hotSalt");
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.output = nbt.getInteger("output");
		this.assembled = nbt.getBoolean("assembled");
		this.reactorBlocks = nbt.getInteger("reactorBlocks");
		this.portCount = nbt.getInteger("portCount");
		this.channelCount = nbt.getInteger("channelCount");
		this.heatExchangerCount = nbt.getInteger("heatExchangerCount");
		this.sourceCount = nbt.getInteger("sourceCount");
		tanks[0].changeTankSize(this.getTankCapacity());
		tanks[1].changeTankSize(this.getTankCapacity());
		tanks[0].readFromNBT(nbt, "salt");
		tanks[1].readFromNBT(nbt, "hotSalt");
		this.readPorts(nbt);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setInteger("output", output);
		nbt.setBoolean("assembled", assembled);
		nbt.setInteger("reactorBlocks", reactorBlocks);
		nbt.setInteger("portCount", portCount);
		nbt.setInteger("channelCount", channelCount);
		nbt.setInteger("heatExchangerCount", heatExchangerCount);
		nbt.setInteger("sourceCount", sourceCount);
		tanks[0].writeToNBT(nbt, "salt");
		tanks[1].writeToNBT(nbt, "hotSalt");
		this.writePorts(nbt);
	}

	protected void readPorts(NBTTagCompound nbt) {
		this.ports.clear();
		NBTTagList list = nbt.getTagList("ports", NBT.TAG_COMPOUND);
		for(int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound tag = list.getCompoundTagAt(i);
			this.ports.add(new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")));
		}
	}

	protected void writePorts(NBTTagCompound nbt) {
		NBTTagList list = new NBTTagList();
		for(BlockPos port : ports) {
			NBTTagCompound tag = new NBTTagCompound();
			tag.setInteger("x", port.getX());
			tag.setInteger("y", port.getY());
			tag.setInteger("z", port.getZ());
			list.appendTag(tag);
		}
		nbt.setTag("ports", list);
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { tanks[1] };
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { tanks[0] };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank getTankToPaste() {
		return tanks[0];
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, net.minecraft.world.World world, int x, int y, int z) {
		return new ContainerMoltenSaltReactor(this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, net.minecraft.world.World world, int x, int y, int z) {
		return new GUIMoltenSaltReactor(this);
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, this.output > 0);
		data.setDouble(CompatEnergyControl.D_OUTPUT_MB, this.output);
	}
}
