package com.hbm.tileentity.machine;

import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.container.ContainerMachineGasCent;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineGasCent;
import com.hbm.inventory.recipes.GasCentrifugeRecipes;
import com.hbm.inventory.recipes.GasCentrifugeRecipes.PseudoFluidType;
import com.hbm.inventory.recipes.GasCentrifugeRecipes.CampaignGrade;
import com.hbm.inventory.recipes.GasCentrifugeRecipes.StageRecipe;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.items.ModItems;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.lib.Library;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.LoopedSoundPacket;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.BufferUtil;
import com.hbm.util.CompatEnergyControl;
import com.hbm.util.InventoryUtil;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluid.IFluidStandardReceiver;
import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

//epic!
public class TileEntityMachineGasCent extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardReceiver, IGUIProvider, IInfoProviderEC, IControlReceiver {
	
	public long power;
	public int progress;
	public boolean isProgressing;
	public static final int maxPower = 100000;
	public static final int processingSpeed = 150;
	public static final int ROTOR_MAINTENANCE = 10000;
	public enum CampaignState { STOPPED, SPINNING_UP, RUNNING, PAUSED_OUTPUT, PAUSED_POWER, MAINTENANCE, COMPLETE }
	public CampaignGrade selectedCampaign = CampaignGrade.CIVILIAN;
	public CampaignState campaignState = CampaignState.STOPPED;
	public String preparedBatch = "";
	public int rotorWear;
	public int spinup;
	private boolean feedPrepared;
	
	public FluidTank tank;
	public PseudoFluidTank inputTank;
	public PseudoFluidTank outputTank;
	
	private static final int[] slots_io = new int[] { 0, 1, 2, 3 };
	
	public TileEntityMachineGasCent() {
		super(8);
		tank = new FluidTank(Fluids.UF6, 2000);
		inputTank = new PseudoFluidTank(PseudoFluidType.NUF6, 8000);
		outputTank = new PseudoFluidTank(PseudoFluidType.LEUF6, 8000);
	}
	
	@Override
	public String getName() {
		return "container.gasCentrifuge";
	}
	
	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return i < 4;
	}
	
	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return slots_io;
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		
		power = nbt.getLong("power");
		progress = nbt.getShort("progress");
		tank.readFromNBT(nbt, "tank");
		inputTank.readFromNBT(nbt, "inputTank");
		outputTank.readFromNBT(nbt, "outputTank");
		selectedCampaign = nbt.hasKey("campaign") && "STRATEGIC".equals(nbt.getString("campaign")) ? CampaignGrade.STRATEGIC : CampaignGrade.CIVILIAN;
		try { campaignState = CampaignState.valueOf(nbt.getString("campaignState")); } catch(Exception ex) { campaignState = CampaignState.STOPPED; }
		preparedBatch = nbt.getString("preparedBatch");
		rotorWear = Math.max(0, nbt.getInteger("rotorWear"));
		spinup = nbt.getInteger("spinup");
		feedPrepared = nbt.getBoolean("feedPrepared");
		// Legacy progress was unreserved; preserve it and reserve its feed on the first resumed tick.
		if(progress > 0 && preparedBatch.length() == 0) campaignState = CampaignState.STOPPED;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("power", power);
		nbt.setShort("progress", (short) progress);
		tank.writeToNBT(nbt, "tank");
		inputTank.writeToNBT(nbt, "inputTank");
		outputTank.writeToNBT(nbt, "outputTank");
		nbt.setString("campaign", selectedCampaign.name());
		nbt.setString("campaignState", campaignState.name());
		nbt.setString("preparedBatch", preparedBatch);
		nbt.setInteger("rotorWear", rotorWear);
		nbt.setInteger("spinup", spinup);
		nbt.setBoolean("feedPrepared", feedPrepared);
	}
	
	public int getCentrifugeProgressScaled(int i) {
		StageRecipe recipe = getCurrentRecipe();
		return recipe == null ? 0 : (progress * i) / getDuration(recipe);
	}
	
	public long getPowerRemainingScaled(int i) {
		return (power * i) / maxPower;
	}
	
	private StageRecipe getCurrentRecipe() { return GasCentrifugeRecipes.getStage(inputTank.getTankType(), selectedCampaign); }
	private boolean hasRotor(StageRecipe r) { return slots[7] != null && slots[7].getItem() == ModItems.centrifuge_element && (!r.advancedRotor || slots[6] != null && slots[6].getItem() == ModItems.upgrade_gc_speed); }
	private int getDuration(StageRecipe r) { return slots[6] != null && slots[6].getItem() == ModItems.upgrade_gc_speed ? (r.duration + 1) / 2 : r.duration; }
	private int getEnergyPerTick(StageRecipe r) { return slots[6] != null && slots[6].getItem() == ModItems.upgrade_gc_speed ? r.energyPerTick * 2 : r.energyPerTick; }
	private boolean outputClear(StageRecipe r) { return outputTank.getFill() + r.productAmount <= outputTank.getMaxFill() && InventoryUtil.doesArrayHaveSpace(slots, 0, 3, r.outputs); }
	
	private void enrich(StageRecipe recipe) {
		ItemStack[] output = recipe.outputs;
		outputTank.setFill(outputTank.getFill() + recipe.productAmount);
		
		for(byte i = 0; i < output.length; i++)
			InventoryUtil.tryAddItemToInventory(slots, 0, 3, output[i].copy()); //reference types almost got me again
		rotorWear += recipe.advancedRotor ? 450 : 250;
		feedPrepared = false;
		campaignState = CampaignState.COMPLETE;
	}
	
	private void attemptConversion() {
		if(inputTank.getFill() < inputTank.getMaxFill() && tank.getFill() > 0) {
			int fill = Math.min(inputTank.getMaxFill() - inputTank.getFill(), tank.getFill());
			
			tank.setFill(tank.getFill() - fill);
			inputTank.setFill(inputTank.getFill() + fill);
		}
	}
	
	private boolean attemptTransfer(TileEntity te) {
		if(te instanceof TileEntityMachineGasCent) {
			TileEntityMachineGasCent cent = (TileEntityMachineGasCent) te;
			if(cent == this || outputTank.getFill() <= 0 || outputTank.getTankType() == inputTank.getTankType()) return false;
			if(cent.tank.getFill() == 0 && cent.tank.getTankType() == tank.getTankType() && !cent.feedPrepared && cent.progress == 0) {
				if(cent.inputTank.getTankType() != outputTank.getTankType() && outputTank.getTankType() != PseudoFluidType.NONE) {
					cent.inputTank.setTankType(outputTank.getTankType());
					cent.selectedCampaign = selectedCampaign;
					StageRecipe next = GasCentrifugeRecipes.getStage(outputTank.getTankType(), selectedCampaign);
					cent.outputTank.setTankType(next == null ? PseudoFluidType.NONE : next.product);
				}
				
				//God, why did I forget about the entirety of the fucking math library?
				if(cent.inputTank.getFill() < cent.inputTank.getMaxFill() && outputTank.getFill() > 0) {
					int fill = Math.min(cent.inputTank.getMaxFill() - cent.inputTank.getFill(), outputTank.getFill());
					
					outputTank.setFill(outputTank.getFill() - fill);
					cent.inputTank.setFill(cent.inputTank.getFill() + fill);
				}
				
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	public void updateEntity() {
		
		if(!worldObj.isRemote) {
			
			updateConnections();

			power = Library.chargeTEFromItems(slots, 4, power, maxPower);
			setTankType(5);
			
			if(GasCentrifugeRecipes.fluidConversions.containsValue(inputTank.getTankType())) {
				attemptConversion();
			}
			
			StageRecipe recipe = getCurrentRecipe();
			isProgressing = false;
			if(campaignState != CampaignState.STOPPED && campaignState != CampaignState.COMPLETE) {
				if(recipe == null || !hasRotor(recipe) || rotorWear >= ROTOR_MAINTENANCE) {
					campaignState = CampaignState.MAINTENANCE;
				} else if(!feedPrepared) {
					if(inputTank.getFill() >= recipe.feed) {
						inputTank.setFill(inputTank.getFill() - recipe.feed);
						feedPrepared = true;
						preparedBatch = recipe.id + ":" + worldObj.getTotalWorldTime();
						campaignState = CampaignState.SPINNING_UP;
					}
				} else if(!outputClear(recipe)) {
					campaignState = CampaignState.PAUSED_OUTPUT;
				} else if(power < getEnergyPerTick(recipe)) {
					campaignState = CampaignState.PAUSED_POWER;
				} else if(spinup < 100) {
					campaignState = CampaignState.SPINNING_UP;
					power -= getEnergyPerTick(recipe);
					spinup++;
				} else {
					campaignState = CampaignState.RUNNING;
					isProgressing = true;
					power -= getEnergyPerTick(recipe);
					if(++progress >= getDuration(recipe)) enrich(recipe);
				}
			}
			
			if(worldObj.getTotalWorldTime() % 10 == 0) {
				ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
				TileEntity te = worldObj.getTileEntity(this.xCoord - dir.offsetX, this.yCoord, this.zCoord - dir.offsetZ);
				
				//*AT THE MOMENT*, there's not really any need for a dedicated method for this. Yet.
				attemptTransfer(te);
			}
			
			this.networkPackNT(50);

			PacketDispatcher.wrapper.sendToAllAround(new LoopedSoundPacket(xCoord, yCoord, zCoord), new TargetPoint(worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 50));
		}
	}
	
	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(power);
		buf.writeInt(progress);
		buf.writeBoolean(isProgressing);
		buf.writeByte(selectedCampaign.ordinal());
		buf.writeByte(campaignState.ordinal());
		buf.writeInt(rotorWear);
		BufferUtil.writeString(buf, preparedBatch);
		//pseudofluids can be refactored another day
		buf.writeInt(inputTank.getFill());
		buf.writeInt(outputTank.getFill());
		BufferUtil.writeString(buf, inputTank.getTankType().name); //cough cough
		BufferUtil.writeString(buf, outputTank.getTankType().name);
		
		tank.serialize(buf);
	}
	
	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		power = buf.readLong();
		progress = buf.readInt();
		isProgressing = buf.readBoolean();
		selectedCampaign = CampaignGrade.values()[Math.min(buf.readByte(), CampaignGrade.values().length - 1)];
		campaignState = CampaignState.values()[Math.min(buf.readByte(), CampaignState.values().length - 1)];
		rotorWear = buf.readInt();
		preparedBatch = BufferUtil.readString(buf);
		
		inputTank.setFill(buf.readInt());
		outputTank.setFill(buf.readInt());
		inputTank.setTankType(PseudoFluidType.types.get(BufferUtil.readString(buf)));
		outputTank.setTankType(PseudoFluidType.types.get(BufferUtil.readString(buf)));
		
		tank.deserialize(buf);
	}
	
	private void updateConnections() {
		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			
			if(GasCentrifugeRecipes.fluidConversions.containsValue(inputTank.getTankType())) {
				this.trySubscribe(tank.getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			}
		}
	}
	
	private DirPos[] getConPos() {
		return new DirPos[] {
			new DirPos(xCoord, yCoord - 1, zCoord, Library.NEG_Y),
			new DirPos(xCoord + 1, yCoord, zCoord, Library.POS_X),
			new DirPos(xCoord - 1, yCoord, zCoord, Library.NEG_X),
			new DirPos(xCoord, yCoord, zCoord + 1, Library.POS_Z),
			new DirPos(xCoord, yCoord, zCoord - 1, Library.NEG_Z)
		};
	}

	@Override
	public void setPower(long i) {
		power = i;
	}

	@Override
	public long getPower() {
		return power;
		
	}

	@Override
	public long getMaxPower() {
		return maxPower;
	}
	
	public int getProcessingSpeed() {
		StageRecipe recipe = getCurrentRecipe();
		return recipe == null ? processingSpeed : getDuration(recipe);
	}

	public String getStopReason() { return "gascent.state." + campaignState.name().toLowerCase(); }
	public StageRecipe getDisplayedRecipe() { return getCurrentRecipe(); }
	@Override public boolean hasPermission(EntityPlayer player) { return player.getDistanceSq(xCoord, yCoord, zCoord) < 64; }
	@Override public void receiveControl(NBTTagCompound data) {
		if(data.hasKey("campaign") && preparedBatch.length() == 0 && campaignState == CampaignState.STOPPED)
			selectedCampaign = data.getInteger("campaign") == 1 ? CampaignGrade.STRATEGIC : CampaignGrade.CIVILIAN;
		if(data.getBoolean("start") && (campaignState == CampaignState.STOPPED || campaignState == CampaignState.COMPLETE)) {
			if(campaignState == CampaignState.COMPLETE) { progress = 0; spinup = 0; preparedBatch = ""; }
			campaignState = CampaignState.SPINNING_UP;
		}
		if(data.getBoolean("stop")) {
			campaignState = CampaignState.STOPPED; isProgressing = false;
			// A controlled stop preserves reserved feed and progress, but a spun-down rotor must spin up again.
			spinup = 0;
		}
		if(data.getBoolean("maintain") && campaignState == CampaignState.MAINTENANCE && slots[7] != null && slots[7].getItem() == ModItems.centrifuge_element) {
			rotorWear = 0; slots[7].stackSize--; if(slots[7].stackSize <= 0) slots[7] = null; campaignState = CampaignState.STOPPED;
		}
	}
	
	public void setTankType(int in) {
		
		if(slots[in] != null && slots[in].getItem() instanceof IItemFluidIdentifier) {
			IItemFluidIdentifier id = (IItemFluidIdentifier) slots[in].getItem();
			FluidType newType = id.getType(worldObj, xCoord, yCoord, zCoord, slots[in]);
			
			if(tank.getTankType() != newType && !feedPrepared && progress == 0 && inputTank.getFill() == 0 && outputTank.getFill() == 0) {
				PseudoFluidType pseudo = GasCentrifugeRecipes.fluidConversions.get(newType);
				
				if(pseudo != null) {
					inputTank.setTankType(pseudo);
					StageRecipe first = GasCentrifugeRecipes.getStage(pseudo, selectedCampaign);
					outputTank.setTankType(first == null ? PseudoFluidType.NONE : first.product);
					tank.setTankType(newType);
				}
			}
			
		}
	}
	
	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { tank };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { tank };
	}
	
	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord + 1, yCoord + 5, zCoord + 1);
		}
		
		return bb;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
	
	public class PseudoFluidTank {
		PseudoFluidType type;
		int fluid;
		int maxFluid;
		
		public PseudoFluidTank(PseudoFluidType type, int maxFluid) {
			this.type = type;
			this.maxFluid = maxFluid;
		}
		
		public void setFill(int i) {
			fluid = i;
		}
		
		public void setTankType(PseudoFluidType type) {
			
			if(this.type.equals(type))
				return;
			
			if(type == null)
				this.type = PseudoFluidType.NONE;
			else
				this.type = type;
			
			this.setFill(0);
		}
		
		public PseudoFluidType getTankType() {
			return type;
		}
		
		public int getFill() {
			return fluid;
		}
		
		public int getMaxFill() {
			return maxFluid;
		}
		
		//Called by TE to save fillstate
		public void writeToNBT(NBTTagCompound nbt, String s) {
			nbt.setInteger(s, fluid);
			nbt.setInteger(s + "_max", maxFluid);
			nbt.setString(s + "_type", type.name);
		}
		
		//Called by TE to load fillstate
		public void readFromNBT(NBTTagCompound nbt, String s) {
			fluid = nbt.getInteger(s);
			int max = nbt.getInteger(s + "_max");
			if(max > 0) maxFluid = nbt.getInteger(s + "_max");
			type = PseudoFluidType.types.get(nbt.getString(s + "_type"));
			if(type == null) type = PseudoFluidType.NONE;
		}
		
		/*        ______      ______
		 *       _I____I_    _I____I_
		 *      /      \\\  /      \\\
		 *     |IF{    || ||     } || |
		 *     | IF{   || ||    }  || |
		 *     |  IF{  || ||   }   || |
		 *     |   IF{ || ||  }    || |
		 *     |    IF{|| || }     || |
		 *     |       || ||       || |
		 *     |     } || ||IF{    || |
		 *     |    }  || || IF{   || |
		 *     |   }   || ||  IF{  || |
		 *     |  }    || ||   IF{ || |
		 *     | }     || ||    IF{|| |
		 *     |IF{    || ||     } || |
		 *     | IF{   || ||    }  || |
		 *     |  IF{  || ||   }   || |
		 *     |   IF{ || ||  }    || |
		 *     |    IF{|| || }     || |
		 *     |       || ||       || |
		 *     |     } || ||IF{	   || |
		 *     |    }  || || IF{   || |
		 *     |   }   || ||  IF{  || |
		 *     |  }    || ||   IF{ || |
		 *     | }     || ||    IF{|| |
		 *     |IF{    || ||     } || |
		 *     | IF{   || ||    }  || |
		 *     |  IF{  || ||   }   || |
		 *     |   IF{ || ||  }    || |
		 *     |    IF{|| || }     || |
		 *     |       || ||       || |
		 *     |     } || ||IF{	   || |
		 *     |    }  || || IF{   || |
		 *     |   }   || ||  IF{  || |
		 *     |  }    || ||   IF{ || |
		 *     | }     || ||    IF{|| |
		 *    _|_______||_||_______||_|_
		 *   |                          |
		 *   |                          |
		 *   |       |==========|       |
		 *   |       |NESTED    |       |
		 *   |       |IF  (:    |       |
		 *   |       |STATEMENTS|       |
		 *   |       |==========|       |
		 *   |                          |
		 *   |                          |
		 *   ----------------------------
		 */
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineGasCent(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineGasCent(player.inventory, this);
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, this.progress > 0);
		data.setInteger(CompatEnergyControl.I_PROGRESS, this.progress);
	}
}
