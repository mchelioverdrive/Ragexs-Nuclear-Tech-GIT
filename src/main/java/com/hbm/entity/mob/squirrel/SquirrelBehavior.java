package com.hbm.entity.mob.squirrel;

import java.util.Random;

import com.hbm.items.ModItems;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

final class SquirrelBehavior {
    private static final ForgeDirection[] SIDES = {ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.WEST, ForgeDirection.EAST};
    private static final int LEAP_TICKS = 12;

    private final EntitySquirrel squirrel;
    private final Random random;
    private TreeRoute tree, cachedTree;
    private int cacheExpires, nextTreeAttempt, nextDecision, nextThreatCheck, threatenedUntil, nextMeal;
    private double threatX, threatY, threatZ;
    private double watchYaw;
    private int watchUntil;
    private int approachUntil, gripUntil;
    private boolean climbing;
    private Vec3 leapFrom, leapTo;
    private int leapTick;

    SquirrelBehavior(EntitySquirrel squirrel) {
        this.squirrel = squirrel;
        this.random = squirrel.getRNG();
    }

    void stop() {
        squirrel.getNavigator().clearPathEntity();
        tree = null;
        climbing = false;
        leapTo = null;
        watchUntil = 0;
        squirrel.action(EntitySquirrel.IDLE);
    }

    void tick() {
        int now = squirrel.ticksExisted;
        if(squirrel.isInWater() || squirrel.handleLavaMovement()) {
            stop();
            return;
        }
        if(now >= nextThreatCheck) {
            nextThreatCheck = now + 8 + random.nextInt(5);
            senseThreat(now);
        }
        if(climbing || leapTo != null) return;
        if(now < watchUntil) {
            squirrel.getLookHelper().setLookPosition(squirrel.posX + Math.sin(watchYaw) * 3D,
                    squirrel.posY + squirrel.getEyeHeight(), squirrel.posZ + Math.cos(watchYaw) * 3D, 12F, 15F);
        }
        if(tree != null) {
            if(atTree(tree)) {
                climbing = true;
                gripUntil = 0;
                squirrel.getNavigator().clearPathEntity();
                squirrel.action(EntitySquirrel.CLIMBING);
                return;
            }
            if(now < approachUntil && !squirrel.getNavigator().noPath()) return;
            tree = null;
        }
        if(now < nextDecision) return;
        if(squirrel.getAction() == EntitySquirrel.EATING) squirrel.action(EntitySquirrel.IDLE);

        if(now < threatenedUntil) {
            nextDecision = now + 18;
            if(squirrel.onGround && now >= nextTreeAttempt && travelInTrees(now)) return;
            if(squirrel.getNavigator().noPath() || squirrel.getDistanceSq(threatX, threatY, threatZ) < 9D) {
                if(canSearch(6)) moveTo(RandomPositionGenerator.findRandomTargetBlockAwayFrom(squirrel, 6, 3,
                        Vec3.createVectorHelper(threatX, threatY, threatZ)), 1.6D, false);
            }
            return;
        }
        if(!squirrel.getNavigator().noPath() || !squirrel.onGround) return;
        if(now >= nextMeal && forage(now)) return;
        if(now >= nextTreeAttempt && random.nextInt(8) == 0 && travelInTrees(now)) return;
        if(random.nextBoolean()) {
            nextDecision = now + 24 + random.nextInt(41);
            watchUntil = nextDecision;
            watchYaw = Math.toRadians(squirrel.rotationYaw) + (random.nextDouble() - 0.5D) * Math.PI;
        } else {
            if(canSearch(6)) moveTo(RandomPositionGenerator.findRandomTarget(squirrel, 6, 3), 0.85D, false);
            nextDecision = now + 20 + random.nextInt(21);
        }
    }

    private void senseThreat(int now) {
        World world = squirrel.worldObj;
        EntityLivingBase threat = squirrel.getAITarget();
        if(threat == null || !threat.isEntityAlive() || squirrel.getDistanceSqToEntity(threat) > 100D) {
            threat = null;
            EntityPlayer player = world.getClosestPlayerToEntity(squirrel, 7D);
            if(player != null && player.isEntityAlive() && (!player.isSneaking() || squirrel.getDistanceSqToEntity(player) < 4D)
                    && canSearch(8) && squirrel.canEntityBeSeen(player)) threat = player;
            if(threat == null) {
                for(Object nearby : world.getEntitiesWithinAABB(EntityLiving.class, squirrel.boundingBox.expand(5D, 2D, 5D))) {
                    EntityLiving predator = (EntityLiving) nearby;
                    if(predator.isEntityAlive() && predator.getAttackTarget() == squirrel) {
                        threat = predator;
                        break;
                    }
                }
            }
        }
        if(threat == null) return;
        if(now >= threatenedUntil) {
            nextDecision = now;
            watchUntil = 0;
            squirrel.getNavigator().clearPathEntity();
        }
        threatX = threat.posX;
        threatY = threat.posY;
        threatZ = threat.posZ;
        threatenedUntil = now + 36;
    }

    private boolean travelInTrees(int now) {
        nextTreeAttempt = now + 80 + random.nextInt(41);
        int x = MathHelper.floor_double(squirrel.posX), y = MathHelper.floor_double(squirrel.posY) - 1;
        int z = MathHelper.floor_double(squirrel.posZ);
        World world = squirrel.worldObj;
        if(world.blockExists(x, y, z)) {
            Block footing = world.getBlock(x, y, z);
            if((footing.isLeaves(world, x, y, z) || footing.isWood(world, x, y, z)) && findBranch()) {
                squirrel.getNavigator().clearPathEntity();
                return true;
            }
        }
        if(now >= cacheExpires || cachedTree != null && squirrel.getDistanceSq(cachedTree.x, cachedTree.y, cachedTree.z) > 49D) {
            cachedTree = findTree();
            cacheExpires = now + 100;
        }
        TreeRoute route = cachedTree;
        if(route == null || !route.hasSupport(world, route.y)) return false;
        if(!atTree(route) && !moveTo(
                Vec3.createVectorHelper(route.x, route.y, route.z), now < threatenedUntil ? 1.6D : 1D, true)) {
            cachedTree = null;
            cacheExpires = now + 144;
            return false;
        }
        tree = route;
        approachUntil = now + 60;
        return true;
    }

    private boolean atTree(TreeRoute route) {
        return squirrel.onGround && Math.abs(squirrel.posY - route.y) < 0.2D
                && squirrel.getDistanceSq(route.x, route.y, route.z) <= 0.49D;
    }

    private TreeRoute findTree() {
        World world = squirrel.worldObj;
        int x = MathHelper.floor_double(squirrel.posX), y = MathHelper.floor_double(squirrel.posY);
        int z = MathHelper.floor_double(squirrel.posZ);
        double bestScore = Double.MAX_VALUE;
        TreeRoute best = null;
        for(int dx = -6; dx <= 6; dx++) for(int dz = -6; dz <= 6; dz++) {
            if(dx * dx + dz * dz > 36 || !world.blockExists(x + dx, y, z + dz)) continue;
            for(int dy = -1; dy <= 1; dy++) {
                int tx = x + dx, ty = y + dy, tz = z + dz;
                if(!SquirrelHabitat.isLog(world, tx, ty, tz)) continue;
                for(ForgeDirection side : SIDES) {
                    int ax = tx + side.offsetX, az = tz + side.offsetZ;
                    if(!world.blockExists(ax, ty - 1, az) || !World.doesBlockHaveSolidTopSurface(world, ax, ty - 1, az)) continue;
                    double px = ax + 0.5D, pz = az + 0.5D;
                    double score = squirrel.getDistanceSq(px, ty, pz);
                    if(score >= bestScore || !SquirrelHabitat.clear(squirrel, EntitySquirrel.bodyBox(px, ty, pz))) continue;
                    double climbX = px - side.offsetX * 0.19D, climbZ = pz - side.offsetZ * 0.19D;
                    int top = ty;
                    while(top < ty + 6 && SquirrelHabitat.isLog(world, tx, top, tz)
                            && SquirrelHabitat.clear(squirrel, EntitySquirrel.bodyBox(climbX, top + 1, climbZ))) top++;
                    if(top <= ty) continue;
                    bestScore = score;
                    best = new TreeRoute(tx, tz, px, ty, pz, climbX, climbZ, top, EntitySquirrel.faceYaw(side));
                }
            }
        }
        return best;
    }

    /** Run only from the native travel step, after the server decides the next action. */
    boolean move() {
        if(leapTo != null) return leap();
        if(!climbing) return false;
        int now = squirrel.ticksExisted;
        double dx = tree.climbX - squirrel.posX, dz = tree.climbZ - squirrel.posZ;
        double remaining = Math.sqrt(dx * dx + dz * dz);
        double advance = remaining > 0.16D ? 0.16D / remaining : 1D;
        double climbY = Math.min(tree.top, squirrel.posY + 0.17D);
        if(!tree.hasSupport(squirrel.worldObj, Math.max(tree.y, MathHelper.floor_double(climbY - 0.01D)))
                || !step(squirrel.posX + dx * advance, climbY, squirrel.posZ + dz * advance)) {
            stop();
            return false;
        }
        squirrel.rotationYaw = squirrel.rotationYawHead = tree.yaw;
        if(climbY >= tree.top) {
            if(gripUntil == 0) gripUntil = now + 16 + random.nextInt(16);
            if(now >= gripUntil) {
                if(!findBranch()) {
                    stop();
                    return false;
                }
                climbing = false;
                tree = null;
            }
        }
        return true;
    }

    private boolean findBranch() {
        World world = squirrel.worldObj;
        int x = MathHelper.floor_double(squirrel.posX), y = MathHelper.floor_double(squirrel.posY);
        int z = MathHelper.floor_double(squirrel.posZ);
        double bestScore = Double.MAX_VALUE;
        Vec3 from = Vec3.createVectorHelper(squirrel.posX, squirrel.posY, squirrel.posZ);
        Vec3 to = null;
        for(int dx = -2; dx <= 2; dx++) for(int dz = -2; dz <= 2; dz++) {
            if(dx * dx + dz * dz < 2 || !world.blockExists(x + dx, y, z + dz)) continue;
            for(int dy = -1; dy <= 1; dy++) {
                int tx = x + dx, ty = y + dy, tz = z + dz;
                double score = from.squareDistanceTo(tx + 0.5D, ty, tz + 0.5D);
                if(score >= bestScore || !world.blockExists(tx, ty - 1, tz)) continue;
                Block block = world.getBlock(tx, ty - 1, tz);
                if(!block.isLeaves(world, tx, ty - 1, tz) && !block.isWood(world, tx, ty - 1, tz)) continue;
                AxisAlignedBB support = block.getCollisionBoundingBoxFromPool(world, tx, ty - 1, tz);
                if(support == null || support.maxY < ty || support.minX > tx + 0.5D || support.maxX < tx + 0.5D
                        || support.minZ > tz + 0.5D || support.maxZ < tz + 0.5D) continue;
                Vec3 landing = Vec3.createVectorHelper(tx + 0.5D, ty, tz + 0.5D);
                if(!arcClear(from, landing)) continue;
                bestScore = score;
                to = landing;
            }
        }
        if(to == null) return false;
        leapFrom = from;
        leapTo = to;
        leapTick = 0;
        squirrel.rotationYaw = squirrel.rotationYawHead = (float) Math.toDegrees(Math.atan2(from.xCoord - to.xCoord, to.zCoord - from.zCoord));
        squirrel.action(EntitySquirrel.LEAPING);
        return true;
    }

    private boolean arcClear(Vec3 from, Vec3 to) {
        Vec3 previous = from;
        for(int i = 1; i <= LEAP_TICKS; i++) {
            Vec3 point = arc(from, to, i);
            AxisAlignedBB path = EntitySquirrel.bodyBox(previous.xCoord, previous.yCoord, previous.zCoord)
                    .addCoord(point.xCoord - previous.xCoord, point.yCoord - previous.yCoord, point.zCoord - previous.zCoord);
            if(!SquirrelHabitat.clear(squirrel, path)) return false;
            previous = point;
        }
        return true;
    }

    private static Vec3 arc(Vec3 from, Vec3 to, int tick) {
        double t = (double) tick / LEAP_TICKS;
        return Vec3.createVectorHelper(from.xCoord + (to.xCoord - from.xCoord) * t,
                from.yCoord + (to.yCoord - from.yCoord) * t + 4.4D * t * (1D - t),
                from.zCoord + (to.zCoord - from.zCoord) * t);
    }

    private boolean leap() {
        Vec3 point = arc(leapFrom, leapTo, ++leapTick);
        if(!step(point.xCoord, point.yCoord, point.zCoord)) {
            stop();
            return false;
        }
        if(leapTick >= LEAP_TICKS) {
            leapTo = null;
            squirrel.action(EntitySquirrel.IDLE);
            nextDecision = squirrel.ticksExisted + 28 + random.nextInt(25);
        }
        return true;
    }

    private boolean step(double x, double y, double z) {
        double dx = x - squirrel.posX, dy = y - squirrel.posY, dz = z - squirrel.posZ;
        if(!SquirrelHabitat.clear(squirrel, squirrel.boundingBox.addCoord(dx, dy, dz))) return false;
        squirrel.moveEntity(dx, dy, dz);
        squirrel.motionX = squirrel.motionY = squirrel.motionZ = 0D;
        squirrel.fallDistance = 0F;
        return squirrel.getDistanceSq(x, y, z) < 0.01D;
    }

    private boolean forage(int now) {
        nextMeal = now + 30 + random.nextInt(31);
        double best = 25D;
        EntityItem food = null;
        for(Object entry : squirrel.worldObj.getEntitiesWithinAABB(EntityItem.class, squirrel.boundingBox.expand(5D, 2D, 5D))) {
            EntityItem item = (EntityItem) entry;
            double distance = squirrel.getDistanceSqToEntity(item);
            if(!item.isDead && item.delayBeforeCanPickup <= 0 && distance < best && isFood(item.getEntityItem())) {
                food = item;
                best = distance;
            }
        }
        if(food == null) return false;
        if(best <= 1.15D && canSearch(2) && squirrel.canEntityBeSeen(food)) {
            ItemStack stack = food.getEntityItem();
            if(stack.stackSize == 1) food.setDead();
            else {
                ItemStack remaining = stack.copy();
                remaining.stackSize--;
                food.setEntityItemStack(remaining);
            }
            squirrel.heal(1F);
            squirrel.playSound("random.eat", 0.35F, 1.4F);
            squirrel.action(EntitySquirrel.EATING);
            nextDecision = now + 16;
            nextMeal = now + 100 + random.nextInt(61);
            return true;
        }
        if(moveTo(Vec3.createVectorHelper(food.posX, food.boundingBox.minY, food.posZ), 1D, false)) {
            nextDecision = now + 18;
            return true;
        }
        return false;
    }

    private static boolean isFood(ItemStack stack) {
        if(stack == null || stack.stackSize <= 0) return false;
        if(stack.getItem() == Items.wheat_seeds || stack.getItem() == Items.pumpkin_seeds || stack.getItem() == Items.melon_seeds
                || stack.getItem() == ModItems.teaseeds)
            return true;
        for(int id : OreDictionary.getOreIDs(stack)) {
            String name = OreDictionary.getOreName(id);
            if(name.equals("listAllseed") || name.equals("listAllnut") || name.equals("cropPeanut") || name.equals("cropChestnut")
                    || name.equals("cropWalnut") || name.equals("cropHazelnut") || name.equals("cropAlmond")
                    || name.equals("cropPecan") || name.equals("cropPistachio") || name.equals("cropCashew")) return true;
        }
        return false;
    }

    private boolean moveTo(Vec3 target, double speed, boolean exact) {
        if(target == null) return false;
        int radius = (int) ((float) squirrel.getEntityAttribute(SharedMonsterAttributes.followRange).getAttributeValue() + 8F);
        if(!canSearch(radius)) return false;
        PathEntity path = squirrel.getNavigator().getPathToXYZ(target.xCoord, target.yCoord, target.zCoord);
        if(path == null) return false;
        PathPoint end = path.getFinalPathPoint();
        if(exact && (end == null || end.xCoord != MathHelper.floor_double(target.xCoord)
                || end.yCoord != MathHelper.floor_double(target.yCoord) || end.zCoord != MathHelper.floor_double(target.zCoord))) return false;
        return squirrel.getNavigator().setPath(path, speed);
    }

    private boolean canSearch(int radius) {
        int x = MathHelper.floor_double(squirrel.posX), z = MathHelper.floor_double(squirrel.posZ);
        return squirrel.worldObj.checkChunksExist(x - radius, 0, z - radius, x + radius, 0, z + radius);
    }

    private static final class TreeRoute {
        final int trunkX, trunkZ, y, top;
        final double x, z, climbX, climbZ;
        final float yaw;

        TreeRoute(int trunkX, int trunkZ, double x, int y, double z, double climbX, double climbZ, int top, float yaw) {
            this.trunkX = trunkX;
            this.trunkZ = trunkZ;
            this.x = x;
            this.y = y;
            this.z = z;
            this.climbX = climbX;
            this.climbZ = climbZ;
            this.top = top;
            this.yaw = yaw;
        }

        boolean hasSupport(World world, int y) {
            return SquirrelHabitat.isLog(world, trunkX, y, trunkZ);
        }
    }
}
