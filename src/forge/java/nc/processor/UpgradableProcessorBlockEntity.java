package nc.processor;

import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Rate;
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

abstract class UpgradableProcessorBlockEntity extends BlockEntity {
    final ProcessorUpgrades upgrades = new ProcessorUpgrades(this);
    final ProcessorEnergy power;
    final Rate rate;
    private final int baseCapacity;
    private double basePower;
    double batchTicks;
    double progressHalfTicks;

    UpgradableProcessorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Rate rate, int baseCapacity) {
        super(type, pos, state);
        this.rate = rate;
        this.baseCapacity = baseCapacity;
        power = new ProcessorEnergy(this, Integer.MAX_VALUE, this::energyCapacity);
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
        return NCItemProcessors.speedUpgrade(upgrades.getStackInSlot(ProcessorUpgrades.SPEED).getCount());
    }

    private double powerMultiplier() {
        return NCItemProcessors.powerUpgrade(upgrades.getStackInSlot(ProcessorUpgrades.SPEED).getCount(),
                upgrades.getStackInSlot(ProcessorUpgrades.ENERGY).getCount());
    }

    int energyPerTick() {
        return (int) Math.ceil(basePower * powerMultiplier());
    }

    private int energyCapacity() {
        double scaledCapacity = rate.time(baseCapacity) * rate.powerScale() * powerMultiplier() / speed();
        return Math.max(1, Math.max(energyPerTick(), (int) Math.ceil(scaledCapacity)));
    }

    boolean installUpgrade(Player player, InteractionHand hand, Direction face) {
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

    void dropUpgrades(Level level, BlockPos pos) {
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), upgrades.getStackInSlot(slot));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
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
