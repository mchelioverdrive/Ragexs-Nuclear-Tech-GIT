package com.hbm.render.util;

import com.hbm.render.loader.prepared.PreparedModelHandle;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.model.obj.Face;
import net.minecraftforge.client.model.obj.GroupObject;
import net.minecraftforge.client.model.obj.TextureCoordinate;
import net.minecraftforge.client.model.obj.Vertex;
import net.minecraftforge.client.model.obj.WavefrontObject;
import net.minecraftforge.client.model.IModelCustom;
import net.minecraftforge.common.util.ForgeDirection;

public class ObjUtil {

	public static void renderWithIcon(IModelCustom model, IIcon icon, Tessellator tes, float rot, boolean shadow) {
		if(model instanceof PreparedModelHandle) renderWithIcon((PreparedModelHandle) model, icon, tes, rot, shadow);
		else renderWithIcon(requireLegacyWavefront(model), icon, tes, rot, shadow);
	}

	public static void renderWithIcon(IModelCustom model, IIcon icon, Tessellator tes, float rot, float pitch, boolean shadow) {
		if(model instanceof PreparedModelHandle) renderWithIcon((PreparedModelHandle) model, icon, tes, rot, pitch, shadow);
		else renderWithIcon(requireLegacyWavefront(model), icon, tes, rot, pitch, shadow);
	}

	public static void renderWithIcon(IModelCustom model, IIcon icon, Tessellator tes, float rot, float pitch, float roll, boolean shadow) {
		if(model instanceof PreparedModelHandle) renderWithIcon((PreparedModelHandle) model, icon, tes, rot, pitch, roll, shadow);
		else renderWithIcon(requireLegacyWavefront(model), icon, tes, rot, pitch, roll, shadow);
	}

	public static void renderPartWithIcon(IModelCustom model, String name, IIcon icon, Tessellator tes, float rot, boolean shadow) {
		if(model instanceof PreparedModelHandle) renderPartWithIcon((PreparedModelHandle) model, name, icon, tes, rot, shadow);
		else renderPartWithIcon(requireLegacyWavefront(model), name, icon, tes, rot, shadow);
	}

	public static void renderPartWithIcon(IModelCustom model, String name, IIcon icon, Tessellator tes, float rot, float pitch, boolean shadow) {
		if(model instanceof PreparedModelHandle) renderPartWithIcon((PreparedModelHandle) model, name, icon, tes, rot, pitch, shadow);
		else renderPartWithIcon(requireLegacyWavefront(model), name, icon, tes, rot, pitch, shadow);
	}

	public static void renderPartWithIcon(IModelCustom model, String name, IIcon icon, Tessellator tes, float rot, float pitch, float roll, boolean shadow) {
		if(model instanceof PreparedModelHandle) renderPartWithIcon((PreparedModelHandle) model, name, icon, tes, rot, pitch, roll, shadow);
		else renderPartWithIcon(requireLegacyWavefront(model), name, icon, tes, rot, pitch, roll, shadow);
	}

	private static WavefrontObject requireLegacyWavefront(IModelCustom model) {
		if(model instanceof WavefrontObject) return (WavefrontObject) model;
		throw new IllegalArgumentException("Unsupported icon-remapped model implementation: "
				+ (model == null ? "null" : model.getClass().getName()));
	}

	public static void renderWithIcon(PreparedModelHandle model, IIcon icon, Tessellator tes, float rot, boolean shadow) {
		model.tessellateWithIcon(icon, tes, rot, 0F, 0F, shadow, null, hasColor, red, green, blue);
	}

	public static void renderWithIcon(PreparedModelHandle model, IIcon icon, Tessellator tes, float rot, float pitch, boolean shadow) {
		model.tessellateWithIcon(icon, tes, rot, pitch, 0F, shadow, null, hasColor, red, green, blue);
	}

	public static void renderWithIcon(PreparedModelHandle model, IIcon icon, Tessellator tes, float rot, float pitch, float roll, boolean shadow) {
		model.tessellateWithIcon(icon, tes, rot, pitch, roll, shadow, null, hasColor, red, green, blue);
	}

	public static void renderPartWithIcon(PreparedModelHandle model, String name, IIcon icon, Tessellator tes, float rot, boolean shadow) {
		model.tessellateWithIcon(icon, tes, rot, 0F, 0F, shadow, name, hasColor, red, green, blue);
	}

	public static void renderPartWithIcon(PreparedModelHandle model, String name, IIcon icon, Tessellator tes, float rot, float pitch, boolean shadow) {
		model.tessellateWithIcon(icon, tes, rot, pitch, 0F, shadow, name, hasColor, red, green, blue);
	}

	public static void renderPartWithIcon(PreparedModelHandle model, String name, IIcon icon, Tessellator tes, float rot, float pitch, float roll, boolean shadow) {
		model.tessellateWithIcon(icon, tes, rot, pitch, roll, shadow, name, hasColor, red, green, blue);
	}

	public static void renderWithIcon(WavefrontObject model, IIcon icon, Tessellator tes, float rot, boolean shadow) {
		renderWithIcon(model, icon, tes, rot, 0, 0, shadow);
	}

	public static void renderWithIcon(WavefrontObject model, IIcon icon, Tessellator tes, float rot, float pitch, boolean shadow) {
		renderWithIcon(model, icon, tes, rot, pitch, 0, shadow);
	}

	public static void renderWithIcon(WavefrontObject model, IIcon icon, Tessellator tes, float rot, float pitch, float roll, boolean shadow) {
		float sinRoll = MathHelper.sin(roll);
		float cosRoll = MathHelper.cos(roll);
		float sinPitch = MathHelper.sin(pitch);
		float cosPitch = MathHelper.cos(pitch);
		float sinRot = MathHelper.sin(rot);
		float cosRot = MathHelper.cos(rot);

		for(GroupObject go : model.groupObjects) {
			
			for(Face f : go.faces) {

				Vertex n = f.faceNormal;
				double normalX = n.x * cosPitch + n.y * sinPitch;
				double normalY = n.y * cosPitch - n.x * sinPitch;
				double normalZ = n.z;
				double rotatedNormalX = normalX * cosRot + normalZ * sinRot;
				double rotatedNormalZ = normalZ * cosRot - normalX * sinRot;
				tes.setNormal((float) rotatedNormalX, (float) normalY, (float) rotatedNormalZ);

				if(shadow) {
					float brightness = ((float) normalY + 0.7F) * 0.9F - (float) Math.abs(rotatedNormalX) * 0.1F + (float) Math.abs(rotatedNormalZ) * 0.1F;

					if(brightness < 0.45F)
						brightness = 0.45F;

					tes.setColorOpaque_F(brightness, brightness, brightness);
				}

				for(int i = 0; i < f.vertices.length; i++) {

					Vertex v = f.vertices[i];
					double rollY = v.y * cosRoll + v.z * sinRoll;
					double rollZ = v.z * cosRoll - v.y * sinRoll;
					double pitchX = v.x * cosPitch + rollY * sinPitch;
					float y = (float) (rollY * cosPitch - v.x * sinPitch);
					float x = (float) (pitchX * cosRot + rollZ * sinRot);
					float z = (float) (rollZ * cosRot - pitchX * sinRot);

					TextureCoordinate t = f.textureCoordinates[i];
					tes.addVertexWithUV(x, y, z, icon.getInterpolatedU(t.u * 16), icon.getInterpolatedV(t.v * 16));

					// The shoddy way of rendering a tringulated model with a
					// quad tessellator
					if(i % 3 == 2)
						tes.addVertexWithUV(x, y, z, icon.getInterpolatedU(t.u * 16), icon.getInterpolatedV(t.v * 16));
				}
			}
		}
	}

	public static void renderPartWithIcon(WavefrontObject model, String name, IIcon icon, Tessellator tes, float rot, boolean shadow) {
		renderPartWithIcon(model, name, icon, tes, rot, 0, 0, shadow);
	}

	public static void renderPartWithIcon(WavefrontObject model, String name, IIcon icon, Tessellator tes, float rot, float pitch, boolean shadow) {
		renderPartWithIcon(model, name, icon, tes, rot, pitch, 0, shadow);
	}

	public static void renderPartWithIcon(WavefrontObject model, String name, IIcon icon, Tessellator tes, float rot, float pitch, float roll, boolean shadow) {

		GroupObject go = null;

		for(GroupObject obj : model.groupObjects) {
			if(obj.name.equals(name))
				go = obj;
		}

		if(go == null)
			return;

		float sinRoll = MathHelper.sin(roll);
		float cosRoll = MathHelper.cos(roll);
		float sinPitch = MathHelper.sin(pitch);
		float cosPitch = MathHelper.cos(pitch);
		float sinRot = MathHelper.sin(rot);
		float cosRot = MathHelper.cos(rot);

		for(Face f : go.faces) {

			Vertex n = f.faceNormal;
			double normalX = n.x * cosPitch + n.y * sinPitch;
			double normalY = n.y * cosPitch - n.x * sinPitch;
			double normalZ = n.z;
			double rotatedNormalX = normalX * cosRot + normalZ * sinRot;
			double rotatedNormalZ = normalZ * cosRot - normalX * sinRot;
			tes.setNormal((float) rotatedNormalX, (float) normalY, (float) rotatedNormalZ);

			if(shadow || hasColor) {
				
				float brightness = 1.0F;
				
				if(shadow) {
					brightness = ((float) normalY * 0.3F + 0.7F) - (float) Math.abs(rotatedNormalX) * 0.1F + (float) Math.abs(rotatedNormalZ) * 0.1F;
	
					if(brightness < 0.45F)
						brightness = 0.45F;
				}

				if(hasColor) {
					tes.setColorOpaque((int)(red * brightness), (int)(green * brightness), (int)(blue * brightness));
				} else {
					tes.setColorOpaque_F(brightness, brightness, brightness);
				}
			}

			for(int i = 0; i < f.vertices.length; i++) {

				Vertex v = f.vertices[i];
				double rollY = v.y * cosRoll + v.z * sinRoll;
				double rollZ = v.z * cosRoll - v.y * sinRoll;
				double pitchX = v.x * cosPitch + rollY * sinPitch;
				float y = (float) (rollY * cosPitch - v.x * sinPitch);
				float x = (float) (pitchX * cosRot + rollZ * sinRot);
				float z = (float) (rollZ * cosRot - pitchX * sinRot);

				TextureCoordinate t = f.textureCoordinates[i];
				tes.addVertexWithUV(x, y, z, icon.getInterpolatedU(t.u * 16D), icon.getInterpolatedV(t.v * 16D));

				// The shoddy way of rendering a tringulated model with a quad
				// tessellator
				if(f.vertices.length == 3 && i % 3 == 2)
					tes.addVertexWithUV(x, y, z, icon.getInterpolatedU(t.u * 16D), icon.getInterpolatedV(t.v * 16D));
			}
		}
	}

	/** Renders preselected, untransformed OBJ groups without lookup or temporary geometry. */
	public static void renderGroupsWithIcon(GroupObject[] groups, IIcon icon, Tessellator tes, boolean shadow) {
		for(GroupObject group : groups) {
			for(Face f : group.faces) {
				Vertex n = f.faceNormal;
				tes.setNormal(n.x, n.y, n.z);

				if(shadow || hasColor) {
					float brightness = 1.0F;
					if(shadow) {
						brightness = n.y * 0.3F + 0.7F - Math.abs(n.x) * 0.1F + Math.abs(n.z) * 0.1F;
						if(brightness < 0.45F) brightness = 0.45F;
					}

					if(hasColor) {
						tes.setColorOpaque((int)(red * brightness), (int)(green * brightness), (int)(blue * brightness));
					} else {
						tes.setColorOpaque_F(brightness, brightness, brightness);
					}
				}

				for(int i = 0; i < f.vertices.length; i++) {
					Vertex v = f.vertices[i];
					TextureCoordinate t = f.textureCoordinates[i];
					double u = icon.getInterpolatedU(t.u * 16D);
					double textureV = icon.getInterpolatedV(t.v * 16D);
					tes.addVertexWithUV(v.x, v.y, v.z, u, textureV);

					if(f.vertices.length == 3 && i == 2) {
						tes.addVertexWithUV(v.x, v.y, v.z, u, textureV);
					}
				}
			}
		}
	}

	public static void renderGroupsWithIcon(PreparedModelHandle model, String[] groups, IIcon icon, Tessellator tes, boolean shadow) {
		model.tessellateGroupsWithIcon(groups, icon, tes, shadow, hasColor, red, green, blue);
	}
	
	private static int red;
	private static int green;
	private static int blue;
	private static boolean hasColor = false;
	
	public static void setColor(int color) {
		red = (color & 0xff0000) >> 16;
		green = (color & 0x00ff00) >> 8;
		blue = color & 0x0000ff;
		hasColor = true;
	}
	
	public static void setColor(int r, int g, int b) {
		red = r;
		green = g;
		blue = b;
		hasColor = true;
	}
	
	public static void clearColor() {
		hasColor = false;
	}

	// Both methods assume model is facing towards +X (EAST)
	// Why not +Z (NORTH)? Pitch doesn't rotate as you would expect in that case using the (current) draw methods
	public static float getPitch(ForgeDirection dir) {
		if (dir == ForgeDirection.UP) return (float)Math.PI * -0.5F;
		if (dir == ForgeDirection.DOWN) return (float)Math.PI * 0.5F;
		return 0;
	}

	public static float getYaw(ForgeDirection dir) {
		if (dir == ForgeDirection.NORTH) return (float)Math.PI * 0.5f;;
		if (dir == ForgeDirection.SOUTH) return (float)Math.PI * -0.5f;
		if (dir == ForgeDirection.WEST) return (float)Math.PI;
		return 0;
	}

}
