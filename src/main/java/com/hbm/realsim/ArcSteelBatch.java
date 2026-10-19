package com.hbm.realsim;

import java.util.ArrayList;
import java.util.List;
import com.hbm.items.ModItems;
import com.hbm.items.ItemEnums.EnumCokeType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Owning-thread, count-based EAF bill. Recovery ratios are deliberately separate from physical mass bills. */
public final class ArcSteelBatch {
    public static final int DRI = 8, STEEL = 32, SLAG = 2;
    public static final long WORK_QUANTA = IndustrialDemand.arcWorkQuanta(STEEL * 1000000L);
    private ItemStack[] reserved;
    private ItemStack[] products;
    private long required, paid;
    public boolean active() { return reserved != null; }
    public boolean complete() { return active() && paid == required; }
    public long required() { return active() ? required : WORK_QUANTA; }
    public long paid() { return paid; }
    public static boolean input(ItemStack stack) { return ingredient(stack) >= 0; }
    private static int ingredient(ItemStack stack) {
        if(stack == null || stack.stackSize <= 0 || stack.getItem().hasContainerItem(stack)) return -1;
        if(stack.getItem() == ModItems.iron_dri && stack.getItemDamage() == 0) return 0;
        if(stack.getItem() == ModItems.coke && stack.getItemDamage() == EnumCokeType.COAL.ordinal()) return 1;
        if(stack.getItem() == ModItems.quicklime && stack.getItemDamage() == 0) return 2;
        return -1;
    }
    public static boolean containsDRI(ItemStack[] slots) {
        for(int i = 5; i < 25; i++) if(ingredient(slots[i]) == 0) return true;
        return false;
    }
    private static ItemStack[] copy(ItemStack[] slots) {
        ItemStack[] result = slots.clone();
        for(int i = 5; i < 25; i++) if(result[i] != null) result[i] = result[i].copy();
        return result;
    }
    private static ItemStack[] output() { return new ItemStack[] {new ItemStack(ModItems.ingot_steel, STEEL), new ItemStack(ModItems.slagingot, SLAG)}; }
    private static boolean fit(ItemStack[] slots, ItemStack[] products) {
        for(ItemStack product : products) if(!ProcessMachine.add(slots, 5, 24, product.copy())) return false;
        return true;
    }
    private static List<ItemStack> removeFeeds(ItemStack[] slots) {
        int[] left = {DRI, 1, 1}; List<ItemStack> feeds = new ArrayList<ItemStack>();
        for(int i = 5; i < 25; i++) {
            int kind = ingredient(slots[i]);
            if(kind < 0 || left[kind] == 0) continue;
            int amount = Math.min(left[kind], slots[i].stackSize);
            ItemStack feed = slots[i].copy(); feed.stackSize = amount; feeds.add(feed);
            slots[i].stackSize -= amount; left[kind] -= amount;
            if(slots[i].stackSize == 0) slots[i] = null;
        }
        return left[0] == 0 && left[1] == 0 && left[2] == 0 ? feeds : null;
    }
    public static boolean canReserve(ItemStack[] slots) {
        ItemStack[] preview = copy(slots);
        return removeFeeds(preview) != null && fit(preview, output());
    }
    public boolean reserve(ItemStack[] slots) {
        if(active()) return false;
        ItemStack[] preview = copy(slots); List<ItemStack> feeds = removeFeeds(preview);
        if(feeds == null || !fit(copy(preview), output())) return false;
        // Every ingredient and all product space have been checked before committing any mutation.
        for(int i = 5; i < 25; i++) slots[i] = preview[i];
        reserved = feeds.toArray(new ItemStack[feeds.size()]); products = output(); required = WORK_QUANTA; paid = 0;
        return true;
    }
    public boolean outputReady(ItemStack[] slots) { return active() && fit(copy(slots), products); }
    /** Owner debits the returned work from its native electrical buffer in the same transaction. */
    public long pay(long budget) {
        if(!active() || budget < 0) throw new IllegalStateException("Invalid arc steel payment");
        long debit = Math.min(required - paid, budget); paid += debit; return debit;
    }
    public boolean finish(ItemStack[] slots) {
        if(!complete() || !outputReady(slots)) return false;
        for(ItemStack product : products) ProcessMachine.add(slots, 5, 24, product.copy());
        clear(); return true;
    }
    /** Teardown yields either the original unfinished feeds or the completed products, once. */
    public ItemStack[] takeDrops() {
        if(!active()) return new ItemStack[0];
        ItemStack[] result = complete() ? products : reserved; clear(); return result;
    }
    private void clear() { reserved = null; products = null; paid = 0; required = 0; }
    public void write(NBTTagCompound parent) {
        if(!active()) { parent.removeTag("realSimArcSteel"); return; }
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("version", 1); tag.setLong("required", required); tag.setLong("paid", paid);
        tag.setTag("feeds", writeStacks(reserved)); tag.setTag("products", writeStacks(products)); parent.setTag("realSimArcSteel", tag);
    }
    private static NBTTagList writeStacks(ItemStack[] stacks) {
        NBTTagList list = new NBTTagList();
        for(ItemStack stack : stacks) { NBTTagCompound tag = new NBTTagCompound(); stack.writeToNBT(tag); list.appendTag(tag); }
        return list;
    }
    private static ItemStack[] readStacks(NBTTagList list) {
        if(list.tagCount() < 1 || list.tagCount() > 20) throw new IllegalStateException("Invalid arc steel inventory");
        ItemStack[] result = new ItemStack[list.tagCount()];
        for(int i = 0; i < result.length; i++) {
            result[i] = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i));
            if(result[i] == null || result[i].stackSize <= 0 || result[i].stackSize > result[i].getMaxStackSize()) throw new IllegalStateException("Invalid arc steel stack");
        }
        return result;
    }
    public void read(NBTTagCompound parent) {
        clear(); if(!parent.hasKey("realSimArcSteel", 10)) return;
        NBTTagCompound tag = parent.getCompoundTag("realSimArcSteel");
        if(tag.getInteger("version") != 1) throw new IllegalStateException("Unsupported arc steel batch");
        required = tag.getLong("required"); paid = tag.getLong("paid");
        if(required <= 0 || required > 1000000000L || paid < 0 || paid > required) throw new IllegalStateException("Invalid arc steel work");
        reserved = readStacks(tag.getTagList("feeds", 10)); products = readStacks(tag.getTagList("products", 10));
    }
}
