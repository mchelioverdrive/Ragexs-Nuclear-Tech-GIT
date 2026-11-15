package com.hbm.world.gemini;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.EntityWoodcrawler;
import com.hbm.main.MainRegistry;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.BiomeGenForest;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

public final class WoodcrawlerSpawning {

	// Scoped to synchronous scripted callbacks; never saved, and removed even on a veto.
	private static final Set<EntityWoodcrawler> INVASION = Collections.newSetFromMap(new IdentityHashMap<EntityWoodcrawler, Boolean>());
	private static final Map<World, Map<String, Long>> DIAGNOSTICS = new WeakHashMap<World, Map<String, Long>>();

	public static boolean isForest(BiomeGenBase biome) {
		// Vanilla Flower Forest is the registered forest mutation (ID 4 + 128).
		// Do not rely on a localized name or complete dictionary tags for that biome.
		return biome != null && (biome == BiomeGenBase.forest || biome instanceof BiomeGenForest
				|| biome == BiomeGenBase.getBiomeGenArray()[BiomeGenBase.forest.biomeID + 128]
				|| BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.FOREST));
	}

	/** Startup-only registration uses the same eligibility as both admission paths. */
	public static BiomeGenBase[] forestBiomes() {
		List<BiomeGenBase> forests = new ArrayList<BiomeGenBase>();
		for(BiomeGenBase biome : BiomeGenBase.getBiomeGenArray()) if(isForest(biome)) forests.add(biome);
		return forests.toArray(new BiomeGenBase[forests.size()]);
	}

	public static boolean isForest(World world, int x, int z) {
		return world.provider.dimensionId == 0 && world.blockExists(x, 0, z)
				&& isForest(world.getBiomeGenForCoords(x, z));
	}

	public static boolean canSpawn(World world, EntityWoodcrawler candidate) {
		if(GeminiSpawning.manualAdmission(candidate)) return true;
		if(!GeminiInvasionManager.canActivityOccur(world) || GeminiConfig.woodcrawlerSpawnWeight == 0
				|| GeminiConfig.woodcrawlerMaxLoaded == 0 || world.difficultySetting == EnumDifficulty.PEACEFUL) return false;
		return GeminiPopulation.canAdmit(world, EntityWoodcrawler.class, candidate, GeminiConfig.woodcrawlerMaxLoaded);
	}
	public static boolean canConvert(World world) {
		return canConvert(world, null);
	}
	public static boolean canConvert(World world, net.minecraft.entity.Entity source) {
		return GeminiInvasionManager.canActivityOccur(world) && world.difficultySetting != EnumDifficulty.PEACEFUL
				&& GeminiPopulation.canAdmitHatch(world, EntityWoodcrawler.class, GeminiConfig.woodcrawlerMaxLoaded, source);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
		if(event.world.isRemote) return;
		if(event.entityLiving instanceof EntityWoodcrawler && (!canSpawn(event.world, (EntityWoodcrawler) event.entityLiving)
				|| !isForest(event.world, MathHelper.floor_float(event.x), MathHelper.floor_float(event.z))
				|| !INVASION.contains(event.entityLiving)
					&& event.world.rand.nextInt(100) >= GeminiInvasionManager.encounterPercent(event.world))) {
			event.setResult(Event.Result.DENY);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onJoin(EntityJoinWorldEvent event) {
		if(event.world.isRemote || !(event.entity instanceof EntityWoodcrawler)) return;
		EntityWoodcrawler mob = (EntityWoodcrawler) event.entity;
		if(!mob.addedToChunk && !(mob.getEntityData().getBoolean("WoodcrawlerConverted")
				? GeminiInvasionManager.canActivityOccur(event.world) && event.world.difficultySetting != EnumDifficulty.PEACEFUL
					&& GeminiPopulation.canAdmit(event.world, EntityWoodcrawler.class, mob, GeminiConfig.woodcrawlerMaxLoaded)
				: canSpawn(event.world, mob))) event.setCanceled(true);
	}

	@SubscribeEvent
	public void onInvasion(GeminiInvasionManager.InvasionEvent event) {
		WorldServer world = event.world;
		if(!GeminiInvasionManager.canActivityOccur(world)) return;
		if(world.difficultySetting == EnumDifficulty.PEACEFUL || GeminiConfig.woodcrawlerSpawnWeight == 0) {
			diagnostic(world, "admission", "Woodcrawler encounters disabled by difficulty or spawn weight"); return;
		}
		List<EntityPlayer> players = new ArrayList<EntityPlayer>(), forestPlayers = new ArrayList<EntityPlayer>();
		for(Object entry : world.playerEntities) {
			EntityPlayer player = (EntityPlayer) entry;
			if(player.isDead || player.capabilities.disableDamage) continue;
			players.add(player);
			if(isForest(world, MathHelper.floor_double(player.posX), MathHelper.floor_double(player.posZ))) forestPlayers.add(player);
		}
		if(players.isEmpty()) { diagnostic(world, "players", "No active survival/adventure players in the Overworld"); return; }
		int phase = Math.min(3, GeminiInvasionManager.getPhase(world) - 1);
		int min = GeminiConfig.woodcrawlerInvasionMinCount[phase], max = GeminiConfig.woodcrawlerInvasionMaxCount[phase];
		int target = min + world.rand.nextInt(max - min + 1), spawned = 0;
		int[] placedX = new int[target], placedZ = new int[target];
		int unsupported = 0, unavailable = 0, placement = 0, forge = 0, insertion = 0;
		boolean population = false;
		EntityWoodcrawler mob = null;
		int budget = GeminiConfig.woodcrawlerInvasionMaxSpawnAttempts;
		for(int attempt = 0; attempt < budget && spawned < target; attempt++) {
			// Check available species AND combined (including pending cocoon) capacity
			// before constructing the next entity. Each successful join counts immediately.
			if(!GeminiPopulation.canAdmit(world, EntityWoodcrawler.class, null, GeminiConfig.woodcrawlerMaxLoaded)) {
				population = true; break;
			}
			List<EntityPlayer> anchors = !forestPlayers.isEmpty() && attempt < budget * 2 / 3 ? forestPlayers : players;
			EntityPlayer player = anchors.get(world.rand.nextInt(anchors.size()));
			double angle = world.rand.nextDouble() * Math.PI * 2;
			int radius = 28 + world.rand.nextInt(37);
			int x = MathHelper.floor_double(player.posX + Math.cos(angle) * radius);
			int z = MathHelper.floor_double(player.posZ + Math.sin(angle) * radius);
			// Include the complete body and collision-query margin before touching terrain.
			if(!world.checkChunksExist(x - 2, 0, z - 2, x + 2, world.getHeight() - 1, z + 2)) { unavailable++; continue; }
			if(!isForest(world, x, z)) { unsupported++; continue; }
			boolean crowded = false;
			for(Object entry : world.playerEntities) {
				EntityPlayer nearby = (EntityPlayer) entry;
				if(nearby.isDead) continue;
				double dx = x + 0.5D - nearby.posX, dz = z + 0.5D - nearby.posZ, distance = dx * dx + dz * dz;
				Vec3 look = nearby.getLookVec();
				// Prefer behind every nearby observer; relax only for the final third
				// of the fixed budget so facing direction cannot starve encounters.
				if(distance < 28D * 28D || attempt < budget * 2 / 3 && distance < 64D * 64D
						&& look != null && look.xCoord * dx + look.zCoord * dz > Math.sqrt(distance) * 0.25D) { crowded = true; break; }
			}
			for(int i = 0; i < spawned; i++) {
				double dx = x - placedX[i], dz = z - placedZ[i];
				if(dx * dx + dz * dz < 64D) crowded = true;
			}
			if(crowded) { placement++; continue; }
			int y = world.getTopSolidOrLiquidBlock(x, z);
			boolean cave = player.posY + 8 < y && attempt % 3 == 0;
			// Nearby cave players also get bounded cave-floor probes. Never scan a
			// full column or construct an entity inside solid geology.
			if(cave) {
				y = MathHelper.floor_double(player.posY) + 4;
				int bottom = Math.max(2, y - 12);
				while(y > bottom && !World.doesBlockHaveSolidTopSurface(world, x, y - 1, z)) y--;
			}
			if(y < 2 || y > world.getHeight() - 4 || !World.doesBlockHaveSolidTopSurface(world, x, y - 1, z)) { placement++; continue; }
			if(mob == null) mob = new EntityWoodcrawler(world);
			mob.setLocationAndAngles(x + 0.5D, y, z + 0.5D, world.rand.nextFloat() * 360, 0);
			// The standing model is 5.6 blocks tall. Use its existing 1.8-block
			// crawling posture under forest canopies instead of rejecting valid forest floors.
			mob.setCrouching(true);
			if(!validInvasionPlacement(world, mob)) { placement++; continue; }
			INVASION.add(mob);
			try {
				// Honor Forge vetoes without applying the ordinary natural-spawn roll
				// or vanilla's doMobSpawning/sky-light rules to scripted GHE encounters.
				if(ForgeEventFactory.canEntitySpawn(mob, world, (float) mob.posX, (float) mob.posY, (float) mob.posZ) == Event.Result.DENY) { forge++; continue; }
				GeminiSpawning.markNatural(mob);
				if(!ForgeEventFactory.doSpecialSpawn(mob, world, (float) mob.posX, (float) mob.posY, (float) mob.posZ)) mob.onSpawnWithEgg(null);
				// Callbacks can change the entity or population; validate again before insertion.
				if(!GeminiPopulation.canAdmit(world, EntityWoodcrawler.class, null, GeminiConfig.woodcrawlerMaxLoaded)) { population = true; break; }
				if(!canSpawn(world, mob) || !validInvasionPlacement(world, mob)) { placement++; continue; }
				if(world.spawnEntityInWorld(mob)) {
					if(cave) mob.beginUndergroundEmergenceIfNeeded();
					placedX[spawned] = x; placedZ[spawned++] = z;
				} else insertion++;
			} finally {
				INVASION.remove(mob);
				// Discard initialized/vetoed candidates instead of repeating initialization.
				mob = null;
			}
		}
		if(GeminiConfig.woodcrawlerInvasionDiagnostics) diagnostic(world, spawned > 0 ? "success" : "encounter",
				"Phase " + (phase + 1) + " encounter spawned " + spawned + "/" + target
				+ "; Unsupported biome=" + unsupported + "; No valid loaded spawn position=" + unavailable
				+ "; Population limit reached=" + population + "; Spawn placement rejected=" + placement
				+ "; Forge spawn event rejected=" + forge + "; World insertion rejected=" + insertion);
	}

	private static boolean validInvasionPlacement(WorldServer world, EntityWoodcrawler mob) {
		int x = MathHelper.floor_double(mob.posX), y = MathHelper.floor_double(mob.posY), z = MathHelper.floor_double(mob.posZ);
		return y >= 2 && mob.boundingBox.maxY < world.getHeight()
				&& world.checkChunksExist(x - 2, y - 1, z - 2, x + 2, y + 3, z + 2) && isForest(world, x, z)
				&& World.doesBlockHaveSolidTopSurface(world, x, y - 1, z)
				// Artificial lighting still protects bases; daylight is handled by existing burrowing AI.
				&& world.getSavedLightValue(EnumSkyBlock.Block, x, y, z) <= 7
				&& world.checkNoEntityCollision(mob.boundingBox)
				&& world.getCollidingBoundingBoxes(mob, mob.boundingBox).isEmpty() && !world.isAnyLiquid(mob.boundingBox);
	}

	static void diagnostic(World world, String reason, String message) {
		if(!GeminiConfig.woodcrawlerInvasionDiagnostics || world.isRemote) return;
		Map<String, Long> last = DIAGNOSTICS.get(world);
		if(last == null) { last = new HashMap<String, Long>(); DIAGNOSTICS.put(world, last); }
		long now = world.getTotalWorldTime();
		Long previous = last.get(reason);
		if(!"success".equals(reason) && previous != null && now >= previous && now - previous < 1200L) return;
		last.put(reason, now);
		MainRegistry.logger.info("[Gemini invasion] " + message);
	}
}
