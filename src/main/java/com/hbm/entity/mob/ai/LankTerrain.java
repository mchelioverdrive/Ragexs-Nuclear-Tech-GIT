package com.hbm.entity.mob.ai;

import com.hbm.entity.mob.EntityFRIEND;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;

public final class LankTerrain {

	private static final int OBSERVER_RANGE = 144; // Beyond the 128-block tracker, with camera margin.
	private static final double FRONT_CONE = 0.25D;

	private LankTerrain() { }

	public static boolean isObserver(EntityPlayer player) {
		return player instanceof EntityPlayerMP && !(player instanceof FakePlayer) && player.isEntityAlive();
	}

	public static boolean isTarget(EntityPlayer player) {
		return isObserver(player) && !player.isPlayerSleeping();
	}

	public static boolean loadedBetween(World world, int x, int z, int otherX, int otherZ, int margin) {
		return world.checkChunksExist(Math.min(x, otherX) - margin, 0, Math.min(z, otherZ) - margin,
				Math.max(x, otherX) + margin, 0, Math.max(z, otherZ) + margin);
	}

	public static boolean canUseNavigator(EntityFRIEND lank) {
		int radius = MathHelper.ceiling_float_int(lank.getNavigator().getPathSearchRange()) + 16;
		int x = MathHelper.floor_double(lank.posX), z = MathHelper.floor_double(lank.posZ);
		return loadedBetween(lank.worldObj, x, z, x, z, radius);
	}

	public static boolean canStand(EntityFRIEND lank, int x, int y, int z) {
		World world = lank.worldObj;
		if(y < 1 || y + lank.height >= world.getActualHeight() || !loadedBetween(world, x, z, x, z, 1)
				|| !world.isSideSolid(x, y - 1, z, ForgeDirection.UP)) return false;
		double halfWidth = lank.width / 2D;
		AxisAlignedBB body = AxisAlignedBB.getBoundingBox(x + 0.5D - halfWidth, y, z + 0.5D - halfWidth,
				x + 0.5D + halfWidth, y + lank.height, z + 0.5D + halfWidth);
		return world.func_147461_a(body).isEmpty() && !world.isAnyLiquid(body);
	}

	/** Try nearby ledges first so that caves and excavated spaces remain eligible. */
	public static ChunkCoordinates findFooting(EntityFRIEND lank, int x, int nearY, int z, int verticalRange, boolean surface) {
		World world = lank.worldObj;
		if(!loadedBetween(world, x, z, x, z, 1)) return null;
		for(int dy = 0; dy <= verticalRange; dy++) {
			if(canStand(lank, x, nearY - dy, z)) return new ChunkCoordinates(x, nearY - dy, z);
			if(dy > 0 && canStand(lank, x, nearY + dy, z)) return new ChunkCoordinates(x, nearY + dy, z);
		}
		if(!surface) return null;
		int y = world.getTopSolidOrLiquidBlock(x, z);
		return canStand(lank, x, y, z) ? new ChunkCoordinates(x, y, z) : null;
	}

	public static double lookDot(EntityPlayer player, double x, double y, double z) {
		Vec3 look = player.getLookVec();
		Vec3 direction = Vec3.createVectorHelper(x - player.posX, y - eyeY(player), z - player.posZ);
		return direction.lengthVector() < 0.01D ? 1D : look.dotProduct(direction.normalize());
	}

	public static boolean hiddenFromObservers(World world, double x, double y, double z, float height, boolean spawning) {
		for(Object entry : world.playerEntities) {
			EntityPlayer player = (EntityPlayer) entry;
			if(!isObserver(player) || Math.abs(player.posX - x) > OBSERVER_RANGE || Math.abs(player.posZ - z) > OBSERVER_RANGE) continue;
			if(spawning && player.getDistanceSq(x, y, z) < 16D * 16D) return false;
			if(spawning && lookDot(player, x, y + height * 0.5D, z) < FRONT_CONE
					&& lookDot(player, x, y + 0.2D, z) < FRONT_CONE
					&& lookDot(player, x, y + height, z) < FRONT_CONE) continue;
			if(!occluded(player, x, y + 0.2D, z) || !occluded(player, x, y + height * 0.5D, z)
					|| !occluded(player, x, y + height, z)) return false;
			// A wall edge can cover the center while leaving a shoulder visible.
			double dx = player.posX - x, dz = player.posZ - z;
			double length = Math.sqrt(dx * dx + dz * dz);
			if(length > 0.01D) {
				double sideX = dz / length * 0.3D, sideZ = dx / length * 0.3D;
				if(!occluded(player, x + sideX, y + height * 0.7D, z - sideZ)
						|| !occluded(player, x - sideX, y + height * 0.7D, z + sideZ)) return false;
			}
		}
		return true;
	}

	public static boolean occluded(EntityPlayer player, double x, double y, double z) {
		World world = player.worldObj;
		if(!loadedBetween(world, MathHelper.floor_double(player.posX), MathHelper.floor_double(player.posZ),
				MathHelper.floor_double(x), MathHelper.floor_double(z), 0)) return false;
		// The vanilla ray tracer has a step limit. Treat a potentially truncated ray as visible.
		double eyeY = eyeY(player);
		if(Math.abs(player.posX - x) + Math.abs(eyeY - y) + Math.abs(player.posZ - z) > 190D) return false;
		MovingObjectPosition hit = world.func_147447_a(Vec3.createVectorHelper(player.posX, eyeY, player.posZ),
				Vec3.createVectorHelper(x, y, z), false, true, false);
		return hit != null && world.getBlock(hit.blockX, hit.blockY, hit.blockZ).isOpaqueCube();
	}

	private static double eyeY(EntityPlayer player) {
		return player.posY + player.getEyeHeight();
	}
}
