package com.hbm.blocks.bomb;

import com.hbm.config.GeneralConfig;
import com.hbm.explosion.ExplosionLarge;
import com.hbm.explosion.vanillant.ExplosionVNT;
import com.hbm.explosion.vanillant.standard.BlockAllocatorStandard;
import com.hbm.explosion.vanillant.standard.BlockProcessorStandard;
import com.hbm.main.MainRegistry;
import com.hbm.tileentity.bomb.TileEntityCharge;
import cpw.mods.fml.client.registry.RenderingRegistry;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import org.apache.logging.log4j.Level;

import java.util.List;

public class BlockChargeC4CSGO extends BlockChargeC4 {

	@Override
	public BombReturnCode explode(World world, int x, int y, int z) {

		if(!world.isRemote) {
			safe = true;
			world.setBlockToAir(x, y, z);
			safe = false;

			ExplosionVNT xnt = new ExplosionVNT(world, x + 0.5, y + 0.5, z + 0.5, 30F).makeStandard();
			xnt.setBlockAllocator(new BlockAllocatorStandard(32));
			xnt.setBlockProcessor(new BlockProcessorStandard().setNoDrop());
			xnt.explode();
			ExplosionLarge.spawnParticles(world, x + 0.5, y + 0.5, z + 0.5, 50);

			return BombReturnCode.DETONATED;
		}

		return BombReturnCode.UNDEFINED;
	}

	public static int renderID = RenderingRegistry.getNextAvailableRenderId();

	@Override
	public int getRenderType() {
		return renderID;
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
		super.addInformation(stack, player, list, ext);
		list.add(EnumChatFormatting.BLUE + "Does not drop blocks.");
	}
	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack itemStack) {
	if(!world.isRemote) {
			if(GeneralConfig.enableExtendedLogging) {
			MainRegistry.logger.log(Level.INFO, "[BOMBPL]" + this.getLocalizedName() + " placed at " + x + " / " + y + " / " + z + "! " + "by "+ player.getCommandSenderName());
		}
	}
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		if(world.isRemote) {
			return true;
		} else {

			TileEntityCharge charge = (TileEntityCharge) world.getTileEntity(x, y, z);

			if(!charge.started) {

				if(player.isSneaking()) {

					if(charge.timer > 0) {

						//todo defuser code here since this fucking mod is retarded and special needs because we need
						// 2 GODDAMN METHODS FOR THE EXACT SAME FUCKING BULLSHIT FOR SOME FUCKING REASON
						// I'm so fucking confused bro

						//if(player.getHeldItem() = ModItems.defuser)

						charge.started = true;
						world.playSoundEffect(x, y, z, "hbm:weapon.fstbmbStart", 1.0F, 1.0F);
					}
				} else {


					if(charge.timer == 600) { charge.timer = 1200; }

					world.playSoundEffect(x, y, z, "hbm:item.techBoop", 1.0F, 1.0F);
				}

				charge.markDirty();
			}

			return false;
		}
	}
}
