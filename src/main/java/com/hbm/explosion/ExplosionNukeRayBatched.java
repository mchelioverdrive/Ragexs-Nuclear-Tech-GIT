package com.hbm.explosion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.hbm.util.fauxpointtwelve.BlockPos;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

public class ExplosionNukeRayBatched {
	public HashMap<ChunkCoordIntPair, List<FloatTriplet>> perChunk = new HashMap<ChunkCoordIntPair, List<FloatTriplet>>();
	public List<ChunkCoordIntPair> orderedChunks = new LinkedList<ChunkCoordIntPair>();
	// Build distance buckets incrementally, then drain them one coordinate at a time.
	private final TreeMap<Integer, LinkedList<ChunkCoordIntPair>> ordering = new TreeMap<Integer, LinkedList<ChunkCoordIntPair>>();
	int posX, posY, posZ;
	World world;
	int strength, length;
	private boolean contained;
	private int surfaceY = 256;
	int gspNumMax, gspNum;
	double gspX, gspY;
	public boolean isAusf3Complete;

	private boolean rayActive, rayFinishing;
	private double rayX, rayY, rayZ;
	private int rayStep;
	private float rayStrength;
	private FloatTriplet lastPos;
	private final HashSet<ChunkCoordIntPair> rayChunks = new HashSet<ChunkCoordIntPair>();
	private Iterator<ChunkCoordIntPair> rayChunkIterator;
	private long collectionTick = Long.MIN_VALUE;
	private int raysThisTick;

	private ChunkCoordIntPair removalChunk;
	private int removalRay, removalStep = -1;
	private boolean removalInChunk, reconstructed;
	private final HashSet<BlockPos> toRem = new HashSet<BlockPos>();
	private final HashSet<BlockPos> toRemTips = new HashSet<BlockPos>();
	private Iterator<BlockPos> removalIterator;

	private NBTTagCompound restoring;
	private int restorePhase, restoreIndex, restorePoint;
	private HashSet<ChunkCoordIntPair> restoredOrder;

	public ExplosionNukeRayBatched(World world, int x, int y, int z, int strength, int speed, int length) {
		this(world, x, y, z, strength, speed, length, false, 256);
	}
	public ExplosionNukeRayBatched(World world, int x, int y, int z, int strength, int speed, int length, boolean contained, int surfaceY) {
		this.world = world; posX = x; posY = y; posZ = z;
		this.strength = strength; this.length = length; this.contained = contained; this.surfaceY = surfaceY;
		gspNumMax = (int)(2.5 * Math.PI * Math.pow(strength, 2));
		gspNum = 1; gspX = Math.PI;
	}

	private void generateGspUp() {
		if(gspNum < gspNumMax) {
			int k = gspNum + 1;
			double hk = -1.0 + 2.0 * (k - 1.0) / (gspNumMax - 1.0);
			gspX = Math.acos(hk);
			double lon = gspY + 3.6 / Math.sqrt(gspNumMax) / Math.sqrt(1.0 - hk * hk);
			gspY = lon % (Math.PI * 2);
		} else { gspX = 0.0; gspY = 0.0; }
		gspNum++;
	}

	public boolean hasPendingWork() { return restoring != null || !isAusf3Complete || !perChunk.isEmpty(); }
	public boolean canWork(long tick, int count) {
		return restoring != null || isAusf3Complete || gspNum > gspNumMax || collectionTick != tick || raysThisTick < count;
	}
	private int distance(ChunkCoordIntPair coord) {
		return Math.abs((posX >> 4) - coord.chunkXPos) + Math.abs((posZ >> 4) - coord.chunkZPos);
	}
	private void queueOrder(ChunkCoordIntPair coord) {
		int distance = distance(coord);
		LinkedList<ChunkCoordIntPair> bucket = ordering.get(distance);
		if(bucket == null) { bucket = new LinkedList<ChunkCoordIntPair>(); ordering.put(distance, bucket); }
		bucket.addLast(coord);
	}

	/** Legacy API retained for callers outside the governed entity path. */
	public void collectTip(int count) { collectTip(count, Long.MAX_VALUE, collectionTick + 1); }
	public void collectTip(int count, long deadline, long tick) {
		if(!restore(deadline) || isAusf3Complete) return;
		if(collectionTick != tick) { collectionTick = tick; raysThisTick = 0; }
		while(gspNum <= gspNumMax && raysThisTick < count && ExplosionWorkScheduler.before(deadline)) {
			if(!rayActive) {
				rayX = Math.sin(gspX) * Math.cos(gspY); rayZ = Math.sin(gspX) * Math.sin(gspY); rayY = Math.cos(gspX);
				rayStep = 0; rayStrength = strength; lastPos = null; rayActive = true; rayFinishing = false;
			}
			int rayLength = (int)Math.ceil(strength);
			while(!rayFinishing && ExplosionWorkScheduler.before(deadline)) {
				if(rayStep >= rayLength || rayStep > length) { rayFinishing = true; break; }
				float x = (float)(posX + rayX * rayStep), y = (float)(posY + rayY * rayStep), z = (float)(posZ + rayZ * rayStep);
				if(contained && y >= surfaceY) { rayFinishing = true; break; }
				int ix = (int)Math.floor(x), iy = (int)Math.floor(y), iz = (int)Math.floor(z);
				double fac = (100 - (double)rayStep / rayLength * 100) * 0.07D;
				// A chunk load/resistance callback is indivisible; commit this step before yielding.
				Block block = world.getBlock(ix, iy, iz);
				if(!ExplosionWorkScheduler.before(deadline)) return;
				if(!block.getMaterial().isLiquid()) rayStrength -= Math.pow(masqueradeResistance(block), 7.5D - fac);
				if(rayStrength > 0 && block != Blocks.air && !block.getMaterial().isLiquid()) {
					lastPos = new FloatTriplet(x, y, z);
					rayChunks.add(new ChunkCoordIntPair(ix >> 4, iz >> 4));
				}
				if(rayStrength <= 0 || rayStep + 1 >= length || rayStep == rayLength - 1) rayFinishing = true;
				rayStep++;
			}
			if(!rayFinishing) return;
			if(rayChunkIterator == null) rayChunkIterator = rayChunks.iterator();
			while(rayChunkIterator.hasNext() && ExplosionWorkScheduler.before(deadline)) {
				ChunkCoordIntPair coord = rayChunkIterator.next();
				List<FloatTriplet> points = perChunk.get(coord);
				if(points == null) { points = new ArrayList<FloatTriplet>(); perChunk.put(coord, points); queueOrder(coord); }
				points.add(lastPos);
				rayChunkIterator.remove(); // NBT retains only endpoints not yet appended.
			}
			if(rayChunkIterator.hasNext()) return;
			rayChunkIterator = null; rayActive = false; rayFinishing = false; lastPos = null;
			generateGspUp(); raysThisTick++;
		}
		if(gspNum > gspNumMax) finalizeOrder(deadline);
	}

	private void finalizeOrder(long deadline) {
		while(!ordering.isEmpty() && ExplosionWorkScheduler.before(deadline)) {
			Map.Entry<Integer, LinkedList<ChunkCoordIntPair>> entry = ordering.firstEntry();
			orderedChunks.add(entry.getValue().removeFirst());
			if(entry.getValue().isEmpty()) ordering.remove(entry.getKey());
		}
		if(ordering.isEmpty()) isAusf3Complete = true;
	}

	public void processChunk() { processChunk(Long.MAX_VALUE); }
	public void processChunk(long deadline) {
		if(!restore(deadline) || !isAusf3Complete || perChunk.isEmpty() || !ExplosionWorkScheduler.before(deadline)) return;
		if(removalChunk == null) {
			removalChunk = orderedChunks.get(0); removalRay = 0; removalStep = -1; reconstructed = false;
		}
		List<FloatTriplet> points = perChunk.get(removalChunk);
		int chunkX = removalChunk.chunkXPos, chunkZ = removalChunk.chunkZPos;
		int enter = Math.max(0, Math.min(Math.abs(posX - (chunkX << 4)), Math.abs(posZ - (chunkZ << 4))) - 16);
		while(!reconstructed && removalRay < points.size() && ExplosionWorkScheduler.before(deadline)) {
			FloatTriplet tip = points.get(removalRay);
			Vec3 vec = Vec3.createVectorHelper(tip.xCoord - posX, tip.yCoord - posY, tip.zCoord - posZ);
			double len = vec.lengthVector(), dx = vec.xCoord / len, dy = vec.yCoord / len, dz = vec.zCoord / len;
			int tipX = (int)Math.floor(tip.xCoord), tipY = (int)Math.floor(tip.yCoord), tipZ = (int)Math.floor(tip.zCoord);
			if(removalStep < 0) { removalStep = enter; removalInChunk = false; }
			while(removalStep < len && ExplosionWorkScheduler.before(deadline)) {
				int x = (int)Math.floor(posX + dx * removalStep), y = (int)Math.floor(posY + dy * removalStep), z = (int)Math.floor(posZ + dz * removalStep);
				if(x >> 4 != chunkX || z >> 4 != chunkZ) {
					if(removalInChunk) { removalStep = (int)Math.ceil(len); break; }
					removalStep++; continue;
				}
				removalInChunk = true;
				Block block = world.getBlock(x, y, z);
				if(!ExplosionWorkScheduler.before(deadline)) return;
				if(!world.isAirBlock(x, y, z) && !block.getMaterial().isLiquid()) {
					BlockPos pos = new BlockPos(x, y, z);
					if(x == tipX && y == tipY && z == tipZ) toRemTips.add(pos);
					toRem.add(pos);
				}
				removalStep++;
			}
			if(removalStep < len) return;
			removalRay++; removalStep = -1;
		}
		if(removalRay == points.size()) reconstructed = true;
		if(!reconstructed) return;
		if(removalIterator == null) removalIterator = toRem.iterator();
		while(removalIterator.hasNext() && ExplosionWorkScheduler.before(deadline)) {
			BlockPos pos = removalIterator.next();
			if(toRemTips.contains(pos)) handleTip(pos.getX(), pos.getY(), pos.getZ());
			else world.setBlock(pos.getX(), pos.getY(), pos.getZ(), Blocks.air, 0, 2);
			// Callbacks and tile cleanup finish before advancing persisted removal progress.
			removalIterator.remove(); toRemTips.remove(pos);
		}
		if(removalIterator.hasNext()) return;
		perChunk.remove(removalChunk); orderedChunks.remove(0);
		removalChunk = null; removalIterator = null; reconstructed = false;
	}

	protected void handleTip(int x, int y, int z) { world.setBlock(x, y, z, Blocks.air, 0, 3); }
	public static float masqueradeResistance(Block block) {
		if(block == Blocks.sandstone) return Blocks.stone.getExplosionResistance(null);
		if(block == Blocks.obsidian) return Blocks.stone.getExplosionResistance(null) * 3;
		return block.getExplosionResistance(null);
	}
	public class CoordComparator implements Comparator<ChunkCoordIntPair> {
		public int compare(ChunkCoordIntPair a, ChunkCoordIntPair b) { return distance(a) - distance(b); }
	}
	public class FloatTriplet {
		public float xCoord, yCoord, zCoord;
		public FloatTriplet(float x, float y, float z) { xCoord = x; yCoord = y; zCoord = z; }
	}

	/** A partially decoded save remains authoritative until decoding completes. */
	public void readFromNBT(NBTTagCompound tag) {
		restoring = tag; restorePhase = restoreIndex = restorePoint = 0;
		perChunk.clear(); orderedChunks.clear(); ordering.clear(); restoredOrder = new HashSet<ChunkCoordIntPair>(); rayChunks.clear(); toRem.clear(); toRemTips.clear();
		rayChunkIterator = null; removalIterator = null;
		gspNum = tag.getInteger("gspNum"); gspX = tag.getDouble("gspX"); gspY = tag.getDouble("gspY");
		isAusf3Complete = false;
		if(tag.hasKey("contained")) contained = tag.getBoolean("contained");
		if(tag.hasKey("surfaceY")) surfaceY = tag.getInteger("surfaceY");
		rayActive = tag.getBoolean("rayActive"); rayFinishing = tag.getBoolean("rayFinishing");
		rayX = tag.getDouble("rayX"); rayY = tag.getDouble("rayY"); rayZ = tag.getDouble("rayZ");
		rayStep = tag.getInteger("rayStep"); rayStrength = tag.getFloat("rayStrength");
		lastPos = tag.hasKey("lastPos") ? readPoint(tag.getCompoundTag("lastPos")) : null;
		removalChunk = tag.hasKey("removalChunk") ? readCoord(tag.getCompoundTag("removalChunk")) : null;
		removalRay = tag.getInteger("removalRay"); removalStep = tag.hasKey("removalStep") ? tag.getInteger("removalStep") : -1;
		removalInChunk = tag.getBoolean("removalInChunk"); reconstructed = tag.getBoolean("reconstructed");
		collectionTick = Long.MIN_VALUE; raysThisTick = 0;
	}

	private boolean restore(long deadline) {
		while(restoring != null && ExplosionWorkScheduler.before(deadline)) {
			String key = restorePhase == 0 ? "ordered" : restorePhase == 1 ? "chunks" : restorePhase == 2 ? "rayChunks" : restorePhase == 3 ? "remaining" : "tips";
			NBTTagList list = restoring.getTagList(key, 10);
			if(restoreIndex >= list.tagCount()) {
				restoreIndex = restorePoint = 0;
				if(++restorePhase == 5) {
					boolean complete = restoring.getBoolean("complete");
					restoring = null; restoredOrder = null;
					// Old complete saves lacked an ordered queue; rebuild distance order in slices.
					if(complete && ordering.isEmpty()) isAusf3Complete = true;
				}
				continue;
			}
			NBTTagCompound item = list.getCompoundTagAt(restoreIndex);
			if(restorePhase == 1) {
				ChunkCoordIntPair coord = readCoord(item);
				List<FloatTriplet> points = perChunk.get(coord);
				if(points == null) {
					points = new ArrayList<FloatTriplet>(); perChunk.put(coord, points);
					if(!restoredOrder.contains(coord)) queueOrder(coord);
				}
				NBTTagList savedPoints = item.getTagList("points", 10);
				if(restorePoint < savedPoints.tagCount()) { points.add(readPoint(savedPoints.getCompoundTagAt(restorePoint++))); continue; }
				restorePoint = 0;
			} else if(restorePhase == 0) {
				ChunkCoordIntPair coord = readCoord(item); orderedChunks.add(coord); restoredOrder.add(coord);
			} else if(restorePhase == 2) rayChunks.add(readCoord(item));
			else {
				BlockPos pos = new BlockPos(item.getInteger("x"), item.getInteger("y"), item.getInteger("z"));
				if(restorePhase == 3) toRem.add(pos); else toRemTips.add(pos);
			}
			restoreIndex++;
		}
		return restoring == null;
	}

	public void writeToNBT(NBTTagCompound tag) {
		if(restoring != null) {
			for(Object key : restoring.func_150296_c()) tag.setTag((String)key, restoring.getTag((String)key).copy());
			return;
		}
		tag.setInteger("workVersion", 2);
		tag.setInteger("gspNum", gspNum); tag.setDouble("gspX", gspX); tag.setDouble("gspY", gspY);
		tag.setBoolean("complete", isAusf3Complete); tag.setBoolean("contained", contained); tag.setInteger("surfaceY", surfaceY);
		tag.setBoolean("rayActive", rayActive); tag.setBoolean("rayFinishing", rayFinishing);
		tag.setDouble("rayX", rayX); tag.setDouble("rayY", rayY); tag.setDouble("rayZ", rayZ);
		tag.setInteger("rayStep", rayStep); tag.setFloat("rayStrength", rayStrength);
		if(lastPos != null) tag.setTag("lastPos", writePoint(lastPos));
		tag.setTag("rayChunks", writeCoords(rayChunks)); tag.setTag("ordered", writeCoords(orderedChunks));
		NBTTagList chunks = new NBTTagList();
		for(Map.Entry<ChunkCoordIntPair, List<FloatTriplet>> entry : perChunk.entrySet()) {
			NBTTagCompound chunk = writeCoord(entry.getKey()); NBTTagList points = new NBTTagList();
			for(FloatTriplet point : entry.getValue()) points.appendTag(writePoint(point));
			chunk.setTag("points", points); chunks.appendTag(chunk);
		}
		tag.setTag("chunks", chunks);
		if(removalChunk != null) tag.setTag("removalChunk", writeCoord(removalChunk));
		tag.setInteger("removalRay", removalRay); tag.setInteger("removalStep", removalStep);
		tag.setBoolean("removalInChunk", removalInChunk); tag.setBoolean("reconstructed", reconstructed);
		tag.setTag("remaining", writePositions(toRem)); tag.setTag("tips", writePositions(toRemTips));
	}
	private static ChunkCoordIntPair readCoord(NBTTagCompound tag) { return new ChunkCoordIntPair(tag.getInteger("x"), tag.getInteger("z")); }
	private static NBTTagCompound writeCoord(ChunkCoordIntPair coord) {
		NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("x", coord.chunkXPos); tag.setInteger("z", coord.chunkZPos); return tag;
	}
	private static NBTTagList writeCoords(Iterable<ChunkCoordIntPair> coords) {
		NBTTagList list = new NBTTagList(); for(ChunkCoordIntPair coord : coords) list.appendTag(writeCoord(coord)); return list;
	}
	private FloatTriplet readPoint(NBTTagCompound tag) { return new FloatTriplet(tag.getFloat("x"), tag.getFloat("y"), tag.getFloat("z")); }
	private static NBTTagCompound writePoint(FloatTriplet point) {
		NBTTagCompound tag = new NBTTagCompound(); tag.setFloat("x", point.xCoord); tag.setFloat("y", point.yCoord); tag.setFloat("z", point.zCoord); return tag;
	}
	private static NBTTagList writePositions(Iterable<BlockPos> positions) {
		NBTTagList list = new NBTTagList();
		for(BlockPos pos : positions) {
			NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("x", pos.getX()); tag.setInteger("y", pos.getY()); tag.setInteger("z", pos.getZ()); list.appendTag(tag);
		}
		return list;
	}
}
