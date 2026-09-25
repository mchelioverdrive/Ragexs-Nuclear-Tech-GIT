package com.hbm.render.loader;

import java.io.InputStream;
import java.util.List;

import com.hbm.render.loader.prepared.PreparedModelCache;
import com.hbm.render.loader.prepared.PreparedModelHandle;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelFormatException;

/** Compatibility facade for the former HFR parser. */
public class HFRWavefrontObject implements IModelCustomNamed {

	private final ResourceLocation resource;
	private final String fileName;
	private final PreparedModelHandle handle;

	public HFRWavefrontObject(ResourceLocation resource) throws ModelFormatException {
		this(resource, true);
	}

	public HFRWavefrontObject(ResourceLocation resource, boolean smoothing) throws ModelFormatException {
		this.resource = resource;
		this.fileName = resource.toString();
		this.handle = PreparedModelCache.getHfr(resource, smoothing, false);
	}

	public HFRWavefrontObject(String filename, InputStream inputStream) throws ModelFormatException {
		this.resource = null;
		this.fileName = filename;
		this.handle = PreparedModelCache.fromStream(filename, inputStream, true);
	}

	public void reload() throws ModelFormatException {
		if(resource == null) throw new ModelFormatException("Cannot reload streamed model without a resource location: " + fileName);
		// Resource-backed handles are replaced atomically by PreparedModelCache.
	}

	@Override public void renderAll() { handle.renderAll(); }
	@Override public void renderOnly(String... groupNames) { handle.renderOnly(groupNames); }
	@Override public void renderPart(String partName) { handle.renderPart(partName); }
	@Override public void renderAllExcept(String... excludedGroupNames) { handle.renderAllExcept(excludedGroupNames); }
	@Override public String getType() { return "prepared_hfr_obj"; }
	@Override public List<String> getPartNames() { return handle.getPartNames(); }

	public HFRWavefrontObjectVBO asVBO() {
		handle.enableGpu();
		return new HFRWavefrontObjectVBO(handle);
	}

	public String getFileName() { return fileName; }
	public WavefrontObjDisplayList asDisplayList() { return new WavefrontObjDisplayList(this); }
	PreparedModelHandle getPreparedHandle() { return handle; }
}
