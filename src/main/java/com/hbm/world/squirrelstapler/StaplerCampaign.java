package com.hbm.world.squirrelstapler;

import com.hbm.config.SquirrelStaplerConfig;
import com.hbm.entity.mob.squirrel.EntitySquirrel;
import com.hbm.items.ModItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class StaplerCampaign extends WorldSavedData {
    private static final String NAME = "RNTSquirrelStapler";
    private static final String DAY_TOKEN = "StaplerDay", TROPHY = "StaplerTrophy", LARGE = "StaplerLarge";
    static final int HUNT = 0, COUNTDOWN = 1, ARRIVAL = 2, COMPLETE = 3;

    int day, days, normal, large, normalRequired, largeRequired, cabinY;
    int stage, remainingTicks, countdownDuration;
    boolean controlsDaylight, previousDaylight;
    String dayToken = "";
    private final Set<String> deposited = new HashSet<>();

    // Only the data instance attached to the server world owns live encounter state.
    final StaplerEncounters encounters = new StaplerEncounters();
    long nextPreyCheck;
    long clientStamp = -1L;
    boolean enabledLastTick;

    public StaplerCampaign(String name) { super(name); }

    static StaplerCampaign get(World world) {
        StaplerCampaign data = (StaplerCampaign)world.mapStorage.loadData(StaplerCampaign.class, NAME);
        if(data == null) {
            data = new StaplerCampaign(NAME);
            data.days = SquirrelStaplerConfig.settings.days;
            data.startDay(1);
            world.mapStorage.setData(NAME, data);
        }
        return data;
    }

    void startDay(int value) {
        SquirrelStaplerConfig.Values settings = SquirrelStaplerConfig.settings;
        day = value;
        normalRequired = settings.normalRequired;
        largeRequired = day == 1 ? 0 : settings.largeRequired;
        normal = large = remainingTicks = countdownDuration = 0;
        stage = HUNT;
        deposited.clear();
        dayToken = UUID.randomUUID().toString();
        encounters.clear();
        nextPreyCheck = 0;
        markDirty();
    }

    void reset() {
        days = SquirrelStaplerConfig.settings.days;
        startDay(1);
    }

    boolean ready() { return normal >= normalRequired && large >= largeRequired; }
    int normalLeft() { return Math.max(0, normalRequired - normal); }
    int largeLeft() { return Math.max(0, largeRequired - large); }
    int secondsLeft() { return (remainingTicks + 19) / 20; }

    void completeObjective() {
        if(stage != HUNT) return;
        normal = normalRequired;
        large = largeRequired;
        markDirty();
    }

    boolean startFinale() {
        if(stage != HUNT) return false;
        completeObjective();
        encounters.clear();
        stage = COUNTDOWN;
        remainingTicks = countdownDuration = SquirrelStaplerConfig.settings.finaleSeconds * 20;
        markDirty();
        return true;
    }

    public static ItemStack trophy(EntitySquirrel squirrel) {
        World world = squirrel.worldObj;
        StaplerCampaign data = get(world);
        if(data.stage != HUNT || data.ready()) return null;
        ItemStack trophy = new ItemStack(StaplerMode.trophy);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(DAY_TOKEN, data.dayToken);
        tag.setString(TROPHY, squirrel.getUniqueID().toString());
        tag.setBoolean(LARGE, squirrel.isLargeHuntingSquirrel());
        trophy.setTagCompound(tag);
        trophy.setStackDisplayName(squirrel.isLargeHuntingSquirrel() ? "Large Squirrel Trophy" : "Squirrel Trophy");
        return trophy;
    }

    boolean attach(EntityPlayer player) {
        if(stage != HUNT || ready()) {
            tell(player, "The rack has all it needs.");
            return false;
        }
        int trophySlot = -1, stapleSlot = -1;
        for(int slot = 0; slot < player.inventory.mainInventory.length; slot++) {
            ItemStack stack = player.inventory.mainInventory[slot];
            if(stack == null) continue;
            if(stapleSlot < 0 && stack.getItem() == ModItems.staples) stapleSlot = slot;
            if(trophySlot >= 0 || stack.getItem() != StaplerMode.trophy || !stack.hasTagCompound()) continue;
            NBTTagCompound tag = stack.getTagCompound();
            String id = tag.getString(TROPHY);
            boolean big = tag.getBoolean(LARGE);
            if(dayToken.equals(tag.getString(DAY_TOKEN)) && !id.isEmpty() && !deposited.contains(id)
                    && (big ? largeLeft() > 0 : normalLeft() > 0)) trophySlot = slot;
        }
        if(trophySlot < 0 || stapleSlot < 0) {
            tell(player, trophySlot < 0 ? "Bring a needed trophy from today's hunt." : "The stapler is empty. Fetch staples.");
            return false;
        }
        NBTTagCompound trophy = player.inventory.mainInventory[trophySlot].getTagCompound();
        deposited.add(trophy.getString(TROPHY));
        if(trophy.getBoolean(LARGE)) large++;
        else normal++;
        player.inventory.decrStackSize(trophySlot, 1);
        player.inventory.decrStackSize(stapleSlot, 1);
        player.inventory.markDirty();
        markDirty();
        return true;
    }

    static void tell(EntityPlayer player, String text) { player.addChatMessage(new ChatComponentText(text)); }

    @Override public void readFromNBT(NBTTagCompound tag) {
        days = Math.max(1, Math.min(30, tag.getInteger("Days")));
        day = Math.max(1, Math.min(days, tag.getInteger("Day")));
        normalRequired = Math.max(1, tag.getInteger("NormalRequired"));
        largeRequired = Math.max(0, tag.getInteger("LargeRequired"));
        normal = Math.max(0, tag.getInteger("Normal"));
        large = Math.max(0, tag.getInteger("Large"));
        cabinY = tag.getInteger("CabinY");
        stage = Math.max(HUNT, Math.min(COMPLETE, tag.getInteger("Stage")));
        remainingTicks = Math.max(0, tag.getInteger("Remaining"));
        countdownDuration = Math.max(1, tag.getInteger("CountdownDuration"));
        if(stage == COUNTDOWN) remainingTicks = Math.min(countdownDuration, remainingTicks);
        controlsDaylight = tag.getBoolean("ControlsDaylight");
        previousDaylight = tag.getBoolean("PreviousDaylight");
        dayToken = tag.getString("DayToken");
        if(dayToken.isEmpty()) dayToken = UUID.randomUUID().toString();
        deposited.clear();
        NBTTagList claimed = tag.getTagList("Deposited", 8);
        for(int i = 0; i < claimed.tagCount(); i++) deposited.add(claimed.getStringTagAt(i));
    }

    @Override public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("Days", days);
        tag.setInteger("Day", day);
        tag.setInteger("NormalRequired", normalRequired);
        tag.setInteger("LargeRequired", largeRequired);
        tag.setInteger("Normal", normal);
        tag.setInteger("Large", large);
        tag.setInteger("CabinY", cabinY);
        tag.setInteger("Stage", stage);
        tag.setInteger("Remaining", remainingTicks);
        tag.setInteger("CountdownDuration", countdownDuration);
        tag.setBoolean("ControlsDaylight", controlsDaylight);
        tag.setBoolean("PreviousDaylight", previousDaylight);
        tag.setString("DayToken", dayToken);
        NBTTagList claimed = new NBTTagList();
        for(String id : deposited) claimed.appendTag(new NBTTagString(id));
        tag.setTag("Deposited", claimed);
    }
}
