package com.hbm.entity.logic;

import java.util.List;

import org.apache.logging.log4j.Level;

import com.hbm.config.BombConfig;
import com.hbm.config.GeneralConfig;
import com.hbm.entity.effect.EntityFalloutRain;
import com.hbm.explosion.ExplosionNukeGeneric;
import com.hbm.explosion.ExplosionNukeRayBatched;
import com.hbm.explosion.ExplosionWorkScheduler;
import com.hbm.explosion.nuclear.BurstType;
import com.hbm.explosion.nuclear.NuclearBurstContext;
import com.hbm.explosion.nuclear.NuclearBurstResolver;
import com.hbm.explosion.nuclear.NuclearDetonationSpec;
import com.hbm.explosion.nuclear.NuclearEffectsProfile;
import com.hbm.explosion.nuclear.NuclearEffectsSolver;
import com.hbm.main.MainRegistry;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.NuclearSeismicPacket;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
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
import net.minecraft.util.LongHashMap;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Incremental terrain processor and pressure-front controller for ordinary nukes.
 * Terrain work is deliberately independent from the shock front so server lag cannot
 * repeatedly damage entities or alter the apparent speed of the blast.
 */
public class EntityNukeExplosionMK5 extends EntityExplosionChunkloading {
	private static final int MAX_IGNITION_CANDIDATES_PER_TICK = 256;
	private static final int IGNITION_BATCH_SIZE = 32;
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
	private boolean groundIgnitionComplete;
	private ThermalIgnitionJob ignitionJob;
	private long lastIgnitionTick = Long.MIN_VALUE;
	private int ignitionCandidatesThisTick;
	private long activeWorkTick = Long.MIN_VALUE;
	private final ExplosionWorkScheduler.Job ignitionWork = new ExplosionWorkScheduler.Job() {
		public net.minecraft.entity.Entity owner() { return EntityNukeExplosionMK5.this; }
		public long activeTick() { return activeWorkTick; }
		public boolean ready(long tick) { return ignitionJob != null && !groundIgnitionComplete && (lastIgnitionTick != tick || ignitionCandidatesThisTick < MAX_IGNITION_CANDIDATES_PER_TICK); }
		public void run(long deadline, long tick) {
			advanceThermalGroundIgnition(deadline, tick);
			if(groundIgnitionComplete) ExplosionWorkScheduler.remove(this);
		}
	};
	private final ExplosionWorkScheduler.Job terrainWork = new ExplosionWorkScheduler.Job() {
		public net.minecraft.entity.Entity owner() { return EntityNukeExplosionMK5.this; }
		public long activeTick() { return activeWorkTick; }
		public boolean ready(long tick) { return explosion == null || explosion.canWork(tick, speed * 10); }
		public void run(long deadline, long tick) {
			if(!ExplosionWorkScheduler.before(deadline)) return;
			if(explosion == null) {
				explosion = new ExplosionNukeRayBatched(worldObj, (int)posX, (int)posY, (int)posZ, strength, speed, length, false, (int)Math.floor(burstContext != null ? burstContext.surfaceY : posY + spec.burialDepth));
				if(pendingExplosionData != null) { explosion.readFromNBT(pendingExplosionData); pendingExplosionData = null; }
			}
			if(!explosion.isAusf3Complete) explosion.collectTip(speed * 10, deadline, tick);
			else explosion.processChunk(deadline);
			if(!explosion.hasPendingWork() && ExplosionWorkScheduler.before(deadline)) {
				confirmPostExcavationBreach();
				ExplosionWorkScheduler.remove(this);
			}
		}
	};
	private boolean falloutSpawned;
	private boolean seismicSent;
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
			activeWorkTick = ExplosionWorkScheduler.currentTick();
			if(!seismicSent) { sendSeismicShake(); seismicSent = true; }
			if(!thermalApplied && (spec.burstType != BurstType.SUBSURFACE || spec.actualSurfaceBreach)) { applyThermalFlash(); thermalApplied = true; }
			applyThermalGroundIgnition();
			if(!promptApplied && (spec.burstType != BurstType.SUBSURFACE || spec.actualSurfaceBreach)) { applyPromptRadiation(); promptApplied = true; }
			advanceShockFront();
			if(ignitionJob != null && !groundIgnitionComplete) ExplosionWorkScheduler.register(ignitionWork);
		}
		if(worldObj.isRemote) return;
		double terrainRadius = spec.burstType == BurstType.SUBSURFACE ? effects.cavityRadius : effects.craterRadius;
		if(terrainRadius > 0D && spec.burstType != BurstType.AIR && spec.burstType != BurstType.VACUUM) {
			if(explosion == null || explosion.hasPendingWork() || (spec.burstType == BurstType.SUBSURFACE && !spec.breachConfirmationComplete)) ExplosionWorkScheduler.register(terrainWork);
			else if(currentShockRadius >= effects.lightBlastRadius) finishDetonation();
		} else if(currentShockRadius >= effects.lightBlastRadius) finishDetonation();
	}

	private void ensureEffects() {
		if(spec == null) {
			burstContext = NuclearBurstResolver.resolve(worldObj, posX, posY, posZ, Math.max(1, length));
			spec = NuclearDetonationSpec.fromLegacyRadius(burstContext.legacyRadius);
			spec.createsFallout = fallout; spec.salted = salted; copyContext(spec, burstContext);
		}
		if(effects == null) effects = NuclearEffectsSolver.solve(spec);
	}


	private void advanceShockFront() {
		if(spec.contained) { if(currentShockRadius == 0D) ExplosionNukeGeneric.dealGroundShock(worldObj, posX, posY, posZ, effects.groundShockRadius, spec.burialDepth); currentShockRadius = effects.lightBlastRadius; return; }
		if(effects.lightBlastRadius <= 0D) return;
		previousShockRadius = currentShockRadius;
		currentShockRadius = Math.min(effects.lightBlastRadius, currentShockRadius + Math.max(4D, effects.lightBlastRadius / 20D));
		double sourceX = spec.vented ? spec.breachX + 0.5D : posX, sourceY = spec.vented ? spec.breachY + 0.5D : (spec.burstType == BurstType.SURFACE && burstContext != null ? burstContext.surfaceY + 0.5D : posY), sourceZ = spec.vented ? spec.breachZ + 0.5D : posZ;
		ExplosionNukeGeneric.dealDamageFront(worldObj, sourceX, sourceY, sourceZ, previousShockRadius, currentShockRadius, effects.moderateBlastRadius, (float)(125F * (spec.vented ? spec.surfaceBreakthroughFactor : 1D)));
	}

	private void applyThermalFlash() {
		if(effects.thermalRadius <= 0D || spec.contained || spec.burstType == BurstType.UNDERWATER || spec.burstType == BurstType.VACUUM) return;
		double ignitionRadius = spec.burstType == BurstType.AIR ? effects.lightBlastRadius : effects.thermalRadius;
		double ignitionBelow = spec.burstType == BurstType.AIR ? ExplosionNukeGeneric.getBlastHeightBelow(ignitionRadius) : ignitionRadius;
		double ignitionAbove = spec.burstType == BurstType.AIR ? ExplosionNukeGeneric.getBlastHeightAbove(ignitionRadius) : ignitionRadius;
		double flashX = spec.vented ? spec.breachX + 0.5D : posX, flashY = spec.vented ? spec.breachY + 0.5D : posY, flashZ = spec.vented ? spec.breachZ + 0.5D : posZ;
		List<EntityLivingBase> entities = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, AxisAlignedBB.getBoundingBox(flashX - ignitionRadius, flashY - ignitionBelow, flashZ - ignitionRadius, flashX + ignitionRadius, flashY + ignitionAbove, flashZ + ignitionRadius));
		for(EntityLivingBase entity : entities) {
			double dx = entity.posX - flashX, dy = entity.posY + entity.getEyeHeight() - flashY, dz = entity.posZ - flashZ;
			double distanceSq = Math.max(1D, dx * dx + dy * dy + dz * dz);
			double horizontalDistanceSq = dx * dx + dz * dz;
			boolean thermalTarget = distanceSq <= effects.thermalRadius * effects.thermalRadius;
			boolean airburstBlastTarget = spec.burstType == BurstType.AIR && horizontalDistanceSq <= ignitionRadius * ignitionRadius && dy >= -ignitionBelow && dy <= ignitionAbove;
			if((!thermalTarget && !airburstBlastTarget) || worldObj.rayTraceBlocks(Vec3.createVectorHelper(flashX, flashY, flashZ), Vec3.createVectorHelper(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ)) != null) continue;
			double fluence = spec.yieldKt * spec.thermalFraction * 50D / distanceSq;
			if(thermalTarget && distanceSq <= effects.fireballRadius * effects.fireballRadius) {
				entity.attackEntityFrom(com.hbm.lib.ModDamageSource.nuclearBlast, 1000F);
			} else if(thermalTarget && fluence > 1D) {
				entity.attackEntityFrom(com.hbm.lib.ModDamageSource.nuclearBlast, (float)Math.min(100D, fluence));
			}
			// An unobstructed target inside the airburst blast footprint is exposed to the flash.
			if(spec.burstType == BurstType.AIR) entity.setFire(20);
			else if(fluence > 8D) entity.setFire((int)Math.min(20D, fluence / 2D));
			if(thermalTarget && fluence > 0.5D) entity.addPotionEffect(new PotionEffect(Potion.blindness.id, (int)Math.min(20 * 30, 20D + fluence * 20D), 0));
		}
	}

	/** Schedules exposed surface ignition independently from the immediate entity flash. */
	private void applyThermalGroundIgnition() {
		if(worldObj.isRemote || groundIgnitionComplete || ignitionJob != null) return;
		if(spec.burstType == BurstType.SUBSURFACE && !spec.actualSurfaceBreach) return;
		if(spec.contained || spec.burstType == BurstType.UNDERWATER || spec.burstType == BurstType.VACUUM || effects.thermalRadius <= 0D) return;
		// Airbursts have no crater pass to spread secondary fires. Cover the entire
		// blast footprint where practical and place fire on exposed solid terrain,
		// not only on terrain whose block itself is flammable.
		double ignitionRadius = spec.burstType == BurstType.AIR ? effects.lightBlastRadius : effects.thermalRadius;
		double sourceX = spec.vented ? spec.breachX + 0.5D : posX, sourceY = spec.vented ? spec.breachY + 0.5D : (spec.burstType == BurstType.SURFACE && burstContext != null ? burstContext.surfaceY + 0.5D : posY), sourceZ = spec.vented ? spec.breachZ + 0.5D : posZ;
		int samples = Math.min(65536, Math.max(4096, (int)Math.ceil(Math.PI * ignitionRadius * ignitionRadius)));
		ignitionJob = new ThermalIgnitionJob(worldObj.rand.nextLong(), sourceX, sourceY, sourceZ, ignitionRadius, samples);
	}

	/** Dispatched on the server thread; the candidate cap spans every slice this tick. */
	private void advanceThermalGroundIgnition(long deadline, long tick) {
		if(worldObj.isRemote || ignitionJob == null || groundIgnitionComplete) return;
		if(lastIgnitionTick != tick) { lastIgnitionTick = tick; ignitionCandidatesThisTick = 0; }
		ThermalIgnitionJob job = ignitionJob;
		if(!job.restoreSeen(deadline)) return;
		// Both sampling (including duplicates) and processing consume the tick's cap.
		// Insertion into a small chunk-ordered batch and deduplication are timed too.
		while(ignitionCandidatesThisTick < MAX_IGNITION_CANDIDATES_PER_TICK && ExplosionWorkScheduler.before(deadline)) {
			if(job.batchAttempts < IGNITION_BATCH_SIZE && job.sampleCursor < job.sampleCount) {
				ignitionCandidatesThisTick++;
				job.sampleCandidate();
			} else if(job.pendingCursor < job.pendingCount) {
				ignitionCandidatesThisTick++;
				long column = job.pending[job.pendingCursor];
				if(!processIgnitionColumn(job, (int)column, (int)(column >> 32), deadline)) break;
				job.pendingCursor++;
				job.columnPhase = 0; job.fuelSide = -1;
			} else if(job.sampleCursor < job.sampleCount) {
				job.batchAttempts = job.pendingCursor = job.pendingCount = 0;
			} else {
				break;
			}
		}
		groundIgnitionComplete = job.sampleCursor == job.sampleCount && job.pendingCursor == job.pendingCount;
	}

	private boolean processIgnitionColumn(ThermalIgnitionJob job, int x, int z, long deadline) {
		while(ExplosionWorkScheduler.before(deadline)) {
			int y = job.columnY;
			switch(job.columnPhase) {
			case 0: job.columnY = worldObj.getHeightValue(x, z) - 1; job.columnPhase++; break;
			case 1:
				if(y < 0 || !worldObj.isAirBlock(x, y + 1, z)) return true;
				job.columnPhase++; break;
			case 2:
				if(worldObj.getBlock(x, y, z).getMaterial().isLiquid()) return true;
				job.columnPhase++; break;
			case 3:
				boolean fuel;
				if(job.fuelSide < 0) {
					net.minecraft.block.Block block = worldObj.getBlock(x, y, z);
					if(!ExplosionWorkScheduler.before(deadline)) return false;
					fuel = block.isFlammable(worldObj, x, y, z, ForgeDirection.UP);
				}
				else {
					ForgeDirection side = ForgeDirection.VALID_DIRECTIONS[job.fuelSide];
					int xx = x + side.offsetX, yy = y + 1 + side.offsetY, zz = z + side.offsetZ;
					net.minecraft.block.Block block = worldObj.getBlock(xx, yy, zz);
					if(!ExplosionWorkScheduler.before(deadline)) return false;
					fuel = block.isFlammable(worldObj, xx, yy, zz, side.getOpposite());
				}
				if(fuel) job.columnPhase++;
				else if(++job.fuelSide == ForgeDirection.VALID_DIRECTIONS.length) return true;
				break;
			case 4:
				if(worldObj.rayTraceBlocks(Vec3.createVectorHelper(job.sourceX, job.sourceY, job.sourceZ), Vec3.createVectorHelper(x + 0.5D, y + 1.1D, z + 0.5D)) != null) return true;
				job.columnPhase++; break;
			default:
				worldObj.setBlock(x, y + 1, z, Blocks.fire, 0, 3);
				return true;
			}
		}
		return false;
	}

	private static final class ThermalIgnitionJob {
		final long seed;
		final double sourceX, sourceY, sourceZ, radius;
		final int sampleCount;
		final LongHashMap seen = new LongHashMap();
		long[] seenColumns;
		final long[] pending = new long[IGNITION_BATCH_SIZE];
		int sampleCursor, seenCount, batchAttempts, pendingCursor, pendingCount;
		int columnPhase, columnY, fuelSide = -1;
		private NBTTagCompound restoring;
		private int restoreCursor;

		ThermalIgnitionJob(long seed, double sourceX, double sourceY, double sourceZ, double radius, int sampleCount) {
			this.seed = seed; this.sourceX = sourceX; this.sourceY = sourceY; this.sourceZ = sourceZ;
			this.radius = radius; this.sampleCount = sampleCount;
		}

		void sampleCandidate() {
			double distance = radius * Math.sqrt(sampleUnit(seed, sampleCursor, 0));
			double angle = sampleUnit(seed, sampleCursor, 1) * Math.PI * 2D;
			int x = (int)Math.floor(sourceX + Math.cos(angle) * distance);
			int z = (int)Math.floor(sourceZ + Math.sin(angle) * distance);
			sampleCursor++; batchAttempts++;
			long column = ChunkCoordIntPair.chunkXZ2Int(x, z);
			if(seen.getValueByKey(column) != null) return;
			seen.add(column, Boolean.TRUE);
			seenColumns[seenCount++] = column;
			long chunk = ChunkCoordIntPair.chunkXZ2Int(x >> 4, z >> 4);
			int insert = pendingCount;
			while(insert > 0 && chunkOf(pending[insert - 1]) > chunk) {
				pending[insert] = pending[insert - 1]; insert--;
			}
			pending[insert] = column; pendingCount++;
		}

		private static long chunkOf(long column) {
			return ChunkCoordIntPair.chunkXZ2Int((int)column >> 4, (int)(column >> 32) >> 4);
		}

		/** Index-addressed SplitMix64 output; sqrt(U) retains uniform disc sampling. */
		private static double sampleUnit(long seed, int index, int component) {
			long value = seed + 0x9E3779B97F4A7C15L * (index * 2L + component + 1L);
			value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
			value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
			return ((value ^ (value >>> 31)) >>> 11) * 0x1.0p-53;
		}

		void writeToNBT(NBTTagCompound nbt) {
			if(restoring != null) {
				for(Object key : restoring.func_150296_c()) nbt.setTag((String)key, restoring.getTag((String)key).copy());
				return;
			}
			nbt.setLong("seed", seed);
			nbt.setDouble("sourceX", sourceX); nbt.setDouble("sourceY", sourceY); nbt.setDouble("sourceZ", sourceZ); nbt.setDouble("radius", radius);
			nbt.setInteger("samples", sampleCount); nbt.setInteger("sampleCursor", sampleCursor); nbt.setInteger("batchAttempts", batchAttempts); nbt.setInteger("pendingCursor", pendingCursor);
			nbt.setIntArray("seen", packColumns(seenColumns, seenCount));
			nbt.setIntArray("pending", packColumns(pending, pendingCount));
			nbt.setInteger("columnPhase", columnPhase); nbt.setInteger("columnY", columnY); nbt.setInteger("fuelSide", fuelSide);
		}

		static ThermalIgnitionJob readFromNBT(NBTTagCompound nbt) {
			int samples = Math.min(65536, Math.max(4096, nbt.getInteger("samples")));
			ThermalIgnitionJob job = new ThermalIgnitionJob(nbt.getLong("seed"), nbt.getDouble("sourceX"), nbt.getDouble("sourceY"), nbt.getDouble("sourceZ"), nbt.getDouble("radius"), samples);
			job.sampleCursor = Math.min(samples, Math.max(0, nbt.getInteger("sampleCursor")));
			job.batchAttempts = Math.min(IGNITION_BATCH_SIZE, Math.max(0, nbt.getInteger("batchAttempts")));
			int[] seen = nbt.getIntArray("seen"), pending = nbt.getIntArray("pending");
			job.seenCount = Math.min(job.sampleCursor, seen.length / 2);
			job.restoring = nbt;
			job.pendingCount = Math.min(job.batchAttempts, pending.length / 2);
			for(int i = 0; i < job.pendingCount; i++) job.pending[i] = ChunkCoordIntPair.chunkXZ2Int(pending[i * 2], pending[i * 2 + 1]);
			job.pendingCursor = Math.min(job.pendingCount, Math.max(0, nbt.getInteger("pendingCursor")));
			job.columnPhase = nbt.getInteger("columnPhase"); job.columnY = nbt.getInteger("columnY"); job.fuelSide = nbt.hasKey("fuelSide") ? nbt.getInteger("fuelSide") : -1;
			return job;
		}

		boolean restoreSeen(long deadline) {
			if(seenColumns == null) {
				if(!ExplosionWorkScheduler.before(deadline)) return false;
				seenColumns = new long[sampleCount];
				if(!ExplosionWorkScheduler.before(deadline)) return false;
			}
			if(restoring == null) return true;
			int[] data = restoring.getIntArray("seen");
			while(restoreCursor < seenCount && ExplosionWorkScheduler.before(deadline)) {
				long column = ChunkCoordIntPair.chunkXZ2Int(data[restoreCursor * 2], data[restoreCursor * 2 + 1]);
				seenColumns[restoreCursor++] = column; seen.add(column, Boolean.TRUE);
			}
			if(restoreCursor == seenCount) restoring = null;
			return restoring == null;
		}

		private static int[] packColumns(long[] columns, int count) {
			int[] packed = new int[count * 2];
			for(int i = 0; i < count; i++) { packed[i * 2] = (int)columns[i]; packed[i * 2 + 1] = (int)(columns[i] >> 32); }
			return packed;
		}
	}

	private void applyPromptRadiation() {
		if(effects.promptRadiationRadius <= 0D || spec.contained) return;
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


	private void sendSeismicShake() {
		if(spec.groundCoupling <= 0D) return;
		int duration = (int)Math.min(12000D, 1800D + spec.burialDepth * 35D + effects.groundShockRadius * 12D);
		double range = Math.max(64D, effects.groundShockRadius * 5D);
		PacketDispatcher.wrapper.sendToAllAround(new NuclearSeismicPacket(posX, posY, posZ, spec.yieldKt, spec.groundCoupling, spec.burialDepth, duration, (float)Math.min(2D, .35D + spec.groundCoupling)), new TargetPoint(worldObj.provider.dimensionId, posX, posY, posZ, range));
	}

	/** Re-check only after every queued terrain block has been applied. Prediction is never promoted implicitly. */
	private void confirmPostExcavationBreach() {
		if(spec.burstType != BurstType.SUBSURFACE || spec.breachConfirmationComplete) return;
		int searchRadius = Math.min(64, Math.max(8, (int)Math.ceil(effects.cavityRadius)));
		int[] breach = NuclearBurstResolver.findOpenRelease(worldObj, (int)Math.floor(posX), (int)Math.floor(posY), (int)Math.floor(posZ), searchRadius);
		spec.breachConfirmationComplete = true; spec.actualSurfaceBreach = breach != null; spec.vented = breach != null; spec.contained = breach == null;
		spec.atmosphericReleaseFactor = breach == null ? 0D : Math.max(.05D, spec.predictedBreakthroughFactor);
		if(breach != null) { spec.breachX=breach[0]; spec.breachY=breach[1]; spec.breachZ=breach[2]; }
		effects = NuclearEffectsSolver.solve(spec);
		if(spec.actualSurfaceBreach) { if(!thermalApplied) { applyThermalFlash(); thermalApplied=true; } applyThermalGroundIgnition(); if(!promptApplied) { applyPromptRadiation(); promptApplied=true; } }
	}

	private void finishDetonation() {
		if(worldObj.isRemote) return;
		if(pendingExplosionData != null || (explosion != null && explosion.hasPendingWork())) return;
		if(!falloutSpawned && fallout && effects.falloutSourceStrength > 0D && spec.burstType != BurstType.VACUUM && (spec.burstType != BurstType.SUBSURFACE || (spec.actualSurfaceBreach && spec.atmosphericReleaseFactor >= 0.10D))) {
			EntityFalloutRain rain = new EntityFalloutRain(worldObj);
			rain.setPosition(spec.burstType == BurstType.SUBSURFACE ? spec.breachX + 0.5D : posX, spec.burstType == BurstType.SUBSURFACE ? spec.breachY + 0.5D : posY, spec.burstType == BurstType.SUBSURFACE ? spec.breachZ + 0.5D : posZ); rain.setSalted(salted);
			rain.setSourceMultiplier(effects.falloutSourceStrength / Math.max(0.001D, spec.yieldKt * spec.fissionFraction));
			rain.setScale((int)((length * 2.5D + falloutAdd) * BombConfig.falloutRange / 100D * rain.getSourceMultiplier()));
			worldObj.spawnEntityInWorld(rain); falloutSpawned = true;
		}
		// Fallout starts at the original finish condition, even if ignition needs more ticks.
		if(ignitionJob != null && !groundIgnitionComplete) return;
		clearChunkLoader(); setDead();
	}

	@Override public void setDead() {
		ignitionJob = null;
		super.setDead();
	}

	@Override protected void readEntityFromNBT(NBTTagCompound nbt) {
		ticksExisted = nbt.getInteger("ticksExisted"); strength = nbt.getInteger("strength"); speed = nbt.getInteger("speed"); length = nbt.getInteger("length");
		fallout = nbt.getBoolean("fallout"); salted = nbt.getBoolean("salted"); falloutAdd = nbt.getInteger("falloutAdd"); previousShockRadius = nbt.getDouble("previousShock"); currentShockRadius = nbt.getDouble("currentShock"); promptApplied = nbt.getBoolean("promptApplied"); thermalApplied = nbt.getBoolean("thermalApplied"); falloutSpawned = nbt.getBoolean("falloutSpawned"); seismicSent = nbt.getBoolean("seismicSent");
		ignitionJob = nbt.hasKey("thermalIgnitionJob") ? ThermalIgnitionJob.readFromNBT(nbt.getCompoundTag("thermalIgnitionJob")) : null;
		// Legacy thermalApplied means the old synchronous ignition pass already ran.
		groundIgnitionComplete = nbt.hasKey("groundIgnitionComplete") ? nbt.getBoolean("groundIgnitionComplete") : ignitionJob == null && thermalApplied;
		lastIgnitionTick = Long.MIN_VALUE;
		spec = NuclearDetonationSpec.fromLegacyRadius(Math.max(1, length)); if(nbt.hasKey("yieldKt")) spec.yieldKt = nbt.getDouble("yieldKt"); spec.fissionFraction = nbt.getDouble("fissionFraction"); spec.burstHeight = nbt.getDouble("burstHeight"); spec.groundCoupling = nbt.getDouble("groundCoupling"); spec.thermalFraction = nbt.getDouble("thermalFraction"); spec.promptGammaFraction = nbt.getDouble("gammaFraction"); spec.promptNeutronFraction = nbt.getDouble("neutronFraction"); spec.createsFallout = fallout; spec.createsEMP = nbt.getBoolean("emp"); spec.salted = salted;
		try { spec.burstType = BurstType.valueOf(nbt.getString("burstType")); } catch(IllegalArgumentException ex) { spec.burstType = BurstType.SURFACE; }
		if(nbt.hasKey("burialDepth")) { spec.burialDepth = nbt.getDouble("burialDepth"); spec.predictedBreakthroughFactor = spec.surfaceBreakthroughFactor = nbt.getDouble("surfaceBreakthrough"); spec.actualSurfaceBreach = nbt.getBoolean("actualSurfaceBreach"); spec.atmosphericReleaseFactor = nbt.getDouble("atmosphericRelease"); spec.surfaceDeformationFactor = nbt.getDouble("surfaceDeformation"); spec.breachConfirmationComplete = nbt.getBoolean("breachConfirmed"); spec.contained = nbt.getBoolean("contained"); spec.vented = nbt.getBoolean("vented"); spec.breachX = nbt.getInteger("breachX"); spec.breachY = nbt.getInteger("breachY"); spec.breachZ = nbt.getInteger("breachZ"); }
		else { burstContext = NuclearBurstResolver.resolve(worldObj, posX, posY, posZ, Math.max(1, length)); copyContext(spec, burstContext); }
		effects = NuclearEffectsSolver.solve(spec);
		if(nbt.hasKey("terrainWork")) pendingExplosionData = nbt.getCompoundTag("terrainWork");
	}
	@Override protected void writeEntityToNBT(NBTTagCompound nbt) {
		ensureEffects(); nbt.setInteger("ticksExisted", ticksExisted); nbt.setInteger("strength", strength); nbt.setInteger("speed", speed); nbt.setInteger("length", length); nbt.setBoolean("fallout", fallout); nbt.setBoolean("salted", salted); nbt.setInteger("falloutAdd", falloutAdd); nbt.setDouble("previousShock", previousShockRadius); nbt.setDouble("currentShock", currentShockRadius); nbt.setBoolean("promptApplied", promptApplied); nbt.setBoolean("thermalApplied", thermalApplied); nbt.setBoolean("falloutSpawned", falloutSpawned); nbt.setBoolean("seismicSent", seismicSent);
		nbt.setBoolean("groundIgnitionComplete", groundIgnitionComplete);
		if(ignitionJob != null) { NBTTagCompound ignitionWork = new NBTTagCompound(); ignitionJob.writeToNBT(ignitionWork); nbt.setTag("thermalIgnitionJob", ignitionWork); }
		if(explosion != null) { NBTTagCompound terrainWork = new NBTTagCompound(); explosion.writeToNBT(terrainWork); nbt.setTag("terrainWork", terrainWork); }
		else if(pendingExplosionData != null) nbt.setTag("terrainWork", pendingExplosionData.copy());
		nbt.setDouble("yieldKt", spec.yieldKt); nbt.setDouble("fissionFraction", spec.fissionFraction); nbt.setString("burstType", spec.burstType.name()); nbt.setDouble("burstHeight", spec.burstHeight); nbt.setDouble("groundCoupling", spec.groundCoupling); nbt.setDouble("thermalFraction", spec.thermalFraction); nbt.setDouble("gammaFraction", spec.promptGammaFraction); nbt.setDouble("neutronFraction", spec.promptNeutronFraction); nbt.setBoolean("emp", spec.createsEMP);
		nbt.setDouble("burialDepth", spec.burialDepth); nbt.setBoolean("actualSurfaceBreach", spec.actualSurfaceBreach); nbt.setDouble("atmosphericRelease", spec.atmosphericReleaseFactor); nbt.setDouble("surfaceDeformation", spec.surfaceDeformationFactor); nbt.setBoolean("breachConfirmed", spec.breachConfirmationComplete); nbt.setDouble("surfaceBreakthrough", spec.surfaceBreakthroughFactor); nbt.setBoolean("contained", spec.contained); nbt.setBoolean("vented", spec.vented); nbt.setInteger("breachX", spec.breachX); nbt.setInteger("breachY", spec.breachY); nbt.setInteger("breachZ", spec.breachZ);
	}

	public static EntityNukeExplosionMK5 statFac(World world, int r, double x, double y, double z) {
		if(GeneralConfig.enableExtendedLogging && !world.isRemote) MainRegistry.logger.log(Level.INFO, "[NUKE] Initialized explosion at " + x + " / " + y + " / " + z + " with legacy radius " + r + "!");
		if(r == 0) r = 25;
		NuclearBurstContext context = NuclearBurstResolver.resolve(world, x, y, z, Math.max(1, r));
		EntityNukeExplosionMK5 mk5 = new EntityNukeExplosionMK5(world); mk5.length = context.legacyRadius; mk5.burstContext = context; mk5.spec = NuclearDetonationSpec.fromLegacyRadius(context.legacyRadius); copyContext(mk5.spec, context); mk5.effects = NuclearEffectsSolver.solve(mk5.spec);
		double terrainRadius = context.burstType == BurstType.SUBSURFACE ? mk5.effects.cavityRadius : mk5.effects.craterRadius;
		mk5.strength = Math.max(1, (int)Math.ceil(terrainRadius * 2D)); mk5.speed = Math.max(1, (int)Math.ceil(100000D / mk5.strength)); mk5.setPosition(x, y, z);
		if(GeneralConfig.enableExtendedLogging && !world.isRemote) MainRegistry.logger.log(Level.INFO, "[NUKE] type=" + context.burstType + " yieldKt=" + context.yieldKt + " surfaceY=" + context.surfaceY + " burialDepth=" + context.burialDepth + " coupling=" + context.groundCoupling + " predictedBreakthrough=" + context.predictedBreakthroughFactor + " confirmedBreach=" + context.actualSurfaceBreach + " atmosphericRelease=" + context.atmosphericReleaseFactor + " breach=" + context.breachX + "," + context.breachY + "," + context.breachZ + " contained=" + context.contained + " vented=" + context.vented + " thermal=" + mk5.effects.thermalRadius + " blast=" + mk5.effects.lightBlastRadius + " crater=" + mk5.effects.craterRadius + " fallout=" + mk5.effects.falloutSourceStrength);
		return mk5;
	}
	private static void copyContext(NuclearDetonationSpec target, NuclearBurstContext context) { target.burstType = context.burstType; target.burstHeight = context.burstHeight; target.groundCoupling = context.groundCoupling; target.burialDepth = context.burialDepth; target.predictedBreakthroughFactor = target.surfaceBreakthroughFactor = context.predictedBreakthroughFactor; target.actualSurfaceBreach = context.actualSurfaceBreach; target.atmosphericReleaseFactor = context.atmosphericReleaseFactor; target.surfaceDeformationFactor = context.surfaceDeformationFactor; target.contained = context.contained; target.vented = context.vented; target.breachX = context.breachX; target.breachY = context.breachY; target.breachZ = context.breachZ; }
	public static EntityNukeExplosionMK5 statFacNoRad(World world, int r, double x, double y, double z) { EntityNukeExplosionMK5 mk5 = statFac(world, r, x, y, z); mk5.fallout = false; mk5.spec.createsFallout = false; return mk5; }
	public static EntityNukeExplosionMK5 statFacSalted(World world, int r, double x, double y, double z) { EntityNukeExplosionMK5 mk5 = statFac(world, r, x, y, z); mk5.salted = true; mk5.spec.salted = true; return mk5; }
	public EntityNukeExplosionMK5 moreFallout(int fallout) { falloutAdd = fallout; return this; }
}
