package com.hbm.particle;

import com.hbm.main.ModEventHandlerClient;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

/** Tiny saliva fleck: no contact callbacks, secondary effects or persistent residue. */
@SideOnly(Side.CLIENT)
public final class ParticleRabiesFroth extends EntityFX {
	public ParticleRabiesFroth(World world, double x, double y, double z, double vx, double vy, double vz, float size) {
		super(world, x, y, z);
		particleIcon = ModEventHandlerClient.particleBase;
		particleRed = particleGreen = particleBlue = 1F;
		particleScale = size * (0.10F + rand.nextFloat() * 0.05F);
		particleMaxAge = 8 + rand.nextInt(5);
		particleGravity = 0.12F;
		motionX = vx;
		motionY = vy;
		motionZ = vz;
		noClip = true;
	}

	@Override public void onUpdate() {
		super.onUpdate();
		particleAlpha = 0.9F * Math.min(1F, (particleMaxAge - particleAge) / 4F);
	}

	@Override public int getFXLayer() { return 1; }
}
