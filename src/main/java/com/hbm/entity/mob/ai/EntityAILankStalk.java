package com.hbm.entity.mob.ai;

import java.util.HashMap;
import java.util.Map;

import com.hbm.config.EuropaLankConfig;
import com.hbm.entity.mob.EntityFRIEND;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public final class EntityAILankStalk extends EntityAIBase {

	private final EntityFRIEND lank;
	private final World world;
	private final Map<ChunkCoordinates, Long> rejected = new HashMap<>();
	private EntityPlayer target;
	private ChunkCoordinates destination;
	private long travelUntil;
	private long nextDecision;
	private long nextSearch;
	private long nextRetreatSearch;
	private EntityPlayer retreatFrom;

	public EntityAILankStalk(EntityFRIEND lank) {
		this.lank = lank;
		this.world = lank.worldObj;
		setMutexBits(3);
	}

	public EntityPlayer getTarget() {
		return target;
	}

	public void setTarget(EntityPlayer player) {
		target = player;
	}

	@Override
	public boolean shouldExecute() {
		return EuropaLankConfig.settings.passiveStalking && !lank.isDefending();
	}

	@Override
	public void resetTask() {
		stopMoving();
	}

	@Override
	public void updateTask() {
		if(target != null) lank.getLookHelper().setLookPositionWithEntity(target, 30F, 30F);
		long now = world.getTotalWorldTime();
		if(now < nextDecision) return;
		nextDecision = now + 20;
		EuropaLankConfig.Values settings = EuropaLankConfig.settings;

		double range = settings.maximumStalkingDistance + 32D;
		if(target != null && (target.worldObj != world || !LankTerrain.isTarget(target)
				|| lank.getDistanceSqToEntity(target) > range * range)) {
			target = null;
			stopMoving();
		}
		if(target == null) target = findNearest(range, true);
		EntityPlayer threat = findNearest(settings.retreatDistance, false);
		if(threat == null && target == null) return;

		if(destination != null) {
			if(threat != null && threat != retreatFrom) {
				stopMoving();
			} else if(!lank.getNavigator().noPath() && now < travelUntil && routeStillLoaded()) {
				return;
			} else {
				boolean reached = lank.getDistanceSq(destination.posX + 0.5D, destination.posY, destination.posZ + 0.5D) < 4D;
				if(!reached) rejected.put(destination, now + 300);
				stopMoving();
				if(reached && threat == null) nextSearch = now + 100 + lank.getRNG().nextInt(100);
			}
		}
		if(now < (threat == null ? nextSearch : nextRetreatSearch)) return;
		double distance = target == null ? 0D : lank.getDistanceToEntity(target);
		if(threat == null && distance >= settings.minimumStalkingDistance && distance <= settings.maximumStalkingDistance) return;
		if(threat == null) nextSearch = now + 80;
		else nextRetreatSearch = now + 40;
		rejected.values().removeIf(expiration -> expiration <= now);
		if(!lank.onGround || !LankTerrain.canUseNavigator(lank)) return;

		ChunkCoordinates candidate = choosePosition(threat, settings);
		if(candidate == null) return;
		PathEntity path = lank.getNavigator().getPathToXYZ(candidate.posX, candidate.posY, candidate.posZ);
		PathPoint end = path == null ? null : path.getFinalPathPoint();
		if(end == null || end.xCoord != candidate.posX || end.yCoord != candidate.posY || end.zCoord != candidate.posZ
				|| !lank.getNavigator().setPath(path, threat == null ? 0.85D : 1.2D)) {
			rejected.put(candidate, now + 300);
			return;
		}
		destination = candidate;
		travelUntil = now + 180;
		retreatFrom = threat;
	}

	private EntityPlayer findNearest(double range, boolean claim) {
		double best = range * range;
		EntityPlayer closest = null;
		for(Object entry : world.playerEntities) {
			EntityPlayer player = (EntityPlayer) entry;
			if(!LankTerrain.isTarget(player)) continue;
			double distance = lank.getDistanceSqToEntity(player);
			if(distance < best && (!claim || EntityFRIEND.isUnclaimed(player, lank))) {
				best = distance;
				closest = player;
			}
		}
		return closest;
	}

	private ChunkCoordinates choosePosition(EntityPlayer threat, EuropaLankConfig.Values settings) {
		EntityPlayer reference = threat == null ? target : threat;
		double desired = threat != null ? settings.minimumStalkingDistance
				: (settings.minimumStalkingDistance + settings.maximumStalkingDistance) * 0.5D;
		double currentDistance = lank.getDistanceToEntity(reference);
		double angle = lank.getRNG().nextDouble() * Math.PI * 2D;
		int y = MathHelper.floor_double(lank.boundingBox.minY + 0.5D);
		ChunkCoordinates selected = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for(int attempt = 0; attempt < 8; attempt++, angle += Math.PI / 4D) {
			double step = 6D + lank.getRNG().nextInt(7);
			int x = MathHelper.floor_double(lank.posX + Math.cos(angle) * step);
			int z = MathHelper.floor_double(lank.posZ + Math.sin(angle) * step);
			ChunkCoordinates spot = LankTerrain.findFooting(lank, x, y, z, 3, false);
			if(spot == null || rejected.containsKey(spot)) continue;
			double distance = reference.getDistance(x + 0.5D, spot.posY, z + 0.5D);
			if(threat != null && distance <= currentDistance + 3D) continue;
			if(threat == null && (distance <= settings.retreatDistance
					|| Math.abs(distance - desired) >= Math.abs(currentDistance - desired) - 1D)) continue;
			double midY = spot.posY + lank.height * 0.5D;
			double score = threat != null ? distance : -Math.abs(distance - desired);
			score += LankTerrain.occluded(reference, x + 0.5D, midY, z + 0.5D) ? 4D
					: -2D * Math.max(0D, LankTerrain.lookDot(reference, x + 0.5D, midY, z + 0.5D));
			if(score > bestScore) {
				bestScore = score;
				selected = spot;
			}
		}
		return selected;
	}

	private boolean routeStillLoaded() {
		int x = MathHelper.floor_double(lank.posX), z = MathHelper.floor_double(lank.posZ);
		return LankTerrain.loadedBetween(world, x, z, destination.posX, destination.posZ, 2);
	}

	private void stopMoving() {
		destination = null;
		retreatFrom = null;
		lank.getNavigator().clearPathEntity();
	}
}
