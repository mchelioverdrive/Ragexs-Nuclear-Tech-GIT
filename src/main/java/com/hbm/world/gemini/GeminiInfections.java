package com.hbm.world.gemini;

import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.IDeepRootCarrier;
import com.hbm.handler.disease.DeepRootDisease;
import com.hbm.handler.disease.DiseaseHost;
import com.hbm.handler.disease.DiseaseManager;
import com.hbm.handler.disease.Infection;

import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/** Shared server-thread infection boundary for Gemini creatures and future environmental producers. */
public final class GeminiInfections {
	private GeminiInfections() { }

	/** Deterministic exposure for an already validated interaction; the canonical pathogen rechecks the Mars gate. */
	public static boolean expose(EntityLivingBase victim, String source, String sourceHost) {
		return loaded(victim) && DiseaseManager.infect(victim, DiseaseManager.pathogen(DeepRootDisease.ID), source, sourceHost);
	}

	/**
	 * Call once per confirmed, uncanceled wound whose contact was validated at hit time.
	 * Never call for proximity or a pre-armor hurt event alone. Knockback may separate the pair before confirmation.
	 */
	public static boolean transmitWound(EntityLivingBase carrier, EntityLivingBase victim) {
		if(!(carrier instanceof IDeepRootCarrier) || !carrier.isEntityAlive() || !loaded(carrier)
				|| carrier == victim || carrier.worldObj != victim.worldObj || !loaded(victim) || !DeepRootDisease.active(victim)
				|| DiseaseHost.get(victim).infections.containsKey(DeepRootDisease.ID)) return false;
		float chance = victim instanceof EntityPlayer ? GeminiConfig.deepRootPlayerTransmissionChance : GeminiConfig.deepRootMobTransmissionChance;
		return victim.getRNG().nextFloat() < chance
				&& expose(victim, "wound:" + carrier.getUniqueID(), EntityList.getEntityString(carrier));
	}

	/** Bounded contact validation for native carrier attack reach. Never loads terrain. */
	public static boolean hasContact(EntityLivingBase carrier, EntityLivingBase victim) {
		if(carrier == victim || carrier.worldObj != victim.worldObj || carrier.worldObj.isRemote) return false;
		AxisAlignedBB a = carrier.boundingBox, b = victim.boundingBox;
		double sx = MathHelper.clamp_double((b.minX + b.maxX) * 0.5D, a.minX, a.maxX);
		double sy = MathHelper.clamp_double((b.minY + b.maxY) * 0.5D, a.minY, a.maxY);
		double sz = MathHelper.clamp_double((b.minZ + b.maxZ) * 0.5D, a.minZ, a.maxZ);
		double tx = MathHelper.clamp_double(sx, b.minX, b.maxX);
		double ty = MathHelper.clamp_double(sy, b.minY, b.maxY);
		double tz = MathHelper.clamp_double(sz, b.minZ, b.maxZ);
		double dx = tx - sx, dy = ty - sy, dz = tz - sz;
		if(dx * dx + dy * dy + dz * dz > 1.25D * 1.25D) return false;
		for(int x = MathHelper.floor_double(Math.min(sx, tx) - 1D) >> 4; x <= MathHelper.floor_double(Math.max(sx, tx) + 1D) >> 4; x++)
			for(int z = MathHelper.floor_double(Math.min(sz, tz) - 1D) >> 4; z <= MathHelper.floor_double(Math.max(sz, tz) + 1D) >> 4; z++)
				if(!carrier.worldObj.getChunkProvider().chunkExists(x, z)) return false;
		Vec3 from = Vec3.createVectorHelper(sx, sy, sz), to = Vec3.createVectorHelper(tx, ty, tz);
		AxisAlignedBB contact = AxisAlignedBB.getBoundingBox(Math.min(sx, tx), Math.min(sy, ty), Math.min(sz, tz),
				Math.max(sx, tx), Math.max(sy, ty), Math.max(sz, tz)).expand(0.001D, 0.001D, 0.001D);
		for(Object collision : carrier.worldObj.func_147461_a(contact)) {
			AxisAlignedBB box = (AxisAlignedBB) collision;
			if(box.isVecInside(from) || box.isVecInside(to) || box.calculateIntercept(from, to) != null) return false;
		}
		return true;
	}

	/** Eligibility only; MockerySpawning owns checked replacement from the loaded-host callback. */
	public static boolean isConversionEligible(EntityLivingBase victim) {
		if(victim instanceof EntityPlayer || !loaded(victim) || !DeepRootDisease.active(victim)) return false;
		Infection infection = DiseaseHost.get(victim).infections.get(DeepRootDisease.ID);
		return infection != null && DeepRootDisease.stage(infection) == DeepRootDisease.Stage.TERMINAL;
	}

	private static boolean loaded(EntityLivingBase host) {
		return !host.worldObj.isRemote && host.addedToChunk
				&& host.worldObj.getChunkProvider().chunkExists(host.chunkCoordX, host.chunkCoordZ);
	}
}
