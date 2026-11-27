package com.hbm.items.tool;

import java.util.List;

import com.hbm.lib.RefStrings;
import com.hbm.main.MainRegistry;
import com.hbm.util.fauxpointtwelve.Rotation;
import com.hbm.world.gen.structure.SettlementGenerator;
import com.hbm.world.gen.structure.SettlementKind;
import com.hbm.world.gen.structure.SettlementRoads.Profile;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

public final class ItemSettlementWand extends Item {
    private static final Rotation[] ROTATIONS = Rotation.values();
    private static final Profile[] PROFILES = Profile.values();
    private final SettlementKind kind;

    public ItemSettlementWand(SettlementKind kind, String texture) {
        this.kind = kind;
        setUnlocalizedName(kind.wandName());
        setTextureName(RefStrings.MODID + ":" + texture);
        setMaxStackSize(1);
        setCreativeTab(MainRegistry.consumableTab);
        setFull3D();
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
            int side, float hitX, float hitY, float hitZ) {
        // The client must run its normal interaction path to send the right-click packet.
        return !world.isRemote && useOnBlock(stack, player, world, x, y, z, side);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
            int side, float hitX, float hitY, float hitZ) {
        return world.isRemote || useOnBlock(stack, player, world, x, y, z, side);
    }

    private boolean useOnBlock(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side) {
        if(!canUse(player)) return true;
        if(player.isSneaking()) {
            cycle(stack, "Variant", kind.variantCount());
            feedback(player, "selected", name(variant(stack)));
        } else if(side != 1) {
            feedback(player, "top");
        } else {
            SettlementGenerator.request(world, player, kind, variant(stack), ROTATIONS[rotation(stack)], profile(stack), x, y, z);
        }
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if(world.isRemote || !canUse(player)) return stack;
        if(player.isSneaking()) {
            if(kind == SettlementKind.ROAD) {
                cycle(stack, "RoadProfile", PROFILES.length);
                feedback(player, "selected", new ChatComponentTranslation(profile(stack).nameKey()));
            } else {
                cycle(stack, "Variant", kind.variantCount());
                feedback(player, "selected", name(variant(stack)));
            }
        } else {
            cycle(stack, "Rotation", ROTATIONS.length);
            feedback(player, "rotation", rotation(stack) * 90);
        }
        return stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(StatCollector.translateToLocalFormatted("settlement.wand.selected", StatCollector.translateToLocal(kind.variantKey(variant(stack)))));
        if(kind == SettlementKind.ROAD) lines.add(StatCollector.translateToLocal(profile(stack).nameKey()));
        lines.add(StatCollector.translateToLocalFormatted("settlement.wand.rotation", rotation(stack) * 90));
        lines.add(StatCollector.translateToLocal("settlement.wand.controls"));
        if(kind == SettlementKind.ROAD) lines.add(StatCollector.translateToLocal("settlement.wand.roadControls"));
    }

    private boolean canUse(EntityPlayer player) {
        if(player.capabilities.isCreativeMode) return true;
        feedback(player, "creative");
        return false;
    }

    private int variant(ItemStack stack) { return read(stack, "Variant", kind.variantCount()); }
    private int rotation(ItemStack stack) { return read(stack, "Rotation", ROTATIONS.length); }
    private Profile profile(ItemStack stack) { return PROFILES[read(stack, "RoadProfile", PROFILES.length)]; }
    private ChatComponentTranslation name(int variant) { return new ChatComponentTranslation(kind.variantKey(variant)); }

    private static int read(ItemStack stack, String key, int count) {
        return stack.hasTagCompound() ? Math.floorMod(stack.getTagCompound().getInteger(key), count) : 0;
    }

    private static void cycle(ItemStack stack, String key, int count) {
        if(!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger(key, (read(stack, key, count) + 1) % count);
    }

    private static void feedback(EntityPlayer player, String key, Object... args) {
        player.addChatMessage(new ChatComponentTranslation("settlement.wand." + key, args));
    }
}
