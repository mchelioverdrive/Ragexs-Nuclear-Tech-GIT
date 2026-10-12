package com.hbm.entity.effect;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.BombConfig;
import com.hbm.config.FalloutConfigJSON;
import com.hbm.config.FalloutConfigJSON.FalloutEntry;
import com.hbm.config.WorldConfig;
import com.hbm.entity.item.EntityFallingBlockNT;
import com.hbm.entity.logic.EntityExplosionChunkloading;
import com.hbm.explosion.ExplosionWorkScheduler;
import com.hbm.explosion.FalloutCollapseManager;
import com.hbm.saveddata.AuxSavedData;
import com.hbm.world.WorldUtil;
import com.hbm.world.biome.BiomeGenCraterBase;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.LongHashMap;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.util.ForgeDirection;


import java.util.*;

public class EntityFalloutRain extends EntityExplosionChunkloading {

	private boolean gatherComplete;
	private int gatherRing, gatherStep;
	// Only an unfinished legacy gather needs exclusions; new rings never emit a duplicate.
	private final Set<Long> legacyChunks = new HashSet<Long>();
	private boolean legacyGather;
	private boolean activeChunk, activeOuter, biomeModified, columnStarted, columnStompComplete;
	private long chunkCursor;
	private int columnCursor, columnY = 255, columnDepth;
	private long activeWorkTick = Long.MIN_VALUE, delayTick = Long.MIN_VALUE;
	private boolean eligible;
	private NBTTagCompound restoring;
	private int restoreQueue, restoreIndex;
	private static final int MAX_PENDING_COLLAPSES = 1024;
	// A block can request itself and up to two layers after a solid transformation.
	private static final int MAX_COLLAPSES_PER_BLOCK = 3;
	private final long[] collapsePositions = new long[MAX_PENDING_COLLAPSES];
	private final Block[] collapseBlocks = new Block[MAX_PENDING_COLLAPSES];
	private final int[] collapseMetadata = new int[MAX_PENDING_COLLAPSES];
	private LongHashMap collapsePending = new LongHashMap();
	private int collapseHead, collapseCount;
	private long blockedWorkTick = Long.MIN_VALUE;
	private final ExplosionWorkScheduler.Job falloutWork = new ExplosionWorkScheduler.Job() {
		public net.minecraft.entity.Entity owner() { return EntityFalloutRain.this; }
		public long activeTick() { return activeWorkTick; }
		public boolean ready(long tick) {
			return restoring != null || (!gatherComplete && !activeChunk && chunksToProcess.isEmpty() && outerChunksToProcess.isEmpty()) || (eligible && blockedWorkTick != tick);
		}
		public void run(long deadline, long tick) {
			if(!restoreQueues(deadline)) return;
			if(eligible) {
				processCollapses(deadline, tick);
				processFallout(deadline, tick);
			} else if(!gatherComplete && chunksToProcess.isEmpty() && outerChunksToProcess.isEmpty()) gatherChunks(deadline);
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

	private void processFallout(long deadline, long tick) {
		int scale = getScale();
		double radiusSquared = (double)scale * scale;
		while(ExplosionWorkScheduler.before(deadline)) {
			if(!activeChunk) {
				activeOuter = chunksToProcess.isEmpty();
				List<Long> queue = activeOuter ? outerChunksToProcess : chunksToProcess;
				if(queue.isEmpty() && !gatherComplete) { gatherChunks(deadline); continue; }
				if(queue.isEmpty()) {
					if(collapseCount == 0) finishFallout();
					else if(!FalloutCollapseManager.canSpawn(tick)) blockedWorkTick = tick;
					return;
				}
				chunkCursor = queue.get(queue.size() - 1);
				activeChunk = true; columnCursor = 0; biomeModified = false; columnStarted = false;
			}
			// Signed coordinates are retained in new saves, including negative chunks.
			int chunkX = (int)chunkCursor, chunkZ = (int)(chunkCursor >> 32);
			Chunk chunk = worldObj.getChunkFromChunkCoords(chunkX, chunkZ);
			if(!ExplosionWorkScheduler.before(deadline)) return;
			ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
			WorldUtil.BiomeAccess biomes = worldObj.provider.dimensionId == 0 && WorldConfig.enableCraterBiomes ? new WorldUtil.BiomeAccess(chunk) : null;
			while(columnCursor < 256 && ExplosionWorkScheduler.before(deadline)) {
				int x = (chunkX << 4) + columnCursor / 16, z = (chunkZ << 4) + columnCursor % 16;
				double dx = x - posX, dz = z - posZ;
				if(activeOuter && dx * dx + dz * dz > radiusSquared) { columnCursor++; continue; }
				// The original block-coordinate reference and normalized distance are unchanged.
				double percent = Math.hypot(dx, dz) * 100 / scale;
				if(!columnStarted) {
					columnY = highestOccupiedY(sections); columnDepth = 0;
					columnStarted = true; columnStompComplete = false;
				}
				if(!columnStompComplete) {
					if(!stomp(x, z, percent, sections, deadline)) {
						if(collapseCount > MAX_PENDING_COLLAPSES - MAX_COLLAPSES_PER_BLOCK && !FalloutCollapseManager.canSpawn(tick)) blockedWorkTick = tick;
						return;
					}
					columnStompComplete = true;
				}
				if(!ExplosionWorkScheduler.before(deadline)) return;
				if(biomes != null) {
					BiomeGenBase original = biomes.get(worldObj, x, z);
					BiomeGenBase biome = getBiomeChange(percent, scale, original);
					if(!ExplosionWorkScheduler.before(deadline)) return;
					if(biomes.setIfChanged(x, z, original, biome)) biomeModified = true;
				}
				columnCursor++; columnStarted = false; // Commit only after this column's effects finish.
			}
			if(columnCursor < 256 || !ExplosionWorkScheduler.before(deadline)) return;
			if(biomeModified) WorldUtil.syncBiomeChange(worldObj, chunkX << 4, chunkZ << 4, chunk);
			List<Long> queue = activeOuter ? outerChunksToProcess : chunksToProcess;
			queue.remove(queue.size() - 1); // Keep the active chunk queued until its sync completes.
			activeChunk = false;
		}
	}

	private static int highestOccupiedY(ExtendedBlockStorage[] sections) {
		for(int section = Math.min(15, sections.length - 1); section >= 0; section--) {
			if(sections[section] != null && !sections[section].isEmpty()) return Math.min(255, sections[section].getYLocation() + 15);
		}
		return -1;
	}

	private static Block blockAt(ExtendedBlockStorage[] sections, int x, int y, int z) {
		if(y < 0 || y >= 256) return Blocks.air;
		ExtendedBlockStorage section = sections[y >> 4];
		return section == null ? Blocks.air : section.getBlockByExtId(x & 15, y & 15, z & 15);
	}

	private static int metadataAt(ExtendedBlockStorage[] sections, int x, int y, int z) {
		if(y < 0 || y >= 256) return 0;
		ExtendedBlockStorage section = sections[y >> 4];
		return section == null ? 0 : section.getExtBlockMetadata(x & 15, y & 15, z & 15);
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

	/** Incremental square rings, clipped to the original horizontal circle. One pending chunk at a time. */
	private void gatherChunks(long deadline) {
		int scale = getScale(), centerX = (int)Math.floor(posX / 16D), centerZ = (int)Math.floor(posZ / 16D);
		int lastRing = (int)((scale + 15L) / 16L + 1L);
		double radiusSquared = (double)scale * scale;
		while(!gatherComplete && ExplosionWorkScheduler.before(deadline)) {
			if(gatherRing > lastRing) { gatherComplete = true; legacyChunks.clear(); return; }
			int r = gatherRing, step = gatherStep, dx = 0, dz = 0;
			if(r > 0) {
				if(step < 2 * r) { dx = -r + step; dz = -r; }
				else if(step < 4 * r) { dx = r; dz = -r + step - 2 * r; }
				else if(step < 6 * r) { dx = r - (step - 4 * r); dz = r; }
				else { dx = -r; dz = r - (step - 6 * r); }
			}
			if(++gatherStep >= (r == 0 ? 1 : 8 * r)) { gatherStep = 0; gatherRing++; }
			long cx = (long)centerX + dx, cz = (long)centerZ + dz;
			long minX = cx * 16L, minZ = cz * 16L;
			if(minX < -30000000L || minX >= 30000000L || minZ < -30000000L || minZ >= 30000000L) continue;
			double distanceX = Math.max(0D, Math.max(minX - posX, posX - (minX + 16L)));
			double distanceZ = Math.max(0D, Math.max(minZ - posZ, posZ - (minZ + 16L)));
			if(distanceX * distanceX + distanceZ * distanceZ > radiusSquared) continue;
			long coord = ChunkCoordIntPair.chunkXZ2Int((int)cx, (int)cz);
			if(legacyChunks.contains(coord)) continue;
			outerChunksToProcess.add(coord); // All newly enumerated chunks apply the per-column radius check.
			return;
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

	private boolean stomp(int x, int z, double dist, ExtendedBlockStorage[] sections, long deadline) {
		while(columnY >= 0) {
			if(columnDepth >= 2) return true;
			if(!ExplosionWorkScheduler.before(deadline)) return false;
			int y = columnY;
			ExtendedBlockStorage section = sections[y >> 4];
			if(section == null || section.isEmpty()) { columnY = (y >> 4 << 4) - 1; continue; }

			Block b = section.getBlockByExtId(x & 15, y & 15, z & 15);
			if(!ExplosionWorkScheduler.before(deadline)) return false;

			if(b.getMaterial() == Material.air || b == ModBlocks.fallout || b == ModBlocks.salted_fallout) {
				columnY--; continue;
			}
			// Reserve the worst-case group before any random effects or callbacks. Pausing
			// here leaves the Y/depth cursor untouched and never repeats a transformation.
			if(collapseCount > MAX_PENDING_COLLAPSES - MAX_COLLAPSES_PER_BLOCK) return false;
			// Once a block is selected, finish all its random effects and callbacks atomically.

			Block above = blockAt(sections, x, y + 1, z);
			int meta = section.getExtBlockMetadata(x & 15, y & 15, z & 15);

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
					blockAt(sections, x, y + 1, z).isAir(worldObj, x, y + 1, z)) {

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

				if(blockAt(sections, x, y - 1, z) == Blocks.air) {

					for(int i = 0; i <= columnDepth; i++) {

						queueCollapse(x, y + i, z, blockAt(sections, x, y + i, z), metadataAt(sections, x, y + i, z));
					}
				}
			}

			if(!transformed && b.isNormalCube()) {
				columnDepth++;
			}
			columnY--; // Advance only after effects and collapse queueing finish.
		}
		return true;
	}

	private static long collapsePosition(int x, int y, int z) {
		return ((long)x & 0x3FFFFFFL) << 38 | ((long)z & 0x3FFFFFFL) << 12 | (y & 4095);
	}

	private void queueCollapse(int x, int y, int z, Block block, int meta) {
		long position = collapsePosition(x, y, z);
		if(collapsePending.containsItem(position)) return;
		int index = (collapseHead + collapseCount) % MAX_PENDING_COLLAPSES;
		collapsePositions[index] = position; collapseBlocks[index] = block; collapseMetadata[index] = meta;
		collapsePending.add(position, Boolean.TRUE); collapseCount++;
	}

	private void removeCollapse() {
		collapsePending.remove(collapsePositions[collapseHead]);
		collapseBlocks[collapseHead] = null;
		collapseHead = (collapseHead + 1) % MAX_PENDING_COLLAPSES; collapseCount--;
	}

	private void processCollapses(long deadline, long tick) {
		while(collapseCount > 0 && ExplosionWorkScheduler.before(deadline)) {
			long position = collapsePositions[collapseHead];
			int x = (int)(position >> 38), y = (int)(position & 4095), z = (int)(position << 26 >> 38);
			Block block = collapseBlocks[collapseHead];
			int meta = collapseMetadata[collapseHead];
			// Do not remove the source here: the falling entity owns that on its first update.
			if(worldObj.getBlock(x, y, z) != block || worldObj.getBlockMetadata(x, y, z) != meta) { removeCollapse(); continue; }
			if(!ExplosionWorkScheduler.before(deadline) || !FalloutCollapseManager.canSpawn(tick)) return;
			EntityFallingBlockNT entity = new EntityFallingBlockNT(worldObj, x + 0.5D, y + 0.5D, z + 0.5D, block, meta);
			entity.markFalloutCollapse();
			if(!worldObj.spawnEntityInWorld(entity)) { blockedWorkTick = tick; return; }
			FalloutCollapseManager.spawned(entity);
			removeCollapse();
		}
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
		gatherRing = tag.getInteger("gatherRing"); gatherStep = tag.getInteger("gatherStep");
		// A completed old gather has only its saved remainder. Never re-enumerate it:
		// older saves do not retain the chunks whose effects have already finished.
		legacyGather = tag.hasKey("legacyGather") ? tag.getBoolean("legacyGather") : tag.getInteger("workVersion") < 4 && !gatherComplete;
		tickDelay = tag.hasKey("tickDelay") ? tag.getInteger("tickDelay") : BombConfig.fDelay;
		activeChunk = tag.getBoolean("activeChunk"); activeOuter = tag.getBoolean("activeOuter"); chunkCursor = tag.getLong("chunkCursor");
		columnCursor = tag.getInteger("columnCursor"); columnY = tag.hasKey("columnY") ? tag.getInteger("columnY") : 255;
		columnDepth = tag.getInteger("columnDepth"); biomeModified = tag.getBoolean("biomeModified");
		columnStarted = tag.getBoolean("columnStarted"); columnStompComplete = tag.getBoolean("columnStompComplete");
		chunksToProcess.clear(); outerChunksToProcess.clear();
		legacyChunks.clear(); collapsePending = new LongHashMap();
		Arrays.fill(collapseBlocks, null); collapseHead = collapseCount = 0;
		blockedWorkTick = Long.MIN_VALUE;
		restoring = tag; restoreQueue = restoreIndex = 0; delayTick = activeWorkTick = Long.MIN_VALUE;
	}

	private boolean restoreQueues(long deadline) {
		while(restoring != null && ExplosionWorkScheduler.before(deadline)) {
			String key = restoreQueue == 0 ? "chunks" : restoreQueue == 1 ? "outerChunks" : restoreQueue == 2 ? "legacyChunks" : "collapses";
			int[] data = restoring.getIntArray(key);
			int stride = restoreQueue == 3 ? 5 : 2;
			if(restoreIndex + stride > data.length) {
				restoreIndex = 0;
				if(++restoreQueue == 4) { restoring = null; legacyGather = false; }
				continue;
			}
			if(restoreQueue == 3) {
				queueCollapse(data[restoreIndex], data[restoreIndex + 1], data[restoreIndex + 2], Block.getBlockById(data[restoreIndex + 3]), data[restoreIndex + 4]);
			} else {
				long coord = ChunkCoordIntPair.chunkXZ2Int(data[restoreIndex], data[restoreIndex + 1]);
				if(restoreQueue < 2) (restoreQueue == 0 ? chunksToProcess : outerChunksToProcess).add(coord);
				if(!gatherComplete && (restoreQueue == 2 || legacyGather)) legacyChunks.add(coord);
			}
			restoreIndex += stride;
		}
		return restoring == null;
	}

	@Override
	protected void writeEntityToNBT(NBTTagCompound tag) {
		tag.setInteger("workVersion", 4); tag.setInteger("scale", getScale());
		tag.setBoolean("salt", salted); tag.setDouble("sourceMultiplier", sourceMultiplier);
		// No effects run during restore. Save the complete original queues with the
		// restored scalar cursors, without overwriting Entity's current base NBT fields.
		tag.setIntArray("chunks", restoring != null ? restoring.getIntArray("chunks") : writeChunksToIntArray(chunksToProcess));
		tag.setIntArray("outerChunks", restoring != null ? restoring.getIntArray("outerChunks") : writeChunksToIntArray(outerChunksToProcess));
		tag.setIntArray("legacyChunks", restoring != null ? restoring.getIntArray("legacyChunks") : writeChunksToIntArray(legacyChunks));
		tag.setIntArray("collapses", restoring != null ? restoring.getIntArray("collapses") : writeCollapses());
		tag.setBoolean("legacyGather", legacyGather);
		tag.setBoolean("gatherComplete", gatherComplete); tag.setInteger("gatherRing", gatherRing); tag.setInteger("gatherStep", gatherStep);
		tag.setInteger("tickDelay", tickDelay);
		tag.setBoolean("activeChunk", activeChunk); tag.setBoolean("activeOuter", activeOuter); tag.setLong("chunkCursor", chunkCursor);
		tag.setInteger("columnCursor", columnCursor); tag.setInteger("columnY", columnY); tag.setInteger("columnDepth", columnDepth);
		tag.setBoolean("biomeModified", biomeModified); tag.setBoolean("columnStarted", columnStarted); tag.setBoolean("columnStompComplete", columnStompComplete);
	}

	private int[] writeChunksToIntArray(Collection<Long> coords) {
		int[] data = new int[coords.size() * 2];
		int index = 0;
		for(long coord : coords) {
			data[index++] = (int)coord;
			data[index++] = (int)(coord >> 32);
		}
		return data;
	}

	private int[] writeCollapses() {
		int[] data = new int[collapseCount * 5];
		for(int i = 0; i < collapseCount; i++) {
			int index = (collapseHead + i) % MAX_PENDING_COLLAPSES;
			long position = collapsePositions[index];
			data[i * 5] = (int)(position >> 38);
			data[i * 5 + 1] = (int)(position & 4095);
			data[i * 5 + 2] = (int)(position << 26 >> 38);
			data[i * 5 + 3] = Block.getIdFromBlock(collapseBlocks[index]);
			data[i * 5 + 4] = collapseMetadata[index];
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
