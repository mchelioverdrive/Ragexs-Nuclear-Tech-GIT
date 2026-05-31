package com.hbm.blocks.gas;

import java.util.Random;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class BlockSaturnStorm extends BlockGasBase {

	public BlockSaturnStorm() {

		// pale yellow-beige
		super(
			0.90F,
			0.84F,
			0.62F
		);

		this.setBlockName(
			"saturn_storm"
		);

		this.setBlockTextureName(
			"hbm:saturn_storm"
		);
	}

	@Override
	public boolean isStaticGas() {
		return true;
	}

	@Override
	public ForgeDirection getFirstDirection(
		World world,
		int x,
		int y,
		int z
	) {

		// calmer atmospheric flow
		return randomHorizontal(
			world
		);
	}

	@Override
	public ForgeDirection getSecondDirection(
		World world,
		int x,
		int y,
		int z
	) {

		// slight vertical instability
		//SHOULD BE EXTREME 1,100 MPH WINDS.
		if(world.rand.nextInt(5) == 0) {

			return world.rand.nextBoolean()
				? ForgeDirection.UP
				: ForgeDirection.DOWN;
		}

		return randomHorizontal(
			world
		);
	}

	@Override
	public int getDelay(World world) {

		// slower movement than Jupiter
		return 2;
	}

	@Override
	public void onEntityCollidedWithBlock(
		World world,
		int x,
		int y,
		int z,
		Entity entity
	) {

		// stronger atmospheric drag (denser gas = harder to correct motion)
		entity.motionX *= 0.96D;
		entity.motionY *= 0.985D;
		entity.motionZ *= 0.96D;

		// ==============================
		// SATURN JET STREAM FORCE
		// consistent high-speed push
		// ==============================
		double windX =
			(world.rand.nextDouble() - 0.5D) * 1.2D;

		double windZ =
			(world.rand.nextDouble() - 0.5D) * 1.2D;

		// apply stronger but smoother horizontal flow
		entity.motionX += windX * 0.35D;
		entity.motionZ += windZ * 0.35D;

		// very mild vertical shear
		entity.motionY +=
			(world.rand.nextDouble() - 0.5D)
				* 0.04D;

		entity.fallDistance = 0F;

		// ==============================
		// RARE STORM BURST (NOT COMMON)
		// ==============================
		if(world.rand.nextInt(180) == 0) {

			entity.motionX += windX * 1.5D;
			entity.motionZ += windZ * 1.5D;
			entity.motionY += 0.3D;
		}

		double windAngle = Math.sin((x * 0.01) + (z * 0.01));
		entity.motionX += Math.cos(windAngle) * 5;
		entity.motionZ += Math.sin(windAngle) * 5;

		// ==============================
		// LIGHT DAMAGE = cold + pressure
		// ==============================
		if(entity instanceof EntityLivingBase
			&& world.rand.nextInt(120) == 0) {

			((EntityLivingBase)entity)
				.attackEntityFrom(
					DamageSource.drown,
					1.0F
				);
		}

		// extremely rare electrical discharge
		if(entity instanceof EntityLivingBase
			&& world.rand.nextInt(300) == 0) {

			((EntityLivingBase)entity)
				.attackEntityFrom(
					DamageSource.magic,
					1.0F
				);
		}
	}

	@Override
	public void randomDisplayTick(
		World world,
		int x,
		int y,
		int z,
		Random rand
	) {

		super.randomDisplayTick(
			world,
			x,
			y,
			z,
			rand
		);

		EntityPlayer p =
			world.getClosestPlayer(
				x + 0.5,
				y + 0.5,
				z + 0.5,
				16
			);

		if(p == null)
			return;

		// pale atmospheric haze
		if(rand.nextInt(60) == 0) {

			world.spawnParticle(
				"cloud",
				x + rand.nextDouble(),
				y + rand.nextDouble(),
				z + rand.nextDouble(),
				0.0D,
				0.005D,
				0.0D
			);
		}

		// occasional faint static spark
		if(rand.nextInt(240) == 0) {

			world.spawnParticle(
				"reddust",
				x + rand.nextDouble(),
				y + rand.nextDouble(),
				z + rand.nextDouble(),
				0.0D,
				0.0D,
				0.0D
			);
		}
	}
}
