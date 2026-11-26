package nc.processor;

import nc.automation.MachineInterface;
import nc.init.NCItemProcessors;
import nc.processor.ProcessorProfile.Rates;
import nc.recipe.ProcessingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.Nullable;

import java.util.List;

abstract class UpgradableProcessorBlockEntity extends BlockEntity {
    final ProcessorUpgrades upgrades = new ProcessorUpgrades(this);
    final ProcessorEnergy power;
    final Rates rate;
    private final int baseCapacity;
    private final boolean generator;
    private boolean redstoneControl;
    private double basePower;
    double batchTicks;
    double progressHalfTicks;

    abstract List<? extends ProcessingRecipe> availableRecipes(Level level);

    abstract ProcessorInputs recipeInput();

    UpgradableProcessorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Rates rate, int baseCapacity) {
        this(type, pos, state, rate, baseCapacity, false, false);
    }

    UpgradableProcessorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Rates rate,
                                   int baseCapacity, boolean generator, boolean redstoneControl) {
        super(type, pos, state);
        this.rate = rate;
        this.baseCapacity = baseCapacity;
        this.generator = generator;
        this.redstoneControl = redstoneControl;
        power = new ProcessorEnergy(this, Integer.MAX_VALUE, this::energyCapacity, generator, rate::enabled);
    }

    final boolean redstoneControl() {
        return redstoneControl;
    }

    final boolean redstoneHalted(Level level, BlockPos pos) {
        return redstoneControl && MachineInterface.powered(level, pos, this);
    }

    final void toggleRedstoneControl() {
        setRedstoneControl(!redstoneControl);
    }

    final void setRedstoneControl(boolean enabled) {
        if (redstoneControl != enabled) {
            redstoneControl = enabled;
            setChanged();
        }
    }

    abstract ProcessorItemPorts itemPorts();

    abstract @Nullable ProcessorFluidTanks fluidTanks();

    final void autoEject(Level level) {
        itemPorts().push(level);
        var tanks = fluidTanks();
        if (tanks != null) tanks.push(level);
    }

    boolean allowsSettingsCopy() {
        return true;
    }

    boolean missingRedstoneControl() {
        // Older Forge saves paused stock processors whenever they were powered.
        return true;
    }

    void updateRates(double rawTicks, double configuredTicks, double configuredPower) {
        double previousTicks = batchTicks > 0 ? batchTicks : rawTicks;
        if (progressHalfTicks > 0 && configuredTicks > 0 && previousTicks > 0 && previousTicks != configuredTicks) {
            progressHalfTicks = progressHalfTicks * (configuredTicks / previousTicks);
            setChanged();
        }
        batchTicks = configuredTicks;
        basePower = configuredPower;
    }

    int progress() {
        return batchTicks > 0 ? (int) (1000D * Math.min(1D, progressHalfTicks / (2D * batchTicks))) : 0;
    }

    double speed() {
        if (!hasUpgrades()) {
            return 1D;
        }
        int speed = upgrades.getStackInSlot(ProcessorUpgrades.SPEED).getCount();
        return generator ? NCItemProcessors.generatorSpeedUpgrade(speed, upgrades.getStackInSlot(ProcessorUpgrades.ENERGY).getCount())
                : NCItemProcessors.speedUpgrade(speed);
    }

    private double powerMultiplier() {
        if (!hasUpgrades()) {
            return 1D;
        }
        int speed = upgrades.getStackInSlot(ProcessorUpgrades.SPEED).getCount();
        return generator ? NCItemProcessors.generatorPowerUpgrade(speed)
                : NCItemProcessors.powerUpgrade(speed, upgrades.getStackInSlot(ProcessorUpgrades.ENERGY).getCount());
    }

    int energyPerTick() {
        return (int) Math.ceil(basePower * powerMultiplier());
    }

    private int energyCapacity() {
        double scaledCapacity = rate.time(baseCapacity) * rate.powerScale() * powerMultiplier() / speed();
        return Math.max(1, Math.max(energyPerTick(), (int) Math.ceil(scaledCapacity)));
    }

    boolean installUpgrade(Player player, InteractionHand hand, Direction face) {
        if (!hasUpgrades()) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);
        int slot = ProcessorUpgrades.slotFor(held);
        if (slot < 0) {
            return false;
        }
        return getCapability(ForgeCapabilities.ITEM_HANDLER, face).map(handler -> {
            ItemStack offered = held.copyWithCount(player.isShiftKeyDown() ? held.getCount() : 1);
            int inserted = offered.getCount()
                    - handler.insertItem(handler.getSlots() - ProcessorUpgrades.SLOTS + slot, offered, false).getCount();
            if (inserted > 0 && !player.getAbilities().instabuild) {
                held.shrink(inserted);
            }
            return inserted > 0;
        }).orElse(false);
    }

    boolean hasUpgrades() {
        return true;
    }

    void dropAuxiliaryInventory(Level level, BlockPos pos) {
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), upgrades.getStackInSlot(slot));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("redstone_control", redstoneControl);
        tag.put("upgrades", upgrades.serializeNBT());
        tag.putInt("energy", power.getEnergyStored());
        long whole = (long) progressHalfTicks;
        tag.putLong("progress_half_ticks", whole);
        if (progressHalfTicks > 0) {
            if (progressHalfTicks > whole) {
                tag.putDouble("progress_fraction", progressHalfTicks - whole);
            }
            if (batchTicks > 0) {
                tag.putDouble("progress_duration", batchTicks);
            }
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("redstone_control")) {
            redstoneControl = tag.getBoolean("redstone_control");
        } else if (tag.contains("redstoneControl")) {
            redstoneControl = tag.getBoolean("redstoneControl");
        } else {
            redstoneControl = missingRedstoneControl();
        }
        CompoundTag storedUpgrades = tag.getCompound("upgrades").copy();
        storedUpgrades.putInt("Size", ProcessorUpgrades.SLOTS);
        upgrades.deserializeNBT(storedUpgrades);
        power.load(tag.getInt("energy"));
        double fraction = tag.getDouble("progress_fraction");
        progressHalfTicks = Math.max(0, tag.getLong("progress_half_ticks")) + (fraction > 0 && fraction < 1 ? fraction : 0);
        double savedDuration = tag.getDouble("progress_duration");
        batchTicks = Double.isFinite(savedDuration) && savedDuration > 0 ? savedDuration : 0;
    }
}
