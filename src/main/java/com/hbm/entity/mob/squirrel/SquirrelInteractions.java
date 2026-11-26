package com.hbm.entity.mob.squirrel;

import com.hbm.config.SquirrelConfig;
import com.hbm.entity.mob.squirrel.SquirrelAttachments.Attachment;
import com.hbm.handler.GeminiCaptures;
import com.hbm.handler.WoodcrawlerCaptures;
import com.hbm.handler.restraint.Restraints;
import com.hbm.items.ModItems;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public final class SquirrelInteractions {
    static void useEmptyHand(EntityPlayer player, EntitySquirrel squirrel) {
        if(player.getDistanceSqToEntity(squirrel) > 36D || !squirrel.isEntityAlive()
                || Restraints.blocked(player) || !GeminiCaptures.available(player)) return;
        if(squirrel.isAttached()) {
            Attachment attachment = squirrel.getAttachment();
            if(attachment != null && player.worldObj.canMineBlock(player, attachment.x, attachment.y, attachment.z)
                    && player.canPlayerEdit(attachment.x, attachment.y, attachment.z, attachment.face.ordinal(), null)) {
                squirrel.detach();
                Restraints.message(player, "squirrel.detached");
            }
            return;
        }
        EntitySquirrel current = GeminiCaptures.captive(player, EntitySquirrel.class);
        if(current != null) {
            if(current == squirrel) release(player, current);
            else Restraints.message(player, "squirrel.already_carrying");
            return;
        }
        if(!SquirrelConfig.settings.enabled || squirrel.getLeashed() || squirrel.riddenByEntity != null
                || !WoodcrawlerCaptures.available(squirrel)) return;
        if(GeminiCaptures.attach(player, squirrel, false, reason -> squirrel.carriedBy(null), () -> carry(player, squirrel))) {
            double x = squirrel.posX, y = squirrel.posY, z = squirrel.posZ;
            squirrel.carriedBy(player);
            if(!carry(player, squirrel)) {
                GeminiCaptures.release(player, squirrel);
                squirrel.carriedBy(null);
                return;
            }
            Restraints.message(player, "squirrel.carried");
            MinecraftForge.EVENT_BUS.post(new Captured(player, squirrel, x, y, z));
        }
    }

    private static boolean carry(EntityPlayer player, EntitySquirrel squirrel) {
        if(!SquirrelConfig.settings.enabled || player.isPlayerSleeping() || Restraints.blocked(player)
                || !GeminiCaptures.available(player)) return false;
        double yaw = Math.toRadians(player.rotationYaw);
        double x = player.posX - Math.cos(yaw) * 0.52D, z = player.posZ - Math.sin(yaw) * 0.52D;
        double low = player.boundingBox.minY, y = low + 1.02D;
        if(!SquirrelHabitat.clear(squirrel, EntitySquirrel.bodyBox(x, y, z))) {
            x = player.posX;
            y = low + 0.28D;
            z = player.posZ;
        }
        if(!SquirrelHabitat.clear(squirrel, EntitySquirrel.bodyBox(x, y, z))) return false;
        GeminiCaptures.position(squirrel, x, y, z);
        squirrel.rotationYaw = squirrel.rotationYawHead = squirrel.renderYawOffset = player.rotationYaw;
        return true;
    }

    public static boolean isCarrying(EntityPlayer player) {
        if(!player.worldObj.isRemote) return GeminiCaptures.captive(player, EntitySquirrel.class) != null;
        for(Object entry : player.worldObj.getEntitiesWithinAABB(EntitySquirrel.class, player.boundingBox.expand(2D, 2D, 2D))) {
            if(((EntitySquirrel) entry).getCarrierId() == player.getEntityId()) return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void releaseOnBlock(PlayerInteractEvent event) {
        EntityPlayer player = event.entityPlayer;
        if(event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || event.useBlock == Event.Result.DENY
                || !player.isSneaking() || player.getHeldItem() != null || Restraints.blocked(player)) return;
        if(event.world.isRemote) return;
        EntitySquirrel squirrel = GeminiCaptures.captive(player, EntitySquirrel.class);
        if(squirrel != null) {
            event.setCanceled(true);
            release(player, squirrel);
        }
    }

    private static void release(EntityPlayer player, EntitySquirrel squirrel) {
        GeminiCaptures.release(player, squirrel);
        squirrel.carriedBy(null);
        Restraints.message(player, "squirrel.released");
    }

    public static boolean staple(ItemStack stapler, EntityPlayer player, int x, int y, int z, int clickedSide) {
        World world = player.worldObj;
        if(world.isRemote) return isCarrying(player);
        EntitySquirrel squirrel = GeminiCaptures.captive(player, EntitySquirrel.class);
        if(squirrel == null) return false;
        if(Restraints.blocked(player) || !GeminiCaptures.available(player)) return true;
        SquirrelConfig.Values settings = SquirrelConfig.settings;
        if(!settings.enabled || !settings.stapling) {
            Restraints.message(player, "squirrel.stapling_disabled");
            return true;
        }
        ForgeDirection face = ForgeDirection.getOrientation(clickedSide);
        if(face == ForgeDirection.UNKNOWN || !SquirrelHabitat.isLog(world, x, y, z)
                || !world.canMineBlock(player, x, y, z) || !player.canPlayerEdit(x, y, z, clickedSide, stapler)
                || !player.canPlayerEdit(x + face.offsetX, y + face.offsetY, z + face.offsetZ, clickedSide, stapler)) {
            Restraints.message(player, "squirrel.needs_log");
            return true;
        }
        AxisAlignedBB space = EntitySquirrel.faceBox(x, y, z, face);
        if(!SquirrelHabitat.loaded(world, space.expand(1D, 0D, 1D)) || !SquirrelHabitat.clear(squirrel, space)
                || !world.checkNoEntityCollision(space, squirrel)) {
            Restraints.message(player, "squirrel.face_blocked");
            return true;
        }
        for(Object entry : world.getEntitiesWithinAABB(EntitySquirrel.class, space)) {
            EntitySquirrel other = (EntitySquirrel) entry;
            if(other != squirrel && other.isAttached()) {
                Restraints.message(player, "squirrel.face_blocked");
                return true;
            }
        }
        int cost = settings.staplesPerAttachment;
        int total = 0;
        for(ItemStack stack : player.inventory.mainInventory) {
            if(stack != null && stack.getItem() == ModItems.staples) total += stack.stackSize;
        }
        if(total < cost) {
            Restraints.message(player, "squirrel.needs_staples", cost);
            return true;
        }

        Attachment attachment = new Attachment(squirrel, player.getUniqueID(), x, y, z, face);
        GeminiCaptures.release(player, squirrel);
        squirrel.carriedBy(null);
        squirrel.pin(attachment);
        for(int slot = 0; slot < player.inventory.mainInventory.length && cost > 0; slot++) {
            ItemStack stack = player.inventory.mainInventory[slot];
            if(stack == null || stack.getItem() != ModItems.staples) continue;
            int used = Math.min(cost, stack.stackSize);
            player.inventory.decrStackSize(slot, used);
            cost -= used;
        }
        player.inventory.markDirty();
        SquirrelAttachments.get(world).record(attachment);
        squirrel.playSound("random.click", 0.65F, 1.35F);
        Restraints.message(player, "squirrel.attached");
        MinecraftForge.EVENT_BUS.post(new Attached(player, squirrel, attachment));
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void join(EntityJoinWorldEvent event) {
        if(event.world.isRemote) return;
        if(event.entity instanceof EntitySquirrel && !com.hbm.world.squirrelstapler.StaplerWorldType.active(event.world) && !SquirrelConfig.settings.enabled
                && !((EntitySquirrel) event.entity).wasLoadedFromNBT()) {
            event.setCanceled(true);
            return;
        }
        Class<?> type = event.entity.getClass();
        if(type != EntityWolf.class && type != EntityOcelot.class) return;
        EntityTameable predator = (EntityTameable) event.entity;
        for(Object entry : predator.targetTasks.taskEntries) {
            if(((EntityAITasks.EntityAITaskEntry) entry).action instanceof HuntSquirrel) return;
        }
        predator.targetTasks.addTask(type == EntityWolf.class ? 5 : 2, new HuntSquirrel(predator));
    }

    private static boolean wildPrey(Entity entity) {
        return entity instanceof EntitySquirrel && entity.isEntityAlive() && GeminiCaptures.available(entity);
    }

    private static final class HuntSquirrel extends EntityAINearestAttackableTarget {
        private final EntityTameable predator;

        HuntSquirrel(EntityTameable predator) {
            super(predator, EntitySquirrel.class, predator instanceof EntityWolf ? 200 : 300, true, false,
                    SquirrelInteractions::wildPrey);
            this.predator = predator;
        }

        @Override public boolean shouldExecute() {
            return SquirrelConfig.settings.enabled && !predator.isTamed() && super.shouldExecute();
        }

        @Override public boolean continueExecuting() {
            return SquirrelConfig.settings.enabled && !predator.isTamed() && wildPrey(predator.getAttackTarget())
                    && super.continueExecuting();
        }
    }

    /** Posted on the Forge bus on the server thread after a player takes custody. */
    public static final class Captured extends PlayerEvent {
        public final EntitySquirrel squirrel;
        public final double x, y, z;

        private Captured(EntityPlayer player, EntitySquirrel squirrel, double x, double y, double z) {
            super(player);
            this.squirrel = squirrel;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** Posted on the Forge bus on the server thread after placement, payment, and progression recording. */
    public static final class Attached extends PlayerEvent {
        public final EntitySquirrel squirrel;
        public final Attachment attachment;

        private Attached(EntityPlayer player, EntitySquirrel squirrel, Attachment attachment) {
            super(player);
            this.squirrel = squirrel;
            this.attachment = attachment;
        }
    }
}
