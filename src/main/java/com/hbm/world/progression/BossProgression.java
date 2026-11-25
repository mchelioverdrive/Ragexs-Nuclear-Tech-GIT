package com.hbm.world.progression;

import java.io.File;
import java.util.EnumSet;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Server-side boss milestones shared by all players and dimensions of a save. */
public final class BossProgression extends WorldSavedData {
    private static final String NAME = "rntBossProgression";

    public enum Boss {
        ENDER_DRAGON("EnderDragon", null),
        WITHER_STORM("WitherStorm", ENDER_DRAGON),
        PRISMARINE_COLOSSUS("PrismarineColossus", WITHER_STORM);

        private final String saveKey;
        private final Boss prerequisite;

        Boss(String saveKey, Boss prerequisite) {
            this.saveKey = saveKey;
            this.prerequisite = prerequisite;
        }
    }

    private final EnumSet<Boss> defeated = EnumSet.noneOf(Boss.class);
    private boolean witherStormBypass;
    private boolean legacyEndChecked;

    public BossProgression(String name) {
        super(name);
    }

    public static BossProgression get(World world) {
        World overworld = DimensionManager.getWorld(0);
        if(overworld == null) overworld = world;
        BossProgression data = (BossProgression)overworld.mapStorage.loadData(BossProgression.class, NAME);
        if(data == null) {
            data = new BossProgression(NAME);
            overworld.mapStorage.setData(NAME, data);
        }
        return data;
    }

    public boolean canSummon(Boss boss) {
        return (boss == Boss.WITHER_STORM && witherStormBypass)
            || boss.prerequisite == null || isDefeated(boss.prerequisite);
    }

    public boolean isDefeated(Boss boss) {
        return defeated.contains(boss);
    }

    public void recordDefeat(Boss boss) {
        if(defeated.add(boss)) markDirty();
    }

    public boolean isWitherStormBypassEnabled() {
        return witherStormBypass;
    }

    public void setWitherStormBypass(boolean enabled) {
        if(witherStormBypass == enabled) return;
        witherStormBypass = enabled;
        markDirty();
    }

    public static void importLegacyEnd(World overworld) {
        BossProgression data = get(overworld);
        if(data.legacyEndChecked || data.defeated.contains(Boss.ENDER_DRAGON)) return;
        File end = new File(overworld.getSaveHandler().getWorldDirectory(), "DIM-1");
        EnderDragonHistory.Result result = EnderDragonHistory.findExitPortal(end);
        if(result == EnderDragonHistory.Result.FOUND) data.recordDefeat(Boss.ENDER_DRAGON);
        if(result != EnderDragonHistory.Result.INCOMPLETE) {
            data.legacyEndChecked = true;
            data.markDirty();
        }
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        NBTTagCompound milestones = tag.getCompoundTag("Defeated");
        defeated.clear();
        for(Boss boss : Boss.values()) {
            if(milestones.getBoolean(boss.saveKey)) defeated.add(boss);
        }
        witherStormBypass = tag.getBoolean("WitherStormBypass");
        legacyEndChecked = tag.getBoolean("LegacyEndChecked");
    }

    @Override public void writeToNBT(NBTTagCompound tag) {
        NBTTagCompound milestones = new NBTTagCompound();
        for(Boss boss : defeated) milestones.setBoolean(boss.saveKey, true);
        tag.setTag("Defeated", milestones);
        tag.setBoolean("WitherStormBypass", witherStormBypass);
        tag.setBoolean("LegacyEndChecked", legacyEndChecked);
    }

    public static final class Events {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void dragonDied(LivingDeathEvent event) {
            World world = event.entityLiving.worldObj;
            if(!world.isRemote && world.provider.dimensionId == -1 && event.entityLiving instanceof EntityDragon
                && event.entityLiving.getHealth() <= 0F) {
                get(world).recordDefeat(Boss.ENDER_DRAGON);
            }
        }
    }
}
