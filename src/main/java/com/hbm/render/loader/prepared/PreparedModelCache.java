package com.hbm.render.loader.prepared;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hbm.config.ClientConfig;
import com.hbm.main.MainRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelFormatException;

/** Shared cache and resource-reload owner for compact prepared OBJ models. */
public final class PreparedModelCache {

	private static final Map<Key, PreparedModelHandle> MODELS = new LinkedHashMap<Key, PreparedModelHandle>();
	private static final Map<String, Integer> DEBUG_PARSE_COUNTS = new LinkedHashMap<String, Integer>();
	private static long cacheHits;
	private static long cacheMisses;
	private static long uploadNanos;
	private static long uploadedBytes;

	private PreparedModelCache() { }

	/** Forge WavefrontObject-compatible OBJ semantics, including its UV inset. */
	public static PreparedModelHandle get(ResourceLocation resource, boolean smoothing, boolean requestGpu) {
		return get(new Key(resource, smoothing, Format.FORGE_OBJ), requestGpu);
	}

	/** HFR-compatible OBJ semantics; unlike Forge OBJ, UVs are submitted exactly. */
	public static PreparedModelHandle getHfr(ResourceLocation resource, boolean smoothing, boolean requestGpu) {
		return get(new Key(resource, smoothing, Format.HFR_OBJ), requestGpu);
	}

	public static PreparedModelHandle getHmf(ResourceLocation resource) {
		return get(new Key(resource, false, Format.HMF), false);
	}

	public static PreparedModelHandle fromStream(String name, InputStream input, boolean smoothing) {
		ResourceLocation diagnosticName = new ResourceLocation("hbm", "stream/" + Integer.toHexString(name.hashCode()) + ".obj");
		Key key = new Key(diagnosticName, smoothing, Format.HFR_OBJ);
		return new PreparedModelHandle(key, PreparedObjParser.parse(diagnosticName, input, smoothing, false, false), false);
	}

	public static PreparedModelHandle fromHmfStream(String name, InputStream input) {
		ResourceLocation diagnosticName = new ResourceLocation("hbm", "stream/" + Integer.toHexString(name.hashCode()) + ".hmf");
		Key key = new Key(diagnosticName, false, Format.HMF);
		return new PreparedModelHandle(key, PreparedObjParser.parse(diagnosticName, input, false, true, false), false);
	}

	private static PreparedModelHandle get(Key key, boolean requestGpu) {
		synchronized(MODELS) {
			PreparedModelHandle handle = MODELS.get(key);
			if(handle != null) {
				if(metricsEnabled()) cacheHits++;
				if(requestGpu) handle.enableGpu();
				return handle;
			}
			if(metricsEnabled()) cacheMisses++;
			PreparedModelData data = parse(Minecraft.getMinecraft().getResourceManager(), key);
			handle = new PreparedModelHandle(key, data, requestGpu);
			MODELS.put(key, handle);
			return handle;
		}
	}

	public static void reloadAll(IResourceManager resourceManager) {
		long start = metricsEnabled() ? System.nanoTime() : 0L;
		int replaced = 0;
		synchronized(MODELS) {
			for(Map.Entry<Key, PreparedModelHandle> entry : MODELS.entrySet()) {
				try {
					PreparedModelData replacement = parse(resourceManager, entry.getKey());
					entry.getValue().replace(replacement);
					replaced++;
				} catch(RuntimeException e) {
					MainRegistry.logger.error("Keeping previous prepared model after reload failure: " + entry.getKey().resource, e);
				}
			}
		}
		if(metricsEnabled()) {
			long cpuBytes = 0L;
			long gpuBytes = 0L;
			for(PreparedModelHandle handle : MODELS.values()) {
				cpuBytes += handle.getRetainedCpuBytes();
				gpuBytes += handle.getUploadedGpuBytes();
			}
			MainRegistry.logger.info("Prepared model reload: " + replaced + " models, " + ((System.nanoTime() - start) / 1_000_000D)
					+ " ms, CPU " + cpuBytes + " bytes, GPU " + gpuBytes + " bytes, hits/misses " + cacheHits + "/" + cacheMisses
					+ ", backend " + PreparedModelGpuBackend.getDescription() + ", cumulative upload "
					+ (uploadNanos / 1_000_000D) + " ms (" + uploadedBytes + " bytes)");
			for(Map.Entry<String, Integer> count : DEBUG_PARSE_COUNTS.entrySet()) {
				MainRegistry.logger.info("Prepared model parses: " + count.getKey() + " = " + count.getValue());
			}
		}
	}

	private static PreparedModelData parse(IResourceManager resourceManager, Key key) {
		long start = metricsEnabled() ? System.nanoTime() : 0L;
		InputStream input = null;
		try {
			IResource resource = resourceManager.getResource(key.resource);
			input = resource.getInputStream();
			PreparedModelData data = PreparedObjParser.parse(key.resource, input, key.smoothing, key.format == Format.HMF,
					key.format == Format.FORGE_OBJ);
			input = null; // parser closes its reader and underlying stream
			if(metricsEnabled()) {
				String name = key.toString();
				Integer count = DEBUG_PARSE_COUNTS.get(name);
				DEBUG_PARSE_COUNTS.put(name, count == null ? 1 : count + 1);
				MainRegistry.logger.info("Prepared model parsed " + name + " in " + ((System.nanoTime() - start) / 1_000_000D)
						+ " ms; " + data.getVertexCount() + " vertices, " + data.retainedBytes + " retained CPU bytes");
			}
			return data;
		} catch(IOException e) {
			throw new ModelFormatException("IO exception reading prepared model '" + key.resource + "'", e);
		} finally {
			if(input != null) {
				try {
					input.close();
				} catch(IOException ignored) { }
			}
		}
	}

	static boolean metricsEnabled() {
		return ClientConfig.DEBUG_PREPARED_MODEL_METRICS.get();
	}

	static void recordUpload(long nanos, long bytes) {
		uploadNanos += nanos;
		uploadedBytes += bytes;
	}

	private enum Format { FORGE_OBJ, HFR_OBJ, HMF }

	static final class Key {
		final ResourceLocation resource;
		final boolean smoothing;
		final Format format;

		Key(ResourceLocation resource, boolean smoothing, Format format) {
			this.resource = resource;
			this.smoothing = smoothing;
			this.format = format;
		}

		@Override
		public int hashCode() {
			return 31 * (31 * resource.hashCode() + (smoothing ? 1 : 0)) + format.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if(this == obj) return true;
			if(!(obj instanceof Key)) return false;
			Key other = (Key) obj;
			return smoothing == other.smoothing && format == other.format && resource.equals(other.resource);
		}

		@Override
		public String toString() {
			return resource + " [format=" + format + ", smoothing=" + smoothing + "]";
		}
	}
}
