package com.hbm.render.loader.prepared;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.FloatBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import com.hbm.util.Compat;

/**
 * Optional GPU boundary for prepared models. Angelica is resolved without a
 * link-time dependency and all of its tracked GL entry points are cached once.
 */
abstract class PreparedModelGpuBackend {

	private static final String ANGELICA_GLSM = "com.gtnewhorizons.angelica.glsm.GLStateManager";
	private static final Selection SELECTION = select();

	static PreparedModelGpuBackend get() {
		return SELECTION.backend;
	}

	static String getDescription() {
		return SELECTION.description;
	}

	abstract int createBuffer(FloatBuffer data);
	abstract void deleteBuffer(int buffer);
	abstract void bindArrayBuffer(int buffer);
	abstract void enableClientState(int state);
	abstract void disableClientState(int state);
	abstract void vertexPointer(int size, int type, int stride, long offset);
	abstract void texCoordPointer(int size, int type, int stride, long offset);
	abstract void normalPointer(int type, int stride, long offset);
	abstract void drawArrays(int mode, int first, int count);

	private static Selection select() {
		if(!Compat.isModLoaded(Compat.MOD_ANG)) {
			return new Selection(new LwjglBackend(), "LWJGL VBO");
		}

		try {
			ClassLoader loader = PreparedModelGpuBackend.class.getClassLoader();
			Class<?> glsm = Class.forName(ANGELICA_GLSM, false, loader);
			return new Selection(new AngelicaBackend(glsm), "Angelica GLSM VBO");
		} catch(ClassNotFoundException | NoSuchMethodException | IllegalAccessException | SecurityException | LinkageError e) {
			return new Selection(null, "CPU (Angelica GLSM VBO API unavailable: " + e.getClass().getSimpleName() + ")");
		}
	}

	private static final class Selection {
		final PreparedModelGpuBackend backend;
		final String description;

		Selection(PreparedModelGpuBackend backend, String description) {
			this.backend = backend;
			this.description = description;
		}
	}

	private static final class LwjglBackend extends PreparedModelGpuBackend {

		@Override
		int createBuffer(FloatBuffer data) {
			int buffer = GL15.glGenBuffers();
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
			return buffer;
		}

		@Override void deleteBuffer(int buffer) { GL15.glDeleteBuffers(buffer); }
		@Override void bindArrayBuffer(int buffer) { GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer); }
		@Override void enableClientState(int state) { GL11.glEnableClientState(state); }
		@Override void disableClientState(int state) { GL11.glDisableClientState(state); }
		@Override void vertexPointer(int size, int type, int stride, long offset) { GL11.glVertexPointer(size, type, stride, offset); }
		@Override void texCoordPointer(int size, int type, int stride, long offset) { GL11.glTexCoordPointer(size, type, stride, offset); }
		@Override void normalPointer(int type, int stride, long offset) { GL11.glNormalPointer(type, stride, offset); }
		@Override void drawArrays(int mode, int first, int count) { GL11.glDrawArrays(mode, first, count); }
	}

	private static final class AngelicaBackend extends PreparedModelGpuBackend {

		private final MethodHandle genBuffers;
		private final MethodHandle bindBuffer;
		private final MethodHandle bufferData;
		private final MethodHandle deleteBuffers;
		private final MethodHandle enableClientState;
		private final MethodHandle disableClientState;
		private final MethodHandle vertexPointer;
		private final MethodHandle texCoordPointer;
		private final MethodHandle normalPointer;
		private final MethodHandle drawArrays;

		AngelicaBackend(Class<?> glsm) throws NoSuchMethodException, IllegalAccessException {
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			genBuffers = lookup.findStatic(glsm, "glGenBuffers", MethodType.methodType(int.class));
			bindBuffer = lookup.findStatic(glsm, "glBindBuffer", MethodType.methodType(void.class, int.class, int.class));
			bufferData = lookup.findStatic(glsm, "glBufferData", MethodType.methodType(void.class, int.class, FloatBuffer.class, int.class));
			deleteBuffers = lookup.findStatic(glsm, "glDeleteBuffers", MethodType.methodType(void.class, int.class));
			enableClientState = lookup.findStatic(glsm, "glEnableClientState", MethodType.methodType(void.class, int.class));
			disableClientState = lookup.findStatic(glsm, "glDisableClientState", MethodType.methodType(void.class, int.class));
			vertexPointer = lookup.findStatic(glsm, "glVertexPointer", MethodType.methodType(void.class, int.class, int.class, int.class, long.class));
			texCoordPointer = lookup.findStatic(glsm, "glTexCoordPointer", MethodType.methodType(void.class, int.class, int.class, int.class, long.class));
			normalPointer = lookup.findStatic(glsm, "glNormalPointer", MethodType.methodType(void.class, int.class, int.class, long.class));
			drawArrays = lookup.findStatic(glsm, "glDrawArrays", MethodType.methodType(void.class, int.class, int.class, int.class));
		}

		@Override
		int createBuffer(FloatBuffer data) {
			try {
				int buffer = (int) genBuffers.invokeExact();
				bindBuffer.invokeExact(GL15.GL_ARRAY_BUFFER, buffer);
				bufferData.invokeExact(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
				bindBuffer.invokeExact(GL15.GL_ARRAY_BUFFER, 0);
				return buffer;
			} catch(Throwable e) {
				throw operationFailure("create prepared VBO", e);
			}
		}

		@Override
		void deleteBuffer(int buffer) {
			try {
				deleteBuffers.invokeExact(buffer);
			} catch(Throwable e) {
				throw operationFailure("delete prepared VBO", e);
			}
		}

		@Override
		void bindArrayBuffer(int buffer) {
			try {
				bindBuffer.invokeExact(GL15.GL_ARRAY_BUFFER, buffer);
			} catch(Throwable e) {
				throw operationFailure("bind prepared VBO", e);
			}
		}

		@Override
		void enableClientState(int state) {
			try {
				enableClientState.invokeExact(state);
			} catch(Throwable e) {
				throw operationFailure("enable prepared client array", e);
			}
		}

		@Override
		void disableClientState(int state) {
			try {
				disableClientState.invokeExact(state);
			} catch(Throwable e) {
				throw operationFailure("disable prepared client array", e);
			}
		}

		@Override
		void vertexPointer(int size, int type, int stride, long offset) {
			try {
				vertexPointer.invokeExact(size, type, stride, offset);
			} catch(Throwable e) {
				throw operationFailure("set prepared vertex pointer", e);
			}
		}

		@Override
		void texCoordPointer(int size, int type, int stride, long offset) {
			try {
				texCoordPointer.invokeExact(size, type, stride, offset);
			} catch(Throwable e) {
				throw operationFailure("set prepared texture pointer", e);
			}
		}

		@Override
		void normalPointer(int type, int stride, long offset) {
			try {
				normalPointer.invokeExact(type, stride, offset);
			} catch(Throwable e) {
				throw operationFailure("set prepared normal pointer", e);
			}
		}

		@Override
		void drawArrays(int mode, int first, int count) {
			try {
				drawArrays.invokeExact(mode, first, count);
			} catch(Throwable e) {
				throw operationFailure("draw prepared VBO", e);
			}
		}

		private static RuntimeException operationFailure(String operation, Throwable cause) {
			if(cause instanceof RuntimeException) return (RuntimeException) cause;
			if(cause instanceof Error) throw (Error) cause;
			return new IllegalStateException("Angelica GLSM could not " + operation, cause);
		}
	}
}
