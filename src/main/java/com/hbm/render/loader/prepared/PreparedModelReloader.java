package com.hbm.render.loader.prepared;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

@SideOnly(Side.CLIENT)
public final class PreparedModelReloader implements IResourceManagerReloadListener {

	@Override
	public void onResourceManagerReload(IResourceManager resourceManager) {
		PreparedModelCache.reloadAll(resourceManager);
	}
}
