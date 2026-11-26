package com.hbm.world.squirrelstapler;

import com.hbm.config.SquirrelStaplerConfig;
import com.hbm.entity.EntityMappings;
import com.hbm.entity.mob.sandworm.EntitySandworm;
import com.hbm.handler.guncfg.Gun762mmFactory;
import com.hbm.handler.restraint.Restraints;
import com.hbm.items.ModItems;
import com.hbm.items.weapon.ItemGunBase;
import com.hbm.lib.RefStrings;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.StaplerStatePacket;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;

public final class StaplerMode {
    public static Item rifle;
    static Item trophy;

    public static void registerItems() {
        rifle = new ItemGunBase(Gun762mmFactory.getBoltConfig()).setUnlocalizedName("gun_bolt_action").setTextureName(RefStrings.MODID + ":gun_bolt_action");
        trophy = new Item().setMaxStackSize(1).setUnlocalizedName("squirrel_stapler_trophy").setTextureName(RefStrings.MODID + ":squirreljak");
        GameRegistry.registerItem(rifle, rifle.getUnlocalizedName());
        GameRegistry.registerItem(trophy, trophy.getUnlocalizedName());
    }

    public static void register() {
        new StaplerWorldType();
        StaplerMode mode = new StaplerMode();
        GameRegistry.registerWorldGenerator(new StaplerForest(), 1000);
        MinecraftForge.EVENT_BUS.register(mode);
        FMLCommonHandler.instance().bus().register(mode);
    }

    @SubscribeEvent public void spawn(WorldEvent.CreateSpawnPosition event) {
        World world = event.world;
        if(world.isRemote || !StaplerWorldType.isForest(world)) return;
        int y = StaplerForest.ground(world, 8, 8);
        world.setSpawnLocation(8, y > 0 ? y : 66, 8);
        event.setCanceled(true);
    }

    @SubscribeEvent public void tick(TickEvent.WorldTickEvent event) {
        World world = event.world;
        if(world.isRemote || !StaplerWorldType.isForest(world)) return;
        StaplerCampaign data = StaplerCampaign.get(world);
        boolean enabled = SquirrelStaplerConfig.settings.enabled;
        if(event.phase == TickEvent.Phase.START) {
            if(enabled && data.stage == StaplerCampaign.HUNT && data.ready() && data.day < data.days) advanceIfSleeping(world, data);
            return;
        }
        boolean running = enabled && data.stage != StaplerCampaign.COMPLETE;
        if(!running) {
            boolean wasRunning = data.enabledLastTick || data.controlsDaylight;
            daylight(world, data, false);
            if(wasRunning) {
                data.encounters.clear();
                StaplerForest.lamp(world, data, false);
            }
        } else {
            daylight(world, data, true);
            if(!world.playerEntities.isEmpty()) {
                if(data.stage == StaplerCampaign.HUNT) {
                    if(data.ready() && data.day == data.days) {
                        beginFinale(world, data);
                    } else {
                        data.encounters.tickHunt(world, data);
                        if(!data.ready()) StaplerEncounters.replenishPrey(world, data);
                    }
                } else data.encounters.tickFinale(world, data);
            }
            if(world.getTotalWorldTime() % 10L == 0L || !data.enabledLastTick) {
                world.setWorldTime(skyTime(data));
                StaplerForest.lamp(world, data, data.encounters.cabinDark());
            }
        }
        data.enabledLastTick = running;
        sync(world, data);
    }

    private static long skyTime(StaplerCampaign data) {
        if(data.stage == StaplerCampaign.ARRIVAL) return 17800L;
        if(data.stage == StaplerCampaign.COUNTDOWN) return 13000L + 4800L * (data.countdownDuration - data.remainingTicks) / data.countdownDuration;
        if(data.ready()) return 13700L;
        return 2200L + 2050L * (Math.min(5, data.day) - 1);
    }

    private static void daylight(World world, StaplerCampaign data, boolean control) {
        if(data.controlsDaylight != control) {
            if(control) data.previousDaylight = world.getGameRules().getGameRuleBooleanValue("doDaylightCycle");
            else world.getGameRules().setOrCreateGameRule("doDaylightCycle", Boolean.toString(data.previousDaylight));
            data.controlsDaylight = control;
            data.markDirty();
        }
        if(control && world.getGameRules().getGameRuleBooleanValue("doDaylightCycle"))
            world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
    }

    private static void advanceIfSleeping(World world, StaplerCampaign data) {
        for(Object entry : world.playerEntities) {
            EntityPlayer player = (EntityPlayer)entry;
            ChunkCoordinates bed = player.playerLocation;
            if(!player.isPlayerFullyAsleep() || bed == null || !cabinBed(data, bed.posX, bed.posY, bed.posZ)) continue;
            setDay(world, data, data.day + 1);
            return;
        }
    }

    static void setDay(World world, StaplerCampaign data, int day) {
        data.startDay(day);
        refreshDay(world, data);
    }

    static void refreshDay(World world, StaplerCampaign data) {
        for(Object entry : world.playerEntities) {
            EntityPlayer player = (EntityPlayer)entry;
            if(player.isPlayerSleeping()) player.wakeUpPlayer(false, true, true);
            StaplerCampaign.tell(player, "Day " + data.day + ". The rack is ready for today's catch.");
        }
        world.setWorldTime(skyTime(data));
        StaplerForest.lamp(world, data, false);
        StaplerForest.updateDisplay(world, data);
        sync(world, data);
    }

    static boolean beginFinale(World world, StaplerCampaign data) {
        if(!data.startFinale()) return false;
        for(Object entry : world.playerEntities) {
            EntityPlayer player = (EntityPlayer)entry;
            if(player.isPlayerSleeping()) player.wakeUpPlayer(true, true, false);
            StaplerCampaign.tell(player, "GOD IS COMING");
        }
        world.setWorldTime(skyTime(data));
        StaplerForest.lamp(world, data, false);
        StaplerForest.updateDisplay(world, data);
        sync(world, data);
        return true;
    }

    @SubscribeEvent public void sleep(PlayerSleepInBedEvent event) {
        World world = event.entityPlayer.worldObj;
        if(world.isRemote || !StaplerWorldType.active(world)) return;
        StaplerCampaign data = StaplerCampaign.get(world);
        if(data.stage == StaplerCampaign.COMPLETE) return;
        if(data.stage == StaplerCampaign.HUNT && data.day < data.days && data.ready() && cabinBed(data, event.x, event.y, event.z)) return;
        event.result = EntityPlayer.EnumStatus.OTHER_PROBLEM;
        StaplerCampaign.tell(event.entityPlayer, data.stage != StaplerCampaign.HUNT ? "The woods will keep you awake."
                : data.ready() ? "Use the cabin bed." : "Bring today's trophies to the cabin rack before sleeping.");
    }

    private static boolean cabinBed(StaplerCampaign data, int x, int y, int z) {
        return data.cabinY == y && x == StaplerForest.BED_X && (z == StaplerForest.BED_Z || z == StaplerForest.BED_Z - 1);
    }

    public static boolean attach(EntityPlayer player, int x, int y, int z) {
        World world = player.worldObj;
        if(!(world.isRemote ? StaplerWorldType.isForest(world) : StaplerWorldType.active(world))
                || x != StaplerForest.DISPLAY_X || z != StaplerForest.DISPLAY_Z
                || world.getBlock(x, y, z) != Blocks.log) return false;
        if(world.isRemote) return true;
        StaplerCampaign data = StaplerCampaign.get(world);
        if(data.cabinY + 1 != y || data.cabinY == 0) return false;
        if(Restraints.blocked(player) || !data.attach(player)) return true;
        world.playSoundAtEntity(player, "random.click", 0.75F, 1.35F);
        StaplerForest.updateDisplay(world, data);
        if(data.ready()) {
            if(data.day == data.days) beginFinale(world, data);
            else {
                world.setWorldTime(skyTime(data));
                StaplerCampaign.tell(player, "Today's tally is complete. Sleep in the cabin bed.");
            }
        }
        sync(world, data);
        return true;
    }

    @SubscribeEvent public void restock(PlayerInteractEvent event) {
        World world = event.world;
        if(world.isRemote || !StaplerWorldType.active(world) || event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
                || event.x != StaplerForest.SUPPLY_X || event.z != StaplerForest.SUPPLY_Z) return;
        StaplerCampaign data = StaplerCampaign.get(world);
        if(data.cabinY + 1 != event.y || data.stage != StaplerCampaign.HUNT || Restraints.blocked(event.entityPlayer)) return;
        if(world.getBlock(event.x, event.y, event.z) != Blocks.wall_sign) return;
        event.setCanceled(true);
        EntityPlayer player = event.entityPlayer;
        int trophies = data.normalRequired + data.largeRequired;
        topUp(player, new ItemStack(rifle), 1, false);
        topUp(player, new ItemStack(ModItems.stapler), 1, false);
        topUp(player, StaplerForest.ammunition(1), Math.max(32, trophies * 3), true);
        topUp(player, new ItemStack(ModItems.staples), Math.max(16, trophies), true);
        topUp(player, new ItemStack(Items.bread), 8, true);
        StaplerCampaign.tell(player, "Supplies topped up where inventory allowed.");
    }

    private static void topUp(EntityPlayer player, ItemStack supply, int minimum, boolean matchDamage) {
        for(ItemStack stack : player.inventory.mainInventory) {
            if(stack != null && stack.getItem() == supply.getItem()
                    && (!matchDamage || stack.getItemDamage() == supply.getItemDamage())) minimum -= stack.stackSize;
        }
        while(minimum > 0) {
            ItemStack stack = supply.copy();
            int count = Math.min(minimum, stack.getMaxStackSize());
            stack.stackSize = count;
            if(!player.inventory.addItemStackToInventory(stack)) break;
            minimum -= count;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void join(EntityJoinWorldEvent event) {
        World world = event.world;
        if(world.isRemote) return;
        Entity entity = event.entity;
        if(entity instanceof StaplerSquirrels.Apparition || entity instanceof EntityStaplerArrival) {
            if(!StaplerWorldType.active(world) || !StaplerCampaign.get(world).encounters.owns(entity)) event.setCanceled(true);
        } else if((entity instanceof StaplerSquirrels.Large && !StaplerWorldType.isForest(world)) || blocked(world, entity)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void spawnCheck(LivingSpawnEvent.CheckSpawn event) {
        if(!event.world.isRemote && blocked(event.world, event.entityLiving)) event.setResult(Result.DENY);
    }

    private static boolean blocked(World world, Entity entity) {
        if(!StaplerWorldType.active(world)) return false;
        if(EntityMappings.isGeminiEntity(entity.getClass())) return SquirrelStaplerConfig.settings.suppressGemini;
        if(entity instanceof IBossDisplayData || entity instanceof EntitySandworm) return true;
        return SquirrelStaplerConfig.settings.suppressHostiles
                && (entity instanceof IMob || entity.isCreatureType(EnumCreatureType.monster, false));
    }

    @SubscribeEvent public void chunkUnload(ChunkEvent.Unload event) {
        if(!event.world.isRemote && StaplerWorldType.isForest(event.world))
            StaplerCampaign.get(event.world).encounters.chunkUnloaded(event.getChunk().xPosition, event.getChunk().zPosition);
    }

    @SubscribeEvent public void breakFixture(BlockEvent.BreakEvent event) {
        if(StaplerWorldType.active(event.world) && !event.world.isRemote
                && fixture(StaplerCampaign.get(event.world), event.x, event.y, event.z)) event.setCanceled(true);
    }

    @SubscribeEvent public void explosion(ExplosionEvent.Detonate event) {
        if(!StaplerWorldType.active(event.world) || event.world.isRemote) return;
        StaplerCampaign data = StaplerCampaign.get(event.world);
        event.getAffectedBlocks().removeIf(p -> fixture(data, p.chunkPosX, p.chunkPosY, p.chunkPosZ));
    }

    private static boolean fixture(StaplerCampaign data, int x, int y, int z) {
        int height = y - data.cabinY;
        if(data.cabinY <= 0 || height < -1 || height > 3) return false;
        if(x == StaplerForest.BED_X && (z == StaplerForest.BED_Z || z == StaplerForest.BED_Z - 1)) return height <= 0;
        if(x == StaplerForest.DISPLAY_X && (z == StaplerForest.DISPLAY_Z || z == StaplerForest.DISPLAY_Z + 1)) return true;
        if(x == StaplerForest.TALLY_X && (z == StaplerForest.DISPLAY_Z || z == StaplerForest.DISPLAY_Z + 1)) return height >= 1;
        return (x == StaplerForest.SUPPLY_X || x == StaplerForest.SUPPLY_X + 1) && z == StaplerForest.SUPPLY_Z;
    }

    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) { syncPlayer(event.player); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { syncPlayer(event.player); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { syncPlayer(event.player); }

    private static void syncPlayer(EntityPlayer player) {
        if(!(player instanceof EntityPlayerMP) || !StaplerWorldType.active(player.worldObj)) return;
        PacketDispatcher.wrapper.sendTo(new StaplerStatePacket(snapshot(StaplerCampaign.get(player.worldObj))), (EntityPlayerMP)player);
    }

    static void sync(World world, StaplerCampaign data) {
        boolean enabled = SquirrelStaplerConfig.settings.enabled;
        int effects = effects(data), seconds = data.stage == StaplerCampaign.COUNTDOWN ? data.secondsLeft() : 0;
        int fog = fogDistance();
        long stamp = enabled ? data.day | (long)data.days << 5 | (long)data.normalLeft() << 10 | (long)data.largeLeft() << 17
                | (long)data.stage << 22 | (long)seconds << 24 | (long)fog << 36 | (long)effects << 45 : 0L;
        if(data.clientStamp == stamp) return;
        data.clientStamp = stamp;
        StaplerStatePacket packet = new StaplerStatePacket(enabled ? snapshot(data) : new NBTTagCompound());
        for(Object entry : world.playerEntities) if(entry instanceof EntityPlayerMP) PacketDispatcher.wrapper.sendTo(packet, (EntityPlayerMP)entry);
    }

    private static NBTTagCompound snapshot(StaplerCampaign data) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Day", data.day);
        tag.setInteger("Days", data.days);
        tag.setInteger("Normal", data.normalLeft());
        tag.setInteger("Large", data.largeLeft());
        tag.setInteger("NormalMounted", data.normal);
        tag.setInteger("LargeMounted", data.large);
        tag.setInteger("CabinY", data.cabinY);
        tag.setInteger("Stage", data.stage);
        tag.setInteger("Seconds", data.stage == StaplerCampaign.COUNTDOWN ? data.secondsLeft() : 0);
        tag.setInteger("Fog", fogDistance());
        tag.setInteger("Effects", effects(data));
        return tag;
    }

    private static int fogDistance() {
        SquirrelStaplerConfig.Values settings = SquirrelStaplerConfig.settings;
        return settings.fog ? settings.fogDistance : 0;
    }

    private static int effects(StaplerCampaign data) {
        return (data.encounters.dark() ? 1 : 0) | (data.encounters.silent() ? 2 : 0);
    }
}
