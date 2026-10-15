package com.hbm.blocks.machine;

import java.util.List;
import java.util.Random;

import com.hbm.blocks.ITooltipProvider;
import com.hbm.handler.atmosphere.IBlockSealable;
import com.hbm.inventory.fluid.trait.FT_Combustible.FuelGrade;
import com.hbm.tileentity.machine.TileEntityMachineDiesel;

import com.hbm.util.I18nUtil;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class MachineDiesel extends BlockMachineBase implements ITooltipProvider, IBlockSealable {

	public MachineDiesel() {
		super(Material.iron, 0);
		this.rotatable = true;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityMachineDiesel();
	}

	@Override
	public int getRenderType(){
		return -1;
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
	public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
		TileEntity tile = world.getTileEntity(x, y, z);

		if(tile instanceof TileEntityMachineDiesel) {
			TileEntityMachineDiesel diesel = (TileEntityMachineDiesel) tile;

			if(diesel.hasAcceptableFuel() && diesel.tank.getFill() > 0) {

				ForgeDirection dir = ForgeDirection.getOrientation(tile.getBlockMetadata());
				ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
				world.spawnParticle("smoke", x + 0.5 - dir.offsetX * 0.6 + rot.offsetX * 0.1875, y + 0.3125, z + 0.5 - dir.offsetZ * 0.6 + rot.offsetZ * 0.1875, 0, 0, 0);
			}
		}
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {

        list.add(EnumChatFormatting.YELLOW + com.hbm.realsim.MachineInfo.watts(TileEntityMachineDiesel.ratedPowerWatts) + " compact compression-ignition genset");
        list.add("Diesel/cracked diesel: 32%; reform blends: 30%; petroil: 27% LHV");
        list.add("Short service tank; use external storage for autonomy");

		list.add(EnumChatFormatting.DARK_RED + I18nUtil.resolveKey("tooltip.furnace.monoxide"));
		list.add(EnumChatFormatting.AQUA + I18nUtil.resolveKey("tooltip.machine.waterlogging"));
	}

	@Override
	public boolean isSealed(World world, int x, int y, int z) {
		return false;
	}
}
