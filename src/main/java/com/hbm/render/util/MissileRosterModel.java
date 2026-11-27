package com.hbm.render.util;

import java.util.EnumMap;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.hbm.items.weapon.MissileDefinition;

/** Block-scale exterior models based on recognizable silhouettes and finishes. */
public final class MissileRosterModel {
	private static final int SIDES = 12;
	private static final Map<MissileDefinition, MissileRosterModel> MODELS = new EnumMap<>(MissileDefinition.class);
	private static final MissileRosterModel DAMAGED_TITAN = new MissileRosterModel(
			10.6F, 0.65F, Nose.STEPPED, Tail.BARE, Paint.RUSTED, 0.24F, 0.78F);

	static {
		register(MissileDefinition.V2, 5.8F, 0.47F, Nose.ROUNDED, Tail.FINS, Paint.TEST_PATTERN, 0.3F, 0.72F);
		register(MissileDefinition.REDSTONE, 6.5F, 0.43F, Nose.CONE, Tail.FINS, Paint.OLIVE, 0.24F, 0.76F);
		register(MissileDefinition.R12, 7.1F, 0.46F, Nose.CONE, Tail.FINS, Paint.OLIVE, 0.18F, 0.81F);
		register(MissileDefinition.THOR, 7.5F, 0.49F, Nose.CONE, Tail.BARE, Paint.WHITE, 0.24F, 0.71F);
		register(MissileDefinition.JUPITER, 7F, 0.61F, Nose.BLUNT, Tail.BARE, Paint.WHITE, 0.18F, 0.73F);
		register(MissileDefinition.R7, 10.7F, 0.57F, Nose.TAPER, Tail.BOOSTERS, Paint.SOVIET, 0.36F, 0.72F);
		register(MissileDefinition.ATLAS_D, 9.7F, 0.64F, Nose.BLUNT, Tail.SKIRT, Paint.ATLAS, 0.18F, 0.77F);
		register(MissileDefinition.ATLAS_F, 9.9F, 0.6F, Nose.CONE, Tail.SKIRT, Paint.ATLAS, 0.2F, 0.78F);
		register(MissileDefinition.SCUD, 6.2F, 0.44F, Nose.TAPER, Tail.FINS, Paint.OLIVE, 0.21F, 0.75F);
		register(MissileDefinition.MINUTEMAN_I, 8F, 0.47F, Nose.TAPER, Tail.BARE, Paint.WHITE, 0.35F, 0.78F);
		register(MissileDefinition.TITAN_II, 10.6F, 0.65F, Nose.STEPPED, Tail.BARE, Paint.TITAN, 0.24F, 0.78F);
		register(MissileDefinition.SERGEANT, 4F, 0.37F, Nose.CONE, Tail.FINS, Paint.OLIVE, 0.22F, 0.79F);
		register(MissileDefinition.PERSHING, 5.8F, 0.39F, Nose.STEPPED, Tail.FINS, Paint.WHITE, 0.27F, 0.67F);
		register(MissileDefinition.SPARTAN, 5.3F, 0.49F, Nose.TAPER, Tail.FINS, Paint.WHITE, 0.25F, 0.77F);
		register(MissileDefinition.MINUTEMAN_II, 8.4F, 0.47F, Nose.CONE, Tail.BARE, Paint.WHITE, 0.32F, 0.8F);
		register(MissileDefinition.MINUTEMAN_III, 9.1F, 0.49F, Nose.BLUNT, Tail.BARE, Paint.TITAN, 0.38F, 0.81F);
		register(MissileDefinition.LANCE, 4.4F, 0.32F, Nose.CONE, Tail.FINS, Paint.OLIVE, 0.2F, 0.75F);
		register(MissileDefinition.R36M, 10.7F, 0.68F, Nose.TAPER, Tail.BARE, Paint.SOVIET, 0.27F, 0.78F);
		register(MissileDefinition.DF5, 10.9F, 0.62F, Nose.CONE, Tail.BARE, Paint.WHITE, 0.26F, 0.76F);
		register(MissileDefinition.TOMAHAWK, 5.2F, 0.37F, Nose.BLUNT, Tail.WINGS, Paint.GRAY, 0.2F, 0.8F);
		register(MissileDefinition.TOPOL, 8.9F, 0.48F, Nose.TAPER, Tail.BARE, Paint.OLIVE, 0.35F, 0.7F);
		register(MissileDefinition.PEACEKEEPER, 9.5F, 0.59F, Nose.BLUNT, Tail.BARE, Paint.TITAN, 0.32F, 0.78F);
		register(MissileDefinition.TRIDENT_II, 7.6F, 0.7F, Nose.BLUNT, Tail.BARE, Paint.NAVAL, 0.3F, 0.74F);
		register(MissileDefinition.DF21, 6.6F, 0.43F, Nose.STEPPED, Tail.BARE, Paint.OLIVE, 0.22F, 0.68F);
		register(MissileDefinition.ATACMS, 3.9F, 0.48F, Nose.ROUNDED, Tail.CLIPPED_FINS, Paint.OLIVE, 0.2F, 0.75F);
		register(MissileDefinition.TOPOL_M, 9.3F, 0.53F, Nose.CONE, Tail.BARE, Paint.GRAY, 0.34F, 0.67F);
		register(MissileDefinition.SHAHAB3, 7.3F, 0.46F, Nose.STEPPED, Tail.FINS, Paint.SAND, 0.2F, 0.73F);
		register(MissileDefinition.DF31, 8.7F, 0.51F, Nose.CONE, Tail.BARE, Paint.WHITE, 0.26F, 0.69F);
		register(MissileDefinition.ISKANDER, 4.8F, 0.51F, Nose.ROUNDED, Tail.CLIPPED_FINS, Paint.OLIVE, 0.22F, 0.7F);
		register(MissileDefinition.M51, 8.1F, 0.72F, Nose.ROUNDED, Tail.BARE, Paint.NAVAL, 0.27F, 0.72F);
		register(MissileDefinition.YARS, 9.5F, 0.55F, Nose.TAPER, Tail.BARE, Paint.OLIVE, 0.4F, 0.69F);
		register(MissileDefinition.AGNI_V, 8.5F, 0.56F, Nose.TAPER, Tail.BARE, Paint.SAND, 0.32F, 0.72F);
		register(MissileDefinition.DF26, 8.3F, 0.49F, Nose.BICONIC, Tail.BARE, Paint.GRAY, 0.25F, 0.7F);
		register(MissileDefinition.SHAHEEN_III, 7.7F, 0.51F, Nose.STEPPED, Tail.BARE, Paint.SAND, 0.28F, 0.73F);
		register(MissileDefinition.HWASONG_15, 10.3F, 0.58F, Nose.BICONIC, Tail.BARE, Paint.NORTH_KOREAN, 0.21F, 0.76F);
		register(MissileDefinition.DF17, 7.3F, 0.43F, Nose.GLIDER, Tail.CLIPPED_FINS, Paint.OLIVE, 0.23F, 0.59F);
		register(MissileDefinition.DF41, 10.2F, 0.61F, Nose.BLUNT, Tail.BARE, Paint.GRAY, 0.25F, 0.78F);
		register(MissileDefinition.SARMAT, 11.5F, 0.76F, Nose.BLUNT, Tail.BARE, Paint.WHITE, 0.35F, 0.81F);
		register(MissileDefinition.SENTINEL, 9.3F, 0.59F, Nose.BICONIC, Tail.BARE, Paint.TITAN, 0.29F, 0.71F);
		register(MissileDefinition.CPS, 7.4F, 0.51F, Nose.GLIDER, Tail.BARE, Paint.TITAN, 0.32F, 0.61F);
		register(MissileDefinition.ORESHNIK, 9F, 0.54F, Nose.STEPPED, Tail.BARE, Paint.SOVIET, 0.3F, 0.74F);
	}

	public final float height;
	private final float radius;
	private final Nose nose;
	private final Tail tail;
	private final Paint paint;
	private final float stage;
	private final float shoulder;
	private int displayList;

	private MissileRosterModel(float height, float radius, Nose nose, Tail tail, Paint paint, float stage, float shoulder) {
		this.height = height;
		this.radius = radius;
		this.nose = nose;
		this.tail = tail;
		this.paint = paint;
		this.stage = stage;
		this.shoulder = shoulder;
	}

	private static void register(MissileDefinition missile, float height, float radius, Nose nose, Tail tail,
			Paint paint, float stage, float shoulder) {
		MODELS.put(missile, new MissileRosterModel(height, radius, nose, tail, paint, stage, shoulder));
	}

	public static MissileRosterModel get(MissileDefinition missile) {
		return MODELS.get(missile);
	}

	public static MissileRosterModel damagedTitan() {
		return DAMAGED_TITAN;
	}

	/** Uniform pixel scale that fits the rotated model at every spin angle. */
	public double inventoryScale() {
		return 7.5D * Math.sqrt(2D) / (height / 2D + radialExtent());
	}

	public float radialExtent() {
		float radialExtent = radius * (nose == Nose.GLIDER ? 1.45F : 1F);
		switch(tail) {
		case FINS: radialExtent = radius * 1.95F; break;
		case CLIPPED_FINS: radialExtent = radius * 1.55F; break;
		case WINGS: radialExtent = radius * 3.9F; break;
		case SKIRT: radialExtent = Math.max(radialExtent, radius * 1.25F); break;
		case BOOSTERS: radialExtent = radius * 2.08F; break;
		default: break;
		}
		return radialExtent;
	}

	public void render() {
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_COLOR_MATERIAL);
		GL11.glEnable(GL11.GL_NORMALIZE);
		GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
		if(displayList == 0) {
			displayList = GL11.glGenLists(1);
			if(displayList != 0) {
				GL11.glNewList(displayList, GL11.GL_COMPILE);
				build();
				GL11.glEndList();
			}
		}
		if(displayList == 0) build();
		else GL11.glCallList(displayList);
		GL11.glPopAttrib();
	}

	private void build() {
		float lower = radius * (nose == Nose.ROUNDED && tail == Tail.FINS ? 0.67F : 0.92F);
		float skirt = radius * (tail == Tail.SKIRT ? 1.25F : 1F);
		ring(0F, lower * 0.66F, 0.06F, lower, Paint.NOZZLE, 0, 0);
		cap(lower * 0.66F);
		ring(0.06F, lower, 0.16F, skirt, paint.body, 0, 0);
		ring(0.16F, skirt, stage, radius, paint.body, 0, 0);
		ring(stage, radius, stage + 0.022F, radius, paint.band, 0, 0);
		float upper = radius * (nose == Nose.STEPPED ? 0.80F : 0.95F);
		ring(stage + 0.022F, radius, shoulder - 0.045F, upper, paint.body, 0, 0);
		ring(shoulder - 0.045F, upper, shoulder, upper, paint.band, 0, 0);

		float rise = 1F - shoulder;
		switch(nose) {
		case ROUNDED:
			ring(shoulder, upper, shoulder + rise * 0.35F, upper * 0.87F, paint.nose, 0, 0);
			ring(shoulder + rise * 0.35F, upper * 0.87F, shoulder + rise * 0.75F, upper * 0.50F, paint.nose, 0, 0);
			ring(shoulder + rise * 0.75F, upper * 0.50F, 1F, 0F, paint.nose, 0, 0);
			break;
		case BLUNT:
			ring(shoulder, upper, shoulder + rise * 0.70F, upper * 0.62F, paint.nose, 0, 0);
			ring(shoulder + rise * 0.70F, upper * 0.62F, shoulder + rise * 0.93F, upper * 0.40F, paint.nose, 0, 0);
			ring(shoulder + rise * 0.93F, upper * 0.40F, 1F, 0F, paint.nose, 0, 0);
			break;
		case TAPER:
			ring(shoulder, upper, shoulder + rise * 0.55F, upper * 0.44F, paint.nose, 0, 0);
			ring(shoulder + rise * 0.55F, upper * 0.44F, 1F, 0F, paint.nose, 0, 0);
			break;
		case STEPPED:
			ring(shoulder, upper, shoulder + rise * 0.20F, upper * 0.61F, paint.band, 0, 0);
			ring(shoulder + rise * 0.20F, upper * 0.61F, shoulder + rise * 0.42F, upper * 0.61F, paint.nose, 0, 0);
			ring(shoulder + rise * 0.42F, upper * 0.61F, 1F, 0F, paint.nose, 0, 0);
			break;
		case BICONIC:
			ring(shoulder, upper, shoulder + rise * 0.38F, upper * 0.52F, paint.nose, 0, 0);
			ring(shoulder + rise * 0.38F, upper * 0.52F, 1F, 0F, paint.band, 0, 0);
			break;
		case GLIDER:
			glider(shoulder, upper);
			break;
		case CONE:
			ring(shoulder, upper, 1F, 0F, paint.nose, 0, 0);
			break;
		}

		switch(tail) {
		case FINS:
			fins(4, 0.055F, 0.31F, 1.95F);
			break;
		case CLIPPED_FINS:
			fins(4, 0.09F, 0.19F, 1.55F);
			break;
		case WINGS:
			fins(4, 0.06F, 0.23F, 1.72F);
			fins(2, 0.35F, 0.60F, 3.9F);
			break;
		case BOOSTERS:
			for(int i = 0; i < 4; i++) {
				float x = i % 2 == 0 ? radius * (i == 0 ? 1.22F : -1.22F) : 0F;
				float z = i % 2 == 1 ? radius * (i == 1 ? 1.22F : -1.22F) : 0F;
				ring(0.02F, radius * 0.72F, 0.16F, radius * 0.86F, paint.band, x, z);
				ring(0.16F, radius * 0.86F, 0.53F, radius * 0.24F, paint.body, x, z);
				ring(0.53F, radius * 0.24F, 0.60F, 0F, paint.body, x, z);
			}
			break;
		default:
			break;
		}
	}

	private void ring(float y0, float r0, float y1, float r1, int color, float x, float z) {
		float slope = (r0 - r1) / ((y1 - y0) * height);
		GL11.glBegin(GL11.GL_QUADS);
		for(int i = 0; i < SIDES; i++) {
			color(paint == Paint.TEST_PATTERN && color != Paint.NOZZLE && (i / 3) % 2 == 1 ? paint.band : color);
			double a = 2D * Math.PI * i / SIDES;
			double b = 2D * Math.PI * (i + 1) / SIDES;
			GL11.glNormal3d(Math.cos((a + b) / 2D), slope, Math.sin((a + b) / 2D));
			GL11.glVertex3d(x + r0 * Math.cos(a), y0 * height, z + r0 * Math.sin(a));
			GL11.glVertex3d(x + r1 * Math.cos(a), y1 * height, z + r1 * Math.sin(a));
			GL11.glVertex3d(x + r1 * Math.cos(b), y1 * height, z + r1 * Math.sin(b));
			GL11.glVertex3d(x + r0 * Math.cos(b), y0 * height, z + r0 * Math.sin(b));
		}
		GL11.glEnd();
	}

	private void cap(float size) {
		color(0x24262A);
		GL11.glNormal3f(0F, -1F, 0F);
		GL11.glBegin(GL11.GL_TRIANGLE_FAN);
		GL11.glVertex3f(0F, 0F, 0F);
		for(int i = 0; i <= SIDES; i++) {
			double angle = 2D * Math.PI * i / SIDES;
			GL11.glVertex3d(size * Math.cos(angle), 0D, size * Math.sin(angle));
		}
		GL11.glEnd();
	}

	private void fins(int count, float root, float front, float span) {
		for(int i = 0; i < count; i++) {
			GL11.glPushMatrix();
			GL11.glRotatef(i * 360F / count, 0F, 1F, 0F);
			float inner = radius * 0.80F;
			float outside = radius * span;
			float heel = (root + (front - root) * 0.25F) * height;
			float tip = (root + (front - root) * 0.48F) * height;
			float thickness = radius * 0.07F;
			color(paint.body);
			GL11.glBegin(GL11.GL_QUADS);
			for(int side = -1; side <= 1; side += 2) {
				GL11.glNormal3f(0F, 0F, side);
				GL11.glVertex3f(inner, root * height, side * thickness);
				GL11.glVertex3f(outside, heel, 0F);
				GL11.glVertex3f(outside, tip, 0F);
				GL11.glVertex3f(inner, front * height, side * thickness);
			}
			GL11.glEnd();
			GL11.glPopMatrix();
		}
	}

	private void glider(float base, float width) {
		float length = (1F - base) * height;
		float floor = base * height;
		ring(base, width, base + (1F - base) * 0.28F, width * 0.18F, paint.band, 0, 0);
		for(int side = -1; side <= 1; side += 2) {
			color(side == 1 ? paint.nose : paint.band);
			GL11.glNormal3f(0F, 0.3F, side);
			GL11.glBegin(GL11.GL_QUADS);
			GL11.glVertex3f(-width * 1.45F, floor, side * width * 0.18F);
			GL11.glVertex3f(width * 1.45F, floor, side * width * 0.18F);
			GL11.glVertex3f(width * 0.14F, floor + length, 0F);
			GL11.glVertex3f(-width * 0.14F, floor + length, 0F);
			GL11.glEnd();

			color(paint.body);
			GL11.glNormal3f(side, 0.2F, 0F);
			GL11.glBegin(GL11.GL_TRIANGLES);
			GL11.glVertex3f(side * width * 1.45F, floor, -width * 0.18F);
			GL11.glVertex3f(side * width * 1.45F, floor, width * 0.18F);
			GL11.glVertex3f(side * width * 0.14F, floor + length, 0F);
			GL11.glEnd();
		}
	}

	private static void color(int rgb) {
		GL11.glColor3ub((byte) (rgb >> 16), (byte) (rgb >> 8), (byte) rgb);
	}

	private enum Nose { ROUNDED, BLUNT, CONE, TAPER, STEPPED, BICONIC, GLIDER }

	private enum Tail { BARE, FINS, CLIPPED_FINS, WINGS, SKIRT, BOOSTERS }

	private enum Paint {
		TEST_PATTERN(0xE0DBCC, 0xC6C6BF, 0x343839),
		OLIVE(0x697050, 0x575D43, 0xC7C3AE),
		WHITE(0xCBCDD0, 0xE3E4E1, 0x303334),
		SOVIET(0xBDBEC0, 0xAD9B7C, 0x6B7669),
		ATLAS(0xB1B9BF, 0x292D30, 0xE2E2DB),
		TITAN(0xB1B7BE, 0xE2E1D5, 0x292E34),
		GRAY(0x929AA0, 0xACB0AE, 0x535E61),
		NAVAL(0xD3D1C8, 0x535C64, 0x344555),
		SAND(0x9B936D, 0xC2B48A, 0x6E7154),
		NORTH_KOREAN(0x4E5151, 0xBCBFB9, 0xD0D1CA),
		RUSTED(0x6C5345, 0x92705B, 0x4C4B40);

		static final int NOZZLE = 0x45474B;
		final int body;
		final int nose;
		final int band;

		Paint(int body, int nose, int band) {
			this.body = body;
			this.nose = nose;
			this.band = band;
		}
	}
}
