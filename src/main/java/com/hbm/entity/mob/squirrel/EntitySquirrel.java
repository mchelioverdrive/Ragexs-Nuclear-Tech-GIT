package com.hbm.entity.mob.squirrel;

import com.hbm.config.SquirrelConfig;
import com.hbm.entity.mob.squirrel.SquirrelAttachments.Attachment;
import com.hbm.handler.restraint.Restraints;
import com.hbm.items.ModItems;
import com.hbm.world.squirrelstapler.StaplerCampaign;
import com.hbm.world.squirrelstapler.StaplerWorldType;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class EntitySquirrel extends EntityAnimal {
    private static final int ATTACHED_FACE = 20, CARRIER = 21, ACTION = 22;
    public static final byte IDLE = 0, CLIMBING = 1, LEAPING = 2, EATING = 3;
    private static final float WIDTH = 0.58F, HEIGHT = 0.78F;

    private final SquirrelBehavior behavior = new SquirrelBehavior(this);
    private SquirrelConfig.Values appliedConfig;
    private Attachment attachment;
    private boolean restoreAttachment, restored;

    public EntitySquirrel(World world) {
        super(world);
        setSize(WIDTH * bodyScale(), HEIGHT * bodyScale());
        tasks.addTask(0, new EntityAISwimming(this));
        getNavigator().setAvoidsWater(true);
        getNavigator().setEnterDoors(false);
    }

    @Override protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(ATTACHED_FACE, (byte) -1);
        dataWatcher.addObject(CARRIER, -1);
        dataWatcher.addObject(ACTION, IDLE);
    }

    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(maximumHealth(SquirrelConfig.settings));
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.26D);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(8D);
    }

    @Override public boolean isAIEnabled() { return true; }
    @Override public EntityAgeable createChild(EntityAgeable mate) { return new EntitySquirrel(worldObj); }
    @Override public boolean isBreedingItem(ItemStack stack) { return false; }

    protected float bodyScale() { return 1F; }
    protected double maximumHealth(SquirrelConfig.Values settings) { return settings.health; }
    public boolean isLargeHuntingSquirrel() { return false; }
    public boolean canUseTrees() { return true; }
    public void frighten(EntityPlayer player) { behavior.frighten(player, 130); }

    @Override protected boolean canDespawn() { return StaplerWorldType.active(worldObj) && !isAttached() && getCarrierId() < 0; }

    @Override protected String getLivingSound() {
        return StaplerWorldType.active(worldObj) ? "hbm:stapler.chatter" : super.getLivingSound();
    }

    @Override protected void dropFewItems(boolean killedByPlayer, int looting) {
        if(StaplerWorldType.active(worldObj) && killedByPlayer) {
            ItemStack trophy = StaplerCampaign.trophy(this);
            if(trophy != null) {
                entityDropItem(trophy, 0F);
                return;
            }
        }
        dropItem(isBurning() ? ModItems.cooked_squirrel : ModItems.squirrel, 1);
    }

    @Override public boolean getCanSpawnHere() {
        return SquirrelHabitat.clear(this, boundingBox) && worldObj.checkNoEntityCollision(boundingBox);
    }

    public boolean isAttached() { return dataWatcher.getWatchableObjectByte(ATTACHED_FACE) >= 0; }
    public int getCarrierId() { return dataWatcher.getWatchableObjectInt(CARRIER); }
    public byte getAction() { return dataWatcher.getWatchableObjectByte(ACTION); }
    public ForgeDirection getAttachmentFace() { return ForgeDirection.getOrientation(dataWatcher.getWatchableObjectByte(ATTACHED_FACE)); }
    public Attachment getAttachment() { return attachment; }

    void action(byte action) { dataWatcher.updateObject(ACTION, action); }
    boolean wasLoadedFromNBT() { return restored; }

    void carriedBy(EntityPlayer player) {
        dataWatcher.updateObject(CARRIER, player == null ? -1 : player.getEntityId());
        if(player != null) behavior.stop();
    }

    @Override public boolean interact(EntityPlayer player) {
        if(StaplerWorldType.active(worldObj)) return false;
        if(player.isSneaking() && player.getHeldItem() == null) {
            if(!worldObj.isRemote) SquirrelInteractions.useEmptyHand(player, this);
            return true;
        }
        return super.interact(player);
    }

    @Override protected void updateAITasks() {
        if(isAttached()) return;
        if(!worldObj.isRemote && isEntityAlive()) {
            if(Restraints.blocked(this)) behavior.stop();
            else behavior.tick();
        }
        super.updateAITasks();
    }

    @Override public void onUpdate() {
        if(!worldObj.isRemote) {
            SquirrelConfig.Values settings = SquirrelConfig.settings;
            if(appliedConfig != settings) {
                appliedConfig = settings;
                getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(maximumHealth(settings));
                if(getHealth() > getMaxHealth()) setHealth(getMaxHealth());
            }
            if(attachment != null) updateAttachment();
        }
        super.onUpdate();
    }

    private void updateAttachment() {
        if(restoreAttachment) {
            restoreAttachment = false;
            SquirrelAttachments.get(worldObj).restore(attachment);
        }
        if(worldObj.blockExists(attachment.x, attachment.y, attachment.z)) {
            Block current = worldObj.getBlock(attachment.x, attachment.y, attachment.z);
            if(!current.isWood(worldObj, attachment.x, attachment.y, attachment.z)
                    || !attachment.log.equals(Block.blockRegistry.getNameForObject(current))
                    || attachment.metadata != worldObj.getBlockMetadata(attachment.x, attachment.y, attachment.z)) {
                detach();
                return;
            }
        }
        holdPosition();
    }

    void pin(Attachment attachment) {
        this.attachment = attachment;
        dataWatcher.updateObject(ATTACHED_FACE, (byte) attachment.face.ordinal());
        behavior.stop();
        holdPosition();
        worldObj.updateEntityWithOptionalForce(this, false);
        velocityChanged = true;
    }

    void detach() {
        if(attachment == null) return;
        attachment = null;
        restoreAttachment = false;
        dataWatcher.updateObject(ATTACHED_FACE, (byte) -1);
        SquirrelAttachments.get(worldObj).remove(getUniqueID());
    }

    private void holdPosition() {
        double x = faceX(attachment.x, attachment.face), y = faceY(attachment.y, attachment.face);
        double z = faceZ(attachment.z, attachment.face);
        if(posX != x || posY != y || posZ != z) setPosition(x, y, z);
        rotationYaw = rotationYawHead = renderYawOffset = faceYaw(attachment.face);
        rotationPitch = 0;
        stopMotion();
    }

    private void stopMotion() {
        motionX = motionY = motionZ = 0D;
        moveStrafing = moveForward = 0F;
        setJumping(false);
        fallDistance = 0F;
    }

    static AxisAlignedBB faceBox(int x, int y, int z, ForgeDirection face) {
        return bodyBox(faceX(x, face), faceY(y, face), faceZ(z, face));
    }

    static AxisAlignedBB bodyBox(double x, double y, double z) {
        double halfWidth = WIDTH / 2D;
        return AxisAlignedBB.getBoundingBox(x - halfWidth, y, z - halfWidth, x + halfWidth, y + HEIGHT, z + halfWidth);
    }

    private static double faceX(int x, ForgeDirection face) { return x + 0.5D + face.offsetX * (0.501D + WIDTH / 2D); }
    private static double faceZ(int z, ForgeDirection face) { return z + 0.5D + face.offsetZ * (0.501D + WIDTH / 2D); }

    private static double faceY(int y, ForgeDirection face) {
        if(face == ForgeDirection.UP) return y + 1.001D;
        if(face == ForgeDirection.DOWN) return y - HEIGHT - 0.001D;
        return y + (1D - HEIGHT) / 2D;
    }

    public static float faceYaw(ForgeDirection face) {
        switch(face) {
            case SOUTH: return 180F;
            case WEST: return -90F;
            case EAST: return 90F;
            default: return 0F;
        }
    }

    @Override public void moveEntity(double x, double y, double z) {
        if(isAttached()) stopMotion();
        else super.moveEntity(x, y, z);
    }

    @Override public void moveEntityWithHeading(float strafe, float forward) {
        if(isAttached()) {
            stopMotion();
        } else if(worldObj.isRemote || !behavior.move()) {
            super.moveEntityWithHeading(strafe, forward);
        }
    }

    @Override protected void collideWithNearbyEntities() {
        if(!isAttached() && getCarrierId() < 0) super.collideWithNearbyEntities();
    }

    @Override public boolean canBePushed() { return !isAttached() && getCarrierId() < 0 && super.canBePushed(); }
    @Override public boolean canBeCollidedWith() { return getCarrierId() < 0 && super.canBeCollidedWith(); }
    @Override public boolean allowLeashing() { return !isAttached() && getCarrierId() < 0 && super.allowLeashing(); }

    @Override public void travelToDimension(int destination) {
        if(!isAttached() && getCarrierId() < 0) super.travelToDimension(destination);
    }

    @Override public void mountEntity(Entity mount) {
        if(mount == null || !isAttached() && getCarrierId() < 0) super.mountEntity(mount);
    }

    @Override protected void fall(float distance) { super.fall(Math.max(0F, distance - 4F)); }

    @Override public void setDead() {
        if(!worldObj.isRemote) detach();
        super.setDead();
    }

    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setBoolean("SquirrelSaved", true);
        if(attachment != null && isEntityAlive()) tag.setTag("SquirrelAttachment", attachment.write());
    }

    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        restored = tag.getBoolean("SquirrelSaved");
        appliedConfig = null;
        Attachment saved = Attachment.read(tag.getCompoundTag("SquirrelAttachment"));
        attachment = saved != null && saved.dimension == worldObj.provider.dimensionId && saved.squirrel.equals(getUniqueID())
                ? saved : null;
        restoreAttachment = attachment != null;
        dataWatcher.updateObject(ATTACHED_FACE, attachment == null ? (byte) -1 : (byte) attachment.face.ordinal());
    }
}
