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

		// thick atmospheric drag
		entity.motionX *= 0.95D;
		entity.motionY *= 0.97D;
		entity.motionZ *= 0.95D;

		// softer wind shear
		entity.motionX +=
			(world.rand.nextDouble() - 0.5D)
				* 0.22D;

		entity.motionZ +=
			(world.rand.nextDouble() - 0.5D)
				* 0.22D;

		// slight buoyancy/turbulence
		entity.motionY +=
			(world.rand.nextDouble() - 0.45D)
				* 0.08D;

		entity.fallDistance =
			0F;

		// rare strong gust
		if(world.rand.nextInt(120) == 0) {

			entity.motionX +=
				(world.rand.nextDouble() - 0.5D)
					* 1.4D;

			entity.motionY +=
				world.rand.nextDouble()
					* 0.4D;

			entity.motionZ +=
				(world.rand.nextDouble() - 0.5D)
					* 1.4D;
		}

		// freezing damage
		if(entity instanceof EntityLivingBase
			&& world.rand.nextInt(80) == 0) {

			((EntityLivingBase)entity)
				.attackEntityFrom(
					DamageSource.drown, //freezing
					1.0F
				);
		}

		// extremely rare electrical discharge
		if(entity instanceof EntityLivingBase
			&& world.rand.nextInt(250) == 0) {

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
