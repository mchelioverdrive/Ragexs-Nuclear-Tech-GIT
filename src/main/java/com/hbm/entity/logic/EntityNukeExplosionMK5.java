package com.hbm.entity.logic;

import java.util.List;

import org.apache.logging.log4j.Level;

import com.hbm.config.BombConfig;
import com.hbm.config.GeneralConfig;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.trait.CBT_Atmosphere;
import com.hbm.entity.effect.EntityFalloutRain;
import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.explosion.ExplosionNukeGeneric;
import com.hbm.explosion.ExplosionNukeRayBatched;
import com.hbm.explosion.nuclear.BurstType;
import com.hbm.explosion.nuclear.NuclearDetonationSpec;
import com.hbm.explosion.nuclear.NuclearEffectsProfile;
import com.hbm.explosion.nuclear.NuclearEffectsSolver;
import com.hbm.main.MainRegistry;
import com.hbm.util.ContaminationUtil;
import com.hbm.util.ContaminationUtil.ContaminationType;
import com.hbm.util.ContaminationUtil.HazardType;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * Incremental terrain processor and pressure-front controller for ordinary nukes.
 * Terrain work is deliberately independent from the shock front so server lag cannot
 * repeatedly damage entities or alter the apparent speed of the blast.
 */
public class EntityNukeExplosionMK5 extends EntityExplosionChunkloading {
	public int strength;
	public int speed;
	public int length;
	public boolean fallout = true;
	public boolean salted = false;
	private int falloutAdd;
	private double previousShockRadius;
	private double currentShockRadius;
	private boolean promptApplied;
	private boolean thermalApplied;
	private NuclearDetonationSpec spec;
	private NuclearEffectsProfile effects;
	ExplosionNukeRayBatched explosion;
	private NBTTagCompound pendingExplosionData;

	public EntityNukeExplosionMK5(World world) { super(world); }
	public EntityNukeExplosionMK5(World world, int strength, int speed, int length) {
		super(world); this.strength = strength; this.speed = speed; this.length = length;
	}

	@Override public void onUpdate() {
		if(strength == 0) { clearChunkLoader(); setDead(); return; }
		if(!worldObj.isRemote) loadChunk((int)Math.floor(posX / 16D), (int)Math.floor(posZ / 16D));
		for(Object player : worldObj.playerEntities) ((EntityPlayer)player).triggerAchievement(MainRegistry.achManhattan);
		ensureEffects();
		if(!worldObj.isRemote) {
			if(!thermalApplied) { applyThermalFlash(); thermalApplied = true; }
			if(!promptApplied) { applyPromptRadiation(); promptApplied = true; }
			advanceShockFront();
		}
		if(effects.craterRadius <= 0D) { if(currentShockRadius >= effects.lightBlastRadius) finishDetonation(); return; }
		if(explosion == null) { // Legacy spherical ray crater: replace with terrain-aware geometry in a later phase.
			explosion = new ExplosionNukeRayBatched(worldObj, (int)posX, (int)posY, (int)posZ, strength, speed, length); if(pendingExplosionData != null) { explosion.readFromNBT(pendingExplosionData); pendingExplosionData = null; } }
		if(!explosion.isAusf3Complete) explosion.collectTip(speed * 10);
		else if(!explosion.perChunk.isEmpty()) {
			long start = System.currentTimeMillis();
			while(!explosion.perChunk.isEmpty() && System.currentTimeMillis() < start + BombConfig.mk5) explosion.processChunk();
		} else finishDetonation();
	}

	private void ensureEffects() {
		if(spec == null) {
			spec = NuclearDetonationSpec.fromLegacyRadius(Math.max(1, length));
			spec.createsFallout = fallout; spec.salted = salted; classifyBurst(worldObj, spec, posX, posY, posZ);
		}
		if(effects == null) effects = NuclearEffectsSolver.solve(spec);
	}

	private static void classifyBurst(World world, NuclearDetonationSpec spec, double x, double y, double z) {
		CBT_Atmosphere atmosphere = CelestialBody.getTrait(world, CBT_Atmosphere.class);
		if(CelestialBody.inOrbit(world) || atmosphere == null || atmosphere.getPressure() < 0.01D) { spec.burstType = BurstType.VACUUM; spec.burstHeight = 0D; spec.groundCoupling = 0D; return; }
		if(world.getBlock((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z)).getMaterial().isLiquid()) { spec.burstType = BurstType.UNDERWATER; spec.burstHeight = 0D; spec.groundCoupling = 1D; return; }
		double surfaceY = world.getHeightValue((int)Math.floor(x), (int)Math.floor(z));
		spec.burstHeight = y - surfaceY;
		if(y < surfaceY - 3D) { spec.burstType = BurstType.SUBSURFACE; spec.groundCoupling = 1D; return; }
		NuclearEffectsProfile preliminary = NuclearEffectsSolver.solve(spec);
		double fireballBottom = y - preliminary.fireballRadius;
		if(fireballBottom > surfaceY) { spec.burstType = BurstType.AIR; spec.groundCoupling = 0D; }
		else { double intersectionDepth = surfaceY - fireballBottom; spec.groundCoupling = Math.max(0D, Math.min(1D, intersectionDepth / Math.max(1D, preliminary.fireballRadius))); spec.burstType = spec.groundCoupling < 0.15D ? BurstType.AIR : BurstType.SURFACE; }
	}

	private void advanceShockFront() {
		if(effects.lightBlastRadius <= 0D) return;
		previousShockRadius = currentShockRadius;
		currentShockRadius = Math.min(effects.lightBlastRadius, currentShockRadius + Math.max(4D, effects.lightBlastRadius / 20D));
		ExplosionNukeGeneric.dealDamageFront(worldObj, posX, posY, posZ, previousShockRadius, currentShockRadius, effects.moderateBlastRadius, 125F);
	}

	private void applyThermalFlash() {
		if(effects.thermalRadius <= 0D || spec.burstType == BurstType.UNDERWATER || spec.burstType == BurstType.VACUUM) return;
		List<EntityLivingBase> entities = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, AxisAlignedBB.getBoundingBox(posX, posY, posZ, posX, posY, posZ).expand(effects.thermalRadius, effects.thermalRadius, effects.thermalRadius));
		for(EntityLivingBase entity : entities) {
			double dx = entity.posX - posX, dy = entity.posY + entity.getEyeHeight() - posY, dz = entity.posZ - posZ;
			double distanceSq = Math.max(1D, dx * dx + dy * dy + dz * dz);
			if(distanceSq > effects.thermalRadius * effects.thermalRadius || worldObj.rayTraceBlocks(Vec3.createVectorHelper(posX, posY, posZ), Vec3.createVectorHelper(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ)) != null) continue;
			double fluence = spec.yieldKt * spec.thermalFraction * 50D / distanceSq;
			if(fluence > 1D) entity.attackEntityFrom(com.hbm.lib.ModDamageSource.nuclearBlast, (float)Math.min(20D, fluence));
			if(fluence > 8D) entity.setFire((int)Math.min(10D, fluence / 2D));
		}
	}

	private void applyPromptRadiation() {
		if(effects.promptRadiationRadius <= 0D || spec.burstType == BurstType.VACUUM) return;
		radiate((float)(2500000F * spec.fissionFraction), effects.promptRadiationRadius);
	}
	private void radiate(float rads, double range) {
		List<EntityLivingBase> entities = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, AxisAlignedBB.getBoundingBox(posX, posY, posZ, posX, posY, posZ).expand(range, range, range));
		for(EntityLivingBase e : entities) {
			Vec3 vec = Vec3.createVectorHelper(e.posX - posX, e.posY + e.getEyeHeight() - posY, e.posZ - posZ);
			double len = vec.lengthVector(); if(len < 1D || len > range) continue; vec = vec.normalize();
			float attenuation = 1F;
			for(int i = 1; i < len; i++) { if(worldObj.getBlock((int)Math.floor(posX + vec.xCoord * i), (int)Math.floor(posY + vec.yCoord * i), (int)Math.floor(posZ + vec.zCoord * i)) != Blocks.air) attenuation += 2F; }
			ContaminationUtil.contaminate(e, HazardType.RADIATION, ContaminationType.RAD_BYPASS, rads / attenuation / (float)(len * len));
		}
	}

	private void finishDetonation() {
		if(fallout && effects.falloutSourceStrength > 0D && spec.burstType != BurstType.VACUUM) {
			EntityFalloutRain rain = new EntityFalloutRain(worldObj);
			rain.setPosition(posX, posY, posZ); rain.setSalted(salted);
			double normalizedFallout = Math.max(0D, Math.min(1D, effects.falloutSourceStrength / Math.max(0.001D, spec.yieldKt * spec.fissionFraction)));
			rain.setFalloutStrength(normalizedFallout);
			rain.setScale(Math.max(1, (int)((length * 2.5D + falloutAdd) * normalizedFallout * BombConfig.falloutRange / 100)));
			worldObj.spawnEntityInWorld(rain);
		}
		clearChunkLoader(); setDead();
	}

	@Override protected void readEntityFromNBT(NBTTagCompound nbt) {
		ticksExisted = nbt.getInteger("ticksExisted"); strength = nbt.getInteger("strength"); speed = nbt.getInteger("speed"); length = nbt.getInteger("length");
		fallout = nbt.getBoolean("fallout"); salted = nbt.getBoolean("salted"); falloutAdd = nbt.getInteger("falloutAdd"); previousShockRadius = nbt.getDouble("previousShock"); currentShockRadius = nbt.getDouble("currentShock"); promptApplied = nbt.getBoolean("promptApplied"); thermalApplied = nbt.getBoolean("thermalApplied");
		spec = NuclearDetonationSpec.fromLegacyRadius(Math.max(1, length)); if(nbt.hasKey("yieldKt")) spec.yieldKt = nbt.getDouble("yieldKt"); spec.fissionFraction = nbt.getDouble("fissionFraction"); spec.burstHeight = nbt.getDouble("burstHeight"); spec.groundCoupling = nbt.getDouble("groundCoupling"); spec.thermalFraction = nbt.getDouble("thermalFraction"); spec.promptGammaFraction = nbt.getDouble("gammaFraction"); spec.promptNeutronFraction = nbt.getDouble("neutronFraction"); spec.createsFallout = fallout; spec.createsEMP = nbt.getBoolean("emp"); spec.salted = salted;
		try { spec.burstType = BurstType.valueOf(nbt.getString("burstType")); } catch(IllegalArgumentException ex) { spec.burstType = BurstType.SURFACE; }
		effects = NuclearEffectsSolver.solve(spec);
		if(nbt.hasKey("terrainWork")) pendingExplosionData = nbt.getCompoundTag("terrainWork");
	}
	@Override protected void writeEntityToNBT(NBTTagCompound nbt) {
		ensureEffects(); nbt.setInteger("ticksExisted", ticksExisted); nbt.setInteger("strength", strength); nbt.setInteger("speed", speed); nbt.setInteger("length", length); nbt.setBoolean("fallout", fallout); nbt.setBoolean("salted", salted); nbt.setInteger("falloutAdd", falloutAdd); nbt.setDouble("previousShock", previousShockRadius); nbt.setDouble("currentShock", currentShockRadius); nbt.setBoolean("promptApplied", promptApplied); nbt.setBoolean("thermalApplied", thermalApplied);
		if(explosion != null) { NBTTagCompound terrainWork = new NBTTagCompound(); explosion.writeToNBT(terrainWork); nbt.setTag("terrainWork", terrainWork); }
		nbt.setDouble("yieldKt", spec.yieldKt); nbt.setDouble("fissionFraction", spec.fissionFraction); nbt.setString("burstType", spec.burstType.name()); nbt.setDouble("burstHeight", spec.burstHeight); nbt.setDouble("groundCoupling", spec.groundCoupling); nbt.setDouble("thermalFraction", spec.thermalFraction); nbt.setDouble("gammaFraction", spec.promptGammaFraction); nbt.setDouble("neutronFraction", spec.promptNeutronFraction); nbt.setBoolean("emp", spec.createsEMP);
	}

	public static EntityNukeExplosionMK5 statFac(World world, int r, double x, double y, double z) {
		if(GeneralConfig.enableExtendedLogging && !world.isRemote) MainRegistry.logger.log(Level.INFO, "[NUKE] Initialized explosion at " + x + " / " + y + " / " + z + " with legacy radius " + r + "!");
		if(r == 0) r = 25;
		EntityNukeExplosionMK5 mk5 = new EntityNukeExplosionMK5(world); mk5.length = Math.max(1, r); mk5.spec = NuclearDetonationSpec.fromLegacyRadius(mk5.length); classifyBurst(world, mk5.spec, x, y, z); mk5.effects = NuclearEffectsSolver.solve(mk5.spec);
		mk5.length = Math.max(1, (int)Math.ceil(mk5.effects.craterRadius)); mk5.strength = Math.max(1, (int)Math.ceil(mk5.effects.craterRadius * 2D)); mk5.speed = Math.max(1, (int)Math.ceil(100000D / mk5.strength)); mk5.setPosition(x, y, z);
		EntityNukeTorex.statFac(world, x, y, z, mk5.spec.burstType, mk5.effects.fireballRadius, mk5.effects.visualScale, mk5.effects.cloudTopHeight, mk5.spec.groundCoupling, mk5.spec.burstHeight); return mk5;
	}
	public static EntityNukeExplosionMK5 statFacNoRad(World world, int r, double x, double y, double z) { EntityNukeExplosionMK5 mk5 = statFac(world, r, x, y, z); mk5.fallout = false; mk5.spec.createsFallout = false; return mk5; }
	public static EntityNukeExplosionMK5 statFacSalted(World world, int r, double x, double y, double z) { EntityNukeExplosionMK5 mk5 = statFac(world, r, x, y, z); mk5.salted = true; mk5.spec.salted = true; return mk5; }
	public EntityNukeExplosionMK5 moreFallout(int fallout) { falloutAdd = fallout; return this; }
}
