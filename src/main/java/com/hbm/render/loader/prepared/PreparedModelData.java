package com.hbm.render.loader.prepared;

import org.lwjgl.opengl.GL11;

/** Immutable packed geometry produced by {@link PreparedObjParser}. */
final class PreparedModelData {

	static final int POSITION_OFFSET = 0;
	static final int UV_OFFSET = 3;
	static final int NORMAL_OFFSET = 5;
	static final int FLOATS_PER_VERTEX = 8;

	final float[] vertices;
	final String[] groupNames;
	final String[] materialNames;
	final int[] firstVertices;
	final int[] vertexCounts;
	final int[] drawModes;
	final boolean dynamicHmfUv;
	final boolean forgeUvInset;
	final boolean triangleOnly;
	final long retainedBytes;

	PreparedModelData(float[] vertices, String[] groupNames, String[] materialNames, int[] firstVertices, int[] vertexCounts, int[] drawModes,
			boolean dynamicHmfUv, boolean forgeUvInset) {
		this.vertices = vertices;
		this.groupNames = groupNames;
		this.materialNames = materialNames;
		this.firstVertices = firstVertices;
		this.vertexCounts = vertexCounts;
		this.drawModes = drawModes;
		this.dynamicHmfUv = dynamicHmfUv;
		this.forgeUvInset = forgeUvInset;

		boolean triangles = true;
		for(int mode : drawModes) {
			if(mode != GL11.GL_TRIANGLES) {
				triangles = false;
				break;
			}
		}
		this.triangleOnly = triangles && !dynamicHmfUv;
		this.retainedBytes = vertices.length * 4L + firstVertices.length * 4L + vertexCounts.length * 4L + drawModes.length * 4L;
	}

	int getVertexCount() {
		return vertices.length / FLOATS_PER_VERTEX;
	}
}
