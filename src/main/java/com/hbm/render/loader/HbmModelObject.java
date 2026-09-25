package com.hbm.render.loader;

import java.io.InputStream;

import com.hbm.render.loader.prepared.PreparedModelCache;
import com.hbm.render.loader.prepared.PreparedModelHandle;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModelCustom;
import net.minecraftforge.client.model.ModelFormatException;

/** Compatibility facade for the legacy HMF parser and mutable face graph. */
public class HbmModelObject implements IModelCustom {

	private final PreparedModelHandle handle;

	public HbmModelObject(ResourceLocation resource) throws ModelFormatException {
		handle = PreparedModelCache.getHmf(resource);
	}

	public HbmModelObject(String filename, InputStream inputStream) throws ModelFormatException {
		handle = PreparedModelCache.fromHmfStream(filename, inputStream);
	}

	@Override public String getType() { return "prepared_hmf"; }
	@Override public void renderAll() { handle.renderAll(); }
	@Override public void renderOnly(String... groupNames) { handle.renderOnly(groupNames); }
	@Override public void renderPart(String partName) { handle.renderPart(partName); }
	@Override public void renderAllExcept(String... excludedGroupNames) { handle.renderAllExcept(excludedGroupNames); }
}
