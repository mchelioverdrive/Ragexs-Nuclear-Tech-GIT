package com.hbm.blocks.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.IPersistentInfoProvider;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.blocks.ModBlocks;
import com.hbm.lib.RefStrings;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.BufPacket;
import com.hbm.tileentity.IBufPacketReceiver;
import com.hbm.tileentity.IPersistentNBT;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.BobMathUtil;
import com.hbm.util.I18nUtil;
import com.hbm.util.fauxpointtwelve.BlockPos;

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.PowerNetMK2;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Pre;
import net.minecraftforge.common.util.ForgeDirection;

public class MachineCapacitor extends BlockContainer implements ILookOverlay, IPersistentInfoProvider, ITooltipProvider {

	@SideOnly(Side.CLIENT) public IIcon iconTop;
	@SideOnly(Side.CLIENT) public IIcon iconSide;
	@SideOnly(Side.CLIENT) public IIcon iconBottom;
	@SideOnly(Side.CLIENT) public IIcon iconInnerTop;
	@SideOnly(Side.CLIENT) public IIcon iconInnerSide;
	
	protected long capacityQuanta;
	String name;

	public MachineCapacitor(Material mat, long capacityQuanta, String name) {
		super(mat);
		this.capacityQuanta = capacityQuanta;
		this.name = name;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister iconRegister) {
		super.registerBlockIcons(iconRegister);
		this.iconTop = iconRegister.registerIcon(RefStrings.MODID + ":capacitor_" + name + "_top");
		this.iconSide = iconRegister.registerIcon(RefStrings.MODID + ":capacitor_" + name + "_side");
		this.iconBottom = iconRegister.registerIcon(RefStrings.MODID + ":capacitor_" + name + "_bottom");
		this.iconInnerTop = iconRegister.registerIcon(RefStrings.MODID + ":capacitor_" + name + "_inner_top");
		this.iconInnerSide = iconRegister.registerIcon(RefStrings.MODID + ":capacitor_" + name + "_inner_side");
	}

	public static int renderID = RenderingRegistry.getNextAvailableRenderId();

	@Override public int getRenderType() { return renderID; }
	@Override public boolean isOpaqueCube() { return false; }
	@Override public boolean renderAsNormalBlock() { return false; }

	@Override
	public int onBlockPlaced(World world, int x, int y, int z, int side, float fX, float fY, float fZ, int meta) {
		return side;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityCapacitor(this.capacityQuanta);
	}

	@Override
	public void printHook(Pre event, World world, int x, int y, int z) {
		
		TileEntity te = world.getTileEntity(x, y, z);
		
		if(!(te instanceof TileEntityCapacitor))
			return;
		
		TileEntityCapacitor battery = (TileEntityCapacitor) te;
		List<String> text = new ArrayList();
		text.add(EnergyUnits.formatJoules(battery.getStoredEnergyQuanta()) + " / " + EnergyUnits.formatJoules(battery.getEnergyCapacityQuanta()));
		
		double percent = (double) battery.getStoredEnergyQuanta() / (double) battery.getEnergyCapacityQuanta();
		int charge = (int) Math.floor(percent * 10_000D);
		int color = ((int) (0xFF - 0xFF * percent)) << 16 | ((int)(0xFF * percent) << 8);
		text.add("&[" + color + "&]" + (charge / 100D) + "%");
		text.add(EnumChatFormatting.GREEN + "-> " + EnumChatFormatting.RESET + "+" + EnergyUnits.formatQuantaPerTickAsWatts(battery.receivedQuantaThisTick));
		text.add(EnumChatFormatting.RED + "<- " + EnumChatFormatting.RESET + "-" + EnergyUnits.formatQuantaPerTickAsWatts(battery.sentQuantaThisTick));
		
		ILookOverlay.printGeneric(event, I18nUtil.resolveKey(getUnlocalizedName() + ".name"), 0xffff00, 0x404000, text);
	}

	@Override
	public void addInformation(ItemStack stack, NBTTagCompound persistentTag, EntityPlayer player, List list, boolean ext) {
		list.add(EnumChatFormatting.GOLD + "Energy Capacity: " + EnergyUnits.formatJoules(this.capacityQuanta));
		list.add(EnumChatFormatting.GOLD + "Maximum Input: " + EnergyUnits.formatQuantaPerTickAsWatts(this.capacityQuanta / 200));
		list.add(EnumChatFormatting.GOLD + "Maximum Output: " + EnergyUnits.formatQuantaPerTickAsWatts(this.capacityQuanta / 600));
		list.add(EnumChatFormatting.YELLOW + EnergyUnits.formatJoules(EnergyUnits.readEnergyQuanta(persistentTag, "power")) + " / " + EnergyUnits.formatJoules(EnergyUnits.readCapacityQuanta(persistentTag, "maxPower")));
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {

		if(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
			for(String s : I18nUtil.resolveKeyArray("tile.capacitor.desc")) list.add(EnumChatFormatting.YELLOW + s);
		} else {
			list.add(EnumChatFormatting.DARK_GRAY + "" + EnumChatFormatting.ITALIC +"Hold <" +
					EnumChatFormatting.YELLOW + "" + EnumChatFormatting.ITALIC + "LSHIFT" +
					EnumChatFormatting.DARK_GRAY + "" + EnumChatFormatting.ITALIC + "> to display more info");
		}
		if(this == ModBlocks.capacitor_complex) {
			list.add("TaCdSa236-7 N-Boosted Anti Mass core surrounded by");
			list.add("Flashlead antimatter lattice in a BF Stabilization Matrix");
			list.add("subjected to the Ferric Osmiridium-Lutece");
			list.add("ψ(x,t)=Aeiℏ(px−Et) Wavefunction.");
		}
	
	}
	
	@Override
	public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
		return IPersistentNBT.getDrops(world, x, y, z, this);
	}

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack itemStack) {
		IPersistentNBT.restoreData(world, x, y, z, itemStack);
	}

	@Override
	public void onBlockHarvested(World world, int x, int y, int z, int meta, EntityPlayer player) {
		
		if(!player.capabilities.isCreativeMode) {
			harvesters.set(player);
			this.dropBlockAsItem(world, x, y, z, meta, 0);
			harvesters.set(null);
		}
	}
	
	@Override
	public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int meta) {
		player.addStat(StatList.mineBlockStatArray[getIdFromBlock(this)], 1);
		player.addExhaustion(0.025F);
	}

	public static class TileEntityCapacitor extends TileEntityLoadedBase implements IEnergyProviderMK2, IEnergyReceiverMK2, IBufPacketReceiver, IPersistentNBT {
		
		public long energyQuanta;
		protected long maxPower;
		public long receivedQuantaThisTick;
		public long sentQuantaThisTick;
		private int connectionMetadata = Integer.MIN_VALUE;
		private int providerX;
		private int providerY;
		private int providerZ;
		private ForgeDirection providerDirection = ForgeDirection.UNKNOWN;
		
		public TileEntityCapacitor() { }
		
		public TileEntityCapacitor(long maxPower) {
			this.maxPower = maxPower;
		}
		
		@Override
		public void updateEntity() {
			
			if(!worldObj.isRemote) {
				
				int metadata = this.getBlockMetadata();
				ForgeDirection opp = ForgeDirection.getOrientation(metadata);
				ForgeDirection dir = opp.getOpposite();
				
				BlockPos pos = new BlockPos(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
				
				boolean didStep = false;
				ForgeDirection last = null;
				
				while(worldObj.getBlock(pos.getX(), pos.getY(), pos.getZ()) == ModBlocks.capacitor_bus) {
					ForgeDirection current = ForgeDirection.getOrientation(worldObj.getBlockMetadata(pos.getX(), pos.getY(), pos.getZ()));
					if(!didStep) last = current;
					didStep = true;
					
					if(last != current) {
						pos = null;
						break;
					}
					
					pos = pos.offset(current);
				}

				int targetX = pos != null && last != null ? pos.getX() : 0;
				int targetY = pos != null && last != null ? pos.getY() : 0;
				int targetZ = pos != null && last != null ? pos.getZ() : 0;
				ForgeDirection targetDirection = pos != null && last != null ? last : ForgeDirection.UNKNOWN;
				if(this.connectionMetadata != metadata || this.providerX != targetX || this.providerY != targetY || this.providerZ != targetZ || this.providerDirection != targetDirection) {
					PowerNetMK2.detachEndpoint(this);
					this.connectionMetadata = metadata;
					this.providerX = targetX;
					this.providerY = targetY;
					this.providerZ = targetZ;
					this.providerDirection = targetDirection;
				}
				
				if(pos != null && last != null) {
					this.tryUnsubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ());
					this.tryProvide(worldObj, pos.getX(), pos.getY(), pos.getZ(), last);
				}
				
				this.trySubscribe(worldObj, xCoord + opp.offsetX, yCoord + opp.offsetY, zCoord + opp.offsetZ, opp);

				PacketDispatcher.wrapper.sendToAllAround(new BufPacket(xCoord, yCoord, zCoord, this), new TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 15));
				
				this.sentQuantaThisTick = 0;
				this.receivedQuantaThisTick = 0;
			}
		}

		@Override
		public void serialize(ByteBuf buf) {
			buf.writeLong(energyQuanta);
			buf.writeLong(maxPower);
			buf.writeLong(receivedQuantaThisTick);
			buf.writeLong(sentQuantaThisTick);
		}
		
		@Override
		public void deserialize(ByteBuf buf) {
			energyQuanta = buf.readLong();
			maxPower = buf.readLong();
			receivedQuantaThisTick = buf.readLong();
			sentQuantaThisTick = buf.readLong();
		}

		@Override
		public long receiveEnergyQuanta(long energyQuanta) {
			if(energyQuanta <= this.getEnergyCapacityQuanta() - this.getStoredEnergyQuanta()) {
				this.setStoredEnergyQuanta(energyQuanta + this.getStoredEnergyQuanta());
				this.receivedQuantaThisTick += energyQuanta;
				return 0;
			}
			long capacity = this.getEnergyCapacityQuanta() - this.getStoredEnergyQuanta();
			long overshoot = energyQuanta - capacity;
			this.receivedQuantaThisTick += (this.getEnergyCapacityQuanta() - this.getStoredEnergyQuanta());
			this.setStoredEnergyQuanta(this.getEnergyCapacityQuanta());
			return overshoot;
		}
		
		@Override
		public void extractEnergyQuanta(long energyQuanta) {
			this.sentQuantaThisTick += Math.min(this.getStoredEnergyQuanta(), energyQuanta);
			this.setStoredEnergyQuanta(this.getStoredEnergyQuanta() - energyQuanta);
		}

		@Override
		public long getStoredEnergyQuanta() {
			return energyQuanta;
		}

		@Override
		public long getEnergyCapacityQuanta() {
			return maxPower;
		}

		@Override public long getMaxOutputQuantaPerTick() {
			return this.getEnergyCapacityQuanta() / 300;
		}
		
		@Override public long getMaxInputQuantaPerTick() {
			return this.getEnergyCapacityQuanta() / 100;
		}

		@Override
		public ConnectionPriority getPriority() {
			return ConnectionPriority.LOW;
		}

		@Override
		public void setStoredEnergyQuanta(long energyQuanta) {
			if(this.energyQuanta == energyQuanta) return;
			this.energyQuanta = energyQuanta;
			this.markPowerNetDirty();
		}
		
		@Override
		public boolean canConnect(ForgeDirection dir) {
			return dir == ForgeDirection.getOrientation(this.getBlockMetadata());
		}

		@Override
		public void writeNBT(NBTTagCompound nbt) {
			NBTTagCompound data = new NBTTagCompound();
			EnergyUnits.writeEnergyQuanta(data, energyQuanta);
			EnergyUnits.writeCapacityQuanta(data, maxPower);
			nbt.setTag(NBT_PERSISTENT_KEY, data);
		}

		@Override
		public void readNBT(NBTTagCompound nbt) {
			NBTTagCompound data = nbt.getCompoundTag(NBT_PERSISTENT_KEY);
			this.energyQuanta = EnergyUnits.readEnergyQuanta(data, "power");
			this.maxPower = EnergyUnits.readCapacityQuanta(data, "maxPower");
		}
		
		@Override
		public void readFromNBT(NBTTagCompound nbt) {
			super.readFromNBT(nbt);
			this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
			this.maxPower = EnergyUnits.readCapacityQuanta(nbt, "maxPower");
		}
		
		@Override
		public void writeToNBT(NBTTagCompound nbt) {
			super.writeToNBT(nbt);
			EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
			EnergyUnits.writeCapacityQuanta(nbt, maxPower);
		}
	}
}
