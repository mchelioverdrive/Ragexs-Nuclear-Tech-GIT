package com.hbm.entity.mob.gemini;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.hbm.blocks.ModBlocks;
import com.hbm.config.GeminiConfig;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.GeminiInvasionManager;
import com.hbm.world.gemini.GeminiPopulation;
import com.hbm.world.gemini.GeminiSpawning;
import com.hbm.world.gemini.WoodcrawlerSpawning;
import com.hbm.world.gemini.WretchCamps;
import cpw.mods.fml.common.eventhandler.Cancelable;
import cpw.mods.fml.common.eventhandler.Event;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

/** Gardener biomass, independent of the villager/Fake Person processing nest. No terrain mutations. */
public final class EntityGardenerCocoon extends Entity implements IGeminiCreature {
    private int remaining, duration, retry, collapse;
    private boolean sealed, completed, manual, resolving;

    public EntityGardenerCocoon(World world) {
        super(world); setSize(1.5F, 3F); preventEntitySpawning = true;
    }
    @Override protected void entityInit() {
        dataWatcher.addObject(20, (byte) 0); // Hanging / surface.
        dataWatcher.addObject(21, (byte) 0); // Coarse visual maturity; never an interface progress bar.
        dataWatcher.addObject(22, (byte) 0); // Empty, collapsing shell.
    }
    public boolean isHanging() { return dataWatcher.getWatchableObjectByte(20) == 0; }
    public float maturity() { return dataWatcher.getWatchableObjectByte(21) / 100F; }
    public boolean isEmpty() { return dataWatcher.getWatchableObjectByte(22) != 0; }
    public float collapseAmount() { return Math.max(0, dataWatcher.getWatchableObjectByte(22) - 1) / 30F; }
    private void form(boolean hanging) {
        dataWatcher.updateObject(20, (byte) (hanging ? 0 : 1));
        setSize(hanging ? 1.5F : 2F, hanging ? 3F : 1F);
        setPosition(posX, posY, posZ);
    }
    @Override public boolean canBeCollidedWith() { return !isDead; }
    @Override public boolean canBePushed() { return false; }
    @Override public void moveEntity(double x, double y, double z) { }

    public static boolean suitable(Entity prey) {
        return prey instanceof EntityLiving && !(prey instanceof IGeminiCreature) && !(prey instanceof IBossDisplayData)
                && !prey.noClip && prey.width <= 1.1F && prey.height <= 2.5F
                && prey.ridingEntity == null && prey.riddenByEntity == null;
    }
    private static boolean enabled(EntityGardener parent) {
        return !parent.isOcean() && GeminiSpawning.canAct(parent, 3)
                && parent.worldObj.getGameRules().getGameRuleBooleanValue("doMobSpawning")
                && GeminiPopulation.canAdmitCocoon(parent.worldObj);
    }
    private static boolean naturalSupport(World world, int x, int y, int z, boolean hanging) {
        Block block = world.getBlock(x, y, z);
        boolean natural = block == Blocks.stone || block == Blocks.dirt || block == Blocks.grass || block == Blocks.gravel
                || block == Blocks.sandstone || block == ModBlocks.gardener_flesh;
        return natural && world.getTileEntity(x, y, z) == null
                && !block.hasTileEntity(world.getBlockMetadata(x, y, z))
                && block.isSideSolid(world, x, y, z, hanging ? ForgeDirection.DOWN : ForgeDirection.UP);
    }
    /** Fast loaded-only geometry check, also used during transport and after attachment. */
    public boolean attachmentValid() {
        int x = MathHelper.floor_double(posX), z = MathHelper.floor_double(posZ);
        int y = MathHelper.floor_double(isHanging() ? boundingBox.maxY + 0.01D : posY - 0.01D);
        return posY > 1 && boundingBox.maxY < worldObj.getHeight() - 1
                && GardenerNests.loaded(worldObj, boundingBox.expand(1, 1, 1))
                && naturalSupport(worldObj, x, y, z, isHanging())
                && (!isHanging() || !worldObj.canBlockSeeTheSky(x, y - 1, z))
                && worldObj.getCollidingBoundingBoxes(this, boundingBox).isEmpty()
                && !worldObj.isAnyLiquid(boundingBox);
    }
    private boolean site(EntityGardener parent, Entity victim) {
        if(!attachmentValid() || WretchCamps.hasPlayerPlacement(worldObj, boundingBox.expand(1, 0, 1))
                || GardenerNests.overlapsStructure(worldObj, boundingBox.expand(1, 1, 1))) return false;
        // Air only, including the roots' footprint. Never overwrite tiles, plants, doors or furnishings.
        for(int x = MathHelper.floor_double(boundingBox.minX); x <= MathHelper.floor_double(boundingBox.maxX); x++)
            for(int z = MathHelper.floor_double(boundingBox.minZ); z <= MathHelper.floor_double(boundingBox.maxZ); z++)
                for(int y = MathHelper.floor_double(posY); y < MathHelper.ceiling_double_int(boundingBox.maxY); y++)
                    if(!worldObj.isAirBlock(x, y, z) || worldObj.getTileEntity(x, y, z) != null
                            || !worldObj.canMineBlock(FakePlayerFactory.getMinecraft((WorldServer) worldObj), x, y, z)) return false;
        for(Object entry : worldObj.getEntitiesWithinAABBExcludingEntity(this, boundingBox)) {
            Entity other = (Entity) entry;
            if(other == victim || other.isDead || other instanceof EntityGardenerTentacle && ((EntityGardenerTentacle) other).owner() == parent) continue;
            if(other.canBeCollidedWith() || other.preventEntitySpawning || other instanceof EntityGardenerCocoon) return false;
        }
        return true;
    }
    public Vec3 victimGoal(Entity victim) {
        return Vec3.createVectorHelper(posX, posY + 0.25D + victim.height * 0.5D, posZ);
    }
    /** One bounded search per capture, never per tick. Conservative swept-box clearance needs no pathfinder. */
    public static EntityGardenerCocoon findCeiling(EntityGardener parent, Entity victim) {
        if(!suitable(victim) || !enabled(parent)) return null;
        World world = parent.worldObj;
        int cx = MathHelper.floor_double(victim.posX), cz = MathHelper.floor_double(victim.posZ);
        EntityGardenerCocoon candidate = new EntityGardenerCocoon(world);
        for(int i = 0; i < 25; i++) {
            int x = cx + i % 5 - 2, z = cz + i / 5 - 2;
            if((x >> 4) != (cx >> 4) || (z >> 4) != (cz >> 4)) continue;
            for(int y = MathHelper.floor_double(victim.boundingBox.maxY) + 2; y <= MathHelper.floor_double(parent.posY) + 10; y++) {
                candidate.setPosition(x + 0.5D, y - 3D, z + 0.5D);
                Vec3 goal = candidate.victimGoal(victim);
                if(parent.getDistanceSq(goal.xCoord, goal.yCoord, goal.zCoord) > 16D * 16D || !candidate.attachmentValid()) continue;
                AxisAlignedBB path = victim.boundingBox.addCoord(goal.xCoord - victim.posX,
                        goal.yCoord - victim.boundingBox.minY - victim.height * 0.5D, goal.zCoord - victim.posZ);
                if(!GardenerNests.loaded(world, path.expand(1, 0, 1)) || !world.getCollidingBoundingBoxes(victim, path).isEmpty()
                        || world.isAnyLiquid(path) || !candidate.site(parent, victim)) continue;
                return candidate;
            }
        }
        return null;
    }
    /** Only a confirmed lethal feeding bite supplies surface biomass; no saved victim can be restored. */
    public static void afterFeeding(EntityGardener parent, Entity victim) {
        if(!suitable(victim) || victim.isEntityAlive() || !enabled(parent) || victim.getEntityData().getBoolean("GardenerConsumed")) return;
        victim.getEntityData().setBoolean("GardenerConsumed", true);
        EntityGardenerCocoon cocoon = new EntityGardenerCocoon(parent.worldObj);
        cocoon.form(false);
        for(int i = 0; i < 75; i++) {
            int x = MathHelper.floor_double(victim.posX) + i % 5 - 2;
            int z = MathHelper.floor_double(victim.posZ) + i / 5 % 5 - 2;
            int y = MathHelper.floor_double(victim.posY) - i / 25;
            cocoon.setPosition(x + 0.5D, y, z + 0.5D);
            if(cocoon.site(parent, victim)) { cocoon.place(parent, victim, false); return; }
        }
    }
    public boolean attach(EntityGardener parent, Entity victim) {
        if(!suitable(victim) || !victim.isEntityAlive() || !parent.isHolding(victim)
                || victim.chunkCoordX != (MathHelper.floor_double(posX) >> 4)
                || victim.chunkCoordZ != (MathHelper.floor_double(posZ) >> 4)
                || victimGoal(victim).squareDistanceTo(Vec3.createVectorHelper(victim.posX,
                        victim.boundingBox.minY + victim.height * 0.5D, victim.posZ)) > 0.16D) return false;
        return place(parent, victim, true);
    }
    private boolean place(EntityGardener parent, Entity victim, boolean consume) {
        if(worldObj.isRemote || !enabled(parent) || !site(parent, victim)) return false;
        // Honor the existing cancelable mob-attack protection hook before a non-damaging enclosure.
        if(consume && MinecraftForge.EVENT_BUS.post(new LivingAttackEvent((EntityLiving) victim, DamageSource.causeMobDamage(parent), 0F))) return false;
        if(MinecraftForge.EVENT_BUS.post(new PlacementEvent(parent, victim, this))) return false;
        if(isDead || !enabled(parent) || !site(parent, victim) || consume && (!victim.isEntityAlive() || !parent.isHolding(victim))) return false;
        duration = remaining = GeminiConfig.gardenerCocoonSeconds * 20;
        manual = GeminiSpawning.isManual(parent);
        if(!worldObj.spawnEntityInWorld(this)) { super.setDead(); return false; }
        // Source and structure occupy the same chunk at handoff. Failed admission never consumes prey.
        if(isDead || consume && (!victim.isEntityAlive() || !parent.isHolding(victim))) { super.setDead(); return false; }
        if(consume) {
            victim.setDead();
            if(!victim.isDead) { super.setDead(); return false; }
        }
        sealed = true;
        return true;
    }
    @Cancelable public static final class PlacementEvent extends Event {
        public final EntityGardener gardener;
        public final Entity victim;
        public final EntityGardenerCocoon cocoon;
        PlacementEvent(EntityGardener gardener, Entity victim, EntityGardenerCocoon cocoon) {
            this.gardener = gardener; this.victim = victim; this.cocoon = cocoon;
        }
    }
    private boolean active() {
        return GeminiInvasionManager.hasMarsVisited(worldObj) && worldObj.difficultySetting != EnumDifficulty.PEACEFUL
                && (manual || GeminiConfig.gardenerEnabled && GeminiInvasionManager.canActivityOccur(worldObj, 3));
    }
    @Override public void onUpdate() {
        super.onUpdate(); motionX = motionY = motionZ = 0;
        if(worldObj.isRemote) {
            if((height > 2F) != isHanging()) {
                setSize(isHanging() ? 1.5F : 2F, isHanging() ? 3F : 1F);
                setPosition(posX, posY, posZ);
            }
            return;
        }
        if(!sealed) { super.setDead(); return; }
        if(completed) {
            if(++collapse >= 30) super.setDead();
            else if(collapse % 5 == 0) dataWatcher.updateObject(22, (byte) (collapse + 1));
            return;
        }
        if(!GardenerNests.loaded(worldObj, boundingBox.expand(1, 1, 1))) return;
        if(ticksExisted % 20 == 1 && !attachmentValid()) { setDead(); return; }
        if(!active()) return;
        if(remaining > 0) remaining--;
        if(ticksExisted % 40 == 0 || remaining == 0)
            dataWatcher.updateObject(21, (byte) Math.min(100, (duration - remaining) * 100L / Math.max(1, duration)));
        if(remaining > 0 || resolving) return;
        if(retry > 0) { retry--; return; }
        retry = 100;
        if(!attachmentValid()) { setDead(); return; }
        hatch();
    }
    private void hatch() {
        resolving = true;
        try {
            // Stable across retries/reloads, but distinct from the briefly coexisting empty shell.
            UUID outputId = UUID.nameUUIDFromBytes(("gardener-woodcrawler:" + getUniqueID()).getBytes(StandardCharsets.UTF_8));
            if(GeminiPopulation.find(worldObj, outputId) != null) { finish(); return; }
            if(!worldObj.getGameRules().getGameRuleBooleanValue("doMobSpawning") || !WoodcrawlerSpawning.canConvert(worldObj)) return;
            EntityWoodcrawler output = new EntityWoodcrawler(worldObj);
            NBTTagCompound identity = new NBTTagCompound();
            identity.setLong("UUIDMost", outputId.getMostSignificantBits());
            identity.setLong("UUIDLeast", outputId.getLeastSignificantBits());
            output.inheritConversionIdentity(identity);
            output.setCrouching(true);
            output.crouchBlend = output.prevCrouchBlend = 1;
            // At most 350 loaded probes, only once per five seconds; never split the chunk-save handoff.
            for(int level = 0; level < 14; level++) for(int i = 0; i < 25; i++) {
                double x = MathHelper.floor_double(posX) + i % 5 - 2 + 0.5D;
                double z = MathHelper.floor_double(posZ) + i / 5 - 2 + 0.5D;
                double y = MathHelper.floor_double(posY) + 1 - level;
                if((MathHelper.floor_double(x) >> 4) != (MathHelper.floor_double(posX) >> 4)
                        || (MathHelper.floor_double(z) >> 4) != (MathHelper.floor_double(posZ) >> 4) || y < 1) continue;
                output.setLocationAndAngles(x, y, z, rotationYaw, 0);
                if(!GardenerNests.loaded(worldObj, output.boundingBox.expand(1, 1, 1))
                        || !World.doesBlockHaveSolidTopSurface(worldObj, MathHelper.floor_double(x), (int) y - 1, MathHelper.floor_double(z))
                        || !worldObj.getCollidingBoundingBoxes(output, output.boundingBox).isEmpty() || worldObj.isAnyLiquid(output.boundingBox)) continue;
                // A ceiling cocoon must not hatch through an intervening floor into another room.
                if(worldObj.func_147447_a(Vec3.createVectorHelper(posX, posY + 0.5D, posZ),
                        Vec3.createVectorHelper(x, y + 0.5D, z), false, true, false) != null) continue;
                boolean occupied = false;
                for(Object entry : worldObj.getEntitiesWithinAABBExcludingEntity(this, output.boundingBox)) {
                    Entity other = (Entity) entry;
                    if(!other.isDead && (other.preventEntitySpawning || other.canBeCollidedWith())) { occupied = true; break; }
                }
                if(occupied) continue;
                if(worldObj.spawnEntityInWorld(output)) finish();
                return; // Protection vetoes wait for the next retry, rather than trying every coordinate.
            }
        } finally { resolving = false; }
    }
    private void finish() { completed = true; dataWatcher.updateObject(22, (byte) 1); }
    @Override public boolean attackEntityFrom(DamageSource source, float amount) {
        if(worldObj.isRemote || !sealed || isDead || resolving || amount <= 0 || Float.isNaN(amount)
                || source.getEntity() instanceof IGeminiCreature) return false;
        setDead(); return true;
    }
    @Override public void setDead() {
        // Destroying biomass destroys its contents. There is never a second saved living victim to release.
        if(resolving) return;
        completed = true; super.setDead();
    }
    @Override protected void writeEntityToNBT(NBTTagCompound nbt) {
        nbt.setBoolean("Hanging", isHanging()); nbt.setBoolean("Sealed", sealed);
        nbt.setBoolean("Completed", completed); nbt.setBoolean("Manual", manual);
        nbt.setInteger("Remaining", remaining); nbt.setInteger("Duration", duration); nbt.setInteger("Collapse", collapse);
    }
    @Override protected void readEntityFromNBT(NBTTagCompound nbt) {
        form(nbt.getBoolean("Hanging")); sealed = nbt.getBoolean("Sealed");
        completed = nbt.getBoolean("Completed"); manual = nbt.getBoolean("Manual");
        duration = MathHelper.clamp_int(nbt.getInteger("Duration"), 20, 72000);
        remaining = MathHelper.clamp_int(nbt.getInteger("Remaining"), 0, duration);
        collapse = MathHelper.clamp_int(nbt.getInteger("Collapse"), 0, 30); retry = 20;
        dataWatcher.updateObject(21, (byte) ((duration - remaining) * 100L / duration));
        dataWatcher.updateObject(22, (byte) (completed ? 1 : 0));
    }
}
