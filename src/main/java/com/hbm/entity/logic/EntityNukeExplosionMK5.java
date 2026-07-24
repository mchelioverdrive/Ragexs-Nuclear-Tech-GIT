package com.hbm.entity.logic;

import java.util.List;

import org.apache.logging.log4j.Level;

import com.hbm.config.BombConfig;
import com.hbm.config.GeneralConfig;
import com.hbm.entity.effect.EntityFalloutRain;
import com.hbm.explosion.ExplosionNukeGeneric;
import com.hbm.explosion.ExplosionNukeRayBatched;
import com.hbm.explosion.nuclear.BurstType;
import com.hbm.explosion.nuclear.NuclearBurstContext;
import com.hbm.explosion.nuclear.NuclearBurstResolver;
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
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
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
	private NuclearBurstContext burstContext;
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
			if(!thermalApplied) { applyThermalFlash(); applyThermalGroundIgnition(); thermalApplied = true; }
			if(!promptApplied) { applyPromptRadiation(); promptApplied = true; }
			advanceShockFront();
		}
		if(effects.craterRadius > 0D) {
			if(explosion == null) { explosion = new ExplosionNukeRayBatched(worldObj, (int)posX, (int)posY, (int)posZ, strength, speed, length); if(pendingExplosionData != null) { explosion.readFromNBT(pendingExplosionData); pendingExplosionData = null; } }
			if(!explosion.isAusf3Complete) explosion.collectTip(speed * 10);
			else if(!explosion.perChunk.isEmpty()) { long start = System.currentTimeMillis(); while(!explosion.perChunk.isEmpty() && System.currentTimeMillis() < start + BombConfig.mk5) explosion.processChunk(); }
			else if(currentShockRadius >= effects.lightBlastRadius) finishDetonation();
		} else if(currentShockRadius >= effects.lightBlastRadius) finishDetonation();
	}

	private void ensureEffects() {
		if(spec == null) {
			burstContext = NuclearBurstResolver.resolve(worldObj, posX, posY, posZ, Math.max(1, length));
			spec = NuclearDetonationSpec.fromLegacyRadius(burstContext.legacyRadius);
			spec.createsFallout = fallout; spec.salted = salted; spec.burstType = burstContext.burstType; spec.burstHeight = burstContext.burstHeight; spec.groundCoupling = burstContext.groundCoupling;
		}
		if(effects == null) effects = NuclearEffectsSolver.solve(spec);
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
			if(distanceSq <= effects.fireballRadius * effects.fireballRadius) {
				entity.attackEntityFrom(com.hbm.lib.ModDamageSource.nuclearBlast, 1000F);
			} else if(fluence > 1D) {
				entity.attackEntityFrom(com.hbm.lib.ModDamageSource.nuclearBlast, (float)Math.min(100D, fluence));
			}
			// A clear line of sight is an exposed target. An airburst's thermal pulse
			// ignites every such target, rather than only targets past an arbitrary fluence cutoff.
			if(spec.burstType == BurstType.AIR) entity.setFire(20);
			else if(fluence > 8D) entity.setFire((int)Math.min(20D, fluence / 2D));
			if(fluence > 0.5D) entity.addPotionEffect(new PotionEffect(Potion.blindness.id, (int)Math.min(20 * 30, 20D + fluence * 20D), 0));
		}
	}

	/** Ignites exposed flammables without creating a crater or terrain-ray workload. */
	private void applyThermalGroundIgnition() {
		if(spec.burstType == BurstType.UNDERWATER || spec.burstType == BurstType.VACUUM || effects.thermalRadius <= 0D) return;
		// Airbursts have no crater pass to spread secondary fires, so sample enough
		// exposed surface positions to create a dense thermal ignition footprint.
		int samples = Math.min(16384, Math.max(1024, (int)Math.ceil(effects.thermalRadius * 96D)));
		for(int i = 0; i < samples; i++) {
			double distance = effects.thermalRadius * Math.sqrt(worldObj.rand.nextDouble());
			double angle = worldObj.rand.nextDouble() * Math.PI * 2D;
			int x = (int)Math.floor(posX + Math.cos(angle) * distance);
			int z = (int)Math.floor(posZ + Math.sin(angle) * distance);
			int y = worldObj.getHeightValue(x, z) - 1;
			if(y < 0 || !worldObj.isAirBlock(x, y + 1, z)) continue;
			if(worldObj.getBlock(x, y, z).isFlammable(worldObj, x, y, z, net.minecraftforge.common.util.ForgeDirection.UP)) worldObj.setBlock(x, y + 1, z, Blocks.fire, 0, 3);
		}
	}

	private void applyPromptRadiation() {
		if(effects.promptRadiationRadius <= 0D) return;
		// Apply both prompt gamma dose and neutron activation. This happens independently
		// of terrain processing, so a clean airburst cannot lose its initial radiation.
		radiate(HazardType.RADIATION, (float)(2000000F * spec.fissionFraction * spec.promptGammaFraction / 0.05D), effects.promptRadiationRadius);
		radiate(HazardType.NEUTRON, (float)(5000F * spec.fissionFraction * spec.promptNeutronFraction / 0.02D), effects.promptRadiationRadius);
	}
	private void radiate(HazardType hazard, float rads, double range) {
		List<EntityLivingBase> entities = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, AxisAlignedBB.getBoundingBox(posX, posY, posZ, posX, posY, posZ).expand(range, range, range));
		for(EntityLivingBase e : entities) {
			Vec3 vec = Vec3.createVectorHelper(e.posX - posX, e.posY + e.getEyeHeight() - posY, e.posZ - posZ);
			double len = vec.lengthVector(); if(len > range) continue;
			// Do not skip entities at the hypocenter: they still receive prompt radiation
			// even though thermal damage will normally kill them first.
			if(len < 1D) len = 1D; else vec = vec.normalize();
			float attenuation = 1F;
			for(int i = 1; i < len; i++) { if(worldObj.getBlock((int)Math.floor(posX + vec.xCoord * i), (int)Math.floor(posY + vec.yCoord * i), (int)Math.floor(posZ + vec.zCoord * i)) != Blocks.air) attenuation += 2F; }
			ContaminationUtil.contaminate(e, hazard, ContaminationType.RAD_BYPASS, rads / attenuation / (float)(len * len));
		}
	}

	private void finishDetonation() {
		if(fallout && effects.falloutSourceStrength > 0D && spec.burstType != BurstType.VACUUM) {
			EntityFalloutRain rain = new EntityFalloutRain(worldObj);
			rain.setPosition(posX, posY, posZ); rain.setSalted(salted);
			rain.setSourceMultiplier(effects.falloutSourceStrength / Math.max(0.001D, spec.yieldKt * spec.fissionFraction));
			rain.setScale((int)((length * 2.5D + falloutAdd) * BombConfig.falloutRange / 100D * rain.getSourceMultiplier()));
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
		NuclearBurstContext context = NuclearBurstResolver.resolve(world, x, y, z, Math.max(1, r));
		EntityNukeExplosionMK5 mk5 = new EntityNukeExplosionMK5(world); mk5.length = context.legacyRadius; mk5.burstContext = context; mk5.spec = NuclearDetonationSpec.fromLegacyRadius(context.legacyRadius); mk5.spec.burstType = context.burstType; mk5.spec.burstHeight = context.burstHeight; mk5.spec.groundCoupling = context.groundCoupling; mk5.effects = NuclearEffectsSolver.solve(mk5.spec);
		mk5.strength = Math.max(1, (int)Math.ceil(mk5.effects.craterRadius * 2D)); mk5.speed = Math.max(1, (int)Math.ceil(100000D / mk5.strength)); mk5.setPosition(x, y, z); return mk5;
	}
	public static EntityNukeExplosionMK5 statFacNoRad(World world, int r, double x, double y, double z) { EntityNukeExplosionMK5 mk5 = statFac(world, r, x, y, z); mk5.fallout = false; mk5.spec.createsFallout = false; return mk5; }
	public static EntityNukeExplosionMK5 statFacSalted(World world, int r, double x, double y, double z) { EntityNukeExplosionMK5 mk5 = statFac(world, r, x, y, z); mk5.salted = true; mk5.spec.salted = true; return mk5; }
	public EntityNukeExplosionMK5 moreFallout(int fallout) { falloutAdd = fallout; return this; }
}
