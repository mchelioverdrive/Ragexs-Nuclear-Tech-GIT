package com.hbm.entity.mob.gemini;

import java.util.UUID;

import com.hbm.config.GeminiConfig;
import com.hbm.world.gemini.GeminiInvasionManager;
import com.hbm.world.gemini.GeminiSpawning;
import com.hbm.world.gemini.WoodcrawlerConversionEvent;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

/** A stationary, saved processing structure. The snapshot has exactly one owner. */
public final class EntityWoodcrawlerCocoon extends Entity implements IGeminiCreature {

	private NBTTagCompound victim;
	private int remainingTicks, blockedTicks, retryTicks;
	private byte decision;
	private boolean releasing, manual, resolving;

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
		if(carrier.worldObj.isRemote || !GeminiSpawning.canAct(carrier, 1)) return null;
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
		return !worldObj.isRemote && GeminiInvasionManager.hasMarsVisited(worldObj)
				&& worldObj.difficultySetting != EnumDifficulty.PEACEFUL
				&& (manual || GeminiInvasionManager.canActivityOccur(worldObj));
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
		// Releasing an ordinary villager is safe even while the Gemini gate is closed.
		if(!releasing && !active()) return;
		if(!releasing && remainingTicks > 0) { remainingTicks--; return; }
		if(!releasing && ++blockedTicks > 1200) beginRelease();
		if(retryTicks > 0) { retryTicks--; return; }
		retryTicks = 99;
		resolve();
	}

	private void resolve() {
		if(resolving || victim == null) return;
		resolving = true;
		try {
			// Also handles a previously admitted output restored alongside a stale saved nest.
			if(victim.hasKey("UUIDMost", 4) && victim.hasKey("UUIDLeast", 4)) {
				UUID id = new UUID(victim.getLong("UUIDMost"), victim.getLong("UUIDLeast"));
				for(Object entry : worldObj.loadedEntityList) {
					Entity entity = (Entity) entry;
					if(!entity.isDead && id.equals(entity.getUniqueID())) { finish(); return; }
				}
			}
			EntityLiving output = null;
			if(!releasing) {
				WoodcrawlerConversionEvent event = new WoodcrawlerConversionEvent(this, victim);
				MinecraftForge.EVENT_BUS.post(event);
				output = event.getReplacement();
				// A failed roll or unsupported villager is a terminal, lossless abort.
				if(output == null && (!shouldConvertToFakePerson()
						|| EntityList.stringToClassMapping.get(victim.getString("id")) != EntityVillager.class)) beginRelease();
			}
			if(releasing) {
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
				if(!clearSpace(output) || !worldObj.checkNoEntityCollision(output.boundingBox, this)) continue;
				if(worldObj.spawnEntityInWorld(output)) finish();
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

	private void finish() {
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
	}

	@Override protected void readEntityFromNBT(NBTTagCompound nbt) {
		victim = nbt.hasKey("Victim", 10) ? nbt.getCompoundTag("Victim") : null;
		remainingTicks = Math.max(0, Math.min(72000, nbt.getInteger("Remaining")));
		blockedTicks = Math.max(0, Math.min(1201, nbt.getInteger("Blocked")));
		decision = (byte) Math.max(0, Math.min(2, nbt.getByte("Decision")));
		manual = nbt.getBoolean("Manual");
		if(nbt.getBoolean("Releasing")) beginRelease();
	}
}
