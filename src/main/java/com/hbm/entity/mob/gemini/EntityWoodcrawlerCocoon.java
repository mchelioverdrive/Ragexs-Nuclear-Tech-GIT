package com.hbm.entity.mob.gemini;

import java.util.UUID;

import com.hbm.config.GeminiConfig;
import com.hbm.handler.GeminiCaptures;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.GeminiInvasionManager;
import com.hbm.world.gemini.GeminiSpawning;
import com.hbm.world.gemini.GeminiPopulation;
import com.hbm.world.gemini.WoodcrawlerConversionEvent;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

/** A stationary, saved processing structure. The snapshot has exactly one owner. */
public final class EntityWoodcrawlerCocoon extends Entity implements IGeminiCreature {

	private NBTTagCompound victim;
	private int remainingTicks, blockedTicks, retryTicks;
	private byte decision;
	private boolean releasing, manual, resolving;
	private boolean liveCapture;
	private EntityVillager original;
	private int missingTicks, gripTicks;

	boolean bindVictim(EntityVillager villager) {
		if(!GeminiCaptures.attach(this, villager, false, this::detachVictim)) return false;
		liveCapture = true; original = villager;
		gripTicks = 0;
		villager.getEntityData().setString("WoodcrawlerCocoon", getUniqueID().toString());
		villager.getEntityData().setInteger("WoodcrawlerCocoonId", getEntityId());
		villager.getEntityData().setLong("WoodcrawlerCocoonUntil", worldObj.getTotalWorldTime() + 40);
		GeminiCaptures.diagnostic(this, villager, "wrapping", "Cocoon took over the grip", 0, gripPoint(villager));
		return true;
	}
	private EntityVillager liveVictim() {
		return original != null && GeminiCaptures.valid(this, original) ? original : null;
	}
	public static boolean isConversionSource(Entity original, Entity replacement) {
		Entity owner = original.worldObj.getEntityByID(original.getEntityData().getInteger("WoodcrawlerCocoonId"));
		return owner instanceof EntityWoodcrawlerCocoon && ((EntityWoodcrawlerCocoon) owner).resolving
				&& ((EntityWoodcrawlerCocoon) owner).liveVictim() == original && original.getUniqueID().equals(replacement.getUniqueID());
	}
	private boolean tickLiveVictim() {
		EntityVillager original = liveVictim();
		if(original == null && this.original != null) { finish(); return false; }
		if(original == null && ticksExisted % 20 == 1 && victim != null) {
			UUID id = new UUID(victim.getLong("UUIDMost"), victim.getLong("UUIDLeast"));
			for(Object entry : worldObj.getEntitiesWithinAABB(EntityVillager.class, boundingBox.expand(4, 3, 4))) {
				EntityVillager candidate = (EntityVillager) entry;
				if(id.equals(candidate.getUniqueID()) && bindVictim(candidate)) { original = candidate; break; }
			}
		}
		if(original == null) { if(++missingTicks > 100) finish(); return false; }
		missingTicks = 0;
		if(!original.isEntityAlive() || getDistanceSqToEntity(original) > 16 || original.ridingEntity != null) { finish(); return false; }
		if(releasing) { finish(); return false; }
		original.getEntityData().setLong("WoodcrawlerCocoonUntil", worldObj.getTotalWorldTime() + 40);
		Vec3 goal = gripPoint(original);
		double dx = goal.xCoord - original.posX, dy = goal.yCoord - original.boundingBox.minY - original.height * 0.5D;
		double dz = goal.zCoord - original.posZ;
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double t = distance > 0.18D ? 0.18D / distance : 1D;
		AxisAlignedBB sweep = original.boundingBox.addCoord(dx * t, dy * t, dz * t).contract(0.001D, 0.001D, 0.001D);
		if(!GardenerNests.loaded(worldObj, sweep) || !worldObj.func_147461_a(sweep).isEmpty()) {
			if(++gripTicks >= 40) { finish("Cocoon grip path obstructed"); return false; }
			return false;
		}
		gripTicks = 0;
		GeminiCaptures.position(original, original.posX + dx * t, original.posY + dy * t, original.posZ + dz * t);
		return true;
	}

	private Vec3 gripPoint(EntityVillager villager) {
		return Vec3.createVectorHelper(posX, boundingBox.minY + villager.height * 0.5D + 0.12D, posZ);
	}

	public EntityWoodcrawlerCocoon(World world) {
		super(world);
		setSize(0.9F, 1.8F);
		preventEntitySpawning = true;
		isImmuneToFire = true;
	}

	@Override protected void entityInit() {
		dataWatcher.addObject(20, Byte.valueOf((byte) 0));
	}

	public boolean isReleasing() { return dataWatcher.getWatchableObjectByte(20) != 0; }
	@Override public boolean canBeCollidedWith() { return !isDead; }
	@Override public boolean canBePushed() { return false; }

	/** Does not alter terrain, load chunks, or consume the source on a vetoed spawn. */
	public static EntityWoodcrawlerCocoon place(EntityWoodcrawler carrier, NBTTagCompound snapshot, double x, double y, double z,
			int elapsed, byte previousDecision) {
		if(carrier.worldObj.isRemote || !GeminiSpawning.canAct(carrier, 1)
				|| !GeminiPopulation.canAdmitWoodcrawlerCocoon(carrier.worldObj)) return null;
		EntityWoodcrawlerCocoon nest = new EntityWoodcrawlerCocoon(carrier.worldObj);
		nest.victim = (NBTTagCompound) snapshot.copy();
		nest.remainingTicks = Math.max(0, GeminiConfig.woodcrawlerConversionSeconds * 20 - elapsed);
		nest.decision = previousDecision;
		nest.manual = GeminiSpawning.isManual(carrier);
		// The victim's feet first, then a bounded ring at the capture site. No path is needed.
		for(int i = 0; i < 26; i++) {
			double px = x + (i == 0 ? 0 : (i - 1) % 5 - 2);
			double pz = z + (i == 0 ? 0 : (i - 1) / 5 - 2);
			if((MathHelper.floor_double(px) >> 4) != (MathHelper.floor_double(x) >> 4)
					|| (MathHelper.floor_double(pz) >> 4) != (MathHelper.floor_double(z) >> 4)) continue;
			nest.setLocationAndAngles(px, y, pz, carrier.rotationYaw, 0);
			if(!nest.clearSpace(nest) || !carrier.worldObj.getEntitiesWithinAABB(EntityWoodcrawlerCocoon.class, nest.boundingBox).isEmpty()) continue;
			return carrier.worldObj.spawnEntityInWorld(nest) ? nest : null;
		}
		return null;
	}

	/** Roll back a spawn when a mod refuses removal of the original victim. */
	void cancelPlacement() { finish(); }

	private boolean active() {
		return !worldObj.isRemote && worldObj.difficultySetting != EnumDifficulty.PEACEFUL
				&& (manual ? GeminiInvasionManager.manualActivationAllowed(worldObj) : GeminiInvasionManager.canActivityOccur(worldObj));
	}

	public boolean shouldConvertToFakePerson() {
		if(!active() || releasing || victim == null || remainingTicks > 0) return false;
		if(decision == 0) decision = (byte) (rand.nextFloat() < GeminiConfig.fakePersonConversionChance ? 1 : 2);
		return decision == 1;
	}

	@Override public void onUpdate() {
		super.onUpdate();
		motionX = motionY = motionZ = 0;
		if(worldObj.isRemote) return;
		if(victim == null) { super.setDead(); return; }
		if(liveCapture && !tickLiveVictim()) return;
		// Releasing an ordinary villager is safe even while the Gemini gate is closed.
		if(!releasing && !active()) return;
		if(!releasing && remainingTicks > 0) { remainingTicks--; return; }
		if(retryTicks > 0) { retryTicks--; return; }
		retryTicks = 99;
		resolve();
	}

	private void resolve() {
		if(resolving || victim == null) return;
		resolving = true;
		try {
			if(liveCapture && liveVictim() == null) return; // Wait for the saved source to resolve, never spawn a second identity.
			// Also handles a previously admitted output restored alongside a stale saved nest.
			if(victim.hasKey("UUIDMost", 4) && victim.hasKey("UUIDLeast", 4)) {
				UUID id = new UUID(victim.getLong("UUIDMost"), victim.getLong("UUIDLeast"));
				Entity existing = GeminiPopulation.find(worldObj, id);
				if(existing != null && existing.addedToChunk && worldObj.getEntityByID(existing.getEntityId()) == existing) {
					EntityVillager original = liveVictim();
					if(original != null) original.setDead();
					finish(); return;
				}
				for(Object entry : worldObj.getEntitiesWithinAABB(EntityVillager.class, boundingBox.expand(16, 8, 16))) {
					Entity entity = (Entity) entry;
					if(!entity.isDead && id.equals(entity.getUniqueID()) && entity != liveVictim()) { finish(); return; }
				}
			}
			EntityLiving output = null;
			if(!releasing) {
				boolean fake = shouldConvertToFakePerson(); // Commit the outcome before admission/event retries.
				// Population pressure is temporary, including a zero cap. It must not consume failure time.
				if(!GeminiPopulation.canAdmitHatch(worldObj, fake ? EntityFakePerson.class : EntityWoodcrawler.class,
						fake ? GeminiConfig.fakePersonMaxLoaded : GeminiConfig.woodcrawlerMaxLoaded, this)) return;
				WoodcrawlerConversionEvent event = new WoodcrawlerConversionEvent(this, victim);
				MinecraftForge.EVENT_BUS.post(event);
				output = event.getReplacement();
				if(output == null && EntityList.stringToClassMapping.get(victim.getString("id")) != EntityVillager.class) beginRelease();
				else if((blockedTicks += 100) > 1200) beginRelease();
			}
			if(releasing) {
				if(liveCapture) { finish(); return; }
				Entity restored = EntityList.createEntityFromNBT((NBTTagCompound) victim.copy(), worldObj);
				if(!(restored instanceof EntityVillager)) { finish(); return; }
				output = (EntityLiving) restored;
				// Preserve identity, trades and mod data; escape leaves the already-wounded victim alive.
				output.setHealth(Math.max(1F, Math.min(output.getHealth(), output.getMaxHealth() * 0.5F)));
			}
			if(output == null || output.isDead || output.worldObj != worldObj || worldObj.loadedEntityList.contains(output)) return;
			if(!releasing && !active()) return;
			// Keep output and nest in the same chunk so normal chunk saves cannot split the transfer.
			int chunkX = MathHelper.floor_double(posX) >> 4, chunkZ = MathHelper.floor_double(posZ) >> 4;
			for(int i = 0; i < 75; i++) {
				double x = posX + (i % 5 - 2), y = posY + i / 25, z = posZ + (i / 5 % 5 - 2);
				if((MathHelper.floor_double(x) >> 4) != chunkX || (MathHelper.floor_double(z) >> 4) != chunkZ) continue;
				output.setLocationAndAngles(x, y, z, rotationYaw, 0);
				if(!clearSpace(output)) continue;
				EntityVillager original = liveVictim();
				boolean occupied = false;
				for(Object entry : worldObj.getEntitiesWithinAABBExcludingEntity(this, output.boundingBox)) {
					Entity other = (Entity) entry;
					if(other != original && !other.isDead && other.preventEntitySpawning) { occupied = true; break; }
				}
				if(occupied) continue;
				if(GeminiPopulation.spawnFromCocoon(output, this)) {
					if(original != null) {
						original.setDead();
						if(!original.isDead) { output.setDead(); beginRelease(); return; }
					}
					finish();
				}
				return; // A protection veto is retried later, never hammered at every position.
			}
		} finally { resolving = false; }
	}

	private boolean clearSpace(Entity entity) {
		int x = MathHelper.floor_double(entity.posX), y = MathHelper.floor_double(entity.posY), z = MathHelper.floor_double(entity.posZ);
		return y > 0 && y + entity.height < worldObj.getHeight()
				&& worldObj.checkChunksExist(MathHelper.floor_double(entity.boundingBox.minX), y - 1,
						MathHelper.floor_double(entity.boundingBox.minZ), MathHelper.floor_double(entity.boundingBox.maxX),
						MathHelper.ceiling_double_int(entity.boundingBox.maxY), MathHelper.floor_double(entity.boundingBox.maxZ))
				&& worldObj.getBlock(x, y - 1, z).getMaterial().blocksMovement()
				&& worldObj.getCollidingBoundingBoxes(entity, entity.boundingBox).isEmpty()
				&& !worldObj.isAnyLiquid(entity.boundingBox);
	}

	private void beginRelease() {
		releasing = true;
		dataWatcher.updateObject(20, Byte.valueOf((byte) 1));
	}

	private void detachVictim(String reason) {
		if(original != null) {
			GeminiCaptures.diagnostic(this, original, "cocoon", reason, remainingTicks, null);
			GeminiCaptures.release(this, original);
			original.getEntityData().removeTag("WoodcrawlerCocoon"); original.getEntityData().removeTag("WoodcrawlerCocoonId");
			original.getEntityData().removeTag("WoodcrawlerCocoonUntil");
		}
		original = null;
	}

	private void finish() { finish("Cocoon processing ended"); }
	private void finish(String reason) {
		detachVictim(reason);
		victim = null;
		super.setDead();
	}

	@Override public boolean attackEntityFrom(DamageSource source, float amount) {
		if(worldObj.isRemote || amount <= 0 || source.getEntity() instanceof IGeminiCreature) return false;
		beginRelease();
		retryTicks = 0;
		resolve();
		return true;
	}

	@Override public void setDead() {
		if(!worldObj.isRemote && victim != null) {
			beginRelease();
			resolve(); // Vetoed releases retain a visible, broken nest and saved victim until safe.
			return;
		}
		super.setDead();
	}

	@Override protected void writeEntityToNBT(NBTTagCompound nbt) {
		if(victim != null) nbt.setTag("Victim", victim.copy());
		nbt.setInteger("Remaining", remainingTicks);
		nbt.setInteger("Blocked", blockedTicks);
		nbt.setByte("Decision", decision);
		nbt.setBoolean("Releasing", releasing);
		nbt.setBoolean("Manual", manual);
		nbt.setBoolean("LiveCapture", liveCapture);
	}

	@Override protected void readEntityFromNBT(NBTTagCompound nbt) {
		victim = nbt.hasKey("Victim", 10) ? nbt.getCompoundTag("Victim") : null;
		remainingTicks = Math.max(0, Math.min(72000, nbt.getInteger("Remaining")));
		blockedTicks = Math.max(0, Math.min(1201, nbt.getInteger("Blocked")));
		decision = (byte) Math.max(0, Math.min(2, nbt.getByte("Decision")));
		manual = nbt.getBoolean("Manual");
		liveCapture = nbt.getBoolean("LiveCapture"); original = null; missingTicks = 0;
		if(nbt.getBoolean("Releasing")) beginRelease();
	}
}
