package com.hbm.entity.effect;

import java.awt.Color;
import java.util.ArrayList;

import com.hbm.dim.CelestialBody;
import com.hbm.dim.trait.CBT_Atmosphere;
import com.hbm.main.MainRegistry;
import com.hbm.explosion.nuclear.BurstType;
import com.hbm.explosion.nuclear.NuclearBurstContext;
import com.hbm.explosion.nuclear.NuclearBurstResolver;
import com.hbm.render.entity.effect.NukeCloudClient;
import com.hbm.util.TrackerUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/*
 * Toroidial Convection Simulation Explosion Effect
 * Tor                             Ex
 */
public class EntityNukeTorex extends Entity {

	public double coreHeight = 3;
	public double convectionHeight = 3;
	public double torusWidth = 3;
	public double rollerSize = 1;
	public double heat = 1;
	public double lastSpawnY = - 1;
	public ArrayList<Cloudlet> cloudlets = new ArrayList<>();
	private long nextCloudletIdentity;
	private double tickSimulationSpeed;
	private int tickColorType;
	//public static int cloudletLife = 200;

	public boolean didPlaySound = false;
	public boolean didShake = false;

	private static final BurstType[] BURST_TYPES = BurstType.values();
	private boolean hasSufficientPressure = true;
	private double resolvedBurstHeight;
	private double resolvedFireballRadius;
	private double resolvedGroundCoupling = 1D;

	public EntityNukeTorex(World world) {
		super(world);
		this.ignoreFrustumCheck = true;
		this.setSize(1F, 50F);

		// Check current atmospheric pressure (wow, so difficult)
		CBT_Atmosphere atmosphere = CelestialBody.getTrait(world, CBT_Atmosphere.class);
		if(CelestialBody.inOrbit(world) || atmosphere == null || atmosphere.getPressure() < 0.01) {
			hasSufficientPressure = false;
		}
	}

	@Override
	protected void entityInit() {
		this.dataWatcher.addObject(10, new Float(1));
		this.dataWatcher.addObject(11, new Integer(0));
		this.dataWatcher.addObject(12, new Integer(BurstType.SURFACE.ordinal()));
		this.dataWatcher.addObject(13, new Float(1F));
		this.dataWatcher.addObject(14, new Float(0F));
		this.dataWatcher.addObject(15, new Float(0F));
		this.dataWatcher.addObject(16, new Float(1F));
	}

	@Override @SideOnly(Side.CLIENT) public int getBrightnessForRender(float interp) { return 15728880; }
	@Override public float getBrightness(float interp) { return 1.0F; }

	@Override
	public void onUpdate() {
		if(isContainedVisual()) { if(!worldObj.isRemote || ticksExisted > 2) setDead(); return; }

		if(worldObj.isRemote) updateClient();
		if(!worldObj.isRemote && this.ticksExisted > getMaxAge()) this.setDead();
	}

	@SideOnly(Side.CLIENT)
	private void updateClient() {
		double s = 1.5;
		double cs = 1.5;
		int maxAge = getMaxAge();
		boolean airburst = isAirburstVisual();
		tickSimulationSpeed = getSimulationSpeed();
		tickColorType = dataWatcher.getWatchableObjectInt(11);
		NukeCloudClient.State visual = NukeCloudClient.beginTick(this, hasSufficientPressure);

		if(ticksExisted == 1) this.setScale((float) s);

		if(lastSpawnY == -1) lastSpawnY = airburst ? posY : posY - 3;

		if(ticksExisted < 100) this.worldObj.lastLightningBolt = 2;

		int spawnTarget = airburst ? (int)Math.floor(posY) : Math.max(worldObj.getHeightValue((int) Math.floor(posX), (int) Math.floor(posZ)) - 3, 1);
		double moveSpeed = airburst ? 0D : 0.5D;

		if(Math.abs(spawnTarget - lastSpawnY) < moveSpeed) {
			lastSpawnY = spawnTarget;
		} else {
			lastSpawnY += moveSpeed * Math.signum(spawnTarget - lastSpawnY);
		}

		if(!hasSufficientPressure) {
			// pondering my glowing orb
			if(ticksExisted < 40) {
				double range = (ticksExisted + rand.nextDouble()) * 0.6;
				int toSpawn = ticksExisted * 5;
				int lifetime = Math.max(800 - ticksExisted * 10, 40);

				for(int i = 0; i < toSpawn; i++) {
					if(!visual.allowBirth(TorexType.STARFISH, toSpawn, Math.min(lifetime, 20))) continue;
					double x = posX + rand.nextGaussian() * range;
					double y = posY + rand.nextGaussian() * range;
					double z = posZ + rand.nextGaussian() * range;
					Cloudlet cloud = new Cloudlet(x, y, z, (float)(rand.nextDouble() * 2D * Math.PI), 0, lifetime, TorexType.STARFISH);
					cloud.setScale(4F, 5F);
					cloud.setMotion(ticksExisted < 25 && rand.nextBoolean() ? 0.5 : 0.05);
					visual.add(cloud);
				}
			}

			for(int i = 0; i < cloudlets.size(); i++) cloudlets.get(i).update();
			coreHeight += 0.15 / s;
			torusWidth += 0.05 / s;
			rollerSize = torusWidth * 0.8;
			convectionHeight = coreHeight + rollerSize;

			int maxHeat = (int) (50 * cs);
			heat = maxHeat - Math.pow((maxHeat * this.ticksExisted) / maxAge, 1);

			visual.finishTick();

			return;
		}

		// spawn mush clouds
		double range = (torusWidth - rollerSize) * 0.25;
		double simSpeed = tickSimulationSpeed;
		int toSpawn = (int) Math.ceil(10 * simSpeed * simSpeed * (airburst ? 0.35D + getGroundCoupling() * 0.65D : 1D));
		int lifetime = Math.min((ticksExisted * ticksExisted) + 200, maxAge - ticksExisted + 200);

		for(int i = 0; i < toSpawn; i++) {
			if(!visual.allowBirth(TorexType.STANDARD, toSpawn, lifetime)) continue;
			double x = posX + rand.nextGaussian() * range;
			double z = posZ + rand.nextGaussian() * range;
			Cloudlet cloud = new Cloudlet(x, lastSpawnY, z, (float)(rand.nextDouble() * 2D * Math.PI), 0, lifetime);
			cloud.setScale(1F + this.ticksExisted * 0.005F * (float) cs, 5F * (float) cs);
			visual.add(cloud);
		}

		// spawn shock clouds
		if(ticksExisted < 150) {

			int cloudCount = ticksExisted * 5;
			int shockLife = Math.max(300 - ticksExisted * 20, 50);

			for(int i = 0; i < cloudCount; i++) {
				if(!visual.allowBirth(TorexType.SHOCK, cloudCount, shockLife)) continue;
				double radius = (ticksExisted * 1.5 + rand.nextDouble()) * 1.5;
				float rot = (float) (Math.PI * 2 * rand.nextDouble());
				double x = radius * MathHelper.cos(rot) + posX;
				double z = -radius * MathHelper.sin(rot) + posZ;
				visual.add(new Cloudlet(x, airburst ? posY : worldObj.getHeightValue((int)x + 1, (int)z), z, rot, 0, shockLife, TorexType.SHOCK)
					.setScale(7F, 2F).setMotion(ticksExisted > 15 ? 0.75 : 0));
			}

			if(!didPlaySound) {
				if(MainRegistry.proxy.me() != null && MainRegistry.proxy.me().getDistanceToEntity(this) < (ticksExisted * 1.5 + 1) * 1.5) {
					MainRegistry.proxy.playSoundClient(posX, posY, posZ, "hbm:weapon.nuclearExplosion", 10_000F, 1F);
					didPlaySound = true;
				}
			}
		}

		// spawn ring clouds
		if(ticksExisted < 130 * s) {
			lifetime *= s;
			for(int i = 0; i < 2; i++) {
				if(!visual.allowBirth(TorexType.RING, 2, lifetime)) continue;
				Cloudlet cloud = new Cloudlet(posX, posY + coreHeight, posZ, (float)(rand.nextDouble() * 2D * Math.PI), 0, lifetime, TorexType.RING);
				cloud.setScale(1F + this.ticksExisted * 0.0025F * (float) (cs * cs), 3F * (float) (cs * cs));
				visual.add(cloud);
			}
		}

		// spawn condensation clouds
		if(ticksExisted > 130 * s && ticksExisted < 600 * s) {

			for(int i = 0; i < 20; i++) {
				for(int j = 0; j < 4; j++) {
					if(!visual.allowBirth(TorexType.CONDENSATION, ticksExisted > 200 * s ? 160 : 80, (int)((20 + ticksExisted / 10) * 1.1), j)) continue;
					float angle = (float) (Math.PI * 2 * rand.nextDouble());
					double radius = (torusWidth + rollerSize * (5 + rand.nextDouble())) * MathHelper.cos((float)(Math.PI / 45 * j));
					double x = radius * MathHelper.cos(angle);
					double z = -radius * MathHelper.sin(angle);
					Cloudlet cloud = new Cloudlet(posX + x, posY + coreHeight - 5 + j * s, posZ + z, angle, 0, (int) ((20 + ticksExisted / 10) * (1 + rand.nextDouble() * 0.1)), TorexType.CONDENSATION);
					cloud.setScale(0.125F * (float) (cs), 3F * (float) (cs));
					cloud.visualLane = j;
					visual.add(cloud);
				}
			}
		}
		if(ticksExisted > 200 * s && ticksExisted < 600 * s) {

			for(int i = 0; i < 20; i++) {
				for(int j = 0; j < 4; j++) {
					if(!visual.allowBirth(TorexType.CONDENSATION, ticksExisted > 200 * s ? 160 : 80, (int)((20 + ticksExisted / 10) * 1.1), j + 4)) continue;
					float angle = (float) (Math.PI * 2 * rand.nextDouble());
					double radius = (torusWidth + rollerSize * (3 + rand.nextDouble() * 0.5)) * MathHelper.cos((float)(Math.PI / 45 * j));
					double x = radius * MathHelper.cos(angle);
					double z = -radius * MathHelper.sin(angle);
					Cloudlet cloud = new Cloudlet(posX + x, posY + coreHeight + 25 + j * cs, posZ + z, angle, 0, (int) ((20 + ticksExisted / 10) * (1 + rand.nextDouble() * 0.1)), TorexType.CONDENSATION);
					cloud.setScale(0.125F * (float) (cs), 3F * (float) (cs));
					cloud.visualLane = j + 4;
					visual.add(cloud);
				}
			}
		}

		for(int i = 0; i < cloudlets.size(); i++) cloudlets.get(i).update();
		coreHeight += 0.15 / s;
		torusWidth += 0.05 / s;
		rollerSize = torusWidth * 0.35;
		convectionHeight = coreHeight + rollerSize;

		int maxHeat = (int) (50 * cs);
		heat = maxHeat - Math.pow((maxHeat * this.ticksExisted) / maxAge, 1);

		visual.finishTick();
	}

	@Override
	public void setDead() {
		super.setDead();
		if(worldObj.isRemote) releaseClient();
	}

	@SideOnly(Side.CLIENT)
	private void releaseClient() { NukeCloudClient.release(this); }

	public EntityNukeTorex setScale(float scale) {
		if(!worldObj.isRemote) getDataWatcher().updateObject(10, scale);
		this.coreHeight = this.coreHeight / 1.5D * scale;
		this.convectionHeight = this.convectionHeight / 1.5D * scale;
		this.torusWidth = this.torusWidth / 1.5D * scale;
		this.rollerSize = this.rollerSize / 1.5D * scale;
		return this;
	}

	private boolean isAirburstVisual() { return getBurstType() == BurstType.AIR; }
	public boolean isContainedVisual() { return getBurstType() == BurstType.SUBSURFACE && this.dataWatcher.getWatchableObjectFloat(16) <= 0F; }
	private BurstType getBurstType() { int type = this.dataWatcher.getWatchableObjectInt(12); return type >= 0 && type < BURST_TYPES.length ? BURST_TYPES[type] : BurstType.SURFACE; }
	private double getGroundCoupling() { return this.dataWatcher.getWatchableObjectFloat(13); }

	private EntityNukeTorex applyBurstContext(NuclearBurstContext context) {
		this.resolvedBurstHeight = context.burstHeight; this.resolvedFireballRadius = context.fireballRadius; this.resolvedGroundCoupling = context.groundCoupling;
		this.dataWatcher.updateObject(12, context.burstType.ordinal()); this.dataWatcher.updateObject(13, (float)context.groundCoupling); this.dataWatcher.updateObject(14, (float)context.burstHeight); this.dataWatcher.updateObject(15, (float)context.fireballRadius); this.dataWatcher.updateObject(16, (float)context.atmosphericReleaseFactor);
		if(context.burstType == BurstType.VACUUM) this.hasSufficientPressure = false;
		return this;
	}

	public EntityNukeTorex setType(int type) {
		this.dataWatcher.updateObject(11, type);
		return this;
	}

	public double getSimulationSpeed() {

		int lifetime = getMaxAge();
		int simSlow = lifetime / 4;
		int simStop = lifetime / 2;
		int life = EntityNukeTorex.this.ticksExisted;

		if(life > simStop) {
			return 0D;
		}

		if(life > simSlow) {
			return 1D - ((double)(life - simSlow) / (double)(simStop - simSlow));
		}

		return 1.0D;
	}

	public double getScale() {
		return this.dataWatcher.getWatchableObjectFloat(10);
	}

	public double getSaturation() {
		double d = (double) this.ticksExisted / (double) this.getMaxAge();
		return 1D - (d * d * d * d);
	}

	public double getGreying() {

		int lifetime = getMaxAge();
		int greying = lifetime * 3 / 4;

		if(ticksExisted > greying) {
			return 1 + ((double)(ticksExisted - greying) / (double)(lifetime - greying));
		}

		return 1D;
	}

	public float getAlpha() {

		int lifetime = getMaxAge();
		int fadeOut = lifetime * 3 / 4;
		int life = EntityNukeTorex.this.ticksExisted;

		if(life > fadeOut) {
			float fac = (float)(life - fadeOut) / (float)(lifetime - fadeOut);
			return 1F - fac;
		}

		return 1.0F;
	}

	public int getMaxAge() {
		double s = this.getScale();
		return (int) (45 * 20 * s);
	}

	public class Cloudlet {

		public final long identity = nextCloudletIdentity++;
		public double sortDistance;
		public int visualLane;
		private final double angleSin, angleCos;
		private final int rainbow;

		public double posX;
		public double posY;
		public double posZ;
		public double prevPosX;
		public double prevPosY;
		public double prevPosZ;
		public double motionX;
		public double motionY;
		public double motionZ;
		public int age;
		public int cloudletLife;
		public float angle;
		public boolean isDead = false;
		float rangeMod = 1.0F;
		public float colorMod = 1.0F;
		public Vec3 color;
		public Vec3 prevColor;
		public TorexType type;

		public Cloudlet(double posX, double posY, double posZ, float angle, int age, int maxAge) {
			this(posX, posY, posZ, angle, age, maxAge, TorexType.STANDARD);
		}

		public Cloudlet(double posX, double posY, double posZ, float angle, int age, int maxAge, TorexType type) {
			this.posX = posX;
			this.posY = posY;
			this.posZ = posZ;
			this.age = age;
			this.cloudletLife = maxAge;
			this.angle = angle;
			this.angleSin = MathHelper.sin(angle);
			this.angleCos = MathHelper.cos(angle);
			this.rainbow = Color.HSBtoRGB(angle / 2F / (float)Math.PI, 1F, 1F);
			this.color = Vec3.createVectorHelper(0, 0, 0);
			this.prevColor = Vec3.createVectorHelper(0, 0, 0);
			this.rangeMod = 0.3F + rand.nextFloat() * 0.7F;
			this.colorMod = 0.8F + rand.nextFloat() * 0.2F;
			this.type = type;

			this.updateColor();
		}

		private void update() {

			age++;

			if(age > cloudletLife) {
				this.isDead = true;
			}

			this.prevPosX = this.posX;
			this.prevPosY = this.posY;
			this.prevPosZ = this.posZ;

			double dx = EntityNukeTorex.this.posX - this.posX;
			double dz = EntityNukeTorex.this.posZ - this.posZ;
			// Vec3.lengthVector/normalize use float-rounded MathHelper.sqrt_double in 1.7.10.
			double simPosX = EntityNukeTorex.this.posX + MathHelper.sqrt_double(dx * dx + dz * dz);

			if(this.type == TorexType.STANDARD) {
				setRollMotion(simPosX, false);
				double factor = MathHelper.clamp_double((this.posY - EntityNukeTorex.this.posY) / coreHeight, 0, 1);
				double liftY = EntityNukeTorex.this.posY + convectionHeight - this.posY;
				double length = MathHelper.sqrt_double(dx * dx + liftY * liftY + dz * dz);
				double liftScale = MathHelper.clamp_double(1D - (simPosX - (EntityNukeTorex.this.posX + torusWidth)), 0, 1);
				double lx = length < 1.0E-4D ? 0 : dx / length * liftScale;
				double ly = length < 1.0E-4D ? 0 : liftY / length * liftScale;
				double lz = length < 1.0E-4D ? 0 : dz / length * liftScale;
				this.motionX = motionX * factor + lx * (1D - factor);
				this.motionY = motionY * factor + ly * (1D - factor);
				this.motionZ = motionZ * factor + lz * (1D - factor);
			} else if(this.type == TorexType.SHOCK || this.type == TorexType.STARFISH) {
				double factor = MathHelper.clamp_double((this.posY - EntityNukeTorex.this.posY) / coreHeight, 0, 1);
				this.motionX = angleCos * factor;
				this.motionY = 0;
				this.motionZ = -angleSin * factor;
			} else if(this.type == TorexType.RING) {
				setRollMotion(simPosX, true);
			} else if(this.type == TorexType.CONDENSATION) {
				double speed = 0.00002 * EntityNukeTorex.this.ticksExisted;
				this.motionX = -dx * speed;
				this.motionY = 0;
				this.motionZ = -dz * speed;
			}

			double mult = this.motionMult * tickSimulationSpeed;

			this.posX += this.motionX * mult;
			this.posY += this.motionY * mult;
			this.posZ += this.motionZ * mult;

			this.updateColor();
		}

		/* The original convection/ring math on the X/Y simulation plane, without temporary vectors. */
		private void setRollMotion(double simPosX, boolean ring) {
			if(ring && simPosX > EntityNukeTorex.this.posX + torusWidth * 2) {
				motionX = motionY = motionZ = 0;
				return;
			}
			double torusX = EntityNukeTorex.this.posX + torusWidth;
			double torusY = EntityNukeTorex.this.posY + coreHeight * (ring ? 0.5 : 1);
			double dx = torusX - simPosX, dy = torusY - this.posY;
			double roller = rollerSize * rangeMod * (ring ? 0.25 : 1);
			double dist = MathHelper.sqrt_double(dx * dx + dy * dy) / roller - 1D;
			float rotation = (float)((1D - Math.pow(Math.E, -dist)) * Math.PI * 0.5D);
			double rx = -dx / dist, ry = -dy / dist;
			double cos = MathHelper.cos(rotation), sin = MathHelper.sin(rotation);
			double mx = torusX + (rx * cos + ry * sin) - simPosX;
			double my = torusY + (ry * cos - rx * sin) - this.posY;
			if(ring) { mx *= 0.001D; my *= 0.001D; }
			double length = MathHelper.sqrt_double(mx * mx + my * my);
			if(length < 1.0E-4D) { mx = my = 0; } else { mx /= length; my /= length; }
			motionX = mx * angleCos; motionY = my; motionZ = -mx * angleSin;
		}

		private void updateColor() {
			// Two owned vectors retain the public color helpers without allocating on every tick.
			Vec3 previous = this.prevColor;
			this.prevColor = this.color;
			this.color = previous;
			double dx = EntityNukeTorex.this.posX - posX;
			double dy = EntityNukeTorex.this.posY + coreHeight - posY;
			double dz = EntityNukeTorex.this.posZ - posZ;
			double col = 2D / Math.max(Math.sqrt((dx * dx + dy * dy + dz * dz) / heat), 1);
			if(this.type == TorexType.STARFISH) {
				color.xCoord = Math.max(col * 8, 0.5); color.yCoord = Math.max(col * 1.5, 0.1); color.zCoord = Math.max(col * 0.5, 0.05);
			} else if(tickColorType == 1) {
				color.xCoord = Math.max(col, 0.25); color.yCoord = Math.max(col * 2, 0.25); color.zCoord = Math.max(col * 0.5, 0.25);
			} else if(tickColorType == 2) {
				if(this.type == TorexType.RING) {
					color.xCoord = color.yCoord = color.zCoord = Math.max(col, 0.25);
				} else {
					color.xCoord = (rainbow >> 16 & 255) / 255D; color.yCoord = (rainbow >> 8 & 255) / 255D; color.zCoord = (rainbow & 255) / 255D;
				}
			} else {
				color.xCoord = Math.max(col * 2, 0.25); color.yCoord = Math.max(col * 1.5, 0.25); color.zCoord = Math.max(col * 0.5, 0.25);
			}
		}

		public double getInterpX(float interp, float scale) { return interpComponent(prevPosX, posX, EntityNukeTorex.this.posX, interp, scale); }
		public double getInterpY(float interp, float scale) { return interpComponent(prevPosY, posY, EntityNukeTorex.this.posY, interp, scale); }
		public double getInterpZ(float interp, float scale) { return interpComponent(prevPosZ, posZ, EntityNukeTorex.this.posZ, interp, scale); }
		private double interpComponent(double previous, double current, double origin, float interp, float scale) {
			double value = previous + (current - previous) * interp;
			return type == TorexType.SHOCK ? value : (value - origin) * scale + origin;
		}

		public Vec3 getInterpPos(float interp) {
			float scale = (float)EntityNukeTorex.this.getScale();
			return Vec3.createVectorHelper(getInterpX(interp, scale), getInterpY(interp, scale), getInterpZ(interp, scale));
		}

		public double getInterpRed(float interp, double greying) { return interpColor(prevColor.xCoord, color.xCoord, interp, greying); }
		public double getInterpGreen(float interp, double greying) { return interpColor(prevColor.yCoord, color.yCoord, interp, greying); }
		public double getInterpBlue(float interp, double greying) { return interpColor(prevColor.zCoord, color.zCoord, interp, greying); }
		private double interpColor(double previous, double current, float interp, double greying) {
			return type == TorexType.CONDENSATION ? 1 : (previous + (current - previous) * interp) * (greying + (type == TorexType.RING ? 1 : 0));
		}

		public Vec3 getInterpColor(float interp) {
			double greying = EntityNukeTorex.this.getGreying();
			return Vec3.createVectorHelper(getInterpRed(interp, greying), getInterpGreen(interp, greying), getInterpBlue(interp, greying));
		}

		public float getAlpha() {
			return getAlpha(EntityNukeTorex.this.getAlpha());
		}

		public float getAlpha(float cloudAlpha) {
			float alpha = (1F - ((float)age / (float)cloudletLife)) * cloudAlpha;
			if(this.type == TorexType.CONDENSATION) alpha *= 0.25;
			return alpha;
		}

		private float startingScale = 1;
		private float growingScale = 5F;

		public float getScale() {
			return getScale((float)EntityNukeTorex.this.getScale());
		}

		public float getScale(float cloudScale) {
			float base = startingScale + ((float)age / (float)cloudletLife) * growingScale;
			if(this.type != TorexType.SHOCK) base *= cloudScale;
			return base;
		}

		public Cloudlet setScale(float start, float grow) {
			this.startingScale = start;
			this.growingScale = grow;
			return this;
		}

		private double motionMult = 1F;

		public Cloudlet setMotion(double mult) {
			this.motionMult = mult;
			return this;
		}
	}

	public static enum TorexType {
		STANDARD,
		SHOCK,
		RING,
		CONDENSATION,
		STARFISH,
	}

	@Override protected void writeEntityToNBT(NBTTagCompound nbt) { }
	@Override public boolean writeToNBTOptional(NBTTagCompound nbt) { return false; }
	@Override public void readEntityFromNBT(NBTTagCompound nbt) { this.setDead(); }

	@Override
	@SideOnly(Side.CLIENT)
	public boolean isInRangeToRenderDist(double distance) {
		return true;
	}

	public static void statFacStandard(World world, double x, double y, double z, float scale) { statFac(world, x, y, z, scale); }
	public static void statFacBale(World world, double x, double y, double z, float scale) { statFac(world, x, y, z, scale); }

	public static void statFac(World world, double x, double y, double z, float scale) {
		NuclearBurstContext context = NuclearBurstResolver.resolve(world, x, y, z, Math.max(1, Math.round(scale)));
		EntityNukeTorex torex = new EntityNukeTorex(world).applyBurstContext(context).setScale(MathHelper.clamp_float((float)context.effects.visualScale, 0.5F, 5F));
		torex.setPosition(x, y, z);
		torex.forceSpawn = true;
		world.spawnEntityInWorld(torex);
		TrackerUtil.setTrackingRange(world, torex, 1000);
	}
	public static void startFacAnti(World world, double x, double y, double z, float scale) {
		EntityNukeTorex torex = new EntityNukeTorex(world).setScale(MathHelper.clamp_float(scale * 0.01F, 0.5F, 5F)).setType(2);
		torex.setPosition(x, y, z);
		world.spawnEntityInWorld(torex);
		TrackerUtil.setTrackingRange(world, torex, 1000);
	}
}
