package com.hbm.handler.restraint;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;

/** Owned by the original entity. Runtime AI references are never serialized or held in global maps. */
public final class RestraintState implements IExtendedEntityProperties {
    public static final String KEY = "hbmRestraint";
    public boolean cuffs, jacket, device;
    public UUID escort, deviceToken;
    public int dimension, x, y, z, onset, asleep, recovery, terminal, terminalElapsed;
    public double anchorX, anchorY, anchorZ;
    public boolean anchored;
    // Server-only confirmation belongs to this held stack/session, never to saved or synchronized state.
    public net.minecraft.item.ItemStack injectionConfirmStack;
    public long injectionConfirmUntil;
    public int injectionConfirmDimension;
    public boolean injectionConfirmReleased;
    private final List<EntityAITasks.EntityAITaskEntry> tasks = new ArrayList<EntityAITasks.EntityAITaskEntry>();
    private final List<EntityAITasks.EntityAITaskEntry> targets = new ArrayList<EntityAITasks.EntityAITaskEntry>();
    private int aiMode;

    public static RestraintState get(EntityLivingBase entity) {
        RestraintState state = (RestraintState) entity.getExtendedProperties(KEY);
        if(state == null) {
            state = new RestraintState();
            entity.registerExtendedProperties(KEY, state);
        }
        return state;
    }

    public boolean bound() { return cuffs || jacket || device || asleep > 0; }
    public boolean occupied() { return device && deviceToken != null; }
    public boolean active() { return bound() || onset > 0 || recovery > 0 || terminal > 0; }

    /** Suspend original tasks, including zero-mutex mod tasks, and restore the exact same instances. */
    public void controlAI(EntityLivingBase entity, boolean block) {
        if(!(entity instanceof EntityLiving)) return;
        EntityLiving mob = (EntityLiving) entity;
        int mode = block ? 2 : jacket && com.hbm.config.RestraintConfig.enabled ? 1 : 0;
        if(mode != aiMode) {
            restore(mob.tasks, tasks); restore(mob.targetTasks, targets);
            mob.getNavigator().clearPathEntity(); aiMode = mode;
        }
        if(mode > 0) {
            suspend(mob.tasks, tasks, mode == 2);
            suspend(mob.targetTasks, targets, true);
            mob.setAttackTarget(null);
            mob.setRevengeTarget(null);
        }
    }

    private static void suspend(EntityAITasks scheduler, List<EntityAITasks.EntityAITaskEntry> saved, boolean all) {
        for(Object entry : new ArrayList<Object>(scheduler.taskEntries)) {
            EntityAITasks.EntityAITaskEntry task = (EntityAITasks.EntityAITaskEntry) entry;
            String name = task.action.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
            if(!all && !(name.contains("attack") || name.contains("ranged") || name.contains("shoot") || name.contains("bow") || name.contains("weapon") || name.contains("gun"))) continue;
            if(!saved.contains(task)) saved.add(task);
            scheduler.removeTask(task.action);
        }
    }

    private static void restore(EntityAITasks scheduler, List<EntityAITasks.EntityAITaskEntry> saved) {
        for(EntityAITasks.EntityAITaskEntry task : saved) {
            boolean present = false;
            for(Object current : scheduler.taskEntries)
                if(((EntityAITasks.EntityAITaskEntry) current).action == task.action) present = true;
            if(!present) scheduler.addTask(task.priority, task.action);
        }
        saved.clear();
    }

    public NBTTagCompound write() {
        NBTTagCompound n = new NBTTagCompound();
        n.setBoolean("cuffs", cuffs); n.setBoolean("jacket", jacket); n.setBoolean("device", device);
        if(escort != null) n.setString("escort", escort.toString());
        if(deviceToken != null) n.setString("token", deviceToken.toString());
        n.setInteger("dimension", dimension); n.setInteger("x", x); n.setInteger("y", y); n.setInteger("z", z);
        n.setInteger("onset", onset); n.setInteger("asleep", asleep); n.setInteger("recovery", recovery);
        n.setInteger("terminal", terminal); n.setInteger("terminalElapsed", terminalElapsed);
        n.setBoolean("anchored", anchored);
        n.setDouble("anchorX", anchorX); n.setDouble("anchorY", anchorY); n.setDouble("anchorZ", anchorZ);
        return n;
    }

    public void read(NBTTagCompound n) {
        cuffs = n.getBoolean("cuffs"); jacket = n.getBoolean("jacket"); device = n.getBoolean("device");
        escort = uuid(n.getString("escort")); deviceToken = uuid(n.getString("token"));
        if(deviceToken == null) device = false;
        dimension = n.getInteger("dimension"); x = n.getInteger("x"); y = n.getInteger("y"); z = n.getInteger("z");
        onset = Math.max(0, n.getInteger("onset")); asleep = Math.max(0, n.getInteger("asleep")); recovery = Math.max(0, n.getInteger("recovery"));
        terminal = Math.max(0, n.getInteger("terminal")); terminalElapsed = Math.max(0, n.getInteger("terminalElapsed"));
        anchored = n.getBoolean("anchored");
        anchorX = n.getDouble("anchorX"); anchorY = n.getDouble("anchorY"); anchorZ = n.getDouble("anchorZ");
    }

    public static UUID uuid(String value) {
        try { return UUID.fromString(value); } catch(IllegalArgumentException ex) { return null; }
    }

    @Override public void saveNBTData(NBTTagCompound n) { if(active()) n.setTag(KEY, write()); else n.removeTag(KEY); }
    @Override public void loadNBTData(NBTTagCompound n) {
        read(n.getCompoundTag(KEY));
        // Grabs belong to a live session; retain escort in network snapshots, never across entity reloads.
        escort = null;
        if(!device) anchored = false;
    }
    @Override public void init(Entity entity, World world) { }
}
