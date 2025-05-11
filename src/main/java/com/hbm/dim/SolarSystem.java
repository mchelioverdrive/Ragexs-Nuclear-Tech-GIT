package com.hbm.dim;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.NotImplementedException;

import com.hbm.config.SpaceConfig;
import com.hbm.dim.trait.CBT_Atmosphere;
import com.hbm.dim.trait.CBT_Temperature;
import com.hbm.dim.trait.CBT_Water;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.lib.RefStrings;
import com.hbm.main.MainRegistry;
import com.hbm.util.AstronomyUtil;
import com.hbm.util.BobMathUtil;

import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class SolarSystem {

	public static CelestialBody kerbol;

	// How much to scale celestial objects when rendering
	public static final double RENDER_SCALE = 90F;
	//nerfed for irl values
	public static final double SUN_RENDER_SCALE = 4F;


	public static void init() {
		// All values pulled directly from KSP, most values are auto-converted to MC friendly ones
		//the sun
		kerbol = new CelestialBody("kerbol")
			.withMassRadius(1.989e30F, 696_340) //testing irl values
			.withRotationalPeriod(432_000) //idc about rotational period, you can have that
			.withTexture("textures/environment/sun.png")
			.withShader(new ResourceLocation(RefStrings.MODID, "shaders/blackhole.frag"), 3) // Only shows when CBT_Destroyed
			.withSatellites(

				//alright, we're gonna do some math here
				//basically this shit is not actually kilometers, if it was it would look right, so we're gonna assume:
				// A. KSP values are being used (1/10th) so divided by 10, but I scaled the actual space objects down
				// from 180 to 90 so
				// that'd be
				// B. I'm not gonna do the stupid fucking time scale shit mainly because this is minecraft
				// and that ruins the game
				//My current understanding of this system is that Semi Major Axis just means distance from the sun basically

				//yk what nah just gonna put irl values in

				//mercury
				new CelestialBody("moho", SpaceConfig.mohoDimension, Body.MOHO)
					.withMassRadius(3.301e23F, 2_440)
					.withSemiMajorAxis(57_909_000)
					.withRotationalPeriod(210_000)
					.withColor(0.4863F, 0.4F, 0.3456F)
					.withBlockTextures(RefStrings.MODID + ":moho_stone", "", "", "")
					.withAxialTilt(30F)
					.withProcessingLevel(1)
					.withTraits(new CBT_Temperature(200)),

				//venus
				new CelestialBody("eve", SpaceConfig.eveDimension, Body.EVE)
					.withMassRadius(4.867e24F, 6_052)
					.withSemiMajorAxis(108_210_000) //idk what this is
					.withRotationalPeriod(80_500)
					.withColor(0.408F, 0.298F, 0.553F)
					.withBlockTextures(RefStrings.MODID + ":eve_stone_2", "", "", "")
					.withProcessingLevel(2)
					.withTraits(new CBT_Atmosphere(Fluids.EVEAIR, 5D), new CBT_Temperature(400), new CBT_Water(Fluids.MERCURY)),
					//.withSatellites(

					//	new CelestialBody("gilly")
					//		.withMassRadius(1.242e17F, 13)
					//		.withSemiMajorAxis(31_500)
					//		.withRotationalPeriod(28_255)
					//		.withTexture("hbm:textures/misc/space/planet.png")

					//),
					//venus has no moons.
					//this should be Deimos (Mars’s other moon).

				//earth
				new CelestialBody("kerbin", 0, Body.KERBIN) // overworld
					.withMassRadius(5.972e24F, 6_371)
					.withSemiMajorAxis(49_666_666) //149_598_000/3 = 49,666,666
					.withRotationalPeriod(21_549)
					.withColor(0.608F, 0.914F, 1.0F)
					.withTraits(new CBT_Atmosphere(Fluids.AIR, 1D), new CBT_Water())
					.withSatellites(

						//our atmosphere refracts light and shit so fuck you it looks right

						new CelestialBody("mun", SpaceConfig.moonDimension, Body.MUN)
							.withMassRadius(7.347e22F, 1_737)
							.withSemiMajorAxis(96_100) //384_400 / 4 = 96,100 because it could not be seen
							.withRotationalPeriod(138_984)
							.withTidalLockingTo("kerbin")
							.withBlockTextures(RefStrings.MODID + ":moon_rock", "", "", "")
						//,

						//new CelestialBody("minmus", SpaceConfig.minmusDimension, Body.MINMUS)
						//	.withMassRadius(2.646e19F, 60)
						//	.withSemiMajorAxis(47_000)
						//	.withRotationalPeriod(40_400)
						//	.withBlockTextures(RefStrings.MODID + ":minmus_stone", "", "", "")
						//	.withTraits(new CBT_Water(Fluids.MILK))

					),

				//mars
				new CelestialBody("duna", SpaceConfig.dunaDimension, Body.DUNA)
					.withMassRadius(6.417e23F, 3_390)
					.withSemiMajorAxis(227_956_000) //too big too big too big
					.withRotationalPeriod(65_518)
					.withTidalLockingTo("ike")
					.withColor(0.6471f, 0.2824f, 0.1608f)
					.withBlockTextures(RefStrings.MODID + ":duna_rock", "", "", "")
					.withProcessingLevel(1)
					.withTraits(new CBT_Atmosphere(Fluids.DUNAAIR, 0.1D))
					.withProcessingLevel(1)
					.withSatellites(

						//phobos
						new CelestialBody("ike", SpaceConfig.ikeDimension, Body.IKE)
							.withMassRadius(1.0659e16F, 11)
							.withSemiMajorAxis(9_377)
							.withBlockTextures(RefStrings.MODID + ":ike_stone", "", "", "")
							.withProcessingLevel(1)
							.withRotationalPeriod(65_518)
							.withTidalLockingTo("duna")
							.withProcessingLevel(1)
							.withTraits(new CBT_Water(Fluids.BROMINE))

					),

				//ceres
				new CelestialBody("dres", SpaceConfig.dresDimension, Body.DRES)
					.withMassRadius(9.393e20F, 473)
					.withSemiMajorAxis(413_700_000)
					.withRotationalPeriod(34_800)
					.withBlockTextures(RefStrings.MODID + ":dresbase", "", "", "")
					.withProcessingLevel(2),




				//jupiter
				new CelestialBody("jool")
					.withMassRadius(1.898e27F, 69_911) // was radius 6_000 but that just rendered too large, so density is currently incorrect
					//skibidi mode: on
					.withSemiMajorAxis(778_479_000)
					.withRotationalPeriod(36_000)
					.withColor(0.4588f, 0.6784f, 0.3059f)
					.withSatellites(

						//europa
						new CelestialBody("laythe", SpaceConfig.laytheDimension, Body.LAYTHE)
							.withMassRadius(4.799e22F, 1_560)
							.withSemiMajorAxis(671_100)
							.withRotationalPeriod(52_981)
							.withTidalLockingTo("jool")
							.withProcessingLevel(3)
							.withTraits(new CBT_Atmosphere(Fluids.AIR, 0.45D).and(Fluids.XENON, 0.15D), new CBT_Water()),

						//Ganymede
						new CelestialBody("vall") //probably
							.withMassRadius(1.4819e23F, 2_634)
							.withSemiMajorAxis(1_070_400)
							.withRotationalPeriod(105_962),

						//Callisto
						new CelestialBody("tylo") // what value is this planet gonna add???
							.withMassRadius(1.0759e23F, 2_410)
							.withSemiMajorAxis(1_882_700)
							.withRotationalPeriod(211_926),

						//Amalthea
						new CelestialBody("bop")
							.withMassRadius(2.08e18F, 83)
							.withSemiMajorAxis(181_365)
							.withRotationalPeriod(544_507),

						//Himalia
						new CelestialBody("pol")
							.withMassRadius(6.7e18F, 85)
							.withSemiMajorAxis(11_460_000)
							.withRotationalPeriod(901_902)

					),

				//saturn
				new CelestialBody("sarnus")
					.withMassRadius(5.683e26F, 58_232)
					.withSemiMajorAxis(1_429_400_000)
					.withRotationalPeriod(28_500)
					.withColor(1f, 0.6862f, 0.5882f)
					.withSatellites(

					//pan
					new CelestialBody("hale") //no
						.withMassRadius(4.95e15F, 14)
						.withSemiMajorAxis(133_584)
						.withRotationalPeriod(23_555),

					//Atlas
					new CelestialBody("ovok") //nah
						.withMassRadius(6.6e16F, 15)
						.withSemiMajorAxis(137_670)
						.withRotationalPeriod(29_440),


					//IO
					new CelestialBody("slate") //not you tho
						.withMassRadius(8.94e22F, 1_815)
						.withSemiMajorAxis(421_700)
						.withRotationalPeriod(192_771),

					//Titan
					new CelestialBody("tekto")
						.withMassRadius(1.345e23F, 2_575)
						.withSemiMajorAxis(1_221_870)
						.withRotationalPeriod(57_915)
						.withAxialTilt(25F)
						.withTraits(new CBT_Atmosphere(Fluids.TEKTOAIR, 1.5F))

				),

				//Pluto
				new CelestialBody("eeloo") //will add
					.withMassRadius(1.309e22F, 1_188)
					.withSemiMajorAxis(5_906_380)
					//5,906,380,000 int too large also isn't pluto
					// like not a fucking moon of saturn?
					.withRotationalPeriod(9_157_915)


			);

		runTests();
	}

	// Simple enum used for blocks and items
	public enum Body {
		ORBIT(""),
		KERBIN("kerbin"),
		MUN("mun"),
		MINMUS("minmus"),
		DUNA("duna"),
		MOHO("moho"),
		DRES("dres"),
		EVE("eve"),
		IKE("ike"),
		LAYTHE("laythe");
		// TEKTO("tekto");

		public String name;

		Body(String name) {
			this.name = name;
		}

		// memoising, since ore rendering would be horrendous otherwise
		private CelestialBody body;
		public CelestialBody getBody() {
			if(this == ORBIT)
				return null;

			if(body == null)
				body = CelestialBody.getBody(name);

			return body;
		}

		public int getProcessingLevel() {
			if(this == ORBIT) return 0;
			return getBody().processingLevel;
		}

		public String getStoneTexture() {
			if(this == ORBIT) return null;
			return getBody().stoneTexture;
		}

		public int getDimensionId() {
			if(this == ORBIT) return SpaceConfig.orbitDimension;
			return getBody().dimensionId;
		}
	}

	public static class AstroMetric {

		// Convert a solar system into a set of metrics defining their position and size in the sky for a given body

		public double distance;
		public double angle;
		public double apparentSize;
		public double phase;

		protected Vec3 position;

		public CelestialBody body;

		public AstroMetric(CelestialBody body, Vec3 position) {
			this.body = body;
			this.position = position;
		}

	}

	/**
	 * Celestial mechanics
	 */

	// Create an ordered list for rendering all bodies within the system, minus the parent star
	public static List<AstroMetric> calculateMetricsFromBody(World world, float partialTicks, double longitude, CelestialBody body) {
		List<AstroMetric> metrics = new ArrayList<AstroMetric>();

		// You know not the horrors I have suffered through, in order to fix tidal locking
		double offset = (double)body.getRotationalPeriod() * (longitude / 360.0);

		double ticks = ((double)world.getTotalWorldTime() + offset + partialTicks) * (double)AstronomyUtil.TIME_MULTIPLIER;

		// Get our XYZ coordinates of all bodies
		calculatePositionsRecursive(metrics, null, body.getStar(), ticks);

		// Get the metrics from a given body
		calculateMetricsFromBody(metrics, body);

		// Sort by increasing distance
		metrics.sort((a, b) -> {
			return (int)(b.distance - a.distance);
		});

		return metrics;
	}

	public static List<AstroMetric> calculateMetricsFromSatellite(World world, float partialTicks, CelestialBody orbiting, double altitude) {
		List<AstroMetric> metrics = new ArrayList<AstroMetric>();

		double ticks = ((double)world.getTotalWorldTime() + partialTicks) * (double)AstronomyUtil.TIME_MULTIPLIER;

		// Get our XYZ coordinates of all bodies
		calculatePositionsRecursive(metrics, null, orbiting.getStar(), ticks);

		// Add our orbiting satellite position
		Vec3 position = calculatePosition(orbiting, altitude, ticks);
		for(AstroMetric metric : metrics) {
			if(metric.body == orbiting) {
				position = position.addVector(metric.position.xCoord, metric.position.yCoord, metric.position.zCoord);
				break;
			}
		}

		// Get the metrics from the orbiting position
		calculateMetricsFromPosition(metrics, position);

		// Sort by increasing distance
		metrics.sort((a, b) -> {
			return (int)(b.distance - a.distance);
		});

		return metrics;
	}

	public static List<AstroMetric> calculateMetricsBetweenSatelliteOrbits(World world, float partialTicks, CelestialBody from, CelestialBody to, double fromAltitude, double toAltitude, double t) {
		List<AstroMetric> metrics = new ArrayList<AstroMetric>();

		double ticks = ((double)world.getTotalWorldTime() + partialTicks) * (double)AstronomyUtil.TIME_MULTIPLIER;

		// Get our XYZ coordinates of all bodies
		calculatePositionsRecursive(metrics, null, from.getStar(), ticks);

		// Add our orbiting satellite position
		Vec3 fromPos = calculatePosition(from, fromAltitude, ticks);
		Vec3 toPos = calculatePosition(to, toAltitude, ticks);
		for(AstroMetric metric : metrics) {
			if(metric.body == from) {
				fromPos = fromPos.addVector(metric.position.xCoord, metric.position.yCoord, metric.position.zCoord);
			}
			if(metric.body == to) {
				toPos = toPos.addVector(metric.position.xCoord, metric.position.yCoord, metric.position.zCoord);
			}
		}

		// Lerp smoothly between the two positions (maybe a fancy circular lerp somehow?)
		Vec3 position = lerp(fromPos, toPos, t);

		// Get the metrics from the orbiting position
		calculateMetricsFromPosition(metrics, position);

		// Sort by increasing distance
		metrics.sort((a, b) -> {
			return (int)(b.distance - a.distance);
		});

		return metrics;
	}

	public static double calculateDistanceBetweenTwoBodies(World world, CelestialBody from, CelestialBody to) {
		List<AstroMetric> metrics = new ArrayList<AstroMetric>();

		double ticks = (double)world.getTotalWorldTime() * (double)AstronomyUtil.TIME_MULTIPLIER;

		// Get our XYZ coordinates of all bodies
		calculatePositionsRecursive(metrics, null, from.getStar(), ticks);

		Vec3 fromPos = Vec3.createVectorHelper(0, 0, 0);
		Vec3 toPos = Vec3.createVectorHelper(0, 0, 0);
		for(AstroMetric metric : metrics) {
			if(metric.body == from) fromPos = metric.position;
			if(metric.body == to) toPos = metric.position;
		}

		return fromPos.distanceTo(toPos);
	}

	private static Vec3 lerp(Vec3 from, Vec3 to, double t) {
		double x = BobMathUtil.clampedLerp(from.xCoord, to.xCoord, t);
		double y = BobMathUtil.clampedLerp(from.yCoord, to.yCoord, t);
		double z = BobMathUtil.clampedLerp(from.zCoord, to.zCoord, t);

		return Vec3.createVectorHelper(x, y, z);
	}

	// Recursively calculate the XYZ position of all planets from polar coordinates + time
	private static void calculatePositionsRecursive(List<AstroMetric> metrics, AstroMetric parentMetric, CelestialBody body, double ticks) {
		Vec3 parentPosition = parentMetric != null ? parentMetric.position : Vec3.createVectorHelper(0, 0, 0);

		for(CelestialBody satellite : body.satellites) {
			Vec3 position = calculatePosition(satellite, ticks).addVector(parentPosition.xCoord, parentPosition.yCoord, parentPosition.zCoord);
			AstroMetric metric = new AstroMetric(satellite, position);

			metrics.add(metric);

			calculatePositionsRecursive(metrics, metric, satellite, ticks);
		}
	}

	// Calculates the position of the body around its parent
	private static Vec3 calculatePosition(CelestialBody body, double ticks) {
		// Get how far (in radians) a planet has gone around its parent
		double yearTicks = body.getOrbitalPeriod() * (double)AstronomyUtil.TICKS_IN_DAY;
		double angleRadians = 2 * Math.PI * (ticks / yearTicks);

		double x = body.semiMajorAxisKm * Math.cos(angleRadians);
		double y = body.semiMajorAxisKm * Math.sin(angleRadians);

		return Vec3.createVectorHelper(x, y, 0);
	}

	// Same but for an arbitrary satellite around a body
	private static Vec3 calculatePosition(CelestialBody body, double altitude, double ticks) {
		double orbitalPeriod = 2 * Math.PI * Math.sqrt((altitude * altitude * altitude) / (AstronomyUtil.GRAVITATIONAL_CONSTANT * body.massKg));
		orbitalPeriod /= (double)AstronomyUtil.SECONDS_IN_KSP_DAY;
		double orbitTicks = orbitalPeriod * (double)AstronomyUtil.TICKS_IN_DAY;
		double angleRadians = 2 * Math.PI * (ticks / orbitTicks);

		double x = altitude / 1000 * Math.cos(angleRadians);
		double y = altitude / 1000 * Math.sin(angleRadians);

		return Vec3.createVectorHelper(x, y, 0);
	}

	// Calculates the metrics for a given body in the system
	private static void calculateMetricsFromBody(List<AstroMetric> metrics, CelestialBody body) {
		AstroMetric from = null;
		for(AstroMetric metric : metrics) {
			if(metric.body == body) {
				from = metric;
				break;
			}
		}

		for(AstroMetric to : metrics) {
			if(from == to)
				continue;

			calculateMetric(to, from.position);
		}
	}

	private static void calculateMetricsFromPosition(List<AstroMetric> metrics, Vec3 position) {
		for(AstroMetric to : metrics) {
			calculateMetric(to, position);
		}
	}

	private static void calculateMetric(AstroMetric metric, Vec3 position) {
		// Calculate distance between bodies, for sorting
		metric.distance = position.distanceTo(metric.position);

		// Calculate apparent size, for scaling in render
		metric.apparentSize = getApparentSize(metric.body.radiusKm, metric.distance);

		// Get angle in relation to 0, 0 (sun position, origin)
		metric.angle = getApparentAngleDegrees(position, metric.position);

		metric.phase = getApparentAngleDegrees(metric.position, position) / 180.0;
	}

	private static double getApparentSize(double radius, double distance) {
		return 2D * (float)Math.atan((2D * radius) / (2D * distance)) * RENDER_SCALE;
	}

	private static double getApparentAngleDegrees(Vec3 from, Vec3 to) {
		double angleToOrigin = Math.atan2(-from.yCoord, -from.xCoord);
		double angleToTarget = Math.atan2(to.yCoord - from.yCoord, to.xCoord - from.xCoord);

		return MathHelper.wrapAngleTo180_double(Math.toDegrees(angleToOrigin - angleToTarget));
	}

	// Calculates how large to render the sun in the sky from a given vantage point
	public static double calculateSunSize(CelestialBody from) {
		if(from.parent == null) return 0;
		if(from.parent.parent != null) return calculateSunSize(from.parent);
		return getApparentSize(from.parent.radiusKm, from.semiMajorAxisKm) * SUN_RENDER_SCALE;
	}

	// Gets angle for a single planet, good for locking tidal bodies
	public static double calculateSingleAngle(World world, float partialTicks, CelestialBody from, CelestialBody to) {
		List<AstroMetric> metrics = new ArrayList<AstroMetric>();

		double ticks = ((double)world.getTotalWorldTime() + partialTicks) * (double)AstronomyUtil.TIME_MULTIPLIER;

		// Get our XYZ coordinates of all bodies
		calculatePositionsRecursive(metrics, null, from.getStar(), ticks);

		AstroMetric metricFrom = null;
		AstroMetric metricTo = null;

		for(AstroMetric metric : metrics) {
			if(metric.body == from) {
				metricFrom = metric;
			} else if(metric.body == to) {
				metricTo = metric;
			}
		}

		return getApparentAngleDegrees(metricFrom.position, metricTo.position);
	}

	public static double calculateSingleAngle(World world, float partialTicks, CelestialBody orbiting, double altitude) {
		List<AstroMetric> metrics = new ArrayList<AstroMetric>();

		double ticks = ((double)world.getTotalWorldTime() + partialTicks) * (double)AstronomyUtil.TIME_MULTIPLIER;

		// Get our XYZ coordinates of all bodies
		calculatePositionsRecursive(metrics, null, orbiting.getStar(), ticks);

		// Add our orbiting satellite position
		Vec3 from = calculatePosition(orbiting, altitude, ticks);
		Vec3 to = Vec3.createVectorHelper(0, 0, 0);
		for(AstroMetric metric : metrics) {
			if(metric.body == orbiting) {
				to = metric.position;
				from = from.addVector(to.xCoord, to.yCoord, to.zCoord);
				break;
			}
		}

		return getApparentAngleDegrees(from, to);
	}


	/**
	 * Delta-V Calcs
	 */

	// Get a number of buckets of fuel required to travel somewhere, (halved, since we're assuming bipropellant)
	public static int getCostBetween(CelestialBody from, CelestialBody to, int mass, int thrust, int isp, boolean fromOrbit, boolean toOrbit) {
		double fromDrag = getAtmosphericDrag(from.getTrait(CBT_Atmosphere.class));
		double toDrag = getAtmosphericDrag(to.getTrait(CBT_Atmosphere.class));

		double launchDV = fromOrbit ? 0 : SolarSystem.getLiftoffDeltaV(from, mass, thrust, fromDrag);
		double travelDV = SolarSystem.getDeltaVBetween(from, to);
		double landerDV = toOrbit ? 0 : SolarSystem.getLandingDeltaV(to, mass, thrust, toDrag);

		double totalDV = launchDV + travelDV + landerDV;

		return getFuelCost(totalDV, mass, isp);
	}

	public static int getFuelCost(double deltaV, int mass, int isp) {
		// Get the fraction of the rocket that must be fuel in order to achieve the deltaV
		double g0 = 9.81;
		double exhaustVelocity = isp * g0;
		double massFraction = 1 - Math.exp(-(deltaV / exhaustVelocity));

		// Get the mass of a rocket that has that fraction, and the mass of the propellant
		double totalMass = mass / (1 - massFraction);
		double propellantMass = totalMass - mass;
		double propellantVolume = propellantMass / 2; // two propellants

		return propellantVolume + 100 > Integer.MAX_VALUE ? Integer.MAX_VALUE : MathHelper.ceiling_double_int(propellantVolume * 0.01D) * 100;
	}

	private static double getAtmosphericDrag(CBT_Atmosphere atmosphere) {
		if(atmosphere == null) return 0;
		double pressure = atmosphere.getPressure();
		return Math.log(pressure + 1.0D) / 10.0D;
	}

	// Provides the deltaV required to get into orbit, ignoring losses due to atmospheric friction
	// Make sure to convert from kN to N (kilonewtons to newtons) before calling these two functions
	public static double getLiftoffDeltaV(CelestialBody body, float craftMassKg, float craftThrustN, double atmosphericDrag) {
		return calculateSurfaceToOrbitDeltaV(body, craftMassKg, craftThrustN, atmosphericDrag, false);
	}

	// Uses aerobraking if an atmosphere is present
	public static double getLandingDeltaV(CelestialBody body, float craftMassKg, float craftThrustN, double atmosphericDrag) {
		return calculateSurfaceToOrbitDeltaV(body, craftMassKg, craftThrustN, atmosphericDrag, atmosphericDrag > 0.006);
	}

	private static double calculateSurfaceToOrbitDeltaV(CelestialBody body, float craftMassKg, float craftThrustN, double atmosphericDrag, boolean lossesOnly) {
		float gravity = body.getSurfaceGravity();
		double orbitalDeltaV = Math.sqrt((AstronomyUtil.GRAVITATIONAL_CONSTANT * body.massKg) / (body.radiusKm * 1_000));
		double thrustToWeightRatio = craftThrustN / (craftMassKg * gravity);

		if(thrustToWeightRatio < 1)
			return Double.MAX_VALUE;

		// We have to find out how long the burn will take to get our "gravity tax"
		// Shorter burns have less gravity losses, meaning higher thrust is desirable
		double acceleration = (thrustToWeightRatio - 1) * gravity;
		double timeToOrbit = orbitalDeltaV / acceleration;
		double gravityLosses = gravity * timeToOrbit * 2; // No perfect burns

		if(lossesOnly)
			return gravityLosses * (1 - atmosphericDrag); // drag helps on the way down

		return orbitalDeltaV + gravityLosses * (1 + atmosphericDrag); // and hinders on the way up
	}

	// Provides the deltaV required to transfer from the orbit of one body to the orbit of another
	// Does not currently support travelling to the main body (Sol)
	// Our structure doesn't currently require this, but if it does, go annoy Mellow to add it lmao
	public static double getDeltaVBetween(CelestialBody start, CelestialBody end) {
		return calculateHohmannTransfer(start, end);
	}

	// This calculates the entire transfer cost, adding together the cost of two burns
	private static double calculateHohmannTransfer(CelestialBody start, CelestialBody end) {
		if(start == end) {
			// Transfer to self, ignore

			return 0;
		} else if(start.parent == null || end.parent == null) {
			throw new NotImplementedException("Transfers to and from solar bodies not supported");
		} else if(start.parent == end.parent) {
			// Intersystem transfer

			double firstBurnCost = calculateSingleHohmannTransfer(start.parent.massKg, start.semiMajorAxisKm, end.semiMajorAxisKm, start.massKg, start.radiusKm + AstronomyUtil.DEFAULT_ALTITUDE_KM);
			double secondBurnCost = calculateSingleHohmannTransfer(start.parent.massKg, end.semiMajorAxisKm, start.semiMajorAxisKm, end.massKg, end.radiusKm + AstronomyUtil.DEFAULT_ALTITUDE_KM);

			return firstBurnCost + secondBurnCost;
		} else if (start == end.parent) {
			// Transferring from parent body to moon

			double firstBurnCost = calculateSingleHohmannTransfer(start.massKg, start.radiusKm + AstronomyUtil.DEFAULT_ALTITUDE_KM, end.semiMajorAxisKm);
			double secondBurnCost = calculateSingleHohmannTransfer(start.massKg, end.semiMajorAxisKm, start.radiusKm + AstronomyUtil.DEFAULT_ALTITUDE_KM, end.massKg, end.radiusKm + AstronomyUtil.DEFAULT_ALTITUDE_KM);

			return firstBurnCost + secondBurnCost;
		} else if(start.parent == end) {
			// Transferring from moon to parent body

			double firstBurnCost = calculateSingleHohmannTransfer(end.massKg, start.semiMajorAxisKm, end.radiusKm + AstronomyUtil.DEFAULT_ALTITUDE_KM, start.massKg, start.radiusKm + AstronomyUtil.DEFAULT_ALTITUDE_KM);
			double secondBurnCost = calculateSingleHohmannTransfer(end.massKg, end.radiusKm + AstronomyUtil.DEFAULT_ALTITUDE_KM, start.semiMajorAxisKm);

			return firstBurnCost + secondBurnCost;
		} else {
			// Complex transfer (moon -> moon, moon -> other planet)

			CelestialBody commonParent = getCommonParent(start, end);
			CelestialBody fromBody = start;
			CelestialBody toBody = end;
			float currentFromOrbitRadius = fromBody.semiMajorAxisKm;
			float currentToOrbitRadius = toBody.semiMajorAxisKm;

			double burnCost = 0;

			// Go up the tree from start
			while(fromBody.parent != commonParent) {
				burnCost += calculateSingleHohmannTransfer(fromBody.parent.massKg, fromBody.semiMajorAxisKm, fromBody.semiMajorAxisKm, fromBody.massKg, fromBody.semiMajorAxisKm);
				currentFromOrbitRadius = fromBody.semiMajorAxisKm;
				fromBody = fromBody.parent;
			}

			// Go up the tree from end
			while(toBody.parent != commonParent) {
				burnCost += calculateSingleHohmannTransfer(toBody.parent.massKg, toBody.semiMajorAxisKm, toBody.semiMajorAxisKm, toBody.massKg, toBody.semiMajorAxisKm);
				currentToOrbitRadius = toBody.semiMajorAxisKm;
				toBody = toBody.parent;
			}

			// Transfer interplanetary
			burnCost += calculateSingleHohmannTransfer(commonParent.massKg, fromBody.semiMajorAxisKm, toBody.semiMajorAxisKm, fromBody.massKg, currentFromOrbitRadius);
			burnCost += calculateSingleHohmannTransfer(commonParent.massKg, toBody.semiMajorAxisKm, fromBody.semiMajorAxisKm, toBody.massKg, currentToOrbitRadius);

			return burnCost;
		}
	}

	private static CelestialBody getCommonParent(CelestialBody start, CelestialBody end) {
		CelestialBody startParent = start.parent;

		while(startParent != null) {
			CelestialBody endParent = end.parent;
			while(endParent != null) {
				if(startParent == endParent)
					return startParent;

				endParent = endParent.parent;
			}
			startParent = startParent.parent;
		}

		throw new InvalidParameterException("Bodies aren't in the same solar system");
	}


	// All transfer math is commutative, injection burn (getting onto the transfer orbit) takes the exact same dV as
	// the insertion burn (entering the target orbit)

	// Calculate orbit to orbit transfer around a parent body, without any need to escape an inner gravity well.
	// This is used to transfer from low orbit to a moon.
	private static double calculateSingleHohmannTransfer(float parentMassKg, float fromRadiusKm, float toRadiusKm) {
		double parentGravitationalParameter = parentMassKg * AstronomyUtil.GRAVITATIONAL_CONSTANT;

		// We're finding the dv to transfer between these circular orbits
		double startOrbitalRadius = fromRadiusKm * 1_000;
		double endOrbitalRadius = toRadiusKm * 1_000;

		// The semimajor axis of the transfer orbit (average distance of orbit)
		double transferSemiMajorAxis = (startOrbitalRadius + endOrbitalRadius) / 2;

		// Our current orbital velocity around the parent (planet orbital velocity)
		double currentOrbitalVelocity = Math.sqrt(parentGravitationalParameter / startOrbitalRadius);

		// The velocity we need to get onto our transfer orbit
		double requiredVelocity = Math.sqrt(parentGravitationalParameter * ((2 / startOrbitalRadius) - (1 / transferSemiMajorAxis)));

		// The true velocity we need to add to get onto our transfer orbit (desired energy minus the energy we already have)
		return Math.abs(requiredVelocity - currentOrbitalVelocity);
	}

	// Calculate orbit to orbit transfer around a parent body, escaping from a well.
	// This is used for interplanetary transfers.
	private static double calculateSingleHohmannTransfer(float parentMassKg, float fromRadiusKm, float toRadiusKm, float fromMassKg, float parkingOrbitRadiusKm) {
		// First we get our required velocity change ignoring the body we're currently orbiting
		double hyperbolicVelocity = calculateSingleHohmannTransfer(parentMassKg, fromRadiusKm, toRadiusKm);

		double fromGravitationalParameter = fromMassKg * AstronomyUtil.GRAVITATIONAL_CONSTANT;

		// Our current orbital radius and velocity around the starting body
		double parkingOrbitRadius = parkingOrbitRadiusKm * 1_000;
		double parkingOrbitVelocity = Math.sqrt(fromGravitationalParameter / parkingOrbitRadius);

		// The amount of energy needed to escape the start body to get onto our transfer orbit
		double escapeVelocity = Math.sqrt((2 * fromGravitationalParameter) / parkingOrbitRadius);
		double escapeHyperVelocity = Math.sqrt(hyperbolicVelocity * hyperbolicVelocity + escapeVelocity * escapeVelocity);

		// The first half of the required dV to get to our destination!
		return escapeHyperVelocity - parkingOrbitVelocity;
	}

	public static void runTests() {
		CelestialBody kerbin = CelestialBody.getBody("kerbin");
		CelestialBody eve = CelestialBody.getBody("eve");
		CelestialBody duna = CelestialBody.getBody("duna");
		CelestialBody mun = CelestialBody.getBody("mun");
		CelestialBody minmus = CelestialBody.getBody("minmus");
		CelestialBody ike = CelestialBody.getBody("ike");

		float deltaIVMass = 500_000;
		float RD180RocketThrust = 7_887 * 1_000;

		MainRegistry.logger.info("Kerbin launch cost: " + getLiftoffDeltaV(kerbin, deltaIVMass, RD180RocketThrust, 0));
		MainRegistry.logger.info("Eve launch cost: " + getLiftoffDeltaV(eve, deltaIVMass, RD180RocketThrust, 0));
		MainRegistry.logger.info("Duna launch cost: " + getLiftoffDeltaV(duna, deltaIVMass, RD180RocketThrust, 0));
		MainRegistry.logger.info("Mun launch cost: " + getLiftoffDeltaV(mun, deltaIVMass, RD180RocketThrust, 0));
		MainRegistry.logger.info("Minmus launch cost: " + getLiftoffDeltaV(minmus, deltaIVMass, RD180RocketThrust, 0));
		MainRegistry.logger.info("Ike launch cost: " + getLiftoffDeltaV(ike, deltaIVMass, RD180RocketThrust, 0));

		MainRegistry.logger.info("Kerbin -> Eve cost: " + getDeltaVBetween(kerbin, eve) + " - should be: " + (950+90+80+1330));
		MainRegistry.logger.info("Kerbin -> Duna cost: " + getDeltaVBetween(kerbin, duna) + " - should be: " + (950+130+250+360));
		MainRegistry.logger.info("Kerbin -> Ike cost: " + getDeltaVBetween(kerbin, ike) + " - should be: " + (950+130+250+30+180));
		MainRegistry.logger.info("Eve -> Duna cost: " + getDeltaVBetween(eve, duna));
		MainRegistry.logger.info("Kerbin -> Mun cost: " + getDeltaVBetween(kerbin, mun) + " - should be: " + (860+310));
		MainRegistry.logger.info("Kerbin -> Minmus cost: " + getDeltaVBetween(kerbin, minmus) + " - should be: " + (930+160));
		MainRegistry.logger.info("Mun -> Kerbin cost: " + getDeltaVBetween(mun, kerbin) + " - should be: " + (860+310));
		MainRegistry.logger.info("Minmus -> Kerbin cost: " + getDeltaVBetween(minmus, kerbin) + " - should be: " + (930+160));
		MainRegistry.logger.info("Minmus -> Ike cost: " + getDeltaVBetween(minmus, ike));

		MainRegistry.logger.info("Kerbin orbital period: " + kerbin.getOrbitalPeriod() + " - should be: " + 426);
		MainRegistry.logger.info("Eve orbital period: " + eve.getOrbitalPeriod() + " - should be: " + 261);
		MainRegistry.logger.info("Mun orbital period: " + mun.getOrbitalPeriod() + " - should be: " + 6);
	}

}
