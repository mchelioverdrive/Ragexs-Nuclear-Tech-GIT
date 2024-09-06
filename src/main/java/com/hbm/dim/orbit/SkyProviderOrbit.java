package com.hbm.dim.orbit;

import java.util.List;

import org.lwjgl.opengl.GL11;

import com.hbm.dim.CelestialBody;
import com.hbm.dim.SkyProviderCelestial;
import com.hbm.dim.SolarSystem;
import com.hbm.dim.SolarSystem.AstroMetric;
import com.hbm.dim.orbit.OrbitalStation.StationState;
import com.hbm.lib.Library;
import com.hbm.util.BobMathUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.Vec3;

public class SkyProviderOrbit extends SkyProviderCelestial {

	@Override
	public void render(float partialTicks, WorldClient world, Minecraft mc) {
		WorldProviderOrbit provider = (WorldProviderOrbit) world.provider;
		OrbitalStation station = OrbitalStation.clientStation;
		double progress = station.getTransferProgress(partialTicks);
		float orbitalTilt = 80;

		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_FOG);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_TEXTURE_2D);

		GL11.glEnable(GL11.GL_BLEND);
		RenderHelper.disableStandardItemLighting();

		OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

		
		float celestialAngle = world.getCelestialAngle(partialTicks);
		float celestialPhase = (1 - (celestialAngle + 0.5F) % 1) * 2 - 1;

		float starBrightness = world.getStarBrightness(partialTicks);

		renderStars(partialTicks, world, mc, starBrightness, celestialAngle, orbitalTilt);

		GL11.glPushMatrix();
		{

			GL11.glRotatef(orbitalTilt, 1.0F, 0.0F, 0.0F);
			GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
			GL11.glRotatef(celestialAngle * 360.0F, 1.0F, 0.0F, 0.0F);

			// digma balls
			renderDigamma(partialTicks, world, mc, celestialAngle);

			OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);

			double sunSize = SolarSystem.calculateSunSize(station.orbiting);
			if(station.state != StationState.ORBIT) {
				double sunTargetSize = SolarSystem.calculateSunSize(station.target);
				sunSize = BobMathUtil.lerp(progress, sunSize, sunTargetSize);
			}
			double coronaSize = sunSize * (3 - Library.smoothstep(Math.abs(celestialPhase), 0.7, 0.8));

			renderSun(partialTicks, world, mc, sunSize, coronaSize, 1, 0);

			CelestialBody orbiting = station.orbiting;

			List<AstroMetric> metrics;
			if(station.state == StationState.ORBIT) {
				double altitude = provider.getOrbitalAltitude(station.orbiting);
				metrics = SolarSystem.calculateMetricsFromSatellite(world, partialTicks, station.orbiting, altitude);
			} else {
				double fromAlt = provider.getOrbitalAltitude(station.orbiting);
				double toAlt = provider.getOrbitalAltitude(station.target);
				metrics = SolarSystem.calculateMetricsBetweenSatelliteOrbits(world, partialTicks, station.orbiting, station.target, fromAlt, toAlt, progress);

				if(progress > 0.5) orbiting = station.target;
			}

			renderCelestials(partialTicks, world, mc, metrics, celestialAngle, null, Vec3.createVectorHelper(0, 0, 0), 1, 1, orbiting);

		}
		GL11.glPopMatrix();


		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_FOG);

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDepthMask(true);
	}
	
}
