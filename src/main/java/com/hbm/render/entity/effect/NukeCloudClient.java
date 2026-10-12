package com.hbm.render.entity.effect;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;

import org.lwjgl.opengl.GL11;

import com.hbm.config.ClientConfig;
import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.entity.effect.EntityNukeTorex.Cloudlet;
import com.hbm.entity.effect.EntityNukeTorex.TorexType;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;

/** Transient, client-thread-only visual ownership. No world entity scans or gameplay budgets. */
@SideOnly(Side.CLIENT)
public final class NukeCloudClient {

	private static final IdentityHashMap<EntityNukeTorex, State> states = new IdentityHashMap<>();
	private static final ArrayList<State> active = new ArrayList<>();
	private static World world;
	private static int total;
	private static int clientTick;
	private static long frame;
	private static volatile boolean disconnected;
	public static final Camera camera = new Camera();

	@SubscribeEvent public void join(EntityJoinWorldEvent event) {
		if(event.world.isRemote && event.entity instanceof EntityNukeTorex && !((EntityNukeTorex) event.entity).isContainedVisual()) {
			register((EntityNukeTorex) event.entity);
		}
	}

	@SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
		if(event.phase != TickEvent.Phase.START) return;
		World current = Minecraft.getMinecraft().theWorld;
		if(disconnected || current != world) { clear(); world = current; disconnected = false; }
		clientTick++;
		boolean changed = false;
		for(int i = active.size() - 1; i >= 0; i--) {
			EntityNukeTorex cloud = active.get(i).cloud;
			if(cloud.isDead || cloud.worldObj != current || current.getEntityByID(cloud.getEntityId()) != cloud) {
				release(cloud); changed = true;
			}
		}
		// Rebalance only registered clouds, including offscreen clouds. Tiny budgets rotate their remainder.
		if(changed || !active.isEmpty()) rebalance();
	}

	@SubscribeEvent public void renderTick(TickEvent.RenderTickEvent event) {
		if(event.phase == TickEvent.Phase.START) frame++;
	}

	@SubscribeEvent public void unload(WorldEvent.Unload event) {
		if(event.world.isRemote && event.world == world) { clear(); world = null; }
	}

	@SubscribeEvent public void disconnect(ClientDisconnectionFromServerEvent event) {
		// FML may dispatch this on the networking thread. Release references on the next client tick.
		disconnected = true;
	}

	private static void clear() {
		for(int i = 0; i < active.size(); i++) active.get(i).dispose();
		active.clear(); states.clear(); total = 0;
		camera.valid = false; camera.frame = -1; camera.view = null;
	}

	public static void release(EntityNukeTorex cloud) {
		State state = states.remove(cloud);
		if(state == null) return;
		total -= cloud.cloudlets.size();
		state.dispose(); active.remove(state);
	}

	private static State register(EntityNukeTorex cloud) {
		if(world != cloud.worldObj) { clear(); world = cloud.worldObj; }
		State state = states.get(cloud);
		if(state == null) {
			state = new State(cloud); states.put(cloud, state); active.add(state); rebalance();
		}
		return state;
	}

	private static void rebalance() {
		int size = active.size();
		if(size == 0) return;
		int maximum = ClientConfig.nukeCloudMaxTotalCloudlets.get();
		int perCloud = ClientConfig.nukeCloudMaxCloudlets.get();
		int base = maximum / size, remainder = maximum % size;
		int offset = maximum < size ? (clientTick / 20) % size : 0;
		for(int i = 0; i < size; i++) {
			State state = active.get(i);
			int quota = Math.min(perCloud, base + ((i - offset + size) % size < remainder ? 1 : 0));
			state.setQuota(quota);
		}
	}

	public static State beginTick(EntityNukeTorex cloud, boolean pressure) {
		State state = register(cloud);
		state.prepareTypes(pressure);
		state.updateDetail();
		return state;
	}

	public static State renderState(EntityNukeTorex cloud) { return states.get(cloud); }

	public static final class State {
		private final EntityNukeTorex cloud;
		private final int[] counts = new int[5];
		private final int[] quotas = new int[5];
		private final int[] sequence = new int[13];
		private final int[] retained = new int[5];
		private final int[] laneCounts = new int[8];
		private final int[] retainedLanes = new int[8];
		private int laneMask;
		private int typeMask = 1;
		private int quota;
		private int quotaEpoch = -1;
		private int detail;
		public Cloudlet[] order = new Cloudlet[0];
		private Cloudlet[] scratch = new Cloudlet[0];
		public int count;
		private int sorted;
		private int sortTick = Integer.MIN_VALUE;
		private long sortFrame = -1;
		private double sortX, sortY, sortZ, sortScale;
		private double sortOriginX, sortOriginY, sortOriginZ;
		private Entity sortView;
		private float boundsScale;
		private double boundsX, boundsY, boundsZ;
		public double minX, minY, minZ, maxX, maxY, maxZ;

		private State(EntityNukeTorex cloud) { this.cloud = cloud; }

		private void setQuota(int limit) {
			int epoch = clientTick / 20;
			if(quota == limit && (limit >= Integer.bitCount(typeMask) || quotaEpoch == epoch)) return;
			quotaEpoch = epoch;
			quota = limit; divideTypes(); trim();
			if(order.length > limit) {
				order = Arrays.copyOf(order, limit); scratch = new Cloudlet[limit];
			}
		}

		private void prepareTypes(boolean pressure) {
			int age = cloud.ticksExisted;
			int mask = pressure ? 1 : 16;
			if(pressure) {
				if(age < 150) mask |= 2;
				if(age < 195) mask |= 4;
				if(age > 195 && age < 900) mask |= 8;
			}
			for(int i = 0; i < 5; i++) if(counts[i] > 0) mask |= 1 << i;
			int lanes = pressure && age > 195 && age < 900 ? (age > 300 ? 255 : 15) : 0;
			for(int i = 0; i < 8; i++) if(laneCounts[i] > 0) lanes |= 1 << i;
			if(mask != typeMask || lanes != laneMask) { typeMask = mask; laneMask = lanes; divideTypes(); trim(); }
		}

		private void divideTypes() {
			// Reserve representation before assigning weighted density: main 4, shock 3, ring 1, condensation 2.
			int types = Integer.bitCount(typeMask), available = Math.max(0, quota - types);
			int weight = 0;
			for(int i = 0; i < 5; i++) if((typeMask & (1 << i)) != 0) weight += weight(i);
			Arrays.fill(quotas, 0);
			int assigned = 0;
			int offset = quota < types ? Math.floorMod(clientTick / 20 + cloud.getEntityId(), 5) : 0;
			for(int i = 0; i < 5 && assigned < quota; i++) {
				int type = (i + offset) % 5;
				if((typeMask & (1 << type)) == 0) continue;
				quotas[type] = 1 + (int)((long)available * weight(type) / weight); assigned += quotas[type];
			}
			for(int i = 0; assigned < quota; i = (i + 1) % 5) if((typeMask & (1 << i)) != 0) { quotas[i]++; assigned++; }
		}

		private static int weight(int type) { return type == 0 ? 4 : type == 1 ? 3 : type == 3 ? 2 : 1; }

		private void trim() {
			Arrays.fill(retained, 0);
			Arrays.fill(retainedLanes, 0);
			// Evenly retain samples of each type instead of cutting off one spatial/age band.
			for(int i = 0; i < cloud.cloudlets.size(); i++) {
				Cloudlet item = cloud.cloudlets.get(i);
				int type = item.type.ordinal();
				int index = type == 3 ? retainedLanes[item.visualLane]++ : retained[type]++;
				int size = type == 3 ? laneCounts[item.visualLane] : counts[type];
				int limit = type == 3 ? laneQuota(item.visualLane) : quotas[type];
				if(size > limit && (long)(index + 1) * limit / size == (long)index * limit / size) item.isDead = true;
			}
			finishTick();
		}

		private int laneQuota(int lane) {
			int lanes = Integer.bitCount(laneMask);
			if(lanes == 0 || (laneMask & (1 << lane)) == 0) return 0;
			int rank = Integer.bitCount(laneMask & ((1 << lane) - 1));
			return quotas[3] / lanes + (rank < quotas[3] % lanes ? 1 : 0);
		}

		private void updateDetail() {
			if(!camera.valid) return;
			double dx = camera.x - cloud.posX, dy = camera.y - cloud.posY, dz = camera.z - cloud.posZ;
			double extent = Math.max(32, Math.max(cloud.coreHeight + cloud.rollerSize, cloud.torusWidth + cloud.rollerSize * 6) * cloud.getScale());
			if(count > 0) {
				double bx = Math.max(Math.abs(minX - cloud.posX), Math.abs(maxX - cloud.posX));
				double by = Math.max(Math.abs(minY - cloud.posY), Math.abs(maxY - cloud.posY));
				double bz = Math.max(Math.abs(minZ - cloud.posZ), Math.abs(maxZ - cloud.posZ));
				extent = Math.max(extent, Math.sqrt(bx * bx + by * by + bz * bz));
			}
			double distance = Math.max(0, Math.sqrt(dx * dx + dy * dy + dz * dz) - extent) / Math.max(128, extent * 2);
			// Hysteresis around size-relative thresholds 1, 2 and 4. Birth thinning is nested and deterministic.
			while(detail < 3 && distance > (1 << detail) * 1.15) detail++;
			while(detail > 0 && distance < (1 << (detail - 1)) * 0.85) detail--;
		}

		public boolean allowBirth(TorexType kind, int births, int lifetime) {
			return allowBirth(kind, births, lifetime, 0);
		}

		public boolean allowBirth(TorexType kind, int births, int lifetime, int lane) {
			int type = kind.ordinal();
			// Separate streams preserve both condensation rings and all four height bands at low density.
			int index = sequence[type == 3 ? 5 + lane : type]++;
			double density = Math.min(1D, quotas[type] / (double)Math.max(1L, (long)births * lifetime)) / (1 << detail);
			// Low discrepancy selection, independent of RNG and render frame count. No deferred births.
			double sample = (Integer.reverse(index) & 0xffffffffL) * 0x1.0p-32;
			return sample < density && counts[type] < quotas[type] && (type != 3 || laneCounts[lane] < laneQuota(lane)) && count < quota && total < ClientConfig.nukeCloudMaxTotalCloudlets.get();
		}

		public void add(Cloudlet item) {
			if(count == order.length) {
				int capacity = (int)Math.min(quota, Math.max((long)count + 1, Math.max(16L, (long)count * 2)));
				order = Arrays.copyOf(order, capacity); scratch = new Cloudlet[capacity];
			}
			order[count++] = item; cloud.cloudlets.add(item); counts[item.type.ordinal()]++; total++;
			if(item.type == TorexType.CONDENSATION) laneCounts[item.visualLane]++;
		}

		public void finishTick() {
			int write = 0;
			for(int i = 0; i < cloud.cloudlets.size(); i++) {
				Cloudlet item = cloud.cloudlets.get(i);
				if(item.isDead) {
					counts[item.type.ordinal()]--; total--;
					if(item.type == TorexType.CONDENSATION) laneCounts[item.visualLane]--;
				}
				else cloud.cloudlets.set(write++, item);
			}
			while(cloud.cloudlets.size() > write) cloud.cloudlets.remove(cloud.cloudlets.size() - 1);
			int oldCount = count, oldSorted = sorted; write = 0; sorted = 0;
			minX = minY = minZ = Double.POSITIVE_INFINITY;
			maxX = maxY = maxZ = Double.NEGATIVE_INFINITY;
			float scale = (float)cloud.getScale();
			boundsScale = scale; boundsX = cloud.posX; boundsY = cloud.posY; boundsZ = cloud.posZ;
			for(int i = 0; i < oldCount; i++) {
				Cloudlet item = order[i];
				if(item.isDead) continue;
				order[write++] = item; if(i < oldSorted) sorted++;
				double radius = item.getScale(scale) * 2D; // Covers any billboard orientation, including interpolation.
				double x = item.getInterpX(0, scale), y = item.getInterpY(0, scale), z = item.getInterpZ(0, scale);
				double nx = item.getInterpX(1, scale), ny = item.getInterpY(1, scale), nz = item.getInterpZ(1, scale);
				minX = Math.min(minX, Math.min(x, nx) - radius); maxX = Math.max(maxX, Math.max(x, nx) + radius);
				minY = Math.min(minY, Math.min(y, ny) - radius); maxY = Math.max(maxY, Math.max(y, ny) + radius);
				minZ = Math.min(minZ, Math.min(z, nz) - radius); maxZ = Math.max(maxZ, Math.max(z, nz) + radius);
			}
			count = write; Arrays.fill(order, write, oldCount, null);
		}

		public boolean boundsCurrent(float scale) {
			return boundsScale == scale && boundsX == cloud.posX && boundsY == cloud.posY && boundsZ == cloud.posZ;
		}

		public void refreshOrder(float interp) {
			if(sortFrame == frame || count == 0 || !camera.valid) return;
			double dx = camera.x - sortX, dy = camera.y - sortY, dz = camera.z - sortZ;
			boolean full = sortTick == Integer.MIN_VALUE || cloud.ticksExisted - sortTick >= 2 ||
				dx * dx + dy * dy + dz * dz > 64 || sortView != camera.view || sortScale != cloud.getScale() ||
				sortOriginX != cloud.posX || sortOriginY != cloud.posY || sortOriginZ != cloud.posZ;
			int start = full ? 0 : sorted;
			if(!full && start == count) return;
			sortFrame = frame;
			if(full) {
				sortX = camera.x; sortY = camera.y; sortZ = camera.z; sortView = camera.view; sortScale = cloud.getScale(); sortTick = cloud.ticksExisted;
				sortOriginX = cloud.posX; sortOriginY = cloud.posY; sortOriginZ = cloud.posZ;
			}
			float scale = (float)sortScale;
			for(int i = start; i < count; i++) {
				Cloudlet item = order[i];
				dx = item.getInterpX(interp, scale) - sortX; dy = item.getInterpY(interp, scale) - sortY; dz = item.getInterpZ(interp, scale) - sortZ;
				item.sortDistance = dx * dx + dy * dy + dz * dz;
			}
			mergeSort(start, count);
			if(start > 0) merge(0, start, count); // Insert new membership using the cached camera/keys, without a full sort.
			sorted = count;
		}

		private void mergeSort(int from, int to) {
			if(to - from < 2) return;
			int middle = (from + to) >>> 1;
			mergeSort(from, middle); mergeSort(middle, to); merge(from, middle, to);
		}

		private void merge(int from, int middle, int to) {
			int left = from, right = middle;
			for(int i = from; i < to; i++) {
				if(right == to || left < middle && before(order[left], order[right])) scratch[i] = order[left++];
				else scratch[i] = order[right++];
			}
			System.arraycopy(scratch, from, order, from, to - from);
			Arrays.fill(scratch, from, to, null);
		}

		private static boolean before(Cloudlet a, Cloudlet b) {
			int comparison = Double.compare(b.sortDistance, a.sortDistance);
			return comparison < 0 || comparison == 0 && a.identity < b.identity;
		}

		private void dispose() {
			cloud.cloudlets = new ArrayList<>(); order = new Cloudlet[0]; scratch = new Cloudlet[0]; count = sorted = 0; sortView = null;
		}
	}

	/** Camera and clipping planes from the actual GL view, before the cloud's local translation. */
	public static final class Camera {
		private final FloatBuffer model = GLAllocation.createDirectFloatBuffer(16);
		private final FloatBuffer projection = GLAllocation.createDirectFloatBuffer(16);
		private final double[] clip = new double[16];
		private final double[][] planes = new double[6][4];
		private long frame = -1;
		private int pass;
		private double originX, originY, originZ;
		public double x, y, z;
		public boolean valid;
		public Entity view;

		public void capture() {
			int currentPass = MinecraftForgeClient.getRenderPass();
			if(frame == NukeCloudClient.frame && pass == currentPass) return;
			frame = NukeCloudClient.frame; pass = currentPass;
			GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, model); GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
			originX = RenderManager.renderPosX; originY = RenderManager.renderPosY; originZ = RenderManager.renderPosZ;
			// Invert the affine view's 3x3 part, retaining camera mods' translations, rotation and scale.
			double a = model.get(0), b = model.get(4), c = model.get(8), d = model.get(1), e = model.get(5), f = model.get(9), g = model.get(2), h = model.get(6), i = model.get(10);
			double determinant = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
			valid = Double.isFinite(determinant) && Math.abs(determinant) > 1E-12;
			view = Minecraft.getMinecraft().renderViewEntity;
			if(!valid) return; // Unknown/singular view: keep geometry and previous detail, never cull it.
			double tx = -model.get(12), ty = -model.get(13), tz = -model.get(14);
			x = originX + ((e * i - f * h) * tx + (c * h - b * i) * ty + (b * f - c * e) * tz) / determinant;
			y = originY + ((f * g - d * i) * tx + (a * i - c * g) * ty + (c * d - a * f) * tz) / determinant;
			z = originZ + ((d * h - e * g) * tx + (b * g - a * h) * ty + (a * e - b * d) * tz) / determinant;
			for(int column = 0; column < 4; column++) for(int row = 0; row < 4; row++) {
				double value = 0;
				for(int k = 0; k < 4; k++) value += projection.get(k * 4 + row) * model.get(column * 4 + k);
				clip[column * 4 + row] = value;
			}
			for(int plane = 0; plane < 6; plane++) for(int component = 0; component < 4; component++) {
				planes[plane][component] = clip[component * 4 + 3] + (plane % 2 == 0 ? 1 : -1) * clip[component * 4 + plane / 2];
			}
		}

		public boolean visible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
			if(!valid) return true;
			for(int i = 0; i < 6; i++) {
				double[] p = planes[i];
				if(p[0] * ((p[0] >= 0 ? maxX : minX) - originX) + p[1] * ((p[1] >= 0 ? maxY : minY) - originY) + p[2] * ((p[2] >= 0 ? maxZ : minZ) - originZ) + p[3] < 0) return false;
			}
			return true;
		}
	}
}
