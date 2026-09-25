package com.hbm.render.loader.prepared;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.hbm.render.loader.HmfController;
import com.hbm.render.loader.IModelCustomNamed;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;

/**
 * Stable renderer-facing reference. Resource reload replaces its immutable
 * state rather than invalidating references held by renderers.
 */
public final class PreparedModelHandle implements IModelCustomNamed {

	private static final int STRIDE_BYTES = PreparedModelData.FLOATS_PER_VERTEX * 4;
	private static final float FORGE_UV_INSET = 0.0005F;

	final PreparedModelCache.Key key;
	private volatile State state;
	private volatile boolean gpuRequested;

	PreparedModelHandle(PreparedModelCache.Key key, PreparedModelData data, boolean requestGpu) {
		this.key = key;
		this.gpuRequested = requestGpu;
		this.state = createState(data, requestGpu);
	}

	public void enableGpu() {
		if(gpuRequested) return;
		gpuRequested = true;
		State current = state;
		if(current.vbo == 0 && current.data.triangleOnly && !current.data.dynamicHmfUv && PreparedModelGpuBackend.get() != null) {
			State replacement = createState(current.data, true);
			state = replacement;
			current.deleteGpu();
		}
	}

	void replace(PreparedModelData data) {
		State old = state;
		State replacement = createState(data, gpuRequested);
		state = replacement;
		old.deleteGpu();
	}

	long getRetainedCpuBytes() { return state.data.retainedBytes; }
	long getUploadedGpuBytes() { return state.gpuBytes; }

	@Override
	public String getType() {
		return "prepared_obj";
	}

	@Override
	public void renderAll() {
		renderSelection(null, null, false);
	}

	@Override
	public void renderOnly(String... groupNames) {
		renderSelection(null, groupNames, false);
	}

	@Override
	public void renderPart(String partName) {
		renderSelection(partName, null, false);
	}

	@Override
	public void renderAllExcept(String... excludedGroupNames) {
		renderSelection(null, excludedGroupNames, true);
	}

	/** Draw a group after a caller-applied model-matrix transform. */
	public void renderPartDynamic(String partName) {
		renderSelection(partName, null, false);
	}

	public List<String> getPartNames() {
		return state.partNames;
	}

	private void renderSelection(String singleName, String[] names, boolean exclude) {
		State snapshot = state;
		if(snapshot.vbo != 0) {
			renderGpu(snapshot, singleName, names, exclude);
		} else {
			renderCpu(snapshot.data, singleName, names, exclude);
		}
	}

	private static void renderGpu(State state, String singleName, String[] names, boolean exclude) {
		PreparedModelData data = state.data;
		PreparedModelGpuBackend backend = state.backend;
		backend.bindArrayBuffer(state.vbo);
		backend.enableClientState(GL11.GL_VERTEX_ARRAY);
		backend.enableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		backend.enableClientState(GL11.GL_NORMAL_ARRAY);
		backend.vertexPointer(3, GL11.GL_FLOAT, STRIDE_BYTES, 0L);
		backend.texCoordPointer(2, GL11.GL_FLOAT, STRIDE_BYTES, 12L);
		backend.normalPointer(GL11.GL_FLOAT, STRIDE_BYTES, 20L);

		if(singleName == null && names == null && !exclude) {
			// Uploaded models are triangle-only, so a whole-model draw does not
			// need to stop at named-group or material metadata boundaries.
			backend.drawArrays(GL11.GL_TRIANGLES, 0, data.getVertexCount());
		} else {
			for(int range = 0; range < data.groupNames.length; range++) {
				if(matches(data.groupNames[range], singleName, names) != exclude) {
					backend.drawArrays(data.drawModes[range], data.firstVertices[range], data.vertexCounts[range]);
				}
			}
		}

		backend.disableClientState(GL11.GL_NORMAL_ARRAY);
		backend.disableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		backend.disableClientState(GL11.GL_VERTEX_ARRAY);
		backend.bindArrayBuffer(0);
	}

	private static void renderCpu(PreparedModelData data, String singleName, String[] names, boolean exclude) {
		Tessellator tessellator = Tessellator.instance;
		if(singleName == null && names == null && !exclude && !data.dynamicHmfUv) {
			int activeMode = -1;
			for(int range = 0; range < data.groupNames.length; range++) {
				if(data.drawModes[range] != activeMode) {
					if(activeMode != -1) tessellator.draw();
					activeMode = data.drawModes[range];
					tessellator.startDrawing(activeMode);
				}
				emitCpuVertices(data, tessellator, data.firstVertices[range], data.vertexCounts[range], data.drawModes[range]);
			}
			if(activeMode != -1) tessellator.draw();
			return;
		}
		for(int range = 0; range < data.groupNames.length; range++) {
			if(matches(data.groupNames[range], singleName, names) == exclude) continue;
			tessellator.startDrawing(data.drawModes[range]);
			int end = data.firstVertices[range] + data.vertexCounts[range];
			if(data.dynamicHmfUv) {
				int faceSize = data.drawModes[range] == GL11.GL_TRIANGLES ? 3 : 4;
				double timeOffset = ((double) System.currentTimeMillis() % HmfController.modoloMod) / HmfController.quotientMod;
				for(int face = data.firstVertices[range]; face < end; face += faceSize) {
					float averageU = 0F;
					float averageV = 0F;
					for(int corner = 0; corner < faceSize; corner++) {
						int offset = (face + corner) * PreparedModelData.FLOATS_PER_VERTEX;
						averageU += data.vertices[offset + PreparedModelData.UV_OFFSET];
						averageV += data.vertices[offset + PreparedModelData.UV_OFFSET + 1];
					}
					averageU /= faceSize;
					averageV /= faceSize;
					int normal = face * PreparedModelData.FLOATS_PER_VERTEX + PreparedModelData.NORMAL_OFFSET;
					tessellator.setNormal(data.vertices[normal], data.vertices[normal + 1], data.vertices[normal + 2]);
					for(int corner = 0; corner < faceSize; corner++) {
						int offset = (face + corner) * PreparedModelData.FLOATS_PER_VERTEX;
						float u = data.vertices[offset + PreparedModelData.UV_OFFSET];
						float v = data.vertices[offset + PreparedModelData.UV_OFFSET + 1];
						if(Float.isNaN(u) || Float.isNaN(v)) {
							tessellator.addVertex(data.vertices[offset], data.vertices[offset + 1], data.vertices[offset + 2]);
						} else {
							float offsetU = u > averageU ? -0.0005F : 0.0005F;
							float offsetV = v > averageV ? -0.0005F : 0.0005F;
							tessellator.addVertexWithUV(data.vertices[offset], data.vertices[offset + 1], data.vertices[offset + 2], u + offsetU, v + offsetV + timeOffset);
						}
					}
				}
				tessellator.draw();
				continue;
			}
			emitCpuVertices(data, tessellator, data.firstVertices[range], data.vertexCounts[range], data.drawModes[range]);
			tessellator.draw();
		}
	}

	private static void emitCpuVertices(PreparedModelData data, Tessellator tessellator, int firstVertex, int vertexCount, int drawMode) {
		if(data.forgeUvInset) {
			emitForgeCpuVertices(data, tessellator, firstVertex, vertexCount, drawMode);
			return;
		}
		int end = firstVertex + vertexCount;
		for(int vertex = firstVertex; vertex < end; vertex++) {
			int offset = vertex * PreparedModelData.FLOATS_PER_VERTEX;
			tessellator.setNormal(data.vertices[offset + PreparedModelData.NORMAL_OFFSET], data.vertices[offset + PreparedModelData.NORMAL_OFFSET + 1], data.vertices[offset + PreparedModelData.NORMAL_OFFSET + 2]);
			float u = data.vertices[offset + PreparedModelData.UV_OFFSET];
			float v = data.vertices[offset + PreparedModelData.UV_OFFSET + 1];
			if(Float.isNaN(u) || Float.isNaN(v)) tessellator.addVertex(data.vertices[offset], data.vertices[offset + 1], data.vertices[offset + 2]);
			else tessellator.addVertexWithUV(data.vertices[offset], data.vertices[offset + 1], data.vertices[offset + 2], u, v);
		}
	}

	private static void emitForgeCpuVertices(PreparedModelData data, Tessellator tessellator, int firstVertex, int vertexCount, int drawMode) {
		int faceSize = drawMode == GL11.GL_TRIANGLES ? 3 : 4;
		int end = firstVertex + vertexCount;
		for(int face = firstVertex; face < end; face += faceSize) {
			float averageU = 0F;
			float averageV = 0F;
			boolean hasUv = true;
			for(int corner = 0; corner < faceSize; corner++) {
				int offset = (face + corner) * PreparedModelData.FLOATS_PER_VERTEX + PreparedModelData.UV_OFFSET;
				float u = data.vertices[offset];
				float v = data.vertices[offset + 1];
				if(Float.isNaN(u) || Float.isNaN(v)) {
					hasUv = false;
					break;
				}
				averageU += u;
				averageV += v;
			}
			if(hasUv) {
				averageU /= faceSize;
				averageV /= faceSize;
			}
			for(int corner = 0; corner < faceSize; corner++) {
				int offset = (face + corner) * PreparedModelData.FLOATS_PER_VERTEX;
				tessellator.setNormal(data.vertices[offset + PreparedModelData.NORMAL_OFFSET], data.vertices[offset + PreparedModelData.NORMAL_OFFSET + 1],
						data.vertices[offset + PreparedModelData.NORMAL_OFFSET + 2]);
				if(!hasUv) {
					tessellator.addVertex(data.vertices[offset], data.vertices[offset + 1], data.vertices[offset + 2]);
				} else {
					float u = data.vertices[offset + PreparedModelData.UV_OFFSET];
					float v = data.vertices[offset + PreparedModelData.UV_OFFSET + 1];
					float offsetU = u > averageU ? -FORGE_UV_INSET : FORGE_UV_INSET;
					float offsetV = v > averageV ? -FORGE_UV_INSET : FORGE_UV_INSET;
					tessellator.addVertexWithUV(data.vertices[offset], data.vertices[offset + 1], data.vertices[offset + 2], u + offsetU, v + offsetV);
				}
			}
		}
	}

	/** Submit packed geometry into an already-running block/inventory quad batch. */
	public void tessellateWithIcon(IIcon icon, Tessellator tessellator, float yaw, float pitch, float roll, boolean shadow,
			String partName, boolean hasColor, int red, int green, int blue) {
		tessellateWithIcon(icon, tessellator, yaw, pitch, roll, shadow, partName, null, hasColor, red, green, blue);
	}

	public void tessellateGroupsWithIcon(String[] groupNames, IIcon icon, Tessellator tessellator, boolean shadow,
			boolean hasColor, int red, int green, int blue) {
		tessellateWithIcon(icon, tessellator, 0F, 0F, 0F, shadow, null, groupNames, hasColor, red, green, blue);
	}

	/** Specialized allocation-free replacement for the solar mirror's pivoted panel transform. */
	public void tessellatePivotedPartWithIcon(String partName, IIcon icon, Tessellator tessellator, float pitch, float yaw, float pivotY) {
		PreparedModelData data = state.data;
		float sinPitch = (float) Math.sin(pitch);
		float cosPitch = (float) Math.cos(pitch);
		float sinYaw = (float) Math.sin(yaw);
		float cosYaw = (float) Math.cos(yaw);
		for(int range = 0; range < data.groupNames.length; range++) {
			if(!partName.equals(data.groupNames[range])) continue;
			int faceSize = data.drawModes[range] == GL11.GL_TRIANGLES ? 3 : 4;
			int end = data.firstVertices[range] + data.vertexCounts[range];
			for(int face = data.firstVertices[range]; face < end; face += faceSize) {
				int normal = face * PreparedModelData.FLOATS_PER_VERTEX + PreparedModelData.NORMAL_OFFSET;
				float nx = data.vertices[normal];
				float ny = data.vertices[normal + 1];
				float nz = data.vertices[normal + 2];
				tessellator.setNormal(nx, ny, nz);
				float brightness = (ny + 1F) * 0.65F;
				if(brightness < 0.45F) brightness = 0.45F;
				tessellator.setColorOpaque_F(brightness, brightness, brightness);
				for(int corner = 0; corner < faceSize; corner++) {
					int offset = (face + corner) * PreparedModelData.FLOATS_PER_VERTEX;
					float localY = data.vertices[offset + 1] - pivotY;
					float pitchedY = localY * cosPitch + data.vertices[offset + 2] * sinPitch;
					float pitchedZ = data.vertices[offset + 2] * cosPitch - localY * sinPitch;
					float x = data.vertices[offset] * cosYaw + pitchedZ * sinYaw;
					float z = pitchedZ * cosYaw - data.vertices[offset] * sinYaw;
					float y = pitchedY + pivotY;
					double u = icon.getInterpolatedU(data.vertices[offset + PreparedModelData.UV_OFFSET] * 16D);
					double v = icon.getInterpolatedV(data.vertices[offset + PreparedModelData.UV_OFFSET + 1] * 16D);
					tessellator.addVertexWithUV(x, y, z, u, v);
					if(faceSize == 3 && corner == 2) tessellator.addVertexWithUV(x, y, z, u, v);
				}
			}
		}
	}

	private void tessellateWithIcon(IIcon icon, Tessellator tessellator, float yaw, float pitch, float roll, boolean shadow,
			String partName, String[] groupNames, boolean hasColor, int red, int green, int blue) {
		PreparedModelData data = state.data;
		float sinRoll = (float) Math.sin(roll);
		float cosRoll = (float) Math.cos(roll);
		float sinPitch = (float) Math.sin(pitch);
		float cosPitch = (float) Math.cos(pitch);
		float sinYaw = (float) Math.sin(yaw);
		float cosYaw = (float) Math.cos(yaw);

		for(int range = 0; range < data.groupNames.length; range++) {
			if(partName != null && !partName.equals(data.groupNames[range])) continue;
			if(groupNames != null && !matches(data.groupNames[range], null, groupNames)) continue;
			int faceSize = data.drawModes[range] == GL11.GL_TRIANGLES ? 3 : 4;
			int end = data.firstVertices[range] + data.vertexCounts[range];
			for(int face = data.firstVertices[range]; face < end; face += faceSize) {
				int a = face * PreparedModelData.FLOATS_PER_VERTEX;
				int b = (face + 1) * PreparedModelData.FLOATS_PER_VERTEX;
				int c = (face + 2) * PreparedModelData.FLOATS_PER_VERTEX;
				float ax = data.vertices[b] - data.vertices[a];
				float ay = data.vertices[b + 1] - data.vertices[a + 1];
				float az = data.vertices[b + 2] - data.vertices[a + 2];
				float bx = data.vertices[c] - data.vertices[a];
				float by = data.vertices[c + 1] - data.vertices[a + 1];
				float bz = data.vertices[c + 2] - data.vertices[a + 2];
				float nx = ay * bz - az * by;
				float ny = az * bx - ax * bz;
				float nz = ax * by - ay * bx;
				float normalLength = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				if(normalLength != 0F) {
					nx /= normalLength;
					ny /= normalLength;
					nz /= normalLength;
				}

				double pitchedNormalX = nx * cosPitch + ny * sinPitch;
				double pitchedNormalY = ny * cosPitch - nx * sinPitch;
				double rotatedNormalX = pitchedNormalX * cosYaw + nz * sinYaw;
				double rotatedNormalZ = nz * cosYaw - pitchedNormalX * sinYaw;
				tessellator.setNormal((float) rotatedNormalX, (float) pitchedNormalY, (float) rotatedNormalZ);

				if(shadow || hasColor) {
					float brightness = 1F;
					if(shadow) {
						brightness = partName == null
								? ((float) pitchedNormalY + 0.7F) * 0.9F - (float) Math.abs(rotatedNormalX) * 0.1F + (float) Math.abs(rotatedNormalZ) * 0.1F
								: (float) pitchedNormalY * 0.3F + 0.7F - (float) Math.abs(rotatedNormalX) * 0.1F + (float) Math.abs(rotatedNormalZ) * 0.1F;
						if(brightness < 0.45F) brightness = 0.45F;
					}
					if(hasColor) tessellator.setColorOpaque((int) (red * brightness), (int) (green * brightness), (int) (blue * brightness));
					else tessellator.setColorOpaque_F(brightness, brightness, brightness);
				}

				for(int corner = 0; corner < faceSize; corner++) {
					int offset = (face + corner) * PreparedModelData.FLOATS_PER_VERTEX;
					double rollY = data.vertices[offset + 1] * cosRoll + data.vertices[offset + 2] * sinRoll;
					double rollZ = data.vertices[offset + 2] * cosRoll - data.vertices[offset + 1] * sinRoll;
					double pitchX = data.vertices[offset] * cosPitch + rollY * sinPitch;
					double y = rollY * cosPitch - data.vertices[offset] * sinPitch;
					double x = pitchX * cosYaw + rollZ * sinYaw;
					double z = rollZ * cosYaw - pitchX * sinYaw;
					double u = icon.getInterpolatedU(data.vertices[offset + PreparedModelData.UV_OFFSET] * 16D);
					double v = icon.getInterpolatedV(data.vertices[offset + PreparedModelData.UV_OFFSET + 1] * 16D);
					tessellator.addVertexWithUV(x, y, z, u, v);
					if(faceSize == 3 && corner == 2) tessellator.addVertexWithUV(x, y, z, u, v);
				}
			}
		}
	}

	private static boolean matches(String groupName, String singleName, String[] names) {
		if(singleName != null) return groupName.equalsIgnoreCase(singleName);
		if(names == null) return true;
		for(String name : names) if(groupName.equalsIgnoreCase(name)) return true;
		return false;
	}

	private static State createState(PreparedModelData data, boolean upload) {
		int vbo = 0;
		long gpuBytes = 0L;
		PreparedModelGpuBackend backend = upload && data.triangleOnly && !data.dynamicHmfUv ? PreparedModelGpuBackend.get() : null;
		long start = PreparedModelCache.metricsEnabled() && backend != null ? System.nanoTime() : 0L;
		if(backend != null && data.getVertexCount() > 0) {
			FloatBuffer buffer = BufferUtils.createFloatBuffer(data.vertices.length);
			putUploadedVertices(data, buffer);
			buffer.flip();
			vbo = backend.createBuffer(buffer);
			gpuBytes = data.vertices.length * 4L;
		}
		if(start != 0L) PreparedModelCache.recordUpload(System.nanoTime() - start, gpuBytes);
		return new State(data, backend, vbo, gpuBytes);
	}

	private static void putUploadedVertices(PreparedModelData data, FloatBuffer buffer) {
		if(!data.forgeUvInset) {
			for(int i = 0; i < data.vertices.length; i++) {
				float value = data.vertices[i];
				buffer.put(Float.isNaN(value) ? 0F : value);
			}
			return;
		}

		for(int range = 0; range < data.groupNames.length; range++) {
			int faceSize = data.drawModes[range] == GL11.GL_TRIANGLES ? 3 : 4;
			int end = data.firstVertices[range] + data.vertexCounts[range];
			for(int face = data.firstVertices[range]; face < end; face += faceSize) {
				float averageU = 0F;
				float averageV = 0F;
				boolean hasUv = true;
				for(int corner = 0; corner < faceSize; corner++) {
					int offset = (face + corner) * PreparedModelData.FLOATS_PER_VERTEX + PreparedModelData.UV_OFFSET;
					float u = data.vertices[offset];
					float v = data.vertices[offset + 1];
					if(Float.isNaN(u) || Float.isNaN(v)) {
						hasUv = false;
						break;
					}
					averageU += u;
					averageV += v;
				}
				if(hasUv) {
					averageU /= faceSize;
					averageV /= faceSize;
				}
				for(int corner = 0; corner < faceSize; corner++) {
					int offset = (face + corner) * PreparedModelData.FLOATS_PER_VERTEX;
					float u = data.vertices[offset + PreparedModelData.UV_OFFSET];
					float v = data.vertices[offset + PreparedModelData.UV_OFFSET + 1];
					buffer.put(data.vertices[offset]).put(data.vertices[offset + 1]).put(data.vertices[offset + 2]);
					if(hasUv) {
						buffer.put(u + (u > averageU ? -FORGE_UV_INSET : FORGE_UV_INSET));
						buffer.put(v + (v > averageV ? -FORGE_UV_INSET : FORGE_UV_INSET));
					} else {
						buffer.put(0F).put(0F);
					}
					buffer.put(data.vertices[offset + PreparedModelData.NORMAL_OFFSET]);
					buffer.put(data.vertices[offset + PreparedModelData.NORMAL_OFFSET + 1]);
					buffer.put(data.vertices[offset + PreparedModelData.NORMAL_OFFSET + 2]);
				}
			}
		}
	}

	private static final class State {
		final PreparedModelData data;
		final PreparedModelGpuBackend backend;
		final int vbo;
		final long gpuBytes;
		final List<String> partNames;

		State(PreparedModelData data, PreparedModelGpuBackend backend, int vbo, long gpuBytes) {
			this.data = data;
			this.backend = backend;
			this.vbo = vbo;
			this.gpuBytes = gpuBytes;
			List<String> names = new ArrayList<String>();
			for(String name : data.groupNames) if(!names.contains(name)) names.add(name);
			this.partNames = Collections.unmodifiableList(names);
		}

		void deleteGpu() {
			if(vbo != 0) backend.deleteBuffer(vbo);
		}
	}
}
