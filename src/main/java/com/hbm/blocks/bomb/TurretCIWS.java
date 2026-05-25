package com.hbm.blocks.bomb;

import java.util.List;
import java.util.Random;

import com.hbm.blocks.turret.TurretBase;
//import com.hbm.entity.particle.EntityGasFlameFX;
import com.hbm.lib.ModDamageSource;
import com.hbm.tileentity.bomb.TileEntityTurretCIWS;

import com.hbm.blocks.ITooltipProvider;

import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class TurretCIWS extends TurretBase implements ITooltipProvider {

	public TurretCIWS(Material mat) {
		super(mat);
	}
	Random rand = new Random();

	@Override
	public TileEntity createNewTileEntity(World p_149915_1_, int p_149915_2_) {
		return new TileEntityTurretCIWS();
	}

	@Override
	public boolean executeHoldAction(World world, int i, double yaw, double pitch, int x, int y, int z) {

		TileEntityTurretCIWS te = (TileEntityTurretCIWS) world.getTileEntity(x, y, z);

		// No power = no tracking, no rotation, no firing
		if(!te.hasPower()) {
			return false;
		}

		boolean flag = false;

		if(pitch < -60)
			pitch = -60;
		if(pitch > 30)
			pitch = 30;

		if(te.spin < 35)
			te.spin += 5;

		if(te.spin > 25 && i % 2 == 0) {

			// Double-check power before firing
			if(te.getPower() < TileEntityTurretCIWS.POWER_PER_SHOT) {
				return false;
			}

			Vec3 vector = Vec3.createVectorHelper(
				-Math.sin(yaw / 180.0F * (float)Math.PI) * Math.cos(pitch / 180.0F * (float)Math.PI),
				-Math.sin(pitch / 180.0F * (float)Math.PI),
				Math.cos(yaw / 180.0F * (float)Math.PI) * Math.cos(pitch / 180.0F * (float)Math.PI)
			);

			vector.normalize();

			if(!world.isRemote) {

				rayShot(
					world,
					vector,
					x + vector.xCoord * 2.5 + 0.5,
					y + vector.yCoord * 2.5 + 0.5,
					z + vector.zCoord * 2.5 + 0.5,
					100,
					40.0F,
					40
				);

				// Consume power per shot
				te.consumePower(TileEntityTurretCIWS.POWER_PER_SHOT);
			}

			world.playSoundEffect(
				x,
				y,
				z,
				"hbm:weapon.gun_m61a1_snd",
				5.0F,
				1.25F
			);

			flag = true;
		}

		return flag;
	}

	private void rayShot(World world, Vec3 vec, double posX, double posY, double posZ, int range, float damage, int hitPercent) {
		List<Entity> entities = world.getLoadedEntityList();


		for(float i = 0; i < range; i += 0.25F) {
			double pX = posX + vec.xCoord * i;
			double pY = posY + vec.yCoord * i;
			double pZ = posZ + vec.zCoord * i;

			if(world.getBlock((int)pX, (int)pY, (int)pZ).getMaterial() != Material.air)
				break;

			List<Entity> hit =
				world.getEntitiesWithinAABBExcludingEntity(
					null,
					AxisAlignedBB.getBoundingBox(
						pX - 0.125,
						pY - 0.125,
						pZ - 0.125,
						pX + 0.125,
						pY + 0.125,
						pZ + 0.125
					)
				);

			for(int j = 0; j < hit.size(); j++) {
				Entity ent = hit.get(j);


				if(rand.nextInt(100) < hitPercent) {
					ent.attackEntityFrom(ModDamageSource.shrapnel, 40.0F);
				}
			}
		}
	}

	@Override
	public void addInformation(
		ItemStack stack,
		EntityPlayer player,
		List list,
		boolean ext
	) {
		this.addStandardInfo(stack, player, list, ext);

		list.add("Requires power");
		list.add("Uses 20×102 mm ammunition");
		list.add("Intercepts incoming missiles");
	}

	@Override
	public void executeReleaseAction(World world, int i, double yaw, double pitch, int x, int y, int z) { }
}
