package com.hbm.entity.mob.gemini;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/** Ambient queries dispatch to the actual nearby mob's override, including compatible mod creatures. */
final class WoodcrawlerMimicry {
    private static final Method AMBIENT = cpw.mods.fml.relauncher.ReflectionHelper.findMethod(EntityLiving.class,
            null, new String[] {"getLivingSound", "func_70639_aQ"});
    private static final Map<String, Set<String>> SOUNDS = new HashMap<String, Set<String>>();
    private final EntityWoodcrawler mob;
    private int next;
    WoodcrawlerMimicry(EntityWoodcrawler mob) { this.mob = mob; next = 120 + mob.getEntityId() % 80; }
    void tick() {
        if(mob.ticksExisted < next) return;
        next = mob.ticksExisted + 80;
        if(mob.isTransporting() || mob.isBurrowing() || mob.isGrabbing() || mob.isProbing()) return;
        EntityLivingBase prey = mob.getAttackTarget();
        boolean hidden = prey != null && !prey.canEntityBeSeen(mob);
        if(prey != null && !hidden && mob.getHuntState() == EntityWoodcrawler.CHASE) { next += 240; return; }
        String selected = null; int matches = 0, examined = 0;
        for(Object entry : mob.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, mob.boundingBox.expand(12, 6, 12))) {
            EntityLivingBase nearby = (EntityLivingBase) entry;
            if(++examined > 64) break;
            if(nearby == mob || nearby instanceof IGeminiCreature || !nearby.isEntityAlive() || mob.getDistanceSqToEntity(nearby) > 144) continue;
            String sound = null;
            if(nearby instanceof EntityLiving) {
                try { sound = (String) AMBIENT.invoke(nearby); }
                catch(ReflectiveOperationException | RuntimeException ignored) { continue; }
            } else if(nearby instanceof EntityPlayer && nearby.onGround && nearby.getDistanceSq(nearby.prevPosX, nearby.prevPosY, nearby.prevPosZ) > 0.0025) {
                net.minecraft.block.Block under = nearby.worldObj.getBlock(net.minecraft.util.MathHelper.floor_double(nearby.posX),
                        net.minecraft.util.MathHelper.floor_double(nearby.boundingBox.minY - 0.2), net.minecraft.util.MathHelper.floor_double(nearby.posZ));
                if(under.getMaterial().blocksMovement()) sound = under.stepSound.getStepResourcePath();
            }
            if(valid(sound) && mob.getRNG().nextInt(++matches) == 0) selected = sound;
        }
        if(selected != null) {
            mob.playSound(selected, hidden ? 0.55F : 0.4F, 0.92F + mob.getRNG().nextFloat() * 0.16F);
            next = mob.ticksExisted + (hidden ? 100 : 200) + mob.getRNG().nextInt(hidden ? 100 : 220);
        }
    }
    private static boolean valid(String sound) {
        if(sound == null || sound.isEmpty()) return false;
        net.minecraft.util.ResourceLocation id = new net.minecraft.util.ResourceLocation(sound);
        String domain = id.getResourceDomain();
        Set<String> known = SOUNDS.get(domain);
        if(known == null) {
            known = new HashSet<String>(); SOUNDS.put(domain, known);
            // Dedicated servers do not ship the client asset index. These are vanilla 1.7.10 events.
            if("minecraft".equals(domain)) java.util.Collections.addAll(known,
                    "mob.villager.idle", "mob.cow.say", "mob.sheep.say", "mob.pig.say", "mob.wolf.bark", "mob.wolf.pant",
                    "mob.wolf.whine", "mob.chicken.say", "mob.cat.meow", "mob.horse.idle", "mob.bat.idle", "mob.zombie.say",
                    "mob.skeleton.say", "mob.spider.say", "mob.endermen.idle", "step.stone", "step.grass", "step.gravel",
                    "step.wood", "step.sand", "step.snow", "step.ladder", "step.cloth");
            try(InputStream stream = WoodcrawlerMimicry.class.getClassLoader().getResourceAsStream("assets/" + domain + "/sounds.json")) {
                if(stream != null) for(Map.Entry<String, JsonElement> entry : new JsonParser().parse(new InputStreamReader(stream, "UTF-8")).getAsJsonObject().entrySet()) known.add(entry.getKey());
            } catch(Exception ignored) { /* Unknown sound domains are silent. */ }
        }
        return known.contains(id.getResourcePath());
    }
}
