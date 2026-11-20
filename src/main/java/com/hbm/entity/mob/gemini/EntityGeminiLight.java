package com.hbm.entity.mob.gemini;

import com.hbm.config.GeminiConfig;
import com.hbm.handler.GeminiCaptures;
import com.hbm.handler.restraint.Restraints;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.GeminiLights;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/** A transient, tracked predator. The encounter owns admission and coordination. */
public final class EntityGeminiLight extends Entity {
    public static final int OBSERVE = 0, LURE = 1, HUNT = 2, HARVEST = 3, ASCEND = 4, HIDDEN = 5;
    private static final int MODE = 16, VARIANT = 17, CAPTIVE = 18;
    private GeminiLights.Encounter encounter;
    private Vec3 home, goal;
    private EntityLivingBase prey;
    private UUID lastFailedPrey;
    private int stateSince, decisionAt, failedUntil, blockedTicks;
    private boolean leaving, vanished;
    private float health = 8F;
    private double gripY;

    private int interpolation;
    private double clientX, clientY, clientZ;
    private float glow, prevGlow;

    public EntityGeminiLight(World world) {
        super(world);
        setSize(0.7F, 0.7F);
        isImmuneToFire = true;
    }

    public EntityGeminiLight(World world, GeminiLights.Encounter encounter, Vec3 home) {
        this(world);
        this.encounter = encounter;
        this.home = home;
        setPosition(home.xCoord, home.yCoord, home.zCoord);
        dataWatcher.updateObject(VARIANT, (byte)(rand.nextInt(8) | (encounter.kind == GeminiLights.Kind.HARVEST ? 8 : 0)));
        decisionAt = 95 + rand.nextInt(130);
    }

    @Override protected void entityInit() {
        dataWatcher.addObject(MODE, (byte)OBSERVE);
        dataWatcher.addObject(VARIANT, (byte)0);
        dataWatcher.addObject(CAPTIVE, -1);
    }

    public int mode() { return dataWatcher.getWatchableObjectByte(MODE); }
    public int variant() { return dataWatcher.getWatchableObjectByte(VARIANT); }
    public int captiveId() { return dataWatcher.getWatchableObjectInt(CAPTIVE); }
    public float glow(float partial) { return prevGlow + (glow - prevGlow) * partial; }

    private boolean isHarvest() { return encounter.kind == GeminiLights.Kind.HARVEST; }

    private void begin(int mode, int duration) {
        dataWatcher.updateObject(MODE, (byte)mode);
        stateSince = ticksExisted;
        decisionAt = ticksExisted + duration;
        blockedTicks = 0;
        goal = null;
    }

    @Override public void onUpdate() {
        super.onUpdate();
        if(worldObj.isRemote) {
            if(interpolation > 0) {
                setPosition(posX + (clientX - posX) / interpolation, posY + (clientY - posY) / interpolation,
                        posZ + (clientZ - posZ) / interpolation);
                interpolation--;
            }
            prevGlow = glow;
            int mode = mode();
            float level = mode == HIDDEN ? 0F : mode == HARVEST ? 1F : mode == HUNT ? 0.84F : mode == LURE ? 0.64F : 0.46F;
            glow += (level - glow) * 0.16F;
            return;
        }
        if(encounter == null || isDead) { setDead(); return; }
        int mode = mode();
        boolean scan = (ticksExisted + getEntityId()) % 20 == 0;
        switch(mode) {
            case OBSERVE: observe(scan); break;
            case LURE: lure(scan); break;
            case HUNT: hunt(); break;
            case HARVEST: harvest(); break;
            case ASCEND: ascend(); break;
            case HIDDEN:
                if(ticksExisted >= decisionAt) begin(OBSERVE, 24 + rand.nextInt(30));
                break;
            default: setDead();
        }
    }

    private void observe(boolean scan) {
        if(scan && ticksExisted >= 80 && !vanished && rand.nextInt(6) == 0) {
            EntityPlayer observer = observer();
            if(observer != null && watching(observer)) {
                vanished = true;
                begin(HIDDEN, 30 + rand.nextInt(70));
                return;
            }
        }
        if(ticksExisted >= decisionAt) {
            EntityPlayer observer = observer();
            if(isHarvest() || rand.nextInt(3) == 0) {
                begin(LURE, 265 + rand.nextInt(126));
                lureWaypoint(observer);
                return;
            }
            decisionAt = ticksExisted + 40 + rand.nextInt(80);
            if(rand.nextInt(3) == 0) goal = null;
            else if(observer != null) {
                double dx = posX - observer.posX, dz = posZ - observer.posZ;
                double scale = (35D + rand.nextInt(15)) / Math.max(1D, Math.sqrt(dx * dx + dz * dz));
                waypoint(observer.posX + dx * scale + offset(8), observer.posZ + dz * scale + offset(8),
                        34 + rand.nextInt(28));
            } else {
                double blend = rand.nextBoolean() ? 0.55D : 0.85D;
                waypoint(encounter.x + (home.xCoord - encounter.x) * blend + offset(10),
                        encounter.z + (home.zCoord - encounter.z) * blend + offset(10), 35 + rand.nextInt(23));
            }
        }
        if(!fly(0.30D)) goal = null;
    }

    private void lureWaypoint(EntityPlayer observer) {
        if(isHarvest()) {
            double spread = 1.15D + rand.nextDouble() * 0.27D;
            waypoint(encounter.x + (home.xCoord - encounter.x) * spread,
                    encounter.z + (home.zCoord - encounter.z) * spread, 7 + rand.nextInt(9));
        } else if(observer != null) {
            waypoint(observer.posX + offset(17), observer.posZ + offset(17), 7 + rand.nextInt(8));
        } else {
            waypoint((home.xCoord + encounter.x) * 0.5D + offset(9),
                    (home.zCoord + encounter.z) * 0.5D + offset(9), 9 + rand.nextInt(8));
        }
    }

    private void lure(boolean scan) {
        if(ticksExisted >= decisionAt) { retreat(false); return; }
        if(scan && ticksExisted - stateSince >= 35) {
            EntityLivingBase target = findPrey();
            if(target != null) {
                if(!isHarvest() && rand.nextInt(7) == 0) { retreat(false); return; }
                if(encounter.claim(target, this)) {
                    prey = target;
                    begin(HUNT, 260 + (int)(16F / GeminiConfig.lightsMovementSpeed));
                    return;
                }
            }
        }
        if(fly(0.72D)) blockedTicks = 0;
        else if(++blockedTicks >= 12) retreat(false);
    }

    private void hunt() {
        int hunt = huntingRange();
        if(ticksExisted >= decisionAt || prey == null || prey.worldObj != worldObj || !prey.isEntityAlive()
                || horizontalSq(prey.posX, prey.posZ) > (hunt + 12D) * (hunt + 12D)
                || ticksExisted % 5 == 0 && !eligiblePrey(prey)) {
            retreat(false);
            return;
        }
        double y = prey.boundingBox.maxY + 0.85D;
        double dx = prey.posX - posX, dy = y - posY, dz = prey.posZ - posZ;
        // Get over the prey before dropping through the canopy line.
        double altitude = dx * dx + dz * dz > 3D * 3D ? Math.max(y + 5D, posY - 0.16D) : y;
        goal = Vec3.createVectorHelper(prey.posX, altitude, prey.posZ);
        if(dx * dx + dy * dy + dz * dz < 1.7D * 1.7D && eligiblePrey(prey)
                && clearLine(posX, posY + 0.35D, posZ, prey.posX, prey.boundingBox.maxY - 0.12D, prey.posZ)
                && liftClear(prey, prey.posY + 2.1D)
                && GeminiCaptures.attach(this, prey, false, reason -> retreat(true))) {
            encounter.captured(prey);
            gripY = prey.posY;
            begin(HARVEST, 76);
            dataWatcher.updateObject(CAPTIVE, prey.getEntityId());
            worldObj.playSoundAtEntity(this, "hbm:gemini.iris_rumble", 0.8F, isHarvest() ? 0.56F : 0.80F);
            return;
        }
        if(fly(1.45D)) blockedTicks = 0;
        else if(++blockedTicks >= 12) retreat(false);
    }

    private void harvest() {
        EntityLivingBase victim = prey;
        if(victim == null || !GeminiCaptures.valid(this, victim)
                || victim instanceof EntityPlayer && ((EntityPlayer)victim).capabilities.disableDamage) {
            retreat(true);
            return;
        }
        int age = ticksExisted - stateSince;
        double lift = age < 25 ? GeminiCaptures.liftProgress(age, 25) : 1D - GeminiCaptures.liftProgress(age - 48, 28);
        double y = gripY + 2.1D * lift;
        if(!liftClear(victim, y)) { retreat(true); return; }
        GeminiCaptures.position(victim, victim.posX, y, victim.posZ);
        // Rise with the captive so the tether stays above its head.
        double hoverY = victim.boundingBox.maxY + 1.1D;
        if(!clear(boundingBox.addCoord(0D, hoverY - posY, 0D))) { retreat(true); return; }
        setPosition(posX, hoverY, posZ);
        if(age == 42 && GeminiConfig.lightsHarvestDamage > 0F)
            victim.attackEntityFrom(new EntityDamageSource("geminiHarvest", this), GeminiConfig.lightsHarvestDamage);
        if(ticksExisted >= decisionAt) retreat(true);
    }

    private boolean liftClear(EntityLivingBase victim, double y) {
        return clear(victim.boundingBox.addCoord(0D, y - victim.posY, 0D));
    }

    private void retreat(boolean leave) {
        if(mode() == HUNT && prey != null) {
            lastFailedPrey = prey.getUniqueID();
            failedUntil = ticksExisted + 220;
        }
        release();
        leaving = leave;
        begin(ASCEND, leave ? 100 : 170);
        waypoint(posX, posZ, leave ? 66D : 47D);
    }

    private void ascend() {
        if(goal == null || getDistanceSq(goal.xCoord, goal.yCoord, goal.zCoord) < 0.5D || ticksExisted >= decisionAt) {
            if(leaving) setDead();
            else begin(OBSERVE, 55 + rand.nextInt(65));
        } else if(fly(1.25D)) {
            blockedTicks = 0;
        } else if(++blockedTicks >= 6) {
            blockedTicks = 0;
            waypoint(posX + offset(6), posZ + offset(6), 48D);
        }
    }

    public void depart() {
        if(!isDead && !leaving) retreat(true);
    }

    private void release() {
        if(prey == null) return;
        GeminiCaptures.release(this, prey);
        encounter.relinquish(prey, this);
        prey = null;
        dataWatcher.updateObject(CAPTIVE, -1);
    }

    @Override public void setDead() {
        if(!worldObj.isRemote) release();
        super.setDead();
    }

    @Override public boolean canBeCollidedWith() { return !isDead && mode() != HIDDEN; }

    @Override public boolean attackEntityFrom(DamageSource source, float amount) {
        if(worldObj.isRemote || !canBeCollidedWith() || isEntityInvulnerable() || source.getEntity() == null || !(amount > 0F)) return false;
        health -= amount;
        if(health <= 0F) setDead();
        else if(mode() == HARVEST) retreat(true);
        else if(mode() == HUNT || mode() == LURE) retreat(false);
        return true;
    }

    private EntityPlayer observer() {
        EntityPlayer nearest = null;
        double best = GeminiConfig.lightsDetectionRange * (double)GeminiConfig.lightsDetectionRange;
        for(Object entry : worldObj.playerEntities) {
            EntityPlayer player = (EntityPlayer)entry;
            double distance = horizontalSq(player.posX, player.posZ);
            if(distance < best && Math.abs(player.posY - posY) < 100D && player.isEntityAlive()
                    && !(player instanceof FakePlayer) && GeminiLights.exposed(player)) {
                nearest = player;
                best = distance;
            }
        }
        return nearest;
    }

    private boolean watching(EntityPlayer player) {
        double y = player.posY + player.getEyeHeight();
        Vec3 direction = Vec3.createVectorHelper(posX - player.posX, posY + 0.35D - y, posZ - player.posZ).normalize();
        return player.getLookVec().dotProduct(direction) >= 0.997D
                && clearLine(player.posX, y, player.posZ, posX, posY + 0.35D, posZ);
    }

    private EntityLivingBase findPrey() {
        int range = huntingRange();
        double best = range * (double)range;
        EntityLivingBase nearest = null;
        for(Object entry : worldObj.playerEntities) {
            EntityPlayer player = (EntityPlayer)entry;
            double distance = horizontalSq(player.posX, player.posZ);
            if(distance <= best && eligiblePrey(player)) { nearest = player; best = distance; }
        }
        if(nearest != null) return nearest;

        // Passive prey uses the nearby chunk entity index and a smaller search than players.
        int local = Math.min(26, range);
        best = local * (double)local;
        AxisAlignedBB area = AxisAlignedBB.getBoundingBox(posX - local, Math.max(0D, posY - 62D), posZ - local,
                posX + local, posY + 12D, posZ + local);
        int examined = 0;
        for(Object entry : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, area)) {
            EntityLivingBase target = (EntityLivingBase)entry;
            if(!(target instanceof EntityAnimal) && !(target instanceof EntityVillager)) continue;
            double distance = horizontalSq(target.posX, target.posZ);
            if(distance <= best && eligiblePrey(target)) { nearest = target; best = distance; }
            if(++examined >= 48) break;
        }
        return nearest;
    }

    private boolean eligiblePrey(EntityLivingBase target) {
        if(target.worldObj != worldObj || !target.isEntityAlive() || !target.addedToChunk || target.ridingEntity != null
                || target.riddenByEntity != null || target instanceof IGeminiCreature || target instanceof IBossDisplayData
                || target.width > 2F || target.height > 3F || Math.abs(target.posY - posY) > 75D
                || target.boundingBox.maxY + 4D >= worldObj.getActualHeight()) return false;
        if(target instanceof EntityPlayer && (((EntityPlayer)target).capabilities.disableDamage
                || ((EntityPlayer)target).isPlayerSleeping() || target instanceof FakePlayer)) return false;
        double dx = target.posX - encounter.x, dz = target.posZ - encounter.z;
        return dx * dx + dz * dz <= 112D * 112D && (ticksExisted >= failedUntil || !target.getUniqueID().equals(lastFailedPrey))
                && encounter.available(target, this) && GeminiCaptures.available(target) && !Restraints.blocked(target)
                && !target.getEntityData().hasKey("MockeryCaptor") && GeminiLights.exposed(target);
    }

    private static int huntingRange() { return Math.min(GeminiConfig.lightsHuntingRange, GeminiConfig.lightsDetectionRange); }

    private double horizontalSq(double x, double z) {
        double dx = x - posX, dz = z - posZ;
        return dx * dx + dz * dz;
    }

    private double offset(double spread) { return (rand.nextDouble() * 2D - 1D) * spread; }

    private void waypoint(double x, double z, double height) {
        int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z);
        double dx = x - encounter.x, dz = z - encounter.z;
        if(dx * dx + dz * dz > 116D * 116D || !worldObj.getChunkProvider().chunkExists(bx >> 4, bz >> 4)) return;
        double y = Math.min(worldObj.getActualHeight() - 2D, worldObj.getHeightValue(bx, bz) + height);
        goal = Vec3.createVectorHelper(x, y, z);
    }

    private boolean fly(double speed) {
        if(goal == null) return true;
        double dx = goal.xCoord - posX, dy = goal.yCoord - posY, dz = goal.zCoord - posZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if(distance < 0.04D) return true;
        double step = Math.min(1D, GeminiConfig.lightsMovementSpeed * speed / distance);
        if(moveIfClear(dx * step, dy * step, dz * step)) return true;
        // Horizontal diversions go up and around a ridge or tree instead of sliding into it.
        return dx * dx + dz * dz > 0.25D && moveIfClear(0D, GeminiConfig.lightsMovementSpeed, 0D);
    }

    private boolean moveIfClear(double dx, double dy, double dz) {
        int x = MathHelper.floor_double(posX + dx), z = MathHelper.floor_double(posZ + dz);
        boolean changesChunk = (x >> 4) != chunkCoordX || (z >> 4) != chunkCoordZ;
        if(Math.abs(x) >= 29999984 || Math.abs(z) >= 29999984
                || changesChunk && !worldObj.doChunksNearChunkExist(x, MathHelper.floor_double(posY + dy), z, 32)
                || !clear(boundingBox.addCoord(dx, dy, dz))) return false;
        setPosition(posX + dx, posY + dy, posZ + dz);
        return true;
    }

    private boolean clear(AxisAlignedBB box) {
        return box.minY >= 0D && box.maxY < worldObj.getActualHeight()
                && GardenerNests.loaded(worldObj, box.expand(1D, 0D, 1D))
                && worldObj.func_147461_a(box).isEmpty() && !worldObj.isAnyLiquid(box);
    }

    private boolean clearLine(double x1, double y1, double z1, double x2, double y2, double z2) {
        return GardenerNests.loaded(worldObj, AxisAlignedBB.getBoundingBox(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)))
                && worldObj.func_147447_a(Vec3.createVectorHelper(x1, y1, z1), Vec3.createVectorHelper(x2, y2, z2), false, true, false) == null;
    }

    @Override public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int increments) {
        clientX = x; clientY = y; clientZ = z;
        interpolation = 3;
    }

    @Override public int getBrightnessForRender(float partial) { return 0xF000F0; }
    @Override public boolean isInRangeToRenderDist(double distance) { return distance < 192D * 192D; }
    @Override public boolean writeToNBTOptional(NBTTagCompound tag) { return false; }
    @Override protected void writeEntityToNBT(NBTTagCompound tag) { }
    @Override protected void readEntityFromNBT(NBTTagCompound tag) { setDead(); }
}
