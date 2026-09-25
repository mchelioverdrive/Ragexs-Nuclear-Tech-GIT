package com.hbm.render.loader;

import java.util.Collections;
import java.util.List;

import com.hbm.render.loader.prepared.PreparedModelHandle;

/** Compatibility facade for legacy HFR .asVBO() call sites. */
public class HFRWavefrontObjectVBO implements IModelCustomNamed {

	private final PreparedModelHandle handle;

	public HFRWavefrontObjectVBO(HFRWavefrontObject obj) {
		this(obj.getPreparedHandle());
	}

	HFRWavefrontObjectVBO(PreparedModelHandle handle) {
		this.handle = handle;
		this.handle.enableGpu();
	}

	public static void reloadModels() { }
	public static void deleteModels() { }
	public void reload() { }
	public void deleteBuffers() { }

	@Override public String getType() { return "prepared_obj_vbo"; }
	@Override public void renderAll() { handle.renderAll(); }
	@Override public void renderOnly(String... groupNames) { handle.renderOnly(groupNames); }
	@Override public void renderPart(String partName) { handle.renderPart(partName); }
	@Override public void renderAllExcept(String... excludedGroupNames) { handle.renderAllExcept(excludedGroupNames); }
	@Override public List<String> getPartNames() { return handle.getPartNames(); }

	public static List<HFRWavefrontObjectVBO> getLoadedModels() {
		return Collections.emptyList();
	}
}
