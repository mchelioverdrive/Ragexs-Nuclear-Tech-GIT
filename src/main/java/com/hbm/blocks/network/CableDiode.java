package com.hbm.blocks.network;

import api.hbm.energymk2.EnergyUnits;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.BobMathUtil;
import com.hbm.util.Compat;
import com.hbm.util.I18nUtil;

import api.hbm.block.IToolable;
import api.hbm.energymk2.IEnergyConnectorBlock;
import api.hbm.energymk2.IEnergyConnectorMK2;
import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.Nodespace;
import api.hbm.energymk2.Nodespace.PowerNode;
import api.hbm.energymk2.PowerNetMK2;
import api.hbm.energymk2.IEnergyReceiverMK2.ConnectionPriority;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Pre;
import net.minecraftforge.common.util.ForgeDirection;

public class CableDiode extends BlockContainer implements IEnergyConnectorBlock, ILookOverlay, IToolable, ITooltipProvider {
	
	public CableDiode(Material mat) {
		super(mat);
	}

	public static int renderID = RenderingRegistry.getNextAvailableRenderId();

	@Override
	public int getRenderType() {
		return renderID;
	}
	
	@Override
	public boolean isOpaqueCube() {
		return false;
	}
	
	@Override
	public boolean renderAsNormalBlock() {
		return false;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public boolean shouldSideBeRendered(IBlockAccess p_149646_1_, int p_149646_2_, int p_149646_3_, int p_149646_4_, int p_149646_5_) {
		return true;
	}
	
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack stack) {
		int l = BlockPistonBase.determineOrientation(world, x, y, z, player);
		world.setBlockMetadataWithNotify(x, y, z, l, 2);
	}

	@Override
	public boolean canConnect(IBlockAccess world, int x, int y, int z, ForgeDirection dir) {
		return true;
	}

	@Override
	public boolean onScrew(World world, EntityPlayer player, int x, int y, int z, int side, float fX, float fY, float fZ, ToolType tool) {

		TileEntityDiode te = (TileEntityDiode)world.getTileEntity(x, y, z);
		
		if(world.isRemote)
			return true;
		
		if(tool == ToolType.SCREWDRIVER) {
			if(te.level < 11)
				te.level++;
			PowerNetMK2.markReceiverDemandDirty(te);
			te.markDirty();
			world.markBlockForUpdate(x, y, z);
			return true;
		}
		
		if(tool == ToolType.HAND_DRILL) {
			if(te.level > 1)
				te.level--;
			PowerNetMK2.markReceiverDemandDirty(te);
			te.markDirty();
			world.markBlockForUpdate(x, y, z);
			return true;
		}
		
		if(tool == ToolType.DEFUSER) {
			int p = te.priority.ordinal() + 1;
			if(p > 4) p = 0;
			te.priority = ConnectionPriority.values()[p];
			PowerNetMK2.markReceiverDemandDirty(te);
			te.markDirty();
			world.markBlockForUpdate(x, y, z);
			return true;
		}
		
		return false;
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
		list.add(EnumChatFormatting.GOLD + "Limits throughput and restricts flow direction");
		list.add(EnumChatFormatting.YELLOW + "Use screwdriver to increase throughput");
		list.add(EnumChatFormatting.YELLOW + "Use hand drill to decrease throughput");
		list.add(EnumChatFormatting.YELLOW + "Use defuser to change network priority");
	}

	@Override
	public void printHook(Pre event, World world, int x, int y, int z) {
		
		TileEntity te = world.getTileEntity(x, y, z);
		
		if(!(te instanceof TileEntityDiode))
			return;
		
		TileEntityDiode diode = (TileEntityDiode) te;
		
		List<String> text = new ArrayList();
		text.add("Maximum Transfer: " + EnergyUnits.formatQuantaPerTickAsWatts(diode.getRatedTransferQuantaPerTick()));
		text.add("Priority: " + diode.priority.name());
		
		ILookOverlay.printGeneric(event, I18nUtil.resolveKey(getUnlocalizedName() + ".name"), 0xffff00, 0x404000, text);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityDiode();
	}
	
	public static class TileEntityDiode extends TileEntityLoadedBase implements IEnergyReceiverMK2 {
		
		@Override
		public void readFromNBT(NBTTagCompound nbt) {
			super.readFromNBT(nbt);
			level = nbt.getInteger("level");
			priority = ConnectionPriority.values()[nbt.getByte("p")];
		}
		
		@Override
		public void writeToNBT(NBTTagCompound nbt) {
			super.writeToNBT(nbt);
			nbt.setInteger("level", level);
			nbt.setByte("p", (byte) this.priority.ordinal());
		}

		@Override
		public Packet getDescriptionPacket() {
			NBTTagCompound nbt = new NBTTagCompound();
			this.writeToNBT(nbt);
			return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, nbt);
		}
		
		@Override
		public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
			this.readFromNBT(pkt.func_148857_g());
		}
		
		int level = 1;
		
		private ForgeDirection getDir() {
			return ForgeDirection.getOrientation(this.getBlockMetadata()).getOpposite();
		}

		@Override
		public void updateEntity() {
			
			if(!worldObj.isRemote) {
				for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
					
					if(dir == getDir())
						continue;
					
					this.trySubscribe(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
				}
				
				pulses = 0;
				this.setStoredEnergyQuanta(0); //tick is over, reset our allowed transfe
			}
		}

		@Override
		public boolean canConnect(ForgeDirection dir) {
			return dir != getDir();
		}
		
		/** Used as an intra-tick tracker for how much energy has been transmitted, resets to 0 each tick and maxes out based on transfer */
		private long energyQuanta;
		private boolean recursionBrake = false;
		private int pulses = 0;
		public ConnectionPriority priority = ConnectionPriority.NORMAL;

		@Override
		public long receiveEnergyQuanta(long energyQuanta) {

			if(recursionBrake)
				return energyQuanta;
			
			pulses++;
			if(this.getStoredEnergyQuanta() >= this.getEnergyCapacityQuanta() || pulses > 10) return energyQuanta; //if we have already maxed out transfer or max pulses, abort
			
			recursionBrake = true;
			
			ForgeDirection dir = getDir();
			PowerNode node = Nodespace.getNode(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
			TileEntity te = Compat.getTileStandard(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
			
			if(node != null && !node.expired && node.hasValidNet() && te instanceof IEnergyConnectorMK2 && ((IEnergyConnectorMK2) te).canConnect(dir.getOpposite())) {
				long toTransfer = Math.min(energyQuanta, this.getMaxInputQuantaPerTick());
				long remainder = node.net.sendEnergyQuantaThroughDiode(toTransfer);
				long transferred = (toTransfer - remainder);
				this.setStoredEnergyQuanta(this.energyQuanta + transferred);
				energyQuanta -= transferred;
				
			} else if(te instanceof IEnergyReceiverMK2 && te != this) {
				IEnergyReceiverMK2 rec = (IEnergyReceiverMK2) te;
				if(rec.canConnect(dir.getOpposite())) {
					long toTransfer = Math.min(energyQuanta, rec.getMaxInputQuantaPerTick());
					long remainder = rec.receiveEnergyQuanta(toTransfer);
					energyQuanta -= (toTransfer - remainder);
					recursionBrake = false;
					return energyQuanta;
				}
			}
			
			recursionBrake = false;
			return energyQuanta;
		}

		@Override
		public long getMaxInputQuantaPerTick() {
			return this.getEnergyCapacityQuanta() - this.getStoredEnergyQuanta();
		}

		public long getRatedTransferQuantaPerTick() {
			return (long) Math.pow(10, level);
		}

		@Override
		public long getEnergyCapacityQuanta() {
			return getRatedTransferQuantaPerTick();
		}

		@Override
		public long getStoredEnergyQuanta() {
			return Math.min(energyQuanta, this.getEnergyCapacityQuanta());
		}
		
		@Override
		public void setStoredEnergyQuanta(long energyQuanta) {
			if(this.energyQuanta == energyQuanta) return;
			this.energyQuanta = energyQuanta;
			this.markPowerNetDirty();
		}

		@Override
		public ConnectionPriority getPriority() {
			return this.priority;
		}
	}
}
