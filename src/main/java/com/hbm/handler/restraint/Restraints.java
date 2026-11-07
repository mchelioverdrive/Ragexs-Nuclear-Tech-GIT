package com.hbm.handler.restraint;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.WeakHashMap;
import com.hbm.config.RestraintConfig;
import com.hbm.items.ModItems;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.RestraintPacket;
import com.hbm.tileentity.machine.TileEntityRestraintDevice;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityEvent.EntityConstructing;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

/** All mutations and damage run on the owning server tick or native interaction callback. */
public final class Restraints {
    private static final UUID SPEED = UUID.fromString("14f1512a-bf1c-4dfe-909c-c77679e7e7c1");
    private final Map<World, Map<Integer, UUID>> anchors = new WeakHashMap<World, Map<Integer, UUID>>();
    public static final DamageSource INJECTION = new DamageSource("rntInjection").setDamageBypassesArmor();
    public static final DamageSource BLADE = new DamageSource("rntBlade").setDamageBypassesArmor();
    public static final DamageSource ELECTRIC = new DamageSource("rntElectric").setDamageBypassesArmor();
    public static final DamageSource HANGING = new DamageSource("rntHanging").setDamageBypassesArmor().setDamageIsAbsolute();

    public static boolean compatible(EntityLivingBase e) {
        if(!RestraintConfig.enabled || !e.isEntityAlive() || e instanceof IBossDisplayData
                || e.width < 0.35F || e.width > 0.95F || e.height < 1.3F || e.height > 2.5F) return false;
        String name = EntityList.getEntityString(e);
        if(RestraintConfig.excluded.contains(name)) return false;
        if(e instanceof EntityPlayer) return RestraintConfig.players;
        return e instanceof EntityVillager || e instanceof EntityZombie || e instanceof EntitySkeleton
                || RestraintConfig.humanoids.contains(name)
                || e instanceof EntityCreature && !(e instanceof EntityCreeper) && !(e instanceof EntityBlaze) && e.height / e.width >= 2.4F;
    }

    public static boolean sedate(EntityLivingBase e) {
        if(e.worldObj.isRemote || !compatible(e)) return false;
        RestraintState s = RestraintState.get(e);
        if(s.onset > 0 || s.asleep > 0 || s.recovery > 0) return false;
        s.onset = RestraintConfig.onsetTicks;
        s.asleep = 0;
        changed(e);
        return true;
    }

    public static void changed(EntityLivingBase e) {
        if(e.worldObj.isRemote) return;
        RestraintState s = RestraintState.get(e);
        s.controlAI(e, RestraintConfig.enabled && s.bound());
        updateSpeed(e, s);
        ((net.minecraft.world.WorldServer) e.worldObj).getEntityTracker().func_151247_a(e, PacketDispatcher.wrapper.getPacketFrom(new RestraintPacket(e)));
        if(e instanceof EntityPlayerMP) PacketDispatcher.wrapper.sendTo(new RestraintPacket(e), (EntityPlayerMP) e);
    }

    private static void updateSpeed(EntityLivingBase e, RestraintState s) {
        IAttributeInstance attribute = e.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        if(attribute == null) return;
        // Stop voluntary travel without cancelling collision impulses, gravity or native forced movement.
        double amount = !RestraintConfig.enabled ? 0 : s.bound() ? -1 : s.onset > 0 ? -0.75 : 0;
        AttributeModifier previous = attribute.getModifier(SPEED);
        if(previous != null && previous.getAmount() == amount) return;
        if(previous != null) attribute.removeModifier(previous);
        if(amount != 0) attribute.applyModifier(new AttributeModifier(SPEED, "RNT restraint slowing", amount, 2).setSaved(false));
    }

    @SubscribeEvent public void construct(EntityConstructing event) {
        if(event.entity instanceof EntityLivingBase) RestraintState.get((EntityLivingBase) event.entity);
    }

    @SubscribeEvent public void tracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event) {
        if(event.target instanceof EntityLivingBase && event.entityPlayer instanceof EntityPlayerMP)
            PacketDispatcher.wrapper.sendTo(new RestraintPacket((EntityLivingBase) event.target), (EntityPlayerMP) event.entityPlayer);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void tick(LivingUpdateEvent event) {
        EntityLivingBase e = event.entityLiving;
        if(e.worldObj.isRemote) return;
        RestraintState s = RestraintState.get(e);
        if(!s.active()) return;
        if(!RestraintConfig.enabled || e instanceof EntityPlayer && !RestraintConfig.players) {
            if(s.cuffs) e.entityDropItem(new ItemStack(ModItems.handcuffs), 0);
            if(s.jacket) e.entityDropItem(new ItemStack(ModItems.straight_jacket), 0);
            clear(e); return;
        }
        boolean sync = e.ticksExisted % 20 == 0;
        if(s.recovery > 0) --s.recovery;
        if(s.onset > 0 && --s.onset == 0) {
            s.asleep = RestraintConfig.sedationTicks;
            s.anchored = false; sync = true;
        } else if(s.asleep > 0 && --s.asleep == 0) {
            s.recovery = RestraintConfig.recoveryTicks;
            s.anchored = false; sync = true;
        }
        if(s.terminal > 0) {
            --s.terminal; ++s.terminalElapsed;
            if(s.terminalElapsed % 20 == 0 || s.terminal == 0) {
                float damage = RestraintConfig.injectionDamage * (1F + s.terminalElapsed / 40F);
                if(s.terminal == 0 && damage > 0) damage = Math.max(damage, e.getMaxHealth() * 2);
                e.attackEntityFrom(INJECTION, damage);
            }
        }
        if(!e.isEntityAlive()) return;
        if(s.occupied()) {
            if(s.dimension != e.worldObj.provider.dimensionId) clearDevice(e);
            else if(e.worldObj.blockExists(s.x, s.y, s.z)) {
                TileEntity tile = e.worldObj.getTileEntity(s.x, s.y, s.z);
                if(!(tile instanceof TileEntityRestraintDevice) || !((TileEntityRestraintDevice) tile).owns(e)) clearDevice(e);
                else ((TileEntityRestraintDevice) tile).position(e);
            } else {
                // Never force-load the device chunk; hold at the last saved anchor until it returns.
                hold(e, s);
            }
        }
        s.controlAI(e, s.bound());
        updateSpeed(e, s);
        EntityPlayer leader = escort(e, s);
        if(s.escort != null && leader == null) { s.escort = null; sync = true; }
        if(!s.device) s.anchored = false;
        if(s.bound()) {
            e.setJumping(false); e.moveForward = e.moveStrafing = 0;
            if(e instanceof EntityLiving) ((EntityLiving) e).getNavigator().clearPathEntity();
        }
        if(s.device || leader != null) {
            Map<Integer, UUID> worldAnchors = anchors.get(e.worldObj);
            if(worldAnchors == null) { worldAnchors = new HashMap<Integer, UUID>(); anchors.put(e.worldObj, worldAnchors); }
            worldAnchors.put(e.getEntityId(), e.getUniqueID());
        }
        if(sync) changed(e);
    }

    private static EntityPlayer escort(EntityLivingBase e, RestraintState s) {
        EntityPlayer leader = s.escort == null || s.device || !s.bound() || e.ridingEntity != null || e.riddenByEntity != null
                ? null : e.worldObj.func_152378_a(s.escort);
        return leader != null && leader != e && leader.worldObj == e.worldObj && leader.isEntityAlive()
                && e.worldObj.playerEntities.contains(leader) && !blocked(leader)
                && leader.getDistanceSqToEntity(e) < 100 ? leader : null;
    }

    /** Apply device anchors or collision-aware dragging after native physics. */
    @SubscribeEvent public void endTick(TickEvent.WorldTickEvent event) {
        if(event.phase != TickEvent.Phase.END || event.world.isRemote) return;
        Map<Integer, UUID> entries = anchors.get(event.world);
        if(entries == null) return;
        for(Iterator<Map.Entry<Integer, UUID>> it = entries.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, UUID> entry = it.next();
            Entity entity = event.world.getEntityByID(entry.getKey());
            if(!(entity instanceof EntityLivingBase) || !entity.getUniqueID().equals(entry.getValue()) || !entity.isEntityAlive()) { it.remove(); continue; }
            EntityLivingBase e = (EntityLivingBase) entity;
            RestraintState s = RestraintState.get(e);
            if(!RestraintConfig.enabled) { it.remove(); continue; }
            if(s.device) {
                hold(e, s);
                e.motionX = e.motionY = e.motionZ = 0; e.fallDistance = 0;
            } else {
                EntityPlayer leader = escort(e, s);
                if(leader == null) { it.remove(); continue; }
                double dx = leader.posX - e.posX, dz = leader.posZ - e.posZ;
                double distance = Math.sqrt(dx * dx + dz * dz);
                if(distance > 1.6) {
                    double step = Math.min(0.32, (distance - 1.6) * 0.25);
                    boolean grounded = e.onGround;
                    // moveEntity preserves native stepping, walls, collision and gravity. Never teleport through obstructions.
                    e.moveEntity(dx / distance * step, 0, dz / distance * step);
                    if(grounded && e.isCollidedHorizontally && leader.posY > e.posY + 0.5) e.motionY = 0.42;
                    // A player's own client is excluded from ordinary tracker position packets.
                    if(e instanceof EntityPlayerMP && ((EntityPlayerMP) e).playerNetServerHandler != null)
                        e.setPositionAndUpdate(e.posX, e.posY, e.posZ);
                    e.velocityChanged = true;
                }
            }
        }
    }
    @SubscribeEvent public void unload(net.minecraftforge.event.world.WorldEvent.Unload event) { anchors.remove(event.world); }

    private static void releaseOwned(EntityPlayer player, World world) {
        if(world == null || world.isRemote) return;
        for(Object entry : world.loadedEntityList) if(entry instanceof EntityLivingBase) {
            EntityLivingBase e = (EntityLivingBase) entry;
            RestraintState s = RestraintState.get(e);
            if(player.getUniqueID().equals(s.escort)) { s.escort = null; s.anchored = false; changed(e); }
        }
    }
    @SubscribeEvent public void logout(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        releaseOwned(event.player, event.player.worldObj);
    }
    @SubscribeEvent public void dimension(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent event) {
        releaseOwned(event.player, net.minecraftforge.common.DimensionManager.getWorld(event.fromDim));
    }

    private static void hold(EntityLivingBase e, RestraintState s) {
        if(!s.anchored) { s.anchorX = e.posX; s.anchorY = e.posY; s.anchorZ = e.posZ; s.anchored = true; }
        e.setPosition(s.anchorX, s.anchorY, s.anchorZ);
    }

    public static boolean blocked(EntityLivingBase e) {
        if(!RestraintConfig.enabled || e instanceof EntityPlayer && !RestraintConfig.players) return false;
        return RestraintState.get(e).bound();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void attack(LivingAttackEvent event) {
        Entity source = event.source.getEntity();
        if(source instanceof EntityLivingBase && blocked((EntityLivingBase) source)) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void playerAttack(AttackEntityEvent event) {
        if(blocked(event.entityPlayer)) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void itemUse(PlayerUseItemEvent.Start event) {
        if(blocked(event.entityPlayer)) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void blockUse(PlayerInteractEvent event) {
        if(blocked(event.entityPlayer)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void interact(EntityInteractEvent event) {
        EntityPlayer player = event.entityPlayer;
        if(blocked(player)) { event.setCanceled(true); return; }
        if(!(event.target instanceof EntityLivingBase)) return;
        EntityLivingBase e = (EntityLivingBase) event.target;
        RestraintState s = RestraintState.get(e);
        ItemStack held = player.getHeldItem();
        boolean tool = held != null && (held.getItem() == ModItems.handcuffs || held.getItem() == ModItems.straight_jacket
                || held.getItem() == ModItems.tranquilizer_syringe || held.getItem() == Items.lead);
        if(!s.bound() && (held != null && held.getItem() == Items.lead || !compatible(e))) return;
        if(!tool && !s.bound()) return;
        event.setCanceled(true);
        if(player.worldObj.isRemote || player.getDistanceSqToEntity(e) > 36 || !compatible(e)) return;
        if(s.occupied()) { message(player, "restraint.in_device"); return; }
        EntityPlayer owner = escort(e, s);
        if(owner != null && owner != player) { message(player, "restraint.escort_busy"); return; }
        if((held == null || held.getItem() == Items.lead) && !player.isSneaking() && s.bound()) {
            s.escort = owner == player ? null : player.getUniqueID();
            s.anchored = false; changed(e); message(player, s.escort == null ? "restraint.escort_stop" : "restraint.escort");
            return;
        }
        if(held == null && player.isSneaking()) {
            if(s.jacket) { s.jacket = false; give(player, new ItemStack(ModItems.straight_jacket)); }
            else if(s.cuffs) { s.cuffs = false; s.escort = null; give(player, new ItemStack(ModItems.handcuffs)); }
            s.anchored = false; if(!s.bound()) s.escort = null;
            changed(e); return;
        }
        if(held == null) return;
        if(player.isSneaking()) return;
        if(held.getItem() == ModItems.handcuffs && !s.cuffs) {
            s.cuffs = true; s.anchored = false; consume(player, held); changed(e);
        } else if(held.getItem() == ModItems.straight_jacket && !s.jacket && (s.cuffs || s.asleep > 0)) {
            s.jacket = true; consume(player, held); changed(e);
        } else if(held.getItem() == ModItems.tranquilizer_syringe && sedate(e)) {
            consume(player, held); give(player, new ItemStack(ModItems.syringe_empty));
            e.worldObj.playSoundAtEntity(e, "random.fizz", 0.4F, 1.5F);
        }
    }

    public static void consume(EntityPlayer p, ItemStack stack) {
        // Restraints represent physical inventory even in creative; removal always returns exactly one.
        --stack.stackSize;
        if(stack.stackSize <= 0) p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
        p.inventory.markDirty(); p.inventoryContainer.detectAndSendChanges();
    }
    public static void give(EntityPlayer p, ItemStack stack) {
        // InventoryPlayer discards overflow in creative, so insert through RNT's inventory helper.
        ItemStack remainder = com.hbm.util.InventoryUtil.tryAddItemToInventory(p.inventory.mainInventory, stack);
        if(remainder != null) p.dropPlayerItemWithRandomChoice(remainder, false);
        p.inventory.markDirty(); p.inventoryContainer.detectAndSendChanges();
    }
    public static void message(EntityPlayer player, String key, Object... args) { player.addChatMessage(new ChatComponentTranslation(key, args)); }

    public static void clearDevice(EntityLivingBase e) {
        RestraintState s = RestraintState.get(e);
        s.device = false; s.deviceToken = null; s.anchored = false;
        e.motionX = e.motionY = e.motionZ = 0;
        changed(e);
    }
    private static void clear(EntityLivingBase e) {
        RestraintState s = RestraintState.get(e);
        s.device = false; s.deviceToken = null; s.escort = null;
        s.cuffs = s.jacket = s.anchored = false;
        s.onset = s.asleep = s.recovery = s.terminal = s.terminalElapsed = 0;
        changed(e);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void death(LivingDeathEvent event) {
        EntityLivingBase e = event.entityLiving;
        if(e.worldObj.isRemote || event.isCanceled()) return;
        if(e instanceof EntityPlayer) releaseOwned((EntityPlayer) e, e.worldObj);
        RestraintState s = RestraintState.get(e);
        if(s.occupied() && s.dimension == e.worldObj.provider.dimensionId && e.worldObj.blockExists(s.x, s.y, s.z)) {
            TileEntity tile = e.worldObj.getTileEntity(s.x, s.y, s.z);
            if(tile instanceof TileEntityRestraintDevice && ((TileEntityRestraintDevice) tile).owns(e)) ((TileEntityRestraintDevice) tile).forget();
        }
        if(s.cuffs) e.entityDropItem(new ItemStack(ModItems.handcuffs), 0);
        if(s.jacket) e.entityDropItem(new ItemStack(ModItems.straight_jacket), 0);
        clear(e);
    }
}
