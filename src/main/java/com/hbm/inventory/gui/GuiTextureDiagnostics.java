package com.hbm.inventory.gui;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import com.hbm.main.MainRegistry;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;

/** Checks custom GUI texture fields on opening, never in the rendering loop. */
@SideOnly(Side.CLIENT)
public final class GuiTextureDiagnostics implements IResourceManagerReloadListener {

    private final Set<String> checked = new HashSet<>();

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.gui;
        for(Class<?> type = gui.getClass(); type != null && type.getName().startsWith("com.hbm."); type = type.getSuperclass()) {
            for(Field field : type.getDeclaredFields()) {
                if(field.getType() != ResourceLocation.class) continue;
                try {
                    field.setAccessible(true);
                    ResourceLocation texture = (ResourceLocation) field.get(gui);
                    if(texture == null || !texture.getResourcePath().startsWith("textures/")) continue;
                    String key = gui.getClass().getName() + ":" + texture;
                    if(!checked.add(key)) continue;
                    try(InputStream stream = Minecraft.getMinecraft().getResourceManager().getResource(texture).getInputStream()) {
                        // Resolve through the active resource packs, just like TextureManager.
                    } catch(IOException failure) {
                        MainRegistry.logger.warn("GUI " + gui.getClass().getName() + " could not read texture " + texture, failure);
                    }
                } catch(IllegalAccessException | SecurityException failure) {
                    String key = gui.getClass().getName() + ":" + field;
                    if(checked.add(key)) MainRegistry.logger.warn("Could not inspect GUI texture " + field + " for " + gui.getClass().getName(), failure);
                }
            }
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        checked.clear();
    }
}
