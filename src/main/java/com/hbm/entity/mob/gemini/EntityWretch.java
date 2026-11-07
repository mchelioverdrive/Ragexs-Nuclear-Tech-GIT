package com.hbm.entity.mob.gemini;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.WretchAnatomyPacket;
import com.hbm.config.GeminiConfig;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.WretchCamps;
import com.hbm.world.gemini.GeminiSpawning;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/** Territory-bound major boss. The server owns every attack, capture, throw and terrain mutation. */
public final class EntityWretch extends EntityCreature implements IMob, IBossDisplayData, IGeminiCreature, IEntityAdditionalSpawnData {
    public static final int IDLE = 0, SWIPE = 1, SLAM = 2, GRAB = 3, HOLD = 4, THROW = 5, RECOVER = 6, FEED = 7, BREACH = 8, PULL = 9, ASSIMILATE = 10;
    private final EntityWretchPart[] parts = new EntityWretchPart[WretchPose.PARTS];
    private final WretchPose skeleton = new WretchPose(this);
    final WretchNavigation navigation = new WretchNavigation(this);
    final WretchBrain brain = new WretchBrain(this);
    final WretchCapture capture = new WretchCapture(this);
    public final WretchAnatomy anatomy = new WretchAnatomy();
    private boolean operational;
    private final java.util.Random cosmeticRandom = new java.util.Random(getUniqueID().getLeastSignificantBits());
    private int nextFoley, nextStep, nextVoice, lastPresentation = -1;
    private double stepTravel;
    private boolean soundAwake;
    private final Set<UUID> struck = new HashSet<UUID>();
    private int homeX, homeY, homeZ;
    private boolean campBound, eggSpawned, fromSave;
    private int attackTicks, cooldown = 80, feedCooldown = 300, lastAttack = SWIPE;
    private float interruptDamage;
    private EntityLivingBase meal;

    public EntityWretch(World world) {
        super(world); setSize(WretchPose.WIDTH, WretchPose.HEIGHT); stepHeight = 1.1F;
        // 1.7.10 culls entities against the physics box. RenderWretch tests the full pose envelope instead.
        experienceValue = 250; func_110163_bv(); ignoreFrustumCheck = true;
        renderDistanceWeight = 4D;
        getNavigator().setAvoidsWater(true); setHealth(getMaxHealth());
    }
    @Override protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(17, 0); dataWatcher.addObject(18, 0); dataWatcher.addObject(19, (byte) WretchBrain.DORMANT);
        dataWatcher.addObject(20, (byte) IDLE); dataWatcher.addObject(21, 0);
        dataWatcher.addObject(22, (byte) 0); dataWatcher.addObject(23, -1);
        dataWatcher.addObject(24, 0F); dataWatcher.addObject(25, 1F); dataWatcher.addObject(26, 4F);
        dataWatcher.addObject(27, (byte) 0); dataWatcher.addObject(28, (byte) 0);
        // Float at index 31 encodes as 0x7F, the 1.7.10 metadata terminator. Use an integer timer.
        dataWatcher.addObject(29, 0); dataWatcher.addObject(30, (byte) IDLE); dataWatcher.addObject(31, 0);
    }
    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(GeminiConfig.wretchHealth);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.21D);
        // Native follow range is retained for compatibility; WretchBrain owns detection and local navigation.
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(16D);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(0.95D);
    }
    @Override public boolean isAIEnabled() { return true; }
    @Override protected boolean canDespawn() { return false; }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean canBeCollidedWith() { return false; } // Physical hits use the visible multipart anatomy.
    @Override protected void collideWithNearbyEntities() {
        if(worldObj.isRemote || !isOperational() || !isAwake()) return;
        int examined = 0; WretchPose pose = pose(1);
        for(Object entry : worldObj.getEntitiesWithinAABBExcludingEntity(this, boundingBox.expand(.2, 0, .2))) {
            if(++examined > 32) break;
            Entity entity = (Entity) entry;
            if(!entity.canBePushed() || entity instanceof EntityLivingBase && capture.owns((EntityLivingBase) entity)) continue;
            for(int slot = 0; slot < WretchPose.PARTS; slot++) if(parts[slot] != null && parts[slot].boundingBox.intersectsWith(entity.boundingBox)
                    && pose.touches(slot, entity.boundingBox)) { entity.applyEntityCollision(this); break; }
        }
    }
    @Override protected String getLivingSound() { return null; } // Irregular, height-positioned server foley below.
    @Override protected String getHurtSound() { return "hbm:entity.wretch.hurt"; }
    @Override protected String getDeathSound() { return "hbm:entity.wretch.death"; }
    @Override protected float getSoundPitch() { return 0.92F + cosmeticRandom.nextFloat() * 0.13F; }
    @Override protected void func_145780_a(int x, int y, int z, net.minecraft.block.Block block) { }
    @Override public float getEyeHeight() { return 18.8F; }
    public WretchPose pose(float partial) { return skeleton.update(partial); }
    public AxisAlignedBB visualBounds() {
        return boundingBox.expand(19D, 3D, 19D); // Includes fully folded elbows, fingers and the death pose.
    }
    public int previousState() { return dataWatcher.getWatchableObjectByte(30); }
    public float previousStateTime() { return dataWatcher.getWatchableObjectInt(31); }
    public double awakeAmount(float partial) {
        return deathTime > 0 ? 1D : isAwake() ? MathHelper.clamp_double(((int) worldObj.getTotalWorldTime() - dataWatcher.getWatchableObjectInt(29) + partial) / 60D, 0D, 1D) : 0D;
    }
    public int presentation() { return (dataWatcher.getWatchableObjectByte(28) & 0xFF) >> 2; }
    public double presentationTime() { return presentationTime(1); }
    public double presentationTime(float partial) { return MathHelper.clamp_double(((int) worldObj.getTotalWorldTime() - dataWatcher.getWatchableObjectInt(18) + partial) / 90D, 0D, 1D); }
    public int behavior() { return (dataWatcher.getWatchableObjectByte(22) & 0xFF) >> 1; }
    public int previousBehavior() { return dataWatcher.getWatchableObjectByte(19) & 15; }
    public float behaviorTime(float partial) { return Math.max(0, (int) worldObj.getTotalWorldTime() - dataWatcher.getWatchableObjectInt(17) + partial); }
    public double territoryRadius() { return GeminiConfig.wretchTerritoryRadius; }
    public boolean isOperational() { return isEntityAlive() && (worldObj.isRemote || operational && addedToChunk); }
    public Vec3 headPoint(float partial) { return pose(partial).worldHead(); }
    void syncBehavior(int behavior, int elapsed) {
        int now = (int) worldObj.getTotalWorldTime();
        if(behavior == WretchBrain.AWAKENING) dataWatcher.updateObject(29, now - elapsed);
        else if(behavior != WretchBrain.DORMANT && !isAwake()) dataWatcher.updateObject(29, now - 60);
        dataWatcher.updateObject(19, (byte) behavior());
        dataWatcher.updateObject(17, now - elapsed);
        dataWatcher.updateObject(22, (byte) (behavior << 1 | (behavior == WretchBrain.DORMANT ? 0 : 1)));
        skeleton.invalidate();
    }
    void present(int mode, Vec3 point) {
        if(state() != IDLE || !canReach(point.xCoord, point.yCoord, point.zCoord)) return;
        aimPoint(point.xCoord, point.yCoord, point.zCoord);
        dataWatcher.updateObject(28, (byte) (phase() | mode << 2)); dataWatcher.updateObject(18, (int) worldObj.getTotalWorldTime());
        lastPresentation = -1; skeleton.invalidate();
    }
    void clearPresentation() { dataWatcher.updateObject(28, (byte) phase()); lastPresentation = -1; skeleton.invalidate(); }
    int combatTicks() { return attackTicks; }
    public void suspendWretch() { brain.pause(); }
    public boolean wasLoaded() { return fromSave; }
    public boolean hasCamp() { return campBound; }
    public boolean isEggSpawned() { return eggSpawned; }
    public int campX() { return homeX; }
    public int campY() { return homeY; }
    public int campZ() { return homeZ; }
    public void bindCamp(UUID id, int x, int y, int z) { entityUniqueID = id; homeX = x; homeY = y; homeZ = z; campBound = true; eggSpawned = false; }
    @Override public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);
        if(!worldObj.isRemote && !campBound && GeminiSpawning.initialize(this)) {
            eggSpawned = true;
            homeX = MathHelper.floor_double(posX); homeY = MathHelper.floor_double(posY); homeZ = MathHelper.floor_double(posZ);
        }
        return data;
    }
    public boolean inTerritory(double x, double z, double radius) {
        double dx = x - homeX - 0.5D, dz = z - homeZ - 0.5D;
        return (campBound || eggSpawned) && dx * dx + dz * dz <= radius * radius;
    }
    public int state() { return dataWatcher.getWatchableObjectByte(20); }
    public float stateTime(float partial) { return Math.max(0, (int) worldObj.getTotalWorldTime() - dataWatcher.getWatchableObjectInt(21) + partial); }
    public boolean isAwake() { return (dataWatcher.getWatchableObjectByte(22) & 1) != 0; }
    public float aimX() { return dataWatcher.getWatchableObjectFloat(24); }
    public float aimY() { return dataWatcher.getWatchableObjectFloat(25); }
    public float aimZ() { return dataWatcher.getWatchableObjectFloat(26); }
    public int attackArm() { return dataWatcher.getWatchableObjectByte(27); }
    public int phase() { return dataWatcher.getWatchableObjectByte(28) & 3; }
    private boolean active() {
        return isEntityAlive() && (GeminiSpawning.manualAdmission(this) || WretchCamps.active(worldObj) && WretchCamps.owns(this))
                && GardenerNests.loaded(worldObj, boundingBox.expand(9, 1, 9));
    }
    private void stop() { navigation.halt(); }
    @Override public void moveEntity(double x, double y, double z) {
        if(navigation == null) { super.moveEntity(x, y, z); return; }
        if(!worldObj.isRemote && (!operational || !isAwake())) x = z = 0;
        AxisAlignedBB swept = boundingBox.addCoord(x, y, z).expand(1, 0, 1);
        if(!GardenerNests.loaded(worldObj, swept)) { motionX = motionZ = 0; return; }
        if(y != 0 && navigation.anatomyClear(posX, posY, posZ) && !navigation.anatomyClear(posX, posY + y, posZ)) y = 0;
        AxisAlignedBB step = AxisAlignedBB.getBoundingBox(posX + x - width * 0.5, posY + 0.02, posZ + z - width * 0.5,
                posX + x + width * 0.5, posY + 0.8, posZ + z + width * 0.5);
        if((x != 0 || z != 0) && !navigation.anatomyClear(posX + x, posY + y, posZ + z)
                && !(onGround && !worldObj.getCollidingBoundingBoxes(this, step).isEmpty()
                && navigation.anatomyClear(posX + x, posY + 1, posZ + z))) x = z = 0;
        // Native feet collision supplies real stepping, support and gravity. The raised torso and leg
        // columns are separately swept above; empty space beneath the torso is not a solid collider.
        boundingBox.setBounds(posX - width * 0.5, posY, posZ - width * 0.5, posX + width * 0.5, posY + 0.8, posZ + width * 0.5);
        try { super.moveEntity(x, y, z); }
        finally { boundingBox.setBounds(posX - width * 0.5, posY, posZ - width * 0.5, posX + width * 0.5, posY + height, posZ + width * 0.5); }
    }
    @Override protected void updateAITasks() {
        if(!operational) { navigation.halt(); return; }
        int phase = !GeminiConfig.wretchHealthAggression ? 0 : getHealth() <= getMaxHealth() * 0.3F ? 2 : getHealth() <= getMaxHealth() * 0.65F ? 1 : 0;
        dataWatcher.updateObject(28, (byte) (phase | presentation() << 2));
        if(cooldown > 0) cooldown--;
        if(feedCooldown > 0) feedCooldown--;
        brain.tick();
        if(state() != IDLE && stateTime(0) > 0) tickAttack();
        if(GeminiConfig.wretchLivestockFeeding && state() == IDLE && getAttackTarget() == null && behavior() == WretchBrain.WANDERING && feedCooldown == 0 && cooldown == 0
                && ticksExisted % 60 == 0 && getHealth() < getMaxHealth() * 0.85F && findMeal()) {
            begin(FEED); stop(); feedCooldown = 600;
        }
        navigation.tick(); getLookHelper().onUpdateLook();
    }
    @Override public void onUpdate() {
        double oldX = posX, oldY = posY, oldZ = posZ;
        if(!worldObj.isRemote) {
            operational = active();
            if(!operational) { brain.pause(); dataWatcher.updateObject(22, (byte) 0); removeParts(); }
        }
        super.onUpdate(); skeleton.invalidate();
        if(!worldObj.isRemote && (state() != IDLE || presentation() != WretchPose.WATCH) && (posX != oldX || posY != oldY || posZ != oldZ))
            aimPoint(oldX + aimX(), oldY + aimY(), oldZ + aimZ());
        if(!worldObj.isRemote) { tickPresentation(); skeleton.invalidate(); }
        if(worldObj.isRemote || !operational || !isEntityAlive()) return;
        if(ticksExisted % (isAwake() ? 10 : 20) == 1) maintainParts();
        renderYawOffset = rotationYaw;
    }
    boolean validPlayer(EntityLivingBase entity) {
        return entity instanceof EntityPlayer && com.hbm.world.gemini.WretchDisturbances.eligible((EntityPlayer) entity)
                && entity.worldObj == worldObj && worldObj.getEntityByID(entity.getEntityId()) == entity
                && entity.ridingEntity == null && entity.riddenByEntity == null && !entity.getEntityData().hasKey("MockeryCaptor")
                && inTerritory(entity.posX, entity.posZ, territoryRadius()) && Math.abs(entity.posY - posY) <= 32;
    }
    boolean validPrey(EntityLivingBase entity) { return entity instanceof EntityPlayer ? validPlayer(entity) : GeminiConfig.wretchAssimilation && capture.eligible(entity); }
    boolean sight(EntityLivingBase entity, double range) {
        if(entity == null || !entity.isEntityAlive() || entity.worldObj != worldObj || getDistanceSqToEntity(entity) > range * range) return false;
        return clearRay(headPoint(1), Vec3.createVectorHelper(entity.posX, entity.boundingBox.minY + entity.getEyeHeight(), entity.posZ), true);
    }
    private boolean clearRay(Vec3 from, Vec3 to, boolean glass) {
        if(!WretchBrain.lineLoaded(worldObj, from, to)) return false;
        // The ray walker mutates its origin; shared pose joints must remain immutable here.
        for(int pass = 0; pass < (glass ? 5 : 1); pass++) {
            MovingObjectPosition hit = worldObj.func_147447_a(Vec3.createVectorHelper(from.xCoord, from.yCoord, from.zCoord), to, false, true, false);
            if(hit == null) return true;
            if(!glass || worldObj.getBlock(hit.blockX, hit.blockY, hit.blockZ).getMaterial() != net.minecraft.block.material.Material.glass) return false;
            double dx = to.xCoord - from.xCoord, dy = to.yCoord - from.yCoord, dz = to.zCoord - from.zCoord;
            double length = Math.max(0.001, Math.sqrt(dx * dx + dy * dy + dz * dz));
            dx /= length; dy /= length; dz /= length;
            // Exit exactly this glass cell. A wall immediately behind it must still occlude sight.
            double exit = Double.POSITIVE_INFINITY;
            if(Math.abs(dx) > 1E-8) exit = Math.min(exit, ((dx > 0 ? hit.blockX + 1 : hit.blockX) - hit.hitVec.xCoord) / dx);
            if(Math.abs(dy) > 1E-8) exit = Math.min(exit, ((dy > 0 ? hit.blockY + 1 : hit.blockY) - hit.hitVec.yCoord) / dy);
            if(Math.abs(dz) > 1E-8) exit = Math.min(exit, ((dz > 0 ? hit.blockZ + 1 : hit.blockZ) - hit.hitVec.zCoord) / dz);
            if(hit.hitVec.distanceTo(to) <= exit + 0.001) return true;
            from = hit.hitVec.addVector(dx * (exit + 0.001), dy * (exit + 0.001), dz * (exit + 0.001));
        }
        return false;
    }
    private boolean visible(EntityLivingBase target, double range) { return sight(target, range); }
    boolean canReach(double x, double y, double z) {
        double dx = x - posX, dz = z - posZ;
        if(dx * dx + dz * dz > 12 * 12 || y < posY - 1 || y > posY + 18) return false;
        Vec3 shoulder = WretchPose.point(this, attackArm(), 0, 1);
        double ax = shoulder.xCoord - x, ay = shoulder.yCoord - y, az = shoulder.zCoord - z;
        return ax * ax + ay * ay + az * az < 18.6 * 18.6;
    }
    boolean attackClear(int arm, EntityLivingBase victim) {
        Vec3 shoulder = WretchPose.point(this, arm, 0, 1), elbow = WretchPose.point(this, arm, 1, 1), hand = WretchPose.point(this, arm, 2, 1);
        Vec3 contact = Vec3.createVectorHelper(victim.posX, victim.boundingBox.minY + victim.height * 0.5, victim.posZ);
        return clearRay(shoulder, elbow, false) && clearRay(elbow, hand, false) && clearRay(hand, contact, false);
    }
    boolean tryCombat() {
        if(state() != IDLE || cooldown != 0 || awakeAmount(0) < 1) return false;
        EntityLivingBase target = getAttackTarget();
        Vec3 point = target != null && validPrey(target) && sight(target, GeminiConfig.wretchDetectionRange)
                ? Vec3.createVectorHelper(target.posX, target.boundingBox.minY + target.height * 0.5, target.posZ) : brain.attackPoint();
        if(point == null || !canReach(point.xCoord, point.yCoord, point.zCoord)) return false;
        aimPoint(point.xCoord, point.yCoord, point.zCoord); dataWatcher.updateObject(27, (byte) rand.nextInt(2));
        int next = point.yCoord - posY < 2.5 && rand.nextInt(phase() >= 1 ? 3 : 5) == 0 ? SLAM : SWIPE;
        if(target != null && capture.eligible(target) && target.getEntityData().getLong("WretchEscapeUntil") <= worldObj.getTotalWorldTime()
                && rand.nextInt(lastAttack == GRAB ? 3 : 4) != 0) next = GRAB;
        if(next == SLAM && lastAttack == SLAM) next = SWIPE;
        lastAttack = next; clearPresentation(); begin(next); voice("stretch", 1.5F); return true;
    }
    void structuralAttack(Vec3 point) {
        clearPresentation(); dataWatcher.updateObject(27, (byte) rand.nextInt(2));
        aimPoint(point.xCoord, point.yCoord, point.zCoord); begin(BREACH); stop(); voice("stretch", 1.5F);
    }
    void cancelCombat() { release(); meal = null; if(state() != IDLE) begin(IDLE); navigation.halt(); setAttackTarget(null); }
    public boolean mayBreakContact(int x, int y, int z) { return brain.destructiveIntent() && brain.building.contacts(x, y, z); }
    public void contactBroken() { brain.structuralProgress(); }
    public boolean hasSpawnClearance() { return navigation.canStand(posX, posY, posZ); }
    private void aimPoint(double x, double y, double z) {
        dataWatcher.updateObject(24, (float) (x - posX)); dataWatcher.updateObject(25, (float) (y - posY)); dataWatcher.updateObject(26, (float) (z - posZ)); skeleton.invalidate();
    }
    private void aim(EntityLivingBase target) { aimPoint(target.posX, target.boundingBox.minY + target.height * 0.5, target.posZ); }
    void begin(int next) {
        dataWatcher.updateObject(30, (byte) state()); dataWatcher.updateObject(31, (int) stateTime(0));
        dataWatcher.updateObject(20, (byte) next); dataWatcher.updateObject(21, (int) worldObj.getTotalWorldTime());
        attackTicks = 0; struck.clear(); interruptDamage = 0; brain.building.resetStrike();
        skeleton.invalidate();
    }
    void recovery() { release(); meal = null; begin(RECOVER); stop(); }
    private void release() { capture.release(); }
    void captiveId(int id) { dataWatcher.updateObject(23, id); }
    public int captiveId() { return dataWatcher.getWatchableObjectInt(23); }
    public void syncAnatomy() {
        if(!worldObj.isRemote) ((WorldServer) worldObj).getEntityTracker().func_151247_a(this, PacketDispatcher.wrapper.getPacketFrom(new WretchAnatomyPacket(this)));
    }
    public static boolean isCaptured(EntityLivingBase victim) {
        if(victim == null || victim.worldObj.isRemote) return false;
        Entity owner = victim.worldObj.getEntityByID(victim.getEntityData().getInteger("WretchCaptorId"));
        return owner instanceof EntityWretch && ((EntityWretch) owner).capture.owns(victim);
    }
    public static void releaseVictim(EntityLivingBase victim, boolean died) {
        if(victim == null || victim.worldObj.isRemote) return;
        Entity owner = victim.worldObj.getEntityByID(victim.getEntityData().getInteger("WretchCaptorId"));
        if(owner instanceof EntityWretch && ((EntityWretch) owner).capture.owns(victim)) {
            if(died) ((EntityWretch) owner).capture.death(null); else ((EntityWretch) owner).recovery();
        }
        if(!isCaptured(victim)) {
            victim.getEntityData().removeTag("WretchCaptor"); victim.getEntityData().removeTag("WretchCaptorId"); victim.getEntityData().removeTag("WretchCaptorUntil");
        }
    }
    public static void victimDied(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        EntityLivingBase victim = event.entityLiving;
        if(victim.worldObj.isRemote) return;
        Entity owner = victim.worldObj.getEntityByID(victim.getEntityData().getInteger("WretchCaptorId"));
        if(owner instanceof EntityWretch && ((EntityWretch) owner).capture.owns(victim)) ((EntityWretch) owner).capture.death(event);
    }
    @Override public void writeSpawnData(ByteBuf buf) {
        buf.writeLong(getUniqueID().getMostSignificantBits()); buf.writeLong(getUniqueID().getLeastSignificantBits()); anatomy.writeVisual(buf);
    }
    @Override public void readSpawnData(ByteBuf buf) {
        entityUniqueID = new UUID(buf.readLong(), buf.readLong()); anatomy.readVisual(buf);
    }

    private void tickAttack() {
        attackTicks++;
        int state = state();
        if(state == SWIPE && attackTicks <= 20 && validPrey(getAttackTarget()) && sight(getAttackTarget(), GeminiConfig.wretchDetectionRange)) aim(getAttackTarget());
        if(state == RECOVER) {
            if(attackTicks >= (phase() == 2 ? 16 : 24)) { begin(IDLE); cooldown = phase() == 2 ? 18 : 25; }
            return;
        }
        if(state == HOLD || state == THROW || state == PULL || state == ASSIMILATE) {
            capture.tick(state, attackTicks); return;
        }
        if(state == FEED) {
            if(!GeminiConfig.wretchLivestockFeeding || !eligibleMeal(meal) || !visible(meal, 4.5D)) { recovery(); return; }
            aim(meal);
            if(attackTicks % 4 == 0) {
                double blend = attackTicks / 40D;
                ((WorldServer) worldObj).func_147487_a("reddust", meal.posX + (posX - meal.posX) * blend,
                        meal.posY + 0.5D + blend, meal.posZ + (posZ - meal.posZ) * blend, 5, 0.18D, 0.12D, 0.18D, 0D);
            }
            if(attackTicks == 40) {
                float health = meal.getHealth();
                if(health > 0 && meal.attackEntityFrom(DamageSource.causeMobDamage(this), health + 2F) && !meal.isEntityAlive()) {
                    heal(Math.min(30F, getMaxHealth() * 0.035F)); voice("assimilate", 1.2F);
                }
                recovery();
            }
            return;
        }
        if(state == SWIPE && attackTicks >= 23 && attackTicks <= 32) { strike(false); brain.building.strike(attackArm()); }
        if(state == BREACH && attackTicks >= 26 && attackTicks <= 34) {
            if(attackTicks == 28) soundAt("impact", posX + aimX(), posY + aimY(), posZ + aimZ(), 3F);
            brain.building.strike(attackArm());
        }
        if(state == GRAB && attackTicks >= 28 && attackTicks <= 35) {
            strike(true); if(state() == HOLD) return;
        }
        if(state == SLAM && attackTicks == 34) slam();
        if(attackTicks >= (state == SLAM || state == BREACH ? 44 : 38)) recovery();
    }
    private void strike(boolean grab) {
        int arm = attackArm();
        Vec3 a = WretchPose.point(this, arm, 1, 1), b = WretchPose.point(this, arm, 2, 1);
        Vec3 fingers = pose(1).fingerTip(arm);
        AxisAlignedBB area = GardenerPose.bounds(a, b, WretchPose.ARM_RADIUS);
        AxisAlignedBB handArea = GardenerPose.bounds(b, fingers, 0.3D);
        area.setBounds(Math.min(area.minX, handArea.minX), Math.min(area.minY, handArea.minY), Math.min(area.minZ, handArea.minZ),
                Math.max(area.maxX, handArea.maxX), Math.max(area.maxY, handArea.maxY), Math.max(area.maxZ, handArea.maxZ));
        int examined = 0;
        for(Object entry : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, area)) {
            if(++examined > 48) break;
            EntityLivingBase victim = (EntityLivingBase) entry;
            if(!validPrey(victim) || struck.contains(victim.getUniqueID()) || !attackClear(arm, victim)
                    || !(GardenerPose.touches(a, b, victim.boundingBox, WretchPose.ARM_RADIUS)
                    || GardenerPose.touches(b, fingers, victim.boundingBox, 0.3D))) continue;
            struck.add(victim.getUniqueID());
            if(grab) {
                // A forearm sweep can wound; only the articulated palm/fingers can capture.
                if((GardenerPose.touches(b, fingers, victim.boundingBox, 0.3D) || GardenerPose.touches(b, b, victim.boundingBox, 0.55D)) && capture.take(victim)) return;
                continue;
            }
            if(victim.attackEntityFrom(DamageSource.causeMobDamage(this), phase() == 2 ? 17F : 13F)) knock(victim, 0.95D, 0.3D);
        }
    }
    private void slam() {
        double x = posX + aimX(), z = posZ + aimZ();
        AxisAlignedBB area = AxisAlignedBB.getBoundingBox(x - 4, posY - 2, z - 4, x + 4, posY + 3, z + 4);
        if(!GardenerNests.loaded(worldObj, area.expand(2, 0, 2))) return;
        soundAt("impact", x, posY + 0.4D, z, 4F);
        ((WorldServer) worldObj).func_147487_a("blockcrack_" + net.minecraft.block.Block.getIdFromBlock(net.minecraft.init.Blocks.dirt) + "_0", x, posY + 0.1D, z, 36, 2D, 0.15D, 2D, 0.15D);
        int examined = 0;
        for(Object entry : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, area)) {
            if(++examined > 48) break;
            EntityLivingBase victim = (EntityLivingBase) entry;
            boolean contact = false;
            for(int arm = 0; arm < 2 && !contact; arm++) {
                Vec3 hand = WretchPose.point(this, arm, 2, 1), tip = pose(1).fingerTip(arm);
                contact = (GardenerPose.touches(hand, tip, victim.boundingBox, 0.65) || GardenerPose.touches(hand, hand, victim.boundingBox, 0.8)) && attackClear(arm, victim);
            }
            if(validPrey(victim) && contact && victim.attackEntityFrom(DamageSource.causeMobDamage(this), phase() == 2 ? 21F : 17F)) knock(victim, 0.8D, 0.65D);
        }
        brain.building.strike(0); brain.building.strike(1);
    }
    private void knock(EntityLivingBase victim, double force, double up) {
        double dx = victim.posX - posX, dz = victim.posZ - posZ, length = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
        victim.addVelocity(dx / length * force, up, dz / length * force); victim.velocityChanged = true;
    }
    private void soundAt(String sound, double x, double y, double z, float volume) {
        worldObj.playSoundEffect(x, y, z, "hbm:entity.wretch." + sound, volume, 0.9F + cosmeticRandom.nextFloat() * 0.2F);
    }
    void voice(String sound, float volume) {
        WretchPose p = pose(1);
        soundAt(sound, posX, posY + p.body.yCoord + 3, posZ, volume);
    }
    /** Presentation only: no target selection, path changes, damage or building mutations. */
    private void tickPresentation() {
        if(!isEntityAlive()) return;
        if(isAwake() && !soundAwake) {
            voice("awaken", 3.5F);
            nextFoley = ticksExisted + 60 + cosmeticRandom.nextInt(60); nextVoice = ticksExisted + Math.max(20, GeminiConfig.wretchVoiceSeconds * 20) + cosmeticRandom.nextInt(240);
        }
        soundAwake = isAwake();
        if(!isAwake()) {
            if(ticksExisted >= nextFoley) { voice("breath", 0.45F); nextFoley = ticksExisted + 180 + cosmeticRandom.nextInt(180); }
            return;
        }
        double dx = posX - prevPosX, dz = posZ - prevPosZ;
        stepTravel += Math.sqrt(dx * dx + dz * dz);
        if(onGround && stepTravel > 1.8 && ticksExisted >= nextStep) {
            int foot = (ticksExisted / 13) & 1;
            Vec3 at = WretchPose.point(this, foot + 2, 2, 1);
            soundAt("step", at.xCoord, at.yCoord, at.zCoord, 3.2F);
            stepTravel = 0; nextStep = ticksExisted + 12 + cosmeticRandom.nextInt(8);
        }
        if(ticksExisted >= nextFoley) {
            voice(state() == FEED ? "assimilate" : cosmeticRandom.nextInt(3) == 0 ? "stretch" : "tissue", 0.6F);
            nextFoley = ticksExisted + 75 + cosmeticRandom.nextInt(120);
        }
        if(GeminiConfig.wretchVoiceSeconds > 0 && ticksExisted >= nextVoice) {
            if(anatomy.size() > 0 && cosmeticRandom.nextBoolean()) {
                int seed = anatomy.seed(cosmeticRandom.nextInt(anatomy.size()));
                Vec3 at = pose(1).remainPoint(seed);
                soundAt("voice", at.xCoord, at.yCoord, at.zCoord, 1.5F);
            } else voice("voice", 1.2F);
            int interval = GeminiConfig.wretchVoiceSeconds * 20;
            nextVoice = ticksExisted + interval + cosmeticRandom.nextInt(interval * 2 + 1);
        }
        if(presentation() != WretchPose.WATCH) {
            if(state() != IDLE || presentationTime() >= 1) clearPresentation();
            else if(presentationTime() >= 0.43 && lastPresentation != presentation()) {
                soundAt(presentation() == WretchPose.WINDOW ? "tap" : presentation() == WretchPose.ROOF ? "scrape" : "scratch",
                        posX + aimX(), posY + aimY(), posZ + aimZ(), presentation() == WretchPose.ROOF ? 2.2F : 1.1F);
                lastPresentation = presentation();
            }
        }
    }

    private boolean eligibleMeal(EntityLivingBase entity) {
        if(!(entity instanceof EntityAnimal) || entity instanceof IBossDisplayData || entity instanceof IGeminiCreature
                || !entity.isEntityAlive() || entity.worldObj != worldObj || !entity.addedToChunk || !inTerritory(entity.posX, entity.posZ, 28)
                || entity.ridingEntity != null || entity.riddenByEntity != null || entity.isEntityInvulnerable()
                || ((EntityLiving) entity).hasCustomNameTag() || ((EntityLiving) entity).getLeashed()
                || ((EntityAnimal) entity).isChild() || ((EntityAnimal) entity).isInLove()
                || entity instanceof EntityPig && ((EntityPig) entity).getSaddled()
                || entity.getEntityData().getBoolean("WretchProtected") || entity.getEntityData().hasKey("MockeryCaptor")) return false;
        Class<?> type = entity.getClass();
        // Exact vanilla types exclude optional mod bosses, machines, pets and inventory-bearing mounts.
        return type == EntityCow.class || type == EntityMooshroom.class || type == EntityPig.class
                || type == EntitySheep.class || type == EntityChicken.class;
    }
    private boolean findMeal() {
        int examined = 0;
        for(Object entry : worldObj.getEntitiesWithinAABB(EntityAnimal.class, boundingBox.expand(4, 2, 4))) {
            if(++examined > 32) break;
            EntityLivingBase candidate = (EntityLivingBase) entry;
            if(eligibleMeal(candidate) && visible(candidate, 4.5D)) { meal = candidate; aim(meal); return true; }
        }
        return false;
    }

    public AxisAlignedBB partBox(int slot) {
        return pose(1).bounds(slot);
    }
    public boolean hitPart(int slot, DamageSource source, float damage) {
        if(worldObj.isRemote || !isOperational() || slot < 0 || slot >= parts.length) return false;
        {
            if(source.isProjectile() && source.getSourceOfDamage() != null) {
                Entity shot = source.getSourceOfDamage(); Vec3 from = Vec3.createVectorHelper(shot.posX, shot.posY, shot.posZ);
                if(!pose(1).rayTouches(slot, from, from.addVector(shot.motionX, shot.motionY, shot.motionZ), shot.width * 0.5D)) return false;
            } else if(source.getEntity() instanceof EntityPlayer && !source.isExplosion()) {
                EntityPlayer p = (EntityPlayer) source.getEntity(); Vec3 eye = Vec3.createVectorHelper(p.posX, p.boundingBox.minY + p.getEyeHeight(), p.posZ), look = p.getLookVec();
                double reach = p.capabilities.isCreativeMode ? 6D : 4.5D;
                Vec3 end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
                if(!GardenerNests.loaded(worldObj, GardenerPose.bounds(eye, end, 1D))) return false;
                MovingObjectPosition wall = worldObj.func_147447_a(Vec3.createVectorHelper(eye.xCoord, eye.yCoord, eye.zCoord), end, false, true, false);
                if(!pose(1).rayTouches(slot, eye, wall == null ? end : wall.hitVec, 0)) return false;
            }
        }
        boolean hit = damage(source, damage);
        if(hit && (slot < WretchPose.ARM_PARTS || slot >= WretchPose.HEAD_END && slot < WretchPose.HAND_END + 2)
                && damage >= 5F && (state() == GRAB || state() == HOLD || state() == THROW || state() == PULL || state() == ASSIMILATE)) recovery();
        return hit;
    }
    @Override public boolean attackEntityFrom(DamageSource source, float damage) {
        // The navigation box spans the legs' empty centre. Direct hits must meet visible anatomy.
        Entity attacker = source.getEntity(), shot = source.getSourceOfDamage();
        if(!source.isExplosion() && (source.isProjectile() && shot != null || attacker instanceof EntityPlayer)) {
            Vec3 from, to;
            double extra = 0;
            if(source.isProjectile() && shot != null) {
                from = Vec3.createVectorHelper(shot.posX, shot.posY, shot.posZ);
                to = from.addVector(shot.motionX, shot.motionY, shot.motionZ); extra = shot.width * 0.5D;
            } else {
                EntityPlayer p = (EntityPlayer) attacker;
                from = Vec3.createVectorHelper(p.posX, p.boundingBox.minY + p.getEyeHeight(), p.posZ);
                Vec3 look = p.getLookVec(); double reach = p.capabilities.isCreativeMode ? 6 : 4.5;
                to = from.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
                if(!GardenerNests.loaded(worldObj, GardenerPose.bounds(from, to, 1))) return false;
                MovingObjectPosition wall = worldObj.func_147447_a(Vec3.createVectorHelper(from.xCoord, from.yCoord, from.zCoord), to, false, true, false);
                if(wall != null) to = wall.hitVec;
            }
            WretchPose pose = pose(1);
            for(int i = 0; i < WretchPose.PARTS; i++) if(pose.rayTouches(i, from, to, extra)) return hitPart(i, source, damage);
            return false;
        }
        return damage(source, damage);
    }
    private boolean damage(DamageSource source, float damage) {
        if(worldObj.isRemote || !active() || damage <= 0 || Float.isNaN(damage) || Float.isInfinite(damage)
                || source.getEntity() instanceof IGeminiCreature) return false;
        float before = getHealth();
        boolean result = super.attackEntityFrom(source, damage);
        if(result && isEntityAlive()) {
            if(before > getHealth()) brain.injured(source.getEntity());
            interruptDamage += Math.max(0, before - getHealth());
            if(interruptDamage >= 12F && (state() == GRAB || state() == HOLD || state() == THROW || state() == PULL || state() == ASSIMILATE || state() == FEED)) recovery();
        }
        return result;
    }
    private void maintainParts() {
        for(int i = 0; i < parts.length; i++) if(parts[i] == null || parts[i].isDead || !parts[i].addedToChunk) {
            if(parts[i] != null) parts[i].setDead();
            EntityWretchPart part = new EntityWretchPart(worldObj); part.attach(this, i);
            if(GardenerNests.loaded(worldObj, part.boundingBox.expand(1, 0, 1)) && worldObj.spawnEntityInWorld(part)) parts[i] = part;
        }
    }
    private void removeParts() { for(int i = 0; i < parts.length; i++) if(parts[i] != null) { parts[i].setDead(); parts[i] = null; } }
    @Override public void onDeath(DamageSource source) {
        super.onDeath(source);
        if(!worldObj.isRemote && dead) { release(); removeParts(); WretchCamps.defeated(this); }
    }
    @Override protected void dropFewItems(boolean player, int looting) { dropItem(Items.bone, 8 + rand.nextInt(5)); dropItem(Items.rotten_flesh, 12 + rand.nextInt(9)); }
    @Override public void setDead() { if(!worldObj.isRemote) { release(); removeParts(); } super.setDead(); }
    @Override public void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        nbt.setBoolean("WretchCamp", campBound); nbt.setInteger("CampX", homeX); nbt.setInteger("CampY", homeY); nbt.setInteger("CampZ", homeZ);
        nbt.setBoolean("WretchEggSpawned", eggSpawned);
        nbt.setInteger("WretchCooldown", cooldown); nbt.setInteger("FeedCooldown", feedCooldown); nbt.setInteger("LastAttack", lastAttack);
        brain.write(nbt); anatomy.write(nbt);
        // In-flight attacks/captures are transient. Vanilla NBT owns health, attributes and position.
    }
    @Override public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt); fromSave = true;
        campBound = nbt.getBoolean("WretchCamp"); homeX = nbt.getInteger("CampX"); homeY = nbt.getInteger("CampY"); homeZ = nbt.getInteger("CampZ");
        eggSpawned = !campBound && nbt.getBoolean("WretchEggSpawned");
        cooldown = Math.max(80, MathHelper.clamp_int(nbt.getInteger("WretchCooldown"), 0, 200));
        feedCooldown = Math.max(200, MathHelper.clamp_int(nbt.getInteger("FeedCooldown"), 0, 600));
        lastAttack = MathHelper.clamp_int(nbt.getInteger("LastAttack"), SWIPE, GRAB);
        release(); meal = null; begin(IDLE); brain.read(nbt); anatomy.read(nbt);
    }
}
