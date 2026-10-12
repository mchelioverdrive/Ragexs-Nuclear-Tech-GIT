package com.hbm.entity.effect;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.BombConfig;
import com.hbm.config.FalloutConfigJSON;
import com.hbm.config.FalloutConfigJSON.FalloutEntry;
import com.hbm.config.WorldConfig;
import com.hbm.entity.item.EntityFallingBlockNT;
import com.hbm.entity.logic.EntityExplosionChunkloading;
import com.hbm.explosion.ExplosionWorkScheduler;
import com.hbm.saveddata.AuxSavedData;
import com.hbm.world.WorldUtil;
import com.hbm.world.biome.BiomeGenCraterBase;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.util.ForgeDirection;


import java.util.*;

public class EntityFalloutRain extends EntityExplosionChunkloading {

	private boolean gatherComplete;
	private int gatherPhase, gatherAngle, gatherDistance, reverseCursor;
	private Set<Long> gatheredChunks = new HashSet<Long>();
	private Set<Long> gatheredOuter = new HashSet<Long>();
	private boolean activeChunk, activeOuter, biomeModified, columnStarted, columnStompComplete;
	private long chunkCursor;
	private int columnCursor, columnY = 255, columnDepth;
	private long activeWorkTick = Long.MIN_VALUE, delayTick = Long.MIN_VALUE;
	private boolean eligible;
	private NBTTagCompound restoring;
	private int restoreQueue, restoreIndex;
	private final ExplosionWorkScheduler.Job falloutWork = new ExplosionWorkScheduler.Job() {
		public net.minecraft.entity.Entity owner() { return EntityFalloutRain.this; }
		public long activeTick() { return activeWorkTick; }
		public boolean ready(long tick) { return restoring != null || !gatherComplete || eligible; }
		public void run(long deadline, long tick) {
			if(!restoreQueues(deadline)) return;
			if(!gatherComplete) gatherChunks(deadline);
			if(gatherComplete && eligible) processFallout(deadline);
		}
	};
	private boolean salted = false;
	private double sourceMultiplier = 1D;
	public EntityFalloutRain(World p_i1582_1_) {
		super(p_i1582_1_);
		this.setSize(4, 20);
		this.ignoreFrustumCheck = true;
		this.isImmuneToFire = true;
	}

	//TODO idea: weather2 compat - spread fallout based on wind and actual fallout rain mechanic

	public EntityFalloutRain(World p_i1582_1_, int maxAge) {
		super(p_i1582_1_);
		this.setSize(4, 20);
		this.isImmuneToFire = true;
	}

	private int tickDelay = BombConfig.fDelay;

	@Override
	public void onUpdate() {
		if(worldObj.isRemote || isDead) return;
		activeWorkTick = ExplosionWorkScheduler.currentTick();
		if(delayTick != activeWorkTick) {
			delayTick = activeWorkTick;
			eligible = tickDelay == 0;
			if(eligible) tickDelay = BombConfig.fDelay;
			tickDelay--;
		}
		ExplosionWorkScheduler.register(falloutWork);
	}

	private void finishFallout() {
		clearChunkLoader(); setDead();
		if(BombConfig.rain > 0 && getScale() > 150) {
			WorldInfo info = worldObj.getWorldInfo();
			info.setRaining(true); info.setThundering(true);
			info.setRainTime(BombConfig.rain); info.setThunderTime(BombConfig.rain);
			AuxSavedData.setThunder(worldObj, BombConfig.rain);
		}
	}

	private void processFallout(long deadline) {
		while(ExplosionWorkScheduler.before(deadline)) {
			if(!activeChunk) {
				activeOuter = chunksToProcess.isEmpty();
				List<Long> queue = activeOuter ? outerChunksToProcess : chunksToProcess;
				if(queue.isEmpty()) { finishFallout(); return; }
				chunkCursor = queue.get(queue.size() - 1);
				activeChunk = true; columnCursor = 0; biomeModified = false; columnStarted = false;
			}
			// Signed coordinates are retained in new saves, including negative chunks.
			int chunkX = (int)chunkCursor, chunkZ = (int)(chunkCursor >> 32);
			while(columnCursor < 256 && ExplosionWorkScheduler.before(deadline)) {
				int x = (chunkX << 4) + columnCursor / 16, z = (chunkZ << 4) + columnCursor % 16;
				double distance = Math.hypot(x - posX, z - posZ), percent = distance * 100 / getScale();
				if(activeOuter && distance > getScale()) { columnCursor++; continue; }
				if(!columnStarted) { columnY = 255; columnDepth = 0; columnStarted = true; columnStompComplete = false; }
				if(!columnStompComplete) {
					if(!stomp(x, z, percent, deadline)) return;
					columnStompComplete = true;
				}
				if(!ExplosionWorkScheduler.before(deadline)) return;
				if(worldObj.provider.dimensionId == 0) {
					BiomeGenBase biome = getBiomeChange(percent, getScale(), worldObj.getBiomeGenForCoords(x, z));
					if(!ExplosionWorkScheduler.before(deadline)) return;
					if(biome != null) { WorldUtil.setBiome(worldObj, x, z, biome); biomeModified = true; }
				}
				columnCursor++; columnStarted = false; // Commit only after this column's effects finish.
			}
			if(columnCursor < 256 || !ExplosionWorkScheduler.before(deadline)) return;
			if(biomeModified) WorldUtil.syncBiomeChange(worldObj, chunkX << 4, chunkZ << 4);
			List<Long> queue = activeOuter ? outerChunksToProcess : chunksToProcess;
			queue.remove(queue.size() - 1); // Keep the active chunk queued until its sync completes.
			activeChunk = false;
		}
	}

	public static BiomeGenBase getBiomeChange(double dist, int scale, BiomeGenBase original) {
		if(!WorldConfig.enableCraterBiomes) return null;
		//get world dimension, if not overworld, return null
		//if (world == null || world.provider.dimensionId != 0) return null;
		//we can't register world here because of how fucked minecraft modding is

		if(scale >= 150 && dist < 8)
			return BiomeGenCraterBase.craterInnerBiome;
		if(scale >= 100 && dist < 28 && original != BiomeGenCraterBase.craterInnerBiome)
			return BiomeGenCraterBase.craterBiome;
		if(scale >= 40 && original != BiomeGenCraterBase.craterInnerBiome && original != BiomeGenCraterBase.craterBiome)
			return BiomeGenCraterBase.craterOuterBiome;
		return null;
	}

	private final List<Long> chunksToProcess = new ArrayList<>();
	private final List<Long> outerChunksToProcess = new ArrayList<>();

	/** Original radial samples and insertion order, with bounded sampling and reversal. */
	private void gatherChunks(long deadline) {
		int outerRange = getScale(), adjustedMaxAngle = 20 * outerRange / 32;
		while(!gatherComplete && ExplosionWorkScheduler.before(deadline)) {
			if(gatherPhase < 2) {
				if(gatherAngle > adjustedMaxAngle) {
					gatherAngle = 0;
					if(gatherPhase == 0) gatherPhase = 1;
					else gatherDistance += 8;
					if(gatherDistance > outerRange) gatherPhase = 2;
					continue;
				}
				int distance = gatherPhase == 0 ? outerRange : gatherDistance;
				Vec3 vector = Vec3.createVectorHelper(distance, 0, 0);
				vector.rotateAroundY((float)(gatherAngle * Math.PI / 180.0 / (adjustedMaxAngle / 360.0)));
				long coord = ChunkCoordIntPair.chunkXZ2Int((int)(posX + vector.xCoord) >> 4, (int)(posZ + vector.zCoord) >> 4);
				if(gatherPhase == 0) {
					if(gatheredOuter.add(coord)) outerChunksToProcess.add(coord);
				} else if(!gatheredOuter.contains(coord) && gatheredChunks.add(coord)) chunksToProcess.add(coord);
				gatherAngle++;
			} else {
				List<Long> queue = gatherPhase == 2 ? chunksToProcess : outerChunksToProcess;
				if(reverseCursor < queue.size() / 2) {
					Collections.swap(queue, reverseCursor, queue.size() - 1 - reverseCursor);
					reverseCursor++;
				} else {
					reverseCursor = 0;
					if(++gatherPhase == 4) { gatherComplete = true; gatheredChunks = null; gatheredOuter = null; }
				}
			}
		}
	}

	private void placeFallout(int x, int y, int z, Block falloutBlock) {

		Block existing = worldObj.getBlock(x, y, z);

		// Stack onto existing fallout
		if(existing == falloutBlock) {

			int meta = worldObj.getBlockMetadata(x, y, z);

			if(meta < getMaximumDepositedLayer()) {
				worldObj.setBlockMetadataWithNotify(
					x, y, z,
					meta + 1,
					3
				);
			}

			return;
		}

		// Place new fallout
		if(falloutBlock.canPlaceBlockAt(worldObj, x, y, z)) {
			worldObj.setBlock(x, y, z, falloutBlock, 0, 3);
		}
	}

	private boolean stomp(int x, int z, double dist, long deadline) {
		while(columnY >= 0) {
			if(columnDepth >= 2) return true;
			if(!ExplosionWorkScheduler.before(deadline)) return false;
			int y = columnY;

			Block b = worldObj.getBlock(x, y, z);
			if(!ExplosionWorkScheduler.before(deadline)) return false;

			if(b.getMaterial() == Material.air || b == ModBlocks.fallout || b == ModBlocks.salted_fallout) {
				columnY--; continue;
			}
			// Once a block is selected, finish all its random effects and callbacks atomically.

			Block above = worldObj.getBlock(x, y + 1, z);
			int meta = worldObj.getBlockMetadata(x, y, z);

			/*
			 * Fallout deposition
			 *
			 * Real fallout settles mostly in a ring outside the fireball,
			 * not directly at ground zero.
			 */
			if(
				columnDepth == 0 &&
					(
						above == Blocks.air ||
							(
								above.isReplaceable(worldObj, x, y + 1, z) &&
									!above.getMaterial().isLiquid()
							)
					)
			) {

				double normalized = dist / 100D;

				// Peak fallout deposition farther away from hypocenter
				double chance = 0.18D * sourceMultiplier * Math.exp(-Math.pow((normalized - 0.72D) / 0.22D, 2));

				if(this.salted) {
					chance *= 1.75D;
				}

				if(rand.nextDouble() < chance) {

					placeFallout(
						x,
						y + 1,
						z,
						this.salted ? ModBlocks.salted_fallout : ModBlocks.fallout
					);
				}
			}

			/*
			 * Thermal ignition near hypocenter
			 */
			if(dist < 35 && b.isFlammable(worldObj, x, y, z, ForgeDirection.UP)) {

				if(rand.nextInt(3) == 0 &&
					worldObj.getBlock(x, y + 1, z).isAir(worldObj, x, y + 1, z)) {

					setBlock(x, y + 1, z, Blocks.fire);
				}
			}

			/*
			 * Apply fallout material transformations
			 */
			boolean transformed = false;

			for(FalloutEntry entry : FalloutConfigJSON.entries) {

				if(entry.eval(worldObj, x, y, z, b, meta, dist, b, meta)) {

					if(entry.isSolid()) {
						columnDepth++;
					}

					transformed = true;
					break;
				}
			}

			/*
			 * Blast-collapse behavior
			 */
			float hardness = b.getBlockHardness(worldObj, x, y, z);

			if(
				y > 0 &&
					dist < 30 &&
					hardness >= 0 &&
					hardness <= 8F
			) {

				if(worldObj.getBlock(x, y - 1, z) == Blocks.air) {

					for(int i = 0; i <= columnDepth; i++) {

						Block block = worldObj.getBlock(x, y + i, z);

						EntityFallingBlockNT entityfallingblock =
							new EntityFallingBlockNT(
								worldObj,
								x + 0.5D,
								y + 0.5D + i,
								z + 0.5D,
								block,
								worldObj.getBlockMetadata(x, y + i, z)
							);

						entityfallingblock.canDrop = false;
						worldObj.spawnEntityInWorld(entityfallingblock);
					}
				}
			}

			if(!transformed && b.isNormalCube()) {
				columnDepth++;
			}
			columnY--; // Advance only after deposition, transformations, fire and falling entities finish.
		}
		return true;
	}

	public void setBlock(int x, int y, int z, Block block) {
		setBlock(x, y, z, block, 0);
	}

	public void setBlock(int x, int y, int z, Block block, int meta) {
		worldObj.setBlock(x, y, z, block, meta, 3); //this was supposed to write the position to a list for a multi block update, but forge already has that built-in. whoops.
	}

	@Override
	protected void entityInit() {
		super.entityInit();
		this.dataWatcher.addObject(16, 0);
	}

	@Override
	protected void readEntityFromNBT(NBTTagCompound tag) {
		setScale(tag.getInteger("scale"));
		salted = tag.getBoolean("salt"); sourceMultiplier = tag.hasKey("sourceMultiplier") ? tag.getDouble("sourceMultiplier") : 1D;
		gatherComplete = tag.hasKey("gatherComplete") ? tag.getBoolean("gatherComplete") : tag.getIntArray("chunks").length > 0 || tag.getIntArray("outerChunks").length > 0;
		gatherPhase = tag.getInteger("gatherPhase"); gatherAngle = tag.getInteger("gatherAngle");
		gatherDistance = tag.getInteger("gatherDistance"); reverseCursor = tag.getInteger("reverseCursor");
		tickDelay = tag.hasKey("tickDelay") ? tag.getInteger("tickDelay") : BombConfig.fDelay;
		activeChunk = tag.getBoolean("activeChunk"); activeOuter = tag.getBoolean("activeOuter"); chunkCursor = tag.getLong("chunkCursor");
		columnCursor = tag.getInteger("columnCursor"); columnY = tag.hasKey("columnY") ? tag.getInteger("columnY") : 255;
		columnDepth = tag.getInteger("columnDepth"); biomeModified = tag.getBoolean("biomeModified");
		columnStarted = tag.getBoolean("columnStarted"); columnStompComplete = tag.getBoolean("columnStompComplete");
		chunksToProcess.clear(); outerChunksToProcess.clear();
		gatheredChunks = gatherComplete ? null : new HashSet<Long>(); gatheredOuter = gatherComplete ? null : new HashSet<Long>();
		restoring = tag; restoreQueue = restoreIndex = 0; delayTick = activeWorkTick = Long.MIN_VALUE;
	}

	private boolean restoreQueues(long deadline) {
		while(restoring != null && ExplosionWorkScheduler.before(deadline)) {
			int[] data = restoring.getIntArray(restoreQueue == 0 ? "chunks" : "outerChunks");
			if(restoreIndex + 1 >= data.length) {
				restoreIndex = 0;
				if(++restoreQueue == 2) restoring = null;
				continue;
			}
			long coord = ChunkCoordIntPair.chunkXZ2Int(data[restoreIndex], data[restoreIndex + 1]);
			(restoreQueue == 0 ? chunksToProcess : outerChunksToProcess).add(coord);
			if(!gatherComplete) (restoreQueue == 0 ? gatheredChunks : gatheredOuter).add(coord);
			restoreIndex += 2;
		}
		return restoring == null;
	}

	@Override
	protected void writeEntityToNBT(NBTTagCompound tag) {
		tag.setInteger("workVersion", 2); tag.setInteger("scale", getScale());
		tag.setBoolean("salt", salted); tag.setDouble("sourceMultiplier", sourceMultiplier);
		// No effects run during restore. Save the complete original queues with the
		// restored scalar cursors, without overwriting Entity's current base NBT fields.
		tag.setIntArray("chunks", restoring != null ? restoring.getIntArray("chunks") : writeChunksToIntArray(chunksToProcess));
		tag.setIntArray("outerChunks", restoring != null ? restoring.getIntArray("outerChunks") : writeChunksToIntArray(outerChunksToProcess));
		tag.setBoolean("gatherComplete", gatherComplete); tag.setInteger("gatherPhase", gatherPhase); tag.setInteger("gatherAngle", gatherAngle);
		tag.setInteger("gatherDistance", gatherDistance); tag.setInteger("reverseCursor", reverseCursor); tag.setInteger("tickDelay", tickDelay);
		tag.setBoolean("activeChunk", activeChunk); tag.setBoolean("activeOuter", activeOuter); tag.setLong("chunkCursor", chunkCursor);
		tag.setInteger("columnCursor", columnCursor); tag.setInteger("columnY", columnY); tag.setInteger("columnDepth", columnDepth);
		tag.setBoolean("biomeModified", biomeModified); tag.setBoolean("columnStarted", columnStarted); tag.setBoolean("columnStompComplete", columnStompComplete);
	}

	private int[] writeChunksToIntArray(List<Long> coords) {
		int[] data = new int[coords.size() * 2];
		for(int i = 0; i < coords.size(); i++) {
			data[i * 2] = (int)(long)coords.get(i);
			data[i * 2 + 1] = (int)(coords.get(i) >> 32);
		}
		return data;
	}

	public void setScale(int i) {
		this.dataWatcher.updateObject(16, i);
	}

	public int getScale() {
		int scale = this.dataWatcher.getWatchableObjectInt(16);
		return scale == 0 ? 1 : scale;
	}
	public void setSourceMultiplier(double multiplier) { sourceMultiplier = Math.max(0D, Math.min(1D, multiplier)); }
	public double getSourceMultiplier() { return sourceMultiplier; }
	private int getMaximumDepositedLayer() { return Math.max(0, Math.min(7, (int)Math.ceil(7D * sourceMultiplier))); }

	public void setSalted(boolean salt) {
		this.salted = salt;
	}

	public boolean getSalted() {
		boolean salt = this.salted;
		return salt;
	}
}
