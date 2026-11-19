package com.hbm.entity.mob.gemini;

import java.util.UUID;
import com.hbm.config.GeminiConfig;
import com.hbm.handler.GeminiCaptures;
import com.hbm.handler.disease.DeepRootDisease;
import com.hbm.handler.disease.DiseaseManager;
import com.hbm.handler.disease.DiseaseHost;
import com.hbm.handler.disease.Infection;
import com.hbm.world.gemini.GeminiInfections;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.MockeryGrowth;
import com.hbm.world.gemini.MockerySpawning;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IEntityLivingData;
import com.hbm.world.gemini.GeminiSpawning;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Rooted predator. One live captive, bounded assimilation, finite colony and sterile buds. */
public final class EntityNaturesMockery extends EntityMob implements IDeepRootCarrier {
    private static final int ASSIMILATION_TICKS = 220, ASSIMILATION_LIMIT = 4;
    private static final int LIFT_TICKS = 18;
    private UUID captive;
    private EntityLivingBase captiveEntity;
    private int missingTicks, liftTicks, obstructedTicks;
    private int captureTicks, cooldown, growthTicks, spreadTicks, placed, buds, nutrition;
    private boolean sterile, rooted;
    private boolean assimilating;
    private int assimilations;
    private double rootX, rootY, rootZ, anchorX, anchorY, anchorZ;
    private String phrase = "";
    private int phraseTicks;
    public float extension, prevExtension;

    public EntityNaturesMockery(World world) {
        super(world);
        setSize(1.1F, 2.4F);
        experienceValue = 12;
        func_110163_bv();
    }
    @Override public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        GeminiSpawning.initialize(this);
        return super.onSpawnWithEgg(data);
    }
    @Override protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(20, Integer.valueOf(-1)); // Actual target runtime ID; UUID is authoritative on server/save.
        dataWatcher.addObject(21, Integer.valueOf(0));
        dataWatcher.addObject(22, Byte.valueOf((byte) 0));
    }
    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(70D);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0D);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1D);
    }
    @Override public boolean isAIEnabled() { return true; }
    @Override protected boolean isMovementBlocked() { return true; }
    @Override public void moveEntity(double x, double y, double z) { super.moveEntity(0, y, 0); }
    @Override public void applyEntityCollision(Entity entity) { }
    @Override protected boolean canDespawn() { return false; }
    @Override public boolean getCanSpawnHere() { return MockerySpawning.canSpawn(worldObj, this) && MockerySpawning.space(this, null); }
    @Override protected String getLivingSound() { return null; }
    @Override protected String getHurtSound() { return "mob.slime.big"; }
    @Override protected String getDeathSound() { return "mob.ghast.death"; }
    public int getGrowth() { return dataWatcher.getWatchableObjectInt(21); }
    public boolean isHolding() { return dataWatcher.getWatchableObjectByte(22) != 0; }
    public EntityLivingBase tendrilTarget() {
        int id = dataWatcher.getWatchableObjectInt(20);
        Entity entity = id < 0 ? null : worldObj.getEntityByID(id);
        return entity instanceof EntityLivingBase && entity.isEntityAlive() ? (EntityLivingBase) entity : null;
    }
    public boolean owns(EntityLivingBase target) { return captive != null && captive.equals(target.getUniqueID()); }
    public boolean canCompleteAssimilation(EntityLivingBase target) {
        return assimilating && captureTicks >= ASSIMILATION_TICKS && assimilations < ASSIMILATION_LIMIT
                && liftTicks >= LIFT_TICKS && isHolding() && owns(target) && GeminiCaptures.valid(this, target);
    }
    public boolean withinColony(double x, double y, double z) {
        double dx = x - rootX, dy = y - rootY, dz = z - rootZ;
        return dx * dx + dy * dy + dz * dz <= GeminiConfig.mockeryRadius * GeminiConfig.mockeryRadius;
    }
    /** Nest support uses the existing colony/growth system, without creating a new breeding population. */
    public void seedGardenerColony(double x, double y, double z) {
        sterile = true; rooted = true; rootX = x; rootY = y; rootZ = z;
        nutrition = 20; dataWatcher.updateObject(21, Integer.valueOf(20));
    }
    private boolean prey(EntityLivingBase target) {
        return target != null && target != this && target.worldObj == worldObj && target.isEntityAlive()
                && target.addedToChunk && target.ridingEntity == null && target.riddenByEntity == null
                && (owns(target) || GeminiCaptures.available(target))
                && !target.getEntityData().hasKey("GardenerCaptor")
                && !com.hbm.entity.mob.gemini.EntityWretch.isCaptured(target)
                && target.getEntityData().getLong("WoodcrawlerCocoonUntil") <= worldObj.getTotalWorldTime()
                && target.getEntityData().getLong("WoodcrawlerCaptorUntil") <= worldObj.getTotalWorldTime()
                && (!target.getEntityData().hasKey("MockeryCaptor")
                    || getUniqueID().toString().equals(target.getEntityData().getString("MockeryCaptor")))
                && DiseaseManager.pathogen(DeepRootDisease.ID).supports(target);
    }
    private boolean reach(EntityLivingBase target) {
        if(!prey(target) || getDistanceSqToEntity(target) > 25D) return false;
        // Do not let a ray test load a neighboring chunk.
        int x0 = MathHelper.floor_double(Math.min(posX, target.posX)) >> 4;
        int x1 = MathHelper.floor_double(Math.max(posX, target.posX)) >> 4;
        int z0 = MathHelper.floor_double(Math.min(posZ, target.posZ)) >> 4;
        int z1 = MathHelper.floor_double(Math.max(posZ, target.posZ)) >> 4;
        for(int x = x0; x <= x1; x++) for(int z = z0; z <= z1; z++)
            if(!worldObj.getChunkProvider().chunkExists(x, z)) return false;
        return worldObj.func_147447_a(Vec3.createVectorHelper(posX, posY + 1.2D, posZ),
                Vec3.createVectorHelper(target.posX, target.boundingBox.minY + target.height * 0.5D, target.posZ), false, true, false) == null;
    }

    @Override public void onUpdate() {
        if(!worldObj.isRemote && !MockerySpawning.active(this)) {
            release(); motionX = motionY = motionZ = 0; return;
        }
        prevExtension = extension;
        extension += ((dataWatcher.getWatchableObjectInt(20) >= 0 ? 1F : 0F) - extension) * 0.16F;
        motionX = motionZ = 0;
        super.onUpdate();
        if(worldObj.isRemote) return;
        if(!rooted) { rootX = posX; rootY = posY; rootZ = posZ; rooted = true; }
        if(!isEntityAlive() || !MockerySpawning.active(this)) { release(); return; }
        if(cooldown > 0) cooldown--;
        if(phraseTicks > 0 && --phraseTicks == 0) phrase = "";
        if(captive != null) tickCapture();
        else if(cooldown == 0 && (ticksExisted + getEntityId()) % 10 == 0) findPrey();
        if(++growthTicks >= GeminiConfig.mockeryGrowthSeconds * 20) {
            growthTicks = 0;
            if(nutrition > 0 && getGrowth() < 100) {
                nutrition--;
                dataWatcher.updateObject(21, Integer.valueOf(getGrowth() + 1));
            }
        }
        if(++spreadTicks >= GeminiConfig.mockerySpreadSeconds * 20) {
            spreadTicks = 0;
            int radius = Math.min(GeminiConfig.mockeryRadius, 2 + getGrowth() / 15);
            int x = MathHelper.floor_double(rootX) + rand.nextInt(radius * 2 + 1) - radius;
            int z = MathHelper.floor_double(rootZ) + rand.nextInt(radius * 2 + 1) - radius;
            int y = MathHelper.floor_double(rootY) + rand.nextInt(5) - 2;
            if(placed < GeminiConfig.mockerySpreadLimit && MockeryGrowth.place(this, x, y, z)) placed++;
            if(com.hbm.world.gemini.GeminiInvasionManager.canActivityOccur(worldObj, 3)
                    && !sterile && buds < GeminiConfig.mockeryOffshootLimit && getGrowth() >= 40 * (buds + 1)
                    && rand.nextInt(4) == 0) bud(x, y, z);
        }
        if(ticksExisted % 240 == 0 && rand.nextBoolean()) {
            playSound("mob.endermen.stare", 0.55F, 0.55F);
            if(phraseTicks > 0) for(Object entry : worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(12, 6, 12))) {
                EntityPlayer player = (EntityPlayer) entry;
                if(getDistanceSqToEntity(player) <= 144D)
                    player.addChatMessage(new ChatComponentText("Nature's Mockery whispers: \"" + phrase + "\""));
            }
        }
    }

    private void findPrey() {
        EntityLivingBase closest = null;
        double distance = 25D;
        int examined = 0;
        for(Object entry : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, boundingBox.expand(5, 3, 5))) {
            if(++examined > 32) break;
            EntityLivingBase target = (EntityLivingBase) entry;
            if(target.getEntityData().getLong("MockeryEscapeUntil") > worldObj.getTotalWorldTime()
                    || target.getEntityData().hasKey("MockeryCaptor") || !reach(target)) continue;
            double d = getDistanceSqToEntity(target);
            if(d < distance) { closest = target; distance = d; }
        }
        if(closest == null) return;
        captive = closest.getUniqueID();
        captiveEntity = closest;
        captureTicks = 0;
        assimilating = false;
        closest.getEntityData().setString("MockeryCaptor", getUniqueID().toString());
        closest.getEntityData().setInteger("MockeryCaptorId", getEntityId());
        dataWatcher.updateObject(20, Integer.valueOf(closest.getEntityId()));
    }

    private void tickCapture() {
        if(captiveEntity == null) {
            if(++missingTicks > 100) { release("Saved victim missing"); return; }
            if(missingTicks % 10 != 1) return;
            int examined = 0;
            for(Object entry : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, boundingBox.expand(5, 3, 5))) {
                if(++examined > 32) break;
                EntityLivingBase candidate = (EntityLivingBase) entry;
                if(captive.equals(candidate.getUniqueID()) && getUniqueID().toString().equals(candidate.getEntityData().getString("MockeryCaptor"))) {
                    if(!prey(candidate) || (isHolding() ? !bind(candidate) : !reach(candidate))) {
                        captiveEntity = candidate;
                        release("Saved grip invalid");
                        return;
                    }
                    captiveEntity = candidate;
                    candidate.getEntityData().setInteger("MockeryCaptorId", getEntityId());
                    dataWatcher.updateObject(20, Integer.valueOf(candidate.getEntityId()));
                    break;
                }
            }
            if(captiveEntity == null) return;
        }
        EntityLivingBase target = captiveEntity;
        if(isHolding() ? !prey(target) || !GeminiCaptures.valid(this, target) : !reach(target)) {
            release("Victim left the tendrils");
            return;
        }
        captureTicks = Math.min(ASSIMILATION_TICKS, captureTicks + 1);
        if(captureTicks == 20 && !isHolding()) {
            anchorX = target.posX; anchorY = target.posY; anchorZ = target.posZ;
            if(!bind(target)) { release("Victim could not be attached"); return; }
            dataWatcher.updateObject(22, Byte.valueOf((byte) 1));
            playSound("mob.slime.attack", 0.9F, 0.5F);
            GeminiInfections.expose(target, "capture:" + getUniqueID(), "natures_mockery");
            assimilating = !(target instanceof EntityPlayer) && assimilations < ASSIMILATION_LIMIT
                    && worldObj.getGameRules().getGameRuleBooleanValue("doMobSpawning")
                    && DiseaseHost.get(target).infections.containsKey(DeepRootDisease.ID) && DeepRootDisease.active(target);
            GeminiCaptures.diagnostic(this, target, "lift", "Initial attachment", captureTicks, assimilationPoint(target));
        }
        if(isHolding()) {
            boolean atPoint = drawIn(target);
            if(captiveEntity != target) return;
            if(assimilating) {
                Infection infection = DiseaseHost.get(target).infections.get(DeepRootDisease.ID);
                if(infection == null || !DeepRootDisease.active(target)
                        || !worldObj.getGameRules().getGameRuleBooleanValue("doMobSpawning")) { release("Assimilation interrupted"); return; }
                // Reuse the saved disease stages and tracking packet for the restrained host's visible transformation.
                int stage = Math.min(DeepRootDisease.Stage.SPROUTS.ordinal(), (captureTicks - 20) / 40);
                if(DeepRootDisease.stage(infection).ordinal() < stage)
                    DeepRootDisease.selectStage(target, DeepRootDisease.Stage.values()[stage]);
                if(captureTicks >= ASSIMILATION_TICKS && atPoint) {
                    boolean converted = MockerySpawning.assimilate(target, this);
                    if(converted) { assimilations++; nutrition = Math.min(100, nutrition + 20); }
                    release(converted ? "Assimilation completed" : "Assimilation rejected");
                    if(converted) cooldown = 600;
                    return;
                }
            }
        }
        if(!assimilating && captureTicks >= 100) release("Hold completed"); // Same four-second player hold.
    }

    private boolean bind(EntityLivingBase target) {
        return GeminiCaptures.attach(this, target, false, this::release);
    }

    private Vec3 assimilationPoint(EntityLivingBase target) {
        double angle = Math.atan2(anchorZ - posZ, anchorX - posX);
        // Leave room beside the trunk for the replacement's own collision box, including at diagonal grips.
        return Vec3.createVectorHelper(posX + Math.cos(angle) * 1.65D,
                boundingBox.minY + Math.max(1.2D, target.height * 0.5D + 0.18D),
                posZ + Math.sin(angle) * 1.65D);
    }

    private boolean drawIn(EntityLivingBase target) {
        Vec3 goal = assimilationPoint(target);
        double lift = GeminiCaptures.liftProgress(liftTicks + 1, LIFT_TICKS);
        double startY = anchorY - (target.posY - target.boundingBox.minY) + target.height * 0.5D;
        double x = anchorX + (goal.xCoord - anchorX) * lift;
        double y = startY + (goal.yCoord - startY) * lift;
        double z = anchorZ + (goal.zCoord - anchorZ) * lift;
        AxisAlignedBB swept = target.boundingBox.addCoord(x - target.posX,
                y - target.boundingBox.minY - target.height * 0.5D, z - target.posZ).contract(0.001, 0.001, 0.001);
        if(!GardenerNests.loaded(worldObj, swept) || !worldObj.func_147461_a(swept).isEmpty()) {
            if(++obstructedTicks >= 20) release("Tendril path obstructed");
            return false;
        }
        obstructedTicks = 0;
        GeminiCaptures.center(target, x, y, z);
        if(liftTicks < LIFT_TICKS && ++liftTicks == LIFT_TICKS)
            GeminiCaptures.diagnostic(this, target, assimilating ? "assimilating" : "holding",
                    "Reached tendril attachment", captureTicks, goal);
        return liftTicks >= LIFT_TICKS;
    }

    private void release() { release("Captor inactive or removed"); }
    private void release(String reason) {
        EntityLivingBase target = captiveEntity;
        if(target != null && captive != null && captive.equals(target.getUniqueID())) {
            GeminiCaptures.diagnostic(this, target, assimilating ? "assimilating" : "holding", reason, captureTicks, null);
            GeminiCaptures.release(this, target);
            NBTTagCompound tag = target.getEntityData();
            if(getUniqueID().toString().equals(tag.getString("MockeryCaptor"))) {
                tag.removeTag("MockeryCaptor"); tag.removeTag("MockeryCaptorId");
            }
            tag.setLong("MockeryEscapeUntil", worldObj.getTotalWorldTime() + 400L);
        }
        captiveEntity = null;
        captive = null; captureTicks = 0; cooldown = 100; assimilating = false;
        missingTicks = liftTicks = obstructedTicks = 0;
        dataWatcher.updateObject(20, Integer.valueOf(-1));
        dataWatcher.updateObject(22, Byte.valueOf((byte) 0));
    }

    private void bud(int x, int y, int z) {
        if(!worldObj.getGameRules().getGameRuleBooleanValue("doMobSpawning")
                || !withinColony(x + 0.5D, y, z + 0.5D) || !MockerySpawning.canSpawn(worldObj, null)
                || getDistanceSq(x + 0.5D, y, z + 0.5D) < 9D) return;
        EntityNaturesMockery child = new EntityNaturesMockery(worldObj);
        child.sterile = true; child.rooted = true;
        child.rootX = rootX; child.rootY = rootY; child.rootZ = rootZ;
        child.setLocationAndAngles(x + 0.5D, y, z + 0.5D, rand.nextFloat() * 360F, 0);
        if(MockerySpawning.space(child, null) && worldObj.spawnEntityInWorld(child)) buds++;
    }

    public void hear(String text) {
        if(!MockerySpawning.active(this) || phraseTicks > 0) return;
        phrase = net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(text).replaceAll("[\\p{Cntrl}]", "");
        if(phrase.length() > 48) phrase = phrase.substring(0, 48);
        phraseTicks = phrase.isEmpty() ? 0 : 1200;
    }
    @Override public boolean attackEntityFrom(DamageSource source, float damage) {
        return !worldObj.isRemote && MockerySpawning.active(this) && !(source.getEntity() instanceof IGeminiCreature)
                && super.attackEntityFrom(source, damage);
    }
    @Override public void onDeath(DamageSource source) { if(!worldObj.isRemote) release(); super.onDeath(source); }
    @Override public void setDead() { if(!worldObj.isRemote) release(); super.setDead(); }

    @Override public void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        nbt.setInteger("Growth", getGrowth()); nbt.setInteger("Nutrition", nutrition);
        nbt.setInteger("GrowthTicks", growthTicks); nbt.setInteger("SpreadTicks", spreadTicks);
        nbt.setInteger("Placed", placed); nbt.setInteger("Buds", buds); nbt.setBoolean("Sterile", sterile);
        nbt.setBoolean("Rooted", rooted); nbt.setDouble("RootX", rootX); nbt.setDouble("RootY", rootY); nbt.setDouble("RootZ", rootZ);
        nbt.setInteger("CaptureTicks", captureTicks); nbt.setInteger("CaptureCooldown", cooldown);
        nbt.setInteger("CaptureLiftTicks", liftTicks);
        nbt.setBoolean("Assimilating", assimilating); nbt.setInteger("Assimilations", assimilations);
        if(captive != null) nbt.setString("CaptiveUUID", captive.toString());
        nbt.setDouble("AnchorX", anchorX); nbt.setDouble("AnchorY", anchorY); nbt.setDouble("AnchorZ", anchorZ);
        nbt.setString("Phrase", phrase); nbt.setInteger("PhraseTicks", phraseTicks);
    }
    @Override public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt);
        dataWatcher.updateObject(21, Integer.valueOf(clamp(nbt, "Growth", 100)));
        nutrition = clamp(nbt, "Nutrition", 100); growthTicks = clamp(nbt, "GrowthTicks", GeminiConfig.mockeryGrowthSeconds * 20);
        spreadTicks = clamp(nbt, "SpreadTicks", GeminiConfig.mockerySpreadSeconds * 20);
        placed = clamp(nbt, "Placed", 4096); buds = clamp(nbt, "Buds", 2); sterile = nbt.getBoolean("Sterile");
        rooted = nbt.getBoolean("Rooted"); rootX = nbt.getDouble("RootX"); rootY = nbt.getDouble("RootY"); rootZ = nbt.getDouble("RootZ");
        captureTicks = clamp(nbt, "CaptureTicks", ASSIMILATION_TICKS); cooldown = clamp(nbt, "CaptureCooldown", 600);
        liftTicks = clamp(nbt, "CaptureLiftTicks", LIFT_TICKS);
        assimilating = nbt.getBoolean("Assimilating"); assimilations = clamp(nbt, "Assimilations", ASSIMILATION_LIMIT);
        try { captive = UUID.fromString(nbt.getString("CaptiveUUID")); } catch(IllegalArgumentException ignored) { captive = null; }
        dataWatcher.updateObject(22, Byte.valueOf((byte) (captive != null && captureTicks >= 20 ? 1 : 0)));
        anchorX = nbt.getDouble("AnchorX"); anchorY = nbt.getDouble("AnchorY"); anchorZ = nbt.getDouble("AnchorZ");
        phrase = nbt.getString("Phrase"); if(phrase.length() > 48) phrase = phrase.substring(0, 48);
        phraseTicks = clamp(nbt, "PhraseTicks", 1200);
    }
    private static int clamp(NBTTagCompound nbt, String key, int max) { return MathHelper.clamp_int(nbt.getInteger(key), 0, max); }
}
