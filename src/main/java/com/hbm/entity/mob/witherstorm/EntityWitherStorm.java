package com.hbm.entity.mob.witherstorm;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.hbm.config.WitherStormConfig;
import api.hbm.entity.IRadiationImmune;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/** Hostile encounter root. Command Block Wither and all evolved forms share this identity. */
public class EntityWitherStorm extends EntityFlying implements IBossDisplayData, IMob, IRadiationImmune {
    private final UUID[] partIds = new UUID[10];
    private final EntityWitherStormPart[] parts = new EntityWitherStormPart[10];
    private final int[] partChunksX = new int[10], partChunksZ = new int[10];
    private final EntityLivingBase[] targets = new EntityLivingBase[3];
    private final UUID[] targetIds = new UUID[3];
    private final int[] attacks = new int[] {40, 60, 80};
    private final List<Entity> nearby = new ArrayList<Entity>(64);
    public final WitherStormTerrain terrain = new WitherStormTerrain(this);
    public boolean registered;
    private int searchOffset, roarTimer = 200;
    private int lastGrowthSoundMass;
    private float visualScale = 0.35F, previousVisualScale = 0.35F;
    private boolean restoring;

    public EntityWitherStorm(World world) {
        super(world);
        setSize(2F, 4F);
        noClip = true; isImmuneToFire = true; ignoreFrustumCheck = true;
        experienceValue = 500;
    }
    @Override protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(20, Integer.valueOf(0)); // consumed mass
        dataWatcher.addObject(21, Byte.valueOf((byte)0)); // phase
        dataWatcher.addObject(22, Byte.valueOf((byte)1)); // command core intact
        dataWatcher.addObject(23, Integer.valueOf(0)); // transition ticks remaining
        for(int i = 0; i < 3; i++) dataWatcher.addObject(24 + i, Integer.valueOf(0));
        dataWatcher.addObject(27, Float.valueOf(0.35F)); // server-configured growth scale
    }
    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(300D);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(64D);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1D);
    }
    @Override protected void updateEntityActionState() { } // Flight/attacks below own all AI.
    @Override protected void despawnEntity() { }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public EnumCreatureAttribute getCreatureAttribute() { return EnumCreatureAttribute.UNDEAD; }
    @Override public void addPotionEffect(PotionEffect effect) { if(effect.getPotionID() != Potion.wither.id) super.addPotionEffect(effect); }
    public int getGrowth() { return dataWatcher.getWatchableObjectInt(20); }
    public WitherStormPhase getPhase() { return WitherStormPhase.values()[Math.max(0, Math.min(3, dataWatcher.getWatchableObjectByte(21)))]; }
    public boolean hasCore() { return dataWatcher.getWatchableObjectByte(22) != 0; }
    public int transitionTicks() { return dataWatcher.getWatchableObjectInt(23); }
    public int headTarget(int head) { return dataWatcher.getWatchableObjectInt(24 + head); }
    public float getStormScale() {
        return dataWatcher.getWatchableObjectFloat(27);
    }
    public float renderScale(float partial) { return previousVisualScale + (visualScale - previousVisualScale) * partial; }
    public float attackStrength() { return 8F + getPhase().ordinal() * 5F + Math.min(16F, getGrowth() / 25000F); }
    public void addMass(int amount) { if(amount > 0) setGrowth((int)Math.min(1000000000L, (long)getGrowth() + amount)); }
    public void setGrowth(int mass) {
        if(worldObj.isRemote) return;
        WitherStormPhase before = getPhase(), after = WitherStormPhase.forGrowth(Math.max(0, mass));
        float oldMax = getMaxHealth(), health = getHealth();
        dataWatcher.updateObject(20, Math.max(0, Math.min(1000000000, mass)));
        dataWatcher.updateObject(21, (byte)after.ordinal());
        dataWatcher.updateObject(27, after == WitherStormPhase.COMMAND_BLOCK ? 0.35F + 0.65F * Math.min(1F, (float)getGrowth() / WitherStormConfig.transform)
            : (float)Math.min(2.8D, 1D + Math.log1p((double)getGrowth() / 12500D) * 0.3D));
        // Vanilla 1.7.10 clamps maxHealth at 1024. Keep the boss inside that contract.
        float max = Math.min(1024F, 300F + (float)Math.sqrt(getGrowth()) * 1.1F);
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(max);
        setHealth(Math.min(max, health + Math.max(0F, max - oldMax)));
        if(before != after && !restoring) {
            dataWatcher.updateObject(23, 80);
            playSound("hbm:entity.witherStorm.transform", 2F, 1F);
        }
    }
    @Override public String getCommandSenderName() {
        return hasCustomNameTag() ? getCustomNameTag() : (getPhase() == WitherStormPhase.COMMAND_BLOCK ? "Command Block Wither" : "Wither Storm — " + getPhase().name());
    }
    @Override public void onLivingUpdate() {
        if(!worldObj.isRemote && registered && !WitherStormEncounters.get(worldObj).contains(getUniqueID())) { setDead(); return; }
        previousVisualScale = visualScale;
        visualScale += (getStormScale() - visualScale) * 0.05F;
        if(!worldObj.isRemote && isEntityAlive()) {
            if(!registered) { if(!WitherStormEncounters.get(worldObj).admit(this)) { setDead(); return; } registered = true; }
            if(transitionTicks() > 0) dataWatcher.updateObject(23, transitionTicks() - 1);
            if(ticksExisted % 20 == 1) {
                WitherStormEncounters.get(worldObj).pruneProjectiles(this);
                WitherStormMath.nearby(this, 64, nearby, searchOffset++);
                chooseTargets();
            }
            for(int i = 0; i < 3; i++) {
                if(!WitherStormMath.edible(targets[i], worldObj) || getDistanceSqToEntity(targets[i]) > 96D * 96D) { targets[i] = null; dataWatcher.updateObject(24 + i, 0); }
            }
            boolean complete = maintainParts();
            fly(complete);
            for(int i = 0; i < 3; i++) if(--attacks[i] <= 0) {
                attacks[i] = Math.max(10, WitherStormConfig.attackTicks / (1 + getPhase().ordinal())) + rand.nextInt(10);
                EntityLivingBase target = targets[i];
                if(target != null && canEntityBeSeen(target)) fire(i, target);
            }
            interact();
            terrain.tick();
            if(--roarTimer <= 0) { playSound("hbm:entity.witherStorm.roar", 2F, 0.9F); roarTimer = 300 + rand.nextInt(200); }
            if(ticksExisted % 100 == 0) playSound("hbm:entity.witherStorm.hum", 0.7F, 1F);
            if(ticksExisted % 160 == 0 && getGrowth() > lastGrowthSoundMass) { playSound("hbm:entity.witherStorm.grow", 1F, 1F); lastGrowthSoundMass = getGrowth(); }
            if(getPhase() == WitherStormPhase.THUNDERSTORM && ticksExisted % 100 == 0 && !worldObj.provider.hasNoSky) {
                // A short local encounter refresh leaves normal weather expiry in charge afterwards.
                worldObj.getWorldInfo().setRaining(true); worldObj.getWorldInfo().setThundering(true);
                worldObj.getWorldInfo().setRainTime(200); worldObj.getWorldInfo().setThunderTime(200);
            }
        }
        float s = getStormScale();
        float wantedWidth = getPhase() == WitherStormPhase.COMMAND_BLOCK ? 2F : 8F * s;
        float wantedHeight = getPhase() == WitherStormPhase.COMMAND_BLOCK ? 4F : 12F * s;
        if(width != wantedWidth || height != wantedHeight) {
            // 1.7.10 setSize moves a growing entity toward its old box corner.
            // Keep growth and reload sizing centered on the saved flight position.
            double x = posX, y = posY, z = posZ;
            setSize(wantedWidth, wantedHeight); setPosition(x, y, z);
        }
        fallDistance = 0;
        super.onLivingUpdate();
    }
    private void chooseTargets() {
        for(int head = 0; head < 3; head++) {
            EntityLivingBase best = null; double score = Double.MAX_VALUE;
            Vec3 mouth = mouth(head);
            for(Entity e : nearby) {
                if(!(e instanceof EntityPlayer) && !(e instanceof IMob)) continue;
                if(!WitherStormMath.edible(e, worldObj)) continue;
                double d = e.getDistanceSq(mouth.xCoord, mouth.yCoord, mouth.zCoord);
                if(e instanceof EntityPlayer) d *= 0.5D;
                for(int previous = 0; previous < head; previous++) if(e == targets[previous]) d *= 2D;
                if(targetIds[head] != null && targetIds[head].equals(e.getUniqueID())) d *= 0.9D;
                if(d < score) { score = d; best = (EntityLivingBase)e; }
            }
            targets[head] = best; targetIds[head] = best == null ? null : best.getUniqueID();
            dataWatcher.updateObject(24 + head, best == null ? 0 : best.getEntityId());
        }
    }
    private void fly(boolean complete) {
        EntityLivingBase target = targets[0];
        double tx = target == null ? posX : target.posX, tz = target == null ? posZ : target.posZ;
        double ground = posY - 12;
        if(WitherStormMath.loaded(worldObj, posX, posZ)) {
            Chunk chunk = worldObj.getChunkProvider().provideChunk(MathHelper.floor_double(posX) >> 4, MathHelper.floor_double(posZ) >> 4);
            ground = chunk.getHeightValue(MathHelper.floor_double(posX) & 15, MathHelper.floor_double(posZ) & 15);
        }
        double ty = Math.min(200, Math.max(ground + (getPhase() == WitherStormPhase.COMMAND_BLOCK ? 7 : 15), target == null ? ground + 15 : target.posY + 12));
        double dx = tx - posX, dz = tz - posZ, distance = Math.sqrt(dx * dx + dz * dz);
        double speed = getPhase() == WitherStormPhase.COMMAND_BLOCK ? 0.22D : 0.32D;
        motionX = WitherStormMath.limit(motionX * 0.92D + (distance > 6 ? dx / distance * 0.025D : 0), speed);
        motionZ = WitherStormMath.limit(motionZ * 0.92D + (distance > 6 ? dz / distance * 0.025D : 0), speed);
        motionY = WitherStormMath.limit(motionY * 0.9D + (ty - posY) * 0.004D, 0.24D);
        double margin = Math.max(34D, 13 * getStormScale() + 3);
        if(!complete || !WitherStormMath.areaLoaded(worldObj, posX + motionX, posZ + motionZ, margin)) motionX = motionY = motionZ = 0;
        if(distance > 2 && target != null) rotationYaw += MathHelper.wrapAngleTo180_float((float)(Math.atan2(dx, -dz) * 180D / Math.PI) - rotationYaw) * 0.04F;
        renderYawOffset = rotationYaw; rotationYawHead = rotationYaw;
    }
    /** Every slot has one saved UUID. A known unloaded slot is never replaced. */
    private boolean maintainParts() {
        boolean complete = true;
        for(int i = 0; i < 10; i++) {
            boolean active = i < 3 || getPhase() != WitherStormPhase.COMMAND_BLOCK;
            if(!active) { if(parts[i] != null) parts[i].setDead(); parts[i] = null; continue; }
            if(parts[i] == null || parts[i].isDead || WitherStormEncounters.get(worldObj).find(partIds[i], worldObj) != parts[i]) {
                parts[i] = null;
                Entity found = WitherStormEncounters.get(worldObj).find(partIds[i], worldObj);
                if(found instanceof EntityWitherStormPart) parts[i] = (EntityWitherStormPart)found;
                else {
                    if(ticksExisted < 40 || (partIds[i] != null && !worldObj.getChunkProvider().chunkExists(partChunksX[i], partChunksZ[i]))) { complete = false; continue; }
                    EntityWitherStormPart part = new EntityWitherStormPart(worldObj);
                    part.attach(this, i, partIds[i]);
                    Vec3 position = partPosition(i); part.setPosition(position.xCoord, position.yCoord, position.zCoord);
                    if(!WitherStormMath.loaded(worldObj, part.posX, part.posZ) || !worldObj.spawnEntityInWorld(part)) { part.setDead(); complete = false; continue; }
                    partIds[i] = part.getUniqueID(); parts[i] = part;
                }
            }
            parts[i].parent = this;
            parts[i].followParent();
            partChunksX[i] = MathHelper.floor_double(parts[i].posX) >> 4; partChunksZ[i] = MathHelper.floor_double(parts[i].posZ) >> 4;
        }
        return complete;
    }
    public Vec3 partPosition(int slot) {
        float s = getStormScale();
        if(slot < 3) return local((slot - 1) * (getPhase() == WitherStormPhase.COMMAND_BLOCK ? 1.7D : 6D) * s,
            (slot == 1 ? 2D : 4D) * s, -(getPhase() == WitherStormPhase.COMMAND_BLOCK ? 0.8D : 5D) * s);
        double angle = (slot - 3) * Math.PI * 2D / 7D;
        double wave = Math.sin((worldObj.getTotalWorldTime() + slot * 19) * 0.04D);
        double length = slot >= 8 ? 11D : 8D;
        return local(Math.cos(angle) * (length + wave) * s, (slot >= 8 ? -3D : -5D) * s, Math.sin(angle) * length * s);
    }
    public Vec3 tentacleRoot(int slot) {
        double angle = (slot - 3) * Math.PI * 2D / 7D;
        return local(Math.cos(angle) * 2 * getStormScale(), 5 * getStormScale(), Math.sin(angle) * 2 * getStormScale());
    }
    public Vec3 mouth(int head) { Vec3 v = partPosition(head); return Vec3.createVectorHelper(v.xCoord, v.yCoord + getStormScale(), v.zCoord); }
    private Vec3 local(double x, double y, double z) {
        double a = rotationYaw * Math.PI / 180D, c = Math.cos(a), s = Math.sin(a);
        return Vec3.createVectorHelper(posX + x * c - z * s, posY + y, posZ + x * s + z * c);
    }
    private void fire(int head, EntityLivingBase target) {
        Vec3 p = mouth(head);
        Vec3 direction = Vec3.createVectorHelper(target.posX - p.xCoord, target.posY + target.height * 0.5D - p.yCoord, target.posZ - p.zCoord).normalize();
        EntityWitherStormSkull skull = new EntityWitherStormSkull(worldObj);
        skull.launch(this, p, direction);
        if(!WitherStormMath.areaLoaded(worldObj, skull.posX, skull.posZ, 34) || !worldObj.spawnEntityInWorld(skull)) skull.setDead();
        if(head == 0) playSound("hbm:entity.witherStorm.shoot", 1F, 1F);
    }
    private void interact() {
        for(int index = 0; index < nearby.size(); index++) {
            Entity e = nearby.get(index);
            if(!WitherStormMath.edible(e, worldObj) || getDistanceSqToEntity(e) > 80D * 80D) continue;
            Vec3 nearest = mouth(0); double best = e.getDistanceSq(nearest.xCoord, nearest.yCoord, nearest.zCoord);
            for(int h = 1; h < 3; h++) { Vec3 m = mouth(h); double d = e.getDistanceSq(m.xCoord, m.yCoord, m.zCoord); if(d < best) { best = d; nearest = m; } }
            int beam = -1;
            if(e instanceof EntityItem || e instanceof EntityFallingBlock) beam = 0;
            else for(int h = 0; h < 3; h++) {
                EntityLivingBase target = targets[h];
                if(target != null && (e == target || e.getDistanceSqToEntity(target) < 25D)) { beam = h; break; }
            }
            if(beam >= 0 && best < 48D * 48D) WitherStormMath.pull(e, nearest.xCoord, nearest.yCoord, nearest.zCoord, 0.075D + getPhase().ordinal() * 0.015D);
            boolean touching = best < Math.pow(1.6D * getStormScale(), 2) || boundingBox.intersectsWith(e.boundingBox);
            boolean devouring = false;
            if(getPhase().ordinal() >= 2) for(int t = 8; t < 10; t++) if(parts[t] != null && parts[t].getDistanceSqToEntity(e) < 16D * getStormScale() * getStormScale()) { touching = true; devouring = true; }
            if(touching && (e instanceof EntityItem || e instanceof EntityFallingBlock)) {
                int mass = e instanceof EntityItem ? Math.min(64, ((EntityItem)e).getEntityItem().stackSize) : 3;
                if(e instanceof EntityFallingBlock) ((EntityFallingBlock)e).field_145813_c = false;
                e.setDead(); addMass(mass); continue;
            }
            if(touching && e instanceof EntityLivingBase && ticksExisted % 10 == 0) {
                EntityLivingBase living = (EntityLivingBase)e;
                boolean hurt = living.attackEntityFrom(DamageSource.causeMobDamage(this), attackStrength() * (devouring ? 2F : 1F));
                if(hurt && !living.isEntityAlive()) { addMass(Math.max(10, Math.min(200, (int)living.getMaxHealth()))); heal(4F); }
            }
            if(ticksExisted % 20 == 0 && e instanceof EntityLivingBase && getPhase() != WitherStormPhase.COMMAND_BLOCK) {
                for(int t = 3; t < 10; t++) if(parts[t] != null && parts[t].getDistanceSqToEntity(e) < 9D * getStormScale() * getStormScale()) {
                    if(e.attackEntityFrom(DamageSource.causeMobDamage(this), attackStrength())) {
                        ((EntityLivingBase)e).addPotionEffect(new PotionEffect(Potion.wither.id, 100, 0));
                        if(t == 3 || t == 8) playSound("hbm:entity.witherStorm.tentacle", 0.8F, 1F);
                    }
                    break;
                }
            }
        }
    }
    @Override public boolean attackEntityFrom(DamageSource source, float damage) {
        if(worldObj.isRemote || source.getEntity() instanceof EntityWitherStorm || source.getEntity() instanceof EntityWitherStormPart || source.getSourceOfDamage() instanceof EntityWitherStormSkull || !Float.isFinite(damage)) return false;
        return super.attackEntityFrom(source, damage);
    }
    @Override protected String getLivingSound() { return "hbm:entity.witherStorm.ambient"; }
    @Override protected String getHurtSound() { return "hbm:entity.witherStorm.hurt"; }
    @Override protected String getDeathSound() { return "hbm:entity.witherStorm.death"; }
    @Override protected float getSoundVolume() { return 1.5F; }
    @Override public int getTalkInterval() { return 200; }
    @Override public void onDeath(DamageSource source) {
        if(!worldObj.isRemote) { dataWatcher.updateObject(22, (byte)0); WitherStormEncounters.get(worldObj).remove(getUniqueID()); }
        super.onDeath(source);
    }
    @Override protected void dropFewItems(boolean player, int looting) { entityDropItem(new ItemStack(Items.nether_star), 0); }
    @Override public void setDead() {
        boolean owner = !worldObj.isRemote && registered && WitherStormEncounters.get(worldObj).find(getUniqueID(), worldObj) == this;
        super.setDead();
        if(!worldObj.isRemote) {
            for(EntityWitherStormPart part : parts) if(part != null) part.setDead();
            WitherStormEncounters.get(worldObj).untrack(this);
            if(owner) WitherStormEncounters.get(worldObj).remove(getUniqueID());
        }
    }
    @Override public void writeEntityToNBT(NBTTagCompound n) {
        super.writeEntityToNBT(n);
        n.setInteger("Growth", getGrowth()); n.setString("Phase", getPhase().name()); n.setBoolean("Core", hasCore()); n.setBoolean("Registered", registered);
        n.setInteger("Transition", transitionTicks()); n.setInteger("RoarTimer", roarTimer);
        n.setInteger("GrowthSoundMass", lastGrowthSoundMass);
        WitherStormMath.uuid(n, "Encounter", getUniqueID());
        for(int i = 0; i < 10; i++) { WitherStormMath.uuid(n, "Part" + i, partIds[i]); n.setInteger("PartX" + i, partChunksX[i]); n.setInteger("PartZ" + i, partChunksZ[i]); }
        for(int i = 0; i < 3; i++) { WitherStormMath.uuid(n, "Target" + i, targetIds[i]); n.setInteger("Attack" + i, attacks[i]); }
        terrain.write(n);
    }
    @Override public void readEntityFromNBT(NBTTagCompound n) {
        super.readEntityFromNBT(n);
        float savedHealth = getHealth(); restoring = true; setGrowth(n.getInteger("Growth")); restoring = false; setHealth(savedHealth);
        registered = n.getBoolean("Registered"); dataWatcher.updateObject(22, (byte)(!n.hasKey("Core") || n.getBoolean("Core") ? 1 : 0));
        lastGrowthSoundMass = n.getInteger("GrowthSoundMass");
        dataWatcher.updateObject(23, Math.max(0, Math.min(80, n.getInteger("Transition")))); roarTimer = Math.max(40, Math.min(500, n.getInteger("RoarTimer")));
        for(int i = 0; i < 10; i++) { partIds[i] = WitherStormMath.uuid(n, "Part" + i); partChunksX[i] = n.getInteger("PartX" + i); partChunksZ[i] = n.getInteger("PartZ" + i); }
        for(int i = 0; i < 3; i++) { targetIds[i] = WitherStormMath.uuid(n, "Target" + i); attacks[i] = Math.max(1, Math.min(12000, n.getInteger("Attack" + i))); }
        visualScale = previousVisualScale = getStormScale(); terrain.read(n);
    }
}
