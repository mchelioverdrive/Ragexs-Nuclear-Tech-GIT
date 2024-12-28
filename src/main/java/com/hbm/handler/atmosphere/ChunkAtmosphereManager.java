package com.hbm.handler.atmosphere;

import com.hbm.dim.trait.CBT_Atmosphere;
import com.hbm.items.ModItems;
import com.hbm.items.food.ItemConserve;
import com.hbm.util.EnumUtil;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFood;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;

import static com.hbm.extprop.HbmLivingProps.getAtmosphere;

public class ChunkAtmosphereManager {

    public static ChunkAtmosphereHandler proxy = new ChunkAtmosphereHandler();

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        proxy.receiveWorldLoad(event);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        proxy.receiveWorldUnload(event);
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        proxy.receiveBlockPlaced(event);
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        proxy.receiveBlockBroken(event);
    }

	@SubscribeEvent
	public void onDetonate(ExplosionEvent.Detonate event) {
		proxy.receiveDetonate(event);
	}

	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {
		proxy.receiveServerTick(event);
	}

}
