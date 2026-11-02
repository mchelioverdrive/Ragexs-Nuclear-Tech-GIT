package com.hbm.handler.disease;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/** Explicit saliva-bearing bite, with ordinary mob damage/protection semantics. Not generic mammal melee. */
public final class BiteDamageSource extends EntityDamageSource {
	public BiteDamageSource(EntityLivingBase source) { super("mob", source); }

	/** The attack and wound event use the same body-to-body reach and unobstructed contact test. */
	static boolean hasContact(EntityLivingBase source, EntityLivingBase victim) {
		if(source.worldObj != victim.worldObj) return false;
		AxisAlignedBB a = source.boundingBox, b = victim.boundingBox;
		double sx = MathHelper.clamp_double((b.minX + b.maxX) / 2D, a.minX, a.maxX);
		double sy = MathHelper.clamp_double((b.minY + b.maxY) / 2D, a.minY, a.maxY);
		double sz = MathHelper.clamp_double((b.minZ + b.maxZ) / 2D, a.minZ, a.maxZ);
		double tx = MathHelper.clamp_double(sx, b.minX, b.maxX);
		double ty = MathHelper.clamp_double(sy, b.minY, b.maxY);
		double tz = MathHelper.clamp_double(sz, b.minZ, b.maxZ);
		double dx = tx - sx, dy = ty - sy, dz = tz - sz;
		double reach = source instanceof EntityBat ? 0.45D : 0.6D;
		if(dx * dx + dy * dy + dz * dz > reach * reach) return false;
		if(!RabiesBehavior.chunksLoaded(source, Math.min(a.minX, b.minX) - 1D, Math.min(a.minZ, b.minZ) - 1D,
				Math.max(a.maxX, b.maxX) + 1D, Math.max(a.maxZ, b.maxZ) + 1D)) return false;
		Vec3 start = Vec3.createVectorHelper(sx, sy, sz), end = Vec3.createVectorHelper(tx, ty, tz);
		AxisAlignedBB contact = AxisAlignedBB.getBoundingBox(Math.min(sx, tx), Math.min(sy, ty), Math.min(sz, tz),
				Math.max(sx, tx), Math.max(sy, ty), Math.max(sz, tz)).expand(0.001D, 0.001D, 0.001D);
		// A selection ray can miss panes when both endpoints lie inside their selection box.
		// Use native physical block shapes, including panes/fences and segments starting inside a shape.
		for(Object collision : source.worldObj.func_147461_a(contact)) {
			AxisAlignedBB box = (AxisAlignedBB) collision;
			if(box.isVecInside(start) || box.isVecInside(end) || box.calculateIntercept(start, end) != null) return false;
		}
		return true;
	}
}
