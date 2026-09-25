package com.hbm.render.loader.prepared;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelFormatException;

/**
 * OBJ reader which retains only packed draw-ready vertices and compact range
 * metadata. Parsing scratch and OBJ index tables are discarded on return.
 */
final class PreparedObjParser {

	private PreparedObjParser() { }

	static PreparedModelData parse(ResourceLocation resource, InputStream input, boolean smoothing, boolean dynamicHmfUv, boolean forgeUvInset) {
		FloatList positions = new FloatList(4096);
		FloatList normals = new FloatList(4096);
		FloatList uvs = new FloatList(2048);
		FloatList output = new FloatList(16384);
		List<String> groupNames = new ArrayList<String>();
		List<String> materialNames = new ArrayList<String>();
		IntList firstVertices = new IntList(32);
		IntList vertexCounts = new IntList(32);
		IntList drawModes = new IntList(32);

		String group = "Default";
		String material = "";
		int activeRange = -1;
		int lineNumber = 0;
		int[] positionIndices = new int[4];
		int[] uvIndices = new int[4];
		int[] normalIndices = new int[4];
		float[] components = new float[4];

		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(input));
			try {
				String line;
				while((line = reader.readLine()) != null) {
					lineNumber++;
					line = line.trim();
					if(line.length() == 0 || line.charAt(0) == '#') continue;

					if(isDirective(line, "v")) {
						int count = parseComponents(line, 1, components, resource, lineNumber);
						require(count >= 3, resource, lineNumber, "vertex requires three coordinates");
						positions.add(components[0]);
						positions.add(components[1]);
						positions.add(components[2]);
					} else if(isDirective(line, "vn")) {
						int count = parseComponents(line, 2, components, resource, lineNumber);
						require(count >= 3, resource, lineNumber, "normal requires three coordinates");
						normals.add(components[0]);
						normals.add(components[1]);
						normals.add(components[2]);
					} else if(isDirective(line, "vt")) {
						int count = parseComponents(line, 2, components, resource, lineNumber);
						require(count >= 2, resource, lineNumber, "texture coordinate requires two coordinates");
						uvs.add(components[0]);
						uvs.add(1F - components[1]);
					} else if(isDirective(line, "g") || isDirective(line, "o")) {
						group = line.substring(1).trim();
						if(group.length() == 0) group = "Default";
						activeRange = -1;
					} else if(isDirective(line, "usemtl")) {
						material = line.substring(6).trim();
						activeRange = -1;
					} else if(isDirective(line, "f")) {
						int corners = parseFaceIndices(line, positions.size() / 3, uvs.size() / 2, normals.size() / 3,
								positionIndices, uvIndices, normalIndices, resource, lineNumber);
						require(corners == 3 || corners == 4, resource, lineNumber, "only triangle and quad faces are supported");
						int mode = corners == 3 ? GL11.GL_TRIANGLES : GL11.GL_QUADS;
						int currentVertex = output.size() / PreparedModelData.FLOATS_PER_VERTEX;
						if(activeRange < 0 || drawModes.get(activeRange) != mode) {
							groupNames.add(group);
							materialNames.add(material);
							firstVertices.add(currentVertex);
							vertexCounts.add(0);
							drawModes.add(mode);
							activeRange = drawModes.size() - 1;
						}

						int p0 = positionIndices[0] * 3;
						int p1 = positionIndices[1] * 3;
						int p2 = positionIndices[2] * 3;
						float ax = positions.get(p1) - positions.get(p0);
						float ay = positions.get(p1 + 1) - positions.get(p0 + 1);
						float az = positions.get(p1 + 2) - positions.get(p0 + 2);
						float bx = positions.get(p2) - positions.get(p0);
						float by = positions.get(p2 + 1) - positions.get(p0 + 1);
						float bz = positions.get(p2 + 2) - positions.get(p0 + 2);
						float faceX = ay * bz - az * by;
						float faceY = az * bx - ax * bz;
						float faceZ = ax * by - ay * bx;
						float length = (float) Math.sqrt(faceX * faceX + faceY * faceY + faceZ * faceZ);
						if(length != 0F) {
							faceX /= length;
							faceY /= length;
							faceZ /= length;
						}

						for(int i = 0; i < corners; i++) {
							int position = positionIndices[i] * 3;
							output.add(positions.get(position));
							output.add(positions.get(position + 1));
							output.add(positions.get(position + 2));
							if(uvIndices[i] >= 0) {
								int uv = uvIndices[i] * 2;
								output.add(uvs.get(uv));
								output.add(uvs.get(uv + 1));
							} else {
								output.add(Float.NaN);
								output.add(Float.NaN);
							}
							if(smoothing && normalIndices[i] >= 0) {
								int normal = normalIndices[i] * 3;
								output.add(normals.get(normal));
								output.add(normals.get(normal + 1));
								output.add(normals.get(normal + 2));
							} else {
								output.add(faceX);
								output.add(faceY);
								output.add(faceZ);
							}
						}
						vertexCounts.set(activeRange, vertexCounts.get(activeRange) + corners);
					}
				}
			} finally {
				reader.close();
			}
		} catch(IOException e) {
			throw new ModelFormatException("IO exception reading prepared OBJ '" + resource + "'", e);
		} catch(ModelFormatException e) {
			throw e;
		} catch(RuntimeException e) {
			throw new ModelFormatException("Error parsing prepared OBJ '" + resource + "' at line " + lineNumber, e);
		}

		return new PreparedModelData(output.toArray(), groupNames.toArray(new String[groupNames.size()]),
				materialNames.toArray(new String[materialNames.size()]), firstVertices.toArray(), vertexCounts.toArray(), drawModes.toArray(),
				dynamicHmfUv, forgeUvInset);
	}

	private static int parseComponents(String line, int cursor, float[] output, ResourceLocation resource, int lineNumber) {
		int count = 0;
		while(cursor < line.length() && count < output.length) {
			while(cursor < line.length() && Character.isWhitespace(line.charAt(cursor))) cursor++;
			if(cursor >= line.length()) break;
			int end = cursor + 1;
			while(end < line.length() && !Character.isWhitespace(line.charAt(end))) end++;
			output[count++] = parseFloat(line.substring(cursor, end), resource, lineNumber);
			cursor = end;
		}
		return count;
	}

	private static boolean isDirective(String line, String directive) {
		return line.startsWith(directive) && line.length() > directive.length() && Character.isWhitespace(line.charAt(directive.length()));
	}

	private static int parseFaceIndices(String line, int positionCount, int uvCount, int normalCount, int[] positions, int[] uvs,
			int[] normals, ResourceLocation resource, int lineNumber) {
		int cursor = 1;
		int corners = 0;
		while(cursor < line.length()) {
			while(cursor < line.length() && Character.isWhitespace(line.charAt(cursor))) cursor++;
			if(cursor >= line.length()) break;
			require(corners < 4, resource, lineNumber, "only triangle and quad faces are supported");
			int end = cursor + 1;
			while(end < line.length() && !Character.isWhitespace(line.charAt(end))) end++;
			int slash1 = line.indexOf('/', cursor);
			if(slash1 < 0 || slash1 >= end) {
				positions[corners] = resolveIndex(line.substring(cursor, end), positionCount, resource, lineNumber);
				uvs[corners] = -1;
				normals[corners] = -1;
			} else {
				positions[corners] = resolveIndex(line.substring(cursor, slash1), positionCount, resource, lineNumber);
				int slash2 = line.indexOf('/', slash1 + 1);
				if(slash2 < 0 || slash2 >= end) {
					uvs[corners] = slash1 + 1 < end ? resolveIndex(line.substring(slash1 + 1, end), uvCount, resource, lineNumber) : -1;
					normals[corners] = -1;
				} else {
					uvs[corners] = slash1 + 1 < slash2 ? resolveIndex(line.substring(slash1 + 1, slash2), uvCount, resource, lineNumber) : -1;
					normals[corners] = slash2 + 1 < end ? resolveIndex(line.substring(slash2 + 1, end), normalCount, resource, lineNumber) : -1;
				}
			}
			corners++;
			cursor = end;
		}
		return corners;
	}

	private static int resolveIndex(String token, int count, ResourceLocation resource, int lineNumber) {
		int value;
		try {
			value = Integer.parseInt(token);
		} catch(NumberFormatException e) {
			throw new ModelFormatException("Invalid OBJ index at line " + lineNumber + " in '" + resource + "'", e);
		}
		int resolved = value > 0 ? value - 1 : count + value;
		require(resolved >= 0 && resolved < count, resource, lineNumber, "OBJ index is out of range");
		return resolved;
	}

	private static float parseFloat(String token, ResourceLocation resource, int lineNumber) {
		try {
			return Float.parseFloat(token);
		} catch(NumberFormatException e) {
			throw new ModelFormatException("Invalid number at line " + lineNumber + " in '" + resource + "'", e);
		}
	}

	private static void require(boolean condition, ResourceLocation resource, int lineNumber, String message) {
		if(!condition) throw new ModelFormatException(message + " at line " + lineNumber + " in '" + resource + "'");
	}

	private static final class FloatList {
		private float[] values;
		private int size;

		FloatList(int capacity) { values = new float[capacity]; }
		void add(float value) { ensure(size + 1); values[size++] = value; }
		float get(int index) { return values[index]; }
		int size() { return size; }
		float[] toArray() {
			float[] copy = new float[size];
			System.arraycopy(values, 0, copy, 0, size);
			return copy;
		}
		private void ensure(int required) {
			if(required <= values.length) return;
			float[] grown = new float[Math.max(required, values.length * 2)];
			System.arraycopy(values, 0, grown, 0, size);
			values = grown;
		}
	}

	private static final class IntList {
		private int[] values;
		private int size;

		IntList(int capacity) { values = new int[capacity]; }
		void add(int value) { ensure(size + 1); values[size++] = value; }
		int get(int index) { return values[index]; }
		void set(int index, int value) { values[index] = value; }
		int size() { return size; }
		int[] toArray() {
			int[] copy = new int[size];
			System.arraycopy(values, 0, copy, 0, size);
			return copy;
		}
		private void ensure(int required) {
			if(required <= values.length) return;
			int[] grown = new int[Math.max(required, values.length * 2)];
			System.arraycopy(values, 0, grown, 0, size);
			values = grown;
		}
	}
}
