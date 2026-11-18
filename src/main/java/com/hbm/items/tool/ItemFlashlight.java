package com.hbm.items.tool;

import java.util.List;
import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.EntityWoodcrawler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** A held, directed source. No temporary blocks, chunk loading or global lighting replacement. */
public final class ItemFlashlight extends Item {
    public ItemFlashlight() { setMaxStackSize(1); setFull3D(); }
    public static boolean on(ItemStack stack) { return stack != null && stack.getItem() instanceof ItemFlashlight && stack.hasTagCompound() && stack.stackTagCompound.getBoolean("On"); }
    public static boolean uv(ItemStack stack) { return stack != null && stack.hasTagCompound() && stack.stackTagCompound.getBoolean("UV"); }
    public static double range(ItemStack stack) { return stack != null && stack.hasTagCompound() ? Math.max(4, Math.min(48, stack.stackTagCompound.getInteger("BeamRange"))) : 20; }
    @Override public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if(!world.isRemote) {
            if(!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
            if(player.isSneaking()) stack.stackTagCompound.setBoolean("UV", !uv(stack));
            else stack.stackTagCompound.setBoolean("On", !on(stack));
            stack.stackTagCompound.setInteger("BeamRange", GeminiConfig.flashlightRange);
            player.inventory.markDirty(); player.inventoryContainer.detectAndSendChanges();
            world.playSoundAtEntity(player, "random.click", 0.3F, uv(stack) ? 1.4F : 1F);
        }
        return stack;
    }
    @Override public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        if(world.isRemote || !held || !(entity instanceof EntityPlayer)) return;
        if(!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        if(stack.stackTagCompound.getInteger("BeamRange") != GeminiConfig.flashlightRange) stack.stackTagCompound.setInteger("BeamRange", GeminiConfig.flashlightRange);
        if(!on(stack) || (world.getTotalWorldTime() + entity.getEntityId()) % 5 != 0) return;
        EntityPlayer player = (EntityPlayer) entity;
        Vec3 from = Vec3.createVectorHelper(player.posX, player.boundingBox.minY + player.getEyeHeight(), player.posZ);
        Vec3 direction = player.getLookVec(); double distance = GeminiConfig.flashlightRange;
        Vec3 end = from.addVector(direction.xCoord * distance, direction.yCoord * distance, direction.zCoord * distance);
        double spread = 0.25 + distance * 0.16;
        AxisAlignedBB bounds = AxisAlignedBB.getBoundingBox(Math.min(from.xCoord, end.xCoord) - spread, Math.min(from.yCoord, end.yCoord) - spread,
                Math.min(from.zCoord, end.zCoord) - spread, Math.max(from.xCoord, end.xCoord) + spread,
                Math.max(from.yCoord, end.yCoord) + spread, Math.max(from.zCoord, end.zCoord) + spread);
        int examined = 0;
        for(Object entry : world.getEntitiesWithinAABB(EntityWoodcrawler.class, bounds)) {
            if(++examined > 32) break;
            EntityWoodcrawler mob = (EntityWoodcrawler) entry;
            // Sample the actual collision box, not tall eyes outside the player's aim.
            for(int i = 0; i < 3; i++) {
                Vec3 point = Vec3.createVectorHelper(mob.posX, mob.boundingBox.minY + mob.height * (0.2 + i * 0.3), mob.posZ);
                double dx = point.xCoord - from.xCoord, dy = point.yCoord - from.yCoord, dz = point.zCoord - from.zCoord;
                double along = dx * direction.xCoord + dy * direction.yCoord + dz * direction.zCoord;
                double radius = 0.25 + along * 0.16;
                if(along < 0 || along > distance || dx * dx + dy * dy + dz * dz - along * along > radius * radius
                        || !com.hbm.world.gemini.GardenerNests.loaded(world, AxisAlignedBB.getBoundingBox(Math.min(from.xCoord, point.xCoord), Math.min(from.yCoord, point.yCoord), Math.min(from.zCoord, point.zCoord),
                            Math.max(from.xCoord, point.xCoord), Math.max(from.yCoord, point.yCoord), Math.max(from.zCoord, point.zCoord)))
                        || world.func_147447_a(Vec3.createVectorHelper(from.xCoord, from.yCoord, from.zCoord), point, false, true, false) != null) continue;
                mob.receiveFlashlight(player.posX, player.posZ, uv(stack)); break;
            }
        }
    }
    @Override public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(net.minecraft.util.StatCollector.translateToLocal("flashlight.controls"));
        lines.add(net.minecraft.util.StatCollector.translateToLocal(uv(stack) ? "flashlight.uv" : "flashlight.visible")
                + " / " + net.minecraft.util.StatCollector.translateToLocal(on(stack) ? "flashlight.on" : "flashlight.off"));
    }
    @Override public int getColorFromItemStack(ItemStack stack, int pass) { return uv(stack) ? 0xBC88FF : 0xFFFFFF; }
}
