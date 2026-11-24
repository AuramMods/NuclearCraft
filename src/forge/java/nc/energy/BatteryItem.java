package nc.energy;

import nc.init.NCBatteries.Tier;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntSupplier;

public final class BatteryItem {
    static final String STORAGE = "energyStorage";
    private static final String ENERGY = "energy";
    private static final int BAR_COLOR = 0xC876DD;

    private BatteryItem() {}

    public static int capacity(ItemStack stack) {
        return stack.getItem() instanceof Chargeable item ? item.capacity() : 0;
    }

    public static int stored(ItemStack stack) {
        var tag = stack.getTag();
        return tag == null ? 0 : (int) Math.max(0, Math.min(capacity(stack), tag.getCompound(STORAGE).getLong(ENERGY)));
    }

    public static void store(ItemStack stack, int charge) {
        stack.getOrCreateTagElement(STORAGE).putLong(ENERGY, charge);
    }

    static Component chargeLabel(long charge, long capacity) {
        return Component.translatable("block.nuclearcraft.battery.charge", charge, capacity);
    }

    private static int barWidth(ItemStack stack) {
        return Math.round(13F * stored(stack) / capacity(stack));
    }

    private interface Chargeable {
        int capacity();

        int transfer();
    }

    public static final class BlockBattery extends BlockItem implements Chargeable {
        private final Tier tier;

        public BlockBattery(BatteryBlock block, Tier tier) {
            super(block, new Properties().stacksTo(1));
            this.tier = tier;
        }

        @Override public int capacity() { return tier.capacity(); }
        @Override public int transfer() { return tier.transfer(); }
        @Override public int getBarColor(ItemStack stack) { return BAR_COLOR; }
        @Override public int getBarWidth(ItemStack stack) { return barWidth(stack); }
        @Override public boolean isBarVisible(ItemStack stack) { return stored(stack) > 0; }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flags) {
            lines.add(chargeLabel(stored(stack), capacity()));
        }

        @Override
        public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
            return new Energy(stack, this);
        }
    }

    public static final class Cell extends Item implements Chargeable {
        private final IntSupplier capacity;
        private final IntSupplier transfer;

        public Cell(IntSupplier capacity, IntSupplier transfer) {
            super(new Properties().stacksTo(1));
            this.capacity = capacity;
            this.transfer = transfer;
        }

        @Override public int capacity() { return capacity.getAsInt(); }
        @Override public int transfer() { return transfer.getAsInt(); }
        @Override public int getBarColor(ItemStack stack) { return BAR_COLOR; }
        @Override public int getBarWidth(ItemStack stack) { return barWidth(stack); }
        @Override public boolean isBarVisible(ItemStack stack) { return stored(stack) > 0; }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flags) {
            lines.add(chargeLabel(stored(stack), capacity()));
        }

        @Override
        public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
            return new Energy(stack, this);
        }
    }

    private static final class Energy implements IEnergyStorage, ICapabilityProvider {
        private final ItemStack stack;
        private final Chargeable item;
        private final LazyOptional<IEnergyStorage> handler = LazyOptional.of(() -> this);

        private Energy(ItemStack stack, Chargeable item) {
            this.stack = stack;
            this.item = item;
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == ForgeCapabilities.ENERGY && canReceive() ? handler.cast() : LazyOptional.empty();
        }

        @Override
        public int receiveEnergy(int request, boolean simulate) {
            int current = getEnergyStored();
            int received = Math.min(Math.min(request, item.transfer()), getMaxEnergyStored() - current);
            if (received <= 0 || !canReceive()) {
                return 0;
            }
            if (!simulate) {
                store(stack, current + received);
            }
            return received;
        }

        @Override
        public int extractEnergy(int request, boolean simulate) {
            int current = getEnergyStored();
            int extracted = Math.min(Math.min(request, item.transfer()), current);
            if (extracted <= 0 || !canExtract()) {
                return 0;
            }
            if (!simulate) {
                store(stack, current - extracted);
            }
            return extracted;
        }

        @Override public int getEnergyStored() { return stored(stack); }
        @Override public int getMaxEnergyStored() { return item.capacity(); }
        @Override public boolean canReceive() { return stack.getCount() == 1; }
        @Override public boolean canExtract() { return canReceive(); }
    }
}
