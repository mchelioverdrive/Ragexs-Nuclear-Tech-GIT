package com.hbm.entity.mob.gemini;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;
import com.hbm.config.GeminiConfig;
import com.hbm.world.gemini.WretchDisturbances;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Authoritative encounter controller. Combat/capture timelines remain owned by EntityWretch. */
public final class WretchBrain {
    public static final int DORMANT = 0, AWAKENING = 1, WANDERING = 2, INVESTIGATING = 3,
            STALKING = 4, WATCHING = 5, APPROACHING = 6, HUNTING = 7, CHARGING = 8,
            AMBUSHING = 9, BREACHING = 10, GRABBING = 11, ASSIMILATING = 12,
            SEARCHING = 13, RECOVERING = 14, RETURNING = 15;
    private final EntityWretch boss;
    final WretchBuilding building;
    private final ArrayList<Memory> memories = new ArrayList<Memory>(8);
    private final double[] visitedX = new double[8], visitedZ = new double[8];
    private int visitedCount, visitedCursor, scanCursor;
    private int preyId = -1;
    private int state = DORMANT, clock, stateTicks, budget, quiet, stalking, nextCharge, nextDecision;
    private int cueRemaining, cueKind, reasonRemaining, aggression, failedBreaches, noBreachUntil;
    private double cueX, cueY, cueZ, goalX, goalY, goalZ;
    private float attention;
    private long lastCue;
    private UUID focus;
    private boolean running, restored, goalSet, observed;
    private static final class Memory {
        UUID id;
        double x, y, z, vx, vz, lookX, lookZ;
        int sight = -100000, heard = -100000;
    }
    WretchBrain(EntityWretch boss) { this.boss = boss; building = new WretchBuilding(boss); }
    int state() { return state; }
    boolean operational() { return running; }
    void pause() {
        if(!running) return;
        running = false; boss.navigation.halt(); boss.cancelCombat(); boss.setAttackTarget(null); boss.syncBehavior(DORMANT, 0);
    }
    void tick() {
        if(!running) {
            running = true;
            if(restored) { restored = false; boss.syncBehavior(state, stateTicks); }
            else if(state != DORMANT) transition(AWAKENING);
            else boss.syncBehavior(DORMANT, 0);
        }
        clock++; stateTicks++; quiet = Math.min(1000000, quiet + 1);
        if(cueRemaining > 0) cueRemaining--;
        if(reasonRemaining > 0) reasonRemaining--;
        attention = Math.max(0, attention - 0.015F);
        EntityLivingBase live = boss.getAttackTarget();
        if(live != null && !boss.validPrey(live)) boss.setAttackTarget(null);
        int interval = state == DORMANT ? 20 : 10;
        if(clock % interval == 0) { disturbances(); perceive(); prune(); }
        Memory target = selected();
        if(target != null) boss.getLookHelper().setLookPosition(target.x, target.y + 1.3, target.z, 12, 8);
        else if(cueRemaining > 0) boss.getLookHelper().setLookPosition(cueX, cueY + 1, cueZ, 8, 6);
        if(state == DORMANT) {
            boss.navigation.halt();
            if(attention >= GeminiConfig.wretchWakeThreshold) transition(AWAKENING);
            return;
        }
        if(state == AWAKENING) {
            boss.navigation.halt();
            if(stateTicks >= 60) transition(target != null && fresh(target, 20) ? aggression >= 75 ? HUNTING : STALKING : cueRemaining > 0 ? INVESTIGATING : WANDERING);
            return;
        }
        if(boss.state() != EntityWretch.IDLE) { combat(target); return; }
        if(clock % 20 == 0 && target != null && clock - target.heard < 100 && building.known(clock)
                && (state == WATCHING || state == APPROACHING || state == AMBUSHING)) aggression = Math.min(100, aggression + 1);
        if(state == GRABBING || state == ASSIMILATING || state == RECOVERING || state == BREACHING) {
            transition(target == null ? cueRemaining > 0 ? SEARCHING : RETURNING : aggression >= 55 ? HUNTING : STALKING);
        }
        if(quiet > GeminiConfig.wretchQuietSeconds * 20 && state != RETURNING) transition(RETURNING);
        if(target == null && focus != null) { focus = null; if(state != RETURNING && state != WANDERING) transition(SEARCHING); }
        switch(state) {
        case WANDERING:
            if(target != null) { transition(STALKING); break; }
            if(cueRemaining > 0) { transition(INVESTIGATING); break; }
            if((!goalSet || boss.navigation.arrived(2) || stateTicks > budget) && clock >= nextDecision) {
                patrol(); stateTicks = 0; budget = between(140, 320); nextDecision = clock + 40;
            }
            break;
        case INVESTIGATING:
            if(target != null && fresh(target, 20)) { transition(aggression >= 75 ? HUNTING : STALKING); break; }
            if(cueRemaining == 0 || stateTicks > GeminiConfig.wretchSearchSeconds * 20) { transition(RETURNING); break; }
            if(!goalSet) move(cueX, cueY, cueZ, 0.09);
            building.observe(cueX, cueY, cueZ, clock);
            if(boss.navigation.arrived(4)) transition(building.known(clock) ? WATCHING : SEARCHING);
            else if(boss.navigation.failures >= 3) { if(!breach(cueX, cueY, cueZ)) transition(SEARCHING); }
            break;
        case STALKING:
            if(target == null) { transition(SEARCHING); break; }
            if(!fresh(target, 30)) { transition(building.known(clock) ? APPROACHING : SEARCHING); break; }
            stalking++;
            if(stalking >= GeminiConfig.wretchStalkMinSeconds * 20 && (aggression >= 60 || stalking >= budget)) { transition(HUNTING); break; }
            if(observed && stateTicks > 25 && clock >= nextDecision) {
                nextDecision = clock + between(100, 220); aggression = Math.min(100, aggression + 5);
                transition(boss.getRNG().nextInt(3) == 0 ? APPROACHING : WATCHING); break;
            }
            if(!goalSet || boss.navigation.arrived(2) || boss.navigation.failures >= 3 || stateTicks > 180) {
                Vec3 point = tactical(target, 19, 31, false);
                if(point != null) move(point.xCoord, point.yCoord, point.zCoord, GeminiConfig.wretchStalkSpeed);
                else transition(WATCHING);
                stateTicks = 0;
            }
            if(!observed && aggression > 35 && clock >= nextDecision && boss.getRNG().nextInt(5) == 0) { nextDecision = clock + 180; transition(AMBUSHING); }
            break;
        case WATCHING:
            boss.navigation.halt();
            if(budget - stateTicks >= 92) building.gesture(clock);
            if(target != null && !fresh(target, 20) && aggression >= 75 && building.known(clock) && breach(target.x, target.y, target.z)) break;
            if(target != null && fresh(target, 20)) stalking++;
            if(aggression >= 80 && target != null && fresh(target, 20) && stateTicks > 30) { transition(HUNTING); break; }
            if(stateTicks >= budget) transition(target == null ? cueRemaining > 0 ? SEARCHING : RETURNING
                    : building.known(clock) ? APPROACHING : !fresh(target, 20) ? SEARCHING : boss.getRNG().nextInt(3) == 0 ? APPROACHING : STALKING);
            break;
        case APPROACHING:
            if(target == null && cueRemaining == 0) { transition(RETURNING); break; }
            if(!goalSet) {
                Vec3 point = building.approach(clock);
                if(point == null && target != null) point = tactical(target, 9, 16, false);
                if(point == null) { transition(SEARCHING); break; }
                move(point.xCoord, point.yCoord, point.zCoord, 0.11);
            }
            if(boss.navigation.arrived(2)) transition(WATCHING);
            else if(boss.navigation.failures >= 3 || stateTicks > 240) transition(AMBUSHING);
            if(target != null && fresh(target, 20) && aggression >= 75) transition(HUNTING);
            break;
        case HUNTING:
            if(target == null || !fresh(target, 30)) { transition(SEARCHING); break; }
            if(boss.tryCombat()) break;
            move(target.x + target.vx * 10, target.y, target.z + target.vz * 10, GeminiConfig.wretchHuntSpeed);
            double distance = horizontal(target.x - boss.posX, target.z - boss.posZ);
            if(clock >= nextCharge && distance > 8 && distance < 38 && boss.getRNG().nextInt(30) == 0) transition(CHARGING);
            else if(boss.navigation.failures >= 2 && !breach(target.x, target.y, target.z) && stateTicks > 160) transition(AMBUSHING);
            break;
        case CHARGING:
            if(!goalSet && target != null) {
                double dx = target.x - boss.posX, dz = target.z - boss.posZ, len = Math.max(1, horizontal(dx, dz));
                move(target.x + target.vx * 12 + dx / len * 4, target.y, target.z + target.vz * 12 + dz / len * 4, GeminiConfig.wretchChargeSpeed);
            }
            boss.tryCombat(); // A swipe can continue moving; slams and captures anchor their own frames.
            if(boss.navigation.failures >= 2) { if(target == null || !breach(target.x, target.y, target.z)) transition(AMBUSHING); }
            else if(stateTicks >= budget || boss.navigation.arrived(1.5)) transition(target != null && fresh(target, 20) ? HUNTING : SEARCHING);
            break;
        case AMBUSHING:
            if(target == null) { transition(SEARCHING); break; }
            if(!goalSet) {
                Vec3 point = building.approach(clock);
                if(point == null) point = tactical(target, 11, 21, true);
                if(point == null) { transition(WATCHING); break; }
                move(point.xCoord, point.yCoord, point.zCoord, 0.075);
            }
            if(boss.navigation.arrived(2)) {
                boss.navigation.halt(); if(budget - stateTicks >= 92) building.gesture(clock);
                if(fresh(target, 20) && horizontal(target.x - boss.posX, target.z - boss.posZ) < 14 && (!observed || aggression >= 80)) transition(HUNTING);
            }
            if(stateTicks > budget || boss.navigation.failures >= 4) transition(fresh(target, 20) ? STALKING : SEARCHING);
            break;
        case SEARCHING:
            if(target != null && fresh(target, 20)) { transition(aggression >= 60 ? HUNTING : STALKING); break; }
            if(stateTicks >= GeminiConfig.wretchSearchSeconds * 20) { cueRemaining = 0; focus = null; transition(RETURNING); break; }
            if(!goalSet || boss.navigation.arrived(2) || boss.navigation.failures >= 3) {
                double x = target == null ? cueX : target.x + target.vx * 20;
                double z = target == null ? cueZ : target.z + target.vz * 20;
                Vec3 point = building.approach(clock);
                if(point == null) point = nearby(x, target == null ? cueY : target.y, z, stateTicks < 80 ? 3 : 10);
                if(point != null) move(point.xCoord, point.yCoord, point.zCoord, 0.085);
                else if(!breach(x, target == null ? cueY : target.y, z)) transition(RETURNING);
            }
            break;
        case RETURNING:
            if(quiet < 100 && target != null && fresh(target, 20)) { transition(STALKING); break; }
            if(quiet < 100 && cueRemaining > 0) { transition(INVESTIGATING); break; }
            if(horizontal(boss.posX - boss.campX() - 0.5, boss.posZ - boss.campZ() - 0.5) < 7 || stateTicks > 600 && quiet >= GeminiConfig.wretchQuietSeconds * 20) {
                if(quiet >= GeminiConfig.wretchQuietSeconds * 20) { focus = null; memories.clear(); attention = 0; transition(DORMANT); }
                else transition(WANDERING);
            } else if((!goalSet || boss.navigation.failures >= 3) && clock >= nextDecision) {
                nextDecision = clock + 40;
                Vec3 point = nearby(boss.campX() + 0.5, boss.campY(), boss.campZ() + 0.5, 5);
                if(point != null) move(point.xCoord, point.yCoord, point.zCoord, 0.12);
                else boss.navigation.halt();
            }
            break;
        default: transition(RETURNING);
        }
    }
    private void combat(Memory target) {
        int attack = boss.state();
        int visibleState = attack == EntityWretch.GRAB || attack == EntityWretch.HOLD || attack == EntityWretch.THROW || attack == EntityWretch.PULL ? GRABBING
                : attack == EntityWretch.ASSIMILATE ? ASSIMILATING : attack == EntityWretch.FEED ? RECOVERING : attack == EntityWretch.RECOVER ? RECOVERING
                : attack == EntityWretch.BREACH ? BREACHING : HUNTING;
        if(state != visibleState) transition(visibleState, attack == EntityWretch.SWIPE || attack == EntityWretch.GRAB && boss.combatTicks() < 20);
        if(target != null && attack == EntityWretch.SWIPE && boss.combatTicks() < 32)
            move(target.x, target.y, target.z, GeminiConfig.wretchHuntSpeed * 0.4);
        else if(target != null && attack == EntityWretch.GRAB && boss.combatTicks() < 20)
            move(target.x, target.y, target.z, GeminiConfig.wretchStalkSpeed);
        else boss.navigation.halt();
    }
    private void transition(int next) { transition(next, false); }
    private void transition(int next, boolean keepMoving) {
        state = next; stateTicks = 0; goalSet = false; if(!keepMoving) boss.navigation.halt(); boss.navigation.failures = 0;
        nextDecision = clock + 20;
        budget = next == WATCHING ? between(90, 240) : next == CHARGING ? between(32, 52) : next == AMBUSHING ? between(220, 420)
                : between(GeminiConfig.wretchStalkMinSeconds * 20, GeminiConfig.wretchStalkMaxSeconds * 20);
        if(next == CHARGING) { nextCharge = clock + between(180, 360); boss.voice("charge", 3F); }
        boss.syncBehavior(next, 0);
        if(next != WATCHING && next != AMBUSHING) boss.clearPresentation();
    }
    private void disturbances() {
        WretchDisturbances.Cue[] cues = WretchDisturbances.recent(boss.worldObj); if(cues == null) return;
        long newest = lastCue; float strongest = -1, total = 0;
        WretchDisturbances.Cue chosen = null;
        long worldTime = boss.worldObj.getTotalWorldTime();
        for(WretchDisturbances.Cue cue : cues) {
            if(cue == null || cue.serial <= lastCue) continue;
            newest = Math.max(newest, cue.serial);
            if(worldTime < cue.time || worldTime - cue.time > 200 || !boss.inTerritory(cue.x, cue.z, boss.territoryRadius())) continue;
            double range = Math.min(GeminiConfig.wretchHearingRange, cue.kind == WretchDisturbances.MOVEMENT ? 32 : cue.kind == WretchDisturbances.WORK ? 72 : cue.kind == WretchDisturbances.COMBAT ? 96 : 192);
            double distance = horizontal(cue.x - boss.posX, cue.z - boss.posZ);
            if(distance > range || Math.abs(cue.y - boss.posY) > 40) continue;
            float strength = (float) (cue.strength * (1 - distance / (range + 1)));
            total += strength;
            if(strength > strongest) { chosen = cue; strongest = strength; }
        }
        lastCue = newest;
        if(chosen == null) return;
        attention = Math.min(100, attention + Math.min(24, total)); quiet = 0;
        if(selected() == null || !fresh(selected(), 30)) {
            if(state == SEARCHING && horizontal(chosen.x - cueX, chosen.z - cueZ) > 3) stateTicks = 0;
            cueX = chosen.x; cueY = chosen.y; cueZ = chosen.z; cueKind = chosen.kind;
            cueRemaining = GeminiConfig.wretchSearchSeconds * 20; goalSet = false;
        }
        if(chosen.kind >= WretchDisturbances.COMBAT) {
            reasonRemaining = 400; aggression = Math.min(100, aggression + (chosen.kind == WretchDisturbances.EXPLOSION ? 20 : 10));
        }
        if(chosen.kind != WretchDisturbances.MOVEMENT) building.observe(chosen.x, chosen.y, chosen.z, clock);
        if(chosen.player != null) { Memory m = memory(chosen.player); m.heard = clock; if(m.sight < clock - 30) { m.x = chosen.x; m.y = chosen.y; m.z = chosen.z; } }
    }
    private void perceive() {
        EntityLivingBase best = null; Memory bestMemory = null; double score = -Double.MAX_VALUE; observed = false;
        int count = boss.worldObj.playerEntities.size(), limit = Math.min(32, count), visibleCount = 0;
        for(int i = 0; i < limit; i++) {
            EntityPlayer p = (EntityPlayer) boss.worldObj.playerEntities.get((scanCursor + i) % count);
            if(!boss.validPlayer(p) || !boss.sight(p, GeminiConfig.wretchDetectionRange)) continue;
            visibleCount++;
            Memory m = memory(p.getUniqueID());
            int elapsed = Math.max(1, clock - m.sight);
            m.vx = elapsed < 100 ? MathHelper.clamp_double((p.posX - m.x) / elapsed, -0.4, 0.4) : 0;
            m.vz = elapsed < 100 ? MathHelper.clamp_double((p.posZ - m.z) / elapsed, -0.4, 0.4) : 0;
            m.x = p.posX; m.y = p.boundingBox.minY; m.z = p.posZ; m.sight = clock;
            Vec3 look = p.getLookVec(); m.lookX = look.xCoord; m.lookZ = look.zCoord;
            double value = 100 - horizontal(m.x - boss.posX, m.z - boss.posZ) + (m.id.equals(focus) ? 30 : 0);
            if(value > score) { score = value; best = p; bestMemory = m; }
        }
        if(count > 0) scanCursor = (scanCursor + limit) % count;
        // Player stalking remains primary. In their absence, remember visible villagers and explicitly
        // compatible humanoids; do not scan the world's loaded entity list or retain unloaded objects.
        if(best == null && state != DORMANT && state != AWAKENING && com.hbm.config.GeminiConfig.wretchAssimilation) {
            net.minecraft.entity.Entity cached = boss.worldObj.getEntityByID(preyId);
            if(cached instanceof EntityLivingBase && !(cached instanceof EntityPlayer) && boss.validPrey((EntityLivingBase) cached) && boss.sight((EntityLivingBase) cached, 40)) best = (EntityLivingBase) cached;
            if(clock % 40 == 0) {
                int examined = 0; double nearest = best == null ? Double.MAX_VALUE : boss.getDistanceSqToEntity(best);
                AxisAlignedBB area = AxisAlignedBB.getBoundingBox(boss.posX - 40, boss.posY - 4, boss.posZ - 40, boss.posX + 40, boss.posY + 24, boss.posZ + 40);
                for(Object entry : boss.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, area)) {
                    if(++examined > 32) break;
                    EntityLivingBase candidate = (EntityLivingBase) entry;
                    if(candidate instanceof EntityPlayer || !boss.validPrey(candidate) || !boss.sight(candidate, 40)) continue;
                    double distance = boss.getDistanceSqToEntity(candidate);
                    if(best != null && best.getClass() == net.minecraft.entity.passive.EntityVillager.class && candidate.getClass() != net.minecraft.entity.passive.EntityVillager.class) continue;
                    if(distance < nearest || candidate.getClass() == net.minecraft.entity.passive.EntityVillager.class && best != null && best.getClass() != net.minecraft.entity.passive.EntityVillager.class) { best = candidate; nearest = distance; }
                }
            }
            if(best != null) {
                preyId = best.getEntityId(); bestMemory = memory(best.getUniqueID());
                bestMemory.x = best.posX; bestMemory.y = best.boundingBox.minY; bestMemory.z = best.posZ; bestMemory.sight = clock;
                bestMemory.lookX = best.getLookVec().xCoord; bestMemory.lookZ = best.getLookVec().zCoord;
            } else preyId = -1;
        }
        boss.setAttackTarget(best);
        if(best != null) {
            focus = bestMemory.id; quiet = 0; attention = Math.min(100, attention + (state == DORMANT ? 1.2F : 0.5F) * Math.min(4, visibleCount));
            aggression = Math.min(100, aggression + (state == DORMANT || state == AWAKENING ? 0 : 1));
            Vec3 head = boss.headPoint(1), eye = Vec3.createVectorHelper(best.posX, best.boundingBox.minY + best.getEyeHeight(), best.posZ), look = best.getLookVec();
            double dx = head.xCoord - eye.xCoord, dy = head.yCoord - eye.yCoord, dz = head.zCoord - eye.zCoord;
            double length = Math.max(0.001, Math.sqrt(dx * dx + dy * dy + dz * dz));
            observed = best instanceof EntityPlayer && (dx * look.xCoord + dy * look.yCoord + dz * look.zCoord) / length > 0.88;
            building.observe(bestMemory.x, bestMemory.y, bestMemory.z, clock);
            cueX = bestMemory.x; cueY = bestMemory.y; cueZ = bestMemory.z; cueRemaining = GeminiConfig.wretchSearchSeconds * 20;
        }
    }
    private Memory memory(UUID id) {
        for(Memory m : memories) if(m.id.equals(id)) return m;
        if(memories.size() == 8) {
            Memory oldest = memories.get(0);
            for(Memory m : memories) if(Math.max(m.sight, m.heard) < Math.max(oldest.sight, oldest.heard)) oldest = m;
            memories.remove(oldest);
        }
        Memory m = new Memory(); m.id = id; memories.add(m); return m;
    }
    private Memory selected() {
        for(Memory m : memories) if(m.id.equals(focus)) return m;
        return null;
    }
    private boolean fresh(Memory m, int ticks) { return clock - m.sight <= ticks; }
    private void prune() {
        for(Iterator<Memory> it = memories.iterator(); it.hasNext();) {
            Memory m = it.next();
            if(clock - Math.max(m.sight, m.heard) > GeminiConfig.wretchMemorySeconds * 20) it.remove();
        }
    }
    private Vec3 tactical(Memory m, int near, int far, boolean ahead) {
        Vec3 best = null; double bestScore = -Double.MAX_VALUE;
        for(int i = 0; i < 7; i++) {
            double angle = boss.getRNG().nextDouble() * Math.PI * 2, radius = between(near, far);
            double x = m.x + Math.cos(angle) * radius + (ahead ? m.vx * 28 : 0), z = m.z + Math.sin(angle) * radius + (ahead ? m.vz * 28 : 0);
            double y = boss.navigation.surface(x, boss.posY, z);
            if(!candidate(x, y, z)) continue;
            Vec3 chest = Vec3.createVectorHelper(x, y + 10, z), eye = Vec3.createVectorHelper(m.x, m.y + 1.6, m.z);
            if(!lineLoaded(boss.worldObj, chest, eye)) continue;
            boolean concealed = boss.worldObj.func_147447_a(chest, eye, false, true, false) != null;
            double dot = ((x - m.x) * m.lookX + (z - m.z) * m.lookZ) / Math.max(1, radius);
            double score = (concealed ? 30 : 0) - dot * (ahead ? -12 : 12) - horizontal(x - boss.posX, z - boss.posZ) * 0.4;
            if(score > bestScore) { bestScore = score; best = Vec3.createVectorHelper(x, y, z); }
        }
        return best;
    }
    private Vec3 nearby(double x, double y, double z, int radius) {
        for(int i = 0; i < 7; i++) {
            double angle = boss.getRNG().nextDouble() * Math.PI * 2, range = boss.getRNG().nextDouble() * radius;
            double px = x + Math.cos(angle) * range, pz = z + Math.sin(angle) * range;
            // Local ground only; do not look down through a distant unloaded destination column.
            double py = boss.navigation.surface(px, MathHelper.clamp_double(y, boss.posY - 2, boss.posY + 2), pz);
            if(candidate(px, py, pz)) return Vec3.createVectorHelper(px, py, pz);
        }
        return null;
    }
    private boolean candidate(double x, double y, double z) {
        if(!Double.isFinite(y) || !boss.inTerritory(x, z, boss.territoryRadius() - 3) || horizontal(x - boss.posX, z - boss.posZ) < 3) return false;
        for(int i = 0; i < visitedCount; i++) if(horizontal(x - visitedX[i], z - visitedZ[i]) < 3) return false;
        return boss.navigation.canStand(x, y, z);
    }
    private void patrol() {
        double angle = boss.getRNG().nextDouble() * Math.PI * 2, radius = boss.getRNG().nextDouble() * boss.territoryRadius() * 0.6;
        Vec3 p = nearby(boss.campX() + 0.5 + Math.cos(angle) * radius, boss.campY(), boss.campZ() + 0.5 + Math.sin(angle) * radius, 5);
        if(p != null) move(p.xCoord, p.yCoord, p.zCoord, 0.055);
    }
    private void move(double x, double y, double z, double speed) {
        if(!boss.inTerritory(x, z, boss.territoryRadius() - 2)) return;
        if(!goalSet || horizontal(goalX - x, goalZ - z) > 3) {
            visitedX[visitedCursor] = x; visitedZ[visitedCursor] = z; visitedCursor = (visitedCursor + 1) % visitedX.length;
            visitedCount = Math.min(visitedX.length, visitedCount + 1);
        }
        goalX = x; goalY = y; goalZ = z; goalSet = true; boss.navigation.goal(x, y, z, speed);
    }
    private boolean breach(double x, double y, double z) {
        if(!destructiveIntent() || clock < noBreachUntil || boss.state() != EntityWretch.IDLE) return false;
        Vec3 point = building.obstruction(x, y, z);
        if(point == null) return false;
        if(++failedBreaches > 3) { noBreachUntil = clock + 600; failedBreaches = 0; return false; }
        boss.structuralAttack(point); return true;
    }
    void structuralProgress() { failedBreaches = 0; }
    Vec3 attackPoint() {
        Memory m = selected();
        return m != null && fresh(m, 200) && aggression >= 60 ? Vec3.createVectorHelper(m.x, m.y + 0.9, m.z) : null;
    }
    boolean destructiveIntent() {
        Memory m = selected();
        return running && state != DORMANT && state != AWAKENING && aggression >= 55
                && (m != null && (fresh(m, 400) || fresh(m, 900) && clock - m.heard < 200) || reasonRemaining > 0 && cueRemaining > 0);
    }
    void injured(net.minecraft.entity.Entity source) {
        quiet = 0; attention = GeminiConfig.wretchWakeThreshold + 1; aggression = 90; reasonRemaining = 600;
        if(source != null && source.worldObj == boss.worldObj) {
            cueX = source.posX; cueY = source.posY; cueZ = source.posZ; cueKind = WretchDisturbances.COMBAT;
            cueRemaining = GeminiConfig.wretchSearchSeconds * 20;
        }
        if(state == DORMANT) transition(AWAKENING);
    }
    private int between(int min, int max) { return min + boss.getRNG().nextInt(Math.max(1, max - min + 1)); }
    private static double horizontal(double x, double z) { return Math.sqrt(x * x + z * z); }
    public static boolean lineLoaded(World world, Vec3 from, Vec3 to) {
        double distance = Math.max(Math.abs(to.xCoord - from.xCoord), Math.abs(to.zCoord - from.zCoord));
        int steps = Math.max(1, (int) Math.ceil(distance / 8));
        if(steps > 64 || from.yCoord < 0 || to.yCoord < 0 || from.yCoord > 255 || to.yCoord > 255) return false;
        for(int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = MathHelper.floor_double(from.xCoord + (to.xCoord - from.xCoord) * t), z = MathHelper.floor_double(from.zCoord + (to.zCoord - from.zCoord) * t);
            if(!world.getChunkProvider().chunkExists(x >> 4, z >> 4)) return false;
        }
        return true;
    }
    void write(NBTTagCompound root) {
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("Version", 1); tag.setInteger("State", state); tag.setInteger("Elapsed", stateTicks);
        tag.setInteger("Budget", budget); tag.setInteger("Quiet", quiet); tag.setInteger("Stalking", stalking); tag.setInteger("Aggression", aggression);
        tag.setFloat("Attention", attention); tag.setInteger("ChargeDelay", Math.max(0, nextCharge - clock)); tag.setInteger("CueRemaining", cueRemaining);
        tag.setDouble("CueX", cueX); tag.setDouble("CueY", cueY); tag.setDouble("CueZ", cueZ); tag.setInteger("CueKind", cueKind); tag.setInteger("ReasonRemaining", reasonRemaining);
        if(focus != null) tag.setString("Focus", focus.toString());
        NBTTagList list = new NBTTagList();
        for(Memory m : memories) {
            NBTTagCompound entry = new NBTTagCompound(); entry.setString("Id", m.id.toString());
            entry.setDouble("X", m.x); entry.setDouble("Y", m.y); entry.setDouble("Z", m.z); entry.setDouble("VX", m.vx); entry.setDouble("VZ", m.vz);
            entry.setDouble("LookX", m.lookX); entry.setDouble("LookZ", m.lookZ); entry.setInteger("SightAge", clock - m.sight); entry.setInteger("HeardAge", clock - m.heard); list.appendTag(entry);
        }
        tag.setTag("Targets", list); building.write(tag, clock); root.setTag("WretchBrain", tag);
    }
    void read(NBTTagCompound root) {
        clock = 0; memories.clear(); focus = null; running = false; restored = true;
        if(!root.hasKey("WretchBrain")) { state = DORMANT; return; }
        NBTTagCompound tag = root.getCompoundTag("WretchBrain");
        state = MathHelper.clamp_int(tag.getInteger("State"), DORMANT, RETURNING);
        stateTicks = MathHelper.clamp_int(tag.getInteger("Elapsed"), 0, 72000); budget = MathHelper.clamp_int(tag.getInteger("Budget"), 60, 12000);
        quiet = MathHelper.clamp_int(tag.getInteger("Quiet"), 0, 1000000); stalking = MathHelper.clamp_int(tag.getInteger("Stalking"), 0, 12000);
        aggression = MathHelper.clamp_int(tag.getInteger("Aggression"), 0, 100); attention = tag.getFloat("Attention");
        if(Float.isNaN(attention) || Float.isInfinite(attention)) attention = 0; attention = MathHelper.clamp_float(attention, 0, 100);
        nextCharge = MathHelper.clamp_int(tag.getInteger("ChargeDelay"), 0, 600); cueRemaining = MathHelper.clamp_int(tag.getInteger("CueRemaining"), 0, GeminiConfig.wretchSearchSeconds * 20);
        cueX = tag.getDouble("CueX"); cueY = tag.getDouble("CueY"); cueZ = tag.getDouble("CueZ"); cueKind = MathHelper.clamp_int(tag.getInteger("CueKind"), 0, 3);
        reasonRemaining = MathHelper.clamp_int(tag.getInteger("ReasonRemaining"), 0, 600);
        if(!Double.isFinite(cueX + cueY + cueZ) || !boss.inTerritory(cueX, cueZ, boss.territoryRadius())) { cueRemaining = reasonRemaining = 0; }
        try { focus = UUID.fromString(tag.getString("Focus")); } catch(IllegalArgumentException ignored) { }
        NBTTagList list = tag.getTagList("Targets", 10);
        for(int i = 0; i < Math.min(8, list.tagCount()); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i); Memory m = new Memory();
            try { m.id = UUID.fromString(entry.getString("Id")); } catch(IllegalArgumentException ignored) { continue; }
            m.x = entry.getDouble("X"); m.y = entry.getDouble("Y"); m.z = entry.getDouble("Z");
            if(!Double.isFinite(m.x + m.y + m.z) || !boss.inTerritory(m.x, m.z, boss.territoryRadius())) continue;
            m.vx = finiteVelocity(entry.getDouble("VX")); m.vz = finiteVelocity(entry.getDouble("VZ"));
            m.lookX = finiteUnit(entry.getDouble("LookX")); m.lookZ = finiteUnit(entry.getDouble("LookZ"));
            // A saved UUID/position is a memory, never a currently visible entity after loading.
            m.sight = -Math.max(31, MathHelper.clamp_int(entry.getInteger("SightAge"), 0, 12001));
            m.heard = -MathHelper.clamp_int(entry.getInteger("HeardAge"), 0, 12001); memories.add(m);
        }
        building.read(tag);
        if(state == GRABBING || state == ASSIMILATING || state == BREACHING || state == RECOVERING || state == CHARGING || state == HUNTING) { state = SEARCHING; stateTicks = 0; }
        boss.setAttackTarget(null); goalSet = false;
    }
    private static double finiteVelocity(double value) { return Double.isFinite(value) ? MathHelper.clamp_double(value, -0.4, 0.4) : 0; }
    private static double finiteUnit(double value) { return Double.isFinite(value) ? MathHelper.clamp_double(value, -1, 1) : 0; }
}
